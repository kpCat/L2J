param([Parameter(Mandatory)][ValidateSet('a')][string]$Episode,[Parameter(Mandatory)][string]$Startup,[Parameter(Mandatory)][string]$Label,[string]$AdditionalCohortJson='')
$ErrorActionPreference='Stop'
$module045=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task045=Join-Path $module045 'docs/phantoms/tasks/PHANTOM-M1-SIGNAL-LIFECYCLE-045'
$startup045=[IO.Path]::GetFullPath($Startup)
if(-not $startup045.StartsWith([IO.Path]::GetFullPath((Join-Path $task045 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact045 startup required.'}
$ledger045=@(Get-Content -LiteralPath (Join-Path $startup045 'lifetimes.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$ids045=@($ledger045 | Where-Object {$_.kind -ceq 'REGISTER'} | ForEach-Object {[long]$_.profile_id} | Sort-Object -Unique)
if($AdditionalCohortJson){
 $input045=[IO.Path]::GetFullPath($AdditionalCohortJson)
 if(-not $input045.StartsWith([IO.Path]::GetFullPath((Join-Path $task045 'evidence'))+[IO.Path]::DirectorySeparatorChar)){throw 'Exact original045 cohort required.'}
 $original045=@((Get-Content -LiteralPath $input045 -Raw | ConvertFrom-Json))
 if($original045.Count -lt 1 -or $original045.Count -gt 8 -or @($original045 | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Original whole cohort1..8 required.'}
 $ids045=@($ids045+@($original045 | ForEach-Object {[long]$_.profileId}) | Sort-Object -Unique)
}
if($ids045.Count -eq 0 -or $ids045.Count -gt 1280){throw 'Existing all-birth read budget1..1280 required.'}
$cut045=Join-Path $task045 "evidence/$Label"
& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds $ids045 -AllBirthsStopped039 -OutputRoot $cut045
if($LASTEXITCODE -ne 0){throw 'Stopped all-birth cut failed.'}
[pscustomobject]@{startup=$startup045;profiles=$ids045.Count;lastKind=$ledger045[-1].kind;dropped=$ledger045[-1].dropped;failure=$ledger045[-1].failure;cut=$cut045} | ConvertTo-Json
