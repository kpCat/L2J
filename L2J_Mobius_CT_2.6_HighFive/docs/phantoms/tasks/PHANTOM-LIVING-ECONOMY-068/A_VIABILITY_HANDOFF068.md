# A: viability по фактической экипировке и ресурсам

Статус: подключённая реализация, isolated javac PASS, два focused DB-free теста PASS, существующий background-model 10/10 PASS. Общая Ant-сборка и настоящая A-сцена остаются у основного исполнителя, который владеет последовательным слотом. Native результат этим документом не заявлен.

## Scope и локальные образцы

Прочитаны TASK/A/T1, ближайшие инструкции, PhantomHistoricalBackgroundPlanner, L2jPhantomBackgroundAuthority/CombatFacts, PhantomBackgroundModel/State, PhantomEngagementPlanner/Controller, PhantomVisibleAutoPlay, existing combat/historical suites и штатные CreatureStat/Creature champion ветки. Повторно использованы прежние формулы encounter/incoming damage, detached Monster scalar projection без spawn, штатные AutoUse skill conditions, native sit/stand и существующий EXHAUSTED/отсутствие целей → replan. Вынужденное соседнее изменение HistoricalBackgroundService обновляет только read-only planning projection: прежний блок использовал сохранённые HP/gear даже для текущего видимого персонажа.

Изменены ровно восемь production-файлов, один test-файл и этот handoff — bounded cohesive exception в десять файлов:

1. `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java`
2. `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java`
3. `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java`
4. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java`
5. `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java`
6. `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`
7. `java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementPlanner.java`
8. `java/org/l2jmobius/gameserver/phantoms/actions/PhantomEngagementController.java`
9. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatViability068Checks.java`
10. Этот документ.

## Поведение и доступные методы для B

`PhantomBackgroundModel.estimateEncounter(CombatFacts, Target)` даёт прежнюю детерминированную оценку без RNG и без сжатия длительности до 20 секунд. `viableEncounter(CombatFacts, Vitals, double requiredMp, Target)` проверяет текущие HP/MP, прогноз входящего урона и прежний максимальный combat budget 120 секунд. Overload с `List<Target> additionalThreats` добавляет прогноз входящего урона существующих нападающих за то же время. Simulation evaluate сохраняет прежние RNG, округление, clamp и расход ресурсов.

`PhantomBackgroundAuthority.PlanningCombat` содержит CombatFacts, Vitals и фактическую стоимость следующего выбранного умения. Производственная authority предоставляет `planningCombat(Player)`, `planningTarget(int npcId)` и `viableTarget(PlanningCombat,int npcId)`. Legacy test authorities сохраняют прежние API/defaults. В производстве отсутствующая/неподдержанная NPC projection не допускает кандидата.

`L2jPhantomBackgroundAuthority.nativePlanningCombat(Player, Creature, Skill)` читает штатные current HP/MP/CP, PAtk/MAtk/PDef/MDef, скорости, реген и native MP cost. Gear/passives/buffs уже включены native getters. `nativePlanningTarget(Creature, boolean remainingHp)` читает native stats конкретной цели; чемпионский HP multiplier учитывается как штатное деление входящего урона, поскольку maxHP само по себе его не отражает. Никаких class/race/уровневых/именных исключений и blanket запрета чемпионов нет. Недамажащий magic debuff не заменяет physical offense в оценке.

Historical planner фильтрует цели до прежнего ранжирования и переходит к прежним lower-level tiers при отсутствии подходящих. На один plan NPC projection кэшируется по ID; глобального stale cache нет. Visible recovery использует текущие native gear/resources, не меняя сохранённый transaction baseline. Native engagement и stock PhantomPolicy проверяют ту же оценку; дополнительные уже нападающие учитываются в пределах существующего combat policy maximumObservedAttackers. Sweep/corpse path сохранён, valuable spoil не фильтруется.

Под текущим writer native ATTACK intention заканчивается при непригодной цели через штатный IDLE; captured hit/cast completion не отменяется. Existing rest остаётся ограничен 45 секундами и расходует native regen; необходимая MP теперь определяется реальными AutoUse умениями также для не-магов. Engagement budget обновляется native kill witness вместо каждого damage sequence. Отсутствие пригодной цели и EXHAUSTED продолжают вызывать прежний replan/failure memory.

