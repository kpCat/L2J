# Полномочия и пределы TASK025

Пользователь поручил следующую инженерную задачу после FAILED024. Разрешения ниже относятся
только к этой задаче. Общие repository/platform safety остаются; внешние approvals не обходить.
Задание не должно ждать ручной login, arm, exit, worktree, commit/push или очередную фазу.

## Время и расход
Ориентир180–300мин, максимум360мин wall-clock с первого действия. К120мин — short full
server probe. Не ждать истечения6ч при раннем успехе/доказанном outside-scope препятствии.
После270мин новые semantic edits запрещены; reserve90мин для acceptance и publication.
Нет старого hard cap4repair/8starts. Каждый runtime restart имеет named purpose в ledger;
повтор неизменённого RED ради удачи запрещён. Planned crash≤2, ниже отдельно.
Сначала targeted checks, не полныйWORLD suite после каждого изменения.

Вывод команд≤60строк, raw в evidence. После compaction читать PROGRESS_CURRENT≤100строк
и текущий DESIGN, не весь многомесячный чат. Без субагентов. Никаких повторных blocked turns.
Doc/script scaffolding — переиспользование tools024; не новый мини-фреймворк аудита.

## Git
Base07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6, branch experiment/m1-candidate007-observe008.
Разрешены fetch/ls-remote/show/status/diff/log/hash/read и:
`git worktree add --detach <own-path> <exact-base>`.
Own path `C:\Users\ZBook\.codex\worktrees\m1-checkpoint-025\L2J_Mobius`, свободный suffix если занят.
Main `C:\Users\ZBook\L2J_Mobius` read-only. Чужой worktree/diff не менять.
Exact-path add, commit и `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`
обязательны для своего task diff при GREEN/BLOCKED/FAILED. Если remote продвинулся —
свой local commit+patch; PUBLISH_BLOCKED, без force/merge/rebase.
Нельзя git add ., reset/clean/stash/rebase/force и feature/phantom-world changes.

## DB
PLAY l2jmobiush5_localplay3 только SELECT/SHOW/export. Старые night023*/contract024* только
read/export. Own DB l2jmobiush5_localplay_contract025a..h: export/import/schema setup через
имеющиеся native tools разрешены. a — копия retained024c для452; b — fixture lane;
c/d — natural acceptance; e/f — fault lanes; g/h — запас. Не менять чужую TEST metadata.
Shared TEST только существующий gate+restore; если barrier — own lane из TASK024, не
подмена checksum. Test-only fixture DML через существующий guarded initializer допускается
в own fixture DB; natural cohort не получает grants/HP/XP/items/forced class/schedule.
kpCat/main profile не трогать. Не восстанавливать live progress из counters.

## Processes и synthetic
Все новые runtime loopback, manifest/PID/startTime/JAR проверены, не захватывают чужие порты.
Occupied port → свой согласованный loopback set в manifests, не kill чужого Java.
Запуск и graceful stop own JVM разрешены. Работа synthetic/observer — existing service,
ordinary clone TestAdmin, GameClient=null, no fake REAL_LOGIN. Прежние per-session TTL и
5 starts/JVM не обходить. Новый session допускается только по явной границе сценария.

Плановые crashes:≤2 на exact own isolated GameServer, first AFTER_NATIVE, optional
AFTER_FINALIZE после его resolution; REALcount0, source/epoch/receipt/dump сохраняются.
Emergency exact PID+start-time+runtime force — после2 bounded graceful attempts+dumps,
REALcount0; это CLEANUP_FAIL, не лечение. MariaDB и чужие JVM никогда не останавливать.
Если появился REAL: не kick/move/force; уйти в другой own runtime или safe tests, не ждать
«вышел». В конце свои JVM по умолчаниюSTOPPED, clones/evidence сохранены.
