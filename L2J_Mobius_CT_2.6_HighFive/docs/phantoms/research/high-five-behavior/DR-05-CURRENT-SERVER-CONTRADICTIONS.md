# DR-05 — current-server contradictions

Текущие расхождения зафиксированы, но не исправляются в Goal 013:

- `Servitor` прямо зеркалит боевые attributes владельца в текущей реализации; это расходится с внешней формулой распределения 20/80.
- Olympiad path удаляет pet, но сохраняет servitor; единое внешнее утверждение «все summons запрещены» текущим кодом не подтверждается.
- Summon death передаёт hate владельцу.
- `Servitor Barrier` снимается любым действием, кроме move.
- `Mutual Response` применяется к servitor, а не ко всем controlled actors.
- `Summon Friend` ограничивается текущими server checks и не означает готовность без target/resource/condition.
- Pet имеет inventory/pickup, servitor — нет.

Эти факты помечаются как `CURRENT_SERVER_IMPLEMENTATION`. Attribute formula, Olympiad policy и summon command behavior остаются вне scope.

Authority: `CURRENT_SERVER_IMPLEMENTATION` и `DISPUTED`. Confidence: `HIGH` для текущего кода, `LOW` для неподтверждённого внешнего `20/80`.
