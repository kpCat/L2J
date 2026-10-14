param([Parameter(Mandatory)][long]$ProfileId,[Parameter(Mandatory)][string]$OutputFile)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime=[IO.Path]::GetFullPath((Join-Path $module '.phantom-local/contract044a/runtime'))
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
. (Join-Path $runtime 'LocalPlay-Ownership.ps1')
$output=[IO.Path]::GetFullPath($OutputFile)
$allowed=[IO.Path]::GetFullPath((Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044/evidence'))+[IO.Path]::DirectorySeparatorChar
if($ProfileId -le 0 -or -not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $output)){throw 'New exact044 one-profile output required'}
$state=Get-LocalPlayRoleState $runtime 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))
if($state.state -cne 'RUNNING' -or -not $state.recordVerified){throw 'Exact own live Game incarnation required; no attach'}
$runtimeId=Get-LocalPlayRuntimeId $runtime
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
$classes=Join-Path $PSScriptRoot 'signal-snapshot-fixed-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module 'dist/libs/*') -d $classes (Join-Path $PSScriptRoot 'SignalSnapshot044.java')
if($LASTEXITCODE -ne 0){throw 'Passive snapshot compile failed'}
$manifest=Join-Path $classes 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest,"Manifest-Version: 1.0`nAgent-Class: SignalSnapshot044`n`n",[Text.Encoding]::ASCII)
$jar=Join-Path $PSScriptRoot 'signal-snapshot-fixed044.jar'
& (Join-Path $jdk 'jar.exe') cfm $jar $manifest -C $classes .
if($LASTEXITCODE -ne 0){throw 'Passive snapshot packaging failed'}
& (Join-Path $jdk 'java.exe') --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'observer-classes') AttachContract024 $state.pid $jar ($runtimeId+"`n"+$runtime+"`n"+$ProfileId+"`n"+$output)
if($LASTEXITCODE -ne 0 -or -not (Test-Path $output)){throw 'Passive snapshot did not produce output'}
Write-Output 'ONE_PROFILE_PASSIVE_SNAPSHOT_WRITTEN; native facts NONATOMIC, no away/G4 verdict inferred'
