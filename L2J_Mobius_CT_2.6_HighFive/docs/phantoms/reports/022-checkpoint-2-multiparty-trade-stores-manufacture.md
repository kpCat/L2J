# Goal 022 Checkpoint 2 — multi-party trade, stores and manufacture

## Status

`PARTIAL`: implementation имеет статус
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`, но terminal plain-verify gate не
закрыт.

- Branch: `feature/phantom-world`.
- Required parent: `feb569efa787917411cfb5c419f0e8646c3ee84f`.
- Commit subject: `feat(phantoms): add multiparty trade stores and manufacture`.
- Seed: `22002202`.
- DB: только `l2jmobiush5_phantom_test`.
- Goal 023 не начат.

Goal 022 Checkpoint 1 зафиксирован как
`ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER`: C1 aggregate,
affected regressions, verifiers и jar прошли; единственный plain verify упал на
историческом combat timing case, а точный isolated rerun прошёл `20/20` без
изменений source. Этот отчёт не утверждает, что C1 plain verify прошёл.

## Summary

- C2 entry gate использует индексированный lookup reservation `profile_id`
  независимо от current character link и revalidates participants до
  idempotent nonterminal result.
- Добавлен durable bounded offer lifecycle и participant model для Phantom и
  exact external Player/store owners.
- Ordinary direct-trade, private-store и manufacture packets делегируют единым
  packet-independent canonical services.
- Реализованы strict Goals и шесть Decision steps для direct trade, store BUY,
  store SELL и manufacture.
- Social mutation только active/perceptible; background execution возвращает
  `ACTIVE_REQUIRED` и сохраняет resumable operation.
- До первой mutation operation становится `OBSERVING`; exact-after commits,
  partial/ambiguous fail-stop-ится без redispatch.
- Phantom владеет видимыми SELL, PACKAGE_SELL, BUY и MANUFACTURE stores через
  durable store plan.

## Changed production/data

Новые:

- `phantom_reservations_checkpoint2.sql` — participant index и offer table;
- `PhantomEconomyOffer` / `PhantomEconomyOfferService`;
- `PhantomSocialEconomyGoalSpec`, `PhantomMultipartyEconomyDecision`,
  `PhantomMultipartyEconomyService`;
- `PhantomStorePlan`, `PhantomStoreService`;
- `DirectTradeService`, `PrivateStoreService`, `ManufactureService`.

Изменённые:

- `PhantomEconomyOperation`, `PhantomEconomyReservationService` и
  `PhantomEconomyMaterializationLifecycle`;
- `PhantomSystem` composition/startup/shutdown;
- `RecipeCraftObserver` и `RecipeManager` multi-party immutable evidence;
- `TradeList` observer/exact private-store seams;
- семь ordinary packet adapters для thin delegation.

`Player.java`, `Inventory`/`PlayerInventory`, `TradeItem`, другие хроники,
mail/warehouse/auction/combat/clan не изменялись.

## Architecture decisions

- Durable operation/reservations остаются единым C1 kernel; C2 не создаёт второй
  inventory или compensation engine.
- External participant имеет `profile_id=0`, но всегда exact character owner и
  authority evidence. Phantom links блокируются profile-first.
- TradeList monitors упорядочены по owner object ID после economy locks.
- Direct trade требует реального server-side accept/confirm второго владельца;
  Phantom не подделывает ordinary Player consent.
- Private-store exact path отклоняет stale quantity/price вместо client clamp.
- Результат empty headless store фиксируется economy observer до штатного
  store-close/disconnection cleanup.
- Manufacture формулы/RNG/ALT lifecycle остаются в `RecipeManager`.
- Новые worker/thread/executor/Future/task не добавлены; packet invocation и
  fake `GameClient` отсутствуют.

Подробности: `docs/phantoms/architecture/MULTIPARTY_ECONOMY_CONTRACT.md`.

## DB / migration

Additive idempotent migration создаёт индекс
`idx_phantom_economy_reservations_profile_operation(profile_id, operation_id)`
и InnoDB table `phantom_economy_offers`. C1 tables не перестраиваются.

Fresh test schema подготовлена штатной целью `prepare-phantom-test-db` только на
MariaDB `127.0.0.1:3308/l2jmobiush5_phantom_test`. Production DB не открывалась.

## Focused evidence before freeze

Все C2 modes используют seed `22002202`:

- participant index/link drift: `3/3` PASS;
- offer lifecycle: `2/2` PASS;
- direct trade: `2/2` PASS;
- private-store BUY/package/lifecycle: `2/2` PASS;
- private-store SELL: `1/1` PASS;
- manufacture: `1/1` PASS;
- restart/fail-stop: `1/1` PASS;
- performance: `1/1` PASS.

Все четыре six-step paths дополнительно доказывают
`BACKGROUND → ACTIVE_REQUIRED → ACTIVE canonical effect`.

Affected route PASS:

- все восемь C1 suites: `2/2`, `17/17`, `2/2`, `5/5`, `5/5`, `5/5`,
  `2/2`, `2/2`;
- commerce hardening `5/5`;
- acquisition source switching `5/5`;
- production materialization `20/20`;
- server shutdown handoff `7/7`.

Historical verifier 021c2 и 022c1 прошли под Windows PowerShell 5.1 и
PowerShell 7.6.3 с byte-identical output. SHA-256 outputs:

- 021c2: `3405dcd62da39d7c50bb57440a6b7cf9a5dbe2eea1ed6e714c5e4eb0e4db241a`;
- 022c1: `65c12e235c1170888459eba994d7aa28a571f73b4057dfc827102d85c5f6a793`.

## Performance evidence

Focused smoke выполнил:

- 100000 offer hashes и bounded lookup;
- 100000 participant/resource overlap checks;
- по 10000 direct/store/manufacture authority operations;
- 10000 expiry/cleanup checks.

Retained observers после terminal cases равны нулю.

## Additional READ_SET

Сверх исходного READ_SET открывались точные ближайшие symbols:

- `OfflineTradeConfig`, `OfflineTraderTable` и `Disconnection` — подтвердить
  существующий empty-store cleanup без redesign;
- `PrivateStoreType` и точный `Player.setPrivateStoreType` symbol — проверить
  headless close ordering без изменения Player;
- `TradeList` exact mutation methods и `RequestTrade` — request/list authority;
- `PhantomGoalStateStore` и profile component APIs — Goal/store durable writes;
- C1 report/verifiers и Goal 021 reports — historical/release pattern;
- Ant static verifier/verify targets — включить C2 в общий gate.

Другие chronicles и запрещённые subsystem families не сканировались.

## Deviations / limitations / risks

- Ordinary visible offline-store Player поддерживается только если он уже
  существует в canonical World/store state; C2 не эмулирует offline login.
- Manufacture normal/failure/rare выбирает текущий server RNG. Observer и
  reconciliation принимают только фактическую каноническую ветвь.
- ALT multi-pass остаётся штатной RecipeManager task; C2 не создаёт task.
- Independent verdict отсутствует; self-accept не выполнялся.

## Commands

Выполнены `ant compile`, восемь focused C2 targets, точный C2 affected target,
historical verifier 021c2/022c1 под PS5/PS7, final C2 aggregate, два разрешённых
full `ant verify` invocation, exact isolated C1 concurrency diagnostic,
standalone `ant jar` и bounded source/git inspections, разрешённые
TASK/Agents.md. Fresh schema подготовлена `prepare-phantom-test-db`.

Git использовался только для обязательных branch/status/diff/history/scope
проверок. Commit/push и terminal release commands фиксируются ниже.

## Terminal release evidence

- Final `phantom-economy-checkpoint2-test`: PASS; все восемь suite reports
  зелёные: `3/3`, `2/2`, `2/2`, `2/2`, `1/1`, `1/1`, `1/1`, `1/1`; seed
  `22002202`; 1:09.
- После aggregate production/data/test/build/verifier frozen; последующие
  изменения ограничены этим terminal report.
- Первый full `ant verify` invocation не получил terminal Ant result: внешний
  PowerShell с `ErrorActionPreference=Stop` остановил wrapper на ожидаемом
  negative-control `Java Result: 1`. До остановки все динамические reports,
  включая C2, были зелёными; все static targets затем прошли isolated. Этот
  invocation не представлен как PASS.
- Второй и последний разрешённый plain `ant verify`: FAIL через 12:35 на
  неизменённом C1 stress-case
  `economy-reservation-concurrency.participant-reverse-order-stress`, iteration
  34: `Economy participant evidence changed before lifecycle lock`; остальные
  104 обновлённых XML до точки остановки имели `failures=0`.
- Exact isolated `phantom-economy-reservation-concurrency-test` сразу после
  failure прошёл `17/17` за 0:21 без source changes, подтверждая timing-flake.
  Это не переписывает plain verify как PASS и не расширяет exact C1 combat
  waiver. Третий full verify запрещён TASK.md.
- Standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:16; `GameServer.jar` и
  `LoginServer.jar` собраны и скопированы в `dist/libs`.
- Terminal gate status: `PARTIAL`. Ordinary commit/push и два byte-identical
  post-commit verifier 022c2 выполняются как honest bounded handoff; success
  token не печатается.
- Mojibake-маркеры в изменённых файлах проверяются отдельным финальным guard.
- Escaped Cyrillic в изменённых файлах проверяется отдельным финальным guard.

## Next step

Независимый review должен оценить C2 implementation и незакрытый plain-verify
gate. Goal 023 не начинать.
