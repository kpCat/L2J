[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Test')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain067([string]$Path){
    $absolute067=[IO.Path]::GetFullPath($Path)
    $walk067=[IO.Path]::GetPathRoot($absolute067)
    foreach($part067 in $absolute067.Substring($walk067.Length).Split('\')){
        if(!$part067){continue}
        $walk067=Join-Path $walk067 $part067
        if((Test-Path -LiteralPath $walk067) -and ((Get-Item -LiteralPath $walk067 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK067: plain physical paths required.'}
    }
}
function Read-Field067([string]$Path,[string]$Key){
    Assert-Plain067 $Path
    $matches067=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches067.Count -ne 1){throw "TASK067: need one key $Key."}
    return $matches067[0].Groups[1].Value.Trim()
}
function Get-TestTreeHash067([string]$Root){
    $lines067=@(foreach($tree067 in @('bin','resources')){
        $treeRoot067=Join-Path $Root "phantom-test/$tree067"
        if(!(Test-Path -LiteralPath $treeRoot067 -PathType Container)){throw 'TASK067: compiled test tree missing.'}
        foreach($file067 in Get-ChildItem -LiteralPath $treeRoot067 -Recurse -File | Sort-Object FullName){
            Assert-Plain067 $file067.FullName
            $tree067+'/'+$file067.FullName.Substring($treeRoot067.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file067.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines067.Count){throw 'TASK067: empty compiled test tree.'}
    $sha067=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha067.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines067 -join "`n"))))).Replace('-','')}
    finally{$sha067.Dispose()}
}
function Get-CommittedTreeHash067([string]$Root){
    $lines067=@(foreach($entry067 in @('build.xml','java','test','dist','tools')){
        $entryRoot067=Join-Path $Root $entry067
        foreach($file067 in Get-ChildItem -LiteralPath $entryRoot067 -Recurse -File | Sort-Object FullName){
            Assert-Plain067 $file067.FullName
            $relative067=$file067.FullName.Substring($Root.Length+1).Replace('\','/')
            if($relative067 -cin @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')){continue}
            $relative067+':'+(Get-FileHash -LiteralPath $file067.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines067.Count){throw 'TASK067: empty committed source tree.'}
    $sha067=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha067.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines067 -join "`n"))))).Replace('-','')}
    finally{$sha067.Dispose()}
}
function Set-Config067([string]$Relative,[string]$Key,[string]$Value){
    $path067=Join-Path $output067 $Relative
    Assert-Plain067 $path067
    $text067=[IO.File]::ReadAllText($path067)
    $pattern067='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text067,$pattern067).Count -ne 1){throw "TASK067: need one $Key in $Relative."}
    $text067=[regex]::Replace($text067,$pattern067,{param($match067) $match067.Groups[1].Value+$Value})
    [IO.File]::WriteAllText($path067,$text067,[Text.UTF8Encoding]::new($false))
}
function Set-Private067([string]$Relative){
    $path067=Join-Path $output067 $Relative
    Assert-Plain067 $path067
    $sid067='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant067=if((Get-Item -LiteralPath $path067).PSIsContainer){$sid067+':(OI)(CI)F'}else{$sid067+':F'}
    & icacls.exe $path067 /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant067 | Out-Null
    if($LASTEXITCODE){throw 'TASK067: private DACL operation failed.'}
}

