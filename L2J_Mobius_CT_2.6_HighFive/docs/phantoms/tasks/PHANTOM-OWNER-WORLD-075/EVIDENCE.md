# EVIDENCE075 — пропорциональная проверка без тестовой кампании

### A/B tracked configs and enchant
- `config-profile`: before/after exact effective file paths, 38/38 `OWNER_PROFILE_38.tsv` traced in SOURCE and candidate, `git diff --check`, tracked status and source manifest SHA, candidate copy SHA. Rebuild from same source/repackage must preserve exact owner config; negative mutation in copied candidate should make verifier RED before runtime.
- XML parser validation for EnchantItemGroups/EnchantItemData, safe from +0/+3/+10/+16/+24, unsupported +25 and bonusRate100. Existing special scroll limits unaffected. For all player values, grep exact key and load PlayerConfig test without UI fake evidence.
- ServitorShare: stat-specific stock multiplier 1.0 parity, default 2.0, independent settings (e.g. PA2, MA1), summon effect lifecycle add/remove, zero cross-skill regressions. No long native cycle for XML.

### C quest preservation
- Compare tracked Goal037 report/calls and present quest runtime (source + focused local test/compile if necessary), per-multiplier real input, 1x vs representative non-1x and high-level repeat for Q00298. Q00186 deliberate negative match. If no legitimate standalone correction, PASS_NOT_REGRESSED / no edit.

### D native offline shifts
- One real existing phantom with actual surplus and end-of-shift state: store open at Geo-approved reserved slot; physically published native SELL/BUY or previously learned MANUFACTURE; no stock/resource grants; two owner modes cannot hold same character; 2 sellers cannot occupy same slot; another human/GM protected.
- One native actual purchase/sale with seller/buyer native receipts + exact inventory/Adena conservation. No need exhaust product categories.
- Switch to/from offline, item object IDs and balances persist. Failure after durable write reconciles or fail-close, never duplicates.

### E restart catch-up
- Simulate controlled wall-clock gap through existing bounded controller or virtual clock in OWN075 test; no endless loops. SameDB restart twice: identical interval not re-credited. No fake transactions while server off.
- If unsafe for live runtime, report exact blocker and keep previous historical behavior; do not mutate old DB/candidates.

### F client-facing broker
- Prefer source audit and native packet/HTML path proof. Categories and listing+seller location from current native objects; stale listing not a valid trade, radar uses client-native marker without teleport. GUI display remains OWNER_QA_PENDING unless user validates.

One compact result file `docs/phantoms/reports/PHANTOM-OWNER-WORLD-075.md` lists A–F independent, baseline/commits/tests and verifiable SHA references. Status PARTIAL when any HARD target not actually verified; no relabeling on focused tests alone.
