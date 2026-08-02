# Goal 021 Checkpoint 1 — acquisition/spoil

## Статус

`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`

Terminal closure: `PARTIAL_AGGREGATE_RERUN_REQUIRED`. Реализация и финальное дерево собраны и прошли focused/affected/plain verify, но нормативный aggregate на финальном дереве не имеет зелёного запуска после последнего test-fixture исправления. Статус `ACCEPT` и success-token недопустимы.

- Ветка: `feature/phantom-world`.
- Test DB: `l2jmobiush5_phantom_test`.
- Seed всех Goal 021 routes: `21002101` через `phantom.goal021c1.seed`.
- Accepted Goal 020 parent: `d48dccb42dcfe5993f1c852e021086e498c0622d`.
- Foundation: `bf0cc37b2af7023f3709f635ae4350306b892597`, `feat(phantoms): add acquisition planning and spoil chains`.
- Safety completion: `c764382485d27391a6449aa4843d4f684efc1f12`, `fix(phantoms): complete acquisition eligibility and recovery`.
- Final closure: один ordinary direct child safety-коммита с subject `fix(phantoms): close acquisition recovery and recipe truth`; точный SHA фиксируется post-commit verifier и финальным handoff без amend.

Это первый из двух заранее запланированных checkpoint Goal 021, не Goal 021A/021B.

## Реализованный результат

- Strict acquisition XML и bounded `acquisition.state` с лимитом 4096 bytes.
- Progress вычисляется только из immutable baseline и authoritative current item count.
- Death-drop/spoil sources строятся из Game Knowledge, spawn и Topology authority; capability — из exact Progression/learned-skill evidence.
- Active spoil использует canonical Spoil 254, существующий Combat kill и canonical Sweeper 42; второго combat loop нет.
- Background death-drop/spoil выполняется существующей atomic Background transaction: item/background/Goal/acquisition изменяются all-or-none.
- Recipe path остаётся planning-only. Двухпроходный bounded probe обходит не более четырёх alternatives, depth 6 и 48 nodes на alternative; union exact ingredient IDs ограничен 128.
- Active inventory read выполняется через текущий Combat actor lease; background read валидирует profile, class и authority hashes и использует только explicit `IN (...)` IDs. Оба API возвращают immutable maps, включая нули для отсутствующих IDs.
- Только финальный recipe plan сохраняется; deficits, `inventoryUsed`, shared-ingredient accounting и alternative choice основаны на canonical counts.
- `preferredMethodBonus` участвует в score до canonical sort и ambiguity. Preference не делает ineligible/cooling source допустимым.
- `REJECTED` spoil/sweep после persisted dispatch освобождает lease, фиксирует ровно один source failure с exact reason и после третьего failure переводит состояние к bounded switch без blind recast.
- `COMBAT_SUBMITTED` сначала сверяет exact Goal/source/target owner. Foreign session не читается и не consume-ится; пропавшая session восстанавливается только по live target, owned spoiled corpse или item growth. Недостаточная evidence ограничена `verificationAttempts` и завершается `UNCERTAIN/BLOCKED` receipt.

## Границы scope

- Manor и quest collection: `DEFERRED_CHECKPOINT_2`.
- Craft, trade, private stores и enchant не исполняются; это Goal 022.
- `Player.java`, `Party.java`, schema, skill handlers, quest handlers и другие хроники не менялись.
- Новых production/data файлов final closure не добавляет; новых workers, библиотек и config keys нет.
- Phantom World остаётся выключенным по умолчанию.

## Изменённые файлы

Production/data — 11 файлов, новых production-файлов нет:

- `dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml`;
- `PhantomAcquisitionCatalog.java`, `PhantomAcquisitionRecipePlanner.java`, `PhantomAcquisitionService.java`, `PhantomAcquisitionSourcePlanner.java`, `PhantomAcquisitionState.java`;
- `PhantomBackgroundService.java`, `PhantomBackgroundTransaction.java`;
- `L2jCombatBackend.java`, `PhantomCombatActorLease.java`, `PhantomCombatService.java`.

Tests/build/verifier/docs — 7 файлов:

- `PhantomAcquisitionSuite.java`, `PhantomBackgroundSuite.java`, `PhantomCombatServerIntegrationSuite.java`;
- `build.xml`, `tools/phantoms/verify-task-021c1.ps1`;
- этот report и `docs/phantoms/reviews/021-checkpoint-1-independent-review.md`.

Итого 18 изменённых файлов при лимитах 12 production/data и 20 total. `PhantomAcquisitionStateCodec.java` не потребовал изменения.

## Архитектурные решения

- Exact inventory truth добавлен как узкий read-only API существующих active Combat lease и Background transaction; нового repository/component/schema нет.
- Missing `COMBAT_SUBMITTED` session согласуется через тот же ACQUISITION external lease. Новый Combat scheduler/loop не создавался.
- Recipe path остаётся planner-only: probe и exact read не сохраняют промежуточное состояние, а final plan не выполняет craft.
- Preferred method реализован policy value в strict XML и входит в общий score до canonical sort/ambiguity.

## DB и atomicity

Schema/migrations не менялись. Автоматические DB tests использовали только test DB. Проверены pre-commit rollback, post-commit recovery, exact replay, learned-skill drift и отсутствие read-side mutation. Background acquisition mutation по-прежнему коммитит canonical items, Background state, Goal и acquisition state одной транзакцией.

