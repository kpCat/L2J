[CmdletBinding()]
param([Parameter(Mandatory)][string]$BuildRoot,
      [Parameter(Mandatory)][ValidateSet('PhantomLivingCombat071Native','PhantomSupply071JourneyNative')][string]$Check,
      [ValidateSet('prepare','verify','resume','inspect','combat','combat-resume','combat-roles','combat-remaining','grade-resume','reserve','mammon','')][string]$Mode='',
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task071Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$task=Join-Path $repo '.phantom-local/task071'
$native=Join-Path $task 'native-test/game'
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
if((Split-Path $build -Parent) -ine $task){throw 'OWN071 build path required.'}
foreach($path in @($build,$native)){Assert-Plain071 $path}
if((Read-Field071 (Join-Path $task 'ownership071.properties') 'owner') -cne 'TASK071_LIVING_COMBAT'){throw 'Own071 owner differs.'}
$java=(Get-Command java.exe -ErrorAction Stop).Source
$deps=@(Get-ChildItem -LiteralPath (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Where-Object Name -notin @('GameServer.jar','LoginServer.jar') | ForEach-Object FullName)
$classpaths=@((Join-Path $build 'dist/libs/GameServer.jar'),(Join-Path $build 'phantom-test/bin'))+$deps
if(!(Test-Path -LiteralPath $classpaths[0])){$classpaths[0]=Join-Path $build 'bin'}
$log=Join-Path $task "reports/$Label.log"
if(Test-Path -LiteralPath $log){throw 'Use a new evidence label; no overwrite.'}
$parameters=@('-Xms512m','-Xmx4g','-Dfile.encoding=UTF-8','-Dphantom.task071.native=true',"-Dphantom.module.root=$module","-Dphantom.test.config=$native/config/Database.ini",'-cp',($classpaths -join ';'),"org.l2jmobius.tests.phantoms.$Check")
if($Mode){$parameters+= $Mode}
Push-Location $native
$previousErrorAction071=$ErrorActionPreference
try{ $ErrorActionPreference='Continue'; & $java @parameters *> $log; $code=$LASTEXITCODE }
finally{ $ErrorActionPreference=$previousErrorAction071; Pop-Location }
Get-Content -LiteralPath $log -Tail 18
Write-Output "OWN071_NATIVE_EXIT=$code; log=$log"
exit $code
