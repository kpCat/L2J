param()
$ErrorActionPreference='Stop'
$repo042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../..'))
$paths042=Get-Content (Join-Path $PSScriptRoot 'REVIEW_ALLOWLIST042.txt')
$checked042=0
foreach($path042 in $paths042 | Where-Object {$_ -like '*.ps1'}){
    $tokens042=$null;$errors042=$null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $repo042 $path042),[ref]$tokens042,[ref]$errors042)
    if($errors042.Count){throw ($errors042 | Out-String)}
    $checked042++
}
"TASK042 PowerShell syntax PASS: $checked042 exact source files."
