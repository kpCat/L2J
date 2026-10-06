# ACCEPTANCE

Engineering GREEN:
- typed NATIVE_CONTEXT_HANDOFF exists;
- exact request claim minted only by existing task011 native-required gate;
- NORMAL non-COMPLETE catchup remains fenced;
- wrong/stale claim remains fenced;
- HistoricalBackground and Background lifecycle verify exact claim;
- no catchup completion/progress fabrication;
- H01-H14 GREEN;
- ecology30/30;
- handoff6/6;
- recorder3/3;
- jar GREEN.

Runtime minimum:
- accepted online/local profile reaches MATERIALIZE_CALL service.NATIVE_CONTEXT_HANDOFF;
- no normal-fence rejection for exact claim;
- MAT_PLAYER_LOAD_BEGIN reached.

Preferred:
- MATERIALIZE_RESULT SUCCESS;
- MAT_WORLD_SPAWN;
- worldPresent=true.

If post-load fails:
TASK_RESULT=BLOCKED_NEXT_EDGE, exact MAT_* edge, STOP.

If exact prestate does not support contract:
BLOCKED_CONTRACT_MISMATCH.

M1 remains OPEN.
