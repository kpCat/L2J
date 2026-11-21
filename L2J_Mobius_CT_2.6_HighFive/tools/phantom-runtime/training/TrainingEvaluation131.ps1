[CmdletBinding(DefaultParameterSetName = 'Evaluate')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Evaluate')]
    [Parameter(Mandatory = $true, ParameterSetName = 'Admission')][string]$Manifest,
    [Parameter(Mandatory = $true, ParameterSetName = 'Evaluate')][string]$EvidenceRoot,
    [Parameter(Mandatory = $true, ParameterSetName = 'Evaluate')][string]$Output,
    [Parameter(Mandatory = $true, ParameterSetName = 'Admission')][switch]$Admission,
    [Parameter(Mandatory = $true, ParameterSetName = 'Admission')][string]$ExpectedManifestSha,
    [Parameter(ParameterSetName = 'Admission')][string]$PriorProtocol,
    [Parameter(ParameterSetName = 'Admission')][string]$PriorProtocolSha,
    [Parameter(Mandatory = $true, ParameterSetName = 'Fingerprint')][switch]$Fingerprint
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

function Get-EvaluationPhysicalDirectory([string]$Path) {
    $directory = Get-Item -LiteralPath $Path -Force
    if (!$directory.PSIsContainer) { throw 'TASK131 evaluation requires a physical directory.' }
    for ($part = $directory; $null -ne $part; $part = $part.Parent) {
        if ($part.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'TASK131 evaluation temporary directory contains a reparse point.'
        }
    }
    return $directory
}

function Get-EvaluationFileSha([Security.Cryptography.HashAlgorithm]$Hasher, [string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        return [BitConverter]::ToString($Hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
    }
}

if ($PSCmdlet.ParameterSetName -eq 'Evaluate') {
    foreach ($candidatePath in @($Manifest, $EvidenceRoot, $Output)) {
        if (![IO.Path]::IsPathRooted($candidatePath)) { throw 'TASK131 evaluation paths must be absolute.' }
    }
}
if ($PSCmdlet.ParameterSetName -eq 'Admission' -and (!$Admission -or ![IO.Path]::IsPathRooted($Manifest) -or
    $ExpectedManifestSha -cnotmatch '^[a-f0-9]{64}$')) {
    throw 'TASK131 admission requires the absolute frozen manifest and its SHA-256.'
}
if (($PSBoundParameters.ContainsKey('PriorProtocol') -or $PSBoundParameters.ContainsKey('PriorProtocolSha')) -and
    (![IO.Path]::IsPathRooted($PriorProtocol) -or $PriorProtocolSha -cnotmatch '^[a-f0-9]{64}$')) {
    throw 'TASK131 admission requires both the absolute prior protocol and its SHA-256.'
}

# Launcher environment options could change the compiled closure or inject runtime code outside it.
foreach ($name in @('JDK_JAVAC_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS')) {
    if (![string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "TASK131 evaluation requires an unmodified JDK launcher. Clear $name."
    }
}
$javac = (Get-Command javac -CommandType Application | Select-Object -First 1).Source
$version = & $javac -version
if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -cnotmatch '^javac 25(?:[.+-]\S+)?$') {
    throw 'TASK131 evaluation requires JDK 25.'
}
$java = Join-Path (Split-Path -Parent $javac) 'java.exe'
if (!(Test-Path -LiteralPath $java -PathType Leaf)) { throw 'TASK131 evaluation requires java from the same JDK as javac.' }

$module = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$gameSource = Join-Path $module 'java'
$testSource = Join-Path $module 'test/java'
$reader = Join-Path $testSource 'org/l2jmobius/tests/phantoms/PhantomLearning131Evaluation.java'
# Package-private ClanSocialPersistence shares its source file with ClanSocialRepository.
$sourcePeer = Join-Path $gameSource 'org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java'
$libraryRoot = Join-Path $module 'dist/libs'
$libraryNames = @('HikariCP-7.0.2.jar', 'mysql-connector-j-9.5.0.jar', 'slf4j-api-2.0.17.jar', 'slf4j-simple-2.0.17.jar')
$taskTempRoot = Join-Path (Split-Path -Parent $module) '.phantom-local/logs/TASK131'
$tempParent = if (Test-Path -LiteralPath $taskTempRoot -PathType Container) {
    (Get-EvaluationPhysicalDirectory $taskTempRoot).FullName
} else {
    (Get-EvaluationPhysicalDirectory ([IO.Path]::GetTempPath())).FullName
}
$tempName = 'Phantom-Task131-Evaluation-' + [guid]::NewGuid().ToString('N')
$tempPath = Join-Path $tempParent $tempName
if ($PSCmdlet.ParameterSetName -eq 'Evaluate') {
    $evidencePrefix = [IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ($tempPath.StartsWith($evidencePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        $tempParent = (Get-EvaluationPhysicalDirectory ([IO.Path]::GetTempPath())).FullName
        $tempPath = Join-Path $tempParent $tempName
        if ($tempPath.StartsWith($evidencePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'TASK131 evaluation temporary directory must be outside the evidence directory.'
        }
    }
}

$created = $false
$exitCode = 0
try {
    $null = New-Item -ItemType Directory -Path $tempPath
    $created = $true
    $classes = Join-Path $tempPath 'classes'
    $libraries = Join-Path $tempPath 'libs'
    $null = New-Item -ItemType Directory -Path $classes, $libraries
    $jarPaths = @(foreach ($name in $libraryNames) {
        $sourceJar = Get-Item -LiteralPath (Join-Path $libraryRoot $name) -Force
        if ($sourceJar.PSIsContainer -or ($sourceJar.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'TASK131 evaluation dependency must be a physical JAR.'
        }
        $copy = Join-Path $libraries $name
        [IO.File]::Copy($sourceJar.FullName, $copy)
        $copy
    })

    $separator = [IO.Path]::PathSeparator
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $cliSha = Get-EvaluationFileSha $hasher $PSCommandPath
        Push-Location -LiteralPath $tempPath
        try {
            & $javac --release 25 -encoding UTF-8 -proc:none -g:none -sourcepath (@($gameSource, $testSource) -join $separator) `
                -classpath ($jarPaths -join $separator) -d $classes $reader $sourcePeer
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        if ($exitCode -eq 0) {
            if (!(Test-Path -LiteralPath (Join-Path $classes 'org/l2jmobius/tests/phantoms/PhantomLearning131Evaluation.class') -PathType Leaf)) {
                throw 'TASK131 evaluation class was not produced.'
            }
            if ($cliSha -cne (Get-EvaluationFileSha $hasher $PSCommandPath)) {
                throw 'TASK131 evaluation launcher changed during compilation.'
            }
            [string[]]$entries = @(
                foreach ($path in [IO.Directory]::EnumerateFiles($classes, '*.class', [IO.SearchOption]::AllDirectories)) {
                    $relative = $path.Substring($classes.Length + 1).Replace('\', '/')
                    "class`t$relative`t$(Get-EvaluationFileSha $hasher $path)"
                }
                foreach ($path in $jarPaths) {
                    "jar`t$([IO.Path]::GetFileName($path))`t$(Get-EvaluationFileSha $hasher $path)"
                }
                "cli`tTrainingEvaluation131.ps1`t$cliSha"
            )
            [Array]::Sort($entries, [StringComparer]::Ordinal)
            $closure = "TASK131_EVALUATOR_CLOSURE_V1`n" + ($entries -join "`n") + "`n"
            $evaluatorSha = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($closure))).Replace('-', '').ToLowerInvariant()
            if ($PSCmdlet.ParameterSetName -eq 'Fingerprint') {
                Write-Output $evaluatorSha
            } else {
                $classPath = (@($classes) + $jarPaths) -join $separator
                $evaluationArguments = if ($PSCmdlet.ParameterSetName -eq 'Admission') {
                    '--admission', $Manifest, $ExpectedManifestSha, $evaluatorSha
                    if ($PriorProtocol) { $PriorProtocol, $PriorProtocolSha }
                } else {
                    @($Manifest, $EvidenceRoot, $Output, $evaluatorSha)
                }
                & $java -classpath $classPath org.l2jmobius.tests.phantoms.PhantomLearning131Evaluation @evaluationArguments
                $exitCode = $LASTEXITCODE
            }
        }
    } finally {
        $hasher.Dispose()
    }
} finally {
    if ($created) {
        $owned = Get-EvaluationPhysicalDirectory $tempPath
        if (![string]::Equals($owned.FullName, $tempPath, [StringComparison]::OrdinalIgnoreCase) -or
            ![string]::Equals($owned.Parent.FullName.TrimEnd('\', '/'), $tempParent.TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase) -or
            $owned.Name -cne $tempName) {
            throw 'TASK131 evaluation temporary cleanup refused an unexpected path.'
        }
        $aliases = @(Get-ChildItem -LiteralPath $owned.FullName -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
        if ($aliases.Count -ne 0) { throw 'TASK131 evaluation temporary cleanup refused a reparse point.' }
        Remove-Item -LiteralPath $owned.FullName -Recurse -Force
    }
}

exit $exitCode
