# C — Реальная Java-проверка staged v3 и неисполняемый handoff

## Native content oracle (НЕ игровая runtime parity)

Design **separate** `scripts/Test-PSS008-V3-Java.ps1` (and narrow Java probe if needed): PSS003 operator is scoped to custom overlay and cannot be relabeled for v3. May reuse proven PSS003 audited physical-copy technique, SHA guards, limited Ant target; must verify its source/target/recipient assumptions **before** reuse.

1. Operator accepts only exact finished `workspace/v3-proposals/<32hex>` under own Studio workspace, no symlinks/junctions/reparse at any ancestor; validates read-only source stamps/receipt approval IDs/target diff exactness *before* creating scratch.
2. Copy READ-ONLY from High Five to separate `workspace/v3-proposals/<id>/oracle-<guid>/` only audited allowlist: build.xml, `java/**/*.java`, `test/java/**/*.java`, bounded test resources, required dist/libs JAR but not GameServer/LoginServer/sources JAR. Copy/verify every SHA and size; no config/DB/.phantom-local/.git/server/client/process/output copied.
3. Prohibit original `ant -f <HighFive>\build.xml` or tools' working directory inside L2J. Validate reachable Ant tasks, dependencies, classpaths and output/temp properties; physically constrain ALL outputs to own shadow regardless `build.xml` defaults. JDK25 and Ant; missing dependencies => `BLOCKED_JAVA`, never fake PASS.
4. Run narrow `phantom-humanized-v3-content-validate` only on safe physical copy. Run real `PhantomHumanizedCatalog.loadV3(<stage shadow phantomDataRoot>, true)` AND same for baseline shadow, compare exact counters/delta, loaded file/candidate IDs, act/topic, `understand` PATTERN and eligible `select` TEMPLATE (if zero probability bounded attempts, explain limit and never fake successful selection). Check stable combinedHash and source SHA unchanged. Include negative staged *second* copy with duplicate existing/new ID or invalid schema, prove actual loader rejects (native nonzero expected, distinguish from harness error).
5. Recheck source, staged and java bridge bytes/hashes after execution. Independent immutable `java-validation.json` binds exact stage receipt fingerprint, target files hashes, actual source stamps, Java commands/exit/counters/hashes. Original C# stage receipt status stays `STAGED_V3_UNVALIDATED`; no rewriting it to assert PASS. `PASS_JAVA_STAGED_V3 / JAVA_CONTENT_ONLY_NOT_RUNTIME_READY` only for this exact proof.
6. Do not run full ant test suite, server/client/DB, geodata, gameplay/sim, Git or Java in source. Max 4000 files / 256MiB and >=512MiB available scratch as safe precedent; any cap/change in local Java/build contract ⇒ BLOCKED_JAVA before execution.

## Dialog regression / scope

Replay a *small synthetic controlled set* through actual catalog selectors in Java: representative matching PATTERN and eligible TEMPLATE, unmatched input, exact duplicate negative, wrong act/band/register, placeholder/functional blocked in staging; don't assert real client/social/player AI correctness. Plain C# `PackPreview` comparisons use `CATALOG_APPROXIMATE` status, not Java parity. Optional operator-selected real lab tests are private workspace only; never commit/print raw transcripts.

## Handoff (NO installation implementation)

Only after operator executes Java oracle and exact proof PASS for current immutable stage, and third independent default-NO confirmation reviewing files/hashes, optionally prepare directory:

```
workspace/release-candidates/<guid>/
   proposed/<exact 1-2 relative v3 segment paths>
   backup/<same relative paths, byte-exact original>
   release-manifest.json
   CHECKLIST_RU.txt
```

- `release-manifest.json`: exact original/staged SHA256 & sizes, relative allowlisted paths, stage-id, Java validation proof SHA, expected current source fingerprint, schema version, status `OFFLINE_HANDOFF_NOT_INSTALLED`. **NOT** server-ready/runtime certified; no executable files/scripts, no archive ZIP, no changing L2J. CHECKLIST_RU describes a future separate manual maintenance/backup/recheck/apply/verify/rollback decision. This task must NOT perform that decision or automatic copy. Original backup and proposed material live only in ignored private workspace; no raw XML in tracked reports.
- A stale/missing/modified proof or source drift, wrong target delta, missing operator consent, cancelled operation, low disk, malicious path => NO release candidate; stage remains unmodified. An unvalidated stage never becomes release artifact simply because UI said "OK".
- If user explicitly requests real production deployment in a future task, require fresh authorisation, maintenance window, verified backups and new read-only proof. Current PSS008 must not include any Apply/Install/Deploy function even disabled.

## Required evidence

- Java native exit codes and actual stdout with synthetic IDs/counters, baseline/staged hashes, negative exit, source SHA equality. Do not publish raw user texts/tokens/PII/paths revealing personal files beyond allowed generic software paths.
- Pre-release stale-mutated proof test, hash mismatch test and cancellation; no release directory. Validate backup byte equality and proposed pair only in full success fixture. Manual release bundle **folder** never enters Git.
- Java NOT_RUN/BLOCKED is not GREEN; still report successful A/B and no final release. No inheritance of historical PSS003 custom proof.
