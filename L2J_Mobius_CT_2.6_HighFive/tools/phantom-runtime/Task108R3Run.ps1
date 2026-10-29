[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Causal','Historical','Audit','Prepare','Build','Bind','Smoke','SmokeFinish','Seal','A','B','AcceptA','AcceptB')][string]$Action,
    [string]$SqlClient = (Join-Path $env:ProgramFiles 'MariaDB 11.4/bin/mariadb.exe')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Hold-ProcessHandle([Diagnostics.Process]$Process) {
    if ($null -eq $Process) { throw 'OWN108R3C_PROCESS_REQUIRED' }
    # Start-Process redirection on Windows PowerShell 5.1 can lose ExitCode unless
    # this Process owns its cached handle before waiting (PowerShell issue 5421).
    $handle = $Process.Handle
    if ($null -eq $handle -or $handle -isnot [IntPtr] -or $handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_PROCESS_HANDLE_UNAVAILABLE' }
    return $handle
}
function Assert-ObservedExitCode([object]$Value) {
    # Do not cast: PowerShell converts a null value to integer zero.
    if ($null -eq $Value -or $Value -isnot [int]) { throw 'OWN108R3C_EXIT_STATUS_UNKNOWN' }
    return $Value
}
function Get-TrackedProcessExitCode([Diagnostics.Process]$Process, [object]$Handle) {
    if ($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_EXIT_HANDLE_UNKNOWN' }
    if (!$Process.HasExited) { throw 'OWN108R3C_PROCESS_NOT_EXITED' }
    return Assert-ObservedExitCode $Process.ExitCode
}
function Physical([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    for ($walk = $item; $null -ne $walk; $walk = if ($walk -is [IO.FileInfo]) { $walk.Directory } else { $walk.Parent }) {
        if ($walk.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'OWN108R3C_REPARSE' }
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
    if (!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid) { throw 'OWN108R3C_PRIVATE_OWNER_REQUIRED' }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid, 'S-1-5-18')) { throw 'OWN108R3C_FOREIGN_ACL' }
    }
}
function Private-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'OWN108R3C_FRESH_DIRECTORY_REQUIRED' }
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
    if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0) { throw 'OWN108R3C_JAVA_PRESENT_NO_RUNTIME' }
    if (@(Get-NetTCPConnection -ErrorAction Stop | Where-Object { $_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308) }).Count -ne 0) { throw 'OWN108R3C_TCP_PRESENT_NO_RUNTIME' }
}

