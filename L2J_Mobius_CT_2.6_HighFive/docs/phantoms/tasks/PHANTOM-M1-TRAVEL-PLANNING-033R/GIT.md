# Git — явное разрешение пользователя при отправке GOAL

Разрешены read-first `status`, `rev-parse`, `ls-remote`, bounded `diff/log/show`,
`fetch origin experiment/m1-candidate007-observe008` и создание собственного detached worktree от `df71140e659670e2e3d225605f64689a6d32d281`.
Нормальный `push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` и exact-path add/commit результата
обязательны для GREEN/PARTIAL/BLOCKED/FAILED. Branch может быть занят другим
worktree: detached + обычный refspec push не требует менять тот checkout.

Запрещены git add ., add -A, reset/clean/stash/rebase/force/amend, чужой restore,
переключение main и незаявленный merge. При remote drift — не переписывать ветку:
сохранить local own результат и report BLOCKED_REMOTE_DRIFT.

Финальный allowlist составляется из реально изменённых SOURCE_MAP paths и этой
папки. Проверить staged names/whitespace/encoding. UTF-8; preserve existing per-file
EOL. Сырой SQL/JFR/credentials/account dumps не публиковать, только hashes и reviewed
scalar evidence. Команды записывать в GIT_COMMANDS.md с основанием разрешения.

В конце SHA local=remote и source/JAR identity. Не писать «остановился по указанию
пользователя», если причина scope/time boundary; STOP_AUTHORITY=TASK033R_CONTRACT.
