param()

$ErrorActionPreference = "Stop"
$Parent = "ca50ea28f233e41343035977c55c98129e5d113a"
$AcceptedBaseline = "8dba87e9c1d5828376b80c1ea16c4578726d4947"
$Branch = "feature/phantom-world"
$Subject = "fix(phantoms): harden progression capability extensibility"
$ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$RepoRoot = (& git -C $ModuleRoot rev-parse --show-toplevel).Trim()
$ModuleName = Split-Path $ModuleRoot -Leaf
$Pass = 0
$Fail = 0

function Test-Gate
{
	param([string]$Id, [bool]$Condition, [string]$Detail)
	if ($Condition)
	{
		$script:Pass++
		Write-Output ("PASS " + $Id + " :: " + $Detail)
	}
	else
	{
		$script:Fail++
		Write-Output ("FAIL " + $Id + " :: " + $Detail)
	}
}

function Git-Text
{
	param([string[]]$Arguments)
	$oldPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"
	$output = & git -C $RepoRoot @Arguments 2>$null
	$exitCode = $LASTEXITCODE
	$ErrorActionPreference = $oldPreference
	if ($exitCode -ne 0)
	{
		throw ("git command failed with exit code " + $exitCode)
	}
	return (($output) -join "`n").Trim()
}

function Git-Succeeds
{
	param([string[]]$Arguments)
	$oldPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"
	& git -C $RepoRoot @Arguments 1>$null 2>$null
	$result = $LASTEXITCODE -eq 0
	$ErrorActionPreference = $oldPreference
	return $result
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
$parentOfParent = Git-Text @("rev-parse", ($Parent + "^"))
$childCount = [int](Git-Text @("rev-list", "--count", ($Parent + "..HEAD")))
$worktreePhase = $head -eq $Parent
$oneChild = $childCount -eq 1
$exactParent = $worktreePhase -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Parent)
$exactSubject = $worktreePhase -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $Subject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))

Test-Gate "repository.module" ($ModuleName -eq "L2J_Mobius_CT_2.6_HighFive") $ModuleName
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.parent-exists" ((Git-Text @("cat-file", "-t", $Parent)) -eq "commit") $Parent
Test-Gate "repository.accepted-baseline" ($parentOfParent -eq $AcceptedBaseline) $AcceptedBaseline
Test-Gate "repository.one-child" ($worktreePhase -or $oneChild) "worktree phase or one ordinary child"
Test-Gate "repository.exact-parent" $exactParent "Goal 013A is based on the required parent"
Test-Gate "repository.subject" $exactSubject "exact subject after commit"
Test-Gate "repository.remote-phase" (($remote -eq $Parent) -or ($remote -eq $head)) "baseline before push or exact pushed head"
Test-Gate "repository.root-gitignore" (Git-Succeeds @("diff", "--quiet", $Parent, "--", ".gitignore")) "root .gitignore unchanged"

$changed = New-Object System.Collections.Generic.SortedSet[string]
foreach ($arguments in @(
	@("diff", "--name-only", $Parent),
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

$allowedExact = @(
	"build.xml",
	"dist/game/data/phantoms/progression/high-five-capabilities-v1.xml",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md",
	"docs/phantoms/reports/013a-progression-capability-extensibility-hardening.md",
	"java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatActionOwnershipSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"tools/phantoms/verify-task-013a.ps1"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/progression/*") -and
	($_ -notlike "test/java/org/l2jmobius/tests/phantoms/PhantomProgression*.java") -and
	($_ -notlike "test/java/org/l2jmobius/tests/phantoms/PhantomCapability*.java") -and
	($_ -notlike "docs/phantoms/research/high-five-behavior/*") -and
	($_ -notlike "docs/phantoms/tasks/013a-progression-capability-extensibility-hardening/*")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "Goal 013A exact allowlist" } else { $outside -join "," })
