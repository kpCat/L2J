# Фактические команды008

Все Git операции разрешены TASK008/direct goal, ограничены source freeze и publication
экспериментальной ветки. Ни одна операция не меняла чужой index/feature branch/history.

```text
git status --short --branch
git rev-parse --show-toplevel
git worktree list
git remote -v
git rev-parse feature/phantom-world
git rev-parse --git-dir --git-common-dir
git show 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25:<task007>/RESULT.md
git show 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25:<task007>/HANDOFF.md
git show 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25:<task007>/candidate-paused/ARTIFACT.md
git worktree add C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius -b experiment/m1-candidate007-observe008 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25
git -c core.autocrlf=false add --pathspec-from-file=L2J_Mobius_CT_2.6_HighFive/.phantom-local/observe008/snapshot-paths.nul --pathspec-file-nul
git diff --cached --stat
git diff --cached --check
git -c core.autocrlf=false commit -m "experiment(phantoms): UNACCEPTED candidate007 / observation008 frozen snapshot"
git rev-parse HEAD
git status --short --untracked-files=no
git show HEAD:L2J_Mobius_CT_2.6_HighFive/<each of103 manifest paths>
git -C <experiment-worktree> diff --name-only HEAD -- L2J_Mobius_CT_2.6_HighFive
git -C <experiment-worktree> remote get-url origin
git -C <experiment-worktree> ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -c core.autocrlf=false diff --check dd58a512c4cb9c6a5318d7320633a35ae849dbf0 -- <task008>
git -c core.autocrlf=false diff --name-only dd58a512c4cb9c6a5318d7320633a35ae849dbf0 -- L2J_Mobius_CT_2.6_HighFive
git -c core.autocrlf=false diff --check 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25 dd58a512c4cb9c6a5318d7320633a35ae849dbf0
git diff --cached --no-ext-diff --no-color -- <task008>/tools <task008>/CONFIG_OVERRIDES.tsv
git diff --cached --numstat
git rev-parse HEAD HEAD^
```

`<task007>` означает
`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIVING-WORLD-CLOSEOUT-007`.
Первый ARTIFACT show по неверному candidate-paused path вернул fatal; правильный
committed ARTIFACT.md прочитан из task007 root в pinned worktree. MAIN input HANDOFF
не использовался как actual pause report.
`<task008>` — `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CANDIDATE007-OBSERVATION-008`.

Snapshot diff --check сообщил whitespace в exact frozen source bytes. Они не
нормализовались: изменение этих bytes запрещено source freeze. Проверка поздних
task008 docs/tooling отдельная; итог — VALIDATION.txt. Это не скрытый whitespace PASS
для исходного кандидата.

Дальнейшая publication использует exact new docs/tooling/evidence paths в NUL pathspec,
`git -c core.autocrlf=false add --pathspec-from-file=... --pathspec-file-nul`,
`git diff --cached --check`, scoped cached diff review, `git commit -m`,
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`,
`git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`.
После commit повторно103 blob hashes и committed scope проверяются read-only.
Final commit subject: `docs(phantoms): record observation008 client transport blocker and cleanup`.

## Build, runtime и клиент

```powershell
Prepare-Observation.ps1 -Phase Freeze -Repository <experiment-worktree>
Prepare-Observation.ps1 -Phase VerifyCommit -Repository <experiment-worktree>
ant -q -Dbuild=.phantom-local/observe008/build jar
Prepare-Observation.ps1 -Phase Runtime -Repository <experiment-worktree>
Start-LocalPlay.ps1 -Background -LoginTimeoutSeconds 60 -GameTimeoutSeconds 480
Check-LocalPlay.ps1
L2.cmd -Action Status
L2.cmd -Action Login
L2.cmd -Action Login -TargetProcessId 14380
L2.cmd -Action Off -TargetProcessId 14380
Get-LocalPlayPilot.ps1
jcmd.exe 328 Thread.print
jcmd.exe 3064 Thread.print
Stop-Observation.ps1 -RuntimeRoot <private-observation-runtime> -UserAuthorizedForce
```

Login повторён в ходе bounded repair одного Native.cs; ForceRestart0/server restart0.
Stop-Observation разрешён отдельным ответом пользователя для exact responsive PID328/3064.
Native.cs exact restored, backup hash verified. Process.CloseMainWindow только client14380,
без force. Computer Use list/window selection и две failed captures; реальные Login PNG
просмотрены view_image. Свежий arm и Pilot gameplay requests не выполнялись.

DB: локальный установленный mariadb.exe/mariadb-dump.exe; параметры сверены по help.
Секрет передавался только в process environment MYSQL_PWD с finally removal.
Export flags `--no-defaults --host=127.0.0.1 --port=3308 --single-transaction
--skip-lock-tables --skip-triggers --skip-routines --skip-events --result-file=<private>/play-snapshot.sql`;
source `l2jmobiush5_localplay3`. Единственный CREATE DATABASE —
`l2jmobiush5_localplay_observe008`; import с `--database=l2jmobiush5_localplay_observe008`.
Read-only SELECT проверяли schema absence, engines, counts, TestAdmin и state ordinals.
PLAY/TEST UPDATE/DELETE/DROP0. Password/INI/dump не опубликованы.

Auto-review отклонил только первый вариант с новым plaintext db-client.ini.
Файл не создан, операция не выполнена. Использован предложенный review вариант
non-persistent secret path; CLI environment pattern подтверждён существующим
Read-M1CloseoutCapacity.ps1. Запрет не обходился сохранением секрета другим способом.

Win32 API для bounded transport read сверены в primary docs:
[SetCursorPos](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setcursorpos),
[SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput),
[MOUSEINPUT](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-mouseinput).
API documentation не является proof фактической доставки input.
