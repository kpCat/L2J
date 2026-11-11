# What is actual «профит» and how to prove it

Do not collapse a complex combat outcome into DPS. Objective should be versioned per native mode, with **vector of observed values** and comparable, calibrated scalar only where needed for a specific learning choice. Sample episode metric vector:
`confirmedKill/unknown, objectiveSucceeded/unknown, exactDamage, usefulHeal, damageTaken, deaths, duration, MPSpent, consumablesSpent, realExp, realSp, realAdenaNet, dropReceipts, supportSaved, retreatOutcome, captureValidity`. Unsupported entries UNKNOWN, not zero. For 004 only Solo/Party PvE observations are required.

The 002 reward formula is approximately `clip(8*exactHP/targetMaxHP - mpCost/1000 - castMillis/30000 - itemCost*.005, -1, +1)` — useful for limited proof but NOT maximum DPS/win/farm outcome. 003 HEAL reward `usefulHP/maxHP - actualMP/1000 - elapsedNanos/1e11`; good for isolated healed HP, but not survival of Party. Do not quietly mix incompatible raw reward scales in shared V3 head. Separate family calibration, supported normalization, masked terminal evidence, and explicit weights/config per mode; full multi-step RL may be deferred, but no claim of its achievement.

Minimum quality measurements after each campaign segment:
- actual selected-action receipts, exact event-key uniqueness and no incomplete credit;
- per-family and per-class TRAIN/TUNING/AUDIT size and MSE or appropriate metric; compare to untrained constant and last compatible accepted; no reused audit;
- native matchups or matched PvE scenes to compare stock/V1/V3, actual target death/time/survival when exact; report result UNKNOWN when insufficient, do NOT infer tactical superiority from MSE;
- change in learning performance from earlier to later sessions on frozen holdouts; no regression test HEAL Cardinal97 and EvaSaint105, DAMAGE Solo/Party; do not erase a losing group in aggregate;
- candidate state `DRAFT/REJECTED/QUALIFIED` and explicit accepted TEST pin; never auto apply to MAIN/PLAY.

No fixed guaranteed win rate, no assertion every class must beat higher-level class. Future PvP requires league of old/current policies, mirrored sides, randomized actual gear/levels/roles, 1×1, 1×2, 2×1, N×M, multi-group conflicts; evaluate winning and retreat separately and preserve legality. This is roadmap, NOT acceptance of 004.

**Early-stop recommendations** after sufficient independent samples and confidence intervals: improvement plateaus vs old policies, acceptable no-regression across covered roles, budget limits. If one model beats another on limited seed, do not announce that it has reached mastery. If qualifying candidates all rejected, stop honest bounded campaign and report why; don't weaken thresholds or change seeds for a green badge.
