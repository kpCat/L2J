param([switch] $WorkingTree)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RequiredParent = "840e159a989f6372da9c471c915413f1e4470daf"
$RequiredSubject = "fix(phantoms): harden rift recruitment integration"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "23002311"

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
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 023A artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 023A artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	if ($script:Mode -eq "working") { return [IO.File]::ReadAllText((Join-Path $script:ModuleRoot $relativePath), $encoding) }
	return $encoding.GetString((Read-CommitBytes $script:TargetCommit $relativePath))
}

function Target-Sha256([string] $relativePath)
{
	$sha = [Security.Cryptography.SHA256]::Create()
	try { return ([BitConverter]::ToString($sha.ComputeHash((Read-TargetBytes $relativePath)))).Replace("-", "").ToUpperInvariant() }
	finally { $sha.Dispose() }
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
	if ($path -match '^java/org/l2jmobius/gameserver/phantoms/rift/.*\.java$') { return $true }
	if ($path -match '^docs/phantoms/tasks/023a-rift-production-integration-corrections/') { return $true }
	if ($path -match '^test/java/org/l2jmobius/tests/phantoms/(?:PhantomRiftCorrectionsSuite|PhantomRiftSuite|PhantomPartyServerIntegrationSuite|PhantomTestLauncher)\.java$') { return $true }
	return $path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md', 'docs/PHANTOM_BOTS_ROADMAP.md', 'build.xml',
		'dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml',
		'docs/phantoms/reports/023a-rift-production-integration-corrections.md',
		'docs/phantoms/reviews/023-independent-review.md', 'docs/phantoms/reviews/023a-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java',
		'java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyDecision.java',
		'java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java',
		'tools/phantoms/verify-task-023a.ps1'
	)
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 023A must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 023A requires exact accepted Goal 023 parent."
		$script:Mode = "working"
		$script:TargetCommit = $head
	}
	else
	{
		$script:Mode = "committed"
		$script:TargetCommit = $head
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $head) | Select-Object -First 1) -eq $RequiredParent) "Goal 023A completion is not the ordinary direct child."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $head) | Select-Object -First 1) -eq $RequiredSubject) "Goal 023A commit subject changed."
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--"); Add-Untracked $changed }
	else { Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--") }
	$paths = @($changed | Sort-Object)
	Assert-True (($paths.Count -gt 0) -and ($paths.Count -le 32)) "Goal 023A total scope exceeds 32 files."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 023A path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|model/groups/Party\.java$|PartyInvitationService\.java$|DimensionalRiftManager\.java$|tasks/02[4-9]|tasks/0[3-9][0-9]') "Forbidden Goal 023A path: $path"
		Assert-True ($path -notmatch '^\.phantom-local/|^\.idea/|\.class$|\.jar$|\.zip$') "Generated/binary Goal 023A path: $path"
	}
	$changedProductionData = @($paths | Where-Object { Is-ProductionData $_ })
	$added = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $added @("diff", "--name-only", "--diff-filter=A", $RequiredParent, "--")
		Add-Untracked $added
	}
	else { Add-Paths $added @("diff", "--name-only", "--diff-filter=A", $RequiredParent, $script:TargetCommit, "--") }
	$newProductionData = @($paths | Where-Object { (Is-ProductionData $_) -and $added.Contains($_) })
	Assert-True ($changedProductionData.Count -le 16) "Changed production/data scope exceeds 16."
	Assert-True ($newProductionData.Count -le 6) "New production/data scope exceeds 6."
	Assert-True (@($paths | Where-Object { $_ -match '\.sql$' }).Count -eq 0) "Goal 023A changed SQL."

	$model = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftModel.java'
	Contains-All $model @('SCHEMA_VERSION = 2', 'ENSURE_PARTY_BINDING', 'PartyBindingReceipt', 'CandidateReceipt', 'PendingInvitationReceipt', 'canonicalExpiresAtGameTick', 'legacyUntrusted', 'MAX_CANDIDATES = 32') 'Rift preparation v2 model'
	$codec = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftStateCodec.java'
	Contains-All $codec @('LEGACY_SCHEMA_VERSION = 1', 'LEGACY_STAGES', 'legacyUntrusted', 'Unknown Rift preparation schema version') 'Backward-safe Rift codec'
	$coordinator = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java'
	Contains-All $coordinator @('bindContentGoal', 'observeContentBinding', 'ManagedInvitationDecision', 'ManagedInvitationPolicy', 'conversationOwnsAccept', 'explicitConsent', 'OperationKind.SUPPORT') 'Goal 017 binding and consent seams'
	Assert-True ($coordinator -notmatch 'new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture') "Goal 017 correction added a worker."
	$port = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java'
	Contains-All $port @('bindContentGoal', 'observeContentBinding', 'PartyInvitationService.getInstance().observe', 'expiresAtGameTick', 'case "party.invite.refused"', 'case "party.invite.expired"') 'Production Rift Party port'
	Assert-True ($port -notmatch 'contains\("timeout"\)') "Rift terminal typing uses substring inference."
	$service = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java'
	Contains-All $service @('case ENSURE_PARTY_BINDING', 'evaluateManagedInvitation', '_store.load(stored.profileId())', 'sameSources(current, readiness)', 'candidateFacts', 'candidateClaimAvailable', 'RIFT_INVITE_REQUEST', 'RIFT_INVITE_REFUSED', 'rift.ready.binding_changed') 'Corrected Rift orchestration'
	Assert-True ($service -notmatch 'DimensionalRiftManager|destroyItem|teleToLocation|new\s+DimensionalRift|World\.getPlayers\(\)|new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture|GameClient') "Rift service gained forbidden mutation/scan/worker/client dependency."
	$backend = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.java'
	Contains-All $backend @('getVisibleObjectsInRange', 'OwnerKind.PHANTOM ? 0 : 1', '.limit(limit)', '.map(this::reference)', 'party.invite.preference', 'RelationshipEvidence.neutral') 'Phantom-first bounded relationship discovery'
	Assert-True ($backend.IndexOf('.limit(limit)', [StringComparison]::Ordinal) -lt $backend.IndexOf('.map(this::reference)', [StringComparison]::Ordinal)) "Candidate identity resolution occurs before the cap."
	Assert-True ($backend -notmatch 'World\.getInstance\(\)\.getPlayers\(\)') "Global player scan found."
	$policy = Read-TargetUtf8 'dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml'
	Assert-True ($policy.Contains('inviteTimeoutMillis="15000"')) "Rift invite timeout is not canonical 15 seconds."
	$conversation = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java'
	Contains-All $conversation @('SemanticFactType.RIFT_INVITE_REQUEST', 'SemanticFactType.RIFT_INVITE_REFUSED', '"rift.invite_request"', '"rift.invite_refused"') 'Goal 020 Rift invitation facts'
	$system = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Contains-All $system @('commerceCatalog.catalog(), _socialService', 'installManagedInvitationPolicy(PhantomRiftService.GOAL_TYPE') 'Production composition'
	$metrics = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftMetrics.java'
	Contains-All $metrics @('_needsParty', '_needsRole', '_needsMemberReady', '_needsSupplies', '_needsTravel', '_inviteAccepted', '_inviteRefused', '_inviteExpired', '_candidateRejected', '_rosterStale', '_sourceStale', '_bindingConflicts') 'Bounded metrics'

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomRiftCorrectionsSuite.java'
	Contains-All $tests @('REQUIRED_SEED = 23002311L', 'PARTY_BINDING', 'MANAGED_CONSENT', 'PREINVITE_REVALIDATION', 'INVITATION_AUTHORITY', 'RESTART_MIGRATION', 'SEMANTIC_FACTS', 'CANDIDATE_ORDERING', 'ROUTE_BINDING', 'PERFORMANCE', '100000', '10000') 'Goal 023A focused tests'
	$integration = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java'
	Contains-All $integration @('new PhantomPartyCoordinator', 'new L2jPhantomRiftPartyPort', 'PartyInvitationService.getInstance()', 'HeadlessPlayerOutboundSession', '05-rift-production-port-canonical-managed-accept') 'Canonical acceptance integration'
	Assert-True ($integration -notmatch 'new\s+GameClient') "Acceptance integration fabricated GameClient."
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('phantom.goal023a.seed" value="23002311"', 'phantom-rift-goal023a-focused-test', 'phantom-rift-goal023a-test', 'phantom-rift-goal023a-affected-test', 'phantom-static-verify-023a') 'Goal 023A Ant routes'
	$launcher = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java'
	foreach ($routeMode in @('rift023a-party-binding', 'rift023a-managed-consent', 'rift023a-preinvite-revalidation', 'rift023a-invitation-authority', 'rift023a-restart-migration', 'rift023a-semantic-facts', 'rift023a-candidate-ordering', 'rift023a-route-binding', 'rift023a-performance')) { Assert-True ($build.Contains($routeMode) -or $launcher.Contains($routeMode)) "Mode absent: $routeMode" }
	$goal023Target = [regex]::Match($build, '<target name="phantom-static-verify-023"[\s\S]*?</target>').Value
	Assert-True (-not $goal023Target.Contains('-WorkingTree')) "Historical Goal 023 Ant verifier still uses -WorkingTree."

	$review023 = Read-TargetUtf8 'docs/phantoms/reviews/023-independent-review.md'
	Contains-All $review023 @('CHANGES_REQUIRED', 'R023A-01', 'R023A-02', 'R023A-03', 'R023A-04', 'R023A-05', 'R023A-06', 'R023A-07', 'R023A-08', 'Goal 024+: NOT_STARTED') 'Goal 023 baseline review'
	$review023a = Read-TargetUtf8 'docs/phantoms/reviews/023a-independent-review.md'
	Contains-All $review023a @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'self-accept', 'Goal 024') 'Goal 023A review handoff'
	$report = Read-TargetUtf8 'docs/phantoms/reports/023a-rift-production-integration-corrections.md'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, $RequiredSeed, 'R023A-01', 'R023A-08', 'PowerShell 5.1', 'PowerShell 7', 'byte-identical') 'Goal 023A report'
	$master = Read-TargetUtf8 'PHANTOM_DEVELOPMENT_MASTER_PLAN.md'
	$roadmap = Read-TargetUtf8 'docs/PHANTOM_BOTS_ROADMAP.md'
	Contains-All $master @('CORRECTIVE_023A_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 024') 'Master plan corrective marker'
	Contains-All $roadmap @('CORRECTIVE_023A_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 024') 'Roadmap corrective marker'

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
			if ($path -notmatch 'verify-task-023a\.ps1$') { Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found: $path" }
		}
	}

	if ($script:Mode -eq "working")
	{
		& git -c core.safecrlf=false diff --check $RequiredParent --
		Assert-True ($LASTEXITCODE -eq 0) "Working diff check failed."
	}
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		Assert-True ($remote -eq $script:TargetCommit) "Remote head differs from Goal 023A commit."
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed diff check failed."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @('org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.class','org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.class','org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.class')) { Assert-True ($jarEntries -contains $entry) "JAR lacks: $entry" }
	}

	Write-Output 'TASK023A_VERIFIER_OK'
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
	Write-Output "scope=$($paths.Count)"
	Write-Output "changed_production_data=$($changedProductionData.Count)"
	Write-Output "new_production_data=$($newProductionData.Count)"
	Write-Output "rift_policy_sha256=$(Target-Sha256 'dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml')"
}
finally { Pop-Location }