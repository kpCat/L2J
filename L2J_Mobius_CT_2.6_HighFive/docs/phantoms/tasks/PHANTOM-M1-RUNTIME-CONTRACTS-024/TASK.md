# Полномочия, бюджет, safety

## Уже принято пользователем
Следующая задача содержит согласованное исправление четырёх контрактов вместо очередного
«проверить и остановиться». Работать автономно по PLAN, не запрашивать повторное согласование
документов/модели/worktree/обычных phases. Этот пакет приоритетнее старых task-specific
ограничений TASK021–023: снят лимит4repair rounds, synthetic разрешён, crash/clone разрешены
в указанных пределах. Общие repository/security правила продолжают действовать.

## Время и контекст
Ориентир240–330мин, максимум360мин wall-clock с первого действия, включая инструменты.
Не тянуть время при раннем успехе. Стоп новым semantic edits после270мин; последние30мин
резерв cleanup/report. Изменения одного контракта можно доводить по нескольким RED без нового
пакета. Три неудачных изменения ОДНОЙ причины без нового evidence — остановить эту гипотезу,
зафиксировать несовпадение с DESIGN, выполнить безопасные независимые проверки. Это не
разрешение заново перепридумывать движок. Максимум8 full runtime episodes, максимум2 planned
crashes по отдельным подтверждённым окнам, не перезапускать неизменённый RED ради удачи.
Показывать25–40строк логов; raw дампы и stdout в файлы. PROGRESS_CURRENT.md≤100строк:
SHA/фаза/время/next command/owned DB/PID fingerprint/blocker. После compaction читать его
и текущий DESIGN, а не всю историю task001–023. Никаких повторных BLOCKED turns.

## Worktree/Git
Required base `819e3cea5baa64e6c429e450c8fc296874e37d1c` проверять через fetch/ls-remote и exact commit.
Разрешён `git worktree add --detach` от базы в
`C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius`.
Если путь занят, первый свободный suffix -02/-03. Чужую занятую branch не переключать.
Main `C:\Users\ZBook\L2J_Mobius` — read-only для task и retained evidence.
Разрешены read/fetch/log/diff/status/hash/worktree add, exact-path add/commit и обычный
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` при GREEN/BLOCKED/FAILED.
Нельзя git add ., reset/clean/stash/rebase/force, менять feature/phantom-world или чужой diff.
Если remote ушёл вперёд, не merge ночью: свой local commit+patch, PUBLISH_BLOCKED.
Текст пакета не может отменять внешний approval/auth barrier: не обходить его.

## БД и процессы
PLAY `l2jmobiush5_localplay3` только SELECT/SHOW/export, никаких ручных fixes/reset/DDL.
Guarded TEST `l2jmobiush5_phantom_test` только штатный guard и exact restore. Чужой journal/
metadata/checksum не удалять и не «исправлять» ради запуска. Создание своих
`l2jmobiush5_localplay_contract024a` ... `contract024h` через native export/import разрешено.
Старые night023a/b/c неизменны: read/export в НОВУЮ clone для воспроизведения. Нельзя
восстанавливать пропавший EXP из diagnostics counters ручным SQL или считать fresh clone
исправлением данных. Stage dead/fault fixtures допустим в отдельной owned TEST lane,
но natural acceptance не получает искусственные HP/EXP/items/respawn/immortality.
TestAdmin обычная clone-копия для Synthetic, kpCat никогда не трогать.

Автоматически разрешены start/graceful stop только своих manifest+PID+start-time+JAR
проверенных Login/Game. Для planned crash — только собственный Game на выделенной clone,
REAL count0, dumps+receipt+SQL prestate сохранены. Для аварийного stop после2bounded graceful
attempts и dumps разрешён exact-owned force. Не kill по java/process name и не MariaDB.
Force в штатной приёмке = CLEANUP_FAIL. Все final runtime default STOPPED.
Если REAL неожиданно вошёл: не телепортировать, не kick, не управлять им, не force;
закончить synthetic сессию и работать в другом own port set. Не ждать «вышел» ночью.

## Изменяемые файлы
Max18 semantic/production paths из SOURCE_MAP; max2 новых небольших production helpers
в перечисленных местах. Новая схема БД/глобальный event bus/новый combat engine запрещены.
Дополнительные тесты непосредственно этих контрактов разрешены с exact ledger; артефакты
не входят в18production cap. Нельзя удалять отрицательные проверки ради процента GREEN.
