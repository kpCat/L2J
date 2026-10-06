import csv
import json
from collections import Counter
from pathlib import Path

task = Path(__file__).resolve().parent
meta = json.loads((task / 'CAUSAL_META.json').read_text(encoding='utf-8-sig'))
with (task / 'CAUSAL_TRACE.tsv').open(encoding='utf-8-sig', newline='') as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
assert len(rows) == int(meta['retained']) == int(meta['attempts']) == 1423
assert int(meta['dropped']) == 0 and meta['active'] == 'false'
assert [int(r['seq']) for r in rows] == list(range(1, len(rows) + 1))
duration = (int(meta['snapshotNanos']) - int(meta['startedNanos'])) / 1e9
assert 0 < duration <= 60
assert (meta['preState'], meta['clientState'], meta['identityOwner']) == ('ARMED_IDLE', 'IN_GAME', 'REAL_LOGIN')
counts = Counter(r['event'] for r in rows)
required = ('READY_ECOLOGY_DUE', 'READY_PASS', 'MATERIALIZE_CALL', 'MATERIALIZE_RESULT', 'MAT_WORLD_SPAWN', 'ECOLOGY_ORPHAN_ADOPTED')
assert all(counts[name] == 0 for name in required)
first = next(r for r in rows if r['event'] == 'READY_PRESENCE_OFFLINE')
assert (first['seq'], first['profileId'], first['reason']) == ('283', '110', 'presence.no_current_local_demand')
candidates = [r for r in rows if r['event'] == 'LOCAL_CANDIDATE']
assert len(candidates) == 392 and all(r['stateA'] == 'OFFLINE' for r in candidates)
summaries = [r for r in rows if r['event'] == 'HUMAN_REFRESH_SUMMARY']
assert len(summaries) == 49 and all(r['value1'] == '1' and r['value3'] == '0' for r in summaries)
assert (task / 'LOGOUT_BEFORE_STOP.tsv').read_text(encoding='utf-8-sig') == (task / 'LOGOUT_AFTER_STOP.tsv').read_text(encoding='utf-8-sig')
assert 'STOCK_GRACEFUL_STOP_CONFIRMED' in (task / 'GRACEFUL_STOP.log').read_text(encoding='utf-8-sig')
with (task / 'CAUSAL_EVENT_COUNTS.tsv').open('w', encoding='utf-8', newline='') as stream:
    writer = csv.writer(stream, delimiter='\t')
    writer.writerow(('event', 'count'))
    writer.writerows(sorted({**counts, **{name: counts[name] for name in required}}.items()))
facts = {'durationSeconds': duration, 'events': len(rows), 'dropped': 0, 'requiredMinimumPass': False,
         'firstLostEdge': first, 'worldPresentNaturalProved': False, 'M1': 'OPEN',
         'postStopSavedCharacterExact': True, 'forceUsed': False}
(task / 'CAUSAL_FACTS.json').write_text(json.dumps(facts, indent=2) + '\n', encoding='utf-8')
print('TRACE_CONTIGUOUS_BOUNDED_REAL_LOGIN=PASS')
print('REQUIRED_RUNTIME_MINIMUM=FAIL_READY_PASS_0_MATERIALIZE_CALL_0')
print('POST_STOP_SAVED_CHARACTER_EXACT=PASS')
