[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext -RequireEnabled
$session = Get-PilotSession $context
$requestId = [guid]::NewGuid().ToString('D')
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
