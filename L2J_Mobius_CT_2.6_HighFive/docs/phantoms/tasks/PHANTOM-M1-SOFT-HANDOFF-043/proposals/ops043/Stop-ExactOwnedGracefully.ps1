param([Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference = 'Stop'
$taskModule = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $taskModule 'tools/phantom-local-play/LocalPlay-Pilot.ps1')
$runtime013 = [IO.Path]::GetFullPath($RuntimeRoot)
$dbConfig = Join-Path $runtime013 'game/config/Database.ini'
$url013 = Get-PilotIniValue $dbConfig 'URL'
if ($url013 -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_phantom_test_043[ab])\?') { throw 'Unexpected exact observation database.' }
$database013 = $Matches[2]
$savedPassword = $env:MYSQL_PWD
try
{
    $env:MYSQL_PWD = Get-PilotIniValue $dbConfig 'Password'
    $login013 = Get-PilotIniValue $dbConfig 'Login'
    $savedPreference013=$ErrorActionPreference; $ErrorActionPreference='Continue'
    try {
        $rows013 = @(& 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' --no-defaults --host=127.0.0.1 --port=3308 "--user=$login013" --batch --skip-column-names "--database=$database013" -e "SELECT charId,char_name,online,level,exp,sp,x,y,z FROM characters WHERE charId=268492939 AND char_name='TestAdmin'; SELECT COUNT(*) FROM characters WHERE online=1;")
        $queryExit013=$LASTEXITCODE
    } finally {$ErrorActionPreference=$savedPreference013}
    if ($queryExit013 -ne 0 -or $rows013.Count -ne 2 -or $rows013[1] -cne '0' -or $rows013[0].Split("`t")[2] -cne '0') { throw 'Exact logout/store or absence of REAL online characters unconfirmed; server left running.' }
    $rows013 | Set-Content (Join-Path $PSScriptRoot "$database013-logout.tsv") -Encoding utf8
    Write-Output "DB_LOGOUT_CONFIRMED $database013 $($rows013[0])"
}
finally { $env:MYSQL_PWD = $savedPassword }

$runtimeId013 = Get-LocalPlayRuntimeId $runtime013
foreach ($role013 in @('GameServer','LoginServer'))
{
    $ports013 = if ($role013 -ceq 'GameServer') { @([int](Get-PilotIniValue (Join-Path $runtime013 'game/config/Server.ini') 'GameserverPort')) } else { @([int](Get-PilotIniValue (Join-Path $runtime013 'login/config/Server.ini') 'LoginPort'), [int](Get-PilotIniValue (Join-Path $runtime013 'login/config/Server.ini') 'LoginserverPort')) }
    $state013 = Get-LocalPlayRoleState $runtime013 $role013 "$role013.jar" $ports013
    if ($state013.state -ceq 'STOPPED' -or $state013.state -ceq 'STALE_RECORD') { Write-Output "ALREADY_STOPPED role=$role013"; continue }
    if (-not $state013.recordVerified -or ($state013.state -cne 'RUNNING' -and ($state013.state -cne 'INCONSISTENT' -or $state013.reason -cne 'marked JVM does not own all expected ports'))) { throw 'Exact owned running/partial-start identity missing; no shutdown requested.' }
    $process013 = Get-Process -Id $state013.pid -ErrorAction Stop
    if ($process013.StartTime.ToUniversalTime().Ticks -ne [long]$state013.startTimeUtcTicks) { throw 'PID incarnation changed; no shutdown requested.' }
    $agent013 = 'stock-shutdown043.jar'
    $savedPreference013=$ErrorActionPreference; $ErrorActionPreference='Continue'
    try { & java --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'stock-shutdown-classes043') RequestShutdown ([string]$state013.pid) $runtimeId013 $role013 $runtime013 $database013 (Join-Path $PSScriptRoot $agent013); $requestExit013=$LASTEXITCODE }
    finally {$ErrorActionPreference=$savedPreference013}
    $null = $process013.WaitForExit(45000)
    if (-not $process013.HasExited) { throw 'Stock graceful shutdown incomplete; server left running, no force fallback.' }
    $owners013 = Get-LocalPlayPortOwners $ports013
    if ($owners013.Count -ne 0) { throw 'Listeners remain after graceful stop.' }
    Write-Output "STOCK_GRACEFUL_STOP_CONFIRMED role=$role013 pid=$($state013.pid)"
}

