param([switch]$LayoutOnly, [string]$ZipPath)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$designer = Get-Content -LiteralPath (Join-Path $root 'src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs') -Raw -Encoding UTF8
function Height([string]$name) {
    $match = [regex]::Match($designer, [regex]::Escape($name) + ' = new System.Drawing.Size\(\d+, (\d+)\)')
    if (-not $match.Success) { throw "Missing Designer size: $name" }
    return [int]$match.Groups[1].Value
}
# Conservative 40px non-client allowance; useful static gate, not physical DPI evidence.
$minimumEditorHeight = (Height 'txtInstruction.Size') + (Height 'MinimumSize') - (Height 'ClientSize') - 40
if ($minimumEditorHeight -lt 40) { throw "Instruction editor collapses at minimum form height: $minimumEditorHeight px" }
if ($designer -notmatch 'cmbMode.SelectedIndex = 0;' -or $designer -notmatch 'Ответ \(TEMPLATE\)' -or $designer -notmatch 'Входная фраза \(PATTERN\)' -or $designer -notmatch 'Смешанный \(MIXED\)') { throw 'Missing explicit mode/default' }
Write-Output "PASS static minimum editor height: $minimumEditorHeight px; mode selector/default. NOT a visual DPI test."
if ($LayoutOnly) { exit 0 }
$owned = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-002-owned-files.txt') -Encoding UTF8
$mojibake = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
foreach ($relative in $owned) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Owned file missing: $relative" }
    $content = [IO.File]::ReadAllText($path)
    # This verifier's marker literals are intentional technical search data.
    $scanContent = $content
    if ($relative -eq 'scripts/Verify-PSS002.ps1') { $scanContent = ($content -split "`n" | Where-Object { -not $_.StartsWith('$mojibake = ') }) -join "`n" }
    foreach ($marker in $mojibake) { if ($scanContent.Contains($marker)) { throw "Mojibake marker in $relative" } }
    if ($content -match $escaped) { throw "Escaped Cyrillic in $relative" }
}
Write-Output 'PASS: mojibake markers in changed files checked (verifier marker literals excluded).'
Write-Output 'PASS: escaped Cyrillic in changed files checked separately.'
$sourceEvidence = Get-Content -LiteralPath (Join-Path $root 'reports/PSS-002-source-smoke.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sourceRoot = Join-Path $sourceEvidence.Source 'dist/game/data/phantoms'
$verified = 0
foreach ($stamp in $sourceEvidence.Before) {
    $path = Join-Path $sourceRoot $stamp.RelativePath
    if ((Get-Item -LiteralPath $path).Length -ne $stamp.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $stamp.Sha256) { throw "Source drift: $($stamp.RelativePath)" }
    $verified++
}
Write-Output "PASS: final source SHA/bytes equality $verified/$($sourceEvidence.Before.Count). Read-only."
$taskManifest = Get-Content -LiteralPath (Join-Path $root 'docs/tasks/PSS-002/PACKAGE_MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $taskManifest.files) {
    $path = Join-Path $root $item.path
    if ((Get-Item -LiteralPath $path).Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $item.sha256) { throw "User task package changed: $($item.path)" }
}
Write-Output 'PASS: original user task package manifest SHA/bytes unchanged.'
if ($ZipPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        $manifest = @()
        foreach ($entry in $zip.Entries) {
            $relative = $entry.FullName.Replace('\','/')
            if ($relative.EndsWith('/')) { continue }
            if ($relative -match '(^|/)(\.git|\.vs|bin|obj|workspace|local|cache|artifacts)(/|$)|\.(exe|dll|pdb|zip|pem|key|pfx)$|(^|/)\.env|settings\.json$|session\.json$|secrets?\.json$|live-drafts') { throw "Forbidden ZIP entry: $relative" }
            $path = Join-Path $root $relative
            $stream = $entry.Open()
            try { $hash = [Security.Cryptography.SHA256]::Create(); $digest = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
            finally { $stream.Dispose(); if ($hash) { $hash.Dispose() } }
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $digest -or (Get-Item -LiteralPath $path).Length -ne $entry.Length) { throw "ZIP/source mismatch: $relative" }
            $manifest += [pscustomobject]@{ Path=$relative; Bytes=$entry.Length; Sha256=$digest }
        }
        foreach ($relative in $owned) { if ($manifest.Path -notcontains $relative) { throw "Owned path absent from ZIP: $relative" } }
        $allowedRoots = @('src','tests','scripts','docs','reports','.editorconfig','.gitignore','AGENTS.md','Directory.Build.props','PhantomSemanticStudio.sln','README_RU.md','START_HERE_RU.txt','Start.cmd','BASELINE_MANIFEST.json')
        $expected = @()
        foreach ($name in $allowedRoots) {
            $source = Join-Path $root $name
            if (-not (Test-Path -LiteralPath $source)) { continue }
            foreach ($file in Get-ChildItem -LiteralPath $source -File -Recurse) {
                $relative = $file.FullName.Substring($root.Length).TrimStart('\','/').Replace('\','/')
                if ($relative -match '(^|/)(bin|obj|\.vs|workspace|local|node_modules|cache)(/|$)|\.(exe|dll|pdb|zip|user|suo|pem|key|pfx)$|(^|/)\.env|settings\.json$|session\.json$|secrets?\.json$|live-drafts') { continue }
                $expected += $relative
            }
        }
        if (Compare-Object ($expected | Sort-Object) ($manifest.Path | Sort-Object)) { throw 'ZIP does not match exact allowed source inventory' }
        $receipt = @{ Zip=[IO.Path]::GetFullPath($ZipPath); Sha256=(Get-FileHash -LiteralPath $ZipPath).Hash.ToLowerInvariant(); Files=$manifest.Count; Manifest=$manifest }
        [IO.File]::WriteAllText((Join-Path $root 'artifacts/PSS-002-zip-manifest.json'), ($receipt | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
        Write-Output "PASS: ZIP $($manifest.Count) files, all owned paths, per-file SHA/bytes, no workspace/token files/binaries; manifest in artifacts."
    }
    finally { $zip.Dispose() }
}
