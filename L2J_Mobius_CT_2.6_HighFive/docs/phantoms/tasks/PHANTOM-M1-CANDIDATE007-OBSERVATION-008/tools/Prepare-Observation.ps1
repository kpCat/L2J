[CmdletBinding()]
param([ValidateSet('Freeze','VerifyCommit')][string]$Phase, [Parameter(Mandatory)][string]$Repository)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = Join-Path $Repository 'L2J_Mobius_CT_2.6_HighFive'
$task = Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-CANDIDATE007-OBSERVATION-008'
$candidate = Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-LIVING-WORLD-CLOSEOUT-007/candidate-paused'
$manifest = Join-Path $candidate 'SOURCE_MANIFEST.tsv'
if ((Get-FileHash $manifest).Hash -cne '5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350') { throw 'Manifest hash mismatch.' }
$rows = @(Import-Csv $manifest -Delimiter "`t")
$integrated = @($rows | Where-Object state -like 'INTEGRATED_*')
$groups = @($rows | Group-Object state | Select-Object Name,Count)
$groups | Format-Table
if ($rows.Count -ne 161 -or $integrated.Count -ne 103) { throw 'Manifest count mismatch.' }
function Safe-Path([string]$Root,[string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or ($Relative -split '[/\\]' -contains '..')) { throw 'Unsafe relative path.' }
    $full = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (-not $full.StartsWith([IO.Path]::GetFullPath($Root).TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside root.' }
    $walk = $full
    while ($walk.Length -ge $Root.Length) {
        if ((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Reparse path forbidden.' }
        $walk = Split-Path -Parent $walk
    }
    return $full
}
foreach ($row in $rows) {
    $source = Safe-Path $candidate $row.artifact_path
    if ((Get-Item $source).Length -ne [long]$row.bytes -or (Get-FileHash $source).Hash -cne $row.sha256) { throw "Artifact mismatch: $($row.module_path)" }
}
if ($Phase -eq 'Freeze') {
    foreach ($row in $integrated) {
        $destination = Safe-Path $module $row.module_path
        $source = Safe-Path $candidate $row.artifact_path
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-FileHash $destination).Hash -cne $row.sha256) { throw 'Destination hash mismatch.' }
    }
    $integrated | Export-Csv (Join-Path $task 'CANDIDATE_SOURCE.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    $publication = @($integrated | ForEach-Object { 'L2J_Mobius_CT_2.6_HighFive/'+$_.module_path })
    $publication += @(Get-ChildItem $task -File -Recurse | ForEach-Object { $_.FullName.Substring($Repository.TrimEnd('\').Length+1).Replace('\','/') })
    $private = Join-Path $module '.phantom-local/observe008'
    New-Item -ItemType Directory -Path $private -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $private 'snapshot-paths.nul'),($publication -join [char]0)+[char]0,[Text.UTF8Encoding]::new($false))
    Write-Output 'Verified 161 artifacts, overlaid 103 integrated entries, skipped 58 pending.'
} else {
    $export = Join-Path $module '.phantom-local/observe008/blob-check'
    New-Item -ItemType Directory -Path $export -Force | Out-Null
    foreach ($row in $integrated) {
        $info = [Diagnostics.ProcessStartInfo]::new('git',('show HEAD:L2J_Mobius_CT_2.6_HighFive/'+$row.module_path))
        $info.WorkingDirectory = $Repository
        $info.UseShellExecute = $false
        $info.RedirectStandardOutput = $true
        $process = [Diagnostics.Process]::Start($info)
        $file = Join-Path $export 'blob.bin'
        $stream = [IO.File]::Create($file)
        try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or (Get-FileHash $file).Hash -cne $row.sha256) { throw "Committed blob mismatch: $($row.module_path)" }
        if ((Get-FileHash (Safe-Path $module $row.module_path)).Hash -cne $row.sha256) { throw 'Source tree mismatch.' }
    }
    Write-Output 'Verified 103 committed blob and source tree SHA256 hashes.'
}
