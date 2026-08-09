# Goal 024 — Farming resource claims, negotiation and conflict convergence

## Identifier

```text
Task ID: 024-farming-resource-negotiation
Goal: 024
Branch: feature/phantom-world
Required parent: e67298697eaecc629a03b215a78ffa947233efd3
Required commit subject: feat(phantoms): add farming resource negotiation
Seed: 24002401
Success token: GOAL_024_FARMING_RESOURCE_NEGOTIATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```

Goal025+ must not start.

## Previous-goal review gate

Before production changes read `PRIOR_INDEPENDENT_REVIEW.md` and record in repository docs:

```text
Goal 023C: ACCEPT
R023C-01: CLOSED
Goal 023 overall: ACCEPT
accepted baseline: e67298697eaecc629a03b215a78ffa947233efd3
Goal 024: IN_PROGRESS, later IMPLEMENTED_PENDING_INDEPENDENT_REVIEW only after gates
Goal 025+: NOT_STARTED
```

Do not erase historical CHANGES_REQUIRED statuses attached to old exact 023/023A baselines. If HEAD is not exact required parent, do not reset/rebase/force history; produce truthful BLOCKED report/ordinary commit/push per workflow.

## Goal

Implement one coherent capability:

```text
current Goal021 source -> exact resource claim
perceptible competing Phantom claim -> bounded negotiation
both real acquisition goals + world/social facts -> explainable resolution
SHARE / WAIT / MOVE / REFUSE / ESCALATE typed acts
agreement history -> Goal018
conflict gate -> Goal021
facts -> Goal020
```

No PvP/PK execution.

## Read-first docs

Read master plan, roadmap, workflow contract, task package standard, report template, 023/023A/023B/023C review docs, 023C report, and this package. `ARCHITECTURE.md`, `TEST_CASES.md`, `ACCEPTANCE.md` are normative.

## Pre-audited production read set

Goal021 acquisition:

```text
phantoms/acquisition/PhantomAcquisitionGoalSpec.java
phantoms/acquisition/PhantomAcquisitionState.java
phantoms/acquisition/PhantomAcquisitionStateCodec.java
phantoms/acquisition/PhantomAcquisitionStore.java
phantoms/acquisition/PhantomAcquisitionSourcePlanner.java
phantoms/acquisition/PhantomAcquisitionService.java
phantoms/acquisition/PhantomAcquisitionDecision.java
dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml
```

Goal010 topology/perception:

```text
phantoms/topology/PhantomTopologyService.java
phantoms/topology/PhantomTopologyQuery.java
phantoms/topology/PhantomTopologyProfileRegistry.java
phantoms/topology/PhantomPerceptionProvider.java
phantoms/topology/PhantomTopologySignalLedger.java
phantoms/topology/PhantomTopologyNodeKind.java
phantoms/topology/PhantomTopologyAnchorRole.java
phantoms/topology/PhantomPerceptionChannel.java
```

Goal018 social:

```text
phantoms/social/PhantomSocialModel.java
phantoms/social/PhantomSocialService.java
phantoms/social/PhantomSocialCatalog.java
phantoms/social/PhantomSocialStore.java
dist/game/data/phantoms/social/high-five-social-v1.xml
```

Goal020 conversation:

```text
phantoms/conversation/PhantomConversationExecutionModel.java
phantoms/conversation/PhantomConversationExecutionCatalog.java
phantoms/conversation/PhantomConversationExecutionService.java
phantoms/conversation/PhantomConversationExecutionPort.java
phantoms/conversation/L2jPhantomConversationExecutionPort.java
dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml
dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml
```

Read semantic XML/corpus only if exact supported `farming.conflict.query` utterance grounding requires it.

Party/decision/composition:

```text
phantoms/party/PhantomPartyCoordinator.java
phantoms/decision/PhantomGoal.java
phantoms/decision/PhantomGoalStateStore.java
phantoms/decision/PhantomCandidateRegistry.java
phantoms/decision/PhantomStepHandlerRegistry.java
phantoms/economy/PhantomEconomyConflictPort.java
phantoms/PhantomSystem.java
```

## Test/build read set

Read exact launcher-registered current tests for acquisition core/source/active/background/restart, topology perception/signal ledger, social core/integration, conversation query/execution, Party state query, Goal023C aggregate, profile component persistence/fault injection patterns; plus launcher, relevant build.xml targets, verifier023c.

## No artificial file-count budget

There is **no numerical file limit**. Expected production changes likely include new `phantoms/farming/**`, `PhantomAcquisitionService.java`, possibly narrow acquisition Decision plumbing, Goal010 bounded perception seam, Goal020 query adapter/catalog/model as needed, `PhantomSystem.java`, new farming policy XML, narrow social XML additions, and conversation data only if exact grounding requires it.

