# TASK017 manual login gate

Fresh database: l2jmobiush5_localplay_observe017, SELECT/export clone PLAY.
Code SHA: f8279bdac3be114c25f6ad1a4b7ebc02dbd6f831, normal push подтверждён.
LoginServer PID1740: RUNNING, ports2106/9014 owned.
GameServer PID16016: RUNNING, port7777 owned.
Check-LocalPlay: CONFIG PASS, target1280, active8, maxMaterialized8,
maxScheduled10000, diagnostics=True.
Pilot enabled/AutoAttach TestAdmin, Synthetic=False.
TestAdmin accesslevel100, GM hide/invisible/invulnerable/silence=False.
Original runtime preservation: 304/304 hashes unchanged после preparation.

Locality regression3/3, existing recorder3/3, ant -q jar GREEN.
Scope/source/mojibake/escaped Cyrillic guards PASS перед engineering commit.
Fresh causal BEGIN ещё не выполнен. Ожидается ручной ответ пользователя «в игре».
После ответа выполнить Observe-Causal.ps1 ровно один раз: admitted STATUS
IN_GAME/REAL_LOGIN/ARMED_IDLE, max45s, accepted ONLINE/physical LOCAL profiles.
Stop early при MAT_WORLD_SPAWN либо exact blocker одного profile >=10.
Blocker не чинить. До shutdown обязательный manual character-select logout,
online0/saved fields, stock graceful stop без force.

Runtime/evidence edits после engineering commit пока не опубликованы; финальный
exact-path artifact commit/push выполняется после trace/logout/stop.
M1=OPEN. Automatic continuation=false. Работа остановлена на manual login gate.
