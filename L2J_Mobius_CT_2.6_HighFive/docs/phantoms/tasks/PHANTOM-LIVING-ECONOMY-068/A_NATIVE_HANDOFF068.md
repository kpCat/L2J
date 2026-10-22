# Ordinary A/B native helper068

Итог: PARTIAL T1/T9. Production factory действительно создала8 READY births/ecology и штатно остановилась. Финальная same-identity подготовка R7 остановилась до baseline/measurement: для57 mapped Geo-точек нет одновременно merchant и warehouse в объявленном helper радиусе12к; ближайшее значение17 961. Это ограничение выбранного fixture, не доказанный product viability defect. Заработок/покупка/бой/Sweep/bank и periodic B не измерены. Повторный подбор точек/радиуса и дополнительные запуски остановлены по bounded gate основного исполнителя.

## Exact scope и reuse

Новые файлы:

- `test/java/org/l2jmobius/tests/phantoms/PhantomOrdinaryLife068Native.java`
- `test/java/org/l2jmobius/gameserver/phantoms/PhantomOrdinaryLife068Factory.java`
- Этот handoff.

Прочитаны TEST_CASES T1/T9, historical066R2 handoff, PhantomTask066R2DwarfNativeScene/Input, Task066 ordinary setup, LocalPlayPilotNativeSuite, production ecology033 suite, PopulationManager/Store/Catalog/Ecology, полный production factory и его observer/stop API, stock AutoUse/Soulshot/Creature events, supply policy/quote и существующая ProductionAuthorityFixture. Ручной profiles.create/root/baseline shortcut из старого Dwarf component scene намеренно не перенесён.

Factory bridge открывает только существующий package-private `PhantomSystem.startConfiguredForTesting(Settings)`. Все production owners создаются исходной фабрикой. Новых реализаций Decision, ecology, bootstrap, catchup, simulation, combat, nav, commerce нет. Shared PhantomSystem/Player/Shutdown/launcher/tools не меняются. Root B fixture `Bg068B1`/profile2 helper не адресует; existing non-population role profiles сохраняются.

## Последовательность

Рекомендуется `run 480` одним JVM: native infrastructure → обычные FRESH Manager births при ACTIVE target0/no human → полный штатный STOP → разрешённый остановленный TEST preimage → запуск той же production фабрики с естественным catchup/Decision и native passive access0 viewpoint → только наблюдение → полный штатный STOP.

Число births вычисляется первым ECONOMY ordinal существующего deterministic catalog, максимум24. Реальный pure plan дал8. Его stock FRESH schedule `morning.v3`; до births вычисляется подходящий timezone с запасом±45мин ACTIVE, без изменений schedule components. Actual phase остаётся штатной. ACTIVE capacity во второй фазе покрывает весь маленький cohort: иначе региональная quota1 могла бы не выбрать economy profile. Actual World demand задаётся обычным passive Player около выбранного field; никакого GameClient/synthetic/network proof этим не заявляется.

Helper не вставляет READY, ecology, goal, background baseline или cursor. Перед TEST preimage обязательны настоящий Manager READY, ecology component, правильные seed/ordinal/link и отсутствие background.state. Допускается уже существующий естественный unplanned PENDING goal0 claim только с interval0/cursor=from; он сохраняется без правок. Если baseline или запланированный cursor уже появился — prerequisite FAIL с сохранением identities, без wipe/reset/reseed.

TEST preimage явно содержит Scavenger54/20 с согласованными EXP/skills Spoil2/Sweep1/Expertise1, D gear,20 healing potions, невесомую по native penalty заполненность около79% для последующего material bank и wallet на1 Adena ниже вычисленного порога. Это не заработок. Неиспользуемый birth inventory удаляется только до measurement и до baseline, чтобы его поздняя продажа не выдавала TEST gear за доход. Starter quota0/initial shots0. В измерении helper не меняет HP/MP/gear/balance/skills/goal/cursor, не атакует вручную и не поднимает предметы.

