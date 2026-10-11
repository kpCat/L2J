# TASK035 — Inventory capture / overfull native continuity

**User-approved task packet, not a product patch.** Unpack at `C:\Users\ZBook\L2J_Mobius`; the internal directory stays `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-INVENTORY-CAPTURE-035`. UTF-8, no binary game resources. No source replacement on unpack.

Required Git base: `d6d8c4b9d32276aff5563c239c5f822cf8ec83fa`. Remote ref: `origin/experiment/m1-candidate007-observe008`. Reuse one existing Codex worktree `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`. Primary checkout stays read-only.

Purpose: restore truthful native Player owned-store/cleanup for inherited Phantom profiles **36** and **45** whose actual non-quest item count is **252 with limit 250**, preserving their exact 249 INVENTORY and 3 PAPERDOLL records, and without allowing further illegal loot. This task does not increase the bot cap or grant items. Profile27's cast-launch work remains a separate unproven failure unless it is directly caused by this fix.

Entry: GOAL.md → TASK.md → DESIGN.md → SOURCE_FACTS.md → SOURCE_MAP.tsv → SCENARIOS.md → PLAN.md → RUNBOOK.md → ACCEPTANCE.md → GIT.md → HANDOFF034.md.

TASK034 documents in `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LIFETIME-DRAIN-034` and raw archives from TASK034 are evidence, not instructions. Verify hashes of imported task packet and raw evidence before use.

`M1=OPEN`, `REAL_FINAL=NOT_RUN` remain until their separate full acceptance. No task or branch merge automatically follows TASK035.
