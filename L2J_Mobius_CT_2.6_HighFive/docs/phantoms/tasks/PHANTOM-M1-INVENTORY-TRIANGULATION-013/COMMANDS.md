# Task013 commands

Все write/build/native/commit/push выполняются из isolated worktree. FOREIGN checkout только read. Нет subagents.

## Build / focused tests

```powershell
ant -q '-Dbuild=.phantom-local/build013' compile-tests
java '-Xmx4096m' "-Dphantom.module.root=$taskModule" "-Dphantom.test.reports=$taskModule/.phantom-local/triangulation-admission" "-Dphantom.test.config=$taskModule/.phantom-local/Database.test.ini" '-Dphantom.m1.world.mode=historical_context' '-cp' "$taskModule/.phantom-local/build013/bin;$taskModule/.phantom-local/build013/phantom-test/bin;$taskModule/.phantom-local/build013/phantom-test/resources;$taskModule/dist/libs/*" org.l2jmobius.tests.phantoms.PhantomTestLauncher m1-production-world 15001501
ant -q '-Dbuild=.phantom-local/build013' '-Dbuild.test.reports=.phantom-local/contracts013' '-Dphantom.m1.native.focus=native-context-contract' phantom-m1-native-lifecycle-test phantom-population-ecology-goal033-test phantom-population-ecology-handoff-regression-test
java '-cp' "$taskModule/.phantom-local/build013/bin;$taskModule/.phantom-local/build013/phantom-test/bin;$taskModule/.phantom-local/build013/phantom-test/resources;$taskModule/dist/libs/*" "-Dphantom.test.reports=$taskModule/.phantom-local/handoff013" org.l2jmobius.tests.phantoms.PhantomTestLauncher population-ecology-handoff-regression 33003300
ant -q '-Dbuild=.phantom-local/jar013' jar
```

Native command cwd=isolated dist/game. Initial RED added '-Dphantom.m1.inventory.expectedDiffs=5', reports=triangulation-red. Reports green/proof/final/admission preserve successive bounded diagnosis. The combined Ant command passed first two suites but its final handoff target does not exist; command exit1. Actual handoff launcher exit0/6 of6. One initial Java launch had unquoted PowerShell -D parsing failure; one concurrent Ant/native attempt had missing classes before fixture apply. No credit from failed launches.

## Git inventory/base commands used

Authorized by explicit isolated-worktree/base/exact-diff/publication request and task GIT.md:

```text
git rev-parse --git-dir --git-common-dir --show-superproject-working-tree
git branch --show-current
git status --short
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git worktree list --porcelain
git rev-parse 88b7dd76643cb80b78246668cd16052e740a55aa
git log -5 --format='%H %P %s' ae6f0236f7f16039eb4b1e03f4df009943c59b91
git merge-base --is-ancestor ae6f0236f7f16039eb4b1e03f4df009943c59b91 88b7dd76643cb80b78246668cd16052e740a55aa
git merge-base --is-ancestor 88b7dd76643cb80b78246668cd16052e740a55aa ae6f0236f7f16039eb4b1e03f4df009943c59b91
git diff --stat 88b7dd76643cb80b78246668cd16052e740a55aa ae6f0236f7f16039eb4b1e03f4df009943c59b91
git diff --name-status 88b7dd76643cb80b78246668cd16052e740a55aa ae6f0236f7f16039eb4b1e03f4df009943c59b91
git diff --check 88b7dd76643cb80b78246668cd16052e740a55aa ae6f0236f7f16039eb4b1e03f4df009943c59b91
git switch --detach ae6f0236f7f16039eb4b1e03f4df009943c59b91
git rev-parse HEAD
git diff --exit-code -- dist/db_installer/sql test/resources/phantoms/db/migrations
git diff --stat -- dist/db_installer/sql test/resources/phantoms/db/migrations
git diff --exit-code -- java
git diff --stat -- java test/java
git diff --numstat -- java test/java
git diff -- java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java
git diff -- test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java
git diff --check -- java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java test/java/org/l2jmobius/tests/phantoms/PhantomM1PopulationFixture.java
```

Native create_worktree initially exact88, then approved detached ae6 base. No Git mutation in FOREIGN/main checkout. Six SQL files temporarily use fixture-compatible LF bytes in NEW worktree; normalized Git diff empty; never stage them. Private reports/build/runtime/config/dumps are never staged.

