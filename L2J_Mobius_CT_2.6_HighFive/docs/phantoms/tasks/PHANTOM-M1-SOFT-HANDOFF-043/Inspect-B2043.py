"""Finite, immutable B2 reconstruction; unavailable root/source fields stay UNKNOWN."""
from pathlib import Path
from collections import Counter
import hashlib
import json
import re

task = Path(__file__).resolve().parent
module = task.parents[3]
scene = module / 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/evidence/LIFE_B2_RUNNER_FIXED042'
phases = [json.loads(line) for line in (scene / 'phase-markers.jsonl').read_text(encoding='utf-8-sig').splitlines()]
away = next(row for row in phases if row['phase'] == 'AWAY_ARRIVED')
raw = scene / 'full-native/full-cohort-samples.jsonl'
assert hashlib.sha256(raw.read_bytes()).hexdigest() == '52cf75a556fc979ce1681d488cdfd79f83a5d1d17c3ebbd45aa55c12906d6727'
rows = []
for line in raw.open(encoding='utf-8-sig'):
    frame = json.loads(line)
    if frame['sampleNanos'] < away['sampleNanos']:
        continue
    observer = frame['observer']
    if not observer['present'] or (observer['x'], observer['y']) != (38131, 42673):
        continue
    actor = next(a for a in frame['actors'] if a['profileId'] == '367')
    pins = re.search(r'retentionPins=\[([^]]*)\]', actor['operatorAdmission'])
    rows.append(dict(seconds=(frame['sampleNanos']-away['sampleNanos'])/1e9,
        sampleNanos=frame['sampleNanos'], pins=pins.group(1) if pins else 'UNKNOWN',
        **{key: actor.get(key, 'UNKNOWN') for key in ('materializedAtNanos', 'worldPresent', 'physicalDemand',
            'observerPrewarm', 'lastHumanNanos', 'recentHumanAgeSeconds', 'inCombat', 'attacking', 'casting',
            'moving', 'hp', 'aiAutoAttacking', 'aiAttackTarget', 'nativeFarmCycleSequence', 'nativeDamageSequence',
            'liveAutoPlayTickReason', 'current.nativeOwnerOutstanding', 'current.nativeOwnerPendingTimers',
            'current.nativeOwnerWork', 'current.nativeOwnerState', 'current.nativeLastCompletedEvent')}))
paused = [r for r in rows if r['liveAutoPlayTickReason'] == 'locality_retire_paused']
assert paused and {r['nativeFarmCycleSequence'] for r in paused} == {'22'}
assert {r['nativeDamageSequence'] for r in paused} == {'71'}
assert all(r['physicalDemand'] == 'false' and r['worldPresent'] == 'true' for r in rows)
assert float(rows[-1]['hp']) < float(rows[0]['hp'])
value = dict(contract='TASK043_B2_CAUSAL_CLASSIFICATION', profileId=367, objectId=268487935,
    epoch=657223077372300, departure=next(r for r in phases if r['phase'] == 'DEPART')['sampleNanos'],
    awayArrival=away['sampleNanos'], frames=len(rows),
    tickCounts=dict(Counter(r['liveAutoPlayTickReason'] for r in rows)),
    classification={
        'NEW_NATIVE_ROOT': 'UNKNOWN exact last acquired root; outgoing useful counters stop at locality expiry; paused AutoPlay guard observed',
        'EARNED_IN_FLIGHT': 'PRESENT earned attack-hit and original kill completion; incoming/outgoing per-ticket origin absent',
        'INCOMING_COMBAT_HARD_HOLD': 'CONFIRMED HP180 to20.058 with new earned attack-hit; World/same epoch retained',
        'STALE_HOLD_AFTER_SETTLEMENT': 'NOT_PROVEN settlement never observed; incoming damage continues',
        'BACKGROUND_INELIGIBLE': 'CONFIRMED native context owner retained; no release or actual producer reached',
        'EVIDENCE_GAP': 'Exact root acquisition timestamp, every attacker source and native intention not captured in042'},
    sourceOwner={
        'retention': 'Facts.nativeFacts includes isInCombat; permitsNewActions excludes ACTIVE_ACTION alone',
        'autoPlay': 'Policy.acquireTick pauses roots and uses stock IDLE after expiry; earned callbacks retained',
        'native': 'Creature.doAttack schedules EARNED attack-hit for actor and target participants; Creature.isInCombat uses AI attackTarget/autoAttacking',
        'handoff': 'PhantomSystem.nativeContextDemand forbids handoff while outstanding/retention remains'},
    productionCausalRed=False, historicalB2='SOFT_RETIRE_BOUND120 FAIL retained',
    samples=[rows[i] for i in sorted({0, len(rows)//4, len(rows)//2, 3*len(rows)//4, len(rows)-1})],
    raw=dict(path=str(raw), sha256=hashlib.sha256(raw.read_bytes()).hexdigest()))
output = task / 'evidence/B2_CAUSE043.json'
with output.open('x', encoding='utf-8') as stream:
    json.dump(value, stream, ensure_ascii=False, indent=2)
print(f'B2 cause recorded: {len(rows)} frames; legal incoming hard hold; no product RED; root/source UNKNOWN explicit.')
