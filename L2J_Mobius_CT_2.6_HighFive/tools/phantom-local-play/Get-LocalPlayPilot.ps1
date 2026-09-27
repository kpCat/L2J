[CmdletBinding()]
param([ValidatePattern('^[0-9a-fA-F-]{36}$')][string] $RequestId)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext
if ($RequestId)
{
	$path = Join-Path $context.PilotRoot ("results\$RequestId.xml")
	if (-not (Test-Path -LiteralPath $path)) { throw "Результат $RequestId ещё не опубликован." }
	Read-PilotResult $path | ConvertTo-Json -Depth 8 -Compress
	exit 0
}
$state = 'OFF'
$expiry = $null
$actor = $null
$sessionPath = Join-Path $context.PilotRoot 'session.properties'
if (Test-Path -LiteralPath $sessionPath)
{
	try
	{
		$session = Get-PilotSession $context
		$state = if ($session.state -cin @('ARMED_IDLE', 'RUNNING')) { [string] $session.state } else { 'UNKNOWN' }
		$expiry = [DateTimeOffset]::FromUnixTimeMilliseconds([long] $session.expiresUtcMillis).UtcDateTime.ToString('o')
		$actor = [int] $session.objectId
	}
	catch { $state = 'EXPIRED_OR_STALE' }
}
elseif (Test-Path -LiteralPath (Join-Path $context.PilotRoot 'arm.properties')) { $state = 'WAITING_ARM' }
[pscustomobject]@{ state = $state; runActive = ($state -ceq 'RUNNING'); gamePid = $context.Pid; actorObjectId = $actor; expiresUtc = $expiry } | ConvertTo-Json -Compress
