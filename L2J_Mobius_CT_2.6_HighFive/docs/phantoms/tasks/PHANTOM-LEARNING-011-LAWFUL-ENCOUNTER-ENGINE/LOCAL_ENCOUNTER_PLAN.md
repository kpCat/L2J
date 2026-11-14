# Локальный source contract011 — согласованное продолжение

14.11.2026 владелец выбрал вариант1: продолжить тот же Goal011, разрешил минимальные additive изменения knowledge builder/background planner и потребовал сохранить глобальный farm contract. База продолжения: `e9cf75a2d2327fe5b7ef299c496386e219c8941e`, source004 и GitHub feature совпали, source004 чистый.

## Read-first и причина

Прочитаны AGENTS, builder/planner, canonical L2jGameKnowledgeBackend, SpawnFact/TerritoryGeometry, NpcSpawnTerritory.geometrySnapshot, topology anchor/node, PhantomGameKnowledgeParitySuite (testTerritoryCoverage), Farm074 fixtures, acquired scene/native paths и build.xml. Переиспользуются immutable loader geometry/hash и contains с banned-полигонами, FARMING anchor, existing native Combat/Party owners и pre-CUT gate. Дополнительных AGENTS/code-map/pattern-файлов в целевых java/test каталогах не найдено. JDK25/Ant; никаких новых зависимостей.

Глобальный exactTerritoryNode требует один anchor в2000 от каждой вершины; это намеренная семантика, подтверждённая parity test22. Для конкретного native target внутри широкой территории нужен отдельный локальный witness. Успех такого witness не делает всю территорию пригодной для фонового фарма.

## Exact source allowlist продолжения

Пути относительно модуля High Five:

