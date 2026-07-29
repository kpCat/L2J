param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$goal014Commit = "696689987276137f6a7f3661329171c9ee65e6f9"
$goal014Parent = "e9b98a243a68a710425a062155b9197ee6692b17"
$acceptedCompletion = "9c9412bc4a05a520a83b5187054d6c8a8c12db3c"
$requiredBranch = "feature/phantom-world"
$goal014Subject = "feat(phantoms): add npc commerce supply and travel loop"
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
		-or $local -eq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java" `
		-or $local -eq "build.xml" `
		-or $local.StartsWith("test/java/org/l2jmobius/tests/phantoms/PhantomCommerce", [StringComparison]::Ordinal) `
		-or $local -eq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java" `
		-or $local -eq "tools/phantoms/verify-task-014.ps1" `
		-or $local -eq "tools/phantoms/verify-task-014a.ps1" `
		-or $local -eq "docs/PHANTOM_BOTS_ROADMAP.md" `
		-or $local -eq "docs/phantoms/PHANTOM_CODEX_EFFICIENCY_STANDARD.md" `
		-or $local -eq "docs/phantoms/architecture/COMMERCE_SUPPLY_TRAVEL_CONTRACT.md" `
		-or $local -eq "docs/phantoms/reviews/013b-durable-class-skill-learning-review.md" `
		-or $local -eq "docs/phantoms/reviews/014-npc-commerce-supply-travel-loop-review.md" `
		-or $local -eq "docs/phantoms/reports/014-npc-commerce-supply-travel-loop.md" `
		-or $local -eq "docs/phantoms/reports/014a-commerce-ownership-integration-hardening.md" `
		-or $local.StartsWith("docs/phantoms/tasks/014-npc-commerce-supply-travel-loop/", [StringComparison]::Ordinal) `
		-or $local.StartsWith("docs/phantoms/tasks/014a-commerce-ownership-integration-hardening/", [StringComparison]::Ordinal)
}

$branch = (& git -C $repositoryRoot branch --show-current).Trim()
Assert-True ($branch -eq $requiredBranch) "Wrong branch: $branch"

$head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$goal014Parents = @(((& git -C $repositoryRoot show -s --format=%P $goal014Commit).Trim() -split " ") | Where-Object { $_ })
Assert-True ($goal014Parents.Count -eq 1) "Goal 014 commit is a merge commit."
Assert-True ($goal014Parents[0] -eq $goal014Parent) "Goal 014 commit parent is not exact."
Assert-True ((& git -C $repositoryRoot show -s --format=%s $goal014Commit).Trim() -eq $goal014Subject) "Goal 014 commit subject is not exact."
& git -C $repositoryRoot merge-base --is-ancestor $goal014Commit $head
Assert-True ($LASTEXITCODE -eq 0) "Goal 014 commit is not an ancestor of HEAD."
& git -C $repositoryRoot merge-base --is-ancestor $acceptedCompletion $head
Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 014A completion is not an ancestor of HEAD."
$graphMode = "accepted-completion-ancestor"

$changedPaths = @(& git -c core.autocrlf=false -C $repositoryRoot diff --name-only "$goal014Parent..$acceptedCompletion")
$changedPaths = @($changedPaths | Where-Object { $_ } | Sort-Object -Unique)
Assert-True ($changedPaths.Count -gt 0) "No Goal 014 artifacts found."
foreach ($path in $changedPaths)
{
	Assert-True (Is-AllowedPath $path) "Out-of-scope path: $path"
}

$requiredFiles = @(
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceCatalog.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceCatalogLoader.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceReceipt.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceDecision.java",
	"java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceReceiptStore.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCommerceSuite.java",
	"tools/phantoms/verify-task-014.ps1",
	"docs/phantoms/architecture/COMMERCE_SUPPLY_TRAVEL_CONTRACT.md",
	"docs/phantoms/reviews/013b-durable-class-skill-learning-review.md",
	"docs/phantoms/reports/014-npc-commerce-supply-travel-loop.md"
)
foreach ($local in $requiredFiles)
{
	Assert-True (Test-Path -LiteralPath (Join-Path $moduleRoot $local) -PathType Leaf) "Missing required file: $local"
}

