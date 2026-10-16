"""Offline TASK057 comparisons; uses the existing strict039 CAS chain checker."""
import csv
import hashlib
import json
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'ops/validation'))
from complete_chain045 import check_complete_chain

DB = 'l2jmobiush5_phantom_test_051a'

def props(path):
    return dict(line.split('=', 1) for line in Path(path).read_text(encoding='utf-8-sig').splitlines() if '=' in line)

def table(boot, name):
    with (ROOT / f'ops/raw/RUN{boot}-057-SQL/{name}.tsv').open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))

def components(boot):
    result = {}
    for line in (ROOT / f'ops/raw/RUN{boot}-057-SQL/durable-hex.tsv').read_text(encoding='utf-8-sig').splitlines():
        pid, version, name, payload = line.split('\t')
        result[(int(pid), name)] = (int(version), hashlib.sha256(bytes.fromhex(payload)).hexdigest())
    return result

def records(boot):
    proof = json.loads((ROOT / f'CONSERVATION-RUN{boot}-057.json').read_text(encoding='utf-8-sig'))
    assert proof['pass'] and not proof['failures']
    result = []
    for pin in proof['rawPins']:
        payload = Path(pin['file']).read_bytes()
        assert hashlib.sha256(payload).hexdigest() == pin['sha256']
        result.append(props(pin['file']))
    return result

def useful(boot, frozen):
    label = {5: 'RESTART5_057', 6: 'RESTART6B_057'}[boot]
    frames = json.loads((ROOT / f'ops/raw/{label}/all-samples.json').read_text(encoding='utf-8-sig'))
    grouped = {}
    for frame in frames:
        for actor in frame['actors']:
            pid = int(actor['profileId'])
            if pid not in frozen or actor.get('worldPresent') != 'true': continue
            key = (pid, int(actor['objectId']), int(actor['materializedAtNanos']))
            grouped.setdefault(key, []).append({name: int(actor[name]) for name in ('nativeDamageSequence','nativeRewardSequence','exp','sp')})
    result = []
    for key, rows in grouped.items():
        delta = {name: rows[-1][name] - rows[0][name] for name in rows[0]}
        if delta['nativeDamageSequence'] > 0 and delta['nativeRewardSequence'] > 0:
            result.append(dict(profileId=key[0], objectId=key[1], epoch=key[2], delta=delta))
    return result

historical_epochs = {}
for historical_boot in (1, 2, 3):
    historical_proof = json.loads((ROOT / f'CONSERVATION-RUN{historical_boot}-057.json').read_text(encoding='utf-8-sig'))
    # Historical FAIL stays FAIL. Only individually hash-verified REGISTER facts establish earlier epochs.
    for pin in historical_proof['rawPins']:
        if not Path(pin['file']).name.endswith('-register.properties'):
            continue
        payload = Path(pin['file']).read_bytes()
        assert hashlib.sha256(payload).hexdigest() == pin['sha256']
        old = props(pin['file'])
        assert old['kind'] == 'REGISTER'
        historical_epochs.setdefault(int(old['profileId']), []).append(int(old['epoch']))
all_records = {boot: records(boot) for boot in (4, 5, 6)}
height_rules = {}
for line in (ROOT / 'ops/task057/G5-final-load-height.log').read_text(encoding='utf-8-sig').splitlines():
    if line.startswith('LOAD_HEIGHT050\t'):
        fields = line.split('\t')
        assert fields[8] == 'true' and fields[6] == fields[7]
        height_rules[(fields[1], int(fields[2]))] = tuple(map(int, fields[3:8]))
