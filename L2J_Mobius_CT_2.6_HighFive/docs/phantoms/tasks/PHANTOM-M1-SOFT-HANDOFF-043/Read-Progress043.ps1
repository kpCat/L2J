param([string]$Label='LIFE_A1_043')
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
. (Join-Path $module043 '.phantom-local/ops043/Read-SharedJson032.ps1')
$scene043=Join-Path $PSScriptRoot "evidence/$Label"
$frame043=Join-Path $scene043 'full-native/full-frame-latest.json'
if(-not (Test-Path $frame043)){'Native enrollment/frame pending.'; return}
$frame=Read-SharedJson032 $frame043
$primary043=Join-Path $scene043 'primary.json'
if(Test-Path $primary043){$ids043=@((Read-SharedJson032 $primary043).profileId)}else{$ids043=@()}
$actors043=@($frame.actors | Where-Object {$_.profileId -cin $ids043} | Select-Object profileId,materializedAtNanos,worldPresent,dead,hp,mp,inCombat,incomingTargetCount043,incomingRoster043,liveAutoPlayTickReason,nativeFarmCycleSequence,nativeDamageSequence,physicalDemand)
@{sampleNanos=$frame.sampleNanos;proofFailure=$frame.proofFailure;observer=$frame.observer;primary=$actors043} | ConvertTo-Json -Depth 6
