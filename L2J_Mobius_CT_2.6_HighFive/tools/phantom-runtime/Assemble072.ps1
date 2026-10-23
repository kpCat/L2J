[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Release')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task072Common.ps1')
$module=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$binding=Get-Binding072 $module $Kind
$schema=$binding.schema
$ports=$binding.ports
foreach($path in @($module,$source,$build,$output,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain072 $path}
if($output -ine $binding.root -or !$source.StartsWith($binding.task+'\source-',[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $build -Parent) -ine $binding.task -or (Split-Path $build -Leaf) -cnotmatch '^build-final-[a-z0-9-]+$'){throw 'TASK072: exact owned source/build/output paths required.'}
if(Test-Path -LiteralPath $output){throw 'TASK072: output exists, no overwrite or implicit migration.'}
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in $ports}).Count){throw 'TASK072: selected ports occupied; preserve their owner.'}
Assert-Java072 $JavaHome
$kindName=$Kind.ToLowerInvariant()
if([IO.Path]::GetFullPath($DatabaseConfig) -ine (Join-Path $binding.task "private/Database.$kindName.ini") -or [IO.Path]::GetFullPath($RegistrationFile) -ine (Join-Path $binding.task "private/hexid-$kindName.txt")){throw 'TASK072: exact own private inputs required.'}
Assert-Jdbc072 (Read-Field072 $DatabaseConfig 'URL') $schema
if((Read-Field072 $DatabaseConfig 'MaximumDatabaseConnections') -cne '4' -or (Read-Field072 $RegistrationFile 'ServerID') -cne '1' -or (Read-Field072 $RegistrationFile 'HexID') -cnotmatch '^-?[0-9a-fA-F]{1,128}$'){throw 'TASK072: bounded own DB/registration inputs required.'}
$owner=Join-Path $build 'BUILD_OWNERSHIP072.properties'
if((Read-Field072 $owner 'version') -cne '1' -or (Read-Field072 $owner 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field072 $owner 'buildRoot')).TrimEnd('\') -ine $build){throw 'TASK072: exact build ownership required.'}
$receiptPath=Join-Path $build 'BUILD_INFO072.json'
$receipt=Read-Json072 $receiptPath
if($receipt.format -ne 1 -or $receipt.owner -cne $binding.owner -or $receipt.sourceState -cne 'COMMITTED' -or $receipt.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($receipt.sourceModuleRoot).TrimEnd('\') -ine $module -or [IO.Path]::GetFullPath($receipt.committedModuleRoot).TrimEnd('\') -ine $source -or [IO.Path]::GetFullPath($receipt.buildRoot).TrimEnd('\') -ine $build){throw 'TASK072: committed build receipt binding differs.'}
$archive=[IO.Path]::GetFullPath($receipt.sourceArchivePath)
Assert-Plain072 $archive
if((Split-Path $archive -Parent) -ine $binding.task -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $receipt.sourceArchiveSha256 -or (Get-TreeHash072 $source @('build.xml','java','test','dist','tools') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')) -ine $receipt.committedTreeSha256 -or (Get-TreeHash072 (Join-Path $build 'phantom-test') @('bin','resources')) -ine $receipt.testTreeSha256){throw 'TASK072: archive/source/test provenance differs.'}
foreach($role in @('GameServer','LoginServer')){
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Algorithm SHA256).Hash -ine $receipt.$key){throw 'TASK072: built JAR provenance differs.'}
}
if([IO.Path]::GetFullPath($GeoManifest) -ine (Join-Path $binding.task 'GEODATA_INPUTS072.tsv')){throw 'TASK072: exact prepared GeoData manifest required.'}
$regions=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t")
if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203 -or @($regions.source | Select-Object -Unique).Count -ne 203){throw 'TASK072: expected203 unique regions.'}
$protectedGeo=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Personal-Adventure-067-candidate/game/data/geodata'
foreach($region in $regions){
    Assert-Plain072 $region.source
    $file=Get-Item -LiteralPath $region.source -Force
    if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file.PSIsContainer -or (Split-Path $file.FullName -Parent) -ine $protectedGeo -or $file.Name -cne (Split-Path $region.relativePath -Leaf) -or $file.Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK072: protected physical GeoData differs.'}
}

New-Item -ItemType Directory -Path $output | Out-Null
Write-Text072 (Join-Path $output 'assembly-ownership072.properties') "version=1`nowner=$($binding.owner)`nruntimeRoot=$($output.Replace('\','/'))`nsourceCommit=$SourceCommit`n"
Copy-Item -Path (Join-Path $source 'dist/*') -Destination $output -Recurse
foreach($role in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Destination (Join-Path $output "libs/$role.jar") -Force}
foreach($folder in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs','tools/classes')){New-Item -ItemType Directory -Path (Join-Path $output $folder) -Force | Out-Null}
foreach($private in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private072 (Join-Path $output $private)}
foreach($role in @('game','login')){Write-Text072 (Join-Path $output "$role/config/Database.ini") ([IO.File]::ReadAllText($DatabaseConfig))}
$registration=Join-Path $output 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration)){Write-Text072 $registration ''}
Set-Private072 $registration
[IO.File]::WriteAllBytes($registration,[IO.File]::ReadAllBytes($RegistrationFile))
foreach($region in $regions){
    $destination=Join-Path $output $region.relativePath
    Assert-Plain072 $destination
    Copy-Item -LiteralPath $region.source -Destination $destination
    if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK072: physical GeoData copy hash differs.'}
}
$geoReceipt=Join-Path $output 'GEODATA072.tsv'
$regions | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config072 $output 'game/config/Server.ini' 'GameserverPort' ([string]$ports[1])
Set-Config072 $output 'game/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config072 $output 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config072 $output 'game/config/Server.ini' 'LoginHost' '127.0.0.1'
Set-Config072 $output 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config072 $output 'login/config/Server.ini' 'LoginserverPort' ([string]$ports[0])
Set-Config072 $output 'login/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config072 $output 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config072 $output 'login/config/Server.ini' 'LoginHostname' '127.0.0.1'
Set-Config072 $output 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
Set-Config072 $output 'game/config/Interface.ini' 'EnableGUI' 'False'
Set-Config072 $output 'login/config/Interface.ini' 'EnableGUI' 'False'
# Own candidate profile; stock source defaults remain OFF. Personal QoL/blue opts stay at shipped defaults.
Set-Config072 $output 'game/config/Custom/PhantomPlayers.ini' 'EnablePhantomSystem' 'True'
Set-Config072 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationTarget' '16'
Set-Config072 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationActiveTarget' '16'
Set-Config072 $output 'game/config/Custom/PhantomPlayers.ini' 'MaxMaterializedPhantoms' '16'
Set-Config072 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomStarterShotLifetimeLimit' '5000'
Set-Config072 $output 'game/config/Custom/PhantomMerchants.ini' 'EnableOfflinePhantomMerchants' 'True'
$communityBoard072='True'
Set-Config072 $output 'game/config/Custom/CommunityBoard.ini' 'CustomCommunityBoard' $communityBoard072
Set-Config072 $output 'game/config/Custom/CommunityBoard.ini' 'CommunityPremiumSystem' 'True'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'EnablePremiumSystem' 'True'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateDropChance' '2'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateDropAmount' '1'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateSpoilChance' '2'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateSpoilAmount' '1'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateDropChanceByItemId' '57,1'
Set-Config072 $output 'game/config/Custom/PremiumSystem.ini' 'PremiumRateDropAmountByItemId' '57,2'
# Preserve the committed stock Java25 profile. Full203 geodata alone exceeded the
# attempted1536MB heap before population startup; do not promise that memory limit.
$tools=Join-Path $output 'tools'
foreach($tool in @('Runtime072.ps1','Task072Common.ps1','Task072DatabaseProbe.java')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-runtime/$tool") -Destination $tools}
Copy-Item -LiteralPath (Join-Path $source 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -d (Join-Path $tools 'classes') (Join-Path $tools 'Task072DatabaseProbe.java')
if($LASTEXITCODE){throw 'TASK072: standalone read-only database helper compile failed.'}
$toolRows=@(foreach($relative in @('Runtime072.ps1','Task072Common.ps1','LocalPlay-Ownership.ps1','Task072DatabaseProbe.java','classes/Task072DatabaseProbe.class')){[pscustomobject]@{relativePath=('tools/'+$relative);sha256=(Get-FileHash -LiteralPath (Join-Path $tools $relative) -Algorithm SHA256).Hash}})
$toolRows | Export-Csv -LiteralPath (Join-Path $output 'TOOLS072.tsv') -Delimiter "`t" -NoTypeInformation -Encoding UTF8
foreach($action in @('START','STATUS','STOP')){Write-Text072 (Join-Path $output "$action.cmd") "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime072.ps1`" -Action $action -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n"}
$info=[ordered]@{format=1;owner=$binding.owner;sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema;sourceArchiveSha256=$receipt.sourceArchiveSha256;committedTreeSha256=$receipt.committedTreeSha256;testTreeSha256=$receipt.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash;gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;toolManifestSha256=(Get-FileHash -LiteralPath (Join-Path $output 'TOOLS072.tsv') -Algorithm SHA256).Hash;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt -Algorithm SHA256).Hash;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]}
Write-Text072 (Join-Path $output 'BUILD_INFO072.json') ($info | ConvertTo-Json)
Write-Text072 (Join-Path $output 'OWNERSHIP072.json') ([ordered]@{owner=$binding.owner;gameRoot=(Join-Path $output 'game');allowPriceApply=$false} | ConvertTo-Json)
Copy-Item -LiteralPath $receiptPath -Destination (Join-Path $output 'SOURCE_BUILD_INFO072.json')
Write-Text072 (Join-Path $output 'local-play.json') ([ordered]@{format=1;owner=$binding.owner;codeSha=$SourceCommit;databaseName=$schema;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]} | ConvertTo-Json)
$installation072=Read-Field072 (Join-Path $binding.task 'ownership072.properties') 'installationId'
if([guid]::Parse($installation072).ToString() -cne $installation072){throw 'TASK072: canonical installation UUID required.'}
Write-Text072 (Join-Path $output 'ownership072.properties') "version=1`nowner=$($binding.owner)`nrole=$($binding.role)`nschema=$schema`ninstallationId=$installation072`nruntimeRoot=$($output.Replace('\','/'))`nsourceModuleRoot=$($module.Replace('\','/'))`nclientLoginPort=$($ports[0])`ngamePort=$($ports[1])`ncontrolPort=$($ports[2])`ngameJarSha256=$($receipt.gameJarSha256)`nloginJarSha256=$($receipt.loginJarSha256)`n"
Write-Text072 (Join-Path $output 'runtime.local.ini') "JavaHome=$JavaHome`nDatabaseName=$schema`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=240`nStopTimeoutSeconds=240`n"
Set-Private072 (Join-Path $output 'runtime.local.ini')
$guide072=@"
# TASK072 — самостоятельная поставка

Исходники: $SourceCommit. Тип: $Kind. БД: $schema на127.0.0.1:3308. Порты: $($ports -join ',').

START.cmd, STATUS.cmd и STOP.cmd работают из этой папки без checkout. JDK25 указан в runtime.local.ini. GameServer использует Xms2g/Xmx4g; установлены203 физические GeoData. При занятых портах START отказывает и сохраняет чужие процессы. STOP выполняет штатное завершение, проверяет PID/owner и отсутствие соединений собственной БД.

Обычные rates и Adena — x1. CustomCommunityBoard, PremiumSystem и покупка премиума включены только здесь. Premium XP/SP2; drop/spoil chance2 amount1; Adena chance1 amount2. Цена премиума1млн Adena за реальный день приx1. Цены072 явно закреплены; START их не пересчитывает. Разработка нового механизма рейтования отложена владельцем.

Phantom population/active/cap16, offline merchants200; конечный starter shots5000 — стартовая субсидия. Новые обычные аккаунты создаются штатным клиентом по решению владельца. Старые персонажи, TestAdmin/kpCat и клиент сюда не переносились.

Personal QoL остаётся OFF, AllowedAccounts пуст. Для личного доступа владелец указывает свой обычный access0 account в соответствующих PersonalCharacterQoL/PersonalPremiumQoL конфигурациях и включает нужные дочерние функции. Фантомы и GM не получают личные passes.

BUILD_INFO072.json, SOURCE_BUILD_INFO072.json, TOOLS072.tsv и GEODATA072.tsv фиксируют committed source/JAR/tools/Geo. Фактическая готовность и незакрытые072 gates указаны в ACCEPTANCE072.tsv и итоговом отчёте. После проверки обе072 JVM должны быть STOPPED; визуальная приёмка владельцем остаётся OWNER_VISUAL_PENDING.
"@
Write-Text072 (Join-Path $output 'START_HERE_072_RU.md') $guide072 $true
& (Join-Path $tools 'Runtime072.ps1') -Action Validate -RuntimeRoot $output
Write-Output "TASK072_ASSEMBLED kind=$Kind; source=$SourceCommit; regions203; DB_WRITES=0; PROCESS_ACTIONS=0"
