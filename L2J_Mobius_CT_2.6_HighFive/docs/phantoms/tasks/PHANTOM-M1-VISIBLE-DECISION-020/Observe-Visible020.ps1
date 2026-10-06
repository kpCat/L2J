[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot, [Parameter(Mandatory)][string]$OutputRoot,
      [ValidateSet('BASELINE','POLL')][string]$Stage='BASELINE')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $RuntimeRoot 'LocalPlay-Pilot.ps1')
$context=Get-PilotContext -RequireEnabled -ActorMode RealClient
if ($Stage -eq 'BASELINE') {
    if (Test-Path -LiteralPath $OutputRoot) { throw 'No automatic observation restart.' }
    $session=Get-PilotSession $context
    if ($session.state -cne 'ARMED_IDLE') { throw 'Manual actor must be ARMED_IDLE.' }
    New-Item -ItemType Directory -Path $OutputRoot | Out-Null
    $run=[guid]::NewGuid().ToString('D')
    $run | Set-Content (Join-Path $OutputRoot 'run-id.txt') -Encoding utf8
    $observer=$null
} else {
    $run=(Get-Content (Join-Path $OutputRoot 'run-id.txt') -Raw).Trim()
    $baseline=Get-Content (Join-Path $OutputRoot 'baseline.json') -Raw | ConvertFrom-Json
    $observer=$baseline.observer
    $started=[DateTimeOffset]::Parse($baseline.utc)
    if (([DateTimeOffset]::UtcNow-$started).TotalSeconds -gt 120) { throw 'Same-epoch observation limit120s reached.' }
}
function Capture([string]$Label,[ValidateSet('STATUS','SNAPSHOT_PHANTOMS','SELECT_VISIBLE_PHANTOM_TRACE','SNAPSHOT_SELECTED_PHANTOM_TRACE')][string]$Operation,[hashtable]$Arguments=@{}) {
    $json=& (Join-Path $RuntimeRoot 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -RunId $run -Arguments $Arguments -ActorMode RealClient -TimeoutSeconds 10
    $result=$json | ConvertFrom-Json
    $json | Set-Content (Join-Path $OutputRoot "$Label.json") -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $context.PilotRoot "results/$($result.requestId).xml") -Destination (Join-Path $OutputRoot "$Label.xml")
    if ($result.status -cne 'SUCCEEDED') { throw "Read-only $Operation rejected: $($result.reason)" }
    foreach ($key in @('x','y','z','instanceId','targetId','moving','teleporting')) {
        if ($result.before.$key -cne $result.after.$key) { throw "Observer changed within $Operation/$key" }
        if ($null -ne $observer -and $result.before.$key -cne $observer.$key) { throw "Observer changed since baseline/$key" }
    }
    Write-Host "$Label status=$($result.status) reason=$($result.reason) utc=$($result.endUtc)"
    return $result
}
function Census([string]$Label,[string]$Profile) {
    $after=0L
    for ($page=0;$page -lt 8;$page++) {
        $result=Capture "$Label-page$page" 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        for ($index=1;$index -le [int]$result.candidate.censusCount;$index++) {
            $prefix="census$index."
            if ($result.candidate.PSObject.Properties["${prefix}profileId"].Value -cne $Profile) { continue }
            $fields=[ordered]@{}
            foreach ($property in $result.candidate.PSObject.Properties) { if ($property.Name.StartsWith($prefix)) { $fields[$property.Name.Substring($prefix.Length)]=$property.Value } }
            return [pscustomobject]@{utc=$result.endUtc;fields=[pscustomobject]$fields}
        }
        $next=[long]$result.candidate.censusNextProfileId
        if ($next -le $after) { break }
        $after=$next
    }
    throw 'Selected natural visible epoch absent from census; stop observation.'
}
if ($Stage -eq 'BASELINE') {
    $status=Capture 'STATUS' 'STATUS'
    if ($status.before.identityOwner -cne 'REAL_LOGIN' -or $status.before.online -cne 'true' -or $status.before.worldPresent -cne 'true' -or $status.before.clientIdentity -ceq 'none' -or $status.before.moving -cne 'false' -or $status.before.teleporting -cne 'false') { throw 'REAL_LOGIN/IN_GAME passive actor not verified.' }
    $observer=$status.before
    $initial=Capture 'PHANTOMS-initial' 'SNAPSHOT_PHANTOMS' @{includeCensus='true'}
    $selected=Capture 'SELECT' 'SELECT_VISIBLE_PHANTOM_TRACE'
    $profile=[string]$selected.candidate.profileId
    $trace=Capture 'TRACE-baseline' 'SNAPSHOT_SELECTED_PHANTOM_TRACE'
    $census=Census 'CENSUS-baseline' $profile
    $record=[ordered]@{utc=$census.utc;observer=$observer;profileId=$profile;trace=$trace.candidate;census=$census.fields}
    $record | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'baseline.json') -Encoding utf8
    $record | ConvertTo-Json -Depth 8
} else {
    $label='POLL-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $trace=Capture "$label-trace" 'SNAPSHOT_SELECTED_PHANTOM_TRACE'
    $census=Census "$label-census" ([string]$baseline.profileId)
    foreach ($key in @('profileId','objectId','materializedAtNanos','nativeEvidenceEpoch')) {
        if ($census.fields.$key -cne $baseline.census.$key) { throw "Selected epoch changed/$key; stop without reselection." }
    }
    [ordered]@{utc=$census.utc;elapsedSeconds=([DateTimeOffset]::Parse($census.utc)-$started).TotalSeconds;trace=$trace.candidate;census=$census.fields} | ConvertTo-Json -Depth 8 | Tee-Object -FilePath (Join-Path $OutputRoot "$label.json")
}
