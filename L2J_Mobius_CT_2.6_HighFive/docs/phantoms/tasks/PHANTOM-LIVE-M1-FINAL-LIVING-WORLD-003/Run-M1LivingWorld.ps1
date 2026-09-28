[CmdletBinding()]
param(
	[ValidateRange(0, [long]::MaxValue)][long] $AfterProfileId = 0,
	[ValidatePattern('^[0-9a-fA-F-]{36}$')][string] $ResumePreparedRequestId,
	[switch] $ResumeFromReentry
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$invoke = Join-Path $module 'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1'
$stop = Join-Path $module 'tools/phantom-local-play/Stop-LocalPlayPilot.ps1'
$output = Join-Path $PSScriptRoot 'M1_CONNECTED_WORLD.tsv'
$censusOutput = Join-Path $PSScriptRoot 'M1_VISIBLE_LIFE_CENSUS.tsv'
$runId = [guid]::NewGuid().ToString('D')
$deadline = [DateTime]::UtcNow.AddMinutes(10)
$rows = New-Object System.Collections.Generic.List[object]
$census = New-Object System.Collections.Generic.List[object]
$origin = $null
$uncertain = $false
$profileId = 0L
$route = $null
if ($ResumeFromReentry -and (-not $ResumePreparedRequestId)) { throw 'RESUME_REENTRY_REQUIRES_CURRENT_LEASE_ROUTE' }

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
	if (($object.$name -is [DateTime]) -or ($object.$name -is [DateTimeOffset])) { return $object.$name.ToUniversalTime().ToString('o') }
	return [string] $object.$name
}

function Capture([string] $phase, [string] $transition = '')
{
	$result = Invoke-Proof 'SNAPSHOT_M1_ENVELOPE'
	if ($result.status -cne 'SUCCEEDED') { throw "ENVELOPE_SNAPSHOT_FAILED:$($result.reason)" }
	$after = $result.after
	$target = $result.candidate
	for ($sample = 1; $sample -le [int](Read-Field $target 'censusCount' '0'); $sample++)
	{
		$prefix = "census${sample}."
		$record = [ordered]@{ utc = (Read-Field $result 'endUtc'); phase = $phase }
		foreach ($field in @('profileId', 'objectId', 'level', 'npcId', 'anchor', 'goalStatus', 'runtimeReason', 'travelReason', 'dead', 'moving', 'attacking', 'casting', 'autoPlay', 'party', 'store', 'intention', 'shortTargets', 'longTargets'))
		{
			$record[$field] = Read-Field $target ($prefix + $field)
		}
		$script:census.Add([pscustomobject]$record)
	}
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
		admitted = (Read-Field $target 'admitted'); transitionStatus = (Read-Field $target 'transitionStatus'); lastMaterializationFailure = (Read-Field $target 'lastMaterializationFailure')
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
	$parts = [Math]::Max(1, [int][Math]::Ceiling([Math]::Sqrt([Math]::Pow($toX - $fromX, 2) + [Math]::Pow($toY - $fromY, 2)) / 300.0))
	for ($part = 1; $part -le $parts; $part++)
	{
		$x = [int][Math]::Round($fromX + (($toX - $fromX) * $part / $parts))
		$y = [int][Math]::Round($fromY + (($toY - $fromY) * $part / $parts))
		$z = [int][Math]::Round($fromZ + (($toZ - $fromZ) * $part / $parts))
		$result = Invoke-Proof 'MOVE_SELF' @{ x = $x; y = $y; z = $z }
		if ($result.status -cne 'ACCEPTED') { throw "MOVE_REJECTED:${phase}:${part}:$($result.reason)" }
		Wait-At $x $y $z $phase
	}
}

