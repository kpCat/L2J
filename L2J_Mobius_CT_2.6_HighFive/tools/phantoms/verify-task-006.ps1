[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "9d0465eb62f9913644fab9f1d60feb2f4fd9a674"
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
        ($relative -ceq "dist/game/config/Custom/PhantomPlayers.ini") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/player/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/GameClient.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/Disconnection.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileComponent.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-006.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md") -or
        $relative.StartsWith("docs/phantoms/tasks/006-production-materialization-lifecycle/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/005-core-profile-persistence-envelope.md") -or
        ($relative -ceq "docs/phantoms/reports/006-production-materialization-lifecycle.md") -or
        ($relative -ceq "docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md")
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
    Add-Result "repository.accepted-base" ($baseExists.ExitCode -eq 0) $BaseCommit

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
    Add-Result "repository.one-ordinary-task-006-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "feat(phantoms): add production materialization lifecycle") $subject
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
    Add-Result "scope.exact-allowlist-with-bounded-legacy-fixture" ($scopeViolations.Count -eq 0) $(if ($scopeViolations.Count -eq 0) { "no violations" } else { $scopeViolations -join "," })
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no task binaries"
    $schemaChanges = @($changed | Where-Object { $_ -match "(?i)dist/db_installer/sql|phantom_profiles\.sql" })
    Add-Result "scope.no-schema-change" ($schemaChanges.Count -eq 0) $(if ($schemaChanges.Count -eq 0) { "none" } else { $schemaChanges -join "," })
    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/GameServer.java",
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java",
        "tools/phantoms/verify-task-005.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    $required = @(
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomRetainedIdentityRecovery.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java",
        "tools/phantoms/verify-task-006.ps1",
        "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md",
        "docs/phantoms/reports/006-production-materialization-lifecycle.md",
        "docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md")
    foreach ($relative in $required)
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }
    foreach ($name in @("ACCEPTANCE.md", "ARCHITECTURE.md", "CODEX_LAUNCHER.txt", "CONTEXT.md", "PACKAGE_MANIFEST.json", "RECOVERY_CONTRACT.md", "TASK.md", "TEST_CASES.md"))
    {
        Add-Result "artifact.task-package.$name" (Test-Path -LiteralPath (Join-Path $moduleRoot "docs/phantoms/tasks/006-production-materialization-lifecycle/$name") -PathType Leaf) $name
    }

    $configPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
    $iniPath = Join-Path $moduleRoot "dist/game/config/Custom/PhantomPlayers.ini"
    Add-Result "config.default-false-false-32" ((Test-ContainsAll -Path $iniPath -Tokens @("EnablePhantomSystem = False", "EnablePhantomDiagnostics = False", "MaxMaterializedPhantoms = 32")) -and (Test-ContainsAll -Path $configPath -Tokens @("DEFAULT_MAX_MATERIALIZED_PHANTOMS = 32", "Settings.disabled()", 'normalized.matches("[0-9]+")', "parsed >= 1", "parsed <= 10000"))) "disabled defaults and strict cap"
    $config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
    Add-Result "config.no-signed-cap" (-not $config.Contains('"[+-]?[0-9]+"')) "unsigned decimal only"

    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    Add-Result "system.production-start-order" (Test-ContainsAll -Path $systemPath -Tokens @("_scheduler.start()", "PhantomProfileRepository.open()", "new PhantomMaterializationService(", "_materializationService.start()", "_state = State.RUNNING")) "scheduler/repository/service/running"
    Add-Result "system.disabled-before-production-construction" (Test-ContainsAll -Path $systemPath -Tokens @("if (!PhantomPlayersConfig.isEnabled() || (_configuredInstance != null))", "new PhantomSystem(PhantomPlayersConfig.settings(), true)")) "disabled configured path returns before repository/service"
    Add-Result "system.drain-before-scheduler-stop" (Test-ContainsAll -Path $systemPath -Tokens @("_materializationService.shutdown()", "result.state() != ServiceState.STOPPED", "_scheduler.stop()", "_state = State.FAILED")) "failed drain retained"
    Add-Result "system.configured-instance-retained-on-failure" (-not (Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8).Contains("finally`r`n`t`t{`r`n`t`t`t_configuredInstance = null")) "clear only after STOPPED"

    $corePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java"
    $spikePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java"
    $servicePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java"
    $core = Get-Content -LiteralPath $corePath -Raw -Encoding UTF8
    $spike = Get-Content -LiteralPath $spikePath -Raw -Encoding UTF8
    $service = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
    Add-Result "lifecycle.single-core-states" (Test-ContainsAll -Path $corePath -Tokens @("STORED", "CLAIMED", "LOADING", "MATERIALIZING", "ACTIVE", "DEMATERIALIZING", "FAILED", "Player.load(", "attachOutboundSession", "setOnlineStatus(true, true)", "spawnMe()", "storeMe()", "deleteMe()")) "canonical actor lifecycle"
    Add-Result "lifecycle.spike-thin-wrapper" ((Test-ContainsAll -Path $spikePath -Tokens @("private final PhantomMaterializedPlayer _materializedPlayer", "_materializedPlayer.materialize()", "_materializedPlayer.cleanup()", "_materializedPlayer.tryAcquireAction()")) -and -not [regex]::IsMatch($spike, "Player\.load\(|\.setOnlineStatus\(|\.spawnMe\(|\.storeMe\(|\.deleteMe\(")) "no duplicate lifecycle"
    Add-Result "lifecycle.no-fixture-in-production-core-service" (-not [regex]::IsMatch("$core`n$service", "FIXTURE_ITEM_ID|PhantomActionFacade")) "fixture remains compatibility-only"
    Add-Result "action.tokenized-no-arbitrary-executor" ((Test-ContainsAll -Path $corePath -Tokens @("ActionLease tryAcquireAction()", "AtomicBoolean _closed", "compareAndSet(false, true)", "_actionAdmissionOpen = false", "_admittedActionCount")) -and -not [regex]::IsMatch("$core`n$service", "Consumer<|Function<|public\s+.*Runnable")) "tokenized admission only"

    Add-Result "service.required-api-and-states" (Test-ContainsAll -Path $servicePath -Tokens @(
        "NEW,", "RUNNING,", "STOPPING,", "STOPPED,", "FAILED",
        "public boolean start()", "public MaterializeResult materialize(long profileId)",
        "public DematerializeResult dematerialize(long profileId)", "public DematerializeResult retryCleanup(long profileId)",
        "public Optional<MaterializationSnapshot> find(long profileId)", "public List<MaterializationSnapshot> list()",
        "public ShutdownResult shutdown()", "public ServiceSnapshot snapshot()")) "explicit service lifecycle/API"
    Add-Result "service.required-results" (Test-ContainsAll -Path $servicePath -Tokens @(
        "SERVICE_NOT_RUNNING", "PROFILE_NOT_FOUND", "PROFILE_UNLINKED", "ALREADY_ACTIVE",
        "CHARACTER_ALREADY_ACTIVE", "CAPACITY_REACHED", "IDENTITY_BUSY",
        "RETAINED_IDENTITY_NOT_RECOVERABLE", "MATERIALIZATION_FAILED_RETAINED",
        "CLEANUP_FAILED_RETAINED", "NOT_ACTIVE")) "bounded result taxonomy"
    Add-Result "service.fair-cap-and-conditional-maps" (Test-ContainsAll -Path $servicePath -Tokens @(
        "new Semaphore(maximumMaterialized, true)", "ConcurrentHashMap<Long, Entry>",
        "ConcurrentHashMap<Integer, Entry>", "putIfAbsent(", "remove(profileId, entry)",
        "_permits.tryAcquire()", "_permits.release()")) "fair cap and exact ownership"
    Add-Result "service.no-global-slow-lock" (-not [regex]::IsMatch($service, "synchronized\s*\(_stateMonitor\)[\s\S]{0,1800}(Player\.load|\.storeMe|\.deleteMe|\.spawnMe)")) "global monitor contains reservation work only"
    Add-Result "service.no-per-actor-executor" (-not [regex]::IsMatch($service, "Executor|ScheduledFuture|CompletableFuture|new\s+Thread|ThreadPool")) "no per-actor worker"
    Add-Result "service.no-auto-selection" (-not [regex]::IsMatch($service, "findAll|listProfiles|SELECT\s+\*\s+FROM\s+phantom_profiles|scheduleAtFixedRate|scheduleWithFixedDelay")) "no automatic profile scan/materialization"
    Add-Result "shutdown.stable-two-pass-ten-seconds" (Test-ContainsAll -Path $servicePath -Tokens @(
        "MAXIMUM_SHUTDOWN_TIMEOUT_MILLIS = 10000", "sortedEntries()", "shutdownPass(",
        "if (!failed.isEmpty()", "_state = ServiceState.FAILED", "failedProfileIds()")) "bounded exact drain"

    $identityPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java"
    $recoveryPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomRetainedIdentityRecovery.java"
    $disconnectionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/Disconnection.java"
    Add-Result "identity.reserved-retained-token-aware" (Test-ContainsAll -Path $identityPath -Tokens @("RESERVED", "RETAINED", "OwnerSnapshot", "markRetained(", "releaseRetained(", "entry.matches(expected)", "_owners.remove(expected.objectId(), entry)")) "same-entry conditional release"
    Add-Result "identity.disconnection-marks-retained" (Test-ContainsAll -Path $disconnectionPath -Tokens @("retainPlayerIdentityLeaseFor(", "matchingIdentityLease", "PhantomPlayerCleanupPolicy.isComplete")) "failed/incomplete real cleanup retention"
    Add-Result "recovery.exact-evidence-and-prepared-query" (Test-ContainsAll -Path $recoveryPath -Tokens @(
        "owner.ownerKind() != OwnerKind.REAL_LOGIN", "owner.state() != OwnerState.RETAINED",
        "world.getPlayer(objectId)", "world.findObject(objectId)",
        "containsObjectId(objectId)", '"SELECT online FROM characters WHERE charId = ?"',
        "connection.prepareStatement(SELECT_CHARACTER_ONLINE)", "statement.setInt(1, objectId)",
        "if (result.next())", "online == 0", "_identityRegistry.releaseRetained(owner)")) "strict evidence and atomic removal"
    $recovery = Get-Content -LiteralPath $recoveryPath -Raw -Encoding UTF8
    Add-Result "recovery.no-periodic-or-age-release" (-not [regex]::IsMatch($recovery, "Scheduled|Executor|ThreadPool|Timer|\bage\b|createdAt|sleep|while\s*\(")) "explicit/on-demand only"

    $metricsPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java"
    $tracePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java"
    $metrics = Get-Content -LiteralPath $metricsPath -Raw -Encoding UTF8
    Add-Result "metrics.fixed-counters-current-peak" ((Test-ContainsAll -Path $metricsPath -Tokens @(
        "_materializationRequested", "_materializationSucceeded", "_materializationRejected",
        "_materializationFailuresRetained", "_dematerializationSucceeded", "_cleanupFailuresRetained",
        "_retainedRecoverySucceeded", "_retainedRecoveryRejected", "_shutdownFailures",
        "_activeCurrent", "_activePeak")) -and -not [regex]::IsMatch($metrics, "Map<|ConcurrentHashMap")) "fixed AtomicLong counters"
    Add-Result "trace.bounded-short-events" (Test-ContainsAll -Path $tracePath -Tokens @("MAX_EVENT_NAME_LENGTH = 48", "new String[capacity]", "_sampleEvery", "_events")) "bounded sampled trace"

    $componentPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileComponent.java"
    $profileSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java"
    $profileSuite = Get-Content -LiteralPath $profileSuitePath -Raw -Encoding UTF8
    Add-Result "followup.component-value-equality" (Test-ContainsAll -Path $componentPath -Tokens @("Arrays.equals(payload, other.payload)", "Arrays.hashCode(payload)")) "payload bytes by value"
    Add-Result "followup.owned-row-cleanup-sentinel" ((Test-ContainsAll -Path $profileSuitePath -Tokens @("_ownedProfileIds", "_foreignSentinelProfileId", "DELETE FROM phantom_profiles WHERE profile_id = ?", "assertForeignSentinel()", "deleteForeignSentinel()")) -and -not $profileSuite.Contains('executeUpdate("DELETE FROM phantom_profiles")')) "exact owned cleanup"
    Add-Result "followup.equality-regressions" (Test-ContainsAll -Path $profileSuitePath -Tokens @("Separately loaded equal component snapshots", "Equal component snapshots have different hash codes", "Different component payloads compare equal")) "equality/hash/difference tests"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    $productionSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java"
    $performanceSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationPerformanceSuite.java"
    Add-Result "tests.launcher-modes" (Test-ContainsAll -Path $launcherPath -Tokens @('"production-materialization"', "new PhantomProductionMaterializationSuite()", '"production-materialization-performance"', "new PhantomProductionMaterializationPerformanceSuite()")) "both forked routes"
    Add-Result "tests.ant-targets-forked" (Test-ContainsAll -Path $buildPath -Tokens @("phantom-production-materialization-test", "phantom-production-materialization-performance-smoke", "phantom-static-verify-006", 'fork="true"', "verify-task-006.ps1")) "targets and forked JVM"
    $caseCount = [regex]::Matches((Get-Content -LiteralPath $productionSuitePath -Raw -Encoding UTF8), 'registry\.add\(').Count
    Add-Result "tests.production-matrix" (($caseCount -ge 16) -and (Test-ContainsAll -Path $productionSuitePath -Tokens @(
        "CAPACITY_REACHED", "RESERVED_OWNER", "WORLD_PLAYER_PRESENT", "WORLD_OBJECT_PRESENT",
        "AUTOSAVE_PRESENT", "CHARACTER_ONLINE", "CHARACTER_NOT_FOUND", "releaseRetained(stale)",
        "CLEANUP_FAILED_RETAINED", "ServiceState.FAILED", "activePeak()"))) "$caseCount explicit cases"
    Add-Result "tests.performance-one-ten" (Test-ContainsAll -Path $performanceSuitePath -Tokens @("runCycles(context, 1)", "runCycles(context, 10)", "assertClean(", "availablePermits()")) "one/ten and residue"

    $goal005Report = Join-Path $moduleRoot "docs/phantoms/reports/005-core-profile-persistence-envelope.md"
    $goal005Review = Join-Path $moduleRoot "docs/phantoms/reviews/005-core-profile-persistence-envelope-review.md"
    $contractPath = Join-Path $moduleRoot "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md"
    $reportPath = Join-Path $moduleRoot "docs/phantoms/reports/006-production-materialization-lifecycle.md"
    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    Add-Result "docs.goal005-closure" ((Test-ContainsAll -Path $goal005Report -Tokens @(
        "9d0465eb62f9913644fab9f1d60feb2f4fd9a674", "18/18", "18/18", "69/69", "independent review", "ACCEPT", "Goal 006", "ALLOWED")) -and
        (Test-ContainsAll -Path $goal005Review -Tokens @("Goal 005: ACCEPT", "Revert: NOT_REQUIRED", "owned-row test cleanup", "value equality", "Goal 006: ALLOWED", "Goal 007: NOT_STARTED"))) "accepted baseline and carried follow-ups"
    Add-Result "docs.lifecycle-contract" (Test-ContainsAll -Path $contractPath -Tokens @("PhantomMaterializedPlayer", "PhantomMaterializationService", "RESERVED", "RETAINED", "ActionLease", "shutdown", "restart", "Goal 007")) "production lifecycle contract"
    Add-Result "docs.goal006-report" (Test-ContainsAll -Path $reportPath -Tokens @(
        "PENDING_INDEPENDENT_REVIEW", "PRODUCTION_MATERIALIZATION_LIFECYCLE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Goal 007", "NOT_STARTED", "Production DB", "no access", "three", "16/16",
        "bounded scope", "PhantomSkeletonSuite.java")) "report/gate/scope evidence"
    Add-Result "roadmap.progress-only" (Test-ContainsAll -Path $roadmapPath -Tokens @(
        "9d0465eb62f9913644fab9f1d60feb2f4fd9a674", "Goal 005", "ACCEPT",
        "Goal 006", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 007", "NOT_STARTED")) "accepted baseline and current gate"

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
    $escapedPattern = '\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};'
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
