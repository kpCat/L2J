# Offline integration checks. Every mutation is confined to a fresh private copy.
function Test-Train022PostV2([string]$History,[string]$Evidence) {
    if(!$Evidence){throw 'POSTV2_EVIDENCE_REQUIRED'}
    $Evidence=[IO.Path]::GetFullPath($Evidence)
    New-TrainPrivateRoot @{Root=$Evidence}
    $source=Get-Train022Layout $History
    $script:PostV2Original=@{}
    function Copy-PostV2File([string]$From,[string]$To){
        Assert-G0PhysicalFile $From
        $sha=(Get-FileHash -LiteralPath $From).Hash
        $script:PostV2Original[$From]=$sha
        $null=[IO.Directory]::CreateDirectory((Split-Path $To -Parent))
        Copy-Item -LiteralPath $From -Destination $To
        Assert-TrainHash $To $sha
    }
    $fixture=Join-Path $Evidence 'fixture'
    New-TrainPrivateRoot @{Root=$fixture}
    foreach($name in @('train','train022','train021-c1')){New-TrainPrivateRoot @{Root=(Join-Path $fixture $name)}}
    $copyBase=Join-Path $fixture 'train022'
    foreach($file in @(Get-ChildItem -LiteralPath $source.BaseRoot -File)){
        Copy-PostV2File $file.FullName (Join-Path $copyBase $file.Name)
    }
    foreach($campaign in @(Get-ChildItem -LiteralPath (Join-Path $source.BaseRoot 'campaigns') -Directory)){
        $dest=Join-Path $copyBase ('campaigns\'+$campaign.Name)
        New-TrainPrivateRoot @{Root=$dest}
        foreach($file in @(Get-ChildItem -LiteralPath $campaign.FullName -File)){Copy-PostV2File $file.FullName (Join-Path $dest $file.Name)}
        foreach($name in @('ledger','reservations','rounds','logs')){$null=[IO.Directory]::CreateDirectory((Join-Path $dest $name))}
        foreach($name in @('ledger','reservations')){
            foreach($file in @(Get-ChildItem -LiteralPath (Join-Path $campaign.FullName $name) -File)){
                Copy-PostV2File $file.FullName (Join-Path $dest ($name+'\'+$file.Name))
            }
        }
        foreach($round in @(Get-ChildItem -LiteralPath (Join-Path $campaign.FullName 'rounds') -Directory)){
            $roundDest=Join-Path $dest ('rounds\'+$round.Name);New-TrainPrivateRoot @{Root=$roundDest}
            foreach($file in @(Get-ChildItem -LiteralPath $round.FullName -File|Where-Object {$_.Name -notlike 'preservation-*'})){
                Copy-PostV2File $file.FullName (Join-Path $roundDest $file.Name)
            }
            $sqlRecord=Join-Path $round.FullName 'sql\owned-process.json'
            if(Test-Path -LiteralPath $sqlRecord){Copy-PostV2File $sqlRecord (Join-Path $roundDest 'sql\owned-process.json')}
        }
    }
    $catalog='autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001'
    foreach($file in @(Get-ChildItem -LiteralPath (Join-Path $History $catalog) -File)){
        Copy-PostV2File $file.FullName (Join-Path $fixture ('train\'+$catalog+'\'+$file.Name))
    }
    foreach($work in @(Get-ChildItem -LiteralPath (Join-Path $History $catalog) -Directory|Where-Object {$_.Name -cmatch '^[a-f0-9]{64}$'})){
        foreach($file in @(Get-ChildItem -LiteralPath $work.FullName -File|Where-Object {$_.Name -notlike 'preservation-*'})){
            Copy-PostV2File $file.FullName (Join-Path $fixture ('train\'+$catalog+'\'+$work.Name+'\'+$file.Name))
        }
    }
    $cohort=Join-Path $History $catalog
    $manifests=@((Join-Path $cohort 'cohort016.json'))+@(Get-ChildItem -LiteralPath $cohort -Filter 'settled016.json' -Recurse -File|ForEach-Object {$_.FullName})
    foreach($manifest in $manifests){
        foreach($pin in (Read-TrainJson $manifest).files.PSObject.Properties){
            $path=[IO.Path]::GetFullPath((Join-Path $cohort $pin.Name))
            if(!$path.StartsWith($cohort+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'POSTV2_COHORT_PIN_SCOPE'}
            Copy-PostV2File $path (Join-Path $fixture ('train\'+$catalog+'\'+$pin.Name))
        }
    }
    $accepted=Join-Path (Split-Path $History -Parent) 'train021-c1'
    foreach($file in @(Get-ChildItem -LiteralPath $accepted -File|Where-Object {$_.Name -notlike 'preservation-*'})){
        Copy-PostV2File $file.FullName (Join-Path $fixture ('train021-c1\'+$file.Name))
    }
    $fixtureHistory=Join-Path $fixture 'train'
    $old=Get-Train022Layout $fixtureHistory
    $active=Join-Path $copyBase 'active022.json';$activeBytes=[IO.File]::ReadAllBytes($active)
    $cli=Join-Path $old.Module 'tools\phantom-runtime\training\Training.ps1'
    $params=(Get-Command $cli).Parameters
    $legacy=$null
    try{Invoke-TrainActionValue022 $fixtureHistory Start 480 2 -PrepareOnly|Out-Null}catch{$legacy=$_.Exception.Message}
    Write-TrainJson (Join-Path $Evidence 'legacy-red.json') @{error=$legacy;GS=0;SQL=0}
    Check022 ($legacy -cin @('TRAIN022_CONTINUATION_PREVIOUS_CAMPAIGN','TRAIN022_NEW_CAMPAIGN_REQUIRED')) '022 expected post-V2 refusal missing'
    Check022 ($params.ContainsKey('ActionValue022NewCampaign') -and $params.ContainsKey('ActionValue022PrepareOnly')) '022 post-V2 CLI switches missing'
    $script:PostV2Checks=@()
    function Reject-PostV2([string]$Name,[string[]]$Paths,[scriptblock]$Mutation,[string]$Pattern='*'){
        $saved=@{};foreach($path in $Paths){$saved[$path]=$(if(Test-Path -LiteralPath $path){[IO.File]::ReadAllBytes($path)}else{$null})}
        try{
            & $Mutation
            $beforeAdmission=[Convert]::ToBase64String([IO.File]::ReadAllBytes($active))
            $errorCode=$null
            try{Invoke-TrainActionValue022 $fixtureHistory Start 480 2 -NewCampaign -PrepareOnly|Out-Null}catch{$errorCode=$_.Exception.Message}
            Check022 ($null -ne $errorCode -and $errorCode -like $Pattern) ('022 negative admitted: '+$Name+' / '+$errorCode)
            Check022 ([Convert]::ToBase64String([IO.File]::ReadAllBytes($active)) -ceq $beforeAdmission) ('022 failed admission changed active: '+$Name)
            $script:PostV2Checks+=@{name=$Name;result='REJECT';code=$errorCode}
        }finally{
            foreach($path in $Paths){if($null -eq $saved[$path]){if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path}}else{[IO.File]::WriteAllBytes($path,$saved[$path])}}
        }
    }
    $claim=Join-Path $copyBase 'continuation022-v2.claim.json';$authority=Join-Path $copyBase 'continuation022-v2.json'
    $preflight=Join-Path $old.Root 'rounds\round000000\preflight020.json'
    $headPath=Join-Path $old.Ledger 'HEAD';$commit=Join-Path $old.Root 'rounds\round000000\commit022.json'
    Reject-PostV2 'missing claim' @($claim) {Remove-Item -LiteralPath $claim}
    Reject-PostV2 'wrong claim campaign' @($claim) {$r=Read-TrainJson $claim;$r.campaignId=('a'*32);Write-TrainJson $claim $r}
    Reject-PostV2 'spoof authority' @($authority) {$r=Read-TrainJson $authority;$r.authority='INFERRED';Write-TrainJson $authority $r}
    Reject-PostV2 'stale active source206' @($active) {$r=Read-TrainJson $active;$r.campaignId=(Read-TrainJson $authority).previousCampaign;Write-TrainJson $active $r} 'TRAIN022_SOURCE_NOT_POST_V2'
    Reject-PostV2 'changed HEAD' @($headPath) {Write-TrainText $headPath ('a'*64)}
    $model=Join-Path $old.Ledger 'candidate-90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6.bin'
    Reject-PostV2 'changed candidate216' @($model) {[IO.File]::WriteAllBytes($model,@(1,2,3))}
    Reject-PostV2 'missing cursor' @($preflight) {$r=Read-TrainJson $preflight;$r.candidates[0].PSObject.Properties.Remove('nextCursor022');Write-TrainJson $preflight $r}
    Reject-PostV2 'forged cursor' @($preflight) {$r=Read-TrainJson $preflight;$r.candidates[0].nextCursor022=2;Write-TrainJson $preflight $r}
    Reject-PostV2 'uncommitted last reservation' @($commit) {Remove-Item -LiteralPath $commit}
    $controller=Join-Path $old.Root 'controller022.json'
    Reject-PostV2 'foreign live controller PID' @($controller) {$r=Read-TrainJson $controller;$r.pid=$PID;$r.created=1;Write-TrainJson $controller $r} 'TRAIN022_SOURCE_CONTROLLER_LIVE_OR_FOREIGN'
    $nativeIdentity=Join-Path $old.Root 'rounds\round000000\native-process.json'
    Reject-PostV2 'foreign live native PID' @($nativeIdentity) {$r=Read-TrainJson $nativeIdentity;$r.pid=$PID;$r.created=1;Write-TrainJson $nativeIdentity $r} 'TRAIN022_SOURCE_NATIVE_LIVE_OR_FOREIGN'
    $catalogPath=Join-Path $fixtureHistory ($catalog+'\cohort016.json')
    Reject-PostV2 'stale catalog source' @($catalogPath) {Write-TrainText $catalogPath '{}'}
    $bootPin=(Read-TrainJson $authority).bootPins[0];$boot=Join-Path $copyBase $bootPin.relativePath
    Reject-PostV2 'tampered pinned historical boot' @($boot) {Write-TrainText $boot '{}'}
    $thirdBoot=Join-Path $old.Root 'rounds\round000000\boot-reserved020.json'
    Reject-PostV2 'tampered third historical boot' @($thirdBoot) {Write-TrainText $thirdBoot '{}'}
    $missing=Join-Path $Evidence 'missing-source';New-TrainPrivateRoot @{Root=$missing};New-TrainPrivateRoot @{Root=(Join-Path $missing 'train')}
    $rejected=$false;try{Invoke-TrainActionValue022 (Join-Path $missing 'train') Start 480 2 -NewCampaign -PrepareOnly|Out-Null}catch{$rejected=$_.Exception.Message -ceq 'TRAIN022_NEW_SOURCE_REQUIRED'}
    Check022 $rejected '022 independent Start silently initialized188'
    $extraBoot=Join-Path $old.Root 'rounds\round000000\extra\boot-reserved020.json'
    $null=[IO.Directory]::CreateDirectory((Split-Path $extraBoot -Parent))
    Reject-PostV2 'unauthorized fourth historical boot' @($extraBoot) {Write-TrainText $extraBoot '{}'} 'TRAIN022_*BOOT*'
    $beforeRead=@{};foreach($n in @('status022.json','report022.json','campaign022.json')){$beforeRead[$n]=(Get-FileHash -LiteralPath (Join-Path $old.Root $n)).Hash}
    foreach($command in @('Status','Report')){Invoke-TrainActionValue022 $fixtureHistory $command|Out-Null}
    foreach($n in $beforeRead.Keys){Assert-TrainHash (Join-Path $old.Root $n) $beforeRead[$n]}
    foreach($command in @('Stop','Pause','Resume')){
        $refused=$false;try{Invoke-TrainActionValue022 $fixtureHistory $command -NewCampaign -PrepareOnly|Out-Null}catch{$refused=$_.Exception.Message -ceq 'TRAIN022_NEW_OPTIONS_START_ONLY'}
        Check022 $refused ('022 new options reached '+$command)
    }
    $resumeRefused=$false
    try{Invoke-TrainActionValue022 $fixtureHistory Resume|Out-Null}catch{$resumeRefused=$_.Exception.Message -like 'TRAIN022_RESUME_*'}
    Check022 $resumeRefused '022 V2 Resume admitted exhausted budget'
    # Reserve an interrupted publication in a separate copy; it must stay visible.
    $failed=Join-Path $Evidence 'failed';New-TrainPrivateRoot @{Root=$failed}
    foreach($name in @('train','train022')){Copy-Item -LiteralPath (Join-Path $fixture $name) -Destination $failed -Recurse}
    $privateAcl=Get-Acl -LiteralPath (Join-Path $fixture 'train')
    foreach($directory in @(Get-ChildItem -LiteralPath $failed -Recurse -Directory)){Set-Acl -LiteralPath $directory.FullName -AclObject $privateAcl}
    $failedHistory=Join-Path $failed 'train';$failedLayout=Get-Train022Layout $failedHistory
    $cold=Join-Path $failedLayout.Ledger 'cold-1ff7547d9d4571673e1e5bfa58a1f3ac3799e4d95f587a32db9ae39622df2f69.bin'
    Write-TrainText $cold 'tampered'
    $failedActive=Join-Path $failedLayout.BaseRoot 'active022.json';$failedSha=(Get-FileHash -LiteralPath $failedActive).Hash
    $rejected=$false;try{Invoke-TrainActionValue022 $failedHistory Start 480 2 -NewCampaign -PrepareOnly|Out-Null}catch{$rejected=$_.Exception.Message -ceq 'TRAIN022_COMMIT_FAILED'}
    Check022 $rejected '022 corrupt cold accepted';Assert-TrainHash $failedActive $failedSha
    $rejected=$false;try{Invoke-TrainActionValue022 $failedHistory Start 480 2 -NewCampaign -PrepareOnly|Out-Null}catch{$rejected=$_.Exception.Message -ceq 'TRAIN022_ADMISSION_INCOMPLETE'}
    Check022 $rejected '022 interrupted admission silently restarted'
    $oldAfterFailure=Get-Train022Status $failedLayout
    Check022 ($oldAfterFailure.trainerStatus -ceq 'AUTO_STOPPED') '022 failed prepare hid previous active status'
    $race=Join-Path $Evidence 'race';New-TrainPrivateRoot @{Root=$race}
    foreach($name in @('train','train022')){Copy-Item -LiteralPath (Join-Path $fixture $name) -Destination $race -Recurse}
    foreach($directory in @(Get-ChildItem -LiteralPath $race -Recurse -Directory)){Set-Acl -LiteralPath $directory.FullName -AclObject $privateAcl}
    $raceHistory=Join-Path $race 'train';$raceBase=Join-Path $race 'train022'
    $countBefore=@(Get-ChildItem -LiteralPath (Join-Path $raceBase 'campaigns') -Directory).Count
    $raceArgs=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$cli+'"'),'-Action','ActionValue022','-ActionValue022Command','Start','-Root',('"'+$raceHistory+'"'),'-ActionValue022NewCampaign','-ActionValue022PrepareOnly','-ActionValue022Minutes','480','-ActionValue022BootLimit','2')
    $racers=@()
    try{
        $first=Start-Process -FilePath $old.PowerShell -ArgumentList $raceArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Evidence 'race1.out') -RedirectStandardError (Join-Path $Evidence 'race1.err')
        $racers+=@{process=$first;handle=(Hold-ProcessHandle $first)}
        $clock=[Diagnostics.Stopwatch]::StartNew()
        while(!(Get-ChildItem -LiteralPath $raceBase -File -Filter 'admission-*.json') -and !$first.HasExited -and $clock.Elapsed.TotalSeconds -lt 15){Start-Sleep -Milliseconds 50}
        Check022 (@(Get-ChildItem -LiteralPath $raceBase -File -Filter 'admission-*.json').Count -eq 1) '022 fresh admission not observed'
        $during=Invoke-TrainActionValue022 $raceHistory Status|ConvertFrom-Json
        Check022 ($during.trainerStatus -cin @('AUTO_STOPPED','NOT_STARTED')) '022 Status unavailable during preparation'
        $second=Start-Process -FilePath $old.PowerShell -ArgumentList $raceArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Evidence 'race2.out') -RedirectStandardError (Join-Path $Evidence 'race2.err')
        $racers+=@{process=$second;handle=(Hold-ProcessHandle $second)}
        foreach($racer in $racers){Check022 ($racer.process.WaitForExit(30000)) '022 fresh concurrent prepare timeout';Check022 ((Get-TrackedProcessExitCode $racer.process $racer.handle) -eq 0) '022 fresh concurrent prepare failed'}
    }finally{foreach($racer in $racers){$racer.process.Dispose()}}
    Check022 (@(Get-ChildItem -LiteralPath (Join-Path $raceBase 'campaigns') -Directory).Count -eq ($countBefore+1)) '022 concurrent fresh Start created duplicate campaign'
    $raceLayout=Get-Train022Layout $raceHistory
    Check022 ((Get-Train022Status $raceLayout).gsBoots -eq 0 -and !(Test-Path -LiteralPath (Join-Path $raceLayout.Root 'controller022.json'))) '022 race crossed broker boundary'
    # Use the actual CLI, in its own PowerShell process (Training.ps1 owns exit).
    & $old.PowerShell -NoProfile -ExecutionPolicy Bypass -File $cli -Action ActionValue022 -ActionValue022Command Start -Root $fixtureHistory -ActionValue022NewCampaign -ActionValue022PrepareOnly -ActionValue022Minutes 480 -ActionValue022BootLimit 2 | Out-Null
    Check022 ($LASTEXITCODE -eq 0) '022 real prepare-only CLI failed'
    $new=Get-Train022Layout $fixtureHistory;$config=Read-TrainJson (Join-Path $new.Root 'campaign022.json');$state=Get-Train022Status $new
    Check022 ($new.Root -ine $old.Root -and $config.previousCampaign -ceq (Split-Path $old.Root -Leaf) -and $config.cursorStart -eq 4 -and $config.maxBoots -eq 2) '022 independent lineage/budget lost'
    Check022 (([datetime]$config.deadlineUtc-[datetime]$config.startedUtc).TotalMinutes -eq 480) '022 future plan duration changed'
    Check022 ($state.modelSha -ceq '90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6' -and $state.globalUpdates -eq 216 -and $state.gsBoots -eq 0) '022 imported model/credit changed'
    $lineage=Read-TrainJson (Join-Path $new.Root 'lineage022.json')
    Check022 ($lineage.step -eq 2 -and $lineage.historicalV2Boots -eq 3) '022 global sequence/history lost'
    $proposal=Invoke-Train022Java $new 'proposal' @($new.Ledger,(Join-Path $fixtureHistory $catalog),'4','2',([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')))
    Check022 ($proposal.cursor -eq 4 -and $proposal.policy -ceq 'MODEL_V1') '022 next curriculum step reset'
    foreach($pin in $lineage.ledgerPins.PSObject.Properties){Assert-TrainHash (Join-Path $old.Ledger $pin.Name) $pin.Value;Assert-TrainHash (Join-Path $new.Ledger $pin.Name) $pin.Value}
    $newActiveSha=(Get-FileHash -LiteralPath $active).Hash
    Invoke-TrainActionValue022 $fixtureHistory Start 480 2 -NewCampaign -PrepareOnly|Out-Null
    Assert-TrainHash $active $newActiveSha
    $configPath=Join-Path $new.Root 'campaign022.json';$configSha=(Get-FileHash -LiteralPath $configPath).Hash
    # Real authenticated current process exercises the live branch, without a trainer.
    $controllerPath=Join-Path $new.Root 'controller022.json';$statusPath=Join-Path $new.Root 'status022.json'
    $statusBytes=[IO.File]::ReadAllBytes($statusPath)
    $self=[Diagnostics.Process]::GetCurrentProcess()
    $identity=New-TrainProcessRecord $self $self.MainModule.FileName $Evidence Director
    Write-TrainJson $controllerPath $identity
    $running=Read-TrainJson $statusPath;$running.trainerStatus='RUNNING';Write-TrainJson $statusPath $running
    Invoke-TrainActionValue022 $fixtureHistory Start 480 2 -NewCampaign -PrepareOnly|Out-Null
    Assert-TrainHash $active $newActiveSha;Assert-TrainHash $configPath $configSha
    Remove-Item -LiteralPath $controllerPath;[IO.File]::WriteAllBytes($statusPath,$statusBytes)
    # Concurrent calls use the same named mutex and already prepared active root.
    $args022=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$cli+'"'),'-Action','ActionValue022','-ActionValue022Command','Start','-Root',('"'+$fixtureHistory+'"'),'-ActionValue022NewCampaign','-ActionValue022PrepareOnly','-ActionValue022Minutes','480','-ActionValue022BootLimit','2')
    $children=@()
    try{
        foreach($i in 1..2){$children+=Start-Process -FilePath $old.PowerShell -ArgumentList $args022 -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Evidence ('double'+$i+'.out')) -RedirectStandardError (Join-Path $Evidence ('double'+$i+'.err'))}
        foreach($child in $children){$held=Hold-ProcessHandle $child;Check022 ($child.WaitForExit(30000)) '022 concurrent prepare timeout';Check022 ((Get-TrackedProcessExitCode $child $held) -eq 0) '022 concurrent prepare failed'}
    }finally{foreach($child in $children){$child.Dispose()}}
    Assert-TrainHash $active $newActiveSha;Assert-TrainHash $configPath $configSha
    foreach($command in @('Pause','Stop')){Invoke-TrainActionValue022 $fixtureHistory $command|Out-Null;Assert-TrainHash $configPath $configSha}
    Invoke-TrainActionValue022 $fixtureHistory Start 15 1 -NewCampaign -PrepareOnly|Out-Null
    $next=Get-Train022Layout $fixtureHistory;$nextConfig=Read-TrainJson (Join-Path $next.Root 'campaign022.json')
    Check022 ($next.Root -ine $new.Root -and $nextConfig.cursorStart -eq 4 -and $nextConfig.durationMinutes -eq 15 -and $nextConfig.maxBoots -eq 1) '022 empty stopped session stranded inherited cursor'
    Check022 (!(Test-Path -LiteralPath (Join-Path $new.Root 'broker022.json')) -and !(Test-Path -LiteralPath (Join-Path $new.Root 'controller022.json'))) '022 prepare started broker'
    Check022 ((Get-Train022HistoricalBoots $old (Read-TrainJson (Join-Path $old.Root 'campaign022.json'))) -eq 3) '022 V2 historical counter reset'
    Assert-TrainPortsFree @(33364,18024,19324)
    foreach($path in $script:PostV2Original.Keys){Assert-TrainHash $path $script:PostV2Original[$path]}
    Write-TrainJson (Join-Path $Evidence 'result.json') @{status='OFFLINE_GREEN';negative=$script:PostV2Checks;modelSha=$state.modelSha;globalUpdates=216;globalKeys=302;cursor=4;step=2;historicalBoots=3;newBoots=0;GS=0;SQL=0;native=0;training480='NOT_RUN';manualGate='REQUIRED';originalFilesVerified=$script:PostV2Original.Count}
    Write-Output ('TRAIN022_POST_V2_GREEN negatives='+$script:PostV2Checks.Count+' originalFiles='+$script:PostV2Original.Count+' cursor=4 updates=216 keys=302 GS=0 SQL=0')
}
