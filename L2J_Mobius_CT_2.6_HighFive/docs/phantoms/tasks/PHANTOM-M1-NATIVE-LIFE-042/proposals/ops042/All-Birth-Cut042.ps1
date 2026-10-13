param([Parameter(Mandatory)][ValidateSet('a','b')][string]$Episode,[Parameter(Mandatory)][string]$Startup,[Parameter(Mandatory)][string]$Label,[string]$AdditionalCohortJson='')
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task042=Join-Path $module042 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042'
$startup042=[IO.Path]::GetFullPath($Startup)
if(-not $startup042.StartsWith([IO.Path]::GetFullPath((Join-Path $task042 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact042 startup required.'}
$ledger042=@(Get-Content -LiteralPath (Join-Path $startup042 'lifetimes.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$ids042=@($ledger042 | Where-Object {$_.kind -ceq 'REGISTER'} | ForEach-Object {[long]$_.profile_id} | Sort-Object -Unique)
if($AdditionalCohortJson){
 $input042=[IO.Path]::GetFullPath($AdditionalCohortJson)
 if(-not $input042.StartsWith([IO.Path]::GetFullPath((Join-Path $task042 'evidence'))+[IO.Path]::DirectorySeparatorChar)){throw 'Exact original042 cohort required.'}
 $original042=@((Get-Content -LiteralPath $input042 -Raw | ConvertFrom-Json))
 if($original042.Count -lt 1 -or $original042.Count -gt 8 -or @($original042 | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Original whole cohort1..8 required.'}
 $ids042=@($ids042+@($original042 | ForEach-Object {[long]$_.profileId}) | Sort-Object -Unique)
}
if($ids042.Count -eq 0 -or $ids042.Count -gt 1280){throw 'Existing all-birth read budget1..1280 required.'}
$cut042=Join-Path $task042 "evidence/$Label"
& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds $ids042 -AllBirthsStopped039 -OutputRoot $cut042
if($LASTEXITCODE -ne 0){throw 'Stopped all-birth cut failed.'}
[pscustomobject]@{startup=$startup042;profiles=$ids042.Count;lastKind=$ledger042[-1].kind;dropped=$ledger042[-1].dropped;failure=$ledger042[-1].failure;cut=$cut042} | ConvertTo-Json
