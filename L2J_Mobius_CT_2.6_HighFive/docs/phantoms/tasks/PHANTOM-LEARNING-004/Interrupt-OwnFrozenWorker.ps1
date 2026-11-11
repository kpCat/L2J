[CmdletBinding()]
param([int]$TimeoutSeconds=1800)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
. (Join-Path $module 'tools\phantom-runtime\training\TrainingOwner.ps1')
$layout=Get-TrainLayout $null
$expected=Join-Path (Split-Path $module -Parent) '.phantom-local\learning002\train'
if($layout.Root -ine $expected){throw 'LEARNING004_OWN_ROOT_REQUIRED'}
Assert-TrainInstallation $layout
Initialize-TrainWin32
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while([DateTime]::UtcNow -lt $deadline){
    $state=Read-TrainJson (Join-Path $layout.Root 'director.json')
    if($state.phase -in @('FAILED','STOPPED','STOP_UNCONFIRMED')){throw 'LEARNING004_DIRECTOR_NOT_EVALUATING'}
    if($state.phase -ne 'EVALUATING'){Start-Sleep -Seconds 1;continue}
    $attempt=Join-Path $layout.Root ('attempts\'+$state.attemptId)
    if(!(Test-Path -LiteralPath (Join-Path $attempt 'damage004.segment.json'))){Start-Sleep -Seconds 1;continue}
    if((Get-Content -LiteralPath (Join-Path $attempt 'batch.complete') -Raw) -match 'partial=true'){throw 'LEARNING004_COMPLETE_SEGMENT_REQUIRED'}
    $record=Read-TrainJson (Join-Path $layout.Root 'worker-process.json')
    if(Test-TrainProcessAbsent $record){Start-Sleep -Seconds 1;continue}
    try{Assert-TrainProcess $record -Kind Worker}catch{if($_.Exception.Message -ceq 'TRAIN002_PROCESS_UNAVAILABLE'){Start-Sleep -Seconds 1;continue};throw}
    $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
    if($cim.CommandLine -notmatch 'org\.l2jmobius\.gameserver\.phantoms\.learning\.TrainingEvaluator\s+"?train"?\s'){Start-Sleep -Seconds 1;continue}
    $facts=@(& $record.image -cp $layout.ClassPath org.l2jmobius.gameserver.phantoms.learning.Learning004DurabilityChecks inspect $layout.Root)
    if($LASTEXITCODE -ne 0){throw 'LEARNING004_INSPECT_FAILED'}
    $frozen=@($facts|Where-Object{$_ -match '^LEARNING004_FROZEN run=[a-f0-9]{32} datasetSha=[a-f0-9]{64}$'})
    if($frozen.Count -ne 1){Start-Sleep -Seconds 1;continue}
    $config=Read-TrainConfig (Join-Path $layout.Root 'TRAIN_CONFIG.ini')
    Assert-TrainPortsFree @([int]$config.gamePort,[int]$config.loginPort)
    $native=Read-TrainJson (Join-Path $layout.Root 'native-process.json')
    if(!(Test-TrainProcessAbsent $native)){throw 'LEARNING004_NATIVE_STILL_ACTIVE'}
    $current=Read-TrainJson (Join-Path $layout.Root 'worker-process.json')
    if($current.pid -ne $record.pid -or $current.nonce -cne $record.nonce -or [long]$current.created -ne [long]$record.created){throw 'LEARNING004_WORKER_CHANGED'}
    $process=Get-Process -Id $record.pid
    try{
        $held=Hold-ProcessHandle $process
        Assert-TrainProcess $record -Kind Worker
        if($process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$record.created){throw 'LEARNING004_WORKER_CREATION_CHANGED'}
        Stop-Process -InputObject $process -ErrorAction Stop
        if(!$process.WaitForExit(10000)){throw 'LEARNING004_WORKER_EXIT_NOT_OBSERVED'}
        $exit=Get-TrackedProcessExitCode $process $held
        $receipt=@{schemaVersion=4;proof='CONTROLLED_OWN_FROZEN_TRAIN_WORKER_INTERRUPT';pid=$record.pid;created=$record.created;nonce=$record.nonce;imageSha=$record.imageHash;attemptId=$state.attemptId;frozen=$frozen[0];before=$facts;exitCode=$exit;nativeAbsent=$true;finishedUtc=[DateTime]::UtcNow.ToString('o')}
        Write-TrainJson (Join-Path $layout.Root 'controlled-interrupt004.json') $receipt
        Write-Host ('LEARNING004_CONTROLLED_INTERRUPT_PASS pid='+$record.pid+' '+$frozen[0]+' exit='+$exit)
        exit 0
    }finally{$process.Dispose()}
}
throw 'LEARNING004_FROZEN_WORKER_NOT_OBSERVED_WITHIN_TIMEOUT'
