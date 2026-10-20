[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
$helper064=Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
if(!(Test-Path -LiteralPath $helper064)){$helper064=Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1'}
. $helper064
function Assert-Plain064([string]$Path){
    $walk064=[IO.Path]::GetPathRoot($Path)
    foreach($part064 in $Path.Substring($walk064.Length).Split('\')){
        if(!$part064){continue}; $walk064=Join-Path $walk064 $part064
        if((Test-Path -LiteralPath $walk064) -and ((Get-Item -LiteralPath $walk064 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK064: alias запрещён.'}
    }
}
function Read-Field064([string]$Path,[string]$Key){
    Assert-Plain064 ([IO.Path]::GetFullPath($Path))
    $match064=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($match064.Count -ne 1){throw "TASK064: нужен один ключ $Key."}; return $match064[0].Groups[1].Value.Trim()
}
$runtime064=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain064 $runtime064
$ownerPath064=Join-Path $runtime064 'ownership064.properties'
$schema064=Read-Field064 $ownerPath064 'schema'
if((Read-Field064 $ownerPath064 'version') -cne '1' -or (Read-Field064 $ownerPath064 'owner') -cne 'TASK064_CAMPAIGN' -or [IO.Path]::GetFullPath((Read-Field064 $ownerPath064 'runtimeRoot')).TrimEnd('\') -ine $runtime064){throw 'TASK064: root/owner mismatch.'}
$role064=Read-Field064 $ownerPath064 'role'
if($schema064 -ceq 'l2jmobiush5_phantom_test_064'){
    $module064=[IO.Path]::GetFullPath((Read-Field064 $ownerPath064 'sourceModuleRoot')).TrimEnd('\')
    if($role064 -cne 'MUTABLE_FIXTURE' -or $runtime064 -ine (Join-Path $module064 '.phantom-local/task064/runtime')){throw 'TASK064: exact fixture root required.'}
    $expectedPorts064=@(2244,7944,9244)
}elseif($schema064 -ceq 'l2jmobiush5_phantom_release_064a'){
    if($role064 -cne 'RELEASE_CANDIDATE' -or (Split-Path $runtime064 -Leaf) -cne 'World-Integrated-064-candidate'){throw 'TASK064: exact candidate role/root required.'}
    $expectedPorts064=@(2144,7844,9144)
}else{throw 'TASK064: historical/foreign schema forbidden.'}
$manifest064=Get-Content -LiteralPath (Join-Path $runtime064 'local-play.json') -Raw | ConvertFrom-Json
$build064=Get-Content -LiteralPath (Join-Path $runtime064 'BUILD_INFO.json') -Raw | ConvertFrom-Json
$settings064=Join-Path $runtime064 'runtime.local.ini'
if((Read-Field064 $settings064 'DatabaseName') -cne $schema064 -or $manifest064.databaseName -cne $schema064 -or $build064.databaseName -cne $schema064 -or $manifest064.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $manifest064.codeSha -cne $build064.codeCommit -or $build064.codeCommit -cnotmatch '^[0-9a-f]{40}$'){throw 'TASK064: source/schema manifests mismatch.'}
foreach($kind064 in @('game','login')){
    $url064=Read-Field064 (Join-Path $runtime064 "$kind064/config/Database.ini") 'URL'
    if($url064 -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema064+'\?useSSL=false(?:&[A-Za-z0-9=:-]+)*$')){throw 'TASK064: JDBC endpoint mismatch.'}
}
$gameConfig064=Join-Path $runtime064 'game/config/Server.ini'
$loginConfig064=Join-Path $runtime064 'login/config/Server.ini'
$actualPorts064=@([int](Read-Field064 $loginConfig064 'LoginserverPort'),[int](Read-Field064 $gameConfig064 'GameserverPort'),[int](Read-Field064 $loginConfig064 'LoginPort'))
if([int](Read-Field064 $gameConfig064 'LoginPort') -ne $actualPorts064[2]){throw 'TASK064: control ports differ.'}
for($i064=0;$i064 -lt 3;$i064++){
    $key064=@('clientLoginPort','gamePort','controlPort')[$i064]
    if($actualPorts064[$i064] -ne $expectedPorts064[$i064] -or [int](Read-Field064 $ownerPath064 $key064) -ne $expectedPorts064[$i064] -or [int]$manifest064.$key064 -ne $expectedPorts064[$i064] -or [int]$build064.$key064 -ne $expectedPorts064[$i064]){throw 'TASK064: exact ports mismatch.'}
}
foreach($kind064 in @('GameServer','LoginServer')){
    $jar064=Join-Path $runtime064 "libs/$kind064.jar"; Assert-Plain064 $jar064
    $key064=if($kind064 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash064=(Get-FileHash -LiteralPath $jar064 -Algorithm SHA256).Hash
    if($hash064 -ine $build064.$key064 -or $hash064 -ine $manifest064.$key064 -or $hash064 -ine (Read-Field064 $ownerPath064 $key064)){throw 'TASK064: JAR provenance mismatch.'}
}
if($Action -ceq 'Validate'){Write-Output 'TASK064_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
$runtimeId064=Get-LocalPlayRuntimeId $runtime064
$ports064=@{GameServer=@($actualPorts064[1]);LoginServer=@($actualPorts064[0],$actualPorts064[2])}
function Role-State064([string]$Role){return Get-LocalPlayRoleState $runtime064 $Role "$Role.jar" $ports064[$Role]}
function Write-Status064{
    [ordered]@{runtime=$runtime064;runtimeId=$runtimeId064;database=$schema064;source=$build064.codeCommit;game=(Role-State064 'GameServer');login=(Role-State064 'LoginServer');ports=$ports064;gameJarSha256=$build064.gameJarSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status064; return}
$javaHome064=Read-Field064 $settings064 'JavaHome'
$java064=Join-Path $javaHome064 'bin/java.exe'
if(!(Test-Path -LiteralPath $java064) -or [IO.File]::ReadAllText((Join-Path $javaHome064 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK064: JDK25 required.'}
foreach($dir064 in @('local-play','local-play/pids','local-play/control','local-play/logs')){
    $path064=Join-Path $runtime064 $dir064; Assert-Plain064 $path064
    New-Item -ItemType Directory -Path $path064 -Force | Out-Null
}
$lock064=[IO.File]::Open((Join-Path $runtime064 'local-play/operator.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role064([string]$Role){
    $state064=Role-State064 $Role
    if($state064.state -in @('STOPPED','STALE_RECORD')){return}
    if(!$state064.recordVerified -or ($state064.state -cne 'RUNNING' -and !($state064.state -ceq 'INCONSISTENT' -and $state064.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK064: STOP ownership rejected: $Role/$($state064.state)"}
    $process064=Get-Process -Id $state064.pid -ErrorAction Stop
    if($process064.StartTime.ToUniversalTime().Ticks -ne [long]$state064.startTimeUtcTicks){throw 'TASK064: stale PID.'}
    $request064=Join-Path $runtime064 "local-play/control/stop-$Role.properties"
    if(Test-Path -LiteralPath $request064){Move-Item -LiteralPath $request064 -Destination ($request064+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body064="version=1`nowner=TASK064_CAMPAIGN`nruntime=$runtimeId064`nrole=$Role`npid=$($state064.pid)`nstartTimeUtcTicks=$($state064.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    $temporary064=$request064+'.tmp'; [IO.File]::WriteAllText($temporary064,$body064,[Text.UTF8Encoding]::new($false)); Move-Item -LiteralPath $temporary064 -Destination $request064
    $timeout064=[int](Read-Field064 $settings064 'StopTimeoutSeconds')
    if($timeout064 -lt 10 -or $timeout064 -gt 600){throw 'TASK064: invalid stop timeout.'}
    $null=$process064.WaitForExit($timeout064*1000)
    if(!$process064.HasExited -or (Get-LocalPlayPortOwners $ports064[$Role]).Count){throw "TASK064: graceful STOP incomplete: $Role; force fallback absent."}
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state064.pid)"
}
function Start-Role064([string]$Role){
    $kind064=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $options064=[IO.File]::ReadAllText((Join-Path $runtime064 "$kind064/java.cfg")).Trim()
    if($options064 -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK064: foreign startup agent/marker forbidden.'}
    $stamp064=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments064="$options064 -Dphantom.localplay.runtime=$runtimeId064 -Dphantom.localplay.role=$Role -Dphantom.task064.control=true -jar ..\libs\$Role.jar"
    $process064=Start-Process -FilePath $java064 -ArgumentList $arguments064 -WorkingDirectory (Join-Path $runtime064 $kind064) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime064 "local-play/logs/$Role-$stamp064-stdout.log") -RedirectStandardError (Join-Path $runtime064 "local-play/logs/$Role-$stamp064-stderr.log") -PassThru
    $record064=[pscustomobject]@{pid=$process064.Id;startTimeUtcTicks=$process064.StartTime.ToUniversalTime().Ticks}
    Save-LocalPlayRecord (Join-Path $runtime064 "local-play/pids/$Role.json") $Role $record064 "$Role.jar" $runtimeId064
    $timeout064=[int](Read-Field064 $settings064 $(if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}))
    if($timeout064 -lt 1 -or $timeout064 -gt 600){throw 'TASK064: invalid startup timeout.'}
    $until064=[DateTime]::UtcNow.AddSeconds($timeout064)
    do{
        if($process064.HasExited){throw "TASK064: $Role exited; inspect own logs."}
        $state064=Role-State064 $Role
        if($state064.state -ceq 'RUNNING' -and $state064.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process064.Id)";return}
        if($state064.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK064: foreign port owner.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until064)
    throw "TASK064: $Role startup timed out; no force fallback."
}
try{
    if($Action -ceq 'Stop'){Stop-Role064 'GameServer';Stop-Role064 'LoginServer'}
    else{
        foreach($kind064 in @('GameServer','LoginServer')){if((Role-State064 $kind064).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK064: START requires both own roles stopped and ports free.'}}
        Start-Role064 'LoginServer';Start-Role064 'GameServer'
    }
    Write-Status064
}finally{$lock064.Dispose()}