Field выбирается из stock NPC/spawn/topology facts: обычный неагрессивный monster10..18 со Spoil, настоящий Geo, merchant/warehouse в пределах12к. Проверяются не более двух исходных field points до measurement. Неагрессивность определяется stock `isAggressive`, а не радиусом агрессии. Read-only настоящий HistoricalPlanner выполняет глобальное initial planning, затем ту же локальную viability-проверку `replanVisibleLocal`, что использует visible owner; helper не сохраняет ни один goal. Вычисление бюджета использует реальные grade-compatible Soulshot buy offers, существующих World Merchant, stock Mpc base tax, полезный partial count по расстоянию/скорости и существующую supply availableBudget. Допускается только маршрут в прежнем walking budget с обязательной платой0; native navigation остаётся окончательной authority. Начальный wallet=minimumUsefulPurchaseThreshold−1, а не стоимость полного60-hit stock.

## Evidence

Измерение читает native evidence и существующий durable CommerceReceiptStore. Требуются native kill/EXP/pickup и недостающий доход, COMMITTED BUY нужных shots с реальным денежным delta, следующий native charged attack/positive physical damage и новый kill, native Sweep, COMMITTED deposit соответствующего stock spoil material в настоящую warehouse и новый kill после bank. Charged witness — штатные read-only ConsumerEventListener: OnCreatureAttack видит native charged state после prepareOffensiveShot, OnCreatureDamageDealt подтверждает следующий положительный physical hit той же цели. Эти listeners не меняют event result и не заменяют callbacks.

Catchup component/cursor/interval, root revision, native owner, supply/learning/recovery и admission печатаются каждые10сек. При смерти или EXHAUSTED сохраняется failure trace; поздняя revision отмечается отдельно как replan observation. Если природа не дала failure episode, он не считается доказанным. Время helper ограничено30..720сек. Законченная цепочка сразу завершает наблюдение. Early virtual catchup income, позволяющий купить до native earned witness, даёт FAIL, а не native PASS.

Native output TSV имеет timestamp, CREATE_NEW, production code-source и preimage. `ordinary068-preparing.properties` остаётся как маркер начатого preparation; существующие input/pending marker запрещают повторные births. Для разрешённого bounded диагностического продолжения есть `measure <seconds>` на тех же identities, без повторной подготовки.

## Проверенные команды

Из `D:/Codex/repos/L2J-phantom-world` выполнен isolated `javac` двух exact новых Java-файлов с classpath `build-dev GameServer.jar + phantom-test/bin + module/dist/libs/*`, output `.phantom-local/task068/ordinary-helper-bin`. Exit0. Этот output содержит только test helpers, никаких shadow production classes. Финальный compile log: `.phantom-local/task068/reports/ordinary068-compile-helper-only.log` (последующий compile после переноса duration guard также exit0 в tool output).

```powershell
java '-Dphantom.module.root=D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive' -cp '.phantom-local/task068/ordinary-helper-bin;.phantom-local/task068/build-dev/dist/libs/GameServer.jar;.phantom-local/task068/build-dev/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomOrdinaryLife068Native plan
```

Exit0, `BirthPlan[target=8, zone=-03:15, schedule=morning.v3]; DB_WRITES=0; DB_CONNECTIONS=0`. Timezone вычисляется динамически, это пример фактического последнего вывода. Log `ordinary068-plan-helper-only.log`.

Два negative guards реально выполнены без native infrastructure/DB: `run 30` без own068 флага завершился expected exit1 с exact lane refusal; `-Dphantom.task068.native=true ... run 29` завершился expected exit1 с duration bound refusal до module/config access. Logs `ordinary068-guard-no-lane.log`, `ordinary068-guard-duration.log`. Первый промежуточный compile имел duplicate local variable `plan`; он исправлен, это fixture compilation issue, не product RED.

## Native команда после передачи последовательного слота

Working directory: `.phantom-local/task068/native-test/game`. Использовать абсолютный classpath и фактический Java25. Слот выдаёт основной исполнитель; параллельных Ant/DB/runtime не запускать.

```powershell
java -Xmx4g '-Dfile.encoding=UTF-8' '-Dphantom.task068.native=true' '-Dphantom.module.root=D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive' '-Dphantom.test.config=D:/Codex/repos/L2J-phantom-world/.phantom-local/task068/native-test/game/config/Database.ini' -cp 'D:/Codex/repos/L2J-phantom-world/.phantom-local/task068/ordinary-helper-bin;D:/Codex/repos/L2J-phantom-world/.phantom-local/task068/build-dev/dist/libs/GameServer.jar;D:/Codex/repos/L2J-phantom-world/.phantom-local/task068/build-dev/phantom-test/bin;D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomOrdinaryLife068Native run 480
```

