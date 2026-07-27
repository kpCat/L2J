param()

$ErrorActionPreference = "Stop"
$Base = "7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2"
$Branch = "feature/phantom-world"
$ExpectedSubject = "feat(phantoms): add authoritative game knowledge"
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

function Count-Matches
{
	param([string]$Text, [string]$Pattern)
	return ([regex]::Matches($Text, $Pattern)).Count
}

$head = Git-Text @("rev-parse", "HEAD")
$branch = Git-Text @("branch", "--show-current")
$commitCount = [int](Git-Text @("rev-list", "--count", ($Base + "..HEAD")))
$phaseValid = ($head -eq $Base) -or ($commitCount -eq 1)
$parentValid = ($head -eq $Base) -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Base)
$subjectValid = ($head -eq $Base) -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $ExpectedSubject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))
Test-Gate "repository.module-root" ((Split-Path $ModuleRoot -Leaf) -eq "L2J_Mobius_CT_2.6_HighFive") "High Five module"
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 010C base exists"
Test-Gate "repository.one-ordinary-child" $phaseValid "baseline worktree or one child"
Test-Gate "repository.parent" $parentValid "exact parent"
Test-Gate "repository.subject" $subjectValid "exact subject after commit"
Test-Gate "repository.remote-phase" (($remote -eq $Base) -or ($remote -eq $head)) "base before push or exact head"

