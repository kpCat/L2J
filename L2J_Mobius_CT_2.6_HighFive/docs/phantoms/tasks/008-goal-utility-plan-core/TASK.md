# GOAL 008 — Goal model, Utility AI core and plan executor

## 1. Identifier

- Goal: `008-goal-utility-plan-core`
- Branch: `feature/phantom-world`
- Accepted baseline: `357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018`
- Parent: `9958edd9e133557f4966eed0a4124e68326401b3`
- Git root: `C:\Users\endim\L2J_Mobius\`
- Only module: `C:\Users\endim\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Test DB only: `l2jmobiush5_phantom_test`
- Production DB: forbidden during Codex execution
- Seed: `20260725001`
- Model: Sol
- Effort: Very High

## 2. Gate

```text
Stage I: COMPLETE
Goal 006 / 006A / 006B: ACCEPT
Goal 007 / 007A: ACCEPT
Goal 008: ALLOWED
Goal 009: NOT_STARTED
```

Accepted Goal 007A evidence:

```text
Commit: 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
Parent: 9958edd9e133557f4966eed0a4124e68326401b3
Scheduler: 17/17 ×3
Scale: 2/2 ×2
Scale SHA-256: 67B7FC26B98141661890DFAAE5F307B86BB5C768EA82A2DF6A8D1F1556F7EE30
Production: 20/20 ×3
Shutdown: 5/5 ×3
Verifier: 63/63 ×2
Verifier SHA-256: D0F1BBD00C96AE180BA7D96A9B808F20C18467A2F996183CBBD9E559702C78A1
```

One bounded scheduler integration follow-up belongs here, not in a separate 007B:
when an external retained-cleanup retry returns, recompute the current requested
state under the scheduler monitor instead of using the stale target captured
before the external call.

## 3. Result

Implement a deterministic, domain-neutral decision core:

- one immutable/versioned persisted goal per explicitly attached profile;
- generic `DomainRef` and capability requirements;
- bounded sealed candidate and step-handler registries;
- normalized integer Utility AI scoring and deterministic tie breaking;
- immutable typed plans with bounded steps;
- one executor slice/handler invocation per scheduler work item;
- timeout, retry, cancellation, replan and terminal goal states;
- bounded reason/explanation snapshots;
- restart loads the goal but never blindly restores an active plan.

Production must still start with:

```text
attached decision profiles = 0
registered scheduler profiles = 0
candidate registry = empty and sealed
step-handler registry = empty and sealed
automatic goal/profile creation = 0
automatic materialization = 0
```

No concrete combat, navigation, commerce, class, quest, item or language action.

## 4. Mandatory reading

Read fully:

