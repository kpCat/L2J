# Goal 024A — Farming agreement lifecycle corrections

## 1. Identifier

```text
Task ID: 024a-farming-agreement-lifecycle-corrections
Goal: 024A corrective
Branch: feature/phantom-world
Required parent: 2603776c6996007b147f93e4c7e79f145ceb8a89
Required commit subject: fix(phantoms): harden farming agreement lifecycle
Seed: 24002402
Success token: GOAL_024A_FARMING_AGREEMENT_LIFECYCLE_CORRECTED_PENDING_INDEPENDENT_REVIEW
```

Goal025+ must not start.

## 2. Independent review entering task

Record before editing:

```text
Goal 023 overall: ACCEPT
Goal 024: CHANGES_REQUIRED
R024A-01: OPEN
R024A-02: OPEN
R024A-03: OPEN
Goal 025+: NOT_STARTED
```

Read `REVIEW_FINDINGS.md` first. Preserve accepted Goal024 architecture.

## 3. Mandatory read-first docs

```text
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/CODEX_WORKFLOW_CONTRACT.md
docs/phantoms/TASK_PACKAGE_STANDARD.md
docs/phantoms/CODEX_REPORT_TEMPLATE.md

docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md
docs/phantoms/reports/024-farming-resource-negotiation.md
docs/phantoms/reviews/024-independent-review.md
docs/phantoms/tasks/024-farming-resource-negotiation/*
this 024A package
```

`ARCHITECTURE.md`, `TEST_CASES.md`, `ACCEPTANCE.md`, `REVIEW_FINDINGS.md` are normative.

## 4. Pre-audited production read set

### Goal024 domain

```text
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingConflictPort.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingConversationFacts.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingDecision.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingModel.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingPersistencePort.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingPolicy.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStateCodec.java
java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStore.java
dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml
```

### Goal021 authority/gate

```text
java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionGoalSpec.java
java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionState.java
java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java
java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionDecision.java
```

### Goal010 exact perception/restart facts

```text
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyQuery.java
java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionChannel.java
```

### Goal018 / Goal020 / composition

```text
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java
java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialModel.java
dist/game/data/phantoms/social/high-five-social-v1.xml
java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Read Party code only if correction touches exact same-Party call path.

## 5. Pre-audited tests/build/tools

```text
test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java
test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java
build.xml
tools/phantoms/verify-task-024.ps1
```

Read exact Goal010/018/020/021 tests only when their call path is touched or required for final regression target mapping.

## 6. Expected change set — not a file budget

Expected primary production changes:

```text
PhantomFarmingService.java
PhantomFarmingModel.java
PhantomFarmingStateCodec.java
PhantomFarmingStore.java (only if schema handling needs it)
PhantomAcquisitionService.java
```

Possible justified seams:

```text
PhantomFarmingConflictPort.java
PhantomFarmingConversationFacts.java
PhantomFarmingDecision.java
PhantomTopologyService.java
L2jPhantomConversationExecutionPort.java
PhantomSystem.java
```

Tests/build/verifier/docs as needed.

There is **no numeric file-count ceiling**. For every changed High Five file outside predicted set report path, exact symbol/call path, why predicted set was insufficient, why change belongs to R024A-01/02/03. Do not mark BLOCKED just because correct fix spans additional proven file.

## 7. Baseline guard

Before edits:

```text
git status --short --branch
git rev-parse HEAD
git rev-parse --abbrev-ref HEAD
git show -s --format=%H%n%P%n%s HEAD
git diff --check
```

Require HEAD `2603776c6996007b147f93e4c7e79f145ceb8a89`, branch `feature/phantom-world`. No rebase/reset/amend/force.

## 8. Required corrections

Implement only three findings in REVIEW_FINDINGS.

### 8.1 R024A-01

Split pre-final arbitration evidence from post-final stable binding.

Mandatory dynamic invariant:

```text
same goal/revision/source/resource + progress increased
  final SHARE/WAIT/MOVE remains live
```

But progress/evidence changed before FINAL -> old draft cannot finalize.

### 8.2 R024A-02

Persist exact causal perception evidence and implement exact-counterpart lazy rehydration.

Mandatory invariant:

```text
fresh visibility needed to start
history may continue exact pair for bounded TTL
restart loser-first cannot become ALLOW because holder runtime cache is empty
```

No scans.

### 8.3 R024A-03

Replace manual boolean authority with evidence-driven production reconciliation.

Mandatory invariant:

```text
actual Goal021 switch/completion/release
  -> observed bilateral terminal farming lifecycle
  -> exact Goal018 history
