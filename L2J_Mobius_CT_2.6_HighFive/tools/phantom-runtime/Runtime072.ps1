[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
. (Join-Path $PSScriptRoot 'Task072Common.ps1')
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain072 $runtime
$ownerPath=Join-Path $runtime 'ownership072.properties'
$schema=Read-Field072 $ownerPath 'schema'
$module=Read-Field072 $ownerPath 'sourceModuleRoot'
$binding=Assert-Binding072 $runtime $module $schema (Read-Field072 $ownerPath 'role')
if((Read-Field072 $ownerPath 'version') -cne '1' -or (Read-Field072 $ownerPath 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field072 $ownerPath 'runtimeRoot')).TrimEnd('\') -ine $runtime){throw 'TASK072: root/owner mismatch.'}
if([IO.Path]::GetFullPath($PSCommandPath) -ine (Join-Path $runtime 'tools/Runtime072.ps1')){throw 'TASK072: use the runtime-contained operator tool.'}
$manifest=Read-Json072 (Join-Path $runtime 'local-play.json')
$build=Read-Json072 (Join-Path $runtime 'BUILD_INFO072.json')
$sourcePath=Join-Path $runtime 'SOURCE_BUILD_INFO072.json'
$source=Read-Json072 $sourcePath
$settings=Join-Path $runtime 'runtime.local.ini'
if((Read-Field072 $settings 'DatabaseName') -cne $schema -or $manifest.format -ne 1 -or $manifest.owner -cne $binding.owner -or $manifest.databaseName -cne $schema -or $manifest.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $build.format -ne 1 -or $build.owner -cne $binding.owner -or $build.databaseName -cne $schema -or $build.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $manifest.codeSha -cne $build.codeCommit -or $build.sourceState -cne 'COMMITTED'){throw 'TASK072: source/schema manifests differ.'}
if($source.format -ne 1 -or $source.owner -cne $binding.owner -or $source.sourceState -cne 'COMMITTED' -or $source.codeCommit -cne $build.codeCommit -or [IO.Path]::GetFullPath($source.sourceModuleRoot).TrimEnd('\') -ine [IO.Path]::GetFullPath($module).TrimEnd('\') -or (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ine $build.buildReceiptSha256){throw 'TASK072: committed source receipt differs.'}
foreach($key in @('sourceArchiveSha256','committedTreeSha256','testTreeSha256')){if($build.$key -cnotmatch '^[0-9a-fA-F]{64}$' -or $source.$key -ine $build.$key){throw 'TASK072: source/archive/test provenance differs.'}}
foreach($role in @('game','login')){Assert-Jdbc072 (Read-Field072 (Join-Path $runtime "$role/config/Database.ini") 'URL') $schema}
$gameConfig=Join-Path $runtime 'game/config/Server.ini'
$loginConfig=Join-Path $runtime 'login/config/Server.ini'
$actualPorts=@([int](Read-Field072 $loginConfig 'LoginserverPort'),[int](Read-Field072 $gameConfig 'GameserverPort'),[int](Read-Field072 $loginConfig 'LoginPort'))
if([int](Read-Field072 $gameConfig 'LoginPort') -ne $actualPorts[2] -or (Read-Field072 $gameConfig 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field072 $gameConfig 'LoginHost') -cne '127.0.0.1' -or (Read-Field072 $loginConfig 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field072 $loginConfig 'LoginHostname') -cne '127.0.0.1' -or (Read-Field072 $gameConfig 'AcceptAlternateID') -cne 'False' -or (Read-Field072 $loginConfig 'AcceptNewGameServer') -cne 'False'){throw 'TASK072: exact loopback registration binding required.'}
for($i=0;$i -lt 3;$i++){
    $key=@('clientLoginPort','gamePort','controlPort')[$i]
    if($actualPorts[$i] -ne $binding.ports[$i] -or [int](Read-Field072 $ownerPath $key) -ne $actualPorts[$i] -or [int]$manifest.$key -ne $actualPorts[$i] -or [int]$build.$key -ne $actualPorts[$i]){throw 'TASK072: exact ports differ.'}
}
foreach($role in @('GameServer','LoginServer')){
    $jar=Join-Path $runtime "libs/$role.jar"
    Assert-Plain072 $jar
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if($hash -ine $build.$key -or $hash -ine $source.$key -or $hash -ine $manifest.$key -or $hash -ine (Read-Field072 $ownerPath $key)){throw 'TASK072: whole JAR provenance differs.'}
}
$toolManifest=Join-Path $runtime 'TOOLS072.tsv'
Assert-Plain072 $toolManifest
if((Get-FileHash -LiteralPath $toolManifest -Algorithm SHA256).Hash -ine $build.toolManifestSha256){throw 'TASK072: tool manifest differs.'}
$toolRows=@(Import-Csv -LiteralPath $toolManifest -Delimiter "`t")
$toolPaths=@('tools/Runtime072.ps1','tools/Task072Common.ps1','tools/LocalPlay-Ownership.ps1','tools/Task072DatabaseProbe.java','tools/classes/Task072DatabaseProbe.class')
if($toolRows.Count -ne $toolPaths.Count -or @($toolRows.relativePath | Select-Object -Unique).Count -ne $toolPaths.Count){throw 'TASK072: exact tool set required.'}
foreach($tool in $toolRows){
    if($tool.relativePath -cnotin $toolPaths){throw 'TASK072: unknown tool path.'}
    $path=Join-Path $runtime $tool.relativePath
    Assert-Plain072 $path
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $tool.sha256){throw 'TASK072: operator tool provenance differs.'}
}
$geoManifest=Join-Path $runtime 'GEODATA072.tsv'
Assert-Plain072 $geoManifest
if((Get-FileHash -LiteralPath $geoManifest -Algorithm SHA256).Hash -ine $build.geoManifestSha256){throw 'TASK072: GeoData manifest differs.'}
if($Action -cin @('Validate','Start')){
	$price072=Join-Path $runtime 'game/data/economy070/pricing070.tsv'
	$pin072=Join-Path $runtime 'game/data/economy070/pricing070.sha256'
	Assert-Plain072 $price072
	Assert-Plain072 $pin072
	$pinned072=[IO.File]::ReadAllText($pin072).Trim()
	if($pinned072 -cnotmatch '^[0-9a-f]{64}$' -or (Get-FileHash -LiteralPath $price072 -Algorithm SHA256).Hash.ToLowerInvariant() -cne $pinned072){throw 'TASK072: applied price payload checksum differs; START blocked.'}
    $regions=@(Import-Csv -LiteralPath $geoManifest -Delimiter "`t")
    if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203){throw 'TASK072: exact203 physical regions required.'}
    foreach($region in $regions){
        if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$'){throw 'TASK072: invalid region receipt.'}
        $path=Join-Path $runtime $region.relativePath
        Assert-Plain072 $path
        if((Get-Item -LiteralPath $path).Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK072: physical GeoData differs.'}
    }
}
$javaHome=Read-Field072 $settings 'JavaHome'
Assert-Java072 $javaHome
$java=Join-Path $javaHome 'bin/java.exe'
if($Action -ceq 'Validate'){Write-Output 'TASK072_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. (Join-Path $runtime 'tools/LocalPlay-Ownership.ps1')
$runtimeId=Get-LocalPlayRuntimeId $runtime
$ports=@{GameServer=@($actualPorts[1]);LoginServer=@($actualPorts[0],$actualPorts[2])}
function Role-State072([string]$Role){
    foreach($relative in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain072 (Join-Path $runtime $relative)}
    $record=Join-Path $runtime "local-play/pids/$Role.json"
    if(Test-Path -LiteralPath $record){$null=Read-Json072 $record}
    foreach($row in @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Where-Object {$_.CommandLine -and $_.CommandLine -match ('(?:^|\s)-Dphantom\.localplay\.runtime='+$runtimeId+'(?:\s|$)')})){
        if([string]$row.ExecutablePath -ine $java -or [string]$row.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task072\.control=true(?:\s|$)' -or [string]$row.CommandLine -match '(?:^|\s)-Dphantom\.task(?!072\.control=true(?:\s|$))' -or [string]$row.CommandLine -match '-javaagent:|-agentlib:|-agentpath:'){throw 'TASK072: marked process has foreign executable/control/agent authority.'}
    }
    return Get-LocalPlayRoleState $runtime $Role "$Role.jar" $ports[$Role]
}
function Database-Connections072([ValidateSet('status','quiescent')][string]$Mode){
    $value=@(& $java '-Dfile.encoding=UTF-8' -cp ((Join-Path $runtime 'tools/classes')+';'+(Join-Path $runtime 'libs/*')) Task072DatabaseProbe $Mode $runtime $schema)
    if($LASTEXITCODE -or $value.Count -ne 1 -or [string]$value[0] -cnotmatch '^[0-9]+$'){throw 'TASK072: read-only own database connection check failed.'}
    return [long]$value[0]
}
function Write-Status072{
    [ordered]@{runtime=$runtime;runtimeId=$runtimeId;owner=$binding.owner;database=$schema;source=$build.codeCommit;game=(Role-State072 'GameServer');login=(Role-State072 'LoginServer');ports=$ports;databaseConnections=(Database-Connections072 'status');gameJarSha256=$build.gameJarSha256;sourceArchiveSha256=$build.sourceArchiveSha256;testTreeSha256=$build.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status072; return}
foreach($relative in @('local-play','local-play/pids','local-play/control','local-play/logs')){Assert-Plain072 (Join-Path $runtime $relative)}
$lockPath=Join-Path $runtime 'local-play/operator.lock'
Assert-Plain072 $lockPath
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role072([string]$Role){
    $state=Role-State072 $Role
    if($state.state -cin @('STOPPED','STALE_RECORD')){return}
    if(!$state.recordVerified -or ($state.state -cne 'RUNNING' -and !($state.state -ceq 'INCONSISTENT' -and $state.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK072: STOP ownership rejected: $Role/$($state.state)"}
    $process=Get-Process -Id $state.pid -ErrorAction Stop
    if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'TASK072: stale PID incarnation.'}
    $timeout=[int](Read-Field072 $settings 'StopTimeoutSeconds')
    if($timeout -lt 10 -or $timeout -gt 600){throw 'TASK072: bounded stop timeout required.'}
    $request=Join-Path $runtime "local-play/control/stop-$Role.properties"
    Assert-Plain072 $request
    if(Test-Path -LiteralPath $request){Move-Item -LiteralPath $request -Destination ($request+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body="version=1`nowner=$($binding.owner)`nruntime=$runtimeId`nrole=$Role`npid=$($state.pid)`nstartTimeUtcTicks=$($state.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    Write-Text072 ($request+'.tmp') $body
    Move-Item -LiteralPath ($request+'.tmp') -Destination $request
    $null=$process.WaitForExit($timeout*1000)
    if(!$process.HasExited -or (Get-LocalPlayPortOwners $ports[$Role]).Count){throw "TASK072: STOP_BLOCKED $Role; no force fallback."}
    $recordPath=Join-Path $runtime "local-play/pids/$Role.json"
    $record=Read-Json072 $recordPath
    if([long]$record.pid -ne [long]$state.pid -or [long]$record.startTimeUtcTicks -ne [long]$state.startTimeUtcTicks -or $record.runtime -cne $runtimeId -or $record.role -cne $Role){throw 'TASK072: stopped incarnation receipt changed.'}
    $archive=Join-Path $runtime 'local-play/stopped-pids'
    Assert-Plain072 $archive
    New-Item -ItemType Directory -Path $archive -Force | Out-Null
    $destination=Join-Path $archive "$Role-$($state.pid)-$($state.startTimeUtcTicks).json"
    Assert-Plain072 $destination
    if(Test-Path -LiteralPath $destination){throw 'TASK072: stopped receipt exists; no overwrite.'}
    Move-Item -LiteralPath $recordPath -Destination $destination
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state.pid)"
}
function Start-Role072([string]$Role){
    $kind=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg=Join-Path $runtime "$kind/java.cfg"
    Assert-Plain072 $cfg
    $options=[IO.File]::ReadAllText($cfg).Trim()
    if($options -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK072: foreign startup agent/marker forbidden.'}
    $key=if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}
    $timeout=[int](Read-Field072 $settings $key)
    if($timeout -lt 1 -or $timeout -gt 600){throw 'TASK072: bounded startup timeout required.'}
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $installation072=Read-Field072 $ownerPath 'installationId'
    if([guid]::Parse($installation072).ToString() -cne $installation072){throw 'TASK072: canonical installation UUID required.'}
    $arguments="$options -Dphantom.localplay.runtime=$runtimeId -Dphantom.localplay.role=$Role -Dphantom.task072.control=true -Dphantom.economy.schema=$schema -Dphantom.economy.installation=$installation072 -jar ..\libs\$Role.jar"
    $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $runtime $kind) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "local-play/logs/$Role-$stamp-stdout.log") -RedirectStandardError (Join-Path $runtime "local-play/logs/$Role-$stamp-stderr.log") -PassThru
    Save-LocalPlayRecord (Join-Path $runtime "local-play/pids/$Role.json") $Role ([pscustomobject]@{pid=$process.Id;startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks}) "$Role.jar" $runtimeId
    $until=[DateTime]::UtcNow.AddSeconds($timeout)
    do{
        if($process.HasExited){throw "TASK072: $Role exited; inspect own logs."}
        $state=Role-State072 $Role
        if($state.state -ceq 'RUNNING' -and $state.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process.Id)"; return}
        if($state.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK072: foreign port owner; no process action.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until)
    throw "TASK072: START_BLOCKED $Role; no force fallback."
}
$startPriceLock=$null
try{
    if($Action -ceq 'Stop'){
        Stop-Role072 'GameServer'; Stop-Role072 'LoginServer'
        $null=Database-Connections072 'quiescent'
    }else{
        $priceLock=Join-Path $runtime 'game/.price072.lock'
        Assert-Plain072 $priceLock
        if(Test-Path -LiteralPath $priceLock){throw 'TASK072: pricing Apply/Rollback lock exists; resolve stopped operator transaction before START.'}
        # Share the pricing CreateNew lock until both native roles own their ports.
        # A concurrent Apply cannot pass STOPPED and start writing while Main starts.
        $startPriceLock=[IO.File]::Open($priceLock,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        foreach($role in @('GameServer','LoginServer')){if((Role-State072 $role).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK072: START requires stopped own roles and free ports.'}}
        $null=Database-Connections072 'quiescent'
        Start-Role072 'LoginServer'; Start-Role072 'GameServer'
    }
    Write-Status072
}finally{
    if($startPriceLock){$startPriceLock.Dispose();Remove-Item -LiteralPath $priceLock}
    $lock.Dispose()
}
