# PHANTOM-FARM-ECONOMY-066 — результат кампании

21.10.2026 UTC. **CORE066=PARTIAL**. Обычная связанная S1 и полезное продолжение после sameDB restart подтверждены; связанная dwarf S2 не доказана. Последний impacted набор имеет два незакрытых случая. Полная приёмка G0–G10 и исторические M1–M7 не объявляются.

Матрица: [ACCEPTANCE066.tsv](../tasks/PHANTOM-FARM-ECONOMY-066/ACCEPTANCE066.tsv). Точный scope: [CHANGED_FILES066.txt](../tasks/PHANTOM-FARM-ECONOMY-066/CHANGED_FILES066.txt). Семь исходных документов пакета сохранены побайтово. Bounded exception свыше10 файлов разрешён AUTHORITY066: одна причинная цепочка combat → loot → capacity → maintenance → native evidence и поставка.

## Общие причины и изменения

- Отсутствующий/несовместимый SS/SPS/BSS означает UNAVAILABLE и допускает законный бой. Ошибка нативного списания означает FAILED; epoch/admission/checkpoint, MP, reagent, ammunition, learned skill и reuse guards сохранены. Бесплатное создание beastshots удалено.
- Optional закупка требует бюджета на установленный target deficit60hits; четырёхзарядные micro-trips больше не вытесняют бой. Обязательные боеприпасы/лечение допускают доступный полезный частичный запас. Изменение cash/stock разрешает новую оценку покупки.
- Pickup/sweep работает под существующим AutoPlay owner: range600 (config200–1200), scan64, batch3, память отказов32/5с. Сначала угроза; права/party/quest/вес/slots и pickup решают native Player/AI. Exact source receipts отделяют inventory acquisition от немедленно потреблённой herb; request не считается результатом.
- Soft target отделён от physical ceiling=soft+burst, включая loading/failed retained. Burst0 сохраняет strict режим. Idle cleanup через существующую bounded ready queue после штатного60с grace; exact epoch/human/Party/Store/earned/persistence holds перепроверяются до seal.
- Общий read-only recipe/product index защищает ценные/неизвестные материалы и старшую роль ingredient. Maintenance child существующего SupplyJourney ограничен4операциями/count600: NPC SELL/refund, платный warehouse, withdraw до BUY, CrystallizationService и возвращение к захваченной farm цели.
- Native checkpoint + economy claim сериализуют хозяйственного writer. Inventory/warehouse/refund сохраняются и SQL counts сверяются до COMMITTED; неоднозначность fail-closed, без выдуманной компенсации.
- Общий конфликт no-target30с с native corpse7с + decay tick + respawn25с обрывал продуктивный farm. После реальных kill/damage/reward используется существующее90с окно; абсолютный90с useful-debt сохранён. Native timers/rates/RNG не менялись. Reward witness сохраняется при одновременном законном pickup.
- Исправлено соответствие native EXP и сохранённого rank. Добавлены bounded read-only vitals/economy/inventory diagnostics и066 ownership/provenance/normal shutdown tools.

Новый engine/таймер на actor, глобальная навигация, UI/Studio, изменения шансов/NPC/skills ради PASS и новая подсистема фоновой симуляции не добавлялись.

## Сборка и привязка

Baseline/live remote: `934c19f906aa67349f1fd498eacfbb5a56d9b90f`, branch `feature/phantom-world`. Финальный **code commit**: `1432e2807fa96921af0869a080fbb35d10d3ee56`.

| Artifact | SHA-256 |
|---|---|
| GameServer.jar | `0523CFC26BA73D2CF2F3380A46BCED881925734FB5CD0FD6F205E037587CBF8A` |
| LoginServer.jar | `D5D8D15554042C82B5379D3B90416C7D4EC75685303BF8B74FD506E9C32DEA06` |
| Committed source archive | `FA74132F9EAF4C8356F44414C0FE2A18043C25C24A360827AEEC7DA8189D61F9` |
| Committed test tree | `55F6F8645FC4CBE2FFA3DEF4CF18DCCAF9FCE9732A2D2D452C43C676883750EE` |

Clean isolated `tools/phantom-runtime/Build-Task066.ps1`, Ant `jar` + `compile-tests`: exit0, receipt22:02:51 UTC. Ant последовательно; JDK25.0.4.1/Ant1.10.17, зависимости не менялись. Production JAR без test controllers/helpers. `ant verify` — NOT_RUN_BY_SCOPE.

Private evidence далее относительно Git root `.phantom-local/task066/`. Credentials и raw DB rows в Git не добавлены.

## Focused/native проверки

