# DB-free contract checks. The copied controller has no runtime, Java classpath or SQL lane.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'

function Assert-Check([bool]$Condition,[string]$Name) {
    if(!$Condition){throw "TRAINING131_CHECK_FAILED $Name"}
}

$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$taskLogs=Join-Path $repository '.phantom-local\logs\TASK131'
$sandbox=Join-Path $taskLogs ('control-contract-'+[guid]::NewGuid().ToString('N'))
$training=Join-Path $sandbox 'HighFive\tools\phantom-runtime\training'
$campaign=Join-Path $sandbox '.phantom-local\logs\TASK131\campaigns\n01'
$null=New-Item -ItemType Directory -Path $training,$campaign
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'TrainingCombat131.ps1'),(Join-Path $PSScriptRoot 'TrainingOwner.ps1') -Destination $training
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\G0SqlIdentity.ps1') -Destination (Split-Path $training -Parent)

$scriptPath=Join-Path $training 'TrainingCombat131.ps1'
$manifest=Join-Path $campaign 'campaign.json'
$sha='a'*64
$now=[DateTime]::UtcNow
$body=[ordered]@{schema='TRAINING_COMBAT131_V1';campaignId='n01';phase='N01';cycle=0;auditBank=0;sourceCommit='a'*40
    runtimeRoot=(Join-Path $sandbox '.phantom-local\logs\TASK131\native-runtime');sqlLane='n01-setup-r999';javaSha=$sha;gameJarSha=$sha
    classPinsSha=$sha;executionSha=$sha;safetyReviewSha=$sha;fixtureSha=$sha;policySha=$sha;modelSha='NONE';dbSealSha=$sha;jdbcSha=$sha
    dependencyShas=@{};configShas=@{};quota=@{maxBoots=2;maxEpisodes=4;bootSeconds=30;drainSeconds=10;deadlineUtc=$now.AddMinutes(10).ToString('o')}
    scenes=@(1..4|ForEach-Object {@{id="n01-scene-$_";participantCount=1;deadlineSeconds=5}})}
$encoding=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($manifest,($body|ConvertTo-Json -Depth 8),$encoding)

$status=& $scriptPath -Action Status -Manifest $manifest | ConvertFrom-Json
Assert-Check ($status.status -ceq 'READY' -and $status.completed -eq 0 -and $status.remaining -eq 4 -and $status.boots -eq 0) 'ready counters'
Assert-Check (@(Get-ChildItem -LiteralPath $campaign -Force).Count -eq 1) 'status is read only'
try{
    $null=& $scriptPath -Action Preflight -Manifest $manifest
    throw 'UNEXPECTED_PREFLIGHT_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -match 'authorization\.json') 'preflight requires owner authority'
}
Assert-Check (@(Get-ChildItem -LiteralPath $campaign -Force).Count -eq 1) 'preflight is read only'

