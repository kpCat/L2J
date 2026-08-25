# Goal 030 Checkpoint 2 — consolidated release closure

## Status

**BLOCKED**

`Goal030` остаётся `IN_PROGRESS`, CP2 — `BLOCKED / IN_PROGRESS`, CP3 — `NOT_STARTED`. Coverage matrix не повышалась: `11 COVERED_PRIOR / 6 COVERED_CP1 / 0 COVERED_CP2 / 3 PENDING_GOAL030`. Pending остаются `activity-materialization:CP2`, `restart-failure-recovery:CP3`, `rollback-release-control:CP3`.

Причина остановки: обязательный focused Repair 1 target не стал green в разрешённые два runtime-запуска. Второй запуск дал `1/3 PASS`; две оставшиеся ошибки точно локализованы в synthetic test clock, исправлены после запуска, но третий focused run запрещён efficiency budget. Без green focused regression CP2 runtime не запускался.

## Summary

Part A выполнена: `CODEX_WORKFLOW_CONTRACT.md` получил нормативный checkpoint/release repair budget и guidance по narrow reads, focused gates и model/reasoning metadata.

Repair 1 реализован частично и компилируется:

- добавлен source-compatible `PhantomConversationGoalRuntimePort` с `SYNCHRONIZED / BUSY / UNAVAILABLE / FAILED`, `NOOP`, one-shot delegate bridge и canonical DecisionEngine adapter;
- adapter использует только `DecisionEngine.find/reload`;
- exact runtime goalId + revision `>= minimumRevision` возвращает `SYNCHRONIZED` без reload;
- `RELOADED` повторно проверяет exact identity/revision; `BUSY`, `REJECTED/NOT_RUNNING`, persistence conflict/failure отображаются по task contract;
- Conversation после единственной atomic `conversation.execution + goal.state` mutation вызывает runtime synchronization;
- durable ACTIVE `SUBMITTED` повторно проверяет runtime sync перед обычным наблюдением; `BUSY/UNAVAILABLE` сохраняют тот же entry и используют существующую delayed pulse scheduling; `FAILED` переводится в bounded `UNCERTAIN/execution.failed`;
- `PhantomSystem` создаёт bridge до ConversationExecution и устанавливает DecisionEngine delegate после `DecisionEngine.start()` и до `Scheduler.start()`, без owner reorder.

Audit terminal/abandon paths нашёл тот же потенциальный stale-runtime hazard в `resolveMissingSubmittedInvitation`, `rejectSubmittedGoal` и `expireOwnedGoal`. Эти пути не расширялись после остановки focused gate; это остаётся обязательным bounded продолжением перед SUCCESS.

## Durable vs runtime evidence

Pre-audited production root подтверждён чтением exact parent: Conversation store атомарно меняет execution component и `goal.state`, а Decision scheduler работает из attached runtime slot. Новый adapter является явной post-commit границей и не добавляет GoalStore polling в DecisionEngine.

Runtime proof не может быть заявлен green: focused cases 01/02 в последнем разрешённом запуске истекли до Goal submission из-за test clock mismatch. После запуска fixture переведена на existing explicit `() -> 101` constructor; повтор не выполнялся.

## Focused tests

Target: `phantom-conversation-decision-runtime-sync-goal030cp2-test`, seed `30003024`, guarded DB, no provisioning, forked, timeout `180000 ms`.

1. Invocation 1: compile-stage FAIL до suite runtime — test fixture ошибочно ожидал boolean от `DecisionEngine.start()`. Production compile прошёл; fixture исправлена под подтверждённый `void` API. Runtime runs: `0`.
2. Runtime run 1/2: `0/3 PASS`. Cases 01/02 получили `NoSuchElementException`, case 03 показал два общих dispatch. Root: cases 01/02 использовали production wall clock для synthetic minute-100 plan; case 03 считал goal acknowledgement и query вместе.
3. Runtime run 2/2: `1/3 PASS`. Case 03 PASS после exact query `planId` accounting; cases 01/02 повторили expiry до Goal submission. После запуска оба constructors исправлены на explicit minute `101`; третий run не выполнен.

