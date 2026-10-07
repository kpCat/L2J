[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha,
      [ValidateRange(360,420)][int]$Seconds=380,[string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{},[hashtable]$SetupMove=@{},[switch]$CollectSealed)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($RuntimeRoot -notmatch '[\\/]contract024[a-h][\\/]runtime[\\/]?$'){throw 'Exact TASK024 owned runtime required.'}
if(Test-Path -LiteralPath $OutputRoot){throw 'Evidence directory already exists; no overwrite.'}
$manifest=Get-Content (Join-Path $RuntimeRoot 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $FrozenSha -or (Get-FileHash (Join-Path $RuntimeRoot 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Frozen SHA/JAR mismatch.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $RuntimeRoot 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D')
$run | Set-Content (Join-Path $OutputRoot 'run-id.txt') -Encoding utf8
$counter=0
$observer=$null
function Capture023([string]$Operation,[hashtable]$Arguments=@{},[bool]$SetupMutation=$false){
    $json=& (Join-Path $RuntimeRoot 'Invoke-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run -Operation $Operation -Arguments $Arguments -TimeoutSeconds 10
    $result=$json | ConvertFrom-Json -DateKind String
    $script:counter++
    $name=('{0:D4}-{1}' -f $script:counter,$Operation)
    $json | Set-Content (Join-Path $OutputRoot "$name.json") -Encoding utf8
    $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic
    Copy-Item -LiteralPath (Join-Path $context.PilotRoot "results/$($result.requestId).xml") -Destination (Join-Path $OutputRoot "$name.xml")
    if($SetupMutation){
        if($Operation -notin @('TELEPORT_SELF','MOVE_SELF','STOP_MOVE','STATUS') -or $result.status -notin @('SUCCEEDED','ACCEPTED')){throw "SETUP_REJECTED:$Operation/$($result.reason)"}
        return $result
    }
    if($result.status -cne 'SUCCEEDED'){throw "READ_REJECTED:$Operation/$($result.reason)"}
    foreach($key in @('x','y','z','instanceId','targetId','moving','teleporting')){
        if($result.before.$key -cne $result.after.$key){throw "Read changed observer: $Operation/$key"}
        if($null -ne $script:observer -and $result.before.$key -cne $script:observer.$key){throw "Observer moved during frozen scene: $key"}
    }
    return $result
}
function Census023{
    $actors=[Collections.Generic.List[object]]::new()
    $after=0L
    for($page=0;$page -lt 8;$page++){
        $r=Capture023 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        for($i=1;$i -le [int]$r.candidate.censusCount;$i++){
            $prefix="census$i."
            $fields=[ordered]@{}
            foreach($p in $r.candidate.PSObject.Properties){if($p.Name.StartsWith($prefix)){$fields[$p.Name.Substring($prefix.Length)]=$p.Value}}
            $actors.Add([pscustomobject]$fields)
        }
        if(-not $r.candidate.PSObject.Properties['censusNextProfileId']){break}
        $next=[long]$r.candidate.censusNextProfileId
        if($next -le $after){break}
        $after=$next
    }
    return @($actors)
}
function Inventory023([string]$Label,[object[]]$Actors){
    $ids=@($Actors | ForEach-Object {[int]$_.objectId})
    if($ids.Count -eq 0){return}
    $config=Join-Path $RuntimeRoot 'game/config/Database.ini'
    $url=Get-PilotIniValue $config 'URL'
    if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract024[a-h])\?'){throw 'Owned clone DB mismatch.'}
    $db=$Matches[2]
    $saved=$env:MYSQL_PWD
    try{
        $env:MYSQL_PWD=Get-PilotIniValue $config 'Password'
        $login=Get-PilotIniValue $config 'Login'
        $idList=$ids -join ','
        & 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' --no-defaults --host=127.0.0.1 --port=3308 "--user=$login" --batch "--database=$db" -e "SELECT charId,level,exp,sp,expBeforeDeath,x,y,z FROM characters WHERE charId IN ($idList) ORDER BY charId; SELECT owner_id,object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id IN ($idList) ORDER BY owner_id,object_id;" | Set-Content (Join-Path $OutputRoot "$Label-inventory.tsv") -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'Owned raw inventory SELECT failed.'}
    }finally{$env:MYSQL_PWD=$saved}
}
$started=$false
$samples=[Collections.Generic.List[object]]::new()
try{
    & (Join-Path $RuntimeRoot 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true
    $status=Capture023 'STATUS'
    if($status.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $status.before.clientIdentity -cne 'none' -or $status.before.worldPresent -cne 'true'){throw 'Synthetic exact native identity unverified.'}
    if($SetupTeleport.Count){
        # One deterministic factual spawn-area setup, before freezing observer/cohort.
        $null=Capture023 'TELEPORT_SELF' $SetupTeleport $true
        Start-Sleep -Seconds 2
        $status=Capture023 'STATUS'
        if($status.before.teleporting -cne 'false' -or [Math]::Abs([int]$status.before.x-[int]$SetupTeleport.x) -gt 32 -or [Math]::Abs([int]$status.before.y-[int]$SetupTeleport.y) -gt 32){throw 'SETUP_ARRIVAL_UNCONFIRMED'}
    }
    if($SetupMove.Count){
        $setupWatch=[Diagnostics.Stopwatch]::StartNew()
        Push-Location (Join-Path $RuntimeRoot 'game')
        try{
            & java -cp '../libs/*' (Join-Path $PSScriptRoot 'ReadDryPath023.java') ([string]$status.before.x) ([string]$status.before.y) ([string]$status.before.z) ([string]$SetupMove.x) ([string]$SetupMove.y) *> (Join-Path $OutputRoot 'setup-dry-path.txt')
            if($LASTEXITCODE -ne 0){throw 'Factual stock dry setup route rejected; no MOVE requested.'}
        }finally{Pop-Location}
        do{
            if($setupWatch.Elapsed.TotalSeconds -gt 70){throw 'Bounded native setup movement deadline.'}
            $status=Capture023 'STATUS' @{} $true
            $dx=[int]$SetupMove.x-[int]$status.before.x; $dy=[int]$SetupMove.y-[int]$status.before.y
            $distance=[Math]::Sqrt($dx*$dx+$dy*$dy)
            if($distance -le 24){break}
            $scale=[Math]::Min(1.0,300.0/$distance)
            $nextX=[int]$status.before.x+[int]($dx*$scale); $nextY=[int]$status.before.y+[int]($dy*$scale)
            $null=Capture023 'MOVE_SELF' @{x="$nextX";y="$nextY";z=[string]$status.before.z} $true
            $stepWatch=[Diagnostics.Stopwatch]::StartNew()
            do{
                Start-Sleep -Milliseconds 500
                $status=Capture023 'STATUS' @{} $true
                if($status.before.moving -ceq 'false' -and [Math]::Sqrt([Math]::Pow([int]$status.before.x-$nextX,2)+[Math]::Pow([int]$status.before.y-$nextY,2)) -le 32){break}
                if($stepWatch.Elapsed.TotalSeconds -gt 12){throw 'Native setup move arrival unconfirmed.'}
            }while($true)
        }while($true)
        $null=Capture023 'STOP_MOVE' @{} $true
        $status=Capture023 'STATUS'
    }
    $script:observer=$status.before
    $warm=[Diagnostics.Stopwatch]::StartNew()
    do{
        $baseline=@(Census023)
        $eligible=@($baseline | Where-Object {$_.eligible -ceq 'true' -and [int]$_.npcId -gt 0})
        if($eligible.Count -ge 4){break}
        if($warm.Elapsed.TotalSeconds -ge 60){throw "ENVIRONMENT_GAP: $($eligible.Count) eligible / $($baseline.Count) total natural actors after 60s."}
        Start-Sleep -Seconds 5
    }while($true)
    # Freeze every visible natural actor, including idle/dead members; no success filtering.
    $primary=@($eligible | Where-Object {[string]$_.profileId -notin $PreviousPrimaryIds} | Sort-Object {[long]$_.profileId} | Select-Object -First 2)
    if($primary.Count -ne 2){throw 'ENVIRONMENT_GAP: different preselected primary pair unavailable.'}
    $baseline | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'baseline-cohort.json') -Encoding utf8
    $primary | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'primary.json') -Encoding utf8
    Inventory023 'baseline' $baseline
    if($CollectSealed){
        & (Join-Path $PSScriptRoot 'Arm-Collector024.ps1') -RuntimeRoot $RuntimeRoot -CohortJson (Join-Path $OutputRoot 'baseline-cohort.json') -OutputRoot (Join-Path $OutputRoot 'sealed') -FollowCurrentEpochs
    }
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $samples.Add([pscustomobject]@{elapsedSeconds=0;actors=$baseline})
    while($watch.Elapsed.TotalSeconds -lt $Seconds){
        Start-Sleep -Seconds 4
        $actors=@(Census023)
        $sample=[pscustomobject]@{elapsedSeconds=$watch.Elapsed.TotalSeconds;actors=$actors}
        $samples.Add($sample)
        $sample | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot ('cohort-{0:D3}.json' -f $samples.Count)) -Encoding utf8
    }
    $final=@($samples[$samples.Count-1].actors)
    Inventory023 'final-live' $baseline
    $tail=@($samples | Where-Object {$_.elapsedSeconds -le ($watch.Elapsed.TotalSeconds-120)} | Select-Object -Last 1)[0]
    $rows=@(foreach($initial in $baseline){
        $missing=0; $changedEpoch=$false; $lastReward=[long]$initial.nativeRewardSequence; $lastProgress=0.0; $maxIdle=0.0
        foreach($sample in $samples){
            $seen=@($sample.actors | Where-Object {$_.profileId -ceq $initial.profileId})
            if($seen.Count -ne 1){$missing++; continue}
            if($seen[0].materializedAtNanos -cne $initial.materializedAtNanos -or $seen[0].nativeEvidenceEpoch -cne $initial.nativeEvidenceEpoch){$changedEpoch=$true}
            if([long]$seen[0].nativeRewardSequence -gt $lastReward){$maxIdle=[Math]::Max($maxIdle,$sample.elapsedSeconds-$lastProgress); $lastProgress=$sample.elapsedSeconds; $lastReward=[long]$seen[0].nativeRewardSequence}
        }
        $maxIdle=[Math]::Max($maxIdle,$watch.Elapsed.TotalSeconds-$lastProgress)
        $last=@($final | Where-Object {$_.profileId -ceq $initial.profileId})
        $tailActor=@($tail.actors | Where-Object {$_.profileId -ceq $initial.profileId})
        $same=$last.Count -eq 1 -and $tailActor.Count -eq 1 -and $last[0].materializedAtNanos -ceq $initial.materializedAtNanos -and $last[0].nativeEvidenceEpoch -ceq $initial.nativeEvidenceEpoch
        $cycles=if($same){[long]$last[0].nativeFarmCycleSequence-[long]$initial.nativeFarmCycleSequence}else{0}
		$rewards=if($same){[long]$last[0].nativeRewardSequence-[long]$initial.nativeRewardSequence}else{0}
		$kills=if($same){[long]$last[0].nativeKillSequence-[long]$initial.nativeKillSequence}else{0}
		$damage=if($same){[long]$last[0].nativeDamageSequence-[long]$initial.nativeDamageSequence}else{0}
        $targets=if($same){[long]$last[0].nativeTargetSequence-[long]$initial.nativeTargetSequence}else{0}
        $tailRewards=if($same){[long]$last[0].nativeRewardSequence-[long]$tailActor[0].nativeRewardSequence}else{0}
        $isPrimary=[string]$initial.profileId -in @($primary | ForEach-Object {[string]$_.profileId})
        $exp=if($same){[long]$last[0].nativeExpGained-[long]$initial.nativeExpGained}else{0}
        $sp=if($same){[long]$last[0].nativeSpGained-[long]$initial.nativeSpGained}else{0}
        $minimum=if($isPrimary){5}else{2}
        $pass=$same -and $missing -eq 0 -and !$changedEpoch -and $maxIdle -le 90 -and $cycles -ge $minimum -and $kills -ge $minimum -and $damage -ge $minimum -and $rewards -ge $minimum -and $targets -ge $minimum -and $tailRewards -ge 1 -and $exp -gt 0 -and $sp -gt 0 -and $last[0].nativeEvidenceOverflow -ceq 'false' -and $last[0].pendingOwnedStore -ceq 'false' -and $last[0].cleanupPhase -ceq 'NONE'
        [pscustomobject]@{profileId=$initial.profileId;objectId=$initial.objectId;primary=$isPrimary;sameEpoch=$same;missingSamples=$missing;changedEpoch=$changedEpoch;maxIdleSeconds=$maxIdle;cycles=$cycles;kills=$kills;damage=$damage;rewards=$rewards;targetTransitions=$targets;tail120Rewards=$tailRewards;exp=$exp;sp=$sp;pass=$pass;last=if($last.Count){$last[0]}else{$null}}
    })
    $rows | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'cohort-result.json') -Encoding utf8
    [ordered]@{frozenSha=$FrozenSha;runId=$run;elapsedSeconds=$watch.Elapsed.TotalSeconds;cohortCount=$baseline.Count;farmPass=(@($rows | Where-Object {-not $_.pass}).Count -eq 0);realFinalPass='NOT_RUN';primaryIds=@($primary.profileId)} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'result.json') -Encoding utf8
    $rows | Select-Object profileId,primary,sameEpoch,cycles,rewards,targetTransitions,tail120Rewards,exp,sp,pass | Format-Table -AutoSize
}finally{
    $samples | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $OutputRoot 'all-samples.json') -Encoding utf8
    if($started){& (Join-Path $RuntimeRoot 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8}
}
