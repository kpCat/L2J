[CmdletBinding()]
param([ValidateSet('RealClient', 'Synthetic')][string] $ActorMode = 'RealClient', [string] $RunId = '')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext -RequireEnabled -ActorMode $ActorMode
$session = Get-PilotSession $context
$requestId = [guid]::NewGuid().ToString('D')
if ($ActorMode -ceq 'Synthetic')
{
	if ($RunId -and ($RunId -cne $session.sessionId)) { throw 'SYNTHETIC_STOP_RUN_ID_MISMATCH' }
	$record = "version=1`ncommand=STOP`nrunId=$($session.sessionId)`nruntimeId=$($context.RuntimeId)`npid=$($context.Pid)`nstartTimeUtcTicks=$($context.StartTimeUtcTicks)`n"
	Write-PilotAtomicText (Join-Path $context.SyntheticRoot 'control.properties') $record -Replace
	for ($i=0; $i -lt 50; $i++)
	{
		Start-Sleep -Milliseconds 200
		$current = Get-PilotSession $context
		if ($current.state -ceq 'STOPPED') { [pscustomobject]@{state='STOPPED'; requestId=$requestId; gamePid=$context.Pid; syntheticCleanup='PASS'} | ConvertTo-Json -Compress; return }
		if ($current.state -ceq 'CLEANUP_FAILED') { throw "SYNTHETIC_CLEANUP_FAILED:$($current.reason)" }
	}
	throw 'SYNTHETIC_STOP_NOT_CONFIRMED'
}
$record = "version=1`nsessionId=$($session.sessionId)`nrequestId=$requestId`n"
Write-PilotAtomicText (Join-Path $context.PilotRoot 'stop.properties') $record -Replace
for ($i = 0; $i -lt 25; $i++)
{
	Start-Sleep -Milliseconds 200
	$current = Get-PilotSession $context
	if ($current.state -cne 'RUNNING')
	{
		[pscustomobject]@{ state = 'STOPPED'; requestId = $requestId; gamePid = $context.Pid } | ConvertTo-Json -Compress
		return
	}
}
[pscustomobject]@{ state = 'STOP_REQUESTED'; requestId = $requestId; gamePid = $context.Pid } | ConvertTo-Json -Compress
