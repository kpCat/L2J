[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime = Join-Path $module '.phantom-local/observe018/runtime'
$private = Join-Path $module '.phantom-local/observe018/causal'
if (Test-Path -LiteralPath $private) { throw 'Observation already exists; no automatic repeat.' }
New-Item -ItemType Directory -Path $private | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$context = Get-PilotContext -RequireEnabled -ActorMode RealClient
$session = Get-PilotSession $context
if ($session.state -cne 'ARMED_IDLE' -or [int]$session.objectId -ne 268492939) { throw 'Exact TestAdmin ARMED_IDLE not verified.' }
$run = [guid]::NewGuid().ToString('D')
function Invoke-Trace([string]$Operation,[hashtable]$Arguments=@{}) {
    $json = & (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -RunId $run -Arguments $Arguments -ActorMode RealClient
    $result = $json | ConvertFrom-Json
    $json | Set-Content (Join-Path $private "$($result.sequence)-$Operation.json") -Encoding utf8
    if ($result.status -cne 'SUCCEEDED') { throw "Trace refused: $Operation/$($result.reason)" }
    return $result
}
$status = Invoke-Trace 'STATUS'
if ($status.before.identityOwner -cne 'REAL_LOGIN' -or $status.before.online -cne 'true' -or $status.before.worldPresent -cne 'true' -or $status.before.clientIdentity -ceq 'none') { throw 'REAL_LOGIN client/world identity not verified.' }
Write-Output "VERIFIED ARMED_IDLE IN_GAME REAL_LOGIN objectId=268492939 position=$($status.before.x),$($status.before.y),$($status.before.z)"
# Stopwatch starts before BEGIN; leave three seconds for END mailbox acknowledgement.
$watch = [Diagnostics.Stopwatch]::StartNew()
$begin = Invoke-Trace 'BEGIN_PHANTOM_CAUSAL_TRACE'
Write-Output "CAUSAL_BEGIN serverUtc=$($begin.endUtc) startedNanos=$($begin.candidate.startedNanos)"
$lastHeartbeat = 0.0
$probeCursor = '0'
$online = [Collections.Generic.HashSet[string]]::new()
$local = [Collections.Generic.HashSet[string]]::new()
$counts = @{}
$handoffs = [Collections.Generic.HashSet[string]]::new()
$lastMat = @{}
$stopReason = 'MAXIMUM_WINDOW'
while ($watch.Elapsed.TotalSeconds -lt 55.0) {
    if ($watch.Elapsed.TotalSeconds - $lastHeartbeat -ge 5.0) {
        Write-PilotHeartbeat $context ([string]$session.sessionId) $run
        $lastHeartbeat = $watch.Elapsed.TotalSeconds
    }
    $probe = Invoke-Trace 'SNAPSHOT_PHANTOM_CAUSAL_TRACE' @{afterSeq=$probeCursor;maxEvents='128'}
    $rawPath = Join-Path $context.PilotRoot ("results/" + $probe.requestId + '.xml')
    foreach ($match in [regex]::Matches([IO.File]::ReadAllText($rawPath), '\bevent\.\d+="([^"]*)"')) {
        $f = [Net.WebUtility]::HtmlDecode($match.Groups[1].Value).Split("`t")
        if ($f.Count -ne 11) { throw 'Raw causal separator unavailable.' }
        $id = $f[3]
        if ($f[4] -ceq 'LOCAL_CANDIDATE' -and $f[5] -ceq 'ONLINE') { $null = $online.Add($id) }
        if ($f[4] -ceq 'LOCAL_CANDIDATE' -and $f[5] -ceq 'LOCAL' -and $f[7] -ceq 'physical.demand') { $null = $local.Add($id) }
        if (-not ($online.Contains($id) -and $local.Contains($id))) { continue }
        if ($f[4] -ceq 'MATERIALIZE_CALL' -and $f[7] -ceq 'service.NATIVE_CONTEXT_HANDOFF') { $null = $handoffs.Add($id); $lastMat[$id] = 'MATERIALIZE_CALL' }
        if (-not $handoffs.Contains($id)) { continue }
        if ($f[4].StartsWith('MAT_')) { $lastMat[$id] = "$($f[4])/$($f[5])/$($f[6])" }
        if ($f[4] -ceq 'MATERIALIZE_RESULT' -and $f[5] -ceq 'SUCCESS' -and $f[10] -ceq '1') { $stopReason = "NATURAL_WORLD_PRESENT profile=$id objectId=$($f[8]) epoch=$($f[9]) seq=$($f[0])"; break }
        $blocked = ($f[4] -ceq 'MATERIALIZE_RESULT' -and $f[5] -cne 'SUCCESS')
        if ($blocked) {
            $key = "$id|$($lastMat[$id])|$($f[4])|$($f[5])|$($f[6])|$($f[7])"
            if (-not $counts.ContainsKey($key)) { $counts[$key] = 0 }
            $counts[$key]++
            if ($counts[$key] -ge 5) { $stopReason = "REPEATED_EXACT_BLOCKER count=$($counts[$key]) key=$key seq=$($f[0])"; break }
        }
    }
    $probeCursor = [string]$probe.candidate.nextSeq
    if ($stopReason -cne 'MAXIMUM_WINDOW') { break }
    if ($probe.candidate.hasMore -cne 'true') { Start-Sleep -Milliseconds 100 }
}
$page = Invoke-Trace 'END_PHANTOM_CAUSAL_TRACE' @{maxEvents='128'}
$serverSeconds = ([long]$page.candidate.snapshotNanos - [long]$begin.candidate.startedNanos) / 1e9
@{serverSeconds=$serverSeconds;wallSeconds=$watch.Elapsed.TotalSeconds;stopReason=$stopReason;online=@($online | ForEach-Object { $_ });local=@($local | ForEach-Object { $_ });blockers=$counts} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $private 'timing.json') -Encoding utf8
Write-Output "CAUSAL_END seconds=$serverSeconds stopReason=$stopReason watched=$($page.candidate.watched)"
$pageCount = 1
while ($page.candidate.hasMore -ceq 'true') {
    $cursor = [string]$page.candidate.nextSeq
    $page = Invoke-Trace 'SNAPSHOT_PHANTOM_CAUSAL_TRACE' @{afterSeq=$cursor;maxEvents='128'}
    if ([long]$page.candidate.nextSeq -le [long]$cursor -or ++$pageCount -gt 8192) { throw 'Frozen pagination did not advance.' }
}
& python (Join-Path $PSScriptRoot 'Recover-Trace.py')
if ($LASTEXITCODE -ne 0) { throw 'Frozen export failed.' }
if ($serverSeconds -gt 60.0) { throw "Trace exceeded required 60 seconds: $serverSeconds" }
Write-Output "CAUSAL_EXPORT_COMPLETE pages=$pageCount retained=$($page.candidate.retained) dropped=$($page.candidate.dropped)"
