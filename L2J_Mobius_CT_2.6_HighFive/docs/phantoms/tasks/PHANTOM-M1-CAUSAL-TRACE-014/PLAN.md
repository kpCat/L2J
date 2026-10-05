# PLAN

Target <=90 minutes plus one manual login wait.
No subagents.

## A — history + codemap <=25m
1. Verify base/head.
2. Re-read exact historical GOOD docs and source SHA.
3. Enumerate GOOD..BAD commits.
4. Build M1_CRITICAL_PATH_CODEMAP.md.
5. Build initial REGRESSION_MATRIX.tsv.

No source edits before A completes.

## B — recorder <=30m
1. Implement observation-only recorder + hooks.
2. Add targeted tests.
3. Prove hooks do not alter return values, conditions or state decisions.
4. Focused tests GREEN.
5. `ant -q jar`.
6. Exact-path source commit + normal push same experiment branch.

If recorder needs gameplay/state ownership changes: BLOCKED_DESIGN.

## C — observe014 <=25m
1. Fresh clone `l2jmobiush5_localplay_observe014`.
2. Runtime 1280/8/8/10000.
3. `EnablePhantomDiagnostics=True`.
4. TestAdmin accesslevel100 inherited; GM startup hide/invisible/invulnerable/silence all False.
5. Pilot autoattach TestAdmin; Synthetic=False.
6. Start bounded JFR.
7. Start server; ask:
   `Сервер готов. Войди вручную TestAdmin и напиши "в игре".`
8. WAIT.
9. Verify IN_GAME + REAL_LOGIN + ARMED_IDLE.
10. BEGIN causal trace.
11. Observe exactly 90s at persisted TestAdmin location near Elven area.
12. Do not move user unless trace proves zero topology candidates; if so only use the historical Elven prewarm lane already proven in archived evidence.
13. SNAPSHOT/END trace.
14. No gameplay fix even if cause obvious.
15. Before shutdown ask user to exit to character select; verify online=0 and persisted level/exp/sp/x/y/z.
16. Graceful owned stop only.

## D — diagnosis <=10m
Name exactly one FIRST_LOST_EDGE, e.g.:
- human refresh -> zero topology candidates;
- candidate -> presence offline;
- locality -> signal missing/backpressure;
- signal -> scheduler no promotion;
- promotion -> ecology/readiness defer;
- READY_PASS -> materialize not called;
- materialize call -> rejected;
- materialize success -> no World actor.

Correlate with matrix and shortlist <=12 suspect commits.
No fix.
