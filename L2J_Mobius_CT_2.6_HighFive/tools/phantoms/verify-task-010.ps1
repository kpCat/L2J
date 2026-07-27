param()

$ErrorActionPreference = "Stop"
$Base = "0780c77ae605d8b2c36a4ff0345092506fb9f9c5"
$Branch = "feature/phantom-world"
$ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$RepoRoot = (& git -C $ModuleRoot rev-parse --show-toplevel).Trim()
$ModuleName = Split-Path $ModuleRoot -Leaf
$PassCount = 0
$FailCount = 0

function Test-Gate
{
	param([string]$Id, [bool]$Condition, [string]$Detail)
	if ($Condition)
	{
		$script:PassCount++
		Write-Output ("PASS " + $Id + " :: " + $Detail)
	}
	else
	{
		$script:FailCount++
		Write-Output ("FAIL " + $Id + " :: " + $Detail)
	}
}

function Git-Text
{
	param([string[]]$Arguments)
	$previousPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"
	$output = & git -C $RepoRoot @Arguments 2>$null
	$exitCode = $LASTEXITCODE
	$ErrorActionPreference = $previousPreference
	if ($exitCode -ne 0)
	{
		throw ("git command failed with exit code " + $exitCode)
	}
	return (($output) -join "`n").Trim()
}

function Module-Path
{
	param([string]$RepositoryPath)
	$normalized = $RepositoryPath.Replace("\", "/")
	$prefix = $ModuleName + "/"
	if ($normalized.StartsWith($prefix))
	{
		return $normalized.Substring($prefix.Length)
	}
	return $normalized
}

function Read-Text
{
	param([string]$RelativePath)
	return [System.IO.File]::ReadAllText((Join-Path $ModuleRoot $RelativePath), [System.Text.UTF8Encoding]::new($false, $true))
}

$head = Git-Text @("rev-parse", "HEAD")
$currentBranch = Git-Text @("branch", "--show-current")
$commitCountText = Git-Text @("rev-list", "--count", ($Base + "..HEAD"))
$commitCount = if ($commitCountText) { [int]$commitCountText } else { -1 }
$phaseValid = ($head -eq $Base) -or ($commitCount -eq 1)
Test-Gate "repository.module-root" ((Split-Path $ModuleRoot -Leaf) -eq "L2J_Mobius_CT_2.6_HighFive") "High Five module root"
Test-Gate "repository.branch" ($currentBranch -eq $Branch) $currentBranch
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "accepted Goal 009A baseline"
Test-Gate "repository.one-ordinary-child" $phaseValid "baseline working tree or one ordinary child"
if ($commitCount -eq 1)
{
	$parent = Git-Text @("rev-parse", "HEAD^")
	$subject = Git-Text @("show", "-s", "--format=%s", "HEAD")
	Test-Gate "repository.parent" ($parent -eq $Base) $parent
	Test-Gate "repository.subject" ($subject -eq "feat(phantoms): add topology perception graph") $subject
}
else
{
	Test-Gate "repository.parent" ($head -eq $Base) "pre-commit baseline"
	Test-Gate "repository.subject" ($head -eq $Base) "pre-commit working tree"
}
$remoteRef = Git-Text @("rev-parse", ("origin/" + $Branch))
Test-Gate "repository.remote-ref" (($remoteRef -eq $Base) -or ($remoteRef -eq $head)) $remoteRef

$changed = New-Object System.Collections.Generic.HashSet[string]
$committedDiff = Git-Text @("diff", "--name-only", ($Base + "...HEAD"))
foreach ($line in ($committedDiff -split "`r?`n"))
{
	if ($line) { [void]$changed.Add((Module-Path $line)) }
}
$workingDiff = Git-Text @("diff", "--name-only")
foreach ($line in ($workingDiff -split "`r?`n"))
{
	if ($line) { [void]$changed.Add((Module-Path $line)) }
}
$stagedDiff = Git-Text @("diff", "--cached", "--name-only")
foreach ($line in ($stagedDiff -split "`r?`n"))
{
	if ($line) { [void]$changed.Add((Module-Path $line)) }
}
$untrackedFiles = Git-Text @("ls-files", "--others", "--exclude-standard")
foreach ($line in ($untrackedFiles -split "`r?`n"))
{
	if ($line) { [void]$changed.Add((Module-Path $line)) }
}
$changedFiles = @($changed | Sort-Object)
$allowedPattern = "^(java/org/l2jmobius/gameserver/phantoms/topology/.+\.java|java/org/l2jmobius/gameserver/phantoms/PhantomSystem\.java|java/org/l2jmobius/gameserver/phantoms/PhantomMetrics\.java|java/org/l2jmobius/gameserver/Shutdown\.java|dist/game/data/phantoms/topology/.+\.xml|build\.xml|test/java/org/l2jmobius/tests/phantoms/(PhantomTestLauncher|PhantomTopologyCoreSuite|PhantomTopologyPerceptionSuite|PhantomTopologyProductionCorpusSuite|PhantomTopologyPerformanceSuite|PhantomServerShutdownHandoffSuite|PhantomSkeletonSuite)\.java|tools/phantoms/verify-task-010\.ps1|docs/PHANTOM_BOTS_ROADMAP\.md|docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT\.md|docs/phantoms/tasks/010-topology-anchors-perception-graph/.+|docs/phantoms/reports/(009a-navigation-route-ownership-hardening|010-topology-anchors-perception-graph)\.md|docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review\.md)$"
$outside = @($changedFiles | Where-Object { $_ -notmatch $allowedPattern })
Test-Gate "scope.changed-files" ($changedFiles.Count -ge 10) ($changedFiles.Count.ToString() + " scoped artifacts")
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "exact" } else { $outside -join "," })
Test-Gate "scope.high-five-only" (@($changedFiles | Where-Object { $_ -match "L2J_Mobius_CT_" }).Count -eq 0) "High Five module only"
Test-Gate "scope.no-config-schema-goal011-012" (@($changedFiles | Where-Object { $_ -match "^dist/game/config/|^dist/db_installer/|/011-|/012-" }).Count -eq 0) "frozen"
Test-Gate "scope.no-binaries" (@($changedFiles | Where-Object { $_ -match "\.(jar|class|dll|exe|zip|7z|png|jpg)$" }).Count -eq 0) "no task binaries"

