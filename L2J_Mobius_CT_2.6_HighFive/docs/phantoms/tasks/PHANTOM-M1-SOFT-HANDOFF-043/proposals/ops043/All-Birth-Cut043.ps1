param([Parameter(Mandatory)][ValidateSet('a','b')][string]$Episode,[Parameter(Mandatory)][string]$Startup,[Parameter(Mandatory)][string]$Label,[string]$AdditionalCohortJson='')
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task043=Join-Path $module043 'docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043'
$startup043=[IO.Path]::GetFullPath($Startup)
if(-not $startup043.StartsWith([IO.Path]::GetFullPath((Join-Path $task043 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact043 startup required.'}
$ledger043=@(Get-Content -LiteralPath (Join-Path $startup043 'lifetimes.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$ids043=@($ledger043 | Where-Object {$_.kind -ceq 'REGISTER'} | ForEach-Object {[long]$_.profile_id} | Sort-Object -Unique)
if($AdditionalCohortJson){
 $input043=[IO.Path]::GetFullPath($AdditionalCohortJson)
 if(-not $input043.StartsWith([IO.Path]::GetFullPath((Join-Path $task043 'evidence'))+[IO.Path]::DirectorySeparatorChar)){throw 'Exact original043 cohort required.'}
 $original043=@((Get-Content -LiteralPath $input043 -Raw | ConvertFrom-Json))
 if($original043.Count -lt 1 -or $original043.Count -gt 8 -or @($original043 | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Original whole cohort1..8 required.'}
 $ids043=@($ids043+@($original043 | ForEach-Object {[long]$_.profileId}) | Sort-Object -Unique)
}
if($ids043.Count -eq 0 -or $ids043.Count -gt 1280){throw 'Existing all-birth read budget1..1280 required.'}
$cut043=Join-Path $task043 "evidence/$Label"
& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds $ids043 -AllBirthsStopped039 -OutputRoot $cut043
if($LASTEXITCODE -ne 0){throw 'Stopped all-birth cut failed.'}
[pscustomobject]@{startup=$startup043;profiles=$ids043.Count;lastKind=$ledger043[-1].kind;dropped=$ledger043[-1].dropped;failure=$ledger043[-1].failure;cut=$cut043} | ConvertTo-Json
