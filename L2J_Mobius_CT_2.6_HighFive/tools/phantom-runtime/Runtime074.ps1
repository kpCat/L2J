[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Validate','Start','Status','Stop','Snapshot')][string]$Action,
      [Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
. (Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1')
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\'); Assert-Plain073 $runtime
$owner=Join-Path $runtime 'ownership074.properties'
$module=Read-Field073 $owner 'sourceModuleRoot'; $schema=Read-Field073 $owner 'schema'
$test=$schema -ceq 'l2jmobiush5_phantom_test_074'
$expected=if($test){Join-Path (Split-Path $module -Parent) '.phantom-local/task074/runtime-test'}else{Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Living-Systems-074-candidate'}
if($schema -cnotin @('l2jmobiush5_phantom_test_074','l2jmobiush5_phantom_release_074') -or $runtime -ine $expected -or (Read-Field073 $owner 'owner') -cne 'TASK074_LIVING_SYSTEMS' -or [IO.Path]::GetFullPath((Read-Field073 $owner 'runtimeRoot')).TrimEnd('\') -ine $runtime -or (Split-Path $module -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive' -or [IO.Path]::GetFullPath($PSCommandPath) -ine (Join-Path $runtime 'tools/Runtime074.ps1')){throw 'OWN074 exact contained operator/root/schema required.'}
$info=Read-Json073 (Join-Path $runtime 'BUILD074.json')
if($info.owner -cne 'TASK074_LIVING_SYSTEMS' -or $info.sourceState -cne 'COMMITTED' -or $info.sourceCommit -cnotmatch '^[0-9a-f]{40}$'){throw 'OWN074 committed source provenance required.'}
$installation=Read-Field073 $owner 'installationId'
if([guid]::Parse($installation).ToString() -cne $installation){throw 'OWN074 canonical installation UUID required.'}
$ports=if($test){@(2254,7954,9254)}else{@(2106,7777,9154)}
foreach($role in @('game','login')){
    $config=Join-Path $runtime "$role/config/Database.ini"
    if((Read-Field073 $config 'URL') -cnotmatch ('^jdbc:mysql://127\.0\.0\.1:3308/'+$schema+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'OWN074 exact loopback JDBC required.'}
}
if([int](Read-Field073 (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort') -ne $ports[1] -or [int](Read-Field073 (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort') -ne $ports[0] -or [int](Read-Field073 (Join-Path $runtime 'login/config/Server.ini') 'LoginPort') -ne $ports[2] -or [int](Read-Field073 (Join-Path $runtime 'game/config/Server.ini') 'LoginPort') -ne $ports[2]){throw 'OWN074 ports differ.'}
foreach($role in @('GameServer','LoginServer')){
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath (Join-Path $runtime "libs/$role.jar") -Algorithm SHA256).Hash -ine $info.$key -or (Read-Field073 $owner $key) -ine $info.$key){throw 'OWN074 whole JAR differs.'}
}
$tools074=Join-Path $runtime 'TOOLS074.tsv'; $geo074=Join-Path $runtime 'GEODATA074.tsv'; $source074=Join-Path $runtime 'SOURCE_BUILD074.json'
if((Get-FileHash -LiteralPath $tools074 -Algorithm SHA256).Hash -ine $info.toolManifestSha256 -or (Get-FileHash -LiteralPath $geo074 -Algorithm SHA256).Hash -ine $info.geoManifestSha256 -or (Get-FileHash -LiteralPath $source074 -Algorithm SHA256).Hash -ine $info.sourceReceiptSha256){throw 'OWN074 artifact/source manifest differs.'}
$toolRows074=@(Import-Csv -LiteralPath $tools074 -Delimiter "`t")
if($toolRows074.Count -ne 5 -or @($toolRows074.path | Select-Object -Unique).Count -ne 5){throw 'OWN074 exact five operator artifacts required.'}
foreach($row in $toolRows074){
    if($row.path -cnotmatch '^tools/(Runtime074\.ps1|Runtime074Probe\.java|Task073Common\.ps1|LocalPlay-Ownership\.ps1|classes/Runtime074Probe\.class)$'){throw 'OWN074 unknown operator artifact.'}
    $path=Join-Path $runtime $row.path; Assert-Plain073 $path
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $row.sha256){throw 'OWN074 operator artifact changed.'}
}
if($Action -in @('Validate','Start')){
    $regions=@(Import-Csv -LiteralPath (Join-Path $runtime 'GEODATA074.tsv') -Delimiter "`t")
    if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203){throw 'OWN074 physical Geo203 required.'}
    foreach($region in $regions){$path=Join-Path $runtime $region.relativePath; Assert-Plain073 $path; if($region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $region.sha256){throw 'OWN074 Geo region differs.'}}
    $price=Join-Path $runtime 'game/data/economy070/pricing070.tsv'
    if((Get-FileHash -LiteralPath $price -Algorithm SHA256).Hash -ine ([IO.File]::ReadAllText((Join-Path $runtime 'game/data/economy070/pricing070.sha256')).Trim())){throw 'OWN074 pinned price payload differs.'}
}
$javaHome=Read-Field073 (Join-Path $runtime 'runtime.local.ini') 'JavaHome'; Assert-Java073 $javaHome; $java=Join-Path $javaHome 'bin/java.exe'
if($Action -eq 'Validate'){Write-Output 'OWN074_BINDING_PASS; DB_WRITES=0; PROCESS_ACTIONS=0';return}
$runtimeId=Get-LocalPlayRuntimeId $runtime
$rolePorts=@{GameServer=@($ports[1]);LoginServer=@($ports[0],$ports[2])}
function State074([string]$Role){return Get-LocalPlayRoleState $runtime $Role "$Role.jar" $rolePorts[$Role]}
function Probe074([string]$Mode){& $java -cp ((Join-Path $runtime 'tools/classes')+';'+(Join-Path $runtime 'libs/*')) Runtime074Probe $Mode $runtime; if($LASTEXITCODE){throw 'OWN074 database probe failed.'}}
function Status074{[ordered]@{owner='TASK074_LIVING_SYSTEMS';root=$runtime;source=$info.sourceCommit;game=(State074 'GameServer');login=(State074 'LoginServer');databaseConnections=(Probe074 'status');gameJarSha256=$info.gameJarSha256} | ConvertTo-Json -Depth 5}
if($Action -eq 'Status'){Status074;return}
if($Action -eq 'Snapshot'){Probe074 'snapshot';return}
$lock=[IO.File]::Open((Join-Path $runtime 'local-play/operator.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    if($Action -eq 'Stop'){
        foreach($role in @('GameServer','LoginServer')){
            $state=State074 $role
            if($state.state -in @('STOPPED','STALE_RECORD')){continue}
            if(!$state.recordVerified -or ($state.state -ne 'RUNNING' -and !($state.state -eq 'INCONSISTENT' -and $state.reason -eq 'marked JVM does not own all expected ports'))){throw 'OWN074 STOP incarnation/port ownership rejected.'}
            $process=Get-Process -Id $state.pid
            if($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'OWN074 STOP stale PID.'}
            $row=Get-CimInstance Win32_Process -Filter ('ProcessId='+$state.pid)
            if($row.ExecutablePath -ine $java -or $row.CommandLine -notmatch '-Dphantom\.task074\.control=true' -or $row.CommandLine -match '-javaagent:|-agentlib:|-agentpath:'){throw 'OWN074 STOP foreign executable/control.'}
            $request=Join-Path $runtime "local-play/control/stop-$role.properties"
            if(Test-Path -LiteralPath $request){throw 'OWN074 unresolved stop request; preserve it.'}
            Write-Text073 ($request+'.tmp') "version=1`nowner=TASK074_LIVING_SYSTEMS`nruntime=$runtimeId`nrole=$role`npid=$($state.pid)`nstartTimeUtcTicks=$($state.startTimeUtcTicks)`ndeadlineUtcMillis=$([DateTimeOffset]::UtcNow.AddSeconds(110).ToUnixTimeMilliseconds())`n"
            Move-Item -LiteralPath ($request+'.tmp') -Destination $request
            $null=$process.WaitForExit(240000)
            if(!$process.HasExited -or (Get-LocalPlayPortOwners $rolePorts[$role]).Count){throw 'OWN074 stock STOP blocked; no force fallback.'}
            $record=Join-Path $runtime "local-play/pids/$role.json"
            Move-Item -LiteralPath $record -Destination ($record+'.stopped-'+$state.startTimeUtcTicks)
            Write-Output "OWN074_STOCK_STOP $role PID=$($state.pid)"
        }
        $null=Probe074 'quiescent'
    }else{
        foreach($role in @('GameServer','LoginServer')){if((State074 $role).state -notin @('STOPPED','STALE_RECORD')){throw 'OWN074 START requires free ports/stopped roles.'}}
        $null=Probe074 'quiescent'
        foreach($role in @('LoginServer','GameServer')){
            $kind=if($role -ceq 'GameServer'){'game'}else{'login'}; $options=[IO.File]::ReadAllText((Join-Path $runtime "$kind/java.cfg")).Trim()
            if($options -match '-javaagent:|-agentlib:|-agentpath:|phantom\.|[\r\n]'){throw 'OWN074 foreign startup option.'}
            $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
            $arguments="$options -Dphantom.localplay.runtime=$runtimeId -Dphantom.localplay.role=$role -Dphantom.task074.control=true -Dphantom.economy.schema=$schema -Dphantom.economy.installation=$installation -jar ..\libs\$role.jar"
            $process=Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $runtime $kind) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime "local-play/logs/$role-$stamp-stdout.log") -RedirectStandardError (Join-Path $runtime "local-play/logs/$role-$stamp-stderr.log") -PassThru
            Save-LocalPlayRecord (Join-Path $runtime "local-play/pids/$role.json") $role ([pscustomobject]@{pid=$process.Id;startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks}) "$role.jar" $runtimeId
            $until=[DateTime]::UtcNow.AddSeconds(240)
            do{if($process.HasExited){throw 'OWN074 native Main exited; inspect own logs.'};$state=State074 $role;if($state.state -eq 'RUNNING' -and $state.recordVerified){break};Start-Sleep -Milliseconds 500}while([DateTime]::UtcNow -lt $until)
            if($state.state -ne 'RUNNING'){throw 'OWN074 START bounded timeout; no force fallback.'}
            Write-Output "OWN074_STOCK_START $role PID=$($process.Id)"
        }
    }
    Status074
}finally{$lock.Dispose()}
