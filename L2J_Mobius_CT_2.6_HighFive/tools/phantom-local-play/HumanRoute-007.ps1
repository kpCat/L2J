[CmdletBinding(DefaultParameterSetName='Operator')]
param(
    [Parameter(Mandatory, ParameterSetName='Operator')]
    [ValidateSet('status', 'arm', 'arrived', 'skip', 'stop', 'next', 'renew', 'reload')][string]$Action,
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory, ParameterSetName='Operator')][ValidateRange(1, 2147483647)][int]$ServerProcessId,
    [Parameter(ParameterSetName='Operator')][ValidatePattern('^(?:-|[a-z0-9][a-z0-9-]{0,47})$')][string]$Scene = '-',
    [Parameter(Mandatory, ParameterSetName='Initialize')][switch]$Initialize,
    [Parameter(ParameterSetName='Operator')][ValidatePattern('^[A-Za-z0-9_]{1,32}$')][string]$TestAccount,
    [Parameter(ParameterSetName='Operator')][ValidateRange(1, 2147483647)][int]$ObjectId,
    [Parameter(ParameterSetName='Operator')][string]$ScenarioFile,
    [Parameter(ParameterSetName='Operator')][string]$FailureSnapshot,
    [Parameter(ParameterSetName='Operator')][ValidateRange(5, 60)][int]$PermitMinutes = 45
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
$encoding = [Text.UTF8Encoding]::new($false, $true)

function Assert-PlainPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    $cursor = [IO.Path]::GetPathRoot($full)
    foreach ($part in $full.Substring($cursor.Length).Split('\', [StringSplitOptions]::RemoveEmptyEntries)) {
        $cursor = Join-Path $cursor $part
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Human Route: linked paths are refused.'
        }
    }
}

function Assert-PrivatePath([string]$Path) {
    Assert-PlainPath $Path
    $acl = Get-Acl -LiteralPath $Path
    if ($acl.GetOwner([Security.Principal.SecurityIdentifier]) -ne $owner) { throw 'Human Route: unexpected file owner.' }
    $allowed = $false
    foreach ($rule in $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow) {
            if ($rule.IdentityReference -ne $owner) { throw 'Human Route: non-owner ACL entry.' }
            $allowed = $true
        }
    }
    if (!$allowed) { throw 'Human Route: owner has no access rule.' }
}

function Write-AtomicRequest([string]$Path, [string]$Content) {
    $pending = Join-Path $Root ('.request-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($pending, $Content, $encoding)
        Assert-PrivatePath $pending
        [IO.File]::Move($pending, $Path)
    }
    finally {
        if ([IO.File]::Exists($pending)) { [IO.File]::Delete($pending) }
    }
}

