# GOAL 007A — Scheduler transition ownership hardening

## Identity

- Task: `007a-scheduler-transition-ownership-hardening`
- Branch: `feature/phantom-world`
- Base: `9958edd9e133557f4966eed0a4124e68326401b3`
- Parent: `82a03342e52ff4b6c023b8ea224da8b1c2f6657f`
- Module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive`
- Test DB only: `l2jmobiush5_phantom_test`
- Production DB: forbidden
- Seed: `20260725001`
- Model: Sol
- Effort: Very High

## Independent gate

```text
Stage I: COMPLETE
Goal 006 / 006A / 006B: ACCEPT
Goal 007 architecture direction: ACCEPT
Goal 007 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 007A: REQUIRED
Goal 008 / 009: BLOCKED
```

Keep the accepted five states, explicit registration, bounded signal/ready/due
structures, one shared pulse, fairness, hysteresis, overload cadence, typed
work items, canonical materialization bridge, explicit retained retry, zero
automatic registration/materialization and disabled zero-work behavior.

## Findings to close

### P1 — in-flight unregister orphan

A pulse may be outside the scheduler monitor calling `materialize(profileId)`
while the slot still has effective SLEEPING/WARM/BACKGROUND. Concurrent
`unregister` currently removes that non-materialized slot. A successful late
materialization then has no scheduler owner and is never cleaned.

### P1 — retained state silently cleared

`transitionPlanLocked` checks `requested == effective` before retained-failure
state. Signal update, withdrawal or TTL expiry can therefore erase the explicit
retry requirement without releasing retained lifecycle ownership.

### P1 — false ACTIVE after cleanup retry

A successful retained-dematerialization `retryCleanup` removes the canonical
actor. Generic success handling may nevertheless copy a newer requested ACTIVE
or NEARBY directly into effective state without a fresh materialize call.

### P1 — adapter trusts status instead of ownership

Specific materialization results such as `WORLD_OBJECT_IDENTITY_BUSY` may carry
a retained service entry. The adapter classifies only
`MATERIALIZATION_FAILED_RETAINED` as retained and may start automatic retries.

### P1/P2 — STOPPING is not quiescent

A pulse already outside the monitor may start/deliver work after `beginStop`,
and `finishStop` may clear slots without proving pulse/external-call quiescence.

## Required architecture

### Slot in-flight ownership

Add bounded slot markers equivalent to:

```text
processing
boundaryInFlight
boundaryGeneration
```

No Future/thread/executor/history per slot.

- Set boundary-in-flight under `_monitor` before leaving it for a lifecycle port.
- Clear it under `_monitor` in every outcome.
- Never physically remove a slot while processing or boundary-in-flight.
- Unregister during either marker becomes pending, clears live signal values,
  requests SLEEPING and ensures one coalesced next opportunity.
- A late successful promotion must be followed by dematerialization before slot
  removal.
- Remove only when terminal non-materialized, no retained failure and no
  in-flight work.

### Retained failure precedence

Check retained failure before requested/effective equality, grace and ordinary
transition logic.

Signals may change requested state but never clear retained ownership or the
explicit-retry requirement. Only successful explicit `retryCleanup` may clear
it.

### Truth after cleanup retry

After retry cleanup success:

1. lifecycle ownership must be absent;
2. canonical materialization must be absent;
3. clear retained state;
4. if requested is WARM/BACKGROUND/SLEEPING, use that truthful effective state;
5. if requested is ACTIVE/NEARBY, set effective SLEEPING and schedule one
   immediate fresh materialize opportunity;
6. never publish ACTIVE/NEARBY directly from cleanup success;
7. pending unregister removes only after cleanup.

### Actual ownership classification

Extend the narrow materialization port with an equivalent of:

```java
boolean hasLifecycleOwnership(long profileId)
```

It is true whenever the materialization service retains an entry in any state.

Adapter mapping:

```text
SUCCESS, or ALREADY_ACTIVE with exact ACTIVE actor
    -> SUCCESS
service still owns profile
    -> RETAINED_FAILURE
otherwise
    -> TRANSIENT_BLOCK