$requiredArtifacts = @(
	"dist/game/data/phantoms/topology/high-five-core.xml",
	"docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md",
	"docs/phantoms/reports/010-topology-anchors-perception-graph.md",
	"docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review.md",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java",
	"tools/phantoms/verify-task-010.ps1"
)
foreach ($artifact in $requiredArtifacts)
{
	Test-Gate ("artifact." + $artifact) (Test-Path -LiteralPath (Join-Path $ModuleRoot $artifact) -PathType Leaf) $artifact
}

$frozenPaths = @(
	"java/org/l2jmobius/gameserver/data/xml/MapRegionData.java",
	"java/org/l2jmobius/gameserver/data/xml/NpcData.java",
	"java/org/l2jmobius/gameserver/data/xml/SpawnData.java",
	"java/org/l2jmobius/gameserver/data/SpawnTable.java",
	"java/org/l2jmobius/gameserver/data/xml/DoorData.java",
	"java/org/l2jmobius/gameserver/model/actor/instance/Door.java",
	"java/org/l2jmobius/gameserver/model/World.java",
	"java/org/l2jmobius/gameserver/phantoms/navigation",
	"java/org/l2jmobius/gameserver/phantoms/decision",
	"java/org/l2jmobius/gameserver/phantoms/player",
	"java/org/l2jmobius/gameserver/phantoms/profile",
	"java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
)
foreach ($path in $frozenPaths)
{
	$diff = Git-Text @("diff", "--name-only", $Base, "--", ($ModuleName + "/" + $path))
	Test-Gate ("frozen." + $path) ([string]::IsNullOrEmpty($diff)) "unchanged"
}

