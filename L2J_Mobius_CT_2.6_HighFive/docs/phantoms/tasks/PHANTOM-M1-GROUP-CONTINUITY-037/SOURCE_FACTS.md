# SOURCE_FACTS037 — независимый read-first координатора

Чтение выполнено по pinned BASE, не по default branch. Точное время/локальный runtime036
здесь не повторялись; приватный архив недоступен координатору. Корневые причины пяти
FAIL остаются неизвестными до проверки raw. Следующее — установленные свойства кода.

1. `continuity028.py` (git blob `c441ecc621dc5936ba3b2ea7f8e9f6e6f74eb164`):
   для present требуется worldPresent=true И dead=false; любой false завершает segment.
   смена native epoch тоже начинает segment. `absent or len(segments)!=1` порождает
   COMPLETE_LINEAGE_REQUIRED. SQL этот скрипт вообще не читает. Полный save и этот FAIL
   логически совместимы. Исправлять persistence из одного этого маркера запрещено.
2. Evaluator требует:4–8 ID, ровно 2 primaries,360–420 с, первого/последнего frame в 5 с от
   краёв, sample gap<=5 с, подходящую observer identity; complete cycle/damage/kill/reward/
   target delta>=5 primaries и>=2 остальным в ОДНОМ segment; positive EXP и SP; useful gap<=90 с;
   reward в последних 120 с; resource-rest<=45 с; отсутствие native invalid/pending/cleanup.
   CLI требует kind=Scene, telemetryMailboxCommands=0,sameSession=true. Два START/STOP
   control commands — не обязательно два telemetry commands. Не смешивать эти счётчики.
3. `PhantomBackgroundDecision.startVisible/awaitVisible`: unsuitable→stop→REPLAN.
   retry сам по себе не свидетельствует работы: нужно дойти до последующего start и native progress.
4. `PhantomHistoricalBackgroundService.visibleFarmReady`: уже записывает точный реальный
   predicate `VISIBLE_FARM_READINESS`, причины cooldown/protocol/route/target/stored_goal.
   Не писать второй collector вместо чтения имеющегося. `prepareVisibleDecision` вне
   handler locks выполняет stopped/quiet→replacePlan→handoff rebind→decision.reload.
5. `PhantomVisibleAutoPlay` хранит exact goal/revision/epoch policy и обе регистрации.
   noTargetExpired, ресурсный отдых и native-settlement pause не являются combat engine.
   Долг полезного прогресса нельзя обновлять сменой session/goal, лечением или snapshot.
6. `PlayerNativeEvidence` различает причины OWNER_FAILURE/PHASE_DEADLINE/TARGET_CAP/
   COMBAT_ACCOUNTING/прочие. Sticky unproven нельзя сбросить ради PASS. Snapshot проверяет
   горизонты phases; timeout — следствие, сначала найти начало/конец и реальные writers.
   Completed cast-launch не равен completed farm cycle. Cycle требует damaged+killed+
   EXP+SP+nextSelected, в том числе при законной cooperative атаке.
7. `PhantomHistoricalBackgroundPlanner` уже имеет replanVisibleLocal/remainsVisibleSuitable
   и factual area distance. `PhantomVisibleFarmTravel` использует NavigationService и 033R
   corridor planner. Использовать их: не внедрять второй поиск пути или GK проект.

Current handoff evidence036 — см. HANDOFF036. Файлы и Git blobs — SOURCE_MAP.tsv.
Разделение выводов: SOURCE_CONFIRMED; RAW036_REPLAYED (только после реального анализа);
CURRENT_RUNTIME_PROVEN; HYPOTHESIS. В отчёте не смешивать эти статусы.