$module067=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source067=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build067=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output067=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$task067=Join-Path (Split-Path $module067 -Parent) '.phantom-local/task067'
foreach($path067 in @($module067,$source067,$build067,$output067,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain067 $path067}
if((Split-Path $module067 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive' -or !$source067.StartsWith($task067+'\',[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $build067 -Parent) -ine $task067 -or (Split-Path $build067 -Leaf) -cnotmatch '^build-(?:final-)?[a-z0-9-]+$'){throw 'TASK067: own module/source/distinct build paths required.'}
$buildOwner067=Join-Path $build067 'BUILD_OWNERSHIP067.properties'
if((Read-Field067 $buildOwner067 'version') -cne '1' -or (Read-Field067 $buildOwner067 'owner') -cne 'TASK067_PERSONAL_ADVENTURE' -or [IO.Path]::GetFullPath((Read-Field067 $buildOwner067 'buildRoot')).TrimEnd('\') -ine $build067){throw 'TASK067: exact build ownership required.'}
if(Test-Path -LiteralPath $output067){throw 'TASK067: output exists; no overwrite.'}
if($Kind -ceq 'Test'){
    if($output067 -ine (Join-Path $task067 'native-test')){throw 'TASK067: exact test root required.'}
    $schema067='l2jmobiush5_personal_test_067';$role067='MUTABLE_FIXTURE';$ports067=@(2248,7948,9248)
}else{
    if($output067 -ine (Join-Path ([IO.Path]::GetPathRoot($module067)) 'PhantomWorld/Personal-Adventure-067-candidate')){throw 'TASK067: exact authorized external candidate path required.'}
    $schema067='l2jmobiush5_personal_release_067';$role067='RELEASE_CANDIDATE';$ports067=@(2148,7848,9148)
}
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in $ports067}).Count){throw 'TASK067: selected own ports are occupied; preserve the owner.'}
$javaRelease067=Join-Path $JavaHome 'release'
if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')) -or [IO.File]::ReadAllText($javaRelease067) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK067: JDK25 required.'}
$buildInfoPath067=Join-Path $build067 'BUILD_INFO067.json'
Assert-Plain067 $buildInfoPath067
$buildInfo067=Get-Content -LiteralPath $buildInfoPath067 -Raw | ConvertFrom-Json
if($buildInfo067.format -ne 1 -or $buildInfo067.owner -cne 'TASK067_PERSONAL_ADVENTURE' -or $buildInfo067.sourceState -cne 'COMMITTED' -or $buildInfo067.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($buildInfo067.sourceModuleRoot).TrimEnd('\') -ine $module067 -or [IO.Path]::GetFullPath($buildInfo067.committedModuleRoot).TrimEnd('\') -ine $source067 -or [IO.Path]::GetFullPath($buildInfo067.buildRoot).TrimEnd('\') -ine $build067){throw 'TASK067: committed build receipt binding differs.'}
$archive067=[IO.Path]::GetFullPath($buildInfo067.sourceArchivePath)
Assert-Plain067 $archive067
if(!$archive067.StartsWith($task067+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $archive067 -Algorithm SHA256).Hash -ine $buildInfo067.sourceArchiveSha256 -or (Get-TestTreeHash067 $build067) -ine $buildInfo067.testTreeSha256 -or $buildInfo067.committedTreeSha256 -cnotmatch '^[0-9A-Fa-f]{64}$' -or (Get-CommittedTreeHash067 $source067) -ine $buildInfo067.committedTreeSha256){throw 'TASK067: archive/source/test tree provenance differs.'}
foreach($kind067 in @('GameServer','LoginServer')){
    $jar067=Join-Path $build067 "dist/libs/$kind067.jar"
    Assert-Plain067 $jar067
    $key067=if($kind067 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath $jar067 -Algorithm SHA256).Hash -ine $buildInfo067.$key067){throw 'TASK067: built JAR provenance differs.'}
}
$dist067=Join-Path $source067 'dist'
Assert-Plain067 $dist067
if(@(Get-ChildItem -LiteralPath $dist067 -Recurse -Force | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'TASK067: source data contains an alias.'}
$databasePath067=[IO.Path]::GetFullPath($DatabaseConfig)
if((Get-Item -LiteralPath $databasePath067).Length -gt 8192 -or (Read-Field067 $databasePath067 'URL') -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema067+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK067: own database config differs.'}
$database067=[IO.File]::ReadAllText($databasePath067)
if((Get-Item -LiteralPath $RegistrationFile).Length -gt 8192){throw 'TASK067: bounded private registration required.'}
$geodata067=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t" | Where-Object kind -eq 'GeoData')
if($geodata067.Count -ne 203 -or @($geodata067.relativePath | Select-Object -Unique).Count -ne 203 -or @($geodata067.source | Select-Object -Unique).Count -ne 203){throw 'TASK067: expected 203 unique inventoried regions.'}
foreach($geo067 in $geodata067){
    $physical067=[IO.Path]::GetFullPath($geo067.source)
    Assert-Plain067 $physical067
    $file067=Get-Item -LiteralPath $physical067 -Force
    if($file067.PSIsContainer -or $file067.Length -ne [long]$geo067.bytes -or (Get-FileHash -LiteralPath $physical067 -Algorithm SHA256).Hash -ine $geo067.sha256 -or $geo067.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file067.Name -cne (Split-Path $geo067.relativePath -Leaf)){throw 'TASK067: GeoData source identity changed.'}
    $protectedGeo067=Join-Path ([IO.Path]::GetPathRoot($module067)) 'PhantomWorld/Farm-Economy-066-R2-candidate/game/data/geodata'
    if((Split-Path $physical067 -Parent) -ine $protectedGeo067){throw 'TASK067: inventoried read-only066R2 GeoData source required.'}
}

New-Item -ItemType Directory -Path $output067 | Out-Null
[IO.File]::WriteAllText((Join-Path $output067 'assembly-ownership067.properties'),"version=1`nowner=TASK067_PERSONAL_ADVENTURE`nrole=$role067`nschema=$schema067`nruntimeRoot=$($output067.Replace('\','/'))`nsourceModuleRoot=$($module067.Replace('\','/'))`nsourceCommit=$SourceCommit`n",[Text.UTF8Encoding]::new($false))
Copy-Item -Path (Join-Path $dist067 '*') -Destination $output067 -Recurse
foreach($kind067 in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build067 "dist/libs/$kind067.jar") -Destination (Join-Path $output067 "libs/$kind067.jar") -Force}
foreach($folder067 in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs')){New-Item -ItemType Directory -Path (Join-Path $output067 $folder067) -Force | Out-Null}
foreach($private067 in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private067 $private067}
foreach($kind067 in @('game','login')){[IO.File]::WriteAllText((Join-Path $output067 "$kind067/config/Database.ini"),$database067,[Text.UTF8Encoding]::new($false))}
$registration067=Join-Path $output067 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration067)){[IO.File]::WriteAllBytes($registration067,[byte[]]@())}
Set-Private067 'game/config/hexid.txt'
[IO.File]::WriteAllBytes($registration067,[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($RegistrationFile)))
foreach($geo067 in $geodata067){
    $destination067=Join-Path $output067 $geo067.relativePath
    Assert-Plain067 $destination067
    Copy-Item -LiteralPath $geo067.source -Destination $destination067
    if((Get-Item -LiteralPath $destination067).Length -ne [long]$geo067.bytes -or (Get-FileHash -LiteralPath $destination067 -Algorithm SHA256).Hash -ine $geo067.sha256){throw 'TASK067: physical GeoData copy verification failed.'}
}
$geoReceipt067=Join-Path $output067 'GEODATA067.tsv'
$geodata067 | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt067 -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config067 'game/config/Server.ini' 'GameserverPort' ([string]$ports067[1])
Set-Config067 'game/config/Server.ini' 'LoginPort' ([string]$ports067[2])
Set-Config067 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config067 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config067 'login/config/Server.ini' 'LoginserverPort' ([string]$ports067[0])
Set-Config067 'login/config/Server.ini' 'LoginPort' ([string]$ports067[2])
Set-Config067 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config067 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
$tools067=Join-Path $output067 'tools'
New-Item -ItemType Directory -Path $tools067 -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source067 'tools/phantom-runtime/Runtime067.ps1') -Destination $tools067
Copy-Item -LiteralPath (Join-Path $source067 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools067
foreach($action067 in @('START','STATUS','STOP')){
    [IO.File]::WriteAllText((Join-Path $output067 "$action067.cmd"),"@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime067.ps1`" -Action $action067 -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n",[Text.Encoding]::ASCII)
}
$game067=(Get-FileHash -LiteralPath (Join-Path $output067 'libs/GameServer.jar') -Algorithm SHA256).Hash
$login067=(Get-FileHash -LiteralPath (Join-Path $output067 'libs/LoginServer.jar') -Algorithm SHA256).Hash
$helperHash067=(Get-FileHash -LiteralPath (Join-Path $tools067 'LocalPlay-Ownership.ps1') -Algorithm SHA256).Hash
$runtimeHash067=(Get-FileHash -LiteralPath (Join-Path $tools067 'Runtime067.ps1') -Algorithm SHA256).Hash
$info067=[ordered]@{format=1;owner='TASK067_PERSONAL_ADVENTURE';sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema067;sourceArchiveSha256=$buildInfo067.sourceArchiveSha256;committedTreeSha256=$buildInfo067.committedTreeSha256;testTreeSha256=$buildInfo067.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $buildInfoPath067 -Algorithm SHA256).Hash;gameJarSha256=$game067;loginJarSha256=$login067;ownershipHelperSha256=$helperHash067;runtimeToolSha256=$runtimeHash067;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt067 -Algorithm SHA256).Hash;clientLoginPort=$ports067[0];gamePort=$ports067[1];controlPort=$ports067[2]}
[IO.File]::WriteAllText((Join-Path $output067 'BUILD_INFO067.json'),($info067 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $buildInfoPath067 -Destination (Join-Path $output067 'SOURCE_BUILD_INFO067.json')
$manifest067=[ordered]@{format=1;owner='TASK067_PERSONAL_ADVENTURE';codeSha=$SourceCommit;databaseName=$schema067;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$game067;loginJarSha256=$login067;clientLoginPort=$ports067[0];gamePort=$ports067[1];controlPort=$ports067[2]}
[IO.File]::WriteAllText((Join-Path $output067 'local-play.json'),($manifest067 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$owner067="version=1`nowner=TASK067_PERSONAL_ADVENTURE`nrole=$role067`nschema=$schema067`nruntimeRoot=$($output067.Replace('\','/'))`nsourceModuleRoot=$($module067.Replace('\','/'))`nclientLoginPort=$($ports067[0])`ngamePort=$($ports067[1])`ncontrolPort=$($ports067[2])`ngameJarSha256=$game067`nloginJarSha256=$login067`nownershipHelperSha256=$helperHash067`nruntimeToolSha256=$runtimeHash067`n"
[IO.File]::WriteAllText((Join-Path $output067 'ownership067.properties'),$owner067,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $output067 'runtime.local.ini'),"JavaHome=$JavaHome`nDatabaseName=$schema067`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=180`nStopTimeoutSeconds=180`n",[Text.UTF8Encoding]::new($false))
Set-Private067 'runtime.local.ini'
$startHere067=@"
# TASK067 — самостоятельная поставка

Тип: $Kind. Исходники: $SourceCommit. JDK25, штатные LoginServer и GameServer.
Собственная БД: $schema067 на 127.0.0.1:3308.
Порты: login=$($ports067[0]), game=$($ports067[1]), registration=$($ports067[2]).

START.cmd запускает только эту поставку; STATUS.cmd показывает проверенные PID и состояние; STOP.cmd запрашивает штатное завершение. Принудительного завершения нет. Перед повторным запуском обе роли должны быть STOPPED, порты свободны.

Настройки личных возможностей находятся в game/config/Custom/PersonalCharacterQoL.ini. Master и личный allowlist обязательны, новые переключатели по умолчанию выключены. Client QA отложена владельцем; успешная сборка и запуск сами по себе не доказывают игровые сценарии.

BUILD_INFO067.json и SOURCE_BUILD_INFO067.json связывают исходники, архив, JAR, тесты и tools; GEODATA067.tsv фиксирует 203 физические копии. Секреты остаются в private/config файлах и в Git не публикуются. Прежние поставки и их БД не изменяются.
"@
[IO.File]::WriteAllText((Join-Path $output067 'START_HERE_067_RU.md'),$startHere067,[Text.UTF8Encoding]::new($true))
& (Join-Path $tools067 'Runtime067.ps1') -Action Validate -RuntimeRoot $output067
Write-Output "TASK067_ASSEMBLED kind=$Kind; regions=$($geodata067.Count); source=$SourceCommit; DB_WRITES=0; PROCESS_ACTIONS=0"
