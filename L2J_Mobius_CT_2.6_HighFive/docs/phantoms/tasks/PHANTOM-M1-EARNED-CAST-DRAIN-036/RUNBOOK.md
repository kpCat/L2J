# RUNBOOK036 — без пользователя и без новых копий репозитория

## Bootstrap
1. OWN `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius` уже существует.
   Проверить registration, full HEAD, remote, tracked/untracked task boundaries, process census.
   Не удалять/переключать worktree. Если HEAD другой — exact BLOCKED_BASE, не reset.
2. MAIN packet проверить MANIFEST и копировать только указанные файлы в соответствующий OWN path.
   Runtime на MAIN не запускать. Другие файлы MAIN не менять.
3. Чтение evidence035/034 строго selective и hash-verified. Приватные ops035 и свежий035b runtime
   находятся в OWN; пересоздать только036 helpers с явными substitutions/manifest.
   Источник данных035b или прежний032a назвать до старта и snapshot readonly export зафиксировать.
   Текущие declined/неполные states не «лечить» SQL. Не выбирать source post-failure ради лёгкого GREEN.

## Runtime
- Разрешены schemas только `l2jmobiush5_localplay_contract036a`, `...036b`, `...036t`.
  Create/import из coherent source разрешены один раз на новую собственную схему; не overwite
  уже имеющуюся DB. Обычные GameServer writes и native fixtures только внутри exact manifest.
- MariaDB127.0.0.1:3308, JDK25/Ant из фактического установленного окружения.
  Preserve pop1280 READY/8720 RETIRED, MaxScheduled10000, actual materialization cap8;
  active target<=cap8. Не уменьшать total population и не повышать cap/deadlines ради результата.
- Игровой клиент не запускать. Existing Synthetic: настоящий headless non-Phantom наблюдатель,
  без REAL_LOGIN/arm. Начальная точка — proven pre-World setup033R/current LocalPlay resolver,
  не spawn в Giran→teleport с занятием чужих slots. Background permission не фильтр physical demand.
- TTL525s/watchdog30s/sequence400 из текущего контракта сохранить; heartbeat один, отдельно от mailbox.
  ACCEPTED != ARRIVED; uncertain command проверяется по requestId, не отправляется повторно.
- Собственная сцена до360–420s с запасом на Synthetic stop; не строить длинный away route.
  Для primary нужен действительный current caster и action evidence; отсутствующий27 не подменять.
- Первый full-server probe до60мин; максимум6 starts,4 новых product builds.
  Native test JVM отдельно учитывать. Final classpath JAR-first, old compiled code не используется.

## Debug
JFR/jcmd сначала без pause, JDWP только loopback на свободном порту own manifest.
Breakpoint точечный, только диагностическая проба; paused time исключается из liveness proof.
Не использовать global method trap и не оставлять JVM suspended на финальной сцене.
HotSwap допустим только body-compatible диагностический эксперимент, с записью классов/хеша;
полученные PASS не заменяют запуск committed freeze. Если JDWP недоступен, existing trace +
bounded instrumentation; не ждать пользователя и не исследовать инструменты часами.

## Остановка и сохранение
- Проверить всех logged-in REAL. При неожиданном REAL session не кикать/не менять/не kill;
  такая внешняя session блокирует disruptive шаг, не повод считать consent полученным.
- Existing authorized stop helper, exact PID+startTicks+runtime/Jar/DB ownership, stock Game/Login stop.
  Проверить отдельно native STOPPED/COMPLETE и выход процессов. Диагностические helpers освободить.
- Если native shutdown FAILED: зафиксировать live ownership/first incident/threads перед stock exit;
  сохранить failed state. Не retry-reset deadline, не force-release tickets. Плановые process-crash
  gates не разрешены. Force process kill не предразрешён; при недоступном stock stop — STOP_BLOCKED
  с exact PID/причиной, без busy wait и без утверждения JVM0.
- Архивировать выбранные raw/source/Jar/receipts один раз за пределы worktree с SHA каждой записи.
  Не собирать гигантский universal archive. Не удалять DB, worktree, старые runtime или evidence.
  Новый helper/output максимум одна task family; один final report, не десятки receipt-only commits.
- phantom-dev СОХРАНИТЬ. Disk audit/удаление старых worktree остаются на паузе.
