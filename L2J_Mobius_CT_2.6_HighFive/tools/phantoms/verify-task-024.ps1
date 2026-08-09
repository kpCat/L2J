param([switch] $WorkingTree)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RequiredParent = "e67298697eaecc629a03b215a78ffa947233efd3"
$RequiredSubject = "feat(phantoms): add farming resource negotiation"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "24002401"

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
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 024 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 024 artifact is absent: $relativePath"
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
	Assert-True ($LASTEXITCODE -eq 0) "Could not inspect Goal 024 added lines."
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
	if ($path -match '^docs/phantoms/tasks/024-farming-resource-negotiation/') { return $true }
	if ($path -match '^java/org/l2jmobius/gameserver/phantoms/farming/') { return $true }
	if ($path -match '^dist/game/data/phantoms/farming/') { return $true }
	return $path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
		'build.xml',
		'docs/PHANTOM_BOTS_ROADMAP.md',
		'docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md',
		'docs/phantoms/reports/024-farming-resource-negotiation.md',
		'docs/phantoms/reviews/023c-independent-review.md',
		'docs/phantoms/reviews/024-independent-review.md',
		'dist/game/data/phantoms/social/high-five-social-v1.xml',
		'dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml',
		'dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv',
		'dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml',
		'dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml',
		'dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java',
		'java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java',
		'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java',
		'java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionCatalog.java',
		'java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionModel.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomSemanticSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomActivationGateSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomConversationSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
		'tools/phantoms/verify-task-024.ps1'
	)
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 024 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 024 requires the exact accepted Goal 023C parent."
		$script:Mode = "working"
		$script:TargetCommit = $head
	}
	else
	{
		& git merge-base --is-ancestor $RequiredParent $head
		Assert-True ($LASTEXITCODE -eq 0) "Goal 024 parent is not an ancestor of HEAD."
		$lineage = @(Git-Lines @("rev-list", "--first-parent", "--reverse", "$RequiredParent..$head"))
		Assert-True ($lineage.Count -ge 1) "Goal 024 completion commit is absent."
		$script:TargetCommit = $lineage[0]
		$script:Mode = "committed"
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredParent) "Goal 024 is not the ordinary direct child."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 024 commit subject changed."
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--"); Add-Untracked $changed }
	else { Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--") }
	$paths = @($changed | Sort-Object)
	Assert-True ($paths.Count -gt 0) "Goal 024 scope is empty."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 024 path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|model/groups/Party\.java$|tasks/02[5-9]|tasks/0[3-9][0-9]|\.sql$|\.l2j$') "Forbidden Goal 024 path: $path"
		Assert-True ($path -notmatch '^\.phantom-local/|^\.idea/|\.class$|\.jar$|\.zip$') "Generated/binary Goal 024 path: $path"
	}
	Assert-True (@($paths | Where-Object { $_ -match '\.sql$' }).Count -eq 0) "Goal 024 changed SQL."

	$model = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingModel.java'
	Contains-All $model @('COMPONENT_TYPE = "farming.conflict"', 'SCHEMA_VERSION = 1', 'ROOM', 'MOB_GROUP', 'SHARE', 'WAIT', 'MOVE', 'REFUSE', 'ESCALATE', 'lowerRemaining', 'higherRemaining', 'exactPair') 'Farming model'
	$service = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java'
	Contains-All $service @('PhantomAcquisitionService', 'perceptibleProfiles(profileId, PhantomPerceptionChannel.LOCAL_CHAT', 'sameParty', 'Math.min(callerProfileId, counterpartProfileId)', 'AFTER_FIRST_FINAL', 'exactPair', 'goal.persistence', 'conflict.escalation', 'farming.agreement.offered', 'farming.agreement.accepted', 'farming.agreement.refused', 'farming.conflict.escalated', 'agreement.fulfilled', 'agreement.broken') 'Farming service'
	Assert-True ($service -notmatch 'World\.getPlayers|World\.getInstance\(\)\.getPlayers|listProfiles\s*\(|sendPacket\s*\(|\.switchSource\s*\(|GameClient|doAttack|forceAttack|startCombat') "Farming service gained forbidden global/client/acquisition/combat mutation."
	$port = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingConflictPort.java'
	Contains-All $port @('ALLOW', 'SHARE', 'NEGOTIATE', 'WAIT', 'MOVE', 'STALE', 'private static final Evaluator EMPTY', 'farming.conflict.uninstalled') 'Narrow Goal 021 farming gate'
	$acquisition = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java'
	Contains-All $acquisition @('case TRAVEL_REQUIRED -> gatedDirective', 'case TARGET_REQUIRED -> gatedDirective', 'PhantomFarmingConflictPort.evaluate', 'case ALLOW, SHARE -> allowed', 'case NEGOTIATE, WAIT -> new Directive(DirectiveKind.BLOCKED', 'case MOVE, STALE -> new Directive(DirectiveKind.SWITCH', 'public OperationResult switchSource') 'Goal 021 boundary gate'
	$topology = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java'
	Contains-All $topology @('perceptibleProfiles(long observerProfileId, PhantomPerceptionChannel channel, int limit)', 'listForNodes(nodes, limit + 1, view.generation())', '.limit(limit)') 'Bounded Goal 010 perception seam'
	$topologyAdded = Read-AddedText 'java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java'
	Assert-True ($topologyAdded -notmatch 'listProfiles\s*\(|World\.getPlayers') "Goal 010 seam gained a global profile/player scan."

	$policy = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingPolicy.java'
	Contains-All $policy @('DocumentBuilderFactory', 'disallow-doctype-decl', 'Set.of("claimLeaseMinutes"', 'MessageDigest.getInstance("SHA-256")', 'maximumClaimants', 'perceptionLimit') 'Strict hashed farming policy'
	$policyXml = Read-TargetUtf8 'dist/game/data/phantoms/farming/high-five-farming-conflict-v1.xml'
	Contains-All $policyXml @('claimLeaseMinutes="3"', 'maximumRounds="3"', 'maximumAlternatives="4"', 'maximumClaimants="8"', 'perceptionLimit="32"', 'historyReceipts="4"') 'Farming policy bounds'
	$social = Read-TargetUtf8 'dist/game/data/phantoms/social/high-five-social-v1.xml'
	Contains-All $social @('code="1016" key="farming.agreement.offered"', 'code="1017" key="farming.agreement.accepted"', 'code="1018" key="farming.agreement.refused"', 'code="1019" key="farming.conflict.escalated"') 'Goal 018 farming events'

	$semantic = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java'
	$executionModel = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionModel.java'
	$executionPort = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java'
	Contains-All $semantic @('farming.conflict.query') 'Goal 019 typed intent'
	Contains-All $executionModel @('farming.conflict.query') 'Goal 020 query proposal'
	Contains-All $executionPort @('case "farming.conflict.query" -> farmingConflict(profileId)', 'FARMING_CLAIM_STATUS', 'FARMING_REMAINING', 'FARMING_ESCALATION') 'Goal 020 farming facts'

	$system = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Contains-All $system @('new PhantomFarmingService', 'PhantomFarmingConflictPort.install(_farmingService)', 'PhantomFarmingConflictPort.uninstall(_farmingService)', 'new PhantomFarmingDecision', 'farmingSnapshot()') 'Production lifecycle composition'
	$farmingPackage = (($paths | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/phantoms/farming/.*\.java$' } | ForEach-Object { Read-TargetUtf8 $_ }) -join [Environment]::NewLine)
	Assert-True ($farmingPackage -notmatch 'new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture|\bFuture<|ThreadPool\.|World\.getPlayers|World\.getInstance\(\)\.getPlayers|listProfiles\s*\(|sendPacket\s*\(|GameClient|doAttack|forceAttack|teleToLocation|addPartyMember|removePartyMember') "Farming package gained a forbidden worker/global/client/combat/navigation/Party mutation."

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomFarmingSuite.java'
	Contains-All $tests @('24002401L', 'RESOURCE_POLICY', 'PERCEPTION_CLAIMS', 'PARTY_SHARE', 'BILATERAL', 'CONVERGENCE', 'FACTS', 'RESTART_FAULT', 'LIFECYCLE_PERFORMANCE', '100_000') 'Dynamic Goal 024 proof'
	$acquisitionTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionSuite.java'
	Contains-All $acquisitionTests @('FARMING_GATE', '24002401L', 'PhantomFarmingConflictPort.install', 'PhantomFarmingConflictPort.uninstall') 'Real Goal 021 gate regression'
	$combatIntegrationTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java'
	Contains-All $combatIntegrationTests @('Service manor Combat cleanup did not quiesce before Harvester dispatch.', 'Service manor Harvester dispatch did not persist HARVEST_DISPATCHING.') 'Goal 021 manor-active lifecycle regression'
	$semanticTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomSemanticSuite.java'
	Contains-All $semanticTests @('assertEquals(242, first.corpus().size()', 'assertEquals(15, first.intents().size()', 'farming.conflict.query') 'Goal 019 semantic pack regression'
	$activationTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomActivationGateSuite.java'
	Contains-All $activationTests @('16C749B9E151E7D5FE7D702989A71DFC2AB3EEDDE9FA103C40B7D01A36E66A18', '2B7676BCCFD4395C267BC298E2F2C8DAE265E23CEE76D76853504BF7172F935E', 'index < 86') 'Goal 020 semantic activation regression'
	$conversationTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomConversationSuite.java'
	Contains-All $conversationTests @('assertEquals(129, catalog.corpusCases()') 'Goal 020 conversation corpus regression'
	$launcher = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java'
	Contains-All $launcher @('farming-resource-policy', 'farming-perception-claims', 'farming-party-share', 'farming-bilateral', 'farming-convergence', 'farming-facts', 'farming-restart-fault', 'farming-acquisition-gate', 'farming-lifecycle-performance') 'Goal 024 launcher modes'
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('phantom.goal024.seed" value="24002401"', 'phantom-farming-goal024-focused-test', 'phantom-farming-goal024-affected-test', 'phantom-farming-goal024-test', 'phantom-static-verify-024', 'phantom-rift-goal023c-test', 'phantom-acquisition-manor-restart-transition-test', 'phantom-acquisition-quest-background-test') 'Goal 024 Ant routes'
	$goal023cVerifier = [regex]::Match($build, '<target name="phantom-static-verify-023c"[\s\S]*?</target>').Value
	Assert-True (-not $goal023cVerifier.Contains('-WorkingTree')) "Historical Goal 023C verifier still uses -WorkingTree."

	$review023 = Read-TargetUtf8 'docs/phantoms/reviews/023-independent-review.md'
	$review023a = Read-TargetUtf8 'docs/phantoms/reviews/023a-independent-review.md'
	Assert-True ($review023.Contains('CHANGES_REQUIRED')) "Historical Goal 023 CHANGES_REQUIRED was erased."
	Assert-True ($review023a.Contains('CHANGES_REQUIRED')) "Historical Goal 023A CHANGES_REQUIRED was erased."
	$review023c = Read-TargetUtf8 'docs/phantoms/reviews/023c-independent-review.md'
	Contains-All $review023c @('Goal 023C: ACCEPT', 'R023C-01: CLOSED', 'Goal 023 overall: ACCEPT', $RequiredParent, 'Goal 025+: NOT_STARTED') 'Independent Goal 023C acceptance'
	$contract = Read-TargetUtf8 'docs/phantoms/architecture/FARMING_RESOURCE_NEGOTIATION_CONTRACT.md'
	Contains-All $contract @('ROOM', 'MOB_GROUP', 'exactPair', 'SHARE', 'WAIT', 'MOVE', 'REFUSE', 'ESCALATE', 'Goal 025') 'Farming architecture contract'
	$handoff = Read-TargetUtf8 'docs/phantoms/reviews/024-independent-review.md'
	Contains-All $handoff @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSeed, 'Goal 025+: NOT_STARTED') 'Goal 024 review handoff'
	Assert-True (-not $handoff.Contains('Goal 024: ACCEPT')) "Goal 024 handoff self-accepted the goal."
	$report = Read-TargetUtf8 'docs/phantoms/reports/024-farming-resource-negotiation.md'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, $RequiredSeed, 'PowerShell 5.1', 'PowerShell 7', 'byte-identical', 'production DB', 'Goal 025') 'Goal 024 report'
	$master = Read-TargetUtf8 'PHANTOM_DEVELOPMENT_MASTER_PLAN.md'
	$roadmap = Read-TargetUtf8 'docs/PHANTOM_BOTS_ROADMAP.md'
	Contains-All $master @('Goal 024', 'IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 025') 'Master Goal 024 status'
	Contains-All $roadmap @('Goal 024', 'IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'Goal 025') 'Roadmap Goal 024 status'

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
			if ($path -notmatch 'verify-task-024\.ps1$') { Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found: $path" }
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
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote does not contain Goal 024."
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed diff check failed."
	}

	$classEntries = @(
		'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.class',
		'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingConflictPort.class',
		'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingDecision.class',
		'org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingStateCodec.class'
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

	Write-Output 'TASK024_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
	Write-Output "scope=$($paths.Count)"
}
finally
{
	Pop-Location
}
