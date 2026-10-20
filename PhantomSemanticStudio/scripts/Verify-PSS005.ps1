param([switch]$StaticOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Verify-Designer.ps1')
$designer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs') -Raw -Encoding UTF8
$form = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.cs') -Raw -Encoding UTF8
foreach ($name in @('btnFindSimilar', 'btnSemanticReview')) {
    if ($designer -notmatch ($name + ' = new System.Windows.Forms.Button\(\);') -or $designer -notmatch ('contentCandidates.Controls.Add\(' + $name + '\)')) { throw ('Missing designed candidate action: ' + $name) }
}
if ($designer -notmatch 'btnFindSimilar.Click \+= FindSimilar_Click' -or $designer -notmatch 'btnSemanticReview.Click \+= SemanticReview_Click') { throw 'Missing semantic events' }
foreach ($handler in @('FindSimilar_Click', 'SemanticReview_Click')) {
    $section = ($form -split ('private async void ' + $handler), 2)[1]
    $section = ($section -split 'private ', 2)[0]
    if ($section -notmatch 'ResolvePendingEdit\(' -or $section -notmatch 'RequireCandidate\(' -or $section -notmatch 'SemanticDuplicateScout.Search\(') { throw ('Missing saved-candidate scout: ' + $handler) }
    if ($handler -eq 'FindSimilar_Click' -and $section -match 'Model\.|HttpClient|ReviewSemanticAsync') { throw 'Offline action invokes network' }
    if ($handler -eq 'SemanticReview_Click' -and ($section -notmatch 'reader.Load\(' -or $section -notmatch 'WithCurrentEvidence\(' -or $section -notmatch 'SaveCandidateVersion\(')) { throw 'Missing fresh-source atomic evidence save' }
}
if ($form -notmatch 'STALE' -or $form -notmatch 'MODEL_ADVISORY_NOT_VERIFIED' -or $form -notmatch 'COVERAGE_LIMITED') { throw 'Missing honest coverage/freshness display' }
if ($form -match 'BindCandidates\(updated.Id\);\s*txtValidation.Text = FormatSemanticShortlist\(updated, shortlist, evidence, true\)') { throw 'Fresh display can be overwritten with unconditional current=true' }
Write-Output 'PASS PSS-005 static Designer actions and saved-candidate/fresh-source flow; VS/UI/DPI not implied'
if ($StaticOnly) { exit 0 }

$owned = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-005-owned-files.txt') -Encoding UTF8
$markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($relative -eq 'scripts/Verify-PSS005.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^\$markers =' }) -join "`n" }
    foreach ($marker in $markers) { if ($content.Contains($marker)) { throw ('Mojibake marker in ' + $relative) } }
}
Write-Output 'PASS mojibake markers in changed files checked (strict UTF-8)'
foreach ($relative in $owned) {
    $content = [IO.File]::ReadAllText((Join-Path $root $relative), (New-Object Text.UTF8Encoding $false,$true))
    if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw ('Escaped Cyrillic in ' + $relative) }
}
Write-Output 'PASS escaped Cyrillic in changed files checked separately'
$manifest = Get-Content -LiteralPath (Join-Path $root 'docs/tasks/PSS-005/PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $manifest.files) {
    $path = Join-Path $root $item.path
    if ((Get-Item -LiteralPath $path).Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw ('Task package changed: ' + $item.path) }
}
Write-Output 'PASS unchanged task package SHA/bytes'
$stamps = Get-Content -LiteralPath (Join-Path $root 'artifacts/PSS-005/readonly-stamps.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($stamp in $stamps) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $stamp.Path))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Protected stamp escaped Studio' }
    if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $stamp.Sha256) { throw ('Protected owner changed: ' + $stamp.Path) }
}
Write-Output ('PASS unchanged protected owners SHA/bytes: ' + $stamps.Count)
