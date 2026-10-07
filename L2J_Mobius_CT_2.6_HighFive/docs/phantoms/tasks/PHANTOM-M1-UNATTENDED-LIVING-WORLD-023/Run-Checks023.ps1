[CmdletBinding()]
param([string]$Suite='PhantomLivingWorld023Suite',[string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$reports023=Join-Path $PSScriptRoot "evidence/$Label"
$cp="$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
if($Engine -ceq 'Base'){
    $baseRuntime=Join-Path $module '.phantom-local/night023a/runtime'
    $baseManifest=Get-Content (Join-Path $baseRuntime 'local-play.json') -Raw | ConvertFrom-Json
    $baseJar=Join-Path $baseRuntime 'libs/GameServer.jar'
    if($baseManifest.codeSha -cne 'fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6' -or (Get-FileHash $baseJar).Hash -cne $baseManifest.gameJarSha256){throw 'Required unchanged-base JAR identity mismatch.'}
    $cp="$baseJar;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$baseRuntime/libs/*"
    "engine=UNCHANGED_REQUIRED_BASE;jarSha256=$($baseManifest.gameJarSha256);sameGuardedFixture=true" | Set-Content (Join-Path $PSScriptRoot "$Label-engine.txt") -Encoding utf8
}
$args023=@('-Xmx4g','-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8',
    "-Dphantom.test.config=$module/.phantom-local/Database.test.ini","-Dphantom.module.root=$module",
    "-Dphantom.test.reports=$reports023")
if($Focus){$args023+="-Dphantom.m1.native.focus=$Focus"}
if($Suite -ceq 'PhantomNativeFarmContinuation022Suite' -and $Focus){$args023+="-Dphantom023.nativeFarmFocus=$Focus"}
$args023+=@('-cp',$cp)
if($Suite -ceq 'native'){$args023+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','m1-native-lifecycle','15001501')}
elseif($Suite -ceq 'server-shutdown'){$args023+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','server-shutdown-handoff','23002301')}
else{$args023+=@("org.l2jmobius.tests.phantoms.$Suite",$module,$reports023)}
$log=Join-Path $PSScriptRoot "$Label.log"
Push-Location (Join-Path $module 'dist/game')
try { & java @args023 *> $log; $exit023=$LASTEXITCODE; Get-Content -LiteralPath $log -Tail 12; exit $exit023 }
finally { Pop-Location }
