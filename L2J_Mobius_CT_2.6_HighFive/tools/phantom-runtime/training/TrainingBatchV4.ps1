# Explicit TEST-only mini-batch v1. Historical campaign pointers are never written here.
function Assert-Train024Quality($Guard) {
    if($Guard.quality -cne 'QUALITY_UNPROVEN' -or $null -ne $Guard.bestVerified -or $Guard.stopReason -or
        $Guard.nextSplit -cnotin @('TRAIN','TUNING','AUDIT') -or ($Guard.nextSplit -ceq 'TRAIN' -and $Guard.trainRemaining -le 0)){
        throw 'TRAIN024_QUALITY_ADMISSION_REFUSED'
    }
}
function Get-Train024Credit([string]$Split,[bool]$Complete,[int]$Paid) {
    if($Split -cnotin @('TRAIN','TUNING','AUDIT') -or $Paid -lt 0 -or $Paid -gt 256){throw 'TRAIN024_RECEIPT_BOUND'}
    if($Complete -and $Split -ceq 'TRAIN'){return $Paid};return 0
}
function Get-Train024Recovery($State) {
    if($State.boots -eq 0){return 'NEVER_RETRY_NATIVE'}
    if(!$State.allComplete -or $State.episodes -ne 2){return 'INCOMPLETE_BATCH_NO_TRAIN_CREDIT'}
    if(!$State.closed){return 'DRAIN_REQUIRED'}
    if($State.committed){return 'AUTHENTICATE_REPLAY_ZERO'}
    if($State.prepared -and $State.cold){return 'REAUTHENTICATE_THEN_COMMIT'}
    return 'AUTHENTICATE_THEN_PREPARE'
}
function Assert-Train024ReservedAdmission($Guard,$Reservation,$Admission,[bool]$Latched) {
    if($Latched -or $Admission.quality -cne 'QUALITY_UNPROVEN' -or $null -ne $Admission.bestVerified -or $Admission.stopReason -or
        $Admission.nextSplit -cne $Reservation.split -or $Guard.reservedSlots -ne ($Admission.reservedSlots+1) -or
        $Guard.trainReservations -ne ($Admission.trainReservations+$(if($Reservation.split -ceq 'TRAIN'){1}else{0}))){throw 'TRAIN024_RESERVED_ADMISSION_CHANGED'}
    if($Guard.stopReason -and !($Guard.stopReason -ceq 'PAUSED_EVAL_COVERAGE_GAP' -and $Reservation.split -ceq 'AUDIT' -and $Guard.reservedSlots -eq 8)){throw 'TRAIN024_QUALITY_ADMISSION_REFUSED'}
}
function Invoke-Train024Java($Layout,[string]$Command,[string[]]$Arguments,$Context) {
    if(!$Context -or !$Context.Batch024){throw 'TRAIN024_CONTEXT_REQUIRED'}
    Assert-Train022Closed $Context
    $identity=Read-TrainJson (Join-Path $Context.Root 'native-process.json')
    if(!(Test-TrainExitReceipt $Context $identity)){throw 'TRAIN024_ORIGINAL_HELD_EXIT_REQUIRED'}
    $receipt=Read-TrainJson (Get-TrainExitReceiptPath $Context $identity)
    if($receipt.exitCode -ne 0 -and !($Command -ceq 'partial' -and $receipt.exitCode -eq 1)){throw 'TRAIN024_NATIVE_EXIT_REQUIRED'}
    if($Command -cne 'partial'){
        $batch=Read-TrainJson (Join-Path $Context.Root 'batch-complete024.json')
        $created=[datetime]::FromFileTimeUtc([long]$identity.created)
        if($batch.pid -ne $identity.pid -or [Math]::Abs((([datetime]$batch.processStart).ToUniversalTime()-$created).TotalSeconds) -gt 1){throw 'TRAIN024_EXACT_GS_LIFETIME_REQUIRED'}
    }
    $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
    $cp=(Join-Path $Context.BuildRoot 'phantom-test\bin')+';'+(Join-Path $Context.BuildRoot 'runtime-libs\*')
    if($Command -ceq 'partial'){$cp=(Join-Path $Layout.Module '.phantom-local\learning024-verifier')+';'+$cp}
    $args024=@(Get-Train022JavaFlags @{Context022=$Context})+@(Get-TrainJavaArguments 'org.l2jmobius.tests.phantoms.PhantomLearning024V4Batch' (@($Command)+$Arguments) $cp ([guid]::NewGuid().ToString('N')) 2 512)
    $stem=Join-Path $Layout.Root ('logs\batch-'+$Command+'-'+[guid]::NewGuid().ToString('N'))
    $process=Start-Process -FilePath $java -ArgumentList $args024 -WorkingDirectory $Layout.Root -WindowStyle Hidden -PassThru -RedirectStandardOutput ($stem+'.out') -RedirectStandardError ($stem+'.err')
    $held=Hold-ProcessHandle $process
    try{
        if(!$process.WaitForExit(30000)){$process.Kill();$null=$process.WaitForExit(10000);throw 'TRAIN024_OFFLINE_TIMEOUT'}
        if((Get-TrackedProcessExitCode $process $held) -ne 0){throw ('TRAIN024_'+$Command.ToUpperInvariant()+'_FAILED')}
        $lines=@([IO.File]::ReadAllLines($stem+'.out')|Where-Object {$_ -match '^\{'})
        if($lines.Count){return $lines[-1]|ConvertFrom-Json}
    }finally{$process.Dispose()}
}
function Assert-Train024Round($Context) {
    $l=$Context.Layout024
    if($Context.Root -cne (Join-Path $l.Root 'rounds\round000000') -or $Context.BuildRoot -cne (Join-Path $l.Module '.phantom-local\learning024-build')){throw 'TRAIN024_EXACT_SCOPE'}
    Assert-TrainHash (Join-Path $l.Root 'campaign022.json') $Context.ConfigSha
    $r=Read-TrainJson (Join-Path $l.Root 'reservations\000000.json')
    if($r.batchSchema -cne 'NATIVE_BATCH024_V1' -or $r.episodeCount -ne 2 -or $r.head -cne $Context.Head -or $r.modelSha -cne $Context.ModelSha -or $r.split -cne $Context.Split){throw 'TRAIN024_RESERVATION_DRIFT'}
}
function Assert-Train024Boundary($Context,[switch]$Fast) {
    Assert-Train024Round $Context
    $l=$Context.Layout024;$config=Read-TrainJson (Join-Path $l.Root 'campaign022.json')
    $now=[datetime]::UtcNow
    if($Context.ContainsKey('LastUtc') -and $now -lt $Context.LastUtc){throw 'TRAIN024_CLOCK_SKEW'};$Context.LastUtc=$now
    if($now -ge ([datetime]$config.deadlineUtc).ToUniversalTime()){throw 'TRAIN024_DEADLINE'}
    if(([IO.DriveInfo]::new([IO.Path]::GetPathRoot($l.Root))).AvailableFreeSpace -lt [long]$config.minDiskBytes){throw 'TRAIN024_DISK_LIMIT'}
    if(Test-Path -LiteralPath (Join-Path $l.Root 'stop.request')){throw 'TRAIN024_STOP_REQUEST'}
    Assert-TrainHash (Join-Path $l.BaseRoot 'active022.json') $config.originalActiveSha
    if(!$Fast){foreach($pin in $config.sources.PSObject.Properties){Assert-TrainHash $pin.Name $pin.Value}}
}
function Get-Train024Metrics($Context) {
    $episodes=@()
    for($i=0;$i -lt 2;$i++){
        $ep=@{};foreach($k in $Context.Keys){$ep[$k]=$Context[$k]};$ep.Remove('Batch024');$ep.Root=Join-Path $Context.Root ('episodes\episode'+$i)
        $episodes+=@(Get-Train022Metrics $ep)
    }
    $m=@{round=$Context.Round;split=$Context.Split;batch024=$true;episodes=$episodes;episodeCount=2;batchCount=1;episodesPerBoot=2;nativeProofSha=(Get-FileHash -LiteralPath (Join-Path $Context.Root 'batch-complete024.json')).Hash.ToLowerInvariant();coverage='TWO_INDEPENDENT_SOLO_CLASS94_EPISODES';superiority='NOT_EVALUATED';groups=@();perAction=@();MODEL_REORDERED=$false;EXPERIMENT_REORDERED=$false}
    foreach($name in @('paidActions','trainCredit','experimentPaid','stockPaid','hpDamage','mpCost','durationNanos','kills','deathsLowerBound','unknownOrUnpaidDecisions')){$m[$name]=($episodes|Measure-Object -Property $name -Sum).Sum}
    foreach($e in $episodes){$m.MODEL_REORDERED=$m.MODEL_REORDERED -or $e.MODEL_REORDERED;$m.EXPERIMENT_REORDERED=$m.EXPERIMENT_REORDERED -or $e.EXPERIMENT_REORDERED;$m.groups+=@($e.groups);$m.perAction+=@($e.perAction)}
    $m.reason=($episodes.reason -join ',');$m.outcome=($episodes.outcome -join ',');$m.episodeSha='PER_EPISODE';$m.sourceSha=$episodes[0].sourceSha;$m.completeDeaths=$null
    $native=Read-TrainJson (Join-Path $Context.Root 'native-process.json');$exit=Read-TrainJson (Get-TrainExitReceiptPath $Context $native);$m.nativeProcessSeconds=[double]$exit.nativeElapsedMillis/1000
    $m.estimatedOverheadSeconds=$m.nativeProcessSeconds-[double]$m.durationNanos/1e9
    return $m
}
function New-Train024Campaign([string]$HistoryRoot) {
    $sourceLayout=Get-Train022Layout $HistoryRoot
    $source=Get-Train022IndependentSource $sourceLayout
    $l=@{};foreach($k in $sourceLayout.Keys){$l[$k]=$sourceLayout[$k]}
    $id=[guid]::NewGuid().ToString('N');$l.Root=Join-Path $l.BaseRoot ('campaigns\'+$id);$l.Ledger=Join-Path $l.Root 'ledger';$l.BuildRoot=Join-Path $l.Module '.phantom-local\learning024-build'
    $greenPath=Join-Path $l.BuildRoot 'offline-green024.json';$green=Read-TrainJson $greenPath
    if($green.schema -cne 'OFFLINE_GREEN024_V1' -or $green.GS -ne 0 -or $green.SQL -ne 0){throw 'TRAIN024_OFFLINE_GREEN_REQUIRED'}
    foreach($pin in $green.sources.PSObject.Properties){Assert-TrainHash $pin.Name $pin.Value}
    $previous=$sourceLayout.Root
    $selector=Join-Path $l.BaseRoot 'latest-batch024.json'
    if(Test-Path -LiteralPath $selector){
        $last=Read-TrainJson $selector
        if($last.campaignId -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN024_PREVIOUS_SCOPE'}
        $previous=Join-Path $l.BaseRoot ('campaigns\'+$last.campaignId)
        $oldConfig=Read-TrainJson (Join-Path $previous 'campaign022.json')
        if($oldConfig.batchSchema -cne 'NATIVE_BATCH024_V1'){throw 'TRAIN024_PREVIOUS_SCHEMA'}
        $oldLayout=@{};foreach($k in $l.Keys){$oldLayout[$k]=$l[$k]};$oldLayout.Root=$previous;$oldLayout.Ledger=Join-Path $previous 'ledger'
        $oldGuard=Get-Train023Guard $oldLayout;Assert-Train024Quality $oldGuard
        if(Test-Path -LiteralPath (Join-Path $previous 'rounds\round000000\boot-reserved020.json')){Assert-Train022Closed @{Root=(Join-Path $previous 'rounds\round000000')}}
        if(Test-Path -LiteralPath (Join-Path $previous 'rounds\round000000\commit022.json')){throw 'TRAIN024_ACCEPTED_BATCH_REQUIRES_OWNER_REVIEW'}
    }
    New-TrainPrivateRoot $l
    foreach($dir in @('logs','ledger','rounds','reservations')){$null=[IO.Directory]::CreateDirectory((Join-Path $l.Root $dir))}
    foreach($file in Get-ChildItem -LiteralPath $sourceLayout.Ledger -File){Assert-G0PhysicalFile $file.FullName;Copy-Item -LiteralPath $file.FullName -Destination $l.Ledger;Assert-TrainHash (Join-Path $l.Ledger $file.Name) (Get-FileHash -LiteralPath $file.FullName).Hash}
    $now=[datetime]::UtcNow
    $config=@{schemaVersion=22;batchSchema='NATIVE_BATCH024_V1';campaignId=$id;previousCampaign=(Split-Path $previous -Leaf);previousConfigSha=(Get-FileHash -LiteralPath (Join-Path $previous 'campaign022.json')).Hash;previousHead=$source.head.head;previousModelSha=$source.head.modelSha;qualityStartSequence=$source.head.sequence;cursorStart=$source.cursor;sourceCatalogSha=(Read-TrainJson (Join-Path $sourceLayout.Root 'campaign022.json')).sourceCatalogSha;startedUtc=$now.ToString('o');deadlineUtc=$now.AddMinutes(16).ToString('o');maxBoots=2;minDiskBytes=8GB;maxMemoryBytes=6GB;maxCpuCores=2.1;maxNoData=1;maxStagnation=1;originalActiveSha=(Get-FileHash -LiteralPath (Join-Path $l.BaseRoot 'active022.json')).Hash;sources=$green.sources;productionV4='OFF'}
    $policy=New-Train023Policy $l $config;Write-Train022Immutable (Join-Path $l.Root 'quality-policy023.json') $policy
    $config.qualityPolicySha=(Get-FileHash -LiteralPath (Join-Path $l.Root 'quality-policy023.json')).Hash
    Write-Train022Immutable (Join-Path $l.Root 'campaign022.json') $config
    $guard=Assert-Train023Admission $l;Assert-Train024Quality $guard
    Write-Train022Immutable (Join-Path $l.Root 'quality-admission024.json') $guard
    $nonce=[guid]::NewGuid().ToString('N');$hash=[Security.Cryptography.SHA256]::Create()
    try{$slot=[BitConverter]::ToString($hash.ComputeHash($script:TrainUtf8.GetBytes($nonce+'/batch024'))).Replace('-','').ToLowerInvariant()}finally{$hash.Dispose()}
    $claims=@(Get-ChildItem -LiteralPath $l.BaseRoot -Filter 'boot024-*.json' -File).Count
    if($claims -ge 2){throw 'TRAIN024_TWO_BOOT_RESERVATION_LIMIT'}
    $r=@{schemaVersion=22;batchSchema='NATIVE_BATCH024_V1';episodeCount=2;round=0;cursor=$source.cursor;episodeId=$slot;nonce=$nonce;head=$source.head.head;modelSha=$source.head.modelSha;localUpdates=$source.head.localUpdates;step=$source.head.sequence;split=$guard.nextSplit;bootsBefore=$claims;sourceCatalogSha=$config.sourceCatalogSha;configSha=(Get-FileHash -LiteralPath (Join-Path $l.Root 'campaign022.json')).Hash;qualityAdmissionSha=(Get-FileHash -LiteralPath (Join-Path $l.Root 'quality-admission024.json')).Hash}
    $r.globalUpdates=$source.head.globalUpdates
    Write-Train022Immutable (Join-Path $l.Root 'reservations\000000.json') $r
    Write-TrainJson $selector @{schema='BATCH_SELECTOR024_V1';campaignId=$id}
    return $l
}
function Complete-Train024Batch($Layout,$Context) {
    Assert-Train024Round $Context
    Assert-Train022Closed $Context
    if(!(Test-Path -LiteralPath (Join-Path $Context.Root 'batch-complete024.json'))){throw 'TRAIN024_INCOMPLETE_BATCH_NO_TRAIN_CREDIT'}
    $verified=Invoke-Train022Java $Layout 'batch-verify' @($Layout.Ledger,$Context.Root) $Context
    $path=Join-Path $Context.Root 'prepared022.json'
    $prepared=if(Test-Path -LiteralPath $path){Read-TrainJson $path}else{Invoke-Train022Java $Layout 'batch-prepare' @($Layout.Ledger,$Context.Root,$Context.Head) $Context}
    if(!(Test-Path -LiteralPath $path)){Write-Train022Immutable $path $prepared}
    $null=Invoke-Train022Java $Layout 'batch-verify-prepared' @($Layout.Ledger,$Context.Root,$Context.Head,$prepared.head) $Context
    $null=Invoke-Train022Java $Layout 'cold' @($Layout.Ledger,$prepared.head)
    $head=Invoke-Train022Java $Layout 'commit' @($Layout.Ledger,$Context.Head,$prepared.head)
    $reservation=Read-TrainJson (Join-Path $Layout.Root 'reservations\000000.json')
    $expectedCredit=Get-Train024Credit $Context.Split $true ([int]$verified.paid)
    if([long]$head.globalUpdates -ne ([long]$reservation.globalUpdates+$expectedCredit)){throw 'TRAIN024_EXACT_BATCH_CREDIT_REQUIRED'}
    if(!(Test-Path -LiteralPath (Join-Path $Context.Root 'commit022.json'))){Write-Train022Immutable (Join-Path $Context.Root 'commit022.json') $head}
    $null=Invoke-Train022Java $Layout 'batch-replay' @($Layout.Ledger,$Context.Root) $Context
    try{$guard=Assert-Train023Admission $Layout}catch{if($_.Exception.Message -notlike 'TRAIN023_PAUSED_*'){throw}}
    $guard=Get-Train023Guard $Layout
    $state=@{schemaVersion=24;trainerStatus='PAUSED';reason='BATCH_COMPLETE_OWNER_REVIEW';observedUtc=[datetime]::UtcNow.ToString('o');deadlineUtc=(Read-TrainJson (Join-Path $Layout.Root 'campaign022.json')).deadlineUtc;modelSha=$head.modelSha;ledgerHead=$head.head;globalUpdates=$head.globalUpdates;localUpdates=$head.localUpdates;segment=$head.segment;gsBoots=1;completedEpisodes=2;metrics=$guard.metrics;refusedAttempts=0;unknownEpisodes=0;manualGate='REQUIRED';productionV4='OFF';SUPERIORITY='NOT_EVALUATED'}
    Save-Train022Status $Layout $state 'PAUSED' $state.reason
    return $state
}
function Invoke-TrainMiniBatch024([string]$HistoryRoot,[ValidateSet('Prepare','Run','Recover','Report')][string]$Command) {
    Initialize-TrainWin32
    $base=Get-Train022Layout $HistoryRoot
    $lockPath=Join-Path $base.BaseRoot 'batch024.lock'
    $lock=[IO.FileStream]::new($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        if($Command -ceq 'Prepare'){$l=New-Train024Campaign $HistoryRoot;return @{campaignRoot=$l.Root;status='PREPARED_NO_GS';manualGate='REQUIRED'}}
        $last=Read-TrainJson (Join-Path $base.BaseRoot 'latest-batch024.json')
        if($last.campaignId -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN024_SELECTOR_SCOPE'}
        $l=@{};foreach($k in $base.Keys){$l[$k]=$base[$k]};$l.Root=Join-Path $l.BaseRoot ('campaigns\'+$last.campaignId);$l.Ledger=Join-Path $l.Root 'ledger';$l.BuildRoot=Join-Path $l.Module '.phantom-local\learning024-build'
        $r=Read-TrainJson (Join-Path $l.Root 'reservations\000000.json');$c=Get-Train022Context $l $r
        if($Command -ceq 'Report'){
            if(Test-Path -LiteralPath (Join-Path $l.Root 'status024.json')){return Read-TrainJson (Join-Path $l.Root 'status024.json')}
            if(Test-Path -LiteralPath (Join-Path $l.Root 'status022.json')){return Read-TrainJson (Join-Path $l.Root 'status022.json')}
            return @{campaignRoot=$l.Root;status='PREPARED_OR_INCOMPLETE';trainingCredit=0;manualGate='REQUIRED'}
        }
        if($Command -ceq 'Recover'){
            Stop-Train022Children $c
            $phase=Get-Train024Recovery @{boots=$(if(Test-Path -LiteralPath (Join-Path $c.Root 'boot-reserved020.json')){1}else{0});episodes=@(Get-ChildItem -LiteralPath (Join-Path $c.Root 'episodes') -Filter 'native021.complete.json' -Recurse -File -ErrorAction SilentlyContinue).Count;allComplete=(Test-Path -LiteralPath (Join-Path $c.Root 'batch-complete024.json'));closed=$true;prepared=(Test-Path -LiteralPath (Join-Path $c.Root 'prepared022.json'));cold=$false;committed=(Test-Path -LiteralPath (Join-Path $c.Root 'commit022.json'))}
            if($phase -cin @('NEVER_RETRY_NATIVE','INCOMPLETE_BATCH_NO_TRAIN_CREDIT')){throw ('TRAIN024_'+$phase)}
            return Complete-Train024Batch $l $c
        }
        if(Test-Path -LiteralPath $c.Root){throw 'TRAIN024_NEVER_RETRY_AMBIGUOUS_NATIVE'}
        Assert-Train024Boundary $c
        $guard=Get-Train023Guard $l
        Assert-TrainHash (Join-Path $l.Root 'quality-admission024.json') $r.qualityAdmissionSha
        Assert-Train024ReservedAdmission $guard $r (Read-TrainJson (Join-Path $l.Root 'quality-admission024.json')) (Test-Path -LiteralPath (Join-Path $l.BaseRoot 'quality-stop023.json'))
        Write-Train022Immutable (Join-Path $l.BaseRoot ('boot024-'+($c.BootsBefore+1)+'.json')) @{campaignId=$last.campaignId;reservedUtc=[datetime]::UtcNow.ToString('o');maxSeconds=480;batch=$r.episodeId}
        try{Invoke-TrainActionValue020 $HistoryRoot $l.Ledger -Autonomous022 $c}
        catch{Pause-Train024Partial $l $c;throw}
        return Complete-Train024Batch $l $c
    }finally{$lock.Dispose()}
}
function Pause-Train024Partial($Layout,$Context) {
    Assert-Train022Closed $Context
    $partial=Invoke-Train022Java $Layout 'batch-partial' @($Layout.Ledger,$Context.Root) $Context
    if($partial.trainingCredit -ne 0){throw 'TRAIN024_PARTIAL_CREDIT_FORBIDDEN'}
    $path=Join-Path $Context.Root 'partial024.json'
    if(!(Test-Path -LiteralPath $path)){Write-Train022Immutable $path $partial}
    if($partial.deaths -gt 0){
        $stop=Join-Path $Layout.BaseRoot 'quality-stop023.json'
        if(!(Test-Path -LiteralPath $stop)){Write-Train022Immutable $stop @{schema='QUALITY_STOP023_V1';reason='PAUSED_SAFETY_REVIEW';referenceAnchor=$Context.ModelSha;decisionSha=(Get-FileHash -LiteralPath $path).Hash;ownerGate='REQUIRED';unlock='NOT_IMPLEMENTED'}}
    }
    $native=Read-TrainJson (Join-Path $Context.Root 'native-process.json');$exit=Read-TrainJson (Get-TrainExitReceiptPath $Context $native)
    $status=@{schema='PARTIAL_BATCH024_V1';trainerStatus='PAUSED';reason='PAUSED_SAFETY_REVIEW';sealedEpisodes=$partial.sealedEpisodes;episodes=$partial.sealedEpisodes;batches=0;gsBoots=1;episodesPerBoot=$partial.sealedEpisodes;nativeDurationSeconds=([double]$exit.nativeElapsedMillis/1000);paid=$partial.paid;trainingCredit=0;modelSha=$Context.ModelSha;ledgerHead=[IO.File]::ReadAllText((Join-Path $Layout.Ledger 'HEAD'));manualGate='REQUIRED';productionV4='OFF';quality='QUALITY_UNPROVEN';bestVerified=$null}
    $seconds=0.0
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $Context.Root 'episodes') -Filter 'episode020.json' -Recurse -File){$seconds+=[double](Read-TrainJson $file.FullName).durationNanos/1e9}
    $status.estimatedOverheadSeconds=$status.nativeDurationSeconds-$seconds
    Write-TrainJson (Join-Path $Layout.Root 'status024.json') $status
    Write-TrainJson (Join-Path $Layout.Root 'report024.json') $status
    $lines=@('# Mini-batch024: приостановлено','Причина: реальная смерть actor, требуется safety review.');$rows=''
    foreach($key in @('batches','episodes','gsBoots','episodesPerBoot','nativeDurationSeconds','estimatedOverheadSeconds','paid','trainingCredit','modelSha','ledgerHead','quality','manualGate')){
        $value=[string]$status[$key];$lines+=($key+': '+$value);$rows+='<tr><th>'+[Net.WebUtility]::HtmlEncode($key)+'</th><td>'+[Net.WebUtility]::HtmlEncode($value)+'</td></tr>'
    }
    Write-TrainText (Join-Path $Layout.Root 'report024.md') ($lines -join "`r`n")
    Write-TrainText (Join-Path $Layout.Root 'report024.html') ('<!doctype html><html lang="ru"><meta charset="utf-8"><title>Mini-batch024</title><h1>Приостановлено: требуется safety review</h1><table>'+$rows+'</table><p>Неполный batch: credit0. Экономия времени не измерена. Production V4 OFF.</p></html>')
    Write-Output 'TRAIN024_PARTIAL_AUTHENTICATED_ZERO_CREDIT_SAFETY_PAUSED'
}
