# Accepted human-local first lost edge

Profile110 принадлежит watched ONLINE и physical LOCAL cohort. One BEGIN/END,
10.5980153s monotonic, 1119 retained/attempts, dropped0. REAL_LOGIN/IN_GAME/ARMED_IDLE
подтверждены admitted STATUS до BEGIN. Human refresh: 1 human, 54 final candidates,
0 world phantoms, 11 summaries. Watched OFFLINE probes=0.

| Seq | Event | State A/B | Reason |
| --- | --- | --- | --- |
| 3 | LOCAL_CANDIDATE | ONLINE/ | topology.eligible |
| 12 | LOCAL_CANDIDATE | LOCAL/ | physical.demand |
| 20 | SCHED_SIGNAL_ACCEPTED | SLEEPING/NEARBY_PERCEPTIBLE | human.local |
| 21 | LOCAL_SIGNAL_RESULT | NEARBY_PERCEPTIBLE/ACCEPTED | human.local |
| 37 | SCHED_LOCAL_SCAN | SLEEPING/ACTIVE | DEFERRED |
| 38 | SCHED_LOCAL_PROMOTION_SELECTED | SLEEPING/ACTIVE | DEFERRED |
| 39 | SCHED_BOUNDARY_PLAN | MATERIALIZE/ACTIVE | execute |
| 40 | READY_ECOLOGY_DUE | LOCAL/COMPLETE | ecology.native_materialization_required |
| 41 | READY_PASS | LOCAL/SUCCESS |  |
| 42 | MATERIALIZE_CALL | / | service.NORMAL |
| 43 | MATERIALIZE_RESULT | CATCHUP_FENCED/ | CATCHUP_FENCED |
| 44 | SCHED_BOUNDARY_RESULT | MATERIALIZE/DEFERRED |  |


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
