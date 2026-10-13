param([Parameter(Mandatory)][ValidateSet('x72','x73')][string]$Episode,[Parameter(Mandatory)][string]$Candidate,[Parameter(Mandatory)][string]$FrozenSha)
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence'
$runtime041=Join-Path $module041 ".phantom-local/contract041$Episode/runtime"
$control041=Join-Path $PSScriptRoot 'Control032.ps1'
$prefix041=$Candidate+'_'+$Episode.ToUpperInvariant()+'_041'
$probe041=Join-Path $evidence041 ($prefix041+'_EARNED')
$window041=Join-Path $evidence041 ($prefix041+'_WINDOW')
$bootstrap041=Join-Path $evidence041 ($prefix041+'_BOOTSTRAP.json')
if(Test-Path $bootstrap041){throw 'Immutable crash campaign exists.'}
@([pscustomobject]@{profileId=68;materializedAtNanos=0}) | ConvertTo-Json | Set-Content $bootstrap041 -Encoding utf8
$crashed041=$false
try {
 & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $PSScriptRoot ($prefix041+'_START.log'))
 if($LASTEXITCODE -ne 0){throw 'Exact crash-lane start failed.'}
 & (Join-Path $PSScriptRoot 'Verify-CrashGuards041.ps1') -Episode $Episode -Label ($prefix041+'_GUARDS') *> (Join-Path $PSScriptRoot ($prefix041+'_GUARDS.log'))
 if($LASTEXITCODE -ne 0 -or !(Test-Path (Join-Path $evidence041 ($prefix041+'_GUARDS/result.json')))){throw 'Actual negative crash guards not proved.'}
 & (Join-Path $PSScriptRoot 'Run-CensusPreparation041.ps1') -Episode $Episode -CohortJson $bootstrap041 -OutputRoot (Join-Path $evidence041 ($prefix041+'_PREP90')) *> (Join-Path $PSScriptRoot ($prefix041+'_PREP.log'))
 if($LASTEXITCODE -ne 0){throw 'Prospective fixed native preparation failed.'}
 & (Join-Path $PSScriptRoot 'Observe032.ps1') -Mode Probe -Episode $Episode -Seconds 80 -FrozenSha $FrozenSha -SetupAtVisibleNativeCluster041 -OutputRoot $probe041 *> (Join-Path $PSScriptRoot ($prefix041+'_EARNED.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual pre-enrolled earned native probe failed.'}
 $primary041=@(Get-Content (Join-Path $probe041 'primary.json') -Raw | ConvertFrom-Json)[0]
 $samples041=@(Get-Content (Join-Path $probe041 'all-samples.json') -Raw | ConvertFrom-Json)
 $first041=@($samples041[0].actors | Where-Object profileId -ceq $primary041.profileId)[0]
 $last041=@($samples041[-1].actors | Where-Object profileId -ceq $primary041.profileId)[0]
 if(!$first041 -or !$last041 -or $last041.worldPresent -cne 'true' -or $last041.dead -cne 'false' -or $last041.materializedAtNanos -cne $first041.materializedAtNanos -or [long]$last041.nativeFarmCycleSequence -le [long]$first041.nativeFarmCycleSequence -or [long]$last041.nativeDamageSequence -le [long]$first041.nativeDamageSequence -or [long]$last041.nativeExpGained -le [long]$first041.nativeExpGained){throw 'Prospectively selected crash identity has no actual earned work; no substitute actor.'}
 $single041=Join-Path $probe041 'exact-crash-cohort.json'
 @($primary041) | ConvertTo-Json -Depth 14 | Set-Content $single041 -Encoding utf8
 $mode041=if($Episode -ceq 'x72'){'CrashNative'}else{'CrashFinalize'}
 & $control041 -Action Collector -Mode $mode041 -Episode $Episode -CohortJson $single041 -OutputRoot $window041 *> (Join-Path $PSScriptRoot ($prefix041+'_ARM.log'))
 if($LASTEXITCODE -ne 0){throw 'Exact authorized window arm failed.'}
 $manifest041=Get-Content (Join-Path $runtime041 'local-play/pids/GameServer.json') -Raw | ConvertFrom-Json
 $game041=Get-Process -Id $manifest041.pid
 $null=$game041.Handle
 $deadline041=[DateTime]::UtcNow.AddSeconds(180)
 while(!$game041.HasExited -and [DateTime]::UtcNow -lt $deadline041){Start-Sleep -Seconds 1; $game041.Refresh()}
 if(!$game041.HasExited){throw 'Authorized window not reached; stock stop will disarm injector.'}
 $receipt041=Get-Content (Join-Path $window041 'planned-crash.properties') -Raw | ConvertFrom-StringData
 $expectedExit041=if($Episode -ceq 'x72'){72}else{73}
 if($game041.ExitCode -ne $expectedExit041 -or [int]$receipt041.exit -ne $expectedExit041 -or [long]$receipt041.pid -ne [long]$manifest041.pid -or [long]$receipt041.profileId -ne [long]$primary041.profileId -or $receipt041.REALcount -cne '0'){throw 'Exact reached-window/exit proof differs.'}
 $crashed041=$true
 & $control041 -Action Stop -Episode $Episode *> (Join-Path $PSScriptRoot ($prefix041+'_LOGIN_STOP.log'))
 if($LASTEXITCODE -ne 0){throw 'Own Login stock stop failed after planned Game crash.'}
 $recovery041=Join-Path $evidence041 ($prefix041+'_RECOVERY1')
 if($Episode -ceq 'x72'){
  & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha -StartupRecoveryProfileId ([long]$primary041.profileId) -RecoveryOutputRoot $recovery041 -StartupCohortJson $single041 *> (Join-Path $PSScriptRoot ($prefix041+'_RECOVERY1_START.log'))
 }else{
  & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha -StartupCohortJson $single041 *> (Join-Path $PSScriptRoot ($prefix041+'_RECOVERY1_START.log'))
 }
 if($LASTEXITCODE -ne 0){throw 'First fault-off sameDB recovery boot failed.'}
 for($boot041=1;$boot041 -le 2;$boot041++){
  $label041=$prefix041+'_RECOVERY'+$boot041+'_USEFUL'
  & (Join-Path $PSScriptRoot 'Run-CensusPreparation041.ps1') -Episode $Episode -CohortJson $single041 -OutputRoot (Join-Path $evidence041 ($label041+'_PREP90')) *> (Join-Path $PSScriptRoot ($label041+'_PREP.log'))
  if($LASTEXITCODE -ne 0){throw 'Recovery prospective native preparation failed.'}
  & (Join-Path $PSScriptRoot 'Observe032.ps1') -Mode Probe -Episode $Episode -Seconds 80 -FrozenSha $FrozenSha -SetupAtVisibleNativeCluster041 -OutputRoot (Join-Path $evidence041 $label041) -StopOwnServerOnComplete *> (Join-Path $PSScriptRoot ($label041+'.log'))
  if($LASTEXITCODE -ne 0){throw 'Recovery useful continuation/stock-stop failed.'}
  if($boot041 -eq 1){
   & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha -StartupCohortJson $single041 *> (Join-Path $PSScriptRoot ($prefix041+'_IDEMPOTENCE2_START.log'))
   if($LASTEXITCODE -ne 0){throw 'Second fault-off sameDB start failed.'}
  }
 }
 @{actualPlannedCrash=$true;window=$window041;exit=$expectedExit041;profileId=[long]$primary041.profileId;firstRecovery=$recovery041;secondBootCompleted=$true;faultOff=$true;stockStopsCompleted=$true;semanticValidation='PENDING_FULL_NATIVE_SQL_CHAIN';source=$FrozenSha;utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot ($prefix041+'_EXECUTION.json')) -Encoding utf8
 'ACTUAL_ISOLATED_CRASH_RECOVERY_EXECUTION_COMPLETE; semantic gates still require exact SQL/native validation'
} catch {
 $_ | Out-String | Set-Content (Join-Path $PSScriptRoot ($prefix041+'_FAILURE.txt')) -Encoding utf8
 & $control041 -Action Stop -Episode $Episode *> (Join-Path $PSScriptRoot ($prefix041+'_SAFE_STOCK_STOP.log'))
 if($crashed041){'MANDATORY_RECOVERY_AUDIT_REQUIRED' | Set-Content (Join-Path $PSScriptRoot ($prefix041+'_RECOVERY_REQUIRED.txt'))}
 throw
}
