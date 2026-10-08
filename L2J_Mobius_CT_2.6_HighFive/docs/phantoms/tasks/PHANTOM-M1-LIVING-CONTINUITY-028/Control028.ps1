[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export','Collector')][string]$Action,
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','FullObserve','Census','Flush')][string]$Mode='Build', [string]$CohortJson='', [string]$ObserverRunId='')
$ErrorActionPreference='Stop'
$module028=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module028 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtime028=Join-Path $module028 ".phantom-local/contract028$Episode/runtime"
if($Action -ceq 'Collector'){
    $ops=Join-Path $module028 '.phantom-local/ops028'
    $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
    $source=Join-Path $PSScriptRoot 'Contract028Observer.java'
    $hash=(Get-FileHash $source).Hash
    $agentClass='Contract028Observer'+$hash.Substring(0,12)
    $agentJar=Join-Path $ops ('observer028-'+$hash.Substring(0,12)+'.jar')
    $classes=Join-Path $ops 'observer-classes'
    if($Mode -ceq 'Build'){
        New-Item -ItemType Directory -Path $classes -Force | Out-Null
        $generated=Join-Path $ops ($agentClass+'.java')
        [IO.File]::WriteAllText($generated,[IO.File]::ReadAllText($source).Replace('Contract028Observer',$agentClass),[Text.UTF8Encoding]::new($false))
        $attach=Join-Path $module028 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
        & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp (Join-Path $module028 'dist/libs/*') -d $classes $generated $attach
        if($LASTEXITCODE -ne 0){throw 'Collector compile failed.'}
        $mf=Join-Path $ops 'observer028.mf'
        [IO.File]::WriteAllText($mf,"Manifest-Version: 1.0`nAgent-Class: $agentClass`n`n",[Text.UTF8Encoding]::new($false))
        & (Join-Path $jdk 'jar.exe') --create --file $agentJar --manifest $mf -C $classes .
        if($LASTEXITCODE -ne 0){throw 'Collector JAR failed.'}
        [ordered]@{sourceSha256=$hash;jarSha256=(Get-FileHash $agentJar).Hash;attachSourceSha256=(Get-FileHash $attach).Hash} | ConvertTo-Json | Set-Content (Join-Path $ops 'observer028-build.json') -Encoding utf8
        return
    }
    . (Join-Path $runtime028 'LocalPlay-Pilot.ps1')
    $manifest=Get-Content (Join-Path $runtime028 'local-play.json') -Raw | ConvertFrom-Json
    if($manifest.databaseName -cne "l2jmobiush5_localplay_contract028$Episode" -or (Get-FileHash (Join-Path $runtime028 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Exact DB/JAR guard failed.'}
    $build=Get-Content (Join-Path $ops 'observer028-build.json') -Raw | ConvertFrom-Json
    if($hash -cne $build.sourceSha256 -or (Get-FileHash $agentJar).Hash -cne $build.jarSha256){throw 'Collector hash guard failed.'}
    $state=Get-LocalPlayRoleState $runtime028 'GameServer' 'GameServer.jar' @(7777)
    if($state.state -cne 'RUNNING' -or !$state.recordVerified -or (Get-Process -Id $state.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state.startTimeUtcTicks){throw 'Exact PID/start guard failed.'}
    $output=[IO.Path]::GetFullPath($OutputRoot)
    $allowed=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
    if(-not $output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact task evidence output guard failed.'}
    $lines=[Collections.Generic.List[string]]::new()
    $lines.Add('runtime='+$runtime028.Replace('\','/')); $lines.Add('output='+$output.Replace('\','/'))
    $lines.Add('owner=TASK028_CONTRACT'); $lines.Add('pid='+$state.pid); $lines.Add('startTicks='+$state.startTimeUtcTicks)
    $lines.Add('codeSha='+$manifest.codeSha); $lines.Add('observerRunId='+$ObserverRunId)
    $lines.Add('mode='+$(switch($Mode){'FullObserve'{'FULL_OBSERVE'} 'Census'{'CENSUS'} 'Flush'{'FLUSH'}}))
    if($Mode -ne 'Flush'){
        $rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
        if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows.profileId | Select-Object -Unique).Count -ne $rows.Count){throw 'Exact cohort 1..8 guard.'}
        foreach($row in $rows){if([long]$row.profileId -le 0 -or ($Mode -ceq 'FullObserve' -and [long]$row.materializedAtNanos -le 0)){throw 'Exact profile/epoch required.'};$lines.Add('profile.'+$row.profileId+'='+$row.materializedAtNanos)}
    }
    $spec=Join-Path (Split-Path $runtime028 -Parent) ('observer-'+[guid]::NewGuid().ToString('N')+'.properties')
    [IO.File]::WriteAllLines($spec,$lines,[Text.UTF8Encoding]::new($false))
    & (Join-Path $jdk 'java.exe') --add-modules jdk.attach -cp $classes AttachContract024 $state.pid $agentJar $spec
    if($LASTEXITCODE -ne 0){throw 'Exact collector attach failed.'}
    "TASK028_OBSERVER mode=$Mode"
    return
}
switch($Action){
    'Update'{
        $body028=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Update-OwnedRuntime024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract028')
        & ([scriptblock]::Create($body028)) -Episode $Episode -Revision $Revision
    }
    'Start'{
        if($ExpectedSha -notmatch '^[0-9a-f]{40}$'){throw 'Exact committed code SHA required.'}
        $manifest028=Get-Content (Join-Path $runtime028 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest028.codeSha -cne $ExpectedSha -or $manifest028.databaseName -cne "l2jmobiush5_localplay_contract028$Episode"){throw 'Owned source/DB mismatch.'}
        foreach($role028 in @('GameServer','LoginServer')){
            $expected028=if($role028 -ceq 'GameServer'){$manifest028.gameJarSha256}else{$manifest028.loginJarSha256}
            if((Get-FileHash (Join-Path $runtime028 "libs/$role028.jar")).Hash -cne $expected028){throw 'Owned JAR hash mismatch.'}
        }
        & (Join-Path $runtime028 'Start-LocalPlay.ps1') -Background -GameTimeoutSeconds 180
    }
    'Stop'{
        $dumpJob028=$null
        if($DumpDuringStop){
            $dumpRoot028=[IO.Path]::GetFullPath($OutputRoot)
            $taskEvidence028=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
            if(-not $dumpRoot028.StartsWith($taskEvidence028,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $dumpRoot028)){throw 'Immutable task stop-dump output required.'}
            . (Join-Path $runtime028 'LocalPlay-Ownership.ps1')
            $gameState028=Get-LocalPlayRoleState $runtime028 'GameServer' 'GameServer.jar' @(7777)
            if($gameState028.state -cne 'RUNNING' -or !$gameState028.recordVerified){throw 'Exact running owned GameServer required for stop dumps.'}
            New-Item -ItemType Directory -Path $dumpRoot028 | Out-Null
            $gameState028 | ConvertTo-Json | Set-Content (Join-Path $dumpRoot028 'exact-process.json') -Encoding utf8
            $dumpJob028=Start-Job -ArgumentList $gameState028.pid,$gameState028.startTimeUtcTicks,$dumpRoot028 -ScriptBlock {
                param($Game028,$Ticks028,$Output028)
                for($i028=0;$i028 -lt 4;$i028++){
                    Start-Sleep -Seconds 1
                    $process028=Get-Process -Id $Game028 -ErrorAction SilentlyContinue
                    if(!$process028){"OWNED_GAME_EXITED beforeDump=$i028";break}
                    if($process028.StartTime.ToUniversalTime().Ticks -ne [long]$Ticks028){throw 'Owned dump incarnation changed.'}
                    & 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $Game028 Thread.print -l *> (Join-Path $Output028 "threads-$i028.txt")
                    "DUMP=$i028 EXIT=$LASTEXITCODE UTC=$([DateTime]::UtcNow.ToString('O'))"
                }
            }
        }
        try{ & (Join-Path $runtime028 'Stop-LocalPlay.ps1') }
        finally{
            if($dumpJob028){$dumpJob028 | Wait-Job -Timeout 30 | Out-Null; Receive-Job $dumpJob028 *> (Join-Path $dumpRoot028 'dump-control.log'); Remove-Job $dumpJob028 -Force}
        }
    }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort028=Join-Path (Split-Path $runtime028 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort028 -Encoding utf8
        $body028=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Export-Persistence024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract028')
        & ([scriptblock]::Create($body028)) -RuntimeRoot $runtime028 -CohortJson $cohort028 -OutputRoot $OutputRoot
    }
}
