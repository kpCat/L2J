"""Keep the three historical memberships separate in the current owned clone."""
import csv
import argparse
import hashlib
import json
from pathlib import Path

TASK = Path(__file__).resolve().parents[1]


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--label', default='0726')
    args = parser.parse_args()
    if not args.label.replace('_','').isalnum():
        raise ValueError('Bounded label required')
    output = TASK / f'evidence/LONGITUDINAL_{args.label}_REPORT.json'
    groups = []
    sources = []
    for name in ('old026', 'old027C', 'old027D'):
        membership = TASK / f'evidence/LONGITUDINAL_0726_{name}-membership.json'
        native = TASK / f'evidence/LONGITUDINAL_{args.label}_{name}_NATIVE'
        sql = TASK / f'evidence/LONGITUDINAL_{args.label}_{name}_SQL'
        ids = [int(row['profileId']) for row in json.loads(membership.read_text(encoding='utf-8-sig'))]
        characters = {int(row['profileId']): row for row in rows(sql / 'characters.tsv')}
        census = {int(row['profileId']): row for row in rows(native / 'exact-selected-membership.tsv')}
        admissions = {int(row['profileId']): row['operatorAdmission'] for row in rows(native / 'operator-admission.tsv')}
        canonical = {}
        for line in (sql / 'durable-decoded.tsv').read_text(encoding='utf-8-sig').splitlines():
            fields = line.split('\t')
            canonical.setdefault(int(fields[0]), {})[fields[2]] = fields
        report = []
        for pid in ids:
            if pid not in characters or pid not in census or pid not in admissions or 'state' not in canonical.get(pid, {}):
                raise ValueError(f'Missing historical member: {name}/{pid}')
            report.append({'profileId': pid, 'currentOwnCloneCharacter': characters[pid],
                           'nativeMembership': census[pid], 'operatorAdmission': admissions[pid],
                           'canonical': canonical[pid], 'farmPassInferred': False})
        groups.append({'group': name, 'requiredIds': ids, 'accounted': len(report), 'rows': report})
        for path in (membership, native / 'exact-selected-membership.tsv', native / 'operator-admission.tsv',
                     sql / 'characters.tsv', sql / 'durable-decoded.tsv', sql / 'runtime-manifest.json'):
            sources.append({'path': str(path), 'sha256': digest(path)})
    previous = TASK.parent / 'PHANTOM-M1-LIFECYCLE-COMPLETION-027'
    for path in (previous / 'RESULT.md', previous / 'evidence/FINAL027_OLD026_LONGITUDINAL24.json',
                 previous / 'evidence/R12_D_NATURAL380/baseline-cohort.json'):
        sources.append({'immutableHistoricalSource': str(path), 'sha256': digest(path)})
    with output.open('x', encoding='utf-8') as stream:
        json.dump({'kind': 'HISTORICAL_MEMBERSHIPS_IN_CURRENT_OWN_CLONE', 'groups': groups, 'inputs': sources,
                   'crossCloneEarnedStateEqualityClaimed': False, 'noMembershipReplacement': True}, stream, indent=2)
    print(json.dumps({'accounted': sum(group['accounted'] for group in groups), 'logicalGroups': len(groups)}))


if __name__ == '__main__':
    main()
