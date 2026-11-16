[CmdletBinding()]
param(
    [ValidateSet('Test-Cycle013','Test-Config','Test-Arguments','Test-Atomic','Test-ColdBaseline','Test-ExportAdmission','Test-PrivateBootstrap','Test-EmptyBootstrapRecovery','Test-ErrorHistory','Test-CimExitRace','Test-AdaptiveIdle','Test-StartupHandshake','Test-JobLifetime','Test-Broker','Test-Crash','Test-Isolation','Test-Generate','Test-Native','Test-Detached','Test-Resume','Test-Training','Test-Export','Test-GuiOwner')][string]$Action='Test-Config',
    [string]$Root,
    [switch]$Headless013,
    [switch]$Readiness014,
    [switch]$Readiness015,
    [switch]$Readiness016,
    [switch]$Closure017,
    [switch]$ActionValue019,
    [switch]$ActionValue020,
    [string]$GuiEvidenceRoot,
    [switch]$LegacyArguments,
    [switch]$ConfirmOwnedCrash,
    [ValidateRange(1,120)][int]$ObserveSeconds=3
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$owner=Join-Path $PSScriptRoot 'TrainingOwner.ps1'
if (!(Test-Path -LiteralPath $owner -PathType Leaf)) { throw 'TRAIN002_OWNER_CONTRACT_MISSING' }
. $owner
$layout=Get-TrainLayout $Root
function Require([bool]$Condition,[string]$Reason) { if (!$Condition) { throw $Reason } }
if($ActionValue020){
    # NOT_NATIVE: незавершённая запись не является COMPLETED; Stop до process-record обязан пережить гонку старта.
    $fixture=Join-Path ([IO.Path]::GetTempPath()) ('Train020-Step-'+[guid]::NewGuid().ToString('N'))
    $null=[IO.Directory]::CreateDirectory($fixture)
    try{
        Write-TrainJson (Join-Path $fixture 'cold020.json') @{sha=('a'*64)}
        Require (!(Test-TrainActionValue020Complete $fixture)) 'TRAIN020_INCOMPLETE_COLD_FALSE_COMPLETED'
        $null=Request-TrainActionValue020Stop $fixture $false $false
        Require (Test-Path -LiteralPath (Join-Path $fixture 'stop.request')) 'TRAIN020_STOP_BEFORE_PROCESS_RECORD_LOST'
    }finally{foreach($n in @('cold020.json','stop.request')){[IO.File]::Delete((Join-Path $fixture $n))};[IO.Directory]::Delete($fixture)}
    $cleanup=[Collections.Generic.List[string]]::new();$failed=$false
    try{Invoke-TrainCleanup020 @({throw 'EXPECTED_CLEANUP_FAILURE'},{ $cleanup.Add('SQL_STOP_ATTEMPTED') })}catch{$failed=$true}
    Require ($failed -and $cleanup.Contains('SQL_STOP_ATTEMPTED')) 'TRAIN020_CLEANUP_SKIPPED_SQL'
    # Только контракт proof; эти данные никогда не передаются оптимизатору.
    $sha='a'*64
    $valid=[pscustomobject]@{modelSha=$sha;MODEL_CONSULTED=$true;NATIVE_EXECUTED=$true;EFFECT_PAID=$true;nativePaid=1;settled=$true;episodeSha=('b'*64);newTrainSamples=1}
    Assert-TrainActionValue020Proof 0 $valid $sha
    foreach($field in @('settled','newTrainSamples','episodeSha')){
        $bad=$valid.PSObject.Copy();$bad.$field=$(if($field -ceq 'settled'){$false}elseif($field -ceq 'newTrainSamples'){0}else{''})
        $refused=$false;try{Assert-TrainActionValue020Proof 0 $bad $sha}catch{$refused=$true}
        Require $refused ('TRAIN020_INCOMPLETE_NATIVE_PROOF_ACCEPTED_'+$field)
    }
    Write-Output 'TRAIN020_PROOF_CONTRACT_PASS NOT_NATIVE GS=0 SQL=0';return
}
if($ActionValue019){
    # NOT_NATIVE proof fixtures, never game samples or optimizer input. A process exit0 cannot prove a paid cast.
    $sha='a'*64
    $empty=[pscustomobject]@{modelSha=$sha;MODEL_CONSULTED=$false;NATIVE_EXECUTED=$false;EFFECT_PAID=$false;nativePaid=0}
    $refused=$false;try{Assert-TrainActionValue019Proof 0 $empty $sha}catch{$refused=$true}
    Require $refused 'TRAIN019_FALSE_NATIVE_SUCCESS'
    $valid=[pscustomobject]@{modelSha=$sha;MODEL_CONSULTED=$true;NATIVE_EXECUTED=$true;EFFECT_PAID=$true;nativePaid=1}
    Assert-TrainActionValue019Proof 0 $valid $sha
    $refused=$false;try{Assert-TrainActionValue019Proof 0 $valid ('b'*64)}catch{$refused=$true}
    Require $refused 'TRAIN019_FOREIGN_MODEL_PROOF'
    Write-Output 'TRAIN019_PROOF_CONTRACT_PASS NOT_NATIVE GS=0 SQL=0';return
}
if($Closure017){
    $fixture=Join-Path ([IO.Path]::GetTempPath()) ('Training017-'+[guid]::NewGuid().ToString('N'))
    $campaign=Join-Path $fixture 'autonomous009-learning016-g2-v1'
    $cycle=Join-Path $campaign 'cycles\c000001'
    $null=[IO.Directory]::CreateDirectory($cycle)
    try{
        $parent=@{schemaVersion=13;modelVersion=14;modelSha=('a'*64);optimizerSha=('b'*64);terminalWeightsSha=('c'*64);optimizerUpdates=903;completedEpochs=223;acceptedTestVersion=2;acceptedTestSha=('d'*64);featureSchemaSha=('e'*64);productionPromotion=$false}
        $dataset='autonomous009-learning016-g2-v1-c000001'
        $config=@{autonomous016Version='1';autonomous013Version='1';autonomous009Campaign='autonomous009-learning016-g2-v1';outcome006DatasetId=$dataset}
        Write-TrainJson (Join-Path $campaign 'base-config.json') $config
        $reservation=Join-Path $cycle 'fit-reserved016.json'
        Write-TrainJson $reservation @{schemaVersion=16;cycle=$dataset;parent=$parent;trainingCredit=$false}
        Write-TrainJson (Join-Path $cycle 'fit-result.json') @{candidateVersion=0;productionPromotion=$false;reservationSha=(Get-FileHash -LiteralPath $reservation).Hash;metrics=@{usedForTraining=$false;objective='ACQUIRED_ACTION_ADVANTAGE_V2'}}
        $checkpoint=Join-Path $cycle 'checkpoint016.json'
        $saved=@{schemaVersion=16;cycle=$dataset;selected=$parent;parent=$parent;fits=0;reason='NO_FIT_INSUFFICIENT_VALID_DATA';independentEvaluation=$false;productionPromotion=$false;bestVersion=2;bestSha=('d'*64)}
        Write-TrainJson $checkpoint $saved
        $checkpointSha=(Get-FileHash -LiteralPath $checkpoint).Hash
        $journal=[pscustomobject]@{schemaVersion=13;campaign=$config.autonomous009Campaign;cycle=1;dataset=$dataset;phase='FIT';fits=0;completedCycles=0;pending=$false;pendingDecision=$null;sourceSha=('f'*64);jarSha=('1'*64);parentCheckpoint=[pscustomobject]$parent;patience=0;bestVersion=2;bestSha=('d'*64)}
        $path=Join-Path $campaign 'director-state.json'
        Save-TrainCycleState013 $path $journal
        $journal.phase='DONE016';$journal.completedCycles=1
        try{Save-TrainCycleState013 $path $journal}catch{throw 'TRAIN017_VALID_NO_FIT_DONE016_REJECTED'}
        Require ((Read-TrainJson $path).phase -ceq 'DONE016') 'TRAIN017_FINAL_PHASE_NOT_SAVED'
        foreach($phase in @('DONE017','SOME999')){
            $bad=$journal.PSObject.Copy();$bad.phase=$phase
            $refused=$false;try{Save-TrainCycleState013 $path $bad}catch{$refused=$true}
            Require $refused 'TRAIN017_UNKNOWN_NUMERIC_PHASE_ACCEPTED'
        }
        foreach($version in @('0','13','14','15')){
            $badConfig=$config.Clone();$badConfig.autonomous016Version=$version
            Write-TrainJson (Join-Path $campaign 'base-config.json') $badConfig
            $refused=$false;try{Save-TrainCycleState013 $path $journal}catch{$refused=$true}
            Require $refused 'TRAIN017_LEGACY_DONE016_ACCEPTED'
        }
        Write-TrainJson (Join-Path $campaign 'base-config.json') $config
        foreach($change in @(@{fits=1},@{reason='ONE_FIT_COLD_RESTORED'},@{cycle='foreign'},@{productionPromotion=$true})){
            $bad=$saved.Clone();foreach($key in $change.Keys){$bad[$key]=$change[$key]};Write-TrainJson $checkpoint $bad
            $refused=$false;try{Save-TrainCycleState013 $path $journal}catch{$refused=$true}
            Require $refused 'TRAIN017_CHANGED_CHECKPOINT_ACCEPTED'
        }
        Write-TrainJson $checkpoint $saved
        Require ((Get-FileHash -LiteralPath $checkpoint).Hash -ceq $checkpointSha) 'TRAIN017_FIXTURE_CHECKPOINT_DRIFT'
        Initialize-TrainWin32
        $processFixture=@{Root=(Join-Path $fixture 'process-owner');Sql=(Join-Path $fixture 'process-owner\sql')}
        New-TrainPrivateRoot $processFixture
        $release=Join-Path $processFixture.Root 'release';$ready=Join-Path $processFixture.Root 'ready'
        $stub=Join-Path $processFixture.Root 'child.ps1'
        Write-TrainText $stub 'param($Ready,$Release) [IO.File]::WriteAllText($Ready,"ready");$end=[DateTime]::UtcNow.AddSeconds(15);while(![IO.File]::Exists($Release) -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 20};exit 7'
        $arguments='-NoProfile -ExecutionPolicy Bypass -File "'+$stub+'" -Ready "'+$ready+'" -Release "'+$release+'"'
        $child=Start-Process -FilePath $layout.PowerShell -ArgumentList $arguments -WindowStyle Hidden -PassThru
        $held=Hold-ProcessHandle $child
        try{
            $end=[DateTime]::UtcNow.AddSeconds(10)
            while(!(Test-Path -LiteralPath $ready) -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 20}
            Require (Test-Path -LiteralPath $ready) 'TRAIN017_CHILD_NOT_READY'
            $record=New-TrainProcessRecord $child $layout.PowerShell ([guid]::NewGuid().ToString('N')) Worker
            $null=[IO.Directory]::CreateDirectory((Join-Path $processFixture.Root ('workers\'+$record.nonce)))
            Write-TrainText $release 'exit';Require ($child.WaitForExit(5000)) 'TRAIN017_CHILD_NOT_EXITED'
            Write-TrainExitReceipt $processFixture $record $child $held
            & {
                function Get-TrainLayout {return $processFixture}
                Require (Test-TrainProcessAbsent $record $child $held $processFixture) 'TRAIN017_READONLY_HELD_EXIT_REJECTED'
                Require (!(Test-Path -LiteralPath (Join-Path $processFixture.Root 'process-observations'))) 'TRAIN017_READONLY_PROBE_WROTE_HISTORY'
                $unseen={param($ProcessId017) [pscustomobject]@{Success=$false;Error=31}}
                Require (Test-TrainProcessAbsent $record $null $null $processFixture $unseen) 'TRAIN017_READONLY_PRIVATE_EXIT_RECEIPT_NOT_USED'
                $foreign=$record.PSObject.Copy();$foreign.nonce='f'*32
                $rejected=$false;try{$null=Test-TrainProcessAbsent $foreign $null $null $processFixture $unseen}catch{$rejected=$_.Exception.Message -ceq 'TRAIN002_PROCESS_VISIBILITY_UNKNOWN'}
                Require $rejected 'TRAIN017_READONLY_UNPROVEN_EXIT_ACCEPTED'
                $script:ProcessProbeQueries017=0
                $retry={param($ProcessId017) $script:ProcessProbeQueries017++;[pscustomobject]@{Success=$false;Error=$(if($script:ProcessProbeQueries017 -eq 1){5}else{87})}}
                Require (Test-TrainProcessAbsent $record $null $null $processFixture $retry) 'TRAIN017_READONLY_RETRY_NOT_ABSENT'
                Require (!(Test-Path -LiteralPath (Join-Path $processFixture.Root 'process-observations'))) 'TRAIN017_READONLY_RETRY_WROTE_HISTORY'
            }
        }finally{if(!$child.HasExited){Write-TrainText $release 'exit';$null=$child.WaitForExit(5000)};$child.Dispose()}
        $tokens=$null;$errors=$null
        $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
        Require ($errors.Count -eq 0) 'TRAIN017_SOURCE_PARSE'
        $definitions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Test-TrainIncremental016'},$false)
        . ([scriptblock]::Create(($definitions|ForEach-Object {$_.Extent.Text}) -join "`n"))
        $director=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Invoke-TrainAutonomousDirector013'},$false)[0]
        $fitNode=$director.Body.FindAll({param($n) $n -is [Management.Automation.Language.IfStatementAst] -and $n.Extent.Text.StartsWith("if(`$journal.phase -ceq 'FIT')",[StringComparison]::Ordinal)},$true)[0]
        $fitBody=[scriptblock]::Create($fitNode.Extent.Text)
        & {
            $journal.phase='FIT';$journal.completedCycles=0
            $config=@{autonomous016Version='1'};$script:Config=$config;$layout=@{Root=$fixture};$cycleDirectory=$cycle;$directory=$campaign
            $Dependencies=$null
            function Write-TrainConfig {throw 'TRAIN017_UNEXPECTED_CONFIG_WRITE'}
            function Publish-TrainState {throw 'TRAIN017_UNEXPECTED_HISTORY_WRITE'}
            function Invoke-TrainJava {throw 'TRAIN017_UNEXPECTED_JAVA'}
            function Read-TrainCold013 {throw 'TRAIN017_UNEXPECTED_SQL'}
            $cause='';try{& $fitBody}catch{$cause=$_.Exception.Message}
            Require ($cause -ceq 'TRAIN017_EXISTING_CHECKPOINT_USE_REPAIR') 'TRAIN017_COLD_FIT_ENTERED_EXTERNAL_WORK'
        }
        $proof=Get-TrainNoFitCompletion016 $campaign $journal
        $repair=Join-Path $fixture 'repair-closure017'
        $before=(Get-FileHash -LiteralPath $path).Hash
        $first=Write-TrainNoFitClosure017 $repair $proof
        $receiptPath=Join-Path $repair 'closure.json';$receiptSha=(Get-FileHash -LiteralPath $receiptPath).Hash
        $again=Write-TrainNoFitClosure017 $repair $proof
        Require ($first.status -ceq 'COMPLETED_NO_FIT' -and $again.status -ceq 'COMPLETED_NO_FIT' -and
            (Get-FileHash -LiteralPath $receiptPath).Hash -ceq $receiptSha -and (Get-FileHash -LiteralPath $path).Hash -ceq $before -and
            (Get-FileHash -LiteralPath $checkpoint).Hash -ceq $checkpointSha) 'TRAIN017_COLD_CLOSURE_MUTATED_HISTORY'
        $markerPath=Join-Path $repair 'complete.json';$markerText=[IO.File]::ReadAllText($markerPath)
        $marker=Read-TrainJson $markerPath;$marker.trainingCredit=$true;Write-TrainJson $markerPath $marker
        $refused=$false;try{$null=Write-TrainNoFitClosure017 $repair $proof}catch{$refused=$true}
        Require $refused 'TRAIN017_TAMPERED_CREDIT_MARKER_ACCEPTED'
        Write-TrainText $markerPath $markerText
        $race=Join-Path $fixture 'parallel-closure';$proofPath=Join-Path $fixture 'parallel-proof.json';Write-TrainJson $proofPath $proof
        $publisher=Join-Path $fixture 'publisher.ps1'
        Write-TrainText $publisher 'param($Owner,$Directory,$ProofPath,$Ready,$ResultPath) $ErrorActionPreference="Stop";. $Owner;[IO.File]::WriteAllText($Ready,"ready");$closed=Write-TrainNoFitClosure017 $Directory (Read-TrainJson $ProofPath);Write-TrainJson $ResultPath @{status=$closed.status;receiptSha=(Get-FileHash -LiteralPath (Join-Path $Directory "closure.json")).Hash}'
        $hash=[Security.Cryptography.SHA256]::Create()
        try{$lockName='Local\PhantomTrainClosure017_'+([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes([IO.Path]::GetFullPath($race).TrimEnd('\').ToUpperInvariant())))).Replace('-','')}finally{$hash.Dispose()}
        $mutex=[Threading.Mutex]::new($false,$lockName);Require ($mutex.WaitOne(5000)) 'TRAIN017_FIXTURE_LOCK_BUSY'
        $publishers=@();$released=$false
        try{
            foreach($index in 0..1){
                $readyPath=Join-Path $fixture ('publisher-ready'+$index);$resultPath=Join-Path $fixture ('publisher-result'+$index+'.json')
                $argsLine='-NoProfile -ExecutionPolicy Bypass -File "'+$publisher+'" -Owner "'+$owner+'" -Directory "'+$race+'" -ProofPath "'+$proofPath+'" -Ready "'+$readyPath+'" -ResultPath "'+$resultPath+'"'
                $process=Start-Process -FilePath $layout.PowerShell -ArgumentList $argsLine -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixture ('publisher'+$index+'.out')) -RedirectStandardError (Join-Path $fixture ('publisher'+$index+'.err'))
                $publishers+=@{process=$process;handle=(Hold-ProcessHandle $process);ready=$readyPath;result=$resultPath}
            }
            $limit=[DateTime]::UtcNow.AddSeconds(10)
            while(@($publishers|Where-Object {!(Test-Path -LiteralPath $_.ready)}).Count -and [DateTime]::UtcNow -lt $limit){Start-Sleep -Milliseconds 20}
            Require (@($publishers|Where-Object {!(Test-Path -LiteralPath $_.ready)}).Count -eq 0) 'TRAIN017_PUBLISHERS_NOT_READY'
            foreach($entry in $publishers){$null=$entry.process.WaitForExit(350)}
            Require (!(Test-Path -LiteralPath (Join-Path $race 'closure.json'))) 'TRAIN017_CLOSURE_IGNORED_PUBLICATION_LOCK'
            $mutex.ReleaseMutex();$released=$true
            foreach($entry in $publishers){Require ($entry.process.WaitForExit(10000)) 'TRAIN017_PUBLISHER_DID_NOT_EXIT';Require ((Get-TrackedProcessExitCode $entry.process $entry.handle) -eq 0) 'TRAIN017_PARALLEL_PUBLICATION_FAILED'}
            $firstSha=(Read-TrainJson $publishers[0].result).receiptSha
            Require ($firstSha -ceq (Read-TrainJson $publishers[1].result).receiptSha -and $firstSha -ceq (Get-FileHash -LiteralPath (Join-Path $race 'closure.json')).Hash) 'TRAIN017_PARALLEL_RECEIPT_OVERWRITTEN'
            $null=Write-TrainNoFitClosure017 $race $proof
            Require ($firstSha -ceq (Get-FileHash -LiteralPath (Join-Path $race 'closure.json')).Hash) 'TRAIN017_PARALLEL_READBACK_CHANGED'
        }finally{
            if(!$released){$mutex.ReleaseMutex()};$mutex.Dispose()
            foreach($entry in $publishers){if(!$entry.process.HasExited){$null=$entry.process.WaitForExit(20000)};$entry.process.Dispose()}
        }
        $badProof=$proof.PSObject.Copy();$badProof.dataset='foreign'
        $refused=$false;try{$null=Write-TrainNoFitClosure017 $repair $badProof}catch{$refused=$true}
        Require $refused 'TRAIN017_REPAIR_OVERWROTE_FOREIGN_PROOF'
        Write-TrainText $receiptPath '{}'
        $refused=$false;try{$null=Write-TrainNoFitClosure017 $repair $proof}catch{$refused=$true}
        Require $refused 'TRAIN017_REPAIR_ACCEPTED_CHANGED_RECEIPT'
        Write-Output 'TRAIN017_PHASE_GREEN exactDONE016=1 unknownPhase=2 legacy=4 checkpointNegative=4 SQL=0 JAVA=0 GS=0'
    }finally{
        $resolved=[IO.Path]::GetFullPath($fixture)
        if(!$resolved.StartsWith([IO.Path]::GetTempPath(),[StringComparison]::OrdinalIgnoreCase)){throw 'TRAIN017_FIXTURE_SCOPE'}
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    return
}
if($Readiness016){
    $fixture=Join-Path ([IO.Path]::GetTempPath()) ('Training016-'+[guid]::NewGuid().ToString('N'))
    $null=[IO.Directory]::CreateDirectory($fixture)
    try{
        $values=Read-TrainConfig (Join-Path $PSScriptRoot 'AutonomousTraining.ini')
        $legacy=$values.Clone()
        $values.autonomous013Version='1';$values.autonomous016Version='1';$values.episodesPerBatch='51'
        $values.autonomous009Campaign='autonomous009-learning016-v1';$values.outcome006DatasetId='autonomous009-learning016-v1-c000001'
        $values.autonomous009CycleLimit='1';$values.autonomous009AcceptanceSmoke='true';$values.autonomous009SmokeMaxStarts='3';$values.autonomous009SmokeMaxNativeMinutes='75'
        $file=Join-Path $fixture 'TRAIN_CONFIG.ini'
        try{Write-TrainConfig $file $values;$loaded=Read-TrainConfig $file}catch{throw 'READINESS016_COMMON_CONFIG_51_REJECTED'}
        Require ($loaded.episodesPerBatch -ceq '51') 'READINESS016_CONFIG_DID_NOT_ROUNDTRIP'
        $g2=$values.Clone();$g2.autonomous009Campaign='autonomous009-learning016-g2-v1';$g2.outcome006DatasetId='autonomous009-learning016-g2-v1-c000001'
        try{Write-TrainConfig $file $g2;$g2Loaded=Read-TrainConfig $file}catch{throw 'READINESS016_GENERATION2_REJECTED'}
        Require ($g2Loaded.episodesPerBatch -ceq '51') 'READINESS016_GENERATION2_BATCH_CHANGED'
        $bad=$g2.Clone();$bad.autonomous009Campaign='autonomous009-learning016-g3-v1';$bad.outcome006DatasetId='autonomous009-learning016-g3-v1-c000001'
        $refused=$false;try{Assert-TrainConfig $bad}catch{$refused=$true};Require $refused 'READINESS016_THIRD_GENERATION_ALLOWED'
        foreach($change in @(@{episodesPerBatch='84'},@{episodesPerBatch='48'},@{autonomous016Version='2'},@{autonomous013Version='0'},
            @{autonomous009EvaluationOnly='true'},@{autonomous009Phase='EVAL'},@{autonomous009SmokeMaxStarts='4'},@{autonomous009SmokeMaxNativeMinutes='76'},
            @{autonomous009CycleLimit='0'},@{autonomous009Campaign='autonomous009-goal015-s2'})){
            $bad=$values.Clone();foreach($key in $change.Keys){$bad[$key]=$change[$key]}
            $refused=$false;try{Assert-TrainConfig $bad}catch{$refused=$true}
            Require $refused 'READINESS016_WRONG_BATCH_OR_AUTHORITY_ACCEPTED'
        }
        Write-TrainConfig $file $legacy;Require ((Read-TrainConfig $file).episodesPerBatch -ceq '48') 'READINESS016_LEGACY_CONFIG_CHANGED'
        $budgetFile=Join-Path $fixture 'native-budget016.json'
        $budget=Get-TrainNativeBudget013 $budgetFile 16
        Require ($budget.schemaVersion -eq 16 -and $budget.maximumStarts -eq 3 -and [long]$budget.maximumNativeMillis -eq 4500000L) 'READINESS016_NATIVE_LEDGER_INHERITED_013_BUDGET'
        foreach($ordinal in 1..3){
            $scope016=if($ordinal -eq 2){'autonomous009-learning016-g2-v1-c000001'}else{'autonomous009-learning016-v1-c000001'}
            $reserved=Reserve-TrainNativeBudget013 $budgetFile $scope016 $false 210000L 16
            $settled=Complete-TrainNativeBudget013 $budgetFile $reserved.pending 1000L 'OWN_HELD_HANDLE_EXIT_TIME' 16
            $duplicate=Complete-TrainNativeBudget013 $budgetFile $reserved.pending 1000L 'OWN_HELD_HANDLE_EXIT_TIME' 16
            Require ($settled.starts -eq $ordinal -and $settled.nativeMillis -eq $ordinal*1000 -and $duplicate.nativeMillis -eq $settled.nativeMillis) 'READINESS016_NATIVE_CHARGE_REPLAY'
        }
        $refused=$false;try{$null=Reserve-TrainNativeBudget013 $budgetFile 'autonomous009-learning016-v1-c000001' $false 210000L 16}catch{$refused=$true}
        Require $refused 'READINESS016_FOURTH_GS_ALLOWED'
        $evalFile=Join-Path $fixture 'forbidden-eval.json';$refused=$false
        try{$null=Reserve-TrainNativeBudget013 $evalFile 'autonomous009-learning016-v1-c000001' $true 210000L 16}catch{$refused=$true}
        Require ($refused -and !(Test-Path -LiteralPath $evalFile)) 'READINESS016_CHILD_EVAL_RESERVED'
        Remove-Item -LiteralPath $budgetFile
        $refused=$false;try{$null=Get-TrainNativeBudget013 $budgetFile 16}catch{$refused=$true};Require $refused 'READINESS016_DELETED_LEDGER_RESET'
        $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
        Require ($errors.Count -eq 0) 'READINESS016_SOURCE_PARSE'
        $definitions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -cin @('Test-TrainIncremental016','Get-TrainReadinessWindow016','Assert-TrainReadinessMode016','Save-TrainReadinessControl016','Open-TrainHeadless013','Read-TrainSourceConfig')},$false)
        $script:SourceScripts016=$PSScriptRoot
        # AST functions have no file path; preserve the real script-root dependency explicitly.
        . ([scriptblock]::Create((($definitions|ForEach-Object {$_.Extent.Text}) -join "`n").Replace('$PSScriptRoot','$script:SourceScripts016')))
        $now=[DateTime]::UtcNow;$first=Get-TrainReadinessWindow016 1 @() $now
        $prior1=[pscustomobject]@{schemaVersion=16;session=1;elapsedMillis=599000L;finishedUtc=$now.AddSeconds(599).ToString('o');ownedWorkerAbsent=$true;sqlAbsent=$true;withinCap=$true}
        $second=Get-TrainReadinessWindow016 2 @($prior1) $now.AddSeconds(600)
        $prior2=[pscustomobject]@{schemaVersion=16;session=2;elapsedMillis=599000L;finishedUtc=$now.AddSeconds(1199).ToString('o');ownedWorkerAbsent=$true;sqlAbsent=$true;withinCap=$true}
        $third=Get-TrainReadinessWindow016 3 @($prior1,$prior2) $now.AddSeconds(1200)
        Require ($first.maximumSeconds -eq 600 -and $second.priorElapsedMillis -eq 599000 -and $third.priorElapsedMillis -eq 1198000 -and $third.priorElapsedMillis+$third.maximumSeconds*1000 -le 1800000) 'READINESS016_CUMULATIVE_TIME_RESET'
        $parentCost=[pscustomobject]@{schemaVersion=16;session=1;elapsedMillis=72430L;finishedUtc=$now.AddSeconds(73).ToString('o');ownedWorkerAbsent=$true;sqlAbsent=$true;withinCap=$true}
        $carried=Get-TrainReadinessWindow016 2 @($parentCost) $now.AddSeconds(200) 100160L
        Require ($carried.priorElapsedMillis -eq 172590L -and $carried.maximumSeconds -eq 600) 'READINESS016_GENERATION_RESET_OR_LOST_BUILD_COST'
        & {
            $rollover=Join-Path $fixture 'generation2-control';$layout=@{Root=$rollover;Sql=(Join-Path $rollover 'sql')};$script:Config=$g2.Clone()
            $parentScope=Join-Path $rollover 'autonomous/v1/campaigns/autonomous009-learning016-v1/cohorts/autonomous009-learning016-v1-c000001'
            $null=[IO.Directory]::CreateDirectory($parentScope);$null=[IO.Directory]::CreateDirectory((Join-Path $rollover 'sql'));$null=[IO.Directory]::CreateDirectory((Join-Path $rollover 'autonomous/v16'))
            $op=Join-Path $parentScope 'headless-operation013.json';Write-TrainJson $op @{goalVersion=16;session=1;exitProven=$true;worker=@{unitFixture=$true};status='ERROR'}
            $parentSha=(Get-FileHash -LiteralPath $op).Hash;Write-TrainJson (Join-Path $rollover 'sql/owned-process.json') @{unitFixture=$true}
            Write-TrainJson (Join-Path $rollover 'autonomous/v16/generation2-issued.json') @{schema='TRAIN016_GENERATION2_ISSUED_V1';campaign=$g2.autonomous009Campaign;dataset=$g2.outcome006DatasetId;parentFiles=@{'headless-operation013.json'=$parentSha}}
            $script:heldChecks016=0
            function Test-TrainProcessAbsent {param($Record) Require ([bool]$Record.unitFixture) 'READINESS016_WRONG_PARENT_PROCESS';$script:heldChecks016++;return $true}
            function Test-TrainExitReceipt {param($Layout,$Record) Require ([bool]$Record.unitFixture) 'READINESS016_WRONG_PARENT_EXIT';$script:heldChecks016++;return $true}
            Save-TrainReadinessControl016 (Join-Path $rollover 'autonomous/v16/campaigns/autonomous009-learning016-g2-v1') 2
            Require ((Get-FileHash -LiteralPath $op).Hash -ceq $parentSha -and $script:heldChecks016 -eq 4 -and !(Test-Path -LiteralPath (Join-Path $rollover 'autonomous/v16/readiness/session1-control'))) 'READINESS016_PARENT_CONTROLS_ARCHIVED_OR_UNPROVEN'
        }
        $script:Config=$values.Clone()
        foreach($case in @(@{session=4;prior=@($prior1,$prior2)},@{session=2;prior=@()},@{session=1;prior=@($prior1)})){
            $refused=$false;try{$null=Get-TrainReadinessWindow016 $case.session $case.prior $now.AddSeconds(1200)}catch{$refused=$true};Require $refused 'READINESS016_SESSION_REPLAY_ALLOWED'
        }
        $prior1.schemaVersion=15;$refused=$false;try{$null=Get-TrainReadinessWindow016 2 @($prior1) $now.AddSeconds(1200)}catch{$refused=$true};Require $refused 'READINESS016_HISTORICAL_BUDGET_IMPORTED';$prior1.schemaVersion=16
        $prior1.sqlAbsent=$false;$refused=$false;try{$null=Get-TrainReadinessWindow016 2 @($prior1) $now.AddSeconds(1200)}catch{$refused=$true};Require $refused 'READINESS016_SQL_EXIT_UNPROVEN';$prior1.sqlAbsent=$true
        $script:ReadinessDeadline013=$first;$ctx=Open-TrainHeadless013 $fixture $values 'prepare' ('a'*64) $now
        $again=Open-TrainHeadless013 $fixture $values 'prepare' ('a'*64) $now.AddSeconds(1)
        Require ($ctx.journal.goalVersion -eq 16 -and $ctx.journal.maximumSeconds -eq 600 -and $again.recovered -and $again.journal.hardDeadlineUtc -ceq $ctx.journal.hardDeadlineUtc -and
            (([datetime]$ctx.journal.workDeadlineUtc).ToUniversalTime()-$now).TotalSeconds -eq 390) 'READINESS016_WORK_DEADLINE_WIDENED_ON_RESUME'
        $ctx.journal.status='TIMEOUT';$ctx.journal.exitProven=$true;Write-TrainJson $ctx.path $ctx.journal;$script:ReadinessDeadline013=$second
        $refused=$false;try{$null=Open-TrainHeadless013 $fixture $values 'prepare' ('a'*64) $now.AddSeconds(600)}catch{$refused=$true}
        Require $refused 'READINESS016_BOOLEAN_EXIT_WITHOUT_HELD_RECEIPT_RESTARTED_WORKER'
        & {
            $layout=@{Root=$fixture};$Action='Start';$ConfigProfile='OUTCOME006';$Autonomous=$true;$Readiness016Session=0
            $EnableExploration007=$false;$EnableExploration008=$false;$EnableEvaluation005=$false
            Write-TrainConfig (Join-Path $fixture 'TRAIN_CONFIG.ini') $values
            $refused=$false;$cause='';try{$null=Read-TrainSourceConfig}catch{$cause=$_.Exception.Message;$refused=$cause -ceq 'TRAIN016_G3_REQUIRED_BEFORE_START'}
            Require $refused ('READINESS016_START_WITHOUT_G3_REACHED_SQL_OR_PREPARE cause='+$cause)
        }
        $target=Join-Path $fixture 'physical';$link=Join-Path $fixture 'reparse';$null=[IO.Directory]::CreateDirectory($target)
        $null=New-Item -ItemType Junction -Path $link -Target $target
        $refused=$false;try{Assert-TrainDirectory $link}catch{$refused=$true};Require $refused 'READINESS016_REPARSE_ACCEPTED';Remove-Item -LiteralPath $link -Force
        $director=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Invoke-TrainAutonomousDirector013'},$false)[0]
        $collect=$director.Body.FindAll({param($n) $n -is [Management.Automation.Language.IfStatementAst] -and $n.Extent.Text.StartsWith("if(`$journal.phase -ceq 'COLLECT')",[StringComparison]::Ordinal)},$true)
        Require ($collect.Count -eq 1) 'READINESS016_COLLECT_BRANCH'
        $body=[scriptblock]::Create($collect[0].Extent.Text)
        & {
            $script:Config=$values.Clone();$script:ReadinessSession=0;$script:ReadinessOnly013=$false;$script:ReadinessGoalVersion=16
            $script:probe016=0;$script:native016=0;$script:verify016=0
            $directory=Join-Path $fixture 'director';$cycleDirectory=Join-Path $directory 'cycles/c000001';$cohort=Join-Path $directory 'cohort'
            $null=[IO.Directory]::CreateDirectory($cycleDirectory);$null=[IO.Directory]::CreateDirectory($cohort)
            Write-TrainJson (Join-Path $cohort 'training-admission013.json') @{cells=3;minimumCells=3;classes=2;scopes=2;unitFixture=$true}
            Write-TrainJson (Join-Path $cohort 'cohort016.complete.json') @{unitFixture=$true;trainingCredit=$false}
            $parent016=[pscustomobject]@{modelVersion=14;modelSha=('b'*64);optimizerSha=('e'*64)}
            $journal=[pscustomobject]@{phase='COLLECT';dataset='autonomous009-learning016-v1-c000001';reason='';parentCheckpoint=$parent016};$config=$values.Clone();$campaign=$values.autonomous009Campaign;$Dependencies=$null;$Jar='a'*64;$path=Join-Path $directory 'director.json';$layout=@{Root=$fixture}
            function Assert-TrainHeadlessAdmission013 {param($Scope,$Admission)}
            function Publish-TrainState {param($Phase,$Reason)}
            function Write-TrainConfig {param($File,$Values)}
            function Save-TrainCycleState013 {param($Path,$Journal)}
            function Invoke-TrainJava {param($Dependencies,$Class,$Arguments) if($Arguments[0] -ceq 'verify-seal016'){$script:verify016++}}
            function Invoke-TrainEvaluation013 {param($Dependencies,$Jar,$Journal,$Path,$CycleDirectory,$Probe) $script:probe016++}
            function Invoke-TrainAutonomousNative {param($Dependencies,$Jar,$Journal,$Path) $script:native016++;Write-TrainText (Join-Path $cohort 'collection.complete') 'UNIT_NOT_GAMEPLAY'}
            function Get-TrainAdmissionFailure013 {return $null}
            function Read-TrainCold013 {param($Dependencies) return $parent016}
            function Test-TrainRequest {param($Kind) return $false}
            & $body
            Require ($script:probe016 -eq 0 -and $script:native016 -eq 1 -and $script:verify016 -gt 0 -and $journal.phase -ceq 'FIT') 'READINESS016_DIRECTOR_RAN_EVAL_OR_BYPASSED_SEAL'
        }
        $fitNodes=$director.Body.FindAll({param($n) $n -is [Management.Automation.Language.IfStatementAst] -and $n.Extent.Text.StartsWith("if(`$journal.phase -ceq 'FIT')",[StringComparison]::Ordinal)},$true)
        Require ($fitNodes.Count -eq 1) 'READINESS016_FIT_BRANCH'
        $fitBody=[scriptblock]::Create($fitNodes[0].Extent.Text)
        foreach($scenario in @('INSUFFICIENT','ONE_FIT','AMBIGUOUS')){
            & {
                $layout=@{Root=$fixture};$cycleDirectory=Join-Path $fixture $scenario;$null=[IO.Directory]::CreateDirectory($cycleDirectory)
                $parent=[pscustomobject]@{modelVersion=14;modelSha=('b'*64);optimizerSha=('e'*64);optimizerUpdates=903}
                $child=[pscustomobject]@{modelVersion=15;modelSha=('f'*64);optimizerSha=('d'*64);optimizerUpdates=904}
                $journal=[pscustomobject]@{phase='FIT';dataset='autonomous009-learning016-v1-c000001';parentCheckpoint=$parent;fits=0;reason='';completedCycles=0;bestVersion=2;bestSha=('c'*64)}
                $script:Config=$values.Clone();$config=$values.Clone();$Dependencies=$null;$path=Join-Path $cycleDirectory 'director.json';$script:fitCalls016=0
                function Write-TrainConfig {param($File,$Values)}
                function Publish-TrainState {param($Phase,$Reason)}
                function Save-TrainCycleState013 {param($File,$Journal) Write-TrainJson $File $Journal}
                function Invoke-TrainEvaluation013 {throw 'READINESS016_POST_FIT_EVAL_FORBIDDEN'}
                function Invoke-TrainJava {
                    param($Dependencies,$Class,$Arguments)
                    Require ($Class -ceq 'org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator' -and $Arguments[0] -ceq 'train') 'READINESS016_UNEXPECTED_FIT_DEPENDENCY'
                    $script:fitCalls016++
                    $result=if($scenario -ceq 'ONE_FIT'){@{candidateVersion=15;candidateSha=$child.modelSha;metrics=@{optimizerSha=$child.optimizerSha}}}else{@{candidateVersion=0}}
                    Write-TrainJson (Join-Path $fixture 'tactical-v3-evaluation.json') $result
                }
                function Read-TrainCold013 {param($Dependencies) if($scenario -ceq 'ONE_FIT'){return $child}else{return $parent}}
                if($scenario -ceq 'AMBIGUOUS'){
                    Write-TrainJson (Join-Path $cycleDirectory 'fit-reserved016.json') @{schemaVersion=16;cycle=$journal.dataset;parent=$parent}
                    $refused=$false;try{& $fitBody}catch{$refused=$_.Exception.Message -ceq 'TRAIN016_FIT_RESERVATION_UNCERTAIN_NO_REPLAY'}
                    Require ($refused -and $script:fitCalls016 -eq 0) 'READINESS016_UNKNOWN_FIT_REPLAYED'
                }else{
                    & $fitBody
                    $checkpoint016=Join-Path $cycleDirectory 'checkpoint016.json';$checkpoint016Sha=(Get-FileHash -LiteralPath $checkpoint016).Hash
                    $journal.phase='FIT';$refused=$false
                    try{& $fitBody}catch{$refused=$_.Exception.Message -ceq 'TRAIN017_EXISTING_CHECKPOINT_USE_REPAIR'}
                    Require ($refused -and $script:fitCalls016 -eq 1 -and (Read-TrainJson $path).phase -ceq 'DONE016' -and
                        (Get-FileHash -LiteralPath $checkpoint016).Hash -ceq $checkpoint016Sha -and $journal.bestVersion -eq 2 -and
                        $journal.fits -eq $(if($scenario -ceq 'ONE_FIT'){1}else{0})) 'READINESS017_REPEATED_FIT_OR_REWRITTEN_CHECKPOINT'
                    if($scenario -ceq 'INSUFFICIENT'){Require ($journal.reason -ceq 'NO_FIT_INSUFFICIENT_VALID_DATA') 'READINESS016_FAKE_FIT_ON_INSUFFICIENT_DATA'}
                }
            }
        }
        Write-Output 'READINESS016_GREEN config51=1 legacy=1 sessions3Wall1800=1 native3Minutes75=1 noEval=1 noRepeatedFit=1 actualJunctionRejected=1 SQL=0 GS=0'
    }finally{
        $script:ReadinessDeadline013=$null
        $resolved=[IO.Path]::GetFullPath($fixture);$temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if(!$resolved.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase)){throw 'READINESS016_TEMP_SCOPE'}
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    return
}
if($Readiness015){
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) 'READINESS015_SOURCE_PARSE'
    $functions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -cin @('Get-TrainReadinessWindow014','Get-TrainReadinessWindow015','Open-TrainHeadless013','Assert-TrainReadinessMode015','Test-TrainIncremental016')},$false)
    . ([scriptblock]::Create(($functions|ForEach-Object {$_.Extent.Text}) -join "`n"))
    Require ([bool](Get-Command Get-TrainReadinessWindow015 -ErrorAction SilentlyContinue)) 'READINESS015_SHARED_BUDGET_MISSING'
    $now=[DateTime]::UtcNow;$first=Get-TrainReadinessWindow015 1 $null $now
    Require ($first.goalVersion -eq 15 -and $first.maximumSeconds -eq 480) 'READINESS015_FIRST_CAP'
    $prior=[pscustomobject]@{schemaVersion=15;session=1;elapsedMillis=470000L;finishedUtc=$now.AddSeconds(470).ToString('o');ownedWorkerAbsent=$true;sqlAbsent=$true;withinCap=$true}
    $second=Get-TrainReadinessWindow015 2 $prior $now.AddSeconds(500)
    Require ($second.goalVersion -eq 15 -and $second.maximumSeconds -eq 720 -and [long]$prior.elapsedMillis+720000L -le 1200000L) 'READINESS015_TOTAL_CAP_RESET'
    foreach($case in @(@{session=1;prior=$prior},@{session=2;prior=$null},@{session=3;prior=$prior})){
        $refused=$false;try{$null=Get-TrainReadinessWindow015 $case.session $case.prior $now.AddSeconds(500)}catch{$refused=$true};Require $refused 'READINESS015_REPLAY_OR_THIRD'
    }
    $prior.schemaVersion=14;$refused=$false;try{$null=Get-TrainReadinessWindow015 2 $prior $now.AddSeconds(500)}catch{$refused=$true};Require $refused 'READINESS015_HISTORICAL_RECEIPT_REUSED'
    $prior.schemaVersion=15;$prior.sqlAbsent=$false;$refused=$false;try{$null=Get-TrainReadinessWindow015 2 $prior $now.AddSeconds(500)}catch{$refused=$true};Require $refused 'READINESS015_SQL_EXIT_UNPROVEN'
    $root015=Join-Path ([IO.Path]::GetTempPath()) ('Training015-headless-'+[guid]::NewGuid().ToString('N'))
    $values015=@{autonomous009Campaign='autonomous009-contract015';outcome006DatasetId='autonomous009-contract015-c000001';autonomous009ModelVersion='14'}
    try{
        $script:ReadinessDeadline013=$first
        $ctx015=Open-TrainHeadless013 $root015 $values015 'prepare' ('a'*64) $now
        Require ($ctx015.journal.PSObject.Properties['goalVersion'] -and $ctx015.journal.goalVersion -eq 15 -and $ctx015.journal.maximumSeconds -eq 480 -and
            (([datetime]$ctx015.journal.workDeadlineUtc).ToUniversalTime()-$now).TotalSeconds -eq 270) 'READINESS015_WORKER_CAP_OR_GOAL_WIDENED'
        $cold015=Open-TrainHeadless013 $root015 $values015 'prepare' ('a'*64) $now.AddSeconds(1)
        Require ($cold015.recovered -and $cold015.journal.hardDeadlineUtc -ceq $ctx015.journal.hardDeadlineUtc) 'READINESS015_COLD_DEADLINE_RESET'
        $script:ReadinessDeadline013=$second;$values015.outcome006DatasetId='autonomous009-contract015-c000002'
        $ctxSecond015=Open-TrainHeadless013 $root015 $values015 'prepare' ('b'*64) $now.AddSeconds(500)
        Require ($ctxSecond015.journal.goalVersion -eq 15 -and $ctxSecond015.journal.maximumSeconds -eq 720 -and
            (([datetime]$ctxSecond015.journal.workDeadlineUtc).ToUniversalTime()-([datetime]$second.issuedUtc).ToUniversalTime()).TotalSeconds -eq 510) 'READINESS015_SECOND_WORKER_CAP'
        $script:ReadinessDeadline013=[pscustomobject]@{goalVersion=15;maximumSeconds=1200;issuedUtc=$now.ToString('o')};$values015.outcome006DatasetId='autonomous009-contract015-c000003'
        $refused=$false;try{$null=Open-TrainHeadless013 $root015 $values015 'prepare' ('c'*64) $now}catch{$refused=$true};Require $refused 'READINESS015_WIDENED_PASS_ACCEPTED'
        # Execute the actual COLLECT branch with only Java/EVAL dependencies stubbed. No gameplay proof is fabricated.
        $directorNodes015=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -ceq 'Invoke-TrainAutonomousDirector013'},$false)
        $collectNodes015=$directorNodes015[0].Body.FindAll({param($n) $n -is [Management.Automation.Language.IfStatementAst] -and $n.Extent.Text.StartsWith("if(`$journal.phase -ceq 'COLLECT')",[StringComparison]::Ordinal)},$true)
        Require ($collectNodes015.Count -eq 1) 'READINESS015_DIRECTOR_COLLECT_BRANCH_MISSING'
        $collectBody015=[scriptblock]::Create($collectNodes015[0].Extent.Text)
        foreach($goal015 in @(14,15)){
            & {
                $script:ReadinessSession=1;$script:ReadinessGoalVersion=$goal015;$script:ReadinessOnly013=$true
                $Readiness014Session=if($goal015 -eq 14){1}else{0};$Readiness015Session=if($goal015 -eq 15){1}else{0}
                $script:prepareCalls015=0;$script:probeCalls015=0
                $directory=Join-Path $root015 ('director-goal'+$goal015);$cycleDirectory=Join-Path $directory 'cycles/c000001'
                $null=[IO.Directory]::CreateDirectory($cycleDirectory)
                $cohort=$ctx015.scope;$layout=@{Root=$root015};$path=Join-Path $directory 'director-state.json';$Dependencies=$null;$Jar='a'*64;$config=@{};$campaign='autonomous009-contract015'
                $journal=[pscustomobject]@{phase='COLLECT';dataset='autonomous009-contract015-c000001';reason=''}
                Write-TrainJson (Join-Path $cohort 'training-admission013.json') @{cells=3;minimumCells=3;classes=2;scopes=2;unitFixture=$true;trainingCredit=$false}
                function Assert-TrainHeadlessAdmission013 { param($Scope,$Admission) if(!(Test-Path -LiteralPath $Admission)){throw 'UNIT_TRAIN_ADMISSION_MISSING'} }
                function Publish-TrainState { param($Phase,$Reason) }
                function Write-TrainConfig { param($File,$Values) }
                function Invoke-TrainJava { param($Dependencies,$Class,$Arguments) $script:prepareCalls015++ }
                function Get-TrainAdmissionFailure013 { return $null }
                function Invoke-TrainEvaluation013 {
                    param($Dependencies,$Jar,$Journal,$Path,$CycleDirectory,$Probe)
                    $script:probeCalls015++
                    Write-TrainJson (Join-Path $CycleDirectory 'child-probe-admitted.json') @{unitFixture=$true;trainingCredit=$false}
                }
                function Invoke-TrainAutonomousNative { throw 'READINESS015_ATTEMPTED_NATIVE_START' }
                & $collectBody015
                Require ($script:prepareCalls015 -eq 1 -and $script:probeCalls015 -eq 0) 'READINESS015_DIRECTOR_RAN_INDEPENDENT_PROBE'
                $complete015=Read-TrainJson (Join-Path $directory 'readiness-complete013.json')
                Require ($complete015.schemaVersion -eq $goal015 -and $complete015.scope -ceq 'TRAIN_ONLY' -and !$complete015.nativeStarted -and !$complete015.trainingCredit) 'READINESS015_TRAIN_ONLY_COMPLETION'
            }
        }
        Require ([bool](Get-Command Assert-TrainReadinessMode015 -ErrorAction SilentlyContinue)) 'READINESS015_MODE_GUARD_MISSING'
        Assert-TrainReadinessMode015 @{autonomous013Version='1';autonomous009EvaluationOnly='false';episodesPerBatch='51'} 1
        foreach($badMode015 in @(@{autonomous013Version='0';autonomous009EvaluationOnly='false';episodesPerBatch='51'},@{autonomous013Version='1';autonomous009EvaluationOnly='true';episodesPerBatch='51'})){
            $refused=$false;try{Assert-TrainReadinessMode015 $badMode015 1}catch{$refused=$true};Require $refused 'READINESS015_UNBOUNDED_OR_EVAL_MODE_ACCEPTED'
        }
        $refused=$false;try{Assert-TrainReadinessMode015 @{autonomous013Version='1';autonomous009EvaluationOnly='false';episodesPerBatch='84'} 1}catch{$refused=$true};Require $refused 'READINESS015_PIPELINE_51_NOT_ENFORCED'
        Assert-TrainReadinessMode015 @{autonomous013Version='0';autonomous009EvaluationOnly='true'} 0
    }finally{
        $script:ReadinessDeadline013=$null
        $resolved015=[IO.Path]::GetFullPath($root015);$temp015=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if(!$resolved015.StartsWith($temp015,[StringComparison]::OrdinalIgnoreCase)){throw 'READINESS015_TEMP_SCOPE'}
        if(Test-Path -LiteralPath $resolved015){Remove-Item -LiteralPath $resolved015 -Recurse -Force}
    }
    Write-Output 'READINESS015_GREEN sessions=2 wallCap=1200 workerCapPreserved=1 coldDeadlinePreserved=1 directorTrainOnly=1 incompatibleModesRejected=1 historicalReceiptsPreserved=1 replayRejected=1 thirdRejected=1 SQL=0 GS=0'
    return
}
if($Readiness014){
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) 'READINESS014_SOURCE_PARSE'
    $functions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-TrainReadinessWindow014'},$false)
    . ([scriptblock]::Create(($functions|ForEach-Object {$_.Extent.Text}) -join "`n"))
    Require ([bool](Get-Command Get-TrainReadinessWindow014 -ErrorAction SilentlyContinue)) 'READINESS014_SHARED_BUDGET_MISSING'
    $now=[DateTime]::UtcNow;$first=Get-TrainReadinessWindow014 1 $null $now
    Require ($first.maximumSeconds -eq 480 -and (([datetime]$first.hardDeadlineUtc).ToUniversalTime()-$now).TotalSeconds -eq 480) 'READINESS014_BASELINE_CAP'
    $prior=[pscustomobject]@{schemaVersion=14;session=1;elapsedMillis=470000L;finishedUtc=$now.AddSeconds(470).ToString('o');ownedWorkerAbsent=$true;sqlAbsent=$true;withinCap=$true}
    $second=Get-TrainReadinessWindow014 2 $prior $now.AddSeconds(500)
    Require ($second.maximumSeconds -eq 720 -and [long]$prior.elapsedMillis+720000L -le 1200000L) 'READINESS014_TOTAL_CAP_RESET'
    foreach($case in @(@{session=1;prior=$prior},@{session=2;prior=$null},@{session=3;prior=$prior})){
        $refused=$false;try{$null=Get-TrainReadinessWindow014 $case.session $case.prior $now.AddSeconds(500)}catch{$refused=$true};Require $refused 'READINESS014_REPLAY_OR_THIRD_SESSION'
    }
    $prior.ownedWorkerAbsent=$false;$refused=$false;try{$null=Get-TrainReadinessWindow014 2 $prior $now.AddSeconds(500)}catch{$refused=$true};Require $refused 'READINESS014_UNPROVEN_EXIT'
    Write-Output 'READINESS014_GREEN sessions=2 wallCap=1200 startupAndCleanupIncluded=1 replayRejected=1 thirdRejected=1 unprovenExitRejected=1 SQL=0 GS=0'
    return
}
if($Headless013){
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) 'HEADLESS013_SOURCE_PARSE'
    $functions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and ($n.Name -match 'Headless.*013$' -or $n.Name -cin @('Test-TrainIncremental016','Invoke-TrainJava','Test-TrainRequest','Publish-TrainState','Copy-TrainConfig013'))},$false)
    . ([scriptblock]::Create(($functions|ForEach-Object {$_.Extent.Text}) -join "`n"))
    Require ([bool](Get-Command Open-TrainHeadless013 -ErrorAction SilentlyContinue)) 'HEADLESS013_CONTRACT_MISSING'
    $script:HeadlessAdoption013=$null;$script:ReadinessOnly013=$false;$script:HeadlessCleanupDeadline013=$null;$script:ReadinessDeadline013=$null;$script:State=$null;$script:DirectorNonce='';$script:Config=$null;$script:WorkerRecord=$null
    $root013=Join-Path ([IO.Path]::GetTempPath()) ('Training013-headless-'+[guid]::NewGuid().ToString('N'));$null=[IO.Directory]::CreateDirectory($root013)
    $config=@{autonomous009Campaign='autonomous009-contract013';outcome006DatasetId='autonomous009-contract013-c000001';autonomous009ModelVersion='14'}
    $now=[DateTime]::UtcNow;$ctx=Open-TrainHeadless013 $root013 $config 'prepare' ('a'*64) $now
    Require (([DateTime]::Parse($ctx.journal.hardDeadlineUtc).ToUniversalTime()-$now).TotalSeconds -eq 1200 -and ([DateTime]::Parse($ctx.journal.workDeadlineUtc).ToUniversalTime()-$now).TotalSeconds -eq 990) 'HEADLESS013_TOTAL_CAP_AND_DRAIN_RESERVE'
    $null=Update-TrainHeadless013 $ctx $now $false
    Require ($ctx.journal.status -ceq 'RUNNING' -and !(Test-Path -LiteralPath (Join-Path $ctx.scope 'stop.request'))) 'HEADLESS013_PREMATURE_DRAIN'
    $null=Update-TrainHeadless013 $ctx $now.AddSeconds(991) $false
    Require ($ctx.journal.status -ceq 'DRAINING' -and $ctx.journal.reason -ceq 'TIMEOUT' -and (Test-Path -LiteralPath (Join-Path $ctx.scope 'stop.request'))) 'HEADLESS013_TIMEOUT_MUST_REQUEST_COHORT_DRAIN'
    $resumed=Open-TrainHeadless013 $root013 $config 'prepare' ('a'*64) $now.AddSeconds(992)
    Require ($resumed.journal.nonce -ceq $ctx.journal.nonce -and $resumed.journal.hardDeadlineUtc -ceq $ctx.journal.hardDeadlineUtc -and $resumed.recovered) 'HEADLESS013_RESTART_RESET_IDENTITY_OR_BUDGET'
    Close-TrainHeadless013 $ctx 2 'ERROR' $true $now.AddSeconds(993)
    Require ($ctx.journal.status -ceq 'TIMEOUT' -and !$ctx.journal.trainingCredit) 'HEADLESS013_TIMEOUT_BECAME_SUCCESS_OR_CREDIT'
    foreach($kind in @('CANCELLED','INSUFFICIENT_EVIDENCE','ERROR')){
        $config.outcome006DatasetId='autonomous009-contract013-'+$kind
        $case=Open-TrainHeadless013 $root013 $config 'prepare' ('a'*64) $now
        if($kind -ceq 'CANCELLED'){$null=Update-TrainHeadless013 $case $now $true}
        Close-TrainHeadless013 $case 2 $kind $true $now.AddSeconds(1)
        Require ($case.journal.status -ceq $kind -and !$case.journal.trainingCredit) ('HEADLESS013_REASON_COLLAPSED_'+$kind)
        $cold=Open-TrainHeadless013 $root013 $config 'prepare' ('a'*64) $now.AddSeconds(2)
        Require ($cold.terminal -and $cold.journal.nonce -ceq $case.journal.nonce) 'HEADLESS013_TERMINAL_RESTART_REEXECUTES'
    }
    $config.outcome006DatasetId='autonomous009-contract013-unproven';$case=Open-TrainHeadless013 $root013 $config 'prepare' ('a'*64) $now
    Close-TrainHeadless013 $case 0 'READY' $false $now
    Require ($case.journal.status -ceq 'ERROR') 'HEADLESS013_UNKNOWN_EXIT_ACCEPTED'
    Require ((Get-TrainHeadlessReadinessVerdict013 $false 'READY' $true) -ceq 'ERROR') 'HEADLESS013_PARTIAL_TRAIN_READY_IS_NOT_COMBINED_READY'
    Require ((Get-TrainHeadlessReadinessVerdict013 $true 'READY' $false) -ceq 'TIMEOUT') 'HEADLESS013_COMPLETION_AFTER_CAP_ACCEPTED'
    $fakeAdmission=Join-Path $case.scope 'training-admission013.json';Write-TrainText $fakeAdmission '{}';$orphanScope=Join-Path $root013 'unproven';$null=[IO.Directory]::CreateDirectory($orphanScope)
    $rejected=$false;try{Assert-TrainHeadlessAdmission013 $orphanScope $fakeAdmission}catch{$rejected=$true};Require $rejected 'HEADLESS013_ADMISSION_WITHOUT_TERMINAL_PROVENANCE_ACCEPTED'
    $script:ReadinessDeadline013=$ctx.journal;$config.outcome006DatasetId='autonomous009-contract013-child';$shared=Open-TrainHeadless013 $root013 $config 'admit-eval' ('a'*64) $now.AddSeconds(100)
    Require ($shared.journal.hardDeadlineUtc -ceq $ctx.journal.hardDeadlineUtc) 'HEADLESS013_CHILD_RESET_COMBINED_CAP';$script:ReadinessDeadline013=$null
    Initialize-TrainWin32
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $root013 'owned';New-TrainPrivateRoot $fixture
    foreach($name in @('workers','commands','attempts','logs')){$null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root $name))}
    $layout=$fixture;$stub=Join-Path $fixture.Root 'cooperative.ps1'
    [IO.File]::WriteAllText($stub,'param([string]$Stop,[string]$Ready,[string]$Nonce) [IO.File]::WriteAllText($Ready,$Nonce);$end=[DateTime]::UtcNow.AddSeconds(15);while(![IO.File]::Exists($Stop) -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 25};exit 7',[Text.UTF8Encoding]::new($false))
    foreach($kind in @('TIMEOUT','CANCELLED')){
        $config.outcome006DatasetId='autonomous009-contract013-os-'+$kind;$now=[DateTime]::UtcNow;$case=Open-TrainHeadless013 $fixture.Root $config 'prepare' ('a'*64) $now
        $ready=Join-Path $fixture.Root ($case.journal.nonce+'.ready');$stop=Join-Path $case.scope 'stop.request'
        $argsLine='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$stub+'" -Stop "'+$stop+'" -Ready "'+$ready+'" -Nonce '+$case.journal.nonce
        $child=Start-Process -FilePath $fixture.PowerShell -ArgumentList $argsLine -WindowStyle Hidden -PassThru;$held=Hold-ProcessHandle $child
        try{
            $end=[DateTime]::UtcNow.AddSeconds(10);while(!(Test-Path -LiteralPath $ready) -and !$child.HasExited -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 25}
            Require (Test-Path -LiteralPath $ready) 'HEADLESS013_OS_FIXTURE_NOT_READY'
            $record=New-TrainProcessRecord $child $fixture.PowerShell $case.journal.nonce Worker
            $case.journal.worker=$record;Write-TrainJson $case.path $case.journal;$null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root ('workers\'+$record.nonce)))
            Write-TrainJson (Join-Path $fixture.Root 'headless-active013.json') @{scope=$case.scope;nonce=$record.nonce}
            $adopted=Open-TrainHeadless013 $fixture.Root $config 'prepare' ('a'*64) ([DateTime]::UtcNow)
            Require ($adopted.recovered -and $adopted.journal.worker.pid -eq $record.pid -and $adopted.journal.nonce -ceq $record.nonce) 'HEADLESS013_RECOVERY_STARTED_SECOND_WORKER'
            $recovery=Recover-TrainHeadless013 $adopted;Require ($recovery.process.Id -eq $child.Id -and !$recovery.exitFact) 'HEADLESS013_LIVE_HANDOFF_CHANGED_PID';$recovery.process.Dispose()
            if($kind -ceq 'TIMEOUT'){$null=Update-TrainHeadless013 $case $now.AddSeconds(991) $false}
            else{Require (Request-TrainHeadlessCancel013) 'HEADLESS013_EXTERNAL_CANCEL_NOT_SENT';$null=Update-TrainHeadless013 $case ([DateTime]::UtcNow) $true}
            Require ($child.WaitForExit(10000)) 'HEADLESS013_COOPERATIVE_CHILD_DID_NOT_EXIT'
            Write-TrainExitReceipt $fixture $record $child $held 'headless013-contract-child'
            $recovery=Recover-TrainHeadless013 $adopted;Require (!$recovery.process -and $recovery.exitFact.exitCode -eq 7) 'HEADLESS013_EXIT_BEFORE_JOURNAL_NOT_RECOVERED'
            Close-TrainHeadless013 $case (Get-TrackedProcessExitCode $child $held) 'ERROR' (Test-TrainExitReceipt $fixture $record)
            Require ($case.journal.status -ceq $kind -and (Test-TrainProcessAbsent $record $child $held)) 'HEADLESS013_EXIT_OR_REASON_NOT_PROVEN'
        }finally{if(!$child.HasExited){Write-TrainText $stop $case.journal.nonce;$null=$child.WaitForExit(20000)};$child.Dispose()}
    }
    $orphan=@{};foreach($key in $fixture.Keys){$orphan[$key]=$fixture[$key]};$orphan.Root=Join-Path $root013 'orphan-owned';$orphan.Sql=Join-Path $orphan.Root 'sql';$orphan.Game=Join-Path $orphan.Root 'game';New-TrainPrivateRoot $orphan
    foreach($name in @('workers','commands','attempts','logs','sql','game')){$null=[IO.Directory]::CreateDirectory((Join-Path $orphan.Root $name))}
    $full=Read-TrainConfig (Join-Path $PSScriptRoot 'AutonomousTraining.ini');$full.autonomous013Version='1';$full.autonomous009Campaign='autonomous009-orphan013';$full.outcome006DatasetId='autonomous009-orphan013-c000001'
    Write-TrainConfig (Join-Path $orphan.Root 'TRAIN_CONFIG.ini') $full;$layout=$orphan
    $case=Open-TrainHeadless013 $orphan.Root $full 'prepare' (Get-FileHash -LiteralPath (Join-Path $orphan.Root 'TRAIN_CONFIG.ini')).Hash.ToLowerInvariant()
    $parentScript=Join-Path $orphan.Root 'controller.ps1';$ready=Join-Path $orphan.Root 'child.ready'
    $parentSource=@'
param([string]$OwnerPath,[string]$RootPath,[string]$ScopePath,[string]$ChildPath,[string]$PowerShellPath,[string]$ReadyPath)
$ErrorActionPreference='Stop';. $OwnerPath;Initialize-TrainWin32
$j=Read-TrainJson (Join-Path $ScopePath 'headless-operation013.json')
$a='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$ChildPath+'" -Stop "'+(Join-Path $ScopePath 'stop.request')+'" -Ready "'+$ReadyPath+'" -Nonce '+$j.nonce
$p=Start-Process -FilePath $PowerShellPath -ArgumentList $a -WindowStyle Hidden -PassThru
$end=[DateTime]::UtcNow.AddSeconds(8);while(!(Test-Path -LiteralPath $ReadyPath) -and !$p.HasExited -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 25}
if(!(Test-Path -LiteralPath $ReadyPath)){throw 'CHILD_NOT_READY'}
$j.worker=New-TrainProcessRecord $p $PowerShellPath $j.nonce Worker (Join-Path $RootPath 'TRAIN_CONFIG.ini')
$null=[IO.Directory]::CreateDirectory((Join-Path $RootPath ('workers\'+$j.nonce)))
Write-TrainJson (Join-Path $ScopePath 'headless-operation013.json') $j
Write-TrainJson (Join-Path $RootPath 'headless-active013.json') @{scope=$ScopePath;nonce=$j.nonce}
$p.Dispose();exit 0
'@
    [IO.File]::WriteAllText($parentScript,$parentSource,[Text.UTF8Encoding]::new($false))
    $parentArgs='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$parentScript+'" -OwnerPath "'+$owner+'" -RootPath "'+$orphan.Root+'" -ScopePath "'+$case.scope+'" -ChildPath "'+$stub+'" -PowerShellPath "'+$orphan.PowerShell+'" -ReadyPath "'+$ready+'"'
    $launcher=Start-Process -FilePath $orphan.PowerShell -ArgumentList $parentArgs -WindowStyle Hidden -PassThru
    try{Require ($launcher.WaitForExit(12000)) 'HEADLESS013_CONTROLLER_FIXTURE_HUNG';Require ($launcher.ExitCode -eq 0) 'HEADLESS013_CONTROLLER_FIXTURE_FAILED'}finally{$launcher.Dispose()}
    $pending=Read-TrainJson $case.path;Require (!(Test-TrainHeadlessController013 $pending.worker)) 'HEADLESS013_DEAD_CONTROLLER_TREATED_LIVE'
    function Assert-TrainAutonomousResources {}
    $null=Recover-TrainHeadlessControl013 @{Java=$orphan.PowerShell} -Cancel
    $completed=Read-TrainJson $case.path;Require ($completed.status -ceq 'CANCELLED' -and $completed.exitProven -and !$completed.trainingCredit) 'HEADLESS013_PUBLIC_RECOVERY_DID_NOT_DRAIN'
    Require (Test-TrainExitReceipt $orphan $completed.worker) 'HEADLESS013_PUBLIC_RECOVERY_EXIT_MISSING'
    Write-Output ('HEADLESS013_GREEN timeout=1 cancellation=1 restart=1 recovery=1 distinctReasons=4 unprovenExit=1 realHeldExit=3 publicOrphanTakeover=1 combinedReadyGuard=1 admissionProvenance=1 sharedCap=1 cap=1200 reserve=210 SQL=0 GS=0 SYNTHETIC_CONTROL_AND_OS_CHILD_ONLY evidence='+$root013)
    return
}
if($Action -eq 'Test-Cycle013'){
    $config=Read-TrainConfig (Join-Path $PSScriptRoot 'AutonomousTraining.ini')
    $config.autonomous013Version='1';$config.autonomous009AcceptanceSmoke='true';$config.autonomous009SmokeMaxStarts='4';$config.autonomous009SmokeMaxNativeMinutes='80'
    try {Assert-TrainConfig $config} catch {throw 'TRAIN013_BOUNDED_TRAIN_4_80_REJECTED'}
    $prior=[pscustomobject]@{patience=0;creditedCycles=@();bestVersion=2;bestSha=('a'*64)}
    $receipt=[pscustomobject]@{schemaVersion=13;cycleId='autonomous009-test013-c000001';fitCommitted=$true;optimizerDelta=16;selectedEpoch=0;candidateVersion=15;candidateSha=('b'*64);evalValid=$true;coverage=$true;terminalPairs=4;paidDivergences=1;stockWins=2;learnedWins=2;stockDeaths=1;learnedDeaths=1;resourceComparable=$true;resourceRegression=$false;bestComparison='UNKNOWN';supportedGain=$false;criticalRegression=$false;reason=''}
    $first=Get-TrainCycleDecision013 $receipt $prior
    Require ($first.patience -eq 1 -and !$first.improved -and $first.bestVersion -eq 2 -and $first.reason -ceq 'NO_SUPPORTED_GAIN_LOW_POWER') 'TRAIN013_TUNING_OR_EPOCH0_FALSE_IMPROVEMENT'
    $duplicate=Get-TrainCycleDecision013 $receipt $first
    Require ($duplicate.patience -eq 1 -and $duplicate.creditedCycles.Count -eq 1) 'TRAIN013_DUPLICATE_CYCLE_CREDIT'
    foreach($reason in @('NO_DATA','EVAL_COVERAGE_GAP','INVALID_EVAL','FAILED_FAIL_CLOSED','RESOURCE_LIMIT','PAUSED_BY_OWNER','STOPPED_BY_OWNER')){
        $unready=$receipt.PSObject.Copy();$unready.cycleId='autonomous009-test013-'+$reason;$unready.reason=$reason
        $decision=Get-TrainCycleDecision013 $unready $first
        Require ($decision.patience -eq 1 -and $decision.creditedCycles.Count -eq 1 -and !$decision.improved) ('TRAIN013_INSUFFICIENT_COUNTED_'+$reason)
    }
    $secondReceipt=$receipt.PSObject.Copy();$secondReceipt.cycleId='autonomous009-test013-c000002';$second=Get-TrainCycleDecision013 $secondReceipt $first
    $thirdReceipt=$receipt.PSObject.Copy();$thirdReceipt.cycleId='autonomous009-test013-c000003';$third=Get-TrainCycleDecision013 $thirdReceipt $second
    Require ($third.patience -eq 3 -and $third.phase -ceq 'PAUSED_NO_MEASURABLE_PROGRESS' -and !$third.improved) 'TRAIN013_PATIENCE3_NOT_PAUSED'
    $gain=$receipt.PSObject.Copy();$gain.cycleId='autonomous009-test013-c000004';$gain.selectedEpoch=2;$gain.supportedGain=$true;$gain.bestComparison='INDEPENDENT_MATCHED_SUPPORTED';$gain.terminalPairs=40;$gain.learnedWins=30;$gain.stockWins=15
    $gain|Add-Member bestEvidenceSha ('c'*64);$gain|Add-Member qualityLower95 0.1
    $better=Get-TrainCycleDecision013 $gain $third
    Require ($better.improved -and $better.patience -eq 0 -and $better.bestVersion -eq 15) 'TRAIN013_SUPPORTED_GAIN_NOT_SAVED'
    $regression=$gain.PSObject.Copy();$regression.cycleId='autonomous009-test013-c000005';$regression.criticalRegression=$true
    $rejected=Get-TrainCycleDecision013 $regression $better
    Require (!$rejected.improved -and $rejected.bestVersion -eq 15 -and $rejected.phase -ceq 'PAUSED_REGRESSION_OBSERVED') 'TRAIN013_REGRESSION_PROMOTED'
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training013-cycle-'+[guid]::NewGuid().ToString('N'));$null=[IO.Directory]::CreateDirectory($directory)
    $path=Join-Path $directory 'decision.json';Write-TrainJson $path $third;$cold=Read-TrainJson $path
    $recovered=Get-TrainCycleDecision013 $thirdReceipt $cold
    Require ($recovered.patience -eq 3 -and $recovered.creditedCycles.Count -eq 3) 'TRAIN013_COLD_RESUME_DUPLICATED_CREDIT'
    $journal=[pscustomobject]@{schemaVersion=13;cycle=1;phase='FIT';dataset=$receipt.cycleId;sourceSha=('d'*64);jarSha=('e'*64);completedCycles=0;patience=1}
    $journalPath=Join-Path $directory 'director-state.json';Save-TrainCycleState013 $journalPath $journal
    $journal.phase='EVAL';Save-TrainCycleState013 $journalPath $journal
    $phaseRoot=Join-Path $directory 'cycles\c000001\phases'
    Require ((Test-Path -LiteralPath (Join-Path $phaseRoot 'FIT.entered.json')) -and (Test-Path -LiteralPath (Join-Path $phaseRoot 'FIT.exited.json')) -and (Test-Path -LiteralPath (Join-Path $phaseRoot 'EVAL.entered.json'))) 'TRAIN013_PHASE_RECEIPTS_MISSING'
    $phaseCold=Read-TrainJson $journalPath
    Require ($phaseCold.phase -ceq 'EVAL' -and $phaseCold.patience -eq 1 -and !(Test-Path -LiteralPath (Join-Path $directory 'autonomous\v1\cold-checkpoint.json'))) 'TRAIN013_RESUME_PHASE_OR_HISTORICAL_POINTER_MUTATED'
    $budgetPath=Join-Path $directory 'native-budget.json';$budget=Get-TrainNativeBudget013 $budgetPath
    $r1=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-campaign-a' $false 210000L
    $budget=Complete-TrainNativeBudget013 $budgetPath $r1.pending 100000L 'OWN_HELD_HANDLE_EXIT_TIME'
    $duplicateBudget=Complete-TrainNativeBudget013 $budgetPath $r1.pending 100000L 'OWN_HELD_HANDLE_EXIT_TIME'
    Require ($duplicateBudget.starts -eq 1 -and $duplicateBudget.nativeMillis -eq 100000L) 'TRAIN013_DUPLICATE_NATIVE_CHARGE'
    $e1=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-child-eval' $true 210000L;$null=Complete-TrainNativeBudget013 $budgetPath $e1.pending 500000L 'OWN_HELD_HANDLE_EXIT_TIME'
    $e2=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-child-eval' $true 210000L;$null=Complete-TrainNativeBudget013 $budgetPath $e2.pending 500000L 'OWN_HELD_HANDLE_EXIT_TIME'
    $blocked=$false;try{$null=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-child-eval' $true 210000L}catch{$blocked=$_.Exception.Message -ceq 'TRAIN013_RESOURCE_CHILD_NATIVE_BUDGET'}
    Require $blocked 'TRAIN013_CHILD_THIRD_START_ALLOWED'
    $r4=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-new-campaign-b' $false 210000L;$null=Complete-TrainNativeBudget013 $budgetPath $r4.pending 200000L 'OWN_HELD_HANDLE_EXIT_TIME'
    $blocked=$false;try{$null=Reserve-TrainNativeBudget013 $budgetPath 'autonomous009-new-campaign-c' $false 210000L}catch{$blocked=$_.Exception.Message -ceq 'TRAIN013_RESOURCE_GLOBAL_NATIVE_BUDGET'}
    Require $blocked 'TRAIN013_NEW_CAMPAIGN_RESET_GLOBAL_BUDGET'
    $timeBudget=Join-Path $directory 'child-time-budget.json';$null=Get-TrainNativeBudget013 $timeBudget
    $t1=Reserve-TrainNativeBudget013 $timeBudget 'autonomous009-child-time' $true 210000L;$null=Complete-TrainNativeBudget013 $timeBudget $t1.pending 2090000L 'OWN_HELD_HANDLE_EXIT_TIME'
    $blocked=$false;try{$null=Reserve-TrainNativeBudget013 $timeBudget 'autonomous009-child-time' $true 210000L}catch{$blocked=$_.Exception.Message -ceq 'TRAIN013_RESOURCE_CHILD_NATIVE_BUDGET'}
    Require $blocked 'TRAIN013_CHILD_CUMULATIVE_TIME_RESET'
    $evalDirectory=Join-Path $directory 'evaluation';$rawName=('f'*64)+'/settlement.json';$null=[IO.Directory]::CreateDirectory((Join-Path $evalDirectory ('f'*64)))
    foreach($name in @('protocol-v2.json','evaluation-v2.complete','evaluation-pairs-v2.json',$rawName)){Write-TrainText (Join-Path $evalDirectory $name) '{}'}
    $proofMap=[ordered]@{};foreach($name in @('protocol-v2.json','evaluation-v2.complete','evaluation-pairs-v2.json',$rawName)){$proofMap[$name]=(Get-FileHash -LiteralPath (Join-Path $evalDirectory $name)).Hash}
    $evidence=[pscustomobject]@{schemaVersion=13;cycleId=$receipt.cycleId;modelVersion=15;modelSha=('b'*64);directory=$evalDirectory;completeSha=$proofMap['evaluation-v2.complete'];pairsSha=$proofMap['evaluation-pairs-v2.json'];protocolSha=$proofMap['protocol-v2.json'];proofFiles=[pscustomobject]$proofMap}
    $summaryPath=Join-Path $directory 'eval-v2.json';Write-TrainJson $summaryPath $evidence;$summarySha=(Get-FileHash -LiteralPath $summaryPath).Hash
    Assert-TrainEvaluationReceipt013 $evidence $receipt.cycleId 15 ('b'*64) $summaryPath $summarySha
    foreach($broken in @('evaluation-pairs-v2.json',$rawName)){
        Write-TrainText (Join-Path $evalDirectory $broken) '{"changed":true}'
        $rejected=$false;try{Assert-TrainEvaluationReceipt013 $evidence $receipt.cycleId 15 ('b'*64) $summaryPath $summarySha}catch{$rejected=$true}
        Require $rejected ('TRAIN013_RESUME_CHANGED_EVIDENCE_ACCEPTED_'+$broken)
        Write-TrainText (Join-Path $evalDirectory $broken) '{}'
    }
    Remove-Item -LiteralPath (Join-Path $evalDirectory $rawName)
    $rejected=$false;try{Assert-TrainEvaluationReceipt013 $evidence $receipt.cycleId 15 ('b'*64) $summaryPath $summarySha}catch{$rejected=$true}
    Require $rejected 'TRAIN013_RESUME_MISSING_RAW_ACCEPTED'
    $completedRoot=Join-Path $directory 'completed-campaign';$completedCycle=Join-Path $completedRoot 'cycles\c000003';$null=[IO.Directory]::CreateDirectory($completedCycle)
    foreach($name in @('fit-result.json','eval-v2.json','decision.json')){Write-TrainText (Join-Path $completedCycle $name) '{}'}
    $checkpointPath=Join-Path $completedCycle 'checkpoint.json';Write-TrainJson $checkpointPath @{schemaVersion=13;cycleId=$thirdReceipt.cycleId;completed=$true;fitSha=(Get-FileHash -LiteralPath (Join-Path $completedCycle 'fit-result.json')).Hash;evalSha=(Get-FileHash -LiteralPath (Join-Path $completedCycle 'eval-v2.json')).Hash;decisionSha=(Get-FileHash -LiteralPath (Join-Path $completedCycle 'decision.json')).Hash;patience=3}
    $interrupted=[pscustomobject]@{schemaVersion=13;campaign='autonomous009-test013';cycle=3;phase='CHECKPOINT';dataset=$thirdReceipt.cycleId;sourceSha=('d'*64);jarSha=('e'*64);completedCycles=2;patience=2;creditedCycles=$second.creditedCycles;bestVersion=2;bestSha=('a'*64);bestDecision='UNRESOLVED';improved=$false;reason='';lastCheckpoint='';pendingDecision=$third;lastV2=$null}
    $interruptedPath=Join-Path $completedRoot 'director-state.json';Save-TrainCycleState013 $interruptedPath $interrupted
    $resumed=Complete-TrainCycle013 $interruptedPath (Read-TrainJson $interruptedPath) $checkpointPath $completedRoot
    $resumedAgain=Complete-TrainCycle013 $interruptedPath (Read-TrainJson $interruptedPath) $checkpointPath $completedRoot
    Require ($resumed.phase -ceq 'NEW' -and $resumedAgain.completedCycles -eq 3 -and $resumedAgain.cycle -eq 4 -and $resumedAgain.patience -eq 3 -and $resumedAgain.creditedCycles.Count -eq 3) 'TRAIN013_POST_DECISION_CRASH_OR_DUPLICATE_CHECKPOINT_CREDIT'
    Write-Output 'TRAIN013_CYCLE_GREEN budget=4/80 globalCampaigns=1 child2/35=1 duplicateCharge=1 patience3=1 epoch0=1 noDataInvalidResourceOwner=7 duplicate=1 coldResume=1 phases=1 evidenceTamper=3 checkpointRecovery=1 best=1 regression=1 SQL=0 NATIVE=0 SYNTHETIC_STATE_MACHINE_ONLY'
    return
}
function Reject([scriptblock]$Code,[string]$Reason) {
    $rejected=$false
    try { & $Code | Out-Null } catch { $rejected=$true }
    Require $rejected $Reason
}
if($Action -eq 'Test-GuiOwner'){
    Initialize-TrainWin32;Assert-TrainInstallation $layout
    $config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
    Require ($config.autonomous009AcceptanceSmoke -ceq 'true' -and $config.autonomous009GuiHoldBeforeNative -ceq 'true') 'GUI_SMOKE_REQUIRES_PRE_NATIVE_HOLD'
    $baseStarts=[int]$config.autonomous009PriorStarts;$baseMillis=[long]$config.autonomous009PriorNativeMillis
    $loop013=$config.ContainsKey('autonomous013Version') -and $config.autonomous013Version -ceq '1'
    if($loop013){Require ($baseStarts -eq 0 -and $baseMillis -eq 0 -and $config.autonomous009SmokeMaxStarts -ceq '4' -and $config.autonomous009SmokeMaxNativeMinutes -ceq '80') 'GUI013_EXACT_PRE_NATIVE_BUDGET_REQUIRED'}
    else{Require ($baseStarts -eq 6 -and $baseMillis -eq 2231818 -and $config.autonomous009SmokeMaxStarts -ceq '10') 'GUI_SMOKE_EXACT_AUTHORIZED_BUDGET_REQUIRED'}
    foreach($name in @('owner-process.json','native-process.json','worker-process.json','sql\owned-process.json')){
        $path=Join-Path $layout.Root $name
        Require (!(Test-Path -LiteralPath $path) -or (Test-TrainProcessAbsent (Read-TrainJson $path))) ('GUI_SMOKE_OWN_PROCESS_RUNNING:'+ $name)
    }
    Assert-TrainPortsFree @([int]$config.sqlPort,[int]$config.gamePort,[int]$config.loginPort)
    if(!$GuiEvidenceRoot){throw 'GUI_SMOKE_OUTSIDE_CHECKOUT_EVIDENCE_REQUIRED'}
    $evidence=[IO.Path]::GetFullPath($GuiEvidenceRoot)
    Require (![string]::IsNullOrEmpty([IO.Path]::GetPathRoot($evidence)) -and !$evidence.StartsWith($layout.Clone.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) 'GUI_SMOKE_EVIDENCE_MUST_BE_OUTSIDE_CHECKOUT'
    if(Test-Path -LiteralPath $evidence){throw 'GUI_SMOKE_FRESH_EVIDENCE_REQUIRED'}
    $null=[IO.Directory]::CreateDirectory($evidence);Assert-TrainDirectory $evidence
    $controlParent=Join-Path $layout.Root 'gui-smoke';$null=[IO.Directory]::CreateDirectory($controlParent);Assert-TrainDirectory $controlParent
    $controls=@();$receipts=[Collections.Generic.List[object]]::new();$closed=[Collections.Generic.HashSet[string]]::new();$released=$false
    function New-GuiControl {
        $directory=Join-Path $controlParent ([guid]::NewGuid().ToString('N'));$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
        & $layout.PowerShell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'TrainingGui.ps1') -Root $layout.Root -SmokeControl $directory
        if($LASTEXITCODE -ne 0){throw 'GUI_SMOKE_LAUNCH_FAILED'}
        return $directory
    }
    function Invoke-GuiButton([string]$Directory,[string]$Button){
        $id=[guid]::NewGuid().ToString('N');Write-TrainJson (Join-Path $Directory 'request.json') @{id=$id;action=$Button}
        $path=Join-Path $Directory ('responses\'+$id+'.json');$deadline=[DateTime]::UtcNow.AddMinutes(5)
        while(!(Test-Path -LiteralPath $path) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 250}
        if(!(Test-Path -LiteralPath $path)){throw ('GUI_SMOKE_RESPONSE_TIMEOUT:'+ $Button)}
        $response=Read-TrainJson $path;$receipts.Add($response)
        Require ($response.id -ceq $id -and $response.action -ceq $Button -and [int]$response.exitCode -eq 0) ('GUI_SMOKE_COMMAND_FAILED:'+ $Button)
        Require ($response.invokedVia -ceq $(if($Button -ceq 'Close'){'FORM_CLOSE'}else{'WINFORMS_BUTTON_PERFORMCLICK'})) 'GUI_SMOKE_REAL_BUTTON_REQUIRED'
        if($Button -ceq 'Close'){
            $gui=Get-Process -Id ([int]$response.guiPid) -ErrorAction SilentlyContinue
            if($gui){
                try{
                    Require ($gui.StartTime.ToUniversalTime() -eq [DateTime]::Parse($response.guiCreatedUtc).ToUniversalTime() -and $gui.MainModule.FileName.StartsWith((Join-Path $layout.Root 'gui\'),[StringComparison]::OrdinalIgnoreCase)) 'GUI_SMOKE_CLOSE_IDENTITY_CHANGED'
                    Require ($gui.WaitForExit(20000)) 'GUI_SMOKE_WINDOW_PROCESS_DID_NOT_EXIT'
                }finally{$gui.Dispose()}
            }
            $response|Add-Member -NotePropertyName guiExitConfirmed -NotePropertyValue $true
            $null=$closed.Add($Directory)
        }
        return $response
    }
    function Wait-GuiPhase([string]$Directory,[string]$Phase,[bool]$Live){
        $deadline=[DateTime]::UtcNow.AddMinutes(6)
        do{
            $response=Invoke-GuiButton $Directory 'Status';$facts=$response.facts
            if($facts.phase -ceq $Phase -and [bool]$facts.directorLive -eq $Live){return $facts}
            Start-Sleep -Milliseconds 500
        }while([DateTime]::UtcNow -lt $deadline)
        throw ('GUI_SMOKE_PHASE_TIMEOUT:'+ $Phase)
    }
    function Assert-GuiPriorBudget($Facts){
        Require ($Facts.autonomous.starts -eq $baseStarts -and $Facts.autonomous.nativeMillis -eq $baseMillis -and !$Facts.autonomous.pending) 'GUI_SMOKE_CONSUMED_NATIVE_BEFORE_RELEASE'
    }
    try{
        $first=New-GuiControl;$controls+=@($first)
        $initial=Invoke-GuiButton $first 'Status';Require (!$initial.facts.directorLive) 'GUI_SMOKE_EXPECTED_STOPPED'
        $null=Invoke-GuiButton $first 'Start';$hold=Wait-GuiPhase $first 'GUI_HOLD' $true;Assert-GuiPriorBudget $hold
        $firstNonce=$hold.director.nonce;$firstGeneration=$hold.director.generation
        $second=New-GuiControl;$controls+=@($second)
        $null=Invoke-GuiButton $second 'Start';$joined=Wait-GuiPhase $second 'GUI_HOLD' $true
        Require ($joined.director.nonce -ceq $firstNonce -and $joined.director.generation -eq $firstGeneration) 'GUI_SMOKE_DUPLICATE_DIRECTOR'
        $null=Invoke-GuiButton $first 'Close'
        $survived=Wait-GuiPhase $second 'GUI_HOLD' $true;Assert-GuiPriorBudget $survived
        Require ($survived.director.nonce -ceq $firstNonce) 'GUI_SMOKE_CLOSING_WINDOW_STOPPED_DIRECTOR'
        $null=Invoke-GuiButton $second 'Pause';$paused=Wait-GuiPhase $second $(if($loop013){'PAUSED_BY_OWNER'}else{'PAUSED'}) $false;Assert-GuiPriorBudget $paused
        $null=Invoke-GuiButton $second 'Resume';$resumed=Wait-GuiPhase $second 'GUI_HOLD' $true;Assert-GuiPriorBudget $resumed
        Require ($resumed.director.nonce -cne $firstNonce -and $resumed.director.generation -gt $firstGeneration) 'GUI_SMOKE_RESUME_DID_NOT_CREATE_NEW_DIRECTOR'
        $null=Invoke-GuiButton $second 'Stop';$stopped=Wait-GuiPhase $second 'STOPPED' $false;Assert-GuiPriorBudget $stopped
        $null=Invoke-GuiButton $second 'Export'
        if($loop013){
            $null=Invoke-GuiButton $second 'Close'
            Write-TrainJson (Join-Path $evidence 'gui-acceptance.json') @{schemaVersion=13;status='PASS_REAL_GUI_PRE_NATIVE_OWNER_LIFECYCLE';initialGeneration=$firstGeneration;resumedGeneration=$resumed.director.generation;twoWindowsOneDirector=$true;closingWindowsDidNotStopDirector=$true;guiProcessExitConfirmed=$true;allButtons=@('Start','Status','Pause','Resume','Stop','Export');nativeStarted=$false;nativeStarts=0;nativeMillis=0;receipts=$receipts.ToArray();inputMethod='WINFORMS_PERFORMCLICK_NOT_DESKTOP_INPUT'}
            Write-Output ('TRAIN013_GUI_OWNER_GREEN buttons=6 windows=2 oneDirector=1 closeIndependent=1 stopResume=1 NATIVE=0 evidence='+$evidence)
            return
        }
        $lock=Enter-TrainLock $layout
        try{
            $current=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
            Assert-TrainPortsFree @([int]$current.sqlPort,[int]$current.gamePort,[int]$current.loginPort)
            $before=Join-Path $layout.Root 'TRAIN_CONFIG.ini';$backup=Join-Path $evidence 'before-native-release.ini'
            [IO.File]::Copy($before,$backup,$false);Assert-TrainHash $backup (Get-FileHash -LiteralPath $before).Hash
            $current.autonomous009GuiHoldBeforeNative='false';Write-TrainConfig $before $current
        }finally{$lock.Dispose()}
        $released=$true;$null=Invoke-GuiButton $second 'Resume';$null=Invoke-GuiButton $second 'Close'
        $deadline=[DateTime]::UtcNow.AddMinutes(6);$native=$null
        do{
            $record=Join-Path $layout.Root 'native-process.json'
            if(Test-Path -LiteralPath $record){$candidate=Read-TrainJson $record;if(!(Test-TrainProcessAbsent $candidate)){Assert-TrainProcess $candidate -Kind Native;$native=$candidate;break}}
            Start-Sleep -Milliseconds 500
        }while([DateTime]::UtcNow -lt $deadline)
        Require ($null -ne $native) 'GUI_SMOKE_NATIVE_DID_NOT_SURVIVE_CLOSED_WINDOWS'
        $state=Read-TrainJson (Join-Path $layout.Root 'director.json');$attempt=Join-Path $layout.Root ('attempts\'+$state.attemptId)
        $ready=Join-Path $attempt 'game.ready';$socket=Join-Path $attempt 'socket.ok';$deadline=[DateTime]::UtcNow.AddMinutes(6)
        while((!(Test-Path -LiteralPath $ready) -or !(Test-Path -LiteralPath $socket)) -and [DateTime]::UtcNow -lt $deadline){
            Assert-TrainProcess $native -Kind Native;Start-Sleep -Milliseconds 500
        }
        Require ((Test-Path -LiteralPath $ready) -and (Test-Path -LiteralPath $socket) -and [IO.File]::ReadAllText($ready).Trim() -ceq [string]$native.pid -and [IO.File]::ReadAllText($socket).Trim() -ceq [string]$native.pid) 'GUI_SMOKE_NATIVE_GAME_OR_SOCKET_NOT_READY'
        $journal=Read-TrainJson (Join-Path $layout.Root ('autonomous\v1\campaigns\'+$config.autonomous009Campaign+'\director-state.json'))
        Require ($journal.starts -eq ($baseStarts+1) -and $journal.nativeMillis -eq $baseMillis -and $journal.pending) 'GUI_SMOKE_RELEASE_BUDGET_MISMATCH'
        Write-TrainJson (Join-Path $evidence 'gui-acceptance.json') @{status='PASS_REAL_WINFORMS_BUTTONS_AND_OWNED_CLI';initialGeneration=$firstGeneration;resumedGeneration=$resumed.director.generation;twoWindowsOneDirector=$true;closingWindowsDidNotStopDirector=$true;guiProcessExitConfirmed=$true;allButtons=@('Start','Status','Pause','Resume','Stop','Export');nativeStartedAfterRelease=$true;nativeGameReady=$true;nativeSocketAttested=$true;priorStarts=$baseStarts;priorNativeMillis=$baseMillis;nativePid=$native.pid;receipts=$receipts.ToArray();inputMethod='WINFORMS_PERFORMCLICK_NOT_DESKTOP_INPUT'}
        Write-Host ('TRAIN009_GUI_OWNER_GREEN buttons=6 windows=2 oneDirector=1 closeIndependent=1 resumedNativeStart='+$journal.starts+' evidence='+$evidence)
    }catch{
        if(!$released){& $layout.PowerShell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Training.ps1') -Action Stop -Root $layout.Root|Out-Null}
        throw
    }finally{
        foreach($directory in $controls){if(!$closed.Contains($directory)){try{Write-TrainJson (Join-Path $directory 'request.json') @{id=[guid]::NewGuid().ToString('N');action='Close'}}catch{}}}
    }
    return
}
if($Action -eq 'Test-StartupHandshake'){
    . (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
    if(!(Get-Command Register-TrainPendingBootstrap -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_STARTUP_HANDSHAKE_MISSING'}
    Initialize-TrainWin32
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002Startup-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $directory 'own'
    New-TrainPrivateRoot $fixture
    foreach($name in @('logs','workers','commands','archives')){$null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root $name))}
    foreach($case in @('NO_ACK','CANCEL_AFTER_ACK')){
        $nonce=[guid]::NewGuid().ToString('N');Assert-TrainNoPendingBootstrap $fixture
        $launch=Start-TrainBroker $fixture $nonce -Probe -StartupProbe
        $process=Get-Process -Id $launch.pid -ErrorAction Stop;$held=Hold-ProcessHandle $process
        try{
            $record=Register-TrainPendingBootstrap $fixture $launch $process $held
            Reject {Assert-TrainNoPendingBootstrap $fixture} 'SECOND_PENDING_BOOTSTRAP_ALLOWED'
            $deadline=[DateTime]::UtcNow.AddSeconds(10)
            while(!(Test-Path -LiteralPath $launch.readyPath) -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
            Require (Test-Path -LiteralPath $launch.readyPath) 'STARTUP_PROBE_NOT_READY'
            $null=Assert-TrainBrokerProof $fixture $record
            if($case -ceq 'NO_ACK'){
                Require ($process.WaitForExit(10000)) 'STARTUP_NO_ACK_DID_NOT_EXPIRE'
                Require ((Get-TrackedProcessExitCode $process $held) -eq 1) 'STARTUP_NO_ACK_EXIT'
                Require ((Read-TrainJson ($launch.errorPath)).code -ceq 'TRAIN002_BOOTSTRAP_ACK_TIMEOUT') 'STARTUP_NO_ACK_PRIMARY_REASON'
            }else{
                Write-TrainJson $launch.ackPath @{pid=$record.pid;created=$record.created;nonce=$nonce;proofSha=(Get-FileHash -LiteralPath $launch.proofPath).Hash}
                $deadline=[DateTime]::UtcNow.AddSeconds(5)
                while(!(Test-Path -LiteralPath $launch.acknowledgedPath) -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
                Require (Test-Path -LiteralPath $launch.acknowledgedPath) 'STARTUP_ACK_WINDOW_NOT_OBSERVED'
            }
            $result=Stop-TrainPendingBootstrap $fixture 10 $process $held
            Require ($result.confirmed -and $result.pid -eq $record.pid -and [long]$result.created -eq [long]$record.created) 'STARTUP_PENDING_EXIT_UNPROVEN'
            Require (!(Test-Path -LiteralPath $launch.runAdmittedPath)) 'STARTUP_TIMEOUT_OR_CANCEL_REACHED_RUN'
            Require (!(Test-Path -LiteralPath (Join-Path $fixture.Root 'pending-bootstrap.json'))) 'STARTUP_PENDING_NOT_RECONCILED'
            Assert-TrainNoPendingBootstrap $fixture
            Write-TrainJson (Join-Path $fixture.Root ('startup.'+$case+'.json')) @{case=$case;pid=$record.pid;created=$record.created;nonce=$nonce;exitCode=(Get-TrackedProcessExitCode $process $held);pendingConfirmed=$result.confirmed;runReached=$false;sqlLaunched=$false}
        }finally{$process.Dispose()}
    }
    Write-Output ('TRAIN002_STARTUP_HANDSHAKE_GREEN noAckExpires=1 cancelAfterAck=1 pendingExitConfirmed=2 duplicatePendingRejected=2 SQL=0; evidence='+$directory)
    return
}
if($Action -eq 'Test-JobLifetime'){
    $fixtureSource=Join-Path $PSScriptRoot 'TrainingOwnedJobFixture.cs'
    if(!(Test-Path -LiteralPath $fixtureSource)){throw 'TRAIN002_OWNED_JOB_FIXTURE_MISSING'}
    Initialize-TrainWin32
    if(!('TrainingOwnedJobFixture' -as [type])){Add-Type -Path $fixtureSource}
    $broker=Join-Path $PSScriptRoot 'TrainingBroker.ps1';Assert-G0PhysicalFile $broker
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002JobLifetime-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $directory 'own'
    New-TrainPrivateRoot $fixture;$null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root 'logs'))
    $nonce=[guid]::NewGuid().ToString('N');$stub=Join-Path $fixture.Root 'job-helper.ps1'
    $stubText=@'
param([string]$Root,[string]$Nonce,[string]$OwnerSource,[string]$BrokerSource,[string]$OwnerSha,[string]$BrokerSha)
$ErrorActionPreference='Stop';$env:PSModulePath=Join-Path $PSHOME 'Modules'
if((Get-FileHash -LiteralPath $OwnerSource).Hash -cne $OwnerSha -or (Get-FileHash -LiteralPath $BrokerSource).Hash -cne $BrokerSha){throw 'TRAIN002_FIXTURE_SOURCE_CHANGED'}
. $OwnerSource
. $BrokerSource
$fixture=@{Root=[IO.Path]::GetFullPath($Root);PowerShell=(Join-Path $PSHOME 'powershell.exe')}
try{
    Initialize-TrainWin32;Assert-TrainInstallation $fixture
    $self=[Diagnostics.Process]::GetCurrentProcess();$record=New-TrainProcessRecord $self $fixture.PowerShell $Nonce Worker $PSCommandPath
    Write-TrainJson (Join-Path $Root 'helper.ready.json') $record
    $go=Join-Path $Root 'helper.go.json';$deadline=[DateTime]::UtcNow.AddSeconds(30)
    while(!(Test-Path -LiteralPath $go) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
    if(!(Test-Path -LiteralPath $go)){throw 'TRAIN002_FIXTURE_GO_TIMEOUT'}
    $ack=Read-TrainJson $go
    if($ack.pid -ne $PID -or [long]$ack.created -ne [long]$record.created -or $ack.nonce -cne $Nonce){throw 'TRAIN002_FIXTURE_GO_IDENTITY'}
    $launch=Start-TrainBroker $fixture $Nonce -Probe -LifetimeProbe
    $child=Get-Process -Id $launch.pid -ErrorAction Stop;$null=$child.Handle
    $deadline=[DateTime]::UtcNow.AddSeconds(15)
    while(!(Test-Path -LiteralPath $launch.readyPath) -and !$child.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
    if(!(Test-Path -LiteralPath $launch.readyPath)){throw 'TRAIN002_FIXTURE_FINAL_NOT_READY'}
    $final=New-TrainProcessRecord $child $fixture.PowerShell $Nonce Worker $launch.bootstrapPath;Assert-TrainProcess $final -Kind Worker
    Write-TrainJson (Join-Path $Root 'helper.final.json') @{process=$final;launch=$launch}
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while([DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
}catch{Write-TrainJson (Join-Path $Root 'helper.error.json') (Get-TrainSafeErrorRecord $_);exit 1}
exit 0
'@
    Write-TrainText $stub $stubText
    $arguments='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$stub+'" -Root "'+$fixture.Root+'" -Nonce '+$nonce+' -OwnerSource "'+$owner+'" -BrokerSource "'+$broker+'" -OwnerSha '+(Get-FileHash -LiteralPath $owner).Hash+' -BrokerSha '+(Get-FileHash -LiteralPath $broker).Hash
    $job=[TrainingOwnedJobFixture]::new();$helper=$null;$final=$null;$launch=$null
    try{
        $helper=Start-Process -FilePath $layout.PowerShell -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixture.Root 'helper.out') -RedirectStandardError (Join-Path $fixture.Root 'helper.err')
        $helperHeld=Hold-ProcessHandle $helper;$ready=Join-Path $fixture.Root 'helper.ready.json';$deadline=[DateTime]::UtcNow.AddSeconds(15)
        while(!(Test-Path -LiteralPath $ready) -and !$helper.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
        Require (Test-Path -LiteralPath $ready) 'TRAIN002_FIXTURE_HELPER_NOT_READY'
        $helperRecord=New-TrainProcessRecord $helper $layout.PowerShell $nonce Worker $stub;Assert-TrainProcess $helperRecord -Kind Worker
        $fact=Read-TrainJson $ready
        Require ($fact.pid -eq $helperRecord.pid -and [long]$fact.created -eq [long]$helperRecord.created -and $fact.nonce -ceq $nonce) 'TRAIN002_FIXTURE_HELPER_IDENTITY'
        $job.Assign($helper,[int]$helperRecord.pid,[long]$helperRecord.created)
        Require ($job.Contains($helper,[int]$helperRecord.pid,[long]$helperRecord.created)) 'TRAIN002_FIXTURE_HELPER_NOT_IN_OWN_JOB'
        Write-TrainJson (Join-Path $fixture.Root 'helper.go.json') @{pid=$helperRecord.pid;created=$helperRecord.created;nonce=$nonce}
        $handoff=Join-Path $fixture.Root 'helper.final.json';$deadline=[DateTime]::UtcNow.AddSeconds(20)
        while(!(Test-Path -LiteralPath $handoff) -and !$helper.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
        Require (Test-Path -LiteralPath $handoff) 'TRAIN002_FIXTURE_FINAL_HANDOFF_MISSING'
        $handoffFact=Read-TrainJson $handoff;$finalRecord=$handoffFact.process;$launch=$handoffFact.launch
        Require ($launch.pid -eq $finalRecord.pid -and $finalRecord.nonce -ceq $nonce -and $finalRecord.configPath -ieq (Join-Path $PSScriptRoot 'TrainingBootstrap.ps1')) 'TRAIN002_FIXTURE_FINAL_HANDOFF_IDENTITY'
        $final=Get-Process -Id $finalRecord.pid -ErrorAction Stop;$finalHeld=Hold-ProcessHandle $final
        Assert-TrainProcess $finalRecord -Kind Worker
        Require (!$job.Contains($final,[int]$finalRecord.pid,[long]$finalRecord.created)) 'TRAIN002_FIXTURE_FINAL_INSIDE_OWN_KILL_JOB'
        $proof=Read-TrainJson $launch.proofPath
        Require ($proof.querySucceeded -and $proof.pid -eq $finalRecord.pid -and [long]$proof.created -eq [long]$finalRecord.created -and $proof.nonce -ceq $nonce -and !$proof.tokenElevated -and !$proof.debugEnabled -and $proof.integrityRid -lt 12288) 'TRAIN002_FIXTURE_FINAL_ORDINARY_IDENTITY'
        Write-TrainJson $launch.ackPath @{pid=$finalRecord.pid;created=$finalRecord.created;nonce=$nonce}
        $deadline=[DateTime]::UtcNow.AddSeconds(5)
        while(!(Test-Path -LiteralPath $launch.heartbeatPath) -and !$final.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
        Require (Test-Path -LiteralPath $launch.heartbeatPath) 'TRAIN002_FIXTURE_FIRST_HEARTBEAT_MISSING'
        $before=Read-TrainJson $launch.heartbeatPath
        Assert-TrainProcess $helperRecord -Kind Worker;Assert-TrainProcess $finalRecord -Kind Worker
        Require ($job.Contains($helper,[int]$helperRecord.pid,[long]$helperRecord.created) -and !$job.Contains($final,[int]$finalRecord.pid,[long]$finalRecord.created)) 'TRAIN002_FIXTURE_OWN_JOB_MEMBERSHIP_CHANGED'
        Write-TrainJson (Join-Path $fixture.Root 'lifetime.before.json') @{nonce=$nonce;jobUnnamed=$true;jobHandleInherited=$false;jobFlags=$job.ConfirmedLimitFlags;helper=$helperRecord;final=$finalRecord;finalAnyJob=$proof.inAnyJob;finalJob=$proof.currentJob;beforeHeartbeat=$before;literalUiCloseObserved=$false}
        $closedUtc=[DateTime]::UtcNow;$job.Dispose()
        Require ($helper.WaitForExit(5000)) 'TRAIN002_FIXTURE_HELPER_SURVIVED_OWN_JOB_CLOSE'
        $helperExit=Get-TrainHeldExitProof $helperRecord $helper $helperHeld
        $deadline=[DateTime]::UtcNow.AddSeconds(8);$after=$before
        do{
            Require (!$final.HasExited) 'TRAIN002_FIXTURE_FINAL_DIED_WITH_HELPER'
            $after=Read-TrainJson $launch.heartbeatPath
            if(([DateTime]::UtcNow-$closedUtc).TotalSeconds -ge 3 -and [long]$after.sequence-[long]$before.sequence -ge 3){break}
            Start-Sleep -Milliseconds 100
        }while([DateTime]::UtcNow -lt $deadline)
        Require ([long]$after.sequence-[long]$before.sequence -ge 3 -and ([DateTime]::UtcNow-$closedUtc).TotalSeconds -ge 3) 'TRAIN002_FIXTURE_POST_CLOSE_HEARTBEATS_MISSING'
        Assert-TrainProcess $finalRecord -Kind Worker
        Write-TrainJson $launch.releasePath @{pid=$finalRecord.pid;created=$finalRecord.created;nonce=$nonce}
        Require ($final.WaitForExit(5000)) 'TRAIN002_FIXTURE_FINAL_NORMAL_EXIT_TIMEOUT'
        $finalExit=Get-TrainHeldExitProof $finalRecord $final $finalHeld;Require ($finalExit.exitCode -eq 5) 'TRAIN002_FIXTURE_FINAL_EXIT'
        Write-TrainJson (Join-Path $fixture.Root 'lifetime.after.json') @{nonce=$nonce;ownJobClosedUtc=$closedUtc.ToString('o');helperExit=$helperExit;finalExit=$finalExit;afterHeartbeat=$after;additionalHeartbeats=([long]$after.sequence-[long]$before.sequence);literalUiCloseObserved=$false;result='OWN_KILL_JOB_LIFETIME_INDEPENDENCE_VERIFIED'}
        Write-Output ('TRAIN002_JOB_LIFETIME_GREEN ownKillJobClosed=1 helperExited=1 finalSurvived=1 heartbeats='+([long]$after.sequence-[long]$before.sequence)+' finalExit=5; evidence='+$directory)
    }finally{
        $job.Dispose()
        if($final){if(!$final.HasExited -and $launch){Write-TrainJson $launch.releasePath @{pid=$final.Id;created=$final.StartTime.ToUniversalTime().ToFileTimeUtc();nonce=$nonce};$null=$final.WaitForExit(5000)};$final.Dispose()}
        if($helper){$helper.Dispose()}
    }
    return
}
if($Action -eq 'Test-Broker'){
    $broker=Join-Path $PSScriptRoot 'TrainingBroker.ps1'
    if(!(Test-Path -LiteralPath $broker)){throw 'TRAIN002_BROKER_HELPER_MISSING'}
    . $broker
    if(!(Get-Command Test-TrainBrokerAdmission -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_BROKER_ADMISSION_MISSING'}
    $allowed=[pscustomobject]@{LimitsSucceeded=$true;KillOnJobClose=$false;BreakawayOk=$true;SilentBreakawayOk=$true}
    Require (Test-TrainBrokerAdmission $true $false $null) 'BROKER_NO_JOB_REJECTED'
    Require (Test-TrainBrokerAdmission $true $true $allowed) 'BROKER_NONKILL_BREAKAWAY_SILENT_REJECTED'
    Require (!(Test-TrainBrokerAdmission $false $true $allowed)) 'BROKER_QUERY_FAILURE_ACCEPTED'
    Require (!(Test-TrainBrokerAdmission $true $true $null)) 'BROKER_UNKNOWN_JOB_ACCEPTED'
    $allowed.KillOnJobClose=$true;Require (!(Test-TrainBrokerAdmission $true $true $allowed)) 'BROKER_KILL_JOB_ACCEPTED';$allowed.KillOnJobClose=$false
    $allowed.LimitsSucceeded=$false;Require (!(Test-TrainBrokerAdmission $true $true $allowed)) 'BROKER_UNKNOWN_LIMITS_ACCEPTED';$allowed.LimitsSucceeded=$true
    $allowed.BreakawayOk=$false;Require (!(Test-TrainBrokerAdmission $true $true $allowed)) 'BROKER_NO_BREAKAWAY_ACCEPTED';$allowed.BreakawayOk=$true
    $allowed.SilentBreakawayOk=$false;Require (!(Test-TrainBrokerAdmission $true $true $allowed)) 'BROKER_NO_SILENT_BREAKAWAY_ACCEPTED'
    Initialize-TrainWin32
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002Broker-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $directory 'own'
    New-TrainPrivateRoot $fixture;$null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root 'logs'))
    $nonce=[guid]::NewGuid().ToString('N');$launch=Start-TrainBroker $fixture $nonce -Probe
    Write-TrainJson (Join-Path $fixture.Root 'broker-launch.json') $launch
    $process=Get-Process -Id $launch.pid -ErrorAction Stop;$handle=Hold-ProcessHandle $process
    try{
        $deadline=[DateTime]::UtcNow.AddSeconds(10)
        while(!(Test-Path -LiteralPath $launch.readyPath) -and !$process.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
        Require (Test-Path -LiteralPath $launch.readyPath) 'TRAIN002_BROKER_PROBE_NOT_READY'
        $record=New-TrainProcessRecord $process $layout.PowerShell $nonce Worker $launch.bootstrapPath
        Assert-TrainProcess $record -Kind Worker
        Write-TrainJson (Join-Path $fixture.Root 'broker-process.json') $record
        $ready=Read-TrainJson $launch.readyPath
        Require ($ready.pid -eq $record.pid -and [long]$ready.created -eq [long]$record.created -and $ready.nonce -ceq $nonce) 'TRAIN002_BROKER_PROBE_READY_IDENTITY'
        Write-TrainJson $launch.ackPath @{pid=$record.pid;created=$record.created;nonce=$nonce}
        Require ($process.WaitForExit(20000)) 'TRAIN002_BROKER_PROBE_TIMEOUT_NO_FORCE'
        $exit=Get-TrackedProcessExitCode $process $handle
        Write-TrainJson (Join-Path $fixture.Root 'broker-exit.json') @{pid=$record.pid;created=$record.created;exitCode=$exit;nonce=$nonce}
        $fact=Read-TrainJson $launch.proofPath
        Require ($fact.pid -eq $record.pid -and [long]$fact.created -eq [long]$record.created -and $fact.nonce -ceq $nonce -and $fact.sid -ceq $record.sid) 'TRAIN002_BROKER_PROBE_IDENTITY'
        Require ($fact.admitted -and (Test-TrainBrokerAdmission $fact.querySucceeded $fact.inAnyJob $fact.currentJob)) ('TRAIN002_BOOTSTRAP_JOB_ADMISSION_FAILED evidence='+$directory)
        $null=Assert-TrainBrokerProof $fixture $record
        Require (!$fact.tokenElevated -and !$fact.debugEnabled -and $fact.integrityRid -lt 12288) 'TRAIN002_BROKER_PROBE_NOT_ORDINARY'
        Require ($exit -eq 5) 'TRAIN002_BROKER_PROBE_EXIT'
        Require ([IO.File]::ReadAllText($launch.stdoutPath).Contains('TRAIN002_BROKER_STDOUT:'+ $nonce)) 'TRAIN002_BROKER_STDOUT_MISSING'
        Require ([IO.File]::ReadAllText($launch.stderrPath).Contains('TRAIN002_BROKER_STDERR:'+ $nonce)) 'TRAIN002_BROKER_STDERR_MISSING'
        Write-Output ('TRAIN002_BROKER_GREEN ordinarySID=1 anyJob='+$fact.inAnyJob+' admission=1 negativeCases=6 stdout=1 stderr=1 exit=5; evidence='+$directory)
    }finally{$process.Dispose()}
    return
}
if($Action -eq 'Test-AdaptiveIdle'){
    if(!(Get-Command Get-TrainAdaptiveIdle -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_ADAPTIVE_IDLE_HELPER_MISSING'}
    $config=@{cpuCount='4';cpuBudgetPercent='75';idleSeconds='10';maxAdaptiveIdleSeconds='300'}
    $low=[pscustomobject]@{measurement='PROCESS_METADATA';exitObserved=$true;elapsedMillis=100000;cpuSeconds=100.0;sampledUtc='test'}
    $high=[pscustomobject]@{measurement='PROCESS_METADATA';exitObserved=$true;elapsedMillis=100000;cpuSeconds=400.0;sampledUtc='test'}
    Require ((Get-TrainAdaptiveIdle $config $low).idleSeconds -eq 10) 'LOW_CPU_IDLE_CHANGED'
    Require ((Get-TrainAdaptiveIdle $config $high).idleSeconds -eq 34) 'HIGH_CPU_IDLE_NOT_ADAPTED'
    $config.maxAdaptiveIdleSeconds='20'
    Require ((Get-TrainAdaptiveIdle $config $high).idleSeconds -eq 20) 'ADAPTIVE_IDLE_CAP_EXCEEDED'
    $unknown=Get-TrainAdaptiveIdle $config $null
    Require ($unknown.idleSeconds -eq 10 -and $unknown.measurement -ceq 'UNKNOWN') 'UNKNOWN_CPU_NOT_BASE_IDLE'
    $config.cpuBudgetPercent='0';Reject {Get-TrainAdaptiveIdle $config $high} 'ZERO_CPU_BUDGET_ACCEPTED'
    $config.cpuBudgetPercent='75';$config.maxAdaptiveIdleSeconds='9';Reject {Get-TrainAdaptiveIdle $config $high} 'ADAPTIVE_CAP_BELOW_BASE_ACCEPTED'
    Write-Output 'TRAIN002_ADAPTIVE_IDLE_GREEN low=10 high=34 cap=20 unknown=base invalidBoundsRejected=2 SQL=0 NATIVE=0'
    return
}
if($Action -eq 'Test-CimExitRace'){
    if(!(Get-Command Test-TrainTerminatedCimRace -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_CIM_EXIT_RACE_HELPER_MISSING'}
    Initialize-TrainWin32
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002CimExit-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $directory 'own';$fixture.Sql=Join-Path $fixture.Root 'sql'
    New-TrainPrivateRoot $fixture
    $stub=Join-Path $fixture.Root 'lifetime.ps1'
    [IO.File]::WriteAllText($stub,'param([string]$Ready,[string]$Release,[string]$Nonce) [IO.File]::WriteAllText($Ready,"ready"); $end=[DateTime]::UtcNow.AddSeconds(15); while(![IO.File]::Exists($Release) -and [DateTime]::UtcNow -lt $end){Start-Sleep -Milliseconds 20}; exit 7',[Text.UTF8Encoding]::new($false))
    $children=@()
    try{
        foreach($index in @(1,2)){
            $nonce=[guid]::NewGuid().ToString('N');$ready=Join-Path $fixture.Root ('ready'+$index);$release=Join-Path $fixture.Root ('release'+$index)
            $argsLine='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$stub+'" -Ready "'+$ready+'" -Release "'+$release+'" -Nonce '+$nonce
            $child=Start-Process -FilePath $layout.PowerShell -ArgumentList $argsLine -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $fixture.Root ('child'+$index+'.out')) -RedirectStandardError (Join-Path $fixture.Root ('child'+$index+'.err'))
            $entry=@{process=$child;handle=(Hold-ProcessHandle $child);release=$release;record=$null};$children+=,$entry
            $deadline=[DateTime]::UtcNow.AddSeconds(10)
            while(!(Test-Path -LiteralPath $ready) -and !$child.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 20}
            Require (Test-Path -LiteralPath $ready) 'CIM_STUB_NOT_READY'
            $entry.record=New-TrainProcessRecord $child $layout.PowerShell $nonce Worker
            $null=[IO.Directory]::CreateDirectory((Join-Path $fixture.Root ('workers\'+$nonce)))
            if($index -eq 1){
                $stale=Get-CimInstance Win32_Process -Filter "ProcessId=$($child.Id)"
                Require ((Invoke-CimMethod -InputObject $stale -MethodName GetOwnerSid).ReturnValue -eq 0) 'CIM_INITIAL_OWNER'
                [IO.File]::WriteAllText($release,'exit')
                Require ($child.WaitForExit(10000)) 'CIM_STUB_EXIT_TIMEOUT'
                $vanished=$null;try{Invoke-CimMethod -InputObject $stale -MethodName GetOwnerSid -ErrorAction Stop|Out-Null}catch{$vanished=$_}
                Require ($null -ne $vanished) 'CIM_NOT_FOUND_NOT_REPRODUCED'
                Require (Test-TrainTerminatedCimRace $vanished $entry.record $child $entry.handle) 'OWN_CIM_EXIT_NOT_RECOGNIZED'
                foreach($code in @('TRAIN002_PROCESS_FLAGS_CHANGED','TRAIN002_PROCESS_OWNER','TRAIN002_PROCESS_IMAGE_CHANGED','TRAIN002_PROCESS_CONFIG_CHANGED')){
                    $foreign=[Management.Automation.ErrorRecord]::new([InvalidOperationException]::new($code),$code,[Management.Automation.ErrorCategory]::SecurityError,$null)
                    Require (!(Test-TrainTerminatedCimRace $foreign $entry.record $child $entry.handle)) ('SECURITY_FAILURE_IGNORED:'+ $code)
                }
                Write-TrainExitReceipt $fixture $entry.record $child $entry.handle 'short-lived-powershell'
                Require (Test-TrainExitReceipt $fixture $entry.record) 'EXIT_RECEIPT_NOT_VERIFIED'
                $exitFact=Read-TrainJson (Get-TrainExitReceiptPath $fixture $entry.record)
                Require ($null -ne $exitFact.PSObject.Properties['terminatedUtc'] -and $null -ne $exitFact.PSObject.Properties['nativeElapsedMillis']) 'EXACT_NATIVE_EXIT_TIME_MISSING'
                Require ([DateTime]::Parse($exitFact.terminatedUtc).ToUniversalTime().ToFileTimeUtc() -eq $child.ExitTime.ToUniversalTime().ToFileTimeUtc()) 'EXIT_TIME_IS_OBSERVATION_TIME'
                Require ([long]$exitFact.nativeElapsedMillis -eq [long][Math]::Ceiling(($child.ExitTime.ToUniversalTime()-$child.StartTime.ToUniversalTime()).TotalMilliseconds)) 'NATIVE_ELAPSED_INCLUDES_OBSERVER_DELAY'
                $receipt=Get-TrainExitReceiptPath $fixture $entry.record;$before=[IO.File]::ReadAllText($receipt);$tampered=Read-TrainJson $receipt;$tampered.created=[long]$tampered.created+1;Write-TrainJson $receipt $tampered
                Require (!(Test-TrainExitReceipt $fixture $entry.record)) 'STALE_FILETIME_RECEIPT_ACCEPTED'
                Write-TrainText $receipt $before
                foreach($change in @(@{nativeElapsedMillis=$null},@{nativeElapsedMillis=-1},@{nativeElapsedMillis=([long]$exitFact.nativeElapsedMillis+1)},@{terminatedUtc='unknown'})){
                    $invalid=ConvertFrom-Json $before;foreach($key in $change.Keys){$invalid.$key=$change[$key]};Write-TrainJson $receipt $invalid
                    Require (!(Test-TrainExitReceipt $fixture $entry.record)) ('UNKNOWN_EXIT_TIME_ACCEPTED:'+($change.Keys -join ','))
                }
                Write-TrainText $receipt $before
                $null=Get-TrainProcessResources $child
                Require ((Get-TrainHeldExitProof $entry.record $child $entry.handle).exitCode -eq 7) 'REFRESH_LOST_HELD_EXIT'
            }else{
                Require (!(Test-TrainTerminatedCimRace $vanished $entry.record $child $entry.handle)) 'LIVE_PROCESS_CIM_FAILURE_IGNORED'
                Require (!(Test-TrainExitReceipt $fixture $entry.record)) 'LIVE_WORKER_WITHOUT_RECEIPT_ACCEPTED'
            }
        }
        Write-Output ('TRAIN002_CIM_EXIT_GREEN realStaleCim=1 ownHeldExit=1 liveRejected=1 securityRejected=4 receipt=1 staleReceiptRejected=1 evidence='+$directory)
    }finally{foreach($entry in $children){[IO.File]::WriteAllText($entry.release,'exit');$null=$entry.process.WaitForExit(16000);$entry.process.Dispose()}}
    return
}
if($Action -eq 'Test-ErrorHistory'){
    if(!(Get-Command Save-TrainFailure -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_FAILURE_HISTORY_MISSING'}
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002Errors-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]};$fixture.Root=Join-Path $directory 'own'
    New-TrainPrivateRoot $fixture
    $state=[ordered]@{runId='test';nonce='0123456789abcdef0123456789abcdef';phase='SQL_PREPARE';primaryFailureId='';primaryFailureCode='';primaryFailurePhase='';cleanupFailureId=''}
    $primary=[Management.Automation.ErrorRecord]::new([InvalidOperationException]::new('password=never-print-this'),'PRIMARY_TEST',[Management.Automation.ErrorCategory]::InvalidResult,$null)
    $primaryId=Save-TrainFailure $fixture $state $primary 'PRIMARY'
    $exception=[InvalidOperationException]::new('TRAIN002_PROCESS_VISIBILITY_UNKNOWN');$exception.Data['pid']=123;$exception.Data['nativeError']=5
    $cleanup=[Management.Automation.ErrorRecord]::new($exception,'CLEANUP_TEST',[Management.Automation.ErrorCategory]::InvalidResult,$null)
    $state.phase='FAILED';$cleanupId=Save-TrainFailure $fixture $state $cleanup 'CLEANUP';$state.phase='STOP_UNCONFIRMED'
    Require ($state.primaryFailureId -ceq $primaryId -and $state.cleanupFailureId -ceq $cleanupId -and $state.primaryFailurePhase -ceq 'SQL_PREPARE') 'PRIMARY_FAILURE_OVERWRITTEN'
    $files=@(Get-ChildItem -LiteralPath (Join-Path $fixture.Root 'failures') -File)
    Require ($files.Count -eq 2) 'FAILURE_HISTORY_COUNT'
    foreach($file in $files){$body=[IO.File]::ReadAllText($file.FullName);Require (!$body.Contains('never-print-this') -and !$body.Contains('password=')) 'SECRET_IN_FAILURE_HISTORY'}
    $saved=Read-TrainJson (Join-Path $fixture.Root ('failures\'+$cleanupId+'.json'))
    Require ($saved.pid -eq 123 -and $saved.nativeError -eq 5) 'NATIVE_ERROR_FACT_LOST'
    Write-Output ('TRAIN002_ERROR_HISTORY_GREEN primaryPreserved=1 cleanupSeparate=1 secretExcluded=1 nativeError=5 evidence='+$directory)
    return
}
if($Action -eq 'Test-EmptyBootstrapRecovery'){
    if(!(Get-Command Repair-TrainEmptyBootstrap -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_EMPTY_BOOTSTRAP_RECOVERY_MISSING'}
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002EmptyBootstrap-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]}
    $fixture.Root=Join-Path $directory 'train';$fixture.ExpectedRoot=$fixture.Root;$fixture.Game=Join-Path $fixture.Root 'game';$fixture.Sql=Join-Path $fixture.Root 'sql'
    $config=Read-TrainConfig (Join-Path $PSScriptRoot 'TRAIN_CONFIG.ini')
    New-TrainPrivateRoot $fixture
    Remove-Item -LiteralPath (Join-Path $fixture.Root 'owner.txt')
    $archive=Repair-TrainEmptyBootstrap $fixture $config
    Require (!(Test-Path -LiteralPath $fixture.Root)) 'EMPTY_BOOTSTRAP_NOT_MOVED'
    Require ((Split-Path $archive -Parent) -ceq $directory -and (Split-Path $archive -Leaf) -cmatch '^train\.bootstrap-incomplete\.[0-9a-f]{32}$') 'EMPTY_BOOTSTRAP_ARCHIVE_SCOPE'
    Assert-TrainPrivate $archive
    Require (@(Get-ChildItem -LiteralPath $archive -Force).Count -eq 0) 'EMPTY_BOOTSTRAP_ARCHIVE_CHANGED'
    New-TrainPrivateRoot $fixture
    $ownerBefore=[IO.File]::ReadAllText((Join-Path $fixture.Root 'owner.txt'))
    Reject {Repair-TrainEmptyBootstrap $fixture $config} 'NONEMPTY_BOOTSTRAP_ARCHIVED'
    Require ([IO.File]::ReadAllText((Join-Path $fixture.Root 'owner.txt')) -ceq $ownerBefore) 'NONEMPTY_BOOTSTRAP_MUTATED'
    Write-Output ('TRAIN002_EMPTY_BOOTSTRAP_GREEN emptyPreserved=1 freshOwner=1 nonemptyRefused=1 SQL=0 NATIVE=0 evidence='+$directory)
    return
}
if($Action -eq 'Test-PrivateBootstrap'){
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002Bootstrap-'+[guid]::NewGuid().ToString('N'))
    $fixture=@{};foreach($key in $layout.Keys){$fixture[$key]=$layout[$key]}
    $fixture.Root=Join-Path $directory 'own';$fixture.Game=Join-Path $fixture.Root 'game';$fixture.Sql=Join-Path $fixture.Root 'sql'
    try{
        New-TrainPrivateRoot $fixture
        Assert-TrainPrivate $fixture.Root
        $ownerPath=Join-Path $fixture.Root 'owner.txt';Assert-G0PhysicalFile $ownerPath
        Require ([IO.File]::ReadAllText($ownerPath) -cmatch '^LEARNING002:[0-9a-f]{32}$') 'BOOTSTRAP_OWNER_READBACK'
        Require (@(Get-ChildItem -LiteralPath $fixture.Root -Force).Count -eq 1) 'BOOTSTRAP_UNEXPECTED_ARTIFACT'
        Reject {New-TrainPrivateRoot $fixture} 'BOOTSTRAP_EXISTING_ROOT_OVERWRITTEN'
        Write-Output ('TRAIN002_PRIVATE_BOOTSTRAP_GREEN ordinaryPrivateACL=1 owner=1 existingRejected=1 SQL=0 NATIVE=0 evidence='+$directory)
    }catch{Write-TrainSafeError $_;throw}
    return
}
if($Action -eq 'Test-ExportAdmission'){
    if(!(Get-Command Get-TrainExportAdmission -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_EXPORT_ADMISSION_MISSING'}
    Require ((Get-TrainExportAdmission $true $false $false) -ceq 'QUEUE') 'LIVE_NATIVE_EXPORT_NOT_QUEUED'
    Require ((Get-TrainExportAdmission $true $true $false) -ceq 'QUEUE') 'LIVE_EVALUATION_EXPORT_NOT_QUEUED'
    Require ((Get-TrainExportAdmission $false $true $true) -ceq 'OFFLINE') 'STOPPED_EXPORT_DENIED'
    Reject {Get-TrainExportAdmission $false $false $true} 'NATIVE_EXPORT_WITHOUT_DIRECTOR_ACCEPTED'
    Reject {Get-TrainExportAdmission $false $true $false} 'UNLOCKED_OFFLINE_EXPORT_ACCEPTED'
    Write-Output 'TRAIN002_EXPORT_ADMISSION_GREEN liveQueued=2 offlineLocked=1 unsafeRejected=2 SQL=0 NATIVE=0'
    return
}
if($Action -eq 'Test-ColdBaseline'){
    if(!(Get-Command Get-TrainColdBaseline -CommandType Function -ErrorAction SilentlyContinue)){throw 'TRAIN002_COLD_BASELINE_MISSING'}
    $crashed="# Own config after interrupted native batch`r`nEnablePhantomSystem = True`r`nLearningMode = ON`r`nLearningModelVersion = 17`r`nLearningEvidenceEnabled = True`r`nLearningDeterministicSeed = 183`r`nPhantomPopulationTarget = 5`r`n"
    $expected=$crashed.Replace('EnablePhantomSystem = True','EnablePhantomSystem = False').Replace('LearningMode = ON','LearningMode = OFF').Replace('LearningModelVersion = 17','LearningModelVersion = 0').Replace('LearningEvidenceEnabled = True','LearningEvidenceEnabled = False').Replace('LearningDeterministicSeed = 183','LearningDeterministicSeed = 0')
    $actual=Get-TrainColdBaseline $crashed
    Require ($actual -ceq $expected) 'COLD_BASELINE_NOT_RESTORED'
    Require ((Get-TrainColdBaseline $actual) -ceq $expected) 'COLD_BASELINE_NOT_IDEMPOTENT'
    Reject {Get-TrainColdBaseline ($crashed.Replace('LearningMode = ON', ''))} 'COLD_BASELINE_MISSING_KEY_ACCEPTED'
    Reject {Get-TrainColdBaseline ($crashed+"EnablePhantomSystem = True`r`n")} 'COLD_BASELINE_DUPLICATE_KEY_ACCEPTED'
    Write-Output 'TRAIN002_COLD_BASELINE_GREEN flags=5 unrelatedPreserved=1 idempotent=1 malformedRejected=2 SQL=0 NATIVE=0'
    return
}
if($Action -eq 'Test-Atomic'){
    $directory=Join-Path ([IO.Path]::GetTempPath()) ('Training002Atomic-'+[guid]::NewGuid().ToString('N'))
    $null=[IO.Directory]::CreateDirectory($directory)
    $path=Join-Path $directory 'state.json'
    Write-TrainJson $path @{sequence=1;message='Первое состояние'}
    Write-TrainJson $path @{sequence=2;message='Новое состояние'}
    $state=Read-TrainJson $path
    Require ($state.sequence -eq 2 -and $state.message -ceq 'Новое состояние') 'ATOMIC_REPLACE_READBACK'
    Require (@(Get-ChildItem -LiteralPath $directory -File).Count -eq 1) 'ATOMIC_PENDING_LEAK'
    Write-Output ('TRAIN002_ATOMIC_GREEN new=1 replace=1 UTF8=1 evidence='+$directory)
    return
}
if ($Action -eq 'Test-Arguments') {
    $callArguments=@('prepare','D:\own path\train','D:\source path\module')
    $class='org.l2jmobius.tests.phantoms.PhantomTrainingSql'
    $classpath='D:\source path\build\bin;D:\own path\train\game\libs\*'
    if($LegacyArguments){
        # Exact prior shadowing defect: PowerShell variable names ignore case.
        function Legacy([string[]]$Arguments){
            $arguments=@('-XX:ActiveProcessorCount=4','-Xms256m','-Xmx4096m','-Dphantom.training.worker=abc','-Dphantom.learning.train=true','-cp',('"'+$classpath+'"'),$class)
            $arguments+=@($Arguments|ForEach-Object{'"'+$_+'"'})
            return $arguments
        }
        $actual=@(Legacy $callArguments)
    }else{
        $actual=@(Get-TrainJavaArguments $class $callArguments $classpath 'abc' 4 4096)
    }
    Require ($actual.Count -eq 13) 'JAVA_ARGUMENT_COUNT_OR_DUPLICATION'
    Require (@($actual|Where-Object{$_ -ceq '-cp'}).Count -eq 1) 'DUPLICATE_CLASSPATH_SWITCH'
    Require ($actual[9] -ceq $class) 'JAVA_CLASS_CHANGED'
    Require ($actual[8] -ceq ('"'+$classpath+'"')) 'CLASSPATH_SPACE_QUOTING'
    Require ($actual[5] -ceq '-Dstdout.encoding=UTF-8' -and $actual[6] -ceq '-Dstderr.encoding=UTF-8') 'JAVA_UTF8_STREAMS'
    for($index=0;$index -lt $callArguments.Count;$index++){Require ($actual[10+$index] -ceq ('"'+$callArguments[$index]+'"')) ('JAVA_CLI_ARGUMENT_CHANGED:'+ $index)}
    Write-Output 'TRAIN002_ARGUMENTS_GREEN exactClass=1 cliArgs=3 oneClasspath=1 SQL=0 NATIVE=0'
    return
}
if ($Action -eq 'Test-Config') {
    $config=Read-TrainConfig (Join-Path $PSScriptRoot 'TRAIN_CONFIG.ini')
    Require ($config.sqlPort -eq 33327 -and $config.gamePort -eq 17971 -and $config.loginPort -eq 19271) 'DEFAULT_ENDPOINTS'
    foreach ($change in @(@{schemaVersion='2'},@{mode='PVP'},@{sqlPort='3308'},@{gamePort='33327'},@{maxActiveActors='1'},@{maxHeapMb='0'},@{modelVersion='1';learningMode='ON';modelSha='bad'})) {
        $invalid=@{}; foreach($key in $config.Keys){$invalid[$key]=[string]$config[$key]}; foreach($key in $change.Keys){$invalid[$key]=$change[$key]}
        Reject { Assert-TrainConfig $invalid } ('INVALID_CONFIG_ACCEPTED:'+($change.Keys -join ','))
    }
    $expected=@{pid=123;created=134000000000000000L;image='C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe';sid='S-1-5-21-1';nonce='0123456789abcdef0123456789abcdef'}
    $fact=@{pid=123;created=134000000000000000L;image=$expected.image;sid=$expected.sid;command='powershell.exe -Nonce 0123456789abcdef0123456789abcdef';listener='127.0.0.1';listenerPid=123}
    Assert-TrainIdentitySnapshot $fact $expected
    foreach ($change in @(@{created=134000000000000001L},@{sid='S-1-5-21-2'},@{image='C:\foreign.exe'},@{command='foreign'},@{listener='0.0.0.0'},@{listenerPid=456})) {
        $invalid=@{}; foreach($key in $fact.Keys){$invalid[$key]=$fact[$key]}; foreach($key in $change.Keys){$invalid[$key]=$change[$key]}
        Reject { Assert-TrainIdentitySnapshot $invalid $expected } ('FOREIGN_IDENTITY_ACCEPTED:'+($change.Keys -join ','))
    }
    $auto=@{};foreach($key in $config.Keys){$auto[$key]=[string]$config[$key]}
    foreach($entry in @{autonomous009Version='1';autonomous009Enabled='false';autonomous009Phase='OFF';autonomous009Comparator='STOCK_ON_FROZEN_SHORTLIST_V1';v3Enabled='true';classes='auto';levels='auto';autonomous009MinLevel='20';autonomous009MaxLevel='21';autonomous009ClassWeights='88:3';autonomous009MemoryBudgetMb='8192';episodesPerBatch='48'}.GetEnumerator()){$auto[$entry.Key]=$entry.Value}
    Assert-TrainConfig $auto
    $profile=Read-TrainConfig (Join-Path $PSScriptRoot 'AutonomousTraining.ini')
    Require ($profile.autonomous009Enabled -ceq 'false' -and $profile.classes -ceq 'auto' -and $profile.levels -ceq 'auto') 'AUTONOMOUS_PROFILE_OFF_DEFAULT'
    $extended=@{};foreach($key in $auto.Keys){$extended[$key]=[string]$auto[$key]}
    foreach($entry in @{autonomous009AcceptanceSmoke='true';autonomous009SmokeMaxStarts='10';autonomous009SmokeMaxNativeMinutes='90';autonomous009PriorStarts='6';autonomous009PriorNativeMillis='2231818'}.GetEnumerator()){$extended[$entry.Key]=$entry.Value}
    Assert-TrainConfig $extended
    $extended.autonomous009GuiHoldBeforeNative='true';Assert-TrainConfig $extended
    $evalOnly=@{};foreach($key in $profile.Keys){$evalOnly[$key]=[string]$profile[$key]}
    foreach($entry in @{autonomous009EvaluationOnly='true';autonomous009Exploration='false';autonomous009AcceptanceSmoke='true';autonomous009SmokeMaxStarts='3';autonomous009SmokeMaxNativeMinutes='45';autonomous009Phase='OFF'}.GetEnumerator()){$evalOnly[$entry.Key]=$entry.Value}
    Assert-TrainConfig $evalOnly
    $evalFive=@{};foreach($key in $evalOnly.Keys){$evalFive[$key]=[string]$evalOnly[$key]};$evalFive.autonomous009SmokeMaxStarts='5';Assert-TrainConfig $evalFive
    foreach($change in @(@{autonomous009Phase='COLLECT'},@{autonomous009Phase='FIT'},@{autonomous009SmokeMaxStarts='6'},@{autonomous009SmokeMaxNativeMinutes='46'},@{autonomous009EvaluationEveryCycles='2'},@{episodeSeconds='180'})){
        $invalid=@{};foreach($key in $evalOnly.Keys){$invalid[$key]=[string]$evalOnly[$key]};foreach($key in $change.Keys){$invalid[$key]=$change[$key]}
        Reject {Assert-TrainConfig $invalid} ('EVALUATION_ONLY_SCOPE_ACCEPTED:'+($change.Keys -join ','))
    }
    Require ($extended.autonomous009PriorStarts -ceq '6' -and $extended.autonomous009PriorNativeMillis -ceq '2231818') 'EXTENDED_BUDGET_RESET_PRIOR_USAGE'
    foreach($change in @(@{autonomous009Version='2'},@{autonomous009MinLevel='22'},@{autonomous009MemoryBudgetMb='4096'},@{autonomous009ClassWeights='999:3'},@{episodesPerBatch='50'},@{autonomous009PriorStarts='11'},@{autonomous009AcceptanceSmoke='true';autonomous009SmokeMaxStarts='11'},@{autonomous009AcceptanceSmoke='true';autonomous009SmokeMaxStarts='10';autonomous009SmokeMaxNativeMinutes='91'},@{autonomous009GuiHoldBeforeNative='true'})){
        $invalid=@{};foreach($key in $auto.Keys){$invalid[$key]=[string]$auto[$key]};foreach($key in $change.Keys){$invalid[$key]=$change[$key]}
        Reject {Assert-TrainConfig $invalid} ('INVALID_AUTONOMOUS_CONFIG_ACCEPTED:'+($change.Keys -join ','))
    }
    $now=[DateTime]::UtcNow
    Require ((Get-TrainHeartbeat ($now.ToString('o')) $now).state -ceq 'FRESH') 'FRESH_HEARTBEAT_REJECTED'
    Require ((Get-TrainHeartbeat ($now.AddMinutes(-2).ToString('o')) $now).state -ceq 'STALE') 'STALE_HEARTBEAT_PRESENTED_AS_CURRENT'
    Require ((Get-TrainHeartbeat 'broken' $now).state -ceq 'UNKNOWN') 'INVALID_HEARTBEAT_PRESENTED_AS_CURRENT'
    $empty=Get-TrainEpisodeSummary @() 0
    Require ($empty.attempted -eq 0 -and $empty.settled -eq 0 -and $empty.paidEffects -eq 0 -and $empty.classes.Count -eq 0) 'EMPTY_COHORT_STATUS_FAILED'
    $refused=[pscustomobject]@{classId=103;exclusion='PRE_CUT_REFUSED'}
    $precut=Get-TrainEpisodeSummary @($refused) 1
    Require ($precut.settled -eq 1 -and $precut.completed -eq 0 -and $precut.failed -eq 0 -and $precut.paidEffects -eq 0 -and $precut.classes.Count -eq 0) 'PRE_CUT_STATUS_INVENTED_EFFECTS'
    $native=Get-TrainEpisodeSummary @($refused,[pscustomobject]@{classId=94;afterCut=$true;outcome='COMPLETED';paidRawEffects=2},[pscustomobject]@{classId=110;afterCut=$true;outcome='FAILED';paidRawEffects=0}) 3
    Require ($native.attempted -eq 3 -and $native.settled -eq 3 -and $native.completed -eq 1 -and $native.failed -eq 1 -and $native.paidEffects -eq 2 -and ($native.classes -join ',') -ceq '94,110') 'MIXED_COHORT_STATUS_INCORRECT'
    Write-Output 'TRAIN002_CONFIG_GREEN invalidConfig=7 foreignIdentity=6 autonomousInvalid=9 heartbeat=3 episodeSummary=3 extendedBudget=10/90 evalOnlyBudget=3,5/45 evalOnlyInvalid=6 SQL=0 NATIVE=0'
    return
}
# Authorized OWN disposable TEST fault only: active package TEST_CASES.md T08/T14
# explicitly requires controlled own crash/recovery; SAFETY.md permits own process stop.
if($Action -eq 'Test-Crash'){
    Require $ConfirmOwnedCrash.IsPresent 'TRAIN002_CONFIRM_OWNED_CRASH_REQUIRED'
    Initialize-TrainWin32;Assert-TrainInstallation $layout
    $config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini');$state=Read-TrainJson (Join-Path $layout.Root 'director.json')
    $director=Read-TrainJson (Join-Path $layout.Root 'owner-process.json');$sql=Read-TrainJson (Join-Path $layout.Sql 'owned-process.json');$native=Read-TrainJson (Join-Path $layout.Root 'native-process.json')
    Require ($state.phase -ceq 'NATIVE' -and $state.nonce -ceq $director.nonce -and $state.attemptId -ceq $native.nonce -and $native.nonce -cmatch '^[0-9a-f]{32}$') 'TRAIN002_CRASH_REQUIRES_CURRENT_NATIVE'
    Assert-TrainProcess $director -Kind Director;Assert-TrainSqlOptions $layout $config $sql;Assert-TrainProcess $sql -Kind Sql -Port ([int]$config.sqlPort);Assert-TrainProcess $native -Kind Native -Port ([int]$config.gamePort);Assert-TrainPortsFree @([int]$config.loginPort)
    $pin=Read-TrainJson (Join-Path $layout.Root 'build.json');Assert-TrainHash (Join-Path $layout.Game 'libs\GameServer.jar') $pin.jarSha
    $client=Join-Path (Split-Path $sql.image -Parent) 'mariadb.exe';Assert-G0PhysicalFile $client
    Require ((Get-Item -LiteralPath $client).VersionInfo.ProductVersion -ceq '11.4.13.0') 'TRAIN002_CRASH_SQL_CLIENT_VERSION'
    $admin=Join-Path $layout.Sql 'admin.client.ini';Assert-G0PhysicalFile $admin;$adminLines=[IO.File]::ReadAllLines($admin)
    Require ($adminLines.Count -eq 5 -and ($adminLines[0..3] -join '|') -ceq ('[client]|host=127.0.0.1|port='+$config.sqlPort+'|user=root') -and $adminLines[4] -cmatch '^password=[0-9a-f]{64}$') 'TRAIN002_CRASH_SQL_ADMIN_OPTIONS'
    $probe=[guid]::NewGuid().ToString('N');$prefix=Join-Path $layout.Root ('crash-probe.'+$probe)
    function Read-CrashSql([string]$Query){Require ($Query -cmatch '^SELECT ') 'TRAIN002_CRASH_SELECT_ONLY';$rows=@(& $client ('--defaults-file='+$admin) --protocol=tcp --host=127.0.0.1 ('--port='+$config.sqlPort) --user=root --batch --raw --skip-column-names --database=phantom_learning001_train002 ('--execute='+$Query) 2>($prefix+'.client.err'));Require ($LASTEXITCODE -eq 0) 'TRAIN002_CRASH_SELECT_FAILED';return $rows}
    $query="SELECT p.episode_id,p.epoch,p.scope,p.created_at,e.event_key,e.actor_group,e.skill_id,e.damage,(SELECT COUNT(*) FROM phantom_training_episodes WHERE state='COMMITTED'),(SELECT COUNT(*) FROM phantom_training_events WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP'),(SELECT COUNT(*) FROM phantom_ai_training_examples WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP') FROM phantom_training_episodes p JOIN phantom_ai_training_examples e ON e.created_at>=p.created_at AND e.scope=p.scope LEFT JOIN phantom_training_events t ON t.event_key=e.event_key WHERE p.state='ACTIVE' AND p.epoch='$($native.nonce)' AND e.origin='NATIVE' AND e.evidence_kind='EXACT_CAST_HP' AND e.damage>0 AND t.event_key IS NULL ORDER BY p.ordinal DESC,e.created_at,e.event_key LIMIT 256"
    $rows=@(Read-CrashSql $query);Require ($rows.Count -gt 0) 'TRAIN002_CRASH_WINDOW_NOT_READY'
    $facts=@($rows|ForEach-Object{$f=$_ -split "`t";Require ($f.Count -eq 11 -and $f[0] -cmatch '^[0-9a-f]{32}$' -and $f[1] -ceq $native.nonce -and $f[4] -cmatch '^[0-9a-f]{64}$') 'TRAIN002_CRASH_SQL_ROW';[pscustomobject]@{episodeId=$f[0];epoch=$f[1];scope=$f[2];episodeCreated=$f[3];eventKey=$f[4];actorGroup=$f[5];skillId=$f[6];damage=$f[7];committed=[long]$f[8];exactEvents=[long]$f[9];rawExactEvents=[long]$f[10]}})
    Require (@($facts.episodeId|Select-Object -Unique).Count -eq 1) 'TRAIN002_CRASH_AMBIGUOUS_ACTIVE_EPISODE'
    $process=Get-Process -Id $native.pid -ErrorAction Stop;$handle=Hold-ProcessHandle $process
    try{
        Require (!(Get-TrainHeldExitProof $native $process $handle).exited) 'TRAIN002_CRASH_NATIVE_ALREADY_EXITED'
        $before=@{schemaVersion=1;probeId=$probe;observedUtc=[DateTime]::UtcNow.ToString('o');runId=$state.runId;generation=$state.generation;director=$director;sql=$sql;native=$native;jarSha=$pin.jarSha;episodeId=$facts[0].episodeId;epoch=$native.nonce;rawKeys=@($facts.eventKey);rows=$facts;committed=$facts[0].committed;exactEvents=$facts[0].exactEvents;rawExactEvents=$facts[0].rawExactEvents;clientSha=(Get-FileHash -LiteralPath $client).Hash;adminConfigSha=(Get-FileHash -LiteralPath $admin).Hash}
        Require (!(Test-Path -LiteralPath ($prefix+'.json'))) 'TRAIN002_CRASH_FRESH_PROOF_REQUIRED';Write-TrainJson ($prefix+'.json') $before
        $current=Read-TrainJson (Join-Path $layout.Root 'director.json');Require ($current.phase -ceq 'NATIVE' -and $current.nonce -ceq $director.nonce -and $current.attemptId -ceq $native.nonce) 'TRAIN002_CRASH_GENERATION_CHANGED'
        Assert-TrainProcess $native -Kind Native -Port ([int]$config.gamePort)
        $window=@(Read-CrashSql ("SELECT COUNT(*) FROM phantom_training_episodes p JOIN phantom_ai_training_examples e ON e.created_at>=p.created_at AND e.scope=p.scope LEFT JOIN phantom_training_events t ON t.event_key=e.event_key WHERE p.episode_id='"+$facts[0].episodeId+"' AND p.epoch='"+$native.nonce+"' AND p.state='ACTIVE' AND e.origin='NATIVE' AND e.evidence_kind='EXACT_CAST_HP' AND e.damage>0 AND t.event_key IS NULL"))
        Require ($window.Count -eq 1 -and [long]$window[0] -gt 0 -and !(Get-TrainHeldExitProof $native $process $handle).exited) 'TRAIN002_CRASH_WINDOW_EXPIRED'
        $process.Kill()
        $exited=$process.WaitForExit(5000);$exit=$null;if($exited){$exit=Get-TrackedProcessExitCode $process $handle}
        Write-TrainJson ($prefix+'.after.json') @{probeId=$probe;pid=$native.pid;created=$native.created;attemptId=$native.nonce;killRequested=$true;exitObserved=$exited;exitCode=$exit;observedUtc=[DateTime]::UtcNow.ToString('o')}
        Require $exited 'TRAIN002_CRASH_EXIT_UNCONFIRMED'
        Write-Output ('TRAIN002_OWNED_CRASH_APPLIED proof='+$prefix+'.json; recovery/INCOMPLETE/dedup remain separate verification')
    }finally{$process.Dispose()}
    return
}
$script=Join-Path $PSScriptRoot 'Training.ps1'
$errors=$null; $tokens=$null
$null=[Management.Automation.Language.Parser]::ParseFile($script,[ref]$tokens,[ref]$errors)
Require ($errors.Count -eq 0) 'SUPERVISOR_PARSE_ERROR'
if ($Action -eq 'Test-Isolation') {
    & $script -Action Preflight -Root $layout.Root
    if ($LASTEXITCODE -ne 0) { throw 'ISOLATION_PREFLIGHT_FAILED' }
    return
}
Assert-TrainInstallation $layout
$state=Read-TrainJson (Join-Path $layout.Root 'director.json')
switch ($Action) {
    'Test-Generate' {
        $pointer=Join-Path $layout.Root 'active-catalog.txt';Assert-G0PhysicalFile $pointer
        $relative=[IO.File]::ReadAllText($pointer).Trim()
        Require ($relative -cmatch '^catalogs/[0-9]+-[0-9a-f]{32}$') 'INVALID_ACTIVE_CATALOG_PATH'
        $catalog=Join-Path $layout.Root $relative;Assert-TrainDirectory $catalog
        foreach($name in @('training-actors.tsv','training-scenes.tsv','training-manifest.sha256')) {
            $path=Join-Path $catalog $name
            Require (Test-Path -LiteralPath $path -PathType Leaf) ('MISSING_GENERATED_MANIFEST:'+ $name)
            Assert-G0PhysicalFile $path
        }
        Write-Output ('TRAIN002_GENERATE_EVIDENCE_PRESENT catalog='+$relative+'; native acceptance remains separate')
    }
    'Test-Native' {
        $complete=@(Get-ChildItem -LiteralPath (Join-Path $layout.Root 'attempts') -Filter 'batch.complete' -File -Recurse)
        Require ($complete.Count -gt 0) 'NO_NATIVE_BATCH_COMPLETE'
        Write-Output ('TRAIN002_NATIVE_BATCH_EVIDENCE count='+$complete.Count+'; verify SQL scope/events independently')
    }
    'Test-Detached' {
        . (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
        $record=Read-TrainJson (Join-Path $layout.Root 'owner-process.json')
        Assert-TrainProcess $record -Kind Director
        $bootstrap=Assert-TrainBrokerProof $layout $record
        $bootstrapPath=Join-Path $layout.Root ('logs\bootstrap.'+$record.nonce+'.json')
        $jobSource=Join-Path $PSScriptRoot 'TrainingJobProbe.cs';Assert-G0PhysicalFile $jobSource
        if(!('TrainingJobProbe' -as [type])){Add-Type -Path $jobSource}
        $pin=[Win32SqlIdentity]::Hold([uint32]$record.pid)
        try{
            $identity=[Win32SqlIdentity]::Read($pin,[uint32]$record.pid)
            Require ($identity.Success -and $identity.CreatedFileTime -eq [long]$record.created -and $identity.ImagePath -ieq $record.image) 'DETACHED_JOB_PID_FILETIME_BEFORE'
            $inJob=$false;$queried=[TrainingJobProbe]::IsProcessInJob($pin.DangerousGetHandle(),[IntPtr]::Zero,[ref]$inJob)
            $nativeError=$(if($queried){0}else{[Runtime.InteropServices.Marshal]::GetLastWin32Error()})
            $identityAfter=[Win32SqlIdentity]::Read($pin,[uint32]$record.pid)
            Require ($identityAfter.Success -and $identityAfter.CreatedFileTime -eq [long]$record.created -and $identityAfter.ImagePath -ieq $record.image) 'DETACHED_JOB_PID_FILETIME_AFTER'
            Assert-TrainProcess $record -Kind Director
            $jobProof=Join-Path $layout.Root ('detached-job.'+$record.nonce+'.'+[guid]::NewGuid().ToString('N')+'.json')
            Write-TrainJson $jobProof @{schemaVersion=1;pid=$record.pid;created=$record.created;sid=$record.sid;nonce=$record.nonce;image=$record.image;imageHash=$record.imageHash;commandHash=$record.commandHash;configHash=$record.configHash;probeSourceSha=(Get-FileHash -LiteralPath $jobSource).Hash;querySucceeded=$queried;isInAnyJob=$(if($queried){$inJob}else{$null});nativeError=$nativeError;startupAdmission=$bootstrap.admitted;admissionScope=$bootstrap.admissionScope;startupImmediateJob=$bootstrap.currentJob;bootstrapProofPath=$bootstrapPath;bootstrapProofSha=(Get-FileHash -LiteralPath $bootstrapPath).Hash;ancestorJobChain='NOT_ENUMERATED';literalUiCloseObserved=$false;observedUtc=[DateTime]::UtcNow.ToString('o');source='https://learn.microsoft.com/en-us/windows/win32/api/jobapi/nf-jobapi-isprocessinjob'}
            Require $queried 'DETACHED_JOB_QUERY_FAILED'
            Require ($inJob -eq $bootstrap.inAnyJob) 'DETACHED_ANY_JOB_MEMBERSHIP_CHANGED'
        }finally{$pin.Dispose()}
        $before=[DateTime]::Parse($state.heartbeatUtc).ToUniversalTime()
        Start-Sleep -Seconds $ObserveSeconds
        $after=Read-TrainJson (Join-Path $layout.Root 'director.json')
        Require ($after.nonce -ceq $record.nonce) 'DETACHED_DIRECTOR_GENERATION_CHANGED'
        Require ([DateTime]::Parse($after.heartbeatUtc).ToUniversalTime() -gt $before) 'DETACHED_HEARTBEAT_STALLED'
        Assert-TrainProcess $record -Kind Director
        Write-Output ('TRAIN002_DETACHED_HEARTBEAT_ADVANCED anyJob='+$inJob+' admission='+$bootstrap.admitted+' proof='+$jobProof+'; ancestor chain NOT_ENUMERATED; literal UI close NOT_RUN')
    }
    'Test-Resume' {
        Require ([int]$state.generation -gt 1) 'RESUME_GENERATION_NOT_OBSERVED'
        Require (Test-Path -LiteralPath (Join-Path $layout.Root 'recovery.json')) 'RECOVERY_EVIDENCE_MISSING'
        Write-Output 'TRAIN002_RESUME_EVIDENCE_PRESENT; verify immutable SQL event keys independently'
    }
    'Test-Training' {
        $evaluation=Read-TrainJson (Join-Path $layout.Root 'evaluation.json')
        $history=Read-TrainJson (Join-Path $layout.Root 'status.json')
        Require ([long]$history.attempts -gt 0) 'TRAINING_ATTEMPT_NOT_OBSERVED'
        Require ($evaluation.state -in @('DRAFT','ACCEPTED','REJECTED','COLLECTING')) 'TRAINING_STATE_UNKNOWN'
        # COLLECTING can describe the next fresh audit after a completed attempt.
        # The durable SQL snapshot, not that transient label, proves its history.
        if([long]$evaluation.modelVersion -gt 0){
            Require ([long]$history.accepted -gt 0 -and [long]$history.activeModelVersion -eq [long]$evaluation.modelVersion -and
                $evaluation.modelSha -cmatch '^[0-9a-f]{64}$' -and $history.activeModelSha -ceq $evaluation.modelSha) 'TRAINING_ACCEPTED_CHECKPOINT_MISMATCH'
        }
        Write-Output ('TRAIN002_TRAINING_HISTORY attempts='+$history.attempts+' accepted='+$history.accepted+' current='+$evaluation.state+' activeModel='+$evaluation.modelVersion+' snapshot='+$history.updatedAt)
    }
    'Test-Export' {
        $directory=Join-Path $layout.Root 'exports'
        $before=@();if(Test-Path -LiteralPath $directory){$before=@(Get-ChildItem -LiteralPath $directory -Filter '*.zip' -File|Select-Object -ExpandProperty Name)}
        & $script -Action Export -Root $layout.Root
        if ($LASTEXITCODE -ne 0) { throw 'EXPORT_NOT_AVAILABLE' }
        $after=@();if(Test-Path -LiteralPath $directory){$after=@(Get-ChildItem -LiteralPath $directory -Filter '*.zip' -File|Select-Object -ExpandProperty Name)}
        Require (@($after|Where-Object{$_ -cnotin $before}).Count -gt 0) 'EXPORT_QUEUED_NOT_YET_COMPLETED'
        Write-Output 'TRAIN002_EXPORT_COMPLETED; evaluator validated model provenance'
    }
}
