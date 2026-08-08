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

## Terminal completion authority (supersedes the earlier PARTIAL handoff)

Этот bounded completion выполняется direct ordinary child от foundation
`5fd8dcfc1b294e234cc55aaabc0cbfbbd134e1f7` с exact subject
`fix(phantoms): close multiparty economy causality and lifecycle`. Предыдущий
PARTIAL-раздел сохранён как historical evidence и не является текущим статусом.

Текущий completion status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
Accepted C1: `feb569efa787917411cfb5c419f0e8646c3ee84f`.
C1 review authority: `ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER`.
Deterministic seed: `22002202`.

Production completion закрывает exact direct-trade offer под canonical locks,
ActionLease всех Phantom participants, strict private-store listing/object
authority, structured manufacture admission и полную craft authority, terminal
manufacture recovery, truthful store close/shutdown и deterministic terminal
participant race. Regression выполняет 2000 deterministic terminal-boundary
итераций в обоих caller orders с одной terminal state/audit и без retained
reservations. Parent/current packet matrix сохраняет byte-identical adapters для
семи ordinary trade/store/manufacture packets; Phantom packet calls отсутствуют.

Focused C2 routes, полный C1 aggregate, Goal 014/021 affected regressions и
C2 affected target прошли. Historical verifiers 021c2 и 022c1 прошли под
PowerShell 5.1 и 7.x с byte-identical output. Working verifier 022c2 и final
release commands фиксируются следующим terminal amendment без production, test
или verifier изменений.

## Final successful terminal amendment

- Final `phantom-economy-checkpoint2-test`: PASS, восемь suite summaries
  `3/3`, `2/2`, `2/2`, `2/2`, `1/1`, `1/1`, `1/1`, `1/1`; seed
  `22002202`; `BUILD SUCCESSFUL`; 1:18.
- После aggregate production/data/test/build/verifier были frozen; дальнейшее
  изменение — только этот terminal report.
- Третий и последний разрешённый plain `ant verify`: PASS,
  `BUILD SUCCESSFUL`; 17:02. Expected negative controls завершились ожидаемыми
  non-zero Java Result, итоговый Ant gate зелёный. Четвёртый verify не запускался.
- Standalone `ant jar`: PASS, `BUILD SUCCESSFUL`; 0:17; `GameServer.jar` и
  `LoginServer.jar` собраны и скопированы в `dist/libs`.
- Working verifier 022c2 прошёл под PowerShell 5.1 и 7.x с byte-identical
  output SHA-256
  `157445e3ecd1af8e7cba1bd6885ec1424354e39275a212d41280dc07ce2ded9f`.
- Final status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Commit/push и два
  accepted byte-identical verifier runs выполняются после freeze; exact commit
  является единственным direct child foundation и идентифицируется Git history.
- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверены.

Следующий шаг — только независимый review Goal 022; Goal 023 не начат.

## Final external-trade/manufacture lifetime completion

Этот раздел явно supersedes предыдущий terminal handoff. Required causality parent:
`988ca85e91fb0e3aa2f58dc2aaa1e4277290e1a2`. Exact child subject:
`fix(phantoms): close external trade and manufacture observer lifetime`.

Текущий статус Goal 022 Checkpoint 2 и Goal 022 overall:
`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`. Self-accept не выполнялся, Goal 023 не начат.

Закрытые lifetime-инварианты:

- внешний ordinary Player подтверждает direct trade первым; Phantom не подделывает
  consent и не устанавливает исполняющий observer до canonical confirmation;
- synchronous exchange выполняется только при удерживаемом exact Phantom ActionLease;
- timeout/refusal/stale/cancel/shutdown сначала очищают exact canonical trade pair,
  затем завершают durable operation/offer;
- manufacture intermediate mismatch/fault только taint-ит observer и сохраняет
  OBSERVING, reservations и participant leases до canonical terminal event;
- canonical manufacture abort принадлежит `RecipeManager.requestMakeItemAbort`;
- durable terminal transition освобождает observer/leases в `finally`, включая
  `AFTER_OPERATION_AUDIT`, а accepted offer восстанавливается из terminal operation;
- strict private-store path выполняет полный aggregate preflight до первой mutation;
- bounded shutdown возвращает честный `ShutdownResult`, а `PhantomSystem` fail-stop-ит
  при незавершённой protection ownership.

Focused dynamic evidence до terminal freeze: direct fault matrix достигла 9 applicable
points и прошла `4/4`; manufacture matrix достигла fee/ingredients/product boundaries
и прошла `1/1`; strict BUY/SELL aggregate preflight и external delayed-confirmation
lifetime regressions прошли. Seed: `22002202`, DB только `l2jmobiush5_phantom_test`.

Verifier 022c2 отдельно фиксирует foundation, causality completion и единственный
terminal child; packet adapters сравниваются byte-identical с causality commit.
Итоговые aggregate/plain-verify/jar/commit/push/post-commit verifier evidence
добавляются ниже только после фактического выполнения terminal release sequence.

## Final terminal release evidence

- `compile-tests`: `BUILD SUCCESSFUL`, 2141 production и 84 test sources.
- Focused external/direct route: `4/4`; дополнительно динамически проверены refusal,
  disconnect, cancel, active shutdown и stale line после ordinary confirmation.
- Все восемь original C2 modes: `BUILD SUCCESSFUL`, seed `22002202`.
- C1 aggregate: `BUILD SUCCESSFUL`, seed `22002201`.
- Goal 014/021 affected aggregate: `BUILD SUCCESSFUL`.
- Historical verifier 021c2 и 022c1: PS5/PS7 зелёные и descendant-compatible.
- Working verifier 022c2: PS5/PS7 зелёные с одинаковым normalized output.
- Единственный final `phantom-economy-checkpoint2-test`: `BUILD SUCCESSFUL`, 1:10.
- Freeze: 8 файлов, 4 production, 0 новых файлов; SQL/policy/packet changes = 0;
  восемь packet adapters byte-identical к `988ca85e91fb0e3aa2f58dc2aaa1e4277290e1a2`.
- Четвёртый и последний plain `ant verify`: `BUILD SUCCESSFUL`, 16:34.
  Пятый plain verify не запускался.
- Standalone `ant jar`: `BUILD SUCCESSFUL`, 0:16; GameServer/LoginServer jars
  пересобраны штатной целью.
- Production/test/verifier freeze SHA-256 зафиксированы в terminal handoff;
  post-freeze изменён только этот report evidence.
- Mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- Escaped Cyrillic в добавленных строках изменённых файлов проверены: совпадений нет.

Commit/push и два accepted byte-identical verifier запускаются после создания