Every changed High Five file outside that predicted set must be documented with path, exact symbol/call path, why predicted set was insufficient, and why it belongs to Goal024. Do not touch files merely because listed.

## Baseline guard

Run `git status --short --branch`, `git rev-parse HEAD`, branch, `git show -s --format=%H%n%P%n%s HEAD`, `git diff --check`. Require exact HEAD `e67298697eaecc629a03b215a78ffa947233efd3` and branch feature/phantom-world.

## Mandatory architecture

Implement `ARCHITECTURE.md`. Mandatory results:

1. resource identity only from current Goal021 Source + Goal010 topology;
2. no duplicate remaining/source planner;
3. bounded Goal010-owned perceptibleProfiles seam;
4. no fourth topology signal-ledger source;
5. Goal021 conflict gate before new travel/target work;
6. same-Party SHARE;
7. deterministic bilateral negotiation;
8. two-sided final durability before effect;
9. acts SHARE/WAIT/MOVE/REFUSE/ESCALATE;
10. MOVE delegates to Goal021 switchSource;
11. Goal018 owns long-term social memory/history;
12. Goal020 owns language and exact fact query;
13. ESCALATE has zero PvP/combat side effect;
14. ordinary human gets no fabricated goal/claim/agreement.

## Persistence/policy

Use bounded versioned profile component, no new SQL expected. No startup full-table scan; lazy recovery/revalidation.

Create strict hashed `dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml`; allowlist/range-check every attribute; no General.ini.

## Goal021 gate

Gate only safe resource boundaries; do not interrupt dispatched actions. MOVE goes through existing SWITCH path. Add regression proving uninstalled/disabled conflict port preserves previous acquisition behavior.

## Social/conversation

No direct chat in farming service. New social events use exact agreement/resource identity. Preserve generic agreement.fulfilled/broken semantics. Add typed farming facts/query in Goal020 as described.

## Tests

`TEST_CASES.md` normative. Create focused modes/targets with seed `24002401`; do not override global seed. Cross-seam acceptance should use real existing Goal021/010/018/020 services where the invariant crosses ownership; memory fakes only for pure model/codec/scoring. No fake GameClient.

## Performance/lifecycle

No workers/futures/timers/global scans. Bounded claim/perception/scoring/reconciliation operations. Avoid `World.getPlayers()`, `TopologyService.listProfiles()` hot path, full profile DB page per Decision pulse, or full knowledge rebuild merely to detect conflict.

Use stable paired write order lower profile ID then higher; no bilateral effect until both exact finals; shutdown drains mutation claims and clears runtime leases.

## Forbidden

Goal025+, PvP/PK/attack/force attack, raid/epic/clan, new acquisition/Party/navigation/social/chat kernel, direct chat packets, global player/profile scan, runtime LLM, production DB tests, other chronicles, geodata/.l2j.

## Verification targets

Create:

```text
phantom-farming-goal024-focused-test
phantom-farming-goal024-affected-test
phantom-farming-goal024-test
phantom-static-verify-024
```

Final aggregate includes all Goal024 dynamic modes; exact affected Goal010/017/018/020/021 tests; `phantom-rift-goal023c-test`; historical verifiers 023/023A/023B/023C; working verifier024. Make only the Ant invocation of verifier023C historical/descendant-compatible if needed; preserve its pinned semantics.

Create `tools/phantoms/verify-task-024.ps1` pinned to parent `e67298697eaecc629a03b215a78ffa947233efd3`, branch feature/phantom-world, subject `feat(phantoms): add farming resource negotiation`, seed `24002401`. It checks architecture/safety, not file count.

## Final verification discipline

After source/data/test/build/verifier freeze: diff check; historical verifiers; working verifier024; one final Goal024 aggregate; freeze manifest; one plain `ant verify`; one `ant jar`; freeze unchanged. Then exact staging, ordinary commit `feat(phantoms): add farming resource negotiation`, push. Post-commit verifier024 PS5.1 and already-verified PS7 stdout byte-identical. No amend/rebase/squash/merge/reset/force. Second full aggregate/verify only after real relevant fix and document it.

## Docs/report

Create architecture `FARMING_RESOURCE_NEGOTIATION_CONTRACT.md`, report `024-farming-resource-negotiation.md`, handoff `024-independent-review.md` with only IMPLEMENTED_PENDING_INDEPENDENT_REVIEW after success. Update master/roadmap only after gates. Report exact read set, expansions, resource-key examples, gate call flow, perception bounds, bilateral fault matrix, policy evidence, all five acts, social events, conversation mapping, human safety, operation counts, DB guard, shutdown, verification/git evidence.

## Terminal token

Only after every gate:

```text
GOAL_024_FARMING_RESOURCE_NEGOTIATION_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW
```
