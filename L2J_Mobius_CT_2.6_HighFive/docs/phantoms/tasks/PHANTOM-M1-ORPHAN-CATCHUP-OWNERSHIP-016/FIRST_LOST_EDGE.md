# TASK016 first lost live edge

Trace: 2026-10-06T00:04:48.322492500Z → 00:05:38.317420Z; server monotonic49.9970473s; retained1423/attempts1423/dropped0. Manual real player, ARMED_IDLE before BEGIN.

| Seq | Profile | Event | State A/B | Reason |
| --- | --- | --- | --- | --- |
| 278 | 110 | SCHED_SIGNAL_ACCEPTED | SLEEPING/ACTIVE | background.native_context |
| 280 | 110 | SCHED_LOCAL_SCAN | SLEEPING/SLEEPING | STABLE |
| 281 | 110 | SCHED_LOCAL_PROMOTION_SELECTED | SLEEPING/SLEEPING | STABLE |
| 282 | 110 | SCHED_BOUNDARY_PLAN | MATERIALIZE/ACTIVE | execute |
| 283 | 110 | READY_PRESENCE_OFFLINE | OFFLINE/DEFERRED | presence.no_current_local_demand |
| 284 | 110 | SCHED_BOUNDARY_RESULT | MATERIALIZE/DEFERRED | presence.no_current_local_demand |

Existing PhantomReconcileFirstActivityPort.installPopulationReadiness evaluates online=presence.isOnline(profileId), then local=online&&locality.isLocal(profileId). READY_PRESENCE_OFFLINE proves online=false. The guard returns deferred before ecology.requestMaterializationDue. Why presence evaluates false remains unproved; no calendar-policy root cause is inferred.

READY_PRESENCE_OFFLINE: profile110=34,142=31,175=27, total92. Profile175 first seq500. 49 HUMAN_REFRESH_SUMMARY events show humanCount1/localCandidateCount9/worldPhantomCount0. All392 watched LOCAL_CANDIDATE samples OFFLINE. READY_ECOLOGY_DUE/READY_PASS/MATERIALIZE_CALL/MATERIALIZE_RESULT/MAT_WORLD_SPAWN/ECOLOGY_ORPHAN_ADOPTED counts0.

Required minimum READY_PASS + MATERIALIZE_CALL was not reached. No post-call blocker can be reported. No gameplay fixes, second runtime observation, or automatic continuation. M1=OPEN.
