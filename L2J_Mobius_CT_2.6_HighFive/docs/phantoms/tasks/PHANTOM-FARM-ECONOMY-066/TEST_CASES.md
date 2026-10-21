# TEST_CASES066 — короткие сцены и общие классы отказов

Не добавлять отдельный мир/engine ради тестирования. Использовать существующий launcher, native fixtures и обычный Main; измерения собирать наблюдателем/логами, не управляя актором из доказательства.
Выбор stock NPC/items детерминирован: сортировка по ID среди подходящих данных и реально доступных spawn/anchor. Один запасной вариант до запуска, оба записать в PLAN066. После неудачи не выбирать удобного победителя задним числом.
Тестовый setup разрешён только в своей066 среде до начала measurement; для обычного Main никакой прямой materialize/AI action/XYZ/HP/MP/EXP/item подкрутки в процессе.

## S1. Обычная жизнь с нуля — главный приоритет
1. Новая candidate066 схема, FRESH population и conservative active count. Обычная starter экипировка, у наблюдаемого novice shots=0/Adena=0; skills и mob data — штатные. Если bootstrap даёт подарки, отразить это и использовать отдельную заранее созданную чистую identity для zero-stock доказательства, не чистить реальных персонажей.
2. Подготовить persisted viewpoint отдельного access0 TEST observation-character; его сетевой Player не загружать. EnableLocalPlaySyntheticHuman=False. Зафиксировать inputs, JAR и source SHA.
3. Запустить обычные Main и получать свежие World/owner/epoch, damage/kill/reward/loot/decision receipts. Выполнить G1/G2/G9 минимум, затем остановить сцену; верхняя граница measurement12мин.
4. Нулевой stock/бюджет не должен гонять actor между impossible supply и idle. Не считать движение, targetSequence или EXP из background доказательством native kill.

## S2. Гном и хозяйственная цепочка — один новый representant
1. Не повторять все стартовые деревни: взять dwarf с законными Spoil/Sweeper и уместной экипировкой в stock области не на Gremlins. Native learned-skill evidence либо TEST setup по проверенным skill-tree данным, помеченный как fixture.
2. Подходящий обычный mob близкого уровня со spoil table и практически достаточным базовым шансом; не raid. Ограничение≤12 попыток/12мин; наблюдать реальную статистику, не принуждать удачу/setSpoiled.
3. Наблюдать native Spoil→death→Sweep→полученный материал и продолжение. Для inventory maintenance до measurement допустим явно маркированный набор: дешёвый разрешённый surplus, D/C key-part, B+ key-part, D+ scroll, дорогой защищённый item, нужные расходники и лишняя eligible D/C вещь.
4. Actor самостоятельно доходит до валидного Merchant/warehouse, продаёт surplus, кладёт ценные предметы, законно кристаллизует ненужное, при наличии средств закупается/извлекает свой запас, возвращается к цели. Не считать fixture-вещи заработанными в этой сцене.
5. До/после: objectId/itemId/count/location/owner, Adena и native fee/yield. Успех цепочки важнее полного мешка разнородного случайного loot. Measurement хозяйственного продолжения≤15мин; путь не чинить по координате каждого NPC.

## S3. Свободный чужой дроп, race pickup и геометрия
В одном native fixture окружении обычный контрольный actor убивает подходящие mobs и уходит; другой Phantom после native release подбирает оставленное. Для типов вещей с редким drop допустимы отдельно маркированные ground fixtures; они не заменяют хотя бы один native death-drop.
Сначала проверка отказа при защите, затем естественное окончание; один party-loot случай, один quest/special отказ, один full inventory и weight. Конкурирующий второй picker не производит два предмета.
Три пространственных представителя: открытый путь, препятствие с легальным обходом, недоступный другой слой/закрытый путь. Использовать настоящую GeoData. После недоступной цели actor выбирает другую, не ждёт бесконечно.
Часть комбинаций проверяется pure/native assertions без отдельного запуска Main. Для ranged случая использовать подходящий attack/skill с нужной дистанцией, а не менять pickup range во время сцены.

## S4. Burst и lazy retirement, без пользователя
Существующий native composition harness с теми же production owners: soft2/burst2, две старые materializations; новый current-local demand использует свободный резерв до истечения старого grace.
Наблюдение перемещается между двумя проверенными областями: это изменение passive demand, не teleport Phantom. Возврат до seal сохраняет прежнюю identity. Повтор с отсутствием возврата доказывает retirement даже без следующего запроса capacity.
По одному focused negative на Party/Store/earned work/checkpoint/failed cleanup и stale epoch. Конкурентная admission проверка — ограниченный тест, не нагрузочный soak на тысячи actors.
Pure clock tests покрывают hysteresis boundaries; хотя бы один native цикл ждёт настоящий grace и drain. При неизменных inputs не держать его часами.

## S5. Один завершающий restart на той же БД
После завершённых операций снять snapshot собственных identities, inventory/warehouse, balances и operation receipts, штатно STOP обе роли; проверить own PID/ports.
START с тем же config/JAR/DB, без reseed/reimport/reset. Проверить восстановление и одно полезное продолжение; повтор completed intent не создаёт предмет/Adena/crystals. Затем STOP.
В обычной сцене не моделировать crash. Failure injection разрешена только на своих unit/native test seams до commit/после receipt с явно обозначенной границей; не ломать реальные БД/процессы.

## Focused coverage вместо марафона
- Shots: none/present/depleted/incompatible; magic SS/SPS/BSS distinction; native consumable failures; stale owner.
- Loot: own/free/protected/party, same/different instance, gone/contended, stack/nonstack, capacity/weight, risk и навигационный отказ.
- Spoil: learned/no-skill, success/failure по реальному RNG, lawful/foreign/already-swept corpse; передача управления следующему действию.
- Capacity: burst0, available reserve, ceiling, cancelled demand, epoch change, hard hold, eventual idle cleanup и exact permit accounting.
- Item policy: recipe-derived grade/ambiguous ingredient, protected equipment/consumable/epic, native sell/deposit/withdraw/crystal, отказ и повтор.
- Recovery: прерванный intent, сохранённая receipt и одновременный background/autosave/trade writer без дюпа/потери.
Один краткий статус на достижение этапа. Полный trace не выводить в диалог; ошибки — последние причинные строки, остальное `.phantom-local/task066/`.
