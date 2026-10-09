[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export','Collector','DryPath','Proof')][string]$Action,
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Enroll','FullObserve','Census','Flush','StopMonitor')][string]$Mode='Build', [string]$CohortJson='', [string]$ObserverRunId='',
      [hashtable]$OriginPoint=@{}, [hashtable]$EndpointPoint=@{},
      [ValidateSet('Persistence','Restart')][string]$ProofKind='Restart', [string]$SqlRoot='', [string]$SealedRoot='', [string]$ShutdownLog='')
function Update-Owned030 {
param([string]$Episode,[string]$Revision)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $taskRoot024 '../../../..'))
$private=Join-Path $module ".phantom-local/contract030$Episode"
$runtime=Join-Path $private 'runtime'
$backup=Join-Path $private "revision-$Revision-before"
if(Test-Path -LiteralPath $backup){throw 'Prior revision backup exists; no overwrite.'}
$sha023=(& git -C $module rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or $sha023 -notmatch '^[0-9a-f]{40}$'){throw 'Committed source identity missing.'}
foreach($role in @('GameServer','LoginServer')){
    $pidPath=Join-Path $runtime "local-play/pids/$role.json"
    if(Test-Path -LiteralPath $pidPath){
        $savedPid=Get-Content -LiteralPath $pidPath -Raw | ConvertFrom-Json
        if(Get-Process -Id ([int]$savedPid.pid) -ErrorAction SilentlyContinue){throw "PID still exists; no runtime mutation:$role/$($savedPid.pid)"}
    }
}
$manifestPath=Join-Path $runtime 'local-play.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if($manifest.databaseName -cne "l2jmobiush5_localplay_contract030$Episode"){throw 'Owned clone DB mismatch.'}
if((Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Prior JAR fingerprint mismatch.'}
New-Item -ItemType Directory -Path $backup | Out-Null
Copy-Item -LiteralPath $manifestPath -Destination $backup
foreach($role in @('GameServer','LoginServer')){
    $jarPath=Join-Path $runtime "libs/$role.jar"
    Copy-Item -LiteralPath $jarPath -Destination $backup
    Copy-Item -LiteralPath (Join-Path $module "dist/libs/$role.jar") -Destination $jarPath -Force
    $pidPath=Join-Path $runtime "local-play/pids/$role.json"
    if(Test-Path -LiteralPath $pidPath){
        $resolvedPid=[IO.Path]::GetFullPath($pidPath)
        $resolvedBackup=[IO.Path]::GetFullPath($backup)
        if(-not $resolvedPid.StartsWith([IO.Path]::GetFullPath($runtime)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or -not $resolvedBackup.StartsWith([IO.Path]::GetFullPath($private)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact move escaped owned runtime.'}
        Move-Item -LiteralPath $resolvedPid -Destination (Join-Path $resolvedBackup "$role-pid.json")
    }
}
$manifest.codeSha=$sha023; $manifest.gameSourceCodeSha=$sha023
$manifest.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
$manifest.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Output "OWNED_RUNTIME_UPDATED codeSha=$sha023 episode=$Episode databasePreserved=true"

}
function Export-Owned030 {
param([string]$RuntimeRoot,[string]$CohortJson,[string]$OutputRoot)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot);$output=[IO.Path]::GetFullPath($OutputRoot)
if($runtime -notmatch '[\\/]contract030[a-h][\\/]runtime$'){throw 'Exact owned TASK030 runtime required.'}
if(Test-Path -LiteralPath $output){throw 'Evidence exists; no overwrite.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$cfg=Join-Path $runtime 'game/config/Database.ini'
$url=Get-PilotIniValue $cfg 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract030[a-h])\?'){throw 'Owned DB guard failed.'}
$database=$Matches[2]
$rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Exact full cohort 1..8 required.'}
$ids=($rows | ForEach-Object {[long]$_.profileId}) -join ','
New-Item -ItemType Directory -Path $output | Out-Null
$oldPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=Get-PilotIniValue $cfg 'Password';$login=Get-PilotIniValue $cfg 'Login'
    $client='C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
    $common=@('--no-defaults','--host=127.0.0.1','--port=3308',"--user=$login",'--batch',"--database=$database")
    $queries=[ordered]@{
        characters="SELECT p.profile_id AS profileId,c.charId AS objectId,c.level,c.exp,c.sp,c.expBeforeDeath,c.curHp AS hp,c.maxHp,c.curMp AS mp,c.maxMp,c.curCp AS cp,c.maxCp,c.x,c.y,c.z,c.heading,CASE WHEN c.classid=c.base_class THEN 0 ELSE sc.class_index END AS classIndex,c.classid AS classId,c.race,c.vitality_points AS vitality,c.online FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id LEFT JOIN character_subclasses sc ON sc.charId=c.charId AND sc.class_id=c.classid WHERE p.profile_id IN ($ids) ORDER BY p.profile_id;"
        items="SELECT p.profile_id AS profileId,i.owner_id,i.object_id,i.item_id,i.count,i.loc,i.loc_data,i.enchant_level FROM phantom_profiles p JOIN items i ON i.owner_id=p.character_object_id WHERE p.profile_id IN ($ids) ORDER BY p.profile_id,i.object_id;"
        skills="SELECT p.profile_id AS profileId,s.charId,s.skill_id,s.skill_level,s.class_index FROM phantom_profiles p JOIN character_skills s ON s.charId=p.character_object_id WHERE p.profile_id IN ($ids) ORDER BY p.profile_id,s.class_index,s.skill_id;"
        counts="SELECT DATABASE() AS database_name,(SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='background.owned-store') AS pendingOwnedStores,(SELECT COUNT(*) FROM characters WHERE online<>0) AS onlineCharacters;"
    }
    foreach($query in $queries.GetEnumerator()){
        & $client @common -e $query.Value | Set-Content (Join-Path $output ($query.Key+'.tsv')) -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'Read-only persistence export failed: '+$query.Key}
    }
    & $client @common --skip-column-names -e "SELECT profile_id,row_version,component_type,HEX(payload) FROM phantom_profile_components WHERE profile_id IN ($ids) AND component_type IN ('background.state','background.native-context','background.owned-store') ORDER BY profile_id,component_type;" | Set-Content (Join-Path $output 'durable-hex.tsv') -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Read-only durable payload export failed.'}
    $module=[IO.Path]::GetFullPath((Join-Path $taskRoot024 '../../../..'))
    & java -cp "$module/dist/libs/GameServer.jar;$module/dist/libs/*" (Join-Path $taskRoot024 'ReadDurable024.java') (Join-Path $output 'durable-hex.tsv') | Set-Content (Join-Path $output 'durable-decoded.tsv') -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Existing codec decode failed.'}
    Get-Content (Join-Path $runtime 'local-play.json') -Raw | Set-Content (Join-Path $output 'runtime-manifest.json') -Encoding utf8
    'TASK030_PERSISTENCE_EXPORT database='+$database+' cohort='+$rows.Count+' output='+$output
}finally{$env:MYSQL_PWD=$oldPassword}

}
$ErrorActionPreference='Stop'
$module030=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module030 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtime030=Join-Path $module030 ".phantom-local/contract030$Episode/runtime"
if($Action -ceq 'Proof'){
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    foreach($path in @($CohortJson,$SqlRoot,$SealedRoot,$OutputRoot)){
        if(-not $path -or -not [IO.Path]::GetFullPath($path).StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK030 proof paths required.'}
    }
    if(Test-Path $OutputRoot){throw 'Immutable proof exists.'}
    $name=if($ProofKind -ceq 'Persistence'){'Build-PersistenceProof024.py'}else{'Verify-Restart024.py'}
    $validator=[IO.File]::ReadAllText((Join-Path $taskRoot024 $name)).Replace('contract024','contract030').Replace('TASK024_CONTRACT','TASK030_CONTRACT')
    $arguments=@('--cohort',$CohortJson,'--sealed',$SealedRoot,'--sql',$SqlRoot,'--output',$OutputRoot)
    if($ProofKind -ceq 'Persistence'){$arguments+=@('--shutdown-log',$ShutdownLog)}
    $validator | & python - @arguments
    exit $LASTEXITCODE
}
if($Action -ceq 'DryPath'){
    if($OriginPoint.Count -ne 3 -or $EndpointPoint.Count -lt 2){throw 'Actual origin XYZ and endpoint XY required; endpoint Z comes from stock geodata.'}
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    $output=[IO.Path]::GetFullPath($OutputRoot)
    if(-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $output)){throw 'Immutable own dry-path output required.'}
    $manifest=Get-Content (Join-Path $runtime030 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract030$Episode" -or (Get-FileHash (Join-Path $runtime030 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Own dry-path DB/JAR guard failed.'}
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $helper=Join-Path $taskRoot024 'ReadDryPath023.java'; $classes=Join-Path $module030 '.phantom-local/ops030/dry-classes'
    New-Item -ItemType Directory -Path $output,$classes -Force | Out-Null
    & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module030 'dist/libs/*') -d $classes $helper
    if($LASTEXITCODE -ne 0){throw 'Existing stock dry helper build failed.'}
    $log=Join-Path $output 'stock-dry-path.log'
    Push-Location (Join-Path $runtime030 'game')
    try{
        & (Join-Path $jdk 'java.exe') -Xmx2g -cp "$classes;../libs/*" ReadDryPath023 $OriginPoint.x $OriginPoint.y $OriginPoint.z $EndpointPoint.x $EndpointPoint.y *> $log
        if($LASTEXITCODE -ne 0){throw 'Stock bidirectional dry path rejected; no MOVE allowed.'}
    }finally{Pop-Location}
    $points=@(Get-Content $log | Where-Object {$_ -match '^DRY_POINT\s'} | ForEach-Object {$parts=$_ -split '\s+';[pscustomobject]@{x=[int]$parts[1];y=[int]$parts[2];z=[int]$parts[3]}})
    if($points.Count -lt 2 -or -not (Select-String -LiteralPath $log -Pattern '^DRY_PATH_PASS ')){throw 'Complete native dry-path result missing.'}
    $steps=[Collections.Generic.List[object]]::new(); $previous=$points[0]
    for($i=2;$i -lt $points.Count;$i+=2){$steps.Add($points[$i])}
    if($steps.Count -eq 0 -or $steps[-1].x -ne $points[-1].x -or $steps[-1].y -ne $points[-1].y){$steps.Add($points[-1])}
    foreach($point in $steps){if([Math]::Sqrt([Math]::Pow($point.x-$previous.x,2)+[Math]::Pow($point.y-$previous.y,2)) -gt 300.01 -or [Math]::Abs($point.z-$previous.z) -gt 200){throw 'Native step bound exceeded.'};$previous=$point}
    @{origin=$points[0];endpoint=$points[-1];points=$points;steps=@($steps);helperSha256=(Get-FileHash $helper).Hash;geoLog=$log;geoLogSha256=(Get-FileHash $log).Hash;jarSha256=$manifest.gameJarSha256;sourceSha=$manifest.codeSha;bidirectional=$true} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $output 'path.json') -Encoding utf8
    "TASK030_DRY_PATH samples=$($points.Count) steps=$($steps.Count)"
    return
}
if($Action -ceq 'Collector'){
    $ops=Join-Path $module030 '.phantom-local/ops030'
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $source=Join-Path $PSScriptRoot 'Contract030Observer.java'
    $hash=(Get-FileHash $source).Hash
    $agentClass='Contract030Observer'+$hash.Substring(0,12)
    $agentJar=Join-Path $ops ('observer030-'+$hash.Substring(0,12)+'.jar')
    $classes=Join-Path $ops 'observer-classes'
    if($Mode -ceq 'Build'){
        New-Item -ItemType Directory -Path $classes -Force | Out-Null
        $generated=Join-Path $ops ($agentClass+'.java')
        [IO.File]::WriteAllText($generated,[IO.File]::ReadAllText($source).Replace('Contract030Observer',$agentClass),[Text.UTF8Encoding]::new($false))
        $attach=Join-Path $module030 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
        & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module030 'dist/libs/*') -d $classes $generated $attach
        if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
        $mf=Join-Path $ops 'observer030.mf'
        [IO.File]::WriteAllText($mf,"Manifest-Version: 1.0`nAgent-Class: $agentClass`nPremain-Class: $agentClass`n`n",[Text.UTF8Encoding]::new($false))
        & (Join-Path $jdk 'jar.exe') --create --file $agentJar --manifest $mf -C $classes .
        if($LASTEXITCODE -ne 0){throw 'Collector JAR failed.'}
        [ordered]@{sourceSha256=$hash;jarSha256=(Get-FileHash $agentJar).Hash;attachSourceSha256=(Get-FileHash $attach).Hash} | ConvertTo-Json | Set-Content (Join-Path $ops 'observer030-build.json') -Encoding utf8
        return
    }
    . (Join-Path $runtime030 'LocalPlay-Pilot.ps1')
    $manifest=Get-Content (Join-Path $runtime030 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract030$Episode" -or (Get-FileHash (Join-Path $runtime030 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Exact DB/JAR guard failed.'}
    $build=Get-Content (Join-Path $ops 'observer030-build.json') -Raw | ConvertFrom-Json
    if($hash -cne $build.sourceSha256 -or (Get-FileHash $agentJar).Hash -cne $build.jarSha256){throw 'Collector hash guard failed.'}
    $state=Get-LocalPlayRoleState $runtime030 'GameServer' 'GameServer.jar' @(7777)
    if($state.state -cne 'RUNNING' -or !$state.recordVerified -or (Get-Process -Id $state.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'Exact PID/start guard failed.'}
    $output=[IO.Path]::GetFullPath($OutputRoot)
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    if(-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact task evidence output guard failed.'}
    $lines=[Collections.Generic.List[string]]::new()
    $lines.Add('runtime='+$runtime030.Replace('\','/')); $lines.Add('output='+$output.Replace('\','/'))
    $lines.Add('owner=TASK030_CONTRACT'); $lines.Add('pid='+$state.pid); $lines.Add('startTicks='+$state.startTimeUtcTicks)
    $lines.Add('codeSha='+$manifest.codeSha); $lines.Add('observerRunId='+$ObserverRunId)
    if($EndpointPoint.Count){foreach($key in @('x','y','z')){$lines.Add('endpoint.'+$key+'='+[int]$EndpointPoint[$key])}}
    $lines.Add('mode='+$(switch($Mode){'Enroll'{'OBSERVE'} 'FullObserve'{'FULL_OBSERVE'} 'Census'{'CENSUS'} 'Flush'{'FLUSH'} 'StopMonitor'{'STOP_MONITOR'}}))
    if($Mode -notin @('Flush','StopMonitor')){
        $rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
        if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows.profileId | Select-Object -Unique).Count -ne $rows.Count){throw 'Exact cohort 1..8 guard.'}
        foreach($row in $rows){if([long]$row.profileId -le 0 -or ($Mode -ceq 'FullObserve' -and [long]$row.materializedAtNanos -le 0)){throw 'Exact profile/epoch required.'};$lines.Add('profile.'+$row.profileId+'='+$row.materializedAtNanos)}
    }
    $spec=Join-Path (Split-Path $runtime030 -Parent) ('observer-'+[guid]::NewGuid().ToString('N')+'.properties')
    [IO.File]::WriteAllLines($spec,$lines,[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk 'java.exe') --add-modules jdk.attach -cp $classes AttachContract024 $state.pid $agentJar $spec
    if($LASTEXITCODE -ne 0){throw 'Exact collector attach failed.'}
    "TASK030_OBSERVER mode=$Mode"
    return
}
switch($Action){
    'Update'{
        Update-Owned030 -Episode $Episode -Revision $Revision
    }
    'Start'{
        if($ExpectedSha -notmatch '^[0-9a-f]{40}$'){throw 'Exact committed code SHA required.'}
        $manifest030=Get-Content (Join-Path $runtime030 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest030.codeSha -cne $ExpectedSha -or $manifest030.databaseName -cne "l2jmobiush5_localplay_contract030$Episode"){throw 'Owned source/DB mismatch.'}
        foreach($role030 in @('GameServer','LoginServer')){
            $expected030=if($role030 -ceq 'GameServer'){$manifest030.gameJarSha256}else{$manifest030.loginJarSha256}
            if((Get-FileHash (Join-Path $runtime030 "libs/$role030.jar")).Hash -cne $expected030){throw 'Owned JAR hash mismatch.'}
        }
        $build030=Get-Content (Join-Path $module030 '.phantom-local/ops030/observer030-build.json') -Raw | ConvertFrom-Json
        $source030=Join-Path $PSScriptRoot 'Contract030Observer.java'
        if((Get-FileHash $source030).Hash -cne $build030.sourceSha256){throw 'Startup collector source changed.'}
        $agent030=Join-Path $module030 ('.phantom-local/ops030/observer030-'+$build030.sourceSha256.Substring(0,12)+'.jar')
        if((Get-FileHash $agent030).Hash -cne $build030.jarSha256){throw 'Startup collector JAR changed.'}
        $capture030=Join-Path $PSScriptRoot ('evidence/STARTUP030'+$Episode+'-'+[guid]::NewGuid().ToString('N'))
        $spec030=Join-Path (Split-Path $runtime030 -Parent) ('startup-'+[guid]::NewGuid().ToString('N')+'.properties')
        $lines030=@(('runtime='+$runtime030.Replace('\','/')),('output='+$capture030.Replace('\','/')),'owner=TASK030_CONTRACT','mode=STARTUP','profile.275=0',('codeSha='+$ExpectedSha))
        [IO.File]::WriteAllLines($spec030,$lines030,[Text.UTF8Encoding]::new($false))
        $cfg030=Join-Path $runtime030 'game/java.cfg'
        $options030=[IO.File]::ReadAllText($cfg030) -replace '(?m)\s*-javaagent:\S+',''
        [IO.File]::WriteAllText($cfg030,$options030.Trim()+' -javaagent:'+($agent030.Replace('\','/'))+'='+($spec030.Replace('\','/')),[Text.UTF8Encoding]::new($false))
        & (Join-Path $runtime030 'Start-LocalPlay.ps1') -Background -GameTimeoutSeconds 180
    }
    'Stop'{
        $dumpJob030=$null
        if($DumpDuringStop){
            $dumpRoot030=[IO.Path]::GetFullPath($OutputRoot)
            $taskEvidence030=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
            if(-not $dumpRoot030.StartsWith($taskEvidence030,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $dumpRoot030)){throw 'Immutable task stop-dump output required.'}
            . (Join-Path $runtime030 'LocalPlay-Ownership.ps1')
            $gameState030=Get-LocalPlayRoleState $runtime030 'GameServer' 'GameServer.jar' @(7777)
            if($gameState030.state -cne 'RUNNING' -or !$gameState030.recordVerified){throw 'Exact running owned GameServer required for stop dumps.'}
            New-Item -ItemType Directory -Path $dumpRoot030 | Out-Null
            $gameState030 | ConvertTo-Json | Set-Content (Join-Path $dumpRoot030 'exact-process.json') -Encoding utf8
            $dumpJob030=Start-Job -ArgumentList $gameState030.pid,$gameState030.startTimeUtcTicks,$dumpRoot030 -ScriptBlock {
                param($Game030,$Ticks030,$Output030)
                for($i030=0;$i030 -lt 4;$i030++){
                    Start-Sleep -Seconds 1
                    $process030=Get-Process -Id $Game030 -ErrorAction SilentlyContinue
                    if(!$process030){"OWNED_GAME_EXITED beforeDump=$i030";break}
                    if($process030.StartTime.ToUniversalTime().Ticks -ne [long]$Ticks030){throw 'Owned dump incarnation changed.'}
                    & 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $Game030 Thread.print -l *> (Join-Path $Output030 "threads-$i030.txt")
                    "DUMP=$i030 EXIT=$LASTEXITCODE UTC=$([DateTime]::UtcNow.ToString('O'))"
                }
            }
        }
        if($dumpJob030){ & $PSCommandPath -Action Collector -Mode StopMonitor -Episode $Episode -OutputRoot $dumpRoot030 }
        try{ & (Join-Path $runtime030 'Stop-LocalPlay.ps1') }
        finally{
            if($dumpJob030){$dumpJob030 | Wait-Job -Timeout 30 | Out-Null; Receive-Job $dumpJob030 *> (Join-Path $dumpRoot030 'dump-control.log'); Remove-Job $dumpJob030 -Force}
        }
    }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort030=Join-Path (Split-Path $runtime030 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort030 -Encoding utf8
        Export-Owned030 -RuntimeRoot $runtime030 -CohortJson $cohort030 -OutputRoot $OutputRoot
    }
}
