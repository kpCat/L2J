# RUNTIME_INPUTS066-R2 — protected baseline + isolated delivery

**Git root** `D:\Codex\repos\L2J-phantom-world`; module `L2J_Mobius_CT_2.6_HighFive`. Verify actual branch/HEAD and ownership. Private local helper root: `<GitRoot>\.phantom-local\task066r2` (new, `.gitignore` private).

**Existing baseline066, read-only:** `D:\PhantomWorld\Farm-Economy-066-candidate`; `l2jmobiush5_phantom_release_066a`, `l2jmobiush5_phantom_test_066`; real code commit1432e2807fa96921af0869a080fbb35d10d3ee56; final report HEAD5f112172f31cbf36cd3e1c0e47da48ffc77ccbde. Candidate066 already STOPPED when report issued, but recheck actual PID/ports before any work. **Never START/STOP/wipe/change this release for R2.** Historical057–065/MAIN/PLAY likewise protected.

**New destinations proposal (create only if absent):**
- Release: `D:\PhantomWorld\Farm-Economy-066-R2-candidate`; schema `l2jmobiush5_phantom_release_066r2`; ports Login2147/Game7847/status9147 (adapt to actual verified triad).
- Native TEST: private R2 lane under `.phantom-local\task066r2\test-runtime`; schema `l2jmobiush5_phantom_test_066r2`; ports Login2247/Game7947/status9247.
- Exactly these TWO new R2 schemas; if path/schema exists without own exact manifest, do not use/overwrite. On conflict choose fresh unique R2 suffix, update all exact bindings and record.

**Database choices:** for verifying legacy authority/money/items, take a **read-only snapshot/copy** from stopped original `release_066a` into fresh R2 release schema, using safe transaction consistent backup method, original unchanged. Then prove 48 original identities and receipts unchanged under new code; optionally create ≥2 new R2 newborn identities in that new schema for gift. Native TEST schema can be fresh stock 124 tables/121 scripts as in066. If consistent read-only source clone is impossible, keep separate factual legacy fixture in TEST with preimage hashes and mark acceptance limitation — do not alter066. No automatic live `l2jmobiush5` writes.

**GeoData:** 203 validated physical region files from protected source, copy into own R2 location, verify SHA, no hardlinks/symlinks/reparse or commits. Existing JDK25, Ant, MariaDB11.4.13 `127.0.0.1:3308`; check actual binary versions, no installation or service changes.

**Runtime:** Use read-only existing `Build-Task066.ps1`, `Assemble066.ps1`, `Runtime066.ps1` and `START/STATUS/STOP` contracts as narrow templates; R2 names and safe exact schema/JAR/PID-bound ports. Build **outside** protected dist/release; clean JAR from exact committed source. Private credentials from approved sources only; do not print/copy into git logs or commit. Local tests with dedicated TEST accounts access0, no fake client.

**Observer:** existing `dist/game/config/Custom/LocalPlayPilot.ini` and restricted read-only snapshots `PhantomSystem.operatorNearestVisibleMaterializedTarget` / `OperatorM1TargetSnapshot`, exact objectId, epoch, PID+start ticks; `EnableLocalPlaySyntheticHuman=False`. Observer uses its own passive profile; do not log in as real user, arm human pilot or spawn fake client. Observation point chosen before measurements on actual Geo. `PersonalCharacterQoL.ini/AllowedAccounts` **not** a general autonomous observer credential.

**Output delivery:** R2 START.cmd/STATUS.cmd/STOP.cmd, `START_HERE_066_R2_RU.md`, `BUILD_INFO` and source/JAR/testTree/config/Geo hashes, exact schema/port binding, stopped own JVM. Protect every read-only source against write, normal STOP and zero R2 ports/writers at exit; check preservation of only actually used old resources, do not scan all 122 old DBs. Report must not claim exact gameplay proof based on source-only tests.
