"""Copy immutable native receipts for the existing exact persistence validators."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--cohort', type=Path, required=True)
    parser.add_argument('--sealed', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    original = json.loads(args.cohort.read_text(encoding='utf-8-sig'))
    ids = [int(row['profileId']) for row in original]
    if len(ids) != len(set(ids)) or not 4 <= len(ids) <= 8:
        raise ValueError('Whole enrolled cohort required; duplicate IDs forbidden.')
    receipts = {}
    for root in args.sealed:
        for path in root.glob('*.properties'):
            fields = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
            if 'profileId' not in fields:
                continue
            if fields.get('source') == 'native-finalized-snapshot':
                continue
            pid = int(fields['profileId'])
            if pid not in ids:
                continue
            if fields.get('source') != 'native-sealed-snapshot' or fields.get('nativeOwnerSealed') != 'true':
                raise ValueError(f'Native SEALED receipt required: {path}')
            previous = receipts.get(pid)
            version = int(fields['preparedRowVersion'])
            if previous and version == int(previous[1]['preparedRowVersion']) and digest(path) != digest(previous[0]):
                raise ValueError(f'Conflicting exact native receipt: {pid}/{version}')
            if previous is None or version > int(previous[1]['preparedRowVersion']):
                receipts[pid] = (path, fields)
    if set(receipts) != set(ids):
        raise ValueError(f'Missing native receipts: {sorted(set(ids) - set(receipts))}')
    args.output.mkdir(parents=True, exist_ok=False)
    destination = args.output / 'sealed'
    destination.mkdir()
    selected, lineage = [], []
    for actor in original:
        pid = int(actor['profileId'])
        path, fields = receipts[pid]
        shutil.copyfile(path, destination / path.name)
        selected.append({'profileId': pid, 'materializedAtNanos': int(fields['epoch'])})
        lineage.append({'profileId': pid, 'enrolledEpoch': int(actor['materializedAtNanos']),
                        'latestSealedEpoch': int(fields['epoch']), 'preparedRowVersion': int(fields['preparedRowVersion']),
                        'sourcePath': str(path.resolve()), 'sha256': digest(path)})
    (args.output / 'latest-sealed-cohort.json').write_text(json.dumps(selected, indent=2) + '\n', encoding='utf-8')
    (args.output / 'receipt-inventory.json').write_text(json.dumps({'kind': 'LATEST_SEALED_INVENTORY',
        'originalCohortSha256': digest(args.cohort), 'requiredProfiles': ids, 'rows': lineage,
        'completeTransitionLineageClaimed': False}, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'selected': len(selected), 'requiredProfiles': ids, 'output': str(args.output)}))


if __name__ == '__main__':
    main()
