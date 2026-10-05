# RESULT016 — engineering gate

TASK_RESULT=ENGINEERING_GREEN_RUNTIME_PENDING
BASE=685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d
CENSUS_TOTAL=10000
ORPHAN_EXACT_ADOPTABLE_COUNT=55
ORPHAN_CONFLICT_COUNT=0
PROFILE110_CLASS=ORPHAN_EXACT_ADOPTABLE
PROFILE175_CLASS=ORPHAN_EXACT_ADOPTABLE
RED_GREEN=RED_21_OF_30_TO_GREEN_30_OF_30
ECOLOGY_SUITE=GREEN_30_OF_30
HANDOFF_6_6=GREEN
BUILD=GREEN
USER_LOGIN=PENDING_REQUIRED_MANUAL_GATE
REAL_LOGIN=NOT_YET_VERIFIED
AUTOATTACH=RUNTIME_PENDING
READY_PASS=NOT_YET_OBSERVED
MATERIALIZE_CALL=NOT_YET_OBSERVED
MATERIALIZE_RESULT=NOT_YET_OBSERVED
MAT_WORLD_SPAWN=NOT_YET_OBSERVED
WORLD_PRESENT=NOT_YET_OBSERVED
USER_LOGOUT_SAVE=PENDING
RUNTIME_STOP=PENDING
M1=OPEN

Census покрывает всю retained observe014 population, а не только110/175. Все30000 компонентов прошли exact encode/decode roundtrip. Request IDs опубликованы только как12-hex SHA256 prefixes. Класс adoptable описывает persisted contract; runtime owner/safeBoundary и exact CAS проверяются при adoption.

110: ecologyCursor=historicalFrom=29852110; target=29852115; seedMatch=true; ecologyVersion8001/historicalVersion20153.
175: ecologyCursor=historicalFrom=29852109; target=29852115; seedMatch=true; ecologyVersion7214/historicalVersion12948.

Recovery реализована generic только в PhantomPopulationEcologyService. Historical component, cursor, rewards, goal, inventory, native state и request ID не меняются recovery. Outer cursor остаётся прежним. После adoption worker заканчивает текущий turn; обычный advance и native-context foreground gate работают на следующем bounded turn.

Engineering evidence: RED_RESULTS.txt, ECOLOGY_GREEN_RESULTS.txt, HANDOFF_GREEN_RESULTS.txt, GREEN_FINAL.log. O01–O12 включены в существующую suite. Прежние18 controls и task011 native materialization exception сохранены. Полные WORLD/product suites не запускались. Два существующих javac removal warning вне scope.

Основной checkout и локальный TASK015 использовались только read-only. Работа выполняется в явно разрешённом isolated worktree. Git-команды использовались по прямому разрешению пользователя и GIT.md; точные команды приведены в GIT_USAGE.md.

Mojibake-маркеры в изменённых файлах проверены; совпадений нет.
Escaped Cyrillic в изменённых файлах проверен; совпадений нет.

Current PLAY comparison выполнено SELECT-only:10000 profiles,30000 exact roundtrips, OWNED_EXACT7338/COMPLETE_HISTORY_IDLE2662/orphans0/conflicts0. Только classification counts опубликованы; PLAY не изменён. Fresh observe016 создаётся из current PLAY; adoption runtime evidence условно применимо только при наличии orphan в clone.

Runtime PASS пока не заявляется. Следующий required gate — ручной вход TestAdmin после readiness fresh observe016. Перед shutdown требуется отдельный ручной выход до character select и проверка online=0/сохранённых level,exp,sp,x,y,z. M1 остаётся OPEN.