$topologyFiles = Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "java/org/l2jmobius/gameserver/phantoms/topology") -Filter "*.java" | Sort-Object Name
$topologySource = ($topologyFiles | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName) }) -join "`n"
$loaderSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyLoader.java"
$snapshotSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySnapshot.java"
$querySource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyQuery.java"
$backendSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/L2jTopologyValidationBackend.java"
$registrySource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java"
$providerSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java"
$adapterSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomSchedulerRelevanceSignalPort.java"
$systemSource = Read-Text "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$shutdownSource = Read-Text "java/org/l2jmobius/gameserver/Shutdown.java"

Test-Gate "topology.schema-strict" ($loaderSource.Contains("schemaVersion") -and $loaderSource.Contains("requireAttributes") -and $loaderSource.Contains("Unknown topology")) "strict schema/element/attribute"
Test-Gate "topology.fixed-bounds" ($topologySource.Contains("100_000") -and $topologySource.Contains("200_000") -and $topologySource.Contains("maximumHierarchyDepth")) "entity/query bounds"
Test-Gate "topology.geometry" ($topologySource.Contains("POINT_RADIUS") -and $topologySource.Contains("CUBOID") -and $topologySource.Contains("POLYGON") -and $topologySource.Contains("self-intersecting")) "supported bounded geometry"
Test-Gate "topology.canonical-hash" ($snapshotSource.Contains("SHA-256") -and $snapshotSource.Contains("sorted(NODE_ORDER)") -and $snapshotSource.Contains("sorted(ANCHOR_ORDER)") -and $snapshotSource.Contains("sorted(EDGE_ORDER)")) "sorted semantic SHA-256"
Test-Gate "topology.atomic-reload" ($topologySource.Contains("Invalid reload") -or (($topologySource.Contains("ReloadResult")) -and $topologySource.Contains("_snapshot = candidate"))) "candidate swap only after validation"
Test-Gate "topology.factual-map-region" ($backendSource.Contains("MapRegionData.getInstance().getMapRegionLocId")) "MapRegionData"
Test-Gate "topology.factual-npc" ($backendSource.Contains("NpcData.getInstance().getTemplate")) "NpcData"
Test-Gate "topology.factual-spawn" ($backendSource.Contains("SpawnData.getInstance()") -and $backendSource.Contains("SpawnTable.getInstance().getSpawns")) "SpawnData/SpawnTable"
Test-Gate "topology.factual-door" ($backendSource.Contains("DoorData.getInstance().getDoor")) "DoorData"
Test-Gate "topology.no-name-role-inference" (($topologySource -notmatch "getName\(") -and ($topologySource -notmatch "getTitle\(")) "no localized name/title lookup"
Test-Gate "topology.immutable-indexes" ($snapshotSource.Contains("Map.copyOf") -and $snapshotSource.Contains("List.copyOf") -and $snapshotSource.Contains("_nodeSpatial") -and $snapshotSource.Contains("_edgesByNode")) "immutable spatial/adjacency indexes"
Test-Gate "topology.query-bounds" ($querySource.Contains("maximumReturnedNodes") -and $querySource.Contains("maximumReturnedEdges") -and $querySource.Contains("maximumGraphNodes")) "64/1024/256"
Test-Gate "topology.live-door-overlay" ($querySource.Contains("_backend.doorState") -and ($snapshotSource -notmatch "DoorState")) "live state outside snapshot"
Test-Gate "topology.explicit-profile-only" ($registrySource.Contains("register(long profileId)") -and ($registrySource -notmatch "World|getPlayers|Repository")) "explicit registry"
Test-Gate "topology.monotonic-position" ($registrySource.Contains("sequence <= entry._sequence") -and $registrySource.Contains("_profilesByNode")) "stale reject and atomic membership"
Test-Gate "perception.fixed-sources" ($providerSource.Contains("topology.local_chat") -and $providerSource.Contains("topology.combat") -and $providerSource.Contains("topology.targetability")) "fixed scheduler keys"
Test-Gate "perception.one-hop" ($providerSource.Contains("query.edges(eventNode.get().id())") -and ($providerSource -notmatch "routeHint|ArrayDeque")) "event node plus direct edges"
Test-Gate "perception.minimum" ($providerSource.Contains("NEARBY_PERCEPTIBLE") -and $providerSource.Contains("PhantomActivityState.ACTIVE") -and $providerSource.Contains("requiredState.code() >")) "hard minimum and ACTIVE"
Test-Gate "perception.narrow-port" ($adapterSource.Contains("submitSignal") -and $adapterSource.Contains("withdrawSignal") -and ($adapterSource -notmatch "\.register\(|\.unregister\(")) "submit/withdraw only"
Test-Gate "perception.stop-token" ($providerSource.Contains("_eventsInFlight") -and $providerSource.Contains("_deliveryGate") -and $providerSource.Contains("_eventGeneration") -and $providerSource.Contains("finishStop")) "generation token and quiescence"
Test-Gate "perception.no-direct-action" (($providerSource -notmatch "PhantomMaterialization|PhantomNavigation|Player|Creature")) "no materialization/navigation/actor reference"
Test-Gate "runtime.no-new-executor-thread-future" ($topologySource -notmatch "\b(Thread|Executor|ScheduledFuture|CompletableFuture)\b") "caller-thread synchronous only"

