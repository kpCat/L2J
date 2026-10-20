param([switch]$StaticOnly, [switch]$GitScope)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Verify-Designer.ps1')
& (Join-Path $PSScriptRoot 'Verify-PSS004.ps1') -StaticOnly
& (Join-Path $PSScriptRoot 'Verify-PSS005.ps1') -StaticOnly
$form = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/ChatCorpusForm.cs') -Raw -Encoding UTF8
$designer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/ChatCorpusForm.Designer.cs') -Raw -Encoding UTF8
$main = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.cs') -Raw -Encoding UTF8
$mainDesigner = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs') -Raw -Encoding UTF8
$body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
if (-not $body) { throw 'Corpus InitializeComponent missing' }
$body = ($body -split '#endregion', 2)[0]
if ($body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|File\.|Directory\.|HttpClient|Process\.|Task\.|BuildUi') { throw 'Non-designer logic in corpus layout' }
if ($form -notmatch '(?s)public ChatCorpusForm\(\)\s*\{\s*InitializeComponent\(\);\s*\}') { throw 'Corpus constructor not design safe' }
foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
    if ($form -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing corpus event handler: ' + $match.Groups[1].Value) }
}
foreach ($contract in @('ListView\(\)', 'CheckBoxes = true', 'ReadOnly = true', 'MinimumSize = ', 'AutoScaleMode.Dpi', 'btnCancel.Click')) {
    if ($body -notmatch $contract) { throw ('Missing corpus Designer contract: ' + $contract) }
}
[xml]$resx = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/ChatCorpusForm.resx') -Raw -Encoding UTF8
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj') -Raw -Encoding UTF8
if (-not $project.SelectSingleNode("//Compile[@Update='ChatCorpusForm.cs']/SubType[text()='Form']") -or
    -not $project.SelectSingleNode("//Compile[@Update='ChatCorpusForm.Designer.cs']/DependentUpon[text()='ChatCorpusForm.cs']") -or
    -not $project.SelectSingleNode("//EmbeddedResource[@Update='ChatCorpusForm.resx']/DependentUpon[text()='ChatCorpusForm.cs']")) { throw 'Missing corpus VS nesting' }
if ($mainDesigner -notmatch 'contentSettings.Controls.Add\(btnChatCorpus\)' -or $main -notmatch 'new ChatCorpusForm\(') { throw 'Missing corpus entry point' }
if ($main -notmatch 'Task.Run\(' -or $main -notmatch 'evidenceSelectionVersion' -or $main -notmatch 'PeersFingerprint' -or $main -notmatch 'ReferenceEquals\(snapshot, baseline\)') { throw 'Missing saved-evidence worker guards' }
if ($form -match 'LmStudio|HttpClient|SemanticReview|CandidateReview|SaveSession|IsolatedPackStager|ReviewExporter|Process\.|\.SaveCandidate') { throw 'Corpus UI crossed offline inspection boundary' }
if ($form -notmatch 'selected.Count == 20' -or $form -notmatch 'SOURCE_MATERIAL_ONLY' -or $form -notmatch 'Task.Run\(') { throw 'Missing bounded selection/worker contract' }
$importer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.Core/ChatCorpusImporter.cs') -Raw -Encoding UTF8
$store = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.Core/CorpusStore.cs') -Raw -Encoding UTF8
if ($importer -match 'ReadToEnd|ExtractTo|ExtractAll|Process\.|HttpClient|LmStudio|File.Write') { throw 'Importer is not read-only bounded streaming' }
if ($importer.IndexOf('PreflightDirectory(input, token)') -gt $importer.IndexOf('using var zip = new ZipArchive')) { throw 'Directory preflight runs too late' }
if ($importer -notmatch 'entry.Crc32' -or $importer -notmatch 'MaxLineBytes' -or $importer -notmatch 'FRIENDTELL') { throw 'Missing ZIP/privacy budgets' }
if ($store -match 'token.Register\(.*\.Cancel' -or $store -notmatch 'sqlite3_progress_handler' -or $store -notmatch 'temp_store=MEMORY' -or $store -notmatch 'Pooling = false') { throw 'SQLite cancellation/temp isolation missing' }
Write-Output 'PASS PSS-006 static Designer, six tabs, handlers/resx/nesting; offline UI, streaming/privacy/SQL guards. NOT VS/UI/DPI proof.'
if ($StaticOnly) { exit 0 }

$owned = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-006-owned-files.txt') -Encoding UTF8
if ($owned.Count -ne @($owned | Select-Object -Unique).Count) { throw 'Duplicate inventory paths' }
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
foreach ($relative in $owned) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        $relative -match '(^|/)(artifacts|workspace|bin|obj)/|\.(zip|db|sqlite|log)$|\.db-|\.sqlite-') { throw ('Private/binary path in inventory: ' + $relative) }
    $content = [IO.File]::ReadAllText($path, (New-Object Text.UTF8Encoding $false,$true))
    if ($relative -eq 'scripts/Verify-PSS006.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw ('Mojibake marker in ' + $relative) } }
}
Write-Output 'PASS mojibake markers in changed files checked (strict UTF-8)'
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw ('Escaped Cyrillic in ' + $relative) }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifest = Get-Content -LiteralPath (Join-Path $root 'docs/tasks/PSS-006/PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $manifest.files) {
    $path = Join-Path $root $item.path
    if ((Get-Item -LiteralPath $path).Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw ('Task package changed: ' + $item.path) }
}
Write-Output 'PASS unchanged task package SHA/bytes'
if ($GitScope) {
    Push-Location $root
    try {
        $changed = @(git diff --name-only f92431aa5594a62210916e94bc6b617ec51f4bdc)
        if ($LASTEXITCODE -ne 0) { throw 'Scope diff failed' }
        foreach ($path in $changed) { if ($owned -notcontains $path) { throw ('Frozen/outside owner changed: ' + $path) } }
        Write-Output ('PASS tracked frozen-owner scope vs required base; changed paths=' + $changed.Count)
    } finally { Pop-Location }
}
