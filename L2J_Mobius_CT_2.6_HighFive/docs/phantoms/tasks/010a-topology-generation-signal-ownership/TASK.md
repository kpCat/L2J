# GOAL 010A — Topology generation and signal ownership hardening

## 1. Identity

- Task: `010a-topology-generation-signal-ownership`
- Branch: `feature/phantom-world`
- Base: `e80a641eebaefb59f1bef6bc398084375d2ecd8d`
- Parent: `0780c77ae605d8b2c36a4ff0345092506fb9f9c5`
- Module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive`
- Test DB only: `l2jmobiush5_phantom_test`
- Production DB: forbidden
- Seed: `20260725001`
- Model: Sol
- Effort: Very High

## 2. Independent gate

```text
Goal 007/007A: ACCEPT
Goal 008/008A: ACCEPT
Goal 009/009A: ACCEPT
Goal 010 architecture direction: ACCEPT
Goal 010 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010A: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Keep the accepted versioned XML topology, canonical hash, factual corpus,
immutable indexes, live-door overlay, explicit profile registry, one-hop
local-chat/combat/targetability semantics, bounded fanout and inert startup.
Do not change production topology XML or the server loaders.

## 3. Findings

### P1 — old-generation profile membership can survive reload

`PhantomTopologyProfileRegistry.update()` captures a query and resolves the
point outside its monitor. A successful reload can install generation N+1 and
clear memberships before that update enters the registry monitor. The update
then commits a node and generation calculated from N after N+1 is active.

### P1 — old-generation event may deliver after reload

Perception event tokens protect STOPPING only. An event may capture generation
N topology/recipients, reload installs N+1, then the old event submits
`ACTIVE`/`NEARBY_PERCEPTIBLE` to the scheduler.

### P1 — successful reload discards all existing resolution

`topologyChanged()` clears every node membership and sets `nodeId=null`.
Registered points and sequences are already owned by the registry and must be
deterministically re-resolved against the new snapshot; they must not remain
unresolved until an unrelated future position update.

### P1 — inactive targetability after unregister does not withdraw

`targetability(active=false)` first requires target topology registration. After
explicit unregister it returns without scheduler withdrawal, allowing an older
`topology.targetability → ACTIVE` to survive until TTL.

### P1 — unregister leaves provider-owned sources

Topology unregister removes membership but does not withdraw:

```text
topology.local_chat
topology.combat
topology.targetability
```

A concurrent event can also precompute the recipient and submit after removal
unless final delivery and unregister cleanup share one ordering gate.

## 4. Goal

Implement and prove:

1. profile resolution and commit belong to one exact topology generation;
2. old-generation update cannot commit after reload;
3. old-generation event cannot deliver after new generation becomes observable;
4. successful reload re-resolves every registered immutable point and preserves
   profile sequences;
5. reload invalidates provider-owned signals before swap;
6. signal invalidation failure leaves old snapshot/hash/generation active;
7. unregister withdraws all three fixed sources;
8. inactive targetability withdraws even after topology unregister;
9. no event submit occurs after unregister's final withdrawals;
10. cleanup failure is explicit and retryable;
11. source sequence cannot overflow negative;
12. stop/reload/update/event ordering is deadlock-free and quiescent;
13. Goal 011/012 remain not started;
14. all cumulative regressions remain GREEN.

## 5. Mandatory reading

Read roadmap, master plan, `Agents.md`, workflow/package/report standards, Goal
010 package/report/contract, Goal 009A closure, scheduler signal semantics and
all current topology production/tests. Read every file in this package.

