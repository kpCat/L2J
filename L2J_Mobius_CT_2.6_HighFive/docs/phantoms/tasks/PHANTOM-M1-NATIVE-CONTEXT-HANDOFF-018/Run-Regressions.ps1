param([switch]$Native)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root = Split-Path $module -Parent
$classpath = "$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
Push-Location (Join-Path $module 'dist/game')
try {
    $modes = if ($Native) { @('background-lifecycle','m1-native-lifecycle') } else { @('population-ecology-goal033','population-ecology-handoff-regression') }
    foreach ($mode in $modes) {
        $seed = if ($Native) { 15001501 } else { 33003300 }
        & java -Xmx4096m "-Dphantom.module.root=$module" "-Dphantom.test.reports=$root/build/phantom-test/reports" "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" '-Dphantom.m1.native.focus=native-context-contract' -cp $classpath org.l2jmobius.tests.phantoms.PhantomTestLauncher $mode $seed *> (Join-Path $PSScriptRoot "$mode-GREEN.log")
        $result = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $PSScriptRoot "$mode-GREEN.log") -Tail 5
        if ($result -ne 0) { throw "Regression failed: $mode exit=$result" }
    }
    if (!$Native) {
        & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite *> (Join-Path $PSScriptRoot 'recorder-GREEN.log')
        $result = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $PSScriptRoot 'recorder-GREEN.log') -Tail 5
        if ($result -ne 0) { throw 'Recorder regression failed.' }
    }
} finally { Pop-Location }
