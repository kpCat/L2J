param()

$ErrorActionPreference = "Stop"
$Base = "003604b4f7bda2a8d224d0adcf6349c088154e10"
$Branch = "feature/phantom-world"
$ExpectedSubject = "feat(phantoms): add capability driven combat kernel"
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
$commitCount = [int](Git-Text @("rev-list", "--count", ($Base + "..HEAD")))
$phaseValid = ($head -eq $Base) -or ($commitCount -eq 1)
$parentValid = ($head -eq $Base) -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Base)
$subjectValid = ($head -eq $Base) -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $ExpectedSubject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))

Test-Gate "repository.module-root" ((Split-Path $ModuleRoot -Leaf) -eq "L2J_Mobius_CT_2.6_HighFive") "High Five module"
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.base" ((Git-Text @("cat-file", "-t", $Base)) -eq "commit") "Goal 012 base exists"
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
	"java/org/l2jmobius/gameserver/Shutdown.java",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
	"tools/phantoms/verify-task-012.ps1",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md",
	"docs/phantoms/reports/011a-knowledge-parity-query-truth.md",
	"docs/phantoms/reports/012-capability-driven-combat-kernel.md",
	"docs/phantoms/reviews/011a-knowledge-parity-query-truth-review.md"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/combat/*") -and
	($_ -notlike "docs/phantoms/tasks/012-capability-driven-combat-kernel/*") -and
	($_ -notlike "dist/game/data/geodata/*.l2j")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "only Goal 012 files plus ignored user geodata" } else { $outside -join "," })

$geodataChanged = @($changed | Where-Object { $_ -like "dist/game/data/geodata/*.l2j" })
Test-Gate "scope.geodata-untracked-only" ($geodataChanged.Count -eq 203) ($geodataChanged.Count.ToString() + " preserved user files")
Test-Gate "scope.no-binaries" (@($changed | Where-Object { $_ -match '\.(jar|class|exe|dll|zip|7z|png|jpg)$' }).Count -eq 0) "no binary artifacts"
Test-Gate "scope.no-goal-013-014" (@($changed | Where-Object { $_ -match 'tasks/(013|014)-|reports/(013|014)-' }).Count -eq 0) "future goals untouched"

$frozenPaths = @(
	($ModuleName + "/dist/game/config"),
	($ModuleName + "/dist/game/data/phantoms"),
	($ModuleName + "/dist/game/data/stats"),
	($ModuleName + "/dist/sql"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/World.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/WorldObject.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/actor/Creature.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/actor/Player.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/ai"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/item"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/itemcontainer"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model/skill"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/player"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/knowledge")
)
foreach ($path in $frozenPaths)
{
	Test-Gate ("frozen." + ($path.Replace("/", ".").Replace("\", "."))) (Git-Succeeds @("diff", "--quiet", $Base, "--", $path)) $path
}

$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java"
$policy = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatPolicy.java"
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"
$mode = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatMode.java"
$actorLease = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java"
$capabilities = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java"
$threat = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatThreatTable.java"
$session = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSession.java"
$handlers = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatStepHandlers.java"
$metrics = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatMetrics.java"
$decision = Read-Text "java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java"
$system = Read-Text "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$shutdown = Read-Text "java/org/l2jmobius/gameserver/Shutdown.java"
$combatSources = (($backend, $mode, $actorLease, $capabilities, $threat, $session, $handlers, $metrics, $policy, $service) -join "`n")

foreach ($literal in @("64", "32", "4", "16", "2000", "300", "250", "30_000", "120_000", "5000", "15", "10"))
{
	Test-Gate ("policy.bound." + $literal.Replace("_", "")) ($policy.Contains($literal)) $literal
}
Test-Gate "ownership.materialization-action-lease" ($backend.Contains("tryAcquireAction(profileId)") -and $backend.Contains("ActionLease _materializationLease")) "exact ActionLease retained by adapter"
Test-Gate "ownership.full-session-close" ($service.Contains("cleanup.lease().close()") -and $service.Contains("_actorLeases--")) "lease released by terminal cleanup"
Test-Gate "ownership.cleanup-before-consume" ($session.Contains("_cleanupPending") -and $service.Contains("session._cleanupPending") -and $handlers.Contains("combat.await.cleanup")) "terminal consume waits for cleanup"
Test-Gate "ownership.inflight-pulse-barrier" ($session.Contains("_processing") -and $session.Contains("_deferredCleanupLease") -and $service.Contains("finishProcessing(session)") -and $service.Contains("awaitCleanup(session)")) "cancel cannot close an actor lease during a pulse"
Test-Gate "ownership.stop-start-barrier" ($service.Contains("_startOperations") -and $session.Contains("_startInProgress") -and $service.Contains("session._cleanupPending || session._startInProgress") -and $service.Contains("(_startOperations != 0)")) "cancel and stop wait for in-flight facade ownership"
Test-Gate "ownership.one-session-profile" ($service.Contains("_sessions.get(request.profileId())") -and $service.Contains("REJECTED_EXISTING")) "one retained slot per profile"

Test-Gate "worker.shared-claim" ($service.Contains("boolean _workerClaimed") -and $service.Contains("_workerClaimed ? 1 : 0")) "one shared transient worker"
Test-Gate "worker.threadpool-only" ($service.Contains("ThreadPool.schedule") -and ($combatSources -notmatch 'new\s+Thread|Executors\.|ScheduledFuture|scheduleAtFixedRate|scheduleWithFixedDelay')) "no thread/executor/per-profile Future"
Test-Gate "worker.pulse-bound" ($service.Contains("_policy.maximumSessionsPerPulse()") -and $service.Contains("due.size() <")) "64 sessions maximum per pulse"
Test-Gate "worker.dispatch-stop-gate" ($service.Contains("(_state != ServiceState.RUNNING) || _workerClaimed || _queue.isEmpty()")) "no dispatch outside RUNNING"

Test-Gate "target.normal-monster" ($backend.Contains("isNormalMonster") -and $backend.Contains("NpcKind.MONSTER")) "canonical type and knowledge kind"
Test-Gate "target.threat-forbidden-filter" ($backend.Contains("final TargetSnapshot target = targetSnapshot(monster)") -and $backend.Contains("target.validFor(actor, MAXIMUM_ACQUISITION_DISTANCE)")) "threat observations reuse the full forbidden-target predicate"
Test-Gate "target.raid-rejected" ($backend.Contains("!(monster instanceof RaidBoss)") -and $backend.Contains("!(monster instanceof GrandBoss)")) "raid and grandboss rejected"
Test-Gate "target.world-restrictions" ($backend.Contains("isOnEvent()") -and $backend.Contains("isInOlympiadMode()") -and $backend.Contains("ZoneId.PEACE") -and $backend.Contains("isInSiege()")) "event/peace/siege restrictions"
Test-Gate "target.instance-region-distance" ($backend.Contains("getInstanceId()") -and $backend.Contains("isSurroundingRegion") -and $backend.Contains("MAXIMUM_ACQUISITION_DISTANCE")) "instance/region/distance restrictions"
Test-Gate "threat.bounded" ($threat.Contains("capacity > 32") -and $service.Contains("maximumObservedAttackers()")) "32 entries and 16 observations"
Test-Gate "threat.deterministic" ($threat.Contains("saturatingAdd") -and $threat.Contains("selectionOrder") -and $threat.Contains("evictionOrder") -and $threat.Contains("DECAY_INTERVAL_NANOS")) "bounded deterministic threat"

Test-Gate "capability.game-knowledge" ($capabilities.Contains("classCapabilities") -and $capabilities.Contains("ClassCapabilityFact")) "Game Knowledge capability source"
Test-Gate "capability.generic-modes" ($combatSources.Contains("combat.melee_damage") -and $combatSources.Contains("combat.ranged_physical_damage") -and $combatSources.Contains("combat.ranged_magic_damage")) "three generic loadouts"
Test-Gate "capability.no-class-switch" ($combatSources -notmatch 'setPlayerClass|setActiveClass|addSubClass') "production combat never changes class"
Test-Gate "capability.selected-bound" ($capabilities.Contains(".limit(maximumSkills)") -and $policy.Contains("maximumSelectedSkills != 4")) "selected skills bounded to four"

Test-Gate "facade.attack" ($backend.Contains("setIntention(Intention.ATTACK")) "canonical ATTACK"
Test-Gate "facade.cast" ($backend.Contains("setIntention(Intention.CAST")) "canonical CAST"
Test-Gate "facade.pickup" ($backend.Contains("setIntention(Intention.PICK_UP")) "canonical PICK_UP"
Test-Gate "facade.shots" ($backend.Contains("rechargeShots") -and $backend.Contains("ItemHandler.getInstance().getHandler") -and $backend.Contains("handler.onItemUse")) "canonical shot handler"
Test-Gate "facade.respawn" ($backend.Contains("MapRegionData.getInstance().getTeleToLocation") -and $backend.Contains("TeleportWhereType.TOWN") -and $backend.Contains("setIsPendingRevive(true)") -and $backend.Contains("teleToLocation(location, true)")) "restricted town path"
Test-Gate "facade.no-packets" ($combatSources -notmatch 'network\.(clientpackets|serverpackets)|sendPacket\s*\(|RequestRestartPoint') "no packet route"
Test-Gate "facade.no-direct-mutation" ($combatSources -notmatch 'setCurrentHp\s*\(|setCurrentMp\s*\(|reduceCurrentHp\s*\(|addItem\s*\(|destroyItem\s*\(|removeItem\s*\(|addExp|addSp|calculateDamage') "no direct HP/MP/inventory/EXP/damage mutation"
Test-Gate "facade.no-mutable-session-target" ($session -notmatch 'gameserver\.model|\b(?:WorldObject|Monster|Player|Creature|Skill|Item)\s+_') "session stores IDs/value types only"

Test-Gate "plan.same-object-token" ($decision.Contains("(slot._plan == plan)") -and $decision.Contains("!isPlanCurrent(slot, generation, plan)")) "same plan object preserves token"
Test-Gate "plan.completion-invalidates" ($decision.Contains("slot._plan = null") -and $decision.Contains("recordDecisionPlanCompleted")) "completion clears plan"
Test-Gate "plan.replan-timeout-cancel" ($decision.Contains("timeoutPlanLocked") -and $decision.Contains("replanLocked") -and $decision.Contains("cancelPlanLocked")) "terminal plan paths clear ownership"
Test-Gate "plan.terminal-persistence" ($decision.Contains("PersistenceOperationKind.TERMINAL_COMPLETE") -and $decision.Contains("slot._plan = null")) "terminal goal clears plan before persistence"

foreach ($key in @("combat.start", "combat.await", "combat.cancel", "combat.respawn_town"))
{
	Test-Gate ("handlers." + $key) ($handlers.Contains('"' + $key + '"')) $key
}
Test-Gate "handlers.register-before-seal" (($system.IndexOf("new PhantomCombatStepHandlers") -gt 0) -and ($system.IndexOf("new PhantomCombatStepHandlers") -lt $system.IndexOf("handlerRegistry.seal()"))) "combat handlers registered before seal"
Test-Gate "candidates.none" ($system.Contains("final PhantomCandidateRegistry candidateRegistry") -and $system.Contains("candidateRegistry.seal()") -and ($system -notmatch 'candidateRegistry\.register')) "zero production combat candidates"
Test-Gate "startup.zero-combat-work" ($system.IndexOf("_gameKnowledgeService.start()") -lt $system.IndexOf("_combatService.start()") -and $service.Contains("if ((_state != ServiceState.RUNNING) || _workerClaimed || _queue.isEmpty())")) "combat starts after knowledge with empty queue"
Test-Gate "shutdown.combat-before-materialization" ($system.IndexOf("_combatService.beginStop()") -lt $system.IndexOf("_materializationService.shutdown()") -and $system.Contains("if (combatStopped && (_materializationService != null))")) "combat drain gates materialization"
foreach ($field in @("combatState", "combatActiveSessions", "combatTerminalSessions", "combatQueuedSessions", "combatWorkers", "combatActorLeases"))
{
	Test-Gate ("diagnostics." + $field) ($shutdown.Contains($field) -and $system.Contains($field)) $field
}
Test-Gate "metrics.fixed-coverage" (($metrics.Contains("targetsAccepted")) -and ($metrics.Contains("threatObservations")) -and ($metrics.Contains("skillCastsRejected")) -and ($metrics.Contains("lootSuccess")) -and ($metrics.Contains("respawnCompleted")) -and ($metrics.Contains("stopFailures"))) "fixed aggregate counters"

$core = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java"
$ownership = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java"
$integration = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java"
$performance = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java"
$skeleton = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java"
$shutdownTest = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"

Test-Gate "tests.core-cases" ((Count-Matches $core "registry\.add\(") -ge 38) ((Count-Matches $core "registry\.add\(").ToString() + " cases")
Test-Gate "tests.ownership-cases" ((Count-Matches $ownership "registry\.add\(") -ge 16) ((Count-Matches $ownership "registry\.add\(").ToString() + " cases")
Test-Gate "tests.integration-cases" ((Count-Matches $integration "registry\.add\(") -ge 10) ((Count-Matches $integration "registry\.add\(").ToString() + " cases")
Test-Gate "tests.integration-canonical" ($integration.Contains("canonical-player-ai-attack-and-death") -and $integration.Contains("canonical-selected-skill-cast") -and $integration.Contains("canonical-shot-conservation") -and $integration.Contains("canonical-ground-item-pickup") -and $integration.Contains("restricted-normal-town-respawn")) "real canonical routes"
Test-Gate "tests.performance-exact" ($performance.Contains("10_000") -and $performance.Contains("100_000") -and $performance.Contains("cancellations=10000")) "10k/100k/100k/10k"
Test-Gate "tests.inert-startup" ($skeleton.Contains("running.combat().activeSessions()") -and $skeleton.Contains("running.combat().currentWorkers()")) "zero startup sessions/workers"
Test-Gate "tests.shutdown-diagnostics" ($shutdownTest.Contains("combatActorLeases") -and $shutdownTest.Contains("combatWorkers")) "aggregate combat shutdown fields"
foreach ($mode in @("combat-core", "combat-ownership", "combat-server-integration", "combat-performance"))
{
	Test-Gate ("launcher." + $mode) ($launcher.Contains('case "' + $mode + '"')) $mode
}
foreach ($target in @("phantom-combat-core-test", "phantom-combat-ownership-test", "phantom-combat-server-integration-test", "phantom-combat-performance-smoke"))
{
	Test-Gate ("build." + $target) ($build.Contains('name="' + $target + '"')) $target
}
Test-Gate "build.phantom-static-verify-012" ($build.Contains('name="phantom-static-verify-012"') -and $build.Contains('verify-task-012.ps1')) "current static verifier is in the cumulative route"

$contract = Read-Text "docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md"
$report011a = Read-Text "docs/phantoms/reports/011a-knowledge-parity-query-truth.md"
$report012 = Read-Text "docs/phantoms/reports/012-capability-driven-combat-kernel.md"
$review011a = Read-Text "docs/phantoms/reviews/011a-knowledge-parity-query-truth-review.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
Test-Gate "docs.contract" ($contract.Contains("ActionLease") -and $contract.Contains("Plan cancellation token") -and $contract.Contains("Goal 013")) "combat authority and exclusions"
Test-Gate "docs.goal-011a-report" ($report011a.Contains("Independent review: ACCEPT") -and $report011a.Contains("Stage II: COMPLETE")) "accepted immutable handoff"
Test-Gate "docs.goal-011a-review" ($review011a.Contains("Goal 011A: ACCEPT") -and $review011a.Contains("Goal 012: ALLOWED")) "review closure"
Test-Gate "docs.goal-012-report" ($report012.Contains("Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $report012.Contains("Manual gate: PENDING_INDEPENDENT_REVIEW") -and $report012.Contains("Goal 013: NOT_STARTED") -and $report012.Contains("Goal 014: NOT_STARTED")) "implementation complete; independent gate remains open"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 012: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 013: NOT_STARTED") -and $roadmap.Contains("Goal 014: NOT_STARTED")) "Stage III progress only"

$diffText = Git-Text @("diff", "--unified=0", $Base, "--", ($ModuleName + "/build.xml"), ($ModuleName + "/java"), ($ModuleName + "/test"), ($ModuleName + "/tools"), ($ModuleName + "/docs"))
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

$verifierText = Read-Text "tools/phantoms/verify-task-012.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "deterministic read-only verifier"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarCombat = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarCombat = ($entries -contains "org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.class")
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-combat" $jarCombat "GameServer.jar contains combat service and adapter"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