[xml]$seedXml = Read-Text "dist/game/data/phantoms/topology/high-five-core.xml"
$root = $seedXml.topology
$nodes = @($root.node)
$anchors = @($root.anchor)
$edges = @($root.edge)
Test-Gate "corpus.schema-version" (($root.schemaVersion -eq "1") -and ($root.datasetId -eq "high-five-core") -and ($root.datasetVersion -eq "1")) "high-five-core v1"
Test-Gate "corpus.counts" (($nodes.Count -eq 8) -and ($anchors.Count -eq 8) -and ($edges.Count -eq 3)) "8 nodes / 8 anchors / 3 edges"
Test-Gate "corpus.city-npc-farming" (($anchors.npcId -contains "30080") -and ($anchors.npcId -contains "30081") -and ($anchors.npcId -contains "22859") -and ($anchors.mapRegionLocId -contains "918")) "Giran and Monster facts"
Test-Gate "corpus.factual-room-door" (($edges.doorId -contains "17240102") -and ($nodes.kind -contains "ROOM") -and ($nodes.kind -contains "CORRIDOR")) "SSQ factual door passage"
Test-Gate "corpus.background-perception" (($edges.mode -contains "BACKGROUND") -and (($edges.channels -join ",").Contains("LOCAL_CHAT")) -and (($edges.channels -join ",").Contains("COMBAT")) -and (($edges.channels -join ",").Contains("TARGETABILITY"))) "background edge and three channels"
$sourceCount = @($root.SelectNodes("//source[@path]")).Count
Test-Gate "corpus.source-evidence" ($sourceCount -ge ($nodes.Count + $anchors.Count + $edges.Count)) ($sourceCount.ToString() + " source references")

