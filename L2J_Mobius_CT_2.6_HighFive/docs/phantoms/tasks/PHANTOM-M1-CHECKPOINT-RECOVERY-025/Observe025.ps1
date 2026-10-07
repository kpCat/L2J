[CmdletBinding()]
param([ValidateSet('Probe','Scene')][string]$Mode='Probe',
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
$file025=if($Mode -ceq 'Scene'){'Observe-Scene024.ps1'}else{'Observe-Prelude024.ps1'}
$body025=[IO.File]::ReadAllText((Join-Path $taskRoot024 $file025))
$body025=$body025.Replace("& (Join-Path `$PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot `$runtime", "& (Join-Path `$taskRoot025 'Control025.ps1') -Action Export -Episode `$Episode")
$body025=$body025.Replace('-ProfileIds @($ids)','-ProfileIds $ProbeProfileIds')
$body025=$body025.Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract025').Replace('contract025c','contract025[a-h]')
$body025=$body025.Replace('$started=$true', '$started=$true; $script:heartbeatJob025=Start-Heartbeat025 $run')
function Start-Heartbeat025([string]$RunId025){
    Start-Job -ArgumentList $runtimeHeartbeat025,$RunId025 -ScriptBlock {
        param($Runtime025,$Run025)
        $ErrorActionPreference='Stop'
        . (Join-Path $Runtime025 'LocalPlay-Pilot.ps1')
        $context025=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $Run025
        $watch025=[Diagnostics.Stopwatch]::StartNew()
        while($watch025.Elapsed.TotalSeconds -lt 525){
            Write-PilotHeartbeat $context025 $Run025 $Run025
            Start-Sleep -Seconds 10
        }
    }
}
$script:heartbeatJob025=$null
try{
    if($Mode -ceq 'Scene'){
        & ([scriptblock]::Create($body025)) -RuntimeRoot $runtimeHeartbeat025 -OutputRoot $OutputRoot -FrozenSha $FrozenSha -Seconds $Seconds -PreviousPrimaryIds $PreviousPrimaryIds -SetupTeleport $SetupTeleport -SetupMove $SetupMove -CollectSealed:$CollectSealed
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
