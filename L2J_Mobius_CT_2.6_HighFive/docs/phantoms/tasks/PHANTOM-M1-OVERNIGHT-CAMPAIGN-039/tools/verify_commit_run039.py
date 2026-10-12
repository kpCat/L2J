"""Read-only current all-birth proof; owning commit fields and late component chains are separate."""
import argparse, base64, hashlib, json, re, subprocess
from collections import defaultdict
from pathlib import Path
from commit_chain039 import check_chain

def sha(value): return hashlib.sha256(value).hexdigest()
def props(path):
    return dict(line.split('=',1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
def sql_components(path):
    for block in path.read_text(encoding='utf-8-sig').split('QUERY\t')[1:]:
        lines=block.splitlines()
        if 'SELECT c.profile_id' in lines[0]:
            header=lines[1].split('\t')
            return [dict(zip(header,line.split('\t'))) for line in lines[2:] if line]
    return []
def identity(row): return {k:row[k] for k in ('database','profileId','objectId')} | {'component':row['componentName']}
def integer_identity(row):
    result=identity(row)
    for k in ('profileId','objectId'): result[k]=int(result[k])
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('startup',type=Path); parser.add_argument('scene',type=Path); parser.add_argument('--jar',type=Path,required=True); parser.add_argument('--source',required=True); parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--stock-stop',type=Path)
    args=parser.parse_args(); startup=args.startup.resolve(); scene=args.scene.resolve(); output=args.output.resolve()
    if output.exists(): raise ValueError('Immutable proof exists')
    jar_sha=sha(args.jar.read_bytes())
    ledger=[json.loads(line) for line in (startup/'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    failures=[]; raw={}; pinned=[]
    if [row['seq'] for row in ledger]!=list(range(1,len(ledger)+1)): failures.append('LEDGER_SEQUENCE_GAP')
    if len({row['run_id'] for row in ledger})!=1: failures.append('RUN_NAMESPACE_CONFLICT')
    end=ledger[-1]
    if end['kind']!='RUN_END' or not end.get('exporter_drained') or end.get('dropped')!=0 or end.get('failure'): failures.append('EXPORTER_GAP_OR_RUN_NOT_ENDED')
    for row in ledger:
        if 'raw_file' not in row: continue
        path=startup/row['raw_file']
        if path.parent!=startup or not path.is_file() or sha(path.read_bytes())!=row['raw_sha256']:
            failures.append('RAW_SHA_OR_PATH:'+str(path)); continue
        raw[path.name]=(path,props(path)); pinned.append({'file':str(path),'sha256':row['raw_sha256']})
    commits=[]
    for path,p in raw.values():
        if p.get('kind')!='TRANSACTION_COMMIT039': continue
        if p.get('commitOutcome')!='SUCCESS' or p.get('source')!='actual-owning-jdbc-commit' or p.get('sourceSha')!=args.source or p.get('sourceJarSha256','').lower()!=jar_sha or p.get('captureFailure') or p.get('runId')!=ledger[0]['run_id'] or not re.fullmatch(r'l2jmobiush5_localplay_contract039(?:[abct]|x72|x73)',p.get('database','')) or not p.get('producer'):
            failures.append('COMMIT_PROVENANCE:'+path.name); continue
        if sha(base64.b64decode(p['afterPayloadBase64'],validate=True))!=p['afterPayloadSha256']:
            failures.append('COMMIT_PAYLOAD_TAMPER:'+path.name); continue
        commits.append((path,p))
    registers=[p for _,p in raw.values() if p.get('kind')=='REGISTER']
    terminals=[p for _,p in raw.values() if p.get('kind')=='TERMINAL']
    births={(p['profileId'],p['objectId'],p['epoch']) for p in registers}
    terminal_keys={(p['profileId'],p['objectId'],p['epoch']) for p in terminals}
    if births!=terminal_keys or len(births)!=end.get('native_births'): failures.append('ALL_BIRTH_TERMINAL_GAP')
    native_commits=defaultdict(list); contexts_by_tx=defaultdict(list); component_commits=defaultdict(list)
    for path,p in commits:
        component_commits[tuple(identity(p).values())].append((path,p))
        if p['componentName']=='background.state' and p.get('canonicalChecked')=='true':
            native_commits[(p['profileId'],p['objectId'],p.get('nativeEpoch'),p.get('receiptSha256'))].append((path,p))
        if p['componentName']=='background.native-context': contexts_by_tx[(p['profileId'],p['objectId'],p['transactionSequence'])].append((path,p))
    terminals_by_key={(p['profileId'],p['objectId'],p['epoch']):p for p in terminals}
    seals_by_key=defaultdict(list)
    for path,p in raw.values():
        if p.get('checkpointStage')=='FINALIZED': seals_by_key[(p['profileId'],p['objectId'],p['epoch'],p['receiptSha256'])].append((path,p))
    if len(terminals)!=len(terminal_keys): failures.append('DUPLICATE_TERMINAL')
    sealed_births={key[:3] for key in seals_by_key}
    if sealed_births!=births: failures.append('ALL_BIRTH_FINALIZED_GAP')
    result=[]; inputs=[]
    for seal_key,seals in seals_by_key.items():
        key=seal_key[:3]; receipt=seal_key[3]; terminal=terminals_by_key.get(key,{})
        candidates=native_commits[seal_key]
        row={'profileId':int(key[0]),'objectId':int(key[1]),'epoch':int(key[2]),'terminal':terminal,'nativeCommit':'GAP','lateComponent':'GAP'}
        if len(candidates)!=1 or len(seals)!=1:
            row['reason']='MISSING_OR_AMBIGUOUS_NATIVE_COMMIT_SEAL'; result.append(row); continue
        path,p=candidates[0]; seal_path,seal=seals[0]
        contexts=contexts_by_tx[(p['profileId'],p['objectId'],p['transactionSequence'])]
        if not contexts: row['reason']='PNC_COMMIT_GAP'; result.append(row); continue
        context_path,context=max(contexts,key=lambda entry:int(entry[1]['writeIndex']))
        inputs.append((path,context_path,seal_path)); row.update(commit=str(path),seal=str(seal_path),context=str(context_path),inventoryCount=int(p.get('inventoryCount','0')))
        if any(terminal.get(k)!='true' for k in ('worldAbsent','autoSaveAbsent','leaseAbsent')) or terminal.get('nativeOwnerState')!='DETACHED' or terminal.get('terminalOutstanding')!='0' or terminal.get('terminalPendingTimers')!='0': failures.append('TERMINAL_TRIAD:'+str(key))
        later=seal_path.with_name(seal_path.name+'.sql.tsv')
        component=next((s for s in sql_components(later) if s['component_type']=='background.state'),None) if later.exists() else None
        if component:
            origin=integer_identity(p)|{'version':int(p['afterRowVersion']),'digest':p['afterPayloadSha256']}
            latest=integer_identity(p)|{'version':int(component['row_version']),'digest':sha(bytes.fromhex(component['payload']))}
            links=[]
            for cp,c in component_commits[tuple(identity(p).values())]:
                if c['beforeRowVersion']=='ABSENT': continue
                if not origin['version']<=int(c['beforeRowVersion'])<latest['version']: continue
                links.append(integer_identity(c)|{'beforeVersion':int(c['beforeRowVersion']),'afterVersion':int(c['afterRowVersion']),'beforeDigest':c['beforePayloadSha256'],'afterDigest':c['afterPayloadSha256'],'status':'COMMITTED','producer':c['producer'],'operationId':c['runId']+':'+c['transactionSequence']+':'+c['writeIndex'],'evidenceId':cp.name})
            chain=check_chain(origin,latest,links,maximum_links=4096)
            row['lateComponent']=chain['status']; row['chain']=chain; row['lateSqlSha256']=sha(later.read_bytes())
        result.append(row)
    input_file=output.with_suffix('.native-input.tsv')
    with input_file.open('x',encoding='utf-8') as stream:
        for entry in inputs: stream.write('\t'.join(map(str,entry))+'\n')
    java=Path(r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe')
    cp=str(args.jar)+';'+str(args.jar.parent/'*')
    command=[str(java),'-cp',cp,str(Path(__file__).with_name('VerifyNativeCommit039.java')),str(input_file)]
    checked=subprocess.run(command,capture_output=True,text=True,encoding='utf-8')
    output.with_suffix('.native.log').write_text(checked.stdout+checked.stderr,encoding='utf-8')
    native={line.split('\t',2)[0]:line.split('\t',2)[1:] for line in checked.stdout.splitlines() if '\t' in line}
    for row in result:
        if 'commit' in row:
            value=native.get(row['commit'],['GAP','DECODER_DID_NOT_RETURN']); row['nativeCommit']=value[0]; row['nativeReason']=value[1]
    stop=(args.stock_stop.resolve() if args.stock_stop else scene/'stock-stop')/'typed-stop-done.properties'
    typed=props(stop) if stop.exists() else {}
    drain='phase=DONE, outcome=COMPLETE,' in typed.get('progress','') and 'retainedEntries=0' in typed.get('materialization','') and not typed.get('proofFailure','') and all(typed.get(k)=='false' for k in ('SCHEDULED_POOLShutdown','INSTANT_POOLShutdown','HIGH_PRIORITY_SCHEDULED_POOLShutdown'))
    full=not failures and births and len(result)==len(seals_by_key) and all(row['nativeCommit']=='PASS' and row['lateComponent'] in ('EXACT','LINKED') for row in result)
    value={'contract':'TASK039_CURRENT_OWNING_COMMIT_PROOF','sourceSha':args.source,'jarSha256':jar_sha,'startup':str(startup),'scene':str(scene),'ledgerFrames':len(ledger),'commitRecords':len(commits),'nativeBirths':len(births),'allBirthsTerminal':births==terminal_keys,'GLOBAL_DRAIN':drain,'FULL_SAVE':bool(full),'pass':bool(full and drain),'failures':failures,'rows':result,'rawPins':pinned,'nativeVerifierCommand':command,'nativeVerifierExit':checked.returncode,'old038Ruling':'All historical FAIL/UNKNOWN retained; no retrospective producer'}
    with output.open('x',encoding='utf-8') as stream: json.dump(value,stream,ensure_ascii=False,indent=2); stream.write('\n')
    print(json.dumps({k:value[k] for k in ('nativeBirths','commitRecords','allBirthsTerminal','GLOBAL_DRAIN','FULL_SAVE','pass','failures')},ensure_ascii=False))
    return 0 if value['pass'] else 2
if __name__=='__main__': raise SystemExit(main())
