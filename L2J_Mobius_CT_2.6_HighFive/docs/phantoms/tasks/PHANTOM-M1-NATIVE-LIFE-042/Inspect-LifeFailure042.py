"""Read the immutable B2 away interval; distinguish lawful holds from an unproved defect."""
from collections import Counter
from pathlib import Path
import hashlib
import json
import re

task = Path(__file__).resolve().parent
scene = task / 'evidence/LIFE_B2_RUNNER_FIXED042'
phases = [json.loads(line) for line in (scene / 'phase-markers.jsonl').read_text(encoding='utf-8-sig').splitlines()]
away = next(row for row in phases if row['phase'] == 'AWAY_ARRIVED')
path = scene / 'full-native/full-cohort-samples.jsonl'
rows, pins, ticks = [], Counter(), Counter()
for line in path.open(encoding='utf-8-sig'):
    frame = json.loads(line)
    observer = frame['observer']
    if frame['sampleNanos'] < away['sampleNanos'] or not observer['present'] or (observer['x'], observer['y']) != (38131,42673):
        continue
    actor = next(row for row in frame['actors'] if row['profileId'] == '367')
    admission = actor['operatorAdmission']
    pin = re.search(r'retentionPins=\[([^]]*)\]', admission)
    reasons = pin.group(1) if pin else 'UNKNOWN'
    pins[reasons] += 1
    ticks[actor.get('liveAutoPlayTickReason','')] += 1
    rows.append(dict(seconds=(frame['sampleNanos']-away['sampleNanos'])/1e9,
                     sampleNanos=frame['sampleNanos'],world=actor['worldPresent'],
                     xyz=[actor.get(key) for key in ('x','y','z')],
                     physical=actor['physicalDemand'],prewarm=actor['observerPrewarm'],
                     lastHuman=actor['lastHumanNanos'],recentAge=actor['recentHumanAgeSeconds'],
                     pins=reasons,tick=actor.get('liveAutoPlayTickReason'),
                     inCombat=actor.get('inCombat'),attacking=actor.get('attacking'),moving=actor.get('moving'),
                     hp=actor.get('hp'),aiAutoAttacking=actor.get('aiAutoAttacking'),
                     aiAttackTarget=actor.get('aiAttackTarget'),aiAttackTargetDead=actor.get('aiAttackTargetDead'),
                     cycles=actor['nativeFarmCycleSequence'],damage=actor['nativeDamageSequence'],
                     runtime=actor.get('runtimeReason'),
                     current={key:value for key,value in actor.items() if key.startswith('current.') and
                              any(word in key for word in ('Outstanding','outstanding','Pending','pending','OwnerState','FirstIncident','LastCompleted','OwnerWork'))}))
value = dict(contract='TASK042_B2_SOFT_RETIRE_FAILURE', profileId=367,objectId=268487935,
             actualFailure='SOFT_RETIRE_BOUND120',frames=len(rows),pinCounts=dict(pins),tickCounts=dict(ticks),
             samples=[rows[index] for index in sorted({0,len(rows)//4,len(rows)//2,3*len(rows)//4,len(rows)-1})],
             newProductFix='NONE_UNTIL_CAUSAL_RED',
             source=dict(path=str(path),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
output = task / 'evidence/LIFE_B2_AI_FAILURE042.json'
with output.open('x',encoding='utf-8') as stream:
    json.dump(value,stream,indent=2)
print(json.dumps(value,indent=2))
