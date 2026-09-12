# Goal039 Resume 9 — Goal016 commit-backed verifier + final freeze

Required parent: `f01401d79d5f41aac87cd8425b78a2f00abbf417`
Branch: `feature/phantom-world`
Blocker family: `HISTORICAL_VERIFIER_WORKTREE_EOL_COUPLING`
This is Goal039 Resume 9, NOT Goal040.

## Goal
Fix the proven historical Goal016 verifier defect without weakening historical
integrity, qualify the complete final historical static chain, then finish the
remaining Goal039 release gates.

## Precondition
Require:
`HEAD == origin/feature/phantom-world == f01401d79d5f41aac87cd8425b78a2f00abbf417`

Preserve all user tracked/untracked changes. Record current dirty fingerprint.

## Root cause
Resume8 full verify retry reached `phantom-static-verify-016` and failed only
because `verify-task-016.ps1` hashes historical PACKAGE_MANIFEST payload from the
CURRENT Windows working tree.

Historical manifest expects:
`docs/phantoms/tasks/016-population-manager-schedules/ACCEPTANCE.md`
SHA `fc5bbcc02129ca80760dd03b80122fac6318d5cb6664374337b2cf7eb3cbca5a`,
2366 bytes LF.

Normal Windows clone materialized 2405 bytes / 39 CRLF and SHA
`b44da3b3a90d8bce566e2cef6acbbe1d599952c456a365512231e2615c608b86`.

Goal016 already identifies the unique accepted completion commit. Historical
manifest/payload integrity must therefore read exact bytes from that commit,
not current checkout.

## Required correction
Modify only historical byte-source behavior in:
`tools/phantoms/verify-task-016.ps1`

Accepted/descendant mode:
- add binary-safe commit byte reader using
  `git show <completionCommit>:<modulePath>`;
- read PACKAGE_MANIFEST from the accepted completion commit;
- hash every payload from that same commit;
- strict UTF-8 checks use same commit bytes.

Working mode when HEAD == Goal016 implementation commit:
- preserve current working-tree behavior.

KEEP all existing graph/subject/scope/seed/safety/content checks.

FORBIDDEN:
- LF/CRLF alternate hashes;
- EOL normalization before manifest comparison;
- BOM stripping;
- trim/whitespace/XML/Unicode normalization;
- manifest hash rewrites.

The fix is "read the authoritative historical Git object", not "relax hash".

## Mandatory static census
Before edit inspect final verify historical chain:
Goal014, Goal015, Goal016, Goal017, Goal018, Goal019,
Goal020c1, Goal020c2, Goal022c1, Goal022c2.

Pre-audit says Goal017/018/019/020/022 already use accepted/target commit bytes.
If another verifier in this exact chain proves the SAME defect
(historical accepted commit known but payload hashed from current worktree),
it may be fixed in Resume9. No unrelated verifier cleanup.

## Goal039 test
Update:
`PhantomFullVisionGoal039Suite.REQUIRED_PARENT = "f01401d79d5f41aac87cd8425b78a2f00abbf417"`

Add narrow static case 08 proving Goal016 historical mode is commit-backed and
does not implement alternate EOL hash acceptance.

Expected Goal039 core static after correction: `8/8 PASS`.

## Candidate
Create one normal isolated Git clone in a final external path. Never move it.

Reproduce the PROVEN Resume8 materialization recipe:
- start with default normal clone, preserving historical Goal030 matrix;
- copy byte-identical tracked-clean schema SQL from operator under:
  `dist/db_installer/sql/login`, `dist/db_installer/sql/game`,
  `test/resources/phantoms/db/migrations` (Resume8 count 121);
- copy exact operator bytes for:
  `dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml`
  expected SHA `16c749b9e151e7d5fe7d702989a71dfc2ab3eedde9fa103c40b7d01a36e66a18`;
  `dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv`
  expected SHA `2b7676bccfd4395c267bc298e2f2c8dae265e23cee76d76853504bf7172f935e`;
- DO NOT broad-copy `test/resources/phantoms`;
- Goal030 matrix must remain SHA
  `fd891490e7bed44dba7d33f1b72d5c1de46ff67003190b31d22b7dd96206e64e`;
- DO NOT special-copy Goal016 ACCEPTANCE.md. Its CRLF checkout mismatch is the
  portability proof for the corrected verifier.

Copy guarded `.phantom-local/Database.test.ini` and schema manifest without
printing secrets. No production Database.ini. No prepare.

Overlay only task-owned source:
- Goal016 verifier;
- Goal039 suite;
- any same-family verifier proven by census.

Materialization-only EOL/stat paths are not staged.

## Qualification BEFORE full verify
Must PASS:
- canary `ant -q jar`;
- `jar tf` LoginServer.jar and GameServer.jar;
- compile-tests;
- `ant -q test` twice;
- DB negative guard;
- local-play preflight;
- Goal014;
- Goal015;
- Goal016;
- Goal017;
- Goal018;
- Goal019;
- Goal020c1;
- Goal020c2;
- Goal022c1;
- Goal022c2;
- Goal039 static `8/8`.

For Goal016 explicitly record:
- current worktree ACCEPTANCE.md raw SHA/EOL;
- accepted completion commit blob SHA;
- accepted blob SHA == manifest expected;
- verifier PASS even if worktree is CRLF, because it never uses that worktree
  payload in historical mode.

## Evidence reuse
Do NOT rerun already-fresh expensive gates if no owner semantic change:
- Goal039 final-domain aggregate PASS 50m21s;
- Goal029 scale/environment/endurance PASS 32m01s;
- Goal030 rollback/release PASS 1m56s.

Production/build/config/data/SQL semantic changes expected: 0.

## Final sequence
After qualification:
1. ONE fresh `ant verify` -> PASS.
2. standalone FINAL `ant -q jar`.
3. record SHA/bytes LoginServer.jar and GameServer.jar.
4. fresh Goal034 real stack WITHOUT jar rebuild.
5. prove JAR hashes unchanged, gen1/restart/gen2/continuity/cleanup,
   forced=false, no orphans, integrity=true.
6. final docs/freeze.
7. matrix `28/28 PASS`.
8. Goal039 documentation `2/2 PASS`.
9. Goal039 static `8/8 PASS`.

Final exact marker:
`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`

No Goal040.

## DB safety
Only `127.0.0.1:3308/l2jmobiush5_phantom_test`, user `l2j_phantom_test`.
Production `l2jmobiush5` forbidden even read/probe.
`prepare-phantom-test-db` forbidden.

## Git
Operator repo: bounded read-only Git, exact-path add, one normal commit,
non-force push.
Forbidden: reset/restore/checkout/clean/stash/rebase/merge/amend/force/history rewrite.

SUCCESS:
`phantom(goal-039): freeze declared full vision`

BLOCKED:
`phantom(goal-039): record resume 9 blocker`

New independent product/test blocker: one focused confirmation then STOP.
Same exact historical-verifier family found by mandatory census may be corrected
before full verify.

On ACCEPT: Roadmap v5 FINISHED. No Resume10. No Goal040.
