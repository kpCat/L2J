param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$implementationCommit = "cb4fa6486dd705f5ba46d92bd8576424cbd188ee"
$implementationParent = "696689987276137f6a7f3661329171c9ee65e6f9"
$requiredBranch = "feature/phantom-world"
$implementationSubject = "fix(phantoms): harden commerce ownership and integration"
$completionSubject = "fix(phantoms): complete commerce hardening gate"
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
	return $local.StartsWith("java/org/l2jmobius/gameserver/phantoms/commerce/", [StringComparison]::Ordinal) `
		-or $local -eq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java" `
		-or $local -eq "build.xml" `
		-or $local -eq "test/java/org/l2jmobius/tests/phantoms/PhantomCommerceSuite.java" `
		-or $local -eq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java" `
		-or $local -eq "tools/phantoms/verify-task-014.ps1" `
		-or $local -eq "tools/phantoms/verify-task-014a.ps1" `
		-or $local -eq "docs/PHANTOM_BOTS_ROADMAP.md" `
		-or $local -eq "docs/phantoms/architecture/COMMERCE_SUPPLY_TRAVEL_CONTRACT.md" `
		-or $local -eq "docs/phantoms/reports/014-npc-commerce-supply-travel-loop.md" `
		-or $local -eq "docs/phantoms/reviews/014-npc-commerce-supply-travel-loop-review.md" `
		-or $local -eq "docs/phantoms/reports/014a-commerce-ownership-integration-hardening.md" `
		-or $local.StartsWith("docs/phantoms/tasks/014a-commerce-ownership-integration-hardening/", [StringComparison]::Ordinal)
}

$branch = (& git -C $repositoryRoot branch --show-current).Trim()
Assert-True ($branch -eq $requiredBranch) "Wrong branch: $branch"
$head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$implementationParents = @(((& git -C $repositoryRoot show -s --format=%P $implementationCommit).Trim() -split " ") | Where-Object { $_ })
Assert-True ($implementationParents.Count -eq 1) "Goal 014A implementation commit is a merge commit."
Assert-True ($implementationParents[0] -eq $implementationParent) "Goal 014A implementation commit parent is not exact."
Assert-True ((& git -C $repositoryRoot show -s --format=%s $implementationCommit).Trim() -eq $implementationSubject) "Goal 014A implementation commit subject is not exact."
& git -C $repositoryRoot merge-base --is-ancestor $implementationCommit $head
Assert-True ($LASTEXITCODE -eq 0) "Goal 014A implementation commit is not an ancestor of HEAD."
$graphMode = "working-completion"
if ($head -ne $implementationCommit)
{
	$parents = @(((& git -C $repositoryRoot show -s --format=%P $head).Trim() -split " ") | Where-Object { $_ })
	Assert-True ($parents.Count -eq 1) "Goal 014A completion HEAD is a merge commit."
	Assert-True ($parents[0] -eq $implementationCommit) "Goal 014A completion HEAD is not a direct child of the implementation commit."
	Assert-True ((& git -C $repositoryRoot show -s --format=%s $head).Trim() -eq $completionSubject) "Goal 014A completion commit subject is not exact."
	Assert-True ([int](& git -C $repositoryRoot rev-list --count "$implementationCommit..$head") -eq 1) "Goal 014A graph contains more than one completion child."
	$graphMode = "committed-completion"
}

$changedPaths = @(& git -c core.autocrlf=false -C $repositoryRoot diff --name-only "$implementationParent..$head")
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
Assert-True ($changedPaths.Count -gt 0) "No Goal 014A artifacts found."
foreach ($path in $changedPaths)
{
	Assert-True (Is-AllowedPath $path) "Out-of-scope path: $path"
}

foreach ($obsolete in @("CODEX_EXECUTION_BUDGET_BLOCK.md", "MANIFEST.json", "PHANTOM_CODEX_EFFICIENCY_STANDARD.md"))
{
	Assert-True (-not (Test-Path -LiteralPath (Join-Path $moduleRoot $obsolete))) "Obsolete root file is present: $obsolete"
}

$requiredFiles = @(
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceCatalog.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCommerceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"tools/phantoms/verify-task-014.ps1",
	"tools/phantoms/verify-task-014a.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/reviews/014-npc-commerce-supply-travel-loop-review.md",
	"docs/phantoms/reports/014a-commerce-ownership-integration-hardening.md"
)
foreach ($local in $requiredFiles)
{
	Assert-True (Test-Path -LiteralPath (Join-Path $moduleRoot $local) -PathType Leaf) "Missing required file: $local"
}

$serviceText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java")
foreach ($fact in @("_currentOperations", "_peakOperations", "_currentActorLeases", "_peakActorLeases", "_currentPersistenceClaims", "_peakPersistenceClaims", "PhantomGoalStatus.ACTIVE", "GOAL_REVISION_CONFLICT", "STALE_GOAL_REVISION", "STALE_GOAL", "receipt.state() == State.ABORTED"))
{
	Assert-True ($serviceText.Contains($fact)) "Commerce hardening fact is missing: $fact"
}
Assert-True ([regex]::IsMatch($serviceText, "finishStop\(\)[\s\S]*?_currentOperations\s*!=\s*0[\s\S]*?_currentActorLeases\s*!=\s*0[\s\S]*?_currentPersistenceClaims\s*!=\s*0")) "finishStop does not visibly gate all current ownership counters."
Assert-True ($serviceText.Contains("_goalStore.load(profileId)")) "Commerce does not consult persisted current goal authority."
Assert-True ([regex]::IsMatch($serviceText, "final Reason authority = goalAuthority\([\s\S]*?final PhantomCommerceReceipt prepared =[\s\S]*?cancelled\.getAsBoolean\(\)[\s\S]*?final Reason currentAuthority = goalAuthority\([\s\S]*?saveReceipt\(expectedVersion, prepared\)")) "Current goal authority is not rechecked immediately before PREPARED persistence."

$catalogText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceCatalog.java")
Assert-True ($catalogText.Contains("findBuyOffer(int listId, int itemId)") -and $catalogText.Contains("findTeleportRoute(int npcId, String listName, int ordinal)")) "Exact catalog queries are missing."
Assert-True ($catalogText.Contains("Duplicate buy offer identity") -and $catalogText.Contains("Duplicate teleport route identity")) "Duplicate exact catalog identities do not fail construction."

$backendText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java")
Assert-True ($backendText.Contains("_catalog.findBuyOffer(") -and $backendText.Contains("_catalog.findTeleportRoute(")) "L2jCommerceBackend does not use exact catalog identity."
Assert-True (-not $backendText.Contains("_catalog.findBuyOffers(") -and -not $backendText.Contains("_catalog.findTeleportRoutes(")) "L2jCommerceBackend still performs page-0 exact lookup."

$systemText = Read-Utf8Strict (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java")
Assert-True ($systemText.Contains("final PhantomGoalStateStore goalStateStore") -and $systemText.Contains("new PhantomCommerceService(commerceCatalog, new PhantomCommerceReceiptStore(profileRepository), goalStateStore") -and $systemText.Contains("new PhantomDecisionEngine(goalStateStore")) "PhantomSystem does not share one goal-state authority instance."

$suiteText = Read-Utf8Strict (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomCommerceSuite.java")
foreach ($fact in @("commerce-hardening", "new L2jCommerceBackend(", "new Merchant(", "new Teleporter(", "materialization.dematerialize(", "materialization.materialize(", "player.storeMe()", "currentPersistenceClaims()", "GOAL_REVISION_CONFLICT", "STALE_GOAL_REVISION", "authorityRaceStore.value == null", "authorityRaceActor.firstCalls.get()", "authorityRaceActor.secondCalls.get()", "TELEPORT_PENDING"))
{
	Assert-True ($suiteText.Contains($fact)) "Commerce hardening integration evidence is missing: $fact"
}

$buildText = Read-Utf8Strict (Join-Path $moduleRoot "build.xml")
$launcherText = Read-Utf8Strict (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java")
Assert-True ($buildText.Contains('name="phantom.goal014a.seed" value="14001401"') -and $buildText.Contains('name="phantom-commerce-hardening-test"') -and $buildText.Contains("phantom-static-verify-014a") -and $launcherText.Contains('case "commerce-hardening"')) "Goal 014A build/launcher route is incomplete."

$productionFiles = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce") -Filter "*.java")
$productionText = ($productionFiles | ForEach-Object { Read-Utf8Strict $_.FullName }) -join "`n"
Assert-True (-not [regex]::IsMatch($productionText, "\b(new\s+Thread|ThreadPool|ScheduledFuture|ExecutorService|scheduleAtFixedRate|scheduleWithFixedDelay)\b")) "Commerce production introduced a worker or Future."
Assert-True (-not $productionText.Contains("progression.learn_skill")) "Commerce production crossed into progression."

