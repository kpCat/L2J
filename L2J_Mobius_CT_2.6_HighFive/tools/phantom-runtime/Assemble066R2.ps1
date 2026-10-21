[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Test')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain066([string]$Path){
    $absolute066=[IO.Path]::GetFullPath($Path)
    $walk066=[IO.Path]::GetPathRoot($absolute066)
    foreach($part066 in $absolute066.Substring($walk066.Length).Split('\')){
        if(!$part066){continue}
        $walk066=Join-Path $walk066 $part066
        if((Test-Path -LiteralPath $walk066) -and ((Get-Item -LiteralPath $walk066 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK066: plain physical paths required.'}
    }
}
function Read-Field066([string]$Path,[string]$Key){
    Assert-Plain066 $Path
    $matches066=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches066.Count -ne 1){throw "TASK066: need one key $Key."}
    return $matches066[0].Groups[1].Value.Trim()
}
function Get-TestTreeHash066([string]$Root){
    $lines066=@(foreach($tree066 in @('bin','resources')){
        $treeRoot066=Join-Path $Root "phantom-test/$tree066"
        if(!(Test-Path -LiteralPath $treeRoot066 -PathType Container)){throw 'TASK066: compiled test tree missing.'}
        foreach($file066 in Get-ChildItem -LiteralPath $treeRoot066 -Recurse -File | Sort-Object FullName){
            Assert-Plain066 $file066.FullName
            $tree066+'/'+$file066.FullName.Substring($treeRoot066.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file066.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines066.Count){throw 'TASK066: empty compiled test tree.'}
    $sha066=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha066.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines066 -join "`n"))))).Replace('-','')}
    finally{$sha066.Dispose()}
}
function Set-Config066([string]$Relative,[string]$Key,[string]$Value){
    $path066=Join-Path $output066 $Relative
    Assert-Plain066 $path066
    $text066=[IO.File]::ReadAllText($path066)
    $pattern066='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text066,$pattern066).Count -ne 1){throw "TASK066: need one $Key in $Relative."}
    $text066=[regex]::Replace($text066,$pattern066,{param($match066) $match066.Groups[1].Value+$Value})
    [IO.File]::WriteAllText($path066,$text066,[Text.UTF8Encoding]::new($false))
}
function Set-Private066([string]$Relative){
    $path066=Join-Path $output066 $Relative
    Assert-Plain066 $path066
    $sid066='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant066=if((Get-Item -LiteralPath $path066).PSIsContainer){$sid066+':(OI)(CI)F'}else{$sid066+':F'}
    & icacls.exe $path066 /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant066 | Out-Null
    if($LASTEXITCODE){throw 'TASK066: private DACL operation failed.'}
}

