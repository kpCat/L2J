# SCENARIOS034 — accepted causal cases

| ID | Ситуация | Expected |
| --- | --- | --- |
| F0 | Archived incident but `_activeByProfile` has no entry | Historical finished/archive only, not retained live actor; don't manufacture drain failure. |
| F1 | Retained entry present before Synthetic on same new own JVM | Preserve exact owner and first incident; identify earliest producer; do not blame synthetic/test scene without proof. |
| F2 | Native work/timer/store still outstanding at cleanup | Cleanup remains pending/failed; no early World delete, lease release, reward loss or fake COMPLETE. |
| F3 | Actual native work drains and same live owner is still current | One legitimate retry can finish; stored native fields and first failure retained for audit; entries/leases released only after exact postconditions. |
| F4 | Callback from previous epoch arrives after replacement | Reject stale transition; current Player/World/identity unaffected. |
| F5 | Foreign/REAL_LOGIN identity or autosave belongs to another owner | Fail closed, no `releaseRetained` bypass, no real Player overwrite. |
| F6 | One natural actor earns EXP/SP, calendar/locality removes demand | Native earned/store/retire, FINALIZED payload; exact World/AutoSave/native owner and service entry absent only after quiescence. |
| F7 | Whole materialization manager stop with 3 pre-existing failures | Status FAIL, exact reason per profile, even if six scene lifetimes have detached and JVM exits. No denominator tricks. |
| F8 | True clean source clone without pre-existing faulty episode | Stock stop becomes COMPLETE/retained0. If contaminated clone can't become clean without forbidden SQL heal, report BLOCKED_DATA, not runtime GREEN. |
| F9 | Retrying same failed stop / same cause | No change to timeouts, no forced shutdown pass or unbounded reruns; first incident stable. |

Test one actual baseline-vs-current comparison before deciding whether defect is in *new* product code, historical persisted data, or only diagnostics. Native acceptance is on real GameServer/Player; headless fixture alone never closes F6/F8.
