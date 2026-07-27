# GOAL 010C — Topology absent-source reconciliation

## 1. Identifier

- **Task ID:** `010c-topology-absent-source-reconciliation`
- **Type:** mandatory narrow integration closure for Goal 010
- **Branch:** `feature/phantom-world`
- **Starting baseline:** `030184205c6bf2101cb6256086c0b85c0e26dcd4`
- **Parent:** `f7eb90ecf3badfc615e6ee700d392a5cbb815811`
- **Repository root:** `C:\Users\endim\L2J_Mobius\`
- **Only module:** `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- **Test DB:** `l2jmobiush5_phantom_test`
- **Production DB:** `l2jmobiush5` — never use during execution
- **Seed:** `20260725001`
- **Codex model:** Sol
- **Effort:** Very High

## 2. Independent review gate

```text
Goal 009 / 009A: ACCEPT
Goal 010 architecture direction: ACCEPT
Goal 010A generation/signal ordering: ACCEPT
Goal 010B bounded ledger architecture: ACCEPT
Goal 010B real scheduler reconciliation: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Keep all accepted Goal 010–010B work:

- immutable versioned topology and factual production corpus;
- canonical snapshot hash and bounded indexes;
- exact topology-generation ownership;
- reload membership re-resolution;
- event/unregister delivery ordering;
- one bounded fixed-source ledger per profile;
- shared ledger capacity for active/retained/cleanup identities;
- all-three `NOT_REGISTERED` ledger release;
- explicit cleanup retry;
- never-owned inactive targetability zero allocation;
- fail-closed ambiguous scheduler results;
- no movement, concrete actions, Game Knowledge or automatic population.

This task closes one exact real-scheduler integration defect. Do not redesign the
ledger or generation coordinator.

## 3. Finding

### P1 — scheduler source absence is returned as STALE

The real `PhantomScheduler.withdrawSignal` returns `STALE` when:

```text
source entry is absent
OR
withdraw sequence is not newer than the stored source sequence
```

Goal 010B distinguishes ambiguous STALE from locally proven inactivity, but
accepts STALE only when the ledger state is `INACTIVE_CONFIRMED`.

A newly reserved ledger begins each source at:

```text
NEVER_SUBMITTED
```

That state also proves the topology provider has never activated the reserved
source during the current service lifetime. The source keys are exclusively
owned by the topology provider.

Current failure:

```text
scheduler profile registered
topology profile registered
no local-chat/combat/targetability event ever occurred
topology unregister
→ scheduler returns STALE for all absent sources
→ provider marks OWNERSHIP_UNCERTAIN
→ unregister returns signal failure
→ cleanup retry repeats STALE forever
→ re-registration remains blocked
```

The same defect breaks a normal explicit topology reload before the profile has
used all three source channels. Reload invalidation receives STALE for every
never-submitted source and rejects the candidate indefinitely.

The focused Goal 010B fake port defaults every withdrawal to `ACCEPTED`, so its
scheduler-present retention test does not exercise the real scheduler's absent
source semantics.

## 4. Goal

Implement and prove:

1. `NEVER_SUBMITTED` is treated as locally proven inactive for a STALE
   withdrawal;
2. STALE after `NEVER_SUBMITTED` transitions the slot to
   `INACTIVE_CONFIRMED`;
3. STALE after `INACTIVE_CONFIRMED` remains safe;
4. STALE after `POSSIBLY_ACTIVE` or `OWNERSHIP_UNCERTAIN` remains fail-closed;
5. fresh profile unregister with a real registered scheduler slot succeeds,
   retains exactly one ledger tombstone and creates no pending cleanup;
6. a profile that used only one of the three sources unregisters successfully:
   the active source is withdrawn and the two never-submitted sources reconcile
   through safe STALE;
7. re-registration after safe absent-source cleanup preserves monotonic provider
   sequences and can submit to the real scheduler;
8. explicit topology reload before any perception event succeeds with the real
   scheduler adapter;
9. scheduler `NOT_REGISTERED` still releases the ledger only under the existing
   all-three absence rule;
10. no ambiguous active-source STALE is accepted;
11. all 010A generation and 010B bounded-capacity guarantees remain unchanged;
12. Goal 011/012 remain not started;
13. all cumulative regressions remain GREEN.

## 5. Mandatory reading

Read fully:

- roadmap, master plan, `Agents.md`, workflow/package/report standards;
- Goal 010/010A/010B reports, contracts and reviews;
- `PhantomPerceptionProvider`;
- `PhantomTopologySignalLedger`;
- `PhantomScheduler.submitSignal/withdrawSignal`;
- `PhantomSchedulerRelevanceSignalPort`;
- scheduler focused tests and topology signal-ledger/generation suites;
- all files in this package.

## 6. Initial Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 030184205c6bf2101cb6256086c0b85c0e26dcd4
git diff --name-status f7eb90ecf3badfc615e6ee700d392a5cbb815811..030184205c6bf2101cb6256086c0b85c0e26dcd4
```

