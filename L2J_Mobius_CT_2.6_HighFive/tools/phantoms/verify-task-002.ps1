[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "7aa24faf202567add0fa81561242d37453c6055f",
    [long]$Seed = 20260725001
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
    return ($relative -ceq ".gitignore") -or
        ($relative -ceq "build.xml") -or
        ($relative -ceq "java/org/l2jmobius/commons/config/DatabaseConfig.java") -or
        ($relative -ceq "java/org/l2jmobius/commons/database/DatabaseFactory.java") -or
        $relative.StartsWith("test/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "tools/phantoms/prepare-test-db.ps1") -or
        ($relative -ceq "tools/phantoms/verify-task-002.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/002-automated-test-infrastructure/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/002-automated-test-infrastructure.md")
}

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootResult = Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel")
    $gitRoot = (Resolve-Path $gitRootResult.Output[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $expectedModule = Join-Path $gitRoot $relativeModule
    Add-Result "repository.module-root" ($moduleRoot -ceq $expectedModule) $moduleRoot

    $branchResult = Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")
    $currentBranch = $branchResult.Output[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $currentBranch

    $baseResult = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.base-commit" ($baseResult.ExitCode -eq 0) $BaseCommit

    $headResult = Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")
    $headCommit = $headResult.Output[0]
    $mode = "invalid"
    $commitShape = $false
    if ($headCommit -ceq $BaseCommit)
    {
        $mode = "pre-commit"
        $commitShape = $true
    }
    elseif ($baseResult.ExitCode -eq 0)
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $count = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $mode = "post-commit"
        $commitShape = (($parent -ceq $BaseCommit) -and ($count -eq 1))
    }
    Add-Result "repository.commit-shape" $commitShape $mode
    Add-Result "repository.seed" ($Seed -eq 20260725001) ([string]$Seed)

    $requiredRelative = @(
        ".gitignore",
        "build.xml",
        "java/org/l2jmobius/commons/config/DatabaseConfig.java",
        "java/org/l2jmobius/commons/database/DatabaseFactory.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomAssertions.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomHarnessUnitSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomNegativeControlSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomPerformanceSmokeSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomScenarioSmokeSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestConfigurationException.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestContext.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseIntegrationSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestRegistry.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestResult.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/SentinelJdbcDriver.java",
        "test/java/org/l2jmobius/tests/phantoms/StrictSqlScriptRunner.java",
        "test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql",
        "test/resources/phantoms/scenarios/harness-smoke.properties",
        "tools/phantoms/prepare-test-db.ps1",
        "tools/phantoms/verify-task-002.ps1",
        "docs/phantoms/reports/002-automated-test-infrastructure.md"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $requiredRelative))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }

    $committed = @()
    if ($headCommit -cne $BaseCommit)
    {
        $committed = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$BaseCommit...HEAD")).Output
    }
    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit)).Output
    $cached = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $BaseCommit)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")).Output
    $modulePrefix = "$relativeModule/"
    $taskUntracked = @($untracked | Where-Object { Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix })
    $unrelatedUntracked = @($untracked | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    $changed = Get-OrdinalSortedUnique ([string[]]($committed + $tracked + $cached + $taskUntracked))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "workspace.unrelated-untracked-preserved" $true "$($unrelatedUntracked.Count) excluded from Task 002 scope"

    $scopeViolations = @()
    foreach ($path in $changed)
    {
        if (-not (Test-TaskScopePath -RepositoryPath $path -ModulePrefix $modulePrefix))
        {
            $scopeViolations += $path
        }
    }
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) (($scopeViolations -join ",") -replace "^$", "no violations")
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-task-003" (@($changed | Where-Object { $_ -match "(?i)(tasks|reports|tools|java).*(003|gameserver/phantoms|PhantomPlayers\.ini)" }).Count -eq 0) "Task 003 absent"
    Add-Result "scope.production-java-exact" (@($changed | Where-Object { $_ -match "^$([regex]::Escape($modulePrefix))java/.*\.java$" -and $_ -notmatch "/java/org/l2jmobius/commons/(config/DatabaseConfig|database/DatabaseFactory)\.java$" }).Count -eq 0) "two allowed production support files"
    Add-Result "scope.no-forbidden-production" (@($changed | Where-Object { $_ -match "/java/org/l2jmobius/(gameserver|loginserver)/|/dist/game/|/dist/db_installer/" }).Count -eq 0) "no server/config/schema changes"
    Add-Result "scope.no-binary-test-jar" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binary artifacts"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $buildTokens = @(
        'name="test.src"',
        'name="test.resources"',
        'name="build.test"',
        'name="build.test.bin"',
        'name="build.test.resources"',
        'name="build.test.reports"',
        'name="phantom.test.seed"',
        'name="phantom.test.config"',
        'target name="init-test"',
        'target name="compile-tests"',
        'target name="test"',
        'target name="prepare-phantom-test-db"',
        'target name="phantom-db-guard-negative-control"',
        'target name="phantom-negative-control"',
        'target name="phantom-db-test"',
        'target name="phantom-scenario-test"',
        'target name="phantom-performance-smoke"',
        'target name="phantom-static-verify"',
        'target name="verify"',
        'fork="true"',
        'resultproperty="phantom.negative.exit"',
        'resultproperty="phantom.guard.negative.exit"'
    )
    Add-Result "build.required-contract" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) required tokens"
    $buildText = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $javaCount = ([regex]::Matches($buildText, "<java\s")).Count
    $forkCount = ([regex]::Matches($buildText, 'fork="true"')).Count
    Add-Result "build.all-java-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"
    Add-Result "build.test-separate" (($buildText.Contains('srcdir="${test.src}"')) -and ($buildText.Contains('destdir="${build.test.bin}"')) -and ($buildText.Contains('<fileset dir="${build.bin}">'))) "separate test bin; production jar uses production bin"
    Add-Result "build.no-external-test-framework" ($buildText -notmatch "(?i)junit|testng|maven|gradle") "JDK-only"

    $databaseConfigPath = Join-Path $moduleRoot "java/org/l2jmobius/commons/config/DatabaseConfig.java"
    Add-Result "production.database-config-seam" (Test-ContainsAll -Path $databaseConfigPath -Tokens @("DEFAULT_DATABASE_CONFIG_FILE", "public static void load()", "public static void load(String configFile)", "load(DEFAULT_DATABASE_CONFIG_FILE, false)")) "default + explicit load"
    $databaseFactoryPath = Join-Path $moduleRoot "java/org/l2jmobius/commons/database/DatabaseFactory.java"
    Add-Result "production.database-factory-seam" (Test-ContainsAll -Path $databaseFactoryPath -Tokens @("public static synchronized void init()", "public static synchronized void initFromConfig(String configFile)", "initializePool(false)", "initializePool(true)", "public static synchronized boolean isInitialized()", "DATABASE_POOL = null")) "production + fail-fast lifecycle"
    $factoryText = Get-Content -LiteralPath $databaseFactoryPath -Raw -Encoding UTF8
    Add-Result "production.no-test-policy" ($factoryText -notmatch "l2jmobiush5|l2j_phantom_test|Phantom") "generic factory"

    $guardPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java"
    $guardTokens = @(
        'TARGET_DATABASE = "l2jmobiush5_phantom_test"',
        'PRODUCTION_DATABASE = "l2jmobiush5"',
        'TARGET_USER = "l2j_phantom_test"',
        "TARGET_PORT = 3308",
        'LOCAL_CONFIG_DIRECTORY = ".phantom-local"',
        "toRealPath()",
        "validateJdbcUrl",
        "uri.getUserInfo()",
        'rawPath.contains("%")',
        "ValidatedSettings",
        "password=<redacted>"
    )
    Add-Result "guard.fail-closed-contract" (Test-ContainsAll -Path $guardPath -Tokens $guardTokens) "$($guardTokens.Count) guard invariants"

    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    $launcherText = Get-Content -LiteralPath $launcherPath -Raw -Encoding UTF8
    Add-Result "runner.explicit-suites" (Test-ContainsAll -Path $launcherPath -Tokens @("new PhantomHarnessUnitSuite()", "new PhantomNegativeControlSuite()", "new PhantomTestDatabaseIntegrationSuite()", "new PhantomScenarioSmokeSuite()", "new PhantomPerformanceSmokeSuite()")) "explicit registry"
    Add-Result "runner.exit-contract" (Test-ContainsAll -Path $launcherPath -Tokens @("EXIT_SUCCESS = 0", "EXIT_TEST_FAILURE = 1", "EXIT_CONFIGURATION_REJECTED = 2", "EXIT_INTERNAL_ERROR = 3")) "0/1/2/3"
    Add-Result "runner.reports" (Test-ContainsAll -Path $launcherPath -Tokens @(".txt", ".xml", "testsuite", "seed=", "escapeXml", "sanitize")) "text + XML + redaction"
    Add-Result "runner.no-reflection-discovery" ($launcherText -notmatch "Class\.forName|java\.lang\.reflect|ServiceLoader") "no discovery"

    $negativeMethodStart = $launcherText.IndexOf("private static int runGuardNegative", [System.StringComparison]::Ordinal)
    $negativeMethodEnd = $launcherText.IndexOf("static int exitCodeFor", [System.StringComparison]::Ordinal)
    $negativeMethod = if (($negativeMethodStart -ge 0) -and ($negativeMethodEnd -gt $negativeMethodStart)) { $launcherText.Substring($negativeMethodStart, $negativeMethodEnd - $negativeMethodStart) } else { "" }
    Add-Result "negative.guard-before-driver" (($negativeMethod.Contains("PhantomTestDatabaseGuard.validate")) -and ($negativeMethod -notmatch "Class\.forName|DriverManager|getConnection|initFromConfig|Hikari")) "guard-only rejection path"
    Add-Result "negative.sentinel-proof" (Test-ContainsAll -Path $launcherPath -Tokens @("SentinelJdbcDriver", "Files.exists(marker)", "guard.driverLoads", "guard.connectionAttempts", "return EXIT_CONFIGURATION_REJECTED")) "marker and zero attempts"

    $provisionerPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java"
    $provisionerText = Get-Content -LiteralPath $provisionerPath -Raw -Encoding UTF8
    $inventoryIndex = $provisionerText.IndexOf("StrictSqlScriptRunner.inventory", [System.StringComparison]::Ordinal)
    $driverIndex = $provisionerText.IndexOf("Class.forName", [System.StringComparison]::Ordinal)
    $dropIndex = $provisionerText.IndexOf("dropTarget(connection)", [System.StringComparison]::Ordinal)
    Add-Result "provision.preflight-order" (($inventoryIndex -ge 0) -and ($driverIndex -gt $inventoryIndex) -and ($dropIndex -gt $driverIndex)) "inventory < driver < destructive SQL"
    Add-Result "provision.environment-only" (Test-ContainsAll -Path $provisionerPath -Tokens @("PHANTOM_DB_ADMIN_URL", "PHANTOM_DB_ADMIN_USER", "PHANTOM_DB_ADMIN_PASSWORD", "System.getenv", "SecureRandom", "ATOMIC_MOVE", "test-db.lock")) "environment/secret/atomic/lock"
    Add-Result "provision.strict-schema" (Test-ContainsAll -Path $provisionerPath -Tokens @("StrictSqlScriptRunner.execute", "dist/db_installer/sql/login", "dist/db_installer/sql/game", "test/resources/phantoms/db/migrations", "schema-manifest.txt", "safeCleanup")) "strict schema + cleanup"

    $sqlRunnerPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/StrictSqlScriptRunner.java"
    Add-Result "sql.strict-runner" (Test-ContainsAll -Path $sqlRunnerPath -Tokens @("StandardCharsets.UTF_8", "UNSUPPORTED_SYNTAX", "FILE_ORDER", "SqlScriptException", "statementIndex", "throw new IllegalArgumentException", "executor.execute")) "UTF-8/order/fail-first"
    $migrationPath = Join-Path $moduleRoot "test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql"
    Add-Result "migration.harness-owned" (Test-ContainsAll -Path $migrationPath -Tokens @("CREATE TABLE IF NOT EXISTS", "phantom_test_harness", "fixture_key", "seed", "fixture_value", "created_marker")) "versioned idempotent harness table"

    $allTaskText = ($changed | Where-Object { $_ -match "\.(java|xml|ps1|md|json|txt|sql|properties|gitignore)$|/\.gitignore$" } | ForEach-Object {
        $absolute = Join-Path $gitRoot $_
        if (Test-Path -LiteralPath $absolute -PathType Leaf)
        {
            Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        }
    }) -join "`n"
    Add-Result "safety.no-committed-config" (@($changed | Where-Object { $_ -match "/\.phantom-local/" }).Count -eq 0) "local config excluded from Task 002 changes"
    $ignoreResult = Invoke-Git -Root $gitRoot -Arguments @("check-ignore", "-q", "$relativeModule/.phantom-local/Database.test.ini") -AllowFailure
    Add-Result "safety.local-config-ignored" ($ignoreResult.ExitCode -eq 0) ".phantom-local ignored"
    $trackedConfigResult = Invoke-Git -Root $gitRoot -Arguments @("ls-files", "$relativeModule/.phantom-local/Database.test.ini")
    Add-Result "safety.local-config-untracked" ($trackedConfigResult.Output.Count -eq 0) "local config not tracked"
    Add-Result "safety.no-literal-admin-secret" ($allTaskText -notmatch "(?i)PHANTOM_DB_ADMIN_PASSWORD\s*=\s*['""][^<'""]+") "no admin secret assignment"
    Add-Result "safety.production-db-no-sql" ($provisionerText -notmatch "(?i)(USE|SELECT.+FROM|ALTER|INSERT|UPDATE|DELETE|DROP\s+DATABASE)\s+`?l2jmobiush5`?(\s|;|$)") "no production schema SQL"

    $scenarioText = Get-Content -LiteralPath (Join-Path $moduleRoot "test/resources/phantoms/scenarios/harness-smoke.properties") -Raw -Encoding UTF8
    Add-Result "determinism.seed" ($scenarioText.Contains("20260725001")) "20260725001"
    Add-Result "determinism.checksum" ($scenarioText.Contains("A7D53E8FCBF889691310AAC61A45EFD461702FECE26BA292D73309A9FE357C45")) "expected checksum"
    $performancePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomPerformanceSmokeSuite.java"
    Add-Result "performance.bounded" (Test-ContainsAll -Path $performancePath -Tokens @("OPERATIONS = 250000", "TIMEOUT_NANOS = 30000000000L", "long checksum", "new SplittableRandom")) "250000 operations, O(1), 30s"

    $reportPath = Join-Path $moduleRoot "docs/phantoms/reports/002-automated-test-infrastructure.md"
    $reportTokens = @(
        "## Status",
        "## Starting baseline",
        "## Test runtime architecture",
        "## Production compatibility",
        "## DB guard ordering proof",
        "## Test DB provisioning",
        "## Schema script inventory/hashes",
        "## Dedicated user/grants",
        "## Local config",
        "## Negative controls",
        "## Fixture lifecycle",
        "## Ant targets",
        "## Suite/test counts",
        "## Determinism",
        "## Scenario checksum",
        "## Performance smoke measurement",
        "## Secrets redaction",
        "## Scope",
        "## Commands/exit codes",
        "## Pre/final verifier",
        "## Branch/parent/commit/push",
        "PENDING_INDEPENDENT_REVIEW",
        "NOT_STARTED"
    )
    Add-Result "report.required-sections" ((Test-Path -LiteralPath $reportPath -PathType Leaf) -and (Test-ContainsAll -Path $reportPath -Tokens $reportTokens)) "$($reportTokens.Count) required report facts"

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
    Add-Result "verifier.local-read-only" $true "Git/file checks only; no DB/network/write operations"
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
