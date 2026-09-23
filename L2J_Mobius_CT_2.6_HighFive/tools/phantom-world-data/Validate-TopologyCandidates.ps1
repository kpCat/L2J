[CmdletBinding()]
param([string]$DataRoot='', [string]$RegistryDirectory='', [string]$CandidateDirectory='')
$ErrorActionPreference='Stop'
if($DataRoot -eq ''){$DataRoot=Join-Path $PSScriptRoot '../../dist/game/data'}
if($RegistryDirectory -eq ''){$RegistryDirectory=Join-Path $PSScriptRoot '../../docs/phantoms/live-world'}
if($CandidateDirectory -eq ''){$CandidateDirectory=$RegistryDirectory}
function Hash([string]$p){return (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Assert([bool]$ok,[string]$why){if(-not $ok){throw "TOPOLOGY validation FAILED: $why"}}
$manifest=Get-Content (Join-Path $CandidateDirectory 'TOPOLOGY_CANDIDATE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$accepted=$manifest.accepted_input_hashes
Assert ((Hash (Join-Path $RegistryDirectory 'WORLD_COVERAGE.tsv')) -eq '84af90619ff959af64119ad079cf0f2b85692dec2aa985d2314185b059c76c8e') 'accepted coverage drift'
Assert ((Hash (Join-Path $RegistryDirectory 'WORLD_DATA_MANIFEST.json')) -eq '94ac62cbe005e6df43529506fbb7af6511525a8d3bad355e66413583788a6bf4') 'accepted manifest drift'
Assert ((Hash (Join-Path $PSScriptRoot 'Generate-WorldCoverage.ps1')) -eq $accepted.generator) 'accepted generator drift'
Assert ((Hash (Join-Path $PSScriptRoot 'Generate-TopologyCandidates.ps1')) -eq $manifest.generator_sha256) 'candidate generator drift'
$teleportFacts=@{};foreach($input in $manifest.source_hashes){$p=Join-Path $DataRoot ($input.path.Substring(5).Replace('/',[IO.Path]::DirectorySeparatorChar));Assert ((Hash $p) -eq $input.sha256) "source drift: $($input.path)";if($input.path -like 'data/teleporters/*'){$doc=New-Object Xml.XmlDocument;$doc.XmlResolver=$null;$doc.Load($p);foreach($loc in $doc.SelectNodes('/list/npc/teleport/location')){if($loc.GetAttribute('x') -eq '' -or $loc.GetAttribute('y') -eq '' -or $loc.GetAttribute('z') -eq ''){continue};$type=$loc.ParentNode.GetAttribute('type');$npc=$loc.ParentNode.ParentNode.GetAttribute('id');$key=$input.path+'|'+$npc+'|'+$type+'|'+$loc.GetAttribute('name')+'|'+$loc.GetAttribute('x')+','+$loc.GetAttribute('y')+','+$loc.GetAttribute('z');$teleportFacts[$key]=1}}}
foreach($name in @('WORLD_SPATIAL_ASSOCIATIONS.tsv','TOPOLOGY_CANDIDATES.tsv','ROUTE_CANDIDATES.tsv')){Assert ((Hash (Join-Path $CandidateDirectory $name)) -eq $manifest.output_sha256.$name) "output hash: $name"}
$coverage=@(Import-Csv (Join-Path $RegistryDirectory 'WORLD_COVERAGE.tsv') -Delimiter "`t" -Encoding UTF8 | Where-Object {$_.classification -eq 'ORDINARY_WORLD' -and $_.status -eq 'READY_STATIC'})
$spatial=@(Import-Csv (Join-Path $CandidateDirectory 'WORLD_SPATIAL_ASSOCIATIONS.tsv') -Delimiter "`t" -Encoding UTF8)
$topology=@(Import-Csv (Join-Path $CandidateDirectory 'TOPOLOGY_CANDIDATES.tsv') -Delimiter "`t" -Encoding UTF8)
$routes=@(Import-Csv (Join-Path $CandidateDirectory 'ROUTE_CANDIDATES.tsv') -Delimiter "`t" -Encoding UTF8)
Assert ($coverage.Count -eq 2652 -and $topology.Count -eq 2652 -and $spatial.Count -eq 2652) 'ordinary row accounting count'
$expected=@{};foreach($r in $coverage){Assert (-not $expected.ContainsKey($r.coverage_key)) 'duplicate coverage input';$expected[$r.coverage_key]=$r}
$spatialKeys=@{};foreach($s in $spatial){Assert ($expected.ContainsKey($s.coverage_key) -and -not $spatialKeys.ContainsKey($s.coverage_key)) "spatial key: $($s.coverage_key)";$spatialKeys[$s.coverage_key]=1;Assert ($s.association_status -in @('EXACT_CORE','SOURCE_ASSOCIATED','SPATIAL_CANDIDATE','NEEDS_GEODATA','BLOCKED_AMBIGUOUS')) 'spatial status'}
$nodes=@{};$anchors=@{};$topologyKeys=@{};$coreFile=Join-Path $DataRoot 'phantoms/topology/high-five-core.xml';$core=New-Object Xml.XmlDocument;$core.XmlResolver=$null;$core.Load($coreFile);$coreNodes=@{};$coreAnchors=@{};$coreEdges=@{};foreach($n in $core.SelectNodes('/topology/node')){$coreNodes[$n.GetAttribute('id')]=$n};foreach($a in $core.SelectNodes('/topology/anchor')){$coreAnchors[$a.GetAttribute('id')]=$a};foreach($e in $core.SelectNodes('/topology/edge')){$coreEdges[$e.GetAttribute('id')]=$e}
foreach($t in $topology){
    Assert ($expected.ContainsKey($t.coverage_key) -and -not $topologyKeys.ContainsKey($t.coverage_key)) "topology key: $($t.coverage_key)";$topologyKeys[$t.coverage_key]=1;$r=$expected[$t.coverage_key]
    Assert ($t.npc_ids -eq $r.npc_ids -and $t.npc_level_min -eq $r.npc_level_min -and $t.npc_level_max -eq $r.npc_level_max -and $t.geometry_kind -eq $r.geometry_kind -and $t.instance_id -eq $r.instance_id) "registry facts: $($t.coverage_key)"
    Assert ($t.source_geometry -ne '' -and $t.source_refs.Split('|') -contains $r.source_path -and $t.source_refs.Split('|').Count -le 8) "source geometry/ref: $($t.coverage_key)"
    Assert (-not $nodes.ContainsKey($t.node_id) -and -not $anchors.ContainsKey($t.anchor_id)) "duplicate node/anchor: $($t.coverage_key)";$nodes[$t.node_id]=$t;$anchors[$t.anchor_id]=$t
    Assert ($t.node_id.Length -le 96 -and $t.anchor_id.Length -le 96 -and $t.node_id -cmatch '^[a-z][a-z0-9_.-]*$' -and $t.anchor_id -cmatch '^[a-z][a-z0-9_.-]*$') "ID syntax: $($t.coverage_key)"
    if($t.ownership -eq 'EXISTING_CORE') {Assert ($t.candidate_status -eq 'EXISTING_CORE' -and $coreNodes.ContainsKey($t.node_id) -and $coreAnchors.ContainsKey($t.anchor_id) -and $coreAnchors[$t.anchor_id].GetAttribute('nodeId') -eq $t.node_id) "core ownership: $($t.coverage_key)"}
    else {Assert ($t.ownership -eq 'GENERATED_CANDIDATE' -and -not $coreNodes.ContainsKey($t.node_id) -and -not $coreAnchors.ContainsKey($t.anchor_id)) "generated/core collision: $($t.coverage_key)";Assert ($t.candidate_status -in @('READY_FOR_GEODATA','NEEDS_ANCHOR_GEODATA','BLOCKED_AMBIGUOUS')) "generated status: $($t.coverage_key)";Assert ($t.anchor_validation -eq 'NEEDS_GEODATA') "generated validation: $($t.coverage_key)";if($t.candidate_status -eq 'NEEDS_ANCHOR_GEODATA'){Assert ($t.anchor_z -eq '') "fabricated anchor Z: $($t.coverage_key)"}}
}
$routeIds=@{};$fanout=@{};foreach($route in $routes){Assert (-not $routeIds.ContainsKey($route.route_id)) "duplicate route ID: $($route.route_id)";$routeIds[$route.route_id]=1;Assert ($route.route_id.Length -le 96) 'route ID length';switch($route.candidate_mode){
    'EXISTING_CORE' {Assert ($route.validation_status -eq 'EXISTING_VALIDATED' -and $coreEdges.ContainsKey($route.route_id) -and $route.existing_core_edge_id -eq $route.route_id) 'existing edge'}
    'TELEPORT_FACT' {Assert ($route.validation_status -eq 'FACTUAL_TELEPORT' -and $route.from_kind -eq 'TELEPORTER_NPC' -and $route.to_kind -eq 'SOURCE_LOCATION' -and $route.teleporter_source -ne '') 'teleport direction/status';$fact=$route.teleporter_source+'|'+$route.from_id+'|'+$route.candidate_reason+'|'+$route.to_id;Assert ($teleportFacts.ContainsKey($fact)) "teleport source relation: $($route.route_id)"}
    {$_ -in @('LOCAL_WALK','REGION_LINK')} {Assert ($route.validation_status -eq 'NEEDS_GEODATA' -and $route.from_kind -eq 'NODE' -and $route.to_kind -eq 'NODE' -and $nodes.ContainsKey($route.from_id) -and $nodes.ContainsKey($route.to_id)) 'walking route status/endpoints';Assert ($nodes[$route.from_id].instance_id -eq $nodes[$route.to_id].instance_id) 'cross-instance walking route';$closed='^data/spawns/Catacombs/|^data/spawns/(Aden/TowerOfInsolence|Giran/DevilsIsle|Goddard/ImperialTomb|Oren/IvoryTower)\.xml$';Assert (-not ($nodes[$route.from_id].source_refs -match $closed) -and -not ($nodes[$route.to_id].source_refs -match $closed)) 'closed source walking route';foreach($id in @($route.from_id,$route.to_id)){if(-not $fanout.ContainsKey($id)){$fanout[$id]=0};$fanout[$id]++};Assert ([int]$route.straight_line_distance -le 12000) 'walking distance bound'}
    default {throw "Unknown route mode: $($route.candidate_mode)"}
}}
Assert (@($fanout.Values | Where-Object {$_ -gt 4}).Count -eq 0) 'walking fanout bound'
Assert (@($routes | Where-Object candidate_mode -eq 'TELEPORT_FACT').Count -eq $teleportFacts.Count) 'factual teleport accounting'
Assert ($topology.Count -lt 100000 -and $routes.Count -lt 200000) 'policy cardinality'
Assert ($manifest.counters.ordinary_registry_rows -eq $coverage.Count -and $manifest.counters.route_candidates -eq $routes.Count) 'manifest counters'
"TOPOLOGY production GREEN: ordinary=$($topology.Count) spatial=$($spatial.Count) routes=$($routes.Count) duplicate_ids=0 new_walk_validated=0"
