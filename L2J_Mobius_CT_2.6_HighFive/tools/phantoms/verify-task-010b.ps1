param()

$ErrorActionPreference = "Stop"
$Base = "f7eb90ecf3badfc615e6ee700d392a5cbb815811"
$Branch = "feature/phantom-world"
$ExpectedSubject = "fix(phantoms): bound topology signal ownership"
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
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 010A base exists"
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
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"tools/phantoms/verify-task-010b.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md",
	"docs/phantoms/reports/010a-topology-generation-signal-ownership.md",
	"docs/phantoms/reports/010b-topology-signal-ledger-bounds.md",
	"docs/phantoms/reviews/010a-topology-generation-signal-ownership-review.md"
)
$outside = @($changedFiles | Where-Object { ($_ -notin $allowed) -and ($_ -notlike "docs/phantoms/tasks/010b-topology-signal-ledger-bounds/*") })
Test-Gate "scope.changed-artifacts" ($changedFiles.Count -ge 12) ($changedFiles.Count.ToString() + " artifacts")
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "exact" } else { $outside -join "," })
Test-Gate "scope.high-five-only" (@($changedFiles | Where-Object { $_ -match "L2J_Mobius_CT_" }).Count -eq 0) "module-local paths"
Test-Gate "scope.no-topology-xml" (@($changedFiles | Where-Object { $_ -like "dist/game/data/phantoms/topology/*" }).Count -eq 0) "production corpus frozen"
Test-Gate "scope.no-config" (@($changedFiles | Where-Object { $_ -like "dist/game/config/*" }).Count -eq 0) "config frozen"
Test-Gate "scope.no-schema" (@($changedFiles | Where-Object { $_ -like "dist/db_installer/*" }).Count -eq 0) "DB schema frozen"
Test-Gate "scope.no-goal-011-012" (@($changedFiles | Where-Object { ($_ -match "/011-") -or ($_ -match "/012-") }).Count -eq 0) "future goals absent"
Test-Gate "scope.no-binaries" (@($changedFiles | Where-Object { $_ -match "\.(class|jar|zip|7z|dll|exe|png|jpg)$" }).Count -eq 0) "no binary artifacts"

$requiredArtifacts = @(
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java",
	"tools/phantoms/verify-task-010b.ps1",
	"docs/phantoms/tasks/010b-topology-signal-ledger-bounds/TASK.md",
	"docs/phantoms/reports/010b-topology-signal-ledger-bounds.md",
	"docs/phantoms/reviews/010a-topology-generation-signal-ownership-review.md"
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
	Test-Gate ("frozen." + $group.Key) ([string]::IsNullOrEmpty($diff)) "unchanged from Goal 010A"
}

$provider = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java"
$ledger = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java"
$registry = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java"
$metrics = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java"
$port = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java"
$production = $provider + "`n" + $ledger + "`n" + $service + "`n" + $registry + "`n" + $metrics + "`n" + $port
$ledgerTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologySignalLedgerSuite.java"
$generationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java"
$perceptionTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java"
$performanceTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java"
$build = Read-Text "build.xml"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"

