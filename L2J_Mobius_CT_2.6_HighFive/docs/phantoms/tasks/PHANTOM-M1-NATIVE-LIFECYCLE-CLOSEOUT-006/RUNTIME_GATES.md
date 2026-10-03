# Последующие gates — НЕ authorization текущей задачи

## 0. Закрыть старый чат

Старый continuation закончен на UNKNOWN/budget boundary. Не отправлять туда очередной
большой continuation. Он не запускает #5. Новый инженерный чат выполняет этот пакет.

## 1. Независимый review engineering result

Архитектор проверяет remote codeSHA, parent/diff, RESULT/HANDOFF, реальные tests, JAR
manifest. READY_FOR_CONTROLLED_DEPLOY — разрешение обсуждать deploy, не выполнять его.

## 2. Новый Codex runtime/deploy chat

Только после отдельной команды пользователя/архитектора: сохранить read-only evidence
старого JVM110/142/175, pid/start-time/JAR/config, relevant logs и diagnostics; никаких
ручных PLAY writes. Затем backup → controlled stop → exact committed clean deploy →
start → CONFIG/health/identity/population. Если stop/start нарушил consistency — boundary
RED, не продолжать acceptance и не возвращать старую БД без reconciliation policy.

## 3. Diagnostic run — только при необходимости

Сейчас extra synthetic до fixes не одобрен. Одно исключение возможно позже только если
после исправлений/деплоя остаётся конкретный runtime-only вопрос, не разрешимый TEST.
Предварительно: firstexception+stacks действительно survive, exact SHA/JAR, noactive run,
сформулированная гипотеза и stopcondition. Подсчёт старых4/5 явно сохранён.
Diagnostic не считать одним из двух acceptance GREEN. Не заменять #5 новым скрытым budget.
Если текущие симптомы больше не воспроизводятся, не пытаться восстановить утраченную
историческую ошибку бесконечными заходами: current regression + честный UNKNOWN history.

## 4. Две independent synthetic acceptance scenes

Отдельно явный новый acceptance budget = 2 scenes на стабильном committed SHA.
Разные natural candidates, existing legitimate selection, не fixed profile110/142/175,
не искусственный special bot. NEW_MATERIALIZATION/CONTACT/NATIVE_LIFE/COHORT/
SOFT_RETURN/RESTORE/STOP. После producer fix между scenes пара аннулируется для closeout.
Одна RED → сохранённые evidence/restore/stop, boundary, не blind second.
Две GREEN → WAITING_FINAL_CLIENT, не M1 GREEN. Не silent deadline extension.

## 5. Новый final REAL client gate chat — один run

Windows UI transport: `D:\Tools\L2ClientTools`.

```powershell
cd D:\Tools\L2ClientTools
.\L2.cmd -Action Login
.\L2.cmd -Action Arm -ArmCode <FRESH_ARM_FROM_EXISTING_PILOT>
# SERVER-SIDE: exact TestAdmin REAL_LOGIN; accepted arm; runActive=false.
# Exactly one existing connected M1 runner, not a new artificial acceptance.
.\L2.cmd -Action Off
# SERVER-SIDE: consent OFF and no active run.
```

Fresh arm получить через существующий server/Pilot workflow, не угадывать API/файл.
UiInGame не REAL_LOGIN; InputSentUnverified не ARMED/OFF.
SubmissionUncertain после Arm → read server consent, не слать второй arm вслепую.
Off не запускает выключенную игру. NotSubmitted + consent active → Login → Off → serververify.
ForceRestart только при объективной необходимости, не default.

Connected GREEN + all gates + OFF confirmed → M1 CLOSED, разрешён planning M2.
Connected RED → exact boundary, никакого второго run без диагностики, M2 не начинать.
