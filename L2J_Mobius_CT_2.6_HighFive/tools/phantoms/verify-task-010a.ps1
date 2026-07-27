param()

$ErrorActionPreference = "Stop"
$Base = "e80a641eebaefb59f1bef6bc398084375d2ecd8d"
$Branch = "feature/phantom-world"
$ExpectedSubject = "fix(phantoms): harden topology generation ownership"
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
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 010 base exists"
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
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomSchedulerRelevanceSignalPort.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyMetrics.java",
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyGenerationCoordinator.java",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerceptionSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyProductionCorpusSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"tools/phantoms/verify-task-010a.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/TOPOLOGY_PERCEPTION_CONTRACT.md",
	"docs/phantoms/reports/010-topology-anchors-perception-graph.md",
	"docs/phantoms/reports/010a-topology-generation-signal-ownership.md",
	"docs/phantoms/reviews/010-topology-anchors-perception-graph-review.md"
)
$outside = @($changedFiles | Where-Object { ($_ -notin $allowed) -and ($_ -notlike "docs/phantoms/tasks/010a-topology-generation-signal-ownership/*") })
Test-Gate "scope.changed-artifacts" ($changedFiles.Count -ge 12) ($changedFiles.Count.ToString() + " artifacts")
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "exact" } else { $outside -join "," })
Test-Gate "scope.high-five-only" (@($changedFiles | Where-Object { $_ -match "L2J_Mobius_CT_" }).Count -eq 0) "module-local paths"
Test-Gate "scope.no-topology-xml" (@($changedFiles | Where-Object { $_ -like "dist/game/data/phantoms/topology/*" }).Count -eq 0) "production corpus frozen"
Test-Gate "scope.no-config" (@($changedFiles | Where-Object { $_ -like "dist/game/config/*" }).Count -eq 0) "config frozen"
Test-Gate "scope.no-schema" (@($changedFiles | Where-Object { $_ -like "dist/db_installer/*" }).Count -eq 0) "DB schema frozen"
Test-Gate "scope.no-goal-011-012" (@($changedFiles | Where-Object { ($_ -match "/011-") -or ($_ -match "/012-") }).Count -eq 0) "future goals absent"
Test-Gate "scope.no-binaries" (@($changedFiles | Where-Object { $_ -match "\.(class|jar|zip|7z|dll|exe|png|jpg)$" }).Count -eq 0) "no binary artifacts"

$requiredArtifacts = @(
	"java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyGenerationCoordinator.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java",
	"tools/phantoms/verify-task-010a.ps1",
	"docs/phantoms/tasks/010a-topology-generation-signal-ownership/TASK.md",
	"docs/phantoms/reports/010a-topology-generation-signal-ownership.md",
	"docs/phantoms/reviews/010-topology-anchors-perception-graph-review.md"
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
	"navigation" = @("java/org/l2jmobius/gameserver/phantoms/navigation")
	"decision" = @("java/org/l2jmobius/gameserver/phantoms/decision")
	"scheduler" = @("java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java")
	"materialization" = @("java/org/l2jmobius/gameserver/phantoms/player")
	"profile" = @("java/org/l2jmobius/gameserver/phantoms/profile")
	"lifecycle" = @("java/org/l2jmobius/gameserver/Shutdown.java")
	"config" = @("dist/game/config")
	"schema" = @("dist/db_installer")
}
foreach ($group in $frozenGroups.GetEnumerator())
{
	$paths = @($group.Value | ForEach-Object { $ModuleName + "/" + $_ })
	$diff = Git-Text (@("diff", "--name-only", $Base, "--") + $paths)
	Test-Gate ("frozen." + $group.Key) ([string]::IsNullOrEmpty($diff)) "unchanged from Goal 010"
}

$coordinator = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyGenerationCoordinator.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyService.java"
$registry = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomTopologyProfileRegistry.java"
$provider = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomPerceptionProvider.java"
$port = Read-Text "java/org/l2jmobius/gameserver/phantoms/topology/PhantomRelevanceSignalPort.java"
$production = $coordinator + "`n" + $service + "`n" + $registry + "`n" + $provider + "`n" + $port
$generationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTopologyGenerationSuite.java"
$build = Read-Text "build.xml"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"

