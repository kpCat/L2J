# PLAN

Target <=90 minutes plus one manual login gate.
No subagents.

## A — ownership census <=20m

1. Verify exact base/branch in new isolated worktree.
2. Read TASK014 causal evidence.
3. Read local TASK015 evidence read-only if present.
4. Read retained observe014 DB only.
5. Produce OWNERSHIP_CENSUS.tsv and summary.
6. For profiles110/175 evaluate O01..O11 as far as persisted/runtime-independent facts allow.

If either has from/target/seed conflict:
STOP `BLOCKED_ORPHAN_CONFLICT`.
No production edit.

## B — deterministic RED->GREEN <=30m

1. Add O01..O12 tests.
2. Obtain RED for exact orphan adoption absence.
3. Implement only EcologyService adoption.
4. Focused GREEN.
5. Ecology existing suite GREEN.
6. Handoff 6/6 GREEN.
7. `ant -q jar`.
8. Exact diff/scope review.
9. Commit + normal push same experiment branch.

No WORLD/full suite.

## C — fresh runtime <=30m

1. Fresh clone `l2jmobiush5_localplay_observe016` from current PLAY via established
   SELECT/export pattern. PLAY is not repaired/mutated.
2. Effective runtime:
   target1280 / active8 / maxMaterialized8 / maxScheduled10000;
   diagnostics ON;
   TestAdmin accesslevel100;
   Pilot autoattach TestAdmin;
   Synthetic=False;
   GM startup hide/invis/invul/silence=False.
3. Before user login, record clone ownership classification for profiles110/175.
4. Start server.
5. Allow worker recovery to run; record whether ecology adopts exact orphan(s).
6. Ask:
   `Сервер готов. Войди вручную TestAdmin и напиши "в игре".`
7. WAIT.
8. Verify IN_GAME/REAL_LOGIN/ARMED_IDLE.
9. BEGIN causal trace for max60 seconds.

Required runtime progression:
`ECOLOGY_ORPHAN_ADOPTED` (if orphan still exists in clone)
-> local signal/promotion
-> READY_PASS
-> MATERIALIZE_CALL.

Preferred:
`MATERIALIZE_RESULT SUCCESS`
-> `MAT_WORLD_SPAWN`
-> worldPresent=true.

If a natural Phantom reaches World:
observe max2 additional minutes only.
Do not fix combat/farm in this task.

If next boundary occurs:
record exact trace edge and STOP.

## D — safe shutdown/report <=10m

If TestAdmin online:
ask user to exit to character select and WAIT.
Verify online=0 + persisted level/exp/sp/x/y/z.
Then graceful stop exact owned Game/Login.
No force without separate live permission.

Publish RESULT/HANDOFF.
M1 remains OPEN.
