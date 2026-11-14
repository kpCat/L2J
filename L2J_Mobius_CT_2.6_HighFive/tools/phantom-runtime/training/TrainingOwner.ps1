# Shared owner primitives. Dot-sourcing this file does not launch any process.
. (Join-Path $PSScriptRoot '..\G0SqlIdentity.ps1')
$script:TrainUtf8=[Text.UTF8Encoding]::new($false)
$script:TrainGeoSha='A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5'
$script:TrainProtectedSha='9777AB48BC8D14BDEE77D4ACA8E7E650CB099F03CA33FEA108D5CDB941C59DCD'
$script:TrainProtected005Sha='A523F7FFE87682D2C1BBB0D23F40CAD8DB9634F7F2BFCED83232C3E6703D754A'
$script:TrainProtected006Sha='246AC12CD718E698B18E6AB2B338622B24D505F75BA492EB1CD348BCA2E335CD'
$script:TrainWin32Initialized=$false
function Hold-ProcessHandle([Diagnostics.Process]$Process) {
    if ($null -eq $Process) { throw 'OWN108R3C_PROCESS_REQUIRED' }
    # Start-Process redirection on Windows PowerShell 5.1 can lose ExitCode unless
    # this Process owns its cached handle before waiting (PowerShell issue 5421).
    $handle = $Process.Handle
    if ($null -eq $handle -or $handle -isnot [IntPtr] -or $handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_PROCESS_HANDLE_UNAVAILABLE' }
    return $handle
}
function Assert-ObservedExitCode([object]$Value) {
    # Do not cast: PowerShell converts a null value to integer zero.
    if ($null -eq $Value -or $Value -isnot [int]) { throw 'OWN108R3C_EXIT_STATUS_UNKNOWN' }
    return $Value
}
function Get-TrackedProcessExitCode([Diagnostics.Process]$Process, [object]$Handle) {
    if ($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero) { throw 'OWN108R3C_EXIT_HANDLE_UNKNOWN' }
    if (!$Process.HasExited) { throw 'OWN108R3C_PROCESS_NOT_EXITED' }
    return Assert-ObservedExitCode $Process.ExitCode
}
function Get-TrainHeldExitProof($Record,[Diagnostics.Process]$Process,[object]$Handle) {
    if($null -eq $Process -or $null -eq $Handle -or $Handle -isnot [IntPtr] -or $Handle -eq [IntPtr]::Zero -or $Handle -ne $Process.Handle -or
       $Process.Id -ne $Record.pid -or $Process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$Record.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
    $exited=$Process.HasExited;$exit=$null
    if($exited){$exit=Get-TrackedProcessExitCode $Process $Handle}
    $terminated=$null;$elapsed=$null
    if($exited){$terminated=$Process.ExitTime.ToUniversalTime();$elapsed=[long][Math]::Ceiling(($terminated-$Process.StartTime.ToUniversalTime()).TotalMilliseconds);if($elapsed -lt 0){throw 'TRAIN010_NATIVE_TIME_UNKNOWN'}}
    return [ordered]@{pid=$Record.pid;created=$Record.created;exited=$exited;exitCode=$exit;terminatedUtc=$(if($exited){$terminated.ToString('o')}else{$null});nativeElapsedMillis=$elapsed}
}
function Test-TrainTerminatedCimRace([Management.Automation.ErrorRecord]$Failure,$Record,[Diagnostics.Process]$Process,[object]$Handle) {
    if($Failure.Exception.GetType().FullName -cne 'Microsoft.Management.Infrastructure.CimException' -or
       $Failure.FullyQualifiedErrorId -cne 'HRESULT 0x80041002,Microsoft.Management.Infrastructure.CimCmdlets.InvokeCimMethodCommand'){return $false}
    return (Get-TrainHeldExitProof $Record $Process $Handle).exited
}
function Get-TrainExitReceiptPath($Layout,$Record) {
    if([long]$Record.pid -le 0 -or [long]$Record.created -le 0 -or $Record.nonce -cnotmatch '^[0-9a-f]{32}$'){throw 'TRAIN002_EXIT_RECEIPT_IDENTITY'}
    switch -CaseSensitive ($Record.kind){
        'Worker' {return Join-Path $Layout.Root ('workers\'+$Record.nonce+'\exit.json')}
        'Native' {return Join-Path $Layout.Root ('attempts\'+$Record.nonce+'\exit.json')}
        'Sql' {return Join-Path $Layout.Sql ('exit.'+$Record.created+'.json')}
        'Director' {return Join-Path $Layout.Root ('commands\director-exit.'+$Record.created+'.json')}
        default {throw 'TRAIN002_EXIT_RECEIPT_KIND'}
    }
}
function Write-TrainExitReceipt($Layout,$Record,[Diagnostics.Process]$Process,[object]$Handle,[string]$Class='') {
    Assert-TrainInstallation $Layout
    $held=Get-TrainHeldExitProof $Record $Process $Handle
    if(!$held.exited){throw 'TRAIN002_EXIT_RECEIPT_PROCESS_LIVE'}
    Assert-TrainHash $Record.image $Record.imageHash
    if($Record.configPath){Assert-TrainHash $Record.configPath $Record.configHash}
    $receiptPath=Get-TrainExitReceiptPath $Layout $Record
    if(Test-Path -LiteralPath $receiptPath){if(!(Test-TrainExitReceipt $Layout $Record)){throw 'TRAIN010_EXIT_RECEIPT_CHANGED'};return}
    $fact=[ordered]@{schemaVersion=1;proof='OWN_HELD_HANDLE_EXIT';installationOwner=[IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt'));exitCode=$held.exitCode;finishedUtc=[DateTime]::UtcNow.ToString('o');terminatedUtc=$held.terminatedUtc;nativeElapsedMillis=$held.nativeElapsedMillis}
    foreach($key in @('pid','created','image','imageHash','sid','nonce','kind','commandHash','configPath','configHash')){$fact[$key]=$Record.$key}
    if($Class){$fact['class']=$Class}
    if($Record.kind -ceq 'Native'){$fact['attemptId']=$Record.nonce}
    Write-TrainJson (Get-TrainExitReceiptPath $Layout $Record) $fact
}
function Test-TrainExitReceipt($Layout,$Record) {
    Assert-TrainInstallation $Layout
    $path=Get-TrainExitReceiptPath $Layout $Record
    if(!(Test-Path -LiteralPath $path)){return $false}
    $fact=Read-TrainJson $path
    foreach($key in @('schemaVersion','proof','installationOwner','exitCode')){if(!$fact.PSObject.Properties[$key]){return $false}}
    if($fact.schemaVersion -ne 1 -or $fact.proof -cne 'OWN_HELD_HANDLE_EXIT' -or $fact.installationOwner -cne [IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt')) -or $fact.exitCode -isnot [int]){return $false}
    foreach($key in @('pid','created','image','imageHash','sid','nonce','kind','commandHash','configPath','configHash')){
        if(!$fact.PSObject.Properties[$key] -or [string]$fact.$key -cne [string]$Record.$key){return $false}
    }
    $hasTime=$null -ne $fact.PSObject.Properties['terminatedUtc'] -or $null -ne $fact.PSObject.Properties['nativeElapsedMillis']
    if($hasTime){
        if($null -eq $fact.PSObject.Properties['terminatedUtc'] -or $null -eq $fact.PSObject.Properties['nativeElapsedMillis'] -or
           ($fact.nativeElapsedMillis -isnot [int] -and $fact.nativeElapsedMillis -isnot [long]) -or [long]$fact.nativeElapsedMillis -lt 0){return $false}
        $terminated=[DateTime]::MinValue
        if(![DateTime]::TryParseExact([string]$fact.terminatedUtc,'o',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind,[ref]$terminated) -or $terminated.Kind -ne [DateTimeKind]::Utc){return $false}
        try{$started=[DateTime]::FromFileTimeUtc([long]$Record.created)}catch{return $false}
        if($terminated -lt $started -or [long]$fact.nativeElapsedMillis -ne [long][Math]::Ceiling(($terminated-$started).TotalMilliseconds)){return $false}
    }
    return $true
}
function Initialize-TrainWin32 {
    if($script:TrainWin32Initialized){return}
    $source=Join-Path $PSScriptRoot '..\Win32SqlIdentity.cs'
    $source=[IO.Path]::GetFullPath($source);Assert-G0PhysicalFile $source
    if (!('Win32SqlIdentity' -as [type])) { Add-Type -Path $source }
    if ([Win32SqlIdentity]::CurrentTokenElevated() -or [Win32SqlIdentity]::CurrentDebugEnabled() -or [Win32SqlIdentity]::CurrentIntegrityRid() -ge 12288) { throw 'TRAIN002_ORDINARY_TOKEN_REQUIRED' }
    $uac=Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System' -Name EnableLUA
    if($uac.EnableLUA -ne 1){throw 'TRAIN002_UAC_REQUIRED'}
    $script:TrainWin32Initialized=$true
}
function Get-TrainLayout([string]$Root) {
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..')).TrimEnd('\')
    $clone=Split-Path $module -Parent
    $expected=Join-Path $clone '.phantom-local\learning002\train'
    if (!$Root) { $Root=$expected }
    if (![IO.Path]::IsPathRooted($Root) -or [IO.Path]::GetFullPath($Root).TrimEnd('\') -ine $expected) { throw 'TRAIN002_EXACT_OWN_ROOT_REQUIRED' }
    $bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
    return @{Module=$module;Clone=$clone;Root=$expected;ExpectedRoot=$expected;Game=(Join-Path $expected 'game');Sql=(Join-Path $expected 'sql');
        ClassPath=((Join-Path $clone 'build\phantom-test\bin')+';'+(Join-Path $expected 'game\libs\*'));
        Daemon=(Join-Path $bin 'mariadbd.exe');Install=(Join-Path $bin 'mariadb-install-db.exe');Admin=(Join-Path $bin 'mariadb-admin.exe');
        PowerShell=(Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe')}
}
function Assert-TrainDirectory([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    if (!$item.PSIsContainer -or $item.FullName -ine [IO.Path]::GetFullPath($Path)) { throw 'TRAIN002_PHYSICAL_DIRECTORY_REQUIRED' }
    for($part=$item;$null -ne $part;$part=$part.Parent) { if($part.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TRAIN002_REPARSE_PATH'} }
}
function Assert-TrainPrivate([string]$Path) {
    Assert-TrainDirectory $Path
    $acl=Get-Acl -LiteralPath $Path
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'TRAIN002_PRIVATE_OWNER_REQUIRED'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'TRAIN002_FOREIGN_ACL'}}
}
function New-TrainPrivateRoot($Layout) {
    if(Test-Path -LiteralPath $Layout.Root){throw 'TRAIN002_FRESH_ROOT_REQUIRED'}
    $parent=Split-Path $Layout.Root -Parent
    $existing=$parent
    while(!(Test-Path -LiteralPath $existing)){ $existing=Split-Path $existing -Parent }
    Assert-TrainDirectory $existing
    $null=[IO.Directory]::CreateDirectory($parent)
    Assert-TrainDirectory $parent
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetOwner($sid);$acl.SetAccessRuleProtection($true,$false)
    foreach($trustee in @($sid.Value,'S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    $null=[IO.Directory]::CreateDirectory($Layout.Root,$acl)
    Assert-TrainPrivate $Layout.Root
    Write-TrainText (Join-Path $Layout.Root 'owner.txt') ('LEARNING002:'+([guid]::NewGuid().ToString('N')))
}
function Repair-TrainEmptyBootstrap($Layout,$Config) {
    # Actual START supplies the pinned Get-TrainLayout result under its initialization
    # mutex. The expected path is kept separate so unit fixtures can exercise the
    # same guard with their own explicitly bounded temporary installation.
    if(!$Layout.ContainsKey('ExpectedRoot') -or ![IO.Path]::IsPathRooted($Layout.Root) -or ![IO.Path]::IsPathRooted($Layout.ExpectedRoot) -or
       [IO.Path]::GetFullPath($Layout.Root).TrimEnd('\') -ine $Layout.ExpectedRoot -or $Layout.Root -ine $Layout.ExpectedRoot){throw 'TRAIN002_BOOTSTRAP_RECOVERY_SCOPE'}
    Assert-TrainPrivate $Layout.Root
    if(@(Get-ChildItem -LiteralPath $Layout.Root -Force).Count -ne 0){throw 'TRAIN002_BOOTSTRAP_RECOVERY_NONEMPTY'}
    Assert-TrainPortsFree @([int]$Config.sqlPort,[int]$Config.gamePort,[int]$Config.loginPort)
    $parent=Split-Path $Layout.ExpectedRoot -Parent;Assert-TrainDirectory $parent
    $archive=Join-Path $parent ((Split-Path $Layout.ExpectedRoot -Leaf)+'.bootstrap-incomplete.'+[guid]::NewGuid().ToString('N'))
    $archive=[IO.Path]::GetFullPath($archive)
    if((Split-Path $archive -Parent) -ine $parent -or !$archive.StartsWith($Layout.ExpectedRoot+'.bootstrap-incomplete.',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $archive)){throw 'TRAIN002_BOOTSTRAP_ARCHIVE_SCOPE'}
    [IO.Directory]::Move($Layout.Root,$archive)
    Assert-TrainPrivate $archive
    if(@(Get-ChildItem -LiteralPath $archive -Force).Count -ne 0){throw 'TRAIN002_BOOTSTRAP_ARCHIVE_CHANGED'}
    return $archive
}
function Write-TrainText([string]$Path,[string]$Text) {
    if(Test-Path -LiteralPath $Path){Assert-G0PhysicalFile $Path}
    Assert-TrainDirectory (Split-Path $Path -Parent)
    $pending=$Path+'.'+[guid]::NewGuid().ToString('N')+'.pending'
    try {
        [IO.File]::WriteAllText($pending,$Text,$script:TrainUtf8)
        if(Test-Path -LiteralPath $Path){[IO.File]::Replace($pending,$Path,[NullString]::Value)}else{[IO.File]::Move($pending,$Path)}
    }finally{if(Test-Path -LiteralPath $pending){Remove-Item -LiteralPath $pending}}
}
function Write-TrainJson([string]$Path,$Value){Write-TrainText $Path ($Value|ConvertTo-Json -Depth 12)}
function Read-TrainJson([string]$Path){Assert-G0PhysicalFile $Path;return ConvertFrom-Json ([IO.File]::ReadAllText($Path))}
function Get-TrainSafeErrorRecord([Management.Automation.ErrorRecord]$Failure) {
    # Deliberately exclude Message, Line, PositionMessage, Definition and arguments:
    # they can contain expanded installer/JDBC credentials at later startup phases.
    $command=$null
    if($Failure.Exception -is [Management.Automation.CommandNotFoundException]){$command=$Failure.Exception.CommandName}
    $scriptName=$null;$line=$null
    if($Failure.InvocationInfo){$scriptName=$Failure.InvocationInfo.ScriptName;$line=$Failure.InvocationInfo.ScriptLineNumber}
    $code=$null
    if($Failure.Exception.Message -cmatch '^(?:TRAIN002|OWN108R3C)_[A-Z0-9_]+(?::[A-Za-z0-9_.-]+)?$'){$code=$Failure.Exception.Message}
    $diagnostic=[ordered]@{event='TRAIN002_SAFE_ERROR';exceptionType=$Failure.Exception.GetType().FullName;code=$code;commandName=$command;scriptName=$scriptName;scriptLineNumber=$line;fullyQualifiedErrorId=$Failure.FullyQualifiedErrorId;pid=$null;nativeError=$null;created=$null}
    foreach($key in @('pid','nativeError','created')){if($Failure.Exception.Data.Contains($key)){$diagnostic[$key]=[long]$Failure.Exception.Data[$key]}}
    return $diagnostic
}
function Write-TrainSafeError([Management.Automation.ErrorRecord]$Failure) {
    [Console]::Error.WriteLine(((Get-TrainSafeErrorRecord $Failure)|ConvertTo-Json -Compress))
}
function Save-TrainFailure($Layout,[Collections.IDictionary]$State,[Management.Automation.ErrorRecord]$Failure,[ValidateSet('PRIMARY','CLEANUP','WORKER','NATIVE_RETRY','CONTROL')][string]$Stage) {
    Assert-TrainInstallation $Layout
    $directory=Join-Path $Layout.Root 'failures';$null=[IO.Directory]::CreateDirectory($directory)
    $record=Get-TrainSafeErrorRecord $Failure
    $record['id']=[guid]::NewGuid().ToString('N');$record['stage']=$Stage;$record['observedUtc']=[DateTime]::UtcNow.ToString('o')
    $record['phase']=$null;$record['runId']=$null;$record['nonce']=$null
    if($State){$record.phase=$State['phase'];$record.runId=$State['runId'];$record.nonce=$State['nonce']}
    Write-TrainJson (Join-Path $directory ($record.id+'.json')) $record
    if($State){
        if($Stage -eq 'PRIMARY' -and !$State['primaryFailureId']){$State['primaryFailureId']=$record.id;$State['primaryFailureCode']=$(if($record.code){$record.code}else{$record.exceptionType});$State['primaryFailurePhase']=$record.phase}
        if($Stage -eq 'CLEANUP'){$State['cleanupFailureId']=$record.id}
    }
    return $record.id
}
function Save-TrainProcessObservation($Record,[Nullable[int]]$NativeError,[string]$Proof,[Nullable[int]]$ExitCode=$null,[string]$CimErrorId='') {
    $fact=[ordered]@{event='TRAIN002_PROCESS_QUERY';pid=$Record.pid;created=$Record.created;kind=$Record.kind;nonce=$Record.nonce;nativeError=$NativeError;cimErrorId=$CimErrorId;proof=$Proof;exitCode=$ExitCode;observedUtc=[DateTime]::UtcNow.ToString('o')}
    # Hidden Windows hosts may have no useful stdout/stderr. Keep a durable fact
    # only on an unusual query/exit transition, never on every heartbeat.
    $layout=Get-TrainLayout
    Assert-TrainInstallation $layout
    $directory=Join-Path $layout.Root 'process-observations';$null=[IO.Directory]::CreateDirectory($directory)
    Write-TrainJson (Join-Path $directory ([guid]::NewGuid().ToString('N')+'.json')) $fact
}
function Read-TrainConfig([string]$Path) {
    Assert-G0PhysicalFile $Path
    $values=@{}
    foreach($line in [IO.File]::ReadAllLines($Path,[Text.Encoding]::UTF8)){
        if(!$line.Trim() -or $line.TrimStart().StartsWith('#')){continue}
        if($line -cnotmatch '^([A-Za-z][A-Za-z0-9]*)=(.*)$' -or $values.ContainsKey($Matches[1])){throw 'TRAIN002_CONFIG_SYNTAX_OR_DUPLICATE'}
        $values[$Matches[1]]=$Matches[2].Trim()
    }
    Assert-TrainConfig $values
    return $values
}
function Assert-TrainAutonomousConfig($Values) {
    foreach($entry in @{autonomous009Enabled='false';autonomous009Exploration='true';autonomous009AcceptanceSmoke='false';autonomous009Phase='OFF';autonomous009MinLevel='1';autonomous009MaxLevel='85';autonomous009MemoryBudgetMb='8192';autonomous009DurationMinutes='1440';autonomous009CheckpointMinutes='15';autonomous009ReplayCapacity='128';autonomous009EvaluationEveryCycles='1';autonomous009StagnationLimit='3';autonomous009NoDataLimit='3';autonomous009CycleLimit='0';autonomous009SourceCursor='0';autonomous009ClassWeights='auto';autonomous009Families='DAMAGE,HEAL';autonomous009SmokeMaxStarts='6';autonomous009SmokeMaxNativeMinutes='90'}.GetEnumerator()){
        if(!$Values.ContainsKey($entry.Key)){$Values[$entry.Key]=$entry.Value}
    }
    $bounds=@{autonomous009Version=@(1,1);autonomous009MinLevel=@(1,85);autonomous009MaxLevel=@(1,85);autonomous009MemoryBudgetMb=@(1536,131072);autonomous009DurationMinutes=@(1,10080);autonomous009CheckpointMinutes=@(1,1440);autonomous009ReplayCapacity=@(48,512);autonomous009EvaluationEveryCycles=@(1,100);autonomous009StagnationLimit=@(3,3);autonomous009NoDataLimit=@(1,16);autonomous009CycleLimit=@(0,1000000);autonomous009SourceCursor=@(0,[long]::MaxValue)}
    foreach($key in $bounds.Keys){$number=0L;if(![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $bounds[$key][0] -or $number -gt $bounds[$key][1]){throw ('TRAIN009_CONFIG_RANGE:'+ $key)}}
    if(!$Values.ContainsKey('autonomous009EvaluationOnly')){$Values.autonomous009EvaluationOnly='false'}
    if($Values.autonomous009EvaluationOnly -cnotin @('true','false')){throw 'TRAIN010_EVAL_BOOLEAN'}
    # Same 120-second request bound as PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS; fail before GS.
    if($Values.autonomous009EvaluationOnly -ceq 'true' -and [long]$Values.episodeSeconds -gt 120){throw 'TRAIN010_EVALUATION_EPISODE_NATIVE_BOUND'}
    if($Values.autonomous009EvaluationOnly -ceq 'true' -and ($Values.autonomous009Phase -cnotin @('OFF','EVAL') -or $Values.autonomous009Exploration -cne 'false' -or $Values.autonomous009AcceptanceSmoke -cne 'true' -or [int]$Values.autonomous009EvaluationEveryCycles -ne 1)){throw 'TRAIN010_EVALUATION_ONLY_SCOPE'}
    if(!$Values.ContainsKey('autonomous009GuiHoldBeforeNative')){$Values.autonomous009GuiHoldBeforeNative='false'}
    foreach($key in @('autonomous009Enabled','autonomous009Exploration','autonomous009AcceptanceSmoke','autonomous009GuiHoldBeforeNative')){if($Values[$key] -cnotin @('true','false')){throw ('TRAIN009_CONFIG_BOOLEAN:'+ $key)}}
    if($Values.autonomous009GuiHoldBeforeNative -ceq 'true' -and $Values.autonomous009AcceptanceSmoke -cne 'true'){throw 'TRAIN009_GUI_HOLD_REQUIRES_ACCEPTANCE'}
    if($Values['autonomous009Comparator'] -cne 'STOCK_ON_FROZEN_SHORTLIST_V1' -or $Values['v3Enabled'] -cne 'true' -or $Values.autonomous009Phase -cnotin @('OFF','PREPARE','COLLECT','FIT','EVAL')){throw 'TRAIN009_CONFIG_VERSION_OR_PHASE'}
    if($Values.autonomous009Enabled -ceq 'true'){
        if($Values['configProfile'] -cne 'OUTCOME006' -or $Values['outcome006Enabled'] -cne 'true' -or $Values['v3Terminal006'] -cne 'true' -or
           $Values['exploration007Enabled'] -ceq 'true' -or $Values['exploration008Enabled'] -ceq 'true' -or $Values.autonomous009Phase -ceq 'OFF' -or
           $Values['v3DatasetContextPrefix'] -cne 'autonomous009|' -or $Values['autonomous009Campaign'] -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,64}$' -or
           $Values['outcome006DatasetId'] -cnotmatch '^autonomous009-[A-Za-z0-9_-]{1,80}$'){throw 'TRAIN009_CONFIG_AUTHORITY'}
        if($Values.autonomous009Phase -cin @('COLLECT','FIT','EVAL')){
            $version=0L
            if(![long]::TryParse([string]$Values['autonomous009ModelVersion'],[ref]$version) -or $version -lt 1 -or $Values['autonomous009ModelSha'] -cnotmatch '^[0-9a-f]{64}$'){throw 'TRAIN009_MODEL_PIN_REQUIRED'}
        }
    }
    if([int]$Values.autonomous009MinLevel -gt [int]$Values.autonomous009MaxLevel){throw 'TRAIN009_LEVEL_INTERVAL'}
    if([int]$Values.autonomous009MemoryBudgetMb -lt ([int]$Values.maxHeapMb+512)){throw 'TRAIN009_MEMORY_HEADROOM'}
    if([int]$Values.episodesPerBatch -lt 48 -or [int]$Values.episodesPerBatch % 12 -ne 0 -or [int]$Values.episodesPerBatch -gt [int]$Values.autonomous009ReplayCapacity){throw 'TRAIN009_COHORT_CAPACITY'}
    foreach($bound in @(@('autonomous009PriorStarts',10L),@('autonomous009PriorNativeMillis',5400000L))){if($Values.ContainsKey($bound[0])){$value=0L;if(![long]::TryParse([string]$Values[$bound[0]],[ref]$value) -or $value -lt 0 -or $value -gt $bound[1]){throw 'TRAIN009_PRIOR_BUDGET_INVALID'}}}
    $families=@($Values.autonomous009Families.Split(','))
    if(!$families.Count -or @($families|Select-Object -Unique).Count -ne $families.Count -or @($families|Where-Object {$_ -cnotin @('DAMAGE','HEAL')}).Count){throw 'TRAIN009_ACTION_FAMILIES'}
    if($Values.autonomous009ClassWeights -cne 'auto'){
        $seen=@{}
        foreach($entry in $Values.autonomous009ClassWeights.Split(',')){
            if($entry -cnotmatch '^([0-9]{1,3}):([0-9]{1,4})$'){throw 'TRAIN009_CLASS_WEIGHT_SYNTAX'}
            $classId=[int]$Matches[1];$weight=[int]$Matches[2]
            if($classId -gt 200 -or $weight -lt 1 -or $weight -gt 1000 -or $seen.ContainsKey($classId)){throw 'TRAIN009_CLASS_WEIGHT_RANGE'}
            $seen[$classId]=$true
        }
        # Actual PlayerClass membership is validated by Java; no second manual class catalog in PowerShell.
    }
    if($Values.autonomous009AcceptanceSmoke -ceq 'true'){
        $starts=0
        if($Values.autonomous009EvaluationOnly -ceq 'true'){
            $minutes=0
            if(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts) -or $starts -lt 1 -or $starts -gt 3 -or ![int]::TryParse([string]$Values.autonomous009SmokeMaxNativeMinutes,[ref]$minutes) -or $minutes -lt 1 -or $minutes -gt 45){throw 'TRAIN010_EVALUATION_BUDGET'}
        }elseif(![int]::TryParse([string]$Values.autonomous009SmokeMaxStarts,[ref]$starts) -or $starts -lt 6 -or $starts -gt 10 -or $Values.autonomous009SmokeMaxNativeMinutes -cne '90'){throw 'TRAIN009_ACCEPTANCE_BUDGET_FIXED'}
    }
}
function Assert-TrainConfig($Values) {
    foreach($entry in @{maxTrainingCohorts=32;maxStoredActors=4096;maxStoredProfiles=4096;cpuBudgetPercent=75;maxAdaptiveIdleSeconds=300}.GetEnumerator()){
        if(!$Values.ContainsKey($entry.Key)){$Values[$entry.Key]=[string]$entry.Value}
    }
    foreach($entry in @{maxTrainingCohorts=@(1,256);maxStoredActors=@(32,32768);maxStoredProfiles=@(64,32768)}.GetEnumerator()){
        $number=0L
        if(![long]::TryParse([string]$Values[$entry.Key],[ref]$number) -or $number -lt $entry.Value[0] -or $number -gt $entry.Value[1]){throw ('TRAIN002_CONFIG_RANGE:'+ $entry.Key)}
    }
    $bounds=@{schemaVersion=@(1,1);scenarioLimit=@(3,64);maxActiveActors=@(2,9);episodesPerBatch=@(1,256);episodeSeconds=@(15,900);trainingThreshold=@(32,4096);retentionEpisodes=@(100,100000);cpuCount=@(1,16);maxHeapMb=@(1024,16384);sqlPort=@(33000,65535);gamePort=@(1024,65535);loginPort=@(1024,65535);startupTimeoutSeconds=@(30,900);shutdownTimeoutSeconds=@(30,900);batchTimeoutSeconds=@(60,86400);idleSeconds=@(1,3600);minFreeDiskMb=@(1024,1048576);modelVersion=@(0,2147483647)}
    foreach($key in $bounds.Keys){$number=0L;if(!$Values.ContainsKey($key) -or ![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $bounds[$key][0] -or $number -gt $bounds[$key][1]){throw ('TRAIN002_CONFIG_RANGE:'+ $key)}}
    $optional=@{preparationSeconds=@(15,600);maxExamples=@(32,4096);minAuditPerScope=@(1,2048);minimumImprovementBasisPoints=@(0,5000);evidenceFlushMillis=@(100,30000);maxLedgerEpisodes=@(100,10000000);maxLedgerEvents=@(4096,1000000);maxTrainingAttempts=@(1,4096);cpuBudgetPercent=@(1,100);maxAdaptiveIdleSeconds=@(1,3600)}
    foreach($key in $optional.Keys){if($Values.ContainsKey($key)){$number=0L;if(![long]::TryParse([string]$Values[$key],[ref]$number) -or $number -lt $optional[$key][0] -or $number -gt $optional[$key][1]){throw ('TRAIN002_CONFIG_RANGE:'+ $key)}}}
    $seed=0L;if(!$Values.ContainsKey('seed') -or ![long]::TryParse([string]$Values.seed,[ref]$seed)){throw 'TRAIN002_CONFIG_SEED'}
    foreach($key in @('classes','levels','grades','npcIds','modes','learningMode','modelSha')){if(!$Values.ContainsKey($key)){throw ('TRAIN002_CONFIG_REQUIRED:'+ $key)}}
    if($Values.ContainsKey('autonomous009Enabled') -and $Values.autonomous009Enabled -ceq 'true' -and !$Values.ContainsKey('autonomous009Version')){throw 'TRAIN009_CONFIG_VERSION_REQUIRED'}
    $autonomous=$Values.ContainsKey('autonomous009Version')
    foreach($key in @('classes','levels')){if(!($autonomous -and $Values[$key] -ceq 'auto') -and [string]$Values[$key] -cnotmatch '^[0-9]+(?:,[0-9]+)*$'){throw ('TRAIN002_CONFIG_LIST:'+ $key)}}
    if($Values.grades -cnotmatch '^(?:NONE|D|C|B|A|S)(?:,(?:NONE|D|C|B|A|S))*$' -or ($Values.npcIds -and $Values.npcIds -cnotmatch '^[0-9]+(?:,[0-9]+)*$')){throw 'TRAIN002_CONFIG_CATALOG'}
    if($Values.modes -cnotmatch '^(?:SOLO|PARTY)(?:,(?:SOLO|PARTY))*$' -or ($Values.ContainsKey('mode') -and $Values.mode -cnotmatch '^(?:SOLO|PARTY|BOTH)$')){throw 'TRAIN002_UNSUPPORTED_MODE'}
    if($Values.learningMode -notin @('SHADOW','ON') -or ($Values.learningMode -eq 'ON' -and ([long]$Values.modelVersion -lt 1 -or $Values.modelSha -cnotmatch '^[0-9a-f]{64}$'))){throw 'TRAIN002_MODEL_PIN'}
    if(@($Values.sqlPort,$Values.gamePort,$Values.loginPort|Select-Object -Unique).Count -ne 3){throw 'TRAIN002_DISTINCT_ENDPOINTS_REQUIRED'}
    if([int]$Values.maxAdaptiveIdleSeconds -lt [int]$Values.idleSeconds){throw 'TRAIN002_ADAPTIVE_IDLE_BELOW_BASE'}
    if($autonomous){Assert-TrainAutonomousConfig $Values}
}
function Get-TrainAdaptiveIdle($Config,$Resource) {
    $cores=[int]$Config.cpuCount;$budget=[int]$Config.cpuBudgetPercent;$base=[int]$Config.idleSeconds;$maximum=[int]$Config.maxAdaptiveIdleSeconds
    if($cores -lt 1 -or $cores -gt 16 -or $budget -lt 1 -or $budget -gt 100 -or $base -lt 1 -or $maximum -lt $base -or $maximum -gt 3600){throw 'TRAIN002_ADAPTIVE_IDLE_BOUNDS'}
    $decision=[ordered]@{schemaVersion=1;scope='COMPLETED_NATIVE_CPU_ONLY';measurement='UNKNOWN';reason='UNKNOWN_COMPLETED_NATIVE_METRICS';cpuCount=$cores;cpuBudgetPercent=$budget;baseIdleSeconds=$base;maxAdaptiveIdleSeconds=$maximum;idleSeconds=$base;elapsedMillis=$null;cpuSeconds=$null;sampledUtc=$null;requiredIdleSeconds=$null;predictedAverageNativeCpuPercent=$null;capReached=$false}
    if($null -eq $Resource){return $decision}
    foreach($key in @('measurement','exitObserved','elapsedMillis','cpuSeconds')){if(!$Resource.PSObject.Properties[$key] -or $null -eq $Resource.$key){return $decision}}
    if($Resource.measurement -cne 'PROCESS_METADATA' -or $Resource.exitObserved -isnot [bool] -or !$Resource.exitObserved){return $decision}
    try{$elapsed=[double]$Resource.elapsedMillis;$cpu=[double]$Resource.cpuSeconds}catch{return $decision}
    if([double]::IsNaN($elapsed) -or [double]::IsInfinity($elapsed) -or [double]::IsNaN($cpu) -or [double]::IsInfinity($cpu) -or $elapsed -le 0 -or $cpu -lt 0){return $decision}
    $required=[Math]::Max(0,[Math]::Ceiling($cpu/($cores*$budget/100.0)-$elapsed/1000.0))
    if([double]::IsInfinity($required)){return $decision}
    $delay=[int][Math]::Min($maximum,[Math]::Max($base,$required))
    $decision.measurement='KNOWN';$decision.reason=$(if($required -gt $maximum){'CPU_IDLE_CAPPED'}elseif($delay -gt $base){'CPU_PRESSURE_ADAPTIVE_IDLE'}else{'BASE_IDLE_SUFFICIENT'})
    $decision.idleSeconds=$delay;$decision.elapsedMillis=$elapsed;$decision.cpuSeconds=$cpu;$decision.requiredIdleSeconds=$required;$decision.capReached=($required -gt $maximum)
    if($Resource.PSObject.Properties['sampledUtc']){$decision.sampledUtc=$Resource.sampledUtc}
    $decision.predictedAverageNativeCpuPercent=[Math]::Round(100*$cpu/($cores*($elapsed/1000.0+$delay)),3)
    return $decision
}
function Get-TrainEpisodeSummary([object[]]$Rows,[int]$Attempted) {
    if($null -eq $Rows){$Rows=@()}
    $completed=0;$failed=0;[long]$effects=0;$classes=@()
    foreach($row in $Rows){
        $outcome=$row.PSObject.Properties['outcome'];$afterCut=$row.PSObject.Properties['afterCut'];$classId=$row.PSObject.Properties['classId'];$paid=$row.PSObject.Properties['paidRawEffects']
        if($outcome -and $outcome.Value -ceq 'COMPLETED'){$completed++}
        if($outcome -and $outcome.Value -ceq 'FAILED'){$failed++}
        if($afterCut -and $afterCut.Value -eq $true -and $classId){$classes+=([int]$classId.Value)}
        if($paid -and $null -ne $paid.Value){$effects+=[long]$paid.Value}
    }
    return @{attempted=$Attempted;settled=$Rows.Count;completed=$completed;failed=$failed;classes=@($classes|Sort-Object -Unique);paidEffects=$effects}
}
function Get-TrainHeartbeat([string]$Heartbeat,[datetime]$UtcNow) {
    $stamp=[datetime]::MinValue
    if(!$Heartbeat -or ![datetime]::TryParse($Heartbeat,[ref]$stamp)){return @{state='UNKNOWN';ageSeconds=$null}}
    $age=($UtcNow.ToUniversalTime()-$stamp.ToUniversalTime()).TotalSeconds
    if($age -lt -5){return @{state='UNKNOWN';ageSeconds=$age}}
    return @{state=$(if($age -gt 90){'STALE'}else{'FRESH'});ageSeconds=[Math]::Round($age,1)}
}
function Write-TrainConfig([string]$Path,$Values){Assert-TrainConfig $Values;Write-TrainText $Path (($Values.Keys|Sort-Object|ForEach-Object{$_+'='+$Values[$_]}) -join "`n")}
function Get-TrainColdBaseline([string]$Body) {
    $baseline=@{EnablePhantomSystem='False';LearningMode='OFF';LearningModelVersion='0';LearningEvidenceEnabled='False';LearningDeterministicSeed='0'}
    foreach($key in $baseline.Keys){
        $pattern='(?m)^'+[regex]::Escape($key)+'[ \t]*=[^\r\n]*'
        if([regex]::Matches($Body,$pattern).Count -ne 1){throw ('TRAIN002_COLD_BASELINE_KEY:'+ $key)}
        $Body=[regex]::Replace($Body,$pattern,($key+' = '+$baseline[$key]))
    }
    return $Body
}
function Get-TrainExportAdmission([bool]$DirectorLive,[bool]$NativeStopped,[bool]$LifetimeLockHeld) {
    if($DirectorLive){return 'QUEUE'}
    if(!$NativeStopped -or !$LifetimeLockHeld){throw 'TRAIN002_EXPORT_NOT_AT_OWN_STOPPED_BOUNDARY'}
    return 'OFFLINE'
}
function Get-TrainJavaArguments([string]$Class,[string[]]$Arguments,[string]$ClassPath,[string]$WorkerId,[int]$CpuCount,[int]$HeapMb) {
    $javaArguments=@(('-XX:ActiveProcessorCount='+$CpuCount),'-Xms256m',('-Xmx'+$HeapMb+'m'),('-Dphantom.training.worker='+$WorkerId),'-Dphantom.learning.train=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',('"'+$ClassPath+'"'),$Class)
    $javaArguments+=@($Arguments|ForEach-Object{'"'+$_+'"'})
    return $javaArguments
}
function Assert-TrainIdentitySnapshot($Fact,$Expected) {
    if([long]$Fact.pid -ne [long]$Expected.pid -or [long]$Fact.created -ne [long]$Expected.created -or $Fact.image -ine $Expected.image -or $Fact.sid -cne $Expected.sid -or
       [string]::IsNullOrEmpty($Expected.nonce) -or $Fact.command -notmatch ('(?<![A-Za-z0-9])'+[regex]::Escape($Expected.nonce)+'(?![A-Za-z0-9])')){throw 'TRAIN002_FOREIGN_PROCESS'}
    if($Fact.ContainsKey('listener') -and ($Fact.listener -cne '127.0.0.1' -or [long]$Fact.listenerPid -ne [long]$Expected.pid)){throw 'TRAIN002_FOREIGN_LISTENER'}
}
function New-TrainProcessRecord($Process,[string]$Image,[string]$Nonce,[string]$Kind,[string]$ConfigPath='') {
    $native=[Win32SqlIdentity]::Process([uint32]$Process.Id,0x1000)
    if(!$native.Success -or $native.ImagePath -ine $Image){throw 'TRAIN002_STARTED_PROCESS_UNPROVEN'}
    $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($Process.Id)"
    if(!$cim -or !$cim.CommandLine){throw 'TRAIN002_STARTED_COMMAND_UNAVAILABLE'}
    if($Process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne $native.CreatedFileTime){throw 'TRAIN002_STARTED_PROCESS_INCARNATION_CHANGED'}
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$commandHash=[Convert]::ToBase64String($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($cim.CommandLine)))}finally{$hash.Dispose()}
    $parent=[Win32SqlIdentity]::Process([uint32]$cim.ParentProcessId,0x1000)
    return [ordered]@{pid=$Process.Id;created=$native.CreatedFileTime;parentProcessId=$cim.ParentProcessId;parentCreated=$(if($parent.Success){$parent.CreatedFileTime}else{0L});image=$Image;imageHash=(Get-FileHash -LiteralPath $Image).Hash;sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;nonce=$Nonce;kind=$Kind;commandHash=$commandHash;configPath=$ConfigPath;configHash=$(if($ConfigPath){(Get-FileHash -LiteralPath $ConfigPath).Hash}else{''})}
}
function Assert-TrainProcess($Record,[ValidateSet('Director','Sql','Native','Worker')][string]$Kind,[int]$Port=0) {
    Initialize-TrainWin32
    Assert-G0PhysicalFile $Record.image
    if($Record.kind -cne $Kind -or (Get-FileHash -LiteralPath $Record.image).Hash -cne $Record.imageHash){throw 'TRAIN002_PROCESS_IMAGE_CHANGED'}
    if($Record.configPath){Assert-G0PhysicalFile $Record.configPath;if((Get-FileHash -LiteralPath $Record.configPath).Hash -cne $Record.configHash){throw 'TRAIN002_PROCESS_CONFIG_CHANGED'}}
    for($pass=0;$pass -lt 2;$pass++){
        $identity=[Win32SqlIdentity]::Process([uint32]$Record.pid,0x1000)
        $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.pid)"
        if(!$identity.Success -or !$cim -or $cim.ExecutablePath -ine $Record.image -or (Get-G0Microseconds $identity.CreatedFileTime) -ne (Get-G0Microseconds $cim.CreationDate.ToUniversalTime().ToFileTimeUtc())){
            $failure=[InvalidOperationException]::new('TRAIN002_PROCESS_UNAVAILABLE');$failure.Data['pid']=$Record.pid;$failure.Data['created']=$Record.created;$failure.Data['nativeError']=$identity.Error;throw $failure
        }
        $owner=Invoke-CimMethod -InputObject $cim -MethodName GetOwnerSid
        if($owner.ReturnValue -ne 0 -or $Record.sid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value){throw 'TRAIN002_PROCESS_OWNER'}
        $fact=@{pid=$Record.pid;created=$identity.CreatedFileTime;image=$identity.ImagePath;sid=$owner.Sid;command=$cim.CommandLine}
        $hash=[Security.Cryptography.SHA256]::Create()
        try{$commandHash=[Convert]::ToBase64String($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($cim.CommandLine)))}finally{$hash.Dispose()}
        if($commandHash -cne $Record.commandHash){throw 'TRAIN002_PROCESS_FLAGS_CHANGED'}
        if($Kind -eq 'Sql'){
            $pattern='^"?'+[regex]::Escape($Record.image)+'"?\s+"--defaults-file='+[regex]::Escape($Record.configPath)+'"\s+--console\s*$'
            if($cim.CommandLine -cnotmatch $pattern -or @(Get-CimInstance Win32_Service -Filter "ProcessId=$($Record.pid)").Count){throw 'TRAIN002_SQL_COMMAND_OR_SERVICE'}
        }
        if($Port){$listeners=@([Win32SqlIdentity]::Listeners($Port));if($listeners.Count -ne 1){throw 'TRAIN002_LISTENER_COUNT'};$fact.listener=$listeners[0].LocalAddress;$fact.listenerPid=$listeners[0].OwningProcess}
        Assert-TrainIdentitySnapshot $fact $Record
    }
}
function Test-TrainProcessAbsent($Record,[Diagnostics.Process]$TrackedProcess=$null,[object]$Handle=$null) {
    $lastError=-1
    for($query=0;$query -lt 10;$query++){
        if($null -ne $TrackedProcess){
            $held=Get-TrainHeldExitProof $Record $TrackedProcess $Handle
            if($held.exited){
                Save-TrainProcessObservation $Record $null 'OWN_HELD_HANDLE_EXIT' $held.exitCode
                return $true
            }
        }
        $actual=[Win32SqlIdentity]::Process([uint32]$Record.pid,0x1000)
        if($actual.Success){if($lastError -ge 0){Save-TrainProcessObservation $Record 0 'VISIBLE_AFTER_RETRY'};return $actual.CreatedFileTime -ne [long]$Record.created}
        if($actual.Error -eq 87){if($lastError -ge 0){Save-TrainProcessObservation $Record 87 'ABSENT_AFTER_RETRY'};return $true}
        if($actual.Error -eq 31){
            $layout=Get-TrainLayout
            if((Test-TrainExitReceipt $layout $Record) -and @(Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.pid)").Count -eq 0){
                Save-TrainProcessObservation $Record 31 'PRIVATE_HELD_EXIT_RECEIPT_CIM_ABSENT'
                return $true
            }
        }
        if($actual.Error -ne $lastError){Save-TrainProcessObservation $Record $actual.Error 'VISIBILITY_UNKNOWN';$lastError=$actual.Error}
        if($query -lt 9){Start-Sleep -Milliseconds 100}
    }
    $failure=[InvalidOperationException]::new('TRAIN002_PROCESS_VISIBILITY_UNKNOWN');$failure.Data['pid']=$Record.pid;$failure.Data['created']=$Record.created;$failure.Data['nativeError']=$lastError;throw $failure
}
function Get-TrainProcessResources([Diagnostics.Process]$Process,$Prior=$null) {
    try{
        $Process.Refresh()
        $peak=$Process.PeakWorkingSet64
        if($Prior -and $Prior.peakWorkingSetBytes -gt $peak){$peak=$Prior.peakWorkingSetBytes}
        $started=$Process.StartTime.ToUniversalTime()
        return [ordered]@{pid=$Process.Id;startedUtc=$started.ToString('o');sampledUtc=[DateTime]::UtcNow.ToString('o');elapsedMillis=[long]([DateTime]::UtcNow-$started).TotalMilliseconds;cpuSeconds=[Math]::Round($Process.TotalProcessorTime.TotalSeconds,6);workingSetBytes=$Process.WorkingSet64;peakWorkingSetBytes=$peak;measurement='PROCESS_METADATA';exitObserved=$Process.HasExited}
    }catch{
        if($Prior){return $Prior}
        return [ordered]@{pid=$null;startedUtc=$null;sampledUtc=[DateTime]::UtcNow.ToString('o');elapsedMillis=$null;cpuSeconds=$null;workingSetBytes=$null;peakWorkingSetBytes=$null;measurement='UNKNOWN';exitObserved=$null}
    }
}
function Assert-TrainPortsFree($Ports){Initialize-TrainWin32;foreach($port in $Ports){if(@([Win32SqlIdentity]::Listeners([int]$port)).Count){throw ('TRAIN002_PORT_BUSY:'+ $port)}}}
function Assert-TrainHash([string]$Path,[string]$Sha){Assert-G0PhysicalFile $Path;if((Get-FileHash -LiteralPath $Path).Hash -ine $Sha){throw ('TRAIN002_HASH_MISMATCH:'+ $Path)}}
function Get-TrainProtectedPin($Config) {
    if($Config -and $Config.ContainsKey('configProfile') -and $Config.configProfile -ceq 'OUTCOME006'){
        if(!$Config.ContainsKey('outcome006ProtectedSha') -or $Config.outcome006ProtectedSha -ine $script:TrainProtected006Sha){throw 'OUTCOME006_EXACT_PROTECTED_BASELINE_REQUIRED'}
        return $script:TrainProtected006Sha
    }
    if($Config -and $Config.ContainsKey('configProfile') -and $Config.configProfile -ceq 'EVAL005'){
        if(!$Config.ContainsKey('evaluation005ProtectedSha') -or $Config.evaluation005ProtectedSha -ine $script:TrainProtected005Sha){throw 'EVAL005_EXACT_PROTECTED_BASELINE_REQUIRED'}
        return $script:TrainProtected005Sha
    }
    return $script:TrainProtectedSha
}
function Assert-TrainAssets([string]$GeoDirectory,[string]$GeoManifest,[string]$ProtectedManifest,$Config=$null) {
    Assert-TrainHash $GeoManifest $script:TrainGeoSha;Assert-TrainHash $ProtectedManifest (Get-TrainProtectedPin $Config)
    $geo=ConvertFrom-Json ([IO.File]::ReadAllText($GeoManifest));$protected=ConvertFrom-Json ([IO.File]::ReadAllText($ProtectedManifest))
    Assert-TrainDirectory $GeoDirectory
    $files=@(Get-ChildItem -LiteralPath $GeoDirectory -Force -File)
    if($geo.Count -ne 203 -or $files.Count -ne 203 -or ($geo|Measure-Object bytes -Sum).Sum -ne 1063452308 -or @(Compare-Object $files.Name $geo.name -CaseSensitive).Count -or $protected.Count -ne 207){throw 'TRAIN002_GEO_OR_PROTECTED_SET'}
    foreach($row in $geo){$path=Join-Path $GeoDirectory $row.name;if((Get-Item -LiteralPath $path).Length -ne $row.bytes){throw 'TRAIN002_GEO_SIZE'};Assert-TrainHash $path $row.sha256}
    foreach($row in $protected){Assert-TrainHash $row.path $row.sha256}
}
function Assert-TrainInstallation($Layout) {
    Assert-TrainPrivate $Layout.Root
    $owner=Join-Path $Layout.Root 'owner.txt';Assert-G0PhysicalFile $owner
    if([IO.File]::ReadAllText($owner) -cnotmatch '^LEARNING002:[0-9a-f]{32}$'){throw 'TRAIN002_OWNER_MARKER'}
}
function Enter-TrainLock($Layout) {
    Assert-TrainInstallation $Layout
    $path=Join-Path $Layout.Root 'director.lock'
    if(Test-Path -LiteralPath $path){Assert-G0PhysicalFile $path}
    return [IO.FileStream]::new($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
}
function Assert-TrainSqlOptions($Layout,$Config,$Record) {
    Assert-TrainInstallation $Layout
    Assert-TrainDirectory (Join-Path $Layout.Sql 'data')
    Assert-G0PhysicalFile $Record.configPath
    if((Split-Path $Record.configPath -Parent) -ine $Layout.Sql){throw 'TRAIN002_SQL_CONFIG_SCOPE'}
    $expected=@('[mysqld]','bind-address=127.0.0.1',('port='+$Config.sqlPort),('datadir="'+(Join-Path $Layout.Sql 'data').Replace('\','/')+'"'),'default-time-zone=+00:00')
    if(([IO.File]::ReadAllLines($Record.configPath) -join '|') -cne ($expected -join '|')){throw 'TRAIN002_SQL_OPTIONS'}
}
function Stop-TrainSql($Layout,$Config,$Record) {
    Assert-TrainSqlOptions $Layout $Config $Record
    Assert-TrainProcess $Record -Kind Sql -Port ([int]$Config.sqlPort)
    $process=Get-Process -Id $Record.pid -ErrorAction Stop
    $handle=Hold-ProcessHandle $process
    if($process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$Record.created){throw 'TRAIN002_TRACKED_PROCESS_IDENTITY'}
    $resources=Get-TrainProcessResources $process
    $admin=Join-Path $Layout.Sql 'admin.client.ini';Assert-G0PhysicalFile $admin
    $lines=[IO.File]::ReadAllLines($admin)
    if($lines.Count -ne 5 -or ($lines[0..3] -join '|') -cne ('[client]|host=127.0.0.1|port='+$Config.sqlPort+'|user=root') -or $lines[4] -cnotmatch '^password=[0-9a-f]{64}$'){throw 'TRAIN002_ADMIN_OPTIONS'}
    try{
        & $Layout.Admin ('--defaults-file='+$admin) --protocol=tcp --host=127.0.0.1 ('--port='+$Config.sqlPort) --user=root shutdown
        if($LASTEXITCODE -ne 0){throw 'TRAIN002_SQL_SHUTDOWN_FAILED'}
        $deadline=[DateTime]::UtcNow.AddSeconds([int]$Config.shutdownTimeoutSeconds)
        do{
            $resources=Get-TrainProcessResources $process $resources
            if(Test-TrainProcessAbsent $Record $process $handle){Assert-TrainPortsFree @([int]$Config.sqlPort);Write-TrainExitReceipt $Layout $Record $process $handle;return}
            Start-Sleep -Milliseconds 250
        }while([DateTime]::UtcNow -lt $deadline)
        throw 'TRAIN002_SQL_STOP_UNCONFIRMED'
    }finally{
        $resources=Get-TrainProcessResources $process $resources
        $resources['finishedUtc']=[DateTime]::UtcNow.ToString('o')
        Write-TrainJson (Join-Path $Layout.Sql ('sql-resources.'+$Record.created+'.json')) $resources
        $process.Dispose()
    }
}
function Enter-TrainInitialization($Layout) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$key=([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($Layout.Root.ToLowerInvariant()+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value)))).Replace('-','')}finally{$hash.Dispose()}
    $mutex=[Threading.Mutex]::new($false,('Local\PhantomTraining002-'+$key))
    try{$taken=$mutex.WaitOne(5000)}catch [Threading.AbandonedMutexException]{$taken=$true}
    if(!$taken){$mutex.Dispose();throw 'TRAIN002_ANOTHER_COMMAND_ACTIVE'}
    return $mutex
}
