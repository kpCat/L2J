param([switch] $WorkingTree)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RequiredParent = "563752f6844076fdbaeb3be7c5cae979c757960a"
$RequiredSubject = "fix(phantoms): close rift route and consent gaps"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "23002312"

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
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 023B artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 023B artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Read-AddedText([string] $relativePath)
{
	$repositoryPath = ":(top)" + $script:ModulePrefix + $relativePath
	if ($script:Mode -eq "working") { $lines = & git -c core.safecrlf=false diff --unified=0 $RequiredParent -- $repositoryPath }
	else { $lines = & git -c core.safecrlf=false diff --unified=0 $RequiredParent $script:TargetCommit -- $repositoryPath }
	Assert-True ($LASTEXITCODE -eq 0) "Could not inspect Goal 023B added lines."
	return (($lines | Where-Object { $_.StartsWith("+") -and !$_.StartsWith("+++") }) -join [Environment]::NewLine)
}

function Contains-All([string] $text, [string[]] $tokens, [string] $name)
{
	foreach ($token in $tokens) { Assert-True ($text.Contains($token)) "$name is missing required token: $token" }
}

function Is-ProductionData([string] $path)
{
	return ($path -match '^java/org/l2jmobius/gameserver/') -or ($path -match '^dist/game/(?:data|config)/')
}

