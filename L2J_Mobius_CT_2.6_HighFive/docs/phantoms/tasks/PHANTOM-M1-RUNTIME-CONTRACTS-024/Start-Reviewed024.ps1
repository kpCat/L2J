[CmdletBinding()]
param([switch]$Background,[switch]$ValidateOnly)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime=Join-Path $module '.phantom-local/contract024c/runtime'
$expectedSha='ca3cbef9c03b695dcb9f84a734c5535b4ac72027'
$reference=Get-Content (Join-Path $PSScriptRoot 'evidence/FROZEN_R4_SCENE_A.json') -Raw | ConvertFrom-Json
$manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
if($manifest.codeSha -cne $expectedSha -or $reference.codeSha -cne $expectedSha){throw 'Reviewed source SHA mismatch.'}
foreach($file in $reference.files){
    $path=[IO.Path]::GetFullPath((Join-Path $runtime $file.path))
    if(-not $path.StartsWith($runtime+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $path).Hash -cne $file.sha256){throw "Reviewed runtime file differs: $($file.path)"}
}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$url=Get-PilotIniValue (Join-Path $runtime 'game/config/Database.ini') 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay_contract024c\?'){throw 'Reviewed owned clone mismatch.'}
Write-Output "REVIEWED_CODE_SHA=$expectedSha CONFIG_AND_DATA=$($reference.configAndDataFingerprint) OWNED_CLONE=contract024c"
if($ValidateOnly){'REVIEWED_LAUNCH_GUARDS_PASS';return}
& (Join-Path $runtime 'Start-LocalPlay.ps1') -Background:$Background -GameTimeoutSeconds 180
