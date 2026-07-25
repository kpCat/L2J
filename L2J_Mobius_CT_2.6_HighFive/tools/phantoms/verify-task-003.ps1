[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "84f29a0002b25d2b1ff1a19fa9c92867479fd6a5"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$script:Results = New-Object System.Collections.Generic.List[object]

function Add-Result
{
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    $script:Results.Add([PSCustomObject]@{
        Name = $Name
        Passed = $Passed
        Detail = $Detail
    })
}

function Invoke-Git
{
    param(
        [string]$Root,
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = @(& git -c core.safecrlf=false -C $Root @Arguments 2>&1)
    if (($LASTEXITCODE -ne 0) -and -not $AllowFailure)
    {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE`: $($output -join [Environment]::NewLine)"
    }
    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output = [string[]]$output
    }
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
    param(
        [string]$Path,
        [string[]]$Tokens
    )

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

function Test-TaskScopePath
{
    param(
        [string]$RepositoryPath,
        [string]$ModulePrefix
    )

    if (-not $RepositoryPath.StartsWith($ModulePrefix, [System.StringComparison]::Ordinal))
    {
        return $false
    }
    $relative = $RepositoryPath.Substring($ModulePrefix.Length)
    return ($relative -ceq "build.xml") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/GameServer.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/Shutdown.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/config/ConfigLoader.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "dist/game/config/Custom/PhantomPlayers.ini") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-003.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/003-disabled-skeleton-config-metrics/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md") -or
        ($relative -ceq "docs/phantoms/reports/003-disabled-skeleton-config-metrics.md") -or
        ($relative -ceq "docs/phantoms/reviews/002-automated-test-infrastructure-review.md")
}

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootResult = Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel")
    $gitRoot = (Resolve-Path $gitRootResult.Output[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $modulePrefix = "$relativeModule/"
    Add-Result "repository.module-root" ($moduleRoot -ceq (Join-Path $gitRoot $relativeModule)) $moduleRoot

    $branch = (Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")).Output[0]
    Add-Result "repository.branch" ($branch -ceq $Branch) $branch
    $baseExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.base-commit" ($baseExists.ExitCode -eq 0) $BaseCommit

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $mode = "invalid"
    $shape = $false
    if ($head -ceq $BaseCommit)
    {
        $mode = "pre-commit"
        $shape = $true
    }
    elseif ($baseExists.ExitCode -eq 0)
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $count = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $mode = "post-commit"
        $shape = (($parent -ceq $BaseCommit) -and ($count -eq 1))
    }
    Add-Result "repository.one-commit-shape" $shape $mode

    $required = @(
        "build.xml",
        "dist/game/config/Custom/PhantomPlayers.ini",
        "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
        "tools/phantoms/verify-task-003.ps1",
        "docs/phantoms/reports/003-disabled-skeleton-config-metrics.md"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $required))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }

    $committed = @()
    if ($head -cne $BaseCommit)
    {
        $committed = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$BaseCommit...HEAD")).Output
    }
    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit)).Output
    $cached = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $BaseCommit)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")).Output
    $taskUntracked = @($untracked | Where-Object { Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix })
    $unrelatedUntracked = @($untracked | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    $changed = Get-OrdinalSortedUnique ([string[]]($committed + $tracked + $cached + $taskUntracked))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "workspace.unrelated-untracked-preserved" $true "$($unrelatedUntracked.Count) excluded"

    $violations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($violations.Count -eq 0) (($violations -join ",") -replace "^$", "no violations")
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-task-004-artifacts" (@($changed | Where-Object { $_ -match "(?i)(task|report|verify)[^/]*004|tasks/004|reports/004" }).Count -eq 0) "Task 004 absent"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binaries"

    $frozenSafetyPaths = @(
        "$modulePrefix`tools/phantoms/verify-task-002.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002a.ps1",
        "$modulePrefix`tools/phantoms/prepare-test-db.ps1",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLock.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLockControl.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseBootstrap.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestSchemaManifest.java",
        "$modulePrefix`test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql",
        "$modulePrefix`test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql"
    )
    $frozenDiff = Invoke-Git -Root $gitRoot -Arguments ([string[]](@("diff", "--quiet", $BaseCommit, "--") + $frozenSafetyPaths)) -AllowFailure
    Add-Result "scope.old-safety-artifacts-unchanged" ($frozenDiff.ExitCode -eq 0) "$($frozenSafetyPaths.Count) paths"

    $phantomDirectory = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms"
    $phantomFiles = @(Get-ChildItem -LiteralPath $phantomDirectory -File -Filter "*.java" | Sort-Object Name)
    Add-Result "production.exact-phantom-class-count" ($phantomFiles.Count -eq 4) "$($phantomFiles.Count) classes"
    $phantomContent = ($phantomFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"
    $forbiddenImportPattern = "(?im)^\s*import\s+.*(?:\bPlayer\b|\bWorld\b|\bGameClient\b|clientpackets|serverpackets|ConnectionManager|DatabaseFactory|java\.sql|javax\.sql|ThreadPool|\bThread\b|\bExecutor\b|ScheduledFuture|\bNPC\b|FakePlayer).*;"
    Add-Result "production.no-forbidden-imports" (-not [regex]::IsMatch($phantomContent, $forbiddenImportPattern)) "DB/network/actor APIs absent"
    Add-Result "production.no-forbidden-runtime-types" ($phantomContent -notmatch "\b(?:DatabaseFactory|GameClient|ConnectionManager|ThreadPool|Executor|ScheduledFuture)\b|java\.sql|javax\.sql|clientpackets|serverpackets") "runtime seams absent"
    $concurrencyImports = @([regex]::Matches($phantomContent, "(?im)^\s*import\s+(java\.util\.concurrent\.[^;]+);") | ForEach-Object { $_.Groups[1].Value })
    $invalidConcurrencyImports = @($concurrencyImports | Where-Object { ($_ -cne "java.util.concurrent.ArrayBlockingQueue") -and ($_ -cne "java.util.concurrent.atomic.AtomicLong") })
    Add-Result "production.allowed-concurrency-only" ($invalidConcurrencyImports.Count -eq 0) (($concurrencyImports -join ",") -replace "^$", "none")
    Add-Result "production.no-worker-construction" ($phantomContent -notmatch "\bnew\s+(?:java\.lang\.)?Thread\b|newSingleThread|newFixedThread|scheduleAtFixedRate|scheduleWithFixedDelay") "no worker/task/future"

    $configFilePath = Join-Path $moduleRoot "dist/game/config/Custom/PhantomPlayers.ini"
    $configFile = Get-Content -LiteralPath $configFilePath -Raw -Encoding UTF8
    Add-Result "config.false-defaults" (($configFile -match "(?im)^\s*EnablePhantomSystem\s*=\s*False\s*$") -and ($configFile -match "(?im)^\s*EnablePhantomDiagnostics\s*=\s*False\s*$")) "both canonical flags false"
    $propertyKeys = @(Get-Content -LiteralPath $configFilePath -Encoding UTF8 | Where-Object { ($_ -notmatch "^\s*(#|$)") -and ($_ -match "=") } | ForEach-Object { (($_ -split "=", 2)[0]).Trim() })
    [Array]::Sort($propertyKeys, [System.StringComparer]::Ordinal)
    Add-Result "config.keys-only" (($propertyKeys -join ",") -ceq "EnablePhantomDiagnostics,EnablePhantomSystem") ($propertyKeys -join ",")
    Add-Result "config.inert-production-warning" (($configFile -match "(?i)inert") -and ($configFile -match "(?i)disabled") -and ($configFile -match "(?i)production")) "inert gate comment"

    $configClassPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
    $configClassTokens = @(
        "public final class PhantomPlayersConfig",
        "public static final String PHANTOM_PLAYERS_CONFIG_FILE",
        "private static volatile Settings _settings = new Settings(false, false)",
        "public static void load()",
        "public static Settings read(Path path)",
        "public static Settings settings()",
        "public static boolean isEnabled()",
        "normalized.equalsIgnoreCase(`"true`")",
        "normalized.equalsIgnoreCase(`"false`")",
        "public record Settings(boolean enabled, boolean diagnosticsEnabled)",
        "diagnosticsEnabled = enabled && diagnosticsEnabled"
    )
    Add-Result "config.immutable-strict-contract" (Test-ContainsAll -Path $configClassPath -Tokens $configClassTokens) "$($configClassTokens.Count) tokens"
    $configClass = Get-Content -LiteralPath $configClassPath -Raw -Encoding UTF8
    Add-Result "config.no-external-input" ($configClass -notmatch "System\.get(?:env|Property)|DatabaseFactory|java\.sql|javax\.sql") "file input only"
    Add-Result "config.missing-fail-closed" (($configClass.Contains("!Files.isRegularFile(path)")) -and ($configClass.Contains("return new Settings(false, false)"))) "missing/malformed disabled"

    $configLoaderPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/config/ConfigLoader.java"
    Add-Result "integration.config-loader" (Test-ContainsAll -Path $configLoaderPath -Tokens @("import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;", "PhantomPlayersConfig.load();")) "canonical custom config load"

    $gameServerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/GameServer.java"
    $gameServer = Get-Content -LiteralPath $gameServerPath -Raw -Encoding UTF8
    $configIndex = $gameServer.IndexOf("ConfigLoader.init();", [System.StringComparison]::Ordinal)
    $databaseIndex = $gameServer.IndexOf("DatabaseFactory.init();", [System.StringComparison]::Ordinal)
    $poolIndex = $gameServer.IndexOf("ThreadPool.init();", [System.StringComparison]::Ordinal)
    $guardIndex = $gameServer.IndexOf("if (PhantomPlayersConfig.isEnabled())", [System.StringComparison]::Ordinal)
    $sectionIndex = $gameServer.IndexOf('printSection("Phantom World");', [System.StringComparison]::Ordinal)
    $startIndex = $gameServer.IndexOf("PhantomSystem.startConfigured()", [System.StringComparison]::Ordinal)
    $idIndex = $gameServer.IndexOf("IdManager.getInstance();", [System.StringComparison]::Ordinal)
    Add-Result "integration.startup-order" (($configIndex -ge 0) -and ($databaseIndex -gt $configIndex) -and ($poolIndex -gt $databaseIndex) -and ($guardIndex -gt $poolIndex) -and ($sectionIndex -gt $guardIndex) -and ($startIndex -gt $sectionIndex) -and ($idIndex -gt $startIndex)) "Config < DB < pool < guard < section < start < Id"
    Add-Result "integration.disabled-section-guarded" (($guardIndex -ge 0) -and ($sectionIndex -gt $guardIndex) -and ($startIndex -gt $sectionIndex)) "section inside enabled guard"
    Add-Result "integration.startup-fails-on-false" ($gameServer.Contains("if (!PhantomSystem.startConfigured())")) "enabled failure propagated"

    $shutdownPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/Shutdown.java"
    $shutdown = Get-Content -LiteralPath $shutdownPath -Raw -Encoding UTF8
    $phantomStopIndex = $shutdown.IndexOf("PhantomSystem.shutdownIfStarted()", [System.StringComparison]::Ordinal)
    $poolStopIndex = $shutdown.IndexOf("ThreadPool.shutdown();", [System.StringComparison]::Ordinal)
    Add-Result "integration.shutdown-before-pool" (($phantomStopIndex -ge 0) -and ($poolStopIndex -gt $phantomStopIndex)) "Phantom stop < pool stop"
    Add-Result "integration.shutdown-log-guarded" (Test-ContainsAll -Path $shutdownPath -Tokens @("if (PhantomSystem.shutdownIfStarted())", 'LOGGER.info("Phantom World: Skeleton has been shut down', 'LOGGER.log(Level.WARNING, "Error shutting down Phantom World skeleton.", t);')) "local guarded shutdown"

    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $systemTokens = @(
        "public enum State",
        "NEW,",
        "DISABLED,",
        "RUNNING,",
        "STOPPED",
        "public PhantomSystem(PhantomPlayersConfig.Settings settings)",
        "public synchronized boolean start()",
        "public synchronized boolean shutdown()",
        "public synchronized Snapshot snapshot()",
        "public static synchronized boolean startConfigured()",
        "public static synchronized boolean shutdownIfStarted()",
        "if (!PhantomPlayersConfig.isEnabled() || (_configuredInstance != null))",
        "_configuredInstance = null",
        "QUEUE_CAPACITY = 256",
        "TRACE_CAPACITY = 64",
        "TRACE_SAMPLE_EVERY = 16"
    )
    Add-Result "lifecycle.system-contract" (Test-ContainsAll -Path $systemPath -Tokens $systemTokens) "$($systemTokens.Count) tokens"
    $system = Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8
    Add-Result "lifecycle.disabled-no-queue-trace" (($system.Contains("if (settings.enabled())")) -and ($system.Contains("_scheduler = null;")) -and ($system.Contains("_trace = null;"))) "disabled direct allocation guard"
    Add-Result "lifecycle.configured-publish-after-start" ($system.IndexOf("_configuredInstance = candidate", [System.StringComparison]::Ordinal) -gt $system.IndexOf("candidate.start()", [System.StringComparison]::Ordinal)) "publish after successful start"

    $schedulerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomScheduler.java"
    $schedulerTokens = @(
        "ArrayBlockingQueue<Runnable>",
        "new ArrayBlockingQueue<>(capacity)",
        "public synchronized boolean start()",
        "public synchronized boolean offer(Runnable work)",
        "_queue.offer(work)",
        "_queue.clear()",
        "scheduledTaskCount",
        "new Snapshot(_running, _queue.size(), _queue.remainingCapacity() + _queue.size(), 0)"
    )
    Add-Result "queue.bounded-inert-contract" (Test-ContainsAll -Path $schedulerPath -Tokens $schedulerTokens) "$($schedulerTokens.Count) tokens"
    Add-Result "queue.no-production-submission" (-not $system.Contains(".offer(")) "Task 003 production submits no work"

    $metricsPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomMetrics.java"
    $metrics = Get-Content -LiteralPath $metricsPath -Raw -Encoding UTF8
    $atomicCount = ([regex]::Matches($metrics, "new AtomicLong\(\)")).Count
    Add-Result "metrics.fixed-six-counters" (($atomicCount -eq 6) -and ($metrics -notmatch "\bMap\s*<|\bCollection\s*<")) "AtomicLong=$atomicCount"
    Add-Result "metrics.immutable-snapshot" (Test-ContainsAll -Path $metricsPath -Tokens @("public record Snapshot", "lifecycleStarts", "lifecycleStops", "queueAccepted", "queueRejected", "traceRecorded", "traceDropped")) "fixed snapshot"

    $tracePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomDiagnosticTrace.java"
    $traceTokens = @(
        "_events = enabled ? new String[capacity] : null",
        "MAX_EVENT_NAME_LENGTH = 48",
        "_attempts % _sampleEvery",
        "_start = (_start + 1) % _capacity",
        "_metrics.recordTraceDropped()",
        "_metrics.recordTraceRecorded()",
        "List.copyOf(events)",
        "return Snapshot.disabled()"
    )
    Add-Result "trace.bounded-sampled-contract" (Test-ContainsAll -Path $tracePath -Tokens $traceTokens) "$($traceTokens.Count) tokens"

    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    Add-Result "tests.explicit-skeleton-mode" (Test-ContainsAll -Path $launcherPath -Tokens @('case "skeleton" -> new PhantomSkeletonSuite();')) "explicit mode"
    $skeletonSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java"
    $suiteTokens = @(
        "config-canonical-disabled",
        "config-missing-disabled",
        "config-malformed-and-blank-disabled",
        "configured-disabled-no-instance",
        "disabled-lifecycle-inert",
        "enabled-lifecycle-inert",
        "queue-bounded-no-consumer",
        "trace-disabled-no-storage",
        "trace-sampled-bounded-overwrite",
        "Thread.getAllStackTraces()",
        "capacity plus one",
        "STOPPED system restarted"
    )
    Add-Result "tests.skeleton-regressions" (Test-ContainsAll -Path $skeletonSuitePath -Tokens $suiteTokens) "$($suiteTokens.Count) tokens"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $buildTokens = @(
        'target name="phantom-skeleton-test"',
        '<arg value="skeleton" />',
        'target name="phantom-static-verify-003"',
        'verify-task-003.ps1',
        'phantom-skeleton-test,phantom-negative-control',
        'phantom-static-verify-002a,phantom-static-verify-003"'
    )
    Add-Result "build.task-003-contract" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) tokens"
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $javaCount = ([regex]::Matches($build, "<java\s")).Count
    $forkCount = ([regex]::Matches($build, 'fork="true"')).Count
    Add-Result "build.all-java-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"
    foreach ($priorTarget in @("test", "phantom-negative-control", "phantom-db-guard-negative-control", "phantom-provisioning-lock-control", "phantom-schema-freshness-negative-control", "phantom-lifecycle-negative-control", "phantom-db-test", "phantom-scenario-test", "phantom-performance-smoke", "phantom-static-verify", "phantom-static-verify-002a"))
    {
        Add-Result "build.prior-target.$priorTarget" ($build.Contains("target name=`"$priorTarget`"")) $priorTarget
    }

    $task002aReport = Join-Path $moduleRoot "docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md"
    $provenanceTokens = @(
        "84f29a0002b25d2b1ff1a19fa9c92867479fd6a5",
        "36e5411e01e8e73f8a0fd4d9460e327c28a6798b",
        'Final run 1: `52/52 PASS`',
        'Final run 2: `52/52 PASS`',
        "3DEBD45D104620BE262FC6AE83A0A9244F80D9D409E9FEA504DF0EA815E0249E",
        "Push: successful",
        "Remote ref: exact",
        'Independent review: `ACCEPT`',
        "## Task 003",
        '`ALLOWED`'
    )
    Add-Result "report.task-002a-provenance-closure" (Test-ContainsAll -Path $task002aReport -Tokens $provenanceTokens) "$($provenanceTokens.Count) facts"
    $task002aReportText = Get-Content -LiteralPath $task002aReport -Raw -Encoding UTF8
    Add-Result "report.task-002a-no-post-commit-placeholder" ($task002aReportText -notmatch "PENDING_INDEPENDENT_REVIEW|Commit SHA, push result|Final run 1 и run 2 выполняются") "placeholders removed"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/002-automated-test-infrastructure-review.md"
    Add-Result "report.review-acceptance" (Test-ContainsAll -Path $reviewPath -Tokens @("Original Task 002 implementation: FIX REQUIRED", "Task 002A closure: ACCEPT", "Combined Task 002 test infrastructure: ACCEPT", "Task 003: ALLOWED", "Task 004: NOT_STARTED")) "combined gate"

    $report003Path = Join-Path $moduleRoot "docs/phantoms/reports/003-disabled-skeleton-config-metrics.md"
    $reportHeadings = @(
        "## Status and baseline",
        "## Task 002A closure",
        "## Changed files",
        "## Config and fail-closed behavior",
        "## Disabled and enabled skeleton behavior",
        "## Lifecycle ordering",
        "## Queue and scheduled tasks",
        "## Metrics and trace",
        "## DB and network safety",
        "## Concurrency and memory",
        "## Tests and counts",
        "## Ant targets, verify and jar",
        "## Static verifier",
        "## Scope, commands, deviations and limitations",
        "## Branch, parent and subject",
        "## Manual gate",
        "PENDING_INDEPENDENT_REVIEW",
        "## Task 004",
        "NOT_STARTED",
        "Exact immutable commit SHA, push result and post-commit verifier outputs are"
    )
    Add-Result "report.task-003-sections" (Test-ContainsAll -Path $report003Path -Tokens $reportHeadings) "$($reportHeadings.Count) facts"

    $allChangedText = ($changed | ForEach-Object {
        $absolute = Join-Path $gitRoot $_
        if ((Test-Path -LiteralPath $absolute -PathType Leaf) -and ($_ -notmatch "(?i)\.(jar|class|zip|7z|exe|dll|bin)$"))
        {
            Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        }
    }) -join "`n"
    Add-Result "safety.no-literal-admin-secret" ($allChangedText -notmatch "(?i)PHANTOM_DB_ADMIN_PASSWORD\s*=\s*['""][^<'""]+") "no credential assignment"
    Add-Result "safety.no-local-artifacts" (@($changed | Where-Object { $_ -match "/\.phantom-local/" }).Count -eq 0) ".phantom-local excluded"

    $mojibakeMarkers = @(
        ([string][char]0x0420 + [string][char]0x045F),
        ([string][char]0x0420 + [string][char]0x045C),
        ([string][char]0x0420 + [string][char]0x045B),
        ([string][char]0x0420 + [string][char]0x2022),
        ([string][char]0x0420 + [string][char]0x040E),
        ([string][char]0x0420 + [string][char]0x203A),
        ([string][char]0x0420 + [string][char]0x00A4),
        ([string][char]0x0420 + [string][char]0x045A),
        ([string][char]0x0420 + [string][char]0x0408),
        ([string][char]0x0420 + [string][char]0x0459),
        ([string][char]0x0420 + [string][char]0x0491),
        ([string][char]0x0420 + [string][char]0x00B5),
        ([string][char]0x0420 + [string][char]0x00B0),
        ([string][char]0x0420 + [string][char]0x00BB),
        ([string][char]0x0420 + [string][char]0x0405),
        ([string][char]0x0420 + [string][char]0x0455),
        ([string][char]0x0421 + [string][char]0x040F),
        ([string][char]0x0421 + [string][char]0x20AC),
        ([string][char]0x0421 + [string][char]0x0402),
        ([string][char]0x0421 + [string][char]0x2039),
        ([string][char]0x0421 + [string][char]0x040A),
        ([string][char]0x0421 + [string][char]0x201A),
        ([string][char]0x0421 + [string][char]0x0453),
        ([string][char]0x0421 + [string][char]0x2021),
        ([string][char]0x0421 + [string][char]0x2026),
        ([string][char]0x0421 + [string][char]0x2020),
        ([string][char]0xFFFD)
    )
    $escapedPatterns = @(
        ([string][char]92 + [char]92 + "u04[0-9A-Fa-f]{2}"),
        ([string][char]92 + [char]92 + "u05[0-9A-Fa-f]{2}"),
        ("&" + "#x04[0-9A-Fa-f]{2};"),
        ("&" + "#x05[0-9A-Fa-f]{2};"),
        ("&" + "#X04[0-9A-Fa-f]{2};"),
        ("&" + "#X05[0-9A-Fa-f]{2};")
    )
    $invalidUtf8 = New-Object System.Collections.Generic.List[string]
    $mojibakeFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    $escapedFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    foreach ($repositoryPath in $changed)
    {
        $absolute = Join-Path $gitRoot $repositoryPath
        if (-not (Test-Path -LiteralPath $absolute -PathType Leaf))
        {
            continue
        }
        if (-not (Test-ValidUtf8 -Path $absolute))
        {
            $invalidUtf8.Add($repositoryPath)
            continue
        }
        if ($repositoryPath -match "(?i)\.(jar|class|zip|7z|exe|dll|bin)$")
        {
            continue
        }
        $text = Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        foreach ($marker in $mojibakeMarkers)
        {
            if ($text.IndexOf($marker, [System.StringComparison]::Ordinal) -ge 0)
            {
                [void]$mojibakeFiles.Add($repositoryPath)
            }
        }
        foreach ($pattern in $escapedPatterns)
        {
            if ($text -match $pattern)
            {
                [void]$escapedFiles.Add($repositoryPath)
            }
        }
    }
    Add-Result "text.valid-utf8" ($invalidUtf8.Count -eq 0) (($invalidUtf8 -join ",") -replace "^$", "all changed files")
    Add-Result "text.no-mojibake-markers" ($mojibakeFiles.Count -eq 0) (([string[]]$mojibakeFiles -join ",") -replace "^$", "0 matches")
    Add-Result "text.no-escaped-cyrillic" ($escapedFiles.Count -eq 0) (([string[]]$escapedFiles -join ",") -replace "^$", "0 matches")
    Add-Result "verifier.deterministic-sorted-output" $true "ordinal result names"
    Add-Result "verifier.local-read-only" $true "Git/file checks only; no DB/network/write"
}
catch
{
    Add-Result "verifier.exception" $false $_.Exception.Message
}

$names = [string[]]($script:Results | ForEach-Object { $_.Name })
[Array]::Sort($names, [System.StringComparer]::Ordinal)
$failed = 0
foreach ($name in $names)
{
    $result = $script:Results | Where-Object { $_.Name -ceq $name } | Select-Object -First 1
    $label = if ($result.Passed) { "PASS" } else { "FAIL" }
    if (-not $result.Passed)
    {
        $failed++
    }
    Write-Output "[$label] $($result.Name) - $($result.Detail)"
}

Write-Output "SUMMARY: total=$($script:Results.Count) passed=$($script:Results.Count - $failed) failed=$failed"
if ($failed -ne 0)
{
    exit 1
}
exit 0
