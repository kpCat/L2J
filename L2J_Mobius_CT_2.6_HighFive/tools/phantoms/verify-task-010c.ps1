param()

$ErrorActionPreference = "Stop"
$Base = "030184205c6bf2101cb6256086c0b85c0e26dcd4"
$Branch = "feature/phantom-world"
$ExpectedSubject = "fix(phantoms): reconcile absent topology sources"
$ExpectedTopologyHash = "f8046ed902f024a9181f39b3247d8a6697279db4921ec0a69231c1e9b47cae7f"
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

function Read-BaseText
{
	param([string]$RelativePath)
	return Git-Text @("show", ($Base + ":" + $ModuleName + "/" + $RelativePath))
}

function Count-Matches
{
	param([string]$Text, [string]$Pattern)
	return ([regex]::Matches($Text, $Pattern)).Count
}

function Text-Between
{
	param([string]$Text, [string]$Start, [string]$End)
	$startIndex = $Text.IndexOf($Start)
	$endIndex = $Text.IndexOf($End, $startIndex + $Start.Length)
	if (($startIndex -lt 0) -or ($endIndex -lt 0))
	{
		return ""
	}
	return $Text.Substring($startIndex, $endIndex - $startIndex)
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
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 010B base exists"
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
$allowed = @(
	"build.xml",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologySchedulerSignalIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"tools/phantoms/verify-task-010c.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md",
	"docs/phantoms/reports/010b-topology-signal-ledger-bounds.md",
	"docs/phantoms/reports/010c-topology-absent-source-reconciliation.md",
	"docs/phantoms/reviews/010b-topology-signal-ledger-bounds-review.md"
)
$outside = @($changedFiles | Where-Object { ($_ -notin $allowed) -and ($_ -notlike "docs/phantoms/tasks/010c-topology-absent-source-reconciliation/*") })
Test-Gate "scope.changed-artifacts" ($changedFiles.Count -ge 15) ($changedFiles.Count.ToString() + " artifacts")
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "exact" } else { $outside -join "," })
Test-Gate "scope.high-five-only" (@($changedFiles | Where-Object { $_ -match "L2J_Mobius_CT_" }).Count -eq 0) "module-local paths"
Test-Gate "scope.no-topology-xml" (@($changedFiles | Where-Object { $_ -like "dist/game/data/phantoms/topology/*" }).Count -eq 0) "production corpus frozen"
Test-Gate "scope.no-config" (@($changedFiles | Where-Object { $_ -like "dist/game/config/*" }).Count -eq 0) "config frozen"
Test-Gate "scope.no-schema" (@($changedFiles | Where-Object { $_ -like "dist/db_installer/*" }).Count -eq 0) "DB schema frozen"
Test-Gate "scope.no-goal-011-012" (@($changedFiles | Where-Object { ($_ -match "/011-") -or ($_ -match "/012-") }).Count -eq 0) "future goals absent"
Test-Gate "scope.no-binaries" (@($changedFiles | Where-Object { $_ -match "\.(class|jar|zip|7z|dll|exe|png|jpg)$" }).Count -eq 0) "no binary artifacts"

$requiredArtifacts = @(
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologySchedulerSignalIntegrationSuite.java",
	"tools/phantoms/verify-task-010c.ps1",
	"docs/phantoms/tasks/010c-topology-absent-source-reconciliation/TASK.md",
	"docs/phantoms/reports/010c-topology-absent-source-reconciliation.md",
	"docs/phantoms/reviews/010b-topology-signal-ledger-bounds-review.md"
)
foreach ($artifact in $requiredArtifacts)
{
	Test-Gate ("artifact." + $artifact) (Test-Path -LiteralPath (Join-Path $ModuleRoot $artifact) -PathType Leaf) $artifact
}

$frozenGroups = [ordered]@{
	"loaders" = @(
		"java/org/l2jmobius/gameserver/data/xml/MapRegionData.java",
		"java/org/l2jmobius/gameserver/data/xml/NpcData.java",
		"java/org/l2jmobius/gameserver/data/xml/SpawnData.java",
		"java/org/l2jmobius/gameserver/data/SpawnTable.java",
		"java/org/l2jmobius/gameserver/data/xml/DoorData.java",
		"java/org/l2jmobius/gameserver/model/World.java"
	)
	"topology-loader" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyLoader.java")
	"topology-query" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyQuery.java")
	"topology-snapshot" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySnapshot.java")
	"generation-coordinator" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyGenerationCoordinator.java")
	"topology-service" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java")
	"profile-registry" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java")
	"topology-metrics" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java")
	"scheduler-adapter" = @("java/org/l2jmobius/gameserver/phantoms/topology/PhantomSchedulerRelevanceSignalPort.java")
	"scheduler" = @("java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java")
	"navigation" = @("java/org/l2jmobius/gameserver/phantoms/navigation")
	"decision" = @("java/org/l2jmobius/gameserver/phantoms/decision")
	"materialization" = @("java/org/l2jmobius/gameserver/phantoms/player")
	"profile" = @("java/org/l2jmobius/gameserver/phantoms/profile")
	"lifecycle" = @("java/org/l2jmobius/gameserver/Shutdown.java", "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java")
	"config" = @("dist/game/config")
	"schema" = @("dist/db_installer")
}
foreach ($group in $frozenGroups.GetEnumerator())
{
	$paths = @($group.Value | ForEach-Object { $ModuleName + "/" + $_ })
	$diff = Git-Text (@("diff", "--name-only", $Base, "--") + $paths)
	Test-Gate ("frozen." + $group.Key) ([string]::IsNullOrEmpty($diff)) "unchanged from Goal 010B"
}