Последний фактически выполненный результат: total `3`, passed `1`, failed `2`.

QUERY-priority доказан case 03: при сохранённом Conversation GOAL `SUBMITTED` новый real `item.source` QUERY для ITEM57 terminalized independently и получил exact один `SENT` outbound по своему planId. Repair2 не потребовался.

## Repair ledger

- Repair 1: **IMPLEMENTED_BUT_FOCUSED_GATE_NOT_GREEN**.
- Repair 2: **NOT_USED**. Distinct production/query blocker не найден.
- CP2 fresh runtime runs: `0/2`.
- CP2 utterance diagnostics, real Party invite/accept, social delta, ITEM57 production response, leave, rematerialization и shutdown evidence: `NOT RUN / NOT CLAIMED`.

## Commands and results

- exact parent/branch/upstream checks: PASS, parent `632fd5eb1db625d6e1614a70d3ef0b0ab2559f7b`, `feature/phantom-world`, `origin/feature/phantom-world`;
- standalone `compile-tests`: PASS, 2219 production sources + 123 test sources, 2 unrelated deprecation warnings;
- focused target invocation 1: compile-stage FAIL;
- focused runtime run 1/2: FAIL `0/3`;
- focused runtime run 2/2: FAIL `1/3`;
- CP2 target: NOT RUN;
- CP1/conversation checkpoint2/Party/materialization final gates: NOT RUN;
- jar: NOT RUN, count `0`;
- CP2A/B/C, 030A/B/C, Goal024, soak, verify, broad aggregates и geodata не запускались.

## DB, config, performance and architecture

Guarded test DB использовалась только существующим no-provision focused target; production DB не использовалась. Schema/migrations/config отсутствуют. Новых worker/timer/cache/global policy/public redesign нет. Decision hot path не менялся, per-pulse DB polling не добавлен. Performance CP2 не измерялась, поскольку CP2 не запускался.

## Changed files

- `docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationGoalRuntimePort.java`
- `java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java`
- `java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java`
- `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java`
- `build.xml`
- `docs/phantoms/reports/030-checkpoint-2-cross-domain-autonomous-alpha.md`
- `docs/phantoms/reports/030-checkpoint-2-release-closure.md`

Пользовательский modified `PhantomReleaseBaselineGoal030Checkpoint1Suite.java` и untracked task packages не изменялись и не включаются в commit.

## Process truth

- `apply_patch`: 1 invocation, ACL-rejected до чтения/mutation; applied changes `0`; retry `0`.
- Далее использован bounded UTF-8-no-BOM temp + atomic `Move-Item` fallback.
- Один CRLF anchor fallback остановился до записи; последующий LF-aware fallback применён.
- Несколько source anchor/parser попыток остановились до atomic move; partial files не оставлены.
- Standalone compile cycles: `1`.
- Focused target invocations: `3`; suite runtime runs: `2`.
- CP2 runtime runs: `0`.
- Context compactions: `0`.
- Token usage: goal API не вернул счётчик; значение не выдумывалось.

## Git

- Parent: `632fd5eb1db625d6e1614a70d3ef0b0ab2559f7b`.
- Branch/upstream: `feature/phantom-world` / `origin/feature/phantom-world`.
- Preferred subject: `fix(phantoms): close autonomous alpha integration`.
- Commit SHA и push result: см. финальное сообщение; отчёт входит в commit.

## Next step

Новый task/suffix не требуется. При явно обновлённом focused-run budget: выполнить ровно один focused run на уже исправленном explicit-clock fixture; при PASS завершить terminal/abandon runtime-sync helper regression, затем продолжить исходный CP2 run 1/2. До этого matrix и release status не повышать.
