[CmdletBinding()]
param([ValidateSet('Probe','Scene','Away')][string]$Mode='Probe',
      [ValidateSet('a')][string]$Episode='a',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha,
      [ValidateRange(75,135)][int]$BackgroundSeconds=75, [hashtable]$InitialObservationPoint=@{}, [hashtable]$SetupTeleport=@{}, [switch]$SetupAtNearestCandidate, [switch]$SetupAtVisibleNativeCluster045, [string]$PathJson='', [string]$InitialSetupPathJson='', [string[]]$PreviousPrimaryIds=@(),[long]$SetupProfileId=0,[switch]$NativeStartAtSetup,[switch]$StopOwnServerOnComplete,[string]$PreparedRunId='',[switch]$SingleLifePrimary045,[long]$FixedLifeProfileId045=0,[int]$LifeNpcId045=0,[string]$PreconditionsFile045='',[switch]$LifeCurrentReady045,[switch]$LifeProductive045)
$ErrorActionPreference='Stop'
$DiagnosticOnly045 = $env:PHANTOM_TASK045_DIAGNOSTIC -ceq 'true'
$returnSession045=$false; $returnSnapshotIndex045=0; $sessionWatch045=$null
. ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Read-SharedJson032.ps1')
$module=[IO.Path]::GetFullPath((Join-Path 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-SIGNAL-LIFECYCLE-045' '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract045$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-SIGNAL-LIFECYCLE-045' 'evidence'))+[IO.Path]::DirectorySeparatorChar
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if(-not $OutputRoot.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $OutputRoot)){throw 'Immutable own TASK032 evidence path required.'}
if($Mode -ceq 'Scene' -and ($Seconds -lt 360 -or $Seconds -gt 420)){throw 'Final scene must be 360..420 seconds.'}
if($SetupAtVisibleNativeCluster045 -and ($Mode -cnotin @('Scene','Probe') -or $SetupAtNearestCandidate -or $NativeStartAtSetup -or $SetupTeleport.Count)){throw 'One prospective native census setup required.'}
if($Mode -ceq 'Probe' -and ($Seconds -lt 60 -or $Seconds -gt 150)){throw 'Probe must be 60..150 seconds.'}
if($Mode -ceq 'Away' -and (-not $PathJson)){throw 'Dedicated dry native path required before away episode.'}
if($SingleLifePrimary045 -and $Mode -cne 'Away'){throw 'Single profile lifecycle proof is only S3B soft-away, never G1/G2.'}
if($Mode -ceq 'Away'){
    $path=Get-Content -LiteralPath $PathJson -Raw | ConvertFrom-Json
    $helper=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDryPath023.java'
    if($path.helperSource){$helper=[IO.Path]::GetFullPath($path.helperSource);if($helper -cne ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\ReadDryRoute032.java') -and $helper -cne (Join-Path $module '.phantom-local/ops045/ReadDryPath045.java') -and $helper -cne (Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/ReadDryPath023.java')){throw 'Exact checked dry helper required.'}}
    if(-not $path.bidirectional -or $path.helperSha256 -cne (Get-FileHash $helper).Hash -or $path.geoLogSha256 -cne (Get-FileHash $path.geoLog).Hash){throw 'Immutable native dry path provenance invalid.'}
    if($Seconds -lt 90 -or $Seconds -gt 160 -or (2 * $path.steps.Count) -gt 40){throw 'Bounded away/return plan exceeds40 total native steps or525s/400sequence budget.'}
}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $FrozenSha -or $manifest.databaseName -cne "l2jmobiush5_phantom_test_045$Episode"){throw 'Frozen SHA/exact clone mismatch.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=if($PreparedRunId){
    if($Mode -cnotin @('Scene','Probe') -or $PreparedRunId -notmatch '^[0-9a-f-]{36}$' -or $NativeStartAtSetup -or $SetupAtNearestCandidate -or $SetupTeleport.Count){throw 'Prepared capture must reuse one unchanged native session.'}
    $PreparedRunId
}else{[guid]::NewGuid().ToString('D')}
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
        $raw=& (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -Arguments $Arguments -RunId $run -ActorMode Synthetic -ExternalHeartbeat:(!$returnSession045) -TimeoutSeconds 20
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
    if($returnSession045){return ReadReturnFrame045}
    $frame=Read-SharedJson032 (Join-Path $fullRoot 'full-frame-latest.json')
    if($frame.proofFailure){throw ('OBSERVER_PROOF_INVALID:'+$frame.proofFailure)}
    if($frame.observer.runId -cne $run -or $frame.observer.sessionState -cne 'RUNNING' -or -not $frame.observer.present -or -not $frame.observer.online -or $frame.observer.dead -or [int]$frame.observer.objectId -ne 268492939){throw 'Native observer identity lost; episode ends.'}
    return $frame
}
function ReadArrivalFrame032{
    if($returnSession045){
        $status045=Capture032 'STATUS'
        return [pscustomobject]@{observer=[pscustomobject]@{x=[int]$status045.before.x;y=[int]$status045.before.y;z=[int]$status045.before.z;moving=($status045.before.moving -ceq 'true')}}
    }
    if($Mode -cne 'Away'){return ReadFrame032}
    $arrivalPath045=Join-Path $fullRoot 'arrival-pose-latest.json'
    $arrivalReady045=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path -LiteralPath $arrivalPath045)){
        if($arrivalReady045.Elapsed.TotalSeconds -gt 5){throw 'TELEMETRY_STALE: first native arrival frame absent for5s.'}
        Start-Sleep -Milliseconds 100
    }
    $native=Read-SharedJson032 $arrivalPath045
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
        $routeElapsed045=if($returnSession045){$sessionWatch045.Elapsed.TotalSeconds}else{$watch.Elapsed.TotalSeconds}
        if($routeElapsed045 -gt 320){throw 'Away route budget exhausted before post-return proof.'}
        $frame=ReadArrivalFrame032
        $dx=[double]$point.x-[double]$frame.observer.x; $dy=[double]$point.y-[double]$frame.observer.y
        if([Math]::Sqrt($dx*$dx+$dy*$dy) -gt 300.01 -or [Math]::Abs([int]$point.z-[int]$frame.observer.z) -gt 200){throw 'Factual next dry step bound violated; no MOVE.'}
        $move=Capture032 'MOVE_SELF' @{x=[int]$point.x;y=[int]$point.y;z=[int]$point.z}
        if($move.status -cne 'ACCEPTED'){throw 'Away MOVE admission missing.'}
        WaitArrival032 @{x=[int]$point.x;y=[int]$point.y;z=[int]$point.z}
    }
}
function ReadReturnFrame045{
    $script:returnSnapshotIndex045++
    if($script:returnSnapshotIndex045 -gt 150){throw 'Bounded same-profile return read budget exhausted.'}
    $status045=Capture032 'STATUS'
    $file045=Join-Path $OutputRoot ('return-frame-{0:D3}.properties' -f $script:returnSnapshotIndex045)
    & (Join-Path $PSScriptRoot 'Invoke-SignalSnapshot045.ps1') -ProfileId ([long]$primary[0].profileId) -OutputFile $file045 *> (Join-Path $OutputRoot ('return-read-{0:D3}.log' -f $script:returnSnapshotIndex045))
    if($LASTEXITCODE -ne 0){throw 'Same-profile passive return read failed.'}
    $facts045=Get-Content -LiteralPath $file045 -Raw | ConvertFrom-StringData
    $actor045=[ordered]@{profileId=[string]$primary[0].profileId;missing=([string]($facts045.worldPresent -cne 'true')).ToLowerInvariant();dead='UNKNOWN';nativeFarmCycleSequence='0';nativeRewardSequence='0';nativeDamageSequence='0'}
    foreach($key045 in $facts045.Keys){$actor045[$key045]=$facts045[$key045]}
    $session045=Read-PilotProperties (Join-Path $runtime "playtest-synthetic/$run/session.properties")
    if($session045.state -cne 'RUNNING' -or $session045.sessionId -cne $run -or $status045.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $status045.before.clientIdentity -cne 'none' -or $status045.before.worldPresent -cne 'true'){throw 'Actual return session/native observer identity missing.'}
    $observer045=[pscustomobject]@{runId=$run;sessionState=$session045.state;objectId=268492939;present=($status045.before.worldPresent -ceq 'true');online=($status045.before.online -ceq 'true');dead=($status045.before.dead -ceq 'true');x=[int]$status045.before.x;y=[int]$status045.before.y;z=[int]$status045.before.z;moving=($status045.before.moving -ceq 'true')}
    return [pscustomobject]@{sampleNanos=$facts045.sampleNanos;observer=$observer045;actors=@([pscustomobject]$actor045)}
}
try{
    # Install before Synthetic can cause any materialization/checkpoint. Epoch0 is not enrolled.
    @([pscustomobject]@{profileId=275;materializedAtNanos=0}) | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'collector-bootstrap.json') -Encoding utf8
    & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Enroll -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'collector-bootstrap.log')
    if($LASTEXITCODE -ne 0){throw 'Prospective receipt collector installation failed.'}
    $startArguments=@{RunId=$run}
    if($NativeStartAtSetup){
        if($SetupProfileId -le 0 -or -not $SetupAtNearestCandidate -or $SetupTeleport.Count){throw 'One explicit readonly initial setup profile required'}
        $prestartRoot=Join-Path $OutputRoot 'global-pre-native-start'
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $prestartRoot *> (Join-Path $OutputRoot 'prestart-census.log')
        if($LASTEXITCODE -ne 0){throw 'Pre-start readonly census failed'}
        $prestartRows=@(Import-Csv (Join-Path $prestartRoot 'global-admission.tsv') -Delimiter "`t")
        $center=@($prestartRows | Where-Object {[long]$_.profileId -eq $SetupProfileId -and $_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.instanceId -ceq '0' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddSeconds(540)})
        if($center.Count -ne 1){throw 'Pre-start current READY calendar setup unavailable'}
        $fixed=$center[0]
        $fixed | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-setup-before-native-publication.json') -Encoding utf8
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=[int]$fixed.x;y=[int]$fixed.y;z=[int]$fixed.z} -EndpointPoint @{x=[int]$fixed.x-200;y=[int]$fixed.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
        if($LASTEXITCODE -ne 0){throw 'Initial native setup dry geometry rejected'}
        $startArguments.SetupProfileId=$SetupProfileId
    }
    if($PreparedRunId){
        $preparedContext=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $run
        $preparedState=Read-PilotProperties (Join-Path $preparedContext.PilotRoot 'session.properties')
        if($preparedState.sessionId -cne $run -or $preparedState.state -cne 'RUNNING' -or [long]$preparedState.pid -ne $preparedContext.Pid -or [long]$preparedState.startTimeUtcTicks -ne $preparedContext.StartTimeUtcTicks -or ([long]$preparedState.expiresUtcMillis-[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -lt (($Seconds+40)*1000)){throw 'Prepared native session identity/remaining original525s lease insufficient.'}
        Get-PilotSession $preparedContext | Out-Null
        $startupRaw=@{state='RUNNING';runId=$run;preparedExisting=$true;expiresUtcMillis=$preparedState.expiresUtcMillis;leaseRenewed=$false} | ConvertTo-Json -Compress
    }else{$startupRaw=& (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') @startArguments}
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
                $published=$false
                for($attempt=0;$attempt -lt 4 -and -not $published;$attempt++){
                    try { Write-PilotHeartbeat $context $Run $Run; $published=$true }
                    catch {
                        $cause=$_.Exception.GetBaseException()
                        if($cause -isnot [IO.IOException] -and $cause -isnot [UnauthorizedAccessException]){throw}
                        "$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())	$attempt	$($cause.GetType().Name)	$($cause.HResult)" | Add-Content (Join-Path $Output 'heartbeat-publication-retries.tsv') -Encoding utf8
                        if($attempt -eq 3){throw}
                        Start-Sleep -Milliseconds 100
                    }
                }
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
    & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'collector-bootstrap.json') -OutputRoot $globalRoot *> (Join-Path $OutputRoot 'global-census.log')
    if($LASTEXITCODE -ne 0){throw 'Source-pinned global admission census failed.'}
    $globalRows=@(Import-Csv (Join-Path $globalRoot 'global-admission.tsv') -Delimiter "`t")
    $nearest=@($globalRows | Where-Object {$_.state -ne 'ABSENT'} | Sort-Object {[Math]::Pow([double]$_.x-[double]$status.before.x,2)+[Math]::Pow([double]$_.y-[double]$status.before.y,2)} | Select-Object -First 8)
    $nearest | ForEach-Object {[pscustomobject]@{profileId=[long]$_.profileId;materializedAtNanos=0}} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'nearest-current-eight.json') -Encoding utf8
    if($SetupAtVisibleNativeCluster045){
        $world045=@(Import-Csv (Join-Path $globalRoot 'world-identities.tsv') -Delimiter "`t" | Where-Object {$_.identity -ceq 'PHANTOM' -and $_.online -ceq 'true' -and $_.instanceId -ceq '0'})
        $ranked045=@(foreach($center045 in $world045){
            $matching045=@($globalRows | Where-Object {$_.objectId -ceq $center045.objectId})
            if($matching045.Count -ne 1){throw 'Current native identity lacks exact global profile mapping.'}
            # Exact current World.SHIFT_BY=11 / WORLD_Z_MIN=-16000 / Z_REGION_SIZE=2000,
            # PhantomNativeLocalityEnvelope.couldKnow distance1; no diagnostic radius substitutes visibility.
            $members045=@($world045 | Where-Object {
                [Math]::Abs(([int]$_.x -shr 11)-([int]$center045.x -shr 11)) -le 1 -and
                [Math]::Abs(([int]$_.y -shr 11)-([int]$center045.y -shr 11)) -le 1 -and
                [Math]::Abs([Math]::Floor(([int]$_.z+16000)/2000)-[Math]::Floor(([int]$center045.z+16000)/2000)) -le 1
            })
            [pscustomobject]@{profileId=[long]$matching045[0].profileId;x=[int]$center045.x;y=[int]$center045.y;z=[int]$center045.z;count=$members045.Count;memberObjects=@($members045.objectId);distance=[Math]::Pow([double]$center045.x-[double]$status.before.x,2)+[Math]::Pow([double]$center045.y-[double]$status.before.y,2)}
        })
        $requiredNeighbours045=if($Mode -ceq 'Scene'){6}else{4}
        $ranked045=@($ranked045 | Where-Object {$_.count -ge $requiredNeighbours045} | Sort-Object @{Expression='count';Descending=$true},distance,profileId)
        if($ranked045.Count -eq 0){throw "NATURAL_NATIVE_CLUSTER_PRECONDITION: no $requiredNeighbours045 current native neighbours; no outcome ranking."}
        $fixed=$ranked045[0]; $SetupTeleport=@{x=$fixed.x;y=$fixed.y;z=$fixed.z;instanceId=0}
        [ordered]@{method='PRE_OUTCOME_CURRENT_WORLD_COUNT_DISTANCE_PROFILE';selected=$fixed;source=(Join-Path $globalRoot 'world-identities.tsv');sourceSha256=(Get-FileHash (Join-Path $globalRoot 'world-identities.tsv')).Hash} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputRoot 'prospective-native-setup045.json') -Encoding UTF8
        & (Join-Path $PSScriptRoot 'Control032.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=$fixed.x;y=$fixed.y;z=$fixed.z} -EndpointPoint @{x=$fixed.x-200;y=$fixed.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
        if($LASTEXITCODE -ne 0){throw 'Current native cluster geometry rejected.'}
    }
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
            & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'nearest-current-eight.json') -OutputRoot (Join-Path $OutputRoot 'cohort0-first-guards') *> (Join-Path $OutputRoot 'cohort0-first-guards.log')
            throw 'NATURAL_ADMISSION_NO_READY_ONLINE_CURRENT_CLUSTER: exact eight first guards exported.'
        }
        $fixed=$clusters[0]; $SetupTeleport=@{x=$fixed.x;y=$fixed.y;z=$fixed.z;instanceId=0}
        $fixed | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-setup-candidate.json') -Encoding utf8
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=$SetupTeleport.x;y=$SetupTeleport.y;z=$SetupTeleport.z} -EndpointPoint @{x=$SetupTeleport.x-200;y=$SetupTeleport.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
        if($LASTEXITCODE -ne 0){throw 'Fixed current cluster native geometry rejected.'}
    }
    if($SetupTeleport.Count){
        if(-not $SetupTeleport.ContainsKey('instanceId')){throw 'Checked setup requires exact instanceId.'}
        if($SetupAtVisibleNativeCluster045){
            # Reuse the stock bidirectional dry route before enrollment; never widen TELEPORT_SELF.
            & (Join-Path $PSScriptRoot 'Control032.ps1') -Action DryPath -Episode $Episode -UseStockRoute -OriginPoint @{x=[int]$status.before.x;y=[int]$status.before.y;z=[int]$status.before.z} -EndpointPoint @{x=[int]$fixed.x;y=[int]$fixed.y} -OutputRoot (Join-Path $OutputRoot 'native-setup-prefix') *> (Join-Path $OutputRoot 'native-setup-prefix.log')
            if($LASTEXITCODE -ne 0){throw 'Native setup prefix rejected before enrollment.'}
            $prefixPath045=Get-Content (Join-Path $OutputRoot 'native-setup-prefix/path.json') -Raw | ConvertFrom-Json
            if(-not $prefixPath045.bidirectional -or $prefixPath045.steps.Count -gt 10 -or $prefixPath045.jarSha256 -cne $manifest.gameJarSha256){throw 'Existing ten-step native setup budget exceeded.'}
            foreach($point045 in $prefixPath045.steps){
                $null=Capture032 'TELEPORT_SELF' @{x=[int]$point045.x;y=[int]$point045.y;z=[int]$point045.z;instanceId=0}
                $status=Capture032 'STATUS'
                if($status.before.teleporting -cne 'false' -or [int]$status.before.x -ne [int]$point045.x -or [int]$status.before.y -ne [int]$point045.y -or [int]$status.before.z -ne ([int]$point045.z+5)){throw 'Native setup prefix arrival unconfirmed; no replay.'}
            }
            $SetupTeleport=@{}
        }
    }
    if($SetupTeleport.Count){
        if(-not $SetupTeleport.ContainsKey('instanceId')){throw 'Checked setup requires exact instanceId.'}
        $exactCandidate=$false
        if(-not $SetupAtVisibleNativeCluster045){
            $candidate=Capture032 'SNAPSHOT_PHANTOMS' @{setupProfileId=[long]$fixed.profileId}
            $exactCandidate=$candidate.reason -ceq 'SETUP_CANDIDATE_SNAPSHOT' -and [int]$candidate.candidate.x -eq [int]$SetupTeleport.x -and [int]$candidate.candidate.y -eq [int]$SetupTeleport.y -and [int]$candidate.candidate.z -eq [int]$SetupTeleport.z -and [int]$candidate.candidate.instanceId -eq [int]$SetupTeleport.instanceId
        }
        $nearby=[Math]::Sqrt([Math]::Pow([int]$status.before.x-[int]$SetupTeleport.x,2)+[Math]::Pow([int]$status.before.y-[int]$SetupTeleport.y,2)) -le 2000
        if(-not $exactCandidate -and -not $nearby -and -not $SetupAtNearestCandidate -and -not $SetupAtVisibleNativeCluster045){throw 'Fixed setup differs from admitted candidate; no location retry.'}
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
        if(-not $setupPath.bidirectional -or $setupPath.helperSource -cne ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\ReadDryRoute032.java') -or $setupPath.helperSha256 -cne (Get-FileHash $setupPath.helperSource).Hash -or $setupPath.geoLogSha256 -cne (Get-FileHash $setupPath.geoLog).Hash -or $setupPath.jarSha256 -cne $manifest.gameJarSha256 -or $setupPath.sourceSha -cne $FrozenSha -or $setupPath.steps.Count -gt 10){throw 'Exact immutable native setup prefix provenance required.'}
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
    $minimumCohort=if($Mode -ceq 'Scene'){6}elseif($SingleLifePrimary045){1}else{4}
    if($baseline.Count -lt $minimumCohort -or $baseline.Count -gt 8){
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'nearest-setup-eight.json') -OutputRoot (Join-Path $OutputRoot 'cohort0-first-guards') *> (Join-Path $OutputRoot 'cohort0-first-guards.log')
        throw "NATURAL_COHORT_COUNT:$($baseline.Count); exact eight first guards exported"
    }
    if($Mode -ceq 'Scene'){
        if(@($baseline | Where-Object {$_.dead -cne 'false'}).Count){throw 'ALIVE_WHOLE_COHORT_PRECONDITION: whole setup retained; no actor filtering or heal.'}
        $precondition=@(foreach($actor in $baseline){
            $candidate=@($globalRows | Where-Object {$_.profileId -ceq $actor.profileId})
            $ok=$candidate.Count -eq 1 -and $candidate[0].calendarOnline -ceq 'true' -and $candidate[0].nextBoundary -and [DateTimeOffset]::Parse($candidate[0].nextBoundary) -gt [DateTimeOffset]::UtcNow.AddSeconds($Seconds+30)
            [pscustomobject]@{profileId=$actor.profileId;calendarEligible=$ok;candidate=$candidate}
        })
        $precondition | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'pre-enrollment-calendar038.json') -Encoding utf8
        if(@($precondition | Where-Object {-not $_.calendarEligible}).Count){throw 'STABLE_CALENDAR_PRECONDITION: whole natural cohort retained; no actor filtering.'}
    }
    $suitable=@($baseline | Where-Object {($Mode -ceq 'Probe' -or $_.dead -ceq 'false') -and [int]$_.npcId -gt 0})
    . (Join-Path $module '.phantom-local/ops045/Select-Primary045.ps1')
    $primary=@(if($SingleLifePrimary045){$suitable | Sort-Object {[long]$_.profileId} | Select-Object -First 1}else{Select-Primary045 -Suitable $suitable -PreviousIds $PreviousPrimaryIds})
    if($primary.Count -ne $(if($SingleLifePrimary045){1}else{2})){throw 'Suitable primaries unavailable before outcomes.'}
    $primary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot $(if($Mode -ceq 'Away'){'tentative-primary.json'}else{'primary.json'})) -Encoding utf8
    $endpoint=if($Mode -ceq 'Away'){@{x=[int]$path.endpoint.x;y=[int]$path.endpoint.y;z=[int]$path.endpoint.z}}else{@{}}
    & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'enrollment-census') -EndpointPoint $endpoint *> (Join-Path $OutputRoot 'census-attach.log')
    if($LASTEXITCODE -ne 0){throw 'One-shot World/capacity census failed.'}
    if($Mode -ceq 'Away'){
        $endpointEligibility045=@(Import-Csv (Join-Path $OutputRoot 'enrollment-census/endpoint-locality.tsv') -Delimiter "`t")
        $endpointEligibility045 | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputRoot 'premeasurement-endpoint-eligibility045.json') -Encoding utf8
        $eligibleIds045=@($endpointEligibility045 | Where-Object {$_.worldPresent -ceq 'true' -and $_.prewarm -ceq 'false' -and $_.nativeVisible -ceq 'false'} | ForEach-Object {$_.profileId})
        # Select roles prospectively by capability and the actual dry endpoint, keeping every enrolled actor.
        $farmInputs045=@(Import-Csv (Join-Path $OutputRoot 'enrollment-census/global-admission.tsv') -Delimiter "`t")
        $farmIds045=@($farmInputs045 | Where-Object {$_.admitted -ceq 'true' -and $_.farmAllowed -ceq 'true' -and $_.calendarOnline -ceq 'true' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddSeconds(1200)} | ForEach-Object {$_.profileId})
        $farmInputs045 | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputRoot 'premeasurement-farm-inputs045.json') -Encoding utf8
        $eligibleLife045=@($suitable | Where-Object {$_.profileId -cin $eligibleIds045 -and $_.profileId -cin $farmIds045})
        $eligibleLife045=@(Select-CurrentLifeInputs045 -Suitable $eligibleLife045 -FarmInputs $farmInputs045 -RequireCurrent:($LifeCurrentReady045 -and !$DiagnosticOnly045))
        if($LifeProductive045){
            $productiveIds045=@($farmInputs045 | Where-Object {$_.productive540045 -ceq 'true'} | ForEach-Object {$_.profileId})
            $eligibleLife045=@($eligibleLife045 | Where-Object {$_.profileId -cin $productiveIds045})
        }
        if($FixedLifeProfileId045 -gt 0){$eligibleLife045=@($eligibleLife045 | Where-Object {[long]$_.profileId -eq $FixedLifeProfileId045})}
        if($LifeNpcId045 -gt 0){$eligibleLife045=@($eligibleLife045 | Where-Object {[int]$_.npcId -eq $LifeNpcId045})}
        $primary=@(if($SingleLifePrimary045){Select-LifePrimary045 -Suitable $eligibleLife045 -CurrentReadyOrdinary:$LifeCurrentReady045}else{Select-Primary045 -Suitable $eligibleLife045 -PreviousIds $PreviousPrimaryIds})
        if($primary.Count -ne $(if($SingleLifePrimary045){1}else{2})){throw 'Endpoint-eligible native inputs unavailable before MOVE.'}
        $primary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'primary.json') -Encoding utf8
        @{method=$(if($SingleLifePrimary045){'S3B one living profile with inventory headroom, actual endpoint no-demand; whole cohort retained; before MOVE/outcomes'}else{'native melee/caster capability among actual endpoint no-demand inputs; whole cohort retained; before MOVE/outcomes'});ids=@($primary.profileId);singleLife=$SingleLifePrimary045.IsPresent} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'premeasurement-primary-rule045.json') -Encoding utf8
    }
    $fullMode045=if($SingleLifePrimary045){'Probe032'}else{'FullObserve'}
    $observedCohort045=Join-Path $OutputRoot 'baseline-cohort.json'
    if($SingleLifePrimary045){
        $observedCohort045=Join-Path $OutputRoot 'one-profile-observation045.json'
        @($primary) | ConvertTo-Json -Depth 12 | Set-Content $observedCohort045 -Encoding utf8
    }
    & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode $fullMode045 -Episode $Episode -CohortJson $observedCohort045 -OutputRoot $fullRoot -ObserverRunId $run -EndpointPoint $endpoint *> (Join-Path $OutputRoot 'full-attach.log')
    if($LASTEXITCODE -ne 0){throw 'Existing FullObserve attach failed.'}
    $fullStarted=$true
    $frameReady=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path (Join-Path $fullRoot 'full-frame-latest.json'))){if($frameReady.Elapsed.TotalSeconds -gt 5){throw 'Full native first frame missing.'};Start-Sleep -Milliseconds 100}
    $first=ReadFrame032
    if($Mode -ceq 'Scene'){
        $caster=@($first.actors | Where-Object {$_.nativeIsMage -ceq 'true' -and $_.profileId -in $suitable.profileId} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
        if($caster.Count -ne 1){throw 'PREMEASUREMENT_NATIVE_CASTER_REQUIRED'}
        $otherRole=@($first.actors | Where-Object {$_.nativeIsMage -ceq 'false' -and $_.profileId -in $suitable.profileId} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
        $second=if($otherRole.Count){@($suitable | Where-Object {$_.profileId -ceq $otherRole[0].profileId})}else{@($suitable | Where-Object {$_.profileId -cne $caster[0].profileId} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)}
        $primary=@($baseline | Where-Object {$_.profileId -in @($caster[0].profileId,$second[0].profileId)})
        $primary | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'primary.json') -Encoding utf8
        @{method='lowest-ID native caster + lowest-ID other native role when present, else other eligible; before measurement; no counter filter';ids=@($primary.profileId)} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'premeasurement-primary-rule038.json') -Encoding utf8
    }
    if($Mode -ceq 'Probe'){
        $origin=@{x=[int]$first.observer.x;y=[int]$first.observer.y;z=[int]$first.observer.z}
        $point=@{x=$origin.x-200;y=$origin.y;z=$origin.z}
        $move=Capture032 'MOVE_SELF' $point
        if($move.status -cne 'ACCEPTED'){throw 'Probe MOVE admission missing.'}
        WaitArrival032 $point
        $return=Capture032 'MOVE_SELF' $origin
        if($return.status -cne 'ACCEPTED'){throw 'Probe return admission missing.'}
        WaitArrival032 $origin
        [ordered]@{owner='TASK045_CONTRACT';episode=$Episode;sourceSha=$FrozenSha;runId=$run;nativeMovesComplete=$true;commandCount=$script:commands;baselineSha256=(Get-FileHash (Join-Path $OutputRoot 'baseline-cohort.json')).Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputRoot 'probe-moves-complete.json') -Encoding utf8
    }
    if($Mode -ceq 'Away'){
        if([Math]::Abs($first.observer.x-[int]$path.origin.x) -gt 24 -or [Math]::Abs($first.observer.y-[int]$path.origin.y) -gt 24 -or [Math]::Abs($first.observer.z-[int]$path.origin.z) -gt 48){throw 'Actual observer origin differs from native dry path.'}
        if($SingleLifePrimary045){
            $lifeBefore045=@($first.actors | Where-Object {$_.profileId -ceq $primary[0].profileId})[0]
            $first | ConvertTo-Json -Depth 14 | Set-Content (Join-Path $OutputRoot 'life-native-before.json') -Encoding utf8
            if($DiagnosticOnly045){
                if([long]$lifeBefore045.nativeFarmCycleSequence -lt 1 -or [long]$lifeBefore045.nativeRewardSequence -lt 1 -or [long]$lifeBefore045.nativeDamageSequence -lt 1){throw 'SOURCE_DIAGNOSTIC_REQUIRES_EXISTING_NATIVE_EARNED_WORK'}
                $first | ConvertTo-Json -Depth 14 | Set-Content (Join-Path $OutputRoot 'source-existing-native-earned045.json') -Encoding utf8
            }else{
            $earnedWait045=[Diagnostics.Stopwatch]::StartNew()
            do {
                $earnedFrame045=ReadFrame032
                $earnedActor045=@($earnedFrame045.actors | Where-Object {$_.profileId -ceq $primary[0].profileId})[0]
                $earned045=$earnedActor045.worldPresent -ceq 'true' -and $earnedActor045.materializedAtNanos -ceq $lifeBefore045.materializedAtNanos -and
                    [long]$earnedActor045.nativeFarmCycleSequence -gt [long]$lifeBefore045.nativeFarmCycleSequence -and
                    [long]$earnedActor045.nativeRewardSequence -gt [long]$lifeBefore045.nativeRewardSequence -and
                    [long]$earnedActor045.nativeDamageSequence -gt [long]$lifeBefore045.nativeDamageSequence
                if($earned045){break}
                if($earnedWait045.Elapsed.TotalSeconds -gt 45){throw 'LIFE_NATIVE_EARNED_REQUIRED: preselected identity did not earn before departure.'}
                Start-Sleep -Seconds 1
            }while($true)
            $earnedFrame045 | ConvertTo-Json -Depth 14 | Set-Content (Join-Path $OutputRoot 'life-native-earned.json') -Encoding utf8
            }
            $boundaryWait045=[Diagnostics.Stopwatch]::StartNew()
            $otherHumans045=@(Import-Csv (Join-Path $OutputRoot 'enrollment-census/world-identities.tsv') -Delimiter "`t" | Where-Object {$_.headless -ceq 'false' -and $_.objectId -cne '268492939'})
            if($otherHumans045.Count){throw 'LIFE045_FOREIGN_NATIVE_OBSERVER: no departure allowed.'}
            do {
                $boundaryFrame045=ReadFrame032
                $boundaryActor045=@($boundaryFrame045.actors | Where-Object {$_.profileId -ceq $primary[0].profileId})[0]
                $safe045=Test-LifeBoundary045 -Actor $boundaryActor045 -Epoch $lifeBefore045.materializedAtNanos -OtherNativeHumans $otherHumans045.Count
                if($safe045){break}
                if($boundaryWait045.Elapsed.TotalSeconds -gt 45){throw 'LIFE045_SAFE_BOUNDARY_INPUT_ABSENT: preselected caster retained; no MOVE or replacement.'}
                Start-Sleep -Seconds 1
            }while($true)
            $boundaryFrame045 | ConvertTo-Json -Depth 14 | Set-Content (Join-Path $OutputRoot 'life-predeparture-safe045.json') -Encoding utf8
            $precondition045=if($PreconditionsFile045){$PreconditionsFile045}else{Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-SIGNAL-LIFECYCLE-045/PRECONDITIONS045.md'}
            @{contract='PROSPECTIVE_SAFE_CASTER045';profileId=$primary[0].profileId;sampleNanos=$boundaryFrame045.sampleNanos;preconditionsSha256=(Get-FileHash $precondition045).Hash;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'frozen-predeparture045.json') -Encoding utf8
        }
        $admission=Import-Csv (Join-Path $OutputRoot 'enrollment-census/endpoint-locality.tsv') -Delimiter "`t"
        foreach($id in $primary.profileId){
            $row=@($admission | Where-Object {$_.profileId -ceq $id})
            if($row.Count -ne 1 -or $row[0].worldPresent -cne 'true' -or $row[0].prewarm -cne 'false' -or $row[0].nativeVisible -cne 'false'){throw 'Endpoint lacks native no-demand proof for preselected actor.'}
        }
        MarkPhase032 'DEPART'
        Walk032 @($path.steps)
        MarkPhase032 'AWAY_ARRIVED'
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'away-census') *> (Join-Path $OutputRoot 'away-census.log')
        if($LASTEXITCODE -ne 0){throw 'Bounded away native census failed.'}
        $humans=Import-Csv (Join-Path $OutputRoot 'away-census/world-identities.tsv') -Delimiter "`t" | Where-Object {$_.headless -ceq 'false' -and $_.objectId -cne '268492939'}
        if(@($humans).Count){throw 'Other native human at away boundary.'}
        $away=[Diagnostics.Stopwatch]::StartNew()
        do{
            $frame=ReadFrame032
            foreach($id in $primary.profileId){$row=@($frame.actors | Where-Object {$_.profileId -ceq $id});if($row.Count -ne 1 -or $row[0].observerPrewarm -cne 'false' -or $row[0].observerNativeVisible -cne 'false'){throw 'Native away locality still demanded.'}}
            if(@($frame.actors | Where-Object {$_.profileId -cin $primary.profileId -and $_.worldPresent -ceq 'true'}).Count -eq 0){break}
            if($away.Elapsed.TotalSeconds -gt 120){
                if($DiagnosticOnly045){MarkPhase032 'DIAGNOSTIC_AWAY_BOUND'; break}
                throw 'SOFT_RETIRE_BOUND: preselected actors remain in World after native action/grace.'
            }
            Start-Sleep -Seconds 1
        }while($true)
        if($DiagnosticOnly045){
            MarkPhase032 'DIAGNOSTIC_DONE'
            $Seconds=1
        } else {
        MarkPhase032 'BACKGROUND_ABSENT'
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'away-early') *> (Join-Path $OutputRoot 'away-early-export.log')
        if($LASTEXITCODE -ne 0){throw 'Exact away canonical export failed.'}
        & (Join-Path $PSScriptRoot 'Invoke-SignalSnapshot045.ps1') -ProfileId ([long]$primary[0].profileId) -OutputFile (Join-Path $OutputRoot 'background-due045.properties') *> (Join-Path $OutputRoot 'background-due-read045.log')
        if($LASTEXITCODE -ne 0){throw 'Actual work due could not be measured.'}
        $due045=Get-Content -LiteralPath (Join-Path $OutputRoot 'background-due045.properties') -Raw | ConvertFrom-StringData
        $untilWork045=([long]$due045.nextWorkDueNanos-[long]$due045.sampleNanos)/1000000000.0
        if($untilWork045 -lt 0 -or $untilWork045 -gt 900 -or $due045.admission -notmatch 'effectiveState=BACKGROUND'){throw 'Ordinary background due input absent; no short-window PASS.'}
        $waitSeconds045=[int][Math]::Ceiling($untilWork045)+75
        @{nextWorkDueNanos=$due045.nextWorkDueNanos;sampleNanos=$due045.sampleNanos;actualRemainingSeconds=$untilWork045;plannedWaitSeconds=$waitSeconds045;cadenceChanged=$false} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'MEASURED_BG045.json') -Encoding utf8
        & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-away-stop045.json') -Encoding utf8
        $awayStopped045=Read-PilotProperties (Join-Path $runtime "playtest-synthetic/$run/session.properties")
        if($awayStopped045.state -cne 'STOPPED'){throw 'Original Synthetic stop required before long background wait.'}
        $syntheticStopped=$true
        [IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false))
        $heartbeatJob | Wait-Job -Timeout 10 | Out-Null
        Receive-Job $heartbeatJob -ErrorAction Continue *> (Join-Path $OutputRoot 'away-heartbeat-job.log')
        Remove-Job $heartbeatJob; $heartbeatJob=$null
        $backgroundWait045=[Diagnostics.Stopwatch]::StartNew()
        while($backgroundWait045.Elapsed.TotalSeconds -lt $waitSeconds045){Start-Sleep -Seconds ([Math]::Min(40,[Math]::Max(1,$waitSeconds045-[int]$backgroundWait045.Elapsed.TotalSeconds)))}
        @{actualWaitSeconds=$backgroundWait045.Elapsed.TotalSeconds;originalRunId=$run;originalStopped=$true} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'ACTUAL_BG_WAIT045.json') -Encoding utf8
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'away-late') *> (Join-Path $OutputRoot 'away-late-export.log')
        if($LASTEXITCODE -ne 0){throw 'Exact background step export failed.'}
        $originalRun045=$run
        $run=[guid]::NewGuid().ToString('D')
        & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-return-start045.json') -Encoding utf8
        $returnSession045=$true; $syntheticStopped=$false; $sessionWatch045=[Diagnostics.Stopwatch]::StartNew()
        @{originalRunId=$originalRun045;returnRunId=$run;profileId=$primary[0].profileId;lifetime525Unchanged=$true} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'SESSION_BOUNDARY045.json') -Encoding utf8
        MarkPhase032 'RETURN'
        $back=[Collections.Generic.List[object]]::new()
        for($i=$path.steps.Count-2;$i -ge 0;$i--){$back.Add($path.steps[$i])}
        $back.Add($path.origin); Walk032 @($back)
        MarkPhase032 'RETURNED'
        }
    }
    $first=ReadFrame032
    $sequenceBefore=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    $observation=[Diagnostics.Stopwatch]::StartNew(); $lastNanos=[long]$first.sampleNanos; $maxGap=0.0; $unique=0; $lastFresh=0.0
    $samples=[Collections.Generic.List[object]]::new()
    $samples.Add([pscustomobject]@{elapsedSeconds=0.0;sampleNanos=$first.sampleNanos;observer=$first.observer;actors=$first.actors})
    while($observation.Elapsed.TotalSeconds -lt $Seconds){
        $sessionElapsed045=if($returnSession045){$sessionWatch045.Elapsed.TotalSeconds}else{$watch.Elapsed.TotalSeconds}
        if($sessionElapsed045 -gt 480){throw 'Session budget480s exhausted; TTL not extended.'}
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
    if($Mode -ceq 'Away' -and !$DiagnosticOnly045){MarkPhase032 'POST_RETURN_DONE'}
    $sequenceAfter=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
    [ordered]@{kind=$Mode;sha=$FrozenSha;runId=$run;seconds=$observation.Elapsed.TotalSeconds;nativeUniqueSamples=$unique;maxGapSeconds=$maxGap;commandCount=$script:commands;baselineCount=$baseline.Count;primaryIds=@($primary.profileId);sameSession=(!$returnSession045);telemetryMailboxCommands=([long]$sequenceAfter.nextSequence-[long]$sequenceBefore.nextSequence);sequenceBefore=[long]$sequenceBefore.nextSequence;sequenceAfter=[long]$sequenceAfter.nextSequence;arrivalProof=($Mode -ceq 'Probe')} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'capture-result.json') -Encoding utf8
    if($StopOwnServerOnComplete){
        if($Mode -notin @('Probe','Scene','Away')){throw 'Stock endpoint stop requires a completed owned capture.'}
        # Final observation has ended. Remove the exact owned Synthetic before the stock human-online guard.
        & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8
        $syntheticEnd=Read-PilotProperties (Join-Path (Join-Path $runtime "playtest-synthetic/$run") 'session.properties')
        if($syntheticEnd.state -cne 'STOPPED'){throw 'Exact Synthetic stop required before stock server shutdown.'}
        $syntheticStopped=$true
        # Stock shutdown seals native actors; SQL is exported only after all own writers stop.
        [IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false))
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $gameBefore=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
        $loginBefore=Get-LocalPlayRoleState $runtime 'LoginServer' 'LoginServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort'),[int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginPort'))
        if($gameBefore.state -cne 'RUNNING' -or !$gameBefore.recordVerified -or $loginBefore.state -cne 'RUNNING' -or !$loginBefore.recordVerified){throw 'Both exact own incarnations required before stock stop.'}
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $OutputRoot 'stock-stop') *> (Join-Path $OutputRoot 'stock-stop.log')
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $gameEnd=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
        $loginEnd=Get-LocalPlayRoleState $runtime 'LoginServer' 'LoginServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort'),[int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginPort'))
        $stockText=[IO.File]::ReadAllText((Join-Path $OutputRoot 'stock-stop.log'))
        $typedDone=Get-Content -LiteralPath (Join-Path $OutputRoot 'stock-stop/typed-stop-done.properties') -Raw | ConvertFrom-StringData
        foreach($ended in @(@{role='GameServer';before=$gameBefore;after=$gameEnd},@{role='LoginServer';before=$loginBefore;after=$loginEnd})){
            if($ended.after.state -cnotin @('STOPPED','STALE_RECORD') -or (Get-Process -Id $ended.before.pid -ErrorAction SilentlyContinue) -or !$stockText.Contains("STOCK_GRACEFUL_STOP_CONFIRMED role=$($ended.role) pid=$($ended.before.pid)")){throw 'Exact stock stop/process absence unconfirmed.'}
        }
        if((Get-LocalPlayPortOwners (@([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))+@([int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort'),[int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginPort')))).Count -ne 0 -or $typedDone.progress -notmatch 'phase=DONE, outcome=COMPLETE,' -or $typedDone.materialization -cne 'ShutdownSnapshot[state=STOPPED, retainedEntries=0]' -or $typedDone.proofFailure){throw 'Stock drain/port-release proof unconfirmed.'}
        [ordered]@{sourceSha=$FrozenSha;gamePid=$gameBefore.pid;gameStartTicks=$gameBefore.startTimeUtcTicks;loginPid=$loginBefore.pid;loginStartTicks=$loginBefore.startTimeUtcTicks;gameTerminalState=$gameEnd.state;loginTerminalState=$loginEnd.state;stockLogSha256=(Get-FileHash (Join-Path $OutputRoot 'stock-stop.log')).Hash;typedStopSha256=(Get-FileHash (Join-Path $OutputRoot 'stock-stop/typed-stop-done.properties')).Hash;healthyStockStop=$true} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'stock-stop-verified.json') -Encoding utf8
        $serverStopped=$true
        & ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Export -Episode $Episode -ProfileIds @($baseline.profileId | ForEach-Object {[long]$_}) -OutputRoot (Join-Path $OutputRoot 'stopped-sql') *> (Join-Path $OutputRoot 'stopped-sql-export.log')
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
            if($heartbeatJob){$heartbeatJob | Wait-Job -Timeout 10 | Out-Null; Receive-Job $heartbeatJob -ErrorAction Continue *> (Join-Path $OutputRoot 'heartbeat-job.log'); Remove-Job $heartbeatJob -Force}
        }
    }
    if($fullStarted -and -not $serverStopped){
        . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
        $flushOwner=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
        if($flushOwner.state -ceq 'RUNNING' -and $flushOwner.recordVerified){& ('C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\ops045\Control032.ps1') -Action Collector -Mode Flush -Episode $Episode -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'full-flush.log')}
        else { "TASK032_FLUSH_SKIPPED state=$($flushOwner.state): no late attach to absent own JVM" | Set-Content (Join-Path $OutputRoot 'full-flush.log') -Encoding utf8 }
    }
}
