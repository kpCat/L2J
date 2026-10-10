[CmdletBinding()]
param([ValidateSet('Probe','Scene','Away')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','t')][string]$Episode='a',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha,
      [ValidateRange(75,135)][int]$BackgroundSeconds=75, [hashtable]$InitialObservationPoint=@{}, [hashtable]$SetupTeleport=@{}, [switch]$SetupAtNearestCandidate, [string]$PathJson='', [string]$InitialSetupPathJson='', [string[]]$PreviousPrimaryIds=@(),[long]$SetupProfileId=0,[switch]$NativeStartAtSetup,[switch]$StopOwnServerOnComplete)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedJson032.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract032$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if(-not $OutputRoot.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $OutputRoot)){throw 'Immutable own TASK032 evidence path required.'}
if($Mode -ceq 'Scene' -and ($Seconds -lt 360 -or $Seconds -gt 420)){throw 'Final scene must be 360..420 seconds.'}
if($Mode -ceq 'Probe' -and ($Seconds -lt 60 -or $Seconds -gt 90)){throw 'Probe must be 60..90 seconds.'}
if($Mode -ceq 'Away' -and (-not $PathJson)){throw 'Dedicated dry native path required before away episode.'}
if($Mode -ceq 'Away'){
    $path=Get-Content -LiteralPath $PathJson -Raw | ConvertFrom-Json
    $helper=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDryPath023.java'
    if($path.helperSource){$helper=[IO.Path]::GetFullPath($path.helperSource);if($helper -cne (Join-Path $PSScriptRoot 'ReadDryRoute032.java') -and $helper -cne (Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDryPath023.java')){throw 'Exact checked dry helper required.'}}
    if(-not $path.bidirectional -or $path.helperSha256 -cne (Get-FileHash $helper).Hash -or $path.geoLogSha256 -cne (Get-FileHash $path.geoLog).Hash){throw 'Immutable native dry path provenance invalid.'}
    if($Seconds -lt 90 -or $Seconds -gt 160 -or $path.steps.Count -gt 40){throw 'Bounded away/return plan exceeds525s/400sequence budget.'}
}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $FrozenSha -or $manifest.databaseName -cne "l2jmobiush5_localplay_contract032$Episode"){throw 'Frozen SHA/exact clone mismatch.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D')
$script:commands=0
$started=$false; $heartbeatJob=$null; $fullStarted=$false; $serverStopped=$false; $syntheticStopped=$false
$watch=[Diagnostics.Stopwatch]::StartNew()
$fullRoot=Join-Path $OutputRoot 'full-native'
$stopWriter=Join-Path $OutputRoot 'heartbeat-stop.request'
function Capture032([string]$Operation,[hashtable]$Arguments=@{}){
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
function Discover032{
    $actors=[Collections.Generic.List[object]]::new(); $after=0L
    for($page=0;$page -lt 8;$page++){
        $r=Capture032 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
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
function ReadFrame032{
    $frame=Read-SharedJson032 (Join-Path $fullRoot 'full-frame-latest.json')
    if($frame.proofFailure){throw ('OBSERVER_PROOF_INVALID:'+$frame.proofFailure)}
    if($frame.observer.runId -cne $run -or $frame.observer.sessionState -cne 'RUNNING' -or -not $frame.observer.present -or -not $frame.observer.online -or $frame.observer.dead -or [int]$frame.observer.objectId -ne 268492939){throw 'Native observer identity lost; episode ends.'}
    return $frame
}
function ReadArrivalFrame032{
    if($Mode -cne 'Away'){return ReadFrame032}
    $native=Read-SharedJson032 (Join-Path $fullRoot 'arrival-pose-latest.json')
    if($native.proofFailure -or $native.observer.runId -cne $run -or $native.observer.sessionState -cne 'RUNNING' -or -not $native.observer.present -or -not $native.observer.online -or $native.observer.dead -or [int]$native.observer.objectId -ne 268492939){throw 'Exact native arrival pose identity lost.'}
    return $native
}
function WaitArrival032([hashtable]$Point){
    $arrival=[Diagnostics.Stopwatch]::StartNew()
    do{
        $frame=ReadArrivalFrame032
        $distance=[Math]::Sqrt([Math]::Pow(([double]$frame.observer.x-[double]$Point.x),2)+[Math]::Pow(([double]$frame.observer.y-[double]$Point.y),2))
        if($distance -le 32 -and [Math]::Abs([int]$frame.observer.z-[int]$Point.z) -le 48 -and -not $frame.observer.moving){
            $frame | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot ('arrival-'+$script:commands+'.json')) -Encoding utf8
            return
        }
        Start-Sleep -Milliseconds 100
    }while($arrival.Elapsed.TotalSeconds -lt 12)
    throw 'ACCEPTED_NOT_ARRIVED: factual native arrival missing; no MOVE replay.'
}
function MarkPhase032([string]$Phase){
    $frame=ReadFrame032
    @{phase=$Phase;sampleNanos=$frame.sampleNanos;elapsedFromStart=$watch.Elapsed.TotalSeconds;observer=$frame.observer;actors=$frame.actors} | ConvertTo-Json -Depth 14 -Compress | Add-Content (Join-Path $OutputRoot 'phase-markers.jsonl') -Encoding utf8
}
function Walk032([object[]]$Points){
    foreach($point in $Points){
        if($watch.Elapsed.TotalSeconds -gt 320){throw 'Away route budget exhausted before post-return proof.'}
        $frame=ReadArrivalFrame032
        $dx=[double]$point.x-[double]$frame.observer.x; $dy=[double]$point.y-[double]$frame.observer.y
        if([Math]::Sqrt($dx*$dx+$dy*$dy) -gt 300.01 -or [Math]::Abs([int]$point.z-[int]$frame.observer.z) -gt 200){throw 'Factual next dry step bound violated; no MOVE.'}
        $move=Capture032 'MOVE_SELF' @{x=[int]$point.x;y=[int]$point.y;z=[int]$point.z}
        if($move.status -cne 'ACCEPTED'){throw 'Away MOVE admission missing.'}
        WaitArrival032 @{x=[int]$point.x;y=[int]$point.y;z=[int]$point.z}
    }
}
try{
    # Install before Synthetic can cause any materialization/checkpoint. Epoch0 is not enrolled.
    @([pscustomobject]@{profileId=275;materializedAtNanos=0}) | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'collector-bootstrap.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Enroll -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'collector-bootstrap.log')
    if($LASTEXITCODE -ne 0){throw 'Prospective receipt collector installation failed.'}
    $startArguments=@{RunId=$run}
    if($NativeStartAtSetup){
        if($SetupProfileId -le 0 -or -not $SetupAtNearestCandidate -or $SetupTeleport.Count){throw 'One explicit readonly initial setup profile required'}
        $prestartRoot=Join-Path $OutputRoot 'global-pre-native-start'
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $prestartRoot *> (Join-Path $OutputRoot 'prestart-census.log')
        if($LASTEXITCODE -ne 0){throw 'Pre-start readonly census failed'}
        $prestartRows=@(Import-Csv (Join-Path $prestartRoot 'global-admission.tsv') -Delimiter "`t")
        $center=@($prestartRows | Where-Object {[long]$_.profileId -eq $SetupProfileId -and $_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.instanceId -ceq '0' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddSeconds(540)})
        if($center.Count -ne 1){throw 'Pre-start current READY calendar setup unavailable'}
        $fixed=$center[0]
        $fixed | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-setup-before-native-publication.json') -Encoding utf8
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=[int]$fixed.x;y=[int]$fixed.y;z=[int]$fixed.z} -EndpointPoint @{x=[int]$fixed.x-200;y=[int]$fixed.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
        if($LASTEXITCODE -ne 0){throw 'Initial native setup dry geometry rejected'}
        $startArguments.SetupProfileId=$SetupProfileId
    }
    $startupRaw=& (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') @startArguments
    $startupRaw | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true; $watch.Restart()
    if($NativeStartAtSetup){
        $startup=$startupRaw | ConvertFrom-Json
        if([long]$startup.setupProfileId -ne $SetupProfileId -or $startup.initialPoint.admissionGranted -ne $false -or [int]$startup.initialPoint.x -ne [int]$fixed.x -or [int]$startup.initialPoint.y -ne [int]$fixed.y -or [int]$startup.initialPoint.z -ne [int]$fixed.z -or [int]$startup.initialPoint.instanceId -ne 0){throw 'Exact pre-publication native setup identity changed'}
    }
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
    $status=Capture032 'STATUS'
    if($status.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $status.before.clientIdentity -cne 'none' -or $status.before.worldPresent -cne 'true'){throw 'Native synthetic identity unverified.'}
    $globalRoot=Join-Path $OutputRoot 'global-before-outcomes'
    & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $globalRoot *> (Join-Path $OutputRoot 'global-census.log')
    if($LASTEXITCODE -ne 0){throw 'Source-pinned global admission census failed.'}
    $globalRows=@(Import-Csv (Join-Path $globalRoot 'global-admission.tsv') -Delimiter "`t")
    $nearest=@($globalRows | Where-Object {$_.state -ne 'ABSENT'} | Sort-Object {[Math]::Pow([double]$_.x-[double]$status.before.x,2)+[Math]::Pow([double]$_.y-[double]$status.before.y,2)} | Select-Object -First 8)
    $nearest | ForEach-Object {[pscustomobject]@{profileId=[long]$_.profileId;materializedAtNanos=0}} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'nearest-current-eight.json') -Encoding utf8
    if($SetupAtNearestCandidate -and -not $NativeStartAtSetup){
        if($SetupTeleport.Count){throw 'One source-pinned setup required.'}
        $eligible=@($globalRows | Where-Object {$_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.instanceId -ceq '0' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddSeconds(540)})
        $ranked=@($eligible | ForEach-Object {
            $center=$_; $members=@($eligible | Where-Object {[Math]::Pow([double]$_.x-[double]$center.x,2)+[Math]::Pow([double]$_.y-[double]$center.y,2) -le 2250000})
            [pscustomobject]@{profileId=[long]$center.profileId;x=[int]$center.x;y=[int]$center.y;z=[int]$center.z;count=$members.Count;preparedCount=@($members | Where-Object {[long]$_.committedCursorMinute -ge 0 -and ([long]$_.requestedHorizonMinute-[long]$_.committedCursorMinute) -le 15}).Count;members=@($members.profileId);distance=[Math]::Pow([double]$center.x-[double]$status.before.x,2)+[Math]::Pow([double]$center.y-[double]$status.before.y,2)}
        } | Where-Object {$_.count -ge 4} | Sort-Object @{Expression='preparedCount';Descending=$true},@{Expression='count';Descending=$true},distance,profileId)
        if($SetupProfileId -gt 0){$ranked=@($ranked | Where-Object {$_.profileId -eq $SetupProfileId})}
        $clusters=[Collections.Generic.List[object]]::new()
        foreach($candidate in $ranked){if(@($clusters | Where-Object {[Math]::Pow($_.x-$candidate.x,2)+[Math]::Pow($_.y-$candidate.y,2) -lt 4000000}).Count -eq 0){$clusters.Add($candidate)};if($clusters.Count -eq 3){break}}
        @($clusters) | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'preranked-current-clusters.json') -Encoding utf8
        if($clusters.Count -eq 0){
            & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'nearest-current-eight.json') -OutputRoot (Join-Path $OutputRoot 'cohort0-first-guards') *> (Join-Path $OutputRoot 'cohort0-first-guards.log')
            throw 'NATURAL_ADMISSION_NO_READY_ONLINE_CURRENT_CLUSTER: exact eight first guards exported.'
        }
        $fixed=$clusters[0]; $SetupTeleport=@{x=$fixed.x;y=$fixed.y;z=$fixed.z;instanceId=0}
        $fixed | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-setup-candidate.json') -Encoding utf8
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=$SetupTeleport.x;y=$SetupTeleport.y;z=$SetupTeleport.z} -EndpointPoint @{x=$SetupTeleport.x-200;y=$SetupTeleport.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
        if($LASTEXITCODE -ne 0){throw 'Fixed current cluster native geometry rejected.'}
    }
    if($SetupTeleport.Count){
        if(-not $SetupTeleport.ContainsKey('instanceId')){throw 'Checked setup requires exact instanceId.'}
        $candidate=Capture032 'SNAPSHOT_PHANTOMS' @{setupProfileId=[long]$fixed.profileId}
        $exactCandidate=$candidate.reason -ceq 'SETUP_CANDIDATE_SNAPSHOT' -and [int]$candidate.candidate.x -eq [int]$SetupTeleport.x -and [int]$candidate.candidate.y -eq [int]$SetupTeleport.y -and [int]$candidate.candidate.z -eq [int]$SetupTeleport.z -and [int]$candidate.candidate.instanceId -eq [int]$SetupTeleport.instanceId
        $nearby=[Math]::Sqrt([Math]::Pow([int]$status.before.x-[int]$SetupTeleport.x,2)+[Math]::Pow([int]$status.before.y-[int]$SetupTeleport.y,2)) -le 2000
        if(-not $exactCandidate -and -not $nearby -and -not $SetupAtNearestCandidate){throw 'Fixed setup differs from admitted candidate; no location retry.'}
        $null=Capture032 'TELEPORT_SELF' $SetupTeleport
        $status=Capture032 'STATUS'
        if($status.before.teleporting -cne 'false' -or [Math]::Abs([int]$status.before.x-[int]$SetupTeleport.x) -gt 32 -or [Math]::Abs([int]$status.before.y-[int]$SetupTeleport.y) -gt 32){throw 'Pre-baseline setup arrival unconfirmed.'}
    }
    $enrollmentPoint=$fixed
    if($InitialObservationPoint.Count){
        if($Mode -cne 'Away' -or -not $NativeStartAtSetup -or $SetupTeleport.Count -or $InitialObservationPoint.Count -ne 3){throw 'One explicit checked initial away observation place required.'}
        foreach($axis in @('x','y','z')){if(-not $InitialObservationPoint.ContainsKey($axis) -or [int]$InitialObservationPoint[$axis] -ne [int]$path.origin.$axis){throw 'Initial place differs from immutable native path origin.'}}
        $nearby=[Math]::Sqrt([Math]::Pow([int]$fixed.x-[int]$path.origin.x,2)+[Math]::Pow([int]$fixed.y-[int]$path.origin.y,2)) -le 2000
        # Source World.SHIFT_BY=11; preserve source-known region, instance0 and the existing initial setup2000 bound.
        if(-not $nearby -or [Math]::Abs(([int]$fixed.x -shr 11)-([int]$path.origin.x -shr 11)) -gt 1 -or [Math]::Abs(([int]$fixed.y -shr 11)-([int]$path.origin.y -shr 11)) -gt 1 -or [Math]::Abs([int]$fixed.z-[int]$path.origin.z) -gt 200){throw 'Initial place leaves the source native-known envelope.'}
        if(-not $InitialSetupPathJson){throw 'Checked native setup prefix required.'}
        $setupPath=Get-Content -LiteralPath $InitialSetupPathJson -Raw | ConvertFrom-Json
        if(-not $setupPath.bidirectional -or $setupPath.helperSource -cne (Join-Path $PSScriptRoot 'ReadDryRoute032.java') -or $setupPath.helperSha256 -cne (Get-FileHash $setupPath.helperSource).Hash -or $setupPath.geoLogSha256 -cne (Get-FileHash $setupPath.geoLog).Hash -or $setupPath.jarSha256 -cne $manifest.gameJarSha256 -or $setupPath.sourceSha -cne $FrozenSha -or $setupPath.steps.Count -gt 10){throw 'Exact immutable native setup prefix provenance required.'}
        foreach($axis in @('x','y','z')){if([int]$setupPath.origin.$axis -ne [int]$fixed.$axis -or [int]$setupPath.endpoint.$axis -ne [int]$path.origin.$axis){throw 'Exact source/place setup prefix required.'}}
        foreach($point in $setupPath.steps){
            $null=Capture032 'TELEPORT_SELF' @{x=[int]$point.x;y=[int]$point.y;z=[int]$point.z;instanceId=0}
            $status=Capture032 'STATUS'
            if($status.before.teleporting -cne 'false' -or [int]$status.before.x -ne [int]$point.x -or [int]$status.before.y -ne [int]$point.y -or [int]$status.before.z -ne ([int]$point.z+5)){throw 'Native setup prefix arrival unconfirmed; no replay.'}
        }
        if($status.before.teleporting -cne 'false' -or [int]$status.before.x -ne [int]$path.origin.x -or [int]$status.before.y -ne [int]$path.origin.y -or [int]$status.before.z -ne ([int]$path.origin.z+5)){throw 'Fixed initial place arrival unconfirmed.'}
        # Creature.teleToLocation canonicalizes stock geodata height and adds exactly5 (Creature.java:858).
        $enrollmentPoint=[pscustomobject]@{x=[int]$path.origin.x;y=[int]$path.origin.y;z=([int]$path.origin.z+5)}
        [ordered]@{scope='INITIAL_SETUP_BEFORE_ENROLLMENT';sourceProfile=$SetupProfileId;sourcePoint=$fixed;observationPoint=$path.origin;pathSha256=(Get-FileHash $PathJson).Hash;admissionGranted=$false} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-initial-observation-place.json') -Encoding utf8
    }
    if($NativeStartAtSetup -and ([int]$status.before.x -ne [int]$enrollmentPoint.x -or [int]$status.before.y -ne [int]$enrollmentPoint.y -or [int]$status.before.z -ne [int]$enrollmentPoint.z)){throw 'Initial native observer point changed before enrollment'}
    # Re-rank the same pre-outcome census around the factual post-setup observer position.
    $nearestSetup=@($globalRows | Where-Object {$_.state -ne 'ABSENT'} | Sort-Object {[Math]::Pow([double]$_.x-[double]$status.before.x,2)+[Math]::Pow([double]$_.y-[double]$status.before.y,2)} | Select-Object -First 8)
    $nearestSetup | ForEach-Object {[pscustomobject]@{profileId=[long]$_.profileId;materializedAtNanos=0}} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'nearest-setup-eight.json') -Encoding utf8
    # Fixed enrollment time. No rewards-based spot or actor selection.
    $enrollment=[Diagnostics.Stopwatch]::StartNew()
    while($enrollment.Elapsed.TotalSeconds -lt 25){
        if($heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat writer failed during enrollment.'}
        Start-Sleep -Seconds 1
    }
    if($watch.Elapsed.TotalSeconds -gt 60){throw 'SETUP_BOUND60: no Synthetic clock reset.'}
    $baseline=@(Discover032 | Where-Object {$_.worldPresent -ceq 'true'} | Sort-Object {[long]$_.profileId})
    $baseline | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'baseline-cohort.json') -Encoding utf8
    if($baseline.Count -lt 4 -or $baseline.Count -gt 8){
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'nearest-setup-eight.json') -OutputRoot (Join-Path $OutputRoot 'cohort0-first-guards') *> (Join-Path $OutputRoot 'cohort0-first-guards.log')
        throw "NATURAL_COHORT_COUNT:$($baseline.Count); exact eight first guards exported"
    }
    $suitable=@($baseline | Where-Object {($Mode -ceq 'Probe' -or $_.dead -ceq 'false') -and [int]$_.npcId -gt 0})
    $different=@($suitable | Where-Object {$_.profileId -notin $PreviousPrimaryIds})
    $primary=@($(if($different.Count -ge 2){$different}else{$suitable}) | Select-Object -First 2)
    if($primary.Count -ne 2){throw 'Two suitable primaries unavailable before outcomes.'}
    $primary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'primary.json') -Encoding utf8
    $endpoint=if($Mode -ceq 'Away'){@{x=[int]$path.endpoint.x;y=[int]$path.endpoint.y;z=[int]$path.endpoint.z}}else{@{}}
    & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'enrollment-census') -EndpointPoint $endpoint *> (Join-Path $OutputRoot 'census-attach.log')
    if($LASTEXITCODE -ne 0){throw 'One-shot World/capacity census failed.'}
    & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode FullObserve -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot $fullRoot -ObserverRunId $run -EndpointPoint $endpoint *> (Join-Path $OutputRoot 'full-attach.log')
    if($LASTEXITCODE -ne 0){throw 'Existing FullObserve attach failed.'}
    $fullStarted=$true
    $frameReady=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path (Join-Path $fullRoot 'full-frame-latest.json'))){if($frameReady.Elapsed.TotalSeconds -gt 5){throw 'Full native first frame missing.'};Start-Sleep -Milliseconds 100}
    $first=ReadFrame032
    if($Mode -ceq 'Probe'){
        $origin=@{x=[int]$first.observer.x;y=[int]$first.observer.y;z=[int]$first.observer.z}
        $point=@{x=$origin.x-200;y=$origin.y;z=$origin.z}
        $move=Capture032 'MOVE_SELF' $point
        if($move.status -cne 'ACCEPTED'){throw 'Probe MOVE admission missing.'}
        WaitArrival032 $point
        $return=Capture032 'MOVE_SELF' $origin
        if($return.status -cne 'ACCEPTED'){throw 'Probe return admission missing.'}
        WaitArrival032 $origin
    }
    if($Mode -ceq 'Away'){
        if([Math]::Abs($first.observer.x-[int]$path.origin.x) -gt 24 -or [Math]::Abs($first.observer.y-[int]$path.origin.y) -gt 24 -or [Math]::Abs($first.observer.z-[int]$path.origin.z) -gt 48){throw 'Actual observer origin differs from native dry path.'}
        $admission=Import-Csv (Join-Path $OutputRoot 'enrollment-census/endpoint-locality.tsv') -Delimiter "`t"
        foreach($id in $primary.profileId){
            $row=@($admission | Where-Object {$_.profileId -ceq $id})
            if($row.Count -ne 1 -or $row[0].worldPresent -cne 'true' -or $row[0].prewarm -cne 'false' -or $row[0].nativeVisible -cne 'false'){throw 'Endpoint lacks native no-demand proof for preselected actor.'}
        }
        MarkPhase032 'DEPART'
        Walk032 @($path.steps)
        MarkPhase032 'AWAY_ARRIVED'
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'away-census') *> (Join-Path $OutputRoot 'away-census.log')
        if($LASTEXITCODE -ne 0){throw 'Bounded away native census failed.'}
        $humans=Import-Csv (Join-Path $OutputRoot 'away-census/world-identities.tsv') -Delimiter "`t" | Where-Object {$_.headless -ceq 'false' -and $_.objectId -cne '268492939'}
        if(@($humans).Count){throw 'Other native human at away boundary.'}
        $away=[Diagnostics.Stopwatch]::StartNew()
        do{
            $frame=ReadFrame032
            foreach($id in $primary.profileId){$row=@($frame.actors | Where-Object {$_.profileId -ceq $id});if($row.Count -ne 1 -or $row[0].observerPrewarm -cne 'false' -or $row[0].observerNativeVisible -cne 'false'){throw 'Native away locality still demanded.'}}
            if(@($frame.actors | Where-Object {$_.profileId -cin $primary.profileId -and $_.worldPresent -ceq 'true'}).Count -eq 0){break}
            if($away.Elapsed.TotalSeconds -gt 120){throw 'SOFT_RETIRE_BOUND: preselected actors remain in World after native action/grace.'}
            Start-Sleep -Seconds 1
        }while($true)
        MarkPhase032 'BACKGROUND_ABSENT'
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'away-early') *> (Join-Path $OutputRoot 'away-early-export.log')
        if($LASTEXITCODE -ne 0){throw 'Exact away canonical export failed.'}
        # One full native minute plus scheduling margin, inside the unchanged480s episode budget.
        $backgroundRemaining=$BackgroundSeconds
        while($backgroundRemaining -gt 0){
            $chunk=[Math]::Min(40,$backgroundRemaining)
            Start-Sleep -Seconds $chunk
            $backgroundRemaining-=$chunk
            if($watch.Elapsed.TotalSeconds -gt 320){throw 'Background observation exceeded route-return budget; no threshold extension.'}
        }
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'away-late') *> (Join-Path $OutputRoot 'away-late-export.log')
        if($LASTEXITCODE -ne 0){throw 'Exact background step export failed.'}
        MarkPhase032 'RETURN'
        $back=[Collections.Generic.List[object]]::new()
        for($i=$path.steps.Count-2;$i -ge 0;$i--){$back.Add($path.steps[$i])}
        $back.Add($path.origin); Walk032 @($back)
        MarkPhase032 'RETURNED'
    }
    $first=ReadFrame032
    $sequenceBefore=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    $observation=[Diagnostics.Stopwatch]::StartNew(); $lastNanos=[long]$first.sampleNanos; $maxGap=0.0; $unique=0; $lastFresh=0.0
    $samples=[Collections.Generic.List[object]]::new()
    $samples.Add([pscustomobject]@{elapsedSeconds=0.0;sampleNanos=$first.sampleNanos;observer=$first.observer;actors=$first.actors})
    while($observation.Elapsed.TotalSeconds -lt $Seconds){
        if($watch.Elapsed.TotalSeconds -gt 480){throw 'Episode budget480s exhausted; TTL not extended.'}
        if($heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat writer failed.'}
        $session=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
        if($session.state -cne 'RUNNING'){throw "NATIVE_SESSION_CLOSED:$($session.reason)"}
        $frame=ReadFrame032
        if($Mode -ceq 'Scene' -and ($frame.observer.moving -or $frame.observer.x -ne $first.observer.x -or $frame.observer.y -ne $first.observer.y -or $frame.observer.z -ne $first.observer.z -or $frame.observer.instance -ne $first.observer.instance)){throw 'STATIONARY_OBSERVER_CHANGED: frozen native scene ends.'}
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
    if($Mode -ceq 'Away'){MarkPhase032 'POST_RETURN_DONE'}
    $sequenceAfter=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    [ordered]@{kind=$Mode;sha=$FrozenSha;runId=$run;seconds=$observation.Elapsed.TotalSeconds;nativeUniqueSamples=$unique;maxGapSeconds=$maxGap;commandCount=$script:commands;baselineCount=$baseline.Count;primaryIds=@($primary.profileId);sameSession=$true;telemetryMailboxCommands=([long]$sequenceAfter.nextSequence-[long]$sequenceBefore.nextSequence);sequenceBefore=[long]$sequenceBefore.nextSequence;sequenceAfter=[long]$sequenceAfter.nextSequence;arrivalProof=($Mode -ceq 'Probe')} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'capture-result.json') -Encoding utf8
    if($StopOwnServerOnComplete){
        if($Mode -notin @('Scene','Away')){throw 'Stock endpoint stop is restricted to completed acceptance scenes.'}
        # Final observation has ended. Remove the exact owned Synthetic before the stock human-online guard.
        & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8
        $syntheticEnd=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
        if($syntheticEnd.state -cne 'STOPPED'){throw 'Exact Synthetic stop required before stock server shutdown.'}
        $syntheticStopped=$true
        # Stock shutdown seals native actors; SQL is exported only after all own writers stop.
        [IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false))
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $gameBefore=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
        $loginBefore=Get-LocalPlayRoleState $runtime 'LoginServer' 'LoginServer.jar' @(2106)
        if($gameBefore.state -cne 'RUNNING' -or !$gameBefore.recordVerified -or $loginBefore.state -cne 'RUNNING' -or !$loginBefore.recordVerified){throw 'Both exact own incarnations required before stock stop.'}
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $OutputRoot 'stock-stop') *> (Join-Path $OutputRoot 'stock-stop.log')
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $gameEnd=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
        $loginEnd=Get-LocalPlayRoleState $runtime 'LoginServer' 'LoginServer.jar' @(2106)
        $stockText=[IO.File]::ReadAllText((Join-Path $OutputRoot 'stock-stop.log'))
        $typedDone=Get-Content -LiteralPath (Join-Path $OutputRoot 'stock-stop/typed-stop-done.properties') -Raw | ConvertFrom-StringData
        foreach($ended in @(@{role='GameServer';before=$gameBefore;after=$gameEnd},@{role='LoginServer';before=$loginBefore;after=$loginEnd})){
            if($ended.after.state -cnotin @('STOPPED','STALE_RECORD') -or (Get-Process -Id $ended.before.pid -ErrorAction SilentlyContinue) -or !$stockText.Contains("STOCK_GRACEFUL_STOP_CONFIRMED role=$($ended.role) pid=$($ended.before.pid)")){throw 'Exact stock stop/process absence unconfirmed.'}
        }
        if((Get-LocalPlayPortOwners @(7777,2106)).Count -ne 0 -or $typedDone.progress -notmatch 'phase=DONE, outcome=COMPLETE,' -or $typedDone.materialization -cne 'ShutdownSnapshot[state=STOPPED, retainedEntries=0]' -or $typedDone.proofFailure){throw 'Stock drain/port-release proof unconfirmed.'}
        [ordered]@{sourceSha=$FrozenSha;gamePid=$gameBefore.pid;gameStartTicks=$gameBefore.startTimeUtcTicks;loginPid=$loginBefore.pid;loginStartTicks=$loginBefore.startTimeUtcTicks;gameTerminalState=$gameEnd.state;loginTerminalState=$loginEnd.state;stockLogSha256=(Get-FileHash (Join-Path $OutputRoot 'stock-stop.log')).Hash;typedStopSha256=(Get-FileHash (Join-Path $OutputRoot 'stock-stop/typed-stop-done.properties')).Hash;healthyStockStop=$true} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'stock-stop-verified.json') -Encoding utf8
        $serverStopped=$true
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'stopped-sql') *> (Join-Path $OutputRoot 'stopped-sql-export.log')
        if($LASTEXITCODE -ne 0){throw 'Stopped whole-group SQL endpoint export failed.'}
    }
    "TASK032_CAPTURE_COMPLETE mode=$Mode cohort=$($baseline.Count) samples=$unique gap=$maxGap commands=$script:commands"
}catch{
    $_ | Out-String | Set-Content (Join-Path $OutputRoot 'episode-failure.txt') -Encoding utf8
    throw
}finally{
    if($started){
        try{
            if(-not $serverStopped -and -not $syntheticStopped){
                & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8
            }
            $finalState=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
            $finalState | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'final-synthetic-state.json') -Encoding utf8
            if($finalState.state -cne 'STOPPED'){throw 'Synthetic cleanup not confirmed.'}
        }finally{
            [IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false))
            if($heartbeatJob){$heartbeatJob | Wait-Job -Timeout 10 | Out-Null; Receive-Job $heartbeatJob *> (Join-Path $OutputRoot 'heartbeat-job.log'); Remove-Job $heartbeatJob -Force}
        }
    }
    if($fullStarted -and -not $serverStopped){
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $flushOwner=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
        if($flushOwner.state -ceq 'RUNNING' -and $flushOwner.recordVerified){& (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Flush -Episode $Episode -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'full-flush.log')}
        else { "TASK032_FLUSH_SKIPPED state=$($flushOwner.state): no late attach to absent own JVM" | Set-Content (Join-Path $OutputRoot 'full-flush.log') -Encoding utf8 }
    }
}
