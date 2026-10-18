# DESIGN / Implementation Plan060 — жизнь между боями

> Исполнять inline через executing-plans, без субагентов. Это подготовленный пакет на исполнение; пользовательский /goal утверждает направление и решения ниже. Детали реализации выбирать по текущему коду самостоятельно, с коротким causal read-first, без новых вопросов о разрешённых шагах.

**Goal:** закрывать реальные жизненные цепочки и зависимости М7, а не число тестов.
**Architecture:** единый existing Decision/Goal + уже существующие Navigation/Commerce/Economy/Progression/Party/Pet services. При отсутствии ровно одного связующего owner добавить минимальный typed adapter с lifecycle, не новый AI engine.
**Tech stack:** Java25, Ant, WinPS5.1, existing058t/058a, Mobius High Five.
**Spec:** GOAL/AUTHORITY/TASK/ACCEPTANCE и исходные MILESTONES038/S4–S7.

## Review focus
Goal ownership при смене класса/группы; платежи и двойные callbacks; supply budget и native weight; правильный этаж/TP legality; Pet lifecycle; победа versus отдельный cast; backward-compatible goals/memory; restart без повторных покупок/обучения.

## P0. Continuation, не переустановка
- [ ] Прочитать RESULT059/authority и exact initial source map. Сохранить один stopped baseline058a, current code/JAR/движущиеся процессы/восемь старых orphan. Никакого начального rerun110/30 без причины.
- [ ] Подтвердить finite собственные TEST actors, reusable current tests и нормальный native cleanup. Уточнить native target IDs/skills/items по XML, не по названию роли. Старые вспомогательные overfull/подводные ошибки из059 учесть в общем небольшом preflight, не новой framework.
- [ ] Зафиксировать первыми два самостоятельных сценария различающихся классов/регионов и normal M3 current control. Product goals задаёт producer, тест задаёт только исходные условия и внешние события.

## P1. Общий выбор следующей жизненной цели — главный путь
**Read/modify:** existing population/ecology/Goal/Decision producers и farming/acquisition selection; `L2jPhantomLearningJourney`, `PhantomProgressionService`, commerce/economy decisions. Точный owner создания целей найти по usages, не внедрять второй периодический executor.

- [ ] Воспроизвести важный пробел059: после earned level/SP отсутствует самостоятельная учебная цель или фарм продолжает заведомо неподходящий spot. Показывать исходную cause и goal state, не считать одноминутный простой достаточным доказательством.
- [ ] Собрать ограниченный snapshot потребностей: actual level/class/index/skills/gear, SP/Adena, HP/MP/смерть, предметные запасы и вес, текущий договор Party/HOLD/instance, доступные farm/NPC/market routes и последствия недавних неудач.
- [ ] Producer выбирает по проверяемым условиям: urgent survival/ownership → supplies/разгрузка → подходящее обучение/профессия → полезный farm/party. Сохранить вариативность персонажа; не сортировать всех по одной жёсткой линии одного NPC.
- [ ] Новая цель в existing goal store: причина, class/index, текущая revision и условия пересмотра. Коалесцировать одинаковую потребность; hysteresis/backoff от метаний между двумя спотами; declined/no-path/source-empty запоминаются до изменения фактов/ограниченного retry.
- [ ] Выбор фарма учитывает native level/XP/drop restrictions, нужный ресурс, риск/доступность/стоимость дороги, congestion и способности класса. Угроза/слабая отдача/исчезновение подходящих целей должны вести к новому законному месту. Не уходить от каждого RNG-промаха и не навсегда оставаться на гремлинах.
- [ ] Использовать подтверждённый learning executor059, но вызов обязан возникнуть из producer. После платы пересчитать current skill ranks/role/cost; старая дешёвая версия может быть выгодна, нельзя удалять её из Player по возрасту.
- [ ] Внутри одной positive chain: earned событие → автоматически созданное решение → выполненный маршрут/операция → следующая полезная цель. Second input: другой класс/регион либо исчезнувший источник/слишком дорогая покупка; producer сам выбирает альтернативу. После второго сценария не заявлять universal all-class complete.
- [ ] Ordinary save/restart в одной уже начатой цепочке: intent и earned сохраняются, цели переавторизованы, нет повторного списания/старого маршрута; native Party не создаётся из воздуха. После проверки commit/push, затем продолжить P2/P3.

