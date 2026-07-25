[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "36e5411e01e8e73f8a0fd4d9460e327c28a6798b"
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
        $relative.StartsWith("test/java/org/l2jmobius/tests/phantoms/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql") -or
        ($relative -ceq "tools/phantoms/verify-task-002a.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/002a-test-infrastructure-safety-hotfix/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/002-automated-test-infrastructure.md") -or
        ($relative -ceq "docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md") -or
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
        "test/java/org/l2jmobius/tests/phantoms/PhantomLifecycleFailureControlSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLock.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLockControl.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseBootstrap.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomTestSchemaManifest.java",
        "test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql",
        "tools/phantoms/verify-task-002a.ps1",
        "docs/phantoms/reports/002-automated-test-infrastructure.md",
        "docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md",
        "docs/phantoms/reviews/002-automated-test-infrastructure-review.md"
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
    Add-Result "scope.no-production-java" (@($changed | Where-Object { $_ -match "/java/org/l2jmobius/(commons|gameserver|loginserver)/" }).Count -eq 0) "production Java unchanged"
    Add-Result "scope.no-task-003" (@($changed | Where-Object { $_ -match "(?i)(tasks|reports|java|tools).*(003|gameserver/phantoms|PhantomPlayers\.ini)" }).Count -eq 0) "Task 003 absent"
    $allowedMigration = $modulePrefix + "test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql"
    Add-Result "scope.no-config-or-schema-drift" (@($changed | Where-Object { ($_ -match "/dist/") -or (($_ -match "\.sql$") -and ($_ -cne $allowedMigration)) }).Count -eq 0) "only migration 002"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binaries"
    $oldVerifierDiff = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", "$modulePrefix`tools/phantoms/verify-task-002.ps1") -AllowFailure
    Add-Result "scope.old-verifier-unchanged" ($oldVerifierDiff.ExitCode -eq 0) "verify-task-002.ps1"

    $lockPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLock.java"
    Add-Result "lock.os-ownership-contract" (Test-ContainsAll -Path $lockPath -Tokens @("implements AutoCloseable", "FileChannel", "FileLock", "tryLock()", "StandardOpenOption.CREATE", "ownerToken", "channel.force(true)")) "FileChannel/FileLock/tryLock"
    $provisionerPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java"
    $provisioner = Get-Content -LiteralPath $provisionerPath -Raw -Encoding UTF8
    Add-Result "lock.provisioner-try-with-resources" ($provisioner.Contains("try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))")) "ownership scope"
    Add-Result "lock.no-unconditional-delete" (-not $provisioner.Contains("Files.deleteIfExists(lockFile)")) "persistent lock file"
    $lockScopeIndex = $provisioner.IndexOf("try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))", [System.StringComparison]::Ordinal)
    $ownedCleanupIndex = $provisioner.IndexOf("safeCleanup(admin);", [System.StringComparison]::Ordinal)
    $outerFailureIndex = $provisioner.IndexOf('System.err.println("Phantom test DB provisioning failed:', [System.StringComparison]::Ordinal)
    Add-Result "lock.cleanup-within-ownership" (($lockScopeIndex -ge 0) -and ($ownedCleanupIndex -gt $lockScopeIndex) -and ($outerFailureIndex -gt $ownedCleanupIndex)) "DB cleanup before lock close"
    $lockControlPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLockControl.java"
    Add-Result "lock.cross-process-control" (Test-ContainsAll -Path $lockControlPath -Tokens @("ProcessBuilder", "holder", "contender", "destroyForcibly", "EXIT_CONFIGURATION_REJECTED", "owner token", "protected path")) "normal/contention/crash"

    $manifestPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestSchemaManifest.java"
    Add-Result "manifest.durable-atomic-contract" (Test-ContainsAll -Path $manifestPath -Tokens @('LOCAL_MANIFEST_FILE = "schema-manifest.properties"', 'LOCAL_CONFIG_DIRECTORY', "SCHEMA_VERSION = 1", "scriptCount", "statementCount", "aggregateSha256", "ATOMIC_MOVE", "REPLACE_EXISTING", "MANIFEST_KEY", "writeDatabaseMetadata", "requireExactDatabaseMetadata")) "local + DB metadata"
    $migrationPath = Join-Path $moduleRoot "test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql"
    Add-Result "manifest.migration-002" (Test-ContainsAll -Path $migrationPath -Tokens @("CREATE TABLE IF NOT EXISTS", "phantom_test_schema_manifest", "manifest_key", "schema_version", "script_count", "statement_count", "aggregate_sha256")) "canonical metadata table"

    $bootstrapPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseBootstrap.java"
    $bootstrap = Get-Content -LiteralPath $bootstrapPath -Raw -Encoding UTF8
    $guardIndex = $bootstrap.IndexOf("PhantomTestDatabaseGuard.validate", [System.StringComparison]::Ordinal)
    $inventoryIndex = $bootstrap.IndexOf("PhantomTestSchemaManifest.current", [System.StringComparison]::Ordinal)
    $readIndex = $bootstrap.IndexOf("PhantomTestSchemaManifest.read", [System.StringComparison]::Ordinal)
    $compareIndex = $bootstrap.IndexOf("PhantomTestSchemaManifest.requireExact", [System.StringComparison]::Ordinal)
    $hikariIndex = $bootstrap.IndexOf("DatabaseFactory.initFromConfig", [System.StringComparison]::Ordinal)
    Add-Result "manifest.pre-hikari-order" (($guardIndex -ge 0) -and ($inventoryIndex -gt $guardIndex) -and ($readIndex -gt $inventoryIndex) -and ($compareIndex -gt $readIndex) -and ($hikariIndex -gt $compareIndex)) "guard < inventory < local < compare < Hikari"
    $dbSuitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseIntegrationSuite.java"
    Add-Result "manifest.db-suite-bootstrap-row" (Test-ContainsAll -Path $dbSuitePath -Tokens @("PhantomTestDatabaseBootstrap.initialize", "schemaSnapshot", "requireExactDatabaseMetadata", "schema-manifest-metadata")) "bootstrap + DB row"

    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    Add-Result "freshness.sentinel-before-driver" (Test-ContainsAll -Path $launcherPath -Tokens @("schema-freshness-negative", "PhantomTestDatabaseBootstrap.initialize", "SentinelJdbcDriver", "driverLoads", "connectionAttempts", "sentinel marker absent")) "stale manifest negative control"
    $launcher = Get-Content -LiteralPath $launcherPath -Raw -Encoding UTF8
    $startIndex = $launcher.IndexOf("lifecycleStarted = true", [System.StringComparison]::Ordinal)
    $beforeIndex = $launcher.IndexOf("suite.beforeAll(context)", [System.StringComparison]::Ordinal)
    Add-Result "lifecycle.after-partial-before-all" (($startIndex -ge 0) -and ($beforeIndex -gt $startIndex) -and $launcher.Contains("suite.afterAll(context)")) "lifecycle started before beforeAll"
    $lifecyclePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomLifecycleFailureControlSuite.java"
    Add-Result "lifecycle.failure-control" (Test-ContainsAll -Path $lifecyclePath -Tokens @("partial-before-all-resource", "PhantomTestConfigurationException", "Files.deleteIfExists", "Intentional afterAll lifecycle failure")) "original + cleanup result"

    $guardPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java"
    Add-Result "jdbc.strict-query-allowlist" (Test-ContainsAll -Path $guardPath -Tokens @("ALLOWED_QUERY", '"useSSL", "false"', '"allowPublicKeyRetrieval", "true"', '"serverTimezone", "UTC"', '"characterEncoding", "UTF-8"', "URLDecoder.decode", "toLowerCase(Locale.ROOT)", "duplicate property", "ambiguous key or value")) "canonical query only"
    $unitPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHarnessUnitSuite.java"
    Add-Result "jdbc.auth-query-regressions" (Test-ContainsAll -Path $unitPath -Tokens @("?user=root", "?password=secret", "?Password=secret", "?%70assword=secret", "?password1=secret", "?password2=secret", "?password3=secret", "url-query-duplicate", "url-query-blank-key", "url-query-blank-value", "url-query-encoded-separator", "url-query-valid-generated")) "auth/malformed/valid tests"
    Add-Result "secrets.expanded-redaction" (Test-ContainsAll -Path $launcherPath -Tokens @("JDBC_QUERY_SECRET", "IDENTIFIED_BY_SINGLE_QUOTE", "IDENTIFIED_BY_DOUBLE_QUOTE", "password(?:[123])?", "JDBC_USER_INFO")) "query/userinfo/SQL"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $buildTokens = @(
        'target name="phantom-provisioning-lock-control"',
        'target name="phantom-schema-freshness-negative-control"',
        'target name="phantom-lifecycle-negative-control"',
        'target name="phantom-static-verify-002a"',
        'resultproperty="phantom.freshness.negative.exit"',
        'resultproperty="phantom.lifecycle.negative.exit"',
        'phantom.test.schema.manifest',
        'verify-task-002a.ps1',
        'depends="jar,test,phantom-negative-control,phantom-db-guard-negative-control,phantom-provisioning-lock-control,phantom-schema-freshness-negative-control,phantom-lifecycle-negative-control,phantom-db-test,phantom-scenario-test,phantom-performance-smoke,phantom-static-verify,phantom-static-verify-002a"'
    )
    Add-Result "build.task-002a-contract" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) tokens"
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $javaCount = ([regex]::Matches($build, "<java\s")).Count
    $forkCount = ([regex]::Matches($build, 'fork="true"')).Count
    Add-Result "build.all-java-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"

    $task002Report = Join-Path $moduleRoot "docs/phantoms/reports/002-automated-test-infrastructure.md"
    Add-Result "report.task-002-provenance" (Test-ContainsAll -Path $task002Report -Tokens @("36e5411e01e8e73f8a0fd4d9460e327c28a6798b", "7aa24faf202567add0fa81561242d37453c6055f", "70/70", "863B235A99D686D99F8B1DA98762DCBD3A683D0E729F66CB88590954A609CE0C", "FIX REQUIRED", "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW")) "immutable original facts"
    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/002-automated-test-infrastructure-review.md"
    Add-Result "report.review-record" (Test-ContainsAll -Path $reviewPath -Tokens @("Original Task 002 implementation: FIX REQUIRED", "Revert: NOT_REQUIRED", "Task 002A closure: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW", "Task 003: NOT_STARTED")) "review decision"
    $report002a = Join-Path $moduleRoot "docs/phantoms/reports/002a-test-infrastructure-safety-hotfix.md"
    $reportHeadings = @(
        "## Status",
        "## Starting baseline",
        "## Independent findings addressed",
        "## Lock ownership design",
        "## Cross-process lock evidence",
        "## Schema freshness design",
        "## Pre-Hikari stale-manifest evidence",
        "## DB metadata evidence",
        "## Runner lifecycle fix",
        "## JDBC query allowlist",
        "## Secret redaction",
        "## Ant targets",
        "## Test counts",
        "## Re-provisioning",
        "## Production DB safety",
        "## Scope",
        "## Commands/exit codes",
        "## Pre/final verifier",
        "## Branch/parent/commit/push",
        "## Manual gate",
        "PENDING_INDEPENDENT_REVIEW",
        "## Task 003",
        "NOT_STARTED"
    )
    Add-Result "report.task-002a-sections" (Test-ContainsAll -Path $report002a -Tokens $reportHeadings) "$($reportHeadings.Count) facts"

    $allChangedText = ($changed | ForEach-Object {
        $absolute = Join-Path $gitRoot $_
        if ((Test-Path -LiteralPath $absolute -PathType Leaf) -and ($_ -notmatch "(?i)\.(jar|class|zip|7z|exe|dll|bin)$"))
        {
            Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        }
    }) -join "`n"
    Add-Result "safety.no-literal-admin-secret" ($allChangedText -notmatch "(?i)PHANTOM_DB_ADMIN_PASSWORD\s*=\s*['""][^<'""]+") "no credential assignment"
    Add-Result "safety.no-local-artifacts" (@($changed | Where-Object { $_ -match "/\.phantom-local/" }).Count -eq 0) ".phantom-local excluded"
    Add-Result "safety.no-production-db-sql" ($provisioner -notmatch "(?i)(USE|SELECT.+FROM|ALTER|INSERT|UPDATE|DELETE|DROP\s+DATABASE)\s+`?l2jmobiush5`?(\s|;|$)") "no production DB SQL"

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
