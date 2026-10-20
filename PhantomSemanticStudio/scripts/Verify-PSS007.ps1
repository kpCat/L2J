param([switch]$StaticOnly, [switch]$GitScope, [switch]$Staged)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Verify-PSS006.ps1') -StaticOnly
$form = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/DialogueLabForm.cs') -Raw -Encoding UTF8
$designer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/DialogueLabForm.Designer.cs') -Raw -Encoding UTF8
$body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
if (-not $body) { throw 'Lab InitializeComponent missing' }
$body = ($body -split '#endregion', 2)[0]
if ($body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|File\.|Directory\.|HttpClient|Process\.|Task\.|BuildUi') { throw 'Non-designer logic in lab layout' }
if ($form -notmatch '(?s)public DialogueLabForm\(\)\s*\{\s*InitializeComponent\(\);\s*\}') { throw 'Lab constructor is not design safe' }
foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
    if ($form -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing lab handler: ' + $match.Groups[1].Value) }
}
[xml]$resx = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/DialogueLabForm.resx') -Raw -Encoding UTF8
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj') -Raw -Encoding UTF8
if (-not $project.SelectSingleNode("//Compile[@Update='DialogueLabForm.cs']/SubType[text()='Form']") -or
    -not $project.SelectSingleNode("//Compile[@Update='DialogueLabForm.Designer.cs']/DependentUpon[text()='DialogueLabForm.cs']") -or
    -not $project.SelectSingleNode("//EmbeddedResource[@Update='DialogueLabForm.resx']/DependentUpon[text()='DialogueLabForm.cs']")) { throw 'Missing lab VS nesting' }
foreach ($required in @('chkMentor.Checked = false', 'chkContextReviewed.Checked = false', 'chkShareCorpus.Checked = false', 'chkPiiReviewed.Checked = false', 'AutoScaleMode.Dpi', 'ReadOnly = true')) {
    if ($body -notmatch [regex]::Escape($required)) { throw ('Missing lab UI contract: ' + $required) }
}
$mainDesigner = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs') -Raw -Encoding UTF8
if ($mainDesigner -notmatch 'contentChat.Controls.Add\(btnDialogueLab\)' -or $mainDesigner -notmatch 'btnDialogueLab.Click \+= DialogueLab_Click') { throw 'Missing lab entry in existing tab' }
foreach ($path in @(Get-ChildItem -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.Core') -Filter '*.cs') + @(Get-ChildItem -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms') -Filter '*.cs')) {
    $content = Get-Content -LiteralPath $path.FullName -Raw -Encoding UTF8
    if ($content -match 'Process\.Start|ProcessStartInfo|DllImport|Invoke-Expression|/models/load|/models/unload|ExtractToDirectory') { throw ('Forbidden runtime execution/lifecycle: ' + $path.Name) }
}
Write-Output 'PASS PSS-007 static Designer/handlers/ctor/resx/nesting/entry/default permissions; runtime no shell/model lifecycle. NOT physical UI/DPI/VS.'
if ($StaticOnly) { exit 0 }

$owned = @(Get-Content -LiteralPath (Join-Path $root 'reports/PSS-007-owned-files.txt') -Encoding UTF8)
if ($owned.Count -ne @($owned | Select-Object -Unique).Count) { throw 'Duplicate owned inventory' }
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
$checked = @()
foreach ($relative in $owned) {
    if ($relative -match '\\|(^|/)(artifacts|workspace|labs|corpora|bin|obj)/|\.\.|\.(zip|db|sqlite|log|dll|exe|pdb)$|\.db-|\.sqlite-|session\.json|settings\.json') { throw ('Private/unsafe inventory path: ' + $relative) }
    $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Inventory outside root' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Missing owned file: ' + $relative) }
    $bytes = [IO.File]::ReadAllBytes($path)
    $content = (New-Object Text.UTF8Encoding $false,$true).GetString($bytes)
    # Only this technical marker table is excluded. User-facing text is never excluded.
    if ($relative -eq 'scripts/Verify-PSS007.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw ('Mojibake marker: ' + $relative) } }
    $checked += $relative
}
Write-Output ('PASS mojibake markers in changed files checked; strict UTF-8; files=' + $checked.Count)
foreach ($relative in $checked) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw ('Escaped Cyrillic: ' + $relative) }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifest = Get-Content -LiteralPath (Join-Path $root 'docs/tasks/PSS-007/PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    $path = Join-Path $root $entry.path
    if ((Get-Item -LiteralPath $path).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw ('Task package bytes changed: ' + $entry.path) }
}
$ignore = Get-Content -LiteralPath (Join-Path $root '.gitignore') -Raw -Encoding UTF8
foreach ($pattern in @('artifacts/', 'workspace/', '*.zip', '*.db', '*.sqlite', '*.log')) { if (-not $ignore.Contains($pattern)) { throw ('Missing private ignore: ' + $pattern) } }
Write-Output 'PASS unchanged task SHA/bytes and private corpus/history ignores; exact owned paths contain no user data artifacts'
if ($GitScope -or $Staged) {
    Push-Location $root
    try {
        $fetchUrl = git remote get-url origin
        $pushUrl = git remote get-url --push origin
        if ($fetchUrl -ne 'https://github.com/kpCat/PhantomSemanticStudio.git' -or $pushUrl -ne $fetchUrl) { throw 'Wrong Studio origin' }
        $changed = @(git diff --name-only 8a583e5bc872d3f61b933f217baa985af5425e5f)
        if ($LASTEXITCODE -ne 0) { throw 'Scope diff failed' }
        $untracked = @(git ls-files --others --exclude-standard)
        if ($LASTEXITCODE -ne 0) { throw 'Untracked inventory failed' }
        foreach ($path in @($changed + $untracked)) { if ($owned -notcontains $path) { throw ('Outside exact owned scope: ' + $path) } }
        Write-Output ('PASS final artifact scope guard vs required base; tracked=' + $changed.Count + '; untracked=' + $untracked.Count)
        if ($Staged) {
            $stagedPaths = @(git diff --cached --name-only)
            if ($LASTEXITCODE -ne 0) { throw 'Staged inventory failed' }
            if ($stagedPaths.Count -ne $owned.Count) { throw 'Staged count differs from exact inventory' }
            foreach ($path in $stagedPaths) { if ($owned -notcontains $path) { throw ('Unowned staged file: ' + $path) } }
            git diff --cached --check
            if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed' }
            Write-Output ('PASS exact staged allowlist=' + $stagedPaths.Count + '; private/binary data excluded')
        }
    } finally { Pop-Location }
}
