param([Parameter(Mandatory)][ValidateSet('a','b','x72','x73')][string]$Episode,[Parameter(Mandatory)][string]$Startup,[Parameter(Mandatory)][string]$Label,[string]$AdditionalCohortJson='')
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041'
$startup041=[IO.Path]::GetFullPath($Startup)
if(-not $startup041.StartsWith([IO.Path]::GetFullPath((Join-Path $task041 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact041 startup required.'}
$ledger041=@(Get-Content -LiteralPath (Join-Path $startup041 'lifetimes.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$ids041=@($ledger041 | Where-Object {$_.kind -ceq 'REGISTER'} | ForEach-Object {[long]$_.profile_id} | Sort-Object -Unique)
if($AdditionalCohortJson){
 $input041=[IO.Path]::GetFullPath($AdditionalCohortJson)
 if(-not $input041.StartsWith([IO.Path]::GetFullPath((Join-Path $task041 'evidence'))+[IO.Path]::DirectorySeparatorChar)){throw 'Exact original041 cohort required.'}
 $original041=@((Get-Content -LiteralPath $input041 -Raw | ConvertFrom-Json))
 if($original041.Count -lt 1 -or $original041.Count -gt 8 -or @($original041 | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Original whole cohort1..8 required.'}
 $ids041=@($ids041+@($original041 | ForEach-Object {[long]$_.profileId}) | Sort-Object -Unique)
}
if($ids041.Count -eq 0 -or $ids041.Count -gt 1280){throw 'Existing all-birth read budget1..1280 required.'}
$cut041=Join-Path $task041 "evidence/$Label"
& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds $ids041 -AllBirthsStopped039 -OutputRoot $cut041
if($LASTEXITCODE -ne 0){throw 'Stopped all-birth cut failed.'}
[pscustomobject]@{startup=$startup041;profiles=$ids041.Count;lastKind=$ledger041[-1].kind;dropped=$ledger041[-1].dropped;failure=$ledger041[-1].failure;cut=$cut041} | ConvertTo-Json
