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

Выполнены exact-path add для двух разрешённых source/test files и только TASK016 directory; diff --cached --check/name-only; commits; normal push origin HEAD:refs/heads/experiment/m1-candidate007-observe008. Private runtime/DB exports/secrets не staging. Runtime private catalog helper использует git show HEAD:exact catalog path только для трёх canonical population/ecology catalogs; semantic equality проверяется до private write.

Ни reset, clean, stash, rebase, force, broad add, ни переключение/очистка основной копии не выполнялись.

Executed additionally: git diff 685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d --check/--stat; git add -- exact two Java paths and exact TASK016 directory; git diff --cached --check/--stat; git commit -m 'phantom(task-016): recover exact orphan historical ownership'; git commit -m 'phantom(task-016): preserve existing source line endings'; git diff 685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d HEAD --check. Correction не переписывала историю. Final diff check PASS.

Engineering commits: 5833582b347aaea807e35734b48317bc5409d8a8, 19b2ff90e0be917f548984f73abec19a8f57416a, aaffa76249e919b170ca91f37bcd4fc968e14e97. Normal push remote685c66fb → aaffa762 succeeded before runtime. Final artifact commit does not change production/test.

Exact final review/commit/push commands in the isolated checkout:

```text
git diff 685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d --name-only
git diff 685c66fb8b1a3cad5b5f8f5c64b48736ebf66c8d --check
git status --porcelain=v1
git diff --stat
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-ORPHAN-CATCHUP-OWNERSHIP-016
git diff --cached --check
git diff --cached --name-only
git commit -m "phantom(task-016): record bounded runtime and safe shutdown"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git rev-parse HEAD
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git status --porcelain=v1
```

Verify016.py uses the listed BASE --name-only inspection for exact allowlist. Restore-Private-Catalogs.py reads only:

```text
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v1.xml
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-population-v2.xml
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/high-five-ecology-v1.xml
```

The helper's exact catalog paths are authoritative; no tracked catalog was changed. Final command outputs are retained by this chat; final remote SHA is reported after verification.
