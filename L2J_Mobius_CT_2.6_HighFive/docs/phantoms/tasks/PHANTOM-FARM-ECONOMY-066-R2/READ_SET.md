# READ_SET066-R2 — актуальный узкий список, затем symbols по необходимости

## Authority / отчет
- Git root `D:\Codex\repos\L2J-phantom-world`: root `AGENTS.md`, module `Agents.md`; `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`; `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/CODEX_WORKFLOW_CONTRACT.md`, `RELEASE_CONTRACT.md`, `TASK_PACKAGE_STANDARD.md`.
- `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-FARM-ECONOMY-066.md`; `docs/phantoms/tasks/PHANTOM-FARM-ECONOMY-066/ACCEPTANCE066.tsv`, `HANDOFF066.md`. Их статусы исторические, не редактировать ради R2.
- `docs/phantoms/PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md` и `PHANTOM_CURRENT_STATUS.md` — добавлять только R2 current fact.

## Native combat + shots
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomNativeShotControl.java` — `ShotOutcome/chargeOffensive/activate`.
- `java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java` — `prepareOffensiveShot`.
- `java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java` — `prepareOffensiveShot`, `prepareSummonOffensiveShot`.
- `java/org/l2jmobius/gameserver/model/actor/Creature.java` — native doAttack/beginCast gates; `Player.java` — owner/inventory/cast lifecycle. Avoid broad refactor.
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`; `PhantomBackgroundModel.java` and `L2jPhantomBackgroundAuthority.java`.
- `java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java`, `PhantomAutonomousSupplyPolicy.java`, `PhantomSupplyIntentStore.java`; `PhantomCommerceService.java`, `L2jCommerceBackend.java`.
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.java`.

## Exactly-once starter birth: critical hazard
- `java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java`: `VERSION=PLAYER_CREATION_INITIALIZER_V1`, `Mode.POPULATION` + `CreationPlan` with starting items and equipped gear.
- `java/org/l2jmobius/gameserver/phantoms/population/PopulationInitializationContract.java`: `VERSION_TOKEN=POPULATION_CREATION_AUTHORITY_V1`, `resolve()` reads exactly that plan; old character bootstrap hashes depend on it.
- `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java`, `PhantomPopulationPersistencePort.java`, `PhantomPopulationState/Store` and actual persistence adapter discovered from current source; `java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java`, `PhantomProfileComponent.java` for versioned, durable new-born entitlement.
- Do NOT modify a birth plan and assume old 066 identities survive the authority check. Find the smallest versioned/receipt-safe seam; legacy contract unchanged.
- Item templates actual: `dist/game/data/stats/items/01800-01899.xml` id1835, `02500-02599.xml` id2509, `03900-03999.xml` id3947; stock `ItemHandler`/inventory normal costs and `Player.refreshOverloaded` thresholds (<50% weight penalty free).

## Loot, dwarf, World
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomNativeLootControl.java`, `L2jCombatBackend.java`; `java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java`; `PlayerNativeEvidence.java` exact source receipt.
- `java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeTravelActions.java`, native Geo/PathFinding/Topology + stock spawn records; `PhantomHistoricalBackgroundPlanner.java`, `PhantomNormalGatekeeperTravel.java` if needed.
- `java/org/l2jmobius/gameserver/phantoms/knowledge/...` actual drop/spoil and class capabilities; `PhantomOrdinarySpoilEvidence.java`; `PhantomLootDispositionPolicy.java` and `PhantomInventoryMaintenanceIntent.java`.

## Tests / runtime, use existing
- `test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java`, `PhantomCombatServerIntegrationSuite.java`, `PhantomBackgroundSuite.java`, `PhantomCommerceSuite.java`, `PhantomTask066DwarfSetup.java`, `PhantomTask066Environment.java`, `PhantomTask066PersistentLane.java`.
- `tools/phantom-runtime/Build-Task066.ps1`, `Assemble066.ps1`, `Runtime066.ps1` (read-only references, do not overwrite original).
- `dist/game/config/Custom/PhantomPlayers.ini`, `AutoPlay.ini`; `dist/game/data/phantoms/life/high-five-autonomous-supply-v1.xml`.

**READ-FIRST LIMIT:** read root docs and this list at focused entry points; broader searches only when a specific stacktrace/failing symbol needs it. Do not re-ingest 039–066 reports or scan 1000 unrelated files.
