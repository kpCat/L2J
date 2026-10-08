[CmdletBinding()]
param([ValidateSet('Build','Test','Matrix','Helpers')][string]$Action='Build',
      [string]$Suite='LocalPlayContinuity028Suite',[string]$Focus='',
      [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label='R1', [string]$LauncherId='')
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$ops=Join-Path $module '.phantom-local/ops028'
$lane=Join-Path $module '.phantom-local/contract028a'
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
if($Action -ceq 'Helpers'){
    & python -m unittest discover -s (Join-Path $PSScriptRoot 'proposals') -p test_tools028.py -v
    exit $LASTEXITCODE
}
if($Action -ceq 'Build'){
    & ant compile-tests
    if($LASTEXITCODE -ne 0){throw 'Own compile-tests failed.'}
    # Same accepted TEST guard, adapted only to exact own028 paths/authority. Original remains read-only.
    $source=Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java'
    $generated=Join-Path $ops 'test-lane-source/PhantomHeadlessPlayerTestEnvironment.java'
    $classes=Join-Path $ops 'test-lane-classes'
    New-Item -ItemType Directory -Path (Split-Path $generated -Parent),$classes -Force | Out-Null
    [IO.File]::WriteAllText($generated,[IO.File]::ReadAllText($source).Replace('027','028'),[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp "$root/build/bin;$root/build/phantom-test/bin;$module/dist/libs/*" -d $classes $generated
    if($LASTEXITCODE -ne 0){throw 'Exact private test-lane guard compilation failed.'}
    [ordered]@{sourceSha256=(Get-FileHash $source).Hash;generatedSha256=(Get-FileHash $generated).Hash;adaptation='027 to 028 paths/manifest/authority only; no admission negatives removed'} | ConvertTo-Json | Set-Content (Join-Path $ops 'test-lane-provenance.json') -Encoding utf8
    return
}
if($Action -ceq 'Matrix'){
    $routes=@(
        @('PhantomCheckpointRecovery025Suite','',''), @('PhantomCheckpointDrain025Suite','',''),
        @('PhantomVisibleIntentRecoverySuite','',''), @('native','review',''),
        @('PhantomLivingWorld023Suite','retaliation024',''), @('','','population-ecology-goal033'),
        @('','','population-ecology-handoff-regression'), @('server-shutdown','',''),
        @('position','owned-store',''), @('PhantomNativeFarmContinuation022Suite','raw',''),
        @('PhantomLivingWorld023Suite','cooperative',''), @('PhantomNativeFarmContinuation022Suite','observation026',''),
        @('PhantomNativeFarmContinuation022Suite','callback026',''), @('PhantomSustainedFarm026Suite','',''),
        @('PhantomSustainedFarm026Suite','resource026',''), @('PhantomSustainedFarm026Suite','generation026',''),
        @('PhantomRecoveryBoundary026Suite','',''), @('PhantomNativeEvidenceContinuation022Suite','',''))
    $expected=@(8,3,10,2,3,30,6,8,1,3,1,2,2,3,2,2,2,4)
    $matrix=Join-Path $PSScriptRoot "evidence/$Label-matrix.tsv"
    if(Test-Path $matrix){throw 'Immutable matrix exists.'}
    "route`tsuite`tfocus`tlauncher`texitCode`tsummary" | Set-Content $matrix -Encoding utf8
    $failures=0
    for($i=0;$i -lt $routes.Count;$i++){
        $r=$routes[$i]; $routeLabel=('{0}_{1:D2}' -f $Label,($i+1))
        & pwsh -NoProfile -File $PSCommandPath -Action Test -Suite $r[0] -Focus $r[1] -LauncherId $r[2] -Label $routeLabel
        $routeExit=$LASTEXITCODE
        $summary=(Select-String -LiteralPath (Join-Path $PSScriptRoot "evidence/$routeLabel.log") -Pattern '^SUMMARY:' | Select-Object -Last 1).Line
        if($summary -notmatch 'total=(\d+) passed=(\d+) failed=(\d+)' -or [int]$Matches[1] -ne $expected[$i]){$summary='INVALID:cardinality '+$summary}
        if($routeExit -ne 0 -or $summary -like 'INVALID*'){$failures++}
        "$($i+1)`t$($r[0])`t$($r[1])`t$($r[2])`t$routeExit`t$summary" | Add-Content $matrix -Encoding utf8
    }
    if($failures){exit 1}; exit 0
}
$owned=Get-Content -LiteralPath (Join-Path $lane 'test/owned.properties') | ConvertFrom-StringData
if($owned.owner -cne 'TASK028_CONTRACT' -or $owned.database -cne 'l2jmobiush5_localplay_contract028a' -or (Get-FileHash (Join-Path $lane 'play-snapshot.sql')).Hash.ToLowerInvariant() -cne $owned.exportSha256){throw 'Exact own TEST authority/export guard failed.'}
$active=& 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' "--defaults-extra-file=$(Join-Path $lane 'secrets/client.cnf')" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='l2jmobiush5_localplay_contract028a'"
if($LASTEXITCODE -ne 0 -or "$active".Trim() -cne '0'){throw 'Own TEST lane already in use.'}
$reports=Join-Path $PSScriptRoot "evidence/$Label"
$log=Join-Path $PSScriptRoot "evidence/$Label.log"
if(Test-Path $log){throw 'Immutable test log exists.'}
$cp="$ops/test-lane-classes;$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
$arguments=@('-Xmx4g','-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8',
    "-Dphantom.test.config=$lane/test/Database.test.ini","-Dphantom.contract028.manifest=$lane/test/owned.properties",
    "-Dphantom.module.root=$module","-Dphantom.test.reports=$reports")
if($Focus){$arguments+="-Dphantom.m1.native.focus=$Focus"}
if($Suite -ceq 'PhantomNativeFarmContinuation022Suite' -and $Focus){$arguments+="-Dphantom023.nativeFarmFocus=$Focus"}
if($Suite -ceq 'position' -and $Focus){$arguments+="-Dphantom.background.position.focus=$Focus"}
$arguments+=@('-cp',$cp)
if($LauncherId){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher',$LauncherId,'33003300')}
elseif($Suite -ceq 'native'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','m1-native-lifecycle','15001501')}
elseif($Suite -ceq 'server-shutdown'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','server-shutdown-handoff','23002301')}
elseif($Suite -ceq 'position'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','background-position-canonicalization','15001502')}
elseif($Suite -ceq 'LocalPlayPilotSuite'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','localplay-pilot','28002801')}
elseif($Suite -ceq 'LocalPlayPilotNativeSuite'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','localplay-pilot-native','28002801')}
else{$arguments+=@("org.l2jmobius.tests.phantoms.$Suite",$module,$reports)}
Push-Location (Join-Path $lane 'runtime/game')
try{ & (Join-Path $jdk 'java.exe') @arguments *> $log; $testExit=$LASTEXITCODE; Get-Content -LiteralPath $log -Tail 14; exit $testExit }
finally{Pop-Location}
