[CmdletBinding()]
param([Parameter(Mandatory)][string]$BuildRoot,
      [Parameter(Mandatory)][ValidateSet('PhantomSupply072JourneyNative')][string]$Check,
      [ValidateSet('preflight','prepare','continue','resume','cp-purchase','cp-retained','pvp','')][string]$Mode='',
      [string]$HelperRoot='',
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task072Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$task=Join-Path $repo '.phantom-local/task072'
$native=Join-Path $task 'native-test/game'
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
if((Split-Path $build -Parent) -ine $task){throw 'OWN072 build path required.'}
foreach($path in @($build,$native)){Assert-Plain072 $path}
if((Read-Field072 (Join-Path $task 'ownership072.properties') 'owner') -cne 'TASK072_SUSTAINABLE_LIFE'){throw 'Own072 owner differs.'}
$java=(Get-Command java.exe -ErrorAction Stop).Source
$deps=@(Get-ChildItem -LiteralPath (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Where-Object Name -notin @('GameServer.jar','LoginServer.jar') | ForEach-Object FullName)
$classpaths=@((Join-Path $build 'dist/libs/GameServer.jar'),(Join-Path $build 'phantom-test/bin'))+$deps
if(!(Test-Path -LiteralPath $classpaths[0])){$classpaths[0]=Join-Path $build 'bin'}
if($HelperRoot){
    $helper=[IO.Path]::GetFullPath($HelperRoot).TrimEnd('\')
    if((Split-Path $helper -Parent) -ine $task -or (Split-Path $helper -Leaf) -cnotmatch '^helpers-[a-z0-9-]+$'){throw 'OWN072 exact helper root required.'}
    $proof=Read-Json072 (Join-Path $helper 'HELPERS072.json')
    if($proof.owner -cne 'TASK072_SUSTAINABLE_LIFE' -or $proof.sourceState -cne 'COMMITTED' -or $proof.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or (Split-Path $proof.sourceArchivePath -Parent) -ine $task -or (Get-FileHash -LiteralPath $proof.sourceArchivePath -Algorithm SHA256).Hash -ine $proof.sourceArchiveSha256 -or (Get-TreeHash072 $helper @('bin')) -ine $proof.classTreeSha256 -or (Get-FileHash -LiteralPath $classpaths[0] -Algorithm SHA256).Hash -ine $proof.gameJarSha256){throw 'OWN072 committed helper provenance differs.'}
    $classpaths=@($classpaths[0],(Join-Path $helper 'bin'))+$classpaths[1..($classpaths.Count-1)]
}
$log=Join-Path $task "reports/$Label.log"
if(Test-Path -LiteralPath $log){throw 'Use a new evidence label; no overwrite.'}
$parameters=@('-Xms512m','-Xmx4g','-Dfile.encoding=UTF-8','-Dphantom.task072.native=true',"-Dphantom.module.root=$module","-Dphantom.test.config=$native/config/Database.ini",'-cp',($classpaths -join ';'),"org.l2jmobius.tests.phantoms.$Check")
if($Mode){$parameters+= $Mode}
Push-Location $native
$previousErrorAction072=$ErrorActionPreference
try{ $ErrorActionPreference='Continue'; & $java @parameters *> $log; $code=$LASTEXITCODE }
finally{ $ErrorActionPreference=$previousErrorAction072; Pop-Location }
Get-Content -LiteralPath $log -Tail 18
Write-Output "OWN072_NATIVE_EXIT=$code; log=$log"
exit $code
