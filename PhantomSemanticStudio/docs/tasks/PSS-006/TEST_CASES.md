# PSS-006 — minimum targeted tests

- A1: ShowCandidate saved-evidence selection does not block UI thread while reader/scout runs, verifies latest selection identity only, cancellation clean on close; no stale CURRENT after source or peer drift.
- A2: narrow malformed/reference/model response classifications redact secrets and do not become approved/saved; model JIT unloaded means no fake PASS.
- B1: synthetic UTF-8 zip 5 known lines → 3 public rows + 2 private SKIPPED before persistence (plus counts), correct timestamp/channel and language statuses; no `SECRET_FIXTURE_NOT_TO_PERSIST` in DB bytes.
- B2: 68 synthetic entries/67 MB style load stream, no full archive extraction, no relative path escape; arbitrary filename with `../`, root, symlink metadata or encrypted flag rejected.
- B3: 100:1 compression ratio, 256 MiB uncompressed budget, line >16KiB, >3m records, invalid UTF-8, malformed timestamp and unsupported channel. Reject/skip according to documented policy; partial never published.
- B4: same normalized line across channels distinguished if desired, duplicates within a channel counted; deterministic import IDs/hashes independent of ZIP ordering.
- B5: translit `privet kak dela` candidate, English `need party` not converted mechanically; `ne znayu` ambiguously handled with status; mixed Cyrillic+Latin flagged; no batch translation.
- B6: PII detection flags emails/phones/URLs/IPs in synthetic public messages, preview avoids accidental disclosure; do not claim complete GDPR/anonymization guarantee.
- B7: cancel during 100k-line ingest → no committed partial, existing corpus fully readable; invalid SQLite/index file cannot be silently trusted. Two independent imports never overwrite each other.
- B8: pagination/search uses at most page-size rows, stable sort, responsive cancellation and memory envelope; wrong channel/unknown filter returns empty not all, invalid query injection inert.
- C1: actual WinForms control route, selection of 1–20 public snippets for preview preserves explicit selection through filter, staged output/approvals/session bytes unchanged. Private rows never exposed.
- C2: Visual Studio Designer round-trip only if actually opened/saved/reopened; keyboard/mouse/DPI only with actual observation. Build and static guard are separate.
- C3: stored existing PSS-005 evidence current/stale; PSS-003 stage and PSS-004 exact manual selection do not regress. 88 prior ordinary tests remain.

Synthetic test logs should use only made-up `PlayerA`, `PlayerB`, etc. NEVER paste raw user chat messages/nicks into Git sources/fixtures or reports.
