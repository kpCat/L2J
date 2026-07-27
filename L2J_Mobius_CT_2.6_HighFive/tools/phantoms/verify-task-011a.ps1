param()

$ErrorActionPreference = "Stop"
$Base = "dc4659fea3e76a78841dfee0429bc4ab1ed2b185"
$Branch = "feature/phantom-world"
$ExpectedSubject = "fix(phantoms): harden game knowledge parity and queries"
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
$commitCount = [int](Git-Text @("rev-list", "--count", ($Base + "..HEAD"))
)
$phaseValid = ($head -eq $Base) -or ($commitCount -eq 1)
$parentValid = ($head -eq $Base) -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Base)
$subjectValid = ($head -eq $Base) -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $ExpectedSubject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))

Test-Gate "repository.module-root" ((Split-Path $ModuleRoot -Leaf) -eq "L2J_Mobius_CT_2.6_HighFive") "High Five module"
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 011 base exists"
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

$allowedExact = @(
	"build.xml",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java",
	"java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeQueryTruthSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"tools/phantoms/verify-task-011a.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md",
	"docs/phantoms/reports/011-authoritative-game-knowledge.md",
	"docs/phantoms/reports/011a-knowledge-parity-query-truth.md",
	"docs/phantoms/reviews/011-authoritative-game-knowledge-review.md"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "docs/phantoms/tasks/011a-knowledge-parity-query-truth/*") -and
	($_ -notlike "dist/game/data/geodata/*.l2j")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "only Goal 011A files plus ignored user geodata" } else { $outside -join "," })

