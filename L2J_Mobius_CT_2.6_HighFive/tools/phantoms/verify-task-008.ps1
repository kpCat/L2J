[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivityWorkItem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/activity/PhantomActivitySnapshot.java") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/decision/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerPerformanceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-008.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/reports/007a-scheduler-transition-ownership-hardening.md") -or
        ($relative -ceq "docs/phantoms/reports/008-goal-utility-plan-core.md") -or
        ($relative -ceq "docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md") -or
        $relative.StartsWith("docs/phantoms/tasks/008-goal-utility-plan-core/", [System.StringComparison]::Ordinal)
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
    Add-Result "repository.goal008-base" ($baseExists.ExitCode -eq 0) "accepted baseline exists"

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
    Add-Result "repository.one-ordinary-goal008-child" ($preCommit -or $postCommit) "baseline or one ordinary child"
    $subjectValid = $true
    if ($postCommit)
    {
        $subjectValid = ((Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0] -ceq "feat(phantoms): add goal utility plan core")
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
        "dist/game/config",
        "dist/db_installer",
        "tools/phantoms/verify-task-007a.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    $decisionRoot = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/decision"
    foreach ($required in @(
        "PhantomDomainRef.java", "PhantomCapabilityRequirement.java", "PhantomCapabilitySet.java",
        "PhantomGoal.java", "PhantomGoalStateCodec.java", "PhantomGoalStateStore.java",
        "PhantomCandidateRegistry.java", "PhantomStepHandlerRegistry.java", "PhantomUtilitySelector.java",
        "PhantomPlan.java", "PhantomPlanStep.java", "PhantomDecisionEngine.java"))
    {
        Add-Result "artifact.decision.$required" (Test-Path -LiteralPath (Join-Path $decisionRoot $required) -PathType Leaf) $required
    }
    foreach ($required in @(
        "docs/phantoms/architecture/DECISION_GOAL_PLAN_CONTRACT.md",
        "docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md",
        "docs/phantoms/reports/008-goal-utility-plan-core.md",
        "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPersistenceSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java",
        "tools/phantoms/verify-task-008.ps1"))
    {
        Add-Result "artifact.$required" (Test-Path -LiteralPath (Join-Path $moduleRoot $required) -PathType Leaf) $required
    }

    $goalPath = Join-Path $decisionRoot "PhantomGoal.java"
    $codecPath = Join-Path $decisionRoot "PhantomGoalStateCodec.java"
    $storePath = Join-Path $decisionRoot "PhantomGoalStateStore.java"
    $candidatePath = Join-Path $decisionRoot "PhantomCandidateRegistry.java"
    $handlerPath = Join-Path $decisionRoot "PhantomStepHandlerRegistry.java"
    $selectorPath = Join-Path $decisionRoot "PhantomUtilitySelector.java"
    $planPath = Join-Path $decisionRoot "PhantomPlan.java"
    $stepPath = Join-Path $decisionRoot "PhantomPlanStep.java"
    $enginePath = Join-Path $decisionRoot "PhantomDecisionEngine.java"
    $schedulerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"

    Add-Result "model.domain-ref-bounded" (Test-ContainsAll -Path (Join-Path $decisionRoot "PhantomDomainRef.java") -Tokens @("^[a-z][a-z0-9_.-]{0,31}$", "128", "visible ASCII", "record PhantomDomainRef")) "namespace/key bounds"
    Add-Result "model.goal-v1-bounded" (Test-ContainsAll -Path $goalPath -Tokens @("SCHEMA_VERSION = 1", "MAX_VALID_SOURCES = 16", "MAX_CONSTRAINTS = 16", "revision < 0", "PhantomGoalStatus")) "immutable versioned goal"
    Add-Result "model.capabilities-bounded" (Test-ContainsAll -Path (Join-Path $decisionRoot "PhantomCapabilitySet.java") -Tokens @("MAX_CAPABILITIES = 128", "new TreeMap", "Collections.unmodifiableMap", "rank")) "generic sorted capabilities"
    Add-Result "persistence.envelope-v1" ((Test-ContainsAll -Path $storePath -Tokens @('"goal.runtime"', "COMPONENT_SCHEMA_VERSION = 1", "insertComponent", "updateComponent", "deleteComponent")) -and (Test-ContainsAll -Path $codecPath -Tokens @("MAGIC", "FORMAT_VERSION = 1", "MAX_PAYLOAD_BYTES", "Trailing bytes", "Truncated"))) "binary component envelope"
    $codec = Get-Content -LiteralPath $codecPath -Raw -Encoding UTF8
    Add-Result "persistence.no-json-java-serialization" (-not [regex]::IsMatch($codec, "Object(Input|Output)Stream|Serializable|Gson|Jackson|JSONObject|JsonParser")) "manual deterministic binary"
    Add-Result "persistence.no-plan-fields" (-not [regex]::IsMatch($codec, "PhantomPlan|handler|candidate evaluation|cancellation")) "goal only"
    Add-Result "registry.candidate-256-sealed" (Test-ContainsAll -Path $candidatePath -Tokens @("MAX_CANDIDATES = 256", "void seal()", "TreeMap", "List.copyOf", "registry is sealed")) "bounded sealed candidate registry"
    Add-Result "registry.handler-256-sealed" (Test-ContainsAll -Path $handlerPath -Tokens @("MAX_HANDLERS = 256", "void seal()", "TreeMap", "Collections.unmodifiableMap", "registry is sealed")) "bounded sealed handler registry"
    Add-Result "utility.integer-deterministic" (Test-ContainsAll -Path $selectorPath -Tokens @("weightedScore / totalWeight", "candidate.key().compareTo", "MAX_EXPLANATIONS = 8", "score() < 0", "score() > 1000")) "integer score/tie/top-eight"
    Add-Result "plan.bounds" ((Test-ContainsAll -Path $planPath -Tokens @("MAX_STEPS = 32", "86_400_000", "contiguous")) -and (Test-ContainsAll -Path $stepPath -Tokens @("MAX_ARGUMENTS = 16", "3_600_000", "maximumAttempts", "> 10"))) "typed plan bounds"
    Add-Result "engine.work-sink" (Test-ContainsAll -Path $enginePath -Tokens @("implements PhantomActivityWorkSink", "public void accept(PhantomActivityWorkItem", "handler.execute", "slot._inFlight", "finishStaleLocked", "PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD")) "one slice/generation/conflict"
    $engineContent = Get-Content -LiteralPath $enginePath -Raw -Encoding UTF8
    $acceptStart = $engineContent.IndexOf("public void accept", [System.StringComparison]::Ordinal)
    $findStart = $engineContent.IndexOf("public Optional<RuntimeSnapshot> find", $acceptStart, [System.StringComparison]::Ordinal)
    $acceptBody = if (($acceptStart -ge 0) -and ($findStart -gt $acceptStart)) { $engineContent.Substring($acceptStart, $findStart - $acceptStart) } else { "" }
    Add-Result "engine.no-tick-store-read" (($acceptBody.Length -gt 0) -and (-not [regex]::IsMatch($acceptBody, "_store\.(load|profileExists)"))) "accept path has no store read"
    Add-Result "scheduler.current-request-follow-up" (Test-ContainsAll -Path $schedulerPath -Tokens @("currentRequestedState", "requestedStateLocked(slot)", "slot._requestedState = currentRequestedState", "freshMaterializationRequired", "activityGeneration")) "cleanup current truth and dispatch generation"
    Add-Result "system.empty-production-registries" (Test-ContainsAll -Path $systemPath -Tokens @("new PhantomCandidateRegistry()", "candidateRegistry.seal()", "new PhantomStepHandlerRegistry()", "handlerRegistry.seal()", "new PhantomDecisionEngine", "createScheduler(new PhantomMaterializationServiceActivityPort(_materializationService), _decisionEngine)")) "zero-registration production wiring"
    Add-Result "system.stop-order" (Test-ContainsAll -Path $systemPath -Tokens @("_scheduler.beginStop();", "_decisionEngine.beginStop();", "_materializationService.shutdown();", "_scheduler.finishStop()", "_decisionEngine.finishStop()")) "bounded shutdown handoff"

    $forbiddenProduction = New-Object System.Collections.Generic.List[string]
    foreach ($file in Get-ChildItem -LiteralPath $decisionRoot -File -Filter "*.java")
    {
        $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        if ($content -cmatch "\b(Player|GameClient|ServerPacket|Runnable|Executor|ScheduledFuture|Thread)\b")
        {
            [void]$forbiddenProduction.Add($file.Name)
        }
    }
    Add-Result "decision.no-domain-runtime-owners" ($forbiddenProduction.Count -eq 0) $(if ($forbiddenProduction.Count -eq 0) { "none" } else { $forbiddenProduction -join "," })

    $coreSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionCoreSuite.java"
    $schedulerSuite = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomActivitySchedulerSuite.java"
    Add-Result "tests.core-at-least-22" (([regex]::Matches((Get-Content -LiteralPath $coreSuite -Raw -Encoding UTF8), 'registry\.add\(')).Count -ge 22) "focused core cases"
    Add-Result "tests.scheduler-current-races-and-integration" (Test-ContainsAll -Path $schedulerSuite -Tokens @("cleanup-retry-recomputes-warm-to-sleeping", "cleanup-retry-recomputes-warm-to-active", "manual-scheduler-drives-one-decision-slice", "blockNextRetry")) "two races plus scheduler sink"
    Add-Result "tests.performance-shape" (Test-ContainsAll -Path (Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomDecisionPerformanceSuite.java") -Tokens @("PROFILE_COUNT = 1000", "CANDIDATE_COUNT = 64", "CONSIDERATION_COUNT = 8", "DISPATCH_BUDGET = 32", "Future.class", "Thread.class", "Executor.class")) "required scale shape"
    Add-Result "build.goal008-routes" (Test-ContainsAll -Path (Join-Path $moduleRoot "build.xml") -Tokens @("phantom-decision-core-test", "phantom-decision-persistence-test", "phantom-decision-performance-smoke", "phantom-static-verify-008", "Run Goal 008 and all prior Phantom verification gates.")) "cumulative Ant routes"
    Add-Result "docs.goal007a-accepted" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reviews/007a-scheduler-transition-ownership-hardening-review.md") -Tokens @("Goal 007A: ACCEPT", "Revert: NOT_REQUIRED", "Goal 008: ALLOWED", "Goal 009: NOT_STARTED")) "accepted gate"
    Add-Result "docs.goal008-pending-review" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/phantoms/reports/008-goal-utility-plan-core.md") -Tokens @("PENDING_INDEPENDENT_REVIEW", "Goal 009: NOT_STARTED", "GOAL_UTILITY_PLAN_CORE_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW")) "manual gate preserved"
    Add-Result "docs.roadmap-progress-only" (Test-ContainsAll -Path (Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md") -Tokens @("357c047fdba4bc9ea3b4ee21bcedbd5ce6c64018", "Goal 007A:", "ACCEPT", "Goal 008:", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Goal 009:", "NOT_STARTED")) "progress updated"

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
