[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '../Run-M1RuntimeHandoff.ps1')

function Assert-True([bool] $condition, [string] $reason)
{
	if (-not $condition) { throw "OFFLINE_ASSERT:$reason" }
}

function Assert-Throws([scriptblock] $body, [string] $fragment)
{
	$failure = $null
	try { & $body | Out-Null }
	catch { $failure = $_.Exception.Message }
	if (($null -eq $failure) -or ($failure -notlike "*$fragment*")) { throw "EXPECTED_FAILURE:$fragment;actual=$failure" }
}

$script:stopCalls = 0
$script:transport = { param($op, $operationArgs, $id) if ($op -eq 'STATUS') { return [pscustomobject]@{ status = 'REJECTED'; reason = 'OFFLINE_NO_LOGIN' } }; return [pscustomobject]@{ status = 'ACCEPTED' } }
$script:stopPilot = { $script:stopCalls++; $script:pilotRunning = $false; return [pscustomobject]@{ state = 'STOPPED' } }
$script:utcNow = { [DateTimeOffset]::Parse('2026-09-29T12:00:00Z') }
$script:elapsed = { 0L }
$script:delay = { param($ms) }
Assert-Throws { Invoke-M1Run } 'M1_CLOSEOUT_INCOMPLETE'
Assert-True ($script:stopCalls -eq 1) 'STOP_AFTER_INITIAL_FAILURE'
Assert-True ((Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw) -match 'primaryFailure=NO_CONSENTED_REAL_LOGIN') 'PRIMARY_FAILURE_RETAINED'

$script:transport = {
	param($op, $operationArgs, $id)
	if ($op -eq 'STATUS')
	{
		$script:rows.Add([pscustomobject]@{ phase = 'OFFLINE_IO_NEGATIVE' })
		$script:evidenceRoot = Join-Path $PSScriptRoot 'Test-M1ObserverOffline.ps1'
		return [pscustomobject]@{ status = 'REJECTED'; reason = 'OFFLINE_NO_LOGIN' }
	}
	return [pscustomobject]@{ status = 'ACCEPTED' }
}
Assert-Throws { Invoke-M1Run } 'EVIDENCE_WRITE'
Assert-True ($script:stopCalls -eq 2) 'STOP_AFTER_TSV_FAILURE'

$script:teleportCalls = 0
$script:transport = { param($op, $operationArgs, $id) if ($op -eq 'TELEPORT_SELF') { $script:teleportCalls++ }; return [pscustomobject]@{ status = 'UNCERTAIN' } }
Assert-Throws { Invoke-M1Run } 'UNCERTAIN:STATUS'
Assert-True ($script:stopCalls -eq 3) 'STOP_AFTER_UNCERTAIN'
Assert-True ($script:teleportCalls -eq 0) 'NO_BLIND_RESTORE_AFTER_UNCERTAIN'

$stamp = [DateTimeOffset]::Parse('2026-09-29T12:00:00+03:00')
Assert-True ((Required-Utc ([pscustomobject]@{ date = $stamp }) 'date').Offset -eq [TimeSpan]::Zero) 'DATETIME_OFFSET_UTC'
Assert-True ((Required-Utc ([pscustomobject]@{ date = $stamp.UtcDateTime }) 'date').Offset -eq [TimeSpan]::Zero) 'DATETIME_UTC'
Assert-True ((Required-Utc ([pscustomobject]@{ date = '2026-09-29T09:00:00Z' }) 'date').Hour -eq 9) 'ISO_UTC'
Assert-Throws { Required-Utc ([pscustomobject]@{ date = $null }) 'date' } 'INVALID_REQUIRED_UTC'
Assert-Throws { Required-Int ([pscustomobject]@{ x = $null }) 'x' } 'INVALID_REQUIRED_INT'