## Проверки и команды

Рабочий каталог команд: `D:/Codex/repos/L2J-phantom-world`.

Первое `javac` нового focused test против исходного build-dev JAR завершилось exit 1: отсутствовали новые estimate/viable API. Это feature-first compile absence, не product RED. Диагностика: `.phantom-local/task068/reports/viability068-feature-first.log`.

Затем выполнен `javac -cp ".phantom-local/task068/build-dev/dist/libs/GameServer.jar;.phantom-local/task068/build-dev/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*" -d .phantom-local/task068/viability-bin` с девятью Java-файлами из exact списка выше. Exit 0, лог `.phantom-local/task068/reports/viability068-javac.log`. Полная компиляция всех девяти файлов выполнена одним вызовом, зависимости source-path автоматически не подхватывались.

Focused команда:

```powershell
java -cp '.phantom-local/task068/viability-bin;.phantom-local/task068/build-dev/dist/libs/GameServer.jar;.phantom-local/task068/build-dev/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomCombatViability068Checks
```

Exit 0, `OWN068_COMBAT_VIABILITY_PASS cases=2; DB_WRITES=0`; сохранён `.phantom-local/task068/reports/viability068-focused.log`. Параметры: melee/ranged/magic, слабая/сильная экипировка, текущие HP и MP, доступное обычное нападение без MP, пригодные усиленные цели, опасная усиленная цель, длительность более 120 секунд, дополнительный нападающий.

Регрессионная команда:

```powershell
java '-Dphantom.module.root=D:/Codex/repos/L2J-phantom-world/L2J_Mobius_CT_2.6_HighFive' '-Dphantom.test.reports=D:/Codex/repos/L2J-phantom-world/.phantom-local/task068/reports/viability-model' -cp '.phantom-local/task068/viability-bin;.phantom-local/task068/build-dev/dist/libs/GameServer.jar;.phantom-local/task068/build-dev/phantom-test/bin;L2J_Mobius_CT_2.6_HighFive/dist/libs/*' org.l2jmobius.tests.phantoms.PhantomTestLauncher background-model 15001501
```

Exit 0: total 10, passed 10, failed 0. XML/text отчёты в `.phantom-local/task068/reports/viability-model/`. Это pure MODEL mode; DB/runtime не запускались.

Регистрация основным исполнителем в shared launcher:

```java
case "combat-viability068" -> new PhantomCombatViability068Checks();
```

Shared launcher и другие общие source-файлы помощник не менял. Git-команды в этом блоке не использовались.

## Pending native и ограничения

Подготовлен `PhantomCombatViability068Checks.nativeProjection(Player,Creature)` для вызова из общей own068 A-сцены под её native lease. Он требует exact own068 флаг, access0, отсутствие invul; читает фактические gear/HP/MP/known AutoUse skill, сравнивает projection со штатными getters и возвращает scalar receipt. Вызвать до/после настоящей смены экипировки, при native MP depletion и для реальных NORMAL/BLUE/RED targets. Сам метод не выдаёт предметы/HP/MP/урон и не делает World/DB writes.

Остаются Ant jar/compile-tests, integrated focused runner, фактический farm/replan/death/rest контроль в общей A-сцене и сравнение viable/unviable native loadout/target pairs. Подготовленный reader не доказывает ordinary kills, loot, income, отсутствие death-loop или успешный route. Этого до native запуска не заявляем.

Оценка остаётся прежней приближённой моделью, консервативной подсказкой выбора, а не retail combat engine. Она не моделирует точное число casts, skill power, critical/miss и неизвестные NPC active skill costs. Проверяется стоимость следующего реально доступного умения; последующие действия повторно оценивают текущие MP/loadout. Никакой прогноз бесплатных shots, лечения или регена не кредитуется. Geo/pathfinding/topology/search limits, таймауты, rates, supply budgeting, loot disposition, historical runtimes и protected DB не изменялись.

Mojibake-маркеры в изменённых файлах проверены отдельной полной проверкой.

Escaped Cyrillic в изменённых файлах проверены отдельной проверкой шести паттернов.
