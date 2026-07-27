# GOAL 010B — Topology signal-ledger bounds and cleanup truth

## 1. Identity

- Task: `010b-topology-signal-ledger-bounds`
- Branch: `feature/phantom-world`
- Base: `f7eb90ecf3badfc615e6ee700d392a5cbb815811`
- Parent: `e80a641eebaefb59f1bef6bc398084375d2ecd8d`
- Module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive`
- Test DB only: `l2jmobiush5_phantom_test`
- Production DB: forbidden
- Seed: `20260725001`
- Model: Sol
- Effort: Very High

## 2. Independent gate

```text
Goal 009/009A: ACCEPT
Goal 010 architecture direction: ACCEPT
Goal 010A generation/signal ordering: ACCEPT
Goal 010 overall: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Keep the accepted versioned topology, production corpus, canonical hash, factual
validation, bounded spatial/adjacency indexes, exact generation coordinator,
reload re-resolution, one-hop perception, unregister/event ordering and explicit
cleanup retry. Do not change production topology XML or server loaders.

## 3. Independent findings

### P1 — source identity state is unbounded

`PhantomPerceptionProvider` stores a dynamic map:

```java
Map<SequenceKey, Long> _sequences
```

It is cleared only at service stop. Every historically encountered profile may
leave three entries, even after successful topology unregister. Registry
capacity bounds only current registrations, so sequential identity churn grows
memory without a policy bound.

### P1 — failed cleanup tombstones are unbounded

`_pendingCleanup` is a separate uncapped set. Unregister removes the profile from
the bounded registry before adding a failed-cleanup ID, allowing unlimited new
IDs to repeat the cycle while current registry size remains small.

### P1 — inactive targetability allocates for arbitrary IDs

Inactive targetability is correctly allowed after unregister, but currently any
positive never-owned target ID allocates a permanent targetability sequence and
calls the scheduler port. Repeated arbitrary IDs bypass topology profile
capacity completely.

### P1/P2 — every STALE withdrawal is considered cleaned

Scheduler `withdrawSignal` returns `STALE` both when a source is absent and when
the supplied sequence is not newer than an existing source. Cleanup may treat
STALE as success only when the provider's own exact ledger already proves that
source inactive. A possibly-active source must fail closed.

### P2 — impossible submit results are not fail-closed

Unexpected submit `STALE`, `REJECTED` or `NOT_RUNNING` can be returned as a
nominally accepted event. Reserved topology source keys must surface ownership
failure instead of silently pretending delivery state is known.

## 4. Goal

Implement and prove:

1. signal sequence state is bounded independently of lifetime profile-ID churn;
2. pending cleanup uses the same fixed ownership bound;
3. never-owned inactive targetability performs zero allocation and zero port
   call;
4. one fixed per-profile ledger owns exactly three sources;
5. active identities, retained sequence tombstones and failed-cleanup tombstones
   cannot exceed ledger capacity;
6. new identity registration fails explicitly when a ledger cannot be reserved;
7. retained identity re-registration preserves monotonic source sequences;
8. ledger removal requires truthful scheduler-absence evidence or final stop;
9. STALE cleanup succeeds only for locally proven inactive source state;
10. impossible submit statuses are explicit signal ownership failure;
11. Goal 010A generation/reload/unregister ordering remains unchanged;
12. Goal 011/012 remain not started;
13. all cumulative regression gates remain GREEN.

## 5. Mandatory reading

Read roadmap, master plan, `Agents.md`, workflow/package/report standards,
Goal 010/010A packages/reports/reviews/contracts, current topology service,
profile registry, perception provider, relevance port/adapter, exact scheduler
submit/withdraw semantics, all topology suites, and every file in this package.

