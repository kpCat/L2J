# PLAN

Target <=55 minutes plus one manual login/logout gate.
No subagents.

## A — exact source review <=10m

1. Verify exact branch/head.
2. Read TASK016 RESULT/FIRST_LOST_EDGE and current locality hook.
3. State in `DIAGNOSIS.md` why:
   watched OFFLINE profiles != final local candidates.
4. Confirm summary `localCandidateCount=9` comes after online filtering.

No product semantic theory beyond that.

## B — diagnostic correction <=15m

1. Move recorder watch admission to accepted ONLINE candidates only.
2. Add optional global scalar filter summary only if needed.
3. Add T01-T05 regression.
4. Existing recorder tests GREEN.
5. `ant -q jar`.
6. Review diff to prove no gameplay return/branch/state behavior changed.
7. Exact-path commit + normal push same experiment branch.

## C — fresh observe017 <=20m

1. Fresh clone `l2jmobiush5_localplay_observe017` from current PLAY using existing
   SELECT/export clone pattern.
2. Effective runtime:
   target1280 / active8 / maxMaterialized8 / maxScheduled10000;
   diagnostics=True;
   TestAdmin accesslevel100;
   Pilot AutoAttach;
   Synthetic=False;
   GM hide/invisible/invulnerable/silence=False.
3. Start server and ask:
   `Сервер готов. Войди вручную TestAdmin и напиши "в игре".`
4. WAIT.
5. Verify IN_GAME + REAL_LOGIN + ARMED_IDLE.
6. BEGIN causal trace.
7. Observe max45 seconds.
8. Trace must follow accepted ONLINE/local candidates, not first offline probes.

Stop early if one accepted profile reaches:
- MAT_WORLD_SPAWN, or
- a repeated exact blocker >=10 times.

Do not fix blocker.

## D — diagnosis/cleanup <=10m

For one accepted human-local profile report ordered highest edge:
LOCAL_CANDIDATE ONLINE
-> LOCAL physical demand
-> LOCAL_SIGNAL_RESULT human.local
-> SCHED signal/promotion
-> READY_*
-> MATERIALIZE_CALL
-> MATERIALIZE_RESULT
-> MAT_*.

Name FIRST_LOST_EDGE from this accepted path only.

Before shutdown:
- ask user exit to character select;
- verify online=0 + exact saved level/exp/sp/x/y/z;
- graceful stop exact owned server;
- no force without live permission.

Publish RESULT/HANDOFF and stop.
M1=OPEN.
