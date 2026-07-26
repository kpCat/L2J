[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "ff0b33abad0affc4fe64b4324aee67f256dc96fa"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0
$script:Results = New-Object System.Collections.Generic.List[object]

function Add-Result
{
    param([string]$Name, [bool]$Passed, [string]$Detail)
    $script:Results.Add([PSCustomObject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
}

function Invoke-Git
{
    param([string]$Root, [string[]]$Arguments, [switch]$AllowFailure)
    $output = @(& git -c core.safecrlf=false -C $Root @Arguments 2>&1)
    if (($LASTEXITCODE -ne 0) -and -not $AllowFailure)
    {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return [PSCustomObject]@{ ExitCode = $LASTEXITCODE; Output = [string[]]$output }
}

function Get-OrdinalSortedUnique
{
    param([string[]]$Values)
    $set = New-Object "System.Collections.Generic.HashSet[string]" ([System.StringComparer]::Ordinal)
    foreach ($value in $Values)
    {
        if (-not [string]::IsNullOrWhiteSpace($value))
        {
            [void]$set.Add($value.Trim().Replace("\", "/"))
        }
    }
    $array = [string[]]$set
    [Array]::Sort($array, [System.StringComparer]::Ordinal)
    return $array
}

function Test-ContainsAll
{
    param([string]$Path, [string[]]$Tokens)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        return $false
    }
    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    foreach ($token in $Tokens)
    {
        if ($content.IndexOf($token, [System.StringComparison]::Ordinal) -lt 0)
        {
            return $false
        }
    }
    return $true
}

function Test-TaskScopePath
{
    param([string]$RepositoryPath, [string]$ModulePrefix)
    if (-not $RepositoryPath.StartsWith($ModulePrefix, [System.StringComparison]::Ordinal))
    {
        return $false
    }
    $relative = $RepositoryPath.Substring($ModulePrefix.Length)
    return ($relative -ceq "build.xml") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "tools/phantoms/verify-task-006a.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md") -or
        $relative.StartsWith("docs/phantoms/tasks/006a-materialization-boundary-hardening/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/005-core-profile-persistence-envelope.md") -or
        ($relative -ceq "docs/phantoms/reports/006-production-materialization-lifecycle.md") -or
        ($relative -ceq "docs/phantoms/reports/006a-materialization-boundary-hardening.md") -or
        ($relative -ceq "docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md") -or
        ($relative -ceq "docs/phantoms/reviews/006-production-materialization-lifecycle-review.md")
}

function Test-ValidUtf8
{
    param([string]$Path)
    try
    {
        $strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
        [void]$strictUtf8.GetString([System.IO.File]::ReadAllBytes($Path))
        return $true
    }
    catch
    {
        return $false
    }
}

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRoot = (Resolve-Path (Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel")).Output[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $modulePrefix = "$relativeModule/"
    Add-Result "repository.module-root" ($moduleRoot -ceq (Join-Path $gitRoot $relativeModule)) $moduleRoot
    $currentBranch = (Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")).Output[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $currentBranch
    $baseExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.goal006-base" ($baseExists.ExitCode -eq 0) $BaseCommit

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $shapeMode = "invalid"
    $ordinaryShape = $false
    if ($head -ceq $BaseCommit)
    {
        $shapeMode = "pre-commit"
        $ordinaryShape = $true
    }
    elseif ($baseExists.ExitCode -eq 0)
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $distance = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $parentLine = (Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--parents", "-n", "1", "HEAD")).Output[0]
        if (($parent -ceq $BaseCommit) -and ($distance -eq 1) -and (($parentLine -split " ").Count -eq 2))
        {
            $shapeMode = "post-commit"
            $ordinaryShape = $true
        }
    }
    Add-Result "repository.one-ordinary-goal006a-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "fix(phantoms): harden materialization boundaries") $subject
    }
    else
    {
        Add-Result "repository.commit-subject" $true "checked after commit"
    }

    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit, "--", $relativeModule)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard", "--", $relativeModule)).Output
    $changed = @(Get-OrdinalSortedUnique ([string[]]($tracked + $untracked)))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    $scopeViolations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) $(if ($scopeViolations.Count -eq 0) { "no violations" } else { $scopeViolations -join "," })
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no task binaries"
    Add-Result "scope.no-goal007-artifacts" (@($changed | Where-Object { $_ -match "(?i)(tasks|reports|reviews)/007" }).Count -eq 0) "Goal 007 not started"

    foreach ($frozen in @(
        "dist/game/config/Custom/PhantomPlayers.ini",
        "java/org/l2jmobius/gameserver/GameServer.java",
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/model/World.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/network/GameClient.java",
        "java/org/l2jmobius/gameserver/network/Disconnection.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomRetainedIdentityRecovery.java",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfile.java",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java",
        "tools/phantoms/verify-task-006.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }
    $schemaChanges = @($changed | Where-Object { $_ -match "(?i)dist/db_installer/sql|phantom_profiles\.sql|migration" })
    Add-Result "scope.no-schema-change" ($schemaChanges.Count -eq 0) $(if ($schemaChanges.Count -eq 0) { "none" } else { $schemaChanges -join "," })

    $required = @(
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java",
        "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java",
        "tools/phantoms/verify-task-006a.ps1",
        "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md",
        "docs/phantoms/reports/006a-materialization-boundary-hardening.md",
        "docs/phantoms/reviews/006-production-materialization-lifecycle-review.md")
    foreach ($relative in $required)
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }
    foreach ($name in @("ACCEPTANCE.md", "ARCHITECTURE.md", "CODEX_LAUNCHER.txt", "CONTEXT.md", "PACKAGE_MANIFEST.json", "REVIEW_FINDINGS.md", "TASK.md", "TEST_CASES.md"))
    {
        Add-Result "artifact.task-package.$name" (Test-Path -LiteralPath (Join-Path $moduleRoot "docs/phantoms/tasks/006a-materialization-boundary-hardening/$name") -PathType Leaf) $name
    }

    $corePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java"
    $servicePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java"
    $autosavePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java"
    $core = Get-Content -LiteralPath $corePath -Raw -Encoding UTF8
    $service = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
    $autosave = Get-Content -LiteralPath $autosavePath -Raw -Encoding UTF8

    Add-Result "identity.distinct-failures" (Test-ContainsAll -Path $corePath -Tokens @(
        "WORLD_PLAYER_IDENTITY_BUSY", "WORLD_OBJECT_IDENTITY_BUSY",
        "AUTOSAVE_IDENTITY_BUSY", "WORLD_REGISTRATION_MISMATCH")) "four explicit failures"
    Add-Result "identity.preflight-before-and-after-claim" (([regex]::Matches($core, 'requireIdentityRegistriesFree\("').Count -ge 2) -and
        (Test-ContainsAll -Path $corePath -Tokens @('"before identity claim"', '"after identity claim"', "World.getInstance()", "world.getPlayer(_objectId)", "world.findObject(_objectId)", "containsObjectId(_objectId)"))) "both World maps and autosave"
    Add-Result "identity.post-load-exact-autosave" (Test-ContainsAll -Path $corePath -Tokens @(
        "_player.getObjectId() != _objectId", 'requireWorldIdentityFree("during Player load")',
        "!autoSaveManager.contains(_player)", "autoSaveManager.containsOtherObjectId(_objectId, _player)")) "exact loaded Player and sole autosave owner"
    Add-Result "identity.pre-spawn-recheck" (Test-ContainsAll -Path $corePath -Tokens @(
        'requireWorldIdentityFree("immediately before World spawn")', "_player.spawnMe()")) "World rechecked immediately before spawn"
    Add-Result "identity.post-spawn-both-exact" (Test-ContainsAll -Path $corePath -Tokens @(
        "world.getPlayer(_objectId) != _player", "world.findObject(_objectId) != _player",
        "MaterializationFailure.WORLD_REGISTRATION_MISMATCH")) "both World maps equal exact Player"
    Add-Result "identity.autosave-read-only-query" ((Test-ContainsAll -Path $autosavePath -Tokens @(
        "public boolean containsOtherObjectId(int objectId, Player expectedPlayer)",
        "PLAYER_TIMES.keySet()", "player != expectedPlayer", "player.getObjectId() == objectId")) -and
        -not [regex]::IsMatch($autosave, "containsOtherObjectId[\s\S]{0,500}(put|remove|clear)\(")) "query only"
    Add-Result "identity.foreign-world-cleanup-guard" (Test-ContainsAll -Path $corePath -Tokens @(
        "requireNoForeignWorldIdentity(cleanupPlayer)", "worldPlayer != cleanupPlayer", "worldObject != cleanupPlayer")) "foreign collision is retained"
    Add-Result "service.distinct-results" (Test-ContainsAll -Path $servicePath -Tokens @(
        "ResultStatus.WORLD_PLAYER_IDENTITY_BUSY", "ResultStatus.WORLD_OBJECT_IDENTITY_BUSY",
        "ResultStatus.AUTOSAVE_IDENTITY_BUSY", "ResultStatus.WORLD_REGISTRATION_MISMATCH")) "core failures preserved"
    Add-Result "service.release-only-terminal-stored" (Test-ContainsAll -Path $servicePath -Tokens @(
        "entry._materializedPlayer.snapshot().state() != State.STORED",
        "_activeByProfile.remove(entry._profileId, entry)",
        "_activeByCharacter.remove(entry._characterObjectId, entry)",
        "_permits.release()")) "map and permit release after terminal STORED"

    $actionStart = $service.IndexOf("public Optional<ActionLease> tryAcquireAction(long profileId)", [System.StringComparison]::Ordinal)
    $actionEnd = $service.IndexOf("public Optional<MaterializationSnapshot> find(long profileId)", [System.StringComparison]::Ordinal)
    $actionBody = if (($actionStart -ge 0) -and ($actionEnd -gt $actionStart)) { $service.Substring($actionStart, $actionEnd - $actionStart) } else { "" }
    Add-Result "action.admission-under-state-monitor" (($actionBody.Contains("synchronized (_stateMonitor)")) -and
        ($actionBody.Contains("_state != ServiceState.RUNNING")) -and
        ($actionBody.Contains("_activeByProfile.get(profileId)")) -and
        ($actionBody.Contains("entry._materializedPlayer.tryAcquireAction()"))) "state, lookup and actor admission are atomic with STOPPING"
    Add-Result "action.monitor-has-no-world-db-work" (-not [regex]::IsMatch($actionBody, "Player\.load|World\.|storeMe|deleteMe|spawnMe|Repository|Connection|PreparedStatement")) "bounded admission only"

    Add-Result "shutdown.one-service-drain-attempt" (([regex]::Matches($service, "private DrainAttempt _drainAttempt;").Count -eq 1) -and
        ([regex]::Matches($service, "new DrainAttempt\(").Count -eq 1) -and
        ([regex]::Matches($service, "ThreadPool\.schedule\(").Count -eq 1)) "one tracked attempt and one shared-pool command"
    Add-Result "shutdown.bounded-caller-latch" (Test-ContainsAll -Path $servicePath -Tokens @(
        "callerDeadlineNanos", "attempt._completion.await(remainingNanos, TimeUnit.NANOSECONDS)",
        "return new ShutdownResult(ServiceState.FAILED, failedProfileIds())")) "caller wait uses remaining wall-clock budget"
    Add-Result "shutdown.reuses-in-flight" (Test-ContainsAll -Path $servicePath -Tokens @(
        "(_drainAttempt != null) && !_drainAttempt.isCompleted()", "attempt = _drainAttempt")) "second caller reuses tracked command"
    Add-Result "shutdown.submission-failure-retained" (Test-ContainsAll -Path $servicePath -Tokens @(
        "if (attempt._future == null)", "completeDrainAttemptLocked(attempt, ServiceState.FAILED, failedProfileIds())")) "submission failure is terminal for attempt, not ownership"
    Add-Result "shutdown.late-completion" (Test-ContainsAll -Path $servicePath -Tokens @(
        "retainedProfileIds.isEmpty() ? ServiceState.STOPPED : ServiceState.FAILED",
        "attempt._completion.countDown()")) "late completion can reach STOPPED"
    Add-Result "shutdown.later-explicit-retry" (Test-ContainsAll -Path $servicePath -Tokens @(
        "(_state == ServiceState.RUNNING) || (_state == ServiceState.FAILED)",
        "_drainAttempt = null")) "completed failed attempt permits later retry"
    Add-Result "shutdown.stable-two-pass" (([regex]::Matches($service, "shutdownPass\(").Count -eq 3) -and
        (Test-ContainsAll -Path $servicePath -Tokens @("sortedEntries()", "retryEntries", "System.nanoTime() < attempt._deadlineNanos"))) "at most two ordered passes"
    Add-Result "shutdown.one-transient-future" (([regex]::Matches($service, "ScheduledFuture<\?>").Count -eq 1) -and
        (Test-ContainsAll -Path $servicePath -Tokens @("private static final class DrainAttempt", "private final CountDownLatch _completion"))) "future/latch belongs to service attempt"
    Add-Result "shutdown.no-new-executor-or-thread" (-not [regex]::IsMatch("$core`n$service", "new\s+Thread\s*\(|Executors\.|ExecutorService|ScheduledExecutorService")) "production uses existing ThreadPool only"
    Add-Result "shutdown.no-force-cancel" (-not $service.Contains(".cancel(")) "canonical store/delete is not cancelled"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java"
    $suite = Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8
    $caseCount = [regex]::Matches($suite, 'registry\.add\(').Count
    Add-Result "tests.production-matrix-extended" (($caseCount -ge 19) -and (Test-ContainsAll -Path $suitePath -Tokens @(
        "testMaterializationIdentityBoundaries", "testActionAdmissionAtomicWithStopping",
        "testShutdownCallerWallClock"))) "$caseCount explicit cases"
    Add-Result "tests.identity-collisions" (Test-ContainsAll -Path $suitePath -Tokens @(
        "ResultStatus.WORLD_PLAYER_IDENTITY_BUSY", "ResultStatus.WORLD_OBJECT_IDENTITY_BUSY",
        "ResultStatus.AUTOSAVE_IDENTITY_BUSY", "new ObjectIdResidue(",
        "FailurePoint.AFTER_PLAYER_LOAD", "retryCleanup(profile.profileId())")) "World Player/object/autosave/post-load cases"
    Add-Result "tests.no-split-and-retained-ownership" (Test-ContainsAll -Path $suitePath -Tokens @(
        "Pre-spawn collision created a split World Player map.",
        "Pre-spawn collision released capacity before terminal STORED.",
        "Pre-spawn collision released identity before terminal STORED.")) "split identity rejected fail-closed"
    Add-Result "tests.action-stopping-concurrency" (Test-ContainsAll -Path $suitePath -Tokens @(
        "ServiceState.STOPPING", "attempt < 1000", "Action was admitted after STOPPING",
        "held.close()", "ServiceState.STOPPED")) "bounded repeated rejection after STOPPING"
    Add-Result "tests.blocked-store-wall-clock" (Test-ContainsAll -Path $suitePath -Tokens @(
        "FailurePoint.BEFORE_STORE_OPERATION", "releaseStore.await(5, TimeUnit.SECONDS)",
        "firstElapsed < TimeUnit.SECONDS.toNanos(1)",
        "secondElapsed < TimeUnit.SECONDS.toNanos(1)")) "100-250 ms service timeout remains below one second"
    Add-Result "tests.single-drain-and-timeout-retention" (Test-ContainsAll -Path $suitePath -Tokens @(
        "Second early shutdown invoked duplicate cleanup.",
        "Caller timeout released the service entry.",
        "Caller timeout released capacity.",
        "Caller timeout released identity.")) "duplicate cleanup and ownership release regressions"
    Add-Result "tests.late-completion-and-no-residue" (Test-ContainsAll -Path $suitePath -Tokens @(
        "Tracked drain did not complete after the store block was released.",
        "Late completion invoked cleanup more than once.",
        "_environment.assertClean(_environment.primary(), player)")) "late STOPPED and clean residue"

    $buildPath = Join-Path $moduleRoot "build.xml"
    Add-Result "build.cumulative-verifier-target" (Test-ContainsAll -Path $buildPath -Tokens @(
        'name="phantom-static-verify-006a"', "verify-task-006a.ps1",
        'name="phantom-static-verify-006" depends="phantom-static-verify-006a"',
        "Run Goal 006A and all prior Phantom verification gates.")) "Goal 006A is the cumulative static gate"
    Add-Result "build.production-tests-forked" (Test-ContainsAll -Path $buildPath -Tokens @(
        "phantom-production-materialization-test", "phantom-production-materialization-performance-smoke", 'fork="true"')) "production routes use isolated JVM"

    $goal005Report = Join-Path $moduleRoot "docs/phantoms/reports/005-core-profile-persistence-envelope.md"
    $goal005Review = Join-Path $moduleRoot "docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md"
    $goal006Report = Join-Path $moduleRoot "docs/phantoms/reports/006-production-materialization-lifecycle.md"
    $goal006Review = Join-Path $moduleRoot "docs/phantoms/reviews/006-production-materialization-lifecycle-review.md"
    $goal006aReport = Join-Path $moduleRoot "docs/phantoms/reports/006a-materialization-boundary-hardening.md"
    $contractPath = Join-Path $moduleRoot "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md"
    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    Add-Result "docs.goal005-provenance-corrected" ((Test-ContainsAll -Path $goal005Report -Tokens @(
        "69/69", "483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97", "Task 004B")) -and
        (Test-ContainsAll -Path $goal005Review -Tokens @(
        "69/69", "483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97", "Task 004B"))) "full local handoff evidence retained without invented hash"
    Add-Result "docs.task004b-sha-labeled-only" ((Test-ContainsAll -Path $goal006Report -Tokens @(
        "39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9", "Task 004B")) -and
        -not [regex]::IsMatch((Get-Content -LiteralPath $goal006Report -Raw -Encoding UTF8), "Goal 005[^\r\n]{0,160}39A1D87D")) "Task 004B provenance only"
    Add-Result "docs.goal006-review-verdict" (Test-ContainsAll -Path $goal006Review -Tokens @(
        "Goal 005: ACCEPT", "Goal 006 architecture direction: ACCEPT",
        "Goal 006 commit: FIX_REQUIRED", "Revert: NOT_REQUIRED",
        "Goal 006A: REQUIRED", "Goal 007: BLOCKED")) "independent findings preserved"
    Add-Result "docs.goal006-report-gate" (Test-ContainsAll -Path $goal006Report -Tokens @(
        "FIX_REQUIRED", "Goal 006A", "REQUIRED", "Goal 007", "BLOCKED")) "Goal 006 is not accepted"
    Add-Result "docs.goal006a-report" (Test-ContainsAll -Path $goal006aReport -Tokens @(
        "PRODUCTION_MATERIALIZATION_LIFECYCLE_HARDENED_PENDING_INDEPENDENT_REVIEW",
        "19/19", "Production DB", "l2jmobiush5",
        "Goal 007", "NOT_STARTED", "BLOCKED",
        "483B6CAD90CEAE55E282E492639DA6253F754424FDD7EB8DB57A41B23B966E97")) "closure report and pending manual gate"
    Add-Result "docs.lifecycle-contract-hardened" (Test-ContainsAll -Path $contractPath -Tokens @(
        "World.getPlayer", "World.findObject", "containsOtherObjectId",
        "STOPPING", "DrainAttempt", "ThreadPool", "Goal 006A")) "identity/action/drain boundaries documented"
    Add-Result "roadmap.progress-only-statuses" (Test-ContainsAll -Path $roadmapPath -Tokens @(
        "Goal 006:", "FIX_REQUIRED", "Goal 006A:",
        "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 007:",
        "NOT_STARTED / BLOCKED")) "current progress gate only"

    $changedTextPaths = @($changed | Where-Object { $_ -match "(?i)\.(java|xml|ini|md|txt|json|ps1)$" })
    $invalidUtf8 = New-Object System.Collections.Generic.List[string]
    $mojibake = New-Object System.Collections.Generic.List[string]
    $escaped = New-Object System.Collections.Generic.List[string]
    $credentials = New-Object System.Collections.Generic.List[string]
    $markers = New-Object System.Collections.Generic.List[string]
    foreach ($codePoint in @(0x045F, 0x045C, 0x045B, 0x2022, 0x040E, 0x203A, 0x00A4, 0x045A, 0x0408, 0x0459, 0x0491, 0x00B5, 0x00B0, 0x00BB, 0x0405, 0x0455))
    {
        [void]$markers.Add(([string][char]0x0420) + ([string][char]$codePoint))
    }
    foreach ($codePoint in @(0x040F, 0x20AC, 0x0402, 0x2039, 0x040A, 0x201A, 0x0453, 0x2021, 0x2026, 0x2020))
    {
        [void]$markers.Add(([string][char]0x0421) + ([string][char]$codePoint))
    }
    [void]$markers.Add([string][char]0xFFFD)
    $markerPattern = ($markers | ForEach-Object { [regex]::Escape($_) }) -join "|"
    $escapedPattern = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
    foreach ($repositoryPath in $changedTextPaths)
    {
        $path = Join-Path $gitRoot $repositoryPath
        if (-not (Test-Path -LiteralPath $path -PathType Leaf))
        {
            continue
        }
        if (-not (Test-ValidUtf8 -Path $path))
        {
            [void]$invalidUtf8.Add($repositoryPath)
            continue
        }
        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        if ($content -match $markerPattern) { [void]$mojibake.Add($repositoryPath) }
        if ($content -match $escapedPattern) { [void]$escaped.Add($repositoryPath) }
        $credentialPattern = '(?i)jdbc:mariadb://127\.0\.0\.1:3308/l2jmobiush5(?!_phantom_test)|root' + '/root|password\s*[:=]\s*root'
        if ($content -match $credentialPattern) { [void]$credentials.Add($repositoryPath) }
    }
    Add-Result "encoding.valid-utf8" ($invalidUtf8.Count -eq 0) $(if ($invalidUtf8.Count -eq 0) { "$($changedTextPaths.Count) text files" } else { $invalidUtf8 -join "," })
    Add-Result "encoding.no-mojibake-markers" ($mojibake.Count -eq 0) $(if ($mojibake.Count -eq 0) { "none" } else { $mojibake -join "," })
    Add-Result "encoding.no-escaped-cyrillic" ($escaped.Count -eq 0) $(if ($escaped.Count -eq 0) { "none" } else { $escaped -join "," })
    Add-Result "security.no-credentials" ($credentials.Count -eq 0) $(if ($credentials.Count -eq 0) { "none" } else { $credentials -join "," })

    $self = Get-Content -LiteralPath $PSCommandPath -Raw -Encoding UTF8
    Add-Result "verifier.read-only" (-not [regex]::IsMatch($self, "(?im)^\s*(Set-Content|Add-Content|Out-File|Remove-Item|Move-Item|Copy-Item|New-Item|git\s+(add|commit|push|reset|restore|checkout|clean))\b")) "no write/mutation command"
    $nondeterminismPattern = "(?i)Get-" + "Date|New-" + "Guid|Get-" + "Random|Start-" + "Sleep"
    Add-Result "verifier.deterministic" (-not [regex]::IsMatch($self, $nondeterminismPattern)) "no time/random/sleep output"
}
catch
{
    Add-Result "verifier.exception" $false $_.Exception.Message
}

$passed = @($script:Results | Where-Object { $_.Passed }).Count
$failed = @($script:Results | Where-Object { -not $_.Passed }).Count
foreach ($result in $script:Results)
{
    $state = if ($result.Passed) { "PASS" } else { "FAIL" }
    Write-Output ("{0} {1} :: {2}" -f $state, $result.Name, $result.Detail)
}
Write-Output ("SUMMARY PASS={0} FAIL={1} TOTAL={2}" -f $passed, $failed, $script:Results.Count)
if ($failed -ne 0)
{
    exit 1
}
