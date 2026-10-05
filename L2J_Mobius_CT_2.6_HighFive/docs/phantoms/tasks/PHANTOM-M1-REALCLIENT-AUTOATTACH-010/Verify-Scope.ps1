$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$prefix = 'L2J_Mobius_CT_2.6_HighFive/'
$taskRelative = 'docs/phantoms/tasks/PHANTOM-M1-REALCLIENT-AUTOATTACH-010/'
$allowed = @(Import-Csv (Join-Path $PSScriptRoot 'SOURCE_MAP.tsv') -Delimiter "`t" | Where-Object mode -eq MODIFY | ForEach-Object path)
$changed = @(& git -C $module diff --name-only)
if ($LASTEXITCODE -ne 0) { throw 'Git scope inspection failed.' }
foreach ($path in $changed) { if ($path -notin $allowed -and -not $path.StartsWith($prefix+$taskRelative)) { throw "Outside task010 scope: $path" } }
$files = @($allowed | ForEach-Object { Get-Item -LiteralPath (Join-Path $module $_.Substring($prefix.Length)) })
$files += @(Get-ChildItem -LiteralPath $PSScriptRoot -File -Recurse)
$moji = "Рџ|Рќ|Рћ|Р•|РЎ|Р›|Р¤|Рњ|РЈ|Рљ|Рґ|Рµ|Р°|Р»|РЅ|Рѕ|СЏ|С€|СЂ|С‹|СЊ|С‚|Сѓ|С‡|С…|С†|�"
$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
foreach ($check in @(@{name='MOJIBAKE';pattern=$moji},@{name='ESCAPED_CYRILLIC';pattern=$escaped})) {
    $hits = @($files | Where-Object Name -ne 'Verify-Scope.ps1' | Select-String -Pattern $check.pattern)
    Write-Output "$($check.name)=$($hits.Count)"
    if ($hits.Count) { $hits | Select-Object Path,LineNumber; throw 'Encoding check failed.' }
}
foreach ($file in @($files | Where-Object Extension -eq '.ps1')) {
    $tokens=$null; $errors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
    if ($errors.Count) { throw $errors[0] }
}
& git -C $module diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace diff check failed.' }
Write-Output 'SCOPE_GUARD=PASS; validator regex literals excluded from encoding scan.'
