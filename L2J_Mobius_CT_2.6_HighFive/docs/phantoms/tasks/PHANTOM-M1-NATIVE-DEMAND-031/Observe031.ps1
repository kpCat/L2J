[CmdletBinding()]
param([ValidateSet('a','b','t')][string]$Episode='a',[Parameter(Mandatory)][string]$OutputRoot,[string]$FrozenSha='e083f35d9b3b1c1441f484c8f760c8dc34bbdbc0',[long]$SetupProfileId=0,[long[]]$DebugProfileIds=@())
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedJson031.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract031$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if(-not $OutputRoot.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $OutputRoot)){throw 'Immutable own TASK031 output required.'}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $FrozenSha -or $manifest.databaseName -cne "l2jmobiush5_localplay_contract031$Episode"){throw 'Exact own source/database required.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D'); $started=$false; $heartbeatJob=$null
$stopWriter=Join-Path $OutputRoot 'heartbeat-stop.request'; $fullRoot=Join-Path $OutputRoot 'full-native'; $script:commands031=0
function Capture031([string]$Operation,[hashtable]$Arguments=@{}){
    $script:commands031++; if($script:commands031 -gt 80){throw 'Bounded probe command budget.'}
    $raw=& (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -Arguments $Arguments -RunId $run -ActorMode Synthetic -ExternalHeartbeat -TimeoutSeconds 20
    $result=$raw | ConvertFrom-Json
    $raw | Set-Content (Join-Path $OutputRoot (('{0:D2}-' -f $script:commands031)+$Operation+'.json')) -Encoding utf8
    if($result.runId -cne $run -or $result.status -notin @('SUCCEEDED','ACCEPTED')){throw ('Exact Synthetic operation rejected:'+ $result.status+':'+$result.reason)}
    return $result
}
try{
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true
    $heartbeatJob=Start-Job -ArgumentList $runtime,$run,$OutputRoot,$stopWriter -ScriptBlock {
        param($Runtime,$Run,$Output,$Stop)
        $ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedJson031.ps1'); . (Join-Path $Runtime 'LocalPlay-Pilot.ps1')
        $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $Run
        $watch=[Diagnostics.Stopwatch]::StartNew()
        while($watch.Elapsed.TotalSeconds -lt 525 -and -not (Test-Path $Stop)){
            $process=Get-Process -Id $context.Pid -ErrorAction Stop
            if($process.StartTime.ToUniversalTime().Ticks -ne [long]$context.StartTimeUtcTicks){throw 'Heartbeat incarnation changed'}
            Write-PilotHeartbeat $context $Run $Run
            [DateTime]::UtcNow.ToString('O') | Add-Content (Join-Path $Output 'heartbeat.tsv') -Encoding utf8
            Start-Sleep -Seconds 5
        }
    }
    $wait031=[Diagnostics.Stopwatch]::StartNew()
    while(-not (Test-Path (Join-Path $OutputRoot 'heartbeat.tsv'))){if($wait031.Elapsed.TotalSeconds -gt 10 -or $heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat unavailable'};Start-Sleep -Milliseconds 100}
    $status=Capture031 'STATUS'
    if($status.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $status.before.clientIdentity -cne 'none' -or $status.before.worldPresent -cne 'true'){throw 'Native Synthetic identity missing'}
    @([pscustomobject]@{profileId=275;materializedAtNanos=0}) | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'bootstrap.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Control031.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson (Join-Path $OutputRoot 'bootstrap.json') -OutputRoot (Join-Path $OutputRoot 'initial-census') *> (Join-Path $OutputRoot 'initial-census.log')
    if($LASTEXITCODE -ne 0){throw 'Initial census failed'}
    $globalRows=@(Import-Csv (Join-Path $OutputRoot 'initial-census/global-admission.tsv') -Delimiter "`t")
    $eligible=@($globalRows | Where-Object {$_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.instanceId -ceq '0'})
    $ranked=@($eligible | ForEach-Object {
        $center=$_; $members=@($eligible | Where-Object {[Math]::Pow([double]$_.x-[double]$center.x,2)+[Math]::Pow([double]$_.y-[double]$center.y,2) -le 2250000})
        [pscustomobject]@{profileId=[long]$center.profileId;x=[int]$center.x;y=[int]$center.y;z=[int]$center.z;count=$members.Count;members=$members;distance=[Math]::Pow([double]$center.x-[double]$status.before.x,2)+[Math]::Pow([double]$center.y-[double]$status.before.y,2)}
    } | Sort-Object @{Expression='count';Descending=$true},distance,profileId)
    if($SetupProfileId -gt 0){$ranked=@($ranked | Where-Object {$_.profileId -eq $SetupProfileId})}
    if($ranked.Count -eq 0){throw 'NO_PARTICIPATING_READY_ONLINE_DURABLE_READY_INSTANCE0'}
    $fixed=$ranked[0]
    $tracked=@($fixed.members | Sort-Object {[Math]::Pow([double]$_.x-$fixed.x,2)+[Math]::Pow([double]$_.y-$fixed.y,2)}, @{Expression={[long]$_.profileId}} | Select-Object -First 8)
    if($DebugProfileIds.Count){
        if($DebugProfileIds.Count -gt 8 -or @($DebugProfileIds | Select-Object -Unique).Count -ne $DebugProfileIds.Count){throw 'Bounded unique diagnostic subjects required'}
        $tracked=@($globalRows | Where-Object {[long]$_.profileId -in $DebugProfileIds} | Sort-Object {[long]$_.profileId})
        if($tracked.Count -ne $DebugProfileIds.Count){throw 'All fixed diagnostic subjects must exist in initial census'}
        # Diagnostic state subjects include DEAD/terminal; these are never counted as healthy admission candidates.
    }
    $tracked | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'fixed-before-outcomes.json') -Encoding utf8
    $cohort=Join-Path $OutputRoot 'tracked.json'; @($tracked | ForEach-Object {[pscustomobject]@{profileId=[long]$_.profileId;materializedAtNanos=0}}) | ConvertTo-Json | Set-Content $cohort -Encoding utf8
    & (Join-Path $PSScriptRoot 'Control031.ps1') -Action DryPath -Episode $Episode -OriginPoint @{x=$fixed.x;y=$fixed.y;z=$fixed.z} -EndpointPoint @{x=$fixed.x-200;y=$fixed.y} -OutputRoot (Join-Path $OutputRoot 'setup-dry') *> (Join-Path $OutputRoot 'setup-dry.log')
    if($LASTEXITCODE -ne 0){throw 'Native dry geometry rejected'}
    & (Join-Path $PSScriptRoot 'Control031.ps1') -Action Collector -Mode Probe031 -Episode $Episode -CohortJson $cohort -OutputRoot $fullRoot -ObserverRunId $run *> (Join-Path $OutputRoot 'probe-attach.log')
    if($LASTEXITCODE -ne 0){throw 'Fixed absent/native probe attach failed'}
    $framePath=Join-Path $fullRoot 'full-frame-latest.json'
    if(-not (Test-Path $framePath)){throw 'T0 frame absent'}
    Copy-Item -LiteralPath $framePath -Destination (Join-Path $OutputRoot 'T0-before-approach.json')
    $record=Get-Content (Join-Path $runtime 'local-play/pids/GameServer.json') -Raw | ConvertFrom-Json
    $debug=Join-Path (Split-Path $runtime -Parent) 'debug-first'
    $jcmd='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe'
    & $jcmd $record.pid JFR.start ('name='+(Split-Path $OutputRoot -Leaf)) settings=profile duration=60s ('filename='+$debug+'/'+(Split-Path $OutputRoot -Leaf)+'-60s.jfr') 'jdk.JavaExceptionThrow#enabled=true' 'jdk.JavaExceptionThrow#stackTrace=true' *> (Join-Path $debug 'jfr-start.txt')
    if($LASTEXITCODE -ne 0){throw 'JFR capture did not start'}
    $approachUTC=[DateTime]::UtcNow.ToString('O'); $watch031=[Diagnostics.Stopwatch]::StartNew()
    $setup=Capture031 'SNAPSHOT_PHANTOMS' @{setupProfileId=[long]$fixed.profileId}
    if($setup.reason -cne 'SETUP_CANDIDATE_SNAPSHOT' -or [int]$setup.candidate.x -ne $fixed.x -or [int]$setup.candidate.y -ne $fixed.y -or $setup.candidate.admissionGranted -cne 'false'){throw 'Exact readonly setup point missing'}
    $null=Capture031 'TELEPORT_SELF' @{x=$fixed.x;y=$fixed.y;z=$fixed.z;instanceId=0}
    foreach($second031 in @(5,15,30,60,90)){
        while($watch031.Elapsed.TotalSeconds -lt $second031){if($heartbeatJob.State -ceq 'Failed'){throw 'Heartbeat failed during probe'};Start-Sleep -Milliseconds 250}
        $frame=Read-SharedJson031 $framePath
        if($frame.proofFailure){throw ('OBSERVER_PROOF_INVALID:'+ $frame.proofFailure)}
        if($frame.observer.runId -cne $run -or $frame.observer.sessionState -cne 'RUNNING' -or -not $frame.observer.present -or -not $frame.observer.online -or $frame.observer.dead){throw 'Current native Synthetic unavailable'}
        $frame | ConvertTo-Json -Depth 16 | Set-Content (Join-Path $OutputRoot ("T$second031-after-approach.json")) -Encoding utf8
        Write-Output "PROBE031 T=$second031 world=$(@($frame.actors | Where-Object {$_.worldPresent -ceq 'true'}).Count) physical=$(@($frame.actors | Where-Object {$_.physicalDemand -ceq 'true'}).Count)"
    }
    & $jcmd $record.pid Thread.print -l *> (Join-Path $debug 'threads-after.txt')
    & $jcmd $record.pid JFR.check ('name='+(Split-Path $OutputRoot -Leaf)) *> (Join-Path $debug 'jfr-check.txt')
    & (Join-Path $PSScriptRoot 'Control031.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson $cohort -OutputRoot (Join-Path $OutputRoot 'after-census') *> (Join-Path $OutputRoot 'after-census.log')
    if($LASTEXITCODE -ne 0){throw 'Post-approach census failed'}
    [ordered]@{mode='PROBE031';runId=$run;codeSha=$FrozenSha;approachUTC=$approachUTC;seconds=$watch031.Elapsed.TotalSeconds;trackedIds=@($tracked.profileId);setupEligibleCount=$eligible.Count;oldSetupMatches=@($eligible | Where-Object {$_.farmAllowed -ceq 'true' -and $_.readinessComplete -ceq 'true'}).Count;worldPresent=@($frame.actors | Where-Object {$_.worldPresent -ceq 'true'}).Count;physicalDemand=@($frame.actors | Where-Object {$_.physicalDemand -ceq 'true'}).Count;commandCount=$script:commands031} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputRoot 'capture-result.json') -Encoding utf8
}catch{
    $_ | Out-String | Set-Content (Join-Path $OutputRoot 'probe-failure.txt') -Encoding utf8
    throw
}finally{
    if($started){
        try{& (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8}
        finally{[IO.File]::WriteAllText($stopWriter,'STOP',[Text.UTF8Encoding]::new($false));if($heartbeatJob){$heartbeatJob | Wait-Job -Timeout 10 | Out-Null;Receive-Job $heartbeatJob *> (Join-Path $OutputRoot 'heartbeat-job.log');Remove-Job $heartbeatJob -Force}}
    }
    & (Join-Path $PSScriptRoot 'Control031.ps1') -Action Collector -Mode Flush -Episode $Episode -OutputRoot $fullRoot *> (Join-Path $OutputRoot 'flush.log')
}
