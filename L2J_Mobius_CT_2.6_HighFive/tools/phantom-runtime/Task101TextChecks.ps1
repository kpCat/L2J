[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Mojibake','EscapedCyrillic')][string]$Action,[Parameter(Mandatory)][string]$Allowlist)
$ErrorActionPreference='Stop'
$patterns=if($Action -eq 'Mojibake'){@("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")}else{@('\\u04[0-9A-Fa-f]{2}','\\u05[0-9A-Fa-f]{2}','&#x04[0-9A-Fa-f]{2};','&#x05[0-9A-Fa-f]{2};','&#X04[0-9A-Fa-f]{2};','&#X05[0-9A-Fa-f]{2};')}
$matchesFound=[Collections.Generic.List[string]]::new()
foreach($path in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($Allowlist))){
    if(!(Test-Path -LiteralPath $path -PathType Leaf)){continue}
    # The matcher itself intentionally contains the full forbidden-marker dictionary.
    if([IO.Path]::GetFileName($path) -eq 'Task101TextChecks.ps1'){continue}
    $text=[IO.File]::ReadAllText([IO.Path]::GetFullPath($path))
    foreach($pattern in $patterns){
        $found=if($Action -eq 'Mojibake'){$text.Contains($pattern)}else{[regex]::IsMatch($text,$pattern)}
        if($found){$matchesFound.Add($path+': '+$pattern)}
    }
}
Write-Output ('TASK101_TEXT_CHECK '+$Action+' matches='+$matchesFound.Count)
$matchesFound|Write-Output
if($matchesFound.Count){exit 1}