## Exact local operations

Private Set-ExactTestAdmin013.ps1: exact PLAY SELECT→CAS UPDATE with observed accesslevel0 and online0→SELECT; affectedRows1. No secrets in evidence.
Private Stop-ExactOwnedGracefully.ps1: exact clone DB logout/store/real-online checks, PID/incarnation/runtime checks, Attach public stock shutdown request, port absence. Game old14472/Login old17672; no force. Login-only retry after classloader failure.
Private Prepare-Runtime.ps1 is adapted from existing observe011 script with clone013 and GM four startup flagsFalse, preserving source PLAY file hashes.

## Publication / final verification

Executed in the isolated worktree:

```text
git add -- java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java test/java/org/l2jmobius/tests/phantoms/PhantomM1PopulationFixture.java <each of the 24 enumerated task013 docs files>
git diff --cached --check
git diff --cached --name-status
git commit -m 'Fix committed inventory projection at Phantom native arrival'
git rev-parse HEAD
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

Source commit c23915df10239bfab15ae49276e14833268b9afc, normal push ae6f0236→c23915df102. The 24 exact doc paths are the task directory paths in FINAL_SCOPE.tsv excluding later RUNTIME_READY/OBSERVATION/REAL_LOGOUT/GRACEFUL_STOP_RESULT/FINAL_SCOPE. No directory-wide or wildcard stage, no add-dot.

Runtime operations actually issued:

```powershell
& ./.phantom-local/observe013/tools/Prepare-Runtime.ps1 -ModuleRoot $PWD
& ./.phantom-local/observe013/runtime/Start-LocalPlay.ps1 -Background
& ./.phantom-local/observe013/runtime/Check-LocalPlay.ps1
& ./.phantom-local/observe013/runtime/Get-LocalPlayPilot.ps1
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation STATUS -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SELECT_VISIBLE_PHANTOM_TRACE -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SELECT_VISIBLE_PHANTOM_TRACE -RunId 1439a53b-521a-47b7-b02a-0be01c9b9b92 -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SELECT_VISIBLE_PHANTOM_TRACE -RunId 902b2528-1983-4797-9dcf-1650e25c5c8d -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SNAPSHOT_PHANTOMS -RunId 902b2528-1983-4797-9dcf-1650e25c5c8d -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SNAPSHOT_M1_ENVELOPE -RunId 902b2528-1983-4797-9dcf-1650e25c5c8d -TimeoutSeconds 10
& ./.phantom-local/observe013/runtime/Invoke-LocalPlayPilot.ps1 -Operation SELECT_VISIBLE_PHANTOM_TRACE -RunId 902b2528-1983-4797-9dcf-1650e25c5c8d -TimeoutSeconds 5
& ./.phantom-local/ops013/Stop-ExactOwnedGracefully.ps1 -RuntimeRoot <exact observe013 private runtime>
```

Last SELECT occurred after120s and is explicitly excluded. No arm/PREPARE/gameplay operation. Full results in OBSERVATION.tsv; source-only read confirmed locality snapshot limits. Final stop after user's «вышел» checked exact DB offline/store first, then stock graceful Game26344/Login18056, no force.

Final docs-only stage uses these exact task paths: COMMANDS.md, HANDOFF_RESULT.md, RESULT.md, WORK_LOG.md, RUNTIME_READY.md, OBSERVATION.tsv, REAL_LOGOUT.tsv, GRACEFUL_STOP_RESULT.txt, FINAL_SCOPE.tsv. Final commit message 'Record observe013 runtime boundary and safe real-player logout'. Normal push uses the same HEAD:refs/heads/experiment/m1-candidate007-observe008 destination.

Final read checks: git diff --name-only ae6f0236f7f16039eb4b1e03f4df009943c59b91 HEAD; git diff --cached --name-only; git diff --cached --check; git diff --exit-code -- java test/java; git diff --exit-code -- dist/db_installer/sql test/resources/phantoms/db/migrations; git status --short; git rev-parse HEAD; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008. All are authorized exact scope/publication checks. FINAL_SCOPE enumerates committed paths; no SQL/private runtime/config/dump/binary stage.
