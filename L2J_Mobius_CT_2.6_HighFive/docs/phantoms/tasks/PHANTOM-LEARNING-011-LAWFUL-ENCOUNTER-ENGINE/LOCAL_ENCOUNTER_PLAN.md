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
