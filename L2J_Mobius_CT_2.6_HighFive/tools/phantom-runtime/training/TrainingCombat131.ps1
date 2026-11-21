[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Preflight','Start','Status','Pause','Resume','Stop','Report')][string]$Action,
    [Parameter(Mandatory=$true)][string]$Manifest
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')

$module131=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$task131=Join-Path (Split-Path $module131 -Parent) '.phantom-local\logs\TASK131'
$campaigns131=Join-Path $task131 'campaigns'
$utf131=[Text.UTF8Encoding]::new($false,$true)
$entry131='org.l2jmobius.tests.phantoms.PhantomLearning131Native'
$factory131='org.l2jmobius.gameserver.phantoms.PhantomLearning131Factory'
$sqlSchema131='l2jmobiush5_phantom_test_131'
$dependencies131=@('HikariCP-7.0.2.jar','mysql-connector-j-9.5.0.jar','slf4j-api-2.0.17.jar','slf4j-simple-2.0.17.jar')

function Assert-131($Condition,[string]$Reason) {
    if(!$Condition){throw ('TRAINING131_'+$Reason)}
}

function Assert-131Directory([string]$Path) {
    Assert-131 ([IO.Path]::IsPathRooted($Path)) 'ABSOLUTE_DIRECTORY'
    $directory=Get-Item -Force -LiteralPath $Path
    Assert-131 ($directory.PSIsContainer -and $directory.FullName -ieq $Path) 'PHYSICAL_DIRECTORY'
    for($part=$directory;$null -ne $part;$part=$part.Parent){
        Assert-131 (!($part.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'REPARSE_DIRECTORY'
    }
}

function Get-131Sha([string]$Path) {
    Assert-G0PhysicalFile $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-131Sha([string]$Path,[string]$Sha) {
    Assert-131 ($Sha -cmatch '^[a-f0-9]{64}$' -and (Get-131Sha $Path) -ceq $Sha) 'PIN_DRIFT'
}

function Read-131Json([string]$Path) {
    Assert-G0PhysicalFile $Path
    Assert-131 ((Get-Item -LiteralPath $Path).Length -le 1MB) 'METADATA_SIZE'
    return ConvertFrom-Json ([IO.File]::ReadAllText($Path,$utf131))
}

function Read-131Properties([string]$Path) {
    Assert-G0PhysicalFile $Path
    Assert-131 ((Get-Item -LiteralPath $Path).Length -le 64KB) 'PROPERTIES_SIZE'
    $result=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($line in [IO.File]::ReadAllLines($Path,$utf131)){
        Assert-131 ($line -cmatch '^([A-Za-z][A-Za-z0-9_.-]{0,63}|episode\.[0-9a-f]{64}\.(?:sourceSha|policySha))=(.+)$') 'PROPERTY_LINE'
        Assert-131 (!$result.ContainsKey($Matches[1])) 'PROPERTY_DUPLICATE'
        $result.Add($Matches[1],$Matches[2])
    }
    return ,$result
}

function Write-131Text([string]$Path,[string]$Text) {
    Assert-131Directory (Split-Path $Path -Parent)
    $pending=$Path+'.'+[Guid]::NewGuid().ToString('N')+'.part'
    try{
        $bytes=$utf131.GetBytes($Text)
        $stream=[IO.FileStream]::new($pending,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
        [IO.File]::Move($pending,$Path)
    }finally{if(Test-Path -LiteralPath $pending){Remove-Item -LiteralPath $pending}}
}

function Write-131Json([string]$Path,$Body) {
    Write-131Text $Path (($Body|ConvertTo-Json -Depth 12)+"`n")
}

function ConvertTo-131Utc([string]$Value) {
    Assert-131 ($Value -cmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$') 'UTC_FORMAT'
    return [DateTimeOffset]::Parse($Value,[Globalization.CultureInfo]::InvariantCulture).UtcDateTime
}

function Assert-131Fields($Object,[string[]]$Expected) {
    $names=@($Object.PSObject.Properties.Name)
    Assert-131 ($names.Count -eq $Expected.Count -and @($names|Where-Object {$_ -cnotin $Expected}).Count -eq 0) 'MANIFEST_FIELDS'
}

# Each cycle/bank has one fixed storage slot. A different campaign name cannot renew its quota.
function Read-131Manifest([string]$Path) {
    Assert-131 ([IO.Path]::IsPathRooted($Path) -and $Path -ieq [IO.Path]::GetFullPath($Path)) 'MANIFEST_PATH'
    $manifestSha=Get-131Sha $Path
    $body=Read-131Json $Path
    Assert-131Sha $Path $manifestSha
    Assert-131Fields $body @('schema','campaignId','phase','cycle','auditBank','sourceCommit','runtimeRoot','sqlLane','javaSha','gameJarSha','classPinsSha',
        'executionSha','safetyReviewSha','fixtureSha','policySha','modelSha','dbSealSha','jdbcSha','dependencyShas','configShas','quota','scenes')
    Assert-131 ($body.schema -ceq 'TRAINING_COMBAT131_V1' -and $body.phase -cin @('N01','TRAIN','TUNING','AUDIT') -and $body.sourceCommit -cmatch '^[a-f0-9]{40}$') 'MANIFEST_SCHEMA'
    Assert-131 ($body.cycle -is [int] -and $body.auditBank -is [int]) 'PHASE_ORDINAL'
    $scope=switch -CaseSensitive ($body.phase){
        'N01' {Assert-131 ($body.cycle -eq 0 -and $body.auditBank -eq 0) 'N01_ORDINAL';'n01'}
        'AUDIT' {Assert-131 ($body.cycle -eq 0 -and $body.auditBank -in @(1,2)) 'AUDIT_ORDINAL';'audit-{0:00}' -f $body.auditBank}
        default {Assert-131 ($body.cycle -in (1..6) -and $body.auditBank -eq 0) 'CYCLE_ORDINAL';'{0}-{1:00}' -f $body.phase.ToLowerInvariant(),$body.cycle}
    }
    $root=Join-Path $campaigns131 $scope
    Assert-131 ($body.campaignId -ceq $scope -and $Path -ieq (Join-Path $root 'campaign.json')) 'CAMPAIGN_SCOPE'
    Assert-131 ((Split-Path $body.runtimeRoot -Parent) -ieq $task131 -and (Split-Path $body.runtimeRoot -Leaf) -cmatch '^native-runtime(?:-r[1-9][0-9]*)?$' -and
        $body.sqlLane -cmatch '^[a-z][a-z0-9-]{0,23}$') 'RUNTIME_SCOPE'
    Assert-131 ($body.phase -cne 'N01' -or $body.sqlLane -cmatch '^n01-setup-r[1-9][0-9]*$') 'N01_SQL_LANE'
    foreach($key in @('javaSha','gameJarSha','classPinsSha','executionSha','safetyReviewSha','fixtureSha','policySha','dbSealSha','jdbcSha')){
        Assert-131 ($body.$key -cmatch '^[a-f0-9]{64}$') 'MANIFEST_HASH_FORMAT'
    }
    Assert-131 (($body.phase -ceq 'N01' -and $body.modelSha -ceq 'NONE') -or ($body.phase -cne 'N01' -and $body.modelSha -cmatch '^[a-f0-9]{64}$')) 'MODEL_PIN'
    Assert-131Fields $body.quota @('maxBoots','maxEpisodes','bootSeconds','drainSeconds','deadlineUtc')
    $limit=switch -CaseSensitive ($body.phase){'N01'{4};'TRAIN'{6};'TUNING'{3};'AUDIT'{128}}
    Assert-131 ($body.quota.maxBoots -is [int] -and $body.quota.maxBoots -in (1..12) -and $body.quota.maxEpisodes -is [int] -and
        $body.quota.maxEpisodes -ge 1 -and $body.quota.maxEpisodes -le $limit -and @($body.scenes).Count -eq $body.quota.maxEpisodes -and
        ($body.phase -cnotin @('N01','AUDIT') -or $body.quota.maxEpisodes -eq $limit)) 'PHASE_QUOTA'
    Assert-131 ($body.quota.bootSeconds -is [int] -and $body.quota.bootSeconds -in (1..300) -and $body.quota.drainSeconds -is [int] -and
        $body.quota.drainSeconds -in (1..120)) 'BOOT_DRAIN_QUOTA'
    $sceneIds=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($scene in $body.scenes){
        Assert-131Fields $scene @('id','participantCount','deadlineSeconds')
        Assert-131 ($scene.id -cmatch '^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$' -and $sceneIds.Add([string]$scene.id) -and
            $scene.participantCount -is [int] -and $scene.participantCount -in (1..18) -and $scene.deadlineSeconds -is [int] -and $scene.deadlineSeconds -gt 0) 'SCENE_LIMIT_OR_ID'
    }
    $budget=$body.quota.bootSeconds+$body.quota.drainSeconds+(Get-131SceneSeconds $body 0)
    Assert-131 ($budget -lt 28800) 'EIGHT_HOUR_RUN_FORBIDDEN'
    return @{Body=$body;Manifest=$Path;Sha=$manifestSha;Root=$root;Scope=$scope;Boots=(Join-Path $root 'boots');Deadline=(ConvertTo-131Utc $body.quota.deadlineUtc)}
}

function Get-131Path($Context,[string]$Name) {
    return Join-Path $Context.Root $Name
}

function Get-131SceneSeconds($Body,[int]$Completed) {
    $timing=$Body.scenes|Select-Object -Skip $Completed|Measure-Object -Property deadlineSeconds -Sum -Maximum
    if($Body.phase -ceq 'N01'){return [int]$timing.Maximum}
    return [int]$timing.Sum
}

function Assert-131Authority($Context) {
    $authorityPath=Get-131Path $Context 'authorization.json'
    $authority=Read-131Json $authorityPath
    Assert-131 ($authority.schema -ceq 'TASK131_LAUNCH_AUTHORITY_V1' -and $authority.authority -ceq 'OWNER_TASK131_GOAL' -and
        $authority.scope -ceq 'OWNED_TEST131' -and $authority.manifestSha -ceq $Context.Sha -and $authority.authorityRef -cmatch '^\S.{0,159}$') 'AUTHORITY_SCOPE'
    $reviewPath=Get-131Path $Context 'safety-review024.json'
    Assert-131Sha $reviewPath $Context.Body.safetyReviewSha
    $review=Read-131Json $reviewPath
    Assert-131 ($review.schema -ceq 'TASK131_SAFETY_REVIEW024_V1' -and $review.scope -ceq 'HISTORICAL_024_LINEAGE_ONLY' -and
        $review.latch -ceq 'PAUSED_SAFETY_REVIEW' -and $review.latchSha -ceq '3ab3ac9453d631a07261938e11526cfb45cf45dcfcccc1b940642ddd130c7da9' -and
        $review.checkpointSha -ceq '90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6' -and
        $review.proofSha -ceq '4e0be150b671f0ad68ddb1b0719695b5dc6cc55bbeaff8adb10c6506dddec414') 'LEGACY_LATCH_SCOPE'
    return Get-131Sha $authorityPath
}

function Assert-131Ini([string]$Path,$Expected,[switch]$ExactValues) {
    Assert-G0PhysicalFile $Path
    $observed=@{}
    foreach($line in [IO.File]::ReadLines($Path,$utf131)){
        if($line -match '^\s*([A-Za-z][A-Za-z0-9]*)\s*=\s*(.*?)\s*$' -and $Expected.Contains($Matches[1])){
            Assert-131 (!$observed.ContainsKey($Matches[1])) 'DUPLICATE_RUNTIME_CONFIG'
            $observed[$Matches[1]]=$Matches[2]
        }
    }
    foreach($key in $Expected.Keys){
        $matchesValue=if($ExactValues){$observed[$key] -ceq $Expected[$key]}else{$observed[$key] -ieq $Expected[$key]}
        Assert-131 ($observed.ContainsKey($key) -and $matchesValue) 'RUNTIME_CONFIG_SCOPE'
    }
}

function Assert-131MasterOff($Context) {
    $path=Join-Path $Context.Body.runtimeRoot 'game/config/Custom/PhantomPlayers.ini'
    Assert-131Sha $path $Context.Body.configShas.'game/config/Custom/PhantomPlayers.ini'
    Assert-131Ini $path ([ordered]@{EnablePhantomSystem='False';LearningMode='OFF'})
}

function Assert-131ClassTree($Context) {
    $classRoot=Get-131Path $Context 'native-classes'
    $pinPath=Get-131Path $Context 'class-pins.tsv'
    Assert-131Sha $pinPath $Context.Body.classPinsSha
    Assert-131Directory $classRoot
    $prefix=$classRoot+'\'
    $pins=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($line in [IO.File]::ReadLines($pinPath,$utf131)){
        Assert-131 ($line -cmatch '^([a-f0-9]{64})\t((?:[A-Za-z0-9_$-]+/)*[A-Za-z0-9_$-]+\.class)$') 'CLASS_PIN_FORMAT'
        $relative=$Matches[2].Replace('/','\')
        Assert-131 (!$pins.ContainsKey($relative)) 'CLASS_PIN_DUPLICATE'
        $pins.Add($relative,$Matches[1])
    }
    $entryFile=$entry131.Replace('.','\')+'.class'
    Assert-131 ($pins.Count -in (1..10000) -and $pins.ContainsKey($entryFile)) 'NATIVE_CLASS_REQUIRED'
    $remaining=[Collections.Generic.Stack[string]]::new()
    $remaining.Push($classRoot)
    while($remaining.Count -gt 0){
        $directory=$remaining.Pop()
        Assert-131Directory $directory
        foreach($child in Get-ChildItem -LiteralPath $directory -Force){
            if($child.PSIsContainer){$remaining.Push($child.FullName);continue}
            $relative=$child.FullName.Substring($prefix.Length)
            Assert-131 ($pins.ContainsKey($relative)) 'UNPINNED_CLASS_PATH'
            Assert-131Sha $child.FullName $pins[$relative]
            $null=$pins.Remove($relative)
        }
    }
    Assert-131 ($pins.Count -eq 0) 'MISSING_PINNED_CLASS'
    return $classRoot
}

function Assert-131RuntimeTree([string]$Root) {
    Assert-131Directory $Root
    $directories=[Collections.Generic.Stack[string]]::new()
    $directories.Push($Root)
    while($directories.Count -gt 0){
        $directory=$directories.Pop()
        $entry=Get-Item -Force -LiteralPath $directory
        Assert-131 ($entry.PSIsContainer -and !($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'RUNTIME_REPARSE_DIRECTORY'
        foreach($item in Get-ChildItem -LiteralPath $directory -Force){
            Assert-131 (!($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'RUNTIME_REPARSE_ENTRY'
            if($item.PSIsContainer){$directories.Push($item.FullName)}
        }
    }
}

function Get-131Sql($Context) {
    $lane=$Context.Body.sqlLane
    $root=Join-Path (Join-Path $task131 ('sql-lanes\'+$lane)) 'sql-task131'
    Assert-131Directory $root
    $config=Join-Path $root 'owned-server.ini'
    $data=Join-Path $root 'data'
    $readyPath=Join-Path $root 'schema-ready.json'
    $proofPath=Join-Path $root 'attestation.json'
    $attestationSha=Get-131Sha $proofPath
    $state=Read-131Json (Join-Path $root 'owned-process.json')
    $proof=Read-131Json $proofPath
    $ready=Read-131Json $readyPath
    Assert-131Sha $readyPath $Context.Body.dbSealSha
    Assert-131Sha (Join-Path $root 'Database.ini') $Context.Body.jdbcSha
    Assert-131 ($state.lane -ceq $lane -and $state.schema -ceq $sqlSchema131 -and $state.port -eq 33331 -and
        $state.pid -is [int] -and $state.pid -gt 0 -and [long]$state.created -gt 0) 'OWNED_SQL_STATE'
    Assert-131 ($ready.version -eq 1 -and $ready.lane -ceq $lane -and $ready.schema -ceq $sqlSchema131 -and $ready.port -eq 33331 -and
        $ready.jdbcConfigSha256 -ieq $Context.Body.jdbcSha) 'OWNED_SQL_SCHEMA'
    Assert-131 ($proof.version -eq 1 -and $proof.lane -ceq $lane -and $proof.phase -ceq 'RUNNING' -and $proof.foreignTableDenied -eq $true -and
        $proof.schemaSealSha256 -ieq $Context.Body.dbSealSha -and $proof.os.PID -eq $state.pid -and
        ([DateTime]$proof.os.Created).ToUniversalTime().ToFileTimeUtc() -eq [long]$state.created -and
        $proof.query.host -ceq '127.0.0.1' -and $proof.query.port -eq 33331 -and $proof.query.schema -ceq $sqlSchema131 -and
        $proof.query.principal -ceq 'task131_runtime@127.0.0.1' -and $proof.query.dataDirectory -ieq $data) 'OWNED_SQL_ATTESTATION'
    Initialize-TrainWin32
    $daemon=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin\mariadbd.exe'
    $live=Get-G0OwnedSqlIdentity $state.pid $state.created $daemon $config $data 33331
    Assert-131 ($live.Binary -ceq $state.binary -and $live.ConfigHash -ceq $state.configHash -and
        $live.CommandLine -ceq $proof.os.CommandLine -and $live.OwnerSid -ceq $proof.os.OwnerSid) 'LIVE_SQL_IDENTITY'
    Assert-131Sha $proofPath $attestationSha
    return @{Identity=$live;AttestationSha=$attestationSha;Root=$root}
}

function Assert-131PortsFree {
    Initialize-TrainWin32
    foreach($portNumber in @(2106,33332,33333)){
        Assert-131 (@([Win32SqlIdentity]::Listeners($portNumber)).Count -eq 0) ('OWNED_LISTENER_COLLISION_'+$portNumber)
    }
}

function Get-131Boundary($Context) {
    Assert-131 ($Context.Body.phase -cin @('N01','TRAIN','TUNING')) 'NATIVE_ADAPTER_PHASE_UNAVAILABLE'
    Assert-131 ($Context.Body.sqlLane -cmatch '^n01-setup-r[1-9][0-9]*$') 'NATIVE_SQL_SETUP_LANE'
    $now=[DateTime]::UtcNow
    Assert-131 ($now -lt $Context.Deadline -and ($Context.Deadline-$now).TotalSeconds -lt 28800) 'CAMPAIGN_DEADLINE'
    Assert-131Sha $Context.Manifest $Context.Sha
    $authoritySha=Assert-131Authority $Context
    $runtime=$Context.Body.runtimeRoot
    Assert-131RuntimeTree $runtime
    Assert-131 (!(Test-Path -LiteralPath (Join-Path $runtime 'BOOT_DISABLED.txt'))) 'RUNTIME_BOOT_DISABLED'
    $execution=Get-131Path $Context 'execution.properties'
    Assert-131Sha $execution $Context.Body.executionSha
    $executionPlan=Read-131Properties $execution
    Assert-131 ($executionPlan['schema'] -ceq 'TRAINING_EXECUTION131_V1' -and $executionPlan['phase'] -ceq $Context.Body.phase -and
        $executionPlan['sourceCommit'] -ceq $Context.Body.sourceCommit -and $executionPlan['campaignId'] -ceq $Context.Scope -and
        $executionPlan['sqlLane'] -ceq $Context.Body.sqlLane -and $executionPlan['jdbcSha'] -ceq $Context.Body.jdbcSha -and
        $executionPlan['gameJarSha'] -ceq $Context.Body.gameJarSha -and $executionPlan['maxEpisodes'] -ceq [string]$Context.Body.quota.maxEpisodes -and
        $executionPlan['drainSeconds'] -ceq [string]$Context.Body.quota.drainSeconds) 'EXECUTION_MANIFEST_DRIFT'
    Assert-131 ($executionPlan['runtimeRoot'].Replace('\\','\') -ieq $runtime -and
        $executionPlan['gameJar'].Replace('\\','\') -ieq (Join-Path $runtime 'libs\GameServer.jar')) 'EXECUTION_RUNTIME_PATH'
    if($Context.Body.phase -ceq 'N01'){
        Assert-131 ($executionPlan['fixtureSha'] -ceq $Context.Body.fixtureSha -and $executionPlan['sceneSha'] -cmatch '^[a-f0-9]{64}$' -and
            $executionPlan['sceneScopeSha'] -cmatch '^[a-f0-9]{64}$' -and $executionPlan['enabledPhantomIniSha'] -ceq $Context.Body.policySha -and
            $executionPlan['observeSeconds'] -ceq [string](Get-131SceneSeconds $Context.Body 0)) 'N01_POLICY_FIXTURE_DRIFT'
        Assert-131Sha (Join-Path $runtime 'game/config/Custom/PhantomPlayers.n01.ini') $Context.Body.policySha
    } else {
        Assert-131Sha (Get-131Path $Context 'scenes.tsv') $Context.Body.fixtureSha
        Assert-131Sha (Get-131Path $Context 'policy131.properties') $Context.Body.policySha
        Assert-131TrainPlan $Context $executionPlan
        Assert-131Sha (Join-Path $runtime 'game/config/Custom/PhantomPlayers.n01.ini') $executionPlan['enabledPhantomIniSha']
    }
    if($Context.Body.modelSha -cne 'NONE'){
        Assert-131Sha (Get-131Path $Context ('candidate-'+$Context.Body.modelSha+'.bin')) $Context.Body.modelSha
    }
    Assert-131Fields $Context.Body.dependencyShas $dependencies131
    $libRoot=Join-Path $runtime 'libs'
    $jar=Join-Path $libRoot 'GameServer.jar'
    Assert-131Sha $jar $Context.Body.gameJarSha
    $cp=@($jar)
    foreach($name in $dependencies131){
        $file=Join-Path $libRoot $name
        Assert-131Sha $file $Context.Body.dependencyShas.$name
        $cp+=@($file)
    }
    $classRoot=Assert-131ClassTree $Context
    $cp+=@($classRoot)
    Assert-131Sha (Join-Path $classRoot ($entry131.Replace('.','\')+'.class')) $executionPlan['adapterClassSha']
    Assert-131Sha (Join-Path $classRoot ($factory131.Replace('.','\')+'.class')) $executionPlan['factoryClassSha']
    $runtimeConfigs=@('game/config/Server.ini','game/config/Database.ini','game/config/Custom/PhantomPlayers.ini',
        'game/config/General.ini','game/config/Player.ini','login/config/Server.ini','game/log.cfg')
    Assert-131Fields $Context.Body.configShas $runtimeConfigs
    foreach($name in $runtimeConfigs){Assert-131Sha (Join-Path $runtime $name) $Context.Body.configShas.$name}
    Assert-131 ($Context.Body.configShas.'game/config/Database.ini' -ceq $Context.Body.jdbcSha) 'GAME_JDBC_PIN'
    Assert-131Ini (Join-Path $runtime 'game/config/Database.ini') ([ordered]@{
        URL=('jdbc:mysql://127.0.0.1:33331/'+$sqlSchema131+'?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000')
        Login='task131_runtime';Driver='com.mysql.cj.jdbc.Driver';BackupDatabase='false'
    }) -ExactValues
    Assert-131Ini (Join-Path $runtime 'game/config/Server.ini') ([ordered]@{GameserverHostname='127.0.0.1';GameserverPort='33332';LoginHost='127.0.0.1';LoginPort='33333'})
    Assert-131Ini (Join-Path $runtime 'login/config/Server.ini') ([ordered]@{LoginserverHostname='127.0.0.1';LoginserverPort='2106';LoginHostname='127.0.0.1';LoginPort='33333'})
    Assert-131MasterOff $Context
    if($Context.Body.phase -ceq 'N01'){
        Assert-131Ini (Join-Path $runtime 'game/config/General.ini') ([ordered]@{LogItems='True';LogItemsSmallLog='False';LogItemsIdsOnly='False'})
        Assert-131Ini (Join-Path $runtime 'game/config/Player.ini') ([ordered]@{AutoLoot='False'})
    }
    $java=(Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
    $javaSha=$Context.Body.javaSha
    Assert-131Sha $java $javaSha
    Assert-131 ((Get-Item -LiteralPath $java).VersionInfo.ProductMajorPart -eq 25) 'JDK25_REQUIRED'
    foreach($variable in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){
        Assert-131 ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($variable,'Process'))) 'JAVA_OPTION_INJECTION'
    }
    foreach($component in @($java,$execution)+$cp){
        Assert-131 ($component -cnotmatch '[";\r\n]' -and !$component.EndsWith('\')) 'JVM_PATH_QUOTING'
    }
    $proof=Get-131Sql $Context
    Assert-131Sha (Join-Path $proof.Root 'n01-scenes.tsv') $executionPlan['sceneSha']
    Assert-131Sha (Join-Path $proof.Root 'n01-fixture.tsv') $executionPlan['fixtureSha']
    Assert-131Sha (Join-Path $proof.Root 'n01-scene-scope.properties') $executionPlan['sceneScopeSha']
    if($Context.Body.phase -cin @('TRAIN','TUNING')){Assert-131Sha (Join-Path $proof.Root 'n01-learned-offense.tsv') $executionPlan['learnedWitnessSha']}
    Assert-131PortsFree
    return @{Java=$java;JavaSha=$javaSha;Classpath=($cp -join ';');Execution=$execution;AuthoritySha=$authoritySha;Sql=$proof;Game=(Join-Path $runtime 'game')}
}

function Assert-131TrainPlan($Context,$Execution) {
    $body=$Context.Body
    Assert-131 ($Execution['trainScenesSha'] -ceq $body.fixtureSha -and $Execution['policySha'] -ceq $body.policySha -and
        $Execution['learnerSha'] -ceq $body.modelSha -and $Execution['expectedHead'] -cmatch '^[0-9a-f]{64}$' -and
        $Execution['rollbackHead'] -ceq $Execution['expectedHead'] -and $Execution['rollbackModelSha'] -ceq $body.modelSha -and
        $Execution['opponentSha'] -cmatch '^[0-9a-f]{64}$' -and $Execution['learnedWitnessSha'] -cmatch '^[0-9a-f]{64}$' -and
        $Execution['cycle'] -ceq [string]$body.cycle -and
        $Execution['observeSeconds'] -ceq [string](Get-131SceneSeconds $body 0) -and $body.quota.maxEpisodes -le 4) 'TRAIN_POLICY_FIXTURE_DRIFT'
    if($body.phase -ceq 'TRAIN'){
        Assert-131Sha (Get-131Path $Context ('candidate-'+$Execution['opponentSha']+'.bin')) $Execution['opponentSha']
    }else{
        Assert-131 ($Execution['trainClosedSha'] -cmatch '^[0-9a-f]{64}$') 'TUNING_TRAIN_ORIGIN_PIN'
    }
    $schedule=Get-131Path $Context 'scenes.tsv'
    Assert-G0PhysicalFile $schedule
    Assert-131 ((Get-Item -LiteralPath $schedule).Length -le 4KB) 'TRAIN_SCENE_FILE_BOUND'
    $rows=[IO.File]::ReadAllLines($schedule,$utf131)
    Assert-131 ($rows.Count -eq $body.quota.maxEpisodes+1 -and $rows[0] -ceq (@('episodeId','profileId','seconds') -join [char]9)) 'TRAIN_SCENE_HEADER'
    $profiles=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    for($index=0;$index -lt $body.quota.maxEpisodes;$index++){
        $cell=$rows[$index+1].Split([char]9)
        $scene=$body.scenes[$index]
        Assert-131 ($cell.Count -eq 3 -and $cell[0] -ceq $scene.id -and $cell[0] -cmatch '^[0-9a-f]{64}$' -and
            $cell[1] -cmatch '^[1-9][0-9]*$' -and $profiles.Add($cell[1]) -and $scene.participantCount -eq 1 -and
            $scene.deadlineSeconds -in (5..180) -and $cell[2] -ceq [string]$scene.deadlineSeconds -and
            $Execution['episode.'+$cell[0]+'.sourceSha'] -cmatch '^[0-9a-f]{64}$' -and
            $Execution['episode.'+$cell[0]+'.policySha'] -cmatch '^[0-9a-f]{64}$') 'TRAIN_SCENE_SCOPE'
    }
}

function Get-131State($Context) {
    $completed=0
    $count=0
    $milliseconds=0L
    $status='READY'
    $parent=$Context.Sha
    $latest=$null
    $close=$null
    $curriculum=0
    if(Test-Path -LiteralPath $Context.Boots){
        Assert-131Directory $Context.Boots
        foreach($directory in @(Get-ChildItem -LiteralPath $Context.Boots -Force|Sort-Object Name)){
            $count++
            Assert-131 ($count -eq 1 -or $status -ceq 'PAUSED') 'BOOT_AFTER_TERMINAL'
            Assert-131 ($count -le $Context.Body.quota.maxBoots -and $directory.PSIsContainer -and $directory.Name -ceq ('{0:00000}' -f $count) -and
                $status -cin @('READY','PAUSED')) 'BOOT_LEDGER_ORDER'
            Assert-131Directory $directory.FullName
            $reservationPath=Join-Path $directory.FullName 'reserved.json'
            $reservation=Read-131Json $reservationPath
            $reservationSha=Get-131Sha $reservationPath
            Assert-131 ($reservation.schema -ceq 'TRAINING_RESERVED131_V1' -and $reservation.sequence -eq $count -and
                $reservation.bootId -ceq $directory.Name -and $reservation.manifestSha -ceq $Context.Sha -and
                $reservation.parentSha -ceq $parent -and $reservation.lastCompleted -eq $completed -and
                $reservation.nonce -cmatch '^[a-f0-9]{32}$' -and $reservation.authoritySha -cmatch '^[a-f0-9]{64}$') 'BOOT_RESERVATION_CHAIN'
            if($curriculum -gt 0){
                $reviewSha=Get-131CurriculumReview $Context $latest $parent
                Assert-131 ($reviewSha -and $reservation.PSObject.Properties['curriculumReviewSha'] -and
                    $reservation.curriculumReviewSha -ceq $reviewSha) 'CURRICULUM_RESERVATION_REVIEW'
            }else{
                Assert-131 (!$reservation.PSObject.Properties['curriculumReviewSha']) 'UNEXPECTED_CURRICULUM_REVIEW'
            }
            $reservedAt=ConvertTo-131Utc $reservation.reservedUtc
            $reservedDeadline=ConvertTo-131Utc $reservation.deadlineUtc
            Assert-131 ($reservedAt -lt $reservedDeadline -and $reservedDeadline -le $Context.Deadline -and
                ($reservedDeadline-$reservedAt).TotalSeconds -lt 28800) 'BOOT_RESERVATION_DEADLINE'
            Assert-131Sha (Join-Path $directory.FullName 'launch131.properties') $reservation.launchSha
            $latest=@{Root=$directory.FullName;Reservation=$reservation;Sha=$reservationSha}
            $closePath=Join-Path $directory.FullName 'closed.json'
            $close=$null
            $curriculum=0
            if(!(Test-Path -LiteralPath $closePath)){$status='UNCLOSED_BOOT';continue}
            $close=Read-131Json $closePath
            $parent=Get-131Sha $closePath
            Assert-131 ($close.schema -ceq 'TRAINING_CLOSED131_V1' -and $close.reservationSha -ceq $reservationSha -and
                $close.status -cin @('PAUSED','COMPLETED','STOPPED','UNKNOWN') -and $close.completed -is [int] -and
                $close.completed -ge $completed -and $close.completed -le $Context.Body.quota.maxEpisodes -and
                [long]$close.nativeMillis -ge 0 -and ($close.status -cne 'UNKNOWN' -or $close.completed -eq $completed) -and
                ($close.status -cne 'COMPLETED' -or $close.completed -eq $Context.Body.quota.maxEpisodes) -and
                $close.masterSha -ceq $Context.Body.configShas.'game/config/Custom/PhantomPlayers.ini' -and
                $close.control -cin @('none','pause','stop','curriculum')) 'BOOT_CLOSE_CHAIN'
            $curriculum=[int](Assert-131ClosedProof $Context $latest $close $completed)
            $completed=$close.completed
            $milliseconds+=[long]$close.nativeMillis
            $status=$close.status
        }
    }
    $stopPath=Get-131Path $Context 'stopped.json'
    if(Test-Path -LiteralPath $stopPath){
        $stop=Read-131Json $stopPath
        Assert-131 ($stop.schema -ceq 'TRAINING_STOPPED131_V1' -and $stop.manifestSha -ceq $Context.Sha -and
            $stop.parentSha -ceq $parent -and $status -cin @('READY','PAUSED')) 'STOP_CHAIN'
        $status='STOPPED'
    }
    return @{Status=$status;Completed=$completed;Boots=$count;Milliseconds=$milliseconds;Parent=$parent;Latest=$latest;Closed=$close;Curriculum=$curriculum}
}

function Get-131CurriculumReview($Context,$Boot,[string]$ClosedSha) {
    $path=Join-Path $Boot.Root 'curriculum-review131.json'
    if(!(Test-Path -LiteralPath $path)){return $null}
    $sha=Get-131Sha $path
    $review=Read-131Json $path
    Assert-131Sha $path $sha
    Assert-131Fields $review @('schema','approval','manifestSha','closedSha','reviewRef')
    Assert-131 ($review.schema -ceq 'TASK131_CURRICULUM_REVIEW_V1' -and $review.approval -ceq 'OWNER_TASK131_CURRICULUM_REVIEW' -and
        $review.manifestSha -ceq $Context.Sha -and $review.closedSha -ceq $ClosedSha -and $review.reviewRef -is [string] -and
        $review.reviewRef -cmatch '^\S[^\r\n]{0,159}$') 'CURRICULUM_REVIEW_SCOPE'
    return $sha
}

function Assert-131SuiteBudget($Context,[long]$Milliseconds) {
    $boots=0
    $used=0L
    foreach($directory in @(Get-ChildItem -LiteralPath $campaigns131 -Directory -Force)){
        Assert-131 ($directory.Name -cmatch '^(?:n01|(?:train|tuning)-0[1-6]|audit-0[12])$') 'UNRECOGNIZED_CAMPAIGN_SLOT'
        $other=if($directory.FullName -ieq $Context.Root){$Context}else{Read-131Manifest (Join-Path $directory.FullName 'campaign.json')}
        $state=Get-131State $other
        Assert-131 ($state.Status -cne 'UNCLOSED_BOOT') 'UNRESOLVED_OWNED_BOOT'
        if($state.Curriculum -gt 0){Assert-131 ([bool](Get-131CurriculumReview $other $state.Latest $state.Parent)) 'CURRICULUM_REVIEW_REQUIRED'}
        $boots+=$state.Boots
        $used+=$state.Milliseconds
    }
    Assert-131 ($boots -lt 12 -and $used+$Milliseconds -lt 28800000) 'SUITE_BOOT_OR_TIME_LIMIT'
}

function Get-131AdmissionDeadline($Context,$State) {
    $seconds=(Get-131SceneSeconds $Context.Body $State.Completed)+$Context.Body.quota.bootSeconds+$Context.Body.quota.drainSeconds
    $deadline=[DateTime]::UtcNow.AddSeconds($seconds)
    Assert-131 ($deadline -le $Context.Deadline) 'REMAINING_SCENES_EXCEED_DEADLINE'
    Assert-131SuiteBudget $Context ($seconds*1000L)
    return $deadline
}

function Write-131Properties([string]$Path,$Body) {
    $lines=foreach($key in $Body.Keys){
        $value=[string]$Body[$key]
        Assert-131 ($value -cnotmatch '[\r\n]' -and $value.Length -gt 0) 'LAUNCH_PROPERTY'
        $key+'='+$value.Replace('\','\\')
    }
    Write-131Text $Path (($lines -join "`n")+"`n")
}

function New-131Reservation($Context,$State,$Boundary,[datetime]$Deadline) {
    if(!(Test-Path -LiteralPath $Context.Boots)){$null=New-Item -ItemType Directory -Path $Context.Boots}
    Assert-131Directory $Context.Boots
    $sequence=$State.Boots+1
    $ordinal='{0:00000}' -f $sequence
    $bootId=$ordinal
    $nonce=[guid]::NewGuid().ToString('N')
    $root=Join-Path $Context.Boots $ordinal
    $stagedRoot=Get-131Path $Context ('.reserved-'+$nonce)
    $null=New-Item -ItemType Directory -Path $stagedRoot
    $stagedLaunch=Join-Path $stagedRoot 'launch131.properties'
    Write-131Properties $stagedLaunch ([ordered]@{schema='TRAINING_LAUNCH131_V1';manifestSha=$Context.Sha;campaignId=$Context.Scope;bootId=$bootId;nonce=$nonce
        phase=$Context.Body.phase;executionSha=$Context.Body.executionSha;deadlineUtc=$Deadline.ToString('o');lastCompleted=$State.Completed;bootRoot=$root
        sqlPid=$Boundary.Sql.Identity.PID;sqlStartedUtc=$Boundary.Sql.Identity.Created;sqlAttestationSha=$Boundary.Sql.AttestationSha})
    $reservation=[ordered]@{schema='TRAINING_RESERVED131_V1';sequence=$sequence;bootId=$bootId;nonce=$nonce;manifestSha=$Context.Sha;parentSha=$State.Parent
        launchSha=(Get-131Sha $stagedLaunch);authoritySha=$Boundary.AuthoritySha;lastCompleted=$State.Completed
        reservedUtc=[DateTime]::UtcNow.ToString('o');deadlineUtc=$Deadline.ToString('o')}
    if($State.Curriculum -gt 0){
        $reservation.curriculumReviewSha=Get-131CurriculumReview $Context $State.Latest $State.Parent
        Assert-131 ([bool]$reservation.curriculumReviewSha) 'CURRICULUM_REVIEW_REQUIRED'
    }
    $reservedPath=Join-Path $stagedRoot 'reserved.json'
    Write-131Json $reservedPath $reservation
    $reservationSha=Get-131Sha $reservedPath
    [IO.Directory]::Move($stagedRoot,$root)
    return @{Root=$root;Reservation=$reservation;Sha=$reservationSha;Launch=(Join-Path $root 'launch131.properties')}
}

function Test-131Request($Boot,[ValidateSet('stop','pause')][string]$Request) {
    $path=Join-Path $Boot.Root ($Request+'131.request')
    if(!(Test-Path -LiteralPath $path)){return $false}
    Assert-G0PhysicalFile $path
    Assert-131 (([IO.File]::ReadAllText($path,$utf131)) -ceq ($Boot.Reservation.nonce+"`n")) 'CONTROL_NONCE'
    return $true
}

function Get-131Request($Boot) {
    if(Test-131Request $Boot 'stop'){return 'stop'}
    if(Test-131Request $Boot 'pause'){return 'pause'}
    return ''
}

function Send-131Request($Boot,[ValidateSet('stop','pause')][string]$Request) {
    $path=Join-Path $Boot.Root ($Request+'131.request')
    if(!(Test-Path -LiteralPath $path)){
        try{Write-131Text $path ($Boot.Reservation.nonce+"`n")}catch [IO.IOException]{
            if(!(Test-Path -LiteralPath $path)){throw}
        }
    }
    $null=Get-131Request $Boot
}

function Get-131Command($Context,$Boundary,$Boot) {
    foreach($path in @($Boundary.Classpath,$Boundary.Execution,$Boot.Launch)){
        Assert-131 ($path -cnotmatch '["\r\n]' -and !$path.EndsWith('\')) 'JVM_ARGUMENT_ENCODING'
    }
    $command=switch -CaseSensitive ($Context.Body.phase){
        'N01' {'--n01'}
        'TRAIN' {'--train'}
        'TUNING' {'--tuning'}
        'AUDIT' {'--audit'}
    }
    $options=@('-XX:ActiveProcessorCount=4','-Xms512m','-Xmx4g','-Djava.util.logging.config.file=log.cfg',
        ('-Dphantom.learning131.nonce='+$Boot.Reservation.nonce))
    if($Context.Body.phase -cin @('TRAIN','TUNING')){$options+=@('-Dphantom.learning.train=true')}
    $options+=@('-cp',('"'+$Boundary.Classpath+'"'),$entry131,$command,('"'+$Boundary.Execution+'"'),('"'+$Boot.Launch+'"'))
    return ($options -join ' ')
}

function Get-131ExpectedCommand($Context,$Boot) {
    $libRoot=Join-Path $Context.Body.runtimeRoot 'libs'
    $cp=@((Join-Path $libRoot 'GameServer.jar'))
    foreach($name in $dependencies131){$cp+=@(Join-Path $libRoot $name)}
    $cp+=@(Get-131Path $Context 'native-classes')
    $boot.Launch=Join-Path $boot.Root 'launch131.properties'
    return Get-131Command $Context @{Classpath=($cp -join ';');Execution=(Get-131Path $Context 'execution.properties')} $Boot
}

function Test-131ProcessCommand($Record,[string]$Command) {
    $pattern='^"?'+[regex]::Escape($Record.executable)+'"?\s+'+[regex]::Escape($Record.arguments)+'\s*$'
    return $Command -cmatch $pattern
}

function Assert-131ProcessRecord($Context,$Boot,$Record) {
    Assert-131 ($Record.schema -ceq 'TRAINING_PROCESS131_V1' -and $Record.nonce -ceq $Boot.Reservation.nonce -and
        $Record.reservationSha -ceq $Boot.Sha -and $Record.pid -is [int] -and $Record.pid -gt 0 -and [long]$Record.created -gt 0 -and
        $Record.controllerPid -is [int] -and $Record.controllerPid -gt 0 -and [long]$Record.controllerCreated -gt 0 -and
        $Record.javaSha -ceq $Context.Body.javaSha -and [IO.Path]::IsPathRooted($Record.executable) -and
        [IO.Path]::GetFileName($Record.executable) -ieq 'java.exe' -and $Record.arguments -ceq (Get-131ExpectedCommand $Context $Boot) -and
        (Test-131ProcessCommand $Record $Record.commandLine)) 'PROCESS_RECEIPT_SCOPE'
}

function Assert-131JavaProcess([Diagnostics.Process]$Process,$Record) {
    Assert-131 ($null -ne $Process -and !$Process.HasExited -and $Process.Id -eq $Record.pid -and
        $Process.StartTime.ToUniversalTime().ToFileTimeUtc() -eq [long]$Record.created) 'TRACKED_JAVA_HANDLE'
    $native=[Win32SqlIdentity]::Process([uint32]$Record.pid,0x1000)
    $processId=[int]$Record.pid
    $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    Assert-131 ($native.Success -and $native.CreatedFileTime -eq [long]$Record.created -and $native.ImagePath -ieq $Record.executable -and
        $null -ne $cim -and $cim.ExecutablePath -ieq $Record.executable -and $cim.ParentProcessId -eq $Record.controllerPid -and
        (Get-G0Microseconds $native.CreatedFileTime) -eq (Get-G0Microseconds $cim.CreationDate.ToUniversalTime().ToFileTimeUtc())) 'TRACKED_JAVA_IDENTITY'
    Assert-131 (Test-131ProcessCommand $Record $cim.CommandLine) 'TRACKED_JAVA_COMMAND'
    $owner=Invoke-CimMethod -InputObject $cim -MethodName GetOwnerSid
    Assert-131 ($owner.ReturnValue -eq 0 -and $owner.Sid -ceq [Security.Principal.WindowsIdentity]::GetCurrent().User.Value) 'TRACKED_JAVA_SID'
    return [string]$cim.CommandLine
}

function Assert-131N01Seal($Context,$Boot,$Ack) {
    $seal=Read-131Properties (Join-Path $Boot.Root 'n01-drain.properties')
    Assert-131 ($seal['schema'] -ceq 'N01_DRAIN131_V1' -and $seal['manifestSha'] -ceq $Context.Sha -and
        $seal['bootId'] -ceq $Boot.Reservation.bootId -and $seal['jarSha'] -ieq $Context.Body.gameJarSha -and
        $seal['status'] -ceq $Ack['status'] -and $seal['completed'] -ceq $Ack['completed'] -and $seal['worldOwnedPlayers'] -ceq '0' -and
        $seal['durableStoredActors'] -ceq '4' -and $seal['minRequiredCycles'] -ceq '3') 'N01_NATIVE_DRAIN_SEAL'
    if($Ack['completed'] -ne '0'){
        Assert-131Sha (Join-Path $Boot.Root 'n01-binding.tsv') $seal['bindingSha']
        Assert-131Sha (Join-Path $Boot.Root 'n01-native-cycles.tsv') $seal['cyclesSha']
        Assert-131Sha (Join-Path $Boot.Root 'n01-native-costs.tsv') $seal['costsSha']
        Assert-131Sha (Join-Path $Boot.Root 'n01-native-loot.tsv') $seal['lootSha']
    }
}

function Get-131Ack($Context,$Boot,$Owner,[int]$ExitCode,[string]$Control='observe') {
    Assert-131 ($ExitCode -eq 0) 'NATIVE_EXIT_CODE'
    $path=Join-Path $Boot.Root 'drain131.properties'
    $ackSha=Get-131Sha $path
    $ack=Read-131Properties $path
    Assert-131Sha $path $ackSha
    $keys=@('schema','nonce','manifestSha','bootId','pid','startedUtc','status','completed','sealSha')
    Assert-131 ($ack.Count -eq $keys.Count -and @($keys|Where-Object {!$ack.ContainsKey($_)}).Count -eq 0) 'DRAIN_ACK_FIELDS'
    $started=ConvertTo-131Utc $ack['startedUtc']
    $created=[DateTime]::FromFileTimeUtc([long]$Owner.created)
    Assert-131 ($ack['schema'] -ceq 'NATIVE_DRAIN131_V1' -and $ack['nonce'] -ceq $Boot.Reservation.nonce -and
        $ack['manifestSha'] -ceq $Context.Sha -and $ack['bootId'] -ceq $Boot.Reservation.bootId -and $ack['pid'] -ceq [string]$Owner.pid -and
        [Math]::Abs(($started-$created).TotalMilliseconds) -le 1 -and $ack['completed'] -cmatch '^(?:0|[1-9][0-9]{0,2})$') 'DRAIN_ACK_IDENTITY'
    $completed=[int]$ack['completed']
    $status=$ack['status']
    $sealName=if($Context.Body.phase -ceq 'N01'){'n01-drain.properties'}else{'complete131.tsv'}
    Assert-131Sha (Join-Path $Boot.Root $sealName) $ack['sealSha']
    $curriculum=0
    if($Context.Body.phase -ceq 'N01'){Assert-131N01Seal $Context $Boot $ack}
    if($Context.Body.phase -cin @('TRAIN','TUNING')){$curriculum=Assert-131TrainSeal $Context $Boot $ack}
    if($Control -ceq 'observe'){
        $Control=Get-131Request $Boot
        if($curriculum -gt 0 -and $Control -cne 'stop'){$Control='curriculum'}
        if(!$Control){$Control='none'}
    }
    Assert-131 (($Control -ceq 'curriculum' -and $curriculum -gt 0) -or
        ($Control -ceq 'none' -and $curriculum -eq 0) -or
        ($Control -ceq 'pause' -and $curriculum -eq 0 -and (Test-131Request $Boot 'pause')) -or
        ($Control -ceq 'stop' -and (Test-131Request $Boot 'stop'))) 'DRAIN_CONTROL_RECEIPT'
    Assert-131 ($completed -ge $Boot.Reservation.lastCompleted -and $completed -le $Context.Body.quota.maxEpisodes -and
        (($status -ceq 'COMPLETED' -and $completed -eq $Context.Body.quota.maxEpisodes) -or
         ($status -ceq 'PAUSED' -and (($curriculum -gt 0 -and $Control -cin @('curriculum','stop')) -or
            ($Control -cin @('pause','stop') -and (Test-131Request $Boot 'pause') -and $completed -lt $Context.Body.quota.maxEpisodes))) -or
         ($status -ceq 'STOPPED' -and $Control -ceq 'stop') -or $status -ceq 'UNKNOWN')) 'DRAIN_ACK_BOUNDARY'
    if($status -ceq 'UNKNOWN'){$completed=$Boot.Reservation.lastCompleted}
    if($Control -ceq 'stop' -and ($status -ceq 'PAUSED' -or $Context.Body.phase -cin @('TRAIN','TUNING') -and $status -ceq 'COMPLETED')){
        $status='STOPPED'
    }
    return @{Status=$status;Completed=$completed;AckSha=$ackSha;SealSha=$ack['sealSha'];Control=$Control;Curriculum=$curriculum}
}

function Test-131NativeClaim($Boot) {
    foreach($name in @('train-java.claim','tuning-java.claim','complete131.tsv','drain131.properties','episodes','train-store131.tsv','tuning-store131.tsv',
            'n01-java.claim','n01-binding.tsv','n01-native-cycles.tsv','n01-native-costs.tsv','n01-native-loot.tsv')){
        if(Test-Path -LiteralPath (Join-Path $Boot.Root $name)){return $true}
    }
    return $false
}

function Assert-131TrainSeal($Context,$Boot,$Ack) {
    $path=Join-Path $Boot.Root 'complete131.tsv'
    Assert-131 ((Get-Item -LiteralPath $path).Length -le 8KB) 'TRAIN_NATIVE_SEAL_BOUND'
    $execution=Get-131Path $Context 'execution.properties'
    Assert-131Sha $execution $Context.Body.executionSha
    $plan=Read-131Properties $execution
    $metadata=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    $episodes=[Collections.Generic.List[string]]::new()
    foreach($line in [IO.File]::ReadAllLines($path,$utf131)){
        $cell=$line.Split([char]9)
        Assert-131 ($cell.Count -eq 3) 'TRAIN_NATIVE_SEAL_FIELDS'
        if($cell[0] -ceq 'meta'){
            Assert-131 (!$metadata.ContainsKey($cell[1])) 'TRAIN_NATIVE_SEAL_DUPLICATE'
            $metadata.Add($cell[1],$cell[2])
        }else{
            $ordinal=$Boot.Reservation.lastCompleted+$episodes.Count
            Assert-131 ($cell[0] -ceq 'episode' -and $ordinal -lt $Context.Body.quota.maxEpisodes -and
                $cell[1] -ceq $Context.Body.scenes[$ordinal].id) 'TRAIN_NATIVE_EPISODE_ORDER'
            $episodes.Add($cell[1])
            Assert-131Sha (Join-Path $Boot.Root ('episodes/'+$cell[1]+'/seal131.tsv')) $cell[2]
        }
    }
    $curriculum=0
    if($metadata.ContainsKey('curriculumMisses')){
        Assert-131 ($Context.Body.phase -ceq 'TRAIN' -and $Ack['status'] -ceq 'PAUSED' -and $episodes.Count -gt 0 -and
            $metadata['curriculumMisses'] -cmatch '^[3-6]$' -and [int]$metadata['curriculumMisses'] -le [int]$Ack['completed']) 'TRAIN_CURRICULUM_PROOF'
        $curriculum=[int]$metadata['curriculumMisses']
    }
    Assert-131 ($metadata.Count -eq $(if($curriculum -gt 0){16}else{15}) -and $metadata.ContainsKey('storeSha')) 'TRAIN_NATIVE_STORE_PIN'
    $phase=$Context.Body.phase
    Assert-131Sha (Join-Path $Boot.Root ($phase.ToLowerInvariant()+'-store131.tsv')) $metadata['storeSha']
    $expectedStatus=if($Ack['status'] -ceq 'COMPLETED'){'DRAINED'}else{$Ack['status']}
    Assert-131 ($metadata['schema'] -ceq ('NATIVE_'+$phase+'_BOOT131_V1') -and $metadata['phase'] -ceq $phase -and
        $metadata['status'] -ceq $expectedStatus -and $metadata['bootId'] -ceq $Boot.Reservation.bootId -and $metadata['nonce'] -ceq $Ack['nonce'] -and
        $metadata['pid'] -ceq $Ack['pid'] -and $metadata['processStarted'] -ceq $Ack['startedUtc'] -and $metadata['manifestSha'] -ceq $Context.Sha -and
        $metadata['sourceCommit'] -ceq $Context.Body.sourceCommit -and $metadata['jarSha'] -ceq $Context.Body.gameJarSha -and
        $metadata['expectedHead'] -ceq $plan['expectedHead'] -and $metadata['learnerSha'] -ceq $Context.Body.modelSha -and
        $metadata['opponentSha'] -ceq $plan['opponentSha'] -and $metadata['cycle'] -ceq [string]$Context.Body.cycle -and
        $Boot.Reservation.lastCompleted+$episodes.Count -eq [int]$Ack['completed']) 'TRAIN_NATIVE_SEAL_DRIFT'
    return $curriculum
}

function Assert-131ClosedProof($Context,$Boot,$Close,[int]$LastCompleted) {
    $owner=$null
    $path=Join-Path $Boot.Root 'process.json'
    if($Close.ownerSha -cne 'NONE'){
        Assert-131Sha $path $Close.ownerSha
        $owner=Read-131Json $path
        Assert-131Sha $path $Close.ownerSha
        Assert-131ProcessRecord $Context $Boot $owner
    }else{Assert-131 (!(Test-Path -LiteralPath $path)) 'UNDECLARED_PROCESS_RECEIPT'}
    if($Close.ackSha -cne 'NONE'){
        Assert-131 ($Close.exitCode -is [int] -and $Close.exitCode -eq 0 -and $null -ne $owner) 'SEALED_EXIT'
        $ack=Get-131Ack $Context $Boot $owner $Close.exitCode $Close.control
        Assert-131 ($ack.Status -ceq $Close.status -and $ack.Completed -eq $Close.completed -and
            $ack.AckSha -ceq $Close.ackSha -and $ack.SealSha -ceq $Close.sealSha) 'CLOSE_ACK_DISAGREES'
        return $ack.Curriculum
    }
    Assert-131 ($Close.completed -eq $LastCompleted -and $Close.sealSha -ceq 'NONE') 'UNSEALED_CREDIT'
    if($Close.status -ceq 'UNKNOWN'){
        Assert-131 ($Close.control -ceq 'none' -or (Test-131Request $Boot $Close.control)) 'UNKNOWN_CONTROL_RECEIPT'
        if($Close.reason -ceq 'NATIVE_DEADLINE_OR_DRAIN_LIMIT'){
            Assert-131 ($Close.control -ceq 'stop' -and $null -ne $owner -and $Close.exitCode -is [int]) 'FORCED_UNKNOWN_SCOPE'
        }
        return
    }
    $expected=if($Close.control -ceq 'pause'){'PAUSED'}elseif($Close.control -ceq 'stop'){'STOPPED'}else{'UNKNOWN'}
    Assert-131 ($Close.status -ceq $expected -and (Test-131Request $Boot $Close.control) -and
        (($Close.reason -ceq 'CONTROL_BEFORE_JAVA' -and $null -eq $owner -and $null -eq $Close.exitCode) -or
         ($Close.reason -ceq 'CONTROL_BEFORE_NATIVE_ADMISSION' -and $Context.Body.phase -cin @('N01','TRAIN','TUNING') -and $null -ne $owner -and
          $Close.exitCode -is [int] -and !(Test-131NativeClaim $Boot)))) 'UNSEALED_CONTROL_BOUNDARY'
}

function Write-131Closed($Context,$Boot,[string]$Status,[int]$Completed,[long]$Millis,$ExitCode,[string]$OwnerSha,[string]$AckSha,[string]$SealSha,[string]$Control,[string]$Reason) {
    Assert-131MasterOff $Context
    $path=Join-Path $Boot.Root 'closed.json'
    Write-131Json $path ([ordered]@{schema='TRAINING_CLOSED131_V1';reservationSha=$Boot.Sha;status=$Status;completed=$Completed;nativeMillis=$Millis
        exitCode=$ExitCode;ownerSha=$OwnerSha;ackSha=$AckSha;sealSha=$SealSha;control=$Control;masterSha=$Context.Body.configShas.'game/config/Custom/PhantomPlayers.ini'
        reason=$Reason;closedUtc=[DateTime]::UtcNow.ToString('o')})
}

function Stop-131HeldJava([Diagnostics.Process]$Process,$Record,[int]$DrainSeconds) {
    if(!$Process.HasExited -and !$Process.WaitForExit($DrainSeconds*1000)){
        try{$null=Assert-131JavaProcess $Process $Record}catch{if(!$Process.HasExited){throw}}
        if(!$Process.HasExited){$Process.Kill()}
        Assert-131 ($Process.WaitForExit(10000)) 'OWNED_JAVA_STOP_UNCONFIRMED'
    }
}

function Invoke-131Boot($Context,$State,$Boundary,[datetime]$Deadline) {
    $boot=New-131Reservation $Context $State $Boundary $Deadline
    $process=$null
    $handle=$null
    $record=$null
    $ownerSha='NONE'
    $exitCode=$null
    $elapsed=0L
    $status='UNKNOWN'
    $done=$State.Completed
    $ackSha='NONE'
    $sealSha='NONE'
    $control='none'
    $reason='UNSEALED_NATIVE_EXIT'
    $failure=$null
    try{
        $request=Get-131Request $boot
        if($request){
            $status=if($request -ceq 'stop'){'STOPPED'}else{'PAUSED'}
            $control=$request
            $reason='CONTROL_BEFORE_JAVA'
        } else {
            $arguments=Get-131Command $Context $Boundary $boot
            Assert-131Sha $Boundary.Java $Boundary.JavaSha
            Assert-131Sha $Context.Manifest $Context.Sha
            Assert-131Sha (Get-131Path $Context 'authorization.json') $Boundary.AuthoritySha
            Assert-131PortsFree
            $process=Start-Process -FilePath $Boundary.Java -ArgumentList $arguments -WorkingDirectory $Boundary.Game -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $boot.Root 'native.out') -RedirectStandardError (Join-Path $boot.Root 'native.err')
            $handle=Hold-ProcessHandle $process
            $record=[ordered]@{schema='TRAINING_PROCESS131_V1';pid=$process.Id;created=$process.StartTime.ToUniversalTime().ToFileTimeUtc()
                executable=$Boundary.Java;javaSha=$Boundary.JavaSha;arguments=$arguments;controllerPid=$PID
                controllerCreated=([Diagnostics.Process]::GetCurrentProcess().StartTime.ToUniversalTime().ToFileTimeUtc());nonce=$boot.Reservation.nonce
                reservationSha=$boot.Sha}
            $record.commandLine=Assert-131JavaProcess $process $record
            Assert-131ProcessRecord $Context $boot $record
            $ownerPath=Join-Path $boot.Root 'process.json'
            Write-131Json $ownerPath $record
            $ownerSha=Get-131Sha $ownerPath
            $elapsedWatch=[Diagnostics.Stopwatch]::StartNew()
            $remainingMillis=[Math]::Max(0,($Deadline-[DateTime]::UtcNow).TotalMilliseconds)
            $watchLimit=$remainingMillis
            $drainMillis=$Context.Body.quota.drainSeconds*1000
            $quiesceAt=[Math]::Max(0,$remainingMillis-$drainMillis)
            $forced=$false
            while(!$process.WaitForExit(250)){
                $request=Get-131Request $boot
                if(!$request -and $Context.Body.phase -ceq 'N01' -and $elapsedWatch.ElapsedMilliseconds -ge $quiesceAt){
                    Send-131Request $boot 'stop'
                    $request='stop'
                }
                if($request -and ($request -cne 'pause' -or $Context.Body.phase -cnotin @('TRAIN','TUNING'))){
                    $watchLimit=[Math]::Min($watchLimit,$elapsedWatch.ElapsedMilliseconds+$drainMillis)
                }
                if($elapsedWatch.ElapsedMilliseconds -ge $watchLimit -or [DateTime]::UtcNow -ge $Deadline){
                    Send-131Request $boot 'stop'
                    $control='stop'
                    $reason='NATIVE_DEADLINE_OR_DRAIN_LIMIT'
                    Stop-131HeldJava $process $record 0
                    $forced=$true
                    break
                }
            }
            $exit=Get-TrainHeldExitProof $record $process $handle
            $exitCode=$exit.exitCode
            $elapsed=$exit.nativeElapsedMillis
            Assert-131PortsFree
            Assert-131Sha $Context.Manifest $Context.Sha
            Assert-131Sha $Boundary.Execution $Context.Body.executionSha
            Assert-131MasterOff $Context
            $request=Get-131Request $boot
            if(!$forced -and $Context.Body.phase -cin @('N01','TRAIN','TUNING') -and $request -and !(Test-131NativeClaim $boot)){
                $status=if($request -ceq 'stop'){'STOPPED'}else{'PAUSED'}
                $control=$request
                $reason='CONTROL_BEFORE_NATIVE_ADMISSION'
            }elseif(!$forced){
                $ack=Get-131Ack $Context $boot $record $exitCode
                $status=$ack.Status
                $done=$ack.Completed
                $ackSha=$ack.AckSha
                $sealSha=$ack.SealSha
                $control=$ack.Control
                $reason='NATIVE_DRAIN_ACK_VERIFIED'
            }
        }
    }catch{
        $failure=$_
    }finally{
        if($null -ne $process){
            try{
                if(!$process.HasExited){
                    Send-131Request $boot 'stop'
                    Stop-131HeldJava $process $record $Context.Body.quota.drainSeconds
                }
                if($null -ne $handle -and $null -ne $record){
                    $exit=Get-TrainHeldExitProof $record $process $handle
                    $exitCode=$exit.exitCode
                    $elapsed=$exit.nativeElapsedMillis
                }
                Assert-131PortsFree
            }finally{$process.Dispose()}
        }
    }
    if($null -ne $failure -and $failure.Exception.Message -cmatch '^TRAINING131_[A-Z0-9_]+$'){$reason=$failure.Exception.Message}
    Write-131Closed $Context $boot $status $done $elapsed $exitCode $ownerSha $ackSha $sealSha $control $reason
    if($null -ne $failure){throw $failure}
}

function Open-131SuiteLock {
    Assert-131Directory $campaigns131
    $path=Join-Path $campaigns131 'suite.lock'
    if(Test-Path -LiteralPath $path){Assert-G0PhysicalFile $path}
    return [IO.FileStream]::new($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
}

function Get-131Summary($Context,$State,[switch]$Detailed) {
    $status=$State.Status
    $bootId=$null
    $processId=$null
    $reason=$null
    if($null -ne $State.Latest){
        $bootId=$State.Latest.Reservation.bootId
        if($status -ceq 'UNCLOSED_BOOT'){
            $request=Get-131Request $State.Latest
            $owner=Join-Path $State.Latest.Root 'process.json'
            if(Test-Path -LiteralPath $owner){
                $record=Read-131Json $owner
                Assert-131ProcessRecord $Context $State.Latest $record
                $processId=$record.pid
            }
            if($request){$status='DRAIN_REQUESTED'}
        } else {$reason=$State.Closed.reason}
    }
    $summary=[ordered]@{schema='TRAINING_STATUS131_V1';campaignId=$Context.Scope;phase=$Context.Body.phase;manifestSha=$Context.Sha;status=$status
        bootId=$bootId;lastRecordedPid=$processId;completed=$State.Completed;remaining=$Context.Body.quota.maxEpisodes-$State.Completed;boots=$State.Boots
        bootsRemaining=$Context.Body.quota.maxBoots-$State.Boots;resumable=(Test-131Resumable $Context $State)
        chargedNativeMillis=$State.Milliseconds;deadlineUtc=$Context.Deadline.ToString('o');reason=$reason}
    if($State.Curriculum -gt 0){$summary.curriculumMisses=$State.Curriculum}
    if($Context.Body.phase -cin @('TRAIN','TUNING') -and $status -ceq 'PAUSED' -and !$summary.resumable){
        $summary.resumeReason=$Context.Body.phase+'_BOOT_QUOTA_OR_CLOSURE_REQUIRED'
        if($State.Curriculum -gt 0 -and !(Get-131CurriculumReview $Context $State.Latest $State.Parent)){
            $summary.resumeReason='TRAIN_CURRICULUM_REVIEW_REQUIRED'
        }
    }
    if($Detailed){
        $summary.sourceCommit=$Context.Body.sourceCommit
        $summary.gameJarSha=$Context.Body.gameJarSha
        $summary.policySha=$Context.Body.policySha
        $summary.modelSha=$Context.Body.modelSha
        $summary.fixtureSha=$Context.Body.fixtureSha
        $summary.dbSealSha=$Context.Body.dbSealSha
        $summary.ledgerHeadSha=$State.Parent
        $summary.quality='REQUIRES_INDEPENDENT_AUDIT'
    }
    return $summary
}

function Test-131Resumable($Context,$State) {
    if($State.Status -cne 'PAUSED' -or $State.Boots -ge $Context.Body.quota.maxBoots -or $State.Completed -ge $Context.Body.quota.maxEpisodes){
        return $false
    }
    if($State.Curriculum -gt 0 -and !(Get-131CurriculumReview $Context $State.Latest $State.Parent)){return $false}
    if($Context.Body.phase -ceq 'N01'){return $State.Completed -eq 0 -and !(Test-131NativeClaim $State.Latest)}
    if($Context.Body.phase -cin @('TRAIN','TUNING') -and (Test-131NativeClaim $State.Latest)){
        return $State.Closed.ackSha -cne 'NONE' -and $State.Closed.sealSha -cne 'NONE'
    }
    return $true
}

function Get-131ReservedMillis($Boot) {
    $start=ConvertTo-131Utc $Boot.Reservation.reservedUtc
    $deadline=ConvertTo-131Utc $Boot.Reservation.deadlineUtc
    return [long][Math]::Ceiling(($deadline-$start).TotalMilliseconds)
}

function Assert-131UnclaimedBootExpired($Context,$Boot) {
    Assert-131 ($Context.Body.phase -cin @('N01','TRAIN','TUNING') -and [DateTime]::UtcNow -ge (ConvertTo-131Utc $Boot.Reservation.deadlineUtc) -and
        !(Test-131NativeClaim $Boot) -and !(Test-Path -LiteralPath (Join-Path $Boot.Root 'drain131.properties'))) 'UNCLAIMED_BOOT_MANUAL_OWNERSHIP_REQUIRED'
    $needle='-Dphantom.learning131.nonce='+$Boot.Reservation.nonce
    foreach($candidate in @(Get-CimInstance Win32_Process -Filter "Name='java.exe'")){
        Assert-131 ($candidate.CommandLine -and !$candidate.CommandLine.Contains($needle)) 'UNCLAIMED_BOOT_MANUAL_OWNERSHIP_REQUIRED'
    }
    Assert-131PortsFree
}

function Close-131OrphanBoot($Context,$PriorState) {
    try{$lock=Open-131SuiteLock}catch [IO.IOException]{return Get-131State $Context}
    try{
        $state=Get-131State $Context
        if($state.Status -cne 'UNCLOSED_BOOT'){return $state}
        $boot=$state.Latest
        Assert-131 ($boot.Sha -ceq $PriorState.Latest.Sha -and (Get-131Request $boot) -ceq 'stop') 'ORPHAN_BOOT_SCOPE'
        $ownerPath=Join-Path $boot.Root 'process.json'
        if(!(Test-Path -LiteralPath $ownerPath)){
            Assert-131UnclaimedBootExpired $Context $boot
            Write-131Closed $Context $boot 'UNKNOWN' $state.Completed (Get-131ReservedMillis $boot) $null 'NONE' 'NONE' 'NONE' 'stop' 'EXPIRED_UNCLAIMED_BOOT_WITHOUT_CREDIT'
            return Get-131State $Context
        }
        $record=Read-131Json $ownerPath
        Assert-131ProcessRecord $Context $boot $record
        $java=(Get-Command java.exe -CommandType Application|Select-Object -First 1).Source
        Assert-131 ($java -ieq $record.executable -and $record.javaSha -ceq $Context.Body.javaSha) 'ORPHAN_EXECUTABLE'
        Assert-131Sha $java $record.javaSha
        Initialize-TrainWin32
        $controller=Get-Process -Id $record.controllerPid -ErrorAction SilentlyContinue
        if($controller){
            try{Assert-131 ($controller.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$record.controllerCreated) 'ORIGINAL_CONTROLLER_STILL_LIVE'}
            finally{$controller.Dispose()}
        }
        $child=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
        $nativeMillis=Get-131ReservedMillis $boot
        $exitCode=$null
        if($child){
            try{
                if($child.StartTime.ToUniversalTime().ToFileTimeUtc() -eq [long]$record.created){
                    $held=Hold-ProcessHandle $child
                    Stop-131HeldJava $child $record 0
                    $exit=Get-TrainHeldExitProof $record $child $held
                    $exitCode=$exit.exitCode
                    $nativeMillis=$exit.nativeElapsedMillis
                }
            }finally{$child.Dispose()}
        }
        Assert-131PortsFree
        Write-131Closed $Context $boot 'UNKNOWN' $state.Completed $nativeMillis $exitCode (Get-131Sha $ownerPath) 'NONE' 'NONE' 'stop' 'ORPHAN_RECONCILED_WITHOUT_CREDIT'
        return Get-131State $Context
    }finally{$lock.Dispose()}
}

function Invoke-131Control($Context,[ValidateSet('stop','pause')][string]$Request) {
    $state=Get-131State $Context
    if($state.Status -ceq 'UNCLOSED_BOOT'){
        Send-131Request $state.Latest $Request
        $watch=[Diagnostics.Stopwatch]::StartNew()
        do{
            Start-Sleep -Milliseconds 150
            $state=Get-131State $Context
        }while($state.Status -ceq 'UNCLOSED_BOOT' -and $watch.Elapsed.TotalSeconds -lt $Context.Body.quota.drainSeconds)
        if($Request -ceq 'stop' -and $state.Status -ceq 'UNCLOSED_BOOT'){$state=Close-131OrphanBoot $Context $state}
        return Get-131Summary $Context $state
    }
    if($Request -ceq 'stop' -and $state.Status -cin @('READY','PAUSED')){
        $lock=Open-131SuiteLock
        try{
            $state=Get-131State $Context
            Assert-131 ($state.Status -cin @('READY','PAUSED')) 'STOP_STATE_CHANGED'
            Write-131Json (Get-131Path $Context 'stopped.json') ([ordered]@{schema='TRAINING_STOPPED131_V1';manifestSha=$Context.Sha;parentSha=$state.Parent})
            $state=Get-131State $Context
        }finally{$lock.Dispose()}
    }
    return Get-131Summary $Context $state
}

$context131=Read-131Manifest $Manifest
switch ($Action){
    'Status' {Get-131Summary $context131 (Get-131State $context131)|ConvertTo-Json -Depth 8}
    'Report' {Get-131Summary $context131 (Get-131State $context131) -Detailed|ConvertTo-Json -Depth 8}
    'Preflight' {
        $state131=Get-131State $context131
        Assert-131 ($state131.Status -cin @('READY','PAUSED') -and $state131.Boots -lt $context131.Body.quota.maxBoots -and
            ($state131.Status -ceq 'READY' -or (Test-131Resumable $context131 $state131))) 'PREFLIGHT_STATE_OR_QUOTA'
        $boundary131=Get-131Boundary $context131
        $null=Get-131AdmissionDeadline $context131 $state131
        [ordered]@{schema='TRAINING_PREFLIGHT131_V1';campaignId=$context131.Scope;manifestSha=$context131.Sha;status='READY';sqlPid=$boundary131.Sql.Identity.PID;
            sourceCommit=$context131.Body.sourceCommit;gameJarSha=$context131.Body.gameJarSha;sqlAttestationSha=$boundary131.Sql.AttestationSha;
            completed=$state131.Completed;boots=$state131.Boots}|ConvertTo-Json -Depth 8
    }
    'Pause' {Invoke-131Control $context131 'pause'|ConvertTo-Json -Depth 8}
    'Stop' {Invoke-131Control $context131 'stop'|ConvertTo-Json -Depth 8}
    default {
        $lock131=Open-131SuiteLock
        try{
            $state131=Get-131State $context131
            $required131=if($Action -ieq 'Start'){'READY'}else{'PAUSED'}
            Assert-131 ($state131.Status -ceq $required131 -and $state131.Boots -lt $context131.Body.quota.maxBoots -and
                $state131.Completed -lt $context131.Body.quota.maxEpisodes) 'BOOT_ADMISSION_STATE_OR_QUOTA'
            Assert-131 ($Action -ieq 'Start' -or (Test-131Resumable $context131 $state131)) 'RESUME_NATIVE_BOUNDARY_UNAVAILABLE'
            $boundary131=Get-131Boundary $context131
            if($null -ne $state131.Latest){Assert-131 ($state131.Latest.Reservation.authoritySha -ceq $boundary131.AuthoritySha) 'AUTHORITY_CHANGED_ON_RESUME'}
            $deadline131=Get-131AdmissionDeadline $context131 $state131
            Invoke-131Boot $context131 $state131 $boundary131 $deadline131
            Get-131Summary $context131 (Get-131State $context131) -Detailed|ConvertTo-Json -Depth 8
        }finally{$lock131.Dispose()}
    }
}
