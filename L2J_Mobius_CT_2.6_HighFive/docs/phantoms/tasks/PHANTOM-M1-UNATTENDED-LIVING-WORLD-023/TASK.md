# Полномочия и границы TASK023

## Явное разрешение пользователя
Пользователь попросил одну ночную задачу на 4–6 часов, с исправлениями, без ожидания его
ответов. Это осознанное исключение из прежних 55/90/120-минутных task-бюджетов.
Отсутствие ответа — не STOP. Обычный внутренний RED в перечисленном slice — не STOP.
Не создавать новую задачу/другой чат после каждой причины. Все решения ниже уже приняты
координатором; исполнитель реализует их и проверяет фактические условия.

Максимум 360 минут wall-clock от начала, включая build, runtime, ожидания инфраструктуры
и завершение. Цель 240–330 минут, но не тратить время искусственно при раннем успехе.
После 270-й минуты не начинать новый semantic repair; остаётся интеграционная проверка,
review и публикация. Последние 30 минут обязательно оставить для cleanup/report.
Максимум 4 причинно-обоснованных repair rounds, до 8 полноценных runtime episodes за ночь;
без повторного запуска неизменённого RED ради удачи. Проверки одного исправления в
нескольких детерминированных сценариях не являются отдельными repair rounds.

## Git / рабочая копия
Required remote base: fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6.
Repository: https://github.com/kpCat/L2J.
Разрешён новый isolated detached worktree:
C:\Users\ZBook\.codex\worktrees\m1-overnight-023\L2J_Mobius
Если путь занят, выбрать первый свободный -02/-03 suffix, не удалять существующий.
Если целевая ветка занята другим worktree, detached — штатный разрешённый вариант.
Основной C:\Users\ZBook\L2J_Mobius только read-only для task-пакета/retained evidence.
Разрешены read/fetch/diff/log, worktree add --detach, exact-path add/commit и normal push
origin HEAD:refs/heads/experiment/m1-candidate007-observe008 при любом конечном статусе.
Запрещены git add ., reset/clean/stash/rebase/force, правки feature/phantom-world,
чужой diff, перезапись опубликованных коммитов. Чужой новый remote HEAD — не force:
сохранить patch/отчёт, статус PUBLISH_BLOCKED; не устраивать merge ночью.

## Данные и процессы
PLAY l2jmobiush5_localplay3: SELECT/SHOW/read/export, без UPDATE/DELETE/reset/DDL.
Guarded TEST l2jmobiush5_phantom_test: только существующий test guard и exact cleanup;
не удалять неизвестный journal/diff, не останавливать чужой тестовый процесс.
Разрешены новые owned clone DB:
l2jmobiush5_localplay_night023a .. night023h.
Работающий GameServer сам пишет в СВОЙ clone штатными механиками. Подготовка clone —
только прежний export/import и exact ordinary synthetic observer config. Нельзя ручным
SQL чинить phantom goals/exp/items/presence либо считать fresh clone ремонтом PLAY.
Сохранить все исходные и неудачные snapshots; для restart использовать ТУ ЖЕ clone DB.
kpCat не менять; TestAdmin не превращать в fake REAL. Допускается использовать его
обычную КОПИЮ в owned clone как synthetic при отсутствии клиента/Phantom-связи.

Заранее разрешено: старт/штатный stop только созданных TASK023 Login/Game; один
контролируемый crash только owned GameServer на выделенной task023 clone после
сохранения evidence и отсутствия REAL клиентов; при аварийном зависании после двух
bounded graceful попыток и dumps — завершить только свой exact PID+start-time+JAR+
runtime fingerprint. Никогда не kill Java/MariaDB по имени, не трогать чужие PID.
Force в обычной финальной приёмке = CLEANUP_FAIL, даже если процесс исчез.

## Никаких ночных вопросов
Не использовать AskUser/опрос с таймером. Не запрашивать «в игре», arm, «вышел», GM-права.
Таблица решений без пользователя в RUNBOOK.md. Не вмешиваться в уже открытый клиент.
Если появились внешние системные approval/auth ограничения, их нельзя обходить: сохранить
BARRIER_PLATFORM и продолжить независимые безопасные проверки, если возможно.
Если безопасность/владение не устанавливаются, закончить BLOCKED с сохранённым результатом,
а не бесконечно ждать. STOP_AUTHORITY=TASK023_CONTRACT, не «пользователь попросил стоп».

## Scope
Обязательная последовательность: native cooperative combat safety → continuous farm →
full ordinary lifecycle → server acceptance. Max20 production paths из SOURCE_MAP,
max2 новых production classes; остальные строки OPTIONAL — не команда менять все файлы.
Точечные существующие интеграционные hooks разрешены, переписывание Mobius запрещено.
No M2/chat/PvP/кланы/профессии/новая экономика/глобальный codemap/10k perf milestone.

## Расход контекста
Полные логи/thread dumps/large diffs сохранять в файлы; в tool output показывать только
нужные диапазоны и25–40строк итогов. Не печатать целиком Player.java/весь commit/10k census.
PROGRESS_CURRENT.md: <=80строк, текущий SHA/phase/budget/root/следующая команда/owned
процессы/DB. После compaction читать его, не пересобирать историю всех task001..022.
Обновлять на границах фаз/repair round, не каждые30секунд. No repeated blocked-turn loops.
