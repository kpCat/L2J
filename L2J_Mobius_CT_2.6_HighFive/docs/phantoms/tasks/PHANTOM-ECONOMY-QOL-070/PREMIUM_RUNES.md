# PREMIUM + RUNE audit / activation

**Owner approved**: 1m *regular Adena* daily at x1, rebalanced on meaningful earned-Adena rate changes; x2 XP/SP plus boosted **rare** drop, item chance, spoil. Premium in current 069 default OFF and HTML says 1kk even when effective config differs. Fix product rather than merely turn switch on.

## Preferred premium configured default (author choice)

- EnablePremiumSystem=True, CommunityPremiumSystem=True, CustomCommunityBoard=True **only in OWN070 release**. No OS/client or protected default changes.
- XP=2,SP=2, ordinary drop CHANCE=2 / AMOUNT=1, spoil CHANCE=2 / AMOUNT=1; raid/GrandBoss rare item CHANCE=2/AMOUNT=1 if premium-loot-eligible player. Adena ID57 derived effective ×2 once (example rateChance57=1, rateAmount57=2). **Do not set both 57Chance2 and 57Amount2** causing net4×, and do not silently let item-specific 6656.. override chance to1. For rate suppression IDs=0, suppression remains0. Actual quest rewards and Olympiad tokens unaffected by premium unless explicitly stock.
- Players sharing the same account get actual premium flag and remaining duration; ensure one premium owner does not magically make nonpremium party leader get boosted drops; inspect `NpcTemplate.calculateDrops` raid party owner/looter logic for true eligibility. If canonical math cannot assign bonus without violating loot ownership, provide explicit owner-rule and native proof.
- Premium status and effective chance/amount/spoil/XP/SP visible on page. Buying1/3/7/15/20/30 days must show final applied server price/currency and calculated expiry; native ledger/account premium storage, no fake item granting. No account auto premium; phantoms no gratis premium.
- Premium and rune bonuses may stack through stock stat `PlayerStat.getBonusDropRateMultiplier`, `getBonusSpoilRateMultiplier`, but avoid applying one physical rune twice or letting identical timed runes stack indefinitely. Default max bonus x3 total premium+rune for SAME source before global server rate; do not silently cap preconfigured base/rare raid chance other than stock max100%.

## Rune discovery specifically requested by owner

`HomeBoard` current Merchant after069 contains only Mana728, Seal Stones, Quest/Clan, Scrolls/Sell; no Rune control. `PlayerStat` includes actual native bonuses `BONUS_DROP_ADENA`, `BONUS_DROP_AMOUNT`, `BONUS_DROP_RATE`, `BONUS_SPOIL_RATE`. This does **not** prove a user-visible timed Rune item exists. Make `RUNE_AUDIT070.tsv`: real exact itemID/name/skill/locale client icon/default action/duration/stat effect, available for normal access0? Which existing shop? Native consumption/reuse/expiry/character persistence? If genuine stock effect rune exists, expose conservative timed24h/7d variants in optional `Runes` section with real prices and no overlapping identity/cancel exploit; default rune multiplier max1.5 chance for drop OR spoil, not free gear. If NO supported stock rune, report `NO_NATIVE_RUNE_ITEM`; optionally expose **server-side non-item subscription buff as clearly distinct** only with persistent timed effect and real earned price, but do NOT claim it is a stock rune, invent unknown ID/icon or change client assets; a working rune item is not hard gate if none exists. Owner may approve new client-visible assets in a later campaign.

## Important tests

- On stock x1 no premium: rare grouped/ungrouped boss drop chance baseline, itemCount baseline, spoil baseline, rare item ID list unaffected.
- Premium: deterministic chance=2 (or cap at stock max), amount1; one-item example QueenAnt ring6660 baseline 30%→60% only when proper eligible reward; no free item due to reroll or duplicate loot. Adena x2 **not x4**, ordinary spoil true chance2; raid ignored only if design and explicit owner override? Owner says rare drops BOOSTED, so do not preserve stock raid exclusion as a hidden blocker.
- Purchase/restart: 1 day, 7 days, reject no Adena, date persists, duplicate click/no double benefit without double payment, account other character gets same flag; nonpremium remains normal.
- Rune: only if native item and duration actually exists; checks buff refresh/expire, no double-stack, no premium bypass.
