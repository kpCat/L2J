param(
	[switch] $WorkingTree
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AcceptedCheckpoint1 = "0045f60417f4605f46e3058b9a694278283b1456"
$FoundationCommit = "365c014a48c7998eb880352b00503a28b2f27a2c"
$FoundationSubject = "feat(phantoms): add manor and quest acquisition chains"
$BoundaryCommit = "130a08a90c729dd94c13d782416bc0f1f727e6c7"
$BoundarySubject = "fix(phantoms): complete manor quest topology and causality"
$AnchorCommit = "83b22f2338c297151a9b0881fdf566963ee5d571"
$AnchorSubject = "fix(phantoms): expose territory geometry and finalize acquisition"
$NearFinalCommit = "81e4d2a7044f8c1bafc7db6b5d3c66ce4df050aa"
$NearFinalSubject = "fix(phantoms): finalize feasible manor quest acquisition"
$TerminalCommit = "906b8a043320deb955da02276cf27797e0c5fadd"
$TerminalSubject = "fix(phantoms): close manor attribution and quest service recovery"
$ExactDeltaCommit = "0c41280632617f50d4bd133b59b81326e3b6d3f6"
$ExactDeltaSubject = "fix(phantoms): enforce exact quest callback item delta"
$AcceptedCheckpoint2 = "043844c0fd7a0bfcac0d5f58461a21633b032332"
$RequiredSubject = "fix(phantoms): close quest collection cap boundary"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "21002102"
$TargetSourceIds = @(20013, 20019, 20016)

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Git-Lines([string[]] $arguments)
{
	$result = & git -c core.safecrlf=false @arguments
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

function Read-CommitBytes([string] $commit, [string] $relativePath)
{
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = New-Object Diagnostics.ProcessStartInfo
	$start.FileName = "git"
	$start.Arguments = "show $commit`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = New-Object IO.MemoryStream
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 021c2 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 021c2 artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8Strict([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Get-TargetSha256([string] $relativePath)
{
	$sha256 = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha256.ComputeHash((Read-TargetBytes $relativePath)))).Replace("-", "").ToLowerInvariant()
	}
	finally
	{
		$sha256.Dispose()
	}
}

function Add-ChangedPaths([Collections.Generic.HashSet[string]] $set, [string[]] $arguments)
{
	foreach ($line in Git-Lines $arguments)
	{
		[void] $set.Add((To-ModulePath $line))
	}
}

function Add-WorkingUntracked([Collections.Generic.HashSet[string]] $set)
{
	foreach ($line in Git-Lines @("ls-files", "--others", "--exclude-standard"))
	{
		$normalized = $line.Replace("\", "/")
		$path = To-ModulePath $normalized
		if ($normalized.StartsWith('../', [StringComparison]::Ordinal) -or !(Test-Path -LiteralPath (Join-Path $script:ModuleRoot $path)))
		{
			continue
		}
		[void] $set.Add($path)
	}
}

function Is-ProductionPath([string] $path)
{
	return ($path -match '^java/org/l2jmobius/gameserver/') -or ($path -match '^dist/game/(?:config|data)/')
}

function Is-CumulativeAllowedPath([string] $path)
{
	return ($path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
		'build.xml',
		'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml',
		'dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml',
		'dist/game/data/phantoms/topology/high-five-core.xml',
		'docs/PHANTOM_BOTS_ROADMAP.md',
		'docs/phantoms/architecture/MANOR_QUEST_ACQUISITION_CONTRACT.md',
		'docs/phantoms/reports/021-checkpoint-2-manor-quest-acquisition.md',
		'docs/phantoms/reviews/021-checkpoint-1-final-review.md',
		'docs/phantoms/reviews/021-checkpoint-2-independent-review.md',
		'java/org/l2jmobius/gameserver/data/xml/SpawnData.java',
		'java/org/l2jmobius/gameserver/model/zone/type/NpcSpawnTerritory.java',
		'java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySnapshot.java',
		'tools/phantoms/verify-task-021c1.ps1',
		'tools/phantoms/verify-task-021c2.ps1'
	)) -or
		($path -match '^docs/phantoms/tasks/021-checkpoint-2-manor-quest-acquisition/') -or
		($path -match '^java/org/l2jmobius/gameserver/phantoms/(?:acquisition|background|combat|knowledge)/') -or
		($path -eq 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java') -or
		($path -match '^test/java/org/l2jmobius/tests/phantoms/Phantom(?:Acquisition|Background|Combat|GameKnowledge|TestLauncher|Topology)')
}

function Is-FinalAllowedPath([string] $path)
{
	return $path -in @(
		'build.xml',
		'docs/phantoms/reports/021-checkpoint-2-manor-quest-acquisition.md',
		'docs/phantoms/reviews/021-checkpoint-2-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java',
		'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionQuestSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java',
		'tools/phantoms/verify-task-021c2.ps1'
	)
}

function Canonical-Polygon([object[]] $vertices)
{
	$points = @($vertices | ForEach-Object { "$(($_.x).ToString()),$(($_.y).ToString())" })
	Assert-True (($points.Count -ge 3) -and ($points.Count -le 32)) "Factual polygon is outside the 3..32 vertex bound."
	$candidates = New-Object Collections.Generic.List[string]
	for ($direction = 0; $direction -lt 2; $direction++)
	{
		$current = if ($direction -eq 0) { @($points) } else { @($points[($points.Count - 1)..0]) }
		for ($offset = 0; $offset -lt $points.Count; $offset++)
		{
			$rotated = for ($index = 0; $index -lt $points.Count; $index++) { $current[($offset + $index) % $points.Count] }
			$candidates.Add(($rotated -join ';'))
		}
	}
	return @($candidates | Sort-Object)[0]
}

function Geometry-Key([string] $sourcePath, [int] $lowZ, [int] $highZ, [object[]] $vertices)
{
	return "$sourcePath|$lowZ|$highZ|$(Canonical-Polygon $vertices)"
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 021c2 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	Assert-True ((Git-Lines @("rev-parse", "$FoundationCommit^" ) | Select-Object -First 1) -eq $AcceptedCheckpoint1) "Goal 021c2 foundation parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $FoundationCommit) | Select-Object -First 1) -eq $FoundationSubject) "Goal 021c2 foundation subject changed."
	Assert-True ((Git-Lines @("rev-parse", "$BoundaryCommit^" ) | Select-Object -First 1) -eq $FoundationCommit) "Goal 021c2 boundary parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $BoundaryCommit) | Select-Object -First 1) -eq $BoundarySubject) "Goal 021c2 boundary subject changed."
	Assert-True ((Git-Lines @("rev-parse", "$AnchorCommit^" ) | Select-Object -First 1) -eq $BoundaryCommit) "Goal 021c2 anchor parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $AnchorCommit) | Select-Object -First 1) -eq $AnchorSubject) "Goal 021c2 anchor subject changed."
	Assert-True ((Git-Lines @("rev-parse", "$NearFinalCommit^" ) | Select-Object -First 1) -eq $AnchorCommit) "Goal 021c2 near-final parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $NearFinalCommit) | Select-Object -First 1) -eq $NearFinalSubject) "Goal 021c2 near-final subject changed."
	Assert-True ((Git-Lines @("rev-parse", "$TerminalCommit^" ) | Select-Object -First 1) -eq $NearFinalCommit) "Goal 021c2 terminal parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $TerminalCommit) | Select-Object -First 1) -eq $TerminalSubject) "Goal 021c2 terminal subject changed."
	Assert-True ((Git-Lines @("rev-parse", "$ExactDeltaCommit^" ) | Select-Object -First 1) -eq $TerminalCommit) "Goal 021c2 exact-delta parent changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $ExactDeltaCommit) | Select-Object -First 1) -eq $ExactDeltaSubject) "Goal 021c2 exact-delta subject changed."
	& git merge-base --is-ancestor $AcceptedCheckpoint1 $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 021c1 checkpoint is not an ancestor of HEAD."
	& git merge-base --is-ancestor $ExactDeltaCommit $head
	Assert-True ($LASTEXITCODE -eq 0) "Goal 021c2 exact-delta foundation is not an ancestor of HEAD."
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $AcceptedCheckpoint2) | Select-Object -First 1) -eq $ExactDeltaCommit) "Accepted Goal 021c2 is not one ordinary child of the exact-delta foundation."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $AcceptedCheckpoint2) | Select-Object -First 1) -eq $RequiredSubject) "Accepted Goal 021c2 subject changed."
	& git merge-base --is-ancestor $AcceptedCheckpoint2 $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 021c2 baseline is not an ancestor of HEAD."
	$script:TargetCommit = $AcceptedCheckpoint2
	$script:Mode = "historical"

	$cumulative = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	$final = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-ChangedPaths $cumulative @("diff", "--name-only", $AcceptedCheckpoint1, $script:TargetCommit, "--")
	Add-ChangedPaths $final @("diff", "--name-only", $ExactDeltaCommit, $script:TargetCommit, "--")
	$cumulativePaths = @($cumulative | Sort-Object)
	$finalPaths = @($final | Sort-Object)
	Assert-True (($cumulativePaths.Count -gt 0) -and ($cumulativePaths.Count -le 58)) "Cumulative Goal 021c2 scope exceeds 58 files."
	Assert-True (($finalPaths.Count -gt 0) -and ($finalPaths.Count -le 9)) "Final cap-boundary Goal 021c2 child scope exceeds 9 files."
	foreach ($path in $cumulativePaths)
	{
		Assert-True (Is-CumulativeAllowedPath $path) "Out-of-scope cumulative Goal 021c2 path: $path"
		Assert-True ($path -notmatch '022-checkpoint-1-economy-craft-enchant|phantoms/economy|verify-task-022c1') "Goal 022 path leaked into historical Goal 021c2 scope: $path"
		Assert-True ($path -notmatch '(^|/)(?:Player|Party|Attackable|Spawn|CastleManorManager)\.java$|(^|/)(?:sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)|^dist/game/data/scripts/(?:handlers|quests)/') "Forbidden Goal 021c2 path: $path"
	}
	foreach ($path in $finalPaths)
	{
		Assert-True (Is-FinalAllowedPath $path) "Out-of-scope final Goal 021c2 path: $path"
	}
	$cumulativeProduction = @($cumulativePaths | Where-Object { Is-ProductionPath $_ })
	$finalProduction = @($finalPaths | Where-Object { Is-ProductionPath $_ })
	Assert-True ($cumulativeProduction.Count -le 34) "Cumulative Goal 021c2 exceeds 34 production/data/config files."
	Assert-True ($finalProduction.Count -le 3) "Final cap-boundary Goal 021c2 child exceeds 3 production/data/config files."
	$finalNewProduction = @()
	foreach ($path in $finalProduction)
	{
		$existing = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $ExactDeltaCommit, "--", ($script:ModulePrefix + $path)))
		if ($existing.Count -eq 0) { $finalNewProduction += $path }
	}
	Assert-True ($finalNewProduction.Count -eq 0) "Terminal Goal 021c2 child adds a production/data file."
	foreach ($required in @(
		'docs/phantoms/reports/021-checkpoint-2-manor-quest-acquisition.md',
		'docs/phantoms/reviews/021-checkpoint-2-independent-review.md',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java',
		'tools/phantoms/verify-task-021c2.ps1'
	))
	{
		Assert-True ($final.Contains($required)) "Required final Goal 021c2 artifact is absent: $required"
	}

	foreach ($path in $cumulativePaths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1)$') -or ($path -eq 'build.xml'))
		{
			[void] (Read-TargetUtf8Strict $path)
		}
	}

	$reviewC1 = Read-TargetUtf8Strict 'docs/phantoms/reviews/021-checkpoint-1-final-review.md'
	$verifierC1 = Read-TargetUtf8Strict 'tools/phantoms/verify-task-021c1.ps1'
	Assert-True ($reviewC1.Contains('ACCEPT') -and $reviewC1.Contains($AcceptedCheckpoint1)) "Checkpoint 1 final review is not ACCEPT-pinned."
	Assert-True ($verifierC1.Contains('$AcceptedCheckpoint1 = "0045f60417f4605f46e3058b9a694278283b1456"') -and $verifierC1.Contains('merge-base --is-ancestor') -and $verifierC1.Contains('Read-TargetBytes')) "Verifier 021c1 is not historical/descendant-compatible."

	$sourceSpecs = @(
		@('dist/game/data/spawns/ElvenTerritory/ElvenStarting.xml', 'data/spawns/ElvenTerritory/ElvenStarting.xml', @(20013, 20019)),
		@('dist/game/data/spawns/TalkingIsland/TalkingIslandMonsters.xml', 'data/spawns/TalkingIsland/TalkingIslandMonsters.xml', @(20016))
	)
	$facts = New-Object Collections.Generic.List[object]
	$territories = @{}
	foreach ($spec in $sourceSpecs)
	{
		$moduleSourcePath = $spec[0]
		$sourcePath = $spec[1]
		$ids = @($spec[2])
		[xml] $spawnXml = Read-TargetUtf8Strict $moduleSourcePath
		foreach ($spawn in @($spawnXml.list.spawn))
		{
			$territory = $spawn.territory
			if ($null -eq $territory) { continue }
			$matchedNpcs = @($spawn.npc | Where-Object { $ids -contains [int] $_.id })
			if ($matchedNpcs.Count -eq 0) { continue }
			$vertices = @($territory.node)
			$key = Geometry-Key $sourcePath ([int] $territory.minZ) ([int] $territory.maxZ) $vertices
			if (-not $territories.ContainsKey($key))
			{
				$territories[$key] = [pscustomobject]@{ Key = $key; SourcePath = $sourcePath; Name = [string] $spawn.zone; LowZ = [int] $territory.minZ; HighZ = [int] $territory.maxZ; Vertices = $vertices }
			}
			foreach ($npc in $matchedNpcs)
			{
				$facts.Add([pscustomobject]@{ NpcId = [int] $npc.id; Count = [int] $npc.count; Key = $key })
			}
		}
	}
	foreach ($expected in @(@(20013, 20, 50), @(20019, 17, 49), @(20016, 8, 27)))
	{
		$npcFacts = @($facts | Where-Object { $_.NpcId -eq $expected[0] })
		Assert-True (($npcFacts.Count -eq $expected[1]) -and ((($npcFacts | Measure-Object -Property Count -Sum).Sum) -eq $expected[2])) "Factual loader totals changed for NPC $($expected[0])."
	}
	Assert-True (($facts.Count -eq 45) -and ($territories.Count -eq 35)) "Factual territory inventory is not exactly 45 occurrences / 35 identities."

	[xml] $topology = Read-TargetUtf8Strict 'dist/game/data/phantoms/topology/high-five-core.xml'
	Assert-True (($topology.topology.datasetId -eq 'high-five-core') -and ($topology.topology.datasetVersion -eq '2')) "Topology dataset identity/version changed."
	$polygonNodes = @($topology.topology.node | Where-Object { ($_.kind -eq 'FARMING_AREA') -and ($_.form -eq 'POLYGON') })
	Assert-True ($polygonNodes.Count -eq 15) "Topology must publish exactly 15 factual FARMING_AREA polygons."
	$mapped = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	$nodeIds = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	foreach ($node in $polygonNodes)
	{
		Assert-True ([int] $node.instanceId -eq 0) "Factual farming polygon is not in instance 0: $($node.id)"
		$refs = @($node.source | ForEach-Object { [string] $_.path })
		Assert-True (($refs.Count -eq 1) -and (($sourceSpecs | ForEach-Object { $_[1] }) -contains $refs[0])) "Factual farming node has a non-exact source ref: $($node.id)"
		$key = Geometry-Key $refs[0] ([int] $node.minZ) ([int] $node.maxZ) @($node.vertex)
		Assert-True ($territories.ContainsKey($key)) "Topology polygon is not an exact factual territory: $($node.id)"
		Assert-True ($mapped.Add($key)) "Factual territory was split/duplicated in topology: $($node.id)"
		Assert-True ($nodeIds.Add([string] $node.id)) "Duplicate factual topology node id: $($node.id)"
		$anchors = @($topology.topology.anchor | Where-Object { ($_.role -eq 'FARMING') -and ($_.nodeId -eq $node.id) })
		Assert-True ($anchors.Count -eq 1) "Factual topology node does not have exactly one FARMING anchor: $($node.id)"
		$anchor = $anchors[0]
		Assert-True ([string]::IsNullOrWhiteSpace($anchor.GetAttribute('npcId'))) "Shared factual FARMING anchor must be NPC-anonymous: $($anchor.id)"
		$anchorRefs = @($anchor.source | ForEach-Object { [string] $_.path })
		Assert-True (($anchorRefs.Count -eq 1) -and ($anchorRefs[0] -eq $refs[0])) "Factual node/anchor source identity differs: $($node.id)"
		$maximumSquared = 0L
		foreach ($vertex in @($node.vertex))
		{
			$dx = [long]([int] $anchor.x - [int] $vertex.x)
			$dy = [long]([int] $anchor.y - [int] $vertex.y)
			$squared = ($dx * $dx) + ($dy * $dy)
			if ($squared -gt $maximumSquared) { $maximumSquared = $squared }
		}
		Assert-True ($maximumSquared -le 4000000L) "Factual FARMING anchor exceeds activeTargetDistance=2000: $($anchor.id)"
		Assert-True (([int] $anchor.z -ge ([int] $node.minZ - [int] $anchor.tolerance)) -and ([int] $anchor.z -le ([int] $node.maxZ + [int] $anchor.tolerance))) "Factual FARMING anchor Z/tolerance is outside source bounds: $($anchor.id)"
	}
	Assert-True (($mapped.Count -eq 15) -and (($territories.Count - $mapped.Count) -eq 20)) "Feasible/unmapped territory coverage is not exactly 15/20."
	foreach ($expected in @(@(20013, 9), @(20019, 7), @(20016, 1)))
	{
		$mappedFacts = @($facts | Where-Object { ($_.NpcId -eq $expected[0]) -and $mapped.Contains($_.Key) })
		Assert-True ($mappedFacts.Count -eq $expected[1]) "Mapped feasible occurrence count changed for NPC $($expected[0])."
	}
	$anchorTopologyText = (New-Object Text.UTF8Encoding($false, $true)).GetString((Read-CommitBytes $AnchorCommit 'dist/game/data/phantoms/topology/high-five-core.xml'))
	[xml] $anchorTopology = $anchorTopologyText
	$currentEdgeIds = @($topology.topology.edge | ForEach-Object { [string] $_.id } | Sort-Object)
	$parentEdgeIds = @($anchorTopology.topology.edge | ForEach-Object { [string] $_.id } | Sort-Object)
	Assert-True (($currentEdgeIds.Count -eq $parentEdgeIds.Count) -and (($currentEdgeIds -join '|') -eq ($parentEdgeIds -join '|'))) "Goal 021c2 added, removed or replaced topology edges."

	$territoryClass = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/model/zone/type/NpcSpawnTerritory.java'
	$spawnData = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/data/xml/SpawnData.java'
	$knowledgeBackend = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java'
	$knowledgeBuilder = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java'
	$knowledgeModel = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java'
	$knowledgeSnapshot = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java'
	Assert-True ($territoryClass.Contains('record GeometrySnapshot') -and $territoryClass.Contains('record PolygonGeometry') -and $territoryClass.Contains('List.copyOf') -and $territoryClass.Contains('canonicalSourcePath') -and $territoryClass.Contains('geometryHash') -and $territoryClass.Contains('_unsupportedBannedGeometry')) "Immutable loaded territory boundary is incomplete."
	Assert-True ($spawnData.Contains('spawnSourcePath(file)') -and $spawnData.Contains('new NpcSpawnTerritory(territoryName, zoneForm, sourcePath)')) "SpawnData does not furnish the exact relative source path."
	Assert-True ($knowledgeBackend.Contains('territory.geometrySnapshot()') -and !$knowledgeBackend.Contains('DocumentBuilder') -and !$knowledgeBackend.Contains('getDeclaredField')) "Game Knowledge bypasses loaded geometry authority."
	Assert-True ($knowledgeModel.Contains('TERRITORY_POLYGON') -and $knowledgeModel.Contains('record TerritoryGeometry') -and $knowledgeModel.Contains('additionalUnmappedTerritories')) "Game Knowledge territory model or partial-coverage evidence is incomplete."
	Assert-True ($knowledgeBuilder.Contains('ACTIVE_TARGET_DISTANCE = 2000') -and $knowledgeBuilder.Contains('exactTerritoryNode') -and $knowledgeBuilder.Contains('sameCycle') -and $knowledgeBuilder.Contains('hasFeasibleAnchor') -and $knowledgeBuilder.Contains('withUnmappedTerritories')) "Conservative exact territory mapping is incomplete."
	Assert-True ($knowledgeSnapshot.Contains('record TerritoryCoverage') -and $knowledgeSnapshot.Contains('unmappedDistanceInfeasibleTerritories') -and $knowledgeSnapshot.Contains('unmappedUnsupportedFacts')) "Bounded territory coverage metrics are incomplete."

	$catalogText = Read-TargetUtf8Strict 'dist/game/data/phantoms/acquisition/high-five-acquisition-v1.xml'
	[xml] $catalogXml = $catalogText
	Assert-True ($catalogXml.acquisitionPolicy.limits.activeTargetDistance -eq '2000') "activeTargetDistance was increased."
	$questText = Read-TargetUtf8Strict 'dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml'
	[xml] $questXml = $questText
	$rules = @($questXml.questCollectionCatalog.rule)
	Assert-True (($rules.Count -eq 2) -and (($rules | ForEach-Object { $_.scriptSha256 } | Sort-Object) -join '|') -eq 'cc3c1a893e6fe0763b806a17aa01e1d59a4c3f4743c3a577b2597bec07978d1f|e086d06935b0515142f431486ded1f71b8caa4843f69605296e64a4e8ffdf378') "Curated quest rule/script hashes changed."
	Assert-True ((@($rules.targets.target).Count -eq 3) -and ((@($rules.targets.target) | ForEach-Object { [int] $_.npcId } | Sort-Object) -join ',') -eq '20013,20016,20019') "Curated quest targets are not exactly 20013/20019/20016."

	$service = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java'
	$state = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionState.java'
	$manor = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/acquisition/manor/PhantomAcquisitionManorAuthority.java'
	$background = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java'
	$transaction = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java'
	$combatBackend = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java'
	$combatContract = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java'
	$runtimeSources = $service + "`n" + $manor + "`n" + $background
	Assert-True ($runtimeSources -notmatch '\.(?:addItem|destroyItem|setSeeded|takeHarvest)\s*\(') "Direct manor/quest inventory or corpse mutation bypass exists."
	Assert-True ($runtimeSources -notmatch '\.onKill\s*\(') "Production acquisition invokes Quest.onKill manually."
	Assert-True ($runtimeSources -notmatch '\b(?:ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.|new\s+Thread\s*\(') "Goal 021c2 owns a worker, executor, Future or task."
	Assert-True ($manor.Contains('Seed') -and $manor.Contains('Harvester') -and $manor.Contains('territoryGeometry()') -and $manor.Contains('topologyNodeId()')) "Manor authority bypasses canonical handlers or mapped factual sources."
	Assert-True ($service.Contains('ownsMappedTarget') -and $service.Contains('territoryGeometryHash()') -and $service.Contains('territorySourcePath()') -and $service.Contains('exactPointSpawn()')) "Exact active target territory ownership is incomplete."
	Assert-True ($combatContract.Contains('territoryGeometryHash') -and $combatContract.Contains('spawnTerritoryPresent') -and $combatContract.Contains('exactPointSpawn')) "Combat target snapshot lacks factual territory ownership."
	Assert-True ($combatBackend.Contains('geometrySnapshot()') -and $combatBackend.Contains('spawn.getSpawnTerritory()')) "Combat backend does not publish loaded target territory identity."
	Assert-True ($state.Contains('observeBound(') -and $state.Contains('observedProgress(baselineCount, authoritativeCount, requiredAmount)') -and $state.Contains('appendReceipt(receipts, receipt)') -and $state.Contains('completed ? Phase.NONE : nextPhase')) "Bounded active observation state boundary is incomplete."
	$externalObservation = $service.IndexOf('ReceiptKind.VERIFY, Phase.HARVEST_PREPARED')
	$dispatchGuard = $service.IndexOf('if (cropCount != manor.cropCountBeforeDispatch())')
	$harvesterDispatch = $service.IndexOf('lease.useExactHarvester', $dispatchGuard)
	Assert-True (($externalObservation -ge 0) -and ($dispatchGuard -ge 0) -and ($harvesterDispatch -gt $dispatchGuard) -and $service.Contains('kind == ReceiptKind.ACTIVE_MANOR_HARVEST ? binding.cropCountBeforeDispatch() : current.state().lastObservedCount()') -and $service.Contains('manor.inventory_inconsistent')) "External crop observation, pre-dispatch drift guard or handler-bound manor attribution is incomplete."
	Assert-True ($service.Contains('LongSupplier epochMillis') -and $service.Contains('System::currentTimeMillis') -and $service.Contains('_epochMillis.getAsLong()') -and $service.Contains('saturatingAdd(nowMillis, wait)') -and $service.Contains('remaining > 0') -and $service.Contains('remaining <= wait') -and ($service -notmatch 'logicalNowNanos\s*/\s*1_000_000')) "Restart-safe injected epoch callback deadline is incomplete."
	$callbackStart = $service.IndexOf('private OperationResult observeQuestCallback(')
	$callbackEnd = $service.IndexOf('private boolean exactQuestState(', $callbackStart)
	$callbackBody = if (($callbackStart -ge 0) -and ($callbackEnd -gt $callbackStart)) { $service.Substring($callbackStart, $callbackEnd - $callbackStart) } else { '' }
	$observationStart = $service.IndexOf('private OperationResult observeQuestCollection(')
	$observationEnd = $service.IndexOf('private static QuestBinding questBinding(', $observationStart)
	$observationBody = if (($observationStart -ge 0) -and ($observationEnd -gt $observationStart)) { $service.Substring($observationStart, $observationEnd - $observationStart) } else { '' }
	$verificationStart = $service.IndexOf('private OperationResult verifyCurrent(')
	$verificationEnd = $service.IndexOf('private OperationResult uncertain(', $verificationStart)
	$verificationBody = if (($verificationStart -ge 0) -and ($verificationEnd -gt $verificationStart)) { $service.Substring($verificationStart, $verificationEnd - $verificationStart) } else { '' }
	Assert-True ($callbackBody.Contains('observeQuestCollection(current, quest, rule, count, logicalMinute)') -and !$callbackBody.Contains('advanceToVerify(') -and !$callbackBody.Contains('uncertain(') -and $callbackBody.Contains('count > quest.itemCap()') -and $callbackBody.Contains('quest.item_cap')) "Successful or invalid quest callback still uses generic verification/uncertainty."
	Assert-True ($observationBody.Contains('binding.itemCountBeforeKill()') -and $observationBody.Contains('delta < rule.minimumCount()') -and $observationBody.Contains('delta > rule.maximumCount()') -and $observationBody.Contains('ReceiptKind.ACTIVE_QUEST_COLLECTION, before, count, TerminalResult.OBSERVED') -and $observationBody.Contains('observeBound(count, Status.READY') -and $observationBody.Contains('_store.mutateWithGoal')) "Dedicated exact quest observation/min-max/receipt boundary is incomplete."
	Assert-True ($state.Contains('(itemCountBeforeKill < 0) || (itemCountBeforeKill >= itemCap)') -and $observationBody.Contains('final boolean capReached = count == binding.itemCap()') -and $observationBody.Contains('capReached ? before : count') -and $observationBody.Contains('capReached ? Phase.NONE : Phase.TARGET_REQUIRED') -and $observationBody.Contains('observed.failSource("quest.item_cap", logicalMinute)') -and !$observationBody.Contains('questBinding(binding, count, 0)')) "Active quest cap boundary weakens the binding invariant or rebuilds an executable binding at cap."
	Assert-True ($verificationBody.Contains('current.state().selectedSource().method() == Method.QUEST_COLLECTION') -and $verificationBody.Contains('exactQuestRule(quest)') -and $verificationBody.Contains('exactQuestState(lease, quest, rule)') -and $verificationBody.Contains('observeQuestCollection(current, quest, rule, count, logicalMinute)')) "Legacy QUEST_COLLECTION/VERIFYING does not use exact curated validation."
	Assert-True ($service.Contains('count != quest.itemCountBeforeKill()') -and $service.Contains('quest.item_count_changed') -and $service.Contains('count >= quest.itemCap()') -and $service.IndexOf('count != quest.itemCountBeforeKill()') -lt $service.IndexOf('_combat.startAcquisitionSession')) "Quest pre-combat item baseline/cap revalidation is absent or occurs after Combat submission."
	Assert-True ($background.Contains('_acquisitionLimits.manorAttemptsPerTarget()') -and $background.Contains('_acquisitionLimits.harvestAttemptsPerCorpse()') -and !$background.Contains('projection.harvestPayload(), 3, 3')) "Background manor attempt policy is not catalog-driven."
	Assert-True ($transaction.Contains('LOCK_QUEST_ROWS') -and $transaction.Contains('ORDER BY var FOR UPDATE') -and $transaction.Contains('expectedQuestRows()') -and $transaction.Contains('AFTER_QUEST_LOCKS') -and $transaction.Contains('AFTER_GOAL_STATE_WRITE') -and $transaction.Contains('AFTER_ACQUISITION_STATE_WRITE') -and $transaction.Contains('connection.commit()')) "Atomic exact-row quest background ownership is incomplete."
	Assert-True ($transaction.Contains('if (count > quest.itemCap())') -and $transaction.Contains('throw new StateConflict(Status.ACQUISITION_CONFLICT)') -and $transaction.Contains('count == quest.itemCap() ? quest.itemCountBeforeKill() : count') -and $transaction.Contains('advanced.failSource("quest.item_cap", state.logicalMinute())') -and (($transaction -split 'advanceAcquisitionBinding').Count -ge 4)) "Background cap completion/partial/conflict or post-commit reconstruction is incomplete."
	Assert-True ($transaction -notmatch '(?i)(?:UPDATE|INSERT|DELETE)\s+character_quests') "Background quest path mutates quest state/cond/vars."

	$activeTests = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java'
	$backgroundTests = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java'
	$manorTests = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomAcquisitionManorSuite.java'
	$knowledgeTests = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java'
	$topologyTests = Read-TargetUtf8Strict 'test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java'
	Assert-True ($activeTests.Contains('List.of(20013, 20019, 20016)') -and $activeTests.Contains('setOnKillDelay(100)') -and $activeTests.Contains('granted && notGranted') -and $activeTests.Contains('ownsMappedTarget') -and $activeTests.Contains('another mapped territory') -and $activeTests.Contains('unmapped territory') -and $activeTests.Contains('exact-point Spawn')) "Real delayed quest callback or exact ownership controls are incomplete."
	Assert-True ($activeTests -notmatch '\.onKill\s*\(') "Active quest test invokes Quest.onKill manually."
	Assert-True ($manorTests.Contains('observeBound(') -and $manorTests.Contains('ReceiptKind.VERIFY') -and $manorTests.Contains('ReceiptKind.ACTIVE_MANOR_HARVEST') -and $manorTests.Contains('cropCountBeforeDispatch()') -and $manorTests.Contains('manor.variantSowAttempts') -and $manorTests.Contains('manor.variantHarvestAttempts')) "Manor refreshed overall/binding truth or policy-variant evidence is incomplete."
	Assert-True ($activeTests.Contains('runManorServiceAttribution') -and $activeTests.Contains('External crop delta was misattributed to Harvester') -and $activeTests.Contains('Successful Harvester receipt is absent') -and $activeTests.Contains('Service Harvester produced no bounded crop delta')) "Full acquisition-service manor attribution gate is absent."
	Assert-True ($activeTests.Contains('new PhantomAcquisitionService(') -and $activeTests.Contains('_acquisition.activeAdvance(') -and $activeTests.Contains('matchesAcquisitionSession') -and $activeTests.Contains('QUEST_COMBAT_PREPARED') -and $activeTests.Contains('QUEST_COMBAT_SUBMITTED') -and $activeTests.Contains('QUEST_COMBAT_TERMINAL') -and $activeTests.Contains('QUEST_CALLBACK_WAIT')) "Full acquisition-owned quest service phase chain is absent."
	Assert-True ($activeTests.Contains('_epochMillis::get') -and $activeTests.Contains('restartAcquisitionService()') -and $activeTests.Contains('Clock rollback beyond the wait window') -and $activeTests.Contains('Legacy/small deadline') -and $activeTests.Contains('Observed quest item was not committed before an expired deadline')) "Quest callback restart/rollback/legacy deadline gate is incomplete."
	Assert-True ($activeTests.Contains('01-real-delayed-on-attackable-kill') -and $activeTests.Contains('03-full-service-owned-combat-and-epoch-recovery')) "Direct Combat control replaced or obscured the full acquisition-service gate."
	foreach ($evidence in @('04-full-service-exact-delta-and-legacy-verifying', 'Below-cap +2 callback was accepted', 'Above-cap callback was accepted', 'Decreased callback count was accepted', 'Legacy VERIFYING +2 was accepted', 'Pre-combat quest item drift was accepted', 'A later inventory delta was absorbed by a second quest verification read', 'Acquisition changed curated quest state/cond/vars', 'Blocked pre-combat drift acquired a Combat session'))
	{
		Assert-True ($activeTests.Contains($evidence)) "Exact quest delta full-service evidence is absent: $evidence"
	}
	foreach ($evidence in @('assertQuestCapBoundary(source, Phase.QUEST_CALLBACK_WAIT, 1, true)', 'assertQuestCapBoundary(source, Phase.QUEST_CALLBACK_WAIT, 2, false)', 'assertQuestCapBoundary(source, Phase.VERIFYING, 1, true)', 'assertQuestCapBoundary(source, Phase.VERIFYING, 2, false)', 'Completion exactly at cap', 'Partial completion at cap', 'Exact cap schema-3 acquisition state did not round-trip', 'DirectiveKind.SWITCH', 'A subsequent active advance executed the exhausted quest source'))
	{
		Assert-True ($activeTests.Contains($evidence)) "Active quest cap-boundary evidence is absent: $evidence"
	}
	foreach ($evidence in @('01-real-model-transaction-quest-cap-boundaries', 'testQuestCapCommit(catalog, rule, 1, true)', 'testQuestCapCommit(catalog, rule, 2, false)', 'Post-commit verifier reconstruction is not byte-identical at the quest cap', 'Background partial completion at cap did not block the exhausted source', 'Exact background quest cap replay was not idempotent', 'Explicit background after > cap was not an acquisition conflict', 'changed audited quest rows'))
	{
		Assert-True ($backgroundTests.Contains($evidence)) "Background quest cap-boundary evidence is absent: $evidence"
	}
	Assert-True ($knowledgeTests.Contains('35') -and $knowledgeTests.Contains('15') -and $knowledgeTests.Contains('20') -and $knowledgeTests.Contains('additionalUnmappedTerritories')) "Game Knowledge 35/15/20 partial-coverage evidence is incomplete."
	Assert-True ($topologyTests.Contains('9') -and $topologyTests.Contains('7') -and $topologyTests.Contains('1') -and $topologyTests.Contains('4_000_000L')) "Topology 9/7/1 or distance evidence is incomplete."

	$build = Read-TargetUtf8Strict 'build.xml'
	Assert-True ($build.Contains('name="phantom.goal021c2.seed" value="21002102"') -and $build.Contains('name="phantom-acquisition-checkpoint2-test"') -and $build.Contains('-Dphantom.acquisition.focus=quest-cap') -and $build.Contains('acquisition-atomic-restart')) "Goal 021c2 seed, cap transaction route or final aggregate is absent."
	foreach ($dependency in @('phantom-topology-production-corpus-test', 'phantom-game-knowledge-parity-test', 'phantom-acquisition-manor-catalog-source-test', 'phantom-acquisition-manor-active-test', 'phantom-acquisition-manor-background-test', 'phantom-acquisition-manor-restart-transition-test', 'phantom-acquisition-quest-catalog-source-test', 'phantom-acquisition-quest-active-test', 'phantom-acquisition-quest-background-test', 'phantom-acquisition-checkpoint2-lifecycle-performance-smoke'))
	{
		Assert-True ($build.Contains($dependency)) "Final Goal 021c2 aggregate dependency is absent: $dependency"
	}
	$system = Read-TargetUtf8Strict 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Assert-True ($system.Contains('settings.enabled()') -and $system.Contains('if (!_settings.enabled())') -and $system.Contains('PhantomAcquisitionService')) "Phantom World disabled-mode composition changed."

	$report = Read-TargetUtf8Strict 'docs/phantoms/reports/021-checkpoint-2-manor-quest-acquisition.md'
	Assert-True (($report -split "`r?`n").Count -le 240) "Goal 021c2 report exceeds 240 lines."
	Assert-True ($report.Contains('COMPLETED_PENDING_INDEPENDENT_REVIEW') -and $report.Contains($AcceptedCheckpoint1) -and $report.Contains($AnchorCommit) -and $report.Contains($NearFinalCommit) -and $report.Contains($TerminalCommit) -and $report.Contains($ExactDeltaCommit) -and $report.Contains($RequiredSubject) -and $report.Contains('35') -and $report.Contains('15') -and $report.Contains('20') -and $report.Contains('active cap') -and $report.Contains('background cap')) "Goal 021c2 report/history/coverage handoff is incomplete."
	$reviewC2 = Read-TargetUtf8Strict 'docs/phantoms/reviews/021-checkpoint-2-independent-review.md'
	Assert-True ($reviewC2.Contains('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW') -and $reviewC2.Contains($TerminalCommit) -and $reviewC2.Contains($ExactDeltaCommit) -and $reviewC2.Contains($RequiredSubject) -and $reviewC2.Contains('independent')) "Goal 021c2 independent-review handoff is incomplete."

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B), @(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A), @(0x0420, 0x00A4), @(0x0420, 0x045A),
		@(0x0420, 0x0408), @(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5), @(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026), @(0x0421, 0x040F),
		@(0x0421, 0x20AC), @(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A), @(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026), @(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char] $_[0]) + ([string][char] $_[1])) }) -join '|'
	$replacementCharacter = [string][char] 0xFFFD
	$escapedCyrillic = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $finalPaths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1)$') -or ($path -eq 'build.xml'))
		{
			$text = Read-TargetUtf8Strict $path
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacementCharacter)) "Mojibake marker found in final file: $path"
			Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in final file: $path"
		}
	}

	$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
	& git merge-base --is-ancestor $script:TargetCommit $remote
	Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain accepted Goal 021c2."
	$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
	Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
	foreach ($entry in @(
		'org/l2jmobius/gameserver/model/zone/type/NpcSpawnTerritory$GeometrySnapshot.class',
		'org/l2jmobius/gameserver/phantoms/acquisition/manor/PhantomAcquisitionManorAuthority.class',
		'org/l2jmobius/gameserver/phantoms/acquisition/quest/PhantomAcquisitionQuestCatalog.class',
		'org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel$TerritoryGeometry.class'
	))
	{
		Assert-True ($jarEntries -contains $entry) "GameServer.jar lacks Goal 021c2 entry: $entry"
	}
	Assert-True ($jarEntries -notcontains 'data/phantoms/topology/high-five-core.xml') "Topology datapack must remain outside GameServer.jar."
	& git -c core.safecrlf=false diff --check $ExactDeltaCommit $script:TargetCommit --
	Assert-True ($LASTEXITCODE -eq 0) "Committed git diff --check failed."

	Write-Output 'TASK021C2_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "implementation_commit=$($script:TargetCommit)"
	Write-Output "accepted_parent=$ExactDeltaCommit"
	Write-Output "topology_sha256=$(Get-TargetSha256 'dist/game/data/phantoms/topology/high-five-core.xml')"
	Write-Output "quest_catalog_sha256=$(Get-TargetSha256 'dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml')"
	Write-Output "territories=35"
	Write-Output "mapped_feasible=15"
	Write-Output "unmapped_distance_infeasible=20"
	Write-Output "cumulative_scope=$($cumulativePaths.Count)"
	Write-Output "cumulative_production=$($cumulativeProduction.Count)"
	Write-Output "final_scope=$($finalPaths.Count)"
	Write-Output "final_production=$($finalProduction.Count)"
	Write-Output "final_new_production=$($finalNewProduction.Count)"
}
finally
{
	Pop-Location
}