$provider = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java"
$baseProvider = Read-BaseText "java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java"
$ledger = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java"
$integrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologySchedulerSignalIntegrationSuite.java"
$ledgerTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java"
$generationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java"
$build = Read-Text "build.xml"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"

$staleExpression = 'final boolean staleSafe = \(delivery == SignalDelivery\.STALE\) && \(\(previousState == SourceState\.NEVER_SUBMITTED\) \|\| \(previousState == SourceState\.INACTIVE_CONFIRMED\)\);'
Test-Gate "truth.safe-stale-exact" ((Count-Matches $provider $staleExpression) -eq 1) "safe states are exactly NEVER_SUBMITTED and INACTIVE_CONFIRMED"
Test-Gate "truth.safe-stale-inactive" ($provider.Contains("staleSafe ? SourceState.INACTIVE_CONFIRMED : SourceState.OWNERSHIP_UNCERTAIN")) "safe STALE writes inactive; unsafe STALE stays uncertain"
Test-Gate "truth.safe-stale-success" ($provider.Contains("((delivery == SignalDelivery.STALE) && !operation.signalFailure())")) "safe STALE is a successful withdrawal"
Test-Gate "truth.unsafe-stale-failure" ($provider.Contains("((delivery == SignalDelivery.STALE) && !staleSafe)") -and $provider.Contains("SourceState.OWNERSHIP_UNCERTAIN")) "possibly-active and uncertain STALE fail closed"
Test-Gate "truth.stale-not-scheduler-absence" ($provider.Contains("new SignalOperation(delivery, signalFailure, delivery == SignalDelivery.NOT_REGISTERED)") -and !$provider.Contains("staleSafe, true")) "STALE never proves scheduler absence"
Test-Gate "truth.all-not-registered-release" ($provider.Contains("releaseEligible && pass.allNotRegistered() && !profileRegistered")) "all-three NOT_REGISTERED release unchanged"
Test-Gate "truth.exclusive-provider-invariant" ($ledger.Contains("source keys are exclusively") -and $ledger.Contains("unregister/re-register")) "NEVER_SUBMITTED proof is provider-local"

$submitCurrent = Text-Between $provider "private void applySubmitResult" "private static boolean isImpossibleSubmit"
$submitBase = Text-Between $baseProvider "private void applySubmitResult" "private static boolean isImpossibleSubmit"
Test-Gate "truth.submit-classification-frozen" (($submitCurrent.Length -gt 0) -and ($submitCurrent -eq $submitBase)) "submit classification unchanged"
Test-Gate "ledger.structure-frozen" ((Count-Matches $ledger "private long _(localChat|combat|targetability)Sequence;") -eq 3 -and (Count-Matches $ledger "private SourceState _(localChat|combat|targetability)State") -eq 3 -and ($ledger -notmatch "\b(Map|Set|List|Collection|Queue)<")) "fixed ledger structure unchanged"

$integrationCaseCount = Count-Matches $integrationTests "registry\.add\("
Test-Gate "tests.real-adapter-suite" ($integrationTests.Contains("new PhantomScheduler(") -and $integrationTests.Contains("new PhantomSchedulerRelevanceSignalPort(") -and !$integrationTests.Contains("implements PhantomRelevanceSignalPort")) "actual scheduler and production adapter"
Test-Gate "tests.real-adapter-cases" ($integrationCaseCount -eq 5) ($integrationCaseCount.ToString() + " real integration cases")
Test-Gate "tests.fresh-no-event" ($integrationTests.Contains("fresh-no-event-unregister-reconciles-absent-sources") -and $integrationTests.Contains("signalLedgersCurrent()")) "fresh unregister and retained tombstone"
Test-Gate "tests.partial-source" ($integrationTests.Contains("partial-source-unregister-reconciles-never-submitted") -and $integrationTests.Contains("_localChatState") -and $integrationTests.Contains("_combatState")) "one active and two never-submitted sources"
Test-Gate "tests.reregistration-monotonic" ($integrationTests.Contains("reregistration-submit-is-monotonic-and-accepted") -and $integrationTests.Contains("submitSequence > cleanupSequence")) "newer accepted provider sequence"
Test-Gate "tests.reload-before-events" ($integrationTests.Contains("reload-before-events-reconciles-absent-sources") -and $integrationTests.Contains("ReloadResult.RELOADED")) "reload generation/hash/membership swap"
Test-Gate "tests.scheduler-absent-release" ($integrationTests.Contains("all-not-registered-releases-ledger") -and $integrationTests.Contains("scheduler.unregister(PROFILE_ID)")) "real adapter all-NOT_REGISTERED release"
Test-Gate "tests.possibly-active-stale" ($ledgerTests.Contains("stale-possibly-active-cleanup-fails-closed") -and $ledgerTests.Contains("STALE possibly-active cleanup was treated as success")) "possibly-active STALE remains failure"
Test-Gate "tests.uncertain-stale" ($ledgerTests.Contains("STALE uncertain cleanup retry was treated as success") -and $ledgerTests.Contains("STALE uncertain retry changed fail-closed ownership")) "uncertain STALE remains failure"
Test-Gate "tests.safe-stale-states" ($ledgerTests.Contains("STALE never-submitted cleanup was not accepted") -and $ledgerTests.Contains("STALE locally inactive cleanup was not accepted")) "both locally proven inactive states covered"
Test-Gate "tests.generation-preserved" ((Count-Matches $generationTests "registry\.add\(") -eq 17) "17 generation ownership cases retained"
Test-Gate "build.integration-route" ($build.Contains('name="phantom-topology-scheduler-signal-integration-test"') -and $launcher.Contains('case "topology-scheduler-signal-integration"')) "Ant target and launcher"
Test-Gate "build.verify-route" ($build.Contains("phantom-topology-scheduler-signal-integration-test") -and $build.Contains("verify-task-010c.ps1") -and $build.Contains("Run Goal 010C")) "cumulative verify includes Goal 010C"

