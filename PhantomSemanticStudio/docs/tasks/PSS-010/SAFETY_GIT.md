# Безопасность и Git

1. Директория приложения `C:\Users\ZBook\PhantomSemanticStudio\` — единственный git root, разрешённый для изменений.
2. Весь `C:\Users\ZBook\L2J_Mobius\` строго read-only. Никаких `git`, `ant`, `java`, shell scripts, вносящих изменения, клиентских/серверных тестов и temp в High Five.
3. `chat.zip`/corpus SQLite/все private-input/локальные workspace/proposals/attestations/keys/model bodies/tokens не коммитить и не выводить в GitHub отчётах. Новые тесты содержат только синтетические данные.
4. Existing review-only JSON export, v3 stage, Java proof, HMAC, approvals остаются без изменения.
5. Не запускать `New-ReviewBundle.ps1`; не создавать review ZIP, source ZIP. Архив пользователя содержит только задачу и не является результатом ревью.
6. Перед работой read-only: `git rev-parse --show-toplevel`, `HEAD`, `git status --porcelain=v1 -uall`, `git remote get-url origin`, `git remote get-url --push origin`, `git ls-remote origin refs/heads/main`. При дрейфе удалённой ветки — `BLOCKED_REMOTE`, без обхода.
7. User dirt: `PhantomSemanticStudio.sln` была изменена пользователем до PSS-009 и защищена. Проверить её текущее содержимое, сохранить byte-for-byte, не добавлять в commit; аналогично любой другой user dirt.
8. После RED/GREEN exact `owned-files` и `git diff --check`; `git add -- <exact paths>`; обычный commit; только `git push origin HEAD:refs/heads/main` без force. Проверить локальный/удалённый/публичный SHA. Не использовать `git add .`, `reset`, `clean`, `stash`, `rebase`, `merge`, `amend`, `checkout`, `force`.
9. В случае BLOCKED/FAILED сохранить безопасные отчёты/коммит/разрешённый push только Studio, не выдавать `NOT_RUN` за PASS.
