param([Parameter(Mandatory)][ValidateSet('x72','x73')][string]$Episode,[Parameter(Mandatory)][string]$Candidate,[Parameter(Mandatory)][string]$FrozenSha,[Parameter(Mandatory)][string]$CrashReceipt,[Parameter(Mandatory)][string]$CohortJson,[Parameter(Mandatory)][string]$PriorCut)
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence'
$allowed041=[IO.Path]::GetFullPath($evidence041)+'\'
foreach($input041 in @($CrashReceipt,$CohortJson,$PriorCut)){if(-not [IO.Path]::GetFullPath($input041).StartsWith($allowed041,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact own041 immutable input required.'}}
$receipt041=Get-Content $CrashReceipt -Raw | ConvertFrom-StringData
$cohort041=@((Get-Content $CohortJson -Raw | ConvertFrom-Json))
if($receipt041.REALcount -cne '0' -or [int]$receipt041.exit -ne $(if($Episode -ceq 'x72'){72}else{73}) -or $cohort041.Count -ne 1 -or [long]$receipt041.profileId -ne [long]$cohort041[0].profileId){throw 'Actual selected isolated crash receipt required.'}
$prefix041=$Candidate+'_'+$Episode.ToUpperInvariant()+'_IDEMPOTENCE2_041'
$control041=Join-Path $PSScriptRoot 'Control032.ps1'
$runtime041=Join-Path $module041 ('.phantom-local/contract041'+$Episode+'/runtime')
$run041=''
$known041=@((Get-ChildItem $evidence041 -Directory -Filter ('STARTUP032'+$Episode+'-*')).Name)
try{
 & $control041 -Action Start -Episode $Episode -ExpectedSha $FrozenSha -StartupCohortJson $CohortJson *> (Join-Path $PSScriptRoot ($prefix041+'_START.log'))
 if($LASTEXITCODE -ne 0){throw 'Fault-off second sameDB boot failed.'}
 $startup041=@(Get-ChildItem $evidence041 -Directory -Filter ('STARTUP032'+$Episode+'-*') | Where-Object Name -NotIn $known041)
 if($startup041.Count -ne 1){throw 'One actual new startup namespace required.'}
 $prep041=Join-Path $evidence041 ($prefix041+'_PREP90')
 & (Join-Path $PSScriptRoot 'Run-CensusPreparation041.ps1') -Episode $Episode -KeepSession041 -CohortJson $CohortJson -OutputRoot $prep041 *> (Join-Path $PSScriptRoot ($prefix041+'_PREP.log'))
 if($LASTEXITCODE -ne 0){throw 'Native original-position preparation failed.'}
 $run041=(Get-Content (Join-Path $prep041 'session-start.json') -Raw | ConvertFrom-Json).runId
 $scene041=Join-Path $evidence041 $prefix041
 & (Join-Path $PSScriptRoot 'Observe032.ps1') -Mode Probe -Episode $Episode -Seconds 80 -FrozenSha $FrozenSha -PreparedRunId $run041 -OutputRoot $scene041 -StopOwnServerOnComplete *> (Join-Path $PSScriptRoot ($prefix041+'_PROBE.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual whole native probe/stock stop failed.'}
 $cutLabel041=$prefix041+'_STOPPED_SQL'
 & (Join-Path $PSScriptRoot 'All-Birth-Cut041.ps1') -Episode $Episode -Startup $startup041[0].FullName -Label $cutLabel041 -AdditionalCohortJson $CohortJson *> (Join-Path $PSScriptRoot ($prefix041+'_CUT.log'))
 if($LASTEXITCODE -ne 0){throw 'Actual stopped full canonical cut failed.'}
 & python -B (Join-Path $PSScriptRoot 'validation/CrossBoot041.py') --prior-cut $PriorCut --next-cut (Join-Path $evidence041 $cutLabel041) --startup $startup041[0].FullName --cohort $CohortJson --scene $scene041 --jar (Join-Path $runtime041 'libs/GameServer.jar') --source $FrozenSha --output (Join-Path $PSScriptRoot ($prefix041+'_CROSSBOOT.json')) *> (Join-Path $PSScriptRoot ($prefix041+'_CROSSBOOT.log'))
 Get-Content (Join-Path $PSScriptRoot ($prefix041+'_CROSSBOOT.log')) -Tail 1
}catch{
 $_ | Out-String | Set-Content (Join-Path $PSScriptRoot ($prefix041+'_FAILURE.txt')) -Encoding utf8
 if($run041){try{& (Join-Path $runtime041 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run041 *> (Join-Path $PSScriptRoot ($prefix041+'_LEASE_STOP.log'))}catch{}}
 & $control041 -Action Stop -Episode $Episode *> (Join-Path $PSScriptRoot ($prefix041+'_SAFE_STOCK_STOP.log'))
 throw
}
