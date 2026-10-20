param([switch]$GuardsOnly, [switch]$TextOnly, [switch]$ProbeOnly, [string]$OracleRoot = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$failures = 0
if ($ProbeOnly) {
    $oracle = [IO.Path]::GetFullPath($OracleRoot).TrimEnd('\')
    $allowed = [IO.Path]::GetFullPath((Join-Path $root 'artifacts')).TrimEnd('\') + '\'
    if (-not $oracle.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath (Join-Path $oracle 'build\bin'))) { throw 'Probe regression needs own physical oracle' }
    for ($parent = $oracle; $parent; $parent = [IO.Path]::GetDirectoryName($parent)) {
        if ((Get-Item -LiteralPath $parent -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked oracle refused' }
    }
    $javac = (Get-Command javac).Source; $java = Join-Path (Split-Path $javac -Parent) 'java.exe'
    $classpath = (Join-Path $oracle 'build\bin') + ';' + (Join-Path $oracle 'module\dist\libs\*')
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { & $javac -encoding UTF-8 -cp $classpath -d (Join-Path $oracle 'probe-bin') (Join-Path $PSScriptRoot 'java\Pss003CatalogProbe.java') 2>&1 | Out-File -LiteralPath (Join-Path $oracle 'regression-compile.txt') -Encoding utf8; $compile = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($compile -ne 0) { throw 'Probe regression compilation failed' }
    foreach ($case in @('pattern','override')) {
        $pathFile = if ($case -eq 'pattern') { 'test-stage-pattern-path.txt' } else { 'test-stage-path.txt' }
        $stage = Get-Content -LiteralPath (Join-Path $root ('artifacts\PSS-003\workspace\' + $pathFile)) -Raw
        $receipt = Get-Content -LiteralPath (Join-Path $stage 'receipt.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        $fixture = Join-Path $oracle ('regression-' + $case + '-' + [guid]::NewGuid().ToString('N'))
        foreach ($destination in @((Join-Path $fixture 'stage'), (Join-Path $fixture 'negative'))) {
            foreach ($stamp in $receipt.StagedFiles) {
                $to = Join-Path $destination $stamp.RelativePath
                New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($to)) -Force | Out-Null
                Copy-Item -LiteralPath (Join-Path $stage ('module\dist\game\data\phantoms\' + $stamp.RelativePath)) -Destination $to
            }
        }
        if ($case -eq 'override') {
            $custom = Join-Path $fixture 'negative\conversation\custom\my-phrases.xml'
            $text = [IO.File]::ReadAllText($custom)
            $override = '<template id="v3.lineage.buffs.reply.001" act="support.buff.request" band="UNKNOWN" register="NEUTRAL" profanity="NONE" text="Проверяю операторский баф." override="true"/>'
            [IO.File]::WriteAllText($custom, $text.Replace('<phrases version="1">', '<phrases version="1">' + $override), (New-Object Text.UTF8Encoding $false))
        }
        $spec = Join-Path $fixture 'spec.tsv'
        $rows = $receipt.Candidates | ForEach-Object { "$($_.XmlId)`t$($_.Kind)`t$($_.Act)`t$($_.Topic)`t$($_.TextHash)" }
        [IO.File]::WriteAllLines($spec, [string[]]$rows, (New-Object Text.UTF8Encoding $false))
        $arguments = @('-cp', ((Join-Path $oracle 'probe-bin') + ';' + $classpath), 'Pss003CatalogProbe', $oracle, (Join-Path $oracle 'baseline'),
            (Join-Path $fixture 'stage'), (Join-Path $fixture 'negative'), $spec, [string]$receipt.BaselinePatterns, [string]$receipt.BaselineTemplates, [string]$receipt.AddedPatterns, [string]$receipt.AddedTemplates)
        $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        try { & $java @arguments 2>&1 | Out-File -LiteralPath (Join-Path $fixture 'probe.txt') -Encoding utf8; $code = $LASTEXITCODE }
        finally { $ErrorActionPreference = $previous }
        if ($code -ne 0) { $failures++; Write-Output ("FAIL actual Java $case negative fixture: native exit=$code") }
        else { Write-Output ("PASS actual Java $case negative fixture: duplicate rejected") }
    }
    if ($failures) { exit 1 }; exit 0
}
if (-not $TextOnly) {
    $originalStage = Get-Content -LiteralPath (Join-Path $root 'artifacts\PSS-003\workspace\test-stage-path.txt') -Raw
    $originalReceipt = Get-Content -LiteralPath (Join-Path $originalStage 'receipt.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $fixture = Join-Path $root ('artifacts\PSS-003-guards\' + [guid]::NewGuid().ToString('N'))
    $source = Join-Path $fixture 'source'; $stage = Join-Path $fixture ('workspace\proposals\' + [guid]::NewGuid().ToString('N'))
    foreach ($set in @(@{ Root = $originalReceipt.SourceModule; Stamps = $originalReceipt.SourceFiles; Destination = $source },
        @{ Root = (Join-Path $originalStage 'module'); Stamps = $originalReceipt.StagedFiles; Destination = (Join-Path $stage 'module') })) {
        foreach ($stamp in $set.Stamps) {
            $from = Join-Path $set.Root ('dist\game\data\phantoms\' + $stamp.RelativePath)
            $to = Join-Path $set.Destination ('dist\game\data\phantoms\' + $stamp.RelativePath)
            New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($to)) -Force | Out-Null
            Copy-Item -LiteralPath $from -Destination $to
        }
    }
    $receipt = $originalReceipt; $receipt.SourceModule = $source
    [IO.File]::WriteAllText((Join-Path $stage 'receipt.json'), ($receipt | ConvertTo-Json -Depth 12), (New-Object Text.UTF8Encoding $false))
    # The receipt object was changed above; the original source is read from its original JSON.
    $originalSource = (Get-Content -LiteralPath (Join-Path $originalStage 'receipt.json') -Raw -Encoding UTF8 | ConvertFrom-Json).SourceModule
    $stockBuild = [IO.File]::ReadAllText((Join-Path $originalSource 'build.xml'))
    $outside = Join-Path $fixture 'must-not-be-written'
    $cases = @(
        @{ Name = 'javac literal destdir'; Find = 'destdir="${build.bin}"'; Replace = ('destdir="' + $outside + '"') },
        @{ Name = 'Java literal output'; Find = 'classname="org.l2jmobius.tests.phantoms.PhantomTestLauncher"'; Replace = ('output="' + $outside + '" classname="org.l2jmobius.tests.phantoms.PhantomTestLauncher"') },
        @{ Name = 'Java report JVM path'; Find = '-Dphantom.test.reports=${build.test.reports}'; Replace = ('-Dphantom.test.reports=' + $outside) },
        @{ Name = 'extra Java task in compile'; Find = '<target name="compile" depends="init" description="Compile the source.">'; Replace = ('<target name="compile" depends="init" description="Compile the source."><java classname="Unexpected" output="' + $outside + '"/>') }
    )
    foreach ($case in $cases) {
        if (-not $stockBuild.Contains($case.Find)) { throw ('Fixture source changed: ' + $case.Name) }
        [IO.File]::WriteAllText((Join-Path $source 'build.xml'), $stockBuild.Replace($case.Find, $case.Replace), (New-Object Text.UTF8Encoding $false))
        # Fixture deliberately has no java/libs trees, so a regressed guard never reaches Ant.
        $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-PSS003-Java.ps1') -StageRoot $stage 2>&1
        $code = $LASTEXITCODE
        if ($code -ne 2 -or ($output -join "`n") -notmatch 'BLOCKED_JAVA: unsafe reachable task shape' -or (Test-Path -LiteralPath $outside)) {
            $failures++; Write-Output ('FAIL ' + $case.Name + ': guard did not reject reachable task shape')
        } else { Write-Output ('PASS ' + $case.Name + ': rejected before any Ant/scratch output') }
    }
    [IO.File]::WriteAllText((Join-Path $source 'build.xml'), $stockBuild, (New-Object Text.UTF8Encoding $false))
    $previous = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-PSS003-Java.ps1') -StageRoot $originalStage -JdkBin (Join-Path $fixture 'missing-jdk') 2>&1; $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($code -ne 2 -or ($output -join "`n") -notmatch 'BLOCKED_JAVA: JDK/Ant path unavailable') { $failures++; Write-Output 'FAIL missing JDK must remain BLOCKED_JAVA' } else { Write-Output 'PASS missing JDK remains BLOCKED_JAVA' }
}
if (-not $GuardsOnly) {
    $owned = Get-Content -LiteralPath (Join-Path $root 'reports\PSS-003-owned-files.txt') -Encoding UTF8
    $markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
    foreach ($path in $owned) {
        $content = [IO.File]::ReadAllText((Join-Path $root $path), (New-Object Text.UTF8Encoding $false,$true))
        # This checker contains literal technical search markers; no user-facing text.
        if ($path -eq 'scripts/Verify-PSS003.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\s*\$markers =' }) -join "`n" }
        foreach ($marker in $markers) { if ($content.Contains($marker)) { throw "Mojibake marker: $path" } }
    }
    Write-Output 'PASS mojibake markers in changed files checked'
    foreach ($path in $owned) {
        $content = [IO.File]::ReadAllText((Join-Path $root $path), (New-Object Text.UTF8Encoding $false,$true))
        if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw "Escaped Cyrillic: $path" }
    }
    Write-Output 'PASS escaped Cyrillic in changed files checked separately'
    $manifest = Get-Content -LiteralPath (Join-Path $root 'docs\tasks\PSS-003\PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($file in $manifest.files) {
        $path = Join-Path $root $file.path
        if ((Get-Item -LiteralPath $path).Length -ne $file.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256) { throw ('Task package changed: ' + $file.path) }
    }
    Write-Output 'PASS task package exact SHA/bytes unchanged'
    $evidence = Get-Content -LiteralPath (Join-Path $root 'reports\PSS-003-source.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($stamp in $evidence.Before) {
        $path = Join-Path $evidence.ModuleRoot ('dist\game\data\phantoms\' + $stamp.RelativePath)
        if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $stamp.Sha256) { throw 'Source SHA/bytes changed' }
    }
    Write-Output ('PASS final source equality: ' + $evidence.Before.Count + ' humanized files')
}
if ($failures) { exit 1 }; exit 0
