# L2-QOL-004 evidence report

Дата: 2026-09-14

Branch: `feature/phantom-world`

Required parent: `728f5c40b325e1a38295389f97138cb9d36aa869`

Результат: **SUCCESS**

## Scope и source audit

До правок полностью прочитан task package L2-QOL-004 (`TASK.md`, `SOURCE_AUDIT.md`, `FEATURE_CONTRACT.md`, `TEST_PLAN.md`, `VALIDATION_ENVIRONMENT.md` и сопутствующие package-файлы), `Agents.md`, корневой `README.md`, module `readme.txt`, Personal QoL status/roadmap/operator guide, отчёты QoL-001–003, актуальный build/test wiring и ближайшие владельцы Community Board, multisell, inventory crystallization, EXP toggle, `PlayerVariables` и herb immediate effects. Отдельных code-map/pattern-файлов и дополнительного `AGENTS.md` в дереве Personal QoL нет.

Переиспользованы локальные паттерны: data-driven immutable storefront из `PersonalPremiumQoLService`, единственный runtime-owner с делегированием всех входов, persisted per-character variables, stock inventory crystallization и существующий `QoLShopSuite` с реальным local async `GameClient`/`MultiSellChoose` transport. UI остаётся H5 Community Board HTML; layout не смешан с runtime-логикой.

Изменение ограничено Personal Board storefront/per-player controls. Не менялись public GamePackage schema, Phantom lifecycle/algorithms, production DB, client patch, pet herb behavior, native multisell execution, skill/item templates, XP/SP formulas, Lua/media/provider/runtime или существующая crystallization semantics.

## Реализация

- `_bbsqol` преобразован в категоризированную Personal Board с разделами обзора, персонажа/EXP, passes/drop-spoil, herbs, crystallization, utilities и помощи;
- цены витрины берутся только из валидированного multisell `91001`, а HTML не содержит второй hardcoded price-owner;
- четыре финальные цены в Adena: tier 5 — `100000`, tier 10 — `500000`, tier 20 — `2000000`, tier 40 — `8000000`;
- `PersonalPlayerControlService` стал единым владельцем EXP toggle для voiced-команд, Alt+B и relog restore;
- добавлены независимые persisted per-character herb toggles для recovery, combat и vitality; shipped default сохраняет vanilla auto-use всех herbs;
- suppression подключён к обоим native Player immediate-effect путям; неизвестные immediate items и headless Phantom сохраняют native behavior, pet path не изменён;
- проигнорированный ground herb всё равно уничтожается stock pickup path и не засоряет inventory;
- bypass routing строгий и fail-closed; combat/karma/peace/store restrictions сохранены;
- utility store не добавлен: Mana Potion не имеет подтверждённого Adena retail-owner, а Revita-Pop, vitality items и XP rune являются premium/nontrade/non-sellable. Решение зафиксировано как defer, без выдуманной экономики;
- `SHOP_PRICING.md`, status, roadmap и operator guide фиксируют data owner, edit/restart workflow и не заявляют последующие задачи начатыми.

## Herb classification

Аудит выполнен по полному H5 набору `ex_immediate_effect=true` и фактическим skill effects.

- recovery: `8154`, `8155`, `8600`–`8605`, `8614`, `8952`, `8953`, `10432`, `10433`, `14777`, `14779`;
- vitality: `13028`–`13031`, `20273`, `20926`;
- combat: `8156`, `8157`, `8606`–`8613`, `10655`–`10657`, `14778`, `14824`–`14827`, `20272`, `20274`, `20770`–`20772`, `20927`, `20928`.

## Test evidence

Qualified candidate: `C:\Users\ZBook\L2J_QOL004_RC_20260914_0152`; branch `feature/phantom-world`, HEAD `728f5c40b325e1a38295389f97138cb9d36aa869`, обычный isolated local clone без reparse/worktree links. В candidate перенесён exact QoL-004 overlay. Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не выполнялся. Использовалась только уже подготовленная allowlisted схема `127.0.0.1:3308/l2jmobiush5_phantom_test` с пользователем `l2j_phantom_test`; пароль в вывод не попадал.

По принятому и уже задокументированному в QoL-001 validation-рецепту только в candidate EOL-materialized шесть SQL schema inputs и два pinned Goal020 semantic input. SQL inventory после materialization побайтно совпал с manifest. Semantic SHA-256: `16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18` и `2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E`. Эти validation-only EOL changes не входят в продуктовый commit.

- `ant -q compile-tests`: **PASS**; только два прежних JDK removal warning для `System.runFinalization()`;
- `ant -q qol-004-storefront-controls-test`: **8/8 PASS**, seed `1001002`; exact price ladder, настоящий native multisell debit/credit, data-driven HTML, общий EXP-owner, per-character herb isolation/relog, representative suppression, unknown-immediate vanilla fallback и malformed bypass no-mutation;
- `ant -q phantom-semantic-activation-test`: **3/3 PASS** после документированного candidate EOL-materialization;
- `ant -q qol-004-verify`: **PASS**, `BUILD SUCCESSFUL`, 12 минут 33 секунды; focused, affected QoL-001–003, crystallization, retained historical static и Goal039 freeze/documentation;
- `ant verify`: **PASS**, `BUILD SUCCESSFUL`, 25 минут 27 секунд; fresh полный прогон после qualification, финальный `full-vision-goal039-static` — 8/8;
- финальный `ant -q jar`: **PASS**, 14 секунд; оба архива прошли `jar tf`.

Первый operator aggregate дошёл до Goal039 historical matrix и остановился только из-за заранее существующего пользовательского изменения в frozen Goal030 test-файле; это стало причиной isolated candidate, а не изменения чужого файла. Первая candidate-попытка остановилась до focused cases на stale schema manifest из-за CRLF checkout шести SQL-файлов. Первый полный candidate `ant verify` дошёл до semantic activation и выявил те же CRLF checkout bytes у двух pinned semantic inputs. После применения уже принятого QoL-001 EOL-рецепта targeted semantic gate и fresh полный verify прошли; product/test assertions ради восстановления не ослаблялись.

## Final artifacts

Финальные JAR из candidate:

- `LoginServer.jar`: SHA-256 `BAAC00161FF9634FBD4DA91A975F931CBD5740A2D43D711E81C7AF24404017EF`, 313193 bytes, 200 entries;
- `GameServer.jar`: SHA-256 `78FCD6305F55B705C2EA6D0D6D3FE0F6D034F6F7C8EB2A5FA673952DB86A2A80`, 9028940 bytes, 4325 entries.

Commit subject: `qol(004): consolidate personal board storefront and controls`.

Client UI: **NOT_TESTED_CLIENT_UI**. Серверные HTML, маршруты, labels и handlers покрыты source/tests, но визуальный прогон в H5 client не заявляется. Client patch не требуется.

Следующие QoL-задачи остаются только backlog в `ROADMAP.md`; ни одна не начата в этом scope.

Final scope audit: 16 task-owned paths; exact staging allowlist не включает task package, unrelated user changes, candidate EOL-materialization, local DB configs, build output или JAR.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Control characters в изменённых файлах проверены: совпадений нет.
