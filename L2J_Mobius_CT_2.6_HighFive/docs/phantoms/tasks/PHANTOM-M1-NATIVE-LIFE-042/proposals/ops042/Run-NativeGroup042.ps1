param([ValidateSet('a','b')][string]$Episode='b',[Parameter(Mandatory)][string]$Candidate,[Parameter(Mandatory)][string]$FrozenSha,[switch]$CurrentOriginPreparation042,[string]$CensusNotBeforeUtc='',[switch]$NativeWorldCluster042)
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task042=Join-Path $module042 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042'
$control042=Join-Path $PSScriptRoot 'Control032.ps1'
$prefix042=$Candidate+'_GROUP_'+$Episode.ToUpperInvariant()+'_042'
$bootstrap042=Join-Path $task042 ('evidence/'+$prefix042+'_BOOTSTRAP.json')
if(Test-Path $bootstrap042){throw 'Immutable own042 group exists.'}
@([pscustomobject]@{profileId=1;materializedAtNanos=0}) | ConvertTo-Json | Set-Content $bootstrap042 -Encoding utf8
$runtime042=Join-Path $module042 ('.phantom-local/contract042'+$Episode+'/runtime')
$run042=''
try {
 & $control042 -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $PSScriptRoot ($prefix042+'_START.log'))
 if($LASTEXITCODE -ne 0){throw 'Exact native group start failed.'}
 # Fresh cold-copy topology had no registered profiles two seconds after stock readiness.
 # Fixed premeasurement startup settling; no synthetic lease or gameplay outcome exists yet.
 Start-Sleep -Seconds 90
 if($CensusNotBeforeUtc){
  $calendarStart042=[DateTimeOffset]::Parse($CensusNotBeforeUtc)
  while([DateTimeOffset]::UtcNow -lt $calendarStart042){Start-Sleep -Seconds 15}
 }
 $census042=Join-Path $task042 ('evidence/'+$prefix042+'_PROSPECTIVE_CENSUS')
 & $control042 -Action Collector -Mode Census -Episode $Episode -CohortJson $bootstrap042 -OutputRoot $census042 *> (Join-Path $PSScriptRoot ($prefix042+'_CENSUS.log'))
 if($LASTEXITCODE -ne 0){throw 'Current native input census failed.'}
 $selection042=Join-Path $task042 ('evidence/'+$prefix042+'_SELECTION.json')
 if($NativeWorldCluster042){
  [ordered]@{rule='EXISTING_CURRENT_WORLD_CLUSTER_COUNT_DISTANCE_PROFILE_MIN6';source=$census042;utc=[DateTime]::UtcNow.ToString('o');outcomesInspected=$false} | ConvertTo-Json | Set-Content $selection042 -Encoding utf8
 }elseif($CurrentOriginPreparation042){
  # Existing original observer-position route, independently validated by C20 native eight.
  # This grants no admission and does not apply the alternative READY-location selector.
  $profile042=0
  [ordered]@{rule='EXISTING_SAVED_OBSERVER_ORIGIN_NO_TELEPORT_NO_PROFILE_SELECTION';source=$census042;utc=[DateTime]::UtcNow.ToString('o');outcomesInspected=$false} | ConvertTo-Json | Set-Content $selection042 -Encoding utf8
 }else{$profile042=& (Join-Path $PSScriptRoot 'Select-ReadySetup042.ps1') -CensusRoot $census042 -OutputFile $selection042}
 if(-not $NativeWorldCluster042){
  $preparation042=Join-Path $task042 ('evidence/'+$prefix042+'_PREP90')
  & (Join-Path $PSScriptRoot 'Run-CensusPreparation042.ps1') -Episode $Episode -SetupProfileId $profile042 -KeepSession042 -CohortJson $bootstrap042 -OutputRoot $preparation042 *> (Join-Path $PSScriptRoot ($prefix042+'_PREP.log'))
  if($LASTEXITCODE -ne 0){throw 'Selected native preparation failed.'}
  $run042=(Get-Content (Join-Path $preparation042 'session-start.json') -Raw | ConvertFrom-Json).runId
 }
 $scene042=Join-Path $task042 ('evidence/'+$prefix042)
 $sceneArgs042=@{Mode='Scene';Episode=$Episode;Seconds=380;FrozenSha=$FrozenSha;OutputRoot=$scene042;StopOwnServerOnComplete=$true}
 if($NativeWorldCluster042){$sceneArgs042.SetupAtVisibleNativeCluster042=$true}else{$sceneArgs042.PreparedRunId=$run042}
 & (Join-Path $PSScriptRoot 'Observe032.ps1') @sceneArgs042 *> (Join-Path $PSScriptRoot ($prefix042+'_SCENE.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual native whole-group capture failed.'}
 & (Join-Path $task042 'Verify042.ps1') -Action Evaluate -EpisodeOutput $scene042 *> (Join-Path $PSScriptRoot ($prefix042+'_EVALUATE.log'))
 if($LASTEXITCODE -ne 0){throw 'Original byte-exact evaluation input failed.'}
 'NATIVE_GROUP_CAPTURED; actual CONTINUITY_V2 verdict remains authoritative'
} catch {
 $_ | Out-String | Set-Content (Join-Path $PSScriptRoot ($prefix042+'_FAILURE.txt')) -Encoding utf8
 if($run042){& (Join-Path $runtime042 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run042 *> (Join-Path $PSScriptRoot ($prefix042+'_LEASE_STOP.log'))}
 & $control042 -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $task042 ('evidence/'+$prefix042+'_FAILURE_STOP')) *> (Join-Path $PSScriptRoot ($prefix042+'_SAFE_STOCK_STOP.log'))
 throw
}
