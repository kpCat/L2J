"""Prepare only stock height differences for the existing restart comparator."""
import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
rows = []
for before, after in ((4, 5), (5, 6)):
    with (ROOT / f'ops/raw/RUN{before}-057-SQL/characters.tsv').open(encoding='utf-8-sig', newline='') as stream:
        prior = {int(row['profileId']): row for row in csv.DictReader(stream, delimiter='\t')}
    proof = json.loads((ROOT / f'CONSERVATION-RUN{after}-057.json').read_text(encoding='utf-8-sig'))
    assert proof['pass'] and not proof['failures']
    seen = set()
    for pin in proof['rawPins']:
        path = Path(pin['file'])
        if not path.name.endswith('-register.properties'):
            continue
        data = path.read_bytes()
        assert hashlib.sha256(data).hexdigest() == pin['sha256']
        record = dict(line.split('=', 1) for line in data.decode('utf-8-sig').splitlines() if '=' in line)
        pid = int(record['profileId'])
        if pid in seen:
            continue
        seen.add(pid)
        if pid in prior and int(record['initialZ']) != int(prior[pid]['z']):
            rows.append('\t'.join((f'{before}->{after}', str(pid), prior[pid]['x'], prior[pid]['y'], prior[pid]['z'], record['initialZ'])))
target = ROOT / 'ops/task057/G5-final-load-height.tsv'
with target.open('x', encoding='utf-8') as stream:
    stream.write('\n'.join(rows) + ('\n' if rows else ''))
print(json.dumps({'heightDifferences': len(rows), 'output': str(target)}))
