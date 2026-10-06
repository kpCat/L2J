# PHANTOM-M1-VISIBLE-FARM-019

Статус: **BLOCKED_VISIBLE_DIAGNOSIS**. M1=OPEN. Production changes=0; semantic fix не выполнялся.

На неизменённом required base observe019a доказал natural visible profile110/object268485779 и снял trace t0/+2s/+5s. Trace: attached=true, farm.background/ACTIVE, NEEDS_REPLAN, decisionSequence0, candidateKey=null, step=-1, attempt0, lastResult=null, reasonKey=goal.reloaded. Единственный D0..D12 не доказан, потому что обязательные census guard/runtime/travel поля не получены.

## Evidence и ограничение capture

Первый manual login TestAdmin подтверждён пользователем и Pilot REAL_LOGIN/IN_GAME/ARMED_IDLE. PHANTOMS: alive, IDLE, target0, movingFalse, attackingFalse, AutoPlayFalse, XYZ44126,42751,-3488, distance78. SELECT выбрал тот же profile110. PREPARE-initial подтвердил object268485779/epoch15958142073300.

Но PREPARE содержит побочный `actor.teleToLocation(outside,false)`. Агент пропустил его при исходном чтении, и операция автоматически переместила TestAdmin. Exact reprepare получил ACTOR_BUSY при teleporting=true. После прерывания capture Pilot подтвердил terminal stoppedRunId; последующая snapshot попытка с тем же run получила CANCELLED до отправки. Нельзя считать PREPARE read-only, а отсутствие candidate при decisionSequence0 нельзя считать доказанной D2. Phantom вручную не управлялся.

Два envelope/census snapshots отсутствуют. currentActionGuard/runtimeReason/travelReason/travelFailureReason, native sequence/gain/loot deltas не captured. Replay не запускался: итоговый trace SLOW/recorded0. Diagnosis ambiguity привела к предусмотренному STOP, без fix и второго manual login. Materialization architecture повторно не исследовалась.

## Проверки и shutdown

- `ant -q jar` на exact base: PASS, 17 секунд. Runtime Game/Login JAR hashes совпали с этой сборкой.
- Конфигурация1280/8/8/10000, Diagnostics=True, AutoAttach TestAdmin, Synthetic=False, GM hide/invisible/invulnerable/silence=False подтверждена. Fresh clone observe019a содержит10000 profiles.
- TASK018 принят пользователем. Новые focused/regression suites, RED/GREEN, broad production-materialization rerun и observe019b не запускались: fix gate не достигнут. Старый NATIVE_WORK_SELF_DRAIN заново не классифицирован; native-drain semantics не менялись.
- После «вышел»: TestAdmin и total online0, level12/exp138026/sp13880/XYZ49216,42751,-3491. Exact поля до/после shutdown совпали.
- Game PID22488/Login PID19784 завершены stock graceful shutdown; processes0/ports0/original304hashes preserved/forceFalse подтверждены. Login attach-helper Premature EOF записан, независимая проверка подтверждает фактическую остановку.
- Production/test/build diff отсутствует. Другие хроники не менялись. Schema/migrations/direct PLAY repair отсутствуют. Исходный PLAY только прочитан для fresh clone и проверки сохранности.

Mojibake-маркеры в изменённых файлах проверены — совпадений нет.
Escaped Cyrillic в изменённых файлах проверены — совпадений нет.

## Артефакты и scope

Task folder: docs/phantoms/tasks/PHANTOM-M1-VISIBLE-FARM-019. Исходный пакет11, VISIBLE_DIAGNOSIS.md, EVIDENCE019A.json, RESULT.md, PROGRESS.md, GIT_USAGE.md, BASE_BUILD.log, GRACEFUL_STOP.log, STOP_VERIFY.log, LOGOUT_BEFORE_STOP.tsv, LOGOUT_AFTER_STOP.tsv и этот отчёт составляют exact artifact allowlist. Bounded exception по числу файлов относится к одной report/evidence artifact family; semantic production files=0. Runtime helper scripts остаются локальными и не входят в commit. Raw private captures, credentials, конфиги runtime и бинарники не staged. В STOP_VERIFY.log удалена только посторонняя строка AllowedAccounts; raw копия сохранена private.

Read-first: objective/package, HighFive AGENTS.md, master plan/workflow/package standard, root README/build.xml, TASK018 result и Prepare/Observe/Stop analogs, existing PilotActions/Service, BackgroundDecision/VisibleAutoPlay/DecisionEngine. Корневой и дополнительные AGENTS.md по пути docs отсутствуют; TASK.md у TASK019 отсутствует, источники требований — GOAL/PLAN/DIAGNOSIS/FIX. ВерсииJDK25/Ant и native gameplay owners сохранены.

Branch назначения: experiment/m1-candidate007-observe008. Изоляция detached на d153de95fd0fd8b678f975964179aeba85c57336 создана по разрешённому пути, основной checkout и занятая dirty локальная experiment-ветка не переключались. Git использовался по прямому objective/GIT.md; точные команды — GIT_USAGE.md.

Code SHA: d153de95fd0fd8b678f975964179aeba85c57336.
Основной artifact commit: 010b3bed2f61736837942fb4c4a77e66ff49d2bf.
Normal push: PASS, remote experiment/m1-candidate007-observe008=010b3bed2f61736837942fb4c4a77e66ff49d2bf. Дополнительный receipt commit фиксирует этот проверенный SHA и результат; production не меняется.

Следующая граница: отдельно согласовать наблюдение, сохраняющее неподвижность TestAdmin и дающее требуемый census. Root cause visible farm пока не установлен. Второй fix, death repair, следующий goal/slice и автоматическое продолжение не выполняются.
