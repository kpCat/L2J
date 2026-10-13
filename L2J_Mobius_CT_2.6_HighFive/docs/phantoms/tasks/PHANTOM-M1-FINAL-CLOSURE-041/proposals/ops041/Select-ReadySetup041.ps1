param([Parameter(Mandatory)][string]$CensusRoot,[Parameter(Mandatory)][string]$OutputFile)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $OutputFile){throw 'Immutable prospective selection exists.'}
$source041=Join-Path $CensusRoot 'global-admission.tsv'
$rows041=@(Import-Csv $source041 -Delimiter "`t")
$eligible041=@($rows041 | Where-Object {$_.populationState -ceq 'READY' -and $_.state -ceq 'READY' -and $_.calendarOnline -ceq 'true' -and $_.farmAllowed -ceq 'true' -and $_.instanceId -ceq '0' -and $_.nextBoundary -and [DateTimeOffset]::Parse($_.nextBoundary) -gt [DateTimeOffset]::UtcNow.AddMinutes(40) -and ([long]$_.requestedHorizonMinute-[long]$_.committedCursorMinute) -le 15})
$ranked041=@(foreach($center041 in $eligible041){
    $members041=@($eligible041 | Where-Object {[Math]::Abs(([int]$_.x -shr 11)-([int]$center041.x -shr 11)) -le 1 -and [Math]::Abs(([int]$_.y -shr 11)-([int]$center041.y -shr 11)) -le 1 -and [Math]::Abs([int]$_.z-[int]$center041.z) -le 200})
    [pscustomobject]@{profileId=[long]$center041.profileId;count=$members041.Count;prepared=($center041.readinessComplete -ceq 'true');candidate=$center041}
})
$chosen041=@($ranked041 | Where-Object count -ge 8 | Sort-Object @{Expression='prepared';Descending=$true},@{Expression='count';Descending=$true},@{Expression='profileId';Descending=$true} | Select-Object -First 1)
if($chosen041.Count -ne 1){throw 'No current READY/calendar/farm-eligible eight-member locality before outcomes.'}
[ordered]@{rule='CURRENT_READY_CALENDAR40MIN_FARM_CURSOR15_NATIVE_LOCALITY_PREPARED_COUNT_ID_DESC';utc=[DateTime]::UtcNow.ToString('o');source=$source041;sourceSha256=(Get-FileHash $source041).Hash;selected=$chosen041[0];outcomesInspected=$false} | ConvertTo-Json -Depth 6 | Set-Content $OutputFile -Encoding utf8
$chosen041[0].profileId