$changed = New-Object System.Collections.Generic.HashSet[string]
foreach ($arguments in @(
	@("diff", "--name-only", ($Base + "...HEAD")),
	@("diff", "--name-only"),
	@("diff", "--cached", "--name-only"),
	@("ls-files", "--others", "--exclude-standard")
))
{
	foreach ($line in ((Git-Text $arguments) -split "`r?`n"))
	{
		if ($line)
		{
			[void]$changed.Add((Module-Path $line))
		}
	}
}
$changedFiles = @($changed | Sort-Object)
$exactFiles = @(
	"build.xml",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java",
	"tools/phantoms/verify-task-011.ps1",
	"dist/game/data/stats/npcs/29100-29199.xml",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md",
	"docs/phantoms/reports/010c-topology-absent-source-reconciliation.md",
	"docs/phantoms/reports/011-authoritative-game-knowledge.md",
	"docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md"
)
$outside = @($changedFiles | Where-Object {
	($_ -notin $exactFiles) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/knowledge/*") -and
	($_ -notlike "dist/game/data/phantoms/knowledge/*") -and
	($_ -notlike "docs/phantoms/tasks/011-authoritative-game-knowledge/*")
})
Test-Gate "scope.changed-artifacts" ($changedFiles.Count -ge 25) ($changedFiles.Count.ToString() + " artifacts")
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "exact" } else { $outside -join "," })
Test-Gate "scope.high-five-only" (@($changedFiles | Where-Object { $_ -match "L2J_Mobius_CT_" }).Count -eq 0) "module-local paths"
Test-Gate "scope.no-config" (@($changedFiles | Where-Object { $_ -like "dist/game/config/*" }).Count -eq 0) "config frozen"
Test-Gate "scope.no-schema" (@($changedFiles | Where-Object { $_ -like "dist/db_installer/*" }).Count -eq 0) "DB schema frozen"
Test-Gate "scope.no-goal-012-013" (@($changedFiles | Where-Object { ($_ -match "/012-") -or ($_ -match "/013-") }).Count -eq 0) "future goals absent"
Test-Gate "scope.no-binaries" (@($changedFiles | Where-Object { $_ -match "\.(class|jar|zip|7z|dll|exe|png|jpg)$" }).Count -eq 0) "no binary artifacts"

$requiredArtifacts = @(
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomStaticManorParser.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomCuratedKnowledgeParser.java",
	"dist/game/data/phantoms/knowledge/high-five-core-v1.xml",
	"dist/game/data/stats/npcs/29100-29199.xml",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java",
	"docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md",
	"docs/phantoms/reports/011-authoritative-game-knowledge.md",
	"docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md"
)
foreach ($artifact in $requiredArtifacts)
{
	Test-Gate ("artifact." + $artifact) (Test-Path -LiteralPath (Join-Path $ModuleRoot $artifact) -PathType Leaf) $artifact
}

$frozenGroups = [ordered]@{
	"server-loaders" = @(
		"java/org/l2jmobius/gameserver/data/xml/ItemData.java",
		"java/org/l2jmobius/gameserver/data/xml/NpcData.java",
		"java/org/l2jmobius/gameserver/data/xml/SpawnData.java",
		"java/org/l2jmobius/gameserver/data/SpawnTable.java",
		"java/org/l2jmobius/gameserver/data/xml/RecipeData.java",
		"java/org/l2jmobius/gameserver/data/xml/SkillTreeData.java",
		"java/org/l2jmobius/gameserver/data/xml/SkillData.java"
	)
	"topology" = @("java/org/l2jmobius/gameserver/phantoms/topology", "dist/game/data/phantoms/topology")
	"navigation" = @("java/org/l2jmobius/gameserver/phantoms/navigation")
	"decision" = @("java/org/l2jmobius/gameserver/phantoms/decision")
	"scheduler" = @("java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java", "java/org/l2jmobius/gameserver/phantoms/activity")
	"materialization" = @("java/org/l2jmobius/gameserver/phantoms/player")
	"profile" = @("java/org/l2jmobius/gameserver/phantoms/profile")
	"config" = @("dist/game/config")
	"schema" = @("dist/db_installer")
}
foreach ($group in $frozenGroups.GetEnumerator())
{
	$paths = @($group.Value | ForEach-Object { $ModuleName + "/" + $_ })
	$diff = Git-Text (@("diff", "--name-only", $Base, "--") + $paths)
	Test-Gate ("frozen." + $group.Key) ([string]::IsNullOrEmpty($diff)) "unchanged from Goal 010C"
}

$authority = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeAuthority.java"
$model = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java"
$policy = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgePolicy.java"
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java"
$manor = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomStaticManorParser.java"
$curatedParser = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomCuratedKnowledgeParser.java"
$builder = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java"
$snapshot = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java"
$query = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java"
$metrics = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeMetrics.java"
$system = Read-Text "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$coreTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java"
$parityTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java"
$contentTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java"
$performanceTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"
$curatedPath = Join-Path $ModuleRoot "dist/game/data/phantoms/knowledge/high-five-core-v1.xml"
[xml]$curatedXml = [System.IO.File]::ReadAllText($curatedPath, [System.Text.Encoding]::UTF8)

foreach ($name in @("SERVER_LOADER_FACT", "STATIC_DATAPACK_FACT", "TOPOLOGY_SNAPSHOT_FACT", "CURATED_RECOMMENDATION"))
{
	Test-Gate ("authority." + $name.ToLowerInvariant()) ($authority.Contains($name)) $name
}
Test-Gate "model.raw-drop-semantics" ($model.Contains("rawGroupChance") -and $model.Contains("rawItemChance") -and $model.Contains("minimumCount") -and $model.Contains("maximumCount") -and $model.Contains("GROUP_CUMULATIVE") -and $model.Contains("UNGROUPED_INDEPENDENT")) "raw grouped/ungrouped fields"
Test-Gate "model.no-effective-chance" (($model + $builder + $query) -notmatch "(?i)effectiveChance|runtimeChance|killProbability|expectedValue") "no invented runtime probability"
Test-Gate "model.immutable-values" ((Count-Matches $model "\brecord\b") -ge 15 -and $model.Contains("List.copyOf") -and $model.Contains("Set.copyOf")) "records and defensive copies"
Test-Gate "model.no-mutable-server-types" (($model + $snapshot + $query) -notmatch "\b(Player|Creature|NpcTemplate|ItemTemplate|RecipeList|org\.l2jmobius\.gameserver\.model\.spawns\.Spawn|org\.l2jmobius\.gameserver\.model\.skill\.Skill)\b") "public values exclude mutable server objects"
Test-Gate "policy.fixed-bounds" ($policy.Contains("4096, 100000, 100000, 2000000, 1000000, 100000, 1000000, 100000, 50000, 4096, 64, 16, 32, 256, 100, 64, 256, 512, 96")) "all fixed production defaults"
Test-Gate "policy.no-config" (($policy + $builder + $service) -notmatch "PhantomPlayersConfig|ConfigReader|\.ini") "no Goal 011 config keys"

Test-Gate "backend.items" ($backend.Contains("ItemData.getInstance().getAllItems()")) "all non-null loaded items"
Test-Gate "backend.npcs" ($backend.Contains("NpcData.getInstance().getTemplates(_ -> true)")) "all loaded NPC templates"
Test-Gate "backend.drop-groups" ($backend.Contains("getDropGroups()") -and $backend.Contains("getDropList()") -and $backend.Contains("getSpoilList()")) "grouped, ungrouped and spoil"
Test-Gate "backend.spawns" ($backend.Contains("SpawnTable.getInstance().getSpawnTable()")) "loaded SpawnTable copy"
Test-Gate "backend.spawn-random-sentinel" ($backend.Contains("spawn.getLocationId() == 0") -and $backend.Contains("spawn.getSpawnLocation()") -and $backend.Contains("exact ? loadedX : 0") -and $parityTests.Contains("runtime-random X coordinate")) "territory/location/runtime-offset randomness excluded from canonical facts"
Test-Gate "backend.recipes" ($backend.Contains("getAllItemIds()") -and $backend.Contains("getRecipeByItemId")) "unique RecipeData copy"
Test-Gate "backend.class-skill-evidence" ($backend.Contains("getCompleteClassSkillTree") -and $backend.Contains("SkillData.getInstance()") -and $backend.Contains("PlayerClass.values()")) "mechanical class/skill evidence"
Test-Gate "backend.null-empty-parity" ($backend.Contains("dropGroups == null") -and $backend.Contains("if (source == null)")) "nullable empty loader lists handled"
Test-Gate "backend.no-db-manager" (($backend + $manor + $builder) -notmatch "CastleManorManager|DimensionalRiftManager|DatabaseFactory|java\.sql") "no mutable manager or DB access"

Test-Gate "manor.static-seeds" ($manor.Contains("Strict static parser for data/Seeds.xml") -and $manor.Contains('new ManorFact') -and $manor.Contains('"data/Seeds.xml"')) "dedicated static Seeds.xml parser"
Test-Gate "manor.strict-xml" ($manor.Contains("disallow-doctype-decl") -and $manor.Contains("requireExactAttributes") -and $manor.Contains("Unknown Seeds.xml")) "strict secure XML"
Test-Gate "curated.strict-version" ($curatedParser.Contains('!"knowledge".equals') -and $curatedParser.Contains("schemaVersion") -and $curatedParser.Contains("Unsupported curated knowledge schemaVersion")) "strict versioned XML"
Test-Gate "curated.unknown-and-duplicate" ($curatedParser.Contains("Unknown curated knowledge element") -and $curatedParser.Contains("Duplicate curated class capability identity") -and $curatedParser.Contains("Duplicate curated content identity")) "unknown fields and duplicates rejected"
Test-Gate "curated.source-evidence" ($curatedParser.Contains("_backend.sourceExists(source)") -and $curatedParser.Contains("maximumEvidenceReferences") -and $curatedParser.Contains("maximumEvidenceSkills")) "bounded existing source evidence"

foreach ($index in @(
	"itemById", "npcById", "dropSourcesByItem", "spoilSourcesByItem", "manorFactsByItem",
	"dropFactsByNpc", "spoilFactsByNpc", "spawnFactsByNpc", "spawnAreasByNpc",
	"npcsByTopologyNode", "npcsByMapRegion", "npcsByLevel", "recipeByListId",
	"recipesByProduct", "recipesByIngredient", "classFactsByClassId",
	"classesByCapability", "contentById", "contentByCapability"
))
{
	Test-Gate ("index." + $index) ($snapshot.Contains($index)) $index
}
Test-Gate "index.complete-no-truncation" ($snapshot.Contains("List.copyOf") -and !$snapshot.Contains(".limit(") -and $builder.Contains("exceeds Game Knowledge policy")) "complete indexes or build rejection"
Test-Gate "spawn.topology-mapping" ($builder.Contains("_topology.mostSpecificNode") -and $builder.Contains("SpawnPointKind.EXACT") -and $backend.Contains("SpawnPointKind.TERRITORY_OR_UNRESOLVED")) "exact-only accepted topology mapping"
Test-Gate "spawn.outside-world-unmapped" ($builder.Contains("isWithinWorldBounds") -and $coreTests.Contains("spawn-outside-world-preserved-unmapped")) "outside-world exact facts preserved without fabricated topology"
Test-Gate "spawn.bounded-representatives" ($builder.Contains("maximumSpawnSamples") -and $builder.Contains("_amount = Math.addExact(_amount, fact.amount())")) "full area totals and bounded representatives"
Test-Gate "recipe.reverse-graph" ($snapshot.Contains("buildRecipeProductIndex") -and $snapshot.Contains("buildRecipeIngredientIndex") -and $snapshot.Contains("rareProductItemId")) "product and ingredient edges"

foreach ($hash in @("itemsHash", "npcDropSpoilHash", "spawnHash", "recipeHash", "manorHash", "classCapabilityHash", "contentRequirementHash", "topologyHash", "combinedHash"))
{
	Test-Gate ("hash." + $hash) ($snapshot.Contains($hash)) $hash
}
Test-Gate "hash.sha256-length-prefix" ($snapshot.Contains('MessageDigest.getInstance("SHA-256")') -and $snapshot.Contains("integer(bytes.length)") -and $snapshot.Contains("Double.doubleToRawLongBits")) "canonical SHA-256 and exact doubles"
Test-Gate "hash.no-clock-object-name" (($snapshot + $builder) -notmatch "currentTimeMillis|nanoTime|identityHashCode|getName\(") "no clock/object/localized name in canonical facts"

Test-Gate "builder.references" ($builder.Contains("validateReferences") -and $builder.Contains("Missing") -and $builder.Contains("Duplicate")) "duplicate/reference rejection"
Test-Gate "builder.terminal-coverage" ($builder.Contains("Terminal playable class capability coverage is incomplete")) "dynamic terminal class coverage"
Test-Gate "builder.required-capabilities" ($builder.Contains("REQUIRED_CAPABILITIES") -and (Count-Matches $builder '"(combat|profession)\.[a-z_]+"' ) -ge 12) "all required stable capability keys"
Test-Gate "builder.content-satisfiable" ($builder.Contains("satisfyingClasses") -and $builder.Contains("Content capability requirement is not satisfiable")) "minimum rank/count validation"
Test-Gate "builder.content-kinds" ($builder.Contains("ContentKind.RIFT") -and $builder.Contains("NpcKind.RAID_BOSS") -and $builder.Contains("NpcKind.GRAND_BOSS")) "Rift/RaidBoss/GrandBoss coverage gate"

$queryForbidden = $query -match "ItemData|NpcData|SpawnTable|RecipeData|SkillTreeData|SkillData|Files\.|Path\.|Database|java\.sql"
Test-Gate "query.map-index-page-only" (!$queryForbidden -and $query.Contains("_snapshot.") -and $query.Contains("page(")) "no loader/file/DB dependency"
Test-Gate "query.page-bound" ($model.Contains("(limit > 256)") -and $query.Contains("maximumQueryPageSize")) "1..256"
Test-Gate "query.cursor" ($model.Contains("afterKey") -and $query.Contains("request.afterKey()") -and $query.Contains("nextCursor")) "stable fact-key cursor"
Test-Gate "query.target-buckets" ($query.Contains("_snapshot.npcsByLevel().getOrDefault(level") -and !$query.Contains("_snapshot.npcs().")) "requested level buckets, no full NPC scan"
Test-Gate "query.target-order" ($query.Contains("Math.abs(fact.npc().level() - query.preferredLevel())") -and $query.Contains("thenComparingInt(fact -> fact.npc().level())") -and $query.Contains("thenComparingInt(fact -> fact.npc().npcId())")) "preferred distance, level, NPC ID"

Test-Gate "service.states" (($service.Contains("NEW") -and $service.Contains("BUILDING") -and $service.Contains("RUNNING") -and $service.Contains("STOPPED") -and $service.Contains("FAILED"))) "fixed lifecycle states"
Test-Gate "service.atomic-candidate" ($service.Contains("final PhantomGameKnowledgeSnapshot candidate") -and $service.IndexOf("_snapshot = candidate") -gt $service.IndexOf("candidate =")) "build before publication"
Test-Gate "service.no-reload-worker" (($service + $builder + $query) -notmatch "\breload\b|Executor|ScheduledFuture|CompletableFuture|new Thread|Thread\.of") "one build, no background worker"
Test-Gate "service.stop-acquisition" ($service.Contains("_stopping") -and $service.Contains("_query = null") -and $service.Contains("_snapshot = null")) "begin/finish stop boundary"
Test-Gate "metrics.fixed-aggregate" ($metrics.Contains("enum QueryCategory") -and $metrics.Contains("LongAdder") -and !$metrics.Contains("Map<")) "fixed aggregate counters"

$topologyStart = $system.IndexOf("if (!_topologyService.start())")
$knowledgeStart = $system.IndexOf("if (!_gameKnowledgeService.start())")
$schedulerStart = $system.IndexOf("if (!_scheduler.start())")
Test-Gate "system.start-order" (($topologyStart -ge 0) -and ($knowledgeStart -gt $topologyStart) -and ($schedulerStart -gt $knowledgeStart)) "topology then knowledge then scheduler"
Test-Gate "system.production-one-builder" ((Count-Matches $system "new PhantomGameKnowledgeBuilder") -eq 1 -and (Count-Matches $system "new PhantomGameKnowledgeService") -eq 1) "one production construction/build site"
Test-Gate "system.disabled-no-scan" ($system.IndexOf("if (!_settings.enabled())") -lt $system.IndexOf("new PhantomGameKnowledgeBuilder")) "disabled return precedes knowledge construction"
Test-Gate "system.inert-empty" ($system.Contains("PhantomGameKnowledgeService.inertForTesting")) "empty immutable test path"
Test-Gate "system.no-global-query" (!$system.Contains("configuredGameKnowledge") -and !$service.Contains("static PhantomGameKnowledgeQuery")) "no global static knowledge query API"
$schedulerBegin = $system.IndexOf("_scheduler.beginStop();")
$knowledgeBegin = $system.IndexOf("_gameKnowledgeService.beginStop();")
$topologyBegin = $system.IndexOf("_topologyService.beginStop();")
$schedulerFinish = $system.IndexOf("_scheduler.finishStop();")
$knowledgeFinish = $system.IndexOf("_gameKnowledgeService.finishStop();")
$topologyFinish = $system.IndexOf("_topologyService.finishStop();")
Test-Gate "system.stop-order" (($schedulerBegin -ge 0) -and ($knowledgeBegin -gt $schedulerBegin) -and ($topologyBegin -gt $knowledgeBegin) -and ($schedulerFinish -ge 0) -and ($knowledgeFinish -gt $schedulerFinish) -and ($topologyFinish -gt $knowledgeFinish)) "scheduler, knowledge, topology begin/finish"

$classCapabilities = @($curatedXml.knowledge.classCapability)
$contentRequirements = @($curatedXml.knowledge.contentRequirement)
$capabilityKeys = @($classCapabilities | ForEach-Object { [string]$_.capabilityKey } | Sort-Object -Unique)
$classIds = @($classCapabilities | ForEach-Object { [int]$_.classId } | Sort-Object -Unique)
$requiredKeys = @("combat.tank", "combat.heal", "combat.resurrection", "combat.buff", "combat.debuff", "combat.crowd_control", "combat.melee_damage", "combat.ranged_physical_damage", "combat.ranged_magic_damage", "combat.summon", "profession.spoil", "profession.craft")
Test-Gate "content.schema-metadata" (($curatedXml.knowledge.schemaVersion -eq "1") -and ($curatedXml.knowledge.datasetId -eq "high-five-core") -and ($curatedXml.knowledge.datasetVersion -eq "1")) "versioned production dataset"
Test-Gate "content.terminal-class-count" ($classIds.Count -eq 36) ($classIds.Count.ToString() + " distinct terminal class IDs")
Test-Gate "content.required-keys" (@($requiredKeys | Where-Object { $_ -notin $capabilityKeys }).Count -eq 0) "12 required capability keys"
Test-Gate "content.rift" (@($contentRequirements | Where-Object { ($_.contentKind -eq "RIFT") -and ($_.source.path -contains "data/DimensionalRift.xml") }).Count -ge 1) "Dimensional Rift evidence"
Test-Gate "content.raidboss" (@($contentRequirements | Where-Object { ($_.contentKind -eq "RAID") -and ($_.npcId -eq "25001") -and ($_.source.path -contains "data/stats/npcs/25000-25099.xml") }).Count -eq 1) "RaidBoss 25001 evidence"
Test-Gate "content.grandboss" (@($contentRequirements | Where-Object { ($_.contentKind -eq "EPIC") -and ($_.npcId -eq "29001") -and ($_.source.path -contains "data/stats/npcs/29000-29099.xml") }).Count -eq 1) "GrandBoss 29001 evidence"
$missingSources = @()
foreach ($source in @($curatedXml.SelectNodes("//source") | ForEach-Object { [string]$_.path } | Sort-Object -Unique))
{
	if (!(Test-Path -LiteralPath (Join-Path (Join-Path $ModuleRoot "dist/game") $source) -PathType Leaf))
	{
		$missingSources += $source
	}
}
Test-Gate "content.sources-exist" ($missingSources.Count -eq 0) $(if ($missingSources.Count -eq 0) { "all exact paths exist" } else { $missingSources -join "," })

$invalidCountRanges = @()
Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "dist/game/data/stats/npcs") -Filter "*.xml" | ForEach-Object {
	try
	{
		[xml]$npcXml = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
		foreach ($item in @($npcXml.SelectNodes("//dropLists//item")))
		{
			$minimum = [long]$item.min
			$maximum = [long]$item.max
			if (($minimum -lt 0) -or ($maximum -lt $minimum))
			{
				$npc = $item.SelectSingleNode("ancestor::npc[1]")
				$invalidCountRanges += ($_.Name + ":npc=" + $npc.id + ":item=" + $item.id + ":min=" + $minimum + ":max=" + $maximum)
			}
		}
	}
	catch
	{
		$invalidCountRanges += ($_.Name + ":parse-error")
	}
}
Test-Gate "source.drop-count-bounds" ($invalidCountRanges.Count -eq 0) $(if ($invalidCountRanges.Count -eq 0) { "all loaded raw min/max ranges valid" } else { $invalidCountRanges -join "," })
$zakenSource = Read-Text "dist/game/data/stats/npcs/29100-29199.xml"
$zakenCorrectedLine = '<item id="57" min="9000000" max="11000000" chance="100" /> <!-- Adena -->'
$zakenOldLine = '<item id="57" min="9000000" max="1100000" chance="100" /> <!-- Adena -->'
Test-Gate "source.zaken-adena-exact" ((Count-Matches $zakenSource ([regex]::Escape($zakenCorrectedLine))) -eq 1 -and !$zakenSource.Contains($zakenOldLine)) "NPC 29181 item 57 raw range 9000000..11000000"
$zakenDiff = Git-Text @("diff", "--unified=0", $Base, "--", ($ModuleName + "/dist/game/data/stats/npcs/29100-29199.xml"))
$zakenChangedLines = @(($zakenDiff -split "`r?`n") | Where-Object { $_ -match "^[+-]\s*<item " })
Test-Gate "scope.zaken-one-line-correction" (($zakenChangedLines.Count -eq 2) -and ((Count-Matches $zakenDiff ("(?m)^-\s*" + [regex]::Escape($zakenOldLine) + "$")) -eq 1) -and ((Count-Matches $zakenDiff ("(?m)^\+\s*" + [regex]::Escape($zakenCorrectedLine) + "$")) -eq 1)) "only NPC 29181 item 57 maximum changed"

$coreCaseCount = Count-Matches $coreTests "registry\.add\("
$parityCaseCount = Count-Matches $parityTests "registry\.add\("
$contentCaseCount = Count-Matches $contentTests "registry\.add\("
$performanceCaseCount = Count-Matches $performanceTests "registry\.add\("
Test-Gate "tests.core-cases" ($coreCaseCount -ge 32) ($coreCaseCount.ToString() + " focused cases")
Test-Gate "tests.parity-cases" ($parityCaseCount -ge 12 -and $parityTests.Contains("Loaded grouped/ungrouped drop and spoil parity") -and $parityTests.Contains("zaken-adena-authoritative-range") -and $parityTests.Contains("all-authoritative-drop-count-ranges-valid") -and $parityTests.Contains("11_000_000L")) ($parityCaseCount.ToString() + " exhaustive parity cases")
Test-Gate "tests.content-cases" ($contentCaseCount -ge 12 -and $contentTests.Contains("terminal-class-coverage")) ($contentCaseCount.ToString() + " curated content cases")
Test-Gate "tests.performance-iterations" ($performanceCaseCount -ge 6 -and (Count-Matches $performanceTests "100_000") -ge 1 -and $performanceTests.Contains("boundedTargetLookups")) "4 x 100000 real-corpus queries"
Test-Gate "tests.no-query-source-seam" ($coreTests.Contains("query-has-no-source-seam") -and $parityTests.Contains("query-source-seam-stable") -and $performanceTests.Contains("no-loader-file-db-after-build")) "guarded source seam"

foreach ($mode in @("knowledge-core", "knowledge-parity", "knowledge-content", "knowledge-performance"))
{
	Test-Gate ("launcher." + $mode) ($launcher.Contains('case "' + $mode + '"')) $mode
}
foreach ($target in @("phantom-game-knowledge-core-test", "phantom-game-knowledge-parity-test", "phantom-game-knowledge-content-test", "phantom-game-knowledge-performance-smoke"))
{
	Test-Gate ("build." + $target) ($build.Contains('name="' + $target + '"')) $target
}
Test-Gate "build.verify-route" ($build.Contains("phantom-game-knowledge-core-test") -and $build.Contains("phantom-game-knowledge-parity-test") -and $build.Contains("phantom-game-knowledge-content-test") -and $build.Contains("phantom-game-knowledge-performance-smoke") -and $build.Contains("verify-task-011.ps1")) "Goal 011 and cumulative verify"

$closure = Read-Text "docs/phantoms/reports/010c-topology-absent-source-reconciliation.md"
$review = Read-Text "docs/phantoms/reviews/010c-topology-absent-source-reconciliation-review.md"
$report = Read-Text "docs/phantoms/reports/011-authoritative-game-knowledge.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
Test-Gate "docs.goal010c-handoff" ($closure.Contains("7575ce4c66bdf5c51a27b20bed57c4ed8721b1e2") -and $closure.Contains("03F88A544D1C2D744B6E493AE3140521C97CBEAD21B0FDC7C17F0AE07CB41BE9")) "immutable accepted handoff"
Test-Gate "docs.goal010c-review" ($review.Contains("Goal 010C: ACCEPT") -and $review.Contains("Goal 011: ALLOWED")) "independent verdict"
$contract = Read-Text "docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md"
Test-Gate "docs.contract-raw-boundary" ($report.Contains("Raw chance") -and $contract.Contains("effective chance") -and $contract.Contains("probability-based ranking")) "raw chance limitation documented"
Test-Gate "docs.report" ($report.Contains("Goal 012: NOT_STARTED") -and $report.Contains("Goal 013: NOT_STARTED") -and $report.Contains("Production DB")) "required report sections"
Test-Gate "docs.roadmap-progress" ($roadmap.Contains("Goal 010C: ACCEPT") -and $roadmap.Contains("Goal 011: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 012: NOT_STARTED")) "roadmap final progress"

$textPaths = @($changedFiles | Where-Object { $_ -match "\.(java|xml|md|txt|json|ps1)$" -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf) })
$allChangedText = @($textPaths | ForEach-Object { Read-Text $_ }) -join "`n"
$mojibakeMarkers = @(
	(-join @([char]0x0420, [char]0x045f)),
	(-join @([char]0x0420, [char]0x045c)),
	(-join @([char]0x0420, [char]0x045b)),
	(-join @([char]0x0420, [char]0x2022)),
	(-join @([char]0x0420, [char]0x040e)),
	(-join @([char]0x0420, [char]0x203a)),
	(-join @([char]0x0420, [char]0x00a4)),
	(-join @([char]0x0420, [char]0x045a)),
	(-join @([char]0x0420, [char]0x0408)),
	(-join @([char]0x0420, [char]0x0459)),
	(-join @([char]0x0420, [char]0x0491)),
	(-join @([char]0x0420, [char]0x00b5)),
	(-join @([char]0x0420, [char]0x00b0)),
	(-join @([char]0x0420, [char]0x00bb)),
	(-join @([char]0x0420, [char]0x0405)),
	(-join @([char]0x0420, [char]0x0455)),
	(-join @([char]0x0421, [char]0x040f)),
	(-join @([char]0x0421, [char]0x20ac)),
	(-join @([char]0x0421, [char]0x0402)),
	(-join @([char]0x0421, [char]0x2039)),
	(-join @([char]0x0421, [char]0x040a)),
	(-join @([char]0x0421, [char]0x201a)),
	(-join @([char]0x0421, [char]0x0453)),
	(-join @([char]0x0421, [char]0x2021)),
	(-join @([char]0x0421, [char]0x2026)),
	(-join @([char]0x0421, [char]0x2020)),
	([string][char]0xfffd)
)
$mojibakeFound = @($mojibakeMarkers | Where-Object { $allChangedText.Contains($_) })
$escapedCyrillic = [regex]::Matches($allChangedText, '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};')
Test-Gate "encoding.mojibake" ($mojibakeFound.Count -eq 0) "no mojibake markers in changed text files"
Test-Gate "encoding.escaped-cyrillic" ($escapedCyrillic.Count -eq 0) "no escaped Cyrillic in changed text files"
$securityPaths = @($textPaths | Where-Object { ($_ -notlike "docs/phantoms/tasks/011-authoritative-game-knowledge/*") -and ($_ -ne "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") })
$securityText = @($securityPaths | ForEach-Object { Read-Text $_ }) -join "`n"
Test-Gate "security.no-credentials" ($securityText -notmatch '(?i)(password|passwd)\s*[=:]\s*[^\s]+') "no added production credentials"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ((Read-Text "tools/phantoms/verify-task-011.ps1") -notmatch $mutationPattern) "deterministic read-only verifier"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarKnowledge = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarKnowledge = $entries -contains "org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.class"
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-knowledge" $jarKnowledge "GameServer.jar contains Game Knowledge"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
