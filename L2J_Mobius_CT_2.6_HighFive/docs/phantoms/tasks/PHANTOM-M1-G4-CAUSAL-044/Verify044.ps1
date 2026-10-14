[CmdletBinding()]
param([ValidateSet('Build','Test','Matrix','Helpers','Evaluate')][string]$Action='Build',
      [string]$Suite='LocalPlayContinuity028Suite',[string]$Focus='',
      [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label='R1', [string]$LauncherId='', [long]$LauncherSeed=33003300, [string]$EpisodeOutput='', [string]$JarPath='', [switch]$Development,
      [string]$TestLaneRoot='', [string]$BuildRoot='', [string]$ReportRoot='', [string]$JavaHome='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot')
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$lane=if($TestLaneRoot){[IO.Path]::GetFullPath($TestLaneRoot)}else{Join-Path $module '.phantom-local/contract044a'}
$build044=if($BuildRoot){[IO.Path]::GetFullPath($BuildRoot)}else{Join-Path $module '.phantom-local/ops044/build'}
$evidence044=if($ReportRoot){[IO.Path]::GetFullPath($ReportRoot)}else{Join-Path $PSScriptRoot 'evidence'}
$jdk=Join-Path $JavaHome 'bin'
New-Item -ItemType Directory -Path $evidence044 -Force | Out-Null
if($Action -ceq 'Evaluate'){
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    $EpisodeOutput=[IO.Path]::GetFullPath($EpisodeOutput)
    if(-not $EpisodeOutput.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact own captured episode required.'}
    & python (Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py') $EpisodeOutput --output (Join-Path $EpisodeOutput 'continuity-v2.json') *> (Join-Path $EpisodeOutput 'continuity-evaluation.log')
    if($LASTEXITCODE -ne 0){throw 'Continuity evaluator input invalid.'}
    $samples=@((Get-Content (Join-Path $EpisodeOutput 'all-samples.json') -Raw | ConvertFrom-Json))
    $baseline=@((Get-Content (Join-Path $EpisodeOutput 'baseline-cohort.json') -Raw | ConvertFrom-Json))
    $primary=@((Get-Content (Join-Path $EpisodeOutput 'primary.json') -Raw | ConvertFrom-Json))
    if(Test-Path (Join-Path $EpisodeOutput 'legacy-result.json')){throw 'Immutable legacy result exists.'}
    if($baseline.Count -ne 8){
        @{contract='LEGACY_STRICT_FARM';count=$baseline.Count;verdict='NOT_APPLICABLE_COUNT';pass=$false} | ConvertTo-Json | Set-Content (Join-Path $EpisodeOutput 'legacy-result.json') -Encoding utf8
        return
    }
    $watch=[pscustomobject]@{Elapsed=[pscustomobject]@{TotalSeconds=[double]$samples[-1].elapsedSeconds}}
    $final=@($samples[-1].actors)
    # Exact evaluator extraction and the two existing027 full-observe guards; no thresholds are changed.
    $source=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Observe-Scene024.ps1'
    $text=[IO.File]::ReadAllText($source); $start=$text.IndexOf('    $tail=@($samples'); $end=$text.IndexOf('    $rows | ConvertTo-Json',$start)
    if($start -lt 0 -or $end -le $start){throw 'Legacy evaluator source shape changed.'}
    $legacy=$text.Substring($start,$end-$start)
    $legacy=$legacy.Replace('if($seen.Count -ne 1){$missing++; continue}', 'if($seen.Count -ne 1 -or $seen[0].missing -ceq ''true'' -or $seen[0].worldPresent -cne ''true''){$missing++; continue}')
    $legacy=$legacy.Replace('$pass=$same -and $missing -eq 0', '$pass=$same -and $last[0].dead -ceq ''false'' -and $last[0].worldPresent -ceq ''true'' -and $missing -eq 0')
    . ([scriptblock]::Create($legacy))
    $rows | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $EpisodeOutput 'legacy-cohort-result.json') -Encoding utf8
    @{contract='LEGACY_STRICT_FARM';sourceSha256=(Get-FileHash $source).Hash;count=8;passingRows=@($rows | Where-Object pass).Count;pass=(@($rows | Where-Object {-not $_.pass}).Count -eq 0)} | ConvertTo-Json | Set-Content (Join-Path $EpisodeOutput 'legacy-result.json') -Encoding utf8
    return
}
if($Action -ceq 'Helpers'){
    $helpers=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals'
    if(-not (Test-Path (Join-Path $helpers 'test_tools028.py'))){throw 'Existing028 helper tests absent.'}
    & python -B -m unittest discover -s $helpers -p test_tools028.py -v
    exit $LASTEXITCODE
}
if($Action -ceq 'Build'){
    & 'C:/Tools/apache-ant-1.10.17/bin/ant.bat' -f (Join-Path $module 'build.xml') "-Dbuild=$module/.phantom-local/ops044/build" compile-tests
    if($LASTEXITCODE -ne 0){throw 'Own compile-tests failed.'}
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
    $expected=@(8,3,10,2,3,30,6,8,1,3,1,2,2,3,2,2,2,5)
    $matrix=Join-Path $evidence044 "$Label-matrix.tsv"
    if(Test-Path $matrix){throw 'Immutable matrix exists.'}
    "route`tsuite`tfocus`tlauncher`texitCode`tsummary" | Set-Content $matrix -Encoding utf8
    $failures=0
    for($i=0;$i -lt $routes.Count;$i++){
        $r=$routes[$i]; $routeLabel=('{0}_{1:D2}' -f $Label,($i+1))
        $routeArgs=@('-NoProfile','-File',$PSCommandPath,'-Action','Test','-Label',$routeLabel)
        if($r[0]){$routeArgs+=@('-Suite',$r[0])}; if($r[1]){$routeArgs+=@('-Focus',$r[1])}; if($r[2]){$routeArgs+=@('-LauncherId',$r[2])}; if($JarPath){$routeArgs+=@('-JarPath',$JarPath)}
        $routeArgs+=@('-TestLaneRoot',$lane,'-BuildRoot',$build044,'-ReportRoot',$evidence044,'-JavaHome',$JavaHome)
        & powershell.exe @routeArgs
        $routeExit=$LASTEXITCODE
        $summary=(Select-String -LiteralPath (Join-Path $evidence044 "$routeLabel.log") -Pattern '^SUMMARY:' | Select-Object -Last 1).Line
        if($summary -notmatch 'total=(\d+) passed=(\d+) failed=(\d+)' -or [int]$Matches[1] -ne $expected[$i]){$summary='INVALID:cardinality '+$summary}
        if($routeExit -ne 0 -or $summary -like 'INVALID*'){$failures++}
        "$($i+1)`t$($r[0])`t$($r[1])`t$($r[2])`t$routeExit`t$summary" | Add-Content $matrix -Encoding utf8
    }
    if($failures){exit 1}; exit 0
}
$laneLock044=[IO.File]::Open((Join-Path $lane 'test-lane044t.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
$owned=Get-Content -LiteralPath (Join-Path $lane 'test/owned.properties') | ConvertFrom-StringData
if($owned.owner -cne 'TASK044_CONTRACT' -or $owned.database -cne 'l2jmobiush5_phantom_test_044a' -or (Get-FileHash (Join-Path $lane 'play-snapshot.sql')).Hash.ToLowerInvariant() -cne $owned.exportSha256){throw 'Exact own TEST authority/export guard failed.'}
$active=& 'C:/Program Files/MariaDB 11.4/bin/mariadb.exe' "--defaults-extra-file=$(Join-Path $lane 'secrets/client.cnf')" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='l2jmobiush5_phantom_test_044a'"
if($LASTEXITCODE -ne 0 -or "$active".Trim() -cne '0'){throw 'Own TEST lane already in use.'}
$reports=Join-Path $evidence044 $Label
$log=Join-Path $evidence044 "$Label.log"
if(Test-Path $log){throw 'Immutable test log exists.'}
$selectedJar=if($JarPath){[IO.Path]::GetFullPath($JarPath)}else{Join-Path $module 'dist/libs/GameServer.jar'}
$jarHash=(Get-FileHash -LiteralPath $selectedJar).Hash
$jarRoot=Join-Path $build044 "jar-input/$jarHash"
New-Item -ItemType Directory -Path $jarRoot -Force | Out-Null
$frozenJar=Join-Path $jarRoot 'GameServer.jar'
if(-not (Test-Path -LiteralPath $frozenJar)){Copy-Item -LiteralPath $selectedJar -Destination $frozenJar}
if((Get-FileHash -LiteralPath $frozenJar).Hash -cne $jarHash){throw 'Immutable test Jar hash mismatch.'}
@{jarSha256=$jarHash;jar=$frozenJar;suite=$Suite;focus=$Focus;label=$Label;database=$owned.database;developmentBinFirst=[bool]$Development;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence044 "$Label.provenance.json") -Encoding utf8
$cp="$frozenJar;$build044/phantom-test/bin;$build044/phantom-test/resources;$module/dist/libs/*"
if($Development){$cp="$build044/bin;$cp"; Write-Output 'DEVELOPMENT_BIN_FIRST_NOT_FINAL_JAR_PROOF'}
$arguments=@('-Xmx6g','-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8',
    "-Dphantom.test.config=$lane/test/Database.test.ini","-Dphantom.contract044.manifest=$lane/test/owned.properties",
    "-Dphantom.module.root=$module","-Dphantom.test.reports=$reports")
if($Focus -and $Focus -cne 'capacity031'){$arguments+="-Dphantom.m1.native.focus=$Focus"}
if($Suite -ceq 'PhantomActionLoop038Suite' -and $Focus -cin @('full038','caster040')){$arguments+='-Dphantom.test.minimumFestivalStartMillis=600000'}
if($Suite -ceq 'PhantomFarmProjection030Suite' -and $Focus -ceq 'capacity031'){$arguments+='-Dphantom.contract031.capacity=true'}
if($Suite -ceq 'PhantomNativeFarmContinuation022Suite' -and $Focus){$arguments+="-Dphantom023.nativeFarmFocus=$Focus"}
if($Suite -ceq 'position' -and $Focus){$arguments+="-Dphantom.background.position.focus=$Focus"}
$arguments+=@('-cp',$cp)
if($LauncherId){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher',$LauncherId,[string]$LauncherSeed)}
elseif($Suite -ceq 'native'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','m1-native-lifecycle','15001501')}
elseif($Suite -ceq 'server-shutdown'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','server-shutdown-handoff','23002301')}
elseif($Suite -ceq 'position'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','background-position-canonicalization','15001502')}
elseif($Suite -ceq 'LocalPlayPilotSuite'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','localplay-pilot','28002801')}
elseif($Suite -ceq 'LocalPlayPilotNativeSuite'){$arguments+=@('org.l2jmobius.tests.phantoms.PhantomTestLauncher','localplay-pilot-native','28002801')}
else{$arguments+=@("org.l2jmobius.tests.phantoms.$Suite",$module,$reports)}
Push-Location (Join-Path $lane 'runtime/game')
try{ $ErrorActionPreference='Continue'; & (Join-Path $jdk 'java.exe') @arguments *> $log; $testExit=$LASTEXITCODE; $ErrorActionPreference='Stop'; Get-Content -LiteralPath $log -Tail 14; exit $testExit }
finally{Pop-Location}
} finally {$laneLock044.Dispose()}
