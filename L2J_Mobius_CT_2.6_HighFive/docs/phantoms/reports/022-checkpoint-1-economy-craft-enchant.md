# Goal 022 Checkpoint 1 — economy, craft и enchant

## Status

`COMPLETED_PENDING_INDEPENDENT_REVIEW`.

Required parent: `043844c0fd7a0bfcac0d5f58461a21633b032332`.
Branch: `feature/phantom-world`.
Commit subject: `feat(phantoms): add economy reservations craft and enchant`.
Commit SHA определяется post-commit verifier как единственный ordinary child
required parent.

## Summary

Goal 021 Checkpoint 1, Checkpoint 2 и overall зафиксированы как `ACCEPT`.
Verifier 021c2 переведён на historical accepted blobs и descendant-compatible
HEAD. Goal 022 реализован как первый из двух заранее запланированных
checkpoints, не как 022A/022B; Checkpoint 2 не начат.

Добавлен participant-neutral durable operation/reservation/audit kernel с
stable DB lock order, bounded admission, predispatch-only expiry и fail-stop
restart reconciliation. Один conflict port подключён к NPC commerce,
acquisition/background writers и materialization lifecycle.

Active `SELF_CRAFT` использует immutable observer seam `RecipeManager`.
Background craft атомарно проецирует current `RecipeData`/config и exact Goal
021 `RecipePlan` вместе с Goal/acquisition/background/audit.

Current enchant mutation извлечена в `EnchantItemService`; ordinary packet стал
тонким adapter. Active/background `ITEM_ENCHANT` используют exact objects,
canonical success/safe/blessed/destruction/crystal branches и explicit risk
policy. Equipped background target даёт `ACTIVE_REQUIRED`.

## Changed files

Новые production/data artifacts:

- `phantoms/economy/**` — operation, policy, reservations, projection,
  background transaction, service, Decision и lifecycle integration;
- `RecipeCraftObserver.java` — immutable active craft observation seam;
- `EnchantItemService.java` — canonical active enchant mutation;
- `phantom_reservations.sql` — три durable InnoDB tables;
- `high-five-economy-v1.xml` — strict content-addressed policy.

Изменённые production integration points:

- `RecipeManager`, enchant immutable-template APIs и `RequestEnchantItem`;
- `PhantomSystem`, commerce backend, acquisition service/state, background
  model/hash/transaction и materialization lifecycle chain.

Tests/build/tools/docs:

- `PhantomEconomySuite`, `PhantomTestLauncher`, `build.xml`;
- verifier 021c2, verifier 022c1, Goal 021 final review;
- master plan, roadmap, architecture/report/review и исходный task package.

`Player.java`, inventory core, `TradeList`/`TradeItem`, direct-trade и
private-store packets не изменялись.

## Architecture decisions

- Не создавался fake/null-network `GameClient`; packets не являются Phantom API.
- Durable authority хранит identity/before/intent/reservations, а не копию
  inventory.
- Один Decision step выполняет только reserve, dispatch или reconcile.
- Background mutation блокирует exact rows и коммитит operation/audit/Goal/
  acquisition/background в одной MariaDB transaction.
- После dispatch неопределённость не разрешается timeout/retry: только exact
  reconciliation либо `INCONSISTENT`.
- Kernel не создаёт worker/thread/executor/Future/scheduled task.

Подробный контракт: `docs/phantoms/architecture/ECONOMY_TRANSACTION_CONTRACT.md`.

## DB / migrations

Migration `dist/db_installer/sql/game/phantom_reservations.sql` создаёт
`phantom_economy_operations`, `phantom_economy_reservations` и
`phantom_economy_audit`. Имя обеспечивает применение после parent
`phantom_profiles.sql` штатным лексикографическим runner.

Fresh provisioning выполнен только для `l2jmobiush5_phantom_test`:
119 scripts, 210 statements, schema aggregate
`7C34F6CEDAD175208C31F65121C61850C8C62536EC2682894592EA6D73EEFDB5`.
Production DB не использовалась.

## Config / data

Policy `high-five-economy-v1.xml` строгий, ordered, UTF-8, XXE-safe и
content-addressed. Лимиты: payload 4096 bytes, 32 reservations, 24 item IDs,
4 participants, 1 active operation/profile, 100000 retained nonterminal,
craft 32 attempts, enchant 16, reconciliation 3, audit retention 256.

Phantom World сохраняет существующий disabled-by-default startup gate.

## Focused test results

Seed всех новых modes: `22002201`.

