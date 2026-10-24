[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Prepare','Compile','Native','Build','Helpers')][string]$Action,
      [ValidatePattern('^[a-z0-9-]+$')][string]$Mode='preflight', [string]$SourceCommit='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$task=Join-Path $repo '.phantom-local/task074'
$candidate=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Living-Systems-074-candidate'
$owner='TASK074_LIVING_SYSTEMS'
foreach($path in @($module,$task,$candidate)){Assert-Plain073 $path}
if($Action -eq 'Prepare'){
    if((Test-Path -LiteralPath $task) -or (Test-Path -LiteralPath $candidate)){throw 'OWN074 creation refuses an existing root; preserve it.'}
    if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -in @(2254,7954,9254,2106,7777,9154)).Count){throw 'OWN074 BLOCKED_PORT; no foreign process action.'}
    $geo=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Personal-Adventure-067-candidate/game/data/geodata'
    Assert-Plain073 $geo
    $regions=@(Get-ChildItem -LiteralPath $geo -Filter '*.l2j' -File)
    if($regions.Count -ne 203){throw 'OWN074 requires existing physical203 GeoData.'}
    $database=[IO.File]::ReadAllText((Join-Path $module 'dist/game/config/Database.ini'))
    if($database -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5\?'){throw 'OWN074 operator database input endpoint differs.'}
    New-Item -ItemType Directory -Path $task,(Join-Path $task 'private'),(Join-Path $task 'reports'),(Join-Path $task 'native-test/game') | Out-Null
    Set-Private073 (Join-Path $task 'private')
    Write-Text073 (Join-Path $task 'ownership074.properties') "version=1`nowner=$owner`ntaskRoot=$($task.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_074`nreleaseSchema=l2jmobiush5_phantom_release_074`ninstallationId=$([guid]::NewGuid().ToString())`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n"
    foreach($kind in @('test','release')){
        $settings=[regex]::Replace($database,'(?m)^URL\s*=.*$',"URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_$($kind)_074?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8")
        $settings=[regex]::Replace($settings,'(?m)^MaximumDatabaseConnections\s*=.*$','MaximumDatabaseConnections = 4')
        Write-Text073 (Join-Path $task "private/Database.$kind.ini") $settings
    }
    foreach($part in @('config','data')){Copy-Item -LiteralPath (Join-Path $module "dist/game/$part") -Destination (Join-Path $task 'native-test/game') -Recurse}
    Copy-Item -LiteralPath (Join-Path $task 'private/Database.test.ini') -Destination (Join-Path $task 'native-test/game/config/Database.ini')
    $rows=@(foreach($region in $regions){
        Assert-Plain073 $region.FullName
        $hash=(Get-FileHash -LiteralPath $region.FullName -Algorithm SHA256).Hash
        $target=Join-Path $task ('native-test/game/data/geodata/'+$region.Name)
        if(Test-Path -LiteralPath $target){throw 'OWN074 duplicate Geo target.'}
        Copy-Item -LiteralPath $region.FullName -Destination $target
        if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash){throw 'OWN074 Geo copy differs.'}
        [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$region.Name);source=$region.FullName;bytes=$region.Length;sha256=$hash}
    })
    $rows | Export-Csv -LiteralPath (Join-Path $task 'GEODATA074.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    $protected=@((Join-Path $module 'dist/game/config/Database.ini'),(Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Personal-Adventure-067-candidate/libs/GameServer.jar'))
    @($protected | ForEach-Object {[pscustomobject]@{path=$_;sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash}}) | Export-Csv -LiteralPath (Join-Path $task 'PROTECTED074.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    Write-Output 'OWN074_PREPARED physicalGeo203; no DB/process writes; fresh UUID/private settings.'
    return
}
if((Read-Field073 (Join-Path $task 'ownership074.properties') 'owner') -cne $owner){throw 'OWN074 task authority differs.'}
if($Action -eq 'Compile'){
    $build=Join-Path $task 'focused'
    $ErrorActionPreference='Continue'
    & ant -f (Join-Path $module 'build.xml') "-Dbuild=$build" compile-tests *> (Join-Path $task 'reports/focused-compile.log')
    $code=$LASTEXITCODE; $ErrorActionPreference='Stop'
    if($code){Get-Content -LiteralPath (Join-Path $task 'reports/focused-compile.log') -Tail 35;throw 'OWN074 compile failed.'}
    Write-Output "OWN074_COMPILED $build"
    return
}
if($Action -eq 'Build'){
    if($SourceCommit -cnotmatch '^[0-9a-f]{40}$' -or (& git symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git rev-parse HEAD) -cne $SourceCommit){throw 'OWN074 exact committed feature HEAD required.'}
    $archive=Join-Path $task 'source074.zip'; $source=Join-Path $task 'source-final'; $build=Join-Path $task 'build-final'
    foreach($path in @($archive,$source,$build)){if(Test-Path -LiteralPath $path){throw 'OWN074 final artifacts already exist; no overwrite.'}}
    & git archive --format=zip "--output=$archive" $SourceCommit -- 'L2J_Mobius_CT_2.6_HighFive'
    if($LASTEXITCODE){throw 'OWN074 committed archive failed.'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($archive,$source)
    $committed=Join-Path $source 'L2J_Mobius_CT_2.6_HighFive'
    $ErrorActionPreference='Continue'
    & ant -f (Join-Path $committed 'build.xml') "-Dbuild=$build" jar compile-tests *> (Join-Path $task 'reports/final-build.log')
    $code=$LASTEXITCODE; $ErrorActionPreference='Stop'
    if($code){Get-Content -LiteralPath (Join-Path $task 'reports/final-build.log') -Tail 35;throw 'OWN074 final build failed.'}
    $receipt=[ordered]@{owner=$owner;sourceState='COMMITTED';sourceCommit=$SourceCommit;sourceArchive=$archive;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash;sourceTreeSha256=(Get-TreeHash073 $committed @('build.xml','java','test','tools','dist') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar'));gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash;loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build 'dist/libs/LoginServer.jar') -Algorithm SHA256).Hash;testTreeSha256=(Get-TreeHash073 (Join-Path $build 'phantom-test') @('bin','resources'));builtUtc=[DateTime]::UtcNow.ToString('o')}
    Write-Text073 (Join-Path $task 'BUILD074.json') ($receipt | ConvertTo-Json)
    Write-Output ($receipt | ConvertTo-Json)
    return
}
if($Action -eq 'Helpers'){
    if($SourceCommit -cnotmatch '^[0-9a-f]{40}$' -or (& git symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git rev-parse HEAD) -cne $SourceCommit){throw 'OWN074 exact committed helper HEAD required.'}
    $info=Read-Json073 (Join-Path $task 'BUILD074.json'); $build=Join-Path $task 'build-final'
    if((Get-FileHash -LiteralPath (Join-Path $build 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash -ine $info.gameJarSha256){throw 'OWN074 helper production JAR differs.'}
    $suffix=$SourceCommit.Substring(0,12)
    $archive=Join-Path $task "helpers074-$suffix.zip"; $source=Join-Path $task "helpers-source-$suffix"; $classes=Join-Path $task "helpers-bin-$suffix"
    foreach($path in @($archive,$source,$classes,(Join-Path $task "HELPERS074-$suffix.json"))){if(Test-Path -LiteralPath $path){throw 'OWN074 helper version exists; no overwrite.'}}
    & git archive --format=zip "--output=$archive" $SourceCommit -- 'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms'
    if($LASTEXITCODE){throw 'OWN074 committed helper archive failed.'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($archive,$source)
    New-Item -ItemType Directory -Path $classes | Out-Null
    $files=@('PhantomLivingSystems074Native.java','PhantomSupply074Native.java','PhantomFarm074OwnedNative.java','PhantomMarket074Native.java','PhantomPvp074Native.java') | ForEach-Object {Join-Path $source ('L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/'+$_)}
    $libraries=@(Get-ChildItem -LiteralPath (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Where-Object {$_.Name -notmatch '(-sources|GameServer|LoginServer)\.jar$'} | ForEach-Object FullName)
    $cp=(Join-Path $build 'dist/libs/GameServer.jar')+';'+(Join-Path $build 'phantom-test/bin')+';'+($libraries -join ';')
    $ErrorActionPreference='Continue'
    & javac '-encoding' 'UTF-8' '-cp' $cp '-d' $classes @files *> (Join-Path $task 'reports/helper-compile.log')
    $code=$LASTEXITCODE; $ErrorActionPreference='Stop'
    if($code){Get-Content -LiteralPath (Join-Path $task 'reports/helper-compile.log') -Tail 35;throw 'OWN074 helper compile failed.'}
    $receipt=[ordered]@{owner=$owner;sourceState='COMMITTED';sourceCommit=$SourceCommit;sourceArchive=$archive;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash;classRoot=$classes;classTreeSha256=(Get-TreeHash073 $classes @('.'));gameJarSha256=$info.gameJarSha256;compiledUtc=[DateTime]::UtcNow.ToString('o')}
    if(Test-Path -LiteralPath (Join-Path $task 'HELPERS074.json')){
        $prior=Read-Json073 (Join-Path $task 'HELPERS074.json'); $retained=Join-Path $task ('HELPERS074-'+$prior.sourceCommit.Substring(0,12)+'.json')
        if(!(Test-Path -LiteralPath $retained)){Copy-Item -LiteralPath (Join-Path $task 'HELPERS074.json') -Destination $retained}
    }
    Write-Text073 (Join-Path $task "HELPERS074-$suffix.json") ($receipt | ConvertTo-Json)
    Write-Text073 (Join-Path $task 'HELPERS074.json') ($receipt | ConvertTo-Json)
    Write-Output ($receipt | ConvertTo-Json)
    return
}
$build=if(Test-Path -LiteralPath (Join-Path $task 'BUILD074.json')){Join-Path $task 'build-final'}else{Join-Path $task 'focused'}
$production=if(Test-Path -LiteralPath (Join-Path $task 'BUILD074.json')){Join-Path $build 'dist/libs/GameServer.jar'}else{Join-Path $build 'bin'}
$cp=$production+';'+(Join-Path $build 'phantom-test/bin')+';'+(Join-Path $build 'phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
if(Test-Path -LiteralPath (Join-Path $task 'HELPERS074.json')){
    $helper=Read-Json073 (Join-Path $task 'HELPERS074.json'); $classes=Join-Path $task 'helpers-bin'
    if($helper.PSObject.Properties.Name -contains 'classRoot'){
        $classes=$helper.classRoot
        if([IO.Path]::GetFullPath($classes) -ine (Join-Path $task ('helpers-bin-'+$helper.sourceCommit.Substring(0,12)))){throw 'OWN074 helper classes outside exact committed version.'}
    }
    if($helper.owner -cne $owner -or $helper.sourceState -cne 'COMMITTED' -or (Get-FileHash -LiteralPath $helper.sourceArchive -Algorithm SHA256).Hash -ine $helper.sourceArchiveSha256 -or (Get-TreeHash073 $classes @('.')) -ine $helper.classTreeSha256 -or (Get-FileHash -LiteralPath $production -Algorithm SHA256).Hash -ine $helper.gameJarSha256){throw 'OWN074 committed helper/JAR provenance differs.'}
    $cp=$production+';'+$classes+';'+(Join-Path $build 'phantom-test/bin')+';'+(Join-Path $build 'phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
}
$log=Join-Path $task ('reports/native-'+$Mode+'-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.log')
Push-Location (Join-Path $task 'native-test/game')
try{
    $ErrorActionPreference='Continue'
    & java '-Xms2g' '-Xmx4g' '-Dfile.encoding=UTF-8' '-Duser.language=en' "-Dphantom.module.root=$module" "-Dphantom.test.config=$(Join-Path $task 'native-test/game/config/Database.ini')" '-Dphantom.task074.native=true' -cp $cp org.l2jmobius.tests.phantoms.PhantomLivingSystems074Native $Mode *> $log
    $code=$LASTEXITCODE; $ErrorActionPreference='Stop'
}finally{Pop-Location}
Get-Content -LiteralPath $log -Tail 25
if($code){throw "OWN074 native $Mode exit=$code; log=$log"}
Write-Output "OWN074_NATIVE $Mode exit=0; log=$log"
