[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Test')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module064=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$output064=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
if(Test-Path -LiteralPath $output064){throw 'Output exists; no overwrite.'}
if($Kind -ceq 'Test'){
    if($output064 -ine (Join-Path $module064 '.phantom-local/task064/runtime')){throw 'Exact task064 test root required.'}
    $schema064='l2jmobiush5_phantom_test_064';$role064='MUTABLE_FIXTURE';$ports064=@(2244,7944,9244)
}else{
    if((Split-Path $output064 -Leaf) -cne 'World-Integrated-064-candidate'){throw 'Exact task064 candidate name required.'}
    $schema064='l2jmobiush5_phantom_release_064a';$role064='RELEASE_CANDIDATE';$ports064=@(2144,7844,9144)
}
$source064=[IO.Path]::GetFullPath($CommittedModule)
$build064=[IO.Path]::GetFullPath($BuildRoot)
$database064=[IO.File]::ReadAllText([IO.Path]::GetFullPath($DatabaseConfig))
if($database064 -cnotmatch ('(?m)^URL[ \t]*=[ \t]*jdbc:mysql://127\.0\.0\.1:3308/'+$schema064+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?\r?$')){throw 'Own database config differs.'}
$geodata064=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t" | Where-Object kind -eq 'GeoData')
if($geodata064.Count -ne 203){throw 'Expected the inventoried203 regions.'}
foreach($geo064 in $geodata064){
    $file064=Get-Item -LiteralPath $geo064.source -Force
    if($file064.Attributes -band [IO.FileAttributes]::ReparsePoint -or $file064.Length -ne [long]$geo064.bytes -or (Get-FileHash -LiteralPath $file064.FullName).Hash -ine $geo064.sha256 -or $geo064.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$'){throw 'Geo source identity changed.'}
}
New-Item -ItemType Directory -Path $output064 | Out-Null
Copy-Item -Path (Join-Path $source064 'dist/*') -Destination $output064 -Recurse
foreach($kind064 in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build064 "dist/libs/$kind064.jar") -Destination (Join-Path $output064 "libs/$kind064.jar") -Force}
foreach($geo064 in $geodata064){
    $destination064=Join-Path $output064 $geo064.relativePath
    Copy-Item -LiteralPath $geo064.source -Destination $destination064
    if((Get-Item -LiteralPath $destination064).Length -ne [long]$geo064.bytes -or (Get-FileHash -LiteralPath $destination064).Hash -ine $geo064.sha256){throw 'Geo copy verification failed.'}
}
function Set064([string]$Relative,[string]$Key,[string]$Value){
    $path064=Join-Path $output064 $Relative;$text064=[IO.File]::ReadAllText($path064)
    $pattern064='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text064,$pattern064).Count -ne 1){throw "Need one $Key in $Relative"}
    $text064=[regex]::Replace($text064,$pattern064,{param($match064) $match064.Groups[1].Value+$Value})
    [IO.File]::WriteAllText($path064,$text064,[Text.UTF8Encoding]::new($false))
}
foreach($kind064 in @('game','login')){[IO.File]::WriteAllText((Join-Path $output064 "$kind064/config/Database.ini"),$database064,[Text.UTF8Encoding]::new($false))}
Copy-Item -LiteralPath $RegistrationFile -Destination (Join-Path $output064 'game/config/hexid.txt')
Set064 'game/config/Server.ini' 'GameserverPort' ([string]$ports064[1]);Set064 'game/config/Server.ini' 'LoginPort' ([string]$ports064[2]);Set064 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1';Set064 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set064 'login/config/Server.ini' 'LoginserverPort' ([string]$ports064[0]);Set064 'login/config/Server.ini' 'LoginPort' ([string]$ports064[2]);Set064 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1';Set064 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
$tools064=Join-Path $output064 'tools';New-Item -ItemType Directory -Path $tools064 -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Runtime064.ps1') -Destination $tools064
Copy-Item -LiteralPath (Join-Path $module064 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools064
foreach($action064 in @('START','STATUS','STOP')){
    [IO.File]::WriteAllText((Join-Path $output064 "$action064.cmd"),"@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime064.ps1`" -Action $action064 -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n",[Text.Encoding]::ASCII)
}
$game064=(Get-FileHash -LiteralPath (Join-Path $output064 'libs/GameServer.jar')).Hash
$login064=(Get-FileHash -LiteralPath (Join-Path $output064 'libs/LoginServer.jar')).Hash
$info064=[ordered]@{format=1;codeCommit=$SourceCommit;databaseName=$schema064;gameJarSha256=$game064;loginJarSha256=$login064;clientLoginPort=$ports064[0];gamePort=$ports064[1];controlPort=$ports064[2]}
[IO.File]::WriteAllText((Join-Path $output064 'BUILD_INFO.json'),($info064 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$manifest064=[ordered]@{format=1;codeSha=$SourceCommit;databaseName=$schema064;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$game064;loginJarSha256=$login064;clientLoginPort=$ports064[0];gamePort=$ports064[1];controlPort=$ports064[2];observerCharacter='Task064View'}
[IO.File]::WriteAllText((Join-Path $output064 'local-play.json'),($manifest064 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$owner064="version=1`nowner=TASK064_CAMPAIGN`nrole=$role064`nschema=$schema064`nruntimeRoot=$($output064.Replace('\','/'))`nsourceModuleRoot=$($module064.Replace('\','/'))`nclientLoginPort=$($ports064[0])`ngamePort=$($ports064[1])`ncontrolPort=$($ports064[2])`ngameJarSha256=$game064`nloginJarSha256=$login064`n"
[IO.File]::WriteAllText((Join-Path $output064 'ownership064.properties'),$owner064,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $output064 'runtime.local.ini'),"JavaHome=$JavaHome`nDatabaseName=$schema064`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=180`nStopTimeoutSeconds=180`n",[Text.UTF8Encoding]::new($false))
# Private configs and operator requests are limited to the current Windows user.
foreach($folder064 in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs')){New-Item -ItemType Directory -Path (Join-Path $output064 $folder064) -Force | Out-Null}
foreach($private064 in @('game/config/Database.ini','login/config/Database.ini','runtime.local.ini','game/config/hexid.txt','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control')){
    $path064=Join-Path $output064 $private064
    $sid064='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant064=if((Get-Item -LiteralPath $path064).PSIsContainer){$sid064+':(OI)(CI)F'}else{$sid064+':F'}
    & icacls.exe $path064 /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant064 | Out-Null
    if($LASTEXITCODE){throw 'TASK064 private DACL operation failed.'}
}
& (Join-Path $tools064 'Runtime064.ps1') -Action Validate -RuntimeRoot $output064
Write-Output "TASK064_ASSEMBLED kind=$Kind; regions=$($geodata064.Count); source=$SourceCommit; DB_WRITES=0; PROCESS_ACTIONS=0"
