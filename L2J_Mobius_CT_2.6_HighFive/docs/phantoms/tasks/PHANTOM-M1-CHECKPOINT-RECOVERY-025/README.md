# TASK025 — завершение checkpoint и восстановление принадлежащего ему store

Required base: `07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6`.
Branch: `experiment/m1-candidate007-observe008`.
Frozen code TASK024: `ca3cbef9c03b695dcb9f84a734c5535b4ac72027`.

Цель: после временного checkpoint/arrival/restart персонаж либо продолжает native-жизнь,
либо имеет конкретную исполняемую recovery-операцию, либо явно и обоснованно изолирован.
Нельзя оставлять `ACTIVE + SEALED + no pending receipt + no incident + start_retry` навсегда.

Это НЕ повтор всех A–D TASK024 и НЕ новый combat engine. Его исправления сохраняются.
Три связанные части: холодный owned-store recovery до baseline; конечный live checkpoint;
исполнение заработанных callbacks при checkpoint/drain. Они сходятся в одном жизненном цикле.

Порядок чтения: GOAL → ROOT_CAUSES → DESIGN → PLAN → PATCH_GUIDE → SCENARIOS → RUNBOOK.
Не перечитывать весь архив задач001–024. Разрешённые пути — SOURCE_MAP.tsv.
Автономно, без клиентского login gate. M1 нельзя объявить закрытым без финального REAL gate.

proposals/ содержит исполняемую таблицу решений и проверку контрактов, а не готовый
интегрированный patch. source-excerpts/ — проверенные фрагменты указанного base.
Сначала RED на реальных production-компонентах, потом интеграция. Проверка таблицы —
только проверка пакета; она не заменяет native/runtime tests.
