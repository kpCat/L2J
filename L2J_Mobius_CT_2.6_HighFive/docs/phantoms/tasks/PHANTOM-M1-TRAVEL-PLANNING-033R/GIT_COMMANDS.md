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
