"""Actual same-profile LIFE proof: flags/sleep alone never count as production."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys
from collections import Counter

task = Path(__file__).resolve().parent
module = task.parents[3]
ops = module / '.phantom-local/ops044'
sys.path.insert(0, str(ops / 'validation'))
from raw_archive044 import raw_path

def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def actor(frame, profile): return next(r for r in frame['actors'] if r['profileId'] == str(profile))
def passes(facts):
    return all(facts.get(k) is True for k in ('nativeUseful', 'predepartureFrozen', 'legalSoftRelease',
        'fullOwningSave', 'nativeAbsentDuringBackground', 'realBackgroundInterval',
        'actualProductiveCommittedProducer', 'sameProfileUsefulReturn', 'noRepeatedPositiveOperation'))

parser = argparse.ArgumentParser()
parser.add_argument('--startup', type=Path, required=True)
parser.add_argument('--scene', type=Path, required=True)
parser.add_argument('--conservation', type=Path, required=True)
parser.add_argument('--label', required=True)
args = parser.parse_args()
scene, startup = args.scene.resolve(), args.startup.resolve()
primary = read(scene / 'primary.json')
primary = primary if isinstance(primary, list) else [primary]
assert len(primary) == 1
profile = primary[0]['profileId']
before_frame = read(scene / 'life-native-before.json')
earned_frame = read(scene / 'life-native-earned.json')
before, earned = actor(before_frame, profile), actor(earned_frame, profile)
origin_epoch = before['materializedAtNanos']
native_delta = {k: int(earned[k]) - int(before[k]) for k in ('nativeFarmCycleSequence',
    'nativeRewardSequence', 'nativeDamageSequence', 'nativeExpGained', 'nativeSpGained')}
frames = [json.loads(s) for s in (scene / 'full-native/full-cohort-samples.jsonl').read_text(encoding='utf-8-sig').splitlines()]
phases = {r['phase']: r for r in [json.loads(s) for s in (scene / 'phase-markers.jsonl').read_text(encoding='utf-8-sig').splitlines()]}
absent = phases['BACKGROUND_ABSENT']['sampleNanos']
returned = phases['RETURNED']['sampleNanos']
return_start = phases['RETURN']['sampleNanos']
background_frames = [r for r in frames if absent <= r['sampleNanos'] < return_start]
return_frames = [r for r in frames if returned <= r['sampleNanos'] <= phases['POST_RETURN_DONE']['sampleNanos']]
conservation = read(args.conservation)
ledger = [json.loads(s) for s in (startup / 'lifetimes.jsonl').read_text(encoding='utf-8-sig').splitlines()]
assert ledger[-1]['kind'] == 'RUN_END' and ledger[-1]['dropped'] == 0
commits, terminals = [], []
for r in ledger:
    if r.get('profile_id') != int(profile) or 'raw_file' not in r:
        continue
    path = raw_path(startup, r)
    p = dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
    if p.get('kind') == 'TRANSACTION_COMMIT039' and p.get('componentName') == 'background.state':
        assert p['source'] == 'actual-owning-jdbc-commit' and p['commitOutcome'] == 'SUCCESS'
        assert p['sourceJarSha256'].lower() == conservation['jarSha256']
        commits.append((path, p))
    if p.get('kind') == 'TERMINAL' and p.get('epoch') == origin_epoch:
        terminals.append((path, p))
assert commits
input_file = ops / f'{args.label}-LIFE-receipts.txt'
with input_file.open('x', encoding='utf-8') as stream:
    stream.write(''.join(str(p.resolve()) + '\n' for p, _ in commits))
jar = module / '.phantom-local/contract044a/runtime/libs/GameServer.jar'
command = [r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe',
    '-cp', str(jar) + ';' + str(jar.parent / '*'), str(task / 'Decode-LifeReceipts044.java'), str(input_file)]
decoded_run = subprocess.run(command, capture_output=True, text=True, encoding='utf-8')
(ops / f'{args.label}-LIFE-receipts.tsv').write_text(decoded_run.stdout + decoded_run.stderr, encoding='utf-8')
assert decoded_run.returncode == 0
decoded = {}
for line in decoded_run.stdout.splitlines():
    fields = line.split('\t')
    if fields[1] == 'after' and fields[2] == 'STATE':
        decoded[fields[0]] = dict(profileId=fields[3], exp=int(fields[4]), sp=int(fields[5]), operationKey=fields[6])
productive, repeated, previous, used = [], [], None, set()
for path, p in commits:
    current = decoded[str(path.resolve())]
    if previous and p['producer'] == 'execute' and (current['exp'] > previous['exp'] or current['sp'] > previous['sp']):
        if current['operationKey'] in used:
            repeated.append(current['operationKey'])
        used.add(current['operationKey'])
        if absent <= int(p['commitNanos']) < return_start:
            productive.append(dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                producer=p['producer'], commitNanos=p['commitNanos'], transaction=p['transactionSequence'],
                beforeVersion=p['beforeRowVersion'], afterVersion=p['afterRowVersion'],
                operationKey=current['operationKey'], expDelta=current['exp']-previous['exp'], spDelta=current['sp']-previous['sp']))
    previous = current
useful_return = [actor(r, profile) | {'sampleNanos': r['sampleNanos']} for r in return_frames
    if actor(r, profile)['worldPresent'] == 'true' and actor(r, profile)['materializedAtNanos'] != origin_epoch
    and actor(r, profile).get('nativeEvidenceEpoch') == actor(r, profile)['materializedAtNanos']
    and int(actor(r, profile)['nativeFarmCycleSequence']) > 0 and int(actor(r, profile)['nativeDamageSequence']) > 0]
frozen = read(scene / 'frozen-predeparture044.json')
facts = dict(nativeUseful=all(native_delta[k] > 0 for k in ('nativeFarmCycleSequence','nativeRewardSequence','nativeDamageSequence')),
    predepartureFrozen=str(frozen['profileId']) == str(profile) and frozen['sampleNanos'] <= phases['DEPART']['sampleNanos'],
    legalSoftRelease=len(terminals) == 1 and all(terminals[0][1].get(k) == 'true' for k in ('worldAbsent','autoSaveAbsent','leaseAbsent'))
        and terminals[0][1].get('terminalOutstanding') == '0' and terminals[0][1].get('terminalPendingTimers') == '0',
    fullOwningSave=conservation['FULL_SAVE'] is True and conservation['pass'] is True,
    nativeAbsentDuringBackground=bool(background_frames) and all(actor(r, profile)['worldPresent'] == 'false' for r in background_frames),
    realBackgroundInterval=(return_start-absent)/1e9 >= 75,
    actualProductiveCommittedProducer=bool(productive), sameProfileUsefulReturn=bool(useful_return),
    noRepeatedPositiveOperation=not repeated)
negative = dict(facts, actualProductiveCommittedProducer=False)
assert not passes(negative), 'BACKGROUND flag/elapsed without actual producer must fail'
negative_return = dict(facts, sameProfileUsefulReturn=False)
assert not passes(negative_return), 'Another actor/absent useful same-profile return must fail'
value = dict(contract='TASK044_ACTUAL_FULL_LIFE', G4='PASS' if passes(facts) else 'FAIL',
    profileId=profile, objectId=before['objectId'], originEpoch=origin_epoch, facts=facts,
    nativeEarnedDelta=native_delta, phases={k:r['sampleNanos'] for k,r in phases.items()},
    backgroundElapsedSeconds=(return_start-absent)/1e9, actualProductiveReceipts=productive,
    usefulReturnSamples=[{k:r.get(k) for k in ('sampleNanos','materializedAtNanos','nativeEvidenceEpoch','nativeFarmCycleSequence','nativeDamageSequence','nativeExpGained','nativeSpGained')} for r in useful_return[:3]],
    repeatedPositiveOperations=repeated, terminalPins=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p,_ in terminals],
    negativeNoActualProducer=True, negativeNoUsefulSameProfileReturn=True,
    conservation=dict(path=str(args.conservation),sha256=hashlib.sha256(args.conservation.read_bytes()).hexdigest()),
    rawFrames=dict(path=str(scene/'full-native/full-cohort-samples.jsonl'),sha256=hashlib.sha256((scene/'full-native/full-cohort-samples.jsonl').read_bytes()).hexdigest()),
    producerCounts=dict(Counter(p['producer'] for _,p in commits)), command=command)
output = task / 'evidence' / f'{args.label}-LIFE044.json'
with output.open('x', encoding='utf-8') as stream:
    json.dump(value, stream, ensure_ascii=False, indent=2)
print(json.dumps({k:value[k] for k in ('G4','profileId','facts','backgroundElapsedSeconds')}, ensure_ascii=False))
