# Goal 024 — farming resource negotiation

## Status

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Required parent: `e67298697eaecc629a03b215a78ffa947233efd3`
Branch: `feature/phantom-world`
Required commit subject: `feat(phantoms): add farming resource negotiation`
Seed: `24002401`
Goal 025+: `NOT_STARTED`

Goal 023C зафиксирован как `ACCEPT`, `R023C-01` — `CLOSED`, Goal 023 overall — `ACCEPT` на required parent. Исторические `CHANGES_REQUIRED` exact baselines 023/023A не переписаны.

## Summary

Добавлен один worker-free farming conflict kernel, который строит claim из exact current Goal021 acquisition state, использует current bounded perceptibility Goal010, exact same-Party state Goal017, social evidence/history Goal018 и typed query/language Goal020. Новых acquisition, topology, Party, social или chat kernels нет.

Goal021 получил узкий fail-neutral `PhantomFarmingConflictPort`. Двусторонняя persisted negotiation пишет lower profile id, затем higher profile id; никакой effect, gate agreement или Goal020 fact не появляется до exact two-sided FINAL. `ESCALATE` сохраняет только semantic/social evidence для будущего Goal025 и не вызывает PvP/combat.

## Read-first evidence

Обязательные документы прочитаны полностью: `AGENTS.md`, `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`, `docs/PHANTOM_BOTS_ROADMAP.md`, `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `docs/phantoms/TASK_PACKAGE_STANDARD.md`, report template, reports/reviews 023/023A/023B/023C и весь `docs/phantoms/tasks/024-farming-resource-negotiation/` (`TASK.md`, `ARCHITECTURE.md`, `TEST_CASES.md`, `ACCEPTANCE.md`, `CONTEXT.md`, `PRIOR_INDEPENDENT_REVIEW.md`, manifest/launcher).

Pre-audited production set прочитан:

- Goal021: `PhantomAcquisitionGoalSpec`, `PhantomAcquisitionState`, codec/store/source planner/service/decision и acquisition policy XML;
- Goal010: topology service/query/profile registry/perception provider/signal ledger/node kind/anchor role/perception channel;
- Goal018: social model/service/catalog/store и social XML;
- Goal020: conversation execution model/catalog/service/port/L2j port и оба execution/mapping XML;
- Party/decision/composition: `PhantomPartyCoordinator`, `PhantomGoal`, `PhantomGoalStateStore`, candidate/step registries, `PhantomEconomyConflictPort`, `PhantomSystem`;
- launcher-registered acquisition core/source/active/background/restart, topology perception/signal-ledger/generation, Party state/route, social core/integration, semantic/corpus, conversation catalog/query, profile component/fault patterns, Goal023C tests; `PhantomTestLauncher`, relevant `build.xml`, verifier 023C.

Локальные аналоги: Goal022 conflict port для fail-neutral seam; Goal017 Party persisted saga и stable-id recovery; Goal018 profile component CAS/idempotent events; Goal020 typed bounded query facts/catalog; Goal010 generation lease + node registry; Goal021 existing `SWITCH`/`switchSource` path. README, CODE_MAP и отдельные pattern-файлы для этого модуля не найдены при initial read-first pass и повторно не искались.

Дополнительные High Five чтения/изменения сверх predicted production set:

- `PhantomSemanticPack.java` и semantic XML/corpus — exact call path `farming.conflict.query` потребовал зарегистрированный Goal019 intent и deterministic utterance grounding;
- conversation corpus — exact Goal020 proposal mapping regression;
- `PhantomConversationExecutionCatalog.java`/model — только расширение существующего required fact-label/query allowlist; локальный 32-byte UTF-8 label bound переиспользован;
- `PhantomAcquisitionSuite.java` — real production Goal021 gate regression; `PhantomFarmingSuite.java` — focused Goal024 modes;
- `PhantomCombatServerIntegrationSuite.java`, `L2jCombatBackend`, `PhantomCombatService`, `Player`/`PlayerAI`, штатные `Harvester`/`Harvesting` и item/skill XML 5125/2098 — прочитаны после первого final aggregate failure по exact manor-active call path; изменён только test fixture, чтобы дождаться штатного Combat action quiescence и проверить Harvester dispatch status/phase;
- `PhantomSemanticSuite.java` — прочитан после первого plain verify failure; stale exact Goal019 counts обновлены с 240/14 до фактических 242/15, а `farming.conflict.query` добавлен в explicit required-intent contract;
- `PhantomActivationGateSuite.java` — прочитан после следующего plain verify failure; pinned Goal020 hashes обновлены на фактические SHA-256 Goal024 semantic pack/corpus, а synthetic pattern addition уменьшен с 88 до 86, чтобы сохранить прежний exact hard limit 128;
- `PhantomConversationSuite.java` — прочитан после следующего downstream failure; stale Goal020 corpus count обновлён с 128 до фактических 129 после единственного нового `farming.conflict.query` case;
- master/roadmap/review/report/contract/build/verifier/launcher — обязательные process artifacts.

Другие хроники не читались и не изменялись. Global scans не выполнялись.

## Resource identity and claims

Exact examples:

- ROOM: `ROOM|catacomb.room.17`; anchors `door.left`/`door.right` и разные NPC внутри exact room node конфликтуют;
- outdoor MOB_GROUP: `MOB_GROUP|field.gludio.north|spawn.201|npc.20001`;
- тот же anchor/NPC в `field.gludio.south` — другой resource;
- `RECIPE_PREPARATION` не создаёт farming claim.

Claim содержит Goal id/revision, Source id/method/node/anchor/NPC, required/progress/remaining, priority, acquisition row/evidence, topology authority/generation, age, bounded alternatives и switch feasibility. Повтор без изменения exact facts не делает durable write; lease expiry, revision/source drift и restart revalidation fail closed.

## Gate call flow

```text
Goal021 directive at TRAVEL_REQUIRED/TARGET_REQUIRED
  -> conflictSnapshot(current Goal/Source/required/progress/remaining)
  -> PhantomFarmingConflictPort.evaluate
     ALLOW/SHARE       -> existing acquisition directive
     NEGOTIATE/WAIT    -> BLOCKED/replan, no new resource work
     MOVE/STALE        -> existing SWITCH -> acquisition.switchSource
