# observe016 — required manual login gate

TASK_RESULT=ENGINEERING_GREEN_WAITING_MANUAL_LOGIN
CODE_SHA=aaffa76249e919b170ca91f37bcd4fc968e14e97
GAME_JAR_SHA256=A7C2D07D34F8C6214E8D6D6EF460A6552F9B183D1F8557EFBD60F6738137877A
DATABASE=l2jmobiush5_localplay_observe016
LOGIN_PID=26548; PORTS=2106,9014; OWNERSHIP=VERIFIED
GAME_PID=29940; PORT=7777; OWNERSHIP=VERIFIED
CONFIG=1280/8/8/10000; diagnostics=True; TestAdmin accesslevel100; Pilot AutoAttach; Synthetic=False; GM hide/invisible/invulnerable/silence=False
CLONE_BEFORE=10000 profiles; OWNED_EXACT7338; COMPLETE_HISTORY_IDLE2662; orphans0; conflicts0
PROFILE110_BEFORE=COMPLETE_HISTORY_IDLE; seedMatch=true
PROFILE175_BEFORE=COMPLETE_HISTORY_IDLE; seedMatch=true
ORPHAN_ADOPT_EVENTS=NOT_APPLICABLE_IF_NO_ORPHAN; no artificial orphan created
PILOT=OFF_BEFORE_MANUAL_LOGIN; BEGIN_NOT_ISSUED
TESTADMIN_BEFORE=online0; level12; exp138026; sp13880; x44131; y42673; z-3488
ORIGINAL_PLAY_FILES=304_HASHES_PRESERVED

STOP: требуется ручной вход TestAdmin и ответ «в игре». До ответа IN_GAME/REAL_LOGIN/ARMED_IDLE и BEGIN не выполняются.
После ответа: verify IN_GAME/REAL_LOGIN/ARMED_IDLE; BEGIN; bounded trace до60 seconds; exact next edge; без farm/combat fix.
Перед shutdown: required manual character-select gate, online0 и сохранённые level/exp/sp/x/y/z, затем exact-owned stock graceful stop. Force запрещён без отдельного разрешения.
M1=OPEN; automatic continuation отсутствует.
