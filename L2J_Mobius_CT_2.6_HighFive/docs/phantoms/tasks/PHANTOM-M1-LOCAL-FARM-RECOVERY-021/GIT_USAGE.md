# TASK021 — разрешённые Git-команды

TASK.md прямо разрешает read/fetch, exact-path worktree, exact add/commit и normal push
в experiment/m1-candidate007-observe008. Основной checkout использован только read-only.

Команды из основного checkout: git rev-parse --git-dir; git rev-parse --git-common-dir;
git branch --show-current; git worktree list --porcelain;
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008;
git cat-file -t 2bf2936083bf6b081104c3e03c254ab8f07afc87;
git merge-base --is-ancestor 2bf2936083bf6b081104c3e03c254ab8f07afc87 origin/experiment/m1-candidate007-observe008;
git rev-parse origin/experiment/m1-candidate007-observe008;
git worktree add --detach C:/Users/ZBook/.codex/worktrees/m1-local-farm-021/L2J_Mobius 2bf2936083bf6b081104c3e03c254ab8f07afc87.

В isolated worktree: git rev-parse HEAD; git status --short --untracked-files=normal;
git diff --stat; git diff --numstat; git diff --check;
git diff -- <пять точных production paths из SOURCE_MAP.tsv>;
git diff --name-only 2bf2936083bf6b081104c3e03c254ab8f07afc87.
Verifier проверяет exact allowlist и staged paths. Git add вызывается с каждым exact path
из ARTIFACT_ALLOWLIST.json отдельно, без add-dot. Commit/push записываются в RESULT.

Prepare-Runtime использует git rev-parse HEAD и git show HEAD:<три exact population
catalog paths> только для private byte-identical CRLF normalization. Base cardinality
проверяется read-only git show required-base:<exact travel catalog path>.
После каждого normal push: git ls-remote origin refs/heads/experiment/m1-candidate007-observe008.
Reset/restore/stash/clean/rebase/force и изменение основного checkout не выполнялись.

Exact final staging commands (cwd=HighFive module):
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/ACCEPTANCE.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/ARTIFACT_ALLOWLIST.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/BASE-m1-native-lifecycle.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/BASE-normal-gatekeeper-travel.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/BUILD.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/CLONE_INITIAL021.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/CONFIG_OVERRIDES.tsv
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/DESIGN.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/ENGINEERING_REVIEW.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/EVIDENCE021.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GIT_USAGE.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GOAL.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GRACEFUL_STOP.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GREEN-COMPILE.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GREEN-PhantomLocalFarmRecoverySuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GREEN-PhantomVisibleIntentRecoverySuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/HANDOFF.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Inspect-Prestate021.py
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/LOGOUT_AFTER_STOP.tsv
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/LOGOUT_BEFORE_STOP.tsv
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/OBSERVE021.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Observe-Visible021.ps1
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PACKAGE_MANIFEST.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PATCH_GUIDE.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PLAN.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/POSTSTATE021.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PRESTATE021.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PROGRESS.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PUBLICATION.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Prepare-Runtime.ps1
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RED-COMPILE.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RED-LOCAL-COMPILE.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RED-PhantomLocalFarmRecoverySuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RED-PhantomVisibleIntentRecoverySuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RED.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/REGRESSIONS-PhantomNativeContextHandoffSuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/REGRESSIONS-PhantomVisibleDecisionAdmissionSuite.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RESULT.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/REVIEW_NOTES.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RUNBOOK.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RUNTIME_PRECHECK.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RUNTIME_PREPARE.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RUNTIME_READY.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/RUNTIME_START.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Restore-Private-Catalogs.py
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Run-Engineering.ps1
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/SCENARIOS.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/SCOPE_ENCODING.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/SOURCE_MAP.tsv
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/STOP_VERIFY.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Stop-and-Verify.ps1
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/TASK.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/Verify-Scope021.py
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/background-lifecycle-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/decision-core-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/decision-persistence-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/m1-native-lifecycle-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/normal-gatekeeper-travel-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/population-ecology-goal033-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/population-ecology-handoff-regression-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/recorder-GREEN.log
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomLocalFarmRecoverySuite.java
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomVisibleIntentRecoverySuite.java
git -C .. diff --cached --name-only
git -C .. diff --cached --check
git -C .. commit -m 'Record TASK021 blocked native continuation and observe021 evidence'
git -C .. push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C .. ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C .. status --short --untracked-files=normal

Final artifact integrity correction (PLAN.md changed to factual outcome):
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/PACKAGE_MANIFEST.json
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/GIT_USAGE.md
git -C .. add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-LOCAL-FARM-RECOVERY-021/SCOPE_ENCODING.log
git -C .. commit -m 'Synchronize TASK021 instruction artifact hashes'
git -C .. push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C .. ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C .. status --short --untracked-files=normal
