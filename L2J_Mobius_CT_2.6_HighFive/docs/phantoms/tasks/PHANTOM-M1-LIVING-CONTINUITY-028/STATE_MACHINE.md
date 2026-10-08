# State obligations028 (проекция, не второй движок)

| Состояние | Доказательство входа | Обязательный выход / предел | Что НЕ считается успехом |
|---|---|---|---|
| SEEK_TARGET | open exact session, no target, valid farm mission | допустимая цель либо новый local plan, общий debt<=90с | один reason=seeking, повтор same goal |
| APPROACH | принятое native движение к проверенной цели/точке | измеряемое уменьшение расстояния, arrival или typed terminal; existing nav bound | pending request без продвижения, путь через воду |
| COMBAT | актуальный attack/cast/target с native damage | kill/reward→next target; published work completes | AutoPlay=true или старый reason без новых событий |
| LOOT | конкретный законно доступный drop, native pickup | pickup/inventory delta либо точный отказ; затем next target | получение чужого drop, счётчик без изменения native inventory |
| REST | реальный дефицит ресурса, отсутствие опасной угрозы | stock sit/regen/stand; existing45с bound/hysteresis, затем полезное действие | rename idle→REST, постоянное продление deadline |
| REPLAN | exact terminal/goal invalid/нет полезного прогресса | atomic plan publication и dispatch; общий debt не обнулён | новая revision без новых действий |
| DEAD/RETURN | native смерть или exact cold DEAD | existing45с corpse window, controlled return/store, новый owner и новые cycles; до120с для перехода при здоровом runtime | setHp/принудительный doRevive из harness ради PASS |
| SOFT_RETIRE | humanLocal=false, nativeVisible=false, grace истёк | закрытие новых roots, завершение earned, FINALIZE→absence | endless ACTIVE_ACTION self-pin или исчезновение на глазах |
| BACKGROUND | World absence и exact durable state/receipt | lawful simulation/travel; при новом demand native materialization | отсутствие Player без источника состояния |
| REMATERIALIZE | новое фактическое human demand + eligible canonical state | новый epoch, native actions по текущему goal | повторное использование старого owner/epoch |
| SAFETY_BLOCKED | unclassified native failure, pending ambiguity, stale ownership | диагностика+контрактное восстановление либо FAIL | автоматическое снятие guards |

Приоритеты: safety/identity → смерть/recovery → checkpoint lifecycle → locality retire →
ресурсы → текущий native combat/loot → поиск/локальное перемещение/replan.
Использовать текущие приоритеты services, не реализовывать этот список вторым tick-loop.

Для continuity сохраняется enteredAt/deadline через повторные тики. Новый snapshot
не начинает новую REST/APPROACH фазу. Полезность измеряется native событиями и фактами
положения/ресурсов, а не числом вызовов DecisionEngine. PHASE_DEADLINE остается sticky.
