# Goal 023A — Rift production integration, managed consent and restart closure

## 1. Идентификатор

```text
Task ID: 023a-rift-production-integration-corrections
Goal: 023A corrective
Branch: feature/phantom-world
Required parent: 840e159a989f6372da9c471c915413f1e4470daf
Required commit subject: fix(phantoms): harden rift recruitment integration
Seed: 23002311
Success token: GOAL_023A_RIFT_RECRUITMENT_INTEGRATION_CORRECTED_PENDING_INDEPENDENT_REVIEW
```

Это один coherent corrective Goal 023A. Предварительные 023A1/023A2 не создавать. Goal 024+ не начинать.

## 2. Цель

Закрыть independent-review blockers Goal 023 в production integration:

- безопасно привязать уже существующую canonical Party/Goal 017 claims к `rift.prepare`;
- обеспечить реальный target-side accept/refuse/defer для managed Phantom candidate без auto-consent ordinary real Player;
- выполнить exact pre-invite revalidation;
- перейти на bounded restart-safe `rift.preparation` v2 с party/full invitation identity;
- синхронизировать policy timeout с canonical invitation authority;
- исправить pending/refusal semantic facts, candidate source order, relationship ranking и metrics;
- доказать это real production seams, а не только `TestPartyPort`.

Сохранить уже реализованные factual catalog/readiness/RoleMatcher/side-effect-free entry части. Не переписывать Goal 017 или Goal 023 целиком.

## 3. Зависимости

Обязательные baseline/contracts:

- exact commit `840e159a989f6372da9c471c915413f1e4470daf`;
- accepted Goal 017 Party kernel and current canonical `PartyInvitationService` lifecycle;
- accepted Goal 018 social/relationship query authority;
- accepted Goal 020 typed conversation/action bridge;
- Goal 023 original task/contract/report;
- current profile-component persistence envelope.

Production DB запрещена. Использовать только DB `l2jmobiush5_phantom_test`, если тесту действительно нужна component persistence.

## 4. Контекст и независимое решение

Прочитать `CONTEXT.md` и `REVIEW_FINDINGS.md`. Independent review baseline 840e имеет status:

```text
CHANGES_REQUIRED
```

До production changes обновить `docs/phantoms/reviews/023-independent-review.md`: зафиксировать exact baseline, решение и blocker IDs R023A-01..08. Это не self-accept и не должно утверждать, что Goal 023 принят.

Accepted portions перечислены в `CONTEXT.md`; изменения вне доказанных findings требуют отдельного before/after test и объяснения в отчёте.

## 5. Обязательный bounded audit

### 5.1 Read first

В указанном порядке:

```text
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
docs/phantoms/CODEX_WORKFLOW_CONTRACT.md
docs/phantoms/TASK_PACKAGE_STANDARD.md
docs/phantoms/CODEX_REPORT_TEMPLATE.md
docs/phantoms/tasks/023-rift-advanced-party-recruitment/TASK.md
docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md
docs/phantoms/reports/023-rift-advanced-party-recruitment.md
docs/phantoms/reviews/023-independent-review.md
этот Goal 023A package
```

### 5.2 Exact production read set

```text
phantoms/rift/PhantomRiftService.java
phantoms/rift/PhantomRiftReadinessService.java
phantoms/rift/PhantomRiftModel.java
phantoms/rift/PhantomRiftStateCodec.java
phantoms/rift/PhantomRiftStore.java
phantoms/rift/PhantomRiftPolicy.java
phantoms/rift/PhantomRiftBackend.java
phantoms/rift/L2jPhantomRiftBackend.java
phantoms/rift/L2jPhantomRiftPartyPort.java
phantoms/rift/PhantomRiftMetrics.java
phantoms/rift/PhantomRiftDecision.java
phantoms/party/PhantomPartyCoordinator.java
phantoms/party/PhantomPartyDecision.java
phantoms/party/PhantomPartyBackend.java
phantoms/party/L2jPhantomPartyBackend.java
phantoms/party/PhantomPartyPersistencePort.java
phantoms/party/model/PhantomPartyModel.java
model/groups/PartyInvitationDelivery.java
model/groups/PartyInvitationService.java
model/actor/Player.java (read-only authority check)
phantoms/social/PhantomSocialService.java and exact snapshot/model types only
phantoms/conversation/L2jPhantomConversationExecutionPort.java
PhantomSystem.java
```

