# Git commands TASK025

Источник: actual function-call records этого диалога; shell requests сохранены verbatim. TASK.md Git и прямой запрос пользователя разрешают bounded inspection, own detached worktree, exact-path add/commit и normal push. Main/foreign mutations, git add ., force/merge/rebase/reset не разрешены и не выполнялись. Команды с переменными приведены как исполненные shell requests, а не самостоятельные replay scripts.

2026-10-07T21:15:38.272Z

```powershell
git status --short; git branch --show-current; git rev-parse HEAD; git remote -v; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T21:15:53.031Z

```powershell
git cat-file -t 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6; git show -s --format='%H %s' 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6; git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'; git worktree list --porcelain
```

2026-10-07T21:16:03.886Z

```powershell
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T21:16:03.886Z

```powershell
git worktree add --detach 'C:\Users\ZBook\.codex\worktrees\m1-checkpoint-025\L2J_Mobius' 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6
```

2026-10-07T21:19:36.349Z

```powershell
Copy-Item -LiteralPath 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-CHECKPOINT-RECOVERY-025' -Destination 'C:\Users\ZBook\.codex\worktrees\m1-checkpoint-025\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks' -Recurse; git status --short --untracked-files=no; git rev-parse HEAD
```

2026-10-07T21:32:27.810Z

```powershell
Get-Content tools/phantom-local-play/LocalPlay-Pilot.ps1 | Select-Object -Skip 170 -First 12; Get-Content docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Observe-Scene024.ps1 | Select-Object -Skip 66 -First 15; git diff --stat -- java test; git diff --check
```

2026-10-07T21:35:52.650Z

```powershell
rg -n 'Synthetic.*(Seconds|Commands|Starts)|Session.*(Seconds|Commands)' .phantom-local/contract025a/runtime/game/config/Custom/LocalPlayPilot.ini; Get-Content docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/RED_COLD_BEGIN025_COMPILED.log -Tail 8; git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java | Select-Object -First 60
```

2026-10-07T21:36:41.251Z

```powershell
rg --files java -g '*LocalPlay*Config*'; rg -n 'TTL|525|400|5;' java/org/l2jmobius/gameserver/localplay -g '*Synthetic*'; git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java | Select-Object -Skip 60 -First 55; git diff -- java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -First 60
```

2026-10-07T21:37:30.758Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | Select-Object -First 60; git diff -- test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java | Select-Object -First 50; git status --short --untracked-files=normal -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025
```

2026-10-07T21:38:09.455Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | Select-Object -First 60
```

2026-10-07T21:38:09.455Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | Select-Object -Skip 60 -First 60
```

2026-10-07T21:38:09.455Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -Skip 60 -First 30; git diff -- test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java | Select-Object -First 45
```

2026-10-07T21:38:37.958Z

```powershell
git diff --check; git add -- L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomContracts025DatabaseLane.java L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointRecovery025Suite.java L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/PROGRESS_CURRENT.md L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Prepare025.ps1 L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Control025.ps1 L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Checks025.ps1 L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Observe025.ps1; git diff --cached --stat; git commit -m 'phantom(task-025): resolve cold owned lifecycle before baseline and expose checkpoint causes'
```

2026-10-07T21:38:55.113Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008; git rev-parse HEAD
```

2026-10-07T22:12:58.002Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SOURCE_MAP.tsv' | Select-Object -Skip 20 -First 20; rg --files test/java/org/l2jmobius/tests/phantoms | rg '(Admission|Handoff|Ecology|Closure).*Suite\.java$'; Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/GREEN_TYPED_BOUNDARIES025.log' -Tail 10; git diff --stat; git diff --check
```

2026-10-07T22:13:43.834Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -First 60; Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/PLAN.md' -TotalCount 60
```

2026-10-07T22:15:34.879Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/GREEN_TYPED_DISTINCT_NATIVE025.log' -Tail 10; rg -n 'removeListener\(' java/org/l2jmobius/gameserver/model/events/ListenersContainer.java | Select-Object -First 4; git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java | Select-Object -First 58
```

