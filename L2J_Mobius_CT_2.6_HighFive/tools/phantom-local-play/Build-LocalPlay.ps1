[CmdletBinding()]
param(
	[ValidateSet("Balanced", "Lively", "Stress")]
	[string] $Preset = "Lively",
	[ValidatePattern("^[a-z0-9_-]{1,45}$")]
	[string] $Account = "localplayer",
	[switch] $Mature,
	[switch] $Diagnostics,
	[switch] $ConfirmExistingDatabaseForLocalPlay,
	[switch] $SanitizedZip
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Set-IniValue
{
	param([string] $Path, [string] $Key, [string] $Value)

	$text = [IO.File]::ReadAllText($Path)
	$pattern = "(?m)^([ \t]*" + [regex]::Escape($Key) + "[ \t]*=[ \t]*).*$"
	$matches = [regex]::Matches($text, $pattern)
	if ($matches.Count -ne 1)
	{
		throw "Ожидался ровно один ключ '$Key' в '$Path', найдено: $($matches.Count)."
	}
	$text = [regex]::Replace($text, $pattern, { param($match) $match.Groups[1].Value + $Value })
	[IO.File]::WriteAllText($Path, $text, [Text.UTF8Encoding]::new($false))
}

function Get-IniValue
{
	param([string] $Path, [string] $Key)

	$text = [IO.File]::ReadAllText($Path)
	$pattern = "(?m)^[ \t]*" + [regex]::Escape($Key) + "[ \t]*=[ \t]*([^\r\n]*)\r?$"
	$matches = [regex]::Matches($text, $pattern)
	if ($matches.Count -ne 1)
	{
		return $null
	}
	return $matches[0].Groups[1].Value.Trim()
}

function Assert-CommentsPreserved
{
	param([string] $Source, [string] $Copy)

	$sourceComments = @([IO.File]::ReadAllLines($Source) | Where-Object { $_ -match "^\s*[#;]" })
	$copyComments = @([IO.File]::ReadAllLines($Copy) | Where-Object { $_ -match "^\s*[#;]" })
	if (($sourceComments.Count -ne $copyComments.Count) -or (($sourceComments -join "`n") -cne ($copyComments -join "`n")))
	{
		throw "Комментарии изменились при patch-only обработке '$Copy'."
	}
}

function Test-PrivateDatabaseConfig
{
	param([string] $Path)

	if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
	{
		return $false
	}
	$url = Get-IniValue -Path $Path -Key "URL"
	$login = Get-IniValue -Path $Path -Key "Login"
	$password = Get-IniValue -Path $Path -Key "Password"
	return (-not [string]::IsNullOrWhiteSpace($url)) -and (-not [string]::IsNullOrWhiteSpace($login)) -and (-not [string]::IsNullOrWhiteSpace($password))
}

function Assert-UnderRoot
{
	param([string] $Path, [string] $Root)

	$fullPath = [IO.Path]::GetFullPath($Path)
	$fullRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
	if (-not $fullPath.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase))
	{
		throw "Опасный путь вне разрешённого корня: $fullPath"
	}
}

$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $moduleRoot ".."))
$sourceDist = Join-Path $moduleRoot "dist"
$artifactRoot = Join-Path $moduleRoot "artifacts\local-play"
$runtimeRoot = Join-Path $artifactRoot "runtime"
$temporaryRuntime = Join-Path $artifactRoot ("runtime.build." + $PID)
$buildDist = Join-Path $repositoryRoot "build\dist"

Assert-UnderRoot -Path $artifactRoot -Root $moduleRoot
Assert-UnderRoot -Path $buildDist -Root $repositoryRoot

$presetValues = @{
	Balanced = @{ Population = 120; Active = 24; Materialized = 32 }
	Lively = @{ Population = 160; Active = 32; Materialized = 48 }
	Stress = @{ Population = 240; Active = 48; Materialized = 64 }
}
$selected = $presetValues[$Preset]

$sourceIniFiles = @(Get-ChildItem -LiteralPath $sourceDist -Recurse -Filter "*.ini" -File)
$sourceHashesBefore = @{}
foreach ($file in $sourceIniFiles)
{
	$sourceHashesBefore[$file.FullName] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
}

Write-Host "Fresh build: ant -q jar"
if (Test-Path -LiteralPath $buildDist)
{
	Remove-Item -LiteralPath $buildDist -Recurse -Force
}
Push-Location $moduleRoot
try
{
	& ant -q jar
	if ($LASTEXITCODE -ne 0)
	{
		throw "ant -q jar завершился с кодом $LASTEXITCODE."
	}
}
finally
{
	Pop-Location
}

$loginJar = Join-Path $sourceDist "libs\LoginServer.jar"
$gameJar = Join-Path $sourceDist "libs\GameServer.jar"
foreach ($jar in @($loginJar, $gameJar))
{
	if (-not (Test-Path -LiteralPath $jar -PathType Leaf))
	{
		throw "После сборки отсутствует '$jar'."
	}
}