function Traverse([bool] $west, [string] $phase)
{
	$points = @($script:route.route.Split(';') | ForEach-Object { ,@($_.Split(',') | ForEach-Object { [int]$_ }) })
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
	$origin = [pscustomobject]@{ x = [int]$initial.candidate.originX; y = [int]$initial.candidate.originY; z = [int]$initial.candidate.originZ; instanceId = [int]$initial.candidate.originInstanceId }
	if ($ResumePreparedRequestId)
	{
		$prepared = (& (Join-Path $module 'tools/phantom-local-play/Get-LocalPlayPilot.ps1') -RequestId $ResumePreparedRequestId) | ConvertFrom-Json
		if (($prepared.operation -cne 'PREPARE_M1_ENVELOPE') -or ($prepared.sessionId -cne $initial.sessionId)) { throw 'RESUME_ROUTE_NOT_FROM_CURRENT_LEASE' }
	}
	else { $prepared = Invoke-Proof 'PREPARE_M1_ENVELOPE' @{ afterProfileId = [string] $AfterProfileId } }
	if ($prepared.status -cne 'ACCEPTED') { throw "PREPARE_REJECTED:$($prepared.reason)" }
	$profileId = [long]$prepared.candidate.profileId
	$route = $prepared.candidate
	if ($ResumePreparedRequestId -and (-not $ResumeFromReentry))
	{
		$points = @($route.route.Split(';') | ForEach-Object { ,@($_.Split(',') | ForEach-Object { [int]$_ }) })
		$nearestIndex = 0
		$nearestDistance = [double]::MaxValue
		for ($index = 0; $index -lt $points.Count; $index++)
		{
			$distance = [Math]::Sqrt([Math]::Pow([int]$initial.after.x - $points[$index][0], 2) + [Math]::Pow([int]$initial.after.y - $points[$index][1], 2))
			if ($distance -lt $nearestDistance) { $nearestDistance = $distance; $nearestIndex = $index }
		}
		Move-Leg ([int]$initial.after.x) ([int]$initial.after.y) ([int]$initial.after.z) $points[$nearestIndex][0] $points[$nearestIndex][1] $points[$nearestIndex][2] 'VISIBLE_EXIT_RESUME'
		for ($index = $nearestIndex; $index -gt 0; $index--) { Move-Leg $points[$index][0] $points[$index][1] $points[$index][2] $points[$index - 1][0] $points[$index - 1][1] $points[$index - 1][2] 'VISIBLE_EXIT_RESUME' }
	}
	elseif (-not $ResumeFromReentry)
	{
	Wait-At ([int]$route.startX) ([int]$route.startY) ([int]$route.startZ) 'OUTSIDE_ARRIVAL'
	$null = Wait-For 'OUTSIDE' { param($s) ($s.candidate.worldPresent -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') -and ($s.candidate.localityCurrent -ceq 'false') } 20
	Teleport-To ([int]$route.prewarmX) ([int]$route.prewarmY) ([int]$route.prewarmZ) 'PREWARM_APPROACH'
	$prewarm = Wait-For 'PREWARM' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') } 45
	$null = Capture 'PREWARM' 'FIRST_MATERIALIZED_BEFORE_VISIBILITY'
	Traverse $true 'VISIBLE_FORWARD'
	$null = Capture 'VISIBLE_FORWARD' 'FORWARD_END'
	$null = Wait-For 'VISIBLE_ENTRY' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'true') } 10
	$visibleStationarySamples = 0
	for ($sample = 0; $sample -lt 8; $sample++)
	{
		Start-Sleep -Seconds 5
		$stationary = Capture 'VISIBLE_STATIONARY_LIFE'
		if (($stationary.candidate.regionCanKnow -ceq 'true') -and ($stationary.candidate.worldPresent -cne 'true')) { throw 'VISIBLE_DISAPPEARANCE' }
		if ($stationary.candidate.regionCanKnow -ceq 'true') { $visibleStationarySamples++ }
	}
	if ($visibleStationarySamples -lt 3) { throw 'INSUFFICIENT_STATIONARY_VISIBLE_WINDOW' }
	Traverse $false 'VISIBLE_EXIT'
	}
	if (-not $ResumeFromReentry)
	{
	Teleport-To ([int]$route.startX) ([int]$route.startY) ([int]$route.startZ) 'EXIT_OUTSIDE'
	$null = Capture 'EXIT_GRACE' 'OUTSIDE_AFTER_EXIT'
	Start-Sleep -Seconds 3
	$null = Capture 'EXIT_GRACE' 'GRACE_SAMPLE'
	$null = Wait-For 'DEMATERIALIZATION' { param($s) ($s.candidate.worldPresent -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') -and ($s.candidate.regionCanKnow -ceq 'false') } 20
	}
	$prepared = Invoke-Proof 'PREPARE_M1_ENVELOPE' @{ profileId = [string]$profileId }
	if ($prepared.status -cne 'ACCEPTED') { throw "REENTRY_PREPARE_REJECTED:$($prepared.reason)" }
	$route = $prepared.candidate
	Wait-At ([int]$route.startX) ([int]$route.startY) ([int]$route.startZ) 'REENTRY_OUTSIDE'
	$beforeReentry = Capture 'REENTRY_OUTSIDE'
	$wasStoredOutside = $beforeReentry.candidate.worldPresent -ceq 'false'
	Teleport-To ([int]$route.prewarmX) ([int]$route.prewarmY) ([int]$route.prewarmZ) 'REENTRY_PREWARM'
	$null = Wait-For 'REENTRY_PREWARM' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') } 45
	$null = Capture 'REENTRY_PREWARM' $(if ($wasStoredOutside) { 'REMATERIALIZED_BEFORE_VISIBILITY' } else { 'ALREADY_MATERIALIZED_OUTSIDE_RED' })
	Traverse $true 'REENTRY_VISIBLE'
	$null = Capture 'REENTRY_VISIBLE' 'REENTRY_END'
	if ($ResumeFromReentry)
	{
		for ($sample = 0; $sample -lt 8; $sample++) { Start-Sleep -Seconds 5; $null = Capture 'REENTRY_STATIONARY_LIFE' }
	}
	if (-not $wasStoredOutside) { throw 'REENTRY_NOT_REMATERIALIZED_EXIT_REMAINED_MATERIALIZED' }
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
			if ($restore.status -cne 'ACCEPTED') { throw "ORIGIN_RETURN_REJECTED:$($restore.reason)" }
			Start-Sleep -Seconds 2
			$restored = Invoke-Proof 'STATUS'
			if (($restored.status -cne 'SUCCEEDED') -or ($restored.after.identityOwner -cne 'REAL_LOGIN') -or ($restored.after.worldPresent -cne 'true') -or ([int]$restored.after.instanceId -ne $origin.instanceId) -or ([Math]::Abs([int]$restored.after.x - $origin.x) -gt 64) -or ([Math]::Abs([int]$restored.after.y - $origin.y) -gt 64) -or ([Math]::Abs([int]$restored.after.z - $origin.z) -gt 120)) { throw 'ORIGIN_RETURN_NOT_CONFIRMED' }
		}
		catch { Write-Warning "Pilot origin restoration needs inspection: $($_.Exception.Message)" }
	}
	if ($rows.Count -gt 0)
	{
		$columns = @($rows[0].PSObject.Properties.Name)
		$lines = New-Object System.Collections.Generic.List[string]
		$lines.Add(($columns -join "`t"))
		foreach ($row in $rows) { $lines.Add((($columns | ForEach-Object { [string]$row.$_ }) -join "`t")) }
		if ($ResumePreparedRequestId -and (Test-Path -LiteralPath $output)) { [IO.File]::AppendAllLines($output, [string[]]@($lines | Select-Object -Skip 1), [Text.UTF8Encoding]::new($false)) }
		else { [IO.File]::WriteAllLines($output, $lines, [Text.UTF8Encoding]::new($false)) }
	}
	if ($census.Count -gt 0)
	{
		$columns = @($census[0].PSObject.Properties.Name)
		$lines = New-Object System.Collections.Generic.List[string]
		$lines.Add(($columns -join "`t"))
		foreach ($row in $census) { $lines.Add((($columns | ForEach-Object { [string]$row.$_ }) -join "`t")) }
		if ($ResumePreparedRequestId -and (Test-Path -LiteralPath $censusOutput)) { [IO.File]::AppendAllLines($censusOutput, [string[]]@($lines | Select-Object -Skip 1), [Text.UTF8Encoding]::new($false)) }
		else { [IO.File]::WriteAllLines($censusOutput, $lines, [Text.UTF8Encoding]::new($false)) }
	}
	& $stop | Out-Null
}
