[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[ValidateSet('STATUS', 'CAPABILITIES', 'SNAPSHOT_PHANTOMS', 'SELECT_VISIBLE_PHANTOM_TRACE', 'SNAPSHOT_SELECTED_PHANTOM_TRACE', 'REPLAY_SELECTED_PHANTOM_TRACE', 'SNAPSHOT_TARGETS', 'TELEPORT_SELF', 'MOVE_SELF', 'STOP_MOVE', 'SIT', 'STAND', 'SELECT_TARGET', 'SAY', 'PARTY_INVITE', 'PARTY_RESPOND', 'PARTY_LEAVE', 'ATTACK_NPC', 'CAST_LEARNED_SKILL')]
	[string] $Operation,
	[hashtable] $Arguments = @{},
	[ValidateRange(1, 120)][int] $TimeoutSeconds = 30,
	[ValidatePattern('^[0-9a-fA-F-]{36}$')][string] $RunId
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext -RequireEnabled
$lock = Enter-PilotOperatorLock $context
$effectiveRunId = if ($RunId) { $RunId } else { [guid]::NewGuid().ToString('D') }
try
{
	$session = Get-PilotSession $context
	if ($session.ContainsKey('stoppedRunId') -and ($session.stoppedRunId -ceq $effectiveRunId)) { throw 'CANCELLED: pilot run уже остановлен.' }
	Write-PilotHeartbeat $context ([string] $session.sessionId) $effectiveRunId
	$sequence = [long] $session.nextSequence
	$requestId = [guid]::NewGuid().ToString('D')
	$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
	$memory = New-Object IO.MemoryStream
	$settings = New-Object Xml.XmlWriterSettings
	$settings.Encoding = New-Object Text.UTF8Encoding($false)
	$settings.Indent = $false
	$writer = [Xml.XmlWriter]::Create($memory, $settings)
	try
	{
		$writer.WriteStartDocument()
		$writer.WriteStartElement('pilotRequest')
		$writer.WriteAttributeString('version', '1')
		$writer.WriteAttributeString('requestId', $requestId)
		$writer.WriteAttributeString('sessionId', [string] $session.sessionId)
		$writer.WriteAttributeString('runId', $effectiveRunId)
		$writer.WriteAttributeString('sequence', [string] $sequence)
		$writer.WriteAttributeString('deadlineUtc', $deadline.UtcDateTime.ToString('o'))
		$writer.WriteAttributeString('operation', $Operation)
		if ($Arguments.Count -gt 16) { throw 'Слишком много аргументов pilot action.' }
		foreach ($name in @($Arguments.Keys | Sort-Object))
		{
			if ([string] $name -cnotmatch '^[a-zA-Z][a-zA-Z0-9]{0,31}$') { throw 'Некорректное имя pilot action аргумента.' }
			$value = [string] $Arguments[$name]
			if ($value.Length -gt 4096) { throw 'Слишком длинный pilot action аргумент.' }
			$writer.WriteStartElement('arg')
			$writer.WriteAttributeString('name', [string] $name)
			$writer.WriteAttributeString('value', $value)
			$writer.WriteEndElement()
		}
		$writer.WriteEndElement()
		$writer.WriteEndDocument()
		$writer.Flush()
	}
	finally { $writer.Dispose() }
	$bytes = $memory.ToArray()
	$memory.Dispose()
	$inbox = Join-Path $context.PilotRoot ("inbox\$requestId.xml")
	$resultPath = Join-Path $context.PilotRoot ("results\$requestId.xml")
	Write-PilotAtomicBytes $inbox $bytes
	$lastHeartbeat = [DateTime]::UtcNow
	$lastStopCheck = [DateTime]::UtcNow
	$pollDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds + 2)
	while ([DateTime]::UtcNow -lt $pollDeadline)
	{
		if (([DateTime]::UtcNow - $lastHeartbeat).TotalSeconds -ge 10)
		{
			Write-PilotHeartbeat $context ([string] $session.sessionId) $effectiveRunId
			$lastHeartbeat = [DateTime]::UtcNow
		}
		if (([DateTime]::UtcNow - $lastStopCheck).TotalSeconds -ge 1)
		{
			$current = Read-PilotProperties (Join-Path $context.PilotRoot 'session.properties')
			if ($current.ContainsKey('stoppedRunId') -and ($current.stoppedRunId -ceq $effectiveRunId)) { throw 'CANCELLED: pilot run остановлен.' }
			$lastStopCheck = [DateTime]::UtcNow
		}
		if (Test-Path -LiteralPath $resultPath)
		{
			$result = Read-PilotResult $resultPath
			if (($result.requestId -cne $requestId) -or ($result.sessionId -cne $session.sessionId) -or ([long] $result.sequence -ne $sequence) -or ($result.operation -cne $Operation)) { throw 'Pilot result identity mismatch.' }
			$result | ConvertTo-Json -Depth 8 -Compress
			return
		}
		Start-Sleep -Milliseconds 100
	}
	throw "UNCERTAIN: результат $requestId не опубликован до deadline; не повторяйте action автоматически."
}
finally
{
	try
	{
		if (-not $RunId)
		{
			$heartbeatPath = Join-Path $context.PilotRoot 'heartbeat.properties'
			if (Test-Path -LiteralPath $heartbeatPath)
			{
				$heartbeat = Read-PilotProperties $heartbeatPath
				if ($heartbeat.runId -ceq $effectiveRunId) { Remove-Item -LiteralPath $heartbeatPath -Force }
			}
		}
	}
	finally { $lock.Dispose() }
}
