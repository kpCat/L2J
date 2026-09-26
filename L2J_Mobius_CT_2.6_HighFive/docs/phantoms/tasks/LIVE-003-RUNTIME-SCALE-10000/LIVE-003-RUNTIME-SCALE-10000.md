# LIVE-003 runtime/scale result

Status: **PARTIAL — SCALE_CREATION_THROUGHPUT_BLOCKED**.

## Proven gates

- Production-loaded guarded TEST DB route proof: 15/15 progression rows `PROVEN` in `docs/phantoms/live-world/LIVE003_PROGRESSION_ROUTE_PROOF.tsv`.
- Production locality: a real client at the canonical route surface signaled nearby profiles; unresolved and remote profiles retain fail-closed rules.
- Live 0D: client screenshot at `2026-09-26T13:16:28Z` showed RUNNING, effectiveACTIVE 7, worldMaterialized 7, localSignaled 20, periodic owner ECOLOGY, topology registered/resolved 1280/1280. READY/admitted profile 201 was ACTIVE/STABLE/WORK_DELIVERED and `worldPresent=true`, `nativeFailure=null`.
- 1280: 1280 READY with committed positions and unique names/accounts; catchup and ecology made forward progress, no fatal/OOM/DB exhaustion, exact port ownership and bounded threads. Visual observation: several phantoms were present, one sat; most appeared idle and no chat was seen in the short observation. This is not a LIVE-003 acceptance expansion.

## 5000 attempt

Only the private population target was changed to 5000, including its manifest mirror. ActiveTarget 64, materialized cap 128, MaxScheduled 10000 and other budgets stayed fixed. The clean committed GameServer.jar remained SHA-256 `B0843527651ABF2CF12881F81ED4646382229E16191DA0350A6AD451A75F78B6` from code commit `274764a23f5d4e67363f6607b81693d0746a4112`.

GameServer became RUNNING at `2026-09-26T16:29:24+03:00`; the deadline was `17:14:24+03:00`. Every sampled count stayed at 1280 managed/linked with 1280 distinct names/accounts and no duplicates. Historical/ecology recovery progressed but no new profile appeared. The final sample at `17:14:51+03:00` still showed 1280/1280. **5000 is BLOCKED by SCALE_CREATION_THROUGHPUT_BLOCKED**; target soak and new-profile locality witness could not occur. 10000 was not started.

The exact owned runtime is STOPPED and ports 2106/9014/7777 are closed. Final PLAY SELECT/SHOW snapshot retains 1280 READY and 1280 committed positions; no profiles were removed. Private target remains 5000. PLAY received no direct DML/DDL. The five `LIVE003_*.tsv` summaries beside this report and `EVIDENCE.md` record the gate results; detailed read-only snapshots and minute samples remain under `.phantom-local/logs/LIVE-003-RUNTIME-SCALE-10000/`.

Full `ant verify`: 0. Deliverable `ant jar`: clean detached committed revision only. LIVE-004/005 were not started.
