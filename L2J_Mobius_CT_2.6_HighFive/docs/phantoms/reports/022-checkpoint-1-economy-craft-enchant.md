# Goal 022 Checkpoint 1 — economy, craft и enchant

## Status

`COMPLETED_PENDING_INDEPENDENT_REVIEW`.

Goal 021 accepted baseline: `043844c0fd7a0bfcac0d5f58461a21633b032332`.
Goal 022 C1 foundation: `d02dc8429e88ef507347fc2e3860b0528844ae68`.
Goal 022 C1 lifecycle completion: `9e2bd551ecc03647641c16e393694b9a0cb51e60`.
Goal 022 C1 authority completion: `20fe8daccfb5000b5b970bff7b3555a4051e5dbc`.
Branch: `feature/phantom-world`.
Terminal participant-lifecycle completion subject:
`fix(phantoms): close participant economy lifecycle ordering`.
Final SHA определяется post-commit verifier как единственный ordinary child
authority completion commit.

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

## C1 completion

Bounded completion закрывает найденные review gaps без schema/config change:

- immutable Goal 021 `RecipePlan` повторно допускается после partial receipt;
  active и background выполняют output=1 тремя distinct operations, сохраняют
  progress/receipts/source history и завершают Goal только на третьей попытке;
- canonical failure и rare different-ID остаются committed economy outcomes,
  но не создают target progress; audit содержит exact consumption, `rare` и
  typed source failure;
- reservation admission проверяет linked character каждого participant,
  active-operation exclusivity каждого participant и cross-kind semantic
  overlap при стабильном profile-first lock order;
- actual background craft/enchant transactions покрыты полным fault matrix в
  12 точках до/после commit; pre-commit mutation откатывается, AFTER_COMMIT не
  освобождает committed object IDs;
- active effect-before-Goal и Goal-before-audit windows после restart переходят
  в fail-stop `INCONSISTENT` и не повторяют canonical enchant;
- ordinary `RequestEnchantItem.java` byte-identical lifecycle completion.

## Final authority/resume/risk completion

Bounded final completion закрывает последние authority и resume gates без
изменения принятой schema, policy XML или packet adapter:

- matching `RESERVED`, `DISPATCHING` и `OBSERVING` возобновляются новым plan;
  action-issued boundary durable `DISPATCHING → OBSERVING` запрещает повторный
  вызов canonical craft/enchant, а cancellation и shutdown всегда дают terminal
  state и освобождают claims;
- craft authority использует ordered length-prefixed facts для exact source,
  полного `RecipePlan`, recipe ingredients/stat-use/normal/rare templates,
  current config и policy; enchant authority включает target/scroll/support,
  crystal consequence, current chance/validity/config и policy;
- active dispatch повторно вычисляет authority и exact reservations; drift
  завершается до canonical action без item/vital/Goal mutation;
- `EnchantItemService` самостоятельно отклоняет transaction/store, ownership,
  identity, validity и over-enchant violations до расходования ресурсов;
- replacement evidence берётся только из canonical Adena; резервируется exact
  `ADENA` на Goal replacement reserve, а destructive branch дополнительно
  проверяет risk budget, expense budget, maximum expense и permission;
- normal/rare craft outputs входят в active/background reservations и exact
  before/after attribution; output, совпавший с ingredient, использует один
  merged `ITEM_COUNT` resource;
- `ITEM_OBJECT` конфликтует с другим exact object только по одинаковому
  object ID; cross `ITEM_COUNT`/`ITEM_OBJECT` остаётся item-ID conflict.

## Terminal participant-lifecycle completion

Bounded terminal completion закрывает participant ordering без schema, policy,
packet, adapter или materialization production changes:

- authoritative participant snapshot объединяет initiating profile и все
  reservation profiles, дедуплицируется и сортируется, хранит exact immutable
  profile-to-character links и соблюдает accepted bound 4;
- transition, renewal, reconciliation, expiry, cancel, shutdown и dispatch
  сначала без row locks обнаруживают participant set, затем блокируют все
  profiles по возрастанию, operation и canonical reservations и повторно
  сверяют set/links перед mutation;
- background craft/enchant больше не pre-lock инициатора: generic dispatch seam
  первым получает participant locks, а link drift атомарно завершает operation
  до item/vital/Goal mutation;
- materialization boundary учитывает initiator и reservation-only participant:
  `PREPARED`/`RESERVED` abort всей operation, `DISPATCHING`/`OBSERVING` fail
  closed без mutation, multiple active operations также fail closed;
