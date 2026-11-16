# Восстановить законную сцену до native CUT

## Конкретный дефект предыдущей проверки
`PhantomLearning001ShadowNative.Runtime.actionValue019Solo()` материализует одного class110, вызывает `returnToPartyAnchor` и затем `PhantomLearning001ActorFixture.coldSoloOpeningTargets(player)`. Последняя функция возвращает **пустой список**, если нет подходящего моба в ближайшей зоне/дистанции действительных навыков и нужного запаса HP. Дополнительно действуют обычные `soloOpeningTargets`, `PhantomEngagementPlanner`, farm rewards, Geo/LOS и оценка входящего урона. На 019 это дало `V4_NO_LAWFUL_SOLO_OPENING` до `Combat.startSession`/CUT/score. Один повтор того же boot **не является исправлением**.

## Что изучить сначала (узко)
1. `PhantomLearning001ActorFixture.java:421–488` — `soloOpeningTargets`, `coldSoloOpeningTargets`, `coldOpeningRange`, `inspectColdSoloOpening`, `safeSoloOpening`.
2. `PhantomLearning001ShadowNative.java:1222–1242` — 019 narrow Solo; и методы `prepareAcquired`, `prepareAcquiredOutcome`, `runLearning006Encounter`, `settleLearning004`.
3. `PhantomAutonomousTrainingNative.java:1980–2160` — **работавшая** 016 native admit (source/target/skills/Geo/LOS/Party MP), temporary canonical Spawn, return-to-anchor, placement, exact actor owner, physical match, settlement. Не копировать 51-слотовую кампанию.
4. `PhantomTrainingScenarios.acquiredScenes` и `PhantomEncounterLocalSource.resolve/realize` — native scene source, actual NPC and target placement; построить bounded **single scene adapter** внутри существующего TEST runner, не новый planning framework.

## Допускаемая TEST-подготовка
- Из сохранённого sealed 016 выбрать предсказуемый ранее successful Solo-class сценарием, строго по SHA/source/inventory. Впервые использовать не более 2 кандидатов. Состояние персонажа и inventory актуальны на приватной копии; старые pre-CUT пары не считать активным разрешением.
- Если используется отдельная native fixture spawn (`Spawn`/`PhantomEncounterLocalSource.realize`) — NPC настоящий GameServer, canonical template, target legal, без champion/raid/aggro/invul, полный Geo/LOS, skill/MP/owner checks. Только изолированная TEST-установка, без подмены боевого результата, и явно отразить в evidence `TEST_FIXTURE_SPAWN=true`.
- Не отключать и не ослаблять `PhantomEngagementPlanner.viable`, `farmRewardAvailable`, `PhantomSkillInventory.eligibility`, `PhantomCombatPolicy`, faction isolation, hit/cast conditions, owner leases, native cast permissions. Если существующий 016 путь использует отдельную допустимую ветку admission, переиспользовать её, но **не заменять** за счёт фиктивных HP/MP/EXP.
- TEST может использовать штатное позиционирование актёра при построении законной тестовой сцены (как в 016) только при прежних physical/Geo constraints. Никаких телепортов при обычной игровой эксплуатации.

## Границы доказательств
Preboot можно проверить **сохранённую согласованность** scene/source/skills/Geo, но нельзя подтвердить будущий World до boot. Поэтому GS внутри одной попытки: (a) recheck живого world; (b) fresh source/owner/target; (c) legal request; (d) CUT; (e) actual native paid HP/MP; (f) post-drain original receipts. Если stage a–c отказал — `NO_LAWFUL_SCENE`, не запускайте server второй раз. Не заявлять native success только по exitcode0, empty target list или успешному `Spawn`.
