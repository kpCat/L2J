[CmdletBinding()]
param([ValidateSet('Probe','Scene','Away')][string]$Mode='Probe',
      [ValidateSet('b','c','d','e','f','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha,
      [hashtable]$SetupTeleport=@{}, [string]$PathJson='', [string[]]$PreviousPrimaryIds=@())
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract028$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if(-not $OutputRoot.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $OutputRoot)){throw 'Immutable own TASK028 evidence path required.'}
if($Mode -ceq 'Scene' -and ($Seconds -lt 360 -or $Seconds -gt 420)){throw 'Final scene must be 360..420 seconds.'}
if($Mode -ceq 'Probe' -and ($Seconds -lt 60 -or $Seconds -gt 90)){throw 'Probe must be 60..90 seconds.'}
if($Mode -ceq 'Away' -and (-not $PathJson)){throw 'Dedicated dry native path required before away episode.'}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $FrozenSha -or $manifest.databaseName -cne "l2jmobiush5_localplay_contract028$Episode"){throw 'Frozen SHA/exact clone mismatch.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D')
$script:commands=0
$started=$false; $heartbeatJob=$null; $fullStarted=$false
$watch=[Diagnostics.Stopwatch]::StartNew()
$fullRoot=Join-Path $OutputRoot 'full-native'
$stopWriter=Join-Path $OutputRoot 'heartbeat-stop.request'
function Capture028([string]$Operation,[hashtable]$Arguments=@{}){
    if($script:commands -ge 350){throw 'Planned command budget exhausted; no cap extension.'}
    $script:commands++
    $label=('{0:D4}-{1}' -f $script:commands,$Operation)
    try{
        $raw=& (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -Arguments $Arguments -RunId $run -ActorMode Synthetic -ExternalHeartbeat -TimeoutSeconds 20
        $result=$raw | ConvertFrom-Json
        $raw | Set-Content (Join-Path $OutputRoot "$label.json") -Encoding utf8
        if($result.runId -cne $run){throw 'Exact run mismatch.'}
        if($result.status -notin @('SUCCEEDED','ACCEPTED')){throw "CONTROL_REJECTED:$($result.status):$($result.reason)"}
        return $result
    }catch{
        $_ | Out-String | Set-Content (Join-Path $OutputRoot "$label-error.txt") -Encoding utf8
        # Exact request remains in its mailbox. No retry or replacement UUID is issued.
        throw
    }
}
function Discover028{
    $actors=[Collections.Generic.List[object]]::new(); $after=0L
    for($page=0;$page -lt 8;$page++){
        $r=Capture028 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        for($i=1;$i -le [int]$r.candidate.censusCount;$i++){
            $prefix="census$i."; $fields=[ordered]@{}
            foreach($property in $r.candidate.PSObject.Properties){if($property.Name.StartsWith($prefix)){$fields[$property.Name.Substring($prefix.Length)]=$property.Value}}
            $actors.Add([pscustomobject]$fields)
        }
        if(-not $r.candidate.PSObject.Properties['censusNextProfileId'] -or [long]$r.candidate.censusNextProfileId -le $after){return @($actors)}
        $after=[long]$r.candidate.censusNextProfileId
    }
    throw 'Bounded discovery page limit; no silent truncation.'
}
function ReadFrame028{
    $frame=Get-Content -LiteralPath (Join-Path $fullRoot 'full-frame-latest.json') -Raw | ConvertFrom-Json
    if($frame.observer.runId -cne $run -or $frame.observer.sessionState -cne 'RUNNING' -or -not $frame.observer.present -or [int]$frame.observer.objectId -ne 268492939){throw 'Native observer identity lost; episode ends.'}
    return $frame
}
function WaitArrival028([hashtable]$Point){
    $arrival=[Diagnostics.Stopwatch]::StartNew()
    do{
        $frame=ReadFrame028
        $distance=[Math]::Sqrt([Math]::Pow(([double]$frame.observer.x-[double]$Point.x),2)+[Math]::Pow(([double]$frame.observer.y-[double]$Point.y),2))
        if($distance -le 32 -and [Math]::Abs([int]$frame.observer.z-[int]$Point.z) -le 48 -and -not $frame.observer.moving){
            $frame | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot ('arrival-'+$script:commands+'.json')) -Encoding utf8
            return
        }
        Start-Sleep -Milliseconds 250
    }while($arrival.Elapsed.TotalSeconds -lt 12)
    throw 'ACCEPTED_NOT_ARRIVED: factual native arrival missing; no MOVE replay.'
}
try{
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true; $watch.Restart()
    $heartbeatJob=Start-Job -ArgumentList $runtime,$run,$OutputRoot,$stopWriter -ScriptBlock {
        param($Runtime,$Run,$Output,$Stop)
        $ErrorActionPreference='Stop'
        . (Join-Path $Runtime 'LocalPlay-Pilot.ps1')
        $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $Run
        $incarnation=$context.StartTimeUtcTicks
        $clock=[Diagnostics.Stopwatch]::StartNew()
        try{
            while($clock.Elapsed.TotalSeconds -lt 525 -and -not (Test-Path -LiteralPath $Stop)){
                $process=Get-Process -Id $context.Pid -ErrorAction Stop
                if($process.StartTime.ToUniversalTime().Ticks -ne $incarnation){throw 'Heartbeat PID incarnation changed.'}
                Write-PilotHeartbeat $context $Run $Run
                "$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())`t$Run`t$($context.Pid)`t$incarnation`t$($clock.Elapsed.TotalSeconds)" | Add-Content (Join-Path $Output 'heartbeat-writer.tsv') -Encoding utf8
                Start-Sleep -Seconds 5
            }
        }catch{$_ | Out-String | Set-Content (Join-Path $Output 'heartbeat-failure.txt') -Encoding utf8;throw}
    }
    $writerReady=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path (Join-Path $OutputRoot 'heartbeat-writer.tsv'))){
        if($writerReady.Elapsed.TotalSeconds -gt 10 -or $heartbeatJob.State -ceq 'Failed'){throw 'Independent heartbeat did not start.'}
        Start-Sleep -Milliseconds 100
    }
    $status=Capture028 'STATUS'
    if($status.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $status.before.clientIdentity -cne 'none' -or $status.before.worldPresent -cne 'true'){throw 'Native synthetic identity unverified.'}
    if($SetupTeleport.Count){
        $null=Capture028 'TELEPORT_SELF' $SetupTeleport
        $status=Capture028 'STATUS'
        if($status.before.teleporting -cne 'false' -or [Math]::Abs([int]$status.before.x-[int]$SetupTeleport.x) -gt 32 -or [Math]::Abs([int]$status.before.y-[int]$SetupTeleport.y) -gt 32){throw 'Pre-baseline setup arrival unconfirmed.'}
    }
    # Fixed enrollment time. No rewards-based spot or actor selection.
    $enrollment=[Diagnostics.Stopwatch]::StartNew()
    while($enrollment.Elapsed.TotalSeconds -lt 60){
        if($heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat writer failed during enrollment.'}
        Start-Sleep -Seconds 1
    }
    $baseline=@(Discover028 | Where-Object {$_.worldPresent -ceq 'true'} | Sort-Object {[long]$_.profileId})
    if($baseline.Count -lt 4 -or $baseline.Count -gt 8){throw "NATURAL_COHORT_COUNT:$($baseline.Count); expected4..8"}
    $baseline | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'baseline-cohort.json') -Encoding utf8
    $suitable=@($baseline | Where-Object {$_.dead -ceq 'false' -and [int]$_.npcId -gt 0})
    $different=@($suitable | Where-Object {$_.profileId -notin $PreviousPrimaryIds})
    $primary=@($(if($different.Count -ge 2){$different}else{$suitable}) | Select-Object -First 2)
    if($primary.Count -ne 2){throw 'Two suitable primaries unavailable before outcomes.'}
    $primary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'primary.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Control028.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'enrollment-census') *> (Join-Path $OutputRoot 'census-attach.log')
    if($LASTEXITCODE -ne 0){throw 'One-shot World/capacity census failed.'}
    & (Join-Path $PSScriptRoot 'Control028.ps1') -Action Collector -Mode FullObserve -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot $fullRoot -ObserverRunId $run *> (Join-Path $OutputRoot 'full-attach.log')
    if($LASTEXITCODE -ne 0){throw 'Existing FullObserve attach failed.'}
    $fullStarted=$true
    $frameReady=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path (Join-Path $fullRoot 'full-frame-latest.json'))){if($frameReady.Elapsed.TotalSeconds -gt 5){throw 'Full native first frame missing.'};Start-Sleep -Milliseconds 100}
    $first=ReadFrame028
    if($Mode -ceq 'Probe'){
        $origin=@{x=[int]$first.observer.x;y=[int]$first.observer.y;z=[int]$first.observer.z}
        $point=@{x=$origin.x-200;y=$origin.y;z=$origin.z}
        $move=Capture028 'MOVE_SELF' $point
        if($move.status -cne 'ACCEPTED'){throw 'Probe MOVE admission missing.'}
        WaitArrival028 $point
        $return=Capture028 'MOVE_SELF' $origin
        if($return.status -cne 'ACCEPTED'){throw 'Probe return admission missing.'}
        WaitArrival028 $origin
    }
    if($Mode -ceq 'Away'){throw 'Dedicated native away path phase not yet implemented; no manual Phantom action.'}
    $first=ReadFrame028
    $sequenceBefore=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    $observation=[Diagnostics.Stopwatch]::StartNew(); $lastNanos=[long]$first.sampleNanos; $maxGap=0.0; $unique=0; $lastFresh=0.0
    $samples=[Collections.Generic.List[object]]::new()
    while($observation.Elapsed.TotalSeconds -lt $Seconds){
        if($watch.Elapsed.TotalSeconds -gt 480){throw 'Episode budget480s exhausted; TTL not extended.'}
        if($heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat writer failed.'}
        $session=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
        if($session.state -cne 'RUNNING'){throw "NATIVE_SESSION_CLOSED:$($session.reason)"}
        $frame=ReadFrame028
        if([long]$frame.sampleNanos -gt $lastNanos){
            $gap=([long]$frame.sampleNanos-$lastNanos)/1e9; $maxGap=[Math]::Max($maxGap,$gap)
            if($gap -gt 5){throw "TELEMETRY_GAP:$gap"}
            $lastNanos=[long]$frame.sampleNanos; $unique++; $lastFresh=$observation.Elapsed.TotalSeconds
            $samples.Add([pscustomobject]@{elapsedSeconds=$observation.Elapsed.TotalSeconds;sampleNanos=$frame.sampleNanos;observer=$frame.observer;actors=$frame.actors})
        }
        if($observation.Elapsed.TotalSeconds-$lastFresh -gt 5){throw 'TELEMETRY_STALE: no fresh native frame for5s.'}
        Start-Sleep -Milliseconds 500
    }
    $samples | ConvertTo-Json -Depth 14 | Set-Content (Join-Path $OutputRoot 'all-samples.json') -Encoding utf8
    $sequenceAfter=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    [ordered]@{kind=$Mode;sha=$FrozenSha;runId=$run;seconds=$observation.Elapsed.TotalSeconds;nativeUniqueSamples=$unique;maxGapSeconds=$maxGap;commandCount=$script:commands;baselineCount=$baseline.Count;primaryIds=@($primary.profileId);sameSession=$true;telemetryMailboxCommands=([long]$sequenceAfter.nextSequence-[long]$sequenceBefore.nextSequence);sequenceBefore=[long]$sequenceBefore.nextSequence;sequenceAfter=[long]$sequenceAfter.nextSequence;arrivalProof=($Mode -ceq 'Probe')} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'capture-result.json') -Encoding utf8
    "TASK028_CAPTURE_COMPLETE mode=$Mode cohort=$($baseline.Count) samples=$unique gap=$maxGap commands=$script:commands"
}catch{
    $_ | Out-String | Set-Content (Join-Path $OutputRoot 'episode-failure.txt') -Encoding utf8
    throw
}finally{
    if($started){
        try{
            & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8
            $finalState=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
            $finalState | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'final-synthetic-state.json') -Encoding utf8
            if($finalState.state -cne 'STOPPED'){throw 'Synthetic cleanup not confirmed.'}
        }finally{
            [IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false))
            if($heartbeatJob){$heartbeatJob | Wait-Job -Timeout 10 | Out-Null; Receive-Job $heartbeatJob *> (Join-Path $OutputRoot 'heartbeat-job.log'); Remove-Job $heartbeatJob -Force}
        }
    }
    if($fullStarted){& (Join-Path $PSScriptRoot 'Control028.ps1') -Action Collector -Mode Flush -Episode $Episode -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'full-flush.log')}
}
