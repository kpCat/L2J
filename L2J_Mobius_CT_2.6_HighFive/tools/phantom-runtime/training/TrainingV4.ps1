param([switch]$Controller022,[string]$HistoryRoot022,[string]$Nonce022)
# Explicit TEST-only profile. Dot-sourcing defines functions without starting anything.
. (Join-Path $PSScriptRoot 'TrainingQualityV4.ps1')
. (Join-Path $PSScriptRoot 'TrainingBatchV4.ps1')
function Write-Train022Immutable([string]$Path,$Value) {
    Assert-TrainDirectory (Split-Path $Path -Parent)
    $bytes=$script:TrainUtf8.GetBytes(($Value|ConvertTo-Json -Depth 16))
    if(Test-Path -LiteralPath $Path){throw 'TRAIN022_IMMUTABLE_ALREADY_EXISTS'}
    $part=$Path+'.'+[guid]::NewGuid().ToString('N')+'.part'
    try{
        $stream=[IO.FileStream]::new($part,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
        [IO.File]::Move($part,$Path)
    }finally{if(Test-Path -LiteralPath $part){Remove-Item -LiteralPath $part}}
}
function Get-Train022StopReason($Config,[datetime]$Now,[long]$DiskFree,[int]$Boots,[int]$NoData,[int]$Stagnation=0) {
    if($Now.ToUniversalTime() -lt ([datetime]$Config.startedUtc).ToUniversalTime()){return 'CLOCK_SKEW'}
    if($Now.ToUniversalTime() -ge ([datetime]$Config.deadlineUtc).ToUniversalTime()){return 'DEADLINE'}
    if($DiskFree -lt [long]$Config.minDiskBytes){return 'DISK_LIMIT'}
    if($Boots -ge [int]$Config.maxBoots){return 'BOOT_LIMIT'}
    if($NoData -ge [int]$Config.maxNoData){return 'NO_DATA'}
    if($Stagnation -gt 0 -and $Stagnation -ge [int]$Config.maxStagnation){return 'STAGNATION'}
    return ''
}
function Get-Train022Layout([string]$HistoryRoot) {
    if(!$HistoryRoot -or ![IO.Path]::IsPathRooted($HistoryRoot)){throw 'TRAIN022_ABSOLUTE_HISTORY_REQUIRED'}
    $history=[IO.Path]::GetFullPath($HistoryRoot).TrimEnd('\');Assert-TrainInstallation @{Root=$history}
    if((Split-Path $history -Leaf) -cne 'train'){throw 'TRAIN022_READONLY_TRAIN_REQUIRED'}
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
    $baseRoot=Join-Path (Split-Path $history -Parent) 'train022';$root=$baseRoot
    if(Test-Path -LiteralPath (Join-Path $baseRoot 'active022.json')){
        Assert-TrainInstallation @{Root=$baseRoot};$active=Read-TrainJson (Join-Path $baseRoot 'active022.json')
        if($active.campaignId -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN022_ACTIVE_SCOPE'}
        $root=Join-Path $baseRoot ('campaigns\'+$active.campaignId);Assert-TrainInstallation @{Root=$root}
    }
    return @{Root=$root;BaseRoot=$baseRoot;History=$history;Ledger=(Join-Path $root 'ledger');Module=$module;BuildRoot=(Join-Path $module '.phantom-local\learning022-build');PowerShell=(Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe')}
}
function Get-Train022AcceptedPins($Approval,[string[]]$Allowed) {
    if($Approval.schema -cne 'APPROVED_PROTECTED022_V1' -or $Approval.authority -cne 'EXPLICIT_OWNER_CHAT' -or @($Approval.pins).Count -ne 2 -or $Allowed.Count -ne 2){throw 'TRAIN022_SOURCE_APPROVAL_INVALID'}
    $accepted=@{}
    foreach($pin in $Approval.pins){
        if($pin.path -cnotin $Allowed -or $accepted.ContainsKey($pin.path) -or $pin.sha256 -cnotmatch '^[A-Fa-f0-9]{64}$'){throw 'TRAIN022_SOURCE_APPROVAL_SCOPE'}
        $accepted[$pin.path]=$pin
    }
    return $accepted
}
function Assert-Train022Assets($Context,$Config) {
    $layout=Get-Train022Layout $Context.History
    $approvalPath=Join-Path $layout.BaseRoot 'approved-protected022.json'
    if(!(Test-Path -LiteralPath $approvalPath)){
        Assert-TrainAssets (Join-Path $Context.History 'game\geodata') (Join-Path $Context.History 'geo203.json') (Join-Path $Context.History 'protected207.json') $Config
        return
    }
    # This distinct TEST022 authority is never inferred from drift or written by Start.
    $approval=Read-TrainJson $approvalPath
    $geoPath=Join-Path $Context.History 'geo203.json';$protectedPath=Join-Path $Context.History 'protected207.json'
    Assert-TrainHash $geoPath $script:TrainGeoSha;Assert-TrainHash $protectedPath (Get-TrainProtectedPin $Config)
    if($approval.historicalManifestSha -ine (Get-FileHash -LiteralPath $protectedPath).Hash){throw 'TRAIN022_HISTORICAL_MANIFEST_CHANGED'}
    $repository=Split-Path $layout.Module -Parent
    $allowed=@((Join-Path $repository 'AGENTS.md'),(Join-Path $layout.Module 'build.xml'))
    $accepted=Get-Train022AcceptedPins $approval $allowed
    $geo=Read-TrainJson $geoPath;$protected=Read-TrainJson $protectedPath;$directory=Join-Path $Context.History 'game\geodata'
    Assert-TrainDirectory $directory;$files=@(Get-ChildItem -LiteralPath $directory -File -Force)
    if($geo.Count -ne 203 -or $files.Count -ne 203 -or ($geo|Measure-Object bytes -Sum).Sum -ne 1063452308 -or @(Compare-Object $files.Name $geo.name -CaseSensitive).Count -or $protected.Count -ne 207){throw 'TRAIN022_ASSET_SET'}
    foreach($row in $geo){$path=Join-Path $directory $row.name;if((Get-Item -LiteralPath $path).Length -ne $row.bytes){throw 'TRAIN022_GEO_SIZE'};Assert-TrainHash $path $row.sha256}
    $used=0
    foreach($row in $protected){
        if($accepted.ContainsKey($row.path)){
            if($accepted[$row.path].historicalSha -ine $row.sha256){throw 'TRAIN022_APPROVAL_HISTORICAL_BINDING'}
            Assert-TrainHash $row.path $accepted[$row.path].sha256;$used++
        }else{Assert-TrainHash $row.path $row.sha256}
    }
    if($used -ne 2){throw 'TRAIN022_APPROVAL_EXACT_TWO_REQUIRED'}
}
function Get-Train022JavaFlags($Layout) {
    $c=$Layout.Context022
    $batch=@();if($c.ContainsKey('Batch024') -and $c.Batch024){$batch=@('-Dphantom.learning.batch024=true')}
    return $batch+@('-Dphantom.learning.autonomous022=true',('-Dphantom.learning022.rootName='+(Split-Path $c.Root -Leaf)),('-Dphantom.learning022.parent='+$c.ModelSha),('-Dphantom.learning022.updates='+$c.LocalUpdates),('-Dphantom.learning022.round='+$c.Round),('-Dphantom.learning022.cursor='+$c.Cursor),('-Dphantom.learning022.split='+$c.Split),('-Dphantom.learning022.boot='+($c.BootsBefore+1)),('-Dphantom.learning022.step='+$c.Step))
}
function Test-Train022Property($Value,[string]$Name) {
    if($Value -is [Collections.IDictionary]){return $Value.Contains($Name)}
    return $null -ne $Value.PSObject.Properties[$Name]
}
function Assert-Train022ContinuationContract($Authority) {
    if($Authority.schema -cne 'GOAL022_CONTINUATION_V2' -or $Authority.authority -cne 'EXPLICIT_OWNER_CHAT' -or
        $Authority.baseline -cne '74a1c2ce6bc231fb21b61875272ff1667a606bde' -or $Authority.parentSha -cne '99ebcd8fe606ab54379f59dc411fec0f493b8d388009b0f564eec548f76ba537' -or
        $Authority.historicalBoots -ne 2 -or $Authority.additionalBoots -ne 1 -or $Authority.totalBootLimit -ne 3 -or $Authority.cursor -ne 2 -or @($Authority.bootPins).Count -ne 2){throw 'TRAIN022_CONTINUATION_AUTHORITY_SCOPE'}
}
function Get-Train022HistoricalBoots($Layout,$Config) {
    if(!(Test-Train022Property $Config 'continuationSha')){return @(Get-ChildItem -LiteralPath (Join-Path $Layout.Root 'rounds') -Directory|Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'boot-reserved020.json')}).Count}
    $path=Join-Path $Layout.BaseRoot 'continuation022-v2.json';Assert-TrainHash $path $Config.continuationSha
    $authority=Read-TrainJson $path;Assert-Train022ContinuationContract $authority
    $allowed=@{}
    foreach($pin in $authority.bootPins){
        $full=[IO.Path]::GetFullPath((Join-Path $Layout.BaseRoot $pin.relativePath))
        if(!$full.StartsWith($Layout.BaseRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or $allowed.ContainsKey($full)){throw 'TRAIN022_HISTORICAL_BOOT_SCOPE'}
        Assert-G0PhysicalFile $full;Assert-TrainHash $full $pin.sha256;$allowed[$full]=$true
    }
    # V2 owns only its frozen historical campaigns and its own rounds. Later
    # independent sessions must never add to, or reset, this one-time budget.
    $roots=@($Layout.Root)
    foreach($pin in $authority.bootPins){$roots+=Join-Path $Layout.BaseRoot (($pin.relativePath -split '\\rounds\\')[0])}
    $boots=@(foreach($root in @($roots|Select-Object -Unique)){Get-ChildItem -LiteralPath (Join-Path $root 'rounds') -Filter 'boot-reserved020.json' -Recurse -File})
    foreach($boot in $boots){if(!$allowed.ContainsKey($boot.FullName) -and !$boot.FullName.StartsWith((Join-Path $Layout.Root 'rounds')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'TRAIN022_UNACCOUNTED_HISTORICAL_BOOT'}}
    if($boots.Count -gt 3 -or $boots.Count -lt 2){throw 'TRAIN022_HISTORICAL_BOOT_LIMIT'}
    return $boots.Count
}
function Get-Train022Cursor($Layout,$Config,[int]$Round) {
    if($Round -eq 0){if(Test-Train022Property $Config 'cursorStart'){return [long]$Config.cursorStart};return 0L}
    $path=Join-Path $Layout.Root ('rounds\round'+($Round-1).ToString('D6')+'\preflight020.json')
    if(Test-Path -LiteralPath $path){return [long](Read-TrainJson $path).candidates[0].nextCursor022}
    return [long]$Round
}
function Assert-Train022NoController($Layout) {
    $path=Join-Path $Layout.Root 'controller022.json'
    if(Test-Path -LiteralPath $path){
        $record=Read-TrainJson $path
        # Any current occupant of the recorded PID is an uncertain identity.
        if(Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"){throw 'TRAIN022_SOURCE_CONTROLLER_LIVE_OR_FOREIGN'}
    }
}
function Get-Train022IndependentSource($Layout,[string[]]$Visited=@()) {
    if($Visited.Count -ge 32 -or $Layout.Root -cin $Visited){throw 'TRAIN022_CURSOR_LINEAGE_CYCLE'}
    $Visited+= $Layout.Root
    Assert-Train022NoController $Layout
    $config=Read-TrainJson (Join-Path $Layout.Root 'campaign022.json')
    $status=Get-Train022Status $Layout
    if($status.trainerStatus -cnotin @('STOPPED','AUTO_STOPPED')){throw 'TRAIN022_SOURCE_NOT_STOPPED'}
    $authorityPath=Join-Path $Layout.BaseRoot 'continuation022-v2.json'
    $claimPath=Join-Path $Layout.BaseRoot 'continuation022-v2.claim.json'
    # Published immutable V2 authority and its consumed claim; never re-created.
    Assert-TrainHash $authorityPath '1039643EA733AB14989D32F56B10E76CA67FA941051B89AB7A814D06ABB44BDA'
    Assert-TrainHash $claimPath 'E571C195D130EB755F1097EAFEC9F3CFEFC94B47AB1BD133DE61CBE293182783'
    $authority=Read-TrainJson $authorityPath;Assert-Train022ContinuationContract $authority
    $claim=Read-TrainJson $claimPath
    if($claim.authoritySha -ine (Get-FileHash -LiteralPath $authorityPath).Hash -or $claim.campaignId -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN022_CONSUMED_CLAIM_BINDING'}
    if($config.campaignId -cne (Split-Path $Layout.Root -Leaf) -or
       ($config.campaignId -cne $claim.campaignId -and (!(Test-Train022Property $config 'transition') -or $config.transition -cne 'INDEPENDENT_TEST022_V1'))){throw 'TRAIN022_SOURCE_NOT_POST_V2'}
    $v2=@{};foreach($key in $Layout.Keys){$v2[$key]=$Layout[$key]}
    $v2.Root=Join-Path $Layout.BaseRoot ('campaigns\'+$claim.campaignId);$v2.Ledger=Join-Path $v2.Root 'ledger'
    Assert-TrainInstallation $v2;Assert-Train022NoController $v2
    Assert-TrainHash (Join-Path $v2.Root 'campaign022.json') 'CECDAE6E7DCA60436C74865B18B9C65D44233FAEEEECCD2FF40E3BF0D6A7D160'
    Assert-TrainHash (Join-Path $v2.Root 'rounds\round000000\boot-reserved020.json') 'E0F819A3D74FEBAB33A512E0D86CF5F31D8E863BACF8BA7824A926EBCC93E351'
    $v2config=Read-TrainJson (Join-Path $v2.Root 'campaign022.json');$v2state=Get-Train022Status $v2
    if($v2config.continuationSha -ine $claim.authoritySha -or $v2config.previousCampaign -cne $authority.previousCampaign -or
       $v2state.trainerStatus -cnotin @('STOPPED','AUTO_STOPPED') -or $v2state.gsBoots -ne 3 -or (Get-Train022HistoricalBoots $v2 $v2config) -ne 3){throw 'TRAIN022_V2_NOT_SEALED_THREE_BOOTS'}
    $previous=Join-Path $Layout.BaseRoot ('campaigns\'+$authority.previousCampaign)
    Assert-TrainHash (Join-Path $previous 'campaign022.json') $authority.previousConfigSha
    if([IO.File]::ReadAllText((Join-Path $previous 'ledger\HEAD')) -cne $authority.previousHead){throw 'TRAIN022_CONTINUATION_HEAD_CHANGED'}
    $catalog=Join-Path $Layout.History 'autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001\cohort016.json'
    Assert-TrainHash $catalog $config.sourceCatalogSha
    foreach($pin in $config.sources.PSObject.Properties){
        # This corrective changes the orchestrator, never its pinned native inputs.
        if($pin.Name -ine (Join-Path $PSScriptRoot 'TrainingV4.ps1')){Assert-TrainHash $pin.Name $pin.Value}
    }
    $last=$null;$lastContext=$null
    foreach($path in @(Get-ChildItem -LiteralPath (Join-Path $Layout.Root 'reservations') -File|Sort-Object Name)){
        $reservation=Read-TrainJson $path.FullName
        $context=Get-Train022Context $Layout $reservation
        Assert-Train022Closed $context
        foreach($name in @('native-process.json','sql\owned-process.json')){
            $identityPath=Join-Path $context.Root $name
            if(Test-Path -LiteralPath $identityPath){$identity=Read-TrainJson $identityPath;if(Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.pid)"){throw 'TRAIN022_SOURCE_NATIVE_LIVE_OR_FOREIGN'}}
        }
        Assert-TrainHash (Join-Path $Layout.Root 'campaign022.json') $reservation.configSha
        $commitPath=Join-Path $context.Root 'commit022.json'
        if(Test-Path -LiteralPath $commitPath){
            $commit=Read-TrainJson $commitPath
            if(!$last -or [long]$commit.sequence -gt [long]$last.sequence){$last=$commit;$lastContext=$context}
        }elseif(!(Test-Path -LiteralPath (Join-Path $context.Root 'aborted022.json'))){throw 'TRAIN022_INCOMPLETE_SOURCE_RESERVATION'}
    }
    if(!$last -and (Test-Train022Property $config 'transition') -and $config.transition -ceq 'INDEPENDENT_TEST022_V1'){
        if($config.previousCampaign -cnotmatch '^[a-f0-9]{32}$'){throw 'TRAIN022_CURSOR_PREVIOUS_SCOPE'}
        $parentLayout=@{};foreach($key in $Layout.Keys){$parentLayout[$key]=$Layout[$key]}
        $parentLayout.Root=Join-Path $Layout.BaseRoot ('campaigns\'+$config.previousCampaign);$parentLayout.Ledger=Join-Path $parentLayout.Root 'ledger'
        Assert-TrainHash (Join-Path $parentLayout.Root 'campaign022.json') $config.previousConfigSha
        $inherited=Get-Train022IndependentSource $parentLayout $Visited
        if($config.previousHead -cne $inherited.head.head -or $config.previousModelSha -cne $inherited.head.modelSha -or
           $config.cursorStart -ne $inherited.cursor -or $config.cursorPreflightSha -ine $inherited.preflightSha){throw 'TRAIN022_INHERITED_CURSOR_CHANGED'}
        $last=$inherited.head;$lastContext=$inherited.context
    }
    if(!$last){throw 'TRAIN022_COMMITTED_CURSOR_REQUIRED'}
    if([IO.File]::ReadAllText((Join-Path $Layout.Ledger 'HEAD')) -cne $last.head -or $last.sequence -ne ($lastContext.Step+1)){throw 'TRAIN022_COMMITTED_HEAD_CHANGED'}
    $preflightPath=Join-Path $lastContext.Root 'preflight020.json'
    $preflight=Read-TrainJson $preflightPath;$candidate=$preflight.candidates[0]
    if(!(Test-Train022Property $candidate 'nextCursor022') -or $candidate.cursor022 -ne $lastContext.Cursor -or
       [long]$candidate.nextCursor022 -le $lastContext.Cursor){throw 'TRAIN022_COMMITTED_CURSOR_INVALID'}
    if($Layout.Root -ieq $v2.Root){
        Assert-TrainHash $preflightPath '3763F0633444C945022BEC8F4F9DC6B9AD5A226D907D801733467059645D761A'
        if($last.modelSha -cne '90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6' -or
           $last.head -cne '1ff7547d9d4571673e1e5bfa58a1f3ac3799e4d95f587a32db9ae39622df2f69' -or
           $last.globalUpdates -ne 216 -or $last.globalKeys -ne 302 -or $last.sequence -ne 2 -or $candidate.nextCursor022 -ne 4){throw 'TRAIN022_V2_PARENT216_REQUIRED'}
    }
    Assert-TrainHash (Join-Path $Layout.Ledger ('candidate-'+$last.modelSha+'.bin')) $last.modelSha
    return @{campaignId=$config.campaignId;configSha=(Get-FileHash -LiteralPath (Join-Path $Layout.Root 'campaign022.json')).Hash;head=$last;cursor=[long]$candidate.nextCursor022;preflightSha=(Get-FileHash -LiteralPath $preflightPath).Hash;context=$lastContext;historicalBoots=3;claimSha=(Get-FileHash -LiteralPath $claimPath).Hash;authoritySha=$claim.authoritySha}
}
function Assert-Train022Round([string]$History,$Context) {
    if($Context.ContainsKey('Batch024') -and $Context.Batch024){Assert-Train024Round $Context;return}
    $layout=Get-Train022Layout $History;Assert-TrainInstallation $layout
    if($Context.Root -cne (Join-Path $layout.Root ('rounds\round'+([int]$Context.Round).ToString('D6'))) -or $Context.ModelSha -cnotmatch '^[a-f0-9]{64}$' -or
        [int]$Context.LocalUpdates -lt 0 -or $Context.Split -cnotin @('TRAIN','TUNING','AUDIT') -or $Context.BuildRoot -ine $layout.BuildRoot){throw 'TRAIN022_ROUND_SCOPE'}
    $reservation=Read-TrainJson (Join-Path $layout.Root ('reservations\'+([int]$Context.Round).ToString('D6')+'.json'))
    if($reservation.modelSha -cne $Context.ModelSha -or $reservation.split -cne $Context.Split -or $reservation.head -cne $Context.Head){throw 'TRAIN022_FROZEN_RESERVATION_CHANGED'}
    Assert-TrainHash (Join-Path $layout.Root 'campaign022.json') $reservation.configSha
}
function Get-Train022Command($Layout) {
    $path=Join-Path $Layout.Root 'command022.json'
    if(!(Test-Path -LiteralPath $path)){return ''}
    $command=Read-TrainJson $path;$config=Read-TrainJson (Join-Path $Layout.Root 'campaign022.json')
    if($command.campaignId -cne $config.campaignId -or $command.kind -cnotin @('Pause','Stop','Resume')){throw 'TRAIN022_COMMAND_IDENTITY'}
    return $command.kind
}
function Assert-Train022Boundary($Context,[switch]$Fast) {
    if($Context.ContainsKey('Batch024') -and $Context.Batch024){Assert-Train024Boundary $Context -Fast:$Fast;return}
    $layout=Get-Train022Layout $Context.History;$config=Read-TrainJson (Join-Path $layout.Root 'campaign022.json')
    Assert-TrainHash (Join-Path $layout.Root 'campaign022.json') $Context.ConfigSha
    $actualBoots=Get-Train022HistoricalBoots $layout $config
    if($actualBoots -lt $Context.BootsBefore -or $actualBoots -gt ($Context.BootsBefore+1)){throw 'TRAIN022_BOOT_ACCOUNTING_CHANGED'}
    $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($layout.Root))
    $now=[datetime]::UtcNow
    if($Context.ContainsKey('LastUtc') -and $now -lt $Context.LastUtc){throw 'TRAIN022_CLOCK_SKEW'}
    $Context.LastUtc=$now
    $reason=Get-Train022StopReason $config ([datetime]::UtcNow) $drive.AvailableFreeSpace ([int]$Context.BootsBefore) 0
    if($reason){throw ('TRAIN022_'+$reason)}
    $command=Get-Train022Command $layout
    if($command -cin @('Pause','Stop')){throw ('TRAIN022_'+$command.ToUpperInvariant())}
    if(!$Fast){foreach($pin in $config.sources.PSObject.Properties){Assert-TrainHash $pin.Name $pin.Value}}
}
function Request-Train022Drain($Context,[Diagnostics.Process]$Process,[Diagnostics.Stopwatch]$Clock) {
    $layout=if($Context.ContainsKey('Batch024') -and $Context.Batch024){$Context.Layout024}else{Get-Train022Layout $Context.History};$config=Read-TrainJson (Join-Path $layout.Root 'campaign022.json')
    $reason=''
    try{Assert-Train022Boundary $Context -Fast}catch{$reason=$_.Exception.Message}
    $resources=Get-TrainProcessResources $Process
    if($resources.measurement -ceq 'UNKNOWN'){$reason='RESOURCE_UNKNOWN'}
    elseif($resources.workingSetBytes -gt [long]$config.maxMemoryBytes){$reason='MEMORY_LIMIT'}
    elseif(Test-Train022CpuExceeded $Context $resources.cpuSeconds $Clock.Elapsed.TotalSeconds ([double]$config.maxCpuCores) (Test-Path -LiteralPath (Join-Path $Context.Root 'game-ready022.marker'))){$reason='CPU_LIMIT'}
    if($reason -and !(Test-Path -LiteralPath (Join-Path $Context.Root 'stop.request'))){
        Write-Train022Immutable (Join-Path $Context.Root 'resource-stop022.json') @{reason=$reason;resources=$resources;elapsedSeconds=$Clock.Elapsed.TotalSeconds;maxCpuCores=$config.maxCpuCores;cpuPhase='POST_GS_READY_10_SECOND_DELTA'}
        Write-TrainText (Join-Path $Context.Root 'stop.request') $reason
    }
}
function Test-Train022CpuExceeded($Context,[double]$Cpu,[double]$Seconds,[double]$Limit,[bool]$Ready) {
    if(!$Ready){return $false}
    if(!$Context.ContainsKey('CpuBase')){$Context.CpuBase=$Cpu;$Context.CpuBaseSeconds=$Seconds;return $false}
    $elapsed=$Seconds-[double]$Context.CpuBaseSeconds
    if($elapsed -lt 10){return $false}
    if($Cpu -lt [double]$Context.CpuBase -or $elapsed -le 0){throw 'TRAIN022_CPU_CLOCK_INVALID'}
    $rate=($Cpu-[double]$Context.CpuBase)/$elapsed
    $Context.CpuBase=$Cpu;$Context.CpuBaseSeconds=$Seconds
    return $rate -gt $Limit
}
function Invoke-Train022Java($Layout,[string]$Command,[string[]]$Arguments,$Context=$null) {
    if($Command.StartsWith('batch-',[StringComparison]::Ordinal)){return Invoke-Train024Java $Layout $Command.Substring(6) $Arguments $Context}
    $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
    $classpath=(Join-Path $Layout.BuildRoot 'phantom-test\bin')+';'+(Join-Path $Layout.BuildRoot 'runtime-libs\*')
    if($Command -ceq 'verify-history'){
        $historyBin=Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))) '.phantom-local\learning023-build\phantom-test\bin'
        Assert-G0PhysicalFile (Join-Path $historyBin 'org\l2jmobius\tests\phantoms\PhantomLearning022V4Trainer.class')
        $classpath=$historyBin+';'+$classpath
    }
    $nonce=[guid]::NewGuid().ToString('N');$stem=Join-Path $Layout.Root ('logs\'+$Command+'-'+$nonce)
    $arguments022=Get-TrainJavaArguments 'org.l2jmobius.tests.phantoms.PhantomLearning022V4Trainer' (@($Command)+$Arguments) $classpath $nonce 2 512
    if($Context){$arguments022=@(Get-Train022JavaFlags @{Context022=$Context})+$arguments022}
    $process=Start-Process -FilePath $java -ArgumentList $arguments022 -WorkingDirectory $Layout.Root -WindowStyle Hidden -PassThru -RedirectStandardOutput ($stem+'.out') -RedirectStandardError ($stem+'.err')
    $held=Hold-ProcessHandle $process
    try{
        if(!$process.WaitForExit(30000)){$process.Kill();$null=$process.WaitForExit(10000);throw 'TRAIN022_OFFLINE_TIMEOUT'}
        $code=Get-TrackedProcessExitCode $process $held
        if($code -ne 0){throw ('TRAIN022_'+$Command.ToUpperInvariant()+'_FAILED')}
        $lines=@([IO.File]::ReadAllLines($stem+'.out')|Where-Object {$_ -match '^\{'})
        if($lines.Count){return $lines[-1]|ConvertFrom-Json}
    }finally{$process.Dispose()}
}
function Get-Train022Status($Layout) {
    if(!(Test-Path -LiteralPath $Layout.Root)){return [ordered]@{schemaVersion=22;trainerStatus='NOT_STARTED';gsBoots=0;productionV4='OFF';nightReadiness='NOT_READY'}}
    Assert-TrainInstallation $Layout
    $state=Read-TrainJson (Join-Path $Layout.Root 'status022.json')
    $head=Join-Path $Layout.Ledger 'HEAD'
    if((Test-Path -LiteralPath $head) -and $state.ledgerHead -cne [IO.File]::ReadAllText($head)){$state.trainerStatus='RECOVERY_REQUIRED'}
    if($state.trainerStatus -cin @('RUNNING','STOPPED','PAUSED','AUTO_STOPPED')){
        $path=Join-Path $Layout.Root 'controller022.json';$live=$false
        if(Test-Path -LiteralPath $path){$record=Read-TrainJson $path;$process=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)";$live=$process -and (Get-G0Microseconds $process.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds ([long]$record.created))}
        if(!$live -and $state.trainerStatus -ceq 'RUNNING'){$state.trainerStatus='RECOVERY_REQUIRED'}
        if($live -and $state.trainerStatus -cin @('STOPPED','PAUSED','AUTO_STOPPED')){$state.trainerStatus='DRAINING'}
    }
    return $state
}
function Save-Train022Status($Layout,$State,[string]$Status,[string]$Reason='') {
    $State.trainerStatus=$Status;$State.reason=$Reason;$State.observedUtc=[datetime]::UtcNow.ToString('o')
    $configPath=Join-Path $Layout.Root 'campaign022.json'
    if((Test-Path -LiteralPath $configPath) -and (Test-Train022Property (Read-TrainJson $configPath) 'qualityPolicySha')){
        Write-Train023Progress $Layout $State
        Write-TrainJson (Join-Path $Layout.Root 'status022.json') $State
        Write-TrainJson (Join-Path $Layout.Root 'report022.json') $State
        return
    }
    Write-TrainJson (Join-Path $Layout.Root 'status022.json') $State
    Write-TrainJson (Join-Path $Layout.Root 'report022.json') $State
    $text=@('# Автономный V4 TEST trainer022',('Статус: '+$Status),('Причина: '+$Reason),('Срок UTC: '+$State.deadlineUtc),('Глобальные updates: '+$State.globalUpdates),('Native boots: '+$State.gsBoots),'SUPERIORITY=NOT_EVALUATED; production V4 OFF.','Kills/deaths описывают эпизод и не доказывают причинный вклад одного действия.',('Подлинные измерения и UNKNOWN: '+(Join-Path $Layout.Root 'rounds')))
    $text+=('Отказы: '+$State.refusedAttempts+'; неполные UNKNOWN эпизоды: '+$State.unknownEpisodes)
    foreach($metric in $State.metrics){
        $text+=('Эпизод '+$metric.round+' / '+$metric.split+': paid='+$metric.paidActions+', HP damage='+$metric.hpDamage+', MP='+$metric.mpCost+', время='+([double]$metric.durationNanos/1e9)+'с, kills='+$metric.kills+', deaths≥'+$metric.deathsLowerBound+'. Полное число смертей: UNKNOWN.')
        $text+=('Оплачено experiment='+$metric.experimentPaid+', stock='+$metric.stockPaid+'; MODEL_REORDERED='+$metric.MODEL_REORDERED+'.')
        foreach($group in $metric.groups){$text+=($group.modeSkill+': n='+$group.n+', meanHP='+$group.meanHp+', sample varianceHP='+$(if($null -eq $group.sampleVarianceHp){'UNKNOWN'}else{$group.sampleVarianceHp})+', meanMP='+$group.meanMp)}
    }
    $text+='Исторический 021: 188 updates, experiment1/control8; причинное сравнение UNKNOWN. 480 минут в Goal022 не запускались.'
    Write-TrainText (Join-Path $Layout.Root 'report022.md') ($text -join "`r`n")
}
function Get-Train022Context($Layout,$Reservation) {
    $context=@{Root=(Join-Path $Layout.Root ('rounds\round'+([int]$Reservation.round).ToString('D6')));History=$Layout.History;BuildRoot=$Layout.BuildRoot;Round=[int]$Reservation.round;Cursor=$(if(Test-Train022Property $Reservation 'cursor'){[long]$Reservation.cursor}else{[long]$Reservation.round});ModelSha=$Reservation.modelSha;LocalUpdates=[long]$Reservation.localUpdates;Split=$Reservation.split;Head=$Reservation.head;BootsBefore=[int]$Reservation.bootsBefore;Step=[long]$Reservation.step;ConfigSha=$Reservation.configSha}
    if((Test-Train022Property $Reservation 'batchSchema') -and $Reservation.batchSchema -ceq 'NATIVE_BATCH024_V1'){$context.Batch024=$true;$context.Layout024=$Layout}
    return $context
}
function Stop-Train022Children($Context) {
    if(!(Test-Path -LiteralPath $Context.Root)){Assert-TrainPortsFree @(33364,18024,19324);return}
    Assert-TrainInstallation @{Root=$Context.Root};Initialize-TrainWin32
    if(Test-Path -LiteralPath (Join-Path $Context.Root 'safety020.json')){
        # Original cleanup precedes FIT: its preservation snapshot can include the then-current HEAD.
        # A committed campaign HEAD is intentionally mutable; verified closed episodes need no second drain.
        Assert-Train022Closed $Context
        return
    }
    if((Test-Path -LiteralPath (Join-Path $Context.Root 'boot-reserved020.json')) -and !(Test-Path -LiteralPath (Join-Path $Context.Root 'native-process.json'))){throw 'TRAIN022_NATIVE_IDENTITY_UNCONFIRMED'}
    if((Test-Path -LiteralPath (Join-Path $Context.Root 'sql')) -and !(Test-Path -LiteralPath (Join-Path $Context.Root 'sql\owned-process.json')) -and @(Get-ChildItem -LiteralPath (Join-Path $Context.Root 'sql') -Filter 'owned-server.*.ini' -File).Count){throw 'TRAIN022_SQL_IDENTITY_UNCONFIRMED'}
    Write-TrainText (Join-Path $Context.Root 'stop.request') 'RECOVERY022_DRAIN_NO_NEW_NATIVE'
    $facts=@()
    foreach($entry in @(@{path='native-process.json';kind='Native'},@{path='sql\owned-process.json';kind='Sql'})){
        $path=Join-Path $Context.Root $entry.path
        if(!(Test-Path -LiteralPath $path)){continue}
        $record=Read-TrainJson $path;$actual=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
        $same=$actual -and (Get-G0Microseconds $actual.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds ([long]$record.created))
        if($same){
            Assert-TrainProcess $record $entry.kind
            if($entry.kind -ceq 'Sql'){
                $bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
                Stop-TrainSql @{Root=$Context.Root;Sql=(Join-Path $Context.Root 'sql');Admin=(Join-Path $bin 'mariadb-admin.exe')} @{sqlPort='33364';shutdownTimeoutSeconds='30'} $record -ReadOnlyObservation
            }else{
                $process=Get-Process -Id $record.pid -ErrorAction Stop;$held=Hold-ProcessHandle $process
                try{
                    Assert-TrainProcess $record Native
                    if(!$process.WaitForExit(30000)){Assert-TrainProcess $record Native;$process.Kill();if(!$process.WaitForExit(10000)){throw 'TRAIN022_STOP_UNCONFIRMED'}}
                }finally{$process.Dispose()}
            }
        }
        $now=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
        if($now -and (Get-G0Microseconds $now.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds ([long]$record.created))){throw 'TRAIN022_STOP_UNCONFIRMED'}
        $facts+=@{kind=$entry.kind;pid=$record.pid;created=$record.created;absent=$true}
    }
    Assert-TrainPortsFree @(33364,18024,19324)
    if(Test-Path -LiteralPath (Join-Path $Context.Root 'preservation-before.json')){
        $pins=Read-TrainJson (Join-Path $Context.Root 'preservation-before.json')
        foreach($pin in $pins.PSObject.Properties){Assert-TrainHash $pin.Name $pin.Value}
    }
    if(!(Test-Path -LiteralPath (Join-Path $Context.Root 'recovery-stop022.json'))){Write-Train022Immutable (Join-Path $Context.Root 'recovery-stop022.json') @{test='OFF';ownedProcesses=0;portsFree=$true;processes=$facts;nativeCreditCreated=$false;checkedUtc=[datetime]::UtcNow.ToString('o')}}
}
function Get-Train022Metrics($Context) {
    if($Context.ContainsKey('Batch024') -and $Context.Batch024){return Get-Train024Metrics $Context}
    $proof=Read-TrainJson (Join-Path $Context.Root 'native020.json');$episode=Read-TrainJson (Join-Path $Context.Root 'episode020.json');$preflight=Read-TrainJson (Join-Path $Context.Root 'preflight020.json')
    $groups=@()
    foreach($group in @($proof.records|Group-Object { $_.mode+'/'+$_.chosen+':'+$_.chosenLevel })){
        $rows=@($group.Group);$n=$rows.Count;$hp=($rows|Measure-Object paidHp -Average).Average;$mp=($rows|Measure-Object paidMp -Average).Average;$elapsed=($rows|Measure-Object elapsedNanos -Average).Average
        $variance=$null;if($n -gt 1){$sum=0.0;foreach($row in $rows){$sum+=([double]$row.paidHp-$hp)*([double]$row.paidHp-$hp)};$variance=$sum/($n-1)}
        $groups+=@{modeSkill=$group.Name;n=$n;meanHp=$hp;sampleVarianceHp=$variance;meanMp=$mp;meanElapsedNanos=$elapsed;causalAdvantage='UNKNOWN'}
    }
    $manifest=Read-TrainJson (Join-Path $Context.Root 'manifest021.json')
    return @{round=$Context.Round;split=$Context.Split;nativeProofSha=(Get-FileHash -LiteralPath (Join-Path $Context.Root 'native020.json')).Hash.ToLowerInvariant();episodeSha=$proof.episodeSha;sourceSha=$preflight.candidates[0].sourceSha;
        paidActions=$proof.nativePaid;trainCredit=$(if($Context.Split -ceq 'TRAIN'){$proof.nativePaid}else{0});experimentPaid=$proof.experimentalPaid;stockPaid=$proof.baselinePaid;MODEL_REORDERED=$proof.MODEL_REORDERED;EXPERIMENT_REORDERED=$proof.EXPERIMENT_REORDERED;
        hpDamage=$episode.exactObservedDamage;mpCost=$episode.exactObservedMp;durationNanos=$episode.durationNanos;kills=@($episode.nativeKills|Where-Object {$null -ne $_}).Count;deathsLowerBound=$episode.observedDeathsLowerBound;completeDeaths=$episode.completeDeathCount;outcome=$episode.outcome;reason=$episode.reason;
        unknownOrUnpaidDecisions=([int]$manifest.decisionCount-[int]$proof.nativePaid);coverage='SINGLE_LAWFUL_CLASS94_SOURCE';groups=$groups;perAction=@($proof.records);superiority='NOT_EVALUATED'}
}
function Complete-Train022Round($Layout,$Context,$State) {
    $preparedPath=Join-Path $Context.Root 'prepared022.json'
    $prepared=if(Test-Path -LiteralPath $preparedPath){Read-TrainJson $preparedPath}else{Invoke-Train022Java $Layout 'prepare' @($Layout.Ledger,$Context.Head,$Context.Root) $Context}
    if(!(Test-Path -LiteralPath $preparedPath)){Write-Train022Immutable $preparedPath $prepared}
    $null=Invoke-Train022Java $Layout 'verify-prepared' @($Layout.Ledger,$Context.Head,$Context.Root,$prepared.head) $Context
    $null=Invoke-Train022Java $Layout 'cold' @($Layout.Ledger,$prepared.head)
    $committed=Invoke-Train022Java $Layout 'commit' @($Layout.Ledger,$Context.Head,$prepared.head)
    $State.ledgerHead=$committed.head;$State.modelSha=$committed.modelSha;$State.globalUpdates=$committed.globalUpdates;$State.localUpdates=$committed.localUpdates;$State.segment=$committed.segment
    if(!(Test-Path -LiteralPath (Join-Path $Context.Root 'commit022.json'))){Write-Train022Immutable (Join-Path $Context.Root 'commit022.json') $committed}
    if(!@($State.metrics|Where-Object {$_.round -eq $Context.Round}).Count){$metric=Get-Train022Metrics $Context;$metric.parentSha=$Context.ModelSha;$metric.candidateSha=$committed.modelSha;$metric.ledgerHead=$committed.head;$State.metrics=@($State.metrics)+@($metric)}
    $null=Assert-Train023Admission $Layout
}
function Sync-Train022CommittedState($State,$Head) {
    $State.ledgerHead=$Head.head;$State.modelSha=$Head.modelSha;$State.globalUpdates=$Head.globalUpdates;$State.localUpdates=$Head.localUpdates;$State.segment=$Head.segment
    $count=0;foreach($metric in $State.metrics){$count+=if(Test-Train022Property $metric 'episodeCount'){[int]$metric.episodeCount}else{1}};$State.completedEpisodes=$count
}
function Restore-Train022CommittedMetric($Layout,$Context,$State) {
    if(@($State.metrics|Where-Object {$_.round -eq $Context.Round}).Count){return}
    # HEAD/commit can survive a process death before status/report publication.
    # Authenticate the original source again; replay must add no credit.
    $replay=Invoke-Train022Java $Layout 'replay' @($Layout.Ledger,$Context.Root) $Context
    if($replay.replayAdditionalUpdates -ne 0 -or $replay.localReplayAdditionalUpdates -ne 0){throw 'TRAIN022_RECOVERY_REPLAY_CREDIT'}
    $commit=Read-TrainJson (Join-Path $Context.Root 'commit022.json')
    $metric=Get-Train022Metrics $Context;$metric.parentSha=$Context.ModelSha;$metric.candidateSha=$commit.modelSha;$metric.ledgerHead=$commit.head
    $State.metrics=@($State.metrics)+@($metric)
}
function Start-Train022Broker($Layout) {
    $nonce=[guid]::NewGuid().ToString('N')
    $source=Join-Path $PSScriptRoot 'TrainingV4.ps1'
    $command='"'+$Layout.PowerShell+'" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$source+'" -Controller022 -HistoryRoot022 "'+$Layout.History+'" -Nonce022 '+$nonce
    $environment=[Collections.Generic.List[string]]::new()
    foreach($name in @('SystemRoot','WINDIR','SystemDrive','ComSpec','OS','ProgramFiles','ProgramFiles(x86)','ProgramW6432','PATH','PATHEXT','JAVA_HOME','TEMP','TMP','USERPROFILE','LOCALAPPDATA','APPDATA','USERNAME','USERDOMAIN','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE')){$value=[Environment]::GetEnvironmentVariable($name,'Process');if($null -ne $value){$environment.Add($name+'='+$value)}}
    $environment.Add('PSModulePath='+(Join-Path (Split-Path $Layout.PowerShell -Parent) 'Modules'))
    $startup=New-CimInstance -CimClass (Get-CimClass -ClassName Win32_ProcessStartup -Namespace root/cimv2) -ClientOnly -Property @{CreateFlags=[uint32]16777216;ShowWindow=[uint16]0;EnvironmentVariables=[string[]]$environment.ToArray()}
    $created=Invoke-CimMethod -ClassName Win32_Process -Namespace root/cimv2 -MethodName Create -Arguments @{CommandLine=$command;CurrentDirectory=$Layout.Root;ProcessStartupInformation=$startup}
    if($created.ReturnValue -ne 0 -or $created.ProcessId -le 0){throw 'TRAIN022_BROKER_CREATE_FAILED'}
    $wait=[datetime]::UtcNow.AddSeconds(15)
    do{
        if(Test-Path -LiteralPath (Join-Path $Layout.Root 'controller022.json')){
            $record=Read-TrainJson (Join-Path $Layout.Root 'controller022.json')
            if($record.nonce -ceq $nonce){Assert-TrainProcess $record Director;return}
        }
        Start-Sleep -Milliseconds 100
    }while([datetime]::UtcNow -lt $wait)
    throw 'TRAIN022_DIRECTOR_NOT_CONFIRMED'
}
function Invoke-Train022Controller($Layout,[string]$Nonce) {
    Initialize-TrainWin32;Assert-TrainInstallation $Layout
    $lock=Enter-TrainLock $Layout;$state=Read-TrainJson (Join-Path $Layout.Root 'status022.json')
    $context=$null
    try{
        if(!('TrainingJobProbe' -as [type])){Add-Type -Path (Join-Path $PSScriptRoot 'TrainingJobProbe.cs')}
        $inside=$false;$self=[Diagnostics.Process]::GetCurrentProcess()
        $queried=[TrainingJobProbe]::IsProcessInJob($self.Handle,[IntPtr]::Zero,[ref]$inside)
        $job=[TrainingJobProbe]::CurrentJob()
        if(!(Test-TrainBrokerAdmission $queried $inside $job)){throw 'TRAIN022_DETACHED_JOB_UNPROVEN'}
        $identity=New-TrainProcessRecord $self $Layout.PowerShell $Nonce Director (Join-Path $PSScriptRoot 'TrainingV4.ps1')
        Write-TrainJson (Join-Path $Layout.Root 'controller022.json') $identity
        Write-Train022Immutable (Join-Path $Layout.Root ('logs\lifetime-'+$Nonce+'.json')) @{querySucceeded=$queried;inAnyJob=$inside;currentJob=$job;admitted=$true;pid=$identity.pid;created=$identity.created}
        $config=Read-TrainJson (Join-Path $Layout.Root 'campaign022.json');$noData=0
        $state.gsBoots=Get-Train022HistoricalBoots $Layout $config
        Save-Train022Status $Layout $state 'RUNNING'
        # Recovery never starts a replacement native action for an interrupted reservation.
        foreach($path in @(Get-ChildItem -LiteralPath (Join-Path $Layout.Root 'reservations') -File|Sort-Object Name)){
            $reservation=Read-TrainJson $path.FullName;$context=Get-Train022Context $Layout $reservation
            if(Test-Path -LiteralPath (Join-Path $context.Root 'commit022.json')){Restore-Train022CommittedMetric $Layout $context $state;continue}
            if(Test-Path -LiteralPath (Join-Path $context.Root 'aborted022.json')){continue}
            if(Test-Path -LiteralPath (Join-Path $context.Root 'native021.complete.json')){
                Stop-Train022Children $context;Assert-Train022Closed $context
                Complete-Train022Round $Layout $context $state
            }else{
                if(Test-Path -LiteralPath $context.Root){Stop-Train022Children $context;Assert-Train022Closed $context}
                $noData++;if(Test-Path -LiteralPath (Join-Path $context.Root 'cut020.json')){$state.unknownEpisodes++}else{$state.refusedAttempts++}
                if(Test-Path -LiteralPath $context.Root){Write-Train022Immutable (Join-Path $context.Root 'aborted022.json') @{reason='INCOMPLETE_NATIVE_NO_CREDIT';trainingCredit=0}}
            }
        }
        Sync-Train022CommittedState $state (Invoke-Train022Java $Layout 'head' @($Layout.Ledger))
        while($true){
            $command=Get-Train022Command $Layout
            if($command -cin @('Pause','Stop')){Save-Train022Status $Layout $state $(if($command -ceq 'Pause'){'PAUSED'}else{'STOPPED'}) 'OPERATOR';return}
            $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($Layout.Root))
            $reason=Get-Train022StopReason $config ([datetime]::UtcNow) $drive.AvailableFreeSpace $state.gsBoots $noData $state.stagnation
            if($reason){Save-Train022Status $Layout $state 'AUTO_STOPPED' $reason;return}
            $head=Invoke-Train022Java $Layout 'head' @($Layout.Ledger)
            $round=@(Get-ChildItem -LiteralPath (Join-Path $Layout.Root 'reservations') -File).Count
            $quality=Assert-Train023Admission $Layout
            $split=$quality.nextSplit
            $episodeNonce=[guid]::NewGuid().ToString('N');$digest=[Security.Cryptography.SHA256]::Create()
            try{$episodeId=[BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($episodeNonce+'/0'))).Replace('-','').ToLowerInvariant()}finally{$digest.Dispose()}
            $reservation=@{schemaVersion=22;round=$round;cursor=(Get-Train022Cursor $Layout $config $round);episodeId=$episodeId;nonce=$episodeNonce;head=$head.head;modelSha=$head.modelSha;localUpdates=$head.localUpdates;step=[long]$head.sequence;split=$split;bootsBefore=[int]$state.gsBoots;reservedUtc=[datetime]::UtcNow.ToString('o');sourceCatalogSha=$config.sourceCatalogSha;configSha=(Get-FileHash -LiteralPath (Join-Path $Layout.Root 'campaign022.json')).Hash}
            $ready=Join-Path $Layout.Root 'ready022.json'
            if($round -eq 0 -and (Test-Path -LiteralPath $ready)){
                $reservation=Read-TrainJson $ready
                if($reservation.round -ne 0 -or $reservation.head -cne $head.head -or $reservation.modelSha -cne $head.modelSha -or $reservation.localUpdates -ne $head.localUpdates -or $reservation.step -ne $head.sequence -or $reservation.split -cne $split -or $reservation.sourceCatalogSha -cne $config.sourceCatalogSha -or $reservation.bootsBefore -ne $state.gsBoots -or $reservation.cursor -ne (Get-Train022Cursor $Layout $config $round)){throw 'TRAIN022_OFFLINE_READINESS_STALE'}
                Assert-TrainHash (Join-Path $Layout.Root 'campaign022.json') $reservation.configSha
            }
            Write-Train022Immutable (Join-Path $Layout.Root ('reservations\'+$round.ToString('D6')+'.json')) $reservation
            $context=Get-Train022Context $Layout $reservation;$state.activeRound=$round
            Save-Train022Status $Layout $state 'RUNNING'
            try{Invoke-TrainActionValue020 $Layout.History $Layout.Ledger -Autonomous022 $context}
            finally{if(Test-Path -LiteralPath (Join-Path $context.Root 'boot-reserved020.json')){$state.gsBoots++}}
            Assert-Train022Closed $context
            $beforeUpdates=[long]$state.globalUpdates
            Complete-Train022Round $Layout $context $state
            if($split -ceq 'TRAIN' -and [long]$state.globalUpdates -le $beforeUpdates){$state.stagnation++}else{$state.stagnation=0}
            $state.completedEpisodes++;$noData=0
            Save-Train022Status $Layout $state 'RUNNING'
        }
    }catch{
        $state.lastError=$_.Exception.Message
        $command=Get-Train022Command $Layout;$closed=$false
        if($command -cin @('Pause','Stop')){
            try{if($context -and (Test-Path -LiteralPath $context.Root)){Assert-Train022Closed $context}else{Assert-TrainPortsFree @(33364,18024,19324)};$closed=$true}catch{$closed=$false}
        }
        if($state.lastError -like 'TRAIN023_PAUSED_*'){
            Save-Train022Status $Layout $state 'PAUSED' $state.lastError
            return
        }
        elseif($closed){Save-Train022Status $Layout $state $(if($command -ceq 'Pause'){'PAUSED'}else{'STOPPED'}) 'OPERATOR_DRAIN_CONFIRMED'}
        else{Save-Train022Status $Layout $state 'BLOCKED' $state.lastError}
        throw
    }finally{$lock.Dispose()}
}
function Assert-Train022Closed($Context) {
    $safetyPath=Join-Path $Context.Root 'safety020.json'
    if(!(Test-Path -LiteralPath $safetyPath)){$safetyPath=Join-Path $Context.Root 'recovery-stop022.json'}
    if(!(Test-Path -LiteralPath $safetyPath)){throw 'TRAIN022_STOP_UNCONFIRMED'}
    $safety=Read-TrainJson $safetyPath
    if($safety.test -cne 'OFF' -or $safety.ownedProcesses -ne 0 -or !$safety.portsFree){throw 'TRAIN022_STOP_UNCONFIRMED'}
    Assert-TrainPortsFree @(33364,18024,19324)
    foreach($file in @('native-process.json','sql\owned-process.json')){
        if(Test-Path -LiteralPath (Join-Path $Context.Root $file)){
            $record=Read-TrainJson (Join-Path $Context.Root $file)
            if(Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"|Where-Object {(Get-G0Microseconds $_.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds ([long]$record.created))}){throw 'TRAIN022_OWNED_PROCESS_REMAINS'}
        }
    }
}
function Invoke-TrainActionValue022([string]$HistoryRoot,[ValidateSet('Start','Status','Pause','Resume','Stop','Report','Batch024Prepare','Batch024Run','Batch024Recover','Batch024Report')][string]$Command,[int]$Minutes=480,[int]$BootLimit=0,[switch]$PrepareOnly,[switch]$NewCampaign) {
    if($Command.StartsWith('Batch024',[StringComparison]::Ordinal)){
        if($PrepareOnly -or $NewCampaign){throw 'TRAIN024_EXPLICIT_COMMAND_ONLY'}
        Invoke-TrainMiniBatch024 $HistoryRoot $Command.Substring(8)|ConvertTo-Json -Depth 16;return
    }
    if(($PrepareOnly -or $NewCampaign) -and $Command -cne 'Start'){throw 'TRAIN022_NEW_OPTIONS_START_ONLY'}
    $layout=Get-Train022Layout $HistoryRoot
    if($Command -cin @('Status','Report')){
        $status=Get-Train022Status $layout
        $configPath=Join-Path $layout.Root 'campaign022.json'
        if((Test-Path -LiteralPath $configPath) -and (Test-Train022Property (Read-TrainJson $configPath) 'qualityPolicySha')){Write-Train023Progress $layout $status;Write-TrainJson (Join-Path $layout.Root 'report022.json') $status}
        $status|ConvertTo-Json -Depth 16;return
    }
    Initialize-TrainWin32
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$key=[BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($layout.BaseRoot))).Replace('-','')}finally{$hash.Dispose()}
    $mutex=[Threading.Mutex]::new($false,('Local\Phantom022-'+$key));$held=$false
    try{
        $held=$mutex.WaitOne(15000);if(!$held){throw 'TRAIN022_START_LOCK_TIMEOUT'}
        $layout=Get-Train022Layout $HistoryRoot
        $qualityConfig=Join-Path $layout.Root 'campaign022.json'
        if($Command -cin @('Start','Resume') -and (Test-Path -LiteralPath $qualityConfig) -and (Test-Train022Property (Read-TrainJson $qualityConfig) 'qualityPolicySha')){
            $null=Assert-Train023Admission $layout
        }
        if($NewCampaign -and !(Test-Path -LiteralPath (Join-Path $layout.Root 'status022.json'))){throw 'TRAIN022_NEW_SOURCE_REQUIRED'}
        if($Command -ceq 'Start'){
            if($Minutes -lt 1 -or $Minutes -gt 480){throw 'TRAIN022_DURATION_1_480'}
            $maximumBoots=if($Minutes -eq 480){96}else{2}
            if($BootLimit -lt 0 -or $BootLimit -gt $maximumBoots){throw 'TRAIN022_BOOT_LIMIT_SCOPE'}
            if(Test-Path -LiteralPath (Join-Path $layout.Root 'status022.json')){
                $startStatus=Get-Train022Status $layout
                if($startStatus.trainerStatus -cne 'RUNNING'){Assert-Train022NoController $layout}
                if($startStatus.trainerStatus -cnotin @('STOPPED','AUTO_STOPPED','NOT_STARTED','RUNNING')){throw 'TRAIN022_SOURCE_NOT_STOPPED'}
            }
        }
        $bootstrapIncomplete=$Command -ceq 'Start' -and (Test-Path -LiteralPath $layout.Root) -and !(Test-Path -LiteralPath (Join-Path $layout.Root 'status022.json'))
        if($bootstrapIncomplete){
            throw 'TRAIN022_INCOMPLETE_BOOTSTRAP_UNCONFIRMED'
        }
        if($Command -ceq 'Start' -and (Test-Path -LiteralPath $layout.Root) -and ($bootstrapIncomplete -or (Get-Train022Status $layout).trainerStatus -cin @('STOPPED','AUTO_STOPPED'))){
            foreach($path in @(Get-ChildItem -LiteralPath (Join-Path $layout.Root 'reservations') -File)){$old=Get-Train022Context $layout (Read-TrainJson $path.FullName);if(Test-Path -LiteralPath $old.Root){Assert-Train022Closed $old}}
            Assert-Train022NoController $layout
            $layout.PreviousLedger=$layout.Ledger;$layout.PreviousRoot=$layout.Root;$layout.NewCampaign=[guid]::NewGuid().ToString('N')
            $authorityPath=Join-Path $layout.BaseRoot 'continuation022-v2.json'
            $claimPath=Join-Path $layout.BaseRoot 'continuation022-v2.claim.json'
            if($NewCampaign){
                $layout.Independent=Get-Train022IndependentSource $layout
                $admission=Join-Path $layout.BaseRoot ('admission-'+$layout.Independent.campaignId+'-'+$layout.Independent.head.head+'.json')
                if(Test-Path -LiteralPath $admission){throw 'TRAIN022_ADMISSION_INCOMPLETE'}
                Write-Train022Immutable $admission @{campaignId=$layout.NewCampaign;source=$layout.Independent.campaignId;head=$layout.Independent.head.head}
            }elseif(Test-Path -LiteralPath $claimPath){throw 'TRAIN022_NEW_CAMPAIGN_REQUIRED'}
            elseif(Test-Path -LiteralPath $authorityPath){
                $authority=Read-TrainJson $authorityPath;Assert-Train022ContinuationContract $authority
                if($Minutes -ne 15 -or $BootLimit -ne 1 -or (Split-Path $layout.Root -Leaf) -cne $authority.previousCampaign){throw 'TRAIN022_CONTINUATION_PREVIOUS_CAMPAIGN'}
                Assert-TrainHash (Join-Path $layout.Root 'campaign022.json') $authority.previousConfigSha
                Assert-TrainHash (Join-Path $layout.Ledger ('candidate-'+$authority.parentSha+'.bin')) $authority.parentSha
                if([IO.File]::ReadAllText((Join-Path $layout.Ledger 'HEAD')) -cne $authority.previousHead){throw 'TRAIN022_CONTINUATION_HEAD_CHANGED'}
                $previous=Read-TrainJson (Join-Path $layout.Root 'rounds\round000000\preflight020.json')
                if($previous.candidates[0].nextCursor022 -ne $authority.cursor){throw 'TRAIN022_CONTINUATION_CURSOR_CHANGED'}
                $layout.Continuation=$authority;$layout.ContinuationSha=(Get-FileHash -LiteralPath $authorityPath).Hash
                $probe=[pscustomobject]@{continuationSha=$layout.ContinuationSha};$null=Get-Train022HistoricalBoots $layout $probe
                Write-Train022Immutable (Join-Path $layout.BaseRoot 'continuation022-v2.claim.json') @{campaignId=$layout.NewCampaign;authoritySha=$layout.ContinuationSha}
            }
            $layout.Root=Join-Path $layout.BaseRoot ('campaigns\'+$layout.NewCampaign);$layout.Ledger=Join-Path $layout.Root 'ledger'
        }
        if(!(Test-Path -LiteralPath $layout.Root)){
            if($Command -cne 'Start'){Get-Train022Status $layout|ConvertTo-Json;return}
            if($Minutes -lt 1 -or $Minutes -gt 480){throw 'TRAIN022_DURATION_1_480'}
            $maximumBoots=if($Minutes -eq 480){96}else{2}
            if($BootLimit -lt 0 -or $BootLimit -gt $maximumBoots){throw 'TRAIN022_BOOT_LIMIT_SCOPE'}
            if(!$layout.ContainsKey('Independent')){
                $accepted=Join-Path (Split-Path $layout.History -Parent) 'train021-c1'
                if(!(Test-TrainActionValue020Complete $accepted -Experiment021)){throw 'TRAIN022_ACCEPTED188_PROOF_REQUIRED'}
                $parent=Join-Path $accepted 'candidate021-90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6.bin'
                Assert-TrainHash $parent '90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6'
            }
            Assert-TrainPortsFree @(33364,18024,19324)
            New-TrainPrivateRoot $layout
            foreach($name in @('logs','ledger','rounds','reservations')){$null=[IO.Directory]::CreateDirectory((Join-Path $layout.Root $name))}
            $now=[datetime]::UtcNow;$sources=@{}
            foreach($path in @((Join-Path $PSScriptRoot 'TrainingV4.ps1'),(Join-Path $PSScriptRoot 'TrainingQualityV4.ps1'),(Join-Path $PSScriptRoot 'TrainingOwner.ps1'),(Join-Path $layout.BuildRoot 'runtime-libs\GameServer.jar'))){$sources[$path]=(Get-FileHash -LiteralPath $path).Hash}
            $historyVerifier=Join-Path $layout.Module '.phantom-local\learning023-build\phantom-test\bin\org\l2jmobius\tests\phantoms\PhantomLearning022V4Trainer.class'
            Assert-G0PhysicalFile $historyVerifier;$sources[$historyVerifier]=(Get-FileHash -LiteralPath $historyVerifier).Hash
            $approvalPath=Join-Path $layout.BaseRoot 'approved-protected022.json';if(Test-Path -LiteralPath $approvalPath){$sources[$approvalPath]=(Get-FileHash -LiteralPath $approvalPath).Hash}
            $testBin=Join-Path $layout.BuildRoot 'phantom-test\bin\org\l2jmobius\tests\phantoms'
            foreach($prefix in @('PhantomLearning020V4Native','PhantomLearning022V4Trainer','PhantomAutonomousTrainingNative','PhantomLearning001ShadowNative','PhantomTrainingActors')){foreach($file in @(Get-ChildItem -LiteralPath $testBin -Filter ($prefix+'*.class') -File)){$sources[$file.FullName]=(Get-FileHash -LiteralPath $file.FullName).Hash}}
            $catalog=Join-Path $layout.History 'autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001\cohort016.json'
            $config=@{schemaVersion=22;campaignId=$(if($layout.ContainsKey('NewCampaign')){$layout.NewCampaign}else{[guid]::NewGuid().ToString('N')});startedUtc=$now.ToString('o');deadlineUtc=$now.AddMinutes($Minutes).ToString('o');durationMinutes=$Minutes;maxBoots=$(if($BootLimit -gt 0){$BootLimit}else{$maximumBoots});maxNoData=3;maxStagnation=3;minDiskBytes=8GB;maxMemoryBytes=6GB;maxCpuCores=2.1;cpuPolicy='POST_GS_READY_10_SECOND_DELTA';sources=$sources;sourceCatalogSha=(Get-FileHash -LiteralPath $catalog).Hash;productionV4='OFF'}
            if($layout.ContainsKey('Continuation')){$config.continuationSha=$layout.ContinuationSha;$config.maxBoots=3;$config.cursorStart=$layout.Continuation.cursor;$config.previousCampaign=$layout.Continuation.previousCampaign}
            if($layout.ContainsKey('Independent')){
                $source=$layout.Independent
                $config.transition='INDEPENDENT_TEST022_V1';$config.previousCampaign=$source.campaignId;$config.previousHead=$source.head.head;$config.previousModelSha=$source.head.modelSha
                $config.cursorStart=$source.cursor;$config.cursorPreflightSha=$source.preflightSha;$config.previousConfigSha=$source.configSha
                $config.historicalV2Boots=3;$config.consumedAuthoritySha=$source.authoritySha;$config.consumedClaimSha=$source.claimSha
                $config.prepareOnly=[bool]$PrepareOnly
                $config.qualityStartSequence=$source.head.sequence
            }
            if(!$layout.ContainsKey('Independent')){throw 'TRAIN023_REFERENCE216_NEW_CAMPAIGN_REQUIRED'}
            Write-Train022Immutable (Join-Path $layout.Root 'quality-policy023.json') (New-Train023Policy $layout $config)
            $config.qualityPolicySha=(Get-FileHash -LiteralPath (Join-Path $layout.Root 'quality-policy023.json')).Hash
            Write-Train022Immutable (Join-Path $layout.Root 'campaign022.json') $config
            if($layout.ContainsKey('PreviousLedger')){
                $ledgerPins=@{}
                foreach($file in @(Get-ChildItem -LiteralPath $layout.PreviousLedger -File|Where-Object {$_.Name -ceq 'HEAD' -or $_.Name -cmatch '^(candidate|ledger|cold)-[a-f0-9]{64}\.bin$|^prepared-[a-f0-9]{64}\.seal$'})){
                    Assert-G0PhysicalFile $file.FullName;$sha=(Get-FileHash -LiteralPath $file.FullName).Hash;$target=Join-Path $layout.Ledger $file.Name
                    if(Test-Path -LiteralPath $target){throw 'TRAIN022_LEDGER_COPY_NOT_FRESH'}
                    Copy-Item -LiteralPath $file.FullName -Destination $target;Assert-TrainHash $target $sha
                    $ledgerPins[$file.Name]=$sha
                }
                $head=Invoke-Train022Java $layout 'head' @($layout.Ledger)
                if($layout.ContainsKey('Independent')){
                    $source=$layout.Independent;$context=$source.context
                    foreach($name in @('head','modelSha','globalUpdates','localUpdates','segment','sequence','globalKeys')){if($head.$name -cne $source.head.$name){throw 'TRAIN022_COPIED_HEAD_CHANGED'}}
                    # Canonical load validates every historical segment, model and key.
                    # commit-to-self checks an existing cold seal without generating one.
                    $null=Invoke-Train022Java $layout 'commit' @($layout.Ledger,$head.head,$head.head)
                    $null=Invoke-Train022Java $layout 'verify-prepared' @($layout.Ledger,$context.Head,$context.Root,$head.head) $context
                    $cohort=Join-Path $layout.History 'autonomous\v1\campaigns\autonomous009-learning016-g2-v1\cohorts\autonomous009-learning016-g2-v1-c000001'
                    $proposal=Invoke-Train022Java $layout 'proposal' @($layout.Ledger,$cohort,[string]$context.Cursor,[string]$context.Step,([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')))
                    $recorded=(Read-TrainJson (Join-Path $context.Root 'preflight020.json')).candidates[0]
                    if($proposal.nextCursor -ne $source.cursor -or $proposal.cell -cne $recorded.cell -or $proposal.sourceSha -cne $recorded.sourceSha){throw 'TRAIN022_CURSOR_SOURCE_CHANGED'}
                    foreach($name in $ledgerPins.Keys){Assert-TrainHash (Join-Path $layout.PreviousLedger $name) $ledgerPins[$name];Assert-TrainHash (Join-Path $layout.Ledger $name) $ledgerPins[$name]}
                    Assert-TrainHash (Join-Path $layout.PreviousRoot 'campaign022.json') $source.configSha
                    Assert-TrainHash (Join-Path $context.Root 'preflight020.json') $source.preflightSha
                    foreach($pin in $config.sources.Keys){Assert-TrainHash $pin $config.sources[$pin]}
                    Write-Train022Immutable (Join-Path $layout.Root 'lineage022.json') @{schema='INDEPENDENT_TEST022_V1';previousCampaign=$source.campaignId;previousHead=$head.head;previousModelSha=$head.modelSha;cursorStart=$source.cursor;step=$head.sequence;ledgerPins=$ledgerPins;historicalV2Boots=3;GS=0;SQL=0}
                }
            }else{$head=Invoke-Train022Java $layout 'initialize' @($layout.Ledger,$parent)}
            $state=@{schemaVersion=22;trainerStatus='NOT_STARTED';reason='';observedUtc=$now.ToString('o');deadlineUtc=$config.deadlineUtc;ledgerHead=$head.head;modelSha=$head.modelSha;globalUpdates=$head.globalUpdates;localUpdates=$head.localUpdates;segment=$head.segment;gsBoots=0;completedEpisodes=0;unknownEpisodes=0;refusedAttempts=0;stagnation=0;activeRound=-1;lastError='';productionV4='OFF';nightReadiness='NOT_READY';SUPERIORITY='NOT_EVALUATED';metrics=@();manualGate='REQUIRED';historicalBaseline=@{updates=188;experimentPaid=1;stockPaid=8;hpDamage=6786;mpCost=625;deathsLowerBound=1;comparison='HISTORICAL_NOT_CAUSAL'}}
            if($layout.ContainsKey('Continuation')){$state.gsBoots=2}
            Save-Train022Status $layout $state 'NOT_STARTED'
            if($layout.ContainsKey('NewCampaign')){Write-TrainJson (Join-Path $layout.BaseRoot 'active022.json') @{campaignId=$layout.NewCampaign;parentHead=$head.head}}
        }
        $live=$false;$identityPath=Join-Path $layout.Root 'controller022.json'
        if(Test-Path -LiteralPath $identityPath){$identity=Read-TrainJson $identityPath;$actual=Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.pid)";if($actual -and (Get-G0Microseconds $actual.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds ([long]$identity.created))){Assert-TrainProcess $identity Director;$live=$true}}
        if($Command -cin @('Pause','Stop','Resume')){
            $config=Read-TrainJson (Join-Path $layout.Root 'campaign022.json')
            if($Command -ceq 'Resume'){
                $drive=[IO.DriveInfo]::new([IO.Path]::GetPathRoot($layout.Root))
                $reason=Get-Train022StopReason $config ([datetime]::UtcNow) $drive.AvailableFreeSpace (Get-Train022HistoricalBoots $layout $config) 0
                if($reason){throw ('TRAIN022_RESUME_'+$reason)}
            }
            Write-TrainJson (Join-Path $layout.Root 'command022.json') @{campaignId=$config.campaignId;kind=$Command;requestedUtc=[datetime]::UtcNow.ToString('o')}
            $state=Get-Train022Status $layout
            if($Command -cin @('Pause','Stop')){
                foreach($round in @(Get-ChildItem -LiteralPath (Join-Path $layout.Root 'rounds') -Directory)){
                    if(!(Test-Path -LiteralPath (Join-Path $round.FullName 'safety020.json'))){Write-TrainText (Join-Path $round.FullName 'stop.request') ('OPERATOR022_'+$Command)}
                }
                Save-Train022Status $layout $state ($Command.ToUpperInvariant()+'_REQUESTED') 'DRAIN_REQUIRED'
                if(!$live){
                    foreach($path in @(Get-ChildItem -LiteralPath (Join-Path $layout.Root 'reservations') -File)){$context=Get-Train022Context $layout (Read-TrainJson $path.FullName);Stop-Train022Children $context;if(Test-Path -LiteralPath $context.Root){Assert-Train022Closed $context}}
                    Save-Train022Status $layout $state $(if($Command -ceq 'Pause'){'PAUSED'}else{'STOPPED'}) 'OFF_CONFIRMED'
                }
                Get-Train022Status $layout|ConvertTo-Json -Depth 16;return
            }
        }
        if($PrepareOnly){if($Command -cne 'Start'){throw 'TRAIN022_PREPARE_ONLY_START'};Get-Train022Status $layout|ConvertTo-Json -Depth 16;return}
        if(!$live -and ($Command -ceq 'Resume' -or (Get-Train022Status $layout).trainerStatus -ceq 'NOT_STARTED')){$null=Assert-Train023Admission $layout;Start-Train022Broker $layout}
        Get-Train022Status $layout|ConvertTo-Json -Depth 16
    }finally{if($held){$mutex.ReleaseMutex()};$mutex.Dispose()}
}
if($Controller022){
    $ErrorActionPreference='Stop';Set-StrictMode -Version Latest
    . (Join-Path $PSScriptRoot 'TrainingOwner.ps1');. (Join-Path $PSScriptRoot 'TrainingBroker.ps1')
    $layout022=Get-Train022Layout $HistoryRoot022
    Invoke-Train022Controller $layout022 $Nonce022
}
