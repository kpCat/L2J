# Фактические команды task009

Git разрешён прямым запросом пользователя на exact verification и publication,
локальным AGENTS.md и TASK009. MAIN проверялся только для inventory; чужие правки
не менялись. Далее Git исполняется в существующем task008 worktree.

```text
git status --short
git branch --show-current
git rev-parse HEAD
git worktree list
git status --short --untracked-files=no
git rev-parse @{upstream}
git merge-base --is-ancestor dd58a512c4cb9c6a5318d7320633a35ae849dbf0 HEAD
git remote get-url origin
```

Upstream не настроен; rev-parse @{upstream} вернул fatal. Explicit normal push
на заданную branch не требует создания/переключения ветки.

Runtime/UI/read-only диагностика:

```text
Get-FileHash (103 candidate source entries, source manifest, runtime JAR, l2.ini)
Get-FileHash (304 original-preservation entries)
Check-LocalPlay.ps1
Start-LocalPlay.ps1 -Background -LoginTimeoutSeconds 60 -GameTimeoutSeconds 120
Get-LocalPlayPilot.ps1
Prepare-LocalPlayPilot.ps1 -ExpectedName TestAdmin -ArmMinutes 10
L2.cmd -Action Status -TargetProcessId 5696 -TimeoutSeconds 30
L2.cmd -Action Login -TargetProcessId 5696 -TimeoutSeconds 30
L2.cmd -Action Off -TargetProcessId 5696 -TimeoutSeconds 30
L2.cmd -Action Status -TargetProcessId 15116 -TimeoutSeconds 30
L2.cmd -Action Arm -ArmCode <fresh-private> -TargetProcessId 15116 -TimeoutSeconds 30
jcmd.exe 14532 Thread.print
jcmd.exe 17884 Thread.print
jcmd.exe 17884 VM.system_properties (только user.name/user.dir/runtime markers)
Get-NetTCPConnection
Get-CimInstance Win32_Process (java/l2 only)
Get-Acl (pilot root/inbox/processing/results/journal)
Process.CloseMainWindow() (exact owned JVMs)
Remove-Item -LiteralPath <private-runtime>/playtest-pilot/arm.properties
Stop-Process -Id 17884 -Force
Stop-Process -Id 14532 -Force
```

Client запускался существующим exe с аргументами из прямого запроса.
Пароли и arm code в publication не включаются. MariaDB SELECT: DATABASE(),
TestAdmin charId/online/coordinates, accounts login/lastactive, profile COUNT.
Пароль CLI — временный MYSQL_PWD с finally removal. SQL mutation отсутствуют.
Force отдельно разрешён пользователем после failed graceful close.
Computer-use: импорт sky, list_windows, выбор/активация единственного l2 окна;
ввода через sky не было. Screenshots просмотрены через view_image.

Publication и final verification, exact task009 paths:

```text
git diff --check -- <task009>
git -c core.autocrlf=false add -- <exact nine task009 files>
git diff --cached --name-only
git diff --cached --check
git diff --cached --no-ext-diff --no-color -- <task009 text files>
git commit -m "docs(phantoms): record observation009 pilot config blocker"
git rev-parse HEAD
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff-tree --no-commit-id --name-only -r HEAD
git status --short --untracked-files=no
```

Force push, branch, reset, restore, clean, stash, merge, rebase и feature push не используются.
