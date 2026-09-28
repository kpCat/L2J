[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$invoke = Join-Path $module 'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1'
$runId = [guid]::NewGuid().ToString('D')
$deadline = [DateTime]::UtcNow.AddMinutes(8)
$rows = New-Object System.Collections.Generic.List[object]
$census = New-Object System.Collections.Generic.List[object]
$origin = $null
$uncertain = $false
$profileId = 0L
$objectId = 0
$birthUtc = $null
$failure = $null
$restored = $false
$firstLocalUtc = $null
$firstMaterializedUtc = $null
$firstVisibleUtc = $null
$coverage = 'NOT_OBSERVED'
$cleanup = $false

# Reuse M1-003's consented Pilot operations and immutable native snapshots.
function Invoke-Proof([string] $operation, [hashtable] $arguments = @{})
{
	if ([DateTime]::UtcNow -ge $script:deadline) { throw 'ACCEPTANCE_DEADLINE_EXPIRED' }
	if ($script:cleanup -and ($operation -cnotin @('TELEPORT_SELF', 'STATUS'))) { throw 'CLEANUP_OPERATION_REJECTED' }
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
	$actor = $result.after
	$target = $result.candidate
	if ((Read-Field $actor 'identityOwner') -cne 'REAL_LOGIN' -or [long](Read-Field $target 'profileId' '0') -ne $script:profileId) { throw 'ACTOR_OR_TARGET_IDENTITY_CHANGED' }
	$record = [ordered]@{ utc = (Read-Field $result 'endUtc'); phase = $phase; transition = $transition; humanX = (Read-Field $actor 'x'); humanY = (Read-Field $actor 'y'); humanZ = (Read-Field $actor 'z') }
	foreach ($field in @('profileId', 'objectId', 'committedX', 'committedY', 'committedZ', 'liveX', 'liveY', 'liveZ', 'worldPresent', 'snapshotWorldPresent', 'regionCanKnow', 'clientVisible', 'distance2D', 'localityCurrent', 'materializationState', 'materializedAgeMillis', 'activityState', 'requestedState', 'activeSignalSources', 'boundaryInFlight', 'presenceReason', 'admitted', 'transitionStatus', 'lastMaterializationFailure', 'calendarState', 'calendarOnline', 'nextBoundary', 'nativeVisible', 'retentionPins', 'readinessReason', 'committedCursorMinute', 'requestedHorizonMinute', 'initialCatchupComplete', 'requestPending', 'readinessQueued', 'readinessRunning', 'readinessRevision', 'lastTransitionReason', 'censusCount', 'censusEligible'))
	{
		$record[$field] = Read-Field $target $field
	}
	$page = $target
	$totalCensus = 0
	$totalEligible = 0
	for ($pageNumber = 0; $pageNumber -lt 6; $pageNumber++)
	{
		$totalCensus += [int](Read-Field $page 'censusCount' '0')
		$totalEligible += [int](Read-Field $page 'censusEligible' '0')
		for ($sample = 1; $sample -le [int](Read-Field $page 'censusCount' '0'); $sample++)
		{
			$entry = [ordered]@{ utc = $record.utc; phase = $phase }
			foreach ($field in @('profileId', 'objectId', 'level', 'npcId', 'anchor', 'goalStatus', 'runtimeReason', 'travelReason', 'travelFailureReason', 'travelFailureSequence', 'dead', 'moving', 'attacking', 'casting', 'autoPlay', 'party', 'store', 'intention', 'shortTargets', 'longTargets', 'x', 'y', 'z', 'targetObjectId', 'eligible', 'idleReason')) { $entry[$field] = Read-Field $page "census${sample}.$field" }
			$script:census.Add([pscustomobject]$entry)
		}
		$cursor = [long](Read-Field $page 'censusNextProfileId' '0')
		if ($cursor -eq 0) { break }
		$pageResult = Invoke-Proof 'SNAPSHOT_M1_ENVELOPE' @{ censusAfterProfileId = $cursor }
		if ($pageResult.status -cne 'SUCCEEDED') { throw "CENSUS_PAGE_FAILED:$($pageResult.reason)" }
		$page = $pageResult.candidate
	}
	$record.censusCount = [string]$totalCensus
	$record.censusEligible = [string]$totalEligible
	$script:rows.Add([pscustomobject]$record)
	if (($target.regionCanKnow -ceq 'true') -and ($target.worldPresent -cne 'true')) { throw 'VISIBLE_DISAPPEARANCE' }
	if (($null -eq $script:firstLocalUtc) -and ($target.localityCurrent -ceq 'true')) { $script:firstLocalUtc = $record.utc }
	if (($null -eq $script:firstMaterializedUtc) -and ($target.worldPresent -ceq 'true')) { $script:firstMaterializedUtc = $record.utc }
	if (($null -eq $script:firstVisibleUtc) -and ($target.regionCanKnow -ceq 'true')) { $script:firstVisibleUtc = $record.utc }
	if (($script:objectId -eq 0) -and ($target.worldPresent -ceq 'true'))
	{
		$script:objectId = [int]$target.objectId
		$script:birthUtc = [DateTime]::Parse($record.utc).ToUniversalTime().AddMilliseconds(-[long]$target.materializedAgeMillis)
	}
	if ($script:objectId -gt 0)
	{
		if (($target.worldPresent -cne 'true') -or ([int]$target.objectId -ne $script:objectId)) { throw 'CONTINUITY_PLAYER_LOST' }
		$currentBirth = [DateTime]::Parse($record.utc).ToUniversalTime().AddMilliseconds(-[long]$target.materializedAgeMillis)
		if ([Math]::Abs(($currentBirth - $script:birthUtc).TotalMilliseconds) -gt 1500) { throw 'CONTINUITY_PLAYER_REMATERIALIZED' }
	}
	return $result
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

function Teleport-To([int] $x, [int] $y, [int] $z, [string] $phase)
{
	$result = Invoke-Proof 'TELEPORT_SELF' @{ x = $x; y = $y; z = $z; instanceId = 0 }
	if ($result.status -cne 'ACCEPTED') { throw "TELEPORT_REJECTED:${phase}:$($result.reason)" }
	$null = Wait-For $phase { param($s) ($s.after.teleporting -cne 'true') -and ($s.after.worldPresent -ceq 'true') -and ([Math]::Abs([int]$s.after.x - $x) -le 64) -and ([Math]::Abs([int]$s.after.y - $y) -le 64) -and ([Math]::Abs([int]$s.after.z - $z) -le 120) } 10
}

function Follow-Native($sample, [string] $phase)
{
	# The Phantom owns its movement. TestAdmin approaches its observed native location.
	Teleport-To ([int]$sample.candidate.liveX) ([int]$sample.candidate.liveY) ([int]$sample.candidate.liveZ) $phase
}

function Walk-To([int] $x, [int] $y, [int] $z, [string] $phase)
{
	$sample = Capture $phase
	for ($step = 0; $step -lt 100; $step++)
	{
		$dx = $x - [int]$sample.after.x; $dy = $y - [int]$sample.after.y; $dz = $z - [int]$sample.after.z
		$distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
		if (($distance -le 32) -and ([Math]::Abs($dz) -le 100)) { return $sample }
		$fraction = [Math]::Min(1.0, 300.0 / [Math]::Max(1.0, $distance))
		$next = @{ x = [int]([int]$sample.after.x + $dx * $fraction); y = [int]([int]$sample.after.y + $dy * $fraction); z = [int]([int]$sample.after.z + $dz * $fraction) }
		$moved = Invoke-Proof 'MOVE_SELF' $next
		if ($moved.status -cne 'ACCEPTED') { throw "NATIVE_APPROACH_REJECTED:$($moved.reason)" }
		$sample = Wait-For $phase { param($s) (-not ($s.after.moving -ceq 'true')) -or (([Math]::Abs([int]$s.after.x - $next.x) -le 32) -and ([Math]::Abs([int]$s.after.y - $next.y) -le 32)) } 5
	}
	throw "NATIVE_APPROACH_BOUND:$phase"
}

function Save-Tsv($data, [string] $name)
{
	if ($data.Count -eq 0) { return }
	$columns = @($data[0].PSObject.Properties.Name)
	$lines = New-Object System.Collections.Generic.List[string]
	$lines.Add(($columns -join "`t"))
	foreach ($row in $data) { $lines.Add((($columns | ForEach-Object { [string]$row.$_ }) -join "`t")) }
	[IO.File]::WriteAllLines((Join-Path $PSScriptRoot $name), $lines, [Text.UTF8Encoding]::new($false))
}

try
{
	$initial = Invoke-Proof 'STATUS'
	if (($initial.status -cne 'SUCCEEDED') -or ($initial.after.identityOwner -cne 'REAL_LOGIN') -or ($initial.after.worldPresent -cne 'true')) { throw 'NO_CONSENTED_REAL_LOGIN' }
	$origin = [pscustomobject]@{ x = [int]$initial.candidate.originX; y = [int]$initial.candidate.originY; z = [int]$initial.candidate.originZ; instanceId = [int]$initial.candidate.originInstanceId }
	$prepared = Invoke-Proof 'PREPARE_M1_ENVELOPE'
	if ($prepared.status -cne 'ACCEPTED') { throw "PREPARE_REJECTED:$($prepared.reason)" }
	$profileId = [long]$prepared.candidate.profileId
	$route = $prepared.candidate
	$null = Wait-For 'OUTSIDE' { param($s) ($s.after.teleporting -cne 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.localityCurrent -ceq 'false') } 10
	if ([DateTime]::Parse($route.nextBoundary).ToUniversalTime() -le [DateTime]::UtcNow.AddSeconds(180)) { throw 'SCENE_INVALIDATED:CALENDAR_HORIZON' }
	$latency = [Diagnostics.Stopwatch]::StartNew()
	$null = Walk-To ([int]$route.prewarmX) ([int]$route.prewarmY) ([int]$route.prewarmZ) 'PREWARM_APPROACH'
	foreach ($waypoint in ([string]$route.route).Split(';'))
	{
		$point = $waypoint.Split(',')
		$null = Walk-To ([int]$point[0]) ([int]$point[1]) ([int]$point[2]) 'NATIVE_ENTRY'
	}
	$latency.Stop()
	$null = Capture 'VISIBLE_ENTRY' "NATURAL_APPROACH_MS=$($latency.ElapsedMilliseconds);LOCAL=$firstLocalUtc;MATERIALIZED=$firstMaterializedUtc;VISIBLE=$firstVisibleUtc"
	if (($objectId -eq 0) -or ($null -eq $firstVisibleUtc)) { throw 'NORMAL_MATERIALIZATION_DURING_APPROACH_NOT_OBSERVED' }
	for ($sample = 0; $sample -lt 8; $sample++)
	{
		Start-Sleep -Seconds 5
		$observed = Capture 'VISIBLE_LIFE'
		if ($observed.candidate.regionCanKnow -cne 'true') { Follow-Native $observed 'NATIVE_FOLLOW' }
	}
	Teleport-To ([int]$route.startX) ([int]$route.startY) ([int]$route.startZ) 'SHORT_LEAVE'
	$null = Wait-For 'SOFT_CONTINUITY' { param($s) ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.localityCurrent -ceq 'false') } 10
	for ($sample = 0; $sample -lt 3; $sample++) { Start-Sleep -Seconds 5; $returnTarget = Capture 'SOFT_CONTINUITY' 'SAME_PLAYER_RETAINED' }
	Follow-Native $returnTarget 'SHORT_RETURN'
	$null = Wait-For 'RETURN_VISIBLE' { param($s) $s.candidate.regionCanKnow -ceq 'true' } 10
	$null = Capture 'RETURN_VISIBLE' 'SAME_PLAYER_NO_CHURN'
	$ordinary = @($census | Where-Object { ($_.goalStatus -ceq 'ACTIVE') -and ($_.dead -ceq 'false') -and ($_.store -ceq 'false') -and ($_.party -ceq 'false') })
	$eligibleProfiles = @($census | Where-Object { $_.eligible -ceq 'true' } | Select-Object -ExpandProperty profileId -Unique).Count
	$coverage = if ($eligibleProfiles -ge 4) { 'COHORT_COVERAGE_SUFFICIENT' } else { 'COHORT_COVERAGE_INSUFFICIENT' }
	$failedIdle = @($ordinary | Where-Object { ($_.idleReason -ceq 'ACTIVE_IDLE') -and ($_.travelFailureReason -match '(navigation_|native_progress_|route_absent)') } | Group-Object profileId | Where-Object { @($_.Group | Select-Object -ExpandProperty travelFailureSequence -Unique).Count -ge 2 })
	if ($failedIdle.Count -gt 0) { throw 'ACTIVE_FARM_REPEATED_FAILED_NAVIGATION_IDLE' }
	$useful = @($ordinary | Where-Object { ($_.moving -ceq 'true') -or ($_.attacking -ceq 'true') }).Count -gt 0
	$recovery = @($ordinary | Group-Object profileId | Where-Object { @($_.Group | ForEach-Object { "$($_.npcId)@$($_.anchor)" } | Select-Object -Unique).Count -gt 1 }).Count -gt 0
	if (-not ($useful -or $recovery)) { throw 'ORDINARY_NATIVE_LIFE_OR_BOUNDED_RECOVERY_NOT_OBSERVED' }
	Write-Host "CONNECTED PASS: profile=$profileId object=$objectId approachMs=$($latency.ElapsedMilliseconds) samePlayer=True nativeLife=$useful alternateRecovery=$recovery eligibleUnique=$eligibleProfiles coverage=$coverage"
}
catch
{
	$failure = $_.Exception.Message
	Write-Warning "CONNECTED FAILED: $failure"
}
finally
{
	$cleanup = $true
	$deadline = [DateTime]::UtcNow.AddSeconds(45)
	if (($null -ne $origin) -and (-not $uncertain))
	{
		try
		{
			$result = Invoke-Proof 'TELEPORT_SELF' @{ x = $origin.x; y = $origin.y; z = $origin.z; instanceId = $origin.instanceId }
			if ($result.status -cne 'ACCEPTED') { throw 'ORIGIN_RETURN_REJECTED' }
			Start-Sleep -Seconds 2
			$result = Invoke-Proof 'STATUS'
			$restored = ($result.status -ceq 'SUCCEEDED') -and ($result.after.identityOwner -ceq 'REAL_LOGIN') -and ($result.after.worldPresent -ceq 'true') -and ([int]$result.after.instanceId -eq $origin.instanceId) -and ([Math]::Abs([int]$result.after.x - $origin.x) -le 64) -and ([Math]::Abs([int]$result.after.y - $origin.y) -le 64) -and ([Math]::Abs([int]$result.after.z - $origin.z) -le 120)
			if (-not $restored) { throw 'ORIGIN_RETURN_NOT_CONFIRMED' }
			Write-Host 'ORIGIN RESTORED: REAL_LOGIN confirmed'
		}
		catch { $failure = "ORIGIN_RETURN:$($_.Exception.Message)"; Write-Warning $failure }
	}
	Save-Tsv $rows 'M1_CONNECTED_WORLD.tsv'
	Save-Tsv $census 'M1_VISIBLE_LIFE_CENSUS.tsv'
	[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'M1_CONNECTED_RESULT.txt'), "runId=$runId`nfailed=$failure`nrestored=$restored`ncoverage=$coverage`nlocal=$firstLocalUtc`nmaterialized=$firstMaterializedUtc`nvisible=$firstVisibleUtc`n", [Text.UTF8Encoding]::new($false))
	& (Join-Path $module 'tools/phantom-local-play/Stop-LocalPlayPilot.ps1') | Out-Null
}
if ($failure -or (-not $restored)) { throw $(if ($failure) { $failure } else { 'ORIGIN_RETURN_NOT_CONFIRMED' }) }
