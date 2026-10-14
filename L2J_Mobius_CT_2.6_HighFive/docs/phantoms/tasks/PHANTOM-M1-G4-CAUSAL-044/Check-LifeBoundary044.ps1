$ErrorActionPreference='Stop'
$module044=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
. (Join-Path $module044 '.phantom-local/ops044/Select-Primary044.ps1')
$scene044=Join-Path $PSScriptRoot 'evidence/LIFE_A1_044'
$frames044=@(Get-Content (Join-Path $scene044 'full-native/full-cohort-samples.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$frame044=@($frames044 | Where-Object {$_.sampleNanos -eq 662333889262600})[0]
$actor044=@($frame044.actors | Where-Object profileId -CEQ '189')[0]
$other044=@(Import-Csv (Join-Path $scene044 'enrollment-census/world-identities.tsv') -Delimiter "`t" | Where-Object {$_.headless -ceq 'false' -and $_.objectId -cne '268492939'})
$oldRejected044=$actor044.operatorAdmission -match 'retentionPins=\[[^\]]*NATIVE_VISIBLE'
if(-not $oldRejected044 -or -not (Select-String -LiteralPath (Join-Path $scene044 'episode-failure.txt') -SimpleMatch 'LIFE044_SAFE_BOUNDARY_INPUT_ABSENT' -Quiet)){throw 'Actual A1 old-guard RED missing.'}
if(-not (Test-LifeBoundary044 -Actor $actor044 -Epoch $actor044.materializedAtNanos -OtherNativeHumans $other044.Count)){throw 'Same actual sole-observer quiet frame must be accepted.'}
if(Test-LifeBoundary044 -Actor $actor044 -Epoch '0' -OtherNativeHumans 0){throw 'Stale epoch accepted.'}
if(Test-LifeBoundary044 -Actor $actor044 -Epoch $actor044.materializedAtNanos -OtherNativeHumans 1){throw 'Foreign native hard hold accepted.'}
$incoming044=$actor044 | Select-Object *
$incoming044.incomingTargetCount044='1'
if(Test-LifeBoundary044 -Actor $incoming044 -Epoch $actor044.materializedAtNanos -OtherNativeHumans 0){throw 'Actual incoming combat accepted as safe.'}
@{contract='RUNNER044_SOLE_OBSERVER_BOUNDARY';actualA1Red=$true;sampleNanos=$frame044.sampleNanos;profileId=189;greenSameFrame=$true;negativeStaleEpoch=$true;negativeForeignNativeHold=$true;negativeIncomingCombat=$true;productionEdits=$false;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'evidence/RUNNER_BOUNDARY044.json') -Encoding utf8
'RUNNER_BOUNDARY044 PASS: actual A1 RED -> same-frame GREEN; stale/foreign/incoming negatives.'
