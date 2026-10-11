# TASK033R — разрешённые Git-команды

Основание: пользовательский GOAL/GIT.md и дополнение WORKTREE CLEANUP.
Все изменения/staging/commit выполняются только в собственном exact-base worktree.
Main и retained032 используются только для bounded identity/inventory reads.

Read-first inventory: `git rev-parse --show-toplevel`, `git rev-parse HEAD`,
`git branch --show-current`, `git status --short`, `git diff --name-only`,
`git log -1 --format=%H`, `git show --no-patch --format=%H df71140e659670e2e3d225605f64689a6d32d281`.
Remote authority: `git ls-remote origin refs/heads/experiment/m1-candidate007-observe008`.
Native Codex create_worktree created the registered detached checkout at the required base.
No new checkout is authorized after the cleanup addition.

Own scope verification: `git -C <own> status --short`, `git -C <own> diff --check`,
`git -C <own> diff --stat`, exact-path `git -C <own> diff -- <paths>`.
Sandbox ownership/network failures were read failures; no global Git configuration was changed.
Commands needing the user's OS ownership/network run with platform escalation.
CRLF-aware whitespace check uses the per-command option
`-c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol`.

Publication: exact manifested paths only via `git add -- <exact paths>`,
`git diff --cached --name-only`, `git diff --cached --check`,
`git commit -m <scoped message>`, `git rev-parse HEAD`,
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`.
Existing Control032 Update helper reads `git -C <own module> rev-parse HEAD` for runtime identity.
Full exact path lists and commit IDs are recorded in the final artifact scope guard.

Final cleanup authority: `git worktree list --porcelain`; only after archive/hash/process/publication
checks, `git -C C:/Users/ZBook/L2J_Mobius worktree remove C:/Users/ZBook/.codex/worktrees/m1-travel-033r/L2J_Mobius`.
If normal removal rejects, record CLEANUP_PENDING; no force/clean/reset/merge/branch deletion.

## Фактическая final bounded inventory

`git ls-files` выполнялся только из own root для USER WORKTREE CLEANUP tracked inventory.
`git diff --name-only df71140e659670e2e3d225605f64689a6d32d281 HEAD`
и `git diff --stat df71140e659670e2e3d225605f64689a6d32d281 HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java`
проверяли только own exact-base→candidate scope. Также выполнены own
`git diff --stat`, CRLF-aware `git diff --check`, `git status --short`,
`git worktree list --porcelain`, `git rev-parse HEAD` и exact branch `git ls-remote`.
Final path allowlist — FINAL_SCOPE_GUARD.tsv, полные commit path inventories — COMMIT_PATHS.tsv.

Для трёх собственных commits выполнены exact inventory reads:

```powershell
git show --format= --name-only d4357795a74a8dacdb725df5d1f1ddbf1115d6c5
git show --format= --name-only cdc9d8b514904cdc7632965b323228581d356068
git show --format= --name-only 51cf051b3d20fbb83e6c0e16ce8c7bcad7af52cb
```

Own commit commands (cwd=`C:/Users/ZBook/.codex/worktrees/m1-travel-033r/L2J_Mobius`):

```powershell
git add -- 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/.gitignore' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/ACCEPTANCE.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/DESIGN.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/ESCAPE_AND_RECOVERY.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/GIT.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/GIT_COMMANDS.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/GOAL.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/HANDOFF.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/MANIFEST.sha256' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PATCH_GUIDE.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PLAN.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PROGRESS033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/README.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/RUNBOOK.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/Run033R.ps1' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/SCENARIOS.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/SOURCES.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/SOURCE_MAP.tsv' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/STATE_MACHINE.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/TASK.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/USER_ROUTE_SKETCH.png' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/VERIFICATION.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/proposals/BoundedTravelSearch033R.java' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/proposals/BoundedTravelSearch033RTest.java' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/proposals/README.md' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTravelPlanning033RSuite.java'
git commit -m 'TASK033R: prove bounded native WALK corridors before movement'

git add -- 'L2J_Mobius_CT_2.6_HighFive/dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PROGRESS033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/Run033R.ps1' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeTravelActions.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomTravelStrategy.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTravelPlanning033RSuite.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomVisibleContinuity032Suite.java'
git commit -m 'TASK033R: select complete lawful travel and own native return timers'

git add -- 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PROGRESS033R.md' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNativeTravelActions.java' 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTravelPlanning033RSuite.java'
git commit -m 'TASK033R: retain native corridor with wrappers and stock GK interaction'
```

Final verification/report exact paths и normal publication commands:

```powershell
git add -- 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/CLEANUP033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/RESULT_HANDOFF033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/SOURCE_REVIEW033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/PROGRESS033R.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/GIT_COMMANDS.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/MANIFEST.sha256' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/FINAL_SCOPE_GUARD.tsv' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/CODE_SHA256.tsv' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/COMMIT_PATHS.tsv' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTravelPlanning033RSuite.java' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/README.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-TRAVEL-PLANNING-033R/VERIFICATION.md'
git diff --cached --name-only
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check
git commit -m 'TASK033R: verify native route and publish PARTIAL handoff'
git rev-parse HEAD
git log --format=%H:%P df71140e659670e2e3d225605f64689a6d32d281..HEAD
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git status --short
git diff --name-only df71140e659670e2e3d225605f64689a6d32d281 HEAD
git worktree list --porcelain
git -C C:/Users/ZBook/L2J_Mobius worktree remove C:/Users/ZBook/.codex/worktrees/m1-travel-033r/L2J_Mobius
git -C C:/Users/ZBook/L2J_Mobius worktree list --porcelain
```

Порядок final publication/removal завершения подтверждают внешние PUBLICATION.json
и CLEANUP_RESULT.json; этот файл фиксирует разрешённые exact commands до своего commit.
Нормальный отказ removal означает CLEANUP_PENDING. No merge и no branch deletion.
Три pyc, созданные C69 в own old-helper directory, удалены обычными exact-file
Remove-Item после UTC creation/hash/path guards. Это не Git restore/clean и не
изменение старых tracked sources; metadata сохранена в GENERATED_TEMP_CLEANUP.json.
