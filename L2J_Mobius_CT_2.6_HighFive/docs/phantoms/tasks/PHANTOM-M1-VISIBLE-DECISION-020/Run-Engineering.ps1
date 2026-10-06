param([ValidateSet('RED','GREEN','REGRESSIONS')][string]$Phase='GREEN', [switch]$SkipHandoff)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$classpath="$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
$encoding=@('-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8')
Push-Location (Join-Path $module 'dist/game')
try {
    if ($Phase -eq 'REGRESSIONS') {
        if (!$SkipHandoff) {
        & java -Xmx4096m @encoding "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomNativeContextHandoffSuite $module "$root/build/phantom-test/reports" *> (Join-Path $PSScriptRoot 'TASK018-GREEN.log')
        Get-Content (Join-Path $PSScriptRoot 'TASK018-GREEN.log') -Tail 3
        if ($LASTEXITCODE -ne 0) { throw 'TASK018 regression failed.' }
        }
        foreach ($mode in @('population-ecology-goal033','population-ecology-handoff-regression','background-lifecycle','decision-core','decision-persistence')) {
            $seed=if ($mode.StartsWith('population-ecology')) { 33003300 } elseif ($mode -eq 'background-lifecycle') { 15001501 } else { 20002001 }
            & java -Xmx4096m @encoding "-Dphantom.module.root=$module" "-Dphantom.test.reports=$root/build/phantom-test/reports" "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher $mode $seed *> (Join-Path $PSScriptRoot "$mode-GREEN.log")
            Get-Content (Join-Path $PSScriptRoot "$mode-GREEN.log") -Tail 3
            if ($LASTEXITCODE -ne 0) { throw "$mode regression failed." }
        }
        & java @encoding -cp $classpath org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite *> (Join-Path $PSScriptRoot 'recorder-GREEN.log')
        Get-Content (Join-Path $PSScriptRoot 'recorder-GREEN.log') -Tail 3
        if ($LASTEXITCODE -ne 0) { throw 'recorder regression failed.' }
    } else {
        & java -Xmx4096m @encoding "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomVisibleDecisionAdmissionSuite $module "$root/build/phantom-test/reports" *> (Join-Path $PSScriptRoot "$Phase.log")
        $result=$LASTEXITCODE
        Get-Content (Join-Path $PSScriptRoot "$Phase.log") -Tail 20
        if ($Phase -eq 'RED') {
            if ($result -eq 0 -or !(Select-String (Join-Path $PSScriptRoot 'RED.log') -SimpleMatch 'RED: successful exact foreground handoff' -Quiet)) { throw 'Genuine semantic RED absent.' }
        } elseif ($result -ne 0) { throw 'Admission contract failed.' }
    }
} finally { Pop-Location }
