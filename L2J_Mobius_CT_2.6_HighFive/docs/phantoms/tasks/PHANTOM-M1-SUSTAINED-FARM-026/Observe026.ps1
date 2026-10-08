[CmdletBinding()]
param([ValidateSet('Probe','Discovery','Scene')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [string]$FrozenSha='',[string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{},[hashtable]$SetupMove=@{},
      [switch]$CollectSealed,[long[]]$ProbeProfileIds=@(452),[long[]]$OriginalProfileIds=@())
$ErrorActionPreference='Stop'
$taskRoot026=$PSScriptRoot
$module026=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$source026=Join-Path $module026 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/Observe025.ps1'
$body026=[IO.File]::ReadAllText($source026).Replace('$PSScriptRoot','$taskRoot026').Replace('contract025','contract026').Replace('ops025','ops026')
$body026=$body026.Replace(".Replace('`$taskRoot026',", ".Replace('`$PSScriptRoot',")
$body026=$body026.Replace('"& (Join-Path `$taskRoot026 ''Read-Clone024.ps1'') -RuntimeRoot `$runtime"', '"& (Join-Path `$PSScriptRoot ''Read-Clone024.ps1'') -RuntimeRoot `$runtime"')
$body026=$body026.Replace('Control025.ps1','Control026.ps1').Replace('Collector025.ps1','Control026.ps1')
$body026=$body026.Replace('(Get-FileHash $PSCommandPath)', "(Get-FileHash (Join-Path `$taskRoot026 'Observe026.ps1'))")
if($OriginalProfileIds.Count){
    if($Mode -ceq 'Probe' -or $Episode -notin @('c','d') -or $OriginalProfileIds.Count -ne 8 -or @($OriginalProfileIds | Select-Object -Unique).Count -ne 8 -or $CollectSealed){throw 'Full original cohort requires eight exact IDs, c/d scene/discovery, and its own sealed hook.'}
    $script:fullStarted026=$false
    $fullCensus026=@'
function Census023{
    $visible026=@(VisibleCensus026)
    if(-not $script:fullStarted026){
        $input026=Join-Path $OutputRoot 'original-cohort-request.json'
        @($OriginalProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_;materializedAtNanos=0}}) | ConvertTo-Json | Set-Content $input026 -Encoding utf8
        & (Join-Path $taskRoot026 'Control026.ps1') -Action Collector -Mode FullObserve -Episode $Episode -CohortJson $input026 -OutputRoot (Join-Path $OutputRoot 'full-native') *> (Join-Path $OutputRoot 'full-native-attach.log')
        if($LASTEXITCODE -ne 0){throw 'Full original scope attach failed.'}
        $script:fullStarted026=$true
    }
    $latest026=Join-Path $OutputRoot 'full-native/full-cohort-latest.json'
    if(-not (Test-Path $latest026) -or ([DateTime]::UtcNow-(Get-Item $latest026).LastWriteTimeUtc).TotalSeconds -gt 3){throw 'Exact full-cohort observation missing/stale.'}
    return @(Get-Content $latest026 -Raw | ConvertFrom-Json)
}
'@
    $inject026=@'
$body025=$body025.Replace('function Census023{','function VisibleCensus026{')
$body025=$body025.Insert($body025.IndexOf('function Inventory023'),$fullCensus026+"`n")
$body025=$body025.Replace('if($seen.Count -ne 1){$missing++; continue}', 'if($seen.Count -ne 1 -or $seen[0].missing -ceq ''true'' -or $seen[0].worldPresent -cne ''true''){$missing++; continue}')
$body025=$body025.Replace('$pass=$same -and $missing -eq 0', '$pass=$same -and $last[0].dead -ceq ''false'' -and $last[0].worldPresent -ceq ''true'' -and $missing -eq 0')
'@
    $body026=$body026.Replace('# Leave time for the existing mailbox census round trip inside the <=5s sampling bound.', $inject026+"`n# Leave time for the existing mailbox census round trip inside the <=5s sampling bound.")
}
& ([scriptblock]::Create($body026)) -Mode $Mode -Episode $Episode -OutputRoot $OutputRoot -Seconds $Seconds -FrozenSha $FrozenSha -PreviousPrimaryIds $PreviousPrimaryIds -SetupTeleport $SetupTeleport -SetupMove $SetupMove -CollectSealed:$CollectSealed -ProbeProfileIds $ProbeProfileIds
