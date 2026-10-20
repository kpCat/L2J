# DESIGN / IMPLEMENTATION061 — две сквозные игровые вертикали

## Общий контракт зарядки и доступного снабжения20октября

Тип оружейных сосок определяется конкретным native действием, грейд — текущим оружием. Existing NativeShotControl сохраняет независимые SS/BSS auto-shot preferences; native frontend через прежний PlayerNativeWork требует реальный оплаченный charge только у strict Phantom owner. Pet/servitor наследует текущий owner и теряет допуск при detach. Native H5 beast handlers расходуют owner inventory; по явному разрешению владельца допускается конечное пополнение unpacked6645/6647 на60 атак с native weight/capacity, без второго таймера/хранилища.

Reserve500 для экипировки сохраняется. Необходимые для боя SS/SpS/arrows/bolts используют защитный остаток min(configured reserve, четверть фактического кошелька): абсолютный500 при native-born кошельке215 блокировал любые законные покупки и, совместно со строгой зарядкой, весь доход. Общий расчёт применяется при выборе source, создании expenseBudget и native counter; действуют прежние fees/обратная дорога, quoted budget, цена, количество, weight/capacity и атомарный Commerce. Обычные соски/деньги не выдаются; нулевой/недостаточный кошелёк не становится положительным. Данные intents/receipts совместимы, SQL миграция не нужна.

## Поправка владельца20.10.2026: общая автоматическая карьера

Полный Q401 заменён обязательным Phantom-only повышением по PlayerClass20/40/76. Старые QuestState/items и raw FAIL сохраняются. Дополнительного бюджета нет; финальный резерв начинается не позже05:17UTC, дедлайн06:47UTC.

Согласованный срез может превышать10 файлов: существующий career Decision выбирает следующий edge; Progression сериализует native Player/class/noble операцию; существующий background owned-store attests этот же edge. Это одна вертикаль, без нового AI, таймера или хранилища.

- `career.choice` — неизменный выбор конечной ветки в ProfileRepository, до публикации READY у новых профилей, лениво из текущего класса у старых. PlayerClass задаёт дерево и пол Kamael; native CategoryData задаёт предпочтения ролей. Hashed population catalog и веса55/8/8/12/7/10 не меняются. Недоступный старой специализации архетип не разрешает сменить уже выбранную ветку; выбирается законное продолжение.
- Существующий `career.intent` хранит короткую следующую операцию и root/object/class/index witnesses. Перед Player.setPlayerClass проверяются branch/level/ownership/native busy guards; после освобождения ACTION lease выполняется существующий owned checkpoint. Новая операция не обходит сохранение, main/subclass и capacity.
- Receipt остаётся совместимым POS1/POZ1. Вместо исключения только0→1 принимается соседний native edge с сохранённым career.choice и текущим root/child. Старый Q401 attestation сохранён для необязательных legacy/native проверок.
- Отдельный Phantom-only XML управляет профессиями и Noblesse (default main80). Native setNoble/skills/save, без предметной повторяемой награды. ClassMaster.xml/global AutoNoblesse/люди не меняются.
- Проверки: RED текущего producer; RED общего receipt20/40/76; native Decision→owned save→sameDB reload, idempotency/underlevel/branch guards, затем реальная жизнь из целой committed поставки. Подготовленные TEST уровни не считаются заработанными.

> Исполнять inline по executing-plans, без субагентов. /goal пользователя утверждает этот brief. Реализация должна следовать проверенным интерфейсам текущего кода, а не выдуманным сигнатурам. Сначала read-first нужного узла, затем тест/правка/проверка/публикация.

**Goal:** обычный запуск даёт реальную автономную жизнь; карьера сама проводит персонажа через профессию и продолжает развитие.
**Architecture:** существующий shared scheduler/Decision, materialization и owned native actions; QuestInstance/script остаётся владельцем квестов/класса, Progression — навыков, Commerce — оплаты. Новый career producer выбирает цель, но не реализует второй квестовый движок.
**Tech stack:** Java25, Ant, WinPS5.1, stock H5 scripts/data, MariaDB058t/058a.
**Spec:** GOAL + AUTHORITY + ACCEPTANCE; source findings в SOURCE_MAP.

