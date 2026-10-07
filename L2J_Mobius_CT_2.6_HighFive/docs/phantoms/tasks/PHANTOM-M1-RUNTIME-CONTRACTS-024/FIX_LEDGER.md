# TASK024 semantic fixes

Required base: 819e3cea5baa64e6c429e450c8fc296874e37d1c. Main checkout is untouched.

| Fix | Independent RED | Reused producer / contract | Verification |
|---|---|---|---|
| B geometry | RED_B | L2jPhantomBackgroundAuthority native capture; appended POSITION_REQUIRES_NATIVE ordinal | GREEN_B01 3/3; probe b SEALED→SQL 5/5, including off-area613 |
| C dependency lifetime | RED_C | PhantomSystem materialization/background stop graph; topology stops after POST_STORE | GREEN_C02 3/3; REG_C_FULL 8/8 |
| C monitor | RED_C_MONITOR | shutdown outside configured/instance monitor after exact shutdown claim | Included GREEN_C02; actual b/c graceful drain stopped=true |
| A original native entry | RED_A2, actual HitTask | PlayerNativeWork original action origin; Creature attack/cast entry | GREEN_A01 2/2; stale epoch negative REG_A_STALE_ORIGIN 3/3; REG_A_DYNAMIC 6/6 |
| D locality | RED_D01 | Historical planner factual area geometry and capped local alternatives | GREEN_D01 1/1 |
| D useful arrival / route witness | RED_D02_D03 | VisibleFarmTravel arrival before nonterminal deadline; history excludes exact failed route witness | GREEN_D02_D03_01 2/2; current-intent regressions 10/10 |
| B09 scheduler pending handoff | RED_B09_PENDING_HANDOFF; required base existing matrix fails before visible selection too | Exact resumeVisibleOwnedStore before ordinary handoff/ActionLease checks; pending directive remains visible only with exact current owner | GREEN_B09_PENDING_HANDOFF 1/1 |
| B10 pending handler transition | RED_B10_LIVE_PENDING_HANDLER, isolated existing LIVE_PREPARE | After exact resume, reuse advance() to revalidate actual farm arrival; never treat off-area as arrived | GREEN_B10_FAULT_MATRIX 1/1, all27 matrix boundaries; REG_B09_AFTER_B10 1/1 |
| B11 planned PENDING restart | RED_B11_PENDING_STALE_AUTHORITY; full c diagnostics show exact restored topology/presence but stale PENDING authority | Route PENDING through existing recoverStale before native attestation; existing canonical refresh, owner claim and replacePlan CAS remain | GREEN_B11_PENDING_STALE_AUTHORITY 1/1 with live/dead variants; REG_D_AFTER_B11 10/10 |

No guard removal, native coordinate substitution, diagnostic counter restoration, incident reset, combat-engine/scheduler/schema rewrite. Production paths currently11, within18 allowlist cap. B09 uses the existing BackgroundDecision visible candidate binding together with D; no new abstraction.

Known negative evidence retained: REG_A_LIVING_FULL N02 fails identically on unchanged required base; the direct skill fixture has native EXP but no original-entry cycle evidence. GREEN_B09_FAULT_MATRIX accidentally used an unsupported focus and therefore ran the full12-case position suite: 6/12 passed, six failures retained. A focused owned-store rerun isolates B10 independently. No broad suite PASS is claimed.

Older a43 full scenes remain failed evidence: FINAL_SCENE_A complete7, only252 met all farm criteria; FINAL_SCENE_B had only3 eligible actors at its unteleported origin, ENVIRONMENT_GAP before baseline. They are superseded only because B09/B10 are legitimate newly reproduced semantic corrections; they are not erased or counted as successes. PlayerNativeEvidence caps and incidents remain READ-only.

Collector correction is task-only evidence tooling: World-only enumeration missed a retained/dead Player's final SEALED capture. c proof failed606 and remains FAIL. Collector now reads retained materialization Player references too and restricts export to the current checkpoint thread. Expected state never comes from SQL.

Runtime episodes2/8; crashes0/2 as of 18:00UTC. All full owned JVMs are stopped. The isolated TEST JVM is separate from full runtime episodes. Planned crash agent compiled, not armed yet; it delegates the existing injector, checks REALcount0 and emits SEALED receipt, SQL and dump hashes at the exact AFTER_NATIVE/AFTER_FINALIZE fault point before halt.

18:38UTC update: episodes3/8, crashes0/2, all full JVMs stopped gracefully. c episode3 on5bb had two rejected setup locations before baseline, no measured R2 natural scene. Exact persisted native points and presenceOnline=true were restored for all7; six stayed unmaterialized because planned PENDING attestation rejected stale authority before recovery. B11 has its own RED and GREEN; this is an in-scope restart correction, not a reroll of a failed measurement. c shutdown drain stopped=true in155ms. Episode4 will use the next verified semantic SHA and preserve c DB.

Task-only collector follows later epochs only when explicitly requested, after exact current materialization/Player/owner/checkpoint-thread validation. It exports initialEpoch and epochTransition; scene same-epoch/farm failures remain unchanged. Persistence comparisons may separately accept a documented exact-current epoch transition; expected values still come only from immutable SEALED receipts, never SQL/counters.
