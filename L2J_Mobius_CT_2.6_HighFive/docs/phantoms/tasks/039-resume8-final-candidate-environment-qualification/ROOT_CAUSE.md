# Root cause and non-solutions

## Root cause family

`FINAL_CANDIDATE_ISOLATION_AND_FILESYSTEM_STABILITY`

The release candidate was not treated as a first-class, stable working copy.

The historical static verifier requires real Git topology, while Java/Ant/DB
guard require stable real filesystem paths.

A plain archive nested inside the operator repo satisfies neither requirement
reliably.

Moving a populated candidate after test/build activity is also unsafe on Windows:
it changes canonical paths and can interact badly with open/just-closed archive
handles.

## Correct model

The final release candidate is a disposable but ordinary isolated Git clone:

```text
<unique external rc root>\
    .git\
    L2J_Mobius_CT_2.6_HighFive\
        build.xml
        java\
        test\
        dist\
        .phantom-local\
```

Properties:

- outside the operator repo;
- its own `.git`;
- exact branch;
- exact parent commit;
- normal checkout semantics;
- real directories, not reparse points;
- DB test config copied into its own `.phantom-local`;
- never renamed/moved after creation;
- all Ant/Java processes run to completion before the next step.

## Why normal isolated clone is preferred

Use a normal local clone, not a bare exported `git archive`, for the final
candidate.

This automatically gives:

- the Git root historical verifiers expect;
- normal working-tree EOL/filter behavior;
- actual filesystem files;
- no dependency on the operator dirty tree.

Prefer:

`git -c core.longpaths=true clone --local --no-hardlinks --branch feature/phantom-world --single-branch <operatorRepoRoot> <uniqueRcRoot>`

The clone's automatic initial checkout is explicitly authorized ONLY for this
new disposable isolated clone.

Do not run `git checkout`, `reset`, `restore`, `clean`, `stash`, `rebase`,
`merge` or `amend` in the operator repository.

## Never move the candidate

Choose a unique final path before cloning.

Do not:

- clone under the operator working tree;
- build under the operator `.phantom-local`;
- clone somewhere and then `Move-Item`;
- rename the candidate after `.phantom-local` is created;
- use a directory junction for candidate root/module/.phantom-local.

If a qualification attempt is bad, dispose only that task-created unique
candidate after recording its path/fingerprint, then create a NEW unique
candidate directly in its final path.

## Non-solutions

Do NOT:

- change Goal014 verifier to tolerate the wrong Git root;
- replace its Git-history checks with hardcoded current paths;
- weaken `toRealPath`;
- allow DB config outside candidate `.phantom-local`;
- copy production Database.ini;
- change Java version;
- suppress zipfs exceptions;
- exclude LoginServer.jar from classpath;
- run tests in the dirty operator tree;
- replace `17`/`23`/other unrelated assertions;
- rerun the entire 80+ minute domain/scale sequence just to test candidate path.

## Same-family recovery budget

Candidate qualification is intentionally cheap and may be retried before the
full verify.

Up to 3 fresh candidate qualification attempts are allowed for this SAME
environment family.

Each retry must use a new unique external path and must not mutate product code.

After one candidate is QUALIFIED, expensive full verify may start.

If full verify later hits the same environment family despite qualification:

1. record exact failure;
2. one candidate recreation + full requalification is allowed;
3. one final full verify retry is allowed.

If that also fails from the same environment family: BLOCKED_ENVIRONMENT.

A substantive Java/test/gameplay assertion failure is a new independent blocker:
one focused confirmation then STOP.
