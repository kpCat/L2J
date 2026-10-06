param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$private = Join-Path $module '.phantom-local/observe021'
$runtime = Join-Path $private 'runtime'
$option = Join-Path $private 'secrets/client.cnf'
$client = 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
$database = 'l2jmobiush5_localplay_observe021'
$query = "SELECT charId,char_name,online,level,exp,sp,x,y,z FROM characters WHERE charId=268492939 AND char_name='TestAdmin'; SELECT COUNT(*) FROM characters WHERE online=1;"
function Read-Saved {
    $rows = @(& $client "--defaults-extra-file=$option" --batch --skip-column-names "--database=$database" -e $query)
    if ($LASTEXITCODE -ne 0 -or $rows.Count -ne 2 -or $rows[1] -cne '0' -or $rows[0].Split("`t")[2] -cne '0') { throw 'Logout/store or total online0 unconfirmed; leave server running.' }
    return $rows
}
$before = Read-Saved
$before | Set-Content (Join-Path $PSScriptRoot 'LOGOUT_BEFORE_STOP.tsv') -Encoding utf8
Write-Output "LOGOUT_SAVE_CONFIRMED $($before[0]) totalOnline=$($before[1])"
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$pilotContext=Get-PilotContext -RequireEnabled -ActorMode RealClient
$pilotSession=Get-PilotSession $pilotContext
if ($pilotSession.state -ceq 'RUNNING') {
    & (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode RealClient
    $pilotSession=Get-PilotSession $pilotContext
}
if ($pilotSession.state -ceq 'RUNNING') { throw 'Pilot run still active; leave server running.' }
Write-Output "NO_ACTIVE_PILOT_RUN state=$($pilotSession.state)"
& (Join-Path $runtime 'Stop-LocalPlay.ps1') *> (Join-Path $PSScriptRoot 'GRACEFUL_STOP.log')
if (-not $?) { Get-Content (Join-Path $PSScriptRoot 'GRACEFUL_STOP.log') -Tail 15; throw 'Graceful stop incomplete; no force fallback.' }
Get-Content (Join-Path $PSScriptRoot 'GRACEFUL_STOP.log') -Tail 10
$after = Read-Saved
$after | Set-Content (Join-Path $PSScriptRoot 'LOGOUT_AFTER_STOP.tsv') -Encoding utf8
if (($before -join "`n") -cne ($after -join "`n")) { throw 'Saved character fields changed during stop.' }
Write-Output "SAVED_FIELDS_EXACT_MATCH $($after[0]) totalOnline=$($after[1])"
& (Join-Path $runtime 'Check-LocalPlay.ps1')
. (Join-Path $runtime 'LocalPlay-Ownership.ps1')
if ((Get-LocalPlayPortOwners @(2106,9014,7777)).Count -ne 0) { throw 'Server ports still owned after stop.' }
foreach ($role in @('LoginServer','GameServer')) {
    $record = Get-Content (Join-Path $runtime "local-play/pids/$role.json") -Raw | ConvertFrom-Json
    if (Get-Process -Id $record.pid -ErrorAction SilentlyContinue) { throw 'Recorded server process remains.' }
}
$original = 'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'
$preserved = @(Import-Csv -LiteralPath (Join-Path $private 'original-preservation.tsv') -Delimiter "`t")
foreach ($entry in $preserved) {
    if ((Get-FileHash -LiteralPath (Join-Path $original $entry.path)).Hash -cne $entry.sha256) { throw 'Original runtime preservation failed.' }
}
Write-Output "POST_STOP_PASS processes=0 ports=0 originalHashes=$($preserved.Count) force=false"
