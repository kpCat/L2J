[CmdletBinding()]
param([Parameter(Mandatory)][string]$ModuleRoot,[Parameter(Mandatory)][string]$CommittedModule,
      [Parameter(Mandatory)][string]$BuildRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [Parameter(Mandatory)][string]$GeoManifest,[Parameter(Mandatory)][string]$DatabaseConfig,
      [Parameter(Mandatory)][string]$RegistrationFile,[Parameter(Mandatory)][string]$JavaHome,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [ValidateSet('Test','Release')][string]$Kind='Release')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task068Common.ps1')
$module=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
$source=[IO.Path]::GetFullPath($CommittedModule).TrimEnd('\')
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$output=[IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$binding=Get-Binding068 $module $Kind
$schema=$binding.schema
$ports=$binding.ports
foreach($path in @($module,$source,$build,$output,$GeoManifest,$DatabaseConfig,$RegistrationFile,$JavaHome)){Assert-Plain068 $path}
if($output -ine $binding.root -or !$source.StartsWith($binding.task+'\source-',[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $build -Parent) -ine $binding.task -or (Split-Path $build -Leaf) -cnotmatch '^build-final-[a-z0-9-]+$'){throw 'TASK068: exact owned source/build/output paths required.'}
if(Test-Path -LiteralPath $output){throw 'TASK068: output exists, no overwrite or implicit migration.'}
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -in $ports}).Count){throw 'TASK068: selected ports occupied; preserve their owner.'}
Assert-Java068 $JavaHome
$kindName=$Kind.ToLowerInvariant()
if([IO.Path]::GetFullPath($DatabaseConfig) -ine (Join-Path $binding.task "private/Database.$kindName.ini") -or [IO.Path]::GetFullPath($RegistrationFile) -ine (Join-Path $binding.task "private/hexid-$kindName.txt")){throw 'TASK068: exact own private inputs required.'}
Assert-Jdbc068 (Read-Field068 $DatabaseConfig 'URL') $schema
if((Read-Field068 $DatabaseConfig 'MaximumDatabaseConnections') -cne '4' -or (Read-Field068 $RegistrationFile 'ServerID') -cne '1' -or (Read-Field068 $RegistrationFile 'HexID') -cnotmatch '^-?[0-9a-fA-F]{1,128}$'){throw 'TASK068: bounded own DB/registration inputs required.'}
$owner=Join-Path $build 'BUILD_OWNERSHIP068.properties'
if((Read-Field068 $owner 'version') -cne '1' -or (Read-Field068 $owner 'owner') -cne $binding.owner -or [IO.Path]::GetFullPath((Read-Field068 $owner 'buildRoot')).TrimEnd('\') -ine $build){throw 'TASK068: exact build ownership required.'}
$receiptPath=Join-Path $build 'BUILD_INFO068.json'
$receipt=Read-Json068 $receiptPath
if($receipt.format -ne 1 -or $receipt.owner -cne $binding.owner -or $receipt.sourceState -cne 'COMMITTED' -or $receipt.codeCommit -cne $SourceCommit -or [IO.Path]::GetFullPath($receipt.sourceModuleRoot).TrimEnd('\') -ine $module -or [IO.Path]::GetFullPath($receipt.committedModuleRoot).TrimEnd('\') -ine $source -or [IO.Path]::GetFullPath($receipt.buildRoot).TrimEnd('\') -ine $build){throw 'TASK068: committed build receipt binding differs.'}
$archive=[IO.Path]::GetFullPath($receipt.sourceArchivePath)
Assert-Plain068 $archive
if((Split-Path $archive -Parent) -ine $binding.task -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $receipt.sourceArchiveSha256 -or (Get-TreeHash068 $source @('build.xml','java','test','dist','tools') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')) -ine $receipt.committedTreeSha256 -or (Get-TreeHash068 (Join-Path $build 'phantom-test') @('bin','resources')) -ine $receipt.testTreeSha256){throw 'TASK068: archive/source/test provenance differs.'}
foreach($role in @('GameServer','LoginServer')){
    $key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'}
    if((Get-FileHash -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Algorithm SHA256).Hash -ine $receipt.$key){throw 'TASK068: built JAR provenance differs.'}
}
if([IO.Path]::GetFullPath($GeoManifest) -ine (Join-Path $binding.task 'GEODATA_INPUTS068.tsv')){throw 'TASK068: exact prepared GeoData manifest required.'}
$regions=@(Import-Csv -LiteralPath $GeoManifest -Delimiter "`t")
if($regions.Count -ne 203 -or @($regions.relativePath | Select-Object -Unique).Count -ne 203 -or @($regions.source | Select-Object -Unique).Count -ne 203){throw 'TASK068: expected203 unique regions.'}
$protectedGeo=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Personal-Adventure-067-candidate/game/data/geodata'
foreach($region in $regions){
    Assert-Plain068 $region.source
    $file=Get-Item -LiteralPath $region.source -Force
    if($region.kind -cne 'GeoData' -or $region.relativePath -cnotmatch '^game/data/geodata/[0-9]{2}_[0-9]{2}\.l2j$' -or $file.PSIsContainer -or (Split-Path $file.FullName -Parent) -ine $protectedGeo -or $file.Name -cne (Split-Path $region.relativePath -Leaf) -or $file.Length -ne [long]$region.bytes -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK068: protected physical GeoData differs.'}
}

New-Item -ItemType Directory -Path $output | Out-Null
Write-Text068 (Join-Path $output 'assembly-ownership068.properties') "version=1`nowner=$($binding.owner)`nruntimeRoot=$($output.Replace('\','/'))`nsourceCommit=$SourceCommit`n"
Copy-Item -Path (Join-Path $source 'dist/*') -Destination $output -Recurse
foreach($role in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Destination (Join-Path $output "libs/$role.jar") -Force}
foreach($folder in @('private','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','local-play/control','local-play/pids','local-play/logs','tools/classes')){New-Item -ItemType Directory -Path (Join-Path $output $folder) -Force | Out-Null}
foreach($private in @('game/config/Database.ini','login/config/Database.ini','game/config/Custom/LocalPlayPilot.ini','private','playtest-pilot','local-play/control','local-play/pids','local-play/logs')){Set-Private068 (Join-Path $output $private)}
foreach($role in @('game','login')){Write-Text068 (Join-Path $output "$role/config/Database.ini") ([IO.File]::ReadAllText($DatabaseConfig))}
$registration=Join-Path $output 'game/config/hexid.txt'
if(!(Test-Path -LiteralPath $registration)){Write-Text068 $registration ''}
Set-Private068 $registration
[IO.File]::WriteAllBytes($registration,[IO.File]::ReadAllBytes($RegistrationFile))
foreach($region in $regions){
    $destination=Join-Path $output $region.relativePath
    Assert-Plain068 $destination
    Copy-Item -LiteralPath $region.source -Destination $destination
    if((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ine $region.sha256){throw 'TASK068: physical GeoData copy hash differs.'}
}
$geoReceipt=Join-Path $output 'GEODATA068.tsv'
$regions | Select-Object kind,relativePath,bytes,sha256,source | Export-Csv -LiteralPath $geoReceipt -Delimiter "`t" -NoTypeInformation -Encoding UTF8
Set-Config068 $output 'game/config/Server.ini' 'GameserverPort' ([string]$ports[1])
Set-Config068 $output 'game/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config068 $output 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Set-Config068 $output 'game/config/Server.ini' 'LoginHost' '127.0.0.1'
Set-Config068 $output 'game/config/Server.ini' 'AcceptAlternateID' 'False'
Set-Config068 $output 'login/config/Server.ini' 'LoginserverPort' ([string]$ports[0])
Set-Config068 $output 'login/config/Server.ini' 'LoginPort' ([string]$ports[2])
Set-Config068 $output 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
Set-Config068 $output 'login/config/Server.ini' 'LoginHostname' '127.0.0.1'
Set-Config068 $output 'login/config/Server.ini' 'AcceptNewGameServer' 'False'
# Own candidate profile; stock source defaults remain OFF. Personal QoL/blue opts stay at shipped defaults.
Set-Config068 $output 'game/config/Custom/PhantomPlayers.ini' 'EnablePhantomSystem' 'True'
Set-Config068 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationTarget' '8'
Set-Config068 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomPopulationActiveTarget' '8'
Set-Config068 $output 'game/config/Custom/PhantomPlayers.ini' 'MaxMaterializedPhantoms' '8'
Set-Config068 $output 'game/config/Custom/PhantomPlayers.ini' 'PhantomStarterShotLifetimeLimit' '0'
Set-Config068 $output 'game/config/Custom/PhantomMerchants.ini' 'EnableOfflinePhantomMerchants' 'True'
Set-Config068 $output 'game/config/Custom/CommunityBoard.ini' 'CustomCommunityBoard' 'True'
$tools=Join-Path $output 'tools'
foreach($tool in @('Runtime068.ps1','Task068Common.ps1','Task068DatabaseProbe.java')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-runtime/$tool") -Destination $tools}
Copy-Item -LiteralPath (Join-Path $source 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools
New-Item -ItemType Directory -Path (Join-Path $tools 'phantom-economy') | Out-Null
foreach($tool in @('Price-PhantomEconomy.ps1','README_RU.md')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-economy/$tool") -Destination (Join-Path $tools 'phantom-economy')}
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -d (Join-Path $tools 'classes') (Join-Path $tools 'Task068DatabaseProbe.java')
if($LASTEXITCODE){throw 'TASK068: standalone read-only database helper compile failed.'}
$toolRows=@(foreach($relative in @('Runtime068.ps1','Task068Common.ps1','LocalPlay-Ownership.ps1','Task068DatabaseProbe.java','classes/Task068DatabaseProbe.class','phantom-economy/Price-PhantomEconomy.ps1','phantom-economy/README_RU.md')){[pscustomobject]@{relativePath=('tools/'+$relative);sha256=(Get-FileHash -LiteralPath (Join-Path $tools $relative) -Algorithm SHA256).Hash}})
$toolRows | Export-Csv -LiteralPath (Join-Path $output 'TOOLS068.tsv') -Delimiter "`t" -NoTypeInformation -Encoding UTF8
foreach($action in @('START','STATUS','STOP')){Write-Text068 (Join-Path $output "$action.cmd") "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime068.ps1`" -Action $action -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n"}
$info=[ordered]@{format=1;owner=$binding.owner;sourceState='COMMITTED';codeCommit=$SourceCommit;databaseName=$schema;sourceArchiveSha256=$receipt.sourceArchiveSha256;committedTreeSha256=$receipt.committedTreeSha256;testTreeSha256=$receipt.testTreeSha256;buildReceiptSha256=(Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash;gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;toolManifestSha256=(Get-FileHash -LiteralPath (Join-Path $output 'TOOLS068.tsv') -Algorithm SHA256).Hash;geoManifestSha256=(Get-FileHash -LiteralPath $geoReceipt -Algorithm SHA256).Hash;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]}
Write-Text068 (Join-Path $output 'BUILD_INFO068.json') ($info | ConvertTo-Json)
Write-Text068 (Join-Path $output 'OWNERSHIP068.json') ([ordered]@{owner=$binding.owner;gameRoot=(Join-Path $output 'game');allowPriceApply=$true} | ConvertTo-Json)
Copy-Item -LiteralPath $receiptPath -Destination (Join-Path $output 'SOURCE_BUILD_INFO068.json')
Write-Text068 (Join-Path $output 'local-play.json') ([ordered]@{format=1;owner=$binding.owner;codeSha=$SourceCommit;databaseName=$schema;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$receipt.gameJarSha256;loginJarSha256=$receipt.loginJarSha256;clientLoginPort=$ports[0];gamePort=$ports[1];controlPort=$ports[2]} | ConvertTo-Json)
Write-Text068 (Join-Path $output 'ownership068.properties') "version=1`nowner=$($binding.owner)`nrole=$($binding.role)`nschema=$schema`nruntimeRoot=$($output.Replace('\','/'))`nsourceModuleRoot=$($module.Replace('\','/'))`nclientLoginPort=$($ports[0])`ngamePort=$($ports[1])`ncontrolPort=$($ports[2])`ngameJarSha256=$($receipt.gameJarSha256)`nloginJarSha256=$($receipt.loginJarSha256)`n"
Write-Text068 (Join-Path $output 'runtime.local.ini') "JavaHome=$JavaHome`nDatabaseName=$schema`nLoginTimeoutSeconds=60`nGameTimeoutSeconds=240`nStopTimeoutSeconds=240`n"
Set-Private068 (Join-Path $output 'runtime.local.ini')
$guide068=@"
# TASK068 — самостоятельная поставка

Исходники: $SourceCommit. Тип: $Kind. БД: $schema на127.0.0.1:3308. Порты: $($ports -join ',').

START.cmd, STATUS.cmd и STOP.cmd работают из этой папки без checkout. Требуется установленный JDK25, указанный в runtime.local.ini. STOP закрывает приём новых сделок и manufacture, дожидается принятого native крафта, выполняет штатный shutdown и проверяет отсутствие соединений с собственной БД. Force fallback отсутствует.

| Файл в game/config/Custom | Ключи собственного начального профиля |
|---|---|
| PhantomPlayers.ini | EnablePhantomSystem=True; population/active/cap8; starter shots0 — измерительный профиль068 |
| PhantomMerchants.ini | EnableOfflinePhantomMerchants=True; target200; hard cap250; lifetime identities300; batch5; rotation30s |
| PhantomMerchants.ini | finite BOOTSTRAP capital100000/identity, global30000000; stock value500000000, per-item10000; без refill |
| PhantomMarket.ini | единые bid/ask/recipe/rate цены; manufacture minimum100, fee200bps BOM |
| CommunityBoard.ini | CustomCommunityBoard=True; фактические reachable Alt+B lists |
| ChampionMonsters.ini | red/blue switches сохранены OFF; BlueChampionEnable включает отдельную categorical probability |
| PersonalCharacterQoL.ini | personal master/allowlist/solo Rift сохранены OFF/пусто; глобальный minimum Party не изменён |

Все эти конфиги читаются при запуске; после изменения требуется штатный STOP/START. У существующей population нельзя произвольно менять PhantomPopulationTimeZone: он входит в сохранённый initialization contract. TEST-профиль/пассивный origin и окончательные результаты A–G фиксируются отдельно перед измерением.

PhantomStarterShotLifetimeLimit=0 нужен только профилю измерения068: заработать и купить расходники без стартовой субсидии. Механизм R2 сохранён: значение5000, заданное до рождения новых боевых identities, разрешает их конечный пожизненный фонд с сохранённым issued/limit и защитой от replay. Включение позже не выдаёт новый фонд старым identities; merchants не получают этот фонд ни при каком значении. Это настройка собственного профиля, не удаление функции.

Новые merchants не имеют battle/decision/background jobs. SELL использует существующий inventory, BUY — настоящую Adena, MANUFACTURE — выученные native рецепты/skill/MP и материалы заказчика. MerchantPreparedOfflineCharacterIds принимает только явно подготовленные disabled-account native manufacturers без другого Phantom owner; новые роли автоматически не получают чужой профессии или рецептов.

Инструмент цен находится в tools/phantom-economy/Price-PhantomEconomy.ps1, инструкция — соседний README_RU.md. Итоговая поставка сохраняет исходные цены Alt+B; тестовый Apply не является её настройкой по умолчанию. Plan по умолчанию читает game и пишет diff во внешнюю папку. Apply требует отдельного операторского решения, exact plan SHA, OWNERSHIP068.json и preimage; создаёт backup. Rollback сохраняет поздние чужие изменения и отказывается при конфликте. В собственной среде разрешён только actual1/1 профиль; x10/x20 — расчёты, Apply запрещён. После Apply/Rollback нужен restart. Не применять эти инструменты к историческим поставкам. Native TEST-покупка не подключает Alt+B к обычному снабжению фантомов.

BUILD_INFO068.json, SOURCE_BUILD_INFO068.json, TOOLS068.tsv и GEODATA068.tsv фиксируют source/JAR/tool/Geo provenance. Пароли и hexid находятся в закрытых конфигурациях; старые поставки не изменены. Клиентская визуальная QA отложена владельцем; native packet evidence её не подменяет.
"@
Write-Text068 (Join-Path $output 'START_HERE_068_RU.md') $guide068 $true
& (Join-Path $tools 'Runtime068.ps1') -Action Validate -RuntimeRoot $output
Write-Output "TASK068_ASSEMBLED kind=$Kind; source=$SourceCommit; regions203; DB_WRITES=0; PROCESS_ACTIONS=0"
