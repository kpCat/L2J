[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
. (Join-Path $PSScriptRoot 'Task069Common.ps1')
$Action=$Action.Substring(0,1).ToUpperInvariant()+$Action.Substring(1).ToLowerInvariant()
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
Assert-Plain069 $runtime
$ownerPath=Join-Path $runtime 'ownership069.properties'
$schema=Read-Field069 $ownerPath 'schema'
$module=Read-Field069 $ownerPath 'sourceModuleRoot'
$binding=Assert-Binding069 $runtime $module $schema (Read-Field069 $ownerPath 'role')
if((Read-Field069 $ownerPath 'version') -cne '1' -or (Read-Field069 $ownerPath 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field069 $ownerPath 'runtimeRoot')).TrimEnd('\') -ine $runtime){throw 'TASK069: root/owner mismatch.'}
if([IO.Path]::GetFullPath($PSCommandPath) -ine (Join-Path $runtime 'tools/Runtime069.ps1')){throw 'TASK069: use the runtime-contained operator tool.'}
$manifest=Read-Json069 (Join-Path $runtime 'local-play.json')
$build=Read-Json069 (Join-Path $runtime 'BUILD_INFO069.json')
$sourcePath=Join-Path $runtime 'SOURCE_BUILD_INFO069.json'
$source=Read-Json069 $sourcePath
$settings=Join-Path $runtime 'runtime.local.ini'
if((Read-Field069 $settings 'DatabaseName') -cne $schema -or $manifest.format -ne 1 -or $manifest.owner -cne $binding.owner -or $manifest.databaseName -cne $schema -or $manifest.databaseConfig -cne 'USER_CONFIRMED_EXISTING' -or $build.format -ne 1 -or $build.owner -cne $binding.owner -or $build.databaseName -cne $schema -or $build.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $manifest.codeSha -cne $build.codeCommit -or $build.sourceState -cne 'COMMITTED'){throw 'TASK069: source/schema manifests differ.'}
if($source.format -ne 1 -or $source.owner -cne $binding.owner -or $source.sourceState -cne 'COMMITTED' -or $source.codeCommit -cne $build.codeCommit -or [IO.Path]::GetFullPath($source.sourceModuleRoot).TrimEnd('\') -ine [IO.Path]::GetFullPath($module).TrimEnd('\') -or (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ine $build.buildReceiptSha256){throw 'TASK069: committed source receipt differs.'}
foreach($key in @('sourceArchiveSha256','committedTreeSha256','testTreeSha256')){if($build.$key -cnotmatch '^[0-9a-fA-F]{64}$' -or $source.$key -ine $build.$key){throw 'TASK069: source/archive/test provenance differs.'}}
foreach($role in @('game','login')){Assert-Jdbc069 (Read-Field069 (Join-Path $runtime "$role/config/Database.ini") 'URL') $schema}
$gameConfig=Join-Path $runtime 'game/config/Server.ini'
$loginConfig=Join-Path $runtime 'login/config/Server.ini'
$actualPorts=@([int](Read-Field069 $loginConfig 'LoginserverPort'),[int](Read-Field069 $gameConfig 'GameserverPort'),[int](Read-Field069 $loginConfig 'LoginPort'))
if([int](Read-Field069 $gameConfig 'LoginPort') -ne $actualPorts[2] -or (Read-Field069 $gameConfig 'GameserverHostname') -cne '127.0.0.1' -or (Read-Field069 $gameConfig 'LoginHost') -cne '127.0.0.1' -or (Read-Field069 $loginConfig 'LoginserverHostname') -cne '127.0.0.1' -or (Read-Field069 $loginConfig 'LoginHostname') -cne '127.0.0.1' -or (Read-Field069 $gameConfig 'AcceptAlternateID') -cne 'False' -or (Read-Field069 $loginConfig 'AcceptNewGameServer') -cne 'False'){throw 'TASK069: exact loopback registration binding required.'}
for($i=0;$i -lt 3;$i++){
    $key=@('clientLoginPort','gamePort','controlPort')[$i]
    if($actualPorts[$i] -ne $binding.ports[$i] -or [int](Read-Field069 $ownerPath $key) -ne $actualPorts[$i] -or [int]$manifest.$key -ne $actualPorts[$i] -or [int]$build.$key -ne $actualPorts[$i]){throw 'TASK069: exact ports differ.'}
}
foreach($role in @('GameServer','LoginServer')){
    $jar=Join-Path $runtime "libs/$role.jar"
    Assert-Plain069 $jar
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    $hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if($hash -ine $build.$key -or $hash -ine $source.$key -or $hash -ine $manifest.$key -or $hash -ine (Read-Field069 $ownerPath $key)){throw 'TASK069: whole JAR provenance differs.'}
}
$toolManifest=Join-Path $runtime 'TOOLS069.tsv'
Assert-Plain069 $toolManifest
if((Get-FileHash -LiteralPath $toolManifest -Algorithm SHA256).Hash -ine $build.toolManifestSha256){throw 'TASK069: tool manifest differs.'}
$toolRows=@(Import-Csv -LiteralPath $toolManifest -Delimiter "`t")
$toolPaths=@('tools/Runtime069.ps1','tools/Task069Common.ps1','tools/LocalPlay-Ownership.ps1','tools/Task069DatabaseProbe.java','tools/classes/Task069DatabaseProbe.class','tools/phantom-economy/Price-PhantomEconomy.ps1','tools/phantom-economy/README_RU.md')
if($toolRows.Count -ne $toolPaths.Count -or @($toolRows.relativePath | Select-Object -Unique).Count -ne $toolPaths.Count){throw 'TASK069: exact tool set required.'}
foreach($tool in $toolRows){
    if($tool.relativePath -cnotin $toolPaths){throw 'TASK069: unknown tool path.'}
    $path=Join-Path $runtime $tool.relativePath
    Assert-Plain069 $path
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $tool.sha256){throw 'TASK069: operator tool provenance differs.'}
}
$geoManifest=Join-Path $runtime 'GEODATA069.tsv'
Assert-Plain069 $geoManifest
if((Get-FileHash -LiteralPath $geoManifest -Algorithm SHA256).Hash -ine $build.geoManifestSha256){throw 'TASK069: GeoData manifest differs.'}
if($Action -cin @('Validate','Start')){
    $regions=@(Import-Csv -LiteralPath $geoManifest -Delimiter "`t")
    if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203){throw 'TASK069: exact203 physical regions required.'}
    foreach($region in $regions){
        if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$'){throw 'TASK069: invalid region receipt.'}
        $path=Join-Path $runtime $region.relativePath
        Assert-Plain069 $path
        if((Get-Item -LiteralPath $path).Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK069: physical GeoData differs.'}
    }
}
$javaHome=Read-Field069 $settings 'JavaHome'
Assert-Java069 $javaHome
$java=Join-Path $javaHome 'bin/java.exe'
if($Action -ceq 'Validate'){Write-Output 'TASK069_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
. (Join-Path $runtime 'tools/LocalPlay-Ownership.ps1')
$runtimeId=Get-LocalPlayRuntimeId $runtime
$ports=@{GameServer=@($actualPorts[1]);LoginServer=@($actualPorts[0],$actualPorts[2])}
function Role-State069([string]$Role){
    foreach($relative in @('local-play','local-play/pids',"local-play/pids/$Role.json",'local-play/control')){Assert-Plain069 (Join-Path $runtime $relative)}
    $record=Join-Path $runtime "local-play/pids/$Role.json"
    if(Test-Path -LiteralPath $record){$null=Read-Json069 $record}
    foreach($row in @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Where-Object {$_.CommandLine -and $_.CommandLine -match ('(?:^|\s)-Dphantom\.localplay\.runtime='+$runtimeId+'(?:\s|$)')})){
        if([string]$row.ExecutablePath -ine $java -or [string]$row.CommandLine -cnotmatch '(?:^|\s)-Dphantom\.task069\.control=true(?:\s|$)' -or [string]$row.CommandLine -match '(?:^|\s)-Dphantom\.task(?!069\.control=true(?:\s|$))' -or [string]$row.CommandLine -match '-javaagent:|-agentlib:|-agentpath:'){throw 'TASK069: marked process has foreign executable/control/agent authority.'}
    }
    return Get-LocalPlayRoleState $runtime $Role "$Role.jar" $ports[$Role]
}
function Database-Connections069([ValidateSet('status','quiescent')][string]$Mode){
    $value=@(& $java '-Dfile.encoding=UTF-8' -cp ((Join-Path $runtime 'tools/classes')+';'+(Join-Path $runtime 'libs/*')) Task069DatabaseProbe $Mode $runtime $schema)
    if($LASTEXITCODE -or $value.Count -ne 1 -or [string]$value[0] -cnotmatch '^[0-9]+$'){throw 'TASK069: read-only own database connection check failed.'}
    return [long]$value[0]
}
function Write-Status069{
    [ordered]@{runtime=$runtime;runtimeId=$runtimeId;owner=$binding.owner;database=$schema;source=$build.codeCommit;game=(Role-State069 'GameServer');login=(Role-State069 'LoginServer');ports=$ports;databaseConnections=(Database-Connections069 'status');gameJarSha256=$build.gameJarSha256;sourceArchiveSha256=$build.sourceArchiveSha256;testTreeSha256=$build.testTreeSha256} | ConvertTo-Json -Depth 6
}
if($Action -ceq 'Status'){Write-Status069; return}
foreach($relative in @('local-play','local-play/pids','local-play/control','local-play/logs')){Assert-Plain069 (Join-Path $runtime $relative)}
$lockPath=Join-Path $runtime 'local-play/operator.lock'
Assert-Plain069 $lockPath
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
function Stop-Role069([string]$Role){
    $state=Role-State069 $Role
    if($state.state -cin @('STOPPED','STALE_RECORD')){return}
    if(!$state.recordVerified -or ($state.state -cne 'RUNNING' -and !($state.state -ceq 'INCONSISTENT' -and $state.reason -ceq 'marked JVM does not own all expected ports'))){throw "TASK069: STOP ownership rejected: $Role/$($state.state)"}
    $process=Get-Process -Id $state.pid -ErrorAction Stop
    if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'TASK069: stale PID incarnation.'}
    $timeout=[int](Read-Field069 $settings 'StopTimeoutSeconds')
    if($timeout -lt 10 -or $timeout -gt 600){throw 'TASK069: bounded stop timeout required.'}
    $request=Join-Path $runtime "local-play/control/stop-$Role.properties"
    Assert-Plain069 $request
    if(Test-Path -LiteralPath $request){Move-Item -LiteralPath $request -Destination ($request+'.stale-'+[guid]::NewGuid().ToString('N'))}
    $body="version=1`nowner=$($binding.owner)`nruntime=$runtimeId`nrole=$Role`npid=$($state.pid)`nstartTimeUtcTicks=$($state.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
    Write-Text069 ($request+'.tmp') $body
    Move-Item -LiteralPath ($request+'.tmp') -Destination $request
    $null=$process.WaitForExit($timeout*1000)
    if(!$process.HasExited -or (Get-LocalPlayPortOwners $ports[$Role]).Count){throw "TASK069: STOP_BLOCKED $Role; no force fallback."}
    $recordPath=Join-Path $runtime "local-play/pids/$Role.json"
    $record=Read-Json069 $recordPath
    if([long]$record.pid -ne [long]$state.pid -or [long]$record.startTimeUtcTicks -ne [long]$state.startTimeUtcTicks -or $record.runtime -cne $runtimeId -or $record.role -cne $Role){throw 'TASK069: stopped incarnation receipt changed.'}
    $archive=Join-Path $runtime 'local-play/stopped-pids'
    Assert-Plain069 $archive
    New-Item -ItemType Directory -Path $archive -Force | Out-Null
    $destination=Join-Path $archive "$Role-$($state.pid)-$($state.startTimeUtcTicks).json"
    Assert-Plain069 $destination
    if(Test-Path -LiteralPath $destination){throw 'TASK069: stopped receipt exists; no overwrite.'}
    Move-Item -LiteralPath $recordPath -Destination $destination
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state.pid)"
}
function Start-Role069([string]$Role){
    $kind=if($Role -ceq 'GameServer'){'game'}else{'login'}
    $cfg=Join-Path $runtime "$kind/java.cfg"
    Assert-Plain069 $cfg
    $options=[IO.File]::ReadAllText($cfg).Trim()
    if($options -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'TASK069: foreign startup agent/marker forbidden.'}
    $key=if($Role -ceq 'GameServer'){'GameTimeoutSeconds'}else{'LoginTimeoutSeconds'}
    $timeout=[int](Read-Field069 $settings $key)
    if($timeout -lt 1 -or $timeout -gt 600){throw 'TASK069: bounded startup timeout required.'}
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $arguments="$options -Dphantom.localplay.runtime=$runtimeId -Dphantom.localplay.role=$Role -Dphantom.task069.control=true -jar ..\libs\$Role.jar"
    $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $runtime $kind) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "local-play/logs/$Role-$stamp-stdout.log") -RedirectStandardError (Join-Path $runtime "local-play/logs/$Role-$stamp-stderr.log") -PassThru
    Save-LocalPlayRecord (Join-Path $runtime "local-play/pids/$Role.json") $Role ([pscustomobject]@{pid=$process.Id;startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks}) "$Role.jar" $runtimeId
    $until=[DateTime]::UtcNow.AddSeconds($timeout)
    do{
        if($process.HasExited){throw "TASK069: $Role exited; inspect own logs."}
        $state=Role-State069 $Role
        if($state.state -ceq 'RUNNING' -and $state.recordVerified){Write-Output "STOCK_START_CONFIRMED $Role PID=$($process.Id)"; return}
        if($state.state -ceq 'FOREIGN_PORT_OWNER'){throw 'TASK069: foreign port owner; no process action.'}
        Start-Sleep -Milliseconds 500
    }while([DateTime]::UtcNow -lt $until)
    throw "TASK069: START_BLOCKED $Role; no force fallback."
}
try{
    if($Action -ceq 'Stop'){
        Stop-Role069 'GameServer'; Stop-Role069 'LoginServer'
        $null=Database-Connections069 'quiescent'
    }else{
        foreach($role in @('GameServer','LoginServer')){if((Role-State069 $role).state -cnotin @('STOPPED','STALE_RECORD')){throw 'TASK069: START requires stopped own roles and free ports.'}}
        $null=Database-Connections069 'quiescent'
        Start-Role069 'LoginServer'; Start-Role069 'GameServer'
    }
    Write-Status069
}finally{$lock.Dispose()}