## 6. Initial audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline f7eb90ecf3badfc615e6ee700d392a5cbb815811
git diff --name-status e80a641eebaefb59f1bef6bc398084375d2ecd8d..f7eb90ecf3badfc615e6ee700d392a5cbb815811
```

Expected `HEAD == origin/feature/phantom-world == f7eb90ec...`.
Preserve unrelated `docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for
unreviewed production/config/schema drift.

## 7. Fixed signal-ledger architecture

Replace `_sequences` and `_pendingCleanup` with one bounded map:

```text
Map<Long, ProfileSignalLedger>
```

Capacity is exactly:

```text
policy.maximumRegisteredProfiles
```

No config key.

Each ledger has fixed fields only:

```text
profileId
localChatSequence
combatSequence
targetabilitySequence
localChatState
combatState
targetabilityState
cleanupPending
cleanupInFlight
```

No dynamic source map, event history, callback, Future or task.

Source state equivalent to:

```text
NEVER_SUBMITTED
POSSIBLY_ACTIVE
INACTIVE_CONFIRMED
OWNERSHIP_UNCERTAIN
```

There are exactly three source slots.

The same capacity includes:

- current topology registrations;
- successfully unregistered IDs whose scheduler source sequence must be retained;
- failed-cleanup tombstones;
- cleanup operations in flight.

Expose aggregate current/peak/capacity metrics only.

## 8. Registration and capacity

A new profile registration reserves a ledger before registry publication.

- Existing retained ledger: reuse it.
- New ID with free ledger capacity: create an empty ledger.
- New ID at capacity: return explicit `SIGNAL_LEDGER_CAPACITY`.
- Failed registry registration removes a just-created empty ledger.
- Cleanup-pending identity returns existing `CLEANUP_PENDING`.

No registry mutation may occur when ledger reservation fails.

## 9. Source truth transitions

### Submit

Allocate the next sequence from the fixed source slot.

```text
ACCEPTED / COALESCED
→ POSSIBLY_ACTIVE
→ normal delivered result

BACKPRESSURE
→ sequence consumed, ownership state unchanged
→ per-recipient backpressure

NOT_REGISTERED
→ sequence consumed, ownership state unchanged
→ per-recipient unregistered

STALE / REJECTED / NOT_RUNNING / SEQUENCE_EXHAUSTED
→ explicit SIGNAL_FAILURE
→ OWNERSHIP_UNCERTAIN where applicable
```

A STALE submit is impossible under exclusive monotonic ownership and is never a
success.

### Withdraw

```text
ACCEPTED / COALESCED
→ INACTIVE_CONFIRMED
→ retain sequence tombstone

NOT_REGISTERED
→ INACTIVE_CONFIRMED
→ scheduler-absence evidence for this cleanup pass

STALE
→ success only if ledger already says INACTIVE_CONFIRMED
→ otherwise OWNERSHIP_UNCERTAIN and cleanup failure

BACKPRESSURE / REJECTED / NOT_RUNNING / SEQUENCE_EXHAUSTED
→ cleanup failure
→ possible-active/uncertain state retained
```

Cleanup is COMPLETE only when all three sources are truthfully inactive.

## 10. Ledger release proof

A ledger may be removed only when:

1. profile is not topology registered;
2. no cleanup is pending/in flight;
3. all three withdrawals in the same final cleanup pass returned
   `NOT_REGISTERED`, proving scheduler profile absence; or
4. final topology service STOPPED clears all ledgers.

`ACCEPTED`, `COALESCED` and `STALE` do not prove scheduler slot absence because
the scheduler may retain a null source entry and its sequence.

This preserves monotonicity while the scheduler profile remains registered, but
allows bounded churn after scheduler removal.

## 11. Inactive targetability

For `active=false`:

- existing ledger: allocate newer targetability sequence and withdraw;
- no ledger: return explicit no-ownership/unregistered outcome;
- never-owned ID: zero ledger allocation and zero scheduler-port call.

For `active=true`, exact topology registration and ledger ownership remain
mandatory.

## 12. Unregister, cleanup retry and reload

