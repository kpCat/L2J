[CmdletBinding()]
param(
    [string]$OutputDirectory = '',
    [string]$WorkDirectory = '',
    [switch]$SkipCompile
)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($OutputDirectory -eq '') { $OutputDirectory = Join-Path $module 'docs/phantoms/live-world' }
if ($WorkDirectory -eq '') { $WorkDirectory = Join-Path $module '.phantom-local/logs/LIVE-002-D1/current' }
$output = [IO.Path]::GetFullPath($OutputDirectory)
$work = [IO.Path]::GetFullPath($WorkDirectory)
& python (Join-Path $PSScriptRoot 'travel_backbone.py') prepare --module $module --work $work
if ($LASTEXITCODE -ne 0) { throw 'Travel preparation failed.' }
if (-not $SkipCompile) {
    Push-Location $module
    try { & ant compile-tests; if ($LASTEXITCODE -ne 0) { throw 'Travel helper compilation failed.' } }
    finally { Pop-Location }
}
$build = [IO.Path]::GetFullPath((Join-Path $module '../build'))
$classes = Join-Path $build 'bin'
$testClasses = Join-Path $build 'phantom-test/bin'
$libs = @(Get-ChildItem (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName })
$classpath = (@($classes, $testClasses) + $libs) -join ';'
Push-Location (Join-Path $module 'dist/game')
try {
    & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomTravelGeoProbe (Join-Path $work 'geo-input.tsv') (Join-Path $work 'geo-proof.tsv')
    if ($LASTEXITCODE -ne 0) { throw 'Native GeoEngine probe failed.' }
}
finally { Pop-Location }
& python (Join-Path $PSScriptRoot 'travel_backbone.py') finalize --module $module --work $work --output $output
if ($LASTEXITCODE -ne 0) { throw 'Travel publication failed.' }