Test-Gate "scope.no-other-chronicle" (@($changed | Where-Object { $_ -match '^L2J_Mobius_' }).Count -eq 0) "High Five only"
Test-Gate "scope.no-accepted-knowledge" (@($changed | Where-Object { $_ -match '^(java/org/l2jmobius/gameserver/phantoms/knowledge|dist/game/data/phantoms/knowledge)/' }).Count -eq 0) "accepted Game Knowledge unchanged"
Test-Gate "scope.no-core-config-schema" (@($changed | Where-Object { $_ -match '^(java/org/l2jmobius/gameserver/(model|data)/|dist/game/config/|dist/sql/|sql/)' }).Count -eq 0) "server core, config and schema frozen"
Test-Gate "scope.no-protected-phantom-subsystems" (@($changed | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/phantoms/(scheduler|persistence|player|identity)/|/PhantomSystem\.java$|/Shutdown\.java$' }).Count -eq 0) "scheduler, persistence, materialization, identity and shutdown unchanged"
Test-Gate "scope.no-future-goal-artifacts" (@($changed | Where-Object { $_ -match '(tasks|reports|reviews)/(014|015|017|025)-' }).Count -eq 0) "Goal 014/015/017/025 production work not started"
Test-Gate "scope.no-geodata-binaries" (@($changed | Where-Object { $_ -match '\.l2j$|\.(jar|class|exe|dll|zip|7z|png|jpg|jpeg)$' }).Count -eq 0) "no geodata or binary artifacts"

$progressionFiles = @(Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "java/org/l2jmobius/gameserver/phantoms/progression") -Filter "*.java" -File | Sort-Object Name)
$progression = ($progressionFiles | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8) }) -join "`n"
$model = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java"
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java"
$backendContract = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionBackend.java"
$builder = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCatalogBuilder.java"
$evaluator = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCapabilityEvaluator.java"
$parser = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionSourceParser.java"
$resolver = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java"
$combatContract = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java"
$combatBackend = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"

[xml]$capabilityXml = Read-Text "dist/game/data/phantoms/progression/high-five-capabilities-v1.xml"
$rules = @($capabilityXml.progression.capabilityRule)
$triples = @($rules | ForEach-Object { $_.classId + ":" + $_.capabilityKey + ":" + $_.variantKey })
$pairs = @($rules | ForEach-Object { $_.classId + ":" + $_.capabilityKey })
$sameGroupVariants = @($pairs | Group-Object | Where-Object { $_.Count -gt 1 })
$allVariantKeysValid = @($rules | Where-Object { [string]::IsNullOrWhiteSpace($_.variantKey) }).Count -eq 0

Test-Gate "capability.xml-variant-required" $allVariantKeysValid ($rules.Count.ToString() + " curated variants")
Test-Gate "capability.xml-triple-unique" (($triples | Sort-Object -Unique).Count -eq $triples.Count) "unique class/group/variant triples"
Test-Gate "capability.xml-same-group-proof" ($sameGroupVariants.Count -ge 1) ($sameGroupVariants.Count.ToString() + " same-group pair")
Test-Gate "capability.model-identity" ($model.Contains("String capabilityKey, String variantKey") -and $model.Contains("capabilityKey + ':' + variantKey")) "variant identity is part of stable keys"
Test-Gate "capability.parser-identity" ($parser.Contains('required(element, "variantKey")') -and $parser.Contains("thenComparing(CapabilitySeed::variantKey)")) "strict variant parser and ordering"
Test-Gate "capability.no-evidence-collapse" (($evaluator -notmatch '\.findFirst\s*\(') -and ($resolver -notmatch '\.findFirst\s*\(') -and $evaluator.Contains("actor.knows(rule.actionSkill())")) "exact action variant evaluated without first-match collapse"
Test-Gate "capability.resolver-all-variants" ($resolver.Contains("flatMap(capability -> capability.skills().stream())") -and $resolver.Contains("filter(skill -> lease.supportsSkill(skill, mode))") -and $resolver.Contains("mapToInt(CapabilityEvidence::rank).max()")) "support filtering precedes rank metadata"
Test-Gate "capability.no-central-class-branch" (($progression -notmatch 'switch\s*\(\s*.*class') -and ($resolver -notmatch 'switch\s*\(\s*.*class')) "no central class switch"

