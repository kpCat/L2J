param()

$ErrorActionPreference = "Stop"
$SemanticBase = "8143cb7f89d348854fc469a0955b22405f23e9b6"
$ReviewedBaselineExtension = "74dd973c167adf0a74e7af78ed7944e2518c16cb"
$Branch = "feature/phantom-world"
$ExpectedSubject = "fix(phantoms): harden combat action ownership"
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
$commitCount = [int](Git-Text @("rev-list", "--count", ($ReviewedBaselineExtension + "..HEAD")))
$phaseValid = ($head -eq $ReviewedBaselineExtension) -or ($commitCount -eq 1)
$parentValid = ($head -eq $ReviewedBaselineExtension) -or ((Git-Text @("rev-parse", "HEAD^")) -eq $ReviewedBaselineExtension)
$subjectValid = ($head -eq $ReviewedBaselineExtension) -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $ExpectedSubject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))
$extensionPaths = @((Git-Text @("diff", "--name-only", ($SemanticBase + ".." + $ReviewedBaselineExtension))) -split "`r?`n" | Where-Object { $_ })

Test-Gate "repository.module-root" ((Split-Path $ModuleRoot -Leaf) -eq "L2J_Mobius_CT_2.6_HighFive") "High Five module"
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.semantic-base" ((Git-Text @("cat-file", "-t", $SemanticBase)) -eq "commit") "Goal 012 commit exists"
Test-Gate "repository.reviewed-extension-parent" ((Git-Text @("rev-parse", ($ReviewedBaselineExtension + "^"))) -eq $SemanticBase) "reviewed baseline extension is a direct Goal 012 child"
Test-Gate "repository.reviewed-extension-scope" (($extensionPaths.Count -eq 1) -and ($extensionPaths[0] -eq ".gitignore")) "reviewed extension changes only root .gitignore"
Test-Gate "repository.one-task-child" $phaseValid "worktree phase or one ordinary Goal 012A child"
Test-Gate "repository.task-parent" $parentValid "exact working parent"
Test-Gate "repository.subject" $subjectValid "exact subject after commit"
Test-Gate "repository.remote-phase" (($remote -eq $ReviewedBaselineExtension) -or ($remote -eq $head)) "parent before push or exact pushed head"

