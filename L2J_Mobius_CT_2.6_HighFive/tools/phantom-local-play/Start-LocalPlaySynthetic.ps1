[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-fA-F-]{36}$')][string] $RunId)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')
$context = Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $RunId
Protect-PilotDirectory $context.SyntheticRoot
Initialize-PilotMailbox $context
$lock = Enter-PilotOperatorLock $context
try
{
	Write-PilotHeartbeat $context $RunId $RunId
	$expiry = [DateTimeOffset]::UtcNow.AddSeconds(30).ToUnixTimeMilliseconds()
	$control = "version=1`ncommand=START`nrunId=$RunId`nruntimeId=$($context.RuntimeId)`npid=$($context.Pid)`nstartTimeUtcTicks=$($context.StartTimeUtcTicks)`nexpiresUtcMillis=$expiry`n"
	Write-PilotAtomicText (Join-Path $context.SyntheticRoot 'control.properties') $control
	for ($i=0; $i -lt 150; $i++)
	{
		Start-Sleep -Milliseconds 200
		$path = Join-Path $context.SyntheticRoot 'session.properties'
		if (-not (Test-Path -LiteralPath $path)) { continue }
		$state = Read-PilotProperties $path
		if ($state.sessionId -cne $RunId) { continue }
		if ($state.state -cne 'RUNNING') { throw "SYNTHETIC_START_REJECTED:$($state.reason)" }
		Get-PilotSession $context | Out-Null
		[pscustomobject]@{state='RUNNING'; runId=$RunId; gamePid=$context.Pid; actorObjectId=$state.objectId} | ConvertTo-Json -Compress
		return
	}
	throw 'SYNTHETIC_START_TIMEOUT'
}
finally { $lock.Dispose() }
