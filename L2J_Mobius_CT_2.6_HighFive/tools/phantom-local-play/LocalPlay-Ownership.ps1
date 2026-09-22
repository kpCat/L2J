function Get-LocalPlayRuntimeId([string] $RuntimeRoot)
{
	$path = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\').ToLowerInvariant()
	$bytes = [Text.Encoding]::UTF8.GetBytes($path)
	$hash = [Security.Cryptography.SHA256]::Create()
	try { return ([BitConverter]::ToString($hash.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
	finally { $hash.Dispose() }
}

function Read-LocalPlayRecord([string] $Path)
{
	if (-not (Test-Path -LiteralPath $Path)) { return $null }
	try { return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
	catch { return [pscustomobject]@{ format = 0 } }
}

function Save-LocalPlayRecord([string] $Path, [string] $Role, [object] $Process, [string] $Jar, [string] $RuntimeId)
{
	$record = [ordered]@{ format = 2; role = $Role; pid = [int] $Process.pid; startTimeUtcTicks = [long] $Process.startTimeUtcTicks; jar = $Jar; runtime = $RuntimeId }
	$temporary = "$Path.tmp"
	$record | ConvertTo-Json -Compress | Set-Content -LiteralPath $temporary -Encoding UTF8
	Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Resolve-LocalPlayOwnership([string] $Role, [string] $RuntimeId, [string] $Jar, [int[]] $Ports, $Record, [object[]] $Processes, [hashtable] $PortOwners)
{
	$matches = @($Processes | Where-Object { ($_.role -ceq $Role) -and ($_.runtime -ceq $RuntimeId) -and ($_.jar -ceq $Jar) })
	$stale = $null -ne $Record
	if ($matches.Count -gt 1) { return [pscustomobject]@{ state = 'INCONSISTENT'; pid = 0; staleRecord = $stale; recordVerified = $false; reason = 'multiple marked JVMs' } }
	if ($matches.Count -eq 1)
	{
		$process = $matches[0]
		$stale = ($null -ne $Record) -and (($null -eq $Record.PSObject.Properties['format']) -or ([int] $Record.format -ne 2))
		if (($null -ne $Record) -and (-not $stale))
		{
			$stale = ([string] $Record.role -cne $Role) -or ([int] $Record.pid -ne [int] $process.pid) -or ([long] $Record.startTimeUtcTicks -ne [long] $process.startTimeUtcTicks) -or ([string] $Record.jar -cne $Jar) -or ([string] $Record.runtime -cne $RuntimeId)
		}
		$recordVerified = ($null -ne $Record) -and (-not $stale)
		foreach ($port in $Ports)
		{
			if ($PortOwners.ContainsKey($port) -and ([int] $PortOwners[$port] -ne [int] $process.pid)) { return [pscustomobject]@{ state = 'FOREIGN_PORT_OWNER'; pid = [int] $process.pid; staleRecord = $stale; recordVerified = $recordVerified; reason = "port $port owned by $($PortOwners[$port])" } }
		}
		$allOwned = @($Ports | Where-Object { (-not $PortOwners.ContainsKey($_)) -or ([int] $PortOwners[$_] -ne [int] $process.pid) }).Count -eq 0
		if ($allOwned) { return [pscustomobject]@{ state = 'RUNNING'; pid = [int] $process.pid; startTimeUtcTicks = [long] $process.startTimeUtcTicks; staleRecord = $stale; recordVerified = $recordVerified; reason = '' } }
		return [pscustomobject]@{ state = 'INCONSISTENT'; pid = [int] $process.pid; startTimeUtcTicks = [long] $process.startTimeUtcTicks; staleRecord = $stale; recordVerified = $recordVerified; reason = 'marked JVM does not own all expected ports' }
	}
	foreach ($port in $Ports)
	{
		if ($PortOwners.ContainsKey($port)) { return [pscustomobject]@{ state = 'FOREIGN_PORT_OWNER'; pid = 0; staleRecord = $stale; recordVerified = $false; reason = "port $port owned by $($PortOwners[$port])" } }
	}
	if ($stale) { return [pscustomobject]@{ state = 'STALE_RECORD'; pid = 0; staleRecord = $true; recordVerified = $false; reason = 'record has no marked owner' } }
	return [pscustomobject]@{ state = 'STOPPED'; pid = 0; staleRecord = $false; recordVerified = $false; reason = '' }
}

function Get-LocalPlayProcesses([string] $RuntimeId)
{
	$rows = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop)
	foreach ($row in $rows)
	{
		$command = [string] $row.CommandLine
		if ($command -notmatch ('(?:^|\s)-Dphantom\.localplay\.runtime=' + [regex]::Escape($RuntimeId) + '(?:\s|$)')) { continue }
		$roleMatch = [regex]::Match($command, '(?:^|\s)-Dphantom\.localplay\.role=(LoginServer|GameServer)(?:\s|$)')
		$jarMatch = [regex]::Match($command, '(?:^|\s)-jar\s+"?(?:[^\s"]*[\\/])?(LoginServer|GameServer)\.jar"?(?:\s|$)')
		if (-not $roleMatch.Success -or -not $jarMatch.Success) { continue }
		try { $started = (Get-Process -Id ([int] $row.ProcessId) -ErrorAction Stop).StartTime.ToUniversalTime().Ticks }
		catch { continue }
		[pscustomobject]@{ pid = [int] $row.ProcessId; startTimeUtcTicks = [long] $started; role = $roleMatch.Groups[1].Value; jar = $jarMatch.Groups[1].Value + '.jar'; runtime = $RuntimeId }
	}
}

function Get-LocalPlayPortOwners([int[]] $Ports)
{
	$owners = @{}
	foreach ($port in $Ports)
	{
		$ids = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
		if ($ids.Count -eq 1) { $owners[$port] = [int] $ids[0] }
		elseif ($ids.Count -gt 1) { $owners[$port] = -1 }
	}
	return $owners
}

function Get-LocalPlayRoleState([string] $RuntimeRoot, [string] $Role, [string] $Jar, [int[]] $Ports)
{
	$id = Get-LocalPlayRuntimeId $RuntimeRoot
	$record = Read-LocalPlayRecord (Join-Path $RuntimeRoot "local-play\pids\$Role.json")
	$processes = @(Get-LocalPlayProcesses $id)
	$owners = Get-LocalPlayPortOwners $Ports
	return Resolve-LocalPlayOwnership $Role $id $Jar $Ports $record $processes $owners
}
