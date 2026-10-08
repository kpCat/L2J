[CmdletBinding()]
param([ValidateSet('Probe','Discovery','Scene')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [string]$FrozenSha='', [string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{}, [hashtable]$SetupMove=@{},
      [switch]$CollectSealed, [long[]]$ProbeProfileIds=@(452))
$ErrorActionPreference='Stop'
$taskRoot025=$PSScriptRoot
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtimeHeartbeat025=Join-Path $module025 ".phantom-local/contract025$Episode/runtime"
$file025=if($Mode -cne 'Probe'){'Observe-Scene024.ps1'}else{'Observe-Prelude024.ps1'}
$body025=[IO.File]::ReadAllText((Join-Path $taskRoot024 $file025))
if($Mode -cne 'Probe'){
    $pilotBody025=[IO.File]::ReadAllText((Join-Path $runtimeHeartbeat025 'Invoke-LocalPlayPilot.ps1'))
    $heartbeatCall025='Write-PilotHeartbeat $context ([string] $session.sessionId) $effectiveRunId'
    if(([regex]::Matches($pilotBody025,[regex]::Escape($heartbeatCall025))).Count -ne 2){throw 'Existing pilot heartbeat call shape changed.'}
    $pilotBody025=$pilotBody025.Replace('$PSScriptRoot','$runtimeHeartbeat025').Replace($heartbeatCall025,'# Independent TASK025 heartbeat owns this record; native watchdog and TTL are unchanged.')
    $script:pilotInvocation025=[scriptblock]::Create($pilotBody025)
    $body025=$body025.Replace("& (Join-Path `$RuntimeRoot 'Invoke-LocalPlayPilot.ps1')",'& $script:pilotInvocation025')
}
if($Mode -ceq 'Discovery'){
    $body025=$body025.Replace('[ValidateRange(360,420)]','[ValidateRange(60,120)]')
    $body025=$body025.Replace('    $final=@($samples[$samples.Count-1].actors)', '    [ordered]@{purpose="FIRST_PRODUCER_DISCOVERY";elapsedSeconds=$watch.Elapsed.TotalSeconds;frozenSha=$FrozenSha;finalSceneClaim=$false} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot "discovery-result.json") -Encoding utf8; return')
}
$body025=$body025.Replace("& (Join-Path `$PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot `$runtime", "& (Join-Path `$taskRoot025 'Control025.ps1') -Action Export -Episode `$Episode")
$body025=$body025.Replace('-ProfileIds @($ids)','-ProfileIds $ProbeProfileIds')
$body025=$body025.Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract025').Replace('contract025c','contract025[a-h]')
$body025=$body025.Replace("& (Join-Path `$taskRoot024 'Arm-Collector024.ps1') -RuntimeRoot `$RuntimeRoot", "& (Join-Path `$taskRoot025 'Collector025.ps1') -Mode Observe -Episode `$Episode")
$body025=$body025.Replace(' -FollowCurrentEpochs','')
# Leave time for the existing mailbox census round trip inside the <=5s sampling bound.
$body025=$body025.Replace('Start-Sleep -Seconds 4','Start-Sleep -Seconds 2')
$body025=$body025.Replace('$started=$true', '$started=$true; $script:heartbeatJob025=Start-Heartbeat025 $run')
function Start-Heartbeat025([string]$RunId025){
    Start-Job -ArgumentList $runtimeHeartbeat025,$RunId025 -ScriptBlock {
        param($Runtime025,$Run025)
        $ErrorActionPreference='Stop'
        . (Join-Path $Runtime025 'LocalPlay-Pilot.ps1')
        $context025=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $Run025
        $watch025=[Diagnostics.Stopwatch]::StartNew()
        while($watch025.Elapsed.TotalSeconds -lt 525){
            try{
                Write-PilotHeartbeat $context025 $Run025 $Run025
                "HEARTBEAT_UTC=$([DateTime]::UtcNow.ToString('O')) ELAPSED=$($watch025.Elapsed.TotalSeconds)"
            }catch{
                "HEARTBEAT_FAILURE_UTC=$([DateTime]::UtcNow.ToString('O')) REASON=$($_.Exception.Message)"
                throw
            }
            Start-Sleep -Seconds 10
        }
    }
}
$script:heartbeatJob025=$null
$script:counter=0
$script:observer=$null
try{
    if($Mode -cne 'Probe'){
        & ([scriptblock]::Create($body025)) -RuntimeRoot $runtimeHeartbeat025 -OutputRoot $OutputRoot -FrozenSha $FrozenSha -Seconds $Seconds -PreviousPrimaryIds $PreviousPrimaryIds -SetupTeleport $SetupTeleport -SetupMove $SetupMove -CollectSealed:$CollectSealed
        $samplesPath025=Join-Path $OutputRoot 'all-samples.json'
        if(Test-Path $samplesPath025){
            $sampling025=@(Get-Content $samplesPath025 -Raw | ConvertFrom-Json)
            [double]$maximumGap025=0
            for($i025=1;$i025 -lt $sampling025.Count;$i025++){$maximumGap025=[Math]::Max($maximumGap025,[double]$sampling025[$i025].elapsedSeconds-[double]$sampling025[$i025-1].elapsedSeconds)}
            [ordered]@{source='READ_ONLY_COHORT_SAMPLE_TIMES';limitSeconds=5;maximumGapSeconds=$maximumGap025;sampleCount=$sampling025.Count;pass=($maximumGap025 -le 5);nativeTtlSeconds=525;observerSha256=(Get-FileHash $PSCommandPath).Hash;pilotInputSha256=(Get-FileHash (Join-Path $runtimeHeartbeat025 'Invoke-LocalPlayPilot.ps1')).Hash} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'sampling-result.json') -Encoding utf8
        }
    }else{
        & ([scriptblock]::Create($body025)) -RuntimeRoot $runtimeHeartbeat025 -OutputRoot $OutputRoot -Seconds $Seconds
    }
}finally{
    if($script:heartbeatJob025){
        Stop-Job $script:heartbeatJob025
        Receive-Job $script:heartbeatJob025 *> (Join-Path $OutputRoot 'heartbeat-control.log')
        Remove-Job $script:heartbeatJob025
    }
}
