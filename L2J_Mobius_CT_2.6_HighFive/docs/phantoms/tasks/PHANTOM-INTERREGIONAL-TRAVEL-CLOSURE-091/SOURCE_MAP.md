# SOURCE_MAP — read-first actual source на HEAD 627bca947fd

Все пути ниже относительно `L2J_Mobius_CT_2.6_HighFive/`. Имена и примерные строки подтверждены source review 25.10.2026, **не использовать номера вместо повторного чтения действующего файла**. Никаких новых native owners без проверки.

| Owner / путь | Конкретная точка |
|---|---|
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java` | `candidate(currentAnchorId,... )` ~381–392 требует `travel.route(...)`; native no-D1 gate до goal selection |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java` | `proposedStep` ~110, `resumeStep` ~122, `advance` ~300, `executePlan` ~492, `walk` ~528, `terminalResult` ~702, `replanEdge` ~770, `Journey` ~1086; реальный action owner |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomNormalGatekeeperTravel.java` | `load`, `parse`, `matchesNative`, `observeNativeLeg` ~191, `route` ~260, `fee` ~342; certified D1 ≠ foreground transient |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java` | `planEpisode` ~44, legacy `plan` ~114, max128 nodes/512edges/32queries/4096labels, Geo candidate frontier + hysteresis |
| `java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java` | `canWalkDirect` ~86, `findWalkEpisode` ~118, `planTravel` ~161, `findPath` ~234; normal single-GK limits |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java` | `submitWalkEpisode`, `submitWalkCorridor`, `submitTravelPlan`, queue/worker, cancellation, validation, completed plans |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeTravelActions.java` | `observe` via `NormalGatekeeperTravel`, `snapshot` ~114, `available` ~135, `gatekeeper` ~142; native NPC bypass/fee/landing |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomTravelStrategy.java` | `Stage/Candidate/Options` strict complete-plan and bound (8 candidates/64 stages/128 legs) |
| `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationPolicy.java` | `productionDefaults` 120s maximum attempt, max2 concurrent pathfinders, 64 waypoints |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java` | `captureVisibleArrival` ~1045, `continueVisibleCheckpoint` ~1071, owned work/guarded recovery; avoid NATIVE_WORK_SELF_DRAIN |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java` | immutable 12-field record, `SCHEMA_VERSION=2`, `withState/after` state copies, receipt/inventory truth |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundStateCodec.java` | `FORMAT_VERSION=2`, `COMPACT_FORMAT_VERSION=3`, old payload strict canonical bytes, max4096/8192 |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java` | canonical SQL row locks/`phantom_profile_components`, state/goal/inventory TX; any persistent intent CAS to live native facts |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundGoalSpec.java` + `java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java` | exact goalId/revision authority; no persisted free-form inventing new goal |
| `java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java` | `travelQuery`, certified topology/fee, canonical committed anchor validation |
| `java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyLoader.java` | strict topology XML source refs, validation, traversability |
| `dist/game/data/phantoms/travel/high-five-normal-gk.xml` | verified 54 native legs / 7 source NPC; not all 256 D1 records |
| `dist/game/data/phantoms/topology/high-five-core.xml`, `high-five-generated-01.xml`, `high-five-generated-07.xml` | current anchors, relative region map; inspect before any data changes |
| `tools/phantom-world-data/normal_gk_catalog.py` + `targeted_travel_connectors.py` | approved D1 join/source authority. No manual fabricated XML/hashes |
| `test/region_travel089.py`, `test/java/org/l2jmobius/tests/phantoms/PhantomTravel089Geo.java`, `PhantomTravel089Test.java` | existing source-only Geo/DB-free regression; NOT native Player evidence |
| `test/java/org/l2jmobius/tests/phantoms/PhantomUniversalRegionTravel087.java`, `PhantomTask087Lane.java` | native fixture/guard examples; **087 runtime** protected, reuse source ideas only |
| `tools/phantom-runtime/Task087.ps1`, `Task089.ps1` | bounded isolated physical stages, safety receipts; no uncontrolled copy/probe retry loops |
| `test/java/org/l2jmobius/tests/phantoms/PhantomNormalGatekeeperPureTest.java`, `PhantomWalkCorridor064Test.java`, `PhantomGeneratedHistoricalRouteSuite.java`, `PhantomLocalFarmRecoverySuite.java` | existing focused regression owner candidates |

**Known source facts:** `GATEKEEPER_FACTS.tsv` 256 NORMAL facts/26 source NPC; `high-five-normal-gk.xml` 54 legs/7 NPC. Actual Human NPC30006 (`Others/17_25.xml`), Elf NPC30146 (`Others/21_19.xml`), Orc NPC30576 (`Others/18_14.xml`), Giran control NPC30080. Quest settings 090 are protected (`RateQuestReward=50`, `RateQuestRewardAdena=25`, 272 overrides). Source facts/coords from report089 are diagnostic starting points, **not route-proof instructions**.

Read native Mobius classes `TeleporterData/TeleportHolder/TeleportLocation`, `Npc.canInteract/onBypassFeedback`, `Player/getAI/World/PathFinding/GeoEngine/ZoneManager`, `PlayerNativeWork/PhantomNativeWorkScope` at actual locations discovered by agent. Do not fork them. Query exact files/methods before coding; this map is a starting index, not an exhaustive allowlist of methods.
