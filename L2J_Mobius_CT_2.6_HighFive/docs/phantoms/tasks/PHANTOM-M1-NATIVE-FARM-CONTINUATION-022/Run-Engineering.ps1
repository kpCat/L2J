param()
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$classpath="$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
$java='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/java.exe'
$encoding=@('-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8')
$failures=[Collections.Generic.List[string]]::new()
Push-Location (Join-Path $module 'dist/game')
try {
    foreach ($suite in @('PhantomNativeEvidenceContinuation022Suite','PhantomAutoPlayOwnership022Suite','PhantomNativeFarmContinuation022Suite','PhantomVisibleIntentRecoverySuite','PhantomLocalFarmRecoverySuite','PhantomVisibleDecisionAdmissionSuite','PhantomNativeContextHandoffSuite')) {
        $log=Join-Path $PSScriptRoot "GREEN-$suite.log"
        & $java -Xmx4096m @encoding "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath "org.l2jmobius.tests.phantoms.$suite" $module "$root/build/phantom-test/reports" *> $log
        $result=$LASTEXITCODE; Get-Content $log -Tail 2
        if ($result -ne 0) { $failures.Add($suite); if ($suite -eq 'PhantomNativeFarmContinuation022Suite') { throw 'Golden native smoke failed; preserve TEST evidence.' } }
    }
    foreach ($mode in @('population-ecology-goal033','population-ecology-handoff-regression','background-lifecycle','decision-core','decision-persistence')) {
        $seed=if ($mode.StartsWith('population-ecology')) { 33003300 } elseif ($mode -eq 'background-lifecycle') { 15001501 } else { 20002001 }
        $log=Join-Path $PSScriptRoot "$mode-GREEN.log"
        & $java -Xmx4096m @encoding "-Dphantom.module.root=$module" "-Dphantom.test.reports=$root/build/phantom-test/reports" "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher $mode $seed *> $log
        $result=$LASTEXITCODE; Get-Content $log -Tail 2
        if ($result -ne 0) { $failures.Add($mode) }
    }
    foreach ($focus in @('ownership','native-phase','closure','loot')) {
        $log=Join-Path $PSScriptRoot "native-$focus-GREEN.log"
        & $java -Xmx4096m @encoding "-Dphantom.m1.native.focus=$focus" "-Dphantom.module.root=$module" "-Dphantom.test.reports=$root/build/phantom-test/reports" "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher m1-native-lifecycle 15001501 *> $log
        $result=$LASTEXITCODE; Get-Content $log -Tail 2
        if ($result -ne 0) { $failures.Add("native-$focus") }
    }
    & $java @encoding -cp $classpath org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite *> (Join-Path $PSScriptRoot 'recorder-GREEN.log')
    if ($LASTEXITCODE -ne 0) { $failures.Add('recorder') }
    Get-Content (Join-Path $PSScriptRoot 'recorder-GREEN.log') -Tail 2
    if ($failures.Count -gt 0) { throw ('Failed suites: '+($failures -join ',')) }
} finally { Pop-Location }
