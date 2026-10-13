param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!(Test-Path (Join-Path $module042 'build.xml')) -or !(Test-Path (Join-Path $PSScriptRoot 'Control032.ps1'))) {throw 'Stage exact tracked042 sources in module/.phantom-local/ops042 first.'}
$classes042=Join-Path $PSScriptRoot 'stock-shutdown-classes042'
$jar042=Join-Path $PSScriptRoot 'stock-shutdown042.jar'
if ((Test-Path $classes042) -or (Test-Path $jar042)) {throw 'New own042 shutdown build output required.'}
New-Item -ItemType Directory -Path $classes042 | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp (Join-Path $module042 'dist/libs/*') -d $classes042 (Join-Path $PSScriptRoot 'RequestShutdown.java') (Join-Path $PSScriptRoot 'RuntimeShutdownAgent.java')
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown sources failed compilation.'}
$manifest042=Join-Path $classes042 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest042,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm $jar042 $manifest042 -C $classes042 .
if ($LASTEXITCODE -ne 0) {throw 'Tracked stock-shutdown helper packaging failed.'}
Get-FileHash $jar042
