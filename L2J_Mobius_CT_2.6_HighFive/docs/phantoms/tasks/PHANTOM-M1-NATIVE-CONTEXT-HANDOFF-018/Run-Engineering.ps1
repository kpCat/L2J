param([ValidateSet('RED','GREEN')][string]$Phase = 'GREEN', [switch]$SkipCompile)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root = Split-Path $module -Parent
$classpath = "$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
if (!$SkipCompile) {
    Push-Location $module
    try {
        & ant -q compile-tests *> (Join-Path $PSScriptRoot "$Phase-COMPILE.log")
        if ($LASTEXITCODE -ne 0) { throw 'compile-tests failed.' }
    } finally { Pop-Location }
}
Push-Location (Join-Path $module 'dist/game')
try {
    & java -Xmx4096m "-Dphantom.test.config=$module/.phantom-local/Database.test.ini" -cp $classpath org.l2jmobius.tests.phantoms.PhantomNativeContextHandoffSuite $module "$root/build/phantom-test/reports" *> (Join-Path $PSScriptRoot "$Phase.log")
    $result = $LASTEXITCODE
    Get-Content -LiteralPath (Join-Path $PSScriptRoot "$Phase.log") -Tail 18
    Write-Output "INTEGRATED_$Phase exit=$result"
    if ($Phase -ceq 'RED') {
        if ($result -eq 0 -or !(Select-String -LiteralPath (Join-Path $PSScriptRoot 'RED.log') -SimpleMatch 'admission=catchup.normal_fenced' -Quiet)) { throw 'Required integrated RED was not observed.' }
    } elseif ($result -ne 0) { throw 'Handoff contracts failed.' }
} finally { Pop-Location }
