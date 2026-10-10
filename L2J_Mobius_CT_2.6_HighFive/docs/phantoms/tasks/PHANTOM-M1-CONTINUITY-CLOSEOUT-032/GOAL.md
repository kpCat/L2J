# GOAL032

Required base: `df6ee99c71a4c8b07ad05545b200e113d3b0d3c9`.
Branch: `experiment/m1-candidate007-observe008`.
Model/reasoning: **GPT-6.1 Sol / Very High**, без субагентов, новый диалог.

Выполнить TASK/DESIGN/PLAN/SOURCE_MAP/SCENARIOS/ACCEPTANCE.
Пользователь просит рабочую видимую жизнь, а не ещё один диагностический отчёт.

Главная boundary: на последнем runtime031 группа6/8. Profile43 теряет visible-сессию
через `_visibleSuitable=false`; первый конкретный guard UNKNOWN. У117 есть18cycles,
но отдельный useful-progress debt. Ни одну причину не считать установленной заранее.

Разрешены диагностика и несколько связанных исправлений после собственных RED,
включая переход guard→replan→resume и сохранение уже работающего rest/cooperative path.
Не останавливаться после первого in-scope subreason или успешного targeted suite.
После этого продолжить A/B continuity, away/background/return, lineage/restart и
оба crash windows на одном frozen кандидате, если prerequisites выполнены.

Autonomous Synthetic; не просить «в игре», «вышел», arm или подтверждение этапов.
Main/PLAY/foreign/kpCat не менять. Свои isolated worktree/clones — по RUNBOOK.

Ориентир4–6ч, максимум480мин от START_UTC. Semantic freeze на360-й минуте.
Последние30мин — обязательный cleanup/publication. Нет нового лимита числа fixes.
При отсутствии новой проверяемой информации60мин — сменить метод диагностики внутри
scope, не повторять тот же запуск; если следующий осмысленный шаг отсутствует —
безопасно закончить с точной boundary, а не расходовать весь потолок.

Exact-path commit + normal push результата обязательны при любом исходе.
Все свои JVM остановить; healthy cleanup и вынужденный stop различать.
Максимальный статус без клиента `M1=WAITING_REAL_FINAL`, только если ВСЕ server gates
закрыты. Иначе `M1=OPEN`. `REAL_FINAL=NOT_RUN`. Следующую задачу не начинать.
