[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputRoot)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if($runtime -notmatch '[\\/]night023c[\\/]runtime$'){throw 'Only dedicated TASK023 clone c may be crashed.'}
if(Test-Path -LiteralPath $OutputRoot){throw 'Planned crash evidence already exists; no second crash.'}
$marker=Join-Path (Split-Path $runtime -Parent) 'PLANNED_CRASH_023.used'
if(Test-Path -LiteralPath $marker){throw 'Dedicated clone already consumed its one planned crash.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$runtimeId=Get-LocalPlayRuntimeId $runtime
$cfg=Join-Path $runtime 'game/config/Database.ini'
if((Get-PilotIniValue $cfg 'URL') -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay_night023c\?'){throw 'Dedicated owned clone guard failed.'}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne '1b71464a9e09a80dad890e2bfd39df0f3b5d3b96' -or (Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Exact frozen owned JAR mismatch.'}
$port=[int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort')
$state=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @($port)
if($state.state -cne 'RUNNING' -or !$state.recordVerified){throw 'Exact owned running process not established.'}
$saved=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=Get-PilotIniValue $cfg 'Password'; $login=Get-PilotIniValue $cfg 'Login'
    $online=& 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' --no-defaults --host=127.0.0.1 --port=3308 "--user=$login" --batch --skip-column-names --database=l2jmobiush5_localplay_night023c -e 'SELECT COUNT(*) FROM characters WHERE online=1'
    if($LASTEXITCODE -ne 0 -or "$online".Trim() -cne '0'){throw 'No REAL clients not confirmed.'}
}finally{$env:MYSQL_PWD=$saved}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
$state | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputRoot 'exact-owned-state.json') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $runtime 'local-play/pids/GameServer.json') -Destination (Join-Path $OutputRoot 'GameServer.json')
foreach($command in @('Thread.print','VM.command_line')){
    & jcmd ([string]$state.pid) $command > (Join-Path $OutputRoot ($command+'.txt'))
    if($LASTEXITCODE -ne 0){throw 'Pre-crash exact owned dump failed.'}
}
$process=Get-Process -Id $state.pid -ErrorAction Stop
if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'PID incarnation changed before planned crash.'}
"PLANNED_CRASH_BEGIN utc=$([DateTime]::UtcNow.ToString('o')) pid=$($state.pid) startTicks=$($state.startTimeUtcTicks) runtime=$runtimeId jarSha256=$($manifest.gameJarSha256); dedicatedClone=night023c; realClients=0; emergencyForce=false" | Set-Content (Join-Path $OutputRoot 'planned-crash.txt') -Encoding utf8
$once=[IO.File]::Open($marker,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{$bytes=[Text.Encoding]::UTF8.GetBytes("$($state.pid)/$($state.startTimeUtcTicks)/$runtimeId");$once.Write($bytes,0,$bytes.Length);$once.Flush($true)}finally{$once.Dispose()}
Stop-Process -Id $state.pid -Force -ErrorAction Stop
if(-not $process.WaitForExit(10000)){throw 'Planned crashed process still present.'}
'PLANNED_CRASH_COMPLETE exactOwnedGamePidOnly=true; no runtime/DB restore' | Add-Content (Join-Path $OutputRoot 'planned-crash.txt') -Encoding utf8
Get-Content (Join-Path $OutputRoot 'planned-crash.txt')