function Read-Record([string]$Path) {
    Assert-PrivatePath $Path
    $reader = [IO.FileStream]::new($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    try {
        $bytes = [byte[]]::new(4097)
        $count = 0
        while ($count -lt $bytes.Length) {
            $read = $reader.Read($bytes, $count, $bytes.Length - $count)
            if ($read -eq 0) { break }
            $count += $read
        }
        if ($count -eq 0 -or $count -gt 4096) { throw 'Human Route: invalid receipt size.' }
        $content = $encoding.GetString($bytes, 0, $count)
    }
    finally { $reader.Dispose() }
    $record = @{}
    foreach ($line in $content.Split("`n", [StringSplitOptions]::RemoveEmptyEntries)) {
        $parts = $line.Split('=', 2)
        if ($parts.Count -ne 2 -or $record.ContainsKey($parts[0])) { throw 'Human Route: malformed receipt.' }
        $record.Add($parts[0], $parts[1])
    }
    return $record
}

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { throw 'Human Route requires Windows LocalAppData.' }
$privateParent = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'PhantomHumanRoutes'))
$Root = [IO.Path]::GetFullPath($Root).TrimEnd('\')
if (![string]::Equals([IO.Path]::GetDirectoryName($Root), $privateParent, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Human Route: use a new directory directly below LocalAppData/PhantomHumanRoutes.'
}
Assert-PlainPath $Root

function Get-QueueInputs {
    Assert-PlainPath $ScenarioFile
    $scenario = Get-Item -LiteralPath $ScenarioFile
    if ($scenario.PSIsContainer -or $scenario.Length -eq 0 -or $scenario.Length -gt 8192) { throw 'Human Route: invalid scenario file.' }
    $shards = @()
    if ($FailureSnapshot) {
        Assert-PlainPath $FailureSnapshot
        $shards = @(Get-ChildItem -LiteralPath $FailureSnapshot -File | Where-Object Name -Match '^region--?\d+_-?\d+\.tsv$' | Select-Object -First 5)
        if ($shards.Count -lt 1 -or $shards.Count -gt 4) { throw 'Human Route: supply one to four exact failure shards.' }
    }
    else {
        $rows = [IO.File]::ReadAllLines($scenario.FullName, $encoding)
        if ($rows.Count -eq 0) { throw 'Human Route: empty scenario.' }
        foreach ($row in $rows) {
            $fields = $row.Split("`t")
            if ($fields.Count -ne 15 -or $fields[11] -cne 'RUNTIME_FARM_REFUSAL') { throw 'Human Route: learned scenes require failure shards.' }
        }
    }
    foreach ($shard in $shards) {
        Assert-PlainPath $shard.FullName
        if ($shard.Length -eq 0 -or $shard.Length -gt 2097152) { throw 'Human Route: invalid failure shard.' }
    }

    return @($scenario) + $shards
}

if ($Initialize) {
    if (Test-Path -LiteralPath $Root) { throw 'Human Route: initialization needs a fresh directory.' }
    [IO.Directory]::CreateDirectory($privateParent) | Out-Null
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($owner)
    $acl.SetAccessRuleProtection($true, $false)
    $inherit = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($owner, 'FullControl', $inherit, 'None', 'Allow'))
    New-Item -ItemType Directory -Path $Root | Out-Null
    Set-Acl -LiteralPath $Root -AclObject $acl
    Assert-PrivatePath $Root
    [IO.Directory]::CreateDirectory((Join-Path $Root 'evidence')) | Out-Null
    Assert-PrivatePath (Join-Path $Root 'evidence')
    [pscustomobject]@{ result = 'PRIVATE_MAILBOX_PREPARED' }
    return
}

Assert-PrivatePath $Root
if (($Action -in @('arrived', 'skip', 'next')) -ne ($Scene -ne '-')) { throw 'Human Route: exact scene receipt required for this action.' }
$isConsent = $Action -in @('arm', 'renew')
$hasIdentity = $PSBoundParameters.ContainsKey('TestAccount') -or $PSBoundParameters.ContainsKey('ObjectId')
if (($isConsent -and (!$TestAccount -or $ObjectId -lt 1)) -or (!$isConsent -and $hasIdentity)) {
    throw 'Human Route: arm and renew require explicit consent for an exact account and object.'
}
if (($Action -eq 'reload') -ne [bool]$ScenarioFile -or ($FailureSnapshot -and $Action -ne 'reload')) {
    throw 'Human Route: only reload accepts a scene bundle.'
}
$serverPath = Join-Path $Root 'server.txt'
$statePath = Join-Path $Root 'status.txt'
$isStop = $Action -eq 'stop'
$replyPath = Join-Path $Root $(if ($isStop) { 'stop-receipt.txt' } else { 'receipt.txt' })
$server = Read-Record $serverPath
$state = Read-Record $statePath
$process = Get-Process -Id $ServerProcessId
$started = ([DateTimeOffset]$process.StartTime).ToUnixTimeMilliseconds()
if ($server['version'] -ne '2' -or $server['pid'] -ne [string]$ServerProcessId -or $process.ProcessName -notin @('java', 'javaw') -or
    [Math]::Abs([long]$server['started'] - $started) -gt 1 -or $server['incarnation'] -ne $state['incarnation']) {
    throw 'Human Route: expected GameServer incarnation is unavailable.'
}
$incarnation = ([guid]$server['incarnation']).ToString()
if ($state['phase'] -eq 'OFF' -and $Action -in @('status', 'stop')) {
    $state.Remove('incarnation')
    $state.Remove('next_sequence')
    [pscustomobject]$state
    return
}
if ($state['phase'] -eq 'OFF') { throw 'Human Route: this GameServer session is closed.' }
$sequence = if ($isStop) { 0 } else { [long]$state['next_sequence'] }
if (!$isStop -and $sequence -lt 1) { throw 'Human Route: invalid next sequence.' }
$id = [guid]::NewGuid().ToString()
$stagedFiles = @()
$bundle = $null
try {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(12).ToUnixTimeMilliseconds()
    if ($isConsent) {
        $ticket = Join-Path $Root "permit-$id.txt"
        $expires = [DateTimeOffset]::UtcNow.AddMinutes($PermitMinutes).ToUnixTimeMilliseconds()
        Write-AtomicRequest $ticket "EnableHumanRouteDemonstration=True`nincarnation=$incarnation`nid=$id`nsequence=$sequence`nrequestDeadline=$deadline`naccount=$TestAccount`nobjectId=$ObjectId`nexpires=$expires`n"
        $stagedFiles += $ticket
    }
    elseif ($Action -eq 'reload') {
        $inputs = @(Get-QueueInputs)
        $bundle = Join-Path $Root "queue-$id"
        New-Item -ItemType Directory -Path $bundle | Out-Null
        Assert-PrivatePath $bundle
        for ($i = 0; $i -lt $inputs.Count; $i++) {
            $inputFile = $inputs[$i]
            $destination = Join-Path $bundle $(if ($i -eq 0) { 'scenes.tsv' } else { $inputFile.Name })
            $bytes = [IO.File]::ReadAllBytes($inputFile.FullName)
            if ($bytes.Length -ne $inputFile.Length) { throw 'Human Route: scene input changed during staging.' }
            $stagedFiles += $destination
            [IO.File]::WriteAllBytes($destination, $bytes)
            Assert-PrivatePath $destination
        }
    }
    Write-AtomicRequest (Join-Path $Root $(if ($isStop) { 'stop.txt' } else { 'request.txt' })) "version=1`nincarnation=$incarnation`nid=$id`nsequence=$sequence`ndeadline=$deadline`naction=$Action`nscene=$Scene`n"

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt 13000) {
        if ([IO.File]::Exists($replyPath)) {
            $reply = Read-Record $replyPath
            if ($reply['incarnation'] -eq $incarnation -and $reply['id'] -eq $id) {
                if ($reply['result'] -eq 'TELEPORT_PENDING') {
                    do {
                        Start-Sleep -Milliseconds 250
                        $reply = Read-Record $statePath
                        if ($reply['incarnation'] -ne $incarnation) { throw 'Human Route: GameServer incarnation changed during landing.' }
                        if ($reply['passage_id'] -ne $id) { throw 'Human Route: another passage superseded this landing. Check current status.' }
                    } while ($reply['phase'] -eq 'TELEPORT_PENDING' -and $watch.ElapsedMilliseconds -lt 17000)
                }
                $reply.Remove('incarnation')
                $reply.Remove('id')
                $reply.Remove('next_sequence')
                [pscustomobject]$reply
                return
            }
        }
        Start-Sleep -Milliseconds 200
    }
    throw 'Human Route: no matching receipt. Check status before taking another action.'
}
finally {
    foreach ($file in $stagedFiles) {
        if ([IO.File]::Exists($file)) {
            Assert-PrivatePath $file
            [IO.File]::Delete($file)
        }
    }
    if ($bundle -and [IO.Directory]::Exists($bundle)) {
        Assert-PrivatePath $bundle
        [IO.Directory]::Delete($bundle)
    }
}