New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
if (Test-Path -LiteralPath $temporaryRuntime)
{
	Remove-Item -LiteralPath $temporaryRuntime -Recurse -Force
}
New-Item -ItemType Directory -Path $temporaryRuntime | Out-Null
Copy-Item -Path (Join-Path $sourceDist "*") -Destination $temporaryRuntime -Recurse -Force

$phantomConfig = Join-Path $temporaryRuntime "game\config\Custom\PhantomPlayers.ini"
$marketConfig = Join-Path $temporaryRuntime "game\config\Custom\PhantomMarket.ini"
$characterConfig = Join-Path $temporaryRuntime "game\config\Custom\PersonalCharacterQoL.ini"
$premiumConfig = Join-Path $temporaryRuntime "game\config\Custom\PersonalPremiumQoL.ini"
$progressionConfig = Join-Path $temporaryRuntime "game\config\Custom\PersonalProgressionQoL.ini"
$loginServerConfig = Join-Path $temporaryRuntime "login\config\Server.ini"

$patches = [ordered]@{
	$phantomConfig = [ordered]@{
		EnablePhantomSystem = "True"
		EnablePhantomHumanizedConversation = "True"
		EnablePhantomCustomConversationPack = "True"
		EnablePhantomMatureConversation = $(if ($Mature) { "True" } else { "False" })
		EnablePhantomDiagnostics = $(if ($Diagnostics) { "True" } else { "False" })
		MaxMaterializedPhantoms = [string] $selected.Materialized
		MaxScheduledPhantomProfiles = "10000"
		PhantomSchedulerPulseMillis = "100"
		PhantomSchedulerProfilesPerPulse = "128"
		PhantomPopulationTarget = [string] $selected.Population
		PhantomPopulationActiveTarget = [string] $selected.Active
		EnablePhantomEcology = "True"
		PhantomEcologyPreset = "LIVING"
	}
	$marketConfig = [ordered]@{
		EnableAutonomousPhantomMarket = "True"
	}
	$characterConfig = [ordered]@{
		EnablePersonalCharacterQoL = "True"
		EnablePersonalCrossClassSkills = "True"
		EnablePersonalCrystallization = "True"
		EnablePersonalSevenSignsAccess = "True"
		EnablePersonalPartySupport = "True"
		EnablePersonalEffectDurations = "True"
		AllowedAccounts = $Account
	}
	$premiumConfig = [ordered]@{
		EnablePersonalPremiumQoL = "True"
		EnablePersonalPremiumShop = "True"
	}
	$progressionConfig = [ordered]@{
		EnablePersonalQuestOverLevelRelief = "True"
		EnableServerWideAutoNoblesse = "True"
	}
	$loginServerConfig = [ordered]@{
		AutoCreateAccounts = "True"
	}
}

foreach ($entry in $patches.GetEnumerator())
{
	foreach ($setting in $entry.Value.GetEnumerator())
	{
		Set-IniValue -Path $entry.Key -Key $setting.Key -Value ([string] $setting.Value)
	}
	$relativePath = $entry.Key.Substring($temporaryRuntime.Length + 1)
	Assert-CommentsPreserved -Source (Join-Path $sourceDist $relativePath) -Copy $entry.Key
}

$toolFiles = @("Start-LocalPlay.ps1", "Stop-LocalPlay.ps1", "Check-LocalPlay.ps1")
foreach ($toolFile in $toolFiles)
{
	Copy-Item -LiteralPath (Join-Path $PSScriptRoot $toolFile) -Destination (Join-Path $temporaryRuntime $toolFile) -Force
}

$startCmd = "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Start-LocalPlay.ps1`"`r`n"
$stopCmd = "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Stop-LocalPlay.ps1`"`r`n"
$checkCmd = "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Check-LocalPlay.ps1`"`r`n"
[IO.File]::WriteAllText((Join-Path $temporaryRuntime "START_LOCAL_PLAY.cmd"), $startCmd, [Text.Encoding]::ASCII)
[IO.File]::WriteAllText((Join-Path $temporaryRuntime "STOP_LOCAL_PLAY.cmd"), $stopCmd, [Text.Encoding]::ASCII)
[IO.File]::WriteAllText((Join-Path $temporaryRuntime "CHECK_LOCAL_PLAY.cmd"), $checkCmd, [Text.Encoding]::ASCII)