Test-Gate "coordinator.exactly-one-lock" ((Count-Matches $coordinator "private final ReentrantReadWriteLock _lock") -eq 1) "one lock field"
Test-Gate "coordinator.fair" ($coordinator.Contains("new ReentrantReadWriteLock(true)")) "fair ownership"
Test-Gate "coordinator.read-lease" ($coordinator.Contains("Lease read()") -and $coordinator.Contains("_lock.readLock()")) "read lease"
Test-Gate "coordinator.write-lease" ($coordinator.Contains("Lease write()") -and $coordinator.Contains("_lock.writeLock()")) "write lease"
Test-Gate "coordinator.exact-view" ($coordinator.Contains("record View(PhantomTopologyQuery query, long generation)")) "query plus generation"
Test-Gate "coordinator.service-owned" ((Count-Matches $service "new PhantomTopologyGenerationCoordinator\(\)") -eq 1) "one service-owned instance"
Test-Gate "coordinator.lock-before-monitor" ((Count-Matches $service "try \(PhantomTopologyGenerationCoordinator\.Lease ignored = _generationCoordinator\.(read|write)\(\)\)") -ge 10) "service acquires generation before monitor"

Test-Gate "profile.update-read-owner" ($service.Contains("return view == null ? UpdateResult.NOT_RUNNING : _profileRegistry.update(profileId, point, sequence, view.query(), view.generation())")) "resolution and commit inside read lease"
Test-Gate "profile.resolve-before-monitor" ($registry.IndexOf("final Optional<PhantomTopologyNode> resolved = query.mostSpecificNode(point);") -lt $registry.IndexOf("synchronized (_monitor)", $registry.IndexOf("UpdateResult update"))) "query resolution precedes registry monitor"
Test-Gate "profile.commit-generation" ($registry.Contains("if (_generation != requiredGeneration)") -and $registry.Contains("entry._topologyGeneration = requiredGeneration")) "exact generation at commit"
Test-Gate "profile.recipient-generation" ($registry.Contains("entry._topologyGeneration == requiredGeneration")) "recipient generation filter"
Test-Gate "profile.list-generation" ($registry.Contains("List<ProfileTopologySnapshot> listForNodes(Set<String> nodeIds, int limit, long requiredGeneration)")) "bounded exact-generation list"
Test-Gate "profile.sequence-preserved" ($registry.Contains("new CandidateEntry(profile.profileId(), profile.point(), profile.sequence(), nodeId)")) "candidate retains sequence"
Test-Gate "profile.no-public-constructor" ($registry -notmatch "public PhantomTopologyProfileRegistry\(") "registry construction is package-private"

$rebuildIndex = $service.IndexOf("_profileRegistry.rebuildCandidate")
$invalidateIndex = $service.IndexOf("_perceptionProvider.invalidateForReload", $rebuildIndex)
$installIndex = $service.IndexOf("_profileRegistry.installCandidate", $invalidateIndex)
$swapIndex = $service.IndexOf("_snapshot = candidate", $installIndex)
Test-Gate "reload.write-owner" ($service.Contains("_generationCoordinator.write()")) "reload uses write ownership"
Test-Gate "reload.rebuild-all" ($registry.Contains("_entries.values().stream().map(PhantomTopologyProfileRegistry::snapshot)") -and $registry.Contains("query.mostSpecificNode(profile.point())")) "all stored points re-resolved"
Test-Gate "reload.unresolved-explicit" ($registry.Contains("resolved.map(PhantomTopologyNode::id).orElse(null)")) "true unresolved membership"
Test-Gate "reload.invalidation-before-install" (($rebuildIndex -ge 0) -and ($rebuildIndex -lt $invalidateIndex) -and ($invalidateIndex -lt $installIndex)) "source invalidation precedes membership install"
Test-Gate "reload.install-before-swap" (($installIndex -ge 0) -and ($installIndex -lt $swapIndex)) "candidate membership precedes topology swap"
Test-Gate "reload.failure-explicit" ($service.Contains("REJECTED_SIGNAL_INVALIDATION")) "invalidation failure result"
Test-Gate "reload.failure-no-swap" ($service.IndexOf("return ReloadResult.REJECTED_SIGNAL_INVALIDATION;") -lt $swapIndex) "old snapshot retained on cleanup failure"
Test-Gate "reload.overflow-safe" ($service.Contains("_snapshot.generation() == Long.MAX_VALUE") -and $service.Contains("Math.addExact(expectedGeneration, 1L)")) "generation cannot wrap"

