# PHANTOM-DISK-CLEANUP-001 — план безопасной очистки

Дата: 2026-11-12. Работа разрешена владельцем автономно; неоднозначный игровой код не активировать и не удалять. Operational-план выполнен; факт финальной доставки фиксируется отдельным publication receipt.

Цель: реально освободить место, сохранив уникальные разработки и доказательства. Четыре отчёта — единственный разрешённый scope изменений в основном checkout. Диагностические JSON, скрипты и компактные сохранения находятся вне checkout и не публикуются.

Прочитаны: корневые AGENTS.md и README.md; PHANTOM_CURRENT_STATUS.md; CODEX_WORKFLOW_CONTRACT.md; PHANTOM-REPO-CONSOLIDATION-001/TASK.md и CLEANUP_POLICY.md; LEARNING-005/CONSOLIDATION.md. Task package cleanup до начала отсутствовал; code-map не найден. Локальный паттерн: per-folder inventory → compact preservation → SHA verification → exact allowlist → native worktree removal → postflight.

- [x] Зафиксировать исходный HEAD, remote и свободное место, обновить origin refs.
- [x] Найти чат LEARNING-006, его клон и результаты; защитить текущий чат и остальные активные окружения.
- [x] Измерить logical bytes и оценку allocation, найти Git-корни без обхода reparse points; точную освобождённую ёмкость измерять средствами ОС.
- [x] Проверить HEAD/refs/ancestry, staged/unstaged/unmerged/untracked/ignored и процессы для каждого кандидата.
- [x] Сохранить D-worktrees/migration057/006/gear125 и C-overlay компактно, проверить SHA-256 и восстановление. C-overlay:2523876 files,77608 Git streams,224336 stored mappings VERIFIED.
- [x] Удалить доказанно ненужное по exact allowlist:18 targets. Registered worktrees — только git worktree remove без --force; dirty109 сохранён.
- [x] Проверить отсутствие каталогов, metadata, сохранённые SHA и измерить C:/D: повторно;00627751/27751 и protected207/207 неизменны.
- [x] Завершить INVENTORY.md, COMMANDS.md и RESULT.md. Последние encoding/diff checks — обязательная pre-commit команда COMMANDS; receipt фиксирует фактический результат.

Финальная доставка: только эти4 Markdown-файла, ordinary non-force push в feature/phantom-world, независимый GitHub commit/files readback. Product code не добавляется;006 WIP не сливается. Точный outgoing SHA и результаты доставки сохраняются после push в `D:\Codex\_PRESERVE\PHANTOM-DISK-CLEANUP-001\PUBLICATION.json` и финальном ответе (receipt не включается в собственный commit).

Ограничения: не менять master, историю, чужой dirt, protected release057–061, SQL/MAIN/PLAY, клиент, общую MariaDB; не запускать серверы или сборки без необходимости. LEARNING-006 завершила turn с PARTIAL и manual gate REQUIRED; её WIP не считается принятой игровой функциональностью. Клон, TEST и визуализации006 сохраняются. Активные ASU/Rust-чаты сохраняются. Настройки, credentials, sessions, archived_sessions и state SQLite не изменяются.

Проверка риска перед каждым удалением: абсолютный путь внутри разрешённого корня; ни один компонент не reparse; нет активного владельца/процесса; Git-история сохранена; все незакоммиченные/ignored уникальные данные сохранены либо кандидат оставлен. Неизвестность означает PRESERVED/BLOCKED для конкретного каталога, остальные действия продолжаются.

Проверки operational: Git readback, SHA-256 компактного сохранения, exact path absence, filesystem free-space deltas. Новые продуктовые тесты не нужны: production-код не меняется. Статусы VERIFIED, CLEANED, PRESERVED и BLOCKED отражаются раздельно. FULL за инвентаризацию запрещён.

Дополнительное указание владельца:006 завершилась PARTIAL; её clone/TEST SQL/checkpoints/.phantom-local и связанные уникальные данные нужны для продолжения. Они исключены из удаления. Выполнена дополнительная резервная копия не-Git окружения006, файловый restore smoke и postflight SHA27751/27751, protected207/207, HEAD5a38d351… неизменён. GitHub не является заменой backup окружения.

Удаление шести exact D-путей отдельно одобрено владельцем после автоматического rejection и дополнительного restore smoke. Оно завершено; общий Git metadata сохранил только main и dirty109. Остальная очистка проводится только после завершения и проверки соответствующего overlay.
