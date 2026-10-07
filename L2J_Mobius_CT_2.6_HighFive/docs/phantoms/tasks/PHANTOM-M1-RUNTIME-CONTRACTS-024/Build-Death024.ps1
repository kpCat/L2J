$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops024'
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
& "$jdk/javac.exe" -encoding UTF-8 -cp "$module/dist/libs/GameServer.jar;$module/dist/libs/*" -d $ops (Join-Path $PSScriptRoot 'Contract024DeathAgent.java')
if($LASTEXITCODE -ne 0){throw 'Death fixture compile failed.'}
$manifest=Join-Path $ops 'contract024-death.mf'
"Manifest-Version: 1.0`nAgent-Class: Contract024DeathAgent`n`n" | Set-Content -LiteralPath $manifest -Encoding ascii
$classes=@(Get-ChildItem -LiteralPath $ops -Filter 'Contract024DeathAgent*.class' | ForEach-Object {$_.Name})
Push-Location $ops
try{ & "$jdk/jar.exe" --create --file contract024-death.jar --manifest $manifest @classes; if($LASTEXITCODE -ne 0){throw 'Death fixture package failed.'} }
finally{Pop-Location}
'TASK024_DEATH_BUILD_PASS'
