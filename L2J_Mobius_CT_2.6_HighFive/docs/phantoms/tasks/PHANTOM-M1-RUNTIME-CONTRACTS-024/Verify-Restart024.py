"""Read-only exact canonical comparison; expected values originate only in SEALED receipts."""
import argparse, csv, hashlib, json
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--cohort',type=Path,required=True)
p.add_argument('--sealed',type=Path,required=True)
p.add_argument('--sql',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--crash-receipt',type=Path)
a=p.parse_args()
def props(path): return dict(line.split('=',1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
def rows(name):
    with (a.sql/name).open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f,delimiter='\t'))
cohort=json.loads(a.cohort.read_text(encoding='utf-8-sig'))
snapshots={}
for path in a.sealed.glob('*.properties'):
    row=props(path)
    if 'profileId' not in row: continue
    pid=int(row['profileId'])
    if pid not in snapshots or int(row['preparedRowVersion'])>int(snapshots[pid][1]['preparedRowVersion']): snapshots[pid]=(path,row)
chars={int(r['profileId']):r for r in rows('characters.tsv')}
items,skills=rows('items.tsv'),rows('skills.tsv')
contexts,states,pending={},{},set()
for line in (a.sql/'durable-decoded.tsv').read_text(encoding='utf-8-sig').splitlines():
    r=line.split('\t'); pid=int(r[0])
    if r[2]=='context': contexts[pid]=r
    elif r[2]=='state': states[pid]=r
    elif r[2]=='owned': pending.add(pid)
integers=('level','exp','sp','expBeforeDeath','x','y','z','heading','classIndex','classId','race','vitality')
floating=('hp','maxHp','mp','maxMp','cp','maxCp')
out=[]; errors=[]
for actor in cohort:
    pid=int(actor['profileId'])
    if pid not in snapshots or pid not in chars:
        errors.append(f'profile {pid}: missing immutable SEALED receipt or SQL'); continue
    path,snapshot=snapshots[pid]; saved=chars[pid]
    expected={k:int(snapshot[k]) for k in integers}|{k:float(snapshot[k]) for k in floating}
    expected|={k:snapshot[k] for k in ('inventoryHash','skillsHash')}
    actual={k:int(saved[k]) for k in integers}|{k:float(saved[k]) for k in floating}
    inv=hashlib.sha256()
    for item in sorted((r for r in items if int(r['profileId'])==pid and r['loc'] in ('INVENTORY','PAPERDOLL')),key=lambda r:int(r['object_id'])):
        for k in ('object_id','item_id','count','loc'): inv.update(item[k].encode('ascii')); inv.update(b'\0')
    actual['inventoryHash']=inv.hexdigest()
    selected=sorted((r for r in skills if int(r['profileId'])==pid and int(r['class_index'])==actual['classIndex']),key=lambda r:int(r['skill_id']))
    actual['skillsHash']=hashlib.sha256(''.join(f"{r['skill_id']}:{r['skill_level']}\n" for r in selected).encode('ascii')).hexdigest()
    differences={k:{'sealed':v,'sql':actual[k]} for k,v in expected.items() if (abs(v-actual[k])>0.000001 if k in floating else v!=actual[k])}
    c,s=contexts.get(pid),states.get(pid)
    finalized=bool(c and s and pid not in pending and c[3]=='COMPLETED' and c[4]==s[1] and c[5]==s[4] and int(s[1])>int(snapshot['preparedRowVersion']))
    passed=not differences and finalized
    out.append(dict(profileId=pid,source=snapshot.get('source'),snapshotFile=path.name,snapshotSha256=hashlib.sha256(path.read_bytes()).hexdigest(),epoch=int(snapshot['epoch']),initialEpoch=snapshot.get('initialEpoch'),preparedVersion=int(snapshot['preparedRowVersion']),stateVersion=int(s[1]) if s else None,nativeFinalized=finalized,differences=differences,pass_=passed))
    if not passed: errors.append(f'profile {pid}: exact={not differences}, finalized={finalized}')
receipt_integrity=None
if a.crash_receipt:
    r=props(a.crash_receipt); directory=a.crash_receipt.parent
    receipt_integrity=(r['owner']=='TASK024_CONTRACT' and r['REALcount']=='0' and r['mode'] in ('CRASH_NATIVE','CRASH_FINALIZE') and r['faultPoint']==('AFTER_OWNED_NATIVE_STORE' if r['mode']=='CRASH_NATIVE' else 'AFTER_OWNED_FINALIZE_COMMIT'))
    for file,key in [(r['snapshotFile'],'snapshotSha256'),('window-sql.tsv','sqlSha256'),('pre-arm-threads.txt','preDumpSha256')]:
        receipt_integrity &= hashlib.sha256((directory/file).read_bytes()).hexdigest()==r[key].lower()
    receipt_integrity &= int(r['profileId']) in snapshots
    if not receipt_integrity: errors.append('Exact planned crash receipt integrity failed')
counts=rows('counts.tsv')[0]
manifest=json.loads((a.sql/'runtime-manifest.json').read_text(encoding='utf-8-sig'))
if not counts['database_name'].startswith('l2jmobiush5_localplay_contract024') or manifest['databaseName']!=counts['database_name']: errors.append('Owned database identity differs')
if int(counts['pendingOwnedStores'])!=0: errors.append('Pending owned-store receipts remain')
document=dict(kind='EXACT_SEALED_TO_SAME_DB_CANONICAL',sourceSha=manifest['codeSha'],database=counts['database_name'],requiredProfiles=[int(r['profileId']) for r in cohort],rows=out,receiptIntegrity=receipt_integrity,errors=errors,pass_=not errors and len(out)==len(cohort),noDiagnosticCounterRestoration=True)
a.output.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(pass_=document['pass_'],rows=len(out),errors=errors)))
raise SystemExit(0 if document['pass_'] else 2)
