[CmdletBinding()]
param(
    [string]$WorkDirectory = '',
    [switch]$SkipCompile,
    [switch]$ProofOnly
)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($WorkDirectory -eq '') { $WorkDirectory = Join-Path $module '.phantom-local/logs/LIVE-002-FINAL-GEO/current' }
$work = [IO.Path]::GetFullPath($WorkDirectory)
if (-not $SkipCompile) {
    Push-Location $module
    try { & ant compile-tests; if ($LASTEXITCODE -ne 0) { throw 'Geo helper compilation failed.' } }
    finally { Pop-Location }
}
$build = [IO.Path]::GetFullPath((Join-Path $module '../build'))
$libs = @(Get-ChildItem (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName })
$classpath = (@((Join-Path $build 'bin'), (Join-Path $build 'phantom-test/bin')) + $libs) -join ';'
Push-Location (Join-Path $module 'dist/game')
try {
    & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomTravelGeoProbe (Join-Path $work 'adaptive-geo-input.tsv') (Join-Path $work 'adaptive-geo-proof.tsv')
    if ($LASTEXITCODE -ne 0) { throw 'Native GeoEngine bridge probe failed.' }
}
finally { Pop-Location }
if (-not $ProofOnly) {
    & python (Join-Path $PSScriptRoot 'final_geo.py') finalize-adaptive --module $module --work $work
    if ($LASTEXITCODE -ne 0) { throw 'Adaptive bridge proof accounting failed.' }
}
