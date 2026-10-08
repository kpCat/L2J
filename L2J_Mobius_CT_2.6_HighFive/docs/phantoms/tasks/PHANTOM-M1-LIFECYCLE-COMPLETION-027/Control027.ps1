[CmdletBinding()]
param([ValidateSet('Update','Start','Stop','Export','Check','Collector','Matrix','Proof')][string]$Action='Collector',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',[string]$OutputRoot='',
      [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Observe','FullObserve','Census','Recovery','Flush','CrashNative','CrashFinalize')][string]$Mode='Build',
      [string]$CohortJson='', [string]$Suite='PhantomCheckpointRecovery025Suite',
      [string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [string]$Label='', [string]$LauncherId='', [switch]$ResetFixture,
      [ValidateSet('Persistence','Restart')][string]$ProofKind='Restart',
      [string]$SqlRoot='',[string]$SealedRoot='',[string]$ShutdownLog='',[string]$CrashReceipt='')
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if($Action -ceq 'Proof'){
    $allowed027=[IO.Path]::GetFullPath((Join-Path $task027 'evidence'))+[IO.Path]::DirectorySeparatorChar
    foreach($path027 in @($CohortJson,$SqlRoot,$SealedRoot,$OutputRoot)){
        if(-not $path027 -or -not [IO.Path]::GetFullPath($path027).StartsWith($allowed027,[StringComparison]::OrdinalIgnoreCase)){throw 'TASK027 proof exact evidence scope.'}
    }
    if(Test-Path $OutputRoot){throw 'Immutable proof already exists.'}
    $name027=if($ProofKind -ceq 'Persistence'){'Build-PersistenceProof024.py'}else{'Verify-Restart024.py'}
    $validator027=[IO.File]::ReadAllText((Join-Path $module027 "docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/$name027")).Replace('contract024','contract027').Replace('TASK024_CONTRACT','TASK027_CONTRACT')
    $arguments027=@('--cohort',$CohortJson,'--sealed',$SealedRoot,'--sql',$SqlRoot,'--output',$OutputRoot)
    if($CrashReceipt){
        if($ProofKind -cne 'Restart' -or -not [IO.Path]::GetFullPath($CrashReceipt).StartsWith($allowed027,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK027 crash receipt scope.'}
        $arguments027+=@('--crash-receipt',$CrashReceipt)
    }
    if($ProofKind -ceq 'Persistence'){$arguments027+=@('--shutdown-log',$ShutdownLog,'--allow-current-epoch-transition')}
    # Reuse the existing validator through stdin; no new helper or weaker field comparison.
    $validator027 | & python - @arguments027
    exit $LASTEXITCODE
}
if($Action -ceq 'Matrix'){
    if($Episode -cne 'a' -or $Engine -cne 'Candidate' -or $ExpectedSha -notmatch '^[0-9a-f]{40}$' -or $Label -notmatch '^[A-Za-z0-9_-]+$'){throw 'Exact own candidate TEST matrix guard.'}
    $actual027=& git -C (Split-Path $module027 -Parent) rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $actual027 -cne $ExpectedSha){throw 'Matrix committed SHA mismatch.'}
    $matrix027=Join-Path $task027 "$Label-matrix.tsv"
    if(Test-Path $matrix027){throw 'Immutable matrix output already exists.'}
    $routes027=@(
        @('PhantomCheckpointRecovery025Suite','',''),
        @('PhantomCheckpointDrain025Suite','',''),
        @('PhantomVisibleIntentRecoverySuite','',''),
        @('native','review',''),
        @('PhantomLivingWorld023Suite','retaliation024',''),
        @('','','population-ecology-goal033'),
        @('','','population-ecology-handoff-regression'),
        @('server-shutdown','',''),
        @('position','owned-store',''),
        @('PhantomNativeFarmContinuation022Suite','raw',''),
        @('PhantomLivingWorld023Suite','cooperative',''),
        @('PhantomNativeFarmContinuation022Suite','observation026',''),
        @('PhantomNativeFarmContinuation022Suite','callback026',''),
        @('PhantomSustainedFarm026Suite','',''),
        @('PhantomSustainedFarm026Suite','resource026',''),
        @('PhantomSustainedFarm026Suite','generation026',''),
        @('PhantomRecoveryBoundary026Suite','',''),
        @('PhantomNativeEvidenceContinuation022Suite','','')
    )
    # Cardinalities are confirmed by the R11 route XML/logs, including corrected owned-store.
    $counts027=@(8,3,10,2,3,30,6,8,1,3,1,2,2,3,2,2,2,4)
    "route`tsuite`tfocus`tlauncher`texitCode`tcodeSha`tsummary" | Set-Content $matrix027 -Encoding utf8
    $failed027=0
    for($index027=0;$index027 -lt $routes027.Count;$index027++){
        $route027=$routes027[$index027]; $routeLabel027=('{0}_{1:D2}' -f $Label,($index027+1))
        $arguments027=@('-NoProfile','-File',(Join-Path $task027 'Control027.ps1'),'-Action','Check','-Episode','a','-Engine','Candidate','-Label',$routeLabel027)
        if($route027[0]){$arguments027+=@('-Suite',$route027[0])}
        if($route027[1]){$arguments027+=@('-Focus',$route027[1])}
        if($route027[2]){$arguments027+=@('-LauncherId',$route027[2])}
        & pwsh @arguments027 *> (Join-Path $task027 "$routeLabel027-launch.log")
        $exit027=$LASTEXITCODE
        $summary027=(Select-String -LiteralPath (Join-Path $task027 "$routeLabel027.log") -Pattern '^SUMMARY:' | Select-Object -Last 1).Line
        if(-not $summary027){$summary027='INVALID:no SUMMARY'}
        if($summary027 -notmatch 'total=(\d+) passed=(\d+) failed=(\d+)' -or [int]$Matches[1] -ne $counts027[$index027]){$summary027='INVALID:route cardinality '+$summary027}
        if($exit027 -ne 0 -or $summary027 -match 'INVALID'){$failed027++}
        "$($index027+1)`t$($route027[0])`t$($route027[1])`t$($route027[2])`t$exit027`t$ExpectedSha`t$summary027" | Add-Content $matrix027 -Encoding utf8
        Write-Output "TASK027_MATRIX $($index027+1)/18 exit=$exit027 $summary027"
    }
    if($failed027){exit 1}else{exit 0}
}
$source027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/Control026.ps1'
$body027=[IO.File]::ReadAllText($source027).Replace('026','027')
$body027=$body027.Replace('PHANTOM-M1-SUSTAINED-FARM-027','PHANTOM-M1-LIFECYCLE-COMPLETION-027')
$body027=$body027.Replace('$taskRoot027=$PSScriptRoot','$taskRoot027=$task027').Replace('(Join-Path $PSScriptRoot','(Join-Path $task027')
$body027=$body027.Replace('6ebe1d93f4ee168cd8952f0416dc920f26c430ac','882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf')
& ([scriptblock]::Create($body027)) @PSBoundParameters
