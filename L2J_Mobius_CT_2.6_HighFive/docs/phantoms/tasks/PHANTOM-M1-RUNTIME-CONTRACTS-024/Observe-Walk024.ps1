[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [ValidateRange(35000,40000)][int]$OutsideX=38000)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
if($runtime -notmatch '[\\/]contract024[a-h][\\/]runtime$'){throw 'Exact TASK023 runtime required.'}
if(Test-Path -LiteralPath $OutputRoot){throw 'Evidence exists.'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$run=[guid]::NewGuid().ToString('D'); $counter=0; $started=$false
$clock=[Diagnostics.Stopwatch]::StartNew()
$waterBounds=@(Get-ChildItem (Join-Path $runtime 'game/data/zones') -File -Filter '*.xml' | ForEach-Object {
    [xml]$doc=Get-Content $_.FullName -Raw
    foreach($zone in @($doc.list.zone | Where-Object {$_.type -ceq 'WaterZone'})){
        if($zone.shape -notin @('NPoly','Cuboid')){throw 'Unknown water zone geometry.'}
        $xs=@($zone.node | ForEach-Object {[int]$_.X}); $ys=@($zone.node | ForEach-Object {[int]$_.Y})
        [pscustomobject]@{name=[string]$zone.name;minX=($xs | Measure-Object -Minimum).Minimum;maxX=($xs | Measure-Object -Maximum).Maximum;minY=($ys | Measure-Object -Minimum).Minimum;maxY=($ys | Measure-Object -Maximum).Maximum;minZ=[int]$zone.minZ;maxZ=[int]$zone.maxZ}
    }
})
function Call023([string]$Operation,[hashtable]$Arguments=@{}){
    if($clock.Elapsed.TotalSeconds -gt 480 -or $counter -ge 350){throw 'Bounded walk session exhausted.'}
    $raw=& (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run -Operation $Operation -Arguments $Arguments -TimeoutSeconds 10
    $result=$raw | ConvertFrom-Json -DateKind String
    $script:counter++; $name=('{0:D4}-{1}' -f $script:counter,$Operation)
    $raw | Set-Content (Join-Path $OutputRoot "$name.json") -Encoding utf8
    $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic
    Copy-Item -LiteralPath (Join-Path $context.PilotRoot "results/$($result.requestId).xml") -Destination (Join-Path $OutputRoot "$name.xml")
    if($result.status -notin @('SUCCEEDED','ACCEPTED')){throw "WALK_REJECTED:$Operation/$($result.reason)"}
    if($result.before.identityOwner -cne 'LOCALPLAY_TEST_HUMAN' -or $result.before.clientIdentity -cne 'none'){throw 'Exact ordinary synthetic identity changed.'}
    return $result
}
function Census023{
    $actors=[Collections.Generic.List[object]]::new();$after=0L
    for($page=0;$page -lt 8;$page++){
        $r=Call023 'SNAPSHOT_PHANTOMS' @{includeCensus='true';censusAfterProfileId="$after"}
        for($i=1;$i -le [int]$r.candidate.censusCount;$i++){
            $prefix="census$i."; $fields=[ordered]@{}
            foreach($p in $r.candidate.PSObject.Properties){if($p.Name.StartsWith($prefix)){$fields[$p.Name.Substring($prefix.Length)]=$p.Value}}
            $actors.Add([pscustomobject]$fields)
        }
        if(-not $r.candidate.PSObject.Properties['censusNextProfileId']){return @($actors)}
        $next=[long]$r.candidate.censusNextProfileId
        if($next -le $after){return @($actors)}
        $after=$next
    }
    throw 'Walk bounded pagination exhausted; incomplete census is not accepted.'
}
function Walk023([int]$DestinationX,[int]$DestinationY){
    while($true){
        $s=(Call023 'STATUS').after
        $dx=$DestinationX-[int]$s.x; $dy=$DestinationY-[int]$s.y
        $length=[Math]::Sqrt($dx*$dx+$dy*$dy)
        if($length -le 24){break}
        $scale=[Math]::Min(1.0,300.0/[double]$length)
        $x=[int]$s.x+[int]($dx*$scale); $y=[int]$s.y+[int]($dy*$scale)
        foreach($water in $waterBounds){
            if([int]$s.x -ge $water.minX -and [int]$s.x -le $water.maxX -and [int]$s.y -ge $water.minY -and [int]$s.y -le $water.maxY -and [int]$s.z -ge $water.minZ -and [int]$s.z -le $water.maxZ){throw "Native position entered water bounds: $($water.name)"}
        }
        $null=Call023 'MOVE_SELF' @{x="$x";y="$y";z=[string]$s.z}
        $arrival=[Diagnostics.Stopwatch]::StartNew()
        do{
            Start-Sleep -Seconds 1
            $s=(Call023 'STATUS').after
            if($s.moving -ceq 'false' -and [Math]::Sqrt([Math]::Pow([int]$s.x-$x,2)+[Math]::Pow([int]$s.y-$y,2)) -le 32){break}
            if($arrival.Elapsed.TotalSeconds -gt 12){throw 'Native move arrival unconfirmed.'}
        }while($true)
        $null=Census023
    }
    $null=Call023 'STOP_MOVE'
}
try{
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-start.json') -Encoding utf8
    $started=$true; $origin=(Call023 'STATUS').after
    # Preflight every100 units with actual stock heights and native geometry in both directions.
    Push-Location (Join-Path $runtime 'game')
    try{
        & java -cp '../libs/*' (Join-Path $PSScriptRoot 'ReadDryPath023.java') ([string]$origin.x) ([string]$origin.y) ([string]$origin.z) ([string]$OutsideX) ([string]$origin.y) *> (Join-Path $OutputRoot 'dry-path.txt')
        if($LASTEXITCODE -ne 0){throw 'Factual stock dry-path preflight rejected; no walk requested.'}
    }finally{Pop-Location}
    $warm=[Diagnostics.Stopwatch]::StartNew()
    do{
        $baseline=@(Census023)
        if($baseline.Count -gt 0){break}
        if($warm.Elapsed.TotalSeconds -ge 30){throw 'No natural visible actor for independent walk gate.'}
        Start-Sleep -Seconds 3
    }while($true)
    $baseline | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'baseline.json') -Encoding utf8
    $ids=@($baseline | ForEach-Object {[long]$_.profileId})
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'before') -ProfileIds $ids
    Walk023 $OutsideX ([int]$origin.y)
    $outside=(Call023 'STATUS').after
    $outside | ConvertTo-Json | Set-Content (Join-Path $OutputRoot 'outside.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'away-early') -ProfileIds $ids
    for($i=0;$i -lt 8;$i++){Start-Sleep -Seconds 10; $null=Census023}
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'away-late') -ProfileIds $ids
    Walk023 ([int]$origin.x) ([int]$origin.y)
    for($i=0;$i -lt 6;$i++){Start-Sleep -Seconds 10; $returned=@(Census023)}
    $returned | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputRoot 'returned.json') -Encoding utf8
    & (Join-Path $PSScriptRoot 'Read-Clone024.ps1') -RuntimeRoot $runtime -OutputRoot (Join-Path $OutputRoot 'after') -ProfileIds $ids
    "NATIVE_WALK_OBSERVED seconds=$($clock.Elapsed.TotalSeconds) requests=$counter; gate requires receipt/epoch review"
}finally{
    if($started){& (Join-Path $runtime 'Stop-LocalPlayPilot.ps1') -ActorMode Synthetic -RunId $run | Set-Content (Join-Path $OutputRoot 'synthetic-stop.json') -Encoding utf8}
}
