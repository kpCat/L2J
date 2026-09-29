[CmdletBinding()]
param([string] $ModuleRoot = '')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$script:module = if ($ModuleRoot) { [IO.Path]::GetFullPath($ModuleRoot) } else { [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..')) }
$script:invoke = Join-Path $script:module 'tools/phantom-local-play/Invoke-LocalPlayPilot.ps1'
$script:stopTool = Join-Path $script:module 'tools/phantom-local-play/Stop-LocalPlayPilot.ps1'
$script:transport = $null
$script:utcNow = $null
$script:elapsed = $null
$script:delay = $null
$script:stopPilot = $null
$script:gameMetrics = $null

function Now-Utc { return (& $script:utcNow) }
function Elapsed-Ms { return [long](& $script:elapsed) }
function Pause-Ms([int] $milliseconds) { & $script:delay $milliseconds }

# Existing Pilot transport is replaceable only when the runner is dot-sourced by its offline test.
function Invoke-Proof([string] $operation, [hashtable] $arguments = @{})
{
	if ($script:cleanup -and ($operation -cnotin @('STOP_MOVE', 'TELEPORT_SELF', 'STATUS'))) { throw 'CLEANUP_OPERATION_REJECTED' }
	if ((-not $script:cleanup) -and ((Elapsed-Ms) -ge 480000)) { throw 'ACCEPTANCE_DEADLINE_EXPIRED' }
	if ($script:cleanup -and ($script:cleanupClock.ElapsedMilliseconds -ge 45000)) { throw 'CLEANUP_DEADLINE_EXPIRED' }
	$limit = if ($script:cleanup) { 400 } else { 368 }
	if ($script:requestCount -ge $limit) { throw 'MAILBOX_BUDGET_EXHAUSTED' }
	$script:requestCount++
	try { $result = & $script:transport $operation $arguments $script:runId }
	catch { if ($_.Exception.Message -match 'UNCERTAIN') { $script:uncertain = $true }; throw }
	if (-not $result) { throw "PILOT_ACTION_FAILED:$operation" }
	if ($result -is [string]) { $result = $result | ConvertFrom-Json }
	if (($result.status -eq 'UNCERTAIN') -or ((Read-Field $result 'harnessResult') -eq 'UNCERTAIN')) { $script:uncertain = $true; throw "UNCERTAIN:$operation" }
	return $result
}

function Read-Field($object, [string] $name, $fallback = $null)
{
	if (($null -eq $object) -or ($null -eq $object.PSObject.Properties[$name]) -or ($null -eq $object.$name)) { return $fallback }
	if (($object.$name -is [DateTime]) -or ($object.$name -is [DateTimeOffset])) { return $object.$name.ToUniversalTime().ToString('o') }
	return [string] $object.$name
}

function Required-Int($object, [string] $name)
{
	$value = Read-Field $object $name
	$parsed = 0
	if (($null -eq $value) -or (-not [int]::TryParse([string]$value, [ref]$parsed))) { throw "INVALID_REQUIRED_INT:$name" }
	return $parsed
}

function Required-Utc($object, [string] $name)
{
	$value = Read-Field $object $name
	$parsed = [DateTimeOffset]::MinValue
	if (($null -eq $value) -or (-not [DateTimeOffset]::TryParse([string]$value, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeUniversal, [ref]$parsed))) { throw "INVALID_REQUIRED_UTC:$name" }
	return $parsed.ToUniversalTime()
}

function Capture([string] $phase, [bool] $includeCensus = $false, [string] $transition = '')
{
	$result = $null
	for ($retry = 0; $retry -le 3; $retry++)
	{
		$result = Invoke-Proof 'SNAPSHOT_M1_ENVELOPE' @{ includeCensus = $(if ($includeCensus) { 'true' } else { 'false' }) }
		if (($result.status -ceq 'SUCCEEDED') -and ($null -ne $result.PSObject.Properties['candidate']) -and ((Read-Field $result.candidate 'positionSource') -cnotin @('TRANSITION', 'UNAVAILABLE'))) { break }
		if (($result.status -cne 'SUCCEEDED') -and ($result.reason -cnotin @('M1_TARGET_TRANSITION', 'ENVELOPE_TARGET_UNAVAILABLE'))) { throw "ENVELOPE_SNAPSHOT_FAILED:$($result.reason)" }
		if ($retry -eq 3) { throw "M1_TARGET_TRANSITION_TIMEOUT:$phase" }
		Pause-Ms 1000
	}
	$actor = $result.after
	$target = $result.candidate
	if (((Read-Field $actor 'identityOwner') -cne 'REAL_LOGIN') -or ((Required-Int $target 'profileId') -ne $script:profileId)) { throw 'ACTOR_OR_TARGET_IDENTITY_CHANGED' }
	if ((Read-Field $target 'selectionKind') -cne $script:selectionKind) { throw 'SELECTION_KIND_CHANGED' }
	$utc = (Required-Utc $result 'endUtc').ToString('o')
	$record = [ordered]@{ utc = $utc; phase = $phase; transition = $transition; humanX = (Required-Int $actor 'x'); humanY = (Required-Int $actor 'y'); humanZ = (Required-Int $actor 'z') }
	foreach ($field in @('profileId', 'objectId', 'materializedAtNanos', 'selectionKind', 'positionSource', 'observationChanged', 'committedSequence', 'committedX', 'committedY', 'committedZ', 'observedX', 'observedY', 'observedZ', 'liveX', 'liveY', 'liveZ', 'worldPresent', 'snapshotWorldPresent', 'regionCanKnow', 'humanPrewarm', 'clientVisible', 'distance2D', 'localityCurrent', 'materializationState', 'nativeMoving', 'nativeAttacking', 'nativeCasting', 'nativeAutoPlay', 'nativeTargetObjectId', 'nativeTargetMonsterAlive', 'activityState', 'busyReason', 'retentionPins', 'readinessReason', 'historicalStatus', 'ordinaryQueued', 'urgentQueued', 'workerState', 'physicalCount', 'runnableOrdinary', 'travelReason', 'travelFailureReason', 'censusCount', 'censusEligible')) { $record[$field] = Read-Field $target $field }
	foreach ($field in @('signalDelivery', 'localityOverflow', 'activeProfile', 'currentStage', 'enqueueAgeMillis', 'lastProgressAgeMillis', 'nextWakeMillis', 'nextRetryMillis', 'historicalRequestId', 'innerCursorMinute', 'innerTargetMinute', 'innerRevision', 'admittedPreparationCount', 'waitingPreparationCount', 'focusId', 'focusAgeMillis', 'oldestWaitMillis', 'reservedPaused', 'committedIntervals', 'elapsedBatchMillis', 'participants', 'resizePending', 'retiredReserve', 'resizePhase')) { $record[$field] = Read-Field $target $field }
	if ($includeCensus) { Read-Census $target $record }
	$script:rows.Add([pscustomobject]$record)
	$priorLocal = $script:firstLocalUtc
	if (($null -eq $script:firstLocalUtc) -and ($record.localityCurrent -ceq 'true')) { $script:firstLocalUtc = $utc }
	if (($null -eq $script:firstCouldKnowUtc) -and ($record.regionCanKnow -ceq 'true')) { $script:firstCouldKnowUtc = $utc }
	if (($null -eq $script:firstClientVisibleUtc) -and ($record.clientVisible -ceq 'true')) { $script:firstClientVisibleUtc = $utc }
	if (($null -eq $script:firstMaterializedUtc) -and ($record.worldPresent -ceq 'true'))
	{
		$script:firstMaterializedUtc = $utc
		if (($script:selectionKind -ceq 'STORED_START') -and ($null -ne $priorLocal) -and ($record.regionCanKnow -ceq 'false') -and ($record.clientVisible -ceq 'false')) { $script:prewarmMaterialized = $true }
		else { $script:prewarmTimingUnproven = $true }
	}
	if ($script:continuityLocked)
	{
		if (($record.worldPresent -cne 'true') -or ((Required-Int $target 'objectId') -ne $script:lockedObjectId)) { throw 'CONTINUITY_PLAYER_LOST' }
		if ((Read-Field $target 'materializedAtNanos') -cne $script:lockedEpoch) { throw 'CONTINUITY_PLAYER_REMATERIALIZED' }
	}
	elseif (($record.worldPresent -ceq 'true') -and (($record.localityCurrent -ceq 'true') -or ($record.clientVisible -ceq 'true')))
	{
		$script:continuityLocked = $true
		$script:lockedObjectId = Required-Int $target 'objectId'
		$script:lockedEpoch = Read-Field $target 'materializedAtNanos'
		if (($null -eq $script:lockedEpoch) -or ($script:lockedEpoch -ceq '0')) { throw 'INVALID_MATERIALIZATION_EPOCH' }
	}
	if ($phase -ceq 'OBSERVE')
	{
		if ($record.worldPresent -ceq 'true')
		{
			if (($null -ne $script:lastNativePoint) -and ($record.nativeMoving -ceq 'true') -and ([Math]::Sqrt([Math]::Pow((Required-Int $target 'observedX') - $script:lastNativePoint.x, 2) + [Math]::Pow((Required-Int $target 'observedY') - $script:lastNativePoint.y, 2)) -ge 32)) { $script:nativeDisplacement = $true }
			$script:lastNativePoint = [pscustomobject]@{ x = (Required-Int $target 'observedX'); y = (Required-Int $target 'observedY') }
			if (($record.nativeTargetMonsterAlive -ceq 'true') -and (($record.nativeAttacking -ceq 'true') -or ($record.nativeCasting -ceq 'true'))) { $script:selectedNativeFarm = $true }
		}
	}
	return $result
}

function Read-Census($firstPage, $record)
{
	$page = $firstPage
	$total = 0; $eligible = 0; $cursor = 0L
	for ($pageNumber = 0; $pageNumber -lt 6; $pageNumber++)
	{
		$count = Required-Int $page 'censusCount'
		$total += $count; $eligible += Required-Int $page 'censusEligible'
		for ($sample = 1; $sample -le $count; $sample++)
		{
			$entry = [ordered]@{ utc = $record.utc; phase = $record.phase }
			foreach ($field in @('profileId', 'objectId', 'level', 'npcId', 'anchor', 'goalStatus', 'runtimeReason', 'travelReason', 'travelFailureReason', 'travelFailureSequence', 'dead', 'moving', 'attacking', 'casting', 'autoPlay', 'party', 'store', 'intention', 'shortTargets', 'longTargets', 'x', 'y', 'z', 'targetObjectId', 'targetMonsterAlive', 'eligible', 'idleReason')) { $entry[$field] = Read-Field $page "census${sample}.$field" }
			$script:census.Add([pscustomobject]$entry)
		}
		$cursorText = Read-Field $page 'censusNextProfileId' '0'
		if (-not [long]::TryParse([string]$cursorText, [ref]$cursor)) { throw 'INVALID_CENSUS_CURSOR' }
		if ($cursor -eq 0) { break }
		if ($pageNumber -eq 5) { $script:censusComplete = $false; break }
		$pageResult = Invoke-Proof 'SNAPSHOT_M1_ENVELOPE' @{ includeCensus = 'true'; censusAfterProfileId = [string]$cursor }
		if ($pageResult.status -cne 'SUCCEEDED') { throw "CENSUS_PAGE_FAILED:$($pageResult.reason)" }
		$page = $pageResult.candidate
	}
	$record.censusCount = [string]$total
	$record.censusEligible = [string]$eligible
}

function Wait-For([string] $phase, [scriptblock] $predicate, [int] $seconds)
{
	for ($attempt = 0; $attempt -lt $seconds; $attempt++)
	{
		$sample = Capture $phase
		if (& $predicate $sample) { return $sample }
		Pause-Ms 1000
	}
	throw "PHASE_TIMEOUT:$phase"
}

function Teleport-Ticket($prepared, [string] $phase)
{
	$x = Required-Int $prepared 'destinationX'; $y = Required-Int $prepared 'destinationY'; $z = Required-Int $prepared 'destinationZ'; $instance = Required-Int $prepared 'destinationInstanceId'
	$token = Read-Field $prepared 'm1Token'
	if ([string]::IsNullOrWhiteSpace($token)) { throw 'INVALID_M1_TICKET' }
	$result = Invoke-Proof 'TELEPORT_SELF' @{ x = $x; y = $y; z = $z; instanceId = $instance; m1Token = $token }
	if ($result.status -cne 'ACCEPTED') { throw "TELEPORT_REJECTED:${phase}:$($result.reason)" }
	return Wait-For $phase { param($s) ($s.after.teleporting -cne 'true') -and ($s.after.worldPresent -ceq 'true') -and ((Required-Int $s.after 'instanceId') -eq $instance) -and ([Math]::Abs((Required-Int $s.after 'x') - $x) -le 64) -and ([Math]::Abs((Required-Int $s.after 'y') - $y) -le 64) -and ([Math]::Abs((Required-Int $s.after 'z') - $z) -le 120) } 10
}

function Prepare-Phase([string] $phase)
{
	$result = Invoke-Proof 'PREPARE_M1_ENVELOPE' @{ stage = $phase; profileId = [string]$script:profileId }
	if (($result.status -cne 'ACCEPTED') -or ((Required-Int $result.candidate 'profileId') -ne $script:profileId)) { throw "PREPARE_${phase}_REJECTED:$($result.reason)" }
	return $result.candidate
}

function Route-Points($prepared)
{
	$text = Read-Field $prepared 'route'
	if ([string]::IsNullOrWhiteSpace($text)) { throw 'INVALID_APPROACH_ROUTE' }
	$points = New-Object System.Collections.Generic.List[object]
	foreach ($part in $text.Split(';'))
	{
		$xyz = $part.Split(',')
		if ($xyz.Count -ne 3) { throw 'INVALID_APPROACH_ROUTE' }
		$points.Add([pscustomobject]@{ x = [int]::Parse($xyz[0]); y = [int]::Parse($xyz[1]); z = [int]::Parse($xyz[2]) })
	}
	if (($points.Count -lt 2) -or ($points.Count -gt 64)) { throw 'INVALID_APPROACH_ROUTE' }
	return $points.ToArray()
}

function Route-Stale($planned, $current)
{
	if ((Read-Field $planned 'positionSource') -cne (Read-Field $current 'positionSource')) { return $true }
	if ((Read-Field $planned 'committedSequence') -cne (Read-Field $current 'committedSequence') -and (Read-Field $current 'positionSource') -ceq 'COMMITTED') { return $true }
	$x = Required-Int $current 'observedX'; $y = Required-Int $current 'observedY'
	$px = Required-Int $planned 'observedX'; $py = Required-Int $planned 'observedY'
	return ((($x -shr 11) -ne ($px -shr 11)) -or (($y -shr 11) -ne ($py -shr 11)) -or (([Math]::Pow($x - $px, 2) + [Math]::Pow($y - $py, 2)) -gt (256 * 256)))
}

function Contact($sample)
{
	return $script:approachMoveConfirmed -and ($sample.candidate.worldPresent -ceq 'true') -and ($sample.candidate.clientVisible -ceq 'true') -and ((Required-Int $sample.candidate 'distance2D') -le 900)
}

function Approach-Tracked
{
	$start = Elapsed-Ms; $lastPlan = -2000L; $plans = 0; $index = 1; $stalled = 0
	$planned = $null; $points = @(); $script:approachMoveConfirmed = $false
	while (((Elapsed-Ms) - $start) -lt 120000)
	{
		$sample = Capture 'APPROACH'
		if (Contact $sample) { $script:contact = $true; return $sample }
		$needPlan = ($null -eq $planned) -or (Route-Stale $planned $sample.candidate) -or ($index -ge $points.Count)
		if ($needPlan)
		{
			if ($plans -ge 6) { throw 'APPROACH_REPLAN_BUDGET_EXHAUSTED' }
			$remaining = 2000 - ((Elapsed-Ms) - $lastPlan)
			if ($remaining -gt 0) { Pause-Ms ([int]$remaining) }
			$planned = Prepare-Phase 'APPROACH'
			$points = @(Route-Points $planned)
			$index = 1; $plans++; $lastPlan = Elapsed-Ms
			continue
		}
		$hereX = Required-Int $sample.after 'x'; $hereY = Required-Int $sample.after 'y'; $hereZ = Required-Int $sample.after 'z'
		$waypoint = $points[$index]
		$dx = $waypoint.x - $hereX; $dy = $waypoint.y - $hereY; $dz = $waypoint.z - $hereZ
		$distance = [Math]::Sqrt($dx * $dx + $dy * $dy)
		if (($distance -le 32) -and ([Math]::Abs($dz) -le 100)) { $index++; continue }
		$fraction = [Math]::Min(1.0, [Math]::Min(300.0 / [Math]::Max(1.0, $distance), 150.0 / [Math]::Max(1.0, [Math]::Abs($dz))))
		$next = @{ x = [int]($hereX + $dx * $fraction); y = [int]($hereY + $dy * $fraction); z = [int]($hereZ + $dz * $fraction) }
		$move = Invoke-Proof 'MOVE_SELF' $next
		if ($move.status -cne 'ACCEPTED') { throw "NATIVE_APPROACH_REJECTED:$($move.reason)" }
		$progress = $false
		for ($poll = 0; $poll -lt 5; $poll++)
		{
			Pause-Ms 1000
			$after = Capture 'APPROACH'
			$displacement = [Math]::Sqrt([Math]::Pow((Required-Int $after.after 'x') - $hereX, 2) + [Math]::Pow((Required-Int $after.after 'y') - $hereY, 2))
			if ($displacement -ge 16) { $progress = $true; $script:approachMoveConfirmed = $true }
			if (Contact $after) { $script:contact = $true; return $after }
			if (([Math]::Abs((Required-Int $after.after 'x') - $next.x) -le 32) -and ([Math]::Abs((Required-Int $after.after 'y') - $next.y) -le 32)) { break }
			if (($after.after.moving -cne 'true') -and $progress) { break }
		}
		if (-not $progress) { $stalled++; if ($stalled -gt 1) { throw 'APPROACH_NO_NATIVE_DISPLACEMENT' }; $planned = $null }
		else { $stalled = 0 }
	}
	throw 'APPROACH_DEADLINE_EXPIRED'
}

function Save-Tsv($data, [string] $name)
{
	if ($data.Count -eq 0) { return }
	$columns = @($data[0].PSObject.Properties.Name)
	$lines = New-Object System.Collections.Generic.List[string]
	$lines.Add(($columns -join "`t"))
	foreach ($row in $data) { $lines.Add((($columns | ForEach-Object { [string]$row.$_ }) -join "`t")) }
	[IO.File]::WriteAllLines((Join-Path $script:evidenceRoot $name), $lines, [Text.UTF8Encoding]::new($false))
}

function Invoke-M1Run
{
	$script:runId = [guid]::NewGuid().ToString('D')
	$script:evidenceRoot = Join-Path $script:module ('.phantom-local/m1-005-connected-' + $script:runId)
	[IO.Directory]::CreateDirectory($script:evidenceRoot) | Out-Null
	$clock = [Diagnostics.Stopwatch]::StartNew()
	if ($null -eq $script:transport) { $script:transport = { param($op, $operationArgs, $id) & $script:invoke -Operation $op -Arguments $operationArgs -RunId $id } }
	if ($null -eq $script:utcNow) { $script:utcNow = { [DateTimeOffset]::UtcNow } }
	if ($null -eq $script:elapsed) { $script:elapsed = { $clock.ElapsedMilliseconds }.GetNewClosure() }
	if ($null -eq $script:delay) { $script:delay = { param($ms) Start-Sleep -Milliseconds $ms } }
	if ($null -eq $script:stopPilot) { $script:stopPilot = { & $script:stopTool } }
	if ($null -eq $script:gameMetrics) { $script:gameMetrics = { $pilot = & (Join-Path $script:module 'tools/phantom-local-play/Get-LocalPlayPilot.ps1') | ConvertFrom-Json; $game = Get-Process -Id ([int]$pilot.gamePid) -ErrorAction Stop; [pscustomobject]@{ pid = $game.Id; cpuMillis = [long]$game.TotalProcessorTime.TotalMilliseconds; privateBytes = [long]$game.PrivateMemorySize64; pilotState = $pilot.state } } }
	$script:rows = [Collections.Generic.List[object]]::new(); $script:census = [Collections.Generic.List[object]]::new()
	$script:cleanup = $false; $script:cleanupClock = $null; $script:uncertain = $false; $script:requestCount = 0
	$script:profileId = 0L; $script:selectionKind = ''; $script:origin = $null; $script:restored = $false
	$script:firstLocalUtc = $null; $script:firstCouldKnowUtc = $null; $script:firstClientVisibleUtc = $null; $script:firstMaterializedUtc = $null
	$script:prewarmMaterialized = $false; $script:prewarmTimingUnproven = $false; $script:continuityLocked = $false
	$script:lockedObjectId = 0; $script:lockedEpoch = $null; $script:lastNativePoint = $null
	$script:approachMoveConfirmed = $false; $script:contact = $false; $script:nativeDisplacement = $false; $script:selectedNativeFarm = $false
	$script:censusComplete = $true; $script:stopState = 'NOT_CONFIRMED'; $primaryFailure = ''; $cleanupFailures = [Collections.Generic.List[string]]::new()
	$script:startMetrics = $null; $script:endMetrics = $null
	$matrix = [ordered]@{ NEW_MATERIALIZATION = 'NOT_OBSERVED'; CONTACT = 'NOT_OBSERVED'; NATIVE_LIFE = 'NOT_OBSERVED'; COHORT = 'NOT_OBSERVED'; SOFT_RETURN = 'NOT_OBSERVED'; RESTORE = 'NOT_CONFIRMED'; STOP = 'NOT_CONFIRMED' }
	try
	{
		$initial = Invoke-Proof 'STATUS'
		if (($initial.status -cne 'SUCCEEDED') -or ((Read-Field $initial.after 'identityOwner') -cne 'REAL_LOGIN') -or ((Read-Field $initial.after 'worldPresent') -cne 'true')) { throw 'NO_CONSENTED_REAL_LOGIN' }
		$script:startMetrics = & $script:gameMetrics
		if (($null -eq $script:startMetrics) -or ($script:startMetrics.pilotState -cne 'RUNNING')) { throw 'OWNED_GAME_METRICS_UNAVAILABLE' }
		$script:origin = [pscustomobject]@{ x = (Required-Int $initial.candidate 'originX'); y = (Required-Int $initial.candidate 'originY'); z = (Required-Int $initial.candidate 'originZ'); instanceId = (Required-Int $initial.candidate 'originInstanceId') }
		$prepared = Invoke-Proof 'PREPARE_M1_ENVELOPE' @{ stage = 'INITIAL' }
		if ($prepared.status -cne 'ACCEPTED') { throw "PREPARE_INITIAL_REJECTED:$($prepared.reason)" }
		$script:profileId = [long](Required-Int $prepared.candidate 'profileId')
		$script:selectionKind = Read-Field $prepared.candidate 'selectionKind'
		if ($script:selectionKind -cnotin @('STORED_START', 'EXISTING_START')) { throw 'INVALID_SELECTION_KIND' }
		$boundary = Required-Utc $prepared.candidate 'nextBoundary'
		if ($boundary -le (Now-Utc).AddSeconds(240)) { throw 'SCENE_INVALIDATED:CALENDAR_HORIZON' }
		$outside = Wait-For 'OUTSIDE' { param($s) $s.after.teleporting -cne 'true' } 10
		if (((Read-Field $outside.candidate 'positionSource') -ceq 'COMMITTED') -and ((Read-Field $outside.candidate 'committedSequence') -cne (Read-Field $prepared.candidate 'committedSequence')) -or ($outside.candidate.humanPrewarm -ceq 'true') -or ($outside.candidate.regionCanKnow -ceq 'true'))
		{
			$reprepared = Invoke-Proof 'PREPARE_M1_ENVELOPE' @{ stage = 'INITIAL'; profileId = [string]$script:profileId }
			if (($reprepared.status -cne 'ACCEPTED') -or ((Required-Int $reprepared.candidate 'profileId') -ne $script:profileId) -or ((Read-Field $reprepared.candidate 'selectionKind') -cne $script:selectionKind)) { throw "SCENE_INVALIDATED:SAME_TARGET_REPREPARE:$($reprepared.reason)" }
			$outside = Wait-For 'OUTSIDE' { param($s) $s.after.teleporting -cne 'true' } 10
		}
		if (($outside.candidate.humanPrewarm -cne 'false') -or ($outside.candidate.regionCanKnow -cne 'false') -or ($outside.candidate.clientVisible -cne 'false')) { throw 'SCENE_INVALIDATED:OUTSIDE_NOT_PROVEN' }
		if (($script:selectionKind -ceq 'STORED_START') -and ($outside.candidate.worldPresent -ceq 'true')) { $script:prewarmTimingUnproven = $true }
		$null = Approach-Tracked
		$matrix.CONTACT = if ($script:contact) { 'PASS' } else { 'NOT_OBSERVED' }
		$entry = Capture 'OBSERVE' $true 'CONTACT'
		$observationStart = Elapsed-Ms; $middleCensus = $false
		while (((Elapsed-Ms) - $observationStart) -lt 40000)
		{
			Pause-Ms 2000
			$elapsedObserve = (Elapsed-Ms) - $observationStart
			$takeCensus = (-not $middleCensus) -and ($elapsedObserve -ge 20000)
			$null = Capture 'OBSERVE' $takeCensus
			if ($takeCensus) { $middleCensus = $true }
		}
		$matrix.NEW_MATERIALIZATION = if (($script:selectionKind -ceq 'STORED_START') -and $script:prewarmMaterialized -and (-not $script:prewarmTimingUnproven) -and ($null -ne $script:firstClientVisibleUtc)) { 'PASS' } elseif ($script:selectionKind -ceq 'EXISTING_START') { 'EXISTING_START' } else { 'UNPROVEN' }
		$leaveStart = Elapsed-Ms; $left = $false
		for ($attempt = 0; $attempt -lt 2; $attempt++)
		{
			$null = Invoke-Proof 'STOP_MOVE'
			$ticket = Prepare-Phase 'LEAVE'
			try { $leave = Teleport-Ticket $ticket 'LEAVE' }
			catch { if ($attempt -eq 1) { throw }; continue }
			if (($leave.candidate.regionCanKnow -ceq 'false') -and ($leave.candidate.humanPrewarm -ceq 'false') -and ($leave.candidate.clientVisible -ceq 'false') -and ($leave.candidate.localityCurrent -ceq 'false')) { $left = $true; break }
		}
		if (-not $left) { throw 'LEAVE_NOT_OUTSIDE_CURRENT_PLAYER' }
		$absenceStart = Elapsed-Ms
		while (((Elapsed-Ms) - $absenceStart) -lt 15000)
		{
			Pause-Ms 2000
			$absent = Capture 'ABSENT'
			if (($absent.candidate.regionCanKnow -cne 'false') -or ($absent.candidate.humanPrewarm -cne 'false') -or ($absent.candidate.clientVisible -cne 'false') -or ($absent.candidate.worldPresent -cne 'true')) { throw 'SOFT_ABSENCE_BROKEN' }
		}
		$returned = $null
		for ($attempt = 0; $attempt -lt 2; $attempt++)
		{
			$ticket = Prepare-Phase 'RETURN'
			try { $returned = Teleport-Ticket $ticket 'RETURN'; break }
			catch { if ($attempt -eq 1) { throw } }
		}
		$visibleAgain = Wait-For 'RETURN_VISIBLE' { param($s) ($s.candidate.clientVisible -ceq 'true') -and ((Required-Int $s.candidate 'distance2D') -le 900) } 10
		if (((Elapsed-Ms) - $absenceStart) -gt 45000) { throw 'SOFT_RETURN_DEADLINE_EXPIRED' }
		$null = Capture 'RETURN_VISIBLE' $true 'SAME_PLAYER_NO_CHURN'
		$matrix.SOFT_RETURN = if ($script:continuityLocked -and ((Read-Field $visibleAgain.candidate 'materializedAtNanos') -ceq $script:lockedEpoch)) { 'PASS' } else { 'NOT_PROVEN' }
		$ordinaryFarm = @($script:census | Where-Object { ($_.eligible -ceq 'true') -and ($_.targetMonsterAlive -ceq 'true') -and (($_.attacking -ceq 'true') -or ($_.casting -ceq 'true')) })
		$nativeAction = $script:nativeDisplacement -or $script:selectedNativeFarm
		$matrix.NATIVE_LIFE = if ($nativeAction -and ($script:selectedNativeFarm -or ($ordinaryFarm.Count -gt 0))) { 'PASS' } else { 'NOT_OBSERVED' }
		$eligibleProfiles = @($script:census | Where-Object { $_.eligible -ceq 'true' } | Select-Object -ExpandProperty profileId -Unique).Count
		$failedIdle = @($script:census | Where-Object { ($_.eligible -ceq 'true') -and ($_.idleReason -ceq 'ACTIVE_IDLE') -and ($_.travelFailureReason -match '(navigation_|native_progress_|route_absent)') } | Group-Object profileId | Where-Object { $_.Count -ge 2 })
		$matrix.COHORT = if ($script:censusComplete -and ($eligibleProfiles -ge 4) -and ($failedIdle.Count -eq 0)) { 'PASS' } else { 'INSUFFICIENT_OR_FAILED' }
	}
	catch { $primaryFailure = $_.Exception.Message; Write-Warning "CONNECTED FAILED: $primaryFailure" }
	finally
	{
		$script:cleanup = $true; $script:cleanupClock = [Diagnostics.Stopwatch]::StartNew()
		try { if (-not $script:uncertain) { $null = Invoke-Proof 'STOP_MOVE' } }
		catch { $cleanupFailures.Add("STOP_MOVE:$($_.Exception.Message)") }
		try
		{
			if (($null -ne $script:origin) -and (-not $script:uncertain))
			{
				$originMove = Invoke-Proof 'TELEPORT_SELF' @{ x = $script:origin.x; y = $script:origin.y; z = $script:origin.z; instanceId = $script:origin.instanceId }
				if ($originMove.status -cne 'ACCEPTED') { throw "ORIGIN_RETURN_REJECTED:$($originMove.reason)" }
				for ($poll = 0; $poll -lt 10; $poll++)
				{
					Pause-Ms 1000
					$check = Invoke-Proof 'STATUS'
					if (($check.status -ceq 'SUCCEEDED') -and ((Read-Field $check.after 'identityOwner') -ceq 'REAL_LOGIN') -and ((Required-Int $check.after 'instanceId') -eq $script:origin.instanceId) -and ([Math]::Abs((Required-Int $check.after 'x') - $script:origin.x) -le 64) -and ([Math]::Abs((Required-Int $check.after 'y') - $script:origin.y) -le 64) -and ([Math]::Abs((Required-Int $check.after 'z') - $script:origin.z) -le 120)) { $script:restored = $true; break }
				}
				if (-not $script:restored) { throw 'ORIGIN_RETURN_NOT_CONFIRMED' }
				$matrix.RESTORE = 'PASS'
			}
		}
		catch { $cleanupFailures.Add("ORIGIN_RETURN:$($_.Exception.Message)") }
		try { Save-Tsv $script:rows 'M1_CONNECTED_WORLD.tsv'; Save-Tsv $script:census 'M1_VISIBLE_LIFE_CENSUS.tsv' }
		catch { $cleanupFailures.Add("EVIDENCE_WRITE:$($_.Exception.Message)") }
		try
		{
			$stopped = & $script:stopPilot
			if ($stopped -is [string]) { $stopped = $stopped | ConvertFrom-Json }
			$script:stopState = Read-Field $stopped 'state' 'NOT_CONFIRMED'
			if ($script:stopState -ceq 'STOPPED') { $matrix.STOP = 'PASS' }
			else { $cleanupFailures.Add("STOP:$script:stopState") }
		}
		catch { $cleanupFailures.Add("STOP:$($_.Exception.Message)") }
		try { if ($null -ne $script:startMetrics) { $script:endMetrics = & $script:gameMetrics; if (($null -eq $script:endMetrics) -or ($script:endMetrics.pid -ne $script:startMetrics.pid)) { throw 'OWNED_GAME_PID_CHANGED' }; if ($script:endMetrics.pilotState -ceq 'RUNNING') { throw 'PILOT_RUN_STILL_ACTIVE' } } }
		catch { $cleanupFailures.Add("METRICS:$($_.Exception.Message)") }
		try
		{
			$complete = @($matrix.Values | Where-Object { $_ -cne 'PASS' }).Count -eq 0
			$grade = if ($complete -and (-not $primaryFailure) -and ($cleanupFailures.Count -eq 0)) { 'GREEN — M1 VISIBLE WORLD COMPLETE (1280)' } elseif (($matrix.CONTACT -ceq 'PASS') -and ($matrix.SOFT_RETURN -ceq 'PASS')) { 'PARTIAL — EXISTING_PLAYER_CONTINUITY_PASS; M1_OPEN' } else { 'RED_OR_UNPROVEN — M1_OPEN' }
			$lines = @("runId=$script:runId", "result=$grade", "primaryFailure=$primaryFailure", "cleanupFailures=$($cleanupFailures -join ';')", "selectionKind=$script:selectionKind", "profileId=$script:profileId", "requests=$script:requestCount", "firstLocalUtc=$script:firstLocalUtc", "firstMaterializedUtc=$script:firstMaterializedUtc", "firstCouldKnowUtc=$script:firstCouldKnowUtc", "firstClientVisibleUtc=$script:firstClientVisibleUtc", "stopState=$script:stopState", "gamePid=$(Read-Field $script:startMetrics 'pid')", "cpuStartMillis=$(Read-Field $script:startMetrics 'cpuMillis')", "cpuEndMillis=$(Read-Field $script:endMetrics 'cpuMillis')", "privateStartBytes=$(Read-Field $script:startMetrics 'privateBytes')", "privateEndBytes=$(Read-Field $script:endMetrics 'privateBytes')", "pilotStateAfterStop=$(Read-Field $script:endMetrics 'pilotState' 'UNVERIFIED')")
			foreach ($key in $matrix.Keys) { $lines += "${key}=$($matrix[$key])" }
			[IO.File]::WriteAllLines((Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt'), $lines, [Text.UTF8Encoding]::new($false))
		}
		catch { $cleanupFailures.Add("RESULT_WRITE:$($_.Exception.Message)") }
	}
	if ($primaryFailure -or ($cleanupFailures.Count -gt 0) -or (@($matrix.Values | Where-Object { $_ -cne 'PASS' }).Count -gt 0)) { throw "M1_CLOSEOUT_INCOMPLETE:primary=$primaryFailure;cleanup=$($cleanupFailures -join ';');evidence=$script:evidenceRoot" }
	Write-Host "CONNECTED PASS: profile=$script:profileId evidence=$script:evidenceRoot"
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-M1Run }
