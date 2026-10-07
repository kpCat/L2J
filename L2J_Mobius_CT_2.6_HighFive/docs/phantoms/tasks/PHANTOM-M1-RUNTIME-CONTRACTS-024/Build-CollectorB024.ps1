$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops024'
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
& "$jdk/javac.exe" -encoding UTF-8 -cp "$module/dist/libs/GameServer.jar;$module/dist/libs/*" -d $ops (Join-Path $PSScriptRoot 'Contract024Agent5.java')
if($LASTEXITCODE -ne 0){throw 'Collector B compile failed.'}
$manifest=Join-Path $ops 'contract024-agent5.mf'
"Manifest-Version: 1.0`nAgent-Class: Contract024Agent5`n`n" | Set-Content -LiteralPath $manifest -Encoding ascii
$classes=@(Get-ChildItem -LiteralPath $ops -Filter 'Contract024Agent5*.class' | ForEach-Object {$_.Name})
Push-Location $ops
try{ & "$jdk/jar.exe" --create --file contract024-agent5.jar --manifest $manifest @classes; if($LASTEXITCODE -ne 0){throw 'Collector B package failed.'} }
finally{Pop-Location}
'TASK024_DUAL_COHORT_BUILD_PASS'
