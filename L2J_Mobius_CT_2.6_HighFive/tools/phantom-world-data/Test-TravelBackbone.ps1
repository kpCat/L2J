[CmdletBinding()]
param([switch]$SkipCompile)
$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $PSScriptRoot
try { & python -m unittest test_travel_backbone.py; if ($LASTEXITCODE -ne 0) { throw 'Travel fixture suite failed.' } }
finally { Pop-Location }
if (-not $SkipCompile) {
    Push-Location $module
    try { & ant phantom-geodata-rules-test; if ($LASTEXITCODE -ne 0) { throw 'GeoEngine route controls failed.' } }
    finally { Pop-Location }
}
else {
    $build = [IO.Path]::GetFullPath((Join-Path $module '../build'))
    $libs = @(Get-ChildItem (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName })
    $classpath = (@((Join-Path $build 'bin'), (Join-Path $build 'phantom-test/bin')) + $libs) -join ';'
    Push-Location (Join-Path $module 'dist/game')
    try {
        & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomGeoValidationRulesTest
        if ($LASTEXITCODE -ne 0) { throw 'GeoEngine route controls failed.' }
    }
    finally { Pop-Location }
}