$coreTests = ([regex]::Matches((Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java"), "registry\.add\(")).Count
$perceptionTests = ([regex]::Matches((Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java"), "registry\.add\(")).Count
$performanceSource = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java"
$corpusTestSource = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java"
$buildSource = Read-Text "build.xml"
$launcherSource = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
Test-Gate "tests.core-at-least-30" ($coreTests -ge 30) ($coreTests.ToString() + " cases")
Test-Gate "tests.perception-at-least-22" ($perceptionTests -ge 22) ($perceptionTests.ToString() + " cases")
Test-Gate "tests.performance-shape" ($performanceSource.Contains("10_000") -and $performanceSource.Contains("20_000") -and $performanceSource.Contains("50_000") -and $performanceSource.Contains("EVENT_COUNT = 1000")) "10k/20k/50k/10k/1000+1000"
Test-Gate "tests.corpus-current-loaders" ($corpusTestSource.Contains("L2jTopologyValidationBackend") -and $corpusTestSource.Contains("SpawnData.getInstance") -and $corpusTestSource.Contains("DoorData.getInstance")) "real High Five loaders"
Test-Gate "build.topology-routes" (($buildSource -match "phantom-topology-core-test") -and ($buildSource -match "phantom-topology-perception-test") -and ($buildSource -match "phantom-topology-production-corpus-test") -and ($buildSource -match "phantom-topology-performance-smoke") -and ($launcherSource -match "topology-core") -and ($launcherSource -match "topology-perception") -and ($launcherSource -match "topology-corpus") -and ($launcherSource -match "topology-performance")) "four targeted routes"

$startRepository = $systemSource.IndexOf("PhantomProfileRepository.open()")
$startMaterialization = $systemSource.IndexOf("_materializationService.start()", $startRepository)
$startDecision = $systemSource.IndexOf("_decisionEngine.start()", $startMaterialization)
$startNavigation = $systemSource.IndexOf("_navigationService.start()", $startDecision)
$startTopology = $systemSource.IndexOf("_topologyService.start()", $startNavigation)
$startScheduler = $systemSource.IndexOf("_scheduler.start()", $startTopology)
Test-Gate "system.start-order" (($startRepository -ge 0) -and ($startRepository -lt $startMaterialization) -and ($startMaterialization -lt $startDecision) -and ($startDecision -lt $startNavigation) -and ($startNavigation -lt $startTopology) -and ($startTopology -lt $startScheduler)) "repository/materialization/decision/navigation/topology/scheduler"
$shutdownRunning = $systemSource.IndexOf("if (_state == State.RUNNING)")
$beginScheduler = $systemSource.IndexOf("_scheduler.beginStop()", $shutdownRunning)
$beginTopology = $systemSource.IndexOf("_topologyService.beginStop()", $beginScheduler)
$beginDecision = $systemSource.IndexOf("_decisionEngine.beginStop()", $beginTopology)
$beginNavigation = $systemSource.IndexOf("_navigationService.beginStop()", $beginDecision)
$drainMaterialization = $systemSource.IndexOf("_materializationService.shutdown()", $beginNavigation)
$finishScheduler = $systemSource.IndexOf("_scheduler.finishStop()", $drainMaterialization)
$finishTopology = $systemSource.IndexOf("_topologyService.finishStop()", $finishScheduler)
$finishDecision = $systemSource.IndexOf("_decisionEngine.finishStop()", $finishTopology)
$finishNavigation = $systemSource.IndexOf("_navigationService.finishStop()", $finishDecision)
Test-Gate "system.shutdown-order" (($beginScheduler -lt $beginTopology) -and ($beginTopology -lt $beginDecision) -and ($beginDecision -lt $beginNavigation) -and ($beginNavigation -lt $drainMaterialization) -and ($drainMaterialization -lt $finishScheduler) -and ($finishScheduler -lt $finishTopology) -and ($finishTopology -lt $finishDecision) -and ($finishDecision -lt $finishNavigation)) "required subsystem order"
Test-Gate "system.disabled-inert" ($systemSource.Contains("if (!_settings.enabled())") -and $systemSource.Contains("_state = State.DISABLED")) "no topology construction on disabled path"
Test-Gate "shutdown.topology-aggregate" ($systemSource.Contains("topologyRegisteredProfiles") -and $systemSource.Contains("topologyEventsInFlight") -and $systemSource.Contains("topologyGeneration") -and $shutdownSource.Contains("topologyState") -and $shutdownSource.Contains("topologyEventsInFlight")) "bounded topology shutdown truth"
Test-Gate "shutdown.two-server-attempts" (([regex]::Matches($shutdownSource, "PhantomSystem\.shutdownIfStarted\(\)")).Count -eq 2) "exact two calls"

$review009a = Read-Text "docs/phantoms/reviews/009a-navigation-route-ownership-hardening-review.md"
$report009a = Read-Text "docs/phantoms/reports/009a-navigation-route-ownership-hardening.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
$contract = Read-Text "docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md"
Test-Gate "docs.goal009a-closure" ($review009a.Contains("Goal 009A: ACCEPT") -and $review009a.Contains("Goal 010: ALLOWED") -and $report009a.Contains("Final verifier: 56/56")) "immutable accepted handoff"
Test-Gate "docs.roadmap-progress" ($roadmap.Contains("0780c77ae605d8b2c36a4ff0345092506fb9f9c5") -and $roadmap.Contains("Goal 010: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 011: NOT_STARTED")) "progress only"
Test-Gate "docs.topology-contract" ($contract.Contains("NEARBY_PERCEPTIBLE") -and $contract.Contains("direct edge") -and $contract.Contains("Game Knowledge")) "architecture boundary"

$utf8Valid = $true
$mojibakeHits = New-Object System.Collections.Generic.List[string]
$escapedHits = New-Object System.Collections.Generic.List[string]
$mojibakeMarkers = @(
	"0KDRnw==", "0KDRnA==", "0KDRmw==", "0KDigKI=", "0KDQjg==", "0KDigLo=", "0KDCpA==", "0KDRmg==", "0KDQiA==",
	"0KDRmQ==", "0KDSkQ==", "0KDCtQ==", "0KDCsA==", "0KDCuw==", "0KDQhQ==", "0KDRlQ==", "0KHQjw==", "0KHigqw=",
	"0KHQgg==", "0KHigLk=", "0KHQig==", "0KHigJo=", "0KHRkw==", "0KHigKE=", "0KHigKY=", "0KHigKA=", "77+9"
) | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }
$escapedPattern = "\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};"
foreach ($file in $changedFiles)
{
	$absolute = Join-Path $ModuleRoot $file
	if (!(Test-Path -LiteralPath $absolute -PathType Leaf)) { continue }
	try
	{
		$text = [System.Text.UTF8Encoding]::new($false, $true).GetString([System.IO.File]::ReadAllBytes($absolute))
	}
	catch
	{
		$utf8Valid = $false
		continue
	}
	foreach ($marker in $mojibakeMarkers)
	{
		if ($text.Contains($marker))
		{
			$mojibakeHits.Add($file)
			break
		}
	}
	if ($text -match $escapedPattern)
	{
		$escapedHits.Add($file)
	}
}
Test-Gate "encoding.valid-utf8" $utf8Valid ($changedFiles.Count.ToString() + " text artifacts")
Test-Gate "encoding.no-mojibake-markers" ($mojibakeHits.Count -eq 0) $(if ($mojibakeHits.Count -eq 0) { "none" } else { ($mojibakeHits | Sort-Object -Unique) -join "," })
Test-Gate "encoding.no-escaped-cyrillic" ($escapedHits.Count -eq 0) $(if ($escapedHits.Count -eq 0) { "none" } else { ($escapedHits | Sort-Object -Unique) -join "," })

$securityText = $topologySource + "`n" + (Read-Text "dist/game/data/phantoms/topology/high-five-core.xml") + "`n" + $contract
Test-Gate "security.no-credentials" ($securityText -notmatch "(?i)(password\s*=|jdbc:mysql|root/root|api[_-]?key)") "none"
$verifierSource = Read-Text "tools/phantoms/verify-task-010.ps1"
Test-Gate "verifier.read-only" ($verifierSource -notmatch "(?im)^\s*(Set-Content|Add-Content|Out-File|Remove-Item|Copy-Item|Move-Item|New-Item)\b") "no filesystem mutation command"
$nondeterministicPattern = ("Get" + "-Date|" + "Ran" + "dom|Start" + "-Sleep")
Test-Gate "verifier.deterministic" ($verifierSource -notmatch $nondeterministicPattern) "no volatile output"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
if (Test-Path -LiteralPath $jarPath)
{
	$jarEntries = (& jar tf $jarPath) -join "`n"
	Test-Gate "jar.topology-entries" ($jarEntries.Contains("org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.class")) "production topology classes present"
	Test-Gate "jar.no-test-entries" ($jarEntries -notmatch "org/l2jmobius/tests/phantoms") "tests excluded"
}
else
{
	Test-Gate "jar.topology-entries" $false "GameServer.jar missing"
	Test-Gate "jar.no-test-entries" $false "GameServer.jar missing"
}

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
