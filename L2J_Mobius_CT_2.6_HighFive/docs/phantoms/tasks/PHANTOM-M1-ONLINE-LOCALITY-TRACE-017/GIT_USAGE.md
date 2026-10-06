# TASK017 Git usage

Git разрешён прямым запросом пользователя и TASK017/GIT.md.
Main checkout только read-only; foreign local experiment worktree не изменён.
Native create_worktree создал isolated detached checkout от required base.
Публикация только normal push в ту же remote experiment branch.

Read-only inventory и base checks, исполненные в чате:

```text
git status --short
git branch --show-current
git rev-parse HEAD
git worktree list
git rev-parse --verify experiment/m1-candidate007-observe008
git -C C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius status --short -uno
git -C C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius rev-parse --abbrev-ref --symbolic-full-name @{upstream}
git -c safe.directory=C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius -C C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius status --short -uno
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C C:\Users\ZBook\.codex\worktrees\m1-online-trace-017\L2J_Mobius rev-parse HEAD
git diff --numstat
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java
```

Первый foreign-worktree read получил dubious ownership. Использован только
command-local safe.directory. Первый ls-remote без escalation не имел network;
разрешённый повтор подтвердил required base. Default-sandbox diff в новом worktree
не увидел repository metadata; elevated exact diff выполнен успешно.

Verify017.py выполняет точные scope guard/source reads:

```text
git diff 0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7 --name-only
git ls-files --others --exclude-standard
git show 0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7:L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/topology/PhantomHumanLocalityControl.java
git diff 0f16f29eff4d78d42a8f4da48fc0bc44ad466ad7 --check
```

Exact-path commit/push commands и их результаты дополняются после исполнения.
Prepare-Runtime.ps1 читает git rev-parse HEAD. Restore-Private-Catalogs.py читает
git show HEAD:exact dist/game/data/phantoms/population paths для трёх canonical
high-five-population-v1.xml, high-five-population-v2.xml, high-five-ecology-v1.xml.
Catalog semantic equality проверяется до записи только в private runtime.
Reset/clean/stash/rebase/force/broad add отсутствуют.
