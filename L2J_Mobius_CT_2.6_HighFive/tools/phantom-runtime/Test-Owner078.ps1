[CmdletBinding()]
param([string]$RuntimeRoot = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$builder = Join-Path $PSScriptRoot 'Build-Owner078.ps1'
if (!(Test-Path -LiteralPath $builder)) { throw '078 safety contract absent: isolated builder required.' }
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('owner078-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    $marker = Join-Path $scratch 'Путь с пробелом'
    New-Item -ItemType Directory -Path $marker | Out-Null
    $sentinel = Join-Path $marker 'owner.txt'
    [IO.File]::WriteAllText($sentinel, 'preserve-owner-data')
    $before = (Get-FileHash -LiteralPath $sentinel).Hash
    $rejected = $false
    try { & $builder -OutputRoot $marker -StageRoot (Join-Path $scratch 'stage') -ValidateOnly }
    catch { if ($_.Exception.Message -notmatch 'OUTPUT_EXISTS') { throw }; $rejected = $true }
    if (!$rejected -or (Get-FileHash -LiteralPath $sentinel).Hash -cne $before) { throw 'Existing output was accepted or changed.' }
    if ($RuntimeRoot) {
        $fixture = Join-Path $marker 'tools'
        New-Item -ItemType Directory -Path $fixture | Out-Null
        $stub = 'param($Action,$RuntimeRoot)' + "`r`n" + 'if ([IO.Path]::GetFullPath($RuntimeRoot).TrimEnd(''\'') -ine [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ''..'')).TrimEnd(''\'')) { exit 91 }; exit 37'
        [IO.File]::WriteAllText((Join-Path $fixture 'Runtime.ps1'), $stub, [Text.Encoding]::ASCII)
        foreach ($action in @('START','STATUS','STOP')) {
            $wrapper = Join-Path $marker ($action+'_078.cmd')
            Copy-Item -LiteralPath (Join-Path $RuntimeRoot ($action+'_078.cmd')) -Destination $wrapper
            $null = & cmd.exe /d /c ('call "'+$wrapper+'" <nul')
            if ($LASTEXITCODE -ne 37) { throw "Wrapper path or exit code failed: $action/$LASTEXITCODE" }
        }
    }
    Write-Output 'PASS existing output fails closed; sentinel unchanged'
    $runtime = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Runtime.ps1'))
    if ($runtime -match 'Stop-Process|taskkill|CloseMainWindow' -or $runtime -notmatch 'RequestShutdown') { throw 'Stock shutdown contract broken.' }
    Write-Output 'PASS stock shutdown only; no force fallback'
    . (Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1')
    $foreign = Resolve-LocalPlayOwnership 'GameServer' ('a' * 64) 'GameServer.jar' @(7777) $null @() @{7777=99999}
    if ($foreign.state -cne 'FOREIGN_PORT_OWNER' -or $foreign.recordVerified) { throw 'Foreign port accepted.' }
    $process = [pscustomobject]@{role='GameServer';runtime=('a'*64);jar='GameServer.jar';pid=99999;startTimeUtcTicks=100}
    $badRecord = [pscustomobject]@{format=2;role='GameServer';runtime=('a'*64);jar='GameServer.jar';pid=99998;startTimeUtcTicks=100}
    $unknown = Resolve-LocalPlayOwnership 'GameServer' ('a'*64) 'GameServer.jar' @(7777) $badRecord @($process) @{7777=99999}
    if ($unknown.recordVerified) { throw 'Bad launch manifest accepted for STOP.' }
    Write-Output 'PASS foreign port owner rejected without process action'
} finally {
    # Only this invocation created this random temporary directory.
    if ([IO.Path]::GetFullPath($scratch).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()), [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $scratch -Recurse -Force
    }
}