$changed = New-Object System.Collections.Generic.SortedSet[string]
foreach ($arguments in @(
	@("diff", "--name-only", ($ReviewedBaselineExtension + "...HEAD")),
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
	"docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md",
	"docs/phantoms/reports/012-capability-driven-combat-kernel.md",
	"docs/phantoms/reports/012a-combat-action-ownership-truth.md",
	"docs/phantoms/reviews/012-capability-driven-combat-kernel-review.md",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatActionOwnershipSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"tools/phantoms/verify-task-012a.ps1"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/combat/*") -and
	($_ -notlike "docs/phantoms/tasks/012a-combat-action-ownership-truth/*")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "only Goal 012A allowlist" } else { $outside -join "," })
Test-Gate "scope.no-binaries" (@($changed | Where-Object { $_ -match '\.(jar|class|exe|dll|zip|7z|png|jpg|jpeg)$' }).Count -eq 0) "no binary artifacts"
Test-Gate "scope.no-goal-013-014" (@($changed | Where-Object { $_ -match 'tasks/(013|014)-|reports/(013|014)-' }).Count -eq 0) "future goals untouched"
Test-Gate "scope.no-config-schema-datapack" (@($changed | Where-Object { $_ -match '^(dist/game/config|dist/game/data/(?!phantoms)|dist/sql|sql/)' }).Count -eq 0) "config, schema and datapack untouched"
$geodataCount = @(Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "dist/game/data/geodata") -Filter "*.l2j" -File -ErrorAction SilentlyContinue).Count
Test-Gate "scope.geodata-preserved" ($geodataCount -eq 203) ($geodataCount.ToString() + " user-owned files")

$frozenPaths = @(
	($ModuleName + "/java/org/l2jmobius/commons/threads/ThreadPool.java"),
	($ModuleName + "/java/org/l2jmobius/gameserver/model"),
	($ModuleName + "/java/org/l2jmobius/gameserver/ai"),
	($ModuleName + "/java/org/l2jmobius/gameserver/data"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/decision"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/knowledge"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/player"),
	($ModuleName + "/java/org/l2jmobius/gameserver/phantoms/materialization"),
	($ModuleName + "/dist/game/config"),
	($ModuleName + "/dist/game/data/phantoms"),
	($ModuleName + "/dist/game/data/stats"),
	($ModuleName + "/dist/sql")
)
foreach ($path in $frozenPaths)
{
	Test-Gate ("frozen." + ($path.Replace("/", ".").Replace("\", "."))) (Git-Succeeds @("diff", "--quiet", $ReviewedBaselineExtension, "--", $path)) $path
}

$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java"
$session = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSession.java"
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"
$backendContract = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java"
$actorLease = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java"
$ownedAction = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomOwnedAction.java"
$skillSafety = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSkillSafety.java"
$respawnRequest = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomRespawnRequest.java"
$handlers = Read-Text "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatStepHandlers.java"
$system = Read-Text "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$combatSources = (($service, $session, $backend, $backendContract, $actorLease, $ownedAction, $skillSafety, $respawnRequest, $handlers) -join "`n")

Test-Gate "dispatch.explicit-result" ($service.Contains("record DispatchResult(boolean accepted, DispatchHandle handle)") -and $service.Contains("DispatchResult accepted") -and $service.Contains("DispatchResult rejected")) "accepted result or rejection"
Test-Gate "dispatch.explicit-handle" ($service.Contains("interface DispatchHandle") -and $service.Contains("cancelIfNotStarted()") -and $service.Contains("DispatchState state()")) "bounded shared handle"
Test-Gate "dispatch.null-rejected" ($service.Contains("if (future == null)") -and $service.Contains("return DispatchResult.rejected()")) "null scheduled future is rejection"
Test-Gate "dispatch.throw-rejected" ($service.Contains("catch (Throwable throwable)") -and $service.Contains("_metrics.dispatchFailed()")) "Throwable cannot retain a worker claim"
Test-Gate "dispatch.shared-gate" ((Count-Matches $service 'synchronized \(_dispatchGate\)') -ge 3) "dispatch, worker start and STOPPING share one gate"
Test-Gate "dispatch.exact-claim" ($service.Contains("new WorkerClaim(++_nextWorkerGeneration)") -and $service.Contains("releaseWorkerClaimLocked(claim)")) "generation-owned shared claim"
Test-Gate "dispatch.cancel-scheduled" ($service.Contains("!claim._running") -and $service.Contains("claim._handle.cancelIfNotStarted()")) "scheduled-not-started cancellation"
Test-Gate "dispatch.top-level-finally" ($service.Contains("private void pulse(WorkerClaim claim)") -and $service.Contains("boolean ownsClaim = false") -and $service.Contains("finally") -and $service.Contains("releaseWorkerClaimLocked(claim)")) "top-level claim release including STOPPING callback"
Test-Gate "dispatch.throwable-isolation" ($service.Contains("handleProcessThrowable(session)") -and ((Count-Matches $service 'catch \(Throwable throwable\)') -ge 5)) "per-session and boundary Throwable isolation"
Test-Gate "dispatch.no-per-profile-future" ($combatSources -notmatch 'Map<[^>]*ScheduledFuture|Map<[^>]*Future|ScheduledFuture<[^>]*>\s+_.*profile|new\s+Thread|Executors\.') "one shared Future only; no per-profile task"

Test-Gate "cleanup.states" ($service.Contains("FAILED_RETRYABLE") -and $service.Contains("IN_PROGRESS") -and $service.Contains("COMPLETE")) "explicit cleanup ownership states"
Test-Gate "cleanup.attempt-bound" ($service.Contains("MAXIMUM_AUTOMATIC_CLEANUP_ATTEMPTS = 3") -and $session.Contains("_cleanupAttempts")) "three automatic attempts"
Test-Gate "cleanup.failure-retains-lease" ($service.Contains("session._cleanupState = CleanupState.FAILED_RETRYABLE") -and $service.Contains("if (failure == null)") -and $service.Contains("lease.close()")) "close occurs only on confirmed cleanup"
Test-Gate "cleanup.retry-route" ($service.Contains("retryFailedCleanup()") -and $service.Contains("cleanupRetryDue(session)")) "bounded automatic plus explicit reconciliation"
Test-Gate "cleanup.consume-blocked" ($service.Contains("session._cleanupState != CleanupState.COMPLETE") -and $service.Contains("consumeTerminal")) "terminal consume requires complete cleanup"
Test-Gate "cleanup.stop-blocked" ($service.Contains("(_actorLeases != 0)") -and $service.Contains("!_sessions.isEmpty()") -and $service.Contains("finishStop()")) "stop cannot hide retained ownership"
Test-Gate "cleanup.descriptor" ($ownedAction.Contains("combatTargetObjectId") -and $ownedAction.Contains("selectedSkill") -and $ownedAction.Contains("pickupObjectId")) "exact ATTACK/CAST/PICK_UP facts"
Test-Gate "cleanup.pickup-object" ($session.Contains("new PhantomOwnedAction") -and $service.Contains("withPickupObjectId(candidate.worldObjectId())")) "exact pickup world object retained"
Test-Gate "cleanup.canonical-adapter" ($actorLease.Contains("cancelOwnedAction(PhantomOwnedAction action)") -and $backend.Contains("public void cancelOwnedAction(PhantomOwnedAction action)")) "one exact cleanup facade"
Test-Gate "cleanup.foreign-preserved" ($backend.Contains("getAttackTarget().getObjectId() == action.combatTargetObjectId()") -and $backend.Contains("getCastTarget().getObjectId() == action.combatTargetObjectId()") -and $backend.Contains("selectedTargetObjectId == action.pickupObjectId()") -and $backend.Contains("cancelledOwnedAction")) "only matching canonical action stopped"
Test-Gate "cleanup.explicit-cancel-bound" ($service.Contains("CLEANUP_WAIT_MILLIS = 5000") -and $service.Contains("deadline - System.nanoTime()")) "cancel wait at most five seconds"

Test-Gate "loot.observation-model" ($backendContract.Contains("ACQUIRED_BY_ACTOR") -and $backendContract.Contains("LOST_WITHOUT_ACQUISITION") -and $backendContract.Contains("INELIGIBLE")) "causal result states"
Test-Gate "loot.pre-evidence" ($backendContract.Contains("actorInventoryCountBefore") -and $backendContract.Contains("groundCount")) "candidate captures ownership baseline"
Test-Gate "loot.inventory-object-evidence" ($backend.Contains("getItemByObjectId(candidate.worldObjectId())") -and $backend.Contains("exactInventoryItem.getOwnerId() == _player.getObjectId()") -and $backend.Contains("LootObservation.ACQUIRED_BY_ACTOR")) "same object in actor inventory"
Test-Gate "loot.inventory-delta-evidence" ($backend.Contains("inventoryCount - candidate.actorInventoryCountBefore()") -and $backend.Contains("candidate.groundCount()")) "positive exact item delta"
Test-Gate "loot.disappearance-not-success" ($backend.Contains("return LootObservation.LOST_WITHOUT_ACQUISITION") -and $service.Contains("observation == LootObservation.ACQUIRED_BY_ACTOR")) "success increment is evidence-gated"
Test-Gate "loot.partial-positive-only" ($session.Contains("_lootAcquiredByActor") -and $session.Contains("_lootLostWithoutAcquisition") -and $service.Contains("session._lootAcquiredByActor > 0")) "partial needs actor acquisition"

Test-Gate "skill.active-negative-one" ($skillSafety.Contains("facts.active()") -and $skillSafety.Contains("facts.negative()") -and $skillSafety.Contains("facts.oneTarget()")) "hostile one-target predicate"
Test-Gate "skill.no-pvp-suicide-special" ($skillSafety.Contains("facts.pvpOnly()") -and $skillSafety.Contains("facts.suicide()") -and $skillSafety.Contains("facts.hero()") -and $skillSafety.Contains("facts.gameMaster()") -and $skillSafety.Contains("facts.sevenSigns()") -and $skillSafety.Contains("facts.transformationSkill()")) "unsafe categories rejected"
Test-Gate "skill.real-target-type" ($skillSafety.Contains("skill.getTargetType() == TargetType.ONE") -and $skillSafety.Contains("skill.hasNegativeEffect()")) "exact Skill facts"
Test-Gate "skill.exact-mode-cast" ($actorLease.Contains("cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)") -and $service.Contains("session._request.mode()") -and $backend.Contains("supportsSkill(selected, mode)")) "session mode revalidated by backend"

Test-Gate "respawn.exact-token" ($respawnRequest.Contains("PhantomCancellationToken planOwnershipToken") -and $handlers.Contains("new PhantomRespawnRequest")) "plan-owned request"
Test-Gate "respawn.cancel-before-acquire" ($service.Contains("request.planOwnershipToken().isCancelled()") -and $service.Contains("RespawnOutcome.CANCELLED")) "cancelled request has no actor ownership"
Test-Gate "respawn.session-gate" ($service.Contains("session._cleanupState != CleanupState.COMPLETE") -and $service.Contains("RespawnOutcome.RETRY")) "active and cleanup session reject respawn"
Test-Gate "respawn.operation-identity" ($service.Contains("_respawnOperations.get(request.profileId()) != operation") -and $service.Contains("_respawnOperations.remove(request.profileId(), operation)")) "exact in-flight operation"
Test-Gate "respawn.post-acquire-reconcile" ($service.Contains("operation._actorAcquired = true") -and $service.Contains("operation._sideEffectStarted = true")) "recheck occurs after actor acquisition"
Test-Gate "respawn.stop-barrier" ($service.Contains("!_respawnOperations.isEmpty()") -and $service.Contains("ServiceState.STOPPING")) "in-flight operation gates stop"
Test-Gate "shutdown.explicit-cleanup-retry" ($system.Contains("_combatService.retryFailedCleanup()") -and $system.Contains("_combatService.finishStop()")) "failed cleanup remains visible at shutdown"

Test-Gate "facade.no-packets" ($combatSources -notmatch 'network\.(clientpackets|serverpackets)|sendPacket\s*\(|RequestRestartPoint') "no packet route"
Test-Gate "facade.no-direct-mutation" ($combatSources -notmatch 'setCurrentHp\s*\(|setCurrentMp\s*\(|reduceCurrentHp\s*\(|addItem\s*\(|destroyItem\s*\(|removeItem\s*\(|addExp|addSp|calculateDamage') "no direct HP/MP/inventory/EXP/damage mutation"
Test-Gate "facade.canonical-actions" ($backend.Contains("setIntention(Intention.ATTACK") -and $backend.Contains("setIntention(Intention.CAST") -and $backend.Contains("setIntention(Intention.PICK_UP")) "canonical PlayerAI route"

$actionTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatActionOwnershipSuite.java"
$coreTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatCoreSuite.java"
$ownershipTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatOwnershipSuite.java"
$integrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java"
$performanceTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomCombatPerformanceSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"

Test-Gate "tests.action-count" ((Count-Matches $actionTests "registry\.add\(") -ge 33) ((Count-Matches $actionTests "registry\.add\(").ToString() + " cases")
foreach ($case in @("null-production-handle-rejection", "scheduled-worker-cancelled-before-start", "cleanup-throw-retains-lease", "stale-cleanup-preserves-foreign-actions", "other-player-pickup-not-acquired", "exact-session-mode-revalidated", "in-flight-respawn-is-stop-barrier"))
{
	Test-Gate ("tests.action." + $case) ($actionTests.Contains($case)) $case
}
Test-Gate "tests.action.running-before-pulse-stop" ($actionTests.Contains("RunningBeforePulseDispatcher") -and $actionTests.Contains("STOPPING callback retained its exact worker claim")) "RUNNING handle reconciles exact claim after STOPPING"
Test-Gate "tests.core-count" ((Count-Matches $coreTests "registry\.add\(") -ge 47) ((Count-Matches $coreTests "registry\.add\(").ToString() + " cases")
Test-Gate "tests.ownership-count" ((Count-Matches $ownershipTests "registry\.add\(") -ge 17) ((Count-Matches $ownershipTests "registry\.add\(").ToString() + " cases")
Test-Gate "tests.integration-count" ((Count-Matches $integrationTests "registry\.add\(") -ge 19) ((Count-Matches $integrationTests "registry\.add\(").ToString() + " cases")
foreach ($case in @("other-player-pickup-is-not-acquisition", "despawn-is-not-acquisition", "range-loss-is-not-acquisition", "cancel-during-exact-pickup", "stop-during-exact-pickup", "foreign-cast-and-pickup-survive", "positive-one-target-skill-rejected"))
{
	Test-Gate ("tests.integration." + $case) ($integrationTests.Contains($case)) $case
}
Test-Gate "tests.performance-bounds" ($performanceTests.Contains("10_000") -and $performanceTests.Contains("100_000") -and $performanceTests.Contains("dispatchFailures=0") -and $performanceTests.Contains("cleanupFailures=0")) "10k/100k with zero ownership failures"
Test-Gate "launcher.action-ownership" ($launcher.Contains('case "combat-action-ownership"')) "focused mode registered"
Test-Gate "build.action-ownership" ($build.Contains('name="phantom-combat-action-ownership-test"') -and $build.Contains('<arg value="combat-action-ownership"')) "focused target registered"
Test-Gate "build.verify-route" ($build.Contains('name="phantom-static-verify-012a"') -and $build.Contains("verify-task-012a.ps1")) "cumulative route uses current verifier"

$contract = Read-Text "docs/phantoms/architecture/COMBAT_KERNEL_CONTRACT.md"
$report012 = Read-Text "docs/phantoms/reports/012-capability-driven-combat-kernel.md"
$report012a = Read-Text "docs/phantoms/reports/012a-combat-action-ownership-truth.md"
$review012 = Read-Text "docs/phantoms/reviews/012-capability-driven-combat-kernel-review.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
Test-Gate "docs.contract" ($contract.Contains("Goal 012A") -and $contract.Contains("Action lease") -and $contract.Contains('PICK_UP')) "ownership truth documented"
Test-Gate "docs.goal-012-immutable" ($report012.Contains("Commit: 8143cb7f89d348854fc469a0955b22405f23e9b6") -and $report012.Contains("Final verifier: 112/112") -and $report012.Contains("byte-identical") -and $report012.Contains("action ownership/causal truth FIX_REQUIRED")) "accepted evidence preserved"
Test-Gate "docs.review-verdict" ($review012.Contains("Goal 012 architecture direction: ACCEPT") -and $review012.Contains("Goal 012 commit: FIX_REQUIRED") -and $review012.Contains("Goal 012A: REQUIRED") -and $review012.Contains("Goal 013: BLOCKED")) "exact independent verdict"
Test-Gate "docs.goal-012a-report" ($report012a.Contains("Status: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $report012a.Contains("COMBAT_ACTION_OWNERSHIP_TRUTH_HARDENED_PENDING_INDEPENDENT_REVIEW")) "manual gate remains independent"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 012: FIX_REQUIRED") -and $roadmap.Contains("Goal 012A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 013: NOT_STARTED / BLOCKED") -and $roadmap.Contains("Goal 014: NOT_STARTED")) "progress only"

$diffText = Git-Text @("diff", "--unified=0", $ReviewedBaselineExtension, "--", ($ModuleName + "/build.xml"), ($ModuleName + "/java"), ($ModuleName + "/test"), ($ModuleName + "/tools"), ($ModuleName + "/docs"))
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
	if ((Git-Text @("ls-files", "--others", "--exclude-standard", "--", ($ModuleName + "/" + $path))) -and (Test-Path -LiteralPath (Join-Path $ModuleRoot $path) -PathType Leaf))
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
Test-Gate "encoding.utf8-strict" $true "all verifier-read text decoded as strict UTF-8"
Test-Gate "encoding.mojibake-added-lines" ($mojibakeFound.Count -eq 0) $(if ($mojibakeFound.Count -eq 0) { "no new mojibake markers" } else { $mojibakeFound -join "," })
Test-Gate "encoding.escaped-cyrillic-added-lines" ($addedText -notmatch $escapedPattern) "no new escaped Cyrillic"
$credentialPattern = ("(?i)(pass" + "word|pass" + "wd|sec" + "ret)\s*[:=]\s*[^\s$<{]+|ro" + "ot/ro" + "ot")
Test-Gate "security.no-credentials" ($addedText -notmatch $credentialPattern) "no embedded credentials"

$verifierText = Read-Text "tools/phantoms/verify-task-012a.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "deterministic read-only verifier"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarRequired = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarRequired = @(
			"org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.class",
			"org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService`$DispatchHandle.class",
			"org/l2jmobius/gameserver/phantoms/combat/PhantomOwnedAction.class",
			"org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSkillSafety.class",
			"org/l2jmobius/gameserver/phantoms/combat/PhantomRespawnRequest.class"
		) | ForEach-Object { $entries -contains $_ } | Where-Object { !$_ } | Measure-Object | Select-Object -ExpandProperty Count
		$jarRequired = $jarRequired -eq 0
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production-ownership" $jarRequired "GameServer.jar contains Goal 012A production classes"
Test-Gate "jar.tests-absent" $jarTestsAbsent "GameServer.jar contains no test classes"

$total = $PassCount + $FailCount
Write-Output ("SUMMARY PASS=" + $PassCount + " FAIL=" + $FailCount + " TOTAL=" + $total)
if ($FailCount -ne 0)
{
	exit 1
}
