param([ValidateSet('RED','GREEN','REGRESSIONS','TRAVEL')][string]$Phase='GREEN', [string]$Only='', [switch]$Baseline)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$classpath="$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
if ($Baseline) {
    if ($Phase -ne 'RED') { throw 'Baseline is RED only.' }
    $baseRoot='C:/Users/ZBook/.codex/worktrees/m1-visible-decision-020/L2J_Mobius'
    $classpath="$baseRoot/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
}
$encoding=@('-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8')
Push-Location (Join-Path $module 'dist/game')
try {
    $suites=if ($Phase -eq 'TRAVEL') { @() } elseif ($Phase -eq 'REGRESSIONS') { @('PhantomVisibleDecisionAdmissionSuite','PhantomNativeContextHandoffSuite') } else { @('PhantomVisibleIntentRecoverySuite','PhantomLocalFarmRecoverySuite') }
    if ($Only) { $suites=@($suites | Where-Object { $_ -eq $Only }); if ($suites.Count -ne 1) { throw 'Unknown focused suite.' } }
    foreach ($suite in $suites) {
        $log=Join-Path $PSScriptRoot "$Phase-$suite.log"
        & java -Xmx4096m @encoding "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath "org.l2jmobius.tests.phantoms.$suite" $module "$root/build/phantom-test/reports" *> $log
        $result=$LASTEXITCODE
        Get-Content -LiteralPath $log -Tail 14
        if ($Phase -eq 'RED') { if ($result -eq 0 -or !(Select-String -LiteralPath $log -SimpleMatch 'RED:' -Quiet)) { throw 'Semantic RED missing.' } }
        elseif ($result -ne 0) { throw "$suite failed." }
    }
    if ($Phase -in @('REGRESSIONS','TRAVEL')) {
        $modes=if ($Phase -eq 'TRAVEL') { @('normal-gatekeeper-travel','m1-native-lifecycle') } else { @('population-ecology-goal033','population-ecology-handoff-regression','background-lifecycle','decision-core','decision-persistence','normal-gatekeeper-travel','m1-native-lifecycle') }
        foreach ($mode in $modes) {
            $seed=if ($mode.StartsWith('population-ecology')) { 33003300 } elseif ($mode -in @('background-lifecycle','m1-native-lifecycle')) { 15001501 } else { 20002001 }
            $log=Join-Path $PSScriptRoot "$mode-GREEN.log"
            & java -Xmx4096m @encoding '-Dphantom.m1.native.focus=travel' "-Dphantom.module.root=$module" "-Dphantom.test.reports=$root/build/phantom-test/reports" "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher $mode $seed *> $log
            $result=$LASTEXITCODE; Get-Content -LiteralPath $log -Tail 3
            if ($result -ne 0) { throw "$mode failed." }
        }
        & java @encoding -cp $classpath org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite *> (Join-Path $PSScriptRoot 'recorder-GREEN.log')
        if ($LASTEXITCODE -ne 0) { throw 'Recorder failed.' }
        Get-Content -LiteralPath (Join-Path $PSScriptRoot 'recorder-GREEN.log') -Tail 3
    }
} finally { Pop-Location }
