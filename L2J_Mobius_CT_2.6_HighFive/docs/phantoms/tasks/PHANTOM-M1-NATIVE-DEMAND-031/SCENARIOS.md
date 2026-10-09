# Проверки031

S01: READY/online exact pending native-context handoff, PNC FARM=false,
dueSnapshot.complete=false. Старый setup отвергает; новый может выбрать МЕСТО.
Только actual demand+production guards могут выдать native request. Новая таблица
setup сама по себе не является положительным native proof.
S02: тот же профиль с чужим request/epoch/pending owned receipt — native admission
продолжает запрещаться, хотя observer вправе смотреть в эту область.
S03: INCONSISTENT/DEAD не healthy кандидаты, не исчезают из census/report.
S04: ONLINE=false сохраняется false, не имитировать присутствие/календарь.
S05: existing ordinary ready case не ухудшается; fixed IDs не выбираются по kills.
S06: первая реальная SQLException даёт class/SQLState/vendor/stack/stage, native
return/rollback прежние. RuntimeException не выдаётся за SQLException.
S07: unrelated startup errors не вытесняют foreground first events; coalescing
не стирает первый root. При потере нужного события diagnosis incomplete; admission прежний.
S08: OFF diagnostic parity; owner/state/goal/cursor/PNC/evidence unchanged.
S09: cached generic failure не считается новым exception; onset timestamp должен
относиться к текущей JVM. Baseline-invalid отдельно от first-current-transition.
S10: actual probe имеет живого observer, current point, реальный human-local signal
и materialization lifecycle без direct calls. Нет demand — это отдельный результат.
S11: каждый enrolled actor сохраняется в результате; если smoke удачен — native
cycle/EXP/SP deltas и epoch, иначе точный first edge. Подставлять unit counts нельзя.

Outcome fields:
TASK_RESULT=GREEN/BLOCKED/FAILED
DIAGNOSIS_PASS=true/false
SETUP_CONTRACT_MISMATCH=PROVEN/DISPROVEN/UNPROVEN
CURRENT_FIRST_CAUSE=<stage,class,cause,source path:line; либо UNKNOWN с причиной>
BASELINE_INVALID_COUNTS=<не смешивать с current failures>
NATURAL_WORLD_PRESENT=<count>
NATIVE_FARM_SMOKE=<deltas; NOT_RUN допустим, не PASS>
SAFETY_GATES_UNCHANGED=true/false
CLEANUP_PASS=true/false
M1=OPEN

GREEN031 только если устранён ошибочный setup AND actual causal path получен:
либо natural World/native progression, либо exact current first exception/typed
неисправный переход с доказанной причинностью. Один переписанный helper недостаточен.
Даже GREEN031 означает диагностическую/контрактную развилку, НЕ серверную приёмкуM1.

S12: собственная GameServer JVM с loopback JDWP либо зафиксированным невозможным
включением без изменения shared launcher. `VM.command_line` подтверждает exact PID.
S13: `jcmd Thread.print -l`/JFR capture выполняются без пересборки и не изменяют
backend failure/ownership. Пропуск JFR или отсутствие stack не заменяется выдуманным
SQLException: логический false имеет свой exact guard proof.
S14: debugger suspend timestamps маркированы и исключены из watchdog/farm timing
acceptance; baseline и final source SHA не подменяют HotSwap.
S15: HotSwap, если вообще применялся, меняет только body `DIAGNOSTIC_ONLY` метода;
результат не выдаётся за shipped JAR. Полный семантический fix отложен.
