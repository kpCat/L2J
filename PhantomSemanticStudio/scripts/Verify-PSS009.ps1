param([switch]$StaticOnly, [switch]$GitScope, [switch]$Staged)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$base = 'cddf9a65ffc5b052c110b264c4fe3973f8e6a37c'
& (Join-Path $PSScriptRoot 'Verify-PSS008.ps1') -StaticOnly
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj') -Raw -Encoding UTF8
$names = @('MainForm','StageSelectionForm','DialogueLabForm','ChatCorpusForm','PackQualityForm','V3ProposalForm')
foreach ($name in $names) {
    $prefix = Join-Path $root ('src/PhantomSemanticStudio.WinForms/' + $name)
    $form = [IO.File]::ReadAllText($prefix + '.cs')
    $designer = [IO.File]::ReadAllText($prefix + '.Designer.cs')
    $body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
    if (-not $body -or $body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|File\.|Directory\.|HttpClient|Process\.|Task\.|BuildUi') { throw ('Non-designer logic: ' + $name) }
    if ($form -notmatch ('(?s)public ' + $name + '\(\)\s*\{\s*InitializeComponent\(\);\s*\}')) { throw ('Unsafe ctor: ' + $name) }
    foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
        if ($form -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing handler: ' + $name) }
    }
    [xml]$resx = [IO.File]::ReadAllText($prefix + '.resx')
    if (-not $project.SelectSingleNode("//Compile[@Update='$name.cs']/SubType[text()='Form']") -or
        -not $project.SelectSingleNode("//Compile[@Update='$name.Designer.cs']/DependentUpon[text()='$name.cs']") -or
        -not $project.SelectSingleNode("//EmbeddedResource[@Update='$name.resx']/DependentUpon[text()='$name.cs']")) { throw ('Missing nesting: ' + $name) }
    if ($body.Contains('AutoScrollMinSize') -or -not $body.Contains('MinimumSize = new System.Drawing.Size(760, 420)')) { throw ('Unscaled scroll/minimum guard: ' + $name) }
    if ($name -eq 'MainForm') {
        $suffixes = @('Generate','Chat','Library','Candidates','Export','Settings')
        if ([regex]::Matches($body, '= new System.Windows.Forms.TabPage\(\)').Count -ne 6) { throw 'Tab count changed' }
        foreach ($suffix in $suffixes) {
            $page = 'tab' + $suffix
            $sizing = $page + '.Size = new System.Drawing.Size(1252, 712);'
            if (-not $body.Contains($sizing) -or $body.IndexOf($sizing) -gt $body.IndexOf('content' + $suffix + '.Controls.Add(')) { throw ('Missing early page guard: ' + $page) }
            if (-not $body.Contains($page + '.Controls.Add(layout' + $suffix + ')')) { throw ('Incorrect content owner: ' + $page) }
        }
        if (-not $body.Contains('tabs.Multiline = true;')) { throw 'Tab headers can be hidden' }
        foreach ($control in @('gridLibrary','gridCandidates','txtConversation','grpRequest')) {
            if ($body -notmatch ($control + '\.Anchor = [^;]*Bottom[^;]*Right')) { throw ('Stretch contract missing: ' + $control) }
        }
        $width = 1252; $height = 712
    } else {
        $suffixes = @('Root')
        $size = [regex]::Match($body, 'ClientSize = new System.Drawing.Size\((\d+), (\d+)\);')
        $width = [int]$size.Groups[1].Value; $height = [int]$size.Groups[2].Value
        if (-not $body.Contains('Controls.Add(layoutRoot)')) { throw ('Incorrect root content owner: ' + $name) }
    }
    foreach ($suffix in $suffixes) {
        $layout = 'layout' + $suffix; $content = 'content' + $suffix
        foreach ($required in @(
            "${layout}.Dock = System.Windows.Forms.DockStyle.Fill;", "${layout}.AutoScroll = true;",
            "${layout}.ColumnCount = 1;", "${layout}.RowCount = 1;",
            "${layout}.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.AutoSize));",
            "${layout}.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.AutoSize));",
            "${content}.Dock = System.Windows.Forms.DockStyle.Fill;",
            "${content}.MinimumSize = new System.Drawing.Size($width, $height);",
            "${layout}.Controls.Add($content, 0, 0);")) {
            if (-not $body.Contains($required)) { throw ('Responsive/scalable canvas guard: ' + $name + '/' + $required) }
        }
        if ($body.IndexOf($content + '.Size =') -gt $body.IndexOf($content + '.Controls.Add(')) { throw ('Late anchor baseline: ' + $content) }
    }
}
Write-Output 'PASS PSS-009 static six-page sizes/scroll/anchors; six ctors/handlers/resx/nesting; NOT physical UI/DPI/VS'
if ($StaticOnly) { exit 0 }
$owned = @(Get-Content -LiteralPath (Join-Path $root 'reports/PSS-009-owned-files.txt') -Encoding UTF8 | Where-Object { $_ })
if ($owned.Count -ne @($owned | Sort-Object -Unique).Count) { throw 'Duplicate owned inventory' }
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
foreach ($relative in $owned) {
    if ($relative -match '\\|(^|/)(artifacts|workspace|labs|corpora|bin|obj|private-input)/|\.\.|\.(zip|db|sqlite|log|dll|exe|pdb|key|png|jpg)$|\.db-|\.sqlite-|session\.json|settings\.json') { throw ('Private/unsafe path: ' + $relative) }
    $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Invalid owned path: ' + $relative) }
    $content = [Text.UTF8Encoding]::new($false,$true).GetString([IO.File]::ReadAllBytes($path))
    if ($relative -match '^scripts/Verify-PSS\d+\.ps1$') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw ('Mojibake: ' + $relative) } }
    if ($content -match 'sk-[A-Za-z0-9_-]{24,}|gh[pousr]_[A-Za-z0-9]{24,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----') { throw ('Secret marker: ' + $relative) }
    if ($relative.StartsWith('reports/') -and $content -match '(?i)[A-Z]:[\\/]Users[\\/]|chat\.zip|\.sqlite\b') { throw ('Private report content: ' + $relative) }
}
Write-Output ('PASS mojibake markers in changed files checked; strict UTF-8; privacy inventory files=' + $owned.Count)
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative),[Text.UTF8Encoding]::new($false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw ('Escaped Cyrillic: ' + $relative) }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifestPath = Join-Path $root 'docs/tasks/PSS-009/PACKAGE_MANIFEST.json'
if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne '8e5a385f624dc701454ce4f660bc81f7aec9118abab0733746aa81df497be6f7') { throw 'Task manifest changed' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    $path = Join-Path $root $entry.path
    if ((Get-Item -LiteralPath $path).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw ('Task bytes changed: ' + $entry.path) }
}
Write-Output 'PASS task package exact SHA/bytes unchanged (11 files + manifest)'
if ($GitScope -or $Staged) {
    Push-Location $root
    try {
        if ((git rev-parse --show-toplevel).Replace('/','\') -ne $root) { throw 'Wrong Git root' }
        $fetch = git remote get-url origin; $push = git remote get-url --push origin
        if ($fetch -ne 'https://github.com/kpCat/PhantomSemanticStudio.git' -or $push -ne $fetch) { throw 'Wrong Studio origin' }
        $changed = @(git diff --name-only $base); if ($LASTEXITCODE -ne 0) { throw 'Scope diff failed' }
        $untracked = @(git ls-files --others --exclude-standard); if ($LASTEXITCODE -ne 0) { throw 'Inventory failed' }
        if ($changed -contains 'PhantomSemanticStudio.sln') {
            if ((Get-FileHash -LiteralPath 'PhantomSemanticStudio.sln' -Algorithm SHA256).Hash.ToLowerInvariant() -ne '4aa3a62b295b89b435bbad62ea34a67b0eb3447f52a89c927d711138af786442') { throw 'Incoming user solution dirt changed' }
            $changed = @($changed | Where-Object { $_ -ne 'PhantomSemanticStudio.sln' })
            Write-Output 'PASS incoming user solution preserved byte-exact, excluded from staging; tree has explicit user dirt'
        }
        $actual = @($changed + $untracked | Sort-Object -Unique)
        foreach ($relative in $actual) { if ($owned -notcontains $relative) { throw ('Outside exact allowlist: ' + $relative) } }
        if ($actual.Count -ne $owned.Count) { throw 'Owned inventory differs from actual changes' }
        Write-Output ('PASS exact scope vs required base; owned=' + $owned.Count + '; Core/LM/Java/L2J excluded')
        if ($Staged) {
            $index = @(git diff --cached --name-only $base); if ($LASTEXITCODE -ne 0 -or $index.Count -ne $owned.Count) { throw 'Staged count mismatch' }
            foreach ($relative in $index) { if ($owned -notcontains $relative) { throw ('Unowned staged file: ' + $relative) } }
            git diff --cached --check $base; if ($LASTEXITCODE -ne 0) { throw 'Whitespace gate failed' }
            Write-Output ('PASS exact staged allowlist=' + $index.Count + '; private/binary/keys excluded')
        }
    } finally { Pop-Location }
}
