param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$module045=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!(Test-Path (Join-Path $module045 'build.xml')) -or !(Test-Path (Join-Path $PSScriptRoot 'Control032.ps1'))) {throw 'Stage exact tracked045 sources in module/.phantom-local/ops045 first.'}
$classes045=Join-Path $PSScriptRoot 'stock-shutdown-classes045v2'
$jar045=Join-Path $PSScriptRoot 'stock-shutdown045v2.jar'
if ((Test-Path $classes045) -or (Test-Path $jar045)) {throw 'New own045 shutdown build output required.'}
New-Item -ItemType Directory -Path $classes045 | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp (Join-Path $module045 'dist/libs/*') -d $classes045 (Join-Path $PSScriptRoot 'RequestShutdown.java') (Join-Path $PSScriptRoot 'RuntimeShutdownAgent045.java')
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown sources failed compilation.'}
$manifest045=Join-Path $classes045 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest045,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent045`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm $jar045 $manifest045 -C $classes045 .
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown helper packaging failed.'}
Get-FileHash $jar045
