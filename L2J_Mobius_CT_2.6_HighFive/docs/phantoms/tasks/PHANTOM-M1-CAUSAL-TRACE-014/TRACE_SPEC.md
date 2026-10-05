# CAUSAL FLIGHT RECORDER

Existing `PhantomDiagnosticTrace` is not enough: it samples only short names and has no profile/state correlation.

Add a separate observation-only recorder:
`org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder`.

## Activation

Recorder retains nothing unless BOTH:
- `PhantomPlayersConfig.settings().diagnosticsEnabled()` is true;
- a consented LocalPlay Pilot starts a trace session.

Shipped/default behavior unchanged.

Expose bounded Pilot operations:
- `BEGIN_PHANTOM_CAUSAL_TRACE`
- `SNAPSHOT_PHANTOM_CAUSAL_TRACE`
- `END_PHANTOM_CAUSAL_TRACE`

If exact protocol naming must differ, document names.

## Bounds

- one active session;
- first max 8 unique local-demand profiles become watched;
- profileId 0 reserved for global summaries;
- fixed ring <=8192 events;
- bounded reason <=96 chars;
- no DB calls from event hooks;
- no waits/sleeps;
- no file I/O from event hooks;
- no acquisition of existing gameplay locks from recorder;
- snapshot/export only after observation;
- explicit dropped counter.

Prefer AtomicLong + AtomicReferenceArray or equivalent lock-free/bounded design.

## Event fields

`seq,nanoTime,threadName,profileId,event,stateA,stateB,reason,value1,value2,value3`

## Required events

Locality:
HUMAN_REFRESH_BEGIN
HUMAN_REFRESH_SUMMARY
LOCAL_CANDIDATE
LOCAL_CANDIDATE_REMOVED
LOCAL_SIGNAL_RESULT

Scheduler:
SCHED_SIGNAL_ACCEPTED
SCHED_SIGNAL_COALESCED
SCHED_SIGNAL_BACKPRESSURE
SCHED_SIGNAL_REJECTED
SCHED_LOCAL_SCAN
SCHED_LOCAL_PROMOTION_SELECTED
SCHED_BOUNDARY_PLAN
SCHED_BOUNDARY_RESULT

Readiness:
READY_PRESENCE_OFFLINE
READY_NOT_LOCAL
READY_ECOLOGY_DUE
READY_ECOLOGY_DEFER
READY_CURRENT_LOCAL_FALSE
READY_PASS

Materialization:
MATERIALIZE_CALL
MATERIALIZE_RESULT

If PhantomMaterializedPlayer is reached:
MAT_IDENTITY_CLAIMED
MAT_PLAYER_LOAD_BEGIN
MAT_PLAYER_LOAD_OK
MAT_AFTER_PLAYER_LOAD_OK
MAT_ONLINE
MAT_WORLD_SPAWN
MAT_ACTION_ADMISSION_OPEN
MAT_ABORT

No full Player/snapshot serialization.

Tests:
- diagnostics off => no-op;
- diagnostics on but no Pilot session => no retention;
- ring wrap/drop bound;
- only max8 watched profiles;
- session reset;
- concurrent recording safe;
- recorder exception never escapes into gameplay.
