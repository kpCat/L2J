[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputPath)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if($runtime -notmatch '[\\/]contract024[a-h][\\/]runtime$'){throw 'Exact TASK024 runtime required.'}
if(Test-Path -LiteralPath $OutputPath){throw 'Fingerprint evidence exists.'}
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
$files=@(foreach($part in @('game/config','login/config','libs','game/data')){
    Get-ChildItem (Join-Path $runtime $part) -File -Recurse | ForEach-Object {
        [pscustomobject]@{path=$_.FullName.Substring($runtime.Length+1).Replace('\','/');sha256=(Get-FileHash $_.FullName).Hash}
    }
}) | Sort-Object path
$bytes=[Text.Encoding]::UTF8.GetBytes(($files | ForEach-Object {"$($_.path)`t$($_.sha256)`n"}) -join '')
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
[pscustomobject]@{codeSha=$manifest.codeSha;gameJarSha256=$manifest.gameJarSha256;configAndDataFingerprint=$hash;files=$files} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputPath -Encoding utf8
"FROZEN_RUNTIME_SHA=$($manifest.codeSha) CONFIG_AND_DATA=$hash FILES=$($files.Count)"
