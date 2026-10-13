"""Read-only actual cross-boot chain, native load and stable goal proof; no SQL mutations."""
import argparse, base64, csv, hashlib, json, subprocess
from pathlib import Path
from complete_chain041 import check_complete_chain as check_chain
from raw_archive041 import raw_path

def sha(data): return hashlib.sha256(data).hexdigest()
def props(file): return dict(line.split('=',1) for line in file.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
def components(root):
    result={}
    for line in (root/'durable-hex.tsv').read_text(encoding='utf-8-sig').splitlines():
        pid,version,kind,payload=line.split('\t',3)
        result[(int(pid),kind)]={'version':int(version),'digest':sha(bytes.fromhex(payload))}
    return result

p=argparse.ArgumentParser(description=__doc__)
for name in ('prior-cut','next-cut','startup','cohort','scene','jar','output'): p.add_argument('--'+name,type=Path,required=True)
p.add_argument('--source',required=True)
a=p.parse_args()
if a.output.exists(): raise ValueError('Immutable output exists')
before,after=components(a.prior_cut),components(a.next_cut)
cohort=json.loads(a.cohort.read_text(encoding='utf-8-sig'))
if isinstance(cohort,dict): cohort=[cohort]  # WinPS5 single-member JSON; same exact immutable identity.
if not isinstance(cohort,list) or not 1<=len(cohort)<=8 or any(not isinstance(row,dict) for row in cohort): raise ValueError('Exact one-to-eight cohort objects required')
ids=[int(row['profileId']) for row in cohort]
if any(pid<=0 for pid in ids) or len(ids)!=len(set(ids)): raise ValueError('Positive distinct exact cohort IDs required')
errors=[]; records=[]; pins=[]; loads=[]; results=[]
jarsha=sha(a.jar.read_bytes()); database=None
for root in (a.prior_cut,a.next_cut):
    manifest=json.loads((root/'runtime-manifest.json').read_text(encoding='utf-8-sig'))
    with (root/'counts.tsv').open(encoding='utf-8-sig',newline='') as f: count=next(csv.DictReader(f,delimiter='\t'))
    if manifest['codeSha']!=a.source or manifest['gameJarSha256'].lower()!=jarsha or count['database_name']!=manifest['databaseName'] or count['pendingOwnedStores']!='0' or count['onlineCharacters']!='0': errors.append('STOPPED_AUTHORITY:'+str(root))
    if database and database!=manifest['databaseName']: errors.append('NOT_SAME_DATABASE')
    database=manifest['databaseName']
    for file in root.iterdir():
        if file.is_file(): pins.append({'file':str(file.resolve()),'sha256':sha(file.read_bytes())})
ledger=[json.loads(line) for line in (a.startup/'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
if ledger[-1]['kind']!='RUN_END' or ledger[-1].get('dropped')!=0 or ledger[-1].get('failure') or not ledger[-1].get('exporter_drained'): errors.append('UNFINISHED_RUN_OR_GAP')
if [row['seq'] for row in ledger]!=list(range(1,len(ledger)+1)): errors.append('LEDGER_GAP')
for row in ledger:
    if 'raw_file' not in row: continue
    file=raw_path(a.startup,row); raw=file.read_bytes()
    if sha(raw)!=row['raw_sha256']: errors.append('RAW_SHA:'+str(file)); continue
    value=props(file); records.append((row['seq'],file,value))
commits=[]
for seq,file,value in records:
    if value.get('kind')!='TRANSACTION_COMMIT039': continue
    if value.get('sourceSha')!=a.source or value.get('sourceJarSha256','').lower()!=jarsha or value.get('database')!=database or value.get('source')!='actual-owning-jdbc-commit' or value.get('commitOutcome')!='SUCCESS' or value.get('captureFailure') or sha(base64.b64decode(value['afterPayloadBase64']))!=value['afterPayloadSha256']: errors.append('COMMIT_PROVENANCE:'+str(file)); continue
    commits.append((seq,file,value))
for pid in ids:
    births=[(seq,file,row) for seq,file,row in records if row.get('kind')=='REGISTER' and int(row['profileId'])==pid and row.get('nativeBirthBoundary')=='true']
    row={'profileId':pid,'chains':{},'nativeLoad':'GAP','stableGoal':False}
    for kind in ('background.state','background.native-context','goal.runtime'):
        origin=before.get((pid,kind)); end=after.get((pid,kind))
        if not origin or not end: errors.append('MISSING_COMPONENT:'+str(pid)+':'+kind); continue
        objectid=next((int(value['objectId']) for _,_,value in commits if int(value['profileId'])==pid),0)
        identity=dict(database=database,profileId=pid,objectId=objectid,component=kind)
        links=[]
        for seq,file,value in commits:
            if int(value['profileId'])!=pid or value['componentName']!=kind or value['beforeRowVersion']=='ABSENT' or not origin['version']<=int(value['beforeRowVersion'])<end['version']: continue
            links.append(identity|dict(beforeVersion=int(value['beforeRowVersion']),afterVersion=int(value['afterRowVersion']),beforeDigest=value['beforePayloadSha256'],afterDigest=value['afterPayloadSha256'],status='COMMITTED',producer=value['producer'],operationId=value['runId']+':'+value['transactionSequence']+':'+value['writeIndex'],evidenceId=file.name))
        checked=check_chain(identity|origin,identity|end,links,maximum_links=4096); row['chains'][kind]=checked
        if checked['status'] not in ('EXACT','LINKED'): errors.append('CROSS_BOOT_CHAIN:'+str(pid)+':'+kind+':'+checked['reason'])
    row['stableGoal']=before.get((pid,'goal.runtime'))==after.get((pid,'goal.runtime')) and (pid,'goal.runtime') in before
    row['goalLineage']=row['chains'].get('goal.runtime',{}).get('status','GAP')
    if row['goalLineage'] not in ('EXACT','LINKED'): errors.append('GOAL_CHANGED_WITHOUT_GOAL_PRODUCER_PROOF:'+str(pid))
    if births:
        birthseq,birthfile,birth=min(births,key=lambda entry:entry[0])
        marked=next(((seq,file,value) for seq,file,value in commits if seq>birthseq and int(value['profileId'])==pid and value['componentName']=='background.state' and value['producer']=='markMaterialized'),None)
        if marked: loads.append((marked[1],birthfile)); row['nativeLoad']='PENDING_DECODER'; row['birth']=str(birthfile); row['markCommit']=str(marked[1])
    if row['nativeLoad']=='GAP': errors.append('ORIGINAL_PROFILE_NO_ACTUAL_NATIVE_LOAD:'+str(pid))
    results.append(row)
inputfile=a.output.with_suffix('.load-input.tsv')
inputfile.write_text(''.join(str(x.resolve())+'\t'+str(y.resolve())+'\n' for x,y in loads),encoding='utf-8')
java=Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe')
decoded=subprocess.run([str(java),'-cp',str(a.jar.resolve())+';'+str(a.jar.resolve().parent/'*'),str(Path(__file__).with_name('VerifyNativeLoad041.java').resolve()),str(inputfile.resolve())],capture_output=True,text=True,encoding='utf-8')
a.output.with_suffix('.native-load.log').write_text(decoded.stdout+decoded.stderr,encoding='utf-8')
values={line.split('\t',2)[0]:line.split('\t',2)[1] for line in decoded.stdout.splitlines() if '\t' in line}
for row in results:
    if row.get('markCommit'): row['nativeLoad']=values.get(str(Path(row['markCommit']).resolve()),'GAP')
    if row['nativeLoad']!='PASS': errors.append('NATIVE_LOAD_NOT_PASS:'+str(row['profileId']))
receipt_input=a.output.with_suffix('.receipt-input.tsv')
state_commits=[entry for entry in commits if entry[2]['componentName']=='background.state' and int(entry[2]['profileId']) in ids]
receipt_input.write_text('CUT\t'+str((a.prior_cut/'durable-hex.tsv').resolve())+'\n'+''.join('COMMIT\t'+str(file.resolve())+'\n' for _,file,_ in state_commits),encoding='utf-8')
receipt_run=subprocess.run([str(java),'-cp',str(a.jar.resolve())+';'+str(a.jar.resolve().parent/'*'),str(Path(__file__).with_name('DecodeStateReceipt041.java').resolve()),str(receipt_input.resolve())],capture_output=True,text=True,encoding='utf-8')
a.output.with_suffix('.receipts.log').write_text(receipt_run.stdout+receipt_run.stderr,encoding='utf-8')
if receipt_run.returncode: errors.append('RECEIPT_CODEC_FAILED')
decoded_receipts={}
for line in receipt_run.stdout.splitlines():
    label,pid,exp,sp,key=line.split('\t'); decoded_receipts[label]=dict(profileId=int(pid),exp=int(exp),sp=int(sp),operationKey=key)
last={pid:decoded_receipts.get('CUT:'+str(pid)) for pid in ids}
used={pid:{last[pid]['operationKey']} if last[pid] else set() for pid in ids}; positive_background=0
for _,file,value in state_commits:
    pid=int(value['profileId']); current=decoded_receipts.get(str(file.resolve())); previous=last[pid]
    if not current or not previous: errors.append('RECEIPT_ORIGIN_GAP:'+str(pid)); continue
    if value['producer']=='execute' and (current['exp']>previous['exp'] or current['sp']>previous['sp']):
        positive_background+=1
        if current['operationKey'] in used[pid]: errors.append('REAPPLIED_POSITIVE_BACKGROUND_RECEIPT:'+str(pid)+':'+current['operationKey'])
        used[pid].add(current['operationKey'])
    last[pid]=current
samples=json.loads((a.scene/'all-samples.json').read_text(encoding='utf-8-sig'))
for row in results:
    seen=[actor for sample in samples for actor in sample['actors'] if int(actor['profileId'])==row['profileId'] and actor.get('worldPresent')=='true']
    row['usefulResume']=bool(seen and int(seen[-1].get('nativeDamageSequence','0'))>int(seen[0].get('nativeDamageSequence','0')) and int(seen[-1].get('nativeFarmCycleSequence','0'))>int(seen[0].get('nativeFarmCycleSequence','0')))
    if not row['usefulResume']: errors.append('ORIGINAL_PROFILE_NO_USEFUL_RESUME:'+str(row['profileId']))
document=dict(contract='ACTUAL_SAME_DB_CROSS_BOOT041',sourceSha=a.source,jarSha256=jarsha,database=database,priorCut=str(a.prior_cut.resolve()),nextCut=str(a.next_cut.resolve()),startup=str(a.startup.resolve()),rows=results,errors=errors,pass_=not errors,pinnedCuts=pins,positiveBackgroundReceipts=positive_background,repeatedPositiveReceiptCheck='actual execute receipt identities; native earned ownership separately proved by all-birth verifier',goalScope='exact unchanged goal bytes/version only; changed goals require separate actual producer proof',noSQLWrites=True)
a.output.write_text(json.dumps(document,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(dict(pass_=document['pass_'],rows=len(results),errors=errors)))
raise SystemExit(0 if document['pass_'] else 2)
