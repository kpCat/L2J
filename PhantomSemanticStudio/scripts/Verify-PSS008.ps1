param([switch]$StaticOnly, [switch]$GitScope, [switch]$Staged, [switch]$JavaGuards)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$base = '0344c9c0671d53a7bb757db7a92b1dfd03246e9c'
& (Join-Path $PSScriptRoot 'Verify-PSS007.ps1') -StaticOnly
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj') -Raw -Encoding UTF8
foreach ($name in @('PackQualityForm', 'V3ProposalForm')) {
    $form = Get-Content -LiteralPath (Join-Path $root ('src/PhantomSemanticStudio.WinForms/' + $name + '.cs')) -Raw -Encoding UTF8
    $designer = Get-Content -LiteralPath (Join-Path $root ('src/PhantomSemanticStudio.WinForms/' + $name + '.Designer.cs')) -Raw -Encoding UTF8
    $body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
    if (-not $body) { throw 'InitializeComponent missing' }
    if ($body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|File\.|Directory\.|HttpClient|Process\.|Task\.|BuildUi') { throw ('Non-designer logic: ' + $name) }
    if ($form -notmatch ('(?s)public ' + $name + '\(\)\s*\{\s*InitializeComponent\(\);\s*\}')) { throw ('Unsafe ctor: ' + $name) }
    foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
        if ($form -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing handler: ' + $match.Groups[1].Value) }
    }
    [xml]$resx = Get-Content -LiteralPath (Join-Path $root ('src/PhantomSemanticStudio.WinForms/' + $name + '.resx')) -Raw -Encoding UTF8
    if (-not $project.SelectSingleNode("//Compile[@Update='$name.cs']/SubType[text()='Form']") -or
        -not $project.SelectSingleNode("//Compile[@Update='$name.Designer.cs']/DependentUpon[text()='$name.cs']") -or
        -not $project.SelectSingleNode("//EmbeddedResource[@Update='$name.resx']/DependentUpon[text()='$name.cs']")) { throw ('Missing nesting: ' + $name) }
    foreach ($required in @('AutoScaleMode.Dpi', 'ReadOnly = true')) { if (-not $body.Contains($required)) { throw ('Missing UI contract: ' + $required) } }
    if ($name -eq 'V3ProposalForm') {
        foreach ($required in @('chkSelection.Checked = false', 'chkEditorial.Checked = false', 'chkRelease.Checked = false')) { if (-not $body.Contains($required)) { throw ('Default consent not NO: ' + $required) } }
        # Proof/preview publish together only after both awaited validations and final cancellation check.
        if ($form -notmatch '(?s)var receipt = await Task.Run.*?var preview =.*?token.ThrowIfCancellationRequested\(\);\s*currentStage = stage; proofPath = path; proofHash = hash; txtProof.Text = preview;') { throw 'Early proof publication' }
    }
}
$main = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs') -Raw -Encoding UTF8
foreach ($entry in @('contentLibrary.Controls.Add(btnPackQuality)', 'contentExport.Controls.Add(btnV3Proposal)', 'btnPackQuality.Click += PackQuality_Click', 'btnV3Proposal.Click += V3Proposal_Click')) {
    if (-not $main.Contains($entry)) { throw ('Missing entry: ' + $entry) }
}
Write-Output 'PASS PSS-008 static Designer/ctor/resx/nesting/handlers; separate forms; default NO; complete proof preview; NOT physical UI/DPI/VS'
if ($StaticOnly) { exit 0 }

if ($JavaGuards) {
    function GuardPath([string]$Path, [string]$Boundary) {
        $pathFull = [IO.Path]::GetFullPath($Path); $boundaryFull = [IO.Path]::GetFullPath($Boundary).TrimEnd('\')
        if (-not ($pathFull.Equals($boundaryFull, [StringComparison]::OrdinalIgnoreCase) -or $pathFull.StartsWith($boundaryFull + '\', [StringComparison]::OrdinalIgnoreCase))) { throw 'Verifier fixture path escape' }
        $ancestor = $pathFull
        while ($ancestor) {
            try { if ([IO.File]::GetAttributes($ancestor) -band [IO.FileAttributes]::ReparsePoint) { throw 'Verifier reparse path' } }
            catch [IO.FileNotFoundException] { }
            catch [IO.DirectoryNotFoundException] { }
            $ancestor = [IO.Path]::GetDirectoryName($ancestor)
        }
    }
    # Existing PSS003 guard-fixture pattern: copies only stamped content, mutates only Studio fixtures.
    $stagePath = (Get-Content -LiteralPath (Join-Path $root 'artifacts/PSS-008/workspace/test-v3-stage-path.txt') -Raw).Trim()
    GuardPath $stagePath (Join-Path $root 'artifacts/PSS-008/workspace/v3-proposals')
    $integrity = & dotnet (Join-Path $root 'tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll') --pss-008-inspect-stage $stagePath
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'PASS_V3_STAGE_INTEGRITY') { throw 'Verifier input stage integrity rejected' }
    $original = Get-Content -LiteralPath (Join-Path $stagePath 'receipt.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $fixture = Join-Path $root ('artifacts/PSS-008-guards/' + [guid]::NewGuid().ToString('N'))
    GuardPath $fixture $root
    $source = Join-Path $fixture 'source'; $stage = Join-Path $fixture ('workspace/v3-proposals/' + $original.StageId)
    foreach ($set in @(@{ From = $original.SourceModule; To = $source; Stamps = $original.SourceFiles }, @{ From = (Join-Path $stagePath 'module'); To = (Join-Path $stage 'module'); Stamps = $original.StagedFiles })) {
        foreach ($stamp in $set.Stamps) {
            if ($stamp.RelativePath -match '\\|:|\.\.|^/' -or $stamp.RelativePath -notmatch '^(semantic|conversation)/') { throw 'Verifier invalid copy stamp path' }
            $to = Join-Path $set.To ('dist/game/data/phantoms/' + $stamp.RelativePath)
            GuardPath $to $fixture
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($to)) -Force | Out-Null
            GuardPath $to $fixture
            Copy-Item -LiteralPath (Join-Path $set.From ('dist/game/data/phantoms/' + $stamp.RelativePath)) -Destination $to
        }
    }
    # Test-only boundary marker; no repository is initialized and no git runs here.
    # Prevent the enclosing Studio .git from being mistaken for this synthetic source boundary.
    GuardPath $source $fixture; GuardPath $stage $fixture
    [IO.File]::WriteAllText((Join-Path $source '.git'), 'synthetic boundary fixture; no git metadata', (New-Object Text.UTF8Encoding $false))
    $stockBuild = [IO.File]::ReadAllText((Join-Path $original.SourceModule 'build.xml'))
    $original.SourceModule = $source
    [IO.File]::WriteAllText((Join-Path $stage 'receipt.json'), ($original | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
    $outside = Join-Path $fixture 'must-not-be-written'
    $cases = @(
        @{ Name = 'javac destdir'; Find = 'destdir="${build.bin}"'; Replace = ('destdir="' + $outside + '"') },
        @{ Name = 'Java output'; Find = 'classname="org.l2jmobius.tests.phantoms.PhantomTestLauncher"'; Replace = ('output="' + $outside + '" classname="org.l2jmobius.tests.phantoms.PhantomTestLauncher"') },
        @{ Name = 'Java report JVM path'; Find = '-Dphantom.test.reports=${build.test.reports}'; Replace = ('-Dphantom.test.reports=' + $outside) },
        @{ Name = 'reachable extra Java task'; Find = '<target name="compile" depends="init" description="Compile the source.">'; Replace = ('<target name="compile" depends="init" description="Compile the source."><java classname="Unexpected" output="' + $outside + '"/>') },
        @{ Name = 'top-level property drift'; Find = '<property name="build" location="../build" />'; Replace = ('<property name="build" location="' + $outside + '" />') }
    )
    foreach ($case in $cases) {
        if (-not $stockBuild.Contains($case.Find)) { throw ('Guard fixture contract changed: ' + $case.Name) }
        GuardPath (Join-Path $source 'build.xml') $fixture
        [IO.File]::WriteAllText((Join-Path $source 'build.xml'), $stockBuild.Replace($case.Find, $case.Replace), (New-Object Text.UTF8Encoding $false))
        # No java/libs trees in fixture: a guard regression cannot reach Ant.
        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-PSS008-V3-Java.ps1') -StageRoot $stage 2>&1
        $code = $LASTEXITCODE
        if ($code -ne 2 -or ($output -join "`n") -notmatch 'BLOCKED_JAVA: unaudited build contract' -or (Test-Path -LiteralPath $outside) -or @(Get-ChildItem -LiteralPath $stage -Directory -Filter 'oracle-*').Count -ne 0) { throw ('FAIL guard: ' + $case.Name) }
        Write-Output ('PASS PSS-008 ' + $case.Name + ': blocked before Ant/scratch output')
    }
    [IO.File]::WriteAllText((Join-Path $source 'build.xml'), $stockBuild, (New-Object Text.UTF8Encoding $false))
    $catalog = Join-Path $source 'java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java'
    GuardPath $catalog $fixture
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($catalog)) -Force | Out-Null
    [IO.File]::WriteAllText($catalog, 'synthetic unaudited catalog; never compiled', (New-Object Text.UTF8Encoding $false))
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-PSS008-V3-Java.ps1') -StageRoot $stage 2>&1
    if ($LASTEXITCODE -ne 2 -or ($output -join "`n") -notmatch 'BLOCKED_JAVA: unaudited catalog contract' -or @(Get-ChildItem -LiteralPath $stage -Directory -Filter 'oracle-*').Count -ne 0) { throw 'Catalog contract guard failed' }
    Write-Output 'PASS PSS-008 catalog drift: BLOCKED_JAVA before Ant/scratch output'
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-PSS008-V3-Java.ps1') -StageRoot $stagePath -JdkBin (Join-Path $fixture 'missing-jdk') 2>&1
    if ($LASTEXITCODE -ne 2 -or ($output -join "`n") -notmatch 'BLOCKED_JAVA: JDK/Ant path unavailable') { throw 'Missing JDK guard failed' }
    Write-Output 'PASS PSS-008 missing JDK remains BLOCKED_JAVA; no installation or source writes'
}

$owned = @(Get-Content -LiteralPath (Join-Path $root 'reports/PSS-008-owned-files.txt') -Encoding UTF8)
if ($owned.Count -ne @($owned | Select-Object -Unique).Count) { throw 'Duplicate owned inventory' }
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
foreach ($relative in $owned) {
    if ($relative -match '\\|(^|/)(artifacts|workspace|labs|corpora|bin|obj|private-input)/|\.\.|\.(zip|db|sqlite|log|dll|exe|pdb|key)$|\.db-|\.sqlite-|session\.json|settings\.json|java-validation\.json|java-attestation\.json') { throw ('Private/unsafe path: ' + $relative) }
    $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Invalid owned path: ' + $relative) }
    $content = (New-Object Text.UTF8Encoding $false,$true).GetString([IO.File]::ReadAllBytes($path))
    if ($relative -eq 'scripts/Verify-PSS008.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw ('Mojibake: ' + $relative) } }
    if ($content -match 'sk-[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{24,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----') { throw ('Secret token/key marker: ' + $relative) }
}
Write-Output ('PASS mojibake markers in changed files checked; strict UTF-8; public inventory secret-token/key scan; files=' + $owned.Count)
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw ('Escaped Cyrillic: ' + $relative) }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifestPath = Join-Path $root 'docs/tasks/PSS-008/PACKAGE_MANIFEST.json'
if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne '7c80e3c90aefac50879c8f74e9108ca3f7844a6bc14c9e136023a0ffc86f3550') { throw 'Task manifest changed' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    $path = Join-Path $root $entry.path
    if ((Get-Item -LiteralPath $path).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw ('Task bytes changed: ' + $entry.path) }
}
Write-Output 'PASS task package exact SHA/bytes unchanged (11 files + manifest)'
$evidence = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-008-source.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sourceModule = 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ($evidence.Before.Count -ne 65 -or ($evidence.Before | ConvertTo-Json -Depth 5 -Compress) -ne ($evidence.After | ConvertTo-Json -Depth 5 -Compress)) { throw 'Source evidence mismatch' }
foreach ($stamp in $evidence.Before) {
    if ($stamp.RelativePath -match '\\|:|\.\.|^/' -or $stamp.RelativePath -notmatch '^(semantic|conversation)/') { throw 'Invalid source stamp path' }
    $path = Join-Path $sourceModule ('dist/game/data/phantoms/' + $stamp.RelativePath)
    if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $stamp.Sha256) { throw 'Source SHA/bytes changed' }
}
Write-Output 'PASS final source equality: all65 humanized SHA/bytes; read-only'
if ($GitScope -or $Staged) {
    Push-Location $root
    try {
        $fetchUrl = git remote get-url origin; $pushUrl = git remote get-url --push origin
        if ($fetchUrl -ne 'https://github.com/kpCat/PhantomSemanticStudio.git' -or $pushUrl -ne $fetchUrl) { throw 'Wrong Studio origin' }
        $changed = @(git diff --name-only $base); if ($LASTEXITCODE -ne 0) { throw 'Scope diff failed' }
        $untracked = @(git ls-files --others --exclude-standard); if ($LASTEXITCODE -ne 0) { throw 'Inventory failed' }
        foreach ($path in @($changed + $untracked)) { if ($owned -notcontains $path) { throw ('Outside exact allowlist: ' + $path) } }
        if (@($changed + $untracked | Sort-Object -Unique).Count -ne $owned.Count) { throw 'Owned inventory differs from actual changes' }
        Write-Output ('PASS exact scope guard vs required base; owned=' + $owned.Count)
        if ($Staged) {
            # Complete index vs required base, including ordinary linear follow-up commits.
            $stagedPaths = @(git diff --cached --name-only $base); if ($LASTEXITCODE -ne 0 -or $stagedPaths.Count -ne $owned.Count) { throw 'Staged count mismatch' }
            foreach ($path in $stagedPaths) { if ($owned -notcontains $path) { throw ('Unowned staged file: ' + $path) } }
            git diff --cached --check $base; if ($LASTEXITCODE -ne 0) { throw 'Whitespace gate failed' }
            Write-Output ('PASS exact staged allowlist=' + $stagedPaths.Count + '; private/binary/keys excluded')
        }
    } finally { Pop-Location }
}
