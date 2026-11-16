[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Start','Run','Status','Pause','Resume','Stop','Report','Export','Preflight','Prepare','RepairClosure017','ActionValue019','ActionValue020','ActionValue021')][string]$Action,
    [string]$Root,
    [string]$RepairEvidenceRoot,
    [string]$ColdLineageProof,
    [string]$ActionValueCandidate,
    [ValidateSet('RunOnce','Status','Stop')][string]$ActionValue020Command='Status',
    [ValidateSet('V1','V3','EVAL005','OUTCOME006')][string]$ConfigProfile='V1',
    [switch]$EnableOutcome006,
    [switch]$EnableExploration007,
    [switch]$EnableExploration008,
    [switch]$Autonomous,
    [ValidateRange(0,2)][int]$Readiness014Session=0,
    [ValidateRange(0,2)][int]$Readiness015Session=0,
    [ValidateRange(0,3)][int]$Readiness016Session=0,
    [switch]$Json,
    [switch]$EnableEvaluation005,
    [ValidateRange(1,4)][int]$Evaluation005PairLimit=4,
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')][string]$Evaluation005Campaign='learning005',
    [ValidatePattern('^[0-9a-f]{32}$')][string]$Nonce
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
if($Action -ceq 'ActionValue019'){Invoke-TrainActionValue019 $Root $ActionValueCandidate;exit}
if($Action -ceq 'ActionValue020'){Invoke-TrainActionValue020Step $Root $ActionValueCandidate $ActionValue020Command;exit}
if($Action -ceq 'ActionValue021'){Invoke-TrainActionValue021Step $Root $ActionValueCandidate $ActionValue020Command;exit}
$layout=if($Action -ceq 'RepairClosure017'){$null}else{Get-TrainLayout $Root}
$script:State=$null
$script:DirectorNonce=$Nonce
$script:SqlRecord=$null
$script:NativeRecord=$null
$script:WorkerRecord=$null
$script:Config=$null
$script:AutonomousCheckpointContext=$null
$script:HeadlessCleanupDeadline013=$null
$script:ReadinessOnly013=$false
$script:ReadinessDeadline013=$null
$script:HeadlessAdoption013=$null
if(@(@($Readiness014Session,$Readiness015Session,$Readiness016Session)|Where-Object {$_ -gt 0}).Count -gt 1){throw 'TRAIN015_EXCLUSIVE_READINESS_GOAL'}
$script:ReadinessSession=if($Readiness016Session -gt 0){$Readiness016Session}elseif($Readiness015Session -gt 0){$Readiness015Session}else{$Readiness014Session}
$script:ReadinessGoalVersion=if($Readiness016Session -gt 0){16}elseif($Readiness015Session -gt 0){15}else{14}
if($script:ReadinessSession -gt 0 -and ($Action -cne 'Prepare' -or !$Autonomous)){throw 'TRAIN014_READINESS_ONLY'}
function Get-TrainReadinessWindow014([int]$Session,$Prior,[datetime]$Now=[DateTime]::UtcNow) {
    if($Session -eq 1){if($Prior){throw 'TRAIN014_SESSION_REPLAY'};$seconds=480}
    elseif($Session -eq 2){
        if(!$Prior -or $Prior.schemaVersion -ne 14 -or $Prior.session -ne 1 -or !$Prior.ownedWorkerAbsent -or !$Prior.sqlAbsent -or !$Prior.withinCap -or
            [long]$Prior.elapsedMillis -lt 0 -or [long]$Prior.elapsedMillis -gt 480000 -or ([datetime]$Prior.finishedUtc).ToUniversalTime() -gt $Now.ToUniversalTime()){throw 'TRAIN014_PRIOR_EXIT_OR_CAP'}
        $seconds=720
    }else{throw 'TRAIN014_TWO_SESSIONS_ONLY'}
    return [pscustomobject]@{schemaVersion=13;goalVersion=14;session=$Session;nonce=[guid]::NewGuid().ToString('N');issuedUtc=$Now.ToUniversalTime().ToString('o');hardDeadlineUtc=$Now.ToUniversalTime().AddSeconds($seconds).ToString('o');maximumSeconds=$seconds;drainReserveSeconds=210;trainingCredit=$false}
}
function Get-TrainReadinessWindow015([int]$Session,$Prior,[datetime]$Now=[DateTime]::UtcNow) {
    $mapped=$null
    if($Prior){
        if($Prior.schemaVersion -ne 15){throw 'TRAIN015_HISTORICAL_RECEIPT_REUSE'}
        $mapped=[pscustomobject]@{schemaVersion=14;session=$Prior.session;elapsedMillis=$Prior.elapsedMillis;finishedUtc=$Prior.finishedUtc;ownedWorkerAbsent=$Prior.ownedWorkerAbsent;sqlAbsent=$Prior.sqlAbsent;withinCap=$Prior.withinCap}
    }
    $window=Get-TrainReadinessWindow014 $Session $mapped $Now
    $window.goalVersion=15
    return $window
}
function Assert-TrainReadinessMode015($Values,[int]$Session) {
    if($Session -eq 0){return}
    if($Session -notin @(1,2) -or !$Values.ContainsKey('autonomous013Version') -or $Values.autonomous013Version -cne '1' -or
        !$Values.ContainsKey('autonomous009EvaluationOnly') -or $Values.autonomous009EvaluationOnly -cne 'false'){throw 'TRAIN015_TRAIN_ONLY_BOUNDED_MODE_REQUIRED'}
    if(!$Values.ContainsKey('episodesPerBatch') -or $Values.episodesPerBatch -cne '51'){throw 'TRAIN015_PIPELINE_51_REQUIRED'}
}
function Test-TrainIncremental016($Values) {return $Values -and $Values.ContainsKey('autonomous016Version') -and $Values.autonomous016Version -ceq '1'}
function Assert-TrainReadinessMode016($Values,[int]$Session) {
    if($Session -gt 0 -and !(Test-TrainIncremental016 $Values)){throw 'TRAIN016_EXPLICIT_PROTOCOL_REQUIRED'}
    if(Test-TrainIncremental016 $Values){
        Assert-TrainConfig $Values
        if($Session -notin @(0,1,2,3) -or ($Action -ceq 'Prepare' -and $Session -eq 0)){throw 'TRAIN016_BOUNDED_READINESS_REQUIRED'}
        if($Values.autonomous009Campaign -ceq 'autonomous009-learning016-g2-v1' -and $Session -gt 0 -and $Session -notin @(2,3)){throw 'TRAIN016_GENERATION2_REQUIRES_REMAINING_SESSIONS'}
    }
}
function Get-TrainReadinessWindow016([int]$Session,[object[]]$Prior,[datetime]$Now=[DateTime]::UtcNow,[long]$HistoricalExtraMillis=0) {
    $rows=@($Prior|Where-Object {$null -ne $_});$elapsed=0L;$ordinal=0
    if($Session -lt 1 -or $Session -gt 3 -or $rows.Count -ne $Session-1){throw 'TRAIN016_READINESS_ORDINAL_OR_REPLAY'}
    if($HistoricalExtraMillis -notin @(0L,100160L) -or ($Session -eq 1 -and $HistoricalExtraMillis -ne 0)){throw 'TRAIN016_HISTORICAL_CHARGE_CHANGED'}
    foreach($row in $rows){
        $ordinal++
        if($row.schemaVersion -ne 16 -or $row.session -ne $ordinal -or !$row.ownedWorkerAbsent -or !$row.sqlAbsent -or !$row.withinCap -or
           [long]$row.elapsedMillis -lt 0 -or [long]$row.elapsedMillis -gt 600000L -or ([datetime]$row.finishedUtc).ToUniversalTime() -gt $Now.ToUniversalTime()){throw 'TRAIN016_PRIOR_EXIT_OR_CAP'}
        $elapsed+=[long]$row.elapsedMillis
    }
    $elapsed+=$HistoricalExtraMillis
    $maximum=[int][Math]::Min(600,[Math]::Floor((1800000L-$elapsed)/1000))
    if($maximum -le 210){throw 'TRAIN016_HEADLESS_BUDGET_EXHAUSTED'}
    return [pscustomobject]@{schemaVersion=13;goalVersion=16;session=$Session;nonce=[guid]::NewGuid().ToString('N');issuedUtc=$Now.ToUniversalTime().ToString('o');hardDeadlineUtc=$Now.ToUniversalTime().AddSeconds($maximum).ToString('o');maximumSeconds=$maximum;priorElapsedMillis=$elapsed;historicalExtraMillis=$HistoricalExtraMillis;drainReserveSeconds=210;trainingCredit=$false}
}
function Save-TrainReadinessControl016([string]$PassRoot,[int]$Session) {
    if($Session -eq 1){return}
    $scope=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$script:Config.autonomous009Campaign+'\cohorts\'+$script:Config.outcome006DatasetId)
    $fromParent=$Session -eq 2 -and $script:Config.autonomous009Campaign -ceq 'autonomous009-learning016-g2-v1'
    if($fromParent){
        if(Test-Path -LiteralPath (Join-Path $scope 'headless-operation013.json')){throw 'TRAIN016_GENERATION2_SESSION_ALREADY_EXISTS'}
        $scope=Join-Path $layout.Root 'autonomous\v1\campaigns\autonomous009-learning016-v1\cohorts\autonomous009-learning016-v1-c000001'
    }
    if(Test-Path -LiteralPath (Join-Path $scope 'cohort016.complete.json')){throw 'TRAIN016_ALREADY_SEALED_NO_MORE_READINESS'}
    $old=Read-TrainJson (Join-Path $scope 'headless-operation013.json')
    if($old.goalVersion -ne 16 -or $old.session -ne $Session-1 -or !$old.exitProven -or !$old.worker -or !(Test-TrainProcessAbsent $old.worker) -or !(Test-TrainExitReceipt $layout $old.worker)){throw 'TRAIN016_PREVIOUS_WORKER_EXIT_UNPROVEN'}
    $sql=Read-TrainJson (Join-Path $layout.Sql 'owned-process.json')
    if(!(Test-TrainProcessAbsent $sql) -or !(Test-TrainExitReceipt $layout $sql)){throw 'TRAIN016_PREVIOUS_SQL_EXIT_UNPROVEN'}
    if($fromParent){
        $issued=Read-TrainJson (Join-Path $layout.Root 'autonomous\v16\generation2-issued.json')
        if($issued.schema -cne 'TRAIN016_GENERATION2_ISSUED_V1' -or $issued.campaign -cne $script:Config.autonomous009Campaign -or $issued.dataset -cne $script:Config.outcome006DatasetId -or $old.status -cne 'ERROR'){throw 'TRAIN016_GENERATION2_PARENT_CONTROL_CHANGED'}
        Assert-TrainHash (Join-Path $scope 'headless-operation013.json') $issued.parentFiles.'headless-operation013.json'
        return # The original campaign controls are immutable; no archive/remove/rewrite.
    }
    $ownerSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'owner.txt')).Hash
    $backup=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('PhantomLearning016\control-backups\'+$ownerSha+'\session'+($Session-1))
    if(!(Test-Path -LiteralPath $backup)){
        $null=[IO.Directory]::CreateDirectory($backup)
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User;$acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetOwner($sid);$acl.SetAccessRuleProtection($true,$false)
        foreach($trustee in @($sid.Value,'S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
        Set-Acl -LiteralPath $backup -AclObject $acl
    }
    Assert-TrainPrivate $backup
    $archive=Join-Path $layout.Root ('autonomous\v16\readiness\session'+($Session-1)+'-control');$null=[IO.Directory]::CreateDirectory($archive);Assert-TrainDirectory $archive
    foreach($entry in @(@{root=$scope;prefix='cohort';names=@('headless-operation013.json','headless-result013.json','preparation-cost014.json','stop.request')},@{root=$PassRoot;prefix='director';names=@('readiness-issued013.json','readiness-result013.json')})){
        foreach($name in $entry.names){
            $file=Join-Path $entry.root $name;if(!(Test-Path -LiteralPath $file)){continue};Assert-G0PhysicalFile $file;$sha=(Get-FileHash -LiteralPath $file).Hash
            foreach($destination in @((Join-Path $backup ($entry.prefix+'-'+$name)),(Join-Path $archive ($entry.prefix+'-'+$name)))){
                if(!(Test-Path -LiteralPath $destination)){[IO.File]::Copy($file,$destination,$false)};Assert-TrainHash $destination $sha
            }
            if($name -cne 'headless-operation013.json'){Remove-Item -LiteralPath $file}
        }
    }
}
function Test-TrainOutcomeV2($Values) {
    if(!$Values.ContainsKey('evaluationOutcomeVersion') -or $Values.evaluationOutcomeVersion -ceq '1'){return $false}
    if($Values.evaluationOutcomeVersion -cne '2' -or $Values.autonomous009EvaluationOnly -cne 'true' -or $Values.autonomous009Exploration -cne 'false' -or
       $Values.autonomous009Phase -cnotin @('OFF','EVAL') -or [int]$Values.autonomous009SmokeMaxStarts -gt 2 -or [int]$Values.autonomous009SmokeMaxNativeMinutes -gt 35){throw 'EVAL012_ONLY_FROZEN_BOUNDED_EVAL'}
    return $true
}
function Assert-TrainOutcomeV2Proof($NativeProof,$Pairs) {
    if($NativeProof.schema -cne 'EVAL_COMPLETE_V2' -or $NativeProof.trainingStateUnchanged -ne $true -or $NativeProof.trainingCredit -ne $false -or
       $NativeProof.productionPromotion -ne $false -or $Pairs.schema -cne 'EVAL_PAIRS_V2' -or $Pairs.trainingCredit -ne $false){throw 'EVAL012_PROOF_SCHEMA_OR_CREDIT'}
    $matched=@($Pairs.pairs|Where-Object {$_.terminalComparable -eq $true -and $_.status -cin @('TERMINAL_COMPARABLE','COMPARABLE_WITH_TIMEOUT')})
    if([int]$Pairs.terminalComparablePairs -ne $matched.Count -or @($Pairs.pairs|Group-Object cell|Where-Object {$_.Count -ne 1}).Count){throw 'EVAL012_PAIR_COUNT_OR_REUSE'}
    foreach($scope in @('SOLO','PARTY')){
        $group=@($matched|Where-Object {$_.direction.StartsWith($scope+'_',[StringComparison]::Ordinal)})
        $ab=@($group|Where-Object {$_.order -ceq 'STOCK_LEARNED'}).Count;$ba=@($group|Where-Object {$_.order -ceq 'LEARNED_STOCK'}).Count
        $tiers=@($group|ForEach-Object {$_.stratum}|Select-Object -Unique)
        if($group.Count -lt 2 -or $ab -lt 1 -or $ba -lt 1 -or $ab -ne $ba -or $tiers -cnotcontains 'LOWER_PRECUT_BURDEN' -or $tiers -cnotcontains 'HIGHER_PRECUT_BURDEN'){throw ('EVAL012_TERMINAL_PAIR_COVERAGE_'+$scope)}
    }
}
function Read-TrainSourceConfig {
    $profile=$ConfigProfile
    $current=$null
    $saved=Join-Path $layout.Root 'TRAIN_CONFIG.ini'
    if(Test-Path -LiteralPath $saved){$current=Read-TrainConfig $saved;if($current.ContainsKey('configProfile')){$profile=$current.configProfile}}
    if($current -and $current.ContainsKey('autonomous013ParentCampaign')){
        if($current.autonomous013ParentCampaign -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$'){throw 'TRAIN013_RECOVERY_PARENT_PATH'}
        $parentVersion=if(Test-TrainIncremental016 $current){16}else{13}
        $parentDirectory=Join-Path $layout.Root ('autonomous\v'+$parentVersion+'\campaigns\'+$current.autonomous013ParentCampaign)
        $parentPath=Join-Path $parentDirectory 'base-config.json';$parentJournal=Read-TrainJson (Join-Path $parentDirectory 'director-state.json')
        if((Get-FileHash -LiteralPath $parentPath).Hash -cne $current.autonomous013ParentConfigSha -or $parentJournal.configSha -cne $current.autonomous013ParentConfigSha){throw 'TRAIN013_RECOVERY_PARENT_SHA'}
        $current=Copy-TrainConfig013 (Read-TrainJson $parentPath)
    }
    if($Autonomous -or ($current -and $current.ContainsKey('autonomous009Version'))){
        if($EnableExploration007 -or $EnableExploration008 -or $EnableEvaluation005){throw 'TRAIN009_EXCLUSIVE_OPT_IN_REQUIRED'}
        $values=Read-TrainConfig (Join-Path $PSScriptRoot 'AutonomousTraining.ini')
        if($current -and $current.ContainsKey('autonomous009Version')){foreach($key in $current.Keys){$values[$key]=$current[$key]}}
        elseif($current){foreach($key in @('sqlPort','gamePort','loginPort','assetSourceRoot')){if($current.ContainsKey($key)){$values[$key]=$current[$key]}}}
        if($Action -in @('Start','Resume')){
            $values.autonomous009Enabled='true';$values.outcome006Enabled='true'
            if($values.autonomous009Campaign -ceq 'autonomous009-uninitialized'){$values.autonomous009Campaign='autonomous009-'+[guid]::NewGuid().ToString('N');$values.outcome006DatasetId=$values.autonomous009Campaign+'-c000001'}
            if($values.autonomous009Phase -ceq 'OFF'){$values.autonomous009Phase=if($values.autonomous009EvaluationOnly -ceq 'true'){'EVAL'}else{'PREPARE'}}
        }
        if((Test-TrainIncremental016 $values) -and $Action -ceq 'Prepare'){$values.autonomous009Enabled='true';$values.outcome006Enabled='true';$values.autonomous009Phase='PREPARE'}
        if((Test-TrainIncremental016 $values) -and $Action -cin @('Start','Resume')){
            $seal=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$values.autonomous009Campaign+'\cohorts\'+$values.outcome006DatasetId+'\cohort016.complete.json')
            if(!(Test-Path -LiteralPath $seal)){throw 'TRAIN016_G3_REQUIRED_BEFORE_START'}
        }
        Assert-TrainReadinessMode016 $values $Readiness016Session
        Assert-TrainConfig $values;return $values
    }
    if($EnableExploration007 -and $EnableExploration008){throw 'TRAIN008_EXCLUSIVE_OPT_IN_REQUIRED'}
    $is008=$EnableExploration008 -or ($current -and $current.ContainsKey('exploration008Enabled') -and $current.exploration008Enabled -ceq 'true')
    $is007=!$is008 -and ($EnableExploration007 -or ($current -and $current.ContainsKey('exploration007Enabled') -and $current.exploration007Enabled -ceq 'true'))
    if($is007 -or $is008){$profile='OUTCOME006'}
    $name=if($is008){'Exploration008.ini'}elseif($is007){'Exploration007.ini'}elseif($profile -ceq 'OUTCOME006'){'Outcome006.ini'}elseif($profile -ceq 'EVAL005'){'Evaluation005.ini'}elseif($profile -ceq 'V3'){'TrainingV3.ini'}else{'TRAIN_CONFIG.ini'}
    $values=Read-TrainConfig (Join-Path $PSScriptRoot $name)
    if($is008){
        if($EnableExploration008){$values.exploration008Enabled='true';$values.outcome006Enabled='true'}
        elseif($current){foreach($key in @($values.Keys | Where-Object {$_ -like 'exploration008*' -or $_ -eq 'outcome006Enabled'})){if($current.ContainsKey($key)){$values[$key]=$current[$key]}}}
        if($values.exploration008Campaign -cne 'learning008-pve-generalization-v1' -or $values.exploration008Dataset -cne 'learning008-residual-v1' -or $values.exploration007Enabled -cne 'false'){throw 'TRAIN008_EXACT_CAMPAIGN_REQUIRED'}
    }
    if($is007){
        if($EnableExploration007){$values.exploration007Enabled='true';$values.outcome006Enabled='true'}
        elseif($current){$values.exploration007Enabled=$current.exploration007Enabled;$values.outcome006Enabled=$current.outcome006Enabled}
        if($values.exploration007Campaign -cne 'learning007-causal-v1' -or $values.exploration007Dataset -cne 'learning007-advantage-v1'){throw 'TRAIN007_EXACT_CAMPAIGN_REQUIRED'}
    }
    if($profile -ceq 'OUTCOME006'){
        if($EnableOutcome006){$values.outcome006Enabled='true'}
        elseif($current -and $current.ContainsKey('outcome006Enabled')){$values.outcome006Enabled=$current.outcome006Enabled}
        if($Action -in @('Start','Resume','Run') -and $values.outcome006Enabled -cne 'true'){throw 'OUTCOME006_EXPLICIT_OPT_IN_REQUIRED'}
    }
    if($profile -ceq 'EVAL005'){
        if($EnableEvaluation005){$values.evaluation005Enabled='true';$values.evaluation005MaxPairsPerScope=[string]$Evaluation005PairLimit;$values.evaluation005Campaign=$Evaluation005Campaign}
        elseif($current -and $current.ContainsKey('evaluation005Enabled')){foreach($key in @('evaluation005Enabled','evaluation005MaxPairsPerScope','evaluation005Campaign')){$values[$key]=$current[$key]}}
        if($Action -in @('Start','Resume','Run') -and $values.evaluation005Enabled -cne 'true'){throw 'EVAL005_EXPLICIT_OPT_IN_REQUIRED'}
    }
    return $values
}
function Publish-TrainState([string]$Phase,[string]$Reason='') {
    if(!$script:State){return}
    if($script:Config -and $script:Config.ContainsKey('autonomous013Version') -and $script:Config.autonomous013Version -ceq '1' -and $script:Config.autonomous009EvaluationOnly -ceq 'true' -and $Phase -cin @('NATIVE','NATIVE_BOOT')){$Phase='AUDITING'}
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
    if($script:Config -and $script:Config.ContainsKey('configProfile') -and $script:Config.configProfile -ceq 'OUTCOME006'){
        $protectedManifest=Join-Path $layout.Clone '.phantom-local\learning006-baseline\protected207.006.json'
        Assert-TrainHash $protectedManifest (Get-TrainProtectedPin $script:Config)
    }
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
function Open-TrainHeadless013([string]$Installation,$Values,[string]$Operation,[string]$ConfigSha,[datetime]$Now=[DateTime]::UtcNow) {
    if($Operation -cnotin @('prepare','admit-eval') -or $Values.autonomous009Campaign -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$' -or $Values.outcome006DatasetId -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$' -or $ConfigSha -cnotmatch '^[a-f0-9]{64}$'){throw 'TRAIN013_HEADLESS_IDENTITY'}
    $relative=if($Operation -ceq 'prepare'){'cohorts\'+$Values.outcome006DatasetId}else{'evaluations\'+$Values.outcome006DatasetId+'-'+$Values.autonomous009ModelVersion}
    $scope=Join-Path $Installation ('autonomous\v1\campaigns\'+$Values.autonomous009Campaign+'\'+$relative)
    $null=[IO.Directory]::CreateDirectory($scope);Assert-TrainDirectory $scope;$path=Join-Path $scope 'headless-operation013.json';$recovered=Test-Path -LiteralPath $path
    $freshPass=Get-Variable -Name ReadinessDeadline013 -Scope Script -ValueOnly -ErrorAction SilentlyContinue
    if($recovered -and $freshPass -and $freshPass.PSObject.Properties['goalVersion'] -and $freshPass.goalVersion -eq 16){
        $prior=Read-TrainJson $path
        if(!$prior.PSObject.Properties['goalVersion'] -or $prior.goalVersion -ne 16 -or !$prior.PSObject.Properties['session'] -or $prior.session -gt $freshPass.session){throw 'TRAIN016_FOREIGN_HEADLESS_CONTEXT'}
        if($prior.session -lt $freshPass.session){
            if($prior.session -ne $freshPass.session-1 -or !$prior.exitProven -or !$prior.worker -or $prior.status -cin @('RUNNING','DRAINING') -or
                !(Test-TrainProcessAbsent $prior.worker) -or !(Test-TrainExitReceipt $layout $prior.worker)){throw 'TRAIN016_PREVIOUS_HELD_EXIT_REQUIRED'}
            $recovered=$false
        }
    }
    if($recovered){
        $j=Read-TrainJson $path
        $maximum=if($j.PSObject.Properties['goalVersion'] -and $j.goalVersion -eq 16){if($j.maximumSeconds -le 210 -or $j.maximumSeconds -gt 600){throw 'TRAIN016_HEADLESS_CAP'};[int]$j.maximumSeconds}elseif($j.PSObject.Properties['goalVersion'] -and $j.goalVersion -in @(14,15)){if($j.maximumSeconds -notin @(480,720)){throw 'TRAIN014_HEADLESS_CAP'};[int]$j.maximumSeconds}else{1200}
        foreach($field in @('issuedUtc','workDeadlineUtc','hardDeadlineUtc')){$j.$field=([datetime]$j.$field).ToUniversalTime().ToString('o')}
        if($j.schemaVersion -ne 13 -or $j.operation -cne $Operation -or $j.configSha -cne $ConfigSha -or $j.scope -cne $scope -or $j.trainingCredit -ne $false -or $j.nonce -cnotmatch '^[a-f0-9]{32}$' -or
           ([DateTime]::Parse($j.hardDeadlineUtc)-[DateTime]::Parse($j.issuedUtc)).TotalSeconds -ne $maximum -or ([DateTime]::Parse($j.workDeadlineUtc)-[DateTime]::Parse($j.issuedUtc)).TotalSeconds -ne ($maximum-210)){throw 'TRAIN013_HEADLESS_COLD_PIN_CHANGED'}
    }else{
        $pass=Get-Variable -Name ReadinessDeadline013 -Scope Script -ValueOnly -ErrorAction SilentlyContinue
        $issued=if($pass){[DateTime]::Parse($pass.issuedUtc).ToUniversalTime()}else{$Now.ToUniversalTime()}
        $bounded=$pass -and $pass.PSObject.Properties['goalVersion'] -and $pass.goalVersion -in @(14,15,16)
        $maximum=if($bounded){[int]$pass.maximumSeconds}else{1200}
        if($bounded -and $pass.goalVersion -eq 16){if($maximum -le 210 -or $maximum -gt 600){throw 'TRAIN016_HEADLESS_CAP'}}
        elseif($maximum -notin @(480,720,1200) -or ($bounded -and $maximum -eq 1200)){throw 'TRAIN014_HEADLESS_CAP'}
        $j=[pscustomobject]@{schemaVersion=13;operation=$Operation;scope=$scope;configSha=$ConfigSha;nonce=[guid]::NewGuid().ToString('N');issuedUtc=$issued.ToString('o');workDeadlineUtc=$issued.AddSeconds($maximum-210).ToString('o');hardDeadlineUtc=$issued.AddSeconds($maximum).ToString('o');drainDeadlineUtc='';status='RUNNING';reason='';worker=$null;exitCode=$null;exitProven=$false;resultSha='';trainingCredit=$false}
        if($maximum -ne 1200){$j|Add-Member goalVersion $pass.goalVersion;$j|Add-Member maximumSeconds $maximum}
        if($bounded -and $pass.goalVersion -eq 16){$j|Add-Member session $pass.session}
        Write-TrainJson $path $j
    }
    return [pscustomobject]@{scope=$scope;path=$path;journal=$j;recovered=$recovered;terminal=($j.status -cin @('READY','TIMEOUT','CANCELLED','INSUFFICIENT_EVIDENCE','ERROR','DRAIN_UNCONFIRMED'))}
}
function Update-TrainHeadless013($Context,[datetime]$Now,[bool]$Cancelled) {
    $j=$Context.journal;if($Context.terminal){return $Context}
    $stop=Join-Path $Context.scope 'stop.request'
    if(!$j.reason){
        if($Now.ToUniversalTime() -ge [DateTime]::Parse($j.workDeadlineUtc).ToUniversalTime()){$j.reason='TIMEOUT'}
        elseif($Cancelled -or (Test-Path -LiteralPath $stop)){$j.reason='CANCELLED'}
    }
    if($j.reason){
        if(!$j.drainDeadlineUtc){$drain=$Now.ToUniversalTime().AddSeconds(210);$hard=[DateTime]::Parse($j.hardDeadlineUtc).ToUniversalTime();if($drain -gt $hard){$drain=$hard};$j.drainDeadlineUtc=$drain.ToString('o')}
        if(!(Test-Path -LiteralPath $stop)){Write-TrainText $stop $j.nonce}
        if([IO.File]::ReadAllText($stop) -cne $j.nonce){throw 'TRAIN013_HEADLESS_STOP_IDENTITY'}
        $j.status='DRAINING'
        if($Now.ToUniversalTime() -ge ([datetime]$j.drainDeadlineUtc).ToUniversalTime()){$j.status='DRAIN_UNCONFIRMED'}
        Write-TrainJson $Context.path $j
    }
    return $Context
}
function Close-TrainHeadless013($Context,[int]$ExitCode,[string]$NativeStatus,[bool]$ExitProven,[datetime]$Now=[DateTime]::UtcNow) {
    $j=$Context.journal;$j.exitCode=$ExitCode;$j.exitProven=$ExitProven
    $j.status=if(!$ExitProven){'ERROR'}elseif($j.reason){$j.reason}elseif($ExitCode -eq 0 -and $NativeStatus -ceq 'READY'){'READY'}elseif($NativeStatus -cin @('TIMEOUT','CANCELLED','INSUFFICIENT_EVIDENCE')){$NativeStatus}else{'ERROR'}
    $j.reason=if($j.status -ceq 'READY'){''}else{$j.status}
    Write-TrainJson $Context.path $j;$Context.terminal=$true
}
function Get-TrainHeadlessActive013 {
    $activePath=Join-Path $layout.Root 'headless-active013.json';if(!(Test-Path -LiteralPath $activePath)){return $null}
    $active=Read-TrainJson $activePath;$prefix=(Join-Path $layout.Root 'autonomous\v1\campaigns')+'\'
    $scope=[IO.Path]::GetFullPath([string]$active.scope)
    if(!$scope.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase) -or $scope.Substring($prefix.Length) -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}\\(?:cohorts|evaluations)\\autonomous009-[A-Za-z0-9_-]{1,100}$'){throw 'TRAIN013_HEADLESS_CANCEL_SCOPE'}
    $j=Read-TrainJson (Join-Path $scope 'headless-operation013.json')
    if($j.nonce -cne $active.nonce -or $j.scope -cne $scope){throw 'TRAIN013_HEADLESS_CANCEL_IDENTITY'}
    return [pscustomobject]@{scope=$scope;path=(Join-Path $scope 'headless-operation013.json');journal=$j;recovered=$true;terminal=($j.status -cnotin @('RUNNING','DRAINING'))}
}
function Test-TrainHeadlessController013($Record) {
    $parent=Get-Process -Id $Record.parentProcessId -ErrorAction SilentlyContinue
    if(!$parent){return $false}
    try{return !$parent.HasExited -and $parent.StartTime.ToUniversalTime().ToFileTimeUtc() -eq [long]$Record.parentCreated}finally{$parent.Dispose()}
}
function Request-TrainHeadlessCancel013 {
    $context=Get-TrainHeadlessActive013;if(!$context){return $false};$j=$context.journal;$scope=$context.scope
    if($context.terminal -or !$j.worker){return $false}
    if(Test-TrainExitReceipt $layout $j.worker){return $false}
    if(Test-TrainProcessAbsent $j.worker){return $false}
    Assert-TrainProcess $j.worker -Kind Worker
    if($j.worker.nonce -cne $j.nonce){throw 'TRAIN013_HEADLESS_CANCEL_WORKER'}
    Write-TrainText (Join-Path $scope 'stop.request') $j.nonce
    return $true
}
function Get-TrainHeadlessCleanup013($Values) {
    $copy=@{};foreach($key in $Values.Keys){$copy[$key]=$Values[$key]}
    if($script:HeadlessCleanupDeadline013){$remaining=[Math]::Floor(($script:HeadlessCleanupDeadline013-[DateTime]::UtcNow).TotalSeconds);$copy.shutdownTimeoutSeconds=[string][Math]::Max(0,[Math]::Min([int]$copy.shutdownTimeoutSeconds,$remaining))}
    return $copy
}
function Get-TrainHeadlessReadinessVerdict013([bool]$Complete,[string]$LastStatus,[bool]$WithinCap) {
    if(!$WithinCap){return 'TIMEOUT'}
    if($Complete){return 'READY'}
    if($LastStatus -cin @('TIMEOUT','CANCELLED','INSUFFICIENT_EVIDENCE','ERROR','DRAIN_UNCONFIRMED')){return $LastStatus}
    return 'ERROR'
}
function Assert-TrainHeadlessAdmission013([string]$Scope,[string]$Admission) {
    if((Test-Path -LiteralPath $Admission) -and !(Test-Path -LiteralPath (Join-Path $Scope 'headless-operation013.json'))){throw 'TRAIN013_HEADLESS_ADMISSION_WITHOUT_PROVENANCE'}
}
function Throw-TrainHeadless013($Context) {
    $failure=[InvalidOperationException]::new('TRAIN013_HEADLESS_'+$Context.journal.status);$failure.Data['headlessOperation']=$Context.journal.operation;throw $failure
}
function Stop-TrainHeadlessSql013($Values,$Record) {
    if(!$script:HeadlessCleanupDeadline013){Stop-TrainSql $layout $Values $Record;return}
    if(Test-TrainExitReceipt $layout $Record){return}
    Assert-TrainSqlOptions $layout $Values $Record;Assert-TrainProcess $Record -Kind Sql -Port ([int]$Values.sqlPort)
    $remaining=[Math]::Floor(($script:HeadlessCleanupDeadline013-[DateTime]::UtcNow).TotalSeconds)
    if($remaining -lt 4){throw 'TRAIN013_HEADLESS_SQL_DRAIN_UNCONFIRMED'}
    $sqlProcess=Get-Process -Id $Record.pid -ErrorAction Stop;$sqlHeld=Hold-ProcessHandle $sqlProcess
    if($sqlProcess.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$Record.created){$sqlProcess.Dispose();throw 'TRAIN013_HEADLESS_SQL_IDENTITY'}
    $adminNonce=[guid]::NewGuid().ToString('N');$directory=Join-Path $layout.Root ('workers\'+$adminNonce);$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
    $original=Join-Path $layout.Sql 'admin.client.ini';Assert-G0PhysicalFile $original;$lines=[IO.File]::ReadAllLines($original)
    if($lines.Count -ne 5 -or ($lines[0..3] -join '|') -cne ('[client]|host=127.0.0.1|port='+$Values.sqlPort+'|user=root') -or $lines[4] -cnotmatch '^password=[0-9a-f]{64}$'){$sqlProcess.Dispose();throw 'TRAIN002_ADMIN_OPTIONS'}
    $client=Join-Path $layout.Sql ('headless-stop.'+$adminNonce+'.client.ini');[IO.File]::Copy($original,$client,$false);Assert-TrainHash $client (Get-FileHash -LiteralPath $original).Hash
    $connect=[Math]::Max(1,[Math]::Min(5,[Math]::Floor(($remaining-2)/2)));$shutdown=[Math]::Max(1,[Math]::Min([int]$Values.shutdownTimeoutSeconds,$remaining-$connect-2))
    $adminArgs=@(('"--defaults-file='+$client+'"'),'--protocol=tcp','--host=127.0.0.1',('--port='+$Values.sqlPort),'--user=root',('--connect-timeout='+$connect),('--shutdown-timeout='+$shutdown),'shutdown')
    $adminProcess=$null
    try{
        $adminProcess=Start-Process -FilePath $layout.Admin -ArgumentList $adminArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'admin.out') -RedirectStandardError (Join-Path $directory 'admin.err');$adminHeld=Hold-ProcessHandle $adminProcess
        $adminRecord=@{pid=$adminProcess.Id;created=$adminProcess.StartTime.ToUniversalTime().ToFileTimeUtc()}
        Write-TrainJson (Join-Path $directory 'process.json') @{schemaVersion=13;pid=$adminRecord.pid;created=$adminRecord.created;nonce=$adminNonce;image=$layout.Admin;imageSha=(Get-FileHash -LiteralPath $layout.Admin).Hash;clientSha=(Get-FileHash -LiteralPath $client).Hash;kind='OWN_SQL_ADMIN'}
        $timer=[Diagnostics.Stopwatch]::StartNew()
        while(!$adminProcess.WaitForExit(100) -and [DateTime]::UtcNow -lt $script:HeadlessCleanupDeadline013 -and $timer.ElapsedMilliseconds -lt $remaining*1000L){}
        if(!$adminProcess.HasExited){throw 'TRAIN013_HEADLESS_SQL_ADMIN_UNCONFIRMED'}
        Write-TrainJson (Join-Path $directory 'admin-exit.json') (Get-TrainHeldExitProof $adminRecord $adminProcess $adminHeld)
        if((Get-TrackedProcessExitCode $adminProcess $adminHeld) -ne 0){throw 'TRAIN013_HEADLESS_SQL_SHUTDOWN_ERROR'}
        while(!(Test-TrainProcessAbsent $Record $sqlProcess $sqlHeld) -and [DateTime]::UtcNow -lt $script:HeadlessCleanupDeadline013 -and $timer.ElapsedMilliseconds -lt $remaining*1000L){Start-Sleep -Milliseconds 100}
        if(!(Test-TrainProcessAbsent $Record $sqlProcess $sqlHeld)){throw 'TRAIN013_HEADLESS_SQL_DRAIN_UNCONFIRMED'}
        Write-TrainExitReceipt $layout $Record $sqlProcess $sqlHeld;Assert-TrainPortsFree @([int]$Values.sqlPort)
        Write-TrainJson (Join-Path $layout.Sql 'headless-stop013.json') @{schemaVersion=13;adminNonce=$adminNonce;adminExitProven=$true;sqlExitProven=$true;hardDeadlineUtc=$script:HeadlessCleanupDeadline013.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o')}
    }finally{if($adminProcess){$adminProcess.Dispose()};$sqlProcess.Dispose()}
}
function Assert-TrainHeadlessResult013($Context,[string]$Installation) {
    $result=Read-TrainJson (Join-Path $Context.scope 'headless-result013.json')
    if($result.nonce -cne $Context.journal.nonce -or $result.configSha -cne $Context.journal.configSha -or $result.trainingCredit -ne $false){throw 'TRAIN013_HEADLESS_RESULT_IDENTITY'}
    if($result.status -ceq 'READY'){
        $proofRoot=if($Context.journal.operation -ceq 'prepare'){$Context.scope}else{Join-Path $Installation ('autonomous\v1\prepared\'+$Context.journal.configSha+'\'+(Get-FileHash -LiteralPath (Join-Path $Installation 'game\libs\GameServer.jar')).Hash.ToLowerInvariant())}
        $names=@('cells.tsv',$(if($Context.journal.operation -ceq 'prepare'){'training-admission013.json'}else{'headless.complete'}))
        $entries=@($result.proofFiles.PSObject.Properties);if($entries.Count -ne 2){throw 'TRAIN013_HEADLESS_PROOF_COUNT'}
        foreach($name in $names){$file=Join-Path $proofRoot $name;$relative=$file.Substring($Installation.Length+1).Replace('\','/');$entry=$result.proofFiles.PSObject.Properties[$relative];if(!$entry){throw 'TRAIN013_HEADLESS_PROOF_PATH'};Assert-TrainHash $file ([string]$entry.Value)}
    }
    return $result
}
function Recover-TrainHeadless013($Context) {
    $record=$Context.journal.worker
    if(!$Context.recovered -or !$record -or $record.nonce -cne $Context.journal.nonce){throw 'TRAIN013_HEADLESS_RECOVERY_START_UNPROVEN'}
    if(Test-TrainExitReceipt $layout $record){return @{process=$null;exitFact=(Read-TrainJson (Get-TrainExitReceiptPath $layout $record))}}
    if(Test-TrainProcessAbsent $record){
        throw 'TRAIN013_HEADLESS_RECOVERY_EXIT_UNPROVEN'
    }
    Assert-TrainProcess $record -Kind Worker
    $process=Get-Process -Id $record.pid -ErrorAction Stop
    if($process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$record.created){$process.Dispose();throw 'TRAIN013_HEADLESS_RECOVERY_IDENTITY'}
    return @{process=$process;exitFact=$null}
}
function Restore-TrainHeadlessConfig013 {
    $raw=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
    if(!$raw.ContainsKey('autonomous013Version') -or $raw.autonomous013Version -cne '1'){return}
    $campaign=if($raw.ContainsKey('autonomous013ParentCampaign')){$raw.autonomous013ParentCampaign}else{$raw.autonomous009Campaign}
    $version=if(Test-TrainIncremental016 $raw){16}else{13}
    $directory=Join-Path $layout.Root ('autonomous\v'+$version+'\campaigns\'+$campaign);$basePath=Join-Path $directory 'base-config.json'
    if(Test-Path -LiteralPath $basePath){$j=Read-TrainJson (Join-Path $directory 'director-state.json');Assert-TrainHash $basePath $j.configSha;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') (Copy-TrainConfig013 (Read-TrainJson $basePath))}
}
function Recover-TrainHeadlessControl013($Dependencies,[switch]$Cancel) {
    $context=Get-TrainHeadlessActive013;if(!$context -or !$context.journal.worker){return $false}
    $configPath=Join-Path $layout.Root 'TRAIN_CONFIG.ini';$raw=Read-TrainConfig $configPath
    if(!$raw.ContainsKey('autonomous013Version') -or $raw.autonomous013Version -cne '1'){return $false}
    $scopeCampaign=$context.scope.Substring(((Join-Path $layout.Root 'autonomous\v1\campaigns')+'\').Length).Split('\')[0]
    if($raw.autonomous009Campaign -cne $scopeCampaign){return $false}
    if(Test-TrainHeadlessController013 $context.journal.worker){return $false}
    if($context.terminal){Restore-TrainHeadlessConfig013;return $false}
    if((Get-FileHash -LiteralPath $configPath).Hash.ToLowerInvariant() -cne $context.journal.configSha){throw 'TRAIN013_HEADLESS_RECOVERY_CONFIG_CHANGED'}
    $previous=$script:Config;$script:Config=$raw;$deadline=([datetime]$context.journal.hardDeadlineUtc).ToUniversalTime()
    $adoption=Recover-TrainHeadless013 $context
    if($adoption.process){$script:HeadlessAdoption013=@{nonce=$context.journal.nonce;process=$adoption.process;handle=(Hold-ProcessHandle $adoption.process)}}
    try{
        if($Cancel){$null=Update-TrainHeadless013 $context ([DateTime]::UtcNow) $true}
        Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @($context.journal.operation,$layout.Root)
    }catch{
        if(!$Cancel -or $_.Exception.Message -cnotmatch '^TRAIN013_HEADLESS_(CANCELLED|TIMEOUT)$'){throw}
    }finally{
        $script:HeadlessAdoption013=$null;if($adoption.process){$adoption.process.Dispose()}
        if(!$script:HeadlessCleanupDeadline013 -or $script:HeadlessCleanupDeadline013 -gt $deadline){$script:HeadlessCleanupDeadline013=$deadline}
        $sqlPath=Join-Path $layout.Sql 'owned-process.json'
        if(Test-Path -LiteralPath $sqlPath){$sql=Read-TrainJson $sqlPath;if(!(Test-TrainExitReceipt $layout $sql) -and !(Test-TrainProcessAbsent $sql)){Stop-TrainHeadlessSql013 $raw $sql}}
        Restore-TrainHeadlessConfig013
        $state=Read-TrainJson $context.path
        if($Cancel -or $state.status -cne 'READY'){$off=Read-TrainConfig $configPath;$off.autonomous009Enabled='false';$off.autonomous009Phase='OFF';$off.outcome006Enabled='false';Write-TrainConfig $configPath $off}
        Write-TrainJson (Join-Path $context.scope 'headless-recovery013.json') @{schemaVersion=13;nonce=$state.nonce;status=$state.status;workerExitProven=$state.exitProven;finishedUtc=[DateTime]::UtcNow.ToString('o');trainingCredit=$false}
        $script:Config=$previous
    }
    return $true
}
function Invoke-TrainJava($Dependencies,[string]$Class,[string[]]$Arguments,[int]$TimeoutSeconds=600) {
    $script:WorkerRecord=$null
    if($script:Config.ContainsKey('autonomous009EvaluationOnly') -and $script:Config.autonomous009EvaluationOnly -ceq 'true' -and $Class -ceq 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' -and $Arguments[0] -ceq 'train'){throw 'TRAIN010_EVAL_TRAINING_REFUSED'}
    $headless=$null
    if($script:Config.ContainsKey('autonomous013Version') -and $script:Config.autonomous013Version -ceq '1' -and $Class -ceq 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' -and $Arguments[0] -cin @('prepare','admit-eval')){
        $headless=Open-TrainHeadless013 $layout.Root $script:Config $Arguments[0] (Get-FileHash -LiteralPath (Join-Path $layout.Root 'TRAIN_CONFIG.ini')).Hash.ToLowerInvariant()
        $script:HeadlessCleanupDeadline013=[DateTime]::Parse($headless.journal.hardDeadlineUtc).ToUniversalTime()
        if($script:ReadinessOnly013 -and !$script:ReadinessDeadline013){$script:ReadinessDeadline013=$headless.journal}
        if($headless.terminal){
            if($headless.journal.status -cne 'READY'){Throw-TrainHeadless013 $headless}
            Assert-TrainHash (Join-Path $headless.scope 'headless-result013.json') $headless.journal.resultSha
            $null=Assert-TrainHeadlessResult013 $headless $layout.Root
            if(!$headless.journal.worker -or !(Test-TrainExitReceipt $layout $headless.journal.worker)){throw 'TRAIN013_HEADLESS_RECOVERY_EXIT_UNPROVEN'}
            if(!$script:ReadinessOnly013){$script:HeadlessCleanupDeadline013=$null};return
        }
        $recovery=$null
        if($headless.recovered){
            if($script:HeadlessAdoption013 -and $script:HeadlessAdoption013.nonce -ceq $headless.journal.nonce){
                $tracked=$script:HeadlessAdoption013;$held=Get-TrainHeldExitProof $headless.journal.worker $tracked.process $tracked.handle
                if($held.exited){Write-TrainExitReceipt $layout $headless.journal.worker $tracked.process $tracked.handle $Class;$recovery=@{process=$null;exitFact=(Read-TrainJson (Get-TrainExitReceiptPath $layout $headless.journal.worker))}}
                else{$recovery=@{process=$tracked.process;exitFact=$null}}
            }else{$recovery=Recover-TrainHeadless013 $headless}
            if($recovery.exitFact){
                $resultPath=Join-Path $headless.scope 'headless-result013.json';$nativeResult=if(Test-Path -LiteralPath $resultPath){Assert-TrainHeadlessResult013 $headless $layout.Root}else{[pscustomobject]@{status='ERROR'}}
                if(Test-Path -LiteralPath $resultPath){$headless.journal.resultSha=(Get-FileHash -LiteralPath $resultPath).Hash}
                if(!$headless.journal.reason -and ([datetime]$recovery.exitFact.terminatedUtc).ToUniversalTime() -gt [DateTime]::Parse($headless.journal.workDeadlineUtc).ToUniversalTime()){$headless.journal.reason='TIMEOUT'}
                Close-TrainHeadless013 $headless ([int]$recovery.exitFact.exitCode) $nativeResult.status $true
                if($headless.journal.status -cne 'READY'){Throw-TrainHeadless013 $headless}
                if(!$script:ReadinessOnly013){$script:HeadlessCleanupDeadline013=$null};return
            }
        }
        $null=Update-TrainHeadless013 $headless ([DateTime]::UtcNow) $false
        if(!$headless.recovered -and $headless.journal.reason){Close-TrainHeadless013 $headless -1 $headless.journal.reason $true;Throw-TrainHeadless013 $headless}
    }
    $workerId=if($headless){$headless.journal.nonce}else{[guid]::NewGuid().ToString('N')};$directory=Join-Path $layout.Root ('workers\'+$workerId)
    $null=[IO.Directory]::CreateDirectory($directory)
    $javaArguments=@(Get-TrainJavaArguments $Class $Arguments $layout.ClassPath $workerId ([int]$script:Config.cpuCount) ([int]$script:Config.maxHeapMb))
    if($headless){$javaArguments=@('-Dphantom.training.headless013=true')+$javaArguments}
    if($script:Config.ContainsKey('autonomous009FaultOnce') -and $script:Config.autonomous009FaultOnce -ceq 'true' -and $Class -ceq 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' -and $Arguments[0] -ceq 'train'){
        if($script:Config.autonomous009Phase -cne 'FIT'){throw 'TRAIN009_FAULT_OFFLINE_FIT_ONLY'}
        $javaArguments=@('-Dphantom.learning009.preCommitFault=true')+$javaArguments
    }
    if($script:Config.ContainsKey('exploration007FaultOnce') -and $script:Config.exploration007FaultOnce -ceq 'true' -and $Class -ceq 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' -and $Arguments[0] -ceq 'train'){
        if($script:Config.exploration007Phase -cne 'FIT'){throw 'TRAIN007_FAULT_OFFLINE_FIT_ONLY'}
        $javaArguments=@('-Dphantom.learning007.preCommitFault=true')+$javaArguments
    }
    if($headless -and $headless.recovered){
        $process=$recovery.process
    }else{$process=Start-Process -FilePath $Dependencies.Java -ArgumentList $javaArguments -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'worker.out') -RedirectStandardError (Join-Path $directory 'worker.err')}
    $handle=Hold-ProcessHandle $process
    $record=$null
    $workerResources=Get-TrainProcessResources $process
    try{
    $record=if($headless -and $headless.recovered){$headless.journal.worker}else{New-TrainProcessRecord $process $Dependencies.Java $workerId Worker (Join-Path $layout.Root 'TRAIN_CONFIG.ini')}
    $script:WorkerRecord=$record
    Write-TrainJson (Join-Path $directory 'process.json') $record
    Write-TrainJson (Join-Path $layout.Root 'worker-process.json') $record
    if($headless){$headless.journal.worker=$record;Write-TrainJson $headless.path $headless.journal;Write-TrainJson (Join-Path $layout.Root 'headless-active013.json') @{scope=$headless.scope;nonce=$workerId}}
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $timer=[Diagnostics.Stopwatch]::StartNew();$remainingWork=if($headless){[Math]::Max(0,([DateTime]::Parse($headless.journal.workDeadlineUtc).ToUniversalTime()-[DateTime]::UtcNow).TotalMilliseconds)}else{0}
    $remainingHard=if($headless){[Math]::Max(0,($script:HeadlessCleanupDeadline013-[DateTime]::UtcNow).TotalMilliseconds)}else{0};$drainElapsed=-1L
    while(!$process.WaitForExit(1000)){
        $workerResources=Get-TrainProcessResources $process $workerResources
        Assert-TrainAutonomousResources
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
        if($headless){
            $cancelled=(Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')
            $now=[DateTime]::UtcNow
            if($timer.ElapsedMilliseconds -ge $remainingWork -and !$headless.journal.reason){$now=[DateTime]::Parse($headless.journal.workDeadlineUtc).ToUniversalTime()}
            $null=Update-TrainHeadless013 $headless $now $cancelled
            if($headless.journal.reason -and $drainElapsed -lt 0){$drainElapsed=$timer.ElapsedMilliseconds;$script:HeadlessCleanupDeadline013=([datetime]$headless.journal.drainDeadlineUtc).ToUniversalTime()}
            if([DateTime]::UtcNow -ge $script:HeadlessCleanupDeadline013 -or $timer.ElapsedMilliseconds -ge $remainingHard -or ($drainElapsed -ge 0 -and $timer.ElapsedMilliseconds-$drainElapsed -ge 210000) -or $headless.journal.status -ceq 'DRAIN_UNCONFIRMED'){throw 'TRAIN013_HEADLESS_DRAIN_UNCONFIRMED'}
        }elseif([DateTime]::UtcNow -gt $deadline){throw 'TRAIN002_WORKER_TIMEOUT_NO_FORCE'}
    }
    $exit=Get-TrackedProcessExitCode $process $handle
    $workerResources=Get-TrainProcessResources $process $workerResources
    Write-TrainJson (Join-Path $directory 'resources.json') $workerResources
    Assert-TrainHash $record.image $record.imageHash
    Assert-TrainHash $record.configPath $record.configHash
    Write-TrainExitReceipt $layout $record $process $handle $Class
    if($headless){
        $null=Update-TrainHeadless013 $headless ([DateTime]::UtcNow) $false
        $resultPath=Join-Path $headless.scope 'headless-result013.json';$nativeStatus='ERROR'
        if(Test-Path -LiteralPath $resultPath){$nativeResult=Assert-TrainHeadlessResult013 $headless $layout.Root;$nativeStatus=$nativeResult.status;$headless.journal.resultSha=(Get-FileHash -LiteralPath $resultPath).Hash}
        Close-TrainHeadless013 $headless $exit $nativeStatus (Test-TrainExitReceipt $layout $record)
        if($headless.journal.status -cne 'READY'){Throw-TrainHeadless013 $headless}
        if(!$script:ReadinessOnly013){$script:HeadlessCleanupDeadline013=$null}
    }
    if($exit -ne 0){throw ('TRAIN002_WORKER_FAILED:'+ $Class)}
    $script:WorkerRecord=$null
    }catch{
        $primary=$_
        if($headless -and !$headless.terminal){
            try{
                $headless.journal.reason=if($headless.journal.reason){$headless.journal.reason}else{'ERROR'};$null=Update-TrainHeadless013 $headless ([DateTime]::UtcNow) $false
                $script:HeadlessCleanupDeadline013=([datetime]$headless.journal.drainDeadlineUtc).ToUniversalTime();$drainTimer=[Diagnostics.Stopwatch]::StartNew()
                while(!$process.HasExited -and [DateTime]::UtcNow -lt $script:HeadlessCleanupDeadline013 -and $drainTimer.ElapsedMilliseconds -lt 210000){$null=$process.WaitForExit(250)}
                if($process.HasExited -and $record){
                    Write-TrainExitReceipt $layout $record $process $handle $Class
                    Write-TrainJson (Join-Path $directory 'resources.json') (Get-TrainProcessResources $process $workerResources)
                    Close-TrainHeadless013 $headless (Get-TrackedProcessExitCode $process $handle) 'ERROR' (Test-TrainExitReceipt $layout $record)
                }else{$headless.journal.status='DRAIN_UNCONFIRMED';Write-TrainJson $headless.path $headless.journal}
            }catch{$null=Save-TrainFailure $layout $script:State $_ 'HEADLESS_CLEANUP'}
        }
        if(!$primary.Exception.Data.Contains('pid')){$primary.Exception.Data['pid']=$process.Id}
        $null=Save-TrainFailure $layout $script:State $primary 'WORKER'
        throw $primary
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
function Invoke-Train007Native($Dependencies,[string]$Jar) {
    $campaign=Join-Path $layout.Root 'outcome006\campaigns\learning007-causal-v1'
    $budgetPath=Join-Path $campaign 'budget.json'
    if(Test-Path -LiteralPath $budgetPath){$budget=Read-TrainJson $budgetPath}else{
        $budget=[pscustomobject]@{schemaVersion=7;starts=0;collectionStarts=0;wallMillis=0L;collectionWallMillis=0L;pending=$false;pendingStartedUtc='';lastAttempt='';conservativeRecovery=$false}
    }
    if($budget.pending){throw 'TRAIN007_PENDING_NATIVE_RESERVATION_REQUIRES_OWN_EXIT_AUDIT'}
    $collection=$script:Config.exploration007Phase -ceq 'COLLECT'
    if([int]$budget.starts -ge 8 -or [long]$budget.wallMillis -ge 5400000 -or ($collection -and ([int]$budget.collectionStarts -ge 3 -or [long]$budget.collectionWallMillis -ge 3600000))){throw 'TRAIN007_NATIVE_BUDGET_EXHAUSTED'}
    $budget.starts=[int]$budget.starts+1;if($collection){$budget.collectionStarts=[int]$budget.collectionStarts+1}
    $budget.pending=$true;$budget.pendingStartedUtc=[DateTime]::UtcNow.ToString('o');Write-TrainJson $budgetPath $budget
    $budgetTimer=[Diagnostics.Stopwatch]::StartNew()
    try{Invoke-TrainNative $Dependencies $Jar}finally{
        $nativePath=Join-Path $layout.Root 'native-process.json'
        if((Test-Path -LiteralPath $nativePath) -and (Test-TrainProcessAbsent (Read-TrainJson $nativePath))){
            $elapsed=[long]$budgetTimer.ElapsedMilliseconds
            $budget.wallMillis=[long]$budget.wallMillis+$elapsed;if($collection){$budget.collectionWallMillis=[long]$budget.collectionWallMillis+$elapsed}
            $budget.lastAttempt=$script:State.attemptId;$budget.pending=$false;Write-TrainJson $budgetPath $budget
        }
    }
}
function Invoke-Train008Native($Dependencies,[string]$Jar) {
    $budgetPath=Join-Path $layout.Root 'outcome006\campaigns\learning008-pve-generalization-v1\budget.json'
    if(Test-Path -LiteralPath $budgetPath){$budget=Read-TrainJson $budgetPath}else{
        $budget=[pscustomobject]@{schemaVersion=8;starts=0;collectionStarts=0;wallMillis=0L;collectionWallMillis=0L;pending=$false;pendingStartedUtc='';lastAttempt='';conservativeRecovery=$false}
    }
    if($budget.schemaVersion -ne 8 -or $budget.pending){throw 'TRAIN008_PENDING_NATIVE_RESERVATION_REQUIRES_OWN_EXIT_AUDIT'}
    $collection=$script:Config.exploration008Phase -ceq 'COLLECT'
    $recovery=$script:Config.ContainsKey('exploration008Recovery') -and $script:Config.exploration008Recovery -ceq 'true'
    $minimumSlotMillis=([long]$script:Config.startupTimeoutSeconds+[long]$script:Config.preparationSeconds+[long]$script:Config.episodeSeconds+[long]$script:Config.shutdownTimeoutSeconds+150L)*1000L
    if([int]$budget.starts -ge 8 -or [long]$budget.wallMillis+$minimumSlotMillis -ge 5400000 -or ($collection -and (([int]$budget.collectionStarts -ge 3 -and (!$recovery -or [int]$budget.collectionStarts -ge 4)) -or [long]$budget.collectionWallMillis+$minimumSlotMillis -ge 3600000))){throw 'TRAIN008_NATIVE_BUDGET_EXHAUSTED_WITH_BOOT_SETTLEMENT_RESERVE'}
    $budget.starts=[int]$budget.starts+1;if($collection){$budget.collectionStarts=[int]$budget.collectionStarts+1}
    $budget | Add-Member -MemberType NoteProperty -Name recoveryStarts -Value ([Math]::Max(0,[int]$budget.collectionStarts-3)) -Force
    $budget.pending=$true;$budget.pendingStartedUtc=[DateTime]::UtcNow.ToString('o');Write-TrainJson $budgetPath $budget
    $budgetTimer=[Diagnostics.Stopwatch]::StartNew()
    $remainingMillis=5400000L-[long]$budget.wallMillis
    if($collection){$remainingMillis=[Math]::Min($remainingMillis,3600000L-[long]$budget.collectionWallMillis)}
    $nativeDeadline=[DateTime]::UtcNow.AddMilliseconds($remainingMillis-([long]$script:Config.shutdownTimeoutSeconds+30L)*1000L)
    try{Invoke-TrainNative $Dependencies $Jar -BudgetDeadline $nativeDeadline}finally{
        # Charge teardown as well, including a failed boot/timeout. Unconfirmed exit leaves pending fail-closed.
        Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
        $nativePath=Join-Path $layout.Root 'native-process.json'
        if((Test-Path -LiteralPath $nativePath) -and (Test-TrainProcessAbsent (Read-TrainJson $nativePath))){
            $elapsed=[long]$budgetTimer.ElapsedMilliseconds;$budget.wallMillis=[long]$budget.wallMillis+$elapsed
            if($collection){$budget.collectionWallMillis=[long]$budget.collectionWallMillis+$elapsed}
            $budget.lastAttempt=$script:State.attemptId;$budget.pending=$false;Write-TrainJson $budgetPath $budget
        }
    }
}
function Wait-TrainSqlReady($Record) {
    $client=Join-Path $layout.Sql 'admin.client.ini'
    Assert-G0PhysicalFile $client;Assert-G0PhysicalFile $layout.Admin
    $probe=[guid]::NewGuid().ToString('N')
    $out=Join-Path $layout.Root ('logs\sql-ready-'+$probe+'.out')
    $err=Join-Path $layout.Root ('logs\sql-ready-'+$probe+'.err')
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {
        Assert-TrainProcess $Record -Kind Sql -Port ([int]$script:Config.sqlPort)
        $process=Start-Process -FilePath $layout.Admin -ArgumentList @(('--defaults-extra-file="'+$client+'"'),'--connect-timeout=2','--silent','ping') -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
        $handle=Hold-ProcessHandle $process
        try {
            if(!$process.WaitForExit(5000)){throw 'TRAIN002_SQL_READY_PROBE_NOT_STOPPED'}
            if((Get-TrackedProcessExitCode $process $handle) -eq 0){
                Assert-TrainProcess $Record -Kind Sql -Port ([int]$script:Config.sqlPort)
                return
            }
        } finally {$process.Dispose()}
        Start-Sleep -Milliseconds 200
    } while([DateTime]::UtcNow -lt $deadline)
    throw 'TRAIN002_SQL_READY_TIMEOUT'
}
function Start-TrainSql($Dependencies) {
    $initialized=Join-Path $layout.Sql 'initialized.json'
    $receipt=Join-Path $layout.Sql 'owned-process.json'
    if(Test-Path -LiteralPath $receipt){
        $existing=Read-TrainJson $receipt
        if(!(Test-TrainProcessAbsent $existing)){Assert-TrainSqlOptions $layout $script:Config $existing;Assert-TrainProcess $existing -Kind Sql -Port ([int]$script:Config.sqlPort);$script:SqlRecord=$existing;Wait-TrainSqlReady $existing;return $existing}
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
    $script:SqlRecord=$record
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while(!@([Win32SqlIdentity]::Listeners([int]$script:Config.sqlPort)).Count -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 200}
    Assert-TrainSqlOptions $layout $script:Config $record;Assert-TrainProcess $record -Kind Sql -Port ([int]$script:Config.sqlPort)
    Wait-TrainSqlReady $record
    return $record
}
function Stop-TrainNative([int]$Seconds,[Diagnostics.Process]$TrackedProcess=$null,[object]$TrackedHandle=$null) {
    $path=Join-Path $layout.Root 'native-process.json'
    if(!(Test-Path -LiteralPath $path)){return}
    $record=Read-TrainJson $path
    if($TrackedProcess){
        if((Get-TrainHeldExitProof $record $TrackedProcess $TrackedHandle).exited){Write-TrainExitReceipt $layout $record $TrackedProcess $TrackedHandle;return}
    }elseif(Test-TrainProcessAbsent $record){return}
    try{Assert-TrainProcess $record -Kind Native}catch{
        if($TrackedProcess -and (Get-TrainHeldExitProof $record $TrackedProcess $TrackedHandle).exited){Write-TrainExitReceipt $layout $record $TrackedProcess $TrackedHandle;return}
        throw
    }
    $process=$TrackedProcess;$handle=$TrackedHandle;$dispose=$false
    if(!$process){$process=Get-Process -Id $record.pid -ErrorAction Stop;$handle=Hold-ProcessHandle $process;$dispose=$true}
    try{
        $null=Get-TrainHeldExitProof $record $process $handle
        Write-TrainText (Join-Path $layout.Root ('attempts\'+$record.nonce+'\stop.request')) $script:DirectorNonce
        $timer=[Diagnostics.Stopwatch]::StartNew()
        do{
            if(Test-TrainProcessAbsent $record $process $handle){Write-TrainExitReceipt $layout $record $process $handle;return}
            Publish-TrainState 'DRAINING';Start-Sleep -Milliseconds 200
        }while($timer.Elapsed.TotalSeconds -lt $Seconds)
        throw 'TRAIN002_NATIVE_STOP_UNCONFIRMED_SQL_RETAINED'
    }finally{if($dispose){$process.Dispose()}}
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
function Invoke-TrainNative($Dependencies,[string]$JarSha,[datetime]$BudgetDeadline=[datetime]::MaxValue) {
    Reset-TrainColdBaseline
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    Assert-TrainProcess $script:SqlRecord -Kind Sql -Port ([int]$script:Config.sqlPort)
    $attempt=if($script:AutonomousCheckpointContext -and $script:AutonomousCheckpointContext.journal.schemaVersion -eq 13){$script:AutonomousCheckpointContext.reservationId}else{[guid]::NewGuid().ToString('N')};$directory=Join-Path $layout.Root ('attempts\'+$attempt)
    if(Test-Path -LiteralPath $directory){throw 'TRAIN013_RESERVED_NATIVE_ATTEMPT_ALREADY_EXISTS'}
    $null=[IO.Directory]::CreateDirectory($directory)
    $script:State.attemptId=$attempt;Publish-TrainState 'NATIVE_BOOT'
    $config=Join-Path $layout.Root 'TRAIN_CONFIG.ini'
    $arguments=@(('-XX:ActiveProcessorCount='+$script:Config.cpuCount),'-Xms512m',('-Xmx'+$script:Config.maxHeapMb+'m'),'-Dphantom.learning.train=true','-Dphantom.learning.witness=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',('"'+$layout.ClassPath+'"'),'org.l2jmobius.tests.phantoms.PhantomTrainingNative',('"'+$layout.Root+'"'),$attempt,$JarSha,('"'+$config+'"'))
    $process=Start-Process -FilePath $Dependencies.Java -ArgumentList $arguments -WorkingDirectory $layout.Game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory 'native.out') -RedirectStandardError (Join-Path $directory 'native.err')
    $handle=Hold-ProcessHandle $process
    $record=New-TrainProcessRecord $process $Dependencies.Java $attempt Native $config
    $script:NativeRecord=$record;Write-TrainJson (Join-Path $layout.Root 'native-process.json') $record
    $resources=Get-TrainProcessResources $process
    if($script:AutonomousCheckpointContext){
        $journal=$script:AutonomousCheckpointContext.journal
        $reservation=Join-Path $directory 'budget-reservation.json'
        if(Test-Path -LiteralPath $reservation){throw 'TRAIN010_NATIVE_RESERVATION_EXISTS'}
        Write-TrainJson $reservation @{campaign=$journal.campaign;attemptId=$attempt;pid=$record.pid;created=$record.created;bootUtc=[DateTime]::FromFileTimeUtc([long]$record.created).ToString('o');reservedUtc=$journal.pendingStartedUtc;startOrdinal=$journal.starts;priorNativeMillis=$journal.nativeMillis}
    }
    try{
    $ready=Join-Path $directory 'game.ready';$deadline=[DateTime]::UtcNow.AddSeconds([int]$script:Config.startupTimeoutSeconds)
    while(!$process.HasExited -and !(Test-Path -LiteralPath $ready)){
        $resources=Get-TrainProcessResources $process $resources
        Publish-TrainState 'NATIVE_BOOT'
        if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce}
        if([DateTime]::UtcNow -ge $BudgetDeadline -or ($script:AutonomousCheckpointContext -and $script:AutonomousCheckpointContext.timer.ElapsedMilliseconds -ge $script:AutonomousCheckpointContext.nativeBudgetMillis)){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce;throw 'TRAIN008_NATIVE_BOOT_BUDGET_DEADLINE'}
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
        Assert-TrainAutonomousResources
        Publish-TrainState 'NATIVE'
        Save-TrainAutonomousProgressCheckpoint
        if(Test-TrainRequest 'stop'){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce}
        if(Test-TrainRequest 'pause'){Write-TrainText (Join-Path $directory 'pause.request') $script:DirectorNonce}
        if([DateTime]::UtcNow -ge $BudgetDeadline -or ($script:AutonomousCheckpointContext -and $script:AutonomousCheckpointContext.timer.ElapsedMilliseconds -ge $script:AutonomousCheckpointContext.nativeBudgetMillis)){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce;throw 'TRAIN008_NATIVE_BUDGET_DEADLINE'}
        if([DateTime]::UtcNow -gt $deadline){Write-TrainText (Join-Path $directory 'stop.request') $script:DirectorNonce;throw 'TRAIN002_NATIVE_BATCH_TIMEOUT'}
    }
    $exit=Get-TrackedProcessExitCode $process $handle
    Assert-TrainPortsFree @([int]$script:Config.gamePort,[int]$script:Config.loginPort)
    Assert-TrainHash (Join-Path $layout.Game 'libs\GameServer.jar') $JarSha
    Write-TrainExitReceipt $layout $record $process $handle
    if($exit -ne 0 -or !(Test-Path -LiteralPath (Join-Path $directory 'batch.complete'))){
        $failure=Join-Path $directory 'batch.failed'
        if($script:Config.ContainsKey('autonomous009Version') -and (Test-Path -LiteralPath $failure) -and [IO.File]::ReadAllText($failure) -match 'AUTONOMOUS_ACTOR_CAPACITY_OR_SCOPE'){throw 'TRAIN009_RESOURCE_ACTOR_CAPACITY_OR_SCOPE'}
        throw 'TRAIN002_NATIVE_BATCH_INCOMPLETE'
    }
    $script:State.batchesCompleted=[int]$script:State.batchesCompleted+1
    $script:NativeRecord=$null
    }finally{
        try{Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds) $process $handle}finally{
            $resources=Get-TrainProcessResources $process $resources
            $resources['attemptId']=$attempt;$resources['observedUtc']=[DateTime]::UtcNow.ToString('o');$resources['finishedUtc']=$null
            $held=Get-TrainHeldExitProof $record $process $handle
            if($held.exited){$resources['finishedUtc']=$held.terminatedUtc;$resources['elapsedMillis']=$held.nativeElapsedMillis;$resources['exitObserved']=$true;$resources['exitCode']=$held.exitCode}
            Write-TrainJson (Join-Path $directory 'native-resources.json') $resources
            $process.Dispose()
        }
    }
}
function Assert-TrainAutonomousResources {
    if(!$script:Config -or !$script:Config.ContainsKey('autonomous009Version')){return}
    $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($layout.Root));$free=[long]$drive.AvailableFreeSpace
    if($free -lt [long]$script:Config.minFreeDiskMb*1048576L){throw 'TRAIN009_RESOURCE_DISK_LIMIT'}
    $memory=[long][Diagnostics.Process]::GetCurrentProcess().WorkingSet64
    foreach($record in @($script:NativeRecord,$script:WorkerRecord,$script:SqlRecord)){
        if(!$record){continue}
        $owned=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
        if($owned){try{if($owned.StartTime.ToUniversalTime().ToFileTimeUtc() -eq [long]$record.created){$memory+=$owned.WorkingSet64}}finally{$owned.Dispose()}}
    }
    if($memory -gt [long]$script:Config.autonomous009MemoryBudgetMb*1048576L){throw 'TRAIN009_RESOURCE_MEMORY_LIMIT'}
    if($script:State){$script:State['freeDiskBytes']=$free;$script:State['ownedWorkingSetBytes']=$memory}
}
function Save-TrainAutonomousProgressCheckpoint {
    $context=$script:AutonomousCheckpointContext
    if(!$context -or ([DateTime]::UtcNow-$context.lastUtc).TotalMinutes -lt [int]$script:Config.autonomous009CheckpointMinutes){return}
    $directory=Join-Path (Split-Path $context.journalPath -Parent) 'progress-checkpoints';$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
    $path=Join-Path $directory ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ')+'.json')
    $coldPath=Join-Path $layout.Root 'autonomous\v1\cold-checkpoint.json';$cold=Read-TrainJson $coldPath
    Write-TrainJson $path @{schemaVersion=9;kind='INFLIGHT_PROGRESS_REFERENCE';modelChangedByCheckpoint=$false;phase=$context.journal.phase;dataset=$context.journal.dataset;attemptId=$script:State.attemptId;activeModelVersion=$script:Config.autonomous009ModelVersion;activeModelSha=$script:Config.autonomous009ModelSha;lastVerifiedColdCheckpoint=$cold;directorGeneration=$script:State.generation;completedNativeMillis=$context.journal.nativeMillis;pendingStartedUtc=$context.journal.pendingStartedUtc;nativeProcess=$script:NativeRecord;createdUtc=[DateTime]::UtcNow.ToString('o');reservedSlotsNeverReplayed=$true}
    $context.journal.lastCheckpoint=$path;Write-TrainJson $context.journalPath $context.journal;$context.lastUtc=[DateTime]::UtcNow
}
function Invoke-TrainAutonomousNative($Dependencies,[string]$Jar,$Journal,[string]$JournalPath) {
    Assert-TrainAutonomousResources
    $smoke=$script:Config.autonomous009AcceptanceSmoke -ceq 'true'
    $loop013=$Journal.schemaVersion -eq 13
    $nativeLimit=if($loop013){[long]$Journal.maximumNativeMillis}elseif($script:Config.autonomous009EvaluationOnly -ceq 'true'){[long]$script:Config.autonomous009SmokeMaxNativeMinutes*60000L}else{5400000L}
    $startLimit=if($loop013){[int]$Journal.maximumStarts}else{[int]$script:Config.autonomous009SmokeMaxStarts}
    $reserve=([long]$script:Config.shutdownTimeoutSeconds+30L)*1000L
    if($smoke -and ([int]$Journal.starts -ge $startLimit -or [long]$Journal.nativeMillis+$reserve -ge $nativeLimit)){throw 'TRAIN009_NATIVE_BUDGET_EXHAUSTED'}
    if($script:Config.autonomous009GuiHoldBeforeNative -ceq 'true'){
        if(!$smoke){throw 'TRAIN009_GUI_HOLD_REQUIRES_ACCEPTANCE'}
        $holdDeadline=[DateTime]::UtcNow.AddMinutes(3)
        while(!(Test-TrainRequest 'stop') -and !(Test-TrainRequest 'pause')){
            if([DateTime]::UtcNow -ge $holdDeadline){throw 'TRAIN009_GUI_HOLD_TIMEOUT'}
            Assert-TrainAutonomousResources
            Publish-TrainState 'GUI_HOLD' 'Acceptance: проверка кнопок до запуска GameServer; native budget не расходуется.'
            Start-Sleep -Milliseconds 500
        }
        return
    }
    if($Journal.pending){throw 'TRAIN009_PENDING_NATIVE_REQUIRES_EXIT_RECOVERY'}
    $goal=if(Test-TrainIncremental016 $script:Config){16}else{13}
    $reservationId='';$budgetPath=Join-Path $layout.Root ('autonomous\v'+$goal+'\native-budget.json');$scopeSpent=0L
    if($loop013){
        $scope=if($goal -eq 16){$script:Config.outcome006DatasetId}else{$script:Config.autonomous009Campaign}
        $global=Reserve-TrainNativeBudget013 $budgetPath $scope ($script:Config.autonomous009EvaluationOnly -ceq 'true') $reserve $goal
        $reservationId=$global.pending;$reserved=@($global.reservations|Where-Object {$_.attemptId -ceq $reservationId})[0]
        $Journal.starts=[int]$global.starts;$Journal.nativeMillis=[long]$global.nativeMillis;$Journal.pendingStartedUtc=$reserved.startedUtc
        foreach($row in @($global.reservations|Where-Object {$_.scope -ceq $reserved.scope -and $null -ne $_.chargedMillis})){$scopeSpent+=[long]$row.chargedMillis}
    }else{$Journal.starts=[int]$Journal.starts+1;$Journal.pendingStartedUtc=[DateTime]::UtcNow.ToString('o')}
    $Journal.pending=$true;Write-TrainJson $JournalPath $Journal
    $timer=[Diagnostics.Stopwatch]::StartNew()
    $available=$nativeLimit-[long]$Journal.nativeMillis-$reserve
    if($loop013 -and $script:Config.autonomous009EvaluationOnly -ceq 'true'){$available=[Math]::Min($available,2100000L-$scopeSpent-$reserve)}
    $script:AutonomousCheckpointContext=@{journal=$Journal;journalPath=$JournalPath;lastUtc=[DateTime]::UtcNow;timer=$timer;nativeBudgetMillis=$available;reservationId=$reservationId}
    $deadline=if($smoke){[DateTime]::UtcNow.AddMilliseconds($available)}else{[DateTime]::Parse($Journal.sessionStartedUtc).ToUniversalTime().AddMinutes([int]$script:Config.autonomous009DurationMinutes)}
    try{Invoke-TrainNative $Dependencies $Jar -BudgetDeadline $deadline}finally{
        Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)
        $script:AutonomousCheckpointContext=$null
        $path=Join-Path $layout.Root 'native-process.json'
        if((Test-Path -LiteralPath $path) -and (Test-TrainProcessAbsent (Read-TrainJson $path))){
            $record=Read-TrainJson $path
            if($loop013 -and $record.nonce -cne $reservationId){throw 'TRAIN013_RESERVED_NATIVE_EXIT_NOT_ATTESTED'}
            $exitPath=Get-TrainExitReceiptPath $layout $record;$elapsed=[long]$timer.ElapsedMilliseconds;$proof='MONOTONIC_CONSERVATIVE'
            if((Test-TrainExitReceipt $layout $record)){
                $exitFact=Read-TrainJson $exitPath
                if($exitFact.PSObject.Properties['nativeElapsedMillis']){$elapsed=[long]$exitFact.nativeElapsedMillis;$proof='OWN_HELD_HANDLE_EXIT_TIME'}
            }
            if(($loop013 -or $script:Config.autonomous009EvaluationOnly -ceq 'true') -and $proof -cne 'OWN_HELD_HANDLE_EXIT_TIME'){throw 'TRAIN010_NATIVE_TIME_UNKNOWN'}
            $charge=Join-Path $layout.Root ('attempts\'+$record.nonce+'\budget-charge.json')
            if(Test-Path -LiteralPath $charge){throw 'TRAIN010_NATIVE_CHARGE_ALREADY_RECORDED'}
            Write-TrainJson $charge @{campaign=$Journal.campaign;attemptId=$record.nonce;pid=$record.pid;created=$record.created;chargedMillis=$elapsed;monotonicUpperMillis=$timer.ElapsedMilliseconds;proof=$proof;priorNativeMillis=$Journal.nativeMillis;maximumNativeMillis=$nativeLimit}
            if($loop013){$global=Complete-TrainNativeBudget013 $budgetPath $record.nonce $elapsed $proof $goal;$Journal.nativeMillis=[long]$global.nativeMillis;$Journal.starts=[int]$global.starts}
            else{$Journal.nativeMillis=[long]$Journal.nativeMillis+$elapsed}
            $Journal.pending=$false;$Journal.lastAttempt=$script:State.attemptId;Write-TrainJson $JournalPath $Journal
        }
    }
}
function Get-TrainControlSource013([string]$Directory=$PSScriptRoot) {
    $names=@('Training.ps1','TrainingOwner.ps1','TrainingBroker.ps1','TrainingBootstrap.ps1','TrainingGui.cs','TrainingGui.Designer.cs','TrainingJobProbe.cs','AutonomousTraining.ini')
    $lines=@($names|ForEach-Object {$file=Join-Path $Directory $_;Assert-G0PhysicalFile $file;$_+':'+(Get-FileHash -LiteralPath $file).Hash})
    $sha=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
}
function Invoke-TrainRepairClosure017([string]$TrainRoot,[string]$OutputRoot,[string]$ColdPath) {
    foreach($path017 in @($TrainRoot,$OutputRoot,$ColdPath)){if(!$path017 -or ![IO.Path]::IsPathRooted($path017)){throw 'TRAIN017_ABSOLUTE_REPAIR_PATH_REQUIRED'}}
    $train=[IO.Path]::GetFullPath($TrainRoot).TrimEnd('\');Assert-TrainDirectory $train
    $clone=Split-Path (Split-Path (Split-Path $train -Parent) -Parent) -Parent
    if((Join-Path $clone '.phantom-local\learning002\train') -ine $train){throw 'TRAIN017_REPAIR_INSTALLATION_SCOPE'}
    $executor=Get-TrainLayout
    $output=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
    foreach($protectedRoot in @($clone,(Split-Path $executor.Clone -Parent))){
        if($output -ieq $protectedRoot -or $output.StartsWith($protectedRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'TRAIN017_REPAIR_OUTPUT_PROTECTED'}
    }
    if((Split-Path $output -Leaf) -cne 'repair-closure017'){throw 'TRAIN017_REPAIR_OUTPUT_SCOPE'}
    $layout=@{Root=$train;ExpectedRoot=$train;Clone=$clone;Module=(Join-Path $clone 'L2J_Mobius_CT_2.6_HighFive');Game=(Join-Path $train 'game');Sql=(Join-Path $train 'sql')}
    Assert-TrainInstallation $layout
    $inputLock=Join-Path $train 'director.lock';Assert-G0PhysicalFile $inputLock
    try{$inputLease=[IO.FileStream]::new($inputLock,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)}catch{throw 'TRAIN017_REPAIR_INPUT_LEASE_UNAVAILABLE'}
    try{
    $config=Read-TrainConfig (Join-Path $train 'TRAIN_CONFIG.ini')
    if(!(Test-TrainIncremental016 $config) -or $config.autonomous009Enabled -cne 'false' -or $config.autonomous009Phase -cne 'OFF' -or $config.outcome006Enabled -cne 'false'){throw 'TRAIN017_REPAIR_REQUIRES_OFF'}
    foreach($record in @('owner-process.json','worker-process.json','native-process.json','sql\owned-process.json')){
        if(!(Test-TrainProcessAbsent (Read-TrainJson (Join-Path $train $record)) -ReadOnlyLayout $layout)){throw 'TRAIN017_REPAIR_PROCESS_PRESENT'}
    }
    Assert-TrainPortsFree @([int]$config.sqlPort,[int]$config.gamePort,[int]$config.loginPort)
    $campaign=Join-Path $train ('autonomous\v16\campaigns\'+$config.autonomous009Campaign)
    # Exact accepted frozen016 inputs, not self-attested replacement journals/checkpoints.
    $historical017=[ordered]@{
        'director-state.json'='331CFECF66B1A82AB2BBA74E16C76195D9B4DF1388399E0B88627EC8ECD444E7'
        'base-config.json'='94956032AA6076803162E9DD17EBCD217E2DD4651009A06D1ECA2A1F6F7D8ECC'
        'cycles\c000001\checkpoint016.json'='12284BC08DC3CF2A92D1C410068B93E04A1A92E3170077A0B872E5DA43F06B52'
        'cycles\c000001\fit-reserved016.json'='3D3A5EF2FB3839C9A63FCD6999EF9AF84B96C1B61E3F47EDC91DDD65C10941BA'
        'cycles\c000001\fit-result.json'='10E69B2C258FF39F6014B872358754C2C9C9E27692C8B3B79A914AD2D8BE6BD5'
    }
    foreach($entry in $historical017.GetEnumerator()){Assert-TrainHash (Join-Path $campaign $entry.Key) $entry.Value}
    Assert-TrainHash (Join-Path $train 'autonomous\v16\native-budget.json') '8846F368FE6BC3E3AA580701E3C751E58F02DD6E159F765652C9A3B5B3D163E0'
    Assert-TrainHash (Join-Path $train 'autonomous\v16\generation2-issued.json') '8F9A23F485E784494F292C292F96111A4C54A76D493F56360C5FA1129D26E98F'
    $journalPath=Join-Path $campaign 'director-state.json';$journal=Read-TrainJson $journalPath
    if($journal.phase -cne 'FIT'){throw 'TRAIN017_REPAIR_HISTORICAL_PHASE'}
    $proof=Get-TrainNoFitCompletion016 $campaign $journal
    $basePath=Join-Path $campaign 'base-config.json';Assert-TrainHash $basePath $journal.configSha
    $base=Read-TrainJson $basePath
    if($config.Count -ne @($base.PSObject.Properties).Count){throw 'TRAIN017_REPAIR_CONFIG_DRIFT'}
    foreach($property in $base.PSObject.Properties){
        if($property.Name -cnotin @('autonomous009Enabled','autonomous009Phase','outcome006Enabled') -and $config[$property.Name] -cne $property.Value){throw 'TRAIN017_REPAIR_CONFIG_DRIFT'}
    }
    $build=Read-TrainJson (Join-Path $train 'build.json')
    if($build.sourceFingerprint -cne $journal.sourceSha -or $build.jarSha.ToLowerInvariant() -cne $journal.jarSha -or
       (Get-TrainSourceFingerprint) -cne $journal.sourceSha -or
       (Get-TrainControlSource013 (Join-Path $layout.Module 'tools\phantom-runtime\training')) -cne $journal.controlSourceSha){throw 'TRAIN017_REPAIR_FROZEN_SOURCE_DRIFT'}
    Assert-TrainHash (Join-Path $layout.Module 'dist\libs\GameServer.jar') $journal.jarSha
    Assert-TrainHash (Join-Path $layout.Game 'libs\GameServer.jar') $journal.jarSha
    Assert-TrainHash (Join-Path $train 'owner.txt') $journal.ownerSha
    Assert-TrainHash (Join-Path $train 'geo203.json') $journal.geoSha
    Assert-TrainHash (Join-Path $train 'protected207.json') $journal.protectedSha
    $budgetPath=Join-Path $train 'autonomous\v16\native-budget.json';$budget=Read-TrainJson $budgetPath
    if($budget.pending -or [int]$budget.starts -ne [int]$journal.starts -or [long]$budget.nativeMillis -ne [long]$journal.nativeMillis){throw 'TRAIN017_REPAIR_PENDING_OR_BUDGET_DRIFT'}
    $cold=Read-TrainJson $ColdPath
    if($cold.schemaVersion -ne 17 -or $cold.readOnly -isnot [bool] -or !$cold.readOnly -or $cold.root -ine $train -or [int]$cold.rows -ne 183 -or
       $cold.sqlSha -cne '9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D'){throw 'TRAIN017_REPAIR_COLD_PROOF'}
    Assert-TrainHash $cold.rawSql $cold.sqlSha
    foreach($key in @('schemaVersion','modelVersion','modelSha','optimizerSha','terminalWeightsSha','optimizerUpdates','completedEpochs','acceptedTestVersion','acceptedTestSha','featureSchemaSha','productionPromotion')){
        if($cold.selected.$key -cne $proof.selected.$key){throw 'TRAIN017_REPAIR_COLD_LINEAGE_DRIFT'}
    }
    $sqlFiles=@(Get-ChildItem -LiteralPath (Join-Path $train 'sql\data') -Recurse -File -Force)
    if(!$sqlFiles.Count -or $sqlFiles.Count -gt 4096 -or $sqlFiles.Count -ne @($cold.sqlFiles).Count){throw 'TRAIN017_REPAIR_COLD_STORE_CHANGED'}
    $dataPrefix=[IO.Path]::GetFullPath((Join-Path $train 'sql\data')).TrimEnd('\')+'\'
    $actualPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($actualFile in $sqlFiles){
        Assert-G0PhysicalFile $actualFile.FullName
        $canonical=[IO.Path]::GetFullPath($actualFile.FullName)
        if(!$canonical.StartsWith($dataPrefix,[StringComparison]::OrdinalIgnoreCase) -or !$actualPaths.Add($canonical)){throw 'TRAIN017_REPAIR_COLD_STORE_PATH'}
    }
    $declaredPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($file in $cold.sqlFiles){
        if($file.relative -cnotmatch '^sql/data/[A-Za-z0-9_./@#-]+$' -or @($file.relative.Split('/')|Where-Object {$_ -ceq '.' -or $_ -ceq '..' -or !$_}).Count){throw 'TRAIN017_REPAIR_COLD_STORE_PATH'}
        $canonical=[IO.Path]::GetFullPath((Join-Path $train $file.relative))
        if(!$canonical.StartsWith($dataPrefix,[StringComparison]::OrdinalIgnoreCase) -or !$declaredPaths.Add($canonical)){throw 'TRAIN017_REPAIR_COLD_STORE_PATH'}
    }
    if(!$actualPaths.SetEquals($declaredPaths)){throw 'TRAIN017_REPAIR_COLD_STORE_CHANGED'}
    foreach($file in $cold.sqlFiles){Assert-TrainHash (Join-Path $train $file.relative) $file.sha256}
    $pins=[ordered]@{}
    foreach($relative in @('TRAIN_CONFIG.ini','build.json','owner.txt','geo203.json','protected207.json','autonomous\v1\cold-checkpoint.json','autonomous\v16\native-budget.json')){
        $file=Join-Path $train $relative;Assert-G0PhysicalFile $file;$pins[$relative]=(Get-FileHash -LiteralPath $file).Hash
    }
    if($pins['TRAIN_CONFIG.ini'] -cne $cold.configSha -or $pins['build.json'] -cne $cold.buildSha -or
       $pins['autonomous\v1\cold-checkpoint.json'] -cne 'ACC21BED707B518FB58E23FEC006E7D658723763C73C237A039D950E14C9B010'){throw 'TRAIN017_REPAIR_COLD_PIN_DRIFT'}
    $executorPins=[ordered]@{}
    foreach($relative in @('tools\phantom-runtime\training\Training.ps1','tools\phantom-runtime\training\TrainingOwner.ps1','tools\phantom-runtime\training\Test-Training.ps1','test\java\org\l2jmobius\tests\phantoms\PhantomAutonomousTrainingNative.java','test\java\org\l2jmobius\tests\phantoms\PhantomLearning001ShadowNative.java')){
        $file=Join-Path $executor.Module $relative;Assert-G0PhysicalFile $file;$executorPins[$relative]=(Get-FileHash -LiteralPath $file).Hash
    }
    $proof|Add-Member frozenPins $pins;$proof|Add-Member executorPins $executorPins
    $proof|Add-Member historicalJournalSha (Get-FileHash -LiteralPath $journalPath).Hash
    $proof|Add-Member coldProofSha (Get-FileHash -LiteralPath $ColdPath).Hash
    $proof|Add-Member historicalVerdict 'PARTIAL016_NATIVE_COLLECTED_DIRECTOR_CLOSE_FAILED'
    return Write-TrainNoFitClosure017 $output $proof
    }finally{$inputLease.Dispose()}
}
function Copy-TrainConfig013($Values) {
    $copy=@{}
    if($Values -is [Collections.IDictionary]){foreach($key in $Values.Keys){$copy[$key]=[string]$Values[$key]}}
    else{foreach($property in $Values.PSObject.Properties){$copy[$property.Name]=[string]$property.Value}}
    return $copy
}
function Read-TrainCold013($Dependencies) {
    Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('checkpoint013',$layout.Root)
    return Read-TrainJson (Join-Path $layout.Root 'autonomous\v13\sql-checkpoint.json')
}
function Get-TrainAdmissionFailure013 {
    if(!$script:WorkerRecord){return ''}
    $errorPath=Join-Path $layout.Root ('workers\'+$script:WorkerRecord.nonce+'\worker.err')
    if(Test-Path -LiteralPath $errorPath){
        $body=[IO.File]::ReadAllText($errorPath)
        if($body -cmatch 'AUTONOMOUS_NATIVE_FAILED (TRAIN013_INSUFFICIENT_LEGAL_COHORT|AUTONOMOUS_BLOCKED_EVAL_NO_LAWFUL_SCENE|EVAL012_[A-Z0-9_]*(?:COVERAGE|INSUFFICIENT|NO_ELIGIBLE)[A-Z0-9_]*)\b'){return $Matches[1]}
    }
    return ''
}
function Invoke-TrainEvaluation013($Dependencies,[string]$Jar,$Journal,[string]$JournalPath,[string]$CycleDirectory,[bool]$PrepareOnly,[bool]$VerifyOnly=$false) {
    $parentConfig=Copy-TrainConfig013 $script:Config
    if($VerifyOnly){Assert-TrainEvaluationReceipt013 $Journal.lastV2 $Journal.dataset ([long]$Journal.lastEvaluation.candidateVersion) $Journal.lastEvaluation.candidateSha (Join-Path $CycleDirectory 'eval-v2.json') $Journal.evalSummarySha}
    $candidate=if($PrepareOnly){[pscustomobject]@{candidateVersion=$Journal.parentCheckpoint.modelVersion;candidateSha=$Journal.parentCheckpoint.modelSha}}else{$Journal.lastEvaluation}
    $suffix=if($PrepareOnly){'-probe'}else{'-eval'}
    $child=Copy-TrainConfig013 $parentConfig
    $child.autonomous009Campaign=$Journal.campaign+('-e{0:D6}' -f [int]$Journal.cycle)+$(if($PrepareOnly){'-p'}else{''})
    $child.outcome006DatasetId=$Journal.dataset+$suffix
    $child.autonomous009EvaluationOnly='true';$child.autonomous009Exploration='false';$child.autonomous009Phase='EVAL';$child.evaluationOutcomeVersion='2'
    $child.autonomous009EvaluationEveryCycles='1';$child.autonomous009SmokeMaxStarts='2';$child.autonomous009SmokeMaxNativeMinutes='35';$child.autonomous009Families='DAMAGE'
    $child.autonomous009ModelVersion=[string]$candidate.candidateVersion;$child.autonomous009ModelSha=$candidate.candidateSha
    $child.autonomous009EvaluationReferenceCampaign=$Journal.campaign;$child.autonomous009EvaluationReferenceDataset=$Journal.dataset
    $child.autonomous013ParentCampaign=$Journal.campaign;$child.autonomous013ParentConfigSha=$Journal.configSha
    $reference=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$Journal.campaign+'\evaluations\'+$Journal.dataset+'-'+$candidate.candidateVersion)
    $null=[IO.Directory]::CreateDirectory($reference);Assert-TrainDirectory $reference
    $pin=Join-Path $reference 'protocol.json'
    if(!(Test-Path -LiteralPath $pin)){if($VerifyOnly){throw 'TRAIN013_EVALUATION_REFERENCE_MISSING'};Write-TrainJson $pin @{schema='FROZEN_TRAIN_REFERENCE_V13';modelVersion=$candidate.candidateVersion;modelSha=$candidate.candidateSha;dataset=$Journal.dataset;nativeEvaluationEvidence=$false;trainingCredit=$false}}
    $pinned=Read-TrainJson $pin
    if($pinned.modelSha -cne $candidate.candidateSha -or [long]$pinned.modelVersion -ne [long]$candidate.candidateVersion -or $pinned.dataset -cne $Journal.dataset){throw 'TRAIN013_CHILD_REFERENCE_CHANGED'}
    $switch=Join-Path $CycleDirectory ('child'+$suffix+'-switch.json')
    if(!(Test-Path -LiteralPath $switch)){if($VerifyOnly){throw 'TRAIN013_EVALUATION_SWITCH_MISSING'};Write-TrainJson $switch @{schemaVersion=13;cycleId=$Journal.dataset;parentConfig=$parentConfig;childConfig=$child;completed=$false}}
    $saved=Read-TrainJson $switch
    $child=Copy-TrainConfig013 $saved.childConfig
    if($child.autonomous009ModelSha -cne $candidate.candidateSha -or $child.outcome006DatasetId -cne $Journal.dataset+$suffix){throw 'TRAIN013_CHILD_PIN_CHANGED'}
    try{
        $script:Config=$child;Assert-TrainConfig $child;$null=Test-TrainOutcomeV2 $child;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $child
        $evaluationDirectory=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$child.autonomous009Campaign+'\evaluations\'+$child.outcome006DatasetId+'-'+$candidate.candidateVersion)
        $admission=Join-Path $CycleDirectory ('child'+$suffix+'-admitted.json')
        if($VerifyOnly -and !(Test-Path -LiteralPath $admission)){throw 'TRAIN013_EVALUATION_ADMISSION_MISSING'}
        Assert-TrainHeadlessAdmission013 $evaluationDirectory $admission
        Publish-TrainState 'AUDITING' 'Проверка независимых законных V2 сцен и terminal provenance до GameServer.'
        Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('admit-eval',$layout.Root)
        if(!(Test-Path -LiteralPath $admission)){
            Write-TrainJson $admission @{schemaVersion=13;cycleId=$Journal.dataset;modelVersion=$candidate.candidateVersion;modelSha=$candidate.candidateSha;configSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'TRAIN_CONFIG.ini')).Hash;prepared=$true;nativeEvidence=$false;trainingCredit=$false}
        }
        if($PrepareOnly){return $null}
        $complete=Join-Path $evaluationDirectory 'evaluation-v2.complete';$pairsPath=Join-Path $evaluationDirectory 'evaluation-pairs-v2.json'
        if(!(Test-Path -LiteralPath $complete)){
            if($VerifyOnly){throw 'TRAIN013_EVALUATION_COMPLETE_MISSING'}
            if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){return $null}
            Publish-TrainState 'AUDITING' 'Независимая V2 EVAL frozen candidate; credit/FIT выключены.'
            Invoke-TrainAutonomousNative $Dependencies $Jar $Journal $JournalPath
        }
        if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){return $null}
        if(!(Test-Path -LiteralPath $complete)){throw 'TRAIN013_EVAL_COVERAGE_GAP'}
        Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('verify-eval013',$layout.Root)
        $worker=Read-TrainJson (Join-Path $layout.Root 'worker-process.json')
        $lines=@([IO.File]::ReadAllLines((Join-Path $layout.Root ('workers\'+$worker.nonce+'\worker.out')))|Where-Object {$_.StartsWith('TRAIN013_TYPED_EVAL_PROOF ',[StringComparison]::Ordinal)})
        if($lines.Count -ne 1){throw 'TRAIN013_TYPED_EVAL_PROOF_MISSING'}
        $typed=ConvertFrom-Json $lines[0].Substring('TRAIN013_TYPED_EVAL_PROOF '.Length)
        if([long]$typed.modelVersion -ne [long]$candidate.candidateVersion -or $typed.modelSha -cne $candidate.candidateSha -or $typed.directory -ine $evaluationDirectory -or $typed.trainingCredit -ne $false){throw 'TRAIN013_TYPED_EVAL_PIN_CHANGED'}
        $proof=Read-TrainJson $complete;$pairs=Read-TrainJson $pairsPath;Assert-TrainOutcomeV2Proof $proof $pairs
        $protocol=Read-TrainJson (Join-Path $evaluationDirectory 'protocol-v2.json')
        if([long]$protocol.modelVersion -ne [long]$candidate.candidateVersion -or $protocol.modelSha -cne $candidate.candidateSha -or $protocol.configSha -cne (Get-FileHash -LiteralPath (Join-Path $layout.Root 'TRAIN_CONFIG.ini')).Hash.ToLowerInvariant()){throw 'TRAIN013_EVALUATION_AUTHORITY_CHANGED'}
        return [pscustomobject]@{schemaVersion=13;cycleId=$Journal.dataset;directory=$evaluationDirectory;completeSha=(Get-FileHash -LiteralPath $complete).Hash;pairsSha=(Get-FileHash -LiteralPath $pairsPath).Hash;protocolSha=(Get-FileHash -LiteralPath (Join-Path $evaluationDirectory 'protocol-v2.json')).Hash;proofFiles=$typed.proofFiles;modelVersion=$candidate.candidateVersion;modelSha=$candidate.candidateSha;statistics=$pairs.statistics;pairs=$pairs.pairs;trainingCredit=$false;productionPromotion=$false}
    }finally{
        $script:Config=$parentConfig;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $parentConfig
    }
}
function Invoke-TrainAutonomousDirector013($Dependencies,[string]$Jar) {
    if($script:Config.autonomous009EvaluationOnly -ceq 'true' -or $script:Config.autonomous009AcceptanceSmoke -cne 'true'){throw 'TRAIN013_OWN_TRAIN_ACCEPTANCE_REQUIRED'}
    $baseConfig=Copy-TrainConfig013 $script:Config;$campaign=$baseConfig.autonomous009Campaign
    $goal=if(Test-TrainIncremental016 $baseConfig){16}else{13}
    $directory=Join-Path $layout.Root ('autonomous\v'+$goal+'\campaigns\'+$campaign);$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
    $path=Join-Path $directory 'director-state.json';$basePath=Join-Path $directory 'base-config.json'
    if(!(Test-Path -LiteralPath $basePath)){Write-TrainJson $basePath $baseConfig}
    $baseConfig=Copy-TrainConfig013 (Read-TrainJson $basePath)
    $ownerSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'owner.txt')).Hash;$sourceSha=Get-TrainSourceFingerprint
    $controlSha=Get-TrainControlSource013;$geoSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'geo203.json')).Hash;$protectedSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'protected207.json')).Hash
    $budgetPath=Join-Path $layout.Root ('autonomous\v'+$goal+'\native-budget.json');$global=Get-TrainNativeBudget013 $budgetPath $goal
    if($global.pending){
        $reservation=@($global.reservations|Where-Object {$_.attemptId -ceq $global.pending})[0]
        $record=Read-TrainJson (Join-Path $layout.Root 'native-process.json')
        if($record.nonce -cne $global.pending -or !(Test-TrainProcessAbsent $record) -or !(Test-TrainExitReceipt $layout $record) -or [DateTime]::FromFileTimeUtc([long]$record.created) -lt [DateTime]::Parse($reservation.startedUtc).ToUniversalTime()){throw 'TRAIN013_PENDING_EXIT_UNPROVEN'}
        $exitFact=Read-TrainJson (Get-TrainExitReceiptPath $layout $record)
        $chargePath=Join-Path $layout.Root ('attempts\'+$record.nonce+'\budget-charge.json')
        if(Test-Path -LiteralPath $chargePath){$charge=Read-TrainJson $chargePath;if([long]$charge.priorNativeMillis -ne [long]$reservation.priorNativeMillis -or [long]$charge.chargedMillis -ne [long]$exitFact.nativeElapsedMillis){throw 'TRAIN013_CHARGE_MISMATCH'}}
        else{Write-TrainJson $chargePath @{campaign=$campaign;attemptId=$record.nonce;pid=$record.pid;created=$record.created;chargedMillis=$exitFact.nativeElapsedMillis;priorNativeMillis=$reservation.priorNativeMillis;maximumNativeMillis=4800000L;proof='OWN_HELD_HANDLE_EXIT_TIME'}}
        $global=Complete-TrainNativeBudget013 $budgetPath $record.nonce ([long]$exitFact.nativeElapsedMillis) 'OWN_HELD_HANDLE_EXIT_TIME' $goal
    }
    if(Test-Path -LiteralPath $path){
        $journal=Read-TrainJson $path
        if($journal.schemaVersion -ne 13 -or $journal.campaign -cne $campaign -or $journal.jarSha -cne $Jar -or $journal.sourceSha -cne $sourceSha -or $journal.ownerSha -cne $ownerSha -or $journal.configSha -cne (Get-FileHash -LiteralPath $basePath).Hash){throw 'TRAIN013_COLD_PIN_CHANGED'}
        if($journal.controlSourceSha -cne $controlSha -or $journal.geoSha -cne $geoSha -or $journal.protectedSha -cne $protectedSha){throw 'TRAIN013_CONTROL_OR_ASSET_PIN_CHANGED'}
    }else{
        $journal=[pscustomobject]@{schemaVersion=13;campaign=$campaign;jarSha=$Jar;sourceSha=$sourceSha;ownerSha=$ownerSha;configSha=(Get-FileHash -LiteralPath $basePath).Hash;cycle=1;dataset='';phase='NEW';starts=0;nativeMillis=0L;maximumStarts=[int]$baseConfig.autonomous009SmokeMaxStarts;maximumNativeMillis=([long]$baseConfig.autonomous009SmokeMaxNativeMinutes*60000L);pending=$false;pendingStartedUtc='';lastAttempt='';sessionStartedUtc=[DateTime]::UtcNow.ToString('o');completedCycles=0;fits=0;noData=0;patience=0;creditedCycles=@();bestVersion=0L;bestSha='';bestDecision='UNRESOLVED';lastCheckpoint='';lastEvaluation=$null;lastV2=$null;parentCheckpoint=$null;improved=$false;reason=''}
        $journal|Add-Member controlSourceSha $controlSha;$journal|Add-Member geoSha $geoSha;$journal|Add-Member protectedSha $protectedSha
        $journal|Add-Member pendingDecision $null;$journal|Add-Member evalSummarySha ''
    }
    $journal.starts=[int]$global.starts;$journal.nativeMillis=[long]$global.nativeMillis;$journal.pending=$false;Save-TrainCycleState013 $path $journal
    $script:Config=$baseConfig;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $baseConfig
    while(!(Test-TrainRequest 'stop')){
        Assert-TrainAutonomousResources
        if(Test-TrainRequest 'pause'){Publish-TrainState 'PAUSED_BY_OWNER' 'Атомарная граница013 сохранена.';return}
        $script:State['autonomousCampaign']=$campaign;$script:State['autonomousCycle']=$journal.cycle;$script:State['autonomousFits']=$journal.fits;$script:State['autonomousNativeStarts']=$journal.starts;$script:State['autonomousNativeMillis']=$journal.nativeMillis
        if($journal.phase -cnotin @('DECIDE','CHECKPOINT') -and ($journal.reason -cin @('NO_DATA','EVAL_COVERAGE_GAP','INVALID_EVAL','REGRESSION_OBSERVED') -or $journal.patience -ge 3)){Publish-TrainState $(if($journal.reason -ceq 'NO_DATA'){'INSUFFICIENT_EVIDENCE'}elseif($journal.reason -ceq 'EVAL_COVERAGE_GAP'){'PAUSED_EVAL_COVERAGE_GAP'}elseif($journal.reason -ceq 'REGRESSION_OBSERVED'){'PAUSED_REGRESSION_OBSERVED'}elseif($journal.patience -ge 3){'PAUSED_NO_MEASURABLE_PROGRESS'}else{'FAILED_FAIL_CLOSED'}) $journal.reason;return}
        if([int]$journal.completedCycles -ge [int]$baseConfig.maxTrainingCohorts){Publish-TrainState 'RESOURCE_LIMIT' 'TRAIN013_COHORT_RETENTION_CAP';return}
        if([int]$baseConfig.autonomous009CycleLimit -gt 0 -and [int]$journal.completedCycles -ge [int]$baseConfig.autonomous009CycleLimit){Publish-TrainState 'PAUSED' 'CYCLE_LIMIT_NEXT_PLAN_SAVED';return}
        $cycleDirectory=Join-Path $directory ('cycles\c{0:D6}' -f [int]$journal.cycle);$null=[IO.Directory]::CreateDirectory($cycleDirectory);Assert-TrainDirectory $cycleDirectory
        if($journal.phase -ceq 'NEW'){
            $cold=Read-TrainCold013 $Dependencies;$journal.parentCheckpoint=$cold
            if([long]$journal.bestVersion -eq 0){$journal.bestVersion=[long]$cold.acceptedTestVersion;$journal.bestSha=$cold.acceptedTestSha;Write-TrainJson (Join-Path $directory 'best-verified-test.json') @{schemaVersion=13;modelVersion=$journal.bestVersion;modelSha=$journal.bestSha;authority='PRESERVED_ACCEPTED_TEST';productionPromotion=$false}}
            $journal.dataset=$campaign+('-c{0:D6}' -f [int]$journal.cycle)
            Write-TrainJson (Join-Path $cycleDirectory 'parent.json') $cold
            $journal.phase='COLLECT';Save-TrainCycleState013 $path $journal
        }
        $config=Copy-TrainConfig013 $baseConfig;$config.autonomous009ModelVersion=[string]$journal.parentCheckpoint.modelVersion;$config.autonomous009ModelSha=$journal.parentCheckpoint.modelSha;$config.outcome006DatasetId=$journal.dataset;$config.autonomous009SourceCursor=[string]([int]$journal.cycle-1)
        $cohort=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$campaign+'\cohorts\'+$journal.dataset)
        if($journal.phase -ceq 'COLLECT'){
            $config.autonomous009Phase='COLLECT';$script:Config=$config;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $config
            try{
                if((Test-TrainIncremental016 $script:Config) -and !$script:ReadinessOnly013 -and !(Test-Path -LiteralPath (Join-Path $cohort 'cohort016.complete.json'))){throw 'TRAIN016_G3_REQUIRED_BEFORE_NATIVE_MODE'}
                Assert-TrainHeadlessAdmission013 $cohort (Join-Path $cohort 'training-admission013.json')
                Publish-TrainState 'COLLECTING' 'Проверка законного TRAIN cohort и terminal provenance до GS.'
                if(!(Test-TrainIncremental016 $script:Config) -or !(Test-Path -LiteralPath (Join-Path $cohort 'cohort016.complete.json'))){Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('prepare',$layout.Root)}
                if(Test-TrainIncremental016 $script:Config){Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('verify-seal016',$layout.Root)}
                if(Test-TrainIncremental016 $script:Config){
                    $liveCold=Read-TrainCold013 $Dependencies
                    if($liveCold.modelVersion -ne $journal.parentCheckpoint.modelVersion -or $liveCold.modelSha -cne $journal.parentCheckpoint.modelSha -or $liveCold.optimizerSha -cne $journal.parentCheckpoint.optimizerSha){throw 'TRAIN016_STALE_MODEL_OR_ADAM_BEFORE_GS'}
                }
                $admission=Read-TrainJson (Join-Path $cohort 'training-admission013.json')
                if([int]$admission.cells -lt [int]$admission.minimumCells -or [int]$admission.classes -lt 2 -or [int]$admission.scopes -lt 2){$journal.reason='NO_DATA';Save-TrainCycleState013 $path $journal;continue}
                if(!(Test-TrainIncremental016 $script:Config) -and $script:ReadinessSession -eq 0 -and !(Test-Path -LiteralPath (Join-Path $cycleDirectory 'child-probe-admitted.json'))){$null=Invoke-TrainEvaluation013 $Dependencies $Jar $journal $path $cycleDirectory $true}
            }catch{
                $cause=if($_.Exception.Message -ceq 'TRAIN013_HEADLESS_INSUFFICIENT_EVIDENCE'){$(if($_.Exception.Data['headlessOperation'] -ceq 'prepare'){'TRAIN013_INSUFFICIENT_LEGAL_COHORT'}else{'EVAL012_INSUFFICIENT_COVERAGE'})}else{Get-TrainAdmissionFailure013}
                if(!$cause){throw}
                $journal.reason=if($cause -ceq 'TRAIN013_INSUFFICIENT_LEGAL_COHORT'){'NO_DATA'}else{'EVAL_COVERAGE_GAP'};Save-TrainCycleState013 $path $journal;continue
            }
            if($script:ReadinessOnly013){
                if($script:ReadinessSession -gt 0){
                    Write-TrainJson (Join-Path $directory 'readiness-complete013.json') @{schemaVersion=$script:ReadinessGoalVersion;scope='TRAIN_ONLY';campaign=$campaign;cycleId=$journal.dataset;trainAdmissionSha=(Get-FileHash -LiteralPath (Join-Path $cohort 'training-admission013.json')).Hash;nativeStarted=$false;trainingCredit=$false}
                    return
                }
                Write-TrainJson (Join-Path $directory 'readiness-complete013.json') @{schemaVersion=13;campaign=$campaign;cycleId=$journal.dataset;trainAdmissionSha=(Get-FileHash -LiteralPath (Join-Path $cohort 'training-admission013.json')).Hash;independentProbeSha=(Get-FileHash -LiteralPath (Join-Path $cycleDirectory 'child-probe-admitted.json')).Hash;nativeStarted=$false;trainingCredit=$false;requiresCodexPhaseCommand=$false}
                return
            }
            $readinessResult=Join-Path $directory 'readiness-result013.json'
            if((Test-Path -LiteralPath $readinessResult) -and (Read-TrainJson $readinessResult).status -cne 'READY'){throw 'TRAIN013_READINESS_NOT_READY_NO_NATIVE'}
            if(!(Test-Path -LiteralPath (Join-Path $cohort 'collection.complete'))){Publish-TrainState 'COLLECTING' 'Реальные TRAIN/TUNING/AUDIT бои.';Invoke-TrainAutonomousNative $Dependencies $Jar $journal $path}
            if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){continue}
            $journal.phase='FIT';Save-TrainCycleState013 $path $journal
        }
        if($journal.phase -ceq 'FIT'){
            # A frozen result is historical evidence, never a request to dispatch or rewrite FIT.
            if((Test-TrainIncremental016 $script:Config) -and (Test-Path -LiteralPath (Join-Path $cycleDirectory 'checkpoint016.json'))){throw 'TRAIN017_EXISTING_CHECKPOINT_USE_REPAIR'}
            $config.autonomous009Phase='FIT';$script:Config=$config;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $config;Publish-TrainState 'FITTING' 'Продолжение exact terminal Adam; selected checkpoint отдельно.'
            if(Test-TrainIncremental016 $script:Config){
                $fitReservation=Join-Path $cycleDirectory 'fit-reserved016.json';$fitResult=Join-Path $cycleDirectory 'fit-result.json'
                if(Test-Path -LiteralPath $fitReservation){
                    $reserved=Read-TrainJson $fitReservation
                    if($reserved.schemaVersion -ne 16 -or $reserved.cycle -cne $journal.dataset -or $reserved.parent.modelSha -cne $journal.parentCheckpoint.modelSha -or $reserved.parent.optimizerSha -cne $journal.parentCheckpoint.optimizerSha){throw 'TRAIN016_FIT_RESERVATION_CHANGED'}
                    if(!(Test-Path -LiteralPath $fitResult)){throw 'TRAIN016_FIT_RESERVATION_UNCERTAIN_NO_REPLAY'}
                    $fit=Read-TrainJson $fitResult
                    if(!$fit.PSObject.Properties['reservationSha'] -or $fit.reservationSha -cne (Get-FileHash -LiteralPath $fitReservation).Hash){throw 'TRAIN016_FIT_RESULT_LINEAGE_CHANGED'}
                }else{
                    if([int]$journal.fits -ne 0){throw 'TRAIN016_FIT_ALREADY_COMMITTED_NO_REPLAY'}
                    Write-TrainJson $fitReservation @{schemaVersion=16;cycle=$journal.dataset;parent=$journal.parentCheckpoint;trainingCredit=$false;issuedUtc=[DateTime]::UtcNow.ToString('o')}
                    Invoke-TrainJava $Dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
                    $fit=Read-TrainJson (Join-Path $layout.Root 'tactical-v3-evaluation.json');$fit|Add-Member reservationSha (Get-FileHash -LiteralPath $fitReservation).Hash;Write-TrainJson $fitResult $fit
                }
                $cold=Read-TrainCold013 $Dependencies
                if([long]$fit.candidateVersion -gt 0){
                    if($cold.modelVersion -ne $fit.candidateVersion -or $cold.modelSha -cne $fit.candidateSha -or $cold.optimizerSha -cne $fit.metrics.optimizerSha -or [long]$cold.optimizerUpdates -le [long]$journal.parentCheckpoint.optimizerUpdates){throw 'TRAIN016_FIT_COLD_READBACK_CHANGED'}
                    $journal.fits=1;$journal.reason='ONE_FIT_COLD_RESTORED'
                }else{
                    if($cold.modelVersion -ne $journal.parentCheckpoint.modelVersion -or $cold.modelSha -cne $journal.parentCheckpoint.modelSha -or $cold.optimizerSha -cne $journal.parentCheckpoint.optimizerSha){throw 'TRAIN016_NO_FIT_LINEAGE_CHANGED'}
                    $journal.reason='NO_FIT_INSUFFICIENT_VALID_DATA'
                }
                Write-TrainJson (Join-Path $cycleDirectory 'checkpoint016.json') @{schemaVersion=16;cycle=$journal.dataset;selected=$cold;parent=$journal.parentCheckpoint;bestVersion=$journal.bestVersion;bestSha=$journal.bestSha;fits=$journal.fits;reason=$journal.reason;independentEvaluation=$false;productionPromotion=$false}
                $journal.phase='DONE016';$journal.completedCycles=1;Save-TrainCycleState013 $path $journal;Publish-TrainState 'STOPPED' $journal.reason;return
            }
            Invoke-TrainJava $Dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
            $fit=Read-TrainJson (Join-Path $layout.Root 'tactical-v3-evaluation.json')
            if([long]$fit.candidateVersion -le 0){$journal.reason='NO_DATA';Write-TrainJson (Join-Path $cycleDirectory 'fit-result.json') $fit;Save-TrainCycleState013 $path $journal;continue}
            $cold=Read-TrainCold013 $Dependencies
            if($cold.modelVersion -ne $fit.candidateVersion -or $cold.modelSha -cne $fit.candidateSha -or $cold.optimizerSha -cne $fit.metrics.optimizerSha -or [long]$cold.optimizerUpdates -le [long]$journal.parentCheckpoint.optimizerUpdates){throw 'TRAIN013_FIT_COLD_READBACK_CHANGED'}
            Write-TrainJson (Join-Path $cycleDirectory 'fit-result.json') $fit
            $journal.lastEvaluation=$fit;$journal.fits=[int]$journal.fits+1;$journal.phase='EVAL';Save-TrainCycleState013 $path $journal
        }
        if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){continue}
        if($journal.phase -ceq 'EVAL'){
            $config.autonomous009Phase='FIT';$script:Config=$config
            try{$v2=Invoke-TrainEvaluation013 $Dependencies $Jar $journal $path $cycleDirectory $false}catch{
                if(!(Get-TrainAdmissionFailure013) -and $_.Exception.Message -cne 'TRAIN013_EVAL_COVERAGE_GAP'){throw};$journal.reason='EVAL_COVERAGE_GAP';Save-TrainCycleState013 $path $journal;continue
            }
            if(!$v2){continue}
            Write-TrainJson (Join-Path $cycleDirectory 'eval-v2.json') $v2;$journal.evalSummarySha=(Get-FileHash -LiteralPath (Join-Path $cycleDirectory 'eval-v2.json')).Hash;$journal.lastV2=$v2;$journal.phase='DECIDE';Save-TrainCycleState013 $path $journal
        }
        if($journal.phase -ceq 'DECIDE'){
            $script:Config=$config;$verified=Invoke-TrainEvaluation013 $Dependencies $Jar $journal $path $cycleDirectory $false $true
            if(!$verified){continue}
            $stats=$journal.lastV2.statistics;$pairs=@($journal.lastV2.pairs|Where-Object {$_.terminalComparable})
            $receipt=[pscustomobject]@{schemaVersion=13;cycleId=$journal.dataset;fitCommitted=$true;optimizerDelta=([long]$journal.lastEvaluation.metrics.optimizerUpdates-[long]$journal.parentCheckpoint.optimizerUpdates);selectedEpoch=[int]$journal.lastEvaluation.metrics.epochs;candidateVersion=$journal.lastEvaluation.candidateVersion;candidateSha=$journal.lastEvaluation.candidateSha;evalValid=$true;coverage=$true;terminalPairs=$pairs.Count;paidDivergences=([int]$stats.SOLO.sides.LEARNED.paidLegalDivergences+[int]$stats.PARTY.sides.LEARNED.paidLegalDivergences);stockWins=([int]$stats.SOLO.sides.STOCK.wins+[int]$stats.PARTY.sides.STOCK.wins);learnedWins=([int]$stats.SOLO.sides.LEARNED.wins+[int]$stats.PARTY.sides.LEARNED.wins);stockDeaths=([int]$stats.SOLO.sides.STOCK.deathArms+[int]$stats.PARTY.sides.STOCK.deathArms);learnedDeaths=([int]$stats.SOLO.sides.LEARNED.deathArms+[int]$stats.PARTY.sides.LEARNED.deathArms);resourceComparable=$true;resourceRegression=$false;bestComparison='UNKNOWN';supportedGain=$false;criticalRegression=$false;reason=''}
            $knownMp=@($pairs|Where-Object {$null -ne $_.stock.selectedPaidMp -and $null -ne $_.learned.selectedPaidMp}).Count -eq $pairs.Count
            $stockMp=if($knownMp){[double](($pairs|ForEach-Object {$_.stock.selectedPaidMp}|Measure-Object -Sum).Sum)}else{$null}
            $learnedMp=if($knownMp){[double](($pairs|ForEach-Object {$_.learned.selectedPaidMp}|Measure-Object -Sum).Sum)}else{$null}
            $receipt.resourceRegression=$knownMp -and $stockMp -gt 0 -and $learnedMp -gt 1.10*$stockMp -and $receipt.learnedWins -le $receipt.stockWins -and $receipt.learnedDeaths -ge $receipt.stockDeaths
            $receipt|Add-Member regressionObserved ($receipt.learnedWins -lt $receipt.stockWins -or $receipt.learnedDeaths -gt $receipt.stockDeaths)
            $decision=Get-TrainCycleDecision013 $receipt $journal
            Write-TrainJson (Join-Path $cycleDirectory 'decision.json') @{schemaVersion=13;cycleId=$journal.dataset;receipt=$receipt;decision=$decision;resourceMetrics=$journal.lastV2.pairs;selectedPaidMpKnown=$knownMp;stockSelectedPaidMp=$stockMp;learnedSelectedPaidMp=$learnedMp;wholePartyResources='UNKNOWN';uncertainty=$stats;bestComparison='UNKNOWN';evaluationSha=$journal.lastV2.pairsSha}
            $journal.pendingDecision=$decision;$journal.phase='CHECKPOINT';Save-TrainCycleState013 $path $journal
        }
        if($journal.phase -ceq 'CHECKPOINT'){
            $script:Config=$config;$verified=Invoke-TrainEvaluation013 $Dependencies $Jar $journal $path $cycleDirectory $false $true
            if(!$verified){continue}
            Publish-TrainState 'CHECKPOINTING' 'SQL, terminal Adam, выбранные веса и независимая V2 фиксируются отдельно.'
            $script:Config=$config;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $config;$cold=Read-TrainCold013 $Dependencies
            if($cold.optimizerSha -cne $journal.lastEvaluation.metrics.optimizerSha -or $cold.modelSha -cne $journal.lastEvaluation.candidateSha){throw 'TRAIN013_CHECKPOINT_LINEAGE_CHANGED'}
            $checkpoint=Join-Path $cycleDirectory 'checkpoint.json'
            $completed=[ordered]@{schemaVersion=13;cycleId=$journal.dataset;completed=$true;selected=$cold;optimizerParent=$journal.parentCheckpoint;fitSha=(Get-FileHash -LiteralPath (Join-Path $cycleDirectory 'fit-result.json')).Hash;evalSha=$journal.evalSummarySha;decisionSha=(Get-FileHash -LiteralPath (Join-Path $cycleDirectory 'decision.json')).Hash;bestVersion=$journal.pendingDecision.bestVersion;bestSha=$journal.pendingDecision.bestSha;patience=$journal.pendingDecision.patience;productionPromotion=$false}
            if(Test-Path -LiteralPath $checkpoint){$existing=Read-TrainJson $checkpoint;if(($existing|ConvertTo-Json -Compress -Depth 12) -cne ([pscustomobject]$completed|ConvertTo-Json -Compress -Depth 12)){throw 'TRAIN013_COMPLETED_CHECKPOINT_CHANGED'}}else{Write-TrainJson $checkpoint $completed}
            $journal=Complete-TrainCycle013 $path $journal $checkpoint $directory
            if($journal.patience -ge 3){Publish-TrainState 'PAUSED_NO_MEASURABLE_PROGRESS' $journal.reason;return}
        }
        Invoke-TrainQueuedExport $Dependencies
    }
    Publish-TrainState 'STOPPED_BY_OWNER' 'STOP013: журнал сохранён, повторных reserved боёв нет.'
}
function Invoke-TrainAutonomousDirector($Dependencies,[string]$Jar) {
    if($script:Config.ContainsKey('autonomous013Version') -and $script:Config.autonomous013Version -ceq '1' -and $script:Config.autonomous009EvaluationOnly -cne 'true'){Invoke-TrainAutonomousDirector013 $Dependencies $Jar;return}
    $outcomeV2=Test-TrainOutcomeV2 $script:Config
    $campaign=$script:Config.autonomous009Campaign
    $directory=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$campaign);$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
    $path=Join-Path $directory 'director-state.json'
    if(Test-Path -LiteralPath $path){
        $journal=Read-TrainJson $path
        if($journal.schemaVersion -ne 9 -or $journal.campaign -cne $campaign -or $journal.ownerSha -cne (Get-FileHash -LiteralPath (Join-Path $layout.Root 'owner.txt')).Hash){throw 'TRAIN009_CAMPAIGN_PIN_CHANGED'}
        if($journal.jarSha -cne $Jar){
            $sealed=@(Get-ChildItem -LiteralPath $directory -File -Recurse | Where-Object {$_.Name -in @('protocol.json','cells.tsv','reserved.json','collection.complete')})
            if($sealed.Count -gt 0 -or $journal.phase -cne 'COLLECT' -or [int]$journal.completedCycles -ne 0 -or $journal.pending){throw 'TRAIN009_CAMPAIGN_PIN_CHANGED'}
            # No cohort has been sealed or dispatched. Preserve the failed pre-CUT source revision and cumulative budget.
            Write-TrainJson (Join-Path $directory ('precut-source-revision.'+[guid]::NewGuid().ToString('N')+'.json')) @{oldJarSha=$journal.jarSha;newJarSha=$Jar;priorStarts=$journal.starts;priorNativeMillis=$journal.nativeMillis;reason='NO_SEALED_COHORT_NO_RESERVED_SLOT';journalBefore=$journal}
            $journal.jarSha=$Jar
        }
        if($journal.pending){
            $native=Join-Path $layout.Root 'native-process.json'
            if(!(Test-Path -LiteralPath $native) -or !(Test-TrainProcessAbsent (Read-TrainJson $native))){throw 'TRAIN009_PENDING_NATIVE_EXIT_UNPROVEN'}
            $elapsed=[long][Math]::Ceiling(([DateTime]::UtcNow-[DateTime]::Parse($journal.pendingStartedUtc).ToUniversalTime()).TotalMilliseconds);$exact=$false
            $record=Read-TrainJson $native
            if((Test-TrainExitReceipt $layout $record)){
                $exitFact=Read-TrainJson (Get-TrainExitReceiptPath $layout $record)
                if($exitFact.PSObject.Properties['nativeElapsedMillis'] -and [DateTime]::FromFileTimeUtc([long]$record.created) -ge [DateTime]::Parse($journal.pendingStartedUtc).ToUniversalTime()){$elapsed=[long]$exitFact.nativeElapsedMillis;$exact=$true}
            }
            if($script:Config.autonomous009EvaluationOnly -ceq 'true' -and !$exact){throw 'TRAIN010_PENDING_EXIT_TIME_UNPROVEN'}
            $journal.nativeMillis=[long]$journal.nativeMillis+$elapsed;$journal.pending=$false;$journal.conservativeRecovery=!$exact
        }
    }else{
        $prior=0L;if($script:Config.ContainsKey('autonomous009PriorNativeMillis')){$prior=[long]$script:Config.autonomous009PriorNativeMillis;if($prior -lt 0 -or $prior -gt 5400000){throw 'TRAIN009_PRIOR_BUDGET_INVALID'}}
        $priorStarts=0;if($script:Config.ContainsKey('autonomous009PriorStarts')){$priorStarts=[int]$script:Config.autonomous009PriorStarts}
        $journal=[pscustomobject]@{schemaVersion=9;campaign=$campaign;jarSha=$Jar;ownerSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'owner.txt')).Hash;cycle=1;phase='NEW';dataset='';teacherVersion=0L;teacherSha='';starts=$priorStarts;nativeMillis=$prior;pending=$false;pendingStartedUtc='';lastAttempt='';conservativeRecovery=$false;sessionStartedUtc=[DateTime]::UtcNow.ToString('o');completedCycles=0;fits=0;noData=0;stagnation=0;lastCheckpoint='';lastEvaluation='';improved=$false}
    }
    if($script:Config.autonomous009EvaluationOnly -ceq 'true'){
        if($journal.phase -ceq 'NEW'){$journal.phase='EVAL';$journal.dataset=$script:Config.outcome006DatasetId;$journal.teacherVersion=[long]$script:Config.autonomous009ModelVersion;$journal.teacherSha=$script:Config.autonomous009ModelSha;$journal.lastEvaluation=[pscustomobject]@{candidateVersion=$journal.teacherVersion;candidateSha=$journal.teacherSha;kind='FROZEN_EVALUATION_ONLY'}}
        if($journal.phase -cnotin @('EVAL','EVAL_COMPLETE') -or [int]$journal.fits -ne 0){throw 'TRAIN010_EVAL_JOURNAL_SCOPE'}
        if($journal.phase -ceq 'EVAL_COMPLETE'){Publish-TrainState 'PAUSED' 'Независимая EVAL уже завершена; повторных запусков нет.';return}
    }
    $journal.sessionStartedUtc=[DateTime]::UtcNow.ToString('o');Write-TrainJson $path $journal
    while(!(Test-TrainRequest 'stop')){
        Assert-TrainAutonomousResources
        if(Test-TrainRequest 'pause'){Publish-TrainState 'PAUSED' 'Текущая атомарная операция сохранена.';return}
        if(([DateTime]::UtcNow-[DateTime]::Parse($journal.sessionStartedUtc).ToUniversalTime()).TotalMinutes -ge [int]$script:Config.autonomous009DurationMinutes){Publish-TrainState 'RESOURCE_LIMIT' 'DURATION_LIMIT';return}
        $script:State['autonomousCampaign']=$campaign;$script:State['autonomousCycle']=$journal.cycle;$script:State['autonomousFits']=$journal.fits;$script:State['autonomousNativeStarts']=$journal.starts;$script:State['autonomousNativeMillis']=$journal.nativeMillis
        $script:State['autonomousCheckpoint']=$journal.lastCheckpoint;$script:State['autonomousImproved']=$journal.improved
        if($journal.phase -ceq 'NEW'){
            Publish-TrainState 'CHECKPOINTING' 'Холодная проверка выбранных весов и terminal Adam.'
            Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('checkpoint',$layout.Root)
            $cold=Read-TrainJson (Join-Path $layout.Root 'autonomous\v1\cold-checkpoint.json')
            $journal.teacherVersion=[long]$cold.modelVersion;$journal.teacherSha=$cold.modelSha;$journal.dataset=$campaign+('-c{0:D6}' -f [int]$journal.cycle);$journal.phase='COLLECT';Write-TrainJson $path $journal
        }
        $script:Config.autonomous009ModelVersion=[string]$journal.teacherVersion;$script:Config.autonomous009ModelSha=$journal.teacherSha;$script:Config.outcome006DatasetId=$journal.dataset;$script:Config.autonomous009SourceCursor=[string]([int]$journal.cycle-1)
        $cohort=Join-Path $directory ('cohorts\'+$journal.dataset)
        if($journal.phase -ceq 'COLLECT'){
            $script:Config.autonomous009Phase='COLLECT';Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $script:Config
            if(!(Test-Path -LiteralPath (Join-Path $cohort 'collection.complete'))){Publish-TrainState 'COLLECTING';Invoke-TrainAutonomousNative $Dependencies $Jar $journal $path}
            if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){continue}
            $journal.phase='FIT';Write-TrainJson $path $journal
        }
        if($journal.phase -ceq 'FIT'){
            $script:Config.autonomous009Phase='FIT';Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $script:Config;Publish-TrainState 'FITTING'
            Invoke-TrainJava $Dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
            $evaluation=Read-TrainJson (Join-Path $layout.Root 'tactical-v3-evaluation.json')
            if($script:Config.ContainsKey('autonomous009AfterCommitFaultOnce') -and $script:Config.autonomous009AfterCommitFaultOnce -ceq 'true' -and [long]$evaluation.candidateVersion -gt 0){
                $fault=Join-Path $cohort 'after-commit-before-journal.once.json'
                if(!(Test-Path -LiteralPath $fault)){
                    Write-TrainJson $fault @{candidateVersion=$evaluation.candidateVersion;candidateSha=$evaluation.candidateSha;dataset=$journal.dataset;reason='OWN_ACCEPTANCE_POST_COMMIT_PRE_JOURNAL_FAULT';generation=$script:State.generation}
                    throw 'TRAIN009_AFTER_COMMIT_FAULT_ONCE'
                }
            }
            $journal.lastEvaluation=$evaluation
            if([long]$evaluation.candidateVersion -gt 0){$journal.fits=[int]$journal.fits+1;$journal.noData=0;$journal.phase='EVAL';$journal.improved=([double]$evaluation.metrics.tuningMse -lt [double]$evaluation.metrics.tuningInitialMse);if($journal.improved){$journal.stagnation=0}else{$journal.stagnation=[int]$journal.stagnation+1}}
            else{$journal.noData=[int]$journal.noData+1;$journal.phase='CHECKPOINT'}
            Write-TrainJson $path $journal
        }
        if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){continue}
        if($journal.phase -ceq 'EVAL'){
            if($script:Config.autonomous009EvaluationOnly -ceq 'true' -or [int]$journal.cycle % [int]$script:Config.autonomous009EvaluationEveryCycles -eq 0){
                $script:Config.autonomous009Phase='EVAL';$script:Config.autonomous009ModelVersion=[string]$journal.lastEvaluation.candidateVersion;$script:Config.autonomous009ModelSha=$journal.lastEvaluation.candidateSha;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $script:Config
                $evaluationDirectory=Join-Path $directory ('evaluations\'+$journal.dataset+'-'+$journal.lastEvaluation.candidateVersion)
                $completionName=if($outcomeV2){'evaluation-v2.complete'}else{'evaluation.complete'}
                if(!(Test-Path -LiteralPath (Join-Path $evaluationDirectory $completionName))){Publish-TrainState 'AUDITING' 'Независимые native A/B; без training credits.';Invoke-TrainAutonomousNative $Dependencies $Jar $journal $path}
                if((Test-TrainRequest 'stop') -or (Test-TrainRequest 'pause')){continue}
            }
            if($script:Config.autonomous009EvaluationOnly -ceq 'true'){
                $complete=Join-Path $evaluationDirectory $(if($outcomeV2){'evaluation-v2.complete'}else{'evaluation.complete'});$pairPath=Join-Path $evaluationDirectory $(if($outcomeV2){'evaluation-pairs-v2.json'}else{'evaluation-pairs.json'})
                if(!(Test-Path -LiteralPath $complete) -or !(Test-Path -LiteralPath $pairPath)){throw 'TRAIN010_EVAL_PROOF_MISSING'}
                $nativeProof=Read-TrainJson $complete;$pairs=Read-TrainJson $pairPath
                if($outcomeV2){
                    Assert-TrainOutcomeV2Proof $nativeProof $pairs
                    $journal.phase='EVAL_COMPLETE';Write-TrainJson $path $journal;Publish-TrainState 'PAUSED' 'Независимая V2 EVAL завершена; терминальные исходы учтены, обучение и cycle pointer сохранены.';return
                }
                $matched=@($pairs.pairs|Where-Object {$_.status -ceq 'MATCHED_COMPLETED'});$directions=@($matched|ForEach-Object {$_.direction})
                $ab=@($matched|Where-Object {$_.order -ceq 'STOCK_LEARNED'}).Count;$ba=@($matched|Where-Object {$_.order -ceq 'LEARNED_STOCK'}).Count
                if($nativeProof.trainingStateUnchanged -ne $true -or [int]$pairs.completedPairs -ne $matched.Count -or $matched.Count -lt 2 -or $ab -ne $ba -or
                   $directions -cnotcontains 'SOLO_DAMAGE' -or ($directions -cnotcontains 'PARTY_HEAL' -and $directions -cnotcontains 'PARTY_DAMAGE')){throw 'TRAIN010_EVAL_MATCHED_PAIR_COVERAGE'}
                $journal.phase='EVAL_COMPLETE';Write-TrainJson $path $journal;Publish-TrainState 'PAUSED' 'Независимая EVAL завершена без обучения и изменения cycle pointer.';return
            }
            $journal.phase='CHECKPOINT';Write-TrainJson $path $journal
        }
        if($journal.phase -ceq 'CHECKPOINT'){
            Publish-TrainState 'CHECKPOINTING';Invoke-TrainJava $Dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @('checkpoint',$layout.Root)
            $checkpoint=Join-Path $directory ('checkpoint-{0:D6}.json' -f [int]$journal.cycle)
            $cold=Read-TrainJson (Join-Path $layout.Root 'autonomous\v1\cold-checkpoint.json')
            if(Test-Path -LiteralPath $checkpoint){$prior=Read-TrainJson $checkpoint;if($prior.optimizerSha -cne $cold.optimizerSha){throw 'TRAIN009_CHECKPOINT_CHANGED'}}else{Write-TrainJson $checkpoint $cold}
            $journal.lastCheckpoint=$checkpoint;$journal.completedCycles=[int]$journal.completedCycles+1;$journal.cycle=[int]$journal.cycle+1;$journal.phase='NEW';Write-TrainJson $path $journal
            Invoke-TrainQueuedExport $Dependencies
            if([int]$journal.stagnation -ge 3){Publish-TrainState 'PAUSED' 'STAGNATION_LIMIT_3: кандидат сохранён; продвижения нет.';return}
            if([int]$journal.noData -ge [int]$script:Config.autonomous009NoDataLimit){Publish-TrainState 'INSUFFICIENT_EVIDENCE' 'Недостаточно независимых paid данных; сохранены и неудачные попытки.';return}
            if([int]$script:Config.autonomous009CycleLimit -gt 0 -and [int]$journal.completedCycles -ge [int]$script:Config.autonomous009CycleLimit){Publish-TrainState 'PAUSED' 'CYCLE_LIMIT';return}
            $resource=Join-Path $layout.Root ('attempts\'+$script:State.attemptId+'\native-resources.json');$facts=$null;if(Test-Path -LiteralPath $resource){$facts=Read-TrainJson $resource};$idle=Get-TrainAdaptiveIdle $script:Config $facts
            for($second=0;$second -lt $idle.idleSeconds -and !(Test-TrainRequest 'stop') -and !(Test-TrainRequest 'pause');$second++){Publish-TrainState 'COLLECTING' 'Ожидание CPU budget перед новым cohort.';Start-Sleep -Seconds 1}
        }
    }
}
function Publish-TrainStatus {
    if($Json){
        $result=[ordered]@{schemaVersion=9;installation=$layout.Root;directorLive=$false;phase='IDLE';reason='';sampledUtc=[DateTime]::UtcNow.ToString('o');director=$null;autonomous=$null;checkpoint=$null;episodes=$null}
        if(Test-Path -LiteralPath $layout.Root){
            Assert-TrainInstallation $layout;$live=Get-TrainLiveDirector;$result.directorLive=($null -ne $live)
            $statePath=Join-Path $layout.Root 'director.json'
            if(Test-Path -LiteralPath $statePath){
                $state=Read-TrainJson $statePath;$result.director=$state;$result.phase=$state.phase;$result.reason=$state.reason
                $heartbeat=Get-TrainHeartbeat $(if($state.PSObject.Properties['heartbeatUtc']){[string]$state.heartbeatUtc}else{''}) ([DateTime]::UtcNow)
                $result['heartbeat']=$heartbeat
                if($live -and $heartbeat.state -cne 'FRESH'){$result.phase='STALE';$result.reason='PID жив, но свежий heartbeat не подтверждён. Последняя фаза: '+$state.phase}
                if(!$live -and $state.phase -notin @('STOPPED','PAUSED','FAILED','RESOURCE_LIMIT','INSUFFICIENT_EVIDENCE') -and $state.phase -cnotmatch '^(PAUSED_|STOPPED_|FAILED_FAIL_CLOSED)'){$result.phase='STOPPED';$result.reason='Процесс Director завершён; требуется продолжение с проверкой журналов.'}
            }
            $config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
            if($config.ContainsKey('autonomous009Version') -and $config.autonomous009Campaign -cmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$'){
                $protocolDirectory=if($config.ContainsKey('autonomous013Version') -and $config.autonomous013Version -ceq '1'){'autonomous\v13'}else{'autonomous\v1'}
                $statusCampaign=if($protocolDirectory -ceq 'autonomous\v13' -and $config.ContainsKey('autonomous013ParentCampaign')){$config.autonomous013ParentCampaign}else{$config.autonomous009Campaign}
                if($statusCampaign -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$'){throw 'TRAIN013_STATUS_CAMPAIGN_SCOPE'}
                $campaign=Join-Path $layout.Root ($protocolDirectory+'\campaigns\'+$statusCampaign);$journal=Join-Path $campaign 'director-state.json'
                if(Test-Path -LiteralPath $journal){$result.autonomous=Read-TrainJson $journal}
                $checkpoint=Join-Path $layout.Root $(if($protocolDirectory -ceq 'autonomous\v13'){'autonomous\v13\sql-checkpoint.json'}else{'autonomous\v1\cold-checkpoint.json'});if(Test-Path -LiteralPath $checkpoint){$result.checkpoint=Read-TrainJson $checkpoint}
                if($result.autonomous -and $result.autonomous.dataset){
                    $cohort=Join-Path $layout.Root ('autonomous\v1\campaigns\'+$statusCampaign+'\cohorts\'+$result.autonomous.dataset)
                    if(Test-Path -LiteralPath $cohort){
                        Assert-TrainDirectory $cohort
                        $reserved=@(Get-ChildItem -LiteralPath $cohort -Directory | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'reserved.json')} | Select-Object -First 512)
                        $rows=@($reserved | ForEach-Object{$item=Join-Path $_.FullName 'result.json';if(Test-Path -LiteralPath $item){Read-TrainJson $item}})
                        $result.episodes=Get-TrainEpisodeSummary $rows $reserved.Count
                    }
                }
            }
        }
        [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);Write-Output ($result|ConvertTo-Json -Depth 20 -Compress);return
    }
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
        $null=Recover-TrainHeadlessControl013 $dependencies
        $script:Config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
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
        if($script:Config.ContainsKey('autonomous009Version')){Invoke-TrainAutonomousDirector $dependencies $jar;return}
        if($script:Config.ContainsKey('exploration008Enabled') -and $script:Config.exploration008Enabled -ceq 'true'){
            if($script:Config.exploration008Campaign -cne 'learning008-pve-generalization-v1' -or $script:Config.exploration008Dataset -cne 'learning008-residual-v1' -or $script:Config.exploration007Enabled -cne 'false'){throw 'TRAIN008_EXACT_CAMPAIGN_REQUIRED'}
            Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning008Native' @('prepare',$layout.Root)
            if($script:Config.exploration008Phase -ceq 'COLLECT'){
                $complete=Join-Path $layout.Root 'outcome006\campaigns\learning008-pve-generalization-v1\native.complete'
                if(!(Test-Path -LiteralPath $complete)){Reset-TrainColdBaseline;Invoke-Train008Native $dependencies $jar}
            }elseif($script:Config.exploration008Phase -ceq 'FIT'){
                Publish-TrainState 'EVALUATING' 'Factual residual008 fit; native collection is stopped.'
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.Learning008Checks' @('offline',$layout.Root)
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('report',$layout.Root)
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('export',$layout.Root)
            }elseif($script:Config.exploration008Phase -in @('EVALUATE','CONTROL')){
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('prepare',$layout.Root)
                $complete=Join-Path $layout.Root ('outcome006\evaluation008\'+$script:Config.exploration008EvaluationCampaign+'\native.complete')
                if(!(Test-Path -LiteralPath $complete)){Reset-TrainColdBaseline;Invoke-Train008Native $dependencies $jar}
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('report',$layout.Root)
            }elseif($script:Config.exploration008Phase -cne 'PREPARE'){throw 'TRAIN008_UNKNOWN_PHASE'}
            Publish-TrainState 'PAUSED' 'Граница008: сохранены native reservations, optimizer и история; автоматического повышения нет.'
            while(!(Test-TrainRequest 'stop')){Invoke-TrainQueuedExport $dependencies;Start-Sleep -Milliseconds 500}
            return
        }
        if($script:Config.ContainsKey('exploration007Enabled') -and $script:Config.exploration007Enabled -ceq 'true'){
            if($script:Config.exploration007Campaign -cne 'learning007-causal-v1' -or $script:Config.exploration007Dataset -cne 'learning007-advantage-v1'){throw 'TRAIN007_EXACT_CAMPAIGN_REQUIRED'}
            Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning007Native' @('prepare',$layout.Root)
            if($script:Config.exploration007Phase -ceq 'COLLECT'){
                $complete=Join-Path $layout.Root 'outcome006\campaigns\learning007-causal-v1\native.complete'
                if(!(Test-Path -LiteralPath $complete)){Reset-TrainColdBaseline;Invoke-Train007Native $dependencies $jar}
            }elseif($script:Config.exploration007Phase -ceq 'FIT'){
                if($script:Config.ContainsKey('exploration007VerifyRollback') -and $script:Config.exploration007VerifyRollback -ceq 'true'){
                    Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.Learning007Checks' @('fault-readback',$layout.Root)
                }
                Publish-TrainState 'EVALUATING' 'Advantage007 offline fit; native collection is stopped.'
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.Learning007Checks' @('offline',$layout.Root)
                Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('export',$layout.Root)
            }elseif($script:Config.exploration007Phase -in @('EVALUATE','CONTROL')){
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('prepare',$layout.Root)
                Reset-TrainColdBaseline;Invoke-Train007Native $dependencies $jar
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('report',$layout.Root)
            }elseif($script:Config.exploration007Phase -cne 'PREPARE'){throw 'TRAIN007_UNKNOWN_PHASE'}
            Publish-TrainState 'PAUSED' 'Advantage007 segment settled; history and native reservations retained.'
            while(!(Test-TrainRequest 'stop')){Invoke-TrainQueuedExport $dependencies;Start-Sleep -Milliseconds 500}
            return
        }
        if($script:Config.ContainsKey('configProfile') -and $script:Config.configProfile -ceq 'OUTCOME006'){
            if(!$script:Config.ContainsKey('outcome006Phase') -or $script:Config.outcome006Phase -notin @('PREPARE','COLLECT','PROOF','EVALUATE','CONTROL')){throw 'OUTCOME006_PHASE_NOT_IMPLEMENTED'}
            if($script:Config.outcome006Phase -ceq 'COLLECT'){
                $outcomeCampaign=Join-Path $layout.Root 'outcome006\collection'
                if($script:Config.ContainsKey('outcome006Campaign') -and $script:Config.outcome006Campaign){
                    if($script:Config.outcome006Campaign -cne 'learning006r1' -or $script:Config.outcome006DatasetId -cne 'outcome006r1-v1'){throw 'OUTCOME006R1_EXACT_CAMPAIGN_REQUIRED'}
                    $outcomeCampaign=Join-Path $layout.Root 'outcome006\campaigns\learning006r1'
                    $clock=Join-Path $outcomeCampaign 'active.nanos'
                    if((Test-Path -LiteralPath $clock) -and !(Test-Path -LiteralPath (Join-Path $outcomeCampaign 'native.complete'))){
                        Assert-G0PhysicalFile $clock
                        if([long][IO.File]::ReadAllText($clock) -ge 5400000000000){Write-TrainText (Join-Path $outcomeCampaign 'native.complete') "TIME_BUDGET_EXHAUSTED_OR_UNCLOSED_SEGMENT`n"}
                    }
                }
                # Consumed slots remain immutable across native segments and fresh Director generations.
                if(!(Test-Path -LiteralPath (Join-Path $outcomeCampaign 'native.complete'))){
                    if($script:Config.ContainsKey('outcome006Campaign') -and $script:Config.outcome006Campaign -ceq 'learning006r1'){
                        Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks' @('before-r1',$layout.Root)
                    }
                    Reset-TrainColdBaseline
                    Invoke-TrainNative $dependencies $jar
                }
                if(!(Test-TrainRequest 'stop') -and (Test-Path -LiteralPath (Join-Path $outcomeCampaign 'native.complete'))){
                    Publish-TrainState 'EVALUATING' 'Проверка terminal targets006; только existing optimizer continuation.'
                    Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('train',$layout.Root)
                    Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @('report',$layout.Root)
                }
            }
            elseif($script:Config.outcome006Phase -in @('PROOF','EVALUATE','CONTROL')){
                if(!$script:Config.ContainsKey('outcome006Campaign') -or $script:Config.outcome006Campaign -cne 'learning006r1'){throw 'OUTCOME006R1_EXPLICIT_EVALUATION_CAMPAIGN_REQUIRED'}
                Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('prepare',$layout.Root)
                if($script:Config.outcome006Phase -ceq 'PROOF'){Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.Learning006OutcomeChecks' @('commit-r1',$layout.Root)}
                $evaluationName=$script:Config.outcome006Phase.ToLowerInvariant()
                if($script:Config.ContainsKey('outcome006EvaluationCampaign')){$evaluationName=$script:Config.outcome006EvaluationCampaign}
                if($evaluationName -cnotmatch '^[a-z][a-z0-9_-]{0,63}$' -or !$evaluationName.StartsWith($script:Config.outcome006Phase.ToLowerInvariant(),[StringComparison]::Ordinal)){throw 'OUTCOME006R1_EVALUATION_NAMESPACE_REQUIRED'}
                $evaluationDirectory=Join-Path $layout.Root ('outcome006\evaluation006r1\'+$evaluationName)
                if(!(Test-Path -LiteralPath (Join-Path $evaluationDirectory 'native.complete'))){Reset-TrainColdBaseline;Invoke-TrainNative $dependencies $jar}
                if(!(Test-TrainRequest 'stop')){Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @('report',$layout.Root)}
            }
            Publish-TrainState 'PAUSED' 'Граница006: native budget сохранён; следующая фаза требует отдельного exact candidate pin.'
            while(!(Test-TrainRequest 'stop')){Invoke-TrainQueuedExport $dependencies;Start-Sleep -Milliseconds 500}
            return
        }
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
        $reason=if($_.Exception.Message -match 'TRAIN(?:002|009|013)_[A-Z0-9_:]+'){$Matches[0]}else{$_.Exception.GetType().Name}
        Publish-TrainState $(if($reason -like '*_RESOURCE_*' -or $reason -like '*_BUDGET_*'){'RESOURCE_LIMIT'}elseif($script:Config -and $script:Config.ContainsKey('autonomous013Version') -and $script:Config.autonomous013Version -ceq '1'){'FAILED_FAIL_CLOSED'}else{'FAILED'}) $reason
        throw
    }finally{
        $clean=$false
        try{
            if($script:Config){Stop-TrainNative ([int]$script:Config.shutdownTimeoutSeconds)}
            if($script:Config){Wait-TrainWorker ([int](Get-TrainHeadlessCleanup013 $script:Config).shutdownTimeoutSeconds)}
            if($script:SqlRecord -and !(Test-TrainProcessAbsent $script:SqlRecord)){Stop-TrainHeadlessSql013 (Get-TrainHeadlessCleanup013 $script:Config) $script:SqlRecord}
            if($script:Config){Assert-TrainPortsFree @([int]$script:Config.sqlPort,[int]$script:Config.gamePort,[int]$script:Config.loginPort)}
            if(Test-Path -LiteralPath (Join-Path $layout.Root 'protected207.json')){Assert-TrainAssets (Join-Path $layout.Game 'geodata') (Join-Path $layout.Root 'geo203.json') (Join-Path $layout.Root 'protected207.json') $script:Config}
            $clean=$true
            if($script:State.phase -notin @('FAILED','PAUSED','RESOURCE_LIMIT','INSUFFICIENT_EVIDENCE') -and $script:State.phase -cnotmatch '^(PAUSED_|STOPPED_|FAILED_FAIL_CLOSED)'){Publish-TrainState 'STOPPED'}
        }catch{
            $null=Save-TrainFailure $layout $script:State $_ 'CLEANUP'
            throw
        }finally{
            if(!$clean){Publish-TrainState 'STOP_UNCONFIRMED' 'Сохранены данные и receipts; чужие процессы не останавливались.'}
            $lock.Dispose()
        }
    }
}

# Repair has no startup/config/failure-history path and never opens Java, SQL or GS.
if($Action -ceq 'RepairClosure017'){
    try{
        Initialize-TrainWin32
        $closed=Invoke-TrainRepairClosure017 $Root $RepairEvidenceRoot $ColdLineageProof
        Write-Output ('TRAIN017_REPAIR '+$closed.status+' JAVA=0 SQL=0 GS=0 FIT=0')
        exit 0
    }catch{Write-TrainSafeError $_;exit 1}
}
# Status does not require Java, SQL, build artifacts or asset copies.
try {
    if($Action -eq 'Prepare' -and !$Autonomous){throw 'TRAIN002_PREPARE_AUTONOMOUS_REQUIRED'}
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
    if($Action -cin @('Stop','Pause') -and (Test-Path -LiteralPath $layout.Root)){
        $active=Get-TrainHeadlessActive013
        if($active -and !$active.terminal -and $active.journal.worker -and !(Get-TrainLiveDirector)){
            if(!(Test-TrainHeadlessController013 $active.journal.worker)){
                $initialization=Enter-TrainInitialization $layout
                try{$null=Recover-TrainHeadlessControl013 (Get-TrainDependencies) -Cancel}finally{$initialization.ReleaseMutex();$initialization.Dispose()}
                Write-Host 'Отмена headless обработана: собственные worker и SQL завершены, receipts сохранены.';exit 0
            }
            $null=Request-TrainHeadlessCancel013
            Write-Host 'Отмена headless запрошена. Собственный controller завершит worker и SQL в сохранённом drain deadline; это ещё не подтверждение STOPPED.'
            exit 0
        }
    }
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
        if($Action -cin @('Start','Resume','Prepare') -and !$live){$null=Recover-TrainHeadlessControl013 (Get-TrainDependencies);$script:Config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')}
        if($Action -in @('Start','Resume')){
            if($live){
                if($Action -eq 'Resume'){Request-TrainBoundary 'resume' $live.nonce;Write-Host 'Продолжение запрошено; committed история сохранена.'}
                else{Write-Host 'Собственный директор уже работает; второй запуск не создан.'}
                Publish-TrainStatus;exit 0
            }
            # Probe the lifetime lock before launch. The initialization mutex closes
            # the fresh-root race; the detached Run owns this file lock for its life.
            $probe=Enter-TrainLock $layout;$probe.Dispose()
            if($Autonomous -or $script:Config.ContainsKey('autonomous009Version')){
                $configPath=Join-Path $layout.Root 'TRAIN_CONFIG.ini';$configSha=(Get-FileHash -LiteralPath $configPath).Hash
                $archive=Join-Path $layout.Root ('archives\TRAIN_CONFIG.pre-start.'+$configSha.ToLowerInvariant()+'.ini')
                if(Test-Path -LiteralPath $archive){Assert-TrainHash $archive $configSha}else{[IO.File]::Copy($configPath,$archive,$false);Assert-TrainHash $archive $configSha}
                $script:Config=Read-TrainSourceConfig;Assert-TrainReadinessMode015 $script:Config $Readiness015Session;Write-TrainConfig $configPath $script:Config
            }
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
        if($Action -in @('Report','Export','Prepare')){
            $dependencies=Get-TrainDependencies
            # A live director owns SQL/worker scheduling, including its evaluation
            # boundary. Export gets a separate durable queue, never a new client.
            if($live){
                if($Action -eq 'Prepare'){throw 'TRAIN002_PREPARE_REQUIRES_STOPPED_DIRECTOR'}
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
                if($Autonomous -and $Action -in @('Report','Prepare')){
                    $null=Test-TrainOutcomeV2 $script:Config
                    $jar=Ensure-TrainBuild $dependencies
                    $assets=Get-TrainAssets;Ensure-TrainStage $jar $assets
                    $script:ReadinessOnly013=$Action -ceq 'Prepare' -and $script:Config.ContainsKey('autonomous013Version') -and $script:Config.autonomous013Version -ceq '1' -and $script:Config.autonomous009EvaluationOnly -cne 'true'
                    if($script:ReadinessOnly013){
                        $passVersion=if(Test-TrainIncremental016 $script:Config){16}else{13}
                        $passRoot=Join-Path $layout.Root ('autonomous\v'+$passVersion+'\campaigns\'+$script:Config.autonomous009Campaign);$null=[IO.Directory]::CreateDirectory($passRoot);Assert-TrainDirectory $passRoot
                        Restore-TrainHeadlessConfig013;$script:Config=Read-TrainSourceConfig;Assert-TrainReadinessMode015 $script:Config $Readiness015Session;Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $script:Config
                        $passPath=Join-Path $passRoot 'readiness-issued013.json';$configSha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'TRAIN_CONFIG.ini')).Hash
                        if(Test-TrainIncremental016 $script:Config){
                            if($script:ReadinessGoalVersion -ne 16 -or $script:ReadinessSession -lt 1){throw 'TRAIN016_BOUNDED_READINESS_REQUIRED'}
                            $budgetRoot=Join-Path $layout.Root 'autonomous\v16\readiness';$null=[IO.Directory]::CreateDirectory($budgetRoot);Assert-TrainDirectory $budgetRoot
                            $issuedPath014=Join-Path $budgetRoot ('session'+$script:ReadinessSession+'-issued.json')
                            if(Test-Path -LiteralPath $issuedPath014){throw 'TRAIN016_SESSION_ALREADY_ISSUED_NO_REPLAY'}
                            $priorResults=@()
                            if($script:ReadinessSession -gt 1){foreach($priorOrdinal in 1..($script:ReadinessSession-1)){
                                $prior=Read-TrainJson (Join-Path $budgetRoot ('session'+$priorOrdinal+'-result.json'))
                                Assert-TrainHash (Join-Path $budgetRoot ('session'+$priorOrdinal+'-issued.json')) $prior.issuedSha
                                $priorResults+=@($prior)
                            }}
                            $extra016=if($script:Config.autonomous009Campaign -ceq 'autonomous009-learning016-g2-v1'){100160L}else{0L}
                            $pass=Get-TrainReadinessWindow016 $script:ReadinessSession $priorResults -HistoricalExtraMillis $extra016
                            Save-TrainReadinessControl016 $passRoot $script:ReadinessSession
                            $pass|Add-Member configSha $configSha;$pass|Add-Member jarSha $jar
                            Write-TrainJson $issuedPath014 $pass;Write-TrainJson $passPath $pass
                        }
                        elseif(Test-Path -LiteralPath $passPath){$pass=Read-TrainJson $passPath;if($pass.configSha -cne $configSha -or $pass.jarSha -cne $jar){throw 'TRAIN013_READINESS_COLD_PIN'}}
                        else{
                            if($script:ReadinessSession -gt 0){
                                $budgetRoot=Join-Path $layout.Root ('autonomous\v'+$script:ReadinessGoalVersion+'\readiness');$null=[IO.Directory]::CreateDirectory($budgetRoot);Assert-TrainDirectory $budgetRoot
                                $issuedPath014=Join-Path $budgetRoot ('session'+$script:ReadinessSession+'-issued.json')
                                if(Test-Path -LiteralPath $issuedPath014){throw 'TRAIN014_SESSION_ALREADY_ISSUED'}
                                $priorPath=Join-Path $budgetRoot 'session1-result.json';$prior=if(Test-Path -LiteralPath $priorPath){Read-TrainJson $priorPath}else{$null}
                                $pass=if($script:ReadinessGoalVersion -eq 15){Get-TrainReadinessWindow015 $script:ReadinessSession $prior}else{Get-TrainReadinessWindow014 $script:ReadinessSession $prior}
                                $pass|Add-Member configSha $configSha;$pass|Add-Member jarSha $jar
                                Write-TrainJson $issuedPath014 $pass
                            }else{$issued=[DateTime]::UtcNow;$pass=[pscustomobject]@{schemaVersion=13;nonce=[guid]::NewGuid().ToString('N');issuedUtc=$issued.ToString('o');hardDeadlineUtc=$issued.AddSeconds(1200).ToString('o');configSha=$configSha;jarSha=$jar;maximumSeconds=1200;drainReserveSeconds=210;trainingCredit=$false}}
                            Write-TrainJson $passPath $pass
                        }
                        $maximum=if($script:ReadinessSession -gt 0){if(!$pass.PSObject.Properties['goalVersion'] -or $pass.goalVersion -ne $script:ReadinessGoalVersion -or $pass.session -ne $script:ReadinessSession){throw 'TRAIN014_SESSION_PIN'};if($script:ReadinessGoalVersion -eq 16){[int]$pass.maximumSeconds}elseif($script:ReadinessSession -eq 1){480}else{720}}else{1200}
                        if($script:ReadinessGoalVersion -eq 16 -and ($maximum -le 210 -or $maximum -gt 600)){throw 'TRAIN016_HEADLESS_CAP'}
                        if($pass.schemaVersion -ne 13 -or $pass.nonce -cnotmatch '^[a-f0-9]{32}$' -or $pass.maximumSeconds -ne $maximum -or $pass.drainReserveSeconds -ne 210 -or $pass.trainingCredit -ne $false -or (([datetime]$pass.hardDeadlineUtc).ToUniversalTime()-([datetime]$pass.issuedUtc).ToUniversalTime()).TotalSeconds -ne $maximum){throw 'TRAIN013_READINESS_DEADLINE_PIN'}
                        $script:ReadinessDeadline013=$pass;$script:HeadlessCleanupDeadline013=([datetime]$pass.hardDeadlineUtc).ToUniversalTime()
                        if([DateTime]::UtcNow -ge $script:HeadlessCleanupDeadline013){throw 'TRAIN013_HEADLESS_TIMEOUT'}
                        $script:DirectorNonce=$pass.nonce
                        $script:State=[ordered]@{schemaVersion=1;runId=$pass.nonce;generation=0;nonce=$pass.nonce;phase='PREPARING';reason='Bounded headless readiness only; GS/FIT/credit disabled.';heartbeatUtc='';attemptId='';batchesCompleted=0;cpuSeconds=0;workingSetBytes=0;primaryFailureId='';primaryFailureCode='';primaryFailurePhase='';cleanupFailureId='';readinessOnly=$true}
                    }
                    $script:SqlRecord=Start-TrainSql $dependencies
                    $operation=if($Action -eq 'Prepare' -and $script:Config.autonomous009EvaluationOnly -ceq 'true'){'admit-eval'}elseif($Action -eq 'Prepare'){'prepare'}else{'inspect'}
                    if($script:ReadinessOnly013){Invoke-TrainAutonomousDirector013 $dependencies $jar;if(!(Test-Path -LiteralPath (Join-Path $passRoot 'readiness-complete013.json'))){throw 'TRAIN013_HEADLESS_INSUFFICIENT_EVIDENCE'}}
                    else{Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomAutonomousTrainingNative' @($operation,$layout.Root)}
                }elseif($script:Config.ContainsKey('evaluation005Enabled') -and $script:Config.evaluation005Enabled -ceq 'true'){
                    Invoke-TrainJava $dependencies 'org.l2jmobius.tests.phantoms.PhantomLearning005Native' @($Action.ToLowerInvariant(),$layout.Root)
                }else{
                    $script:SqlRecord=Start-TrainSql $dependencies
                    Invoke-TrainJava $dependencies 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' @($Action.ToLowerInvariant(),$layout.Root)
                }
            }finally{
                Wait-TrainWorker ([int](Get-TrainHeadlessCleanup013 $script:Config).shutdownTimeoutSeconds)
                if($script:SqlRecord -and !(Test-TrainProcessAbsent $script:SqlRecord)){Stop-TrainHeadlessSql013 (Get-TrainHeadlessCleanup013 $script:Config) $script:SqlRecord}
                if($script:ReadinessOnly013){
                    $finished=[DateTime]::UtcNow;$ready=Test-Path -LiteralPath (Join-Path $passRoot 'readiness-complete013.json');$within=$finished -le ([datetime]$pass.hardDeadlineUtc).ToUniversalTime();$lastStatus=if(Test-Path -LiteralPath (Join-Path $layout.Root 'headless-active013.json')){(Read-TrainJson (Join-Path (Read-TrainJson (Join-Path $layout.Root 'headless-active013.json')).scope 'headless-operation013.json')).status}else{'ERROR'};$result=Get-TrainHeadlessReadinessVerdict013 $ready $lastStatus $within
                    Write-TrainJson (Join-Path $passRoot 'readiness-result013.json') @{schemaVersion=13;status=$result;finishedUtc=$finished.ToString('o');elapsedMillis=[long]($finished-([datetime]$pass.issuedUtc).ToUniversalTime()).TotalMilliseconds;withinCap=($finished -le ([datetime]$pass.hardDeadlineUtc).ToUniversalTime());ownedWorkerAbsent=$true;sqlAbsent=(!$script:SqlRecord -or (Test-TrainProcessAbsent $script:SqlRecord));nativeStarted=$false;trainingCredit=$false}
                    if($script:ReadinessSession -gt 0){
                        $result014=Read-TrainJson (Join-Path $passRoot 'readiness-result013.json');$result014.schemaVersion=$script:ReadinessGoalVersion;$result014|Add-Member session $script:ReadinessSession
                        if($script:ReadinessGoalVersion -eq 16){$result014|Add-Member issuedSha (Get-FileHash -LiteralPath (Join-Path $layout.Root ('autonomous\v16\readiness\session'+$script:ReadinessSession+'-issued.json'))).Hash}
                        $resultPath014=Join-Path $layout.Root ('autonomous\v'+$script:ReadinessGoalVersion+'\readiness\session'+$script:ReadinessSession+'-result.json');if(Test-Path -LiteralPath $resultPath014){throw 'TRAIN014_RESULT_ALREADY_EXISTS'};Write-TrainJson $resultPath014 $result014
                    }
                    $script:Config.autonomous009Enabled='false';$script:Config.autonomous009Phase='OFF';$script:Config.outcome006Enabled='false';Write-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini') $script:Config
                    Publish-TrainState $(if($result -ceq 'READY'){'STOPPED'}elseif($result -ceq 'CANCELLED'){'STOPPED_BY_OWNER'}elseif($result -ceq 'INSUFFICIENT_EVIDENCE'){'INSUFFICIENT_EVIDENCE'}elseif($result -ceq 'TIMEOUT'){'RESOURCE_LIMIT'}else{'FAILED_FAIL_CLOSED'}) ('TRAIN013_HEADLESS_'+$result)
                    if(!$within){throw 'TRAIN013_HEADLESS_TOTAL_DEADLINE_EXCEEDED'}
                }
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
    $reason=if($controlFailure.Exception.Message -match 'TRAIN(?:002|009|013)_[A-Z0-9_:]+'){$Matches[0]}else{$controlFailure.Exception.GetType().Name}
    Write-Host ('Команда не завершена: '+$reason+'. Данные сохранены; смотрите TRAIN_STATUS и приватные логи установки.')
    exit 1
}
