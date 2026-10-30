[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit','Prepare','Build','Bind','Smoke','Seal','A','B','AcceptA','AcceptB')][string]$Action,
    [string]$SqlClient = (Join-Path $env:ProgramFiles 'MariaDB 11.4/bin/mariadb.exe')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Hold-ProcessHandle([Diagnostics.Process]$Process) {
    if ($null -eq $Process) { throw 'OWN108-COMPLETE_PROCESS_REQUIRED' }
    # Start-Process redirection on Windows PowerShell 5.1 can lose ExitCode unless
    # this Process owns its cached handle before waiting (PowerShell issue 5421).
    $handle = $Process.Handle
    if ($null -eq $handle -or $handle -isnot [IntPtr] -or $handle -eq [IntPtr]::Zero) { throw 'OWN108-COMPLETE_PROCESS_HANDLE_UNAVAILABLE' }
    return $handle
}
function Assert-ObservedExitCode([object]$Value) {
    # Do not cast: PowerShell converts a null value to integer zero.
    if ($null -eq $Value -or $Value -isnot [int]) { throw 'OWN108-COMPLETE_EXIT_STATUS_UNKNOWN' }
    return $Value
}
function Get-TrackedProcessExitCode([Diagnostics.Process]$Process, [object]$Handle) {
    if ($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero) { throw 'OWN108-COMPLETE_EXIT_HANDLE_UNKNOWN' }
    if (!$Process.HasExited) { throw 'OWN108-COMPLETE_PROCESS_NOT_EXITED' }
    return Assert-ObservedExitCode $Process.ExitCode
}
function Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    for ($walk = $item; $null -ne $walk; $walk = if ($walk -is [IO.FileInfo]) { $walk.Directory } else { $walk.Parent }) {
        if ($walk.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'OWN108-COMPLETE_REPARSE' }
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
    if (!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid) { throw 'OWN108-COMPLETE_PRIVATE_OWNER_REQUIRED' }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid, 'S-1-5-18')) { throw 'OWN108-COMPLETE_FOREIGN_ACL' }
    }
}
function Private-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'OWN108-COMPLETE_FRESH_DIRECTORY_REQUIRED' }
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
    if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0) { throw 'OWN108-COMPLETE_JAVA_PRESENT_NO_RUNTIME' }
    if (@(Get-NetTCPConnection -ErrorAction Stop | Where-Object { $_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308) }).Count -ne 0) { throw 'OWN108-COMPLETE_TCP_PRESENT_NO_RUNTIME' }
}