$module066=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source066=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build066=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output066=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$task066r2=Join-Path (Split-Path $module066 -Parent) '.phantom-local/task066r2'
foreach($path066 in @($module066,$source066,$build066,$output066,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain066 $path066}
if((Split-Path $module066 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive' -or !$source066.StartsWith($task066r2+'\',[StringComparison]::OrdinalIgnoreCase) -or ($build066 -ine (Join-Path $task066r2 'build') -and $build066 -ine (Join-Path $task066r2 'build-final'))){throw 'TASK066: own module/source/build paths required.'}
if(Test-Path -LiteralPath $output066){throw 'TASK066: output exists; no overwrite.'}
if($Kind -ceq 'Test'){
    if($output066 -ine (Join-Path $task066r2 'runtime')){throw 'TASK066: exact test root required.'}
    $schema066='l2jmobiush5_phantom_test_066r2';$role066='MUTABLE_FIXTURE';$ports066=@(2247,7947,9247)
}else{
    if($output066 -ine (Join-Path ([IO.Path]::GetPathRoot($module066)) 'PhantomWorld/Farm-Economy-066-R2-candidate')){throw 'TASK066: exact authorized external candidate path required.'}
    $schema066='l2jmobiush5_phantom_release_066r2';$role066='RELEASE_CANDIDATE';$ports066=@(2147,7847,9147)
}
$javaRelease066=Join-Path $JavaHome 'release'
if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')) -or [IO.File]::ReadAllText($javaRelease066) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK066: JDK25 required.'}
$buildInfoPath066=Join-Path $build066 'BUILD_INFO066R2.json'
Assert-Plain066 $buildInfoPath066
$buildInfo066=Get-Content -LiteralPath $buildInfoPath066 -Raw | ConvertFrom-Json
if($buildInfo066.format -ne 1 -or $buildInfo066.owner -cne 'TASK066R2_FARM_ECONOMY' -or $buildInfo066.sourceState -cne 'COMMITTED' -or $buildInfo066.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($buildInfo066.sourceModuleRoot).TrimEnd('\') -ine $module066 -or [IO.Path]::GetFullPath($buildInfo066.committedModuleRoot).TrimEnd('\') -ine $source066 -or [IO.Path]::GetFullPath($buildInfo066.buildRoot).TrimEnd('\') -ine $build066){throw 'TASK066: committed build receipt binding differs.'}
$archive066=[IO.Path]::GetFullPath($buildInfo066.sourceArchivePath)
Assert-Plain066 $archive066
if(!$archive066.StartsWith($task066r2+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $archive066 -Algorithm SHA256).Hash -ine $buildInfo066.sourceArchiveSha256 -or (Get-TestTreeHash066 $build066) -ine $buildInfo066.testTreeSha256){throw 'TASK066: archive/test tree provenance differs.'}
foreach($kind066 in @('GameServer','LoginServer')){
    $jar066=Join-Path $build066 "dist/libs/$kind066.jar"
    Assert-Plain066 $jar066
    $key066=if($kind066 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath $jar066 -Algorithm SHA256).Hash -ine $buildInfo066.$key066){throw 'TASK066: built JAR provenance differs.'}
}
$dist066=Join-Path $source066 'dist'
Assert-Plain066 $dist066
if(@(Get-ChildItem -LiteralPath $dist066 -Recurse -Force | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'TASK066: source data contains an alias.'}
$databasePath066=[IO.Path]::GetFullPath($DatabaseConfig)
if((Get-Item -LiteralPath $databasePath066).Length -gt 8192 -or (Read-Field066 $databasePath066 'URL') -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema066+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK066: own database config differs.'}
$database066=[IO.File]::ReadAllText($databasePath066)
if((Get-Item -LiteralPath $RegistrationFile).Length -gt 8192){throw 'TASK066: bounded private registration required.'}
$geodata066=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t" | Where-Object kind -eq 'GeoData')
if($geodata066.Count -ne 203 -or @($geodata066.relativePath | Select-Object -Unique).Count -ne 203 -or @($geodata066.source | Select-Object -Unique).Count -ne 203){throw 'TASK066: expected 203 unique inventoried regions.'}
foreach($geo066 in $geodata066){
    $physical066=[IO.Path]::GetFullPath($geo066.source)
    Assert-Plain066 $physical066
    $file066=Get-Item -LiteralPath $physical066 -Force
    if($file066.PSIsContainer -or $file066.Length -ne [long]$geo066.bytes -or (Get-FileHash -LiteralPath $physical066 -Algorithm SHA256).Hash -ine $geo066.sha256 -or $geo066.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file066.Name -cne (Split-Path $geo066.relativePath -Leaf)){throw 'TASK066: GeoData source identity changed.'}
    if($physical066 -inotmatch '\\Farm-Economy-066-candidate\\game\\data\\geodata\\[0-9]{2}_[0-9]{2}\.l2j$'){throw 'TASK066R2: inventoried read-only066 GeoData source required.'}
}

New-Item -ItemType Directory -Path $output066 | Out-Null
Copy-Item -Path (Join-Path $dist066 '*') -Destination $output066 -Recurse
foreach($kind066 in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build066 "dist/libs/$kind066.jar") -Destination (Join-Path $output066 "libs/$kind066.jar") -Force}
foreach($folder066 in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs')){New-Item -ItemType Directory -Path (Join-Path $output066 $folder066) -Force | Out-Null}
foreach($private066 in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private066 $private066}
foreach($kind066 in @('game','login')){[IO.File]::WriteAllText((Join-Path $output066 "$kind066/config/Database.ini"),$database066,[Text.UTF8Encoding]::new($false))}
$registration066=Join-Path $output066 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration066)){[IO.File]::WriteAllBytes($registration066,[byte[]]@())}
Set-Private066 'game/config/hexid.txt'
[IO.File]::WriteAllBytes($registration066,[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($RegistrationFile)))
foreach($geo066 in $geodata066){
    $destination066=Join-Path $output066 $geo066.relativePath
    Assert-Plain066 $destination066
    Copy-Item -LiteralPath $geo066.source -Destination $destination066
    if((Get-Item -LiteralPath $destination066).Length -ne [long]$geo066.bytes -or (Get-FileHash -LiteralPath $destination066 -Algorithm SHA256).Hash -ine $geo066.sha256){throw 'TASK066: physical GeoData copy verification failed.'}
}
$geoReceipt066=Join-Path $output066 'GEODATA066R2.tsv'
$geodata066 | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt066 -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config066 'game/config/Server.ini' 'GameserverPort' ([string]$ports066[1])
Set-Config066 'game/config/Server.ini' 'LoginPort' ([string]$ports066[2])
Set-Config066 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config066 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config066 'login/config/Server.ini' 'LoginserverPort' ([string]$ports066[0])
Set-Config066 'login/config/Server.ini' 'LoginPort' ([string]$ports066[2])
Set-Config066 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config066 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
$tools066=Join-Path $output066 'tools'
New-Item -ItemType Directory -Path $tools066 -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Runtime066R2.ps1') -Destination $tools066
Copy-Item -LiteralPath (Join-Path $module066 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools066
foreach($action066 in @('START','STATUS','STOP')){
    [IO.File]::WriteAllText((Join-Path $output066 "$action066.cmd"),"@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime066R2.ps1`" -Action $action066 -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n",[Text.Encoding]::ASCII)
}
$game066=(Get-FileHash -LiteralPath (Join-Path $output066 'libs/GameServer.jar') -Algorithm SHA256).Hash
$login066=(Get-FileHash -LiteralPath (Join-Path $output066 'libs/LoginServer.jar') -Algorithm SHA256).Hash
$helperHash066=(Get-FileHash -LiteralPath (Join-Path $tools066 'LocalPlay-Ownership.ps1') -Algorithm SHA256).Hash
$runtimeHash066=(Get-FileHash -LiteralPath (Join-Path $tools066 'Runtime066R2.ps1') -Algorithm SHA256).Hash
$info066=[ordered]@{format=1;owner='TASK066R2_FARM_ECONOMY';sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema066;sourceArchiveSha256=$buildInfo066.sourceArchiveSha256;testTreeSha256=$buildInfo066.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $buildInfoPath066 -Algorithm SHA256).Hash;gameJarSha256=$game066;loginJarSha256=$login066;ownershipHelperSha256=$helperHash066;runtimeToolSha256=$runtimeHash066;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt066 -Algorithm SHA256).Hash;clientLoginPort=$ports066[0];gamePort=$ports066[1];controlPort=$ports066[2]}
[IO.File]::WriteAllText((Join-Path $output066 'BUILD_INFO066R2.json'),($info066 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $buildInfoPath066 -Destination (Join-Path $output066 'SOURCE_BUILD_INFO066R2.json')
$manifest066=[ordered]@{format=1;owner='TASK066R2_FARM_ECONOMY';codeSha=$SourceCommit;databaseName=$schema066;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$game066;loginJarSha256=$login066;clientLoginPort=$ports066[0];gamePort=$ports066[1];controlPort=$ports066[2];observerCharacter='Task066R2View'}
[IO.File]::WriteAllText((Join-Path $output066 'local-play.json'),($manifest066 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$owner066="version=1`nowner=TASK066R2_FARM_ECONOMY`nrole=$role066`nschema=$schema066`nruntimeRoot=$($output066.Replace('\','/'))`nsourceModuleRoot=$($module066.Replace('\','/'))`nclientLoginPort=$($ports066[0])`ngamePort=$($ports066[1])`ncontrolPort=$($ports066[2])`ngameJarSha256=$game066`nloginJarSha256=$login066`nownershipHelperSha256=$helperHash066`nruntimeToolSha256=$runtimeHash066`n"
[IO.File]::WriteAllText((Join-Path $output066 'ownership066r2.properties'),$owner066,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $output066 'runtime.local.ini'),"JavaHome=$JavaHome`nDatabaseName=$schema066`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=180`nStopTimeoutSeconds=180`n",[Text.UTF8Encoding]::new($false))
Set-Private066 'runtime.local.ini'
& (Join-Path $tools066 'Runtime066R2.ps1') -Action Validate -RuntimeRoot $output066
Write-Output "TASK066_ASSEMBLED kind=$Kind; regions=$($geodata066.Count); source=$SourceCommit; DB_WRITES=0; PROCESS_ACTIONS=0"
