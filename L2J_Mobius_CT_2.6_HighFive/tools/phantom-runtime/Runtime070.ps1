[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
. (Join-Path $PSScriptRoot 'Task070Common.ps1')
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain070 $runtime
$ownerPath=Join-Path $runtime 'ownership070.properties'
$schema=Read-Field070 $ownerPath 'schema'
$module=Read-Field070 $ownerPath 'sourceModuleRoot'
$binding=Assert-Binding070 $runtime $module $schema (Read-Field070 $ownerPath 'role')
if((Read-Field070 $ownerPath 'version') -cne '1' -or (Read-Field070 $ownerPath 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field070 $ownerPath 'runtimeRoot')).TrimEnd('\') -ine $runtime){throw 'TASK070: root/owner mismatch.'}
if([IO.Path]::GetFullPath($PSCommandPath) -ine (Join-Path $runtime 'tools/Runtime070.ps1')){throw 'TASK070: use the runtime-contained operator tool.'}
$manifest=Read-Json070 (Join-Path $runtime 'local-play.json')
$build=Read-Json070 (Join-Path $runtime 'BUILD_INFO070.json')
$sourcePath=Join-Path $runtime 'SOURCE_BUILD_INFO070.json'
$source=Read-Json070 $sourcePath
$settings=Join-Path $runtime 'runtime.local.ini'
if((Read-Field070 $settings 'DatabaseName') -cne $schema -or $manifest.format -ne 1 -or $manifest.owner -cne $binding.owner -or $manifest.databaseName -cne $schema -or $manifest.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $build.format -ne 1 -or $build.owner -cne $binding.owner -or $build.databaseName -cne $schema -or $build.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $manifest.codeSha -cne $build.codeCommit -or $build.sourceState -cne 'COMMITTED'){throw 'TASK070: source/schema manifests differ.'}
if($source.format -ne 1 -or $source.owner -cne $binding.owner -or $source.sourceState -cne 'COMMITTED' -or $source.codeCommit -cne $build.codeCommit -or [IO.Path]::GetFullPath($source.sourceModuleRoot).TrimEnd('\') -ine [IO.Path]::GetFullPath($module).TrimEnd('\') -or (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ine $build.buildReceiptSha256){throw 'TASK070: committed source receipt differs.'}
foreach($key in @('sourceArchiveSha256','committedTreeSha256','testTreeSha256')){if($build.$key -cnotmatch '^[0-9a-fA-F]{64}$' -or $source.$key -ine $build.$key){throw 'TASK070: source/archive/test provenance differs.'}}
foreach($role in @('game','login')){Assert-Jdbc070 (Read-Field070 (Join-Path $runtime "$role/config/Database.ini") 'URL') $schema}
$gameConfig=Join-Path $runtime 'game/config/Server.ini'
$loginConfig=Join-Path $runtime 'login/config/Server.ini'
$actualPorts=@([int](Read-Field070 $loginConfig 'LoginserverPort'),[int](Read-Field070 $gameConfig 'GameserverPort'),[int](Read-Field070 $loginConfig 'LoginPort'))
if([int](Read-Field070 $gameConfig 'LoginPort') -ne $actualPorts[2] -or (Read-Field070 $gameConfig 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field070 $gameConfig 'LoginHost') -cne '127.0.0.1' -or (Read-Field070 $loginConfig 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field070 $loginConfig 'LoginHostname') -cne '127.0.0.1' -or (Read-Field070 $gameConfig 'AcceptAlternateID') -cne 'False' -or (Read-Field070 $loginConfig 'AcceptNewGameServer') -cne 'False'){throw 'TASK070: exact loopback registration binding required.'}
for($i=0;$i -lt 3;$i++){
    $key=@('clientLoginPort','gamePort','controlPort')[$i]
    if($actualPorts[$i] -ne $binding.ports[$i] -or [int](Read-Field070 $ownerPath $key) -ne $actualPorts[$i] -or [int]$manifest.$key -ne $actualPorts[$i] -or [int]$build.$key -ne $actualPorts[$i]){throw 'TASK070: exact ports differ.'}
}
foreach($role in @('GameServer','LoginServer')){
    $jar=Join-Path $runtime "libs/$role.jar"
    Assert-Plain070 $jar
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if($hash -ine $build.$key -or $hash -ine $source.$key -or $hash -ine $manifest.$key -or $hash -ine (Read-Field070 $ownerPath $key)){throw 'TASK070: whole JAR provenance differs.'}
}
$toolManifest=Join-Path $runtime 'TOOLS070.tsv'
Assert-Plain070 $toolManifest
if((Get-FileHash -LiteralPath $toolManifest -Algorithm SHA256).Hash -ine $build.toolManifestSha256){throw 'TASK070: tool manifest differs.'}
$toolRows=@(Import-Csv -LiteralPath $toolManifest -Delimiter "`t")
$toolPaths=@('tools/Runtime070.ps1','tools/Task070Common.ps1','tools/LocalPlay-Ownership.ps1','tools/Task070DatabaseProbe.java','tools/classes/Task070DatabaseProbe.class','tools/phantom-economy/Price-PhantomEconomy070.ps1','tools/phantom-economy/README070_RU.md')
if($toolRows.Count -ne $toolPaths.Count -or @($toolRows.relativePath | Select-Object -Unique).Count -ne $toolPaths.Count){throw 'TASK070: exact tool set required.'}
foreach($tool in $toolRows){
    if($tool.relativePath -cnotin $toolPaths){throw 'TASK070: unknown tool path.'}
    $path=Join-Path $runtime $tool.relativePath
    Assert-Plain070 $path
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $tool.sha256){throw 'TASK070: operator tool provenance differs.'}
}
$geoManifest=Join-Path $runtime 'GEODATA070.tsv'
Assert-Plain070 $geoManifest
if((Get-FileHash -LiteralPath $geoManifest -Algorithm SHA256).Hash -ine $build.geoManifestSha256){throw 'TASK070: GeoData manifest differs.'}
if($Action -cin @('Validate','Start')){
    $regions=@(Import-Csv -LiteralPath $geoManifest -Delimiter "`t")
    if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203){throw 'TASK070: exact203 physical regions required.'}
    foreach($region in $regions){
        if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$'){throw 'TASK070: invalid region receipt.'}
        $path=Join-Path $runtime $region.relativePath
        Assert-Plain070 $path
        if((Get-Item -LiteralPath $path).Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK070: physical GeoData differs.'}
    }
}
$javaHome=Read-Field070 $settings 'JavaHome'
Assert-Java070 $javaHome
$java=Join-Path $javaHome 'bin/java.exe'
if($Action -ceq 'Validate'){Write-Output 'TASK070_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. (Join-Path $runtime 'tools/LocalPlay-Ownership.ps1')
$runtimeId=Get-LocalPlayRuntimeId $runtime
$ports=@{GameServer=@($actualPorts[1]);LoginServer=@($actualPorts[0],$actualPorts[2])}
function Role-State070([string]$Role){
    foreach($relative in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain070 (Join-Path $runtime $relative)}
    $record=Join-Path $runtime "local-play/pids/$Role.json"
    if(Test-Path -LiteralPath $record){$null=Read-Json070 $record}
    foreach($row in @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Where-Object {$_.CommandLine -and $_.CommandLine -match ('(?:^|\s)-Dphantom\.localplay\.runtime='+$runtimeId+'(?:\s|$)')})){
        if([string]$row.ExecutablePath -ine $java -or [string]$row.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task070\.control=true(?:\s|$)' -or [string]$row.CommandLine -match '(?:^|\s)-Dphantom\.task(?!070\.control=true(?:\s|$))' -or [string]$row.CommandLine -match '-javaagent:|-agentlib:|-agentpath:'){throw 'TASK070: marked process has foreign executable/control/agent authority.'}
    }
    return Get-LocalPlayRoleState $runtime $Role "$Role.jar" $ports[$Role]
}
function Database-Connections070([ValidateSet('status','quiescent')][string]$Mode){
    $value=@(& $java '-Dfile.encoding=UTF-8' -cp ((Join-Path $runtime 'tools/classes')+';'+(Join-Path $runtime 'libs/*')) Task070DatabaseProbe $Mode $runtime $schema)
    if($LASTEXITCODE -or $value.Count -ne 1 -or [string]$value[0] -cnotmatch '^[0-9]+$'){throw 'TASK070: read-only own database connection check failed.'}
    return [long]$value[0]
}
function Write-Status070{
    [ordered]@{runtime=$runtime;runtimeId=$runtimeId;owner=$binding.owner;database=$schema;source=$build.codeCommit;game=(Role-State070 'GameServer');login=(Role-State070 'LoginServer');ports=$ports;databaseConnections=(Database-Connections070 'status');gameJarSha256=$build.gameJarSha256;sourceArchiveSha256=$build.sourceArchiveSha256;testTreeSha256=$build.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status070; return}
foreach($relative in @('local-play','local-play/pids','local-play/control','local-play/logs')){Assert-Plain070 (Join-Path $runtime $relative)}
$lockPath=Join-Path $runtime 'local-play/operator.lock'
Assert-Plain070 $lockPath
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role070([string]$Role){
    $state=Role-State070 $Role
    if($state.state -cin @('STOPPED','STALE_RECORD')){return}
    if(!$state.recordVerified -or ($state.state -cne 'RUNNING' -and !($state.state -ceq 'INCONSISTENT' -and $state.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK070: STOP ownership rejected: $Role/$($state.state)"}
    $process=Get-Process -Id $state.pid -ErrorAction Stop
    if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'TASK070: stale PID incarnation.'}
    $timeout=[int](Read-Field070 $settings 'StopTimeoutSeconds')
    if($timeout -lt 10 -or $timeout -gt 600){throw 'TASK070: bounded stop timeout required.'}
    $request=Join-Path $runtime "local-play/control/stop-$Role.properties"
    Assert-Plain070 $request
    if(Test-Path -LiteralPath $request){Move-Item -LiteralPath $request -Destination ($request+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body="version=1`nowner=$($binding.owner)`nruntime=$runtimeId`nrole=$Role`npid=$($state.pid)`nstartTimeUtcTicks=$($state.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    Write-Text070 ($request+'.tmp') $body
    Move-Item -LiteralPath ($request+'.tmp') -Destination $request
    $null=$process.WaitForExit($timeout*1000)
    if(!$process.HasExited -or (Get-LocalPlayPortOwners $ports[$Role]).Count){throw "TASK070: STOP_BLOCKED $Role; no force fallback."}
    $recordPath=Join-Path $runtime "local-play/pids/$Role.json"
    $record=Read-Json070 $recordPath
    if([long]$record.pid -ne [long]$state.pid -or [long]$record.startTimeUtcTicks -ne [long]$state.startTimeUtcTicks -or $record.runtime -cne $runtimeId -or $record.role -cne $Role){throw 'TASK070: stopped incarnation receipt changed.'}
    $archive=Join-Path $runtime 'local-play/stopped-pids'
    Assert-Plain070 $archive
    New-Item -ItemType Directory -Path $archive -Force | Out-Null
    $destination=Join-Path $archive "$Role-$($state.pid)-$($state.startTimeUtcTicks).json"
    Assert-Plain070 $destination
    if(Test-Path -LiteralPath $destination){throw 'TASK070: stopped receipt exists; no overwrite.'}
    Move-Item -LiteralPath $recordPath -Destination $destination
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state.pid)"
}
function Start-Role070([string]$Role){
    $kind=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg=Join-Path $runtime "$kind/java.cfg"
    Assert-Plain070 $cfg
    $options=[IO.File]::ReadAllText($cfg).Trim()
    if($options -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK070: foreign startup agent/marker forbidden.'}
    $key=if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}
    $timeout=[int](Read-Field070 $settings $key)
    if($timeout -lt 1 -or $timeout -gt 600){throw 'TASK070: bounded startup timeout required.'}
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments="$options -Dphantom.localplay.runtime=$runtimeId -Dphantom.localplay.role=$Role -Dphantom.task070.control=true -jar ..\libs\$Role.jar"
    $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $runtime $kind) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "local-play/logs/$Role-$stamp-stdout.log") -RedirectStandardError (Join-Path $runtime "local-play/logs/$Role-$stamp-stderr.log") -PassThru
    Save-LocalPlayRecord (Join-Path $runtime "local-play/pids/$Role.json") $Role ([pscustomobject]@{pid=$process.Id;startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks}) "$Role.jar" $runtimeId
    $until=[DateTime]::UtcNow.AddSeconds($timeout)
    do{
        if($process.HasExited){throw "TASK070: $Role exited; inspect own logs."}
        $state=Role-State070 $Role
        if($state.state -ceq 'RUNNING' -and $state.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process.Id)"; return}
        if($state.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK070: foreign port owner; no process action.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until)
    throw "TASK070: START_BLOCKED $Role; no force fallback."
}
$startPriceLock=$null
try{
    if($Action -ceq 'Stop'){
        Stop-Role070 'GameServer'; Stop-Role070 'LoginServer'
        $null=Database-Connections070 'quiescent'
    }else{
        $priceLock=Join-Path $runtime 'game/.price070.lock'
        Assert-Plain070 $priceLock
        if(Test-Path -LiteralPath $priceLock){throw 'TASK070: pricing Apply/Rollback lock exists; resolve stopped operator transaction before START.'}
        # Share the pricing CreateNew lock until both native roles own their ports.
        # A concurrent Apply cannot pass STOPPED and start writing while Main starts.
        $startPriceLock=[IO.File]::Open($priceLock,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        foreach($role in @('GameServer','LoginServer')){if((Role-State070 $role).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK070: START requires stopped own roles and free ports.'}}
        $null=Database-Connections070 'quiescent'
        Start-Role070 'LoginServer'; Start-Role070 'GameServer'
    }
    Write-Status070
}finally{
    if($startPriceLock){$startPriceLock.Dispose();Remove-Item -LiteralPath $priceLock}
    $lock.Dispose()
}
