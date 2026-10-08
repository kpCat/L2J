[CmdletBinding()]
param([ValidateSet('ProbeStop','Lifecycle','SoftReturn')][string]$Action='ProbeStop',
      [ValidateSet('b','c','d','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$CodeSha,
      [int]$Seconds=180)
$ErrorActionPreference='Stop'
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime027=Join-Path $module027 ".phantom-local/contract027$Episode/runtime"
$out027=[IO.Path]::GetFullPath($OutputRoot)
$allowed027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
if(-not $out027.StartsWith($allowed027,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $out027)){throw 'Immutable TASK027 evidence required.'}
$manifest027=Get-Content (Join-Path $runtime027 'local-play.json') -Raw | ConvertFrom-Json
if($manifest027.codeSha -cne $CodeSha -or $manifest027.databaseName -cne "l2jmobiush5_localplay_contract027$Episode"){throw 'Exact code/database mismatch.'}
if($Action -ceq 'SoftReturn'){
    if($Episode -cne 'g'){throw 'Independent native soft-return is restricted to own027g.'}
    $taskRoot027=$PSScriptRoot
    $legacy027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
    $walk027=[IO.File]::ReadAllText((Join-Path $legacy027 'Observe-Walk024.ps1'))
    $walk027=$walk027.Replace("& (Join-Path `$PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot `$runtime", "& (Join-Path `$taskRoot027 'Control027.ps1') -Action Export -Episode g")
    $walk027=$walk027.Replace('$PSScriptRoot','$legacy027').Replace('contract024','contract027')
    $walk027=$walk027.Replace('$counter=0','$script:counter=0')
    & ([scriptblock]::Create($walk027)) -RuntimeRoot $runtime027 -OutputRoot $out027 -OutsideX 38000
    exit $LASTEXITCODE
}
if($Action -ceq 'Lifecycle'){
    if($Episode -cne 'g'){throw 'Native severe encounter is restricted to own027g.'}
    $taskRoot027=$PSScriptRoot
    $legacy027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
    $ops027=Join-Path $module027 '.phantom-local/ops027'
    # Reuse the existing polling fixture, including stock NPC stats and exact cleanup.
    # No event listener with disk output is installed; no phantom command is issued.
    $agent027=[IO.File]::ReadAllText((Join-Path $legacy027 'Contract024DeathAgent.java')).Replace('024','027').Replace('PHANTOM-M1-RUNTIME-CONTRACTS-027','PHANTOM-M1-LIFECYCLE-COMPLETION-027')
    $agentPath027=Join-Path $ops027 'Contract027DeathAgent.java'
    [IO.File]::WriteAllText($agentPath027,$agent027,[Text.UTF8Encoding]::new($false))
    $builder027=[IO.File]::ReadAllText((Join-Path $legacy027 'Build-Death024.ps1')).Replace('024','027').Replace("(Join-Path `$PSScriptRoot 'Contract027DeathAgent.java')",'$agentPath027').Replace('$PSScriptRoot','$taskRoot027')
    & ([scriptblock]::Create($builder027))
    function InvokeNativeDeath027([string]$RuntimeRoot,[string]$CohortJson,[string]$OutputRoot){
        $arm027=[IO.File]::ReadAllText((Join-Path $legacy027 'Arm-Death024.ps1')).Replace('024','027').Replace('contract027c','contract027g')
        $arm027=$arm027.Replace('(Join-Path $PSScriptRoot','(Join-Path $taskRoot027')
        $attach027=Join-Path $legacy027 'AttachContract024.java'
        $arm027=$arm027.Replace('-cp $ops AttachContract027','-cp $ops $attach027')
        $arm027=$arm027.Replace("& (Join-Path `$taskRoot027 'Export-Persistence027.ps1') -RuntimeRoot `$runtime -CohortJson (Join-Path `$output 'selected.json') -OutputRoot (Join-Path `$output 'before-sql') | Out-Null", "& (Join-Path `$taskRoot027 'Control027.ps1') -Action Export -Episode g -ProfileIds @(`$actors.profileId) -OutputRoot (Join-Path `$output 'before-sql') | Out-Null")
        & ([scriptblock]::Create($arm027)) -RuntimeRoot $RuntimeRoot -CohortJson $CohortJson -OutputRoot $OutputRoot
    }
    $lifecycle027=[IO.File]::ReadAllText((Join-Path $legacy027 'Observe-Lifecycle024.ps1')).Replace('024','027')
    $lifecycle027=$lifecycle027.Replace('$counter=0','$script:counter=0').Replace('$observer=$null','$script:observer=$null')
    $lifecycle027=$lifecycle027.Replace("& (Join-Path `$PSScriptRoot 'Arm-Death027.ps1')",'InvokeNativeDeath027')
    & ([scriptblock]::Create($lifecycle027)) -RuntimeRoot $runtime027 -OutputRoot $out027 -FrozenSha $CodeSha -Seconds 200
    exit $LASTEXITCODE
}
New-Item -ItemType Directory -Path $out027 | Out-Null
$run027=[guid]::NewGuid().ToString('D')
$run027 | Set-Content (Join-Path $out027 'run-id.txt') -Encoding utf8
$started027=$false
$stopped027=$false
$counter027=0
function Read027([string]$Operation,[hashtable]$Arguments=@{}){
    $raw027=& (Join-Path $runtime027 'Invoke-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run027 -Operation $Operation -Arguments $Arguments -TimeoutSeconds 10
    $value027=$raw027 | ConvertFrom-Json -DateKind String
    $script:counter027++
    $raw027 | Set-Content (Join-Path $out027 ('{0:D4}-{1}.json' -f $script:counter027,$Operation)) -Encoding utf8
    if($value027.status -cne 'SUCCEEDED' -or $value027.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $value027.before.clientIdentity -cne 'none'){throw 'Exact synthetic read rejected.'}
    foreach($key027 in @('x','y','z','instanceId','moving','targetId','teleporting')){if($value027.before.$key027 -cne $value027.after.$key027){throw 'Observer read mutated state.'}}
    return $value027
}
try{
    & (Join-Path $runtime027 'Start-LocalPlaySynthetic.ps1') -RunId $run027 | Set-Content (Join-Path $out027 'synthetic-start.json') -Encoding utf8
    $started027=$true
    $null=Read027 'STATUS'
    $watch027=[Diagnostics.Stopwatch]::StartNew()
    $cohort027=@()
    do{
        $rows027=[Collections.Generic.List[object]]::new()
        $cursor027=0L
        for($page027=0;$page027 -lt 8;$page027++){
            $value027=Read027 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$cursor027"}
            for($index027=1;$index027 -le [int]$value027.candidate.censusCount;$index027++){
                $prefix027="census$index027."
                $row027=[ordered]@{}
                foreach($property027 in $value027.candidate.PSObject.Properties){if($property027.Name.StartsWith($prefix027)){$row027[$property027.Name.Substring($prefix027.Length)]=$property027.Value}}
                $rows027.Add([pscustomobject]$row027)
            }
            if(-not $value027.candidate.PSObject.Properties['censusNextProfileId']){break}
            $next027=[long]$value027.candidate.censusNextProfileId
            if($next027 -le $cursor027){break}
            $cursor027=$next027
        }
        $cohort027=@($rows027 | Sort-Object {[long]$_.profileId} | Select-Object -First 8)
        if($cohort027.Count){break}
        Start-Sleep -Seconds 2
    }while($watch027.Elapsed.TotalSeconds -lt 60)
    if(-not $cohort027.Count){throw 'ENVIRONMENT_GAP:no naturally visible lifetime.'}
    $cohort027 | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $out027 'baseline-cohort.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Control027.ps1') -Action Collector -Mode Observe -Episode $Episode -CohortJson (Join-Path $out027 'baseline-cohort.json') -OutputRoot (Join-Path $out027 'native') *> (Join-Path $out027 'attach.log')
    if($LASTEXITCODE -ne 0){throw 'Exact observer attach failed.'}
    $pending027=$false
    do{
        $trace027=Join-Path $out027 'native/event-dispatch.tsv'
        if(Test-Path $trace027){
            $latest027=Get-Content $trace027 -Tail 16
            if(@($latest027 | Where-Object {$_ -match 'ON_ATTACKABLE_KILL:SUBMITTED' -and $_ -match 'executorEntered=0:start=0' -and $_ -match 'dueMs=([1-9][0-9]*)'}).Count -gt 0){$pending027=$true;break}
        }
        if(([int]$watch027.Elapsed.TotalSeconds % 5) -eq 0){$null=Read027 'STATUS'}
        Start-Sleep -Milliseconds 250
    }while($watch027.Elapsed.TotalSeconds -lt $Seconds)
    [ordered]@{utc=[DateTime]::UtcNow.ToString('O');codeSha=$CodeSha;pendingOriginalNativeKill=$pending027;elapsedSeconds=$watch027.Elapsed.TotalSeconds;cohortCount=$cohort027.Count;cohortIds=@($cohort027.profileId);dumpPause=$false} | ConvertTo-Json | Set-Content (Join-Path $out027 'before-stop.json') -Encoding utf8
    & (Join-Path $runtime027 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run027 *> (Join-Path $out027 'synthetic-stop.log')
    $started027=$false
    & (Join-Path $PSScriptRoot 'Control027.ps1') -Action Stop -Episode $Episode *> (Join-Path $out027 'first-stock-stop.log')
    if($LASTEXITCODE -ne 0){throw 'Owned graceful stop failed.'}
    $stopped027=$true
    & (Join-Path $PSScriptRoot 'Control027.ps1') -Action Export -Episode $Episode -ProfileIds @($cohort027.profileId) -OutputRoot (Join-Path $out027 'after-stop-natural') *> (Join-Path $out027 'export-natural.log')
    if($LASTEXITCODE -ne 0){throw 'Post-stop durable export failed.'}
    & (Join-Path $PSScriptRoot 'Control027.ps1') -Action Export -Episode $Episode -ProfileIds @(110,142,175,260,275,278,404,447) -OutputRoot (Join-Path $out027 'after-stop-longitudinal') *> (Join-Path $out027 'export-longitudinal.log')
    if($LASTEXITCODE -ne 0){throw 'Longitudinal durable export failed.'}
    'TASK027_FIRST_PROBE_FINISHED processExitOnly=true;healthyDrainRequiresLogAndNativeProof=true'
}finally{
    if($started027 -and -not $stopped027){
        & (Join-Path $runtime027 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run027 *> (Join-Path $out027 'synthetic-stop.log')
    }
}
