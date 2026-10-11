# GIT_COMMANDS037

Git использован только по прямому user request и GIT.md TASK037. Разрешение включает
bounded inspection, exact-path commit и normal push при любом исходе. MAIN read-only;
branch/reset/merge/rebase/restore/clean/force push/new worktree не выполнялись.

OWN=C:/Users/ZBook/.codex/worktrees/phantom-dev/L2J_Mobius.
MODULE=OWN/L2J_Mobius_CT_2.6_HighFive. MAIN=C:/Users/ZBook/L2J_Mobius.
Ниже реальные команды; повторные identity/status checks сгруппированы. Полные исходные
shell invocations с UTC сохранены в private git-actual037.json; final audit/publication receipt
дополняют публикационные команды после сохранения этого журнала.

В MAIN, только inventory existing worktrees (два запроса16:32Z):
```powershell
git worktree list --porcelain
```

В OWN, начальная base/remote и точные scope checks:
```powershell
git status --short
git rev-parse HEAD
git remote -v
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git diff --numstat -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git diff --name-only
git diff --check -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
git -c core.whitespace=cr-at-eol diff --check -- L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java
```
Первоначальные remote requests в restricted shell не подтвердили remote; повтор с network
escalation подтвердил BASE. Source blob fingerprints вычислены из bytes/raw-or-LF, без git show.
Первоначальный whitespace FAIL касался CR-at-EOL; scoped option не меняет global config.

17:25–17:30Z exact candidate review/publication:
```powershell
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' status --short
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --stat
$taskStagePaths=Get-Content -LiteralPath 'docs\phantoms\tasks\PHANTOM-M1-GROUP-CONTINUITY-037\STAGE_ALLOWLIST037.txt'
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' add -- $taskStagePaths
git -c core.whitespace=cr-at-eol -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --cached --check
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --cached --name-only
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --cached -- 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java' 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' commit -m 'TASK037: preserve useful progress debt across live epoch replans'
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' rev-parse HEAD
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --name-only
```
Candidate local=remote=7fafb0673c0d9ad87e6b417d3cd3876b61732045 before Jar build/freeze.
Initial26 exact add arguments are recorded in immutable precommit-scope037.json and the
archived candidate allowlist receipt:17 manifest packet paths, MANIFEST,Verify037,PROGRESS037,
IMPLEMENTATION037,ENROLLMENT_RULE037,STAGE_ALLOWLIST037, production VisibleAutoPlay and two
test files. No evidence/runtime/foreign paths staged. Runtime prepare/update/start wrappers
also call `git -C MODULE rev-parse HEAD` for exact provenance; MODULE is the absolute path above.

Final publication protocol uses only these seven literal task paths; source/test/Jar frozen:
```powershell
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --name-only
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' add -- 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/PROGRESS037.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/IMPLEMENTATION037.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/STAGE_ALLOWLIST037.txt' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/RESULT_HANDOFF037.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/EVIDENCE_INDEX037.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/CLEANUP037.md' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-GROUP-CONTINUITY-037/GIT_COMMANDS037.md'
git -c core.whitespace=cr-at-eol -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --cached --check
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' diff --cached --name-only
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' commit -m 'TASK037: publish failed group continuity evidence and cleanup'
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' rev-parse HEAD
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git -C 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius' status --short
```
This block is saved before execution so final SHA is bound externally; publication037.json
records actual success/failure, equality, archive SHA/size and final scope receipt. No claim
that a future command has passed substitutes for that receipt. G7 completion is external.
