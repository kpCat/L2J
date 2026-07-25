[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

function Resolve-AntCommand
{
    $command = Get-Command ant -ErrorAction SilentlyContinue
    if ($null -ne $command)
    {
        return $command.Source
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANT_HOME))
    {
        $candidate = Join-Path $env:ANT_HOME "bin\ant.bat"
        if (Test-Path -LiteralPath $candidate -PathType Leaf)
        {
            return $candidate
        }
    }

    $knownCandidate = Join-Path $env:TEMP "codex-phantom-task001-ant-1.10.15\apache-ant-1.10.15\bin\ant.bat"
    if (Test-Path -LiteralPath $knownCandidate -PathType Leaf)
    {
        return $knownCandidate
    }

    throw "Apache Ant 1.10.15 was not found in PATH, ANT_HOME, or the existing Task 001 temporary tool location."
}

foreach ($name in @("PHANTOM_DB_ADMIN_URL", "PHANTOM_DB_ADMIN_USER", "PHANTOM_DB_ADMIN_PASSWORD"))
{
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value))
    {
        throw "Required environment variable is missing: $name"
    }
}

$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$antCommand = Resolve-AntCommand

try
{
    & $antCommand -f (Join-Path $moduleRoot "build.xml") prepare-phantom-test-db
    if ($LASTEXITCODE -ne 0)
    {
        throw "Phantom test database provisioning failed with exit code $LASTEXITCODE."
    }
}
finally
{
    Remove-Item Env:PHANTOM_DB_ADMIN_URL -ErrorAction SilentlyContinue
    Remove-Item Env:PHANTOM_DB_ADMIN_USER -ErrorAction SilentlyContinue
    Remove-Item Env:PHANTOM_DB_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
