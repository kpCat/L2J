param([switch] $WorkingTree)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RequiredParent = "1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb"
$RequiredSubject = "feat(phantoms): add rift readiness and advanced party recruitment"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "23002301"

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
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 023 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 023 artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Target-Sha256([string] $relativePath)
{
	$sha = [Security.Cryptography.SHA256]::Create()
	try { return ([BitConverter]::ToString($sha.ComputeHash((Read-TargetBytes $relativePath)))).Replace("-", "").ToUpperInvariant() }
	finally { $sha.Dispose() }
}

function Read-AddedText([string] $relativePath)
{
	$repositoryPath = ":(top)" + $script:ModulePrefix + $relativePath
	if ($script:Mode -eq "working") { $lines = & git -c core.safecrlf=false diff --unified=0 $RequiredParent -- $repositoryPath }
	else { $lines = & git -c core.safecrlf=false diff --unified=0 $RequiredParent $script:TargetCommit -- $repositoryPath }
	Assert-True ($LASTEXITCODE -eq 0) "Could not inspect added lines."
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
	if ($path -match '^java/org/l2jmobius/gameserver/phantoms/rift/.*\.java$') { return $true }
	if ($path -match '^docs/phantoms/tasks/023-rift-advanced-party-recruitment/') { return $true }
	return $path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md', 'build.xml',
		'dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml',
		'docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md',
		'docs/phantoms/reports/023-rift-advanced-party-recruitment.md',
		'docs/phantoms/reviews/022-final-review.md',
		'docs/phantoms/reviews/023-independent-review.md',
		'java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java',
		'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomRiftSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
		'tools/phantoms/verify-task-022c2.ps1', 'tools/phantoms/verify-task-023.ps1'
	)
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 023 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 023 requires exact accepted Goal 022 parent."
		$script:Mode = "working"
		$script:TargetCommit = $head
	}
	else
	{
		& git merge-base --is-ancestor $RequiredParent $head
		Assert-True ($LASTEXITCODE -eq 0) "Goal 023 parent is not an ancestor of HEAD."
		$lineage = @(Git-Lines @("rev-list", "--first-parent", "--reverse", "$RequiredParent..$head"))
		Assert-True ($lineage.Count -ge 1) "Goal 023 completion commit is absent."
		$script:TargetCommit = $lineage[0]
		$script:Mode = "historical"
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredParent) "Goal 023 is not the ordinary direct child."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 023 commit subject changed."
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--"); Add-Untracked $changed }
	else { Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--") }
	$paths = @($changed | Sort-Object)
	Assert-True (($paths.Count -gt 0) -and ($paths.Count -le 48)) "Goal 023 total scope exceeds 48 files."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 023 path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|model/groups/Party\.java$|PartyInvitationService\.java$|tasks/02[4-9]|tasks/0[3-9][0-9]') "Forbidden Goal 023 path: $path"
	}
	$newPaths = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working") { Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $RequiredParent, "--"); Add-Untracked $newPaths }
	else { Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $RequiredParent, $script:TargetCommit, "--") }
	$newProductionData = @($newPaths | Where-Object { Is-ProductionData $_ })
	$changedProductionData = @($paths | Where-Object { Is-ProductionData $_ })
	Assert-True ($newProductionData.Count -le 18) "New production/data scope exceeds 18."
	Assert-True ($changedProductionData.Count -le 28) "Changed production/data scope exceeds 28."
	Assert-True (@($paths | Where-Object { $_ -match '\.sql$' }).Count -eq 0) "Goal 023 changed SQL."

	$expectedHashes = @{
		'dist/game/data/DimensionalRift.xml' = 'BBAF488F3A9B5A7765716679B532223EBFB26877D1FE111D35F94DBE21349AD9'
		'dist/game/data/xsd/DimensionalRift.xsd' = 'B8D4DC7235F72FA970116145A34371A4DB94B7E1F8517D39E782A38F25C5EE8F'
		'dist/game/config/General.ini' = 'B3DB41E77B95BE588AAC9BF75A93FBF01019714C6E1AD58619E55F948C6178FE'
		'java/org/l2jmobius/gameserver/config/GeneralConfig.java' = $(if ($script:Mode -eq "working") { '94AC374844114C9D83A76B2175716710180FE5CA790234037C9C8764A3FB5957' } else { 'B7C4B37244D7AAB6F4340BBD32C570728C86D1FDDFCDE0EE52570A32216FF9CF' })
	}
	foreach ($entry in $expectedHashes.GetEnumerator()) { Assert-True ((Target-Sha256 $entry.Key) -eq $entry.Value) "Pinned Rift source/config drifted: $($entry.Key)" }

	$xml = New-Object Xml.XmlDocument
	$xml.XmlResolver = $null
	$xml.LoadXml((Read-TargetUtf8 'dist/game/data/DimensionalRift.xml'))
	$types = @($xml.SelectNodes('/rift/area') | ForEach-Object { [int] $_.type } | Sort-Object)
	Assert-True (($types -join ',') -eq '1,2,3,4,5,6') "Rift XML does not contain exactly six types."
	foreach ($area in $xml.SelectNodes('/rift/area')) { Assert-True ($area.SelectNodes('room').Count -eq 9) "Rift type does not contain nine rooms." }
	$policyXml = New-Object Xml.XmlDocument
	$policyXml.XmlResolver = $null
	$policyXml.LoadXml((Read-TargetUtf8 'dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml'))
	Assert-True (($policyXml.DocumentElement.candidateLimit -eq '32') -and ($policyXml.SelectNodes('/riftPolicy/tier').Count -eq 6)) "Rift policy bounds/types changed."

	$catalog = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftCatalog.java'
	Contains-All $catalog @('disallow-doctype-decl', 'ACCESS_EXTERNAL_DTD', 'MAX_BYTES', 'levelsSupported = false', 'TYPE_KEYS', 'rooms.size() != 9') 'Strict factual catalog'
	$readiness = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftReadinessService.java'
	Contains-All $readiness @('PhantomPartyRoleMatcher', 'canonicalParty', 'fullParty', 'minimumPartySize', 'ReadinessDimension.LEVEL', 'ReadinessDimension.ALIVE', 'ReadinessDimension.VITALS', 'ReadinessDimension.EQUIPMENT', 'ReadinessDimension.CAPABILITIES', 'ReadinessDimension.SUPPLIES', 'ReadinessDimension.TRAVEL', 'Status.READY_TO_ENTER') 'Canonical readiness'
	$backend = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.java'
	Contains-All $backend @('getVisibleObjectsInRange', '.limit(limit)', 'entryReadiness', 'MemberRef.real') 'Bounded live backend'
	Assert-True ($backend -notmatch 'World\.getInstance\(\)\.getPlayers\(\)') "Global online-player scan found."
	$service = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.java'
	Contains-All $service @('GOAL_TYPE = "rift.prepare"', 'Stage.REQUEST_INVITE', 'Stage.OBSERVE_INVITE', 'Status.INVITE_PENDING', 'refusalCooldownMillis', 'requestRoute', 'Status.READY_TO_ENTER', 'latest(long profileId)') 'Durable preparation'
	Assert-True ($service -notmatch 'DimensionalRiftManager|destroyItem|teleToLocation|new\s+DimensionalRift|start\s*\(') "Preparation service gained Rift mutation."
	$partyPort = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.java'
	Contains-All $partyPort @('formForGoal', '_coordinator.invite', '_coordinator.requestRoute', 'OperationPhase.ABORTED') 'Goal 017 handoff'
	$conversation = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java'
	Contains-All $conversation @('PhantomRiftConversationFacts.NONE', '_riftFacts.latest(profileId)', '"rift.missing_role"', '"rift.ready"') 'Typed latest facts'
	$system = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Assert-True (($system.IndexOf('riftDecision.registerCandidates(candidateRegistry)', [StringComparison]::Ordinal) -ge 0) -and ($system.IndexOf('riftDecision.registerCandidates(candidateRegistry)', [StringComparison]::Ordinal) -lt $system.IndexOf('candidateRegistry.seal()', [StringComparison]::Ordinal))) "Rift candidate registration occurs after registry seal."
	Assert-True (($system.IndexOf('riftDecision.registerHandlers(handlerRegistry)', [StringComparison]::Ordinal) -ge 0) -and ($system.IndexOf('riftDecision.registerHandlers(handlerRegistry)', [StringComparison]::Ordinal) -lt $system.IndexOf('handlerRegistry.seal()', [StringComparison]::Ordinal))) "Rift handler registration occurs after registry seal."
	$managerAdded = Read-AddedText 'java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java'
	Contains-All $managerAdded @('entryReadiness', 'EntryReadinessSnapshot', 'Side-effect-free entry authority') 'Rift query seam'
	Assert-True ($managerAdded -notmatch 'destroyItem|teleToLocation|new\s+DimensionalRift|setPartyInside|spawn') "Manager addition contains mutation."

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomRiftSuite.java'
	foreach ($token in @('REQUIRED_SEED = 23002301L', 'ordinary-consent-never-forged', 'pending-invite-no-duplicate-after-restart', 'typed-latest-snapshot-facts', '100_000', '10_000')) { Assert-True ($tests.Contains($token)) "Dynamic evidence absent: $token" }
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('phantom.goal023.seed" value="23002301"', 'phantom-rift-goal023-test', 'phantom-rift-goal023-affected-test', 'phantom-static-verify-023') 'Goal 023 Ant routes'
	foreach ($testMode in @('rift-catalog-authority', 'rift-roster-readiness', 'rift-role-composition', 'rift-recruitment', 'rift-real-player-invite', 'rift-travel-readiness', 'rift-restart-reconciliation', 'rift-performance')) { Assert-True ($build.Contains($testMode)) "Mode absent: $testMode" }

	$review022 = Read-TargetUtf8 'docs/phantoms/reviews/022-final-review.md'
	Contains-All $review022 @('ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER', 'Goal 022 overall:', 'ACCEPT', $RequiredParent, 'does not claim') 'Goal 022 final ACCEPT'
	$verifier022 = Read-TargetUtf8 'tools/phantoms/verify-task-022c2.ps1'
	Contains-All $verifier022 @('$AcceptedBaseline = "1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb"', '$script:Mode = "historical"', 'merge-base --is-ancestor') 'Historical 022c2 verifier'
	$contract = Read-TargetUtf8 'docs/phantoms/architecture/RIFT_RECRUITMENT_CONTRACT.md'
	$report = Read-TargetUtf8 'docs/phantoms/reports/023-rift-advanced-party-recruitment.md'
	$review = Read-TargetUtf8 'docs/phantoms/reviews/023-independent-review.md'
	Contains-All $contract @('READY_TO_ENTER', '7079', 'RoleMatcher', 'ordinary real Player', 'Goal 017', 'one pending invite') 'Architecture contract'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, $RequiredSeed, 'RiftMinPartySize = 2', 'PowerShell 5.1', 'PowerShell 7', 'byte-identical') 'Goal 023 report'
	Contains-All $review @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'независимого review', 'self-accept', 'Goal 024') 'Independent review handoff'

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B), @(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A), @(0x0420, 0x00A4), @(0x0420, 0x045A),
		@(0x0420, 0x0408), @(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5), @(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026), @(0x0421, 0x040F),
		@(0x0421, 0x20AC), @(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A), @(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026), @(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char] $_[0]) + ([string][char] $_[1])) }) -join '|'
	$replacement = [string][char] 0xFFFD
	$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $paths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1)$') -or ($path -eq 'build.xml'))
		{
			$text = Read-TargetUtf8 $path
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacement)) "Mojibake marker found: $path"
			if ($path -notmatch 'verify-task-0(?:22c2|23)\.ps1$') { Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found: $path" }
		}
	}
	foreach ($path in @($changedProductionData | Where-Object { $_ -match '\.java$' }))
	{
		$text = if ($newPaths.Contains($path)) { Read-TargetUtf8 $path } else { Read-AddedText $path }
		Assert-True ($text -notmatch 'new\s+Thread\s*\(|Executors\.|ScheduledFuture|CompletableFuture|\bFuture<|ThreadPool\.|GameClient|sendPacket\s*\(') "Forbidden worker/client/packet API found: $path"
	}
	if ($script:Mode -eq "working") { & git -c core.safecrlf=false diff --check $RequiredParent --; Assert-True ($LASTEXITCODE -eq 0) "Working diff check failed." }
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote does not contain Goal 023."
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed diff check failed."
	}
	$classEntries = @(
		'org/l2jmobius/gameserver/phantoms/rift/PhantomRiftService.class',
		'org/l2jmobius/gameserver/phantoms/rift/PhantomRiftCatalog.class',
		'org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftBackend.class',
		'org/l2jmobius/gameserver/phantoms/rift/L2jPhantomRiftPartyPort.class'
	)
	if ($script:Mode -eq "working")
	{
		foreach ($entry in $classEntries)
		{
			Assert-True (Test-Path -LiteralPath (Join-Path (Split-Path $script:ModuleRoot -Parent) ("build/bin/" + $entry)) -PathType Leaf) "Compiled classes lack: $entry"
		}
	}
	else
	{
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in $classEntries) { Assert-True ($jarEntries -contains $entry) "JAR lacks: $entry" }
	}

	Write-Output 'TASK023_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
	Write-Output "scope=$($paths.Count)"
	Write-Output "changed_production_data=$($changedProductionData.Count)"
	Write-Output "new_production_data=$($newProductionData.Count)"
	Write-Output "rift_xml_sha256=$(Target-Sha256 'dist/game/data/DimensionalRift.xml')"
	Write-Output "rift_xsd_sha256=$(Target-Sha256 'dist/game/data/xsd/DimensionalRift.xsd')"
}
finally { Pop-Location }
