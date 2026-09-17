[CmdletBinding()]
param(
	[int] $LoginTimeoutSeconds = 60,
	[int] $GameTimeoutSeconds = 600
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RuntimeRoot
{
	if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "local-play.json"))
	{
		return $PSScriptRoot
	}
	$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
	return (Join-Path $moduleRoot "artifacts\local-play\runtime")
}

function Get-IniValue
{
	param([string] $Path, [string] $Key)
	$text = [IO.File]::ReadAllText($Path)
	$match = [regex]::Match($text, "(?m)^[ \t]*" + [regex]::Escape($Key) + "[ \t]*=[ \t]*([^\r\n]*)\r?$")
	if (-not $match.Success) { throw "Не найден ключ '$Key' в '$Path'." }
	return $match.Groups[1].Value.Trim()
}

function Get-DatabaseName
{
	param([string] $Path)
	$url = Get-IniValue $Path "URL"
	$match = [regex]::Match($url, "^jdbc:(?:mysql|mariadb)://[^/]+/([^?]+)(?:\?.*)?$", [Text.RegularExpressions.RegexOptions]::IgnoreCase)
	if (-not $match.Success) { throw "Некорректный JDBC URL в private runtime Database.ini." }
	return $match.Groups[1].Value
}

function Test-OwnedProcess
{
	param([string] $RecordPath)
	if (-not (Test-Path -LiteralPath $RecordPath)) { return $false }
	try
	{
		$record = Get-Content -LiteralPath $RecordPath -Raw | ConvertFrom-Json
		$process = Get-Process -Id ([int] $record.pid) -ErrorAction Stop
		return $process.StartTime.ToUniversalTime().ToString("o") -eq [string] $record.startTimeUtc
	}
	catch
	{
		return $false
	}
}

function Wait-TcpPort
{
	param([int] $Port, [int] $TimeoutSeconds, [Diagnostics.Process] $Process)
	$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
	do
	{
		if ($Process.HasExited) { return $false }
		$client = [Net.Sockets.TcpClient]::new()
		try
		{
			$task = $client.ConnectAsync("127.0.0.1", $Port)
			if ($task.Wait(500) -and $client.Connected) { return $true }
		}
		catch { }
		finally { $client.Dispose() }
		Start-Sleep -Milliseconds 500
	}
	while ([DateTime]::UtcNow -lt $deadline)
	return $false
}

function Start-Server
{
	param([string] $Role, [string] $WorkingDirectory, [string] $JarName, [string] $RecordPath)
	$javaHomeExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { $null }
	$javaExecutable = if ($javaHomeExecutable -and (Test-Path -LiteralPath $javaHomeExecutable)) { $javaHomeExecutable } else { (Get-Command java.exe -ErrorAction Stop).Source }
	$javaOptions = @((Get-Content -LiteralPath (Join-Path $WorkingDirectory "java.cfg") -Raw).Trim() -split "\s+" | Where-Object { $_ })
	$arguments = @($javaOptions + @("-jar", "..\libs\$JarName"))
	$process = Start-Process -FilePath $javaExecutable -ArgumentList $arguments -WorkingDirectory $WorkingDirectory -PassThru
	$record = [ordered]@{ role = $Role; pid = $process.Id; startTimeUtc = $process.StartTime.ToUniversalTime().ToString("o") }
	$record | ConvertTo-Json | Set-Content -LiteralPath $RecordPath -Encoding UTF8
	return $process
}

$runtimeRoot = Get-RuntimeRoot
$manifestPath = Join-Path $runtimeRoot "local-play.json"
if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Runtime не собран. Сначала запустите Build-LocalPlay.ps1." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$databaseStatus = [string] $manifest.databaseConfig
$allowedDatabaseStatuses = @("USER_CONFIRMED_EXISTING", "FRESH_LOCAL_PROVISIONED")
if (($allowedDatabaseStatuses -notcontains $databaseStatus) -or (Test-Path -LiteralPath (Join-Path $runtimeRoot "DB_CONFIG_REQUIRED.txt")))
{
	throw "Local play заблокирован: DatabaseConfig=$($manifest.databaseConfig). Нужна явная локальная DB-конфигурация и подтверждение при сборке."
}
if ($databaseStatus -eq "FRESH_LOCAL_PROVISIONED")
{
	$databaseName = [string] $manifest.databaseName
	if ([string]::IsNullOrWhiteSpace($databaseName)) { throw "Fresh local play заблокирован: manifest не содержит databaseName." }
	$loginDatabaseName = Get-DatabaseName (Join-Path $runtimeRoot "login\config\Database.ini")
	$gameDatabaseName = Get-DatabaseName (Join-Path $runtimeRoot "game\config\Database.ini")
	if (($loginDatabaseName -cne $databaseName) -or ($gameDatabaseName -cne $databaseName))
	{
		throw "Fresh local play заблокирован: runtime Database.ini не совпадает с manifest databaseName."
	}
}

$pidRoot = Join-Path $runtimeRoot "local-play\pids"
New-Item -ItemType Directory -Path $pidRoot -Force | Out-Null
$loginRecord = Join-Path $pidRoot "LoginServer.json"
$gameRecord = Join-Path $pidRoot "GameServer.json"
if ((Test-OwnedProcess $loginRecord) -or (Test-OwnedProcess $gameRecord)) { throw "Local play уже запущен. Используйте CHECK_LOCAL_PLAY.cmd." }
Remove-Item -LiteralPath $loginRecord, $gameRecord -Force -ErrorAction SilentlyContinue

$loginDirectory = Join-Path $runtimeRoot "login"
$gameDirectory = Join-Path $runtimeRoot "game"
$loginPort = [int] (Get-IniValue (Join-Path $loginDirectory "config\Server.ini") "LoginPort")
$gamePort = [int] (Get-IniValue (Join-Path $gameDirectory "config\Server.ini") "GameserverPort")

$loginProcess = Start-Server -Role "LoginServer" -WorkingDirectory $loginDirectory -JarName "LoginServer.jar" -RecordPath $loginRecord
if (-not (Wait-TcpPort -Port $loginPort -TimeoutSeconds $LoginTimeoutSeconds -Process $loginProcess))
{
	& (Join-Path $runtimeRoot "Stop-LocalPlay.ps1")
	throw "LoginServer не открыл порт $loginPort за $LoginTimeoutSeconds секунд. Проверьте runtime/login/log."
}

$gameProcess = Start-Server -Role "GameServer" -WorkingDirectory $gameDirectory -JarName "GameServer.jar" -RecordPath $gameRecord
if (-not (Wait-TcpPort -Port $gamePort -TimeoutSeconds $GameTimeoutSeconds -Process $gameProcess))
{
	Write-Warning "GameServer не открыл порт $gamePort за $GameTimeoutSeconds секунд. Выполняется остановка только записанных PID."
	& (Join-Path $runtimeRoot "Stop-LocalPlay.ps1")
	throw "GameServer startup failed. Проверьте runtime/game/log."
}

Write-Host "Local play запущен: Login PID=$($loginProcess.Id), Game PID=$($gameProcess.Id)."
Write-Host "Логи: $(Join-Path $runtimeRoot 'login\log') и $(Join-Path $runtimeRoot 'game\log')."
