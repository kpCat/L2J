param([Parameter(Mandatory)][string]$CensusRoot,[Parameter(Mandatory)][string]$OutputFile)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $OutputFile){throw 'Immutable prospective selection exists.'}
$source042=Join-Path $CensusRoot 'global-admission.tsv'
$rows042=@(Import-Csv $source042 -Delimiter "`t")
$eligible042=@($rows042 | Where-Object {$_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.farmAllowed -ceq 'true' -and $_.instanceId -ceq '0' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddMinutes(40) -and ([long]$_.requestedHorizonMinute-[long]$_.committedCursorMinute) -le 15})
$ranked042=@(foreach($center042 in $eligible042){
    $members042=@($eligible042 | Where-Object {[Math]::Abs(([int]$_.x -shr 11)-([int]$center042.x -shr 11)) -le 1 -and [Math]::Abs(([int]$_.y -shr 11)-([int]$center042.y -shr 11)) -le 1 -and [Math]::Abs([int]$_.z-[int]$center042.z) -le 200})
    [pscustomobject]@{profileId=[long]$center042.profileId;count=$members042.Count;prepared=($center042.readinessComplete -ceq 'true');candidate=$center042}
})
$chosen042=@($ranked042 | Where-Object count -ge 8 | Sort-Object @{Expression='prepared';Descending=$true},@{Expression='count';Descending=$true},@{Expression='profileId';Descending=$true} | Select-Object -First 1)
if($chosen042.Count -ne 1){throw 'No current READY/calendar/farm-eligible eight-member locality before outcomes.'}
[ordered]@{rule='CURRENT_READY_CALENDAR40MIN_FARM_CURSOR15_NATIVE_LOCALITY_PREPARED_COUNT_ID_DESC';utc=[DateTime]::UtcNow.ToString('o');source=$source042;sourceSha256=(Get-FileHash $source042).Hash;selected=$chosen042[0];outcomesInspected=$false} | ConvertTo-Json -Depth 6 | Set-Content $OutputFile -Encoding utf8
$chosen042[0].profileId
