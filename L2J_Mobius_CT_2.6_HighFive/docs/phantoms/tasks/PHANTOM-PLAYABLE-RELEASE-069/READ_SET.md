# READ_SET069 — точные входы / код

**Сначала** read `AGENTS.md`, `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/PHANTOM_CURRENT_STATUS.md`, `docs/phantoms/PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md`, корневой `CODEX_WORKFLOW_CONTRACT.md` (фактический путь), `RELEASE_CONTRACT.md`, пакеты069 и `docs/phantoms/reports/PHANTOM-LIVING-ECONOMY-068.md`. Затем только relevant history:
- M1 `docs/phantoms/reports/PHANTOM-M1-FINAL-057.md`; исторические G4/G6.
- M2 `docs/phantoms/reports/PHANTOM-M2-M7-CAMPAIGN-058.md`.
- M3–M7 `docs/phantoms/reports/PHANTOM-M3-M7-CAMPAIGN-059.md`, `PHANTOM-LIVING-PROGRESSION-060.md`, `PHANTOM-WORLD-CAREER-061.md`.
- M1 presence `PHANTOM-LIVING-PRESENCE-065.md`; farm `PHANTOM-FARM-ECONOMY-066.md` и `...066-R2.md`; personal `PHANTOM-PERSONAL-ADVENTURE-067.md`; TASK068 report.

Все пути ниже от `L2J_Mobius_CT_2.6_HighFive/`; никогда не предполагать, что живой source/jar совпадает со старым HEAD без проверки.

**Raid curse+drop**:
- `java/org/l2jmobius/gameserver/model/actor/Creature.java`: native curse on physical attack ~5333, skill target ~6282, support ~6447.
- `dist/game/data/scripts/ai/bosses/QueenAnt/QueenAnt.java`: отдельный onAggroRangeEnter curse ~398–451, boss29001.
- `java/org/l2jmobius/gameserver/model/actor/templates/NpcTemplate.java`: ordinary/raid loot per-level filter `calculateDrops`, around742/937/845/960; `java/org/l2jmobius/gameserver/qol/LevelGapProtectionPolicy.java` (existing personal carrier policy, не делать глобальный reuse для raid без необходимости).
- `java/org/l2jmobius/gameserver/config/NpcConfig.java`, `dist/game/config/NPC.ini` (`DisableRaidCurse=False` historical), `java/org/l2jmobius/gameserver/model/actor/instance/RaidBoss.java`, `GrandBoss.java`. Поиск по всему production/deployed script на `RAID_CURSE`, `giveRaidCurse`, `RAID_DISABLE_CURSE`, `4515`, `4215` (?) — последние IDs проверить по CommonSkill, не полагаться на пример.

**Epic access**:
- `dist/game/data/scripts/ai/bosses/QueenAnt/QueenAnt.java` (open world nest), `Core/Core.java` (Cruma Tower Core29006, teleport shortcuts), `Orfen/Orfen.java`, `Baium/Baium.java`, `Antharas/Antharas.java`, `Valakas/Valakas.java`, `Beleth/Beleth.java`, `Frintezza/LastImperialTomb.java` (min player level80 check ~777, party min9/max27), and actual Zaken source in `scripts/instances`/`scripts/ai` by name.
- `java/org/l2jmobius/gameserver/model/zone/type/BossZone.java` (если присутствует), `dist/game/data/zones/**`, `dist/game/data/instances/**`, actual NPC teleport/bypass scripts, `GrandBossManager`, no arbitrary global `InstanceManager` admission bypass.

**Mana and UI**:
- `dist/game/data/stats/items/00700-00799.xml` Item728 + Mana Drug726 (each has `player isPvpFlagged=false`), `dist/game/data/stats/skills/custom/10000-10099.xml` skill10001 ManaHeal100 reuse500ms; `dist/game/config/Custom/AutoPotions.ini`; `dist/game/config/Rates.ini` item57 overrides.
- `dist/game/data/html/CommunityBoard/Custom/navigation.html`, `merchant/main.html`, `buffer/main.html`, `premium/main.html`; `dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java`, `PersonalPremiumQoLBoard.java`, `java/org/l2jmobius/gameserver/config/custom/CommunityBoardConfig.java`, `dist/game/config/Custom/CommunityBoard.ini`, `PremiumSystem.ini`.
- `dist/game/data/buylists/0000423.xml`, `dist/game/data/multisell/custom/600010.xml` (ordinary jewelry), `600012.xml` (scrolls), `600014.xml` (B/A weapons), `600016.xml` (A gear), `600024.xml` (quest/clan). Existing merchant UI points to these; guard direct bypass if remove button.
- `dist/game/data/multisell/91001.xml` (pass tiers), `91002.xml` (progression items), `dist/game/data/custom/personal-qol/level-gap-items.xml`, `dist/game/config/Custom/PersonalPremiumQoL.ini`, `PersonalCharacterQoL.ini`, `PersonalProgressionQoL.ini`; code `java/org/l2jmobius/gameserver/qol/PersonalPremiumQoLService.java`, `PersonalCharacterQoLService.java`.
- `dist/game/data/stats/skills/**` for Wind Walk id1204 and valid skill level; `CommunityAvailableBuffs` currently does NOT list1204.

**Seal stones**:
- `java/org/l2jmobius/gameserver/model/sevensigns/SevenSigns.java`: 6360 blue3, 6361 green5,6362 red10, `calcAncientAdenaReward`.
- `java/org/l2jmobius/gameserver/model/itemcontainer/Inventory.java`: `ANCIENT_ADENA_ID=5575`; normal npc/service via `NpcData`, `scripts/handlers/bypass/npc/Multisell.java`, `dist/game/data/html/merchant/**`, real Blacksmith in Giran. DO NOT mint Ancient Adena in bypass handler without atomic inventory cost.

**M1–M7 native owners**:
- `java/org/l2jmobius/gameserver/phantoms/background/{PhantomHistoricalBackgroundService,PhantomVisibleAutoPlay,PhantomVisibleFarmTravel}.java`;
- `java/org/l2jmobius/gameserver/phantoms/progression/{PhantomAutomaticProfessionPolicy,PhantomCareerChoice,PhantomAutonomousLearning,L2jPhantomLearningJourney}.java`;
- `java/org/l2jmobius/gameserver/phantoms/party/{PhantomPartyCoordinator,PhantomPartyTactics,PhantomPartyRouteCoordinator}.java`;
- `java/org/l2jmobius/gameserver/phantoms/combat/{PhantomCombatService,PhantomPartySupportAction,PhantomRaidCombatRequest,PhantomPvpCombatRequest,PhantomCombatSkillSafety}.java`;
- `java/org/l2jmobius/gameserver/phantoms/economy/{PhantomOfflineMerchantService,PhantomStoreService}.java`;
- `java/org/l2jmobius/gameserver/phantoms/commerce/{L2jPhantomSupplyJourney,PhantomLootDispositionPolicy}.java`;
- `dist/game/config/Custom/{PhantomPlayers.ini,PhantomMerchants.ini,PhantomMarket.ini,ChampionMonsters.ini}`;
- `tools/phantom-runtime/{Assemble068.ps1,Runtime068.ps1,Build-Task068.ps1}` read-only reference, **не запускать эти tools против нового 069 root** (они strict-bind068).

**Existing local runtime** protected: `D:\PhantomWorld\Living-Economy-068-candidate`/release068, `D:\PhantomWorld\AltB-View-068V`/QA, all older candidates. Repo code can be updated on own task branch as authorized; no old data rewrites.