## История gate-сигналов

- Ранний foundation aggregate не был принят: active Spoil fixture зависел от unseeded magic-resist roll. Fixture был детерминирован без подмены NPC/drop authority.
- Ранее выполненный full `ant verify` с глобальным `-Dphantom.test.seed=21002101` не является валидным gate: общий harness ожидает собственные seeds и корректно отклонил override checksum.
- Для final closure разрешены новые terminal gates: Goal aggregate использует только `phantom.goal021c1.seed`, а full verification запускается plain `ant verify` без global seed override.

## Focused и affected проверки до freeze

- `compile-tests` — PASS.
- `phantom-acquisition-rejected-recovery-test` — PASS.
- `phantom-acquisition-combat-submitted-recovery-test` — PASS.
- `phantom-acquisition-recipe-inventory-test` — PASS: active и background вызывают `PhantomAcquisitionService.plan`; background использует временный test-DB ingredient и подтверждает zero craft/item mutation.
- `phantom-acquisition-preferred-method-test` — PASS.
- Affected acquisition/background/lifecycle, все четыре существующих Combat routes и отдельный Goal 015 regression — PASS одним bounded Ant invocation.
- `verify-task-020c2.ps1` в PowerShell 5.1/7.6 — `TASK020C2_VERIFIER_OK`, historical accepted Goal 020.
- Working `verify-task-021c1.ps1` в PowerShell 5.1/7.6 — byte-identical `GOAL_021C1_VERIFIED`.

## Финальные terminal gates

- Первый final `phantom-acquisition-checkpoint1-test` — PASS за 6:37 и выполнил исходные восемь, safety и closure routes с `phantom.goal021c1.seed=21002101`.
- Финальный diff audit после первого aggregate выявил реальный stale-authority риск background inventory read. Добавлены exact current-authority hash check и regression test; focused inventory test и working verifiers прошли.
- Второй и последний разрешённый aggregate — FAIL на `acquisition-active-spoil.02`: предыдущий test fixture вызывал `target.getStat().setLevel(1)`, но `Npc.getLevel()` читает immutable template level, поэтому magic-resist оставался случайным.
- Fixture исправлен только в `PhantomCombatServerIntegrationSuite`: test-owned Monster временно возвращает effective level 1 для canonical Spoil, затем восстанавливает template level до kill/drop calculation. Production chance/resist и canonical NPC/drop template не менялись. Focused active-spoil после исправления — PASS 3/3.
- Третий aggregate прямо запрещён verification authority. Поэтому на текущем финальном дереве нет допустимого terminal-green aggregate, хотя причина второго FAIL исправлена и focused route зелёный.
- Второй разрешённый plain `ant verify` без global seed override — PASS, `BUILD SUCCESSFUL`, 13:44.
- Standalone `ant jar` после финального production/test изменения — PASS, `BUILD SUCCESSFUL`, 17 секунд.

## Команды

- `ant compile-tests`;
- ordered focused targets для REJECTED, COMBAT_SUBMITTED, recipe inventory и preferred method;
- affected acquisition/background/combat/Goal 015 targets одним Ant invocation;
- `powershell.exe ... verify-task-020c2.ps1` и portable PowerShell 7 equivalent;
- `powershell.exe ... verify-task-021c1.ps1` и portable PowerShell 7 equivalent;
- два разрешённых запуска `ant phantom-acquisition-checkpoint1-test`;
- второй разрешённый plain `ant verify` без global seed override;
- standalone `ant jar`.

Для всех Ant-команд использовался portable Apache Ant 1.10.17. Все DB routes работали только с `l2jmobiush5_phantom_test`.

## Производительность и lifecycle

Affected и aggregate routes повторно подтвердили 100k indexed source plans, 10k bounded recipe DAGs и 10k acquisition Decision advances. Acquisition service не владеет worker/thread; terminal/recovery tests подтверждают нулевые retained transition/external/navigation claims.

## Отклонения и исправленные gate-сигналы

- Первый closure recipe-inventory focused run отклонил test fixture: единственный штатный fixture item 57 не входил ни в один production recipe DAG. Исправлен только test setup — canonical ingredient временно создаётся в test DB, а before/after assertions подтверждают, что production service его не расходует.
- Повтор recipe-inventory focused target прошёл. После первого зелёного aggregate финальный diff audit обнаружил stale background authority admission; production guard и regression test потребовали второй разрешённый aggregate.
- Второй aggregate остановился на недетерминированном Spoil fixture. Причина исправлена test-only subclass с отделением cast-level от canonical drop-level; отдельный active-spoil target после исправления прошёл 3/3.
- Ранний foundation aggregate с unseeded Spoil fixture и старый full verify с global seed override остаются исторически невалидными и не используются как terminal evidence.

## Ограничения и риски

- Полноценный pathfinding без geodata этим checkpoint не подтверждается.
- Recipe execution отсутствует намеренно.
- Независимое принятие checkpoint ещё не выполнено; этот отчёт не переводит статус в `ACCEPT`.
- Для полного terminal closure требуется новая явная verification authority на один aggregate rerun; текущая authority исчерпана и запрещает третий запуск.

## Следующий шаг

Получить отдельное разрешение на один aggregate rerun финального дерева, затем выполнить независимое review Goal 021 Checkpoint 1. Manor/quest Checkpoint 2 и Goal 022 не начинать до отдельного gate.
