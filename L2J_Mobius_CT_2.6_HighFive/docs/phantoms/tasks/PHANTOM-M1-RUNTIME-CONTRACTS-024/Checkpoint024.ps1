param([string]$Message='Preserve native phantom contracts and bounded local recovery')
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$task='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/'
$map=Import-Csv (Join-Path $PSScriptRoot 'SOURCE_MAP.tsv') -Delimiter "`t"
$allowed=@($map | Where-Object {$_.mode -notin @('READ','TEST_BOUNDED','TASK_WRITE','PRIVATE_RUNTIME')} | ForEach-Object {$_.path})
$allowed+=@('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java','L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomContracts024DatabaseLane.java')
$allowed+='L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java'
$changed=@(& git -C $root diff --name-only)
if($LASTEXITCODE -ne 0){throw 'Bounded source inventory failed.'}
foreach($path in $changed){if($path -notin $allowed -and -not $path.StartsWith($task)){throw "OUTSIDE_SCOPE:$path"}}
if(@($changed | Where-Object {$_.StartsWith('L2J_Mobius_CT_2.6_HighFive/java/')}).Count -gt 18){throw 'Production path cap exceeded.'}
& git -C $root diff --check -- $changed
if($LASTEXITCODE -ne 0){throw 'Exact diff whitespace check failed.'}
$paths=@($changed)+@('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomRuntimeContracts024Suite.java','L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomContracts024DatabaseLane.java',$task.TrimEnd('/'))
& git -C $root add -- $paths
if($LASTEXITCODE -ne 0){throw 'Exact-path add failed.'}
$staged=@(& git -C $root diff --cached --name-only)
foreach($path in $staged){if($path -notin $allowed -and -not $path.StartsWith($task)){throw "STAGED_OUTSIDE_SCOPE:$path"}}
$staged | Set-Content (Join-Path $PSScriptRoot 'CHECKPOINT_SCOPE.txt') -Encoding utf8
& git -C $root add -- (Join-Path $task 'CHECKPOINT_SCOPE.txt')
if($LASTEXITCODE -ne 0){throw 'Exact scope receipt add failed.'}
& git -C $root commit -m $Message
if($LASTEXITCODE -ne 0){throw 'Exact-path commit failed.'}
& git -C $root rev-parse HEAD
