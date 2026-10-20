# PSS-007 — acceptance gates and honest statuses

## Minimum for GREEN_IMPLEMENTATION
- A: form + pure Pack-only engine in own code, long chat trace, correct source IDs and `PACK_CATALOG_APPROXIMATE`, no-match; local mode works with LM off. No Java-runtime-parity claim.
- B: opt-in OFF default; strict structured separate Mentor roles and one clarification; editable/temporary memory; only explicit commit of scoped lesson or DRAFT with baseline gates; malformed LM does not mutate approved candidates.
- C: opt-in public corpus selection bridge with 1–20 detached sanitized snippets, separate model sharing confirmation, 1–3 per explicit translit/translate request; no batch LM; corpus immutable and private skipped; no raw transcript data in Git reports.
- All new UI static Designer-authored; existing six tabs, candidate editor, StageSelectionForm, ChatCorpusForm, PSS-003 Java staged operator and PSS-004 selection still build/function. Keep `.Designer.cs/.resx` writable from VS Designer.
- Targeted A/B/C RED→GREEN, Release Build-Verify **at least 98 existing checks**, 0 compiler errors/warnings ideally, source/approvals untouched, robust cancellation, JSON strict.
- `reports/PSS-007-final.md`, `reports/PSS-007-ui.md`, `reports/PSS-007-owned-files.txt`, `reports/PSS-007-plan.md` and bounded sanitized evidence. Review via public GitHub; **no review ZIP**.
- Commit(s) and ordinary non-force push to own `origin/main`, `ls-remote` proof exact HEAD/local/remote SHA, clean tree; never push private corpus or secrets. All scope guard checks PASS.

## Separate statuses that cannot be inherited or faked
- `LIVE_LM`: if still unloaded, `BLOCKED_LM`/`NOT_TESTED` with reason; fake HTTP is not live Gemma. Never auto load/unload. Fixing safe classification is not proof that model honors schema.
- `UI_INTERACTIVE`, `DPI_100`, `DPI_150`, `VS_DESIGNER_ROUND_TRIP`: PASS only when physically exercised, otherwise NOT_TESTED with one clear operator checklist. STA controls ≠ physical mouse/keyboard.
- `JAVA_CONTENT`/`SERVER_RUNTIME`: **NOT_RUN / NOT_SERVER_READY**, except a separately authorized isolated operator run; no code path installing XML. `PackPreview` not Java parity.
- `ACTUAL_CORPUS`: may reuse stored 685498 public import; no requirement to reimport 67 MB or run mass model inference; report exact new actual read(s) if done, otherwise NOT_RUN. Never quote real public/private rows in tracked evidence.

## If a subgate blocks
Do not strip validators, change permissions or invent substitutes. Commit/push safe results with `BLOCKED`, clear next action and test coverage per A/B/C. Do not silently skip any selected excerpt, draft or pending clarification. Continue independent subparts when safe; **STOP before PSS-008**.
