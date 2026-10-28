[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Prepare')][string]$Action,
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [Parameter(Mandatory=$true)][string]$StageRoot,
    [Parameter(Mandatory=$true)][string]$InstallationId
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Separate 102 authority. Historical native lanes are never enabled by this tool.
function Assert-Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    $absolute = [IO.Path]::GetFullPath($Path)
    if ($item.FullName -ine $absolute) { throw 'OWN102_NONCANONICAL_PATH' }
    while ($null -ne $item) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'OWN102_REPARSE_PATH' }
        if ($item -is [IO.FileInfo]) { $item = $item.Directory } else { $item = $item.Parent }
    }
    return $absolute
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-New([string]$Path, [string]$Content) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Content); $stream.Write($bytes, 0, $bytes.Length) }
    finally { $stream.Dispose() }
}

$source = Assert-Physical $SourceRoot
$repo = Assert-Physical (Split-Path -Parent $source)
$stage = [IO.Path]::GetFullPath($StageRoot)
if ([Guid]::ParseExact($InstallationId, 'D').ToString() -cne $InstallationId -or (Split-Path -Leaf $source) -cne 'L2J_Mobius_CT_2.6_HighFive') { throw 'OWN102_SOURCE_UUID' }
if ($stage.StartsWith($repo + '\', [StringComparison]::OrdinalIgnoreCase) -or $stage -ieq $repo -or (Test-Path -LiteralPath $stage)) { throw 'OWN102_FRESH_EXTERNAL_STAGE_REQUIRED' }
Assert-Physical (Split-Path -Parent $stage) | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$sourceOwner = [Security.Principal.NTAccount]::new((Get-Acl -LiteralPath $source).Owner).Translate([Security.Principal.SecurityIdentifier])
if ($sourceOwner.Value -cne $identity.Value) { throw 'OWN102_SOURCE_OWNER_EXECUTION_REQUIRED' }
$head = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'OWN102_HEAD' }
$branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
& git -C $repo merge-base --is-ancestor 9245f95aa6c4dd7c50df47c71cd68f2aa4b0b9d0 HEAD
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feature/phantom-world') { throw 'OWN102_BASELINE' }

[IO.Directory]::CreateDirectory($stage) | Out-Null
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($identity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $stage -AclObject $acl
$actualAcl = Get-Acl -LiteralPath $stage
if (!$actualAcl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($actualAcl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $identity.Value) { throw 'OWN102_OWNER_ACL' }
foreach ($rule in $actualAcl.Access) {
    if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity.Value, 'S-1-5-18')) { throw 'OWN102_PRIVATE_ACL' }
}
Assert-Physical $stage | Out-Null
$shadow = Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
[IO.Directory]::CreateDirectory($shadow) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $stage 'private')) | Out-Null
Write-New (Join-Path $stage 'prepare.begun') "installationId=$InstallationId`nutc=$([DateTime]::UtcNow.ToString('o'))`n"

$inventory = [Collections.Generic.List[string]]::new()
foreach ($entry in @('build.xml', 'java', 'test', 'dist', 'tools')) {
    $origin = Join-Path $source $entry
    Assert-Physical $origin | Out-Null
    $files = if (Test-Path -LiteralPath $origin -PathType Leaf) { @(Get-Item -LiteralPath $origin) } else { @(Get-ChildItem -LiteralPath $origin -File -Recurse -Force) }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
        if ($relative -eq 'dist/game/config/Database.ini' -or $relative -eq 'dist/login/config/Database.ini' -or $relative -match '^dist/(game|login)/(log|logs)/') { continue }
        Assert-Physical $file.FullName | Out-Null
        $copy = Join-Path $shadow $relative
        [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
        $before = Hash $file.FullName
        [IO.File]::Copy($file.FullName, $copy, $false)
        if ((Hash $file.FullName) -cne $before -or (Hash $copy) -cne $before) { throw 'OWN102_COPY_DRIFT' }
        $inventory.Add("$relative`t$before")
    }
}
# Read-only physical Geo input; never start or modify its historical runtime.
$geoRoot = Assert-Physical (Join-Path $repo '.phantom-local/task074/runtime-test/game/data/geodata')
$geoPin = Join-Path $repo '.phantom-local/task074/GEODATA074.tsv'
$geoRows = @(Import-Csv -LiteralPath $geoPin -Delimiter "`t" | Where-Object { $_.kind -eq 'GeoData' })
if ($geoRows.Count -ne 203) { throw 'OWN102_GEO_COUNT' }
foreach ($row in $geoRows) {
    $name = Split-Path -Leaf $row.relativePath
    if ($name -cnotmatch '^\d{2}_\d{2}\.l2j$') { throw 'OWN102_GEO_NAME' }
    $origin = Assert-Physical (Join-Path $geoRoot $name)
    if ((Hash $origin) -ine $row.sha256) { throw 'OWN102_GEO_PIN' }
    $relative = 'dist/game/data/geodata/' + $name
    $copy = Join-Path $shadow $relative
    [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
    if (Test-Path -LiteralPath $copy) { if ((Hash $copy) -ine $row.sha256) { throw 'OWN102_GEO_PREEXISTING_DRIFT' } }
    else { [IO.File]::Copy($origin, $copy, $false) }
    if ((Hash $copy) -ine $row.sha256) { throw 'OWN102_GEO_COPY' }
    $inventory.Add("$relative`t$($row.sha256.ToLowerInvariant())")
}
Write-New (Join-Path $stage 'inputs.tsv') (($inventory | Sort-Object -Unique) -join "`n")
$ownerLines = @(
    'owner=TASK102_POPULATION_SCALE', 'schema=l2jmobiush5_phantom_test_102',
    "installationId=$InstallationId", "expiresUtc=$([DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ'))",
    "sourceHead=$head", "branch=$branch", "ownerSid=$($identity.Value)",
    "sourceModule=$($source.Replace('\','/'))", "stage=$($stage.Replace('\','/'))",
    "sourceDatabaseSha256=$(Hash (Join-Path $source 'dist/game/config/Database.ini'))",
    "inputsSha256=$(Hash (Join-Path $stage 'inputs.tsv'))", "geoPinSha256=$(Hash $geoPin)",
    'outputAllowlist=private,build,receipts'
)
Write-New (Join-Path $stage 'private/owner.properties') ($ownerLines -join "`n")
Write-New (Join-Path $stage 'prepare.completed') "installationId=$InstallationId`ninputs=$($inventory.Count)`n"
Write-Output "OWN102_PREPARED inputs=$($inventory.Count) Geo=203 databaseConfig=ABSENT DDL_DML=0"