Test-Gate "ledger.dynamic-sequence-map-removed" (!$provider.Contains("_sequences") -and !$provider.Contains("SequenceKey")) "unbounded sequence identity map absent"
Test-Gate "ledger.pending-set-removed" (!$provider.Contains("_pendingCleanup")) "standalone pending-cleanup set absent"
Test-Gate "ledger.one-profile-map" ((Count-Matches $provider "Map<Long, PhantomTopologySignalLedger> _signalLedgers") -eq 1) "one per-profile ledger map"
Test-Gate "ledger.fixed-source-count" ($provider.Contains("List.of(LOCAL_CHAT_SOURCE, COMBAT_SOURCE, TARGETABILITY_SOURCE)") -and ((Count-Matches $ledger "private long _(localChat|combat|targetability)Sequence;") -eq 3)) "three fixed source slots"
Test-Gate "ledger.fixed-state-count" ((Count-Matches $ledger "private SourceState _(localChat|combat|targetability)State") -eq 3) "three fixed source truth states"
Test-Gate "ledger.no-dynamic-collection" ($ledger -notmatch "\b(Map|Set|List|Collection|Queue)<") "ledger contains no dynamic source/history collection"
Test-Gate "ledger.fixed-cleanup-flags" ($ledger.Contains("private boolean _cleanupPending;") -and $ledger.Contains("private boolean _cleanupInFlight;")) "cleanup flags share the ledger"
Test-Gate "ledger.capacity-policy" ($provider.Contains("_signalLedgers.size() >= _policy.maximumRegisteredProfiles()") -and $provider.Contains("RegistrationResult.SIGNAL_LEDGER_CAPACITY")) "capacity equals maximumRegisteredProfiles"
Test-Gate "ledger.capacity-result" ($registry.Contains("SIGNAL_LEDGER_CAPACITY")) "explicit registration result"

$reserveIndex = $provider.IndexOf("_signalLedgers.put(profileId, ledger)")
$publishIndex = $provider.IndexOf("_registry.register(profileId, generation)", $reserveIndex)
$rollbackIndex = $provider.IndexOf("created && (registration != RegistrationResult.REGISTERED)", $publishIndex)
Test-Gate "registration.reserve-before-publication" (($reserveIndex -ge 0) -and ($reserveIndex -lt $publishIndex)) "ledger reserved before registry publication"
Test-Gate "registration.rollback-empty-reservation" (($rollbackIndex -gt $publishIndex) -and $provider.Contains("ledger.isEmptyReservation()")) "failed registry registration releases only new empty ledger"
Test-Gate "registration.retained-reuse" ($provider.Contains("if (existing != null)") -and $provider.Contains("ledger = existing")) "retained identity reuses monotonic ledger"
Test-Gate "registration.cleanup-guard" ($provider.Contains("existing.cleanupPending() || existing.cleanupInFlight()") -and $provider.Contains("RegistrationResult.CLEANUP_PENDING")) "pending or in-flight cleanup blocks registration"

$inactiveIndex = $provider.IndexOf("private SignalOperation withdrawEvent")
$inactiveLedgerIndex = $provider.IndexOf("final PhantomTopologySignalLedger ledger = signalLedger(profileId)", $inactiveIndex)
$inactiveNoOwnerIndex = $provider.IndexOf("new SignalOperation(SignalDelivery.NOT_REGISTERED, false, true)", $inactiveLedgerIndex)
$inactivePortIndex = $provider.IndexOf("return withdrawSource(ledger, sourceKey)", $inactiveNoOwnerIndex)
Test-Gate "targetability.never-owned-no-allocation" (($inactiveLedgerIndex -ge 0) -and ($inactiveLedgerIndex -lt $inactiveNoOwnerIndex) -and ($inactiveNoOwnerIndex -lt $inactivePortIndex)) "missing ledger returns before allocation and port call"
Test-Gate "targetability.existing-ledger-only" ($provider.Contains("operation = withdrawEvent(token, event.targetProfileId(), TARGETABILITY_SOURCE)")) "inactive targetability uses retained ownership"

