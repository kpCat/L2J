# Git permissions034 — exact and conservative

Remote `origin`, branch `experiment/m1-candidate007-observe008`, required base `44139ca556ad3d5ac35309d5fe222269d3ada256`. Detached owned worktree is permitted and preferred. Do not merge into `feature/phantom-world` in TASK034.

Permitted read-only: exact `git rev-parse`, `git ls-remote origin`, `git worktree list --porcelain`, `git status --short --untracked-files=no` scoped to own paths, `git diff -- <exact known paths>`, `git diff --stat -- <exact paths>`, `git show <exact SHA>:<exact known file>`, `git log -n 5 --format=...`.

Permitted scoped setup: `git -C C:/Users/ZBook/L2J_Mobius worktree add --detach <exact-owned-reusable-path> 44139ca556ad3d5ac35309d5fe222269d3ada256` **only if** target absent and unregistered. Existing owned tree: inspect source+status first; if clean and detached older commit, `git -C <own> fetch origin experiment/m1-candidate007-observe008` then `git -C <own> switch --detach 44139ca556ad3d5ac35309d5fe222269d3ada256` only after positive clean/safe check; if uncertain BLOCKED_WORKTREE. Fetch only named ref. Do not checkout on user's main worktree.

Permitted writes: `git -C <own> add -- <literal exact task Java/test/task-doc paths>`; inspect staged names/diff/whitespace; `git -C <own> commit -m 'TASK034: ...'`; `git -C <own> push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` normal non-force; `git -C <own> rev-parse HEAD`; `git -C <own> ls-remote origin refs/heads/experiment/m1-candidate007-observe008`. The last two must match. If push non-FF because branch advanced, stop BLOCKED_REMOTE, never force/reset/rebase automatically.

No `git reset`, `git clean`, `git prune`, `git worktree prune`, `git branch -D`, `git push --force`, `git push --force-with-lease`, `git rebase`, auto-merge, `git restore` broad, `git add .`, deleting branch or entire `.codex/worktrees`. Any `git worktree remove` only own verified tree after archive+process guards, without force, from outside path. No changes in repository user's other worktrees.

Report changed files and exact commit/parent/runtime Jar SHA. Do not claim remote publication until read-back confirms same public SHA.

The task packet arrives in the user's MAIN checkout as untracked files. It must be SHA-verified and copied narrowly into the owned worktree before any coding; `git worktree add` does not move these files. Read-only access to the exact task folder in main is permitted, write access there is not.
