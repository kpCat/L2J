[CmdletBinding()]
param([ValidateSet('Test','Release')][string]$Kind='Release', [switch]$RefreshCommittedBuild)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$task=Join-Path (Split-Path $module -Parent) '.phantom-local/task074'
$test=$Kind -ceq 'Test'; $kindName=$Kind.ToLowerInvariant()
$output=if($test){Join-Path $task 'runtime-test'}else{Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Living-Systems-074-candidate'}
$schema='l2jmobiush5_phantom_'+$kindName+'_074'; $ports=if($test){@(2254,7954,9254)}else{@(2106,7777,9154)}
foreach($path in @($module,$task,$output)){Assert-Plain073 $path}
if((Test-Path -LiteralPath $output) -and !$RefreshCommittedBuild){throw 'OWN074 installation exists; no overwrite/migration.'}
if($RefreshCommittedBuild -and !(Test-Path -LiteralPath $output)){throw 'OWN074 refresh requires its existing stopped installation.'}
if((Read-Field073 (Join-Path $task 'ownership074.properties') 'owner') -cne 'TASK074_LIVING_SYSTEMS' -or !(Test-Path -LiteralPath (Join-Path $task ('bootstrap-'+$kindName+'.complete')))){throw 'OWN074 task/schema installation required.'}
if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -in $ports).Count){throw 'OWN074 selected port occupied; preserve owner.'}
$info=Read-Json073 (Join-Path $task 'BUILD074.json'); $source=Join-Path $task 'source-final/L2J_Mobius_CT_2.6_HighFive'; $build=Join-Path $task 'build-final'
if($info.PSObject.Properties.Name -contains 'sourceRoot'){$source=$info.sourceRoot;$build=$info.buildRoot}
if($info.owner -cne 'TASK074_LIVING_SYSTEMS' -or $info.sourceState -cne 'COMMITTED' -or $info.sourceCommit -cnotmatch '^[0-9a-f]{40}$' -or (Get-FileHash -LiteralPath $info.sourceArchive -Algorithm SHA256).Hash -ine $info.sourceArchiveSha256 -or (Get-TreeHash073 $source @('build.xml','java','test','tools','dist') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')) -ine $info.sourceTreeSha256 -or (Get-TreeHash073 (Join-Path $build 'phantom-test') @('bin','resources')) -ine $info.testTreeSha256){throw 'OWN074 committed archive/source/test provenance differs.'}
foreach($role in @('GameServer','LoginServer')){$key=if($role -ceq 'GameServer'){'gameJarSha256'}else{'loginJarSha256'};if((Get-FileHash -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Algorithm SHA256).Hash -ine $info.$key){throw 'OWN074 final JAR differs.'}}
if($RefreshCommittedBuild){
    $refreshLock=[IO.File]::Open((Join-Path $output 'local-play/operator.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
    $bound=Join-Path $output 'ownership074.properties'; $installation=Read-Field073 (Join-Path $task 'ownership074.properties') 'installationId'
    if((Read-Field073 $bound 'owner') -cne 'TASK074_LIVING_SYSTEMS' -or (Read-Field073 $bound 'schema') -cne $schema -or (Read-Field073 $bound 'installationId') -cne $installation){throw 'OWN074 refresh authority differs.'}
    & (Join-Path $output 'tools/Runtime074.ps1') -Action Validate -RuntimeRoot $output
    $state=& (Join-Path $output 'tools/Runtime074.ps1') -Action Status -RuntimeRoot $output | Out-String | ConvertFrom-Json
    if($state.game.state -notin @('STOPPED','STALE_RECORD') -or $state.login.state -notin @('STOPPED','STALE_RECORD') -or [int]$state.databaseConnections -ne 0){throw 'OWN074 refresh requires stopped incarnations and DB0.'}
    $old=Read-Json073 (Join-Path $output 'BUILD074.json')
    if($old.sourceCommit -ceq $info.sourceCommit){throw 'OWN074 refresh requires the one new committed correction.'}
    $outside=Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Living-Systems-074-candidate'
    if((Read-Field073 (Join-Path $outside 'ownership074.properties') 'installationId') -cne $installation){throw 'OWN074 outside-checkout backup owner differs.'}
    $backup=Join-Path $outside ('local-play/provenance/'+$kindName+'-before-'+$old.sourceCommit)
    Assert-Plain073 $backup
    if(Test-Path -LiteralPath $backup){throw 'OWN074 prior build backup exists; no repeated refresh.'}
    New-Item -ItemType Directory -Path $backup | Out-Null
    foreach($relative in @('libs/GameServer.jar','libs/LoginServer.jar','SOURCE_BUILD074.json','BUILD074.json','ownership074.properties','START_HERE_074_RU.md','tools/Runtime074Probe.java','tools/classes/Runtime074Probe.class','TOOLS074.tsv')){
        $original=Join-Path $output $relative; $copy=Join-Path $backup ([IO.Path]::GetFileName($relative))
        Copy-Item -LiteralPath $original -Destination $copy
        if((Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash -ine (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash){throw 'OWN074 byte backup differs; original preserved.'}
    }
    $javaHome=Read-Field073 (Join-Path $output 'runtime.local.ini') 'JavaHome'; Assert-Java073 $javaHome
    $probeBuild=Join-Path $build ('operator-probe-'+$kindName)
    if(Test-Path -LiteralPath $probeBuild){throw 'OWN074 correction probe artifact already exists; no overwrite.'}
    New-Item -ItemType Directory -Path $probeBuild | Out-Null
    & (Join-Path $javaHome 'bin/javac.exe') -encoding UTF-8 -d $probeBuild (Join-Path $source 'tools/phantom-runtime/Runtime074Probe.java')
    if($LASTEXITCODE){throw 'OWN074 correction read-only probe compile failed; runtime artifacts preserved.'}
    foreach($role in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Destination (Join-Path $output "libs/$role.jar") -Force}
    Copy-Item -LiteralPath (Join-Path $source 'tools/phantom-runtime/Runtime074Probe.java') -Destination (Join-Path $output 'tools/Runtime074Probe.java') -Force
    Copy-Item -LiteralPath (Join-Path $probeBuild 'Runtime074Probe.class') -Destination (Join-Path $output 'tools/classes/Runtime074Probe.class') -Force
    @('tools/Runtime074.ps1','tools/Runtime074Probe.java','tools/Task073Common.ps1','tools/LocalPlay-Ownership.ps1','tools/classes/Runtime074Probe.class') | ForEach-Object {[pscustomobject]@{path=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $output $_) -Algorithm SHA256).Hash}} | Export-Csv -LiteralPath (Join-Path $output 'TOOLS074.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $task 'BUILD074.json') -Destination (Join-Path $output 'SOURCE_BUILD074.json') -Force
    foreach($entry in @(@('toolManifestSha256','TOOLS074.tsv'),@('geoManifestSha256','GEODATA074.tsv'),@('sourceReceiptSha256','SOURCE_BUILD074.json'))){$info | Add-Member -NotePropertyName $entry[0] -NotePropertyValue (Get-FileHash -LiteralPath (Join-Path $output $entry[1]) -Algorithm SHA256).Hash}
    Write-Text073 (Join-Path $output 'BUILD074.json') ($info | ConvertTo-Json)
    $binding=[IO.File]::ReadAllText($bound)
    foreach($key in @('gameJarSha256','loginJarSha256')){$binding=[regex]::Replace($binding,('(?m)^'+$key+'=.*$'),($key+'='+$info.$key))}
    Write-Text073 $bound $binding
    $guide=Join-Path $output 'START_HERE_074_RU.md'; Write-Text073 $guide ([IO.File]::ReadAllText($guide).Replace($old.sourceCommit,$info.sourceCommit)) $true
    & (Join-Path $output 'tools/Runtime074.ps1') -Action Validate -RuntimeRoot $output
    Write-Output "OWN074_REFRESHED kind=$Kind;source=$($info.sourceCommit);same schema/UUID/config/data;old artifacts byte-backed outside checkout;DB0;no process actions"
    return
    }finally{$refreshLock.Dispose()}
}
$geo=@(Import-Csv -LiteralPath (Join-Path $task 'GEODATA074.tsv') -Delimiter "`t")
if($geo.Count -ne 203){throw 'OWN074 physical203 regions required.'}
foreach($region in $geo){Assert-Plain073 $region.source; if((Get-FileHash -LiteralPath $region.source -Algorithm SHA256).Hash -ine $region.sha256){throw 'OWN074 source Geo differs.'}}
New-Item -ItemType Directory -Path $output | Out-Null
Copy-Item -Path (Join-Path $source 'dist/*') -Destination $output -Recurse
foreach($role in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $build "dist/libs/$role.jar") -Destination (Join-Path $output "libs/$role.jar") -Force}
foreach($folder in @('tools','tools/classes','local-play/control','local-play/pids','local-play/logs')){New-Item -ItemType Directory -Path (Join-Path $output $folder) -Force | Out-Null}
$settings=Join-Path $task "private/Database.$kindName.ini"
if((Read-Field073 $settings 'URL') -cnotmatch ('^jdbc:mysql://127\.0\.0\.1:3308/'+$schema+'\?')){throw 'OWN074 private JDBC differs.'}
foreach($role in @('game','login')){Copy-Item -LiteralPath $settings -Destination (Join-Path $output "$role/config/Database.ini") -Force;Set-Private073 (Join-Path $output "$role/config/Database.ini")}
Copy-Item -LiteralPath (Join-Path $task "private/hexid-$kindName.txt") -Destination (Join-Path $output 'game/config/hexid.txt') -Force; Set-Private073 (Join-Path $output 'game/config/hexid.txt')
foreach($region in $geo){$target=Join-Path $output $region.relativePath;Copy-Item -LiteralPath $region.source -Destination $target;if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ine $region.sha256){throw 'OWN074 installed physical Geo differs.'}}
Copy-Item -LiteralPath (Join-Path $task 'GEODATA074.tsv') -Destination (Join-Path $output 'GEODATA074.tsv')
Copy-Item -LiteralPath (Join-Path $task 'BUILD074.json') -Destination (Join-Path $output 'SOURCE_BUILD074.json')
foreach($entry in @(
    @('game/config/Server.ini','GameserverPort',[string]$ports[1]),@('game/config/Server.ini','LoginPort',[string]$ports[2]),@('game/config/Server.ini','GameserverHostname','127.0.0.1'),@('game/config/Server.ini','LoginHost','127.0.0.1'),@('game/config/Server.ini','AcceptAlternateID','False'),
    @('login/config/Server.ini','LoginserverPort',[string]$ports[0]),@('login/config/Server.ini','LoginPort',[string]$ports[2]),@('login/config/Server.ini','LoginserverHostname','127.0.0.1'),@('login/config/Server.ini','LoginHostname','127.0.0.1'),@('login/config/Server.ini','AcceptNewGameServer','False'),
    @('game/config/Interface.ini','EnableGUI','False'),@('login/config/Interface.ini','EnableGUI','False'),@('game/config/Custom/PhantomPlayers.ini','EnablePhantomSystem','True'),@('game/config/Custom/PhantomPlayers.ini','PhantomPopulationTarget','16'),@('game/config/Custom/PhantomPlayers.ini','PhantomPopulationActiveTarget','16'),@('game/config/Custom/PhantomPlayers.ini','MaxMaterializedPhantoms','16'),@('game/config/Custom/PhantomMerchants.ini','EnableOfflinePhantomMerchants','True')
)){Set-Config073 $output $entry[0] $entry[1] $entry[2]}
$javaHome=Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent; Assert-Java073 $javaHome
foreach($file in @('Runtime074.ps1','Runtime074Probe.java','Task073Common.ps1')){Copy-Item -LiteralPath (Join-Path $source "tools/phantom-runtime/$file") -Destination (Join-Path $output 'tools')}
Copy-Item -LiteralPath (Join-Path $source 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination (Join-Path $output 'tools')
& (Join-Path $javaHome 'bin/javac.exe') -encoding UTF-8 -d (Join-Path $output 'tools/classes') (Join-Path $output 'tools/Runtime074Probe.java')
if($LASTEXITCODE){throw 'OWN074 read-only operator helper compile failed.'}
@('tools/Runtime074.ps1','tools/Runtime074Probe.java','tools/Task073Common.ps1','tools/LocalPlay-Ownership.ps1','tools/classes/Runtime074Probe.class') | ForEach-Object {[pscustomobject]@{path=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $output $_) -Algorithm SHA256).Hash}} | Export-Csv -LiteralPath (Join-Path $output 'TOOLS074.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$info | Add-Member -NotePropertyName toolManifestSha256 -NotePropertyValue (Get-FileHash -LiteralPath (Join-Path $output 'TOOLS074.tsv') -Algorithm SHA256).Hash
$info | Add-Member -NotePropertyName geoManifestSha256 -NotePropertyValue (Get-FileHash -LiteralPath (Join-Path $output 'GEODATA074.tsv') -Algorithm SHA256).Hash
$info | Add-Member -NotePropertyName sourceReceiptSha256 -NotePropertyValue (Get-FileHash -LiteralPath (Join-Path $output 'SOURCE_BUILD074.json') -Algorithm SHA256).Hash
Write-Text073 (Join-Path $output 'BUILD074.json') ($info | ConvertTo-Json)
$installation=Read-Field073 (Join-Path $task 'ownership074.properties') 'installationId';$role=if($test){'MUTABLE_FIXTURE'}else{'RELEASE_CANDIDATE'}
Write-Text073 (Join-Path $output 'ownership074.properties') "version=1`nowner=TASK074_LIVING_SYSTEMS`nrole=$role`nschema=$schema`ninstallationId=$installation`nruntimeRoot=$($output.Replace('\','/'))`nsourceModuleRoot=$($module.Replace('\','/'))`ngameJarSha256=$($info.gameJarSha256)`nloginJarSha256=$($info.loginJarSha256)`n"
Write-Text073 (Join-Path $output 'runtime.local.ini') "JavaHome=$javaHome`nDatabaseName=$schema`n"
foreach($action in @('START','STATUS','STOP')){Write-Text073 (Join-Path $output "$action.cmd") "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime074.ps1`" -Action $action -RuntimeRoot `"%~dp0.`"`r`nexit /b %errorlevel%`r`n"}
Write-Text073 (Join-Path $output 'START_HERE_074_RU.md') "# OWN074 — самостоятельная поставка`n`nИсходники: $($info.sourceCommit). БД: $schema, 127.0.0.1:3308. Порты: $($ports -join ','). JDK25: runtime.local.ini. Штатный heap из java.cfg сохранён.`n`nSTART.cmd, STATUS.cmd, STOP.cmd работают из этой папки. STOP проверяет incarnation и вызывает штатное завершение; принудительного kill нет. Чужие процессы и занятые порты не изменяются.`n`n16 обычных активных фантомов; до200 отдельных offline-лавок по конечным существующим квотам. Bootstrap не является заработком. Повторный запуск сохраняет ту же БД, identities и квоты; refill отсутствует. Число лавок не доказывает полноту ассортимента.`n`nBUILD074.json, TOOLS074.tsv и GEODATA074.tsv фиксируют исходники, JAR, tools и203 физические региона. Реальные игровые PASS/PARTIAL и ограничения находятся в отчёте PHANTOM-LIVING-SYSTEMS-074. Личная визуальная приёмка отложена владельцу.`n" $true
& (Join-Path $output 'tools/Runtime074.ps1') -Action Validate -RuntimeRoot $output
Write-Output "OWN074_ASSEMBLED kind=$Kind; root=$output; source=$($info.sourceCommit); physicalGeo203; DB_WRITES=0; PROCESS_ACTIONS=0"
