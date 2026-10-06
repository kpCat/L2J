# RESULT019

TASK_RESULT=BLOCKED_VISIBLE_DIAGNOSIS
BASE=d153de95fd0fd8b678f975964179aeba85c57336
DIAGNOSTIC_PROFILE=110
DIAGNOSTIC_OBJECT=268485779
DIAGNOSTIC_EPOCH=15958142073300 (PREPARE-initial; census отсутствует)
DIAGNOSTIC_CLASS=NOT_PROVEN
TRACE_RUNTIME_STATE=NEEDS_REPLAN
TRACE_CANDIDATE=null
TRACE_STEP=-1
TRACE_ATTEMPT=0
TRACE_LAST_RESULT=null
TRACE_REASON=goal.reloaded
TRACE_DECISION_SEQUENCE=0
TRACE_ATTACHED=true
TRACE_GOAL=farm.background/ACTIVE
CENSUS_ACTION_GUARD=NOT_CAPTURED
CENSUS_RUNTIME_REASON=NOT_CAPTURED
CENSUS_TRAVEL_REASON=NOT_CAPTURED
CENSUS_TRAVEL_FAILURE=NOT_CAPTURED
PRODUCTION_FILES_CHANGED=0
RED_GREEN=NOT_RUN (diagnosis gate не пройден)
HANDOFF018_REGRESSION=NOT_RUN (TASK018 принят пользователем; новый fix отсутствует)
ECOLOGY_30_30=NOT_RUN (fix gate не достигнут)
HANDOFF_6_6=NOT_RUN (fix gate не достигнут)
RECORDER_3_3=NOT_RUN (fix gate не достигнут)
BROAD_MATERIALIZATION=NOT_RUN (post-fix этап не достигнут; старый NATIVE_WORK_SELF_DRAIN заново не классифицирован)
BUILD=ant -q jar PASS на неизменённом base, 17 секунд
CODE_SHA=d153de95fd0fd8b678f975964179aeba85c57336
POSTFIX_PROFILE=NOT_RUN
POSTFIX_OBJECT=NOT_RUN
POSTFIX_EPOCH=NOT_RUN
WORLD_PRESENT=true в pre-fix PHANTOMS
CLIENT_VISIBLE=true в pre-fix PHANTOMS; distance78
AUTOPLAY_OBSERVED=false
TARGET_DELTA=NOT_CAPTURED
DAMAGE_DELTA=NOT_CAPTURED
KILL_DELTA=NOT_CAPTURED
REWARD_DELTA=NOT_CAPTURED
FARM_CYCLE_DELTA=NOT_CAPTURED
EXP_GAIN_DELTA=NOT_CAPTURED
SP_GAIN_DELTA=NOT_CAPTURED
LOOT_DELTA=NOT_CAPTURED
VISIBLE_FARM_PASS=NOT_PROVEN
NEXT_BOUNDARY=требуется отдельно согласованное наблюдение без движения TestAdmin через PREPARE; автоматического продолжения нет
USER_LOGOUT_SAVE=PASS: online0,totalOnline0,level12,exp138026,sp13880,XYZ49216,42751,-3491; exact before/after match
RUNTIME_STOP=PASS: Game22488/Login19784 остановлены штатным shutdown; processes0,ports0,original304hashes preserved,forceFalse
ARTIFACT_COMMIT=PENDING
PUSH=PENDING
M1=OPEN

## Причина остановки

После единственного ручного входа STATUS подтвердил REAL_LOGIN/IN_GAME/ARMED_IDLE. Natural visible profile110 остался alive/IDLE/target0/AutoPlayFalse. Три trace снимка дали attached=true/ACTIVE, но decisionSequence0/NEEDS_REPLAN/goal.reloaded; это не доказывает единственный D-class.

PREPARE-initial выбрал тот же profile110, но автоматически телепортировал TestAdmin к outside-point. Агент не дочитал этот побочный эффект при исходном read-first pass. Exact reprepare сразу получил ACTOR_BUSY. Capture остановился; позже тот же Pilot run был подтверждён terminal и snapshot получил CANCELLED до отправки. Два census snapshots и exact guard/travel fields отсутствуют. Подробности — VISIBLE_DIAGNOSIS.md и EVIDENCE019A.json.

Phantom вручную не двигался/не таргетился/не атаковал. Production, native ownership, native-drain semantics и DB schema не менялись. Второй вход, fix и observe019b не запускались. Login attach-helper вывел Premature EOF на завершении JVM, однако exact PID/порт/store verification подтвердили безопасный stop; force не применялся.

Mojibake-маркеры в изменённых файлах проверены — совпадений нет.
Escaped Cyrillic в изменённых файлах проверены — совпадений нет.

Git использовался по прямому разрешению objective/GIT.md; точные команды и результаты — GIT_USAGE.md. Private runtime/credentials не входят в commit.
