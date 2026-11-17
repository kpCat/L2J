$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
. (Join-Path $PSScriptRoot 'TrainingV4.ps1')
function Check022([bool]$Value,[string]$Reason){if(!$Value){throw $Reason}}
$start=[datetime]'2026-11-17T00:00:00Z'
$config=@{deadlineUtc=$start.AddMinutes(480).ToString('o');startedUtc=$start.ToString('o');minDiskBytes=8GB;maxBoots=2;maxNoData=3;maxStagnation=3}
Check022 ((Get-Train022StopReason $config $start.AddMinutes(479) 100GB 1 0) -ceq '') '022 premature deadline'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(480) 100GB 1 0) -ceq 'DEADLINE') '022 deadline extended'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(-1) 100GB 1 0) -ceq 'CLOCK_SKEW') '022 clock skew accepted'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(1) 1GB 1 0) -ceq 'DISK_LIMIT') '022 disk cap ignored'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(1) 100GB 2 0) -ceq 'BOOT_LIMIT') '022 boot cap ignored'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(1) 100GB 1 3) -ceq 'NO_DATA') '022 no-data loop unbounded'
Check022 ((Get-Train022StopReason $config $start.AddMinutes(1) 100GB 1 0 3) -ceq 'STAGNATION') '022 stagnation unbounded'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('training022-'+[guid]::NewGuid().ToString('N'))
$null=[IO.Directory]::CreateDirectory($temp)
Write-Train022Immutable (Join-Path $temp 'reservation.json') @{value='проверка'}
$before=(Get-FileHash -LiteralPath (Join-Path $temp 'reservation.json')).Hash
$rejected=$false;try{Write-Train022Immutable (Join-Path $temp 'reservation.json') @{value='другой'}}catch{$rejected=$true}
Check022 ($rejected -and (Get-FileHash -LiteralPath (Join-Path $temp 'reservation.json')).Hash -ceq $before) '022 reservation overwrite'
$params=(Get-Command (Join-Path $PSScriptRoot 'Training.ps1')).Parameters
Check022 ($params.ContainsKey('ActionValue022Command') -and $params.ContainsKey('ActionValue022Minutes')) '022 CLI unavailable'
$recovery=Join-Path ([IO.Path]::GetTempPath()) ('training022-closed-'+[guid]::NewGuid().ToString('N'))
New-TrainPrivateRoot @{Root=$recovery}
Stop-Train022Children @{Root=$recovery}
Assert-Train022Closed @{Root=$recovery}
$seal=Read-TrainJson (Join-Path $recovery 'recovery-stop022.json')
Check022 ($seal.test -ceq 'OFF' -and $seal.ownedProcesses -eq 0 -and $seal.nativeCreditCreated -eq $false) '022 pre-native recovery unsafe'
Write-Train022Immutable (Join-Path $recovery 'boot-reserved020.json') @{native='identity missing'}
$rejected=$false;try{Stop-Train022Children @{Root=$recovery}}catch{$rejected=$true}
Check022 $rejected '022 missing native identity accepted'
$approval=[pscustomobject]@{schema='APPROVED_PROTECTED022_V1';authority='EXPLICIT_OWNER_CHAT';pins=@([pscustomobject]@{path='AGENTS.md';sha256=('a'*64)},[pscustomobject]@{path='build.xml';sha256=('b'*64)})}
Check022 ((Get-Train022AcceptedPins $approval @('AGENTS.md','build.xml')).Count -eq 2) '022 exact approval refused'
$approval.authority='INFERRED_FROM_DRIFT';$rejected=$false;try{$null=Get-Train022AcceptedPins $approval @('AGENTS.md','build.xml')}catch{$rejected=$true}
Check022 $rejected '022 approval inferred from drift'
$approval.authority='EXPLICIT_OWNER_CHAT';$approval.pins[1].path='Database.ini';$rejected=$false;try{$null=Get-Train022AcceptedPins $approval @('AGENTS.md','build.xml')}catch{$rejected=$true}
Check022 $rejected '022 approval escaped two-path scope'
$cpu=@{}
Check022 (!(Test-Train022CpuExceeded $cpu 100 10 2.1 $false)) '022 cold GS startup counted as sustained active CPU'
Check022 (!(Test-Train022CpuExceeded $cpu 200 60 2.1 $true)) '022 ready baseline missing'
Check022 (!(Test-Train022CpuExceeded $cpu 201 70 2.1 $true)) '022 startup cumulative CPU leaked into active delta'
Check022 (Test-Train022CpuExceeded $cpu 231 80 2.1 $true) '022 sustained CPU cap disabled'
Write-Output 'TRAIN022_CONTROL_CONTRACT_GREEN cases=18 GS=0 SQL=0'
