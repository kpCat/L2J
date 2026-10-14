param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!(Test-Path (Join-Path $module043 'build.xml')) -or !(Test-Path (Join-Path $PSScriptRoot 'Control032.ps1'))) {throw 'Stage exact tracked043 sources in module/.phantom-local/ops043 first.'}
$classes043=Join-Path $PSScriptRoot 'stock-shutdown-classes043'
$jar043=Join-Path $PSScriptRoot 'stock-shutdown043.jar'
if ((Test-Path $classes043) -or (Test-Path $jar043)) {throw 'New own043 shutdown build output required.'}
New-Item -ItemType Directory -Path $classes043 | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp (Join-Path $module043 'dist/libs/*') -d $classes043 (Join-Path $PSScriptRoot 'RequestShutdown.java') (Join-Path $PSScriptRoot 'RuntimeShutdownAgent.java')
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown sources failed compilation.'}
$manifest043=Join-Path $classes043 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest043,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm $jar043 $manifest043 -C $classes043 .
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown helper packaging failed.'}
Get-FileHash $jar043
