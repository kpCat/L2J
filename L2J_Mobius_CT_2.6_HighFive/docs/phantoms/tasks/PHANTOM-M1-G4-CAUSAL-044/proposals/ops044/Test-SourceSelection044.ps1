$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$raw=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044/evidence/DIAG_A1_044'
. (Join-Path $PSScriptRoot 'Select-Primary044.ps1')
$actor=Get-Content (Join-Path $raw 'tentative-primary.json') -Raw | ConvertFrom-Json
$inputs=@(Get-Content (Join-Path $raw 'premeasurement-farm-inputs044.json') -Raw | ConvertFrom-Json)
if($actor.profileId -cne '68' -or $actor.dead -cne 'false' -or [long]$actor.nativeFarmCycleSequence -lt 1){throw 'Immutable native input fixture changed'}
$source=@(Select-CurrentLifeInputs044 -Suitable @($actor) -FarmInputs $inputs -RequireCurrent:$false)
if($source.Count -ne 1 -or $source[0].profileId -cne '68'){throw 'SOURCE_DIAGNOSTIC_MUST_NOT_REQUIRE_BG_READINESS'}
$life=@(Select-CurrentLifeInputs044 -Suitable @($actor) -FarmInputs $inputs -RequireCurrent:$true)
if($life.Count -ne 0){throw 'FULL_G4_MUST_RETAIN_CURRENT_READINESS_GATE'}
Write-Output 'SOURCE_SELECTION044 PASS: source-only allows actual earned68; full G4 still rejects non-current68'
