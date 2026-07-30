param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$lootCommit = "b800f125bddedadd4f181e9a5f398283e73c4c13"
$lootParent = "32be3bbc320bc3a054aab8c5d39001910f35e4b8"
$requiredParent = $lootCommit
$requiredBranch = "feature/phantom-world"
$requiredSubject = "fix(phantoms): canonicalize background anchor positions"
$acceptedCommits = @(
	$lootParent,
	$lootCommit
)
$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$repositoryRoot = (Resolve-Path ((& git -C $moduleRoot rev-parse --show-toplevel).Trim())).Path
$repositoryPrefix = $repositoryRoot.TrimEnd("\", "/") + "\"
if (-not $moduleRoot.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase))
{
	throw "Module root is outside repository root."
}
$moduleRelative = $moduleRoot.Substring($repositoryPrefix.Length).Replace("\", "/")

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Read-Utf8Strict([string] $path)
{
	$encoding = [Text.UTF8Encoding]::new($false, $true)
	return $encoding.GetString([IO.File]::ReadAllBytes($path))
}

function Is-AllowedPath([string] $path)
{
	$prefix = "$moduleRelative/"
	if (-not $path.StartsWith($prefix, [StringComparison]::Ordinal))
	{
		return $false
	}
	$local = $path.Substring($prefix.Length)
	return $local -eq "java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java" `
		-or $local -eq "build.xml" `
		-or $local -eq "test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java" `
		-or $local -eq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java" `
		-or $local -eq "tools/phantoms/verify-task-015.ps1" `
		-or $local -eq "docs/PHANTOM_BOTS_ROADMAP.md" `
		-or $local -eq "docs/phantoms/architecture/BACKGROUND_FARMING_RECONCILIATION_CONTRACT.md" `
		-or $local -eq "docs/phantoms/reports/015-background-farming-reconciliation.md" `
		-or $local -eq "docs/phantoms/reviews/015-background-position-canonicalization-review.md"
}

$branch = (& git -C $repositoryRoot branch --show-current).Trim()
Assert-True ($branch -eq $requiredBranch) "Wrong branch: $branch"
$head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
foreach ($accepted in $acceptedCommits)
{
	& git -C $repositoryRoot merge-base --is-ancestor $accepted $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted commit is not an ancestor of HEAD: $accepted"
}
Assert-True ((& git -C $repositoryRoot show -s --format=%P $lootCommit).Trim() -eq $lootParent) "Accepted production loot commit has the wrong parent."
Assert-True ((& git -C $repositoryRoot show -s --format=%s $lootCommit).Trim() -eq "fix(phantoms): support ground-loss production drops") "Accepted production loot commit subject changed."

$graphMode = "working-goal"
if ($head -ne $requiredParent)
{
	$parents = @(((& git -C $repositoryRoot show -s --format=%P $head).Trim() -split " ") | Where-Object { $_ })
	Assert-True ($parents.Count -eq 1) "Goal 015 HEAD is a merge commit."
	Assert-True ($parents[0] -eq $requiredParent) "Goal 015 HEAD is not the direct child of the required parent."
	Assert-True ((& git -C $repositoryRoot show -s --format=%s $head).Trim() -eq $requiredSubject) "Goal 015 commit subject is not exact."
	Assert-True ([int](& git -C $repositoryRoot rev-list --count "$requiredParent..$head") -eq 1) "Goal 015 graph has more than one child commit."
	$graphMode = "committed-goal"
}

$changedPaths = @(& git -c core.autocrlf=false -C $repositoryRoot diff --name-only "$requiredParent..$head")
foreach ($line in @(& git -C $repositoryRoot status --porcelain=v1 --untracked-files=all -- $moduleRoot))
{
	if ($line.Length -ge 4)
	{
		$path = $line.Substring(3).Replace("\", "/")
		if ($path.StartsWith('"') -and $path.EndsWith('"'))
		{
			$path = $path.Substring(1, $path.Length - 2)
		}
		$changedPaths += $path
	}
}
$changedPaths = @($changedPaths | Where-Object { $_ } | Sort-Object -Unique)
Assert-True ($changedPaths.Count -gt 0) "No Goal 015 artifacts found."
foreach ($path in $changedPaths)
{
	Assert-True (Is-AllowedPath $path) "Out-of-scope path: $path"
}

foreach ($obsolete in @("CODEX_EXECUTION_BUDGET_BLOCK.md", "MANIFEST.json", "PHANTOM_CODEX_EFFICIENCY_STANDARD.md"))
{
	Assert-True (-not (Test-Path -LiteralPath (Join-Path $moduleRoot $obsolete))) "Obsolete root file is present: $obsolete"
}

$requiredFiles = @(
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundStateCodec.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundInventoryHash.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundLoginGuard.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java",
	"java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java",
	"java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java",
	"java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java",
	"java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecycleBridge.java",
	"java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java",
	"java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityWorkSinkBridge.java",
	"java/org/l2jmobius/gameserver/network/GameClient.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java",
	"tools/phantoms/verify-task-015.ps1",
	"docs/phantoms/architecture/BACKGROUND_FARMING_RECONCILIATION_CONTRACT.md",
	"docs/phantoms/reports/015-background-farming-reconciliation.md",
	"docs/phantoms/reviews/015-background-farming-reconciliation-review.md",
	"docs/phantoms/reviews/015-production-loot-disposition-unblock-review.md",
	"docs/phantoms/reviews/015-background-position-canonicalization-review.md"
)
foreach ($local in $requiredFiles)
{
	Assert-True (Test-Path -LiteralPath (Join-Path $moduleRoot $local) -PathType Leaf) "Missing required file: $local"
}

$packageHashes = @{
	"docs/phantoms/tasks/015-production-loot-disposition-unblock/ACCEPTANCE.md" = "10B09C6E753D3342496ED615C97BBFC3568D406E358CCF89D5EE1E716E502C80"
	"docs/phantoms/tasks/015-production-loot-disposition-unblock/CODEX_LAUNCHER.txt" = "5C4B3921018327093038CBDA14CCFDAB84D5472088C0973E8531C2BAC20DD032"
	"docs/phantoms/tasks/015-production-loot-disposition-unblock/PACKAGE_MANIFEST.json" = "191D0CF239F17306C113883FC1F1463D227C5944A52CA859EAE349A508A13595"
	"docs/phantoms/tasks/015-production-loot-disposition-unblock/TASK.md" = "6F6C390C10976ED7C4727E6A1A51242AA0AD867877E06B4770C80E10873737E4"
}
foreach ($entry in $packageHashes.GetEnumerator())
{
	$actual = (Get-FileHash -LiteralPath (Join-Path $moduleRoot $entry.Key) -Algorithm SHA256).Hash
	Assert-True ($actual -eq $entry.Value) "Task package payload changed: $($entry.Key)"
}

$stateText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java")
foreach ($fact in @('COMPONENT_TYPE = "background.state"', "SCHEMA_VERSION = 2", "MODEL_VERSION = 1", "MATERIALIZED", "READY", "VERIFY_PENDING", "DEAD", "INCONSISTENT", "activityGeneration", "tickSequence", "committedAnchorId", "mutableItemIds", "canonicalHash"))
{
	Assert-True ($stateText.Contains($fact)) "Background state fact is missing: $fact"
}

$modelText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java")
foreach ($fact in @("MAX_ENCOUNTERS = 32", "MAX_ELAPSED_MILLIS = 60_000", "MAX_CHANGED_ITEM_OBJECTS = 16", "MAX_NEW_NON_STACKABLE_OBJECTS = 8", "MAX_GROUND_LOSS_ITEM_IDS = 96", "DropDisposition", "LEAVE_ON_GROUND", "groundLosses", "Math.round(experience)", "(long) skillPoints", "random.variance()", "calculateDeathExperienceLoss", "rollDrops", "groups.values()"))
{
	Assert-True ($modelText.Contains($fact)) "BACKGROUND_MODEL_V1 fact is missing: $fact"
}

$authorityText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java")
foreach ($fact in @("LOOT_POLICY_V1", "compositeKnowledgeHash", "AUTO_LOOT_HERBS", "AUTO_LOOT", "AUTO_LOOT_SLOT_LIMIT", "AUTO_LOOT_ITEM_IDS", "DropDisposition.LEAVE_ON_GROUND", "isFlying()", "isFlyingMounted()", "isMounted()", "canonicalCommittedAnchorPosition", "GeoEngine.getInstance()", "getHeight(x, y, point.z())", "atCanonicalAnchor", "Status.ANCHOR_MISMATCH"))
{
	Assert-True ($authorityText.Contains($fact)) "Production loot authority fact is missing: $fact"
}

$transactionText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java")
foreach ($fact in @("QUERY_TIMEOUT_SECONDS = 5", "LOCK_PROFILE", "LOCK_COMPONENT", "LOCK_CHARACTER", "LOCK_SUBCLASS", "LOCK_SKILLS", "LOCK_ITEMS", "ORDER BY skill_id FOR UPDATE", "ORDER BY object_id FOR UPDATE", "setAutoCommit(false)", "State.VERIFY_PENDING", "expectedAfterHash", "canonicalAutoGetSkills", "IdManager.getInstance().getNextId()", "captureBaseline(PhantomBackgroundState materializedState, PhantomGoal goal)", "lockAndValidateGoal(connection, materializedState.identity().profileId(), goal)", "abortMaterialization", "PhantomBackgroundInventoryHash.compute"))
{
	Assert-True ($transactionText.Contains($fact)) "Canonical transaction fact is missing: $fact"
}

$serviceText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java")
foreach ($fact in @("_currentOperations", "_peakOperations", "_currentIdentityLeases", "_peakIdentityLeases", "_currentTransactions", "_peakTransactions", "_currentTransitionClaims", "_peakTransitionClaims", "OwnerKind.BACKGROUND", "PlayerAutoSaveTaskManager", "PhantomActivityState.BACKGROUND", "PhantomActivityState.WARM", "DEATH_SIGNAL_SOURCE", "doRevive()", "TeleportWhereType.TOWN", "RECOVERY_TELEPORT_TIMEOUT_NANOS", "materializeSucceeded", "materializeAborted", "materializationQuiescence", "failGoal", "captureBaseline(captured, goal)"))
{
	Assert-True ($serviceText.Contains($fact)) "Background service fact is missing: $fact"
}
Assert-True ([regex]::IsMatch($serviceText, "finishStop\(\)[\s\S]*?_currentOperations\.get\(\)\s*!=\s*0[\s\S]*?_currentIdentityLeases\.get\(\)\s*!=\s*0[\s\S]*?_currentTransactions\.get\(\)\s*!=\s*0[\s\S]*?_currentTransitionClaims\.get\(\)\s*!=\s*0")) "finishStop does not visibly gate every ownership counter."

$goalText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundGoalSpec.java")
foreach ($fact in @('GOAL_TYPE = "farm.background"', 'SOURCE_NAMESPACE = "background.farm"', 'CANDIDATE_KEY = "candidate.background.farm"', 'TRAVEL_ACTION = "background.travel"', 'FARM_ACTION = "background.farm"', 'RECOVER_ACTION = "background.recover"', "<npcId>@<anchorId>"))
{
	Assert-True ($goalText.Contains($fact)) "Explicit farm.background contract is missing: $fact"
}

$identityText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java")
$clientText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/GameClient.java")
$loginGuardText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundLoginGuard.java")
$materializationText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java")
Assert-True ($identityText.Contains("BACKGROUND")) "Typed BACKGROUND identity owner is missing."
Assert-True ($clientText.Contains("OwnerKind.BACKGROUND")) "REAL_LOGIN arbitration does not recognize BACKGROUND ownership."
Assert-True ($clientText.Contains("PhantomBackgroundLoginGuard.inspect(objectId)") -and $loginGuardText.Contains("State.MATERIALIZED") -and $loginGuardText.Contains("REJECT_BACKGROUND_OWNED") -and $loginGuardText.Contains("REJECT_UNVERIFIED")) "Durable background real-login guard is incomplete."
Assert-True ($materializationText.Contains("MaterializationLifecycleAttempt") -and $materializationText.Contains("lifecycleAttempt.succeed()") -and $materializationText.Contains("lifecycleAttempt.abortUnlessCompleted()")) "Materialization attempt has no exact terminal lifecycle callback."

$planningText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/decision/PhantomPlanningContext.java")
$stepText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/decision/PhantomStepContext.java")
$engineText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java")
foreach ($fact in @("activityGeneration", "tickSequence", "effectiveState", "logicalNowNanos"))
{
	Assert-True ($planningText.Contains($fact) -and $stepText.Contains($fact)) "Decision context identity is incomplete: $fact"
}
Assert-True ($engineText.Contains("workItem.activityGeneration()") -and $engineText.Contains("workItem.tickSequence()")) "Decision engine does not propagate scheduler identity."

$systemText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java")
foreach ($fact in @("new PhantomBackgroundService(", "new PhantomBackgroundDecision(", "productionLifecycle.install(_backgroundService)", "productionWorkSink.install(_decisionEngine)", "_backgroundService.beginStop()", "_materializationService.shutdown()", "backgroundReadyForMaterializationShutdown()", "permitsMaterializationShutdown"))
{
	Assert-True ($systemText.Contains($fact)) "Production background composition fact is missing: $fact"
}
Assert-True ($systemText.IndexOf("new PhantomBackgroundService(", [StringComparison]::Ordinal) -lt $systemText.IndexOf("new PhantomDecisionEngine(", [StringComparison]::Ordinal)) "Background service is not composed before the decision engine."

$suiteText = Read-Utf8Strict (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java")
foreach ($fact in @("SEED = 15001501L", "PRODUCTION_LOOT_UNBLOCK_SEED = 15001502L", "POSITION_CANONICALIZATION", "testCanonicalAnchorPolicy", "testProductionPositionTransition", "productionTravelSelection", "canonicalAnchorPosition", "testLifecycleLoop(1, 100)", "testLifecycleLoop(50, 0)", "identity <= 300", "100_000", "10_000", "BEFORE_OPERATION_COMMIT", "AFTER_OPERATION_COMMIT", "Status.INCONSISTENT", "PRODUCTION_TARGET_NPC_ID", "PRODUCTION_FARM_ANCHOR_ID", "PhantomTopologyLoader", "Player.load(", "2509", "6645", "Disabled PhantomSystem", "testMaterializationAbortMatrix", "testMaterializingQuiescence", "testCompactInventoryHash", "testAuthoritativeShotContract", "testProductionCorpusAudit", "testProductionLootPolicy", "testGroundLossModelSemantics", "testProductionLootBatch", "LOOT_POLICY_V1", "LEAVE_ON_GROUND", "groundLosses", "testDeathRecovery", "testRealLoginGuard", "10655", "13028", "length <= 4096", "COUNT(*) FROM items"))
{
	Assert-True ($suiteText.Contains($fact)) "Goal 015 evidence is missing: $fact"
}
Assert-True (-not $suiteText.Contains("exactAnchorLifecycle")) "Test-only exact-anchor lifecycle masking is present."
Assert-True (-not $suiteText.Contains("setXYZInvisible")) "Post-load raw-anchor coordinate masking is present."

$buildText = Read-Utf8Strict (Join-Path $moduleRoot "build.xml")
$launcherText = Read-Utf8Strict (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java")
foreach ($target in @("phantom-background-position-canonicalization-test", "phantom-background-production-loot-unblock-test", "phantom-background-model-test", "phantom-background-transaction-test", "phantom-background-lifecycle-test", "phantom-background-decision-test", "phantom-background-server-integration-test", "phantom-background-performance-smoke", "phantom-background-materialization-abort-test", "phantom-background-quiescence-test", "phantom-background-compact-inventory-test", "phantom-background-authoritative-shots-test", "phantom-background-production-audit-test", "phantom-background-recovery-teleport-test", "phantom-background-real-login-test", "phantom-background-test", "phantom-background-completion-test", "phantom-static-verify-015"))
{
	Assert-True ($buildText.Contains("`"$target`"")) "Missing Goal 015 Ant target: $target"
}
Assert-True ($buildText.Contains('name="phantom.goal015.seed" value="15001501"') -and $buildText.Contains('name="phantom.goal015.production.unblock.seed" value="15001502"') -and $buildText.Contains("phantom-background-test") -and $buildText.Contains("phantom-static-verify-015")) "Goal 015 is not in cumulative verify."
foreach ($mode in @("background-position-canonicalization", "background-production-loot-unblock", "background-model", "background-transaction", "background-lifecycle", "background-decision", "background-server-integration", "background-performance", "background-materialization-abort", "background-quiescence", "background-compact-inventory", "background-authoritative-shots", "background-production-audit", "background-recovery-teleport", "background-real-login"))
{
	Assert-True ($launcherText.Contains("case `"$mode`"")) "Missing Goal 015 launcher mode: $mode"
}

$productionFiles = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/background") -Filter "*.java")
$productionText = ($productionFiles | ForEach-Object { Read-Utf8Strict $_.FullName }) -join "`n"
Assert-True (-not [regex]::IsMatch($productionText, "\b(new\s+Thread|ThreadPool|ScheduledFuture|CompletableFuture|ExecutorService|scheduleAtFixedRate|scheduleWithFixedDelay)\b")) "Background production introduced a worker/task/Future."
Assert-True (-not [regex]::IsMatch($productionText, "\b(Logger|LOGGER|_LOGGER)\b")) "Background production introduced high-frequency logging surface."
Assert-True (-not $productionText.Contains("progression.learn_skill")) "Background production crossed the Goal 013B activation gate."

$architectureText = Read-Utf8Strict (Join-Path $moduleRoot "docs/phantoms/architecture/BACKGROUND_FARMING_RECONCILIATION_CONTRACT.md")
foreach ($fact in @("BACKGROUND_MODEL_V1", "VERIFY_PENDING", "Math.round", "NpcTemplate.calculateDrops", "autoCommit=false", "giran.farming.22859", "LOOT_POLICY_V1", "LEAVE_ON_GROUND", "groundLosses", "canonicalCommittedAnchorPosition", "GeoEngine.getHeight", "Supported production pair count"))
{
	Assert-True ($architectureText.Contains($fact)) "Architecture contract fact is missing: $fact"
}
$roadmapText = Read-Utf8Strict (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md")
Assert-True ($roadmapText.Contains("Goal 013: ACCEPT after Goal 013B") -and $roadmapText.Contains("Goal 013B: ACCEPT_WITH_ACTIVATION_GATE") -and $roadmapText.Contains("Goal 014: ACCEPT after Goal 014A") -and $roadmapText.Contains("Goal 014A + completion: ACCEPT") -and $roadmapText.Contains("Goal 015: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmapText.Contains("Goal 017: NOT_STARTED") -and $roadmapText.Contains("Goal 025: NOT_STARTED")) "Roadmap progress truth is incomplete."
$masterPlanText = Read-Utf8Strict (Join-Path $moduleRoot "PHANTOM_DEVELOPMENT_MASTER_PLAN.md")
Assert-True ($masterPlanText.Contains("### 015. Background farming") -and $masterPlanText.Contains('`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`')) "Master plan Goal 015 status line is stale."
$reviewText = Read-Utf8Strict (Join-Path $moduleRoot "docs/phantoms/reviews/015-production-loot-disposition-unblock-review.md")
Assert-True ($reviewText.Contains('`PENDING_INDEPENDENT_REVIEW`') -and $reviewText.Contains($lootParent) -and $reviewText.Contains('Supported production pair count: `1`') -and $reviewText.Contains("22859@giran.farming.22859")) "Goal 015 production unblock review truth is incomplete."
$positionReviewText = Read-Utf8Strict (Join-Path $moduleRoot "docs/phantoms/reviews/015-background-position-canonicalization-review.md")
Assert-True ($positionReviewText.Contains('`PENDING_INDEPENDENT_REVIEW`') -and $positionReviewText.Contains($requiredParent) -and $positionReviewText.Contains($requiredSubject) -and $positionReviewText.Contains("canonicalCommittedAnchorPosition") -and $positionReviewText.Contains("Player.load")) "Goal 015 position canonicalization review truth is incomplete."
$reportPath = Join-Path $moduleRoot "docs/phantoms/reports/015-background-farming-reconciliation.md"
Assert-True ((Get-Content -LiteralPath $reportPath -Encoding UTF8).Count -le 170) "Goal 015 report exceeds the 170-line efficiency bound."
$reportText = Read-Utf8Strict $reportPath
Assert-True ($reportText.Contains('`IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`') -and $reportText.Contains("15001502") -and $reportText.Contains("LOOT_POLICY_V1") -and $reportText.Contains('Supported production pair count: `1`')) "Goal 015 production unblock report truth is incomplete."
Assert-True (Test-Path -LiteralPath (Join-Path $moduleRoot "dist/libs/GameServer.jar") -PathType Leaf) "GameServer.jar is missing."

$mojibakeMarkers = @(
	(-join @([char]0x0420, [char]0x045F)),
	(-join @([char]0x0420, [char]0x045C)),
	(-join @([char]0x0420, [char]0x045B)),
	(-join @([char]0x0420, [char]0x2022)),
	(-join @([char]0x0420, [char]0x040E)),
	(-join @([char]0x0420, [char]0x203A)),
	(-join @([char]0x0420, [char]0x00A4)),
	(-join @([char]0x0420, [char]0x045A)),
	(-join @([char]0x0420, [char]0x0408)),
	(-join @([char]0x0420, [char]0x0459)),
	(-join @([char]0x0420, [char]0x0491)),
	(-join @([char]0x0420, [char]0x00B5)),
	(-join @([char]0x0420, [char]0x00B0)),
	(-join @([char]0x0420, [char]0x00BB)),
	(-join @([char]0x0420, [char]0x0405)),
	(-join @([char]0x0420, [char]0x0455)),
	(-join @([char]0x0421, [char]0x040F)),
	(-join @([char]0x0421, [char]0x20AC)),
	(-join @([char]0x0421, [char]0x0402)),
	(-join @([char]0x0421, [char]0x2039)),
	(-join @([char]0x0421, [char]0x040A)),
	(-join @([char]0x0421, [char]0x201A)),
	(-join @([char]0x0421, [char]0x0453)),
	(-join @([char]0x0421, [char]0x2021)),
	(-join @([char]0x0421, [char]0x2026)),
	(-join @([char]0x0421, [char]0x2020)),
	[string][char]0xFFFD
)
$textExtensions = @(".java", ".xml", ".md", ".ps1", ".json", ".txt")
foreach ($path in $changedPaths)
{
	$fullPath = Join-Path $repositoryRoot $path
	if ((Test-Path -LiteralPath $fullPath -PathType Leaf) -and ($textExtensions -contains [IO.Path]::GetExtension($fullPath)))
	{
		$text = Read-Utf8Strict $fullPath
		foreach ($marker in $mojibakeMarkers)
		{
			Assert-True (-not $text.Contains($marker)) "Mojibake marker found in $path"
		}
		Assert-True (-not [regex]::IsMatch($text, "\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};")) "Escaped Cyrillic found in $path"
	}
}

Write-Output "TASK015_VERIFIER_OK"
Write-Output "branch=$branch"
Write-Output "graph=$graphMode"
Write-Output "requiredParent=$requiredParent"
Write-Output "scopePaths=$($changedPaths.Count)"
Write-Output "seed=15001502"
Write-Output "model=BACKGROUND_MODEL_V1"
Write-Output "state=VERSION2_COMPACT_HASH_VERIFY_PENDING"
Write-Output "transaction=ONE_CANONICAL_MARIADB_BATCH"
Write-Output "identity=BACKGROUND_TYPED_LEASE"
Write-Output "lootPolicy=LOOT_POLICY_V1"
Write-Output "position=GEOENGINE_CANONICAL_COMMITTED_ANCHOR"
Write-Output "productionPairs=1_SUPPORTED"
Write-Output "workers=0"
Write-Output "utf8=STRICT"
Write-Output "jar=present"
