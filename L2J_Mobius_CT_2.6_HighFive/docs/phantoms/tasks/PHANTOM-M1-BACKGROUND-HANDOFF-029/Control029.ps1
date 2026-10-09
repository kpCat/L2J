[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export','Collector','DryPath','Proof')][string]$Action,
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Enroll','FullObserve','Census','Flush')][string]$Mode='Build', [string]$CohortJson='', [string]$ObserverRunId='',
      [hashtable]$OriginPoint=@{}, [hashtable]$EndpointPoint=@{},
      [ValidateSet('Persistence','Restart')][string]$ProofKind='Restart', [string]$SqlRoot='', [string]$SealedRoot='', [string]$ShutdownLog='')
$ErrorActionPreference='Stop'
$module029=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module029 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtime029=Join-Path $module029 ".phantom-local/contract029$Episode/runtime"
if($Action -ceq 'Proof'){
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    foreach($path in @($CohortJson,$SqlRoot,$SealedRoot,$OutputRoot)){
        if(-not $path -or -not [IO.Path]::GetFullPath($path).StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact TASK029 proof paths required.'}
    }
    if(Test-Path $OutputRoot){throw 'Immutable proof exists.'}
    $name=if($ProofKind -ceq 'Persistence'){'Build-PersistenceProof024.py'}else{'Verify-Restart024.py'}
    $validator=[IO.File]::ReadAllText((Join-Path $taskRoot024 $name)).Replace('contract024','contract029').Replace('TASK024_CONTRACT','TASK029_CONTRACT')
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
    $manifest=Get-Content (Join-Path $runtime029 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract029$Episode" -or (Get-FileHash (Join-Path $runtime029 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Own dry-path DB/JAR guard failed.'}
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $helper=Join-Path $taskRoot024 'ReadDryPath023.java'; $classes=Join-Path $module029 '.phantom-local/ops029/dry-classes'
    New-Item -ItemType Directory -Path $output,$classes -Force | Out-Null
    & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module029 'dist/libs/*') -d $classes $helper
    if($LASTEXITCODE -ne 0){throw 'Existing stock dry helper build failed.'}
    $log=Join-Path $output 'stock-dry-path.log'
    Push-Location (Join-Path $runtime029 'game')
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
    "TASK029_DRY_PATH samples=$($points.Count) steps=$($steps.Count)"
    return
}
if($Action -ceq 'Collector'){
    $ops=Join-Path $module029 '.phantom-local/ops029'
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $source=Join-Path $PSScriptRoot 'Contract029Observer.java'
    $hash=(Get-FileHash $source).Hash
    $agentClass='Contract029Observer'+$hash.Substring(0,12)
    $agentJar=Join-Path $ops ('observer029-'+$hash.Substring(0,12)+'.jar')
    $classes=Join-Path $ops 'observer-classes'
    if($Mode -ceq 'Build'){
        New-Item -ItemType Directory -Path $classes -Force | Out-Null
        $generated=Join-Path $ops ($agentClass+'.java')
        [IO.File]::WriteAllText($generated,[IO.File]::ReadAllText($source).Replace('Contract029Observer',$agentClass),[Text.UTF8Encoding]::new($false))
        $attach=Join-Path $module029 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
        & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module029 'dist/libs/*') -d $classes $generated $attach
        if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
        $mf=Join-Path $ops 'observer029.mf'
        [IO.File]::WriteAllText($mf,"Manifest-Version: 1.0`nAgent-Class: $agentClass`n`n",[Text.UTF8Encoding]::new($false))
        & (Join-Path $jdk 'jar.exe') --create --file $agentJar --manifest $mf -C $classes .
        if($LASTEXITCODE -ne 0){throw 'Collector JAR failed.'}
        [ordered]@{sourceSha256=$hash;jarSha256=(Get-FileHash $agentJar).Hash;attachSourceSha256=(Get-FileHash $attach).Hash} | ConvertTo-Json | Set-Content (Join-Path $ops 'observer029-build.json') -Encoding utf8
        return
    }
    . (Join-Path $runtime029 'LocalPlay-Pilot.ps1')
    $manifest=Get-Content (Join-Path $runtime029 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract029$Episode" -or (Get-FileHash (Join-Path $runtime029 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Exact DB/JAR guard failed.'}
    $build=Get-Content (Join-Path $ops 'observer029-build.json') -Raw | ConvertFrom-Json
    if($hash -cne $build.sourceSha256 -or (Get-FileHash $agentJar).Hash -cne $build.jarSha256){throw 'Collector hash guard failed.'}
    $state=Get-LocalPlayRoleState $runtime029 'GameServer' 'GameServer.jar' @(7777)
    if($state.state -cne 'RUNNING' -or !$state.recordVerified -or (Get-Process -Id $state.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'Exact PID/start guard failed.'}
    $output=[IO.Path]::GetFullPath($OutputRoot)
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    if(-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact task evidence output guard failed.'}
    $lines=[Collections.Generic.List[string]]::new()
    $lines.Add('runtime='+$runtime029.Replace('\','/')); $lines.Add('output='+$output.Replace('\','/'))
    $lines.Add('owner=TASK029_CONTRACT'); $lines.Add('pid='+$state.pid); $lines.Add('startTicks='+$state.startTimeUtcTicks)
    $lines.Add('codeSha='+$manifest.codeSha); $lines.Add('observerRunId='+$ObserverRunId)
    if($EndpointPoint.Count){foreach($key in @('x','y','z')){$lines.Add('endpoint.'+$key+'='+[int]$EndpointPoint[$key])}}
    $lines.Add('mode='+$(switch($Mode){'Enroll'{'OBSERVE'} 'FullObserve'{'FULL_OBSERVE'} 'Census'{'CENSUS'} 'Flush'{'FLUSH'}}))
    if($Mode -ne 'Flush'){
        $rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
        if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows.profileId | Select-Object -Unique).Count -ne $rows.Count){throw 'Exact cohort 1..8 guard.'}
        foreach($row in $rows){if([long]$row.profileId -le 0 -or ($Mode -ceq 'FullObserve' -and [long]$row.materializedAtNanos -le 0)){throw 'Exact profile/epoch required.'};$lines.Add('profile.'+$row.profileId+'='+$row.materializedAtNanos)}
    }
    $spec=Join-Path (Split-Path $runtime029 -Parent) ('observer-'+[guid]::NewGuid().ToString('N')+'.properties')
    [IO.File]::WriteAllLines($spec,$lines,[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk 'java.exe') --add-modules jdk.attach -cp $classes AttachContract024 $state.pid $agentJar $spec
    if($LASTEXITCODE -ne 0){throw 'Exact collector attach failed.'}
    "TASK029_OBSERVER mode=$Mode"
    return
}
switch($Action){
    'Update'{
        $body029=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Update-OwnedRuntime024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract029')
        & ([scriptblock]::Create($body029)) -Episode $Episode -Revision $Revision
    }
    'Start'{
        if($ExpectedSha -notmatch '^[0-9a-f]{40}$'){throw 'Exact committed code SHA required.'}
        $manifest029=Get-Content (Join-Path $runtime029 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest029.codeSha -cne $ExpectedSha -or $manifest029.databaseName -cne "l2jmobiush5_localplay_contract029$Episode"){throw 'Owned source/DB mismatch.'}
        foreach($role029 in @('GameServer','LoginServer')){
            $expected029=if($role029 -ceq 'GameServer'){$manifest029.gameJarSha256}else{$manifest029.loginJarSha256}
            if((Get-FileHash (Join-Path $runtime029 "libs/$role029.jar")).Hash -cne $expected029){throw 'Owned JAR hash mismatch.'}
        }
        & (Join-Path $runtime029 'Start-LocalPlay.ps1') -Background -GameTimeoutSeconds 180
    }
    'Stop'{
        $dumpJob029=$null
        if($DumpDuringStop){
            $dumpRoot029=[IO.Path]::GetFullPath($OutputRoot)
            $taskEvidence029=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
            if(-not $dumpRoot029.StartsWith($taskEvidence029,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $dumpRoot029)){throw 'Immutable task stop-dump output required.'}
            . (Join-Path $runtime029 'LocalPlay-Ownership.ps1')
            $gameState029=Get-LocalPlayRoleState $runtime029 'GameServer' 'GameServer.jar' @(7777)
            if($gameState029.state -cne 'RUNNING' -or !$gameState029.recordVerified){throw 'Exact running owned GameServer required for stop dumps.'}
            New-Item -ItemType Directory -Path $dumpRoot029 | Out-Null
            $gameState029 | ConvertTo-Json | Set-Content (Join-Path $dumpRoot029 'exact-process.json') -Encoding utf8
            $dumpJob029=Start-Job -ArgumentList $gameState029.pid,$gameState029.startTimeUtcTicks,$dumpRoot029 -ScriptBlock {
                param($Game029,$Ticks029,$Output029)
                for($i029=0;$i029 -lt 4;$i029++){
                    Start-Sleep -Seconds 1
                    $process029=Get-Process -Id $Game029 -ErrorAction SilentlyContinue
                    if(!$process029){"OWNED_GAME_EXITED beforeDump=$i029";break}
                    if($process029.StartTime.ToUniversalTime().Ticks -ne [long]$Ticks029){throw 'Owned dump incarnation changed.'}
                    & 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $Game029 Thread.print -l *> (Join-Path $Output029 "threads-$i029.txt")
                    "DUMP=$i029 EXIT=$LASTEXITCODE UTC=$([DateTime]::UtcNow.ToString('O'))"
                }
            }
        }
        try{ & (Join-Path $runtime029 'Stop-LocalPlay.ps1') }
        finally{
            if($dumpJob029){$dumpJob029 | Wait-Job -Timeout 30 | Out-Null; Receive-Job $dumpJob029 *> (Join-Path $dumpRoot029 'dump-control.log'); Remove-Job $dumpJob029 -Force}
        }
    }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort029=Join-Path (Split-Path $runtime029 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort029 -Encoding utf8
        $body029=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Export-Persistence024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract029')
        & ([scriptblock]::Create($body029)) -RuntimeRoot $runtime029 -CohortJson $cohort029 -OutputRoot $OutputRoot
    }
}
