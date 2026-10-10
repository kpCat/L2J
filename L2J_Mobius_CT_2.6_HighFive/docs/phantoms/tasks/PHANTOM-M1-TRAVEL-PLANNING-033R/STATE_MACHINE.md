# State machine одной операции перемещения

PLAN → WALK_CORRIDOR → ARRIVAL_VERIFY → FARM
PLAN → RETURN_CAST → RETURN_ARRIVAL_VERIFY → APPROACH_GK → GK_REQUEST
     → GK_ARRIVAL_VERIFY → WALK_CORRIDOR → ARRIVAL_VERIFY → FARM

Из WALK_CORRIDOR:
- transient queue busy → bounded WAIT до прежнего deadline;
- new obstacle → revoke affected edge → REPLAN в том же absolute budget;
- final blocked/repeated stuck → TRANSPORT_ALTERNATIVE или GOAL_REPLAN;
- loss of locality → existing SOFT_RETURN (не новый teleport);
- death/cleanup/epoch change → прекращение новых действий, existing lifecycle.

PLANNED≠EXECUTED; request ACCEPTED≠ARRIVED; intermediate leg≠final arrival.
Никакая контрольная точка не даёт EXP, предмет или fake saved position.
Atomic ownership важнее доступности пути. Задержанный ответ для старой goal/epoch
не двигает нового Player и не списывает scroll/GK fee.

План не обязан быть абсолютно оптимальным и вечным. Он обязан быть явным,
проверяемым на текущих данных и иметь конечный исход при изменении обстановки.
Нельзя постоянно стоять с одинаковым REPLAN и считать это разумным поведением.
