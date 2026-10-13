param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!(Test-Path (Join-Path $module041 'build.xml')) -or !(Test-Path (Join-Path $PSScriptRoot 'Control032.ps1'))) {throw 'Stage exact tracked041 sources in module/.phantom-local/ops041 first.'}
$classes041=Join-Path $PSScriptRoot 'stock-shutdown-classes041'
$jar041=Join-Path $PSScriptRoot 'stock-shutdown041.jar'
if ((Test-Path $classes041) -or (Test-Path $jar041)) {throw 'New own041 shutdown build output required.'}
New-Item -ItemType Directory -Path $classes041 | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp (Join-Path $module041 'dist/libs/*') -d $classes041 (Join-Path $PSScriptRoot 'RequestShutdown.java') (Join-Path $PSScriptRoot 'RuntimeShutdownAgent.java')
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown sources failed compilation.'}
$manifest041=Join-Path $classes041 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest041,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm $jar041 $manifest041 -C $classes041 .
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown helper packaging failed.'}
Get-FileHash $jar041
