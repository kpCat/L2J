[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "6ecd8ba155e63a2dedeeafd65c1961fdb57bf261"
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
    return $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/navigation/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "build.xml") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-009.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md") -or
        ($relative -ceq "docs/phantoms/reports/009-navigation-feasibility-baseline.md") -or
        ($relative -ceq "docs/phantoms/reviews/008a-decision-persistence-timeout-hardening-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/009-navigation-feasibility-baseline/", [System.StringComparison]::Ordinal)
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
    Add-Result "repository.goal009-base" ($baseExists.ExitCode -eq 0) "6ecd8ba1 baseline exists"

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
    Add-Result "repository.one-ordinary-goal009-child" ($preCommit -or $postCommit) "baseline or one ordinary child"
    $subjectValid = $true
    if ($postCommit)
    {
        $subjectValid = ((Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0] -ceq "feat(phantoms): add navigation service baseline")
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
    Add-Result "scope.no-config-schema-topology-knowledge" (@($changed | Where-Object { $_ -match "(?i)(^|/)(dist/game/config|dist/db_installer|sql|schema|migrations)(/|$)|goal-?010|goal-?011|topology|anchor|game-knowledge" }).Count -eq 0) "frozen"

    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/geoengine/GeoEngine.java",
        "java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java",
        "java/org/l2jmobius/gameserver/geoengine/pathfinding/NodeBuffer.java",
        "java/org/l2jmobius/gameserver/config/GeoEngineConfig.java",
        "java/org/l2jmobius/gameserver/model/actor/Creature.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/Shutdown.java",
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

    $navigationRoot = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/navigation"
    $servicePath = Join-Path $navigationRoot "PhantomNavigationService.java"
    $backendPath = Join-Path $navigationRoot "L2jNavigationBackend.java"
    $policyPath = Join-Path $navigationRoot "PhantomNavigationPolicy.java"
    $routePath = Join-Path $navigationRoot "PhantomNavigationRoute.java"
    $progressPath = Join-Path $navigationRoot "PhantomNavigationProgressTracker.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $metricsPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java"
    $coreSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationCoreSuite.java"
    $performanceSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomNavigationPerformanceSuite.java"
    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    $buildPath = Join-Path $moduleRoot "build.xml"

    foreach ($required in @(
        "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md",
        "docs/phantoms/reports/009-navigation-feasibility-baseline.md",
        "docs/phantoms/reviews/008a-decision-persistence-timeout-hardening-review.md",
        "tools/phantoms/verify-task-009.ps1"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    $productionNavigation = Get-ChildItem -LiteralPath $navigationRoot -Filter "*.java" -File
    $forbiddenImports = New-Object System.Collections.Generic.List[string]
    $runtimeOwners = New-Object System.Collections.Generic.List[string]
    foreach ($file in $productionNavigation)
    {
        $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        if ($content -match "(?m)^import\s+.*(?:Player|Creature|GameClient|packet|\.ai\.)") { [void]$forbiddenImports.Add($file.Name) }
        if ($content -match "new\s+Thread|new\s+.*Executor|CompletableFuture|ScheduledFuture<") { [void]$runtimeOwners.Add($file.Name) }
    }
    Add-Result "navigation.no-actor-client-packet-ai-imports" ($forbiddenImports.Count -eq 0) $(if ($forbiddenImports.Count -eq 0) { "none" } else { $forbiddenImports -join "," })
    Add-Result "navigation.no-new-runtime-owner" ($runtimeOwners.Count -eq 0) $(if ($runtimeOwners.Count -eq 0) { "shared ThreadPool only" } else { $runtimeOwners -join "," })

    Add-Result "backend.factual-api" (Test-ContainsAll -Path $backendPath -Tokens @("GeoEngine.getInstance()", "hasGeo(origin.x(), origin.y())", "hasGeo(destination.x(), destination.y())", "GeoEngineConfig.PATHFINDING > 0", "canMoveToTarget(", "PathFinding.getInstance().findPath(", "true);")) "exact High Five APIs"
    Add-Result "backend.capability-modes" (Test-ContainsAll -Path $backendPath -Tokens @("NO_GEODATA", "PARTIAL_GEODATA", "GEODATA_DIRECT_ONLY", "GEODATA_PATHFINDING", "!startGeo && !targetGeo", "startGeo != targetGeo")) "factual endpoint mapping"
    $backendContent = Get-Content -LiteralPath $backendPath -Raw -Encoding UTF8
    Add-Result "backend.lazy-singletons" (-not [regex]::IsMatch($backendContent, "(?m)^\s*(?:private|public|protected)\s+(?:static\s+)?final\s+(?:GeoEngine|PathFinding)")) "no eager singleton field"

    $serviceContent = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
    $directIndex = $serviceContent.IndexOf("_backend.canMoveDirect(entry._request.origin(), entry._request.destination())", [System.StringComparison]::Ordinal)
    $cacheIndex = $serviceContent.IndexOf("findRevalidatedCache(entry, logicalNow)", [System.StringComparison]::Ordinal)
    $pathIndex = $serviceContent.IndexOf("_backend.findPath(entry._request, entry._cancellation)", [System.StringComparison]::Ordinal)
    Add-Result "navigation.direct-before-cache-and-path" (($directIndex -ge 0) -and ($cacheIndex -gt $directIndex) -and ($pathIndex -gt $cacheIndex)) "direct, cache, A* order"
    Add-Result "navigation.explicit-no-geo-direct" (Test-ContainsAll -Path $servicePath -Tokens @("DIRECT_UNVERIFIED_NO_GEODATA", "Mode.DIRECT_UNVERIFIED_NO_GEODATA", "case NO_GEODATA, PARTIAL_GEODATA", "Status.NO_GEODATA")) "unverified versus blocked"
    Add-Result "navigation.no-fallback-after-path" (-not [regex]::IsMatch($serviceContent.Substring($pathIndex), "Mode\.DIRECT_|Status\.DIRECT_")) "no late direct route"
    Add-Result "navigation.queue-worker-bounds" ((Test-ContainsAll -Path $servicePath -Tokens @("ArrayBlockingQueue<RequestEntry>", "maximumConcurrentPathfinders()", "ThreadPool.schedule(worker, 0) != null", "worker drains bounded queued requests")) -or (Test-ContainsAll -Path $servicePath -Tokens @("ArrayBlockingQueue<RequestEntry>", "maximumConcurrentPathfinders()", "ThreadPool.schedule(worker, 0) != null", "drainQueue"))) "bounded shared transient drains"
    Add-Result "navigation.registry-result-bounds" (Test-ContainsAll -Path $servicePath -Tokens @("maximumTrackedProfiles()", "_activeByProfile", "_completed", "_cooldowns", "while (_completed.size()", "while (_cooldowns.size()")) "bounded ownership"
    Add-Result "navigation.cancel-deadline-discard" (Test-ContainsAll -Path $servicePath -Tokens @("changedSince(cancellationGeneration)", "deadlineExpired(entry._request, completedLogicalNanos)", "Status.CANCELLED", "Status.DEADLINE_EXPIRED", "putCacheLocked")) "late results checked before cache/publish"
    Add-Result "navigation.cache-lru-ttl-revalidation" (Test-ContainsAll -Path $servicePath -Tokens @("new LinkedHashMap<>(16, 0.75f, true)", "cacheTtlNanos()", "_backend.canMoveDirect(previous, waypoint)", "recordNavigationCacheInvalidated", "recordNavigationCacheEvicted")) "bounded revalidated cache"
    Add-Result "navigation.cooldown-only-after-direct" (($serviceContent.IndexOf("_cooldowns.get", [System.StringComparison]::Ordinal) -gt $directIndex) -and (Test-ContainsAll -Path $servicePath -Tokens @("setCooldownLocked", "pathfindingCooldownNanos()", "Status.COOLDOWN"))) "A* cooldown"
    Add-Result "navigation.route-bounds" ((Test-ContainsAll -Path $policyPath -Tokens @("12_000", "64", "100_000", "256", "1024")) -and (Test-ContainsAll -Path $routePath -Tokens @("copy.size() > maximumWaypoints", "totalDistance > maximumRouteDistance", "List.copyOf", "copy.getLast().equals(destination)"))) "distance, waypoint and immutability"
    $timeoutIndex = (Get-Content -LiteralPath $progressPath -Raw -Encoding UTF8).IndexOf("ProgressStatus.TIMEOUT", [System.StringComparison]::Ordinal)
    $stuckIndex = (Get-Content -LiteralPath $progressPath -Raw -Encoding UTF8).IndexOf("ProgressStatus.STUCK", [System.StringComparison]::Ordinal)
    Add-Result "navigation.progress-timeout-before-stuck" (($timeoutIndex -ge 0) -and ($stuckIndex -gt $timeoutIndex) -and (Test-ContainsAll -Path $progressPath -Tokens @("ProgressStatus.ARRIVED", "minimumProgress()", "arrivalRadius()", "stuckWindowNanos()", "maximumAttemptDurationNanos()"))) "pure tracker ordering"

    $systemContent = Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8
    $navigationStartIndex = $systemContent.IndexOf("_navigationService.start()", [System.StringComparison]::Ordinal)
    $schedulerStartIndex = $systemContent.IndexOf("_scheduler.start()", [System.StringComparison]::Ordinal)
    Add-Result "system.inert-enabled-start" (($navigationStartIndex -ge 0) -and ($schedulerStartIndex -gt $navigationStartIndex) -and (Test-ContainsAll -Path $systemPath -Tokens @("if (!_settings.enabled())", "_navigationService = new PhantomNavigationService(_metrics)", "ServiceSnapshot.inactive()"))) "disabled no service; enabled empty service"
    $schedulerBegin = $systemContent.IndexOf("_scheduler.beginStop()", [System.StringComparison]::Ordinal)
    $decisionBegin = $systemContent.IndexOf("_decisionEngine.beginStop()", $schedulerBegin, [System.StringComparison]::Ordinal)
    $navigationBegin = $systemContent.IndexOf("_navigationService.beginStop()", $decisionBegin, [System.StringComparison]::Ordinal)
    $materializationStop = $systemContent.IndexOf("_materializationService.shutdown()", $navigationBegin, [System.StringComparison]::Ordinal)
    $schedulerFinish = $systemContent.IndexOf("_scheduler.finishStop()", $materializationStop, [System.StringComparison]::Ordinal)
    $decisionFinish = $systemContent.IndexOf("_decisionEngine.finishStop()", $schedulerFinish, [System.StringComparison]::Ordinal)
    $navigationFinish = $systemContent.IndexOf("_navigationService.finishStop()", $decisionFinish, [System.StringComparison]::Ordinal)
    Add-Result "system.shutdown-order" (($schedulerBegin -ge 0) -and ($decisionBegin -gt $schedulerBegin) -and ($navigationBegin -gt $decisionBegin) -and ($materializationStop -gt $navigationBegin) -and ($schedulerFinish -gt $materializationStop) -and ($decisionFinish -gt $schedulerFinish) -and ($navigationFinish -gt $decisionFinish)) "begin, drain, finish sequence"
    Add-Result "metrics.fixed-aggregate" (Test-ContainsAll -Path $metricsPath -Tokens @("NavigationSnapshot", "submissionsAccepted", "directUnverified", "queuedCurrent", "workersPeak", "cacheInvalidated", "pathTimedOut", "queueWaitExpired", "cooldownRejected", "routeBudgetRejected", "attemptTimeout", "finishStopFailures")) "fixed counters only"

    $coreCount = ([regex]::Matches((Get-Content -LiteralPath $coreSuite -Raw -Encoding UTF8), 'registry\.add\(')).Count
    Add-Result "tests.core-at-least-30" ($coreCount -ge 30) "$coreCount cases"
    Add-Result "tests.core-required-cases" (Test-ContainsAll -Path $coreSuite -Tokens @("direct-unverified-no-geodata", "queue-backpressure-atomic", "inflight-cancellation-late-discard", "deadline-during-backend-late-discard", "cache-invalidated-by-obstacle", "cooldown-does-not-block-direct", "attempt-timeout-precedes-stuck")) "required deterministic cases"
    Add-Result "tests.performance-shape" (Test-ContainsAll -Path $performanceSuite -Tokens @("DIRECT_REQUESTS = 10_000", "PATH_REQUESTS = 1_000", "cacheHitRate >= 0.90", "peakQueuedRequests() <= 256", "peakWorkers() <= 2", "peakCacheEntries() <= 1024", "NAVIGATION_PERFORMANCE_CANONICAL")) "required scale and canonical summary"
    Add-Result "tests.launcher-routes" (Test-ContainsAll -Path $launcherPath -Tokens @('case "navigation-core"', 'case "navigation-performance"')) "launcher modes"
    Add-Result "build.goal009-routes" (Test-ContainsAll -Path $buildPath -Tokens @("phantom-navigation-core-test", "phantom-navigation-performance-smoke", "phantom-static-verify-009", "verify-task-009.ps1", "Run Goal 009 and all prior Phantom verification gates.")) "Ant targets and cumulative verifier"

    Add-Result "docs.navigation-contract" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/architecture/NAVIGATION_SERVICE_CONTRACT.md") -Tokens @("DIRECT_UNVERIFIED_NO_GEODATA", "ArrayBlockingQueue", "ThreadPool", "PathFinding", "Creature", "Goal 010")) "architecture contract"
    Add-Result "docs.goal008a-accepted" ((Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reviews/008a-decision-persistence-timeout-hardening-review.md") -Tokens @("ACCEPT", "Goal 009")) -and (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md") -Tokens @("ACCEPT", "6ecd8ba155e63a2dedeeafd65c1961fdb57bf261"))) "immutable closure"
    Add-Result "docs.goal009-report" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/009-navigation-feasibility-baseline.md") -Tokens @("SUCCESS", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "NAVIGATION_SERVICE_BASELINE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 010: NOT_STARTED")) "implementation handoff"
    Add-Result "docs.roadmap-progress" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md") -Tokens @("Goal 008: ACCEPT", "Goal 008A: ACCEPT", "Goal 009: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 010: NOT_STARTED", "Goal 011: NOT_STARTED")) "progress only"

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
