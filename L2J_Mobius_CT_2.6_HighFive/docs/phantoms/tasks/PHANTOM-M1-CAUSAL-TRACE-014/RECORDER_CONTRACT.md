# Recorder contract и обзор semantics

Default OFF: singleton читает existing `PhantomPlayersConfig.settings().diagnosticsEnabled()`, но ring создаётся только после consented Pilot BEGIN. One session, deadline120s, capacity8192, max8 first spatial local-demand candidates, profile0 summaries. Reasons<=96, event/state<=48, thread<=96. Hooks не читают DB/files, не ждут, не захватывают gameplay locks, не логируют.

CAS ring защищён от late writer после wrap; unavailable sequences = attempts-retained, включая overwrite, CAS collision и in-flight at snapshot. END создаёт immutable export snapshot; каждый session имеет отдельные ring/seq/watch set, старый lifecycle context не может записать в новый session.

## Pilot

- `BEGIN_PHANTOM_CAUSAL_TRACE`: admitted REAL_LOGIN/IN_GAME existing consent lease. Session owner — exact sessionId/runId. Никакого arm, spawn либо gameplay action.
- `SNAPSHOT_PHANTOM_CAUSAL_TRACE`: args `afterSeq` (default0), `maxEvents` (1..128, default64); `nextSeq`, `hasMore`, `retained`, `attempts`, `dropped`, `startedNanos`.
- `END_PHANTOM_CAUSAL_TRACE`: остановка записи, immutable snapshot и первая bounded page; дальнейшие SNAPSHOT pages читают этот же snapshot.
- Existing Pilot stop/revoke вызывает `LocalPlayPilotActions.cancelPendingInvitation`; новый recorder.stop выполняется до существующего cleanup, без snapshot/export и без изменения invitation/store decisions.
- Существующий mailbox 64KiB не расширяется: conservative UTF-8×6 XML expansion bound, 40k page budget и reserve metadata.

## Hook review

`PhantomHumanLocalityControl`: existing online predicate вызывается один раз в прежнем месте, его boolean возвращается без изменения. Наблюдение BEFORE eligible-filter нужно для различения raw spatial candidate→OFFLINE и отсутствия topology candidate. Existing _local/_physical updates, preparation callback и signal decisions не изменены. Дополнительные обходы диагностических removed/final candidate facts gated isRecording.

`PhantomScheduler`: return/status/source generation/queue/flags не переписаны. Hooks читают уже доступные fields; requestedStateLocked не вызывается дополнительно. Boundary plan/outcome фиксируются перед existing execution/apply. SCHED_LOCAL_SCAN профильные facts sampled1s; flags: 1 enqueued, 2 localProcessing, 4 processing, 8 workInFlight, 16 boundaryInFlight, 32 unregister, 64 retainedFailure. stateB scan/selected — cached requested; actual target — boundary PLAN. Outcome events не sampled.

`PhantomReconcileFirstActivityPort`: `!presence.isOnline || !locality.isLocal` раскрыт в локальные boolean с тем же short-circuit и порядком: locality читается только при online. Current locality вызывается один раз как раньше. Withdraw/due/defer/success expressions сохранены.

`PhantomMaterializationServiceActivityPort`: delegate вызывается ровно один раз; withProfile сохраняет result reference и исходную exception. Result hook использует уже созданный immutable service snapshot, не делает дополнительный service/Player query.

`PhantomMaterializedPlayer`: только markers после existing boundaries; identity, load, lifecycle, online, spawn, action-monitor statements и catch/cleanup не меняются. Correlation через синхронный calling scope, не новый gameplay owner.

Проверки: focused RED0/3 → GREEN3/3, включая concurrent10000 events, wrap/drop, max8, diagnostics/no-session, ownership/reset, старый lifecycle context, delegate result/exception. Existing scheduler/locality regressions и jar фиксируются отдельными logs. Runtime causality остаётся непроверенной до observe014.