$module = Physical (Join-Path $PSScriptRoot '../..')
$repo = Physical (Split-Path -Parent $module)
if ((Physical ((& git -C $repo rev-parse --show-toplevel).Trim())) -ine $repo) { throw 'OWN108R3C_GIT_ROOT' }
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior = Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105/resume105.private.json') -Raw | ConvertFrom-Json
$origin = Physical $prior.origin
$original = Properties (Join-Path $origin 'private/owner.properties')
$client = Physical $SqlClient
$options = Physical (Join-Path $prior.archive 'sql-client.private.cnf')
$index = Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-COHORT-RECOVERY-108R3C'
$history = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-STOP-RESTART-108R/index.private.json') | ConvertFrom-Json
$historyBefore = Physical (Join-Path $history.archive 'g0/first-preimage.private.tsv')
$historyAfter = Physical (Join-Path $history.archive 'clearance-afterA/first-preimage.private.tsv')
$historyWitness = Physical (Join-Path $history.stage 'private/receipts108R/merchants107-prestop6.private.tsv')
$previousR2 = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-MARKET-DELTA-RESTART-108R2/index.private.json') | ConvertFrom-Json
$recoveryAfter = Physical (Join-Path $previousR2.archive 'blockedA/first-preimage.private.tsv')
$recoveryWitness = Physical (Join-Path $previousR2.stage 'private/receipts108R2/merchants107-second6.private.tsv')
$ids = Physical (Join-Path $origin 'private/receipts-continuation2/ids4.tsv')
$sourcePolicy = Physical (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')
$checkerSha = Hash (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomMarketDelta108R2Checks.java')
$priorR3 = Get-Content -Raw -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-WORLD-COHORT-RECOVERY-108R3/index.private.json') | ConvertFrom-Json
foreach ($root in @($origin, (Physical $prior.archive), (Physical $prior.resume))) { Assert-Private $root }
if ($original.owner -cne 'TASK102_POPULATION_SCALE' -or $original.schema -cne 'l2jmobiush5_phantom_test_102' -or $original.ownerSid -cne $sid -or
    (Physical $original.sourceModule) -ine $module -or (Hash (Join-Path $origin 'inputs.tsv')) -cne $original.inputsSha256 -or
    ![IO.File]::ReadAllText((Physical (Join-Path $origin 'private/db.created'))).StartsWith($original.installationId + "`n") -or
    (Hash (Join-Path $module 'dist/game/config/Database.ini')) -cne $original.sourceDatabaseSha256) { throw 'OWN108R3C_ORIGINAL_AUTHORITY_DRIFT' }

function Sql([string]$Query, [string]$Output) {
    # Fixed read-only callers only. Keep credentials and complete snapshots outside checkout.
    $rows = @($Query | & $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>($Output + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_READONLY_SQL_FAILED' }
    New-Text $Output (($rows -join "`n") + "`n")
    return $rows
}
function Clearance([string]$Output) {
    Java0
    New-Item -ItemType Directory -Path $Output | Out-Null
    $grants = @(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $Output 'grants.private.tsv'))
    if (!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')) { throw 'OWN108R3C_FULL_VISIBILITY_REQUIRED' }
    $authority = @(Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' (Join-Path $Output 'authority.private.tsv'))
    if ($authority.Count -ne 1 -or $authority[0] -cne ($original.installationId + "`t" + $original.sourceHead + "`t" + $original.inputsSha256)) { throw 'OWN108R3C_SQL_AUTHORITY_DRIFT' }
    $sessions = @(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $Output 'sessions.private.tsv'))
    if ($sessions.Count -ne 0) { throw 'OWN108R3C_FOREIGN_OR_OWN_SQL_PRESENT' }
    foreach ($cut in @('first', 'second')) {
        $null = Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $Output ($cut + '-preimage.private.tsv'))
    }
    if ((Hash (Join-Path $Output 'first-preimage.private.tsv')) -cne (Hash (Join-Path $Output 'second-preimage.private.tsv'))) { throw 'OWN108R3C_UNSTABLE_PREIMAGE' }
    Java0
    New-Text (Join-Path $Output 'clearance.json') (@{ JVM = 0; SQL = 0; TCP = 0; foreign = 0; fullVisibility = $true; authorityExact = $true; stable = $true; utc = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json)
}

if ($Action -in @('Causal','Historical','Audit')) {
    Java0
    $head = (& git -C $repo rev-parse HEAD).Trim()
    $branch = (& git -C $repo symbolic-ref --short HEAD).Trim()
    $tracking = (& git -C $repo rev-parse origin/feature/phantom-world).Trim()
    $live = @(& git -C $repo ls-remote --heads origin refs/heads/feature/phantom-world)
    if ($LASTEXITCODE -ne 0 -or $live.Count -ne 1 -or $branch -cne 'feature/phantom-world' -or $tracking -cne $head -or ($live[0] -split '\s+')[0] -cne $head) { throw 'OWN108R3C_LIVE_PARITY_REQUIRED' }
    if (!(Test-Path -LiteralPath $index)) {
        Private-Directory $index
        $archive = Join-Path ([IO.Path]::GetTempPath()) ('TASK108R3C-' + [guid]::NewGuid().ToString('N'))
        Private-Directory $archive
        New-Text (Join-Path $index 'index.private.json') (@{head=$head;archive=$archive;stage=(Join-Path $archive 'shadow108R3C');utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json)
    }
    $info = Get-Content -Raw -LiteralPath (Join-Path $index 'index.private.json') | ConvertFrom-Json
    $archive = Physical $info.archive
    Assert-Private $index; Assert-Private $archive
    if ($Action -eq 'Causal') {
        if ($head -cne $info.head -or (Test-Path -LiteralPath $info.stage) -or (Test-Path -LiteralPath (Join-Path $archive 'character-delta/approved-character-delta.properties'))) { throw 'OWN108R3C_SINGLE_OFFLINE_CAUSAL_GATE' }
        $savedAfter = Physical (Join-Path $priorR3.archive 'g0/first-preimage.private.tsv')
        $oldPass = Physical (Join-Path $priorR3.archive ('historical-' + $checkerSha + '.pass'))
        $oldProof = Properties $oldPass
        if ($oldProof.checkerSha256 -cne $checkerSha -or $oldProof.before -cne (Hash $historyBefore) -or $oldProof.after -cne (Hash $historyAfter) -or $oldProof.witness -cne (Hash $historyWitness) -or $oldProof.sourcePolicy -cne (Hash $sourcePolicy)) { throw 'OWN108R3C_PRIOR_HISTORICAL_PROOF_DRIFT' }
        [IO.File]::Copy($oldPass, (Join-Path $archive ('historical-' + $checkerSha + '.pass')), $false)
        $sourcePins = [Collections.Generic.List[string]]::new()
        foreach ($relative in @('java/org/l2jmobius/gameserver/model/actor/Player.java','java/org/l2jmobius/gameserver/model/actor/stat/PlayerStat.java','java/org/l2jmobius/gameserver/model/actor/tasks/player/VitalityTask.java','dist/game/config/Player.ini','dist/game/config/Rates.ini','dist/game/config/General.ini','dist/game/data/zones/peace.xml')) {
            $currentHash = Hash (Join-Path $module $relative)
            if ($currentHash -cne (Hash (Join-Path $previousR2.stage ('L2J_Mobius_CT_2.6_HighFive/' + $relative)))) { throw 'OWN108R3C_CAUSAL_SOURCE_CONFIG_DRIFT' }
            $sourcePins.Add($relative + "`t" + $currentHash)
        }
        New-Text (Join-Path $archive 'character-source-pins.tsv') ($sourcePins -join "`n")
        $processPath = Physical (Join-Path $previousR2.stage 'private/receipts108R2/process6.private.json')
        $forcePath = Physical (Join-Path $previousR2.archive 'force-owner-approved-ec57d0ab99124a5e92535cc6ae8790dd/force-result.private.json')
        $processProof = Get-Content -Raw -LiteralPath $processPath | ConvertFrom-Json
        $forceProof = Get-Content -Raw -LiteralPath $forcePath | ConvertFrom-Json
        if ($processProof.PID -ne 31268 -or $forceProof.status -cne 'FORCED_UNCLEAN') { throw 'OWN108R3C_PRIOR_INCARNATION_DRIFT' }
        New-Text (Join-Path $archive 'character-evidence.properties') ("jvmStartUtc=" + ([DateTime]$processProof.CreationUTC).ToUniversalTime().ToString('o') + "`nforceUtc=" + ([DateTime]$forceProof.utc).ToUniversalTime().ToString('o') + "`npriorStop=FORCED_UNCLEAN`nprocessSha256=" + (Hash $processPath) + "`nforceSha256=" + (Hash $forcePath) + "`nsourcePinsSha256=" + (Hash (Join-Path $archive 'character-source-pins.tsv')))
        $scratch = Join-Path $archive 'character-validator'
        New-Item -ItemType Directory -Path $scratch | Out-Null
        $oldCP = ((Join-Path $previousR2.stage 'build/phantom-test/bin') + ';' + (Join-Path $previousR2.stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/*'))
        $sources = @('PhantomTask102Lane.java','PhantomPopulation102Native.java','PhantomWorldCohort108R3Checks.java') | ForEach-Object { Join-Path $module ('test/java/org/l2jmobius/tests/phantoms/' + $_) }
        & javac -encoding UTF-8 -cp $oldCP -d $scratch $sources 1>(Join-Path $archive 'character-compile.log') 2>(Join-Path $archive 'character-compile.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_CHARACTER_COMPILE_FAILED_NO_NATIVE' }
        $out = Join-Path $archive 'character-delta'
        New-Item -ItemType Directory -Path $out | Out-Null
        & java -Xmx2g -cp ($scratch + ';' + $oldCP) org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks character-delta $recoveryAfter $savedAfter $recoveryWitness (Join-Path $archive 'character-evidence.properties') (Join-Path $priorR3.archive 'g0/sessions.private.tsv') $module $out 1>(Join-Path $archive 'character-delta.log') 2>(Join-Path $archive 'character-delta.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_CHARACTER_DELTA_FAILED_NO_NATIVE' }
        Java0
        Write-Output 'OWN108R3C_CAUSAL_PASS exactApprovedPair=true;otherFieldsExact=true;independentReview=REQUIRED;G0=NOT_ACCEPTED;NATIVE=0'
        return
    }
    if ($Action -eq 'Historical') {
    $historicalPass = Join-Path $archive ('historical-' + $checkerSha + '.pass')
    if ($info.head -cne $head -or (Test-Path -LiteralPath (Join-Path $info.stage 'private/native.properties')) -or (Test-Path -LiteralPath (Join-Path $info.stage 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini')) -or (Test-Path -LiteralPath $historicalPass)) { throw 'OWN108R3C_NO_HISTORY_REPLAY_AFTER_BIND_OR_SAME_SOURCE_GATE' }
    $attempt = Join-Path $archive ('historical-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $attempt | Out-Null
    if ((Hash $historyBefore) -cne 'cfe6fc42c4f5820ff53e04383353e107f59bd191d623995cfa6720c844d6df9a' -or (Hash $historyAfter) -cne 'ba19c75580a24a8e696a83e00357c84ea08712b3656d5d9a77abd337aa89fd7a' -or (Test-Path -LiteralPath (Join-Path $history.stage 'private/receipts108R/exit6.clean'))) { throw 'OWN108R3C_HISTORICAL_PREIMAGES_REQUIRED' }
    $historicCP = ((Join-Path $history.stage 'build/phantom-test/bin') + ';' + (Join-Path $history.stage 'build/phantom-test/resources') + ';' + (Join-Path $history.stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/*')).Replace('\','/')
    $ErrorActionPreference = 'Continue'
    try { & java -cp $historicCP org.l2jmobius.tests.phantoms.PhantomStop108Checks market $historyBefore $historyAfter 1>(Join-Path $attempt 'historical-old-red.log') 2>(Join-Path $attempt 'historical-old-red.stderr.private.txt'); $redExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($redExit -eq 0 -or [IO.File]::ReadAllText((Join-Path $attempt 'historical-old-red.stderr.private.txt')) -notmatch 'market receipts/ledger changed') { throw 'OWN108R3C_EXPECTED_HISTORICAL_RED_REQUIRED' }
    $scratch = Join-Path $archive ('historical-bin-' + $checkerSha)
    New-Item -ItemType Directory -Path $scratch | Out-Null
    $sources = @('PhantomMarketDelta108R2Checks.java','PhantomTask102Lane.java','PhantomPopulation102Native.java','PhantomWorldCohort108R3Checks.java') | ForEach-Object { Join-Path $module ('test/java/org/l2jmobius/tests/phantoms/' + $_) }
    & javac -cp $historicCP -d $scratch $sources 1>(Join-Path $attempt 'historical-compile.log') 2>(Join-Path $attempt 'historical-compile.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_HISTORICAL_COMPILE_FAILED_ZERO_NATIVE' }
    & java -Xmx2g -cp ($scratch + ';' + $historicCP) org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks historical $historyBefore $historyAfter $ids $historyWitness $sourcePolicy 1>(Join-Path $attempt 'historical-green.log') 2>(Join-Path $attempt 'historical-green.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_HISTORICAL_FAILED_ZERO_NATIVE' }
    & java -Xmx2g -cp ($scratch + ';' + $historicCP) org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $historyAfter $recoveryAfter $ids $recoveryWitness $sourcePolicy 1>(Join-Path $attempt 'historical78-82.log') 2>(Join-Path $attempt 'historical78-82.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_HISTORICAL78_82_FAILED_ZERO_NATIVE' }
    New-Text $historicalPass ("checkerSha256=" + $checkerSha + "`nbefore=" + (Hash $historyBefore) + "`nafter=" + (Hash $historyAfter) + "`nwitness=" + (Hash $historyWitness) + "`nsourcePolicy=" + (Hash $sourcePolicy) + "`nattempt=" + $attempt.Replace('\','/'))
    Write-Output 'OWN108R3C_HISTORICAL_PASS real=71->78,78->82;empty=78->78;negatives=PASS;JDBC=0;NATIVE=0'
    return
    }
}

Assert-Private $index
$info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
$archive = Physical $info.archive
Assert-Private $archive
function CausalPins {
    $evidence = Properties (Join-Path $archive 'character-evidence.properties')
    $pinPath = Physical (Join-Path $archive 'character-source-pins.tsv')
    if ($evidence.sourcePinsSha256 -cne (Hash $pinPath) -or
        $evidence.processSha256 -cne (Hash (Join-Path $previousR2.stage 'private/receipts108R2/process6.private.json')) -or
        $evidence.forceSha256 -cne (Hash (Join-Path $previousR2.archive 'force-owner-approved-ec57d0ab99124a5e92535cc6ae8790dd/force-result.private.json'))) { throw 'OWN108R3C_CAUSAL_EVIDENCE_LEAF_DRIFT' }
    $allowed = @('java/org/l2jmobius/gameserver/model/actor/Player.java','java/org/l2jmobius/gameserver/model/actor/stat/PlayerStat.java','java/org/l2jmobius/gameserver/model/actor/tasks/player/VitalityTask.java','dist/game/config/Player.ini','dist/game/config/Rates.ini','dist/game/config/General.ini','dist/game/data/zones/peace.xml')
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in [IO.File]::ReadAllLines($pinPath)) {
        $fields = $line -split "`t"
        if ($fields.Count -ne 2 -or $fields[0] -cnotin $allowed -or !$seen.Add($fields[0]) -or $fields[1] -cnotmatch '^[0-9a-f]{64}$' -or
            (Hash (Join-Path $module $fields[0])) -cne $fields[1] -or (Hash (Join-Path $previousR2.stage ('L2J_Mobius_CT_2.6_HighFive/' + $fields[0]))) -cne $fields[1]) { throw 'OWN108R3C_CAUSAL_SOURCE_PIN_DRIFT' }
    }
    if ($seen.Count -ne $allowed.Count) { throw 'OWN108R3C_COMPLETE_CAUSAL_SOURCE_PINS_REQUIRED' }
}
if ($Action -ne 'Audit') {
    $historical = Properties (Join-Path $archive ('historical-' + $checkerSha + '.pass'))
    if ($historical.checkerSha256 -cne $checkerSha -or $historical.before -cne (Hash $historyBefore) -or $historical.after -cne (Hash $historyAfter) -or $historical.witness -cne (Hash $historyWitness) -or $historical.sourcePolicy -cne (Hash $sourcePolicy)) { throw 'OWN108R3C_FRESH_HISTORICAL_GREEN_REQUIRED_ZERO_NATIVE' }
}
if ($Action -eq 'Audit') {
    Java0
    CausalPins
    $head = (& git -C $repo rev-parse HEAD).Trim()
    $live = @(& git -C $repo ls-remote --heads origin refs/heads/feature/phantom-world)
    if ($LASTEXITCODE -ne 0 -or $live.Count -ne 1 -or $head -cne $info.head -or $head -cne (& git -C $repo rev-parse origin/feature/phantom-world).Trim() -or ($live[0] -split '\s+')[0] -cne $head) { throw 'OWN108R3C_FRESH_PARITY_REQUIRED' }
    $info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
    $archive = Physical $info.archive
    Assert-Private $index; Assert-Private $archive
    if ($info.head -cne $head -or (Test-Path -LiteralPath $info.stage) -or (Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN108R3C_FRESH_AUDIT_REQUIRED' }
    $approvalPath = Physical (Join-Path $archive 'character-delta/approved-character-delta.properties')
    $approval = Properties $approvalPath
    $causalReview = Get-Content -Raw -LiteralPath (Join-Path $archive 'review-character-delta.private.json') | ConvertFrom-Json
    if (!$causalReview.independent -or $causalReview.P1 -ne 0 -or $causalReview.P2 -ne 0 -or !$causalReview.mechanismCompatible -or $causalReview.directWriterObserved -or $causalReview.approvalSha256 -cne (Hash $approvalPath) -or $approval.status -cne 'APPROVED_CHARACTER_DELTA' -or $approval.validatorSha256 -cne (Hash (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomWorldCohort108R3Checks.java'))) { throw 'OWN108R3C_INDEPENDENT_CHARACTER_DELTA_REVIEW_REQUIRED' }
    if ($approval.beforeSha256 -cne (Hash $recoveryAfter) -or $approval.afterSha256 -cne (Hash (Join-Path $priorR3.archive 'g0/first-preimage.private.tsv')) -or $approval.evidenceSha256 -cne (Hash (Join-Path $archive 'character-evidence.properties')) -or $approval.witnessSha256 -cne (Hash $recoveryWitness)) { throw 'OWN108R3C_APPROVED_DELTA_PROOF_DRIFT' }
    $previous = $previousR2
    # These two test-only paths are the exact108R3C edit allowance; every other sealed107 input remains pinned.
    $allPins = Get-Content -LiteralPath (Join-Path $previous.archive 'protected-final108R2.private.json') -Raw | ConvertFrom-Json
    $pins = @($allPins | Where-Object { $_.relative -notin @('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java', 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java') })
    foreach ($pin in $pins) { if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108R3C_PROTECTED_SOURCE_DRIFT' } }
    Clearance (Join-Path $archive 'g0')
    if ((Hash (Join-Path $archive 'g0/first-preimage.private.tsv')) -cne $approval.afterSha256) { throw 'OWN108R3C_UNAPPROVED_NEW_DRIFT_NO_NATIVE' }
    $priorCP = ((Join-Path $previousR2.stage 'build/phantom-test/bin') + ';' + (Join-Path $previousR2.stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/*'))
    & java -Xmx2g -cp $priorCP org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $recoveryAfter (Join-Path $archive 'g0/first-preimage.private.tsv') $ids $recoveryWitness $sourcePolicy 1>(Join-Path $archive 'market-g0.log') 2>(Join-Path $archive 'market-g0.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_G0_IDENTITIES_MARKET_FAILED' }
    New-Text (Join-Path $archive 'protected-final108R3C.private.json') ($pins | ConvertTo-Json -Depth 5)
    New-Text (Join-Path $archive 'preparation108R3C.uuid') ([guid]::NewGuid().ToString())
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/OwnerConfig.json') -Destination (Join-Path $archive 'source-owner108R3C.private.json')
    New-Text (Join-Path $archive 'audit.complete') 'G0_108R3C_PASS;JVM=0;SQL=0;TCP=0;foreign=0;stable=true;SQL_WRITES=0'
    Write-Output 'G0_108R3C_PASS originalAuthority=EXACT;fullVisibility=true;foreign=0;JVM=0;SQL=0;TCP=0;stable=true;protected=UNCHANGED;SQL_WRITES=0'
    return
}

Assert-Private $index
$info = Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw | ConvertFrom-Json
$archive = Physical $info.archive
Assert-Private $archive
$stage = $info.stage
function Protected {
    CausalPins
    $manifest = Join-Path $archive 'protected-final108R3C.private.json'
    if (!(Test-Path -LiteralPath $manifest)) { throw 'OWN108R3C_FINAL_SOURCE_PINS_REQUIRED' }
    foreach ($pin in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
        if ((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256) { throw 'OWN108R3C_SEALED_SOURCE_DRIFT' }
    }
    if ((Hash (Join-Path $archive 'source-owner108R3C.private.json')) -cne (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json'))) { throw 'OWN108R3C_OLD_OWNER_OVERRIDES_CHANGED' }
    if ($info.head -cne (& git -C $repo rev-parse HEAD).Trim()) { throw 'OWN108R3C_SOURCE_HEAD_DRIFT' }
}
if ($Action -eq 'Prepare') {
    if (!(Test-Path -LiteralPath (Join-Path $archive 'audit.complete'))) { throw 'OWN108R3C_G0_REQUIRED' }
    Protected
    Clearance (Join-Path $archive 'before-prepare')
    & (Join-Path $PSScriptRoot 'Task102.ps1') -Action Prepare -SourceRoot $module -StageRoot $stage -InstallationId ([IO.File]::ReadAllText((Join-Path $archive 'preparation108R3C.uuid')))
    if (!$?) { throw 'OWN108R3C_PREPARE_FAILED' }
    return
}
Java0; Protected
$stage = Physical $stage; Assert-Private $stage
$shadow = Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
$private = Physical (Join-Path $stage 'private')
$game = Physical (Join-Path $shadow 'dist/game')
$database = Join-Path $game 'config/Database.ini'
$receipts = Join-Path $private 'receipts108R3C'
$cp = ((Join-Path $stage 'build/phantom-test/bin') + ';' + (Join-Path $stage 'build/phantom-test/resources') + ';' + (Join-Path $shadow 'dist/libs/*')).Replace('\', '/')
if ($Action -eq 'Build') {
    if (Test-Path -LiteralPath $database) { throw 'OWN108R3C_BUILD_BEFORE_BIND_ONLY' }
    New-Text (Join-Path $private 'build108R3C.begun') ([DateTime]::UtcNow.ToString('o'))
    # Windows PowerShell 5 wraps normal native stderr as ErrorRecord even for exit0.
    $ErrorActionPreference = 'Continue'
    try { & java -version 2>(Join-Path $archive 'java25.private.txt'); $versionExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($versionExit -ne 0 -or [IO.File]::ReadAllText((Join-Path $archive 'java25.private.txt')) -notmatch 'version "25\.') { throw 'OWN108R3C_JDK25_REQUIRED' }
    & ant -f (Join-Path $shadow 'build.xml') compile-tests jar 1>(Join-Path $archive 'build108R3C.log') 2>(Join-Path $archive 'build108R3C.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_BUILD_FAILED_NO_NATIVE' }
    # Ant's test source root excludes tools/phantom-runtime. These read-only CLI helpers are part of the sealed runtime classpath.
    & javac -cp $cp -d (Join-Path $stage 'build/phantom-test/bin') (Join-Path $shadow 'tools/phantom-runtime/Task104Census.java') (Join-Path $shadow 'tools/phantom-runtime/Task105Census.java') 1>(Join-Path $archive 'census108R3C-compile.log') 2>(Join-Path $archive 'census108R3C-compile.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_CENSUS_COMPILE_FAILED_NO_NATIVE' }
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        $file = Physical (Join-Path $stage ('build/phantom-test/bin/' + $name))
        if ((Get-Item -LiteralPath $file).Length -eq 0) { throw 'OWN108R3C_EMPTY_CENSUS_CLASS' }
    }
    & javap -verbose -classpath $cp Task104Census Task105Census 1>(Join-Path $archive 'census108R3C-javap.log') 2>(Join-Path $archive 'census108R3C-javap.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_CENSUS_LINKAGE_FAILED' }
    $bytecode = [IO.File]::ReadAllText((Join-Path $archive 'census108R3C-javap.log'))
    foreach ($name in @('Task104Census','Task105Census')) { if (!$bytecode.Contains('public final class ' + $name)) { throw 'OWN108R3C_DEFAULT_PACKAGE_REQUIRED' } }
    New-Text (Join-Path $private 'build108R3C.complete') 'serial=compile-tests jar+javac census104/105;exit=0'
    foreach ($step in @('source-Verify','stage-Apply1','stage-Verify1','stage-Apply2','stage-Verify2')) {
        $root = if ($step.StartsWith('source')) { Join-Path $module 'dist/game' } else { $game }
        $operation = if ($step.Contains('Apply')) { 'Apply' } else { 'Verify' }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $module 'tools/OwnerConfig.ps1') -Action $operation -GameRoot $root -ProfilePath (Join-Path $module 'dist/game/config/OwnerConfig.json') 1>(Join-Path $archive ($step + '.log')) 2>(Join-Path $archive ($step + '.stderr.private.txt'))
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_OWNER_CHAIN_FAILED' }
        $ownerLog = [IO.File]::ReadAllText((Join-Path $archive ($step + '.log')))
        if ($ownerLog -notmatch '287' -or $ownerLog -notmatch 'conflicts=0' -or ($step -eq 'stage-Apply2' -and $ownerLog -notmatch 'changed=0')) { throw 'OWN108R3C_OWNER_287_REPEAT0_REQUIRED' }
    }
    New-Text (Join-Path $private 'owner108R3C.complete') 'sourceVerify+Apply+Verify+Apply0+Verify;overrides=287;source=UNCHANGED'
    Write-Output 'OWN108R3C_SERIAL_EXTERNAL_BUILD_PASS'
    return
}
if ($Action -in @('Smoke','SmokeFinish')) {
    if ((Test-Path -LiteralPath $database) -or !(Test-Path -LiteralPath (Join-Path $private 'build108R3C.complete'))) { throw 'OWN108R3C_SMOKE_BEFORE_BIND_REQUIRED' }
    if ($Action -eq 'Smoke') {
    Clearance (Join-Path $archive 'before-smoke')
    $empty = Join-Path $archive 'missing-class-fixture'
    New-Item -ItemType Directory -Path $empty | Out-Null
    $ErrorActionPreference = 'Continue'
    try { & java -cp $empty Task105Census snapshot 1>(Join-Path $archive 'missing-class.log') 2>(Join-Path $archive 'missing-class.stderr.private.txt'); $missingExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($missingExit -eq 0 -or [IO.File]::ReadAllText((Join-Path $archive 'missing-class.stderr.private.txt')) -notmatch 'ClassNotFoundException: Task105Census') { throw 'OWN108R3C_NEGATIVE_CONTROL_FAILED' }
    $classPins = @('Task104Census.class','Task105Census.class') | ForEach-Object { $_ + "`t" + (Hash (Join-Path $stage ('build/phantom-test/bin/' + $_))) }
    New-Text (Join-Path $private 'census108R3C-classes.tsv') ($classPins -join "`n")
    }
    else {
        New-Text (Join-Path $private 'smoke-finish.begun') 'resume=remaining-market-gates;newSnapshots=0;newAnt=0;native=0'
        if ([IO.File]::ReadAllText((Join-Path $archive 'Smoke-action.stderr.private.txt')) -notmatch 'native/SQL grants mismatch' -or
            [IO.File]::ReadAllText((Join-Path $archive 'missing-class.stderr.private.txt')) -notmatch 'ClassNotFoundException: Task105Census' -or
            (Hash (Join-Path $archive 'before-smoke/first-preimage.private.tsv')) -cne (Hash (Join-Path $archive 'g0/first-preimage.private.tsv'))) { throw 'OWN108R3C_EXACT_PARTIAL_SMOKE_REQUIRED' }
        $pins = @(Get-Content -LiteralPath (Join-Path $private 'census108R3C-classes.tsv'))
        if ($pins.Count -ne 2) { throw 'OWN108R3C_PARTIAL_CENSUS_PINS' }
        foreach ($line in $pins) { $fields = $line -split "`t"; if ($fields.Count -ne 2 -or $fields[0] -cnotmatch '^Task10[45]Census\.class$' -or (Hash (Join-Path $stage ('build/phantom-test/bin/' + $fields[0]))) -cne $fields[1]) { throw 'OWN108R3C_PARTIAL_CLASS_DRIFT' } }
    }
    foreach ($cut in @('first','second')) {
        $out = Join-Path $archive ('smoke-' + $cut)
        if ($Action -eq 'Smoke') {
        New-Item -ItemType Directory -Path $out | Out-Null
        & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_RUNNABLE_CENSUS_FAILED_NO_NATIVE' }
        }
        $log = [IO.File]::ReadAllText((Join-Path $out 'census.log'))
        if (!$log.Contains('OWN104_CENSUS_PASS READY=5000;') -or !$log.Contains('OWN105_SNAPSHOT_PASS slices=10;') -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108R3C_NESTED_CENSUS_PROOF_REQUIRED' }
        $checkpoint = @(Get-Content -LiteralPath (Join-Path $out 'durable105.checkpoint'))
        if ($checkpoint.Count -ne 10) { throw 'OWN108R3C_TEN_SLICES_REQUIRED' }
        for ($i=0; $i -lt 10; $i++) {
            $fields = $checkpoint[$i] -split "`t"
            if ($fields.Count -ne 3 -or $fields[0] -cne [string]$i -or $fields[2] -cne (Hash (Join-Path $out "slice$i.private.txt"))) { throw 'OWN108R3C_SLICE_DRIFT' }
        }
        if (@(Compare-Object (Get-Content -LiteralPath (Join-Path $out 'ids.tsv')) (Get-Content -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv'))).Count -ne 0) { throw 'OWN108R3C_ORIGINAL_IDS_DRIFT' }
        Java0
    }
    if ((Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')) -cne (Hash (Join-Path $archive 'smoke-second/durable105.checkpoint'))) { throw 'OWN108R3C_UNSTABLE_SMOKE' }
    if ($Action -eq 'Smoke') {
        & java -cp $cp Task105Census diff (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') 1>(Join-Path $archive 'census108R3C-roles.log') 2>(Join-Path $archive 'census108R3C-roles.stderr.private.txt')
        if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_MERCHANT_G0_FAILED' }
    }
    if ([IO.File]::ReadAllText((Join-Path $archive 'census108R3C-roles.log')) -notmatch 'durableREADY=250;' -or (Get-Item -LiteralPath (Join-Path $archive 'census108R3C-roles.stderr.private.txt')).Length -ne 0) { throw 'OWN108R3C_MERCHANT_G0_FAILED' }
    $marketLog = if ($Action -eq 'SmokeFinish') { 'market108R3C-smoke-finish' } else { 'market108R3C-smoke' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market (Join-Path $archive 'g0/first-preimage.private.tsv') (Join-Path $archive 'before-smoke/first-preimage.private.tsv') $ids $recoveryWitness $sourcePolicy 1>(Join-Path $archive ($marketLog + '.log')) 2>(Join-Path $archive ($marketLog + '.stderr.private.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_MARKET_G0_FAILED' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks historical $historyBefore $historyAfter $ids $historyWitness $sourcePolicy 1>(Join-Path $archive 'market108R3C-sameCP-historical.log') 2>(Join-Path $archive 'market108R3C-sameCP-historical.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_REAL_HISTORICAL_SAME_CLASSPATH_FAILED' }
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $historyAfter $recoveryAfter $ids $recoveryWitness $sourcePolicy 1>(Join-Path $archive 'market108R3C-sameCP78-82.log') 2>(Join-Path $archive 'market108R3C-sameCP78-82.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_REAL78_82_SAME_CLASSPATH_FAILED' }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks 1>(Join-Path $archive 'cohort108R3C-sameCP.log') 2>(Join-Path $archive 'cohort108R3C-sameCP.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_DB_FREE_COHORT_FAILED' }
    Clearance (Join-Path $archive 'after-smoke')
    if ((Hash (Join-Path $archive 'g0/first-preimage.private.tsv')) -cne (Hash (Join-Path $archive 'after-smoke/first-preimage.private.tsv'))) { throw 'OWN108R3C_SMOKE_DATA_DRIFT' }
    New-Text (Join-Path $private 'smoke108R3C.complete') ('classpath=' + $cp + "`nclassesSha256=" + (Hash (Join-Path $private 'census108R3C-classes.tsv')) + "`ncheckpointSha256=" + (Hash (Join-Path $archive 'smoke-first/durable105.checkpoint')))
    Write-Output 'OWN108R3C_SMOKE_PASS nested104=true;slices=10;READY=5000;roles=250;market=VERIFIED;SQL_WRITES=0;JVM=0;SQL=0;TCP=0'
    return
}
function CensusPins {
    $smoke = Properties (Join-Path $private 'smoke108R3C.complete')
    if ($smoke.classpath -cne $cp -or $smoke.classesSha256 -cne (Hash (Join-Path $private 'census108R3C-classes.tsv'))) { throw 'OWN108R3C_SMOKE_CLASSPATH_DRIFT' }
    $rows = @(Get-Content -LiteralPath (Join-Path $private 'census108R3C-classes.tsv'))
    if ($rows.Count -ne 2) { throw 'OWN108R3C_BOTH_CENSUS_PINS_REQUIRED' }
    foreach ($line in $rows) {
        $fields = $line -split "`t"
        if ($fields.Count -ne 2 -or $fields[0] -cnotmatch '^Task10[45]Census\.class$' -or $fields[1] -cne (Hash (Join-Path $stage ('build/phantom-test/bin/' + $fields[0])))) { throw 'OWN108R3C_CENSUS_CLASS_DRIFT' }
    }
    & java -cp $cp org.l2jmobius.tests.phantoms.PhantomWorldCohort108R3Checks census $stage $cp
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_CENSUS_PROVENANCE_FAILED' }
    Java0
}
if ($Action -eq 'Bind') {
    if (!(Test-Path -LiteralPath (Join-Path $private 'owner108R3C.complete'))) { throw 'OWN108R3C_OWNER_CHAIN_REQUIRED' }
    CensusPins
    Clearance (Join-Path $archive 'before-bind')
    if (Test-Path -LiteralPath $database) { throw 'OWN108R3C_FRESH_PREPARE_ONLY' }
    $prepared = Properties (Join-Path $private 'owner.properties')
    if ($prepared.installationId -cne [IO.File]::ReadAllText((Join-Path $archive 'preparation108R3C.uuid')) -or (Hash (Join-Path $stage 'inputs.tsv')) -cne $prepared.inputsSha256 -or !(Test-Path -LiteralPath (Join-Path $private 'build108R3C.complete'))) { throw 'OWN108R3C_PREPARATION_DRIFT' }
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared108R3C-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source108R3C-inputs.tsv')
    $owner = [IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
    $owner = [regex]::Replace($owner, '(?m)^stage=.*$', ('stage=' + $stage.Replace('\', '/')))
    $owner = [regex]::Replace($owner, '(?m)^expiresUtc=.*$', ('expiresUtc=' + [DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'), $owner, [Text.UTF8Encoding]::new($false))
    foreach ($relative in @('inputs.tsv', 'private/db.created')) { Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force }
    Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path $receipts | Out-Null
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime108R3C-final.ini')
    $ini = [IO.File]::ReadAllText((Join-Path $private 'runtime108R3C-final.ini'))
    foreach ($key in @('PhantomPopulationActiveTarget', 'MaxMaterializedPhantoms')) {
        $pattern = '(?m)^(' + [regex]::Escape($key) + '[ \t]*=[ \t]*)[^\r\n]*'
        if ([regex]::Matches($ini, $pattern).Count -ne 1) { throw 'OWN108R3C_OVERLAY_KEY' }
        $ini = [regex]::Replace($ini, $pattern, { param($m) $m.Groups[1].Value + '16' })
    }
    [IO.File]::WriteAllText((Join-Path $game 'config/Custom/PhantomPlayers.ini'), $ini, [Text.UTF8Encoding]::new($false))
    Write-Output 'OWN108R3C_BOUND originalDB=true;newProfiles=0;privateCap=16;source128=UNCHANGED'
    return
}
if ($Action -eq 'Seal') {
    CensusPins
    foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
        if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108R3C_CENSUS_ARTIFACTS_REQUIRED_NO_SEAL' }
    }
    $ready = Get-Content -Raw -LiteralPath (Join-Path $archive 'ready108R3C.private.json') | ConvertFrom-Json
    if (!$ready.dbFreeGreen -or !$ready.sourceAuditComplete -or $ready.reviewP1 -ne 0 -or $ready.reviewP2 -ne 0) { throw 'OWN108R3C_PRE_NATIVE_GATE_REQUIRED' }
    Clearance (Join-Path $archive 'before-seal')
    if (Test-Path -LiteralPath (Join-Path $private 'recovery108R3C.properties')) { throw 'OWN108R3C_SINGLE_SEAL_REQUIRED' }
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
    New-Text (Join-Path $private 'recovery108R3C.properties') ("task=PHANTOM-WORLD-COHORT-RECOVERY-108R3C`nnoReseed=true`njarSha256=" + (Hash (Join-Path $shadow 'dist/libs/GameServer.jar')) + "`nsourceOwnerSha256=" + (Hash (Join-Path $module 'dist/game/config/OwnerConfig.json')) + "`nsourceIniSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')) + "`nsourceMerchantSha256=" + (Hash (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))
    Write-Output 'OWN108R3C_SEALED namespace=108R3C;SAME_DB=true;cap=16;NO_FORCE_ROUTE=true'
    return
}

function Census([string]$Cut) {
    Clearance (Join-Path $archive ('clearance-' + $Cut))
    $out = Join-Path $receipts ('readonly-' + $Cut)
    New-Item -ItemType Directory -Path $out | Out-Null
    & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true') { throw 'OWN108R3C_READONLY_CENSUS_FAILED' }
    $before = switch ($Cut) { 'beforeA' { Join-Path $archive 'g0/first-preimage.private.tsv' } 'afterA' { Join-Path $archive 'clearance-beforeA/first-preimage.private.tsv' } 'beforeB' { Join-Path $archive 'clearance-afterA/first-preimage.private.tsv' } 'afterB' { Join-Path $archive 'clearance-beforeB/first-preimage.private.tsv' } default { throw 'OWN108R3C_EXACT_CUT' } }
    $witness = switch ($Cut) { 'beforeA' { $recoveryWitness } 'beforeB' { Join-Path $receipts 'merchants107-prestop6.private.tsv' } 'afterA' { Join-Path $receipts 'merchants107-prestop6.private.tsv' } 'afterB' { Join-Path $receipts 'merchants107-prestop4.private.tsv' } }
    $after = Join-Path $archive ('clearance-' + $Cut + '/first-preimage.private.tsv')
    & java -Xmx2g -cp $cp org.l2jmobius.tests.phantoms.PhantomMarketDelta108R2Checks market $before $after $ids $witness $sourcePolicy 1>(Join-Path $out 'market108R3C.log') 2>(Join-Path $out 'market108R3C.stderr.private.txt')
    if ($LASTEXITCODE -ne 0) { throw 'OWN108R3C_MARKET_DELTA_REQUIRES_SEPARATE_AUDIT_NO_NEXT_RUNTIME' }
    & java -Xmx2g -cp $cp Task105Census diff $before $after $ids 1>(Join-Path $out 'delta105.log') 2>(Join-Path $out 'delta105.stderr.private.txt')
    if ($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'delta105.log')) -notmatch 'durableREADY=250;' -or [IO.File]::ReadAllText((Join-Path $out 'delta105.log')) -notmatch 'negativeRows=\{\}') { throw 'OWN108R3C_DURABLE_IDENTITY_DELTA_FAILED' }
    Java0
    return $out
}
foreach ($name in @('Task104Census.class', 'Task105Census.class')) {
    if (!(Test-Path -LiteralPath (Join-Path $stage ('build/phantom-test/bin/' + $name)) -PathType Leaf)) { throw 'OWN108R3C_CENSUS_ARTIFACTS_REQUIRED_NO_LAUNCH' }
}
CensusPins
$seal = Properties (Join-Path $private 'recovery108R3C.properties')
foreach ($pair in @(@('jarSha256', (Join-Path $shadow 'dist/libs/GameServer.jar')), @('sourceOwnerSha256', (Join-Path $module 'dist/game/config/OwnerConfig.json')), @('sourceIniSha256', (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')), @('sourceMerchantSha256', (Join-Path $module 'dist/game/config/Custom/PhantomMerchants.ini')))) {
    if ($seal[$pair[0]] -cne (Hash $pair[1])) { throw 'OWN108R3C_SEAL_DRIFT' }
}
$phase = if ($Action -in @('A','AcceptA')) { 6 } else { 4 }
function NativeEvidence([int]$Generation) {
    if (!(Test-Path -LiteralPath (Join-Path $receipts "world$Generation.pass")) -or
        [IO.File]::ReadAllText((Join-Path $receipts "world$Generation.pass")) -notmatch '^WORLD_GATE=PASS;first=16;second=16;stable=16;' -or
        !(Test-Path -LiteralPath (Join-Path $receipts "stop$Generation.natural"))) { throw 'OWN108R3C_SEPARATE_WORLD_AND_NATURAL_STOP_REQUIRED' }
    if ([IO.File]::ReadAllText((Join-Path $receipts "world$Generation-status.txt")) -notmatch '^actual=16/16;requestedCap=16;' -or
        [IO.File]::ReadAllText((Join-Path $receipts "logout108R3C-terminal$Generation.txt")) -notmatch '^calls=1;outstanding=0;event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;') { throw 'OWN108R3C_ACTUAL_WORLD_LOGOUT_REQUIRED' }
    foreach ($cut in @('first','second','prestop','stopped')) {
        if (![IO.File]::ReadAllText((Join-Path $receipts ("merchants107-" + $cut + $Generation + '.txt'))).StartsWith('owners=250;open=250;types={BUY=63, MANUFACTURE=25, SELL=162};')) { throw 'OWN108R3C_ACTUAL250_AND_SPLIT_REQUIRED' }
    }
}
if ($Action -in @('AcceptA','AcceptB')) {
    $pass = Get-Content -Raw -LiteralPath (Join-Path $receipts "postflight$phase.pass.json") | ConvertFrom-Json
    $review = Get-Content -Raw -LiteralPath (Join-Path $archive "review$phase.private.json") | ConvertFrom-Json
    if (!$review.independent -or $review.P1 -ne 0 -or $review.P2 -ne 0 -or $review.postflightSha256 -cne (Hash (Join-Path $receipts "postflight$phase.pass.json"))) { throw 'OWN108R3C_INDEPENDENT_PASS_REQUIRED_NO_CLEAN' }
    foreach ($pin in $pass.pins) { if ((Hash $pin.path) -cne $pin.sha256) { throw 'OWN108R3C_POSTFLIGHT_PROOF_DRIFT' } }
    NativeEvidence $phase
    New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;natural=true;jarSha256=" + $seal.jarSha256)
    New-Text (Join-Path $receipts "clean$phase.properties") ("postflightSha256=" + (Hash (Join-Path $receipts "postflight$phase.pass.json")) + "`nreviewSha256=" + (Hash (Join-Path $archive "review$phase.private.json")) + "`nexitSha256=" + (Hash (Join-Path $receipts "exit$phase.clean")))
    Write-Output "OWN108R3C_GENERATION_CLEAN phase=$phase;independentReview=PASS;natural=true"
    return
}
if (Test-Path -LiteralPath (Join-Path $receipts "launch$phase.begun")) { throw 'OWN108R3C_SINGLE_GENERATION_REQUIRED' }
if ($Action -eq 'B') {
    NativeEvidence 6
    foreach ($name in @('phase6.complete', 'stopped6.checkpoint', 'stop6-world-leases.txt', 'exit6.clean')) { if (!(Test-Path -LiteralPath (Join-Path $receipts $name))) { throw 'OWN108R3C_A_FULL_CLEAN_REQUIRED' } }
    if ([IO.File]::ReadAllText((Join-Path $receipts 'exit6.clean')) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;' -or [IO.File]::ReadAllText((Join-Path $receipts 'merchants107-prestop6.txt')) -notmatch ';market=PASS') { throw 'OWN108R3C_A_NATURAL_CLEAN_MARKET_REQUIRED' }
    $clean = Properties (Join-Path $receipts 'clean6.properties')
    if ($clean.postflightSha256 -cne (Hash (Join-Path $receipts 'postflight6.pass.json')) -or $clean.reviewSha256 -cne (Hash (Join-Path $archive 'review6.private.json')) -or $clean.exitSha256 -cne (Hash (Join-Path $receipts 'exit6.clean'))) { throw 'OWN108R3C_INDEPENDENT_CLEAN_SIGNATURE_DRIFT' }
}
$pre = Census ('before' + $Action)
if ($Action -eq 'B' -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts 'stopped6.checkpoint')).TrimEnd([char[]]"`r`n")) { throw 'OWN108R3C_PRESTART_CHECKPOINT_DRIFT' }
New-Text (Join-Path $receipts "launch$phase.begun") ('utc=' + [DateTime]::UtcNow.ToString('o'))
$uuid = $original.installationId
$arguments = @('-Xms512m', '-Xmx8g', '-Dphantom.task102.native=true', "-Dphantom.task102.stage=$stage", "-Dphantom.task102.uuid=$uuid", "-Dphantom.module.root=$shadow", "-Dphantom.test.config=$database", '-cp', $cp, 'org.l2jmobius.tests.phantoms.PhantomPopulation102Native', ('recovery108R3C' + $Action))
$line = ($arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join ' '
$process = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
$processHandle = $null
$handleFailure = $null
try { $processHandle = Hold-ProcessHandle $process }
catch { $handleFailure = $_.Exception.GetType().FullName }
$os = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $process.Id)
if (!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)) { throw 'OWN108R3C_NEW_PROCESS_IDENTITY_UNPROVEN_NO_RETRY' }
$digest = [Security.Cryptography.SHA256]::Create()
try { $commandHash = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-', '').ToLowerInvariant() } finally { $digest.Dispose() }
New-Text (Join-Path $receipts "process$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256 } | ConvertTo-Json)
if ($null -ne $handleFailure) {
    New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'HANDLE_UNAVAILABLE_NO_RETRY'; failure = $handleFailure; forceApproval = $false } | ConvertTo-Json)
    throw 'OWN108R3C_HANDLE_UNAVAILABLE;NO_B;NO_RETRY;NO_FORCE'
}
Write-Output "OWN108R3C_NATIVE_STARTED generation=$Action;cap=16;newProfiles=FORBIDDEN"
$deadline = [DateTime]::UtcNow.AddMinutes(12)
while (!$process.WaitForExit(1000)) {
    if ((Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")) -or [DateTime]::UtcNow -gt $deadline) {
        New-Text (Join-Path $receipts "retained$phase.private.json") (@{ PID = $process.Id; CreationUTC = $os.CreationDate.ToUniversalTime().ToString('o'); SID = $sid; Executable = $os.ExecutablePath; CommandSHA256 = $commandHash; Stage = $stage; Installation = $uuid; JarSHA256 = $seal.jarSha256; status = 'STOP_BLOCKED_NO_RETRY'; forceApproval = $false } | ConvertTo-Json)
        throw 'OWN108R3C_STOP_BLOCKED;NO_B;NO_RETRY;NO_FORCE'
    }
}
try { $exit = Get-TrackedProcessExitCode $process $processHandle }
catch {
    New-Text (Join-Path $receipts "exit$phase.unknown") ('status=UNKNOWN;utc=' + [DateTime]::UtcNow.ToString('o') + ';failure=' + $_.Exception.GetType().FullName)
    throw 'OWN108R3C_EXIT_STATUS_UNKNOWN;NO_B;NO_RETRY;NO_FORCE'
}
New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc=" + [DateTime]::UtcNow.ToString('o'))
if ($exit -ne 0) { throw 'OWN108R3C_NATIVE_EXIT_FAILED_NO_NEXT_RUNTIME' }
Java0
$post = Census ('after' + $Action)
$boundary = [IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))
if ((!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -and !(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.diagnostic.complete"))) -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
    $boundary -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=250;attributedMerchants=250;samples=2;nativePersisted=true\r?\n?$') { throw 'OWN108R3C_FULL_NATIVE_STOP_PROOF_REQUIRED' }
if ([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")) { throw 'OWN108R3C_POSTEXIT_CHECKPOINT_DRIFT' }
if ([IO.File]::ReadAllText((Join-Path $receipts "logout108R3C-terminal$phase.txt")) -notmatch '^calls=1;outstanding=0;event=ON_PLAYER_LOGOUT;semantics=EARNED;ownerCurrent=true;') { throw 'OWN108R3C_STOP_EARNED_REQUIRED' }
New-Text (Join-Path $receipts "stop$phase.natural") 'STOP_DIAGNOSTIC_PASS;natural=true;exit=0;JVM=0;SQL=0;TCP=0;EARNED=1;outstanding=0;samples=2;checkpoint=EXACT'
Protected
$proofs = @((Join-Path $receipts "exit$phase.observed"), (Join-Path $receipts "stopped$phase.checkpoint"), (Join-Path $receipts "stop$phase-world-leases.txt"), (Join-Path $receipts "logout108R3C-terminal$phase.txt"), (Join-Path $post 'durable105.checkpoint'), (Join-Path $post 'market108R3C.log'), (Join-Path $post 'delta105.log'), (Join-Path $post 'sessions.txt'), (Join-Path $archive ('clearance-after' + $Action + '/first-preimage.private.tsv')), (Join-Path $archive ('clearance-after' + $Action + '/clearance.json')), (Join-Path $private 'native-inputs.tsv'))
$worldOutcome = if (Test-Path -LiteralPath (Join-Path $receipts "world$phase.pass")) { "world$phase.pass" } else { "world$phase.fail" }
$proofs += @((Join-Path $receipts $worldOutcome), (Join-Path $receipts "stop$phase.natural"), (Join-Path $receipts "world$phase-first.tsv"), (Join-Path $receipts "world$phase-second.tsv"), (Join-Path $receipts "world$phase-status.txt"))
foreach ($cut in @('first','second','prestop','stopped')) { $proofs += Join-Path $receipts ("merchants107-" + $cut + $phase + '.txt') }
$proofPins = @($proofs | ForEach-Object { @{path=$_;sha256=(Hash $_)} })
New-Text (Join-Path $receipts "postflight$phase.pass.json") (@{phase=$phase;natural=$true;JVM=0;SQL=0;TCP=0;jarSha256=$seal.jarSha256;pins=$proofPins} | ConvertTo-Json -Depth 5)
if (!(Test-Path -LiteralPath (Join-Path $receipts "world$phase.pass"))) {
    Write-Output "OWN108R3C_WORLD_FAIL;STOP_DIAGNOSTIC_PASS;FULL=BLOCKED;NO_CLEAN;NO_B;NO_RETRY;NO_FORCE"
    throw 'OWN108R3C_WORLD_FAIL_AFTER_DIAGNOSTIC_STOP_NO_CLEAN_NO_B'
}
NativeEvidence $phase
Write-Output "OWN108R3C_POSTFLIGHT_PASS generation=$Action;independentReview=REQUIRED;exit.clean=ABSENT;NO_NEXT_NATIVE_UNTIL_ACCEPT"
