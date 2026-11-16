# Shared owner primitives. Dot-sourcing this file does not launch any process.
. (Join-Path $PSScriptRoot '..\G0SqlIdentity.ps1')
$script:TrainUtf8=[Text.UTF8Encoding]::new($false)
$script:TrainGeoSha='A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5'
$script:TrainProtectedSha='9777AB48BC8D14BDEE77D4ACA8E7E650CB099F03CA33FEA108D5CDB941C59DCD'
$script:TrainProtected005Sha='A523F7FFE87682D2C1BBB0D23F40CAD8DB9634F7F2BFCED83232C3E6703D754A'
$script:TrainProtected006Sha='246AC12CD718E698B18E6AB2B338622B24D505F75BA492EB1CD348BCA2E335CD'
$script:TrainWin32Initialized=$false
function Assert-TrainActionValue019Proof([int]$ExitCode,$Proof,[string]$ExpectedSha) {
    if($ExitCode -ne 0 -or !$Proof -or $Proof.modelSha -cne $ExpectedSha -or $Proof.MODEL_CONSULTED -ne $true -or
        $Proof.NATIVE_EXECUTED -ne $true -or $Proof.EFFECT_PAID -ne $true -or $Proof.nativePaid -lt 1){throw 'TRAIN019_NATIVE_EVIDENCE_MISSING_OR_FAILED'}
}
function Assert-TrainActionValue020Proof([int]$ExitCode,$Proof,[string]$ExpectedSha) {
    Assert-TrainActionValue019Proof $ExitCode $Proof $ExpectedSha
    if($Proof.settled -ne $true -or $Proof.episodeSha -cnotmatch '^[a-f0-9]{64}$' -or $Proof.newTrainSamples -lt 1){throw 'TRAIN020_ORIGINAL_SETTLEMENT_REQUIRED'}
}
function Assert-TrainActionValue021Proof([int]$ExitCode,$Proof,[string]$ExpectedSha) {
    Assert-TrainActionValue020Proof $ExitCode $Proof $ExpectedSha
    if(!$Proof.EXPERIMENT_REORDERED -or $Proof.experimentalPaid -lt 1 -or $Proof.baselinePaid -lt 1 -or
       $Proof.nativePaid -ne ($Proof.experimentalPaid+$Proof.baselinePaid) -or $Proof.MODEL_REORDERED){throw 'TRAIN021_EXPERIMENT_CONTROL_PROOF_REQUIRED'}
}
function Invoke-TrainCleanup020([scriptblock[]]$Steps) {
    $failures=[Collections.Generic.List[string]]::new()
    foreach($step in $Steps){try{& $step}catch{$failures.Add($_.Exception.Message)}}
    if($failures.Count){throw ('TRAIN020_CLEANUP_FAILED: '+($failures -join ' | '))}
}
function Invoke-TrainV4Java020($Layout,[string]$ClassPath,[string]$Command,[string[]]$Arguments) {
    $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
    $args020=Get-TrainJavaArguments 'org.l2jmobius.tests.phantoms.PhantomLearning020V4Native' (@($Command)+$Arguments) $ClassPath ([guid]::NewGuid().ToString('N')) 2 512
    if($Layout.ContainsKey('Experiment021') -and $Layout.Experiment021){$args020=@('-Dphantom.learning.explore021=true')+$args020}
    if($Layout.ContainsKey('Continuation021') -and $Layout.Continuation021){$args020=@('-Dphantom.learning.explore021.continuation=true')+$args020}
    $logStem=if(Test-Path -LiteralPath (Join-Path $Layout.Root ($Command+'.out'))){$Command+'-'+[guid]::NewGuid().ToString('N')}else{$Command}
    $process=Start-Process -FilePath $java -ArgumentList $args020 -WorkingDirectory $Layout.Root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Layout.Root ($logStem+'.out')) -RedirectStandardError (Join-Path $Layout.Root ($logStem+'.err'))
    $handle=Hold-ProcessHandle $process;$clock=[Diagnostics.Stopwatch]::StartNew()
    if(!$process.WaitForExit(120000)){$process.Kill();$null=$process.WaitForExit(10000);throw 'TRAIN020_OFFLINE_DEADLINE'}
    $clock.Stop();$exit=Get-TrackedProcessExitCode $process $handle
    Write-TrainJson (Join-Path $Layout.Root ($logStem+'-exit.json')) @{exit=$exit;elapsedMillis=$clock.ElapsedMilliseconds}
    if($exit -ne 0){throw ('TRAIN020_'+$Command.ToUpperInvariant()+'_FAILED')}
}
function Test-TrainActionValue020Complete([string]$Root,[switch]$Experiment021) {
    try{
        foreach($n in @('native020.json','fit020.json','cold020.json','safety020.json','candidate.bin')){if(!(Test-Path -LiteralPath (Join-Path $Root $n))){return $false}}
        $parent=if($Experiment021){'42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c'}else{'569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061'}
        $before=if($Experiment021){179}else{161}
        $proof=Read-TrainJson (Join-Path $Root 'native020.json');Assert-TrainActionValue020Proof 0 $proof $parent
        $fit=Read-TrainJson (Join-Path $Root 'fit020.json');$cold=Read-TrainJson (Join-Path $Root 'cold020.json');$safety=Read-TrainJson (Join-Path $Root 'safety020.json')
        $candidatePattern=if($Experiment021){'^candidate021-[a-f0-9]{64}\.bin$'}else{'^candidate020-[a-f0-9]{64}\.bin$'}
        if($fit.parentSha -cne $parent -or $fit.candidate -cnotmatch $candidatePattern -or $fit.newSha -cnotmatch '^[a-f0-9]{64}$' -or
           $fit.updatesBefore -ne $before -or $fit.updatesAfter -ne ($before+$fit.newTrainSamples) -or $fit.newTrainSamples -ne $proof.newTrainSamples -or
           $cold.sha -cne $fit.newSha -or $cold.updates -ne $fit.updatesAfter -or $cold.replayAdditionalUpdates -ne 0 -or !$cold.separateJvm -or !$cold.tamperRejected -or
           $safety.test -cne 'OFF' -or $safety.ownedProcesses -ne 0 -or !$safety.portsFree){return $false}
        Assert-TrainHash (Join-Path $Root 'candidate.bin') $parent
        Assert-TrainHash (Join-Path $Root $fit.candidate) $fit.newSha
        Assert-TrainHash (Join-Path $Root 'native020.json') $fit.nativeSha
        if($Experiment021 -and (!$proof.EXPERIMENT_REORDERED -or $proof.experimentalPaid -lt 1 -or $proof.baselinePaid -lt 1)){return $false}
        if($Experiment021){
            $seal=Read-TrainJson (Join-Path $Root 'native021.complete.json')
            Assert-TrainHash (Join-Path $Root 'native020.json') $seal.nativeSha
            Assert-TrainHash (Join-Path $Root 'manifest021.json') $seal.manifestSha
        }
        foreach($pin in $proof.files.PSObject.Properties){
            $path=[IO.Path]::GetFullPath((Join-Path $Root $pin.Name))
            if(!$path.StartsWith($Root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){return $false}
            Assert-TrainHash $path $pin.Value
        }
        return $true
    }catch{return $false}
}
function Request-TrainActionValue020Stop([string]$Root,[bool]$Complete,[bool]$Live) {
    if(!$Complete -and !(Test-Path -LiteralPath (Join-Path $Root 'stop.request'))){Write-TrainText (Join-Path $Root 'stop.request') 'OPERATOR020_STOP'}
    return $(if($Complete -and !$Live){'STOPPED_PROOF_PRESERVED'}else{'STOP_REQUESTED'})
}
function Invoke-TrainActionValue020Step([string]$HistoryRoot,[string]$CandidateDirectory,[ValidateSet('RunOnce','Status','Stop')][string]$Command) {
    $HistoryRoot=[IO.Path]::GetFullPath($HistoryRoot).TrimEnd('\');Assert-TrainInstallation @{Root=$HistoryRoot}
    if((Split-Path $HistoryRoot -Leaf) -cne 'train'){throw 'TRAIN020_HISTORY_ROOT_REQUIRED'}
    $root=Join-Path (Split-Path $HistoryRoot -Parent) 'train020-c1'
    if(!(Test-Path -LiteralPath $root)){
        if($Command -ceq 'RunOnce'){
            if(!$CandidateDirectory){throw 'TRAIN020_PARENT_CANDIDATE_REQUIRED'}
            Invoke-TrainActionValue020 $HistoryRoot $CandidateDirectory -Continuation;return
        }
        Write-Output '{"trainerStatus":"NOT_STARTED","gsBoots":0}';return
    }
    Assert-TrainPrivate $root;Initialize-TrainWin32
    $complete=Test-TrainActionValue020Complete $root
    if($Command -ceq 'Stop'){
        # Кооперативный Stop должен сохраняться даже при отсутствующей/повреждённой identity-записи.
        Write-Output ('{"trainerStatus":"'+(Request-TrainActionValue020Stop $root $complete $false)+'"}');return
    }
    $native=if(Test-Path -LiteralPath (Join-Path $root 'native-process.json')){Read-TrainJson (Join-Path $root 'native-process.json')}else{$null}
    $sql=if(Test-Path -LiteralPath (Join-Path $root 'sql\owned-process.json')){Read-TrainJson (Join-Path $root 'sql\owned-process.json')}else{$null}
    $live=($native -and !(Test-TrainProcessAbsent $native)) -or ($sql -and !(Test-TrainProcessAbsent $sql))
    if($Command -ceq 'RunOnce'){
        if($live){throw 'TRAIN020_STEP_ALREADY_RUNNING'}
        if(!(Test-Path -LiteralPath (Join-Path $root 'native020.json')) -or !(Test-Path -LiteralPath (Join-Path $root 'safety020.json'))){throw 'TRAIN020_NO_GO_USED_ROOT_WITHOUT_SETTLED_PROOF'}
        $safety=Read-TrainJson (Join-Path $root 'safety020.json')
        if($safety.test -cne 'OFF' -or $safety.ownedProcesses -ne 0 -or !$safety.portsFree){throw 'TRAIN020_STOPPED_PROOF_REQUIRED'}
        $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'));$clone=Split-Path $module -Parent
        $classpath=(Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $root 'game\libs\*')
        $layout=@{Root=$root};Invoke-TrainV4Java020 $layout $classpath 'fit' @($root);Invoke-TrainV4Java020 $layout $classpath 'cold' @($root)
    }
    $fit=if(Test-Path -LiteralPath (Join-Path $root 'fit020.json')){Read-TrainJson (Join-Path $root 'fit020.json')}else{$null}
    $complete=Test-TrainActionValue020Complete $root
    [ordered]@{trainerStatus=$(if($live){'RUNNING'}elseif($complete){'COMPLETED'}elseif(Test-Path -LiteralPath (Join-Path $root 'cold020.json')){'NO_GO_INCOMPLETE_OR_INVALID_PROOF'}elseif(Test-Path -LiteralPath (Join-Path $root 'native020.json')){'SETTLED_PENDING_SAVE'}else{'NO_GO_NO_SETTLED_PROOF'});updates=$(if($fit){$fit.updatesAfter}else{161});checkpointSha=$(if($fit){$fit.newSha}else{$null});automaticNextStep=$false}|ConvertTo-Json -Compress
}
function Invoke-TrainActionValue021Step([string]$HistoryRoot,[string]$CandidateDirectory,[ValidateSet('RunOnce','Status','Stop')][string]$Command,[switch]$Continuation021) {
    $HistoryRoot=[IO.Path]::GetFullPath($HistoryRoot).TrimEnd('\');Assert-TrainInstallation @{Root=$HistoryRoot}
    if((Split-Path $HistoryRoot -Leaf) -cne 'train'){throw 'TRAIN021_HISTORY_ROOT_REQUIRED'}
    $root=Join-Path (Split-Path $HistoryRoot -Parent) $(if($Continuation021){'train021-c1'}else{'train021'})
    if(!(Test-Path -LiteralPath $root)){
        if($Command -ceq 'RunOnce'){Invoke-TrainActionValue020 $HistoryRoot $CandidateDirectory -Experiment021 -Continuation021:$Continuation021;return}
        Write-Output '{"trainerStatus":"NOT_STARTED","gsBoots":0,"automaticNextStep":false}';return
    }
    Assert-TrainPrivate $root;Initialize-TrainWin32
    $complete=Test-TrainActionValue020Complete $root -Experiment021
    if($Command -ceq 'Stop'){Write-Output ('{"trainerStatus":"'+(Request-TrainActionValue020Stop $root $complete $false)+'"}');return}
    $live=$false
    foreach($p in @('native-process.json','sql\owned-process.json')){
        if(Test-Path -LiteralPath (Join-Path $root $p)){$live=$live -or !(Test-TrainProcessAbsent (Read-TrainJson (Join-Path $root $p)))}
    }
    if($Command -ceq 'RunOnce'){
        if($live){throw 'TRAIN021_ALREADY_RUNNING'}
        if(!(Test-Path -LiteralPath (Join-Path $root 'safety020.json'))){throw 'TRAIN021_NO_SECOND_BOOT_USED_ROOT'}
        $safety=Read-TrainJson (Join-Path $root 'safety020.json')
        if($safety.test -cne 'OFF' -or $safety.ownedProcesses -ne 0 -or !$safety.portsFree){throw 'TRAIN021_STOPPED_PROOF_REQUIRED'}
        $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'));$clone=Split-Path $module -Parent
        $classpath=(Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $root 'game\libs\*')
        $layout=@{Root=$root;Experiment021=$true;Continuation021=[bool]$Continuation021}
        if($Continuation021 -and !(Test-Path -LiteralPath (Join-Path $root 'native020.json'))){Invoke-TrainV4Java020 $layout $classpath 'recover-publication' @($root)}
        if(!(Test-Path -LiteralPath (Join-Path $root 'native020.json'))){throw 'TRAIN021_NO_SECOND_BOOT_USED_ROOT'}
        Invoke-TrainV4Java020 $layout $classpath 'fit' @($root);Invoke-TrainV4Java020 $layout $classpath 'cold' @($root)
        $complete=Test-TrainActionValue020Complete $root -Experiment021
    }
    $fit=if(Test-Path -LiteralPath (Join-Path $root 'fit020.json')){Read-TrainJson (Join-Path $root 'fit020.json')}else{$null}
    [ordered]@{schemaVersion=21;stepResultVersion=1;trainerStatus=$(if($live){'RUNNING'}elseif($complete){'COMPLETED'}else{'NO_GO_INCOMPLETE_PROOF'});updates=$(if($fit){$fit.updatesAfter}else{179});checkpointSha=$(if($fit){$fit.newSha}else{$null});parentSha='42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c';additionalUpdates=$(if($fit){[long]$fit.updatesAfter-179}else{0});sourceSnapshotSha=$(if(Test-Path -LiteralPath (Join-Path $root 'native020.json')){(Get-FileHash -LiteralPath (Join-Path $root 'native020.json')).Hash.ToLowerInvariant()}else{$null});automaticNextStep=$false}|ConvertTo-Json -Compress
}
function Invoke-TrainActionValue020([string]$HistoryRoot,[string]$CandidateDirectory,[switch]$PreflightOnly,[switch]$Prepared,[switch]$Continuation,[switch]$Experiment021,[switch]$Continuation021) {
    if($Continuation021 -and (!$Experiment021 -or $Continuation)){throw 'TRAIN021_EXCLUSIVE_CONTINUATION'}
    $HistoryRoot=[IO.Path]::GetFullPath($HistoryRoot).TrimEnd('\');$CandidateDirectory=[IO.Path]::GetFullPath($CandidateDirectory).TrimEnd('\')
    Assert-TrainDirectory $HistoryRoot;Assert-TrainDirectory $CandidateDirectory
    if((Split-Path $HistoryRoot -Leaf) -cne 'train' -or (Read-TrainJson (Join-Path $HistoryRoot 'director.json')).phase -cne 'STOPPED'){throw 'TRAIN020_STOPPED_HISTORY_REQUIRED'}
    Assert-TrainInstallation @{Root=$HistoryRoot;Sql=(Join-Path $HistoryRoot 'sql')};Initialize-TrainWin32
    foreach($path in @('sql\owned-process.json','native-process.json','owner-process.json')){if(!(Test-TrainProcessAbsent (Read-TrainJson (Join-Path $HistoryRoot $path)))){throw 'TRAIN020_HISTORY_LIVE'}}
    $ports020=@(33357,18001,19301,33360,18020,19320,33361,18021,19321)
    if($Experiment021){$ports020+=@(33362,18022,19322)}
    if($Continuation021){$ports020+=@(33363,18023,19323)}
    Assert-TrainPortsFree $ports020
    $root=Join-Path (Split-Path $HistoryRoot -Parent) $(if($Continuation021){'train021-c1'}elseif($Experiment021){'train021'}elseif($Continuation){'train020-c1'}else{'train020'})
    $sqlPort=if($Continuation021){33363}elseif($Experiment021){33362}elseif($Continuation){33361}else{33360};$gamePort=if($Continuation021){18023}elseif($Experiment021){18022}elseif($Continuation){18021}else{18020};$loginPort=if($Continuation021){19323}elseif($Experiment021){19322}elseif($Continuation){19321}else{19320}
    if($Continuation021){
        $previous=Join-Path (Split-Path $HistoryRoot -Parent) 'train021';Assert-TrainPrivate $previous
        $previousSafety=Read-TrainJson (Join-Path $previous 'safety020.json')
        if($previousSafety.gsStarts -ne 1 -or $previousSafety.test -cne 'OFF' -or $previousSafety.ownedProcesses -ne 0 -or !$previousSafety.portsFree){throw 'TRAIN021_FIRST_BOOT_STOPPED_REQUIRED'}
        foreach($p in @('native-process.json','sql\owned-process.json')){if(!(Test-TrainProcessAbsent (Read-TrainJson (Join-Path $previous $p)))){throw 'TRAIN021_PREVIOUS_PROCESS_LIVE'}}
    }
    if($Experiment021 -and !(Test-TrainActionValue020Complete (Join-Path (Split-Path $HistoryRoot -Parent) 'train020-c1'))){throw 'TRAIN021_ACCEPTED_PARENT_PROOF_REQUIRED'}
    if($Continuation){
        $previous=Join-Path (Split-Path $HistoryRoot -Parent) 'train020';Assert-TrainPrivate $previous
        if((Read-TrainJson (Join-Path $previous 'safety020.json')).gsStarts -ne 1){throw 'TRAIN020_FIRST_BOOT_RECEIPT_REQUIRED'}
        foreach($p in @('native-process.json','sql\owned-process.json')){if(!(Test-TrainProcessAbsent (Read-TrainJson (Join-Path $previous $p)))){throw 'TRAIN020_PREVIOUS_PROCESS_LIVE'}}
    }
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'));$clone=Split-Path $module -Parent
    $bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
    $layout=@{Root=$root;Game=(Join-Path $root 'game');Sql=(Join-Path $root 'sql');Admin=(Join-Path $bin 'mariadb-admin.exe');Daemon=(Join-Path $bin 'mariadbd.exe');Experiment021=[bool]$Experiment021;Continuation021=[bool]$Continuation021}
    $classpath=(Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $module 'dist\libs\*')
    $cohort=Join-Path $HistoryRoot 'autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001'
    $modelSha=if($Experiment021){'42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c'}else{'569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061'}
    $lease=[IO.FileStream]::new((Join-Path $HistoryRoot 'director.lock'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
    $sql=$null;$sqlProcess=$null;$native=$null;$nativeRecord=$null;$nativeHandle=$null;$clock=$null;$failure=$null;$pins=@{};$admittedRoot=$false
    try {
        if(!$Prepared){
            New-TrainPrivateRoot $layout
            $admittedRoot=$true
            $parentFile=if($Experiment021){'candidate020-'+$modelSha+'.bin'}else{'candidate.bin'}
            Copy-Item -LiteralPath (Join-Path $CandidateDirectory $parentFile) -Destination (Join-Path $root 'candidate.bin')
            Copy-Item -LiteralPath (Join-Path $HistoryRoot 'TRAIN_CONFIG.ini') -Destination $root
            Assert-TrainHash (Join-Path $root 'candidate.bin') $modelSha.ToUpperInvariant()
            Invoke-TrainV4Java020 $layout $classpath 'preflight' @($cohort,$root)
            if($PreflightOnly){return}
        }else{
            Assert-TrainPrivate $root
            if(!(Test-Path -LiteralPath (Join-Path $root 'preflight020.json')) -or (Test-Path -LiteralPath (Join-Path $root 'boot-reserved020.json'))){throw 'TRAIN020_NO_SECOND_BOOT'}
            $admittedRoot=$true
        }
        $historyConfig=Read-TrainConfig (Join-Path $HistoryRoot 'TRAIN_CONFIG.ini')
        Assert-TrainAssets (Join-Path $HistoryRoot 'game\geodata') (Join-Path $HistoryRoot 'geo203.json') (Join-Path $HistoryRoot 'protected207.json') $historyConfig
        $files=@(Get-ChildItem -LiteralPath (Join-Path $HistoryRoot 'autonomous') -File -Recurse)
        foreach($dir in @('outcome006','sql\data')){$files+=@(Get-ChildItem -LiteralPath (Join-Path $HistoryRoot $dir) -File -Recurse)}
        $files+=@(Get-ChildItem -LiteralPath $CandidateDirectory -File -Recurse)
        if($Continuation){$files+=@(Get-ChildItem -LiteralPath (Join-Path (Split-Path $HistoryRoot -Parent) 'train020') -File -Recurse)}
        if($Experiment021){foreach($old in @('train019','train020','train020-c1')){$files+=@(Get-ChildItem -LiteralPath (Join-Path (Split-Path $HistoryRoot -Parent) $old) -File -Recurse)}}
        if($Continuation021){$files+=@(Get-ChildItem -LiteralPath (Join-Path (Split-Path $HistoryRoot -Parent) 'train021') -File -Recurse);$files=@($files|Sort-Object FullName -Unique)}
        foreach($relative in @('owner.txt','geo203.json','protected207.json','TRAIN_CONFIG.ini','director.json','game\libs\GameServer.jar')){$files+=Get-Item -LiteralPath (Join-Path $HistoryRoot $relative)}
        foreach($file in $files){$pins[$file.FullName]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
        Write-TrainJson (Join-Path $root 'preservation-before.json') $pins
        Copy-Item -LiteralPath (Join-Path $HistoryRoot 'game') -Destination $root -Recurse
        $null=[IO.Directory]::CreateDirectory($layout.Sql);Copy-Item -LiteralPath (Join-Path $HistoryRoot 'sql\data') -Destination $layout.Sql -Recurse
        Copy-Item -LiteralPath (Join-Path $module 'dist\libs\GameServer.jar') -Destination (Join-Path $layout.Game 'libs\GameServer.jar') -Force
        Write-TrainText (Join-Path $layout.Sql 'admin.client.ini') ([IO.File]::ReadAllText((Join-Path $HistoryRoot 'sql\admin.client.ini')).Replace('port=33357',('port='+$sqlPort)))
        $database=Join-Path $layout.Game 'config\Database.ini';Write-TrainText $database ([IO.File]::ReadAllText($database).Replace('127.0.0.1:33357/',('127.0.0.1:'+$sqlPort+'/')))
        $server=Join-Path $layout.Game 'config\Server.ini';$body=[IO.File]::ReadAllText($server)
        $body=[regex]::Replace($body,'(?m)^GameserverPort\s*=.*$',('GameserverPort = '+$gamePort));$body=[regex]::Replace($body,'(?m)^LoginPort\s*=.*$',('LoginPort = '+$loginPort));Write-TrainText $server $body
        $config=Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini';Write-TrainText $config (Get-TrainColdBaseline ([IO.File]::ReadAllText($config)))
        $nonce=[guid]::NewGuid().ToString('N');$options=Join-Path $layout.Sql ('owned-server.'+$nonce+'.ini')
        Write-TrainText $options ((@('[mysqld]','bind-address=127.0.0.1',('port='+$sqlPort),('datadir="'+(Join-Path $layout.Sql 'data').Replace('\','/')+'"'),'default-time-zone=+00:00')) -join "`n")
        $sqlProcess=Start-Process -FilePath $layout.Daemon -ArgumentList @(('"--defaults-file='+$options+'"'),'--console') -WorkingDirectory $layout.Sql -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'sql.out') -RedirectStandardError (Join-Path $root 'sql.err')
        $null=Hold-ProcessHandle $sqlProcess;$sql=New-TrainProcessRecord $sqlProcess $layout.Daemon $nonce Sql $options
        Write-TrainJson (Join-Path $layout.Sql 'owned-process.json') $sql
        $ready=[DateTime]::UtcNow.AddSeconds(30)
        while($true){& $layout.Admin ('--defaults-file='+(Join-Path $layout.Sql 'admin.client.ini')) --protocol=tcp ping 1>$null 2>$null;if($LASTEXITCODE -eq 0){break};if($sqlProcess.HasExited -or [DateTime]::UtcNow -ge $ready){throw 'TRAIN020_SQL_NOT_READY'};Start-Sleep -Milliseconds 250}
        $values=@{sqlPort=[string]$sqlPort;shutdownTimeoutSeconds='30'};Assert-TrainSqlOptions $layout $values $sql;Assert-TrainProcess $sql -Kind Sql -Port $sqlPort
        if($Continuation -or $Experiment021){Invoke-TrainV4Java020 $layout $classpath 'source-sql' @($root)}
        $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
        $classpath=(Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $layout.Game 'libs\*')
        $jarSha=(Get-FileHash -LiteralPath (Join-Path $layout.Game 'libs\GameServer.jar')).Hash.ToLowerInvariant()
        $preflightSha=(Get-FileHash -LiteralPath (Join-Path $root 'preflight020.json')).Hash.ToLowerInvariant()
        $args020=Get-TrainJavaArguments 'org.l2jmobius.tests.phantoms.PhantomLearning020V4Native' @('native',$root,$jarSha,$modelSha,$preflightSha) $classpath $nonce 2 4096
        if($Experiment021){$args020=@('-Dphantom.learning.explore021=true')+$args020}
        if($Continuation021){$args020=@('-Dphantom.learning.explore021.continuation=true')+$args020}
        $null=[IO.Directory]::CreateDirectory((Join-Path $root ('attempts\'+$nonce)))
        if(Test-Path -LiteralPath (Join-Path $root 'boot-reserved020.json')){throw 'TRAIN020_NO_SECOND_BOOT'}
        Write-TrainJson (Join-Path $root 'boot-reserved020.json') @{nonce=$nonce;jarSha=$jarSha;preflightSha=$preflightSha;modelSha=$modelSha;goal020BootOrdinal=$(if($Continuation -or $Continuation021){2}else{1});priorNativeMillis=$(if($Continuation021){93190}elseif($Continuation){127510}else{0});sourceSqlSha=$(if($Continuation -or $Experiment021){(Get-FileHash -LiteralPath (Join-Path $root 'source-sql020.json')).Hash.ToLowerInvariant()}else{''})}
        $clock=[Diagnostics.Stopwatch]::StartNew()
        $native=Start-Process -FilePath $java -ArgumentList $args020 -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'native.out') -RedirectStandardError (Join-Path $root 'native.err')
        $nativeHandle=Hold-ProcessHandle $native;$nativeRecord=New-TrainProcessRecord $native $java $nonce Native (Join-Path $root 'candidate.bin')
        Write-TrainJson (Join-Path $root 'native-process.json') $nativeRecord
        $nativeLimit=if($Experiment021){360000}else{430000};$drainAt=if($Experiment021){270000}else{300000}
        while(!$native.HasExited -and $clock.ElapsedMilliseconds -lt $nativeLimit){
            if($clock.ElapsedMilliseconds -ge $drainAt -and !(Test-Path -LiteralPath (Join-Path $root 'stop.request'))){Write-TrainText (Join-Path $root 'stop.request') '020_BOUNDED_DRAIN'}
            Start-Sleep -Milliseconds 250
        }
        if(!$native.HasExited){Assert-TrainProcess $nativeRecord -Kind Native;$native.Kill();$null=$native.WaitForExit(10000);throw 'TRAIN020_NATIVE_DEADLINE'}
        $exit=Get-TrackedProcessExitCode $native $nativeHandle;Write-TrainExitReceipt $layout $nativeRecord $native $nativeHandle
        if(!(Test-Path -LiteralPath (Join-Path $root 'native020.json'))){throw 'TRAIN020_NO_NATIVE_PROOF_SEE_REFUSED_RECEIPTS'}
        Assert-TrainActionValue020Proof $exit (Read-TrainJson (Join-Path $root 'native020.json')) $modelSha
        if($Experiment021){Assert-TrainActionValue021Proof $exit (Read-TrainJson (Join-Path $root 'native020.json')) $modelSha}
    }catch{$failure=$_}finally{
        if(!$admittedRoot){$lease.Dispose()}else{
        try{
            Invoke-TrainCleanup020 @(
                {if($native -and !$native.HasExited){Write-TrainText (Join-Path $root 'stop.request') '020_DRAIN'}},
                {if($native -and !$native.HasExited -and !$native.WaitForExit(30000)){
                    if($nativeRecord){Assert-TrainProcess $nativeRecord -Kind Native}
                    # Объект и cached handle получены непосредственно от Start-Process, без поиска по PID.
                    $null=Hold-ProcessHandle $native;$native.Kill();if(!$native.WaitForExit(10000)){throw 'TRAIN020_NATIVE_STOP_UNCONFIRMED'}
                }},
                {if(Test-Path -LiteralPath (Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini')){$config=Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini';Write-TrainText $config (Get-TrainColdBaseline ([IO.File]::ReadAllText($config)))}},
                {if($sql -and !(Test-TrainProcessAbsent $sql)){Stop-TrainSql $layout @{sqlPort=[string]$sqlPort;shutdownTimeoutSeconds='30'} $sql}
                 elseif($sqlProcess -and !$sqlProcess.HasExited){
                    # Identity-record не создан, GS ещё не запускался: остановить только private SQL-копию по retained handle.
                    $null=Hold-ProcessHandle $sqlProcess
                    $sqlProcess.Kill();if(!$sqlProcess.WaitForExit(10000)){throw 'TRAIN020_SQL_STOP_UNCONFIRMED'}
                 }},
                {if($clock){$clock.Stop()}
                 foreach($path in $pins.Keys){if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $pins[$path]){throw 'TRAIN020_HISTORICAL_DRIFT'}}
                 if($pins.Count){Assert-TrainAssets (Join-Path $HistoryRoot 'game\geodata') (Join-Path $HistoryRoot 'geo203.json') (Join-Path $HistoryRoot 'protected207.json') $historyConfig}},
                {Assert-TrainPortsFree $ports020
                 if(($native -and !$native.HasExited) -or ($sqlProcess -and !$sqlProcess.HasExited)){throw 'TRAIN020_OWN_PROCESS_REMAINS'}}
            )
            if(!$PreflightOnly){Write-TrainJson (Join-Path $root 'safety020.json') @{gsStarts=$(if($clock){1}else{0});goal020TotalGsStarts=$(if(($Continuation -or $Continuation021) -and $clock){2}elseif($clock){1}else{0});nativeElapsedMillis=$(if($clock){$clock.ElapsedMilliseconds}else{0});preservedFiles=$pins.Count;historicalDrift=0;test='OFF';ownedProcesses=0;portsFree=$true}}
        }finally{$lease.Dispose()}
        }
    }
    if($failure){throw $failure}
    Invoke-TrainV4Java020 $layout $classpath 'fit' @($root)
    Invoke-TrainV4Java020 $layout $classpath 'cold' @($root)
    Write-Output ('TRAIN020_SINGLE_STEP_CLOSED root='+$root)
}
function Invoke-TrainActionValue019([string]$HistoryRoot,[string]$CandidateDirectory) {
    if(!$HistoryRoot -or !$CandidateDirectory){throw 'TRAIN019_EXPLICIT_INPUTS'}
    $HistoryRoot=[IO.Path]::GetFullPath($HistoryRoot).TrimEnd('\');$CandidateDirectory=[IO.Path]::GetFullPath($CandidateDirectory).TrimEnd('\')
    Assert-TrainDirectory $HistoryRoot;Assert-TrainDirectory $CandidateDirectory
    if((Split-Path $HistoryRoot -Leaf) -cne 'train' -or (Read-TrainJson (Join-Path $HistoryRoot 'director.json')).phase -cne 'STOPPED'){throw 'TRAIN019_STOPPED_HISTORY_REQUIRED'}
    $old=@{Root=$HistoryRoot;Sql=(Join-Path $HistoryRoot 'sql')};Assert-TrainInstallation $old
    Initialize-TrainWin32
    foreach($path in @('sql\owned-process.json','native-process.json','owner-process.json')){
        $record=Read-TrainJson (Join-Path $HistoryRoot $path)
        if(!(Test-TrainProcessAbsent $record)){throw 'TRAIN019_HISTORY_OWNER_LIVE'}
    }
    Assert-TrainPortsFree @(33357,18001,19301,33359,18019,19319)
    $lease=[IO.FileStream]::new((Join-Path $HistoryRoot 'director.lock'),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
    $root=Join-Path (Split-Path $HistoryRoot -Parent) 'train019'
    if(Test-Path -LiteralPath $root){$lease.Dispose();throw 'TRAIN019_FRESH_ROOT_ONLY_NO_SECOND_BOOT'}
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'));$clone=Split-Path $module -Parent
    $bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
    $layout=@{Root=$root;Game=(Join-Path $root 'game');Sql=(Join-Path $root 'sql');Admin=(Join-Path $bin 'mariadb-admin.exe');Daemon=(Join-Path $bin 'mariadbd.exe')}
    $values=@{sqlPort='33359';shutdownTimeoutSeconds='30'}
    $sql=$null;$native=$null;$nativeRecord=$null;$nativeHandle=$null;$clock=$null;$failure=$null;$pins=@{}
    try {
        New-TrainPrivateRoot $layout
        $files=@(Get-ChildItem -LiteralPath (Join-Path $HistoryRoot 'autonomous') -File -Recurse)
        $files+=@(Get-ChildItem -LiteralPath (Join-Path $HistoryRoot 'outcome006') -File -Recurse)
        $files+=@(Get-ChildItem -LiteralPath (Join-Path $HistoryRoot 'sql\data') -File -Recurse)
        foreach($relative in @('owner.txt','geo203.json','protected207.json','TRAIN_CONFIG.ini','director.json','game\libs\GameServer.jar')){$files+=Get-Item -LiteralPath (Join-Path $HistoryRoot $relative)}
        foreach($file in $files){$pins[$file.FullName]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
        Write-TrainJson (Join-Path $root 'preservation-before.json') $pins
        Copy-Item -LiteralPath (Join-Path $HistoryRoot 'game') -Destination $root -Recurse
        $null=[IO.Directory]::CreateDirectory($layout.Sql)
        Copy-Item -LiteralPath (Join-Path $HistoryRoot 'sql\data') -Destination $layout.Sql -Recurse
        Copy-Item -LiteralPath (Join-Path $module 'dist\libs\GameServer.jar') -Destination (Join-Path $layout.Game 'libs\GameServer.jar') -Force
        Copy-Item -LiteralPath (Join-Path $CandidateDirectory 'candidate.bin') -Destination $root
        $proof=Read-TrainJson (Join-Path $CandidateDirectory 'offline.json')
        Assert-TrainHash (Join-Path $root 'candidate.bin') $proof.modelShaAfter.ToUpperInvariant()
        $client=[IO.File]::ReadAllText((Join-Path $HistoryRoot 'sql\admin.client.ini')).Replace('port=33357','port=33359')
        Write-TrainText (Join-Path $layout.Sql 'admin.client.ini') $client
        $database=Join-Path $layout.Game 'config\Database.ini';Write-TrainText $database ([IO.File]::ReadAllText($database).Replace('127.0.0.1:33357/','127.0.0.1:33359/'))
        $server=Join-Path $layout.Game 'config\Server.ini';$body=[IO.File]::ReadAllText($server)
        $body=[regex]::Replace($body,'(?m)^GameserverPort\s*=.*$','GameserverPort = 18019');$body=[regex]::Replace($body,'(?m)^LoginPort\s*=.*$','LoginPort = 19319');Write-TrainText $server $body
        $config=Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini';Write-TrainText $config (Get-TrainColdBaseline ([IO.File]::ReadAllText($config)))
        $cohort=Join-Path $HistoryRoot 'autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001'
        $selected=$null
        foreach($work in @(Get-ChildItem -LiteralPath $cohort -Directory|Where-Object Name -Match '^[a-f0-9]{64}$'|Sort-Object Name)){
            $result=Read-TrainJson (Join-Path $work.FullName 'result.json')
            if($result.scope -ceq 'SOLO' -and $result.classId -eq 110 -and $result.outcome -ceq 'COMPLETED' -and $result.paidRawEffects -ge 2){$selected=$work;break}
        }
        if(!$selected){throw 'TRAIN019_NO_LIVING_SAVED_SOLO'}
        Copy-Item -LiteralPath (Join-Path $selected.FullName 'roster.tsv') -Destination $root
        $nonce=[guid]::NewGuid().ToString('N');$options=Join-Path $layout.Sql ('owned-server.'+$nonce+'.ini')
        Write-TrainText $options ((@('[mysqld]','bind-address=127.0.0.1','port=33359',('datadir="'+(Join-Path $layout.Sql 'data').Replace('\','/')+'"'),'default-time-zone=+00:00')) -join "`n")
        $sqlProcess=Start-Process -FilePath $layout.Daemon -ArgumentList @(('"--defaults-file='+$options+'"'),'--console') -WorkingDirectory $layout.Sql -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'sql.out') -RedirectStandardError (Join-Path $root 'sql.err')
        $null=Hold-ProcessHandle $sqlProcess;$sql=New-TrainProcessRecord $sqlProcess $layout.Daemon $nonce Sql $options
        Write-TrainJson (Join-Path $layout.Sql 'owned-process.json') $sql
        $ready=[DateTime]::UtcNow.AddSeconds(30)
        while($true){
            & $layout.Admin ('--defaults-file='+(Join-Path $layout.Sql 'admin.client.ini')) --protocol=tcp ping 1>$null 2>$null
            if($LASTEXITCODE -eq 0){break};if($sqlProcess.HasExited -or [DateTime]::UtcNow -ge $ready){throw 'TRAIN019_SQL_NOT_READY'};Start-Sleep -Milliseconds 250
        }
        Assert-TrainSqlOptions $layout $values $sql;Assert-TrainProcess $sql -Kind Sql -Port 33359
        $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
        $classPath=(Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $layout.Game 'libs\*')
        $jarSha=(Get-FileHash -LiteralPath (Join-Path $layout.Game 'libs\GameServer.jar')).Hash.ToLowerInvariant()
        $args019=Get-TrainJavaArguments 'org.l2jmobius.tests.phantoms.PhantomLearning019ActionValue' @('native',$root,$jarSha,$proof.modelShaAfter) $classPath $nonce 2 4096
        $null=[IO.Directory]::CreateDirectory((Join-Path $root ('attempts\'+$nonce)))
        $clock=[Diagnostics.Stopwatch]::StartNew()
        $native=Start-Process -FilePath $java -ArgumentList $args019 -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'native.out') -RedirectStandardError (Join-Path $root 'native.err')
        $nativeHandle=Hold-ProcessHandle $native;$nativeRecord=New-TrainProcessRecord $native $java $nonce Native (Join-Path $root 'candidate.bin')
        Write-TrainJson (Join-Path $root 'native-process.json') $nativeRecord
        while(!$native.HasExited -and $clock.ElapsedMilliseconds -lt 460000){
            if($clock.ElapsedMilliseconds -ge 330000 -and !(Test-Path -LiteralPath (Join-Path $root 'stop.request'))){Write-TrainText (Join-Path $root 'stop.request') '019_BOUNDED_DRAIN'}
            Start-Sleep -Milliseconds 250
        }
        if(!$native.HasExited){Assert-TrainProcess $nativeRecord -Kind Native;$native.Kill();$null=$native.WaitForExit(10000);throw 'TRAIN019_NATIVE_DEADLINE'}
        $exit=Get-TrackedProcessExitCode $native $nativeHandle
        Write-TrainExitReceipt $layout $nativeRecord $native $nativeHandle
        Assert-TrainActionValue019Proof $exit (Read-TrainJson (Join-Path $root 'native019.json')) $proof.modelShaAfter
    } catch {$failure=$_} finally {
        if($native -and !$native.HasExited){Assert-TrainProcess $nativeRecord -Kind Native;$native.Kill();$null=$native.WaitForExit(10000)}
        if(Test-Path -LiteralPath (Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini')){$config=Join-Path $layout.Game 'config\Custom\PhantomPlayers.ini';Write-TrainText $config (Get-TrainColdBaseline ([IO.File]::ReadAllText($config)))}
        if($sql -and !(Test-TrainProcessAbsent $sql)){Stop-TrainSql $layout $values $sql}
        if($clock){$clock.Stop()}
        $drift=@();foreach($path in $pins.Keys){if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $pins[$path]){$drift+=$path}}
        Assert-TrainPortsFree @(33357,18001,19301,33359,18019,19319)
        Write-TrainJson (Join-Path $root 'safety019.json') @{gsStarts=$(if($clock){1}else{0});nativeElapsedMillis=$(if($clock){$clock.ElapsedMilliseconds}else{0});preservedFiles=$pins.Count;historicalDrift=$drift.Count;test='OFF';ownedProcesses=0;portsFree=$true;nativeExit=$(if($native -and $native.HasExited){Get-TrackedProcessExitCode $native $nativeHandle}else{$null})}
        $lease.Dispose()
        if($drift.Count){throw 'TRAIN019_HISTORICAL_DRIFT'}
    }
    if($failure){throw $failure}
    Write-Output ('TRAIN019_NATIVE_CLOSED root='+$root)
}
function Hold-ProcessHandle([Diagnostics.Process]$Process) {
    if ($null -eq $Process) { throw 'OWN108R3C_PROCESS_REQUIRED' }
    # Start-Process redirection on Windows PowerShell 5.1 can lose ExitCode unless
    # this Process owns its cached handle before waiting (PowerShell issue 5421).
    $handle = $Process.Handle
    if ($null -eq $handle -or $handle -isnot [IntPtr] -or $handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_PROCESS_HANDLE_UNAVAILABLE' }
    return $handle
}
function Assert-ObservedExitCode([object]$Value) {
    # Do not cast: PowerShell converts a null value to integer zero.
    if ($null -eq $Value -or $Value -isnot [int]) { throw 'OWN108R3C_EXIT_STATUS_UNKNOWN' }
    return $Value
}
function Get-TrackedProcessExitCode([Diagnostics.Process]$Process, [object]$Handle) {
    if ($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_EXIT_HANDLE_UNKNOWN' }
    if (!$Process.HasExited) { throw 'OWN108R3C_PROCESS_NOT_EXITED' }
    return Assert-ObservedExitCode $Process.ExitCode
}
function Get-TrainHeldExitProof($Record,[Diagnostics.Process]$Process,[object]$Handle) {
    if($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero -or $Handle -ne $Process.Handle -or
       $Process.Id -ne $Record.pid -or $Process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$Record.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
    $exited=$Process.HasExited;$exit=$null
    if($exited){$exit=Get-TrackedProcessExitCode $Process $Handle}
    $terminated=$null;$elapsed=$null
    if($exited){$terminated=$Process.ExitTime.ToUniversalTime();$elapsed=[long][Math]::Ceiling(($terminated-$Process.StartTime.ToUniversalTime()).TotalMilliseconds);if($elapsed -lt 0){throw 'TRAIN010_NATIVE_TIME_UNKNOWN'}}
    return [ordered]@{pid=$Record.pid;created=$Record.created;exited=$exited;exitCode=$exit;terminatedUtc=$(if($exited){$terminated.ToString('o')}else{$null});nativeElapsedMillis=$elapsed}
}
function Test-TrainTerminatedCimRace([Management.Automation.ErrorRecord]$Failure,$Record,[Diagnostics.Process]$Process,[object]$Handle) {
    if($Failure.Exception.GetType().FullName -cne 'Microsoft.Management.Infrastructure.CimException' -or
       $Failure.FullyQualifiedErrorId -cne 'HRESULT 0x80041002,Microsoft.Management.Infrastructure.CimCmdlets.InvokeCimMethodCommand'){return $false}
    return (Get-TrainHeldExitProof $Record $Process $Handle).exited
}
function Get-TrainExitReceiptPath($Layout,$Record) {
    if([long]$Record.pid -le 0 -or [long]$Record.created -le 0 -or $Record.nonce -cnotmatch '^[0-9a-f]{32}$'){throw 'TRAIN002_EXIT_RECEIPT_IDENTITY'}
    switch -CaseSensitive ($Record.kind){
        'Worker' {return Join-Path $Layout.Root ('workers\'+$Record.nonce+'\exit.json')}
        'Native' {return Join-Path $Layout.Root ('attempts\'+$Record.nonce+'\exit.json')}
        'Sql' {return Join-Path $Layout.Sql ('exit.'+$Record.created+'.json')}
        'Director' {return Join-Path $Layout.Root ('commands\director-exit.'+$Record.created+'.json')}
        default {throw 'TRAIN002_EXIT_RECEIPT_KIND'}
    }
}
function Write-TrainExitReceipt($Layout,$Record,[Diagnostics.Process]$Process,[object]$Handle,[string]$Class='') {
    Assert-TrainInstallation $Layout
    $held=Get-TrainHeldExitProof $Record $Process $Handle
    if(!$held.exited){throw 'TRAIN002_EXIT_RECEIPT_PROCESS_LIVE'}
    Assert-TrainHash $Record.image $Record.imageHash
    if($Record.configPath){Assert-TrainHash $Record.configPath $Record.configHash}
    $receiptPath=Get-TrainExitReceiptPath $Layout $Record
    if(Test-Path -LiteralPath $receiptPath){if(!(Test-TrainExitReceipt $Layout $Record)){throw 'TRAIN010_EXIT_RECEIPT_CHANGED'};return}
    $fact=[ordered]@{schemaVersion=1;proof='OWN_HELD_HANDLE_EXIT';installationOwner=[IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt'));exitCode=$held.exitCode;finishedUtc=[DateTime]::UtcNow.ToString('o');terminatedUtc=$held.terminatedUtc;nativeElapsedMillis=$held.nativeElapsedMillis}
    foreach($key in @('pid','created','image','imageHash','sid','nonce','kind','commandHash','configPath','configHash')){$fact[$key]=$Record.$key}
    if($Class){$fact['class']=$Class}
    if($Record.kind -ceq 'Native'){$fact['attemptId']=$Record.nonce}
    Write-TrainJson (Get-TrainExitReceiptPath $Layout $Record) $fact
}
function Test-TrainExitReceipt($Layout,$Record) {
    Assert-TrainInstallation $Layout
    $path=Get-TrainExitReceiptPath $Layout $Record
    if(!(Test-Path -LiteralPath $path)){return $false}
    $fact=Read-TrainJson $path
    foreach($key in @('schemaVersion','proof','installationOwner','exitCode')){if(!$fact.PSObject.Properties[$key]){return $false}}
    if($fact.schemaVersion -ne 1 -or $fact.proof -cne 'OWN_HELD_HANDLE_EXIT' -or $fact.installationOwner -cne [IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt')) -or $fact.exitCode -isnot [int]){return $false}
    foreach($key in @('pid','created','image','imageHash','sid','nonce','kind','commandHash','configPath','configHash')){
        if(!$fact.PSObject.Properties[$key] -or [string]$fact.$key -cne [string]$Record.$key){return $false}
    }
    $hasTime=$null -ne $fact.PSObject.Properties['terminatedUtc'] -or $null -ne $fact.PSObject.Properties['nativeElapsedMillis']
    if($hasTime){
        if($null -eq $fact.PSObject.Properties['terminatedUtc'] -or $null -eq $fact.PSObject.Properties['nativeElapsedMillis'] -or
           ($fact.nativeElapsedMillis -isnot [int] -and $fact.nativeElapsedMillis -isnot [long]) -or [long]$fact.nativeElapsedMillis -lt 0){return $false}
        $terminated=[DateTime]::MinValue
        if(![DateTime]::TryParseExact([string]$fact.terminatedUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind,[ref]$terminated) -or $terminated.Kind -ne [DateTimeKind]::Utc){return $false}
        try{$started=[DateTime]::FromFileTimeUtc([long]$Record.created)}catch{return $false}
        if($terminated -lt $started -or [long]$fact.nativeElapsedMillis -ne [long][Math]::Ceiling(($terminated-$started).TotalMilliseconds)){return $false}
    }
    return $true
}
function Initialize-TrainWin32 {
    if($script:TrainWin32Initialized){return}
    $source=Join-Path $PSScriptRoot '..\Win32SqlIdentity.cs'
    $source=[IO.Path]::GetFullPath($source);Assert-G0PhysicalFile $source
    if (!('Win32SqlIdentity' -as [type])) { Add-Type -Path $source }
    if ([Win32SqlIdentity]::CurrentTokenElevated() -or [Win32SqlIdentity]::CurrentDebugEnabled() -or [Win32SqlIdentity]::CurrentIntegrityRid() -ge 12288) { throw 'TRAIN002_ORDINARY_TOKEN_REQUIRED' }
    $uac=Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System' -Name EnableLUA
    if($uac.EnableLUA -ne 1){throw 'TRAIN002_UAC_REQUIRED'}
    $script:TrainWin32Initialized=$true
}
function Get-TrainLayout([string]$Root) {
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..')).TrimEnd('\')
    $clone=Split-Path $module -Parent
    $expected=Join-Path $clone '.phantom-local\learning002\train'
    if (!$Root) { $Root=$expected }
    if (![IO.Path]::IsPathRooted($Root) -or [IO.Path]::GetFullPath($Root).TrimEnd('\') -ine $expected) { throw 'TRAIN002_EXACT_OWN_ROOT_REQUIRED' }
    $bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
    return @{Module=$module;Clone=$clone;Root=$expected;ExpectedRoot=$expected;Game=(Join-Path $expected 'game');Sql=(Join-Path $expected 'sql');
        ClassPath=((Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $expected 'game\libs\*'));
        Daemon=(Join-Path $bin 'mariadbd.exe');Install=(Join-Path $bin 'mariadb-install-db.exe');Admin=(Join-Path $bin 'mariadb-admin.exe');
        PowerShell=(Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe')}
}
function Assert-TrainDirectory([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    if (!$item.PSIsContainer -or $item.FullName -ine [IO.Path]::GetFullPath($Path)) { throw 'TRAIN002_PHYSICAL_DIRECTORY_REQUIRED' }
    for($part=$item;$null -ne $part;$part=$part.Parent) { if($part.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TRAIN002_REPARSE_PATH'} }
}
function Assert-TrainPrivate([string]$Path) {
    Assert-TrainDirectory $Path
    $acl=Get-Acl -LiteralPath $Path
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'TRAIN002_PRIVATE_OWNER_REQUIRED'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'TRAIN002_FOREIGN_ACL'}}
}
function New-TrainPrivateRoot($Layout) {
    if(Test-Path -LiteralPath $Layout.Root){throw 'TRAIN002_FRESH_ROOT_REQUIRED'}
    $parent=Split-Path $Layout.Root -Parent
    $existing=$parent
    while(!(Test-Path -LiteralPath $existing)){ $existing=Split-Path $existing -Parent }
    Assert-TrainDirectory $existing
    $null=[IO.Directory]::CreateDirectory($parent)
    Assert-TrainDirectory $parent
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetOwner($sid);$acl.SetAccessRuleProtection($true,$false)
    foreach($trustee in @($sid.Value,'S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    $null=[IO.Directory]::CreateDirectory($Layout.Root,$acl)
    Assert-TrainPrivate $Layout.Root
    Write-TrainText (Join-Path $Layout.Root 'owner.txt') ('LEARNING002:'+([guid]::NewGuid().ToString('N')))
}
function Repair-TrainEmptyBootstrap($Layout,$Config) {
    # Actual START supplies the pinned Get-TrainLayout result under its initialization
    # mutex. The expected path is kept separate so unit fixtures can exercise the
    # same guard with their own explicitly bounded temporary installation.
    if(!$Layout.ContainsKey('ExpectedRoot') -or ![IO.Path]::IsPathRooted($Layout.Root) -or ![IO.Path]::IsPathRooted($Layout.ExpectedRoot) -or
       [IO.Path]::GetFullPath($Layout.Root).TrimEnd('\') -ine $Layout.ExpectedRoot -or $Layout.Root -ine $Layout.ExpectedRoot){throw 'TRAIN002_BOOTSTRAP_RECOVERY_SCOPE'}
    Assert-TrainPrivate $Layout.Root
    if(@(Get-ChildItem -LiteralPath $Layout.Root -Force).Count -ne 0){throw 'TRAIN002_BOOTSTRAP_RECOVERY_NONEMPTY'}
    Assert-TrainPortsFree @([int]$Config.sqlPort,[int]$Config.gamePort,[int]$Config.loginPort)
    $parent=Split-Path $Layout.ExpectedRoot -Parent;Assert-TrainDirectory $parent
    $archive=Join-Path $parent ((Split-Path $Layout.ExpectedRoot -Leaf)+'.bootstrap-incomplete.'+[guid]::NewGuid().ToString('N'))
    $archive=[IO.Path]::GetFullPath($archive)
    if((Split-Path $archive -Parent) -ine $parent -or !$archive.StartsWith($Layout.ExpectedRoot+'.bootstrap-incomplete.',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $archive)){throw 'TRAIN002_BOOTSTRAP_ARCHIVE_SCOPE'}
    [IO.Directory]::Move($Layout.Root,$archive)
    Assert-TrainPrivate $archive
    if(@(Get-ChildItem -LiteralPath $archive -Force).Count -ne 0){throw 'TRAIN002_BOOTSTRAP_ARCHIVE_CHANGED'}
    return $archive
}
function Write-TrainText([string]$Path,[string]$Text) {
    if(Test-Path -LiteralPath $Path){Assert-G0PhysicalFile $Path}
    Assert-TrainDirectory (Split-Path $Path -Parent)
    $pending=$Path+'.'+[guid]::NewGuid().ToString('N')+'.pending'
    try {
        [IO.File]::WriteAllText($pending,$Text,$script:TrainUtf8)
        if(Test-Path -LiteralPath $Path){[IO.File]::Replace($pending,$Path,[NullString]::Value)}else{[IO.File]::Move($pending,$Path)}
    }finally{if(Test-Path -LiteralPath $pending){Remove-Item -LiteralPath $pending}}
}
function Write-TrainJson([string]$Path,$Value){Write-TrainText $Path ($Value|ConvertTo-Json -Depth 12)}
function Read-TrainJson([string]$Path){Assert-G0PhysicalFile $Path;return ConvertFrom-Json ([IO.File]::ReadAllText($Path))}
function Get-TrainSafeErrorRecord([Management.Automation.ErrorRecord]$Failure) {
    # Deliberately exclude Message, Line, PositionMessage, Definition and arguments:
    # they can contain expanded installer/JDBC credentials at later startup phases.
    $command=$null
    if($Failure.Exception -is [Management.Automation.CommandNotFoundException]){$command=$Failure.Exception.CommandName}
    $scriptName=$null;$line=$null
    if($Failure.InvocationInfo){$scriptName=$Failure.InvocationInfo.ScriptName;$line=$Failure.InvocationInfo.ScriptLineNumber}
    $code=$null
    if($Failure.Exception.Message -cmatch '^(?:TRAIN002|TRAIN017|OWN108R3C)_[A-Z0-9_]+(?::[A-Za-z0-9_.-]+)?$'){$code=$Failure.Exception.Message}
    $diagnostic=[ordered]@{event='TRAIN002_SAFE_ERROR';exceptionType=$Failure.Exception.GetType().FullName;code=$code;commandName=$command;scriptName=$scriptName;scriptLineNumber=$line;fullyQualifiedErrorId=$Failure.FullyQualifiedErrorId;pid=$null;nativeError=$null;created=$null}
    foreach($key in @('pid','nativeError','created')){if($Failure.Exception.Data.Contains($key)){$diagnostic[$key]=[long]$Failure.Exception.Data[$key]}}
    return $diagnostic
}
function Write-TrainSafeError([Management.Automation.ErrorRecord]$Failure) {
    [Console]::Error.WriteLine(((Get-TrainSafeErrorRecord $Failure)|ConvertTo-Json -Compress))
}
function Save-TrainFailure($Layout,[Collections.IDictionary]$State,[Management.Automation.ErrorRecord]$Failure,[ValidateSet('PRIMARY','CLEANUP','WORKER','NATIVE_RETRY','CONTROL')][string]$Stage) {
    Assert-TrainInstallation $Layout
    $directory=Join-Path $Layout.Root 'failures';$null=[IO.Directory]::CreateDirectory($directory)
    $record=Get-TrainSafeErrorRecord $Failure
    $record['id']=[guid]::NewGuid().ToString('N');$record['stage']=$Stage;$record['observedUtc']=[DateTime]::UtcNow.ToString('o')
    $record['phase']=$null;$record['runId']=$null;$record['nonce']=$null
    if($State){$record.phase=$State['phase'];$record.runId=$State['runId'];$record.nonce=$State['nonce']}
    Write-TrainJson (Join-Path $directory ($record.id+'.json')) $record
    if($State){
        if($Stage -eq 'PRIMARY' -and !$State['primaryFailureId']){$State['primaryFailureId']=$record.id;$State['primaryFailureCode']=$(if($record.code){$record.code}else{$record.exceptionType});$State['primaryFailurePhase']=$record.phase}
        if($Stage -eq 'CLEANUP'){$State['cleanupFailureId']=$record.id}
    }
    return $record.id
}
function Save-TrainProcessObservation($Record,[Nullable[int]]$NativeError,[string]$Proof,[Nullable[int]]$ExitCode=$null,[string]$CimErrorId='') {
    $fact=[ordered]@{event='TRAIN002_PROCESS_QUERY';pid=$Record.pid;created=$Record.created;kind=$Record.kind;nonce=$Record.nonce;nativeError=$NativeError;cimErrorId=$CimErrorId;proof=$Proof;exitCode=$ExitCode;observedUtc=[DateTime]::UtcNow.ToString('o')}
    # Hidden Windows hosts may have no useful stdout/stderr. Keep a durable fact
    # only on an unusual query/exit transition, never on every heartbeat.
    $layout=Get-TrainLayout
    Assert-TrainInstallation $layout
    $directory=Join-Path $layout.Root 'process-observations';$null=[IO.Directory]::CreateDirectory($directory)
    Write-TrainJson (Join-Path $directory ([guid]::NewGuid().ToString('N')+'.json')) $fact
}
function Read-TrainConfig([string]$Path) {
    Assert-G0PhysicalFile $Path
    $values=@{}
    foreach($line in [IO.File]::ReadAllLines($Path,[Text.Encoding]::UTF8)){
        if(!$line.Trim() -or $line.TrimStart().StartsWith('#')){continue}
        if($line -cnotmatch '^([A-Za-z][A-Za-z0-9]*)=(.*)$' -or $values.ContainsKey($Matches[1])){throw 'TRAIN002_CONFIG_SYNTAX_OR_DUPLICATE'}
        $values[$Matches[1]]=$Matches[2].Trim()
    }
    Assert-TrainConfig $values
    return $values
}
function Assert-TrainAutonomousConfig($Values) {
    $loop013=$Values.ContainsKey('autonomous013Version') -and $Values.autonomous013Version -ceq '1'
    $incremental016=$Values.ContainsKey('autonomous016Version') -and $Values.autonomous016Version -ceq '1'
    if($Values.ContainsKey('autonomous016Version') -and $Values.autonomous016Version -cnotin @('0','1')){throw 'TRAIN016_PROTOCOL_VERSION'}
    if($Values.ContainsKey('autonomous013Version') -and $Values.autonomous013Version -cnotin @('0','1')){throw 'TRAIN013_PROTOCOL_VERSION'}
    foreach($entry in @{autonomous009Enabled='false';autonomous009Exploration='true';autonomous009AcceptanceSmoke='false';autonomous009Phase='OFF';autonomous009MinLevel='1';autonomous009MaxLevel='85';autonomous009MemoryBudgetMb='8192';autonomous009DurationMinutes='1440';autonomous009CheckpointMinutes='15';autonomous009ReplayCapacity='128';autonomous009EvaluationEveryCycles='1';autonomous009StagnationLimit='3';autonomous009NoDataLimit='3';autonomous009CycleLimit='0';autonomous009SourceCursor='0';autonomous009ClassWeights='auto';autonomous009Families='DAMAGE,HEAL';autonomous009SmokeMaxStarts='6';autonomous009SmokeMaxNativeMinutes='90'}.GetEnumerator()){
        if(!$Values.ContainsKey($entry.Key)){$Values[$entry.Key]=$entry.Value}
    }
    $bounds=@{autonomous009Version=@(1,1);autonomous009MinLevel=@(1,85);autonomous009MaxLevel=@(1,85);autonomous009MemoryBudgetMb=@(1536,131072);autonomous009DurationMinutes=@(1,10080);autonomous009CheckpointMinutes=@(1,1440);autonomous009ReplayCapacity=@(48,512);autonomous009EvaluationEveryCycles=@(1,100);autonomous009StagnationLimit=@(3,3);autonomous009NoDataLimit=@(1,16);autonomous009CycleLimit=@(0,1000000);autonomous009SourceCursor=@(0,[long]::MaxValue)}
    foreach($key in $bounds.Keys){$number=0L;if(![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $bounds[$key][0] -or $number -gt $bounds[$key][1]){throw ('TRAIN009_CONFIG_RANGE:'+ $key)}}
    if(!$Values.ContainsKey('autonomous009EvaluationOnly')){$Values.autonomous009EvaluationOnly='false'}
    if($Values.autonomous009EvaluationOnly -cnotin @('true','false')){throw 'TRAIN010_EVAL_BOOLEAN'}
    # Same 120-second request bound as PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS; fail before GS.
    if($Values.autonomous009EvaluationOnly -ceq 'true' -and [long]$Values.episodeSeconds -gt 120){throw 'TRAIN010_EVALUATION_EPISODE_NATIVE_BOUND'}
    if($Values.autonomous009EvaluationOnly -ceq 'true' -and ($Values.autonomous009Phase -cnotin @('OFF','EVAL') -or $Values.autonomous009Exploration -cne 'false' -or $Values.autonomous009AcceptanceSmoke -cne 'true' -or [int]$Values.autonomous009EvaluationEveryCycles -ne 1)){throw 'TRAIN010_EVALUATION_ONLY_SCOPE'}
    if(!$Values.ContainsKey('autonomous009GuiHoldBeforeNative')){$Values.autonomous009GuiHoldBeforeNative='false'}
    foreach($key in @('autonomous009Enabled','autonomous009Exploration','autonomous009AcceptanceSmoke','autonomous009GuiHoldBeforeNative')){if($Values[$key] -cnotin @('true','false')){throw ('TRAIN009_CONFIG_BOOLEAN:'+ $key)}}
    if($Values.autonomous009GuiHoldBeforeNative -ceq 'true' -and $Values.autonomous009AcceptanceSmoke -cne 'true'){throw 'TRAIN009_GUI_HOLD_REQUIRES_ACCEPTANCE'}
    if($Values['autonomous009Comparator'] -cne 'STOCK_ON_FROZEN_SHORTLIST_V1' -or $Values['v3Enabled'] -cne 'true' -or $Values.autonomous009Phase -cnotin @('OFF','PREPARE','COLLECT','FIT','EVAL')){throw 'TRAIN009_CONFIG_VERSION_OR_PHASE'}
    if($Values.autonomous009Enabled -ceq 'true'){
        if($Values['configProfile'] -cne 'OUTCOME006' -or $Values['outcome006Enabled'] -cne 'true' -or $Values['v3Terminal006'] -cne 'true' -or
           $Values['exploration007Enabled'] -ceq 'true' -or $Values['exploration008Enabled'] -ceq 'true' -or $Values.autonomous009Phase -ceq 'OFF' -or
           $Values['v3DatasetContextPrefix'] -cne 'autonomous009|' -or $Values['autonomous009Campaign'] -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$' -or
           $Values['outcome006DatasetId'] -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$'){throw 'TRAIN009_CONFIG_AUTHORITY'}
        if($Values.autonomous009Phase -cin @('COLLECT','FIT','EVAL')){
            $version=0L
            if(![long]::TryParse([string]$Values['autonomous009ModelVersion'],[ref]$version) -or $version -lt 1 -or $Values['autonomous009ModelSha'] -cnotmatch '^[0-9a-f]{64}$'){throw 'TRAIN009_MODEL_PIN_REQUIRED'}
        }
    }
    if([int]$Values.autonomous009MinLevel -gt [int]$Values.autonomous009MaxLevel){throw 'TRAIN009_LEVEL_INTERVAL'}
    if([int]$Values.autonomous009MemoryBudgetMb -lt ([int]$Values.maxHeapMb+512)){throw 'TRAIN009_MEMORY_HEADROOM'}
    if($incremental016){
        if(!$loop013 -or $Values.episodesPerBatch -cne '51' -or $Values.autonomous009EvaluationOnly -cne 'false' -or $Values.autonomous009Phase -ceq 'EVAL' -or
           ($Values.ContainsKey('evaluationOutcomeVersion') -and $Values.evaluationOutcomeVersion -cne '1') -or $Values.autonomous009CycleLimit -cne '1' -or
           $Values.autonomous009Campaign -cnotin @('autonomous009-learning016-v1','autonomous009-learning016-g2-v1') -or $Values.outcome006DatasetId -cne ($Values.autonomous009Campaign+'-c000001') -or
           $Values.autonomous009AcceptanceSmoke -cne 'true'){throw 'TRAIN016_FIXED_51_TRAIN_ONLY_CAMPAIGN_REQUIRED'}
        $starts016=0;$minutes016=0
        if(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts016) -or $starts016 -lt 1 -or $starts016 -gt 3 -or
           ![int]::TryParse([string]$Values.autonomous009SmokeMaxNativeMinutes,[ref]$minutes016) -or $minutes016 -lt 1 -or $minutes016 -gt 75){throw 'TRAIN016_NATIVE_BUDGET'}
    }
    if([int]$Values.episodesPerBatch -lt 48 -or (!$incremental016 -and [int]$Values.episodesPerBatch % 12 -ne 0) -or [int]$Values.episodesPerBatch -gt [int]$Values.autonomous009ReplayCapacity){throw 'TRAIN009_COHORT_CAPACITY'}
    foreach($bound in @(@('autonomous009PriorStarts',10L),@('autonomous009PriorNativeMillis',5400000L))){if($Values.ContainsKey($bound[0])){$value=0L;if(![long]::TryParse([string]$Values[$bound[0]],[ref]$value) -or $value -lt 0 -or $value -gt $bound[1]){throw 'TRAIN009_PRIOR_BUDGET_INVALID'}}}
    $families=@($Values.autonomous009Families.Split(','))
    if(!$families.Count -or @($families|Select-Object -Unique).Count -ne $families.Count -or @($families|Where-Object {$_ -cnotin @('DAMAGE','HEAL')}).Count){throw 'TRAIN009_ACTION_FAMILIES'}
    if($Values.autonomous009ClassWeights -cne 'auto'){
        $seen=@{}
        foreach($entry in $Values.autonomous009ClassWeights.Split(',')){
            if($entry -cnotmatch '^([0-9]{1,3}):([0-9]{1,4})$'){throw 'TRAIN009_CLASS_WEIGHT_SYNTAX'}
            $classId=[int]$Matches[1];$weight=[int]$Matches[2]
            if($classId -gt 200 -or $weight -lt 1 -or $weight -gt 1000 -or $seen.ContainsKey($classId)){throw 'TRAIN009_CLASS_WEIGHT_RANGE'}
            $seen[$classId]=$true
        }
        # Actual PlayerClass membership is validated by Java; no second manual class catalog in PowerShell.
    }
    if($Values.autonomous009AcceptanceSmoke -ceq 'true'){
        $starts=0
        if($Values.autonomous009EvaluationOnly -ceq 'true'){
            $minutes=0
            if(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts) -or $starts -lt 1 -or $starts -gt 5 -or ![int]::TryParse([string]$Values.autonomous009SmokeMaxNativeMinutes,[ref]$minutes) -or $minutes -lt 1 -or $minutes -gt 45){throw 'TRAIN010_EVALUATION_BUDGET'}
        }elseif($loop013){
            $minutes=0
            if(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts) -or $starts -lt 1 -or $starts -gt 4 -or ![int]::TryParse([string]$Values.autonomous009SmokeMaxNativeMinutes,[ref]$minutes) -or $minutes -lt 1 -or $minutes -gt 80){throw 'TRAIN013_ACCEPTANCE_BUDGET'}
        }elseif(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts) -or $starts -lt 6 -or $starts -gt 10 -or $Values.autonomous009SmokeMaxNativeMinutes -cne '90'){throw 'TRAIN009_ACCEPTANCE_BUDGET_FIXED'}
    }
}
function Get-TrainNativeBudget013([string]$Path,[int]$Goal=13) {
    if($Goal -notin @(13,16)){throw 'TRAIN016_UNKNOWN_BUDGET_GOAL'}
    $maximumStarts=if($Goal -eq 16){3}else{4};$maximumMillis=if($Goal -eq 16){4500000L}else{4800000L};$goalName='PHANTOM-LEARNING-'+('{0:D3}' -f $Goal)
    if(!(Test-Path -LiteralPath $Path)){
        if(Test-Path -LiteralPath ($Path+'.issued.json')){throw 'TRAIN013_GLOBAL_BUDGET_MISSING_NO_RESET'}
        Write-TrainJson ($Path+'.issued.json') @{schemaVersion=$Goal;goal=$goalName;maximumStarts=$maximumStarts;maximumNativeMillis=$maximumMillis}
        Write-TrainJson $Path @{schemaVersion=$Goal;goal=$goalName;maximumStarts=$maximumStarts;maximumNativeMillis=$maximumMillis;starts=0;nativeMillis=0L;pending='';reservations=@()}
    }
    $budget=Read-TrainJson $Path
    if($budget.schemaVersion -ne $Goal -or $budget.goal -cne $goalName -or $budget.maximumStarts -ne $maximumStarts -or [long]$budget.maximumNativeMillis -ne $maximumMillis){throw 'TRAIN013_GLOBAL_BUDGET_PIN_CHANGED'}
    $rows=@($budget.reservations);$sum=0L
    if($rows.Count -gt $maximumStarts -or $budget.starts -ne $rows.Count -or @($rows|Group-Object attemptId|Where-Object {$_.Count -ne 1}).Count){throw 'TRAIN013_GLOBAL_RESERVATIONS_CHANGED'}
    foreach($row in $rows){
        if($row.attemptId -cnotmatch '^[a-f0-9]{32}$' -or $row.scope -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$'){throw 'TRAIN013_GLOBAL_RESERVATION_IDENTITY'}
        if($null -ne $row.chargedMillis){if([long]$row.chargedMillis -lt 0 -or $row.proof -cne 'OWN_HELD_HANDLE_EXIT_TIME'){throw 'TRAIN013_GLOBAL_CHARGE_UNPROVEN'};$sum+=[long]$row.chargedMillis}
        elseif($budget.pending -cne $row.attemptId){throw 'TRAIN013_GLOBAL_UNCHARGED_RESERVATION'}
    }
    if([long]$budget.nativeMillis -ne $sum -or ($budget.pending -and @($rows|Where-Object {$_.attemptId -ceq $budget.pending -and $null -eq $_.chargedMillis}).Count -ne 1)){throw 'TRAIN013_GLOBAL_CHARGE_SUM_CHANGED'}
    return $budget
}
function Reserve-TrainNativeBudget013([string]$Path,[string]$Scope,[bool]$Evaluation,[long]$DrainReserve,[int]$Goal=13) {
    if($Goal -eq 16 -and ($Evaluation -or $Scope -cnotin @('autonomous009-learning016-v1-c000001','autonomous009-learning016-g2-v1-c000001'))){throw 'TRAIN016_TRAIN_ONLY_BUDGET'}
    $budget=Get-TrainNativeBudget013 $Path $Goal
    if($budget.pending){throw 'TRAIN013_PENDING_NATIVE_REQUIRES_RECOVERY'}
    if($Scope -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$' -or $DrainReserve -lt 0){throw 'TRAIN013_RESERVATION_SCOPE'}
    if([int]$budget.starts -ge [int]$budget.maximumStarts -or [long]$budget.nativeMillis+$DrainReserve -ge [long]$budget.maximumNativeMillis){throw 'TRAIN013_RESOURCE_GLOBAL_NATIVE_BUDGET'}
    $scoped=@($budget.reservations|Where-Object {$_.scope -ceq $Scope});$scopeMillis=0L
    foreach($row in $scoped){if([bool]$row.evaluation -ne $Evaluation){throw 'TRAIN013_RESERVATION_MODE_CHANGED'};$scopeMillis+=[long]$row.chargedMillis}
    if($Evaluation -and ($scoped.Count -ge 2 -or $scopeMillis+$DrainReserve -ge 2100000L)){throw 'TRAIN013_RESOURCE_CHILD_NATIVE_BUDGET'}
    $id=[guid]::NewGuid().ToString('N')
    $row=[pscustomobject]@{attemptId=$id;scope=$Scope;evaluation=$Evaluation;startedUtc=[DateTime]::UtcNow.ToString('o');ordinal=([int]$budget.starts+1);priorNativeMillis=$budget.nativeMillis;chargedMillis=$null;proof=''}
    $budget.reservations=@($budget.reservations)+@($row);$budget.starts=[int]$budget.starts+1;$budget.pending=$id
    Write-TrainJson $Path $budget
    return Get-TrainNativeBudget013 $Path $Goal
}
function Complete-TrainNativeBudget013([string]$Path,[string]$Attempt,[long]$Millis,[string]$Proof,[int]$Goal=13) {
    $budget=Get-TrainNativeBudget013 $Path $Goal
    $rows=@($budget.reservations|Where-Object {$_.attemptId -ceq $Attempt})
    if($rows.Count -ne 1 -or $Millis -lt 0 -or $Proof -cne 'OWN_HELD_HANDLE_EXIT_TIME'){throw 'TRAIN013_NATIVE_CHARGE_IDENTITY_OR_PROOF'}
    $row=$rows[0]
    if($null -ne $row.chargedMillis){if([long]$row.chargedMillis -ne $Millis){throw 'TRAIN013_DUPLICATE_CHARGE_CHANGED'};return $budget}
    if($budget.pending -cne $Attempt){throw 'TRAIN013_NATIVE_CHARGE_RESERVATION_MISSING'}
    $row.chargedMillis=$Millis;$row.proof=$Proof;$budget.nativeMillis=[long]$budget.nativeMillis+$Millis;$budget.pending=''
    Write-TrainJson $Path $budget
    return Get-TrainNativeBudget013 $Path $Goal
}
function Assert-TrainEvaluationReceipt013($Receipt,[string]$CycleId,[long]$Version,[string]$ModelSha,[string]$SummaryPath,[string]$SummarySha) {
    if($Receipt.schemaVersion -ne 13 -or $Receipt.cycleId -cne $CycleId -or [long]$Receipt.modelVersion -ne $Version -or $Receipt.modelSha -cne $ModelSha){throw 'TRAIN013_EVALUATION_CYCLE_OR_MODEL_CHANGED'}
    Assert-TrainHash $SummaryPath $SummarySha;$saved=Read-TrainJson $SummaryPath
    if($saved.cycleId -cne $CycleId -or [long]$saved.modelVersion -ne $Version -or $saved.modelSha -cne $ModelSha -or
       ($saved.proofFiles|ConvertTo-Json -Compress -Depth 6) -cne ($Receipt.proofFiles|ConvertTo-Json -Compress -Depth 6)){throw 'TRAIN013_EVALUATION_SUMMARY_CHANGED'}
    Assert-TrainDirectory $Receipt.directory
    Assert-TrainHash (Join-Path $Receipt.directory 'evaluation-v2.complete') $Receipt.completeSha
    Assert-TrainHash (Join-Path $Receipt.directory 'evaluation-pairs-v2.json') $Receipt.pairsSha
    Assert-TrainHash (Join-Path $Receipt.directory 'protocol-v2.json') $Receipt.protocolSha
    $entries=@($Receipt.proofFiles.PSObject.Properties)
    if($entries.Count -lt 4 -or $entries.Count -gt 4096){throw 'TRAIN013_EVALUATION_RAW_CLOSURE'}
    foreach($name in @('protocol-v2.json','evaluation-v2.complete','evaluation-pairs-v2.json')){if(!$Receipt.proofFiles.PSObject.Properties[$name]){throw 'TRAIN013_EVALUATION_ROOT_PROOF_MISSING'}}
    $prefix=[IO.Path]::GetFullPath($Receipt.directory).TrimEnd('\')+'\'
    foreach($entry in $entries){
        if($entry.Name -cnotmatch '^[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?$' -or $entry.Name -cmatch '(^|/)\.\.?(/|$)' -or [string]$entry.Value -cnotmatch '^[a-fA-F0-9]{64}$'){throw 'TRAIN013_EVALUATION_RAW_PATH_OR_SHA'}
        $file=[IO.Path]::GetFullPath((Join-Path $Receipt.directory $entry.Name))
        if(!$file.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw 'TRAIN013_EVALUATION_RAW_SCOPE'}
        Assert-TrainHash $file ([string]$entry.Value)
    }
}
function Get-TrainNoFitCompletion016([string]$CampaignDirectory,$Journal) {
    Assert-TrainDirectory $CampaignDirectory
    if($Journal.schemaVersion -ne 13 -or [int]$Journal.cycle -ne 1 -or
       $Journal.campaign -cnotin @('autonomous009-learning016-v1','autonomous009-learning016-g2-v1') -or
       $Journal.dataset -cne ($Journal.campaign+'-c000001') -or (Split-Path $CampaignDirectory -Leaf) -cne $Journal.campaign -or
       $Journal.phase -cnotin @('FIT','DONE016') -or [int]$Journal.fits -ne 0 -or $Journal.pending -isnot [bool] -or $Journal.pending -or $Journal.pendingDecision -or
       [int]$Journal.completedCycles -ne $(if($Journal.phase -ceq 'DONE016'){1}else{0})) {throw 'TRAIN017_NO_FIT_JOURNAL'}
    $config=Read-TrainJson (Join-Path $CampaignDirectory 'base-config.json')
    if($config.autonomous016Version -cne '1' -or $config.autonomous013Version -cne '1' -or
       $config.autonomous009Campaign -cne $Journal.campaign -or $config.outcome006DatasetId -cne $Journal.dataset){throw 'TRAIN017_NO_FIT_CAMPAIGN'}
    $cycle=Join-Path $CampaignDirectory 'cycles\c000001'
    $checkpoint=Read-TrainJson (Join-Path $cycle 'checkpoint016.json')
    $reserved=Read-TrainJson (Join-Path $cycle 'fit-reserved016.json')
    $fit=Read-TrainJson (Join-Path $cycle 'fit-result.json')
    if($checkpoint.schemaVersion -ne 16 -or $checkpoint.cycle -cne $Journal.dataset -or [int]$checkpoint.fits -ne 0 -or
       $checkpoint.reason -cne 'NO_FIT_INSUFFICIENT_VALID_DATA' -or $checkpoint.independentEvaluation -isnot [bool] -or $checkpoint.independentEvaluation -or
       $checkpoint.productionPromotion -isnot [bool] -or $checkpoint.productionPromotion -or
       $reserved.schemaVersion -ne 16 -or $reserved.cycle -cne $Journal.dataset -or $reserved.trainingCredit -isnot [bool] -or $reserved.trainingCredit -or
       [long]$fit.candidateVersion -ne 0 -or $fit.productionPromotion -isnot [bool] -or $fit.productionPromotion -or
       $fit.metrics.usedForTraining -isnot [bool] -or $fit.metrics.usedForTraining -or $fit.metrics.objective -cne 'ACQUIRED_ACTION_ADVANTAGE_V2' -or
       $fit.reservationSha -cne (Get-FileHash -LiteralPath (Join-Path $cycle 'fit-reserved016.json')).Hash -or
       [long]$checkpoint.bestVersion -ne [long]$Journal.bestVersion -or $checkpoint.bestSha -cne $Journal.bestSha){throw 'TRAIN017_NO_FIT_CHECKPOINT'}
    $keys=@('schemaVersion','modelVersion','modelSha','optimizerSha','terminalWeightsSha','optimizerUpdates','completedEpochs','acceptedTestVersion','acceptedTestSha','featureSchemaSha','productionPromotion')
    foreach($key in $keys){
        $expected=$Journal.parentCheckpoint.$key
        if($null -eq $expected -or $checkpoint.parent.$key -cne $expected -or $checkpoint.selected.$key -cne $expected -or $reserved.parent.$key -cne $expected){throw 'TRAIN017_NO_FIT_LINEAGE'}
    }
    $files=[ordered]@{}
    foreach($relative in @('base-config.json','cycles\c000001\checkpoint016.json','cycles\c000001\fit-reserved016.json','cycles\c000001\fit-result.json')){
        $file=Join-Path $CampaignDirectory $relative;Assert-G0PhysicalFile $file;$files[$relative]=(Get-FileHash -LiteralPath $file).Hash
    }
    return [pscustomobject]@{schemaVersion=17;status='COMPLETED_NO_FIT';campaign=$Journal.campaign;dataset=$Journal.dataset;selected=$checkpoint.selected;files=$files;trainingCredit=$false;optimizerDelta=0;independentEvaluation=$false;productionPromotion=$false}
}
function Save-TrainCycleState013([string]$Path,$Journal) {
    if($Journal.schemaVersion -ne 13 -or [int]$Journal.cycle -lt 1 -or [int]$Journal.cycle -gt 1000000){throw 'TRAIN013_STATE_VERSION_OR_PHASE'}
    if($Journal.phase -ceq 'DONE016'){$null=Get-TrainNoFitCompletion016 (Split-Path $Path -Parent) $Journal}
    elseif($Journal.phase -cnotmatch '^[A-Z_]{1,32}$'){throw 'TRAIN013_STATE_VERSION_OR_PHASE'}
    $parent=Split-Path $Path -Parent;Assert-TrainDirectory $parent
    $events=Join-Path $parent ('cycles\c{0:D6}\phases' -f [int]$Journal.cycle);$null=[IO.Directory]::CreateDirectory($events);Assert-TrainDirectory $events
    $entered=Join-Path $events ($Journal.phase+'.entered.json')
    if(!(Test-Path -LiteralPath $entered)){Write-TrainJson $entered @{schemaVersion=13;cycle=$Journal.cycle;phase=$Journal.phase;dataset=$Journal.dataset;enteredUtc=[DateTime]::UtcNow.ToString('o');sourceSha=$Journal.sourceSha;jarSha=$Journal.jarSha}}
    if(Test-Path -LiteralPath $Path){
        $previous=Read-TrainJson $Path
        if($previous.phase -cne $Journal.phase -or [int]$previous.cycle -ne [int]$Journal.cycle){
            $previousEvents=Join-Path $parent ('cycles\c{0:D6}\phases' -f [int]$previous.cycle);$null=[IO.Directory]::CreateDirectory($previousEvents);Assert-TrainDirectory $previousEvents
            $exit=Join-Path $previousEvents ($previous.phase+'.exited.json')
            if(!(Test-Path -LiteralPath $exit)){Write-TrainJson $exit @{schemaVersion=13;cycle=$previous.cycle;phase=$previous.phase;nextPhase=$Journal.phase;exitedUtc=[DateTime]::UtcNow.ToString('o');completedCycles=$Journal.completedCycles;patience=$Journal.patience}}
        }
    }
    Write-TrainJson $Path $Journal
    Write-TrainJson (Join-Path (Split-Path $events -Parent) 'cycle-status.json') $Journal
}
function Write-TrainNoFitClosure017([string]$Directory,$Proof) {
    $directory017=[IO.Path]::GetFullPath($Directory).TrimEnd('\')
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$name='Local\PhantomTrainClosure017_'+([BitConverter]::ToString($sha.ComputeHash($script:TrainUtf8.GetBytes($directory017.ToUpperInvariant())))).Replace('-','')}finally{$sha.Dispose()}
    $mutex=[Threading.Mutex]::new($false,$name);$locked=$false
    try{
        try{$locked=$mutex.WaitOne(15000)}catch [Threading.AbandonedMutexException]{$locked=$true}
        if(!$locked){throw 'TRAIN017_REPAIR_PUBLICATION_BUSY'}
        return Write-TrainNoFitClosureLocked017 $directory017 $Proof
    }finally{if($locked){$mutex.ReleaseMutex()};$mutex.Dispose()}
}
function Write-TrainImmutableJson017([string]$Path,$Value) {
    Assert-TrainDirectory (Split-Path $Path -Parent)
    $pending=$Path+'.'+[guid]::NewGuid().ToString('N')+'.pending'
    try{
        $bytes=$script:TrainUtf8.GetBytes(($Value|ConvertTo-Json -Depth 12))
        $stream=[IO.FileStream]::new($pending,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
        # Move is create-only: even a publisher bypassing the mutex cannot replace history.
        [IO.File]::Move($pending,$Path)
    }finally{if(Test-Path -LiteralPath $pending){Remove-Item -LiteralPath $pending}}
}
function Write-TrainNoFitClosureLocked017([string]$Directory,$Proof) {
    if($Proof.schemaVersion -ne 17 -or $Proof.status -cne 'COMPLETED_NO_FIT' -or $Proof.trainingCredit -isnot [bool] -or $Proof.trainingCredit -or
       [long]$Proof.optimizerDelta -ne 0 -or $Proof.independentEvaluation -isnot [bool] -or $Proof.independentEvaluation -or
       $Proof.productionPromotion -isnot [bool] -or $Proof.productionPromotion){throw 'TRAIN017_REPAIR_PROOF'}
    Assert-TrainDirectory (Split-Path ([IO.Path]::GetFullPath($Directory)) -Parent)
    if(!(Test-Path -LiteralPath $Directory)){$null=[IO.Directory]::CreateDirectory($Directory)}
    Assert-TrainDirectory $Directory
    $text=$Proof|ConvertTo-Json -Depth 12 -Compress
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$proofSha=([BitConverter]::ToString($sha.ComputeHash($script:TrainUtf8.GetBytes($text)))).Replace('-','')}finally{$sha.Dispose()}
    $receiptPath=Join-Path $Directory 'closure.json';$markerPath=Join-Path $Directory 'complete.json'
    foreach($entry in Get-ChildItem -LiteralPath $Directory -Force){if($entry.PSIsContainer -or $entry.Name -cnotin @('closure.json','complete.json')){throw 'TRAIN017_REPAIR_UNKNOWN_PENDING'}}
    if(Test-Path -LiteralPath $receiptPath){
        $receipt=Read-TrainJson $receiptPath
        if($receipt.schemaVersion -ne 17 -or $receipt.status -cne 'COMPLETED_NO_FIT' -or $receipt.proofSha -cne $proofSha){throw 'TRAIN017_REPAIR_CHANGED'}
        $stored=$receipt.proof|ConvertTo-Json -Depth 12 -Compress
        if($stored -cne $text){throw 'TRAIN017_REPAIR_CHANGED'}
    }else{
        if(Test-Path -LiteralPath $markerPath){throw 'TRAIN017_REPAIR_MISSING_RECEIPT'}
        $receipt=[ordered]@{schemaVersion=17;status='COMPLETED_NO_FIT';proofSha=$proofSha;proof=$Proof;closedUtc=[DateTime]::UtcNow.ToString('o')}
        Write-TrainImmutableJson017 $receiptPath $receipt
    }
    $receiptSha=(Get-FileHash -LiteralPath $receiptPath).Hash
    if(Test-Path -LiteralPath $markerPath){
        $marker=Read-TrainJson $markerPath
        if(@($marker.PSObject.Properties).Count -ne 4 -or $marker.schemaVersion -ne 17 -or $marker.receiptSha -cne $receiptSha -or $marker.proofSha -cne $proofSha -or
           $marker.trainingCredit -isnot [bool] -or $marker.trainingCredit){throw 'TRAIN017_REPAIR_MARKER_CHANGED'}
    }else{Write-TrainImmutableJson017 $markerPath @{schemaVersion=17;receiptSha=$receiptSha;proofSha=$proofSha;trainingCredit=$false}}
    return Read-TrainJson $receiptPath
}
function Complete-TrainCycle013([string]$JournalPath,$Journal,[string]$CheckpointPath,[string]$CampaignDirectory) {
    $checkpoint=Read-TrainJson $CheckpointPath
    if($Journal.schemaVersion -ne 13 -or $checkpoint.schemaVersion -ne 13 -or $checkpoint.completed -ne $true){throw 'TRAIN013_CHECKPOINT_COMMIT_MARKER'}
    if($Journal.phase -ceq 'NEW' -and $Journal.lastCheckpoint -ceq $CheckpointPath){return $Journal}
    if($Journal.phase -cne 'CHECKPOINT' -or !$Journal.pendingDecision -or $checkpoint.cycleId -cne $Journal.dataset -or $Journal.pendingDecision.cycleId -cne $Journal.dataset -or [int]$checkpoint.patience -ne [int]$Journal.pendingDecision.patience){throw 'TRAIN013_CHECKPOINT_PENDING_DECISION_CHANGED'}
    $cycleDirectory=Split-Path $CheckpointPath -Parent
    foreach($name in @('fit-result.json','eval-v2.json','decision.json')){
        $sha=if($name -ceq 'fit-result.json'){$checkpoint.fitSha}elseif($name -ceq 'eval-v2.json'){$checkpoint.evalSha}else{$checkpoint.decisionSha}
        Assert-TrainHash (Join-Path $cycleDirectory $name) $sha
    }
    $decision=$Journal.pendingDecision
    if($decision.improved){
        $best=@{schemaVersion=13;modelVersion=$decision.bestVersion;modelSha=$decision.bestSha;authority='INDEPENDENT_MATCHED_SUPPORTED';evaluationSha=$checkpoint.evalSha;decisionSha=$checkpoint.decisionSha;productionPromotion=$false}
        Write-TrainJson (Join-Path $CampaignDirectory ('best-verified-'+$decision.bestVersion+'.json')) $best;Write-TrainJson (Join-Path $CampaignDirectory 'best-verified-test.json') $best
    }
    foreach($key in @('patience','creditedCycles','bestVersion','bestSha','bestDecision','improved')){$Journal.$key=$decision.$key}
    $Journal.reason=$decision.reason;$Journal.lastCheckpoint=$CheckpointPath;$Journal.completedCycles=[int]$Journal.completedCycles+1;$Journal.cycle=[int]$Journal.cycle+1;$Journal.phase='NEW';$Journal.dataset='';$Journal.pendingDecision=$null
    Write-TrainJson (Join-Path $CampaignDirectory 'next-plan.json') @{schemaVersion=13;cycle=$Journal.cycle;dataset=($Journal.campaign+('-c{0:D6}' -f [int]$Journal.cycle));sourceCursor=([int]$Journal.cycle-1);planned=$true;previousCheckpointSha=(Get-FileHash -LiteralPath $CheckpointPath).Hash;requiresCodexCommand=$false}
    Save-TrainCycleState013 $JournalPath $Journal
    return $Journal
}
function Get-TrainCycleDecision013($Receipt,$Prior) {
    foreach($key in @('schemaVersion','cycleId','fitCommitted','optimizerDelta','selectedEpoch','candidateVersion','candidateSha','evalValid','coverage','terminalPairs','paidDivergences','stockWins','learnedWins','stockDeaths','learnedDeaths','resourceComparable','resourceRegression','bestComparison','supportedGain','criticalRegression','reason')){
        if(!$Receipt.PSObject.Properties[$key]){throw ('TRAIN013_DECISION_FIELD_'+$key)}
    }
    if($Receipt.schemaVersion -ne 13 -or $Receipt.cycleId -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$' -or $Receipt.candidateSha -cnotmatch '^[a-f0-9]{64}$' -or
       [long]$Receipt.optimizerDelta -lt 0 -or [int]$Receipt.selectedEpoch -lt 0 -or [int]$Receipt.terminalPairs -lt 0 -or [int]$Prior.patience -lt 0){throw 'TRAIN013_DECISION_IDENTITY_OR_RANGE'}
    $credited=@($Prior.creditedCycles);if(@($credited|Select-Object -Unique).Count -ne $credited.Count -or $credited.Count -gt 1000000){throw 'TRAIN013_CYCLE_CREDIT_CORRUPT'}
    if($credited -ccontains $Receipt.cycleId){return $Prior}
    $result=[pscustomobject]@{schemaVersion=13;cycleId=$Receipt.cycleId;phase='NEXT';reason='';patience=[int]$Prior.patience;creditedCycles=$credited;bestVersion=[long]$Prior.bestVersion;bestSha=$Prior.bestSha;bestDecision='UNRESOLVED';improved=$false;confidence='INSUFFICIENT';gameVerdict='INCONCLUSIVE'}
    $reason=[string]$Receipt.reason
    if(!$reason){
        if(!$Receipt.fitCommitted -or [long]$Receipt.optimizerDelta -le 0){$reason='NO_DATA'}
        elseif(!$Receipt.coverage){$reason='EVAL_COVERAGE_GAP'}
        elseif(!$Receipt.evalValid){$reason='INVALID_EVAL'}
        elseif([int]$Receipt.terminalPairs -lt 4 -or !$Receipt.resourceComparable){$reason='EVAL_COVERAGE_GAP'}
    }
    if($reason){
        $phases=@{NO_DATA='INSUFFICIENT_EVIDENCE';EVAL_COVERAGE_GAP='PAUSED_EVAL_COVERAGE_GAP';INVALID_EVAL='FAILED_FAIL_CLOSED';FAILED_FAIL_CLOSED='FAILED_FAIL_CLOSED';RESOURCE_LIMIT='RESOURCE_LIMIT';PAUSED_BY_OWNER='PAUSED_BY_OWNER';STOPPED_BY_OWNER='STOPPED_BY_OWNER'}
        if(!$phases.ContainsKey($reason)){throw 'TRAIN013_UNKNOWN_STOP_REASON'}
        $result.phase=$phases[$reason];$result.reason=$reason;return $result
    }
    $result.creditedCycles=@($credited)+@([string]$Receipt.cycleId)
    # Neither a fitted loss nor candidate-vs-stock is evidence of candidate-vs-best.
    $gain=$Receipt.supportedGain -eq $true -and [int]$Receipt.selectedEpoch -gt 0 -and [int]$Receipt.paidDivergences -gt 0 -and
        [int]$Receipt.terminalPairs -ge 32 -and $Receipt.bestComparison -ceq 'INDEPENDENT_MATCHED_SUPPORTED' -and
        $Receipt.PSObject.Properties['bestEvidenceSha'] -and $Receipt.bestEvidenceSha -cmatch '^[a-f0-9]{64}$' -and
        $Receipt.PSObject.Properties['qualityLower95'] -and [double]$Receipt.qualityLower95 -gt 0 -and [double]$Receipt.qualityLower95 -le 1 -and
        [int]$Receipt.learnedDeaths -le [int]$Receipt.stockDeaths -and !$Receipt.resourceRegression -and !$Receipt.criticalRegression
    if($gain){$result.patience=0;$result.improved=$true;$result.bestVersion=[long]$Receipt.candidateVersion;$result.bestSha=$Receipt.candidateSha;$result.bestDecision='IMPROVEMENT_SUPPORTED';$result.reason='IMPROVEMENT_SUPPORTED';$result.confidence='SUPPORTED';$result.gameVerdict='ADVANTAGE_SUPPORTED'}
    else{
        $result.patience++;$result.reason='NO_SUPPORTED_GAIN_LOW_POWER'
        if([int]$Receipt.paidDivergences -eq 0){$result.gameVerdict='NO_POLICY_DIVERGENCE'}
        if($Receipt.criticalRegression -or $Receipt.resourceRegression -or ($Receipt.PSObject.Properties['regressionObserved'] -and $Receipt.regressionObserved)){$result.reason='REGRESSION_OBSERVED';$result.bestDecision='REJECTED';$result.gameVerdict='REGRESSION_OBSERVED'}
        if($Receipt.criticalRegression){$result.phase='PAUSED_REGRESSION_OBSERVED'}elseif($result.patience -ge 3){$result.phase='PAUSED_NO_MEASURABLE_PROGRESS'}
    }
    return $result
}
function Assert-TrainConfig($Values) {
    foreach($entry in @{maxTrainingCohorts=32;maxStoredActors=4096;maxStoredProfiles=4096;cpuBudgetPercent=75;maxAdaptiveIdleSeconds=300}.GetEnumerator()){
        if(!$Values.ContainsKey($entry.Key)){$Values[$entry.Key]=[string]$entry.Value}
    }
    foreach($entry in @{maxTrainingCohorts=@(1,256);maxStoredActors=@(32,32768);maxStoredProfiles=@(64,32768)}.GetEnumerator()){
        $number=0L
        if(![long]::TryParse([string]$Values[$entry.Key],[ref]$number) -or $number -lt $entry.Value[0] -or $number -gt $entry.Value[1]){throw ('TRAIN002_CONFIG_RANGE:'+ $entry.Key)}
    }
    $bounds=@{schemaVersion=@(1,1);scenarioLimit=@(3,64);maxActiveActors=@(2,9);episodesPerBatch=@(1,256);episodeSeconds=@(15,900);trainingThreshold=@(32,4096);retentionEpisodes=@(100,100000);cpuCount=@(1,16);maxHeapMb=@(1024,16384);sqlPort=@(33000,65535);gamePort=@(1024,65535);loginPort=@(1024,65535);startupTimeoutSeconds=@(30,900);shutdownTimeoutSeconds=@(30,900);batchTimeoutSeconds=@(60,86400);idleSeconds=@(1,3600);minFreeDiskMb=@(1024,1048576);modelVersion=@(0,2147483647)}
    foreach($key in $bounds.Keys){$number=0L;if(!$Values.ContainsKey($key) -or ![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $bounds[$key][0] -or $number -gt $bounds[$key][1]){throw ('TRAIN002_CONFIG_RANGE:'+ $key)}}
    $optional=@{preparationSeconds=@(15,600);maxExamples=@(32,4096);minAuditPerScope=@(1,2048);minimumImprovementBasisPoints=@(0,5000);evidenceFlushMillis=@(100,30000);maxLedgerEpisodes=@(100,10000000);maxLedgerEvents=@(4096,1000000);maxTrainingAttempts=@(1,4096);cpuBudgetPercent=@(1,100);maxAdaptiveIdleSeconds=@(1,3600)}
    foreach($key in $optional.Keys){if($Values.ContainsKey($key)){$number=0L;if(![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $optional[$key][0] -or $number -gt $optional[$key][1]){throw ('TRAIN002_CONFIG_RANGE:'+ $key)}}}
    $seed=0L;if(!$Values.ContainsKey('seed') -or ![long]::TryParse([string]$Values.seed,[ref]$seed)){throw 'TRAIN002_CONFIG_SEED'}
    foreach($key in @('classes','levels','grades','npcIds','modes','learningMode','modelSha')){if(!$Values.ContainsKey($key)){throw ('TRAIN002_CONFIG_REQUIRED:'+ $key)}}
    if($Values.ContainsKey('autonomous009Enabled') -and $Values.autonomous009Enabled -ceq 'true' -and !$Values.ContainsKey('autonomous009Version')){throw 'TRAIN009_CONFIG_VERSION_REQUIRED'}
    $autonomous=$Values.ContainsKey('autonomous009Version')
    foreach($key in @('classes','levels')){if(!($autonomous -and $Values[$key] -ceq 'auto') -and [string]$Values[$key] -cnotmatch '^[0-9]+(?:,[0-9]+)*$'){throw ('TRAIN002_CONFIG_LIST:'+ $key)}}
    if($Values.grades -cnotmatch '^(?:NONE|D|C|B|A|S)(?:,(?:NONE|D|C|B|A|S))*$' -or ($Values.npcIds -and $Values.npcIds -cnotmatch '^[0-9]+(?:,[0-9]+)*$')){throw 'TRAIN002_CONFIG_CATALOG'}
    if($Values.modes -cnotmatch '^(?:SOLO|PARTY)(?:,(?:SOLO|PARTY))*$' -or ($Values.ContainsKey('mode') -and $Values.mode -cnotmatch '^(?:SOLO|PARTY|BOTH)$')){throw 'TRAIN002_UNSUPPORTED_MODE'}
    if($Values.learningMode -notin @('SHADOW','ON') -or ($Values.learningMode -eq 'ON' -and ([long]$Values.modelVersion -lt 1 -or $Values.modelSha -cnotmatch '^[0-9a-f]{64}$'))){throw 'TRAIN002_MODEL_PIN'}
    if(@($Values.sqlPort,$Values.gamePort,$Values.loginPort|Select-Object -Unique).Count -ne 3){throw 'TRAIN002_DISTINCT_ENDPOINTS_REQUIRED'}
    if([int]$Values.maxAdaptiveIdleSeconds -lt [int]$Values.idleSeconds){throw 'TRAIN002_ADAPTIVE_IDLE_BELOW_BASE'}
    if($autonomous){Assert-TrainAutonomousConfig $Values}
}
function Get-TrainAdaptiveIdle($Config,$Resource) {
    $cores=[int]$Config.cpuCount;$budget=[int]$Config.cpuBudgetPercent;$base=[int]$Config.idleSeconds;$maximum=[int]$Config.maxAdaptiveIdleSeconds
    if($cores -lt 1 -or $cores -gt 16 -or $budget -lt 1 -or $budget -gt 100 -or $base -lt 1 -or $maximum -lt $base -or $maximum -gt 3600){throw 'TRAIN002_ADAPTIVE_IDLE_BOUNDS'}
    $decision=[ordered]@{schemaVersion=1;scope='COMPLETED_NATIVE_CPU_ONLY';measurement='UNKNOWN';reason='UNKNOWN_COMPLETED_NATIVE_METRICS';cpuCount=$cores;cpuBudgetPercent=$budget;baseIdleSeconds=$base;maxAdaptiveIdleSeconds=$maximum;idleSeconds=$base;elapsedMillis=$null;cpuSeconds=$null;sampledUtc=$null;requiredIdleSeconds=$null;predictedAverageNativeCpuPercent=$null;capReached=$false}
    if($null -eq $Resource){return $decision}
    foreach($key in @('measurement','exitObserved','elapsedMillis','cpuSeconds')){if(!$Resource.PSObject.Properties[$key] -or $null -eq $Resource.$key){return $decision}}
    if($Resource.measurement -cne 'PROCESS_METADATA' -or $Resource.exitObserved -isnot [bool] -or !$Resource.exitObserved){return $decision}
    try{$elapsed=[double]$Resource.elapsedMillis;$cpu=[double]$Resource.cpuSeconds}catch{return $decision}
    if([double]::IsNaN($elapsed) -or [double]::IsInfinity($elapsed) -or [double]::IsNaN($cpu) -or [double]::IsInfinity($cpu) -or $elapsed -le 0 -or $cpu -lt 0){return $decision}
    $required=[Math]::Max(0,[Math]::Ceiling($cpu/($cores*$budget/100.0)-$elapsed/1000.0))
    if([double]::IsInfinity($required)){return $decision}
    $delay=[int][Math]::Min($maximum,[Math]::Max($base,$required))
    $decision.measurement='KNOWN';$decision.reason=$(if($required -gt $maximum){'CPU_IDLE_CAPPED'}elseif($delay -gt $base){'CPU_PRESSURE_ADAPTIVE_IDLE'}else{'BASE_IDLE_SUFFICIENT'})
    $decision.idleSeconds=$delay;$decision.elapsedMillis=$elapsed;$decision.cpuSeconds=$cpu;$decision.requiredIdleSeconds=$required;$decision.capReached=($required -gt $maximum)
    if($Resource.PSObject.Properties['sampledUtc']){$decision.sampledUtc=$Resource.sampledUtc}
    $decision.predictedAverageNativeCpuPercent=[Math]::Round(100*$cpu/($cores*($elapsed/1000.0+$delay)),3)
    return $decision
}
function Get-TrainEpisodeSummary([object[]]$Rows,[int]$Attempted) {
    if($null -eq $Rows){$Rows=@()}
    $completed=0;$failed=0;[long]$effects=0;$classes=@()
    foreach($row in $Rows){
        $outcome=$row.PSObject.Properties['outcome'];$afterCut=$row.PSObject.Properties['afterCut'];$classId=$row.PSObject.Properties['classId'];$paid=$row.PSObject.Properties['paidRawEffects']
        if($outcome -and $outcome.Value -ceq 'COMPLETED'){$completed++}
        if($outcome -and $outcome.Value -ceq 'FAILED'){$failed++}
        if($afterCut -and $afterCut.Value -eq $true -and $classId){$classes+=([int]$classId.Value)}
        if($paid -and $null -ne $paid.Value){$effects+=[long]$paid.Value}
    }
    return @{attempted=$Attempted;settled=$Rows.Count;completed=$completed;failed=$failed;classes=@($classes|Sort-Object -Unique);paidEffects=$effects}
}
function Get-TrainHeartbeat([string]$Heartbeat,[datetime]$UtcNow) {
    $stamp=[datetime]::MinValue
    if(!$Heartbeat -or ![datetime]::TryParse($Heartbeat,[ref]$stamp)){return @{state='UNKNOWN';ageSeconds=$null}}
    $age=($UtcNow.ToUniversalTime()-$stamp.ToUniversalTime()).TotalSeconds
    if($age -lt -5){return @{state='UNKNOWN';ageSeconds=$age}}
    return @{state=$(if($age -gt 90){'STALE'}else{'FRESH'});ageSeconds=[Math]::Round($age,1)}
}
function Write-TrainConfig([string]$Path,$Values){Assert-TrainConfig $Values;Write-TrainText $Path (($Values.Keys|Sort-Object|ForEach-Object{$_+'='+$Values[$_]}) -join "`n")}
function Get-TrainColdBaseline([string]$Body) {
    $baseline=@{EnablePhantomSystem='False';LearningMode='OFF';LearningModelVersion='0';LearningEvidenceEnabled='False';LearningDeterministicSeed='0'}
    foreach($key in $baseline.Keys){
        $pattern='(?m)^'+[regex]::Escape($key)+'[ \t]*=[^\r\n]*'
        if([regex]::Matches($Body,$pattern).Count -ne 1){throw ('TRAIN002_COLD_BASELINE_KEY:'+ $key)}
        $Body=[regex]::Replace($Body,$pattern,($key+' = '+$baseline[$key]))
    }
    return $Body
}
function Get-TrainExportAdmission([bool]$DirectorLive,[bool]$NativeStopped,[bool]$LifetimeLockHeld) {
    if($DirectorLive){return 'QUEUE'}
    if(!$NativeStopped -or !$LifetimeLockHeld){throw 'TRAIN002_EXPORT_NOT_AT_OWN_STOPPED_BOUNDARY'}
    return 'OFFLINE'
}
function Get-TrainJavaArguments([string]$Class,[string[]]$Arguments,[string]$ClassPath,[string]$WorkerId,[int]$CpuCount,[int]$HeapMb) {
    $javaArguments=@(('-XX:ActiveProcessorCount='+$CpuCount),'-Xms256m',('-Xmx'+$HeapMb+'m'),('-Dphantom.training.worker='+$WorkerId),'-Dphantom.learning.train=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',('"'+$ClassPath+'"'),$Class)
    $javaArguments+=@($Arguments|ForEach-Object{'"'+$_+'"'})
    return $javaArguments
}
function Assert-TrainIdentitySnapshot($Fact,$Expected) {
    if([long]$Fact.pid -ne [long]$Expected.pid -or [long]$Fact.created -ne [long]$Expected.created -or $Fact.image -ine $Expected.image -or $Fact.sid -cne $Expected.sid -or
       [string]::IsNullOrEmpty($Expected.nonce) -or $Fact.command -notmatch ('(?<![A-Za-z0-9])'+[regex]::Escape($Expected.nonce)+'(?![A-Za-z0-9])')){throw 'TRAIN002_FOREIGN_PROCESS'}
    if($Fact.ContainsKey('listener') -and ($Fact.listener -cne '127.0.0.1' -or [long]$Fact.listenerPid -ne [long]$Expected.pid)){throw 'TRAIN002_FOREIGN_LISTENER'}
}
function New-TrainProcessRecord($Process,[string]$Image,[string]$Nonce,[string]$Kind,[string]$ConfigPath='') {
    $native=[Win32SqlIdentity]::Process([uint32]$Process.Id,0x1000)
    if(!$native.Success -or $native.ImagePath -ine $Image){throw 'TRAIN002_STARTED_PROCESS_UNPROVEN'}
    $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($Process.Id)"
    if(!$cim -or !$cim.CommandLine){throw 'TRAIN002_STARTED_COMMAND_UNAVAILABLE'}
    if($Process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne $native.CreatedFileTime){throw 'TRAIN002_STARTED_PROCESS_INCARNATION_CHANGED'}
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$commandHash=[Convert]::ToBase64String($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($cim.CommandLine)))}finally{$hash.Dispose()}
    $parent=[Win32SqlIdentity]::Process([uint32]$cim.ParentProcessId,0x1000)
    return [ordered]@{pid=$Process.Id;created=$native.CreatedFileTime;parentProcessId=$cim.ParentProcessId;parentCreated=$(if($parent.Success){$parent.CreatedFileTime}else{0L});image=$Image;imageHash=(Get-FileHash -LiteralPath $Image).Hash;sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;nonce=$Nonce;kind=$Kind;commandHash=$commandHash;configPath=$ConfigPath;configHash=$(if($ConfigPath){(Get-FileHash -LiteralPath $ConfigPath).Hash}else{''})}
}
function Assert-TrainProcess($Record,[ValidateSet('Director','Sql','Native','Worker')][string]$Kind,[int]$Port=0) {
    Initialize-TrainWin32
    Assert-G0PhysicalFile $Record.image
    if($Record.kind -cne $Kind -or (Get-FileHash -LiteralPath $Record.image).Hash -cne $Record.imageHash){throw 'TRAIN002_PROCESS_IMAGE_CHANGED'}
    if($Record.configPath){Assert-G0PhysicalFile $Record.configPath;if((Get-FileHash -LiteralPath $Record.configPath).Hash -cne $Record.configHash){throw 'TRAIN002_PROCESS_CONFIG_CHANGED'}}
    for($pass=0;$pass -lt 2;$pass++){
        $identity=[Win32SqlIdentity]::Process([uint32]$Record.pid,0x1000)
        $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.pid)"
        if(!$identity.Success -or !$cim -or $cim.ExecutablePath -ine $Record.image -or (Get-G0Microseconds $identity.CreatedFileTime) -ne (Get-G0Microseconds $cim.CreationDate.ToUniversalTime().ToFileTimeUtc())){
            $failure=[InvalidOperationException]::new('TRAIN002_PROCESS_UNAVAILABLE');$failure.Data['pid']=$Record.pid;$failure.Data['created']=$Record.created;$failure.Data['nativeError']=$identity.Error;throw $failure
        }
        $owner=Invoke-CimMethod -InputObject $cim -MethodName GetOwnerSid
        if($owner.ReturnValue -ne 0 -or $Record.sid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'TRAIN002_PROCESS_OWNER'}
        $fact=@{pid=$Record.pid;created=$identity.CreatedFileTime;image=$identity.ImagePath;sid=$owner.Sid;command=$cim.CommandLine}
        $hash=[Security.Cryptography.SHA256]::Create()
        try{$commandHash=[Convert]::ToBase64String($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($cim.CommandLine)))}finally{$hash.Dispose()}
        if($commandHash -cne $Record.commandHash){throw 'TRAIN002_PROCESS_FLAGS_CHANGED'}
        if($Kind -eq 'Sql'){
            $pattern='^"?'+[regex]::Escape($Record.image)+'"?\s+"--defaults-file='+[regex]::Escape($Record.configPath)+'"\s+--console\s*$'
            if($cim.CommandLine -cnotmatch $pattern -or @(Get-CimInstance Win32_Service -Filter "ProcessId=$($Record.pid)").Count){throw 'TRAIN002_SQL_COMMAND_OR_SERVICE'}
        }
        if($Port){$listeners=@([Win32SqlIdentity]::Listeners($Port));if($listeners.Count -ne 1){throw 'TRAIN002_LISTENER_COUNT'};$fact.listener=$listeners[0].LocalAddress;$fact.listenerPid=$listeners[0].OwningProcess}
        Assert-TrainIdentitySnapshot $fact $Record
    }
}
function Test-TrainProcessAbsent($Record,[Diagnostics.Process]$TrackedProcess=$null,[object]$Handle=$null,$ReadOnlyLayout=$null,[scriptblock]$ProcessQuery=$null) {
    if($ReadOnlyLayout){Assert-TrainInstallation $ReadOnlyLayout}
    if($ProcessQuery -and !$ReadOnlyLayout){throw 'TRAIN017_PROCESS_QUERY_REQUIRES_READONLY'}
    $lastError=-1
    for($query=0;$query -lt 10;$query++){
        if($null -ne $TrackedProcess){
            $held=Get-TrainHeldExitProof $Record $TrackedProcess $Handle
            if($held.exited){
                if(!$ReadOnlyLayout){Save-TrainProcessObservation $Record $null 'OWN_HELD_HANDLE_EXIT' $held.exitCode}
                return $true
            }
        }
        $actual=if($ProcessQuery){& $ProcessQuery ([uint32]$Record.pid)}else{[Win32SqlIdentity]::Process([uint32]$Record.pid,0x1000)}
        if($actual.Success){if($lastError -ge 0 -and !$ReadOnlyLayout){Save-TrainProcessObservation $Record 0 'VISIBLE_AFTER_RETRY'};return $actual.CreatedFileTime -ne [long]$Record.created}
        if($actual.Error -eq 87){if($lastError -ge 0 -and !$ReadOnlyLayout){Save-TrainProcessObservation $Record 87 'ABSENT_AFTER_RETRY'};return $true}
        if($actual.Error -eq 31){
            $layout=if($ReadOnlyLayout){$ReadOnlyLayout}else{Get-TrainLayout}
            if((Test-TrainExitReceipt $layout $Record) -and @(Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.pid)").Count -eq 0){
                if(!$ReadOnlyLayout){Save-TrainProcessObservation $Record 31 'PRIVATE_HELD_EXIT_RECEIPT_CIM_ABSENT'}
                return $true
            }
        }
        if($actual.Error -ne $lastError){if(!$ReadOnlyLayout){Save-TrainProcessObservation $Record $actual.Error 'VISIBILITY_UNKNOWN'};$lastError=$actual.Error}
        if($query -lt 9){Start-Sleep -Milliseconds 100}
    }
    $failure=[InvalidOperationException]::new('TRAIN002_PROCESS_VISIBILITY_UNKNOWN');$failure.Data['pid']=$Record.pid;$failure.Data['created']=$Record.created;$failure.Data['nativeError']=$lastError;throw $failure
}
function Get-TrainProcessResources([Diagnostics.Process]$Process,$Prior=$null) {
    try{
        $Process.Refresh()
        $peak=$Process.PeakWorkingSet64
        if($Prior -and $Prior.peakWorkingSetBytes -gt $peak){$peak=$Prior.peakWorkingSetBytes}
        $started=$Process.StartTime.ToUniversalTime()
        return [ordered]@{pid=$Process.Id;startedUtc=$started.ToString('o');sampledUtc=[DateTime]::UtcNow.ToString('o');elapsedMillis=[long]([DateTime]::UtcNow-$started).TotalMilliseconds;cpuSeconds=[Math]::Round($Process.TotalProcessorTime.TotalSeconds,6);workingSetBytes=$Process.WorkingSet64;peakWorkingSetBytes=$peak;measurement='PROCESS_METADATA';exitObserved=$Process.HasExited}
    }catch{
        if($Prior){return $Prior}
        return [ordered]@{pid=$null;startedUtc=$null;sampledUtc=[DateTime]::UtcNow.ToString('o');elapsedMillis=$null;cpuSeconds=$null;workingSetBytes=$null;peakWorkingSetBytes=$null;measurement='UNKNOWN';exitObserved=$null}
    }
}
function Assert-TrainPortsFree($Ports){Initialize-TrainWin32;foreach($port in $Ports){if(@([Win32SqlIdentity]::Listeners([int]$port)).Count){throw ('TRAIN002_PORT_BUSY:'+ $port)}}}
function Assert-TrainHash([string]$Path,[string]$Sha){Assert-G0PhysicalFile $Path;if((Get-FileHash -LiteralPath $Path).Hash -ine $Sha){throw ('TRAIN002_HASH_MISMATCH:'+ $Path)}}
function Get-TrainProtectedPin($Config) {
    if($Config -and $Config.ContainsKey('configProfile') -and $Config.configProfile -ceq 'OUTCOME006'){
        if(!$Config.ContainsKey('outcome006ProtectedSha') -or $Config.outcome006ProtectedSha -ine $script:TrainProtected006Sha){throw 'OUTCOME006_EXACT_PROTECTED_BASELINE_REQUIRED'}
        return $script:TrainProtected006Sha
    }
    if($Config -and $Config.ContainsKey('configProfile') -and $Config.configProfile -ceq 'EVAL005'){
        if(!$Config.ContainsKey('evaluation005ProtectedSha') -or $Config.evaluation005ProtectedSha -ine $script:TrainProtected005Sha){throw 'EVAL005_EXACT_PROTECTED_BASELINE_REQUIRED'}
        return $script:TrainProtected005Sha
    }
    return $script:TrainProtectedSha
}
function Assert-TrainAssets([string]$GeoDirectory,[string]$GeoManifest,[string]$ProtectedManifest,$Config=$null) {
    Assert-TrainHash $GeoManifest $script:TrainGeoSha;Assert-TrainHash $ProtectedManifest (Get-TrainProtectedPin $Config)
    $geo=ConvertFrom-Json ([IO.File]::ReadAllText($GeoManifest));$protected=ConvertFrom-Json ([IO.File]::ReadAllText($ProtectedManifest))
    Assert-TrainDirectory $GeoDirectory
    $files=@(Get-ChildItem -LiteralPath $GeoDirectory -Force -File)
    if($geo.Count -ne 203 -or $files.Count -ne 203 -or ($geo|Measure-Object bytes -Sum).Sum -ne 1063452308 -or @(Compare-Object $files.Name $geo.name -CaseSensitive).Count -or $protected.Count -ne 207){throw 'TRAIN002_GEO_OR_PROTECTED_SET'}
    foreach($row in $geo){$path=Join-Path $GeoDirectory $row.name;if((Get-Item -LiteralPath $path).Length -ne $row.bytes){throw 'TRAIN002_GEO_SIZE'};Assert-TrainHash $path $row.sha256}
    foreach($row in $protected){Assert-TrainHash $row.path $row.sha256}
}
function Assert-TrainInstallation($Layout) {
    Assert-TrainPrivate $Layout.Root
    $owner=Join-Path $Layout.Root 'owner.txt';Assert-G0PhysicalFile $owner
    if([IO.File]::ReadAllText($owner) -cnotmatch '^LEARNING002:[0-9a-f]{32}$'){throw 'TRAIN002_OWNER_MARKER'}
}
function Enter-TrainLock($Layout) {
    Assert-TrainInstallation $Layout
    $path=Join-Path $Layout.Root 'director.lock'
    if(Test-Path -LiteralPath $path){Assert-G0PhysicalFile $path}
    return [IO.FileStream]::new($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
}
function Assert-TrainSqlOptions($Layout,$Config,$Record) {
    Assert-TrainInstallation $Layout
    Assert-TrainDirectory (Join-Path $Layout.Sql 'data')
    Assert-G0PhysicalFile $Record.configPath
    if((Split-Path $Record.configPath -Parent) -ine $Layout.Sql){throw 'TRAIN002_SQL_CONFIG_SCOPE'}
    $expected=@('[mysqld]','bind-address=127.0.0.1',('port='+$Config.sqlPort),('datadir="'+(Join-Path $Layout.Sql 'data').Replace('\','/')+'"'),'default-time-zone=+00:00')
    if(([IO.File]::ReadAllLines($Record.configPath) -join '|') -cne ($expected -join '|')){throw 'TRAIN002_SQL_OPTIONS'}
}
function Stop-TrainSql($Layout,$Config,$Record) {
    Assert-TrainSqlOptions $Layout $Config $Record
    Assert-TrainProcess $Record -Kind Sql -Port ([int]$Config.sqlPort)
    $process=Get-Process -Id $Record.pid -ErrorAction Stop
    $handle=Hold-ProcessHandle $process
    if($process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$Record.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
    $resources=Get-TrainProcessResources $process
    $admin=Join-Path $Layout.Sql 'admin.client.ini';Assert-G0PhysicalFile $admin
    $lines=[IO.File]::ReadAllLines($admin)
    if($lines.Count -ne 5 -or ($lines[0..3] -join '|') -cne ('[client]|host=127.0.0.1|port='+$Config.sqlPort+'|user=root') -or $lines[4] -cnotmatch '^password=[0-9a-f]{64}$'){throw 'TRAIN002_ADMIN_OPTIONS'}
    try{
        & $Layout.Admin ('--defaults-file='+$admin) --protocol=tcp --host=127.0.0.1 ('--port='+$Config.sqlPort) --user=root shutdown
        if($LASTEXITCODE -ne 0){throw 'TRAIN002_SQL_SHUTDOWN_FAILED'}
        $deadline=[DateTime]::UtcNow.AddSeconds([int]$Config.shutdownTimeoutSeconds)
        do{
            $resources=Get-TrainProcessResources $process $resources
            if(Test-TrainProcessAbsent $Record $process $handle){Assert-TrainPortsFree @([int]$Config.sqlPort);Write-TrainExitReceipt $Layout $Record $process $handle;return}
            Start-Sleep -Milliseconds 250
        }while([DateTime]::UtcNow -lt $deadline)
        throw 'TRAIN002_SQL_STOP_UNCONFIRMED'
    }finally{
        $resources=Get-TrainProcessResources $process $resources
        $resources['finishedUtc']=[DateTime]::UtcNow.ToString('o')
        Write-TrainJson (Join-Path $Layout.Sql ('sql-resources.'+$Record.created+'.json')) $resources
        $process.Dispose()
    }
}
function Enter-TrainInitialization($Layout) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$key=([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($Layout.Root.ToLowerInvariant()+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value)))).Replace('-','')}finally{$hash.Dispose()}
    $mutex=[Threading.Mutex]::new($false,('Local\PhantomTraining002-'+$key))
    try{$taken=$mutex.WaitOne(5000)}catch [Threading.AbandonedMutexException]{$taken=$true}
    if(!$taken){$mutex.Dispose();throw 'TRAIN002_ANOTHER_COMMAND_ACTIVE'}
    return $mutex
}
