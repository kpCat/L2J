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

# Exercise the complete runner with virtual time and a naturally materializing moving target.
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false
$script:pilotRunning = $true
$script:utcNow = { ([DateTimeOffset]::Parse('2026-09-29T09:00:00Z')).AddMilliseconds($script:clockMs) }
$script:elapsed = { $script:clockMs }
$script:delay = { param($ms) $script:clockMs += $ms }
$script:gameMetrics = { [pscustomobject]@{ pid = 123; cpuMillis = $script:clockMs; privateBytes = 1000000; pilotState = $(if ($script:pilotRunning) { 'RUNNING' } else { 'ARMED_IDLE' }) } }
$script:transport = {
	param($op, $operationArgs, $id)
	$now = (& $script:utcNow).ToString('o')
	$actor = [pscustomobject]@{ identityOwner = 'REAL_LOGIN'; worldPresent = 'true'; teleporting = 'false'; moving = 'false'; x = [string]$script:actorX; y = '0'; z = '0'; instanceId = '0' }
	if ($op -eq 'STATUS') { return [pscustomobject]@{ status = 'SUCCEEDED'; after = $actor; candidate = [pscustomobject]@{ originX = '1000'; originY = '0'; originZ = '0'; originInstanceId = '0' } } }
	if ($op -eq 'STOP_MOVE') { return [pscustomobject]@{ status = 'SUCCEEDED' } }
	if ($op -eq 'MOVE_SELF') { $script:actorX = [int]$operationArgs.x; return [pscustomobject]@{ status = 'ACCEPTED' } }
	if ($op -eq 'TELEPORT_SELF') { $script:actorX = [int]$operationArgs.x; return [pscustomobject]@{ status = 'ACCEPTED' } }
	if ($op -eq 'PREPARE_M1_ENVELOPE')
	{
		if ($operationArgs.stage -eq 'INITIAL')
		{
			$script:actorX = -3000
			return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; selectionKind = 'STORED_START'; committedSequence = '1'; nextBoundary = '2026-09-29T10:00:00Z' } }
		}
		if ($operationArgs.stage -eq 'APPROACH') { return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; route = "$script:actorX,0,0;$script:targetX,0,0"; observedX = [string]$script:targetX; observedY = '0'; positionSource = $(if ($script:materialized) { 'LIVE' } else { 'COMMITTED' }); committedSequence = '1' } } }
		if ($operationArgs.stage -eq 'LEAVE') { return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; destinationX = '-5000'; destinationY = '0'; destinationZ = '0'; destinationInstanceId = '0'; m1Token = 'leave' } } }
		if ($operationArgs.stage -eq 'RETURN') { return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; destinationX = [string]($script:targetX - 200); destinationY = '0'; destinationZ = '0'; destinationInstanceId = '0'; m1Token = 'return' } } }
	}
	if ($op -ne 'SNAPSHOT_M1_ENVELOPE') { throw "UNEXPECTED_OFFLINE_OP:$op" }
	if ((-not $script:materialized) -and ([Math]::Abs($script:actorX - $script:targetX) -le 1800)) { $script:materialized = $true }
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
	return [pscustomobject]@{ status = 'SUCCEEDED'; endUtc = $now; after = ([pscustomobject]@{ identityOwner = 'REAL_LOGIN'; worldPresent = 'true'; teleporting = 'false'; moving = 'false'; x = [string]$script:actorX; y = '0'; z = '0'; instanceId = '0' }); candidate = [pscustomobject]$data }
}
Invoke-M1Run
$result = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($result -match 'result=GREEN') 'COMPLETE_FAKE_SCENE_GREEN'
Assert-True ($result -match 'NEW_MATERIALIZATION=PASS') 'STORED_PREWARM_PROVEN'
Assert-True ($result -match 'SOFT_RETURN=PASS') 'SAME_PLAYER_RETURN_PROVEN'
Assert-True ($script:clockMs -ge 55000) 'VIRTUAL_OBSERVATION_AND_ABSENCE_ELAPSED'
Write-Output 'M1_OBSERVER_OFFLINE_PASS'