$productionFiles = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce") -Filter "*.java")
$productionText = ($productionFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
Assert-True (-not [regex]::IsMatch($productionText, "MultiSellChoose|RequestMultiSell|multisell\s*\.\s*(execute|run|apply)", "IgnoreCase")) "Multisell execution reference found."
Assert-True (-not $productionText.Contains("progression.learn_skill")) "Forbidden progression.learn_skill production reference found."
Assert-True (-not [regex]::IsMatch($productionText, "\b(new\s+Thread|ThreadPool|ScheduledFuture|ExecutorService|scheduleAtFixedRate|scheduleWithFixedDelay)\b")) "Commerce worker/scheduler reference found."

$servicePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/commerce/PhantomCommerceService.java"
$serviceLines = Get-Content -LiteralPath $servicePath
$committingLine = -1
$firstEffectLine = -1
for ($index = 0; $index -lt $serviceLines.Count; $index++)
{
	if (($committingLine -lt 0) -and $serviceLines[$index].Contains("withState(State.COMMITTING)"))
	{
		$committingLine = $index
	}
	if (($firstEffectLine -lt 0) -and $serviceLines[$index].Contains(".applyFirst("))
	{
		$firstEffectLine = $index
	}
}
Assert-True ($committingLine -ge 0 -and $firstEffectLine -gt $committingLine) "Receipt ordering is not statically visible."

$launcherText = Get-Content -LiteralPath (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -Raw
$buildText = Get-Content -LiteralPath (Join-Path $moduleRoot "build.xml") -Raw
foreach ($route in @("commerce-catalog", "commerce-supply", "commerce-quote", "commerce-receipt", "commerce-decision", "commerce-server-integration", "commerce-performance"))
{
	Assert-True ($launcherText.Contains($route) -and $buildText.Contains($route)) "Missing focused route: $route"
}

$contractText = Read-Utf8Strict (Join-Path $moduleRoot "docs/phantoms/architecture/COMMERCE_SUPPLY_TRAVEL_CONTRACT.md")
Assert-True ($contractText.Contains("cross-server ACID") -and $contractText.Contains("INCONSISTENT")) "Conservative non-atomicity statement is missing."
$reportPath = Join-Path $moduleRoot "docs/phantoms/reports/014-npc-commerce-supply-travel-loop.md"
Assert-True ((Get-Content -LiteralPath $reportPath).Count -le 180) "Goal 014 report exceeds 180 lines."
Assert-True (Test-Path -LiteralPath (Join-Path $moduleRoot "dist/libs/GameServer.jar") -PathType Leaf) "GameServer.jar is missing."

$textExtensions = @(".java", ".xml", ".md", ".ps1")
foreach ($path in $changedPaths)
{
	$fullPath = Join-Path $repositoryRoot $path
	if ((Test-Path -LiteralPath $fullPath -PathType Leaf) -and ($textExtensions -contains [IO.Path]::GetExtension($fullPath)))
	{
		$text = Read-Utf8Strict $fullPath
		Assert-True (-not $text.Contains([string][char]0xFFFD)) "Unicode replacement marker found in $path"
		Assert-True (-not [regex]::IsMatch($text, "\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};")) "Escaped Cyrillic found in $path"
	}
}

Write-Output "TASK014_VERIFIER_OK"
Write-Output "branch=$branch"
Write-Output "graph=$graphMode"
Write-Output "goal014Commit=$goal014Commit"
Write-Output "goal014Parent=$goal014Parent"
Write-Output "scopePaths=$($changedPaths.Count)"
Write-Output "focusedRoutes=7"
Write-Output "receiptOrdering=COMMITTING_BEFORE_FIRST_EFFECT"
Write-Output "multisell=QUERY_ONLY"
Write-Output "workers=0"
Write-Output "utf8=STRICT"
Write-Output "jar=present"