## P2. Пешком, Gatekeeper, supplies и offline — связанный жизненный цикл
**Owners:** `L2jCommerceBackend`/`PhantomCommerceService` (BUY/SELL/TELEPORT), existing Navigation/Topology, `PhantomMultipartyEconomyService`, `PhantomStoreService`, native `PrivateStoreService`/`ManufactureService`/RecipeManager. Не заменить owners прямым SQL.

### P2a. Переходы между локациями
- [ ] В отличие от запрещённого test-TP, **штатный платный/бесплатный по игре Gatekeeper TP разрешён и нужен**. Выбор endpoint из TeleporterData/knowledge; native NPC interaction, уровень/karma/instance/quest/время/fee условия, хватит ли средств на следующий leg/возврат.
- [ ] Walking leg — actual XY/Z/instance через existing corridor/pathfinding; не требовать прямую линию к центру NPC, если canInteract допускает точку рядом. Plan может содержать несколько GK legs; min proof — один native GK transfer в цепочке, не смена XYZ тестом.
- [ ] TP завершение/деньги/позиция проверяются owner; busy/partial/unknown outcome не приводит к повторной плате. Новый focus/instance/Party/HOLD отзывает несовместимый маршрут. Пет и Party следуют лишь по правилам своих native переходов.

### P2b. Соски/стрелы/корм и разгрузка
- [ ] Правильный тип и grade определять по оружию/классу/native item data; для pet — его shots/feed, не оружейные соски владельца. Включить штатный AutoUse/shot charge и проверить реальное потребление в бою.
- [ ] Low-water trigger и target-stock по ожидаемому расходу, Адене, свободному весу/слотам; сохранить резерв на телепорт/основную еду/fee. Не покупать20к зарядов трёх типов каждому. Native движения доступны ПОСЛЕ закупки; validateWeight alone не выдавать за полную мобильность.
- [ ] Сначала фактические NPC offers/stock/prices/tax/conditions; если товар там отсутствует/недоступен — действительное private offer либо manufacture. Не добавлять B/A/S в NPC buylist лишь ради простоты.
- [ ] Продать только безопасный surplus: не экипировку, quest/control/ключевые crafting/recovery предметы, не зарезервированное. Уважать native sell/refund/tax; если current backend честно не поддерживает имеющийся канал, добавить соответствующий canonical путь с conservation или выбрать другой законный источник, не выключать защиту.
- [ ] After restock actor сам возвращается/выбирает следующий farm, а не ожидает команду оператора.

### P2c. Offline purchase / manufacture
- [ ] Discover действительных nearby/известных market offers bounded через существующие services; цена/остаток/owner/instance/состояние лавки перепроверяются у прилавка. Нет всезнания о неопубликованных товарах и global World scan каждый tick.
- [ ] Сценарий1: Phantom покупает подходящие соски у реально открытого OFFLINE_STORE; предметы/адена меняются у обеих сторон, рынок конечен, после сделки расходует купленное. Подготовленный TEST продавец offline действительно detached, а не имитация отсутствия GUI.
- [ ] Сценарий2: через native MANUFACTURE заказывает рецепт у offline гнома с реально изученным recipe; customer приносит материалы и fee, MP/ресурсы/шанс/результат идут через RecipeManager. Отдельно тест ordinary own craft допустим, но не подменяет заказ у offline manufacture.
- [ ] Offer changed/sold out/слишком дорого/нет материалов/no recipe/crafter busy/нет MP/лавка закрылась → точный отказ/requote/альтернатива; никакой бесплатной выдачи или двойного результата. Состоявшуюся операцию после callback/restart не повторять. Проверить одновременные покупки последнего stock/recipe retry адресно.
- [ ] Экономическое наполнение без владельца: небольшие TEST merchant roles/recipes/stocks разрешены pre-scene; продукт не создаёт бесконечные запасы. При отсутствии рынка producer может добыть нужные материалы/деньги или выбрать доступный farm, не притворяется, что все изделия продаются.

