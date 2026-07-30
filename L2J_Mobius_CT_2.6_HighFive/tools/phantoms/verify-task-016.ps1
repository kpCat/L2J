param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$requiredParent = "a546dae868d93d54ec4bc6e1836080b90f810167"
$requiredBranch = "feature/phantom-world"
$requiredSubject = "feat(phantoms): add population manager and schedules"
$seed = "16001601"
$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$repositoryRoot = (Resolve-Path ((& git -C $moduleRoot rev-parse --show-toplevel).Trim())).Path
$repositoryPrefix = $repositoryRoot.TrimEnd("\", "/") + "\"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Read-Utf8Strict([string] $relativePath)
{
	$path = Join-Path $moduleRoot $relativePath
	Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required file is missing: $relativePath"
	$encoding = [Text.UTF8Encoding]::new($false, $true)
	return $encoding.GetString([IO.File]::ReadAllBytes($path))
}

function Get-Sha256([string] $relativePath)
{
	$sha256 = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha256.ComputeHash([IO.File]::ReadAllBytes((Join-Path $moduleRoot $relativePath))))).Replace("-", "")
	}
	finally
	{
		$sha256.Dispose()
	}
}

function Invoke-Git([string[]] $Arguments)
{
	$startInfo = [Diagnostics.ProcessStartInfo]::new()
	$startInfo.FileName = "git"
	$startInfo.UseShellExecute = $false
	$startInfo.RedirectStandardOutput = $true
	$startInfo.RedirectStandardError = $true
	$quoted = @("-C", $repositoryRoot) + $Arguments
	$startInfo.Arguments = ($quoted | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join " "
	$process = [Diagnostics.Process]::new()
	$process.StartInfo = $startInfo
	[void] $process.Start()
	$outputRead = $process.StandardOutput.ReadToEndAsync()
	$errorRead = $process.StandardError.ReadToEndAsync()
	$process.WaitForExit()
	$outputText = $outputRead.GetAwaiter().GetResult()
	$errorText = $errorRead.GetAwaiter().GetResult()
	Assert-True ($process.ExitCode -eq 0) "git $($Arguments -join ' ') failed: $errorText"
	if ($outputText.Length -eq 0)
	{
		return
	}
	return @($outputText.TrimEnd("`r", "`n") -split "`r?`n")
}

function To-ModulePath([string] $repositoryPath)
{
	$normalized = $repositoryPath.Trim().Trim('"').Replace("\", "/")
	if ($normalized.StartsWith($script:moduleRelative + "/", [StringComparison]::Ordinal))
	{
		return $normalized.Substring($script:moduleRelative.Length + 1)
	}
	return $normalized
}

function Is-AllowedPath([string] $path)
{
	$exact = @(
		"build.xml",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java",
		"java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerControlPort.java",
		"java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java",
		"java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
		"java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java",
		"dist/game/config/Custom/PhantomPlayers.ini",
		"dist/game/data/phantoms/population/high-five-population-v1.xml",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-015.ps1",
		"tools/phantoms/verify-task-016.ps1",
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"docs/PHANTOM_BOTS_ROADMAP.md",
		"docs/phantoms/architecture/POPULATION_MANAGER_SCHEDULE_CONTRACT.md",
		"docs/phantoms/reports/016-population-manager-schedules.md",
		"docs/phantoms/reviews/015-background-farming-final-review.md"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/population/[^/]+\.java$")
	{
		return $true
	}
	if ($path -match "^test/java/org/l2jmobius/tests/phantoms/PhantomPopulation[^/]*\.java$")
	{
		return $true
	}
	if ($path -match "^docs/phantoms/tasks/016-population-manager-schedules/[^/]+$")
	{
		return $true
	}
	return $false
}

Assert-True ($moduleRoot.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) "Module root is outside repository root."
$script:moduleRelative = $moduleRoot.Substring($repositoryPrefix.Length).Replace("\", "/")
$head = ([string] (Invoke-Git @("rev-parse", "HEAD") | Select-Object -First 1)).Trim()
$branch = ([string] (Invoke-Git @("branch", "--show-current") | Select-Object -First 1)).Trim()
Assert-True ($branch -eq $requiredBranch) "Unexpected branch: $branch"
[void] (Invoke-Git @("merge-base", "--is-ancestor", $requiredParent, $head))

$mode = ""
$changed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if ($head -eq $requiredParent)
{
	$mode = "working-tree"
	foreach ($line in Invoke-Git @("diff", "--name-only", $requiredParent, "--", $script:moduleRelative))
	{
		if ($line.Trim().Length -gt 0)
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
	foreach ($line in Invoke-Git @("-c", "core.quotepath=false", "status", "--porcelain=v1", "--untracked-files=all", "--", $script:moduleRelative))
	{
		if ($line.Length -ge 4)
		{
			$path = $line.Substring(3)
			if ($path.Contains(" -> "))
			{
				$path = $path.Split(@(" -> "), [StringSplitOptions]::None)[1]
			}
			[void] $changed.Add((To-ModulePath $path))
		}
	}
}
else
{
	$mode = "goal016-commit"
	$parent = ([string] (Invoke-Git @("rev-parse", "HEAD^") | Select-Object -First 1)).Trim()
	$subject = [string] (Invoke-Git @("show", "-s", "--format=%s", "HEAD") | Select-Object -First 1)
	Assert-True ($parent -eq $requiredParent) "Goal 016 commit is not the direct child of the required parent."
	Assert-True ($subject -eq $requiredSubject) "Unexpected Goal 016 commit subject: $subject"
	$status = @(Invoke-Git @("-c", "core.quotepath=false", "status", "--porcelain=v1", "--", $script:moduleRelative))
	Assert-True ($status.Count -eq 0) "Goal 016 post-commit verifier requires a clean module worktree."
	foreach ($line in Invoke-Git @("diff", "--name-only", $requiredParent, $head, "--", $script:moduleRelative))
	{
		if ($line.Trim().Length -gt 0)
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
}

Assert-True ($changed.Count -gt 0) "Goal 016 scope is empty."
foreach ($path in $changed)
{
	Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 016 path: $path"
	Assert-True (-not $path.StartsWith("../", [StringComparison]::Ordinal)) "Path escaped the High Five module: $path"
}
Assert-True (-not ($changed -contains "java/org/l2jmobius/gameserver/model/actor/Player.java")) "Player.java must not change."

$manifestPath = "docs/phantoms/tasks/016-population-manager-schedules/PACKAGE_MANIFEST.json"
$manifest = Read-Utf8Strict $manifestPath | ConvertFrom-Json
Assert-True ($manifest.requiredParent -eq $requiredParent) "Task package parent mismatch."
Assert-True ($manifest.commitSubject -eq $requiredSubject) "Task package subject mismatch."
Assert-True ([string] $manifest.deterministicSeed -eq $seed) "Task package seed mismatch."
foreach ($property in $manifest.payloadSha256.PSObject.Properties)
{
	$actual = Get-Sha256 $property.Name
	Assert-True ($actual -eq ([string] $property.Value).ToUpperInvariant()) "Task package hash mismatch: $($property.Name)"
}

$review015 = Read-Utf8Strict "docs/phantoms/reviews/015-background-farming-final-review.md"
Assert-True ($review015 -match '(?m)^`ACCEPT`\s*$') "Goal 015 independent ACCEPT is missing."
$historical015 = @(& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $moduleRoot "tools/phantoms/verify-task-015.ps1") 2>&1)
Assert-True ($LASTEXITCODE -eq 0) "Historical Goal 015 verifier failed: $($historical015 -join [Environment]::NewLine)"
Assert-True ($historical015 -contains "TASK015_VERIFIER_OK") "Historical Goal 015 verifier token is missing."

$config = Read-Utf8Strict "dist/game/config/Custom/PhantomPlayers.ini"
Assert-True ($config -match "(?m)^PhantomPopulationTarget\s*=\s*0\s*$") "Production population target is not zero."
Assert-True ($config -match "(?m)^PhantomPopulationActiveTarget\s*=\s*0\s*$") "Production active target is not zero."
$configJava = Read-Utf8Strict "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
Assert-True ($configJava -match "DEFAULT_POPULATION_TARGET\s*=\s*0") "Java population target default is not zero."
Assert-True ($configJava -match "DEFAULT_POPULATION_ACTIVE_TARGET\s*=\s*0") "Java active target default is not zero."

$initializer = Read-Utf8Strict "java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java"
$characterCreate = Read-Utf8Strict "java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java"
Assert-True ($characterCreate -match "PlayerCreationInitializer\.initialize\(newChar,\s*Mode\.CLIENT\)") "CharacterCreate does not delegate to the shared initializer."
Assert-True ($initializer -match "InitialEquipmentData" -and $initializer -match "InitialShortcutData" -and $initializer -match "SkillTreeData") "Shared initializer is incomplete."
Assert-True ($initializer -notmatch "GameClient|CharacterCreate|OnPlayerCreate|sendPacket|network\.serverpackets") "Shared initializer directly invokes a forbidden client/packet path."

$populationSources = Get-ChildItem -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/population") -Filter "*.java" -File
$populationText = ($populationSources | ForEach-Object { Read-Utf8Strict ("java/org/l2jmobius/gameserver/phantoms/population/" + $_.Name) }) -join "`n"
Assert-True ($populationText -notmatch "GameClient|CharacterCreate|OnPlayerCreate|sendPacket|network\.serverpackets") "Population code directly invokes a forbidden client/packet path."
Assert-True ($populationText -notmatch "\b(?:Thread|ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.") "Population code creates worker/task/Future infrastructure."
Assert-True ($populationText -notmatch "l2jmobiush5(?!_phantom_test)") "Population code names the production database."

$repository = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java"
Assert-True ($repository -match "createWithComponent" -and $repository -match "setAutoCommit\(false\)" -and $repository -match "connection\.commit\(\)") "Atomic managed shell transaction is missing."
Assert-True ($repository -match "listManagedAfter" -and $repository -match "pageSize > 256" -and $repository -match "LIMIT \?") "Managed startup paging is not bounded to 256."
$store = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationStore.java"
$sagaMarkers = @("ACCOUNT_INTENT", "ACCOUNT_VERIFIED", "CHARACTER_INTENT", "Player.create", "INITIALIZATION_INTENT", "updateCharacterLink", ".ready()")
$lastIndex = -1
foreach ($marker in $sagaMarkers)
{
	$index = $store.IndexOf($marker, [StringComparison]::Ordinal)
	Assert-True ($index -gt $lastIndex) "Creation saga ordering marker is missing or out of order: $marker"
	$lastIndex = $index
}

$scheduler = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
Assert-True ($scheduler -match "installControlPort" -and $scheduler -match "(?s)_pulseInFlight\s*=\s*true;.*?\}\s*try\s*\{\s*_controlPort\.onPulse\(\);") "Scheduler control hook is missing or appears inside the scheduler monitor."
$manager = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java"
Assert-True ($manager -match "loadManagedAfter" -and $manager -match "Math\.min\(256") "Population startup does not use bounded paging."
Assert-True ($manager -match "RETIRE_REQUESTED" -and $manager -match "State\.RETIRED" -and $manager -match "State\.READY") "Retirement/return lifecycle markers are incomplete."
$decision = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java"
Assert-True ($decision -match '"candidate\.population\.bootstrap"' -and $decision -match '"population\.create_character"') "Population decision keys are missing."

$build = Read-Utf8Strict "build.xml"
$targets = @(
	"phantom-population-catalog-test",
	"phantom-population-schedule-test",
	"phantom-population-creation-test",
	"phantom-population-reconciliation-test",
	"phantom-population-lifecycle-test",
	"phantom-population-server-integration-test",
	"phantom-population-performance-smoke"
)
foreach ($target in $targets)
{
	Assert-True ($build -match ('name="' + [Regex]::Escape($target) + '"')) "Missing Goal 016 Ant target: $target"
}
Assert-True ($build -match 'name="phantom-population-test"') "Goal 016 aggregate target is missing."
Assert-True ($build -match 'name="phantom-static-verify-016"') "Goal 016 static verifier target is missing."
Assert-True ($build -match ('phantom\.goal016\.seed"\s+value="' + $seed + '"')) "Goal 016 build seed is missing."

$report = Read-Utf8Strict "docs/phantoms/reports/016-population-manager-schedules.md"
Assert-True (($report -split "`r?`n").Count -le 220) "Goal 016 report exceeds 220 lines."
Assert-True ($report -match "READ_SET" -and $report -match "Creation matrix" -and $report -match "Schedule matrix" -and $report -match "Retirement matrix") "Goal 016 report evidence matrices are incomplete."

$utf8Exclusions = @(
	"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"tools/phantoms/verify-task-015.ps1",
	"tools/phantoms/verify-task-016.ps1"
)
$mojibakeMarkers = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("0KDRn3zQoNGcfNCg0Zt80KDigKJ80KDQjnzQoOKAunzQoMKkfNCg0Zp80KDQiHzQoNGZfNCg0pF80KDCtXzQoMKwfNCgwrt80KDQhXzQoNGVfNCh0I980KHigqx80KHQgnzQoeKAuXzQodCKfNCh4oCafNCh0ZN80KHigKF80KHigKZ80KHigKB877+9")).Split("|")
$escapedCyrillic = "\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};"
foreach ($path in $changed)
{
	if (-not (Test-Path -LiteralPath (Join-Path $moduleRoot $path) -PathType Leaf))
	{
		continue
	}
	if ($path -match "^docs/phantoms/tasks/016-population-manager-schedules/")
	{
		continue
	}
	$textExtensions = @(".java", ".xml", ".ini", ".md", ".ps1")
	if ($textExtensions -notcontains [IO.Path]::GetExtension($path).ToLowerInvariant())
	{
		continue
	}
	$text = Read-Utf8Strict $path
	if ($utf8Exclusions -notcontains $path)
	{
		foreach ($marker in $mojibakeMarkers)
		{
			Assert-True (-not $text.Contains($marker)) "Mojibake marker found in changed file: $path"
		}
		Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in changed file: $path"
	}
}

$jarPath = Join-Path $moduleRoot "dist/libs/GameServer.jar"
Assert-True (Test-Path -LiteralPath $jarPath -PathType Leaf) "GameServer.jar is missing."
$jarTool = Join-Path $env:JAVA_HOME "bin/jar.exe"
Assert-True (Test-Path -LiteralPath $jarTool -PathType Leaf) "JDK jar tool is missing."
$jarEntries = @(& $jarTool tf $jarPath)
Assert-True ($LASTEXITCODE -eq 0) "Cannot inspect GameServer.jar."
$requiredEntries = @(
	"org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.class",
	"org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.class",
	"org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerControlPort.class"
)
foreach ($entry in $requiredEntries)
{
	Assert-True ($jarEntries -contains $entry) "GameServer.jar is missing $entry"
}

Write-Output "TASK016_VERIFIER"
Write-Output "graph=$mode"
Write-Output "scope_files=$($changed.Count)"
Write-Output "historical015=TASK015_VERIFIER_OK"
Write-Output "package=OK"
Write-Output "defaults=0/0"
Write-Output "packet_boundary=OK"
Write-Output "saga=OK"
Write-Output "bounded_lifecycle=OK"
Write-Output "utf8=OK"
Write-Output "jar=OK"
Write-Output "TASK016_VERIFIER_OK"