$production = $provider + "`n" + $ledger
Test-Gate "lifecycle.no-new-production-worker" ($production -notmatch "\b(Executor|ScheduledFuture|CompletableFuture|new Thread|Thread\.of)\b") "no executor/thread/Future/task"

$docsPresent = $requiredArtifacts[4..5] | ForEach-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf }
Test-Gate "docs.required" (@($docsPresent | Where-Object { $_ }).Count -eq 2) "010B review and 010C report"

$textPaths = @($changedFiles | Where-Object { $_ -match "\.(java|xml|md|txt|json|ps1)$" -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf) })
$allChangedText = @($textPaths | ForEach-Object { Read-Text $_ }) -join "`n"
$trackedDiff = Git-Text @("diff", "--unified=0", $Base, "--", $ModuleName)
$addedText = (($trackedDiff -split "`r?`n") | Where-Object { $_ -match "^\+(?!\+\+\+)" } | ForEach-Object { $_.Substring(1) }) -join "`n"
$untrackedNow = (Git-Text @("ls-files", "--others", "--exclude-standard")) -split "`r?`n" | ForEach-Object { Module-Path $_ }
$untrackedText = @($textPaths | Where-Object { $_ -in $untrackedNow } | ForEach-Object { Read-Text $_ }) -join "`n"
$addedText = $addedText + "`n" + $untrackedText
$mojibakePairs = @(
	@(0x0420, 0x045f), @(0x0420, 0x045c), @(0x0420, 0x045b), @(0x0420, 0x2022),
	@(0x0420, 0x040e), @(0x0420, 0x203a), @(0x0420, 0x00a4), @(0x0420, 0x045a),
	@(0x0420, 0x0408), @(0x0420, 0x0459), @(0x0420, 0x0491), @(0x0420, 0x00b5),
	@(0x0420, 0x00b0), @(0x0420, 0x00bb), @(0x0420, 0x0405), @(0x0420, 0x0455),
	@(0x0421, 0x040f), @(0x0421, 0x20ac), @(0x0421, 0x0402), @(0x0421, 0x2039),
	@(0x0421, 0x040a), @(0x0421, 0x201a), @(0x0421, 0x0453), @(0x0421, 0x2021),
	@(0x0421, 0x2026), @(0x0421, 0x2020)
)
$mojibakeFree = !$allChangedText.Contains([char]0xfffd)
foreach ($pair in $mojibakePairs)
{
	$marker = ([string][char]$pair[0]) + ([char]$pair[1])
	$mojibakeFree = $mojibakeFree -and !$allChangedText.Contains($marker)
}
$escapedCyrillicFree = $allChangedText -notmatch '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
Test-Gate "encoding.mojibake" $mojibakeFree "no mojibake markers in changed text files"
Test-Gate "encoding.escaped-cyrillic" $escapedCyrillicFree "no escaped Cyrillic in changed text files"

$goal010Report = Read-Text "docs/phantoms/reports/010-topology-anchors-perception-graph.md"
$topologyDiff = Git-Text @("diff", "--name-only", $Base, "--", ($ModuleName + "/dist/game/data/phantoms/topology"))
$securityValid = ($addedText -notmatch '(?i)(password|passwd)\s*[=:]\s*[^\s]+') -and [string]::IsNullOrEmpty($topologyDiff) -and $goal010Report.Contains($ExpectedTopologyHash)
Test-Gate "security.credentials-and-corpus" $securityValid "no credentials; production topology unchanged"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarProvider = $false
$jarLedger = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarProvider = $entries -contains "org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.class"
		$jarLedger = $entries -contains "org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.class"
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-reconciliation" ($jarProvider -and $jarLedger) "GameServer.jar contains provider and ledger"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
