# Git commands — task011

Git использован по прямому требованию пользователя и GIT.md task011:
read/fetch allowed, exact-path add/commit, normal push same experiment branch.
Рабочая папка для изменений:
`C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius`.
Ниже команды сгруппированы по назначению; повторные read invocations не дублируются.

Inventory/base/upstream/scope verification:

```text
git status --short
git status --short --untracked-files=no
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git rev-parse --abbrev-ref --symbolic-full-name '@{u}'
git worktree list --porcelain
git remote get-url origin
git diff --check
git diff --stat
git diff --name-only
git diff --cached --check
git diff --cached --name-only
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java
git diff -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java
git log -2 --format='%H %s'
git diff 26061ccff5aa22450ff3c4a01a2636e660f879e9 HEAD --name-only
```

Exact engineering commit/push, receipt
`8457b90723e3c8ff6b419080bb2f84bfd80d6638`:

```text
git add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/ENGINEERING.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/RED.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/GREEN.txt
git commit -m 'phantom(task-011): unblock native-context materialization demand'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

Exact prelogin evidence commit/push, receipt
`95ba1150d67b6ba0adaef96158651a1ef4df277e`:

```text
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/PRELOGIN.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/CONFIG_OVERRIDES.tsv
git commit -m 'docs(phantoms): record observe011 prelogin gate'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

Final exact runtime evidence publication (receipt is returned to the user after
the command succeeds; no self-referential SHA is invented):

```text
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/HANDOFF.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/OBSERVATION.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/NATIVE_INCIDENTS.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/CLONE_CHECK.tsv L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-MATERIALIZATION-LIVENESS-011/GIT_COMMANDS.md
git commit -m 'docs(phantoms): record observe011 materialization boundary and stop'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git rev-parse HEAD
git rev-parse refs/remotes/origin/experiment/m1-candidate007-observe008
git status --short
```

Final staged scope must be exactly these six task011 files. Final accumulated
scope relative to the required base must be exactly the two allowed production
services, one targeted test, and task011 evidence. No force push, broad add,
reset, clean, stash, rebase, merge or foreign source change was performed.
