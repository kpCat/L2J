# Exact Git command ledger024

Git использован: да. TASK.md Worktree/Git явно разрешает bounded read/fetch/log/diff/status/hash, detached worktree, exact add/commit и normal push при FAILED. Main inspection был read-only; изменения/commits только own detached worktree.

Ни branch/merge/rebase/reset/restore/clean/stash/force push, ни чужой diff не изменялись. Managed create_worktree использован с ref819e и name m1-contracts-024, не ручной checkout main.

Ниже фактические command strings из текущего own tool transcript; context main до создания, затем own worktree. Checkpoint024.ps1 отдельно содержит точные indirect `git -C $root diff --name-only`, `diff --check -- $changed`, `add -- $paths`, `diff --cached --name-only`, `add -- CHECKPOINT_SCOPE.txt`, `commit -m $Message`, `rev-parse HEAD`. Variables разрешены exact SOURCE_MAP/test/TASK allowlist, полный staged receipt сохранён.

UTC 2026-10-07T15:52:30.018Z

```powershell
git status --short; git branch --show-current; git rev-parse HEAD; git rev-parse --git-dir; git rev-parse --git-common-dir; git remote -v; git worktree list --porcelain; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008; git cat-file -t 819e3cea5baa64e6c429e450c8fc296874e37d1c
```

UTC 2026-10-07T15:52:56.081Z

```powershell
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

UTC 2026-10-07T15:52:56.081Z

```powershell
git fetch origin experiment/m1-candidate007-observe008
```

UTC 2026-10-07T15:53:28.211Z

```powershell
git status --short; git branch --show-current; git rev-parse HEAD; rg --files -g '*023*.ps1' -g '*Synthetic*.ps1' -g '*Pilot*.ps1' -g '*LocalPlay*.ps1' -g '*Guard*.java' -g '*Contracts*.java' L2J_Mobius_CT_2.6_HighFive; Get-Content -LiteralPath 'L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-UNATTENDED-LIVING-WORLD-023\RESULT.md' -TotalCount 120
```

UTC 2026-10-07T15:57:49.339Z

```powershell
git rev-parse HEAD; git status --short; git branch --show-current; Copy-Item -LiteralPath 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024' -Destination 'L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024' -Recurse; Get-FileHash -LiteralPath 'L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\GOAL.md'
```

UTC 2026-10-07T16:16:25.511Z

```powershell
git diff --stat -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java; rg -n '_topologyService.beginStop' L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java; Get-Content -LiteralPath 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/player/PhantomM1DynamicRecipientChecks.java' | Select-Object -Skip 99 -First 65; Get-Content -LiteralPath 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/player/PhantomM1DynamicRecipientChecks.java' | Select-Object -Skip 376 -First 43
```

UTC 2026-10-07T16:36:05.306Z

```powershell
git diff --numstat -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test; git diff --check -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test; Get-Content L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ROOT_CAUSES.md -TotalCount 57
```

UTC 2026-10-07T17:03:04.228Z

```powershell
git diff --check -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test; Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Select-Object ProcessId,CreationDate,CommandLine | Format-List; Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 2106,7777,9014 } | Select-Object LocalAddress,LocalPort,OwningProcess
```

UTC 2026-10-07T17:07:42.840Z

```powershell
rg -n 'canonicalHash' java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundInventoryLedger.java; rg -n 'InventoryLedger' java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java | Select-Object -First 6; git diff --check -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test
```

UTC 2026-10-07T17:08:10.337Z

```powershell
git status --short; git diff --name-only -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test
```

UTC 2026-10-07T17:09:22.341Z

```powershell
python 'C:/Users/ZBook/.codex/visualizations/2026/10/07/01a11710-06cc-7ba3-bffa-5b11b21e41d2/apply024.py' 'C:/Users/ZBook/.codex/visualizations/2026/10/07/01a11710-06cc-7ba3-bffa-5b11b21e41d2/checkpoint-copy.json'; & './docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Checkpoint024.ps1' *> docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/checkpoint-01.log; Get-Content docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/checkpoint-01.log -Tail 8; exit $LASTEXITCODE
```

UTC 2026-10-07T18:00:52.318Z

```powershell
git diff -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java
```

UTC 2026-10-07T18:04:10.982Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

UTC 2026-10-07T18:38:26.264Z

```powershell
python 'C:\Users\ZBook\.codex\visualizations\2026\10\07\01a11710-06cc-7ba3-bffa-5b11b21e41d2\apply024.py' 'C:\Users\ZBook\.codex\visualizations\2026\10\07\01a11710-06cc-7ba3-bffa-5b11b21e41d2\pre-r3-edit.json'; & 'C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\Checkpoint024.ps1' -Message 'Recover exact stale planned history before native attestation' *> 'C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\checkpoint-03.log'; Get-Content -LiteralPath 'C:\Users\ZBook\.codex\worktrees\m1-contracts-024\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-RUNTIME-CONTRACTS-024\checkpoint-03.log' -Tail 4; exit $LASTEXITCODE
```

UTC 2026-10-07T18:42:13.498Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

UTC 2026-10-07T18:56:00.966Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

UTC 2026-10-07T19:15:35.052Z

```powershell
git diff --name-only 819e3cea5baa64e6c429e450c8fc296874e37d1c -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test
```

UTC 2026-10-07T19:25:53.337Z

```powershell
git diff 819e3cea5baa64e6c429e450c8fc296874e37d1c -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomNativeContext.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/Creature.java
```

UTC 2026-10-07T19:44:03.950Z

```powershell
git diff --name-only 819e3cea5baa64e6c429e450c8fc296874e37d1c -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test; git diff --stat ca3cbef9c03b695dcb9f84a734c5535b4ac72027 -- L2J_Mobius_CT_2.6_HighFive/java
```

UTC 2026-10-07T19:47:58.257Z

```powershell
git diff 819e3cea5baa64e6c429e450c8fc296874e37d1c -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/Creature.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java
```

Final closure helper ran exact: `git -C <own-root> diff --name-only 819e3cea5baa64e6c429e450c8fc296874e37d1c --`; `git -C <own-root> diff --name-only ca3cbef9c03b695dcb9f84a734c5535b4ac72027 -- L2J_Mobius_CT_2.6_HighFive/java`. Final publication commands and receipt are also recorded in PUBLICATION.md.
