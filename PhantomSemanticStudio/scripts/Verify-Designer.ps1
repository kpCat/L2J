$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$designer = Get-Content (Join-Path $root 'src\PhantomSemanticStudio.WinForms\MainForm.Designer.cs') -Raw -Encoding UTF8
$form = Get-Content (Join-Path $root 'src\PhantomSemanticStudio.WinForms\MainForm.cs') -Raw -Encoding UTF8
$body = ($designer -split 'private void InitializeComponent\(\)', 2)[1]
if (-not $body) { throw 'InitializeComponent missing' }
$body = ($body -split '#endregion', 2)[0]
if ($body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(') { throw 'Non-designer logic in InitializeComponent' }
if ($body -match 'File\.|Directory\.|HttpClient|Process\.|Task\.') { throw 'I/O in InitializeComponent' }
if ($form -notmatch '(?s)public MainForm\(\)\s*\{\s*InitializeComponent\(\);\s*\}') { throw 'Constructor is not design-safe' }
if ([regex]::Matches($body, '= new System.Windows.Forms.TabPage\(\);').Count -ne 6) { throw 'Expected six designed tabs' }
foreach ($match in [regex]::Matches($body, '\+=\s*(\w+)\s*;')) {
    if ($form -notmatch ('\b' + [regex]::Escape($match.Groups[1].Value) + '\s*\(')) { throw ('Missing handler: '+$match.Groups[1].Value) }
}
[xml]$resx = Get-Content (Join-Path $root 'src\PhantomSemanticStudio.WinForms\MainForm.resx') -Raw -Encoding UTF8
Write-Host 'PASS: static Designer contract. This is NOT a Visual Studio Designer round-trip test.'
foreach ($name in @('V4ImportForm')) {
    $v4Designer = Get-Content (Join-Path $root "src\PhantomSemanticStudio.WinForms\$name.Designer.cs") -Raw -Encoding UTF8
    $v4Form = Get-Content (Join-Path $root "src\PhantomSemanticStudio.WinForms\$name.cs") -Raw -Encoding UTF8
    $v4Body = ($v4Designer -split 'private void InitializeComponent\(\)', 2)[1]
    if (-not $v4Body) { throw "$name InitializeComponent missing" }
    $v4Body = ($v4Body -split '#endregion', 2)[0]
    if ($v4Body -match '\b(for|foreach|while|if|switch)\s*\(|\bawait\b|=>|\.Select\(|\.Where\(|\bFile\.|\bDirectory\.|\bHttpClient\b|\bProcess\.|\bTask\.') { throw "$name contains non-designer logic" }
    if ($v4Form -notmatch ('(?s)public ' + $name + '\(\)\s*\{\s*InitializeComponent\(\);\s*\}')) { throw "$name constructor is not design-safe" }
    foreach ($handler in [regex]::Matches($v4Body, '\+=\s*(\w+)\s*;')) {
        if ($v4Form -notmatch ('\b' + [regex]::Escape($handler.Groups[1].Value) + '\s*\(')) { throw "$name missing event handler" }
    }
    Write-Host "PASS: $name static Designer contract. Visual Studio round-trip NOT_RUN."
}
