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

$nativeFalsePasses = [Collections.Generic.List[string]]::new()
$script:censusComplete = $true; $script:nativeDisplacement = $true; $script:selectedNativeFarm = $false
$script:rows = [Collections.Generic.List[object]]::new()
$script:census = [Collections.Generic.List[object]]::new()
foreach ($id in 1..4)
{
	$script:census.Add([pscustomobject]@{ profileId = [string]$id; eligible = 'true'; targetMonsterAlive = 'true'; attacking = 'false'; casting = 'true'; idleReason = 'NATIVE_ACTION'; travelFailureReason = ''; materializationState = 'FAILED'; actionAdmissionOpen = 'false' })
}
if ((Get-NativeM1Grades).NATIVE_LIFE -ceq 'PASS') { $nativeFalsePasses.Add('A03 another actor cast supplies selected proof') }
if ((Get-NativeM1Grades).COHORT -ceq 'PASS') { $nativeFalsePasses.Add('A01 FAILED/admissionclosed cohort passes') }
$script:selectedNativeFarm = $true
if ((Get-NativeM1Grades).NATIVE_LIFE -ceq 'PASS') { $nativeFalsePasses.Add('A02 selfheal/target flags without attributed progress pass') }
if ((Get-NativeM1Grades).NATIVE_LIFE -ceq 'PASS') { $nativeFalsePasses.Add('A04 missing fresh interval baseline passes') }
Assert-True ($nativeFalsePasses.Count -eq 0) ('NATIVE_GRADING_FALSE_PASS:' + ($nativeFalsePasses -join ';'))

function New-NativeFixture([long] $profile, [long] $sampleNanos, [long] $cycles, [string] $phase = 'NONE')
{
	return [pscustomobject]@{
		profileId = [string]$profile; objectId = [string](1000 + $profile); materializedAtNanos = '900'
		materializationState = 'ACTIVE'; actionAdmissionOpen = 'true'; pendingOwnedStore = 'false'; worldPresent = 'true'
		eligible = 'true'; dead = 'false'; hp = '100'; maxHp = '100'; nativeAttackBy = ''
		nativeEvidenceVersion = '1'; nativeEvidenceOwner = 'PHANTOM'; nativeEvidenceObjectId = [string](1000 + $profile); nativeEvidenceEpoch = '900'
		nativeEvidenceSampleNanos = [string]$sampleNanos; nativeEvidenceOverflow = 'false'; nativeEvidenceSequence = [string]($cycles * 5)
		nativeDamageSequence = [string]($cycles * 2); nativeKillSequence = [string]$cycles; nativeRewardSequence = [string]$cycles
		nativeTargetSequence = [string]$cycles; nativeFarmCycleSequence = [string]$cycles; nativeExpGained = [string]($cycles * 70); nativeSpGained = [string]($cycles * 2)
		nativeLootSequence = '0'; nativeLastProgressNanos = $(if ($cycles -gt 0) { [string]$sampleNanos } else { '0' })
		nativePhase = $phase; nativePhaseSinceNanos = $(if ($phase -ceq 'NONE') { '0' } else { '900' }); nativePhaseDeadlineNanos = $(if ($phase -ceq 'NONE') { '0' } else { '90000000900' })
	}
}

function New-CensusFixture([object[]] $members, [long] $next = 0)
{
	$data = @{ censusCount = [string]$members.Count; censusEligible = [string]@($members | Where-Object { $_.eligible -ceq 'true' }).Count; censusNextProfileId = [string]$next }
	for ($index = 0; $index -lt $members.Count; $index++)
	{
		foreach ($property in $members[$index].PSObject.Properties) { $data["census$($index + 1).$($property.Name)"] = [string]$property.Value }
	}
	return [pscustomobject]$data
}