$script:cleanup = $false; $script:requestCount = 0; $script:profileId = 545L; $script:selectionKind = 'STORED_START'
$script:rows.Clear(); $script:census.Clear(); $script:continuityLocked = $false
$script:firstLocalUtc = $null; $script:firstCouldKnowUtc = $null; $script:firstClientVisibleUtc = $null; $script:firstMaterializedUtc = $null
$script:prewarmTimingUnproven = $false; $script:prewarmMaterialized = $false; $script:approachMoveConfirmed = $true
$script:probes = 0
$script:transport = {
	param($op, $operationArgs, $id)
	if ($op -ne 'SNAPSHOT_M1_ENVELOPE') { throw "UNEXPECTED_OFFLINE_OP:$op" }
	$script:probes++
	$source = if ($script:probes -eq 1) { 'TRANSITION' } else { 'LIVE' }
	$visible = if ($script:probes -ge 4) { 'true' } else { 'false' }
	$epoch = if ($script:probes -ge 5) { '901' } else { '900' }
	return [pscustomobject]@{
		status = 'SUCCEEDED'; endUtc = '2026-09-29T09:00:00Z'
		after = [pscustomobject]@{ identityOwner = 'REAL_LOGIN'; x = 100; y = 100; z = 0 }
		candidate = [pscustomobject]@{ profileId = '545'; objectId = '777'; materializedAtNanos = $epoch; selectionKind = 'STORED_START'; positionSource = $source; committedSequence = '1'; observedX = '200'; observedY = '200'; observedZ = '0'; worldPresent = 'true'; regionCanKnow = 'true'; clientVisible = $visible; distance2D = '150'; localityCurrent = 'true'; nativeMoving = 'false'; nativeTargetMonsterAlive = 'false' }
	}
}
$first = Capture 'APPROACH'
Assert-True ($script:probes -eq 2) 'TRANSITION_REVALIDATED'
Assert-True ($null -eq $script:firstClientVisibleUtc) 'COULD_KNOW_NOT_VISIBLE'
Assert-True (-not (Contact $first)) 'CONTACT_REQUIRES_VISIBILITY'
Assert-True ($script:prewarmTimingUnproven) 'BIRTH_TIMING_NOT_INVENTED'
$null = Capture 'APPROACH'
$visible = Capture 'APPROACH'
Assert-True (Contact $visible) 'CONTACT_WITH_CLIENT_VISIBILITY'
Assert-Throws { Capture 'APPROACH' } 'CONTINUITY_PLAYER_REMATERIALIZED'

$planned = [pscustomobject]@{ positionSource = 'LIVE'; committedSequence = '1'; observedX = '200'; observedY = '200' }
$near = [pscustomobject]@{ positionSource = 'LIVE'; committedSequence = '2'; observedX = '250'; observedY = '250' }
$far = [pscustomobject]@{ positionSource = 'LIVE'; committedSequence = '2'; observedX = '500'; observedY = '500' }
Assert-True (-not (Route-Stale $planned $near)) 'SMALL_LIVE_DRIFT_REUSES_ROUTE'
Assert-True (Route-Stale $planned $far) 'LARGE_LIVE_DRIFT_REPLANS'
Assert-True (Route-Stale $planned ([pscustomobject]@{ positionSource = 'COMMITTED'; committedSequence = '2'; observedX = '200'; observedY = '200' })) 'SOURCE_CHANGE_REPLANS'

