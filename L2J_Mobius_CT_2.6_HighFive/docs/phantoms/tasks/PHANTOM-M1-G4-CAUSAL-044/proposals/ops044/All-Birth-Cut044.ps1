param([Parameter(Mandatory)][ValidateSet('a','b')][string]$Episode,[Parameter(Mandatory)][string]$Startup,[Parameter(Mandatory)][string]$Label,[string]$AdditionalCohortJson='')
$ErrorActionPreference='Stop'
$module044=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task044=Join-Path $module044 'docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044'
$startup044=[IO.Path]::GetFullPath($Startup)
if(-not $startup044.StartsWith([IO.Path]::GetFullPath((Join-Path $task044 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact044 startup required.'}
$ledger044=@(Get-Content -LiteralPath (Join-Path $startup044 'lifetimes.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$ids044=@($ledger044 | Where-Object {$_.kind -ceq 'REGISTER'} | ForEach-Object {[long]$_.profile_id} | Sort-Object -Unique)
if($AdditionalCohortJson){
 $input044=[IO.Path]::GetFullPath($AdditionalCohortJson)
 if(-not $input044.StartsWith([IO.Path]::GetFullPath((Join-Path $task044 'evidence'))+[IO.Path]::DirectorySeparatorChar)){throw 'Exact original044 cohort required.'}
 $original044=@((Get-Content -LiteralPath $input044 -Raw | ConvertFrom-Json))
 if($original044.Count -lt 1 -or $original044.Count -gt 8 -or @($original044 | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Original whole cohort1..8 required.'}
 $ids044=@($ids044+@($original044 | ForEach-Object {[long]$_.profileId}) | Sort-Object -Unique)
}
if($ids044.Count -eq 0 -or $ids044.Count -gt 1280){throw 'Existing all-birth read budget1..1280 required.'}
$cut044=Join-Path $task044 "evidence/$Label"
& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds $ids044 -AllBirthsStopped039 -OutputRoot $cut044
if($LASTEXITCODE -ne 0){throw 'Stopped all-birth cut failed.'}
[pscustomobject]@{startup=$startup044;profiles=$ids044.Count;lastKind=$ledger044[-1].kind;dropped=$ledger044[-1].dropped;failure=$ledger044[-1].failure;cut=$cut044} | ConvertTo-Json
