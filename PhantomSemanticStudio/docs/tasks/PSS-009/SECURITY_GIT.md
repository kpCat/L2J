# PSS-009 — строгая изоляция, файлы, Git

- Единственный кодовый repo: `C:\Users\ZBook\PhantomSemanticStudio\`, единственный origin `https://github.com/kpCat/PhantomSemanticStudio.git`, required main base `cddf9a65ffc5b052c110b264c4fe3973f8e6a37c`.
- `C:\Users\ZBook\L2J_Mobius\` и весь High Five **READ_ONLY**. Нельзя менять/создавать/удалять файлы, stage XML/manifest/custom/Java/build/server/DB/client/geodata/temp или делать Git в L2J. Не запускать Ant/Java/server внутри L2J. UI layout fix не требует чтения игровых данных.
- Не открывать пользовательский `%LOCALAPPDATA%\PhantomSemanticStudio\workspace` для записи в тестах. STA sandbox только `artifacts/PSS-009/**` или отдельный synthetic temp с process-only override; проверка disjoint; сохранность user session/approval.
- Реальные `chat.zip`, SQLite/corpus/labs/history, личные сообщения, raw prompts/tokens/screenshot с local paths, секреты и бинарники — не коммитить, не помещать в Git отчёты, не передавать модели. Task ZIP содержит только инструкции.
- Не менять LM client, model identity, generation JSON/approval, XML stager/handoff/Java proofs. Safety gates PSS001–008 сохранять, тесты/отрицательные проверки не отключать.
- `AGENTS.md` применяется. Проверить `.gitignore`; `reports` только безопасные synthetic scalars/logs и агрегаты. `artifacts` игнорируется.
- Read-first git: `git rev-parse --show-toplevel`, `git rev-parse HEAD`, `git status --porcelain=v1 -uall`, `git remote -v`, `git ls-remote origin refs/heads/main`; если remote != required base — BLOCKED_REMOTE, не перезаписывать.
- Сохранять любые новые локальные изменения пользователя, task package byte-exact. Scoped git add **по точному `reports/PSS-009-owned-files.txt`**, затем `git diff --cached --check`, `git diff --cached --name-only`, `git diff --cached --stat`. На конфликте остановиться.
- Разрешённые мутации: обычный `git commit -m "fix(pss): restore WinForms tab layouts and DPI reachability"` и `git push origin HEAD:refs/heads/main` только Studio; промежуточные ordinary commits по scope допустимы. No force/reset/clean/stash/rebase/amend/branch checkout/git add .
- Публикация обязательна и в GREEN, и в BLOCKED (если безопасный checkpoint); в отчёте exact commit SHA, parent/base, remote equality, clean or explicit remaining dirt. Если origin временно недоступен — честный `PUSH_BLOCKED`, без альтернативного remote.
- **Никаких review ZIP** от Codex; ревью ассистента только по публичному exact GitHub commit/diff/reports.