```

No direct source rewrite.

## 9. Schema

If adding causal perception/social delivery receipt fields changes payload, bump `farming.conflict` schema and implement deterministic safe v1 handling. No SQL. A v1 receipt without sufficient causal evidence cannot directly authorize live agreement.

## 10. Tests

Implement every materially distinct TEST_CASES case.

Most important proof gaps:

```text
SHARE after progress
WAIT after holder progress
active negotiation drift before FINAL
loser-first restart before holder pulse
perceptibility lost after OFFER/final
real Goal021 MOVE switch + automatic FULFILLED
EXPIRED persistence
social failure/retry
```

Do not satisfy with source-string checks.

## 11. Production Goal021 integration

MOVE test must execute existing Goal021 switching path. Invalid proof: only asserting DirectiveKind.SWITCH, manually rewriting Source, or manually calling a farming fulfilled method. Prove source actually changes under Goal021 ownership and Goal024 then reconciles it.

## 12. PvP safety

Fail if changed Goal024A production introduces doAttack, forceAttack, CombatService request/dispatch from farming package, PvP/PK, hostile skill dispatch or conflict Player.setTarget. ESCALATE remains data/history only.

## 13. Historical Goal024 verifier

Make verifier 024 descendant-compatible from build invocation if necessary. Do not weaken its original pinned parent/subject/scope semantics.

Create `tools/phantoms/verify-task-024a.ps1`, pin parent `2603776c6996007b147f93e4c7e79f145ceb8a89`, subject `fix(phantoms): harden farming agreement lifecycle`, branch `feature/phantom-world`, seed `24002402`. No artificial file-count limit.

## 14. Build targets

Create exact current equivalents of:

```text
phantom-farming-goal024a-lifecycle-test
phantom-farming-goal024a-restart-test
phantom-farming-goal024a-acquisition-integration-test
phantom-farming-goal024a-focused-test
phantom-farming-goal024a-affected-test
phantom-static-verify-024a
phantom-farming-goal024a-test
```

Final aggregate includes original Goal024 aggregate and all affected Goal010/017/018/020/021/023C regressions.

## 15. Verification discipline

Use focused tests during development. After freeze:

```text
focused 024A
original Goal024 aggregate
affected matrix
historical verifier 023/023A/023B/023C/024
working verifier 024A
one final 024A aggregate
one plain ant verify
one standalone ant jar
git diff --check
freeze unchanged
```

Then ordinary commit/push and post-commit verifier PS5.1 + verified PS7 byte-identical stdout. Do not loop full ant verify; repeat only after genuine relevant correction and document why.

## 16. Docs/status

Update `docs/phantoms/reviews/024-independent-review.md` to Goal024 CHANGES_REQUIRED with R024A-01/02/03 OPEN.

Create:

```text
docs/phantoms/reports/024a-farming-agreement-lifecycle-corrections.md
docs/phantoms/reviews/024a-independent-review.md
```

024A review handoff after success is only IMPLEMENTED_PENDING_INDEPENDENT_REVIEW. Roadmap/master: Goal024 corrective 024A pending independent review; Goal025+ NOT_STARTED. Do not self-ACCEPT Goal024.

## 17. Report additions

Record before/after binding predicates, pre-final progress drift, post-final SHARE/WAIT progress, causal perception receipt schema, loser-first restart exact call path, v1->v2 behavior if bumped, real Goal021 MOVE switch trace, automatic reconciliation rules, social retry/outbox evidence, fault points, no-scan/PvP proof, verification/commit/push/verifier evidence.

## 18. Commit

On success:

```text
git add -- <exact Goal024A paths>
git commit -m "fix(phantoms): harden farming agreement lifecycle"
git push origin feature/phantom-world
```

No amend/rebase/squash/merge/reset/force push. On BLOCKED revert unsafe/uncompilable production experiments and preserve truthful bounded audit/tests/docs in ordinary commit/push per workflow.

## 19. Success token

Only after every mandatory gate:

```text
GOAL_024A_FARMING_AGREEMENT_LIFECYCLE_CORRECTED_PENDING_INDEPENDENT_REVIEW
```
