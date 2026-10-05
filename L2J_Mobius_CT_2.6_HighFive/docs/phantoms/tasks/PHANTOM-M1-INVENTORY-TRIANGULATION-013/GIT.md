# GIT

Remote base:
88b7dd76643cb80b78246668cd16052e740a55aa

Branch:
experiment/m1-candidate007-observe008

Main checkout contains foreign 012R uncommitted evidence.
READ ONLY. Do not stage/revert it.

Use a new isolated worktree.

Allowed in task worktree:
fetch/read, exact-path add, commit, normal push same experiment branch.

Forbidden:
git add .
reset
clean
stash
rebase
force
merge to feature/phantom-world
touching main-checkout 012R diffs
