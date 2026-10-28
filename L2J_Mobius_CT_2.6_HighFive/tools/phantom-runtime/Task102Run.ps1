[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Seal','Plan','Create','Phase1','RetryPhase1','Phase2','Phase3','Phase4','Phase5','Postflight')][string]$Action,
    [Parameter(Mandatory)][string]$SourceRoot,[Parameter(Mandatory)][string]$StageRoot,[Parameter(Mandatory)][string]$InstallationId
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$source=[IO.Path]::GetFullPath($SourceRoot)
$stage=[IO.Path]::GetFullPath($StageRoot)
$module=Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
$private=Join-Path $stage 'private'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
foreach($root in @($source,$stage)) { for($walk=Get-Item -LiteralPath $root;$null -ne $walk;$walk=$walk.Parent){if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN102_REPARSE'}} }
$acl=Get-Acl -LiteralPath $stage
if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $identity){throw 'OWN102_OWNER_REQUIRED'}
foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity,'S-1-5-18')){throw 'OWN102_ACL_DRIFT'}}
if([Guid]::ParseExact($InstallationId,'D').ToString() -cne $InstallationId -or $stage -ieq (Split-Path $source -Parent) -or $stage.StartsWith((Split-Path $source -Parent)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN102_SCOPE'}
$ownerPath=Join-Path $private 'owner.properties'
$owner=[IO.File]::ReadAllText($ownerPath)
if(!$owner.Contains("installationId=$InstallationId") -or !$owner.Contains('owner=TASK102_POPULATION_SCALE') -or !$owner.Contains('schema=l2jmobiush5_phantom_test_102') -or !$owner.Contains("ownerSid=$identity")){throw 'OWN102_AUTHORITY'}
function Hash([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Put-New([string]$Path,[string]$Text){$stream=[IO.File]::Open($Path,'CreateNew','Write','None');try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}}
$cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
function Probe([string]$Mode){
    Push-Location (Join-Path $module 'dist/game')
    try{ & java -cp $cp (Join-Path $module 'tools/phantom-runtime/Task102DatabaseProbe.java') $Mode $stage $InstallationId; if($LASTEXITCODE -ne 0){throw "OWN102_PROBE_FAILED $Mode exit=$LASTEXITCODE"} }finally{Pop-Location}
}
if($Action -eq 'Seal'){
    if((Test-Path -LiteralPath (Join-Path $private 'db.created')) -or (Test-Path -LiteralPath (Join-Path $private 'sealed.properties'))){throw 'OWN102_SEAL_ONCE_BEFORE_DB'}
    # Input origin remains distinct from the compiled native generation.
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'prepare-inputs.tsv')
    $inputMap=@{};foreach($line in [IO.File]::ReadAllLines((Join-Path $stage 'inputs.tsv'))){$parts=$line.Split("`t");$inputMap[$parts[0]]=$parts[1]}
    foreach($relative in @('java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java','test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java','test/java/org/l2jmobius/tests/phantoms/PhantomPopulationPerformanceSuite.java','test/java/org/l2jmobius/tests/phantoms/PhantomTask102Authority.java','test/java/org/l2jmobius/tests/phantoms/PhantomTask102Lane.java','test/java/org/l2jmobius/tests/phantoms/PhantomPopulation102Native.java','tools/phantom-runtime/Task102.ps1','tools/phantom-runtime/Task102Run.ps1','tools/phantom-runtime/Task102OwnerParity.ps1','tools/phantom-runtime/Task102Preflight.java','tools/phantom-runtime/Task102DatabaseProbe.java')){
        if((Hash (Join-Path $source $relative)) -cne (Hash (Join-Path $module $relative))){throw 'OWN102_SOURCE_STAGE_DRIFT'}
        $inputMap[$relative]=Hash (Join-Path $source $relative)
    }
    [IO.File]::WriteAllText((Join-Path $stage 'inputs.tsv'),(($inputMap.Keys|Sort-Object|ForEach-Object {$_+"`t"+$inputMap[$_]}) -join "`n"),[Text.UTF8Encoding]::new($false))
    $owner=[regex]::Replace($owner,'(?m)^inputsSha256=.*$',('inputsSha256='+(Hash (Join-Path $stage 'inputs.tsv'))));[IO.File]::WriteAllText($ownerPath,$owner,[Text.UTF8Encoding]::new($false))
    New-Item -ItemType Directory -Path (Join-Path $private 'receipts') | Out-Null
    Put-New (Join-Path $private 'owner-final-PhantomPlayers.ini') ([IO.File]::ReadAllText((Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')))
    Put-New (Join-Path $private 'sealed.properties') ('jarSha256='+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))+"`nsourceOwnerConfigSha256="+(Hash (Join-Path $source 'dist/game/config/OwnerConfig.json')))
    Write-Output 'OWN102_SEALED_BEFORE_DB nativeInputs=PER_PHASE;sourceConfigWrites=0'
    return
}
if($Action -eq 'Plan'){Probe 'preflight';Probe 'plan';return}
if($Action -eq 'Create'){Probe 'preflight';Probe 'plan';Probe 'create';return}
if($Action -eq 'Postflight'){Probe 'postflight';return}
if($Action -eq 'RetryPhase1'){
    $receipts=Join-Path $private 'receipts'
    if(!(Test-Path -LiteralPath (Join-Path $receipts 'start1.begun')) -or (Test-Path -LiteralPath (Join-Path $receipts 'phase1.complete')) -or (Test-Path -LiteralPath (Join-Path $private 'retry1.begun'))){throw 'OWN102_SINGLE_CAUSAL_RETRY_REQUIRED'}
    if([IO.File]::ReadAllText((Join-Path $receipts 'stop1.txt')) -notmatch '^drained=true;' -or [IO.File]::ReadAllText((Join-Path $receipts 'stopped1.checkpoint')) -notmatch '^0\s+0\s+'){throw 'OWN102_RETRY_REQUIRES_DRAINED_EMPTY_ORDINARY_PREGRANT_FAILURE'}
    Probe 'postflight'
    $archive=[IO.Path]::GetFullPath((Join-Path $private 'red1'))
    $receiptPath=[IO.Path]::GetFullPath($receipts)
    if(!$archive.StartsWith($private+'\',[StringComparison]::OrdinalIgnoreCase) -or !$receiptPath.StartsWith($private+'\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $archive)){throw 'OWN102_RETRY_ARCHIVE_SCOPE'}
    Put-New (Join-Path $private 'retry1.begun') 'cause=missing canonical supported content bootstrap;ordinaryCharacters=0;no grants;SQL reset=0;JAR unchanged;remainingRetries=0'
    New-Item -ItemType Directory -Path $archive | Out-Null
    Copy-Item -LiteralPath $receiptPath -Destination (Join-Path $archive 'receipts') -Recurse
    foreach($file in Get-ChildItem -LiteralPath $receiptPath -File -Recurse){$copy=Join-Path (Join-Path $archive 'receipts') $file.FullName.Substring($receiptPath.Length+1);if((Hash $copy) -cne (Hash $file.FullName)){throw 'OWN102_RED_RECEIPT_COPY_DRIFT'}}
    New-Item -ItemType Directory -Path (Join-Path $private 'receipts-retry1') | Out-Null
    foreach($name in @('native-inputs1.tsv','native1.properties')){Copy-Item -LiteralPath (Join-Path $private $name) -Destination (Join-Path $archive $name);if((Hash (Join-Path $private $name)) -cne (Hash (Join-Path $archive $name))){throw 'OWN102_RED_MANIFEST_COPY_DRIFT'}}
    $Action='Phase1'
}
$phase=[int]$Action.Substring(5)
$receiptRoot=if(Test-Path -LiteralPath (Join-Path $private 'retry1.begun')){'receipts-retry1'}else{'receipts'}
if(!(Test-Path -LiteralPath (Join-Path $private 'db.created')) -or (Test-Path -LiteralPath (Join-Path $private "$receiptRoot/start$phase.begun"))){throw 'OWN102_CREATED_SINGLE_PHASE_REQUIRED'}
if($phase -gt 1){
    $previous=$phase-1
    if(!(Test-Path -LiteralPath (Join-Path $private "$receiptRoot/phase$previous.complete")) -or [IO.File]::ReadAllText((Join-Path $private "$receiptRoot/stop$previous.txt")) -notmatch '^drained=true;'){throw 'OWN102_PREVIOUS_CHECKPOINT_STOP_REQUIRED'}
}
$sealed=[IO.File]::ReadAllText((Join-Path $private 'sealed.properties'))
if(!$sealed.Contains('jarSha256='+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))) -or !$sealed.Contains('sourceOwnerConfigSha256='+(Hash (Join-Path $source 'dist/game/config/OwnerConfig.json')))){throw 'OWN102_SAME_JAR_OR_OWNER_DRIFT'}
$databaseTarget=Join-Path $module 'dist/game/config/Database.ini'
if(!$owner.Contains('sourceDatabaseSha256='+(Hash (Join-Path $source 'dist/game/config/Database.ini')))){throw 'OWN102_DATABASE_ORIGIN_DRIFT'}
if($phase -eq 1 -and !(Test-Path -LiteralPath $databaseTarget)){
    $database=[IO.File]::ReadAllText((Join-Path $source 'dist/game/config/Database.ini'))
    $scheme=[regex]::Match($database,'(?m)^[ \t]*URL[ \t]*=[ \t]*jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5(?:\?|\r?$)').Groups[1].Value
    if(!$scheme){throw 'OWN102_JDBC_ORIGIN'}
    $database=[regex]::Replace($database,'(?m)^[ \t]*URL[ \t]*=.*$',('URL=jdbc:'+$scheme+'://127.0.0.1:3308/l2jmobiush5_phantom_test_102?useSSL=false'))
    $database=[regex]::Replace($database,'(?m)^[ \t]*MaximumDatabaseConnections[ \t]*=.*$','MaximumDatabaseConnections=4')
    Put-New $databaseTarget $database
}
if(Test-Path -LiteralPath (Join-Path $private 'native1.properties')){
    if(!([IO.File]::ReadAllText((Join-Path $private 'native1.properties'))).Contains('dist/game/config/Database.ini='+(Hash $databaseTarget))){throw 'OWN102_SAME_DATABASE_CONFIG_DRIFT'}
}
$targets=@(500,1000,3000,5000,5000);$caps=@(16,32,64,128,128)
$population=Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini'
$final=[IO.File]::ReadAllText((Join-Path $private 'owner-final-PhantomPlayers.ini'))
$text=$final
foreach($pair in @(@('PhantomPopulationTarget',$targets[$phase-1]),@('PhantomPopulationActiveTarget',$caps[$phase-1]),@('MaxMaterializedPhantoms',$caps[$phase-1]))){
    $value=[string]$pair[1];$pattern='(?m)^([ \t]*'+$pair[0]+'[ \t]*=[ \t]*)[^\r\n]*'
    if([regex]::Matches($text,$pattern).Count -ne 1){throw 'OWN102_POPULATION_KEY'}
    $text=[regex]::Replace($text,$pattern,{param($match) $match.Groups[1].Value+$value})
}
[IO.File]::WriteAllText($population,$text,[Text.UTF8Encoding]::new($false))
$pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini')
$nativeRows=[Collections.Generic.List[string]]::new()
foreach($root in @($module,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
    foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
        if($file.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN102_NATIVE_REPARSE'}
        $relative=$file.FullName.Substring($stage.Length+1).Replace('\','/')
        if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
        $nativeRows.Add($relative+"`t"+(Hash $file.FullName))
    }
}
$generation=if($phase -eq 1 -and (Test-Path -LiteralPath (Join-Path $private 'retry1.begun'))){'1-retry'}else{[string]$phase}
$manifest=Join-Path $private "native-inputs$generation.tsv"
Put-New $manifest (($nativeRows|Sort-Object) -join "`n")
Copy-Item -LiteralPath $manifest -Destination (Join-Path $private 'native-inputs.tsv')
$lines=@($pins|ForEach-Object {$_+'='+(Hash (Join-Path $module $_))})
$lines+='nativeInputsSha256='+(Hash $manifest)
Put-New (Join-Path $private "native$generation.properties") ($lines -join "`n")
Copy-Item -LiteralPath (Join-Path $private "native$generation.properties") -Destination (Join-Path $private 'native.properties')
Push-Location (Join-Path $module 'dist/game')
try{
    & java -Xms512m -Xmx4g '-Dphantom.task102.native=true' "-Dphantom.task102.stage=$stage" "-Dphantom.task102.uuid=$InstallationId" "-Dphantom.module.root=$module" "-Dphantom.test.config=$databaseTarget" -cp $cp org.l2jmobius.tests.phantoms.PhantomPopulation102Native $phase
    if($LASTEXITCODE -ne 0){throw "OWN102_NATIVE_FAILED phase=$phase exit=$LASTEXITCODE"}
}finally{
    Pop-Location
    # This runs only after the owned JVM exits. Never apply/save overlays to source.
    [IO.File]::WriteAllText($population,$final,[Text.UTF8Encoding]::new($false))
}
