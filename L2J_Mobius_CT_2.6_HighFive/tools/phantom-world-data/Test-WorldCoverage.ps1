[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Join-Path ([IO.Path]::GetTempPath()) ('world-coverage-fixture-' + [guid]::NewGuid().ToString('N'))
try
{
    $data = Join-Path $root 'data'
    foreach ($part in @('spawns/Test', 'stats/npcs', 'mapregion', 'teleporters', 'zones', 'instances'))
    {
        [void](New-Item -ItemType Directory -Path (Join-Path $data $part) -Force)
    }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $data 'stats/npcs/test.xml'), '<list><npc id="1" level="5" type="Monster"/><npc id="2" level="9" type="RaidBoss"/><npc id="3" level="1" type="Folk"><status attackable="false" targetable="true"/></npc><npc id="4" level="6" type="Monster"/><npc id="5" level="7" type="EventMonster"/></list>', $utf8)
    $sourceFile = Join-Path $data 'spawns/Test/a.xml'
    $sourceXml = '<list enabled="true"><spawn zone="field"><territory minZ="-10" maxZ="10"><node x="10" y="20"/><node x="30" y="20"/><node x="20" y="30"/></territory><npc id="1" count="2"/><npc id="4" count="1" x="21" y="22" z="0"/></spawn><spawn name="night"><npc id="1" x="40" y="50" z="0" periodOfDay="night"/></spawn><spawn name="raid"><npc id="2" x="60" y="70" z="0"/></spawn><spawn name="event"><npc id="5" x="61" y="71" z="0"/></spawn><spawn name="folk"><npc id="3" x="62" y="72" z="0"/></spawn><spawn name="missing"><npc id="99" x="80" y="90" z="0"/></spawn><spawn name="unknown"><portal id="1"/></spawn></list>'
    [IO.File]::WriteAllText($sourceFile, $sourceXml, $utf8)
    [IO.File]::WriteAllText((Join-Path $data 'instances/i.xml'), '<instance id="7"><spawnlist><group name="general"><npc id="1" x="100" y="200" z="300"/></group></spawnlist></instance>', $utf8)
    foreach ($part in @('mapregion','teleporters','zones')) { [IO.File]::WriteAllText((Join-Path $data "$part/empty.xml"), '<list/>', $utf8) }
    $out1 = Join-Path $root 'out1'
    $out2 = Join-Path $root 'out2'
    & (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -DataRoot $data -OutputDirectory $out1
    $rows = Import-Csv (Join-Path $out1 'WORLD_COVERAGE.tsv') -Delimiter "`t"
    if ($rows.Count -ne 8) { throw "Expected eight rows, got $($rows.Count)." }
    foreach ($expectation in @(@('field','ORDINARY_WORLD','READY_STATIC'), @('night','CONDITIONAL_WORLD','CONDITIONAL'), @('raid','RAID','RAID'), @('event','EVENT_OR_SCRIPTED','EXCLUDED'), @('folk','NON_FARMING','EXCLUDED'), @('missing','UNRESOLVED','BLOCKED_SOURCE'), @('unknown','UNRESOLVED','BLOCKED_SOURCE'), @('general','INSTANCE','INSTANCE')))
    {
        $match = @($rows | Where-Object { $_.source_group -eq $expectation[0] })
        if (($match.Count -ne 1) -or ($match[0].classification -ne $expectation[1]) -or ($match[0].status -ne $expectation[2])) { throw "Classification mismatch: $($expectation -join '/')." }
    }
    $field = @($rows | Where-Object source_group -eq 'field')[0]
    if (($field.geometry_kind -ne 'MIXED') -or ($field.npc_ids -ne '1|4') -or ($field.sample_x -ne '10') -or ($field.sample_z -ne '')) { throw 'Mixed geometry or canonical NPC list mismatch.' }
    $swapped = $sourceXml.Replace('<npc id="1" count="2"/><npc id="4" count="1" x="21" y="22" z="0"/>','<npc id="4" count="1" x="21" y="22" z="0"/><npc id="1" count="2"/>')
    [IO.File]::WriteAllText($sourceFile, $swapped, $utf8)
    & (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -DataRoot $data -OutputDirectory (Join-Path $root 'reordered') | Out-Null
    $reorderedField = @(Import-Csv (Join-Path $root 'reordered/WORLD_COVERAGE.tsv') -Delimiter "`t" | Where-Object source_group -eq 'field')[0]
    if ($reorderedField.coverage_key -ne $field.coverage_key) { throw 'NPC ordering changed coverage key.' }
    Move-Item -LiteralPath $sourceFile -Destination (Join-Path $data 'spawns/Test/temp.xml')
    $upperFile = Join-Path $data 'spawns/Test/A.xml'
    Move-Item -LiteralPath (Join-Path $data 'spawns/Test/temp.xml') -Destination $upperFile
    & (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -DataRoot $data -OutputDirectory (Join-Path $root 'pathcase') | Out-Null
    $caseField = @(Import-Csv (Join-Path $root 'pathcase/WORLD_COVERAGE.tsv') -Delimiter "`t" | Where-Object source_group -eq 'field')[0]
    if ($caseField.coverage_key -ne $field.coverage_key) { throw 'Path case changed coverage key.' }
    Move-Item -LiteralPath $upperFile -Destination (Join-Path $data 'spawns/Test/temp.xml')
    Move-Item -LiteralPath (Join-Path $data 'spawns/Test/temp.xml') -Destination $sourceFile
    [IO.File]::WriteAllText($sourceFile, $sourceXml, $utf8)
    & (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -DataRoot $data -OutputDirectory $out2
    foreach ($name in @('WORLD_COVERAGE.tsv','WORLD_DATA_MANIFEST.json'))
    {
        if ((Get-FileHash (Join-Path $out1 $name) -Algorithm SHA256).Hash -ne (Get-FileHash (Join-Path $out2 $name) -Algorithm SHA256).Hash) { throw "Determinism mismatch: $name." }
    }
    $duplicate = $sourceXml.Replace('</list>', '<spawn name="folk"><npc id="3" x="62" y="72" z="0"/></spawn></list>')
    [IO.File]::WriteAllText($sourceFile, $duplicate, $utf8)
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1') -DataRoot $data -OutputDirectory (Join-Path $root 'duplicate') | Out-Null
    if ($LASTEXITCODE -ne 1) { throw 'Duplicate stable key was not rejected.' }
    $duplicateManifest = Get-Content (Join-Path $root 'duplicate/WORLD_DATA_MANIFEST.json') -Raw | ConvertFrom-Json
    if ($duplicateManifest.counters.duplicate_coverage_keys -ne 1) { throw 'Duplicate-key count mismatch.' }
    'WORLD_COVERAGE fixture GREEN'
}
finally
{
    if (Test-Path $root) { Remove-Item -LiteralPath $root -Recurse -Force }
}
