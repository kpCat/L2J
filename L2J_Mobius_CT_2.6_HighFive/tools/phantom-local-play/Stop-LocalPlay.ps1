[CmdletBinding()]
param([int] $GraceSeconds = 15)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RuntimeRoot
{
	if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "local-play.json")) { return $PSScriptRoot }
	$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
	return (Join-Path $moduleRoot "artifacts\local-play\runtime")
}

function Stop-OwnedProcess
{
	param([string] $RecordPath)
	if (-not (Test-Path -LiteralPath $RecordPath)) { return }
	$record = Get-Content -LiteralPath $RecordPath -Raw | ConvertFrom-Json
	$removeRecord = $false
	try
	{
		$process = Get-Process -Id ([int] $record.pid) -ErrorAction Stop
		if ($process.StartTime.ToUniversalTime().ToString("o") -ne [string] $record.startTimeUtc)
		{
			throw "PID $($record.pid) был повторно использован; чужой процесс не остановлен."
		}
		if ($process.CloseMainWindow())
		{
			$null = $process.WaitForExit($GraceSeconds * 1000)
		}
		if (-not $process.HasExited)
		{
			Stop-Process -Id $process.Id
			$null = $process.WaitForExit(10000)
		}
		if (-not $process.HasExited)
		{
			Stop-Process -Id $process.Id -Force
			$null = $process.WaitForExit(10000)
		}
		if (-not $process.HasExited) { throw "Не удалось остановить подтверждённый PID $($process.Id). PID record сохранён." }
		$removeRecord = $true
		Write-Host "$($record.role) PID=$($record.pid) остановлен."
	}
	catch [Microsoft.PowerShell.Commands.ProcessCommandException]
	{
		if (Get-Process -Id ([int] $record.pid) -ErrorAction SilentlyContinue)
		{
			throw "Нет прав остановить подтверждённый $($record.role) PID=$($record.pid). PID record сохранён."
		}
		$removeRecord = $true
		Write-Host "$($record.role) PID=$($record.pid) уже не запущен."
	}
	finally
	{
		if ($removeRecord)
		{
			Remove-Item -LiteralPath $RecordPath -Force -ErrorAction SilentlyContinue
		}
	}
}

$runtimeRoot = Get-RuntimeRoot
$pidRoot = Join-Path $runtimeRoot "local-play\pids"
Stop-OwnedProcess (Join-Path $pidRoot "GameServer.json")
Stop-OwnedProcess (Join-Path $pidRoot "LoginServer.json")
Write-Host "Остановка завершена. Глобальный taskkill не использовался."
