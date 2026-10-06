"""Summarize one frozen accepted human-local trace; no database/runtime mutation."""
import ast
import csv
import json
from collections import Counter
from pathlib import Path

TASK = Path(__file__).resolve().parent
events = list(csv.DictReader((TASK / 'CAUSAL_TRACE.tsv').open(encoding='utf-8-sig'), delimiter='\t'))
meta = json.loads((TASK / 'CAUSAL_META.json').read_text(encoding='utf-8'))
watched = ast.literal_eval(meta['watched'])
counts = Counter(e['event'] for e in events)
online = {int(e['profileId']) for e in events if e['event'] == 'LOCAL_CANDIDATE' and e['stateA'] == 'ONLINE'}
local = {int(e['profileId']) for e in events if e['event'] == 'LOCAL_CANDIDATE' and e['stateA'] == 'LOCAL' and e['reason'] == 'physical.demand'}
offline = [e for e in events if e['event'] == 'LOCAL_CANDIDATE' and e['stateA'] == 'OFFLINE']
assert len(events) == int(meta['retained']) == int(meta['exported']) == 1119
assert meta['active'] == 'false' and int(meta['dropped']) == 0
assert set(watched) == online == local == {110,129,142,151,160,175,229,242}
assert not offline and counts['MAT_WORLD_SPAWN'] == 0
seconds = (int(meta['snapshotNanos']) - int(meta['startedNanos'])) / 1e9
assert seconds <= 45
summary = [e for e in events if e['event'] == 'HUMAN_REFRESH_SUMMARY']
assert all(e['value1'] == '1' and e['value2'] == '54' and e['value3'] == '0' for e in summary)
profile = [e for e in events if e['profileId'] == '110']
chain = [e for e in profile if int(e['seq']) <= 44]
assert [e['event'] for e in chain] == ['LOCAL_CANDIDATE','LOCAL_CANDIDATE','SCHED_SIGNAL_ACCEPTED','LOCAL_SIGNAL_RESULT','SCHED_LOCAL_SCAN','SCHED_LOCAL_PROMOTION_SELECTED','SCHED_BOUNDARY_PLAN','READY_ECOLOGY_DUE','READY_PASS','MATERIALIZE_CALL','MATERIALIZE_RESULT','SCHED_BOUNDARY_RESULT']
assert sum(e['event'] == 'MATERIALIZE_RESULT' and e['stateA'] == 'CATCHUP_FENCED' for e in profile) == 11
table = '| Seq | Event | State A/B | Reason |\n| --- | --- | --- | --- |\n'
table += ''.join(f"| {e['seq']} | {e['event']} | {e['stateA']}/{e['stateB']} | {e['reason']} |\n" for e in chain)
(TASK / 'FIRST_LOST_EDGE.md').write_text(f'''# Accepted human-local first lost edge

Profile110 принадлежит watched ONLINE и physical LOCAL cohort. One BEGIN/END,
{seconds}s monotonic, 1119 retained/attempts, dropped0. REAL_LOGIN/IN_GAME/ARMED_IDLE
подтверждены admitted STATUS до BEGIN. Human refresh: 1 human, 54 final candidates,
0 world phantoms, 11 summaries. Watched OFFLINE probes=0.

{table}

Scheduler signal seq20 записывается внутри submit до LOCAL_SIGNAL_RESULT seq21;
таблица сохраняет фактический порядок, а не переставляет synchronous callbacks.

Highest edge: MATERIALIZE_RESULT=CATCHUP_FENCED, 11/11 attempts profile110.
First lost edge: MATERIALIZE_CALL NORMAL → lifecycle.beforeMaterialize admission
отклонён → CATCHUP_FENCED. PhantomMaterializationService.java:222–227 ловит
AdmissionRejectedException до создания/загрузки PhantomMaterializedPlayer.
MAT_* отсутствуют; World spawn на этом пути не достигнут.

PhantomHistoricalBackgroundService.java:977–993 показывает NORMAL fence:
load persistence failure → catchup.persistence_unavailable либо persisted state
blocksNormalOperation → catchup.normal_fenced. Recorder сохраняет typed result,
но не message исключения: конкретный внутренний subreason в trace не доказан.
Из CATCHUP_FENCED не делается вывод о точной persisted state или её происхождении.

Early-stop trigger отдельно: accepted profile160, seq980, десятый
READY_ECOLOGY_DEFER/LOCAL/DEFERRED/catchup.renewal.background_state_invalid.
Профили129/151/160/229/242 имеют этот readiness blocker; profiles110/142/175
проходят READY_PASS и возвращают CATCHUP_FENCED после MATERIALIZE_CALL.
Это разные accepted-path outcomes; background.native_context unrelated OFFLINE
attempts не используются для human-local first lost edge.

Blocker в TASK017 не исправлен. Узкая следующая граница: existing NORMAL lifecycle
admission после READY_PASS; отдельное investigation только по новой задаче.
M1=OPEN. Automatic continuation=false.
''', encoding='utf-8')
with (TASK / 'CAUSAL_EVENT_COUNTS.tsv').open('w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f, delimiter='\t', lineterminator='\n')
    writer.writerow(['event','count'])
    writer.writerows(sorted(counts.items()))
facts = dict(watched=watched, acceptedOnlineLocal=sorted(online & local), offlineProbes=0, finalCandidates=54, seconds=seconds, events=len(events), dropped=0,
             bestProfile=110, highestEdge='MATERIALIZE_RESULT', firstLostReason='CATCHUP_FENCED', repeats=11, naturalWorldSpawn=False,
             stopTrigger='profile160 READY_ECOLOGY_DEFER catchup.renewal.background_state_invalid >=10', counts=dict(counts))
(TASK / 'CAUSAL_FACTS.json').write_text(json.dumps(facts, indent=2)+'\n', encoding='utf-8')
(TASK / 'HANDOFF_RESULT.md').write_text(f'''1. Watched ids: {watched}; все8 accepted human-local ONLINE и physical LOCAL; OFFLINE probes0.
2. Profile110 ordered chain: seq3 ONLINE →12 LOCAL →20 scheduler human.local ACCEPTED →21 LOCAL_SIGNAL_RESULT ACCEPTED →38 promotion →39 MATERIALIZE/ACTIVE →40 ecology COMPLETE →41 READY_PASS →42 MATERIALIZE_CALL NORMAL →43 CATCHUP_FENCED →44 scheduler DEFERRED.
3. Highest edge: MATERIALIZE_RESULT; MAT_* не достигнуты.
4. First lost edge: NORMAL lifecycle.beforeMaterialize admission rejected, typed reason CATCHUP_FENCED, 11 повторов profile110. Внутренний exception message не retained. Early-stop trigger profile160 readiness reason catchup.renewal.background_state_invalid >=10.
5. Natural World: не достигнут в trace; MAT_WORLD_SPAWN0, materialize result worldPresent0, все11 human summaries worldPhantomCount0.
6. Пользователь подтвердил character select; online0 и level12/exp138026/sp13880/x44131/y42673/z-3488 exact до/после stop. Game16016/Login1740 stock graceful, force=false, processes0/ports0, original304 hashes PASS. M1=OPEN, automatic continuation=false.
''', encoding='utf-8')
print(json.dumps(facts))