## Review focus — опасные границы заранее
1. Пустой sample не равен пустому World: filters, лимит, ownership и время снимка; отдельный counter contract + actual identity.
2. Preview initial setup не должен тайно исправлять старую БД или каждый boot дописывать готовых персонажей; one-time native setup, stable IDs/restart.
3. Class transition законно меняет class-id: не принять его за чужого owner и не повторить reward/плату. Completion/continuation проверяются по native truth.
4. Во время квеста требуется реальное оружие/предмет/объект; никакого обхода native quest conditions из-за generic FARM/карьерного lease. Missing prerequisites — отказ, не выдача.
5. После restart память, SP/items и quest stage согласованы; owned pending work завершается без duplicate callbacks. Busy/party/HOLD не теряют управление.

## P0. Короткая подготовка и ранний ordinary boot
**Files:** текущие report060/ACCEPTANCE060, LocalPlayPilotService, PhantomSystem (startup/operatorStatus/onlineHumanPoints), PopulationManager/Ecology и runtime tools; связанный код читать адресно.
- [ ] Сверить BASE/OWN/remote, package hashes и реальные058 bindings; один stopped backup058a. Не запускать baseline111 сначала и не обходить старые raw.
- [ ] Выписать из сохранённых060 observer/logs оба zero-среза: runtime/период, independent populationWorldMaterialized/admitted/readiness и actual sample. Отсутствующие данные обозначить отсутствующими. Прочитать реальный sample predicate.
- [ ] Запустить **обычный**058a Main с его текущими documented configs; штатные helpers только Start/Status/Stop. Собственный диагностический источник read-only, не mutation loop. Не откладывать этот boot до конца кампании.
- [ ] Снять согласованные по времени данные: actual World identity/flags/registry, materialization ownership, desired/eligible/admitted/cap, календарь/nextDue, catch-up progress/reason, focal point/instance и текущая root/decision phase. Начать с малого числа профильных IDs, без дампа всего сервера.

## P1. ORDINARY_WORLD_READY — найти и устранить разрыв, а не просто объяснить его
**Owners:** LocalPlayPilotService.refreshObserverFocus/autonomousObservationPoint; PhantomSystem.operatorStatus; текущие Population/Ecology/Scheduler/Materialization; History/VisibleAutoPlay и autonomous learning/supply.

### P1a. Причинная развилка
- [ ] Различить: (a) реально нет подходящих Player, (b) false-negative фильтра/регистра, (c) законное расписание/нет подходящего local actor, (d) незавершённый начальный catch-up, (e) зависшая цепочка demand→admission→materialization→action, (f) неправильный config/datapack/startup dependency.
- [ ] Для установленного дефекта воспроизвести короткий RED на его настоящем owner, исправить и пройти GREEN/negative. При false-negative нельзя объявить жизнь по одному исправленному числу: далее actual action/reward. При отсутствии подходящих входов сначала валидный bounded preview input, а не product patch.
- [ ] Если blocker — catch-up/nextDue, измерить курсор/полезные транзакции и срок до готовности. Не ждать часами без оценки, не подменять время. Fair bounded обработку/ошибочную зависимость от active slots чинить только по причинному тесту. Законное ожидание не равно deadlock; недоказанный warm-all не объявлять необходимым.
- [ ] Небольшая opt-in диагностика в существующем status допустима: фактический охват до/после фильтра, exact причины отказа и свежесть. Не новый agent/controller/«доказатель доказателей». Компонент, обслуживающий Player, и схема сохранения не меняются ради счётчика.

### P1b. Воспроизводимый ordinary игровой вход
- [ ] До нового эпизода определить два пригодных разных class/role профиля и законные конечные ресурсы, loaded geodata/мобов/доступные сервисы. Использовать058a существующих, либо ограниченное добавление AUTHORITY§3. Не обязаны быть именно именованные high-level T058 partners; их отсутствие не оправдывает пустую инструкцию.
- [ ] Подготовка — один раз, до boot/эпизода, с записанными conditions. В ordinary режиме после START никакой test call materialize(), force ACTIVE, ручной root/target/XYZ, kill или каждотактный advance(). Реальный scheduler сам допускает и управляет.
- [ ] Autonomous viewpoint — штатная пассивная точка, не Synthetic Player и не фальшивый REAL. Выбрать её штатным config в пригодном месте до эпизода. Причину исходного zero060 сохранить даже если positive preview использует другой законный вход.
- [ ] Для первого профиля: минимум два full native farm/reward цикла → самостоятельно выбранный learning ИЛИ supply → native journey/плата → return → минимум два новых full native farm/reward цикла. Для второго, другого класса: отдельное actual появление и earned useful action. Этот небольшой контроль не превращается в новый 380-секундный M1 soak.
- [ ] Длительность объявить по маршруту/cooldown/respawn до измерения. При новом input — новый эпизод; старый не стирать. Никакой выдачи недостающего ресурса посреди положительной сцены.
- [ ] Если ordinary actor умирает, проверить штатное recovery и продолжение/смену цели. Не обнулять его историю для повторения. Общие паттерны маршрута исправляются один раз, не привязываются к координатам сцены.
- [ ] Self-review + publish. Наличие фактического World актёра и useful effects должно быть установлено рано; не заменять его «готовый executable».

