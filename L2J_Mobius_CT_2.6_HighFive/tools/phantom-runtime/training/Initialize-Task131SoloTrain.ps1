<#
.SYNOPSIS
Prepare one offline Solo TRAIN cycle from a complete owned N01 fixture.
.DESCRIPTION
Requires reviewed runtime bytes, a sealed four-scene SETUP, verified stored learned-skill rows,
the exact current TASK131 continuation HEAD, and actual frozen learner/opponent files.
Preflight is read-only. Prepare emits controller inputs only after canonical Java preflight.
The tool creates neither launch authorization nor server processes.
#>
[CmdletBinding()]
param(
    [ValidateSet('Preflight','Prepare')][string]$Action='Preflight',
    [Parameter(Mandatory)][ValidatePattern('^n01-setup-r[1-9][0-9]*$')][string]$SqlLane,
    [ValidateRange(1,6)][int]$Cycle=1,
    [string]$RuntimeReceiptSha='',
    [string]$LearnedWitnessSha='',
    [string]$ExpectedHead='',
    [string]$LearnerSha='',
    [string]$OpponentSha='',
    [string]$SafetyReviewPath='',
    [string]$SafetyReviewSha='',
    [string]$DeadlineUtc='',
    [long[]]$Profiles=@(),
    [ValidateRange(5,180)][int]$SecondsPerEpisode=120,
    [ValidateRange(1,12)][int]$MaxBoots=2,
    [ValidateRange(1,300)][int]$BootSeconds=180,
    [ValidateRange(1,120)][int]$DrainSeconds=90,
    [ValidateRange(1,2147483647)][int]$Seed=131,
    [ValidateRange(1,16)][int]$ExplorationCap=4,
    [ValidateRange(0.05,0.20)][double]$Epsilon=0.1
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'G0SqlIdentity.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$task=Join-Path (Split-Path $module -Parent) '.phantom-local\logs\TASK131'
$sql=Join-Path $task "sql-lanes\$SqlLane\sql-task131"
$campaigns=Join-Path $task 'campaigns'
$n01=Join-Path $campaigns 'n01'
$campaignId='train-{0:00}' -f $Cycle
$campaign=Join-Path $campaigns $campaignId
$ledger=Join-Path $task 'fit-ledger'
$utf8=[Text.UTF8Encoding]::new($false,$true)

function Assert-Solo([bool]$Condition,[string]$Reason) {
    if(!$Condition){throw "TASK131_SOLO_TRAIN_$Reason"}
}

function Assert-SoloDirectory([string]$Path) {
    $dir=Get-Item -LiteralPath $Path -Force
    Assert-Solo ($dir.PSIsContainer -and $dir.FullName -ieq $Path) 'PHYSICAL_DIRECTORY'
    for($part=$dir;$null -ne $part;$part=$part.Parent){Assert-Solo (!($part.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'REPARSE_PATH'}
}

function Get-SoloSha([string]$Path) {
    Assert-G0PhysicalFile $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-SoloSha([string]$Path,[string]$Expected) {
    Assert-Solo ($Expected -cmatch '^[a-f0-9]{64}$' -and (Get-SoloSha $Path) -ceq $Expected) 'PIN_MISMATCH'
}

function Get-SoloTextSha([string]$Text) {
    $digest=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($digest.ComputeHash($utf8.GetBytes($Text)))).Replace('-','').ToLowerInvariant()}
    finally{$digest.Dispose()}
}

function Read-SoloText([string]$Path) {
    Assert-G0PhysicalFile $Path
    Assert-Solo ((Get-Item -LiteralPath $Path).Length -le 1MB) 'METADATA_BYTES'
    return [IO.File]::ReadAllText($Path,$utf8)
}

function Read-SoloProperties([string]$Path) {
    $result=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($row in (Read-SoloText $Path) -split '\r?\n'){
        if($row -eq ''){continue}
        Assert-Solo ($row -cmatch '^([a-zA-Z][a-zA-Z0-9.]*)=(.+)$' -and !$result.ContainsKey($Matches[1])) 'PROPERTIES'
        $result.Add($Matches[1],$Matches[2].Replace('\\','\'))
    }
    return ,$result
}

function Write-SoloText([string]$Path,[string]$Text) {
    $contents=$utf8.GetBytes($Text)
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$stream.Write($contents,0,$contents.Length)}finally{$stream.Dispose()}
}

function Write-SoloProperties([string]$Path,$Body) {
    $rows=foreach($key in $Body.Keys){
        $value=[string]$Body[$key]
        Assert-Solo ($value -cnotmatch '[\x00-\x1f]') 'PROPERTY_VALUE'
        $key+'='+$value.Replace('\','\\')
    }
    Write-SoloText $Path (($rows -join "`n")+"`n")
}

function Assert-SoloOff {
    Assert-Solo (@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()|Where-Object Port -in 2106,33331,33332,33333).Count -eq 0) 'OWN_PORTS_NOT_OFF'
}

function Read-SoloTable([string]$Path,[string]$Header,[int]$MaxRows) {
    $lines=(Read-SoloText $Path).TrimEnd("`r","`n") -split '\r?\n'
    Assert-Solo ($lines.Count -le $MaxRows+1 -and $lines[0] -ceq $Header) 'TABLE_HEADER_OR_SIZE'
    $size=$Header.Split([char]9).Length
    foreach($line in $lines|Select-Object -Skip 1){Assert-Solo ($line.Split([char]9).Length -eq $size) 'TABLE_COLUMNS'}
    return @($lines | ConvertFrom-Csv -Delimiter "`t")
}

function Get-SoloFixture {
    Assert-SoloDirectory $sql
    $fixture=Join-Path $sql 'n01-fixture.tsv'
    $scenes=Join-Path $sql 'n01-scenes.tsv'
    $actors=@(Read-SoloTable $fixture ("status`tprofileId`tobjectId`taccount`trole`tclass`tlevel`tskillId`tskillLevel`tweapon`tshotItem`tshots`tadena`tanchor`tnpcId`tspawn`tpreflightObjectId`tx`ty`tz`texp`tsp") 4)
    $targets=@(Read-SoloTable $scenes ("profileId`tactorObjectId`tgoalId`tgoalRevision`tplanIdentity`tanchor`tx`ty`tz`tnpcId`tnpcLevel`tspawnKey`trespawnMinMs`trespawnMaxMs`tpreflightObjectId`tpreflightInstanceId`tpreflightGeneration`tqualifyingTargets`tknowledgeSha256`ttopologySha256`tprogressionSha256`tcommerceSha256") 4)
    Assert-Solo ($actors.Count -eq 4 -and $targets.Count -eq 4 -and @($actors|Where-Object status -cne 'SETUP_STOCK_CANDIDATE').Count -eq 0) 'INCOMPLETE_SCENE_SET'
    $scope=Join-Path $sql 'n01-scene-scope.properties'
    $fixtureSha=Get-SoloSha $fixture
    $sceneSha=Get-SoloSha $scenes
    $scopeSha=Get-SoloSha $scope
    $seal=Read-SoloProperties $scope
    Assert-Solo ($seal['stage'] -ceq 'SETUP_CANDIDATES' -and $seal['fixtureSha256'] -ieq $fixtureSha -and $seal['sceneSha256'] -ieq $sceneSha -and
        $seal['profiles'] -ceq ($actors.profileId -join ',') -and $seal['nextBootMustBindNativeTargets'] -ceq 'true') 'SETUP_SEAL'
    return @{Actors=$actors;Targets=$targets;FixtureSha=$fixtureSha;SceneSha=$sceneSha;ScopeSha=$scopeSha}
}

function Get-SoloRuntime($Fixture) {
    $meta=Join-Path $n01 'candidate-pins.json'
    Assert-SoloSha $meta $RuntimeReceiptSha
    $receipt=Read-SoloText $meta | ConvertFrom-Json
    $pins=$receipt.controllerPins
    $execution=Join-Path $n01 'execution.properties'
    Assert-SoloSha $execution $pins.executionSha
    $exe=Read-SoloProperties $execution
    $runtime=$receipt.runtimeRoot
    Assert-Solo ($receipt.schema -ceq 'TASK131_N01_OFFLINE_CANDIDATE_V1' -and $receipt.status -ceq 'STAGED_OFFLINE' -and $receipt.launchAuthorized -eq $false -and
        $receipt.sqlLane -ceq $SqlLane -and $receipt.sceneSha -ceq $Fixture.SceneSha -and $receipt.sceneScopeSha -ceq $Fixture.ScopeSha -and
        $pins.fixtureSha -ceq $Fixture.FixtureSha -and $exe['runtimeRoot'] -ieq $runtime -and (Split-Path $runtime -Parent) -ieq $task -and
        (Split-Path $runtime -Leaf) -cmatch '^native-runtime-r[1-9][0-9]*$') 'N01_RUNTIME_SCOPE'
    $readback=@(Get-ChildItem -LiteralPath $task -File -Filter 'github-readback-v*.json'|Where-Object {(Get-SoloSha $_.FullName) -ceq $receipt.githubReadbackSha})
    Assert-Solo ($readback.Count -eq 1 -and $readback[0].Name -cmatch '^github-readback-(v[1-9][0-9]*)\.json$') 'REVIEWED_BUILD_SOURCE'
    $build='build-'+$Matches[1]+'-reviewed'
    $recheck=[ordered]@{
        Action='Attest';SqlLane=$SqlLane;RuntimeId=(Split-Path $runtime -Leaf);AuditedBuild=$build;GithubReadback=$readback[0].Name
        GithubReadbackSha=$receipt.githubReadbackSha;GameJarSha=$pins.gameJarSha;LoginJarSha=$receipt.loginJarSha
        AdapterClassSha=$exe['adapterClassSha'];FactoryClassSha=$exe['factoryClassSha'];MaxCyclesPerActor=[int]$exe['maxCyclesPerActor']
        ObserveSeconds=[int]$exe['observeSeconds'];DrainSeconds=[int]$exe['drainSeconds']
    }
    $attested=@(& (Join-Path (Split-Path $PSScriptRoot -Parent) 'Initialize-Task131N01Runtime.ps1') @recheck)
    Assert-Solo ($attested.Count -eq 1 -and $attested[0] -cmatch '^TASK131_N01_SETUP_ATTESTED scenes=4 ') 'N01_ATTESTATION'
    Assert-SoloSha $meta $RuntimeReceiptSha
    return @{Root=$runtime;Pins=$pins;Execution=$exe;ClassRoot=(Join-Path $n01 'native-classes');ClassPins=(Join-Path $n01 'class-pins.tsv');OffSha=$receipt.sqlOffSha}
}

function Get-SoloPlans($Fixture) {
    # Future SETUP must export exact post-STORE native rows. XML class ownership alone is not a learnt rank receipt.
    $witness=Join-Path $sql 'n01-learned-offense.tsv'
    Assert-Solo (Test-Path -LiteralPath $witness -PathType Leaf) 'LEARNED_OFFENSE_WITNESS_ABSENT'
    Assert-SoloSha $witness $LearnedWitnessSha
    $learned=@(Read-SoloTable $witness ("profileId`tobjectId`tclass`tlevel`tskillId`tskillLevel`tprovenance`tfixtureSha`tsceneScopeSha") 64)
    Assert-Solo ($learned.Count -ge 2) 'LEARNED_OFFENSE_WITNESS_EMPTY'
    $byProfile=@{}
    foreach($row in $learned){
        $owner=@($Fixture.Actors|Where-Object profileId -ceq $row.profileId)
        Assert-Solo ($owner.Count -eq 1 -and $row.objectId -ceq $owner[0].objectId -and $row.class -ceq $owner[0].class -and $row.level -ceq $owner[0].level -and
            $row.provenance -ceq 'STORED_CLASS_DAMAGE' -and $row.skillId -cmatch '^[1-9][0-9]*$' -and $row.skillLevel -cmatch '^[1-9][0-9]*$' -and
            $row.fixtureSha -ceq $Fixture.FixtureSha -and $row.sceneScopeSha -ceq $Fixture.ScopeSha) 'LEARNED_OFFENSE_SCOPE'
        if(!$byProfile.ContainsKey($row.profileId)){$byProfile[$row.profileId]=[Collections.Generic.Dictionary[int,int]]::new()}
        Assert-Solo (!$byProfile[$row.profileId].ContainsKey([int]$row.skillId)) 'LEARNED_RANK_DUPLICATE'
        $byProfile[$row.profileId].Add([int]$row.skillId,[int]$row.skillLevel)
    }
    $chosen=@($Fixture.Actors|Where-Object {$Profiles.Count -eq 0 -or [long]$_.profileId -in $Profiles})
    Assert-Solo ($chosen.Count -in (1..4) -and ($Profiles.Count -eq 0 -or $Profiles.Count -eq $chosen.Count)) 'SELECTED_PROFILE_SCOPE'
    $index=0
    foreach($actor in $chosen){
        $stock=@($Fixture.Targets|Where-Object profileId -ceq $actor.profileId)
        Assert-Solo ($stock.Count -eq 1 -and $stock[0].actorObjectId -ceq $actor.objectId -and $stock[0].planIdentity -cmatch '^[a-f0-9]{64}$') 'PLAN_FIXTURE_IDENTITY'
        $skills=$byProfile[$actor.profileId]
        Assert-Solo ($null -ne $skills -and $skills.Count -in (2..16) -and $skills.ContainsKey([int]$actor.skillId) -and
            $skills[[int]$actor.skillId] -eq [int]$actor.skillLevel) 'LEARNED_BASELINE_OR_ALTERNATIVES_UNPROVED'
        $tokens=@($skills.Keys|Sort-Object|ForEach-Object {"${_}:$($skills[$_])"}) -join ','
        $source=Get-SoloTextSha ("TASK131_SOLO_SOURCE_V1`n"+($Fixture.FixtureSha,$Fixture.SceneSha,$Fixture.ScopeSha,$LearnedWitnessSha,$actor.profileId,$stock[0].planIdentity -join "`n")+"`n")
        $episode=Get-SoloTextSha ("TASK131_SOLO_TRAIN_V1`n"+($campaignId,$source,$ExpectedHead,$LearnerSha,$OpponentSha -join "`n")+"`n")
        [pscustomobject]@{Id=$episode;Profile=$actor.profileId;Seconds=$SecondsPerEpisode;Source=$source;Skills=$tokens;Seed=([long]$Seed+$index)}
        $index++
    }
}

function Get-SoloSafetyReview {
    Assert-Solo ([IO.Path]::IsPathRooted($SafetyReviewPath) -and [IO.Path]::GetFullPath($SafetyReviewPath) -ieq $SafetyReviewPath -and
        $SafetyReviewPath.StartsWith($task+'\',[StringComparison]::OrdinalIgnoreCase)) 'SAFETY_REVIEW_SCOPE'
    Assert-SoloSha $SafetyReviewPath $SafetyReviewSha
    $review=Read-SoloText $SafetyReviewPath | ConvertFrom-Json
    Assert-Solo ($review.schema -ceq 'TASK131_SAFETY_REVIEW024_V1' -and $review.scope -ceq 'HISTORICAL_024_LINEAGE_ONLY' -and $review.latch -ceq 'PAUSED_SAFETY_REVIEW' -and
        $review.latchSha -ceq '3ab3ac9453d631a07261938e11526cfb45cf45dcfcccc1b940642ddd130c7da9' -and
        $review.checkpointSha -ceq '90e66051a80f5c36c0c4c727089708f13a8f29cd6a11a1b36712e6084132add6' -and
        $review.proofSha -ceq '4e0be150b671f0ad68ddb1b0719695b5dc6cc55bbeaff8adb10c6506dddec414') 'SAFETY_REVIEW_024'
}

function Get-SoloClassPath($Runtime) {
    $java=(Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
    Assert-SoloSha $java $Runtime.Pins.javaSha
    Assert-Solo ((Get-Item -LiteralPath $java).VersionInfo.ProductMajorPart -eq 25) 'JDK25'
    foreach($variable in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){
        Assert-Solo ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($variable,'Process'))) 'JAVA_OPTION_INJECTION'
    }
    $files=@(Join-Path $Runtime.Root 'libs\GameServer.jar')
    Assert-SoloSha $files[0] $Runtime.Pins.gameJarSha
    foreach($item in $Runtime.Pins.dependencyShas.PSObject.Properties){
        $file=Join-Path $Runtime.Root ('libs\'+$item.Name)
        Assert-SoloSha $file $item.Value
        $files+=@($file)
    }
    foreach($component in @($java)+$files+@($Runtime.ClassRoot)){
        Assert-Solo ($component -cnotmatch '[";\r\n]' -and !$component.EndsWith('\')) 'JVM_PATH_QUOTING'
    }
    return @{Java=$java;Libraries=$files}
}

function Get-SoloPlanPins($Plans,$Java,[string]$EpsilonText) {
    $helper=Join-Path $PSScriptRoot 'PhantomLearning131PlanPins.java'
    $helperSha=Get-SoloSha $helper
    $rows=@("episodeId`tsourceSha`tseed`tcap`tepsilon`tskills")
    foreach($plan in $Plans){$rows+=@(@($plan.Id,$plan.Source,$plan.Seed,$ExplorationCap,$EpsilonText,$plan.Skills) -join "`t")}
    $out=@($rows | & $Java.Java --source=25 -cp ($Java.Libraries -join ';') $helper $ledger $ExpectedHead $LearnerSha $OpponentSha)
    Assert-Solo ($LASTEXITCODE -eq 0 -and $out.Count -eq $Plans.Count) 'CANONICAL_PLAN_CODEC'
    $pins=[ordered]@{}
    for($i=0;$i -lt $Plans.Count;$i++){
        $plan=$Plans[$i]
        Assert-Solo ($out[$i] -cmatch ('^'+[regex]::Escape($plan.Id)+'\t([a-f0-9]{64})$')) 'CANONICAL_PLAN_PIN'
        $pins[$plan.Id]=$Matches[1]
    }
    Assert-SoloSha $helper $helperSha
    return @{HelperSha=$helperSha;Pins=$pins}
}

function New-SoloPrivate([string]$Path) {
    Assert-Solo (!(Test-Path -LiteralPath $Path)) 'NO_OVERWRITE'
    Assert-SoloDirectory (Split-Path $Path -Parent)
    $owner=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($owner)
    $acl.SetAccessRuleProtection($true,$false)
    foreach($value in @($owner.Value,'S-1-5-18','S-1-5-32-544')|Select-Object -Unique){
        $rule=[Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($value),'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $acl.AddAccessRule($rule)
    }
    $null=New-Item -ItemType Directory -Path $Path
    Set-Acl -LiteralPath $Path -AclObject $acl
    $actual=Get-Acl -LiteralPath $Path
    Assert-Solo ($actual.GetSecurityDescriptorSddlForm([Security.AccessControl.AccessControlSections]::Access) -ceq
        $acl.GetSecurityDescriptorSddlForm([Security.AccessControl.AccessControlSections]::Access) -and $actual.GetOwner([Security.Principal.SecurityIdentifier]).Value -ceq $owner.Value) 'PRIVATE_SCOPE'
}

function Copy-SoloFile([string]$Source,[string]$Target,[string]$Sha) {
    Assert-SoloSha $Source $Sha
    $parent=Split-Path $Target -Parent
    if(!(Test-Path -LiteralPath $parent)){$null=[IO.Directory]::CreateDirectory($parent)}
    Assert-SoloDirectory $parent
    [IO.File]::Copy($Source,$Target,$false)
    Assert-SoloSha $Target $Sha
}

function Copy-SoloClasses($Runtime,[string]$Stage) {
    Assert-SoloSha $Runtime.ClassPins $Runtime.Pins.classPinsSha
    $classRoot=Join-Path $Stage 'native-classes'
    $null=New-Item -ItemType Directory -Path $classRoot
    foreach($row in [IO.File]::ReadLines($Runtime.ClassPins,$utf8)){
        Assert-Solo ($row -cmatch '^([a-f0-9]{64})\t((?:[a-zA-Z0-9_$-]+/)*[a-zA-Z0-9_$-]+\.class)$') 'CLASS_PIN_ROW'
        $relative=$Matches[2]
        Copy-SoloFile (Join-Path $Runtime.ClassRoot $relative) (Join-Path $classRoot $relative) $Matches[1]
    }
    Copy-SoloFile $Runtime.ClassPins (Join-Path $Stage 'class-pins.tsv') $Runtime.Pins.classPinsSha
}

Assert-SoloDirectory $task
Assert-SoloOff
# Honest partial scenes and absent SETUP evidence fail before any model, reviewed build or output staging.
$fixture=Get-SoloFixture
foreach($pin in @($RuntimeReceiptSha,$LearnedWitnessSha,$ExpectedHead,$LearnerSha,$OpponentSha,$SafetyReviewSha)){
    Assert-Solo ($pin -cmatch '^[a-f0-9]{64}$') 'REQUIRED_EXTERNAL_PIN'
}
$plans=@(Get-SoloPlans $fixture)
$runtime=Get-SoloRuntime $fixture
Get-SoloSafetyReview
Assert-SoloDirectory $ledger
Assert-Solo ((Read-SoloText (Join-Path $ledger 'HEAD')) -ceq $ExpectedHead) 'FIT_LEDGER_HEAD'
Assert-Solo ($DeadlineUtc -cmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?Z$') 'UTC_DEADLINE'
$until=[DateTimeOffset]::Parse($DeadlineUtc,[Globalization.CultureInfo]::InvariantCulture)
$observe=$plans.Count*$SecondsPerEpisode
$left=($until-[DateTimeOffset]::UtcNow).TotalSeconds
Assert-Solo ($left -gt ($BootSeconds+$observe+$DrainSeconds) -and $left -lt 1800) 'FINITE_DEADLINE'
$java=Get-SoloClassPath $runtime
$epsilonText=$Epsilon.ToString('0.00############',[Globalization.CultureInfo]::InvariantCulture)
$planProof=Get-SoloPlanPins $plans $java $epsilonText
Assert-SoloOff

if($Action -eq 'Preflight'){
    Write-Output "TASK131_SOLO_TRAIN_PREFLIGHT cycle=$Cycle scenes=$($plans.Count) verifiedStoredSkills=true canonicalPlans=true launchAuthorized=false"
    return
}

Assert-Solo (!(Test-Path -LiteralPath $campaign)) 'NO_OVERWRITE'
$stage=Join-Path $campaigns ('.solo-'+[Guid]::NewGuid().ToString('N'))
New-SoloPrivate $stage
Copy-SoloClasses $runtime $stage
Copy-SoloFile $SafetyReviewPath (Join-Path $stage 'safety-review024.json') $SafetyReviewSha
foreach($model in @($LearnerSha,$OpponentSha)|Select-Object -Unique){
    $file='candidate-'+$model+'.bin'
    Copy-SoloFile (Join-Path $ledger $file) (Join-Path $stage $file) $model
}

$schedule=@("episodeId`tprofileId`tseconds")
$policy=[ordered]@{schema='TRAIN_POLICY131_V1';expectedHead=$ExpectedHead;learnerSha=$LearnerSha;opponentSha=$OpponentSha}
$execution=[ordered]@{}
foreach($key in @('schema','sourceCommit','runtimeRoot','gameJar','gameJarSha','adapterClassSha','factoryClassSha','sqlLane','jdbcSha','fixtureSha','sceneSha',
    'sceneScopeSha','enabledPhantomIniSha','maxCyclesPerActor')){$execution[$key]=$runtime.Execution[$key]}
$execution.phase='TRAIN'
$execution.campaignId=$campaignId
$execution.cycle=$Cycle
$execution.maxEpisodes=$plans.Count
$execution.expectedHead=$ExpectedHead
$execution.learnerSha=$LearnerSha
$execution.opponentSha=$OpponentSha
$execution.learnedWitnessSha=$LearnedWitnessSha
$execution.rollbackHead=$ExpectedHead
$execution.rollbackModelSha=$LearnerSha
$execution.observeSeconds=$observe
$execution.drainSeconds=$DrainSeconds
$manifestScenes=@()
foreach($plan in $plans){
    $schedule+=@(@($plan.Id,$plan.Profile,$plan.Seconds) -join "`t")
    $prefix='episode.'+$plan.Id+'.'
    $policy[$prefix+'seed']=$plan.Seed
    $policy[$prefix+'cap']=$ExplorationCap
    $policy[$prefix+'epsilon']=$epsilonText
    $policy[$prefix+'skills']=$plan.Skills
    $execution[$prefix+'sourceSha']=$plan.Source
    $execution[$prefix+'policySha']=$planProof.Pins[$plan.Id]
    $manifestScenes+=@([ordered]@{id=$plan.Id;participantCount=1;deadlineSeconds=$plan.Seconds})
}
Write-SoloText (Join-Path $stage 'scenes.tsv') (($schedule -join "`n")+"`n")
Write-SoloProperties (Join-Path $stage 'policy131.properties') $policy
$execution.trainScenesSha=Get-SoloSha (Join-Path $stage 'scenes.tsv')
$execution.policySha=Get-SoloSha (Join-Path $stage 'policy131.properties')
Write-SoloProperties (Join-Path $stage 'execution.properties') $execution
$executionSha=Get-SoloSha (Join-Path $stage 'execution.properties')
$manifest=[ordered]@{
    schema='TRAINING_COMBAT131_V1';campaignId=$campaignId;phase='TRAIN';cycle=$Cycle;auditBank=0;sourceCommit=$runtime.Pins.sourceCommit
    runtimeRoot=$runtime.Root;sqlLane=$SqlLane;javaSha=$runtime.Pins.javaSha;gameJarSha=$runtime.Pins.gameJarSha;classPinsSha=$runtime.Pins.classPinsSha
    executionSha=$executionSha;safetyReviewSha=$SafetyReviewSha;fixtureSha=$execution.trainScenesSha;policySha=$execution.policySha;modelSha=$LearnerSha
    dbSealSha=$runtime.Pins.dbSealSha;jdbcSha=$runtime.Pins.jdbcSha;dependencyShas=$runtime.Pins.dependencyShas;configShas=$runtime.Pins.configShas
    quota=[ordered]@{maxBoots=$MaxBoots;maxEpisodes=$plans.Count;bootSeconds=$BootSeconds;drainSeconds=$DrainSeconds;deadlineUtc=$DeadlineUtc}
    scenes=$manifestScenes
}
Assert-SoloSha (Join-Path $sql 'n01-fixture.tsv') $fixture.FixtureSha
Assert-SoloSha (Join-Path $sql 'n01-scenes.tsv') $fixture.SceneSha
Assert-SoloSha (Join-Path $sql 'n01-scene-scope.properties') $fixture.ScopeSha
Assert-SoloSha (Join-Path $sql 'n01-learned-offense.tsv') $LearnedWitnessSha
Assert-SoloSha (Join-Path $sql 'off.json') $runtime.OffSha
Assert-Solo ((Read-SoloText (Join-Path $ledger 'HEAD')) -ceq $ExpectedHead) 'FIT_LEDGER_CHANGED'
Assert-SoloOff
Assert-SoloDirectory $stage
[IO.Directory]::Move($stage,$campaign)
try {
    # The native read-only phase requires the final namespace. Publish its controller manifest last.
    $cp=($java.Libraries+@((Join-Path $campaign 'native-classes'))) -join ';'
    $verification=@(& $java.Java '-Dphantom.learning.train=true' -cp $cp org.l2jmobius.tests.phantoms.PhantomLearning131Native --train-preflight (Join-Path $campaign 'execution.properties'))
    Assert-Solo ($LASTEXITCODE -eq 0 -and ($verification -join "`n") -cmatch 'TASK131_NATIVE_PREFLIGHT_READY phase=TRAIN ') 'JAVA_TRAIN_PREFLIGHT'
    Assert-SoloOff
    Assert-SoloSha (Join-Path $campaign 'execution.properties') $executionSha
    $manifestBody=($manifest|ConvertTo-Json -Depth 9)+"`n"
    $close=[ordered]@{
        schema='TASK131_SOLO_TRAIN_OFFLINE_V1';stage='PREPARED_OFFLINE';launchAuthorized=$false;manifestSha=(Get-SoloTextSha $manifestBody)
        runtimeReceiptSha=$RuntimeReceiptSha;learnerSha=$LearnerSha;opponentSha=$OpponentSha;expectedHead=$ExpectedHead;learnedWitnessSha=$LearnedWitnessSha
        planWriterSha=$planProof.HelperSha;profiles=@($plans.Profile);episodeCount=$plans.Count
    }
    Write-SoloText (Join-Path $campaign 'offline-preflight.json') (($close|ConvertTo-Json -Depth 4)+"`n")
    $pending=Join-Path $campaign '.campaign.pending'
    Write-SoloText $pending $manifestBody
    Assert-SoloSha $pending $close.manifestSha
    [IO.File]::Move($pending,(Join-Path $campaign 'campaign.json'))
}catch{
    Assert-SoloDirectory $campaign
    if(!(Test-Path -LiteralPath (Join-Path $campaign 'campaign.json'))){[IO.Directory]::Move($campaign,$stage)}
    throw
}
Write-Output "TASK131_SOLO_TRAIN_PREPARED cycle=$Cycle scenes=$($plans.Count) manifestSha=$($close.manifestSha) launchAuthorized=false"