Test-Gate "truth.states" ($ledger.Contains("NEVER_SUBMITTED") -and $ledger.Contains("POSSIBLY_ACTIVE") -and $ledger.Contains("INACTIVE_CONFIRMED") -and $ledger.Contains("OWNERSHIP_UNCERTAIN")) "fixed source truth states"
Test-Gate "truth.submit-accepted-active" ($provider.Contains("ledger.sourceState(sourceKey, SourceState.POSSIBLY_ACTIVE)")) "accepted/coalesced submit becomes possibly active"
Test-Gate "truth.submit-transient-unchanged" ($provider.Contains("isImpossibleSubmit") -and $provider.Contains("SignalDelivery.BACKPRESSURE") -and $provider.Contains("SignalDelivery.NOT_REGISTERED")) "transient submit outcomes remain separately classified"
Test-Gate "truth.submit-impossible-fails" ($provider.Contains("(delivery == SignalDelivery.STALE) || (delivery == SignalDelivery.REJECTED) || (delivery == SignalDelivery.NOT_RUNNING) || (delivery == SignalDelivery.SEQUENCE_EXHAUSTED)")) "impossible submit statuses fail closed"
Test-Gate "truth.withdraw-confirmed" ($provider.Contains("ledger.sourceState(sourceKey, SourceState.INACTIVE_CONFIRMED)")) "accepted/coalesced/not-registered withdrawal confirms inactive"
Test-Gate "truth.stale-local-proof" ($provider.Contains("(delivery == SignalDelivery.STALE) && (previousState != SourceState.INACTIVE_CONFIRMED)") -and $provider.Contains("(previousState == SourceState.INACTIVE_CONFIRMED)")) "STALE succeeds only from local inactive proof"
Test-Gate "truth.stale-uncertain" ($provider.Contains("SourceState.OWNERSHIP_UNCERTAIN")) "ambiguous STALE fails closed"
Test-Gate "truth.sequence-overflow" ($ledger.Contains("== Long.MAX_VALUE") -and $provider.Contains("recordSignalSequenceExhausted")) "fixed source sequence cannot wrap"

Test-Gate "cleanup.all-three-pass" ($provider.Contains("boolean allNotRegistered = true") -and $provider.Contains("allNotRegistered &= operation.schedulerAbsent()")) "same cleanup pass tracks all three NOT_REGISTERED results"
Test-Gate "cleanup.release-proof" ($provider.Contains("releaseEligible && pass.allNotRegistered() && !profileRegistered")) "release requires scheduler absence and no topology registration"
Test-Gate "cleanup.accepted-retained" (!$provider.Contains("isSuccessfulWithdrawal(SignalDelivery delivery)") -and $provider.Contains("recordSignalLedgerReleased")) "accepted withdrawal alone does not release"
Test-Gate "cleanup.retry-existing-only" ($provider.Contains("ledger = _signalLedgers.get(profileId)") -and $provider.Contains("ledger.cleanupPending()")) "retry consumes existing tombstone"
Test-Gate "cleanup.reload-existing-only" ($provider.Contains("complete &= cleanupSources(profileId, false)") -and ((Count-Matches $provider "new PhantomTopologySignalLedger") -eq 1)) "reload creates no ledger"
Test-Gate "cleanup.no-background-retry" ($production -notmatch "\b(Executor|ScheduledFuture|CompletableFuture|new Thread|Thread\.of)\b") "no executor/thread/Future/task"
Test-Gate "stop.inflight-guard" ($provider.Contains("anyMatch(PhantomTopologySignalLedger::cleanupInFlight)")) "finishStop rejects ledger cleanup in flight"
Test-Gate "stop.final-clear" ($provider.Contains("_signalLedgers.clear()") -and $provider.Contains("_metrics.clearSignalLedgers()")) "final stop clears ledgers and gauge"

Test-Gate "metrics.current-peak-capacity" ($metrics.Contains("_signalLedgersCurrent") -and $metrics.Contains("_signalLedgersPeak") -and $metrics.Contains("_signalLedgerCapacity")) "aggregate ledger gauges"
Test-Gate "metrics.service-exposure" ($service.Contains("signalLedgersCurrent") -and $service.Contains("signalLedgersPeak") -and $service.Contains("signalLedgerCapacity")) "service snapshot exposes aggregate gauges only"

