"""Read-only retained observe020 components using repository binary codec fields."""
import csv
import hashlib
import io
import json
import re
from pathlib import Path
import struct
import subprocess
import sys
from datetime import datetime

if sys.argv[1] == '--evidence':
    task = Path(__file__).resolve().parent
    observation = Path(__file__).resolve().parents[4] / '.phantom-local/observe021/observation'
    baseline = json.loads((observation / 'baseline.json').read_text(encoding='utf-8-sig'))
    samples = [json.loads(path.read_text(encoding='utf-8-sig')) for path in sorted(observation.glob('POLL-*.json')) if re.fullmatch(r'POLL-\d+\.json', path.name)]
    assert samples
    first_time = datetime.fromisoformat(baseline['utc'].replace('Z', '+00:00'))
    for sample in samples:
        sample['reportedElapsedSeconds'] = sample['elapsedSeconds']
        sample['elapsedSeconds'] = (datetime.fromisoformat(sample['utc'].replace('Z', '+00:00')) - first_time).total_seconds()
        assert 0 <= sample['elapsedSeconds'] <= 300
        for key in ('profileId', 'objectId', 'materializedAtNanos', 'nativeEvidenceEpoch'):
            assert sample['census'][key] == baseline['census'][key], ('epoch changed', key)
    final = samples[-1]
    counter_names = ['nativeDamageSequence', 'nativeKillSequence', 'nativeRewardSequence', 'nativeFarmCycleSequence', 'nativeTargetSequence', 'nativeExpGained', 'nativeSpGained', 'nativeLootSequence']
    deltas = {key: int(final['census'][key]) - int(baseline['census'][key]) for key in counter_names}
    cohort_paths = sorted(observation.glob('POLL-*-census-cohort.json'))
    evidence = {
        'result': 'BLOCKED_NATIVE_CONTINUATION', 'database': 'l2jmobiush5_localplay_observe021',
        'codeSha': json.loads((task / 'PUBLICATION.json').read_text(encoding='utf-8-sig'))['codeSha'],
        'runId': (observation / 'run-id.txt').read_text(encoding='utf-8-sig').strip(),
        'initialUtc': baseline['utc'], 'finalUtc': final['utc'], 'actualObservationSeconds': final['elapsedSeconds'],
        'sameEpoch': True, 'sampleCount': 1 + len(samples), 'baseline': baseline, 'samples': samples,
        'counterDeltas': deltas,
        'cohortBaseline': json.loads((observation / 'CENSUS-baseline-cohort.json').read_text(encoding='utf-8-sig')),
        'cohortFinal': json.loads(cohort_paths[-1].read_text(encoding='utf-8-sig')),
        'overflowObserved': any(sample['census']['nativeEvidenceOverflow'] == 'true' for sample in samples),
        'stopReason': 'Repeated adapter autoplay_running versus native autoPlay=false, zero farm progress and native evidence overflow',
        'humanObservation': 'User saw cohort kill one keltir with magic, then stand without continued farming/loot; not attributed to selected profile110 by counters',
        'timestampNote': 'PowerShell automatic DateTime coercion produced erroneous reportedElapsedSeconds in some rows. Corrected durations derive from preserved ISO UTC timestamps; original reported values remain attached. One run, no restart.',
        'runtimeErrorLog': (observation.parent / 'runtime/game/log/error0.log').read_text(encoding='utf-8-sig'),
        'shutdownNativeLog': [line for line in (observation.parent / 'runtime/game/log/java0.log').read_text(encoding='utf-8-sig').splitlines() if 'Phantom World:' in line or 'PhantomSystem' in line],
        'freshInitialComponents': json.loads((task / 'CLONE_INITIAL021.json').read_text(encoding='utf-8-sig')),
        'postStopComponents': json.loads((task / 'POSTSTATE021.json').read_text(encoding='utf-8-sig')),
        'liveAtomicCompare': 'Pre-CAS29 components not captured. Fresh export is28/COMPLETE, final30/PENDING; intervening legitimate history request prevents treating whole interval as immutable local replacement. Exact own replacement immutability is proved by targeted R06/R11, not this broad comparison.',
        'rawEvidence': [{'path': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()} for path in sorted(observation.iterdir()) if path.is_file()]
    }
    (task / 'EVIDENCE021.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: evidence[key] for key in ('result', 'actualObservationSeconds', 'sampleCount', 'sameEpoch', 'counterDeltas', 'overflowObserved')}, ensure_ascii=False))
    sys.exit(0)

class Reader:
    def __init__(self, data): self.data, self.pos = data, 0
    def take(self, count):
        result = self.data[self.pos:self.pos + count]
        assert len(result) == count
        self.pos += count
        return result
    def number(self, fmt): return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]
    def text(self): return self.take(self.number('H')).decode('utf-8')

