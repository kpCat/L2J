param([Parameter(Mandatory)][string]$FramePath)
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$allowed043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
$resolved043=[IO.Path]::GetFullPath($FramePath)
if(-not $resolved043.StartsWith($allowed043,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK043 frame required.'}
. (Join-Path $module043 '.phantom-local/ops043/Read-SharedJson032.ps1')
$frame043=Read-SharedJson032 $resolved043
[pscustomobject]@{proofFailure=$frame043.proofFailure;sampleNanos=$frame043.sampleNanos;observer=$frame043.observer;
    actors=@($frame043.actors | Select-Object profileId,objectId,materializedAtNanos,worldPresent,missing,dead,
        nativeFarmCycleSequence,nativeRewardSequence,nativeDamageSequence,observerPrewarm,physicalDemand)} | ConvertTo-Json -Depth 5
