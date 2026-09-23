[CmdletBinding()]
param(
    [string]$DataRoot = '',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
if ($DataRoot -eq '') { $DataRoot = Join-Path $PSScriptRoot '../../dist/game/data' }
if ($OutputDirectory -eq '') { $OutputDirectory = Join-Path $PSScriptRoot '../../docs/phantoms/live-world' }
$dataPath = [IO.Path]::GetFullPath($DataRoot)
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
$utf8 = New-Object System.Text.UTF8Encoding($false)
$sha = [Security.Cryptography.SHA256]::Create()
$roots = @('spawns','stats/npcs','mapregion','teleporters','zones','instances')
$columns = @('coverage_key','source_path','source_group','classification','status','reason','instance_id','condition_summary','npc_ids','npc_types','npc_level_min','npc_level_max','attackable_npc_count','targetable_npc_count','configured_amount','respawn_summary','geometry_kind','geometry_fingerprint','sample_x','sample_y','sample_z','map_region','zone_refs','topology_candidate','source_sha256')

function Hash-Text([string]$value)
{
    $bytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value))
    return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
}
function Sorted([string[]]$values)
{
    $copy = [string[]]@($values)
    [Array]::Sort($copy, [StringComparer]::Ordinal)
    return ,$copy
}
function Attr($element, [string]$name, [string]$default = '')
{
    if ($null -eq $element -or $null -eq $element.Attributes -or $null -eq $element.Attributes[$name]) { return $default }
    return [string]$element.Attributes[$name].Value
}
function Canonical-Path([string]$path)
{
    return $path.Replace('\','/').ToLowerInvariant()
}
function New-Row
{
    $row = [ordered]@{}
    foreach ($column in $columns) { $row[$column] = '' }
    return $row
}
function Fail([string]$message) { throw $message }

$fileList = New-Object System.Collections.Generic.List[object]
$npcFacts = @{}
$regions = @{}
$rows = New-Object System.Collections.Generic.List[object]
$parseErrors = New-Object System.Collections.Generic.List[string]
$referenced = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
$missing = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
$parsed = 0
$spawnFiles = 0
foreach ($root in $roots)
{
    $directory = Join-Path $dataPath $root
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) { Fail "Missing input root: $root" }
    $files = @(Get-ChildItem -LiteralPath $directory -Recurse -Filter '*.xml' -File)
    if ($files.Count -eq 0) { Fail "Empty input root: $root" }
    foreach ($file in $files)
    {
        $relative = 'data/' + $file.FullName.Substring($dataPath.Length + 1).Replace('\','/')
        $fileList.Add([pscustomobject][ordered]@{ path=$relative; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant(); root=('data/' + $root) })
        if ($root -eq 'spawns') { $spawnFiles++ }
    }
}
$fileByPath = @{}
foreach ($entry in $fileList) { $fileByPath[$entry.path] = $entry }
$orderedPaths = Sorted @($fileByPath.Keys)
$orderedFiles = @($orderedPaths | ForEach-Object { $fileByPath[$_] })
$documents = @{}
foreach ($entry in $orderedFiles)
{
    try
    {
        $file = Join-Path $dataPath ($entry.path.Substring(5).Replace('/',[IO.Path]::DirectorySeparatorChar))
        $readerSettings = New-Object Xml.XmlReaderSettings
        $readerSettings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
        $readerSettings.XmlResolver = $null
        $reader = [Xml.XmlReader]::Create($file, $readerSettings)
        try { $doc = New-Object Xml.XmlDocument; $doc.XmlResolver = $null; $doc.Load($reader) } finally { $reader.Dispose() }
        $documents[$entry.path] = $doc
        $parsed++
    }
    catch
    {
        $parseErrors.Add($entry.path + ': ' + $_.Exception.Message)
    }
}
foreach ($entry in $orderedFiles)
{
    if (-not $documents.ContainsKey($entry.path)) { continue }
    $doc = $documents[$entry.path]
    if ($entry.root -eq 'data/stats/npcs')
    {
        foreach ($npc in $doc.SelectNodes('/list/npc'))
        {
            $id = Attr $npc 'id'
            if ($id -eq '') { continue }
            $status = $npc.SelectSingleNode('status')
            $npcFacts[$id] = [pscustomobject]@{ level=(Attr $npc 'level' '85'); type=(Attr $npc 'type' 'Folk'); attackable=(Attr $status 'attackable' 'true'); targetable=(Attr $status 'targetable' 'true') }
        }
    }
    elseif ($entry.root -eq 'data/mapregion')
    {
        foreach ($region in $doc.SelectNodes('/list/region'))
        {
            foreach ($map in $region.SelectNodes('map')) { $regions[(Attr $map 'X') + ',' + (Attr $map 'Y')] = Attr $region 'name' }
        }
    }
}
foreach ($entry in $orderedFiles)
{
    if (($entry.root -ne 'data/spawns') -and ($entry.root -ne 'data/instances')) { continue }
    if (-not $documents.ContainsKey($entry.path))
    {
        $row = New-Row
        $row.coverage_key = Hash-Text ((Canonical-Path $entry.path) + '|file-parse-failure')
        $row.source_path = $entry.path; $row.source_group = 'FILE_PARSE_FAILURE'; $row.classification = 'UNRESOLVED'; $row.status = 'BLOCKED_SOURCE'; $row.reason = 'xml_parse_failure'; $row.source_sha256 = $entry.sha256
        $rows.Add([pscustomobject]$row)
        continue
    }
    $doc = $documents[$entry.path]
    if ($entry.root -eq 'data/spawns')
    {
        $groups = @($doc.SelectNodes('/list/spawn'))
        $rootElement = $doc.DocumentElement
        if ($rootElement.LocalName -ne 'list') { $parseErrors.Add($entry.path + ': expected list root') }
    }
    else
    {
        $groups = @($doc.SelectNodes('/instance/spawnlist/group'))
        $rootElement = $doc.DocumentElement
        if ($rootElement.LocalName -ne 'instance') { $parseErrors.Add($entry.path + ': expected instance root') }
    }
    foreach ($group in $groups)
    {
        $row = New-Row
        $row.source_path = $entry.path; $row.source_sha256 = $entry.sha256
        $row.instance_id = if ($entry.root -eq 'data/instances') { Attr $rootElement 'id' } else { '0' }
        $row.source_group = if ($entry.root -eq 'data/instances') { Attr $group 'name' } elseif ((Attr $group 'zone') -ne '') { Attr $group 'zone' } else { Attr $group 'name' }
        if ($row.source_group -eq '') { $row.source_group = 'UNNAMED' }
        $npcs = @($group.SelectNodes('npc'))
        $npcTokens = New-Object System.Collections.Generic.List[string]
        $npcIds = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
        $npcTypes = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
        $levels = New-Object System.Collections.Generic.List[int]
        $reasons = New-Object System.Collections.Generic.List[string]
        $conditions = New-Object System.Collections.Generic.List[string]
        $geometry = New-Object System.Collections.Generic.List[string]
        $respawns = New-Object System.Collections.Generic.List[string]
        $amount = 0; $attackable = 0; $targetable = 0; $farming = 0; $raid = $false; $event = $false; $hasPoint = $false
        if (($entry.root -eq 'data/spawns') -and ((Attr $rootElement 'enabled' 'true') -ne 'true')) { $conditions.Add('list.enabled=false') }
        $unknownChildren = @($group.ChildNodes | Where-Object { ($_.NodeType -eq [Xml.XmlNodeType]::Element) -and ($_.LocalName -notin @('npc','territory','banned_territory','AIData')) })
        if ($unknownChildren.Count -gt 0) { $reasons.Add('unsupported_child:' + (($unknownChildren | ForEach-Object LocalName) -join ',')) }
        $territory = $group.SelectSingleNode('territory')
        if ($null -ne $territory)
        {
            $row.geometry_kind = (Attr $territory 'shape' 'NPoly').ToUpperInvariant()
            $geometry.Add('territory:' + (Attr $territory 'minZ') + ':' + (Attr $territory 'maxZ') + ':' + (Attr $territory 'shape' 'NPoly'))
            foreach ($node in $territory.SelectNodes('node'))
            {
                $geometry.Add('vertex:' + (Attr $node 'x') + ':' + (Attr $node 'y'))
                if ($row.sample_x -eq '') { $row.sample_x = Attr $node 'x'; $row.sample_y = Attr $node 'y' }
            }
            if ($territory.SelectNodes('node').Count -eq 0) { $reasons.Add('empty_territory') }
            $vertexCount = $territory.SelectNodes('node').Count
            if ((($row.geometry_kind -eq 'NPOLY') -and ($vertexCount -lt 3)) -or (($row.geometry_kind -eq 'CUBOID') -and ($vertexCount -ne 2)) -or (($row.geometry_kind -eq 'CYLINDER') -and (($vertexCount -ne 1) -or ((Attr $territory 'rad') -eq ''))) -or ($row.geometry_kind -notin @('NPOLY','CUBOID','CYLINDER'))) { $reasons.Add('invalid_territory_shape') }
        }
        foreach ($npc in $npcs)
        {
            $id = Attr $npc 'id'; $countText = Attr $npc 'count' '1'; $count = 0
            if (-not [int]::TryParse($countText, [ref]$count)) { $reasons.Add('invalid_count'); $count = 0 }
            $amount += $count
            if ($id -eq '') { $reasons.Add('missing_npc_id') } else { [void]$npcIds.Add($id); [void]$referenced.Add($id) }
            if ($npcFacts.ContainsKey($id))
            {
                $fact = $npcFacts[$id]; $levels.Add([int]$fact.level)
                [void]$npcTypes.Add($fact.type)
                if ($fact.attackable -eq 'true') { $attackable++ }
                if ($fact.targetable -eq 'true') { $targetable++ }
                if (($fact.type -eq 'Monster') -and ($fact.attackable -eq 'true') -and ($fact.targetable -eq 'true')) { $farming++ }
                if ($fact.type -in @('RaidBoss','GrandBoss')) { $raid = $true }
                if ($fact.type -match 'Event|Script') { $event = $true }
            }
            elseif ($id -ne '') { [void]$missing.Add($id); $reasons.Add('missing_npc:' + $id) }
            $x = Attr $npc 'x'; $y = Attr $npc 'y'; $z = Attr $npc 'z'
            if (($x -ne '') -and ($y -ne '') -and ($z -ne ''))
            {
                $hasPoint = $true; $geometry.Add('point:' + $x + ':' + $y + ':' + $z)
                if ($row.sample_x -eq '') { $row.sample_x = $x; $row.sample_y = $y; $row.sample_z = $z }
            }
            elseif ($null -eq $territory) { $reasons.Add('npc_unresolved_geometry:' + $id) }
            $period = Attr $npc 'periodOfDay'
            if ($period -ne '') { $conditions.Add('periodOfDay=' + $period) }
            $delay = if ($entry.root -eq 'data/instances') { Attr $npc 'respawn' '0' } else { Attr $npc 'respawnDelay' '0' }
            $respawns.Add($id + ':' + $delay + ':' + (Attr $npc 'respawnRandom' '0'))
            $npcTokens.Add($id + ':' + $countText + ':' + $x + ':' + $y + ':' + $z + ':' + $period + ':' + $delay)
        }
        if ($npcs.Count -eq 0) { $reasons.Add('no_npc') }
        if ($amount -le 0) { $reasons.Add('nonpositive_amount') }
        if (($null -eq $territory) -and (-not $hasPoint)) { $reasons.Add('unresolved_geometry') }
        if (($null -ne $territory) -and $hasPoint) { $row.geometry_kind = 'MIXED' }
        elseif ($null -eq $territory) { $row.geometry_kind = if ($hasPoint) { 'POINT' } else { 'UNKNOWN' } }
        $row.geometry_fingerprint = Hash-Text ((Sorted $geometry.ToArray()) -join '|')
        $row.npc_ids = (Sorted @($npcIds)) -join '|'
        $row.npc_types = (Sorted @($npcTypes)) -join '|'
        $row.npc_level_min = if ($levels.Count -gt 0) { ($levels | Measure-Object -Minimum).Minimum } else { '' }
        $row.npc_level_max = if ($levels.Count -gt 0) { ($levels | Measure-Object -Maximum).Maximum } else { '' }
        $row.attackable_npc_count = $attackable; $row.targetable_npc_count = $targetable; $row.configured_amount = $amount
        $row.respawn_summary = (Sorted $respawns.ToArray()) -join '|'
        $row.condition_summary = (Sorted $conditions.ToArray()) -join '|'
        $row.zone_refs = Attr $group 'zone'
        if (($row.sample_x -ne '') -and ($row.sample_y -ne ''))
        {
            $mapX = ([int]$row.sample_x -shr 15) + 20; $mapY = ([int]$row.sample_y -shr 15) + 18
            $mapKey = $mapX.ToString() + ',' + $mapY.ToString()
            if ($regions.ContainsKey($mapKey)) { $row.map_region = $regions[$mapKey] }
        }
        $row.coverage_key = Hash-Text ((Canonical-Path $entry.path) + '|' + $row.source_group.ToLowerInvariant() + '|' + $row.instance_id + '|' + $row.geometry_fingerprint + '|' + ((Sorted $npcTokens.ToArray()) -join '|') + '|' + $row.condition_summary)
        if ($reasons.Count -gt 0) { $row.classification = 'UNRESOLVED'; $row.status = 'BLOCKED_SOURCE'; $row.reason = (Sorted $reasons.ToArray()) -join '|' }
        elseif ($entry.root -eq 'data/instances') { $row.classification = 'INSTANCE'; $row.status = 'INSTANCE'; $row.reason = 'native_instance_spawnlist' }
        elseif ($raid) { $row.classification = 'RAID'; $row.status = 'RAID'; $row.reason = 'native_npc_type' }
        elseif ($event) { $row.classification = 'EVENT_OR_SCRIPTED'; $row.status = 'EXCLUDED'; $row.reason = 'native_npc_type' }
        elseif ($farming -ne $npcs.Count) { $row.classification = 'NON_FARMING'; $row.status = 'EXCLUDED'; $row.reason = 'native_npc_type_or_flags' }
        elseif ($conditions.Count -gt 0) { $row.classification = 'CONDITIONAL_WORLD'; $row.status = 'CONDITIONAL'; $row.reason = 'native_condition' }
        else { $row.classification = 'ORDINARY_WORLD'; $row.status = 'READY_STATIC'; $row.reason = 'native_static_facts'; $row.topology_candidate = 'true' }
        $rows.Add([pscustomobject]$row)
    }
}
$keys = @{}; $duplicates = 0
foreach ($row in $rows) { if ($keys.ContainsKey($row.coverage_key)) { $duplicates++ } else { $keys[$row.coverage_key] = $true } }
$orderedRows = @($rows | Sort-Object -Property coverage_key -CaseSensitive)
$counters = [ordered]@{
    discovered_source_files=$orderedFiles.Count; parsed_source_files=$parsed; spawn_source_files=$spawnFiles; instance_source_files=@($orderedFiles | Where-Object root -eq 'data/instances').Count
    parse_failed_files=$parseErrors.Count; discovered_spawn_groups=$rows.Count; emitted_rows=$rows.Count
    ordinary_world_rows=@($rows | Where-Object classification -eq 'ORDINARY_WORLD').Count
    conditional_rows=@($rows | Where-Object classification -eq 'CONDITIONAL_WORLD').Count
    instance_rows=@($rows | Where-Object classification -eq 'INSTANCE').Count
    raid_rows=@($rows | Where-Object classification -eq 'RAID').Count
    event_or_scripted_rows=@($rows | Where-Object classification -eq 'EVENT_OR_SCRIPTED').Count
    non_farming_rows=@($rows | Where-Object classification -eq 'NON_FARMING').Count
    unresolved_rows=@($rows | Where-Object classification -eq 'UNRESOLVED').Count
    referenced_npc_ids=$referenced.Count; missing_npc_ids=$missing.Count; duplicate_coverage_keys=$duplicates
}
[void](New-Item -ItemType Directory -Path $outputPath -Force)
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add(($columns -join "`t"))
foreach ($row in $orderedRows)
{
    $values = foreach ($column in $columns) { ([string]$row.$column).Replace("`t",' ').Replace("`r",' ').Replace("`n",' ') }
    $lines.Add(($values -join "`t"))
}
$tsvPath = Join-Path $outputPath 'WORLD_COVERAGE.tsv'
[IO.File]::WriteAllText($tsvPath, (($lines -join "`n") + "`n"), $utf8)
$classCounts = [ordered]@{}; $statusCounts = [ordered]@{}
foreach ($name in @('ORDINARY_WORLD','CONDITIONAL_WORLD','INSTANCE','RAID','EVENT_OR_SCRIPTED','NON_FARMING','UNRESOLVED')) { $classCounts[$name] = @($rows | Where-Object classification -eq $name).Count }
foreach ($name in @('READY_STATIC','NEEDS_GEODATA','CONDITIONAL','INSTANCE','RAID','EXCLUDED','BLOCKED_SOURCE')) { $statusCounts[$name] = @($rows | Where-Object status -eq $name).Count }
$manifest = [ordered]@{
    schema_version=1; generator='Generate-WorldCoverage.ps1'; generator_sha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    baseline_commit='c325baa8aac21410e7153bfc1dd43604dbc29816'; input_roots=@($roots | ForEach-Object { 'data/' + $_ })
    input_files=$orderedFiles; input_file_count=$orderedFiles.Count; input_aggregate_sha256=(Hash-Text (($orderedFiles | ForEach-Object { $_.path + "`t" + $_.sha256 }) -join "`n"))
    output_sha256=(Get-FileHash -LiteralPath $tsvPath -Algorithm SHA256).Hash.ToLowerInvariant(); counters=$counters
    classification_counts=$classCounts; status_counts=$statusCounts; parse_errors=@($parseErrors); missing_npc_ids=@(Sorted @($missing))
    canonicalization='UTF-8 LF; ordinal sorted paths/rows/lists; coverage key SHA-256(lowercase slash path|native group|instance id|sorted geometry SHA-256|sorted NPC facts|sorted conditions); lists use |'
}
$manifestPath = Join-Path $outputPath 'WORLD_DATA_MANIFEST.json'
[IO.File]::WriteAllText($manifestPath, (($manifest | ConvertTo-Json -Depth 8 -Compress) + "`n"), $utf8)
Write-Output ("rows={0} ordinary={1} conditional={2} instance={3} raid={4} non_farming={5} unresolved={6} duplicates={7} parse_errors={8}" -f $rows.Count,$counters.ordinary_world_rows,$counters.conditional_rows,$counters.instance_rows,$counters.raid_rows,$counters.non_farming_rows,$counters.unresolved_rows,$duplicates,$parseErrors.Count)
if (($duplicates -gt 0) -or ($parseErrors.Count -gt 0)) { exit 1 }