2026-10-07T22:16:48.536Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/build-r2-candidate.log' -Tail 5; git diff -- java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -Skip 60 -First 55
```

2026-10-07T22:17:40.985Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/R2_CHECKPOINT_CONTRACTS.log' -Tail 10; git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | Select-Object -Skip 75 -First 58
```

2026-10-07T22:17:59.572Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | Select-Object -Skip 133 -First 58
```

2026-10-07T22:21:25.566Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -Skip 115 -First 55
```

2026-10-07T22:24:13.958Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/R2_OBSERVER_HOOK_CHECKPOINTS.log' -Tail 10; git diff --check; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T22:24:48.079Z

```powershell
git add -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointRecovery025Suite.java test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointDrain025Suite.java test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Checks025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Observe025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/PROGRESS_CURRENT.md docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/FIX_LEDGER.md
if($LASTEXITCODE -ne 0){throw 'Exact staging failed.'}
git diff --cached --stat
git commit -m 'phantom(task-025): continue typed checkpoints after earned quiescence and exact finalize'
if($LASTEXITCODE -ne 0){throw 'Exact source commit failed.'}
git rev-parse HEAD
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T22:49:11.265Z

```powershell
Get-Content -LiteralPath 'test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointDrain025Suite.java' -TotalCount 175; git diff --stat; git diff --check; Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SCENARIOS.md' -TotalCount 170
```

2026-10-07T22:53:31.044Z

```powershell
git diff -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java | Select-Object -First 60
```

2026-10-07T22:54:20.637Z

```powershell
rg -n 'phantom.m1.native.focus' test/java; Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SummarizeProbe025.py' -TotalCount 29; Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/VerifyPersistence025.py' -TotalCount 12; git diff --numstat
```

2026-10-07T22:54:57.578Z

```powershell
git add -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointDrain025Suite.java test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointRecovery025Suite.java test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/FIX_LEDGER.md docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/PROGRESS_CURRENT.md docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Observe025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Collector025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Contract025Observer.java docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/VerifyPersistence025.py docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SummarizeProbe025.py
```

2026-10-07T22:54:57.578Z

```powershell
git diff --cached --check; git diff --cached --stat
```

2026-10-07T22:55:18.329Z

```powershell
git commit -m 'phantom(task-025): publish exact native vitals and release completed stale controls'
```

2026-10-07T22:55:18.329Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T22:55:43.571Z

```powershell
git rev-parse HEAD; & 'C:/Tools/apache-ant-1.10.17/bin/ant.bat' -q jar *> 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/r3-committed-jar.log'; exit $LASTEXITCODE
```

2026-10-07T22:56:07.883Z

```powershell
Get-Content -LiteralPath 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/r3-committed-jar.log' -Tail 3; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
```

2026-10-07T23:32:07.021Z

```powershell
git diff --check; git diff --stat | Select-Object -First 18; rg -n 'Initial subsystem drain completed' .phantom-local/contract025c/runtime/game/log/java0.log | Select-Object -Last 2
```

2026-10-07T23:33:11.178Z

```powershell
git add -- java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java test/java/org/l2jmobius/tests/phantoms/PhantomCheckpointDrain025Suite.java docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Checks025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Collector025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Contract025Observer.java docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Observe025.ps1 docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SummarizeProbe025.py docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/PROGRESS_CURRENT.md docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/FIX_LEDGER.md
```

2026-10-07T23:33:11.178Z

```powershell
git diff --cached --check; git diff --cached --stat | Select-Object -First 13
```

2026-10-07T23:33:33.990Z

```powershell
git commit -m 'phantom(task-025): preserve native registrations during admitted checkpoint control'; git rev-parse HEAD
```

2026-10-07T23:33:33.990Z

```powershell
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
```

2026-10-08T00:02:47.536Z

```powershell
$t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; Get-Content "$t/r4-owned-store27-run.log" -Tail 5; Get-Content "$t/collector025-precrash-build.log" -Tail 4; Get-Content "$t/PROGRESS_CURRENT.md" -Tail 9; git diff --stat 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 -- 'L2J_Mobius_CT_2.6_HighFive/java' 'L2J_Mobius_CT_2.6_HighFive/test'; $files=Get-ChildItem -LiteralPath $t -File -Recurse; [pscustomobject]@{taskFiles=$files.Count;sizeMB=[math]::Round(($files|Measure-Object Length -Sum).Sum/1MB,1)}
```

2026-10-08T00:08:31.933Z

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {$_.CommandLine -match 'phantom.contract025.manifest'} | Select-Object ProcessId,CreationDate; $t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; Get-Content "$t/r4-fighter022-run.log" -TotalCount 3; Get-Content "$t/r4-mage022-run.log" -TotalCount 3; git diff 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 -- 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/player/PhantomNativeWorkScope.java' > "$t/evidence/NATIVE_SCOPE_EXACT_DIFF.patch"; Get-Content "$t/evidence/NATIVE_SCOPE_EXACT_DIFF.patch" | Select-Object -Skip 172 -First 34
```

