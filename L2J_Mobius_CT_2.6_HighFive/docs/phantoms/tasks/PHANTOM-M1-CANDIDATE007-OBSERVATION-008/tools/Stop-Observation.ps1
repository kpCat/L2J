[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[switch]$UserAuthorizedForce)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if (-not $UserAuthorizedForce) { throw 'Explicit user force authorization required for responsive experimental JVMs.' }
$expectedRoot='C:\Users\ZBook\.codex\worktrees\m1-observe-008\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\observe008\runtime'
if ([IO.Path]::GetFullPath($RuntimeRoot) -cne $expectedRoot) { throw 'Unexpected runtime root.' }
. (Join-Path $RuntimeRoot 'LocalPlay-Ownership.ps1')
foreach ($role in @('game','login')) {
    $text=[IO.File]::ReadAllText((Join-Path $RuntimeRoot "$role/config/Database.ini"))
    if ($text -notmatch '(?m)^URL\s*=\s*jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay_observe008\?') { throw 'Clone URL mismatch.' }
}
$manifest=Get-Content (Join-Path $RuntimeRoot 'local-play.json') -Raw | ConvertFrom-Json
if ($manifest.codeSha -cne 'dd58a512c4cb9c6a5318d7320633a35ae849dbf0') { throw 'Code SHA mismatch.' }
$rows=[Collections.Generic.List[object]]::new()
foreach ($spec in @(@{role='GameServer';pid=328;ticks=639267496997370983L;ports=@(7777)},@{role='LoginServer';pid=3064;ticks=639267496980561347L;ports=@(2106,9014)})) {
    $state=Get-LocalPlayRoleState $RuntimeRoot $spec.role ($spec.role+'.jar') $spec.ports
    if ($state.state -cne 'RUNNING' -or -not $state.recordVerified -or $state.pid -ne $spec.pid -or $state.startTimeUtcTicks -ne $spec.ticks) { throw 'Exact owned process mismatch.' }
    $process=Get-Process -Id $spec.pid -ErrorAction Stop
    if ($process.StartTime.ToUniversalTime().Ticks -ne $spec.ticks) { throw 'PID changed.' }
    Stop-Process -Id $spec.pid -Force -ErrorAction Stop
    $null=$process.WaitForExit(10000)
    if (Get-Process -Id $spec.pid -ErrorAction SilentlyContinue) { throw 'Process remains alive.' }
    if ((Get-LocalPlayPortOwners $spec.ports).Count -ne 0) { throw 'Ports remain owned.' }
    $rows.Add([pscustomobject]@{role=$spec.role;pid=$spec.pid;startTimeUtcTicks=$spec.ticks;stop='USER_AUTHORIZED_FORCE';gracefulShutdown='FAIL';processStopped=$true})
}
$rows | Export-Csv (Join-Path (Split-Path $RuntimeRoot -Parent) 'cleanup.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$rows | Format-Table
