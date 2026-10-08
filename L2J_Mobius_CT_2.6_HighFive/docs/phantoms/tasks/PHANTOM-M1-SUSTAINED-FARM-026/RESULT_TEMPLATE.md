# RESULT026

```text
TASK_RESULT=GREEN|FAILED|BLOCKED
STOP_AUTHORITY=TASK026_CONTRACT
BASE_SHA=6ebe1d93f4ee168cd8952f0416dc920f26c430ac
FINAL_CODE_SHA=
REMOTE_HEAD=
WALL_MINUTES=
FIRST_RUNTIME_PROBE_MINUTE=
PRODUCTION_PATHS=
N02_S12_FIRST_DIVERGENCE=
N02_S12_PAIRED_RESULT=
CALLBACK_FIRST_PRODUCER=
CALLBACK_DRAIN_PASS=
FARM_A_PASS=
FARM_B_PASS=
COHORT_PASS=
RESOURCE_RECOVERY_PASS=
LOCAL_ACTION_PASS=
LOOT_PASS=
WHOLE_GROUP_PERSISTENCE_PASS=
COLD452_REGRESSION=
SAME_DB_RESTART1=
SAME_DB_RESTART2=
RECOVERY_COMMIT_XYZ_EXACT_PASS=
POST_RECOVERY_XYZ_FIRST_WRITER=
AFTER_FINALIZE_CRASH_PASS=
DEATH_RETURN_FARM=
SOFT_RETURN_FARM=
RETAINED_AT_FIRST_HEALTHY_STOP=
PENDING_OWNED_STORE=
OWNED_JVMS_LISTENERS=
FORCE_USED=
PLAY_WRITES=0
REAL_FINAL_PASS=NOT_RUN
M1=OPEN|WAITING_FINAL_CLIENT
```

Таблица всех cohort actors: initial identity/epoch, missing/change/death, cycles,
EXP/SP, tail120, maxIdle, exact checkpoint/callback/local failure. Причины — доказано,
гипотеза или UNKNOWN. Уцелевшая ранняя статистика не заменяет final outcome.

Изменённые ожидания tests отдельно; каждое с первичным evidence. Полная матрица
regressions. Ссылки на обязательные и отрицательные raw logs. Список остатка M1.
MORNING: точная проверенная команда старта preserved same DB, source/JAR hashes,
runtime по умолчанию STOPPED; не предложение продолжить исполнять неизвестный patch.