Test-Gate "resources.skill-item-fact" ($model.Contains("int itemConsumeId, int itemConsumeCount, int chargeConsumeCount, int maximumSoulConsumeCount") -and $backend.Contains("skill.getItemConsumeId()") -and $backend.Contains("skill.getItemConsumeCount()")) "authoritative skill item/charge/soul facts copied"
Test-Gate "resources.skill-item-ready-now" ($evaluator.Contains("skill.itemConsumeId()") -and $evaluator.Contains("skill.itemConsumeCount()") -and $evaluator.Contains("Math::max")) "curated and skill item requirements merge without double count"
Test-Gate "resources.charge-ready-now" ($evaluator.Contains("actor.charges() < skill.chargeConsumeCount()")) "exact current charges checked"
Test-Gate "resources.soul-ceiling-not-minimum" ($model.Contains("maximumSoulConsumeCount") -and $backend.Contains("_player.getChargedSouls()") -and ($evaluator -notmatch 'souls\(\)\s*<\s*skill\.maximumSoulConsumeCount')) "maximum soul consumption is factual ceiling"
Test-Gate "resources.complete-item-domain" ($backendContract.Contains("Set<Integer> knownItemIds") -and $backend.Contains("ItemData.getInstance().getAllItems()") -and $builder.Contains('requireItem(knownItemIds, fact.itemConsumeId(), "skill consumption")')) "all positive resource IDs validate against ItemData"
Test-Gate "resources.no-hot-loader" (($evaluator -notmatch 'ItemData|SkillData|Files\.|DocumentBuilder|Connection|DataSource') -and ($evaluator -notmatch 'Path\.of')) "READY_NOW has no loader/file/DB scan"

$controlledBodyMatch = [regex]::Match($model, 'record ControlledActorBody\((?<body>[^)]*)\)')
Test-Gate "summon.typed-body" ($controlledBodyMatch.Success -and $controlledBodyMatch.Groups["body"].Value.Contains("currentHp") -and $controlledBodyMatch.Groups["body"].Value.Contains("currentMp") -and !$controlledBodyMatch.Groups["body"].Value.Contains("Cp")) "body facts include HP/MP and no fabricated CP"
Test-Gate "summon.cubic-no-body" ($model.Contains("(actorKind == ActorKind.CUBIC) != (body == null)") -and $model.Contains("Cubic cannot expose body commands.")) "cubic uses absent body and rejects commands"
Test-Gate "summon.own-mechanics" ($model.Contains("List<SkillRef> actorSkills") -and $model.Contains("List<SkillRef> healSkills") -and $model.Contains("List<SkillRef> rechargeSkills") -and $backend.Contains("mechanicSkills(")) "controlled actor own skill evidence preserved"
Test-Gate "summon.taxonomy" ($model.Contains("SERVITOR") -and $model.Contains("PET") -and $model.Contains("BABY_PET") -and $model.Contains("CUBIC") -and $model.Contains("SIEGE_SUMMON") -and $model.Contains("QUEST_SUMMON")) "actor kinds remain separate"

Test-Gate "equipment.query-port" ($backendContract.Contains("Page<OwnedEquipmentFact> ownedEquipment(OwnedEquipmentFilter filter, PageRequest page)") -and $backend.Contains("Owned equipment page limit exceeds 64.")) "bounded filtered owned-equipment query"
Test-Gate "equipment.stable-object-order" ($model.Contains("return key(objectId);") -and $backend.Contains("Comparator.comparing(OwnedEquipmentFact::stableKey)")) "object identity ordering"
Test-Gate "equipment.no-global-score" (($progression -notmatch 'deterministicPreferenceScore|PriorityQueue<OwnedEquipmentFact>|maximumOwnedEquipmentCandidates') -and ($backend -notmatch '\.limit\s*\(\s*64\s*\)')) "no universal score or global top-64 truncation"

Test-Gate "learning.aggregate-required-items" ($model.Contains("SkillLearningItemPlan") -and $model.Contains("Math::addExact") -and $backend.Contains("itemPlan.aggregatedItems()")) "duplicate item IDs aggregate"
Test-Gate "learning.fail-closed-multi-item" ($model.Contains("aggregatedItems.size() <= 1") -and $backend.IndexOf("if (!itemPlan.canonicalAtomicMutationSupported())") -lt $backend.IndexOf("destroyItemByItemId")) "multi-distinct-item case stops before side effects"
Test-Gate "learning.event-after-reconciliation" ($backend.LastIndexOf("notifyEventAsync(new OnPlayerSkillLearn") -gt $backend.LastIndexOf("destroyItemByItemId") -and $backend.LastIndexOf("notifyEventAsync(new OnPlayerSkillLearn") -gt $backend.LastIndexOf("_player.addSkill(skill, true)")) "event follows successful conservation"