### 5.3 Exact test/tool read set

```text
test/.../PhantomRiftSuite.java
existing Goal 017 party invitation/recovery/server integration suites
existing Goal 020 party-action/query suites
PhantomTestLauncher.java
build.xml Goal 017/020/023 targets only
tools/phantoms/verify-task-023.ps1
```

No broad source scan unless one named type cannot be located. Record every expanded file and reason in report.

### 5.4 Baseline proof before edit

Record:

```text
git status --short --branch
git rev-parse HEAD
git rev-parse --abbrev-ref HEAD
git show -s --format=%H%n%P%n%s HEAD
git diff --check
```

Fail/stop production edits if branch/parent differs. Still create honest BLOCKED report/commit/push per workflow.

## 6. Архитектурное решение

`ARCHITECTURE.md` is normative.

Mandatory results:

1. one Goal 017-owned content-party binding seam;
2. one target-side managed invitation policy extension with default non-accepting behavior;
3. corrected Decision stages with separate `ENSURE_PARTY_BINDING`;
4. `rift.preparation` schema v2/backward-safe v1 replan;
5. exact full invitation identity and canonical expiry;
6. exact pre-invite candidate refresh;
7. typed pending/refusal/expiry semantic facts;
8. Phantom-first bounded candidate source order and Goal 018 modifier;
9. bounded complete metrics.

Equivalent class names are allowed, but all invariants/tests are not optional.

## 7. Scope

### 7.1 Allowed production/data paths

```text
java/org/l2jmobius/gameserver/phantoms/rift/**
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java
java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java
java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java
java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java only if typed binding/operation schema requires it
java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java
java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml
```

### 7.2 Allowed tests/build/tools/docs

```text
test/java/org/l2jmobius/tests/phantoms/** exact Rift/Party integration additions
build.xml
PhantomTestLauncher.java
tools/phantoms/verify-task-023a.ps1
Goal 023/023A architecture, report, review, task docs
PHANTOM_DEVELOPMENT_MASTER_PLAN.md
docs/PHANTOM_BOTS_ROADMAP.md
```

### 7.3 Hard file budgets

```text
new production/data files <= 6
changed production/data/config files <= 16
new test files <= 3
changed total files <= 32
new SQL files = 0
other chronicles = 0
```

Budget exceedance is BLOCKED unless a smaller safe implementation is impossible and the report contains exact proof; do not silently exceed.

## 8. Out of scope / forbidden

```text
Goal 024+
Rift entry execution
entry item consumption
teleport/room jump/spawn/combat
new party kernel
new invitation service
PvP/PK/raid/clan/farming conflict
trade/craft/economy mutation
language phrase bank/runtime LLM
fake GameClient
packet invocation
global online-player scan
new worker/thread/executor/Future/task/timer
other chronicles
geodata changes
```

Default expected changes are zero for:

```text
Player.java
Party.java
PartyInvitationService.java
DimensionalRiftManager.java
SQL/schema files
```

If a default-zero file appears necessary, first prove in report why an equivalent narrow seam in allowed Phantom code is impossible. `Player.REQUEST_TIMEOUT` must not be changed.

## 9. Требуемые изменения

### 9.1 Review/status docs

