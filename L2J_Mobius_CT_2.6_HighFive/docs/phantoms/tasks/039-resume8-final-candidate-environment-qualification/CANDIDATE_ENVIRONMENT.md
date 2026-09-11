# Final candidate environment contract

## Source checkout

Operator Git root:

`C:\Users\ZBook\L2J_Mobius\`

Module:

`C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`

Source precondition:

`HEAD == origin/feature/phantom-world == 539688cda76c06bf48528f210cbff03524818871`

Dirty user files in the operator tree are allowed and must be preserved.

## Candidate location

Create a unique real directory OUTSIDE:

`C:\Users\ZBook\L2J_Mobius\`

Example shape only:

`C:\Users\ZBook\L2J_Goal039_RC8_<timestamp>_<nonce>\`

The selected directory must not already exist.

Do not use `%TEMP%` if it resolves through a reparse/junction chain that cannot
be proven stable. Prefer an ordinary directory under `C:\Users\ZBook\`.

Record:

- candidate repo root;
- candidate module root;
- parent of candidate root;
- filesystem attributes;
- whether any ancestor is a reparse point.

Candidate root/module/`.phantom-local` themselves must not be reparse points.

## Creation

Use one normal isolated local clone directly at the final path:

```text
git -c core.longpaths=true clone --local --no-hardlinks --branch feature/phantom-world --single-branch C:\Users\ZBook\L2J_Mobius <candidateRepoRoot>
```

This exact clone operation and its initial checkout are allowed in the disposable
candidate.

Immediately require inside candidate:

```text
git branch --show-current == feature/phantom-world
git rev-parse HEAD == 539688cda76c06bf48528f210cbff03524818871
git rev-parse --show-toplevel == <candidateRepoRoot real path>
git status --porcelain == empty
```

Also prove the candidate repo root is NOT under the operator repo root.

Do not fetch/pull inside candidate after creation.

## Runtime-only test files

Create:

`<candidateModule>\.phantom-local\`

as a normal directory.

Copy from operator module, without printing secrets:

- `.phantom-local\Database.test.ini`
- `.phantom-local\schema-manifest.properties` if required by current tests.

Before Ant:

- source test config must be a normal readable file;
- candidate copy must be a normal readable file;
- PowerShell `Resolve-Path`/real path must succeed;
- Java/NIO-equivalent real path is exercised by the canary tests;
- candidate `.phantom-local` must not be a reparse point.

Never use production `dist/game/config/Database.ini` as test config.

Do not run prepare.

## Resume-8 test-only overlay

Before final qualification/full verify, update only:

`test/java/org/l2jmobius/tests/phantoms/PhantomFullVisionGoal039Suite.java`

so:

`REQUIRED_PARENT = "539688cda76c06bf48528f210cbff03524818871"`

Copy this exact file from the operator working tree into the same path in the
candidate.

No other operator tracked dirty file may be copied.

Task package docs do not need to be part of the runtime candidate.

Run diff/fingerprint checks proving the only committed-source overlay at this
point is that test file.

## JAR/zipfs qualification

Run a non-final qualification build:

`ant -q jar`

Then record SHA-256 + bytes for the qualification JARs, labelled CANARY_ONLY.

Require normal readable files:

- `dist/libs/LoginServer.jar`
- `dist/libs/GameServer.jar`

Exercise both with the JDK archive reader, e.g.:

- `jar tf dist/libs/LoginServer.jar`
- `jar tf dist/libs/GameServer.jar`

Both must exit 0.

Then run:

- `ant -q compile-tests`
- `ant -q test`
- `ant -q test` a second consecutive time

The two `test` runs must both PASS in the same unmoved candidate.

This specifically qualifies:

- classpath/JAR readability;
- repeated Java process lifecycle;
- DB config real-path safety;
- no stale zip/JAR handle problem.

Do not count the canary jar as the final standalone JAR.

## Git-context qualification

With qualification JARs present, run:

`ant -q phantom-static-verify-014`

It must PASS in this candidate.

Record:

- verifier branch;
- verifier repository root;
- module-relative prefix;
- `TASK014_VERIFIER_OK`.

Then run:

`ant -q phantom-full-vision-goal039-static-test`

Expected current static suite: 7/7 PASS.

This also detects wrong checkout normalization/hash behavior before the expensive
verify.

## DB qualification

Run current safe preflight/guard checks required by Resume 7, including:

- DB guard negative control;
- local-play preflight/readiness path that exercises the guarded test config.

No provisioning.

Production DB connection attempt count must remain zero.

## Qualified marker

Only after ALL candidate canaries pass, write task-owned evidence:

`CANDIDATE_ENVIRONMENT_QUALIFIED`

with:

- candidate path;
- parent SHA;
- branch;
- Git root;
- Java version;
- Ant version;
- jar canary SHA/bytes;
- two `ant test` results;
- Goal014 result;
- Goal039 static result;
- DB config real-path result.

Only this marker permits full `ant verify`.

The marker is evidence only and is not production source.
