[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "b6e893f6bb8abf26908e441ee79b92d6f910eb91"
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
    return ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationResult.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/Shutdown.java") -or
        ($relative -ceq "build.xml") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "tools/phantoms/verify-task-009a.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/009-navigation-feasibility-baseline.md") -or
        ($relative -ceq "docs/phantoms/reports/009a-navigation-route-ownership-hardening.md") -or
        ($relative -ceq "docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/009a-navigation-route-ownership-hardening/", [System.StringComparison]::Ordinal)
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
    Add-Result "repository.module-root" ($moduleRoot -ceq (Join-Path $gitRoot $relativeModule)) "High Five module root"
    $currentBranch = (Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")).Output[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $Branch
    $baseExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.goal009a-base" ($baseExists.ExitCode -eq 0) "b6e893f6 baseline exists"

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $preCommit = $head -ceq $BaseCommit
    $postCommit = $false
    if (-not $preCommit -and ($baseExists.ExitCode -eq 0))
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $distance = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $parentLine = (Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--parents", "-n", "1", "HEAD")).Output[0]
        $postCommit = ($parent -ceq $BaseCommit) -and ($distance -eq 1) -and (($parentLine -split " ").Count -eq 2)
    }
    Add-Result "repository.one-ordinary-goal009a-child" ($preCommit -or $postCommit) "baseline or one ordinary child"
    $subjectValid = $true
    if ($postCommit)
    {
        $subjectValid = ((Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0] -ceq "fix(phantoms): harden navigation route ownership")
    }
    Add-Result "repository.commit-subject" $subjectValid "required subject"
    $remote = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "origin/feature/phantom-world")).Output[0]
    Add-Result "repository.remote-ref" $(if ($preCommit) { $remote -ceq $BaseCommit } else { ($remote -ceq $BaseCommit) -or ($remote -ceq $head) }) "baseline or exact child during handoff"

    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit, "--", $relativeModule)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard", "--", $relativeModule)).Output
    $changed = @(Get-OrdinalSortedUnique ([string[]]($tracked + $untracked)))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) scoped artifacts"
    $scopeViolations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) $(if ($scopeViolations.Count -eq 0) { "no violations" } else { $scopeViolations -join "," })
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no task binaries"
    Add-Result "scope.no-config-schema-goal010-011" (@($changed | Where-Object { $_ -match "(?i)(^|/)(dist/game/config|dist/db_installer|sql|schema|migrations)(/|$)|goal-?010|goal-?011|topology|anchor|game-knowledge" }).Count -eq 0) "frozen"

    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/geoengine/GeoEngine.java",
        "java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java",
        "java/org/l2jmobius/gameserver/geoengine/pathfinding/NodeBuffer.java",
        "java/org/l2jmobius/gameserver/config/GeoEngineConfig.java",
        "java/org/l2jmobius/gameserver/model/actor/Creature.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java",
        "java/org/l2jmobius/gameserver/phantoms/decision",
        "java/org/l2jmobius/gameserver/phantoms/player",
        "java/org/l2jmobius/gameserver/phantoms/profile",
        "dist/game/config",
        "dist/db_installer"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    $servicePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java"
    $resultPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationResult.java"
    $metricsPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $shutdownPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/Shutdown.java"
    $coreSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java"
    $performanceSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java"
    $shutdownSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java"
    $buildPath = Join-Path $moduleRoot "build.xml"

    foreach ($required in @(
        "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md",
        "docs/phantoms/reports/009-navigation-feasibility-baseline.md",
        "docs/phantoms/reports/009a-navigation-route-ownership-hardening.md",
        "docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md",
        "tools/phantoms/verify-task-009a.ps1"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    $productionPaths = @($servicePath, $resultPath, $metricsPath, $systemPath, $shutdownPath)
    $runtimeOwners = New-Object System.Collections.Generic.List[string]
    foreach ($path in $productionPaths)
    {
        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        if ($content -match "new\s+Thread\s*\(|new\s+[A-Za-z0-9_.]*Executor[A-Za-z0-9_.]*\s*\(|CompletableFuture|ScheduledFuture<") { [void]$runtimeOwners.Add([System.IO.Path]::GetFileName($path)) }
    }
    Add-Result "navigation.no-new-runtime-owner" ($runtimeOwners.Count -eq 0) $(if ($runtimeOwners.Count -eq 0) { "shared ThreadPool only" } else { $runtimeOwners -join "," })

    $serviceContent = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
    $deadlineIndex = $serviceContent.IndexOf("deadlineExpired(entry._request, preflightLogicalNow)", [System.StringComparison]::Ordinal)
    $budgetIndex = $serviceContent.IndexOf("directDistance > routeDistanceBudget(entry._request)", [System.StringComparison]::Ordinal)
    $capabilityIndex = $serviceContent.IndexOf("_backend.capability(entry._request.origin(), entry._request.destination())", [System.StringComparison]::Ordinal)
    $directIndex = $serviceContent.IndexOf("_backend.canMoveDirect(entry._request.origin(), entry._request.destination())", [System.StringComparison]::Ordinal)
    Add-Result "navigation.preflight-before-backend" (($deadlineIndex -ge 0) -and ($budgetIndex -gt $deadlineIndex) -and ($capabilityIndex -gt $budgetIndex) -and ($directIndex -gt $capabilityIndex)) "deadline and exact route budget precede capability/direct"
    Add-Result "navigation.computed-segment-validation" (Test-ContainsAll -Path $servicePath -Tokens @("waypoints.add(entry._request.destination())", "validateSegments(entry, waypoints, cancellationGeneration, false)", "_backend.canMoveDirect(previous, waypoint)", "Status.ROUTE_OBSTRUCTED")) "normalized route including appended destination is validated"
    Add-Result "navigation.validation-cancel-deadline" (Test-ContainsAll -Path $servicePath -Tokens @("changedSince(cancellationGeneration)", "deadlineExpired(entry._request, _clock.getAsLong())", "return Status.CANCELLED", "return Status.DEADLINE_EXPIRED")) "checks surround segment validation"
    Add-Result "navigation.cache-shared-validation" (Test-ContainsAll -Path $servicePath -Tokens @("validateSegments(entry, cached._route.waypoints()", "recordNavigationCacheRouteObstructed", "recordNavigationCacheInvalidated")) "cache uses bounded segment helper"
    Add-Result "navigation.obstructed-never-published" ((Test-ContainsAll -Path $resultPath -Tokens @("ROUTE_OBSTRUCTED", "successful != (route != null)")) -and (Test-ContainsAll -Path $servicePath -Tokens @("new ValidatedPath(validationStatus, null)", "putCacheLocked(entry, validated._route"))) "typed obstruction carries no route and misses cache"
    Add-Result "metrics.route-validation-modes" (Test-ContainsAll -Path $metricsPath -Tokens @("computedRouteObstructed", "cacheRouteObstructed", "recordNavigationComputedRouteObstructed", "recordNavigationCacheRouteObstructed")) "fixed initial/cache counters"
    Add-Result "navigation.dispatch-stop-gate" (Test-ContainsAll -Path $servicePath -Tokens @("private final Object _dispatchGate", "synchronized (_dispatchGate)", "dispatchClaimedWorker", "_dispatcher.dispatch(() -> drainQueue(claim))")) "dispatch ordered with STOPPING"
    Add-Result "navigation.exact-worker-claim" (Test-ContainsAll -Path $servicePath -Tokens @("_workerClaims", "claimWorkerLocked", "releaseWorkerClaimLocked", "hasAcceptedWorkerLocked", "if (!claim._owned)", "_workers <= 0")) "exact nonnegative claim release"

    Add-Result "shutdown.aggregate-snapshot" (Test-ContainsAll -Path $systemPath -Tokens @("materializationServiceState", "retainedMaterializationEntries", "navigationState", "navigationActiveRequests", "navigationQueuedRequests", "navigationWorkers")) "materialization and navigation aggregate state"
    $shutdownContent = Get-Content -LiteralPath $shutdownPath -Raw -Encoding UTF8
    $shutdownCalls = ([regex]::Matches($shutdownContent, [regex]::Escape("PhantomSystem.shutdownIfStarted()"))).Count
    $secondShutdown = $shutdownContent.LastIndexOf("PhantomSystem.shutdownIfStarted()", [System.StringComparison]::Ordinal)
    $threadPool = $shutdownContent.IndexOf("ThreadPool.shutdown();", $secondShutdown, [System.StringComparison]::Ordinal)
    Add-Result "shutdown.two-calls-before-threadpool" (($shutdownCalls -eq 2) -and ($secondShutdown -ge 0) -and ($threadPool -gt $secondShutdown)) "exact two-call ordering"
    Add-Result "shutdown.subsystem-diagnostic" (Test-ContainsAll -Path $shutdownPath -Tokens @("Final subsystem drain is incomplete", "LOGGER.severe", "materializationServiceState", "retainedMaterializationEntries", "navigationState", "navigationActiveRequests", "navigationQueuedRequests", "navigationWorkers")) "final severe diagnostic is subsystem-wide"

    $coreContent = Get-Content -LiteralPath $coreSuite -Raw -Encoding UTF8
    $coreCount = ([regex]::Matches($coreContent, 'registry\.add\(')).Count
    Add-Result "tests.core-at-least-44" ($coreCount -ge 44) "$coreCount cases"
    Add-Result "tests.route-validation-preflight" (Test-ContainsAll -Path $coreSuite -Tokens @("expired-preflight-skips-backend", "route-budget-preflight-skips-backend", "computed-intermediate-obstruction", "appended-destination-obstruction", "valid-appended-destination", "cancellation-during-segment-validation", "deadline-during-segment-validation", "segment-validation-backend-failure", "obstruction-cooldown-direct-bypass")) "required route cases"
    Add-Result "tests.dispatch-races" (Test-ContainsAll -Path $coreSuite -Tokens @("accepted-dispatch-orders-before-stop", "rejected-dispatch-orders-before-stop", "inline-dispatcher-exact-worker-release", "beginStop overtook", "stranded worker ownership")) "accepted/rejected/inline ordering"
    Add-Result "tests.shutdown-navigation" (Test-ContainsAll -Path $shutdownSuite -Tokens @("navigation-only-blocker-snapshot", "final-diagnostic-includes-navigation-state", "navigationActiveRequests()", "navigationWorkers()", "Final subsystem drain is incomplete")) "navigation-only blocker and final policy"
    Add-Result "tests.performance-shape" (Test-ContainsAll -Path $performanceSuite -Tokens @("DIRECT_REQUESTS = 10_000", "PATH_REQUESTS = 1_000", "cacheHitRate >= 0.90", "NAVIGATION_PERFORMANCE_CANONICAL")) "deterministic structural performance"
    Add-Result "build.goal009a-routes" (Test-ContainsAll -Path $buildPath -Tokens @("phantom-navigation-core-test", "phantom-navigation-performance-smoke", "phantom-server-shutdown-handoff-test", "phantom-decision-core-test", "phantom-decision-persistence-test", "phantom-activity-scheduler-test", "phantom-production-materialization-test", "phantom-headless-player-test", "phantom-profile-persistence-test", "phantom-db-test", "phantom-static-verify-009a", "verify-task-009a.ps1", "Run Goal 009A and all prior Phantom verification gates.")) "targeted and cumulative routes"

    Add-Result "docs.navigation-contract" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md") -Tokens @("ROUTE_OBSTRUCTED", "segment", "STOPPING", "navigationWorkers")) "hardening contract"
    Add-Result "docs.goal009-review" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reviews/009-navigation-feasibility-baseline-review.md") -Tokens @("FIX_REQUIRED", "Goal 009A: REQUIRED", "Goal 010: BLOCKED", "Goal 011: NOT_STARTED")) "independent finding handoff"
    Add-Result "docs.goal009a-report" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/009a-navigation-route-ownership-hardening.md") -Tokens @("SUCCESS", "PENDING_INDEPENDENT_REVIEW", "NAVIGATION_ROUTE_OWNERSHIP_HARDENED_PENDING_INDEPENDENT_REVIEW", "Goal 010", "Goal 011")) "implementation handoff"
    Add-Result "docs.roadmap-progress" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md") -Tokens @("Goal 009: FIX_REQUIRED", "Goal 009A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 010: NOT_STARTED / BLOCKED", "Goal 011: NOT_STARTED")) "progress only"

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
    $slash = [string][char]0x005C
    $escapedPattern = @(
        ([regex]::Escape($slash + "u04") + "[0-9A-Fa-f]{2}"),
        ([regex]::Escape($slash + "u05") + "[0-9A-Fa-f]{2}"),
        ([regex]::Escape("&#" + "x04") + "[0-9A-Fa-f]{2};"),
        ([regex]::Escape("&#" + "x05") + "[0-9A-Fa-f]{2};"),
        ([regex]::Escape("&#" + "X04") + "[0-9A-Fa-f]{2};"),
        ([regex]::Escape("&#" + "X05") + "[0-9A-Fa-f]{2};")
    ) -join "|"
    foreach ($repositoryPath in $changedTextPaths)
    {
        $path = Join-Path $gitRoot $repositoryPath
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
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
    Add-Result "encoding.valid-utf8" ($invalidUtf8.Count -eq 0) $(if ($invalidUtf8.Count -eq 0) { "$($changedTextPaths.Count) text artifacts" } else { $invalidUtf8 -join "," })
    Add-Result "encoding.no-mojibake-markers" ($mojibake.Count -eq 0) $(if ($mojibake.Count -eq 0) { "none" } else { $mojibake -join "," })
    Add-Result "encoding.no-escaped-cyrillic" ($escaped.Count -eq 0) $(if ($escaped.Count -eq 0) { "none" } else { $escaped -join "," })
    Add-Result "security.no-credentials" ($credentials.Count -eq 0) $(if ($credentials.Count -eq 0) { "none" } else { $credentials -join "," })

    $self = Get-Content -LiteralPath $PSCommandPath -Raw -Encoding UTF8
    Add-Result "verifier.read-only" (-not [regex]::IsMatch($self, "(?im)^\s*(Set-Content|Add-Content|Out-File|Remove-Item|Move-Item|Copy-Item|New-Item|git\s+(add|commit|push|reset|restore|checkout|clean))\b")) "no mutation command"
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
