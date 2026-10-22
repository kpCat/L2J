[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Release')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task069Common.ps1')
$module=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$binding=Get-Binding069 $module $Kind
$schema=$binding.schema
$ports=$binding.ports
foreach($path in @($module,$source,$build,$output,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain069 $path}
if($output -ine $binding.root -or !$source.StartsWith($binding.task+'\source-',[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $build -Parent) -ine $binding.task -or (Split-Path $build -Leaf) -cnotmatch '^build-final-[a-z0-9-]+$'){throw 'TASK069: exact owned source/build/output paths required.'}
if(Test-Path -LiteralPath $output){throw 'TASK069: output exists, no overwrite or implicit migration.'}
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in $ports}).Count){throw 'TASK069: selected ports occupied; preserve their owner.'}
Assert-Java069 $JavaHome
$kindName=$Kind.ToLowerInvariant()
if([IO.Path]::GetFullPath($DatabaseConfig) -ine (Join-Path $binding.task "private/Database.$kindName.ini") -or [IO.Path]::GetFullPath($RegistrationFile) -ine (Join-Path $binding.task "private/hexid-$kindName.txt")){throw 'TASK069: exact own private inputs required.'}
Assert-Jdbc069 (Read-Field069 $DatabaseConfig 'URL') $schema
if((Read-Field069 $DatabaseConfig 'MaximumDatabaseConnections') -cne '4' -or (Read-Field069 $RegistrationFile 'ServerID') -cne '1' -or (Read-Field069 $RegistrationFile 'HexID') -cnotmatch '^-?[0-9a-fA-F]{1,128}$'){throw 'TASK069: bounded own DB/registration inputs required.'}
$owner=Join-Path $build 'BUILD_OWNERSHIP069.properties'
if((Read-Field069 $owner 'version') -cne '1' -or (Read-Field069 $owner 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field069 $owner 'buildRoot')).TrimEnd('\') -ine $build){throw 'TASK069: exact build ownership required.'}
$receiptPath=Join-Path $build 'BUILD_INFO069.json'
$receipt=Read-Json069 $receiptPath
if($receipt.format -ne 1 -or $receipt.owner -cne $binding.owner -or $receipt.sourceState -cne 'COMMITTED' -or $receipt.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($receipt.sourceModuleRoot).TrimEnd('\') -ine $module -or [IO.Path]::GetFullPath($receipt.committedModuleRoot).TrimEnd('\') -ine $source -or [IO.Path]::GetFullPath($receipt.buildRoot).TrimEnd('\') -ine $build){throw 'TASK069: committed build receipt binding differs.'}
$archive=[IO.Path]::GetFullPath($receipt.sourceArchivePath)
Assert-Plain069 $archive
if((Split-Path $archive -Parent) -ine $binding.task -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $receipt.sourceArchiveSha256 -or (Get-TreeHash069 $source @('build.xml','java','test','dist','tools') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')) -ine $receipt.committedTreeSha256 -or (Get-TreeHash069 (Join-Path $build 'phantom-test') @('bin','resources')) -ine $receipt.testTreeSha256){throw 'TASK069: archive/source/test provenance differs.'}
foreach($role in @('GameServer','LoginServer')){
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Algorithm SHA256).Hash -ine $receipt.$key){throw 'TASK069: built JAR provenance differs.'}
}
if([IO.Path]::GetFullPath($GeoManifest) -ine (Join-Path $binding.task 'GEODATA_INPUTS069.tsv')){throw 'TASK069: exact prepared GeoData manifest required.'}
$regions=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t")
if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203 -or @($regions.source | Select-Object -Unique).Count -ne 203){throw 'TASK069: expected203 unique regions.'}
$protectedGeo=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Personal-Adventure-067-candidate/game/data/geodata'
foreach($region in $regions){
    Assert-Plain069 $region.source
    $file=Get-Item -LiteralPath $region.source -Force
    if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file.PSIsContainer -or (Split-Path $file.FullName -Parent) -ine $protectedGeo -or $file.Name -cne (Split-Path $region.relativePath -Leaf) -or $file.Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK069: protected physical GeoData differs.'}
}

New-Item -ItemType Directory -Path $output | Out-Null
Write-Text069 (Join-Path $output 'assembly-ownership069.properties') "version=1`nowner=$($binding.owner)`nruntimeRoot=$($output.Replace('\','/'))`nsourceCommit=$SourceCommit`n"
Copy-Item -Path (Join-Path $source 'dist/*') -Destination $output -Recurse
foreach($role in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Destination (Join-Path $output "libs/$role.jar") -Force}
foreach($folder in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs','tools/classes')){New-Item -ItemType Directory -Path (Join-Path $output $folder) -Force | Out-Null}
foreach($private in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private069 (Join-Path $output $private)}
foreach($role in @('game','login')){Write-Text069 (Join-Path $output "$role/config/Database.ini") ([IO.File]::ReadAllText($DatabaseConfig))}
$registration=Join-Path $output 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration)){Write-Text069 $registration ''}
Set-Private069 $registration
[IO.File]::WriteAllBytes($registration,[IO.File]::ReadAllBytes($RegistrationFile))
foreach($region in $regions){
    $destination=Join-Path $output $region.relativePath
    Assert-Plain069 $destination
    Copy-Item -LiteralPath $region.source -Destination $destination
    if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK069: physical GeoData copy hash differs.'}
}
$geoReceipt=Join-Path $output 'GEODATA069.tsv'
$regions | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config069 $output 'game/config/Server.ini' 'GameserverPort' ([string]$ports[1])
Set-Config069 $output 'game/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config069 $output 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config069 $output 'game/config/Server.ini' 'LoginHost' '127.0.0.1'
Set-Config069 $output 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config069 $output 'login/config/Server.ini' 'LoginserverPort' ([string]$ports[0])
Set-Config069 $output 'login/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config069 $output 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config069 $output 'login/config/Server.ini' 'LoginHostname' '127.0.0.1'
Set-Config069 $output 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
Set-Config069 $output 'game/config/Interface.ini' 'EnableGUI' 'False'
Set-Config069 $output 'login/config/Interface.ini' 'EnableGUI' 'False'
# Own candidate profile; stock source defaults remain OFF. Personal QoL/blue opts stay at shipped defaults.
Set-Config069 $output 'game/config/Custom/PhantomPlayers.ini' 'EnablePhantomSystem' 'True'
Set-Config069 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationTarget' '16'
Set-Config069 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationActiveTarget' '16'
Set-Config069 $output 'game/config/Custom/PhantomPlayers.ini' 'MaxMaterializedPhantoms' '16'
Set-Config069 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomStarterShotLifetimeLimit' '5000'
Set-Config069 $output 'game/config/Custom/PhantomMerchants.ini' 'EnableOfflinePhantomMerchants' 'True'
$communityBoard069='True'
Set-Config069 $output 'game/config/Custom/CommunityBoard.ini' 'CustomCommunityBoard' $communityBoard069
Write-Text069 (Join-Path $output 'game/java.cfg') '-server -Dfile.encoding=UTF-8 -Djava.util.logging.manager=org.l2jmobius.log.ServerLogManager -Dorg.slf4j.simpleLogger.log.com.zaxxer.hikari=warn -XX:+UseZGC -Xmx1536m -Xms512m'
$tools=Join-Path $output 'tools'
foreach($tool in @('Runtime069.ps1','Task069Common.ps1','Task069DatabaseProbe.java')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-runtime/$tool") -Destination $tools}
Copy-Item -LiteralPath (Join-Path $source 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools
New-Item -ItemType Directory -Path (Join-Path $tools 'phantom-economy') | Out-Null
foreach($tool in @('Price-PhantomEconomy.ps1','README_RU.md')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-economy/$tool") -Destination (Join-Path $tools 'phantom-economy')}
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -d (Join-Path $tools 'classes') (Join-Path $tools 'Task069DatabaseProbe.java')
if($LASTEXITCODE){throw 'TASK069: standalone read-only database helper compile failed.'}
$toolRows=@(foreach($relative in @('Runtime069.ps1','Task069Common.ps1','LocalPlay-Ownership.ps1','Task069DatabaseProbe.java','classes/Task069DatabaseProbe.class','phantom-economy/Price-PhantomEconomy.ps1','phantom-economy/README_RU.md')){[pscustomobject]@{relativePath=('tools/'+$relative);sha256=(Get-FileHash -LiteralPath (Join-Path $tools $relative) -Algorithm SHA256).Hash}})
$toolRows | Export-Csv -LiteralPath (Join-Path $output 'TOOLS069.tsv') -Delimiter "`t" -NoTypeInformation -Encoding UTF8
foreach($action in @('START','STATUS','STOP')){Write-Text069 (Join-Path $output "$action.cmd") "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime069.ps1`" -Action $action -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n"}
$info=[ordered]@{format=1;owner=$binding.owner;sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema;sourceArchiveSha256=$receipt.sourceArchiveSha256;committedTreeSha256=$receipt.committedTreeSha256;testTreeSha256=$receipt.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash;gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;toolManifestSha256=(Get-FileHash -LiteralPath (Join-Path $output 'TOOLS069.tsv') -Algorithm SHA256).Hash;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt -Algorithm SHA256).Hash;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]}
Write-Text069 (Join-Path $output 'BUILD_INFO069.json') ($info | ConvertTo-Json)
Write-Text069 (Join-Path $output 'OWNERSHIP069.json') ([ordered]@{owner=$binding.owner;gameRoot=(Join-Path $output 'game');allowPriceApply=$true} | ConvertTo-Json)
Copy-Item -LiteralPath $receiptPath -Destination (Join-Path $output 'SOURCE_BUILD_INFO069.json')
Write-Text069 (Join-Path $output 'local-play.json') ([ordered]@{format=1;owner=$binding.owner;codeSha=$SourceCommit;databaseName=$schema;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]} | ConvertTo-Json)
Write-Text069 (Join-Path $output 'ownership069.properties') "version=1`nowner=$($binding.owner)`nrole=$($binding.role)`nschema=$schema`nruntimeRoot=$($output.Replace('\','/'))`nsourceModuleRoot=$($module.Replace('\','/'))`nclientLoginPort=$($ports[0])`ngamePort=$($ports[1])`ncontrolPort=$($ports[2])`ngameJarSha256=$($receipt.gameJarSha256)`nloginJarSha256=$($receipt.loginJarSha256)`n"
Write-Text069 (Join-Path $output 'runtime.local.ini') "JavaHome=$JavaHome`nDatabaseName=$schema`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=240`nStopTimeoutSeconds=240`n"
Set-Private069 (Join-Path $output 'runtime.local.ini')
$guide069=@"
# TASK069 — самостоятельная поставка

Исходники: $SourceCommit. Тип: $Kind. БД: $schema на127.0.0.1:3308. Порты: $($ports -join ',').

START.cmd, STATUS.cmd и STOP.cmd работают из этой папки без checkout. Требуется установленный JDK25, указанный в runtime.local.ini. STOP закрывает приём новых сделок и manufacture, дожидается принятого native крафта, выполняет штатный shutdown и проверяет отсутствие соединений с собственной БД. Force fallback отсутствует.

| Файл в game/config/Custom | Ключи собственного начального профиля |
|---|---|
| PhantomPlayers.ini | EnablePhantomSystem=True; population/active/cap16; конечный пожизненный фонд starter shots5000 |
| PhantomMerchants.ini | EnableOfflinePhantomMerchants=True; target200; hard cap250; lifetime identities300; batch5; rotation30s |
| PhantomMerchants.ini | finite BOOTSTRAP capital100000/identity, global30000000; stock value500000000, per-item10000; без refill |
| PhantomMarket.ini | единые bid/ask/recipe/rate цены; manufacture minimum100, fee200bps BOM |
| CommunityBoard.ini | CustomCommunityBoard=True; Mana728 по1000A; Wind Walk2; постоянный обмен камней3/5/10; готовый шмот недоступен |
| ChampionMonsters.ini | red/blue switches сохранены OFF; BlueChampionEnable включает отдельную categorical probability |
| PersonalCharacterQoL.ini | personal master/allowlist/solo Rift сохранены OFF/пусто; глобальный minimum Party не изменён |

Все эти конфиги читаются при запуске; после изменения требуется штатный STOP/START. У существующей population нельзя произвольно менять PhantomPopulationTimeZone: он входит в сохранённый initialization contract. TEST-профиль/пассивный origin и окончательные результаты A–G фиксируются отдельно перед измерением.

Фонд5000 — явно обозначенная конечная стартовая субсидия, не заработанные расходники; merchants её не получают. Обычная последующая закупка требует настоящей Adena. RateXp/Sp/party/quest/death/spoil/raid и item57 — x1; независимые champion-настройки сохранены.

Новые merchants не имеют battle/decision/background jobs. SELL использует существующий inventory, BUY — настоящую Adena, MANUFACTURE — выученные native рецепты/skill/MP и материалы заказчика. MerchantPreparedOfflineCharacterIds принимает только явно подготовленные disabled-account native manufacturers без другого Phantom owner; новые роли автоматически не получают чужой профессии или рецептов.

Новый аккаунт и обычного access0 персонажа создайте штатным клиентом после START. Личные аккаунты, TestAdmin и kpCat сюда не переносились. Login2106/Game7777 не требуют изменения клиента; при занятых портах START откажет, чужие процессы не остановит. Client QA остаётся OWNER_PLAY_PENDING.

Личный QoL по умолчанию OFF, AllowedAccounts пуст: выберите свой account в game/config/Custom/PersonalCharacterQoL.ini, включите master и только нужные дочерние флаги в PersonalProgressionQoL.ini. PersonalPremiumQoL.ini отдельно управляет магазином91001 и нерасходуемыми pass5/10/20/40. Ненулевой access, GM и фантомы личные функции не получают. Client tooltip pass-предметов остаётся event cake ingredient (CLIENT_TOOLTIP_LIMITATION). PremiumSystem и покупка премиума OFF; x2 не включён. Эпики, Mammon, Codex/Epaulette цены — PENDING_PRODUCT_PRICES, бесконечных продаж нет.

Inventory/equipment/skills/class/quests/variables сохраняются штатным native save и sameDB restart. Перед остановкой используйте только STOP.cmd и дождитесь STOCK_STOP_CONFIRMED. Не удаляйте и не импортируйте повторно БД. Поставка не обещает отсутствие всех ошибок; фактическая матрица069 отдельно отличает PASS/PARTIAL и исторические ограничения.

BUILD_INFO069.json, SOURCE_BUILD_INFO069.json, TOOLS069.tsv и GEODATA069.tsv фиксируют source/JAR/tool/Geo provenance. Пароли и hexid находятся в закрытых конфигурациях; старые поставки не изменены. Клиентская визуальная QA отложена владельцем; native packet evidence её не подменяет.
"@
Write-Text069 (Join-Path $output 'START_HERE_069_RU.md') $guide069 $true
& (Join-Path $tools 'Runtime069.ps1') -Action Validate -RuntimeRoot $output
Write-Output "TASK069_ASSEMBLED kind=$Kind; source=$SourceCommit; regions203; DB_WRITES=0; PROCESS_ACTIONS=0"
