[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime = Join-Path $module '.phantom-local/observe014/runtime'
$private = Join-Path $module '.phantom-local/observe014/causal'
if (Test-Path -LiteralPath $private) { throw 'Causal observation already exists; no automatic repeat.' }
New-Item -ItemType Directory -Path $private | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$context = Get-PilotContext -RequireEnabled -ActorMode RealClient
$session = Get-PilotSession $context
if ($session.state -cne 'ARMED_IDLE' -or [int]$session.objectId -ne 268492939 -or [int]$context.Pid -ne 30124) { throw 'Expected exact TestAdmin ARMED_IDLE session not verified.' }
$run = [guid]::NewGuid().ToString('D')
function Invoke-Trace([string]$Operation,[hashtable]$Arguments=@{}) {
    $json = & (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -RunId $run -Arguments $Arguments -ActorMode RealClient
    $result = $json | ConvertFrom-Json
    $json | Set-Content (Join-Path $private "$($result.sequence)-$Operation.json") -Encoding utf8
    if ($result.status -cne 'SUCCEEDED') { throw "Trace action refused: $Operation/$($result.reason)" }
    return $result
}
$status = Invoke-Trace 'STATUS'
if ($status.before.identityOwner -cne 'REAL_LOGIN' -or $status.before.online -cne 'true' -or $status.before.worldPresent -cne 'true' -or $status.before.clientIdentity -ceq 'none') { throw 'REAL_LOGIN client/world identity not verified.' }
# Successful STATUS is admitted only by sessionValid/realClient: exact client IN_GAME, not detached.
Write-Output "VERIFIED ARMED_IDLE IN_GAME REAL_LOGIN objectId=268492939 position=$($status.before.x),$($status.before.y),$($status.before.z)"
$begin = Invoke-Trace 'BEGIN_PHANTOM_CAUSAL_TRACE'
$watch = [Diagnostics.Stopwatch]::StartNew()
$begunUtc = [DateTimeOffset]::UtcNow
Write-Output "CAUSAL_BEGIN serverUtc=$($begin.endUtc) startedNanos=$($begin.candidate.startedNanos)"
$lastHeartbeat = 0.0
$reported = 0
while ($watch.Elapsed.TotalSeconds -lt 90.0) {
    if ($watch.Elapsed.TotalSeconds - $lastHeartbeat -ge 5.0) {
        Write-PilotHeartbeat $context ([string]$session.sessionId) $run
        $lastHeartbeat = $watch.Elapsed.TotalSeconds
    }
    $checkpoint = [int][math]::Floor($watch.Elapsed.TotalSeconds / 30.0)
    if ($checkpoint -gt $reported) { $reported = $checkpoint; Write-Output "OBSERVING elapsed=$([math]::Round($watch.Elapsed.TotalSeconds,3))s" }
    $remainingMs = (90.0 - $watch.Elapsed.TotalSeconds) * 1000
    if ($remainingMs -gt 0) { Start-Sleep -Milliseconds ([int][math]::Min(100, [math]::Ceiling($remainingMs))) }
}
$elapsed = $watch.Elapsed.TotalSeconds
@{observationSeconds=$elapsed;ackUtc=$begunUtc.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $private 'timing.json') -Encoding utf8
Write-Output "OBSERVATION_WINDOW_FINISHED elapsed=${elapsed}s"
$snapshot = Invoke-Trace 'SNAPSHOT_PHANTOM_CAUSAL_TRACE' @{maxEvents='1'}
$page = Invoke-Trace 'END_PHANTOM_CAUSAL_TRACE' @{maxEvents='128'}
$endUtc = $page.endUtc
$events = [Collections.Generic.List[string]]::new()
$pageCount = 0
do {
    $pageCount++
    foreach ($property in $page.candidate.PSObject.Properties) {
        if ($property.Name.StartsWith('event.')) { $events.Add([string]$property.Value) }
    }
    if ($page.candidate.hasMore -ceq 'true') {
        $cursor = [string]$page.candidate.nextSeq
        $page = Invoke-Trace 'SNAPSHOT_PHANTOM_CAUSAL_TRACE' @{afterSeq=$cursor;maxEvents='128'}
        if ([long]$page.candidate.nextSeq -le [long]$cursor) { throw 'Causal pagination did not advance.' }
    } else { break }
    if ($pageCount -gt 8192) { throw 'Causal pagination bound exceeded.' }
} while ($true)
& python (Join-Path $PSScriptRoot 'Recover-Trace.py')
if ($LASTEXITCODE -ne 0) { throw 'Raw mailbox causal export verification failed.' }
Write-Output "CAUSAL_ENDED elapsed=${elapsed}s watched=$($page.candidate.watched) retained=$($page.candidate.retained) dropped=$($page.candidate.dropped) pages=$pageCount"
