# Read-first факты координатора (pinned aa63daf…)

Проверен remote HEAD; namespace HighFive. Причины tail357/producer566 остаются UNKNOWN,
и этот пакет не объявляет их заранее исправленными.

| Файл / ориентир | Факт | Вывод для038 |
|---|---|---|
| PhantomBackgroundDecision.java,bindVisibleLife/registerCandidates | уже есть weighted candidate, START/AWAIT и typed arrival | не подключать второй планировщик верхнего уровня |
| AutoPlayTaskManager.java,315–347 | discovery требует canSeeTarget AND canMoveToTarget из текущей точки | no direct line не равен no path; обход может никогда не запрашиваться |
| AutoPlayTaskManager.java,123–170 | casting/sitting/queued → skip; corpse имеет отдельный Sweep-путь | не сбрасывать любой corpse target до native Sweep; late hit не новый выбор трупа |
| AutoUseTaskManager.java,289–381 | native target-dead check и canUseMagic перед useMagic | нельзя объявить dead-target loop без точного времени; guards сохранить |
| PlayerAI.java,thinkAttack/thinkCast/thinkPickUp | already move-to-range,doAttack,doCast,doPickup | action adapter не считает damage и не пишет inventory |
| PhantomVisibleFarmTravel.java,hasLocalOpportunity/advance | local opportunity, terminal attempts, canonical stand-point и arrival store | общий свежий action-position query; не дробление прямой заново |
| PhantomNavigationService.java,submitWalkCorridor/cancel/consume | bounded queue; activeByProfile запрещает второй одновременный request | reuse, не новый worker на каждого бота |
| PhantomVisibleAutoPlay.java,Policy.acquireTick | current lease и PAUSED/REVOKED,resources,cast settlement | один authoritative mode; не дублировать writer в двух pools |
| PhantomVisibleAutoPlay.java,Session/stop/forget |037 держит useful debt в inactive Session sameepoch | сохранять debt на retries/replans; terminal forget обязательный |
| PhantomSystem.java,520–650 | уже создаёт party/raid/semantic/clan и busy sources | это не зелёные milestones, но новые дубликаты запрещены |
| PhantomCombatCapabilityResolver.java | capabilities из knowledge/progression и native lease skill support | чтение/переиспользование facts, не открывать конкурентную combat session |
| PhantomCombatBackend.java | разные target модели PvE/PvP/raid/acquisition | universal contract не должен приравнивать все действия к attack-living-NPC |

## Утверждения результата037, а не повторный запуск координатором
J1 `7fafb0673c0d9ad87e6b417d3cd3876b61732045`, Jar
`492e5bf953da4a42218a88d8c8049b4f98b7f9c5f3253d29d375d4b8d74f6f4c`.
FinalB8/8,FinalC7/8:35721 cycles→118с useful gap/tail0. Third4/8:253 travel segment
obstructed при части fresh see/move=true;470/584/566 real death/newepochs.
FULL_SAVE B/C8/8; third566 postSQL8294 против sealed8293, producer UNKNOWN.
Все drains healthy; ошибка непрерывности не равна ошибке сохранения.
Контроль035/036: инвентарь252/250 сохраняется; cast/drain подтверждены, не переписывать.

## Где заканчивается независимое чтение
Координатор прочитал публичные исходники и отчёты. Runtime Windows/MariaDB/геодата и
private TASK037-review.zip здесь не выполнялись. Raw-причины проверяет Codex в OWN.
Пакет не заявляет воспроизведение tail357 и не гарантирует любую карту после038.
