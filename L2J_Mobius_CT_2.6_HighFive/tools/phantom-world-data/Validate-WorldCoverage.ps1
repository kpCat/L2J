[CmdletBinding()]
param(
    [string]$DataRoot = '',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
if ($DataRoot -eq '') { $DataRoot = Join-Path $PSScriptRoot '../../dist/game/data' }
if ($OutputDirectory -eq '') { $OutputDirectory = Join-Path $PSScriptRoot '../../docs/phantoms/live-world' }
$dataPath = [IO.Path]::GetFullPath($DataRoot)
$manifest = Get-Content (Join-Path $OutputDirectory 'WORLD_DATA_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$rows = @(Import-Csv (Join-Path $OutputDirectory 'WORLD_COVERAGE.tsv') -Delimiter "`t" -Encoding UTF8)
if ($rows.Count -ne $manifest.counters.emitted_rows -or $rows.Count -ne $manifest.counters.discovered_spawn_groups) { throw 'Row-count mismatch.' }
if ($manifest.counters.duplicate_coverage_keys -ne 0 -or @($rows.coverage_key | Select-Object -Unique).Count -ne $rows.Count) { throw 'Duplicate coverage key.' }
if ((Get-FileHash (Join-Path $OutputDirectory 'WORLD_COVERAGE.tsv') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.output_sha256) { throw 'Output hash mismatch.' }
if ((Get-FileHash (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.generator_sha256) { throw 'Generator hash mismatch.' }
foreach ($entry in $manifest.input_files)
{
    $file = Join-Path $dataPath ($entry.path.Substring(5).Replace('/',[IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing input: $($entry.path)" }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw "Input hash mismatch: $($entry.path)" }
}
$nativeGroups = 0
foreach ($entry in $manifest.input_files)
{
    if (($entry.root -ne 'data/spawns') -and ($entry.root -ne 'data/instances')) { continue }
    $file = Join-Path $dataPath ($entry.path.Substring(5).Replace('/',[IO.Path]::DirectorySeparatorChar))
    $doc = New-Object Xml.XmlDocument
    $doc.XmlResolver = $null
    $doc.Load($file)
    if ($entry.root -eq 'data/spawns') { $nativeGroups += $doc.SelectNodes('/list/spawn').Count }
    else { $nativeGroups += $doc.SelectNodes('/instance/spawnlist/group').Count }
}
if ($nativeGroups -ne $rows.Count) { throw "Native group mismatch: $nativeGroups vs $($rows.Count)." }
foreach ($row in $rows)
{
    if ($row.status -eq 'READY_STATIC')
    {
        if (($row.classification -ne 'ORDINARY_WORLD') -or ($row.instance_id -ne '0') -or ([int]$row.configured_amount -le 0) -or ($row.npc_types -ne 'Monster') -or ([int]$row.attackable_npc_count -le 0) -or ([int]$row.targetable_npc_count -le 0) -or ($row.sample_x -eq '') -or ($row.sample_y -eq '')) { throw "Invalid READY_STATIC row: $($row.coverage_key)" }
    }
    if (($row.classification -eq 'INSTANCE') -and $row.status -ne 'INSTANCE') { throw "Instance status mismatch: $($row.coverage_key)" }
    if (($row.classification -eq 'RAID') -and $row.status -ne 'RAID') { throw "Raid status mismatch: $($row.coverage_key)" }
    if (($row.classification -eq 'EVENT_OR_SCRIPTED') -and $row.status -eq 'READY_STATIC') { throw "Event marked ready: $($row.coverage_key)" }
}
foreach ($class in @('ORDINARY_WORLD','CONDITIONAL_WORLD','INSTANCE','RAID','EVENT_OR_SCRIPTED','NON_FARMING','UNRESOLVED'))
{
    if (@($rows | Where-Object classification -eq $class).Count -ne $manifest.classification_counts.$class) { throw "Class count mismatch: $class" }
}
foreach ($status in @('READY_STATIC','NEEDS_GEODATA','CONDITIONAL','INSTANCE','RAID','EXCLUDED','BLOCKED_SOURCE'))
{
    if (@($rows | Where-Object status -eq $status).Count -ne $manifest.status_counts.$status) { throw "Status count mismatch: $status" }
}
"WORLD_COVERAGE production GREEN: native_groups=$nativeGroups rows=$($rows.Count) inputs=$($manifest.input_file_count)"
