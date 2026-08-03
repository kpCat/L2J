# Goal 022 Checkpoint 1 — economy, craft и enchant

## Status

`COMPLETED_PENDING_INDEPENDENT_REVIEW`.

Goal 021 accepted baseline: `043844c0fd7a0bfcac0d5f58461a21633b032332`.
Goal 022 C1 foundation: `d02dc8429e88ef507347fc2e3860b0528844ae68`.
Goal 022 C1 lifecycle completion: `9e2bd551ecc03647641c16e393694b9a0cb51e60`.
Branch: `feature/phantom-world`.
Final authority/resume/risk completion subject:
`fix(phantoms): close economy resume authority and risk gates`.
Final SHA определяется post-commit verifier как единственный ordinary child
lifecycle completion commit.

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
- `economy-reservation-concurrency`: 13/13 PASS;
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

- Test DB: только `l2jmobiush5_phantom_test`; production DB не использовалась.
- Bounded affected route: PASS.
- Historical verifier 021c2: PASS под Windows PowerShell 5.1 и PowerShell 7;
  output byte-identical, accepted implementation
  `043844c0fd7a0bfcac0d5f58461a21633b032332`.
- Working-tree verifier 022c1: PASS под Windows PowerShell 5.1 и PowerShell 7;
  output byte-identical, final scope 11, final production 7,
  new production 0, SQL 0, cumulative scope 47, policy SHA-256
  `52ed0748b1919a8736d857fa80ee318e4e1e385827cb6b8038fbda65776598d9`.
- Final `phantom-economy-checkpoint1-test`: PASS: все восемь свежих suite reports
  имеют `failed=0`; seed `22002201`; 1:28.
- Один plain `ant verify`: PASS; обновлены 120 XML suite reports, финальный
  `performance.xml` создан через 14:37 после первого suite report. Intentional
  negative controls `negative` и `lifecycle-control` дали ожидаемые non-zero
  результаты внутри зелёного gate; остальных ошибок/failures нет.
- Один standalone `ant jar`: PASS, `BUILD SUCCESSFUL`, 0:19.
- Mojibake-маркеры в изменённых файлах проверены отдельно: совпадений нет.
- Escaped Cyrillic в изменённых файлах проверены отдельно: совпадений нет.
- `RequestEnchantItem.java` byte-identical lifecycle completion
  `9e2bd551ecc03647641c16e393694b9a0cb51e60`.
- `git diff --check 9e2bd551ecc03647641c16e393694b9a0cb51e60 --`: PASS;
  whitespace errors отсутствуют.
- Freeze production/data/test/build/verifier соблюдён после final aggregate;
  после freeze изменялась только эта terminal section отчёта.
- Final completion создаётся единственным ordinary direct child lifecycle completion
  с subject `fix(phantoms): close economy resume authority and risk gates`; exact SHA,
  push containment и два byte-identical historical verifier runs фиксируются
  неизменяемым post-commit verifier и финальным сообщением, поскольку их нельзя
  записать в этот commit без второго commit/amend.

## Next step

Независимо проверить Checkpoint 1. Не начинать Goal 022 Checkpoint 2 до verdict.
