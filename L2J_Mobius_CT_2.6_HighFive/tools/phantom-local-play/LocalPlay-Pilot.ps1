. (Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1')

function Get-PilotRuntimeRoot
{
	if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'local-play.json')) { return [IO.Path]::GetFullPath($PSScriptRoot) }
	$moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
	return [IO.Path]::GetFullPath((Join-Path $moduleRoot 'artifacts\local-play\runtime'))
}

function Assert-PilotNoReparse([string] $Path)
{
	$full = [IO.Path]::GetFullPath($Path)
	$current = [IO.Path]::GetPathRoot($full)
	foreach ($part in $full.Substring($current.Length).Split([IO.Path]::DirectorySeparatorChar))
	{
		if ($part.Length -eq 0) { continue }
		$current = Join-Path $current $part
		if (-not (Test-Path -LiteralPath $current)) { continue }
		$item = Get-Item -LiteralPath $current -Force
		if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point в private runtime: $current" }
	}
}

function Get-PilotIniValue([string] $Path, [string] $Key)
{
	$text = [IO.File]::ReadAllText($Path)
	$match = [regex]::Match($text, '(?m)^[ \t]*' + [regex]::Escape($Key) + '[ \t]*=[ \t]*([^\r\n]*)\r?$')
	if (-not $match.Success) { throw "Не найден ключ '$Key' в '$Path'." }
	return $match.Groups[1].Value.Trim()
}

