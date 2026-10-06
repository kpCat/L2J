param([Parameter(Mandatory)][string]$Module,[Parameter(Mandatory)][ValidateSet('RED','GREEN')][string]$Phase)
$ErrorActionPreference = 'Stop'
$task = Join-Path $Module 'docs/phantoms/tasks/PHANTOM-M1-ONLINE-LOCALITY-TRACE-017'
$root = Split-Path $Module -Parent
$private = Join-Path $Module '.phantom-local/trace-tests-017'
New-Item -ItemType Directory -Path $private -Force | Out-Null
Push-Location $Module
try {
    & ant -q compile-tests *> (Join-Path $task "$Phase-COMPILE.log")
    if ($LASTEXITCODE -ne 0) { throw 'compile-tests failed; inspect compile log.' }
} finally { Pop-Location }
$classpath = "$root/build/bin;$root/build/phantom-test/bin;$root/build/phantom-test/resources;$Module/dist/libs/*"
Push-Location $private
try {
    & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomHumanLocalityTraceSuite $Module "$root/build/phantom-test/reports" *> (Join-Path $task "$Phase.log")
    $result = $LASTEXITCODE
    Write-Output "LOCALITY_$Phase exit=$result"
    Get-Content -LiteralPath (Join-Path $task "$Phase.log") -Tail 16
    if ($Phase -ceq 'RED' -and $result -eq 0) { throw 'Negative control did not reproduce bug.' }
    if ($Phase -ceq 'GREEN' -and $result -ne 0) { throw 'Locality regression failed.' }
} finally { Pop-Location }
if ($Phase -ceq 'GREEN') {
    Push-Location $Module
    try {
        & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomRuntimeFlightRecorderSuite *> (Join-Path $task 'RECORDER_GREEN.log')
        if ($LASTEXITCODE -ne 0) { throw 'Existing recorder suite failed.' }
        Get-Content -LiteralPath (Join-Path $task 'RECORDER_GREEN.log') -Tail 6
        & ant -q jar *> (Join-Path $task 'BUILD.log')
        if ($LASTEXITCODE -ne 0) { throw 'jar failed.' }
        Get-Content -LiteralPath (Join-Path $task 'BUILD.log') -Tail 8
    } finally { Pop-Location }
}
