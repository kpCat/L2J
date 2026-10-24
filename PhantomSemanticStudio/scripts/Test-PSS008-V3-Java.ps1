param([Parameter(Mandatory=$true)][string]$StageRoot, [string]$JdkBin = '', [string]$AntPath = '',
    [ValidateSet('IsolatedCatalog','AntContent')][string]$ProofMode = 'IsolatedCatalog')
$ErrorActionPreference = 'Stop'
$studio = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd('\')
$protected = [IO.Path]::GetFullPath((Join-Path $studio '..')).TrimEnd('\')
$scratch = $null
$result = [ordered]@{ Version = 1; JavaStatus = 'BLOCKED_JAVA'; Status = 'STAGED_V3_UNVALIDATED'; AntExitCode = $null; ProbeExitCode = $null; Reason = $null }
$isolated = $ProofMode -eq 'IsolatedCatalog'
if ($isolated) { $result.Version = 2; $result.ExecutionMode = 'ISOLATED_CATALOG_V1'; $result.Ant = 'NOT_RUN' }
# TASK081 path B, re-audited for TASK082 facets: complete production dependency closure, JDK-only; no Ant/OwnerConfig execution.
$catalogInputs = [ordered]@{
    'java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java' = '01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05'
    'java/org/l2jmobius/gameserver/model/actor/enums/player/PlayerClass.java' = '9678b970528f17192114669ad96b8538add396ec251f4fbc4476d57c1e64a2cf'
    'java/org/l2jmobius/gameserver/model/actor/enums/creature/Race.java' = '7cd9a7ba39b483b93c6fd47b1ef4572dc526fb0a06419bf918fafc823c5cbc0b'
}

function Within([string]$Path, [string]$Parent) {
    $p = [IO.Path]::GetFullPath($Path).TrimEnd('\'); $r = [IO.Path]::GetFullPath($Parent).TrimEnd('\')
    return $p.Equals($r, [StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r + '\', [StringComparison]::OrdinalIgnoreCase)
}
function NoLinks([string]$Path) {
    $p = [IO.Path]::GetFullPath($Path)
    while ($p) {
        try { if ([IO.File]::GetAttributes($p) -band [IO.FileAttributes]::ReparsePoint) { throw 'BLOCKED_JAVA: reparse path' } }
        catch [IO.FileNotFoundException] { }
        catch [IO.DirectoryNotFoundException] { }
        $p = [IO.Path]::GetDirectoryName($p)
    }
}
function Relative([string]$Root, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Name) -or $Name -match '\\|:|(^|/)(\.|\.\.)(/|$)|//|^/') { throw 'BLOCKED_JAVA: relative path' }
    $path = [IO.Path]::GetFullPath((Join-Path $Root $Name))
    if (-not (Within $path $Root)) { throw 'BLOCKED_JAVA: path escape' }; NoLinks $path; return $path
}
function HashFile([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function VerifyStamps([string]$Root, $Stamps) {
    foreach ($stamp in $Stamps) {
        $path = Relative $Root $stamp.RelativePath
        if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (HashFile $path) -ne $stamp.Sha256) { throw 'SOURCE_DRIFT: stamp mismatch' }
    }
}
function CopyStamps([string]$Root, [string]$Destination, $Stamps) {
    foreach ($stamp in $Stamps) {
        $from = Relative $Root $stamp.RelativePath; $to = Relative $Destination $stamp.RelativePath
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($to)) -Force | Out-Null
        NoLinks $to; Copy-Item -LiteralPath $from -Destination $to
    }
    VerifyStamps $Destination $Stamps
}
function FilesWithoutLinks([string]$Root) {
    NoLinks $Root; $pending = New-Object 'Collections.Generic.Stack[string]'; $pending.Push($Root)
    while ($pending.Count) {
        foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
            NoLinks $item.FullName
            if ($item.PSIsContainer) { $pending.Push($item.FullName) } else { $item }
        }
    }
}
function Native([string]$Tool, [string[]]$Arguments, [string]$Log) {
    # PowerShell 5 treats native stderr as ErrorRecord; preserve native exit explicitly.
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { & $Tool @Arguments 2>&1 | Out-File -LiteralPath $Log -Encoding utf8; return $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
}
function SafeBuild([string]$Path) {
    # TASK063: exact db375d6 baseline, CRLF/LF checkouts; task/classpath pins below stay unchanged.
    if ((HashFile $Path) -notin @('f6bebf5452655de3e08d8941134307650b830c2d0c1f31ded1cc1d0f2bd5fdc1','dafce95145d8d722138dfa16515aac0271110cd80254c204e308a03f78ecddca')) { throw 'BLOCKED_JAVA: unaudited build contract' }
    $settings = New-Object Xml.XmlReaderSettings; $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit; $settings.XmlResolver = $null
    # Stock Ant build has exactly this empty DOCTYPE; no DTD/entity is resolved.
    $text = [IO.File]::ReadAllText($Path).Replace('<!DOCTYPE xml>', '')
    $reader = [Xml.XmlReader]::Create((New-Object IO.StringReader $text), $settings); $xml = New-Object Xml.XmlDocument; $xml.XmlResolver = $null
    try { $xml.Load($reader) } finally { $reader.Dispose() }
    if ($xml.project.basedir -ne '.') { throw 'BLOCKED_JAVA: unexpected Ant basedir' }
    foreach ($node in $xml.project.ChildNodes) {
        if ($node.LocalName -notin @('#comment','description','property','path','pathconvert','target')) { throw 'BLOCKED_JAVA: unexpected top-level Ant task' }
    }
    $dependencies = @{
        'phantom-humanized-v3-content-validate' = 'compile-tests'; 'compile-tests' = 'compile,init-test';
        'compile' = 'init'; 'init' = 'checkRequirements'; 'init-test' = 'checkRequirements'; 'checkRequirements' = ''
    }
    # Hashes of XML task bodies read/audited for PSS-003 (whitespace discarded).
    # Pins every child, attribute, javac destdir and Java report/output argument.
    # An advanced local build must be re-audited, never silently executed.
    $shapes = @{
        'checkRequirements' = 'ecabfaadea6615d904acf35a0701ab0e43868123d445066d0d4b73db4932ac09'
        'init' = '299c4ee2d69ca26fde62285d0ea1c185c223163edad72a4c983ccb94e54e0b5c'
        'compile' = '0ac4d82081526a792629da358f35043ea1d68d52897d874d700b64e6f09e12ab'
        'init-test' = '60f2c2adaa98f99fddbd590caaf2f7bae27d7068e16b367b4ef05ac277a7ad8d'
        'compile-tests' = '658d171225abadfa2f057c07672530fa8874d509a2ea276eefd6ef424b3ff84e'
        'phantom-humanized-v3-content-validate' = '32d034e636e8e16ac1cffb04f51fb5d598edebfa8d805216e94466e8ce79ee62'
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    foreach ($name in $dependencies.Keys) {
        $target = @($xml.project.target | Where-Object { $_.GetAttribute('name') -eq $name })
        if ($target.Count -ne 1 -or $target[0].GetAttribute('depends') -ne $dependencies[$name]) { throw 'BLOCKED_JAVA: unexpected target dependency' }
        $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($target[0].InnerXml))).Replace('-','').ToLowerInvariant()
        if ($hash -ne $shapes[$name]) { throw 'BLOCKED_JAVA: unsafe reachable task shape' }
        foreach ($node in $target[0].SelectNodes('.//*')) {
            if ($node.LocalName -notin @('fail','condition','not','antversion','available','delete','mkdir','javac','copy','fileset','java','jvmarg','arg')) { throw 'BLOCKED_JAVA: unsafe reachable Ant task' }
            if ($node.LocalName -in @('delete','mkdir') -and $node.GetAttribute('dir') -notin @('${build.bin}','${build.test}','${build.test.bin}','${build.test.resources}','${build.test.reports}')) { throw 'BLOCKED_JAVA: unsafe build output' }
            if ($node.LocalName -eq 'copy' -and $node.GetAttribute('todir') -ne '${build.test.resources}') { throw 'BLOCKED_JAVA: unsafe copy output' }
        }
    }
    $paths = @{ 'classpath' = '516a87c7678560ac3a5dcbd5dfe1fc97b772a07b7b93371d48ad53e99a87de31'; 'test.classpath' = 'e3b561b5c0a70b8f92dcc21b2b08058ac20aae05a842b657cda3084f234f0ac6' }
    if (@($xml.project.path).Count -ne 2 -or @($xml.project.pathconvert).Count -ne 1) { throw 'BLOCKED_JAVA: unsafe reachable task shape' }
    foreach ($node in $xml.project.path) {
        $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($node.OuterXml))).Replace('-','').ToLowerInvariant()
        if ($hash -ne $paths[$node.id]) { throw 'BLOCKED_JAVA: unsafe reachable task shape' }
    }
    $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($xml.project.pathconvert.OuterXml))).Replace('-','').ToLowerInvariant()
    $sha.Dispose()
    if ($hash -ne '63ffa41193ec97e0c526940dbf0c27937afc6b13eda640e4b7655a534417a533') { throw 'BLOCKED_JAVA: unsafe reachable task shape' }
    $oracle = ($xml.project.target | Where-Object { $_.GetAttribute('name') -eq 'phantom-humanized-v3-content-validate' }).java
    if ($oracle.classname -ne 'org.l2jmobius.tests.phantoms.PhantomTestLauncher' -or $oracle.classpathref -ne 'test.classpath' -or
        $oracle.fork -ne 'true' -or $oracle.failonerror -ne 'true' -or $oracle.arg[0].value -ne 'post002-semantic-v3-content') { throw 'BLOCKED_JAVA: unexpected Java target' }
}

