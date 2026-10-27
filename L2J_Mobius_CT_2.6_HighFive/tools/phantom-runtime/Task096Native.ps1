[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Prepare')][string]$Action,
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [Parameter(Mandatory=$true)][string]$StageRoot,
    [Parameter(Mandatory=$true)][string]$InstallationId
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    $absolute = [IO.Path]::GetFullPath($Path)
    if ($item.FullName -ne $absolute) { throw 'TASK096N_NONCANONICAL_PATH' }
    while ($null -ne $item) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'TASK096N_REPARSE_PATH' }
        if ($item -is [IO.FileInfo]) { $item = $item.Directory } else { $item = $item.Parent }
    }
    return $absolute
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-New([string]$Path, [string]$Content) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Content)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}

$source = Assert-Physical $SourceRoot
$repo = Assert-Physical (Split-Path -Parent $source)
$stage = [IO.Path]::GetFullPath($StageRoot)
$parsedId = [Guid]::ParseExact($InstallationId, 'D')
if ($parsedId.ToString() -cne $InstallationId -or (Split-Path -Leaf $source) -cne 'L2J_Mobius_CT_2.6_HighFive') { throw 'TASK096N_SOURCE_UUID' }
if ($stage.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $stage -eq $repo -or (Test-Path -LiteralPath $stage)) { throw 'TASK096N_FRESH_EXTERNAL_STAGE_REQUIRED' }
$stageParent = Assert-Physical (Split-Path -Parent $stage)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$sourceOwner = (Get-Acl -LiteralPath $source).Owner
$sourceOwnerSid = [Security.Principal.NTAccount]::new($sourceOwner).Translate([Security.Principal.SecurityIdentifier])
if ($sourceOwnerSid.Value -cne $identity.Value) { throw 'TASK096N_SOURCE_OWNER_EXECUTION_REQUIRED' }
$head = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'TASK096N_HEAD' }
$branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
& git -C $repo merge-base --is-ancestor 3352706b8799f92e3e243ab2890e179b8a35466f HEAD
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feature/phantom-world') { throw 'TASK096N_BASELINE' }

[IO.Directory]::CreateDirectory($stage) | Out-Null
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
$inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
foreach ($sid in @($identity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $rule = [Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', $inheritance, 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $stage -AclObject $acl
$verified = Get-Acl -LiteralPath $stage
foreach ($rule in $verified.Access) {
    $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
    if ($rule.AccessControlType -eq 'Allow' -and $sid -notin @($identity.Value, 'S-1-5-18')) { throw 'TASK096N_PRIVATE_ACL' }
}
Assert-Physical $stage | Out-Null
$shadow = Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
[IO.Directory]::CreateDirectory($shadow) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $stage 'private')) | Out-Null
Write-New (Join-Path $stage 'prepare.begun') "installationId=$InstallationId`nutc=$([DateTime]::UtcNow.ToString('o'))`n"

$inventory = [Collections.Generic.List[string]]::new()
$allowed = @('build.xml', 'java', 'test', 'dist', 'tools', 'docs/phantoms/live-world/LIVE003_CORE_SCALE_10000.tsv')
foreach ($entry in $allowed) {
    $origin = Join-Path $source $entry
    Assert-Physical $origin | Out-Null
    $files = if (Test-Path -LiteralPath $origin -PathType Leaf) { @(Get-Item -LiteralPath $origin) } else { @(Get-ChildItem -LiteralPath $origin -File -Recurse -Force) }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
        if ($relative -eq 'dist/game/config/Database.ini' -or $relative -match '^dist/(game/(log|logs)|login/(log|logs))/') { continue }
        Assert-Physical $file.FullName | Out-Null
        $copy = Join-Path $shadow $relative
        [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
        $before = Hash $file.FullName
        [IO.File]::Copy($file.FullName, $copy, $false)
        if ((Hash $file.FullName) -cne $before -or (Hash $copy) -cne $before) { throw 'TASK096N_COPY_DRIFT' }
        $inventory.Add("$relative`t$before")
    }
}
$geoRoot = Assert-Physical (Join-Path $repo '.phantom-local/task074/runtime-test/game/data/geodata')
$geoPin = Join-Path $repo '.phantom-local/task074/GEODATA074.tsv'
$geoRows = @(Import-Csv -LiteralPath $geoPin -Delimiter "`t" | Where-Object { $_.kind -eq 'GeoData' })
if ($geoRows.Count -ne 203) { throw 'TASK096N_GEO_COUNT' }
foreach ($row in $geoRows) {
    $name = Split-Path -Leaf $row.relativePath
    if ($name -cnotmatch '^\d{2}_\d{2}\.l2j$') { throw 'TASK096N_GEO_NAME' }
    $origin = Assert-Physical (Join-Path $geoRoot $name)
    if ((Hash $origin) -ine $row.sha256) { throw 'TASK096N_GEO_PIN' }
    $relative = 'dist/game/data/geodata/' + $name
    $copy = Join-Path $shadow $relative
    [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
    [IO.File]::Copy($origin, $copy, $false)
    if ((Hash $copy) -ine $row.sha256) { throw 'TASK096N_GEO_COPY' }
    $inventory.Add("$relative`t$($row.sha256.ToLowerInvariant())")
}
Write-New (Join-Path $stage 'inputs.tsv') (($inventory | Sort-Object) -join "`n")
$ownerLines = @(
    'owner=TASK096_NATIVE_ACCEPTANCE', 'schema=l2jmobiush5_phantom_test_096n',
    "installationId=$InstallationId", "expiresUtc=$([DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ'))",
    "sourceHead=$head", "branch=$branch", "sourceModule=$($source.Replace('\','/'))", "stage=$($stage.Replace('\','/'))",
    "sourceDatabaseSha256=$(Hash (Join-Path $source 'dist/game/config/Database.ini'))", "inputsSha256=$(Hash (Join-Path $stage 'inputs.tsv'))",
    "geoPinSha256=$(Hash $geoPin)", 'outputAllowlist=private,build,receipts'
)
Write-New (Join-Path $stage 'private/owner.properties') ($ownerLines -join "`n")
Write-New (Join-Path $stage 'prepare.completed') "installationId=$InstallationId`ninputs=$($inventory.Count)`n"
Write-Output "TASK096N_PREPARED inputs=$($inventory.Count) Geo=203 databaseConfig=ABSENT DDL_DML=0"
