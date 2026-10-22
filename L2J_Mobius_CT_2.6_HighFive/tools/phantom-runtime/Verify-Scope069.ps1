[CmdletBinding()]
param([ValidateSet('Inventory','Mojibake','Escaped')][string]$Action='Inventory')
$ErrorActionPreference='Stop'
$repo069=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$task069=Join-Path $repo069 '.phantom-local/task069'
Push-Location $repo069
try {
    if($Action -eq 'Inventory') {
        $rows069=@(& git status --porcelain --untracked-files=all -- .gitattributes .gitignore L2J_Mobius_CT_2.6_HighFive)
        if($LASTEXITCODE){throw 'Own069 scope inventory failed.'}
        $paths069=@($rows069 | ForEach-Object {$_.Substring(3)} | Where-Object {$_ -notlike '*PHANTOM-ALTB-VIEW-068V/*'})
        $scope069='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-PLAYABLE-RELEASE-069/SOURCE_SCOPE069.tsv'
        $previous069=@()
        if(Test-Path -LiteralPath $scope069){$previous069=@((Import-Csv -LiteralPath $scope069 -Delimiter "`t").path)}
        $paths069=@($previous069+$paths069+$scope069 | Sort-Object -Unique)
        $paths069 | Set-Content -LiteralPath (Join-Path $task069 'code-scope.txt') -Encoding utf8
        $paths069 | ForEach-Object {[pscustomobject]@{path=$_}} | Export-Csv -LiteralPath $scope069 -Delimiter "`t" -NoTypeInformation -Encoding utf8
        & git -c core.whitespace=cr-at-eol diff --check -- @paths069
        if($LASTEXITCODE){throw 'Own069 whitespace guard failed.'}
        Write-Output "SCOPE069=$($paths069.Count); WHITESPACE_PASS"
        return
    }
    $paths069=Get-Content -LiteralPath (Join-Path $task069 'code-scope.txt')
    if($Action -eq 'Mojibake') {
        $markers069=@(('1056,1119;1056,1116;1056,1115;1056,8226;1056,1038;1056,8250;1056,164;1056,1114;1056,1032;1056,1113;1056,1169;1056,181;1056,176;1056,187;1056,1029;1056,1109;1057,1039;1057,8364;1057,1026;1057,8249;1057,1034;1057,8218;1057,1107;1057,8225;1057,8230;1057,8224;65533').Split(';') | ForEach-Object {-join ($_.Split(',') | ForEach-Object {[char][int]$_})})
        $found069=@(Select-String -LiteralPath $paths069 -Pattern $markers069 -SimpleMatch -Encoding utf8)
    } else {
        $found069=@(Select-String -LiteralPath $paths069 -Pattern '\\u04[0-9A-Fa-f]{2}','\\u05[0-9A-Fa-f]{2}','&#x04[0-9A-Fa-f]{2};','&#x05[0-9A-Fa-f]{2};','&#X04[0-9A-Fa-f]{2};','&#X05[0-9A-Fa-f]{2};' -Encoding utf8 -CaseSensitive)
    }
    $found069 | Select-Object Path,LineNumber,Line
    Write-Output "$Action matches=$($found069.Count)"
} finally {Pop-Location}
