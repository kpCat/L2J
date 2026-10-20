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
