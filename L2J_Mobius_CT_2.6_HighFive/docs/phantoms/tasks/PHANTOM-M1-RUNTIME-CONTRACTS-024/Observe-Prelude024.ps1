[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [ValidateRange(30,100)][int]$Seconds=80,[switch]$CrashAfterStop)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot);$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if($runtime -notmatch '[\\/]contract024c[\\/]runtime$'){throw 'Dedicated clone c required.'}
if(Test-Path -LiteralPath $OutputRoot){throw 'Prelude evidence exists.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D'); $started=$false; $counter=0
$ids=[Collections.Generic.HashSet[long]]::new()
function Snapshot023([string]$Operation,[hashtable]$Arguments=@{}){
    $raw=& (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run -Operation $Operation -Arguments $Arguments -TimeoutSeconds 10
    $r=$raw | ConvertFrom-Json -DateKind String
    $script:counter++;$name=('{0:D4}-{1}' -f $script:counter,$Operation)
    $raw | Set-Content (Join-Path $OutputRoot "$name.json") -Encoding utf8
    $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic
    Copy-Item -LiteralPath (Join-Path $context.PilotRoot "results/$($r.requestId).xml") -Destination (Join-Path $OutputRoot "$name.xml")
    if($r.status -cne 'SUCCEEDED' -or $r.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $r.before.clientIdentity -cne 'none'){throw 'Prelude read-only native identity rejected.'}
    foreach($key in @('x','y','z','instanceId','moving','targetId','teleporting')){if($r.before.$key -cne $r.after.$key){throw 'Prelude read mutated observer.'}}
    if($Operation -ceq 'SNAPSHOT_PHANTOMS'){
        for($i=1;$i -le [int]$r.candidate.censusCount;$i++){$null=$ids.Add([long]$r.candidate."census$i.profileId")}
    }
    return $r
}
function Census023{
    $after=0L
    for($page=0;$page -lt 8;$page++){
        $r=Snapshot023 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        if(-not $r.candidate.PSObject.Properties['censusNextProfileId']){return}
        $next=[long]$r.candidate.censusNextProfileId
        if($next -le $after){return}
        $after=$next
    }
    throw 'Prelude bounded pagination exhausted; incomplete census is not accepted.'
}
try{
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true; $null=Snapshot023 'STATUS'; Census023
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'baseline') -ProfileIds @($ids)
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while($watch.Elapsed.TotalSeconds -lt $Seconds){Start-Sleep -Seconds 10;Census023}
    $ids | Sort-Object | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'observed-profile-ids.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'before-stop') -ProfileIds @($ids)
    "PRELUDE_RECORDED seconds=$($watch.Elapsed.TotalSeconds);noFinalSceneClaim=true;noReal=true"
}finally{
    if($started){& (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8}
}
if($CrashAfterStop){& (Join-Path $PSScriptRoot 'Crash-ExactOwned024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'real-process-crash')}
