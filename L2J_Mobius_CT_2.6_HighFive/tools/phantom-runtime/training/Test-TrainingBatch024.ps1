param([Parameter(Mandatory)][string]$EvidenceRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
. (Join-Path $PSScriptRoot 'TrainingV4.ps1')
. (Join-Path $PSScriptRoot 'TrainingBatchV4.ps1')
function Expect024([bool]$Value,[string]$Reason){if(!$Value){throw $Reason}}
function Reject024([scriptblock]$Action){$rejected=$false;try{& $Action|Out-Null}catch{$rejected=$true};Expect024 $rejected 'BATCH024_INVALID_CONTRACT_ACCEPTED'}
$guard=@{quality='QUALITY_UNPROVEN';bestVerified=$null;nextSplit='TRAIN';stopReason='';trainRemaining=6;reservedSlots=0}
Assert-Train024Quality $guard
foreach($reason in @('PAUSED_SAFETY_REVIEW','PAUSED_EVAL_COVERAGE_GAP','BLOCKED_QUALITY_INTEGRITY')){
    $guard.stopReason=$reason;Reject024 {Assert-Train024Quality $guard}
}
$guard.stopReason='';$guard.bestVerified='forged';Reject024 {Assert-Train024Quality $guard};$guard.bestVerified=$null
$guard.nextSplit='AUDIT';Assert-Train024Quality $guard
$admission=@{quality='QUALITY_UNPROVEN';bestVerified=$null;nextSplit='AUDIT';stopReason='';reservedSlots=7;trainReservations=6}
$reserved=@{split='AUDIT'};$after=@{reservedSlots=8;trainReservations=6;stopReason='PAUSED_EVAL_COVERAGE_GAP'}
Assert-Train024ReservedAdmission $after $reserved $admission $false
Reject024 {Assert-Train024ReservedAdmission $after $reserved $admission $true}
$after.stopReason='PAUSED_SAFETY_REVIEW';Reject024 {Assert-Train024ReservedAdmission $after $reserved $admission $false}
$after.stopReason='';$after.reservedSlots=9;Reject024 {Assert-Train024ReservedAdmission $after $reserved $admission $false}
Expect024 ((Get-Train024Credit 'AUDIT' $true 28) -eq 0) 'AUDIT_CREDIT'
Expect024 ((Get-Train024Credit 'TRAIN' $false 28) -eq 0) 'PARTIAL_CREDIT'
Expect024 ((Get-Train024Credit 'TRAIN' $true 28) -eq 28) 'TRAIN_CREDIT'
Reject024 {Get-Train024Credit 'TRAIN' $true 257}
$state=@{boots=0;episodes=0;allComplete=$false;closed=$false;prepared=$false;cold=$false;committed=$false}
Expect024 ((Get-Train024Recovery $state) -ceq 'NEVER_RETRY_NATIVE') 'PRE_NATIVE_RETRY'
$state.boots=1;$state.episodes=1;Expect024 ((Get-Train024Recovery $state) -ceq 'INCOMPLETE_BATCH_NO_TRAIN_CREDIT') 'PARTIAL_RETRY'
$state.episodes=2;$state.allComplete=$true;Expect024 ((Get-Train024Recovery $state) -ceq 'DRAIN_REQUIRED') 'LIVE_FIT'
$state.closed=$true;Expect024 ((Get-Train024Recovery $state) -ceq 'AUTHENTICATE_THEN_PREPARE') 'POST_COMPLETE_RECOVERY'
$state.prepared=$true;$state.cold=$true;Expect024 ((Get-Train024Recovery $state) -ceq 'REAUTHENTICATE_THEN_COMMIT') 'POST_COLD_RECOVERY'
$state.committed=$true;Expect024 ((Get-Train024Recovery $state) -ceq 'AUTHENTICATE_REPLAY_ZERO') 'POST_HEAD_RECOVERY'
$null=[IO.Directory]::CreateDirectory($EvidenceRoot)
Write-TrainJson (Join-Path $EvidenceRoot 'contracts024.json') @{groups=3;GS=0;SQL=0;nativeEvidence=$false;result='GREEN'}
Write-Output 'TRAIN024_CONTRACTS_GREEN groups=3 GS=0 SQL=0 nativeEvidence=false'