# A04 cumulative values at the first sample are a baseline, including already completed cycles.
$script:profileId = 545L
Initialize-NativeM1Evidence ([pscustomobject]@{ naturalCohortProfileIds = '545,546,547,548' })
$baseline = New-NativeFixture 545 10000000900 50
$script:selectedEvidence = Add-NativeEvidence $null $baseline
Assert-True ((Get-NativeM1Grades).NATIVE_LIFE -cne 'PASS') 'A04_CUMULATIVE_BASELINE_NOT_NEW'
$still = New-NativeFixture 545 12000000900 50
$still.nativeLastProgressNanos = $baseline.nativeLastProgressNanos
$script:selectedEvidence = Add-NativeEvidence $script:selectedEvidence $still
Assert-True ((Get-NativeM1Grades).NATIVE_LIFE -cne 'PASS') 'A02_SELF_HEAL_OR_FLAGS_NOT_EFFECT'

# A03/A05/A10 exact scalar owner/epoch/time and numeric contracts fail closed.
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeEvidenceObjectId = '1546'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'ACTOR_OR_EPOCH_MISMATCH'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeEvidenceOwner = 'LOCALPLAY_TEST_HUMAN'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'UNPROVEN_OR_OVERFLOW'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeEvidenceOverflow = 'true'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'UNPROVEN_OR_OVERFLOW'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.PSObject.Properties.Remove('nativeDamageSequence')
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'INVALID_REQUIRED_LONG:nativeDamageSequence'
foreach ($invalid in @('NaN', 'Infinity', '-1', '9223372036854775808', '1.5'))
{
	$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeDamageSequence = $invalid
	Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'INVALID_REQUIRED_LONG:nativeDamageSequence'
}
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $still } 'STALE_SAMPLE'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeLastProgressNanos = '11000000900'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'STALE_EVENT'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativeLastProgressNanos = '899'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'INVALID_TIME'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.nativePhaseSinceNanos = '900'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'INVALID_PHASE_TIME'
$wrong = New-NativeFixture 545 14000000900 49
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'SEQUENCE_REGRESSED'
$wrong = New-NativeFixture 545 14000000900 51; $wrong.materializedAtNanos = '901'; $wrong.nativeEvidenceEpoch = '901'
Assert-Throws { Add-NativeEvidence $script:selectedEvidence $wrong } 'EPOCH_CHANGED'

# A06/A08 two fresh selected cycles survive aggro clearing; two distinct other actors progress.
Initialize-NativeM1Evidence ([pscustomobject]@{ naturalCohortProfileIds = '545,546,547,548' })
$script:selectedEvidence = Add-NativeEvidence $null (New-NativeFixture 545 10000000900 0)
$script:selectedEvidence = Add-NativeEvidence $script:selectedEvidence (New-NativeFixture 545 20000000900 2)
$before = @(545..548 | ForEach-Object { New-NativeFixture $_ 10000000900 0 'REGEN' })
$after = @(545..548 | ForEach-Object { New-NativeFixture $_ 20000000900 $(if ($_ -eq 545) { 2 } elseif ($_ -lt 548) { 1 } else { 0 }) 'REGEN' })
Read-Census (New-CensusFixture $before) ([ordered]@{ utc = '2026-09-29T09:00:00Z'; phase = 'OBSERVE'; censusCount = ''; censusEligible = '' })
Read-Census (New-CensusFixture $after) ([ordered]@{ utc = '2026-09-29T09:00:10Z'; phase = 'OBSERVE'; censusCount = ''; censusEligible = '' })
Assert-True ((Get-NativeM1Grades).NATIVE_LIFE -ceq 'PASS') 'A06_TWO_OWN_CYCLES_WITH_CLEARED_AGGRO'
Assert-True ((Get-NativeM1Grades).COHORT -ceq 'PASS') 'A08_FROZEN_FOUR_TWO_OTHER_PROGRESS'
$state = $script:cohortEvidence['547']; $script:cohortEvidence['547'].useful = $false
Assert-True ((Get-NativeM1Grades).COHORT -cne 'PASS') 'A08_ONE_OTHER_ACTOR_INSUFFICIENT'
$script:cohortEvidence['547'].useful = $true
$record = [ordered]@{ utc = '2026-09-29T09:00:20Z'; phase = 'OBSERVE'; censusCount = ''; censusEligible = '' }
Assert-Throws { Read-Census (New-CensusFixture @($after | Select-Object -First 3)) $record } 'FROZEN_COHORT_ROW_LOST:548'
$unsafe = @($after | ForEach-Object { New-NativeFixture ([long]$_.profileId) 30000000900 3 'REGEN' }); $unsafe[3].materializationState = 'FAILED'
Assert-Throws { Read-Census (New-CensusFixture $unsafe) $record } 'FROZEN_COHORT_UNSAFE:548'
$unsafe[3].materializationState = 'ACTIVE'; $unsafe[3].actionAdmissionOpen = 'false'
Assert-Throws { Read-Census (New-CensusFixture $unsafe) $record } 'FROZEN_COHORT_UNSAFE:548'
$unsafe[3].actionAdmissionOpen = 'true'; $unsafe[3].hp = 'NaN'
Assert-Throws { Read-Census (New-CensusFixture $unsafe) $record } 'FROZEN_COHORT_INVALID_HP:548'
$script:transport = { param($op, $operationArgs, $id) return [pscustomobject]@{ status = 'REJECTED'; reason = 'OFFLINE_PAGE_LOST' } }
$script:cleanup = $false; $script:requestCount = 0; $script:elapsed = { 0L }; $script:runId = 'offline-pagination'
Assert-Throws { Read-Census (New-CensusFixture $before 548) $record } 'CENSUS_PAGE_FAILED'