$module = Physical (Join-Path $PSScriptRoot '../..')
$repo = Physical (Split-Path -Parent $module)
if ((Physical ((& git -C $repo rev-parse --show-toplevel).Trim())) -ine $repo) { throw 'OWN108-COMPLETE_GIT_ROOT' }
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior = Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105/resume105.private.json') -Raw | ConvertFrom-Json
$origin = Physical $prior.origin
$original = Properties (Join-Path $origin 'private/owner.properties')
$client = Physical $SqlClient
$options = Physical (Join-Path $prior.archive 'sql-client.private.cnf')
$index = Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-COMPLETE-108/native'
$history = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-STOP-RESTART-108R/index.private.json') | ConvertFrom-Json
$historyBefore = Physical (Join-Path $history.archive 'g0/first-preimage.private.tsv')
$historyAfter = Physical (Join-Path $history.archive 'clearance-afterA/first-preimage.private.tsv')
$historyWitness = Physical (Join-Path $history.stage 'private/receipts108R/merchants107-prestop6.private.tsv')
$previousR2 = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-DELTA-RESTART-108R2/index.private.json') | ConvertFrom-Json
$recoveryAfter = Physical (Join-Path $previousR2.archive 'blockedA/first-preimage.private.tsv')
$recoveryWitness = Physical (Join-Path $previousR2.stage 'private/receipts108R2/merchants107-second6.private.tsv')
$r2After = $recoveryAfter
$r2Witness = $recoveryWitness
$previousC = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-COHORT-RECOVERY-108R3C/index.private.json') | ConvertFrom-Json
$recoveryAfter = Physical (Join-Path $previousC.archive 'diagnostic-postexit/first-preimage.utf8.private.tsv')
$recoveryWitness = Physical (Join-Path $previousC.stage 'private/receipts108R3C/merchants107-prestop6.private.tsv')
$previousD = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-RESTART-CLOSURE-108R3D/index.private.json') | ConvertFrom-Json
$recoveryAfter = Physical (Join-Path $previousD.archive 'clearance-afterB/first-preimage.private.tsv')
$recoveryWitness = Physical (Join-Path $previousD.stage 'private/receipts108R3D/merchants107-prestop4.private.tsv')
$ids = Physical (Join-Path $origin 'private/receipts-continuation2/ids4.tsv')
$sourcePolicy = Physical (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')
$checkerSha = Hash (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomMarketDelta108R2Checks.java')
$priorR3 = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-COHORT-RECOVERY-108R3/index.private.json') | ConvertFrom-Json
foreach ($root in @($origin, (Physical $prior.archive), (Physical $prior.resume))) { Assert-Private $root }
if ($original.owner -cne 'TASK102_POPULATION_SCALE' -or $original.schema -cne 'l2jmobiush5_phantom_test_102' -or $original.ownerSid -cne $sid -or
    (Physical $original.sourceModule) -ine $module -or (Hash (Join-Path $origin 'inputs.tsv')) -cne $original.inputsSha256 -or
    ![IO.File]::ReadAllText((Physical (Join-Path $origin 'private/db.created'))).StartsWith($original.installationId + "`n") -or
    (Hash (Join-Path $module 'dist/game/config/Database.ini')) -cne $original.sourceDatabaseSha256) { throw 'OWN108-COMPLETE_ORIGINAL_AUTHORITY_DRIFT' }

function Sql([string]$Query, [string]$Output) {
    # Fixed read-only callers only. Keep credentials and complete snapshots outside checkout.
    $rows = @($Query | & $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>($Output + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_READONLY_SQL_FAILED' }
    New-Text $Output (($rows -join "`n") + "`n")
    return $rows
}
function Capture-Skills([string]$Output) {
    $members = @(Get-Content -LiteralPath $ids | ForEach-Object {
        $fields = $_ -split "`t"
        if ($fields.Count -ne 3 -or $fields[1] -cnotmatch '^[1-9][0-9]*$') { throw 'OWN108_COMPLETE_EXACT_IDS_REQUIRED' }
        $fields[1]
    })
    if ($members.Count -ne 5000 -or @($members | Sort-Object -Unique).Count -ne 5000) { throw 'OWN108_COMPLETE_EXACT5000_REQUIRED' }
    $null = Sql ('SELECT charId,class_index,skill_id,skill_level FROM character_skills WHERE charId IN (' + ($members -join ',') + ') ORDER BY charId,class_index,skill_id;') $Output
}
function Clearance([string]$Output) {
    Java0
    New-Item -ItemType Directory -Path $Output | Out-Null
    $grants = @(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $Output 'grants.private.tsv'))
    if (!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')) { throw 'OWN108-COMPLETE_FULL_VISIBILITY_REQUIRED' }
    $authority = @(Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' (Join-Path $Output 'authority.private.tsv'))
    if ($authority.Count -ne 1 -or $authority[0] -cne ($original.installationId + "`t" + $original.sourceHead + "`t" + $original.inputsSha256)) { throw 'OWN108-COMPLETE_SQL_AUTHORITY_DRIFT' }
    $sessions = @(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $Output 'sessions.private.tsv'))
    if ($sessions.Count -ne 0) { throw 'OWN108-COMPLETE_FOREIGN_OR_OWN_SQL_PRESENT' }
    foreach ($cut in @('first', 'second')) {
        $null = Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $Output ($cut + '-preimage.private.tsv'))
    }
    if ((Hash (Join-Path $Output 'first-preimage.private.tsv')) -cne (Hash (Join-Path $Output 'second-preimage.private.tsv'))) { throw 'OWN108-COMPLETE_UNSTABLE_PREIMAGE' }
    Java0
    New-Text (Join-Path $Output 'clearance.json') (@{ JVM = 0; SQL = 0; TCP = 0; foreign = 0; fullVisibility = $true; authorityExact = $true; stable = $true; utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json)
}

if ($Action -eq 'Audit') {
    Java0
    $head = (& git -C $repo rev-parse HEAD).Trim()
    $branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
    $tracking = (& git -C $repo rev-parse refs/remotes/origin/feature/phantom-world).Trim()
    # Live parity was checked once in the task preflight. Freeze that exact base until delivery.
    & git -C $repo merge-base --is-ancestor 11def7f283d90190d014b6635dfff8b0689401ea HEAD
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feature/phantom-world' -or $tracking -cne $head -or $head -cne '11def7f283d90190d014b6635dfff8b0689401ea') { throw 'OWN108-COMPLETE_FRESH_PARITY_REQUIRED' }
    Private-Directory $index
    $archive = Join-Path ([IO.Path]::GetTempPath()) ('TASK108-COMPLETE-' + [guid]::NewGuid().ToString('N'))
    Private-Directory $archive
    $campaign = [guid]::NewGuid().ToString()
    New-Text (Join-Path $index 'index.private.json') (@{head=$head;archive=$archive;stage=(Join-Path $archive 'shadow108-COMPLETE');campaignUuid=$campaign;utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json)
    if ((Hash (Join-Path $previousC.stage 'private/native-inputs.tsv')) -cne 'd0c20df68ef58785e16e2811b181bc0bbc829a8e155e7545edf7e8df07f9fb98' -or
        (Hash $recoveryAfter) -cne '036bff45b95fae570ad7b474ad4022b45dabf81ad79ca35d587ce1a82f342b3d' -or
        (Test-Path -LiteralPath (Join-Path $previousC.stage 'private/receipts108R3C/exit6.clean'))) { throw 'OWN108-COMPLETE_PRIOR_C_EVIDENCE_DRIFT' }
    $production = [Collections.Generic.List[string]]::new()
    $prefix = 'L2J_Mobius_CT_2.6_HighFive/java/'
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $previousC.stage 'private/native-inputs.tsv'))) {
        $fields = $line -split "`t"
        if ($fields[0].StartsWith($prefix, [StringComparison]::Ordinal)) {
            $allowed = @('L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.java', 'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/model/actor/stat/PlayerStat.java')
            $actual = Hash (Join-Path $repo $fields[0])
            if ($fields.Count -ne 2 -or ($actual -cne $fields[1] -and $fields[0] -cnotin $allowed)) { throw 'OWN108-COMPLETE_PRODUCTION_SOURCE_DRIFT' }
            $production.Add($fields[0] + "`t" + $actual)
        }
    }
    if ($production.Count -lt 1000) { throw 'OWN108-COMPLETE_PRODUCTION_PIN_COUNT' }
    New-Text (Join-Path $archive 'production-source-pins.tsv') ($production -join "`n")
    $allPins = Get-Content -Raw -LiteralPath (Join-Path $previousC.archive 'protected-final108R3C.private.json') | ConvertFrom-Json
    foreach ($pin in $allPins) { if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108-COMPLETE_PROTECTED_SOURCE_DRIFT' } }
    Clearance (Join-Path $archive 'g0')
    Capture-Skills (Join-Path $archive 'g0/skills.private.tsv')
    # SQL client line terminators differ between the old PS5 redirect and New-Text.
    # Every line/field must match; no character/progress/inventory delta is accepted here.
    $before = [IO.File]::ReadAllLines($recoveryAfter)
    $after = [IO.File]::ReadAllLines((Join-Path $archive 'g0/first-preimage.private.tsv'))
    if ($before.Length -ne $after.Length) { throw 'OWN108-COMPLETE_G0_CONTENT_DRIFT' }
    for ($i=0; $i -lt $before.Length; $i++) { if ($before[$i] -cne $after[$i]) { throw 'OWN108-COMPLETE_G0_CONTENT_DRIFT' } }
    $priorCP = ((Join-Path $previousC.stage 'build/phantom-test/bin') + ';' + (Join-Path $previousC.stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/*'))
    & java -Xmx2g -cp $priorCP org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $recoveryAfter (Join-Path $archive 'g0/first-preimage.private.tsv') $ids $recoveryWitness $sourcePolicy 1>(Join-Path $archive 'market-g0.log') 2>(Join-Path $archive 'market-g0.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_G0_MARKET_FAILED' }
    & java -Xmx2g -cp $priorCP Task105Census diff $recoveryAfter (Join-Path $archive 'g0/first-preimage.private.tsv') $ids 1>(Join-Path $archive 'identities-g0.log') 2>(Join-Path $archive 'identities-g0.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $archive 'identities-g0.log')) -notmatch 'durableREADY=250;') { throw 'OWN108-COMPLETE_G0_IDENTITIES_FAILED' }
    $oldHistory = Physical (Join-Path $previousC.archive ('historical-' + $checkerSha + '.pass'))
    [IO.File]::Copy($oldHistory, (Join-Path $archive ('historical-' + $checkerSha + '.pass')), $false)
    New-Text (Join-Path $archive 'protected-final108-COMPLETE.private.json') ($allPins | ConvertTo-Json -Depth 5)
    New-Text (Join-Path $archive 'preparation108-COMPLETE.uuid') $campaign
    [IO.File]::Copy((Join-Path $module 'dist/game/config/OwnerConfig.json'), (Join-Path $archive 'source-owner108-COMPLETE.private.json'), $false)
    New-Text (Join-Path $archive 'audit.complete') 'G0_108-COMPLETE_PASS;original102=EXACT;stable=true;JVM=0;SQL=0;TCP=0;foreign=0;SQL_WRITES=0'
    Java0
    Write-Output 'G0_108-COMPLETE_PASS original102=EXACT;5000_READY=EXACT;roles=250;market=AUTHENTICATED;stable=true;JVM=0;SQL=0;TCP=0;foreign=0;SQL_WRITES=0'
    return
}
Assert-Private $index
$info = Get-Content -Raw -LiteralPath (Join-Path $index 'index.private.json') | ConvertFrom-Json
$archive = Physical $info.archive
Assert-Private $archive
$stage = $info.stage
$historical = Properties (Join-Path $archive ('historical-' + $checkerSha + '.pass'))
if ($historical.checkerSha256 -cne $checkerSha -or $historical.before -cne (Hash $historyBefore) -or $historical.after -cne (Hash $historyAfter) -or $historical.witness -cne (Hash $historyWitness) -or $historical.sourcePolicy -cne (Hash $sourcePolicy)) { throw 'OWN108-COMPLETE_HISTORICAL_PROOF_DRIFT' }
function Protected {
    if (!(Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN108-COMPLETE_ACCEPTED_G0_REQUIRED' }
    foreach ($pin in (Get-Content -Raw -LiteralPath (Join-Path $archive 'protected-final108-COMPLETE.private.json') | ConvertFrom-Json)) { if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108-COMPLETE_PROTECTED_SOURCE_DRIFT' } }
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $archive 'production-source-pins.tsv'))) { $fields=$line -split "`t"; if ((Hash (Join-Path $repo $fields[0])) -cne $fields[1]) { throw 'OWN108-COMPLETE_PRODUCTION_SOURCE_DRIFT' } }
    if ((Hash (Join-Path $module 'dist/game/config/OwnerConfig.json')) -cne (Hash (Join-Path $archive 'source-owner108-COMPLETE.private.json')) -or $info.head -cne (& git -C $repo rev-parse HEAD).Trim()) { throw 'OWN108-COMPLETE_SOURCE_HEAD_OWNER_DRIFT' }
}
if ($Action -eq 'Prepare') {
    Protected
    Clearance (Join-Path $archive 'before-prepare')
    & (Join-Path $PSScriptRoot 'Task102.ps1') -Action Prepare -SourceRoot $module -StageRoot $stage -InstallationId $info.campaignUuid
    if (!$?) { throw 'OWN108-COMPLETE_PREPARE_FAILED' }
    return
}
Java0; Protected
$stage = Physical $stage; Assert-Private $stage
$shadow = Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
$private = Physical (Join-Path $stage 'private')
$game = Physical (Join-Path $shadow 'dist/game')
$database = Join-Path $game 'config/Database.ini'
$receipts = Join-Path $private 'receipts108-COMPLETE'
$cp = ((Join-Path $stage 'build/phantom-test/bin') + ';' + (Join-Path $stage 'build/phantom-test/resources') + ';' + (Join-Path $shadow 'dist/libs/*')).Replace('\', '/')
if ($Action -eq 'Build') {
    if (Test-Path -LiteralPath $database) { throw 'OWN108-COMPLETE_BUILD_BEFORE_BIND_ONLY' }
    New-Text (Join-Path $private 'build108-COMPLETE.begun') ([DateTime]::UtcNow.ToString('o'))
    # Windows PowerShell 5 wraps normal native stderr as ErrorRecord even for exit0.
    $ErrorActionPreference = 'Continue'
    try { & java -version 2>(Join-Path $archive 'java25.private.txt'); $versionExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($versionExit -ne 0 -or [IO.File]::ReadAllText((Join-Path $archive 'java25.private.txt')) -notmatch 'version "25\.') { throw 'OWN108-COMPLETE_JDK25_REQUIRED' }
    & ant -f (Join-Path $shadow 'build.xml') compile-tests jar 1>(Join-Path $archive 'build108-COMPLETE.log') 2>(Join-Path $archive 'build108-COMPLETE.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_BUILD_FAILED_NO_NATIVE' }
    # Ant's test source root excludes tools/phantom-runtime. These read-only CLI helpers are part of the sealed runtime classpath.
    & javac -cp $cp -d (Join-Path $stage 'build/phantom-test/bin') (Join-Path $shadow 'tools/phantom-runtime/Task104Census.java') (Join-Path $shadow 'tools/phantom-runtime/Task105Census.java') 1>(Join-Path $archive 'census108-COMPLETE-compile.log') 2>(Join-Path $archive 'census108-COMPLETE-compile.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_CENSUS_COMPILE_FAILED_NO_NATIVE' }
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        $file = Physical (Join-Path $stage ('build/phantom-test/bin/' + $name))
        if ((Get-Item -LiteralPath $file).Length -eq 0) { throw 'OWN108-COMPLETE_EMPTY_CENSUS_CLASS' }
    }
    & javap -verbose -classpath $cp Task104Census Task105Census 1>(Join-Path $archive 'census108-COMPLETE-javap.log') 2>(Join-Path $archive 'census108-COMPLETE-javap.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_CENSUS_LINKAGE_FAILED' }
    $bytecode = [IO.File]::ReadAllText((Join-Path $archive 'census108-COMPLETE-javap.log'))
    foreach ($name in @('Task104Census','Task105Census')) { if (!$bytecode.Contains('public final class ' + $name)) { throw 'OWN108-COMPLETE_DEFAULT_PACKAGE_REQUIRED' } }
    New-Text (Join-Path $private 'build108-COMPLETE.complete') 'serial=compile-tests jar+javac census104/105;exit=0'
    foreach ($step in @('source-Verify','stage-Apply1','stage-Verify1','stage-Apply2','stage-Verify2')) {
        $root = if ($step.StartsWith('source')) { Join-Path $module 'dist/game' } else { $game }
        $operation = if ($step.Contains('Apply')) { 'Apply' } else { 'Verify' }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $module 'tools/OwnerConfig.ps1') -Action $operation -GameRoot $root -ProfilePath (Join-Path $module 'dist/game/config/OwnerConfig.json') 1>(Join-Path $archive ($step + '.log')) 2>(Join-Path $archive ($step + '.stderr.private.txt'))
        if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_OWNER_CHAIN_FAILED' }
        $ownerLog = [IO.File]::ReadAllText((Join-Path $archive ($step + '.log')))
        if ($ownerLog -notmatch '287' -or $ownerLog -notmatch 'conflicts=0' -or ($step -eq 'stage-Apply2' -and $ownerLog -notmatch 'changed=0')) { throw 'OWN108-COMPLETE_OWNER_287_REPEAT0_REQUIRED' }
    }
    New-Text (Join-Path $private 'owner108-COMPLETE.complete') 'sourceVerify+Apply+Verify+Apply0+Verify;overrides=287;source=UNCHANGED'
    Write-Output 'OWN108-COMPLETE_SERIAL_EXTERNAL_BUILD_PASS'
    return
}
if ($Action -eq 'Smoke') {
    if ((Test-Path -LiteralPath $database) -or !(Test-Path -LiteralPath (Join-Path $private 'build108-COMPLETE.complete'))) { throw 'OWN108-COMPLETE_SMOKE_BEFORE_BIND_REQUIRED' }
    Clearance (Join-Path $archive 'before-smoke')
    $empty = Join-Path $archive 'missing-class-fixture'
    New-Item -ItemType Directory -Path $empty | Out-Null
    $ErrorActionPreference = 'Continue'
    try { & java -cp $empty Task105Census snapshot 1>(Join-Path $archive 'missing-class.log') 2>(Join-Path $archive 'missing-class.stderr.private.txt'); $missingExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($missingExit -eq 0 -or [IO.File]::ReadAllText((Join-Path $archive 'missing-class.stderr.private.txt')) -notmatch 'ClassNotFoundException: Task105Census') { throw 'OWN108-COMPLETE_NEGATIVE_CONTROL_FAILED' }
    $classPins = @('Task104Census.class','Task105Census.class') | ForEach-Object { $_ + "`t" + (Hash (Join-Path $stage ('build/phantom-test/bin/' + $_))) }
    New-Text (Join-Path $private 'census108-COMPLETE-classes.tsv') ($classPins -join "`n")
    foreach ($cut in @('first','second')) {
        $out = Join-Path $archive ('smoke-' + $cut)
        New-Item -ItemType Directory -Path $out | Out-Null
        & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_RUNNABLE_CENSUS_FAILED_NO_NATIVE' }
        $log = [IO.File]::ReadAllText((Join-Path $out 'census.log'))
        if (!$log.Contains('OWN104_CENSUS_PASS READY=5000;') -or !$log.Contains('OWN105_SNAPSHOT_PASS slices=10;') -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108-COMPLETE_NESTED_CENSUS_PROOF_REQUIRED' }
        $checkpoint = @(Get-Content -LiteralPath (Join-Path $out 'durable105.checkpoint'))
        if ($checkpoint.Count -ne 10) { throw 'OWN108-COMPLETE_TEN_SLICES_REQUIRED' }
        for ($i=0; $i -lt 10; $i++) {
            $fields = $checkpoint[$i] -split "`t"
            if ($fields.Count -ne 3 -or $fields[0] -cne [string]$i -or $fields[2] -cne (Hash (Join-Path $out "slice$i.private.txt"))) { throw 'OWN108-COMPLETE_SLICE_DRIFT' }
        }
        if (@(Compare-Object (Get-Content -LiteralPath (Join-Path $out 'ids.tsv')) (Get-Content -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv'))).Count -ne 0) { throw 'OWN108-COMPLETE_ORIGINAL_IDS_DRIFT' }
        Java0
    }
    if ((Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')) -cne (Hash (Join-Path $archive 'smoke-second/durable105.checkpoint'))) { throw 'OWN108-COMPLETE_UNSTABLE_SMOKE' }
    & java -cp $cp Task105Census diff (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') 1>(Join-Path $archive 'census108-COMPLETE-roles.log') 2>(Join-Path $archive 'census108-COMPLETE-roles.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_MERCHANT_G0_FAILED' }
    if ([IO.File]::ReadAllText((Join-Path $archive 'census108-COMPLETE-roles.log')) -notmatch 'durableREADY=250;' -or (Get-Item -LiteralPath (Join-Path $archive 'census108-COMPLETE-roles.stderr.private.txt')).Length -ne 0) { throw 'OWN108-COMPLETE_MERCHANT_G0_FAILED' }
    $marketLog = 'market108-COMPLETE-smoke'
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') $ids $recoveryWitness $sourcePolicy 1>(Join-Path $archive ($marketLog + '.log')) 2>(Join-Path $archive ($marketLog + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_MARKET_G0_FAILED' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks historical $historyBefore $historyAfter $ids $historyWitness $sourcePolicy 1>(Join-Path $archive 'market108-COMPLETE-sameCP-historical.log') 2>(Join-Path $archive 'market108-COMPLETE-sameCP-historical.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_REAL_HISTORICAL_SAME_CLASSPATH_FAILED' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $historyAfter $r2After $ids $r2Witness $sourcePolicy 1>(Join-Path $archive 'market108-COMPLETE-sameCP78-82.log') 2>(Join-Path $archive 'market108-COMPLETE-sameCP78-82.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_REAL78_82_SAME_CLASSPATH_FAILED' }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks 1>(Join-Path $archive 'cohort108-COMPLETE-sameCP.log') 2>(Join-Path $archive 'cohort108-COMPLETE-sameCP.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_DB_FREE_COHORT_FAILED' }
    Clearance (Join-Path $archive 'after-smoke')
    if ((Hash (Join-Path $archive 'g0/first-preimage.private.tsv')) -cne (Hash (Join-Path $archive 'after-smoke/first-preimage.private.tsv'))) { throw 'OWN108-COMPLETE_SMOKE_DATA_DRIFT' }
    New-Text (Join-Path $private 'smoke108-COMPLETE.complete') ('classpath=' + $cp + "`nclassesSha256=" + (Hash (Join-Path $private 'census108-COMPLETE-classes.tsv')) + "`ncheckpointSha256=" + (Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')))
    Write-Output 'OWN108-COMPLETE_SMOKE_PASS nested104=true;slices=10;READY=5000;roles=250;market=VERIFIED;SQL_WRITES=0;JVM=0;SQL=0;TCP=0'
    return
}
function CensusPins {
    $smoke = Properties (Join-Path $private 'smoke108-COMPLETE.complete')
    if ($smoke.classpath -cne $cp -or $smoke.classesSha256 -cne (Hash (Join-Path $private 'census108-COMPLETE-classes.tsv'))) { throw 'OWN108-COMPLETE_SMOKE_CLASSPATH_DRIFT' }
    $rows = @(Get-Content -LiteralPath (Join-Path $private 'census108-COMPLETE-classes.tsv'))
    if ($rows.Count -ne 2) { throw 'OWN108-COMPLETE_BOTH_CENSUS_PINS_REQUIRED' }
    foreach ($line in $rows) {
        $fields = $line -split "`t"
        if ($fields.Count -ne 2 -or $fields[0] -cnotmatch '^Task10[45]Census\.class$' -or $fields[1] -cne (Hash (Join-Path $stage ('build/phantom-test/bin/' + $fields[0])))) { throw 'OWN108-COMPLETE_CENSUS_CLASS_DRIFT' }
    }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks census $stage $cp
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_CENSUS_PROVENANCE_FAILED' }
    Java0
}
if ($Action -eq 'Bind') {
    if (!(Test-Path -LiteralPath (Join-Path $private 'owner108-COMPLETE.complete'))) { throw 'OWN108-COMPLETE_OWNER_CHAIN_REQUIRED' }
    CensusPins
    Clearance (Join-Path $archive 'before-bind')
    if (Test-Path -LiteralPath $database) { throw 'OWN108-COMPLETE_FRESH_PREPARE_ONLY' }
    $prepared = Properties (Join-Path $private 'owner.properties')
    if ($prepared.installationId -cne [IO.File]::ReadAllText((Join-Path $archive 'preparation108-COMPLETE.uuid')) -or (Hash (Join-Path $stage 'inputs.tsv')) -cne $prepared.inputsSha256 -or !(Test-Path -LiteralPath (Join-Path $private 'build108-COMPLETE.complete'))) { throw 'OWN108-COMPLETE_PREPARATION_DRIFT' }
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared108-COMPLETE-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source108-COMPLETE-inputs.tsv')
    $owner = [IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
    $owner = [regex]::Replace($owner, '(?m)^stage=.*$', ('stage=' + $stage.Replace('\', '/')))
    $owner = [regex]::Replace($owner, '(?m)^expiresUtc=.*$', ('expiresUtc=' + [DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'), $owner, [Text.UTF8Encoding]::new($false))
    foreach ($relative in @('inputs.tsv', 'private/db.created')) { Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force }
    Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path $receipts | Out-Null
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime108-COMPLETE-final.ini')
    $ini = [IO.File]::ReadAllText((Join-Path $private 'runtime108-COMPLETE-final.ini'))
    foreach ($key in @('PhantomPopulationActiveTarget', 'MaxMaterializedPhantoms')) {
        $pattern = '(?m)^(' + [regex]::Escape($key) + '[ \t]*=[ \t]*)[^\r\n]*'
        if ([regex]::Matches($ini, $pattern).Count -ne 1) { throw 'OWN108-COMPLETE_OVERLAY_KEY' }
        $ini = [regex]::Replace($ini, $pattern, { param($m) $m.Groups[1].Value + '16' })
    }
    [IO.File]::WriteAllText((Join-Path $game 'config/Custom/PhantomPlayers.ini'), $ini, [Text.UTF8Encoding]::new($false))
    Write-Output 'OWN108-COMPLETE_BOUND originalDB=true;newProfiles=0;privateCap=16;source128=UNCHANGED'
    return
}
if ($Action -eq 'Seal') {
    CensusPins
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108-COMPLETE_CENSUS_ARTIFACTS_REQUIRED_NO_SEAL' }
    }
    $ready = Get-Content -Raw -LiteralPath (Join-Path $archive 'ready108-COMPLETE.private.json') | ConvertFrom-Json
    if (!$ready.dbFreeGreen -or !$ready.sourceAuditComplete -or $ready.reviewP1 -ne 0 -or $ready.reviewP2 -ne 0) { throw 'OWN108-COMPLETE_PRE_NATIVE_GATE_REQUIRED' }
    Clearance (Join-Path $archive 'before-seal')
    if (Test-Path -LiteralPath (Join-Path $private 'recovery108-COMPLETE.properties')) { throw 'OWN108-COMPLETE_SINGLE_SEAL_REQUIRED' }
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
    New-Text (Join-Path $private 'recovery108-COMPLETE.properties') ("task=PHANTOM-WORLD-COMPLETE-108`nnoReseed=true`ncampaignUuid=" + $info.campaignUuid + "`njarSha256=" + (Hash (Join-Path $shadow 'dist/libs/GameServer.jar')) + "`nsourceOwnerSha256=" + (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json')) + "`nsourceIniSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')) + "`nsourceMerchantSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))
    Write-Output 'OWN108-COMPLETE_SEALED namespace=108-COMPLETE;SAME_DB=true;cap=16;NO_FORCE_ROUTE=true'
    return
}

function Census([string]$Cut) {
    Clearance (Join-Path $archive ('clearance-' + $Cut))
    $out = Join-Path $receipts ('readonly-' + $Cut)
    New-Item -ItemType Directory -Path $out | Out-Null
    & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108-COMPLETE_READONLY_CENSUS_FAILED' }
    $before = switch ($Cut) { 'beforeA' { Join-Path $archive 'g0/first-preimage.private.tsv' } 'afterA' { Join-Path $archive 'clearance-beforeA/first-preimage.private.tsv' } 'beforeB' { Join-Path $archive 'clearance-afterA/first-preimage.private.tsv' } 'afterB' { Join-Path $archive 'clearance-beforeB/first-preimage.private.tsv' } default { throw 'OWN108-COMPLETE_EXACT_CUT' } }
    $witness = switch ($Cut) { 'beforeA' { $recoveryWitness } 'beforeB' { Join-Path $receipts 'merchants107-prestop6.private.tsv' } 'afterA' { Join-Path $receipts 'merchants107-prestop6.private.tsv' } 'afterB' { Join-Path $receipts 'merchants107-prestop4.private.tsv' } }
    $after = Join-Path $archive ('clearance-' + $Cut + '/first-preimage.private.tsv')
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $before $after $ids $witness $sourcePolicy 1>(Join-Path $out 'market108-COMPLETE.log') 2>(Join-Path $out 'market108-COMPLETE.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108-COMPLETE_MARKET_DELTA_REQUIRES_SEPARATE_AUDIT_NO_NEXT_RUNTIME' }
    & java -Xmx2g -cp $cp Task105Census diff $before $after $ids 1>(Join-Path $out 'delta105.log') 2>(Join-Path $out 'delta105.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'delta105.log')) -notmatch 'durableREADY=250;') { throw 'OWN108-COMPLETE_DURABLE_IDENTITY_DELTA_FAILED' }
    # The old negativeRows signal stays intact. Every SP debit now requires independent causal evidence.
    $skills = Join-Path $out 'skills.private.tsv'
    Capture-Skills $skills
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomComplete108Checks sidecar $skills $ids (Join-Path $out 'skills.properties')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108_COMPLETE_SKILLS_SIDECAR_FAILED' }
    $oldSkills = switch ($Cut) {
        'beforeA' { Join-Path $archive 'g0/skills.private.tsv' }
        'afterA' { Join-Path $receipts 'readonly-beforeA/skills.private.tsv' }
        'beforeB' { Join-Path $receipts 'readonly-afterA/skills.private.tsv' }
        'afterB' { Join-Path $receipts 'readonly-beforeB/skills.private.tsv' }
    }
    $events = if ($Cut -eq 'afterA') { Join-Path $receipts 'skills108-6.events.private.tsv' } elseif ($Cut -eq 'afterB') { Join-Path $receipts 'skills108-4.events.private.tsv' } else { 'NONE' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomComplete108Checks audit $before $after $oldSkills $skills $ids $events $shadow 1>(Join-Path $out 'skills-audit.log') 2>(Join-Path $out 'skills-audit.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108_COMPLETE_SP_SKILLS_CAUSAL_AUDIT_FAILED_NO_NEXT_NATIVE' }
    Java0
    return $out
}
foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
    if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108-COMPLETE_CENSUS_ARTIFACTS_REQUIRED_NO_LAUNCH' }
}
CensusPins
$seal = Properties (Join-Path $private 'recovery108-COMPLETE.properties')
foreach ($pair in @(@('jarSha256', (Join-Path $shadow 'dist/libs/GameServer.jar')), @('sourceOwnerSha256', (Join-Path $module 'dist/game/config/OwnerConfig.json')), @('sourceIniSha256', (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')), @('sourceMerchantSha256', (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))) {
    if ($seal[$pair[0]] -cne (Hash $pair[1])) { throw 'OWN108-COMPLETE_SEAL_DRIFT' }
}
$phase = if ($Action -in @('A','AcceptA')) { 6 } else { 4 }
function NativeEvidence([int]$Generation) {
    if ([IO.File]::ReadAllText((Join-Path $receipts "phase$Generation.complete")) -notmatch ';controlledHumans=2\r?\n?$') { throw 'OWN108_COMPLETE_TWO_OBSERVERS_REQUIRED' }
    foreach ($cut in @('start','final')) {
        $humans = @(Get-Content -LiteralPath (Join-Path $receipts ("cohort108R3-$Generation-$cut.private.tsv")) | Where-Object { $_.StartsWith("OBSERVER`t") })
        if ($humans.Count -ne 2 -or @($humans | Where-Object { $_ -notmatch ';worldCurrent=true;online=true;' -or $_ -notmatch ';instance=0;lease=OwnerSnapshot\[.*ownerKind=LOCALPLAY_TEST_HUMAN' }).Count -ne 0) { throw 'OWN108_COMPLETE_CURRENT_OBSERVER_PROOF_REQUIRED' }
    }
    if (!(Test-Path -LiteralPath (Join-Path $receipts "world$Generation.pass")) -or
        [IO.File]::ReadAllText((Join-Path $receipts "world$Generation.pass")) -notmatch '^WORLD_GATE=PASS;first=16;second=16;stable=16;' -or
        !(Test-Path -LiteralPath (Join-Path $receipts "stop$Generation.natural"))) { throw 'OWN108-COMPLETE_SEPARATE_WORLD_AND_NATURAL_STOP_REQUIRED' }
    if ([IO.File]::ReadAllText((Join-Path $receipts "world$Generation-status.txt")) -notmatch '^actual=16/16;requestedCap=16;' -or
        [IO.File]::ReadAllText((Join-Path $receipts "logout108-COMPLETE-terminal$Generation.txt")) -notmatch '^calls=1;outstanding=0;event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;') { throw 'OWN108-COMPLETE_ACTUAL_WORLD_LOGOUT_REQUIRED' }
    foreach ($cut in @('first','second','prestop','stopped')) {
        if (![IO.File]::ReadAllText((Join-Path $receipts ("merchants107-" + $cut + $Generation + '.txt'))).StartsWith('owners=250;open=250;types={BUY=63, MANUFACTURE=25, SELL=162};')) { throw 'OWN108-COMPLETE_ACTUAL250_AND_SPLIT_REQUIRED' }
    }
}
if ($Action -in @('AcceptA','AcceptB')) {
    $pass = Get-Content -Raw -LiteralPath (Join-Path $receipts "postflight$phase.pass.json") | ConvertFrom-Json
    $review = Get-Content -Raw -LiteralPath (Join-Path $archive "review$phase.private.json") | ConvertFrom-Json
    if (!$review.independent -or $review.P1 -ne 0 -or $review.P2 -ne 0 -or $review.postflightSha256 -cne (Hash (Join-Path $receipts "postflight$phase.pass.json"))) { throw 'OWN108-COMPLETE_INDEPENDENT_PASS_REQUIRED_NO_CLEAN' }
    foreach ($pin in $pass.pins) { if ((Hash $pin.path) -cne $pin.sha256) { throw 'OWN108-COMPLETE_POSTFLIGHT_PROOF_DRIFT' } }
    NativeEvidence $phase
    New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;natural=true;jarSha256=" + $seal.jarSha256)
    New-Text (Join-Path $receipts "clean$phase.properties") ("postflightSha256=" + (Hash (Join-Path $receipts "postflight$phase.pass.json")) + "`nreviewSha256=" + (Hash (Join-Path $archive "review$phase.private.json")) + "`nexitSha256=" + (Hash (Join-Path $receipts "exit$phase.clean")))
    Write-Output "OWN108-COMPLETE_GENERATION_CLEAN phase=$phase;independentReview=PASS;natural=true"
    return
}
if (Test-Path -LiteralPath (Join-Path $receipts "launch$phase.begun")) { throw 'OWN108-COMPLETE_SINGLE_GENERATION_REQUIRED' }
if ($Action -eq 'B') {
    NativeEvidence 6
    foreach ($name in @('phase6.complete', 'stopped6.checkpoint', 'stop6-world-leases.txt', 'exit6.clean')) { if (!(Test-Path -LiteralPath (Join-Path $receipts $name))) { throw 'OWN108-COMPLETE_A_FULL_CLEAN_REQUIRED' } }
    if ([IO.File]::ReadAllText((Join-Path $receipts 'exit6.clean')) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;' -or [IO.File]::ReadAllText((Join-Path $receipts 'merchants107-prestop6.txt')) -notmatch ';market=PASS') { throw 'OWN108-COMPLETE_A_NATURAL_CLEAN_MARKET_REQUIRED' }
    $clean = Properties (Join-Path $receipts 'clean6.properties')
    if ($clean.postflightSha256 -cne (Hash (Join-Path $receipts 'postflight6.pass.json')) -or $clean.reviewSha256 -cne (Hash (Join-Path $archive 'review6.private.json')) -or $clean.exitSha256 -cne (Hash (Join-Path $receipts 'exit6.clean'))) { throw 'OWN108-COMPLETE_INDEPENDENT_CLEAN_SIGNATURE_DRIFT' }
}
$pre = Census ('before' + $Action)
if ($Action -eq 'B' -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts 'stopped6.checkpoint')).TrimEnd([char[]]"`r`n")) { throw 'OWN108-COMPLETE_PRESTART_CHECKPOINT_DRIFT' }
New-Text (Join-Path $receipts "launch$phase.begun") ('utc=' + [DateTime]::UtcNow.ToString('o'))
$uuid = $original.installationId
$arguments = @('-Xms512m', '-Xmx8g', '-Dphantom.task102.native=true', "-Dphantom.task102.stage=$stage", "-Dphantom.task102.uuid=$uuid", "-Dphantom.module.root=$shadow", "-Dphantom.test.config=$database", '-cp', $cp, 'org.l2jmobius.tests.phantoms.PhantomPopulation102Native', ('recovery108Complete' + $Action))
$line = ($arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
$process = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
$processHandle = $null
$handleFailure = $null
try { $processHandle = Hold-ProcessHandle $process }
catch { $handleFailure = $_.Exception.GetType().FullName }
$os = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $process.Id)
if (!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)) { throw 'OWN108-COMPLETE_NEW_PROCESS_IDENTITY_UNPROVEN_NO_RETRY' }
$digest = [Security.Cryptography.SHA256]::Create()
try { $commandHash = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-', '').ToLowerInvariant() } finally { $digest.Dispose() }
New-Text (Join-Path $receipts "process$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256 } | ConvertTo-Json)
if ($null -ne $handleFailure) {
    New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'HANDLE_UNAVAILABLE_NO_RETRY'; failure = $handleFailure; forceApproval = $false } | ConvertTo-Json)
    throw 'OWN108-COMPLETE_HANDLE_UNAVAILABLE;NO_B;NO_RETRY;NO_FORCE'
}
Write-Output "OWN108-COMPLETE_NATIVE_STARTED generation=$Action;cap=16;newProfiles=FORBIDDEN"
$deadline = [DateTime]::UtcNow.AddMinutes(12)
while (!$process.WaitForExit(1000)) {
    if ((Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")) -or [DateTime]::UtcNow -gt $deadline) {
        New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'STOP_BLOCKED_NO_RETRY'; forceApproval = $false } | ConvertTo-Json)
        throw 'OWN108-COMPLETE_STOP_BLOCKED;NO_B;NO_RETRY;NO_FORCE'
    }
}
try { $exit = Get-TrackedProcessExitCode $process $processHandle }
catch {
    New-Text (Join-Path $receipts "exit$phase.unknown") ('status=UNKNOWN;utc=' + [DateTime]::UtcNow.ToString('o') + ';failure=' + $_.Exception.GetType().FullName)
    throw 'OWN108-COMPLETE_EXIT_STATUS_UNKNOWN;NO_B;NO_RETRY;NO_FORCE'
}
New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc=" + [DateTime]::UtcNow.ToString('o'))
if ($exit -ne 0) { throw 'OWN108-COMPLETE_NATIVE_EXIT_FAILED_NO_NEXT_RUNTIME' }
Java0
$post = Census ('after' + $Action)
$boundary = [IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))
if ((!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -and !(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.diagnostic.complete"))) -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
    $boundary -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=250;attributedMerchants=250;samples=2;nativePersisted=true\r?\n?$') { throw 'OWN108-COMPLETE_FULL_NATIVE_STOP_PROOF_REQUIRED' }
if ([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")) { throw 'OWN108-COMPLETE_POSTEXIT_CHECKPOINT_DRIFT' }
if ([IO.File]::ReadAllText((Join-Path $receipts "logout108-COMPLETE-terminal$phase.txt")) -notmatch '^calls=1;outstanding=0;event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;') { throw 'OWN108-COMPLETE_STOP_EARNED_REQUIRED' }
New-Text (Join-Path $receipts "stop$phase.natural") 'STOP_DIAGNOSTIC_PASS;natural=true;exit=0;JVM=0;SQL=0;TCP=0;EARNED=1;outstanding=0;samples=2;checkpoint=EXACT'
Protected
$proofs = @((Join-Path $receipts "exit$phase.observed"), (Join-Path $receipts "stopped$phase.checkpoint"), (Join-Path $receipts "stop$phase-world-leases.txt"), (Join-Path $receipts "logout108-COMPLETE-terminal$phase.txt"), (Join-Path $post 'durable105.checkpoint'), (Join-Path $post 'market108-COMPLETE.log'), (Join-Path $post 'delta105.log'), (Join-Path $post 'sessions.txt'), (Join-Path $archive ('clearance-after' + $Action + '/first-preimage.private.tsv')), (Join-Path $archive ('clearance-after' + $Action + '/clearance.json')), (Join-Path $private 'native-inputs.tsv'))
$worldOutcome = if (Test-Path -LiteralPath (Join-Path $receipts "world$phase.pass")) { "world$phase.pass" } else { "world$phase.fail" }
$proofs += @((Join-Path $receipts $worldOutcome), (Join-Path $receipts "stop$phase.natural"), (Join-Path $receipts "world$phase-first.tsv"), (Join-Path $receipts "world$phase-second.tsv"), (Join-Path $receipts "world$phase-status.txt"))
$proofs += @((Join-Path $post 'skills.private.tsv'), (Join-Path $post 'skills.properties'), (Join-Path $post 'skills-audit.log'), (Join-Path $pre 'skills.private.tsv'), (Join-Path $pre 'skills.properties'), (Join-Path $receipts "skills108-$phase.events.private.tsv"), (Join-Path $receipts "skills108-$phase.events.private.tsv.complete"), (Join-Path $receipts "observers108-$phase.private.tsv"), (Join-Path $receipts "cohort108R3-$phase-start.private.tsv"), (Join-Path $receipts "cohort108R3-$phase-final.private.tsv"))
foreach ($cut in @('first','second','prestop','stopped')) { $proofs += Join-Path $receipts ("merchants107-" + $cut + $phase + '.txt') }
$proofPins = @($proofs | ForEach-Object { @{path=$_;sha256=(Hash $_)} })
New-Text (Join-Path $receipts "postflight$phase.pass.json") (@{phase=$phase;natural=$true;JVM=0;SQL=0;TCP=0;jarSha256=$seal.jarSha256;pins=$proofPins} | ConvertTo-Json -Depth 5)
if (!(Test-Path -LiteralPath (Join-Path $receipts "world$phase.pass"))) {
    Write-Output "OWN108-COMPLETE_WORLD_FAIL;STOP_DIAGNOSTIC_PASS;FULL=BLOCKED;NO_CLEAN;NO_B;NO_RETRY;NO_FORCE"
    throw 'OWN108-COMPLETE_WORLD_FAIL_AFTER_DIAGNOSTIC_STOP_NO_CLEAN_NO_B'
}
NativeEvidence $phase
Write-Output "OWN108-COMPLETE_POSTFLIGHT_PASS generation=$Action;independentReview=REQUIRED;exit.clean=ABSENT;NO_NEXT_NATIVE_UNTIL_ACCEPT"
