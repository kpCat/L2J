# TASK036 — завершение native cast и безопасное освобождение

**Новый чат Codex, GPT-6.1 Sol / Very High, один исполнитель, без субагентов.**
Base: `c0e86fa2950012ae2ee898b841524fa160869a77`; branch: `experiment/m1-candidate007-observe008`.
Переиспользовать `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.

Пользователь спит. Запуск не требует REAL_LOGIN, arm, входа в клиент или разрешения между этапами.
Цель — текущий путь `native cast → earned continuation → завершение → owned store → release`,
а не доказательство заранее выбранной версии исторической ошибки27.

Читать в порядке: GOAL → TASK → HANDOFF → SOURCE_FACTS → DESIGN/PATCH_GUIDE →
PLAN/SCENARIOS/ACCEPTANCE → RUNBOOK/GIT. SOURCE_MAP — точная граница правок.
Пакет содержит только task-файлы и образец диагностического кода; распаковка не меняет production.
Архив распаковывается в `C:\Users\ZBook\L2J_Mobius\`, затем только файлы пакета
копируются в такую же относительную папку OWN по MANIFEST. Не переимпортировать старые пакеты.

Ориентир 2–4 часа, максимум 6 часов от фактического старта. Завершать раньше при достигнутом результате.
`M1=OPEN; REAL_FINAL=NOT_RUN` при любом исходе этой ограниченной задачи.