$verifier014Text = Read-Utf8Strict (Join-Path $moduleRoot "tools/phantoms/verify-task-014.ps1")
Assert-True (-not $verifier014Text.Contains('$preExistingUntracked')) "Goal 014 verifier still has the obsolete-root special whitelist."

$roadmapText = Read-Utf8Strict (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md")
Assert-True ($roadmapText.Contains("Goal 014: FIX_REQUIRED after first review") -and $roadmapText.Contains("Goal 014A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmapText.Contains("Goal 015: NOT_STARTED") -and $roadmapText.Contains("Goal 017: NOT_STARTED") -and $roadmapText.Contains("Goal 025: NOT_STARTED")) "Roadmap progress truth is incomplete."
$reviewText = Read-Utf8Strict (Join-Path $moduleRoot "docs/phantoms/reviews/014-npc-commerce-supply-travel-loop-review.md")
Assert-True ($reviewText.Contains("FIX_REQUIRED after first review") -and $reviewText.Contains("Goal 014A")) "Goal 014 first-review findings are missing."

$reportPath = Join-Path $moduleRoot "docs/phantoms/reports/014a-commerce-ownership-integration-hardening.md"
Assert-True ((Get-Content -LiteralPath $reportPath -Encoding utf8).Count -le 140) "Goal 014A report exceeds 140 lines."
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

Write-Output "TASK014A_VERIFIER_OK"
Write-Output "branch=$branch"
Write-Output "graph=$graphMode"
Write-Output "implementationCommit=$implementationCommit"
Write-Output "implementationParent=$implementationParent"
Write-Output "scopePaths=$($changedPaths.Count)"
Write-Output "seed=14001401"
Write-Output "lifecycle=OPERATIONS_ACTORS_PERSISTENCE"
Write-Output "goalAuthority=CURRENT_ACTIVE_PERSISTED"
Write-Output "catalog=EXACT_UNBOUNDED"
Write-Output "backend=L2J_PLAYER_BUY_SELL_NORMAL_TELEPORT"
Write-Output "utf8=STRICT"
Write-Output "jar=present"
