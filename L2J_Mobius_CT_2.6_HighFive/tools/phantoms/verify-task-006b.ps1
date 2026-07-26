[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/Shutdown.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProductionMaterializationSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-006b.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md") -or
        ($relative -ceq "docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md") -or
        $relative.StartsWith("docs/phantoms/tasks/006b-server-shutdown-handoff/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/006a-materialization-boundary-hardening.md") -or
        ($relative -ceq "docs/phantoms/reports/006b-server-shutdown-handoff.md") -or
        ($relative -ceq "docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md")
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
    Add-Result "repository.one-ordinary-goal006b-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "fix(phantoms): coordinate server shutdown handoff") $subject
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
    $schemaOrConfig = @($changed | Where-Object { $_ -match "(?i)dist/(db_installer|game/config)|migration|phantom_profiles\.sql" })
    Add-Result "scope.no-schema-or-config" ($schemaOrConfig.Count -eq 0) $(if ($schemaOrConfig.Count -eq 0) { "none" } else { $schemaOrConfig -join "," })

    foreach ($frozen in @(
        "dist/game/config/Custom/PhantomPlayers.ini",
        "java/org/l2jmobius/gameserver/GameServer.java",
        "java/org/l2jmobius/gameserver/model/World.java",
        "java/org/l2jmobius/gameserver/model/actor/Player.java",
        "java/org/l2jmobius/gameserver/network/GameClient.java",
        "java/org/l2jmobius/gameserver/network/Disconnection.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomRetainedIdentityRecovery.java",
        "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java",
        "tools/phantoms/verify-task-006a.ps1"))
    {
        $result = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix$frozen") -AllowFailure
        Add-Result "frozen.$frozen" ($result.ExitCode -eq 0) "unchanged"
    }

    $required = @(
        "java/org/l2jmobius/gameserver/Shutdown.java",
        "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java",
        "tools/phantoms/verify-task-006b.ps1",
        "docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md",
        "docs/phantoms/reports/006b-server-shutdown-handoff.md",
        "docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md")
    foreach ($relative in $required)
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }
    foreach ($name in @("ACCEPTANCE.md", "CODEX_LAUNCHER.txt", "CONTEXT.md", "PACKAGE_MANIFEST.json", "REVIEW_FINDINGS.md", "SHUTDOWN_HANDOFF.md", "TASK.md", "TEST_CASES.md"))
    {
        Add-Result "artifact.task-package.$name" (Test-Path -LiteralPath (Join-Path $moduleRoot "docs/phantoms/tasks/006b-server-shutdown-handoff/$name") -PathType Leaf) $name
    }

    $shutdownPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/Shutdown.java"
    $systemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $servicePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java"
    $shutdown = Get-Content -LiteralPath $shutdownPath -Raw -Encoding UTF8
    $system = Get-Content -LiteralPath $systemPath -Raw -Encoding UTF8
    $service = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8

    $shutdownCall = "PhantomSystem.shutdownIfStarted()"
    $firstShutdown = $shutdown.IndexOf($shutdownCall, [System.StringComparison]::Ordinal)
    $disconnect = $shutdown.IndexOf("disconnectAllCharacters();", $firstShutdown + $shutdownCall.Length, [System.StringComparison]::Ordinal)
    $secondShutdown = $shutdown.IndexOf($shutdownCall, $firstShutdown + $shutdownCall.Length, [System.StringComparison]::Ordinal)
    $threadPool = $shutdown.IndexOf("ThreadPool.shutdown();", $secondShutdown + $shutdownCall.Length, [System.StringComparison]::Ordinal)
    Add-Result "shutdown.actual-source-order" (($firstShutdown -ge 0) -and ($firstShutdown -lt $disconnect) -and ($disconnect -lt $secondShutdown) -and ($secondShutdown -lt $threadPool)) "first drain < disconnect < second drain < ThreadPool"
    Add-Result "shutdown.exactly-two-server-calls" (([regex]::Matches($shutdown, [regex]::Escape($shutdownCall))).Count -eq 2) "two source invocations"
    $loopStart = $shutdown.IndexOf("private void disconnectAllCharacters()", [System.StringComparison]::Ordinal)
    $loopEnd = $shutdown.IndexOf("private static class TimeCounter", $loopStart, [System.StringComparison]::Ordinal)
    $loop = if (($loopStart -ge 0) -and ($loopEnd -gt $loopStart)) { $shutdown.Substring($loopStart, $loopEnd - $loopStart) } else { "" }
    $guard = $loop.IndexOf("PhantomSystem.isMaterializationManaged(player)", [System.StringComparison]::Ordinal)
    $genericCleanup = $loop.IndexOf("Disconnection.of(player)", [System.StringComparison]::Ordinal)
    Add-Result "shutdown.generic-managed-guard" (($guard -ge 0) -and ($guard -lt $genericCleanup) -and $loop.Contains("continue;")) "guard precedes Disconnection"
    Add-Result "shutdown.no-direct-service-cleanup-in-loop" (-not [regex]::IsMatch($loop, "shutdownIfStarted|configuredMaterializationService|dematerialize|retryCleanup|\.shutdown\(")) "selection only"
    $betweenFinalAndPool = if (($secondShutdown -ge 0) -and ($threadPool -gt $secondShutdown)) { $shutdown.Substring($secondShutdown, $threadPool - $secondShutdown) } else { "" }
    Add-Result "shutdown.final-failure-severe" ($betweenFinalAndPool.Contains("LOGGER.severe") -and $betweenFinalAndPool.Contains("Shared ThreadPool is about to stop") -and $betweenFinalAndPool.Contains("retainedEntries")) "aggregate severe diagnostic"
    Add-Result "shutdown.no-legacy-success-on-failure" (-not $shutdown.Contains("Skeleton has been shut down")) "legacy success removed"

    Add-Result "classifier.strict-conjunction" (Test-ContainsAll -Path $systemPath -Tokens @(
        "public static synchronized boolean isMaterializationManaged(Player player)",
        "(player == null) || !player.hasHeadlessOutboundSession()",
        "getOwnerKind(player.getObjectId()) != OwnerKind.PHANTOM",
        "configuredMaterializationService()",
        "service.ownsCharacterObjectId(player.getObjectId())")) "headless + PHANTOM + configured exact service ownership"
    $ownerStart = $service.IndexOf("public boolean ownsCharacterObjectId(int objectId)", [System.StringComparison]::Ordinal)
    $ownerEnd = $service.IndexOf("public List<MaterializationSnapshot> list()", $ownerStart, [System.StringComparison]::Ordinal)
    $ownerBody = if (($ownerStart -ge 0) -and ($ownerEnd -gt $ownerStart)) { $service.Substring($ownerStart, $ownerEnd - $ownerStart) } else { "" }
    Add-Result "classifier.read-only-exact-character-map" ($ownerBody.Contains("_activeByCharacter.containsKey(objectId)") -and -not [regex]::IsMatch($ownerBody, "put|remove|clear|Player|Repository|World|Connection")) "read-only exact map query"
    Add-Result "configured.instance-retained-until-stopped" (Test-ContainsAll -Path $systemPath -Tokens @(
        "if (configured.snapshot().state() == State.STOPPED)",
        "_configuredInstance = null;")) "clear only after terminal STOPPED"

    $snapshotStart = $system.IndexOf("public static synchronized ConfiguredShutdownSnapshot configuredShutdownSnapshot()", [System.StringComparison]::Ordinal)
    $snapshotEnd = $system.IndexOf("static synchronized PhantomMaterializationService configuredMaterializationService()", $snapshotStart, [System.StringComparison]::Ordinal)
    $snapshotBody = if (($snapshotStart -ge 0) -and ($snapshotEnd -gt $snapshotStart)) { $system.Substring($snapshotStart, $snapshotEnd - $snapshotStart) } else { "" }
    Add-Result "snapshot.bounded-no-identities-or-db" ((Test-ContainsAll -Path $systemPath -Tokens @(
        "record ConfiguredShutdownSnapshot(boolean configured, State systemState, ServiceState serviceState, int retainedEntries)",
        "ConfiguredShutdownSnapshot.notConfigured()",
        "service.shutdownSnapshot()")) -and -not [regex]::IsMatch($snapshotBody, "profileId|characterObjectId|Repository|Connection|PreparedStatement|World|\.list\(")) "aggregate state/count only"
    Add-Result "snapshot.service-bounded" (Test-ContainsAll -Path $servicePath -Tokens @(
        "public ShutdownSnapshot shutdownSnapshot()",
        "new ShutdownSnapshot(_state, _activeByProfile.size())",
        "record ShutdownSnapshot(ServiceState state, int retainedEntries)")) "no materialization list"
    Add-Result "test-seam.package-private" (Test-ContainsAll -Path $systemPath -Tokens @(
        "static synchronized void configureForTesting(PhantomMaterializationService materializationService)",
        "serviceSnapshot.state() != ServiceState.RUNNING",
        "_configuredInstance = configured;")) "controlled configured-system seam"
    Add-Result "production.no-new-executor-or-thread" (-not [regex]::IsMatch("$shutdown`n$system`n$service", "new\s+Thread\s*\(|Executors\.|ExecutorService|ScheduledExecutorService")) "existing shared ThreadPool only"
    Add-Result "production.one-transient-service-future" (([regex]::Matches($service, "ScheduledFuture<\?>")).Count -eq 1) "Goal 006A DrainAttempt only"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java"
    $suite = if (Test-Path -LiteralPath $suitePath) { Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8 } else { "" }
    Add-Result "tests.focused-four-cases" (([regex]::Matches($suite, 'registry\.add\(')).Count -eq 4) "four bounded cases"
    Add-Result "tests.classifier-matrix" (Test-ContainsAll -Path $suitePath -Tokens @(
        "Active configured Phantom Player was not classified as managed.",
        "Ordinary loaded Player was classified as managed.",
        "Detached offline real Player was classified as managed.",
        "Unowned headless Player was classified as managed.",
        "PHANTOM lease without configured service ownership was classified as managed.",
        "Cleaned Phantom Player remained classified as managed.")) "true/false/cleanup matrix"
    Add-Result "tests.two-phase-policy" (Test-ContainsAll -Path $suitePath -Tokens @(
        "first", "disconnect", "second", "thread-pool",
        "Persistent failure was reported as successful.",
        "more than two server-level Phantom shutdown calls")) "ordering, maximum two and failure status"
    Add-Result "tests.in-flight-reuse" (Test-ContainsAll -Path $suitePath -Tokens @(
        "FailurePoint.BEFORE_STORE_OPERATION",
        "Blocked first server shutdown unexpectedly completed.",
        "Generic disconnect selected the in-flight managed actor.",
        "Second server shutdown did not observe/reuse the released in-flight drain.",
        "Second server shutdown duplicated in-flight cleanup.")) "blocked first, skip, reuse second"
    Add-Result "tests.persistent-failure" (Test-ContainsAll -Path $suitePath -Tokens @(
        "First persistent server shutdown reported success.",
        "Second persistent server shutdown reported success.",
        "Persistent failure cleared the configured instance.",
        "Persistent failure released the service entry.",
        "Explicit teardown cleanup did not stop")) "retained configured state and explicit teardown"
    Add-Result "tests.no-full-server-or-exit" (-not [regex]::IsMatch($suite, "new\s+GameServer|System\.exit|Shutdown\.getInstance\(\)\.run")) "no full manager shutdown"

    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    $buildPath = Join-Path $moduleRoot "build.xml"
    Add-Result "build.focused-launcher-target" ((Test-ContainsAll -Path $launcherPath -Tokens @(
        'case "server-shutdown-handoff" -> new PhantomServerShutdownHandoffSuite()')) -and
        (Test-ContainsAll -Path $buildPath -Tokens @(
        'name="phantom-server-shutdown-handoff-test"', '<arg value="server-shutdown-handoff" />', 'fork="true"'))) "launcher mode and forked Ant target"
    Add-Result "build.cumulative-goal006b" (Test-ContainsAll -Path $buildPath -Tokens @(
        'name="phantom-static-verify-006b"', "verify-task-006b.ps1",
        'name="phantom-static-verify-006a" depends="phantom-static-verify-006b"',
        "Run Goal 006B and all prior Phantom verification gates.")) "Goal 006B cumulative verifier"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/006a-materialization-boundary-hardening-review.md"
    $reportPath = Join-Path $moduleRoot "docs/phantoms/reports/006b-server-shutdown-handoff.md"
    $contractPath = Join-Path $moduleRoot "docs/phantoms/architecture/SERVER_SHUTDOWN_HANDOFF.md"
    $lifecyclePath = Join-Path $moduleRoot "docs/phantoms/architecture/MATERIALIZATION_LIFECYCLE_CONTRACT.md"
    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    Add-Result "docs.goal006a-review" (Test-ContainsAll -Path $reviewPath -Tokens @(
        "Goal 006A local boundary hardening: ACCEPT",
        "Goal 006 overall: FIX_REQUIRED pending 006B",
        "Goal 006B: REQUIRED",
        "Goal 007: BLOCKED")) "review gate preserved"
    Add-Result "docs.goal006b-report" (Test-ContainsAll -Path $reportPath -Tokens @(
        "SERVER_SHUTDOWN_HANDOFF_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "c2f5599ef59cabb1eeff1f3d467f57219d5a1f5f",
        "shutdown-handoff", "Production materialization:",
        "Production DB", "Goal 007", "PENDING_INDEPENDENT_REVIEW")) "closure report and manual gate"
    Add-Result "docs.shutdown-contract" (Test-ContainsAll -Path $contractPath -Tokens @(
        "isMaterializationManaged", "PHANTOM", "headless",
        "disconnectAllCharacters", "ThreadPool.shutdown",
        "ConfiguredShutdownSnapshot", "SEVERE")) "server handoff contract"
    Add-Result "docs.lifecycle-contract-handoff" (Test-ContainsAll -Path $lifecyclePath -Tokens @(
        "Server shutdown handoff", "managed", "ThreadPool.shutdown", "Goal 006B")) "lifecycle contract extended only for handoff"
    Add-Result "roadmap.progress-only-statuses" (Test-ContainsAll -Path $roadmapPath -Tokens @(
        "Goal 006A:", "ACCEPT", "Goal 006B:",
        "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Goal 006 overall:", "FIX_REQUIRED pending 006B",
        "Goal 007:", "NOT_STARTED / BLOCKED")) "progress facts only"

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
