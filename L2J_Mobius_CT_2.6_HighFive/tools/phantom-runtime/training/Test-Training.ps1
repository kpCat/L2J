[CmdletBinding()]
param(
    [ValidateSet('Test-Cycle013','Test-Config','Test-Arguments','Test-Atomic','Test-ColdBaseline','Test-ExportAdmission','Test-PrivateBootstrap','Test-EmptyBootstrapRecovery','Test-ErrorHistory','Test-CimExitRace','Test-AdaptiveIdle','Test-StartupHandshake','Test-JobLifetime','Test-Broker','Test-Crash','Test-Isolation','Test-Generate','Test-Native','Test-Detached','Test-Resume','Test-Training','Test-Export','Test-GuiOwner')][string]$Action='Test-Config',
    [string]$Root,
    [switch]$Headless013,
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
if($Headless013){
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Training.ps1'),[ref]$tokens,[ref]$errors)
    Require ($errors.Count -eq 0) 'HEADLESS013_SOURCE_PARSE'
    $functions=$ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and ($n.Name -match 'Headless.*013$' -or $n.Name -cin @('Invoke-TrainJava','Test-TrainRequest','Publish-TrainState','Copy-TrainConfig013'))},$false)
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
