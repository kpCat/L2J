[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode,
      [Parameter(Mandatory)][ValidatePattern('^R[1-4]$')][string]$Revision)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$private=Join-Path $module ".phantom-local/night023$Episode"
$runtime=Join-Path $private 'runtime'
$backup=Join-Path $private "revision-$Revision-before"
if(Test-Path -LiteralPath $backup){throw 'Prior revision backup exists; no overwrite.'}
$sha023=(& git -C $module rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or $sha023 -notmatch '^[0-9a-f]{40}$'){throw 'Committed source identity missing.'}
foreach($role in @('GameServer','LoginServer')){
    $pidPath=Join-Path $runtime "local-play/pids/$role.json"
    if(Test-Path -LiteralPath $pidPath){
        $savedPid=Get-Content -LiteralPath $pidPath -Raw | ConvertFrom-Json
        if(Get-Process -Id ([int]$savedPid.pid) -ErrorAction SilentlyContinue){throw "PID still exists; no runtime mutation:$role/$($savedPid.pid)"}
    }
}
$manifestPath=Join-Path $runtime 'local-play.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if($manifest.databaseName -cne "l2jmobiush5_localplay_night023$Episode"){throw 'Owned clone DB mismatch.'}
if((Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Prior JAR fingerprint mismatch.'}
New-Item -ItemType Directory -Path $backup | Out-Null
Copy-Item -LiteralPath $manifestPath -Destination $backup
foreach($role in @('GameServer','LoginServer')){
    $jarPath=Join-Path $runtime "libs/$role.jar"
    Copy-Item -LiteralPath $jarPath -Destination $backup
    Copy-Item -LiteralPath (Join-Path $module "dist/libs/$role.jar") -Destination $jarPath -Force
    $pidPath=Join-Path $runtime "local-play/pids/$role.json"
    if(Test-Path -LiteralPath $pidPath){
        $resolvedPid=[IO.Path]::GetFullPath($pidPath)
        $resolvedBackup=[IO.Path]::GetFullPath($backup)
        if(-not $resolvedPid.StartsWith([IO.Path]::GetFullPath($runtime)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or -not $resolvedBackup.StartsWith([IO.Path]::GetFullPath($private)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact move escaped owned runtime.'}
        Move-Item -LiteralPath $resolvedPid -Destination (Join-Path $resolvedBackup "$role-pid.json")
    }
}
$manifest.codeSha=$sha023; $manifest.gameSourceCodeSha=$sha023
$manifest.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
$manifest.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Output "OWNED_RUNTIME_UPDATED codeSha=$sha023 episode=$Episode databasePreserved=true"
