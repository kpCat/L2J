# PHANTOM-CITY-JOURNEYS-097 — уточнение G0 после разрешения самостоятельного выбора

28.10.2026. **PARTIAL / G0_UNPROVEN / NATIVE_NOT_RUN**.
Исходный HEAD проверки: `5b33a5d91830d7756f585fcaef7cd71b5fd44291`, ветка `feature/phantom-world`.
Это дополнение к опубликованному отчёту097; его исторические результаты и task package не переписываются.

## Исправление предыдущего вывода

Пользователь уже разрешил самостоятельный детерминированный выбор ordinary actor и одну create-only fresh TEST097 на loopback3308 при доказанном отсутствии schema. Запрашивать у него profileId/objectId/путь для этого не требовалось.
Наличие разрешения не является доказательством подходящего игрового состояния. Own actor ID должен получиться из обычной creation saga; предшествующие foreign TEST094/096N, MAIN/PLAY и owner environment не используются.
Прежняя проверка11 StartingClass доказывает только отсутствие complete direct/одного-GK Market route из штатных birth positions. Она **не доказывает невозможность естественного farm→city визита** и не заменяет аудит последующих production переходов.

## Проверенные source facts

1. `PhantomHistoricalBackgroundPlanner.planInitial:80–85`, `replan/replaceFromState:93–115` допускают OFFSCREEN_OUTDOOR_PENDING, когда фактических имеющихся ресурсов достаточно. Пригодный CERTIFIED candidate вытесняет pending alternatives (`286–295`). Только наличие fallback не доказывает выбор удалённой точки.
2. `PhantomOffscreenOutdoorTravel.sourceBacked:50–65` требует destination FARMING/public outdoor и реальный rewarding MONSTER; CITY_CENTER туда не допускается. Этот guard не изменён.
3. `replanVisibleLocal:119–129` использует overload с `allowOffscreen=false`. Следующий historical replan зависит от настоящего состояния/пригодности, а не произвольного переноса. Диапазон пригодности прежнего target — `level−10 … level+2` (`Planner:219–234`). Из диапазона нельзя вывести измеренную длительность развития.
4. После подлинного farm arrival Market уже может принять retained rest journey: настоящие BUY deficit/SELL surplus/recipe, прежний ACTIVE root/actor, свободный native owner и пригодный complete маршрут (`PhantomAutonomousMarketProducer:327–331,471–508`). Новый City producer по этим данным не требуется.
5. `PhantomSystem:570–575` дополнительно проверяет READY и настоящий schedule: desired SLEEPING либо calendarOnline в последние lifetimeSeconds до nextBoundary. Stock `PhantomMarket.ini` задаёт lifetime120s; оставшаяся expiry ограничивает и travel. Artificial trading window/clock override не применялся.
6. `L2jPhantomSupplyJourney.choices:674–703` выбирает реальные доступные предложения, учитывает purchase/round-trip fees, полезность и capacity. Stock supply policy задаёт journeyMinutes8. Это finite supply need, не обещание выбора Giran. `L2jPhantomLearningJourney:253–264` выбирает ближайших трёх подходящих trainer и собственный WALK path.

Источник реального stock Giran leg и цены остаётся прежним: пять проверенных incoming GK, например Gludio30256/index6/base29000A, Oren30177/index1/base9400A. Main class<=40 может иметь штатную fee0; отсутствие денег по одной base price не заявляется.
Source core FARMING anchors также просмотрены без World/DB: ближайший к incoming GK lower bound36159, семь population initial farm anchors остаются за12km. Это только core/initial positions, не полный generated-world census.

## Точный недоказанный переход

Для одного обычного fresh profile должен быть установлен **выбранный production planner candidate**: пригодный FARMING target, из которого допустим native Giran itinerary. Нужны согласованные level/combat/resource/Geo facts и отсутствие предпочтительного CERTIFIED candidate, если переход опирается на OFFSCREEN.
Затем тот же actor должен естественно получить валидный Market quote и попасть в настоящее schedule window; весь маршрут обязан уложиться в оставшуюся expiry. Ни готовое имущество, ни готовые координаты/уровень, ни нужная цель не назначаются ради теста.
По выполненному bounded source audit такой положительный предикат **не установлен**. Обратное — невозможность будущего ordinary визита — также не доказано. Корректный итог: **G0_UNPROVEN / NO_PREQUALIFIED_FRESH_CITY_VISITOR**, а не глобальный NO_CITY_ROUTE.
Это недостаточность positive evidence для разрешённой дорогой сцены, не отсутствие пользовательской authority и не установленный production crash/bug. Изменение producer, календаря, planner предпочтений или FARMING guard ради положительного результата не обосновано.
Реальные ID выбираются после разрешённого bootstrap; их отсутствие не является дополнительным требованием к пользователю. Подготовка stage и metadata-only отсутствие schema проверены самостоятельно. Bootstrap не выполнен: полная G0-предпосылка для positive native-сцены пока не установлена; создавать ordinary history/нужный surplus/нужное окно искусственно нельзя.

