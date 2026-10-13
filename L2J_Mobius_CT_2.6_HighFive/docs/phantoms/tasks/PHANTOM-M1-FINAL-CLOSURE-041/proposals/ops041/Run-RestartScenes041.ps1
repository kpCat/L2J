param([Parameter(Mandatory)][ValidateSet('a','b')][string]$Episode,[Parameter(Mandatory)][string]$FrozenSha,[Parameter(Mandatory)][string]$OriginalScene,[Parameter(Mandatory)][string]$PriorCut,[ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Candidate='C4')
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence'
$control041=Join-Path $PSScriptRoot 'Control032.ps1'
$runner041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/Verify041.ps1'
$original041=[IO.Path]::GetFullPath($OriginalScene)
$prior041=[IO.Path]::GetFullPath($PriorCut)
$cohort041=Join-Path $original041 'baseline-cohort.json'
foreach($input041 in @($original041,$prior041)){if(-not $input041.StartsWith([IO.Path]::GetFullPath($evidence041)+[IO.Path]::DirectorySeparatorChar)){throw 'Exact041 input required.'}}
for($restart041=1;$restart041 -le 2;$restart041++){
 $label041=$Candidate+'_RESTART'+$restart041+'_'+$Episode+'041'
 $scene041=Join-Path $evidence041 $label041
 $known041=@((Get-ChildItem -LiteralPath $evidence041 -Directory -Filter ('STARTUP032'+$Episode+'-*')).Name)
 & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha -StartupCohortJson $cohort041 *> (Join-Path $PSScriptRoot ($label041+'-start.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual normal sameDB start failed; no reset/reimport.'}
 $startup041=@(Get-ChildItem -LiteralPath $evidence041 -Directory -Filter ('STARTUP032'+$Episode+'-*') | Where-Object {$_.Name -notin $known041})
 if($startup041.Count -ne 1){throw 'Exact new startup namespace ambiguous.'}
 $preparation041=Join-Path $evidence041 ($label041+'_PREP90')
 & (Join-Path $PSScriptRoot 'Run-CensusPreparation041.ps1') -Episode $Episode -KeepSession041 -CohortJson $cohort041 -OutputRoot $preparation041 *> (Join-Path $PSScriptRoot ($label041+'-prep.log'))
 if($LASTEXITCODE -ne 0){throw 'SameDB original-position native preparation failed.'}
 $prepared041=(Get-Content (Join-Path $preparation041 'session-start.json') -Raw | ConvertFrom-Json).runId
 & (Join-Path $PSScriptRoot 'Observe032.ps1') -Mode Scene -Episode $Episode -Seconds 380 -FrozenSha $FrozenSha -PreparedRunId $prepared041 -OutputRoot $scene041 -StopOwnServerOnComplete *> (Join-Path $PSScriptRoot ($label041+'-scene.log'))
 if($LASTEXITCODE -ne 0){
  & $control041 -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $evidence041 ($label041+'-failed-stock-stop')) *> (Join-Path $PSScriptRoot ($label041+'-failed-stop.log'))
  throw 'Restart useful scene failed; exact guards/raw retained.'
 }
 & $runner041 -Action Evaluate -EpisodeOutput $scene041
 $cutLabel041=$label041+'_ALL_BIRTH_STOPPED'
 & (Join-Path $PSScriptRoot 'All-Birth-Cut041.ps1') -Episode $Episode -Startup $startup041[0].FullName -Label $cutLabel041 -AdditionalCohortJson $cohort041 *> (Join-Path $PSScriptRoot ($label041+'-cut.log'))
 if($LASTEXITCODE -ne 0){throw 'Exact stopped all-birth cut failed.'}
 $cut041=Join-Path $evidence041 $cutLabel041
 & python -B (Join-Path $PSScriptRoot 'validation/verify_commit_run039.py') $startup041[0].FullName $scene041 --jar (Join-Path $module041 'dist/libs/GameServer.jar') --source $FrozenSha --stock-stop (Join-Path $scene041 'stock-stop') --stopped-sql $cut041 --output (Join-Path $PSScriptRoot ($label041+'-save.json')) *> (Join-Path $PSScriptRoot ($label041+'-save.log'))
 Get-Content (Join-Path $PSScriptRoot ($label041+'-save.log')) -Tail 1
 & python -B (Join-Path $PSScriptRoot 'validation/CrossBoot041.py') --prior-cut $prior041 --next-cut $cut041 --startup $startup041[0].FullName --cohort $cohort041 --scene $scene041 --jar (Join-Path $module041 'dist/libs/GameServer.jar') --source $FrozenSha --output (Join-Path $PSScriptRoot ($label041+'-crossboot.json')) *> (Join-Path $PSScriptRoot ($label041+'-crossboot.log'))
 Get-Content (Join-Path $PSScriptRoot ($label041+'-crossboot.log')) -Tail 1
 # A verifier FAIL remains explicit. The second requested actual restart is still performed.
 $prior041=$cut041
}
