param()
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
. (Join-Path $module042 '.phantom-local/ops042/Select-Primary042.ps1')
$inputDocument042=Get-Content (Join-Path $PSScriptRoot 'evidence/LIFE_PRIMARY_INPUT042.json') -Raw | ConvertFrom-Json
$inputs042=@($inputDocument042.items)
$old042=if($true){@($inputs042 | Sort-Object {[long]$_.profileId} | Select-Object -First 1)}
if($old042.Count -eq 1){throw 'Expected actual Windows PowerShell scalar-count RED was not reproduced.'}
$selected042=@(if($true){Select-LifePrimary042 -Suitable $inputs042})
if($selected042.Count -ne 1 -or $selected042[0].profileId -cne '68'){throw 'Single preselected living profile contract failed.'}
$rejected042=$false
try {Select-LifePrimary042 -Suitable @() | Out-Null} catch {if($_ -notmatch 'LIFE_PRIMARY042_INPUT_REQUIRED'){throw};$rejected042=$true}
if(-not $rejected042){throw 'Empty native input must be rejected without replacing a failed actor.'}
. (Join-Path $module042 '.phantom-local/contract042b/runtime/LocalPlay-Pilot.ps1')
. (Join-Path $module042 '.phantom-local/contract042b/runtime/LocalPlay-Ownership.ps1')
if(-not (Get-Command Get-PilotIniValue -ErrorAction Stop)){throw 'Stock cleanup dependency missing.'}
[pscustomobject]@{oldScalarCountRed=$true;singleNativePrimaryGreen=$true;emptyInputNegative=$true;stockCleanupDependency=$true} |
    ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'evidence/LIFE_INPUT_CONTRACT042.json') -Encoding utf8
'LIFE_INPUT042 RED scalar-count; GREEN single profile; negative empty rejected; stock cleanup helper loaded.'
