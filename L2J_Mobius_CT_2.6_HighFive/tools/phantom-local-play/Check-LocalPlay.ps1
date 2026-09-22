[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1')

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

function Get-DatabaseName
{
	param([string] $Path)
	$url = Get-IniValue $Path "URL"
	$match = [regex]::Match($url, "^jdbc:(?:mysql|mariadb)://[^/]+/([^?]+)(?:\?.*)?$", [Text.RegularExpressions.RegexOptions]::IgnoreCase)
	if (-not $match.Success) { return "<invalid>" }
	return $match.Groups[1].Value
}

$runtimeRoot = Get-RuntimeRoot
$manifestPath = Join-Path $runtimeRoot "local-play.json"
if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Runtime не найден. Запустите Build-LocalPlay.ps1." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$phantomConfig = Join-Path $runtimeRoot "game\config\Custom\PhantomPlayers.ini"
$personalConfig = Join-Path $runtimeRoot "game\config\Custom\PersonalCharacterQoL.ini"
$loginConfig = Join-Path $runtimeRoot "login\config\Server.ini"
$databaseMarker = Join-Path $runtimeRoot "DB_CONFIG_REQUIRED.txt"
$databaseStatus = [string] $manifest.databaseConfig
$databaseReady = (@("USER_CONFIRMED_EXISTING", "FRESH_LOCAL_PROVISIONED") -contains $databaseStatus) -and (-not (Test-Path -LiteralPath $databaseMarker))

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
if (-not $databaseReady) { $errors += "Local play не готов: DatabaseConfig=$databaseStatus; требуется USER_CONFIRMED_EXISTING или FRESH_LOCAL_PROVISIONED без DB_CONFIG_REQUIRED.txt." }
if ($databaseStatus -eq "FRESH_LOCAL_PROVISIONED")
{
	$databaseName = [string] $manifest.databaseName
	$loginDatabaseName = Get-DatabaseName (Join-Path $runtimeRoot "login\config\Database.ini")
	$gameDatabaseName = Get-DatabaseName (Join-Path $runtimeRoot "game\config\Database.ini")
	if ([string]::IsNullOrWhiteSpace($databaseName) -or ($loginDatabaseName -cne $databaseName) -or ($gameDatabaseName -cne $databaseName))
	{
		$errors += "Fresh runtime Database.ini не совпадает с manifest databaseName."
	}
}
foreach ($jar in @("LoginServer.jar", "GameServer.jar"))
{
	if (-not (Test-Path -LiteralPath (Join-Path $runtimeRoot "libs\$jar"))) { $errors += "Отсутствует libs/$jar." }
}
if ($errors.Count -gt 0)
{
	Write-Host "DatabaseConfig=$databaseStatus"
	Write-Warning "CHECK NOT READY: локальная база не подтверждена для local play."
	$errors | ForEach-Object { Write-Error $_ }
	exit 1
}

$loginPort = [int] (Get-IniValue $loginConfig "LoginPort")
$clientPort = [int] (Get-IniValue $loginConfig "LoginserverPort")
$gamePort = [int] (Get-IniValue (Join-Path $runtimeRoot "game\config\Server.ini") "GameserverPort")
$loginState = Get-LocalPlayRoleState $runtimeRoot 'LoginServer' 'LoginServer.jar' @($clientPort, $loginPort)
$gameState = Get-LocalPlayRoleState $runtimeRoot 'GameServer' 'GameServer.jar' @($gamePort)
$owners = Get-LocalPlayPortOwners @($clientPort, $loginPort, $gamePort)
Write-Host "CONFIG PASS"
Write-Host "Preset=$($manifest.preset) Population=$($manifest.populationTarget) Active=$($manifest.activeTarget) MaterializedCap=$($manifest.materializedCap) PulseMs=$($manifest.schedulerPulseMillis)"
Write-Host "Ecology=$($manifest.ecology) HumanizedV3=$($manifest.humanizedV3) CustomOverlay=$($manifest.customOverlay) Mature=$($manifest.mature) Diagnostics=$($manifest.diagnostics)"
Write-Host "AutoCreateAccounts=$($manifest.autoCreateAccounts) PersonalQoLAccount=$($manifest.account) AutoNoblesseGlobal=$($manifest.autoNoblesseGlobal)"
Write-Host "DatabaseConfig=$databaseStatus"
Write-Host "LoginServer=$($loginState.state)(pid=$($loginState.pid), staleRecord=$($loginState.staleRecord)) Port${clientPort}=$($owners.ContainsKey($clientPort)) owner=$($owners[$clientPort]) Port${loginPort}=$($owners.ContainsKey($loginPort)) owner=$($owners[$loginPort])"
Write-Host "GameServer=$($gameState.state)(pid=$($gameState.pid), staleRecord=$($gameState.staleRecord)) Port${gamePort}=$($owners.ContainsKey($gamePort)) owner=$($owners[$gamePort])"
if (($loginState.state -eq 'RUNNING') -xor ($gameState.state -eq 'RUNNING')) { Write-Warning 'PARTIAL PAIR: only one role is running.' }
if (($loginState.state -eq 'FOREIGN_PORT_OWNER') -or ($gameState.state -eq 'FOREIGN_PORT_OWNER') -or ($loginState.state -eq 'INCONSISTENT') -or ($gameState.state -eq 'INCONSISTENT')) { exit 2 }
