[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "9958edd9e133557f4966eed0a4124e68326401b3"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityTransitionStatus.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityResultCategory.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "tools/phantoms/verify-task-007a.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/007-shared-activity-scheduler.md") -or
        ($relative -ceq "docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md") -or
        ($relative -ceq "docs/phantoms/reviews/007-shared-activity-scheduler-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/", [System.StringComparison]::Ordinal)
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
    Add-Result "repository.goal007-base" ($baseExists.ExitCode -eq 0) $BaseCommit

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
    Add-Result "repository.one-ordinary-goal007a-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "fix(phantoms): harden scheduler transition ownership") $subject
        $remote = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "origin/feature/phantom-world")).Output[0]
        Add-Result "repository.remote-exact" ($remote -ceq $head) "$remote|$head"
    }
    else
    {
        Add-Result "repository.commit-subject" $true "checked after commit"
        Add-Result "repository.remote-exact" $true "checked after commit and push"
    }

    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit, "--", $relativeModule)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard", "--", $relativeModule)).Output
    $changed = @(Get-OrdinalSortedUnique ([string[]]($tracked + $untracked)))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    $scopeViolations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) $(if ($scopeViolations.Count -eq 0) { "no violations" } else { $scopeViolations -join "," })
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no task binaries"
    Add-Result "scope.no-config-or-schema" (@($changed | Where-Object { $_ -match "(?i)(^|/)(dist/game/config|dist/db_installer|sql|schema|migrations)(/|$)" }).Count -eq 0) "config/schema frozen"
    Add-Result "scope.no-goal008-009" (@($changed | Where-Object { $_ -match "(?i)(goal|task)[-_ ]?00[89]|/00[89]-" }).Count -eq 0) "Goal 008/009 absent"

    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/model/World.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/network/Disconnection.java",
        "java/org/l2jmobius/gameserver/network/GameClient.java",
        "java/org/l2jmobius/gameserver/phantoms/player",
        "java/org/l2jmobius/gameserver/phantoms/profile",
        "dist/game/config",
        "dist/db_installer",
        "tools/phantoms/verify-task-007.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    foreach ($required in @(
        "docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/TASK.md",
        "docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/ACCEPTANCE.md",
        "docs/phantoms/tasks/007a-scheduler-transition-ownership-hardening/TEST_CASES.md",
        "docs/phantoms/reviews/007-shared-activity-scheduler-review.md",
        "docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md",
        "tools/phantoms/verify-task-007a.ps1"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    $schedulerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $portPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityMaterializationPort.java"
    $adapterPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/activity/PhantomMaterializationServiceActivityPort.java"
    $scheduler = Get-Content -LiteralPath $schedulerPath -Raw -Encoding UTF8
    $system = Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8
    $adapter = Get-Content -LiteralPath $adapterPath -Raw -Encoding UTF8

    $retainedIndex = $scheduler.IndexOf("if (slot._retainedFailureKind != RetainedFailureKind.NONE)", [System.StringComparison]::Ordinal)
    $equalIndex = $scheduler.IndexOf("if (requested == slot._effectiveState)", [System.StringComparison]::Ordinal)
    Add-Result "scheduler.retained-precedes-equality" (($retainedIndex -ge 0) -and ($equalIndex -gt $retainedIndex)) "$retainedIndex|$equalIndex"
    Add-Result "scheduler.slot-boundary-markers" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "boolean _processing", "boolean _boundaryInFlight", "long _boundaryGeneration",
        "slot._boundaryInFlight = true", "slot._boundaryGeneration = plan._generation",
        "slot._boundaryInFlight = false")) "processing/boundary/generation markers"
    Add-Result "scheduler.slot-removal-guarded" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "isTerminalNonMaterializedLocked", "!slot._processing && !slot._boundaryInFlight",
        "if (slot._processing || slot._boundaryInFlight)", "removeSlotLocked(slot)")) "no physical in-flight removal"
    Add-Result "scheduler.unregister-coalesced-pending" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "reserveReadyLocked(slot)", "slot._unregisterRequested = true",
        "source._signal = null", "slot._requestedState = PhantomActivityState.SLEEPING",
        "PhantomActivityTransitionStatus.UNREGISTER_PENDING")) "pending unregister clears signals and reserves opportunity"
    Add-Result "scheduler.cleanup-retry-truth" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "freshMaterializationRequired", "PhantomActivityState.SLEEPING : plan._targetState",
        "PhantomActivityTransitionStatus.PROMOTION_PENDING", "ensureNextOpportunityLocked(slot, logicalNow)")) "cleanup success remains non-materialized before fresh promotion"
    Add-Result "scheduler.cleanup-success-confirms-no-ownership" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "_materializationPort.retryCleanup", "_materializationPort.hasLifecycleOwnership",
        "TransitionOutcome.retainedFailure()")) "retry success rejected while ownership remains"
    Add-Result "scheduler.one-pulse-in-flight" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "boolean _pulseInFlight", "|| _pulseInFlight", "_pulseInFlight = true",
        "_pulseInFlight = false", "boolean pulseInFlight")) "single pulse claim and snapshot"
    Add-Result "scheduler.stopping-prevents-new-boundary-work" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "if ((_state != SchedulerState.RUNNING) || (_slots.get(slot._profileId) != slot))",
        "if (_state != SchedulerState.RUNNING)",
        "if ((_state != SchedulerState.RUNNING) || slot._unregisterRequested")) "RUNNING gates processing and work"
    Add-Result "scheduler.finish-refuses-in-flight" (Test-ContainsAll -Path $schedulerPath -Tokens @(
        "if (_pulseInFlight || hasInFlightSlotLocked())",
        "if (slot._processing || slot._boundaryInFlight)",
        "return false;")) "finishStop retains in-flight state"

    Add-Result "adapter.actual-lifecycle-ownership" ((Test-ContainsAll -Path $portPath -Tokens @(
        "boolean hasLifecycleOwnership(long profileId)")) -and (Test-ContainsAll -Path $adapterPath -Tokens @(
        "return _service.find(profileId).isPresent();",
        "return hasLifecycleOwnership(profileId) ? TransitionOutcome.retainedFailure()",
        "final boolean lifecycleOwned = hasLifecycleOwnership(profileId)",
        "if (!lifecycleOwned"))) "service entry ownership drives retained mapping"
    Add-Result "adapter.success-exact-active" (Test-ContainsAll -Path $adapterPath -Tokens @(
        "result.status() == ResultStatus.SUCCESS",
        "result.status() == ResultStatus.ALREADY_ACTIVE",
        "isMaterialized(profileId)",
        "snapshot.state() == State.ACTIVE")) "success and exact ACTIVE mapping"
    Add-Result "system.finish-result-checked" (([regex]::Matches($system, "if \(!_scheduler\.finishStop\(\)\)")).Count -ge 2) "RUNNING and FAILED shutdown paths"
    Add-Result "system.failed-instance-retained" (Test-ContainsAll -Path $systemPath -Tokens @(
        "_state = State.FAILED;", "return false;", "if (_state == State.FAILED)",
        "configured.snapshot().state() == State.STOPPED")) "configured instance clears only at STOPPED"

    $slotStart = $scheduler.IndexOf("private static final class Slot", [System.StringComparison]::Ordinal)
    $slotEnd = $scheduler.IndexOf("private static final class SourceEntry", $slotStart, [System.StringComparison]::Ordinal)
    $slotBody = if (($slotStart -ge 0) -and ($slotEnd -gt $slotStart)) { $scheduler.Substring($slotStart, $slotEnd - $slotStart) } else { "" }
    Add-Result "scheduler.no-per-profile-runtime-owner" (-not [regex]::IsMatch($slotBody, "ScheduledFuture|Future|Thread|Executor|Player|World|GameClient")) "slot has bounded state only"
    $productionChanged = @($changed | Where-Object { $_.StartsWith("$modulePrefix`java/", [System.StringComparison]::Ordinal) -and $_.EndsWith(".java", [System.StringComparison]::Ordinal) })
    $rawThreadFiles = @()
    foreach ($repositoryPath in $productionChanged)
    {
        $content = Get-Content -LiteralPath (Join-Path $gitRoot $repositoryPath) -Raw -Encoding UTF8
        if ($content -match "new\s+Thread\s*\(") { $rawThreadFiles += $repositoryPath }
    }
    Add-Result "production.no-new-raw-thread" ($rawThreadFiles.Count -eq 0) $(if ($rawThreadFiles.Count -eq 0) { "none" } else { $rawThreadFiles -join "," })
    Add-Result "scheduler.one-existing-recurring-pulse" (([regex]::Matches($scheduler, "ThreadPool\.scheduleAtFixedRate")).Count -eq 1) "one shared recurring call"

    $schedulerSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java"
    $productionSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java"
    $shutdownSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java"
    $schedulerSuite = Get-Content -LiteralPath $schedulerSuitePath -Raw -Encoding UTF8
    Add-Result "tests.scheduler-at-least-17" (([regex]::Matches($schedulerSuite, 'registry\.add\(')).Count -ge 17) "focused scheduler cases"
    Add-Result "tests.in-flight-unregister" (Test-ContainsAll -Path $schedulerSuitePath -Tokens @(
        "in-flight-promotion-unregister-cleans-late-success",
        "in-flight-promotion-unregister-retained-explicit-retry",
        "boundaryInFlight()", "UnregisterStatus.PENDING")) "late success and retained paths"
    Add-Result "tests.retained-truth" (Test-ContainsAll -Path $schedulerSuitePath -Tokens @(
        "retained-dematerialization-new-active-requires-fresh-materialize",
        "requested==effective erased retained cleanup ownership.",
        "Cleanup retry falsely published ACTIVE",
        "Fresh ACTIVE opportunity did not call materialize.")) "retained precedence and fresh promotion"
    Add-Result "tests.withdrawal-expiry" (Test-ContainsAll -Path $schedulerSuitePath -Tokens @(
        "retained-materialization-survives-withdrawal-and-expiry",
        "Signal withdrawal erased retained materialization ownership.",
        "TTL expiry erased retained materialization ownership.")) "signal loss cannot erase retained ownership"
    Add-Result "tests.stop-races" (Test-ContainsAll -Path $schedulerSuitePath -Tokens @(
        "stopping-quiesces-boundary-and-work",
        "finishStop cleared a blocked boundary pulse.",
        "finishStop cleared a blocked work pulse.",
        "STOPPING started work after the boundary returned.")) "boundary and work quiescence"
    Add-Result "tests.real-retained-collision" (Test-ContainsAll -Path $productionSuitePath -Tokens @(
        "20-real-retained-collision-scheduler-ownership",
        "FailurePoint.AFTER_PLAYER_LOAD",
        "Outcome.RETAINED_FAILURE",
        "activityPort.hasLifecycleOwnership",
        "automatic materialize loop",
        "Fresh materialization did not restore ACTIVE")) "guarded service ownership integration"
    Add-Result "tests.system-finish-stop" (Test-ContainsAll -Path $shutdownSuitePath -Tokens @(
        "05-in-flight-scheduler-pulse-retains-configured-system",
        "PhantomSystem reported STOPPED while its scheduler pulse was in flight.",
        "scheduler.snapshot().pulseInFlight()",
        "Explicit shutdown after scheduler quiescence did not finish.")) "configured instance retained until pulse quiescence"
    Add-Result "tests.scale-still-10000" (Test-ContainsAll -Path (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java") -Tokens @(
        "10_000", "128", "Future.class", "Thread.class", "Executor.class", "Player.class")) "bounded scale smoke retained"

    Add-Result "build.cumulative-goal007a" (Test-ContainsAll -Path (Join-Path $moduleRoot "build.xml") -Tokens @(
        'name="phantom-static-verify-007" depends="phantom-static-verify-007a"',
        'name="phantom-static-verify-007a"',
        "verify-task-007a.ps1",
        "Run Goal 007A and all prior Phantom verification gates.")) "historical verifier preserved through Goal 007A"
    Add-Result "docs.contract-hardened" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/architecture/ACTIVITY_SCHEDULER_CONTRACT.md") -Tokens @(
        "processing", "boundaryInFlight", "retained", "fresh materialize", "pulseInFlight", "finishStop")) "ownership and quiescence contract"
    Add-Result "docs.review-fix-required" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reviews/007-shared-activity-scheduler-review.md") -Tokens @(
        "FIX_REQUIRED", "Goal 007A", "9958edd9e133557f4966eed0a4124e68326401b3")) "independent findings preserved"
    Add-Result "docs.goal007a-report" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md") -Tokens @(
        "SUCCESS", "ACTIVITY_SCHEDULER_HARDENED_PENDING_INDEPENDENT_REVIEW",
        "Goal 008", "NOT_STARTED / BLOCKED", "Goal 009")) "implementation handoff without next goal"
    Add-Result "docs.roadmap-progress-only" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md") -Tokens @(
        "Goal 007:", "FIX_REQUIRED", "Goal 007A:", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Goal 008:", "NOT_STARTED / BLOCKED", "Goal 009:", "NOT_STARTED / BLOCKED")) "progress only"

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
    $escapedPattern = [regex]::Escape($slash + "u04") + "[0-9A-Fa-f]{2}|" +
        [regex]::Escape($slash + "u05") + "[0-9A-Fa-f]{2}|" +
        [regex]::Escape("&#" + "x04") + "[0-9A-Fa-f]{2};|" +
        [regex]::Escape("&#" + "x05") + "[0-9A-Fa-f]{2};|" +
        [regex]::Escape("&#" + "X04") + "[0-9A-Fa-f]{2};|" +
        [regex]::Escape("&#" + "X05") + "[0-9A-Fa-f]{2};"
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
