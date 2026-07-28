param()

$ErrorActionPreference = "Stop"
$Base = "8dba87e9c1d5828376b80c1ea16c4578726d4947"
$Branch = "feature/phantom-world"
$Subject = "feat(phantoms): add class progression capability catalog"
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
$count = [int](Git-Text @("rev-list", "--count", ($Base + "..HEAD")))
$worktreePhase = $head -eq $Base
$oneChild = $count -eq 1
$parentValid = $worktreePhase -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Base)
$subjectValid = $worktreePhase -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $Subject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))

Test-Gate "repository.module" ($ModuleName -eq "L2J_Mobius_CT_2.6_HighFive") $ModuleName
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") $Base
Test-Gate "repository.one-child" ($worktreePhase -or $oneChild) "worktree phase or one ordinary child"
Test-Gate "repository.parent" $parentValid "exact Goal 012A baseline"
Test-Gate "repository.subject" $subjectValid "exact subject after commit"
Test-Gate "repository.remote-phase" (($remote -eq $Base) -or ($remote -eq $head)) "baseline before push or exact pushed head"
Test-Gate "repository.gitignore" (Git-Succeeds @("diff", "--quiet", $Base, "--", ".gitignore")) "reviewed root .gitignore unchanged"

$changed = New-Object System.Collections.Generic.SortedSet[string]
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

$allowedExact = @(
	"build.xml",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md",
	"docs/phantoms/reports/012a-combat-action-ownership-truth.md",
	"docs/phantoms/reports/013-class-progression-capability-catalog.md",
	"docs/phantoms/reviews/012a-combat-action-ownership-truth-review.md",
	"dist/game/data/phantoms/progression/high-five-capabilities-v1.xml",
	"java/org/l2jmobius/gameserver/Shutdown.java",
	"java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCapabilityRuntimeSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionCatalogSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionParitySuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"tools/phantoms/verify-task-013.ps1"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/progression/*") -and
	($_ -notlike "docs/phantoms/research/high-five-behavior/*") -and
	($_ -notlike "docs/phantoms/tasks/013-class-progression-capability-catalog/*")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "Goal 013 exact allowlist" } else { $outside -join "," })
Test-Gate "scope.no-other-chronicle" (@($changed | Where-Object { $_ -match '^L2J_Mobius_' }).Count -eq 0) "High Five only"
Test-Gate "scope.no-core-config-schema" (@($changed | Where-Object { $_ -match '^(java/org/l2jmobius/gameserver/model|java/org/l2jmobius/gameserver/data|dist/game/config|dist/game/data/(?!phantoms/progression)|dist/sql|sql/)' }).Count -eq 0) "server core, config and schema frozen"
Test-Gate "scope.no-goal-014-015" (@($changed | Where-Object { $_ -match '(tasks|reports|reviews)/(014|015)-' }).Count -eq 0) "future goals not started"
Test-Gate "scope.no-binaries" (@($changed | Where-Object { $_ -match '\.(jar|class|exe|dll|zip|7z|png|jpg|jpeg)$' }).Count -eq 0) "no binary artifacts"

$progressionFiles = @(Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "java/org/l2jmobius/gameserver/phantoms/progression") -Filter "*.java" -File | Sort-Object Name)
$progression = ($progressionFiles | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName) }) -join "`n"
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java"
$model = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java"
$catalog = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCatalog.java"
$builder = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCatalogBuilder.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java"
$handlers = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionStepHandlers.java"
$system = Read-Text "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$shutdown = Read-Text "java/org/l2jmobius/gameserver/Shutdown.java"

