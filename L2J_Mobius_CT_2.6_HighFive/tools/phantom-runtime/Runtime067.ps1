[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop

function Assert-Plain067([string]$Path){
    $absolute067=[IO.Path]::GetFullPath($Path)
    $walk067=[IO.Path]::GetPathRoot($absolute067)
    foreach($part067 in $absolute067.Substring($walk067.Length).Split('\')){
        if(!$part067){continue}
        $walk067=Join-Path $walk067 $part067
        if((Test-Path -LiteralPath $walk067) -and ((Get-Item -LiteralPath $walk067 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK067: plain physical paths required.'}
    }
}
function Read-Field067([string]$Path,[string]$Key){
    Assert-Plain067 $Path
    $matches067=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches067.Count -ne 1){throw "TASK067: need one key $Key."}
    return $matches067[0].Groups[1].Value.Trim()
}
function Read-Json067([string]$Path){
    Assert-Plain067 $Path
    if((Get-Item -LiteralPath $Path).Length -gt 16384){throw 'TASK067: bounded provenance manifest required.'}
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

$runtime067=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain067 $runtime067
$ownerPath067=Join-Path $runtime067 'ownership067.properties'
$schema067=Read-Field067 $ownerPath067 'schema'
if((Read-Field067 $ownerPath067 'version') -cne '1' -or (Read-Field067 $ownerPath067 'owner') -cne 'TASK067_PERSONAL_ADVENTURE' -or [IO.Path]::GetFullPath((Read-Field067 $ownerPath067 'runtimeRoot')).TrimEnd('\') -ine $runtime067){throw 'TASK067: root/owner mismatch.'}
$module067=[IO.Path]::GetFullPath((Read-Field067 $ownerPath067 'sourceModuleRoot')).TrimEnd('\')
if((Split-Path $module067 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK067: source module differs.'}
$role067=Read-Field067 $ownerPath067 'role'
if($schema067 -ceq 'l2jmobiush5_personal_test_067'){
    if($role067 -cne 'MUTABLE_FIXTURE' -or $runtime067 -ine (Join-Path (Split-Path $module067 -Parent) '.phantom-local/task067/native-test')){throw 'TASK067: exact fixture root required.'}
    $expectedPorts067=@(2248,7948,9248)
}elseif($schema067 -ceq 'l2jmobiush5_personal_release_067'){
    if($role067 -cne 'RELEASE_CANDIDATE' -or $runtime067 -ine (Join-Path ([IO.Path]::GetPathRoot($module067)) 'PhantomWorld/Personal-Adventure-067-candidate')){throw 'TASK067: exact authorized external candidate role/root required.'}
    $expectedPorts067=@(2148,7848,9148)
}else{throw 'TASK067: historical/foreign schema forbidden.'}
$manifest067=Read-Json067 (Join-Path $runtime067 'local-play.json')
$build067=Read-Json067 (Join-Path $runtime067 'BUILD_INFO067.json')
$sourceInfoPath067=Join-Path $runtime067 'SOURCE_BUILD_INFO067.json'
$sourceInfo067=Read-Json067 $sourceInfoPath067
$settings067=Join-Path $runtime067 'runtime.local.ini'
if((Read-Field067 $settings067 'DatabaseName') -cne $schema067 -or $manifest067.format -ne 1 -or $manifest067.owner -cne 'TASK067_PERSONAL_ADVENTURE' -or $manifest067.databaseName -cne $schema067 -or $build067.format -ne 1 -or $build067.owner -cne 'TASK067_PERSONAL_ADVENTURE' -or $build067.databaseName -cne $schema067 -or $manifest067.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $manifest067.codeSha -cne $build067.codeCommit -or $build067.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $build067.sourceState -cne 'COMMITTED'){throw 'TASK067: source/schema manifests mismatch.'}
if($sourceInfo067.format -ne 1 -or $sourceInfo067.owner -cne 'TASK067_PERSONAL_ADVENTURE' -or $sourceInfo067.sourceState -cne 'COMMITTED' -or $sourceInfo067.codeCommit -cne $build067.codeCommit -or [IO.Path]::GetFullPath($sourceInfo067.sourceModuleRoot).TrimEnd('\') -ine $module067 -or $sourceInfo067.sourceArchiveSha256 -ine $build067.sourceArchiveSha256 -or $sourceInfo067.testTreeSha256 -ine $build067.testTreeSha256 -or $sourceInfo067.committedTreeSha256 -ine $build067.committedTreeSha256 -or $build067.committedTreeSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or $build067.sourceArchiveSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or $build067.testTreeSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or (Get-FileHash -LiteralPath $sourceInfoPath067 -Algorithm SHA256).Hash -ine $build067.buildReceiptSha256){throw 'TASK067: committed archive/source/test tree receipt mismatch.'}
foreach($kind067 in @('game','login')){
    $url067=Read-Field067 (Join-Path $runtime067 "$kind067/config/Database.ini") 'URL'
    if($url067 -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema067+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK067: JDBC endpoint mismatch.'}
}
$gameConfig067=Join-Path $runtime067 'game/config/Server.ini'
$loginConfig067=Join-Path $runtime067 'login/config/Server.ini'
$actualPorts067=@([int](Read-Field067 $loginConfig067 'LoginserverPort'),[int](Read-Field067 $gameConfig067 'GameserverPort'),[int](Read-Field067 $loginConfig067 'LoginPort'))
if([int](Read-Field067 $gameConfig067 'LoginPort') -ne $actualPorts067[2] -or (Read-Field067 $gameConfig067 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field067 $loginConfig067 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field067 $gameConfig067 'AcceptAlternateID') -cne 'False' -or (Read-Field067 $loginConfig067 'AcceptNewGameServer') -cne 'False'){throw 'TASK067: loopback registration/control binding differs.'}
for($i067=0;$i067 -lt 3;$i067++){
    $key067=@('clientLoginPort','gamePort','controlPort')[$i067]
    if($actualPorts067[$i067] -ne $expectedPorts067[$i067] -or [int](Read-Field067 $ownerPath067 $key067) -ne $expectedPorts067[$i067] -or [int]$manifest067.$key067 -ne $expectedPorts067[$i067] -or [int]$build067.$key067 -ne $expectedPorts067[$i067]){throw 'TASK067: exact ports mismatch.'}
}
foreach($kind067 in @('GameServer','LoginServer')){
    $jar067=Join-Path $runtime067 "libs/$kind067.jar"
    Assert-Plain067 $jar067
    $key067=if($kind067 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash067=(Get-FileHash -LiteralPath $jar067 -Algorithm SHA256).Hash
    if($hash067 -ine $build067.$key067 -or $hash067 -ine $manifest067.$key067 -or $hash067 -ine $sourceInfo067.$key067 -or $hash067 -ine (Read-Field067 $ownerPath067 $key067)){throw 'TASK067: whole JAR provenance mismatch.'}
}
$helper067=Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
if(!(Test-Path -LiteralPath $helper067)){$helper067=Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1'}
foreach($tool067 in @(@{path=$helper067;key='ownershipHelperSha256'},@{path=$PSCommandPath;key='runtimeToolSha256'})){
    Assert-Plain067 $tool067.path
    $hash067=(Get-FileHash -LiteralPath $tool067.path -Algorithm SHA256).Hash
    if($hash067 -ine $build067.($tool067.key) -or $hash067 -ine (Read-Field067 $ownerPath067 $tool067.key)){throw 'TASK067: operator tool provenance mismatch.'}
}
$geoManifest067=Join-Path $runtime067 'GEODATA067.tsv'
Assert-Plain067 $geoManifest067
if((Get-FileHash -LiteralPath $geoManifest067 -Algorithm SHA256).Hash -ine $build067.geoManifestSha256){throw 'TASK067: GeoData manifest differs.'}
if($Action -cin @('Validate','Start')){
    $regions067=@(Import-Csv -LiteralPath $geoManifest067 -Delimiter "`t")
    if($regions067.Count -ne 203 -or @($regions067.relativePath | Select-Object -Unique).Count -ne 203){throw 'TASK067: exact 203-region GeoData manifest required.'}
    foreach($region067 in $regions067){
        if($region067.kind -cne 'GeoData' -or $region067.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $region067.sha256 -cnotmatch '^[0-9A-Fa-f]{64}$'){throw 'TASK067: invalid region receipt.'}
        $regionPath067=Join-Path $runtime067 $region067.relativePath
        Assert-Plain067 $regionPath067
        $regionFile067=Get-Item -LiteralPath $regionPath067 -Force
        if($regionFile067.PSIsContainer -or $regionFile067.Length -ne [long]$region067.bytes -or (Get-FileHash -LiteralPath $regionPath067 -Algorithm SHA256).Hash -ine $region067.sha256){throw 'TASK067: physical GeoData differs.'}
    }
}
if($Action -ceq 'Validate'){Write-Output 'TASK067_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. $helper067
$runtimeId067=Get-LocalPlayRuntimeId $runtime067
$ports067=@{GameServer=@($actualPorts067[1]);LoginServer=@($actualPorts067[0],$actualPorts067[2])}
function Role-State067([string]$Role){
    foreach($path067 in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain067 (Join-Path $runtime067 $path067)}
    $state067=Get-LocalPlayRoleState $runtime067 $Role "$Role.jar" $ports067[$Role]
    if($state067.pid -gt 0){
        $row067=Get-CimInstance Win32_Process -Filter "ProcessId=$($state067.pid)" -ErrorAction Stop
        if(!$row067 -or [string]$row067.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task067\.control=true(?:\s|$)' -or [string]$row067.CommandLine -match '(?:^|\s)-Dphantom\.task(?!067\.control=true(?:\s|$))'){throw 'TASK067: marked process has no exact ordinary control ownership.'}
    }
    return $state067
}
function Write-Status067{
    [ordered]@{runtime=$runtime067;runtimeId=$runtimeId067;owner='TASK067_PERSONAL_ADVENTURE';database=$schema067;source=$build067.codeCommit;game=(Role-State067 'GameServer');login=(Role-State067 'LoginServer');ports=$ports067;gameJarSha256=$build067.gameJarSha256;sourceArchiveSha256=$build067.sourceArchiveSha256;testTreeSha256=$build067.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status067; return}
$javaHome067=Read-Field067 $settings067 'JavaHome'
$java067=Join-Path $javaHome067 'bin/java.exe'
Assert-Plain067 $java067
Assert-Plain067 (Join-Path $javaHome067 'release')
if(!(Test-Path -LiteralPath $java067) -or [IO.File]::ReadAllText((Join-Path $javaHome067 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK067: JDK25 required.'}
foreach($dir067 in @('local-play','local-play/pids','local-play/control','local-play/logs')){
    $path067=Join-Path $runtime067 $dir067
    Assert-Plain067 $path067
    New-Item -ItemType Directory -Path $path067 -Force | Out-Null
}
$lockPath067=Join-Path $runtime067 'local-play/operator.lock'
Assert-Plain067 $lockPath067
$lock067=[IO.File]::Open($lockPath067,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role067([string]$Role){
    $state067=Role-State067 $Role
    if($state067.state -in @('STOPPED','STALE_RECORD')){return}
    if(!$state067.recordVerified -or ($state067.state -cne 'RUNNING' -and !($state067.state -ceq 'INCONSISTENT' -and $state067.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK067: STOP ownership rejected: $Role/$($state067.state)"}
    $process067=Get-Process -Id $state067.pid -ErrorAction Stop
    if($process067.StartTime.ToUniversalTime().Ticks -ne [long]$state067.startTimeUtcTicks){throw 'TASK067: stale PID incarnation.'}
    $timeout067=[int](Read-Field067 $settings067 'StopTimeoutSeconds')
    if($timeout067 -lt 10 -or $timeout067 -gt 600){throw 'TASK067: invalid stop timeout.'}
    $request067=Join-Path $runtime067 "local-play/control/stop-$Role.properties"
    Assert-Plain067 $request067
    if(Test-Path -LiteralPath $request067){Move-Item -LiteralPath $request067 -Destination ($request067+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body067="version=1`nowner=TASK067_PERSONAL_ADVENTURE`nruntime=$runtimeId067`nrole=$Role`npid=$($state067.pid)`nstartTimeUtcTicks=$($state067.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    $temporary067=$request067+'.tmp'
    Assert-Plain067 $temporary067
    [IO.File]::WriteAllText($temporary067,$body067,[Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary067 -Destination $request067
    $null=$process067.WaitForExit($timeout067*1000)
    if(!$process067.HasExited -or (Get-LocalPlayPortOwners $ports067[$Role]).Count){throw "TASK067: graceful STOP incomplete: $Role; force fallback absent."}
    $recordPath067=Join-Path $runtime067 "local-play/pids/$Role.json"
    $record067=Read-Json067 $recordPath067
    if([long]$record067.pid -ne [long]$state067.pid -or [long]$record067.startTimeUtcTicks -ne [long]$state067.startTimeUtcTicks -or $record067.runtime -cne $runtimeId067 -or $record067.role -cne $Role){throw 'TASK067: stopped incarnation receipt changed; preserve it.'}
    $archive067=Join-Path $runtime067 'local-play/stopped-pids'
    Assert-Plain067 $archive067
    New-Item -ItemType Directory -Path $archive067 -Force | Out-Null
    $archived067=Join-Path $archive067 "$Role-$($state067.pid)-$($state067.startTimeUtcTicks).json"
    Assert-Plain067 $archived067
    if(Test-Path -LiteralPath $archived067){throw 'TASK067: stopped receipt already archived; no overwrite.'}
    Move-Item -LiteralPath $recordPath067 -Destination $archived067
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state067.pid)"
}
function Start-Role067([string]$Role){
    $kind067=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg067=Join-Path $runtime067 "$kind067/java.cfg"
    Assert-Plain067 $cfg067
    $options067=[IO.File]::ReadAllText($cfg067).Trim()
    if($options067 -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK067: foreign startup agent/marker forbidden.'}
    $timeout067=[int](Read-Field067 $settings067 $(if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}))
    if($timeout067 -lt 1 -or $timeout067 -gt 600){throw 'TASK067: invalid startup timeout.'}
    $stamp067=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments067="$options067 -Dphantom.localplay.runtime=$runtimeId067 -Dphantom.localplay.role=$Role -Dphantom.task067.control=true -jar ..\libs\$Role.jar"
    $process067=Start-Process -FilePath $java067 -ArgumentList $arguments067 -WorkingDirectory (Join-Path $runtime067 $kind067) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime067 "local-play/logs/$Role-$stamp067-stdout.log") -RedirectStandardError (Join-Path $runtime067 "local-play/logs/$Role-$stamp067-stderr.log") -PassThru
    $record067=[pscustomobject]@{pid=$process067.Id;startTimeUtcTicks=$process067.StartTime.ToUniversalTime().Ticks}
    Save-LocalPlayRecord (Join-Path $runtime067 "local-play/pids/$Role.json") $Role $record067 "$Role.jar" $runtimeId067
    $until067=[DateTime]::UtcNow.AddSeconds($timeout067)
    do{
        if($process067.HasExited){throw "TASK067: $Role exited; inspect own logs."}
        $state067=Role-State067 $Role
        if($state067.state -ceq 'RUNNING' -and $state067.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process067.Id)";return}
        if($state067.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK067: foreign port owner.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until067)
    throw "TASK067: $Role startup timed out; no force fallback."
}
try{
    if($Action -ceq 'Stop'){Stop-Role067 'GameServer';Stop-Role067 'LoginServer'}
    else{
        foreach($kind067 in @('GameServer','LoginServer')){if((Role-State067 $kind067).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK067: START requires both own roles stopped and ports free.'}}
        Start-Role067 'LoginServer'
        Start-Role067 'GameServer'
    }
    Write-Status067
}finally{$lock067.Dispose()}