function Get-PilotContext([switch] $RequireEnabled)
{
	$runtimeRoot = Get-PilotRuntimeRoot
	Assert-PilotNoReparse $runtimeRoot
	$manifestPath = Join-Path $runtimeRoot 'local-play.json'
	if (-not (Test-Path -LiteralPath $manifestPath)) { throw 'Private LocalPlay runtime не найден.' }
	$null = [IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
	$configPath = Join-Path $runtimeRoot 'game\config\Custom\LocalPlayPilot.ini'
	if (-not (Test-Path -LiteralPath $configPath)) { throw 'Private LocalPlayPilot.ini не найден.' }
	if ($RequireEnabled -and ((Get-PilotIniValue $configPath 'EnableLocalPlayPilot') -cne 'True')) { throw 'LocalPlayPilot выключен в private runtime.' }
	$gamePort = [int] (Get-PilotIniValue (Join-Path $runtimeRoot 'game\config\Server.ini') 'GameserverPort')
	$state = Get-LocalPlayRoleState $runtimeRoot 'GameServer' 'GameServer.jar' @($gamePort)
	if (($state.state -cne 'RUNNING') -or (-not $state.recordVerified)) { throw "GameServer не имеет подтверждённого LocalPlay ownership: $($state.state)." }
	$pilotRoot = Join-Path $runtimeRoot 'playtest-pilot'
	Assert-PilotNoReparse $pilotRoot
	return [pscustomobject]@{ RuntimeRoot = $runtimeRoot; PilotRoot = $pilotRoot; RuntimeId = (Get-LocalPlayRuntimeId $runtimeRoot); Pid = [int] $state.pid; StartTimeUtcTicks = [long] $state.startTimeUtcTicks }
}

function Protect-PilotDirectory([string] $Path)
{
	Assert-PilotNoReparse $Path
	if (-not (Test-Path -LiteralPath $Path)) { $null = [IO.Directory]::CreateDirectory($Path) }
	$acl = Get-Acl -LiteralPath $Path
	$acl.SetAccessRuleProtection($true, $false)
	foreach ($rule in @($acl.Access)) { $acl.RemoveAccessRuleSpecific($rule) | Out-Null }
	$inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
	$propagation = [Security.AccessControl.PropagationFlags]::None
	$type = [Security.AccessControl.AccessControlType]::Allow
	foreach ($sidText in @(([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)))
	{
		$sid = New-Object Security.Principal.SecurityIdentifier($sidText)
		$rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, [Security.AccessControl.FileSystemRights]::FullControl, $inheritance, $propagation, $type)
		$acl.AddAccessRule($rule)
	}
	Set-Acl -LiteralPath $Path -AclObject $acl
	$verified = Get-Acl -LiteralPath $Path
	if (-not $verified.AreAccessRulesProtected) { throw "Private ACL не применён: $Path" }
	foreach ($rule in $verified.Access)
	{
		$sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
		if (@(([Security.Principal.WindowsIdentity]::GetCurrent().User.Value)) -notcontains $sid) { throw "Небезопасный ACL: $Path" }
	}
}

function Initialize-PilotMailbox($Context)
{
	Protect-PilotDirectory $Context.PilotRoot
	foreach ($name in @('inbox', 'processing', 'results', 'journal')) { Protect-PilotDirectory (Join-Path $Context.PilotRoot $name) }
}

function Assert-PilotPrivateFile([string] $Path)
{
	Assert-PilotNoReparse $Path
	$allowed = @(([Security.Principal.WindowsIdentity]::GetCurrent().User.Value))
	foreach ($rule in (Get-Acl -LiteralPath $Path).Access)
	{
		$sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
		if ($allowed -notcontains $sid) { throw "Небезопасный ACL private mailbox file: $Path" }
	}
}

function Write-PilotAtomicBytes([string] $Path, [byte[]] $Bytes, [switch] $Replace)
{
	$parent = [IO.Path]::GetDirectoryName($Path)
	Assert-PilotNoReparse $parent
	if ($Bytes.Length -gt 65536) { throw 'Private mailbox record превышает 64 KiB.' }
	$temp = Join-Path $parent (([guid]::NewGuid().ToString('D')) + '.tmp')
	$backup = Join-Path $parent (([guid]::NewGuid().ToString('D')) + '.bak')
	try
	{
		[IO.File]::WriteAllBytes($temp, $Bytes)
		if ([IO.File]::Exists($Path))
		{
			if (-not $Replace) { throw "Private mailbox record уже существует: $Path" }
			Assert-PilotPrivateFile $Path
			[IO.File]::Replace($temp, $Path, $backup)
		}
		else { [IO.File]::Move($temp, $Path) }
	}
	finally
	{
		if ([IO.File]::Exists($temp)) { [IO.File]::Delete($temp) }
		if ([IO.File]::Exists($backup)) { [IO.File]::Delete($backup) }
	}
}

function Write-PilotAtomicText([string] $Path, [string] $Text, [switch] $Replace)
{
	$utf8 = New-Object Text.UTF8Encoding($false)
	Write-PilotAtomicBytes $Path ($utf8.GetBytes($Text)) -Replace:$Replace
}

function Read-PilotProperties([string] $Path)
{
	Assert-PilotNoReparse $Path
	if (-not (Test-Path -LiteralPath $Path)) { throw "Private mailbox record отсутствует: $Path" }
	Assert-PilotPrivateFile $Path
	if ((Get-Item -LiteralPath $Path).Length -gt 65536) { throw 'Private mailbox record превышает 64 KiB.' }
	$map = @{}
	foreach ($line in [IO.File]::ReadAllLines($Path, (New-Object Text.UTF8Encoding($false, $true))))
	{
		if (($line.Length -eq 0) -or $line.StartsWith('#') -or $line.StartsWith('!')) { continue }
		$index = $line.IndexOf('=')
		if ($index -le 0) { throw "Некорректный private record: $Path" }
		$key = $line.Substring(0, $index)
		if ($key -cnotmatch '^[A-Za-z][A-Za-z0-9]*$' -or $map.ContainsKey($key)) { throw "Некорректный private record: $Path" }
		$map[$key] = $line.Substring($index + 1)
	}
	return $map
}

function Get-PilotSession($Context)
{
	$session = Read-PilotProperties (Join-Path $Context.PilotRoot 'session.properties')
	if (($session.version -cne '1') -or ($session.sessionId -cnotmatch '^[0-9a-fA-F-]{36}$') -or ([int] $session.pid -ne $Context.Pid) -or ([long] $session.startTimeUtcTicks -ne $Context.StartTimeUtcTicks)) { throw 'Pilot session не соответствует текущему owned GameServer.' }
	if ([long] $session.expiresUtcMillis -le [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) { throw 'Pilot session истекла.' }
	if ([long] $session.nextSequence -lt 1) { throw 'Некорректная pilot sequence.' }
	return $session
}

function Write-PilotHeartbeat($Context, [string] $SessionId, [string] $RunId)
{
	if (($SessionId -cnotmatch '^[0-9a-fA-F-]{36}$') -or ($RunId -cnotmatch '^[0-9a-fA-F-]{36}$')) { throw 'Некорректная pilot heartbeat identity.' }
	$millis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
	$record = "version=1`nsessionId=$SessionId`nupdatedUtcMillis=$millis`nrunId=$RunId`n"
	Write-PilotAtomicText (Join-Path $Context.PilotRoot 'heartbeat.properties') $record -Replace
}

function Enter-PilotOperatorLock($Context, [int] $TimeoutSeconds = 5)
{
	$path = Join-Path $Context.PilotRoot 'operator.lock'
	$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
	do
	{
		try { return New-Object IO.FileStream($path, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
		catch [IO.IOException]
		{
			if ([DateTime]::UtcNow -ge $deadline) { throw 'Другой pilot operator уже выполняет запрос.' }
			Start-Sleep -Milliseconds 100
		}
	}
	while ($true)
}

function Read-PilotResult([string] $Path)
{
	Assert-PilotNoReparse $Path
	Assert-PilotPrivateFile $Path
	if ((Get-Item -LiteralPath $Path).Length -gt 65536) { throw 'Pilot result превышает 64 KiB.' }
	$settings = New-Object Xml.XmlReaderSettings
	$settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
	$settings.XmlResolver = $null
	$settings.MaxCharactersInDocument = 65536
	$reader = [Xml.XmlReader]::Create($Path, $settings)
	try
	{
		$document = New-Object Xml.XmlDocument
		$document.XmlResolver = $null
		$document.Load($reader)
	}
	finally { $reader.Dispose() }
	if ($document.DocumentElement.LocalName -cne 'pilotResult' -or $document.DocumentElement.NamespaceURI -ne '') { throw 'Некорректный pilotResult.' }
	$result = [ordered]@{}
	foreach ($attribute in $document.DocumentElement.Attributes) { $result[$attribute.Name] = $attribute.Value }
	foreach ($child in $document.DocumentElement.ChildNodes)
	{
		if ($child.NodeType -ne [Xml.XmlNodeType]::Element) { continue }
		$fields = [ordered]@{}
		foreach ($attribute in $child.Attributes) { $fields[$attribute.Name] = $attribute.Value }
		if ($child.InnerText) { $fields['text'] = $child.InnerText }
		$result[$child.LocalName] = [pscustomobject] $fields
	}
	return [pscustomobject] $result
}