Test-Gate "production.no-class-mutation" ($progression -notmatch '\.setPlayerClass\s*\(') "profession changes are observation-only"
Test-Gate "production.no-reward-mutation" ($progression -notmatch '\.(addExpAndSp|setExp|setLevel)\s*\(') "EXP/SP/level grants absent; setSp is deduction-only learning conservation"
Test-Gate "production.no-packets-bypass" ($progression -notmatch 'network\.(clientpackets|serverpackets)|RequestAcquireSkill|sendPacket\s*\(|bypass') "no packet or NPC bypass"
Test-Gate "production.no-workers" ($progression -notmatch 'new\s+Thread|Executors\.|ExecutorService|ScheduledFuture|CompletableFuture|java\.util\.concurrent\.Future') "no progression worker/task/future"
Test-Gate "production.class-only-learning" ($backend.Contains("AcquireSkillType.CLASS") -and $model.Contains("CLASS(true)") -and $model.Contains("TRANSFER(false)")) "only CLASS acquire is executable"
Test-Gate "production.trainer-contract" ($backend.Contains("getLastFolkNPC()") -and $backend.Contains("instanceof Folk") -and $backend.Contains("canInteract(_player)")) "exact current trainer and canonical interaction/range"
Test-Gate "production.learning-conservation" ($backend.Contains("getSp()") -and $backend.Contains("setSp(") -and $backend.Contains("destroyItemByItemId") -and $backend.Contains("addSkill(") -and $backend.Contains("OnPlayerSkillLearn")) "canonical SP/items/skill/event transaction"
Test-Gate "production.canonical-equip" ($backend.Contains("useEquippableItem(item, false)") -and $backend.Contains("getItemByObjectId") -and $backend.Contains("item.getOwnerId()")) "owned item through canonical method"
Test-Gate "production.profession-boundary" ($service.Contains("ProfessionStatus.CANONICAL_QUEST_REQUIRED") -and $service.Contains("ProfessionStatus.LEVEL_PENDING")) "no fabricated class quest"
Test-Gate "production.operation-ownership" ($service.Contains("OperationSlot") -and $service.Contains("_operations.put") -and $service.Contains("_operations.remove") -and $service.Contains("claimActor")) "one operation/profile and exact actor lease"
Test-Gate "production.capability-levels" ($model.Contains("boolean intrinsic") -and $model.Contains("boolean learned") -and $model.Contains("boolean readyNow")) "INTRINSIC/LEARNED/READY_NOW are separate"
Test-Gate "production.target-scope" ($model.Contains("enum TargetScope") -and $model.Contains("SERVITOR") -and $model.Contains("COMMAND_CHANNEL")) "explicit target taxonomy"
Test-Gate "production.complete-indexes" ($catalog.Contains("_classesById") -and $catalog.Contains("_childrenByClass") -and $catalog.Contains("_terminalClasses") -and $catalog.Contains("_classSkillLearns") -and $catalog.Contains("_classesBySkill") -and $catalog.Contains("_skillsByIdentity") -and $catalog.Contains("_equipmentByBodyPart") -and $catalog.Contains("_equipmentByFamily") -and $catalog.Contains("_summonsBySkill") -and $catalog.Contains("_summonsByNpc") -and $catalog.Contains("_petsByNpc") -and $catalog.Contains("_capabilitiesByKey")) "immutable indexed catalog"
Test-Gate "production.sha256" ($builder.Contains('MessageDigest.getInstance("SHA-256")') -and $catalog.Contains("classGraphHash") -and $catalog.Contains("skillLearningHash") -and $catalog.Contains("summonPetHash") -and $catalog.Contains("combinedHash")) "component and combined SHA-256"
Test-Gate "production.no-display-identity" ($progression -notmatch 'getClassName\s*\(|getDisplayName\s*\(') "no localized identity inference"
Test-Gate "production.bounds" ($progression.Contains("maximumPageSize > 256") -and $progression.Contains("maximumOwnedEquipmentCandidates > 64")) "bounded pages and inventory candidates"
Test-Gate "production.handlers" ($handlers.Contains("progression.observe") -and $handlers.Contains("progression.await_level") -and $handlers.Contains("progression.await_profession") -and $handlers.Contains("progression.learn_skill") -and $handlers.Contains("progression.equip_item")) "five exact handlers"
Test-Gate "production.inert" ($system.Contains("if (_productionMaterialization)") -and $system.Contains("new PhantomProgressionService") -and !$system.Contains("PhantomProgressionService.inertForTesting")) "disabled/non-production path creates no progression service"
Test-Gate "production.shutdown-order" ($shutdown.Contains("progression") -and ($system.IndexOf("_progressionService.finishStop()") -lt $system.IndexOf("_materializationService.shutdown()"))) "progression stops before materialization"

$catalogTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionCatalogSuite.java"
$parityTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionParitySuite.java"
$runtimeTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCapabilityRuntimeSuite.java"
$operationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java"
$integrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java"
$performanceTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"
Test-Gate "tests.catalog-count" ($catalogTests.Contains("CASES = 60")) "60 generated cases"
Test-Gate "tests.parity-count" ((Count-Matches $parityTests 'registry\.add\(') -ge 32) ((Count-Matches $parityTests 'registry\.add\(').ToString() + " cases")
Test-Gate "tests.runtime-count" ($runtimeTests.Contains("CASES = 40")) "40 generated cases"
Test-Gate "tests.operation-count" ($operationTests.Contains("CASES = 36")) "36 generated cases"
Test-Gate "tests.integration-count" ((Count-Matches $integrationTests 'registry\.add\(') -ge 18) ((Count-Matches $integrationTests 'registry\.add\(').ToString() + " cases")
Test-Gate "tests.performance-count" ((Count-Matches $performanceTests 'registry\.add\(') -ge 2) ((Count-Matches $performanceTests 'registry\.add\(').ToString() + " cases")
Test-Gate "tests.class-identities" ($parityTests.Contains("male-soulhound") -and $parityTests.Contains("female-soulhound") -and $parityTests.Contains("inspector-parent") -and $parityTests.Contains("judicator-parent") -and $parityTests.Contains("terminal-children")) "High Five class graph boundary cases"
Test-Gate "tests.real-operations" ($integrationTests.Contains("canonical-reward") -and $integrationTests.Contains("real-trainer-learning") -and $integrationTests.Contains("owned-item-equip") -and $integrationTests.Contains("profession-change-observed")) "canonical integration routes"
Test-Gate "tests.performance-shape" ($performanceTests.Contains("CATALOG_BUILDS = 3") -and $performanceTests.Contains("CLASS_QUERIES = 100_000") -and $performanceTests.Contains("SKILL_QUERIES = 100_000") -and $performanceTests.Contains("CAPABILITY_EVALUATIONS = 100_000") -and $performanceTests.Contains("OPERATIONS = 10_000")) "fixed performance corpus"
Test-Gate "tests.launcher-routes" ($launcher.Contains('case "progression-catalog"') -and $launcher.Contains('case "progression-parity"') -and $launcher.Contains('case "capability-runtime"') -and $launcher.Contains('case "progression-operations"') -and $launcher.Contains('case "progression-server-integration"') -and $launcher.Contains('case "progression-performance"')) "six focused modes"
Test-Gate "tests.ant-routes" ($build.Contains('name="phantom-progression-catalog-test"') -and $build.Contains('name="phantom-progression-parity-test"') -and $build.Contains('name="phantom-capability-runtime-test"') -and $build.Contains('name="phantom-progression-operations-test"') -and $build.Contains('name="phantom-progression-server-integration-test"') -and $build.Contains('name="phantom-progression-performance-smoke"')) "six focused Ant targets"
Test-Gate "tests.verify-route" ($build.Contains('name="phantom-static-verify-013"') -and $build.Contains("verify-task-013.ps1") -and $build.Contains("phantom-progression-performance-smoke")) "Goal 013 cumulative verify route"