try {
    if (-not [IO.Path]::IsPathRooted($StageRoot)) { throw 'BLOCKED_JAVA: absolute stage path required' }
    $stage = [IO.Path]::GetFullPath($StageRoot).TrimEnd('\')
    $localWorkspace = Join-Path $env:LOCALAPPDATA 'PhantomSemanticStudio\workspace'
    if ((Within $stage $protected) -or $stage -notmatch '\\workspace\\v3-proposals\\[0-9a-f]{32}$' -or
        -not ((Within $stage (Join-Path $studio 'artifacts')) -or (Within $stage $localWorkspace))) { throw 'BLOCKED_JAVA: stage outside own finished workspace' }
    NoLinks $stage
    $runner = Join-Path $studio 'tests\PhantomSemanticStudio.Tests\bin\Release\net10.0\PhantomSemanticStudio.Tests.dll'
    if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) { throw 'BLOCKED_JAVA: build Studio Release tests first' }
    $integrity = & dotnet $runner --pss-008-inspect-stage $stage
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'PASS_V3_STAGE_INTEGRITY') { throw 'BLOCKED_JAVA: exact stage integrity rejected' }
    $receiptPath = Join-Path $stage 'receipt.json'; $receiptHash = HashFile $receiptPath
    $receipt = Get-Content -LiteralPath (Join-Path $stage 'receipt.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($receipt.Version -notin @(1,2,3) -or $receipt.Status -ne 'STAGED_V3_UNVALIDATED' -or $receipt.JavaStatus -ne 'NOT_RUN' -or -not $receipt.NotForInstallation -or
        -not $receipt.SelectionConfirmed -or -not $receipt.EditorialConfirmed -or $receipt.Candidates.Count -lt 1 -or $receipt.Candidates.Count -gt 20) { throw 'BLOCKED_JAVA: invalid stage receipt' }
    if ($receipt.Version -eq 3) {
        if (-not $isolated) { throw 'BLOCKED_JAVA: facet receipt requires isolated catalog proof' }
        $result.Version = 3; $result.ExecutionMode = 'ISOLATED_CATALOG_FACET_V1'
    }
    $source = [IO.Path]::GetFullPath($receipt.SourceModule).TrimEnd('\'); NoLinks $source
    if ((Within $stage $source) -or (Within $source $stage)) { throw 'BLOCKED_JAVA: overlapping source' }
    $sourceData = Join-Path $source 'dist\game\data\phantoms'; $stageData = Join-Path $stage 'module\dist\game\data\phantoms'
    VerifyStamps $sourceData $receipt.SourceFiles; VerifyStamps $stageData $receipt.StagedFiles
    foreach ($stamp in $receipt.SourceFiles) {
        $copy = @($receipt.StagedFiles | Where-Object RelativePath -eq $stamp.RelativePath)
        if ($copy.Count -ne 1 -or ($stamp.RelativePath -notin @($receipt.Pair.SemanticPath,$receipt.Pair.ConversationPath) -and
            ($copy[0].Sha256 -ne $stamp.Sha256 -or $copy[0].Bytes -ne $stamp.Bytes))) { throw 'BLOCKED_JAVA: non-target stage mutation' }
    }
    if ($receipt.SourceFiles.Count -ne $receipt.StagedFiles.Count) { throw 'BLOCKED_JAVA: stage inventory mismatch' }
    try {
        if (-not $JdkBin) { $JdkBin = Split-Path (Get-Command javac -ErrorAction Stop).Source -Parent }
        $javac = Join-Path $JdkBin 'javac.exe'; $java = Join-Path $JdkBin 'java.exe'
        $ant = if ($isolated) { $null } elseif ($AntPath) { $AntPath } else { (Get-Command ant -ErrorAction Stop).Source }
        $tools = if ($isolated) { @($java,$javac) } else { @($java,$javac,$ant) }
        foreach ($tool in $tools) { if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw 'missing tool' }; NoLinks $tool }
    } catch { throw 'BLOCKED_JAVA: required JDK/Ant path unavailable' }
    # Exact copy allowlist; no data/config/DB/credentials/.git/runtime files.
    $inventory = New-Object 'Collections.Generic.List[object]'
    if ($isolated) {
        foreach ($relative in $catalogInputs.Keys) {
            $file = Relative $source $relative
            if ((HashFile $file) -ne $catalogInputs[$relative]) { throw 'BLOCKED_JAVA: unaudited isolated production input' }
            $inventory.Add([pscustomobject]@{ RelativePath = $relative; Sha256 = (HashFile $file); Bytes = (Get-Item -LiteralPath $file).Length })
        }
    } else {
    $buildPath = Join-Path $source 'build.xml'; NoLinks $buildPath; SafeBuild $buildPath
    $catalogPath = Relative $source 'java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java'
    # 080 reviewed Java079 source at f7676d2; TASK082 adds only the reviewed facet catalog. Ant/OwnerConfig guards remain pinned.
    if ((HashFile $catalogPath) -notin @('ef0361e344a39e3c6c0e6f2b5f5cb4b4d4c82924df1b9fe956ea983cccc7e840','a33ec0bbac8407430a695c443722c76140f39abed18d1629e78cc82388d1b335','493d1010e653f66b2e1f3407914735a4ce5a370f8dd7d0d5349fbccb0fb76ef0','01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05')) { throw 'BLOCKED_JAVA: unaudited catalog contract' }
    if ((HashFile $catalogPath) -ne '01a08536ab4445562dcaebe7aa7704617602a47c65611bcf1d2e849393eddb05') { throw 'BLOCKED_JAVA: bridge requires reviewed Java082 API; pre082 provenance is historical only' }
    $inventory.Add([pscustomobject]@{ RelativePath = 'build.xml'; Sha256 = (HashFile $buildPath); Bytes = (Get-Item -LiteralPath $buildPath).Length })
    foreach ($family in @('java','test/java','test/resources','dist/libs')) {
        foreach ($file in FilesWithoutLinks (Relative $source $family)) {
            $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
            if ($family -eq 'dist/libs' -and $file.Name -match '^(GameServer|LoginServer)\.jar$|-sources\.jar$') { continue }
            $allowed = (($family -in @('java','test/java')) -and $file.Extension -eq '.java') -or
                ($family -eq 'dist/libs' -and $file.Extension -eq '.jar' -and $file.Name -notmatch '^(GameServer|LoginServer)\.jar$|-sources\.jar$') -or
                ($family -eq 'test/resources' -and ($file.Extension -in @('.sql','.tsv') -or $relative -in @('test/resources/phantoms/scenarios/harness-smoke.properties',
                    'test/resources/phantoms/party-state-v1.base64','test/resources/phantoms/M1TimerBootstrap.java','test/resources/phantoms/M1Q266Bootstrap.java','test/resources/phantoms/M1NativeEvidenceContract.java')))
            if (-not $allowed) { throw 'BLOCKED_JAVA: unexpected file in copy allowlist' }
            $inventory.Add([pscustomobject]@{ RelativePath = $relative; Sha256 = (HashFile $file.FullName); Bytes = $file.Length })
        }
    }
    }
    $bytes = ($inventory | Measure-Object Bytes -Sum).Sum
    if ($inventory.Count -gt 4000 -or $bytes -gt 268435456) { throw 'BLOCKED_JAVA: copy exceeds 4000 files / 256 MiB' }
    $drive = New-Object IO.DriveInfo ([IO.Path]::GetPathRoot($stage))
    if ($drive.AvailableFreeSpace -lt 536870912) { throw 'BLOCKED_JAVA: less than 512 MiB scratch budget available' }
    $scratch = Join-Path $stage ('oracle-' + [guid]::NewGuid().ToString('N')); NoLinks $scratch
    New-Item -ItemType Directory -Path $scratch | Out-Null
    $module = Join-Path $scratch 'module'; $baseline = Join-Path $scratch 'baseline'; $negative = Join-Path $scratch 'negative'
    $temp = Join-Path $scratch 'tmp'; New-Item -ItemType Directory -Path $temp | Out-Null
    CopyStamps $source $module $inventory
    CopyStamps $sourceData $baseline $receipt.SourceFiles
    CopyStamps $stageData (Join-Path $module 'dist\game\data\phantoms') $receipt.StagedFiles
    CopyStamps $stageData $negative $receipt.StagedFiles
    $result.Scratch = $scratch; $result.CopyFiles = $inventory.Count; $result.CopyBytes = $bytes
    $result.SourceFingerprint = $receipt.SourceFingerprint; $result.StageFiles = $receipt.StagedFiles
    $result.StageId = $receipt.StageId; $result.ReceiptHash = $receiptHash
    $result.SourceFiles = $receipt.SourceFiles
    $scriptHash = HashFile $PSCommandPath; $bridgePath = Join-Path $PSScriptRoot 'java\Pss008V3CatalogProbe.java'; NoLinks $bridgePath; $bridgeHash = HashFile $bridgePath
    $result.ScriptHash = $scriptHash; $result.BridgeHash = $bridgeHash
    Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $scratch 'operator.ps1')
    $inventoryPath = Join-Path $scratch 'input-inventory.json'
    [IO.File]::WriteAllText($inventoryPath, (ConvertTo-Json -InputObject @($inventory.ToArray()) -Depth 5), (New-Object Text.UTF8Encoding $false))
    $result.InputInventoryHash = HashFile $inventoryPath
    if ($isolated) {
        $result.JavaTools = @(foreach ($tool in @($java,$javac)) {
            [pscustomobject]@{ Path = $tool; Sha256 = (HashFile $tool); Bytes = (Get-Item -LiteralPath $tool).Length }
        })
    }
    # Execution and output/status handling follow below; never start Ant in source.
}
catch {
    $result.Reason = if ($_.Exception.Message -match '^(BLOCKED_JAVA|SOURCE_DRIFT):') { $_.Exception.Message } else { 'BLOCKED_JAVA: safe environment or input unavailable' }
    Write-Output $result.Reason
    Write-Output ("Diagnostic=" + $_.Exception.GetType().Name + "; line=" + $_.InvocationInfo.ScriptLineNumber)
    exit 2
}

