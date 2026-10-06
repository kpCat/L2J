# VISIBLE_DIAGNOSIS — TASK019

Статус: `BLOCKED_VISIBLE_DIAGNOSIS`. Единственная D-class не доказана; semantic fix запрещён. M1=OPEN.

Required base: `d153de95fd0fd8b678f975964179aeba85c57336`. Код до observe019a не изменялся, `ant -q jar` прошёл. Private runtime JAR совпал с этой сборкой. TASK018 принят без повторного исследования materialization.

## Наблюдение на неизменённом коде

После сообщения пользователя «в игре» Pilot подтвердил `ARMED_IDLE`, object268492939. Успешный STATUS подтвердил REAL_LOGIN, online=true, worldPresent=true, clientIdentity присутствует, moving=false, targetId=0. Действующий Pilot service допускает такой REAL_LOGIN actor только при client connection state IN_GAME.

SNAPSHOT_PHANTOMS в 14:56:36 Europe/Chisinau показал ближайший естественный visible actor: profile110/object268485779, distance78, XYZ44126,42751,-3488, alive, IDLE, target0, moving=false, attacking=false, AutoPlay=false. SELECT_VISIBLE_PHANTOM_TRACE выбрал тот же profile110. TestAdmin не таргетил и не атаковал Phantom. Agent не управлял Phantom.

| Поле | t0 | +2s | +5s |
|---|---|---|---|
| server start UTC | 11:56:37.872061900 | 11:56:39.871991700 | 11:56:42.875875200 |
| runtimeState | NEEDS_REPLAN | NEEDS_REPLAN | NEEDS_REPLAN |
| attached | true | true | true |
| goalType / goalStatus | farm.background / ACTIVE | farm.background / ACTIVE | farm.background / ACTIVE |
| decisionSequence | 0 | 0 | 0 |
| candidateKey | null | null | null |
| step | -1 | -1 | -1 |
| attempt | 0 | 0 | 0 |
| lastResult | null | null | null |
| reasonKey | goal.reloaded | goal.reloaded | goal.reloaded |
| topCandidates | [] | [] | [] |
| activityState | null | null | null |
| recorded / dropped | 0 / 0 | 0 / 0 | 0 / 0 |
| health | HEALTHY | HEALTHY | SLOW |

Интервалы между server start t0 и последующими снимками: 1.9999298s и 5.0038133s. Replay не запускался: итоговый trace SLOW, recorded=0. Отсутствующий candidate при decisionSequence=0 не доказывает D2 DECISION_NO_CANDIDATE. Attached=true и ACTIVE также не доказывают, почему обработка не продвинулась. Scheduler/materialization причину не угадывали.

## Нарушение предположения о read-only PREPARE

Existing API трактует положительный profileId INITIAL как reprepare уже выбранного envelope. Поэтому сначала выполнен автоматический INITIAL, который выбрал тот же profile110, затем предпринят exact reprepare. INITIAL вернул ACCEPTED/M1_NATURAL_GEO_PROVEN_ENVELOPE с object268485779 и epoch15958142073300.

Но PREPARE вызывает `actor.teleToLocation(selectedRoute.outside(), false)` в LocalPlayPilotActions.java. Агент пропустил этот побочный эффект при первоначальном чтении метода; утверждение о read-only PREPARE было ошибочным. BEFORE: TestAdmin XYZ44131,42673,-3488, worldPresent=true, teleporting=false. AFTER: XYZ49216,42751,-3491, worldPresent=false, teleporting=true. Это движение наблюдателя нарушило требование неподвижного TestAdmin; к Phantom никакого управления не применялось.

Exact PREPARE для profile110 в 11:56:43.871907800Z получил REJECTED/ACTOR_BUSY при той же незавершённой телепортации. Затем capture-скрипт остановился до census. После разбора evidence попытка SNAPSHOT_M1_ENVELOPE с тем же RunId была отклонена локальным runner как CANCELLED: run уже остановлен. Session.properties подтвердил stoppedRunId именно этого run и state=ARMED_IDLE. Терминальное состояние проверено; observation не перезапускался, другой RunId не использовался.

Точные `currentActionGuard`, `runtimeReason`, `travelReason`, `travelFailureReason` не получены. Два envelope/census snapshots отсутствуют. Native evidence baseline/final отсутствуют. Поэтому ни одна D0..D12 не назначена: это capture gap и BLOCKED_VISIBLE_DIAGNOSIS, а не доказанная gameplay root cause.

## Stop boundary

Fix, deterministic RED, post-fix regressions, broad materialization rerun и observe019b не запускались. Production changes=0. Второй ручной вход не запрошен. Никаких setAutoPlaying hacks, schema/DML repair или native-drain изменений нет.

Пользователю предложено выйти до character select. Итог сохранения и graceful stop отражается в RESULT.md после фактической проверки. Force fallback запрещён и не применялся.

Sanitized точные ответы: EVIDENCE019A.json. Raw JSON/XML сохранены только в private `.phantom-local/observe019a/visible-diagnosis`, вне commit. Следующая граница требует отдельно согласовать наблюдение без побочного движения PREPARE; автоматического продолжения нет.
