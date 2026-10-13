"""Curate bounded result metadata; authoritative full raw stays at its immutable own path."""
from pathlib import Path
import hashlib
import json
import re

task = Path(__file__).resolve().parent
evidence = task / 'evidence'
def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def save(name, value):
    path = evidence / name
    encoded = json.dumps(value, ensure_ascii=False, indent=2).encode('utf-8')
    if path.exists():
        assert path.read_text(encoding='utf-8').encode('utf-8') == encoded, 'Immutable derived summary differs: '+name
        return
    with path.open('xb') as stream:
        stream.write(encoded)

def read_log(path):
    data = path.read_bytes()
    return data.decode('utf-16' if data[:2] in (b'\xff\xfe',b'\xfe\xff') else 'utf-8-sig')

cohort = load(evidence / 'LIFE_B1_042/baseline-cohort.json')
save('LIFE_PRIMARY_INPUT042.json', dict(source='LIFE_B1_042/baseline-cohort.json',
     sourceSha256=sha(evidence / 'LIFE_B1_042/baseline-cohort.json'),
     items=[{key: row[key] for key in ('profileId','objectId','dead','npcId','inventorySlots')} for row in cohort]))

tests = []
labels = [('TESTLANE_GREEN042',3),('LIFECYCLE11_RUN042',11),('SOFT_RETURN042',3),
          ('DEBT_NEGATIVE042',8),('DEBT90_RUN042',2),('ACTION_LOOP042',5)]
for label, expected in labels:
    log = evidence / (label + '.log')
    summaries = re.findall(r'SUMMARY: suite=([^\s]+) seed=(\d+) total=(\d+) passed=(\d+) failed=(\d+)',read_log(log))
    assert len(summaries)==1 and tuple(map(int,summaries[0][2:]))==(expected,expected,0), label
    provenance = load(evidence / (label + '.provenance.json'))
    tests.append(dict(label=label,total=expected,passed=expected,failed=0,suite=summaries[0][0],
                      focus=provenance['focus'],jarSha256=provenance['jarSha256'],
                      logSha256=sha(log),provenanceSha256=sha(evidence/(label+'.provenance.json')),
                      qualification='Unsupported debt037 focus ran default continuity8; actual debt2 is DEBT90_RUN042' if label=='DEBT_NEGATIVE042' else 'ACTUAL_CURRENT_JAR_FIRST'))
matrix = evidence / 'CURRENT93_042-matrix.tsv'
rows = matrix.read_text(encoding='utf-8-sig').splitlines()[1:]
assert len(rows)==18 and all(row.split('\t')[4]=='0' for row in rows)
assert sum(int(re.search(r'total=(\d+)',row).group(1)) for row in rows)==93
save('REGRESSIONS042.json',dict(matrixRoutes=18,matrixPassed=93,matrixFailed=0,matrixSha256=sha(matrix),
     tests=tests,chainNegativeCases=26,chainLogSha256=sha(task.parents[3]/'.phantom-local/ops042/CHAIN_NEGATIVES042.log')))

proofs = []
for label in ('A2','A3','A4','A5','B1','B2'):
    path = evidence / (label+'_CONSERVATION042.json')
    value = load(path)
    keys = ('ledgerFrames','commitRecords','nativeBirths','allBirthsTerminal','GLOBAL_DRAIN',
            'NATIVE_BOUNDARY_INTERVAL','FINAL_STOPPED_CUT','FULL_SAVE','pass','failures')
    proofs.append(dict(label=label,source=str(path),sha256=sha(path),bytes=path.stat().st_size,
                      **{key:value[key] for key in keys}))
save('CONSERVATION_INDEX042.json',dict(scope='Original strict039 full-save proof, complete original owning edges, limit4096 unchanged',
     proofs=proofs,overall='NOT_VERIFIED',limitations=['A1 incomplete collector and no exact stopped SQL/typed DONE before next boot',
                                                   'A2 FULL_SAVE=true but GLOBAL_DRAIN=false: typed DONE not captured in old fallback']))

scene = evidence / 'A5_NATIVE_WORLD_INPUT_GROUP_A_042'
current = load(scene / 'continuity-v2.json')
c24 = load(evidence / 'C24_REUSE042.json')
ids = {row['profileId'] for row in current['rows']}
old_ids = {row['profileId'] for row in c24['cohort']}
assert current['pass'] and current['denominator']==8 and current['seconds']>=380
assert not ids & old_ids, 'Independent cohorts have distinct participant identities'
save('GROUPS042.json',dict(G2='PASS',gameJarSha256=c24['jarSha256'],evaluatorSha256=c24['evaluatorSha256'],
     independentDatabase=True,distinctCohortIdentities=True,
     C24=dict(seconds=c24['seconds'],cohort=c24['cohort'],metadataSha256=sha(evidence/'C24_REUSE042.json')),
     A5=dict(seconds=current['seconds'],denominator=current['denominator'],
             rows=[{key:row[key] for key in ('profileId','primary','cycles','pass','faults')} for row in current['rows']],
             originalResultSha256=sha(scene/'continuity-v2.json'),allSamplesSha256=sha(scene/'all-samples.json'),
             prospectiveRuleSha256=sha(scene/'prospective-native-setup042.json')),
     originalC8='FAIL_PRESERVED; profile276 not excluded or re-evaluated'))

life = evidence / 'LIFE_B2_RUNNER_FIXED042'
primary = load(life/'primary.json')
primary = primary[0] if isinstance(primary,list) else primary
pick = lambda path: next(row for row in load(path)['actors'] if row['profileId']==primary['profileId'])
before, earned = pick(life/'life-native-before.json'), pick(life/'life-native-earned.json')
save('LIFE042.json',dict(G4='FAIL',profileId=primary['profileId'],objectId=primary['objectId'],epoch=before['materializedAtNanos'],
     nativeEarnedDelta={key:int(earned[key])-int(before[key]) for key in
                       ('nativeFarmCycleSequence','nativeRewardSequence','nativeDamageSequence','nativeExpGained','nativeSpGained')},
     failure='SOFT_RETIRE_BOUND120',classification='LEGAL_HARD_NATIVE_COMBAT_HOLD',
     actualIncomingDamage='HP180 ->20; ACTIVE_ACTION retained while actual earned attack-hit tickets arrive',
     invisibleLocalityPin=False,newRootsPaused=True,productiveBackground='NOT_VERIFIED',usefulNativeReturn='NOT_VERIFIED',
     actualDeathObserved=False,productFix='NONE; no causal product defect proven',
     failureFrameMetadataSha256=sha(evidence/'LIFE_B2_AI_FAILURE042.json'),
     stockTypedDoneSha256=sha(evidence/'LIFE_B2_RUNNER_FIXED042-FALLBACK-STOP/typed-stop-done.properties')))
print('Bounded summaries: G2 PASS; G4 FAIL; P4 NOT_VERIFIED; actual current regressions pinned.')