## P3. Настоящая карьера до профессий
- [ ] После P1/P2 реализовывать source-backed producer профессиональных целей, используя actual PlayerClass/SkillTree/вillage_master/QuestState и существующие quest/acquisition adapters. Начать законченной canonical первой профессией, затем40/76, subclass/noblesse по поддержанным путям.
- [ ] Уровень до начала сцены может быть near-threshold fixture; события уровня/квеста/платы/transfer внутри сцены только игровые. `setClassId`, выдача marks или AutoNoblesse — не доказательство прохождения. Не писать универсальный solver543 квестов вместо working route.
- [ ] Матрица по class path/quest/conditions: реализовано/проверено/не поддержано, с обратным переходом к ordinary life. Не делать единственным product путём тестового Fighter20. Общий выбор навыков/магазина/маршрута применяется и к другому классу.
- [ ] Subclass switch/reload не смешивает SP, skill rank, pet eligibility и career goals. Повтор terminal не выдаёт второй class reward и не повторяет плату. После transfer переоценить spot/gear/shot grade.

## P4. Группа и питомцы — не затыкание нехватки тактики
### P4a. Threat-aware preparation/engagement
- [ ] До engagement: полный roster/классы/learned effects, адекватный native вес/бег, текущие HP/MP, доступные бафы/соски/стрелы, цены и ресурсы. Readiness оценивает boss И его minions/curse, маршрут/staging, запас MP и реальный duty-cycle. Нет кульбита «написал Cardinal, получил Wizard».
- [ ] Boss keeper — подходящий танк с текущими средствами удержания. Другие участники при нападении minion и слабом HP не стоят манекенами: отступают ограниченным native movement к зоне помощи/сбора, не тащат группу бесконечно по карте, возвращаются в бой после безопасного захвата угрозы.
- [ ] Танк управляет несколькими реальными NPC threats: single и, где умеет native, area aggression. Для Aura of Hate проверить actual targetType/effects/current rank/радиус/reuse/MP и каждый разрешённый target/parent/instance. Нельзя снять generic FARM запрет raid-minions или менять hate table вручную. NPC-агро не заставляет human PvP выбирать цель.
- [ ] DD assist priority — атакующие уязвимых участников adds, затем boss; boss claim не теряется у танка, heal/recharge не исчезают из-за narrow role alias. Применение не механическое: только те minions, которые реально присутствуют в данном encounter, без придумывания их для Камалоки.
- [ ] Полный бой после narrow aggro/survival GREEN. Можно выбрать более уместный supported boss/instance до сцены; нельзя этим переименовать старый25199 FAIL в PASS. Подтверждать победу/reward/regroup, не один полезный heal.

### P4b. Pet/BabyPet support
- [ ] Разобрать `BabyPet`/`Pet`/PetDataTable/SkillData и существующий controlled-actor pipeline. Improved/evolved buffalo/kookaburra различаются по native template/level/skills; выбрать подтверждённый variant без догадок о числах лечения.
- [ ] Нужен принадлежащий control item, законный summon, pet level/food/MP/abilities и alive state. Если в продукте нет приобретённого пета — законно приобрести/развить через supported путь либо честно жить без него; никаких free summoned high-level pets для каждого бота.
- [ ] Переиспользовать native автоматическое лечение/recharge владельца. Не вызывать второй heal pulse поверх `BabyPet.CastTask`. Проверить фактический recipient/effect/MP/cooldown/корм. Пет помогает, не заменяет обязательную роль heal/recharge при неподходящих возможностях.
- [ ] Один owner с лечащим pet и один с recharging pet: полезные эффекты, depletion, смерть/unSummon/resummon/save/reload/TP при поддержке native. Нельзя иметь две копии одного pet после reload; действия old-owner не выполняются. Ordinary human Pet control обязателен при изменении общего класса.
- [ ] Подготовленный развитый pet в integration — проверка совместимости/поддержки, не заявленная earned evolution. Полная автономная покупка/рост pet отдельно помечается покрытием и не должна задерживать P1/P2.

