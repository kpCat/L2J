"""Compare actual stopped canonical exports with both real restarts; no receipt inference."""
import csv
import hashlib
import json
from pathlib import Path

TASK = Path(__file__).resolve().parents[1]
EVIDENCE = TASK / 'evidence'
GROUPS = ('FINAL_SCENE_07C2_F1', 'FINAL_SCENE_07C2_F2', 'FINAL_AWAY_07C2_F3')
TABLES = ('characters.tsv', 'items.tsv', 'skills.tsv', 'durable-hex.tsv', 'durable-decoded.tsv')


def canonical(path):
    # SQL export row order is not a state change. Keep every cell and header.
    lines = path.read_text(encoding='utf-8-sig').splitlines()
    return sorted(lines)


def main():
    result = {'kind': 'ACTUAL_STOPPED_CANONICAL_RESTART_AUDIT', 'groups': [],
              'SEALEDProofClaimed': False, 'completeTransitionLineageClaimed': False}
    for group in GROUPS:
        cohort_path = EVIDENCE / group / 'baseline-cohort.json'
        enrolled = json.loads(cohort_path.read_text(encoding='utf-8-sig'))
        before = EVIDENCE / (group + '_FINAL_SQL')
        for restart in (1, 2):
            after = EVIDENCE / f'{group}_RESTART{restart}_SQL'
            tables = []
            for name in TABLES:
                left, right = before / name, after / name
                tables.append({'name': name, 'beforeSha256': hashlib.sha256(left.read_bytes()).hexdigest(),
                               'afterSha256': hashlib.sha256(right.read_bytes()).hexdigest(),
                               'canonicalEqual': canonical(left) == canonical(right)})
            with (after / 'counts.tsv').open(encoding='utf-8-sig', newline='') as stream:
                counts = list(csv.DictReader(stream, delimiter='\t'))
            manifest = json.loads((after / 'runtime-manifest.json').read_text(encoding='utf-8-sig'))
            row = {'group': group, 'restart': restart, 'requiredProfiles': [int(a['profileId']) for a in enrolled],
                   'counts': counts, 'sourceSha': manifest['codeSha'], 'jarSha256': manifest['gameJarSha256'],
                   'tables': tables, 'pass': all(t['canonicalEqual'] for t in tables)
                   and len(counts) == 1 and counts[0]['database_name'] == 'l2jmobiush5_localplay_contract028f'
                   and counts[0]['pendingOwnedStores'] == '0' and counts[0]['onlineCharacters'] == '0'}
            result['groups'].append(row)
    result['pass'] = all(row['pass'] for row in result['groups'])
    output = EVIDENCE / 'FINAL_RESTART_CANONICAL_AUDIT.json'
    with output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2); stream.write('\n')
    print(json.dumps({'pass': result['pass'], 'groups': len(result['groups']),
                      'changed': [(r['group'], r['restart'], [t['name'] for t in r['tables'] if not t['canonicalEqual']])
                                  for r in result['groups'] if not r['pass']]}))


if __name__ == '__main__':
    main()
