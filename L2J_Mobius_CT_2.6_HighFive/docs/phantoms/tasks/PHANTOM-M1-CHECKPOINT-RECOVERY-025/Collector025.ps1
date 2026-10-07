[CmdletBinding()]
param([ValidateSet('Build','Observe','Flush')][string]$Mode='Build',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='c',
      [string]$CohortJson='', [string]$OutputRoot='')
$ErrorActionPreference='Stop'
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime025=Join-Path $module025 ".phantom-local/contract025$Episode/runtime"
$ops025=Join-Path $module025 '.phantom-local/ops025'
$jdk025='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
if($Mode -ceq 'Build'){
    $classes025=Join-Path $ops025 'observer-classes'
    New-Item -ItemType Directory -Path $classes025 -Force | Out-Null
    $attach025=Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
    & (Join-Path $jdk025 'javac.exe') -encoding UTF-8 -cp (Join-Path $runtime025 'libs/*') -d $classes025 (Join-Path $PSScriptRoot 'Contract025Observer.java') $attach025
    if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
    $mf025=Join-Path $ops025 'observer025.mf'
    [IO.File]::WriteAllText($mf025,"Manifest-Version: 1.0`nAgent-Class: Contract025Observer`n`n",[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk025 'jar.exe') --create --file (Join-Path $ops025 'observer025.jar') --manifest $mf025 -C $classes025 .
    if($LASTEXITCODE -ne 0){throw 'Matching agent JAR build failed.'}
    [ordered]@{sourceSha256=(Get-FileHash (Join-Path $PSScriptRoot 'Contract025Observer.java')).Hash;jarSha256=(Get-FileHash (Join-Path $ops025 'observer025.jar')).Hash;attachSourceSha256=(Get-FileHash $attach025).Hash} | ConvertTo-Json | Set-Content (Join-Path $ops025 'observer025-build.json') -Encoding utf8
    return
}
if(-not $OutputRoot){throw 'Exact output path required.'}
. (Join-Path $runtime025 'LocalPlay-Ownership.ps1')
. (Join-Path $runtime025 'LocalPlay-Pilot.ps1')
$manifest025=Get-Content (Join-Path $runtime025 'local-play.json') -Raw | ConvertFrom-Json
if($manifest025.databaseName -cne "l2jmobiush5_localplay_contract025$Episode" -or (Get-FileHash (Join-Path $runtime025 'libs/GameServer.jar')).Hash -cne $manifest025.gameJarSha256){throw 'Exact source/DB/JAR guard failed.'}
$build025=Get-Content (Join-Path $ops025 'observer025-build.json') -Raw | ConvertFrom-Json
if((Get-FileHash (Join-Path $PSScriptRoot 'Contract025Observer.java')).Hash -cne $build025.sourceSha256 -or (Get-FileHash (Join-Path $ops025 'observer025.jar')).Hash -cne $build025.jarSha256){throw 'Collector build/source hash mismatch.'}
$state025=Get-LocalPlayRoleState $runtime025 'GameServer' 'GameServer.jar' @(7777)
if($state025.state -cne 'RUNNING' -or !$state025.recordVerified -or (Get-Process -Id $state025.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state025.startTimeUtcTicks){throw 'Exact PID incarnation guard failed.'}
$output025=[IO.Path]::GetFullPath($OutputRoot)
$allowed025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
if(-not $output025.StartsWith($allowed025,[StringComparison]::OrdinalIgnoreCase)){throw 'Task evidence output guard failed.'}
$lines025=[Collections.Generic.List[string]]::new()
$lines025.Add('runtime='+$runtime025.Replace('\','/')); $lines025.Add('output='+$output025.Replace('\','/'))
$lines025.Add('owner=TASK025_CONTRACT'); $lines025.Add('pid='+$state025.pid); $lines025.Add('startTicks='+$state025.startTimeUtcTicks); $lines025.Add('codeSha='+$manifest025.codeSha)
$lines025.Add('mode='+$(if($Mode -ceq 'Flush'){'FLUSH'}else{'OBSERVE'}))
if($Mode -ceq 'Observe'){
    $rows025=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
    if($rows025.Count -lt 1 -or $rows025.Count -gt 8){throw 'Exact cohort 1..8 required.'}
    foreach($row025 in $rows025){if([long]$row025.profileId -le 0 -or [long]$row025.materializedAtNanos -le 0){throw 'Exact profile/epoch required.'};$lines025.Add('profile.'+$row025.profileId+'='+$row025.materializedAtNanos)}
}
$spec025=Join-Path (Split-Path $runtime025 -Parent) ('observer-'+[guid]::NewGuid().ToString('N')+'.properties')
[IO.File]::WriteAllLines($spec025,$lines025,[Text.UTF8Encoding]::new($false))
& (Join-Path $jdk025 'java.exe') --add-modules jdk.attach -cp (Join-Path $ops025 'observer-classes') AttachContract024 $state025.pid (Join-Path $ops025 'observer025.jar') $spec025
if($LASTEXITCODE -ne 0){throw 'Exact collector attach/flush failed.'}
"TASK025_OBSERVER mode=$Mode pid=$($state025.pid)"
