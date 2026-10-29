[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit','Prepare','Build','Bind','Smoke','Seal','A','B')][string]$Action,
    [string]$SqlClient = (Join-Path $env:ProgramFiles 'MariaDB 11.4/bin/mariadb.exe')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    for ($walk = $item; $null -ne $walk; $walk = if ($walk -is [IO.FileInfo]) { $walk.Directory } else { $walk.Parent }) {
        if ($walk.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'OWN108R_REPARSE' }
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
    if (!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid) { throw 'OWN108R_PRIVATE_OWNER_REQUIRED' }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid, 'S-1-5-18')) { throw 'OWN108R_FOREIGN_ACL' }
    }
}
function Private-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'OWN108R_FRESH_DIRECTORY_REQUIRED' }
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
    if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0) { throw 'OWN108R_JAVA_PRESENT_NO_RUNTIME' }
    if (@(Get-NetTCPConnection -ErrorAction Stop | Where-Object { $_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308) }).Count -ne 0) { throw 'OWN108R_TCP_PRESENT_NO_RUNTIME' }
}

$module = Physical (Join-Path $PSScriptRoot '../..')
$repo = Physical (Split-Path -Parent $module)
if ((Physical ((& git -C $repo rev-parse --show-toplevel).Trim())) -ine $repo) { throw 'OWN108R_GIT_ROOT' }
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior = Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105/resume105.private.json') -Raw | ConvertFrom-Json
$origin = Physical $prior.origin
$original = Properties (Join-Path $origin 'private/owner.properties')
$client = Physical $SqlClient
$options = Physical (Join-Path $prior.archive 'sql-client.private.cnf')
$index = Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-STOP-RESTART-108R'
foreach ($root in @($origin, (Physical $prior.archive), (Physical $prior.resume))) { Assert-Private $root }
if ($original.owner -cne 'TASK102_POPULATION_SCALE' -or $original.schema -cne 'l2jmobiush5_phantom_test_102' -or $original.ownerSid -cne $sid -or
    (Physical $original.sourceModule) -ine $module -or (Hash (Join-Path $origin 'inputs.tsv')) -cne $original.inputsSha256 -or
    ![IO.File]::ReadAllText((Physical (Join-Path $origin 'private/db.created'))).StartsWith($original.installationId + "`n") -or
    (Hash (Join-Path $module 'dist/game/config/Database.ini')) -cne $original.sourceDatabaseSha256) { throw 'OWN108R_ORIGINAL_AUTHORITY_DRIFT' }

