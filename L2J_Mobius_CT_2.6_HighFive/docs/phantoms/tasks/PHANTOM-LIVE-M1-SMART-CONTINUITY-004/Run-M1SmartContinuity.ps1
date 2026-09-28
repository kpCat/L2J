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

# Reuse M1-003's consented Pilot operations and immutable native snapshots.
function Invoke-Proof([string] $operation, [hashtable] $arguments = @{})
{
	if ([DateTime]::UtcNow -ge $script:deadline) { throw 'ACCEPTANCE_DEADLINE_EXPIRED' }
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
	foreach ($field in @('profileId', 'objectId', 'committedX', 'committedY', 'committedZ', 'liveX', 'liveY', 'liveZ', 'worldPresent', 'snapshotWorldPresent', 'regionCanKnow', 'clientVisible', 'distance2D', 'localityCurrent', 'materializationState', 'materializedAgeMillis', 'activityState', 'requestedState', 'activeSignalSources', 'boundaryInFlight', 'presenceReason', 'admitted', 'transitionStatus', 'lastMaterializationFailure'))
	{
		$record[$field] = Read-Field $target $field
	}
	$script:rows.Add([pscustomobject]$record)
	for ($sample = 1; $sample -le [int](Read-Field $target 'censusCount' '0'); $sample++)
	{
		$entry = [ordered]@{ utc = $record.utc; phase = $phase }
		foreach ($field in @('profileId', 'objectId', 'level', 'npcId', 'anchor', 'goalStatus', 'runtimeReason', 'travelReason', 'dead', 'moving', 'attacking', 'casting', 'autoPlay', 'party', 'store', 'intention', 'shortTargets', 'longTargets')) { $entry[$field] = Read-Field $target "census${sample}.$field" }
		$script:census.Add([pscustomobject]$entry)
	}
	if (($target.regionCanKnow -ceq 'true') -and ($target.worldPresent -cne 'true')) { throw 'VISIBLE_DISAPPEARANCE' }
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
	$null = Wait-For 'OUTSIDE' { param($s) ($s.candidate.worldPresent -ceq 'false') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.localityCurrent -ceq 'false') } 10
	$latency = [Diagnostics.Stopwatch]::StartNew()
	Teleport-To ([int]$route.prewarmX) ([int]$route.prewarmY) ([int]$route.prewarmZ) 'PREWARM_APPROACH'
	$prewarm = Wait-For 'PREWARM' { param($s) ($s.candidate.worldPresent -ceq 'true') -and ($s.candidate.regionCanKnow -ceq 'false') -and ($s.candidate.clientVisible -ceq 'false') } 10
	$latency.Stop()
	if ($latency.Elapsed.TotalSeconds -ge 15) { throw "STALE_PREWARM_DELAY:$($latency.Elapsed.TotalSeconds)" }
	$objectId = [int]$prewarm.candidate.objectId
	$birthUtc = [DateTime]::Parse((Read-Field $prewarm 'endUtc')).ToUniversalTime().AddMilliseconds(-[long]$prewarm.candidate.materializedAgeMillis)
	$null = Capture 'PREWARM' "MATERIALIZED_BEFORE_VISIBILITY_LATENCY_MS=$($latency.ElapsedMilliseconds)"
	Follow-Native $prewarm 'NATIVE_ENTRY'
	$null = Wait-For 'VISIBLE_ENTRY' { param($s) $s.candidate.regionCanKnow -ceq 'true' } 10
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
	$useful = @($ordinary | Where-Object { ($_.moving -ceq 'true') -or ($_.attacking -ceq 'true') }).Count -gt 0
	$recovery = @($ordinary | Group-Object profileId | Where-Object { @($_.Group | ForEach-Object { "$($_.npcId)@$($_.anchor)" } | Select-Object -Unique).Count -gt 1 }).Count -gt 0
	if (-not ($useful -or $recovery)) { throw 'ORDINARY_NATIVE_LIFE_OR_BOUNDED_RECOVERY_NOT_OBSERVED' }
	Write-Host "CONNECTED PASS: profile=$profileId object=$objectId prewarmMs=$($latency.ElapsedMilliseconds) samePlayer=True nativeLife=$useful alternateRecovery=$recovery"
}
catch
{
	$failure = $_.Exception.Message
	Write-Warning "CONNECTED FAILED: $failure"
}
finally
{
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
	& (Join-Path $module 'tools/phantom-local-play/Stop-LocalPlayPilot.ps1') | Out-Null
}
if ($failure -or (-not $restored)) { throw $(if ($failure) { $failure } else { 'ORIGIN_RETURN_NOT_CONFIRMED' }) }
