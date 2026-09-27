[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$invoke = Join-Path $module 'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1'
$stop = Join-Path $module 'tools/phantom-local-play/Stop-LocalPlayPilot.ps1'
$output = Join-Path $PSScriptRoot 'MATERIALIZATION_CONNECTED_PROOF.tsv'
$runId = [guid]::NewGuid().ToString('D')
$deadline = [DateTime]::UtcNow.AddMinutes(10)
$rows = New-Object System.Collections.Generic.List[object]
$origin = $null
$uncertain = $false
$profileId = 0L

function Invoke-Proof([string] $operation, [hashtable] $arguments = @{})
{
	if ([DateTime]::UtcNow -ge $script:deadline) { throw 'PROOF_DEADLINE_EXPIRED' }
	$json = & $script:invoke -Operation $operation -Arguments $arguments -RunId $script:runId -TimeoutSeconds 20
	if (-not $json) { throw "PILOT_ACTION_FAILED:$operation" }
	$result = $json | ConvertFrom-Json
	if (($result.status -eq 'UNCERTAIN') -or ($result.harnessResult -eq 'UNCERTAIN')) { $script:uncertain = $true; throw "UNCERTAIN:$operation" }
	return $result
}

function Read-Field($object, [string] $name, [string] $fallback = '')
{
	if (($null -eq $object) -or ($null -eq $object.PSObject.Properties[$name])) { return $fallback }
	return [string] $object.$name
}

function Capture([string] $phase, [string] $transition = '')
{
	$result = Invoke-Proof 'SNAPSHOT_M1_ENVELOPE'
	if ($result.status -cne 'SUCCEEDED') { throw "ENVELOPE_SNAPSHOT_FAILED:$($result.reason)" }
	$after = $result.after
	$target = $result.candidate
	if ((Read-Field $after 'identityOwner') -cne 'REAL_LOGIN' -or (((Read-Field $after 'worldPresent') -cne 'true') -and ((Read-Field $after 'teleporting') -cne 'true')) -or [long](Read-Field $target 'profileId' '0') -ne $script:profileId) { throw 'ACTOR_OR_TARGET_IDENTITY_CHANGED' }
	$script:rows.Add([pscustomobject]@{
		utc = (Read-Field $result 'endUtc'); phase = $phase; transition = $transition
		humanX = (Read-Field $after 'x'); humanY = (Read-Field $after 'y'); humanZ = (Read-Field $after 'z'); humanInstance = (Read-Field $after 'instanceId')
		humanRegionX = (Read-Field $target 'humanRegionX'); humanRegionY = (Read-Field $target 'humanRegionY'); humanRegionZ = (Read-Field $target 'humanRegionZ')
		profileId = (Read-Field $target 'profileId'); committedX = (Read-Field $target 'committedX'); committedY = (Read-Field $target 'committedY'); committedZ = (Read-Field $target 'committedZ')
		targetRegionX = (Read-Field $target 'targetRegionX'); targetRegionY = (Read-Field $target 'targetRegionY'); targetRegionZ = (Read-Field $target 'targetRegionZ')
		liveX = (Read-Field $target 'liveX'); liveY = (Read-Field $target 'liveY'); liveZ = (Read-Field $target 'liveZ')
		objectId = (Read-Field $target 'objectId'); worldPresent = (Read-Field $target 'worldPresent'); snapshotWorldPresent = (Read-Field $target 'snapshotWorldPresent')
		regionCanKnow = (Read-Field $target 'regionCanKnow'); clientVisible = (Read-Field $target 'clientVisible'); distance2D = (Read-Field $target 'distance2D')
		localityCurrent = (Read-Field $target 'localityCurrent'); materializationState = (Read-Field $target 'materializationState'); materializedAgeMillis = (Read-Field $target 'materializedAgeMillis')
		activityState = (Read-Field $target 'activityState'); requestedState = (Read-Field $target 'requestedState'); activeSignalSources = (Read-Field $target 'activeSignalSources')
		boundaryInFlight = (Read-Field $target 'boundaryInFlight'); presenceReason = (Read-Field $target 'presenceReason')
	})
	return $result
}

function Wait-At([int] $x, [int] $y, [int] $z, [string] $phase)
{
	for ($attempt = 0; $attempt -lt 25; $attempt++)
	{
		Start-Sleep -Milliseconds 750
		$sample = Capture $phase
		$actor = $sample.after
		$distance = [Math]::Sqrt([Math]::Pow([int]$actor.x - $x, 2) + [Math]::Pow([int]$actor.y - $y, 2))
		if (($distance -le 64) -and ([Math]::Abs([int]$actor.z - $z) -le 120) -and ($actor.moving -cne 'true') -and ($actor.teleporting -cne 'true')) { return }
	}
	throw "ARRIVAL_TIMEOUT:${phase}:$x,$y,$z"
}

function Teleport-To([int] $x, [int] $y, [int] $z, [string] $phase)
{
	$result = Invoke-Proof 'TELEPORT_SELF' @{ x = $x; y = $y; z = $z; instanceId = 0 }
	if ($result.status -cne 'ACCEPTED') { throw "TELEPORT_REJECTED:${phase}:$($result.reason)" }
	Wait-At $x $y $z $phase
}

function Move-Leg([int] $fromX, [int] $fromY, [int] $fromZ, [int] $toX, [int] $toY, [int] $toZ, [string] $phase)
{
	for ($part = 1; $part -le 4; $part++)
	{
		$x = [int][Math]::Round($fromX + (($toX - $fromX) * $part / 4.0))
		$y = [int][Math]::Round($fromY + (($toY - $fromY) * $part / 4.0))
		$z = [int][Math]::Round($fromZ + (($toZ - $fromZ) * $part / 4.0))
		$result = Invoke-Proof 'MOVE_SELF' @{ x = $x; y = $y; z = $z }
		if ($result.status -cne 'ACCEPTED') { throw "MOVE_REJECTED:${phase}:${part}:$($result.reason)" }
		Wait-At $x $y $z $phase
	}
}

function Traverse([bool] $west, [string] $phase)
{
	$points = @(
		@(44126, 42751, -3488), @(42857, 42534, -3514), @(41588, 42316, -3540),
		@(40319, 42099, -3566), @(39050, 41882, -3592)
	)
	if (-not $west) { [array]::Reverse($points) }
	for ($index = 0; $index -lt ($points.Count - 1); $index++)
	{
		Move-Leg $points[$index][0] $points[$index][1] $points[$index][2] $points[$index + 1][0] $points[$index + 1][1] $points[$index + 1][2] $phase
	}
}

function Wait-For([string] $phase, [scriptblock] $predicate, [int] $seconds)
{
	for ($attempt = 0; $attempt -lt $seconds; $attempt++)
	{
		$sample = Capture $phase
		if (& $predicate $sample) { return $sample }
		Start-Sleep -Seconds 1
	}
	throw "PHASE_TIMEOUT:$phase"
}

try
{
	$initial = Invoke-Proof 'STATUS'
	if (($initial.status -cne 'SUCCEEDED') -or ($initial.after.identityOwner -cne 'REAL_LOGIN') -or ($initial.after.worldPresent -cne 'true')) { throw 'NO_CONSENTED_REAL_LOGIN' }
	$origin = [pscustomobject]@{ x = [int]$initial.after.x; y = [int]$initial.after.y; z = [int]$initial.after.z; instanceId = [int]$initial.after.instanceId }
	$prepared = Invoke-Proof 'PREPARE_M1_ENVELOPE'
	if ($prepared.status -cne 'ACCEPTED') { throw "PREPARE_REJECTED:$($prepared.reason)" }
	$profileId = [long]$prepared.candidate.profileId
	Wait-At 45085 42001 -3496 'OUTSIDE_ARRIVAL'
	$null = Wait-For 'OUTSIDE' { param($s) ($s.candidate.worldPresent -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') -and ($s.candidate.localityCurrent -ceq 'false') } 20
	Teleport-To 44126 42751 -3488 'PREWARM_APPROACH'
	$prewarm = Wait-For 'PREWARM' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') } 15
	$null = Capture 'PREWARM' 'FIRST_MATERIALIZED_BEFORE_VISIBILITY'
	Traverse $true 'VISIBLE_FORWARD'
	$null = Capture 'VISIBLE_FORWARD' 'FORWARD_END'
	Traverse $false 'VISIBLE_EXIT'
	Teleport-To 45085 42001 -3496 'EXIT_OUTSIDE'
	$null = Capture 'EXIT_GRACE' 'OUTSIDE_AFTER_EXIT'
	Start-Sleep -Seconds 3
	$null = Capture 'EXIT_GRACE' 'GRACE_SAMPLE'
	$null = Wait-For 'DEMATERIALIZATION' { param($s) ($s.candidate.worldPresent -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') -and ($s.candidate.regionCanKnow -ceq 'false') } 20
	Teleport-To 44126 42751 -3488 'REENTRY_PREWARM'
	$null = Wait-For 'REENTRY_PREWARM' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') } 15
	$null = Capture 'REENTRY_PREWARM' 'REMATERIALIZED_BEFORE_VISIBILITY'
	Traverse $true 'REENTRY_VISIBLE'
	$null = Capture 'REENTRY_VISIBLE' 'REENTRY_END'
}
catch
{
	if ($_.Exception.Message -like '*UNCERTAIN*') { $uncertain = $true }
	Write-Error $_.Exception.Message
}
finally
{
	if (($null -ne $origin) -and (-not $uncertain))
	{
		try
		{
			$restore = Invoke-Proof 'TELEPORT_SELF' @{ x = $origin.x; y = $origin.y; z = $origin.z; instanceId = $origin.instanceId }
			if ($restore.status -eq 'ACCEPTED') { Start-Sleep -Seconds 2; $null = Invoke-Proof 'STATUS' }
		}
		catch { Write-Warning "Pilot origin restoration needs inspection: $($_.Exception.Message)" }
	}
	if ($rows.Count -gt 0)
	{
		$columns = @($rows[0].PSObject.Properties.Name)
		$lines = New-Object System.Collections.Generic.List[string]
		$lines.Add(($columns -join "`t"))
		foreach ($row in $rows) { $lines.Add((($columns | ForEach-Object { [string]$row.$_ }) -join "`t")) }
		[IO.File]::WriteAllLines($output, $lines, [Text.UTF8Encoding]::new($false))
	}
	& $stop | Out-Null
}
