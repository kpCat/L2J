[CmdletBinding()]
param([Parameter(Mandatory)][string]$SceneRoot)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($SceneRoot)
$result=Get-Content (Join-Path $root 'result.json') -Raw | ConvertFrom-Json
$baseline=@(Get-Content (Join-Path $root 'baseline-cohort.json') -Raw | ConvertFrom-Json)
$source=@(Get-Content (Join-Path $root 'all-samples.json') -Raw | ConvertFrom-Json)
$samples=@(foreach($sample in $source){
    $actors=@(foreach($actor in $sample.actors){
        [ordered]@{profile=[long]$actor.profileId;object=[int]$actor.objectId;epoch=[long]$actor.nativeEvidenceEpoch;
            damage=[long]$actor.nativeDamageSequence;kills=[long]$actor.nativeKillSequence;rewards=[long]$actor.nativeRewardSequence;
            cycles=[long]$actor.nativeFarmCycleSequence;targets=[long]$actor.nativeTargetSequence;
            exp=[long]$actor.exp;sp=[long]$actor.sp;loot=[long]$actor.nativeLootSequence;
            overflow=($actor.nativeEvidenceOverflow -ceq 'true');incident=([string]$actor.nativeFirstIncident+[string]$actor.cleanupIncidentId)}
    })
    [ordered]@{elapsed=[double]$sample.elapsedSeconds;actors=$actors}
})
# Preserve ALL source actors/samples, including missing or later extra actors; no success filter.
$document=[ordered]@{schema=1;code_sha=$result.frozenSha;natural_scene=$true;npc_ai=$true;
    cohort_ids=@($baseline | ForEach-Object {[long]$_.profileId});primary_ids=@($result.primaryIds | ForEach-Object {[long]$_});samples=$samples}
$path=Join-Path $root 'evaluator-input.json'
$document | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $path -Encoding utf8
& python (Join-Path $PSScriptRoot 'tools/verify_cohort.py') $path > (Join-Path $root 'provided-evaluator-result.json')
"READ_ONLY_EVALUATOR_EXIT=$LASTEXITCODE; raw $($source.Count) samples retained; failed gate stays failed"
exit $LASTEXITCODE
