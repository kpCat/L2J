# OWNERSHIP CENSUS

Before production edits, create `OWNERSHIP_CENSUS.tsv` for all managed profiles with
both components available.

Columns:
`profileId,populationState,ecologyDisposition,ecologyCursor,ecologyPending,ecologyRequestHash,ecologyTarget,historicalStatus,historicalFrom,historicalCursor,historicalTarget,historicalRequestHash,seedMatch,classification`

Request hash:
bounded SHA-256 prefix (12 hex) only; do not publish full request IDs.

Classifications:
- NO_HISTORICAL
- COMPLETE_HISTORY_IDLE
- OWNED_EXACT
- ORPHAN_EXACT_ADOPTABLE
- ORPHAN_FROM_CONFLICT
- ORPHAN_TARGET_CONFLICT
- ORPHAN_SEED_CONFLICT
- OWNED_REQUEST_ID_CONFLICT
- OWNED_WINDOW_CONFLICT
- OTHER_CONFLICT

Required summary:
counts per class plus exact rows for profiles110/175.

Use retained observe014 DB read-only first.
If current PLAY can be inspected using existing read-only credentials/tooling, compare
classification counts only; PLAY remains SELECT/decode only.
No direct repair DML.
