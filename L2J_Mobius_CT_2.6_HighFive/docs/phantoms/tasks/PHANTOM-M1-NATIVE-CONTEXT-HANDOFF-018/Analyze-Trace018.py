"""Analyze the frozen single-run evidence; never starts a runtime operation."""
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

TASK = Path(__file__).resolve().parent
rows = list(csv.DictReader((TASK / 'CAUSAL_TRACE.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
meta = json.loads((TASK / 'CAUSAL_META.json').read_text(encoding='utf-8-sig'))
assert len(rows) == int(meta['exported']) == int(meta['retained'])
assert len({int(row['seq']) for row in rows}) == len(rows)
assert meta['active'] == 'false'
seconds = (int(meta['snapshotNanos']) - int(meta['startedNanos'])) / 1e9
assert seconds <= 60
counts = Counter(row['event'] for row in rows)
profiles = {}
for profile in sorted({row['profileId'] for row in rows if row['profileId'] != '0'}, key=int):
    events = [row for row in rows if row['profileId'] == profile]
    profiles[profile] = {
        'events': dict(Counter(row['event'] for row in events)),
        'ecologyReasons': dict(Counter(row['reason'] for row in events if row['event'] == 'READY_ECOLOGY_DUE')),
        'schedulerStates': dict(Counter(row['stateA'] for row in events if row['event'] == 'SCHED_LOCAL_SCAN')),
        'firstSeq': events[0]['seq'], 'lastSeq': events[-1]['seq'],
    }
visible = json.loads((TASK / 'VISIBLE_SNAPSHOT_SECOND.json').read_text(encoding='utf-8-sig'))
assert visible['status'] == 'SUCCEEDED'
candidate = visible['candidate']
proof = {
    'result': 'RUNTIME_CAUSAL_CAPTURE_GAP', 'serverSeconds': seconds,
    'events': len(rows), 'dropped': int(meta['dropped']), 'eventCounts': dict(counts),
    'nativeRequiredReadiness': sum(row['event'] == 'READY_ECOLOGY_DUE' and row['stateB'] == 'COMPLETE' and row['reason'] == 'ecology.native_materialization_required' for row in rows),
    'nativeHandoffCalls': sum(row['event'] == 'MATERIALIZE_CALL' and row['reason'] == 'service.NATIVE_CONTEXT_HANDOFF' for row in rows),
    'normalFenceRejects': sum(row['event'] == 'MATERIALIZE_ADMISSION_REJECT' and row['reason'] == 'catchup.normal_fenced' for row in rows),
    'playerLoadBegin': counts['MAT_PLAYER_LOAD_BEGIN'], 'worldSpawn': counts['MAT_WORLD_SPAWN'],
    'profiles': profiles, 'visibleSnapshot': candidate,
    'visibleEpoch': 'UNAVAILABLE_IN_EXISTING_SNAPSHOT',
    'screenshotSha256': hashlib.sha256((TASK / 'CLIENT_VISIBLE.png').read_bytes()).hexdigest(),
    'claim': 'Visible live actor independently proven; the ordered handoff causal chain was not captured. No repeat, forced transition, movement, attack or gameplay repair.',
}
(TASK / 'TRACE_ANALYSIS.json').write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: proof[key] for key in ('result','serverSeconds','events','dropped','nativeRequiredReadiness','nativeHandoffCalls','normalFenceRejects','playerLoadBegin','worldSpawn','visibleEpoch')}))
print('VISIBLE', candidate)
