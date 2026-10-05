# FIRST_LOST_EDGE

**SCHED_LOCAL_PROMOTION_SELECTED → ecology/readiness DEFER before materialization**, reason `native_context.required:coalesced`.

This is the one lost edge on the online, human-local causal path. Source observed: `af45c122db3`, observation-only child of base `ccacd6c5bf8fa1234a5559ece708ee3a35efd536`; BAD gameplay source remains `c23915df10239bfab15ae49276e14833268b9afc`.

Successful STATUS was admitted by `LocalPlayPilotService.sessionValid → realClient`, which requires exact attached client IN_GAME, online non-headless Player and REAL_LOGIN owner. Prior session state was ARMED_IDLE; actor=268492939. Position remained 44131/42673/-3488.

| seq | profile | observed edge |
|---:|---:|---|
| 5–16 | global/110/175 | human refresh; one REAL human, 24 final local candidates; watched110/175 ONLINE and physical demand |
| 17–20 | 110/175 | human.local signals ACCEPTED, requested NEARBY_PERCEPTIBLE |
| 22–24 | 110 | SLEEPING → local promotion selected → MATERIALIZE boundary plan |
| 25–27 | 110 | ecology INCOMPLETE → readiness DEFER → MATERIALIZE boundary result DEFERRED, same reason |
| 28–33 | 175 | same promotion/plan → INCOMPLETE/DEFER/DEFERRED |

Full trace seq1–3739 is contiguous, dropped=0, watched=[110,129,142,151,160,175,229,242]. Profiles110/175 produced 112/111 incomplete due outcomes, all with that reason; 223 total. Their 178 local signals were 160 ACCEPTED +18 COALESCED; local promotion selection occurred273 times. No READY_PASS, MATERIALIZE_CALL, MATERIALIZE_RESULT or MAT_* event occurred for watched profiles.

89 human summaries report humanCount=1, localCandidateCount=24, active online World Phantom count=0. This is a sampled World observation, not a claim that no temporary context Player existed between samples. Six other watched spatial candidates were OFFLINE; that calendar eligibility branch does not explain the failure of online110/175 after successful promotion.

Exact current methods:

1. `PhantomHumanLocalityControl.refresh` → `PhantomScheduler.signal`, `localPulseSafely`, `processLocalPromotion` — candidates, signals and selection are proven to pass.
2. `PhantomScheduler.executeBoundary` calls `PhantomReconcileFirstActivityPort.materialize` for the MATERIALIZE plan.
3. `PhantomReconcileFirstActivityPort.installPopulationReadiness` lambda calls `PhantomPopulationEcologyService.requestMaterializationDue`; `materialize` invokes its delegate only for Outcome.SUCCESS.
4. `requestMaterializationDue → requestDue → registerDue → dueSnapshotLocked` returns complete=false and reason `native_context.required:coalesced`; readiness returns DEFERRED and does not invoke `PhantomMaterializationServiceActivityPort.materialize`.
5. Reason producer/direct prerequisite: `PhantomHistoricalBackgroundService.ensureNativeContext`, `requiresNativeMaterialization`; composition `PhantomSystem.start` installs current-demand predicate `presence.isOnline && locality.isCurrentLocal`.

The native-materialization exception in `registerDue` is present at current HEAD but did not produce `ecology.native_materialization_required`. Trace does not identify the particular false guard operand (current-demand, terminal flag, pending request/historical identity/window/status, etc.). `dueSnapshotLocked` reason precedence constrains several earlier inventory/presence failures, but does not prove every exception-branch operand. No guard was bypassed or changed.

Five history suspects are in SUSPECT_COMMITS.tsv; they touch this gate or its direct native-context/demand/pending-request prerequisites. Commit causality is unproven. No offline-filter, World.spawnMe, AutoPlay or generic latest-blocker fix is proposed.

Timing: the passive loop targets90 seconds after BEGIN acknowledgement. Server BEGIN endUtc=22:34:07.046766700Z; SNAPSHOT endUtc=22:35:37.859037700Z; END endUtc=22:35:38.651268600Z. Thus server capture through END is91.6045019 seconds, including transport/snapshot/end overhead. The first export failed because XML readers normalize literal tabs in attributes; all3739 events were recovered from raw frozen mailbox XML without another BEGIN. The original loop stopwatch value was not persisted before that export failure; exact90.000-second server capture is not claimed.

Next step: a bounded fix is not yet justified from this trace alone. If commit attribution is required, use a separately authorized seven-checkpoint behavioral bisect (GOOD, e92d7d4, 6f6dec7, ca2dbc7, dd58a51, 8457b90, BAD), with comparable fresh DB/time/position fixtures. Nothing was bisected or fixed in TASK014. M1=OPEN.
