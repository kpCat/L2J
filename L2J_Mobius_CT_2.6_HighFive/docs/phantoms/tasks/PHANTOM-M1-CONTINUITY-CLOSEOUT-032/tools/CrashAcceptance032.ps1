[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('b','c')][string]$Episode,
      [Parameter(Mandatory)][string]$ProbeRoot,
      [Parameter(Mandatory)][string]$OutputRoot,
      [int]$RequiredNpcId=18342)
$ErrorActionPreference='Stop'
$task=Split-Path $PSScriptRoot -Parent
. (Join-Path $task 'Read-SharedJson032.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $task '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract032$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path $task 'evidence'))+[IO.Path]::DirectorySeparatorChar
foreach($path in @($ProbeRoot,$OutputRoot)){
    if(-not [IO.Path]::GetFullPath($path).StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact own TASK032 evidence required'}
}
if(Test-Path $OutputRoot){throw 'Immutable actual fault evidence exists'}
. (Join-Path $runtime 'LocalPlay-Ownership.ps1')
$role=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @(7777)
if($role.state -cne 'RUNNING' -or -not $role.recordVerified){throw 'Exact own GameServer incarnation required'}
$held=Get-Process -Id ([int]$role.pid)
if($held.StartTime.ToUniversalTime().Ticks -ne [long]$role.startTimeUtcTicks){throw 'PID incarnation changed'}
$null=$held.Handle # Hold the original process handle before the planned exit.
$deadline=[DateTime]::UtcNow.AddSeconds(180)
$baseline=Join-Path $ProbeRoot 'baseline-cohort.json'
while(-not (Test-Path $baseline)){
    if($held.HasExited -or [DateTime]::UtcNow -ge $deadline){throw 'Pre-outcome baseline unavailable; no fault submitted'}
    Start-Sleep -Milliseconds 100
}
$rows=@(Read-SharedJson032 $baseline)
if($rows.Count -lt 4 -or $rows.Count -gt 8){throw 'Whole natural diagnostic cohort4..8 required'}
# Select only by initial live native farm target, never by EXP/SP, cycles or debt.
$chosen=@($rows | Where-Object {[int]$_.npcId -eq $RequiredNpcId -and $_.dead -ceq 'false' -and $_.worldPresent -ceq 'true'} | Sort-Object {[long]$_.profileId} | Select-Object -First 1)
if($chosen.Count -ne 1 -or [long]$chosen[0].materializedAtNanos -le 0){throw 'Declared initial native farm actor unavailable; no replacement'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
$cohort=Join-Path $OutputRoot 'fault-cohort.json'
$chosen | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $cohort -Encoding utf8
$role | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputRoot 'held-game-incarnation.json') -Encoding utf8
[ordered]@{criterion='FIRST_INITIAL_LIVE_REQUIRED_NPC_NO_COUNTER_FILTER';requiredNpcId=$RequiredNpcId;profileId=$chosen[0].profileId;epoch=$chosen[0].materializedAtNanos;wholeBaselineCount=$rows.Count;baselineSha256=(Get-FileHash $baseline).Hash;selectedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'pre-outcome-selection.json') -Encoding utf8
$mode=if($Episode -ceq 'b'){'CrashNative'}else{'CrashFinalize'}
& (Join-Path $task 'Control032.ps1') -Action Collector -Episode $Episode -Mode $mode -CohortJson $cohort -OutputRoot $OutputRoot *> (Join-Path $OutputRoot 'fault-control.log')
if(-not $?){throw 'Exact typed fault submission failed'}
while(-not $held.WaitForExit(1000)){
    if([DateTime]::UtcNow -ge $deadline){throw 'No actual planned halt within bounded probe; no forced stop'}
}
$actual=$held.ExitCode
$planned=Get-Content -LiteralPath (Join-Path $OutputRoot 'planned-crash.properties') | ConvertFrom-StringData
$expected=if($Episode -ceq 'b'){72}else{73}
$valid=$actual -eq $expected -and [int]$planned.exit -eq $expected -and [int]$planned.pid -eq [int]$role.pid -and [long]$planned.profileId -eq [long]$chosen[0].profileId -and [long]$planned.epoch -eq [long]$chosen[0].materializedAtNanos
[ordered]@{owner='TASK032_CONTRACT';pid=$role.pid;startTicks=$role.startTimeUtcTicks;actualExitCode=$actual;expectedExitCode=$expected;exactWindowAndProcess=$valid;observedUtc=[DateTime]::UtcNow.ToString('o');plannedSha256=(Get-FileHash (Join-Path $OutputRoot 'planned-crash.properties')).Hash} | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'actual-exit.json') -Encoding utf8
if(-not $valid){throw 'Actual original process exit/window differs'}
"TASK032_ACTUAL_HALT_PASS exit=$actual profile=$($chosen[0].profileId) epoch=$($chosen[0].materializedAtNanos)"
