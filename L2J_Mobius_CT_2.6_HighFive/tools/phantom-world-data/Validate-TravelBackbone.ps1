[CmdletBinding()]
param([string]$OutputDirectory = '', [string]$WorkDirectory = '')
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($OutputDirectory -eq '') { $OutputDirectory = Join-Path $module 'docs/phantoms/live-world' }
if ($WorkDirectory -eq '') { $WorkDirectory = Join-Path $module '.phantom-local/logs/LIVE-002-D1/validation' }
$work = [IO.Path]::GetFullPath($WorkDirectory)
& python (Join-Path $PSScriptRoot 'travel_backbone.py') prepare --module $module --work $work
if ($LASTEXITCODE -ne 0) { throw 'Travel validation preparation failed.' }
$build = [IO.Path]::GetFullPath((Join-Path $module '../build'))
$classes = Join-Path $build 'bin'
$testClasses = Join-Path $build 'phantom-test/bin'
if (-not (Test-Path (Join-Path $testClasses 'org/l2jmobius/tests/phantoms/PhantomTravelGeoProbe.class'))) {
    Push-Location $module
    try { & ant compile-tests; if ($LASTEXITCODE -ne 0) { throw 'Travel helper compilation failed.' } }
    finally { Pop-Location }
}
$libs = @(Get-ChildItem (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName })
$classpath = (@($classes, $testClasses) + $libs) -join ';'
$proof = Join-Path $work 'geo-proof.tsv'
$log = Join-Path $work 'validation-geo.log'
Push-Location (Join-Path $module 'dist/game')
try {
    & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomTravelGeoProbe (Join-Path $work 'geo-input.tsv') $proof *> $log
    if ($LASTEXITCODE -ne 0) { throw "Native GeoEngine validation probe failed; see $log" }
}
finally { Pop-Location }
& python (Join-Path $PSScriptRoot 'travel_backbone.py') validate --module $module --output ([IO.Path]::GetFullPath($OutputDirectory)) --proof $proof
if ($LASTEXITCODE -ne 0) { throw 'Travel production validation failed.' }