## P2. Самостоятельная профессия — главный функциональный результат
**Подтверждённые interfaces:** PhantomQuestInstanceService.CLASS_GOAL_TYPE=`class.transfer`; target namespace=`supported.content`; content=`class.warrior-q401`; `advance(profileId, goalId, goalRevision, activityState, logicalNowNanos, token)`. Existing warrior() обрабатывает QuestState и штатный перевод0→1. Backend.invoke вызывает exact script owner, а не сам меняет class.
**Files:** PhantomHistoricalBackgroundService/current Decision root producer, PhantomAutonomousLearning (pattern, не переписывать его), PhantomProgressionService/Model, PhantomQuestInstanceDecision/Service/Catalog/Backend; actual whitelist XML и native Q401/village-master scripts найти через catalog owner. Tests расширить рядом с existing questinstance/native career modes.

### P2a. Не придумывать второй движок
- [ ] Прочитать точный Q401 content/owner/requirements и ближайшую historical проверку, определить существующий gap source→goal. Контентный каталог сейчас exact-five whitelist, а CLASS_PATH в сервисе идёт в warrior(); это не универсальное исполнение любой профессии.
- [ ] Причинный RED: подходящий ordinary профиль имеет lawful eligibility и ещё не выполненный Q401, но producer не создаёт классовое намерение. Тест наблюдает production decision, а не сам ставит class.transfer.
- [ ] Добавить минимальный data-backed выбор карьерного направления в существующую цепочку needs/Decision. Приоритет: безопасность/чужой party или HOLD → критические ресурсы → текущий career intent/профессия → обычное обучение/фарм. Никакого принуждения всех бойцов к Warrior; профиль выбирает поддержанный путь своего класса/предпочтения. Unsupported ветвь сохраняет обычную жизнь и ясную причину.
- [ ] Передать владение текущему QuestInstance через согласованный root/child protocol. Проверить, что existing validGoal действительно принимает такую цель; не обманывать его target/hash. Сохранить parent farm continuation, class-index, ожидаемый конечный class, progress/revision. Native бой заканчивается до перехода; чужая Party не распадается ради профессии.
- [ ] Квестовые шаги исполняют только текущие audited native scripts. Producer не вызывает onKill/награду вручную и не выставляет QuestState/класс. Конкретные данные/NPC/шаги принадлежат whitelist, не имениT061/TestAdmin. При расширении каталога нужны явные новые entries и backward compatibility, не catch-all.

### P2b. Полный путь, не только приём квеста
- [ ] Исходный новый TEST actor может иметь подготовленный19-й уровень/близкий EXP и обычные конечные средства, но никаких заранее выданных quest marks/завершённых стадий. Stock eligibility старта проверить по коду, не по памяти.
- [ ] Сцена: natural eligibility/earned progress → самостоятельное намерение → полный Q401 с разговорами, штатными kills/drop/required weapon → актуальное условие level20 → native master перевод Fighter0→Warrior1. Между NPC допускаются обычные WALK/GK по правилам, не fixture TP.
- [ ] При смене класса распознать **ожидаемый успешный переход**, а не отвергнуть его как stale исходный class. Обновить текущее дерево навыков/loadout/границы цели, не выдавать skills из воздуха. Купленное штатно за SP умение нового класса затем реально применить; после возврата минимум два useful фарм-цикла на цели, подходящей новому уровню/классу по текущей knowledge-policy. Бесконечный возврат к явно переросшему стартовому споту не считается успешной карьерой.
- [ ] Restart в незавершённом квесте либо на классовой границе: сохранить actual QuestState/items/root, создать новую обычную JVM и продолжить через тот же product producer без повторной награды/платы. Достаточно одной выбранной содержательной restart-границы плюс focused negatives остальных; не повторять весь квест для каждого поля.
- [ ] Проверить wrong race/class/index, недостаточные условия/предметы, already transferred/repeated callback, dead/busy/foreign owner, HOLD/party interruption. Ordinary human control нужен для реально изменённых shared paths, не для всех квестов мира.
- [ ] Native positive сначала на058t для быстрой причины, затем **содержательное сквозное подтверждение на ordinary058a** без тестового управляющего цикла. Можно объединить его с P1 и restart-доказательством, не запускать два одинаковых многочасовых опыта.
- [ ] Commit/push достигнутого producer+route+tests, обновить честное coverage: этот class-path закрыт, остальные не становятся PASS автоматически.

