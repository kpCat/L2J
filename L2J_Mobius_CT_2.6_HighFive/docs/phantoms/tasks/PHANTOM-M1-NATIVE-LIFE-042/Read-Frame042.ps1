param([Parameter(Mandatory)][string]$FramePath)
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$allowed042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
$resolved042=[IO.Path]::GetFullPath($FramePath)
if(-not $resolved042.StartsWith($allowed042,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK042 frame required.'}
. (Join-Path $module042 '.phantom-local/ops042/Read-SharedJson032.ps1')
$frame042=Read-SharedJson032 $resolved042
[pscustomobject]@{proofFailure=$frame042.proofFailure;sampleNanos=$frame042.sampleNanos;observer=$frame042.observer;
    actors=@($frame042.actors | Select-Object profileId,objectId,materializedAtNanos,worldPresent,missing,dead,
        nativeFarmCycleSequence,nativeRewardSequence,nativeDamageSequence,observerPrewarm,physicalDemand)} | ConvertTo-Json -Depth 5