Unregister remains ordered under generation read ownership and delivery gate:

```text
registry removal
→ fixed-ledger cleanup pass
→ retain/release ledger by proof
```

Cleanup failure is stored in the ledger, blocks re-registration and is explicitly
retryable. Retry consumes newer fixed sequences.

Reload invalidation:

- uses existing registered ledgers only;
- creates no ledger;
- any uncertain/failed source rejects reload and leaves old generation active;
- successful invalidation preserves ledgers for monotonic post-reload delivery.

No automatic/background retry.

## 13. Stop

`finishStop()` is false while ledger cleanup is in flight.

Final STOPPED clears registry, ledgers and gauges. No scheduler operation starts
after topology STOPPING except an operation that already owned the accepted
generation/delivery boundary before STOPPING.

## 14. Tests

Add:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java
launcher: topology-signal-ledger
Ant target: phantom-topology-signal-ledger-test
```

Mandatory cases:

1. never-owned inactive targetability: zero ledger and zero port call;
2. one registered profile creates one ledger with three fixed slots;
3. unregister with scheduler still present retains one tombstone;
4. re-register retained identity uses newer sequences;
5. all-three NOT_REGISTERED cleanup releases ledger;
6. 100× capacity churn with NOT_REGISTERED cleanup remains bounded;
7. retained-scheduler churn reaches exact cap; next new ID returns
   SIGNAL_LEDGER_CAPACITY;
8. failed-cleanup tombstones count against same capacity;
9. successful retry after ACCEPTED withdrawals retains ledger;
10. retry with all-three NOT_REGISTERED releases ledger;
11. inactive targetability for retained tombstone uses newer sequence;
12. STALE withdrawal while POSSIBLY_ACTIVE fails cleanup;
13. STALE withdrawal while already INACTIVE_CONFIRMED is safe but ledger retained;
14. STALE submit returns SIGNAL_FAILURE;
15. REJECTED/NOT_RUNNING submit returns SIGNAL_FAILURE;
16. current/peak/capacity metrics exact;
17. reload invalidation creates no new ledger and rejects uncertain cleanup;
18. stop clears all ledgers/tombstones;
19. concurrent registration/unregister/event/retry never exceeds cap;
20. no dynamic source map or standalone pending-cleanup set remains.

Repeat signal-ledger, generation, perception and core ×3; corpus ×2;
performance ×2; navigation and shutdown ×3; all decision/scheduler/
materialization/headless/profile/DB/harness/skeleton routes; verify and jar.

## 15. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java
```

