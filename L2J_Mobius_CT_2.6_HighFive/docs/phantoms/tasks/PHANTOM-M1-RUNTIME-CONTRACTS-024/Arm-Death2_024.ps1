[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$CohortJson,[Parameter(Mandatory)][string]$OutputRoot)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if($runtime -notmatch '[\\/]contract024c[\\/]runtime$'){throw 'Exact TASK024 c runtime required.'}
. (Join-Path $runtime 'LocalPlay-Ownership.ps1')
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$state=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
if($state.state -cne 'RUNNING' -or !$state.recordVerified){throw 'Exact owned GameServer missing.'}
$native=Get-Process -Id $state.pid -ErrorAction Stop
if($native.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'PID incarnation changed.'}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if((Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'JAR identity changed.'}
$url=Get-PilotIniValue (Join-Path $runtime 'game/config/Database.ini') 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay_contract024c\?'){throw 'Owned DB mismatch.'}
$actors=@(Get-Content $CohortJson -Raw | ConvertFrom-Json | Where-Object {$_.eligible -ceq 'true' -and $_.dead -cne 'true'} | Sort-Object {[long]$_.profileId} | Select-Object -First 2)
if($actors.Count -ne 2){throw 'Two natural selected death victims unavailable.'}
$output=[IO.Path]::GetFullPath($OutputRoot)
if(Test-Path $output){throw 'Death evidence exists; no replay.'}
if(-not $output.StartsWith([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Output outside task.'}
New-Item -ItemType Directory -Path $output | Out-Null
$actors | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $output 'selected.json') -Encoding utf8
& 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $state.pid Thread.print -l *> (Join-Path $output 'pre-death-threads.txt')
if($LASTEXITCODE -ne 0){throw 'Exact dump failed.'}
& (Join-Path $PSScriptRoot 'Export-Persistence024.ps1') -RuntimeRoot $runtime -CohortJson (Join-Path $output 'selected.json') -OutputRoot (Join-Path $output 'before-sql') | Out-Null
$spec=Join-Path (Split-Path $runtime -Parent) ('death-'+[guid]::NewGuid().ToString('N')+'.properties')
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('runtime='+$runtime.Replace('\','/'));$lines.Add('output='+$output.Replace('\','/'));$lines.Add('owner=TASK024_CONTRACT');$lines.Add('pid='+$state.pid);$lines.Add('startTicks='+$state.startTimeUtcTicks)
foreach($actor in $actors){$lines.Add('profile.'+$actor.profileId+'='+$actor.objectId+','+$actor.materializedAtNanos)}
$lines | Set-Content -LiteralPath $spec -Encoding utf8NoBOM
if((Get-Process -Id $state.pid -ErrorAction Stop).StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'PID changed after preflight.'}
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops024'
& 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe' --add-modules jdk.attach -cp $ops AttachContract024 $state.pid (Join-Path $ops 'contract024-death2.jar') $spec
if($LASTEXITCODE -ne 0){throw 'Death fixture attach rejected; no death proof claimed.'}
'TASK024_DEATH_FIXTURE_INSTALLED pid='+$state.pid
