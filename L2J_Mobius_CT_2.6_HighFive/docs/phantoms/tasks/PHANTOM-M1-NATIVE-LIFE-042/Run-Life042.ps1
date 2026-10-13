param([ValidateSet('a','b')][string]$Episode='b',[string]$Label='LIFE_B1_042',
      [Parameter(Mandatory)][string]$FrozenSha,[Parameter(Mandatory)][string]$PathJson)
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops042=Join-Path $module042 '.phantom-local/ops042'
$output042=Join-Path $PSScriptRoot "evidence/$Label"
if(Test-Path $output042){throw 'Immutable LIFE output exists.'}
try {
    & (Join-Path $ops042 'Control032.ps1') -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $ops042 "$Label-START.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE exact start failed.'}
    Start-Sleep -Seconds 90
    & (Join-Path $ops042 'Observe032.ps1') -Mode Away -Episode $Episode -SingleLifePrimary042 -Seconds 110 -BackgroundSeconds 75 -FrozenSha $FrozenSha -PathJson $PathJson -OutputRoot $output042 -StopOwnServerOnComplete *> (Join-Path $ops042 "$Label-CAPTURE.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE capture failed; all enrolled actors retained.'}
} finally {
    $runtime042=Join-Path $module042 ".phantom-local/contract042$Episode/runtime"
    . (Join-Path $runtime042 'LocalPlay-Pilot.ps1')
    . (Join-Path $runtime042 'LocalPlay-Ownership.ps1')
    $game042=Get-LocalPlayRoleState $runtime042 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime042 'game/config/Server.ini') 'GameserverPort'))
    if($game042.state -ceq 'RUNNING' -and $game042.recordVerified){
        & (Join-Path $ops042 'Control032.ps1') -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $PSScriptRoot "evidence/$Label-FALLBACK-STOP") *> (Join-Path $ops042 "$Label-FINAL-STOP.log")
    } elseif($game042.state -cnotin @('STOPPED','STALE_RECORD')){throw 'LIFE final own process state unverified.'}
}
