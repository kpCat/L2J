[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "b6c58c37f1ba77e92b61e9499a30d17d09c82086"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStore.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "tools/phantoms/verify-task-008a.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/008-goal-utility-plan-core.md") -or
        ($relative -ceq "docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md") -or
        ($relative -ceq "docs/phantoms/reviews/008-goal-utility-plan-core-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/008a-decision-persistence-timeout-hardening/", [System.StringComparison]::Ordinal)
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

function Test-NoStoreCallInsideMonitor
{
    param([string]$Path)
    $lines = Get-Content -LiteralPath $Path -Encoding UTF8
    $braceDepth = 0
    $monitorParentDepth = -1
    $awaitingMonitorBrace = $false
    foreach ($line in $lines)
    {
        if ($line -match "synchronized\s*\(_monitor\)")
        {
            $monitorParentDepth = $braceDepth
            $awaitingMonitorBrace = $true
        }
        if (($monitorParentDepth -ge 0) -and ($line -match "_store\."))
        {
            return $false
        }
        $openCount = ([regex]::Matches($line, "\{")).Count
        $closeCount = ([regex]::Matches($line, "\}")).Count
        $braceDepth += $openCount - $closeCount
        if ($awaitingMonitorBrace -and ($openCount -gt 0))
        {
            $awaitingMonitorBrace = $false
        }
        if (($monitorParentDepth -ge 0) -and -not $awaitingMonitorBrace -and ($braceDepth -le $monitorParentDepth))
        {
            $monitorParentDepth = -1
        }
    }
    return $true
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
    Add-Result "repository.goal008-base" ($baseExists.ExitCode -eq 0) "Goal 008 commit exists"

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
    Add-Result "repository.one-ordinary-goal008a-child" ($preCommit -or $postCommit) "baseline or one ordinary child"
    $subjectValid = $true
    if ($postCommit)
    {
        $subjectValid = ((Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0] -ceq "fix(phantoms): harden decision persistence and timeouts")
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
    Add-Result "scope.no-config-schema-goal009" (@($changed | Where-Object { $_ -match "(?i)(^|/)(dist/game/config|dist/db_installer|sql|schema|migrations)(/|$)|009-" }).Count -eq 0) "frozen"

    foreach ($frozen in @(
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/model/World.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/network",
        "java/org/l2jmobius/gameserver/phantoms/player",
        "java/org/l2jmobius/gameserver/phantoms/profile",
        "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java",
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoal.java",
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateCodec.java",
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomUtilitySelector.java",
        "dist/game/config",
        "dist/db_installer",
        "tools/phantoms/verify-task-008.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    $enginePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java"
    $metricsPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java"
    $coreSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java"
    $persistenceSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java"
    $performanceSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java"
    $buildPath = Join-Path $moduleRoot "build.xml"

    foreach ($required in @(
        "docs/phantoms/tasks/008a-decision-persistence-timeout-hardening/TASK.md",
        "docs/phantoms/reviews/008-goal-utility-plan-core-review.md",
        "docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md",
        "tools/phantoms/verify-task-008a.ps1"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    Add-Result "persistence.no-store-under-monitor" (Test-NoStoreCallInsideMonitor -Path $enginePath) "all GoalStore calls outside synchronized monitor blocks"
    Add-Result "persistence.bounded-pending-attach" (Test-ContainsAll -Path $enginePath -Tokens @("_pendingAttaches", "_slots.size() + _pendingAttaches.size()", "CANCELLED_BY_STOP", "PERSISTENCE_FAILED", "!_pendingAttaches.isEmpty()")) "bounded reservation and stop retention"
    Add-Result "persistence.one-claim-per-runtime" (Test-ContainsAll -Path $enginePath -Tokens @("_persistenceInFlight", "_persistenceOperationId", "_persistenceOperationKind", "Runtime already owns a persistence operation", "isPersistenceClaimCurrentLocked")) "one exact operation token"
    Add-Result "persistence.two-phase-mutations" (Test-ContainsAll -Path $enginePath -Tokens @("claimPersistenceLocked", "executeInsert", "executeReplace", "executeDelete", "reconcileMutation", "return MutationResult.BUSY")) "claim, external call and reconcile"
    Add-Result "persistence.two-phase-reload" (Test-ContainsAll -Path $enginePath -Tokens @("PersistenceOperationKind.RELOAD", "executeLoad", "reconcileReload", "return ReloadResult.BUSY")) "reload exclusion and reconcile"
    Add-Result "persistence.two-phase-terminal" (Test-ContainsAll -Path $enginePath -Tokens @("claimTerminalPersistenceLocked", "reconcileTerminal", "TERMINAL_COMPLETE", "TERMINAL_FAIL", "executeReplace(terminalClaim)")) "terminal write outside monitor"
    Add-Result "persistence.conflict-failure-distinct" ((Test-ContainsAll -Path $enginePath -Tokens @("PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD", "PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD", "PersistenceFailure.CONFLICT", "PersistenceFailure.FAILURE")) -and (Test-ContainsAll -Path $metricsPath -Tokens @("recordDecisionPersistenceConflict", "recordDecisionPersistenceFailure"))) "explicit stable states"
    Add-Result "persistence.detach-stop-retention" (Test-ContainsAll -Path $enginePath -Tokens @("slot._inFlight || slot._persistenceInFlight", "slot._detachPending && !slot._inFlight && !slot._persistenceInFlight", "finishDetachIfPendingLocked")) "runtime retained to quiescence"
    $engineContent = Get-Content -LiteralPath $enginePath -Raw -Encoding UTF8
    Add-Result "persistence.no-automatic-retry" (-not [regex]::IsMatch($engineContent, "ScheduledFuture|Executor|CompletableFuture|scheduleAtFixedRate|scheduleWithFixedDelay")) "no persistence retry owner"
    Add-Result "timeout.explicit-unset-sentinel" (Test-ContainsAll -Path $enginePath -Tokens @("STEP_START_UNSET = -1", "_stepStartedNanos != STEP_START_UNSET", "_stepStartedNanos == STEP_START_UNSET", "_stepStartedNanos = STEP_START_UNSET")) "logical zero remains valid"
    Add-Result "snapshot.boundary-reset" (Test-ContainsAll -Path $enginePath -Tokens @("resetDecisionEvidenceLocked", "_selectedCandidateKey = null", "_selectedScore = -1", "_lastResult = null", "_explanations = List.of()")) "bounded current evidence only"

    $coreCount = ([regex]::Matches((Get-Content -LiteralPath $coreSuite -Raw -Encoding UTF8), 'registry\.add\(')).Count
    $persistenceCount = ([regex]::Matches((Get-Content -LiteralPath $persistenceSuite -Raw -Encoding UTF8), 'registry\.add\(')).Count
    Add-Result "tests.core-at-least-35" ($coreCount -ge 35) "$coreCount cases"
    Add-Result "tests.persistence-at-least-20" ($persistenceCount -ge 20) "$persistenceCount cases"
    Add-Result "tests.store-monitor-and-blocked-stop" (Test-ContainsAll -Path $persistenceSuite -Tokens @("all-store-methods-outside-engine-monitor", "blocked-attach-stop-no-late-publish", "blocked-mutation-keeps-other-profile-responsive", "Thread.holdsLock", "TimeUnit.SECONDS.toNanos(1)")) "monitor and one-second responsiveness"
    Add-Result "tests.persistence-retention-and-states" (Test-ContainsAll -Path $persistenceSuite -Tokens @("terminal-persistence-busy-detach-retention", "conflict-failure-distinct-and-reloadable", "terminal-conflict-and-failure-distinct", "MutationResult.BUSY", "ReloadResult.BUSY")) "busy, detach, conflict and failure"
    Add-Result "tests.timeout-and-success-semantics" (Test-ContainsAll -Path $coreSuite -Tokens @("logical-zero-step-timeout", "plan.step_timeout", "final-success-plan-is-nonterminal-goal", "PhantomGoalStatus.ACTIVE", "RuntimeState.NEEDS_REPLAN")) "step/total distinction retained"
    Add-Result "tests.snapshot-reset" (Test-ContainsAll -Path $coreSuite -Tokens @("goal-boundaries-reset-snapshot-evidence", "activity-generation-resets-snapshot-evidence", "stop-resets-snapshot-evidence", "assertEvidenceReset")) "ownership boundaries"
    Add-Result "tests.performance-shape" (Test-ContainsAll -Path $performanceSuite -Tokens @("PROFILE_COUNT = 1000", "CANDIDATE_COUNT = 64", "CONSIDERATION_COUNT = 8", "DISPATCH_BUDGET = 32", "Future.class", "Thread.class", "Executor.class")) "required scale shape"
    Add-Result "build.goal008a-route" (Test-ContainsAll -Path $buildPath -Tokens @("phantom-static-verify-008a", "verify-task-008a.ps1", "Run Goal 008A and all prior Phantom verification gates.")) "cumulative successor verifier"

    $productionOwners = New-Object System.Collections.Generic.List[string]
    foreach ($relative in @(
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java",
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStore.java",
        "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"))
    {
        $path = Join-Path $moduleRoot $relative
        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        if ($content -match "new\s+Thread|CompletableFuture|new\s+.*Executor|ScheduledFuture<")
        {
            [void]$productionOwners.Add($relative)
        }
    }
    Add-Result "production.no-new-runtime-owner" ($productionOwners.Count -eq 0) $(if ($productionOwners.Count -eq 0) { "none" } else { $productionOwners -join "," })

    Add-Result "docs.contract-hardened" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md") -Tokens @("PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD", "pending attach", "operation token", "logical time")) "decision contract"
    Add-Result "docs.goal008-review-fix-required" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reviews/008-goal-utility-plan-core-review.md") -Tokens @("FIX_REQUIRED", "Goal 008A: REQUIRED", "Goal 009: BLOCKED")) "independent finding provenance"
    Add-Result "docs.goal008a-report" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/008a-decision-persistence-timeout-hardening.md") -Tokens @("SUCCESS", "PENDING_INDEPENDENT_REVIEW", "DECISION_PERSISTENCE_TIMEOUT_HARDENED_PENDING_INDEPENDENT_REVIEW", "Goal 009: NOT_STARTED / BLOCKED")) "implementation handoff"
    Add-Result "docs.roadmap-progress" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md") -Tokens @("Goal 008: FIX_REQUIRED", "Goal 008A: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 009: NOT_STARTED / BLOCKED")) "progress only"

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
