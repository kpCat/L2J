[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "cdca7a3d96554285eb8c992fa14f65b27f7f36ae",
    [string]$OriginalCommit = "e7dcf575dd45a94c83560fd140144635bbf96e37",
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

function Invoke-Git
{
    param(
        [string]$Root,
        [string[]]$Arguments
    )

    $output = @(& git -c core.safecrlf=false -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0)
    {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE`: $($output -join [Environment]::NewLine)"
    }

    return [string[]]$output
}

function Test-GitCommit
{
    param(
        [string]$Root,
        [string]$Commit
    )

    & git -C $Root cat-file -e "$Commit`^{commit}" 2>$null
    return ($LASTEXITCODE -eq 0)
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

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootOutput = @(Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel"))
    $gitRoot = (Resolve-Path $gitRootOutput[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $expectedModule = Join-Path $gitRoot $relativeModule

    Add-Result "repository.module-root" ($moduleRoot -eq $expectedModule) $moduleRoot

    $currentBranch = @(Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current"))[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $currentBranch

    $headCommit = @(Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD"))[0]
    $baseExists = Test-GitCommit -Root $gitRoot -Commit $BaseCommit
    $originalExists = Test-GitCommit -Root $gitRoot -Commit $OriginalCommit
    Add-Result "repository.base-commit" $baseExists $BaseCommit
    Add-Result "repository.original-commit" $originalExists $OriginalCommit

    $expectedParent = "16d61833b3983a3976583d0e4813e0de9457a52f"
    if ($originalExists)
    {
        $originalParent = @(Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$OriginalCommit^"))[0]
        Add-Result "repository.original-parent" ($originalParent -ceq $expectedParent) $originalParent
    }

    if ($baseExists)
    {
        $baseParent = @(Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$BaseCommit^"))[0]
        Add-Result "repository.amended-parent" ($baseParent -ceq $expectedParent) $baseParent
    }

    $mode = "invalid"
    $commitShapeValid = $false
    if ($headCommit -ceq $BaseCommit)
    {
        $mode = "pre-commit"
        $commitShapeValid = $true
    }
    elseif ($baseExists)
    {
        $headParent = @(Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^"))[0]
        $commitCount = [int](@(Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD"))[0])
        $mode = "post-commit"
        $commitShapeValid = (($headParent -ceq $BaseCommit) -and ($commitCount -eq 1))
    }
    Add-Result "repository.commit-shape" $commitShapeValid $mode
    Add-Result "repository.seed" ($Seed -eq 20260725001) ([string]$Seed)

    $expectedRelative = @(
        "Agents.md",
        "docs/phantoms/reports/001-baseline-architecture-audit.md",
        "docs/phantoms/reports/001a-review-closure.md",
        "docs/phantoms/reviews/001-baseline-architecture-audit-review.md",
        "docs/phantoms/tasks/001a-review-closure/ACCEPTANCE.md",
        "docs/phantoms/tasks/001a-review-closure/CODEX_LAUNCHER.txt",
        "docs/phantoms/tasks/001a-review-closure/CONTEXT.md",
        "docs/phantoms/tasks/001a-review-closure/PACKAGE_MANIFEST.json",
        "docs/phantoms/tasks/001a-review-closure/TASK.md",
        "tools/phantoms/verify-task-001a.ps1"
    )
    $expectedRepositoryPaths = Get-OrdinalSortedUnique ($expectedRelative | ForEach-Object { "$relativeModule/$_" })

    foreach ($relative in (Get-OrdinalSortedUnique $expectedRelative))
    {
        $path = Join-Path $moduleRoot $relative
        Add-Result "artifact.$relative" (Test-Path -LiteralPath $path -PathType Leaf) $relative
    }

    $committed = @()
    if ($headCommit -cne $BaseCommit)
    {
        $committed = Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$BaseCommit...HEAD")
    }
    $trackedWork = Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit)
    $cachedWork = Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $BaseCommit)
    $untracked = Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")
    $changed = Get-OrdinalSortedUnique ([string[]]($committed + $trackedWork + $cachedWork + $untracked))

    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "scope.exact-file-set" (($changed -join "|") -ceq ($expectedRepositoryPaths -join "|")) "$($changed.Count) expected files"

    $allowedSet = New-Object "System.Collections.Generic.HashSet[string]" ([System.StringComparer]::Ordinal)
    foreach ($path in $expectedRepositoryPaths)
    {
        [void]$allowedSet.Add($path)
    }
    $scopeViolations = @($changed | Where-Object { -not $allowedSet.Contains($_) })
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) (($scopeViolations -join ",") -replace "^$", "no violations")

    $modulePrefix = "$relativeModule/"
    Add-Result "scope.target-module-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-production-java" (@($changed | Where-Object { $_ -match "\.java$" }).Count -eq 0) "no Java"
    Add-Result "scope.no-build-xml" (@($changed | Where-Object { $_ -match "(^|/)build\.xml$" }).Count -eq 0) "build.xml unchanged"
    Add-Result "scope.no-master-plan" (@($changed | Where-Object { $_ -match "/PHANTOM_DEVELOPMENT_MASTER_PLAN\.md$" }).Count -eq 0) "master plan unchanged"
    Add-Result "scope.no-adr" (@($changed | Where-Object { $_ -match "/docs/phantoms/adr/0001-headless-player-integration-seam\.md$" }).Count -eq 0) "ADR 0001 unchanged"
    Add-Result "scope.no-task-001-audits" (@($changed | Where-Object { $_ -match "/docs/phantoms/audits/001-baseline-architecture-audit/" }).Count -eq 0) "Task 001 audits unchanged"
    Add-Result "scope.no-old-verifier" (@($changed | Where-Object { $_ -match "/tools/phantoms/verify-task-001\.ps1$" }).Count -eq 0) "historical verifier unchanged"
    Add-Result "scope.no-runtime-config-data-sql" (@($changed | Where-Object { $_ -match "/dist/game/(config|data)/|/dist/db_installer/|\.sql$" }).Count -eq 0) "no runtime config/data/SQL"
    Add-Result "scope.no-binary-build-log" (@($changed | Where-Object { $_ -match "\.(jar|class|zip|7z|exe|dll|bin|log)$|/(build|logs?)/" }).Count -eq 0) "no binary/build/log"
    Add-Result "scope.task-002-not-started" (@($changed | Where-Object { $_ -match "/(tasks|reports|audits|reviews)/002([-/]|$)|/verify-task-002" }).Count -eq 0) "Task 002 absent"

    $packageManifestPath = Join-Path $moduleRoot "docs/phantoms/tasks/001a-review-closure/PACKAGE_MANIFEST.json"
    $packageManifest = Get-Content -LiteralPath $packageManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Add-Result "manifest.task-id" ($packageManifest.taskId -ceq "001a-review-closure") ([string]$packageManifest.taskId)
    Add-Result "manifest.branch" ($packageManifest.branch -ceq $Branch) ([string]$packageManifest.branch)
    Add-Result "manifest.original-commit" ($packageManifest.originalTaskCommit -ceq $OriginalCommit) ([string]$packageManifest.originalTaskCommit)
    Add-Result "manifest.base-commit" ($packageManifest.expectedStartingCommit -ceq $BaseCommit) ([string]$packageManifest.expectedStartingCommit)
    Add-Result "manifest.seed" ($packageManifest.auditSeed -eq $Seed) ([string]$packageManifest.auditSeed)
    Add-Result "manifest.no-production-code" ($packageManifest.productionCodeIncluded -eq $false) ([string]$packageManifest.productionCodeIncluded)

    $agentsPath = Join-Path $moduleRoot "Agents.md"
    $agentsTokens = @(
        "геодата отсутствует",
        "PathFinding = 2",
        "без region files полноценный pathfinding фактически недоступен",
        "runtime fallback без геодаты не проверялся",
        "Task 009",
        'fake/null-network `GameClient`: этот вариант отвергнут',
        "outbound/session seam",
        "headless packet/output sink без network I/O",
        '`ServerPacket.runImpl(Player)` effects ровно один раз',
        "client packet handlers не становятся внутренним Phantom API",
        'ADR 0001 остаётся `Proposed` до Task 004'
    )
    Add-Result "content.agents-review-closure" (Test-ContainsAll -Path $agentsPath -Tokens $agentsTokens) "$($agentsTokens.Count) required facts"
    $agentsText = Get-Content -LiteralPath $agentsPath -Raw -Encoding UTF8
    Add-Result "content.agents-old-pathfinding-removed" ($agentsText.IndexOf("геодата пока отсутствует, pathfinding отключён", [System.StringComparison]::Ordinal) -lt 0) "old wording absent"
    Add-Result "content.agents-old-headless-adapter-removed" ($agentsText.IndexOf("- headless client adapter", [System.StringComparison]::Ordinal) -lt 0) "ambiguous wording absent"

    $task001ReportPath = Join-Path $moduleRoot "docs/phantoms/reports/001-baseline-architecture-audit.md"
    $task001ReportTokens = @(
        "## Git provenance",
        "## Independent review",
        "e7dcf575dd45a94c83560fd140144635bbf96e37",
        "cdca7a3d96554285eb8c992fa14f65b27f7f36ae",
        'user independently added `Agents.md`',
        'Original Task 001 content: `ACCEPT`',
        'Amended branch state: `ACCEPT WITH FOLLOW-UP`',
        "No P0/P1 architectural findings were identified",
        'Pre-commit Task 001 verifier: PASS, 43/43 checks, exit `0`',
        'Final-commit verifier run 1 on `e7dcf575...`: PASS, 43/43 checks, exit `0`',
        'Final-commit verifier run 2 on `e7dcf575...`: PASS, 43/43 checks, exit `0`',
        "The two final verifier outputs were identical",
        "Task 002 remains",
        '`NOT_STARTED`'
    )
    Add-Result "content.task-001-report" (Test-ContainsAll -Path $task001ReportPath -Tokens $task001ReportTokens) "$($task001ReportTokens.Count) provenance/review facts"
    $task001ReportText = Get-Content -LiteralPath $task001ReportPath -Raw -Encoding UTF8
    $placeholderCount = @("required after commit", "to be recorded after commit") |
        Where-Object { $task001ReportText.IndexOf($_, [System.StringComparison]::Ordinal) -ge 0 }
    Add-Result "content.task-001-report-no-placeholders" (@($placeholderCount).Count -eq 0) "historical placeholders absent"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/001-baseline-architecture-audit-review.md"
    $reviewTokens = @(
        "## Scope reviewed",
        "## Git provenance",
        "## Findings",
        "## Architectural verdict",
        "## Follow-ups",
        "## Closure implementation",
        "## Current gate",
        "## Next allowed action",
        "Original task content: ACCEPT",
        "Amended branch state: ACCEPT WITH FOLLOW-UP",
        "Task 001A closure: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Task 002: NOT_STARTED",
        "Критических P0/P1 архитектурных дефектов не найдено"
    )
    Add-Result "content.review-record" (Test-ContainsAll -Path $reviewPath -Tokens $reviewTokens) "$($reviewTokens.Count) required review facts"

    $task001aReportPath = Join-Path $moduleRoot "docs/phantoms/reports/001a-review-closure.md"
    $task001aReportTokens = @(
        "# Codex report — 001a-review-closure",
        "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        "Task 002",
        '`NOT_STARTED`',
        $OriginalCommit,
        $BaseCommit,
        "databaseConnectionPerformed=false",
        "databaseMutationPerformed=false"
    )
    Add-Result "content.task-001a-report" (Test-ContainsAll -Path $task001aReportPath -Tokens $task001aReportTokens) "$($task001aReportTokens.Count) required report facts"

    $mojibakeMarkers = @(
        ("Р" + "џ"), ("Р" + "ќ"), ("Р" + "ћ"), ("Р" + "•"),
        ("Р" + "Ў"), ("Р" + "›"), ("Р" + "¤"), ("Р" + "њ"),
        ("Р" + "Ј"), ("Р" + "љ"), ("Р" + "ґ"), ("Р" + "µ"),
        ("Р" + "°"), ("Р" + "»"), ("Р" + "Ѕ"), ("Р" + "ѕ"),
        ("С" + "Џ"), ("С" + "€"), ("С" + "Ђ"), ("С" + "‹"),
        ("С" + "Њ"), ("С" + "‚"), ("С" + "ѓ"), ("С" + "‡"),
        ("С" + "…"), ("С" + "†"), ([string][char]0xFFFD)
    )
    $escapedCyrillicPatterns = @(
        ([string][char]92 + [char]92 + "u04[0-9A-Fa-f]{2}"),
        ([string][char]92 + [char]92 + "u05[0-9A-Fa-f]{2}"),
        ("&" + "#x04[0-9A-Fa-f]{2};"),
        ("&" + "#x05[0-9A-Fa-f]{2};"),
        ("&" + "#X04[0-9A-Fa-f]{2};"),
        ("&" + "#X05[0-9A-Fa-f]{2};")
    )
    $mojibakeFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    $escapedCyrillicFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    foreach ($relative in (Get-OrdinalSortedUnique $expectedRelative))
    {
        $text = Get-Content -LiteralPath (Join-Path $moduleRoot $relative) -Raw -Encoding UTF8
        foreach ($marker in $mojibakeMarkers)
        {
            if ($text.IndexOf($marker, [System.StringComparison]::Ordinal) -ge 0)
            {
                [void]$mojibakeFiles.Add($relative)
            }
        }
        foreach ($pattern in $escapedCyrillicPatterns)
        {
            if ($text -match $pattern)
            {
                [void]$escapedCyrillicFiles.Add($relative)
            }
        }
    }
    Add-Result "text.no-mojibake-markers" ($mojibakeFiles.Count -eq 0) (([string[]]$mojibakeFiles -join ",") -replace "^$", "0 matches")
    Add-Result "text.no-escaped-cyrillic" ($escapedCyrillicFiles.Count -eq 0) (([string[]]$escapedCyrillicFiles -join ",") -replace "^$", "0 matches")

    $invalidUtf8 = New-Object System.Collections.Generic.List[string]
    foreach ($relative in (Get-OrdinalSortedUnique $expectedRelative))
    {
        if (-not (Test-ValidUtf8 -Path (Join-Path $moduleRoot $relative)))
        {
            $invalidUtf8.Add($relative)
        }
    }
    Add-Result "text.valid-utf8" ($invalidUtf8.Count -eq 0) (($invalidUtf8 -join ",") -replace "^$", "all changed files")
    Add-Result "safety.no-db-network" $true "local Git/file checks only"
    Add-Result "safety.repository-read-only" $true "verifier performs no writes"
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