2026-10-08T00:10:17.335Z

```powershell
$t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; Get-Content "$t/evidence/H_R4_SECOND_EXACT.json" -TotalCount 16; Get-Content "$t/stop-h-r4-second.log" -Tail 4; Get-Content "$t/evidence/H_R4_FIRST_STOPPED_SQL/durable-decoded.tsv" -TotalCount 3; Get-Content "$t/evidence/H_R4_SECOND_EARLY_SQL/durable-decoded.tsv" -TotalCount 3; git status --porcelain=v1 --untracked-files=normal -- 'L2J_Mobius_CT_2.6_HighFive/java' 'L2J_Mobius_CT_2.6_HighFive/test' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025' | Select-Object -First 26
```

2026-10-08T00:19:52.663Z

```powershell
$t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; git diff --check; Get-Content "$t/FIX_LEDGER.md" -TotalCount 40; Get-Content "$t/evidence/R4_OWNED_STORE27/background-position-canonicalization.txt" | Select-String -Pattern 'matrix|27|case'; rg --files 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025' -g '*.class' | Select-Object -First 4; Get-Content "$t/PROGRESS_CURRENT.md" -Tail 8
```

2026-10-08T00:24:21.535Z

```powershell
$t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; $f=Get-ChildItem "$t/evidence/FINAL_R4_SCENE_B2" -Filter 'cohort-*.json'|Sort-Object LastWriteTime|Select-Object -Last 1; $v=Get-Content $f.FullName -Raw|ConvertFrom-Json; "sample=$($f.Name) elapsed=$($v.elapsedSeconds)"; Get-Content "$t/final-r4-scene-b2-run.log" -Tail 3; Get-Content "$t/r4-native-review-run.log" -Tail 3; $root='C:/Users/ZBook/.codex/worktrees/m1-checkpoint-025/L2J_Mobius'; Get-Content "$t/Control025.ps1" -TotalCount 8; git log --format='%H %s' 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6..HEAD | Select-Object -First 5; Get-Content "$t/evidence/FINAL_R4_SCENE_E/all-samples.json" -Raw | ConvertFrom-Json | Measure-Object
```

2026-10-08T00:27:46.985Z

```powershell
$t='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; Get-ChildItem -LiteralPath "$t/evidence" -Filter '*.xml' -File -Recurse | Where-Object {$_.DirectoryName -match 'R4_|GREEN_BINDING|H_R4|BASE_(RAW|TIMERS|DYNAMIC)'} | ForEach-Object { $x=[xml](Get-Content $_.FullName -Raw); if($x.testsuite){[pscustomobject]@{route=$_.Directory.Name;name=$x.testsuite.name;total=$x.testsuite.tests;passed=$x.testsuite.passed;failed=$x.testsuite.failures}} } | ConvertTo-Json | Set-Content "$t/evidence/FROZEN_R4_REGRESSION_SUMMARY.json" -Encoding utf8; git diff --check *> "$t/evidence/PREFINAL_DIFF_CHECK.log"; "DIFF_CHECK_EXIT=$LASTEXITCODE"
```

2026-10-08T00:58:51.368Z

```powershell
git diff --name-only 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test; git status --short --untracked-files=normal
```

