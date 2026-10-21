[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop

function Assert-Plain065([string]$Path){
    $absolute065=[IO.Path]::GetFullPath($Path)
    $walk065=[IO.Path]::GetPathRoot($absolute065)
    foreach($part065 in $absolute065.Substring($walk065.Length).Split('\')){
        if(!$part065){continue}
        $walk065=Join-Path $walk065 $part065
        if((Test-Path -LiteralPath $walk065) -and ((Get-Item -LiteralPath $walk065 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK065: plain physical paths required.'}
    }
}
function Read-Field065([string]$Path,[string]$Key){
    Assert-Plain065 $Path
    $matches065=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches065.Count -ne 1){throw "TASK065: need one key $Key."}
    return $matches065[0].Groups[1].Value.Trim()
}
function Read-Json065([string]$Path){
    Assert-Plain065 $Path
    if((Get-Item -LiteralPath $Path).Length -gt 16384){throw 'TASK065: bounded provenance manifest required.'}
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

$runtime065=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain065 $runtime065
$ownerPath065=Join-Path $runtime065 'ownership065.properties'
$schema065=Read-Field065 $ownerPath065 'schema'
if((Read-Field065 $ownerPath065 'version') -cne '1' -or (Read-Field065 $ownerPath065 'owner') -cne 'TASK065_LIVING_PRESENCE' -or [IO.Path]::GetFullPath((Read-Field065 $ownerPath065 'runtimeRoot')).TrimEnd('\') -ine $runtime065){throw 'TASK065: root/owner mismatch.'}
$module065=[IO.Path]::GetFullPath((Read-Field065 $ownerPath065 'sourceModuleRoot')).TrimEnd('\')
if((Split-Path $module065 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK065: source module differs.'}
$role065=Read-Field065 $ownerPath065 'role'
if($schema065 -ceq 'l2jmobiush5_phantom_test_065'){
    if($role065 -cne 'MUTABLE_FIXTURE' -or $runtime065 -ine (Join-Path $module065 '.phantom-local/task065/runtime')){throw 'TASK065: exact fixture root required.'}
    $expectedPorts065=@(2245,7945,9245)
}elseif($schema065 -ceq 'l2jmobiush5_phantom_release_065a'){
    if($role065 -cne 'RELEASE_CANDIDATE' -or (Split-Path $runtime065 -Leaf) -cne 'Living-Presence-065-candidate' -or $runtime065.StartsWith((Split-Path $module065 -Parent)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'TASK065: external exact candidate role/root required.'}
    $expectedPorts065=@(2145,7845,9145)
}else{throw 'TASK065: historical/foreign schema forbidden.'}
$manifest065=Read-Json065 (Join-Path $runtime065 'local-play.json')
$build065=Read-Json065 (Join-Path $runtime065 'BUILD_INFO065.json')
$sourceInfoPath065=Join-Path $runtime065 'SOURCE_BUILD_INFO065.json'
$sourceInfo065=Read-Json065 $sourceInfoPath065
$settings065=Join-Path $runtime065 'runtime.local.ini'
if((Read-Field065 $settings065 'DatabaseName') -cne $schema065 -or $manifest065.format -ne 1 -or $manifest065.owner -cne 'TASK065_LIVING_PRESENCE' -or $manifest065.databaseName -cne $schema065 -or $build065.format -ne 1 -or $build065.owner -cne 'TASK065_LIVING_PRESENCE' -or $build065.databaseName -cne $schema065 -or $manifest065.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $manifest065.codeSha -cne $build065.codeCommit -or $build065.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $build065.sourceState -cne 'COMMITTED'){throw 'TASK065: source/schema manifests mismatch.'}
if($sourceInfo065.format -ne 1 -or $sourceInfo065.owner -cne 'TASK065_LIVING_PRESENCE' -or $sourceInfo065.sourceState -cne 'COMMITTED' -or $sourceInfo065.codeCommit -cne $build065.codeCommit -or [IO.Path]::GetFullPath($sourceInfo065.sourceModuleRoot).TrimEnd('\') -ine $module065 -or $sourceInfo065.sourceArchiveSha256 -ine $build065.sourceArchiveSha256 -or $sourceInfo065.testTreeSha256 -ine $build065.testTreeSha256 -or $build065.sourceArchiveSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or $build065.testTreeSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or (Get-FileHash -LiteralPath $sourceInfoPath065 -Algorithm SHA256).Hash -ine $build065.buildReceiptSha256){throw 'TASK065: committed archive/test tree receipt mismatch.'}
foreach($kind065 in @('game','login')){
    $url065=Read-Field065 (Join-Path $runtime065 "$kind065/config/Database.ini") 'URL'
    if($url065 -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema065+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK065: JDBC endpoint mismatch.'}
}
$gameConfig065=Join-Path $runtime065 'game/config/Server.ini'
$loginConfig065=Join-Path $runtime065 'login/config/Server.ini'
$actualPorts065=@([int](Read-Field065 $loginConfig065 'LoginserverPort'),[int](Read-Field065 $gameConfig065 'GameserverPort'),[int](Read-Field065 $loginConfig065 'LoginPort'))
if([int](Read-Field065 $gameConfig065 'LoginPort') -ne $actualPorts065[2] -or (Read-Field065 $gameConfig065 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field065 $loginConfig065 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field065 $gameConfig065 'AcceptAlternateID') -cne 'False' -or (Read-Field065 $loginConfig065 'AcceptNewGameServer') -cne 'False'){throw 'TASK065: loopback registration/control binding differs.'}
for($i065=0;$i065 -lt 3;$i065++){
    $key065=@('clientLoginPort','gamePort','controlPort')[$i065]
    if($actualPorts065[$i065] -ne $expectedPorts065[$i065] -or [int](Read-Field065 $ownerPath065 $key065) -ne $expectedPorts065[$i065] -or [int]$manifest065.$key065 -ne $expectedPorts065[$i065] -or [int]$build065.$key065 -ne $expectedPorts065[$i065]){throw 'TASK065: exact ports mismatch.'}
}
foreach($kind065 in @('GameServer','LoginServer')){
    $jar065=Join-Path $runtime065 "libs/$kind065.jar"
    Assert-Plain065 $jar065
    $key065=if($kind065 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash065=(Get-FileHash -LiteralPath $jar065 -Algorithm SHA256).Hash
    if($hash065 -ine $build065.$key065 -or $hash065 -ine $manifest065.$key065 -or $hash065 -ine $sourceInfo065.$key065 -or $hash065 -ine (Read-Field065 $ownerPath065 $key065)){throw 'TASK065: whole JAR provenance mismatch.'}
}
$helper065=Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
if(!(Test-Path -LiteralPath $helper065)){$helper065=Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1'}
foreach($tool065 in @(@{path=$helper065;key='ownershipHelperSha256'},@{path=$PSCommandPath;key='runtimeToolSha256'})){
    Assert-Plain065 $tool065.path
    $hash065=(Get-FileHash -LiteralPath $tool065.path -Algorithm SHA256).Hash
    if($hash065 -ine $build065.($tool065.key) -or $hash065 -ine (Read-Field065 $ownerPath065 $tool065.key)){throw 'TASK065: operator tool provenance mismatch.'}
}
$geoManifest065=Join-Path $runtime065 'GEODATA065.tsv'
Assert-Plain065 $geoManifest065
if((Get-FileHash -LiteralPath $geoManifest065 -Algorithm SHA256).Hash -ine $build065.geoManifestSha256){throw 'TASK065: GeoData manifest differs.'}
if($Action -ceq 'Validate'){Write-Output 'TASK065_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. $helper065
$runtimeId065=Get-LocalPlayRuntimeId $runtime065
$ports065=@{GameServer=@($actualPorts065[1]);LoginServer=@($actualPorts065[0],$actualPorts065[2])}
function Role-State065([string]$Role){
    foreach($path065 in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain065 (Join-Path $runtime065 $path065)}
    $state065=Get-LocalPlayRoleState $runtime065 $Role "$Role.jar" $ports065[$Role]
    if($state065.pid -gt 0){
        $row065=Get-CimInstance Win32_Process -Filter "ProcessId=$($state065.pid)" -ErrorAction Stop
        if(!$row065 -or [string]$row065.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task065\.control=true(?:\s|$)' -or [string]$row065.CommandLine -match '(?:^|\s)-Dphantom\.(?:task0(?:5[7-9]|6[0-4])|task065\.manifest)'){throw 'TASK065: marked process has no exact ordinary control ownership.'}
    }
    return $state065
}
function Write-Status065{
    [ordered]@{runtime=$runtime065;runtimeId=$runtimeId065;owner='TASK065_LIVING_PRESENCE';database=$schema065;source=$build065.codeCommit;game=(Role-State065 'GameServer');login=(Role-State065 'LoginServer');ports=$ports065;gameJarSha256=$build065.gameJarSha256;sourceArchiveSha256=$build065.sourceArchiveSha256;testTreeSha256=$build065.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status065; return}
$javaHome065=Read-Field065 $settings065 'JavaHome'
$java065=Join-Path $javaHome065 'bin/java.exe'
Assert-Plain065 $java065
Assert-Plain065 (Join-Path $javaHome065 'release')
if(!(Test-Path -LiteralPath $java065) -or [IO.File]::ReadAllText((Join-Path $javaHome065 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK065: JDK25 required.'}
foreach($dir065 in @('local-play','local-play/pids','local-play/control','local-play/logs')){
    $path065=Join-Path $runtime065 $dir065
    Assert-Plain065 $path065
    New-Item -ItemType Directory -Path $path065 -Force | Out-Null
}
$lockPath065=Join-Path $runtime065 'local-play/operator.lock'
Assert-Plain065 $lockPath065
$lock065=[IO.File]::Open($lockPath065,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role065([string]$Role){
    $state065=Role-State065 $Role
    if($state065.state -in @('STOPPED','STALE_RECORD')){return}
    if(!$state065.recordVerified -or ($state065.state -cne 'RUNNING' -and !($state065.state -ceq 'INCONSISTENT' -and $state065.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK065: STOP ownership rejected: $Role/$($state065.state)"}
    $process065=Get-Process -Id $state065.pid -ErrorAction Stop
    if($process065.StartTime.ToUniversalTime().Ticks -ne [long]$state065.startTimeUtcTicks){throw 'TASK065: stale PID incarnation.'}
    $timeout065=[int](Read-Field065 $settings065 'StopTimeoutSeconds')
    if($timeout065 -lt 10 -or $timeout065 -gt 600){throw 'TASK065: invalid stop timeout.'}
    $request065=Join-Path $runtime065 "local-play/control/stop-$Role.properties"
    Assert-Plain065 $request065
    if(Test-Path -LiteralPath $request065){Move-Item -LiteralPath $request065 -Destination ($request065+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body065="version=1`nowner=TASK065_LIVING_PRESENCE`nruntime=$runtimeId065`nrole=$Role`npid=$($state065.pid)`nstartTimeUtcTicks=$($state065.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    $temporary065=$request065+'.tmp'
    Assert-Plain065 $temporary065
    [IO.File]::WriteAllText($temporary065,$body065,[Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary065 -Destination $request065
    $null=$process065.WaitForExit($timeout065*1000)
    if(!$process065.HasExited -or (Get-LocalPlayPortOwners $ports065[$Role]).Count){throw "TASK065: graceful STOP incomplete: $Role; force fallback absent."}
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state065.pid)"
}
function Start-Role065([string]$Role){
    $kind065=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg065=Join-Path $runtime065 "$kind065/java.cfg"
    Assert-Plain065 $cfg065
    $options065=[IO.File]::ReadAllText($cfg065).Trim()
    if($options065 -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK065: foreign startup agent/marker forbidden.'}
    $timeout065=[int](Read-Field065 $settings065 $(if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}))
    if($timeout065 -lt 1 -or $timeout065 -gt 600){throw 'TASK065: invalid startup timeout.'}
    $stamp065=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments065="$options065 -Dphantom.localplay.runtime=$runtimeId065 -Dphantom.localplay.role=$Role -Dphantom.task065.control=true -jar ..\libs\$Role.jar"
    $process065=Start-Process -FilePath $java065 -ArgumentList $arguments065 -WorkingDirectory (Join-Path $runtime065 $kind065) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime065 "local-play/logs/$Role-$stamp065-stdout.log") -RedirectStandardError (Join-Path $runtime065 "local-play/logs/$Role-$stamp065-stderr.log") -PassThru
    $record065=[pscustomobject]@{pid=$process065.Id;startTimeUtcTicks=$process065.StartTime.ToUniversalTime().Ticks}
    Save-LocalPlayRecord (Join-Path $runtime065 "local-play/pids/$Role.json") $Role $record065 "$Role.jar" $runtimeId065
    $until065=[DateTime]::UtcNow.AddSeconds($timeout065)
    do{
        if($process065.HasExited){throw "TASK065: $Role exited; inspect own logs."}
        $state065=Role-State065 $Role
        if($state065.state -ceq 'RUNNING' -and $state065.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process065.Id)";return}
        if($state065.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK065: foreign port owner.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until065)
    throw "TASK065: $Role startup timed out; no force fallback."
}
try{
    if($Action -ceq 'Stop'){Stop-Role065 'GameServer';Stop-Role065 'LoginServer'}
    else{
        foreach($kind065 in @('GameServer','LoginServer')){if((Role-State065 $kind065).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK065: START requires both own roles stopped and ports free.'}}
        Start-Role065 'LoginServer'
        Start-Role065 'GameServer'
    }
    Write-Status065
}finally{$lock065.Dispose()}
