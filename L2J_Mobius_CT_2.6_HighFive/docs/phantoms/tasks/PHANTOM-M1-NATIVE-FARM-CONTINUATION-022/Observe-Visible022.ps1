[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot, [Parameter(Mandatory)][string]$OutputRoot,
      [ValidateSet('BASELINE','POLL','RUN')][string]$Stage='BASELINE', [double]$RemainingSeconds=300)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ($RuntimeRoot -notmatch '[\\/]observe022([ab])[\\/]runtime[\\/]?$') { throw 'Exact TASK022 episode runtime required.' }
$episode=$Matches[1]
if ($Stage -eq 'RUN') {
    & $PSCommandPath -RuntimeRoot $RuntimeRoot -OutputRoot $OutputRoot -Stage BASELINE
    $first=Get-Content (Join-Path $OutputRoot 'baseline.json') -Raw | ConvertFrom-Json -DateKind String
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $samples=[Collections.Generic.List[object]]::new()
    $cohorts=[Collections.Generic.List[object]]::new()
    $last=$first.census; $lastDecisionChange=0.0; $lastPlayChange=0.0; $lastUseChange=0.0
    $dumpTriggered=-1.0; $fiveReached=-1.0; $next=2.0; $nextCohort=10.0; $stopReason='LIMIT300'
    . (Join-Path $RuntimeRoot 'LocalPlay-Ownership.ps1')
    try {
        while ($watch.Elapsed.TotalSeconds -lt 300) {
            $wait=($next-$watch.Elapsed.TotalSeconds)*1000
            if ($wait -gt 0) { Start-Sleep -Milliseconds ([int][Math]::Min($wait,2000)) }
            if ($watch.Elapsed.TotalSeconds -ge 300) { break }
            if (300-$watch.Elapsed.TotalSeconds -lt 14) { break }
            try { & $PSCommandPath -RuntimeRoot $RuntimeRoot -OutputRoot $OutputRoot -Stage POLL -RemainingSeconds (300-$watch.Elapsed.TotalSeconds) }
            catch { if ("$_" -like '*READ_LIMIT300*') { break }; throw }
            $latest=Get-ChildItem $OutputRoot -Filter 'POLL-*.json' | Where-Object { $_.Name -match '^POLL-[0-9]+\.json$' } | Sort-Object Name | Select-Object -Last 1
            $sample=Get-Content $latest.FullName -Raw | ConvertFrom-Json -DateKind String
            $sample.elapsedSeconds=$watch.Elapsed.TotalSeconds
            $samples.Add($sample); $now=$watch.Elapsed.TotalSeconds; $fresh=$sample.census
            if ($fresh.decisionSequence -cne $last.decisionSequence) { $lastDecisionChange=$now }
            if ($fresh.liveAutoPlayTickFinishedNanos -cne $last.liveAutoPlayTickFinishedNanos) { $lastPlayChange=$now }
            if ($fresh.liveAutoUseTickFinishedNanos -cne $last.liveAutoUseTickFinishedNanos) { $lastUseChange=$now }
            if ($now -ge $nextCohort) {
                $cohortFile=Get-ChildItem $OutputRoot -Filter '*-cohort.json' | Sort-Object Name | Select-Object -Last 1
                $cohorts.Add([pscustomobject]@{elapsedSeconds=$now;actors=(Get-Content $cohortFile.FullName -Raw | ConvertFrom-Json -DateKind String)})
                $nextCohort=$now+10
            }
            $due=$fresh.casting -ceq 'true' -or ([int]$fresh.longTargets -gt 0 -and $fresh.dead -ceq 'false' -and $fresh.pendingOwnedStore -ceq 'false')
            $premature=$last.liveAutoPlayRegistered -ceq 'true' -and $fresh.liveAutoPlayRegistered -ceq 'false'
            if ($dumpTriggered -lt 0 -and ($premature -or ($due -and (($now-$lastDecisionChange) -ge 10 -or ($now-$lastPlayChange) -ge 10 -or ($now-$lastUseChange) -ge 10)))) {
                $dumpTriggered=$now
                $sample | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputRoot 'freeze.json') -Encoding utf8
                $state=Get-LocalPlayRoleState $RuntimeRoot 'GameServer' 'GameServer.jar' @(7777)
                if (-not $state.recordVerified -or $state.state -cne 'RUNNING') { throw 'Exact owned Game JVM missing for diagnostic attach.' }
                $owned=Get-Process -Id $state.pid
                if ($owned.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks) { throw 'Game PID incarnation changed.' }
                $jcmd='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe'
                foreach ($index in 0..3) {
                    $command=if ($index -eq 0) { @($state.pid,'help','Thread.print') } else { @($state.pid,'Thread.print','-l') }
                    $process=Start-Process -FilePath $jcmd -ArgumentList $command -WindowStyle Hidden -RedirectStandardOutput (Join-Path $OutputRoot "thread-$index.log") -RedirectStandardError (Join-Path $OutputRoot "thread-$index.err") -PassThru
                    if (-not $process.WaitForExit(10000)) { throw 'jcmd exceeded10s; no JVM force.' }
                    if ($index -in 1,2) { Start-Sleep -Seconds 2 }
                }
            }
            $cycles=[long]$fresh.nativeFarmCycleSequence-[long]$first.census.nativeFarmCycleSequence
            if ($fiveReached -lt 0 -and $cycles -ge 5 -and $fresh.nativeEvidenceOverflow -ceq 'false') { $fiveReached=$now }
            if ($dumpTriggered -ge 0 -and $now-$dumpTriggered -ge 30) { $stopReason='FREEZE_DIAGNOSTIC_TAIL30'; break }
            if ($fiveReached -ge 0 -and $now-$fiveReached -ge 30) { $stopReason='FIVE_CYCLES_PLUS30'; break }
            $last=$fresh; $next=[Math]::Max($next+2,$watch.Elapsed.TotalSeconds)
        }
    } catch { $stopReason='ERROR:'+$_; throw }
    finally {
        [ordered]@{baseline=$first;elapsedSeconds=$watch.Elapsed.TotalSeconds;stopReason=$stopReason;samples=@($samples);cohort=@($cohorts);STOP_AUTHORITY='TASK022_CONTRACT'} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $PSScriptRoot "EVIDENCE022$episode.json") -Encoding utf8
    }
    return
}
. (Join-Path $RuntimeRoot 'LocalPlay-Pilot.ps1')
$readWatch=[Diagnostics.Stopwatch]::StartNew()
$context=Get-PilotContext -RequireEnabled -ActorMode RealClient
if ($Stage -eq 'BASELINE') {
    if (Test-Path -LiteralPath $OutputRoot) { throw 'No automatic observation restart.' }
    $session=Get-PilotSession $context
    if ($session.state -cne 'ARMED_IDLE') { throw 'Manual actor must be ARMED_IDLE.' }
    New-Item -ItemType Directory -Path $OutputRoot | Out-Null
    $run=[guid]::NewGuid().ToString('D')
    $run | Set-Content (Join-Path $OutputRoot 'run-id.txt') -Encoding utf8
    $observer=$null
} else {
    $run=(Get-Content (Join-Path $OutputRoot 'run-id.txt') -Raw).Trim()
    $baseline=Get-Content (Join-Path $OutputRoot 'baseline.json') -Raw | ConvertFrom-Json -DateKind String
    $observer=$baseline.observer
    $watched=@($baseline.cohortIds)
    $started=[DateTimeOffset]::Parse($baseline.utc)
    if (([DateTimeOffset]::UtcNow-$started).TotalSeconds -gt 300) { throw 'Same-epoch observation limit300s reached.' }
}
function Capture([string]$Label,[ValidateSet('STATUS','SNAPSHOT_PHANTOMS','SELECT_VISIBLE_PHANTOM_TRACE','SNAPSHOT_SELECTED_PHANTOM_TRACE')][string]$Operation,[hashtable]$Arguments=@{}) {
    if ($Stage -eq 'POLL' -and $RemainingSeconds-$readWatch.Elapsed.TotalSeconds -lt 14) { throw 'READ_LIMIT300' }
    $json=& (Join-Path $RuntimeRoot 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -RunId $run -Arguments $Arguments -ActorMode RealClient -TimeoutSeconds 10
    $result=$json | ConvertFrom-Json -DateKind String
    $json | Set-Content (Join-Path $OutputRoot "$Label.json") -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $context.PilotRoot "results/$($result.requestId).xml") -Destination (Join-Path $OutputRoot "$Label.xml")
    if ($result.status -cne 'SUCCEEDED') { throw "Read-only $Operation rejected: $($result.reason)" }
    foreach ($key in @('x','y','z','instanceId','targetId','moving','teleporting')) {
        if ($result.before.$key -cne $result.after.$key) { throw "Observer changed within $Operation/$key" }
        if ($null -ne $observer -and $result.before.$key -cne $observer.$key) { throw "Observer changed since baseline/$key" }
    }
    Write-Host "$Label status=$($result.status) reason=$($result.reason) utc=$($result.endUtc)"
    return $result
}
function Census([string]$Label,[string]$Profile) {
    $after=0L
    $cohort=[Collections.Generic.List[object]]::new()
    $chosen=$null
    for ($page=0;$page -lt 8;$page++) {
        $result=Capture "$Label-page$page" 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        for ($index=1;$index -le [int]$result.candidate.censusCount;$index++) {
            $prefix="census$index."
            $fields=[ordered]@{}
            foreach ($property in $result.candidate.PSObject.Properties) { if ($property.Name.StartsWith($prefix)) { $fields[$property.Name.Substring($prefix.Length)]=$property.Value } }
            if (($Stage -eq 'BASELINE' -and $cohort.Count -lt 4) -or ($Stage -eq 'POLL' -and [string]$fields.profileId -in $watched)) { $cohort.Add([pscustomobject]$fields) }
            if ([string]$fields.profileId -ceq $Profile) { $chosen=[pscustomobject]@{utc=$result.endUtc;fields=[pscustomobject]$fields} }
        }
        $next=[long]$result.candidate.censusNextProfileId
        if ($next -le $after) { break }
        $after=$next
    }
    $cohort | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot "$Label-cohort.json") -Encoding utf8
    if ($null -ne $chosen) { return $chosen }
    throw 'Selected natural visible epoch absent from census; stop observation.'
}
if ($Stage -eq 'BASELINE') {
    $status=Capture 'STATUS' 'STATUS'
    if ($status.before.identityOwner -cne 'REAL_LOGIN' -or $status.before.online -cne 'true' -or $status.before.worldPresent -cne 'true' -or $status.before.clientIdentity -ceq 'none' -or $status.before.moving -cne 'false' -or $status.before.teleporting -cne 'false') { throw 'REAL_LOGIN/IN_GAME passive actor not verified.' }
    $observer=$status.before
    $initial=Capture 'PHANTOMS-initial' 'SNAPSHOT_PHANTOMS' @{includeCensus='true'}
    $startupDeadline=[DateTimeOffset]::UtcNow.AddSeconds(60)
    $startupSample=0
    while ([int]$initial.candidate.censusEligible -eq 0) {
        if ([DateTimeOffset]::UtcNow -ge $startupDeadline) { throw 'No natural visible eligible Player within one60s startup window.' }
        Start-Sleep -Seconds 2
        $initial=Capture ('PHANTOMS-startup'+(++$startupSample)) 'SNAPSHOT_PHANTOMS' @{includeCensus='true'}
    }
    $selected=Capture 'SELECT' 'SELECT_VISIBLE_PHANTOM_TRACE'
    $profile=[string]$selected.candidate.profileId
    $trace=Capture 'TRACE-baseline' 'SNAPSHOT_SELECTED_PHANTOM_TRACE'
    $census=Census 'CENSUS-baseline' $profile
    $baselineCohort=@(Get-Content (Join-Path $OutputRoot 'CENSUS-baseline-cohort.json') -Raw | ConvertFrom-Json -DateKind String)
    $cohortIds=@($profile)+@($baselineCohort | Where-Object { [string]$_.profileId -cne $profile } | Select-Object -First 3 | ForEach-Object { [string]$_.profileId })
    $record=[ordered]@{utc=$census.utc;observer=$observer;profileId=$profile;trace=$trace.candidate;census=$census.fields;cohortIds=$cohortIds;selection='existing read-only nearest visible selection, before baseline; no reselection';preferred110=($profile -ceq '110')}
    $record | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'baseline.json') -Encoding utf8
    Write-Output "BASELINE profile=$profile object=$($census.fields.objectId) epoch=$($census.fields.materializedAtNanos) goal=$($census.fields.goalRevision)/$($census.fields.runtimeGoalRevision) cycles=$($census.fields.nativeFarmCycleSequence) kills=$($census.fields.nativeKillSequence) exp=$($census.fields.nativeExpGained) sp=$($census.fields.nativeSpGained) reason=$($census.fields.runtimeReason)"
} else {
    $label='POLL-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $trace=Capture "$label-trace" 'SNAPSHOT_SELECTED_PHANTOM_TRACE'
    $census=Census "$label-census" ([string]$baseline.profileId)
    foreach ($key in @('profileId','objectId','materializedAtNanos','nativeEvidenceEpoch')) {
        if ($census.fields.$key -cne $baseline.census.$key) { throw "Selected epoch changed/$key; stop without reselection." }
    }
    $elapsed=([DateTimeOffset]::Parse($census.utc)-$started).TotalSeconds
    [ordered]@{utc=$census.utc;elapsedSeconds=$elapsed;trace=$trace.candidate;census=$census.fields} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot "$label.json") -Encoding utf8
    Write-Output "POLL elapsed=$elapsed profile=$($baseline.profileId) goal=$($census.fields.goalRevision)/$($census.fields.runtimeGoalRevision) guard=$($census.fields.currentActionGuard) cycles=$($census.fields.nativeFarmCycleSequence) kills=$($census.fields.nativeKillSequence) targets=$($census.fields.nativeTargetSequence) exp=$($census.fields.nativeExpGained) sp=$($census.fields.nativeSpGained) reason=$($census.fields.runtimeReason) travel=$($census.fields.travelReason) recovery=$($census.fields.visibleRecoveryReason)"
}
