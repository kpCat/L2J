[CmdletBinding()]
param([ValidateSet('Probe','Discovery','Scene','Evaluate')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [string]$FrozenSha='',[string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{},[hashtable]$SetupMove=@{},
      [switch]$CollectSealed,[long[]]$ProbeProfileIds=@(452),[long[]]$OriginalProfileIds=@(),[switch]$NaturalCohort)
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if($Mode -ceq 'Evaluate'){
    $allowed027=[IO.Path]::GetFullPath((Join-Path $task027 'evidence'))+[IO.Path]::DirectorySeparatorChar
    if(-not [IO.Path]::GetFullPath($OutputRoot).StartsWith($allowed027,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact read-only cohort evidence scope.'}
    if(Test-Path (Join-Path $OutputRoot 'interrupted-cohort-result.json')){throw 'Immutable interrupted evaluation already exists.'}
    $samples=@(Get-Content (Join-Path $OutputRoot 'all-samples.json') -Raw | ConvertFrom-Json)
    $baseline=@(Get-Content (Join-Path $OutputRoot 'baseline-cohort.json') -Raw | ConvertFrom-Json)
    $primary=@(Get-Content (Join-Path $OutputRoot 'primary.json') -Raw | ConvertFrom-Json)
    if($baseline.Count -ne 8 -or $samples.Count -lt 2){throw 'Exact enrolled eight/sample inputs required.'}
    $watch=[pscustomobject]@{Elapsed=[pscustomobject]@{TotalSeconds=[double]$samples[-1].elapsedSeconds}}
    $final=@($samples[-1].actors)
    $scene027=[IO.File]::ReadAllText((Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Observe-Scene024.ps1'))
    $start027=$scene027.IndexOf('    $tail=@($samples')
    $end027=$scene027.IndexOf('    $rows | ConvertTo-Json', $start027)
    if($start027 -lt 0 -or $end027 -le $start027){throw 'Existing evaluator source shape changed.'}
    $evaluator027=$scene027.Substring($start027,$end027-$start027)
    $evaluator027=$evaluator027.Replace('if($seen.Count -ne 1){$missing++; continue}', 'if($seen.Count -ne 1 -or $seen[0].missing -ceq ''true'' -or $seen[0].worldPresent -cne ''true''){$missing++; continue}')
    $evaluator027=$evaluator027.Replace('$pass=$same -and $missing -eq 0', '$pass=$same -and $last[0].dead -ceq ''false'' -and $last[0].worldPresent -ceq ''true'' -and $missing -eq 0')
    . ([scriptblock]::Create($evaluator027))
    $rows | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'interrupted-cohort-result.json') -Encoding utf8
    [ordered]@{source='UNCHANGED026_EVALUATOR_ON_PRESERVED_INTERRUPTED_SAMPLES';frozenSha=$FrozenSha;elapsedSeconds=$watch.Elapsed.TotalSeconds;cohortCount=$baseline.Count;passingRows=@($rows | Where-Object pass).Count;farmPass=$false;sceneComplete=$false;reason='UNCERTAIN_MAILBOX_NO_REPLAY';primaryIds=@($primary.profileId)} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'interrupted-result.json') -Encoding utf8
    $rows | Select-Object profileId,primary,sameEpoch,cycles,rewards,targetTransitions,tail120Rewards,maxIdleSeconds,pass | Format-Table -AutoSize
    return
}
$source027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/Observe026.ps1'
$body027=[IO.File]::ReadAllText($source027).Replace('026','027')
$body027=$body027.Replace('PHANTOM-M1-SUSTAINED-FARM-027','PHANTOM-M1-LIFECYCLE-COMPLETION-027')
$body027=$body027.Replace("@('c','d')", "@('c','d','g')")
if($NaturalCohort){
    if($OriginalProfileIds.Count -or $Mode -cne 'Scene' -or $Episode -notin @('c','d','g')){throw 'Natural initial cohort requires its own c/d/g Scene.'}
    # Sentinel enables the unchanged full-cohort evaluator. First visible census binds the actual IDs before any counter outcome.
    $OriginalProfileIds=@(-1,-2,-3,-4,-5,-6,-7,-8)
    $PSBoundParameters['OriginalProfileIds']=$OriginalProfileIds
    $PSBoundParameters.Remove('NaturalCohort') | Out-Null
    $selection027=@'
$visible027=@(VisibleCensus027)
    if(-not $script:fullStarted027){
        $enrollmentWatch027=[Diagnostics.Stopwatch]::StartNew()
        while($visible027.Count -lt 8 -and $enrollmentWatch027.Elapsed.TotalSeconds -lt 60){
            Start-Sleep -Seconds 2
            $visible027=@(VisibleCensus027)
        }
        $enrolled027=@($visible027 | Sort-Object {[long]$_.profileId} | Select-Object -First 8)
        if($enrolled027.Count -ne 8){throw "ENVIRONMENT_GAP: initial natural cohort count=$($enrolled027.Count)"}
        $OriginalProfileIds=@($enrolled027.profileId)
        $enrolled027 | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'baseline-enrollment.json') -Encoding utf8
    }
'@
    $body027=$body027.Replace('$visible027=@(VisibleCensus027)',$selection027)
}
$body027=$body027.Replace('$taskRoot027=$PSScriptRoot','$taskRoot027=$task027').Replace('(Join-Path $PSScriptRoot','(Join-Path $task027')
& ([scriptblock]::Create($body027)) @PSBoundParameters