# A07 a bounded death/recovery is visible in the frozen denominator; endless recovery is RED.
Initialize-NativeM1Evidence ([pscustomobject]@{ naturalCohortProfileIds = '545,546,547,548' })
$before[3].dead = 'true'; $before[3].eligible = 'false'; $before[3].nativePhase = 'DEATH_RECOVERY'
Read-Census (New-CensusFixture $before) $record
$after[3].dead = 'true'; $after[3].eligible = 'false'; $after[3].nativePhase = 'DEATH_RECOVERY'
Read-Census (New-CensusFixture $after) $record
Assert-True ($script:cohortEvidence.ContainsKey('548')) 'A07_DEAD_ACTOR_RETAINS_DENOMINATOR'
$endless = New-NativeFixture 548 101000000900 0 'DEATH_RECOVERY'; $endless.nativePhaseSinceNanos = '20000000900'; $endless.nativePhaseDeadlineNanos = '110000000900'
Assert-Throws { Add-NativeEvidence $script:cohortEvidence['548'] $endless } 'PROGRESS_DEADLINE'
$script:actorMode = 'RealClient'
Assert-Throws { Initialize-NativeM1Evidence ([pscustomobject]@{}) } 'FROZEN_COHORT_MISSING'
Assert-Throws { Initialize-NativeM1Evidence ([pscustomobject]@{ naturalCohortProfileIds = '545,546,547,547' }) } 'FROZEN_COHORT_DUPLICATE'
Assert-Throws { Initialize-NativeM1Evidence ([pscustomobject]@{ naturalCohortProfileIds = '545,546,547' }) } 'FROZEN_COHORT_INSUFFICIENT'