One small fixed `PhantomTopologySignalLedger` class is allowed.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-010b.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md
docs/phantoms/tasks/010b-topology-signal-ledger-bounds/**
docs/phantoms/reports/010a-topology-generation-signal-ownership.md
docs/phantoms/reports/010b-topology-signal-ledger-bounds.md
docs/phantoms/reviews/010a-topology-generation-signal-ownership-review.md
```

## 16. Hard out of scope

No production topology XML, loader/query/snapshot/entity changes. No server
loaders. No scheduler implementation/semantics changes. No navigation,
decision, materialization, Player/Creature/AI/packets, config, DB schema,
Game Knowledge/Goal 011, combat kernel/Goal 012, movement/population/
conversation, new executor/raw thread/per-profile Future/task, production DB,
other chronicles/dependencies/CI/mass formatting, amend/rebase/merge/force push.

## 17. Static verifier

Create deterministic read-only `tools/phantoms/verify-task-010b.ps1` proving:

- base and one ordinary exact-scope commit;
- topology XML/config/schema/Goal 011/012 absent;
- loaders/query/snapshot/generation coordinator/scheduler frozen;
- dynamic `_sequences` map removed;
- standalone `_pendingCleanup` set removed;
- one bounded per-profile fixed-source ledger map;
- capacity equals maximumRegisteredProfiles;
- reservation before registry publication and explicit ledger-cap result;
- never-owned inactive target has no allocation/port call;
- truthful submit/withdraw state transitions;
- STALE proof rule;
- ledger release only all-three NOT_REGISTERED or stop;
- cleanup/reload use existing ledgers;
- ledger gauges and churn tests;
- no executor/thread/per-profile Future;
- tests/Ant/corpus/performance/docs/encoding/credentials/binaries.

## 18. Documentation

Create:

```text
docs/phantoms/reviews/010a-topology-generation-signal-ownership-review.md
docs/phantoms/reports/010b-topology-signal-ledger-bounds.md
```

Update Goal 010A report:

```text
Commit: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Parent: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Push/remote: exact
Core: 38/38 ×3
Perception: 28/28 ×3
Generation: 17/17 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation: 50/50 ×3
Shutdown: 7/7 ×3
Verifier: 82/82 ×2, byte-identical
Verifier SHA-256:
5751E0AEED65FB392D36CC66716DC985CE747F4801FDB2A99AA085CA5B72A802
Independent review:
- generation/signal ordering ACCEPT
- bounded signal ledger FIX_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
```

Review verdict:

```text
Goal 010A generation/signal ordering: ACCEPT
Goal 010 overall: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010B: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Roadmap progress only:

```text
Goal 010: FIX_REQUIRED
Goal 010A: ACCEPT_WITH_010B_BOUNDARY
Goal 010B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: NOT_STARTED / BLOCKED
Goal 012: NOT_STARTED
```

## 19. Commands

Run pre-change verify and Goal 010A verifier. Run:

```bat
ant compile-tests
ant phantom-topology-signal-ledger-test
ant phantom-topology-generation-test
ant phantom-topology-perception-test
ant phantom-topology-core-test
ant phantom-topology-production-corpus-test
ant phantom-topology-performance-smoke
ant phantom-navigation-core-test
ant phantom-navigation-performance-smoke
ant phantom-server-shutdown-handoff-test
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
ant phantom-activity-scheduler-test
ant phantom-production-materialization-test
ant phantom-headless-player-test
ant phantom-profile-persistence-test
ant phantom-db-test
ant test
ant phantom-skeleton-test
```

Repeat signal-ledger/generation/perception/core ×3, corpus ×2, performance ×2,
navigation ×3 and shutdown ×3.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010b.ps1
git diff --check
```

Post-commit run verify/jar/verifier ×2, push and confirm remote exact.

## 20. Result and commit

Successful result:

```text
TOPOLOGY_SIGNAL_LEDGER_BOUNDED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): bound topology signal ownership
```

One ordinary commit over `f7eb90ec...`. Push regardless of SUCCESS/BLOCKED with
safe scoped artifacts only.

## 21. Blocking

Return `BLOCKED` if bounded monotonic ownership requires scheduler changes,
never-owned inactive cleanup cannot be distinguished, ledger capacity cannot be
enforced without another unbounded store, Goal 011/config/schema is required,
production DB is accessed or cumulative verify/jar fails.

On blocker remove unsafe production edits, preserve safe evidence, commit/push
and keep Goal 011 blocked.

## 22. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 010A review:
Ledger model/capacity/current/peak:
Never-owned inactive targetability:
Registration reservation:
Re-registration monotonicity:
Scheduler-present cleanup retention:
Scheduler-absent ledger release:
Identity churn bound:
Pending cleanup bound:
STALE active/inactive cleanup:
Impossible submit statuses:
Reload invalidation:
Cleanup retry:
Concurrent capacity:
Stop clearing:
Signal-ledger tests:
Generation/perception/core/corpus/performance:
All regressions:
ant verify/jar:
Verifier final 1/final 2/identical/SHA:
Production DB:
JAR topology/test entries:
Commit/parent/branch/push/remote:
Report/manual gate:
Goal 011:
Goal 012:
Limitations/blockers:
```