Private inputs требуют прежней platform escalation, без изменения ACL. Final committed JAR/Main/T9 sameDB restart этим helper run не подменяются.

Ограничения: prepare может честно остановиться на естественном baseline/cursor prerequisite; field/service route может оказаться непроходимым штатным nav; Spoil RNG и несколько последовательных путешествий могут не уложиться в bound. В этом случае сохраняются exact trace, actors и stock, а результат остаётся PARTIAL. Нельзя добавлять деньги/HP или исправлять goal/cursor ради успеха. B accounting здесь наблюдается той же фабрикой, отдельный exhaustive transaction/idempotence PASS из наблюдений не выводится.

## Native startup evidence

Неизменный dev JAR: `.phantom-local/task068/build-dev/dist/libs/GameServer.jar`, SHA-256 `2A94C3E03DDEBB937F8E1DD3B569AD27A11C41D425748280A6612C5F3E8496DE`. Java25: `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe`.

- R1 `ordinary068-native-r1.log`, exit1: heap2g недостаточен для полного stock QuestMasterHandler compiler. Остановка до factory/births; ThreadPool/Hikari закрыты. Для того же запуска выбран heap4g без изменений игровых inputs/rates.
- R2 `ordinary068-native-r2.log`, exit1: общий production factory отверг stale source pin Kamaloka до scheduler/populationManager.start. Source и ownTEST Java побайтно одинаковы: rawSHA `DA8023FB03E9BF86215BD8A23B46585261679A6B23D5D60A5EB77F8831A45137`, совпадает с SOURCE_SCOPE067. Strict UTF-8 с нормализацией только CRLF/CR в LF даёт `f2100ed10a36b4ae9f8ad0caa9a09371eb7592acad56f65174bfaf776199e22b`; catalog ещё ожидал `f624...`. Основной исполнитель подтвердил историческую принятую правку067 и исправил одну запись текущего source XML. VerifySource не ослаблен. В own068 скопирован только исправленный XML; обе копии rawSHA `9ECE59B3EF2BE17810F1F0EF7AF10EC986C9D50E3645414C168D8D42340FAA5D`. Historical067 не менялся.
- Узкий read-only `inspect-births` в том же helper использует exact PhantomTask068Lane и существующий repository. Реально exit0, `managedPopulation=0; READY=0; ordinaryInput=false; DB_WRITES=0`, log `ordinary068-preflight-r2.log`. Существующий input guard сохранён. Маркер неудачного startup сохранён rename в `ordinary068-preparing-r2-startup-failed.properties`, SHA `F4DAAEA978FAD9E4B8A92C3E4D6315BC24306B48FC49C56DFC2DBC2D16913A72`. Никакие identities/background/cursor не удалялись и не сбрасывались.
- R3 `ordinary068-native-r3.log`, exit1: тот же JAR, heap4g, `run 480`; production Manager/ecology/bootstrap создали8 READY newcomer profiles, active0. `premeasurementClaim=null`, background baseline отсутствовал. Factory штатно остановлена. Legal stopped TEST class/gear применены к выбранному Scavenger до baseline; окончательные wallet/filler/input ещё не созданы. Helper отказал в дополнительном primary/reserve Spoil predicate. Exact trace `ordinary068-run-1792657351899.tsv`, identities сохранены; повторные births запрещены.
- Source review выявил различие API: `planInitial` глобально ранжирует level/route и не обещает Spoil в исходной локальной области; native visible owner использует `replanVisibleLocal`. Подготовлен `inspect-preparation`: exact existing READY cohort, отсутствие input/baseline/catchup, только первые две исходные точки, глобальный и локальный реальные planners через detached in-memory capture. Он не сохраняет goal/cursor/TEST preimage; native load cleanup остаётся штатным. Isolated compile exit0 (`ordinary068-compile-inspect.log`). Refreshed dev JAR для R4–R6 после общего Ant: SHA `FF2C7CDFCA84CAB1BB1EF0A5B68BAD9FA5ACD31D1F2A83D06F3B14EC476A0C47`.
- R4 `ordinary068-inspect-r4.log`, exit0: сохранённый profile10/object268481943, class54/20, native PAtk99.8876/PDef215.82/HP577/MP188, noBG/noCatchup. Реально `farms=0`, поэтому гипотеза о глобальном ranking не была непосредственной причиной R3. R5 `ordinary068-inspect-r5.log`, exit0, narrow filter counters подтвердили `services=291;eligibleNpc=0` до topology/Geo/planner. В helper неверно использовался `getAggroRange()==0`; stock passive Hunter Tarantula16 имеет `aggroRange=1000,isAggressive=false`. Подтверждённый fixture fix: `template.isAggressive()`.
- R6 `ordinary068-native-r6.log`, exit1: `resume 480`, тот же existing cohort, refreshed JAR `FF2C7...`. Guard подтвердил точные8 READY, mode/target/zone прежнего pending, отсутствие input/baseline, null claim, native Adena0, точный multiset10 equipped TESTgear и только20potions unequipped, Spoil/Sweep. Class/gear/HP/MP не выдавались повторно. После passive filter helper дошёл до немаппированного stock SpawnFact и ошибочно передал null в immutableMap.getOrDefault. До wallet/filler/input/measurement. Последняя bounded fixture правка — пропуск `topologyNodeId()==null` по существующему production planner условию; никакой topology/map/world правки. Измерительные причины: passive filter и nullable mapping; heap — infrastructure, stale pin — общий product startup defect.
- Перед окончательным resume выполнен полный read-only pass preparation filters: nullable NPC/template/spoil/anchor/point, immutable-map keys, native weapon/per-hit/merchant/Mpc, source snapshot references, Player.create existing4arg API, exact pending/issued guards и unplanned claim. Дополнительные точки, input grants и time windows не добавлены.
- R7 `ordinary068-native-r7.log`, exit1, окончательный bounded result. JAR SHA `C94966E1E5AFAE7E9407B604357D8936380A1EF602F684484030DE1B5CE2E60D`; helper-only isolated javac двух файлов exit0 (`ordinary068-compile-r7.log`), production shadow classes отсутствуют. `services=291;eligibleNpc=115;mappedAnchors=57;geoAnchors=57;nearServices=0;minimumServiceDistance=17961.183507775873;farms=0`. До movement/планирования/декларации wallet. Exact trace `ordinary068-resume-1792659272644.tsv`: `fieldPrerequisites count=0;primaryReserve=[]`, claim null preserved. ThreadPool/Hikari закрыты штатно, retained configured owners0. Native/DB слот возвращён основному исполнителю.