$loginDatabaseConfig = Join-Path $temporaryRuntime "login\config\Database.ini"
$gameDatabaseConfig = Join-Path $temporaryRuntime "game\config\Database.ini"
$databaseConfigComplete = (Test-PrivateDatabaseConfig $loginDatabaseConfig) -and (Test-PrivateDatabaseConfig $gameDatabaseConfig)
$databaseConfigStatus = if ($databaseConfigComplete -and $ConfirmExistingDatabaseForLocalPlay)
{
	"USER_CONFIRMED_EXISTING"
}
elseif ($databaseConfigComplete)
{
	"COPIED_PRIVATE_UNVERIFIED"
}
else
{
	"DB_CONFIG_REQUIRED"
}
if ($databaseConfigStatus -ne "USER_CONFIRMED_EXISTING")
{
	[IO.File]::WriteAllText((Join-Path $temporaryRuntime "DB_CONFIG_REQUIRED.txt"), "Укажите локальные LoginServer и GameServer Database.ini. Не используйте production DB для автоматических smoke-тестов.`r`n", [Text.UTF8Encoding]::new($true))
}

$manifest = [ordered]@{
	format = 1
	createdUtc = [DateTime]::UtcNow.ToString("o")
	preset = $Preset
	account = $Account
	populationTarget = $selected.Population
	activeTarget = $selected.Active
	materializedCap = $selected.Materialized
	schedulerPulseMillis = 100
	schedulerProfilesPerPulse = 128
	ecology = "LIVING"
	humanizedV3 = $true
	customOverlay = $true
	mature = [bool] $Mature
	diagnostics = [bool] $Diagnostics
	autoCreateAccounts = $true
	personalQoL = $true
	autoNoblesseGlobal = $true
	databaseConfig = $databaseConfigStatus
	loginJarSha256 = (Get-FileHash -LiteralPath (Join-Path $temporaryRuntime "libs\LoginServer.jar") -Algorithm SHA256).Hash
	gameJarSha256 = (Get-FileHash -LiteralPath (Join-Path $temporaryRuntime "libs\GameServer.jar") -Algorithm SHA256).Hash
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $temporaryRuntime "local-play.json") -Encoding UTF8

if (Test-Path -LiteralPath $runtimeRoot)
{
	Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
}
Move-Item -LiteralPath $temporaryRuntime -Destination $runtimeRoot

$sourceChanged = @()
foreach ($file in $sourceIniFiles)
{
	if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne $sourceHashesBefore[$file.FullName])
	{
		$sourceChanged += $file.FullName
	}
}
if ($sourceChanged.Count -gt 0)
{
	throw "Исходные INI были изменены: $($sourceChanged -join ', ')"
}

$zipPath = Join-Path $artifactRoot "L2J-H5-Phantom-LocalPlay-sanitized.zip"
if ($SanitizedZip)
{
	$sanitizedRoot = Join-Path $artifactRoot ("sanitized.build." + $PID)
	if (Test-Path -LiteralPath $sanitizedRoot)
	{
		Remove-Item -LiteralPath $sanitizedRoot -Recurse -Force
	}
	New-Item -ItemType Directory -Path $sanitizedRoot | Out-Null
	Copy-Item -Path (Join-Path $runtimeRoot "*") -Destination $sanitizedRoot -Recurse -Force
	foreach ($databaseConfig in @((Join-Path $sanitizedRoot "login\config\Database.ini"), (Join-Path $sanitizedRoot "game\config\Database.ini")))
	{
		if (Test-Path -LiteralPath $databaseConfig)
		{
			Set-IniValue -Path $databaseConfig -Key "Login" -Value ""
			Set-IniValue -Path $databaseConfig -Key "Password" -Value ""
		}
	}
	[IO.File]::WriteAllText((Join-Path $sanitizedRoot "DB_CONFIG_REQUIRED.txt"), "Перед запуском укажите локальные реквизиты в login/config/Database.ini и game/config/Database.ini.`r`n", [Text.UTF8Encoding]::new($true))
	$sanitizedManifestPath = Join-Path $sanitizedRoot "local-play.json"
	$sanitizedManifest = Get-Content -LiteralPath $sanitizedManifestPath -Raw | ConvertFrom-Json
	$sanitizedManifest.databaseConfig = "DB_CONFIG_REQUIRED"
	$sanitizedManifest | ConvertTo-Json | Set-Content -LiteralPath $sanitizedManifestPath -Encoding UTF8
	if (Test-Path -LiteralPath $zipPath)
	{
		Remove-Item -LiteralPath $zipPath -Force
	}
	Compress-Archive -Path (Join-Path $sanitizedRoot "*") -DestinationPath $zipPath -CompressionLevel Optimal
	Remove-Item -LiteralPath $sanitizedRoot -Recurse -Force
}

Write-Host "Local-play runtime: $runtimeRoot"
Write-Host "Preset=$Preset Population=$($selected.Population) Active=$($selected.Active) MaterializedCap=$($selected.Materialized) PulseMs=100"
Write-Host "Ecology=LIVING HumanizedV3=True CustomOverlay=True Mature=$([bool] $Mature) Diagnostics=$([bool] $Diagnostics)"
Write-Host "AutoCreateAccounts=True PersonalQoLAccount=$Account AutoNoblesseGlobal=True"
Write-Host "DatabaseConfig=$databaseConfigStatus"
if ($SanitizedZip)
{
	Write-Host "Sanitized ZIP: $zipPath"
}
