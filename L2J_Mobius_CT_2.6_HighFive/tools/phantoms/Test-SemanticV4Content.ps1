[CmdletBinding()]
param(
    [string]$StageRoot = '',
    [switch]$Scale,
    [string]$RoundTripText = '',
    [string]$RoundTripCategory = '',
    [string]$OutputRoot = '',
    [string]$JdkBin = ''
)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repository = [IO.Path]::GetDirectoryName($module)
function Within([string]$Path, [string]$Root) {
    $p = [IO.Path]::GetFullPath($Path).TrimEnd('\'); $r = [IO.Path]::GetFullPath($Root).TrimEnd('\')
    return $p.Equals($r, [StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r + '\', [StringComparison]::OrdinalIgnoreCase)
}
function Plain([string]$Path) {
    $walk = [IO.Path]::GetFullPath($Path)
    while ($walk) {
        if ((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'BLOCKED_JAVA: linked path' }
        $walk = [IO.Path]::GetDirectoryName($walk)
    }
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
if (!$OutputRoot) { $OutputRoot = Join-Path $env:TEMP ('pw-semantic095-' + [guid]::NewGuid().ToString('N')) }
$scratch = [IO.Path]::GetFullPath($OutputRoot)
if ((Within $scratch $repository) -or (Within $repository $scratch) -or (Test-Path -LiteralPath $scratch)) { throw 'BLOCKED_JAVA: a new disjoint external output root is required' }
Plain $scratch
if ($StageRoot) {
    if (![IO.Path]::IsPathRooted($StageRoot)) { throw 'BLOCKED_JAVA: absolute compiled stage path required' }
    $StageRoot = [IO.Path]::GetFullPath($StageRoot); Plain $StageRoot
    if (Within $StageRoot $repository) { throw 'BLOCKED_JAVA: runtime install is not a test stage' }
}
if (($Scale -or $RoundTripText) -and !$StageRoot) { throw 'BLOCKED_JAVA: stage required' }
if ($RoundTripText -and !$RoundTripCategory) { throw 'BLOCKED_JAVA: expected roundtrip category required' }
if (!$JdkBin) { $JdkBin = Split-Path (Get-Command javac -ErrorAction Stop).Source -Parent }
$java = Join-Path $JdkBin 'java.exe'; $javac = Join-Path $JdkBin 'javac.exe'
Plain $java; Plain $javac
$sources = @(
    'commons/config/DatabaseConfig', 'commons/database/DatabaseFactory', 'commons/time/TimeUtil', 'commons/util/ConfigReader',
    'gameserver/config/custom/PhantomPlayersConfig',
    'gameserver/phantoms/activity/PhantomSchedulerControlPort',
    'gameserver/phantoms/activity/PhantomCompositeSchedulerControlPort',
    'gameserver/model/actor/enums/creature/Race', 'gameserver/model/actor/enums/player/PlayerClass',
    'gameserver/model/chat/ChatObservationService', 'gameserver/network/enums/ChatType',
    'gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog',
    'gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService',
    'gameserver/phantoms/conversation/humanized/PhantomHumanizedV4Catalog',
    'gameserver/phantoms/conversation/humanized/PhantomPersonalConversationStore',
    'gameserver/phantoms/conversation/humanized/PhantomSpeechFacetProfile',
    'gameserver/phantoms/conversation/PhantomConversationModel', 'gameserver/phantoms/conversation/PhantomConversationExecutionModel',
    'gameserver/phantoms/decision/PhantomDomainRef', 'gameserver/phantoms/semantic/understanding/PhantomSemanticModel',
    'gameserver/phantoms/profile/PhantomProfile', 'gameserver/phantoms/profile/PhantomProfileComponent',
    'gameserver/phantoms/profile/PhantomProfilePersistenceException', 'gameserver/phantoms/profile/PhantomProfileRepository',
    'gameserver/phantoms/social/PhantomSocialCatalog', 'gameserver/phantoms/social/PhantomSocialEventSink',
    'gameserver/phantoms/social/PhantomSocialModel', 'gameserver/phantoms/social/PhantomSocialReceiptLedger',
    'gameserver/phantoms/social/PhantomSocialService'
    'gameserver/phantoms/social/PhantomPvpSocialBridge', 'gameserver/phantoms/social/PhantomSocialAffiliationContextPort'
) | ForEach-Object { 'java/org/l2jmobius/' + $_ + '.java' }
$sources += @('PhantomSemanticV4ContentProbe', 'PhantomPost002SemanticV3Suite', 'PhantomTestContext', 'PhantomTestRegistry', 'PhantomTestSuite', 'PhantomAssertions') |
    ForEach-Object { 'test/java/org/l2jmobius/tests/phantoms/' + $_ + '.java' }
$library = 'dist/libs/HikariCP-7.0.2.jar'
if ((Hash (Join-Path $module $library)) -ne 'f1e612fa27345be3107a85431e8a8aeb205c15364ab2f2d411e40a9d7bb08095') { throw 'BLOCKED_JAVA: dependency pin mismatch' }
if ((Hash (Join-Path $module 'java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java')) -ne '01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05') { throw 'BLOCKED_JAVA: legacy catalog pin mismatch' }
New-Item -ItemType Directory -Path $scratch | Out-Null
$shadow = Join-Path $scratch 'module'; $classes = Join-Path $scratch 'classes'
$inventory = [Collections.Generic.List[object]]::new()
function CopyExact([string]$Relative) {
    $source = Join-Path $module $Relative; $destination = Join-Path $shadow $Relative
    Plain $source; Plain $destination
    $stamp = [pscustomobject]@{ Path = $Relative; Sha256 = (Hash $source); Bytes = (Get-Item -LiteralPath $source).Length }
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination
    if ((Hash $destination) -ne $stamp.Sha256) { throw 'BLOCKED_JAVA: copy hash mismatch' }
    $inventory.Add($stamp)
}
foreach ($relative in $sources) { CopyExact $relative }
CopyExact 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
CopyExact $library
foreach ($family in @('dist/game/data/phantoms/semantic/humanized', 'dist/game/data/phantoms/conversation/humanized', 'dist/game/data/phantoms/semantic/custom', 'dist/game/data/phantoms/conversation/custom')) {
    $folder = Join-Path $module $family
    if (!(Test-Path -LiteralPath $folder)) { continue }
    Plain $folder
    $pending = [Collections.Generic.Stack[string]]::new(); $pending.Push($folder)
    while ($pending.Count) {
        foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
            Plain $item.FullName
            if ($item.PSIsContainer) { $pending.Push($item.FullName) }
            elseif ($item.Extension -in @('.xml', '.tsv')) { CopyExact $item.FullName.Substring($module.Length + 1) }
            else { throw 'BLOCKED_JAVA: unexpected legacy content file' }
        }
    }
}
CopyExact 'dist/game/data/phantoms/social/high-five-social-v1.xml'
if ($inventory.Count -gt 300 -or ($inventory | Measure-Object Bytes -Sum).Sum -gt 16777216) { throw 'BLOCKED_JAVA: isolated input cap' }
$inventory | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $scratch 'input-inventory.json') -Encoding utf8
New-Item -ItemType Directory -Path $classes | Out-Null
$arguments = @('-encoding', 'UTF-8', '-d', $classes, '-sourcepath', ((Join-Path $shadow 'java') + ';' + (Join-Path $shadow 'test/java')), '-classpath', (Join-Path $shadow $library))
$arguments += $sources | ForEach-Object { Join-Path $shadow $_ }
$saved = @{}
foreach ($key in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH')) { $saved[$key] = [Environment]::GetEnvironmentVariable($key); Remove-Item -LiteralPath ('Env:' + $key) -ErrorAction SilentlyContinue }
try {
    & $javac @arguments 2>&1 | Tee-Object -FilePath (Join-Path $scratch 'compile.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_JAVA: isolated production closure compilation failed' }
    $probeArguments = @('-Xmx256m', ('-Djava.io.tmpdir=' + $scratch), '-cp', $classes, 'org.l2jmobius.tests.phantoms.PhantomSemanticV4ContentProbe', (Join-Path $shadow 'dist/game/data/phantoms'), $scratch)
    if ($Scale) { $probeArguments += @('--scale', $StageRoot) }
    elseif ($RoundTripText) { $probeArguments += @('--roundtrip', $StageRoot, $RoundTripText, $RoundTripCategory) }
    elseif ($StageRoot) { $probeArguments += $StageRoot }
    & $java @probeArguments 2>&1 | Tee-Object -FilePath (Join-Path $scratch 'probe.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_JAVA: isolated consumer probe failed' }
    foreach ($stamp in $inventory) { if ((Hash (Join-Path $module $stamp.Path)) -ne $stamp.Sha256 -or (Hash (Join-Path $shadow $stamp.Path)) -ne $stamp.Sha256) { throw 'SOURCE_DRIFT: isolated proof input changed' } }
    Write-Output ('PRODUCTION_CONTENT_PROOF_PASS sourceFiles=' + $sources.Count + ' inputFiles=' + $inventory.Count + ' inventorySha256=' + (Hash (Join-Path $scratch 'input-inventory.json')) + ' scratch=' + $scratch)
}
finally { foreach ($key in $saved.Keys) { if ($null -eq $saved[$key]) { Remove-Item -LiteralPath ('Env:' + $key) -ErrorAction SilentlyContinue } else { [Environment]::SetEnvironmentVariable($key, $saved[$key]) } } }
