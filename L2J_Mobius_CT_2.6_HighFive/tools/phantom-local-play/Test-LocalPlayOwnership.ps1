$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$helper = Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
if (-not (Test-Path -LiteralPath $helper)) { throw 'RED: canonical ownership helper is missing.' }
. $helper

function Assert-Equal($Expected, $Actual, [string] $Case)
{
	if ($Expected -cne $Actual) { throw "$Case expected '$Expected', got '$Actual'." }
}

$runtime = '0123456789abcdef'
$owned = [pscustomobject]@{ pid = 1234; startTimeUtcTicks = [long] 638942400000000000; role = 'GameServer'; jar = 'GameServer.jar'; runtime = $runtime }
$record = [pscustomobject]@{ format = 2; role = 'GameServer'; pid = 1234; startTimeUtcTicks = [long] 638942400000000000; jar = 'GameServer.jar'; runtime = $runtime }
$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $record @($owned) @{ 7777 = 1234 }
Assert-Equal 'RUNNING' $result.state 'valid record'
Assert-Equal 1234 $result.pid 'valid record PID'

$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $null @($owned) @{ 7777 = 1234 }
Assert-Equal 'RUNNING' $result.state 'missing record recovery'

$stale = [pscustomobject]@{ format = 2; role = 'GameServer'; pid = 1234; startTimeUtcTicks = [long] 1; jar = 'GameServer.jar'; runtime = $runtime }
$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $stale @($owned) @{ 7777 = 1234 }
Assert-Equal 'RUNNING' $result.state 'stale record recovery'
Assert-Equal $true $result.staleRecord 'stale record evidence'

$legacy = [pscustomobject]@{ role = 'GameServer'; pid = 1234; startTimeUtc = '2026-09-23T00:00:00.0000000Z' }
$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $legacy @($owned) @{ 7777 = 1234 }
Assert-Equal 'RUNNING' $result.state 'legacy record recovery'
Assert-Equal $true $result.staleRecord 'legacy record evidence'

$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $null @($owned) @{ 7777 = 9999 }
Assert-Equal 'FOREIGN_PORT_OWNER' $result.state 'foreign port'

$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $record @($owned) @{}
Assert-Equal 'INCONSISTENT' $result.state 'pre-listener JVM'
Assert-Equal $true $result.recordVerified 'pre-listener v2 identity'

$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $null @($owned) @{}
Assert-Equal $false $result.recordVerified 'missing-record pre-listener JVM'

$result = Resolve-LocalPlayOwnership 'GameServer' $runtime 'GameServer.jar' @(7777) $null @($owned, ([pscustomobject]@{ pid = 5678; startTimeUtcTicks = [long] 2; role = 'GameServer'; jar = 'GameServer.jar'; runtime = $runtime })) @{ 7777 = 1234 }
Assert-Equal 'INCONSISTENT' $result.state 'duplicate marked JVM'

Write-Host 'Local-play ownership regression: 8/8 PASS.'