Сохранённые identities: `3:268481890,4:268481897,5:268481905,6:268481913,7:268481921,8:268481929,9:268481936,10:268481943`. Economy actor profile10/object268481943. Pending mode CANONICAL_MANAGER_FRESH/target8/zone−03:30, SHA `40FB021F0C0190AB8E0B59FDF56D66AA89E194C690B80B0BD4C7394A733CF82E`. `ordinary068-input.properties` не создан. Selected legal stopped class/gear/potions сохранены; wallet0, filler не выдавался. Background/root/cursor не вставлялись и не сбрасывались. Root B profile2 сохранён.

Exact R7 команда — приведённый выше абсолютный classpath и Java25 с heap4g, теми же own068/module/config properties, main `org.l2jmobius.tests.phantoms.PhantomOrdinaryLife068Native`, аргументы `resume 480`; working directory `.phantom-local/task068/native-test/game`, output `.phantom-local/task068/reports/ordinary068-native-r7.log`. Ant/native/DB команды выполнялись только в выданном последовательном слоте; Ant в этом блоке запускал основной исполнитель. Все own068 native JVM закрыты. Дальнейшая delivery/Main/T9 sameDB proof остаётся основной задаче.

Mojibake-маркеры в изменённых файлах проверены отдельной полной проверкой.
Escaped Cyrillic в изменённых файлах проверены отдельной проверкой всех шести regex-паттернов.

Git-команды в этом блоке не использовались.
