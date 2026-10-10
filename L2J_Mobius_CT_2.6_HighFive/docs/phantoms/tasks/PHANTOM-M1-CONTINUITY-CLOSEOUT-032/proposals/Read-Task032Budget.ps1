<# Только контроль бюджета процесса работы, не игровых таймеров и не acceptance.
Первый вызов создаёт один собственный JSON; последующие НЕ перезапускают часы.
Использовать pwsh -File ... -StatePath <own task032/evidence/BUDGET032.json>.
Не подключать к GameServer и не использовать для продления Synthetic TTL. #>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$StatePath)
$ErrorActionPreference='Stop'
$full=[IO.Path]::GetFullPath($StatePath)
if ($full -notmatch '[\\/]PHANTOM-M1-CONTINUITY-CLOSEOUT-032[\\/]evidence[\\/]BUDGET032\.json$') {
    throw 'Требуется точный own task032/evidence/BUDGET032.json.'
}
$now=[DateTimeOffset]::UtcNow
if (-not [IO.File]::Exists($full)) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($full)) | Out-Null
    $initial=[ordered]@{task='TASK032';startUtc=$now.ToString('o');freezeUtc=$now.AddHours(6).ToString('o');endUtc=$now.AddHours(8).ToString('o')}
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($initial | ConvertTo-Json))
    $stream=[IO.File]::Open($full,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try {$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)} finally {$stream.Dispose()}
}
$state=[IO.File]::ReadAllText($full) | ConvertFrom-Json
if ($state.task -cne 'TASK032') {throw 'Чужой budget state.'}
$start=[DateTimeOffset]::Parse($state.startUtc)
$freeze=[DateTimeOffset]::Parse($state.freezeUtc)
$end=[DateTimeOffset]::Parse($state.endUtc)
if (($freeze-$start).TotalMinutes -ne 360 -or ($end-$start).TotalMinutes -ne 480) {throw 'Бюджет изменён.'}
[pscustomobject]@{
    startUtc=$start.ToString('o'); elapsedMinutes=[Math]::Round(($now-$start).TotalMinutes,1)
    remainingMinutes=[Math]::Max(0,[Math]::Round(($end-$now).TotalMinutes,1))
    semanticChangesAllowed=($now -lt $freeze)
    cleanupReserve=($now -ge $end.AddMinutes(-30))
    deadlineReached=($now -ge $end)
    endUtc=$end.ToString('o')
} | ConvertTo-Json