## 6. Initial audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline e80a641eebaefb59f1bef6bc398084375d2ecd8d
git diff --name-status 0780c77ae605d8b2c36a4ff0345092506fb9f9c5..e80a641eebaefb59f1bef6bc398084375d2ecd8d
```

Expected `HEAD == origin/feature/phantom-world == e80a641e...`.
Preserve unrelated `docs/agent-tasks/**`.

## 7. Fixed generation architecture

### 7.1 Coordinator

`PhantomTopologyService` owns one bounded topology generation coordinator,
preferably a fair `ReentrantReadWriteLock`.

No thread/executor/task/future is created.

Generation read ownership covers:

- profile update resolution through membership commit;
- perception query, recipient capture and scheduler delivery;
- coordinated register/unregister operations;
- generation-consistent read views.

Generation write ownership covers:

- successful reload swap;
- complete registered-point re-resolution;
- old-source invalidation;
- stop exclusion from reload.

Build/validate XML candidate outside the write lock. Never hold service or
registry monitor while waiting for the generation lock or calling scheduler.

### 7.2 Profile update

```text
acquire generation read lease
→ capture exact query/generation
→ resolve point outside registry monitor
→ registry monitor: require current generation matches
→ validate state/profile/sequence
→ commit point, sequence, node, membership and generation atomically
→ release
```

Mismatch retries at most once or returns explicit `TOPOLOGY_CHANGED`; stale
membership is never committed.

### 7.3 Successful reload

```text
build fully validated candidate outside locks
→ acquire generation write lease
→ verify service RUNNING and expected old generation
→ create candidate query
→ capture all registered points/sequences
→ resolve every non-null point against candidate
→ build complete candidate membership
→ invalidate all provider-owned sources for every registered profile
→ only after successful invalidation install snapshot/query/memberships
→ release
```

Profile point and sequence are preserved. A point becomes unresolved only if the
new snapshot genuinely contains no node.

Reload results distinguish:

```text
RELOADED
REJECTED_VALIDATION
REJECTED_SIGNAL_INVALIDATION
NOT_RUNNING
```

Scheduler `BACKPRESSURE`, `REJECTED` or `NOT_RUNNING` during required
invalidation rejects the swap. `NOT_REGISTERED` is acceptable. No automatic
retry.

## 8. Event generation ownership

Each event:

```text
claim bounded event token
→ acquire generation read lease
→ capture query/generation
→ select only registry entries with that exact generation
→ deliver all scheduler signals while lease is held
→ release generation lease
→ release event token
```

Reload write ownership waits for old events. After generation N+1 is visible,
generation N event delivery is impossible.

## 9. Registry generation model

Registry owns current generation. Replace clearing-only `topologyChanged()` with
candidate rebuild/install APIs equivalent to:

```text
CandidateMembership rebuildCandidate(query, generation)
void installCandidate(candidate)
listForNodes(nodeIds, limit, requiredGeneration)
```

Candidate is immutable, bounded by profile capacity and contains only profile
ID, point, sequence, node ID and generation.

## 10. Signal ownership

Provider owns only:

```text
topology.local_chat
topology.combat
topology.targetability
```

Sequences remain monotonic across unregister/re-register and reload. Use
overflow-safe allocation; exhaustion is explicit failure.

### Coordinated unregister

Production callers go through `PhantomTopologyService`, not a publicly mutable
registry.

```text
generation read lease
→ provider delivery gate
→ remove topology membership
→ withdraw all three sources with newer sequences
→ release
```

Result distinguishes:

```text
UNREGISTERED_AND_WITHDRAWN
UNREGISTERED_WITH_SIGNAL_FAILURE
NOT_REGISTERED
NOT_RUNNING
```

A failed withdrawal does not restore membership; expose explicit retry cleanup.

Immediately before every submit, under delivery ordering, require current
registration and exact event topology generation.

### Inactive targetability

`active=false` always attempts a newer targetability withdrawal, even when the
target is no longer topology registered. `active=true` still requires explicit
registration.

## 11. Mutable registry exposure

Remove public mutable `profiles()` access or make it package-private test-only.
Production-facing service methods are equivalent to:

```text
registerProfile
updateProfile
unregisterProfile
retryProfileSignalCleanup
findProfile
listProfiles
```

## 12. Stop

`beginStop` excludes new generation operations and reload, enters provider
STOPPING and rejects new profile/event work. `finishStop=false` while event,
update, reload or cleanup ownership remains in flight. No blocking wait loop.


## 13. Tests

Extend topology core/perception and add:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java
launcher: topology-generation
Ant: phantom-topology-generation-test
```

Mandatory cases:

1. blocked old-query profile update plus successful reload; old generation never
   commits;
2. reload re-resolves an existing point to a different node while preserving its
   sequence;
3. reload makes a point truly unresolved when the new snapshot has no node;
4. event blocked after capturing generation N; reload cannot install N+1 until
   event delivery completes;
5. after reload returns, no old-generation event delivery occurs;
6. stale-generation registry entry excluded from recipients;
7. active targetability followed by topology unregister withdraws all fixed
   sources;
8. inactive targetability after unregister still emits newer withdraw;
9. event precomputed before unregister: final scheduler operation for each source
   is withdraw;
10. unregister cleanup backpressure is explicit;
11. cleanup retry completes with monotonic sequences;
12. reload invalidates old sources before swap;
13. reload invalidation backpressure retains old hash/generation/membership;
14. rejected reload preserves point/sequence/membership;
15. source sequence cannot wrap negative;
16. reload/update/event/stop race has no deadlock;
17. no public mutable registry exposure.

Repeat:

- topology core ×3;
- topology perception ×3;
- topology generation ×3;
- corpus ×2;
- performance ×2;
- navigation core ×3;
- shutdown ×3;
- all decision/scheduler/materialization/headless/profile/DB/harness/skeleton
  routes;
- cumulative verify/jar.

## 14. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomSchedulerRelevanceSignalPort.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java
```

One small generation-coordinator/read-view class in the topology package is
allowed. Minimal `PhantomSystem` compile adjustment is allowed only if service
API names change.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-010a.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md
docs/phantoms/tasks/010a-topology-generation-signal-ownership/**
docs/phantoms/reports/010-topology-anchors-perception-graph.md
docs/phantoms/reports/010a-topology-generation-signal-ownership.md
docs/phantoms/reviews/010-topology-anchors-perception-graph-review.md
```

## 15. Hard out of scope

No production topology XML changes. No MapRegion/Npc/Spawn/Door/World loaders.
No navigation/decision/scheduler/materialization semantic changes. No
Player/Creature/AI/packets, config, DB schema, Game Knowledge/Goal 011, combat
kernel/Goal 012, movement, population, conversation, new executor/raw thread or
per-profile task/Future. No production DB, other chronicle, dependency, CI,
mass formatting, amend/rebase/merge/force push.

## 16. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-010a.ps1
```

Verify:

- base `e80a641e...`, one ordinary exact-scope commit;
- topology XML, config, schema and Goals 011/012 absent;
- server loaders/navigation/decision/scheduler/lifecycle frozen;
- exactly one generation read/write coordinator;
- no service monitor held while waiting for generation lock;
- profile update owns generation through commit;
- successful reload re-resolves all points;
- event owns generation through scheduler delivery;
- recipient list requires exact generation;
- reload invalidates sources before swap;
- invalidation failure retains old generation;
- inactive targetability withdraws without registration;
- unregister withdraws all three fixed sources;
- final submit checks registration/generation;
- no public mutable registry exposure;
- overflow-safe source sequence;
- stop/reload/update/event quiescence;
- no executor/thread/per-profile Future;
- tests/Ant/corpus/performance/docs/encoding/credentials/binaries.

## 17. Documentation

Create:

```text
docs/phantoms/reviews/010-topology-anchors-perception-graph-review.md
docs/phantoms/reports/010a-topology-generation-signal-ownership.md
```

Update Goal 010 report:

```text
Commit: e80a641eebaefb59f1bef6bc398084375d2ecd8d
Parent: 0780c77ae605d8b2c36a4ff0345092506fb9f9c5
Push/remote: exact
Topology core: 38/38 ×3
Perception: 28/28 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Final verifier: 82/82 ×2, byte-identical
Production topology SHA-256:
f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f
Independent review: FIX_REQUIRED
Goal 010A: REQUIRED
Goal 011: BLOCKED
```

The external Goal 010 handoff did not retain a full verifier output SHA. Do not
invent it.

Review verdict:

```text
Goal 010 architecture direction: ACCEPT
Goal 010 commit: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010A: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Roadmap progress only:

```text
Goal 010: FIX_REQUIRED
Goal 010A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: NOT_STARTED / BLOCKED
Goal 012: NOT_STARTED
```

## 18. Commands

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010.ps1

ant compile-tests
ant phantom-topology-core-test
ant phantom-topology-perception-test
ant phantom-topology-generation-test
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

Run core/perception/generation ×3, corpus ×2 and performance ×2.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010a.ps1
git diff --check
```

Post-commit run verify/jar/verifier ×2, push and confirm remote exact.

## 19. Result and commit

Successful result:

```text
TOPOLOGY_GENERATION_SIGNAL_OWNERSHIP_HARDENED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): harden topology generation ownership
```

One ordinary commit over `e80a641e...`. Push regardless of SUCCESS/BLOCKED with
safe scoped artifacts only.

## 20. Blocking behavior

Return `BLOCKED` if generation consistency requires scheduler semantic changes,
signal cleanup requires a background task, lock order cannot be proven without a
new executor/thread, Goal 011/config/schema changes are required, production DB
is accessed or cumulative verify/jar fails.

On blocker remove unsafe edits, preserve safe evidence, commit/push and keep
Goal 011 blocked.

## 21. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 010 review:
Generation coordinator:
Profile update/reload race:
Registered point re-resolution:
Sequence preservation:
Unresolved after reload:
Old-generation event delivery:
Exact-generation recipients:
Reload source invalidation:
Reload invalidation failure:
Topology unregister source cleanup:
Inactive targetability after unregister:
Concurrent event/unregister final ownership:
Signal cleanup retry:
Sequence overflow:
Mutable registry exposure:
Stop/reload/update/event quiescence:
Topology core:
Topology perception:
Topology generation:
Production corpus:
Topology performance:
All regressions:
ant verify:
ant jar:
Verifier final 1/final 2/identical:
Production DB:
JAR topology/test entries:
Commit/parent/branch/push/remote:
Report:
Manual gate:
Goal 011:
Goal 012:
Limitations/blockers:
```