## P5. Завершённые М4/М5/М6 без привязки к одной сцене
- [ ] М4: выбрать до сцены законную reachable safe point, а не обязательно центр Гирана. Штатный retreat сохраняет current enemy/authority, прекращает старые claims, приходит в допустимое место и возвращается к группе. Если route не доказан — другой bounded lawful route/input, не teleport/NSWE bypass.
- [ ] Rift: через существующие M3/Party owners собрать scoped consent на точную цену fragments, roster, instance и срок. Все реальные члены дали согласие; stale/changed party/new price требуют нового. Затем product adapter сам делает entry/fee/room lifecycle; тест не выполняет вход вместо него.
- [ ] Kamaloka positive059 сохранить; новое template не делать обязательным ради разнообразия. В М6 довести открытые death/logout/timer/room/reuse границы supported content без сокращения stock timers.
- [ ] Рейд/инстанс: настоящий consent/readiness→подготовка→entry/staging→kill/reward→regroup/exit→полезная следующая цель. Natural смерть допустима, если штатное resurrection/recovery сохранило roster и outcome; не заменять умершего новым при измерении.
- [ ] Не начинать одновременно пять raw расследований. Завершить актуальную owner-цепочку, опубликовать, затем следующую. При blocker переиспользовать общий прогресс в другом supported scene и честно сохранить gaps.

## P6. Консолидация, обычное появление и доставка
- [ ] Сохранить current М2/М3/SP и работавшую Камалоку. Собрать покрытие110 selected059/30 native inputs; не запускать всё после каждой строки и не удалять прежние IDs ради зелени. В финале выполнить affected актуальный набор; неизменённые дорогие native proofs reuse только по documented source/data equivalence, не числу тестов.
- [ ] Для manual preview не ограничиться сохранёнными XYZ: в собственной058a ordinary composition проверить законный admission/World presence назначенных QA партнёров и хотя бы начало life/party. TEST observer не называется настоящим человеком. Если нет live данных — QA_SETUP_UNVERIFIED и точный диагностический путь; не обещать пользователю пустую точку как гарантированную встречу. Не сбрасывать население для эффекта.
- [ ] Cumulative source/tests/config/data/tools committed → clean build → one stopped058a export в новую release060a → совпавшие schema/state before boot. Все runtime hashes и manifests должны соответствовать одному принятому source и выбранному layout.
- [ ] Start→fresh Status/useful smoke→native save/Stop→sameDB Start→useful continuation→Stop. Наблюдать состояние после операций, не требовать неподвижных байтов живой БД. Свои процессы/writers завершены, protected057/058/059 не менялись.
- [ ] START_HERE_RU: порты/layout, собственный OWNER_LOGIN060.txt или проверенная точная памятка, non-GM access, TestAdmin/kpCat allowlist, роли/петы/предметы/место/условия появления, что реально посмотреть, параметры life/supply/трат. Synthetic OFF ordinary startup. Никаких секретов в Git.
- [ ] RESULT с deliverable statuses, evidence и единственным следующим главным gap. Если весь М7 реально принят по исходному scope — cumulative standalone runtime ZIP без secrets/SQL/raw; иначе каталог с truthful PARTIAL, не фиктивный RELEASE_ALL_GREEN.
