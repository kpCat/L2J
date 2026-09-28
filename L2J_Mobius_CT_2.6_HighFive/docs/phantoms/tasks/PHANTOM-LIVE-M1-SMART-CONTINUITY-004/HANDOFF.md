# HANDOFF — PHANTOM-LIVE-M1-SMART-CONTINUITY-004

## Repository

- GitHub: `https://github.com/kpCat/L2J`
- Repository: `kpCat/L2J`
- Branch: `feature/phantom-world`
- Module/cwd: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`
- Required initial remote HEAD: `0c2d305ca1759fbdfadf487e584409db4c7ee71b`
- High Five only. Do not touch other chronicles.
- JDK 25 / Apache Ant.
- PLAY DB is read-only (`SELECT`/`SHOW` only). No PLAY DML/DDL/reset/reseed/manual repair.

## Runtime checkpoint

The final connected M1-003 session ran production build/source `c28c5d9999f184aefb7092343548e00a27ca8246`; the final remote `0c2d305...` is the documentation closeout commit on top of it.

M1-003 result: `RED — M1_CONNECTED_LIVE_RED`.

Do not repeat the old investigations. Accepted facts:

1. Canonical generic farm coverage is already broad:
   - independent levels `85/85`;
   - carried progression `1..85`;
   - original route rows `15/15`;
   - source-derived/native-proven topology route data exists.
   Do not run a per-location/per-race live matrix and do not manually patch one spot at a time.

2. Death lifecycle is already component-proven `6/6` and a natural connected dead→alive transition was observed. The production ordinary corpse recovery uses a native death event and a 45-second resurrection window. Do not redesign death unless this task causes a regression.

3. Stock High Five AutoPlay/AutoUse is the materialized farming micro-loop. Do not create a second combat engine.

4. Current scheduler/materialization semantics conflate policy deferral with transition failure:
   - `PhantomActivityMaterializationPort.Outcome` has only `SUCCESS`, `TRANSIENT_BLOCK`, `RETAINED_FAILURE`;
   - `PhantomReconcileFirstActivityPort.materialize()` returns `TRANSIENT_BLOCK` when reconcile/locality is not yet ready;
   - `dematerialize()` returns `TRANSIENT_BLOCK` while native visibility retention is true;
   - scheduler converts transient block to exponential retry up to 30 seconds.

5. Final connected RED showed exactly that problem:
   - profile 8417 received human locality but inherited an existing transient transition backoff;
   - first materialization was delayed ~30 seconds;
   - this is not acceptable prewarm behavior.

6. Current global scheduler demotion grace is only 2000 ms (`PhantomSchedulerPolicy.productionDefaults`). Native could-know visibility is retained, but there is no deliberate ~minute soft recent-human continuity window.

7. Final connected RED also showed a failed visible travel loop:
   - alive ordinary farm profile had no native movement/attack/cast;
   - AutoPlay off, IDLE;
   - `travel.navigation_pending` / `travel.navigation_unproven` / `background.visible.start_retry` / step timeout repeated;
   - `PhantomVisibleFarmTravel` refreshes `visible.farm.travel` before navigation succeeds;
   - terminal/unproven travel clears request/waypoints but can keep the Journey/presence alive, retaining a broken materialized idle loop.

8. `PhantomNavigationService` already exposes concrete terminal statuses and uses native `GeoEngine`/`PathFinding`. Prefer fixing the caller/lifecycle before changing the navigation core.

9. `PhantomVisibleAutoPlay.Policy` still uses the persisted exact NPC ID. This is acceptable only if generic replan/fallback prevents indefinite idle. Do not special-case one NPC. If the exact target is unavailable at a valid destination for a bounded interval, use the same generic suitability/replan machinery rather than standing forever.

## User product requirement added for this continuation

Materialization must be continuity-aware:

- A Phantom must remain materialized while a REAL client can natively know/see it.
- After ordinary human locality/visibility is lost, keep a **soft recent-human hold around 60 seconds** so a player who turns around or briefly leaves a region does not cause despawn/respawn churn.
- Re-entry during the soft hold cancels dematerialization and continues the same live Player.
- Soft holds are reclaimable under real materialization-cap pressure; they must not starve a new HUMAN_NEARBY materialization.
- A native party containing a REAL player is a **hard retention reason**. Ordinary locality loss must not dematerialize that Phantom and randomly break the party.
- Hard retention must be represented as a reusable reason/pin policy, not a one-off party hack, so future `INSTANCE`, `RAID`, `COMBAT`, `PRIVATE_STORE`, `DIRECT_INTERACTION` reasons can reuse it.
- Do not activate broad future M2/M3 gameplay in this task. Only build the retention boundary needed to keep current/future native ownership safe.

## Process constraint

This is a production-fix task, not a proof-only task. Keep reports compact. Raw logs stay as files. Do not append a diary of every command to EVIDENCE.
