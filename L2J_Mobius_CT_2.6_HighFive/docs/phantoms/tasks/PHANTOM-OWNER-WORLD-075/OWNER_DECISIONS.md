# OWNER075 — конкретные решения владельца (из текущих сообщений)

## A — постоянные значения Player.ini

Профиль для обычных игроков И фантомов, а не только GM (этот список известен независимо от отдельного скачанного профиля38):

| Key | Value |
|---|---:|
| AltWeightLimit | 100 |
| MaxPvtStoreSellSlotsDwarf | 20 |
| MaxPvtStoreSellSlotsOther | 20 |
| MaxPvtStoreBuySlotsDwarf | 20 |
| MaxPvtStoreBuySlotsOther | 20 |
| MaximumSlotsForNoDwarf | 200 |
| MaximumSlotsForDwarf | 250 |
| MaximumSlotsForGMPlayer | 250 |
| MaximumSlotsForQuestItems | 200 |
| MaximumWarehouseSlotsForDwarf | 250 |
| MaximumWarehouseSlotsForNoDwarf | 250 |
| MaximumWarehouseSlotsForClan | 250 |
| MaximumFreightSlots | 200 (first validated; optional 250 only after client confirmation) |
| DwarfRecipeLimit | 100 |
| CommonRecipeLimit | 100 |

- 299 is **not** a generally safe warehouse choice: supplied Player.ini warns warehouse must be <300 or client crashes; bonus WH_LIM further increases actual limit. Client acceptance for 20 store slots is owner-pending. Do not assert 50-position High Five UI verified.
- `AltWeightLimit=100` reduces practical encumbrance but does not disable inventory slots or guarantee never-overloaded. Java `int getMaxLoad` cast can overflow at extreme factors; do not set INT_MAX.
- `MerchantLinesPerStore=3` with code hard cap8 is independent from Player.ini. For 20 lines actually displayed at `.offline`, adjust merchant policy only if code/safe client confirmed. No instant 50 products to every seller as substitute for actual goods.

## B1 — обычная заточка

`EnchantItemGroups.xml` CHANCE (by CURRENT enchant before next attempt):

- ARMOR_GROUP / FIGHTER_WEAPON_GROUP / MAGE_WEAPON_GROUP: `0-2 => 100`; `3-9 => 85`; `10-15 => 70`; `16-24 => 66`; `25-65535 => 0`.
- FULL_ARMOR_GROUP: `0-3 => 100`; `4-9 => 85`; `10-15 => 70`; `16-24 => 66`; `25-65535 => 0`.
- Accessories currently use ARMOR_GROUP. No fictitious unique jewel chance unless explicitly requested; future item group can separate if needed.
- Raise normal `maxEnchant=16` to `25` in `EnchantItemData.xml`; do not blanket-edit destruction scrolls max6/15, Olf +10, Yogi+23, event+50, support items with explicit alternative mechanics.
- Formula in `EnchantScroll.calculateSuccess` = `min(100,baseChance+scrollBonusRate+supportBonusRate)`; `bonusRate=10` adds 10 percentage points. Ensure max25 cannot be bypassed by bonusRate100/default65535 and `OverEnchantProtection`/`DisableOverEnchanting` are consistent. No silent client >25 guarantee.

## B2 — Servitor Share 1557

- Already explicit coefficients in `data/stats/skills/01500-01599.xml`, application in `scripts/handlers/skill/effects/ServitorShare.java`, plus `Player.getServitorShareBonus`.
- Create transparent small tracked config with **independent mults**, default2.0; recommended names `ServitorSharePAtkMultiplier`, `ServitorSharePDefMultiplier`, `ServitorShareMAtkMultiplier`, `ServitorShareMDefMultiplier`, `ServitorShareMaxHpMultiplier`, `ServitorShareMaxMpMultiplier`, `ServitorShareCritMultiplier`, `ServitorSharePAtkSpdMultiplier`, `ServitorShareMAtkSpdMultiplier`, and `ServitorShareMaxRecoverableHp/MpMultiplier` where corresponding share XML present.
- `newCoefficient = 1 + (stockCoefficient-1) * requestedMultiplier`. Example stock1.5 becomes2.0 if multiplier2.0. Stock coefficient is copied from XML, not changed in-place. Turn off/tuning1.0 must produce stock; allow independent overrides and respect native skill application/remove/restart.
- No unintended effect on pets/other skills/common NPC, and no global stat multiplier.

## C — quest preservation

- Check Goal037 quest-rate changes before modifying every script. When known original rates helpers exist, use them, preserve stage-dependent/double-rate exceptions. No guessed XP/SP/item formulas.
- `Q00298` (repeat SP at high level) != `Q00186` (other quest). Validate level gating separately for take/finish/reward and replay. Owner's quest-rate modifications lost on prior disk clone may be untracked; document limitations, do not claim recovery of unknown bytes.

## D — shifts and offline SELL/BUY/MANUFACTURE

- Phantom who really amassed sellable goods can set up a store **at shift end/relaxation period**, NOT immediately after one low-value drop. Sellers have real ownership, stock and money; never sell reserved/combat-useful/quest gear.
- Respect positive distance between occupied legal slots (suggestion >=60 native coordinates if supported by existing policy), town/peace, other players/near NPC, avoid stack of traders in one point. No visible teleport; bounded background transition via established topology.
- Use earned goods and real native store mechanics. Prepared merchant bootstrap models are separate and not an excuse for free refill.

## E — historic days while server off

- Do not assume GameServer runs while powered off. On startup, use existing causal time-catchup, with maximum bounded work, eligibility/witness/rate limits, no arbitrary duplicate credits/sales/spoil.
- Real offline SELL does not sell itself while the server is off without a buyer. Simulate only existing documented background systems that have lawful output authority.

## F — native broker

- User wants buy/sell/craft by item/grade/category and marker/arrow toward vendor. First audit existing native Item Broker; do not rewrite unrelated ItemAuction NPC32320-32322. If client UI impossible without patch, declare client work separately optional.

## Configuration artifact: independent owner profile of 38 rules

- The user has now supplied the original 38-rule TSV; it is included byte-for-byte as **`OWNER_PROFILE_38.tsv`**, SHA256 `c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c`. It is mandatory, not an unavailable external file. The Player.ini table above is a convenient summary, not a replacement for any of the 38 entries. See `PROFILE_38_IMPORT.md`.
