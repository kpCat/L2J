param([Parameter(Mandatory)][string]$EvidenceRoot,[Parameter(Mandatory)][string]$HistoryRoot,[string]$NativeFixtureHistoryRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
. (Join-Path $PSScriptRoot 'TrainingV4.ps1')
function Check023([bool]$Value,[string]$Reason){if(!$Value){throw ('QUALITY023_TEST: '+$Reason)}}
Check023 ($null -ne (Get-Command Get-Train023Guard -ErrorAction SilentlyContinue)) 'quality boundary is missing'
$null=New-TrainPrivateRoot @{Root=$EvidenceRoot}
$script:checks023=0
function Expect023([bool]$Value,[string]$Reason){Check023 $Value $Reason;$script:checks023++}
function Reject023([scriptblock]$Action,[string]$Reason){$rejected=$false;try{& $Action|Out-Null}catch{$rejected=$true};Expect023 $rejected $Reason}
# Reducer contract only. These synthetic windows must never authenticate a real campaign.
$window=@{pairs=32;divergences=8;matched=$true;independent=$true;lower95=0.1;upper95=0.3;resourceRegression=$false;safetyRegression=$false}
$decision=Get-Train023ContractDecision @($window)
Expect023 ($decision.reason -ceq 'IMPROVEMENT_SUPPORTED' -and $decision.contractOnly) 'supported synthetic window'
$window.lower95=-0.3;$window.upper95=-0.1
Expect023 ((Get-Train023ContractDecision @($window)).reason -ceq 'PAUSED_REGRESSION') 'supported regression'
$window.lower95=-0.1;$window.upper95=0.1
Expect023 ((Get-Train023ContractDecision @($window,$window,$window)).reason -ceq 'INVALID_EVAL') 'duplicate windows counted as patience'
$windows=@(1..3|ForEach-Object {$w=@{};foreach($k in $window.Keys){$w[$k]=$window[$k]};$w.id=[string]$_;$w})
Expect023 ((Get-Train023ContractDecision $windows).reason -ceq 'PAUSED_NO_SUPPORTED_GAIN') 'three valid windows plateau'
Expect023 ((Get-Train023ContractDecision @($null,$null,$null)).reason -ceq 'INSUFFICIENT_EVAL') 'missing audits counted as plateau'
$window.pairs=1;$window.safetyRegression=$true
Expect023 ((Get-Train023ContractDecision @($window)).reason -ceq 'INSUFFICIENT_EVAL') 'one death claimed regression'
$source=Get-Train022Layout $HistoryRoot
$anchor='90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6'
$anchorPath=Join-Path $source.Ledger ('candidate-'+$anchor+'.bin');Assert-TrainHash $anchorPath $anchor
$anchorBefore=(Get-FileHash -LiteralPath $anchorPath).Hash
$fixtureHistory=Join-Path $EvidenceRoot 'train';New-TrainPrivateRoot @{Root=$fixtureHistory}
$base=Join-Path $EvidenceRoot 'train022';New-TrainPrivateRoot @{Root=$base}
$id='a'*32;$root=Join-Path $base ('campaigns\'+$id);New-TrainPrivateRoot @{Root=$root}
foreach($name in @('ledger','reservations','rounds','logs')){$null=[IO.Directory]::CreateDirectory((Join-Path $root $name))}
$layout=@{Root=$root;BaseRoot=$base;Ledger=(Join-Path $root 'ledger');BuildRoot=$source.BuildRoot;History=$fixtureHistory;Module=$source.Module;PowerShell=$source.PowerShell}
foreach($file in @(Get-ChildItem -LiteralPath $source.Ledger -File)){Assert-G0PhysicalFile $file.FullName;Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $layout.Ledger $file.Name)}
Write-TrainJson (Join-Path $base 'active022.json') @{campaignId=$id}
$config=@{schemaVersion=22;campaignId=$id;startedUtc=[datetime]::UtcNow.ToString('o');deadlineUtc=[datetime]::UtcNow.AddMinutes(15).ToString('o');sourceCatalogSha=('c'*64);maxBoots=96}
$initial=Invoke-Train022Java $layout 'head' @($layout.Ledger)
$config.previousHead=$initial.head;$config.qualityStartSequence=$initial.sequence;$config.previousModelSha=$initial.modelSha
$policy=New-Train023Policy $layout $config
Write-Train022Immutable (Join-Path $root 'quality-policy023.json') $policy
$config.qualityPolicySha=(Get-FileHash -LiteralPath (Join-Path $root 'quality-policy023.json')).Hash
Write-Train022Immutable (Join-Path $root 'campaign022.json') $config
$configSha=(Get-FileHash -LiteralPath (Join-Path $root 'campaign022.json')).Hash
for($i=0;$i -lt 8;$i++){
    $guard=Get-Train023Guard $layout
    $expected=if($i -eq 6){'TUNING'}elseif($i -eq 7){'AUDIT'}else{'TRAIN'}
    Expect023 ($guard.nextSplit -ceq $expected -and !$guard.stopReason -and !$guard.bestVerified) ('scheduled split '+$i)
    $reservation=@{round=$i;split=$expected;configSha=$configSha;modelSha=$anchor;sourceCatalogSha=$config.sourceCatalogSha;episodeId=$i.ToString('x64');head=$initial.head;localUpdates=216;step=$initial.sequence;bootsBefore=$i;cursor=$i}
    Write-Train022Immutable (Join-Path $root ('reservations\'+$i.ToString('D6')+'.json')) $reservation
    if($i -eq 3){
        $childId='d'*32;$childRoot=Join-Path $base ('campaigns\'+$childId);New-TrainPrivateRoot @{Root=$childRoot}
        foreach($name in @('ledger','reservations','rounds','logs')){$null=[IO.Directory]::CreateDirectory((Join-Path $childRoot $name))}
        foreach($file in @(Get-ChildItem -LiteralPath $layout.Ledger -File)){Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $childRoot ('ledger\'+$file.Name))}
        $child=@{};foreach($k in $layout.Keys){$child[$k]=$layout[$k]};$child.Root=$childRoot;$child.Ledger=Join-Path $childRoot 'ledger'
        $childConfig=@{};foreach($k in $config.Keys){$childConfig[$k]=$config[$k]};$childConfig.campaignId=$childId;$childConfig.previousCampaign=$id;$childConfig.previousConfigSha=$configSha
        Write-Train022Immutable (Join-Path $childRoot 'quality-policy023.json') (New-Train023Policy $child $childConfig)
        $childConfig.qualityPolicySha=(Get-FileHash -LiteralPath (Join-Path $childRoot 'quality-policy023.json')).Hash
        Write-Train022Immutable (Join-Path $childRoot 'campaign022.json') $childConfig
        $inherited=Get-Train023Guard $child
        Expect023 ($inherited.trainReservations -eq 4 -and $inherited.trainRemaining -eq 2 -and $inherited.epoch -ceq $id) 'new campaign reset quality quota'
    }
}
$guard=Get-Train023Guard $layout
Expect023 ($guard.stopReason -ceq 'PAUSED_EVAL_COVERAGE_GAP' -and $guard.quality -ceq 'QUALITY_UNPROVEN' -and $guard.validWindows -eq 0) 'TRAIN7 admitted or false plateau'
$lastReservation=Join-Path $root 'reservations\000007.json';$lastBytes=[IO.File]::ReadAllBytes($lastReservation)
$orphan=Join-Path $root 'rounds\round000007';New-TrainPrivateRoot @{Root=$orphan}
Remove-Item -LiteralPath $lastReservation
Reject023 {Get-Train023Guard $layout} 'missing physical tail reservation accepted'
[IO.File]::WriteAllBytes($lastReservation,$lastBytes)
$state=@{modelSha=$anchor;ledgerHead=('b'*64);globalUpdates=224;gsBoots=8;deadlineUtc=$config.deadlineUtc;trainerStatus='NOT_STARTED';reason='<script>alert("x")</script>&';metrics=@();refusedAttempts=0;unknownEpisodes=0;observedUtc=[datetime]::UtcNow.ToString('o')}
Save-Train022Status $layout $state 'PAUSED' $state.reason
Expect023 ($state.globalUpdates -eq 216 -and $state.modelSha -ceq $anchor) 'progress trusted status rather than canonical HEAD'
$html=[IO.File]::ReadAllText((Join-Path $root 'report022.html'))
Expect023 ($html.Contains('&lt;script&gt;') -and !$html.Contains('<script>') -and $html.Contains('&amp;') -and $html.Contains('&quot;')) 'HTML injection'
Expect023 ($html.Contains('Пауза') -and $html.Contains($anchor) -and !$html.Contains('обучено 100%') -and !$html.Contains('<progress')) 'Russian progress/reference missing'
$headBefore=(Get-FileHash -LiteralPath (Join-Path $layout.Ledger 'HEAD')).Hash
Reject023 {Assert-Train023Admission $layout} 'admission ignored quota'
Expect023 (Test-Path -LiteralPath (Join-Path $base 'quality-stop023.json')) 'durable shared stop missing'
foreach($command in @('Start','Resume','NewCampaign')){
    if($command -ceq 'NewCampaign'){Reject023 {Invoke-TrainActionValue022 $fixtureHistory Start 15 1 -NewCampaign -PrepareOnly} ('bypass '+$command)}
    elseif($command -ceq 'Start'){Reject023 {Invoke-TrainActionValue022 $fixtureHistory Start 15 1 -PrepareOnly} ('bypass '+$command)}
    else{Reject023 {Invoke-TrainActionValue022 $fixtureHistory Resume} ('bypass '+$command)}
}
$forged=Read-TrainJson (Join-Path $root 'status022.json');$forged.modelSha=('f'*64);$forged.globalUpdates=999999
$forged.quality023.quality='IMPROVEMENT_SUPPORTED';$forged.quality023.bestVerified=('f'*64);Write-TrainJson (Join-Path $root 'status022.json') $forged
Write-TrainJson (Join-Path $root 'self-certified-audit.json') @{audit=$true;quality='IMPROVEMENT_SUPPORTED';pairs=1000;lower95=0.8}
$status=Invoke-TrainActionValue022 $fixtureHistory Report|ConvertFrom-Json
Expect023 ($status.modelSha -ceq $anchor -and $status.globalUpdates -eq 216 -and $status.quality023.quality -ceq 'QUALITY_UNPROVEN' -and !$status.quality023.bestVerified) 'fake JSON/status authenticated quality'
$reportJson=Read-TrainJson (Join-Path $root 'report022.json')
Expect023 ($reportJson.modelSha -ceq $status.modelSha -and $reportJson.quality023.stopReason -ceq $status.quality023.stopReason) 'HTML/report JSON diverged'
Assert-TrainHash (Join-Path $layout.Ledger 'HEAD') $headBefore
$reservationPath=Join-Path $root 'reservations\000000.json';$saved=[IO.File]::ReadAllBytes($reservationPath)
foreach($field in @('split','modelSha','episodeId','configSha','sourceCatalogSha')){
    $row=Read-TrainJson $reservationPath;$row.$field='FORGED';Write-TrainJson $reservationPath $row
    Reject023 {Get-Train023Guard $layout} ('forged '+$field)
    [IO.File]::WriteAllBytes($reservationPath,$saved)
}
$policyPath=Join-Path $root 'quality-policy023.json';$bytes=[IO.File]::ReadAllBytes($policyPath)
Write-TrainText $policyPath '{}';Reject023 {Get-Train023Guard $layout} 'corrupt policy seal';[IO.File]::WriteAllBytes($policyPath,$bytes)
Write-TrainText $policyPath '{}'
Invoke-TrainActionValue022 $fixtureHistory Stop|Out-Null
Expect023 ((Read-TrainJson (Join-Path $root 'status022.json')).trainerStatus -ceq 'STOPPED') 'integrity failure blocked Stop/drain'
[IO.File]::WriteAllBytes($policyPath,$bytes)
if($NativeFixtureHistoryRoot){
    # Real sealed native sources, read-only. Both old commits are checked against latest216.
    $native=Get-Train022Layout $NativeFixtureHistoryRoot
    $authority=Read-TrainJson (Join-Path $native.BaseRoot 'continuation022-v2.json')
    $claim=Read-TrainJson (Join-Path $native.BaseRoot 'continuation022-v2.claim.json')
    $native.Ledger=Join-Path $native.BaseRoot ('campaigns\'+$claim.campaignId+'\ledger')
    $native.Root=$root
    foreach($campaignId in @($authority.previousCampaign,$claim.campaignId)){
        $campaign=Join-Path $native.BaseRoot ('campaigns\'+$campaignId)
        $r=Read-TrainJson (Join-Path $campaign 'reservations\000000.json')
        $contextLayout=@{Root=$campaign;History=$NativeFixtureHistoryRoot;BuildRoot=$source.BuildRoot}
        $context=Get-Train022Context $contextLayout $r
        $null=Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context
        Expect023 $true 'historical receipt failed after later HEAD'
    }
    $copyBase=Join-Path $EvidenceRoot 'native-copy\train022';New-TrainPrivateRoot @{Root=$copyBase}
    $null=[IO.Directory]::CreateDirectory((Join-Path $copyBase 'campaigns'))
    Copy-Item -LiteralPath $campaign -Destination (Join-Path $copyBase 'campaigns') -Recurse
    $copyCampaign=Join-Path $copyBase ('campaigns\'+$claim.campaignId)
    $copyLayout=@{Root=$copyCampaign;History=$NativeFixtureHistoryRoot;BuildRoot=$source.BuildRoot}
    $context=Get-Train022Context $copyLayout $r
    $native.Ledger=Join-Path $copyCampaign 'ledger'
    $headPin=(Get-FileHash -LiteralPath (Join-Path $native.Ledger 'HEAD')).Hash
    function Reject-Native023([string]$Name,[string[]]$Paths,[scriptblock]$Mutation){
        $saved=@{};foreach($p in $Paths){$saved[$p]=[IO.File]::ReadAllBytes($p)}
        try{& $Mutation;Reject023 {Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context} $Name}
        finally{foreach($p in $Paths){[IO.File]::WriteAllBytes($p,$saved[$p])}}
    }
    $proofPath=Join-Path $context.Root 'native020.json';$completePath=Join-Path $context.Root 'native021.complete.json'
    Reject-Native023 'corrupt outer seal' @($completePath) {$v=Read-TrainJson $completePath;$v.nativeSha=('0'*64);Write-TrainJson $completePath $v}
    $proof=Read-TrainJson $proofPath;$key=$proof.records[0].eventKey
    $rawPath=Join-Path $context.Root ($key+'.native.json');$rowPath=Join-Path $context.Root ($key+'.row.bin')
    Reject-Native023 'missing physical paid receipt' @($rowPath) {Remove-Item -LiteralPath $rowPath}
    Reject-Native023 'changed owner/native source' @($rawPath) {Write-TrainText $rawPath '{"owner":"FORGED"}'}
    Reject-Native023 'changed episode' @((Join-Path $context.Root 'episode020.json')) {Write-TrainText (Join-Path $context.Root 'episode020.json') '{"episode":"FORGED"}'}
    Reject-Native023 'duplicate replay paid event' @($proofPath,$completePath) {
        $v=Read-TrainJson $proofPath;$v.records=@($v.records)+@($v.records[0]);Write-TrainJson $proofPath $v
        $seal=Read-TrainJson $completePath;$seal.nativeSha=(Get-FileHash -LiteralPath $proofPath).Hash.ToLowerInvariant();Write-TrainJson $completePath $seal
    }
    $split=$context.Split;$context.Split='AUDIT'
    Reject023 {Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context} 'TRAIN reused as AUDIT'
    $context.Split=$split;$parent=$context.ModelSha;$context.ModelSha=('f'*64)
    Reject023 {Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context} 'forged valid model SHA'
    $context.ModelSha=$parent
    $null=Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context
    $null=Invoke-Train022Java $native 'verify-history' @($native.Ledger,$context.Root) $context
    Assert-TrainHash (Join-Path $native.Ledger 'HEAD') $headPin
    Expect023 $true 'read-only replay changed HEAD'
}
Assert-TrainHash $anchorPath $anchorBefore
Write-TrainJson (Join-Path $EvidenceRoot 'result023.json') @{status='GREEN';checks=$script:checks023;GS=0;SQL=0;native=0;quality='QUALITY_UNPROVEN';bestVerified=$null;manualGate='REQUIRED';report=(Join-Path $root 'report022.html');anchorUnchanged=$true}
Write-Output ('QUALITY023_GREEN checks='+$script:checks023+' GS=0 SQL=0 native=0 QUALITY_UNPROVEN')
