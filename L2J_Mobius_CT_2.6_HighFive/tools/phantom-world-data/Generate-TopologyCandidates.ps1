[CmdletBinding()]
param(
    [string]$DataRoot = '',
    [string]$RegistryDirectory = '',
    [string]$OutputDirectory = '',
    [switch]$FixtureMode
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
if ($DataRoot -eq '') { $DataRoot = Join-Path $PSScriptRoot '../../dist/game/data' }
if ($RegistryDirectory -eq '') { $RegistryDirectory = Join-Path $PSScriptRoot '../../docs/phantoms/live-world' }
if ($OutputDirectory -eq '') { $OutputDirectory = $RegistryDirectory }
$dataPath = [IO.Path]::GetFullPath($DataRoot)
$registryPath = [IO.Path]::GetFullPath($RegistryDirectory)
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
$utf8 = New-Object Text.UTF8Encoding($false)
$sha = [Security.Cryptography.SHA256]::Create()
function Hash-Text([string]$s) { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))).Replace('-','').ToLowerInvariant() }
function Hash-File([string]$p) { return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant() }
function Rel([string]$p) { return $p.Replace('\','/').ToLowerInvariant() }
function Attr($n,[string]$a) { if ($null -eq $n -or $null -eq $n.Attributes -or $null -eq $n.Attributes[$a]) { return '' }; return [string]$n.Attributes[$a].Value }
function Load-Xml([string]$p) { $d=New-Object Xml.XmlDocument; $d.XmlResolver=$null; $d.Load($p); return $d }
function Source-File([string]$p) { return Join-Path $dataPath ($p.Substring(5).Replace('/',[IO.Path]::DirectorySeparatorChar)) }
function Ordered([object[]]$items) { $a=[string[]]@($items); [Array]::Sort($a,[StringComparer]::Ordinal); return ,$a }
function Write-Tsv([string]$name,[string[]]$columns,[object[]]$rows) {
    $lines=New-Object 'System.Collections.Generic.List[string]'; $lines.Add(($columns -join "`t"))
    foreach($row in $rows) { $cells=foreach($column in $columns) { $v=[string]$row.$column; if ($v.Contains("`t") -or $v.Contains("`n") -or $v.Contains("`r")) { throw "Unsafe TSV value: $name/$column" }; $v }; $lines.Add(($cells -join "`t")) }
    [IO.File]::WriteAllText((Join-Path $outputPath $name), (($lines.ToArray() -join "`n") + "`n"), $utf8)
}
function Distance([int]$x,[int]$y,[int]$a,[int]$b) { return [math]::Round([math]::Sqrt(([double]($x-$a)*($x-$a))+([double]($y-$b)*($y-$b))),0,[MidpointRounding]::AwayFromZero) }
function Native-Geometry($group) {
    $tokens=New-Object 'System.Collections.Generic.List[string]'; $territory=$group.SelectSingleNode('territory')
    $points=New-Object 'System.Collections.Generic.List[object]'; $vertices=New-Object 'System.Collections.Generic.List[string]'
    if($null -ne $territory) {
        $shape=Attr $territory 'shape'; if($shape -eq ''){$shape='NPoly'}
        $tokens.Add(('territory:{0}:{1}:{2}' -f (Attr $territory 'minZ'),(Attr $territory 'maxZ'),$shape))
        foreach($n in $territory.SelectNodes('node')) { $tokens.Add(('vertex:{0}:{1}' -f (Attr $n 'x'),(Attr $n 'y'))); $vertices.Add(('{0},{1}' -f (Attr $n 'x'),(Attr $n 'y'))) }
    }
    foreach($n in $group.SelectNodes('npc')) {
        if((Attr $n 'x') -ne '' -and (Attr $n 'y') -ne '' -and (Attr $n 'z') -ne '') {
            $tokens.Add(('point:{0}:{1}:{2}' -f (Attr $n 'x'),(Attr $n 'y'),(Attr $n 'z')))
            $points.Add([pscustomobject]@{x=(Attr $n 'x');y=(Attr $n 'y');z=(Attr $n 'z');npc=(Attr $n 'id')})
        }
    }
    return [pscustomobject]@{fingerprint=(Hash-Text ((Ordered $tokens.ToArray()) -join '|')); territory=$territory; points=@($points.ToArray()); vertices=@($vertices.ToArray()); vertexKey=((Ordered $vertices.ToArray()) -join '|')}
}
function Native-Group($row) {
    if(-not $script:spawnCache.ContainsKey($row.source_path)) { $script:spawnCache[$row.source_path]=Load-Xml (Source-File $row.source_path) }
    $matches=New-Object 'System.Collections.Generic.List[object]'
    foreach($g in $script:spawnCache[$row.source_path].SelectNodes('/list/spawn')) {
        $name=Attr $g 'zone'; if($name -eq ''){$name=Attr $g 'name'}; if($name -eq ''){$name='UNNAMED'}
        if($name -ne $row.source_group){continue}
        $geom=Native-Geometry $g
        if($geom.fingerprint -eq $row.geometry_fingerprint){$matches.Add($geom)}
    }
    if($matches.Count -ne 1){throw "Native geometry mismatch/ambiguous: $($row.coverage_key) count=$($matches.Count)"}
    return $matches[0]
}
function Point-In-Polygon([int]$x,[int]$y,[object[]]$vertices) {
    $inside=$false; $j=$vertices.Count-1
    for($i=0;$i -lt $vertices.Count;$i++) {
        $a=$vertices[$i].Split(','); $b=$vertices[$j].Split(','); $ax=[double]$a[0];$ay=[double]$a[1];$bx=[double]$b[0];$by=[double]$b[1]
        if((($ay -gt $y) -ne ($by -gt $y)) -and ($x -lt (($bx-$ax)*($y-$ay)/($by-$ay)+$ax))){$inside=-not $inside};$j=$i
    }
    return $inside
}
$coverageFile=Join-Path $registryPath 'WORLD_COVERAGE.tsv'; $manifestFile=Join-Path $registryPath 'WORLD_DATA_MANIFEST.json'
$accepted=[ordered]@{coverage='84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e';manifest='94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4';generator='2bb686b8f0d6494f69ee92c6098390388c1670c9617407bb9cf75def798d1c2a';aggregate='468f2411920df9d5a40833a97ecdff70fba7a1586080e20fa8fe33037f8b7695'}
if(-not $FixtureMode) {
    if((Hash-File $coverageFile) -ne $accepted.coverage -or (Hash-File $manifestFile) -ne $accepted.manifest -or (Hash-File (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1')) -ne $accepted.generator){throw 'BLOCKED_INPUT_DRIFT: accepted LIVE-002-A file hash changed'}
    $m=Get-Content $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if($m.input_aggregate_sha256 -ne $accepted.aggregate -or $m.counters.emitted_rows -ne 3752 -or $m.counters.ordinary_world_rows -ne 2652 -or $m.counters.duplicate_coverage_keys -ne 0 -or $m.counters.missing_npc_ids -ne 0){throw 'BLOCKED_INPUT_DRIFT: accepted LIVE-002-A counters changed'}
    & (Join-Path $PSScriptRoot 'Validate-WorldCoverage.ps1') -DataRoot $dataPath -OutputDirectory $registryPath | Out-Null
}
$all=@(Import-Csv $coverageFile -Delimiter "`t" -Encoding UTF8)
$ordinary=@($all | Where-Object { $_.classification -eq 'ORDINARY_WORLD' -and $_.status -eq 'READY_STATIC' } | Sort-Object coverage_key)
if(-not $FixtureMode -and ($all.Count -ne 3752 -or $ordinary.Count -ne 2652)){throw 'BLOCKED_INPUT_DRIFT: row count changed'}
if(@($all.coverage_key | Select-Object -Unique).Count -ne $all.Count){throw 'Duplicate coverage key'}
$coreFile=Join-Path $dataPath 'phantoms/topology/high-five-core.xml'; $core=Load-Xml $coreFile
$allCoreNodes=@{};$allCoreAnchors=@{};foreach($n in $core.SelectNodes('/topology/node')){$allCoreNodes[(Attr $n 'id')]=1};foreach($a in $core.SelectNodes('/topology/anchor')){$allCoreAnchors[(Attr $a 'id')]=1}
$sourceInputs=New-Object 'System.Collections.Generic.List[object]'; $map=@{}; $zones=New-Object 'System.Collections.Generic.List[object]'; $teleports=New-Object 'System.Collections.Generic.List[object]'
foreach($root in @('mapregion','zones','teleporters')) {
    foreach($file in @(Get-ChildItem (Join-Path $dataPath $root) -Recurse -Filter '*.xml' -File | Sort-Object FullName)) {
        $path='data/'+$file.FullName.Substring($dataPath.Length+1).Replace('\','/'); $sourceInputs.Add([pscustomobject]@{path=$path;sha256=(Hash-File $file.FullName)})
        $doc=Load-Xml $file.FullName
        if($root -eq 'mapregion') { foreach($region in $doc.SelectNodes('/list/region')) { foreach($cell in $region.SelectNodes('map')) { $key=(Attr $cell 'X')+','+(Attr $cell 'Y');$map[$key]=[pscustomobject]@{locId=(Attr $region 'locId');source=$path;name=(Attr $region 'name')} } } }
        if($root -eq 'zones') { foreach($z in $doc.SelectNodes('/list/zone')) { $v=@($z.SelectNodes('node') | ForEach-Object { (Attr $_ 'X')+','+(Attr $_ 'Y') }); if($v.Count -ge 3){$xs=@($v | ForEach-Object {[int]$_.Split(',')[0]});$ys=@($v | ForEach-Object {[int]$_.Split(',')[1]});$zones.Add([pscustomobject]@{id=(Attr $z 'id');name=(Attr $z 'name');source=$path;minZ=(Attr $z 'minZ');maxZ=(Attr $z 'maxZ');vertices=$v;minX=($xs|Measure-Object -Minimum).Minimum;maxX=($xs|Measure-Object -Maximum).Maximum;minY=($ys|Measure-Object -Minimum).Minimum;maxY=($ys|Measure-Object -Maximum).Maximum})} } }
        if($root -eq 'teleporters') { foreach($npc in $doc.SelectNodes('/list/npc')) { foreach($t in $npc.SelectNodes('teleport')) { foreach($loc in $t.SelectNodes('location')) { if((Attr $loc 'x') -ne '' -and (Attr $loc 'y') -ne '' -and (Attr $loc 'z') -ne '') {$teleports.Add([pscustomobject]@{npc=(Attr $npc 'id');type=(Attr $t 'type');label=(Attr $loc 'name');x=[int](Attr $loc 'x');y=[int](Attr $loc 'y');z=[int](Attr $loc 'z');source=$path})} } } } }
    }
}
$sourceInputs.Add([pscustomobject]@{path='data/phantoms/topology/high-five-core.xml';sha256=(Hash-File $coreFile)})
$closedWalkSources='^data/spawns/Catacombs/|^data/spawns/(Aden/TowerOfInsolence|Giran/DevilsIsle|Goddard/ImperialTomb|Oren/IvoryTower)\.xml$'
$teleportBuckets=@{};foreach($t in $teleports){$key=($t.x -shr 15).ToString()+','+($t.y -shr 15).ToString();if(-not $teleportBuckets.ContainsKey($key)){$teleportBuckets[$key]=New-Object 'System.Collections.Generic.List[object]'};$teleportBuckets[$key].Add($t)}
$spawnCache=@{}; $coreFarms=New-Object 'System.Collections.Generic.List[object]'
foreach($node in $core.SelectNodes('/topology/node[@kind="FARMING_AREA"]')) {
    $sources=@($node.SelectNodes('source') | ForEach-Object { Attr $_ 'path' }); $anchors=@($core.SelectNodes('/topology/anchor') | Where-Object { (Attr $_ 'nodeId') -eq (Attr $node 'id') -and (Attr $_ 'role') -eq 'FARMING' })
    $v=@($node.SelectNodes('vertex') | ForEach-Object { (Attr $_ 'x')+','+(Attr $_ 'y') })
    $coreFarms.Add([pscustomobject]@{node=$node;anchors=$anchors;sources=$sources;vertexKey=((Ordered $v)-join '|')})
}
$usedCore=@{};$spatial=New-Object 'System.Collections.Generic.List[object]';$topology=New-Object 'System.Collections.Generic.List[object]';$nodeIds=@{};$anchorIds=@{};$points=New-Object 'System.Collections.Generic.List[object]';$ambiguous=0;$exact=0;$needsAnchor=0;$closedWalkNodes=0
foreach($r in $ordinary) {
    $g=Native-Group $r; $matches=New-Object 'System.Collections.Generic.List[object]'
    foreach($c in $coreFarms) {
        if((Attr $c.node 'instanceId') -ne $r.instance_id -or $c.sources -notcontains $r.source_path -or $c.anchors.Count -ne 1){continue}
        $anchor=$c.anchors[0]; $npc=Attr $anchor 'npcId'; if($npc -ne '' -and ($r.npc_ids.Split('|') -notcontains $npc)){continue}
        if((Attr $c.node 'form') -eq 'POLYGON' -and $g.territory -ne $null -and $c.vertexKey -eq $g.vertexKey -and (Attr $c.node 'minZ') -eq (Attr $g.territory 'minZ') -and (Attr $c.node 'maxZ') -eq (Attr $g.territory 'maxZ')) {$matches.Add($c)}
        elseif((Attr $c.node 'form') -eq 'POINT_RADIUS' -and $g.points.Count -gt 0 -and @($g.points | Where-Object { $_.x -eq (Attr $c.node 'x') -and $_.y -eq (Attr $c.node 'y') -and $_.z -eq (Attr $c.node 'z') -and ($npc -eq '' -or $_.npc -eq $npc) }).Count -gt 0){$matches.Add($c)}
    }
    $ownership='GENERATED_CANDIDATE';$status='NEEDS_ANCHOR_GEODATA';$assocStatus='NEEDS_GEODATA';$existingNode='';$existingAnchor='';$reason='source_geometry_no_validated_anchor';$anchorX='';$anchorY='';$anchorZ='';$anchorProvenance='';$anchorValidation='NEEDS_GEODATA';$coreIds=''
    if($matches.Count -gt 1){$status='BLOCKED_AMBIGUOUS';$assocStatus='BLOCKED_AMBIGUOUS';$reason='multiple_exact_core_geometry_matches';$ambiguous++}
    elseif($matches.Count -eq 1) {
        $c=$matches[0];$existingNode=Attr $c.node 'id';$existingAnchor=Attr $c.anchors[0] 'id'
        if($usedCore.ContainsKey($existingNode)){$status='BLOCKED_AMBIGUOUS';$assocStatus='BLOCKED_AMBIGUOUS';$reason='core_node_matches_multiple_coverage_rows';$ambiguous++}
        else {$usedCore[$existingNode]=$r.coverage_key;$ownership='EXISTING_CORE';$status='EXISTING_CORE';$assocStatus='EXACT_CORE';$reason='same_source_geometry_instance_and_npc_compatible';$exact++;$anchorX=Attr $c.anchors[0] 'x';$anchorY=Attr $c.anchors[0] 'y';$anchorZ=Attr $c.anchors[0] 'z';$anchorProvenance='high-five-core.xml';$anchorValidation='EXISTING_VALIDATED';$coreIds=$existingNode+'|'+$existingAnchor}
    }
    $token=$r.coverage_key.Substring(0,24);$nodeId=if($ownership -eq 'EXISTING_CORE'){$existingNode}else{'generated.farm.'+$token};$anchorId=if($ownership -eq 'EXISTING_CORE'){$existingAnchor}else{'generated.farm.'+$token+'.anchor'}
    if($ownership -ne 'EXISTING_CORE' -and ($allCoreNodes.ContainsKey($nodeId) -or $allCoreAnchors.ContainsKey($anchorId))){throw "Core/generated ID collision: $($r.coverage_key)"}
    if($ownership -ne 'EXISTING_CORE') {
        if($g.points.Count -gt 0){$p=$g.points[0];$anchorX=$p.x;$anchorY=$p.y;$anchorZ=$p.z;$anchorProvenance=$r.source_path+'#npc:'+ $p.npc;$status=if($status -eq 'BLOCKED_AMBIGUOUS'){$status}else{'READY_FOR_GEODATA'}}
        else {$needsAnchor++}
    }
    foreach($entry in @(@($nodeId,$nodeIds),@($anchorId,$anchorIds))){$id=[string]$entry[0];$index=$entry[1];if($id.Length -gt 96 -or $id -cnotmatch '^[a-z][a-z0-9_.-]{0,95}$' -or $index.ContainsKey($id)){throw "Duplicate/invalid topology ID: $id"};$index[$id]=1}
    $x=[int]$r.sample_x;$y=[int]$r.sample_y;$cell=(([int]$x -shr 15)+20).ToString()+','+((([int]$y -shr 15)+18).ToString());$region=$map[$cell];$locId='';$mapSource='';if($null -ne $region){$locId=$region.locId;$mapSource=$region.source}
    $zoneHits=New-Object 'System.Collections.Generic.List[object]'; foreach($z in $zones){if($x -lt $z.minX -or $x -gt $z.maxX -or $y -lt $z.minY -or $y -gt $z.maxY){continue};if(Point-In-Polygon $x $y $z.vertices){if($r.sample_z -ne '' -and $z.minZ -ne '' -and (([int]$r.sample_z -lt [int]$z.minZ) -or ([int]$r.sample_z -gt [int]$z.maxZ))){continue};$zoneHits.Add($z)}}
    $nearest=$null;$nearestDistance=[double]::MaxValue;$tx=$x -shr 15;$ty=$y -shr 15;$candidates=New-Object 'System.Collections.Generic.List[object]';for($ix=-2;$ix -le 2;$ix++){for($iy=-2;$iy -le 2;$iy++){$key=($tx+$ix).ToString()+','+($ty+$iy).ToString();if($teleportBuckets.ContainsKey($key)){$candidates.AddRange($teleportBuckets[$key])}}};if($candidates.Count -eq 0){$candidates.AddRange($teleports)};foreach($t in $candidates){$d=Distance $x $y $t.x $t.y;if($d -lt $nearestDistance -or ($d -eq $nearestDistance -and ($null -eq $nearest -or ([string]$t.source+'|'+$t.npc+'|'+$t.label) -clt ([string]$nearest.source+'|'+$nearest.npc+'|'+$nearest.label)))){$nearest=$t;$nearestDistance=$d}}
    if($nearestDistance -gt 32768){foreach($t in $teleports){$d=Distance $x $y $t.x $t.y;if($d -lt $nearestDistance -or ($d -eq $nearestDistance -and ([string]$t.source+'|'+$t.npc+'|'+$t.label) -clt ([string]$nearest.source+'|'+$nearest.npc+'|'+$nearest.label))){$nearest=$t;$nearestDistance=$d}}}
    $zoneIds=(Ordered @($zoneHits | ForEach-Object { $_.id } | Where-Object { $_ -ne '' } | Select-Object -Unique)) -join '|';$zoneNames=(Ordered @($zoneHits | ForEach-Object { $_.name } | Where-Object { $_ -ne '' } | Select-Object -Unique)) -join '|'
    $refs=New-Object 'System.Collections.Generic.List[string]';$refs.Add($r.source_path);if($mapSource -ne ''){$refs.Add($mapSource)};foreach($z in $zoneHits){$refs.Add($z.source)};if($null -ne $nearest){$refs.Add($nearest.source)};if($ownership -eq 'EXISTING_CORE'){$refs.Add('data/phantoms/topology/high-five-core.xml')};$sourceRefs=(Ordered @($refs | Select-Object -Unique)) -join '|'
    if($assocStatus -ne 'EXACT_CORE' -and $assocStatus -ne 'BLOCKED_AMBIGUOUS'){$assocStatus=if($null -ne $region -or $zoneHits.Count -gt 0){'SOURCE_ASSOCIATED'}elseif($null -ne $nearest){'SPATIAL_CANDIDATE'}else{'NEEDS_GEODATA'}}
    $spatial.Add([pscustomobject][ordered]@{coverage_key=$r.coverage_key;association_status=$assocStatus;topology_ownership=$ownership;existing_node_id=$existingNode;existing_anchor_id=$existingAnchor;map_region_loc_id=$locId;map_region_source=$mapSource;zone_ids=$zoneIds;zone_names=$zoneNames;teleporter_ids=if($null -ne $nearest){$nearest.npc}else{''};teleporter_labels=if($null -ne $nearest){$nearest.label}else{''};nearest_landmark_id=if($null -ne $nearest){$nearest.npc+'@'+$nearest.x+','+$nearest.y+','+$nearest.z}else{''};nearest_landmark_label=if($null -ne $nearest){$nearest.label}else{''};nearest_landmark_distance=if($null -ne $nearest){[string]$nearestDistance}else{''};association_reason=$reason+';map_cell='+$cell+';landmark_proximity_only';source_refs=$sourceRefs})
    $geometryText=if($null -ne $g.territory){'territory:'+ (Attr $g.territory 'minZ')+':'+(Attr $g.territory 'maxZ')+':'+($g.vertices -join '|')}else{($g.points | ForEach-Object { $_.x+','+$_.y+','+$_.z }) -join '|'}
    if($g.points.Count -gt 0 -and $null -ne $g.territory){$geometryText+=';points='+(($g.points | ForEach-Object { $_.x+','+$_.y+','+$_.z }) -join '|')}
    $candidateRefs=if($ownership -eq 'EXISTING_CORE'){$r.source_path+'|data/phantoms/topology/high-five-core.xml'}else{$r.source_path}
    $topology.Add([pscustomobject][ordered]@{coverage_key=$r.coverage_key;candidate_status=$status;ownership=$ownership;node_id=$nodeId;anchor_id=$anchorId;node_kind='FARMING_AREA';instance_id=$r.instance_id;geometry_kind=$r.geometry_kind;source_geometry=$geometryText;anchor_x=$anchorX;anchor_y=$anchorY;anchor_z=$anchorZ;anchor_provenance=$anchorProvenance;anchor_validation=$anchorValidation;npc_ids=$r.npc_ids;npc_level_min=$r.npc_level_min;npc_level_max=$r.npc_level_max;map_region_loc_id=$locId;source_refs=$candidateRefs;existing_core_ids=$coreIds})
    if($r.source_path -match $closedWalkSources){$closedWalkNodes++}
    else {$points.Add([pscustomobject]@{key=$r.coverage_key;node=$nodeId;x=$x;y=$y;cell=$cell;region=$locId;instance=$r.instance_id;refs=$r.source_path})}
}
$routes=New-Object 'System.Collections.Generic.List[object]';$routeIds=@{}
foreach($e in $core.SelectNodes('/topology/edge')){$id=Attr $e 'id';if($routeIds.ContainsKey($id)){throw "Duplicate core edge ID $id"};$routeIds[$id]=1;$routes.Add([pscustomobject][ordered]@{route_id=$id;from_kind='NODE';from_id=(Attr $e 'fromNodeId');to_kind='NODE';to_id=(Attr $e 'toNodeId');candidate_mode='EXISTING_CORE';candidate_reason=(Attr $e 'mode');map_region_loc_id='';straight_line_distance='';teleporter_source='';existing_core_edge_id=$id;validation_status='EXISTING_VALIDATED';source_refs=((Ordered @($e.SelectNodes('source') | ForEach-Object { Attr $_ 'path' })) -join '|')})}
# Bucket lookup touches at most 9 cells per node; four outgoing candidates per node.
$buckets=@{};foreach($p in $points){if(-not $buckets.ContainsKey($p.cell)){$buckets[$p.cell]=New-Object 'System.Collections.Generic.List[object]'};$buckets[$p.cell].Add($p)}
$pairSeen=@{};$degree=@{}
foreach($p in $points){$parts=$p.cell.Split(',');$near=New-Object 'System.Collections.Generic.List[object]';for($dx=-1;$dx -le 1;$dx++){for($dy=-1;$dy -le 1;$dy++){$key=([int]$parts[0]+$dx).ToString()+','+([int]$parts[1]+$dy).ToString();if($buckets.ContainsKey($key)){foreach($q in $buckets[$key]){if($q.key -eq $p.key -or $q.instance -ne $p.instance){continue};$d=Distance $p.x $p.y $q.x $q.y;if($d -le 12000){$near.Add([pscustomobject]@{q=$q;distance=$d;sort=('{0:D8}|{1}' -f [int]$d,$q.key)})}}}}};$ranked=@($near | Sort-Object sort | Select-Object -First 4);foreach($n in $ranked){$q=$n.q;$a=$p.key;$b=$q.key;if([string]::CompareOrdinal($a,$b) -gt 0){$a=$q.key;$b=$p.key};$pair=$a+'|'+$b;if($pairSeen.ContainsKey($pair)){continue};if(-not $degree.ContainsKey($p.node)){$degree[$p.node]=0};if(-not $degree.ContainsKey($q.node)){$degree[$q.node]=0};if($degree[$p.node] -ge 4 -or $degree[$q.node] -ge 4){continue};$pairSeen[$pair]=1;$degree[$p.node]++;$degree[$q.node]++;$id='generated.route.'+(Hash-Text $pair).Substring(0,24);if($routeIds.ContainsKey($id)){throw "Route ID collision $id"};$routeIds[$id]=1;$mode=if($p.region -ne '' -and $p.region -eq $q.region){'LOCAL_WALK'}else{'REGION_LINK'};$routes.Add([pscustomobject][ordered]@{route_id=$id;from_kind='NODE';from_id=$p.node;to_kind='NODE';to_id=$q.node;candidate_mode=$mode;candidate_reason='same_instance_bucket_nearest_4_degree_4_distance_le_12000';map_region_loc_id=if($p.region -eq $q.region){$p.region}else{''};straight_line_distance=[string]$n.distance;teleporter_source='';existing_core_edge_id='';validation_status='NEEDS_GEODATA';source_refs=((Ordered @($p.refs,$q.refs)) -join '|')})}}
$teleportFacts=@{};foreach($t in $teleports){$fact=$t.source+'|'+$t.npc+'|'+$t.type+'|'+$t.label+'|'+$t.x+'|'+$t.y+'|'+$t.z;if($teleportFacts.ContainsKey($fact)){continue};$teleportFacts[$fact]=1;$id='generated.route.'+(Hash-Text ('teleport|'+$fact)).Substring(0,24);if($routeIds.ContainsKey($id)){throw "Route ID collision $id"};$routeIds[$id]=1;$routes.Add([pscustomobject][ordered]@{route_id=$id;from_kind='TELEPORTER_NPC';from_id=$t.npc;to_kind='SOURCE_LOCATION';to_id=('{0},{1},{2}' -f $t.x,$t.y,$t.z);candidate_mode='TELEPORT_FACT';candidate_reason=$t.type+'|'+$t.label;map_region_loc_id='';straight_line_distance='';teleporter_source=$t.source;existing_core_edge_id='';validation_status='FACTUAL_TELEPORT';source_refs=$t.source})}
$routes=@($routes | Sort-Object route_id)
if($topology.Count -ge 100000 -or $routes.Count -ge 200000 -or $nodeIds.Count -ge 100000 -or $anchorIds.Count -ge 100000){throw 'Topology policy bound exceeded'}
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
Write-Tsv 'WORLD_SPATIAL_ASSOCIATIONS.tsv' @('coverage_key','association_status','topology_ownership','existing_node_id','existing_anchor_id','map_region_loc_id','map_region_source','zone_ids','zone_names','teleporter_ids','teleporter_labels','nearest_landmark_id','nearest_landmark_label','nearest_landmark_distance','association_reason','source_refs') $spatial.ToArray()
Write-Tsv 'TOPOLOGY_CANDIDATES.tsv' @('coverage_key','candidate_status','ownership','node_id','anchor_id','node_kind','instance_id','geometry_kind','source_geometry','anchor_x','anchor_y','anchor_z','anchor_provenance','anchor_validation','npc_ids','npc_level_min','npc_level_max','map_region_loc_id','existing_core_ids','source_refs') $topology.ToArray()
Write-Tsv 'ROUTE_CANDIDATES.tsv' @('route_id','from_kind','from_id','to_kind','to_id','candidate_mode','candidate_reason','map_region_loc_id','straight_line_distance','teleporter_source','existing_core_edge_id','validation_status','source_refs') $routes
$bands=New-Object 'System.Collections.Generic.List[object]';foreach($band in @(@(1,5),@(6,10),@(11,19),@(20,39),@(40,51),@(52,60),@(61,75),@(76,80),@(81,85))){$selected=@($topology | Where-Object { [int]$_.npc_level_min -le $band[1] -and [int]$_.npc_level_max -ge $band[0] });$bandKeys=@{};foreach($item in $selected){$bandKeys[$item.node_id]=1};$parent=@{};foreach($key in $bandKeys.Keys){$parent[$key]=$key};foreach($route in $routes){if($route.from_kind -ne 'NODE' -or $route.to_kind -ne 'NODE' -or -not $bandKeys.ContainsKey($route.from_id) -or -not $bandKeys.ContainsKey($route.to_id)){continue};$a=$route.from_id;$b=$route.to_id;while($parent[$a] -ne $a){$a=$parent[$a]};while($parent[$b] -ne $b){$b=$parent[$b]};if($a -ne $b){$parent[$b]=$a}};$roots=@{};foreach($key in $bandKeys.Keys){$a=$key;while($parent[$a] -ne $a){$a=$parent[$a]};$roots[$a]=1};$regions=@($selected | ForEach-Object { $_.map_region_loc_id } | Where-Object {$_ -ne ''} | Select-Object -Unique);$bands.Add([pscustomobject][ordered]@{band=$band[0].ToString()+'-'+$band[1];ordinary_candidate_groups=$selected.Count;distinct_map_regions=$regions.Count;exact_core_groups=@($selected | Where-Object ownership -eq 'EXISTING_CORE').Count;generated_candidates=@($selected | Where-Object ownership -eq 'GENERATED_CANDIDATE').Count;blocked_on_anchor_or_geodata=@($selected | Where-Object candidate_status -ne 'EXISTING_CORE').Count;candidate_components_unvalidated=$roots.Count})}
$counts=[ordered]@{ordinary_registry_rows=$ordinary.Count;exact_core_farming_rows=$exact;generated_farming_rows=($ordinary.Count-$exact);needs_anchor_geodata=$needsAnchor;blocked_ambiguous=$ambiguous;unmatched_existing_core_farming=($coreFarms.Count-$usedCore.Count);closed_source_walk_nodes=$closedWalkNodes;duplicate_node_ids=0;duplicate_anchor_ids=0;duplicate_route_ids=0;route_candidates=$routes.Count;local_walk=@($routes | Where-Object candidate_mode -eq 'LOCAL_WALK').Count;region_link=@($routes | Where-Object candidate_mode -eq 'REGION_LINK').Count;teleport_fact=@($routes | Where-Object candidate_mode -eq 'TELEPORT_FACT').Count;existing_core_edges=@($core.SelectNodes('/topology/edge')).Count}
$outHashes=[ordered]@{};foreach($name in @('WORLD_SPATIAL_ASSOCIATIONS.tsv','TOPOLOGY_CANDIDATES.tsv','ROUTE_CANDIDATES.tsv')){$outHashes[$name]=Hash-File (Join-Path $outputPath $name)}
$payload=[ordered]@{schema='LIVE-002-B/1';accepted_input_hashes=$accepted;generator_sha256=(Hash-File $PSCommandPath);source_hashes=@($sourceInputs | Sort-Object path);counters=$counts;progression_bands=@($bands.ToArray());output_sha256=$outHashes;canonicalization='UTF-8 LF; ordinal rows and source paths; SHA-256 IDs; no wall clock; walking bucket 3x3, 12000 units, nearest 4'}
[IO.File]::WriteAllText((Join-Path $outputPath 'TOPOLOGY_CANDIDATE_MANIFEST.json'),(($payload | ConvertTo-Json -Depth 12 -Compress)+"`n"),$utf8)
"TOPOLOGY generation GREEN: ordinary=$($ordinary.Count) exact=$exact generated=$($ordinary.Count-$exact) anchor_blocked=$needsAnchor routes=$($routes.Count)"