function Write-PreflightPin([string]$Path,[string]$Content) {
    $null=New-Item -ItemType Directory -Force -Path (Split-Path $Path -Parent)
    [IO.File]::WriteAllText($Path,$Content,$encoding)
    return (Get-FileHash -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Publish-PreflightInput {
    $properties=($executionPlan.Keys|ForEach-Object { $_+'='+([string]$executionPlan[$_]).Replace('\','\\') }) -join "`n"
    $body.executionSha=Write-PreflightPin (Join-Path $campaign 'execution.properties') ($properties+"`n")
    $manifestSha=Write-PreflightPin $manifest ($body|ConvertTo-Json -Depth 8)
    $null=Write-PreflightPin (Join-Path $campaign 'authorization.json') (@{schema='TASK131_LAUNCH_AUTHORITY_V1';authority='OWNER_TASK131_GOAL'
        scope='OWNED_TEST131';manifestSha=$manifestSha;authorityRef='DB-free contract fixture'}|ConvertTo-Json)
}

function Assert-PreflightRefused([string]$Message,[string]$Name) {
    Publish-PreflightInput
    try{
        $null=& $scriptPath -Action Preflight -Manifest $manifest
        throw 'UNEXPECTED_PIN_PREFLIGHT_SUCCESS'
    }catch{
        Assert-Check ($_.Exception.Message -match $Message) $Name
    }
}

# Only bytes and paths are exercised. The owned SQL directory is deliberately absent.
$runtimeRoot=$body.runtimeRoot
$body.javaSha=(Get-FileHash -LiteralPath ((Get-Command java.exe -CommandType Application|Select-Object -First 1).Source)).Hash.ToLowerInvariant()
$body.safetyReviewSha=Write-PreflightPin (Join-Path $campaign 'safety-review024.json') (@{schema='TASK131_SAFETY_REVIEW024_V1'
    scope='HISTORICAL_024_LINEAGE_ONLY';latch='PAUSED_SAFETY_REVIEW';latchSha='3ab3ac9453d631a07261938e11526cfb45cf45dcfcccc1b940642ddd130c7da9'
    checkpointSha='90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6';proofSha='4e0be150b671f0ad68ddb1b0719695b5dc6cc55bbeaff8adb10c6506dddec414'}|ConvertTo-Json)
$body.gameJarSha=Write-PreflightPin (Join-Path $runtimeRoot 'libs/GameServer.jar') 'DB-free JAR pin'
foreach($dependency in @('HikariCP-7.0.2.jar','mysql-connector-j-9.5.0.jar','slf4j-api-2.0.17.jar','slf4j-simple-2.0.17.jar')){
    $body.dependencyShas[$dependency]=Write-PreflightPin (Join-Path $runtimeRoot ('libs/'+$dependency)) ('DB-free '+$dependency)
}
$classRoot=Join-Path $campaign 'native-classes'
$adapterPath='org/l2jmobius/tests/phantoms/PhantomLearning131Native.class'
$factoryPath='org/l2jmobius/gameserver/phantoms/PhantomLearning131Factory.class'
$adapterSha=Write-PreflightPin (Join-Path $classRoot $adapterPath) 'DB-free adapter pin'
$factorySha=Write-PreflightPin (Join-Path $classRoot $factoryPath) 'DB-free factory pin'
$body.classPinsSha=Write-PreflightPin (Join-Path $campaign 'class-pins.tsv') "$adapterSha`t$adapterPath`n$factorySha`t$factoryPath`n"
$jdbc="URL=jdbc:mysql://127.0.0.1:33331/l2jmobiush5_phantom_test_131?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000`nLogin=task131_runtime`nDriver=com.mysql.cj.jdbc.Driver`nBackupDatabase=false`n"
$ini=@{
    'game/config/Server.ini'="GameserverHostname=127.0.0.1`nGameserverPort=33332`nLoginHost=127.0.0.1`nLoginPort=33333`n"
    'game/config/Database.ini'=$jdbc
    'game/config/Custom/PhantomPlayers.ini'="EnablePhantomSystem=False`nLearningMode=OFF`n"
    'game/config/General.ini'="LogItems=True`nLogItemsSmallLog=False`nLogItemsIdsOnly=False`n"
    'game/config/Player.ini'="AutoLoot=False`n"
    'login/config/Server.ini'="LoginserverHostname=127.0.0.1`nLoginserverPort=2106`nLoginHostname=127.0.0.1`nLoginPort=33333`n"
    'game/log.cfg'='DB-free log config pin'
}
foreach($file in $ini.Keys){$body.configShas[$file]=Write-PreflightPin (Join-Path $runtimeRoot $file) $ini[$file]}
$body.jdbcSha=$body.configShas.'game/config/Database.ini'
$body.policySha=Write-PreflightPin (Join-Path $runtimeRoot 'game/config/Custom/PhantomPlayers.n01.ini') "EnablePhantomSystem=True`nLearningMode=OFF`n"
$executionPlan=[ordered]@{schema='TRAINING_EXECUTION131_V1';phase='N01';sourceCommit=$body.sourceCommit;campaignId='n01';sqlLane=$body.sqlLane
    jdbcSha=$body.jdbcSha;gameJarSha=$body.gameJarSha;maxEpisodes=4;drainSeconds=10;runtimeRoot=$runtimeRoot;gameJar=(Join-Path $runtimeRoot 'libs/GameServer.jar')
    fixtureSha=$body.fixtureSha;sceneSha=('b'*64);sceneScopeSha=('c'*64);enabledPhantomIniSha=$body.policySha;observeSeconds=5;maxCyclesPerActor=3
    factoryClassSha=$factorySha;adapterClassSha=$adapterSha}
Assert-PreflightRefused 'sql-task131' 'distinct fixture, scenes and canonical factory pins reach the absent SQL boundary'
$body.fixtureSha=$executionPlan.sceneSha
Assert-PreflightRefused '^TRAINING131_N01_POLICY_FIXTURE_DRIFT$' 'scenes SHA cannot replace the fixture SHA'
$body.fixtureSha=$executionPlan.fixtureSha
$body.configShas.'game/config/Database.ini'=Write-PreflightPin (Join-Path $runtimeRoot 'game/config/Database.ini') ($jdbc.Replace('l2jmobiush5_phantom_test_131','foreign_schema'))
$executionPlan.jdbcSha=$body.jdbcSha=$body.configShas.'game/config/Database.ini'
Assert-PreflightRefused '^TRAINING131_RUNTIME_CONFIG_SCOPE$' 'a correctly hashed wrong JDBC schema is refused before SQL'
$body.configShas.'game/config/Database.ini'=Write-PreflightPin (Join-Path $runtimeRoot 'game/config/Database.ini') $jdbc
$executionPlan.jdbcSha=$body.jdbcSha=$body.configShas.'game/config/Database.ini'
$master='game/config/Custom/PhantomPlayers.ini'
foreach($different in @("EnablePhantomSystem=True`nLearningMode=OFF`n","EnablePhantomSystem=False`nLearningMode=TRAIN`n")){
    $body.configShas[$master]=Write-PreflightPin (Join-Path $runtimeRoot $master) $different
    Assert-PreflightRefused '^TRAINING131_RUNTIME_CONFIG_SCOPE$' 'a correctly hashed active master or learning config is refused'
}
$body.configShas[$master]=Write-PreflightPin (Join-Path $runtimeRoot $master) $ini[$master]
Publish-PreflightInput

$body.quota.maxEpisodes=5
[IO.File]::WriteAllText($manifest,($body|ConvertTo-Json -Depth 8),$encoding)
try{
    $null=& $scriptPath -Action Status -Manifest $manifest
    throw 'UNEXPECTED_QUOTA_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_PHASE_QUOTA') 'phase quota cannot be enlarged'
}
$body.quota.maxEpisodes=4
[IO.File]::WriteAllText($manifest,($body|ConvertTo-Json -Depth 8),$encoding)

$stopped=& $scriptPath -Action Stop -Manifest $manifest | ConvertFrom-Json
$report=& $scriptPath -Action Report -Manifest $manifest | ConvertFrom-Json
Assert-Check ($stopped.status -ceq 'STOPPED' -and $report.status -ceq 'STOPPED' -and $report.boots -eq 0 -and $report.remaining -eq 4) 'immutable pre-boot stop'
try{
    $null=& $scriptPath -Action Resume -Manifest $manifest
    throw 'UNEXPECTED_RESUME_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_BOOT_ADMISSION_STATE_OR_QUOTA') 'stopped phase cannot renew its quota'
}

$body.sourceCommit='b'*40
[IO.File]::WriteAllText($manifest,($body|ConvertTo-Json -Depth 8),$encoding)
try{
    $null=& $scriptPath -Action Report -Manifest $manifest
    throw 'UNEXPECTED_SUBSTITUTION_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_STOP_CHAIN') 'manifest substitution rejected'
}

$train=Join-Path (Split-Path $campaign -Parent) 'train-01'
$null=New-Item -ItemType Directory -Path $train
$trainManifest=Join-Path $train 'campaign.json'
$body.campaignId='train-01'
$body.phase='TRAIN'
$body.cycle=1
$body.modelSha=$sha
$previousSqlLane=$body.sqlLane
$body.sqlLane='foreign-scope'
[IO.File]::WriteAllText($trainManifest,($body|ConvertTo-Json -Depth 8),$encoding)
try{
    $null=& $scriptPath -Action Preflight -Manifest $trainManifest
    throw 'UNEXPECTED_TRAIN_PREFLIGHT_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_NATIVE_SQL_SETUP_LANE') 'TRAIN preflight cannot cross its owned setup lane'
}
$body.sqlLane=$previousSqlLane

$tuning=Join-Path (Split-Path $campaign -Parent) 'tuning-01'
$null=New-Item -ItemType Directory -Path $tuning
$tuningManifest=Join-Path $tuning 'campaign.json'
$body.campaignId='tuning-01'
$body.phase='TUNING'
$body.quota.maxEpisodes=3
$body.scenes=@($body.scenes | Select-Object -First 3)
$body.sqlLane='foreign-scope'
[IO.File]::WriteAllText($tuningManifest,($body|ConvertTo-Json -Depth 8),$encoding)
try{
    $null=& $scriptPath -Action Preflight -Manifest $tuningManifest
    throw 'UNEXPECTED_TUNING_PREFLIGHT_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_NATIVE_SQL_SETUP_LANE') 'TUNING preflight cannot cross its owned setup lane'
}
$body.sqlLane=$previousSqlLane