- `docs/PHANTOM_BOTS_ROADMAP.md`;
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`;
- `Agents.md` and workflow/package/report standards;
- Goal 005, Goal 007 and Goal 007A packages/reports/reviews/contracts;
- accepted Goal 006 lifecycle/materialization contracts;
- `PhantomProfileRepository`, component model, `PhantomScheduler`,
  `PhantomSystem`, `PhantomMetrics`, all `phantoms/activity/**` and current tests;
- all files in this package.

Do not modify another chronicle.

## 5. Git audit

```bat
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git fetch origin --prune
git rev-parse HEAD
git rev-parse origin/feature/phantom-world
git show --stat --oneline 357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
git diff --name-status 9958edd9e133557f4966eed0a4124e68326401b3..357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018
```

Expected `HEAD == origin/feature/phantom-world == 357c047f...`.
Preserve and exclude unrelated `docs/agent-tasks/**`.

## 6. Close Goal 007A

Update `docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md`
and create `docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md`.

Verdict:

```text
Goal 007: ACCEPT after Goal 007A
Goal 007A: ACCEPT
Revert: NOT_REQUIRED
Bounded Goal 008 follow-up: cleanup retry uses current requested state
Goal 008: ALLOWED
Goal 009: NOT_STARTED
```

Update roadmap progress only: accepted baseline `357c047f...`, Goal 007/007A
`ACCEPT`, Goal 008 `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, Goal 009
`NOT_STARTED`.

## 7. Scheduler follow-up

Modify `PhantomScheduler` only for this exact behavior after successful retained
cleanup retry and confirmed absence of lifecycle ownership:

1. recompute current requested state from current signals/unregister state;
2. update `_requestedState` to that current value;
3. current WARM/BACKGROUND/SLEEPING becomes the truthful effective state;
4. current ACTIVE/NEARBY first becomes SLEEPING and gets one immediate fresh
   materialization opportunity;
5. do not apply demotion grace to a stale pre-call target;
6. pending unregister still removes only after terminal cleanup.

Add blocked-retry tests for WARM→SLEEPING and WARM→ACTIVE changes during the
external cleanup call. No other scheduler redesign.

## 8. Production package and fixed bounds

Create:

```text
java/org/l2jmobius/gameserver/phantoms/decision/**
```

No `Player`, `GameClient`, packet, LLM or external AI reference is allowed in
this package.

### 8.1 Domain reference

Immutable equivalent:

```java
PhantomDomainRef(String namespace, String key)
```

- namespace: `^[a-z][a-z0-9_.-]{0,31}$`;
- key: visible ASCII, length `1..128`, no surrounding whitespace;
- no lookup or hardcoded catalog.

### 8.2 Capabilities

Immutable:

```text
PhantomCapabilityRequirement(key, minimumRank)
PhantomCapabilitySet(sorted immutable key→rank map)
```

- key max 64 with bounded-key syntax;
- rank `1..1000`;
- max 128 available capabilities;
- max 16 requirements/candidate;
- no concrete production capability keys.

### 8.3 Goal

Immutable `PhantomGoal`, schema version 1, with equivalent fields:

```text
goalId positive long
goalType bounded key
status ACTIVE / COMPLETED / ABANDONED / FAILED
optional subject and target DomainRef
requiredAmount >= 0
currentAmount within requiredAmount
optional acquisitionMethod
validSources max 16
optional selectedAnchor
purposeKey
priority 0..1000
riskBudget and expenseBudget >= 0
deadlineEpochMillis 0 or positive
constraints sorted immutable max 16
reasonKey
revision non-negative
```

Replacement revision must be strictly greater. Only one current goal/profile.

## 9. Persistence

Use existing `phantom_profile_components`; no schema change.

Reserved component:

```text
component_type = goal.runtime
component_schema_version = 1
```

Create stateless store/codec equivalent to:

```text
PhantomGoalStateStore
PhantomGoalStateCodec
```

API: load, insert, optimistic replace, optimistic delete.

Codec requirements:

- deterministic binary format with magic and version;
- payload <= 4096;
- strict lengths/counts before allocation;
- full-consumption check;
- reject truncation, trailing bytes and unknown versions;
- no Java serialization, JSON library or reflection codec.

Only goal state is persisted. Never persist a plan, handler, cancellation token
or explanation history. Reads happen only on explicit attach/reload. Ordinary
scheduler work performs no DB query. Writes happen only on explicit goal
mutation and terminal goal-state changes. After restart an ACTIVE goal becomes
`NEEDS_REPLAN` with no restored plan.

## 10. Candidate and handler registries

Candidate registry:

- max 256;
- unique bounded candidate key;
- registration only before `seal()`;
- immutable lexicographic snapshot after seal;
- supported goal types max 16;
- allowed activity states;
- requirements max 16;
- considerations max 16;
- minimum accepted score `0..1000`;
- typed plan factory.

Step-handler registry:

- max 256;
- unique action key;
- registration only before seal;
- handler receives immutable profile/goal/plan/step/activity/logical-time/
  cancellation-token context;
- no Player, packet or arbitrary Runnable.

Production Goal 008 seals both registries empty.

## 11. Utility scoring

Each consideration has key, weight `1..1000`, and returns score `0..1000` plus
a bounded reason key.

Formula using overflow-safe integer arithmetic:

```text
floor(sum(score * weight) / sum(weight))
```

Requirements are checked first. Missing requirement blocks candidate.
Consideration exception/invalid output blocks only that candidate.
Candidates below threshold are not selected.

Selection:

```text
highest score
→ ASCII candidate key ascending
```

No random/insertion/hash-order tie break. Evaluate max 256 candidates and keep a
bounded top-eight explanation ordered score-desc/key-asc.

## 12. Plan and executor

Immutable plan:

- positive planId and exact goalId;
- candidate key;
- `1..32` contiguous typed steps;
- total timeout `1..86_400_000 ms`;
- logical creation time.

Step:

- action key;
- optional DomainRef target;
- sorted numeric args max 16;
- timeout `1..3_600_000 ms`;
- attempts `1..10`;
- reason key.

No callback is stored in plan data.

Typed handler results:

```text
SUCCESS
RETRY
REPLAN
COMPLETE_GOAL
FAIL_GOAL
CANCELLED
```

Executor rules:

- at most one plan and one handler in flight/profile;
- at most one handler invocation/work item;
- no thread/future/executor/profile task;
- handler outside global engine locks;
- generation checked before and after handler;
- stale result after goal replacement, detach, activity generation change or
  stop is discarded;
- cooperative cancellation only, no thread interruption;
- step and total timeout use scheduler logical monotonic time;
- RETRY uses bounded delay/attempt count;
- REPLAN discards plan and selects later;
- terminal status is persisted;
- persistence conflict enters
  `PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD`, with no auto retry.

## 13. Decision engine and scheduler integration

Create `PhantomDecisionEngine implements PhantomActivityWorkSink` with
`NEW/RUNNING/STOPPING/STOPPED`.

Explicit APIs equivalent to:

```text
start
attach(profileId)
detach(profileId)
insertGoal / setGoal / clearGoal
reload(profileId)
find/list snapshots
beginStop / finishStop
```

Attach validates profile existence, respects bounded capacity, loads the goal
once, and performs no scheduler registration/materialization. Absent component
means attached/no goal. Detach increments cancellation generation and remains
pending while a handler is in flight.

Extend `PhantomActivityWorkItem` with stable activity/dispatch generation that
changes on effective activity or slot lifecycle ownership changes, not every
harmless signal replacement.

Engine work:

- ignores unattached/no-goal/terminal/stale work;
- performs one decision/execution slice;
- no DB reads on unchanged ticks;
- no per-tick logs.

Production startup in `PhantomSystem`:

```text
repository open
→ materialization service start
→ empty candidate/handler registries seal
→ goal store + decision engine start
→ scheduler with decision engine sink
→ scheduler start
```

Shutdown:

```text
scheduler.beginStop
→ decisionEngine.beginStop / cancel generations
→ materialization service drain
→ scheduler.finishStop after pulse quiescence
→ decisionEngine.finishStop after handler quiescence
→ system STOPPED
```

Any unfinished scheduler/engine makes system FAILED and retains configured
instance for a later explicit shutdown.

## 14. Explanations and metrics

Runtime snapshot contains bounded goal/revision/status, runtime state, decision
sequence, selected candidate/score, plan/current step/attempt, last result,
reason key, top candidate evaluations max 8, in-flight flag, generation and
component row version. No exceptions, stack traces, chat text or history.

Add fixed aggregate metrics only: attached current/peak; mutation/reload reject;
decisions/no-goal/no-candidate; candidates evaluated/blocked/failed; plans
created/replanned/completed/failed/cancelled/timed-out; steps attempted/success/
retry/fail/cancel; persistence conflicts; stale results; stop failures. No
candidate/goal dynamic labels.

## 15. Tests

Create:

```text
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java
```

Launcher/Ant modes:

```text
decision-core / phantom-decision-core-test
decision-persistence / phantom-decision-persistence-test
decision-performance / phantom-decision-performance-smoke
```

Core suite at least 22 cases covering model/registry bounds, capabilities,
normalized scoring, consideration isolation, deterministic tie, threshold,
explanation top-eight, plan validation, one-step/work, retry/max attempts,
step/plan timeout, replan, goal completion/failure, blocked-handler goal
replacement, stale work generation, detach pending, stop quiescence and both
scheduler current-request cleanup races.

Persistence suite covers deterministic codec, invalid payloads, optimistic
insert/replace/delete, attach-load-once, zero tick reads, restart replan, no plan
persistence, terminal state persistence and final owned component residue zero.

Manual scheduler integration must drive a test goal through deterministic
candidate selection and one step/work item; replacing the goal during a blocked
handler discards the stale result.

Performance smoke, run twice with identical summary:

```text
1000 attached in-memory runtimes
64 sealed candidates
8 considerations/candidate
bounded dispatch equivalent to 32 profiles/pulse
```

No DB reads after attach and no per-profile thread/future.

Regression: scheduler 17/17 ×3, scale 2/2 ×2, production 20/20 ×3, shutdown 5/5
×3, plus all prior headless/profile/DB/harness/skeleton/performance routes.

## 16. Scope

Allowed production:

```text
java/org/l2jmobius/gameserver/phantoms/decision/**
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java
java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityWorkItem.java
java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java
```

Allowed tests/build:

```text
build.xml
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java
tools/phantoms/verify-task-008.ps1
```

Allowed docs:

```text
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md
docs/phantoms/tasks/008-goal-utility-plan-core/**
docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md
docs/phantoms/reports/008-goal-utility-plan-core.md
docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md
```

## 17. Hard out of scope

No config/schema/profile-core/materialization/identity/network/Player/World
changes. No concrete game actions/catalogs, combat, navigation, Game Knowledge,
Semantic Pack, personality/memory, population/schedules, automatic attach or
registration, multiple goals/profile, random scoring, LLM/external AI, new
executor/raw thread/per-profile task, production DB, other chronicles,
dependencies/CI, old verifier edits, mass formatting, amend/rebase/force push.
Goal 009 remains NOT_STARTED.

## 18. Static verifier

Create deterministic read-only `tools/phantoms/verify-task-008.ps1` checking:

- base/one ordinary exact-scope commit;
- no config/schema/Goal 009;
- Goal 006 lifecycle/network frozen;
- `goal.runtime`, schema 1, deterministic binary <=4096;
- no Java serialization/JSON/plan persistence;
- model/registry/plan bounds;
- no concrete production candidates/handlers/capability keys;
- integer formula and deterministic tie;
- explanation max 8;
- one handler/work and generation checks;
- no DB ordinary tick path;
- scheduler current-request follow-up and work generation;
- engine/scheduler/system stop ordering/finish checks;
- production zero attached/registered and empty sealed registries;
- no Player/GameClient/packet/executor/raw thread/per-profile future in decision
  package;
- tests/docs/encoding/credentials/binaries.

## 19. Commands

```bat
ant verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-007a.ps1

ant compile-tests
ant phantom-decision-core-test
ant phantom-decision-persistence-test
ant phantom-decision-performance-smoke
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

Run decision core ×3, decision performance ×2 and scheduler ×3.

Final:

```bat
ant verify
ant jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\phantoms\verify-task-008.ps1
git diff --check
```

Post-commit repeat verify/jar/verifier ×2, push and verify exact remote ref.
Compare verifier outputs byte-for-byte outside the repository.

## 20. Result, report and commit

Create `docs/phantoms/reports/008-goal-utility-plan-core.md` with full evidence,
manual gate `PENDING_INDEPENDENT_REVIEW` and Goal 009 `NOT_STARTED`.

Successful result:

```text
GOAL_UTILITY_PLAN_CORE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Commit subject:

```text
feat(phantoms): add goal utility plan core
```

One ordinary commit over `357c047f...`; push regardless of SUCCESS/BLOCKED but
only safe scoped artifacts. Do not self-accept Goal 008.
