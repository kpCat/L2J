# GIT036 — точные полномочия

USER TASK036 разрешает read-first и exact-path commit/normal push при любом исходе.
Не разрешает merge, reset, clean, stash, rebase, prune, force, delete refs или worktree.

```powershell
$main = 'C:\Users\ZBook\L2J_Mobius'
$own = 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
$branch = 'experiment/m1-candidate007-observe008'
git -C $main worktree list --porcelain
git -C $own rev-parse HEAD
git -C $own status --porcelain
git -C $own ls-remote origin refs/heads/$branch
```
На старте HEAD и remote должны равняться `c0e86fa2950012ae2ee898b841524fa160869a77`. Игнорируемые данные старых задач
сохраняются; отсутствие в `status` не делает их разрешёнными к удалению.
Не менять ветку основного checkout; detached OWN допустим. Не переносить всё на feature.

Работа: read-only git show/diff/log/cat-file/ls-files/status по точным нужным paths,
source hash list до и после. Публикация:
```powershell
# $paths — реальный буквальный массив только packet036, outputs036 и изменённых разрешённых исходников.
git -C $own add -- @paths
git -C $own diff --cached --name-only
git -c core.whitespace=cr-at-eol -C $own diff --cached --check
git -C $own diff --cached -- @paths
git -C $own commit -m 'TASK036: <фактический проверенный результат>'
git -C $own push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C $own rev-parse HEAD
git -C $own ls-remote origin refs/heads/$branch
```
Заполнять actual paths/message, не запускать placeholders. Нет broad add/-A.
При remote divergence — остановить publication без force; local commit/evidence сохранить.
Report selfSHA хранить во внешнем receipt после commit, не запускать бесконечные confirmation commits.

Read-only imported task documents → explicit task-family exception к generic числу файлов.
SOURCE_MAP задаёт conditional writable methods, не весь репозиторий. Любое semantic изменение
за scope — BLOCKED_ARCHITECTURE. Строгие local AGENTS учитывать; явно разрешённые task036
commit/push выполнять, не ссылаться на generic no-git для отказа от согласованной публикации.
UTF-8, исходные EOL вне hunks сохранить; mojibake и escaped Cyrillic проверяются отдельно.
Raw credentials, полные DB/JFR, приватные игровые logs — не public Git.