2026-10-08T01:07:47.019Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; rg --files $task025 -g '*git*' -g '*GIT*' -g '*scope*' -g '*SCOPE*' -g '*PUSH*' -g '*COMMIT*'; Get-Content "$task025/evidence/FROZEN_R4_REGRESSION_SUMMARY.json" -TotalCount 45; Get-Content "$task025/evidence/FINAL_R4_D_A/baseline-cohort.json" -TotalCount 10; git diff --name-only 41692152ca6944176be8014c307702b7d2a0a6bc -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test
```

2026-10-08T01:09:28.233Z

```powershell
rg --files 'C:/Users/ZBook/.codex/sessions/2026/10/07' | Select-Object -Last 6; rg --files 'C:/Users/ZBook/.codex/sessions/2026/10/08' | Select-Object -Last 4; Get-Content 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/SOURCE_MAP.tsv' -Tail 9; git diff --check *> 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/evidence/FINAL_DIFF_CHECK.log'; $LASTEXITCODE
```

2026-10-08T01:11:53.180Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; git diff --name-only 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test *> "$task025/evidence/FINAL_SOURCE_PATHS.txt"; git status --short --untracked-files=all *> "$task025/evidence/FINAL_SCOPE_STATUS.txt"; git diff --stat 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 HEAD -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test *> "$task025/evidence/FINAL_SOURCE_STAT.txt"; git diff --name-only 41692152ca6944176be8014c307702b7d2a0a6bc -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test *> "$task025/evidence/FINAL_FROZEN_SOURCE_DIFF.txt"; git diff --check *> "$task025/evidence/FINAL_DIFF_CHECK.log"; $LASTEXITCODE
```

2026-10-08T01:15:01.298Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; $qa025=Get-Content "$task025/evidence/FINAL_PACKET_QA.json" -Raw | ConvertFrom-Json; [pscustomobject]@{rawMojibakeLines=$qa025.rawEvidenceFindings.mojibake.Count;rawMojibakeFiles=@($qa025.rawEvidenceFindings.mojibake.path | Sort-Object -Unique).Count} | ConvertTo-Json -Compress; Import-Csv "$task025/evidence/FINAL_PACKET_INVENTORY.tsv" -Delimiter "`t" | Sort-Object {[long]$_.bytes} -Descending | Select-Object -First 4 path,bytes | ConvertTo-Json -Compress; rg --files --hidden --no-ignore 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/contract025d/runtime/game/config' -g '*Database*' -g '*Server*'; git log -4 --format='%H %cI %s'
```

2026-10-08T01:15:40.392Z

```powershell
& python -c 'from pathlib import Path; import re,json; m=Path("L2J_Mobius_CT_2.6_HighFive"); t=m/"docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025"; c=(m/".phantom-local/contract025d/runtime/game/config/Database.ini").read_text(encoding="utf-8-sig"); s=[v.strip().encode() for k,v in re.findall(r"(?m)^\s*([^;#=]+?)\s*=\s*(.*)$",c) if "password" in k.lower() and len(v.strip())>=8]; hits=[p.relative_to(t).as_posix() for p in t.rglob("*") if p.is_file() and any(x in p.read_bytes() for x in s)]; d={"privatePasswordValuesChecked":len(s),"matchingFiles":hits,"pass":not hits}; (t/"evidence/FINAL_PRIVATE_INPUT_SCAN.json").write_text(json.dumps(d,indent=2)+"\n",encoding="utf-8"); print(json.dumps(d))'; git ls-remote origin refs/heads/experiment/m1-candidate007-observe008 *> 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/evidence/REMOTE_BEFORE_FINAL_PACKET.txt'; Get-Content 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/evidence/REMOTE_BEFORE_FINAL_PACKET.txt'
```

2026-10-08T01:16:48.153Z

```powershell
git add -h *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/git-add-help.txt'; Select-String -Path 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/git-add-help.txt' -Pattern 'pathspec-from-file|pathspec-file-nul'; Get-Content 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/evidence/FINAL_PACKET_QA.json' -Tail 8
```

2026-10-08T01:17:03.716Z

```powershell
rg -n 'git (add|commit|push)' 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/GIT_COMMANDS.md' | Select-Object -First 12; & python -c 'import json,re; from pathlib import Path; p=Path(r"C:\Users\ZBook\.codex\sessions\2026\10\08\rollout-2026-10-08T00-14-30-01a11837-c27d-7851-a2b6-ef5034c786ea.jsonl"); a=[json.loads(x).get("payload",{}) for x in p.read_text(encoding="utf-8").splitlines()]; print({"gitExecRequestsWithoutLiteralCmd":sum(x.get("type")=="custom_tool_call" and x.get("name")=="exec" and bool(re.search(r"\bgit\s+",x.get("input",""))) and not bool(re.search(r"\bcmd\s*:\s*\"",x.get("input",""))) for x in a)})'
```

2026-10-08T01:19:52.105Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/ExtractGitLedger025.py'; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/VerifyPacket025.py'; git diff --check *> "$task025/evidence/FINAL_DIFF_CHECK.log"; if($LASTEXITCODE -ne 0){throw 'Final whitespace diff check failed.'}; $paths025=@(Get-ChildItem -LiteralPath $task025 -Recurse -File | ForEach-Object {$_.FullName.Substring($PWD.Path.Length+1).Replace('\','/')}); if(@($paths025 | Where-Object {-not $_.StartsWith($task025+'/',[StringComparison]::Ordinal)}).Count -gt 0){throw 'Exact task path guard failed.'}; [IO.File]::WriteAllBytes("$PWD/L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul",[Text.UTF8Encoding]::new($false).GetBytes(($paths025 -join [char]0)+[char]0)); "EXACT_TASK_PATHS=$($paths025.Count)"
```