Test-Gate "cp.player-snapshot-shape" ($combatContract.Contains("double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp")) "CP is distinct beside HP/MP"
Test-Gate "cp.exact-canonical-copy" ($combatBackend.Contains("_player.getCurrentCp()") -and $combatBackend.Contains("_player.getMaxCp()")) "CP copied from exact Player under actor lease"
Test-Gate "cp.no-profile-persistence" ($progression -notmatch 'PhantomProfile.*[Cc]p|[Cc]p.*PhantomProfile') "no progression/profile CP persistence"

Test-Gate "production.no-class-mutation" ($progression -notmatch '\.setPlayerClass\s*\(') "profession/subclass mutation absent"
Test-Gate "production.no-packets-bypass" ($progression -notmatch 'network\.(clientpackets|serverpackets)|RequestAcquireSkill|sendPacket\s*\(|bypass') "no packet or bypass simulation"
Test-Gate "production.no-workers" ($progression -notmatch 'new\s+Thread|Executors\.|ExecutorService|ScheduledFuture|CompletableFuture|java\.util\.concurrent\.Future') "no progression worker/task/future"

$compositionTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionProductionCompositionSuite.java"
$extensibilityTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionExtensibilitySuite.java"
$integrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java"
$performanceTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java"
$combatCoreTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java"
$combatIntegrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"

Test-Gate "tests.production-composition-not-inert" ($compositionTests.Contains("new PhantomGameKnowledgeService(knowledgeBuilder)") -and $compositionTests.Contains("L2jGameKnowledgeBackend") -and !$compositionTests.Contains("inertForTesting(")) "ordinary Game Knowledge composition"
Test-Gate "tests.production-source-parity" ($compositionTests.Contains("_expectedSources") -and $compositionTests.Contains("assertSourceParity") -and $compositionTests.Contains("assertProvenanceParity") -and $compositionTests.Contains("repeat < 3")) "independent identity/provenance and repeated hashes"
Test-Gate "tests.extensibility-coverage" ((Count-Matches $extensibilityTests 'registry\.add\(') -ge 15) ((Count-Matches $extensibilityTests 'registry\.add\(').ToString() + " unique cases")
Test-Gate "tests.main-subclass-integration" ($integrationTests.Contains("main-subclass-main") -and $integrationTests.Contains("certification") -and $integrationTests.Contains("ordinary-skill")) "real main/subclass/certification isolation"
Test-Gate "tests.summon-cubic-integration" ($integrationTests.Contains("real-servitor") -and $integrationTests.Contains("baby-pet") -and $integrationTests.Contains("cubic")) "real controlled actor variants"
Test-Gate "tests.equipment-over-64" ($integrationTests.Contains("over-64-equipment-objects-owned") -and $integrationTests.Contains("equipment-paging-reaches-each-object-once") -and $integrationTests.Contains("lower-grade-equipment-remains-reachable")) "real complete equipment paging"
Test-Gate "tests.cp-integration" ($combatIntegrationTests.Contains("getCurrentCp()") -and $combatIntegrationTests.Contains("getMaxCp()") -and $combatIntegrationTests.Contains("Next combat snapshot") -and $combatIntegrationTests.Contains("Immutable combat snapshot")) "canonical CP exactness, freshness and immutability"
Test-Gate "tests.disabled-inert" ($combatCoreTests.Contains("disabled-backend-remains-inert") -and $combatCoreTests.Contains("PhantomCombatBackend.inert().tryAcquireActor")) "disabled combat path inert"
Test-Gate "tests.performance-shape" ($performanceTests.Contains("CAPABILITY_EVALUATIONS = 100_000") -and $performanceTests.Contains("CLASS_QUERIES = 100_000") -and $performanceTests.Contains("EQUIPMENT_QUERIES = 50_000") -and $performanceTests.Contains("_elapsedMillis <= 120_000")) "fixed performance corpus and timeout"
Test-Gate "tests.launcher-routes" ($launcher.Contains('case "progression-extensibility"') -and $launcher.Contains('case "progression-production-composition"')) "focused launcher routes"
Test-Gate "tests.ant-routes" ($build.Contains('name="phantom-progression-extensibility-test"') -and $build.Contains('name="phantom-progression-production-composition-test"') -and $launcher.Contains("Long.parseLong(args[1])")) "focused Ant routes accept the explicit deterministic seed"
Test-Gate "tests.verify-route" ($build.Contains("phantom-progression-extensibility-test") -and $build.Contains("phantom-progression-production-composition-test") -and $build.Contains('name="verify"')) "cumulative verify includes Goal 013A"

