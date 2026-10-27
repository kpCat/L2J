[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Build','Files','Native','Restart','Postflight','Stop','Route')][string]$Action,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][ValidatePattern('^v[1-9][0-9]*$')][string]$Candidate,
    [ValidatePattern('^$|^v[1-9][0-9]*$')][string]$Previous = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module094 = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$repo094 = Split-Path $module094 -Parent
$stage094 = [IO.Path]::GetFullPath($Stage).TrimEnd('\')
if (![IO.Path]::IsPathRooted($Stage) -or $Stage -cne $stage094 -or $stage094.StartsWith($repo094 + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'TASK094_EXTERNAL_ABSOLUTE_STAGE' }
foreach ($path094 in @($module094,$stage094)) {
    $cursor094 = Get-Item -LiteralPath $path094 -Force
    while ($null -ne $cursor094) {
        if ($cursor094.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'TASK094_REPARSE' }
        $cursor094 = $cursor094.Parent
    }
}
$identity094 = [Security.Principal.WindowsIdentity]::GetCurrent()
$acl094 = Get-Acl -LiteralPath $stage094
if (!$acl094.AreAccessRulesProtected -or $acl094.Owner -ine $identity094.Name) { throw 'TASK094_PRIVATE_OWNER' }
foreach ($rule094 in $acl094.Access) {
    if ($rule094.AccessControlType -eq 'Allow' -and $rule094.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity094.User.Value,'S-1-5-18')) { throw 'TASK094_PRIVATE_ACL' }
}
$owner094 = Get-Content -LiteralPath (Join-Path $stage094 'private/owner.properties') -Raw | ConvertFrom-StringData
$shadow094 = Join-Path $stage094 'L2J_Mobius_CT_2.6_HighFive'
if ($owner094.owner -cne 'TASK094_PARTY_FARM' -or $owner094.schema -cne 'l2jmobiush5_phantom_test_094' -or [IO.Path]::GetFullPath($owner094.sourceModule) -cne $module094 -or [IO.Path]::GetFullPath($owner094.stage) -cne $stage094 -or [DateTimeOffset]::Parse($owner094.expiresUtc) -le [DateTimeOffset]::UtcNow) { throw 'TASK094_OWNER_BINDING' }
$candidate094 = Join-Path $stage094 ('candidate-' + $Candidate)
$utf8094 = [Text.UTF8Encoding]::new($false)
function Hash094([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-Physical094([string]$Path) {
    $probe094 = [IO.Path]::GetFullPath($Path)
    while (!(Test-Path -LiteralPath $probe094)) { $probe094 = Split-Path $probe094 -Parent }
    $item094 = Get-Item -LiteralPath $probe094 -Force
    while ($null -ne $item094) {
        if ($item094.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'TASK094_REPARSE' }
        $item094 = if ($item094 -is [IO.DirectoryInfo]) { $item094.Parent } else { $item094.Directory }
    }
}
function Assert-Tree094([string]$Path) {
    Assert-Physical094 $Path
    if (!(Test-Path -LiteralPath $Path)) { return }
    $pending094 = [Collections.Generic.Stack[string]]::new()
    $pending094.Push($Path)
    while ($pending094.Count) {
        foreach ($entry094 in Get-ChildItem -LiteralPath $pending094.Pop() -Force) {
            if ($entry094.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'TASK094_REPARSE' }
            if ($entry094.PSIsContainer) { $pending094.Push($entry094.FullName) }
        }
    }
}
function Write-New094([string]$Path,[string[]]$Lines) {
    $stream094 = [IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    $writer094 = [IO.StreamWriter]::new($stream094,$utf8094)
    try { foreach ($line094 in $Lines) { $writer094.WriteLine($line094) } } finally { $writer094.Dispose() }
}
if ($Action -eq 'Build') {
    Assert-Tree094 $shadow094
    Assert-Tree094 (Join-Path $stage094 'build')
    Assert-Tree094 (Join-Path $module094 'test/java')
    Assert-Physical094 $candidate094
    if ((& git -C $repo094 symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git -C $repo094 rev-parse HEAD) -cne $owner094.baseline) { throw 'TASK094_BASELINE_DRIFT' }
    if (!(Test-Path -LiteralPath (Join-Path $stage094 'bootstrap.complete')) -or (Test-Path -LiteralPath $candidate094)) { throw 'TASK094_FRESH_CANDIDATE_REQUIRED' }
    $base094 = @{}
    foreach ($line094 in [IO.File]::ReadLines((Join-Path $stage094 'inputs.tsv'))) {
        $fields094 = $line094.Split("`t")
        $base094[$fields094[1].Substring($shadow094.Length + 1).Replace('\','/')] = $fields094[2]
    }
    $prior094 = @{}
    if ($Previous) {
        $priorPath094 = Join-Path $stage094 ('candidate-' + $Previous)
        Assert-Tree094 $priorPath094
        if (!(Test-Path -LiteralPath (Join-Path $priorPath094 'seal.properties'))) {
            # A failed compile is only a checked copy predecessor, never an executable seal.
            $failedLog094 = Join-Path $priorPath094 'build.log'
            if (!(Test-Path -LiteralPath (Join-Path $priorPath094 'build.started')) -or !(Test-Path -LiteralPath (Join-Path $priorPath094 'overlay.tsv')) -or !(Test-Path -LiteralPath $failedLog094) -or !(Select-String -LiteralPath $failedLog094 -Pattern '^BUILD FAILED$' -Quiet) -or (Test-Path -LiteralPath (Join-Path $priorPath094 'native.log')) -or (Test-Path -LiteralPath (Join-Path $priorPath094 'restart.log'))) { throw 'TASK094_PRIOR_SEAL_OR_COMPILE_FAILURE_REQUIRED' }
        }
        foreach ($line094 in [IO.File]::ReadLines((Join-Path $priorPath094 'overlay.tsv'))) { $fields094 = $line094.Split("`t"); $prior094[$fields094[0]] = $fields094[2] }
    }
    $changed094 = @('PhantomSystem.java','party/PhantomAutonomousFarmParty.java','party/PhantomPartyBackend.java','party/L2jPhantomPartyBackend.java','party/PhantomPartyCoordinator.java','party/PhantomPartyRoleMatcher.java','party/PhantomPartyTactics.java','combat/PhantomSupportEffectAuthority.java','combat/L2jCombatBackend.java') | ForEach-Object { 'java/org/l2jmobius/gameserver/phantoms/' + $_ }
    $changed094 += @('java/org/l2jmobius/gameserver/model/actor/Attackable.java','java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java')
    $changed094 += 'java/org/l2jmobius/gameserver/model/conditions/ConditionTargetRace.java'
    $changed094 += 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java'
    $changed094 += @('java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundGoalSpec.java','java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java')
    $changed094 += 'java/org/l2jmobius/gameserver/phantoms/background/PhantomOwnedStoreIntent.java'
    $changed094 += @('java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java','java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundState.java')
    $changed094 += 'dist/game/data/phantoms/topology/high-five-career-recovery.xml'
    $changed094 += 'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleFarmTravel.java'
    $changed094 += 'java/org/l2jmobius/gameserver/phantoms/navigation/PhantomWalkCorridorPlanner.java'
    $changed094 += @('PhantomNavigationRequest.java','PhantomNavigationService.java','L2jNavigationBackend.java') | ForEach-Object { 'java/org/l2jmobius/gameserver/phantoms/navigation/' + $_ }
    $changed094 += @('party/PhantomPartyRegroupState.java','party/PhantomPartyPersistencePort.java','party/PhantomPartyStore.java','combat/PhantomCombatActorLease.java','combat/PhantomCombatService.java') | ForEach-Object { 'java/org/l2jmobius/gameserver/phantoms/' + $_ }
    $changed094 += 'java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java'
    New-Item -ItemType Directory -Path $candidate094 | Out-Null
    Write-New094 (Join-Path $candidate094 'build.started') @('TASK094 candidate build; no DB or native start')
    foreach ($jarName094 in @('GameServer.jar','LoginServer.jar')) {
        $existingJar094 = Join-Path $shadow094 ('dist/libs/' + $jarName094)
        if (Test-Path -LiteralPath $existingJar094) {
            $retainedJar094 = Join-Path $candidate094 ('before-' + $jarName094)
            [IO.File]::Copy($existingJar094,$retainedJar094,$false)
            if ((Hash094 $existingJar094) -cne (Hash094 $retainedJar094)) { throw 'TASK094_JAR_BACKUP_SHA' }
        }
    }
    $overlay094 = [Collections.Generic.List[string]]::new()
    foreach ($relative094 in $changed094) {
        $source094 = Join-Path $module094 $relative094
        $target094 = Join-Path $shadow094 $relative094
        Assert-Physical094 $source094
        Assert-Physical094 $target094
        $baseline094 = if ($base094.ContainsKey($relative094)) { $base094[$relative094] } else { '-' }
        if (Test-Path -LiteralPath $target094) {
            $expected094 = if ($prior094.ContainsKey($relative094)) { $prior094[$relative094] } else { $baseline094 }
            $before094 = Hash094 $target094
            if ($before094 -cne $expected094) { throw 'TASK094_SHADOW_PREIMAGE_DRIFT' }
            $backup094 = Join-Path $candidate094 ('preimage/' + $relative094)
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($backup094)) | Out-Null
            [IO.File]::Copy($target094,$backup094,$false)
            if ((Hash094 $backup094) -cne $before094) { throw 'TASK094_PREIMAGE_BACKUP_SHA' }
        } elseif ($baseline094 -cne '-') { throw 'TASK094_MISSING_BASE_INPUT' }
        $after094 = Hash094 $source094
        $temporary094 = $target094 + '.task094-' + $Candidate + '.tmp'
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target094)) | Out-Null
        [IO.File]::Copy($source094,$temporary094,$false)
        if ((Hash094 $temporary094) -cne $after094) { throw 'TASK094_COPY_SHA' }
        if (Test-Path -LiteralPath $target094) {
            $atomicBackup094 = $backup094 + '.atomic'
            if (Test-Path -LiteralPath $atomicBackup094) { throw 'TASK094_ATOMIC_BACKUP_EXISTS' }
            [IO.File]::Replace($temporary094,$target094,$atomicBackup094)
            if ((Hash094 $atomicBackup094) -cne $before094) { throw 'TASK094_ATOMIC_BACKUP_SHA' }
        } else { [IO.File]::Move($temporary094,$target094) }
        $overlay094.Add($relative094 + "`t" + $baseline094 + "`t" + $after094)
    }
    Write-New094 (Join-Path $candidate094 'overlay.tsv') $overlay094.ToArray()
    $testSources094 = @(Get-ChildItem -LiteralPath (Join-Path $module094 'test/java') -Recurse -File -Filter '*.java' | Sort-Object FullName | ForEach-Object { $_.FullName.Substring($module094.Length + 1).Replace('\','/') + "`t" + (Hash094 $_.FullName) })
    Write-New094 (Join-Path $candidate094 'test-sources.tsv') $testSources094
    & ant -f (Join-Path $shadow094 'build.xml') ('-Dbuild=' + (Join-Path $stage094 'build')) ('-Dtest.src=' + (Join-Path $module094 'test/java')) compile-tests jar > (Join-Path $candidate094 'build.log') 2>&1
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $candidate094 'build.log') -Tail 30; throw 'TASK094_BUILD_FAILED' }
    & jar --create --file (Join-Path $candidate094 'Test094.jar') -C (Join-Path $stage094 'build/phantom-test/bin') .
    if ($LASTEXITCODE -ne 0) { throw 'TASK094_TEST_JAR_FAILED' }
    $sqlPin094 = Get-Content -LiteralPath (Join-Path $stage094 'pin.complete') -Raw | ConvertFrom-StringData
    Write-New094 (Join-Path $candidate094 'seal.properties') @(
        ('baseline=' + $owner094.baseline), ('installationId=' + $owner094.installationId),
        ('inputsSha256=' + $owner094.inputsSha256), ('sourceCompletionSha256=' + (Hash094 (Join-Path $stage094 'source-completion.tsv'))),
        ('overlaySha256=' + (Hash094 (Join-Path $candidate094 'overlay.tsv'))), ('testSourcesSha256=' + (Hash094 (Join-Path $candidate094 'test-sources.tsv'))),
        ('sqlAggregate=' + $sqlPin094.sqlAggregate), ('gameJarSha256=' + (Hash094 (Join-Path $shadow094 'dist/libs/GameServer.jar'))),
        ('loginJarSha256=' + (Hash094 (Join-Path $shadow094 'dist/libs/LoginServer.jar'))), ('testJarSha256=' + (Hash094 (Join-Path $candidate094 'Test094.jar')))
    )
    Write-Output ('TASK094_BUILD_SEALED candidate=' + $Candidate + ' sourceOverlay=' + $changed094.Count + ' tests=' + $testSources094.Count)
    exit 0
}
if (!(Test-Path -LiteralPath (Join-Path $candidate094 'seal.properties'))) { throw 'TASK094_CANDIDATE_SEAL_REQUIRED' }
if ($Action -eq 'Stop') {
    Assert-Tree094 $candidate094
    if (!(Test-Path -LiteralPath (Join-Path $candidate094 'native.log')) -and !(Test-Path -LiteralPath (Join-Path $candidate094 'restart.log'))) { throw 'TASK094_RUNNING_CANDIDATE_REQUIRED' }
    Write-New094 (Join-Path $candidate094 'stop.requested') @('TASK094 cooperative stop; normal owner drain and STORE required')
    Write-Output 'TASK094_COOPERATIVE_STOP_REQUESTED'
    exit 0
}
$classpath094 = (Join-Path $shadow094 'dist/libs/GameServer.jar') + ';' + (Join-Path $candidate094 'Test094.jar') + ';' + (Join-Path $stage094 'build/phantom-test/resources') + ';' + (Join-Path $shadow094 'dist/libs/*')
$arguments094 = @('-Xms256m','-Xmx6g','-Dfile.encoding=UTF-8','-Dphantom.task094.native=true',('-Dphantom.task094.root=' + $stage094),('-Dphantom.task094.uuid=' + $owner094.installationId),('-Dphantom.task094.candidate=' + $candidate094),('-Dphantom.module.root=' + $shadow094),('-Dphantom.test.config=' + (Join-Path $shadow094 'dist/game/config/Database.ini')),'-cp',$classpath094,'org.l2jmobius.tests.phantoms.PhantomAutonomousParty094Native',$Action.ToLowerInvariant())
$log094 = Join-Path $candidate094 ($Action.ToLowerInvariant() + '.log')
if (Test-Path -LiteralPath $log094) { throw 'TASK094_EXISTING_RUN_LOG_PRESERVED' }
Push-Location -LiteralPath (Join-Path $shadow094 'dist/game')
try { & java @arguments094 > $log094 2>&1; $code094 = $LASTEXITCODE }
finally { Pop-Location }
Get-Content -LiteralPath $log094 -Tail 24
exit $code094