1. `java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java` — отдельная pure проверка concrete source/geometry/point/anchor, без изменения build/mapTopology/exactTerritoryNode/hasFeasibleAnchor.
2. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java` — отдельный local encounter entrypoint, сверяющий witness с immutable canonical facts; прежние методы не менять.
3. `test/java/org/l2jmobius/tests/phantoms/PhantomEncounterLocalSource.java` — минимальный общий prepare/live adapter: canonical loader provenance, ephemeral local FARMING anchor, exact binding fingerprint, fresh source/Geo/faction checks. Не добавляет узлы/anchors в global topology и не подменяет loader SpawnFact.
4. `test/java/org/l2jmobius/tests/phantoms/PhantomEncounterLocalSourceChecks.java` — focused RED→GREEN по пяти требованиям владельца; настоящая SilentValley source geometry читается из canonical XML, synthetic negatives явно являются unit fixtures.
5. `test/java/org/l2jmobius/tests/phantoms/PhantomTrainingScenarios.java` — использовать local source witness вместе с прежним source route; только фактические NPC current/spawn positions, без расширения сетки.
6. `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java` — отдельная explicit local source policy только для acquired encounter fixture, с ownership/incarnation и fresh validation; global policy без изменений.
7. `test/java/org/l2jmobius/tests/phantoms/PhantomAutonomousTrainingNative.java` — восстановление source witness из sealed identity, повторный bind перед CUT, сохранение естественного HP без искусственной травмы; Solo/Party DAMAGE могут закрыть required coverage.
8. `build.xml` — focused local contract target и вызов в011 gate.

Это bounded exception: восемь путей продолжения одного семейства Encounter/Admission плюс связанные task evidence. Ранее опубликованные source пути сохраняются; final scope guard перечислит суммарный exact diff. Документация — текущий task directory, существующий report011 и COMBAT-AI-ROADMAP. Private SQL/weights/Geo не публикуются.

## Влияние на публичные контракты

Только additive local validation methods. Global snapshot, topology XML, source indexes, background source ranking/availability, exactTerritoryNode и partyNativeTarget остаются прежними. Local anchor — явный ограниченный witness конкретной encounter fixture; он не является разрешением фоновой симуляции или маршрута. В native Party source gate по-прежнему действует обычный backend; local policy предоставляет проверяемую source identity, а не обход Combat/Party/risk/owner/Geo/faction.

## Проверки и stop conditions

1. Сохранить исходные body hashes глобальных методов; focused failing local contract test (отдельно отличать compile RED от behavioral RED).
2. GREEN SilentValley local + прежняя feasible territory + wrong source + Geo/LOS/faction negatives; old global bodies/hash и targeted farm regression.
3. Targeted011/autonomous/007/008/009/010, JAR последовательно. Затем один fresh headless Prepare с новой source fingerprint; старое evidence не переписывать.
4. Только после реального paired headless Solo+Party readiness — GS pilot. Бюджет тот же011: максимум3/45мин, фактически0/3; история009/010 сохраняется. Никаких fit/credits/модельных изменений.
5. Если global semantics нельзя сохранить — остановиться с конкретными альтернативами. Не менять Player/Combat/Party/Geo, rates, SQL schema, MAIN/PLAY или protected assets.

Пока не подтверждены: native transfer local source identity, фактический headless coverage, живой CUT/paid/terminal и matched Solo/Party AB/BA. Новый локальный contract сам по себе не доказывает полный combat admission.

## Первый фактический local preflight

Source `eea088af…` опубликован и9полных GitHub bodies проверены по Git blob SHA1. Preflight PASS, SQL183 SHA совпал с историей. Fresh headless session `4e4c34805e5a46f1a41a518fd8ab39d0` построила2SoloDAMAGE+2PartyDAMAGE, все четыре на canonical SilentValley local witnesses (20970/20966). Первый arm отказал до native admission/CUT: legacy reproduceRoster вызвал global planInitial, а L2jPhantomBackgroundAuthority.exactAnchor закономерно не знает ephemeral local anchor. Worker86816 exit2, wall56095ms, CPU119.5s; GS0.

Это проверенный integration RED переноса local witness, не причина ослаблять exactAnchor. Local fixture теперь создаёт явный ordinary Party intent непосредственно из validated witness; global planInitial/replanLogicalRendezvous вызываются только для global source. Existing Party parser round-trip проверяется focused test. Новый intent не даёт background permissions; native Combat/Party/Geo/faction/risk допускаются отдельно.

## Native realization contract

Вторая session `4fdda1d381a34321be595b470008b505` (sourcec458637d…) прошла roster/source restoration, но первый Solo arm отказал до CUT с AUTONOMOUS_LOCAL_SOURCE_NATIVE_BINDING. Worker48008 exit2, wall59031ms, CPU122.21875s; GS0. Разделены canonical origin, declared target point и owned native clone.

Native Spawn применяет RandomSpawns к exact spawn при heading != -1. Реальный TEST config включает RandomSpawns. Первичная гипотеза о различии default heading уточнена review: Spawn наследует Location.setHeading, а он clamping преобразует -1 в0 даже для canonical territory source. Отдельный focused runner `PhantomEncounterLocalSourceChecks spawn-sentinel` подтвердил behavioral RED: requested=-1, actual=0. Это реальный native contract defect, не основание обходить setter или менять config.

В рамках разрешённого fixture scope добавлены pin/copy фактических heading/randomWalking/chaseRange и bounded origin/realized diagnostics. Они сохраняют source parity, но не объявляются исправлением native sentinel. Один диагностический headless pass должен сохранить фактические координаты до запроса отдельного решения о native Spawn. Location/Spawn/gameplay configs не изменены. Не запускать GS и не заявлять FULL при этом отказе.

## Разрешённый Spawn sentinel scope

Владелец явно разрешил минимальный native fix в том же Goal011. В exact source allowlist добавлен только `java/org/l2jmobius/gameserver/model/spawns/Spawn.java`: override setHeading(int) сохраняет -1, все остальные значения передаёт прежнему Location.setHeading с прежним clamp. Generic Location, RandomSpawnsConfig, spawn algorithm, координаты и ограничения не меняются. Согласованный patch — SPAWN_SENTINEL_PROPOSAL.patch; до применения был NOT_APPLIED.

Read-first подтверждает protected heading в Location, отсутствие override в Spawn, native territory/internal RandomSpawns calls setHeading(-1) и существующие doSpawn/decreaseCount/respawnNpc lifecycle APIs. RED sentinel уже воспроизведён в actual Spawn(null), без Reflection или выдуманного NPC. До правки сохранить byte-copy Spawn и SHA.

Проверки: sentinel, обычные/граничные значения в existing focused runner; native doSpawn/respawn в isolated headless fixture при временных true/false флагах с finally restore и без изменения config-файла. Проверить отсутствие второго смещения territory/explicit sentinel и обычные exact spawn inputs, записать реальные initial/cached/respawn позиции. Test fixture lifecycle не является игровым A/B, не создаёт CUT/credits и не меняет HP вручную. Ожидаемое влияние: внутренние native setHeading(-1) сохраняют sentinel, поэтому native respawn следует existing cached-source branch без дополнительного RandomSpawns draw. Любое иное подтверждённое влияние — STOP и отдельное согласование.

Затем полный дешёвый paired Solo/Party preflight. Лимиты по-прежнему3GS/45мин, фактически0/3. Никаких fit/model/optimizer/SQL lineage изменений; global farm byte-preservation gate остаётся обязательным.

Override применён только в Spawn. Previous Spawn bytes после удаления ровно override совпали с SHA019240d6…c183fa. Sentinel behavioral RED→GREEN:9heading values×2flags, ordinary Location clamp unchanged. Full Ant011/autonomous/008/007/JAR exit0,185s. Independent static review не нашёл оставшихся P0/P1/P2; замечание о null exemption set при изначальном False исправлено только в isolated test fixture с точным finally restore.

Перед Spawn scope frozen SQL readback дал183строки и прежний SHA9a9c2d0e…4da70e7d. Cold export CRC12entries PASS;11frozen payloads byte-identical, в manifest отличается только createdUtc. Native8case lifecycle и полный paired preflight ещё NOT_RUN на момент source публикации; unitGREEN не заменяет эти gates. Cumulative bounded exception включает9source paths продолжения и документацию/evidence одного семейства; native core expansion ровно один явно согласованный Spawn.java.

## Общий native gate до sealing ячеек

Sourcee98c94… headless sessiondcbd237…: native8case lifecycle PASS, config восстановлен. Шесть pre-CUT arms прошли (две Solo-пары, одна Party-пара). Последний Party получил штатный WAIT_GROUP_DAMAGE:effectiveDPS172.10/HP12476, duration>60000. Source/Geo/skills/request constructor готовы; native group rule не ослабляется.

Причина остаточного расхождения: статический planner запечатывал первую подходящую ячейку раньше полного native group admission, затем один pre-CUT отказ завершал весь catalog. В рамках прежних fixture paths full paired admission переносится до включения ячейки в cells.tsv. Переиспользуется тот же collectEpisode(admissionOnly=true)/canonical owner, без копии private groupViable или DPS формул. Только отдельный PARTY_DAMAGE_REFUSED до CUT допускает следующий кандидат прежней12-scene страницы: actual wait обязан быть WAIT_GROUP_DAMAGE и единственная reason должна точно ему соответствовать. Generic PARTY_TARGET_REFUSED, owner/source/config/паритет/STOP и смешанные diagnostics фатальны. Общий hard cap12уникальных native headless кандидатов; повторные ключи не запускаются заново. Не расширяются координаты, NPC lists или skill lists; никаких игровых исходов/credits при этом отборе нет. После CUT такой replan невозможен.
