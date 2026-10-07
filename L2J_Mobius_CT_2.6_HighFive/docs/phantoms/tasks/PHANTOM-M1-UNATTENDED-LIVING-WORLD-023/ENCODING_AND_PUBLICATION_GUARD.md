# TASK023 artifact checks

- mojibake-маркеры в изменённых файлах проверены: все27 требуемых markers,
  включая replacement character; production/test18 paths и полная artifact family.
- escaped Cyrillic в изменённых файлах проверены: Unicode04/05 и XML x/X04/05 patterns;
  совпадений нет.

Hand-written source and user-facing report text contain no encoding defects. Two marker
declarations in the checker scripts intentionally contain the literal search patterns.
Three preserved raw dry-path captures contain870 logger date-header lines with replacement
characters from the JVM locale/output decoding. These are captured diagnostic output,
not hand-written captions or source strings. Raw evidence was kept unchanged; geodata/path
result lines remain readable. ARTIFACT_GUARD.json records each exact location separately.

Strict UTF-8 decode succeeds for every included file. All21 immutable package-input hashes
match PACKAGE_MANIFEST.json. No file exceeds95MB. The largest retained inventory TSV is
6,874,927 bytes. Artifact inventory has exact path, size and SHA256; the three generated
guard/inventory/allowlist files omit their own hashes to avoid a circular digest.

Secret scan found three reviewed technical variable-assignment references, without literal
credentials: Prepare-Runtime023.ps1 password read/client option interpolation and
Read-Clone023.ps1 environment preservation. No credential/token/private-key literals were
found. Runtime credentials, full private SQL dumps, original snapshots and clone backups
remain in the owned .phantom-local directory and are excluded from exact-path staging.

The final artifact commit changes only this task package. Production/test code remains
at frozen1b71464a9e09a80dad890e2bfd39df0f3b5d3b96. Publication receipts are local files
excluded from staging; the exact final remote HEAD is recorded after the normal push.
Whole-artifact whitespace output, if any, is retained separately: raw evidence and immutable
inputs are not normalized. Hand-written task files and production/test paths are checked.
Initial exact stage1339 paths passed; whole-artifact check produced519 raw-output lines,
including preserved source-review diff context and JVM thread-dump whitespace. The separate
hand-written task check passed; cached production/test and credential/config inventories
were empty. These diagnostic captures are retained without normalization.
