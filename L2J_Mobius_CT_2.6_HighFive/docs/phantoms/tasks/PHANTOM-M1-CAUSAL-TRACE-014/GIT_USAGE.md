# Git authorization and command audit

User explicitly authorized worktree selection/creation from exact remote/base, GOOD→BAD history inspection, exact-path commits and normal push. Task GIT.md permits ancestor history and those mutations. Original checkout files/foreign diff were not changed, switched, reset, stashed, cleaned or rebased. The new worktree shares normal Git metadata.

Read-only/worktree resolution commands:

```text
git branch --show-current
git rev-parse HEAD
git status --short
git worktree list --porcelain
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git fetch origin refs/heads/experiment/m1-candidate007-observe008:refs/heads/experiment/m1-candidate007-observe008
git worktree add -b codex/m1-causal-014 C:\Users\ZBook\.codex\worktrees\m1-causal-014\L2J_Mobius origin/experiment/m1-candidate007-observe008
```

The existing m1-inventory-013 worktree was detached at the required base; its foreign changes were untouched. Fetch to the occupied local experiment branch was refused. The new exact-path isolated worktree starts at the required remote/base and uses its own codex branch; push explicitly targets the requested experiment branch without switching another checkout.

History/scope verification commands (exact SHA/path arguments are recorded in the generator source and matrix; placeholders below denote those actual arguments):

```text
git log --reverse --format=@@%H\t%ad\t%s --date=short --name-only 561c84a2dc23d6dd953e755e2fafcfbddcd5d395..c23915df10239bfab15ae49276e14833268b9afc
git diff-tree --no-commit-id --name-only -r <history SHA>
git show --format= --ignore-space-at-eol --unified=0 <matrix SHA> -- <matrix exact files>
git show --ignore-space-at-eol --format=format:%H\ %s --unified=1 <suspect SHA> -- <readiness/ecology/history/composition exact files>
git show --ignore-space-at-eol --unified=3 8457b90 -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java
git show HEAD:L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/<pinned catalog>
git status --porcelain --untracked-files=all
git diff --stat
git diff --name-only -- <High Five java path>
git diff --name-only ccacd6c5bf8fa1234a5559ece708ee3a35efd536
git diff -- <reviewed exact source files>
git diff --check
git diff --cached --stat
git diff --cached --check
```

The first per-commit diff-tree inventory was superseded by one batched history read in Build-History.py. No runtime bisect.

Source stage used `git add --` with the nine exact CODE entries in Verify-Task014.py (recorder, seven existing hooks/Pilot files, one focused test) and the exact TASK014 directory. Commit command:
`git commit -m 'phantom(task-014): add bounded causal flight recorder'` → `af45c122db394568e413bebcba8e929a04428926`.

Evidence stage uses `git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CAUSAL-TRACE-014` after base-to-final scope verification. Evidence commit message: `phantom(task-014): record first lost readiness edge`.

Both normal pushes use exactly `git push origin HEAD:experiment/m1-candidate007-observe008`. No force, history rewrite, broad add/restore or other-worktree cleanup. Raw private mailbox/credentials/JFR/binaries are excluded.
