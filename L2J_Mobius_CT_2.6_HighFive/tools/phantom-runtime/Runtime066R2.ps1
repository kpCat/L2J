[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop

function Assert-Plain066([string]$Path){
    $absolute066=[IO.Path]::GetFullPath($Path)
    $walk066=[IO.Path]::GetPathRoot($absolute066)
    foreach($part066 in $absolute066.Substring($walk066.Length).Split('\')){
        if(!$part066){continue}
        $walk066=Join-Path $walk066 $part066
        if((Test-Path -LiteralPath $walk066) -and ((Get-Item -LiteralPath $walk066 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK066: plain physical paths required.'}
    }
}
function Read-Field066([string]$Path,[string]$Key){
    Assert-Plain066 $Path
    $matches066=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches066.Count -ne 1){throw "TASK066: need one key $Key."}
    return $matches066[0].Groups[1].Value.Trim()
}
function Read-Json066([string]$Path){
    Assert-Plain066 $Path
    if((Get-Item -LiteralPath $Path).Length -gt 16384){throw 'TASK066: bounded provenance manifest required.'}
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

$runtime066=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain066 $runtime066
$ownerPath066=Join-Path $runtime066 'ownership066r2.properties'
$schema066=Read-Field066 $ownerPath066 'schema'
if((Read-Field066 $ownerPath066 'version') -cne '1' -or (Read-Field066 $ownerPath066 'owner') -cne 'TASK066R2_FARM_ECONOMY' -or [IO.Path]::GetFullPath((Read-Field066 $ownerPath066 'runtimeRoot')).TrimEnd('\') -ine $runtime066){throw 'TASK066: root/owner mismatch.'}
$module066=[IO.Path]::GetFullPath((Read-Field066 $ownerPath066 'sourceModuleRoot')).TrimEnd('\')
if((Split-Path $module066 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK066: source module differs.'}
$role066=Read-Field066 $ownerPath066 'role'
if($schema066 -ceq 'l2jmobiush5_phantom_test_066r2'){
    if($role066 -cne 'MUTABLE_FIXTURE' -or $runtime066 -ine (Join-Path (Split-Path $module066 -Parent) '.phantom-local/task066r2/runtime')){throw 'TASK066: exact fixture root required.'}
    $expectedPorts066=@(2247,7947,9247)
}elseif($schema066 -ceq 'l2jmobiush5_phantom_release_066r2'){
    if($role066 -cne 'RELEASE_CANDIDATE' -or $runtime066 -ine (Join-Path ([IO.Path]::GetPathRoot($module066)) 'PhantomWorld/Farm-Economy-066-R2-candidate')){throw 'TASK066: exact authorized external candidate role/root required.'}
    $expectedPorts066=@(2147,7847,9147)
}else{throw 'TASK066: historical/foreign schema forbidden.'}
$manifest066=Read-Json066 (Join-Path $runtime066 'local-play.json')
$build066=Read-Json066 (Join-Path $runtime066 'BUILD_INFO066R2.json')
$sourceInfoPath066=Join-Path $runtime066 'SOURCE_BUILD_INFO066R2.json'
$sourceInfo066=Read-Json066 $sourceInfoPath066
$settings066=Join-Path $runtime066 'runtime.local.ini'
if((Read-Field066 $settings066 'DatabaseName') -cne $schema066 -or $manifest066.format -ne 1 -or $manifest066.owner -cne 'TASK066R2_FARM_ECONOMY' -or $manifest066.databaseName -cne $schema066 -or $build066.format -ne 1 -or $build066.owner -cne 'TASK066R2_FARM_ECONOMY' -or $build066.databaseName -cne $schema066 -or $manifest066.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $manifest066.codeSha -cne $build066.codeCommit -or $build066.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $build066.sourceState -cne 'COMMITTED'){throw 'TASK066: source/schema manifests mismatch.'}
if($sourceInfo066.format -ne 1 -or $sourceInfo066.owner -cne 'TASK066R2_FARM_ECONOMY' -or $sourceInfo066.sourceState -cne 'COMMITTED' -or $sourceInfo066.codeCommit -cne $build066.codeCommit -or [IO.Path]::GetFullPath($sourceInfo066.sourceModuleRoot).TrimEnd('\') -ine $module066 -or $sourceInfo066.sourceArchiveSha256 -ine $build066.sourceArchiveSha256 -or $sourceInfo066.testTreeSha256 -ine $build066.testTreeSha256 -or $build066.sourceArchiveSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or $build066.testTreeSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or (Get-FileHash -LiteralPath $sourceInfoPath066 -Algorithm SHA256).Hash -ine $build066.buildReceiptSha256){throw 'TASK066: committed archive/test tree receipt mismatch.'}
foreach($kind066 in @('game','login')){
    $url066=Read-Field066 (Join-Path $runtime066 "$kind066/config/Database.ini") 'URL'
    if($url066 -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema066+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK066: JDBC endpoint mismatch.'}
}
$gameConfig066=Join-Path $runtime066 'game/config/Server.ini'
$loginConfig066=Join-Path $runtime066 'login/config/Server.ini'
$actualPorts066=@([int](Read-Field066 $loginConfig066 'LoginserverPort'),[int](Read-Field066 $gameConfig066 'GameserverPort'),[int](Read-Field066 $loginConfig066 'LoginPort'))
if([int](Read-Field066 $gameConfig066 'LoginPort') -ne $actualPorts066[2] -or (Read-Field066 $gameConfig066 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field066 $loginConfig066 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field066 $gameConfig066 'AcceptAlternateID') -cne 'False' -or (Read-Field066 $loginConfig066 'AcceptNewGameServer') -cne 'False'){throw 'TASK066: loopback registration/control binding differs.'}
for($i066=0;$i066 -lt 3;$i066++){
    $key066=@('clientLoginPort','gamePort','controlPort')[$i066]
    if($actualPorts066[$i066] -ne $expectedPorts066[$i066] -or [int](Read-Field066 $ownerPath066 $key066) -ne $expectedPorts066[$i066] -or [int]$manifest066.$key066 -ne $expectedPorts066[$i066] -or [int]$build066.$key066 -ne $expectedPorts066[$i066]){throw 'TASK066: exact ports mismatch.'}
}
foreach($kind066 in @('GameServer','LoginServer')){
    $jar066=Join-Path $runtime066 "libs/$kind066.jar"
    Assert-Plain066 $jar066
    $key066=if($kind066 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash066=(Get-FileHash -LiteralPath $jar066 -Algorithm SHA256).Hash
    if($hash066 -ine $build066.$key066 -or $hash066 -ine $manifest066.$key066 -or $hash066 -ine $sourceInfo066.$key066 -or $hash066 -ine (Read-Field066 $ownerPath066 $key066)){throw 'TASK066: whole JAR provenance mismatch.'}
}
$helper066=Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
if(!(Test-Path -LiteralPath $helper066)){$helper066=Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1'}
foreach($tool066 in @(@{path=$helper066;key='ownershipHelperSha256'},@{path=$PSCommandPath;key='runtimeToolSha256'})){
    Assert-Plain066 $tool066.path
    $hash066=(Get-FileHash -LiteralPath $tool066.path -Algorithm SHA256).Hash
    if($hash066 -ine $build066.($tool066.key) -or $hash066 -ine (Read-Field066 $ownerPath066 $tool066.key)){throw 'TASK066: operator tool provenance mismatch.'}
}
$geoManifest066=Join-Path $runtime066 'GEODATA066R2.tsv'
Assert-Plain066 $geoManifest066
if((Get-FileHash -LiteralPath $geoManifest066 -Algorithm SHA256).Hash -ine $build066.geoManifestSha256){throw 'TASK066: GeoData manifest differs.'}
if($Action -ceq 'Validate'){Write-Output 'TASK066_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. $helper066
$runtimeId066=Get-LocalPlayRuntimeId $runtime066
$ports066=@{GameServer=@($actualPorts066[1]);LoginServer=@($actualPorts066[0],$actualPorts066[2])}
function Role-State066([string]$Role){
    foreach($path066 in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain066 (Join-Path $runtime066 $path066)}
    $state066=Get-LocalPlayRoleState $runtime066 $Role "$Role.jar" $ports066[$Role]
    if($state066.pid -gt 0){
        $row066=Get-CimInstance Win32_Process -Filter "ProcessId=$($state066.pid)" -ErrorAction Stop
        if(!$row066 -or [string]$row066.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task066r2\.control=true(?:\s|$)' -or [string]$row066.CommandLine -match '(?:^|\s)-Dphantom\.(?:task0(?:5[7-9]|6[0-5])|task066r2\.manifest)'){throw 'TASK066: marked process has no exact ordinary control ownership.'}
    }
    return $state066
}
function Write-Status066{
    [ordered]@{runtime=$runtime066;runtimeId=$runtimeId066;owner='TASK066R2_FARM_ECONOMY';database=$schema066;source=$build066.codeCommit;game=(Role-State066 'GameServer');login=(Role-State066 'LoginServer');ports=$ports066;gameJarSha256=$build066.gameJarSha256;sourceArchiveSha256=$build066.sourceArchiveSha256;testTreeSha256=$build066.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status066; return}
$javaHome066=Read-Field066 $settings066 'JavaHome'
$java066=Join-Path $javaHome066 'bin/java.exe'
Assert-Plain066 $java066
Assert-Plain066 (Join-Path $javaHome066 'release')
if(!(Test-Path -LiteralPath $java066) -or [IO.File]::ReadAllText((Join-Path $javaHome066 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK066: JDK25 required.'}
foreach($dir066 in @('local-play','local-play/pids','local-play/control','local-play/logs')){
    $path066=Join-Path $runtime066 $dir066
    Assert-Plain066 $path066
    New-Item -ItemType Directory -Path $path066 -Force | Out-Null
}
$lockPath066=Join-Path $runtime066 'local-play/operator.lock'
Assert-Plain066 $lockPath066
$lock066=[IO.File]::Open($lockPath066,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role066([string]$Role){
    $state066=Role-State066 $Role
    if($state066.state -in @('STOPPED','STALE_RECORD')){return}
    if(!$state066.recordVerified -or ($state066.state -cne 'RUNNING' -and !($state066.state -ceq 'INCONSISTENT' -and $state066.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK066: STOP ownership rejected: $Role/$($state066.state)"}
    $process066=Get-Process -Id $state066.pid -ErrorAction Stop
    if($process066.StartTime.ToUniversalTime().Ticks -ne [long]$state066.startTimeUtcTicks){throw 'TASK066: stale PID incarnation.'}
    $timeout066=[int](Read-Field066 $settings066 'StopTimeoutSeconds')
    if($timeout066 -lt 10 -or $timeout066 -gt 600){throw 'TASK066: invalid stop timeout.'}
    $request066=Join-Path $runtime066 "local-play/control/stop-$Role.properties"
    Assert-Plain066 $request066
    if(Test-Path -LiteralPath $request066){Move-Item -LiteralPath $request066 -Destination ($request066+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body066="version=1`nowner=TASK066R2_FARM_ECONOMY`nruntime=$runtimeId066`nrole=$Role`npid=$($state066.pid)`nstartTimeUtcTicks=$($state066.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    $temporary066=$request066+'.tmp'
    Assert-Plain066 $temporary066
    [IO.File]::WriteAllText($temporary066,$body066,[Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary066 -Destination $request066
    $null=$process066.WaitForExit($timeout066*1000)
    if(!$process066.HasExited -or (Get-LocalPlayPortOwners $ports066[$Role]).Count){throw "TASK066: graceful STOP incomplete: $Role; force fallback absent."}
    $recordPath066=Join-Path $runtime066 "local-play/pids/$Role.json"
    $record066=Read-Json066 $recordPath066
    if([long]$record066.pid -ne [long]$state066.pid -or [long]$record066.startTimeUtcTicks -ne [long]$state066.startTimeUtcTicks -or $record066.runtime -cne $runtimeId066 -or $record066.role -cne $Role){throw 'TASK066R2: stopped incarnation receipt changed; preserve it.'}
    $archive066=Join-Path $runtime066 'local-play/stopped-pids'
    Assert-Plain066 $archive066
    New-Item -ItemType Directory -Path $archive066 -Force | Out-Null
    $archived066=Join-Path $archive066 "$Role-$($state066.pid)-$($state066.startTimeUtcTicks).json"
    Assert-Plain066 $archived066
    if(Test-Path -LiteralPath $archived066){throw 'TASK066R2: stopped receipt already archived; no overwrite.'}
    Move-Item -LiteralPath $recordPath066 -Destination $archived066
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state066.pid)"
}
function Start-Role066([string]$Role){
    $kind066=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg066=Join-Path $runtime066 "$kind066/java.cfg"
    Assert-Plain066 $cfg066
    $options066=[IO.File]::ReadAllText($cfg066).Trim()
    if($options066 -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK066: foreign startup agent/marker forbidden.'}
    $timeout066=[int](Read-Field066 $settings066 $(if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}))
    if($timeout066 -lt 1 -or $timeout066 -gt 600){throw 'TASK066: invalid startup timeout.'}
    $stamp066=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments066="$options066 -Dphantom.localplay.runtime=$runtimeId066 -Dphantom.localplay.role=$Role -Dphantom.task066r2.control=true -jar ..\libs\$Role.jar"
    $process066=Start-Process -FilePath $java066 -ArgumentList $arguments066 -WorkingDirectory (Join-Path $runtime066 $kind066) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime066 "local-play/logs/$Role-$stamp066-stdout.log") -RedirectStandardError (Join-Path $runtime066 "local-play/logs/$Role-$stamp066-stderr.log") -PassThru
    $record066=[pscustomobject]@{pid=$process066.Id;startTimeUtcTicks=$process066.StartTime.ToUniversalTime().Ticks}
    Save-LocalPlayRecord (Join-Path $runtime066 "local-play/pids/$Role.json") $Role $record066 "$Role.jar" $runtimeId066
    $until066=[DateTime]::UtcNow.AddSeconds($timeout066)
    do{
        if($process066.HasExited){throw "TASK066: $Role exited; inspect own logs."}
        $state066=Role-State066 $Role
        if($state066.state -ceq 'RUNNING' -and $state066.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process066.Id)";return}
        if($state066.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK066: foreign port owner.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until066)
    throw "TASK066: $Role startup timed out; no force fallback."
}
try{
    if($Action -ceq 'Stop'){Stop-Role066 'GameServer';Stop-Role066 'LoginServer'}
    else{
        foreach($kind066 in @('GameServer','LoginServer')){if((Role-State066 $kind066).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK066: START requires both own roles stopped and ports free.'}}
        Start-Role066 'LoginServer'
        Start-Role066 'GameServer'
    }
    Write-Status066
}finally{$lock066.Dispose()}