| Mode / seed | Результат / exit | Фактическая привязка / evidence |
|---|---|---|
| combat-core /20260725001 | 54/54,0 | финальный1432/JAR0523…; reports/final1432-core54 |
| commerce-decision /140014 | 6/6,0; optional trip RED5/6→GREEN6/6 | финальный1432; reports/final1432-decision6 |
| commerce-receipt /140014 | 9/9,0 | финальный1432; reports/final1432-receipt9 |
| earned-learning-native058 /66006601 | shots3/4,1; paid physical/blessed/hybrid+summon PASS, direct unboosted magic FAIL | 7ddfa8a56/JAR D31A4210…; reports/priority-native-shots |
| тот же mode /66006611, loot flag | 5/6,1; ranged INVALID_INPUT: оба stock Geo corridor недоступны | 7dd/JAR D31A…; reports/priority-native-loot |
| прежний loot /66006611 | 6/6,0; stock ranged kill471.28units и pickup | 4244dabd/JAR B42A3C69…; reports/delivery-native-loot |
| farm-economy-cap-native066 /66006621 | 4/4,0 | 7dd/JAR D31A…; reports/priority-native-cap |
| farm-economy-native066 /66006631 | 3/3,0 | 7dd/JAR D31A…; reports/priority-native-economy |
| earned-learning-native058 /66006601, maintenance flag | 1/1,0 | 7dd/JAR D31A…; reports/priority-native-maintenance |

Native tests используют committed tests/whole JAR, свою TEST БД и именованные premeasurement inputs; fixture вещи не объявляются заработанными. Command/options/seed/source/JAR/testTree/exit — EPISODE066.json и suite txt/XML. Последний full impacted `priority-impacted.log` exit1: две ошибки сохранены. После позднего product fix повторены затронутые pure проверки и обычный Main.

`CLASS_SCOPE_EQUIVALENCE066.json`: измеренный7dd→финальный1432 побайтово равны41 acquisition classes,81 capacity classes,68 commerce classes. Это reuse bounded native evidence, а не повторный native прогон всего финального JAR.

Последний Spoil: learned254/42 → два stock kill/reward, один подтверждённый Sweep и продолжение следующего kill; deltas1868×1/2174×1. No42/zeroMP/foreign/alreadySwept negatives PASS. Capacity: arrival972мс до cleanup, idle cleanup60224мс, occupied3→1/excess1→0 без нового pressure; concurrent/loading/failure-held/store-once guards PASS.

Native economy: sale236 Adena; deposit fee30→206; второй fee→176; bank stock withdrawn; BUY→160; ненужная D-вещь69→915 crystals1458. Replay receipt дважды идемпотентен. FULL/no-fee/weight/no-skill/C-skill/equipped/enchanted/reserved/needed negatives PASS. Autonomous child прошёл424units, SELL1896×1→236, bank1917×3/fee30, withdraw1835×120 и вернулся к прежней farm цели без повторной BUY.

## Обычная S1 и sameDB restart

Main, synthetic OFF, AUTONOMOUS, actualNetworkSession=false, без fake GameClient/GM/test-controller. Population48/FRESH; observer — пассивная отдельная identity. Его точка выбрана до сцены по фактическому положению своих actors; фантомы не телепортировались, balances не пополнялись.

S1 `reports/final-town-main`: Game2220/startTicks639282180050161862, Login14952. За85.06с наблюдения профиль20 одной эпохи:3kill/3reward/3inventory-pickup/damage12 →6kill/6reward/4inventory-pickup/damage18, AutoPlay продолжался. Herbs0; это inventory pickups. Штатный STOP22:23 UTC.

После STOP `town-before-restart-release-read.tsv`:48 identity,377 item rows,5 completed receipts, otherConnections0. Один normal restart той же БД/JAR/конфигурации: Game28776/startTicks639282182331190186, Login10720. Профиль19 с прежней identity снова достиг4kill/4reward/3pickup/damage15; профиль6 также4kill/4reward/4pickup. Useful continuation после restart подтверждено; второй полный S1 criterion со следующим damage после порога не заявляется.

`ORDINARY066.json` проверяет именно OperatorM1TargetSnapshot/nativeEvidence object/epoch по сохранённым PID-bound samples. Общий regex collector для части restart actors захватывал более ранний target object из diagnostic строки; эти object values исключены из identity claims. S1 профиль20/restart профиль19 прошли точную проверку. Collector restart закончил bound180с; Main остановлен раньше, после useful continuation.

Итоговый `samedb-after-stop-release-read.tsv`:48 identity/классы и все377 прежних item objects сохранены, добавлен1 helmet35 по новой native BUY профиля7:537→505 Adena, helmet0→1/PAPERDOLL. Пять прежних completed receipts побайтово неизменны, добавилась одна BUY. Остальные12 изменённых строк — Adena: например19 97→141,20 100→113,6 418→473. Складские строки неизменны; заполненного warehouse в Main не было, его операции доказаны отдельным native gate. Точные deltas — `RESTART_COMPARISON066.json`; новый cash не выдаётся за replay старого SELL.

