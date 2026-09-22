[CmdletBinding()]
param([int] $GraceSeconds = 15)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1')

function Get-RuntimeRoot
{
	if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "local-play.json")) { return $PSScriptRoot }
	$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
	return (Join-Path $moduleRoot "artifacts\local-play\runtime")
}

function Get-IniValue([string] $Path, [string] $Key)
{
	$text = [IO.File]::ReadAllText($Path)
	$match = [regex]::Match($text, '(?m)^[ \t]*' + [regex]::Escape($Key) + '[ \t]*=[ \t]*([^\r\n]*)\r?$')
	if (-not $match.Success) { throw "Не найден ключ '$Key' в '$Path'." }
	return $match.Groups[1].Value.Trim()
}

function Stop-OwnedProcess([string] $RuntimeRoot, [string] $Role, [string] $Jar, [int[]] $Ports)
{
	$recordPath = Join-Path $RuntimeRoot "local-play\pids\$Role.json"
	$state = Get-LocalPlayRoleState $RuntimeRoot $Role $Jar $Ports
	if (($state.state -eq 'STOPPED') -or ($state.state -eq 'STALE_RECORD'))
	{
		if (Test-Path -LiteralPath $recordPath) { Remove-Item -LiteralPath $recordPath -Force }
		Write-Host "$Role=$($state.state)."
		return
	}
	if (($state.state -ne 'RUNNING') -and !(($state.state -eq 'INCONSISTENT') -and $state.recordVerified)) { throw "$Role=$($state.state): $($state.reason). Чужой/неподтверждённый процесс не остановлен." }
	$process = Get-Process -Id $state.pid -ErrorAction Stop
	if ($process.StartTime.ToUniversalTime().Ticks -ne [long] $state.startTimeUtcTicks) { throw "$Role PID=$($state.pid) сменился до STOP; остановка заблокирована." }
	if ($process.CloseMainWindow()) { $null = $process.WaitForExit($GraceSeconds * 1000) }
	if (-not $process.HasExited)
	{
		Stop-Process -Id $state.pid -Force -ErrorAction Stop
		$null = $process.WaitForExit(10000)
	}
	if (Get-Process -Id $state.pid -ErrorAction SilentlyContinue) { throw "$Role PID=$($state.pid) ещё жив; record сохранён." }
	$owners = Get-LocalPlayPortOwners $Ports
	foreach ($port in $Ports) { if ($owners.ContainsKey($port)) { throw "$Role остановлен, но порт $port занят PID=$($owners[$port]); record сохранён." } }
	if (Test-Path -LiteralPath $recordPath) { Remove-Item -LiteralPath $recordPath -Force }
	Write-Host "$Role PID=$($state.pid) остановлен."
}

$runtimeRoot = Get-RuntimeRoot
$loginConfig = Join-Path $runtimeRoot 'login\config\Server.ini'
$loginPort = [int] (Get-IniValue $loginConfig 'LoginPort')
$clientPort = [int] (Get-IniValue $loginConfig 'LoginserverPort')
$gamePort = [int] (Get-IniValue (Join-Path $runtimeRoot 'game\config\Server.ini') 'GameserverPort')
Stop-OwnedProcess $runtimeRoot 'GameServer' 'GameServer.jar' @($gamePort)
Stop-OwnedProcess $runtimeRoot 'LoginServer' 'LoginServer.jar' @($clientPort, $loginPort)
$finalGame = Get-LocalPlayRoleState $runtimeRoot 'GameServer' 'GameServer.jar' @($gamePort)
$finalLogin = Get-LocalPlayRoleState $runtimeRoot 'LoginServer' 'LoginServer.jar' @($clientPort, $loginPort)
if (($finalGame.state -ne 'STOPPED') -or ($finalLogin.state -ne 'STOPPED')) { throw "STOP incomplete: Login=$($finalLogin.state), Game=$($finalGame.state)." }
Write-Host "Остановка завершена. Глобальный taskkill не использовался."
