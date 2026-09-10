# Resume 6 test plan

1. Require exact HEAD/origin/branch and record current user dirty paths.
2. Run full startup census.
3. Use preserved Resume5 Goal032 blocker evidence; no redundant pre-fix runtime loop.
4. Patch every proven same-family test call site with existing helper.
5. Add/extend verify-owned structural guard.

Fresh forked-JVM mandatory gates:
- Goal032 reseed: 2/2 PASS
- Goal032 ownership: full PASS
- Goal031 readiness: full PASS
- Goal030 CP3 restart/failure: full PASS
- Goal030 CP3 rollback/release: full PASS
- Goal030 CP2 cross-domain: full PASS
- Goal033 production: 2/2 PASS
- Goal036: 8/8 PASS
- Goal037 native: 8/8 PASS
- Goal039 static/safety: PASS
- DB negative guard: PASS

For each patched suite record helper invocation count=1 and exact seven owners.

Then affected: Goal032 docs/static if cheap; Goal033/033A/033A1; Background position; Goal021 acquisition/restart; Goal037 static; shipped-disabled baseline; dual-mode geodata gates if local geodata exists. No prepare DB.

After green, CLEAN final sequence: Goal039 domain aggregate -> Goal029 scale/environment/endurance -> Goal030 rollback/release -> one fresh `ant verify` -> standalone `ant -q jar` -> fresh Goal034 real stack using final clean JAR -> final docs/freeze -> commit/push.

Same-family census findings are in scope. NEW independent blocker: one focused confirmation, STOP/BLOCKED, no repair in Resume6.

Update Goal039 report/matrix. ACCEPT => `FEATURE_COMPLETE_FOR_DECLARED_SCOPE`, no Goal040. BLOCKED => marker absent, no Goal040.
