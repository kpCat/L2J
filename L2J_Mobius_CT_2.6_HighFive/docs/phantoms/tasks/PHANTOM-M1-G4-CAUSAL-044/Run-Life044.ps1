param([ValidateSet('a','b')][string]$Episode='a',[string]$Label='LIFE_A1_044',
      [Parameter(Mandatory)][string]$FrozenSha,[Parameter(Mandatory)][string]$PathJson,[long]$FixedLifeProfileId=0,[ValidateRange(1,2)][int]$Attempt=1,[int]$LifeNpcId=0,[ValidateSet(0,90)][int]$InitialPreparationSeconds=90,[string]$PreconditionsFile='',[switch]$LifeCurrentReady,[switch]$LifeProductive)
$ErrorActionPreference='Stop'
$module044=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops044=Join-Path $module044 '.phantom-local/ops044'
$output044=Join-Path $PSScriptRoot "evidence/$Label"
if(Test-Path $output044){throw 'Immutable LIFE output exists.'}
if([DateTime]::UtcNow -ge [DateTime]::Parse('2026-10-14T03:59:13Z').ToUniversalTime()){throw 'Publication reserve; no new episode.'}
$frozen044=Join-Path $PSScriptRoot "evidence/$Label-PROSPECTIVE.json"
if(Test-Path $frozen044){throw 'Frozen preconditions already exist.'}
$precondition044=if($PreconditionsFile){[IO.Path]::GetFullPath($PreconditionsFile)}else{Join-Path $PSScriptRoot 'PRECONDITIONS044.md'}
@{contract='LIFE044_PROSPECTIVE_NATIVE_CASTER';episode=$Episode;label=$Label;sourceSha=$FrozenSha;preconditionsSha256=(Get-FileHash $precondition044).Hash;pathSha256=(Get-FileHash $PathJson).Hash;utc=[DateTime]::UtcNow.ToString('O');attempt=$Attempt;fixedProfileId=$FixedLifeProfileId;lifeNpcId=$LifeNpcId;initialPreparationSeconds=$InitialPreparationSeconds;positiveProfileReplacementAllowed=$false;normalOnly=$true} | ConvertTo-Json | Set-Content $frozen044 -Encoding utf8
try {
    & (Join-Path $ops044 'Control032.ps1') -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $ops044 "$Label-START.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE exact start failed.'}
    if($InitialPreparationSeconds -eq 90){Start-Sleep -Seconds 45; Start-Sleep -Seconds 45}
    & (Join-Path $ops044 'Observe032.ps1') -Mode Away -Episode $Episode -SingleLifePrimary044 -FixedLifeProfileId044 $FixedLifeProfileId -LifeNpcId044 $LifeNpcId -LifeCurrentReady044:$LifeCurrentReady -LifeProductive044:$LifeProductive -PreconditionsFile044 $precondition044 -Seconds 110 -BackgroundSeconds 75 -FrozenSha $FrozenSha -PathJson $PathJson -OutputRoot $output044 -StopOwnServerOnComplete *> (Join-Path $ops044 "$Label-CAPTURE.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE capture failed; all enrolled actors retained.'}
} finally {
    $runtime044=Join-Path $module044 ".phantom-local/contract044$Episode/runtime"
    . (Join-Path $runtime044 'LocalPlay-Pilot.ps1')
    . (Join-Path $runtime044 'LocalPlay-Ownership.ps1')
    $game044=Get-LocalPlayRoleState $runtime044 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime044 'game/config/Server.ini') 'GameserverPort'))
    if($game044.state -ceq 'RUNNING' -and $game044.recordVerified){
        & (Join-Path $ops044 'Control032.ps1') -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $PSScriptRoot "evidence/$Label-FALLBACK-STOP") *> (Join-Path $ops044 "$Label-FINAL-STOP.log")
    } elseif($game044.state -cnotin @('STOPPED','STALE_RECORD')){throw 'LIFE final own process state unverified.'}
}
