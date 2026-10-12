# GIT039 — разрешённые операции и публикация

Основание — пользовательский /goal039. Старый STOP038 не запрещает согласованную039
работу, но безопасность Git сохраняется. MAIN не изменять и ветки в нём не переключать.
OWN: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
Initial HEAD=remote=8bf4df23a23854b9ff57c13b83a8ab34f07aa4c2. Иначе BLOCKED_BASE,
без автоматического reset/checkout над чужим diff. Existing worktree используется как есть.

Read: status, rev-parse, diff, show, log, ls-remote, ls-files, check-ignore,
worktree list, merge-base --is-ancestor, точные file/tree/commit reads.
Write: только source/test/docs из SOURCE_MAP и собственная task-family039.
До stage сохранить literal allowlist, проверить diff и protected files.

```powershell
# $own и $paths определены по проверенной identity. $paths — literal список, не wildcard.
git -C $own add -- $paths
git -C $own -c core.whitespace=cr-at-eol diff --cached --check
git -C $own diff --cached --name-only
git -C $own commit -m 'TASK039: <фактический проверенный блок>'
git -C $own push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C $own rev-parse HEAD
git -C $own ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Сохранить реальные argv и UTC. Конфликт normal push: оставить локальный commit/evidence,
вернуть BLOCKED_PUBLISH; не force и не merge чужой истории. Source freeze commit
публикуется до сборки. Final docs commit может иметь другой SHA при тех же code blobs.

Запрещены add., add-A, wildcard stage, reset, clean, stash, rebase, amend, restore,
prune, force add/push, создание или удаление веток/worktrees, merge feature/main,
изменение глобального Git config. Собственный новый patch можно адресно исправлять
редактором с сохранением diff/evidence, но не возвращать заодно чужие файлы.

Сохранить UTF-8, исходные BOM/EOL. Mojibake и escaped Cyrillic проверять отдельно,
без массового форматирования. Не stage bins/Jars/raw SQL/secrets/архивы.
Число собственных документов task-family не является разрешением расширить production scope.

Один финальный RESULT_HANDOFF, MORNING, EVIDENCE_INDEX, CLEANUP и GIT_COMMANDS.
Final SHA связать внешним publication receipt после push, без бесконечной цепочки
коммитов «подтверждаю предыдущий коммит».
