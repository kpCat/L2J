param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RequiredParent = "d48dccb42dcfe5993f1c852e021086e498c0622d"
$RequiredSubject = "feat(phantoms): add acquisition planning and spoil chains"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "21002101"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Git-Lines([string[]] $arguments)
{
	$result = & git @arguments
	Assert-True ($LASTEXITCODE -eq 0) "Git command failed: git $($arguments -join ' ')"
	return @($result | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function To-ModulePath([string] $path)
{
	$normalized = $path.Trim().Trim('"').Replace("\", "/")
	if ($normalized.StartsWith($script:ModulePrefix, [StringComparison]::Ordinal))
	{
		return $normalized.Substring($script:ModulePrefix.Length)
	}
	return $normalized
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 021c1 artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = [Diagnostics.ProcessStartInfo]::new()
	$start.FileName = "git"
	$start.Arguments = "show $($script:TargetCommit)`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = [IO.MemoryStream]::new()
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 021c1 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetUtf8Strict([string] $relativePath)
{
	return [Text.UTF8Encoding]::new($false, $true).GetString((Read-TargetBytes $relativePath))
}

function Is-AllowedPath([string] $path)
{
	if (($path -match '^java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisition[A-Za-z]+\.java$') -or
		($path -eq 'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml') -or
		($path -match '^docs/phantoms/tasks/021-checkpoint-1-acquisition-spoil/') -or
		($path -in @(
			'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
			'build.xml',
			'dist/game/data/phantoms/progression/high-five-capabilities-v1.xml',
			'docs/PHANTOM_BOTS_ROADMAP.md',
			'docs/phantoms/architecture/ACQUISITION_CHAIN_CONTRACT.md',
			'docs/phantoms/reports/021-checkpoint-1-acquisition-spoil.md',
			'docs/phantoms/reviews/020-conversation-final-review.md',
			'docs/phantoms/reviews/021-checkpoint-1-independent-review.md',
			'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
			'java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java',
			'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundAuthority.java',
			'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java',
			'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundOperationKey.java',
			'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java',
			'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java',
			'java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java',
			'java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java',
			'java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java',
			'java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java',
			'java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java',
			'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java',
			'test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java',
			'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java',
			'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
			'tools/phantoms/verify-task-020c2.ps1',
			'tools/phantoms/verify-task-021c1.ps1'
		)))
	{
		return $true
	}
	return $false
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 021c1 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	& git merge-base --is-ancestor $RequiredParent $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 020 baseline is not an ancestor of HEAD."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $RequiredParent) | Select-Object -First 1) -eq "fix(phantoms): close exact conversation invitation ownership") "Accepted Goal 020 subject changed."

	if ($head -eq $RequiredParent)
	{
		$script:Mode = "working"
		$script:TargetCommit = ""
	}
	else
	{
		$matching = @(Git-Lines @("log", "--format=%H", "--fixed-strings", "--grep=$RequiredSubject", "$RequiredParent..$head"))
		Assert-True ($matching.Count -eq 1) "Goal 021c1 subject must identify exactly one implementation commit."
		$script:Mode = "committed"
		$script:TargetCommit = $matching[0]
		Assert-True ((Git-Lines @("rev-parse", "$($script:TargetCommit)^" ) | Select-Object -First 1) -eq $RequiredParent) "Goal 021c1 implementation is not one ordinary child of accepted Goal 020."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 021c1 commit subject changed."
		& git merge-base --is-ancestor $script:TargetCommit $head
		Assert-True ($LASTEXITCODE -eq 0) "Goal 021c1 implementation commit is not an ancestor of HEAD."
	}

	$changed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
	$rangeEnd = "HEAD"
	if ($script:Mode -ne "working")
	{
		$rangeEnd = $script:TargetCommit
	}
	foreach ($line in Git-Lines @("diff", "--name-only", $RequiredParent, $rangeEnd, "--"))
	{
		[void] $changed.Add((To-ModulePath $line))
	}
	if ($script:Mode -eq "working")
	{
		foreach ($line in Git-Lines @("ls-files", "--others", "--exclude-standard", "--"))
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
	$changedPaths = @($changed | Sort-Object)
	Assert-True (($changedPaths.Count -gt 0) -and ($changedPaths.Count -le 54)) "Goal 021c1 total scope must contain 1..54 files."
	foreach ($path in $changedPaths)
	{
		Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 021c1 path: $path"
		Assert-True ($path -notmatch '(^|/)(Player|Party)\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)|(?:^|/)(?:handlers?/skills?|quests?)/') "Forbidden Goal 021c1 path: $path"
	}
	$production = @($changedPaths | Where-Object { ($_ -match '^java/org/l2jmobius/gameserver/') -or ($_ -match '^dist/game/(?:config|data)/') })
	Assert-True ($production.Count -le 30) "Goal 021c1 exceeds 30 production/data/config files."
	$newProduction = @()
	foreach ($path in $production)
	{
		$existing = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $RequiredParent, "--", ($script:ModulePrefix + $path)))
		if ($existing.Count -eq 0)
		{
			$newProduction += $path
		}
	}
	Assert-True ($newProduction.Count -le 16) "Goal 021c1 exceeds 16 new production/data files."

	$required = @(
		'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml',
		'docs/phantoms/architecture/ACQUISITION_CHAIN_CONTRACT.md',
		'docs/phantoms/reports/021-checkpoint-1-acquisition-spoil.md',
		'docs/phantoms/reviews/020-conversation-final-review.md',
		'docs/phantoms/reviews/021-checkpoint-1-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionCatalog.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionDecision.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionGoalSpec.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionRecipePlanner.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionSourcePlanner.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionState.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionStateCodec.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionStore.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java',
		'tools/phantoms/verify-task-021c1.ps1'
	)
	foreach ($path in $required)
	{
		Assert-True ($changed.Contains($path)) "Required Goal 021c1 artifact is outside the implementation commit: $path"
	}

	foreach ($path in $changedPaths)
	{
		if ($path -match '\.(?:java|xml|md|txt|json|ps1)$' -or $path -eq 'build.xml')
		{
			[void] (Read-TargetUtf8Strict $path)
		}
	}

	$policyText = Read-TargetUtf8Strict 'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml'
	Assert-True (($policyText -notmatch '<!DOCTYPE|<!ENTITY') -and ($policyText -match 'version="1"')) "Acquisition XML is not strict version 1 or admits DTD/entity declarations."
	$policy = [xml] $policyText
	$methods = @($policy.acquisitionPolicy.methods.method)
	Assert-True (($methods.Count -eq 5) -and (($methods | ForEach-Object { $_.key } | Sort-Object -Unique).Count -eq 5)) "Acquisition methods are not exact and unique."
	Assert-True ((($methods | Where-Object key -eq 'death_drop').status -eq 'EXECUTABLE') -and (($methods | Where-Object key -eq 'spoil_sweep').status -eq 'EXECUTABLE')) "Death-drop/spoil execution status changed."
	Assert-True ((($methods | Where-Object key -eq 'recipe_preparation').status -eq 'PLANNING_ONLY') -and (($methods | Where-Object key -eq 'manor_crop').status -eq 'DEFERRED_CHECKPOINT_2') -and (($methods | Where-Object key -eq 'quest_collection').status -eq 'DEFERRED_CHECKPOINT_2')) "Recipe/manor/quest checkpoint boundary changed."

	$catalog = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionCatalog.java'
	$state = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionState.java'
	$codec = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionStateCodec.java'
	$planner = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionSourcePlanner.java'
	$recipe = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionRecipePlanner.java'
	$service = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java'
	$decision = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionDecision.java'
	Assert-True ($catalog.Contains('disallow-doctype-decl') -and $catalog.Contains('setExpandEntityReferences(false)') -and $catalog.Contains('Invalid acquisition policy element')) "Acquisition catalog is not strict/XXE-safe."
	Assert-True ($state.Contains('MAX_CANDIDATES = 8') -and $state.Contains('MAX_RECEIPTS = 8') -and $state.Contains('MAX_SWITCHES = 4') -and $state.Contains('MAX_RECIPE_NODES = 48') -and $state.Contains('observedProgress')) "Acquisition durable bounds or baseline progress are incomplete."
	Assert-True ($codec.Contains('DECLARED_WORST_CASE_BYTES = 3824') -and $codec.Contains('PhantomProfileComponent.MAX_PAYLOAD_BYTES') -and $codec.Contains('Non-canonical or trailing acquisition.state payload') -and $codec.Contains('Unknown acquisition.state version')) "Acquisition codec is not bounded and fail-closed."
	Assert-True ($planner.Contains('_knowledge.dropSources') -and $planner.Contains('_knowledge.spoilSources') -and $planner.Contains('_knowledge.spawnAreas') -and $planner.Contains('_topology.snapshot') -and $planner.Contains('_progression.capabilities')) "Acquisition source planner bypasses required indexed authorities."
	Assert-True ($recipe.Contains('_limits.recipeDepth()') -and $recipe.Contains('_limits.recipeNodes()') -and $recipe.Contains('_limits.deficits()') -and $recipe.Contains('!path.add(itemId)')) "Recipe DAG bounds/cycle control are incomplete."
	Assert-True ($decision.Contains('One bounded persisted acquisition transition per Decision step') -and $decision.Contains('PhantomAcquisitionService.BACKGROUND_ACTION')) "Acquisition Decision integration is incomplete."
	Assert-True ($service.Contains('beginCombat') -and $service.Contains('castAcquisition') -and $service.Contains('AcquisitionSkillKind.SWEEP') -and $service.Contains('releaseExternal') -and $service.Contains('beginStop')) "Active spoil/Combat/sweep ownership or lifecycle is incomplete."
	Assert-True ($service -notmatch '\.addItem\s*\(|\.destroyItem\s*\(|\.setCount\s*\(|new\s+Thread\s*\(|ThreadPool\.|ScheduledFuture|ExecutorService') "Acquisition service contains direct inventory mutation or owns a worker."

	$backgroundModel = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java'
	$backgroundTransaction = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java'
	$backgroundAuthority = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java'
	Assert-True ($backgroundModel.Contains('ACQUISITION_DEATH_DROP') -and $backgroundModel.Contains('ACQUISITION_SPOIL_SWEEP') -and $backgroundModel.Contains('ACQUISITION_TARGET') -and $backgroundModel.Contains('INCIDENTAL_DEATH_DROP') -and $backgroundModel.Contains('acquisitionTargetDelta')) "Background acquisition parity model is incomplete."
	Assert-True ($backgroundTransaction.Contains('AFTER_GOAL_STATE_WRITE') -and $backgroundTransaction.Contains('AFTER_ACQUISITION_STATE_WRITE') -and $backgroundTransaction.Contains('validateCommittedAcquisition') -and $backgroundTransaction.Contains('TerminalResult.COMMITTED') -and $backgroundTransaction.Contains('VERIFY_PENDING')) "Atomic item/background/Goal/acquisition recovery is incomplete."
	Assert-True ($backgroundAuthority.Contains('acquisitionInput') -and $backgroundAuthority.Contains('durableSpoilEligible') -and $backgroundAuthority.Contains('DropOrigin.ACQUISITION_TARGET')) "Background acquisition authority or durable spoil capability is incomplete."

	$combatService = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java'
	$combatBackend = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java'
	Assert-True ($combatService.Contains('acquireExternalAction') -and $combatService.Contains('ExternalActionLease') -and $combatBackend.Contains('castAcquisition') -and $combatBackend.Contains('cancelExternalAction')) "Existing Combat ownership seam is incomplete."

	$system = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Assert-True ($system.Contains('new PhantomAcquisitionService') -and $system.Contains('new PhantomAcquisitionDecision') -and $system.Contains('_acquisitionService.beginStop') -and $system.Contains('_acquisitionService.finishStop')) "PhantomSystem acquisition composition/lifecycle is incomplete."
	Assert-True ($system.IndexOf('new PhantomAcquisitionService') -gt $system.IndexOf('if (_productionMaterialization)')) "Acquisition runtime escaped the production/feature-gated composition path."

	$tests = (Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java') + "`n" + (Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java') + "`n" + (Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java')
	foreach ($evidence in @('100_000', '10_000', 'AFTER_OPERATION_COMMIT', 'ACQUISITION_INELIGIBLE', 'SLOT_CAPACITY', 'currentClaims()', 'externalClaims()', 'navigationClaims()', 'castAcquisition', 'AcquisitionSkillKind.SWEEP'))
	{
		Assert-True ($tests.Contains($evidence)) "Mandatory acquisition test evidence is absent: $evidence"
	}
	$build = Read-TargetUtf8Strict 'build.xml'
	$launcher = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java'
	foreach ($focusedMode in @('acquisition-catalog-codec', 'acquisition-source-planner', 'acquisition-recipe-planning', 'acquisition-active-spoil', 'acquisition-background-parity', 'acquisition-atomic-restart', 'acquisition-source-switching', 'acquisition-lifecycle-performance'))
	{
		Assert-True ($build.Contains($focusedMode) -and $launcher.Contains($focusedMode)) "Focused acquisition mode is not wired: $focusedMode"
	}
	Assert-True ($build.Contains('phantom-acquisition-checkpoint1-test') -and $build.Contains($RequiredSeed)) "Final acquisition aggregate or seed is absent."

	$architecture = Read-TargetUtf8Strict 'docs/phantoms/architecture/ACQUISITION_CHAIN_CONTRACT.md'
	$report = Read-TargetUtf8Strict 'docs/phantoms/reports/021-checkpoint-1-acquisition-spoil.md'
	$review020 = Read-TargetUtf8Strict 'docs/phantoms/reviews/020-conversation-final-review.md'
	$review021 = Read-TargetUtf8Strict 'docs/phantoms/reviews/021-checkpoint-1-independent-review.md'
	Assert-True ($review020.Contains('ACCEPT') -and $review020.Contains($RequiredParent)) "Goal 020 final ACCEPT review is not pinned."
	Assert-True ($architecture.Contains('DEFERRED_CHECKPOINT_2') -and $architecture.Contains('Goal 022') -and $architecture.Contains('4096') -and $architecture.Contains('VERIFY_PENDING')) "Acquisition architecture contract is incomplete."
	Assert-True ($report.Contains('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW') -and $report.Contains($RequiredSeed) -and $review021.Contains('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW')) "Goal 021 report/review status or seed is incomplete."
	Assert-True (($report -split "`n").Count -le 240) "Goal 021 report exceeds 240 lines."

	$escaped = '\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};'
	foreach ($path in @(
		'docs/phantoms/architecture/ACQUISITION_CHAIN_CONTRACT.md',
		'docs/phantoms/reports/021-checkpoint-1-acquisition-spoil.md',
		'docs/phantoms/reviews/020-conversation-final-review.md',
		'docs/phantoms/reviews/021-checkpoint-1-independent-review.md',
		'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml'
	))
	{
		Assert-True ((Read-TargetUtf8Strict $path) -notmatch $escaped) "Escaped Cyrillic is present in a new Goal 021 artifact: $path"
	}

	if ($script:Mode -eq "committed")
	{
		Assert-True ((Git-Lines @("status", "--porcelain")).Count -eq 0) "Post-commit Goal 021c1 worktree is not clean."
		$remote = (Git-Lines @("rev-parse", "origin/$RequiredBranch") | Select-Object -First 1)
		Assert-True ($remote -eq $head) "Goal 021c1 HEAD is not pushed to origin/feature/phantom-world."
		$jarPath = Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar'
		Assert-True (Test-Path -LiteralPath $jarPath -PathType Leaf) "GameServer.jar is absent after Goal 021c1."
		$jarEntries = @(& jar tf $jarPath)
		Assert-True ($LASTEXITCODE -eq 0) "GameServer.jar could not be inspected."
		foreach ($class in @('PhantomAcquisitionCatalog.class', 'PhantomAcquisitionDecision.class', 'PhantomAcquisitionService.class', 'PhantomAcquisitionStateCodec.class', 'PhantomAcquisitionStore.class'))
		{
			Assert-True ($jarEntries -contains "org/l2jmobius/gameserver/phantoms/acquisition/$class") "GameServer.jar is missing acquisition class: $class"
		}
	}

	Write-Output "GOAL_021C1_VERIFIED"
}
finally
{
	Pop-Location
}