```

Direct safe-boundary recheck выполняется перед новым travel/active/background work. Уже dispatched combat/action не отменяется. Незарегистрированный port возвращает ALLOW и сохраняет legacy behavior. Farming service не вызывает `switchSource` напрямую.

## Perception bounds

Goal010-owned query принимает explicit limit 1..1023; Goal024 policy передаёт 32. Registry получает максимум `limit + 1`, результат исключает observer, сортируется существующим registry order и режется до limit. Используются current generation, same node/one-hop и existing door/channel/radius rules. Claim bucket ограничен восемью claimants и не заменяет current perceptibility proof. `TopologyService.listProfiles()`, `World.getPlayers()` и четвёртый topology signal source отсутствуют.

## Bilateral persistence and fault matrix

Stable write order: lower profile id → higher profile id.

| Fault point | Durable state after fault | Effect before recovery | Recovery |
|---|---|---:|---|
| after OFFER | lower active only | none | same agreement id resumes RESPONSE |
| after RESPONSE | both active | none | same evidence resumes FINAL |
| after first FINAL | lower final, higher active | none; gate/query suppress one-sided receipt | writes exact mirror |
| after second FINAL | exact two-sided final | allowed only after reread/exactPair | idempotent effect/social replay |

Receipt связывает обе Goal revisions/sources и оба real remaining. Arbitration evidence включает обе стороны: remaining ratio, progress ratio, Goal priority, bounded alternative, claim age, Goal018 `goal.persistence`, `conflict.escalation`, relationship/cooperation, topology authority/generation и deterministic id tie-break. Three-claimant regression допускает не более одного exclusive ALLOW holder.

Все semantic acts покрыты dynamic tests: `SHARE`, `WAIT`, `MOVE`, `REFUSE`, `ESCALATE`. Exact same Party немедленно получает SHARE без bilateral rounds. WAIT не создаёт tight Decision retry; MOVE делегируется Goal021. ESCALATE не содержит attack/combat/navigation side effects.

## Social and conversation

Goal018 catalog получил events:

- `1016 farming.agreement.offered`;
- `1017 farming.agreement.accepted`;
- `1018 farming.agreement.refused`;
- `1019 farming.conflict.escalated`.

Все события idempotent по exact agreement/resource evidence. Generic `agreement.fulfilled`/`agreement.broken` сохранены и фиксируются обеим сторонам ровно один раз.

Goal020 mapping `farming.conflict.query` возвращает bounded unique facts: claim status, own/counterpart remaining, alternative, resource/counterpart, negotiation act/escalation, agreement. Stale или one-sided agreement подавляется. Обычный human `Player` без Phantom profile/current Goal021 state получает пустой результат: fabricated PhantomGoal, claim и auto-agreement не создаются. Farming service не отправляет direct chat packets.

## Policy, persistence and DB

Policy: `dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml`, strict schema identity/version, exact section/attribute allowlists, numeric ranges, XXE/DOCTYPE off, SHA-256 content hash. Bounds: lease 3, TTL 10, wait 5, cooldown 2 minutes; rounds 3; alternatives 4; claimants 8; perception 32; history 4.

Persistence: existing versioned profile component `farming.conflict`, schema 1, bounded codec/state, optimistic CAS and lazy load/revalidation. SQL/migration не добавлены. Startup full-table scan отсутствует. Production DB `l2jmobiush5` тестами не изменялась; DB-backed regressions использовали только guarded `l2jmobiush5_phantom_test` и существующую очистку своей test schema.

## Lifecycle and performance

Worker, Future, timer, per-phantom scheduler отсутствуют. Shutdown закрывает operation claims, снимает static port до раннего failure return и очищает runtime claim/resource maps. Lifecycle regression подтвердил STOPPED, activeClaims=0, operationClaims=0 и отсутствие worker primitive fields.

Targeted performance smoke: 100000 uncontested gate operations, `farming.gateElapsedNanos=328154700` (примерно 3.282 мкс/operation на текущем хосте), maximumBucketSize=1, leaked operationClaims=0. Метрика диагностическая, не latency SLA.

## Changed files

Production:

- new `java/org/l2jmobius/gameserver/phantoms/farming/`: conflict port, model, policy, persistence port/store/codec, conversation facts, service, decision;
- `PhantomAcquisitionService.java`: exact snapshot и narrow boundary gate;
- `PhantomTopologyService.java`: bounded `perceptibleProfiles`;
- `PhantomSystem.java`: lifecycle/composition/registries/query wiring;
- Goal020 execution port/catalog/model и Goal019 semantic pack registrations.

Data:

- new farming policy XML;
- social events 1016–1019;
- semantic intent/corpus and conversation mapping/execution/corpus for `farming.conflict.query`.

Tests/build/process:

- new `PhantomFarmingSuite.java`; updated acquisition suite and launcher;
- bounded regression updates в `PhantomCombatServerIntegrationSuite.java`, `PhantomSemanticSuite.java`, `PhantomActivationGateSuite.java` и `PhantomConversationSuite.java`;
- Goal024 targets/seed/aggregate and historical 023C descendant invocation in `build.xml`;
- new architecture contract, report, review handoff and pinned verifier 024;
- master/roadmap statuses;
- normative task package `docs/phantoms/tasks/024-farming-resource-negotiation/` was supplied untracked at baseline, read completely, not rewritten, and is included as the Goal artifact package.

## Verification results

Pre-freeze diagnostics:

- production/test compile: PASS;
- all eight focused farming modes: PASS;
- real Goal021 farming gate mode: PASS;
- Goal020 facts and lifecycle/performance after final code edge fixes: PASS;
- affected Goal010/017/018/019 modes before Goal020: PASS;
- Goal020 catalog initially failed because new Russian labels exceeded the existing 32-byte UTF-8 contract; labels were shortened in normal Cyrillic, then catalog/query tests passed;
- one existing Goal021 active-spoil crash-recovery run stopped at target HP=1 with farming port uninstalled; exact rerun with seed 21002101 passed 3/3, so production/test code was not changed for the timing flake.
- первый explicit final Goal024 aggregate дошёл до existing Goal021 manor-active smoke и упал: `Service Harvester produced no bounded crop delta`, seed `21002102`; три немедленных targeted запуска на том же seed прошли 3/3, что подтвердило lifecycle race, а не random harvest outcome;
- локальные Goal021 аналоги уже ждут `!isCastingNow() && !isAttackingNow()` между acquisition action и следующим lifecycle этапом. Manor fixture получил тот же bounded quiescence barrier после Combat cleanup и explicit проверки `SUCCESS`/`HARVEST_DISPATCHING`; production-код не менялся. Это bounded exception для второго final aggregate.
- первый plain `ant verify` прошёл compile/jar и широкий regression набор, затем deterministic остановился в `semantic-pack.01`: expected 240 corpus cases, actual 242. Exact audit также выявил следующий stale intent count 14 против 15; тест обновлён на фактические Goal024 additions и explicit `farming.conflict.query`. Freeze пересоздаётся перед повторным verify; production semantic code/data после этого failure не менялись.
- второй plain `ant verify` подтвердил обновлённый Goal019 suite, затем остановился в Goal020 semantic activation: pinned pack/corpus hashes не учитывали Goal024, а synthetic 88 patterns вместе с двумя новыми production patterns превышали `maxPatterns=128`. Pinned hashes обновлены до `16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18` и `2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E`; synthetic addition уменьшен ровно до 86. Targeted activation suite прошёл 3/3.
- третий plain `ant verify` подтвердил activation suite 3/3 и остановился на следующем stale Goal020 corpus count 128 против фактических 129. `PhantomConversationSuite` обновлён на 129; targeted catalog/codec suite прошёл 2/2. Bounded audit соседних Goal020 count assertions дополнительных stale contracts не нашёл.
- следующий полный прогон продолжил работу после 15-минутного timeout tool cell и создал PASS reports вплоть до поздних historical suites, но terminal exit evidence было потеряно; результат не засчитан. Byte-identical captured повтор без изменений freeze завершился exit code 0 за 21 минуту 6 секунд.

Final freeze gate:

```text
FINAL_GATE_RESULT=PASS
final explicit Goal024 aggregate: PASS (5 minutes 57 seconds)
final freeze: PASS (32 files, C14185428EB3B7D99BD36DB96B5CD97F878794CBD40B1BB1D93046F9B1187DE7)
plain ant verify: PASS (captured exit 0, 21 minutes 6 seconds)
standalone ant jar: PASS (14 seconds)
working verifier024 PowerShell 5.1: PASS (scope 46)
working verifier024 PowerShell 7: PASS (scope 46)
historical verifiers 023/023A/023B/023C: PASS
post-commit PS5.1/PS7 stdout byte-identical: REQUIRED_AFTER_PUSH
production DB guard: PASS (test schema only; no production DB mutation)
```

Mojibake-маркеры в изменённых файлах проверены: PASS, 46 files.
Escaped Cyrillic в изменённых файлах проверены: PASS, 46 files.

## Git and delivery

Baseline Git commands, разрешённые TASK: `git status --short --branch`, `git rev-parse HEAD`, `git branch --show-current`, `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`, `git show -s --format=%H%n%P%n%s HEAD`, `git diff --check`; в ходе scope review также bounded `git status --short`, `git diff --name-only`, `git diff --stat`, `git diff --numstat` и full Goal024 diff. История не переписывалась; reset/rebase/amend/merge/force запрещены и не использовались.

Commit SHA в этом одно-коммитном отчёте является self-reference и определяется самим direct-child commit после freeze. Push выполняется после ordinary commit; post-commit verifier требует remote ancestry и тем самым является проверяемым push result. Exact итоговые SHA/push evidence доступны в Git history и terminal handoff без второго/amend commit.

## Deviations, limitations and risks

- `apply_patch` sandbox helper на Windows возвращал ACL deny-read; все изменения всё равно применялись официальным Codex apply-patch driver из временного каталога, не прямой перезаписью файлов.
- Plain `ant` отсутствовал в PATH; использован существующий Apache Ant 1.10.17 по `C:\Users\endim\.cache\codex-ant\apache-ant-1.10.17\bin\ant.bat`. Финальная plain Ant semantics — target `verify` без дополнительных свойств.
- Геоданные/pathfinding не являются частью Goal024 и не доказываются этим gate.
- ESCALATE намеренно не исполняется; combat/PvP принадлежит будущему Goal025.
- Независимый review Goal024 ещё не выполнен; self-accept отсутствует.

## Next step

Независимый review Goal024 по `docs/phantoms/reviews/024-independent-review.md`. Goal025+ не начинать до отдельного принятия gate.
