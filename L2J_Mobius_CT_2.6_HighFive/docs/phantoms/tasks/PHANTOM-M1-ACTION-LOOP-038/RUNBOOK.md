# RUNBOOK038 — автономная работа без новых копий репозитория

MAIN: `C:\Users\ZBook\L2J_Mobius` (только чтение).
OWN: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
MODULE: `OWN\L2J_Mobius_CT_2.6_HighFive`.
MariaDB: `127.0.0.1:3308`.
Новые базы: `l2jmobiush5_localplay_contract038a`, `038b`, `038c`, `038t`
(полный префикс одинаковый; t — native TEST).

До каждой записи проверить manifest, cwd, export hash, JDBC URL, SELECT DATABASE и
исключительность TEST lane. Базы037/036/035/032, PLAY и shared TEST — только READ/export.
Не сбрасывать существующую чужую базу с совпавшим именем. Никаких DROP или миграций схемы.

## Подготовка
1. Проверить Git registration OWN, HEAD, remote и packet MANIFEST. Новый worktree не создавать.
2. Перенести только проверенные файлы038 из MAIN в OWN. MAIN не изменять. Старые ignored
   evidence035/036/037 сохранить. Уборка диска остаётся PAUSED.
3. Применимые AGENTS и workflow прочитать один раз. Если есть настоящий конфликт разрешений,
   сохранить BLOCKED с цитатой правила; не ждать ответа пользователя несколько часов.
4. Проверить hash архива037 и извлечь только выбранные raw/helper families из индекса.
5. Переиспользовать ops037/Verify037/observer/stock stop/reload. Правки wrapper ограничены
   путями, guard038 и outputs. Не создавать новый тестовый runtime-framework.

JDK25/Ant — установленная среда пользователя. Не устанавливать IDE, сетевой бот или SDK.
`ant -q jar` — чистая сборка из committed candidate. Native suites запускаются по существующему
Verify037, с новым exact038t guard; не угадывать имена targets и не использовать shared TEST.
Финальный classpath Jar-first; shadow bin/test-copy production классов не подменяют actual Jar.

## Runtime
Перед START снять PID/startTicks/cwd/cmdline/ports. Чужие процессы не останавливать.
Сохранить1280 READY/8720 RETIRED, target/cap8, scheduled10000, rates, geo и радиусы.
Synthetic — существующий native Player, не Phantom и не REAL_LOGIN; GameClient=null.
Initial point — existing readonly pre-World resolver. Не заполнять cap сначала в Giran.

Короткий diagnostic probe <=150 секунд, с конкретным ожидаемым guard. Не ждать редкого357
часами и не менять календарь для его появления. JDWP только localhost/suspend=n; никаких
breakpoints во время final scenes. В native hooks нет SQL, file I/O, sleep или wait.

Final cohort выбирается ДО результатов по фактическим location/calendar/geometry данным.
Для stable window проверять обычную календарную доступность на420 секунд; не менять расписание.
После enrollment неизменно6–8 ID. Смерть/уход/replacement остаются строкой и исходным FAIL.
Два primary определяются до счётчиков: один caster и другой eligible native участник.
Финальные сцены360–420 секунд, целевая длина380, existing TTL525 секунд без повышения.
Это не ручные doCast/назначения цели, не point-click оператора и не replay старых samples.

Максимум8 full starts,4 production candidates,4 full scenes. Два final starts/две scenes
резервируются сразу. Failed wrapper без созданной JVM не считается стартом, но лог сохраняется;
каждая действительно стартовавшая GameServer JVM считается. Не скрывать budget за другим именем.
При новых semantic изменениях нужна новая final pair на одном Jar. Не перебирать группы до удачи.

## Сохранение и STOP
Последний sample снять до STOP. Existing Synthetic STOP и stock shutdown own Game/Login;
poll с единым первоначальным deadline, без продления на каждом вызове.
FULL_SAVE сопоставляет sealed receipt с его commit boundary и exact lineage последующих записей,
а не со случайным поздним SELECT. Необъяснённое расхождение остаётся NOT_VERIFIED.
Никаких SQL/wait в FINALIZED hook ради удобства проверяющего.

Healthy drain: typed COMPLETE/retained0 до остановки shared pools; собственные routes,
handles и Session state освобождены. Отсутствие JVM — отдельная проверка, не замена drain.
Если stop завис, сохранить threads/DB/evidence. Force/taskkill/Stop-Process -Force не разрешены;
CLEANUP_BLOCKED с точными own PID вместо незаметного принудительного убийства.

## Экономия времени и диска
Одно progress-сообщение на завершённый этап/существенный факт. Не делать три одинаковых
подтверждения BLOCKED и не перечитывать всю историю после каждого шага.
Архивировать выбранные raw+source+Jar+manifests, не полные runtime, DB или чужие worktrees.
Проверять size/SHA. Final SHA связывать внешним publication receipt без цепочки confirmation commits.
Worktree и базы сохранить. M1=OPEN; следующую задачу автоматически не запускать.