outputs = []
for before, after in ((4, 5), (5, 6)):
    frozen = {int(x) for x in (ROOT / f'RUN{before}-057-registered-ids.txt').read_text().splitlines()}
    old_chars = {int(r['profileId']): r for r in table(before, 'characters')}
    new_chars = {int(r['profileId']): r for r in table(after, 'characters')}
    old_components, new_components = components(before), components(after)
    old_items, old_skills = table(before, 'items'), table(before, 'skills')
    next_records = all_records[after]
    commits = [r for r in next_records if r.get('kind') == 'TRANSACTION_COMMIT039']
    first_loads = {}
    for record in next_records:
        if record.get('kind') == 'REGISTER':
            first_loads.setdefault(int(record['profileId']), record)
    rows, errors = [], []
    for pid in sorted(frozen):
        prior, current = old_chars.get(pid), new_chars.get(pid)
        if prior is None or current is None or prior['objectId'] != current['objectId']:
            errors.append(f'{pid}: stable full SQL profile/object missing')
            continue
        object_id = int(prior['objectId'])
        chains = {}
        for component in ('background.state', 'background.native-context', 'goal.runtime'):
            start, end = old_components.get((pid, component)), new_components.get((pid, component))
            if start is None and end is None:
                continue
            own = [r for r in commits if int(r['profileId']) == pid and r['componentName'] == component]
            if start is None or end is None:
                chains[component] = {'status': 'GAP', 'reason': 'component creation/removal needs separate proof'}
                errors.append(f'{pid}/{component}: creation/removal')
                continue
            identity = dict(database=DB, profileId=pid, objectId=object_id, component=component)
            origin, latest = dict(identity, version=start[0], digest=start[1]), dict(identity, version=end[0], digest=end[1])
            edges = [dict(identity, beforeVersion=int(r['beforeRowVersion']), afterVersion=int(r['afterRowVersion']),
                beforeDigest=r['beforePayloadSha256'], afterDigest=r['afterPayloadSha256'], status='COMMITTED',
                producer=r['producer'], operationId=r['runId'] + ':' + r['transactionSequence'] + ':' + r['writeIndex'],
                evidenceId=r['runId'] + ':' + r['transactionSequence'] + ':' + r['writeIndex'])
                for r in own if r['beforeRowVersion'] != 'ABSENT' and start[0] <= int(r['beforeRowVersion']) < end[0]]
            checked = check_complete_chain(origin, latest, edges, maximum_links=4096)
            chains[component] = checked
            if checked['status'] not in ('EXACT', 'LINKED'):
                errors.append(f'{pid}/{component}: {checked["status"]}')
        load = first_loads.get(pid)
        differences = {}
        reconciliation = {}
        if load:
            mp_fields = ('load057CapturedNanos', 'load057NativeEpochNanos', 'load057ElapsedNanos', 'load057FirstRegenDelayNanos', 'load057BeforeFirstRegen', 'load057Mp', 'load057MaxMp')
            if any(name not in load for name in mp_fields):
                differences['firstNativeMp'] = 'Missing actual pre-regeneration MP capture'
            else:
                elapsed = int(load['load057ElapsedNanos'])
                if (load['load057BeforeFirstRegen'] != 'true' or elapsed < 0
                        or elapsed >= int(load['load057FirstRegenDelayNanos'])
                        or elapsed != int(load['load057CapturedNanos']) - int(load['load057NativeEpochNanos'])
                        or load['load057NativeEpochNanos'] != load['epoch']):
                    differences['firstNativeMpTiming'] = {name: load[name] for name in mp_fields[:5]}
                native_mp, stopped_mp = Decimal(load['load057Mp']), Decimal(prior['mp'])
                if not native_mp.is_finite() or not stopped_mp.is_finite() or native_mp != stopped_mp:
                    differences['firstNativeMp'] = dict(stopped=prior['mp'], loaded=load['load057Mp'])
            fields = {'initialExp': 'exp', 'initialSp': 'sp', 'initialX': 'x', 'initialY': 'y', 'initialZ': 'z',
                'load045Level': 'level', 'load045ClassIndex': 'classIndex', 'load045ClassId': 'classId', 'load045ExpBeforeDeath': 'expBeforeDeath'}
            for native, sql in fields.items():
                if int(load[native]) != int(prior[sql]):
                    expected_height = height_rules.get((f'{before}->{after}', pid))
                    if native == 'initialZ' and expected_height == (int(prior['x']), int(prior['y']), int(prior['z']), int(load[native]), int(load[native])):
                        reconciliation['initialZ'] = dict(rule='Player.load GeoEngine.getHeight(x,y,storedZ)', stopped=prior[sql], loaded=load[native], independentlyVerified=True)
                        continue
                    differences[native] = dict(stopped=prior[sql], loaded=load[native])
            items = sorted((r for r in old_items if int(r['profileId']) == pid and r['loc'] in ('INVENTORY', 'PAPERDOLL')), key=lambda r: int(r['object_id']))
            expected_items = '|'.join(':'.join(r[k] for k in ('object_id', 'item_id', 'count', 'loc')) for r in items)
            selected_skills = sorted((r for r in old_skills if int(r['profileId']) == pid and int(r['class_index']) == int(prior['classIndex'])), key=lambda r: int(r['skill_id']))
            expected_skills = ''.join(r['skill_id'] + ':' + r['skill_level'] + '|' for r in selected_skills)
            if expected_items != load['load045Items']:
                differences['fullItems'] = dict(stopped=expected_items, loaded=load['load045Items'])
            if expected_skills != load['load045Skills']:
                differences['fullSkills'] = dict(stopped=expected_skills, loaded=load['load045Skills'])
            old_epochs = historical_epochs.get(pid, []) + [int(r['epoch']) for previous_boot in all_records if previous_boot <= before for r in all_records[previous_boot] if r.get('kind') == 'REGISTER' and int(r['profileId']) == pid]
            if int(load['objectId']) != object_id or not old_epochs or int(load['epoch']) <= max(old_epochs) or any(load[k] != 'true' for k in ('ownerCurrentAtRegister', 'worldAbsentAtRegister', 'nativeBirthBoundary')):
                differences['identityEpoch'] = 'invalid native incarnation'
            if differences:
                errors.append(f'{pid}: native load differences')
        rows.append(dict(profileId=pid, objectId=object_id, chains=chains, nativeLoad=bool(load), differences=differences, stockReconciliation=reconciliation))
    def receipts(boot):
        return {(r['profileId'], r.get('nativeEpoch'), r.get('receiptSha256')) for r in all_records[boot]
            if r.get('kind') == 'TRANSACTION_COMMIT039' and r.get('canonicalChecked') == 'true' and r.get('receiptSha256')}
    repeated = receipts(before) & receipts(after)
    receipt_counts = dict(before=len(receipts(before)), after=len(receipts(after)))
    if not all(receipt_counts.values()):
        errors.append('No actual canonical receipt input for replay comparison')
    if repeated:
        errors.append('Previous native canonical receipt applied again')
    native_work = useful(after, frozen)
    if not native_work:
        errors.append('No actual native damage/reward continuation from frozen profile set')
    outputs.append(dict(boundary=f'{before}->{after}', frozenProfiles=len(frozen), nativeLoads=sum(r['nativeLoad'] for r in rows),
        usefulProfiles=native_work, canonicalReceiptCounts=receipt_counts, repeatedCanonicalReceipts=sorted(repeated), rows=rows, errors=errors, passed=not errors))

output = ROOT / 'ops/task057/G5-FINAL-COMPARE057.json'
if output.exists():
    raise ValueError('Immutable comparison already exists')
document = dict(scope='TWO_ORDINARY_SAME_DB_RESTARTS_TASK057', source='original strict039 raw/native-load045/full stopped SQL',
    database=DB, boundaries=outputs, passed=all(x['passed'] for x in outputs))
output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
for row in outputs:
    print(json.dumps({k: row[k] for k in ('boundary', 'frozenProfiles', 'nativeLoads', 'usefulProfiles', 'errors', 'passed')}))
raise SystemExit(0 if document['passed'] else 2)
