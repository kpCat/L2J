[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "f5b66c4edf1ddf18e044ef8c692d70ecea616485"
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
        ($relative -ceq "dist/db_installer/sql/game/phantom_profiles.sql") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/profile/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java") -or
        ($relative -ceq "tools/phantoms/verify-task-005.ps1") -or
        ($relative -ceq "docs/PHANTOM_BOTS_ROADMAP.md") -or
        ($relative -ceq "docs/phantoms/architecture/PROFILE_PERSISTENCE_CONTRACT.md") -or
        $relative.StartsWith("docs/phantoms/tasks/005-core-profile-persistence-envelope/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/004b-retained-identity-ownership-fix.md") -or
        ($relative -ceq "docs/phantoms/reports/005-core-profile-persistence-envelope.md") -or
        ($relative -ceq "docs/phantoms/reviews/004b-retained-identity-ownership-fix-review.md") -or
        ($relative -ceq "docs/phantoms/adr/0001-headless-player-integration-seam.md")
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
    Add-Result "repository.one-ordinary-task-005-child" $ordinaryShape "$head|$shapeMode"
    if ($shapeMode -ceq "post-commit")
    {
        $subject = (Invoke-Git -Root $gitRoot -Arguments @("show", "-s", "--format=%s", "HEAD")).Output[0]
        Add-Result "repository.commit-subject" ($subject -ceq "feat(phantoms): add profile persistence envelope") $subject
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
    Add-Result "scope.goal-006-not-started" (@($changed | Where-Object { $_ -match "(?i)(task|report|verify)[^/]*006|tasks/006|reports/006" }).Count -eq 0) "Goal 006 artifacts absent"

    $frozenPaths = @(
        "${modulePrefix}java/org/l2jmobius/gameserver/model/actor/Player.java",
        "${modulePrefix}java/org/l2jmobius/gameserver/network/GameClient.java",
        "${modulePrefix}java/org/l2jmobius/gameserver/network/Disconnection.java",
        "${modulePrefix}java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java",
        "${modulePrefix}java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java",
        "${modulePrefix}java/org/l2jmobius/gameserver/phantoms/player",
        "${modulePrefix}java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java",
        "${modulePrefix}tools/phantoms/verify-task-004b.ps1"
    )
    foreach ($frozenPath in $frozenPaths)
    {
        $frozen = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", $frozenPath) -AllowFailure
        Add-Result "frozen.$($frozenPath.Substring($modulePrefix.Length))" ($frozen.ExitCode -eq 0) "unchanged from accepted base"
    }
    $configFrozen = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "${modulePrefix}dist/game/config") -AllowFailure
    Add-Result "frozen.production-config" ($configFrozen.ExitCode -eq 0) "dist/game/config unchanged"

    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    $roadmapSha = if (Test-Path -LiteralPath $roadmapPath -PathType Leaf) { (Get-FileHash -Algorithm SHA256 -LiteralPath $roadmapPath).Hash } else { "missing" }
    Add-Result "roadmap.approved-regions-exact-sha256" ($roadmapSha -ceq "22460C190A496FD8FCEF375F6E232390725AF78D41AA79AB2B42BA505BED38E9") $roadmapSha
    Add-Result "roadmap.progress-and-dependencies" (Test-ContainsAll -Path $roadmapPath -Tokens @("Goal 005", "PENDING_INDEPENDENT_REVIEW", "Goal 006", "NOT_STARTED", "Task 004B", "Accepted")) "Task 004B/Goal 005/Goal 006 state recorded"

    $required = @(
        "dist/db_installer/sql/game/phantom_profiles.sql",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfile.java",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileComponent.java",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfilePersistenceException.java",
        "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java",
        "tools/phantoms/verify-task-005.ps1",
        "docs/phantoms/architecture/PROFILE_PERSISTENCE_CONTRACT.md",
        "docs/phantoms/reports/005-core-profile-persistence-envelope.md",
        "docs/phantoms/reviews/004b-retained-identity-ownership-fix-review.md"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $required))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }
    $taskPackageFiles = @("ACCEPTANCE.md", "ARCHITECTURE.md", "CODEX_LAUNCHER.txt", "CONTEXT.md", "PACKAGE_MANIFEST.json", "SCHEMA.md", "TASK.md", "TEST_CASES.md")
    foreach ($name in $taskPackageFiles)
    {
        Add-Result "artifact.task-package.$name" (Test-Path -LiteralPath (Join-Path $moduleRoot "docs/phantoms/tasks/005-core-profile-persistence-envelope/$name") -PathType Leaf) $name
    }

    $sqlPath = Join-Path $moduleRoot "dist/db_installer/sql/game/phantom_profiles.sql"
    $sql = if (Test-Path -LiteralPath $sqlPath -PathType Leaf) { Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8 } else { "" }
    $createCount = [regex]::Matches($sql, "(?im)^\s*CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS").Count
    $statementCount = [regex]::Matches($sql, ";").Count
    Add-Result "schema.exactly-two-idempotent-statements" (($createCount -eq 2) -and ($statementCount -eq 2)) "create=$createCount; statements=$statementCount"
    Add-Result "schema.no-destructive-or-dml" (-not [regex]::IsMatch($sql, "(?im)^\s*(DROP|ALTER|TRUNCATE|DELETE|UPDATE|INSERT|REPLACE)\b")) "DDL create-only"
    Add-Result "schema.contract-tokens" (Test-ContainsAll -Path $sqlPath -Tokens @(
        'CREATE TABLE IF NOT EXISTS `phantom_profiles`',
        '`profile_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT',
        '`character_object_id` INT NULL DEFAULT NULL',
        '`schema_version` SMALLINT UNSIGNED NOT NULL DEFAULT 1',
        '`row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0',
        'UNIQUE KEY `uq_phantom_profiles_character_object_id` (`character_object_id`)',
        'CREATE TABLE IF NOT EXISTS `phantom_profile_components`',
        '`component_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL',
        '`component_schema_version` SMALLINT UNSIGNED NOT NULL',
        '`payload` VARBINARY(4096) NOT NULL',
        'PRIMARY KEY (`profile_id`, `component_type`)',
        'CONSTRAINT `fk_phantom_profile_components_profile`',
        'REFERENCES `phantom_profiles` (`profile_id`)',
        "ON DELETE CASCADE",
        "ENGINE=InnoDB",
        "DEFAULT CHARACTER SET=utf8mb4"
    )) "tables/columns/index/FK/engine/charset exact tokens"
    Add-Result "schema.no-characters-foreign-key" (-not [regex]::IsMatch($sql, '(?is)REFERENCES\s+`?characters`?')) "optional character link has no FK"

    $profilePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfile.java"
    $componentPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileComponent.java"
    $exceptionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfilePersistenceException.java"
    $repositoryPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java"
    $repository = if (Test-Path -LiteralPath $repositoryPath -PathType Leaf) { Get-Content -LiteralPath $repositoryPath -Raw -Encoding UTF8 } else { "" }
    Add-Result "model.immutable-records" ((Test-ContainsAll -Path $profilePath -Tokens @("public record PhantomProfile(", "Instant createdAt", "Instant updatedAt")) -and (Test-ContainsAll -Path $componentPath -Tokens @("public record PhantomProfileComponent(", "byte[] payload", "payload = copyPayload(payload);", "return payload.clone();"))) "records and defensive copies"
    Add-Result "model.component-bounds" (Test-ContainsAll -Path $componentPath -Tokens @("MAX_PAYLOAD_BYTES = 4096", "MAX_SCHEMA_VERSION = 65535", 'Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$")', "componentSchemaVersion < 1", "payload.length > MAX_PAYLOAD_BYTES")) "type/version/payload validation"
    Add-Result "model.categorized-failures" (Test-ContainsAll -Path $exceptionPath -Tokens @("DATABASE_ERROR", "SCHEMA_MISMATCH", "CONSTRAINT_VIOLATION")) "stable failure categories"
    Add-Result "repository.required-api" (Test-ContainsAll -Path $repositoryPath -Tokens @(
        "public static PhantomProfileRepository open()",
        "public PhantomProfile create(",
        "public Optional<PhantomProfile> find(",
        "public Optional<PhantomProfile> findByCharacterObjectId(",
        "public PhantomProfile updateCharacterLink(",
        "public void delete(",
        "public PhantomProfileComponent insertComponent(",
        "public Optional<PhantomProfileComponent> findComponent(",
        "public List<PhantomProfileComponent> listComponents(",
        "public PhantomProfileComponent updateComponent(",
        "public void deleteComponent("
    )) "core profile/component CRUD"
    Add-Result "repository.optimistic-core" (Test-ContainsAll -Path $repositoryPath -Tokens @("row_version = row_version + 1 WHERE profile_id = ? AND row_version = ?", "throw new ConcurrentModificationException")) "core compare-and-swap"
    Add-Result "repository.optimistic-components" (Test-ContainsAll -Path $repositoryPath -Tokens @("WHERE profile_id = ? AND component_type = ? AND row_version = ?", "ORDER BY component_type", "List.copyOf(components)")) "component CAS and deterministic immutable list"
    Add-Result "repository.no-lock-retry-worker" (-not [regex]::IsMatch($repository, "(?i)SELECT\s+FOR\s+UPDATE|LOCK\s+TABLE|retry|ScheduledFuture|ScheduledExecutor|ThreadPool|new\s+Thread|ConcurrentHashMap|LoadingCache|INSTANCE\s*=")) "no retry/locking/cache/background execution"
    Add-Result "repository.connection-per-operation" (Test-ContainsAll -Path $repositoryPath -Tokens @("DatabaseFactory.getConnection()", "connection.setAutoCommit(false)", "connection.commit()", "connection.rollback()")) "bounded JDBC transactions"
    Add-Result "repository.no-future-domain-models" (-not [regex]::IsMatch($repository, "(?i)personality|goal|schedule|activity|population|materialization|navigation|economy|conversation|memory|reputation")) "opaque persistence only"

    $gameServerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/GameServer.java"
    $phantomSystemPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
    $gameServer = if (Test-Path -LiteralPath $gameServerPath -PathType Leaf) { Get-Content -LiteralPath $gameServerPath -Raw -Encoding UTF8 } else { "" }
    $phantomSystem = if (Test-Path -LiteralPath $phantomSystemPath -PathType Leaf) { Get-Content -LiteralPath $phantomSystemPath -Raw -Encoding UTF8 } else { "" }
    $configReference = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot "dist/game/config") -Recurse -File | Select-String -SimpleMatch "PhantomProfileRepository")
    Add-Result "integration.no-gameserver-phantomsystem-config-wiring" (($gameServer -notmatch "PhantomProfileRepository") -and ($phantomSystem -notmatch "PhantomProfileRepository") -and ($configReference.Count -eq 0)) "repository remains disabled and unwired"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProfilePersistenceSuite.java"
    $headlessEnvironmentPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java"
    Add-Result "tests.launcher-and-ant-target" ((Test-ContainsAll -Path $buildPath -Tokens @("phantom-profile-persistence-test", "profile-persistence")) -and (Test-ContainsAll -Path $launcherPath -Tokens @('"profile-persistence"', "new PhantomProfilePersistenceSuite()"))) "forked Ant route registered"
    Add-Result "tests.profile-suite-contract" (Test-ContainsAll -Path $suitePath -Tokens @(
        "PhantomTestDatabaseBootstrap.initialize(",
        "StrictSqlScriptRunner.execute(",
        "PhantomProfileRepository.open()",
        "testRepositoryRestart()",
        "testConcurrentCoreUpdate()",
        "testPayloadBoundaries()",
        "assertResidueZero()"
    )) "bootstrap/replay/round-trip/restart/concurrency/cleanup"
    $explicitTests = if (Test-Path -LiteralPath $suitePath -PathType Leaf) { [regex]::Matches((Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8), 'registry\.add\(').Count } else { 0 }
    Add-Result "tests.minimum-explicit-cases" ($explicitTests -ge 15) "$explicitTests explicit cases"
    Add-Result "tests.thread-stabilization-test-only-bounded" (Test-ContainsAll -Path $headlessEnvironmentPath -Tokens @(
        "private static void stabilizeInfrastructureThreads()",
        "TimeUnit.SECONDS.toNanos(2)",
        "Math.min(ThreadConfig.HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE, 64)",
        "stableSamples >= 4"
    )) "two-second bounded test-only stabilization"

    $closureReportPath = Join-Path $moduleRoot "docs/phantoms/reports/004b-retained-identity-ownership-fix.md"
    $closureReviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/004b-retained-identity-ownership-fix-review.md"
    $adrPath = Join-Path $moduleRoot "docs/phantoms/adr/0001-headless-player-integration-seam.md"
    Add-Result "provenance.task-004b-closed" ((Test-ContainsAll -Path $closureReportPath -Tokens @("f5b66c4edf1ddf18e044ef8c692d70ecea616485", "66/66", "Independent review: ACCEPT")) -and (Test-ContainsAll -Path $closureReviewPath -Tokens @("Task 004B: ACCEPT", "39A1D87DB35AE8B2DDE28EB11776A69E2F7359AC6539A900BB78D114BDBB7BC9"))) "independent commit/push/verifier evidence"
    Add-Result "provenance.adr-0001-accepted" (Test-ContainsAll -Path $adrPath -Tokens @("Accepted", "f5b66c4edf1ddf18e044ef8c692d70ecea616485", "Task 004B", "ADR 0001")) "accepted on independently reviewed baseline"

    $contractPath = Join-Path $moduleRoot "docs/phantoms/architecture/PROFILE_PERSISTENCE_CONTRACT.md"
    $reportPath = Join-Path $moduleRoot "docs/phantoms/reports/005-core-profile-persistence-envelope.md"
    Add-Result "docs.contract-headings" (Test-ContainsAll -Path $contractPath -Tokens @("# ", "core Phantom profile/persistence envelope", "phantom_profiles", "phantom_profile_components", "## Repository lifecycle", "## Optimistic locking", "opaque", "GameServer", "PhantomSystem")) "architecture contract complete"
    Add-Result "docs.report-headings" (Test-ContainsAll -Path $reportPath -Tokens @(
        "# Task 005", "baseline", "Task 004B", "ADR 0001", "ThreadPool baseline", "fingerprint",
        "Production classes", "Optimistic locking", "provisioning x2", "gate", "Scope", "PENDING_INDEPENDENT_REVIEW",
        'Goal 006: `NOT_STARTED`'
    )) "required report sections/state"
    $report = if (Test-Path -LiteralPath $reportPath -PathType Leaf) { Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 } else { "" }
    $headlessCommandCount = [regex]::Matches($report, "(?m)^ant phantom-headless-player-test$").Count
    Add-Result "docs.three-headless-pass-command-records" ($headlessCommandCount -ge 3) "$headlessCommandCount commands"
    Add-Result "docs.external-handoff-evidence-clause" ($report.Contains("Exact immutable commit SHA, push result and post-commit verifier outputs are`r`nexternal final-handoff evidence generated after this report is committed.") -or $report.Contains("Exact immutable commit SHA, push result and post-commit verifier outputs are`nexternal final-handoff evidence generated after this report is committed.")) "immutable post-commit facts explicitly external"

    $textPaths = @($changed | Where-Object { $_ -match "(?i)\.(java|xml|sql|md|txt|json|ps1)$" })
    $invalidUtf8 = New-Object System.Collections.Generic.List[string]
    $mojibake = New-Object System.Collections.Generic.List[string]
    $escapedCyrillic = New-Object System.Collections.Generic.List[string]
    $credentialLeaks = New-Object System.Collections.Generic.List[string]
    $mojibakeMarkers = New-Object System.Collections.Generic.List[string]
    foreach ($codePoint in @(0x045F, 0x045C, 0x045B, 0x2022, 0x040E, 0x203A, 0x00A4, 0x045A, 0x0408, 0x0459, 0x0491, 0x00B5, 0x00B0, 0x00BB, 0x0405, 0x0455))
    {
        [void]$mojibakeMarkers.Add(([string][char]0x0420) + ([string][char]$codePoint))
    }
    foreach ($codePoint in @(0x040F, 0x20AC, 0x0402, 0x2039, 0x040A, 0x201A, 0x0453, 0x2021, 0x2026, 0x2020))
    {
        [void]$mojibakeMarkers.Add(([string][char]0x0421) + ([string][char]$codePoint))
    }
    [void]$mojibakeMarkers.Add([string][char]0xFFFD)
    $mojibakePattern = ($mojibakeMarkers | ForEach-Object { [regex]::Escape($_) }) -join "|"
    $escapedPattern = '\\u0[45][0-9A-Fa-f]{2}|&#[xX]0[45][0-9A-Fa-f]{2};'
    foreach ($repositoryPath in $textPaths)
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
        if ($content -match $mojibakePattern)
        {
            [void]$mojibake.Add($repositoryPath)
        }
        if ($content -match $escapedPattern)
        {
            [void]$escapedCyrillic.Add($repositoryPath)
        }
        $credentialPattern = '(?i)jdbc:mariadb://127\.0\.0\.1:3308/l2jmobiush5(?!_phantom_test)|root' + '/root|password\s*[:=]\s*root'
        if ($content -match $credentialPattern)
        {
            [void]$credentialLeaks.Add($repositoryPath)
        }
    }
    Add-Result "encoding.valid-utf8" ($invalidUtf8.Count -eq 0) $(if ($invalidUtf8.Count -eq 0) { "$($textPaths.Count) text files" } else { $invalidUtf8 -join "," })
    Add-Result "encoding.no-mojibake-markers" ($mojibake.Count -eq 0) $(if ($mojibake.Count -eq 0) { "none" } else { $mojibake -join "," })
    Add-Result "encoding.no-escaped-cyrillic" ($escapedCyrillic.Count -eq 0) $(if ($escapedCyrillic.Count -eq 0) { "none" } else { $escapedCyrillic -join "," })
    Add-Result "security.no-credentials" ($credentialLeaks.Count -eq 0) $(if ($credentialLeaks.Count -eq 0) { "none" } else { $credentialLeaks -join "," })

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
