[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Test')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain065([string]$Path){
    $absolute065=[IO.Path]::GetFullPath($Path)
    $walk065=[IO.Path]::GetPathRoot($absolute065)
    foreach($part065 in $absolute065.Substring($walk065.Length).Split('\')){
        if(!$part065){continue}
        $walk065=Join-Path $walk065 $part065
        if((Test-Path -LiteralPath $walk065) -and ((Get-Item -LiteralPath $walk065 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK065: plain physical paths required.'}
    }
}
function Read-Field065([string]$Path,[string]$Key){
    Assert-Plain065 $Path
    $matches065=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($matches065.Count -ne 1){throw "TASK065: need one key $Key."}
    return $matches065[0].Groups[1].Value.Trim()
}
function Get-TestTreeHash065([string]$Root){
    $lines065=@(foreach($tree065 in @('bin','resources')){
        $treeRoot065=Join-Path $Root "phantom-test/$tree065"
        if(!(Test-Path -LiteralPath $treeRoot065 -PathType Container)){throw 'TASK065: compiled test tree missing.'}
        foreach($file065 in Get-ChildItem -LiteralPath $treeRoot065 -Recurse -File | Sort-Object FullName){
            Assert-Plain065 $file065.FullName
            $tree065+'/'+$file065.FullName.Substring($treeRoot065.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file065.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines065.Count){throw 'TASK065: empty compiled test tree.'}
    $sha065=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha065.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines065 -join "`n"))))).Replace('-','')}
    finally{$sha065.Dispose()}
}
function Set-Config065([string]$Relative,[string]$Key,[string]$Value){
    $path065=Join-Path $output065 $Relative
    Assert-Plain065 $path065
    $text065=[IO.File]::ReadAllText($path065)
    $pattern065='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text065,$pattern065).Count -ne 1){throw "TASK065: need one $Key in $Relative."}
    $text065=[regex]::Replace($text065,$pattern065,{param($match065) $match065.Groups[1].Value+$Value})
    [IO.File]::WriteAllText($path065,$text065,[Text.UTF8Encoding]::new($false))
}
function Set-Private065([string]$Relative){
    $path065=Join-Path $output065 $Relative
    Assert-Plain065 $path065
    $sid065='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant065=if((Get-Item -LiteralPath $path065).PSIsContainer){$sid065+':(OI)(CI)F'}else{$sid065+':F'}
    & icacls.exe $path065 /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant065 | Out-Null
    if($LASTEXITCODE){throw 'TASK065: private DACL operation failed.'}
}

$module065=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source065=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build065=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output065=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$task065=Join-Path $module065 '.phantom-local/task065'
foreach($path065 in @($module065,$source065,$build065,$output065,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain065 $path065}
if((Split-Path $module065 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive' -or !$source065.StartsWith($task065+'\',[StringComparison]::OrdinalIgnoreCase) -or ($build065 -ine (Join-Path $task065 'build') -and $build065 -ine (Join-Path $task065 'build-final'))){throw 'TASK065: own module/source/build paths required.'}
if(Test-Path -LiteralPath $output065){throw 'TASK065: output exists; no overwrite.'}
if($Kind -ceq 'Test'){
    if($output065 -ine (Join-Path $task065 'runtime')){throw 'TASK065: exact test root required.'}
    $schema065='l2jmobiush5_phantom_test_065';$role065='MUTABLE_FIXTURE';$ports065=@(2245,7945,9245)
}else{
    if($output065 -ine (Join-Path ([IO.Path]::GetPathRoot($module065)) 'PhantomWorld/Living-Presence-065-candidate')){throw 'TASK065: exact authorized external candidate path required.'}
    $schema065='l2jmobiush5_phantom_release_065a';$role065='RELEASE_CANDIDATE';$ports065=@(2145,7845,9145)
}
$javaRelease065=Join-Path $JavaHome 'release'
if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')) -or [IO.File]::ReadAllText($javaRelease065) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK065: JDK25 required.'}
$buildInfoPath065=Join-Path $build065 'BUILD_INFO065.json'
Assert-Plain065 $buildInfoPath065
$buildInfo065=Get-Content -LiteralPath $buildInfoPath065 -Raw | ConvertFrom-Json
if($buildInfo065.format -ne 1 -or $buildInfo065.owner -cne 'TASK065_LIVING_PRESENCE' -or $buildInfo065.sourceState -cne 'COMMITTED' -or $buildInfo065.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($buildInfo065.sourceModuleRoot).TrimEnd('\') -ine $module065 -or [IO.Path]::GetFullPath($buildInfo065.committedModuleRoot).TrimEnd('\') -ine $source065 -or [IO.Path]::GetFullPath($buildInfo065.buildRoot).TrimEnd('\') -ine $build065){throw 'TASK065: committed build receipt binding differs.'}
$archive065=[IO.Path]::GetFullPath($buildInfo065.sourceArchivePath)
Assert-Plain065 $archive065
if(!$archive065.StartsWith($task065+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $archive065 -Algorithm SHA256).Hash -ine $buildInfo065.sourceArchiveSha256 -or (Get-TestTreeHash065 $build065) -ine $buildInfo065.testTreeSha256){throw 'TASK065: archive/test tree provenance differs.'}
foreach($kind065 in @('GameServer','LoginServer')){
    $jar065=Join-Path $build065 "dist/libs/$kind065.jar"
    Assert-Plain065 $jar065
    $key065=if($kind065 -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath $jar065 -Algorithm SHA256).Hash -ine $buildInfo065.$key065){throw 'TASK065: built JAR provenance differs.'}
}
$dist065=Join-Path $source065 'dist'
Assert-Plain065 $dist065
if(@(Get-ChildItem -LiteralPath $dist065 -Recurse -Force | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'TASK065: source data contains an alias.'}
$databasePath065=[IO.Path]::GetFullPath($DatabaseConfig)
if((Get-Item -LiteralPath $databasePath065).Length -gt 8192 -or (Read-Field065 $databasePath065 'URL') -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$schema065+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK065: own database config differs.'}
$database065=[IO.File]::ReadAllText($databasePath065)
if((Get-Item -LiteralPath $RegistrationFile).Length -gt 8192){throw 'TASK065: bounded private registration required.'}
$geodata065=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t" | Where-Object kind -eq 'GeoData')
if($geodata065.Count -ne 203 -or @($geodata065.relativePath | Select-Object -Unique).Count -ne 203 -or @($geodata065.source | Select-Object -Unique).Count -ne 203){throw 'TASK065: expected 203 unique inventoried regions.'}
foreach($geo065 in $geodata065){
    $physical065=[IO.Path]::GetFullPath($geo065.source)
    Assert-Plain065 $physical065
    $file065=Get-Item -LiteralPath $physical065 -Force
    if($file065.PSIsContainer -or $file065.Length -ne [long]$geo065.bytes -or (Get-FileHash -LiteralPath $physical065 -Algorithm SHA256).Hash -ine $geo065.sha256 -or $geo065.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file065.Name -cne (Split-Path $geo065.relativePath -Leaf)){throw 'TASK065: GeoData source identity changed.'}
    if($physical065 -inotmatch '\\World-Integrated-064-candidate\\game\\data\\geodata\\[0-9]{2}_[0-9]{2}\.l2j$'){throw 'TASK065: inventoried read-only064 GeoData source required.'}
}

New-Item -ItemType Directory -Path $output065 | Out-Null
Copy-Item -Path (Join-Path $dist065 '*') -Destination $output065 -Recurse
foreach($kind065 in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build065 "dist/libs/$kind065.jar") -Destination (Join-Path $output065 "libs/$kind065.jar") -Force}
foreach($folder065 in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs')){New-Item -ItemType Directory -Path (Join-Path $output065 $folder065) -Force | Out-Null}
foreach($private065 in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private065 $private065}
foreach($kind065 in @('game','login')){[IO.File]::WriteAllText((Join-Path $output065 "$kind065/config/Database.ini"),$database065,[Text.UTF8Encoding]::new($false))}
$registration065=Join-Path $output065 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration065)){[IO.File]::WriteAllBytes($registration065,[byte[]]@())}
Set-Private065 'game/config/hexid.txt'
[IO.File]::WriteAllBytes($registration065,[IO.File]::ReadAllBytes([IO.Path]::GetFullPath($RegistrationFile)))
foreach($geo065 in $geodata065){
    $destination065=Join-Path $output065 $geo065.relativePath
    Assert-Plain065 $destination065
    Copy-Item -LiteralPath $geo065.source -Destination $destination065
    if((Get-Item -LiteralPath $destination065).Length -ne [long]$geo065.bytes -or (Get-FileHash -LiteralPath $destination065 -Algorithm SHA256).Hash -ine $geo065.sha256){throw 'TASK065: physical GeoData copy verification failed.'}
}
$geoReceipt065=Join-Path $output065 'GEODATA065.tsv'
$geodata065 | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt065 -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config065 'game/config/Server.ini' 'GameserverPort' ([string]$ports065[1])
Set-Config065 'game/config/Server.ini' 'LoginPort' ([string]$ports065[2])
Set-Config065 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config065 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config065 'login/config/Server.ini' 'LoginserverPort' ([string]$ports065[0])
Set-Config065 'login/config/Server.ini' 'LoginPort' ([string]$ports065[2])
Set-Config065 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config065 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
$tools065=Join-Path $output065 'tools'
New-Item -ItemType Directory -Path $tools065 -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Runtime065.ps1') -Destination $tools065
Copy-Item -LiteralPath (Join-Path $module065 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools065
foreach($action065 in @('START','STATUS','STOP')){
    [IO.File]::WriteAllText((Join-Path $output065 "$action065.cmd"),"@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime065.ps1`" -Action $action065 -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n",[Text.Encoding]::ASCII)
}
$game065=(Get-FileHash -LiteralPath (Join-Path $output065 'libs/GameServer.jar') -Algorithm SHA256).Hash
$login065=(Get-FileHash -LiteralPath (Join-Path $output065 'libs/LoginServer.jar') -Algorithm SHA256).Hash
$helperHash065=(Get-FileHash -LiteralPath (Join-Path $tools065 'LocalPlay-Ownership.ps1') -Algorithm SHA256).Hash
$runtimeHash065=(Get-FileHash -LiteralPath (Join-Path $tools065 'Runtime065.ps1') -Algorithm SHA256).Hash
$info065=[ordered]@{format=1;owner='TASK065_LIVING_PRESENCE';sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema065;sourceArchiveSha256=$buildInfo065.sourceArchiveSha256;testTreeSha256=$buildInfo065.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $buildInfoPath065 -Algorithm SHA256).Hash;gameJarSha256=$game065;loginJarSha256=$login065;ownershipHelperSha256=$helperHash065;runtimeToolSha256=$runtimeHash065;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt065 -Algorithm SHA256).Hash;clientLoginPort=$ports065[0];gamePort=$ports065[1];controlPort=$ports065[2]}
[IO.File]::WriteAllText((Join-Path $output065 'BUILD_INFO065.json'),($info065 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $buildInfoPath065 -Destination (Join-Path $output065 'SOURCE_BUILD_INFO065.json')
$manifest065=[ordered]@{format=1;owner='TASK065_LIVING_PRESENCE';codeSha=$SourceCommit;databaseName=$schema065;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$game065;loginJarSha256=$login065;clientLoginPort=$ports065[0];gamePort=$ports065[1];controlPort=$ports065[2];observerCharacter='Task065View'}
[IO.File]::WriteAllText((Join-Path $output065 'local-play.json'),($manifest065 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$owner065="version=1`nowner=TASK065_LIVING_PRESENCE`nrole=$role065`nschema=$schema065`nruntimeRoot=$($output065.Replace('\','/'))`nsourceModuleRoot=$($module065.Replace('\','/'))`nclientLoginPort=$($ports065[0])`ngamePort=$($ports065[1])`ncontrolPort=$($ports065[2])`ngameJarSha256=$game065`nloginJarSha256=$login065`nownershipHelperSha256=$helperHash065`nruntimeToolSha256=$runtimeHash065`n"
[IO.File]::WriteAllText((Join-Path $output065 'ownership065.properties'),$owner065,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $output065 'runtime.local.ini'),"JavaHome=$JavaHome`nDatabaseName=$schema065`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=180`nStopTimeoutSeconds=180`n",[Text.UTF8Encoding]::new($false))
Set-Private065 'runtime.local.ini'
& (Join-Path $tools065 'Runtime065.ps1') -Action Validate -RuntimeRoot $output065
Write-Output "TASK065_ASSEMBLED kind=$Kind; regions=$($geodata065.Count); source=$SourceCommit; DB_WRITES=0; PROCESS_ACTIONS=0"
