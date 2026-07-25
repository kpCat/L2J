[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [long]$Seed = 20260725001,
    [string]$TestDatabase = "l2jmobiush5_phantom_test"
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

    $output = @(& git -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0)
    {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE`: $($output -join [Environment]::NewLine)"
    }

    return [string[]]$output
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

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootOutput = @(Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel"))
    $gitRoot = $gitRootOutput[0]
    $gitRoot = (Resolve-Path $gitRoot).Path
    $expectedModule = Join-Path $gitRoot "L2J_Mobius_CT_2.6_HighFive"

    Add-Result "repository.git-root" ($gitRoot -eq "C:\Users\endim\L2J_Mobius") $gitRoot
    Add-Result "repository.module-root" ($moduleRoot -eq $expectedModule) $moduleRoot

    $currentBranchOutput = @(Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current"))
    $currentBranch = $currentBranchOutput[0]
    Add-Result "repository.branch" ($currentBranch -eq $Branch) $currentBranch

    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $auditRelative = "docs/phantoms/audits/001-baseline-architecture-audit"
    $requiredRelative = @(
        "$auditRelative/BASELINE.md",
        "$auditRelative/BASELINE_MANIFEST.json",
        "$auditRelative/CURRENT_SYSTEM_AUDIT.md",
        "$auditRelative/DEPENDENCY_MAP.md",
        "$auditRelative/HEADLESS_PLAYER_FEASIBILITY.md",
        "$auditRelative/NEXT_TASK_GATES.md",
        "docs/phantoms/adr/0001-headless-player-integration-seam.md",
        "docs/phantoms/reports/001-baseline-architecture-audit.md",
        "tools/phantoms/verify-task-001.ps1"
    )

    foreach ($relative in (Get-OrdinalSortedUnique $requiredRelative))
    {
        $path = Join-Path $moduleRoot $relative
        Add-Result "artifact.$relative" (Test-Path -LiteralPath $path -PathType Leaf) $relative
    }

    $manifestPath = Join-Path $moduleRoot "$auditRelative/BASELINE_MANIFEST.json"
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Add-Result "manifest.schema-version" ($manifest.schemaVersion -eq 1) ([string]$manifest.schemaVersion)
    Add-Result "manifest.task-id" ($manifest.taskId -ceq "001-baseline-architecture-audit") ([string]$manifest.taskId)
    Add-Result "manifest.seed" ($manifest.auditSeed -eq $Seed) ([string]$manifest.auditSeed)
    Add-Result "manifest.production-db" ($manifest.databaseContract.productionDatabase -ceq "l2jmobiush5") ([string]$manifest.databaseContract.productionDatabase)
    Add-Result "manifest.test-db" ($manifest.databaseContract.testDatabase -ceq $TestDatabase) ([string]$manifest.databaseContract.testDatabase)
    Add-Result "manifest.no-db-connection" ($manifest.databaseContract.databaseConnectionPerformed -eq $false) ([string]$manifest.databaseContract.databaseConnectionPerformed)
    Add-Result "manifest.no-db-mutation" ($manifest.databaseContract.databaseMutationPerformed -eq $false) ([string]$manifest.databaseContract.databaseMutationPerformed)
    Add-Result "manifest.branch" ($manifest.repository.workBranch -ceq $Branch) ([string]$manifest.repository.workBranch)

    $allowedVerdicts = @("FEASIBLE", "FEASIBLE_WITH_SEAM", "NOT_FEASIBLE_WITHOUT_PLAN_CHANGE")
    Add-Result "manifest.gate-verdict" ($allowedVerdicts -ccontains $manifest.gateVerdict) ([string]$manifest.gateVerdict)
    Add-Result "manifest.actual-sha" (
        ([string]$manifest.repository.originMasterAtTaskStart -match "^[0-9a-f]{40}$") -and
        ([string]$manifest.repository.headAtTaskStart -match "^[0-9a-f]{40}$")
    ) "$($manifest.repository.originMasterAtTaskStart)/$($manifest.repository.headAtTaskStart)"

    $expectedTargets = @("adding-core", "adding-datapack", "adding-readme", "checkRequirements", "cleanup", "compile", "init", "jar")
    $actualTargets = Get-OrdinalSortedUnique ([string[]]$manifest.build.targets)
    Add-Result "manifest.sorted-targets" (($actualTargets -join "|") -ceq ($expectedTargets -join "|")) ($actualTargets -join ",")
    Add-Result "manifest.jar-exit" ($manifest.build.jarExitCode -eq 0) ([string]$manifest.build.jarExitCode)
    Add-Result "manifest.jar-copies" (($manifest.build.gameServerJarCopied -eq $true) -and ($manifest.build.loginServerJarCopied -eq $true)) "game=$($manifest.build.gameServerJarCopied),login=$($manifest.build.loginServerJarCopied)"

    $manifestText = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
    Add-Result "manifest.no-placeholders" ($manifestText -notmatch "<(sha|text|true\|false|enabled\|disabled|FEASIBLE)") "no schema placeholders"

    $headingChecks = @(
        @("$auditRelative/BASELINE.md", @("# BASELINE", "## Git baseline", "## Environment", "## Build", "## Database isolation contract", "## SHA-256 evidence")),
        @("$auditRelative/CURRENT_SYSTEM_AUDIT.md", @("# CURRENT SYSTEM AUDIT", "## Built-in Fake Players", "## Player creation, restoration and state", "## GameClient and network boundary", "## Enter and leave lifecycle", "## Offline play and offline trade evidence", "## Gameplay subsystem audit", "## Build and testability")),
        @("$auditRelative/DEPENDENCY_MAP.md", @("# DEPENDENCY MAP", "## Lifecycle graph", "## Client-coupling matrix", "## Gameplay subsystem matrix", "## Persistence and transaction map", "## Thread and task ownership map", "## Failure and rollback points")),
        @("$auditRelative/HEADLESS_PLAYER_FEASIBILITY.md", @("# HEADLESS PLAYER FEASIBILITY", "## Gate verdict", "## Minimal seam", "## Alternatives A", "## PhantomActionFacade boundary", "## Lifecycle and rollback", "## Task 004 automated spike")),
        @("$auditRelative/NEXT_TASK_GATES.md", @("# NEXT TASK GATES", "## Task 002", "## Task 003", "## Task 004", "## Risks that cannot pass Task 004")),
        @("docs/phantoms/adr/0001-headless-player-integration-seam.md", @("# ADR 0001", "## Status", "Proposed", "## Context", "## Decision", "## Invariants", "## Alternatives", "## Consequences", "## Risks", "## Validation plan", "## Rollback", "## Supersession condition")),
        @("docs/phantoms/reports/001-baseline-architecture-audit.md", @("# Codex report", "## Status", "SUCCESS", "## Gate verdict", "## Evidence index", "## Scope verification", "## Database safety", "## Determinism", "## Verifier runs", "## Git"))
    )

    foreach ($check in $headingChecks)
    {
        $relative = [string]$check[0]
        $tokens = [string[]]$check[1]
        Add-Result "content.$relative" (Test-ContainsAll -Path (Join-Path $moduleRoot $relative) -Tokens $tokens) ($tokens -join ",")
    }

    $dependencyPath = Join-Path $moduleRoot "$auditRelative/DEPENDENCY_MAP.md"
    $subsystems = @(
        "Party", "Command channel", "Clan/alliance/war", "Direct trade",
        "Private sell/buy/manufacture", "NPC buy/sell/multisell",
        "Inventory/item transfer/reservation", "Mail", "Quest/timers",
        "Instance", "PvP/PK/karma/drop", "Death/resurrection",
        "Siege/fort/territory war", "Raid/epic", "Chat/PM/trade chat",
        "Skills/shots/autouse/autoplay", "Teleport/navigation/geodata",
        "Global ThreadPool/task managers"
    )
    Add-Result "content.subsystem-matrix" (Test-ContainsAll -Path $dependencyPath -Tokens $subsystems) "18 required subsystem rows"

    $committed = Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "origin/master...HEAD")
    $trackedWork = Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "origin/master")
    $untracked = Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")
    $changed = Get-OrdinalSortedUnique ([string[]]($committed + $trackedWork + $untracked))

    $modulePrefix = "$relativeModule/"
    $allowedPatterns = @(
        "^$([regex]::Escape($modulePrefix))docs/phantoms/tasks/001-baseline-architecture-audit/",
        "^$([regex]::Escape($modulePrefix))docs/phantoms/audits/001-baseline-architecture-audit/",
        "^$([regex]::Escape($modulePrefix))docs/phantoms/adr/0001-headless-player-integration-seam\.md$",
        "^$([regex]::Escape($modulePrefix))docs/phantoms/reports/001-baseline-architecture-audit\.md$",
        "^$([regex]::Escape($modulePrefix))tools/phantoms/verify-task-001\.ps1$"
    )

    $scopeViolations = New-Object System.Collections.Generic.List[string]
    foreach ($path in $changed)
    {
        $allowed = $false
        foreach ($pattern in $allowedPatterns)
        {
            if ($path -match $pattern)
            {
                $allowed = $true
                break
            }
        }

        if (-not $allowed)
        {
            $scopeViolations.Add($path)
        }
    }

    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "scope.allowlist" ($scopeViolations.Count -eq 0) (($scopeViolations -join ",") -replace "^$", "no violations")
    Add-Result "scope.target-module-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "no other chronicles"
    Add-Result "scope.no-production-java" (@($changed | Where-Object { $_ -match "\.java$" }).Count -eq 0) "no Java changes"
    Add-Result "scope.no-build-xml" (@($changed | Where-Object { $_ -match "(^|/)build\.xml$" }).Count -eq 0) "build.xml unchanged"
    Add-Result "scope.no-runtime-config-data-sql" (@($changed | Where-Object { $_ -match "/dist/game/(config|data)/|/dist/db_installer/|\.sql$" }).Count -eq 0) "no runtime config/data/SQL"
    Add-Result "scope.no-binary-build-log" (@($changed | Where-Object { $_ -match "\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binary/build/log artifacts"

    $auditFiles = Get-OrdinalSortedUnique ($requiredRelative | Where-Object { $_ -notlike "tools/*" })
    $secretMatches = New-Object System.Collections.Generic.List[string]
    foreach ($relative in $auditFiles)
    {
        $text = Get-Content -LiteralPath (Join-Path $moduleRoot $relative) -Raw -Encoding UTF8
        if ($text -match "(?im)^\s*(Password|Login)\s*=\s*\S+" -or $text -match "root/root")
        {
            $secretMatches.Add($relative)
        }
    }
    Add-Result "safety.no-credentials" ($secretMatches.Count -eq 0) (($secretMatches -join ",") -replace "^$", "no credentials")
    Add-Result "safety.verifier-no-db-network" $true "static file/git checks only"
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
