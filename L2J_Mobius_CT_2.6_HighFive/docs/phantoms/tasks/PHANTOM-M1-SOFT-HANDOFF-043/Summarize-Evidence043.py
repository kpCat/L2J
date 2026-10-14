"""Bounded index of immutable043 outcomes; no verdict is inferred from sleep or flags."""
from pathlib import Path
import csv
import hashlib
import json
import re

task = Path(__file__).resolve().parent
module = task.parents[3]
ops = module / '.phantom-local/ops043'
evidence = task / 'evidence'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def pin(path):
    return dict(path=str(path.resolve()), sha256=hashlib.sha256(path.read_bytes()).hexdigest(), bytes=path.stat().st_size)

def write_new(name, value):
    target = evidence / name
    if target.exists():
        assert read(target) == value, 'Immutable summary differs: '+name
        return
    with target.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)

runs = []
for label in ('A1', 'A2', 'B1', 'C1', 'D1'):
    proof_path = ops / f'{label}_CONSERVATION043.json'
    proof = read(proof_path)
    assert proof['pass'] and proof['FULL_SAVE'] and proof['GLOBAL_DRAIN'] and proof['allBirthsTerminal']
    startup = Path(proof['startup'])
    lines = (startup / 'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()
    end = json.loads(lines[-1])
    assert end['kind'] == 'RUN_END' and end['dropped'] == 0 and not end['failure']
    runs.append(dict(label=label, **{key:proof[key] for key in ('nativeBirths', 'commitRecords',
        'FULL_SAVE', 'GLOBAL_DRAIN', 'NATIVE_BOUNDARY_INTERVAL', 'FINAL_STOPPED_CUT', 'allBirthsTerminal', 'pass')},
        closedRunEnd=end, proof=pin(proof_path), ledger=pin(startup/'lifetimes.jsonl'),
        startup=str(startup), scene=proof['scene']))
write_new('CONSERVATION_INDEX043.json', dict(contract='ALL_CLOSED_OWN043_NORMAL_EPISODES',
    finalEpisode='D1', runs=runs, historicalM1G3Transfer=False, **{'pass':True}))

matrix = list(csv.DictReader((evidence/'CURRENT93_043-matrix.tsv').read_text(encoding='utf-8-sig').splitlines(), delimiter='\t'))
total = passed = 0
provenance = []
for route in matrix:
    match = re.search(r'total=(\d+) passed=(\d+) failed=(\d+)', route['summary'])
    assert route['exitCode'] == '0' and match and match[3] == '0'
    total += int(match[1]); passed += int(match[2])
    provenance.append(pin(evidence/f"CURRENT93_043_{int(route['route']):02d}.provenance.json"))
assert total == passed == 93 and len(matrix) == 18
focused = []
for label, count in (('LIFECYCLE11_043',11), ('TESTLANE_GREEN043',3)):
    log = evidence / (label+'.log')
    log_bytes = log.read_bytes()
    log_text = log_bytes.decode('utf-16' if log_bytes.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
    summary = re.findall(r'SUMMARY:.*total=(\d+) passed=(\d+) failed=(\d+)', log_text)[-1]
    assert tuple(map(int, summary)) == (count,count,0)
    focused.append(dict(label=label, passed=count, total=count, failed=0,
        log=pin(log), provenance=pin(evidence/(label+'.provenance.json'))))
jar = module / '.phantom-local/contract043a/runtime/libs/GameServer.jar'
jar_sha = hashlib.sha256(jar.read_bytes()).hexdigest()
for path in [evidence/f"CURRENT93_043_{int(route['route']):02d}.provenance.json" for route in matrix] + [evidence/(r['label']+'.provenance.json') for r in focused]:
    proof = read(path)
    assert not proof['developmentBinFirst'] and proof['jarSha256'].lower() == jar_sha
write_new('REGRESSIONS043.json', dict(jarSha256=jar_sha, jarFirst=True, currentMatrix=dict(total=93,passed=93,failed=0,routes=18),
    focused=focused, matrix=pin(evidence/'CURRENT93_043-matrix.tsv'), provenance=provenance,
    G2Reuse=pin(evidence/'G2_REUSE043.json'), productSourceChanged=False))

negative_log = ops/'CHAIN_NEGATIVES043.log'
negative_bytes = negative_log.read_bytes()
negative_text = negative_bytes.decode('utf-16' if negative_bytes.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
assert re.search(r'Ran 26 tests in [0-9.]+s', negative_text) and negative_text.rstrip().endswith('OK')
write_new('VALIDATOR_NEGATIVES043.json', dict(existingFocusedTests=26,passed=26,failed=0,
    log=pin(negative_log), originalSummary=negative_text.strip().splitlines()[-3:], newTestsCreated=False))

scene = evidence / 'LIFE_D1_043'
primary = read(scene/'primary.json')
primary = primary[0] if isinstance(primary,list) else primary
profile = str(primary['profileId'])
assert profile == '1189'
def actor(frame):
    return next(row for row in frame['actors'] if str(row['profileId']) == profile)
phases = {row['phase']:row['sampleNanos'] for row in map(json.loads,(scene/'phase-markers.jsonl').read_text(encoding='utf-8-sig').splitlines())}
assert set(phases) == {'DEPART','AWAY_ARRIVED'}
frames = [json.loads(line) for line in (scene/'full-native/full-cohort-samples.jsonl').read_text(encoding='utf-8-sig').splitlines()]
away = [frame for frame in frames if frame['sampleNanos'] >= phases['AWAY_ARRIVED']
    and frame['observer']['sessionState'] == 'RUNNING' and frame['observer']['present']
    and (frame['observer']['x'],frame['observer']['y']) == (38131,42673)]
assert away
quiet = [frame for frame in away if actor(frame).get('worldPresent') == 'true'
    and actor(frame).get('physicalDemand') == 'false' and 'retentionPins=[]' in actor(frame)['operatorAdmission']
    and actor(frame).get('current.nativeOwnerOutstanding') == '0'
    and all(actor(frame).get(key) == 'false' for key in ('dead','moving','casting','attacking','inCombat','aiAutoAttacking'))
    and actor(frame).get('incomingTargetCount043') == '0']
assert quiet and quiet[-1] == away[-1]
last = away[-1]
before, earned = actor(read(scene/'life-native-before.json')), actor(read(scene/'life-native-earned.json'))
delta = {key:int(earned[key])-int(before[key]) for key in ('nativeFarmCycleSequence','nativeRewardSequence','nativeDamageSequence','nativeExpGained','nativeSpGained')}
assert all(delta[key]>0 for key in ('nativeFarmCycleSequence','nativeRewardSequence','nativeDamageSequence'))
frozen = read(scene/'frozen-predeparture043.json')
assert str(frozen['profileId']) == profile and frozen['sampleNanos'] <= phases['DEPART']
assert 'activeSignalSources=1' in actor(last)['operatorAdmission']
write_new('D1-LIFE043.json',dict(contract='TASK043_NATIVE_OUTCOME_RED_WITH_CAUSAL_GAP', G4='FAIL',
    profileId=profile, objectId=before['objectId'], originEpoch=before['materializedAtNanos'],
    nativeEarnedDelta=delta, predepartureFrozen=True, phases=phases, SOFT_RETIRE_BOUND120='FAIL',
    facts=dict(nativeUseful=True,legalSoftRelease=False,actualProductiveCommittedProducer=False,sameProfileUsefulReturn=False),
    classification='STALE_HOLD_AFTER_SETTLEMENT_CANDIDATE', causalOwner='UNKNOWN', productOwnerRedProven=False,
    evidenceGap='Snapshot records signal count, not source key/sequence/expiry; NONATOMIC_VOLATILE native reads. No owner fix is justified.',
    quietAwaySamples=len(quiet), firstQuietSampleNanos=quiet[0]['sampleNanos'], lastQuietSampleNanos=last['sampleNanos'],
    quietSampleSpanSeconds=(last['sampleNanos']-quiet[0]['sampleNanos'])/1e9,
    lastAwayFrame=dict(sampleNanos=last['sampleNanos'],observer=last['observer'],actor=actor(last)),
    incomingScope='visible_monsters_with_native_target_within2000;first8; not a whole-world no-incoming proof',
    excludedFallbackFrames=True, noThirdPositiveAttempt=True,
    fullOwningSaveAtNormalFinalStop=True, conservation=pin(ops/'D1_CONSERVATION043.json'),
    rawFrames=pin(scene/'full-native/full-cohort-samples.jsonl'),
    preconditions=pin(task/'PRECONDITIONS043-D1.md'), prospective=pin(evidence/'LIFE_D1_043-PROSPECTIVE.json'),
    sourcePins=pin(evidence/'SOURCE_PINS043-D1.json')))
print('All five closed043 conservation PASS; current93/93 +11/11 +3/3 exact JAR verified; D1 G4 FAIL with explicit causal owner gap.')