- `economy-reservation-schema`: 2/2 PASS;
- `economy-reservation-concurrency`: 2/2 PASS;
- `economy-self-craft-active`: 1/1 PASS, real materialized Phantom;
- `economy-self-craft-background`: 1/1 PASS;
- `economy-enchant-active`: 1/1 PASS, real materialized Phantom;
- `economy-enchant-background`: 1/1 PASS, четыре canonical branches;
- `economy-restart-transition`: 1/1 PASS;
- `economy-lifecycle-performance`: 2/2 PASS.

Fresh-schema defect, найденный первым provisioning run: migration имя
сортировалось до `phantom_profiles.sql`. Исправлено bounded rename; повторный
fresh provisioning PASS.

Affected regression route PASS для commerce hardening, acquisition source
switching/background parity, background transaction/lifecycle, production
materialization и server shutdown handoff. При этом были найдены и исправлены
два bounded integration defect: reservation identity для SELL/enchant теперь
берётся из `getItemLocation()`, а acquisition сохраняет прежний active fallback,
когда economy conflict port не установлен. Отдельный manor active route один раз
дал нестабильный старый результат и сразу прошёл изолированный rerun без правок;
он не относится к изменённым integration families.

## Performance evidence

Focused smoke выполнил:

- 100000 reservation conflict key checks;
- 100000 craft quotes и 100000 enchant quotes;
- 10000 background craft projections;
- 10000 background enchant projections;
- 10000 deterministic replay identity reconciliations.

Новых runtime tasks/threads и retained claims после suite нет.

## Additional READ_SET

Сверх исходного READ_SET были открыты только ближайшие production symbols:

- `CommonSkill` — подтвердить current craft skill IDs/levels;
- `EnchantItemGroupsData` — подтвердить current chance group ownership;
- `PhantomMaterializationLifecyclePort` — встроить boundary chain без нового
  lifecycle owner;
- `Player` recipe/skill methods — подтвердить существующие active test seams,
  без изменения файла.

## Deviations / limitations / risks

- Active branch outcomes используют canonical server RNG; deterministic полное
  branch покрытие выполняет background projection с seed. Active smoke доказывает
  реальную mutation/consumption/observer cleanup на compatible objects.
- ALT creation остаётся current active-only scheduled behavior;
  background deliberately возвращает `ACTIVE_REQUIRED`.
- Direct trade, private stores, player manufacture, mail и clan warehouse
  остаются Checkpoint 2 и не реализованы.
- Независимый verdict отсутствует; self-accept Checkpoint 1 не выполнялся.

## Commands and terminal gates

До freeze выполнены `ant compile-tests`, isolated test-DB provisioning, восемь
focused modes, bounded affected route и working-tree verifier 022c1 под Windows
PowerShell 5.1 и PowerShell 7 с byte-identical output. Final aggregate, final
plain `ant verify`, standalone `ant jar`, commit/push и два post-commit verifier
runs записываются только в terminal section ниже.

## Terminal section

- Test DB: только `l2jmobiush5_phantom_test`; production DB не использовалась.
- Bounded affected route: PASS.
- Working-tree verifier 022c1: PASS под Windows PowerShell 5.1 и PowerShell 7;
  output byte-identical, scope 47, production 26, new production 13, SQL 1,
  policy SHA-256
  `52ed0748b1919a8736d857fa80ee318e4e1e385827cb6b8038fbda65776598d9`.
- Final `phantom-economy-checkpoint1-test`: PASS, `BUILD SUCCESSFUL`, 1:31,
  seed `22002201`.
- Один plain `ant verify`: PASS, `BUILD SUCCESSFUL`, 16:09. Intentional
  negative controls дали ожидаемые non-zero результаты внутри зелёного gate.
- Один standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:18.
- Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
- `git diff --check 043844c0fd7a0bfcac0d5f58461a21633b032332 --`: PASS;
  присутствуют только Git safe-CRLF warnings, whitespace errors отсутствуют.
- Freeze production/data/test/build/verifier соблюдён после final aggregate;
  после freeze изменялась только эта terminal section отчёта.
- Отчёт входит в единственный ordinary child required parent с subject
  `feat(phantoms): add economy reservations craft and enchant`; exact SHA,
  push containment и два byte-identical historical verifier runs фиксируются
  неизменяемым post-commit verifier и финальным сообщением, поскольку их нельзя
  записать в этот commit без второго commit/amend.

## Next step

Независимо проверить Checkpoint 1. Не начинать Goal 022 Checkpoint 2 до verdict.
