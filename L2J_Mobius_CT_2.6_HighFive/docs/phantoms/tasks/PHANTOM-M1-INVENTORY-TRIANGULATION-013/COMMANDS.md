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

Publication commands and final scope verification will be recorded when executed.
