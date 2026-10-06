# TASK019 — текущая граница

Статус: BLOCKED_VISIBLE_DIAGNOSIS / SAFE_STOP_CONFIRMED / ARTIFACT_PUBLICATION_PENDING. M1=OPEN.

Исходный objective прочитан. Subagents не использовались. Production-файлы не изменены.

Isolated worktree: `C:\Users\ZBook\.codex\worktrees\m1-visible-farm-019\L2J_Mobius`.
HEAD: `d153de95fd0fd8b678f975964179aeba85c57336`, detached; существующая локальная experiment-ветка занята другим dirty worktree и не изменялась. Remote `experiment/m1-candidate007-observe008` подтверждён на required base. Финальный normal push должен использовать `HEAD:refs/heads/experiment/m1-candidate007-observe008`.

`ant -q jar`: PASS, 17 секунд, BASE_BUILD.log. Tracked status до сборки чистый.
Fresh clone: `l2jmobiush5_localplay_observe019a`, 10002 characters, 10000 profiles, 50000 components. TestAdmin object268492939/access100/online0/level12/exp138026/sp13880/XYZ44131,42673,-3488 до запуска.
Private runtime: `.phantom-local/observe019a/runtime`. Исходные 304 PLAY hashes сохранены; pinned catalogs подтверждены. Game/Login JAR совпадают с собранным required base.
CONFIG PASS: target1280/active8/maxMaterialized8/maxScheduled10000; Diagnostics=True; AutoAttach TestAdmin; Synthetic=False; GM hide/invisible/invulnerable/silence=False.
Login PID19784 владеет2106/9014; Game PID22488 владеет7777; зарегистрирован Bartz. Pilot до ручного входа OFF.

Startup incident другого actor object268492286: AFTER_IDENTITY_CLAIM, Loaded Player differs from committed background state, maxHp214/197,maxMp82/75,maxCp85/78, refresh state=DEAD. Это не D-class выбранного visible actor; не исправлялось.

Пользователь сообщил «в игре». ARMED_IDLE / REAL_LOGIN / IN_GAME подтверждены. Выбран ближайший visible profile110/object268485779, alive/IDLE/target0/AutoPlayFalse, distance78. Trace t0/+2/+5 снят в 11:56:37.872061900Z, 11:56:39.871991700Z, 11:56:42.875875200Z: attached=true, goal=farm.background/ACTIVE, runtimeState=NEEDS_REPLAN, decisionSequence=0, candidateKey=null, step=-1, attempt=0, lastResult=null, reasonKey=goal.reloaded, recorded0/dropped0. Итоговая health=SLOW, replay не запускался.
PREPARE-initial выбрал тот же profile110/object268485779/epoch15958142073300, но existing LocalPlayPilotActions.java вызвал actor.teleToLocation(outside,false). Агент пропустил этот побочный эффект при initial read. TestAdmin автоматически перемещён с44131,42673,-3488 на49216,42751,-3491, teleporting=true. Phantom не управлялся. Exact PREPARE получил ACTOR_BUSY; capture прерван. Snapshot попытка с тем же run затем получила CANCELLED: run stopped. Session подтвердил stoppedRunId именно этого run. НЕ ПОВТОРЯТЬ observation, PREPARE или новый run.
Два envelope/census snapshots отсутствуют; currentActionGuard/runtimeReason/travelReason/travelFailureReason недоступны. Уникальная D-class не доказана; выбран предусмотренный STOP BLOCKED_VISIBLE_DIAGNOSIS. Fix, тесты RED/GREEN, broad rerun, observe019b запрещены на этой границе и не запускались.
VISIBLE_DIAGNOSIS.md и EVIDENCE019A.json сохранены в task folder. Raw XML/JSON остаются в private `.phantom-local/observe019a/visible-diagnosis`; их не коммитить. Пользователю через async-question предложено выйти до character select и написать «вышел».
Пользователь сообщил «вышел». Stop-and-Verify.ps1 выполнен: TestAdmin online0,totalOnline0,level12/exp138026/sp13880/XYZ49216,42751,-3491. Exact before/after saved fields совпали; Game22488/Login19784 завершены stock graceful shutdown; processes0/ports0/original304hashes preserved/forceFalse. Login attach-helper вывел Premature EOF при завершении JVM, последующая независимая проверка подтвердила фактический stop. RESULT/report готовятся к exact-path publication. Production files остаются без diff. M1=OPEN, без автоматического продолжения.
Bounded file-count exception: исходный TASK019 пакет11 плюс отчёт/evidence/логи образуют одну artifact family. Production/инфраструктура не меняются. Runtime scripts могут остаться локально, private runtime/credentials не staged.

Git использовался согласно GIT.md и прямому objective: bounded branch/HEAD/status/history/worktree/ancestry/upstream/remote inspection; isolated worktree add; Prepare-Runtime/Restore-Private-Catalogs используют rev-parse и git show для идентичности исходников и pinned blob verification. Commit/push ещё не выполнены.