Expected:

```text
HEAD == origin/feature/phantom-world == 03018420...
```

The extracted Goal 010C package is expected untracked. Preserve unrelated
`docs/agent-tasks/**`. Return `BLOCKED_BASELINE_DRIFT` for unreviewed
production/config/schema drift.

## 7. Fixed reconciliation rule

The only safe STALE rule becomes:

```text
previous local source state == NEVER_SUBMITTED
OR
previous local source state == INACTIVE_CONFIRMED
→ STALE proves no newer topology-provider activation is locally outstanding
→ withdrawal succeeds
→ source state becomes INACTIVE_CONFIRMED
→ schedulerAbsent = false
```

For:

```text
POSSIBLY_ACTIVE
OWNERSHIP_UNCERTAIN
```

STALE remains:

```text
cleanup failure
source = OWNERSHIP_UNCERTAIN
```

`STALE` never proves the scheduler profile slot is absent, so it never
contributes to all-three `NOT_REGISTERED` ledger release.

Do not change submit classification.

## 8. Why NEVER_SUBMITTED is valid proof

The reserved topology source keys are exclusively emitted through
`PhantomPerceptionProvider`.

A ledger is created before topology registration and survives every
unregister/re-register while the scheduler profile may remain present.

Therefore, within one topology service lifetime:

```text
NEVER_SUBMITTED
```

means no accepted/coalesced topology submit was issued for that profile/source
under any earlier sequence in the same ledger.

Service restart clears both the topology ledger and its newly constructed
scheduler state; no cross-process source is restored.

Document this invariant explicitly. Do not generalize it to arbitrary source
providers.

## 9. Real scheduler integration tests

Add a focused suite or extend the signal-ledger suite with an actual
`PhantomScheduler` and `PhantomSchedulerRelevanceSignalPort`.

Preferred new suite:

```text
PhantomTopologySchedulerSignalIntegrationSuite
launcher: topology-scheduler-signal-integration
Ant: phantom-topology-scheduler-signal-integration-test
```

Use:

- real scheduler registry/source state machine;
- no-op materialization port;
- no-op work sink;
- explicit scheduler start/register/stop;
- topology service from a deterministic in-memory snapshot;
- no DB and no production topology loader.

No fake SignalDelivery result for the mandatory real-integration cases.

### Mandatory cases

1. **Fresh profile, no topology source activity**
   - start real scheduler;
   - register scheduler profile;
   - register topology profile;
   - unregister topology profile;
   - real scheduler returns STALE for absent fixed sources;
   - topology result is `UNREGISTERED_AND_WITHDRAWN`;
   - pending cleanup = 0;
   - ledger current = 1;
   - source states become `INACTIVE_CONFIRMED`;
   - re-registration is allowed.

2. **Only targetability was active**
   - activate targetability through topology provider;
   - local-chat/combat remain `NEVER_SUBMITTED`;
   - unregister succeeds;
   - targetability withdrawal is accepted/coalesced by scheduler;
   - absent sources reconcile through safe STALE;
   - pending cleanup = 0.

3. **Re-registration monotonicity**
   - after the fresh no-event unregister, re-register topology profile;
   - emit local-chat or targetability;
   - provider sequence is strictly newer than the safe-STALE cleanup sequence;
   - real scheduler accepts it;
   - no source is marked uncertain.

4. **Reload before events**
   - real scheduler and topology profiles are registered;
   - no perception event occurs;
   - valid topology candidate generation is prepared;
   - reload invalidation reconciles three absent sources;
   - reload returns `RELOADED`;
   - hash/generation/membership swap occurs;
   - pending cleanup remains zero.

5. **Possibly-active STALE remains failure**
   - use a narrow fake/controlled port only for this impossible ordering case;
   - set local source state `POSSIBLY_ACTIVE`;
   - return STALE;
   - cleanup/reload remains failed and uncertain.

6. **Scheduler absent release unchanged**
   - all three real adapter outcomes `NOT_REGISTERED` after scheduler profile
     removal release the ledger as before.

If real scheduler periodic pulse makes test ordering noisy, use the existing
scheduler test seam or a package-safe deterministic driver. Do not change
scheduler production semantics.

## 10. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java
```

A comment-only clarification in:

```text
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java
```

is allowed.

No other production behavior change.

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologySchedulerSignalIntegrationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-010c.ps1
```

