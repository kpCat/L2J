[CmdletBinding()]
param([ValidateSet('Probe','Discovery','Scene')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [string]$FrozenSha='',[string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{},[hashtable]$SetupMove=@{},
      [switch]$CollectSealed,[long[]]$ProbeProfileIds=@(452),[long[]]$OriginalProfileIds=@(),[switch]$NaturalCohort)
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
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
