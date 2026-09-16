[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RuntimeRoot
{
	if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "local-play.json")) { return $PSScriptRoot }
	$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
	return (Join-Path $moduleRoot "artifacts\local-play\runtime")
}

function Get-IniValue
{
	param([string] $Path, [string] $Key)
	$text = [IO.File]::ReadAllText($Path)
	$match = [regex]::Match($text, "(?m)^[ \t]*" + [regex]::Escape($Key) + "[ \t]*=[ \t]*([^\r\n]*)\r?$")
	if (-not $match.Success) { return "<missing>" }
	return $match.Groups[1].Value.Trim()
}

function Get-ProcessState
{
	param([string] $RecordPath)
	if (-not (Test-Path -LiteralPath $RecordPath)) { return "STOPPED" }
	try
	{
		$record = Get-Content -LiteralPath $RecordPath -Raw | ConvertFrom-Json
		$process = Get-Process -Id ([int] $record.pid) -ErrorAction Stop
		if ($process.StartTime.ToUniversalTime().ToString("o") -ne [string] $record.startTimeUtc) { return "STALE_PID" }
		return "RUNNING(pid=$($process.Id))"
	}
	catch { return "STOPPED(stale-record)" }
}

function Test-TcpPort
{
	param([int] $Port)
	$client = [Net.Sockets.TcpClient]::new()
	try { return $client.ConnectAsync("127.0.0.1", $Port).Wait(500) -and $client.Connected }
	catch { return $false }
	finally { $client.Dispose() }
}

$runtimeRoot = Get-RuntimeRoot
$manifestPath = Join-Path $runtimeRoot "local-play.json"
if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Runtime не найден. Запустите Build-LocalPlay.ps1." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$phantomConfig = Join-Path $runtimeRoot "game\config\Custom\PhantomPlayers.ini"
$personalConfig = Join-Path $runtimeRoot "game\config\Custom\PersonalCharacterQoL.ini"
$loginConfig = Join-Path $runtimeRoot "login\config\Server.ini"

$expected = [ordered]@{
	EnablePhantomSystem = "True"
	PhantomPopulationTarget = [string] $manifest.populationTarget
	PhantomPopulationActiveTarget = [string] $manifest.activeTarget
	MaxMaterializedPhantoms = [string] $manifest.materializedCap
	PhantomSchedulerPulseMillis = "100"
	EnablePhantomEcology = "True"
	PhantomEcologyPreset = "LIVING"
	EnablePhantomHumanizedConversation = "True"
	EnablePhantomCustomConversationPack = "True"
	EnablePhantomMatureConversation = $(if ($manifest.mature) { "True" } else { "False" })
	EnablePhantomDiagnostics = $(if ($manifest.diagnostics) { "True" } else { "False" })
}
$errors = @()
foreach ($setting in $expected.GetEnumerator())
{
	$actual = Get-IniValue $phantomConfig $setting.Key
	if ($actual -cne $setting.Value) { $errors += "$($setting.Key): expected '$($setting.Value)', actual '$actual'" }
}
if ((Get-IniValue $personalConfig "AllowedAccounts") -cne [string] $manifest.account) { $errors += "AllowedAccounts не совпадает с manifest." }
if ((Get-IniValue $loginConfig "AutoCreateAccounts") -cne "True") { $errors += "AutoCreateAccounts не равен True." }
foreach ($jar in @("LoginServer.jar", "GameServer.jar"))
{
	if (-not (Test-Path -LiteralPath (Join-Path $runtimeRoot "libs\$jar"))) { $errors += "Отсутствует libs/$jar." }
}
if ($errors.Count -gt 0)
{
	$errors | ForEach-Object { Write-Error $_ }
	exit 1
}

$loginPort = [int] (Get-IniValue $loginConfig "LoginPort")
$gamePort = [int] (Get-IniValue (Join-Path $runtimeRoot "game\config\Server.ini") "GameserverPort")
$pidRoot = Join-Path $runtimeRoot "local-play\pids"
Write-Host "CONFIG PASS"
Write-Host "Preset=$($manifest.preset) Population=$($manifest.populationTarget) Active=$($manifest.activeTarget) MaterializedCap=$($manifest.materializedCap) PulseMs=$($manifest.schedulerPulseMillis)"
Write-Host "Ecology=$($manifest.ecology) HumanizedV3=$($manifest.humanizedV3) CustomOverlay=$($manifest.customOverlay) Mature=$($manifest.mature) Diagnostics=$($manifest.diagnostics)"
Write-Host "AutoCreateAccounts=$($manifest.autoCreateAccounts) PersonalQoLAccount=$($manifest.account) AutoNoblesseGlobal=$($manifest.autoNoblesseGlobal)"
Write-Host "DatabaseConfig=$($manifest.databaseConfig)"
Write-Host "LoginServer=$(Get-ProcessState (Join-Path $pidRoot 'LoginServer.json')) Port${loginPort}=$(Test-TcpPort $loginPort)"
Write-Host "GameServer=$(Get-ProcessState (Join-Path $pidRoot 'GameServer.json')) Port${gamePort}=$(Test-TcpPort $gamePort)"
if (Test-Path -LiteralPath (Join-Path $runtimeRoot "DB_CONFIG_REQUIRED.txt")) { Write-Warning "DB_CONFIG_REQUIRED" }
