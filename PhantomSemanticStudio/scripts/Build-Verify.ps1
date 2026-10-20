param([switch]$CoreOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path -LiteralPath (Join-Path $root 'PhantomSemanticStudio.sln') -PathType Leaf)) { throw 'Run from the Studio source tree.' }
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) { throw '.NET SDK 10 is required.' }
Push-Location $root
try {
    & "$PSScriptRoot\Verify-Designer.ps1"
    $project = if ($CoreOnly) { 'tests\PhantomSemanticStudio.Tests\PhantomSemanticStudio.Tests.csproj' } else { 'PhantomSemanticStudio.sln' }
    & dotnet build $project -c Release --nologo
    if ($LASTEXITCODE -ne 0) { throw "dotnet build failed: $LASTEXITCODE" }
    & dotnet run --project 'tests\PhantomSemanticStudio.Tests\PhantomSemanticStudio.Tests.csproj' -c Release --no-build
    if ($LASTEXITCODE -ne 0) { throw "Core tests failed: $LASTEXITCODE" }
    Write-Host 'PASS: build and console tests. Designer UI, actual LM Studio and Java parity need separate evidence.'
}
finally { Pop-Location }
