param([switch] $WorkingTree)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RequiredParent = "2603776c6996007b147f93e4c7e79f145ceb8a89"
$RequiredSubject = "fix(phantoms): harden farming agreement lifecycle"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "24002402"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition) { throw $message }
}

function Git-Lines([string[]] $arguments)
{
	$result = & git -c core.safecrlf=false @arguments
	Assert-True ($LASTEXITCODE -eq 0) "Git command failed."
	return @($result | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function To-ModulePath([string] $path)
{
	$normalized = $path.Trim().Trim('"').Replace("\", "/")
	if ($normalized.StartsWith($script:ModulePrefix, [StringComparison]::Ordinal)) { return $normalized.Substring($script:ModulePrefix.Length) }
	return $normalized
}

function Add-Paths([Collections.Generic.HashSet[string]] $set, [string[]] $arguments)
{
	foreach ($line in Git-Lines $arguments) { [void] $set.Add((To-ModulePath $line)) }
}

function Add-Untracked([Collections.Generic.HashSet[string]] $set)
{
	foreach ($line in Git-Lines @("ls-files", "--others", "--exclude-standard"))
	{
		$path = To-ModulePath $line
		if (Test-Path -LiteralPath (Join-Path $script:ModuleRoot $path) -PathType Leaf) { [void] $set.Add($path) }
	}
}

function Read-CommitBytes([string] $commit, [string] $relativePath)
{
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = New-Object Diagnostics.ProcessStartInfo
	$start.FileName = "git"
	$start.Arguments = "show " + $commit + ":" + $repositoryPath
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = New-Object IO.MemoryStream
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 024A artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 024A artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Contains-All([string] $text, [string[]] $tokens, [string] $name)
{
	foreach ($token in $tokens) { Assert-True ($text.Contains($token)) "$name is missing required token: $token" }
}

function Is-Allowed([string] $path)
{
	if ($path -match '^docs/phantoms/tasks/024a-farming-agreement-lifecycle-corrections/') { return $true }
	if ($path -match '^java/org/l2jmobius/gameserver/phantoms/farming/') { return $true }
	return $path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
		'build.xml',
		'docs/PHANTOM_BOTS_ROADMAP.md',
		'docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md',
		'docs/phantoms/reports/024a-farming-agreement-lifecycle-corrections.md',
		'docs/phantoms/reviews/024-independent-review.md',
		'docs/phantoms/reviews/024a-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
		'tools/phantoms/verify-task-024a.ps1'
	)
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 024A must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 024A requires the exact Goal 024 parent."
		$script:Mode = "working"
		$script:TargetCommit = $head
	}
	else
	{
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $head) | Select-Object -First 1) -eq $RequiredParent) "Goal 024A is not the ordinary direct child."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $head) | Select-Object -First 1) -eq $RequiredSubject) "Goal 024A commit subject changed."
		$script:Mode = "committed"
		$script:TargetCommit = $head
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--"); Add-Untracked $changed }
	else { Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--") }
	$paths = @($changed | Sort-Object)
	Assert-True ($paths.Count -gt 0) "Goal 024A scope is empty."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 024A path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|model/groups/Party\.java$|tasks/02[5-9]|tasks/0[3-9][0-9]|\.sql$|\.l2j$') "Forbidden Goal 024A path: $path"
		Assert-True ($path -notmatch '^\.phantom-local/|^\.idea/|\.class$|\.jar$|\.zip$') "Generated/binary Goal 024A path: $path"
	}

	$model = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingModel.java'
	Contains-All $model @('SCHEMA_VERSION = 2', 'CausalPerceptionReceipt', 'lowerAuthorityHash', 'higherAuthorityHash', 'socialDeliveryMask', 'sameIdentity', 'exactPair') 'Goal 024A model'
	$codec = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStateCodec.java'
	Contains-All $codec @('LEGACY_MAGIC', 'schemaVersion != 1', 'CausalPerceptionReceipt.legacy', 'farming.legacy.authority') 'Goal 024A schema recovery'
	$store = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStore.java'
	Contains-All $store @('(component.componentSchemaVersion() != 1)', 'PhantomFarmingModel.SCHEMA_VERSION') 'Goal 024A component store'
	$service = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java'
	Contains-All $service @('ConflictObservation', 'capturePerception', 'validPerception', 'persistedCounterpart', 'reconcilePersisted', 'reconcileAgreementLocked', 'liveAuthority(ownSnapshot, ownResource).equals(ownAuthority)', 'liveAuthority(lower, lowerResource).equals(receipt.lowerAuthorityHash())', 'retryAgreementSocial', 'AFTER_FIRST_TERMINAL', 'BEFORE_TERMINAL_SOCIAL', 'ConflictLifecycle.COMPLETED', 'ConflictLifecycle.RELEASED') 'Goal 024A lifecycle service'
	Assert-True (-not $service.Contains('observeAgreementOutcome')) "Manual agreement outcome API remains in production."
	Assert-True ($service -notmatch 'World\.getPlayers|World\.getInstance\(\)\.getPlayers|listProfiles\s*\(|sendPacket\s*\(|\.switchSource\s*\(|GameClient|doAttack|forceAttack|startCombat|new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture|\bFuture<') "Goal 024A farming service gained forbidden scan/client/acquisition/combat/worker behavior."

	$acquisition = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java'
	Contains-All $acquisition @('public ConflictObservation conflictObservation', 'ConflictLifecycle.CURRENT', 'ConflictLifecycle.COMPLETED', 'ConflictLifecycle.RELEASED', 'public OperationResult switchSource') 'Goal 021 lifecycle seam'
	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java'
	Contains-All $tests @('24002402L', 'LIFECYCLE_CORRECTIONS', 'RESTART_CORRECTIONS', 'Monotonic progress invalidated final SHARE', 'Holder progress invalidated final WAIT', 'OFFER drift reused', 'RESPONSE drift reused', 'one-hop visibility', 'Loser-first restart', 'legacy-v1-is-untrusted', 'terminal-bilateral-fault-matrix', 'agreement.broken') 'Goal 024A lifecycle tests'
	$manorFixture = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java'
	Contains-All $manorFixture @('enableExactItemSkill', 'Canonical sow cleanup did not quiesce before the next bounded attempt.', 'Canonical seed item lost its exact Sowing skill.', 'Canonical Harvester item lost its exact skill.', '_player.enableSkill(skills[0].getSkill());', 'resetActor(false);', 'relocateToCombatPoint();', 'Recovery fixture Combat start failed.') 'Goal 024A bounded historical acquisition fixtures'
	$acquisitionTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java'
	Contains-All $acquisitionTests @('FARMING_LIFECYCLE', 'real-goal021-switch-fulfils-old-move-exactly-once', '.switchSource(', 'switchCount()', 'AgreementStatus.FULFILLED') 'Goal 024A real Goal 021 integration'
	$launcher = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java'
	Contains-All $launcher @('farming-lifecycle-corrections', 'farming-restart-corrections', 'farming-acquisition-lifecycle') 'Goal 024A launcher routes'
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('phantom.goal024a.seed" value="24002402"', 'phantom-farming-goal024a-lifecycle-test', 'phantom-farming-goal024a-restart-test', 'phantom-farming-goal024a-acquisition-integration-test', 'phantom-farming-goal024a-focused-test', 'phantom-farming-goal024a-affected-test', 'phantom-static-verify-024a', 'phantom-farming-goal024a-test', 'phantom-farming-goal024-test') 'Goal 024A Ant routes'
	$historical024 = [regex]::Match($build, '<target name="phantom-static-verify-024"[\s\S]*?</target>').Value
	Assert-True (-not $historical024.Contains('-WorkingTree')) "Historical Goal 024 verifier is not descendant-compatible."

	$review024 = Read-TargetUtf8 'docs/phantoms/reviews/024-independent-review.md'
	Contains-All $review024 @('Goal 024: CHANGES_REQUIRED', 'R024A-01: OPEN', 'R024A-02: OPEN', 'R024A-03: OPEN', 'Goal 025+: NOT_STARTED') 'Goal 024 corrective entry status'
	$handoff = Read-TargetUtf8 'docs/phantoms/reviews/024a-independent-review.md'
	Contains-All $handoff @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSeed, 'Goal 025+: NOT_STARTED') 'Goal 024A independent review handoff'
	Assert-True (-not $handoff.Contains('Goal 024A: ACCEPT')) "Goal 024A handoff self-accepted the corrective goal."
	$report = Read-TargetUtf8 'docs/phantoms/reports/024a-farming-agreement-lifecycle-corrections.md'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, $RequiredSeed, 'PowerShell 5.1', 'PowerShell 7', 'byte-identical', 'production DB', 'Goal 025') 'Goal 024A report'
	$contract = Read-TargetUtf8 'docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md'
	Contains-All $contract @('CausalPerceptionReceipt', 'schema v2', 'FULFILLED', 'EXPIRED', 'STALE', 'Goal 025') 'Corrected farming contract'
	$master = Read-TargetUtf8 'PHANTOM_DEVELOPMENT_MASTER_PLAN.md'
	$roadmap = Read-TargetUtf8 'docs/PHANTOM_BOTS_ROADMAP.md'
	Contains-All $master @('Goal 024A', 'IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 025') 'Master Goal 024A status'
	Contains-All $roadmap @('Goal 024A', 'IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 025') 'Roadmap Goal 024A status'

	$mojibakePairs = @(@(0x0420,0x045F),@(0x0420,0x045C),@(0x0420,0x045B),@(0x0420,0x2022),@(0x0420,0x040E),@(0x0420,0x203A),@(0x0420,0x00A4),@(0x0420,0x045A),@(0x0420,0x0408),@(0x0420,0x2122),@(0x0420,0x0491),@(0x0420,0x00B5),@(0x0420,0x00B0),@(0x0420,0x00BB),@(0x0420,0x2026),@(0x0421,0x040F),@(0x0421,0x20AC),@(0x0421,0x0402),@(0x0421,0x2039),@(0x0421,0x040A),@(0x0421,0x201A),@(0x0421,0x0453),@(0x0421,0x040B),@(0x0421,0x2026),@(0x0421,0x2020))
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char] $_[0]) + ([string][char] $_[1])) }) -join '|'
	$replacement = [string][char] 0xFFFD
	$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $paths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1|tsv)$') -or ($path -eq 'build.xml'))
		{
			$text = Read-TargetUtf8 $path
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacement)) "Mojibake marker found: $path"
			if ($path -notmatch 'verify-task-024a\.ps1$') { Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found: $path" }
		}
	}

	if ($script:Mode -eq "working")
	{
		& git -c core.safecrlf=false diff --check $RequiredParent --
		Assert-True ($LASTEXITCODE -eq 0) "Working Goal 024A diff check failed."
		foreach ($entry in @('org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.class', 'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStateCodec.class'))
		{
			Assert-True (Test-Path -LiteralPath (Join-Path (Split-Path $script:ModuleRoot -Parent) ("build/bin/" + $entry)) -PathType Leaf) "Compiled classes lack: $entry"
		}
	}
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		Assert-True ($remote -eq $script:TargetCommit) "Remote feature/phantom-world does not point at Goal 024A."
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed Goal 024A diff check failed."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @('org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.class', 'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStateCodec.class'))
		{
			Assert-True ($jarEntries -contains $entry) "JAR lacks: $entry"
		}
	}

	Write-Output 'TASK024A_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
}
finally
{
	Pop-Location
}
