[CmdletBinding()]
param([Parameter(Mandatory)][string]$BuildRoot,
      [Parameter(Mandatory)][ValidateSet('PhantomSupply073JourneyNative')][string]$Check,
      [ValidateSet('preflight','prepare','resume','recover','history','history-resume','inspect','')][string]$Mode='',
      [string]$HelperRoot='',
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$task=Join-Path $repo '.phantom-local/task073'
$native=Join-Path $task 'native-test/game'
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
if((Split-Path $build -Parent) -ine $task){throw 'OWN073 build path required.'}
foreach($path in @($build,$native)){Assert-Plain073 $path}
if((Read-Field073 (Join-Path $task 'ownership073.properties') 'owner') -cne 'TASK073_EARNED_LIFE'){throw 'Own073 owner differs.'}
$java=(Get-Command java.exe -ErrorAction Stop).Source
$deps=@(Get-ChildItem -LiteralPath (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Where-Object Name -notin @('GameServer.jar','LoginServer.jar') | ForEach-Object FullName)
$classpaths=@((Join-Path $build 'dist/libs/GameServer.jar'),(Join-Path $build 'phantom-test/bin'))+$deps
if(!(Test-Path -LiteralPath $classpaths[0])){$classpaths[0]=Join-Path $build 'bin'}
if($Mode -cne 'preflight'){
    $receipt073=Read-Json073 (Join-Path $build 'BUILD_INFO073.json')
    if($receipt073.owner -cne 'TASK073_EARNED_LIFE' -or $receipt073.sourceState -cne 'COMMITTED' -or $receipt073.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or [IO.Path]::GetFullPath($receipt073.buildRoot).TrimEnd('\') -ine $build -or (Get-FileHash -LiteralPath $classpaths[0] -Algorithm SHA256).Hash -ine $receipt073.gameJarSha256 -or (Get-TreeHash073 (Join-Path $build 'phantom-test') @('bin','resources')) -ine $receipt073.testTreeSha256){throw 'OWN073 measured acceptance requires exact committed whole JAR and test tree.'}
}
if($HelperRoot){
    $helper=[IO.Path]::GetFullPath($HelperRoot).TrimEnd('\')
    if((Split-Path $helper -Parent) -ine $task -or (Split-Path $helper -Leaf) -cnotmatch '^helpers-[a-z0-9-]+$'){throw 'OWN073 exact helper root required.'}
    $proof=Read-Json073 (Join-Path $helper 'HELPERS073.json')
    if($proof.owner -cne 'TASK073_EARNED_LIFE' -or $proof.sourceState -cne 'COMMITTED' -or $proof.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or (Split-Path $proof.sourceArchivePath -Parent) -ine $task -or (Get-FileHash -LiteralPath $proof.sourceArchivePath -Algorithm SHA256).Hash -ine $proof.sourceArchiveSha256 -or (Get-TreeHash073 $helper @('bin')) -ine $proof.classTreeSha256 -or (Get-FileHash -LiteralPath $classpaths[0] -Algorithm SHA256).Hash -ine $proof.gameJarSha256){throw 'OWN073 committed helper provenance differs.'}
    $classpaths=@($classpaths[0],(Join-Path $helper 'bin'))+$classpaths[1..($classpaths.Count-1)]
}
$log=Join-Path $task "reports/$Label.log"
if(Test-Path -LiteralPath $log){throw 'Use a new evidence label; no overwrite.'}
$parameters=@('-Xms512m','-Xmx4g','-Dfile.encoding=UTF-8','-Dphantom.task073.native=true',"-Dphantom.module.root=$module","-Dphantom.test.config=$native/config/Database.ini",'-cp',($classpaths -join ';'),"org.l2jmobius.tests.phantoms.$Check")
if($Mode){$parameters+= $Mode}
Push-Location $native
$previousErrorAction073=$ErrorActionPreference
try{ $ErrorActionPreference='Continue'; & $java @parameters *> $log; $code=$LASTEXITCODE }
finally{ $ErrorActionPreference=$previousErrorAction073; Pop-Location }
Get-Content -LiteralPath $log -Tail 18
Write-Output "OWN073_NATIVE_EXIT=$code; log=$log"
exit $code
