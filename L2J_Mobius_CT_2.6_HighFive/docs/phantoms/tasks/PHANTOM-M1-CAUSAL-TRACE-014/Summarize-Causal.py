"""Read the frozen trace; report event counts and per-profile earliest chain facts."""
import collections
import csv
import json
from pathlib import Path

TASK = Path(__file__).resolve().parent
rows = list(csv.DictReader((TASK / 'CAUSAL_TRACE.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
counts = collections.Counter(r['event'] for r in rows)
print('EVENT_COUNTS', json.dumps(dict(counts), sort_keys=True))
print('GLOBAL', json.dumps([r for r in rows if r['profileId']=='0'][:5]))
for profile in sorted({r['profileId'] for r in rows if r['profileId']!='0'}, key=int):
    subset = [r for r in rows if r['profileId']==profile]
    seen = set()
    first = []
    for r in subset:
        key = (r['event'], r['stateA'], r['stateB'], r['reason'])
        if key not in seen:
            first.append(r)
            seen.add(key)
    print('PROFILE', profile, 'COUNTS', dict(collections.Counter(r['event'] for r in subset)), 'FIRST', json.dumps(first[:16]))
with (TASK / 'CAUSAL_EVENT_COUNTS.tsv').open('w', encoding='utf-8', newline='') as out:
    writer = csv.writer(out, delimiter='\t')
    writer.writerow(['event', 'count'])
    writer.writerows(sorted(counts.items()))
