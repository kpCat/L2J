# Goal039 Resume 8 test plan

## Phase 0 — operator precondition

Require exact branch and:

`HEAD == origin == required parent`

Record dirty-tree fingerprint.

Preserve all pre-existing tracked/untracked user files.

## Phase 1 — read-first

Read:

- all Resume-8 package files;
- current Goal039 report;
- current Goal039 matrix;
- `PhantomFullVisionGoal039Suite`;
- `build.xml` jar/test/verify/Goal034 targets;
- `tools/phantoms/verify-task-014.ps1`;
- `PhantomTestDatabaseGuard`;
- Resume-7 report environment section;
- local Resume-7 evidence manifest if still present.

Do not infer missing credentials from docs/logs.

## Phase 2 — provenance correction

TEST-only:

update Goal039 `REQUIRED_PARENT` to Resume-8 required parent.

No other source change.

Run exact diff check.

## Phase 3 — create isolated candidate

Follow CANDIDATE_ENVIRONMENT.md exactly.

Do not create it beneath operator repo.
Do not move it.
Do not use junction for root/module/.phantom-local.

Normal clone initial checkout is authorized only in this disposable candidate.

## Phase 4 — environment qualification

All mandatory before full verify:

1. candidate branch/HEAD/Git-root exact;
2. candidate working tree clean before the one test overlay;
3. `.phantom-local` real-path checks;
4. `ant -q jar` CANARY_ONLY PASS;
5. `jar tf LoginServer.jar` PASS;
6. `jar tf GameServer.jar` PASS;
7. `ant -q compile-tests` PASS;
8. first `ant -q test` PASS;
9. second consecutive `ant -q test` PASS;
10. `ant -q phantom-static-verify-014` PASS;
11. Goal039 static 7/7 PASS;
12. DB negative guard PASS;
13. guarded local preflight/readiness PASS;
14. no unexpected candidate process left alive.

Only then record `CANDIDATE_ENVIRONMENT_QUALIFIED`.

Qualification candidate may be recreated up to the same-family budget defined in
ROOT_CAUSE.md before expensive verify.

## Phase 5 — reuse already-fresh expensive evidence

Apply EVIDENCE_REUSE.md.

Do NOT rerun the 50m final-domain aggregate, 32m scale/endurance, or rollback
unless reuse preconditions fail.

Record the prior exact-parent results clearly as REUSED_EXACT_PARENT_EVIDENCE.

## Phase 6 — fresh full verify

In the same qualified, unmoved candidate:

`ant verify`

Requirements:

- PASS / exit 0;
- all runtime/DB tail PASS;
- all historical static verifiers PASS;
- Goal014 static PASS in its correct isolated Git root;
- Goal039 structure PASS;
- no zipfs AccessDenied;
- no test-config realPath error;
- production DB unused.

If a PRODUCT/TEST assertion fails:
- one focused confirmation;
- new independent blocker -> STOP.

If the SAME environment family fails despite qualification:
- one fresh-candidate recreation/requalification;
- one final full verify retry allowed.
No product weakening.

## Phase 7 — standalone final JAR

Only after successful full verify.

Run exactly:

`ant -q jar`

This run is the FINAL standalone JAR gate.

Record for BOTH:

- `dist/libs/LoginServer.jar`: SHA-256 and bytes;
- `dist/libs/GameServer.jar`: SHA-256 and bytes.

At minimum the Goal039 final report/freeze must record GameServer final JAR SHA
and bytes; recording LoginServer too is preferred.

After this point, no production Java/config/data/build modification is allowed.

## Phase 8 — fresh Goal034 real stack

Without rerunning `jar`, run current:

`phantom-black-box-local-stack-goal034-test`

It may compile tests but must use the already-built exact final JARs.

Record:

- exact final GameServer JAR SHA/bytes before and after Goal034;
- exact LoginServer JAR SHA/bytes before and after;
- fresh run ID;
- gen1;
- native restart/drain;
- gen2;
- identity continuity;
- cleanup;
- forced=false;
- no orphan processes;
- DB integrity true;
- production DB unused.

JAR hashes must remain byte-identical.

If Goal034 regenerates either server JAR unexpectedly: FAIL final provenance.

## Phase 9 — final docs/freeze

Only after Goal034 PASS:

update current Goal039 artifacts:

- `docs/phantoms/reports/039-final-full-vision-release-gate.md`
- `test/resources/phantoms/release/goal039-full-vision-coverage.tsv`
- `PHANTOM_DEVELOPMENT_MASTER_PLAN.md`
- `docs/PHANTOM_BOTS_ROADMAP.md`
- `docs/phantoms/PHANTOM_CURRENT_STATUS.md`
- `docs/phantoms/NEW_DIALOG_START_MESSAGE.txt`
- create `docs/phantoms/PHANTOM_FEATURE_COMPLETE_FREEZE.md`

Final matrix:
28/28 PASS.

Report exact status token:

`Status: SUCCESS / ACCEPT`

Exact completion marker:

`FEATURE_COMPLETE_FOR_DECLARED_SCOPE`

No automatic Goal040.

Preserve declared limitations:
- bounded Giran;
- Q102/Q152 and Q401 Fighter->Warrior bounded scope;
- Kamaloka 57;
- Pailaka Q128/template 43;
- no universal quest solver;
- Humanized deterministic curated layer, no runtime LLM/internet;
- mature explicit opt-in OFF;
- bounded memory;
- geodata may be DEGRADED/absent.

Overlay final docs/matrix into the same candidate.

Run:

- Goal039 documentation mode: 2/2 PASS;
- Goal039 structure/static: 7/7 PASS;
- final matrix validator PASS.

No full verify rerun is required solely because docs/freeze changed after the
successful full verify.

## Phase 10 — quality and commit

Run:

- strict UTF-8/control scan;
- mojibake scan;
- escaped Cyrillic scan;
- XML/TSV validation where applicable;
- `git diff --check`;
- exact staged allowlist;
- production change count = 0.

One ordinary commit.

SUCCESS subject:

`phantom(goal-039): freeze declared full vision`

Non-force push.

Require final:

`HEAD == origin/feature/phantom-world`

and exact marker present.

## BLOCKED path

Only for a new proven independent blocker or exhausted same-family environment
budget.

Subject:

`phantom(goal-039): record resume 8 blocker`

No completion marker.
No fake 28/28.
No Goal040.
