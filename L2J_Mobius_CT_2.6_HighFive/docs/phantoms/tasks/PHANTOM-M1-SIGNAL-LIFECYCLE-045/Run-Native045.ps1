param([Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops045'
$runtime=Join-Path $module '.phantom-local/contract045a/runtime'
$output=Join-Path $PSScriptRoot 'evidence/NATIVE045'
if(Test-Path $output){throw 'One immutable native045 attempt; no retry.'}
if([DateTime]::UtcNow -ge [DateTime]::Parse('2026-10-14T15:31:35Z').ToUniversalTime()){throw 'Insufficient native due/stop/publication budget.'}
try {
    & (Join-Path $ops 'Control032.ps1') -Action Collector -Mode Build *> (Join-Path $ops 'COLLECTOR_BUILD045.log')
    if($LASTEXITCODE -ne 0){throw 'Read-only collector build failed.'}
    & (Join-Path $ops 'Control032.ps1') -Action DryPath -Episode a -OriginPoint @{x=44131;y=42673;z=-3488} -EndpointPoint @{x=38131;y=42673} -OutputRoot (Join-Path $PSScriptRoot 'evidence/NATIVE_DRY045') *> (Join-Path $ops 'NATIVE_DRY045.log')
    if($LASTEXITCODE -ne 0){throw 'Actual native geometry precondition failed.'}
    & (Join-Path $ops 'Control032.ps1') -Action Start -Episode a -ExpectedSha $FrozenSha *> (Join-Path $ops 'NATIVE_START045.log')
    if($LASTEXITCODE -ne 0){throw 'Exact ordinary045a start failed.'}
    Start-Sleep -Seconds 40
    Start-Sleep -Seconds 40
    & (Join-Path $ops 'Observe032.ps1') -Mode Away -Episode a -SingleLifePrimary045 -LifeCurrentReady045 -PreconditionsFile045 (Join-Path $PSScriptRoot 'PRECONDITIONS045.md') -Seconds 110 -FrozenSha $FrozenSha -PathJson (Join-Path $PSScriptRoot 'evidence/NATIVE_DRY045/path.json') -OutputRoot $output -StopOwnServerOnComplete *> (Join-Path $ops 'NATIVE_CAPTURE045.log')
    if($LASTEXITCODE -ne 0){throw 'Native045 captured failure; original identity/raw preserved.'}
} finally {
    . (Join-Path $runtime 'LocalPlay-Pilot.ps1')
    . (Join-Path $runtime 'LocalPlay-Ownership.ps1')
    $game=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
    if($game.state -ceq 'RUNNING' -and $game.recordVerified){
        & (Join-Path $ops 'Control032.ps1') -Action Stop -Episode a -DumpDuringStop -OutputRoot (Join-Path $PSScriptRoot 'evidence/NATIVE045_FALLBACK_STOP') *> (Join-Path $ops 'NATIVE_FINAL_STOP045.log')
        if($LASTEXITCODE -ne 0){throw 'Stock stop unconfirmed; no force fallback.'}
    }elseif($game.state -cnotin @('STOPPED','STALE_RECORD')){throw 'Own native045 process identity unverified.'}
}
