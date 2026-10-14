param([ValidateSet('a','b')][string]$Episode='a',[string]$Label='LIFE_A1_043',
      [Parameter(Mandatory)][string]$FrozenSha,[Parameter(Mandatory)][string]$PathJson,[long]$FixedLifeProfileId=0,[ValidateRange(1,2)][int]$Attempt=1,[int]$LifeNpcId=0,[ValidateSet(0,90)][int]$InitialPreparationSeconds=90,[string]$PreconditionsFile='',[switch]$LifeCurrentReady,[switch]$LifeProductive)
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops043=Join-Path $module043 '.phantom-local/ops043'
$output043=Join-Path $PSScriptRoot "evidence/$Label"
if(Test-Path $output043){throw 'Immutable LIFE output exists.'}
if([DateTime]::UtcNow -ge [DateTime]::Parse('2026-10-14T02:35:55Z').ToUniversalTime()){throw 'Publication reserve; no new episode.'}
$frozen043=Join-Path $PSScriptRoot "evidence/$Label-PROSPECTIVE.json"
if(Test-Path $frozen043){throw 'Frozen preconditions already exist.'}
$precondition043=if($PreconditionsFile){[IO.Path]::GetFullPath($PreconditionsFile)}else{Join-Path $PSScriptRoot 'PRECONDITIONS043.md'}
@{contract='LIFE043_PROSPECTIVE_NATIVE_CASTER';episode=$Episode;label=$Label;sourceSha=$FrozenSha;preconditionsSha256=(Get-FileHash $precondition043).Hash;pathSha256=(Get-FileHash $PathJson).Hash;utc=[DateTime]::UtcNow.ToString('O');attempt=$Attempt;fixedProfileId=$FixedLifeProfileId;lifeNpcId=$LifeNpcId;initialPreparationSeconds=$InitialPreparationSeconds;positiveProfileReplacementAllowed=$false;normalOnly=$true} | ConvertTo-Json | Set-Content $frozen043 -Encoding utf8
try {
    & (Join-Path $ops043 'Control032.ps1') -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $ops043 "$Label-START.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE exact start failed.'}
    if($InitialPreparationSeconds -eq 90){Start-Sleep -Seconds 45; Start-Sleep -Seconds 45}
    & (Join-Path $ops043 'Observe032.ps1') -Mode Away -Episode $Episode -SingleLifePrimary043 -FixedLifeProfileId043 $FixedLifeProfileId -LifeNpcId043 $LifeNpcId -LifeCurrentReady043:$LifeCurrentReady -LifeProductive043:$LifeProductive -PreconditionsFile043 $precondition043 -Seconds 110 -BackgroundSeconds 75 -FrozenSha $FrozenSha -PathJson $PathJson -OutputRoot $output043 -StopOwnServerOnComplete *> (Join-Path $ops043 "$Label-CAPTURE.log")
    if($LASTEXITCODE -ne 0){throw 'LIFE capture failed; all enrolled actors retained.'}
} finally {
    $runtime043=Join-Path $module043 ".phantom-local/contract043$Episode/runtime"
    . (Join-Path $runtime043 'LocalPlay-Pilot.ps1')
    . (Join-Path $runtime043 'LocalPlay-Ownership.ps1')
    $game043=Get-LocalPlayRoleState $runtime043 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime043 'game/config/Server.ini') 'GameserverPort'))
    if($game043.state -ceq 'RUNNING' -and $game043.recordVerified){
        & (Join-Path $ops043 'Control032.ps1') -Action Stop -Episode $Episode -DumpDuringStop -OutputRoot (Join-Path $PSScriptRoot "evidence/$Label-FALLBACK-STOP") *> (Join-Path $ops043 "$Label-FINAL-STOP.log")
    } elseif($game043.state -cnotin @('STOPPED','STALE_RECORD')){throw 'LIFE final own process state unverified.'}
}