- Replace Goal 023 handoff-only review with factual independent `CHANGES_REQUIRED` review pinned to 840e.
- Preserve exact Goal 022 ACCEPT and C1 timing-flake waiver wording.
- After full success create `docs/phantoms/reviews/023a-independent-review.md` with `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, never ACCEPT.
- Update both roadmap/master consistently only at the end.

### 9.2 Content binding

Implement and integrate the contract in `ARCHITECTURE.md §2`.

Service must enter `ENSURE_PARTY_BINDING` before:

```text
REQUEST_INVITE
REQUEST_PARTY_ROUTE
DECLARE_READY when no exact stable binding exists
```

Persist returned binding receipt. On binding conflict, re-snapshot/replan; never recreate a valid current Party.

### 9.3 Managed Phantom consent

Implement target-side consent extension in Goal 017 lifecycle and one Rift provider. Requirements:

- exact immutable offer/invitation context;
- ACCEPT/REFUSE/DEFER;
- explicit consent precedence;
- no leader-forced candidate goal;
- ordinary real unaffected;
- deterministic, bounded, restart-safe;
- no auto-accept default.

### 9.4 Pre-invite race closure

Implement every check in `ARCHITECTURE.md §6` in production path immediately before delegation. Add typed reason families, not free-form exception text.

### 9.5 Persistence v2

- Add v2 codec/model.
- Backward decode current v1.
- v1 operational receipt forces live replan before any mutation.
- Store exact binding, candidate evidence and full invitation identity/expiry.
- Preserve payload/history bounds.
- No SQL table/migration.

### 9.6 Timeout and terminal mapping

- Policy default `inviteTimeoutMillis=15000`.
- Use exact canonical expiration; policy cannot extend it.
- Remove string-substring terminal mapping.
- Distinguish refusal/expiry/cancel/reject.

### 9.7 Readiness/READY stability

Expose bounded Goal 017 operation stability through the binding port. `READY_TO_ENTER` and final `DECLARE_READY` require no conflicting/pending operation and exact binding/source/roster evidence.

Do not add entry side effects.

### 9.8 Candidate discovery/ranking

- Phantom source first, real source second before cap.
- <=32 actual facts evaluated.
- current perceptible/local topology only.
- Goal 018 relationship modifier when exact query available; neutral typed fallback otherwise.
- stable deterministic evidence/tie-break.

### 9.9 Semantic facts and metrics

Implement all original Goal 023 semantic facts and metric families. Extend Goal 020 typed bridge only; no prose.

## 10. Конфиги

Only existing strict Rift policy may change.

Required:

```text
inviteTimeoutMillis = 15000
```

Keep parser range 15000..60000, but effective runtime timeout is bounded by canonical invitation expiry. Do not add General.ini keys or duplicate factual Rift values.

Any new policy attribute requires:

- strict allowlist/parser validation;
- bounded value;
- policy hash inclusion;
- negative tests;
- documented reason.

Prefer no new attributes.

## 11. Производительность

Mandatory bounds:

- one local visible-object query per candidate discovery;
- no `World.getPlayers()`;
- <=32 candidate `memberFacts` evaluations;
- no full profile/class/NPC/XML/DB scan per pulse;
- no unbounded sorting after a global source;
- no per-pulse social writes; relationship is read-only;
- v1 migration is per loaded component, not full-table rewrite;
- metric cardinality fixed.

Run `rift023a-performance` cases from `TEST_CASES.md` and record operation counts plus wall-clock as diagnostic, not as a flaky hard nanosecond gate.

## 12. Конкурентность и lifecycle

- Existing shared Decision/scheduler pulse only.
- Goal 017 remains single party saga owner.
- All stores use optimistic row versions.
- Save conflict after canonical invitation must reconcile exact operation identity, not resend.
- Shutdown must unregister/stop policy extension through existing coordinator lifecycle; no dangling callback/map.
- Pending invitation one per preparation and one per invitee according to canonical service.
- Stable lock/order rules of Goal 017 remain intact.
- No synchronized cross-service cycle between Rift, Party and Social.
- Read social snapshot outside Goal 017 persistence locks or through a bounded immutable result.

## 13. БД и транзакции

- Production DB `l2jmobiush5` forbidden.
- No new SQL migration/table.
- `rift.preparation` remains profile component.
- DB-backed tests only against `l2jmobiush5_phantom_test` after existing guard.
- Do not change credentials/config to production.
- Component v1→v2 conversion is optimistic and idempotent.
- A failed save does not roll back canonical Party; it creates a reconciliation requirement and prevents duplicate external action.

## 14. Автоматические тесты

`TEST_CASES.md` is normative.

Create/register all nine modes and two Ant targets:

```text
phantom-rift-goal023a-test
phantom-rift-goal023a-affected-test
```

The final 023A aggregate must include:

```text
all nine 023A modes
original phantom-rift-goal023-test
exact Goal 017/020 affected regressions
historical verifier 023
working-tree verifier 023A
```

Before running any descendant aggregate, update only the Ant invocation of verifier 023: `phantom-static-verify-023` must call `verify-task-023.ps1` without `-WorkingTree`. The script itself already selects the first child after its required parent in historical mode. Do not make verifier 023 inspect Goal 023A changes.

Do not replace original Goal 023 tests; add production-seam proof.

## 15. Команды проверки

### 15.1 Focused development commands

After adding Ant launcher routes, run each exact mode once as needed:

```text
ant -Dphantom.goal023a.mode=rift023a-party-binding phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-managed-consent phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-preinvite-revalidation phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-invitation-authority phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-restart-migration phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-semantic-facts phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-candidate-ordering phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-route-binding phantom-rift-goal023a-focused-test
ant -Dphantom.goal023a.mode=rift023a-performance phantom-rift-goal023a-focused-test
```

Create `phantom-rift-goal023a-focused-test` using only seed property `phantom.goal023a.seed=23002311` and mode property. Do not override global seed.

### 15.2 Exact affected regressions

```text
ant phantom-rift-goal023-test
ant phantom-party-canonical-invitation-test
ant phantom-party-state-recovery-test
ant phantom-party-role-vacancy-test
ant phantom-party-route-test
ant phantom-party-lifecycle-test
ant phantom-party-server-integration-test
ant phantom-conversation-party-actions-test
ant phantom-conversation-query-execution-test
```

The aggregate affected target may depend on these to avoid duplicate manual commands, but report exact target results.

### 15.3 Static and source checks before freeze

```text
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-023.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/phantoms/verify-task-023a.ps1 -WorkingTree
```

Also check changed text files for mojibake markers and escaped Cyrillic using the established verifier pattern.

### 15.4 Freeze discipline

After the last relevant code/test/build/verifier change:

1. one final `ant phantom-rift-goal023a-test`;
2. freeze changed production/data/test/build/verifier files and record SHA-256;
3. one plain `ant verify`;
4. one standalone `ant jar`;
5. `git diff --check` and exact scope audit;
6. ordinary commit/push;
7. two post-commit `verify-task-023a.ps1` runs: Windows PowerShell 5.1 and existing verified local PowerShell 7, byte-identical stdout.

A second final aggregate/plain verify is allowed only after a real relevant fix and must be explained. Third full run is forbidden. Do not download PowerShell 7 again when the verified `.phantom-local` copy already exists.

## 16. Критерии приёмки

Every checkbox in `ACCEPTANCE.md` must be satisfied.

Additional decisive failures:

```text
existing committed party still returns endless CLAIM_EXISTS
eligible managed Phantom cannot reach canonical ACCEPT
ordinary real Player is auto-accepted
stale candidate can receive invite
sequence-only restart reconciliation remains
policy timeout remains unused
expiry maps to refusal
READY ignores pending/conflicting party operation
production integration tests still use only TestPartyPort
roadmap/master disagree
```

Any decisive failure means no success token.

## 17. Формат отчёта

Create:

```text
docs/phantoms/reports/023a-rift-production-integration-corrections.md
```

Follow `CODEX_REPORT_TEMPLATE.md` and additionally include:

- exact independent findings closure table R023A-01..08;
- before/after production call flow;
- all changed files grouped by production/test/docs/tools;
- v1→v2 compatibility behavior and payload max measured;
- exact party binding state matrix;
- managed consent precedence matrix;
- exact terminal mapping table;
- commands, exit codes, durations and real output summaries;
- operation-count/performance evidence;
- source freeze hashes;
- any expanded read/scope and reason;
- confirmation no production DB/geodata/other chronicle;
- commit SHA, parent, branch, push and remote-head equality;
- honest limitations/manual gates.

Do not repeat unverified claims from Goal 023 report.

## 18. Commit и push

Required ordinary commit:

```text
fix(phantoms): harden rift recruitment integration
```

Rules:

- one direct child of `840e159a989f6372da9c471c915413f1e4470daf`;
- add only exact allowlisted Goal 023A files;
- no amend/rebase/merge/squash;
- push `feature/phantom-world` even for PARTIAL/BLOCKED;
- final report includes local HEAD and remote branch SHA;
- worktree clean after push.

## 19. Поведение при блокировке

If a safe corrective implementation cannot be completed:

1. revert unstable/noncompiling production changes;
2. preserve factual independent review, bounded audit, tests that reproduce blockers and report;
3. do not weaken/skip assertions or verifier;
4. status `PARTIAL` or `BLOCKED` with exact remaining blocker;
5. ordinary commit/push still required;
6. no Goal 024 work;
7. do not print success token.

## 20. Token/read discipline

Soft goal usage budget: `650000` tokens.

To remain inside it:

- use the supplied review evidence as an index, but verify exact named methods before edit;
- do not reread all earlier Goal implementations;
- no broad `grep` across other chronicles;
- reuse existing test harnesses/PowerShell 7;
- group focused runs after coherent changes;
- do not run full `ant verify` during exploration.