# Six transient LIVE failures consume the existing bounded replan budget.
$script:clockMs = 0L; $script:failedPlans = 0; $script:cleanup = $false; $script:requestCount = 0
$script:approachRouteFailures = [Collections.Generic.List[string]]::new()
$script:profileId = 545L; $script:selectionKind = 'STORED_START'; $script:continuityLocked = $false
$script:rows.Clear(); $script:census.Clear()
$script:firstLocalUtc = $null; $script:firstMaterializedUtc = $null; $script:firstClientVisibleUtc = $null; $script:firstCouldKnowUtc = $null
$script:utcNow = { ([DateTimeOffset]::Parse('2026-09-29T09:00:00Z')).AddMilliseconds($script:clockMs) }
$script:elapsed = { $script:clockMs }
$script:delay = { param($ms) $script:clockMs += $ms }
$script:transport = {
	param($op, $operationArgs, $id)
	if ($op -eq 'PREPARE_M1_ENVELOPE') { $script:failedPlans++; return [pscustomobject]@{ status = 'REJECTED'; reason = 'PATHFIND_NULL' } }
	if ($op -ne 'SNAPSHOT_M1_ENVELOPE') { throw "UNEXPECTED_BOUNDED_OP:$op" }
	return [pscustomobject]@{
		status = 'SUCCEEDED'; endUtc = (& $script:utcNow).ToString('o')
		after = [pscustomobject]@{ identityOwner = 'REAL_LOGIN'; x = '-3000'; y = '0'; z = '0'; instanceId = '0' }
		candidate = [pscustomobject]@{ profileId = '545'; selectionKind = 'STORED_START'; positionSource = 'LIVE'; committedSequence = '1'; observedX = '0'; observedY = '0'; observedZ = '0'; objectId = '777'; materializedAtNanos = '900'; worldPresent = 'true'; clientVisible = 'false'; localityCurrent = 'false'; distance2D = '3000' }
	}
}
Assert-Throws { Approach-Tracked } 'APPROACH_REPLAN_BUDGET_EXHAUSTED:PATHFIND_NULL'
Assert-True ($script:failedPlans -eq 6) 'LIVE_REPLAN_BUDGET_EXACT'
Assert-True ($script:clockMs -lt 120000) 'LIVE_REPLAN_BUDGET_BEFORE_DEADLINE'