function Sql([string]$Query, [string]$Output) {
    # Fixed read-only callers only. Keep credentials and complete snapshots outside checkout.
    $rows = @($Query | & $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>($Output + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_READONLY_SQL_FAILED' }
    New-Text $Output (($rows -join "`n") + "`n")
    return $rows
}
function Clearance([string]$Output) {
    Java0
    New-Item -ItemType Directory -Path $Output | Out-Null
    $grants = @(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $Output 'grants.private.tsv'))
    if (!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')) { throw 'OWN108R_FULL_VISIBILITY_REQUIRED' }
    $authority = @(Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' (Join-Path $Output 'authority.private.tsv'))
    if ($authority.Count -ne 1 -or $authority[0] -cne ($original.installationId + "`t" + $original.sourceHead + "`t" + $original.inputsSha256)) { throw 'OWN108R_SQL_AUTHORITY_DRIFT' }
    $sessions = @(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $Output 'sessions.private.tsv'))
    if ($sessions.Count -ne 0) { throw 'OWN108R_FOREIGN_OR_OWN_SQL_PRESENT' }
    foreach ($cut in @('first', 'second')) {
        $null = Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $Output ($cut + '-preimage.private.tsv'))
    }
    if ((Hash (Join-Path $Output 'first-preimage.private.tsv')) -cne (Hash (Join-Path $Output 'second-preimage.private.tsv'))) { throw 'OWN108R_UNSTABLE_PREIMAGE' }
    Java0
    New-Text (Join-Path $Output 'clearance.json') (@{ JVM = 0; SQL = 0; TCP = 0; foreign = 0; fullVisibility = $true; authorityExact = $true; stable = $true; utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json)
}

if ($Action -eq 'Audit') {
    Java0
    $head = (& git -C $repo rev-parse HEAD).Trim()
    $branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
    $tracking = (& git -C $repo rev-parse origin/feature/phantom-world).Trim()
    $live = @(& git -C $repo ls-remote --heads origin refs/heads/feature/phantom-world)
    if ($LASTEXITCODE -ne 0 -or $live.Count -ne 1 -or $branch -cne 'feature/phantom-world' -or $tracking -cne $head -or ($live[0] -split '\s+')[0] -cne $head) { throw 'OWN108R_LIVE_PARITY_REQUIRED' }
    if (!(Test-Path -LiteralPath $index)) {
        Private-Directory $index
        $archive = Join-Path ([IO.Path]::GetTempPath()) ('TASK108R-' + [guid]::NewGuid().ToString('N'))
        Private-Directory $archive
        New-Text (Join-Path $index 'index.private.json') (@{head=$head;archive=$archive;stage=(Join-Path $archive 'shadow108R');utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json)
    }
    $info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
    $archive = Physical $info.archive
    Assert-Private $index; Assert-Private $archive
    if ($info.head -cne $head -or (Test-Path -LiteralPath $info.stage) -or (Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN108R_FRESH_AUDIT_REQUIRED' }
    $previous = Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LIVING-MARKET-107/index.private.json') -Raw | ConvertFrom-Json
    # These two test-only paths are the exact108R edit allowance; every other sealed107 input remains pinned.
    $allPins = Get-Content -LiteralPath (Join-Path $previous.archive 'protected-final107.private.json') -Raw | ConvertFrom-Json
    $pins = @($allPins | Where-Object { $_.relative -notin @('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java', 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java') })
    foreach ($pin in $pins) { if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108R_PROTECTED_SOURCE_DRIFT' } }
    Clearance (Join-Path $archive 'g0')
    $old108 = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-STOP-RESTART-108/index.private.json') | ConvertFrom-Json
    Assert-Private $old108.archive
    if ((Hash (Join-Path $archive 'g0/first-preimage.private.tsv')) -cne (Hash (Join-Path $old108.archive 'g0/first-preimage.private.tsv'))) { throw 'OWN108R_G0_DRIFT_REQUIRES_AUDIT_NO_NATIVE' }
    New-Text (Join-Path $archive 'protected-final108R.private.json') ($pins | ConvertTo-Json -Depth 5)
    New-Text (Join-Path $archive 'preparation108R.uuid') ([guid]::NewGuid().ToString())
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/OwnerConfig.json') -Destination (Join-Path $archive 'source-owner108R.private.json')
    New-Text (Join-Path $archive 'audit.complete') 'G0_108R_PASS;JVM=0;SQL=0;TCP=0;foreign=0;stable=true;SQL_WRITES=0'
    Write-Output 'G0_108R_PASS originalAuthority=EXACT;fullVisibility=true;foreign=0;JVM=0;SQL=0;TCP=0;stable=true;protected=UNCHANGED;SQL_WRITES=0'
    return
}

Assert-Private $index
$info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
$archive = Physical $info.archive
Assert-Private $archive
$stage = $info.stage
function Protected {
    $manifest = Join-Path $archive 'protected-final108R.private.json'
    if (!(Test-Path -LiteralPath $manifest)) { throw 'OWN108R_FINAL_SOURCE_PINS_REQUIRED' }
    foreach ($pin in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
        if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108R_SEALED_SOURCE_DRIFT' }
    }
    if ((Hash (Join-Path $archive 'source-owner108R.private.json')) -cne (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json'))) { throw 'OWN108R_OLD_OWNER_OVERRIDES_CHANGED' }
    if ($info.head -cne (& git -C $repo rev-parse HEAD).Trim()) { throw 'OWN108R_SOURCE_HEAD_DRIFT' }
}
if ($Action -eq 'Prepare') {
    if (!(Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN108R_G0_REQUIRED' }
    Protected
    Clearance (Join-Path $archive 'before-prepare')
    & (Join-Path $PSScriptRoot 'Task102.ps1') -Action Prepare -SourceRoot $module -StageRoot $stage -InstallationId ([IO.File]::ReadAllText((Join-Path $archive 'preparation108R.uuid')))
    if (!$?) { throw 'OWN108R_PREPARE_FAILED' }
    return
}
Java0; Protected
$stage = Physical $stage; Assert-Private $stage
$shadow = Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
$private = Physical (Join-Path $stage 'private')
$game = Physical (Join-Path $shadow 'dist/game')
$database = Join-Path $game 'config/Database.ini'
$receipts = Join-Path $private 'receipts108R'
$cp = ((Join-Path $stage 'build/phantom-test/bin') + ';' + (Join-Path $stage 'build/phantom-test/resources') + ';' + (Join-Path $shadow 'dist/libs/*')).Replace('\', '/')
if ($Action -eq 'Build') {
    if (Test-Path -LiteralPath $database) { throw 'OWN108R_BUILD_BEFORE_BIND_ONLY' }
    New-Text (Join-Path $private 'build108R.begun') ([DateTime]::UtcNow.ToString('o'))
    & java -version 2>(Join-Path $archive 'java25.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $archive 'java25.private.txt')) -notmatch 'version "25\.') { throw 'OWN108R_JDK25_REQUIRED' }
    & ant -f (Join-Path $shadow 'build.xml') compile-tests jar 1>(Join-Path $archive 'build108R.log') 2>(Join-Path $archive 'build108R.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_BUILD_FAILED_NO_NATIVE' }
    # Ant's test source root excludes tools/phantom-runtime. These read-only CLI helpers are part of the sealed runtime classpath.
    & javac -cp $cp -d (Join-Path $stage 'build/phantom-test/bin') (Join-Path $shadow 'tools/phantom-runtime/Task104Census.java') (Join-Path $shadow 'tools/phantom-runtime/Task105Census.java') 1>(Join-Path $archive 'census108R-compile.log') 2>(Join-Path $archive 'census108R-compile.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_CENSUS_COMPILE_FAILED_NO_NATIVE' }
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        $file = Physical (Join-Path $stage ('build/phantom-test/bin/' + $name))
        if ((Get-Item -LiteralPath $file).Length -eq 0) { throw 'OWN108R_EMPTY_CENSUS_CLASS' }
    }
    & javap -verbose -classpath $cp Task104Census Task105Census 1>(Join-Path $archive 'census108R-javap.log') 2>(Join-Path $archive 'census108R-javap.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_CENSUS_LINKAGE_FAILED' }
    $bytecode = [IO.File]::ReadAllText((Join-Path $archive 'census108R-javap.log'))
    foreach ($name in @('Task104Census','Task105Census')) { if (!$bytecode.Contains('public final class ' + $name)) { throw 'OWN108R_DEFAULT_PACKAGE_REQUIRED' } }
    New-Text (Join-Path $private 'build108R.complete') 'serial=compile-tests jar+javac census104/105;exit=0'
    Write-Output 'OWN108R_SERIAL_EXTERNAL_BUILD_PASS'
    return
}
if ($Action -eq 'Smoke') {
    if ((Test-Path -LiteralPath $database) -or !(Test-Path -LiteralPath (Join-Path $private 'build108R.complete'))) { throw 'OWN108R_SMOKE_BEFORE_BIND_REQUIRED' }
    Clearance (Join-Path $archive 'before-smoke')
    $empty = Join-Path $archive 'missing-class-fixture'
    New-Item -ItemType Directory -Path $empty | Out-Null
    & java -cp $empty Task105Census snapshot 1>(Join-Path $archive 'missing-class.log') 2>(Join-Path $archive 'missing-class.stderr.private.txt')
    if ($LASTEXITCODE -eq 0 -or [IO.File]::ReadAllText((Join-Path $archive 'missing-class.stderr.private.txt')) -notmatch 'ClassNotFoundException: Task105Census') { throw 'OWN108R_NEGATIVE_CONTROL_FAILED' }
    $classPins = @('Task104Census.class','Task105Census.class') | ForEach-Object { $_ + "`t" + (Hash (Join-Path $stage ('build/phantom-test/bin/' + $_))) }
    New-Text (Join-Path $private 'census108R-classes.tsv') ($classPins -join "`n")
    foreach ($cut in @('first','second')) {
        $out = Join-Path $archive ('smoke-' + $cut)
        New-Item -ItemType Directory -Path $out | Out-Null
        & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R_RUNNABLE_CENSUS_FAILED_NO_NATIVE' }
        $log = [IO.File]::ReadAllText((Join-Path $out 'census.log'))
        if (!$log.Contains('OWN104_CENSUS_PASS READY=5000;') -or !$log.Contains('OWN105_SNAPSHOT_PASS slices=10;') -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108R_NESTED_CENSUS_PROOF_REQUIRED' }
        $checkpoint = @(Get-Content -LiteralPath (Join-Path $out 'durable105.checkpoint'))
        if ($checkpoint.Count -ne 10) { throw 'OWN108R_TEN_SLICES_REQUIRED' }
        for ($i=0; $i -lt 10; $i++) {
            $fields = $checkpoint[$i] -split "`t"
            if ($fields.Count -ne 3 -or $fields[0] -cne [string]$i -or $fields[2] -cne (Hash (Join-Path $out "slice$i.private.txt"))) { throw 'OWN108R_SLICE_DRIFT' }
        }
        if (@(Compare-Object (Get-Content -LiteralPath (Join-Path $out 'ids.tsv')) (Get-Content -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv'))).Count -ne 0) { throw 'OWN108R_ORIGINAL_IDS_DRIFT' }
        Java0
    }
    if ((Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')) -cne (Hash (Join-Path $archive 'smoke-second/durable105.checkpoint'))) { throw 'OWN108R_UNSTABLE_SMOKE' }
    & java -cp $cp Task105Census diff (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') 1>(Join-Path $archive 'census108R-roles.log') 2>(Join-Path $archive 'census108R-roles.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $archive 'census108R-roles.log')) -notmatch 'durableREADY=250;') { throw 'OWN108R_MERCHANT_G0_FAILED' }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomStop108Checks market (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') 1>(Join-Path $archive 'market108R-smoke.log') 2>(Join-Path $archive 'market108R-smoke.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_MARKET_G0_FAILED' }
    Clearance (Join-Path $archive 'after-smoke')
    if ((Hash (Join-Path $archive 'g0/first-preimage.private.tsv')) -cne (Hash (Join-Path $archive 'after-smoke/first-preimage.private.tsv'))) { throw 'OWN108R_SMOKE_DATA_DRIFT' }
    New-Text (Join-Path $private 'smoke108R.complete') ('classpath=' + $cp + "`nclassesSha256=" + (Hash (Join-Path $private 'census108R-classes.tsv')) + "`ncheckpointSha256=" + (Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')))
    Write-Output 'OWN108R_SMOKE_PASS nested104=true;slices=10;READY=5000;roles=250;receipts=71;SQL_WRITES=0;JVM=0;SQL=0;TCP=0'
    return
}
function CensusPins {
    $smoke = Properties (Join-Path $private 'smoke108R.complete')
    if ($smoke.classpath -cne $cp -or $smoke.classesSha256 -cne (Hash (Join-Path $private 'census108R-classes.tsv'))) { throw 'OWN108R_SMOKE_CLASSPATH_DRIFT' }
    $rows = @(Get-Content -LiteralPath (Join-Path $private 'census108R-classes.tsv'))
    if ($rows.Count -ne 2) { throw 'OWN108R_BOTH_CENSUS_PINS_REQUIRED' }
    foreach ($line in $rows) {
        $fields = $line -split "`t"
        if ($fields.Count -ne 2 -or $fields[0] -cnotmatch '^Task10[45]Census\.class$' -or $fields[1] -cne (Hash (Join-Path $stage ('build/phantom-test/bin/' + $fields[0])))) { throw 'OWN108R_CENSUS_CLASS_DRIFT' }
    }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomStop108RChecks census $stage $cp
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_CENSUS_PROVENANCE_FAILED' }
    Java0
}
if ($Action -eq 'Bind') {
    CensusPins
    Clearance (Join-Path $archive 'before-bind')
    if (Test-Path -LiteralPath $database) { throw 'OWN108R_FRESH_PREPARE_ONLY' }
    $prepared = Properties (Join-Path $private 'owner.properties')
    if ($prepared.installationId -cne [IO.File]::ReadAllText((Join-Path $archive 'preparation108R.uuid')) -or (Hash (Join-Path $stage 'inputs.tsv')) -cne $prepared.inputsSha256 -or !(Test-Path -LiteralPath (Join-Path $private 'build108R.complete'))) { throw 'OWN108R_PREPARATION_DRIFT' }
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared108R-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source108R-inputs.tsv')
    $owner = [IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
    $owner = [regex]::Replace($owner, '(?m)^stage=.*$', ('stage=' + $stage.Replace('\', '/')))
    $owner = [regex]::Replace($owner, '(?m)^expiresUtc=.*$', ('expiresUtc=' + [DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'), $owner, [Text.UTF8Encoding]::new($false))
    foreach ($relative in @('inputs.tsv', 'private/db.created')) { Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force }
    Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path $receipts | Out-Null
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime108R-final.ini')
    $ini = [IO.File]::ReadAllText((Join-Path $private 'runtime108R-final.ini'))
    foreach ($key in @('PhantomPopulationActiveTarget', 'MaxMaterializedPhantoms')) {
        $pattern = '(?m)^(' + [regex]::Escape($key) + '[ \t]*=[ \t]*)[^\r\n]*'
        if ([regex]::Matches($ini, $pattern).Count -ne 1) { throw 'OWN108R_OVERLAY_KEY' }
        $ini = [regex]::Replace($ini, $pattern, { param($m) $m.Groups[1].Value + '16' })
    }
    [IO.File]::WriteAllText((Join-Path $game 'config/Custom/PhantomPlayers.ini'), $ini, [Text.UTF8Encoding]::new($false))
    Write-Output 'OWN108R_BOUND originalDB=true;newProfiles=0;privateCap=16;source128=UNCHANGED'
    return
}
if ($Action -eq 'Seal') {
    CensusPins
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108R_CENSUS_ARTIFACTS_REQUIRED_NO_SEAL' }
    }
    $ready = Get-Content -Raw -LiteralPath (Join-Path $archive 'ready108R.private.json') | ConvertFrom-Json
    if (!$ready.dbFreeGreen -or !$ready.sourceAuditComplete -or $ready.reviewP1 -ne 0 -or $ready.reviewP2 -ne 0) { throw 'OWN108R_PRE_NATIVE_GATE_REQUIRED' }
    Clearance (Join-Path $archive 'before-seal')
    if (Test-Path -LiteralPath (Join-Path $private 'recovery108R.properties')) { throw 'OWN108R_SINGLE_SEAL_REQUIRED' }
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
    New-Text (Join-Path $private 'recovery108R.properties') ("task=PHANTOM-MARKET-STOP-RESTART-108R`nnoReseed=true`njarSha256=" + (Hash (Join-Path $shadow 'dist/libs/GameServer.jar')) + "`nsourceOwnerSha256=" + (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json')) + "`nsourceIniSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')) + "`nsourceMerchantSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))
    Write-Output 'OWN108R_SEALED namespace=108R;SAME_DB=true;cap=16;NO_FORCE_ROUTE=true'
    return
}

function Census([string]$Cut) {
    Clearance (Join-Path $archive ('clearance-' + $Cut))
    $out = Join-Path $receipts ('readonly-' + $Cut)
    New-Item -ItemType Directory -Path $out | Out-Null
    & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108R_READONLY_CENSUS_FAILED' }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomStop108Checks market (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive ('clearance-' + $Cut + '/first-preimage.private.tsv')) 1>(Join-Path $out 'market108R.log') 2>(Join-Path $out 'market108R.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R_MARKET_DELTA_REQUIRES_SEPARATE_AUDIT_NO_NEXT_RUNTIME' }
    Java0
    return $out
}
foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
    if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108R_CENSUS_ARTIFACTS_REQUIRED_NO_LAUNCH' }
}
CensusPins
$seal = Properties (Join-Path $private 'recovery108R.properties')
foreach ($pair in @(@('jarSha256', (Join-Path $shadow 'dist/libs/GameServer.jar')), @('sourceOwnerSha256', (Join-Path $module 'dist/game/config/OwnerConfig.json')), @('sourceIniSha256', (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')), @('sourceMerchantSha256', (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))) {
    if ($seal[$pair[0]] -cne (Hash $pair[1])) { throw 'OWN108R_SEAL_DRIFT' }
}
$phase = if ($Action -eq 'A') { 6 } else { 4 }
function NativeEvidence([int]$Generation) {
    if ([IO.File]::ReadAllText((Join-Path $receipts "world$Generation-status.txt")) -notmatch '^actual=16/16;requestedCap=16;' -or
        [IO.File]::ReadAllText((Join-Path $receipts "logout108R-terminal$Generation.txt")) -notmatch '^calls=1;outstanding=0;event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;') { throw 'OWN108R_ACTUAL_WORLD_LOGOUT_REQUIRED' }
    foreach ($cut in @('first','second','prestop','stopped')) {
        if (![IO.File]::ReadAllText((Join-Path $receipts ("merchants107-" + $cut + $Generation + '.txt'))).StartsWith('owners=250;open=250;types={BUY=63, MANUFACTURE=25, SELL=162};')) { throw 'OWN108R_ACTUAL250_AND_SPLIT_REQUIRED' }
    }
}
if (Test-Path -LiteralPath (Join-Path $receipts "launch$phase.begun")) { throw 'OWN108R_SINGLE_GENERATION_REQUIRED' }
if ($Action -eq 'B') {
    NativeEvidence 6
    foreach ($name in @('phase6.complete', 'stopped6.checkpoint', 'stop6-world-leases.txt', 'exit6.clean')) { if (!(Test-Path -LiteralPath (Join-Path $receipts $name))) { throw 'OWN108R_A_FULL_CLEAN_REQUIRED' } }
    if ([IO.File]::ReadAllText((Join-Path $receipts 'exit6.clean')) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;' -or [IO.File]::ReadAllText((Join-Path $receipts 'merchants107-prestop6.txt')) -notmatch ';market=PASS') { throw 'OWN108R_A_NATURAL_CLEAN_MARKET_REQUIRED' }
}
$pre = Census ('before' + $Action)
if ($Action -eq 'B' -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts 'stopped6.checkpoint')).TrimEnd([char[]]"`r`n")) { throw 'OWN108R_PRESTART_CHECKPOINT_DRIFT' }
New-Text (Join-Path $receipts "launch$phase.begun") ('utc=' + [DateTime]::UtcNow.ToString('o'))
$uuid = $original.installationId
$arguments = @('-Xms512m', '-Xmx8g', '-Dphantom.task102.native=true', "-Dphantom.task102.stage=$stage", "-Dphantom.task102.uuid=$uuid", "-Dphantom.module.root=$shadow", "-Dphantom.test.config=$database", '-cp', $cp, 'org.l2jmobius.tests.phantoms.PhantomPopulation102Native', ('recovery108R' + $Action))
$line = ($arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
$process = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
$os = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $process.Id)
if (!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)) { throw 'OWN108R_NEW_PROCESS_IDENTITY_UNPROVEN_NO_RETRY' }
$digest = [Security.Cryptography.SHA256]::Create()
try { $commandHash = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-', '').ToLowerInvariant() } finally { $digest.Dispose() }
New-Text (Join-Path $receipts "process$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256 } | ConvertTo-Json)
Write-Output "OWN108R_NATIVE_STARTED generation=$Action;cap=16;newProfiles=FORBIDDEN"
$deadline = [DateTime]::UtcNow.AddMinutes(12)
while (!$process.WaitForExit(1000)) {
    if ((Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")) -or [DateTime]::UtcNow -gt $deadline) {
        New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'STOP_BLOCKED_NO_RETRY'; forceApproval = $false } | ConvertTo-Json)
        throw 'OWN108R_STOP_BLOCKED;NO_B;NO_RETRY;NO_FORCE'
    }
}
$exit = $process.ExitCode
New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc=" + [DateTime]::UtcNow.ToString('o'))
if ($exit -ne 0) { throw 'OWN108R_NATIVE_EXIT_FAILED_NO_NEXT_RUNTIME' }
Java0
$post = Census ('after' + $Action)
$boundary = [IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))
if (!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
    $boundary -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=([0-9]+);attributedMerchants=([0-9]+);samples=2;nativePersisted=true\r?\n?$' -or $matches[1] -cne $matches[2]) { throw 'OWN108R_FULL_NATIVE_STOP_PROOF_REQUIRED' }
if ([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")) { throw 'OWN108R_POSTEXIT_CHECKPOINT_DRIFT' }
NativeEvidence $phase
New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;natural=true;jarSha256=" + $seal.jarSha256)
Protected
Write-Output "OWN108R_GENERATION_CLEAN generation=$Action;cap=16;JVM=0;SQL=0;TCP=0;allResidualLeasesAttributed=true"