$frozenPaths = @(
	($ModuleName + "/dist/game/data/stats/npcs/29100-29199.xml"),
	($ModuleName + "/dist/game/data/phantoms/knowledge"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data/xml/ItemData.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data/xml/NpcData.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data/xml/RecipeData.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data/SpawnTable.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"),
	($ModuleName + "/dist/game/config")
)
foreach ($path in $frozenPaths)
{
	Test-Gate ("frozen." + ($path.Replace("/", ".").Replace("\", "."))) (Git-Succeeds @("diff", "--quiet", $Base, "--", $path)) $path
}

$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/L2jGameKnowledgeBackend.java"
$builder = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeBuilder.java"
$model = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel.java"
$query = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeQuery.java"
$snapshot = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeSnapshot.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.java"
$core = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeCoreSuite.java"
$parity = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeParitySuite.java"
$queryTruth = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeQueryTruthSuite.java"
$content = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgeContentSuite.java"
$performance = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomGameKnowledgePerformanceSuite.java"
$skeleton = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"

$dropSection = $backend.Substring($backend.IndexOf("private static List<DropFact> copyDrops"), $backend.IndexOf("private static void addDrop") - $backend.IndexOf("private static List<DropFact> copyDrops"))
Test-Gate "drops.no-preordinal-sort" (!$dropSection.Contains("holders.sort(") -and !$dropSection.Contains("groups.sort(") -and !$backend.Contains("DROP_HOLDER_ORDER")) "no group/holder sort before ordinals"
Test-Gate "drops.group-list-index" ($dropSection.Contains("groups.get(groupOrdinal)") -and $dropSection.Contains("holders.get(itemOrdinal)")) "exact grouped list indexes"
Test-Gate "drops.ungrouped-list-index" ($dropSection.Contains("source.get(ordinal)") -and $dropSection.Contains("ChanceModel.UNGROUPED_INDEPENDENT")) "exact ungrouped list indexes"
Test-Gate "drops.per-npc-runtime-order" ($snapshot.Contains("NPC_DROP_ORDER") -and $snapshot.Contains("DropFact::groupOrdinal") -and $snapshot.Contains("DropFact::itemOrdinal")) "per-NPC index carries runtime order"
Test-Gate "drops.hash-ordinals" ($snapshot.Contains("integer(fact.groupOrdinal()).integer(fact.itemOrdinal())")) "drop hash includes ordinals"

Test-Gate "recipes.no-silent-continue" (!$backend.Substring($backend.IndexOf("private static List<RecipeFact> copyRecipes")).Contains("continue;")) "recipe copy has no silent continue"
Test-Gate "recipes.ambiguity-category" ($backend.Contains('failure("ambiguity"') -and $backend.Contains("copyRecipesByListId")) "ambiguity is detected and bounded"
Test-Gate "recipes.public-list-resolution" ($backend.Contains("data.getRecipeList(listId)") -and $backend.Contains("Arrays.equals(expectedItemIds, resolvedItemIds)")) "list count and item multiset exact"
Test-Gate "recipes.builder-list-identity" ($builder.Contains("recipeLists") -and $builder.Contains("fact.recipeListId()")) "builder validates unique list identity"

Test-Gate "query.requested-empty" ($query.Contains("isRequestedEmpty") -and $query.Contains("recordTargetCandidates(0, 0)")) "requested empty set returns immediately"
Test-Gate "query.lightweight-area" ($model.Contains("record SpawnAreaSummary") -and $query.Contains("KnowledgePage<SpawnAreaSummary> spawnAreas")) "public area summary"
Test-Gate "query.summary-no-points" (!$model.Substring($model.IndexOf("record SpawnAreaSummary"), $model.IndexOf("record IngredientFact") - $model.IndexOf("record SpawnAreaSummary")).Contains("representativePoints")) "summary has no nested points"
Test-Gate "query.target-cap" ($model.Contains("representativeAreas.size() > 64") -and $query.Contains("Math.min(64")) "TargetFact maximum 64 summaries"
Test-Gate "query.exact-points-page" ($query.Contains("KnowledgePage<SpawnFact> spawnFacts")) "exact points only via paged facts"

Test-Gate "diagnostics.hash-record" ($snapshot.Contains("record Hashes(String itemsHash") -and $service.Contains("PhantomGameKnowledgeSnapshot.Hashes hashes")) "all hashes exposed by service"
Test-Gate "diagnostics.none" ($snapshot.Contains('new Hashes("none", "none", "none", "none", "none", "none", "none", "none", "none")')) "inactive hashes fixed"

Test-Gate "parity.direct-items-npcs" ($parity.Contains("ItemData.getInstance()") -and $parity.Contains("NpcData.getInstance()")) "direct loader expected facts"
Test-Gate "parity.direct-spawns-recipes" ($parity.Contains("SpawnTable.getInstance()") -and $parity.Contains("RecipeData.getInstance()")) "direct spawn/recipe expected facts"
Test-Gate "parity.not-self-referential" (!$parity.Contains("_loaded") -and !$parity.Contains("backend.load(policy)")) "expected facts do not call backend"
Test-Gate "parity.zaken-order" ($parity.Contains("13144") -and $parity.Contains("13143")) "known Zaken runtime order"
Test-Gate "parity.recipe-ambiguity" ($parity.Contains("duplicate-recipe-item-fails-closed") -and $parity.Contains("InvocationTargetException")) "focused ambiguity negative"

$coreCases = Count-Matches $core "registry\.add\("
$parityCases = Count-Matches $parity "registry\.add\("
$queryCases = Count-Matches $queryTruth "registry\.add\("
$contentCases = Count-Matches $content "registry\.add\("
$performanceCases = Count-Matches $performance "registry\.add\("
Test-Gate "tests.core-cases" ($coreCases -ge 48) ($coreCases.ToString() + " cases")
Test-Gate "tests.parity-cases" ($parityCases -ge 20) ($parityCases.ToString() + " cases")
Test-Gate "tests.query-truth-cases" ($queryCases -ge 8) ($queryCases.ToString() + " cases")
Test-Gate "tests.content-cases" ($contentCases -eq 18) ($contentCases.ToString() + " cases")
Test-Gate "tests.performance-cases" ($performanceCases -eq 8 -and $performance.Contains("100_000")) ($performanceCases.ToString() + " cases")
Test-Gate "tests.skeleton-hashes" ($skeleton.Contains("gameKnowledge().hashes()")) "inactive/running/stopped hash diagnostics"

foreach ($mode in @("knowledge-core", "knowledge-query-truth", "knowledge-parity", "knowledge-content", "knowledge-performance"))
{
	Test-Gate ("launcher." + $mode) ($launcher.Contains('case "' + $mode + '"')) $mode
}
foreach ($target in @("phantom-game-knowledge-core-test", "phantom-game-knowledge-query-truth-test", "phantom-game-knowledge-parity-test", "phantom-game-knowledge-content-test", "phantom-game-knowledge-performance-smoke"))
{
	Test-Gate ("build." + $target) ($build.Contains('name="' + $target + '"')) $target
}
Test-Gate "build.verify-route" ($build.Contains("phantom-game-knowledge-query-truth-test") -and $build.Contains('description="Run Goal 011A')) "Goal 011A is cumulative"

$contract = Read-Text "docs/phantoms/architecture/GAME_KNOWLEDGE_CONTRACT.md"
$report = Read-Text "docs/phantoms/reports/011a-knowledge-parity-query-truth.md"
$review = Read-Text "docs/phantoms/reviews/011-authoritative-game-knowledge-review.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
Test-Gate "docs.contract" ($contract.Contains("runtime/source") -and $contract.Contains("SpawnAreaSummary") -and $contract.Contains("requested exact")) "hardened contract"
Test-Gate "docs.review" ($review.Contains("Verdict: FIX_REQUIRED") -and $review.Contains("Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW")) "review findings preserved"
Test-Gate "docs.report" ($report.Contains("Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $report.Contains("Goal 012: BLOCKED") -and $report.Contains("Goal 013: NOT_STARTED")) "required report state"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 011A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 012: BLOCKED")) "roadmap gate"

$diffText = Git-Text @("diff", "--unified=0", $Base, "--", ($ModuleName + "/build.xml"), ($ModuleName + "/java"), ($ModuleName + "/test"), ($ModuleName + "/tools"), ($ModuleName + "/docs/PHANTOM_BOTS_ROADMAP.md"), ($ModuleName + "/docs/phantoms/architecture"), ($ModuleName + "/docs/phantoms/reports"), ($ModuleName + "/docs/phantoms/reviews"))
$addedText = @(($diffText -split "`r?`n") | Where-Object { $_.StartsWith("+") -and !$_.StartsWith("+++") } | ForEach-Object { $_.Substring(1) }) -join "`n"
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
Test-Gate "encoding.mojibake-added-lines" ($mojibakeFound.Count -eq 0) "no new mojibake markers"
Test-Gate "encoding.escaped-cyrillic-added-lines" ($addedText -notmatch $escapedPattern) "no new escaped Cyrillic"

$verifierText = Read-Text "tools/phantoms/verify-task-011a.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "deterministic read-only verifier"

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
		$jarKnowledge = ($entries -contains "org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeService.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/knowledge/PhantomGameKnowledgeModel`$SpawnAreaSummary.class")
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-knowledge" $jarKnowledge "GameServer.jar contains hardened knowledge classes"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
