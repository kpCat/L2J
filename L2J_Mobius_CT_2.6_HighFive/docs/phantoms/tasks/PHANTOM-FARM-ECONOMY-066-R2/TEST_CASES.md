# TEST_CASES066-R2 — bounded high-value scenes

## Global
- Fixed seed only for fixtures; normal Main naturally scheduled and passive. All TEST items/skills/locations configured **before measurement** with metadata `TEST_PREIMAGE`, never called earned. PID/JAR/DB/epoch/objectId evidence required.
- Precheck Geo/skills/loot from stock data, record one primary and one fallback **before** runtime; don't search successive coordinates until something happens to work. Stop when a gate is achieved, no soak.

### S0. Legacy compatibility and starter entitlement
1. Read/copy stopped066 release to new R2 schema with exact original48 identities, old receipts, `PLAYER_CREATION_INITIALIZER_V1`/creation authority hashes. New code cold-start; no `authority.contract_drift` and **zero** retroactive bonus for these48.
2. Create one genuinely NEW R2 physical novice and NEW R2 mage population profile; first materialization only. Check correct No-grade item type id1835/2509/3947 and charged native hit/cast. If initial physical 5000 cause weight penalty, use persistent per-life entitlement5,000 with grants≤1000 when inventory shot stock<100; concrete implementation test.
3. After partial spending and one dematerialization/rematerialization/restart, issued ledger never exceeds5,000, no second subsidy; two simultaneous claims no double grant; zero any subsidy to real Player or background-only state. Test deliberately failed credit boundary (don’t retry ambiguous item mutation).

### S1. Shots have priority, no-poverty deadlock
1. Materialized actor after free quota/for D-grade: compatible owned shots → actual enhanced native strike + exact quantity deducted.
2. Low stock, positive Adena, native reachable merchant with itemID+grade matching: captured goal→ordinary walk/pay→buy sufficient useful stock→farm return→actual enhanced hit. Native balance invariant before/after; no repeated child intent.
3. Zero stock, zero Adena OR merchant unreachable/out of stock: attack/cast still produces lawful damage and, when applicable, kill/reward; due purchase loops bounded; earning Adena triggers single re-evaluation and buys when affordable. Mandatory arrows/MP/regents/summon upkeep negative still blocked.

### S2. Complete original G1: physical/magic/summon
- Direct native physical and damage spell both with 0 stock and MP/HP cost evidence; caster skill/cooldown native and target cast complete, not arbitrary8s timer. With SPS/BSS real native charge and depletion fallback; summons consume existing beastshots only, no auto minting. Use representative mage and fighter, no marathon all skills/classes.

### S3. Background modes and ownership
- Same deterministic ordinary/acquisition/spoil target facts run with 0 optional shots and valid mandatory costs. No RESOURCE_RESERVE solely due optional stock. With stock, consumption exact once; with missing summon mandatory cost/MP/seed remain RED as expected. `World→Background` and reverse epoch writer fences unchanged, no double reward/item.

### S4. One ranged loot positive + negatives
- Pick a supported stock ranged actor/target/drop with prevalidated path/Geo/instance and distance>200 (within policy600), actual ranged kill, ground source receipt, native pick-up. Verify route eligibility before start; report nonreachable wall and wrong Z/instance/protected drops as expected refusals. Do not patch individual coordinates.

### S5. Two legal dwarven careers, separated zones allowed
- Spoiler knows actual Spoil/Sweep; choose stock mob from native spoil table + mapped farm anchor, perform real skill/kill/Sweep, obtain material, then travel to different lawful NPC Merchant/Warehouse (can be town), deposit or sell and return to useful farm. Captures actual income/fees/delta/receipt.
- Separately, Artisan/crafter with native Crystallize turns truly surplus D item into legal crystals; do NOT grant Scavenger nonnative skill. No forcing spoil farm to be adjacent to a warehouse.
- If map permits only steps with background travel, prove invisibility/elapsed time/lawful authority and truthful absence of client-visible teleport. Do not make full journey hostage to one mob near one shop.

### S6. Ordinary Main and restart
- SyntheticOFF/passive observer, two actual new/legacy profiles. Track name/class/level/weapon, exact shot stock, Adena, charging, purchase/earn, native attack/damage/kill/reward, pickup, save, one STOP/restart same config+schema+JAR and further useful action. Verify first-born gift isn't duplicated and 066 legacy receipts untouched. Short observation: stop after proof; no 12-hour gaming session.
- Capacity soft+burst prior PASS gets quick focused regression only where touched; no Giran/raid/full M1–M7 re-tests.

### Exit
- Complete `ACCEPTANCE-R2.tsv`, `HANDOFF-R2.md`, changed-files list and final RU report. Historical066 failures remain historical. If any gate truly unsupported, record concise root cause and ship safe partial without demanding owner login.
