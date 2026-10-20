# PSS-008 — RED/GREEN matrix and operator testing

Follow existing partial console runner, not a new test framework/project. Never prove a feature with mocked inputs only when actual optional operator validation is available; label evidence precisely.

## A — analysis, UI

1. Synthetic patterns for `greet.reply` and missing TEMPLATE; clean+nonclean variants for UNKNOWN/FAMILIAR/CASUAL: assert exact clean counts and topic/act buckets, no `TEMPLATE.Topic` invented. Multiple source files, override provenance.
2. NFKC/ё/case exact normalization vs negated meaning; limited lexical nearest warnings and explicit NOT_SEMANTIC_VERIFIED. Deterministic report under permuted entries; max spans/ranks.
3. Pattern bucket 256, template bucket 4096, 1MiB v3 file/32MiB total, 64 files and effective count boundaries; synthetic 5k/20k manageable, no O(N²) explosion. Operator scan read-only + before/after source SHA stamps and existing candidate/session bytes.
4. Actual STA `PackQualityForm` controls: default read-only, filters, cancellable view, minimum/re-sized control bounds, no `APPROVED`/source mutation. Visual Studio Designer static + physical manual 100/150 separately.

## B — v3 shadow

5. RED: v3 fixture with exact declared paired semantic/conversation files; manual select two CURRENT APPROVED PATTERN/TEMPLATE, explicit paired mapping, IDs `pss.v3.*`, byte-exact remaining files, manifest/custom unaffected, expected count +1/+1; pattern-only and template-only.
6. Negatives: zero/21/unapproved/duplicate peer; wrong/no v3 map or ambiguous pairing, forced inappropriate target; gender!=ANY, placeholder/memory/action/profanity/mature; too-long UTF8/UTF16, duplicate normalized phrase/cross-act, ID collision, unknown act/topic, wrong root/category/attributes, DTD/XXE, max/near-cap v3 and per-bucket counts. All leave zero finished stage and unchanged current session/source.
7. External modification during source copy; user edit/approval drift after preliminary check; junction/path traversal/zero bytes/missing file; cancellation/interrupted staging. Prove only exact own partial removed; unrelated sentinels/finished stages untouched.

## C — Java/oracle/release handoff

8. Operator native Java on **physical shadow**, baseline/staged genuine loadV3, exact selected ID/act/topic/eligible template proof, baseline/staged counters, combined hashes, deterministic reload. Negative duplicate/invalid actual Java-loader reject, fail-open prevention. Real native `Ant exit 0`, `Probe exit 0`, negative expected nonzero. Catches Java moved schema instead of rewriting blindly.
9. Java source guard if build target redirected output outside scratch or reachable task shape modified; no Ant invoked, `BLOCKED_JAVA`. Missing JDK25/libs/permission `BLOCKED_JAVA`; no automatic download.
10. Release candidate negative: no Java validation file, mismatched stage checksum/proof, source drift, missing exact human consent, input symlink, prior failed oracle ⇒ no release folder. Positive synthetic: exact changed v3 files only in `proposed` and byte-equal original in `backup`, manifest accurately pairs all SHA/bytes and says `OFFLINE_HANDOFF_NOT_INSTALLED`. Source ZIP, review ZIP, installer/install code absent.
11. Small Java content smoke for selected inputs, unmatched input and wrong `act/band/register` where applicable. Don't claim social/memory/identity/game runtime parity or full gameplay. Never automatically upload real lab dialogues; only synthetic committed fixtures.

## Regression / final gates

- Focused `--pss-008-a|b|c` RED exit nonzero due actual assertions before implementation; GREEN afterwards. Preserve all 107 original ordinary console tests, old PSS003 custom Java script source unchanged, `--pss-004-controls`, `--pss-005-controls`, `--pss-006-controls`, `--pss-007-controls` when relevant.
- Final `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Verify.ps1` return actual zero warnings/errors and `107 + N` PASS / 0 FAIL. `scripts/Verify-PSS008.ps1` static designer/UTF8/escaped Cyrillic/Git scope SHA and no tracked private binaries. DB/user corpora untouched.
- Test physical GUI via actual Windows if accessible, otherwise record NOT_TESTED plus manual operator steps, never aggregate physical UI with STA/static PASS. Live LM only one tiny explicit opt-in against already user-loaded exact Gemma, no auto load/unload; failure `BLOCKED_LM`. Java conditional and separately status.
- Real source import optional read-only should calculate and compare all 65 actual humanized files SHA/bytes before/after, not rely solely on previous reports; locally changed corpus requires blocking/review rather than assuming fixed count.
- A/B/C gate report exit, stdout, number of new tests and actual user-visible functionality, with no user raw text, original private messages or token in Git.
