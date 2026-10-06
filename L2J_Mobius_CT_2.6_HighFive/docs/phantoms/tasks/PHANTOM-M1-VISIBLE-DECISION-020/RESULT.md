# RESULT020

TASK_RESULT=BLOCKED_NEXT_VISIBLE_EDGE
ADMISSION_ROOT_PROVEN=true
RED=15/18; expected V02/V17/V18 failure
GREEN=18/18; TASK018=14/14
ECOLOGY=30/30; POPULATION_HANDOFF=6/6
RECORDER=3/3; BACKGROUND_LIFECYCLE=4/4
DECISION_CORE=36/36; DECISION_PERSISTENCE=23/23
BUILD=ant -q jar GREEN
VISIBLE_FARM_PASS=false
NEXT_BLOCKER=D6 VISIBLE_TRAVEL_BLOCK
NEXT_BLOCKER_REASON=travel.native_segment_water_entry
DECISION_PRE=0 / goal.reloaded (retained accepted TASK019)
DECISION_POST=444 / step.retry_exhausted (fresh observe020)
SAME_EPOCH_NATIVE_DELTAS=0; cycles/kills/rewards=0; EXP/SP=0
LOOT_DELTA=0; NO_LOOT_OBSERVED
TESTADMIN_DIAGNOSTIC_MOVEMENT=0; target/moving/teleporting unchanged
TESTADMIN_LOGOUT_SAVE=PASS; online=0; exact saved fields unchanged
GRACEFUL_STOP=PASS_STOCK; processes=0; ports=0; force=false; originalHashes=304
M1=OPEN

The two successful census receipts span only 1.790001 seconds in the same object/epoch.
They show identical native counters and overflow=false. Trace history independently
shows decision progression 442 -> 443 -> 444 in the same selected visible lifetime.
The later poll was refused before a runtime request because the 120s limit elapsed;
no full 120s observation, restart or continued farm-success claim is made.

The existing visible-start reached travel.arrive and retried. Travel records terminal
travel.native_segment_water_entry / failureSequence1. The route safety check rejects
a water cell following a dry cell, before issuing the unsafe native movement.
The terminal attempt returns false on subsequent arrivals, preventing AutoPlay start.
Canonical goal revision29 differs from runtime revision28; the census reports
DECISION_GOAL_MISMATCH. This also matters to the exact-goal travel-failure binding,
and is recorded as follow-up evidence without a second semantic change in TASK020.

User replied «вышел». Saved TestAdmin level12/EXP138026/SP13880/XYZ44131,42673,-3488 exactly matches before/after shutdown. GameServer stock shutdown completion and LoginServer stock shutdown request receipts confirmed. Login attach client reported Premature EOF while the JVM exited; all owned processes/ports are absent. No force fallback.

Final artifact guard: exact allowlist, separate mojibake-marker and escaped-Cyrillic checks required immediately before publication. Source/test code remains at engineering commit acbe5513; no second semantic fix or next slice.
