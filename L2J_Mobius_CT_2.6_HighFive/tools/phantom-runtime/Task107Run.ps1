[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit','BuildScratch','Prepare','Bind','Seal','A','B')][string]$Action,
    [string]$SqlClient = (Join-Path $env:ProgramFiles 'MariaDB 11.4/bin/mariadb.exe')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    for ($walk = $item; $null -ne $walk; $walk = if ($walk -is [IO.FileInfo]) { $walk.Directory } else { $walk.Parent }) {
        if ($walk.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'OWN107_REPARSE' }
    }
    return $item.FullName
}
function Hash([string]$Path) { (Get-FileHash -LiteralPath (Physical $Path) -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-Text([string]$Path, [string]$Value) {
    $stream = [IO.File]::Open($Path, 'CreateNew', 'Write', 'None')
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Value); $stream.Write($bytes, 0, $bytes.Length) }
    finally { $stream.Dispose() }
}
function Properties([string]$Path) {
    $result = @{}
    foreach ($line in [IO.File]::ReadAllLines((Physical $Path))) {
        if ($line -match '^([^#=]+)=(.*)$') { $result[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $result
}
function Assert-Private([string]$Path) {
    $acl = Get-Acl -LiteralPath (Physical $Path)
    if (!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid) { throw 'OWN107_PRIVATE_OWNER_REQUIRED' }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid, 'S-1-5-18')) { throw 'OWN107_FOREIGN_ACL' }
    }
}
function Private-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'OWN107_FRESH_DIRECTORY_REQUIRED' }
    Physical (Split-Path -Parent $Path) | Out-Null
    New-Item -ItemType Directory -Path $Path | Out-Null
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid)); $acl.SetAccessRuleProtection($true, $false)
    foreach ($id in @($sid, 'S-1-5-18')) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($id), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-Private $Path
}
function Java0 {
    if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0) { throw 'OWN107_JAVA_PRESENT_NO_RUNTIME' }
    if (@(Get-NetTCPConnection -ErrorAction Stop | Where-Object { $_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308) }).Count -ne 0) { throw 'OWN107_TCP_PRESENT_NO_RUNTIME' }
}

$module = Physical (Join-Path $PSScriptRoot '../..')
$repo = Physical (Split-Path -Parent $module)
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior = Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105/resume105.private.json') -Raw | ConvertFrom-Json
$origin = Physical $prior.origin
$original = Properties (Join-Path $origin 'private/owner.properties')
$client = Physical $SqlClient
$options = Physical (Join-Path $prior.archive 'sql-client.private.cnf')
$index = Join-Path $repo '.phantom-local/logs/PHANTOM-LIVING-MARKET-107'
foreach ($root in @($origin, (Physical $prior.archive), (Physical $prior.resume))) { Assert-Private $root }
if ($original.owner -cne 'TASK102_POPULATION_SCALE' -or $original.schema -cne 'l2jmobiush5_phantom_test_102' -or $original.ownerSid -cne $sid -or
    (Physical $original.sourceModule) -ine $module -or (Hash (Join-Path $origin 'inputs.tsv')) -cne $original.inputsSha256 -or
    ![IO.File]::ReadAllText((Physical (Join-Path $origin 'private/db.created'))).StartsWith($original.installationId + "`n") -or
    (Hash (Join-Path $module 'dist/game/config/Database.ini')) -cne $original.sourceDatabaseSha256) { throw 'OWN107_ORIGINAL_AUTHORITY_DRIFT' }