## P3. Продолжать карьеру, когда A/B действительно готовы
- [ ] Второй иной class-path или следующий переход40 выбрать по фактическим stock scripts/requirements. Начальные подготовленные уровни для isolated условий допустимы, но не считать их earned1→40. Не копировать весь warrior() для каждой ветви: использовать компактные explicit adapters в существующем service, лишь где действительно разные протоколы.
- [ ] Затем поддержанные76/subclass/Noblesse, если остаётся время на законченный сценарий и final validation. Не занимать весь резерв исследованием всех543 квестов.
- [ ] Корм: реальная нехватка → NPC покупка → законный transfer своему Pet → native consumption/support. Разгрузка: data-backed защищённые категории, нужные quest items/экипировка/материалы/reserve сохраняются; продаётся лишь проверенный ненужный surplus по штатной операции. Отсутствующая безопасная классификация — не продавать, а выбрать иной путь, без удаления предметов.
- [ ] Питомцы/боевые роли060 сохраняются; новых рейдов и повторов25199 не запускать в этой кампании, если только точечный бой не непосредственная часть выбранного классового квеста. Не заменять карьеру ещё одним pet/buff PASS.

## P4. Поставка является частью результата, а не архивированием кода
**Files:** текущие tools/phantom-runtime (Runtime/Build-Package/export/explicit guards), dist START/STATUS/STOP/START_HERE_RU; новый RESULT/ACCEPTANCE061.
- [ ] Final functional code/data commit → clean tracked archive → оба целых JAR/datapack/config/tools. Никакого тестового classpath/overlays в ordinary server. Тесты запускать на том же product JAR, различая test classes и продукт.
- [ ] Один stopped export058a → новый release061a, проверить затрагиваемые schema/data/identities; миграции не создают повторные персонажи и не выдают повторные marks. До boot фиксируются actual current profiles; старые prepared snapshots и earned не теряются.
- [ ] Final selected111 из060 сохраняет каждый ID; добавить адресные061 tests. Не обязателен повтор каждой прежней долгой сцены, если семантика не затронута; reuse требует source/data/owner объяснения. Основные W1/C20 ordinary факты должны относиться к текущему final product. При неизменных коде/данных допускается объединить финальную проверку с последней сквозной сценой, не добывать повторные доказательства ради номера папки.
- [ ] START.cmd из WinPS5.1/внешнего CWD → возврат управления после запуска → STATUS actual World/actions → штатный STOP/save → sameDB START → useful continuation → STOP. Первый CMD/harness defect060 разобрать до запуска длинной кампании, stdout/stderr/exit не путать. Не принимать лишь прямой Runtime.ps1 за проверку пользовательского entrypoint.
- [ ] Подтвердить runtime worlds/current IDs рядом с указанной стартовой областью; frozen savedXYZ недостаточно. В инструкции дать актуальные имена/роль/место/условия расписания и существующий read-only способ узнать их статус. Видимость настоящим клиентом остаётся ownerQA, но техническое наличие/исполнение не оставлять без проверки.
- [ ] На финальном source/JAR после рестарта повторить короткий ordinary useful continuation, не второй полный Q401. Зафиксировать class/skills/quest/earned conservation; новая карьера не должна зависеть от старой запущенной tool JVM.
- [ ] Свои JVM STOPPED; HEAD=remote; один отчёт и один coverage TSV. Если A/B не прошли, новая папка только DIAGNOSTIC_CANDIDATE/CORE_INCOMPLETE с точным остатком, не PLAYABLE_READY. Успешные независимые изменения и вся history сохранены.