Test-Gate "event.local-chat-owner" ($provider.Contains("public EventResult localChat") -and $provider.Contains("_generationCoordinator.read()")) "local chat read ownership"
Test-Gate "event.combat-owner" ($provider.Contains("public EventResult combat")) "combat shares read ownership"
Test-Gate "event.targetability-owner" ($provider.Contains("public EventResult targetability")) "targetability shares read ownership"
Test-Gate "event.exact-view" ($provider.Contains("final View view = _viewSupplier.get()")) "owned query/generation view"
Test-Gate "event.exact-recipient-list" ($provider.Contains("_registry.listForNodes(perceptibleNodes, _policy.maximumRecipientsPerEvent(), view.generation())")) "exact-generation fanout"
Test-Gate "event.final-registration-check" ($provider.Contains("_registry.find(profileId, topologyGeneration).isEmpty()")) "registration checked immediately before submit"
Test-Gate "event.final-generation-argument" ($provider.Contains("deliver(token, recipient.getKey(), sourceKey, recipient.getValue(), ttlMillis, view.generation(), true)")) "delivery carries owned generation"
Test-Gate "event.delivery-serialized" ($provider.Contains("synchronized (_deliveryGate)")) "submit/unregister ordering gate"
Test-Gate "event.stop-token" ($provider.Contains("_activeEventTokens") -and $provider.Contains("_eventGeneration") -and $provider.Contains("finishStop")) "bounded quiescence token"
Test-Gate "event.no-direct-runtime-action" ($provider -notmatch "PhantomNavigation|PhantomMaterialization|Player|Creature") "scheduler signal only"

Test-Gate "cleanup.fixed-sources" ($provider.Contains("List.of(LOCAL_CHAT_SOURCE, COMBAT_SOURCE, TARGETABILITY_SOURCE)")) "three owned sources"
Test-Gate "cleanup.unregister-after-removal" ($provider.IndexOf("_registry.remove(profileId, generation)") -lt $provider.IndexOf("cleanupSources(profileId)", $provider.IndexOf("UnregisterAttempt unregisterProfile"))) "membership removed before withdrawals"
Test-Gate "cleanup.inactive-without-registration" ($provider.Contains("withdrawEvent(token, event.targetProfileId(), TARGETABILITY_SOURCE)")) "inactive targetability always withdraws"
Test-Gate "cleanup.failure-pending" ($provider.Contains("_pendingCleanup.add(profileId)")) "failure remains explicit"
Test-Gate "cleanup.retryable" ($provider.Contains("CleanupStatus retryProfileSignalCleanup(long profileId)")) "explicit retry path"
Test-Gate "cleanup.reregister-guard" ($provider.Contains("return RegistrationResult.CLEANUP_PENDING")) "unsafe re-registration blocked"
Test-Gate "cleanup.monotonic-sequence" ($provider.Contains("Math.addExact(current, 1L)") -and $provider.Contains("SEQUENCE_EXHAUSTED")) "overflow-safe source sequence"
Test-Gate "cleanup.no-hidden-worker" ($production -notmatch "\b(Executor|ScheduledFuture|CompletableFuture|new Thread|Thread\.of)\b") "synchronous bounded cleanup"

$generationCaseCount = Count-Matches $generationTests "registry\.add\("
Test-Gate "tests.generation-cases" ($generationCaseCount -eq 17) ($generationCaseCount.ToString() + " focused cases")
Test-Gate "tests.race-and-quiescence" ($generationTests.Contains("reload-update-event-stop-no-deadlock") -and $generationTests.Contains("finishStop()")) "race coverage"
Test-Gate "tests.reload-retention" ($generationTests.Contains("reload-invalidation-failure-retains-generation") -and $generationTests.Contains("rejected-reload-preserves-profile")) "failure retention coverage"
Test-Gate "tests.signal-lifecycle" ($generationTests.Contains("inactive-targetability-after-unregister") -and $generationTests.Contains("cleanup-retry-monotonic")) "cleanup coverage"
Test-Gate "build.generation-route" ($build.Contains('name="phantom-topology-generation-test"') -and $launcher.Contains('case "topology-generation"')) "target and launcher"

$docsPresent = $requiredArtifacts[4..5] | ForEach-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf }
$textPaths = @($changedFiles | Where-Object { $_ -match "\.(java|xml|md|txt|json|ps1)$" -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf) })
$trackedDiff = Git-Text @("diff", "--unified=0", $Base, "--", $ModuleName)
$addedText = (($trackedDiff -split "`r?`n") | Where-Object { ($_ -match "^\+(?!\+\+\+)") } | ForEach-Object { $_.Substring(1) }) -join "`n"
$untrackedNow = (Git-Text @("ls-files", "--others", "--exclude-standard")) -split "`r?`n" | ForEach-Object { Module-Path $_ }
$untrackedText = @($textPaths | Where-Object { $_ -in $untrackedNow } | ForEach-Object { Read-Text $_ }) -join "`n"
$addedText = $addedText + "`n" + $untrackedText
Test-Gate "docs.required" (@($docsPresent | Where-Object { $_ }).Count -eq 2) "review and hardening report"
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

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
