param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$module044=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!(Test-Path (Join-Path $module044 'build.xml')) -or !(Test-Path (Join-Path $PSScriptRoot 'Control032.ps1'))) {throw 'Stage exact tracked044 sources in module/.phantom-local/ops044 first.'}
$classes044=Join-Path $PSScriptRoot 'stock-shutdown-classes044v2'
$jar044=Join-Path $PSScriptRoot 'stock-shutdown044v2.jar'
if ((Test-Path $classes044) -or (Test-Path $jar044)) {throw 'New own044 shutdown build output required.'}
New-Item -ItemType Directory -Path $classes044 | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp (Join-Path $module044 'dist/libs/*') -d $classes044 (Join-Path $PSScriptRoot 'RequestShutdown.java') (Join-Path $PSScriptRoot 'RuntimeShutdownAgent044.java')
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown sources failed compilation.'}
$manifest044=Join-Path $classes044 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest044,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent044`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm $jar044 $manifest044 -C $classes044 .
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown helper packaging failed.'}
Get-FileHash $jar044