function Is-Allowed([string] $path)
{
	if ($path -match '^docs/phantoms/tasks/023b-rift-route-consent-closure/') { return $true }
	return $path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
		'build.xml',
		'docs/PHANTOM_BOTS_ROADMAP.md',
		'docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md',
		'docs/phantoms/reports/023b-rift-route-consent-closure.md',
		'docs/phantoms/reviews/023a-independent-review.md',
		'docs/phantoms/reviews/023b-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java',
		'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java',
		'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
		'tools/phantoms/verify-task-023a.ps1',
		'tools/phantoms/verify-task-023b.ps1'
	)
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 023B must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 023B requires the exact Goal 023A parent."
		$script:Mode = "working"
		$script:TargetCommit = $head
	}
	else
	{
		& git merge-base --is-ancestor $RequiredParent $head
		Assert-True ($LASTEXITCODE -eq 0) "Goal 023B parent is not an ancestor of HEAD."
		$lineage = @(Git-Lines @("rev-list", "--first-parent", "--reverse", "$RequiredParent..$head"))
		Assert-True ($lineage.Count -ge 1) "Goal 023B completion commit is absent."
		$script:TargetCommit = $lineage[0]
		$script:Mode = "historical"
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredParent) "Goal 023B is not the ordinary direct child."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 023B commit subject changed."
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--"); Add-Untracked $changed }
	else { Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--") }
	$paths = @($changed | Sort-Object)
	Assert-True ($paths.Count -gt 0) "Goal 023B scope is empty."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 023B path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|model/groups/Party\.java$|PartyInvitationService\.java$|DimensionalRiftManager\.java$|tasks/02[4-9]|tasks/0[3-9][0-9]|\.sql$|\.l2j$') "Forbidden Goal 023B path: $path"
		Assert-True ($path -notmatch '^\.phantom-local/|^\.idea/|\.class$|\.jar$|\.zip$') "Generated/binary Goal 023B path: $path"
	}
	$changedProductionData = @($paths | Where-Object { Is-ProductionData $_ })
	Assert-True (@($paths | Where-Object { $_ -match '\.sql$' }).Count -eq 0) "Goal 023B changed SQL."

	$routeOwner = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java'
	Contains-All $routeOwner @('enum ActivityStatus', 'PLANNING', 'MOVING', 'REGROUPING', 'ARRIVED', 'FAILED', 'record RouteActivity', 'plannerOwned', 'routeOwned', 'movementOwned', '_pending.containsKey(groupId)', '_routeByGroup.containsKey(groupId)') 'Goal017 route activity'
	$coordinator = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java'
	Contains-All $coordinator @('observeRouteActivity', 'reconcileTerminalRoute', 'routeActivity.nonTerminal()', 'routeActivity.terminal()', 'party.binding.route_active', 'party.binding.route_terminal_reconciled') 'Route-aware content binding'
	$port = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java'
	Contains-All $port @('_coordinator.observeRouteActivity', '_coordinator.reconcileTerminalRoute', 'case PLANNING, MOVING, REGROUPING', 'case ARRIVED', 'case FAILED') 'Production Rift route port'
	$service = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java'
	Contains-All $service @('evaluateManagedInvitation', 'exactInvitation', 'samePendingInvitationBinding', '_party.observeInvite', '_backend.candidateFacts', 'candidate.inAnotherParty', 'candidate.distanceSquared', '_backend.relationship(context.inviteeProfileId()', 'ManagedInvitationDecision.REFUSE', 'ManagedInvitationDecision.DEFER') 'Production managed Rift consent'
	Assert-True ($service -notmatch 'DimensionalRiftManager|destroyItem|teleToLocation|new\s+DimensionalRift|World\.getPlayers\(\)|GameClient') "Rift service gained forbidden mutation/global/client dependency."
	$observeRoute = [regex]::Match($service, 'private AdvanceResult observeRoute\(StoredPreparation stored\)[\s\S]*?private AdvanceResult declareReady').Value
	Assert-True ($observeRoute.Contains('_party.observeRoute')) "Rift observeRoute no longer observes Goal017 route."
	Assert-True (-not $observeRoute.Contains('readiness.status() == Status.READY_TO_ENTER')) "Rift observeRoute still bypasses route ownership when readiness becomes READY."

	$integration = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java'
	Contains-All $integration @('_service::evaluateManagedInvitation', 'Production Rift provider did not reach canonical ACCEPT membership', 'stale-capability-defers', 'negative-relationship-refuses', 'unavailable-evidence-defers-until-expiry', 'InviteStatus.REFUSED', 'InviteStatus.EXPIRED', 'HeadlessPlayerOutboundSession') 'Canonical managed-consent proof'
	Assert-True (-not $integration.Contains('ignored -> PhantomPartyCoordinator.ManagedInvitationDecision.ACCEPT')) "Canonical managed-consent proof still uses ignored->ACCEPT."
	Assert-True ($integration -notmatch 'new\s+GameClient') "Acceptance integration fabricated GameClient."
	$routeTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java'
	Contains-All $routeTests @('ROUTE_CLOSURE', 'planner-pending-binding-preserves-ownership-and-submits-once', 'moving-and-regrouping-binding-preserves-live-route', 'arrived-terminal-cleanup-precedes-stable-binding', 'failed-terminal-cleanup-precedes-stable-binding', 'rift-route-observed-once-and-ready-only-after-clean-binding') 'Dynamic route closure proof'
	$launcher = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java'
	Assert-True ($launcher.Contains('rift023b-route-closure')) "Goal 023B route launcher mode is absent."

	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('phantom.goal023b.seed" value="23002312"', 'phantom-rift-goal023b-route-closure-test', 'phantom-rift-goal023b-managed-consent-test', 'phantom-rift-goal023b-focused-test', 'phantom-rift-goal023b-affected-test', 'phantom-rift-goal023b-test', 'phantom-static-verify-023b') 'Goal 023B Ant routes'
	$goal023 = [regex]::Match($build, '<target name="phantom-static-verify-023"[\s\S]*?</target>').Value
	$goal023a = [regex]::Match($build, '<target name="phantom-static-verify-023a"[\s\S]*?</target>').Value
	Assert-True (-not $goal023.Contains('-WorkingTree')) "Historical Goal 023 verifier uses -WorkingTree."
	Assert-True (-not $goal023a.Contains('-WorkingTree')) "Historical Goal 023A verifier uses -WorkingTree."
	$verifier023a = Read-TargetUtf8 'tools/phantoms/verify-task-023a.ps1'
	Contains-All $verifier023a @('"rev-list", "--first-parent", "--reverse"', '$script:Mode = "historical"', 'merge-base --is-ancestor $script:TargetCommit $remote') 'Descendant-compatible Goal 023A verifier'

	$review023a = Read-TargetUtf8 'docs/phantoms/reviews/023a-independent-review.md'
	Contains-All $review023a @('CHANGES_REQUIRED', 'R023B-01', 'R023B-02', 'Goal 023 baseline', 'Goal 024+: NOT_STARTED') 'Goal 023A review disposition'
	$review023b = Read-TargetUtf8 'docs/phantoms/reviews/023b-independent-review.md'
	Contains-All $review023b @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'R023B-01', 'R023B-02', 'Goal 024+: NOT_STARTED', 'self-accept') 'Goal 023B review handoff'
	$report = Read-TargetUtf8 'docs/phantoms/reports/023b-rift-route-consent-closure.md'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, $RequiredSeed, 'R023B-01', 'R023B-02', 'PowerShell 5.1', 'PowerShell 7', 'byte-identical', 'Goal 024') 'Goal 023B report'
	$contract = Read-TargetUtf8 'docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md'
	Contains-All $contract @('RouteActivity', 'planner-pending', 'PLANNING', 'REGROUPING', 'evaluateManagedInvitation') 'Rift recruitment contract closure'
	$master = Read-TargetUtf8 'PHANTOM_DEVELOPMENT_MASTER_PLAN.md'
	$roadmap = Read-TargetUtf8 'docs/PHANTOM_BOTS_ROADMAP.md'
	Contains-All $master @('CORRECTIVE_023B_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 024') 'Master plan corrective marker'
	Contains-All $roadmap @('CORRECTIVE_023B_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 024') 'Roadmap corrective marker'

	$mojibakePairs = @(@(0x0420,0x045F),@(0x0420,0x045C),@(0x0420,0x045B),@(0x0420,0x2022),@(0x0420,0x040E),@(0x0420,0x203A),@(0x0420,0x00A4),@(0x0420,0x045A),@(0x0420,0x0408),@(0x0420,0x2122),@(0x0420,0x0491),@(0x0420,0x00B5),@(0x0420,0x00B0),@(0x0420,0x00BB),@(0x0420,0x2026),@(0x0421,0x040F),@(0x0421,0x20AC),@(0x0421,0x0402),@(0x0421,0x2039),@(0x0421,0x040A),@(0x0421,0x201A),@(0x0421,0x0453),@(0x0421,0x040B),@(0x0421,0x2026),@(0x0421,0x2020))
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char] $_[0]) + ([string][char] $_[1])) }) -join '|'
	$replacement = [string][char] 0xFFFD
	$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $paths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1)$') -or ($path -eq 'build.xml'))
		{
			$text = Read-TargetUtf8 $path
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacement)) "Mojibake marker found: $path"
			if ($path -notmatch 'verify-task-023b\.ps1$') { Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found: $path" }
		}
	}
	foreach ($path in @($changedProductionData | Where-Object { $_ -match '\.java$' }))
	{
		$added = Read-AddedText $path
		Assert-True ($added -notmatch 'new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture|\bFuture<|ThreadPool\.|GameClient|World\.getInstance\(\)\.getPlayers\(\)|sendPacket\s*\(') "Forbidden worker/client/global-scan API found in Goal 023B additions: $path"
	}

	if ($script:Mode -eq "working")
	{
		& git -c core.safecrlf=false diff --check $RequiredParent --
		Assert-True ($LASTEXITCODE -eq 0) "Working diff check failed."
	}
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote does not contain Goal 023B."
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed diff check failed."
	}
	$classEntries = @(
		'org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.class',
		'org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.class',
		'org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.class',
		'org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.class'
	)
	if ($script:Mode -eq "working")
	{
		foreach ($entry in $classEntries) { Assert-True (Test-Path -LiteralPath (Join-Path (Split-Path $script:ModuleRoot -Parent) ("build/bin/" + $entry)) -PathType Leaf) "Compiled classes lack: $entry" }
	}
	else
	{
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in $classEntries) { Assert-True ($jarEntries -contains $entry) "JAR lacks: $entry" }
	}

	Write-Output 'TASK023B_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
	Write-Output "scope=$($paths.Count)"
	Write-Output "changed_production_data=$($changedProductionData.Count)"
}
finally { Pop-Location }