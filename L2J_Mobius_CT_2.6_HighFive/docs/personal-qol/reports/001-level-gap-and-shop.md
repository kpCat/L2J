# L2-QOL-001 evidence report

Дата: 2026-09-13

Branch: `feature/phantom-world`

Required parent: `4e338a83a2db9bc99a828c2f19b6e05722d3339c`

Результат: **SUCCESS**

## Scope и source audit

До правок прочитаны task package (`TASK.md`, `SOURCE_AUDIT.md`, `FEATURE_CONTRACT.md`, `TEST_PLAN.md`, `VALIDATION_ENVIRONMENT.md`), текущие Phantom freeze/status документы, владельцы native drop/spoil, multisell, Community Board, inventory и test harness/build wiring. Локально переиспользованы strict fail-closed XML/config pattern, immutable snapshot, guarded DB fixture и настоящий async `GameClient` socket-path.

Native source подтверждает: `Attackable` передаёт в reward calculation выбранного main damage dealer Player, включая владельца summon; legacy Fake Player использует NPC actor. `NpcTemplate.calculateDrops` владеет grouped, ungrouped и spoil level-gap penalty. Поэтому изменение ограничено только двумя native penalty участками, а snapshot вычисляется один раз на reward calculation.

Не менялись XP/SP, quest rates, Spoil cast success, amount/random count, chance RNG/check order, party/reward actor selection, raid curse, Phantom lifecycle/algorithm или старый freeze.

## Реализация

- отдельный shipped-OFF INI и strict UTF-8 tier XML;
- ровно четыре tier 5/10/20/40, maximum-only, без расхода;
- обычный Player inventory scan только по четырём ID;
- headless Phantom ACTIVE/WARM/BACKGROUND и legacy NPC Fake Players исключены;
- native grouped/ungrouped/spoil chance получает нейтральный level-gap multiplier только внутри включительной абсолютной границы;
- новый Alt+B board и native multisell `91001` с DEMO Adena prices;
- list guard действует при prepare и execute, generic bypass запрещён, stale prepared list после disable не проходит;
- native payment/ownership/capacity/flood pipeline не заменён.

Carrier templates не менялись:

| ID | Original inventory name | Tier |
|---:|---|---:|
| 22290 | Recipe: Happy Cake - Event | 5 |
| 22296 | Cake Ingredient: Dark Chocolate - Event | 10 |
| 22297 | Cake Ingredient: White Chocolate - Event | 20 |
| 22298 | Cake Ingredient: Creme Fraiche - Event | 40 |

Аудит подтвердил stackable inert EtcItem templates без handler/skill/action/timer/conditions/reference price. Совпадения чисел 22296–22298 в Crystal Caverns относятся к NPC/coordinates, не к item templates.

## Test evidence

Focused candidate: `C:\Users\ZBook\L2J_QOL001_RC_20260913_01a099d1`, sparse checkout содержит только `L2J_Mobius_CT_2.6_HighFive`; branch `feature/phantom-world` и HEAD `4e338a83a2db9bc99a828c2f19b6e05722d3339c`. Production `l2jmobiush5` не читалась и не проверялась; `prepare-phantom-test-db` не выполнялся. Применён существующий allowlisted test DB fixture. Шесть SQL-файлов и два pinned Goal020 semantic input были материализованы только в candidate с принятой EOL-рецептурой предыдущего validation environment; content по `--ignore-space-at-eol` идентичен parent, accepted semantic SHA-256 равны `16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18` и `2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E`. Эти materialization changes в commit не входят.

- `ant qol-level-gap-test`: **7/7 PASS**, seed `1001001`; 31 policy cases и 13 loader/fail-closed negative controls. Реальные grouped/ungrouped/spoil пути подтверждают внутри границы `100.0`, снаружи stock `37.25`, а также maximum-only, inventory/relog и bot exclusion.
- `ant qol-shop-test`: **6/6 PASS**, seed `1001002`; настоящий local async socket/GameClient и native `MultiSellChoose`. Положительная покупка подтверждает `1000` Adena debit и `1` item credit; проверены shipped OFF, generic/stale/wrong-list/malformed/capacity/flood/concurrent negative controls.
- `ant qol-affected-test`: **PASS**; reward, quest-rate/acquisition/background, handler compatibility, commerce, shipped-disabled baseline и DB guard owners.
- Resume9 historical static census 014/015/016/017/018/019/020c1/020c2/022c1/022c2: **PASS**; Goal016 использовал commit-backed historical bytes, Goal030 matrix SHA-256 `FD891490E7BED44DBA7D33F1B72D5C1DE46FF67003190B31D22B7DD96206E64E`.
- `ant verify`: **PASS**, `BUILD SUCCESSFUL`, 27 минут 26 секунд; один fresh full verify после полной qualification и focused/affected.
- `ant -q jar`: **PASS**, 13 секунд; standalone final jar после verify, оба архива прошли `jar tf`.

Первые focused запуски выявили только wiring/fixture проблемы: stale schema manifest из-за принятой EOL-рецептуры candidate, null skill map в synthetic NPC template, реальные prepared entry IDs и native flood interval. Исправлены candidate materialization и test harness; production semantics не ослаблялись.

До полной qualification две sandbox-попытки не смогли прочитать candidate `HikariCP-7.0.2.jar` и не являлись project verify. Затем два длинных prequalification запуска честно остановились на environment setup: первый — на известных CRLF hashes двух pinned semantic inputs, второй — на detached HEAD, который не удовлетворяет historical Goal014 branch guard. После exact EOL-only materialization, переключения того же candidate на `feature/phantom-world`, focused semantic 3/3 и полного green historical static census выполнен указанный выше единственный qualified fresh verify. Product/test assertions ради recovery не менялись.

## Final artifacts

Финальные jar из `C:\Users\ZBook\L2J_QOL001_RC_20260913_01a099d1\build\dist\libs`:

- `LoginServer.jar`: SHA-256 `EBE748CFDFA0EBCC46EB311CC67E459A08FF6694AAC080738EBB6EF78E2942BE`, 313193 bytes, 200 entries;
- `GameServer.jar`: SHA-256 `53578419D73AE56B750D56004C822899F7FD6EAD124A8ED414EC3AB56328FD5F`, 8978564 bytes, 4294 entries.

Commit subject: `qol(001): add level-gap protection items and shop`.

Client UI: **NOT_TESTED_CLIENT_UI**. Серверные HTML/bypass и русские labels покрыты source/tests, но визуальный H5-client прогон не заявляется. Client patch не требуется; инвентарь показывает исходные stock names/icons.

Следующие L2-QOL-002 и L2-QOL-003 только опубликованы в `docs/personal-qol/ROADMAP.md`; vitality/rate items сохранены в backlog и не реализованы.

Final scope audit: 26 task-owned files, operator/candidate parity 26/26, `git diff --cached --check` PASS; task package, user-owned tracked paths, SQL materialization, local DB configs и jar не staged. Оба новых XML parse PASS, shipped feature/shop остаются OFF.

Mojibake-маркеры в изменённых файлах проверены: совпадений нет.

Escaped Cyrillic в изменённых файлах проверены: совпадений нет.

Control characters в изменённых файлах проверены: совпадений нет.
