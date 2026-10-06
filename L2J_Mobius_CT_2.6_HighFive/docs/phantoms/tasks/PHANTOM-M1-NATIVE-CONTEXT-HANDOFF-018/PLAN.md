# PLAN

Target <=95 minutes plus one manual login/logout gate.
No subagents.

## A — exact contradiction/prestate <=15m

1. Verify exact base/branch.
2. Read TASK017 FIRST_LOST_EDGE/RESULT.
3. Read current task011 gate and HistoricalBackground NORMAL fence.
4. Read retained observe017 clone read-only for profiles110/142/175:
   catchup status/request/from/target;
   ecology ownership;
   background state;
   native-context phase/eligibility;
   goal id/revision/status.
5. Write HANDOFF_PRESTATE.tsv.

If profiles110/142/175 do not support exact task011 native-context handoff relation:
STOP BLOCKED_CONTRACT_MISMATCH.

## B — deterministic RED <=15m

Create integrated handoff suite.

Required RED:
task011 native permit reaches readiness success, then current NORMAL materialization is
rejected by catchup.normal_fenced.

Do not fabricate unrelated exceptions.

Add diagnostics-only preservation of lifecycle AdmissionRejectedException message.

## C — implementation <=30m

Implement CONTRACT.md.

Keep requestId typed; never parse it from reason text.

Do not touch Player, ThreadPool, AutoPlay/AutoUse, DB schema, combat/movement or b4 store
semantics.

Run H01-H14 + regressions + jar.
Exact diff review.

Commit + normal push same experiment branch.

## D — fresh observe018 <=25m

Fresh clone:
`l2jmobiush5_localplay_observe018`

Runtime:
target1280 / active8 / maxMaterialized8 / maxScheduled10000
diagnostics=True
TestAdmin accesslevel100
Pilot AutoAttach
Synthetic=False
GM hide/invisible/invulnerable/silence=False

Ask:
`Сервер готов. Войди вручную TestAdmin и напиши "в игре".`

WAIT.

Verify IN_GAME + REAL_LOGIN + ARMED_IDLE.

BEGIN accepted-online causal trace max60s.

Required chain:
READY_ECOLOGY_DUE COMPLETE/ecology.native_materialization_required
-> READY_PASS
-> MATERIALIZE_CALL service.NATIVE_CONTEXT_HANDOFF
-> no MATERIALIZE_ADMISSION_REJECT catchup.normal_fenced
-> MAT_PLAYER_LOAD_BEGIN.

Preferred:
-> MAT_PLAYER_LOAD_OK
-> MAT_AFTER_PLAYER_LOAD_OK
-> MAT_ONLINE
-> MAT_WORLD_SPAWN
-> MATERIALIZE_RESULT SUCCESS.

Stop on first repeated exact post-handoff blocker >=5.
Do not repair that next blocker.

If worldPresent=true:
record profileId/objectId/epoch/XYZ;
continue passive max120s.
If not client-visible and an existing bounded Pilot helper can safely move ONLY TestAdmin
near that exact live Phantom, one REAL-player teleport is allowed.
Never teleport/target/attack the Phantom.

## E — shutdown/report <=10m

Ask user exit to character select.
WAIT.

Verify online=0 + exact level/exp/sp/x/y/z.
Graceful exact-owned Game/Login stop.
No force without explicit permission.

Publish RESULT/HANDOFF.
M1=OPEN.
