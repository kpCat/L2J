[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "82a03342e52ff4b6c023b8ea224da8b1c2f6657f"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/activity/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-007.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/006b-server-shutdown-handoff.md") -or
        ($relative -ceq "docs/phantoms/reports/007-shared-activity-scheduler.md") -or
        ($relative -ceq "docs/phantoms/reviews/006b-server-shutdown-handoff-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/007-shared-activity-scheduler/", [System.StringComparison]::Ordinal)
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
    Add-Result "repository.goal006b-base" ($baseExists.ExitCode -eq 0) $BaseCommit

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
    Add-Result "repository.one-ordinary-goal007-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "feat(phantoms): add shared activity scheduler") $subject
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

    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/GameServer.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/network/GameClient.java",
        "java/org/l2jmobius/gameserver/phantoms/player",
        "java/org/l2jmobius/gameserver/phantoms/profile",
        "dist/db_installer",
        "tools/phantoms/verify-task-006b.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    foreach ($required in @(
        "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityState.java",
        "java/org/l2jmobius/gameserver/phantoms/activity/PhantomRelevanceSignal.java",
        "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java",
        "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityWorkSink.java",
        "java/org/l2jmobius/gameserver/phantoms/activity/PhantomSchedulerPolicy.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java",
        "docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md",
        "docs/phantoms/reports/007-shared-activity-scheduler.md",
        "docs/phantoms/reviews/006b-server-shutdown-handoff-review.md"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    $configPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
    $iniPath = Join-Path $moduleRoot "dist/game/config/Custom/PhantomPlayers.ini"
    Add-Result "config.exact-settings-and-defaults" ((Test-ContainsAll -Path $configPath -Tokens @(
        "DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES = 10000",
        "DEFAULT_SCHEDULER_PULSE_MILLIS = 100",
        "DEFAULT_SCHEDULER_PROFILES_PER_PULSE = 128",
        'strictInteger(config.getValue("MaxScheduledPhantomProfiles"), 1, 1_000_000)',
        'strictInteger(config.getValue("PhantomSchedulerPulseMillis"), 10, 1000)',
        'strictInteger(config.getValue("PhantomSchedulerProfilesPerPulse"), 1, 10000)',
        "maximumScheduled < maximumMaterialized",
        "new Settings(false, false, 0, 0, 0, 0)")) -and
        (Test-ContainsAll -Path $iniPath -Tokens @(
        "MaxScheduledPhantomProfiles = 10000",
        "PhantomSchedulerPulseMillis = 100",
        "PhantomSchedulerProfilesPerPulse = 128"))) "strict ranges, defaults and disabled zero"

    $statePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityState.java"
    $signalPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/activity/PhantomRelevanceSignal.java"
    Add-Result "state.exact-five-stable-codes" (Test-ContainsAll -Path $statePath -Tokens @(
        "ACTIVE(10, true)", "NEARBY_PERCEPTIBLE(20, true)", "WARM(30, false)",
        "BACKGROUND(40, false)", "SLEEPING(50, false)", "requiresMaterialization()")) "five explicit states"
    Add-Result "signal.immutable-and-bounded" (Test-ContainsAll -Path $signalPath -Tokens @(
        "public record PhantomRelevanceSignal",
        'Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$")',
        "sequence < 0", "MAXIMUM_TTL_MILLIS = 86_400_000",
        "(ttlMillis < 1) || (ttlMillis > MAXIMUM_TTL_MILLIS)")) "immutable source/sequence/state/TTL"

    $schedulerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $scheduler = Get-Content -LiteralPath $schedulerPath -Raw -Encoding UTF8
    $system = Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8
    Add-Result "scheduler.bounded-ready-and-due" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "ConcurrentHashMap<Long, Slot> _slots",
        "ArrayBlockingQueue<Long> _readyQueue",
        "TreeSet<DueEntry> _dueEntries",
        "new ArrayBlockingQueue<>(maximumProfiles)",
        "slot._dueEntry",
        "reserveReadyLocked")) "capacity-bound queue and one due handle per slot"
    Add-Result "scheduler.explicit-api-and-retry" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "RegistrationResult register(long profileId)",
        "UnregisterResult unregister(long profileId)",
        "SignalResult submitSignal(long profileId, PhantomRelevanceSignal signal)",
        "SignalResult withdrawSignal(long profileId, String sourceKey, long sequence)",
        "RetryResult retryTransition(long profileId)",
        "RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY")) "typed explicit registration/signals/retry"
    Add-Result "scheduler.coalescing-fairness-hysteresis" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "final boolean coalesced = slot._enqueued",
        "processedProfiles",
        "_fairnessSequence",
        "_demotionEligibleAtNanos",
        "boundedExponentialBackoff",
        "_profilesPerPulse",
        "pulseWallBudgetMillis")) "coalesced fair budgeted state machine"
    Add-Result "scheduler.one-shared-threadpool-pulse" (([regex]::Matches($scheduler, "ThreadPool\.scheduleAtFixedRate")).Count -eq 1) "one production recurring pulse call"
    Add-Result "scheduler.no-inert-runnable-api" (-not [regex]::IsMatch($scheduler, "offer\s*\(\s*Runnable|Queue\s*<\s*Runnable|ArrayBlockingQueue\s*<\s*Runnable")) "typed profile scheduler only"

    $slotStart = $scheduler.IndexOf("private static final class Slot", [System.StringComparison]::Ordinal)
    $slotEnd = $scheduler.IndexOf("private static final class SourceEntry", $slotStart, [System.StringComparison]::Ordinal)
    $slotBody = if (($slotStart -ge 0) -and ($slotEnd -gt $slotStart)) { $scheduler.Substring($slotStart, $slotEnd - $slotStart) } else { "" }
    Add-Result "scheduler.no-per-profile-task-or-player" (-not [regex]::IsMatch($slotBody, "ScheduledFuture|Future|Thread|Executor|Player|World|GameClient")) "slot contains state only"
    Add-Result "scheduler.no-per-pulse-trace-or-info" (-not [regex]::IsMatch($scheduler, "activity\.pulse|LOGGER\.(info|warning)|LOGGER\.(log|warning)\(")) "aggregate counters only"
    Add-Result "scheduler.no-goal008-009-subsystems" (-not [regex]::IsMatch($scheduler, "(?i)topology|navigation|utility.?ai|plan.?executor|population|goal.?scheduler|daily.?schedule")) "no future subsystem implementation"

    Add-Result "materialization.conservative-effective-state" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "!slot._effectiveState.requiresMaterialization() && requested.requiresMaterialization()",
        "BoundaryAction.MATERIALIZE",
        "applyTransitionOutcomeLocked",
        "if (outcome.outcome() == Outcome.SUCCESS)",
        "slot._effectiveState = plan._targetState")) "ACTIVE/NEARBY only after success"
    Add-Result "materialization.actual-service-adapter" (Test-ContainsAll -Path (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java") -Tokens @(
        "_service.materialize(profileId)", "_service.dematerialize(profileId)",
        "_service.retryCleanup(profileId)", "MATERIALIZATION_FAILED_RETAINED",
        "CLEANUP_FAILED_RETAINED")) "accepted lifecycle result mapping"

    $begin = $system.IndexOf("_scheduler.beginStop();", [System.StringComparison]::Ordinal)
    $serviceShutdown = $system.IndexOf("_materializationService.shutdown();", $begin, [System.StringComparison]::Ordinal)
    $finish = $system.IndexOf("_scheduler.finishStop();", $serviceShutdown, [System.StringComparison]::Ordinal)
    Add-Result "system.stop-before-drain-finish-after-stopped" (($begin -ge 0) -and ($serviceShutdown -gt $begin) -and ($finish -gt $serviceShutdown) -and $system.Contains("result.state() != ServiceState.STOPPED")) "begin-stop < service drain < finish-stop"
    Add-Result "system.production-zero-and-noop-sink" ((Test-ContainsAll -Path $systemPath -Tokens @(
        "new PhantomMaterializationServiceActivityPort(_materializationService)",
        "PhantomActivityWorkSink.noop()",
        "return false;",
        "_state = State.DISABLED")) -and -not [regex]::IsMatch($system, "\.register\s*\(")) "zero registrations; disabled returns before repository/service/scheduler"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java"
    $performancePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java"
    Add-Result "tests.scheduler-matrix" (([regex]::Matches((Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8), 'registry\.add\(')).Count -ge 12) "at least twelve focused deterministic cases"
    Add-Result "tests.scale-10000" (Test-ContainsAll -Path $performancePath -Tokens @(
        "10_000", "128", "SLEEPING", "WARM", "CRITICAL",
        "Future.class", "Thread.class", "Executor.class", "Player.class")) "dormant structure and fair warm burst"
    Add-Result "tests.actual-service-bridge" (Test-ContainsAll -Path (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -Tokens @(
        "PhantomMaterializationServiceActivityPort",
        "activityPort.materialize(profile.profileId())",
        "activityPort.dematerialize(profile.profileId())")) "guarded DB/headless bridge"
    Add-Result "tests.failed-drain-retains-scheduler" (Test-ContainsAll -Path (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -Tokens @(
        "Failed first drain did not retain scheduler STOPPING.",
        "Persistent service failure did not retain scheduler STOPPING.",
        "Terminal second shutdown did not finish the scheduler.",
        "Successful explicit teardown did not finish the scheduler.")) "STOPPING retention and post-service finish"
    Add-Result "build.forked-targets-and-cumulative-verify" (Test-ContainsAll -Path (Join-Path $moduleRoot "build.xml") -Tokens @(
        'name="phantom-activity-scheduler-test"',
        '<arg value="activity-scheduler" />',
        'name="phantom-activity-scheduler-performance-smoke"',
        '<arg value="activity-scheduler-performance" />',
        'name="phantom-static-verify-007"',
        "verify-task-007.ps1",
        'name="phantom-static-verify-006b" depends="phantom-static-verify-007"',
        "Run Goal 007 and all prior Phantom verification gates.")) "forked suites and static verifier chain"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/006b-server-shutdown-handoff-review.md"
    $reportPath = Join-Path $moduleRoot "docs/phantoms/reports/007-shared-activity-scheduler.md"
    $contractPath = Join-Path $moduleRoot "docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md"
    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    Add-Result "docs.goal006b-independent-acceptance" (Test-ContainsAll -Path $reviewPath -Tokens @(
        "Goal 006B: ACCEPT", "Goal 006 overall: ACCEPT", "Stage I: COMPLETE",
        "82a03342e52ff4b6c023b8ea224da8b1c2f6657f")) "immutable independent closure"
    Add-Result "docs.activity-contract" (Test-ContainsAll -Path $contractPath -Tokens @(
        "ACTIVE", "NEARBY_PERCEPTIBLE", "WARM", "BACKGROUND", "SLEEPING",
        "PhantomRelevanceSignal", "explicit retry", "ArrayBlockingQueue",
        "TreeSet", "ThreadPool.scheduleAtFixedRate", "no-op")) "scheduler contract"
    Add-Result "docs.goal007-report-and-gate" (Test-ContainsAll -Path $reportPath -Tokens @(
        "ACTIVITY_SCHEDULER_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "PENDING_INDEPENDENT_REVIEW", "Goal 006B", "Stage I",
        "Goal 008", "NOT_STARTED", "Goal 009",
        "82a03342e52ff4b6c023b8ea224da8b1c2f6657f")) "implementation handoff without self-acceptance"
    Add-Result "roadmap.progress-only" (Test-ContainsAll -Path $roadmapPath -Tokens @(
        "Goal 006B:", "ACCEPT", "Stage I:", "COMPLETE",
        "Goal 007:", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Goal 008:", "NOT_STARTED / BLOCKED",
        "Goal 009:", "NOT_STARTED / BLOCKED")) "progress facts only"

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
