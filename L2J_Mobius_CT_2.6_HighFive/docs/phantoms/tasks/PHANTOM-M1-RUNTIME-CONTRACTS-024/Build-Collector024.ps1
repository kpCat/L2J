$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$ops=Join-Path $module '.phantom-local/ops024'
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
& "$jdk/javac.exe" -encoding UTF-8 -cp "$module/dist/libs/GameServer.jar;$module/dist/libs/*" -d $ops (Join-Path $PSScriptRoot 'Contract024Agent.java') (Join-Path $PSScriptRoot 'Contract024CrashAgent.java') (Join-Path $PSScriptRoot 'AttachContract024.java')
if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
$manifest=Join-Path $ops 'contract024-agent.mf'
"Manifest-Version: 1.0`nAgent-Class: Contract024Agent4`n`n" | Set-Content -LiteralPath $manifest -Encoding ascii
$classes=@(Get-ChildItem -LiteralPath $ops -Filter 'Contract024Agent*.class' | ForEach-Object {$_.Name})
Push-Location $ops
try{ & "$jdk/jar.exe" --create --file contract024-agent4.jar --manifest $manifest @classes; if($LASTEXITCODE -ne 0){throw 'Collector package failed.'} }
finally{Pop-Location}
$crashManifest=Join-Path $ops 'contract024-crash.mf'
"Manifest-Version: 1.0`nAgent-Class: Contract024CrashAgent`n`n" | Set-Content -LiteralPath $crashManifest -Encoding ascii
$crashClasses=@(Get-ChildItem -LiteralPath $ops -Filter 'Contract024CrashAgent*.class' | ForEach-Object {$_.Name})
Push-Location $ops
try{ & "$jdk/jar.exe" --create --file contract024-crash.jar --manifest $crashManifest @crashClasses; if($LASTEXITCODE -ne 0){throw 'Crash agent package failed.'} }
finally{Pop-Location}
'TASK024_COLLECTOR_BUILD_PASS'
