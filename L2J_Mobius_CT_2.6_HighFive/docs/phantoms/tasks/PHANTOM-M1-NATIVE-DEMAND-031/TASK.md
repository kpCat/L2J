# TASK031 — допуск наблюдения и настоящая причина текущего отказа

Required base: `e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0`
Branch: `experiment/m1-candidate007-observe008`
Модель: GPT-6.1 Sol, reasoning High. Новый чат, без субагентов.

## Зачем эта задача
Не повторять многочасовой «M1 closeout». TASK030 исправил конкретную проекцию и
collector, но final scene даже не началась. Следующая необходимая развилка:
**не даёт ли setup приблизиться к персонажу именно потому, что ещё не выполнена
работа, которую должен запустить этот подход? И какая текущая ошибка мешает после
настоящего demand?**

Read-first координатора уже установил различие двух контрактов:
1. Observe030 выбирает setup по READY && calendarOnline && farmAllowed &&
   readinessComplete. farmAllowed вычисляется через PNC permits(FARM).
2. registerDue(... materializationDue=true ...) имеет штатный exact
   NATIVE_CONTEXT_HANDOFF при текущем demand, хотя dueSnapshot.complete=false
   и обычный background FARM может быть запрещён.
Это доказанное различие, НЕ доказательство, что оно единолично объясняет cohort0.

## Изменения
A. Исправить только выбор места первоначального Synthetic-наблюдения: не требовать
заранее допуска к background FARM и завершённой dueSnapshot. Реальный production
admission НЕ обходить. Полные критерии финальной игровой сцены не ослаблять.
B. Одним коротким запуском проследить фактическое demand→preparation→materialization.
C. Не терять class/cause/SQLState/stack текущих backend/runtime исключений за общим
BACKEND_FAILURE. Разрешены только ограниченные диагностические изменения, без
замены игрового результата или снятия защит.

Это развилка с ограниченной стоимостью, не попытка объявить M1 закрытым.
Успех диагностики и успех живого мира сообщать РАЗДЕЛЬНО.

## Стоимость и конец
Ориентир 60–90 минут, жёсткий максимум 120 минут wall-clock, из них последние15 —
cleanup/publication. Два коротких native probe максимум; не двухчасовое наблюдение.
Первый probe начать до45-й минуты. Две полные 380s сцены, crash и новый общий аудит
не входят. Никаких циклов «ещё раз подтвердить BLOCKED».

Если новая причина требует изменения production-семантики, вернуть точный вызов,
первое исключение, pre/post state и решение с ограниченным scope. В ЭТОЙ задаче
не чинить неизвестную заранее lifecycle/transaction ошибку и не сбрасывать данные.
Дальнейшее решение принимает координатор, не автономный scope expansion.

## Разрешения
Автономно, без ручного входа/arm и вопросов о фазах. Own isolated detached worktree:
`C:\Users\ZBook\.codex\worktrees\m1-native-demand-031\L2J_Mobius` (свободный suffix).
Main/foreign worktrees/kpCat — read-only. PLAY и старые clones — SELECT/export only.
Новые собственные DB `l2jmobiush5_localplay_contract031a`, `...031b`, `...031t`.
Existing TEST guard; никакой переписи checksum/schema shared TEST ради запуска.

Один неизменяемый export входной БД, SHA/время/предыдущие состояния до первого boot.
Не выдавать clone старого плохого состояния за fresh healthy state. Никакой DML-heal,
reseed/reset/retirement/revive/награды ради результата. Испорченные и DEAD profiles
остаются в отчёте; в living acceptance за живых не засчитываются.

Own start/graceful stop разрешены. Emergency stop только своих exact PID+startTime+
runtime после dumps, проверки REALcount=0 и двух bounded graceful attempts. Force
не является cleanupPASS. Плановых crash нет. Чужие Java/MariaDB не останавливать.

Exact-path commit + normal push разрешены и обязательны при любом итоге. Никаких
add ., reset, clean, stash, rebase, force, force-push; feature/phantom-world не менять.
Не мержить новые remote изменения автоматически. Remote drift — сохранить свой
result/code commit, остановить publication с объяснением, без перезаписи remote.
STOP_AUTHORITY=TASK031_CONTRACT. M1=OPEN. Следующую задачу не начинать.

## Обязательный приоритет runtime-отладки (ревизия пакета031)
Сначала прочитать `DEBUG_PLAYBOOK.md`. Поверх исходного TASK031 приоритет такой:
- НЕТ нового production code до первого actual baseline/first-cause поиска
  средствами jcmd/JFR/JDWP; исходные checks/build используются только для baseline.
- **Первый full-server probe до35мин** (исходный deadline45мин ужесточён).
- Не больше2 starts собственного GameServer; ≤1 baseline build, дополнительная
  сборка только при подтверждённой необходимости. Нет полного retest 92/92 на
  каждом шаге; final focused tests после фиксированного source.
- HotSwap только observational method-body в разрешённых DIAGNOSTIC_ONLY paths,
  не semantic repair и не pass gate. Если JDWP недоступен, результат из JFR/jcmd
  и точных observation hooks обязателен; не тратить час на настройку IDE.
- При фиксированном первом отказе остановиться: записать причину, кто владел,
  какой guard и что надо исправить следующей задачей. Никаких проб исправления
  многокомпонентного product defect в этой диагностической задаче.
Детали и anti-contamination в `DEBUG_PLAYBOOK.md`.
