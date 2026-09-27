# IDLE_ROOT_CAUSE

Не доказано — `M1_SELECTED_TRACE_DISABLED`.

Привязанный REAL_LOGIN TestAdmin находился в (-90825,248162,-3568), instance 0. Pilot выбрал естественный admitted profile 6 в (-90875,248162,-3568), на расстоянии 50 единиц, но в 15:44:44Z и 15:45:36Z сообщил `materialized=false`. Это не выборка материализованного idle phantom, и она не устанавливает причину отсутствия gameplay.

Effective конфигурация LocalPlay сообщает `Diagnostics=False`. В этом режиме `PhantomSystem` создаёт выключенный `PhantomSelectedDecisionTrace` и не передаёт его в `PhantomDecisionEngine`. Существующая команда выбора трассы доступна только GM; TestAdmin намеренно non-GM. В Pilot нет trace operation. Поэтому Goal, candidate/rejection, plan, handler, result и reason обычного видимого idle phantom не захвачены.

Статический код показывает: `farm.background` возвращает `REPLAN recovery.not_dead` для живого WARM/ACTIVE профиля; `PhantomDecisionEngine` также может сообщить `NO_GOAL` или `NO_CANDIDATE`. Ни одна причина не отнесена к живому профилю. Visible-life regression и production fix не выполнялись. Native autonomous action не доказан.
