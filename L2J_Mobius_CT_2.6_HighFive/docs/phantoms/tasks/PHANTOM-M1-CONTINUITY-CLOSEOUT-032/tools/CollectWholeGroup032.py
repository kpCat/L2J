"""Copy actual latest native receipts; preserve original cohorts and epoch segments."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil

p = argparse.ArgumentParser()
p.add_argument('--cohort', type=Path, action='append', required=True)
p.add_argument('--receipt-root', type=Path, action='append', required=True)
p.add_argument('--sha', required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
allowed = Path(__file__).resolve().parent.parent / 'evidence'
for path in [*a.cohort, *a.receipt_root, a.output]:
    if not path.resolve().is_relative_to(allowed):
        raise ValueError('Exact own TASK032 evidence required')
if a.output.exists() or not 1 <= len(a.cohort) <= 3:
    raise ValueError('Immutable1..3 original whole cohorts required')

def props(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)

groups = [json.loads(path.read_text(encoding='utf-8-sig')) for path in a.cohort]
for group in groups:
    if not 4 <= len(group) <= 8 or len({int(row['profileId']) for row in group}) != len(group):
        raise ValueError('Original natural4..8 whole cohort required')
ids = {int(row['profileId']) for group in groups for row in group}
latest = {}
for root in a.receipt_root:
    for path in root.glob('*-finalized.properties'):
        receipt = props(path)
        pid = int(receipt['profileId'])
        if pid not in ids:
            continue
        if receipt['codeSha'] != a.sha or receipt['source'] != 'native-finalized-snapshot' or receipt['checkpointStage'] != 'FINALIZED' or receipt['nativeOwnerSealed'] != 'true' or receipt['exactArgument'] != 'true':
            raise ValueError('Exact current-source sealed FINALIZED required')
        payload = base64.b64decode(receipt['fullStateBase64'], validate=True)
        if hashlib.sha256(payload).hexdigest() != receipt['afterPayloadSha256']:
            raise ValueError('Actual native full payload digest differs')
        if pid not in latest or int(receipt['hookNanos']) > int(latest[pid][1]['hookNanos']):
            latest[pid] = (path, receipt)
a.output.mkdir()
inventory = []
for index, (source, group) in enumerate(zip(a.cohort, groups), 1):
    output = a.output / f'group{index}'
    sealed = output / 'native-receipts'
    sealed.mkdir(parents=True)
    shutil.copyfile(source, output / 'original-cohort.json')
    storage, missing = [], []
    for actor in group:
        pid = int(actor['profileId'])
        if pid not in latest:
            missing.append(pid)
            storage.append({**actor, 'nativeReceiptMissing': True})
            continue
        path, receipt = latest[pid]
        if int(receipt['objectId']) != int(actor['objectId']):
            raise ValueError('Original persistent native identity differs')
        shutil.copyfile(path, sealed / path.name)
        storage.append({**actor, 'initialMaterializedAtNanos': actor['materializedAtNanos'], 'materializedAtNanos': receipt['epoch']})
        inventory.append(dict(group=index, profileId=pid, initialEpoch=actor['materializedAtNanos'], latestEpoch=receipt['epoch'], epochChanged=str(actor['materializedAtNanos']) != receipt['epoch'], hookNanos=receipt['hookNanos'], receipt=str(path.relative_to(allowed)), receiptSha256=hashlib.sha256(path.read_bytes()).hexdigest(), fullLineageClaimed=False))
    (output / 'storage-cohort.json').write_text(json.dumps(storage, indent=2)+'\n', encoding='utf-8')
    (output / 'missing.json').write_text(json.dumps(missing)+'\n', encoding='utf-8')
(a.output / 'inventory.json').write_text(json.dumps(dict(sourceSha=a.sha, groups=len(groups), requiredProfiles=sorted(ids), rows=inventory, fullLineageClaimed=False), indent=2)+'\n', encoding='utf-8')
print(json.dumps(dict(groups=len(groups), required=len(ids), actualLatest=len(latest), missing=sorted(ids-set(latest)), fullLineageClaimed=False)))
