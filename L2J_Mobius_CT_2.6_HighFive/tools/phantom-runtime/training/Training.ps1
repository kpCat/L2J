[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Start','Run','Status','Pause','Resume','Stop','Report','Export','Preflight')][string]$Action,
    [string]$Root,
    [ValidateSet('V1','V3','EVAL005')][string]$ConfigProfile='V1',
    [switch]$EnableEvaluation005,
    [ValidateRange(1,4)][int]$Evaluation005PairLimit=4,
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')][string]$Evaluation005Campaign='learning005',
    [ValidatePattern('^[0-9a-f]{32}$')][string]$Nonce
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
$layout=Get-TrainLayout $Root
$script:State=$null
$script:DirectorNonce=$Nonce
$script:SqlRecord=$null
$script:NativeRecord=$null
$script:WorkerRecord=$null
$script:Config=$null
function Read-TrainSourceConfig {
    $profile=$ConfigProfile
    $current=$null
    $saved=Join-Path $layout.Root 'TRAIN_CONFIG.ini'
    if(Test-Path -LiteralPath $saved){$current=Read-TrainConfig $saved;if($current.ContainsKey('configProfile')){$profile=$current.configProfile}}
    $name=if($profile -ceq 'EVAL005'){'Evaluation005.ini'}elseif($profile -ceq 'V3'){'TrainingV3.ini'}else{'TRAIN_CONFIG.ini'}
    $values=Read-TrainConfig (Join-Path $PSScriptRoot $name)
    if($profile -ceq 'EVAL005'){
        if($EnableEvaluation005){$values.evaluation005Enabled='true';$values.evaluation005MaxPairsPerScope=[string]$Evaluation005PairLimit;$values.evaluation005Campaign=$Evaluation005Campaign}
        elseif($current -and $current.ContainsKey('evaluation005Enabled')){foreach($key in @('evaluation005Enabled','evaluation005MaxPairsPerScope','evaluation005Campaign')){$values[$key]=$current[$key]}}
        if($Action -in @('Start','Resume','Run') -and $values.evaluation005Enabled -cne 'true'){throw 'EVAL005_EXPLICIT_OPT_IN_REQUIRED'}
    }
    return $values
}
function Publish-TrainState([string]$Phase,[string]$Reason='') {
    if(!$script:State){return}
    $script:State.phase=$Phase;$script:State.reason=$Reason;$script:State.heartbeatUtc=[DateTime]::UtcNow.ToString('o')
    $script:State.cpuSeconds=[Math]::Round([Diagnostics.Process]::GetCurrentProcess().TotalProcessorTime.TotalSeconds,3)
    $script:State.workingSetBytes=[Diagnostics.Process]::GetCurrentProcess().WorkingSet64
    Write-TrainJson (Join-Path $layout.Root 'director.json') $script:State
}
function Request-TrainBoundary([string]$Kind,[string]$Token) {
    $request=@{kind=$Kind;nonce=$Token;id=[guid]::NewGuid().ToString('N');issuedUtc=[DateTime]::UtcNow.ToString('o')}
    Write-TrainJson (Join-Path $layout.Root ('commands\'+$request.id+'.json')) $request
    Write-TrainJson (Join-Path $layout.Root 'command.json') $request
    if(Test-Path -LiteralPath (Join-Path $layout.Root 'native-process.json')){
        $record=Read-TrainJson (Join-Path $layout.Root 'native-process.json')
        if(!(Test-TrainProcessAbsent $record)){
            Assert-TrainProcess $record -Kind Native
            if($Kind -in @('stop','pause')){Write-TrainText (Join-Path $layout.Root ('attempts\'+$record.nonce+'\'+$Kind+'.request')) $Token}
        }
    }
}
function Test-TrainRequest([string]$Kind) {
    $path=Join-Path $layout.Root 'command.json'
    if(!(Test-Path -LiteralPath $path)){return $false}
    Assert-G0PhysicalFile $path
    $request=Read-TrainJson $path
    return $request.nonce -ceq $script:DirectorNonce -and $request.kind -ceq $Kind
}
function Get-TrainLiveDirector {
    $path=Join-Path $layout.Root 'owner-process.json'
    if(!(Test-Path -LiteralPath $path)){return $null}
    $record=Read-TrainJson $path
    if(Test-TrainProcessAbsent $record){return $null}
    Assert-TrainProcess $record -Kind Director
    return $record
}
function Get-TrainDependencies {
    Initialize-TrainWin32
    foreach($file in @($layout.Daemon,$layout.Install,$layout.Admin,$layout.PowerShell)){Assert-G0PhysicalFile $file}
    if((Get-Item -LiteralPath $layout.Daemon).VersionInfo.ProductVersion -cne '11.4.13.0'){throw 'TRAIN002_MARIADB_VERSION'}
    $java=(Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
    Assert-G0PhysicalFile $java
    $release=Join-Path (Split-Path (Split-Path $java -Parent) -Parent) 'release';Assert-G0PhysicalFile $release
    if([IO.File]::ReadAllText($release) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\+|\")'){throw 'TRAIN002_JDK25_REQUIRED'}
    $ant=(Get-Command ant.bat -CommandType Application | Select-Object -First 1).Source
    Assert-G0PhysicalFile $ant
    return @{Java=$java;Ant=$ant}
}
function Get-TrainAssets {
    if((Test-Path -LiteralPath (Join-Path $layout.Root 'geo203.json')) -and (Test-Path -LiteralPath (Join-Path $layout.Root 'protected207.json')) -and (Test-Path -LiteralPath (Join-Path $layout.Game 'geodata'))){
        return @{Geo=(Join-Path $layout.Game 'geodata');GeoManifest=(Join-Path $layout.Root 'geo203.json');ProtectedManifest=(Join-Path $layout.Root 'protected207.json')}
    }
    # Bounded discovery: only the documented sibling clone and its attested cycle19.
    $previous=Join-Path (Split-Path $layout.Clone -Parent) 'phantom-learning-001-source\.phantom-local\learning001\cycle19'
    if($script:Config -and $script:Config.ContainsKey('assetSourceRoot') -and $script:Config.assetSourceRoot){
        if(![IO.Path]::IsPathRooted($script:Config.assetSourceRoot)){throw 'TRAIN003_ABSOLUTE_READONLY_ASSET_ROOT_REQUIRED'}
        $previous=[IO.Path]::GetFullPath($script:Config.assetSourceRoot);Assert-TrainDirectory $previous
    }
    elseif(!(Test-Path -LiteralPath (Join-Path $previous 'geo203.json'))){
        # The independent clone may be in repos while the documented001 source is in sibling worktrees.
        # This is read-only bounded discovery; Assert-TrainAssets still verifies every pinned hash.
        $documentedWorktrees=Join-Path (Split-Path (Split-Path $layout.Clone -Parent) -Parent) 'worktrees'
        $previous=Join-Path $documentedWorktrees 'phantom-learning-001-source\.phantom-local\learning001\cycle19'
        Assert-TrainDirectory $previous
    }
    $protectedManifest=Join-Path $previous 'protected207.json'
    if($script:Config -and $script:Config.ContainsKey('configProfile') -and $script:Config.configProfile -ceq 'EVAL005'){
        $protectedManifest=Join-Path $layout.Root 'protected207.005.json'
        Assert-TrainHash $protectedManifest (Get-TrainProtectedPin $script:Config)
    }
    return @{Geo=(Join-Path $previous 'game\geodata');GeoManifest=(Join-Path $previous 'geo203.json');ProtectedManifest=$protectedManifest}
}
function Set-TrainV3CampaignRound($Config) {
    if(!$Config.ContainsKey('v3Core004') -or $Config.v3Core004 -cne 'true'){return}
    $path=Join-Path $layout.Root 'v3-campaign.json'
    if(Test-Path -LiteralPath $path){
        $campaign=Read-TrainJson $path
        if($campaign.schemaVersion -ne 4 -or $campaign.installationOwner -cne [IO.File]::ReadAllText((Join-Path $layout.Root 'owner.txt')) -or [int]$campaign.nextCollectionRound -lt 4 -or [int]$campaign.nextCollectionRound -gt 99){throw 'TRAIN004_CAMPAIGN_IDENTITY_OR_ROUND'}
        $Config.v3CollectionRound=[string][Math]::Max([int]$Config.v3CollectionRound,[int]$campaign.nextCollectionRound)
    }
}
function Save-TrainV3SegmentDispatch($Config,[string]$Phase) {
    if(!$Config.ContainsKey('v3Core004') -or $Config.v3Core004 -cne 'true'){return}
    # Reserve the next fresh physical cohort before dispatch. A killed native worker never resets old CUT actors.
    Write-TrainJson (Join-Path $layout.Root 'v3-campaign.json') @{schemaVersion=4;installationOwner=[IO.File]::ReadAllText((Join-Path $layout.Root 'owner.txt'));runId=$script:State.runId;generation=$script:State.generation;dispatchedPhase=$Phase;nextCollectionRound=([int]$Config.v3CollectionRound+1);dispatchedUtc=[DateTime]::UtcNow.ToString('o')}
}
function Get-TrainSourceFingerprint {
    $files=@(Get-ChildItem -LiteralPath (Join-Path $layout.Module 'java'),(Join-Path $layout.Module 'test\java') -File -Recurse -Filter '*.java')
    $files+=@(Get-ChildItem -LiteralPath (Join-Path $layout.Module 'dist\db_installer\sql'),(Join-Path $layout.Module 'test\resources\phantoms\db\migrations') -File -Recurse -Filter '*.sql')
    $files+=Get-Item -LiteralPath (Join-Path $layout.Module 'build.xml')
    $lines=$files|Sort-Object FullName|ForEach-Object{Assert-G0PhysicalFile $_.FullName;$_.FullName.Substring($layout.Module.Length).Replace('\','/')+' '+(Get-FileHash -LiteralPath $_.FullName).Hash}
    $hash=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))))).Replace('-','').ToLowerInvariant()}finally{$hash.Dispose()}
}
function Ensure-TrainBuild($Dependencies) {
    Publish-TrainState 'BUILD_CHECK'
    $fingerprint=Get-TrainSourceFingerprint
    $stamp=Join-Path $layout.Root 'build.json';$jar=Join-Path $layout.Module 'dist\libs\GameServer.jar'
    $fresh=$false
    if(Test-Path -LiteralPath $stamp){$prior=Read-TrainJson $stamp;$fresh=$prior.sourceFingerprint -ceq $fingerprint -and (Test-Path -LiteralPath $jar) -and (Get-FileHash -LiteralPath $jar).Hash -ceq $prior.jarSha}
    foreach($class in @('org\l2jmobius\tests\phantoms\PhantomTrainingNative.class','org\l2jmobius\tests\phantoms\PhantomTrainingSql.class')){
        if(!(Test-Path -LiteralPath (Join-Path (Join-Path $layout.Clone 'build\phantom-test\bin') $class))){$fresh=$false}
    }
    if(!(Test-Path -LiteralPath (Join-Path $layout.Clone 'build\bin\org\l2jmobius\gameserver\phantoms\learning\TrainingEvaluator.class'))){$fresh=$false}
    if(!$fresh){
        Publish-TrainState 'BUILDING'
        foreach($target in @('compile-tests','jar')){
            $log=Join-Path $layout.Root ('logs\build-'+$target+'-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')+'.log')
            & $Dependencies.Ant -f (Join-Path $layout.Module 'build.xml') $target *> $log
            if($LASTEXITCODE -ne 0){throw ('TRAIN002_BUILD_FAILED:'+ $target)}
            Publish-TrainState 'BUILDING' $target
        }
        if((Get-TrainSourceFingerprint) -cne $fingerprint){throw 'TRAIN002_SOURCE_CHANGED_DURING_BUILD'}
        Write-TrainJson $stamp @{sourceFingerprint=$fingerprint;jarSha=(Get-FileHash -LiteralPath $jar).Hash;builtUtc=[DateTime]::UtcNow.ToString('o')}
    }
    return (Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant()
}
function Set-TrainIni([string]$Game,[string]$Relative,$Overrides) {
    $file=Join-Path $Game ('config\'+$Relative);Assert-G0PhysicalFile $file;$body=[IO.File]::ReadAllText($file)
    foreach($key in $Overrides.Keys){$pattern='(?m)^'+[regex]::Escape($key)+'[ \t]*=[^\r\n]*';if([regex]::Matches($body,$pattern).Count -ne 1){throw ('TRAIN002_INI_KEY:'+ $key)};$body=[regex]::Replace($body,$pattern,($key+' = '+$Overrides[$key]))}
    [IO.File]::WriteAllText($file,$body,$script:TrainUtf8)
}
function Ensure-TrainStage([string]$JarSha,$Assets) {
    Publish-TrainState 'STAGING'
    Assert-TrainAssets $Assets.Geo $Assets.GeoManifest $Assets.ProtectedManifest $script:Config
    if(!(Test-Path -LiteralPath $layout.Game)){
        $pending=Join-Path $layout.Root ('game.pending.'+[guid]::NewGuid().ToString('N'))
        $null=[IO.Directory]::CreateDirectory($pending)
        foreach($name in @('data','log.cfg')){Copy-Item -LiteralPath (Join-Path $layout.Module ('dist\game\'+$name)) -Destination $pending -Recurse}
        foreach($name in @('config','libs','geodata','log')){$null=[IO.Directory]::CreateDirectory((Join-Path $pending $name))}
        Get-ChildItem -LiteralPath (Join-Path $layout.Module 'dist\game\config') -Force|Where-Object Name -ne 'Database.ini'|Copy-Item -Destination (Join-Path $pending 'config') -Recurse
        Get-ChildItem -LiteralPath (Join-Path $layout.Module 'dist\libs') -Filter '*.jar' -File|Where-Object Name -NotMatch '-(sources|javadoc)\.jar$'|Copy-Item -Destination (Join-Path $pending 'libs')
        Get-ChildItem -LiteralPath $Assets.Geo -File|Copy-Item -Destination (Join-Path $pending 'geodata')
        Copy-Item -LiteralPath $Assets.GeoManifest -Destination (Join-Path $layout.Root 'geo203.json')
        Copy-Item -LiteralPath $Assets.ProtectedManifest -Destination (Join-Path $layout.Root 'protected207.json')
        Set-TrainIni $pending 'Interface.ini' @{EnableGUI='False'}
        Set-TrainIni $pending 'GeoEngine.ini' @{GeoDataPath='./geodata/'}
        Set-TrainIni $pending 'Custom\PhantomPlayers.ini' @{EnablePhantomSystem='False';LearningMode='OFF';LearningModelVersion='0';LearningEvidenceEnabled='False';LearningDeterministicSeed='0'}
        Set-TrainIni $pending 'Custom\PhantomMerchants.ini' @{EnableOfflinePhantomMerchants='False'}
        Set-TrainIni $pending 'Custom\OfflineTrade.ini' @{RestoreOffliners='False'}
        Assert-TrainAssets (Join-Path $pending 'geodata') $Assets.GeoManifest $Assets.ProtectedManifest $script:Config
        [IO.Directory]::Move($pending,$layout.Game)
    }
    Assert-TrainDirectory $layout.Game
    $current=Join-Path $layout.Game 'libs\GameServer.jar';Assert-G0PhysicalFile $current
    if((Get-FileHash -LiteralPath $current).Hash -ine $JarSha){
        $archive=Join-Path $layout.Root ('archives\GameServer.'+(Get-FileHash -LiteralPath $current).Hash.ToLowerInvariant()+'.jar')
        if(!(Test-Path -LiteralPath $archive)){Copy-Item -LiteralPath $current -Destination $archive}
        Copy-Item -LiteralPath (Join-Path $layout.Module 'dist\libs\GameServer.jar') -Destination ($current+'.pending')
        Assert-TrainHash ($current+'.pending') $JarSha
        [IO.File]::Replace($current+'.pending',$current,[NullString]::Value)
    }
    Assert-TrainHash $current $JarSha
    Set-TrainIni $layout.Game 'Server.ini' @{LoginHost='127.0.0.1';LoginPort=$script:Config.loginPort;GameserverHostname='127.0.0.1';GameserverPort=$script:Config.gamePort;DatapackRoot='.'}
    Assert-TrainAssets (Join-Path $layout.Game 'geodata') (Join-Path $layout.Root 'geo203.json') (Join-Path $layout.Root 'protected207.json') $script:Config
}
function Invoke-TrainJava($Dependencies,[string]$Class,[string[]]$Arguments,[int]$TimeoutSeconds=600) {
    $workerId=[guid]::NewGuid().ToString('N');$directory=Join-Path $layout.Root ('workers\'+$workerId)
    $null=[IO.Directory]::CreateDirectory($directory)
    $javaArguments=@(Get-TrainJavaArguments $Class $Arguments $layout.ClassPath $workerId ([int]$script:Config.cpuCount) ([int]$script:Config.maxHeapMb))
    $process=Start-Process -FilePath $Dependencies.Java -ArgumentList $javaArguments -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'worker.out') -RedirectStandardError (Join-Path $directory 'worker.err')
    $handle=Hold-ProcessHandle $process
    try{
    $record=New-TrainProcessRecord $process $Dependencies.Java $workerId Worker (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
    $script:WorkerRecord=$record
    Write-TrainJson (Join-Path $directory 'process.json') $record
    Write-TrainJson (Join-Path $layout.Root 'worker-process.json') $record
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while(!$process.WaitForExit(1000)){
        if($process.HasExited){Save-TrainProcessObservation $record $null 'OWN_WORKER_EXIT_AFTER_WAIT' (Get-TrackedProcessExitCode $process $handle);break}
        try{Assert-TrainProcess $record -Kind Worker}catch{
            if(Test-TrainTerminatedCimRace $_ $record $process $handle){
                Save-TrainProcessObservation $record $null 'OWN_WORKER_EXIT_AFTER_CIM_NOT_FOUND' (Get-TrackedProcessExitCode $process $handle) $_.FullyQualifiedErrorId
                break
            }
            if($process.HasExited -and $_.Exception.Message -ceq 'TRAIN002_PROCESS_UNAVAILABLE'){
                $nativeError=$null;if($_.Exception.Data.Contains('nativeError')){$nativeError=[int]$_.Exception.Data['nativeError']}
                Save-TrainProcessObservation $record $nativeError 'OWN_WORKER_EXIT_AFTER_QUERY' (Get-TrackedProcessExitCode $process $handle)
                break
            }
            throw
        }
        if($script:State){Publish-TrainState $script:State.phase}
        if([DateTime]::UtcNow -gt $deadline){throw 'TRAIN002_WORKER_TIMEOUT_NO_FORCE'}
    }
    $exit=Get-TrackedProcessExitCode $process $handle
    Assert-TrainHash $record.image $record.imageHash
    Assert-TrainHash $record.configPath $record.configHash
    Write-TrainExitReceipt $layout $record $process $handle $Class
    if($exit -ne 0){throw ('TRAIN002_WORKER_FAILED:'+ $Class)}
    $script:WorkerRecord=$null
    }catch{
        if(!$_.Exception.Data.Contains('pid')){$_.Exception.Data['pid']=$process.Id}
        $null=Save-TrainFailure $layout $script:State $_ 'WORKER'
        throw
    }finally{$process.Dispose()}
}
function Wait-TrainWorker([int]$Seconds) {
    $path=Join-Path $layout.Root 'worker-process.json'
    if(!(Test-Path -LiteralPath $path)){return}
    $record=Read-TrainJson $path
    $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
    while(!(Test-TrainProcessAbsent $record)){
        Assert-TrainProcess $record -Kind Worker
        if([DateTime]::UtcNow -gt $deadline){throw 'TRAIN002_WORKER_STILL_ACTIVE_SQL_RETAINED'}
        Publish-TrainState 'WAIT_OWN_WORKER'
        Start-Sleep -Milliseconds 500
    }
}
function Start-TrainSql($Dependencies) {
    $initialized=Join-Path $layout.Sql 'initialized.json'
    $receipt=Join-Path $layout.Sql 'owned-process.json'
    if(Test-Path -LiteralPath $receipt){
        $existing=Read-TrainJson $receipt
        if(!(Test-TrainProcessAbsent $existing)){Assert-TrainSqlOptions $layout $script:Config $existing;Assert-TrainProcess $existing -Kind Sql -Port ([int]$script:Config.sqlPort);return $existing}
    }
    Assert-TrainPortsFree @([int]$script:Config.sqlPort)
    if(!(Test-Path -LiteralPath $initialized)){
        if(Test-Path -LiteralPath $layout.Sql){
            if(Test-Path -LiteralPath (Join-Path $layout.Game 'config\Database.ini')){throw 'TRAIN002_PARTIAL_SQL_WITH_GAME_CONFIG'}
            Assert-TrainDirectory $layout.Sql
            $archive=Join-Path $layout.Root ('sql.incomplete.'+[guid]::NewGuid().ToString('N'))
            [IO.Directory]::Move($layout.Sql,$archive)
        }
        $null=[IO.Directory]::CreateDirectory($layout.Sql)
        $password=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
        Write-TrainText (Join-Path $layout.Sql 'admin.client.ini') (('[client]','host=127.0.0.1',('port='+$script:Config.sqlPort),'user=root',('password='+$password)) -join "`n")
        Write-TrainText (Join-Path $layout.Sql 'admin.jdbc.ini') ((('URL=jdbc:mysql://127.0.0.1:'+$script:Config.sqlPort+'/mysql'),'Login=root',('Password='+$password)) -join "`n")
        $install=Start-Process -FilePath $layout.Install -ArgumentList @(('--datadir="'+(Join-Path $layout.Sql 'data')+'"'),('--port='+$script:Config.sqlPort),('--password='+$password),'--silent') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $layout.Sql 'install.out') -RedirectStandardError (Join-Path $layout.Sql 'install.err')
        if($null -eq $install.ExitCode -or $install.ExitCode -ne 0 -or !(Test-Path -LiteralPath (Join-Path $layout.Sql 'data\mysql'))){throw 'TRAIN002_SQL_INSTALL_FAILED'}
        $token=[guid]::NewGuid().ToString('N');$config=Join-Path $layout.Sql ('owned-server.'+$token+'.ini')
        Write-TrainText $config ((@('[mysqld]','bind-address=127.0.0.1',('port='+$script:Config.sqlPort),('datadir="'+(Join-Path $layout.Sql 'data').Replace('\','/')+'"'),'default-time-zone=+00:00')) -join "`n")
        Write-TrainJson $initialized @{nonce=$token;configPath=$config;configHash=(Get-FileHash -LiteralPath $config).Hash;imageHash=(Get-FileHash -LiteralPath $layout.Daemon).Hash}
        $password=$null
    }
    $settings=Read-TrainJson $initialized
    Assert-TrainHash $settings.configPath $settings.configHash;Assert-TrainHash $layout.Daemon $settings.imageHash
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
    $process=Start-Process -FilePath $layout.Daemon -ArgumentList @(('"--defaults-file='+$settings.configPath+'"'),'--console') -WorkingDirectory $layout.Sql -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $layout.Sql ($stamp+'.out')) -RedirectStandardError (Join-Path $layout.Sql ($stamp+'.err'))
    $null=$process.Handle
    $record=New-TrainProcessRecord $process $layout.Daemon $settings.nonce Sql $settings.configPath
    Write-TrainJson $receipt $record
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while(!@([Win32SqlIdentity]::Listeners([int]$script:Config.sqlPort)).Count -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 200}
    Assert-TrainSqlOptions $layout $script:Config $record;Assert-TrainProcess $record -Kind Sql -Port ([int]$script:Config.sqlPort)
    return $record
}
function Stop-TrainNative([int]$Seconds) {
    $path=Join-Path $layout.Root 'native-process.json'
    if(!(Test-Path -LiteralPath $path)){return}
    $record=Read-TrainJson $path
    if(Test-TrainProcessAbsent $record){return}
    Assert-TrainProcess $record -Kind Native
    $process=Get-Process -Id $record.pid -ErrorAction Stop;$handle=Hold-ProcessHandle $process
    try{
        if($process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$record.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
        Write-TrainText (Join-Path $layout.Root ('attempts\'+$record.nonce+'\stop.request')) $script:DirectorNonce
        $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
        do{if(Test-TrainProcessAbsent $record $process $handle){return};Publish-TrainState 'DRAINING';Start-Sleep -Milliseconds 500}while([DateTime]::UtcNow -lt $deadline)
        throw 'TRAIN002_NATIVE_STOP_UNCONFIRMED_SQL_RETAINED'
    }finally{$process.Dispose()}
}
function Reset-TrainColdBaseline {
    Assert-TrainInstallation $layout
    Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
    Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    $path=Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini';Assert-G0PhysicalFile $path
    $before=[IO.File]::ReadAllText($path);$after=Get-TrainColdBaseline $before
    if($before -ceq $after){return}
    $beforeSha=(Get-FileHash -LiteralPath $path).Hash
    $archive=Join-Path $layout.Root ('archives\PhantomPlayers.pre-cold.'+$beforeSha.ToLowerInvariant()+'.ini')
    if(Test-Path -LiteralPath $archive){Assert-TrainHash $archive $beforeSha}else{[IO.File]::Copy($path,$archive,$false);Assert-TrainHash $archive $beforeSha}
    Write-TrainText $path $after
    Write-TrainJson (Join-Path $layout.Root ('archives\cold-baseline.'+[guid]::NewGuid().ToString('N')+'.json')) @{beforeSha=$beforeSha;afterSha=(Get-FileHash -LiteralPath $path).Hash;reason='OWN_NATIVE_STOPPED_PORTS_FREE';gamePort=[int]$script:Config.gamePort;loginPort=[int]$script:Config.loginPort;restoredUtc=[DateTime]::UtcNow.ToString('o')}
}
function Submit-TrainExportRequest {
    Assert-TrainInstallation $layout
    $path=Join-Path $layout.Root 'export-request.json';$resultPath=Join-Path $layout.Root 'export-result.json'
    if(Test-Path -LiteralPath $path){
        $prior=Read-TrainJson $path
        if(!(Test-Path -LiteralPath $resultPath) -or (Read-TrainJson $resultPath).id -cne $prior.id){return $prior.id}
    }
    $directory=Join-Path $layout.Root 'export-requests';$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
    $request=@{id=[guid]::NewGuid().ToString('N');owner=[IO.File]::ReadAllText((Join-Path $layout.Root 'owner.txt'));sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;requestedUtc=[DateTime]::UtcNow.ToString('o')}
    Write-TrainJson (Join-Path $directory ($request.id+'.request.json')) $request
    Write-TrainJson $path $request
    return $request.id
}
function Invoke-TrainQueuedExport($Dependencies) {
    $path=Join-Path $layout.Root 'export-request.json';$resultPath=Join-Path $layout.Root 'export-result.json'
    if(!(Test-Path -LiteralPath $path) -or (Test-TrainRequest 'stop')){return}
    $request=Read-TrainJson $path
    if($request.id -cnotmatch '^[0-9a-f]{32}$' -or $request.owner -cne [IO.File]::ReadAllText((Join-Path $layout.Root 'owner.txt')) -or $request.sid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'TRAIN002_EXPORT_REQUEST_OWNER'}
    if((Test-Path -LiteralPath $resultPath) -and (Read-TrainJson $resultPath).id -ceq $request.id){return}
    Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
    Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    Assert-TrainProcess $script:SqlRecord -Kind Sql -Port ([int]$script:Config.sqlPort)
    Publish-TrainState 'EXPORTING' $request.id
    $result=@{id=$request.id;state='COMPLETED';reason='Проверенная TEST-модель экспортирована.';finishedUtc=''}
    try{
        if($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true'){Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('export',$layout.Root)}
        else{Invoke-TrainJava $Dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('export',$layout.Root)}
    }
    catch{
        # A missing accepted model rejects this request, not the training campaign.
        # An unconfirmed live worker still prevents further work and SQL shutdown.
        Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
        $result.state='FAILED';$result.reason='Экспорт не выполнен: проверенная модель пока недоступна либо проверка экспорта вернула отказ. История обучения сохранена.'
    }
    $result.finishedUtc=[DateTime]::UtcNow.ToString('o')
    Write-TrainJson (Join-Path $layout.Root ('export-requests\'+$request.id+'.result.json')) $result
    Write-TrainJson $resultPath $result
}
function Invoke-TrainNative($Dependencies,[string]$JarSha) {
    Reset-TrainColdBaseline
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    Assert-TrainProcess $script:SqlRecord -Kind Sql -Port ([int]$script:Config.sqlPort)
    $attempt=[guid]::NewGuid().ToString('N');$directory=Join-Path $layout.Root ('attempts\'+$attempt)
    $null=[IO.Directory]::CreateDirectory($directory)
    $script:State.attemptId=$attempt;Publish-TrainState 'NATIVE_BOOT'
    $config=Join-Path $layout.Root 'TRAIN_CONFIG.ini'
    $arguments=@(('-XX:ActiveProcessorCount='+$script:Config.cpuCount),'-Xms512m',('-Xmx'+$script:Config.maxHeapMb+'m'),'-Dphantom.learning.train=true','-Dphantom.learning.witness=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',('"'+$layout.ClassPath+'"'),'org.l2jmobius.tests.phantoms.PhantomTrainingNative',('"'+$layout.Root+'"'),$attempt,$JarSha,('"'+$config+'"'))
    $process=Start-Process -FilePath $Dependencies.Java -ArgumentList $arguments -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'native.out') -RedirectStandardError (Join-Path $directory 'native.err')
    $handle=Hold-ProcessHandle $process
    $record=New-TrainProcessRecord $process $Dependencies.Java $attempt Native $config
    $script:NativeRecord=$record;Write-TrainJson (Join-Path $layout.Root 'native-process.json') $record
    $resources=Get-TrainProcessResources $process
    try{
    $ready=Join-Path $directory 'game.ready';$deadline=[DateTime]::UtcNow.AddSeconds([int]$script:Config.startupTimeoutSeconds)
    while(!$process.HasExited -and !(Test-Path -LiteralPath $ready)){
        $resources=Get-TrainProcessResources $process $resources
        Publish-TrainState 'NATIVE_BOOT'
        if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce}
        if([DateTime]::UtcNow -gt $deadline){throw 'TRAIN002_NATIVE_BOOT_TIMEOUT'}
        Start-Sleep -Milliseconds 500
    }
    if($process.HasExited){throw 'TRAIN002_NATIVE_BOOT_EXIT'}
    Assert-G0PhysicalFile $ready
    if([IO.File]::ReadAllText($ready) -cne [string]$process.Id){throw 'TRAIN002_READY_PID'}
    Assert-TrainProcess $record -Kind Native -Port ([int]$script:Config.gamePort)
    Assert-TrainPortsFree @([int]$script:Config.loginPort)
    Write-TrainText (Join-Path $directory 'socket.ok') ([string]$process.Id)
    $deadline=[DateTime]::UtcNow.AddSeconds([int]$script:Config.batchTimeoutSeconds)
    while(!$process.WaitForExit(1000)){
        $resources=Get-TrainProcessResources $process $resources
        Publish-TrainState 'NATIVE'
        if(Test-TrainRequest 'stop'){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce}
        if(Test-TrainRequest 'pause'){Write-TrainText (Join-Path $directory 'pause.request') $script:DirectorNonce}
        if([DateTime]::UtcNow -gt $deadline){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce;throw 'TRAIN002_NATIVE_BATCH_TIMEOUT'}
    }
    $exit=Get-TrackedProcessExitCode $process $handle
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    Assert-TrainHash (Join-Path $layout.Game 'libs\GameServer.jar') $JarSha
    Write-TrainExitReceipt $layout $record $process $handle
    if($exit -ne 0 -or !(Test-Path -LiteralPath (Join-Path $directory 'batch.complete'))){throw 'TRAIN002_NATIVE_BATCH_INCOMPLETE'}
    $script:State.batchesCompleted=[int]$script:State.batchesCompleted+1
    $script:NativeRecord=$null
    }finally{
        $resources=Get-TrainProcessResources $process $resources
        $resources['attemptId']=$attempt;$resources['finishedUtc']=[DateTime]::UtcNow.ToString('o')
        Write-TrainJson (Join-Path $directory 'native-resources.json') $resources
        $process.Dispose()
    }
}
function Publish-TrainStatus {
    $evaluationConfigPath=Join-Path $layout.Root 'TRAIN_CONFIG.ini'
    if(Test-Path -LiteralPath $evaluationConfigPath){
        $evaluationConfig=Read-TrainConfig $evaluationConfigPath
        if($evaluationConfig.ContainsKey('evaluation005Enabled') -and $evaluationConfig.evaluation005Enabled -ceq 'true' -and $evaluationConfig.evaluation005Campaign -cmatch '^[A-Za-z0-9._-]{1,64}$'){
            $evaluationDirectory=Join-Path $layout.Root ('evaluation005\'+$evaluationConfig.evaluation005Campaign)
            $summary=Join-Path $evaluationDirectory 'summary.json'
            if(Test-Path -LiteralPath $summary){Write-Host ([IO.File]::ReadAllText($summary,[Text.Encoding]::UTF8))}
            Write-Host ('Парный EVAL005: '+(Join-Path $evaluationDirectory 'report.md'))
        }
    }
    if(!(Test-Path -LiteralPath $layout.Root)){Write-Host 'Тренажёр ещё не установлен. Запустите TRAIN_START.cmd.';return}
    Assert-TrainInstallation $layout
    $statePath=Join-Path $layout.Root 'director.json'
    if(Test-Path -LiteralPath $statePath){$state=Read-TrainJson $statePath;Write-Host ('Тренажёр: '+$state.phase+'; причина: '+$state.reason);Write-Host ('Запуск '+$state.runId+'; поколение '+$state.generation+'; завершено батчей '+$state.batchesCompleted);Write-Host ('Последний heartbeat: '+$state.heartbeatUtc)}else{Write-Host 'Каталог создан; директор ещё не сообщил статус.'}
    if((Test-Path -LiteralPath $statePath) -and $state.PSObject.Properties['primaryFailureId'] -and $state.primaryFailureId){Write-Host ('Первичная ошибка '+$state.primaryFailureCode+'; фаза '+$state.primaryFailurePhase+'; журнал failures/'+$state.primaryFailureId+'.json')}
    if((Test-Path -LiteralPath $statePath) -and $state.PSObject.Properties['stopNonce'] -and $state.stopNonce){Write-Host ('Штатная остановка подтверждена: '+$state.stopNonce)}
    $live=Get-TrainLiveDirector
    if($live){Write-Host ('Подтверждён собственный директор PID '+$live.pid)}else{Write-Host 'Собственный директор сейчас не работает.'}
    $status=Join-Path $layout.Root 'status.json'
    if(Test-Path -LiteralPath $status){
        $cached=Read-TrainJson $status
        Write-Host ('Сохранённые SQL-данные на '+$cached.updatedAt+': подтверждённых эпизодов '+$cached.committed+'; неполных '+$cached.incomplete+'; exact NATIVE '+$cached.events+'; попыток обучения '+$cached.attempts+'.')
        if($cached.PSObject.Properties['coverage']){
            foreach($scope in @('SOLO','PARTY')){
                $coverage=$cached.coverage.scopes.$scope
                Write-Host ($scope+': подтверждено '+$coverage.committed+'; exact events '+$coverage.exactEvents+'; актёров '+$coverage.distinctActors+'; навыков '+$coverage.distinctSkills+'.')
            }
        }
        Write-Host ('Полные факты и покрытие: '+$status)
    }
    $evaluation=Join-Path $layout.Root 'evaluation.json';if(Test-Path -LiteralPath $evaluation){$model=Read-TrainJson $evaluation;Write-Host ('Модель: '+$model.state+'; версия '+$model.modelVersion+'; '+$model.reason)}
    $v3Evaluation=Join-Path $layout.Root 'tactical-v3-evaluation.json'
    if(Test-Path -LiteralPath $v3Evaluation){$model=Read-TrainJson $v3Evaluation;Write-Host ('V3: '+$model.state+'; принятая TEST '+$model.modelVersion+'; SHA '+$model.modelSha+'; кандидат '+$model.candidateVersion);if($model.metrics.PSObject.Properties['optimizerUpdates']){Write-Host ('Оптимизатор: '+$model.metrics.optimizerMigration+'; шагов '+$model.metrics.optimizerUpdates+'; SHA '+$model.metrics.optimizerSha)};Write-Host ('Причина: '+$model.reason)}
    $request=Join-Path $layout.Root 'export-request.json';$result=Join-Path $layout.Root 'export-result.json'
    if(Test-Path -LiteralPath $request){$queued=Read-TrainJson $request;if((Test-Path -LiteralPath $result) -and (Read-TrainJson $result).id -ceq $queued.id){$export=Read-TrainJson $result;Write-Host ('Экспорт '+$export.id+': '+$export.state+'; '+$export.reason)}else{Write-Host ('Экспорт '+$queued.id+': ожидает безопасной границы батча.')}}
}
function Invoke-TrainDirector {
    if(!$Nonce){throw 'TRAIN002_DIRECTOR_NONCE_REQUIRED'}
    Assert-TrainInstallation $layout
    $self=[Diagnostics.Process]::GetCurrentProcess();$record=New-TrainProcessRecord $self $layout.PowerShell $Nonce Director $PSCommandPath
    Assert-TrainProcess $record -Kind Director
    $null=Assert-TrainBrokerProof $layout $record
    $lock=Enter-TrainLock $layout
    try{
        $previous=$null;$statePath=Join-Path $layout.Root 'director.json'
        if(Test-Path -LiteralPath $statePath){$previous=Read-TrainJson $statePath}
        $script:State=[ordered]@{schemaVersion=1;runId=$(if($previous){$previous.runId}else{[guid]::NewGuid().ToString('N')});generation=$(if($previous){[int]$previous.generation+1}else{1});nonce=$Nonce;phase='STARTING';reason='';heartbeatUtc='';attemptId='';batchesCompleted=$(if($previous){[int]$previous.batchesCompleted}else{0});nativeFailures=0;cpuSeconds=0;workingSetBytes=0;primaryFailureId='';primaryFailureCode='';primaryFailurePhase='';cleanupFailureId=''}
        Write-TrainJson (Join-Path $layout.Root 'owner-process.json') $record
        Publish-TrainState 'PREFLIGHT'
        $script:Config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
        if(Test-TrainBootstrapCancelled $layout $record){return}
        $dependencies=Get-TrainDependencies
        # Recover the prior own JVM before touching SQL, config, actors or source JAR.
        Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
        Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
        if($previous){Write-TrainJson (Join-Path $layout.Root 'recovery.json') @{previousGeneration=$previous.generation;generation=$script:State.generation;previousPhase=$previous.phase;recoveredUtc=[DateTime]::UtcNow.ToString('o')}}
        Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
        if(Test-TrainBootstrapCancelled $layout $record){return}
        $jar=Ensure-TrainBuild $dependencies
        if(Test-TrainRequest 'stop'){return}
        $assets=Get-TrainAssets;Ensure-TrainStage $jar $assets
        if((Test-TrainRequest 'stop') -or (Test-TrainBootstrapCancelled $layout $record)){return}
        $script:SqlRecord=Start-TrainSql $dependencies
        Publish-TrainState 'SQL_PREPARE'
        Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomTrainingSql' @('prepare',$layout.Root,$layout.Module)
        $isEvaluationIdentity005=($script:Config.ContainsKey('configProfile') -and $script:Config.configProfile -ceq 'EVAL005') -or ($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true')
        if($isEvaluationIdentity005 -and (!$script:Config.ContainsKey('evaluation005Enabled') -or $script:Config.evaluation005Enabled -cne 'true' -or ($script:Config.ContainsKey('v3Core004') -and $script:Config.v3Core004 -ceq 'true'))){throw 'EVAL005_TRAINING_RECOVERY_FORBIDDEN'}
        if(!$isEvaluationIdentity005 -and $script:Config.ContainsKey('v3Core004') -and $script:Config.v3Core004 -ceq 'true'){
            Publish-TrainState 'RESUMING_V3' 'Восстановление committed optimizer либо точного FROZEN батча; ON не изменяется.'
            Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
        }
        while(!(Test-TrainRequest 'stop')){
            Invoke-TrainQueuedExport $dependencies
            if(Test-TrainRequest 'pause'){
                Publish-TrainState 'PAUSED' 'Текущий батч завершён; новые эпизоды не запускаются.'
                Start-Sleep -Seconds 1
                continue
            }
            $next=Read-TrainSourceConfig
            Set-TrainV3CampaignRound $next
            foreach($port in @('sqlPort','gamePort','loginPort')){if($next[$port] -cne $script:Config[$port]){throw 'TRAIN002_ENDPOINT_CHANGE_REQUIRES_NEW_INSTALLATION'}}
            $evaluation=Join-Path $layout.Root 'evaluation.json'
            $isEvaluation005=$next.ContainsKey('evaluation005Enabled') -and $next.evaluation005Enabled -ceq 'true'
            if(!$isEvaluation005 -and (Test-Path -LiteralPath $evaluation)){$model=Read-TrainJson $evaluation;if([long]$model.modelVersion -gt 0 -and $model.modelSha -cmatch '^[0-9a-f]{64}$'){$next.learningMode='ON';$next.modelVersion=[string]$model.modelVersion;$next.modelSha=$model.modelSha}else{$next.learningMode='SHADOW';$next.modelVersion='0';$next.modelSha=''}}
            # V3 model pins are explicit source configuration, applied only at this stopped boundary.
            # A qualified trainer candidate never changes them automatically.
            Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $next;$script:Config=$next
            $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($layout.Root))
            if($drive.AvailableFreeSpace -lt ([long]$next.minFreeDiskMb*1MB)){Publish-TrainState 'PAUSED_DISK' 'Недостаточно свободного диска.';Start-Sleep -Seconds ([int]$next.idleSeconds);continue}
            Assert-TrainProcess $script:SqlRecord -Kind Sql -Port ([int]$next.sqlPort)
            Reset-TrainColdBaseline
            Publish-TrainState 'ACTORS'
            $v3Manifest=$null
            if($next.ContainsKey('v3Enabled') -and $next.v3Enabled -ceq 'true'){
                $v3Phase=if($next.v3Mode -ceq 'ON'){'on-v'+$next.v3ModelVersion+$(if($next.ContainsKey('v3ProofRound') -and [int]$next.v3ProofRound -gt 0){'-r'+$next.v3ProofRound}else{''})}else{'collect'+$(if($next.ContainsKey('v3CollectionRound') -and [int]$next.v3CollectionRound -gt 0){'-r'+$next.v3CollectionRound}else{''})}
                $v3Manifest=Join-Path $layout.Root ('v3-actors-'+$v3Phase+'.tsv')
            }
            if($isEvaluation005){
                $v3Manifest=$null
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('prepare',$layout.Root)
            }elseif($v3Manifest -and (Test-Path -LiteralPath $v3Manifest)){
                Assert-G0PhysicalFile $v3Manifest
                Publish-TrainState 'ACTORS_REUSE' 'Готовый собственный манифест; native вход повторно проверит фактических участников и допустимые действия.'
            }else{Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomTrainingActors' @($layout.Root,(Join-Path $layout.Root 'TRAIN_CONFIG.ini'))}
            try{
                if($v3Manifest){Save-TrainV3SegmentDispatch $next $v3Phase}
                $evaluationComplete=if($isEvaluation005){Join-Path $layout.Root ('evaluation005\'+$next.evaluation005Campaign+'\native.complete')}else{$null}
                if(!$evaluationComplete -or !(Test-Path -LiteralPath $evaluationComplete)){Invoke-TrainNative $dependencies $jar}
                $script:State.nativeFailures=0
            }catch{
                $null=Save-TrainFailure $layout $script:State $_ 'NATIVE_RETRY'
                Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
                $script:State.nativeFailures=[int]$script:State.nativeFailures+1
                if($isEvaluation005){throw}
                Publish-TrainState 'RETRY' 'Предыдущий батч неполон; committed события сохранены.'
                if($script:State.nativeFailures -ge 3){throw 'TRAIN002_NATIVE_CONSECUTIVE_FAILURE_LIMIT'}
                if(Test-TrainProcessAbsent $script:SqlRecord){$script:SqlRecord=Start-TrainSql $dependencies}
                Start-Sleep -Seconds ([int]$next.idleSeconds)
                continue
            }
            Publish-TrainState 'EVALUATING'
            if($isEvaluation005){
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('report',$layout.Root)
                Request-TrainBoundary 'pause' $script:DirectorNonce
                continue
            }
            Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
            Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('report',$layout.Root)
            if($next.ContainsKey('v3Enabled') -and $next.v3Enabled -ceq 'true' -and (!$next.ContainsKey('v3Core004') -or $next.v3Core004 -cne 'true') -and (Test-Path -LiteralPath (Join-Path $layout.Root ('v3-'+$v3Phase+'.complete')))){Request-TrainBoundary 'pause' $script:DirectorNonce}
            Invoke-TrainQueuedExport $dependencies
            $resourcePath=Join-Path $layout.Root ('attempts\'+$script:State.attemptId+'\native-resources.json')
            $completedResources=$null;if(Test-Path -LiteralPath $resourcePath){$completedResources=Read-TrainJson $resourcePath}
            $idle=Get-TrainAdaptiveIdle $next $completedResources
            $idle['attemptId']=$script:State.attemptId;$idle['decidedUtc']=[DateTime]::UtcNow.ToString('o')
            Write-TrainJson (Join-Path (Split-Path $resourcePath -Parent) 'adaptive-idle.json') $idle
            Write-TrainJson (Join-Path $layout.Root 'adaptive-idle.json') $idle
            $script:State['adaptiveIdleSeconds']=$idle.idleSeconds
            Publish-TrainState 'COLLECTING' $idle.reason
            for($second=0;$second -lt [int]$idle.idleSeconds -and !(Test-TrainRequest 'stop') -and !(Test-TrainRequest 'pause');$second++){Start-Sleep -Seconds 1;Publish-TrainState 'COLLECTING' $idle.reason}
        }
        Publish-TrainState 'STOPPING'
    }catch{
        $null=Save-TrainFailure $layout $script:State $_ 'PRIMARY'
        $reason=if($_.Exception.Message -match 'TRAIN002_[A-Z0-9_:]+'){$Matches[0]}else{$_.Exception.GetType().Name}
        Publish-TrainState 'FAILED' $reason
        throw
    }finally{
        $clean=$false
        try{
            if($script:Config){Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)}
            if($script:Config){Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)}
            if($script:SqlRecord -and !(Test-TrainProcessAbsent $script:SqlRecord)){Stop-TrainSql $layout $script:Config $script:SqlRecord}
            if($script:Config){Assert-TrainPortsFree @([int]$script:Config.sqlPort,[int]$script:Config.gamePort,[int]$script:Config.loginPort)}
            if(Test-Path -LiteralPath (Join-Path $layout.Root 'protected207.json')){Assert-TrainAssets (Join-Path $layout.Game 'geodata') (Join-Path $layout.Root 'geo203.json') (Join-Path $layout.Root 'protected207.json') $script:Config}
            $clean=$true
            if($script:State.phase -ne 'FAILED'){Publish-TrainState 'STOPPED'}
        }catch{
            $null=Save-TrainFailure $layout $script:State $_ 'CLEANUP'
            throw
        }finally{
            if(!$clean){Publish-TrainState 'STOP_UNCONFIRMED' 'Сохранены данные и receipts; чужие процессы не останавливались.'}
            $lock.Dispose()
        }
    }
}

# Status does not require Java, SQL, build artifacts or asset copies.
try {
    Initialize-TrainWin32
    if($Action -eq 'Status'){Publish-TrainStatus;exit 0}
    if($Action -eq 'Preflight'){
        $check=Read-TrainSourceConfig;$script:Config=$check
        $null=Get-TrainDependencies
        $assets=Get-TrainAssets
        Assert-TrainAssets $assets.Geo $assets.GeoManifest $assets.ProtectedManifest $script:Config
        Assert-TrainPortsFree @([int]$check.sqlPort,[int]$check.gamePort,[int]$check.loginPort)
        Write-Host 'Предварительная проверка пройдена: config, JDK25, MariaDB11.4, Geo203, protected207 и свободные порты. Сборок и запусков серверов не было.'
        exit 0
    }
    if($Action -eq 'Run'){Invoke-TrainDirector;exit 0}
    if(!(Test-Path -LiteralPath $layout.Root) -and $Action -notin @('Start','Resume')){
        if($Action -eq 'Stop'){Write-Host 'Тренажёр уже остановлен и ещё не установлен.';exit 0}
        throw 'TRAIN002_START_REQUIRED'
    }
    $initialization=Enter-TrainInitialization $layout
    try{
        $bootstrapArchive=$null
        if($Action -in @('Start','Resume') -and (Test-Path -LiteralPath $layout.Root) -and !(Test-Path -LiteralPath (Join-Path $layout.Root 'owner.txt'))){
            $sourceConfig=Read-TrainSourceConfig
            $bootstrapArchive=Repair-TrainEmptyBootstrap $layout $sourceConfig
        }
        if(!(Test-Path -LiteralPath $layout.Root)){
            $config=Read-TrainSourceConfig
            New-TrainPrivateRoot $layout
            foreach($name in @('logs','attempts','workers','archives','commands')){$null=[IO.Directory]::CreateDirectory((Join-Path $layout.Root $name))}
            Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $config
            if($bootstrapArchive){
                Write-TrainJson (Join-Path $layout.Root ('archives\bootstrap-recovery.'+[guid]::NewGuid().ToString('N')+'.json')) @{archive=$bootstrapArchive;reason='EMPTY_PRIVATE_BOOTSTRAP_PRESERVED';recoveredUtc=[DateTime]::UtcNow.ToString('o')}
                Write-Host 'Пустой каталог незавершённого запуска сохранён отдельно; создана новая собственная установка.'
            }
        }
        Assert-TrainInstallation $layout
        $script:Config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
        $live=Get-TrainLiveDirector
        $pendingStopped=$null;$pending=Get-TrainPendingBootstrap $layout
        if($pending){
            if($live -and $Action -notin @('Stop','Pause') -and $pending.process.pid -eq $live.pid -and [long]$pending.process.created -eq [long]$live.created -and $pending.process.nonce -ceq $live.nonce){Complete-TrainPendingBootstrap $layout $live}
            else{$pendingStopped=Stop-TrainPendingBootstrap $layout ([int]$script:Config.shutdownTimeoutSeconds+30)}
            $live=Get-TrainLiveDirector
        }
        if($Action -in @('Start','Resume')){
            if($live){
                if($Action -eq 'Resume'){Request-TrainBoundary 'resume' $live.nonce;Write-Host 'Продолжение запрошено; committed история сохранена.'}
                else{Write-Host 'Собственный директор уже работает; второй запуск не создан.'}
                Publish-TrainStatus;exit 0
            }
            # Probe the lifetime lock before launch. The initialization mutex closes
            # the fresh-root race; the detached Run owns this file lock for its life.
            $probe=Enter-TrainLock $layout;$probe.Dispose()
            Assert-TrainNoPendingBootstrap $layout
            $token=[guid]::NewGuid().ToString('N')
            $launch=Start-TrainBroker $layout $token
            Write-TrainJson (Join-Path $layout.Root ('logs\broker-launch.'+$token+'.json')) $launch
            $process=Get-Process -Id $launch.pid -ErrorAction Stop;$started=$false;$held=$null
            try{
                $held=Hold-ProcessHandle $process
                $pendingRecord=Register-TrainPendingBootstrap $layout $launch $process $held
                $deadline=[DateTime]::UtcNow.AddSeconds(15)
                while(!(Test-Path -LiteralPath $launch.readyPath) -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
                if(!(Test-Path -LiteralPath $launch.readyPath)){throw 'TRAIN002_BOOTSTRAP_READY_TIMEOUT'}
                $ready=Read-TrainJson $launch.readyPath
                if($ready.pid -ne $pendingRecord.pid -or [long]$ready.created -ne [long]$pendingRecord.created -or $ready.nonce -cne $token){throw 'TRAIN002_BOOTSTRAP_READY_IDENTITY'}
                Assert-TrainProcess $pendingRecord -Kind Worker;$null=Assert-TrainBrokerProof $layout $pendingRecord
                Write-TrainJson $launch.ackPath @{pid=$pendingRecord.pid;created=$pendingRecord.created;nonce=$token;proofSha=(Get-FileHash -LiteralPath $launch.proofPath).Hash}
                $deadline=[DateTime]::UtcNow.AddSeconds(15)
                while(!$process.HasExited -and [DateTime]::UtcNow -lt $deadline){
                    $receipt=Join-Path $layout.Root 'owner-process.json'
                    if(Test-Path -LiteralPath $receipt){
                        $running=Read-TrainJson $receipt
                        if($running.nonce -ceq $token){
                            if($running.pid -ne $launch.pid -or (Get-TrainHeldExitProof $running $process $held).exited){throw 'TRAIN002_BROKER_STARTED_INCARNATION'}
                            Assert-TrainProcess $running -Kind Director;$null=Assert-TrainBrokerProof $layout $running
                            Complete-TrainPendingBootstrap $layout $running;$started=$true;break
                        }
                    }
                    Start-Sleep -Milliseconds 200
                }
                if(!$started){throw 'TRAIN002_DETACHED_START_UNPROVEN'}
            }catch{
                $primary=$_
                try{
                    $pending=Get-TrainPendingBootstrap $layout
                    if($pending -and $pending.process.nonce -ceq $token){$null=Stop-TrainPendingBootstrap $layout ([int]$script:Config.shutdownTimeoutSeconds+30) $process $held}
                }catch{$null=Save-TrainFailure $layout $script:State $_ CLEANUP}
                throw $primary
            }finally{$process.Dispose()}
            $workLabel=if($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true'){'Подготовка и парные испытания продолжаются в отдельном процессе.'}else{'Подготовка и обучение продолжаются в отдельном процессе.'}
            Write-Host ('Автономный директор запущен, PID '+$launch.pid+'. '+$workLabel)
            Write-Host ('Статус: '+(Join-Path $PSScriptRoot 'TRAIN_STATUS.cmd'))
            exit 0
        }
        if($Action -eq 'Pause'){
            if(!$live){Write-Host 'Директор уже не работает; новых эпизодов нет.';exit 0}
            Request-TrainBoundary 'pause' $live.nonce
            Write-Host 'Пауза запрошена. Текущий эпизод завершится штатно; TRAIN_STATUS покажет PAUSED.'
            exit 0
        }
        if($Action -eq 'Stop'){
            if($live){
                $director=Get-Process -Id $live.pid -ErrorAction Stop;$directorHandle=Hold-ProcessHandle $director
                try{
                    if($director.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$live.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
                    Request-TrainBoundary 'stop' $live.nonce
                    $deadline=[DateTime]::UtcNow.AddSeconds([int]$script:Config.shutdownTimeoutSeconds+30)
                    while(!(Test-TrainProcessAbsent $live $director $directorHandle) -and [DateTime]::UtcNow -lt $deadline){
                        if(!$director.HasExited){try{Assert-TrainProcess $live -Kind Director}catch{
                            if(Test-TrainTerminatedCimRace $_ $live $director $directorHandle){break}
                            if($director.HasExited -and $_.Exception.Message -ceq 'TRAIN002_PROCESS_UNAVAILABLE'){break};throw
                        }}
                        Start-Sleep -Milliseconds 500
                    }
                    if(!(Test-TrainProcessAbsent $live $director $directorHandle)){throw 'TRAIN002_STOP_REQUESTED_NOT_CONFIRMED'}
                    Write-TrainExitReceipt $layout $live $director $directorHandle
                }finally{$director.Dispose()}
            }
            $script:DirectorNonce=[guid]::NewGuid().ToString('N')
            $guard=Enter-TrainLock $layout
            try{
                Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
                Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
                $receipt=Join-Path $layout.Sql 'owned-process.json'
                if(Test-Path -LiteralPath $receipt){$sql=Read-TrainJson $receipt;if(!(Test-TrainProcessAbsent $sql)){Stop-TrainSql $layout $script:Config $sql}}
                Assert-TrainPortsFree @([int]$script:Config.sqlPort,[int]$script:Config.gamePort,[int]$script:Config.loginPort)
                $facts=@()
                if($pendingStopped){$facts+=@{pid=$pendingStopped.pid;created=$pendingStopped.created;kind='Worker';nonce=$pendingStopped.nonce;absent=$pendingStopped.confirmed;proof=$pendingStopped.proof}}
                foreach($path in @((Join-Path $layout.Root 'owner-process.json'),(Join-Path $layout.Root 'native-process.json'),(Join-Path $layout.Root 'worker-process.json'),$receipt)){
                    if(Test-Path -LiteralPath $path){$old=Read-TrainJson $path;if(!(Test-TrainProcessAbsent $old)){throw 'TRAIN002_STOP_OWN_PROCESS_REMAINS'};$facts+=@{pid=$old.pid;created=$old.created;kind=$old.kind;absent=$true}}
                }
                $stopped=@{schemaVersion=1;runId='';generation=0;nonce='';phase='STOPPED';reason='';heartbeatUtc='';attemptId='';batchesCompleted=0;cpuSeconds=0;workingSetBytes=0}
                $statePath=Join-Path $layout.Root 'director.json'
                if(Test-Path -LiteralPath $statePath){$old=Read-TrainJson $statePath;foreach($property in $old.PSObject.Properties){$stopped[$property.Name]=$property.Value}}
                $stopped.phase='STOPPED';$stopped.reason='Собственные процессы остановлены и порты свободны.';$stopped.heartbeatUtc=[DateTime]::UtcNow.ToString('o');$stopped.stopNonce=$script:DirectorNonce;$stopped.stopConfirmedUtc=$stopped.heartbeatUtc
                Write-TrainJson (Join-Path $layout.Root ('commands\stop-confirmed.'+$script:DirectorNonce+'.json')) @{stopNonce=$script:DirectorNonce;ownerNonce=$stopped.nonce;confirmedUtc=$stopped.stopConfirmedUtc;processes=$facts;ports=@([int]$script:Config.sqlPort,[int]$script:Config.gamePort,[int]$script:Config.loginPort)}
                Write-TrainJson $statePath $stopped
            }finally{$guard.Dispose()}
            Write-Host 'Собственные процессы остановлены, порты свободны. История, модели и логи сохранены.'
            exit 0
        }
        if($Action -in @('Report','Export')){
            $dependencies=Get-TrainDependencies
            # A live director owns SQL/worker scheduling, including its evaluation
            # boundary. Export gets a separate durable queue, never a new client.
            if($live){
                if($Action -eq 'Export'){
                    if((Get-TrainExportAdmission $true $false $false) -cne 'QUEUE'){throw 'TRAIN002_EXPORT_ADMISSION'}
                    $request=Submit-TrainExportRequest
                    Write-Host ('Запрос экспорта '+$request+' сохранён. Директор выполнит его после батча; готовность и причину показывает TRAIN_STATUS.')
                    exit 0
                }
                Publish-TrainStatus
                $report=if($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true'){Join-Path $layout.Root ('evaluation005\'+$script:Config.evaluation005Campaign+'\report.md')}else{Join-Path $layout.Root 'report.md'}
                if(Test-Path -LiteralPath $report){Write-Host ('Последний независимый отчёт: '+$report)}else{Write-Host 'Первый независимый отчёт появится после завершения батча.'}
                exit 0
            }
            $guard=Enter-TrainLock $layout
            try{
                Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
                Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
                Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
                if($Action -eq 'Export' -and (Get-TrainExportAdmission $false $true $true) -cne 'OFFLINE'){throw 'TRAIN002_EXPORT_ADMISSION'}
                if($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true'){
                    Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @($Action.ToLowerInvariant(),$layout.Root)
                }else{
                    $script:SqlRecord=Start-TrainSql $dependencies
                    Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @($Action.ToLowerInvariant(),$layout.Root)
                }
            }finally{
                Wait-TrainWorker ([int]$script:Config.shutdownTimeoutSeconds)
                if($script:SqlRecord -and !(Test-TrainProcessAbsent $script:SqlRecord)){Stop-TrainSql $layout $script:Config $script:SqlRecord}
                $guard.Dispose()
            }
            Write-Host ('Команда '+$Action+' завершена. Результаты: '+$layout.Root)
            exit 0
        }
    }finally{$initialization.ReleaseMutex();$initialization.Dispose()}
}catch{
    $controlFailure=$_
    if(Test-Path -LiteralPath (Join-Path $layout.Root 'owner.txt')){
        try{$null=Save-TrainFailure $layout $script:State $controlFailure 'CONTROL'}catch{Write-TrainSafeError $_}
    }
    Write-TrainSafeError $controlFailure
    $reason=if($controlFailure.Exception.Message -match 'TRAIN002_[A-Z0-9_:]+'){$Matches[0]}else{$controlFailure.Exception.GetType().Name}
    Write-Host ('Команда не завершена: '+$reason+'. Данные сохранены; смотрите TRAIN_STATUS и приватные логи установки.')
    exit 1
}
