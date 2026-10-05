# Git usage TASK016

Разрешение: прямой запрос пользователя на exact remote inspection, isolated worktree, exact-path commit и normal push; TASK016/GIT.md.

До изменений:

```text
git branch --show-current
git rev-parse HEAD
git rev-parse origin/experiment/m1-candidate007-observe008
git status --porcelain=v1
git worktree list --porcelain
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git worktree add --detach C:\Users\ZBook\.codex\worktrees\m1-orphan-016\L2J_Mobius origin/experiment/m1-candidate007-observe008
```

Первый ls-remote без escalation не смог подключиться; повтор с разрешённым network access подтвердил exact685c66fb base. Fetch не нужен, remote-tracking SHA совпал. Экспериментальная local branch занята другим worktree; detached checkout предотвращает изменение чужой local branch.

Scope/review:

```text
git rev-parse HEAD
git status --porcelain=v1
git diff --check
git diff --stat
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java
```

Далее planned: exact-path add для двух разрешённых source/test files и только TASK016 directory; diff --cached --check/name-only; commit; normal push origin HEAD:refs/heads/experiment/m1-candidate007-observe008. Private runtime/DB exports/secrets не staging. Runtime private catalog helper использует git show HEAD:exact catalog path только для трёх canonical population/ecology catalogs; semantic equality проверяется до private write.

Ни reset, clean, stash, rebase, force, broad add, ни переключение/очистка основной копии не выполнялись.

Executed additionally: git diff685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d --check/--stat; git add -- exact two Java paths and exact TASK016 directory; git diff --cached --check/--stat; git commit -m 'phantom(task-016): recover exact orphan historical ownership'; git commit -m 'phantom(task-016): preserve existing source line endings'; git diff685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d HEAD --check. Correction не переписывала историю. Final diff check PASS.
