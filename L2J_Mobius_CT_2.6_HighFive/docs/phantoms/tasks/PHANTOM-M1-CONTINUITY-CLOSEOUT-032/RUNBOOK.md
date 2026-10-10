# RUNBOOK032 — один изолированный цикл, без ручного участия

## Стартовая среда
Windows11/JDK25/Ant/MariaDB127.0.0.1:3308, HighFive only.
Own worktree:
C:\Users\ZBook\.codex\worktrees\m1-continuity-032\L2J_Mobius
Если занят — первый свободный suffix; detached exact base разрешён.
Статус предыдущего runSTOPPED не заменяет проверку PID/ports сейчас.

Own clone names:l2jmobiush5_localplay_contract032a,b,c,d; isolatedTEST032t.
Runtime folders:.phantom-local/contract032[a-dt]/runtime.
Старый031a после доказанного stopped — READ/export; сделать immutable dump+hash+manifest
и восстановить в новой032a. Не менять старую БД ради удобного повторения.
Для доказательства сравнения oldC21/currentC26 нужны одинаковые prestate+clock/catalog;
не объявлять regression из результатов разных cohorts.

Runtime target1280/active8/maxMaterialized8/maxScheduled10000 сохраняется.
Не уменьшать population «для скорости». TestAdmin только clone + Synthetic,
REALcount0; kpCat исключён. Нормальные GM overrides false. Без remote public JDWP.

## Reuse031, не переписывание инфраструктуры
Скопировать только нужные entrypoints/observer/helpers в пакет032, адаптировать exact
task/runtime ownership paths один раз. Не запускать старые031 wrappers, которые могут
внести изменения в старую031DB. До first start static/runtime preflight exact manifests.
Entry points максимум4: Prepare032, Control032, Observe032, Verify032;
один адаптированный observer, existing SQL/lineage helper reuse. Не заводить дубликат
observer для каждой версии. Guard не заменять широким regex ради прохода.
Полные каталоги старых logs/JFR не копировать в Git. Private references+hash достаточно.

Startup observer premain до первых lifetimes. Один instantiated collector на JVM,
stream union не равен telemetry cohort; transient checkpoint не terminal.
Ordinary hooks immutable enqueue/no SQL/FS/wait; exporter failure invalidates proof.
Исправления Windows shared-read/replace из031 сохранить. Не экспериментировать с
новой схемой синхронных checkpoint-SELECT.

## Debug-first
1. Старые43/117 raw chronologies, затем один baseline probe с выбраннымиIDs.
2. JFR/jcmd без остановки; при логическом guard — targeted JDI branch, same epoch.
3. До pause сохранить identity/hash; не вызывать Java methods через debugger для чтения
игровых объектов. Глобальный catch/долгая VM suspension запрещены.
4. Снимок и resume bounded; все pauses исключить из gameplay timing. Старые frames не proof.
5. HotSwap допускается лишь для совместимых локальных диагностических/поведенческих
изменений без миграции field/schema; применимость проверить в текущей JVM.
HotSwap-experiment не final acceptance и не лечит уже испорченные singleton/state.
Никогда не изменять Player fields/SQL/counters вручную в debugger.
6. Source compiles и packaged Jar различать: тестовый маршрут с dist/libs/GameServer.jar
должен получить текущий Jar, а не только новые test classes. Ввести hash preflight,
не повторять ошибку «GREEN использует старый Jar» из031.

## Экономия запусков
Не обязательный clean build перед чтением каждого лога. Baseline testedJar можно
переиспользовать только после проверки source/dependencies/hash. При fixes — targeted
checks; clean committed build перед frozen acceptance. Не full93 после каждой строки.
Не повторять unchanged failure; записи retry должны иметь конкретный новый вопрос.
3 launcher/setup сбоя подряд → разобрать конкретный wrapper, не гонять game ещё раз.
Порог не запрещает продолжить после исправленного preflight.

## Сцены/away
Synthetic per-sessionTTL525, heartbeat5/watchdog30, server400requests/planned350,
5sessionstarts/JVM сохраняются. Heartbeat не продлевает absolute TTL.
Для stationary budget: setup<=60+380scene+45cleanup=485<525.
Warm-up при необходимости отдельный объявленный diagnostic episode, не обнуление clock
того же acceptance run. Постоянная telemetry не идёт через mailbox на каждый sample.

NativeStartAtSetup до World publication, no Giran prefill/cap leak. Место выбирается
по живым/current calendar/canonical геоданным, не по успешным наградам.
Не PNC backgroundFARM как prerequisite physical demand.
Сохранять независимо cold/demanded candidates, baseline и отсутствующих031IDs.
До выбора места сверить фактический current camp и native dry возврат, не абсолютное
смещение x-200 в стену по привычке. Первичный setup не grant на materialization.

Для Away reused ReadArrivalFrame031 на WaitArrival И Walk. До шага проверить freshness
и actual starting pose; observer/route identity не выбирать по retained old epoch.
Native step<=300.01,|dz|<=200; arrival<=32/|dz|48 и12s. До40итоговых steps,
route320s, episode480s,TTL525s — неизменно. Бюджет вычислить до движения, включая
>=75s фактической background работы и время post-return progression. Если route не
помещается, выбрать проверенный более близкий legitimate endpoint ДО эпизода, не
ослаблять bounds. Stock water/GeoEngine остаются авторитетом.
Unknown MOVE не переотправлять. ACCEPTED!=ARRIVED. Частичный отход не SOFT_RETURN_PASS.

## Завершение
Synthetic stop/restore, native drain, immutable receipts и экспорт; затем stock stop.
Не обрывать общий ThreadPool раньше typed DONE. No forced query success.
Зафиксировать exact process absence, pending/retained/collector refs и сохранённыеDB.
Утренний MORNING: проверенная короткая команда запуска конкретногоSHA/clone + gates.
По умолчанию не оставлять сервер «ждать пользователя». REAL_FINAL отдельным согласованным
пользовательским прогоном, без автоматического управления TestAdmin ночью.
