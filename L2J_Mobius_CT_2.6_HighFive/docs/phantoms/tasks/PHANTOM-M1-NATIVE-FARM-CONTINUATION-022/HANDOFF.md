# Заполняемый итог: один компактный RESULT + HANDOFF

Создать RESULT.md с полями:

TASK_RESULT=GREEN|BLOCKED|FAILED
STOP_CLASS=NONE|BUDGET|FARM|LIFECYCLE|ARCHITECTURE|DATA_SAFETY
STOP_AUTHORITY=TASK022_CONTRACT|EXPLICIT_USER_MESSAGE
BASE_SHA=
CODE_SHA=
ENGINEERING_PASS=
ROOT_CAUSES_PROVEN=
CAUSAL_ORDER=
SEMANTIC_ROUNDS=
CORE_PATHS_CHANGED_WITH_PROOF=
TESTS_AND_REGRESSIONS=
TEST_RESTORE_VERIFIED=
PLAY_WRITES=0
CONNECTED_EPISODES=
PRIMARY_PROFILE_OBJECT_EPOCH=
ACTUAL_OBSERVATION_SECONDS=
SAMPLE_COUNT=
DECISION_COMPLETION_FRESH=
AUTO_PLAY_USE_REGISTRATION_STATE=
FIRST_NATIVE_FAILURE=
FIRST_UNPROVEN_REASON=
NATIVE_PHASE_REASON=
NATIVE_KILL_REWARD_NEXTTARGET_CYCLES=
ACTUAL_EXP_SP_DELTA=
ACTUAL_LOOT_INVENTORY_DELTA=
FARM_PASS=
COHORT_OUTCOMES=
NATIVE_OUTSTANDING_AFTER_STOP=
RETAINED_MATERIALIZATION_ENTRIES=
PHANTOM_DRAIN_COMPLETE=
TESTADMIN_SAVE_VERIFIED=
PROCESSES_PORTS_ZERO=
FORCE_USED=false
CLEANUP_PASS=
M1=OPEN

В HANDOFF_RESULT.md максимум1–2страницы: что именно исправлено, лучший полный native
эпизод, отдельная remaining boundary, source SHA+artifacts. Не предлагать произвольный
новый аудит/профилировщик. Не обещать количество оставшихся задач.

PATCH_LEDGER.tsv: файл, метод, semantic/diagnostic, RED-test, causal evidence, regression.
Sources/raw logs сохранять исходными. В published evidence — компактная причинная таблица,
до4actor summaries, важные exception/lock excerpts; полные большие stdout/dumps/JFR только
локально с hashes. Публикация сотенкилобайт одинакового init не добавляет доказательности.