# Exercise the complete runner with virtual time and a naturally materializing moving target.
$script:fakeOwner = 'REAL_LOGIN'
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false
$script:liveRouteFailures = 0; $script:liveRelocated = $false
$script:pilotRunning = $true
$script:neverMaterialize = $false
$script:utcNow = { ([DateTimeOffset]::Parse('2026-09-29T09:00:00Z')).AddMilliseconds($script:clockMs) }
$script:elapsed = { $script:clockMs }
$script:delay = { param($ms) $script:clockMs += $ms }
$script:gameMetrics = { [pscustomobject]@{ pid = 123; cpuMillis = $script:clockMs; privateBytes = 1000000; pilotState = $(if ($script:pilotRunning) { 'RUNNING' } else { 'ARMED_IDLE' }) } }
$script:transport = {
	param($op, $operationArgs, $id)
	$now = (& $script:utcNow).ToString('o')
	$actor = [pscustomobject]@{ identityOwner = $script:fakeOwner; clientIdentity = 'none'; worldPresent = 'true'; teleporting = 'false'; moving = 'false'; x = [string]$script:actorX; y = '0'; z = '0'; instanceId = '0' }
	if ($op -eq 'STATUS') { return [pscustomobject]@{ status = 'SUCCEEDED'; after = $actor; candidate = [pscustomobject]@{ originX = '1000'; originY = '0'; originZ = '0'; originInstanceId = '0' } } }
	if ($op -eq 'STOP_MOVE') { return [pscustomobject]@{ status = 'SUCCEEDED' } }
	if ($op -eq 'MOVE_SELF') { $script:actorX = [int]$operationArgs.x; return [pscustomobject]@{ status = 'ACCEPTED' } }
	if ($op -eq 'TELEPORT_SELF') { $script:actorX = [int]$operationArgs.x; return [pscustomobject]@{ status = 'ACCEPTED' } }
	if ($op -eq 'PREPARE_M1_ENVELOPE')
	{
		if ($operationArgs.stage -eq 'INITIAL')
		{
			$script:actorX = -3000
			return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; selectionKind = 'STORED_START'; committedSequence = '1'; nextBoundary = '2026-09-29T10:00:00Z'; legacySkips = '71:KNOWN_PREFIX_FAIL_CLOSED' } }
		}
		if ($operationArgs.stage -eq 'APPROACH')
		{
			if ($script:materialized -and ($script:liveRouteFailures -eq 0))
			{
				$script:liveRouteFailures++
				return [pscustomobject]@{ status = 'REJECTED'; reason = 'PATHFIND_NULL' }
			}
			$route = if ($script:materialized) { "$script:actorX,0,0;$script:targetX,0,0" } else { "$script:actorX,0,0;-2000,0,0;$script:targetX,0,0" }
			return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; route = $route; observedX = [string]$script:targetX; observedY = '0'; positionSource = $(if ($script:materialized) { 'LIVE' } else { 'COMMITTED' }); committedSequence = '1' } }
		}
		if ($operationArgs.stage -eq 'LEAVE') { return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; destinationX = '-5000'; destinationY = '0'; destinationZ = '0'; destinationInstanceId = '0'; m1Token = 'leave' } } }
		if ($operationArgs.stage -eq 'RETURN') { return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; destinationX = [string]($script:targetX - 200); destinationY = '0'; destinationZ = '0'; destinationInstanceId = '0'; m1Token = 'return' } } }
	}
	if ($op -ne 'SNAPSHOT_M1_ENVELOPE') { throw "UNEXPECTED_OFFLINE_OP:$op" }
	if ((-not $script:neverMaterialize) -and (-not $script:materialized) -and ([Math]::Abs($script:actorX - $script:targetX) -le 1800))
	{
		$script:materialized = $true
		$script:targetX = 300
		$script:liveRelocated = $true
	}
	if ($script:contact) { $script:targetX += 40 }
	$distance = [Math]::Abs($script:actorX - $script:targetX)
	$know = $distance -le 800
	$prewarm = $distance -le 2000
	$visible = $script:materialized -and $know
	$data = @{
		profileId = '545'; selectionKind = 'STORED_START'; positionSource = $(if ($script:materialized) { 'LIVE' } else { 'COMMITTED' })
		committedSequence = '1'; committedX = '0'; committedY = '0'; committedZ = '0'
		observedX = [string]$script:targetX; observedY = '0'; observedZ = '0'
		objectId = $(if ($script:materialized) { '777' } else { '0' }); materializedAtNanos = $(if ($script:materialized) { '900' } else { '0' })
		worldPresent = $script:materialized.ToString().ToLowerInvariant(); regionCanKnow = $know.ToString().ToLowerInvariant(); humanPrewarm = $prewarm.ToString().ToLowerInvariant()
		clientVisible = $visible.ToString().ToLowerInvariant(); localityCurrent = ($distance -le 2200).ToString().ToLowerInvariant(); distance2D = [string]$distance
		nativeMoving = 'true'; nativeAttacking = 'true'; nativeTargetMonsterAlive = 'true'
	}
	if ($operationArgs.includeCensus -eq 'true')
	{
		$data.censusCount = '4'; $data.censusEligible = '4'; $data.censusNextProfileId = '0'
		for ($n = 1; $n -le 4; $n++)
		{
			$data["census${n}.profileId"] = [string](544 + $n)
			$data["census${n}.eligible"] = 'true'
			$data["census${n}.targetMonsterAlive"] = 'true'
			$data["census${n}.attacking"] = 'true'
			$data["census${n}.idleReason"] = 'NONE'
		}
	}
	return [pscustomobject]@{ status = 'SUCCEEDED'; endUtc = $now; after = ([pscustomobject]@{ identityOwner = $script:fakeOwner; clientIdentity = 'none'; worldPresent = 'true'; teleporting = 'false'; moving = 'false'; x = [string]$script:actorX; y = '0'; z = '0'; instanceId = '0' }); candidate = [pscustomobject]$data }
}
Invoke-M1Run
$result = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($result -match 'result=GREEN') 'COMPLETE_FAKE_SCENE_GREEN'
Assert-True ($result -match 'legacySkips=71:KNOWN_PREFIX_FAIL_CLOSED') 'KNOWN_PREFIX_EVIDENCE_RETAINED'
Assert-True ($result -match 'NEW_MATERIALIZATION=PASS') 'STORED_PREWARM_PROVEN'
Assert-True ($result -match 'SOFT_RETURN=PASS') 'SAME_PLAYER_RETURN_PROVEN'
Assert-True ($result -match 'approachRouteFailures=PATHFIND_NULL') 'TYPED_LIVE_ROUTE_EVIDENCE'
Assert-True ($script:liveRouteFailures -eq 1) 'TRANSIENT_LIVE_NO_PATH_DEFERRED'
Assert-True ($script:liveRelocated) 'LIVE_TARGET_RELOCATED_DURING_APPROACH'
Assert-True ($script:clockMs -ge 55000) 'VIRTUAL_OBSERVATION_AND_ABSENCE_ELAPSED'