## Выполнение и границы

- Подготовлен один fresh physical external stage под текущим Windows owner, ACL только owner+SYSTEM; source/module/shadow disjoint, no junction/symlink, cwd shadow/dist/game. Immutable inventory29000, Geo203. Shadow Database.ini отсутствует, native attempts0/restart0; TEST097 schema/provisioning/World не создавались.
- Один metadata-only JDBC probe на фиксированном loopback3308 подтвердил global metadata visibility, **schema097 ABSENT**, foreign sessions0, одинаковые foreign metadata SHA до/после. Connection read-only, SQL только фиксированные SELECT/SHOW, DDL/DML0; common grants/service/datadir не менялись. Fingerprint относится к metadata, не к чужим payload. OS census перед probe: Java0, один существующий mysqld; caller SID/source owner совпали.
- Prepare переиспользует097 private вариант существующего Task096Native.ps1: изменены только owner/schema/diagnostic names и known ancestor. Geo rows сначала сверяются с неизменным GEODATA074.tsv; sealed inputs содержат их exact SHA. Probe повторно проверяет все original/shadow hashes, pin-file SHA, UUID/expiry, ACL/physical ancestry, native cwd и source JDBC origin. Прямо перед DB entry повторяются expiry, authority SHA и source Database.ini SHA. Код запускается проверенным Java source-launch, не неизвестным class file. Arbitrary exception text/credentials не выводятся.
- Prepare exit0: `TASK097_PREPARED inputs=29000 Geo=203 databaseConfig=ABSENT DDL_DML=0`. Probe exit0: `TASK097_PROBE_PASS absent=true inputs=29000 Geo=203 foreignSessions=0 foreignMetadataUnchanged=true SQL_DDL_DML=0 World=NOT_STARTED`. Authority/schema/native scene ещё не проходили bootstrap/native acceptance. Guard-negative launch matrix для новой narrow probe authority не запускалась.
- Private paths/UUID/seals находятся в `.phantom-local/task097/native-stage.json` и собственном external stage; receipts prepare.completed/private/probe.completed сохранены. Полные логи — `.phantom-local/logs/PHANTOM-CITY-JOURNEYS-097/native-prepare.log` и `readonly-probe.log`. Prepare script SHA256 `9401f3c5f4b4f628436b55a893bc7be650b997b9ce43d099fd7ec2da28341007`, reviewed Probe097.java SHA256 `fdb423fd0e4d1b72a41ba5e2e92d7b41e52340a866518a4f36216c453d1cc57d`.
- Исходный ordinary census097 по БД отсутствует; фактические actor ID/XYZ/items/root/epoch не выдуманы. OWNER_CLIENT_QA/KNOWN_LIST, native arrival и STORE/restart остаются NOT_RUN.
- Повторно прочитаны bounded Market/Supply/Learning/composition/Population seams; дополнительные причинные источники — HistoricalPlanner/HistoricalService, supply policy, Market policy/config, bounded092B outcome; для preflight — ближайшие096 Prepare/authority analogues. Не запущены защищённые TEST094/096N и чужие процессы; их DB/ACL/Geo/OwnerConfig/Semantic Pack не менялись. Geo только read/copy в собственный stage.
- Независимый read-only reviewer подтвердил допустимость natural FARMING→Market цепи и ограниченность прежнего birth-only вывода. Он также не установил конкретный positive candidate и не обосновал production fix; DB/World/test runs у reviewer0.
- Независимый review narrow probe нашёл P2 expiry-at-DB-entry; исправлен до единственного DB connection. После повторного review открытых P1/P2=0 в metadata-only probe; это не review bootstrap или native World. Новых Ant compile/test/JAR runs0; один JDK source-launch compile/run probe. Production/test source не изменён, прежние GREEN результаты не выдаются за native evidence.
- Текущий exact allowlist: только этот новый отчёт. Normal non-force publication разрешена исходным TASK097/уточнением пользователя; при remote advance — fail closed. Полный publication SHA/receipt — в финальном сообщении и private task097 receipt, без самоссылочного hash внутри commit.

Git проверки этого продолжения: `git rev-parse HEAD origin/feature/phantom-world`, `git rev-parse HEAD`, `git status --short --branch --untracked-files=no`; Prepare делает bounded rev-parse/symbolic-ref/merge-base ancestor guard. Перед публикацией: свежие fetch/live comparison, exact-file add/diff/check/commit и `git push origin HEAD:feature/phantom-world`. Эти команды разрешены TASK097; force/history rewrite/clean/stash/reset не применялись.
Mojibake-маркеры в изменённом файле проверены: PASS, полный заданный набор, 0 совпадений.
Escaped Cyrillic в изменённом файле проверен: PASS, все шесть шаблонов, 0 совпадений.
Задача не объявляется SUCCESS; после безопасного PARTIAL STOP, без автоматической native-кампании или нового gameplay scope.