function Sql([string]$Query, [string]$Output) {
    # Fixed read-only callers only. Keep credentials and complete snapshots outside checkout.
    $rows = @($Query | & $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>($Output + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN107_READONLY_SQL_FAILED' }
    New-Text $Output (($rows -join "`n") + "`n")
    return $rows
}
function Clearance([string]$Output) {
    Java0
    New-Item -ItemType Directory -Path $Output | Out-Null
    $grants = @(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $Output 'grants.private.tsv'))
    if (!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')) { throw 'OWN107_FULL_VISIBILITY_REQUIRED' }
    $authority = @(Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' (Join-Path $Output 'authority.private.tsv'))
    if ($authority.Count -ne 1 -or $authority[0] -cne ($original.installationId + "`t" + $original.sourceHead + "`t" + $original.inputsSha256)) { throw 'OWN107_SQL_AUTHORITY_DRIFT' }
    $sessions = @(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $Output 'sessions.private.tsv'))
    if ($sessions.Count -ne 0) { throw 'OWN107_FOREIGN_OR_OWN_SQL_PRESENT' }
    foreach ($cut in @('first', 'second')) {
        $null = Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $Output ($cut + '-preimage.private.tsv'))
    }
    if ((Hash (Join-Path $Output 'first-preimage.private.tsv')) -cne (Hash (Join-Path $Output 'second-preimage.private.tsv'))) { throw 'OWN107_UNSTABLE_PREIMAGE' }
    Java0
    New-Text (Join-Path $Output 'clearance.json') (@{ JVM = 0; SQL = 0; TCP = 0; foreign = 0; fullVisibility = $true; authorityExact = $true; stable = $true; utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json)
}

if ($Action -eq 'Audit') {
    Java0
    $pins = Get-Content -LiteralPath (Join-Path $prior.archive 'protected-source105.json') -Raw | ConvertFrom-Json
    foreach ($pin in $pins) { if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN107_PROTECTED_SOURCE_DRIFT' } }
    Private-Directory $index
    $archive = Join-Path (Split-Path -Parent $origin) ('TASK107-' + [guid]::NewGuid().ToString('N'))
    Private-Directory $archive
    New-Text (Join-Path $index 'index.private.json') (@{ archive = $archive; origin = $origin; stage = (Join-Path $archive 'stage107'); preparationId = [guid]::NewGuid().ToString(); head = (& git -C $repo rev-parse HEAD).Trim(); createdUtc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json)
    Clearance (Join-Path $archive 'g0')
    Copy-Item -LiteralPath (Join-Path $prior.archive 'protected-source105.json') -Destination (Join-Path $archive 'protected-source.private.json')
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/OwnerConfig.json') -Destination (Join-Path $archive 'source-owner107.private.json')
    New-Text (Join-Path $archive 'audit.complete') 'G0_107_PASS;JVM=0;SQL=0;TCP=0;foreign=0;stable=true;SQL_WRITES=0'
    Write-Output 'G0_107_PASS originalAuthority=EXACT;fullVisibility=true;foreign=0;JVM=0;SQL=0;TCP=0;stable=true;protected=UNCHANGED;SQL_WRITES=0'
    return
}

if ($Action -eq 'BuildScratch') {
    Assert-Private $index
    $info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
    $archive = Physical $info.archive
    Assert-Private $archive
    if (!(Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN107_G0_REQUIRED' }
    $scratch = Join-Path $archive 'build-only'
    Private-Directory $scratch
    $shadow = Join-Path $scratch 'L2J_Mobius_CT_2.6_HighFive'
    New-Item -ItemType Directory -Path $shadow | Out-Null
    $count = 0
    foreach ($entry in @('build.xml', 'java', 'test', 'dist', 'tools')) {
        $source = Physical (Join-Path $module $entry)
        $files = if (Test-Path -LiteralPath $source -PathType Leaf) { @(Get-Item -LiteralPath $source) } else { @(Get-ChildItem -LiteralPath $source -File -Recurse -Force) }
        foreach ($file in $files) {
            $path = Physical $file.FullName
            $relative = $path.Substring($module.Length + 1).Replace('\', '/')
            if ($relative -eq 'dist/game/config/Database.ini' -or $relative -eq 'dist/login/config/Database.ini' -or $relative -match '^dist/(game|login)/(log|logs|data/geodata)/') { continue }
            $copy = Join-Path $shadow $relative
            [IO.Directory]::CreateDirectory((Split-Path -Parent $copy)) | Out-Null
            [IO.File]::Copy($path, $copy, $false)
            $count++
        }
    }
    New-Text (Join-Path $scratch 'BUILD_ONLY_NOT_NATIVE') 'No Database.ini; no server; no receipts or authority enabling native execution.'
    Write-Output "OWN107_BUILD_SCRATCH_READY files=$count;databaseConfig=ABSENT;native=FORBIDDEN"
    return
}

Assert-Private $index
$info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
$archive = Physical $info.archive
Assert-Private $archive
$stage = $info.stage
function Protected {
    $manifest = Join-Path $archive 'protected-final107.private.json'
    if (!(Test-Path -LiteralPath $manifest)) { throw 'OWN107_FINAL_SOURCE_PINS_REQUIRED' }
    foreach ($pin in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
        if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN107_SEALED_SOURCE_DRIFT' }
    }
    if ((Hash (Join-Path $archive 'source-owner107.private.json')) -cne (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json'))) { throw 'OWN107_OLD_OWNER_OVERRIDES_CHANGED' }
}
if ($Action -eq 'Prepare') {
    if (!(Test-Path -LiteralPath (Join-Path $archive 'ready107.private.json'))) { throw 'OWN107_DB_FREE_CATALOG_REVIEW_REQUIRED' }
    $ready = Get-Content -Raw -LiteralPath (Join-Path $archive 'ready107.private.json') | ConvertFrom-Json
    if (!$ready.dbFreeGreen -or !$ready.sourceAuditComplete -or $ready.reviewP1 -ne 0 -or $ready.reviewP2 -ne 0) { throw 'OWN107_PRE_NATIVE_GATE_REQUIRED' }
    Protected
    Clearance (Join-Path $archive 'before-prepare')
    & (Join-Path $PSScriptRoot 'Task102.ps1') -Action Prepare -SourceRoot $module -StageRoot $stage -InstallationId $info.preparationId
    if (!$?) { throw 'OWN107_PREPARE_FAILED' }
    return
}
Java0; Protected
$stage = Physical $stage; Assert-Private $stage
$shadow = Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
$private = Physical (Join-Path $stage 'private')
$game = Physical (Join-Path $shadow 'dist/game')
$database = Join-Path $game 'config/Database.ini'
$receipts = Join-Path $private 'receipts107'
$cp = (Join-Path $stage 'build/phantom-test/bin') + ';' + (Join-Path $stage 'build/phantom-test/resources') + ';' + (Join-Path $shadow 'dist/libs/*')
if ($Action -eq 'Bind') {
    Clearance (Join-Path $archive 'before-bind')
    if (Test-Path -LiteralPath $database) { throw 'OWN107_FRESH_PREPARE_ONLY' }
    $prepared = Properties (Join-Path $private 'owner.properties')
    if ($prepared.installationId -cne $info.preparationId -or (Hash (Join-Path $stage 'inputs.tsv')) -cne $prepared.inputsSha256) { throw 'OWN107_PREPARATION_DRIFT' }
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared107-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source107-inputs.tsv')
    $owner = [IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
    $owner = [regex]::Replace($owner, '(?m)^stage=.*$', ('stage=' + $stage.Replace('\', '/')))
    $owner = [regex]::Replace($owner, '(?m)^expiresUtc=.*$', ('expiresUtc=' + [DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'), $owner, [Text.UTF8Encoding]::new($false))
    foreach ($relative in @('inputs.tsv', 'private/db.created')) { Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force }
    Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path $receipts | Out-Null
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime107-final.ini')
    $ini = [IO.File]::ReadAllText((Join-Path $private 'runtime107-final.ini'))
    foreach ($key in @('PhantomPopulationActiveTarget', 'MaxMaterializedPhantoms')) {
        $pattern = '(?m)^(' + [regex]::Escape($key) + '[ \t]*=[ \t]*)[^\r\n]*'
        if ([regex]::Matches($ini, $pattern).Count -ne 1) { throw 'OWN107_OVERLAY_KEY' }
        $ini = [regex]::Replace($ini, $pattern, { param($m) $m.Groups[1].Value + '16' })
    }
    [IO.File]::WriteAllText((Join-Path $game 'config/Custom/PhantomPlayers.ini'), $ini, [Text.UTF8Encoding]::new($false))
    Write-Output 'OWN107_BOUND originalDB=true;newProfiles=0;privateCap=16;source128=UNCHANGED'
    return
}
if ($Action -eq 'Seal') {
    Clearance (Join-Path $archive 'before-seal')
    if (Test-Path -LiteralPath (Join-Path $private 'recovery107.properties')) { throw 'OWN107_SINGLE_SEAL_REQUIRED' }
    $rows = [Collections.Generic.List[string]]::new()
    foreach ($root in @($shadow, (Join-Path $stage 'build/phantom-test/bin'), (Join-Path $stage 'build/phantom-test/resources'))) {
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force) {
            $relative = (Physical $file.FullName).Substring($stage.Length + 1).Replace('\', '/')
            if ($relative -match '/dist/(?:game|login)/(?:log|logs)/') { continue }
            $rows.Add($relative + "`t" + (Hash $file.FullName))
        }
    }
    New-Text (Join-Path $private 'native-inputs.tsv') (($rows | Sort-Object) -join "`n")
    $pins = @('dist/libs/GameServer.jar', 'dist/game/config/Database.ini', 'dist/game/config/Custom/PhantomPlayers.ini', 'dist/game/config/Custom/PhantomMerchants.ini') | ForEach-Object { $_ + '=' + (Hash (Join-Path $shadow $_)) }
    New-Text (Join-Path $private 'native.properties') (($pins -join "`n") + "`nnativeInputsSha256=" + (Hash (Join-Path $private 'native-inputs.tsv')))
    New-Text (Join-Path $private 'recovery107.properties') ("task=PHANTOM-LIVING-MARKET-107`nnoReseed=true`njarSha256=" + (Hash (Join-Path $shadow 'dist/libs/GameServer.jar')) + "`nsourceOwnerSha256=" + (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json')) + "`nsourceIniSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')) + "`nsourceMerchantSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))
    Write-Output 'OWN107_SEALED namespace=107;SAME_DB=true;cap=16;NO_FORCE_ROUTE=true'
    return
}

function Census([string]$Cut) {
    Clearance (Join-Path $archive ('clearance-' + $Cut))
    $out = Join-Path $receipts ('readonly-' + $Cut)
    New-Item -ItemType Directory -Path $out | Out-Null
    & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN107_READONLY_CENSUS_FAILED' }
    Java0
    return $out
}
$seal = Properties (Join-Path $private 'recovery107.properties')
foreach ($pair in @(@('jarSha256', (Join-Path $shadow 'dist/libs/GameServer.jar')), @('sourceOwnerSha256', (Join-Path $module 'dist/game/config/OwnerConfig.json')), @('sourceIniSha256', (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')), @('sourceMerchantSha256', (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))) {
    if ($seal[$pair[0]] -cne (Hash $pair[1])) { throw 'OWN107_SEAL_DRIFT' }
}
$phase = if ($Action -eq 'A') { 6 } else { 4 }
if (Test-Path -LiteralPath (Join-Path $receipts "launch$phase.begun")) { throw 'OWN107_SINGLE_GENERATION_REQUIRED' }
if ($Action -eq 'B') {
    foreach ($name in @('phase6.complete', 'stopped6.checkpoint', 'stop6-world-leases.txt', 'exit6.clean')) { if (!(Test-Path -LiteralPath (Join-Path $receipts $name))) { throw 'OWN107_A_FULL_CLEAN_REQUIRED' } }
    if ([IO.File]::ReadAllText((Join-Path $receipts 'exit6.clean')) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;' -or [IO.File]::ReadAllText((Join-Path $receipts 'merchants107-prestop6.txt')) -notmatch ';market=PASS') { throw 'OWN107_A_NATURAL_CLEAN_MARKET_REQUIRED' }
}
$pre = Census ('before' + $Action)
if ($Action -eq 'B' -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts 'stopped6.checkpoint')).TrimEnd([char[]]"`r`n")) { throw 'OWN107_PRESTART_CHECKPOINT_DRIFT' }
New-Text (Join-Path $receipts "launch$phase.begun") ('utc=' + [DateTime]::UtcNow.ToString('o'))
$uuid = $original.installationId
$arguments = @('-Xms512m', '-Xmx8g', '-Dphantom.task102.native=true', "-Dphantom.task102.stage=$stage", "-Dphantom.task102.uuid=$uuid", "-Dphantom.module.root=$shadow", "-Dphantom.test.config=$database", '-cp', $cp, 'org.l2jmobius.tests.phantoms.PhantomPopulation102Native', ('recovery107' + $Action))
$line = ($arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
$process = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
$os = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $process.Id)
if (!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)) { throw 'OWN107_NEW_PROCESS_IDENTITY_UNPROVEN_NO_RETRY' }
$digest = [Security.Cryptography.SHA256]::Create()
try { $commandHash = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-', '').ToLowerInvariant() } finally { $digest.Dispose() }
New-Text (Join-Path $receipts "process$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256 } | ConvertTo-Json)
Write-Output "OWN107_NATIVE_STARTED generation=$Action;cap=16;newProfiles=FORBIDDEN"
$deadline = [DateTime]::UtcNow.AddMinutes(12)
while (!$process.WaitForExit(1000)) {
    if ((Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")) -or [DateTime]::UtcNow -gt $deadline) {
        New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'STOP_BLOCKED_NO_RETRY'; forceApproval = $false } | ConvertTo-Json)
        throw 'OWN107_STOP_BLOCKED;NO_B;NO_RETRY;NO_FORCE'
    }
}
$exit = $process.ExitCode
New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc=" + [DateTime]::UtcNow.ToString('o'))
if ($exit -ne 0) { throw 'OWN107_NATIVE_EXIT_FAILED_NO_NEXT_RUNTIME' }
Java0
$post = Census ('after' + $Action)
$boundary = [IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))
if (!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
    $boundary -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=([0-9]+);attributedMerchants=([0-9]+);samples=2;nativePersisted=true\r?\n?$' -or $matches[1] -cne $matches[2]) { throw 'OWN107_FULL_NATIVE_STOP_PROOF_REQUIRED' }
if ([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")) { throw 'OWN107_POSTEXIT_CHECKPOINT_DRIFT' }
New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;jarSha256=" + $seal.jarSha256)
Protected
Write-Output "OWN107_GENERATION_CLEAN generation=$Action;cap=16;JVM=0;SQL=0;TCP=0;allResidualLeasesAttributed=true"
