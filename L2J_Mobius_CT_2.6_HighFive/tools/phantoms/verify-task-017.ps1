param(
	[string] $ExpectedParent = "0015a5ffd0c10a99514732ef52b969a39ac62eb7"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

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

function Read-Utf8Strict([string] $relativePath)
{
	$path = Join-Path $script:Root $relativePath
	Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required file is absent: $relativePath"
	$bytes = [System.IO.File]::ReadAllBytes($path)
	$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
	return $utf8.GetString($bytes)
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $Root
try
{
	$branch = (Git-Lines @("branch", "--show-current") | Select-Object -First 1)
	Assert-True ($branch -eq "feature/phantom-world") "Goal 017 must remain on feature/phantom-world."

	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($head -ne $ExpectedParent)
	{
		$parent = (Git-Lines @("rev-parse", "HEAD^") | Select-Object -First 1)
		Assert-True ($parent -eq $ExpectedParent) "HEAD is not the single terminal-verification child above required parent."
		$subject = (Git-Lines @("show", "-s", "--format=%s", "HEAD") | Select-Object -First 1)
		Assert-True ($subject -eq "fix(phantoms): finalize party terminal verification") "Goal 017 terminal-verification commit subject is wrong."
	}

	$modulePrefix = (Split-Path $Root -Leaf) + "/"
	$trackedChanges = @(Git-Lines @("diff", "--name-only", $ExpectedParent, "--") | ForEach-Object { $_ -replace ("^" + [regex]::Escape($modulePrefix)), "" })
	$untrackedChanges = @(Git-Lines @("ls-files", "--others", "--exclude-standard") | ForEach-Object { $_ -replace ("^" + [regex]::Escape($modulePrefix)), "" })
	$changedPaths = @($trackedChanges + $untrackedChanges | Sort-Object -Unique)
	Assert-True ($changedPaths.Count -le 10) "Goal 017 terminal verification exceeds the 10-file scope."

	$allowed = @(
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java",
		"java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
		"dist/game/config/Custom/PhantomPlayers.ini",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
		"tools/phantoms/verify-task-017.ps1",
		"docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md",
		"docs/phantoms/reports/017-party-coordination-kernel.md",
		"docs/phantoms/reviews/017-party-terminal-verification-review.md"
	)
	foreach ($path in $changedPaths)
	{
		Assert-True ($allowed -contains $path) "Out-of-scope Goal 017 terminal-verification path: $path"
		Assert-True ($path -notmatch "(^|/)Player\.java$|(^|/)Party\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)") "Forbidden Goal 017 path: $path"
	}
	foreach ($required in @(
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
		"dist/game/config/Custom/PhantomPlayers.ini",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
		"tools/phantoms/verify-task-017.ps1",
		"docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md",
		"docs/phantoms/reports/017-party-coordination-kernel.md",
		"docs/phantoms/reviews/017-party-terminal-verification-review.md"
	))
	{
		Assert-True ($changedPaths -contains $required) "Required terminal-verification artifact is absent: $required"
	}

	$production = @($changedPaths | Where-Object { ($_ -match "^java/") -or ($_ -eq "dist/game/config/Custom/PhantomPlayers.ini") })
	Assert-True ($production.Count -le 4) "Goal 017 terminal verification exceeds four production files."
	$newProduction = @(Git-Lines @("diff", "--name-only", "--diff-filter=A", $ExpectedParent, "--", "java", "dist/game/config/Custom/PhantomPlayers.ini"))
	Assert-True ($newProduction.Count -eq 0) "Goal 017 terminal verification adds a production file."

	$coordinator = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
	Assert-True ($coordinator -match "operationBudget < 10" -and $coordinator -match "between 10 and 10000") "Coordinator does not enforce the safe party pulse minimum."
	Assert-True ($coordinator -match "existing\.state\(\)\.status\(\) != StateStatus\.SOLO" -and $coordinator -match "expectedRowVersion = existing\.rowVersion\(\)" -and $coordinator -match "groupGeneration = existing\.state\(\)\.groupGeneration\(\) \+ 1") "SOLO form reuse is not an optimistic new generation."
	Assert-True ($coordinator -match "abortFormation" -and $coordinator -match "PhantomGoalStatus\.FAILED" -and $coordinator -match "removeGroup\(before\.groupId\(\)\)") "Terminal formation cleanup is incomplete."
	Assert-True (($coordinator -split "goal\.status\(\) != PhantomGoalStatus\.ACTIVE").Count -ge 2) "Exact command goal is not ACTIVE-gated."
	Assert-True ($coordinator -match "stored\.get\(\)\.goal\(\)\.status\(\) == PhantomGoalStatus\.ACTIVE") "Exact form goal is not ACTIVE-gated."
	Assert-True ($coordinator -match "int examined = 1 \+ groupClaims\.size\(\)" -and $coordinator -notmatch "examined \+ expected\.size\(\) \+ 1") "Nine-claim reconcile boundary is still double-counted."

	$config = Read-Utf8Strict "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
	$ini = Read-Utf8Strict "dist/game/config/Custom/PhantomPlayers.ini"
	Assert-True ($config -match 'PhantomPartyOperationsPerPulse"\), 10, 10000' -and $config -match "partyOperationsPerPulse < 10") "Config parser or Settings validation accepts a party budget below 10."
	Assert-True ($config -match "DEFAULT_PARTY_OPERATIONS_PER_PULSE = 64") "Party budget default changed."
	Assert-True ($ini -match "Valid range: 10\.\.10000" -and $ini -match "PhantomPartyOperationsPerPulse = 64") "Canonical party budget comment/default is stale."

	$partyTests = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java"
	Assert-True ($partyTests -match "refusal-makes-form-goal-terminal-and-reusable" -and $partyTests -match "timeout-makes-form-goal-terminal-and-reusable") "Refusal/timeout terminal tests are absent."
	Assert-True ($partyTests -match "nine-member-budget-boundary-makes-progress" -and $partyTests -match "Nine-operation party pulse budget was accepted") "Party budget boundary tests are absent."
	Assert-True ($partyTests -match "Stale terminal callback aborted the new operation" -and $partyTests -match "Stale form decision emitted an automatic retry invitation") "Stale callback/decision retry coverage is absent."

	$populationTests = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomPopulationSuite.java"
	$methodStart = $populationTests.IndexOf("private void testServerIntegration")
	$methodEnd = $populationTests.IndexOf("private ManagedSnapshot advanceToCharacterPresent", $methodStart)
	Assert-True (($methodStart -ge 0) -and ($methodEnd -gt $methodStart)) "Population server integration method is absent."
	$populationMethod = $populationTests.Substring($methodStart, $methodEnd - $methodStart)
	Assert-True ($populationMethod -match "State\.ACTIVE" -and $populationMethod -match "filter\(active ->" -and $populationMethod -match "active\.playerRetained\(\)" -and $populationMethod -match "active\.worldPresent\(\)") "Population test still waits for non-terminal materialization presence."
	Assert-True ($populationMethod -notmatch "Thread\.sleep") "Population race fix uses sleep."

	$configTests = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java"
	Assert-True ($configTests -match "config-party-budget-canonical-boundary" -and $configTests -match "PhantomPartyOperationsPerPulse = 9" -and $configTests -match "PhantomPartyOperationsPerPulse = 10") "Config boundary tests are absent."

	$contract = Read-Utf8Strict "docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md"
	$report = Read-Utf8Strict "docs/phantoms/reports/017-party-coordination-kernel.md"
	$review = Read-Utf8Strict "docs/phantoms/reviews/017-party-terminal-verification-review.md"
	Assert-True ($contract -match "reusable SOLO" -and $contract -match "10\.\.10000" -and $contract -match "FAILED") "Party architecture contract is stale."
	Assert-True ($report -match $ExpectedParent -and $report -match "fix\(phantoms\): finalize party terminal verification" -and $report -match "250") "Goal 017 terminal report evidence is incomplete."
	Assert-True ($review -match $ExpectedParent -and $review -match "ACCEPTED" -and $review -match "test-only") "Independent review record is incomplete."

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B),
		@(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A),
		@(0x0420, 0x00A4), @(0x0420, 0x045A), @(0x0420, 0x0408),
		@(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5),
		@(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026),
		@(0x0421, 0x040F), @(0x0421, 0x20AC), @(0x0421, 0x0402),
		@(0x0421, 0x2039), @(0x0421, 0x040A), @(0x0421, 0x201A),
		@(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026),
		@(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char]$_[0]) + ([string][char]$_[1])) }) -join "|"
	$replacementCharacter = [string][char]0xFFFD
	$escapedCyrillic = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $changedPaths)
	{
		$text = Read-Utf8Strict $path
		Assert-True (($text -notmatch $mojibake) -and -not $text.Contains($replacementCharacter)) "Mojibake marker found in changed file: $path"
		Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in changed file: $path"
	}

	& git diff --check $ExpectedParent --
	Assert-True ($LASTEXITCODE -eq 0) "git diff --check failed."
	Write-Output "TASK017_VERIFIER_OK"
	Write-Output "parent=$ExpectedParent"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
}
finally
{
	Pop-Location
}