function Write-FixtureReceipt([string]$Name,$Value) {
    $path=Join-Path $boot $Name
    $text=if($Value -is [string]){$Value}else{$Value|ConvertTo-Json -Depth 8}
    [IO.File]::WriteAllText($path,$text,$encoding)
    return (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
}

# The only readback from this fabricated receipt chain must be a refusal.
$body.configShas=@{'game/config/Custom/PhantomPlayers.ini'=$sha}
$body.executionSha=Write-PreflightPin (Join-Path $tuning 'execution.properties') (@("expectedHead=$sha","opponentSha=$sha",'') -join [char]10)
[IO.File]::WriteAllText($tuningManifest,($body|ConvertTo-Json -Depth 8),$encoding)
$manifestSha=(Get-FileHash -LiteralPath $tuningManifest).Hash.ToLowerInvariant()
$nonce='c'*32
$boot=Join-Path $tuning 'boots\00001'
$null=New-Item -ItemType Directory -Path $boot
$launchSha=Write-FixtureReceipt 'launch131.properties' "nonce=$nonce`n"
$reservedSha=Write-FixtureReceipt 'reserved.json' @{schema='TRAINING_RESERVED131_V1';sequence=1;bootId='00001';manifestSha=$manifestSha
    parentSha=$manifestSha;lastCompleted=0;nonce=$nonce;authoritySha=$sha;launchSha=$launchSha
    reservedUtc=$now.AddMinutes(-2).ToString('o');deadlineUtc=$now.AddMinutes(-1).ToString('o')}
$libraries=@('GameServer.jar','HikariCP-7.0.2.jar','mysql-connector-j-9.5.0.jar','slf4j-api-2.0.17.jar','slf4j-simple-2.0.17.jar')
$classpath=@($libraries|ForEach-Object {Join-Path $body.runtimeRoot ('libs\'+$_)})+@(Join-Path $tuning 'native-classes')
$executable=Join-Path $sandbox 'java.exe'
$argumentText=('-XX:ActiveProcessorCount=4 -Xms512m -Xmx4g -Djava.util.logging.config.file=log.cfg -Dphantom.learning131.nonce='+$nonce+
    ' -Dphantom.learning.train=true -cp "'+($classpath -join ';')+'" org.l2jmobius.tests.phantoms.PhantomLearning131Native --tuning "'+
    (Join-Path $tuning 'execution.properties')+'" "'+(Join-Path $boot 'launch131.properties')+'"')
$owner=[ordered]@{schema='TRAINING_PROCESS131_V1';pid=131;created=$now.ToFileTimeUtc();executable=$executable;javaSha=$body.javaSha
    arguments=$argumentText;commandLine=('"'+$executable+'" '+$argumentText);controllerPid=132;controllerCreated=$now.ToFileTimeUtc()
    nonce=$nonce;reservationSha=$reservedSha}
$ownerSha=Write-FixtureReceipt 'process.json' $owner
$tuningStoreSha=Write-FixtureReceipt 'tuning-store131.tsv' 'synthetic controller contract store'
$tuningMeta=[ordered]@{schema='NATIVE_TUNING_BOOT131_V1';phase='TUNING';status='PAUSED';bootId='00001';nonce=$nonce;pid='131'
    processStarted=$now.ToString('o');manifestSha=$manifestSha;sourceCommit=$body.sourceCommit;jarSha=$body.gameJarSha
    expectedHead=$sha;learnerSha=$body.modelSha;opponentSha=$sha;cycle='1';storeSha=$tuningStoreSha}
$tuningRows=@($tuningMeta.Keys|ForEach-Object {@('meta',$_,$tuningMeta[$_]) -join [char]9})
$sealSha=Write-FixtureReceipt 'complete131.tsv' (($tuningRows -join [char]10)+[char]10)
$null=Write-FixtureReceipt 'pause131.request' ($nonce+"`n")
$ackSha=Write-FixtureReceipt 'drain131.properties' ((@('schema=NATIVE_DRAIN131_V1',"nonce=$nonce","manifestSha=$manifestSha",'bootId=00001',
    'pid=131',('startedUtc='+$now.ToString('o')),'status=PAUSED','completed=0',"sealSha=$sealSha") -join "`n")+"`n")
$null=Write-FixtureReceipt 'closed.json' @{schema='TRAINING_CLOSED131_V1';reservationSha=$reservedSha;status='COMPLETED';completed=3
    nativeMillis=1;exitCode=0;ownerSha=$ownerSha;ackSha=$ackSha;sealSha=$sealSha;control='pause';masterSha=$sha;reason='NATIVE_DRAIN_ACK_VERIFIED'}
try{
    $null=& $scriptPath -Action Report -Manifest $tuningManifest
    throw 'UNEXPECTED_DRAIN_SUBSTITUTION_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_CLOSE_ACK_DISAGREES') 'an exact PAUSED acknowledgement cannot credit completion'
}

# Exercise the controller's byte protocol in this disposable directory. No receipt is native evidence.
$body.campaignId='train-01'
$body.phase='TRAIN'
$body.quota.maxEpisodes=2
$trainIds=@(('b'*64),('d'*64))
$body.scenes=@($trainIds | ForEach-Object {@{id=$_;participantCount=1;deadlineSeconds=5}})
$body.executionSha=Write-PreflightPin (Join-Path $train 'execution.properties') (@("expectedHead=$sha","opponentSha=$sha",'') -join [char]10)
$trainManifestSha=Write-PreflightPin $trainManifest ($body|ConvertTo-Json -Depth 8)

function Write-TrainClosure([int]$Index,[int]$Prior,[string]$Parent,[string]$Status,[string[]]$Episode,[switch]$Curriculum) {
    $bootId='{0:00000}' -f $Index
    $boot=Join-Path $train ('boots\'+$bootId)
    $null=New-Item -ItemType Directory -Path $boot
    $nonce=if($Index -eq 1){'e'*32}else{'f'*32}
    $stamp=$now.AddSeconds($Index)
    $launchSha=Write-FixtureReceipt 'launch131.properties' "nonce=$nonce"
    $reservedSha=Write-FixtureReceipt 'reserved.json' @{schema='TRAINING_RESERVED131_V1';sequence=$Index;bootId=$bootId;manifestSha=$trainManifestSha
        parentSha=$Parent;lastCompleted=$Prior;nonce=$nonce;authoritySha=$sha;launchSha=$launchSha
        reservedUtc=$stamp.AddSeconds(-5).ToString('o');deadlineUtc=$stamp.AddSeconds(5).ToString('o')}
    $classpath=@($libraries|ForEach-Object {Join-Path $body.runtimeRoot ('libs\'+$_)})+@(Join-Path $train 'native-classes')
    $argumentText=('-XX:ActiveProcessorCount=4 -Xms512m -Xmx4g -Djava.util.logging.config.file=log.cfg -Dphantom.learning131.nonce='+$nonce+
        ' -Dphantom.learning.train=true -cp "'+($classpath -join ';')+'" org.l2jmobius.tests.phantoms.PhantomLearning131Native --train "'+
        (Join-Path $train 'execution.properties')+'" "'+(Join-Path $boot 'launch131.properties')+'"')
    $ownerSha=Write-FixtureReceipt 'process.json' @{schema='TRAINING_PROCESS131_V1';pid=131;created=$stamp.ToFileTimeUtc();executable=$executable;javaSha=$body.javaSha
        arguments=$argumentText;commandLine=('"'+$executable+'" '+$argumentText);controllerPid=132;controllerCreated=$stamp.ToFileTimeUtc()
        nonce=$nonce;reservationSha=$reservedSha}
    $storePath=Join-Path $boot 'train-store131.tsv'
    $storeSha=Write-FixtureReceipt 'train-store131.tsv' 'controller contract store bytes'
    $control=if($Curriculum){'curriculum'}elseif($Status -ceq 'PAUSED'){'pause'}else{'none'}
    if($control -ceq 'pause'){$null=Write-FixtureReceipt 'pause131.request' ($nonce+[char]10)}
    $sealStatus=if($Status -ceq 'COMPLETED'){'DRAINED'}else{$Status}
    $metadata=[ordered]@{schema='NATIVE_TRAIN_BOOT131_V1';phase='TRAIN';status=$sealStatus;bootId=$bootId;nonce=$nonce;pid='131'
        processStarted=$stamp.ToString('o');manifestSha=$trainManifestSha;sourceCommit=$body.sourceCommit;jarSha=$body.gameJarSha
        expectedHead=$sha;learnerSha=$body.modelSha;opponentSha=$sha;cycle=[string]$body.cycle;storeSha=$storeSha}
    if($Curriculum){$metadata.curriculumMisses='3'}
    $sealRows=@($metadata.Keys|ForEach-Object { @('meta',$_,$metadata[$_]) -join [char]9 })
    foreach($id in $Episode){
        $episodeSha=Write-PreflightPin (Join-Path $boot ('episodes\'+$id+'\seal131.tsv')) 'controller contract episode bytes'
        $sealRows+=@(@('episode',$id,$episodeSha) -join [char]9)
    }
    $sealSha=Write-FixtureReceipt 'complete131.tsv' (($sealRows -join [char]10)+[char]10)
    $completed=$Prior+$Episode.Count
    $ackSha=Write-FixtureReceipt 'drain131.properties' ((@('schema=NATIVE_DRAIN131_V1',"nonce=$nonce","manifestSha=$trainManifestSha","bootId=$bootId",
        'pid=131',('startedUtc='+$stamp.ToString('o')),"status=$Status","completed=$completed","sealSha=$sealSha") -join [char]10)+[char]10)
    $closeSha=Write-FixtureReceipt 'closed.json' @{schema='TRAINING_CLOSED131_V1';reservationSha=$reservedSha;status=$Status;completed=$completed
        nativeMillis=1;exitCode=0;ownerSha=$ownerSha;ackSha=$ackSha;sealSha=$sealSha;control=$control;masterSha=$sha;reason='NATIVE_DRAIN_ACK_VERIFIED'}
    return @{Close=$closeSha;Store=$storePath}
}

$paused=Write-TrainClosure 1 0 $trainManifestSha 'PAUSED' $trainIds[0]
$report=& $scriptPath -Action Report -Manifest $trainManifest | ConvertFrom-Json
Assert-Check ($report.status -ceq 'PAUSED' -and $report.resumable -and $report.completed -eq 1 -and $report.remaining -eq 1) 'sealed prefix is resumable'
[IO.File]::WriteAllText($paused.Store,'modified checkpoint',$encoding)
try{
    $null=& $scriptPath -Action Report -Manifest $trainManifest
    throw 'UNEXPECTED_STORE_DRIFT_SUCCESS'
}catch{Assert-Check ($_.Exception.Message -ceq 'TRAINING131_PIN_DRIFT') 'changed checkpoint bytes are refused'}
[IO.File]::WriteAllText($paused.Store,'controller contract store bytes',$encoding)
$null=Write-TrainClosure 2 1 $paused.Close 'COMPLETED' $trainIds[1]
$report=& $scriptPath -Action Report -Manifest $trainManifest | ConvertFrom-Json
Assert-Check ($report.status -ceq 'COMPLETED' -and !$report.resumable -and $report.completed -eq 2 -and $report.boots -eq 2) 'new boot closes only its remaining scene'

$lastBoot=Join-Path $train 'boots\00002'
$lastClose=Join-Path $lastBoot 'closed.json'
$terminal=Get-Content -Raw -LiteralPath $lastClose | ConvertFrom-Json
$null=Write-PreflightPin (Join-Path $lastBoot 'stop131.request') (('f'*32)+[char]10)
$terminal.control='stop'
$null=Write-PreflightPin $lastClose ($terminal | ConvertTo-Json -Depth 4)
try{
    $null=& $scriptPath -Action Report -Manifest $trainManifest
    throw 'UNEXPECTED_COMPLETED_WITH_STOP_SUCCESS'
}catch{Assert-Check ($_.Exception.Message -ceq 'TRAINING131_CLOSE_ACK_DISAGREES') 'completed label cannot override accepted stop'}
$terminal.status='STOPPED'
$null=Write-PreflightPin $lastClose ($terminal | ConvertTo-Json -Depth 4)
$report=& $scriptPath -Action Report -Manifest $trainManifest | ConvertFrom-Json
Assert-Check ($report.status -ceq 'STOPPED' -and !$report.resumable -and $report.completed -eq 2) 'stop preserves settled credit and closes the branch'

$train=Join-Path (Split-Path $train -Parent) 'train-02'
$trainManifest=Join-Path $train 'campaign.json'
$body.campaignId='train-02'
$body.cycle=2
$body.quota.maxEpisodes=4
$diagnosticIds=@(('1'*64),('2'*64),('3'*64),('4'*64))
$body.scenes=@($diagnosticIds | ForEach-Object {@{id=$_;participantCount=1;deadlineSeconds=5}})
$body.executionSha=Write-PreflightPin (Join-Path $train 'execution.properties') (@("expectedHead=$sha","opponentSha=$sha",'') -join [char]10)
$trainManifestSha=Write-PreflightPin $trainManifest ($body|ConvertTo-Json -Depth 8)
$held=Write-TrainClosure 1 0 $trainManifestSha 'PAUSED' $diagnosticIds[0..2] -Curriculum
$report=& $scriptPath -Action Report -Manifest $trainManifest | ConvertFrom-Json
Assert-Check ($report.status -ceq 'PAUSED' -and !$report.resumable -and $report.completed -eq 3 -and $report.remaining -eq 1 -and
    $report.resumeReason -ceq 'TRAIN_CURRICULUM_REVIEW_REQUIRED') 'native curriculum pause charges the three scenes and holds admission'
$reviewPath=Join-Path $train 'boots\00001\curriculum-review131.json'
$review=[ordered]@{schema='TASK131_CURRICULUM_REVIEW_V1';approval='OWNER_TASK131_CURRICULUM_REVIEW';manifestSha=$trainManifestSha
    closedSha=('0'*64);reviewRef='controller contract fixture review'}
$null=Write-PreflightPin $reviewPath ($review | ConvertTo-Json -Depth 4)
try{
    $null=& $scriptPath -Action Report -Manifest $trainManifest
    throw 'UNEXPECTED_FOREIGN_CURRICULUM_REVIEW_SUCCESS'
}catch{Assert-Check ($_.Exception.Message -ceq 'TRAINING131_CURRICULUM_REVIEW_SCOPE') 'review for a different closure is refused'}
$review.closedSha=$held.Close
$null=Write-PreflightPin $reviewPath ($review | ConvertTo-Json -Depth 4)
$report=& $scriptPath -Action Report -Manifest $trainManifest | ConvertFrom-Json
Assert-Check ($report.resumable -and $report.completed -eq 3 -and $report.remaining -eq 1 -and $report.boots -eq 1) 'accepted review opens only the existing remaining quota'

$auditSandbox=Join-Path $taskLogs ('control-contract-audit-'+[guid]::NewGuid().ToString('N'))
$auditTraining=Join-Path $auditSandbox 'HighFive\tools\phantom-runtime\training'
$auditTask=Join-Path $auditSandbox '.phantom-local\logs\TASK131'
$null=New-Item -ItemType Directory -Path $auditTraining
Copy-Item -LiteralPath (Join-Path $training 'TrainingCombat131.ps1'),(Join-Path $training 'TrainingOwner.ps1') -Destination $auditTraining
Copy-Item -LiteralPath (Join-Path (Split-Path $training -Parent) 'G0SqlIdentity.ps1') -Destination (Split-Path $auditTraining -Parent)
$auditController=Join-Path $auditTraining 'TrainingCombat131.ps1'
$auditRoot=Join-Path $auditTask 'campaigns\audit-01'
$auditManifest=Join-Path $auditRoot 'campaign.json'
$audit=[ordered]@{}
foreach($key in $body.Keys){$audit[$key]=$body[$key]}
$audit.campaignId='audit-01'
$audit.phase='AUDIT'
$audit.cycle=0
$audit.auditBank=1
$audit.runtimeRoot=Join-Path $auditTask 'native-runtime'
$audit.modelSha='b'*64
$audit.quota=@{maxBoots=1;maxEpisodes=128;bootSeconds=30;drainSeconds=10;deadlineUtc=[DateTime]::UtcNow.AddMinutes(9).ToString('o')}
$audit.scenes=@(0..127|ForEach-Object {@{id="audit-slot-$_";participantCount=1;deadlineSeconds=1}})
$audit.safetyReviewSha=Write-PreflightPin (Join-Path $auditRoot 'safety-review024.json') (Get-Content -LiteralPath (Join-Path $campaign 'safety-review024.json') -Raw)
$auditPin=Write-PreflightPin $auditManifest ($audit|ConvertTo-Json -Depth 8)
$null=Write-PreflightPin (Join-Path $auditRoot 'authorization.json') (@{schema='TASK131_LAUNCH_AUTHORITY_V1';authority='OWNER_TASK131_GOAL'
    scope='OWNED_TEST131';manifestSha=$auditPin;authorityRef='DB-free AUDIT preflight fixture'}|ConvertTo-Json)
try{
    $null=& $auditController -Action Preflight -Manifest $auditManifest
    throw 'UNEXPECTED_MISSING_AUDIT_PLAN_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_AUDIT_PREFLIGHT_INPUT_ABSENT') 'AUDIT preflight reaches the frozen input boundary'
}
Assert-Check (@(Get-ChildItem -LiteralPath $auditRoot -Force).Count -eq 3) 'AUDIT preflight cannot reserve a boot or change files'

# Exercise the controller's actual metadata and slot checks using a bounded offline verifier response.
$audit.configShas=[ordered]@{}
$audit.gameJarSha=Write-PreflightPin (Join-Path $audit.runtimeRoot 'libs/GameServer.jar') 'DB-free AUDIT JAR pin'
[string[]]$configNames=@($ini.Keys)
[Array]::Sort($configNames,[StringComparer]::Ordinal)
$configLines=foreach($name in $configNames){
    $audit.configShas[$name]=Write-PreflightPin (Join-Path $audit.runtimeRoot $name) $ini[$name]
    "$name`t$($audit.configShas[$name])"
}
$audit.jdbcSha=$audit.configShas.'game/config/Database.ini'
$hash=[Security.Cryptography.SHA256]::Create()
try{$configSha=[BitConverter]::ToString($hash.ComputeHash($encoding.GetBytes("TRAINING_AUDIT_RUNTIME_CONFIG131_V1`n"+($configLines -join "`n")+"`n"))).Replace('-','').ToLowerInvariant()}
finally{$hash.Dispose()}
$evaluation=Join-Path $auditRoot 'evaluation.tsv'
$evaluationSha=Write-PreflightPin $evaluation 'DB-free evaluator handoff fixture'
$audit.executionSha=Write-PreflightPin (Join-Path $auditRoot 'execution.properties') "schema=TRAINING_AUDIT_PREFLIGHT131_V1`nevaluationManifestSha=$evaluationSha`n"
$null=Write-PreflightPin (Join-Path $auditTraining 'TrainingEvaluation131.ps1') @'
param([switch]$Admission,[string]$Manifest,[string]$ExpectedManifestSha)
if(!$Admission -or (Get-FileHash -LiteralPath $Manifest).Hash.ToLowerInvariant() -cne $ExpectedManifestSha){exit 1}
Get-Content -LiteralPath ($Manifest+'.admission-witness.tsv')
exit 0
'@
$admissionMeta=[ordered]@{schema='AUDIT_ADMISSION131_V1';bank='bank-1';manifestSha=$evaluationSha;protocolSha=$audit.fixtureSha;sourceSha=$audit.sourceCommit
    jarSha=$audit.gameJarSha;configSha=$configSha;policySha=$audit.policySha;candidateSha=$audit.modelSha;splitSha=('c'*64);evaluatorSha=('d'*64)
    candidateHead=('e'*64);referenceSha=('f'*64);referenceHead=('1'*64);lockedAt=$now.AddMinutes(-1).ToString('o')}
$admissionRows=@($admissionMeta.Keys|ForEach-Object {"meta`t$_`t$($admissionMeta[$_])"})+
    @($audit.scenes|ForEach-Object {"slot`t$($_.id)`t$($_.participantCount)`t$($_.deadlineSeconds)"})
$null=Write-PreflightPin ($evaluation+'.admission-witness.tsv') (($admissionRows -join "`n")+"`n")

function Publish-AuditContractManifest {
    $pin=Write-PreflightPin $auditManifest ($audit|ConvertTo-Json -Depth 8)
    $null=Write-PreflightPin (Join-Path $auditRoot 'authorization.json') (@{schema='TASK131_LAUNCH_AUTHORITY_V1';authority='OWNER_TASK131_GOAL'
        scope='OWNED_TEST131';manifestSha=$pin;authorityRef='DB-free AUDIT preflight fixture'}|ConvertTo-Json)
}
Publish-AuditContractManifest
$planned=& $auditController -Action Preflight -Manifest $auditManifest | ConvertFrom-Json
Assert-Check ($planned.status -ceq 'FROZEN_INPUTS_VERIFIED' -and !$planned.nativeExecutable -and $planned.scheduledSlots -eq 128 -and $planned.boots -eq 0) 'AUDIT preflight is explicit about its verified inputs and unavailable native runner'
try{
    $null=& $auditController -Action Start -Manifest $auditManifest
    throw 'UNEXPECTED_AUDIT_START_SUCCESS'
}catch{Assert-Check ($_.Exception.Message -ceq 'TRAINING131_NATIVE_ADAPTER_PHASE_UNAVAILABLE') 'verified inputs cannot open the native AUDIT gate'}
$audit.scenes[0].participantCount=2
Publish-AuditContractManifest
try{
    $null=& $auditController -Action Preflight -Manifest $auditManifest
    throw 'UNEXPECTED_AUDIT_SLOT_DRIFT_SUCCESS'
}catch{Assert-Check ($_.Exception.Message -ceq 'TRAINING131_AUDIT_ADMISSION_SCHEDULE_DRIFT') 'campaign cannot change a frozen slot roster'}
Assert-Check (!(Test-Path -LiteralPath (Join-Path $auditRoot 'boots'))) 'neither read-only AUDIT path consumes a boot'

Write-Output "TRAINING131_CONTROL_CHECKS_PASS GS=0 LS=0 SQL=0 artifacts=$sandbox auditArtifacts=$auditSandbox"