```

For cleanup/retry, success is valid only when service ownership is absent.
Expose no Player, Entry or mutable map.

### Stop quiescence

Add one scheduler-wide `pulseInFlight` marker.

- A pulse claims/releases it under `_monitor`; overlapping pulse returns.
- `beginStop` sets STOPPING and cancels the sole recurring future.
- After STOPPING no new lifecycle boundary or work-sink call begins.
- An already-started external call may finish and be reconciled.
- `finishStop` returns false without clearing state while any pulse, processing
  or boundary call is in flight.
- A later explicit `finishStop` after quiescence clears slots/queues/due data.
- Do not wait while holding the global monitor.

`PhantomSystem.shutdown()` must check `finishStop()`:
- true -> STOPPED;
- false -> FAILED/configured instance retained for later explicit shutdown.

Goal 006B two-phase server shutdown remains unchanged.

## Mandatory tests

Extend scheduler tests to at least 17 explicit cases.

1. **In-flight promotion + unregister**
   - block materialize on latch;
   - unregister concurrently;
   - slot remains `UNREGISTER_PENDING`;
   - after success a dematerialize occurs;
   - slot removed only after ownership is absent.
   - Repeat with retained materialization and explicit cleanup retry.

2. **Retained dematerialization + newer ACTIVE**
   - retained failure remains despite requested==effective;
   - no automatic retry;
   - explicit cleanup leaves non-materialized effective state;
   - a fresh materialize is required before ACTIVE returns.

3. **Retained materialization + withdrawal/expiry to SLEEPING**
   - retained status survives equality;
   - explicit retry clears service ownership;
   - stable SLEEPING afterward.

4. **Real adapter retained collision**
   - use guarded DB and existing pre-spawn World-object injection;
   - specific collision result retains service entry;
   - adapter returns RETAINED_FAILURE;
   - scheduler requires explicit retry;
   - no automatic materialize loop;
   - remove residue, retry cleanup, then fresh promotion succeeds.

5. **Stop race**
   - block boundary and separately block work sink;
   - call beginStop;
   - no new boundary/work begins;
   - finishStop false while in flight and snapshots retained;
   - release, pulse quiesces, later finishStop succeeds;
   - no future/queue/due/slot residue.

Regression:
- scheduler suite ×3;
- scale smoke ×2 with identical summary;
- production materialization ×3;
- shutdown handoff ×3;
- headless/profile/DB/harness/skeleton/performance;
- `ant verify`, `ant jar`.

## Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityTransitionStatus.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityResultCategory.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
tools/phantoms/verify-task-007a.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md
docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/**
docs/phantoms/reports/007-shared-activity-scheduler.md
docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md
docs/phantoms/reviews/007-shared-activity-scheduler-review.md
```

## Hard out of scope

No config/schema/profile/lifecycle-core/identity/recovery changes. Do not change
Player, GameClient, Disconnection or World. No auto registration, topology,
goals, Utility AI, plan executor, population, schedules, navigation, combat,
economy, Semantic Pack, Goal 008 or Goal 009. No new executor/raw production
thread/per-profile task. No other chronicles, dependencies, CI, mass formatting,
amend/rebase/merge/force push.

## Documentation

Create:

```text
docs/phantoms/reviews/007-shared-activity-scheduler-review.md
docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md
```

Update Goal 007 report:

```text
Commit: 9958edd9e133557f4966eed0a4124e68326401b3
Parent: 82a03342e52ff4b6c023b8ea224da8b1c2f6657f
Push/remote: exact
Scheduler: 12/12 ×3
Scale: 2/2 ×2
Scale SHA-256:
67B7FC26B98141661890DFAAE5F307B86BB5C768EA82A2DF6A8D1F1556F7EE30
Verifier: 57/57 ×2
Independent review: FIX_REQUIRED
Goal 007A: REQUIRED
Goal 008 / 009: BLOCKED
```

Find the full Goal 007 verifier hash matching prefix `AA5E4956` and suffix
`E05690` only if it exists in retained artifacts; otherwise record the
abbreviated handoff honestly. Do not invent it.

Roadmap progress only:

```text
Goal 007: FIX_REQUIRED
Goal 007A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 008 / 009: NOT_STARTED / BLOCKED
```

## Static verifier

Create deterministic read-only `tools/phantoms/verify-task-007a.ps1` checking:

- base and one ordinary exact-scope commit;
- no config/schema/Goal 008/009;
- Goal 006 lifecycle files frozen;
- retained check precedes requested==effective;
- slot removal guarded by processing/boundary markers;
- cleanup retry never directly publishes ACTIVE/NEARBY;
- adapter uses actual lifecycle ownership;
- real retained-collision integration test;
- one pulse-in-flight marker;
- STOPPING guards boundary/work;
- finishStop refuses in-flight state;
- PhantomSystem checks finishStop result;
- no new executor/raw production thread/per-profile future;
- tests/docs/UTF-8/mojibake/credentials/binaries.

## Commands

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007.ps1

ant compile-tests
ant phantom-activity-scheduler-test
ant phantom-activity-scheduler-performance-smoke
ant phantom-production-materialization-test
ant phantom-server-shutdown-handoff-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Run scheduler ×3, scale ×2 and production materialization ×3.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007a.ps1
git diff --check
```

Commit subject:

```text
fix(phantoms): harden scheduler transition ownership
```

One ordinary commit over `9958edd9...`, push `feature/phantom-world` regardless
of SUCCESS/BLOCKED, without amend/rebase/force push.

Successful result:

```text
ACTIVITY_SCHEDULER_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Goal 008 and Goal 009 remain NOT_STARTED.
