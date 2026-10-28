[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Prepare')][string]$Action,
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [Parameter(Mandatory=$true)][string]$StageRoot,
    [Parameter(Mandatory=$true)][string]$InstallationId
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    $absolute = [IO.Path]::GetFullPath($Path)
    if ($item.FullName -ine $absolute) { throw 'OWN101_NONCANONICAL_PATH' }
    while ($null -ne $item) {
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'OWN101_REPARSE_PATH' }
        if ($item -is [IO.FileInfo]) { $item = $item.Directory } else { $item = $item.Parent }
    }
    return $absolute
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-Text([string]$Path, [string]$Content) {
    $stream = [IO.File]::Open($Path, 'CreateNew', 'Write', 'None')
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Content); $stream.Write($bytes, 0, $bytes.Length) }
    finally { $stream.Dispose() }
}

$source = Physical $SourceRoot
$repo = Physical (Split-Path -Parent $source)
$stage = [IO.Path]::GetFullPath($StageRoot)
if ([Guid]::ParseExact($InstallationId, 'D').ToString() -cne $InstallationId -or (Split-Path -Leaf $source) -cne 'L2J_Mobius_CT_2.6_HighFive') { throw 'OWN101_SOURCE_UUID' }
if ($stage.StartsWith($repo + '\', [StringComparison]::OrdinalIgnoreCase) -or $stage -ieq $repo -or (Test-Path -LiteralPath $stage)) { throw 'OWN101_FRESH_EXTERNAL_STAGE_REQUIRED' }
Physical (Split-Path -Parent $stage) | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$sourceOwner = [Security.Principal.NTAccount]::new((Get-Acl -LiteralPath $source).Owner).Translate([Security.Principal.SecurityIdentifier])
if ($sourceOwner.Value -cne $identity.Value) { throw 'OWN101_SOURCE_OWNER_EXECUTION_REQUIRED' }
$head = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'OWN101_HEAD' }
$branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
& git -C $repo merge-base --is-ancestor e3b4a978a333ae280c922896c418cc2d9df04dd3 HEAD
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feature/phantom-world') { throw 'OWN101_BASELINE' }
$package = Join-Path $source 'docs/phantoms/tasks/PHANTOM-CRAFT-MARKET-CLOSURE-101'
$manifest = Get-Content -LiteralPath (Join-Path $package 'MANIFEST_SHA256.json') -Raw | ConvertFrom-Json
foreach ($file in $manifest.files.PSObject.Properties) {
    $packageFile = Join-Path $package $file.Name
    if ((Hash $packageFile) -cne $file.Value.sha256 -or (Get-Item -LiteralPath $packageFile).Length -ne $file.Value.bytes) { throw 'OWN101_PACKAGE_DRIFT' }
}

[IO.Directory]::CreateDirectory($stage) | Out-Null
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($identity); $acl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($identity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $stage -AclObject $acl
$actual = Get-Acl -LiteralPath $stage
if (!$actual.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($actual.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $identity.Value) { throw 'OWN101_OWNER_ACL' }
foreach ($rule in $actual.Access) {
    if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity.Value, 'S-1-5-18')) { throw 'OWN101_PRIVATE_ACL' }
}
Physical $stage | Out-Null
$shadow = Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
[IO.Directory]::CreateDirectory($shadow) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $stage 'private/preimage')) | Out-Null
New-Text (Join-Path $stage 'prepare.begun') "installationId=$InstallationId`nutc=$([DateTime]::UtcNow.ToString('o'))`n"
$inventory = [Collections.Generic.List[string]]::new()
foreach ($entry in @('build.xml', 'java', 'test', 'dist', 'tools')) {
    $origin = Join-Path $source $entry
    Physical $origin | Out-Null
    $files = if (Test-Path -LiteralPath $origin -PathType Leaf) { @(Get-Item -LiteralPath $origin) } else { @(Get-ChildItem -LiteralPath $origin -File -Recurse -Force) }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
        if ($relative -in @('dist/game/config/Database.ini','dist/login/config/Database.ini') -or $relative -match '^dist/(game|login)/(log|logs)/') { continue }
        Physical $file.FullName | Out-Null
        $copy = Join-Path $shadow $relative
        [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
        $before = Hash $file.FullName
        [IO.File]::Copy($file.FullName, $copy, $false)
        if ((Hash $file.FullName) -cne $before -or (Hash $copy) -cne $before) { throw 'OWN101_COPY_DRIFT' }
        $inventory.Add("$relative`t$before")
    }
}
# Physical copies above are also the SHA-backed preimages; source config is never applied here.
$geoRoot = Physical (Join-Path $repo '.phantom-local/task074/runtime-test/game/data/geodata')
$geoPin = Join-Path $repo '.phantom-local/task074/GEODATA074.tsv'
$geoRows = @(Import-Csv -LiteralPath $geoPin -Delimiter "`t" | Where-Object { $_.kind -eq 'GeoData' })
if ($geoRows.Count -ne 203) { throw 'OWN101_GEO_COUNT' }
foreach ($row in $geoRows) {
    $name = Split-Path -Leaf $row.relativePath
    if ($name -cnotmatch '^\d{2}_\d{2}\.l2j$') { throw 'OWN101_GEO_NAME' }
    $origin = Physical (Join-Path $geoRoot $name)
    if ((Hash $origin) -ine $row.sha256) { throw 'OWN101_GEO_PIN' }
    $relative = 'dist/game/data/geodata/' + $name
    $copy = Join-Path $shadow $relative
    [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
    if (Test-Path -LiteralPath $copy) { if ((Hash $copy) -ine $row.sha256) { throw 'OWN101_GEO_PREEXISTING_DRIFT' } }
    else { [IO.File]::Copy($origin, $copy, $false) }
    if ((Hash $copy) -ine $row.sha256) { throw 'OWN101_GEO_COPY' }
    $inventory.Add("$relative`t$($row.sha256.ToLowerInvariant())")
}
New-Text (Join-Path $stage 'inputs.tsv') (($inventory | Sort-Object -Unique) -join "`n")
$owner = @(
    'owner=TASK101_CRAFT_MARKET', 'schema=l2jmobiush5_phantom_test_101',
    "installationId=$InstallationId", "expiresUtc=$([DateTime]::UtcNow.AddHours(6).ToString('yyyy-MM-ddTHH:mm:ssZ'))",
    "sourceHead=$head", "branch=$branch", "ownerSid=$($identity.Value)",
    "sourceModule=$($source.Replace('\','/'))", "stage=$($stage.Replace('\','/'))",
    "sourceDatabaseSha256=$(Hash (Join-Path $source 'dist/game/config/Database.ini'))",
    "inputsSha256=$(Hash (Join-Path $stage 'inputs.tsv'))", "geoPinSha256=$(Hash $geoPin)",
    'outputAllowlist=private,build,receipts'
)
New-Text (Join-Path $stage 'private/owner.properties') ($owner -join "`n")
New-Text (Join-Path $stage 'prepare.completed') "installationId=$InstallationId`ninputs=$($inventory.Count)`n"
Write-Output "OWN101_PREPARED inputs=$($inventory.Count) Geo=203 package=12 ACL=OWNER_SYSTEM databaseConfig=ABSENT DDL_DML=0"
