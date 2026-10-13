param([ValidateRange(0,10000)][long]$SetupProfileId=0,[Parameter(Mandatory)][string]$OutputRoot,[Parameter(Mandatory)][string]$CohortJson,[ValidateSet('a','b')][string]$Episode='a',[switch]$KeepSession042)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime=Join-Path $module ".phantom-local/contract042$Episode/runtime"
$output=[IO.Path]::GetFullPath($OutputRoot)
$allowed=[IO.Path]::GetFullPath((Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/evidence'))+'\'
if (!$output.StartsWith($allowed) -or (Test-Path $output)) {throw 'New042 diagnostic output required.'}
New-Item -ItemType Directory -Path $output | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D')
$start042=@{RunId=$run}
if($SetupProfileId -gt 0){$start042.SetupProfileId=$SetupProfileId}
& (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') @start042 | Set-Content (Join-Path $output 'session-start.json') -Encoding UTF8
$context=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $run
$keep042=$false
try {
    for ($i=0;$i -lt 18;$i++) {Write-PilotHeartbeat $context $run $run; Start-Sleep -Seconds 5}
    & (Join-Path $PSScriptRoot 'Control032.ps1') -Action Collector -Mode Census -Episode $Episode -CohortJson $CohortJson -OutputRoot (Join-Path $output 'census') *> (Join-Path $output 'census.log')
    if ($LASTEXITCODE -ne 0) {throw 'Read-only native census failed.'}
    $keep042=$KeepSession042.IsPresent
} finally {
    if(-not $keep042){& (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $output 'session-stop.json') -Encoding UTF8}
}