# Actual transport guard reserves cleanup requests and enforces the unchanged time limits.
$script:proofCalls = 0; $script:cleanup = $false; $script:requestCount = 559; $script:runId = 'offline-budget'
$script:elapsed = { 0L }; $script:transport = { param($op, $operationArgs, $id) $script:proofCalls++; return [pscustomobject]@{ status = 'SUCCEEDED' } }
$null = Invoke-Proof 'STATUS'
Assert-Throws { Invoke-Proof 'STATUS' } 'MAILBOX_BUDGET_EXHAUSTED'
Assert-True ($script:proofCalls -eq 1) 'MAILBOX_NORMAL_BOUND_PREVENTS_TRANSPORT'
$script:cleanup = $true; $script:cleanupClock = [Diagnostics.Stopwatch]::StartNew()
foreach ($request in 1..32) { $null = Invoke-Proof 'STATUS' }
Assert-Throws { Invoke-Proof 'STATUS' } 'MAILBOX_BUDGET_EXHAUSTED'
Assert-True ($script:proofCalls -eq 33) 'MAILBOX_EXACT_32_CLEANUP_RESERVE'
$script:cleanup = $false; $script:requestCount = 0; $script:elapsed = { 480000L }
Assert-Throws { Invoke-Proof 'STATUS' } 'ACCEPTANCE_DEADLINE_EXPIRED'
$script:elapsed = { 0L }

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
			return [pscustomobject]@{ status = 'ACCEPTED'; candidate = [pscustomobject]@{ profileId = '545'; selectionKind = 'STORED_START'; committedSequence = '1'; nextBoundary = '2026-09-29T10:00:00Z'; legacySkips = '71:KNOWN_PREFIX_FAIL_CLOSED'; naturalCohortProfileIds = '545,546,547,548' } }
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
	$cycles = [long][Math]::Floor($script:clockMs / 10000.0)
	$sensor = New-NativeFixture 545 (900L + (($script:clockMs + 1) * 1000000L)) $cycles
	$sensor.objectId = '777'; $sensor.nativeEvidenceObjectId = '777'
	$sensor.nativeLastProgressNanos = $(if ($cycles -gt 0) { [string](900L + ($cycles * 10000000000L)) } else { '0' })
	foreach ($field in (Native-EvidenceFields)) { $data[$field] = Read-Field $sensor $field }
	if ($operationArgs.includeCensus -eq 'true')
	{
		$data.censusCount = '4'; $data.censusEligible = '4'; $data.censusNextProfileId = '0'
		for ($n = 1; $n -le 4; $n++)
		{
			$data["census${n}.profileId"] = [string](544 + $n)
			$member = New-NativeFixture (544 + $n) (900L + (($script:clockMs + 1) * 1000000L)) $cycles
			$member.nativeLastProgressNanos = $sensor.nativeLastProgressNanos
			if ($n -eq 1) { $member.objectId = '777'; $member.nativeEvidenceObjectId = '777' }
			foreach ($property in $member.PSObject.Properties) { $data["census${n}.$($property.Name)"] = [string]$property.Value }
			$data["census${n}.eligible"] = 'true'
			$data["census${n}.targetMonsterAlive"] = 'true'
			$data["census${n}.attacking"] = 'true'
			$data["census${n}.idleReason"] = 'NONE'
			$data["census${n}.admittedActionCount"] = '0'
			$data["census${n}.cleanupPhase"] = $(if ($script:neverMaterialize) { 'POST_STORE' } else { 'NONE' })
			$data["census${n}.cleanupFailurePhase"] = $(if ($script:neverMaterialize) { 'POST_STORE' } else { 'NONE' })
			$data["census${n}.cleanupFailureClass"] = $(if ($script:neverMaterialize) { 'java.lang.IllegalStateException' } else { '' })
			$data["census${n}.cleanupFailureMessage"] = $(if ($script:neverMaterialize) { 'owned cleanup diagnostic fixture' } else { '' })
			$data["census${n}.cleanupFailureSequence"] = $(if ($script:neverMaterialize) { '1' } else { '0' })
			$data["census${n}.cleanupFailureAdmittedActionCount"] = '0'
			foreach ($field in @('playerRetained', 'identityLeaseRetained', 'outboundAttached', 'worldPresent')) { $data["census${n}.$field"] = 'true' }
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
Assert-True ($script:clockMs -ge 35000) 'VIRTUAL_OBSERVATION_AND_ABSENCE_ELAPSED'
$earlyObserve = @($script:rows | Where-Object { $_.phase -ceq 'OBSERVE' })
Assert-True (([DateTimeOffset]::Parse($earlyObserve[-1].utc) - [DateTimeOffset]::Parse($earlyObserve[0].utc)).TotalSeconds -lt 180) 'EARLY_EXIT_ONLY_AFTER_COMPLETE_NATIVE_GRADES'

# Two slow cycles remain observable beyond the former 40s window, with fresh damage meanwhile.
$script:ordinaryTransport = $script:transport
$script:transport = {
	param($op, $operationArgs, $id)
	$reply = & $script:ordinaryTransport $op $operationArgs $id
	if ($op -eq 'SNAPSHOT_M1_ENVELOPE')
	{
		$slowCycles = [string][long][Math]::Floor($script:clockMs / 80000.0)
		foreach ($field in @('nativeKillSequence', 'nativeRewardSequence', 'nativeTargetSequence', 'nativeFarmCycleSequence')) { $reply.candidate.$field = $slowCycles }
		if ($operationArgs.includeCensus -eq 'true')
		{
			for ($member = 1; $member -le 4; $member++)
			{
				foreach ($field in @('nativeKillSequence', 'nativeRewardSequence', 'nativeTargetSequence', 'nativeFarmCycleSequence')) { $reply.candidate."census${member}.$field" = $slowCycles }
			}
		}
	}
	return $reply
}
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false
$script:liveRouteFailures = 0; $script:liveRelocated = $false; $script:pilotRunning = $true
Invoke-M1Run
$slowObserve = @($script:rows | Where-Object { $_.phase -ceq 'OBSERVE' })
$slowSeconds = ([DateTimeOffset]::Parse($slowObserve[-1].utc) - [DateTimeOffset]::Parse($slowObserve[0].utc)).TotalSeconds
Assert-True (($slowSeconds -ge 120) -and ($slowSeconds -le 180)) 'OBSERVE_EXTENDED_BOUNDED_180'
Assert-True (($script:clockMs -lt 480000) -and ($script:requestCount -le 560)) 'EXTENDED_OBSERVE_FITS_TOTAL_AND_MAILBOX'
$script:transport = $script:ordinaryTransport

# A transport reply arriving after the observation deadline cannot contribute proof.
$script:transport = {
	param($op, $operationArgs, $id)
	$reply = & $script:ordinaryTransport $op $operationArgs $id
	if (($op -eq 'SNAPSHOT_M1_ENVELOPE') -and $script:contact) { $script:clockMs += 180001L }
	return $reply
}
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'OBSERVATION_DEADLINE_EXPIRED'
Assert-True ($null -eq $script:selectedEvidence) 'LATE_OBSERVE_REPLY_NEVER_CREDITS_SENSOR'
$script:transport = $script:ordinaryTransport

# A 250-second calendar horizon no longer permits a worst-case extended scene.
$script:transport = {
	param($op, $operationArgs, $id)
	$reply = & $script:ordinaryTransport $op $operationArgs $id
	if (($op -eq 'PREPARE_M1_ENVELOPE') -and ($operationArgs.stage -eq 'INITIAL')) { $reply.candidate.nextBoundary = (& $script:utcNow).AddSeconds(250).ToString('o') }
	return $reply
}
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'SCENE_INVALIDATED:CALENDAR_HORIZON'
Assert-True ($script:restored -and ($script:stopState -ceq 'STOPPED')) 'SHORT_CALENDAR_STILL_RESTORES_AND_STOPS'
$script:transport = $script:ordinaryTransport

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
$script:firstGreenReceipt = Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt'
$script:previouslyCompletedProfileId = 545L; $script:previousCompletedEvidencePath = ''
Assert-Throws { Read-PreviousM1Completion } 'PREVIOUS_COMPLETION_EVIDENCE_REQUIRED'
$script:previousCompletedEvidencePath = $script:firstGreenReceipt
$receipt = Read-PreviousM1Completion
Assert-True (($receipt.path -ceq $script:firstGreenReceipt) -and ($receipt.sha256 -match '^[0-9A-F]{64}$')) 'FIRST_GREEN_RECEIPT_EXACT_HASH'
$script:previouslyCompletedProfileId = 546L
Assert-Throws { Read-PreviousM1Completion } 'NOT_FIRST_GREEN_OR_SAME_RUNNER'
$script:previouslyCompletedProfileId = 545L
$invalidReceipt = Join-Path ([IO.Path]::GetDirectoryName($script:firstGreenReceipt)) 'OFFLINE_INVALID_PREVIOUS_RESULT.txt'
[IO.File]::WriteAllLines($invalidReceipt, ($syntheticResult.Replace('COHORT=PASS', 'COHORT=INSUFFICIENT_OR_FAILED').TrimEnd() -split '\r?\n'), [Text.UTF8Encoding]::new($false))
$script:previousCompletedEvidencePath = $invalidReceipt
Assert-Throws { Read-PreviousM1Completion } 'PREVIOUS_COMPLETION_GRADE_FAILED:COHORT'
$script:previousCompletedEvidencePath = $script:firstGreenReceipt
$script:selectorTransport = $script:transport; $script:excludedIdsSeen = ''
$script:transport = {
	param($op, $operationArgs, $id)
	if (($op -eq 'PREPARE_M1_ENVELOPE') -and ($operationArgs.stage -eq 'INITIAL')) { $script:excludedIdsSeen = [string]$operationArgs.excludePreviouslySelectedProfileIds }
	return & $script:selectorTransport $op $operationArgs $id
}
$script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'PREVIOUS_SELECTED_PROFILE_REUSED'
Assert-True ($script:excludedIdsSeen -ceq '545') 'EXCLUSION_ONLY_EXACT_COMPLETED_SELECTED_ID'
$script:previouslyCompletedProfileId = 0L; $script:previousCompletedEvidencePath = ''; $script:transport = $script:selectorTransport
$script:validTransport = $script:transport
$script:neverMaterialize = $true; $script:clockMs = 0L; $script:actorX = 1000; $script:targetX = 0; $script:materialized = $false; $script:pilotRunning = $true
Assert-Throws { Invoke-M1Run } 'APPROACH_DEADLINE_EXPIRED'
$capacityResult = Get-Content (Join-Path $script:evidenceRoot 'M1_CONNECTED_RESULT.txt') -Raw
Assert-True ($capacityResult -match 'primaryFailure=APPROACH_DEADLINE_EXPIRED') 'CAPACITY_PRIMARY_RETAINED'
Assert-True ($capacityResult -match 'COHORT=NOT_OBSERVED') 'DIAGNOSTIC_CENSUS_NEVER_GRADES_ACCEPTANCE'
Assert-True (@($script:census | Where-Object { $_.phase -like 'DIAGNOSTIC_APPROACH_FAILURE_*' } | Select-Object -ExpandProperty phase -Unique).Count -eq 3) 'FAILED_APPROACH_THREE_DIAGNOSTIC_CENSUS'
$diagnosticSample = $script:census | Where-Object { $_.phase -like 'DIAGNOSTIC_APPROACH_FAILURE_*' } | Select-Object -First 1
Assert-True ($diagnosticSample.cleanupPhase -ceq 'POST_STORE' -and $diagnosticSample.cleanupFailureClass -ceq 'java.lang.IllegalStateException' -and $diagnosticSample.cleanupFailureMessage -ceq 'owned cleanup diagnostic fixture' -and $diagnosticSample.cleanupFailureSequence -ceq '1' -and $diagnosticSample.admittedActionCount -ceq '0') 'CLEANUP_DIAGNOSTIC_FIELDS_RETAINED'
Assert-True ($diagnosticSample.playerRetained -ceq 'true' -and $diagnosticSample.identityLeaseRetained -ceq 'true' -and $diagnosticSample.outboundAttached -ceq 'true' -and $diagnosticSample.worldPresent -ceq 'true') 'CLEANUP_OWNERSHIP_FIELDS_RETAINED'
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
