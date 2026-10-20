param([switch]$StaticOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$form = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.cs') -Raw -Encoding UTF8
$stage = ($form -split 'private async void StageXml_Click', 2)[1]
$stage = ($stage -split 'private void Cancel_Click', 2)[0]
if ($stage -match 'Where\(c => c.Status == "APPROVED"\)') { throw 'PSS-004 RED: StageXml_Click implicitly selects all APPROVED and blocks >20.' }
if ($stage -notmatch 'new StageSelectionForm\(' -or $stage -notmatch 'ShowDialog\(this\)' -or $stage -notmatch 'StageBatchSelection.SelectExactIds\(') { throw 'Exact modal selection/recheck missing' }
Write-Output 'PASS exact modal selection flow (static)'
$selection = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/StageSelectionForm.cs') -Raw -Encoding UTF8
$designer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/StageSelectionForm.Designer.cs') -Raw -Encoding UTF8
$body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
if (-not $body) { throw 'Selection InitializeComponent missing' }
$body = ($body -split '#endregion', 2)[0]
if ($body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|File\.|Directory\.|HttpClient|Process\.|Task\.|BuildUi') { throw 'Non-designer logic in selection InitializeComponent' }
if ($selection -notmatch '(?s)public StageSelectionForm\(\)\s*\{\s*InitializeComponent\(\);\s*\}') { throw 'Selection constructor is not design-safe' }
foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
    if ($selection -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing selection handler: ' + $match.Groups[1].Value) }
}
foreach ($required in @('ListView\(\)', 'TextBox\(\)', 'CheckBoxes = true', 'ReadOnly = true', 'btnCreate.Enabled = false', 'CancelButton = btnCancel', 'MinimumSize = ', 'AutoScaleMode.Font')) {
    if ($body -notmatch $required) { throw ('Missing standard selection control/layout: ' + $required) }
}
if ($body -match 'btnCreate.DialogResult\s*=.*OK') { throw 'Designer must not bypass Core validation with DialogResult OK' }
[xml]$resx = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/StageSelectionForm.resx') -Raw -Encoding UTF8
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/PhantomSemanticStudio.WinForms.csproj') -Raw -Encoding UTF8
if (-not $project.SelectSingleNode("//Compile[@Update='StageSelectionForm.cs']/SubType[text()='Form']") -or
    -not $project.SelectSingleNode("//Compile[@Update='StageSelectionForm.Designer.cs']/DependentUpon[text()='StageSelectionForm.cs']") -or
    -not $project.SelectSingleNode("//EmbeddedResource[@Update='StageSelectionForm.resx']/DependentUpon[text()='StageSelectionForm.cs']")) { throw 'Missing Visual Studio form nesting' }
if ($selection -notmatch 'HashSet<string> checkedIds = new\(StringComparer.Ordinal\)' -or $selection -notmatch 'e.NewValue' -or $selection -notmatch 'if \(binding' -or $selection -notmatch 'c.Copy\(\)') { throw 'Missing ID-based selection/copy/rebind contract' }
if ($selection -notmatch 'AcceptButton = btnCancel' -or $stage -notmatch 'ConfirmExactSelection\(ids\)' -or $stage -notmatch 'MessageBoxDefaultButton.Button2' -or $stage -notmatch 'if \(!selectionConfirmed\) return' -or $stage -notmatch 'if \(!editorialConfirmed\) return') { throw 'Missing separate default-No consent decisions' }
$core = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.Core/StageBatchSelection.cs') -Raw -Encoding UTF8
foreach ($runtime in @($core, $selection)) {
    if ($runtime -match 'File\.|Directory\.|Process\.|HttpClient|IsolatedPackStager|WorkspaceStore|ReviewExporter') { throw 'Selection must not execute I/O or staging' }
}
if ($stage -match '\.Create\([^;]*ids, true, true' -or $stage -notmatch 'exactIds, selectionConfirmed, editorialConfirmed, token') { throw 'Exact IDs and actual consent must reach existing Stager' }
Write-Output 'PASS new static Designer controls handlers constructor resx nesting ID/filter safety and consent; NOT a VS round-trip or DPI test'
& (Join-Path $PSScriptRoot 'Verify-Designer.ps1')
if ($StaticOnly) { exit 0 }

$owned = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-004-owned-files.txt') -Encoding UTF8
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    # Literal technical search data only; user-facing strings are never excluded.
    if ($relative -eq 'scripts/Verify-PSS004.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw "Mojibake marker in $relative" } }
}
Write-Output 'PASS mojibake markers in changed files checked (strict UTF-8)'
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw "Escaped Cyrillic in $relative" }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifest = Get-Content -LiteralPath (Join-Path $root 'docs/tasks/PSS-004/PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $manifest.files) {
    $path = Join-Path $root $item.path
    if ((Get-Item -LiteralPath $path).Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw ('Task package changed: ' + $item.path) }
}
Write-Output 'PASS task package payload exact SHA/bytes unchanged'
$stampsPath = Join-Path $root 'artifacts/PSS-004/readonly-stamps.json'
if (Test-Path -LiteralPath $stampsPath) {
    $stamps = Get-Content -LiteralPath $stampsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($stamp in $stamps) {
        $path = [IO.Path]::GetFullPath((Join-Path $root $stamp.Path))
        if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Readonly stamp escaped Studio root' }
        if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $stamp.Sha256) { throw ('Readonly source changed: ' + $stamp.Path) }
    }
    Write-Output ('PASS initial readonly production/task SHA/bytes: ' + $stamps.Count + '; actual L2J import NOT_RUN')
} else { Write-Output 'NOT_TESTED initial readonly stamps unavailable; actual L2J import NOT_RUN' }