Allowed documentation:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md
docs/phantoms/tasks/010c-topology-absent-source-reconciliation/**
docs/phantoms/reports/010b-topology-signal-ledger-bounds.md
docs/phantoms/reports/010c-topology-absent-source-reconciliation.md
docs/phantoms/reviews/010b-topology-signal-ledger-bounds-review.md
```

## 11. Hard out of scope

Forbidden:

- topology XML/data, loader/query/snapshot/entity changes;
- generation coordinator, registry capacity or ledger structure redesign;
- scheduler implementation/semantics;
- navigation, decision, materialization or lifecycle changes;
- Player/Creature/AI/packets;
- config or DB schema;
- Goal 011/012;
- movement/population/conversation;
- new executor/raw production thread/per-profile task;
- production DB;
- other chronicles/dependencies/CI/mass formatting;
- amend/rebase/merge/force push.

## 12. Static verifier

Create deterministic read-only:

```text
tools/phantoms/verify-task-010c.ps1
```

Verify:

- base `03018420...`, one ordinary exact-scope commit;
- no topology XML/config/schema/Goal 011/012;
- scheduler implementation, topology generation/registry/ledger structure,
  loaders/navigation/decision/lifecycle frozen;
- STALE safe states are exactly `NEVER_SUBMITTED` and
  `INACTIVE_CONFIRMED`;
- safe STALE writes `INACTIVE_CONFIRMED`;
- possibly-active/uncertain STALE still fails;
- safe STALE never sets schedulerAbsent;
- real scheduler adapter integration suite exists;
- fresh no-event unregister case;
- partial-source unregister case;
- re-registration sequence case;
- reload-before-events case;
- scheduler-absence release regression;
- no executor/thread/per-profile Future;
- tests/Ant/docs/encoding/credentials/binaries.

## 13. Documentation

Create:

```text
docs/phantoms/reviews/010b-topology-signal-ledger-bounds-review.md
docs/phantoms/reports/010c-topology-absent-source-reconciliation.md
```

Update Goal 010B report with immutable handoff:

```text
Commit: 030184205c6bf2101cb6256086c0b85c0e26dcd4
Parent: f7eb90ecf3badfc615e6ee700d392a5cbb815811
Push/remote: exact
Signal ledger: 20/20 ×3
Generation: 17/17 ×3
Perception: 28/28 ×3
Core: 38/38 ×3
Corpus: 6/6 ×2
Performance: 1/1 ×2
Navigation and shutdown regressions: PASS
Final verifier: 85/85 ×2, byte-identical
External verifier SHA: abbreviated handoff only
ADA98158...25CCA
Independent review:
- bounded ledger architecture ACCEPT
- absent-source real scheduler reconciliation FIX_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
```

Do not invent the missing full verifier SHA.

Review verdict:

```text
Goal 010B bounded architecture: ACCEPT
Goal 010 overall: FIX_REQUIRED
Revert: NOT_REQUIRED
Goal 010C: REQUIRED
Goal 011: BLOCKED
Goal 012: NOT_STARTED
```

Roadmap progress only:

```text
Goal 010: FIX_REQUIRED
Goal 010A: ACCEPT
Goal 010B: ACCEPT_WITH_010C_INTEGRATION_BOUNDARY
Goal 010C: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
Goal 011: NOT_STARTED / BLOCKED
Goal 012: NOT_STARTED
```

## 14. Commands

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010b.ps1

ant compile-tests
ant phantom-topology-scheduler-signal-integration-test
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

Run scheduler-signal integration ×3, signal-ledger ×3, generation ×3 and all
cumulative routes.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-010c.ps1
git diff --check
```

Post-commit run verify/jar/verifier ×2, push and confirm remote exact.

## 15. Result and commit

Successful result:

```text
TOPOLOGY_ABSENT_SOURCE_RECONCILED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
fix(phantoms): reconcile absent topology sources
```

One ordinary commit on top of `03018420...`.

Push regardless of SUCCESS/BLOCKED using safe scoped artifacts only.

## 16. Blocking behavior

Return `BLOCKED` if:

- real scheduler integration disproves exclusive NEVER_SUBMITTED ownership;
- the fix requires changing scheduler implementation;
- safe STALE cannot be distinguished without unbounded history;
- Goal 011/config/schema changes are required;
- production DB is accessed;
- cumulative verify/jar fails.

On blocker remove unsafe production edits, preserve safe evidence, commit/push
and keep Goal 011 blocked.

## 17. Final handoff

```text
Status:
Architecture result:
Baseline:
Goal 010B review:
Safe STALE states:
NEVER_SUBMITTED transition:
INACTIVE_CONFIRMED transition:
Possibly-active STALE:
Fresh real-scheduler unregister:
Partial-source real-scheduler unregister:
Re-registration monotonicity:
Reload-before-events:
Scheduler-absent ledger release:
Pending cleanup:
Ledger current/peak/capacity:
Real integration tests:
Signal-ledger/generation/perception/core:
Corpus/performance:
All regressions:
ant verify:
ant jar:
Verifier final 1/final 2/identical/SHA:
Production DB:
JAR topology/test entries:
Commit/parent/branch/push/remote:
Report:
Manual gate:
Goal 011:
Goal 012:
Limitations/blockers:
```
