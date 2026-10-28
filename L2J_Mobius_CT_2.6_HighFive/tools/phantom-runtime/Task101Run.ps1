[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Build','Create','Seal','Repair','SealHarness','SealStackHarness','Start1','Start2','Start3','Postflight')][string]$Action,
    [Parameter(Mandatory)][string]$SourceRoot,
    [Parameter(Mandatory)][string]$StageRoot,
    [Parameter(Mandatory)][string]$InstallationId
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$source=[IO.Path]::GetFullPath($SourceRoot)
$stage=[IO.Path]::GetFullPath($StageRoot)
$module=Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
$private=Join-Path $stage 'private'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$acl=Get-Acl -LiteralPath $stage
if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $identity){throw 'OWN101_OWNER_REQUIRED'}
foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity,'S-1-5-18')){throw 'OWN101_ACL_DRIFT'}}
for($walk=Get-Item -LiteralPath $stage;$null -ne $walk;$walk=$walk.Parent){if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN101_REPARSE'}}
if([Guid]::ParseExact($InstallationId,'D').ToString() -cne $InstallationId -or $stage.StartsWith((Split-Path $source -Parent)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN101_SCOPE'}
$ownerPath=Join-Path $private 'owner.properties'
$owner=[IO.File]::ReadAllText($ownerPath)
if(!$owner.Contains("installationId=$InstallationId") -or !$owner.Contains('owner=TASK101_CRAFT_MARKET') -or !$owner.Contains('schema=l2jmobiush5_phantom_test_101')){throw 'OWN101_AUTHORITY'}
function Hash([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Put-New([string]$Path,[string]$Text){$stream=[IO.File]::Open($Path,'CreateNew','Write','None');try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}}
function Probe([string]$Mode){Push-Location (Join-Path $module 'dist/game');try{& java --class-path ((Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')) (Join-Path $module 'tools/phantom-runtime/Task101DatabaseProbe.java') $Mode $stage $InstallationId;if($LASTEXITCODE -ne 0){throw "OWN101_PROBE_FAILED $Mode exit=$LASTEXITCODE"}}finally{Pop-Location}}
if($Action -eq 'Build'){
    if(Test-Path -LiteralPath (Join-Path $private 'db.created')){throw 'OWN101_BUILD_AFTER_DB_FORBIDDEN'}
    $paths=@(
        'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
        'java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java',
        'java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftProvision.java',
        'java/org/l2jmobius/gameserver/phantoms/progression/PhantomCraftRecipePolicy.java',
        'java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeCraftRecipes.java',
        'java/org/l2jmobius/gameserver/phantoms/progression/PhantomOrdinaryCraftRecipes.java',
        'java/org/l2jmobius/gameserver/phantoms/progression/L2jPhantomLearningJourney.java',
        'test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java',
        'test/java/org/l2jmobius/tests/phantoms/PhantomTask101Lane.java',
        'test/java/org/l2jmobius/tests/phantoms/PhantomTask101Authority.java',
        'test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java',
        'test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Checks.java',
        'tools/phantom-runtime/Task101.ps1','tools/phantom-runtime/Task101Run.ps1','tools/phantom-runtime/Task101Preflight.java','tools/phantom-runtime/Task101DatabaseProbe.java',
        'dist/game/config/Custom/PhantomMerchantCrafting.ini','dist/game/config/Custom/PhantomOrdinaryCraftRecipes.ini'
    )
    $inventory=@{};foreach($line in [IO.File]::ReadAllLines((Join-Path $stage 'inputs.tsv'))){$fields=$line.Split("`t");$inventory[$fields[0]]=$fields[1]}
    foreach($relative in $paths){
        $origin=Join-Path $source $relative;$target=Join-Path $module $relative;$before=Hash $origin
        if(Test-Path -LiteralPath $target){$preimage=Join-Path $private ('preimage/'+$relative);[IO.Directory]::CreateDirectory((Split-Path $preimage -Parent))|Out-Null;if(!(Test-Path -LiteralPath $preimage)){[IO.File]::Copy($target,$preimage,$false)}}
        [IO.Directory]::CreateDirectory((Split-Path $target -Parent))|Out-Null;[IO.File]::Copy($origin,$target,$true)
        if((Hash $target) -cne $before -or (Hash $origin) -cne $before){throw 'OWN101_SOURCE_COPY_DRIFT'};$inventory[$relative]=$before
    }
    [IO.File]::WriteAllText((Join-Path $stage 'inputs.tsv'),(($inventory.Keys|Sort-Object|ForEach-Object {$_+"`t"+$inventory[$_]}) -join "`n"),[Text.UTF8Encoding]::new($false))
    $owner=[regex]::Replace($owner,'(?m)^inputsSha256=.*$',('inputsSha256='+(Hash (Join-Path $stage 'inputs.tsv'))));[IO.File]::WriteAllText($ownerPath,$owner,[Text.UTF8Encoding]::new($false))
    $buildFile=Join-Path $private 'build101.xml'
    $xml='<project name="OWN101" basedir="'+[Security.SecurityElement]::Escape($module)+'"><import file="'+[Security.SecurityElement]::Escape((Join-Path $module 'build.xml'))+'"/><target name="task101-build" depends="compile-tests,jar"/></project>'
    [IO.File]::WriteAllText($buildFile,$xml,[Text.UTF8Encoding]::new($false))
    & ant -f $buildFile task101-build
    if($LASTEXITCODE -ne 0){throw "OWN101_BUILD_FAILED exit=$LASTEXITCODE"}
    & java -cp ((Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')) org.l2jmobius.tests.phantoms.PhantomCraft101Checks
    if($LASTEXITCODE -ne 0){throw 'OWN101_DB_FREE_FAILED'}
    return
}
if($Action -eq 'Create'){Probe 'preflight';Probe 'plan';Probe 'create';return}
if($Action -eq 'SealStackHarness'){
    if(!(Test-Path -LiteralPath (Join-Path $private 'harness-rare.completed')) -or (Test-Path -LiteralPath (Join-Path $private 'harness-stack.begun')) -or (Test-Path -LiteralPath (Join-Path $private 'receipts/cycle.properties'))){throw 'OWN101_SINGLE_STACK_PROOF_CONTINUATION_ONLY'}
    $archive=Join-Path $private 'preimage-stack';[IO.Directory]::CreateDirectory($archive)|Out-Null
    Put-New (Join-Path $private 'harness-stack.begun') 'cause=exact old SHA reconstructed by native merge of100000+2340 Adena;retain strict purchased object identity;no product edits or DB writes'
    [IO.File]::Copy((Join-Path (Split-Path $source -Parent) '.phantom-local/logs/task101/native2.log'),(Join-Path $archive 'native-red.log'),$false)
    $changed=@('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java','L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task101DatabaseProbe.java','L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task101Run.ps1','build/phantom-test/bin/org/l2jmobius/tests/phantoms/PhantomCraft101Native.class')
    foreach($relative in $changed){$file=Join-Path $stage $relative;$copy=Join-Path $archive $relative;[IO.Directory]::CreateDirectory((Split-Path $copy -Parent))|Out-Null;[IO.File]::Copy($file,$copy,$false);if((Hash $file) -cne (Hash $copy)){throw 'OWN101_STACK_PREIMAGE_DRIFT'}}
    foreach($name in @('native.properties','native-inputs.tsv')){[IO.File]::Copy((Join-Path $private $name),(Join-Path $archive $name),$false)}
    $jarBefore=Hash (Join-Path $module 'dist/libs/GameServer.jar')
    foreach($relative in $changed|Where-Object {$_ -like 'L2J_Mobius_CT_2.6_HighFive/*'}){[IO.File]::Copy((Join-Path (Split-Path $source -Parent) $relative),(Join-Path $stage $relative),$true)}
    $cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')
    & javac -encoding UTF-8 -cp $cp -d (Join-Path $stage 'build/phantom-test/bin') (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java')
    if($LASTEXITCODE -ne 0){throw 'OWN101_STACK_HARNESS_COMPILE_FAILED'}
    Probe 'stackproof'
    $updated=@([IO.File]::ReadAllLines((Join-Path $private 'native-inputs.tsv'))|ForEach-Object {$fields=$_.Split("`t");if($fields[0] -in $changed){$fields[0]+"`t"+(Hash (Join-Path $stage $fields[0]))}else{$_}})
    [IO.File]::WriteAllLines((Join-Path $private 'native-inputs.tsv'),$updated,[Text.UTF8Encoding]::new($false))
    $pinsPath=Join-Path $private 'native.properties';$pins=[regex]::Replace([IO.File]::ReadAllText($pinsPath),'(?m)^nativeInputsSha256=.*$',('nativeInputsSha256='+(Hash (Join-Path $private 'native-inputs.tsv'))));[IO.File]::WriteAllText($pinsPath,$pins,[Text.UTF8Encoding]::new($false))
    if((Hash (Join-Path $module 'dist/libs/GameServer.jar')) -cne $jarBefore){throw 'OWN101_SAME_JAR_CHANGED'}
    Put-New (Join-Path $private 'harness-stack.completed') ('sameJar='+$jarBefore+"`nremainingStarts=2-replay,3;newGrants=0;rawRED=PRESERVED;noMoreRepairs=true")
    return
}
if($Action -eq 'SealHarness'){
    # Owner's continuation: preserve the valid rare craft, then split the remaining paid cycle at a real JVM restart.
    if(!(Test-Path -LiteralPath (Join-Path $private 'repair.completed')) -or (Test-Path -LiteralPath (Join-Path $private 'harness-rare.begun')) -or (Test-Path -LiteralPath (Join-Path $private 'receipts/start2.begun'))){throw 'OWN101_SINGLE_HARNESS_CONTINUATION_ONLY'}
    $archive=Join-Path $private 'preimage-rare';[IO.Directory]::CreateDirectory($archive)|Out-Null
    Put-New (Join-Path $private 'harness-rare.begun') 'cause=source native RARE_PRODUCT is a successful craft;owner requested completion;no further START1;planned START2/3 cutpoint only'
    [IO.File]::Copy((Join-Path (Split-Path $source -Parent) '.phantom-local/logs/task101/native1-replay.log'),(Join-Path $archive 'native-red.log'),$false)
    $changed=@('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java','L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task101DatabaseProbe.java','L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/Task101Run.ps1','build/phantom-test/bin/org/l2jmobius/tests/phantoms/PhantomCraft101Native.class')
    foreach($relative in $changed){$file=Join-Path $stage $relative;$copy=Join-Path $archive $relative;[IO.Directory]::CreateDirectory((Split-Path $copy -Parent))|Out-Null;[IO.File]::Copy($file,$copy,$false);if((Hash $file) -cne (Hash $copy)){throw 'OWN101_RARE_PREIMAGE_DRIFT'}}
    foreach($name in @('native.properties','native-inputs.tsv')){[IO.File]::Copy((Join-Path $private $name),(Join-Path $archive $name),$false)}
    $jarBefore=Hash (Join-Path $module 'dist/libs/GameServer.jar')
    foreach($relative in $changed|Where-Object {$_ -like 'L2J_Mobius_CT_2.6_HighFive/*'}){[IO.File]::Copy((Join-Path (Split-Path $source -Parent) $relative),(Join-Path $stage $relative),$true)}
    $cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')
    & javac -encoding UTF-8 -cp $cp -d (Join-Path $stage 'build/phantom-test/bin') (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java')
    if($LASTEXITCODE -ne 0){throw 'OWN101_RARE_HARNESS_COMPILE_FAILED'}
    Probe 'continuation'
    $rows=[IO.File]::ReadAllLines((Join-Path $private 'native-inputs.tsv'))
    $updated=@($rows|ForEach-Object {$fields=$_.Split("`t");if($fields[0] -in $changed){$fields[0]+"`t"+(Hash (Join-Path $stage $fields[0]))}else{$_}})
    [IO.File]::WriteAllLines((Join-Path $private 'native-inputs.tsv'),$updated,[Text.UTF8Encoding]::new($false))
    $pinsPath=Join-Path $private 'native.properties';$pins=[IO.File]::ReadAllText($pinsPath)
    $pins=[regex]::Replace($pins,'(?m)^nativeInputsSha256=.*$',('nativeInputsSha256='+(Hash (Join-Path $private 'native-inputs.tsv'))));[IO.File]::WriteAllText($pinsPath,$pins,[Text.UTF8Encoding]::new($false))
    if((Hash (Join-Path $module 'dist/libs/GameServer.jar')) -cne $jarBefore){throw 'OWN101_SAME_JAR_CHANGED'}
    Put-New (Join-Path $private 'harness-rare.completed') ('sameJar='+$jarBefore+"`nnewBootstrap=0;newCraftGrant=0;oldRED=PRESERVED;remainingNativeStarts=2,3;noMoreRetries=true")
    Write-Output 'OWN101_HARNESS_ONLY_SEALED sameJAR=true recoveredPaidRare=true reset=0'
    return
}
if($Action -eq 'Repair'){
    $receipts=Join-Path $private 'receipts'
    if(!(Test-Path -LiteralPath (Join-Path $receipts 'start1.tsv')) -or (Test-Path -LiteralPath (Join-Path $receipts 'ids1.txt')) -or (Test-Path -LiteralPath (Join-Path $receipts 'cycle.properties')) -or (Test-Path -LiteralPath (Join-Path $private 'repair.begun'))){throw 'OWN101_EXACT_ONE_PREGRANT_REPAIR_REQUIRED'}
    if([IO.File]::ReadAllText((Join-Path $receipts 'start1.tsv')) -notmatch 'drained=True;remainingOwners=0' -and [IO.File]::ReadAllText((Join-Path $receipts 'start1.tsv')) -notmatch 'drained=true;remainingOwners=0'){throw 'OWN101_RED_NOT_DRAINED'}
    $cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')
    Push-Location (Join-Path $module 'dist/game')
    try{& java -cp $cp (Join-Path $source 'tools/phantom-runtime/Task101DatabaseProbe.java') repaircheck $stage $InstallationId;if($LASTEXITCODE -ne 0){throw 'OWN101_PREGRANT_REPAIR_UNCERTAIN'}}finally{Pop-Location}
    $archive=Join-Path $private 'preimage101';[IO.Directory]::CreateDirectory($archive)|Out-Null
    Put-New (Join-Path $private 'repair.begun') ('cause=Player.create sets template/base53 but leaves activeClass0;native grants=0;source-backed guard correction;no DB reset'+"`noldJar="+(Hash (Join-Path $module 'dist/libs/GameServer.jar')))
    foreach($relative in @('dist/libs/GameServer.jar','java/org/l2jmobius/gameserver/phantoms/economy/PhantomMerchantCraftProvision.java','test/java/org/l2jmobius/tests/phantoms/PhantomCraft101Native.java','tools/phantom-runtime/Task101DatabaseProbe.java','tools/phantom-runtime/Task101Run.ps1')){
        $before=Join-Path $module $relative;$copy=Join-Path $archive $relative;[IO.Directory]::CreateDirectory((Split-Path $copy -Parent))|Out-Null;[IO.File]::Copy($before,$copy,$false);if((Hash $before) -cne (Hash $copy)){throw 'OWN101_RED_PREIMAGE_DRIFT'}
        if($relative -ne 'dist/libs/GameServer.jar'){[IO.File]::Copy((Join-Path $source $relative),$before,$true)}
    }
    [IO.File]::Copy((Join-Path $source 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'),(Join-Path $module 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'),$true)
    # Restore only the shadow population preimage for the normal OwnerConfig build gate.
    [IO.File]::Copy((Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini'),(Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini'),$true)
    & ant -f (Join-Path $private 'build101.xml') task101-build;if($LASTEXITCODE -ne 0){throw 'OWN101_REPAIR_BUILD_FAILED'}
    foreach($name in @('native.properties','native-inputs.tsv')){
        $from=[IO.Path]::GetFullPath((Join-Path $private $name));$to=[IO.Path]::GetFullPath((Join-Path $archive $name))
        if(!$from.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase) -or !$to.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN101_ARCHIVE_SCOPE'}
        [IO.File]::Move($from,$to)
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -Action Seal -SourceRoot $source -StageRoot $stage -InstallationId $InstallationId
    if($LASTEXITCODE -ne 0){throw 'OWN101_REPAIR_SEAL_FAILED'}
    if(!(Test-Path -LiteralPath (Join-Path $private 'repair.completed'))){throw 'OWN101_REPAIR_SEAL_INCOMPLETE'}
    return
}
if($Action -eq 'Seal'){
    if(!(Test-Path -LiteralPath (Join-Path $private 'db.created')) -or (Test-Path -LiteralPath (Join-Path $private 'native.properties'))){throw 'OWN101_SEAL_ONCE_AFTER_CREATE'}
    $database=[IO.File]::ReadAllText((Join-Path $source 'dist/game/config/Database.ini'))
    $scheme=[regex]::Match($database,'(?m)^[ \t]*URL[ \t]*=[ \t]*jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5(?:\?|\r?$)').Groups[1].Value
    if(!$scheme){throw 'OWN101_JDBC_ORIGIN'}
    $database=[regex]::Replace($database,'(?m)^[ \t]*URL[ \t]*=.*$',('URL=jdbc:'+ $scheme+'://127.0.0.1:3308/l2jmobiush5_phantom_test_101?useSSL=false'))
    $database=[regex]::Replace($database,'(?m)^[ \t]*MaximumDatabaseConnections[ \t]*=.*$','MaximumDatabaseConnections=4')
    $databaseTarget=Join-Path $module 'dist/game/config/Database.ini'
    if(Test-Path -LiteralPath $databaseTarget){
        if(!(Test-Path -LiteralPath (Join-Path $private 'repair.begun')) -or [IO.File]::ReadAllText($databaseTarget) -cne $database){throw 'OWN101_EXISTING_DATABASE_CONFIG_DRIFT'}
    }else{Put-New $databaseTarget $database}
    $population=Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini'
    $text=[IO.File]::ReadAllText($population)
    $text=[regex]::Replace($text,'(?m)^([ \t]*(?:PhantomPopulationTarget|PhantomPopulationActiveTarget)[ \t]*=[ \t]*)[0-9]+','${1}0')
    [IO.File]::WriteAllText($population,$text,[Text.UTF8Encoding]::new($false))
    $pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini','dist/game/config/Custom/PhantomMerchants.ini','dist/game/config/Custom/PhantomMerchantCrafting.ini','dist/game/config/Custom/PhantomOrdinaryCraftRecipes.ini')
    $lines=@($pins|ForEach-Object {$_+'='+(Hash (Join-Path $module $_))})
    $nativeRows=[Collections.Generic.List[string]]::new()
    foreach($root in @($module,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
        foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
            $relative=$file.FullName.Substring($stage.Length+1).Replace('\','/')
            if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
            $nativeRows.Add($relative+"`t"+(Hash $file.FullName))
        }
    }
    Put-New (Join-Path $private 'native-inputs.tsv') (($nativeRows|Sort-Object) -join "`n")
    $lines+= 'nativeInputsSha256='+(Hash (Join-Path $private 'native-inputs.tsv'))
    Put-New (Join-Path $private 'native.properties') ($lines -join "`n")
    if((Test-Path -LiteralPath (Join-Path $private 'repair.begun')) -and !(Test-Path -LiteralPath (Join-Path $private 'repair.completed'))){
        Put-New (Join-Path $private 'repair.completed') ('newJar='+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))+"`nsameDB=true;bootstrapReplay=false;retryBudget=EXHAUSTED")
    }
    Write-Output 'OWN101_SEALED sourceConfigWrites=0 populationOverlay=0/0 sameJAR=PINNED'
    return
}
if($Action -eq 'Postflight'){Probe 'postflight';return}
$generation=if($Action -eq 'Start1'){if(Test-Path -LiteralPath (Join-Path $private 'repair.completed')){'1-replay'}else{'1'}}elseif($Action -eq 'Start2'){if(Test-Path -LiteralPath (Join-Path $private 'harness-stack.completed')){'2-replay'}else{'2'}}else{'3'}
$cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
Push-Location (Join-Path $module 'dist/game')
try{
    & java -Xms512m -Xmx4g '-Dphantom.task101.native=true' "-Dphantom.task101.stage=$stage" "-Dphantom.task101.uuid=$InstallationId" "-Dphantom.module.root=$module" "-Dphantom.test.config=$(Join-Path $module 'dist/game/config/Database.ini')" -cp $cp org.l2jmobius.tests.phantoms.PhantomCraft101Native $generation
    if($LASTEXITCODE -ne 0){throw "OWN101_NATIVE_FAILED generation=$generation exit=$LASTEXITCODE"}
}finally{Pop-Location}
