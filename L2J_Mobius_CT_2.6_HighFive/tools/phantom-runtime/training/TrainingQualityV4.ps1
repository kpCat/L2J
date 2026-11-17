# TEST-only batch boundary. No comparator is installed: JSON cannot certify native quality.
function New-Train023Policy($Layout,$Config) {
    $anchor='90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6'
    $epoch=$Config.campaignId
    if(Test-Train022Property $Config 'previousCampaign'){
        $parent=Join-Path $Layout.BaseRoot ('campaigns\'+$Config.previousCampaign)
        $old=Read-TrainJson (Join-Path $parent 'campaign022.json')
        if(Test-Train022Property $old 'qualityPolicySha'){
            Assert-TrainHash (Join-Path $parent 'quality-policy023.json') $old.qualityPolicySha
            $epoch=(Read-TrainJson (Join-Path $parent 'quality-policy023.json')).epoch
        }
    }
    return [ordered]@{schema='QUALITY_POLICY023_V1';epoch=$epoch;referenceAnchor=$anchor;referenceRole='REFERENCE_ANCHOR_UNEVALUATED';startHead=$Config.previousHead;startSequence=$Config.qualityStartSequence;startModelSha=$Config.previousModelSha;maxTrain=6;cycleSlots=8;patience=3;minPairs=32;splitPolicy='TRAIN0_5_TUNING6_AUDIT7';comparator='UNAVAILABLE';productionV4='OFF';promotion='NO_AUTO_PRODUCTION_PROMOTION'}
}
function Get-Train023ContractDecision([object[]]$Windows) {
    # Mathematical contract only, deliberately disconnected from runtime provenance.
    $valid=0;$ids=@{};$reason='INSUFFICIENT_EVAL'
    foreach($w in $Windows){
        if(!$w -or !$w.matched -or !$w.independent -or $w.pairs -lt 32 -or $w.divergences -lt 1){continue}
        $id=if(Test-Train022Property $w 'id'){[string]$w.id}else{'single'}
        if($ids.ContainsKey($id)){return @{contractOnly=$true;reason='INVALID_EVAL';validWindows=$valid}}
        $ids[$id]=$true
        if([double]::IsNaN($w.lower95) -or [double]::IsNaN($w.upper95) -or $w.lower95 -lt -1 -or $w.upper95 -gt 1 -or $w.lower95 -gt $w.upper95){return @{contractOnly=$true;reason='INVALID_EVAL';validWindows=0}}
        $valid++
        if($w.upper95 -lt 0 -or $w.safetyRegression -or $w.resourceRegression){$reason='PAUSED_REGRESSION';break}
        if($w.lower95 -gt 0){$reason='IMPROVEMENT_SUPPORTED';$valid=0}else{$reason='NO_SUPPORTED_GAIN'}
    }
    if($reason -ceq 'NO_SUPPORTED_GAIN' -and $valid -ge 3){$reason='PAUSED_NO_SUPPORTED_GAIN'}
    return @{contractOnly=$true;reason=$reason;validWindows=$valid}
}
function Get-Train023Guard($Layout) {
    $current=$Layout.Root;$visited=@{};$campaigns=@();$epoch=$null
    while($current){
        if($visited.Count -ge 32 -or $visited.ContainsKey($current)){throw 'TRAIN023_LINEAGE_CYCLE'}
        $visited[$current]=$true
        $configPath=Join-Path $current 'campaign022.json';$config=Read-TrainJson $configPath
        if(!(Test-Train022Property $config 'qualityPolicySha')){
            if($current -ieq $Layout.Root){throw 'TRAIN023_POLICY_REQUIRED_NEW_CAMPAIGN'}
            break
        }
        $policyPath=Join-Path $current 'quality-policy023.json';Assert-TrainHash $policyPath $config.qualityPolicySha
        $policy=Read-TrainJson $policyPath
        if($policy.schema -cne 'QUALITY_POLICY023_V1' -or $policy.maxTrain -ne 6 -or $policy.cycleSlots -ne 8 -or $policy.patience -ne 3 -or $policy.minPairs -ne 32 -or
            $policy.referenceAnchor -cne '90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6' -or $policy.referenceRole -cne 'REFERENCE_ANCHOR_UNEVALUATED' -or
            $policy.splitPolicy -cne 'TRAIN0_5_TUNING6_AUDIT7' -or $policy.comparator -cne 'UNAVAILABLE' -or $policy.productionV4 -cne 'OFF' -or $policy.promotion -cne 'NO_AUTO_PRODUCTION_PROMOTION' -or $policy.epoch -cnotmatch '^[a-f0-9]{32}$' -or $policy.startHead -cne $config.previousHead -or $policy.startModelSha -cne $config.previousModelSha -or $policy.startSequence -ne $config.qualityStartSequence){throw 'TRAIN023_POLICY_CONFLICT'}
        if($epoch -and $epoch -cne $policy.epoch){throw 'TRAIN023_EPOCH_RESET'}
        $epoch=$policy.epoch
        Assert-TrainHash (Join-Path $current ('ledger\candidate-'+$policy.referenceAnchor+'.bin')) $policy.referenceAnchor
        $campaigns=@(@{root=$current;config=$config;policy=$policy;configSha=(Get-FileHash -LiteralPath $configPath).Hash})+$campaigns
        if(!(Test-Train022Property $config 'previousCampaign')){break}
        if($config.previousCampaign -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN023_PREVIOUS_SCOPE'}
        $current=Join-Path $Layout.BaseRoot ('campaigns\'+$config.previousCampaign)
        Assert-TrainHash (Join-Path $current 'campaign022.json') $config.previousConfigSha
    }
    $slots=0;$train=0;$events=@{};$metrics=@();$safetyReview=$false;$pins=@()
    foreach($campaign in $campaigns){
        $local=0;$boundaryHead=$campaign.policy.startHead;$boundaryModel=$campaign.policy.startModelSha;$sequence=[long]$campaign.policy.startSequence
        foreach($roundDirectory in @(Get-ChildItem -LiteralPath (Join-Path $campaign.root 'rounds') -Directory)){
            if($roundDirectory.Name -cnotmatch '^round[0-9]{6}$' -or !(Test-Path -LiteralPath (Join-Path $campaign.root ('reservations\'+$roundDirectory.Name.Substring(5)+'.json')))){throw 'TRAIN023_ORPHAN_ROUND'}
        }
        foreach($path in @(Get-ChildItem -LiteralPath (Join-Path $campaign.root 'reservations') -File|Sort-Object Name)){
            $r=Read-TrainJson $path.FullName
            $split=if($slots % 8 -eq 6){'TUNING'}elseif($slots % 8 -eq 7){'AUDIT'}else{'TRAIN'}
            if($path.Name -cne ($local.ToString('D6')+'.json') -or $r.round -ne $local -or $r.split -cne $split -or $r.configSha -ine $campaign.configSha -or
                $r.head -cne $boundaryHead -or $r.modelSha -cne $boundaryModel -or $r.step -ne $sequence -or $r.episodeId -cnotmatch '^[a-f0-9]{64}$' -or $events.ContainsKey($r.episodeId) -or $r.sourceCatalogSha -cne $campaign.config.sourceCatalogSha){throw 'TRAIN023_RESERVATION_INTEGRITY'}
            $events[$r.episodeId]=$true
            Assert-TrainHash (Join-Path $campaign.root ('ledger\candidate-'+$r.modelSha+'.bin')) $r.modelSha
            $pins+= (Get-FileHash -LiteralPath $path.FullName).Hash
            $roundLayout=@{};foreach($k in $Layout.Keys){$roundLayout[$k]=$Layout[$k]};$roundLayout.Root=$campaign.root;$roundLayout.Ledger=Join-Path $campaign.root 'ledger'
            $context=Get-Train022Context $roundLayout $r
            $commitPath=Join-Path $context.Root 'commit022.json'
            if(Test-Path -LiteralPath $commitPath){
                $commit=Read-TrainJson $commitPath
                if($commit.sequence -ne ($sequence+1) -or $commit.head -cnotmatch '^[a-f0-9]{64}$' -or $commit.modelSha -cnotmatch '^[a-f0-9]{64}$'){throw 'TRAIN023_COMMIT_INTEGRITY'}
                # Canonical samples authenticate split/owner/episode/paid bytes and anti-replay.
                # Offline verifier logs stay in the current campaign, not its historical parents.
                $verifyLayout=@{};foreach($k in $roundLayout.Keys){$verifyLayout[$k]=$roundLayout[$k]};$verifyLayout.Root=$Layout.Root
                if($context.ContainsKey('Batch024') -and $context.Batch024){$null=Invoke-Train022Java $verifyLayout 'batch-history' @($roundLayout.Ledger,$context.Root) $context}
                else{$null=Invoke-Train022Java $verifyLayout 'verify-history' @($roundLayout.Ledger,$context.Root) $context}
                $metric=Get-Train022Metrics $context;$metric.candidateSha=$commit.modelSha;$metrics+=@($metric)
                if($metric.deathsLowerBound -gt 0){$safetyReview=$true}
                $pins+= (Get-FileHash -LiteralPath $commitPath).Hash
                $boundaryHead=$commit.head;$boundaryModel=$commit.modelSha;$sequence++
            }
            if($split -ceq 'TRAIN'){$train++};$slots++;$local++
        }
        $checkLayout=@{};foreach($k in $Layout.Keys){$checkLayout[$k]=$Layout[$k]};$checkLayout.Ledger=Join-Path $campaign.root 'ledger'
        $committedHead=Invoke-Train022Java $checkLayout 'head' @($checkLayout.Ledger)
        if($committedHead.head -cne $boundaryHead -or $committedHead.modelSha -cne $boundaryModel -or $committedHead.sequence -ne $sequence){throw 'TRAIN023_MISSING_COMMITTED_RECEIPT'}
    }
    $reason=if($safetyReview){'PAUSED_SAFETY_REVIEW'}elseif($slots -ge 8 -or $train -gt 6){'PAUSED_EVAL_COVERAGE_GAP'}else{''}
    $stopPath=Join-Path $Layout.BaseRoot 'quality-stop023.json'
    if(Test-Path -LiteralPath $stopPath){
        $stop=Read-TrainJson $stopPath
        if($stop.schema -cne 'QUALITY_STOP023_V1' -or $stop.reason -cnotin @('PAUSED_EVAL_COVERAGE_GAP','PAUSED_SAFETY_REVIEW','PAUSED_REGRESSION','PAUSED_NO_SUPPORTED_GAIN') -or $stop.referenceAnchor -cne $policy.referenceAnchor){throw 'TRAIN023_STOP_CORRUPT'}
        $reason=$stop.reason
    }
    $head=Invoke-Train022Java $Layout 'head' @($Layout.Ledger)
    return [ordered]@{schema='QUALITY_GUARD023_V1';epoch=$epoch;quality='QUALITY_UNPROVEN';phase=$(if($reason){'EVAL_INSUFFICIENT'}elseif($slots -ge 6){'EVAL_PENDING'}else{'COLLECTING'});evaluation='INSUFFICIENT_EVAL';stopReason=$reason;latestCommitted=$head;
        nextSplit=$(if($slots % 8 -eq 6){'TUNING'}elseif($slots % 8 -eq 7){'AUDIT'}else{'TRAIN'});reservedSlots=$slots;trainReservations=$train;trainRemaining=[Math]::Max(0,6-$train);validWindows=0;patience=0;
        referenceAnchor=$policy.referenceAnchor;referenceRole=$policy.referenceRole;bestVerified=$null;lastQualityEvidenceSha=$null;coverage='NO_AUTHENTICATED_MATCHED_V4_COMPARATOR';uncertainty='UNKNOWN';metrics=$metrics;sourcePins=$pins;required='Независимые сопоставимые native MODEL/stock/reference окна; frozen policy, strata, propensities, uncertainty и safety/resource checks. Отдельная задача и owner decision.'}
}
function Assert-Train023Admission($Layout) {
    $guard=Get-Train023Guard $Layout
    $body=[ordered]@{schema='QUALITY_DECISION023_V1';epoch=$guard.epoch;reservedSlots=$guard.reservedSlots;trainReservations=$guard.trainReservations;nextSplit=$guard.nextSplit;reason=$guard.stopReason;quality=$guard.quality;referenceAnchor=$guard.referenceAnchor;sourcePins=$guard.sourcePins;bestVerified=$null}
    $bytes=$script:TrainUtf8.GetBytes(($body|ConvertTo-Json -Depth 16));$digest=[Security.Cryptography.SHA256]::Create()
    try{$sha=[BitConverter]::ToString($digest.ComputeHash($bytes)).Replace('-','').ToLowerInvariant()}finally{$digest.Dispose()}
    $receipt=Join-Path $Layout.Root ('quality-decision023-'+$sha+'.json')
    if(Test-Path -LiteralPath $receipt){Assert-TrainHash $receipt $sha}else{Write-Train022Immutable $receipt $body}
    if($guard.stopReason){
        $stop=Join-Path $Layout.BaseRoot 'quality-stop023.json'
        if(!(Test-Path -LiteralPath $stop)){Write-Train022Immutable $stop @{schema='QUALITY_STOP023_V1';epoch=$guard.epoch;reason=$guard.stopReason;referenceAnchor=$guard.referenceAnchor;decisionSha=$sha;ownerGate='REQUIRED';unlock='NOT_IMPLEMENTED'}}
        throw ('TRAIN023_'+$guard.stopReason)
    }
    return $guard
}
function Write-Train023Progress($Layout,$State) {
    $config=Read-TrainJson (Join-Path $Layout.Root 'campaign022.json')
    try{
        $guard=Get-Train023Guard $Layout
        Sync-Train022CommittedState $State $guard.latestCommitted
        $State.gsBoots=Get-Train022HistoricalBoots $Layout $config
    }catch{
        # Rendering must never prevent Pause/Stop from draining an owned child.
        # Invalid source cannot fall back to a supposedly improved status.json.
        $guard=@{schema='QUALITY_GUARD023_V1';quality='QUALITY_UNPROVEN';phase='BLOCKED_INTEGRITY';evaluation='INSUFFICIENT_EVAL';stopReason='BLOCKED_QUALITY_INTEGRITY';reservedSlots=0;trainReservations='UNKNOWN';trainRemaining=0;nextSplit='UNKNOWN';referenceAnchor='90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6';referenceRole='REFERENCE_ANCHOR_UNEVALUATED';bestVerified=$null;lastQualityEvidenceSha=$null;validWindows=0;coverage='INVALID_SOURCE';uncertainty='UNKNOWN';metrics=@();required='Проверка целостности владельцем; продолжение запрещено.'}
        $State.modelSha='UNKNOWN';$State.globalUpdates='UNKNOWN'
    }
    if($State -is [Collections.IDictionary]){$State.quality023=$guard}else{$State|Add-Member -NotePropertyName quality023 -NotePropertyValue $guard -Force}
    $answer=if($guard.stopReason -ceq 'BLOCKED_QUALITY_INTEGRITY'){'Заблокировано — нарушена целостность источника'}elseif($guard.stopReason -ceq 'PAUSED_SAFETY_REVIEW'){'Приостановлено — требуется проверка безопасности'}elseif($guard.stopReason){'Пауза — недостаточно оценок'}elseif($State.trainerStatus -cin @('PAUSED','STOPPED','AUTO_STOPPED','BLOCKED')){'Приостановлено'}elseif($guard.reservedSlots -ge 6){'Мало данных — ожидается независимая оценка'}else{'Можно продолжать ограниченную проверку; нужны данные'}
    if($State.trainerStatus -ceq 'RECOVERY_REQUIRED'){$answer='Заблокировано — требуется восстановление'}
    elseif($State.trainerStatus -cin @('DRAINING','PAUSE_REQUESTED','STOP_REQUESTED')){$answer='Приостановлено — ожидается завершение drain'}
    elseif([datetime]::UtcNow -ge ([datetime]$config.deadlineUtc).ToUniversalTime()){$answer='Приостановлено — срок кампании истёк'}
    elseif([int]$State.gsBoots -ge [int]$config.maxBoots){$answer='Приостановлено — лимит запусков исчерпан'}
    $elapsed=([datetime]::UtcNow-([datetime]$config.startedUtc).ToUniversalTime()).TotalSeconds
    $rows=[ordered]@{'Решение'=$answer;'Качество'=$guard.quality;'Оценка'=$guard.evaluation;'Статус'=$State.trainerStatus;'Причина STOP'=$State.reason;'Quality STOP'=$guard.stopReason;'Кампания'=$config.campaignId;'Фаза'=$guard.phase;'Нативных завершённых эпизодов'=@($guard.metrics).Count;'Зарезервировано TRAIN'=$guard.trainReservations;'Осталось TRAIN до TUNING/AUDIT'=$guard.trainRemaining;'Следующий split'=$guard.nextSplit;'Лимит boots'=$config.maxBoots;'Использовано boots'=$State.gsBoots;'Прошло секунд с создания кампании'=[Math]::Round($elapsed,1);'Срок UTC'=$config.deadlineUtc;'Latest committed SHA'=$State.modelSha;'Глобальные updates'=$State.globalUpdates;'Reference anchor SHA'=$guard.referenceAnchor;'Роль reference'=$guard.referenceRole;'Best verified'='null — ещё не доказан';'Независимых валидных окон'=$guard.validWindows;'Coverage'=$guard.coverage;'Достоверность / uncertainty'='UNKNOWN';'Last quality evidence SHA'='null';'CPU / RAM / disk'='UNKNOWN: новые процессы в Goal023 не измерялись';'Отказы'=$State.refusedAttempts;'Неполные эпизоды'=$State.unknownEpisodes;'Что требуется для приёмки'=$guard.required;'Owner gate'='REQUIRED';'Production V4'='OFF'}
    $splitCounts=@(foreach($split in @('TRAIN','TUNING','AUDIT')){@($guard.metrics|Where-Object {$_.split -ceq $split}).Count})
    $rows['Завершённые TRAIN / TUNING / AUDIT']=$splitCounts -join ' / '
    $batches=@($guard.metrics|Where-Object { (Test-Train022Property $_ 'batch024') -and $_.batch024 })
    if($batches.Count){
        $episodes=0;foreach($metric in $guard.metrics){$episodes+=if(Test-Train022Property $metric 'episodeCount'){[int]$metric.episodeCount}else{1}}
        $rows['Batches']=$batches.Count;$rows['Episodes']=$episodes;$rows['Episodes per boot']=$episodes/[Math]::Max(1,[int]$State.gsBoots)
        $rows['Native duration, seconds']=($batches|Measure-Object nativeProcessSeconds -Sum).Sum
        $rows['Estimated overhead, seconds']=($batches|Measure-Object estimatedOverheadSeconds -Sum).Sum
        $rows['Нативных завершённых эпизодов']=$episodes
    }
    $paid=0;$invalid=0
    foreach($metric in $guard.metrics){$paid+=[int]$metric.paidActions;$invalid+=[int]$metric.unknownOrUnpaidDecisions}
    $rows['Paid / invalid или unpaid']=$paid.ToString()+' / '+$invalid.ToString()
    $rows['Разброс HP / MP / time']='Matched uncertainty UNKNOWN. Внутриэпизодный sample variance приведён ниже при наличии sealed источников.'
    $rows['Доступно boots по бюджету']=[Math]::Max(0,[int]$config.maxBoots-[int]$State.gsBoots)
    $rows['JSON view']='report022.json — текущая проверенная проекция; status022.json — последняя запись контроллера.'
    $lines=@('# Прогресс V4 TEST', $answer, '');$table=''
    foreach($key in $rows.Keys){$value=[string]$rows[$key];$lines+=($key+': '+$value);$table+='<tr><th>'+[Net.WebUtility]::HtmlEncode($key)+'</th><td>'+[Net.WebUtility]::HtmlEncode($value)+'</td></tr>'}
    $episodes=''
    foreach($m in $guard.metrics){
        $line=('Раунд '+$m.round+' / '+$m.split+': paid='+$m.paidActions+'; invalid/unpaid='+$m.unknownOrUnpaidDecisions+'; HP='+$m.hpDamage+'; MP='+$m.mpCost+'; seconds='+([double]$m.durationNanos/1e9)+'; kills='+$m.kills+'; deaths≥'+$m.deathsLowerBound+'; MODEL_REORDERED='+$m.MODEL_REORDERED+'; EXPERIMENT_REORDERED='+$m.EXPERIMENT_REORDERED+'; nativeProofSha='+$m.nativeProofSha)
        $lines+=$line;$episodes+='<p>'+[Net.WebUtility]::HtmlEncode($line)+'</p>'
        foreach($group in $m.groups){$line=($group.modeSkill+': n='+$group.n+'; meanHP='+$group.meanHp+'; varianceHP='+$group.sampleVarianceHp+'; meanMP='+$group.meanMp+'; causal advantage=UNKNOWN');$lines+=$line;$episodes+='<p>'+[Net.WebUtility]::HtmlEncode($line)+'</p>'}
    }
    $history='Исторический Goal022: 2 эпизода, TRAIN=2/TUNING=0/AUDIT=0; paid=28; kills=1; deaths≥1; MODEL_REORDERED=false/false; EXPERIMENT_REORDERED=true/false. Process 89.808/89.615с; эпизоды 44.865/36.328с. Приблизительная разность 44.943/53.287с включает preparation/start/shutdown именно этого сценария. Это не прогноз 8h и не доказательство преимущества.'
    $lines+=$history
    $html='<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Прогресс V4 TEST</title><style>body{font:16px system-ui;margin:2rem auto;padding:0 1rem;max-width:1100px;background:#f3f6fa;color:#172334}h1{font-size:1.8rem}table{border-collapse:collapse;width:100%;background:white}th,td{text-align:left;vertical-align:top;padding:.7rem;border-bottom:1px solid #dde3ed;overflow-wrap:anywhere}th{width:32%}p{line-height:1.5}.notice{padding:1rem;background:#fff0ce;border-radius:.5rem}</style><h1>Прогресс V4 TEST</h1><p class="notice">'+[Net.WebUtility]::HtmlEncode($answer)+'</p><table>'+$table+'</table><h2>Подтверждённые источники текущей линии</h2>'+$episodes+'<h2>Историческая точка отсчёта</h2><p>'+[Net.WebUtility]::HtmlEncode($history)+'</p><p>Updates не измеряют боевое качество. Процент «обучено» и ETA не определены.</p></html>'
    Write-TrainText (Join-Path $Layout.Root 'report022.md') ($lines -join "`r`n")
    Write-TrainText (Join-Path $Layout.Root 'report022.html') $html
}