query = "SELECT profile_id,component_type,row_version,HEX(payload) AS payload_hex FROM phantom_profile_components WHERE profile_id=110 AND component_type IN ('background.catchup','goal.runtime','background.state') ORDER BY component_type"
option = r'C:\Users\ZBook\.codex\worktrees\m1-visible-decision-020\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\observe020\secrets\client.cnf'
mode = sys.argv[2] if len(sys.argv) > 2 else 'retained020'
module = Path(__file__).resolve().parents[4]
if mode == 'snapshot021':
    rows = []
    pattern = re.compile(r"\(110,'(background\.catchup|goal\.runtime|background\.state)',\d+,(\d+),0x([0-9A-Fa-f]+),")
    reading = False
    with (module / '.phantom-local/observe021/play-snapshot.sql').open(encoding='utf-8') as snapshot:
        for line in snapshot:
            if line.startswith('INSERT INTO '): reading = line.startswith('INSERT INTO `phantom_profile_components`')
            if not reading: continue
            rows.extend({'component_type': kind, 'row_version': version, 'payload_hex': payload} for kind, version, payload in pattern.findall(line))
            if len(rows) == 3: break
            if line.rstrip().endswith(';'): reading = False
    assert len(rows) == 3, 'Exact snapshot profile110 components missing'
else:
    assert mode in ('retained020', 'post021')
    database = 'l2jmobiush5_localplay_observe020'
    if mode == 'post021':
        database = 'l2jmobiush5_localplay_observe021'
        option = str(module / '.phantom-local/observe021/secrets/client.cnf')
    result = subprocess.run([r'C:\Program Files\MariaDB 11.4\bin\mariadb.exe', '--defaults-extra-file=' + option, '--batch', database, '-e', query], check=True, capture_output=True, text=True)
    rows = csv.DictReader(io.StringIO(result.stdout), delimiter='\t')
facts = {}
for row in rows:
    data = bytes.fromhex(row['payload_hex'])
    r = Reader(data)
    kind = row['component_type']
    value = {'rowVersion': int(row['row_version']), 'payloadSha256': hashlib.sha256(data).hexdigest()}
    if kind == 'background.catchup':
        assert r.number('I') == 0x50424331 and r.number('I') == 1
        value['status'], value['requestId'] = r.text(), r.text()
        for key in ('seed','from','target','cursor','planOrdinal','intervalOrdinal','generation','knowledgeGeneration','topologyGeneration','goalId','goalRevision'): value[key] = r.number('q')
        value['planIdentity'], value['modelVersion'] = r.text(), r.number('I')
        for key in ('knowledge','topology','progression','commerce','failureReason'): value[key] = r.text()
        assert r.pos == len(data)
    elif kind == 'background.state':
        assert r.number('I') == 0x50424731
        r.take(4)
        value['state'] = ('MATERIALIZED','READY','VERIFY_PENDING','DEAD','INCONSISTENT')[r.number('B')]
    else:
        assert r.number('I') == 0x50475731
        r.take(4)
        value['goalId'], value['revision'], value['statusCode'], value['type'] = r.number('q'), r.number('q'), r.number('B'), r.text()
        def ref(): return [r.text(), r.text()]
        def optional_ref(): return ref() if r.number('B') else None
        def optional_text(): return r.text() if r.number('B') else None
        value['subject'], value['targetRef'] = optional_ref(), optional_ref()
        value['required'], value['current'] = r.number('q'), r.number('q')
        value['acquisitionMethod'] = optional_text()
        value['sources'] = [ref() for _ in range(r.number('B'))]
        value['anchor'] = optional_ref()
        value['purpose'], value['priority'] = r.text(), r.number('H')
        value['risk'], value['expense'], value['deadline'] = r.number('q'), r.number('q'), r.number('q')
        value['constraints'] = {r.text(): r.number('q') for _ in range(r.number('B'))}
        value['reason'] = r.text()
    facts[kind] = value
h, g = facts['background.catchup'], facts['goal.runtime']
if mode == 'retained020': assert h['status'] != 'COMPLETE'
assert h['goalId'] == g['goalId'] and h['goalRevision'] == g['revision'] and g['statusCode'] == 1, (mode, h['status'], h['goalRevision'], g['revision'], g['statusCode'])
facts['normalAdmission'] = h['status'] == 'COMPLETE'
facts['postStopState'] = mode != 'snapshot021'
facts['source'] = mode
text = json.dumps(facts, indent=2, ensure_ascii=False) + '\n'
Path(sys.argv[1]).write_text(text, encoding='utf-8')
print(text)