# The same phase engine accepts the separate owner and never grades it as real client M1 GREEN.
$script:actorMode = 'Synthetic'; $script:fakeOwner = 'LOCALPLAY_TEST_HUMAN'
$script:syntheticStarts = 0; $script:startActor = { $script:syntheticStarts++ }
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false
$script:liveRouteFailures = 0; $script:liveRelocated = $false; $script:pilotRunning = $true
Invoke-M1Run
$syntheticResult = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($syntheticResult -match 'result=SYNTHETIC_SERVER_GREEN; M1_OPEN; FINAL_CLIENT_REQUIRED') 'SYNTHETIC_IS_NOT_FINAL_M1_GREEN'
Assert-True ($script:syntheticStarts -eq 1) 'EXACT_ONE_SYNTHETIC_START'
Assert-True ($syntheticResult -match 'SOFT_RETURN=PASS') 'SYNTHETIC_SHARED_ENGINE_RETURN'
Assert-True ($syntheticResult -match 'STOP=PASS') 'SYNTHETIC_SHARED_ENGINE_STOP'
$script:validTransport = $script:transport
$script:neverMaterialize = $true; $script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'APPROACH_DEADLINE_EXPIRED'
$capacityResult = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($capacityResult -match 'primaryFailure=APPROACH_DEADLINE_EXPIRED') 'CAPACITY_PRIMARY_RETAINED'
Assert-True ($capacityResult -match 'COHORT=NOT_OBSERVED') 'DIAGNOSTIC_CENSUS_NEVER_GRADES_ACCEPTANCE'
Assert-True (@($script:census | Where-Object { $_.phase -like 'DIAGNOSTIC_APPROACH_FAILURE_*' } | Select-Object -ExpandProperty phase -Unique).Count -eq 3) 'FAILED_APPROACH_THREE_DIAGNOSTIC_CENSUS'
Assert-True ($capacityResult -match 'RESTORE=PASS' -and $capacityResult -match 'STOP=PASS') 'CAPACITY_CLEANUP_RETAINED'
$script:neverMaterialize = $false
$script:transport = {
	param($op, $operationArgs, $id)
	if (($op -eq 'PREPARE_M1_ENVELOPE') -and ($operationArgs.stage -eq 'INITIAL')) { return [pscustomobject]@{ status = 'REJECTED'; reason = 'UNKNOWN_INCONSISTENT:5'; candidate = [pscustomobject]@{ legacySkips = '71:KNOWN_PREFIX_FAIL_CLOSED' } } }
	if ($op -eq 'MOVE_SELF') { throw 'UNKNOWN_CANDIDATE_MOVED' }
	return & $script:validTransport $op $operationArgs $id
}
$script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'PREPARE_INITIAL_REJECTED:UNKNOWN_INCONSISTENT:5'
$unknownResult = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($unknownResult -match 'legacySkips=71:KNOWN_PREFIX_FAIL_CLOSED') 'REJECTED_INITIAL_PREFIX_EVIDENCE_RETAINED'
Assert-True ($unknownResult -notmatch '5:KNOWN_PREFIX_FAIL_CLOSED') 'UNKNOWN_NEVER_SKIPPED'
$script:transport = $script:validTransport
$script:fakeOwner = 'REAL_LOGIN'; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'NO_OWNED_SYNTHETIC_HUMAN'
Write-Output 'M1_OBSERVER_OFFLINE_PASS'
