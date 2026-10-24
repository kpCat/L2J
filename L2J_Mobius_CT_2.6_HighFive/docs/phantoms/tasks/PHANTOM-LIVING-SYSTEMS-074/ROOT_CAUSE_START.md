# ROOT_CAUSE_START074 — известные наблюдения против гипотез

Это входной технический журнал **не готовый диагноз**; не подменять причины догадками.

## A — оплаченные D Soulshot не куплены

- TASK073: actor TEST073 `task073supply/Supply073` profile1, lvl20, 35 native kills; перед решением wallet **1747A** (=100 fixture +1647 native). Реальный `PhantomAutonomousSupplyPolicy` позволил **62× item1463 (D Soulshot) по21A**, `availableBudget=1311`, `count=62`, `useful=true`. Следовательно, последний блокер **НЕ** нехватка денег.
- `L2jPhantomSupplyJourney` создал `commerce.buy`, но ни `COMMITTED`, ни debit/credit: `supplies.navigation.requested → supplies.navigation.local_checkpoint_retry → cooldown → ...`. Его вызывают `navigate/acceptRoute`, trip `localAttempt` после `NO_PATH`, `ROUTE_OBSTRUCTED` или `ROUTE_BUDGET_EXCEEDED`, если нужно разбить путь на локальные этапы. Unknown: первичный status, exact Geo obstruction, request destination и фактический mutable actor. Записать эти поля для одного доказательного воспроизведения, не добавляя лог каждую итерацию.
- Исходная pre-birth сцена Tyra30420 + mobs20038/20418 в районе(-64k,+73k) имела FOUND оба исходных WALK-коридора; **это не гарантирует** годность следующего маршрута после35 kills и переноса Player в другую точку.
- После native test cleanup отдельный Combat owner перестал поддерживать actor, тот погиб, был штатно respawn в town; последующая recovery helper имела TEST-side проблему `walkSurface(NPC center)`, затем drift roaming21801. Эти два сбоя теста не разрешают заявить дефект commerce как доказанный и не заменяют законный flow после смерти.
- Не тратить повторную сессию на 35-kill earn-to-budget: TEST074 сцену подобрать с достаточным **явно обозначенным** prior wallet + несколько native earning events; в отчёте разделить исторический, fixture и измеренный заработок. При необходимости доступный earned-only контроль — отдельная короткая сцена, а не обязательный многочасовой гринд.

## B — нет дальнего fallback после local no-target

- В072 `PhantomHistoricalBackgroundPlanner` имеет level radius +2 и native reward guard, так что ложное исключение +1/+2 мобов устранено. Но `PhantomHistoricalBackgroundService` (~1324) использует `replanVisibleLocal`; этот planner выбирает target/anchors из ближней зоны ~2км. У него есть global `plan/replan`, но **не доказан** safe owner bridge с live background при exhaustion; нужно адресно встроить fallback только после освобождения предыдущих claims и с сохранением earn/ownership.
- `SEARCH_LIMIT` в native `L2jNavigationBackend` даёт `ROUTE_BUDGET_EXCEEDED` — не «вся карта непроходима».

## C — неутверждённый дроп

- В073 17 kill результатов `VICTORY_LOOT_BLOCKED`, но всё равно есть earned EXP/SP/Adena и сохранённые реальные item stacks. `PhantomCombatService.lootResult()` возвращает BLOCKED, когда есть remembered candidates, но ни actor, ни party не зафиксированы как приобретатель. Нужно диагностицировать один legal outstanding item и одно negative. Нет права принудительно выдавать этот item ради PASS.

## D — PvP retreat до боя

- TASK072: 192 bounded samples,3 legal field locations, `NO_ROUTE_WITHIN_BUDGET`×2, `SEARCH_LIMIT`×1; PvP противник НЕ создавался. `PhantomPvpRetreatCoordinator.start()` требует route к RESPAWN или CITY_CENTER, что может быть далеко. Прежде чем моделировать бой, проверить факт хотя бы одного законного короткого tactical escape/corridor, затем native opponent interaction. Такой safe staging путь НЕ равен телепорту в мирную зону.

## Read-first критерий

За первые15–20мин создать в `docs/phantoms/reports/IMPLEMENTATION074.md` таблицу A–D: существующий owner, подтверждённая причина/ещё гипотеза, предельный diff, способ проверить. Затем **писать игровой код**, а не копировать новые тестовые framework. Не требовать owner согласования на отдельный `A→B` шаг: authority уже дана этой задачей.
