[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Start','Stop','Status')][string]$Action,
    [string]$RuntimeRoot = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (!$RuntimeRoot) {$RuntimeRoot=Join-Path $PSScriptRoot '..'}
$runtime = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
. (Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1')

function Read-Ini([string]$Path, [string]$Key) {
    $matches = [regex]::Matches([IO.File]::ReadAllText($Path), '(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if ($matches.Count -ne 1) { throw "Ожидался один ключ '$Key': $Path" }
    return $matches[0].Groups[1].Value.Trim()
}
function Assert-NoAlias([string]$Path) {
    $current = [IO.Path]::GetPathRoot($Path)
    foreach ($part in $Path.Substring($current.Length).Split('\')) {
        if (!$part) { continue }; $current = Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Alias запрещён: $current" }
    }
}
Assert-NoAlias $runtime
$settings = Join-Path $runtime 'runtime.local.ini'
$manifest = Get-Content -LiteralPath (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
$build = Get-Content -LiteralPath (Join-Path $runtime 'BUILD_INFO.json') -Raw | ConvertFrom-Json
$database = Read-Ini $settings 'DatabaseName'
if ($database -notmatch '^l2jmobiush5_phantom_(test_041final|test_058a|release_[A-Za-z0-9_]{1,24})$' -or $manifest.databaseName -cne $database -or $manifest.databaseConfig -cne 'USER_CONFIRMED_EXISTING') { throw 'Схема runtime не подтверждена; действие запрещено.' }
if ($database -in @('l2jmobiush5_phantom_test_058a','l2jmobiush5_phantom_release_058a')) {
    . (Join-Path $PSScriptRoot 'Campaign058Ownership.ps1')
    $campaignOwner=Read-Campaign058Owner (Join-Path $runtime 'ownership058.properties')
    Assert-Campaign058Binding $runtime $database $campaignOwner
}
foreach ($role in @('game','login')) {
    $config = Join-Path $runtime "$role/config/Database.ini"
    Assert-NoAlias $config
    $url = Read-Ini $config 'URL'
    if ($url -notmatch ('^jdbc:(mysql|mariadb)://(127\.0\.0\.1|localhost):[0-9]+/'+[regex]::Escape($database)+'(\?.*)?$')) { throw "Неверная JDBC схема: $role" }
}
$ports = @{
    GameServer = @([int](Read-Ini (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
    LoginServer = @([int](Read-Ini (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort'), [int](Read-Ini (Join-Path $runtime 'login/config/Server.ini') 'LoginPort'))
}
$allPorts = @($ports.GameServer + $ports.LoginServer)
if (@($allPorts | Select-Object -Unique).Count -ne 3 -or @($allPorts | Where-Object {$_ -lt 1024 -or $_ -gt 65535}).Count) { throw 'Нужны три различных допустимых порта.' }
if ([int](Read-Ini (Join-Path $runtime 'game/config/Server.ini') 'LoginPort') -ne $ports.LoginServer[1]) { throw 'Login/Game control ports не совпадают.' }
if ($database -in @('l2jmobiush5_phantom_test_058a','l2jmobiush5_phantom_release_058a') -and ($ports.GameServer[0] -ne 7838 -or $ports.LoginServer[0] -ne 2138 -or $ports.LoginServer[1] -ne 9138)) {throw 'TASK058: actual runtime ports отличаются от binding.'}
$runtimeId = Get-LocalPlayRuntimeId $runtime
function Role-State([string]$Role) { return Get-LocalPlayRoleState $runtime $Role "$Role.jar" $ports[$Role] }
function Assert-Jars {
    foreach ($role in @('GameServer','LoginServer')) {
        $path = Join-Path $runtime "libs/$role.jar"; Assert-NoAlias $path
        $expected = if ($role -ceq 'GameServer') {$build.gameJarSha256} else {$build.loginJarSha256}
        $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        if ($actual -ine $expected) { throw "JAR не соответствует BUILD_INFO: $role" }
        $manifestHash = if ($role -ceq 'GameServer') {$manifest.gameJarSha256} else {$manifest.loginJarSha256}
        if ($actual -ine $manifestHash) { throw "JAR не соответствует manifest: $role" }
    }
}
function Read-Observer {
    $path = Join-Path $runtime 'playtest-pilot/observer.properties'
    if (!(Test-Path -LiteralPath $path)) { return [ordered]@{state='ABSENT';fresh=$false} }
    $fields = @{}; foreach ($line in [IO.File]::ReadAllLines($path)) { if ($line -match '^([^#=]+)=(.*)$') {$fields[$Matches[1]]=$Matches[2]} }
    $game = Role-State 'GameServer'
    return [ordered]@{mode=$fields.mode;focus=$fields.objectId;fresh=(($game.state -ceq 'RUNNING') -and ([string]$game.pid -ceq $fields.pid) -and ([string]$game.startTimeUtcTicks -ceq $fields.startTimeUtcTicks) -and (([DateTime]::UtcNow-(Get-Item -LiteralPath $path).LastWriteTimeUtc).TotalSeconds -lt 5));fields=$fields}
}
function Write-Status {
    Assert-Jars
    $acceptance=[ordered]@{m1='UNKNOWN';readyForM2=$false;reason='ACCEPTANCE_RESULT_ABSENT'}
    $resultPath=Join-Path $runtime 'ACCEPTANCE_STATUS.json'
    if (Test-Path -LiteralPath $resultPath) {
        $result=Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if ($result.format -eq 1 -and $result.codeCommit -ceq $build.codeCommit -and $result.gameJarSha256 -ieq $build.gameJarSha256 -and $result.updatedUtc) {$acceptance=$result}
    }
    [ordered]@{runtime=$runtime;runtimeId=$runtimeId;database=$database;source=$build.codeCommit;game=(Role-State 'GameServer');login=(Role-State 'LoginServer');ports=$ports;observer=(Read-Observer);acceptance=$acceptance} | ConvertTo-Json -Depth 7
}
if ($Action -ceq 'Status') { Write-Status; exit 0 }
$javaHome = Read-Ini $settings 'JavaHome'
$java = Join-Path $javaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath (Join-Path $javaHome 'bin/jcmd.exe'))) { throw 'Требуется установленный JDK25 с attach tooling.' }
if (!(Test-Path -LiteralPath (Join-Path $javaHome 'release')) -or [IO.File]::ReadAllText((Join-Path $javaHome 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")') {throw 'Требуется JDK25.'}
New-Item -ItemType Directory -Path (Join-Path $runtime 'local-play/pids') -Force | Out-Null
$lock = [IO.File]::Open((Join-Path $runtime 'local-play/operator.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
function Stop-Owned([string]$Role) {
    $state = Role-State $Role
    if ($state.state -ceq 'STOPPED' -or $state.state -ceq 'STALE_RECORD') { return }
    if (!$state.recordVerified -or ($state.state -cne 'RUNNING' -and !($state.state -ceq 'INCONSISTENT' -and $state.reason -ceq 'marked JVM does not own all expected ports'))) { throw "STOP запрещён: $Role/$($state.state)/$($state.reason)" }
    $process = Get-Process -Id $state.pid -ErrorAction Stop
    if ($process.StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks) { throw 'PID incarnation изменился; STOP запрещён.' }
    $agent = Join-Path $PSScriptRoot 'runtime-shutdown.jar'
    if ((Get-FileHash $agent).Hash -ine $build.shutdownJarSha256) { throw 'Штатный shutdown helper изменён.' }
    # Login may close its attach channel while successfully exiting. Actual absence decides completion.
    $savedPreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & $java --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'classes') RequestShutdown ([string]$state.pid) $runtimeId $Role $runtime $database $agent
    $requestExit = $LASTEXITCODE; $ErrorActionPreference = $savedPreference
    $timeout = [int](Read-Ini $settings 'StopTimeoutSeconds')
    if ($timeout -lt 10 -or $timeout -gt 600) { throw 'StopTimeoutSeconds вне10..600.' }
    $null = $process.WaitForExit($timeout * 1000)
    if (!$process.HasExited -or (Get-LocalPlayPortOwners $ports[$Role]).Count) { throw "Штатный STOP не завершён: $Role. RequestExit=$requestExit. Force fallback отсутствует." }
    # Preserve the stale record for audit; START replaces it only after verifying absence.
    Write-Output "STOCK_STOP_CONFIRMED $Role PID=$($state.pid)"
}
function Wait-Role([string]$Role, [Diagnostics.Process]$Process, [int]$Seconds) {
    if ($Seconds -lt 1 -or $Seconds -gt 600) { throw 'Startup timeout вне1..600.' }
    $until = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        if ($Process.HasExited) { throw "$Role завершился при старте; смотрите local-play/logs." }
        $owners = Get-LocalPlayPortOwners $ports[$Role]
        foreach ($port in $ports[$Role]) { if ($owners.ContainsKey($port) -and $owners[$port] -ne $Process.Id) { throw "Порт $port занят чужим PID." } }
        if ($owners.Count -eq $ports[$Role].Count) { return }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $until)
    throw "$Role не открыл свои порты за$Seconds секунд. Force fallback отсутствует."
}
function Start-Role([string]$Role) {
    $directory = Join-Path $runtime $(if ($Role -ceq 'GameServer') {'game'} else {'login'})
    $options = (Get-Content -LiteralPath (Join-Path $directory 'java.cfg') -Raw).Trim()
    if ($options -match '-javaagent:|-agentlib:|-agentpath:|phantom\.localplay\.|[\r\n]') { throw 'Forensic/fault agents или runtime markers в java.cfg запрещены.' }
    $logRoot = Join-Path $runtime 'local-play/logs'; New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    # Existing ownership parser recognizes this relative JAR independent of spaces in the runtime root.
    $arguments = "$options -Dphantom.localplay.runtime=$runtimeId -Dphantom.localplay.role=$Role -jar ..\libs\$Role.jar"
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $directory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot "$Role-$stamp-stdout.log") -RedirectStandardError (Join-Path $logRoot "$Role-$stamp-stderr.log") -PassThru
    Save-LocalPlayRecord (Join-Path $runtime "local-play/pids/$Role.json") $Role ([pscustomobject]@{pid=$process.Id;startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks}) "$Role.jar" $runtimeId
    $timeoutKey = if ($Role -ceq 'GameServer') {'GameTimeoutSeconds'} else {'LoginTimeoutSeconds'}
    Wait-Role $Role $process ([int](Read-Ini $settings $timeoutKey))
}
try {
    if ($Action -ceq 'Stop') { Stop-Owned 'GameServer'; Stop-Owned 'LoginServer'; Write-Status; exit 0 }
    Assert-Jars
    $login = Role-State 'LoginServer'; $game = Role-State 'GameServer'
    if ($login.state -ceq 'RUNNING' -and $game.state -ceq 'RUNNING' -and $login.recordVerified -and $game.recordVerified) { Write-Status; exit 0 }
    foreach ($state in @($login,$game)) { if ($state.state -cnotin @('STOPPED','STALE_RECORD')) { throw "START запрещён: $($state.state)/$($state.reason). Сначала штатный STOP." } }
    $savedPreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    & $java -cp ((Join-Path $PSScriptRoot 'classes')+';'+(Join-Path $runtime 'libs/*')) RuntimeDatabaseCheck $database (Join-Path $runtime 'game/config/Database.ini') (Join-Path $runtime 'login/config/Database.ini')
    $databaseExit = $LASTEXITCODE; $ErrorActionPreference = $savedPreference
    if ($databaseExit -ne 0) { throw 'Read-only DB preflight не прошёл; JVM не запущены.' }
    Start-Role 'LoginServer'; Start-Role 'GameServer'; Write-Status
} finally { $lock.Dispose() }