Прежние Main эпизоды с неподходящей локальностью, supply micro-trips и no-target expiry не приняты за PASS. Суммарное ordinary наблюдение менее90мин; soak после результата не оставлен.

## Ограничения

- **G1 FAIL:** последний direct dual-class unboosted magic fixture не подтвердил ожидаемый damage/MP cost; melee и paid paths проходят. Причина полностью не доказана; обычный Main не заменяет этот focused gate.
- **G2 FAIL последнего запуска:** ranged geometry INVALID_INPUT; предыдущий native6/6 сохранён как история. Новые координаты/шансы/боевые данные ради PASS не подбирались.
- **G9/S2 BLOCKED:** dwarf preparation остановился ДО mutation: нет двух подходящих stock L18–22 spoil farms с mapped anchors и близким Merchant/Warehouse в заявленном setup. Единственный более слабый mapped вариант NPC20059/L22 не применён: его spoil probabilities/route coverage не обеспечивают прежнюю связанную сцену. Scavenger не получил Crystallize, test helper не включён в production JAR. Отдельные native Spoil/commerce не доказывают полную dwarf жизнь.
- **G10 FAIL как совокупный gate:** build/delivery/STOP/publication выполнены, impacted suite не весь GREEN. MARKET_BRIDGE066=DEFERRED; личный warehouse withdraw реализован. OWNER_CLIENT_QA=DEFERRED, вход владельца не требовался.
- Будущая задача: общий валидатор stock class/skill/farm/counter coverage в существующем journey owner. Экономная background симуляция — только существующая authority, игровое время/RNG/реальные item/Adena balances и единственный World/Background owner; новый066 механизм не создавался.

## Поставка и сохранность

Candidate `D:/PhantomWorld/Farm-Economy-066-candidate`, своя БД `l2jmobiush5_phantom_release_066a`, порты2146/7846/9146. TEST `l2jmobiush5_phantom_test_066`, порты2246/7946/9246. Soft16+burst16/physical32, active32, population48. Из candidate: `START.cmd`, `STATUS.cmd`, `STOP.cmd`; [START_HERE_066_RU.md](../tasks/PHANTOM-FARM-ECONOMY-066/START_HERE_066_RU.md). Повторная инициализация БД не нужна.

BUILD_INFO/SOURCE_BUILD_INFO связывают code/source/JAR/testTree; CONFIG066.tsv —88 актуальных config hashes, GEODATA066.tsv —203 физических файла. Предыдущий config manifest сохранён private с SHA. Credentials только private; protected057–065/MAIN/PLAY/клиент/Studio не запускались и не менялись.

`final-delivery-status.log`: normal STOP Game28776/Login10720, обе066 среды валидны, own JVM0,6 портов свободны, other DB connections0 обеих схем. Force stop не применялся. `preservation-delivery-retry.log`:7 использованных protected files,203 source Geo+203 own copies,2 использованных protected schemas/10tables совпали с preflight. Все122 исторические БД не проверялись. Повторный SELECT receipt сначала отказал CREATE_NEW; предыдущий receipt сохранён с SHA, повторная проверка exit0, protected mutation0.

Самостоятельный focused review: ownership/leases, exact loot receipts, paid/unpaid boundary, capacity/grace, maintenance bounds/native durability и delivery guards. Это self-review, известные gaps не скрыты общим PASS.

mojibake-маркеры в изменённых файлах проверены отдельно: PASS.

escaped Cyrillic в изменённых файлах проверены отдельно: PASS.

Exact allowlist, `git diff --check`/cached check, immutable package7/7 и scope guard выполнены; private logs/credentials/GeoData/JAR/raw personal rows не staged.

Git применялся по прямому AUTHORITY066: `rev-parse`, `symbolic-ref`, `status --porcelain/--short`, `worktree list`, `ls-tree`, `merge-base`, `diff`/`diff --check` по exact paths, `log`, `show`, `ls-remote origin refs/heads/feature/phantom-world`, `archive`, `add -- <exact allowlist>`, `commit -m <message>`, `push origin HEAD:refs/heads/feature/phantom-world`. Цель: bounded inspection/archive/scope и требуемая публикация; clean/reset/stash/force/rebase/merge/cherry-pick не применялись.

Source push exit0, live remote подтвердил code HEAD `1432e2807fa96921af0869a080fbb35d10d3ee56`. Финальный report-only HEAD после последнего обычного push фиксируется и проверяется в candidate `FINAL_PUBLICATION066.json`; JAR привязан к code commit. Кампания закончена до12ч без запроса игрового входа владельца.
