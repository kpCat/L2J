# Итоговый RESULT — заполнить в конце, без дублирования receipt commits

TASK_RESULT=
BASE_SHA=2bf2936083bf6b081104c3e03c254ab8f07afc87
CODE_SHA=
REMOTE_HEAD=
ROOT_PRODUCER=PROVEN|NOT_PROVEN (конкретные источники)
STALE_GOAL_REPRODUCER=
TERMINAL_RECOVERY_REPRODUCER=
CURRENT_GOAL_INITIAL/FINAL=
RUNTIME_GOAL_INITIAL/FINAL=
OWNED_ATOMIC_REPLAN=
REGRESSIONS=
KNOWN_UNRESOLVED_TESTS=NATIVE_WORK_SELF_DRAIN (не исправлялся)
BUILD/JAR_HASHES=
OBSERVED_PROFILE/OBJECT/EPOCH=
ACTUAL_OBSERVATION_SECONDS=
NATIVE_DAMAGE/KILL/REWARD/CYCLE_DELTAS=
NATIVE_EXP/SP/LOOT_DELTAS=
NATIVE_OVERFLOW=
LOCAL_TARGET_ATTEMPTS=
FIRST_UNSAFE_SEGMENT_WITNESS=
LAST_TERMINAL_BINDING=
COHORT_RESULTS=
USER_MOVED_BY_DIAGNOSTICS=false
GUARDED_TEST_RESTORED=
PLAY_WRITES=0
LOGOUT_SAVE=
GRACEFUL_STOP=
M1=OPEN

Приложить только компактные primary evidence: RED/GREEN summaries, exact compare of
immutable catchup fields до/после replan, пары baseline/final census+native counters,
actual route witness, timestamps и shutdown. Не давать новый estimate «ещё N задач».
В один абзац: исправлено ли согласование версии; как завершился отказ маршрута; работает
ли реальный farm; что конкретно осталось. После push остановиться.