$requiredResearch = @(
	"README.md",
	"SOURCE_AUTHORITY_MODEL.md",
	"DR-01-CLASS-PROGRESSION-NORMALIZED.md",
	"DR-01-CONTRADICTIONS-AND-LIVE-GATES.md",
	"DR-02-PVE-CLASS-CAPABILITIES-NORMALIZED.md",
	"DR-03-PVP-CLASS-EQUIPMENT-MECHANICS-NORMALIZED.md",
	"DR-04-PARTY-ROLE-CAPABILITY-MATRIX-NORMALIZED.md",
	"DR-05-SUMMON-PET-ACTOR-CATALOG-NORMALIZED.md",
	"DR-05-CURRENT-SERVER-CONTRADICTIONS.md",
	"DEFERRED-CLAIMS-BY-GOAL.md"
)
$researchRoot = "docs/phantoms/research/high-five-behavior"
$researchPresent = @($requiredResearch | Where-Object { !(Test-Path -LiteralPath (Join-Path $ModuleRoot ($researchRoot + "/" + $_)) -PathType Leaf) }).Count -eq 0
$researchFiles = @($requiredResearch | ForEach-Object { Read-Text ($researchRoot + "/" + $_) })
$research = $researchFiles -join "`n"
Test-Gate "docs.research-present" $researchPresent "DR-01 through DR-05 normalized set"
Test-Gate "docs.research-authority" (($researchFiles | Where-Object { !$_.Contains("Authority:") -or !$_.Contains("Confidence:") }).Count -eq 0) "every normalized document has authority and confidence"
Test-Gate "docs.research-no-raw-citations" ($research -notmatch 'turn[0-9]+|raw browser|BEGIN RAW') "no turn citations or raw research"
Test-Gate "docs.summon-contradictions" ($research.Contains("20/80") -and $research.Contains("Olympiad") -and $research.Contains("Servitor") -and $research.Contains("Pet")) "current Mobius attribute/Olympiad contradictions"
$contract = Read-Text "docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
$report012a = Read-Text "docs/phantoms/reports/012a-combat-action-ownership-truth.md"
$review012a = Read-Text "docs/phantoms/reviews/012a-combat-action-ownership-truth-review.md"
Test-Gate "docs.contract" ($contract.Contains("INTRINSIC") -and $contract.Contains("CANONICAL_QUEST_REQUIRED") -and $contract.Contains("READY_NOW")) "progression capability contract"
Test-Gate "docs.goal-012a-closed" ($report012a.Contains("ACCEPT") -and $review012a.Contains("7F5EFA1D3D506E73A5741010833DF82685A0530BBF24D0E7C9326F8514E81A16")) "immutable Goal 012A closure"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 012: ACCEPT after Goal 012A") -and $roadmap.Contains("Goal 012A: ACCEPT") -and $roadmap.Contains("Goal 013: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 014: NOT_STARTED") -and $roadmap.Contains("Goal 015: NOT_STARTED")) "progress only"

$diffText = Git-Text @("diff", "--unified=0", $Base, "--", ($ModuleName + "/build.xml"), ($ModuleName + "/java"), ($ModuleName + "/test"), ($ModuleName + "/tools"), ($ModuleName + "/docs"), ($ModuleName + "/dist/game/data/phantoms/progression"))
$addedLines = New-Object System.Collections.Generic.List[string]
foreach ($line in ($diffText -split "`r?`n"))
{
	if ($line.StartsWith("+") -and !$line.StartsWith("+++"))
	{
		$addedLines.Add($line.Substring(1))
	}
}
foreach ($path in $changed)
{
	if (($path -notlike "docs/phantoms/tasks/013-class-progression-capability-catalog/*") -and (Git-Text @("ls-files", "--others", "--exclude-standard", "--", ($ModuleName + "/" + $path))) -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $path) -PathType Leaf))
	{
		$addedLines.Add((Read-Text $path))
	}
}
$addedText = $addedLines -join "`n"
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
$mojibakeFound = @($mojibakeMarkers | Where-Object { $addedText.Contains($_) })
$escapedPattern = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
Test-Gate "encoding.utf8-strict" $true "all verifier-read files decode as strict UTF-8"
Test-Gate "encoding.mojibake" ($mojibakeFound.Count -eq 0) "no mojibake markers in changed content"
Test-Gate "encoding.escaped-cyrillic" ($addedText -notmatch $escapedPattern) "no escaped Cyrillic in changed content"
$credentialPattern = ('(?i)(pass' + 'word|pass' + 'wd|sec' + 'ret)\s*[:=]\s*[^\s$<{]+|ro' + 'ot/ro' + 'ot')
Test-Gate "security.no-credentials" ($addedText -notmatch $credentialPattern) "no embedded credentials outside supplied task package"

$verifierText = Read-Text "tools/phantoms/verify-task-013.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "deterministic read-only verifier"

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
		$jarProduction = ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionCatalog.class")
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.progression" $jarProduction "production progression classes present"
Test-Gate "jar.no-tests" $jarTestsAbsent "test classes absent"

$total = $Pass + $Fail
Write-Output ("SUMMARY PASS=" + $Pass + " FAIL=" + $Fail + " TOTAL=" + $total)
if ($Fail -ne 0)
{
	exit 1
}
