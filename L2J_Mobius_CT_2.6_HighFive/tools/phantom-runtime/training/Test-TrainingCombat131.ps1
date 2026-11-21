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
[IO.File]::WriteAllText($trainManifest,($body|ConvertTo-Json -Depth 8),$encoding)
try{
    $null=& $scriptPath -Action Preflight -Manifest $trainManifest
    throw 'UNEXPECTED_TRAIN_PREFLIGHT_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_NATIVE_ADAPTER_PHASE_UNAVAILABLE') 'absent TRAIN adapter cannot report readiness'
}

function Write-FixtureReceipt([string]$Name,$Value) {
    $path=Join-Path $boot $Name
    $text=if($Value -is [string]){$Value}else{$Value|ConvertTo-Json -Depth 8}
    [IO.File]::WriteAllText($path,$text,$encoding)
    return (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
}

# The only readback from this fabricated receipt chain must be a refusal.
$body.configShas=@{'game/config/Custom/PhantomPlayers.ini'=$sha}
[IO.File]::WriteAllText($trainManifest,($body|ConvertTo-Json -Depth 8),$encoding)
$manifestSha=(Get-FileHash -LiteralPath $trainManifest).Hash.ToLowerInvariant()
$nonce='c'*32
$boot=Join-Path $train 'boots\00001'
$null=New-Item -ItemType Directory -Path $boot
$launchSha=Write-FixtureReceipt 'launch131.properties' "nonce=$nonce`n"
$reservedSha=Write-FixtureReceipt 'reserved.json' @{schema='TRAINING_RESERVED131_V1';sequence=1;bootId='00001';manifestSha=$manifestSha
    parentSha=$manifestSha;lastCompleted=0;nonce=$nonce;authoritySha=$sha;launchSha=$launchSha
    reservedUtc=$now.AddMinutes(-2).ToString('o');deadlineUtc=$now.AddMinutes(-1).ToString('o')}
$libraries=@('GameServer.jar','HikariCP-7.0.2.jar','mysql-connector-j-9.5.0.jar','slf4j-api-2.0.17.jar','slf4j-simple-2.0.17.jar')
$classpath=@($libraries|ForEach-Object {Join-Path $body.runtimeRoot ('libs\'+$_)})+@(Join-Path $train 'native-classes')
$executable=Join-Path $sandbox 'java.exe'
$argumentText=('-XX:ActiveProcessorCount=4 -Xms512m -Xmx4g -Djava.util.logging.config.file=log.cfg -Dphantom.learning131.nonce='+$nonce+
    ' -cp "'+($classpath -join ';')+'" org.l2jmobius.tests.phantoms.PhantomLearning131Native --train "'+
    (Join-Path $train 'execution.properties')+'" "'+(Join-Path $boot 'launch131.properties')+'"')
$owner=[ordered]@{schema='TRAINING_PROCESS131_V1';pid=131;created=$now.ToFileTimeUtc();executable=$executable;javaSha=$sha
    arguments=$argumentText;commandLine=('"'+$executable+'" '+$argumentText);controllerPid=132;controllerCreated=$now.ToFileTimeUtc()
    nonce=$nonce;reservationSha=$reservedSha}
$ownerSha=Write-FixtureReceipt 'process.json' $owner
$sealSha=Write-FixtureReceipt 'complete131.tsv' "fabricated test input`n"
$null=Write-FixtureReceipt 'pause131.request' ($nonce+"`n")
$ackSha=Write-FixtureReceipt 'drain131.properties' ((@('schema=NATIVE_DRAIN131_V1',"nonce=$nonce","manifestSha=$manifestSha",'bootId=00001',
    'pid=131',('startedUtc='+$now.ToString('o')),'status=PAUSED','completed=0',"sealSha=$sealSha") -join "`n")+"`n")
$null=Write-FixtureReceipt 'closed.json' @{schema='TRAINING_CLOSED131_V1';reservationSha=$reservedSha;status='COMPLETED';completed=4
    nativeMillis=1;exitCode=0;ownerSha=$ownerSha;ackSha=$ackSha;sealSha=$sealSha;control='pause';masterSha=$sha;reason='NATIVE_DRAIN_ACK_VERIFIED'}
try{
    $null=& $scriptPath -Action Report -Manifest $trainManifest
    throw 'UNEXPECTED_DRAIN_SUBSTITUTION_SUCCESS'
}catch{
    Assert-Check ($_.Exception.Message -ceq 'TRAINING131_CLOSE_ACK_DISAGREES') 'an exact PAUSED acknowledgement cannot credit completion'
}

Write-Output "TRAINING131_CONTROL_CHECKS_PASS GS=0 LS=0 SQL=0 artifacts=$sandbox"
