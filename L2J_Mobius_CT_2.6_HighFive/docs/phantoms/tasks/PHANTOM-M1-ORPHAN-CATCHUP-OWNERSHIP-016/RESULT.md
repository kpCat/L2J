# RESULT016

TASK_RESULT=PARTIAL_RUNTIME_MINIMUM_NOT_REACHED
BASE=685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d
RUNTIME_CODE_SHA=aaffa76249e919b170ca91f37bcd4fc968e14e97
M1=OPEN
AUTOMATIC_CONTINUATION=false

1. Population-wide retained observe014 census: 10000 MANAGED profiles, 30000 exact canonical roundtrips. OWNED_EXACT=7800; COMPLETE_HISTORY_IDLE=2145; ORPHAN_EXACT_ADOPTABLE=55; ORPHAN_CONFLICT=0. Request IDs опубликованы только как 12-hex SHA256 prefixes. Offline adoptable означает persisted contract; owner/safeBoundary/CAS дополнительно проверяются при runtime adoption.
2. Profile110: ORPHAN_EXACT_ADOPTABLE, from=cursor=29852110, target=29852115, seedMatch=true, ecologyVersion=8001/historicalVersion=20153. Profile175: ORPHAN_EXACT_ADOPTABLE, from=cursor=29852109, target=29852115, seedMatch=true, ecologyVersion=7214/historicalVersion=12948. STOP BLOCKED_ORPHAN_CONFLICT не возник.
3. Generic recovery реализована только в PhantomPopulationEcologyService: state.beginRequest(existingHistorical.requestId, existingHistorical.target). Historical component/cursor/requestId и ecology cursor не меняются recovery. После adoption текущий worker turn заканчивается; следующий bounded turn использует обычный advanceRequest и существующий task011 native-context gate. RED 21/30 → GREEN 30/30, прежние18 controls сохранены; handoff6/6 GREEN; ant -q jar GREEN. O01–O12 deterministic. Два существующих javac removal warning вне scope.
4. Fresh l2jmobiush5_localplay_observe016: target1280/active8/maxMaterialized8/maxScheduled10000, diagnostics=True, TestAdmin accesslevel100, Pilot AutoAttach, Synthetic=False, GM hide/invisible/invulnerable/silence=False. Ручной вход пользователя подтверждён: IN_GAME + REAL_LOGIN + ARMED_IDLE через admitted STATUS. Один BEGIN/END: 49.9970473s по server monotonic clock, 1423/1423 events, dropped0. Ни READY_PASS, ни MATERIALIZE_CALL, ни MATERIALIZE_RESULT, ни MAT_WORLD_SPAWN не зарегистрированы. Runtime minimum FAIL. Highest reached boundary: SCHED_BOUNDARY_PLAN → READY_PRESENCE_OFFLINE → SCHED_BOUNDARY_RESULT DEFERRED.
5. Exact first false edge: profile110 seq282 MATERIALIZE/ACTIVE execute → seq283 READY_PRESENCE_OFFLINE OFFLINE/DEFERRED presence.no_current_local_demand → seq284 MATERIALIZE/DEFERRED. В live readiness guard presence.isOnline(profileId)=false до ecology.requestMaterializationDue. Это доказанный boolean, причина presence policy не установлена. Все392 watched LOCAL_CANDIDATE samples OFFLINE; 49 human summaries показывают humanCount=1 и worldPhantomCount=0. Это sampled evidence, natural worldPresent=true не доказано. Post-MATERIALIZE_CALL blocker не классифицируется: CALL не было. Farm/combat не менялись; повторный trace не запускался.
6. Пользователь подтвердил выход до character select. До и после graceful stop: TestAdmin online=0, level12/exp138026/sp13880/x44131/y42673/z-3488, total online0; данные exact одинаковы. GameServer29940 и LoginServer26548 завершились stock graceful shutdown; force не применялся. После stop процессов0/слушающих server ports0. Все304 сохранённых hash основной runtime-копии совпали.

Current PLAY census SELECT-only: OWNED_EXACT7338/COMPLETE_HISTORY_IDLE2662/orphans0/conflicts0. Fresh clone before startup также без orphan;110/175 COMPLETE_HISTORY_IDLE. Поэтому runtime adoption существующего orphan в этом fixture не доказана; deterministic recovery tests GREEN. Retained TASK015 и основной checkout read-only, PLAY repair DML отсутствует.

Exporter первоначально включил timing.json в numeric page sort; после уже выполненного END исправлен только glob в Recover-Trace.py. Frozen mailbox экспортирован без второго BEGIN: CAUSAL_TRACE.tsv/CAUSAL_META.json, TRACE_EXPORT.log. Первоначальная ошибка сохранена в RUNTIME_TRACE.log. Post-stop первая проверка сравнила SELECT с заголовками и без; повтор с одинаковым --skip-column-names подтвердил exact сохранение данных.

Engineering evidence: RED_RESULTS.txt, ECOLOGY_GREEN_RESULTS.txt, HANDOFF_GREEN_RESULTS.txt, GREEN_FINAL.log. Runtime evidence: FIRST_LOST_EDGE.md, CAUSAL_EVENT_COUNTS.tsv, CAUSAL_FACTS.json, LOGOUT_BEFORE_STOP.tsv, LOGOUT_AFTER_STOP.tsv, GRACEFUL_STOP.log, POST_STOP_VERIFY.log. Git использован по прямому разрешению пользователя/TASK016/GIT.md; команды в GIT_USAGE.md. Exact-path commits и normal push той же experiment branch, без force/history rewrite. Финальный commit SHA сообщается после push.

Mojibake-маркеры в изменённых файлах проверены; совпадений нет.
Escaped Cyrillic в изменённых файлах проверены; совпадений нет.

M1 остаётся OPEN. TASK016 остановлен после bounded observation и безопасного shutdown; automatic continuation отсутствует.
