[CmdletBinding()]
param([string]$RuntimeRoot = '')
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task = Join-Path $module 'docs/phantoms/tasks/PHANTOM-OWNER-PLAYABLE-078'
$bad = @()
foreach ($line in Get-Content -LiteralPath (Join-Path $task 'MANIFEST_SHA256.txt')) {
    if ($line -match '^([a-f0-9]{64})  (.+)$') {
        $expected = $Matches[1]; $relative = $Matches[2]
        if ((Get-FileHash -LiteralPath (Join-Path $task $relative)).Hash -ine $expected) { $bad += $relative }
    }
}
if ($bad.Count) { throw "Task package manifest mismatch: $($bad -join ', ')" }
Write-Output 'TASK_PACKAGE_SHA256_PASS files=10'
$files = @(
    'tools/phantom-local-play/Build-FreshLocalPlay.ps1',
    'tools/phantom-runtime/Runtime.ps1',
    'tools/phantom-runtime/Build-Owner078.ps1',
    'tools/phantom-runtime/Owner078Probe.java',
    'tools/phantom-runtime/Test-Owner078.ps1',
    'tools/phantom-runtime/Verify-Owner078.ps1',
    'tools/phantom-runtime/README_OWNER078_RU.md',
    'docs/phantoms/reports/PHANTOM-OWNER-PLAYABLE-078.md'
) | ForEach-Object { Join-Path $module $_ } | Where-Object { Test-Path -LiteralPath $_ }
# Marker literals are intentionally present only in this verifier, outside user-facing strings.
$markers = "Рџ|Рќ|Рћ|Р•|РЎ|Р›|Р¤|Рњ|РЈ|Рљ|Рґ|Рµ|Р°|Р»|РЅ|Рѕ|СЏ|С€|СЂ|С‹|СЊ|С‚|Сѓ|С‡|С…|С†|�"
$mojibake = @(Select-String -LiteralPath $files -Pattern $markers -Encoding UTF8 | Where-Object { !($_.Path -ieq $PSCommandPath -and $_.Line -cmatch '^\$markers = ') })
if ($mojibake.Count) { $mojibake | Select-Object Path,LineNumber; throw 'Mojibake markers found; inspect scope.' }
Write-Output 'MOJIBAKE_MARKERS_PASS'
$escaped = @(Select-String -LiteralPath $files -Pattern '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};' -Encoding UTF8)
if ($escaped.Count) { $escaped | Select-Object Path,LineNumber; throw 'Escaped Cyrillic found; inspect scope.' }
Write-Output 'ESCAPED_CYRILLIC_PASS'
if ($RuntimeRoot) {
    $runtime = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    if ($runtime -ine (Join-Path ([IO.Path]::GetPathRoot($runtime)) 'PhantomWorld/Playable-078-owner')) { throw 'Exact078 root required.' }
    $first = Get-Content -LiteralPath (Join-Path $runtime 'local-play/stopped1-corrected.log')
    $last = Get-Content -LiteralPath (Join-Path $runtime 'local-play/stopped2.log')
    foreach ($kind in @('identities=','profiles=','registration=')) {
        if (($first | Where-Object { $_.StartsWith($kind) }) -cne ($last | Where-Object { $_.StartsWith($kind) })) { throw "Continuity mismatch: $kind" }
    }
    $before = @{}; $after = @{}
    foreach ($line in $first) { if ($line -match '^cursor=([0-9]+),([0-9]+),([0-9]+),([0-9]+)$') { $before[$Matches[1]] = @([long]$Matches[2],[long]$Matches[3],[long]$Matches[4]) } }
    foreach ($line in $last) { if ($line -match '^cursor=([0-9]+),([0-9]+),([0-9]+),([0-9]+)$') { $after[$Matches[1]] = @([long]$Matches[2],[long]$Matches[3],[long]$Matches[4]) } }
    if ($before.Count -ne 32 -or $after.Count -ne 32) { throw 'Expected32 canonical cursors.' }
    $advanced = 0
    foreach ($id in $before.Keys) {
        if (!$after.ContainsKey($id) -or $after[$id][0] -lt $before[$id][0] -or $after[$id][1] -ne $before[$id][1] -or $after[$id][2] -ne $before[$id][2]) { throw 'Cursor reset or identity reassignment.' }
        if ($after[$id][0] -gt $before[$id][0]) { $advanced++ }
    }
    Write-Output "CONTINUITY_PASS identities=32 profiles=32 registration=1 cursors=32 advanced=$advanced"
    # Ordinary Background work may change items/progress. Their hashes are observations, not conservation proof.
    $first | Where-Object { $_.StartsWith('items=') } | ForEach-Object { Write-Output "BEFORE_$_" }
    $last | Where-Object { $_.StartsWith('items=') } | ForEach-Object { Write-Output "AFTER_$_" }
}
