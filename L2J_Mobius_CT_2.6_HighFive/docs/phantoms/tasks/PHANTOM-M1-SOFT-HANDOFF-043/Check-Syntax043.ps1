$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$files043=@(Get-ChildItem -LiteralPath $PSScriptRoot -File -Filter '*.ps1')+@(Get-ChildItem -LiteralPath (Join-Path $module043 '.phantom-local/ops043') -File -Filter '*.ps1')
foreach($file043 in $files043){
 $tokens043=$null;$errors043=$null
 [void][Management.Automation.Language.Parser]::ParseFile($file043.FullName,[ref]$tokens043,[ref]$errors043)
 if($errors043.Count){throw ($errors043 | Out-String)}
}
"WinPS5.1 syntax PASS: $($files043.Count) bounded043 scripts."
