[CmdletBinding()]
param(
	[ValidatePattern("^[A-Za-z0-9_]{1,48}$")]
	[string] $DatabaseName = "l2jmobiush5_localplay",
	[ValidateSet("Balanced", "Lively", "Stress")]
	[string] $Preset = "Lively",
	[ValidatePattern("^[a-z0-9_-]{1,45}$")]
	[string] $Account = "localplayer",
	[switch] $Mature,
	[switch] $Diagnostics,
	[switch] $SanitizedZip
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-IniValue
{
	param([string] $Path, [string] $Key)

	$text = [IO.File]::ReadAllText($Path)
	$matches = [regex]::Matches($text, "(?m)^[ \t]*" + [regex]::Escape($Key) + "[ \t]*=[ \t]*([^\r\n]*)\r?$")
	if ($matches.Count -ne 1)
	{
		throw "Ожидался ровно один ключ '$Key' в '$Path', найдено: $($matches.Count)."
	}
	return $matches[0].Groups[1].Value.Trim()
}

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

function Get-JdbcTarget
{
	param([string] $Url)

	$match = [regex]::Match($Url, "^jdbc:(mysql|mariadb)://([^/:?]+)(?::([0-9]+))?/([^?]+)(\?.*)?$", [Text.RegularExpressions.RegexOptions]::IgnoreCase)
	if (-not $match.Success)
	{
		throw "Неподдерживаемый JDBC URL в исходной конфигурации."
	}
	$port = if ($match.Groups[3].Success) { [int] $match.Groups[3].Value } else { 3306 }
	return [pscustomobject]@{
		Transport = $match.Groups[1].Value.ToLowerInvariant()
		Host = $match.Groups[2].Value
		Port = $port
		Database = $match.Groups[4].Value
		Query = $match.Groups[5].Value
	}
}

function Set-JdbcDatabase
{
	param([string] $Url, [string] $Name)

	$target = Get-JdbcTarget $Url
	return "jdbc:$($target.Transport)://$($target.Host):$($target.Port)/$Name$($target.Query)"
}

function ConvertTo-QuotedProcessArgument
{
	param([string] $Value)
	return '"' + $Value.Replace('"', '\"') + '"'
}

function Invoke-JavaProcess
{
	param(
		[string] $WorkingDirectory,
		[string[]] $Arguments,
		[string[]] $InputLines
	)

	$javaHomeExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { $null }
	$javaExecutable = if ($javaHomeExecutable -and (Test-Path -LiteralPath $javaHomeExecutable)) { $javaHomeExecutable } else { (Get-Command java.exe -ErrorAction Stop).Source }
	$startInfo = [Diagnostics.ProcessStartInfo]::new()
	$startInfo.FileName = $javaExecutable
	$startInfo.Arguments = (($Arguments | ForEach-Object { ConvertTo-QuotedProcessArgument $_ }) -join " ")
	$startInfo.WorkingDirectory = $WorkingDirectory
	$startInfo.UseShellExecute = $false
	$startInfo.CreateNoWindow = $true
	$startInfo.RedirectStandardInput = $true
	$startInfo.RedirectStandardOutput = $true
	$startInfo.RedirectStandardError = $true
	$process = [Diagnostics.Process]::new()
	$process.StartInfo = $startInfo
	if (-not $process.Start())
	{
		throw "Не удалось запустить Java helper."
	}
	$stdoutTask = $process.StandardOutput.ReadToEndAsync()
	$stderrTask = $process.StandardError.ReadToEndAsync()
	try
	{
		foreach ($line in $InputLines)
		{
			$process.StandardInput.WriteLine($line)
		}
	}
	finally
	{
		$process.StandardInput.Close()
	}
	$process.WaitForExit()
	return [pscustomobject]@{
		ExitCode = $process.ExitCode
		StdOut = $stdoutTask.Result
		StdErr = $stderrTask.Result
	}
}

function Invoke-DatabaseGuard
{
	param([string] $Operation, [string] $ExtraPath = "")

	$arguments = @("--class-path", $script:connectorJar, $script:guardSource, $Operation)
	if ($ExtraPath)
	{
		$arguments += $ExtraPath
	}
	$result = Invoke-JavaProcess -WorkingDirectory $script:moduleRoot -Arguments $arguments -InputLines @($script:serverUrl, $script:databaseLogin, $script:databasePassword, $DatabaseName)
	if ($result.ExitCode -ne 0)
	{
		$safeError = ($result.StdErr -split "[\r\n]+" | Where-Object { $_ -match "^ERROR:" } | Select-Object -First 1)
		if (-not $safeError) { $safeError = "Database guard завершился с кодом $($result.ExitCode)." }
		throw $safeError
	}
	return ($result.StdOut.Trim())
}

function Get-TrackedSourceHashes
{
	param([string[]] $Paths)
	$hashes = @{}
	foreach ($path in $Paths)
	{
		$hashes[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
	}
	return $hashes
}

function Assert-SourceHashesUnchanged
{
	param([hashtable] $Before)
	$changed = @()
	foreach ($path in $Before.Keys)
	{
		if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $Before[$path])
		{
			$changed += $path
		}
	}
	if ($changed.Count -gt 0)
	{
		throw "Защищённые source-файлы изменились: $($changed -join ', ')"
	}
}

function Set-ProvisioningFailure
{
	param([string] $RuntimeRoot)
	if (-not (Test-Path -LiteralPath $RuntimeRoot -PathType Container)) { return }
	[IO.File]::WriteAllText((Join-Path $RuntimeRoot "DB_CONFIG_REQUIRED.txt"), "Fresh local-play provisioning не завершён. Не запускайте этот runtime.`r`n", [Text.UTF8Encoding]::new($true))
	[IO.File]::WriteAllText((Join-Path $RuntimeRoot "FRESH_LOCAL_PROVISIONING_FAILED.txt"), "Provisioning завершился ошибкой. Используйте другое новое имя БД или удалите частично созданную БД вручную после проверки.`r`n", [Text.UTF8Encoding]::new($true))
	$manifestPath = Join-Path $RuntimeRoot "local-play.json"
	if (Test-Path -LiteralPath $manifestPath)
	{
		$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
		$manifest.databaseConfig = "FRESH_LOCAL_PROVISIONING_FAILED"
		$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
	}
}

$script:moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$sourceLoginDatabase = Join-Path $script:moduleRoot "dist\login\config\Database.ini"
$sourceGameDatabase = Join-Path $script:moduleRoot "dist\game\config\Database.ini"
$sourceHexid = Join-Path $script:moduleRoot "dist\game\config\hexid.txt"
$runtimeRoot = Join-Path $script:moduleRoot "artifacts\local-play\runtime"
$script:guardSource = Join-Path $PSScriptRoot "FreshLocalPlayDatabaseGuard.java"
$script:connectorJar = @(Get-ChildItem -LiteralPath (Join-Path $script:moduleRoot "dist\libs") -Filter "mysql-connector-j-*.jar" -File | Where-Object { $_.Name -notmatch "-sources\.jar$" } | Select-Object -ExpandProperty FullName)
if ($script:connectorJar.Count -ne 1)
{
	throw "Ожидался ровно один MySQL connector JAR, найдено: $($script:connectorJar.Count)."
}
$script:connectorJar = [string] $script:connectorJar[0]

$knownProtected = @("l2jmobiush5_phantom_test", "mysql", "information_schema", "performance_schema", "sys")
$loginUrl = Get-IniValue $sourceLoginDatabase "URL"
$gameUrl = Get-IniValue $sourceGameDatabase "URL"
$loginTarget = Get-JdbcTarget $loginUrl
$gameTarget = Get-JdbcTarget $gameUrl
$protectedDatabases = @($knownProtected + $loginTarget.Database + $gameTarget.Database | Select-Object -Unique)
if ($protectedDatabases | Where-Object { $_.Equals($DatabaseName, [StringComparison]::OrdinalIgnoreCase) })
{
	throw "Имя '$DatabaseName' защищено и не может быть целью fresh local-play."
}
if ($DatabaseName -notmatch "_localplay")
{
	Write-Warning "Имя '$DatabaseName' прошло safety gates, но для ясности рекомендуется суффикс _localplay."
}
if (($loginTarget.Host -cne $gameTarget.Host) -or ($loginTarget.Port -ne $gameTarget.Port))
{
	throw "Login/Game source configs указывают разные DB servers; автоматическое provisioning заблокировано."
}

$script:databaseLogin = Get-IniValue $sourceLoginDatabase "Login"
$script:databasePassword = Get-IniValue $sourceLoginDatabase "Password"
$gameLogin = Get-IniValue $sourceGameDatabase "Login"
$gamePassword = Get-IniValue $sourceGameDatabase "Password"
if (($script:databaseLogin -cne $gameLogin) -or ($script:databasePassword -cne $gamePassword))
{
	throw "Login/Game source configs используют разные DB credentials; автоматическое provisioning заблокировано."
}
$script:serverUrl = "jdbc:$($loginTarget.Transport)://$($loginTarget.Host):$($loginTarget.Port)/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"

$protectedFiles = @($sourceLoginDatabase, $sourceGameDatabase)
if (Test-Path -LiteralPath $sourceHexid) { $protectedFiles += $sourceHexid }
$protectedFiles += @(Get-ChildItem -LiteralPath (Join-Path $script:moduleRoot "dist\db_installer\sql\login"), (Join-Path $script:moduleRoot "dist\db_installer\sql\game") -Filter "*.sql" -File | Select-Object -ExpandProperty FullName)
$sourceHashesBefore = Get-TrackedSourceHashes $protectedFiles

Write-Host "Safety gate: проверка отсутствия NEW DB '$DatabaseName'."
$absentResult = Invoke-DatabaseGuard "assert-absent"
if ($absentResult -notmatch "^ABSENT ") { throw "Database absence gate не вернул ожидаемое подтверждение." }

$buildArguments = @{
	Preset = $Preset
	Account = $Account
}
if ($Mature) { $buildArguments.Mature = $true }
if ($Diagnostics) { $buildArguments.Diagnostics = $true }
if ($SanitizedZip) { $buildArguments.SanitizedZip = $true }

try
{
	& (Join-Path $PSScriptRoot "Build-LocalPlay.ps1") @buildArguments
	if ($LASTEXITCODE -and ($LASTEXITCODE -ne 0)) { throw "Build-LocalPlay завершился с кодом $LASTEXITCODE." }
	$databaseMarker = Join-Path $runtimeRoot "DB_CONFIG_REQUIRED.txt"
	if (-not (Test-Path -LiteralPath $databaseMarker -PathType Leaf))
	{
		throw "Новый runtime не остался fail-closed перед provisioning."
	}

	Write-Host "Создание строго новой БД '$DatabaseName' атомарной canonical CREATE DATABASE операцией."
	$createResult = Invoke-DatabaseGuard "create-fresh"
	if ($createResult -notmatch "^CREATED ") { throw "Fresh database creation не вернул ожидаемое подтверждение." }

	$runtimeInstallerRoot = Join-Path $runtimeRoot "db_installer"
	Set-IniValue -Path (Join-Path $runtimeInstallerRoot "config\Interface.ini") -Key "EnableGUI" -Value "False"
	$installerInput = @("2", "1", $loginTarget.Host, [string] $loginTarget.Port, $script:databaseLogin, $script:databasePassword, $DatabaseName, "3")
	$installerResult = Invoke-JavaProcess -WorkingDirectory $runtimeInstallerRoot -Arguments @("-jar", "DatabaseInstaller.jar") -InputLines $installerInput
	if (($installerResult.ExitCode -ne 0) -or ($installerResult.StdOut -match "(?m)^\[ERROR\]") -or ($installerResult.StdErr -match "(?i)exception|error") -or ($installerResult.StdOut -notmatch "Installation completed successfully"))
	{
		throw "Canonical DatabaseInstaller не завершил чистую установку login+game schema."
	}
	Write-Host "Canonical DatabaseInstaller: login+game PASS."

	$sqlRoot = Join-Path $runtimeInstallerRoot "sql"
	$schemaResult = Invoke-DatabaseGuard "verify-schema-empty" $sqlRoot
	if ($schemaResult -notmatch "^SCHEMA_EMPTY_PASS ") { throw "Fresh schema verification не вернул ожидаемое подтверждение." }
	Write-Host $schemaResult

	$runtimeLoginDatabase = Join-Path $runtimeRoot "login\config\Database.ini"
	$runtimeGameDatabase = Join-Path $runtimeRoot "game\config\Database.ini"
	Set-IniValue $runtimeLoginDatabase "URL" (Set-JdbcDatabase $loginUrl $DatabaseName)
	Set-IniValue $runtimeLoginDatabase "Login" $script:databaseLogin
	Set-IniValue $runtimeLoginDatabase "Password" $script:databasePassword
	Set-IniValue $runtimeGameDatabase "URL" (Set-JdbcDatabase $gameUrl $DatabaseName)
	Set-IniValue $runtimeGameDatabase "Login" $script:databaseLogin
	Set-IniValue $runtimeGameDatabase "Password" $script:databasePassword
	Set-IniValue -Path (Join-Path $runtimeRoot "login\config\Interface.ini") -Key "EnableGUI" -Value "False"
	Set-IniValue -Path (Join-Path $runtimeRoot "game\config\Interface.ini") -Key "EnableGUI" -Value "False"

	$registerWorkingDirectory = Join-Path $runtimeRoot "login"
	$generatedHexid = Join-Path $registerWorkingDirectory "hexid.txt"
	$runtimeHexid = Join-Path $runtimeRoot "game\config\hexid.txt"
	Remove-Item -LiteralPath $generatedHexid -Force -ErrorAction SilentlyContinue
	Remove-Item -LiteralPath $runtimeHexid -Force -ErrorAction SilentlyContinue
	$registerResult = Invoke-JavaProcess -WorkingDirectory $registerWorkingDirectory -Arguments @("-cp", "..\libs\*", "org.l2jmobius.tools.GameServerRegister") -InputLines @("3", "2", "y", "2", "1", "5")
	$registerFailed = ($registerResult.ExitCode -ne 0) -or ($registerResult.StdOut -notmatch "Game Server ID 2 .* successfully removed") -or ($registerResult.StdOut -notmatch "Game server with ID: 1 .* successfully registered") -or ($registerResult.StdOut -match "(?i)unexpected error|error removing|already registered|no server found")
	$knownConsoleRefreshBug = $registerResult.StdErr -match "NullPointerException" -and $registerResult.StdErr -match "GameServerRegister.*serversList"
	$unexpectedRegisterError = ($registerResult.StdErr -match "(?i)SEVERE|Exception") -and (-not $knownConsoleRefreshBug)
	if ($registerFailed -or $unexpectedRegisterError -or (-not (Test-Path -LiteralPath $generatedHexid -PathType Leaf)))
	{
		throw "Canonical GameServerRegister не создал проверяемую fresh identity."
	}
	Move-Item -LiteralPath $generatedHexid -Destination $runtimeHexid -Force
	$registeredResult = Invoke-DatabaseGuard "verify-registered" $runtimeHexid
	if ($registeredResult -notmatch "^REGISTERED_PASS ") { throw "GameServer identity verification не вернул ожидаемое подтверждение." }
	Write-Host $registeredResult

	$manifestPath = Join-Path $runtimeRoot "local-play.json"
	$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
	$manifest.databaseConfig = "FRESH_LOCAL_PROVISIONED"
	$manifest | Add-Member -NotePropertyName databaseName -NotePropertyValue $DatabaseName -Force
	$manifest | Add-Member -NotePropertyName databaseHost -NotePropertyValue $loginTarget.Host -Force
	$manifest | Add-Member -NotePropertyName databasePort -NotePropertyValue $loginTarget.Port -Force
	$manifest | Add-Member -NotePropertyName gameServerId -NotePropertyValue 1 -Force
	$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
	Remove-Item -LiteralPath $databaseMarker -Force
	Remove-Item -LiteralPath (Join-Path $runtimeRoot "FRESH_LOCAL_PROVISIONING_FAILED.txt") -Force -ErrorAction SilentlyContinue

	& (Join-Path $runtimeRoot "Check-LocalPlay.ps1")
	if ($LASTEXITCODE -and ($LASTEXITCODE -ne 0)) { throw "Check-LocalPlay завершился с кодом $LASTEXITCODE." }
	Assert-SourceHashesUnchanged $sourceHashesBefore

	Write-Host "FRESH_LOCAL_PROVISIONED: database=$DatabaseName serverId=1."
	Write-Host "Исходные Database.ini, hexid и canonical SQL остались byte-identical."
	Write-Host "Runtime готов. Аккаунт '$Account' будет создан только при первом ручном входе."
}
catch
{
	Set-ProvisioningFailure $runtimeRoot
	Assert-SourceHashesUnchanged $sourceHashesBefore
	throw
}
finally
{
	$script:databasePassword = $null
	$gamePassword = $null
}
