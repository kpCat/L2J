# Git commands and exact publication scope

Authorization: user explicitly requested exact-path commit and normal push to
`experiment/m1-candidate007-observe008`; input task012 GIT.md allows fetch/read,
exact-path add, commit and normal push. No Git reset/clean/stash/rebase/force/merge,
branch creation or git restore was used.

Initial cwd `C:/Users/ZBook/L2J_Mobius` was the unrelated feature checkout.
Read-only commands executed there:

```text
git branch --show-current
git rev-parse HEAD
git status --short
git status --short --untracked-files=no
git worktree list
git rev-parse --verify 88b7dd76643cb80b78246668cd16052e740a55aa
```

They identified the existing experiment checkout at the exact required base.
No feature checkout diff, staging or mutation was performed.

Remaining cwd:
`C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius`.

```text
git status --short --untracked-files=no
git diff --stat -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java
git diff --check -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java
git remote get-url origin
git rev-parse --abbrev-ref --symbolic-full-name '@{u}'
git diff -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java
git diff --exit-code -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/PhantomM1HistoricalNativeContextChecks.java
git diff --name-only
```

Upstream lookup reported no configured upstream. Therefore push uses the explicit
requested ref. The test diff was preserved as evidence, then removed using
apply_patch. Source diff verification returned exit0/empty paths.

Exact publication commands, with repeated read-only verification omitted:

```text
git add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/RESULT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/RESTORE-EVIDENCE.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/HANDOFF-BLOCKED.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/FOCUSED-ATTEMPT.txt L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/UNVERIFIED-NATIVE-DIAGNOSTIC.patch L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-NATIVE-ATTESTATION-012/GIT_COMMANDS.md
git diff --cached --check
git diff --cached --name-only
git branch --show-current
git rev-parse HEAD
git diff --name-only
git commit -m "phantom(task-012): record guarded TEST restore blocker"
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git status --short --untracked-files=no
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

Only the six listed evidence files are staged. Journal, secrets, DB config, dumps,
compiled outputs, scripts under .phantom-local and all foreign files are excluded.
Actual publication outcome and resulting commit SHA are reported in chat.