2026-10-08T01:21:54.532Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/ExtractGitLedger025.py'; git diff --check *> "$task025/evidence/FINAL_DIFF_CHECK.log"; if($LASTEXITCODE -ne 0){throw 'Final whitespace check failed.'}; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/VerifyPacket025.py'; if($LASTEXITCODE -ne 0){throw 'Final packet QA failed.'}; $paths025=@(Get-ChildItem -LiteralPath $task025 -Recurse -File -Force | ForEach-Object {$_.FullName.Substring($PWD.Path.Length+1).Replace('\','/')}); if(@($paths025 | Where-Object {-not $_.StartsWith($task025+'/',[StringComparison]::Ordinal)}).Count -gt 0){throw 'Exact task path guard failed.'}; [IO.File]::WriteAllBytes("$PWD/L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul",[Text.UTF8Encoding]::new($false).GetBytes(($paths025 -join [char]0)+[char]0)); git --literal-pathspecs add --pathspec-from-file=L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul --pathspec-file-nul *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-stage.log'; if($LASTEXITCODE -ne 0){throw 'Exact staging failed.'}; git diff --cached --name-only *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-staged-paths.txt'; git diff --cached --check *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-check.log'; if($LASTEXITCODE -ne 0){throw 'Staged whitespace check failed.'}; git diff --cached --stat *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-stat.txt'; $staged025=@(Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-staged-paths.txt'); if(@($staged025 | Where-Object {$_ -notin $paths025}).Count -gt 0){throw 'Unexpected staged path.'}; "EXACT_STAGED=$($staged025.Count) ENUMERATED=$($paths025.Count) CACHED_CHECK=PASS"; Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-stat.txt' -Tail 1
```

2026-10-08T01:23:25.695Z

```powershell
git -c core.whitespace=cr-at-eol diff --cached --check *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-check-crlf.log'; $exit025=$LASTEXITCODE; Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-check-crlf.log' -TotalCount 12; Get-Item 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-check-crlf.log' | Select-Object Length | ConvertTo-Json -Compress; "EXIT=$exit025"
```

2026-10-08T01:24:16.918Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; $authored025=@(Get-ChildItem -LiteralPath $task025 -File -Force | Where-Object {$_.Extension -in @('.md','.ps1','.py','.java','.tsv') -or $_.Name -ceq '.gitattributes'} | ForEach-Object {$_.FullName.Substring($PWD.Path.Length+1).Replace('\','/')}); git -c core.whitespace=cr-at-eol diff --cached --check -- @authored025 *> "$task025/evidence/FINAL_AUTHORED_CACHED_CHECK.log"; if($LASTEXITCODE -ne 0){throw 'Authored staged whitespace check failed.'}; $source025=Get-Content "$task025/evidence/FINAL_SOURCE_PATHS.txt"; git diff --check 07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6 HEAD -- @source025 *> "$task025/evidence/FINAL_SOURCE_DIFF_CHECK.log"; if($LASTEXITCODE -ne 0){throw 'Exact source whitespace check failed.'}; "AUTHORED_WHITESPACE=PASS PATHS=$($authored025.Count) SOURCE_WHITESPACE=PASS"; $staged025=@(Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-staged-paths.txt'); if(@($staged025 | Where-Object {-not $_.StartsWith($task025+'/',[StringComparison]::Ordinal)}).Count -gt 0){throw 'Staged outside task root.'}; "STAGED_SCOPE=PASS PATHS=$($staged025.Count)"
```

2026-10-08T01:25:36.847Z

```powershell
$task025='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/ExtractGitLedger025.py'; & python -X utf8 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/VerifyPacket025.py'; if($LASTEXITCODE -ne 0){throw 'Final packet QA failed.'}; $paths025=@(Get-ChildItem -LiteralPath $task025 -Recurse -File -Force | ForEach-Object {$_.FullName.Substring($PWD.Path.Length+1).Replace('\','/')}); [IO.File]::WriteAllBytes("$PWD/L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul",[Text.UTF8Encoding]::new($false).GetBytes(($paths025 -join [char]0)+[char]0)); git --literal-pathspecs add --pathspec-from-file=L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul --pathspec-file-nul *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-stage.log'; if($LASTEXITCODE -ne 0){throw 'Exact staging failed.'}; git diff --cached --name-only *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-staged-paths.txt'; $staged025=@(Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-staged-paths.txt'); if(@($staged025 | Where-Object {$_ -notin $paths025}).Count -gt 0){throw 'Unexpected staged path.'}; $authored025=@(Get-ChildItem -LiteralPath $task025 -File -Force | Where-Object {$_.Extension -in @('.md','.ps1','.py','.java','.tsv') -or $_.Name -ceq '.gitattributes'} | ForEach-Object {$_.FullName.Substring($PWD.Path.Length+1).Replace('\','/')}); git -c core.whitespace=cr-at-eol diff --cached --check -- @authored025 *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-authored-staged-check.log'; if($LASTEXITCODE -ne 0){throw 'Authored staged check failed.'}; git diff --cached --stat *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-stat.txt'; "EXACT_STAGED=$($staged025.Count) AUTHOR_CHECK=PASS"; Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-cached-stat.txt' -Tail 1; git commit -m 'phantom(task-025): retain failed frozen acceptance and exact recovery evidence' *> 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-commit.log'; if($LASTEXITCODE -ne 0){throw 'Required final commit failed.'}; Get-Content 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-commit.log' -TotalCount 2; git rev-parse HEAD
```

## Final exact publication commands

Список для последнего closeout после ledger snapshot. Фактические exit codes,
artifact SHA, remote SHA и clean scope сохраняются после commit/push в own
.phantom-local/ops025/publication-final.json. Exact NUL path list включает только
перечисленные task files; git add . не используется.

```powershell
git --literal-pathspecs add --pathspec-from-file=L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops025/final-task-pathspec.nul --pathspec-file-nul
git diff --cached --name-only
git diff --cached --check
git diff --cached --stat
git commit -m 'phantom(task-025): retain failed frozen acceptance and exact recovery evidence'
git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008
git rev-parse HEAD
git ls-remote origin refs/heads/experiment/m1-candidate007-observe008
git status --porcelain=v1 --untracked-files=all
git diff --name-only 41692152ca6944176be8014c307702b7d2a0a6bc -- L2J_Mobius_CT_2.6_HighFive/java L2J_Mobius_CT_2.6_HighFive/test
git log -1 --format=%H -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025
```