$ledgerCaseCount = Count-Matches $ledgerTests "registry\.add\("
Test-Gate "tests.ledger-cases" ($ledgerCaseCount -eq 20) ($ledgerCaseCount.ToString() + " focused cases")
Test-Gate "tests.never-owned" ($ledgerTests.Contains("never-owned-inactive-target-no-state-or-port-call")) "zero allocation and port-call coverage"
Test-Gate "tests.churn-and-capacity" ($ledgerTests.Contains("high-identity-churn-remains-bounded") -and $ledgerTests.Contains("retained-identities-reach-exact-capacity") -and $ledgerTests.Contains("failed-cleanup-counts-against-capacity")) "active/retained/failed bounds coverage"
Test-Gate "tests.truth" ($ledgerTests.Contains("stale-possibly-active-cleanup-fails-closed") -and $ledgerTests.Contains("stale-confirmed-inactive-cleanup-is-safe-retained") -and $ledgerTests.Contains("stale-submit-is-signal-failure")) "STALE and submit truth coverage"
Test-Gate "tests.release-and-stop" ($ledgerTests.Contains("all-not-registered-cleanup-releases-ledger") -and $ledgerTests.Contains("all-not-registered-retry-releases-ledger") -and $ledgerTests.Contains("final-stop-clears-all-ledgers")) "release proof coverage"
Test-Gate "tests.concurrent-capacity" ($ledgerTests.Contains("concurrent-registration-event-unregister-retry-is-bounded")) "concurrent capacity coverage"
Test-Gate "tests.generation-preserved" ((Count-Matches $generationTests "registry\.add\(") -eq 17) "17 generation ownership cases retained"
Test-Gate "tests.perception-preserved" ((Count-Matches $perceptionTests "registry\.add\(") -eq 28) "28 perception cases retained"
Test-Gate "tests.performance-ledger" ($performanceTests.Contains("signalLedgersCurrent()") -and $performanceTests.Contains("signalLedgerCapacity()")) "10000-profile ledger performance assertions"
Test-Gate "build.ledger-route" ($build.Contains('name="phantom-topology-signal-ledger-test"') -and $launcher.Contains('case "topology-signal-ledger"')) "Ant target and launcher"
Test-Gate "build.verify-route" ($build.Contains("phantom-topology-signal-ledger-test") -and $build.Contains("verify-task-010b.ps1")) "cumulative verify includes Goal 010B"

$docsPresent = $requiredArtifacts[4..5] | ForEach-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf }
Test-Gate "docs.required" (@($docsPresent | Where-Object { $_ }).Count -eq 2) "010A review and 010B report"

$textPaths = @($changedFiles | Where-Object { $_ -match "\.(java|xml|md|txt|json|ps1)$" -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf) })
$trackedDiff = Git-Text @("diff", "--unified=0", $Base, "--", $ModuleName)
$addedText = (($trackedDiff -split "`r?`n") | Where-Object { ($_ -match "^\+(?!\+\+\+)") } | ForEach-Object { $_.Substring(1) }) -join "`n"
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
$mojibakeFree = !$addedText.Contains([char]0xfffd)
foreach ($pair in $mojibakePairs)
{
	$marker = ([string][char]$pair[0]) + ([char]$pair[1])
	$mojibakeFree = $mojibakeFree -and !$addedText.Contains($marker)
}
$escapedCyrillicFree = $addedText -notmatch '\\\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};'
Test-Gate "encoding.mojibake" $mojibakeFree "no new mojibake markers"
Test-Gate "encoding.escaped-cyrillic" $escapedCyrillicFree "no new escaped Cyrillic"

$goal010Report = Read-Text "docs/phantoms/reports/010-topology-anchors-perception-graph.md"
$topologyDiff = Git-Text @("diff", "--name-only", $Base, "--", ($ModuleName + "/dist/game/data/phantoms/topology"))
$securityValid = ($addedText -notmatch '(?i)(password|passwd)\s*[=:]\s*[^\s]+') -and [string]::IsNullOrEmpty($topologyDiff) -and $goal010Report.Contains($ExpectedTopologyHash)
Test-Gate "security.credentials-and-corpus" $securityValid "no credentials; production topology unchanged and canonical hash preserved"

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
		$jarProduction = $entries -contains "org/l2jmobius/gameserver/phantoms/topology/PhantomTopologySignalLedger.class"
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-ledger" $jarProduction "GameServer.jar contains the fixed ledger"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
