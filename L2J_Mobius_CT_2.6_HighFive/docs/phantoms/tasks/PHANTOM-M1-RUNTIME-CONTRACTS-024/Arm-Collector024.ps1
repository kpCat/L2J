[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$CohortJson,
      [Parameter(Mandatory)][string]$OutputRoot,[ValidateSet('OBSERVE','CRASH_NATIVE','CRASH_FINALIZE')][string]$Mode='OBSERVE')
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if($runtime -notmatch '[\\/]contract024[a-h][\\/]runtime$'){throw 'TASK024 exact owned runtime required.'}
. (Join-Path $runtime 'LocalPlay-Ownership.ps1')
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if((Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Runtime JAR hash mismatch.'}
$state=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
if($state.state -cne 'RUNNING' -or !$state.recordVerified){throw 'Exact owned GameServer is not running.'}
$process=Get-Process -Id $state.pid -ErrorAction Stop
if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'PID incarnation changed.'}
$url=Get-PilotIniValue (Join-Path $runtime 'game/config/Database.ini') 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract024[a-h])\?'){throw 'Owned DB guard failed.'}
$rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
if($rows.Count -lt 1 -or $rows.Count -gt 8){throw 'Exact cohort size 1..8 required.'}
$output=[IO.Path]::GetFullPath($OutputRoot)
$spec=Join-Path (Split-Path $runtime -Parent) ('collector-'+[guid]::NewGuid().ToString('N')+'.properties')
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('runtime='+$runtime.Replace('\','/'));$lines.Add('output='+$output.Replace('\','/'))
$lines.Add('owner=TASK024_CONTRACT');$lines.Add('pid='+$state.pid);$lines.Add('startTicks='+$state.startTimeUtcTicks);$lines.Add('mode='+$Mode)
foreach($row in $rows){
    if([long]$row.profileId -le 0 -or [long]$row.materializedAtNanos -le 0){throw 'Exact profile epoch required.'}
    $lines.Add('profile.'+$row.profileId+'='+$row.materializedAtNanos)
}
$lines | Set-Content -LiteralPath $spec -Encoding utf8NoBOM
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops024'
& 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe' --add-modules jdk.attach -cp $ops AttachContract024 $state.pid (Join-Path $ops 'contract024-agent2.jar') $spec
if($LASTEXITCODE -ne 0){throw 'Collector attach failed; no persistence proof claimed.'}
'TASK024_COLLECTOR_INSTALLED pid='+$state.pid+' mode='+$Mode+' cohort='+$rows.Count
