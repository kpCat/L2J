param([ValidateSet('a','b')][string]$Episode='b',[Parameter(Mandatory)][string]$Candidate,[Parameter(Mandatory)][string]$FrozenSha,[switch]$CurrentOriginPreparation041)
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041'
$control041=Join-Path $PSScriptRoot 'Control032.ps1'
$prefix041=$Candidate+'_GROUP_'+$Episode.ToUpperInvariant()+'_041'
$bootstrap041=Join-Path $task041 ('evidence/'+$prefix041+'_BOOTSTRAP.json')
if(Test-Path $bootstrap041){throw 'Immutable own041 group exists.'}
@([pscustomobject]@{profileId=1;materializedAtNanos=0}) | ConvertTo-Json | Set-Content $bootstrap041 -Encoding utf8
$runtime041=Join-Path $module041 ('.phantom-local/contract041'+$Episode+'/runtime')
$run041=''
try {
 & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $PSScriptRoot ($prefix041+'_START.log'))
 if($LASTEXITCODE -ne 0){throw 'Exact native group start failed.'}
 # Fresh cold-copy topology had no registered profiles two seconds after stock readiness.
 # Fixed premeasurement startup settling; no synthetic lease or gameplay outcome exists yet.
 Start-Sleep -Seconds 90
 $census041=Join-Path $task041 ('evidence/'+$prefix041+'_PROSPECTIVE_CENSUS')
 & $control041 -Action Collector -Mode Census -Episode $Episode -CohortJson $bootstrap041 -OutputRoot $census041 *> (Join-Path $PSScriptRoot ($prefix041+'_CENSUS.log'))
 if($LASTEXITCODE -ne 0){throw 'Current native input census failed.'}
 $selection041=Join-Path $task041 ('evidence/'+$prefix041+'_SELECTION.json')
 if($CurrentOriginPreparation041){
  # Existing original observer-position route, independently validated by C20 native eight.
  # This grants no admission and does not apply the alternative READY-location selector.
  $profile041=0
  [ordered]@{rule='EXISTING_SAVED_OBSERVER_ORIGIN_NO_TELEPORT_NO_PROFILE_SELECTION';source=$census041;utc=[DateTime]::UtcNow.ToString('o');outcomesInspected=$false} | ConvertTo-Json | Set-Content $selection041 -Encoding utf8
 }else{$profile041=& (Join-Path $PSScriptRoot 'Select-ReadySetup041.ps1') -CensusRoot $census041 -OutputFile $selection041}
 $preparation041=Join-Path $task041 ('evidence/'+$prefix041+'_PREP90')
 & (Join-Path $PSScriptRoot 'Run-CensusPreparation041.ps1') -Episode $Episode -SetupProfileId $profile041 -KeepSession041 -CohortJson $bootstrap041 -OutputRoot $preparation041 *> (Join-Path $PSScriptRoot ($prefix041+'_PREP.log'))
 if($LASTEXITCODE -ne 0){throw 'Selected native preparation failed.'}
 $run041=(Get-Content (Join-Path $preparation041 'session-start.json') -Raw | ConvertFrom-Json).runId
 $scene041=Join-Path $task041 ('evidence/'+$prefix041)
 & (Join-Path $PSScriptRoot 'Observe032.ps1') -Mode Scene -Episode $Episode -Seconds 380 -FrozenSha $FrozenSha -PreparedRunId $run041 -OutputRoot $scene041 -StopOwnServerOnComplete *> (Join-Path $PSScriptRoot ($prefix041+'_SCENE.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual native whole-group capture failed.'}
 & (Join-Path $task041 'Verify041.ps1') -Action Evaluate -EpisodeOutput $scene041 *> (Join-Path $PSScriptRoot ($prefix041+'_EVALUATE.log'))
 if($LASTEXITCODE -ne 0){throw 'Original byte-exact evaluation input failed.'}
 'NATIVE_GROUP_CAPTURED; actual CONTINUITY_V2 verdict remains authoritative'
} catch {
 $_ | Out-String | Set-Content (Join-Path $PSScriptRoot ($prefix041+'_FAILURE.txt')) -Encoding utf8
 if($run041){& (Join-Path $runtime041 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run041 *> (Join-Path $PSScriptRoot ($prefix041+'_LEASE_STOP.log'))}
 & $control041 -Action Stop -Episode $Episode *> (Join-Path $PSScriptRoot ($prefix041+'_SAFE_STOCK_STOP.log'))
 throw
}