$savedJavaHome = $env:JAVA_HOME; $savedJavaOptions = $env:JAVA_TOOL_OPTIONS; $savedAntOptions = $env:ANT_OPTS
$savedJdkOptions = $env:JDK_JAVA_OPTIONS; $savedUnderscoreOptions = $env:_JAVA_OPTIONS; $savedAntArgs = $env:ANT_ARGS
$savedClasspath = $env:CLASSPATH
$exit = 2
Push-Location $scratch
try {
    $env:JAVA_HOME = Split-Path (Split-Path $javac -Parent) -Parent
    $env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir="' + $temp + '"'
    $env:ANT_OPTS = '-Xmx2g'; $env:JDK_JAVA_OPTIONS = $null; $env:_JAVA_OPTIONS = $null; $env:ANT_ARGS = $null; $env:CLASSPATH = $null
    $javaVersionLog = Join-Path $scratch 'java-version.txt'; $antVersionLog = Join-Path $scratch 'ant-version.txt'
    if ((Native $javac @('-version') $javaVersionLog) -ne 0 -or (Get-Content -LiteralPath $javaVersionLog -Raw) -notmatch 'javac 25\.') { throw 'BLOCKED_JAVA: JDK25 required' }
    $result.Jdk = (Get-Content -LiteralPath $javaVersionLog | Where-Object { $_ -match '^javac ' }) -join ' '
    if (-not $isolated) {
        if ((Native $ant @('-version') $antVersionLog) -ne 0) { throw 'BLOCKED_JAVA: Ant unavailable' }
        $result.Ant = (Get-Content -LiteralPath $antVersionLog | Where-Object { $_ -match '^Apache Ant' }) -join ' '
    }
    VerifyStamps $source $inventory; VerifyStamps $sourceData $receipt.SourceFiles
    VerifyStamps $stageData $receipt.StagedFiles
    $output = Join-Path $scratch 'build'; NoLinks $output
    $result.JavaStatus = 'FAILED_JAVA'; $result.Status = 'FAILED_JAVA'
    if (-not $isolated) {
    SafeBuild (Join-Path $module 'build.xml')
    # CLI properties pin every reachable write/copy path, overriding source properties.
    $arguments = @('-f', (Join-Path $module 'build.xml'), "-Dbasedir=$module", "-Dbuild=$output", "-Dbuild.bin=$output\bin",
        "-Dbuild.test=$output\phantom-test", "-Dbuild.test.bin=$output\phantom-test\bin", "-Dbuild.test.resources=$output\phantom-test\resources",
        "-Dbuild.test.reports=$output\phantom-test\reports", "-Ddatapack=$module\dist", "-Dsrc=$module\java", "-Dlibs=$module\dist\libs",
        "-Dtest.src=$module\test\java", "-Dtest.resources=$module\test\resources", 'phantom-humanized-v3-content-validate')
    $result.AntCommand = @($ant) + $arguments
    Push-Location $module
    try { $result.AntExitCode = Native $ant $arguments (Join-Path $scratch 'ant-content.txt') } finally { Pop-Location }
    if ($result.AntExitCode -ne 0) { throw 'FAILED_JAVA: Ant content target rejected shadow' }
    }
    $spec = Join-Path $scratch 'expected-ids.tsv'
    $rows = $receipt.Candidates | ForEach-Object {
        if ($_.XmlId -notmatch '^pss\.v3\.[pt]\.[0-9a-f]{32}$' -or $_.Act -notmatch '^[a-z][a-z0-9_.-]{0,63}$' -or
            $_.Topic -notmatch '^[a-z][a-z0-9_.-]{0,63}$' -or $_.TextHash -notmatch '^[0-9a-f]{64}$' -or
            $_.Kind -notin @('PATTERN','TEMPLATE') -or [string]$_.Voice -cnotin @('','calm','open','direct','empathetic') -or
            ($_.Kind -eq 'PATTERN' -and [string]$_.Voice -ne '') -or $_.Band -cnotin @('UNKNOWN','NEUTRAL','FAMILIAR','TRUSTED','RIVAL','TENSE','HOSTILE') -or
            $_.Register -cnotin @('NEUTRAL','CASUAL')) { throw 'FAILED_JAVA: invalid expected-ID receipt' }
        $target = if ($_.Kind -eq 'PATTERN') { $receipt.Pair.SemanticPath } else { $receipt.Pair.ConversationPath }
        $row = "$($_.XmlId)`t$($_.Kind)`t$($_.Act)`t$($_.Topic)`t$($_.TextHash)`t$target`t$($_.Voice)`t$($_.Band)`t$($_.Register)"
        if ($receipt.Version -eq 3) { $row += "`t$($_.Facet)`t$($_.MinIntensity)" }
        $row
    }
    if ($receipt.Version -eq 3) { $rows = @('PSS008_FACET_SPEC_V1') + @($rows) }
    [IO.File]::WriteAllLines($spec, [string[]]$rows, (New-Object Text.UTF8Encoding $false))
    $specHash = HashFile $spec; $result.ExpectedIdsSha256 = $specHash
    $bridge = Join-Path $scratch 'probe-bin'; New-Item -ItemType Directory -Path $bridge | Out-Null
    $classpath = if ($isolated) { $bridge } else { "$output\bin;$module\dist\libs\*" }
    $bridgeCopy = Join-Path $scratch 'Pss008V3CatalogProbe.java'; Copy-Item -LiteralPath $bridgePath -Destination $bridgeCopy
    if ((HashFile $bridgeCopy) -ne $bridgeHash) { throw 'FAILED_JAVA: bridge copy hash' }
    if ($isolated) {
        $empty = Join-Path $scratch 'empty'; New-Item -ItemType Directory -Path $empty | Out-Null
        # Explicit source closure, no processors, implicit sources, inherited classpath or prebuilt JARs.
        $compileArgs = @('--release','25','-encoding','UTF-8','-proc:none','-implicit:none','-sourcepath',$empty,'-classpath',$empty,'-d',$bridge) +
            @($inventory | ForEach-Object { Relative $module $_.RelativePath }) + @($bridgeCopy)
        $result.CompileCommand = @($javac) + $compileArgs
        $compileExit = Native $javac $compileArgs (Join-Path $scratch 'probe-compile.txt')
    } else {
        $compileExit = Native $javac @('-encoding','UTF-8','-cp',$classpath,'-d',$bridge,$bridgeCopy) (Join-Path $scratch 'probe-compile.txt')
    }
    $result.CompileExitCode = $compileExit
    if ($compileExit -ne 0) { throw 'FAILED_JAVA: bridge compilation failed' }
    $runtimeClasspath = if ($isolated) { $bridge } else { "$bridge;$classpath" }
    $probeArgs = @('-cp', $runtimeClasspath, 'Pss008V3CatalogProbe', $scratch, $baseline, (Join-Path $module 'dist\game\data\phantoms'),
        $negative, $spec, [string]$receipt.BaselinePatterns, [string]$receipt.BaselineTemplates, [string]$receipt.AddedPatterns, [string]$receipt.AddedTemplates)
    $result.ProbeCommand = @($java) + $probeArgs
    $probeLog = Join-Path $scratch 'probe.txt'; $result.ProbeExitCode = Native $java $probeArgs $probeLog
    if ($result.ProbeExitCode -ne 0) { throw 'FAILED_JAVA: actual v3 loader probe rejected stage' }
    $proof = @(Get-Content -LiteralPath $probeLog | Where-Object { $_ -match '^PASS_JAVA_STAGED_V3 baselinePatterns=\d+ baselineTemplates=\d+ stagedPatterns=\d+ stagedTemplates=\d+ checkedIds=\d+ baselineHash=[0-9a-f]{64} stagedHash=[0-9a-f]{64} negative=DUPLICATE_AND_SCHEMA_REJECTED$' })
    if ($proof.Count -ne 1) { throw 'FAILED_JAVA: actual load evidence missing' }
    $negativeExit = Native $java @('-cp', $runtimeClasspath, 'Pss008V3CatalogProbe', '--negative', $scratch, $negative) (Join-Path $scratch 'negative.txt')
    $result.NegativeExitCode = $negativeExit
    if ($negativeExit -ne 3) { throw 'FAILED_JAVA: negative loader native exit not proven' }
    $schemaExit = Native $java @('-cp', $runtimeClasspath, 'Pss008V3CatalogProbe', '--negative', $scratch, (Join-Path $scratch 'negative-schema')) (Join-Path $scratch 'negative-schema.txt')
    $result.SchemaNegativeExitCode = $schemaExit
    if ($schemaExit -ne 3) { throw 'FAILED_JAVA: negative schema native exit not proven' }
    # Source inputs and stage are immutable across compilation, native tests and probe.
    VerifyStamps $source $inventory; VerifyStamps $sourceData $receipt.SourceFiles; VerifyStamps $stageData $receipt.StagedFiles
    VerifyStamps $module $inventory; VerifyStamps $baseline $receipt.SourceFiles
    VerifyStamps (Join-Path $module 'dist\game\data\phantoms') $receipt.StagedFiles
    if ($isolated) {
        foreach ($tool in $result.JavaTools) {
            if ((Get-Item -LiteralPath $tool.Path).Length -ne $tool.Bytes -or (HashFile $tool.Path) -ne $tool.Sha256) { throw 'FAILED_JAVA: JDK tool drift' }
        }
    }
    if ((HashFile $spec) -ne $specHash) { throw 'FAILED_JAVA: selected-ID spec drift' }
    if ((HashFile $receiptPath) -ne $receiptHash -or (HashFile $PSCommandPath) -ne $scriptHash -or (HashFile (Join-Path $scratch 'operator.ps1')) -ne $scriptHash -or (HashFile $inventoryPath) -ne $result.InputInventoryHash -or (HashFile $bridgePath) -ne $bridgeHash -or (HashFile $bridgeCopy) -ne $bridgeHash) { throw 'FAILED_JAVA: receipt or bridge drift' }
    $integrity = & dotnet $runner --pss-008-inspect-stage $stage
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'PASS_V3_STAGE_INTEGRITY') { throw 'FAILED_JAVA: post-run stage integrity' }
    # PS5 Get-Content adds provider/drive ETS properties; serialize a plain string.
    $result.SourceEqual = $true; $result.ProbeEvidence = ($proof -join ' '); $result.JavaStatus = 'PASS_JAVA_STAGED_V3'
    $result.Status = 'JAVA_CONTENT_ONLY_NOT_RUNTIME_READY'; $exit = 0
    Write-Output $proof[0]
    $antStatus = if ($isolated) { 'NOT_RUN' } else { $result.AntExitCode }
    Write-Output "PASS source and stage SHA/bytes unchanged; copied=$($inventory.Count) files/$bytes bytes; Ant=$antStatus; probe=$($result.ProbeExitCode); negative=$negativeExit"
}
catch {
    if ($_.Exception.Message -match '^BLOCKED_JAVA:') { $result.JavaStatus = 'BLOCKED_JAVA'; $result.Status = 'STAGED_V3_UNVALIDATED' }
    $result.Reason = if ($_.Exception.Message -match '^(BLOCKED_JAVA|FAILED_JAVA|SOURCE_DRIFT):') { $_.Exception.Message } else { 'FAILED_JAVA: safe oracle execution failed' }
    Write-Output $result.Reason
    Write-Output ("Diagnostic=" + $_.Exception.GetType().Name + "; line=" + $_.InvocationInfo.ScriptLineNumber)
}
finally {
    Pop-Location
    $env:JAVA_HOME = $savedJavaHome; $env:JAVA_TOOL_OPTIONS = $savedJavaOptions; $env:ANT_OPTS = $savedAntOptions
    $env:JDK_JAVA_OPTIONS = $savedJdkOptions; $env:_JAVA_OPTIONS = $savedUnderscoreOptions; $env:ANT_ARGS = $savedAntArgs
    $env:CLASSPATH = $savedClasspath
    NoLinks $scratch
    $logNames = if ($isolated) { @('probe.txt','negative.txt','negative-schema.txt','probe-compile.txt','java-version.txt') } else {
        @('ant-content.txt','probe.txt','negative.txt','negative-schema.txt','probe-compile.txt','java-version.txt','ant-version.txt') }
    $result.Logs = @(foreach ($name in $logNames) {
        $logPath = Join-Path $scratch $name
        if (Test-Path -LiteralPath $logPath -PathType Leaf) { [pscustomobject]@{ RelativePath = $name; Sha256 = (HashFile $logPath); Bytes = (Get-Item -LiteralPath $logPath).Length } }
    })
    $proofPath = Join-Path $scratch 'java-validation.json'
    if (Test-Path -LiteralPath $proofPath) { throw 'FAILED_JAVA: immutable proof already exists' }
    [IO.File]::WriteAllText($proofPath, ($result | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
    if ($exit -eq 0 -and $result.JavaStatus -eq 'PASS_JAVA_STAGED_V3') {
        # Local per-stage secret, outside oracle/proof export. No source/receipt/key logging.
        # Protects against public-metadata-only forged proofs, not a malicious OS owner.
        $workspace = Split-Path (Split-Path $stage -Parent) -Parent
        $keyRoot = Relative $workspace 'java-attestations'; NoLinks $keyRoot
        New-Item -ItemType Directory -Path $keyRoot -Force | Out-Null
        $keyPath = Relative $keyRoot ($receipt.StageId + '.key')
        if (-not (Test-Path -LiteralPath $keyPath)) {
            $key = New-Object byte[] 32; $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($key) } finally { $rng.Dispose() }
            $stream = [IO.File]::Open($keyPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            try { $stream.Write($key, 0, $key.Length); $stream.Flush($true) } finally { $stream.Dispose() }
        }
        if ((Get-Item -LiteralPath $keyPath).Length -ne 32) { throw 'FAILED_JAVA: invalid local attestation key' }
        $key = [IO.File]::ReadAllBytes($keyPath); $hmac = New-Object Security.Cryptography.HMACSHA256
        try {
            $hmac.Key = $key; $proofHash = HashFile $proofPath
            $payload = [Text.Encoding]::UTF8.GetBytes("PSS008_NATIVE_JAVA_V" + $result.Version + "`n" + $receipt.StageId + "`n" + $proofHash)
            $mac = [BitConverter]::ToString($hmac.ComputeHash($payload)).Replace('-','').ToLowerInvariant()
        } finally { $hmac.Dispose(); [Array]::Clear($key, 0, $key.Length) }
        $attestation = [ordered]@{ Version = $result.Version; StageId = $receipt.StageId; ProofSha256 = $proofHash; Mac = $mac }
        $attestationPath = Relative $scratch 'java-attestation.json'
        if (Test-Path -LiteralPath $attestationPath) { throw 'FAILED_JAVA: immutable attestation exists' }
        [IO.File]::WriteAllText($attestationPath, ($attestation | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
    }
    Write-Output "JavaStatus=$($result.JavaStatus); Status=$($result.Status); evidence=$scratch"
}
exit $exit
