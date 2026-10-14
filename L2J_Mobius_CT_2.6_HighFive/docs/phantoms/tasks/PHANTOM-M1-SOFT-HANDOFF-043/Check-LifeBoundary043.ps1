$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
. (Join-Path $module043 '.phantom-local/ops043/Select-Primary043.ps1')
$scene043=Join-Path $PSScriptRoot 'evidence/LIFE_A1_043'
$frames043=@(Get-Content (Join-Path $scene043 'full-native/full-cohort-samples.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
$frame043=@($frames043 | Where-Object {$_.sampleNanos -eq 662333889262600})[0]
$actor043=@($frame043.actors | Where-Object profileId -CEQ '189')[0]
$other043=@(Import-Csv (Join-Path $scene043 'enrollment-census/world-identities.tsv') -Delimiter "`t" | Where-Object {$_.headless -ceq 'false' -and $_.objectId -cne '268492939'})
$oldRejected043=$actor043.operatorAdmission -match 'retentionPins=\[[^\]]*NATIVE_VISIBLE'
if(-not $oldRejected043 -or -not (Select-String -LiteralPath (Join-Path $scene043 'episode-failure.txt') -SimpleMatch 'LIFE043_SAFE_BOUNDARY_INPUT_ABSENT' -Quiet)){throw 'Actual A1 old-guard RED missing.'}
if(-not (Test-LifeBoundary043 -Actor $actor043 -Epoch $actor043.materializedAtNanos -OtherNativeHumans $other043.Count)){throw 'Same actual sole-observer quiet frame must be accepted.'}
if(Test-LifeBoundary043 -Actor $actor043 -Epoch '0' -OtherNativeHumans 0){throw 'Stale epoch accepted.'}
if(Test-LifeBoundary043 -Actor $actor043 -Epoch $actor043.materializedAtNanos -OtherNativeHumans 1){throw 'Foreign native hard hold accepted.'}
$incoming043=$actor043 | Select-Object *
$incoming043.incomingTargetCount043='1'
if(Test-LifeBoundary043 -Actor $incoming043 -Epoch $actor043.materializedAtNanos -OtherNativeHumans 0){throw 'Actual incoming combat accepted as safe.'}
@{contract='RUNNER043_SOLE_OBSERVER_BOUNDARY';actualA1Red=$true;sampleNanos=$frame043.sampleNanos;profileId=189;greenSameFrame=$true;negativeStaleEpoch=$true;negativeForeignNativeHold=$true;negativeIncomingCombat=$true;productionEdits=$false;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'evidence/RUNNER_BOUNDARY043.json') -Encoding utf8
'RUNNER_BOUNDARY043 PASS: actual A1 RED -> same-frame GREEN; stale/foreign/incoming negatives.'
