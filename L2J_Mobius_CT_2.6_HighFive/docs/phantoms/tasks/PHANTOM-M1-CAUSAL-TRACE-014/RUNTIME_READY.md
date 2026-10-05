# observe014 — required manual login gate

Source commit `af45c122db3`; normal push confirmed to `origin/experiment/m1-candidate007-observe008` from exact base `ccacd6c5bf8fa1234a5559ece708ee3a35efd536`.
Working checkout: `C:/Users/ZBook/.codex/worktrees/m1-causal-014/L2J_Mobius`, local branch `codex/m1-causal-014` because the experiment local branch is occupied by another worktree. Original checkout and its foreign diff untouched.

Fresh database: `l2jmobiush5_localplay_observe014`; 10002 characters, 10000 profiles, 50000 components. Before startup TestAdmin: objectId=268492939, accesslevel=100, online=0, level=12, exp=138026, sp=13880, x=44131, y=42673, z=-3488.
Original PLAY: 304 config/libs/geodata/PID-record file hashes preserved by preparation.

CONFIG PASS: population=1280, active=8, materialized cap=8, scheduler profiles=10000, diagnostics=True. Pilot enabled, AutoAttach TestAdmin, Synthetic=False. GMStartupBuilderHide/Invulnerable/Invisible/Silence=False.
Pinned catalogs restored from exact Git blobs only in the private runtime, after checking equality ignoring checkout CRLF.
GameServer jar SHA256: `39DC73EEE44D8D67B8BA9F552538A007CBD5A416E67F56B12E024FCB0C0CF4B5`.

2026-10-06 01:30:24 Europe/Chisinau: GameServer loaded, registered on LoginServer as Server 1 Bartz.
Ownership check: LoginServer RUNNING pid=19644, ports2106/9014 owned; GameServer RUNNING pid=30124, port7777 owned.
JFR.check: recording1 name=observe014, settings=profile, maxsize=128MB, maxage=20m, running, dumponexit=true; private observe014.jfr destination.
Startup Phantom/materialization diagnostics exist. They are not sufficient to name FIRST_LOST_EDGE and no cause or suspect shortlist is inferred from them.

Recorder tests: 3/3 GREEN; affected scheduler/local-priority/topology-perception and jar Ant exit0. Scope/diff checks passed.
Mojibake markers in changed files checked: no matches.
Escaped Cyrillic in changed files checked: no matches.

STOP at manual gate: ask owner to enter TestAdmin and say «в игре». No BEGIN, observation or gameplay action has been issued. Recorder remains inactive until consented BEGIN.
After owner confirmation: verify IN_GAME/REAL_LOGIN/ARMED_IDLE; BEGIN; 90-second causal trace; SNAPSHOT/END; one FIRST_LOST_EDGE and <=12 edge-related suspects. No fix or runtime bisect.
Before shutdown request character select, verify online=0 and saved level/exp/sp/x/y/z, then exact-owned stock graceful stop only. Private Stop-LocalPlay wrapper has no force fallback.
M1=OPEN. Task014 diagnosis pending; not GREEN.
