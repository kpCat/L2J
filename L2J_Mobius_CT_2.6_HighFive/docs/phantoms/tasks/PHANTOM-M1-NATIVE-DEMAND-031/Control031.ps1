[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export','Collector','DryPath','Proof')][string]$Action,
      [ValidateSet('a','b','t')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Enroll','FullObserve','Probe031','Census','Flush','StopMonitor','CrashNative','CrashFinalize')][string]$Mode='Build', [string]$CohortJson='', [string]$ObserverRunId='', [long]$StartupRecoveryProfileId=0, [string]$RecoveryOutputRoot='',
      [hashtable]$OriginPoint=@{}, [hashtable]$EndpointPoint=@{}, [switch]$UseStockRoute, [string]$ViaPoints='', [switch]$SearchBidirectional, [string]$VerifyPointsFile='',
      [ValidateSet('Persistence','Restart')][string]$ProofKind='Restart', [string]$SqlRoot='', [string]$SealedRoot='', [string]$ShutdownLog='')
function Update-Owned031 {
param([string]$Episode,[string]$Revision)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $taskRoot024 '../../../..'))
$private=Join-Path $module ".phantom-local/contract031$Episode"
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
if($manifest.databaseName -cne "l2jmobiush5_localplay_contract031$Episode"){throw 'Owned clone DB mismatch.'}
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
$syntheticSource=Join-Path $module 'tools/phantom-local-play/Start-LocalPlaySynthetic.ps1'
$syntheticRuntime=Join-Path $runtime 'Start-LocalPlaySynthetic.ps1'
if((Get-FileHash $syntheticSource).Hash -cne (Get-FileHash $syntheticRuntime).Hash){
    Copy-Item -LiteralPath $syntheticRuntime -Destination (Join-Path $backup 'Start-LocalPlaySynthetic.ps1')
    Copy-Item -LiteralPath $syntheticSource -Destination $syntheticRuntime -Force
}
$manifest.codeSha=$sha023; $manifest.gameSourceCodeSha=$sha023
$manifest.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
$manifest.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Output "OWNED_RUNTIME_UPDATED codeSha=$sha023 episode=$Episode databasePreserved=true"

}
function Export-Owned031 {
param([string]$RuntimeRoot,[string]$CohortJson,[string]$OutputRoot)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot);$output=[IO.Path]::GetFullPath($OutputRoot)
if($runtime -notmatch '[\\/]contract031[abt][\\/]runtime$'){throw 'Exact owned TASK031 runtime required.'}
if(Test-Path -LiteralPath $output){throw 'Evidence exists; no overwrite.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$cfg=Join-Path $runtime 'game/config/Database.ini'
$url=Get-PilotIniValue $cfg 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract031[abt])\?'){throw 'Owned DB guard failed.'}
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
    'TASK031_PERSISTENCE_EXPORT database='+$database+' cohort='+$rows.Count+' output='+$output
}finally{$env:MYSQL_PWD=$oldPassword}

}
$ErrorActionPreference='Stop'
$module031=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module031 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtime031=Join-Path $module031 ".phantom-local/contract031$Episode/runtime"
if($Action -ceq 'Proof'){
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    foreach($path in @($CohortJson,$SqlRoot,$SealedRoot,$OutputRoot)){
        if(-not $path -or -not [IO.Path]::GetFullPath($path).StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK031 proof paths required.'}
    }
    if(Test-Path $OutputRoot){throw 'Immutable proof exists.'}
    $name=if($ProofKind -ceq 'Persistence'){'Build-PersistenceProof024.py'}else{'Verify-Restart024.py'}
    $validator=[IO.File]::ReadAllText((Join-Path $taskRoot024 $name)).Replace('contract024','contract031').Replace('TASK024_CONTRACT','TASK031_CONTRACT')
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
    $manifest=Get-Content (Join-Path $runtime031 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract031$Episode" -or (Get-FileHash (Join-Path $runtime031 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Own dry-path DB/JAR guard failed.'}
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $helper=if($UseStockRoute){Join-Path $PSScriptRoot 'ReadDryRoute031.java'}else{Join-Path $taskRoot024 'ReadDryPath023.java'}; $dryClass=[IO.Path]::GetFileNameWithoutExtension($helper); $classes=Join-Path $module031 '.phantom-local/ops031/dry-classes'
    New-Item -ItemType Directory -Path $output,$classes -Force | Out-Null
    & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module031 'dist/libs/*') -d $classes $helper
    if($LASTEXITCODE -ne 0){throw 'Existing stock dry helper build failed.'}
    $log=Join-Path $output 'stock-dry-path.log'
    Push-Location (Join-Path $runtime031 'game')
    try{
        if($ViaPoints -and (-not $UseStockRoute -or $ViaPoints -notmatch '^-?\d+,-?\d+(;-?\d+,-?\d+){0,4}$')){throw 'Bounded explicit stock route waypoint format required.'}
        $dryArgs=@('-Xmx2g','-cp',"$classes;../libs/*",$dryClass,$OriginPoint.x,$OriginPoint.y,$OriginPoint.z,$EndpointPoint.x,$EndpointPoint.y)
        if($VerifyPointsFile){
            $input031=[IO.Path]::GetFullPath($VerifyPointsFile)
            if(-not $UseStockRoute -or $ViaPoints -or $SearchBidirectional -or -not $input031.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $input031)){throw 'Exact own immutable points/native verifier required.'}
            $dryArgs+=('VERIFY:'+$input031)
        }
        if($ViaPoints){$dryArgs+=('VIA:'+$ViaPoints)}
        if($SearchBidirectional){if(-not $UseStockRoute -or $ViaPoints){throw 'Search requires exact stock helper without VIA.'};$dryArgs+='GRID'}
        & (Join-Path $jdk 'java.exe') @dryArgs *> $log
        if($LASTEXITCODE -ne 0){throw 'Stock bidirectional dry path rejected; no MOVE allowed.'}
    }finally{Pop-Location}
    $points=@(Get-Content $log | Where-Object {$_ -match '^DRY_POINT\s'} | ForEach-Object {$parts=$_ -split '\s+';[pscustomobject]@{x=[int]$parts[1];y=[int]$parts[2];z=[int]$parts[3]}})
    if($points.Count -lt 2 -or -not (Select-String -LiteralPath $log -Pattern '^DRY_PATH_PASS ')){throw 'Complete native dry-path result missing.'}
    $steps=[Collections.Generic.List[object]]::new(); $previous=$points[0]
    if($UseStockRoute){for($i=1;$i -lt $points.Count;$i++){$steps.Add($points[$i])}}else{for($i=2;$i -lt $points.Count;$i+=2){$steps.Add($points[$i])}}
    if($steps.Count -eq 0 -or $steps[-1].x -ne $points[-1].x -or $steps[-1].y -ne $points[-1].y){$steps.Add($points[-1])}
    foreach($point in $steps){if([Math]::Sqrt([Math]::Pow($point.x-$previous.x,2)+[Math]::Pow($point.y-$previous.y,2)) -gt 300.01 -or [Math]::Abs($point.z-$previous.z) -gt 200){throw 'Native step bound exceeded.'};$previous=$point}
    @{origin=$points[0];endpoint=$points[-1];points=$points;steps=@($steps);helperSource=$helper;helperSha256=(Get-FileHash $helper).Hash;geoLog=$log;geoLogSha256=(Get-FileHash $log).Hash;jarSha256=$manifest.gameJarSha256;sourceSha=$manifest.codeSha;bidirectional=$true} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $output 'path.json') -Encoding utf8
    "TASK031_DRY_PATH samples=$($points.Count) steps=$($steps.Count)"
    return
}
if($Action -ceq 'Collector'){
    $ops=Join-Path $module031 '.phantom-local/ops031'
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $source=Join-Path $PSScriptRoot 'Contract031Observer.java'
    $hash=(Get-FileHash $source).Hash
    $agentClass='Contract031Observer'+$hash.Substring(0,12)
    $agentJar=Join-Path $ops ('observer031-'+$hash.Substring(0,12)+'.jar')
    $classes=Join-Path $ops 'observer-classes'
    if($Mode -ceq 'Build'){
        New-Item -ItemType Directory -Path $classes -Force | Out-Null
        $generated=Join-Path $ops ($agentClass+'.java')
        [IO.File]::WriteAllText($generated,[IO.File]::ReadAllText($source).Replace('Contract031Observer',$agentClass),[Text.UTF8Encoding]::new($false))
        $attach=Join-Path $module031 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
        & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module031 'dist/libs/*') -d $classes $generated $attach
        if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
        $mf=Join-Path $ops 'observer031.mf'
        [IO.File]::WriteAllText($mf,"Manifest-Version: 1.0`nAgent-Class: $agentClass`nPremain-Class: $agentClass`n`n",[Text.UTF8Encoding]::new($false))
        & (Join-Path $jdk 'jar.exe') --create --file $agentJar --manifest $mf -C $classes .
        if($LASTEXITCODE -ne 0){throw 'Collector JAR failed.'}
        [ordered]@{sourceSha256=$hash;jarSha256=(Get-FileHash $agentJar).Hash;attachSourceSha256=(Get-FileHash $attach).Hash} | ConvertTo-Json | Set-Content (Join-Path $ops 'observer031-build.json') -Encoding utf8
        return
    }
    . (Join-Path $runtime031 'LocalPlay-Pilot.ps1')
    $manifest=Get-Content (Join-Path $runtime031 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract031$Episode" -or (Get-FileHash (Join-Path $runtime031 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Exact DB/JAR guard failed.'}
    $build=Get-Content (Join-Path $ops 'observer031-build.json') -Raw | ConvertFrom-Json
    if($hash -cne $build.sourceSha256 -or (Get-FileHash $agentJar).Hash -cne $build.jarSha256){throw 'Collector hash guard failed.'}
    $state=Get-LocalPlayRoleState $runtime031 'GameServer' 'GameServer.jar' @(7777)
    if($state.state -cne 'RUNNING' -or !$state.recordVerified -or (Get-Process -Id $state.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'Exact PID/start guard failed.'}
    $startupIdentity='-javaagent:'+$agentJar.Replace([char]92,[char]47)+'='
    if(-not ([IO.File]::ReadAllText((Join-Path $runtime031 'game/java.cfg'))).Contains($startupIdentity)){throw 'STARTUP_COLLECTOR_IDENTITY_CHANGED: stop/restart required; no late observer version attach.'}
    $output=[IO.Path]::GetFullPath($OutputRoot)
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    if(-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact task evidence output guard failed.'}
    $lines=[Collections.Generic.List[string]]::new()
    $lines.Add('runtime='+$runtime031.Replace('\','/')); $lines.Add('output='+$output.Replace('\','/'))
    $lines.Add('owner=TASK031_CONTRACT'); $lines.Add('pid='+$state.pid); $lines.Add('startTicks='+$state.startTimeUtcTicks)
    $lines.Add('codeSha='+$manifest.codeSha); $lines.Add('observerRunId='+$ObserverRunId)
    if($EndpointPoint.Count){foreach($key in @('x','y','z')){$lines.Add('endpoint.'+$key+'='+[int]$EndpointPoint[$key])}}
    if($Mode -in @('CrashNative','CrashFinalize')){
        if($Episode -cne 'b'){throw 'Crash allowed only in own031b.'}
        $prior=@(Get-ChildItem (Join-Path $PSScriptRoot 'evidence') -Filter 'planned-crash.properties' -Recurse -File)
        if($prior.Count -ge 4){throw 'Four bounded crash windows already captured: two causal plus two frozen.'}
        if(Test-Path (Join-Path $output 'pre-arm-threads.txt')){throw 'Crash cannot be re-armed.'}
        New-Item -ItemType Directory -Path $output -Force | Out-Null
        $dump=Join-Path $output 'pre-arm-threads.txt'
        & (Join-Path $jdk 'jcmd.exe') $state.pid Thread.print -l *> $dump
        if($LASTEXITCODE -ne 0){throw 'Exact pre-crash jcmd failed.'}
        $lines.Add('preDump='+$dump.Replace('\','/'));$lines.Add('preDumpHash='+(Get-FileHash $dump).Hash.ToLowerInvariant())
    }
    $lines.Add('mode='+$(switch($Mode){'Enroll'{'OBSERVE'} 'FullObserve'{'FULL_OBSERVE'} 'Probe031'{'PROBE031'} 'Census'{'CENSUS'} 'Flush'{'FLUSH'} 'StopMonitor'{'STOP_MONITOR'} 'CrashNative'{'CRASH_NATIVE'} 'CrashFinalize'{'CRASH_FINALIZE'}}))
    if($Mode -notin @('Flush','StopMonitor')){
        $rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
        if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows.profileId | Select-Object -Unique).Count -ne $rows.Count){throw 'Exact cohort 1..8 guard.'}
        foreach($row in $rows){if([long]$row.profileId -le 0 -or ($Mode -in @('FullObserve','CrashNative','CrashFinalize') -and [long]$row.materializedAtNanos -le 0)){throw 'Exact profile/epoch required.'};$lines.Add('profile.'+$row.profileId+'='+$row.materializedAtNanos)}
    }
    $spec=Join-Path (Split-Path $runtime031 -Parent) ('observer-'+[guid]::NewGuid().ToString('N')+'.properties')
    [IO.File]::WriteAllLines($spec,$lines,[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk 'java.exe') --add-modules jdk.attach -cp $classes AttachContract024 $state.pid $agentJar $spec
    if($LASTEXITCODE -ne 0){throw 'Exact collector attach failed.'}
    "TASK031_OBSERVER mode=$Mode"
    return
}
switch($Action){
    'Update'{
        Update-Owned031 -Episode $Episode -Revision $Revision
    }
    'Start'{
        if($ExpectedSha -notmatch '^[0-9a-f]{40}$'){throw 'Exact committed code SHA required.'}
        $manifest031=Get-Content (Join-Path $runtime031 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest031.codeSha -cne $ExpectedSha -or $manifest031.databaseName -cne "l2jmobiush5_localplay_contract031$Episode"){throw 'Owned source/DB mismatch.'}
        foreach($role031 in @('GameServer','LoginServer')){
            $expected031=if($role031 -ceq 'GameServer'){$manifest031.gameJarSha256}else{$manifest031.loginJarSha256}
            if((Get-FileHash (Join-Path $runtime031 "libs/$role031.jar")).Hash -cne $expected031){throw 'Owned JAR hash mismatch.'}
        }
        $build031=Get-Content (Join-Path $module031 '.phantom-local/ops031/observer031-build.json') -Raw | ConvertFrom-Json
        $source031=Join-Path $PSScriptRoot 'Contract031Observer.java'
        if((Get-FileHash $source031).Hash -cne $build031.sourceSha256){throw 'Startup collector source changed.'}
        $agent031=Join-Path $module031 ('.phantom-local/ops031/observer031-'+$build031.sourceSha256.Substring(0,12)+'.jar')
        if((Get-FileHash $agent031).Hash -cne $build031.jarSha256){throw 'Startup collector JAR changed.'}
        $capture031=Join-Path $PSScriptRoot ('evidence/STARTUP031'+$Episode+'-'+[guid]::NewGuid().ToString('N'))
        if($StartupRecoveryProfileId -gt 0){
            if($Episode -cne 'b' -or -not $RecoveryOutputRoot){throw 'Own031b exact recovery profile/output required.'}
            $capture031=[IO.Path]::GetFullPath($RecoveryOutputRoot)
            if(-not $capture031.StartsWith([IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $capture031)){throw 'Immutable own recovery output required.'}
        }
        $spec031=Join-Path (Split-Path $runtime031 -Parent) ('startup-'+[guid]::NewGuid().ToString('N')+'.properties')
        $lines031=@(('runtime='+$runtime031.Replace('\','/')),('output='+$capture031.Replace('\','/')),'owner=TASK031_CONTRACT','mode=STARTUP','profile.275=0',('codeSha='+$ExpectedSha))
        if($StartupRecoveryProfileId -gt 0){$lines031=$lines031 | ForEach-Object {$_ -replace '^mode=STARTUP$','mode=STARTUP_RECOVERY' -replace '^profile.275=0$',('profile.'+$StartupRecoveryProfileId+'=0')}}
        [IO.File]::WriteAllLines($spec031,$lines031,[Text.UTF8Encoding]::new($false))
        $cfg031=Join-Path $runtime031 'game/java.cfg'
        $options031=[IO.File]::ReadAllText($cfg031) -replace '(?m)\s*-javaagent:\S+',''
        [IO.File]::WriteAllText($cfg031,$options031.Trim()+' -javaagent:'+($agent031.Replace('\','/'))+'='+($spec031.Replace('\','/')),[Text.UTF8Encoding]::new($false))
        $recoveryJob031=$null
        if($StartupRecoveryProfileId -gt 0){
            $recoveryJob031=Start-Job -ArgumentList $capture031 -ScriptBlock {
                param($Output)
                $deadline=[DateTime]::UtcNow.AddSeconds(180)
                while([DateTime]::UtcNow -lt $deadline){
                    if(Test-Path (Join-Path $Output 'recovery-commit.properties')){[IO.File]::WriteAllText((Join-Path $Output 'release-recovery.signal'),'SQL_EXPORT_COMPLETE',[Text.UTF8Encoding]::new($false));return 'RECOVERY_EXACT_EXPORT_RELEASED'}
                    Start-Sleep -Milliseconds 100
                }
                throw 'Recovery commit not observed before admission.'
            }
        }
        try{ & (Join-Path $runtime031 'Start-LocalPlay.ps1') -Background -GameTimeoutSeconds 180 }
        finally{if($recoveryJob031){$recoveryJob031 | Wait-Job -Timeout 5 | Out-Null; Receive-Job $recoveryJob031; Remove-Job $recoveryJob031 -Force}}
    }
    'Stop'{
        $dumpJob031=$null
        if($DumpDuringStop){
            $dumpRoot031=[IO.Path]::GetFullPath($OutputRoot)
            $taskEvidence031=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
            if(-not $dumpRoot031.StartsWith($taskEvidence031,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $dumpRoot031)){throw 'Immutable task stop-dump output required.'}
            . (Join-Path $runtime031 'LocalPlay-Ownership.ps1')
            $gameState031=Get-LocalPlayRoleState $runtime031 'GameServer' 'GameServer.jar' @(7777)
            if($gameState031.state -cne 'RUNNING' -or !$gameState031.recordVerified){throw 'Exact running owned GameServer required for stop dumps.'}
            New-Item -ItemType Directory -Path $dumpRoot031 | Out-Null
            $gameState031 | ConvertTo-Json | Set-Content (Join-Path $dumpRoot031 'exact-process.json') -Encoding utf8
            $dumpJob031=Start-Job -ArgumentList $gameState031.pid,$gameState031.startTimeUtcTicks,$dumpRoot031 -ScriptBlock {
                param($Game031,$Ticks031,$Output031)
                for($i031=0;$i031 -lt 4;$i031++){
                    Start-Sleep -Seconds 1
                    $process031=Get-Process -Id $Game031 -ErrorAction SilentlyContinue
                    if(!$process031){"OWNED_GAME_EXITED beforeDump=$i031";break}
                    if($process031.StartTime.ToUniversalTime().Ticks -ne [long]$Ticks031){throw 'Owned dump incarnation changed.'}
                    & 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $Game031 Thread.print -l *> (Join-Path $Output031 "threads-$i031.txt")
                    "DUMP=$i031 EXIT=$LASTEXITCODE UTC=$([DateTime]::UtcNow.ToString('O'))"
                }
            }
        }
        if($dumpJob031){ & $PSCommandPath -Action Collector -Mode StopMonitor -Episode $Episode -OutputRoot $dumpRoot031 }
        try{ & (Join-Path $runtime031 'Stop-LocalPlay.ps1') }
        finally{
            if($dumpJob031){$dumpJob031 | Wait-Job -Timeout 30 | Out-Null; Receive-Job $dumpJob031 *> (Join-Path $dumpRoot031 'dump-control.log'); Remove-Job $dumpJob031 -Force}
        }
    }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort031=Join-Path (Split-Path $runtime031 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort031 -Encoding utf8
        Export-Owned031 -RuntimeRoot $runtime031 -CohortJson $cohort031 -OutputRoot $OutputRoot
    }
}
