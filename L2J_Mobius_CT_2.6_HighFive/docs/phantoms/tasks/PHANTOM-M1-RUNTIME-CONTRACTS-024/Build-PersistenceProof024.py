"""Assemble truthful comparisons from raw immutable receipts, SQL and a healthy drain log."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--cohort', type=Path, required=True)
parser.add_argument('--sealed', type=Path, required=True)
parser.add_argument('--sql', type=Path, required=True)
parser.add_argument('--shutdown-log', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
def rows(name):
    with (args.sql / name).open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))
def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
def digest(files):
    h = hashlib.sha256()
    for path in sorted(files):
        h.update(path.name.encode()); h.update(b'\0'); h.update(path.read_bytes())
    return h.hexdigest()
cohort = json.loads(args.cohort.read_text(encoding='utf-8-sig'))
snapshots = {}
for path in args.sealed.glob('*.properties'):
    p = properties(path); pid = int(p['profileId'])
    if pid not in snapshots or int(p['preparedRowVersion']) > int(snapshots[pid][1]['preparedRowVersion']):
        snapshots[pid] = (path, p)
chars = {int(r['profileId']): r for r in rows('characters.tsv')}
items, skills = rows('items.tsv'), rows('skills.tsv')
contexts, states, pending = {}, {}, set()
for line in (args.sql / 'durable-decoded.tsv').read_text(encoding='utf-8-sig').splitlines():
    row = line.split('\t'); pid = int(row[0])
    if row[2] == 'context': contexts[pid] = row
    elif row[2] == 'state': states[pid] = row
    elif row[2] == 'owned': pending.add(pid)
ints = ('level','exp','sp','expBeforeDeath','x','y','z','heading','classIndex','classId','race','vitality')
floats = ('hp','maxHp','mp','maxMp','cp','maxCp')
comparisons = []
errors = []
for actor in cohort:
    pid = int(actor['profileId'])
    if pid not in snapshots or pid not in chars:
        errors.append(f'profile {pid}: missing sealed snapshot or SQL'); continue
    path, p = snapshots[pid]
    if int(p['epoch']) != int(actor['materializedAtNanos']):
        errors.append(f'profile {pid}: epoch differs'); continue
    expected = {k: int(p[k]) for k in ints} | {k: float(p[k]) for k in floats}
    expected |= {k: p[k] for k in ('inventoryHash','skillsHash')}
    saved = {k: int(chars[pid][k]) for k in ints} | {k: float(chars[pid][k]) for k in floats}
    ih = hashlib.sha256()
    for item in sorted((r for r in items if int(r['profileId']) == pid and r['loc'] in ('INVENTORY','PAPERDOLL')), key=lambda r: int(r['object_id'])):
        for key in ('object_id','item_id','count','loc'):
            ih.update(item[key].encode('ascii')); ih.update(b'\0')
    saved['inventoryHash'] = ih.hexdigest()
    selected_skills = sorted((r for r in skills if int(r['profileId']) == pid and int(r['class_index']) == saved['classIndex']), key=lambda r: int(r['skill_id']))
    saved['skillsHash'] = hashlib.sha256(''.join(f"{r['skill_id']}:{r['skill_level']}\n" for r in selected_skills).encode('ascii')).hexdigest()
    c, s = contexts.get(pid), states.get(pid)
    finalized = bool(c and s and pid not in pending and c[3] == 'COMPLETED' and c[4] == s[1] and c[5] == s[4] and int(s[1]) == int(p['preparedRowVersion']) + 1)
    if expected['vitality'] != saved['vitality']: errors.append(f'profile {pid}: vitality differs')
    comparisons.append(dict(profileId=pid, objectId=int(p['objectId']), epoch=int(p['epoch']), source=p['source'], nativeFinalized=finalized, projectionReady=False, expected=expected, saved=saved, snapshotFile=path.name, nativeContext=c))
counts = rows('counts.tsv')[0]
healthy = 'Phantom World: Initial subsystem drain completed, stopped=true' in args.shutdown_log.read_text(encoding='utf-8-sig')
if not healthy: errors.append('healthy complete subsystem drain not proved')
for row in comparisons: row['projectionReady'] = healthy
manifest = json.loads((args.sql / 'runtime-manifest.json').read_text(encoding='utf-8-sig'))
doc = dict(version=1, sourceSha=manifest['codeSha'], database=counts['database_name'], barrier='QUIESCENT_NATIVE_PREPARE', rawSnapshotSha256=digest([v[0] for v in snapshots.values()]), rawSqlSha256=digest(list(args.sql.glob('*.tsv'))), retainedEntries=0 if healthy else -1, pendingOwnedStores=int(counts['pendingOwnedStores']), forcedStop=False, requiredProfiles=[int(a['profileId']) for a in cohort], rows=comparisons, collectorErrors=errors, retainedZeroEvidence=str(args.shutdown_log))
args.output.write_text(json.dumps(doc, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(dict(collected=len(comparisons), errors=errors)))
sys.exit(2 if errors else 0)
