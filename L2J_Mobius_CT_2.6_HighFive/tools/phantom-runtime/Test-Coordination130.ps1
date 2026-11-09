[CmdletBinding()]
param(
    [ValidateSet('package','fixture','recover','artifact-check','native','restart','postflight')][string]$Action,
    [Parameter(Mandatory=$true)][string]$RuntimeRoot,
    [ValidateSet('original','morning')][string]$Fixture='original',
    [ValidateSet('regression','first-party')][string]$Lane='regression',
    [string]$GeoSource
)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$source=[IO.Directory]::GetParent($module).FullName
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
$parent=[IO.Directory]::GetParent($source).FullName
$firstParty=$Lane -eq 'first-party'
$runtimeName=if($firstParty){'runtime-first-party'}else{'runtime'}
$localName=if($firstParty){'task130-first-party'}else{'task130'}
$runnerName=if($firstParty){'phantom-test-first-party'}else{'phantom-test'}
$gamePort=if($firstParty){7961}else{7960}
$loginPort=if($firstParty){9261}else{9260}
$pilotPort=if($firstParty){2261}else{2260}
if($firstParty -and $Fixture -ne 'original'){throw 'TEST130_FIRST_PARTY_FIXTURE'}
if($runtime -ine (Join-Path $parent $runtimeName)){throw 'TEST130_EXACT_SIBLING_RUNTIME_REQUIRED'}
foreach($path in @($source,$parent,$runtime)){
    if((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TEST130_REPARSE_ROOT'}
}
$marker=Join-Path $runtime 'owner130.txt'
function Assert-PortsFree {
    if(@(Get-NetTCPConnection -State Listen | Where-Object {$_.LocalPort -in @($gamePort,$loginPort,$pilotPort)}).Count){throw 'TEST130_PORT_BUSY'}
}
function Assert-Protected {
    foreach($seal in (Get-Content -LiteralPath (Join-Path $parent 'evidence\protected-pre-native.json') -Raw | ConvertFrom-Json)){
        if((Get-FileHash -LiteralPath $seal.path -Algorithm SHA256).Hash -cne $seal.sha256){throw 'TEST130_PROTECTED_HASH_CHANGED'}
    }
}
function Assert-OwnJvmAbsent {
    $own=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -eq 'java.exe' -and $_.CommandLine -and $_.CommandLine.Contains($runtime)})
    if($own.Count){throw 'TEST130_OWN_JVM_ALREADY_PRESENT'}
}
Assert-Protected
Assert-PortsFree
$sqlConfig=Join-Path $module ('.phantom-local\'+$localName+'\Database.ini')
if($Action -eq 'package'){
    if(Test-Path -LiteralPath $runtime){throw 'TEST130_PACKAGE_ALREADY_EXISTS_NO_OVERWRITE'}
    $geo=[IO.Path]::GetFullPath($GeoSource).TrimEnd('\')
    $files=@(Get-ChildItem -LiteralPath $geo -Filter '*.l2j' -File)
    if($files.Count -ne 203 -or ($files | Measure-Object -Property Length -Sum).Sum -ne 1063452308){throw 'TEST130_GEO203_REQUIRED'}
    $null=New-Item -ItemType Directory -Path $runtime
    Copy-Item -LiteralPath (Join-Path $module 'dist\game\config') -Destination $runtime -Recurse
    Copy-Item -LiteralPath (Join-Path $module 'dist\game\log.cfg') -Destination $runtime
    # Stock IXmlReader and script sourcepath require physical cwd/data, regardless of DatapackRoot.
    Copy-Item -LiteralPath (Join-Path $module 'dist\game\data') -Destination $runtime -Recurse
    $null=New-Item -ItemType Directory -Path (Join-Path $runtime 'geodata'),(Join-Path $runtime 'logs'),(Join-Path $runtime 'libs')
    Copy-Item -Path (Join-Path $module 'dist\libs\*.jar') -Destination (Join-Path $runtime 'libs')
    Copy-Item -LiteralPath $sqlConfig -Destination (Join-Path $runtime 'config\Database.ini')
    $encoding=[Text.UTF8Encoding]::new($false)
    $datapack=$runtime
    $relativeGeo=([Uri]::new($datapack+'\')).MakeRelativeUri([Uri]::new((Join-Path $runtime 'geodata')+'\')).ToString()
    $overrides=@{
        'Interface.ini'=@{EnableGUI='False'}
        'Server.ini'=@{LoginHost='127.0.0.1';LoginPort=[string]$loginPort;GameserverHostname='127.0.0.1';GameserverPort=[string]$gamePort;DatapackRoot=$datapack.Replace('\','/')}
        'GeoEngine.ini'=@{GeoDataPath=$relativeGeo}
        'Custom\PhantomPlayers.ini'=@{EnablePhantomSystem='False';PhantomPopulationTarget='3';PhantomPopulationActiveTarget='3';EnablePhantomEcology='True'}
        'Custom\PhantomMerchants.ini'=@{EnableOfflinePhantomMerchants='False'}
        'Custom\OfflineTrade.ini'=@{RestoreOffliners='False'}
    }
    if($firstParty){
        $overrides['Custom\PhantomPlayers.ini']['PhantomPopulationTarget']='2'
        $overrides['Custom\PhantomPlayers.ini']['PhantomPopulationActiveTarget']='2'
        $overrides['Custom\PhantomPlayers.ini']['PhantomEcologyWorldAgeDays']='0'
    }
    foreach($file in $overrides.Keys){
        $path=Join-Path $runtime ('config\'+$file)
        $body=[IO.File]::ReadAllText($path)
        foreach($key in $overrides[$file].Keys){
            $pattern='(?m)^'+[regex]::Escape($key)+'\s*=.*$'
            if([regex]::Matches($body,$pattern).Count -ne 1){throw ('TEST130_CONFIG_KEY_'+$key)}
            $body=[regex]::Replace($body,$pattern,($key+' = '+$overrides[$file][$key]))
        }
        [IO.File]::WriteAllText($path,$body,$encoding)
    }
    $geoManifest=@()
    foreach($file in $files){
        $target=Join-Path $runtime ('geodata\'+$file.Name)
        Copy-Item -LiteralPath $file.FullName -Destination $target
        $hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -cne $hash){throw 'TEST130_GEO_COPY_HASH'}
        $geoManifest+=@{name=$file.Name;sha256=$hash;bytes=$file.Length}
    }
    $geoManifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'geo203.json') -Encoding UTF8
    [IO.File]::WriteAllText($marker,'TASK130:'+([guid]::NewGuid().ToString('N')),$encoding)
    Write-Output 'TEST130_MINIMAL_PACKAGE_PASS;GEO203=PHYSICAL_HASHED_COPY;DATA=ONE_119MB_STOCK_COPY;SERVERS=0'
    return
}
if(!(Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw) -cnotmatch '^TASK130:[0-9a-f]{32}$'){throw 'TEST130_OWNER_MARKER'}
Assert-OwnJvmAbsent
if($Action -eq 'postflight'){
    Write-Output 'TEST130_POSTFLIGHT_OS_PASS;OWN_JVM=0;OWN_LISTENERS=0;PROTECTED=EXACT'
    return
}
$built=Join-Path $module 'dist\libs\GameServer.jar'
$builtHash=(Get-FileHash -LiteralPath $built).Hash
$nativeClaim=Join-Path $runtime 'native130.launch'
if($firstParty){
    if($builtHash -cne '46E0437859939756DF1F5F1A60159DD6D4D1F41D29CAC220BC2E05642627CBC9'){throw 'TEST130_FIRST_PARTY_PINNED_JAR'}
    if((Get-FileHash -LiteralPath $sqlConfig).Hash -cne (Get-FileHash -LiteralPath (Join-Path $runtime 'config\Database.ini')).Hash){throw 'TEST130_FIRST_PARTY_SQL_CONFIG_MISMATCH'}
    if($Action -eq 'native' -and ((Test-Path -LiteralPath $nativeClaim) -or !(Test-Path -LiteralPath (Join-Path $runtime 'fixture130.properties')) -or
        !(Test-Path -LiteralPath (Join-Path $runtime 'fixture-authority.ready')))){
        throw 'TEST130_FIRST_PARTY_FIXTURE_OR_BOOT_BUDGET'
    }
}
$outcomeCheck=Join-Path $PSScriptRoot 'Assert-Coordination130Outcome.ps1'
if($Action -in @('native','restart') -and !(Test-Path -LiteralPath $outcomeCheck -PathType Leaf)){
    throw 'TEST130_OUTCOME_CHECK_REQUIRED'
}
$restartClaim=Join-Path $runtime 'restart130.launch'
if($Action -eq 'restart'){
    if((Test-Path -LiteralPath $restartClaim) -or @(Get-ChildItem -LiteralPath $runtime -Filter 'restart-*.process.json' -File).Count){
        throw 'TEST130_RESTART_ALREADY_ATTEMPTED'
    }
    $first=Get-ChildItem -LiteralPath $runtime -Filter 'native-*.process.json' -File | Sort-Object Name -Descending | Select-Object -First 1
    if(!$first){throw 'TEST130_PRIOR_NATIVE_ATTEMPT_MISSING'}
    $firstAttempt=$first.Name -creplace '\.process\.json$',''
    & $outcomeCheck -RuntimeRoot $runtime -Attempt $firstAttempt -Fixture $Fixture -JarHash $builtHash
}
$java=(Get-Command java -CommandType Application -ErrorAction Stop).Source
$dependencies=@(Get-ChildItem -LiteralPath (Join-Path $runtime 'libs') -Filter '*.jar' -File | Where-Object {$_.Name -notlike 'GameServer*' -and $_.Name -notmatch '-(sources|javadoc)\.jar$'} | ForEach-Object FullName)
$testRoot=Join-Path (Join-Path $source 'build') $runnerName
$testBin=Join-Path $testRoot 'bin'
$classTail=($dependencies -join ';')+';'+$testBin+';'+(Join-Path $testRoot 'resources')
if($Action -in @('artifact-check','native','restart')){
    $javap=Join-Path (Split-Path $java -Parent) 'javap.exe'
    if(!(Test-Path -LiteralPath $javap -PathType Leaf)){throw 'TEST130_JAVAP_REQUIRED'}
    $inspection='org.l2jmobius.gameserver.phantoms.PhantomSystem$PlanningInspection'
    $api=(& $javap -s -classpath $built $inspection) -join [char]10
    if($LASTEXITCODE -ne 0){throw 'TEST130_JAR_PLANNING_INSPECTION_CLASS_REQUIRED'}
    $classpathForInspection=$built+';'+$classTail
    $runnerClasses=@('PhantomCoordination130Native','PhantomCoordination130Integration')
    $witnesses=@{}
    foreach($name in $runnerClasses){
        $type='org.l2jmobius.tests.phantoms.'+$name
        $expected=(Join-Path $testBin ($type.Replace('.','\')+'.class')).Replace('\','/')
        $body=@(& $javap -sysinfo -c -p -classpath $classpathForInspection $type)
        if($LASTEXITCODE -ne 0){throw ('TEST130_RUNNER_BYTECODE_REQUIRED '+$name)}
        if(!$body[0].StartsWith('Classfile ') -or $body[0].Substring(10).TrimStart('/') -ine $expected){
            throw ('TEST130_RUNNER_ORIGIN_MISMATCH '+$name)
        }
        $witnesses[$name]=$body
    }
    $missing=@()
    $accessors=[ordered]@{
        managedPartyPage='()Ljava/util/function/BiFunction;'
        partyOffer='()Ljava/util/function/BiFunction;'
    }
    foreach($method in $accessors.Keys){
        $descriptor=[regex]::Escape($accessors[$method])
        $call=[regex]::Escape($inspection.Replace('.','/')+'.'+$method+':')
        if($api -cnotmatch ('\s'+$method+'\(\);\s+descriptor: '+$descriptor)){$missing+=('jar.'+$method)}
        if(!($witnesses['PhantomCoordination130Native'] -cmatch ('// Method '+$call+$descriptor))){$missing+=('runner.'+$method)}
    }
    if(!($witnesses['PhantomCoordination130Native'] -cmatch 'invokestatic\s+#[0-9]+\s+// Method exactGroupRewards:\(Lorg/l2jmobius/gameserver/model/actor/PlayerNativeEvidence\$Target;Ljava/util/List;\)Ljava/util/List;')){
        $missing+='runner.exactGroupRewards'
    }
    if($missing.Count){throw ('TEST130_SHARED_ROUTE_LINKAGE_REFUSED missing='+($missing -join ',')+' jar='+$builtHash)}
    Write-Output ('TEST130_SHARED_ROUTE_LINKAGE_PASS jar='+$builtHash+' runner='+$testBin)
    if($Action -eq 'artifact-check'){return}
}
$request=Join-Path $parent ('g0-native130-'+[guid]::NewGuid().ToString('N'))
if($firstParty){
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Invoke-Coordination130FirstPartySql.ps1') -Action attest
}else{
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Invoke-G0IdentityAttestation.ps1') -RequestDirectory $request
}
if($LASTEXITCODE -ne 0){throw 'TEST130_G0_IDENTITY_FAILED'}
$jar=Join-Path $runtime ('libs\GameServer.'+$builtHash+'.jar')
if(!(Test-Path -LiteralPath $jar)){Copy-Item -LiteralPath $built -Destination $jar}
if((Get-FileHash -LiteralPath $jar).Hash -cne $builtHash){throw 'TEST130_FINAL_JAR_DIFFERS'}
$classpath=$jar+';'+$classTail
$javaArgs=@('-XX:ActiveProcessorCount=4','-Xms512m','-Xmx4g',('-Dphantom.task130.runtime="'+$runtime+'"'),('-Dphantom.task130.jar="'+$jar+'"'),('-Dphantom.task130.fixture='+$Fixture),('-Dphantom.task130.lane='+$Lane),'-Djava.util.logging.config.file=log.cfg','-cp',('"'+$classpath+'"'),'org.l2jmobius.tests.phantoms.PhantomCoordination130Integration',$Action,('"'+$module+'"'))
$attempt=$Action+'-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$out=Join-Path $runtime ($attempt+'.stdout.log');$err=Join-Path $runtime ($attempt+'.stderr.log')
if($Action -eq 'restart'){
    $reservation=[IO.File]::Open($restartClaim,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $reservation.Dispose()
}
if($firstParty -and $Action -eq 'native'){
    $reservation=[IO.File]::Open($nativeClaim,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $reservation.Dispose()
}
$process=Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $runtime -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
$processHandle=$process.Handle
$fact=Get-CimInstance Win32_Process -Filter ('ProcessId='+$process.Id)
$owner=Invoke-CimMethod -InputObject $fact -MethodName GetOwner
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
if(!$fact -or $fact.ExecutablePath -ine $java -or !$fact.CommandLine.Contains($runtime) -or ($owner.Domain+'\'+$owner.User) -ine $identity){throw 'TEST130_STARTED_IDENTITY_MISMATCH'}
@{pid=$process.Id;created=$fact.CreationDate.ToUniversalTime().ToString('o');java=$java;owner=$identity;jar=(Get-FileHash -LiteralPath $jar).Hash;phase=$Action;stdout=$out;stderr=$err} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime ($attempt+'.process.json')) -Encoding UTF8
Write-Output ('TEST130_STARTED_OWNED_PID='+$process.Id+';PHASE='+$Action+';NORMAL_SHUTDOWN_ONLY=true')
$process.WaitForExit()
$process.Refresh()
@{pid=$process.Id;exitCode=$process.ExitCode;phase=$Action;finished=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime ($attempt+'.exit.json')) -Encoding UTF8
Write-Output ('TEST130_OWN_EXIT='+$process.ExitCode+';PHASE='+$Action)
if($Action -in @('native','restart')){
    Assert-OwnJvmAbsent
    Assert-PortsFree
    Assert-Protected
    Write-Output 'TEST130_POSTFLIGHT_OS_PASS;OWN_JVM=0;OWN_LISTENERS=0;PROTECTED=EXACT'
    if($process.ExitCode -eq 0){
        & $outcomeCheck -RuntimeRoot $runtime -Attempt $attempt -Fixture $Fixture -JarHash $builtHash
    }
}
exit $process.ExitCode
