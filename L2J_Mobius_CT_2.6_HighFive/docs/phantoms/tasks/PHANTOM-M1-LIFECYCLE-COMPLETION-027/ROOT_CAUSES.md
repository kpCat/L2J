# Доказательства и пределы вывода

BASE 882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf, его parent cb2d9b08d96e4f2386836b40d5ae87ceba9fcf63.
Последний commit содержит публикацию TASK026, parent — frozen R6 product.

## S1 — подтверждённый путь раннего отказа shutdown
TASK026 `evidence/FINAL_R6_B_C_NATIVE_SHUTDOWN_LOGS/java0.log`:
- 17:25:27.886 начат shutdown;
- 17:25:27.923 Initial incomplete (33ms): system FAILED, materialization RUNNING,
  entries8, progression/navigation STOPPING;
- 17:25:27.941 Final incomplete (17ms), .942 shared pools shutting down;
- .944..949 ACTION_DRAIN interruption в другом profile/object из scheduler→background.recover.

В PhantomSystem: shutdownIfStarted ждёт ecology до10s; остальные finish checks и
backgroundReadyForMaterializationShutdown возвращают false без bounded progress wait.
В Shutdown два вызова этого hook отделены лишь disconnect/game-time-stop и сразу идут
к ThreadPool shutdown. Существующий native drain до них может даже не дойти.

Доказано: текущая композиция допускает закрытие executors раньше lifecycle quiescence.
Не доказано: какое конкретно prerequisite первым было busy в B. В новом RED захватить
первый блокирующий gate: death reconciliations/background operation/transition/другой сервис.
Исправление общего PENDING-контракта допустимо после composed RED, а не по одному boolean.

## S2 — callback, не смешивать с S1
Original profile404 EVENT24354 ON_ATTACKABLE_KILL: epoch197184915592600,
RESERVED/EARNED, SUBMITTED, delay2500ms, future notDone/notCancelled, dueMs=-4383.
Это exact publication. UNKNOWN: не снят с очереди, вошёл и блокирован перед tryStart,
другая блокировка или нагрузка. Текущий startNanos выставляется внутри owner monitor,
поэтому start0 не доказывает, что executor body не начинался.

Добавить различение EXECUTOR_ENTERED (до ParticipantWork/owner locks), TICKET_RUNNING,
BODY_EXIT и COMPLETED для выбранных событий. Это наблюдение, не новый планировщик.
Не лечить предполагаемый starvation увеличением pools/timeouts. Не replay callback.

## L1 — dead/absent
В A профиль275 DEAD до baseline; в B110/175 DEAD,275/447 alive в SQL, но отсутствуют.
Пять отсутствий не равны пяти зависшим в AutoPlay персонажам.
Нужны exact presence/locality/capacity/state/epoch/recovery reason. Нет доказанной причины
каждого отсутствия. Старую denominator8/строки TASK026 не менять и не объявлять PASS.
PhantomOrdinaryDeathRecovery существует; она держит death map, pulse и native reconciliation.
BackgroundService.recoverOwned выполняет native recovery, затем синхронный dematerialize,
reconcile и возможный rematerialize. Этот путь вызывается из scheduler (стек B).

## L2 — фарм/phase
Farming R6 по-прежнему 3/8 +3/8. Route absent, phase deadlines и natural deaths открыты.
TASK027 не должен заявлять root cause для всех этих строк из одного shutdown exception.
N02/S12, cold452 и025 recovery теперь прошли026 regressions: сохранять, не чинить заново.
После DEAD-return нужен реальный новый farm progress; просто revive не является успехом.

## P1 — сравнение сохранения
A SEALED→SQL7/8; отсутствующий275 не дал final live receipt. B1/8 при failed drain.
Это отсутствие целой линии доказательства, не автоматическое доказательство потери EXP.
d restart8/8 twice. c первый restart поменял MATERIALIZED→READY для142/260/404,
при сохранных native bytes; второй идемпотентен. Законный lifecycle marker отдельно от
native payload. Старый raw FAIL остаётся, не переписать его постфактум.
