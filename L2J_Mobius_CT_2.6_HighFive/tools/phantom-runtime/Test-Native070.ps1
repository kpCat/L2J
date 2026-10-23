[CmdletBinding()]
param([Parameter(Mandatory)][string]$BuildRoot,
      [Parameter(Mandatory)][ValidateSet('PhantomEconomy070Native','PhantomEconomy070RecoveryNative','PhantomEconomy070QuestNative','PhantomEconomy070CurseNative','PhantomEconomy070BoardNative')][string]$Check,
      [ValidateSet('prepare','verify','resume','inspect','next','')][string]$Mode='',
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label,
      [switch]$DevOverlay)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task070Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$task=Join-Path $repo '.phantom-local/task070'
$native=Join-Path $task 'native-test/game'
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
if((Split-Path $build -Parent) -ine $task){throw 'OWN070 build path required.'}
foreach($path in @($build,$native)){Assert-Plain070 $path}
if((Read-Field070 (Join-Path $task 'ownership070.properties') 'owner') -cne 'TASK070_ECONOMY_QOL'){throw 'Own070 owner differs.'}
$java=(Get-Command java.exe -ErrorAction Stop).Source
$deps=@(Get-ChildItem -LiteralPath (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Where-Object Name -notin @('GameServer.jar','LoginServer.jar') | ForEach-Object FullName)
$classpaths=@((Join-Path $build 'dist/libs/GameServer.jar'),(Join-Path $build 'phantom-test/bin'))+$deps
if($DevOverlay){$classpaths=@((Join-Path $task 'dev-overlay'))+$classpaths}
$log=Join-Path $task "reports/$Label.log"
if(Test-Path -LiteralPath $log){throw 'Use a new evidence label; no overwrite.'}
$parameters=@('-Dfile.encoding=UTF-8','-Dphantom.task070.native=true',"-Dphantom.module.root=$module","-Dphantom.test.config=$native/config/Database.ini",'-cp',($classpaths -join ';'),"org.l2jmobius.tests.phantoms.$Check")
if($Mode){$parameters+= $Mode}
Push-Location $native
try{ & $java @parameters *> $log; $code=$LASTEXITCODE }
finally{ Pop-Location }
Get-Content -LiteralPath $log -Tail 18
Write-Output "OWN070_NATIVE_EXIT=$code; log=$log"
exit $code
