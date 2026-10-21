# ACCEPTANCE066 — результат, а не число зелёных тестов

Матрица `ACCEPTANCE066.tsv`: id / status / code_sha / jar_sha256 / test_or_scene / evidence_path / limitation. Разрешены PASS, FAIL, BLOCKED, NOT_RUN, DEFERRED. PASS только по актуальному коду и соответствующему виду доказательств.
CORE066=PASS требует G0–G10. MARKET_BRIDGE066 отдельный extension, OWNER_CLIENT_QA=DEFERRED допускается и не требует запроса пользователю. Прочие незакрытые пункты → CORE066=PARTIAL/BLOCKED с конкретными причинами.

## G0 — безопасный baseline и среда
Live HEAD/branch проверены; чужой dirt сохранён; input SHA совпали; собственные paths/schemas/PID incarnations установлены; old057–065 не запускались/не изменялись.
Не считать отсутствие замеченных ошибок доказательством проверки всех чужих122 БД. Область preservation и источники явно перечислены.

## G1 — shots не являются разрешением на бой
Focused + native: melee и magic без SS/SPS/BSS наносят ненулевой native damage; есть native kill/reward. Нулевые Adena не блокируют законный бой.
Наличие shots даёт штатную зарядку и списание; исчерпание на следующем действии не стопорит AI. Проверен затронутый summon path без создания ресурсов.
Negative: arrows/reagents/MP/learned skill/reuse и stale/draining owner по-прежнему запрещают именно недопустимое действие. Ошибка accounting не превращается в бесплатный buff.

## G2 — законный pickup и завершение серии
Проверены предметы от собственного и чужого убийства, ground Adena, обычный ресурс и non-stack item; сбор не зависит от текущего farm NPC ID.
До окончания чужой защиты — отказ; после штатного освобождения — разрешённый pickup. Party loot распределён нативно; quest/special ограничения не ослаблены.
Ranged kill вне прежних200 единиц не оставляет доступный ценный drop навсегда. Серия минимум из3 доступных объектов завершается либо оставляет явную lawful причину отказа.
Конкурирующий подбор/исчезновение, capacity/weight, другой instance/Z-слой и недоступный путь не дюпают вещь и не создают бесконечный retry.

## G3 — настоящий Spoil/Sweeper
Learned skills → native Spoil attempt/успех → смерть подходящего NPC → native Sweep → реальные item deltas из его spoil table; затем полезное продолжение.
Без навыка/недостаточный MP/чужое защищённое spoil ownership/уже swept corpse → штатный отказ. Не выдавать статистическую неудачу spoil за дефект и не подменять шанс.
Переход к следующей цели или supply не обрывает допустимый sweep; actor под угрозой не обязан умирать ради ресурса.

## G4 — мягкий cap с настоящим lazy cleanup
При soft2/burst2 и занятых2 местах новый актуальный local actor появляется в World ДО cleanup невидимого предыдущего, а не после скрытого повышения единственного cap.
В фокусном native сценарии с готовыми profiles — arrival≤10s; cold bootstrap/catchup измерить отдельно, не включать в этот SLA.
Возврат в старую область до seal не создаёт дубль identity/Player; без возврата и hard holds cleanup завершает штатный grace+drain, occupied/excess уменьшаются без дополнительного pressure-запроса.
Задать ожидаемый native срок до запуска: текущий grace60s + ограниченный scheduler/drain budget (ориентир≤120s для idle actor). В pure tests разрешены часы, в native — не подкручивать их ради PASS.
Burst0 сохраняет strict поведение; concurrent admission не превышает ceiling; hard-held/failed-retained actors учитываются и не вытесняются. В hard-full состоянии очередь не мешает другим полезным действиям.

## G5 — защита имущества и классификация
Data-driven проверки stock рецептов/предметов для D/C/B/A/S+: key-part NONE не означает мусор; неоднозначный ingredient трактуется консервативно.
Проверены equipped/upgrade/active recipe/reservation/quest/reagent/enchanted/epic исключения; отсутствие цены не разрешает дешёвую распродажу.
Гном с неподходящим уровнем Crystallize не получает capability. Порог B+ не превращает редкую вещь в случайную жертву RNG.