- real lifecycle adapter покрыт `beforeMaterialize` и `beforeStore`, а две
  противоположные concurrent lock orders проходят по 100 итераций без deadlock;
- authority/risk, exact RecipePlan, observer, canonical enchant, reservations,
  restart/cancellation и single-participant paths сохранены.

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
- `economy-reservation-concurrency`: 17/17 PASS, включая participant lifecycle,
  drift, dispatch lock order и 200 reverse-order iterations;
- `economy-self-craft-active`: 2/2 PASS, real materialized Phantom и три
  successive service operations;
- `economy-self-craft-background`: 5/5 PASS, actual transaction outcomes,
  three-attempt lifecycle и 12-point fault matrix;
- `economy-enchant-active`: 5/5 PASS, full service chain, actor validation,
  packet parity и две
  non-atomic restart windows;
- `economy-enchant-background`: 5/5 PASS, authority/risk variants, четыре actual canonical outcomes и
  12-point fault matrix;
- `economy-restart-transition`: 2/2 PASS, включая shutdown terminalization;
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
  branch покрытие выполняют actual background transactions с seed. Active tests
  доказывают реальную mutation/consumption/observer cleanup и restart fail-stop.
- ALT creation остаётся current active-only scheduled behavior;
  background deliberately возвращает `ACTIVE_REQUIRED`.
- Direct trade, private stores, player manufacture, mail и clan warehouse
  остаются Checkpoint 2 и не реализованы.
- Независимый verdict отсутствует; self-accept Checkpoint 1 не выполнялся.

## Commands and terminal gates

До freeze выполняются `ant compile-tests`, восемь focused modes, bounded
affected routes и working-tree verifier 022c1 под Windows PowerShell 5.1 и
PowerShell 7 с byte-identical output. Final aggregate, final plain `ant verify`,
standalone `ant jar`, commit/push и два post-commit verifier runs фиксируются в
terminal section ниже.

## Terminal section

- Terminal gate status: `PARTIAL` из-за одного unrelated historical timing-flake
  в обязательном plain `ant verify`; C1 implementation gates зелёные.
- Test DB: только `l2jmobiush5_phantom_test`; production DB не использовалась.
- Exact `compile-tests`: PASS; bounded Goal 014/021 affected route: PASS во всех
  семи reports (`5/5`, `5/5`, `4/4`, `7/7`, `4/4`, `20/20`, `7/7`).
- Historical verifier 021c2: PASS под Windows PowerShell 5.1 и PowerShell 7;
  output byte-identical, accepted implementation
  `043844c0fd7a0bfcac0d5f58461a21633b032332`.
- Working-tree verifier 022c1: PASS под Windows PowerShell 5.1 и PowerShell 7;
  output byte-identical; terminal scope 6, production 2, cumulative scope 47,
  new production 0, SQL/XML 0; policy SHA-256
  `52ed0748b1919a8736d857fa80ee318e4e1e385827cb6b8038fbda65776598d9`.
- Final `phantom-economy-checkpoint1-test`: PASS: все восемь свежих suite reports
  имеют `failed=0`: `2/2`, `17/17`, `2/2`, `5/5`, `5/5`, `5/5`, `2/2`,
  `2/2`; seed `22002201`; 1:45.
- Единственный plain `ant verify`: FAIL через 3:40 на старом
  `combat-server-integration.02` (`Victory cleanup retained the exact dead
  combat target`) до запуска economy modes. Точный isolated diagnostic сразу
  прошёл `20/20` за 0:51 без правок, подтверждая timing-flake. Второй full
  verify не запускался: terminal task разрешает его только после relevant fix,
  а combat suite вне exact scope.
- Один standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:20.
- Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
- `RequestEnchantItem.java` byte-identical authority completion
  `20fe8daccfb5000b5b970bff7b3555a4051e5dbc`.
- `git diff --check 20fe8daccfb5000b5b970bff7b3555a4051e5dbc --`: PASS;
  whitespace errors отсутствуют.
- Freeze production/data/test/build/verifier соблюдён после final aggregate;
  после freeze изменялась только эта terminal section отчёта.
- Terminal completion создаётся единственным ordinary direct child authority completion
  с subject `fix(phantoms): close participant economy lifecycle ordering`; exact SHA,
  push containment и два byte-identical historical verifier runs фиксируются
  неизменяемым post-commit verifier и финальным сообщением, поскольку их нельзя
  записать в этот commit без второго commit/amend.

## Next step

Независимо проверить Checkpoint 1. Не начинать Goal 022 Checkpoint 2 до verdict.
