# PHANTOM-M1-LIFETIME-DRAIN-034

Сервер High Five, Phantom World, **TASK034: точное происхождение retained entries, причинное исправление, естественное освобождение lifetime**.

Required Git base: `44139ca556ad3d5ac35309d5fe222269d3ada256` (remote `experiment/m1-candidate007-observe008`).
Последний TASK033R: M1=OPEN, маршрут obstacle→native arrival→>=5 cycles подтверждён, global stop: **retained3**. При этом все шесть первоначальных участников последней сцены отдельно имели DETACHED/outstanding=0/pendingTimers=0. Нельзя объявлять retained3 ошибкой этих шести без идентификации.

Читай `GOAL.md` → `TASK.md` → `HANDOFF033R.md` → `DESIGN.md` → `PLAN.md` → `SOURCE_MAP.tsv` → `SCENARIOS.md` → `RUNBOOK.md` → `ACCEPTANCE.md` → `GIT.md` и далее фактические исходники/тесты. Нет приложенных production-патчей; поиск первой причины обязателен.

Ориентир 2–4 часа, максимум 5 часов от фактического запуска. Самостоятельная работа без субагентов. Запускать только в собственном рабочем каталоге, не трогая main/PLAY/чужие worktrees.

Важно: задача после распаковки находится в ОСНОВНОМ checkout пользователя, который READ-ONLY. Новый Git worktree создан из remote commit и НЕ наследует эти untracked task-файлы. До исполнения скопируй ровно 10 task-файлов+manifest по SHA в соответствующую папку own worktree, не через git и без иных файлов; исходник main не изменяй.