## G6 — NPC-продажа и деньги
Излишек исчезает/попадает в native refund согласно политике, Adena поступает один раз по native цене. Следующая покупка тратит именно существующие деньги.
Автономный actor сам выбирает разрешённого Merchant и доходит до взаимодействия существующей навигацией; неверный NPC/list/distance/instance даёт отказ.
Default runtime config проверен, а не изменён на более удобный refund/zero-price режим. Невозможная продажа безопасно откладывается без блокировки всего фарма.

## G7 — ограниченный настоящий склад
Сохранение ценного → native fee → warehouse; withdraw для нужды → inventory. Slots, merge стаков, вес при возврате и запреты предметов соблюдены.
FULL/нет платы/активный trade/reservation → не потерять item/Adena, не повторять списание. Имеющееся на складе учитывается до повторной покупки того же ресурса.
Inventory/warehouse/World/market не владеют одной единицей вещи одновременно; actor после housekeeping возвращается к прежней полезной цели.

## G8 — кристаллизация
Реальная ненужная eligible D/C вещь превращается через CrystallizationService в точный штатный yield; исходный объект отсутствует. Проверено отрицание для no-skill/equipped/protected/epic/B+ default.
Повтор intent/cancellation/restart не начисляет кристаллы второй раз. Защита нужной экипировки действует поверх native сервиса, который сам может уметь снимать надетую вещь.

## G9 — одна связанная жизнь и рестарт
Обычный Main, synthetic OFF, автономная точка наблюдения, нет client login/GM/test-controller в measurement. Минимум3 последовательных native kills,≥2 подтверждённых pickup, продолжение следующего действия.
Дополнительная связанная dwarf сцена покрывает Spoil/Sweep и хозяйственную цепочку до возвращения к фарму (TEST setup происхождение отмечено; не выдавать fixture предмет за заработанный).
Один штатный sameDB restart сохраняет identity, inventory/warehouse, Adena и завершённые receipts; повторная работа продолжается без повторного начисления. Старые client QA gates остаются DEFERRED.

## G10 — сборка, ревью, поставка, остановка, публикация
Clean Java/JAR и явный impacted suite PASS; self-review focused diff проведён, открытые критические ошибки не спрятаны флагом PASS. Ant verify глобально NOT_RUN_BY_SCOPE.
Mojibake и escaped Cyrillic проверены отдельно; `git diff --check` на exact allowlist; в Git/ZIP/log summaries нет secrets/GeoData/JAR/raw personal rows.
Новый candidate самостоятельный, source→code SHA→JAR→runtime binding записан. START/STATUS/STOP работают. Все собственные JVM/порты освобождены штатно либо явно BLOCKED_SHUTDOWN.
Commit и non-force push подтверждены live remote. Если remote продвинулся, не писать PASS публикации; safe local commit сохраняется.

## MARKET_BRIDGE066 — только дешёвое расширение имеющегося пути
При готовом existing store lifecycle: изъять из банка один законный избыток → открыть native SELL → штатная сделка → точный item/Adena баланс. Без отдельного market engine/сотен лавок/новой системы offline persistence.
Если потребовалась бы перестройка рынка, отметить DEFERRED_MARKET, перечислить seam. G7 withdraw для личной потребности всё равно выполняется.

## Проверки и анти-накрутка
Команды брать из текущего build.xml/PhantomTestLauncher; G1–G8 получают короткие named tests в существующем runner, не десятки test-only подсистем.
Пример безопасного build из Git root: `ant -f .\L2J_Mobius_CT_2.6_HighFive\build.xml -Dbuild=<own066-build> compile-tests`; финальный jar — через проверенный отдельный066 build wrapper с committed source.
Не запрашивать неизвестный mode наугад и не запускать весь verify ради поиска существующей проверки. Точный command/seed/exit фиксировать в evidence manifest.
Промежуточно — только failed/affected test. В финале один impacted run на финальном коде; при product-fix повторить действительно затронутые проверки и обновить привязку, без массового re-run unrelated gates.
Данные классов/recipe legality проверять широко автоматически, геометрию — по классам препятствий из TEST_CASES, не race×level×mob. Никакого boss25199 или марафона1→85.