$normalizedResearch = @(
	"DR-01-CLASS-PROGRESSION-NORMALIZED.md",
	"DR-02-PVE-CLASS-CAPABILITIES-NORMALIZED.md",
	"DR-03-PVP-CLASS-EQUIPMENT-MECHANICS-NORMALIZED.md",
	"DR-04-PARTY-ROLE-CAPABILITY-MATRIX-NORMALIZED.md",
	"DR-05-SUMMON-PET-ACTOR-CATALOG-NORMALIZED.md"
)
$researchRoot = "docs/phantoms/research/high-five-behavior"
$researchTexts = @($normalizedResearch | ForEach-Object { Read-Text ($researchRoot + "/" + $_) })
$research = $researchTexts -join "`n"
Test-Gate "docs.stable-claim-ids" ($research.Contains("DR01-CLASS-001") -and $research.Contains("DR02-PVE-CAP-001") -and $research.Contains("DR03-PVP-MECH-001") -and $research.Contains("DR04-PARTY-CAP-001") -and $research.Contains("DR05-SUMMON-001")) "DR-01 through DR-05 stable IDs"
Test-Gate "docs.claim-metadata" (($researchTexts | Where-Object { !$_.Contains("Authority") -or !$_.Contains("Confidence") -or !$_.Contains("Source paths") }).Count -eq 0) "authority, confidence and source paths retained"
Test-Gate "docs.no-raw-research" ($research -notmatch 'turn[0-9]+|raw browser|BEGIN RAW') "no raw research or turn citations"
$contract = Read-Text "docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
Test-Gate "docs.contract" ($contract.Contains("(classId, capabilityKey, variantKey)") -and $contract.Contains("maximumSoulConsumeCount") -and $contract.Contains("currentCp") -and $contract.Contains("typed absent body") -and $contract.Contains("OwnedEquipmentFact")) "corrected variant/resource/summon/equipment/CP contract"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 013: FIX_REQUIRED") -and $roadmap.Contains("Goal 013A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 014: NOT_STARTED, blocked") -and $roadmap.Contains("Goal 015: NOT_STARTED") -and $roadmap.Contains("Goal 017: NOT_STARTED")) "progress truth only"

$implementationPaths = @($changed | Where-Object { ($_ -match '^(java|test)/') -and ($_ -ne "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") })
$implementationText = ($implementationPaths | Where-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf } | ForEach-Object { Read-Text $_ }) -join "`n"
Test-Gate "safety.test-db-only" ($implementationText -notmatch 'l2jmobiush5(?!_phantom_test)') "no production DB name in implementation"

$changedText = ($changed | Where-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf } | ForEach-Object { Read-Text $_ }) -join "`n"
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
$mojibakeFound = @($mojibakeMarkers | Where-Object { $changedText.Contains($_) })
$escapedPattern = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
Test-Gate "encoding.utf8-strict" $true "all verifier-read changed files decode as strict UTF-8"
Test-Gate "encoding.mojibake" ($mojibakeFound.Count -eq 0) "no mojibake markers in changed files"
Test-Gate "encoding.escaped-cyrillic" ($changedText -notmatch $escapedPattern) "no escaped Cyrillic in changed files"

$verifierText = Read-Text "tools/phantoms/verify-task-013a.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "no filesystem/history mutation command"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarProduction = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarProduction = ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.class")
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production" $jarProduction "progression and combat production classes present"
Test-Gate "jar.no-tests" $jarTestsAbsent "test classes absent"

$total = $Pass + $Fail
Write-Output ("SUMMARY PASS=" + $Pass + " FAIL=" + $Fail + " TOTAL=" + $total)
if ($Fail -ne 0)
{
	exit 1
}
Write-Output "VERIFY_TASK_013A_OK"
