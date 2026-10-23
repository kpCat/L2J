# READ_SET072 — конкретные исходники и исходные evidence

Все пути ниже **относительны к** `L2J_Mobius_CT_2.6_HighFive/`, кроме явно указанного root.

## Сначала 071 факты
- `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071.md`: кратко `FULL071=PARTIAL`, `RELEASE=PASS`, native CP/supply/skills.
- `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071-data/ACCEPTANCE071.tsv`: B_CAUSE/B_SHOTS/B_CP/C_GEAR/C_SHOTS PARTIAL, C_RECOVERY BLOCKED_INPUT, A_EPIC PARTIAL.
- `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071-data/POST_PUBLICATION_AUDIT071.md`: CP NPC30081 actorZ−3080 vs NPC Z−3506, XY16, native250; paid receipt отсутствует. Retained source profile1, SELL6716/BUY1835, actor19 vs wolf4 0 reward, nearest eligible lvl9 ~11569m.
- `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071-data/native-supply-resume.tsv` и `native-earned-supply.tsv` (последний при наличии), chronology sales/buy/return/fail; `test/java/org/l2jmobius/tests/phantoms/PhantomSupply071JourneyNative.java` lines 114–125 premeasurement profile19 and choice, `selectScene` (~240–290) hardcoded mobs2..8, `kill` (~296–310) same level2..8, `earnedContinuation` (~320+) checks EXP/SP and5km.
- `test/java/org/l2jmobius/tests/phantoms/PhantomCombat071Native.java`, `PhantomLivingCombat071Native.java`; `docs/phantoms/reports/PHANTOM-LIVING-COMBAT-071-data/native-combat-tail.tsv`; `PhantomPvpSuite`, `PhantomSupply071Suite`. Это reference для новых 072-fixtures; не запускать 071 prepare с той же БД!

## Combat & threat
- `java/org/l2jmobius/gameserver/phantoms/combat/{PhantomCombatService,L2jCombatBackend,PhantomCombatCapabilityResolver,PhantomNativeShotControl,PhantomPvpSkillSafety,PhantomPvpCombatRequest,PhantomCombatPolicy}.java`.
- `java/org/l2jmobius/gameserver/phantoms/pvp/{PhantomPvpService,PhantomPvpPolicy,PhantomPvpContext,PhantomPvpModel}.java`: Source ACTUAL_ATTACK/FARMING_ESCALATION/PARTY_DEFENSE/REVENGE; stage WARN/HELP/ENGAGE/RETREAT, per-pair risk/cooldown; `handleCombatTerminal` returns `SUPPLY_REQUIRED` to retreat; combat `processPvp` native CP threshold, `issuePvpAction` requires shot reserve2 before casts.
- `java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java` and existing retreat coordinator class (locate actual path before touching).
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java`: HP rest hysteresis and durable PHASE_DEADLINE, don't refresh debt.

## Supply, native equipment and discovery
- `java/org/l2jmobius/gameserver/phantoms/commerce/{L2jPhantomSupplyJourney,L2jCommerceBackend,PhantomCommerceCatalog,PhantomCommerceCatalogLoader,PhantomCommerceService,PhantomCommerceReceiptStore,PhantomOfflineSupply,PhantomSupplyIntentStore}.java` (verify exact files; if a name differs use immediate package search).
- `java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.java`, `PhantomProgressionCatalog`/native skill owners.
- `java/org/l2jmobius/gameserver/phantoms/navigation/{L2jNavigationBackend,PhantomNativeTravelActions,PhantomNormalGatekeeperTravel}.java` (verify exact class location; do not invent a second pathfinder).
- `dist/game/data/buylists/3008100.xml`: **Helvetia30081** stock 1835 NG SS; **1463 D SS**; 5591 CP50, 5592 CP200. List presence alone != spawned availability/valid interaction. `dist/game/data/buylists/3000400.xml` **Katerina30004** has NG SS1835 but not D1463; do not reuse 071 source as D source.
- `dist/game/data/stats/items/01400-01499.xml`: 1463 D,1464 C,1465 B,1466 A,1467 S `default_action=SOULSHOT` and crystal grade. `dist/game/data/stats/items/01800-01899.xml` 1835 NG. `dist/game/data/stats/items/05500-05599.xml`: 5591 CP+50, 5592 Greater CP+200, reuse500ms and Olympiad restriction.
- `dist/game/config/Rates.ini`: `RateXp=1` and `MonsterExpMaxLevelDifference=11` in source; actual runtime ini must be checked. `dist/game/config/NPC.ini` raid behavior. No changes to global EXP rule to cure 071 fixture.

## Economy/run/build ownership
- `java/org/l2jmobius/gameserver/qol/Economy070Shop.java` `verifySchema(Connection)` requires `phantom.economy.schema`, installation UUID, regex `l2jmobiush5_phantom_(test|release)_[0-9]{3,4}`, task>=71, own task_id == schema suffix, schema version1 and current migration071 SHA pinned `e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3`. Avoid unnecessary Java rewrite for072; provide correct OWN072 metadata.
- `dist/db_installer/sql/game/{economy070.sql,economy071.sql}` install **only in new own072 DB**; no reseeding installed economy stock on restart.
- `dist/game/data/economy070/pricing070.tsv` and `.sha256` approved x1 prices after071; do NOT touch. `dist/game/config/Custom/{PhantomPlayers.ini,PhantomMarket.ini,PremiumSystem.ini,CommunityBoard.ini}`.
- `tools/phantom-runtime/{Build-Task071,Assemble071,Runtime071,Task071Common,Task071DatabaseProbe,Prepare-Inputs071}.ps1` / `.java` as **patterns only**; strict071 ownership needs **new** 072 wrappers/receipts, not repointing old scripts.
- Root `AGENTS.md`; module `Agents.md` if present, `docs/phantoms/{CODEX_WORKFLOW_CONTRACT,RELEASE_CONTRACT,PHANTOM_CURRENT_STATUS,TASK_PACKAGE_STANDARD}.md`. Historic TASK069/070 read only if direct cross-boundary regression.
