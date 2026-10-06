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
