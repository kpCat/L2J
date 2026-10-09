"""Audit the entire actual native away/return flow, including failures; no epoch sums."""
import argparse
import hashlib
import json
from pathlib import Path
from boundary_sql028 import properties, tables
from continuity028 import segment_result


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def durable(root):
    result = {}
    for line in (root/'durable-decoded.tsv').read_text(encoding='utf-8-sig').splitlines():
        fields = line.split('\t')
        result.setdefault(fields[0], {})[fields[2]] = fields
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('episode', type=Path)
    parser.add_argument('--path', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = args.episode
    baseline = read(root/'baseline-cohort.json')
    primaries = read(root/'primary.json')
    ids = {str(row['profileId']) for row in baseline}
    primary_ids = [str(row['profileId']) for row in primaries]
    failures = []
    markers = [json.loads(line) for line in (root/'phase-markers.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    phases = {row['phase']: row for row in markers}
    required = ['DEPART','AWAY_ARRIVED','BACKGROUND_ABSENT','RETURN','RETURNED','POST_RETURN_DONE']
    if [row['phase'] for row in markers] != required:
        failures.append('FLOW_INCOMPLETE_OR_PHASE_ORDER')
    begin = int(phases['DEPART']['sampleNanos'])
    end = int(phases.get('POST_RETURN_DONE', markers[-1])['sampleNanos'])
    frames = [json.loads(line) for line in (root/'full-native/full-cohort-samples.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    frames = [frame for frame in frames if begin <= int(frame['sampleNanos']) <= end]
    run = markers[0]['observer']['runId']
    gaps = []
    for i, frame in enumerate(frames):
        observer = frame['observer']
        if observer.get('runId') != run or observer.get('sessionState') != 'RUNNING' or not observer.get('present') or not observer.get('online') or observer.get('dead') or int(observer.get('objectId',0)) != 268492939:
            failures.append('OBSERVER_IDENTITY')
        if len(frame['actors']) != len(ids) or {str(row['profileId']) for row in frame['actors']} != ids:
            failures.append('ENROLLED_IDENTITY_MISSING')
        for actor in frame['actors']:
            for prefix in ('','current.'):
                if actor.get(prefix+'nativeFirstUnprovenReason','NONE') != 'NONE' or actor.get(prefix+'nativeFirstIncident',''):
                    failures.append('WHOLE_FLOW_NATIVE_SAFETY')
        if i:
            gaps.append((int(frame['sampleNanos'])-int(frames[i-1]['sampleNanos']))/1e9)
    if not frames or int(frames[0]['sampleNanos']) != begin or any(not 0 < gap <= 5 for gap in gaps):
        failures.append('WHOLE_FLOW_TELEMETRY_GAP')
    if len(ids) not in range(4,9) or len(set(primary_ids)) != 2 or not set(primary_ids) <= ids:
        failures.append('FIXED_ENROLLMENT')
    if float(markers[-1]['elapsedFromStart']) > 480:
        failures.append('EPISODE480_BOUND')
    path = read(args.path)
    moves = sorted(root.glob('[0-9][0-9][0-9][0-9]-MOVE_SELF.json'))
    points = path['steps'] + list(reversed(path['steps'][:-1])) + [path['origin']]
    if len(moves) != len(points):
        failures.append('NATIVE_ROUTE_INCOMPLETE')
    arrivals = []
    for move, point in zip(moves, points):
        result = read(move)
        arrival_path = root/f'arrival-{int(move.name[:4])}.json'
        arrival = read(arrival_path)['observer']
        exact = result['status'] == 'ACCEPTED' and result['runId'] == run and int(result['actorObjectId']) == 268492939 and arrival['runId'] == run and not arrival['moving'] and ((int(arrival['x'])-int(point['x']))**2+(int(arrival['y'])-int(point['y']))**2)**0.5 <= 32 and abs(int(arrival['z'])-int(point['z'])) <= 48
        arrivals.append({'requestId':result['requestId'], 'sequence':result['sequence'], 'actualXYZ':[arrival['x'],arrival['y'],arrival['z']], 'arrived':exact})
        if not exact:
            failures.append('ACCEPTED_NOT_ARRIVED')
    early = durable(root/'away-early') if (root/'away-early/durable-decoded.tsv').exists() else {}
    late = durable(root/'away-late') if (root/'away-late/durable-decoded.tsv').exists() else {}
    post = read(root/'all-samples.json') if (root/'all-samples.json').exists() else []
    actors = []
    for primary in primaries:
        pid = str(primary['profileId'])
        initial_epoch = int(primary['materializedAtNanos'])
        issues = []
        absent = phases.get('BACKGROUND_ABSENT')
        if absent is None:
            issues.append('NATIVE_ABSENCE_UNPROVEN')
        else:
            actor = next(row for row in absent['actors'] if str(row['profileId']) == pid)
            if actor.get('worldPresent') != 'false' or actor.get('observerPrewarm') != 'false' or actor.get('observerNativeVisible') != 'false':
                issues.append('NATIVE_ABSENCE_OR_DEMAND')
        receipts = []
        for source in (root/'full-native').glob('*-finalized.properties'):
            p = properties(source)
            if p.get('profileId') != pid or int(p['epoch']) != initial_epoch:
                continue
            native = tables(source.with_name(source.name+'.sql.tsv'))
            components = next(rows for query, rows in native.items() if 'FROM phantom_profile_components' in query)
            state = next((row for row in components if row['component_type']=='background.state'), None)
            receipts.append({'path':str(source), 'finalizedHook':p.get('checkpointStage')=='FINALIZED', 'exactArgument':p.get('exactArgument')=='true', 'exactSqlState':bool(state and int(state['row_version'])==int(p['preparedRowVersion'])+1 and hashlib.sha256(bytes.fromhex(state['payload'])).hexdigest()==p['afterPayloadSha256']), 'pendingOwned':any(row['component_type']=='background.owned-store' for row in components)})
        if not any(row['finalizedHook'] and row['exactArgument'] and row['exactSqlState'] and not row['pendingOwned'] for row in receipts):
            issues.append('EXACT_FINALIZE_SQL_UNPROVEN')
        e, l = early.get(pid,{}), late.get(pid,{})
        context_valid = lambda view: 'state' in view and 'context' in view and 'owned' not in view and view['context'][3]=='COMPLETED' and view['context'][4]==view['state'][1] and view['context'][5]==view['state'][4]
        background_step = bool(context_valid(e) and context_valid(l) and int(l['state'][1])>int(e['state'][1]) and l['context'][8]=='true')
        segments, active, epoch = [], [], None
        for frame in post:
            row = next(actor for actor in frame['actors'] if str(actor['profileId']) == pid)
            if row.get('worldPresent') != 'true' or row.get('dead') != 'false':
                if active: segments.append(segment_result(active, float(post[-1]['elapsedSeconds'])))
                active, epoch = [], None
                continue
            current = int(row['current.nativeEvidenceEpoch'])
            if current != int(row['materializedAtNanos']) or int(row['current.nativeEvidenceObjectId']) != int(primary['objectId']): issues.append('NEW_OWNER_IDENTITY')
            if row['current.nativeEvidenceOverflow']!='false' or row['current.nativeFirstUnprovenReason']!='NONE' or row.get('current.nativeFirstIncident','') or row.get('pendingOwnedStore')!='false' or row.get('cleanupPhase')!='NONE': issues.append('NATIVE_SAFETY')
            if epoch is not None and current != epoch:
                segments.append(segment_result(active,float(post[-1]['elapsedSeconds']))); active=[]
            epoch=current
            active.append((float(frame['elapsedSeconds']),row))
        if active: segments.append(segment_result(active,float(post[-1]['elapsedSeconds'])))
        qualifying = lambda minimum: any(segment['epoch'] != initial_epoch and all(segment['delta'][key]>=minimum for key in ('FarmCycleSequence','KillSequence','RewardSequence','DamageSequence','TargetSequence')) and segment['delta']['ExpGained']>0 and segment['delta']['SpGained']>0 and segment['maxIdleSeconds']<=90 for segment in segments)
        if not qualifying(2): issues.append('POST_RETURN2_OR_NEW_ZONE_LINEAGE_REQUIRED')
        actors.append({'profileId':pid,'enrolledEpoch':initial_epoch,'receipts':receipts,'earlyCanonical':e,'lateCanonical':l,'lawfulBackgroundStep':background_step,'postReturnSegments':segments,'newEpoch5':qualifying(5),'newEpoch2':qualifying(2),'faults':sorted(set(issues))})
    if not any(row['lawfulBackgroundStep'] for row in actors): failures.append('LAWFUL_BACKGROUND_STEP_UNPROVEN')
    if not any(row['newEpoch5'] for row in actors): failures.append('POST_RETURN5_UNPROVEN')
    capture = read(root/'capture-result.json') if (root/'capture-result.json').exists() else {}
    if capture.get('kind')!='Away' or capture.get('runId')!=run or not capture.get('sameSession') or capture.get('telemetryMailboxCommands')!=0: failures.append('CONTROL_CAPTURE_INCOMPLETE')
    sources = [root/'baseline-cohort.json',root/'primary.json',root/'phase-markers.jsonl',root/'full-native/full-cohort-samples.jsonl',args.path]
    report = {'contract':'NATIVE_SOFT_RETURN028','frozenSha':capture.get('sha'),'requiredProfiles':sorted(ids,key=int),'primaries':primary_ids,'runId':run,'phases':[row['phase'] for row in markers],'wholeFlowSamples':len(frames),'maxGapSeconds':max(gaps,default=0),'arrivals':arrivals,'rows':actors,'faults':sorted(set(failures)),'pass':not failures and all(not row['faults'] for row in actors),'postOnlySamplesUsedAsWholeFlow':False,'inputs':[{'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()} for path in sources]}
    with args.output.open('x',encoding='utf-8') as stream: json.dump(report,stream,indent=2)
    print(json.dumps({'pass':report['pass'],'faults':report['faults'],'wholeFlowSamples':len(frames),'rows':[{'profileId':row['profileId'],'faults':row['faults'],'newEpoch5':row['newEpoch5'],'backgroundStep':row['lawfulBackgroundStep']} for row in actors]}))


if __name__=='__main__': main()
