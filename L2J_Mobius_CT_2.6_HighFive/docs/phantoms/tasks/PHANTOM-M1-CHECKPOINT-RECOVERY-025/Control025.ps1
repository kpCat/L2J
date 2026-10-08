[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export')][string]$Action,
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@(),[switch]$DumpDuringStop)
$ErrorActionPreference='Stop'
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$taskRoot024=Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024'
$runtime025=Join-Path $module025 ".phantom-local/contract025$Episode/runtime"
switch($Action){
    'Update'{
        $body025=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Update-OwnedRuntime024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract025')
        & ([scriptblock]::Create($body025)) -Episode $Episode -Revision $Revision
    }
    'Start'{
        if($ExpectedSha -notmatch '^[0-9a-f]{40}$'){throw 'Exact committed code SHA required.'}
        $manifest025=Get-Content (Join-Path $runtime025 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest025.codeSha -cne $ExpectedSha -or $manifest025.databaseName -cne "l2jmobiush5_localplay_contract025$Episode"){throw 'Owned source/DB mismatch.'}
        foreach($role025 in @('GameServer','LoginServer')){
            $expected025=if($role025 -ceq 'GameServer'){$manifest025.gameJarSha256}else{$manifest025.loginJarSha256}
            if((Get-FileHash (Join-Path $runtime025 "libs/$role025.jar")).Hash -cne $expected025){throw 'Owned JAR hash mismatch.'}
        }
        & (Join-Path $runtime025 'Start-LocalPlay.ps1') -Background -GameTimeoutSeconds 180
    }
    'Stop'{
        $dumpJob025=$null
        if($DumpDuringStop){
            $dumpRoot025=[IO.Path]::GetFullPath($OutputRoot)
            $taskEvidence025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))+[IO.Path]::DirectorySeparatorChar
            if(-not $dumpRoot025.StartsWith($taskEvidence025,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path $dumpRoot025)){throw 'Immutable task stop-dump output required.'}
            . (Join-Path $runtime025 'LocalPlay-Ownership.ps1')
            $gameState025=Get-LocalPlayRoleState $runtime025 'GameServer' 'GameServer.jar' @(7777)
            if($gameState025.state -cne 'RUNNING' -or !$gameState025.recordVerified){throw 'Exact running owned GameServer required for stop dumps.'}
            New-Item -ItemType Directory -Path $dumpRoot025 | Out-Null
            $gameState025 | ConvertTo-Json | Set-Content (Join-Path $dumpRoot025 'exact-process.json') -Encoding utf8
            $dumpJob025=Start-Job -ArgumentList $gameState025.pid,$gameState025.startTimeUtcTicks,$dumpRoot025 -ScriptBlock {
                param($Game025,$Ticks025,$Output025)
                for($i025=0;$i025 -lt 4;$i025++){
                    Start-Sleep -Seconds 1
                    $process025=Get-Process -Id $Game025 -ErrorAction SilentlyContinue
                    if(!$process025){"OWNED_GAME_EXITED beforeDump=$i025";break}
                    if($process025.StartTime.ToUniversalTime().Ticks -ne [long]$Ticks025){throw 'Owned dump incarnation changed.'}
                    & 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin/jcmd.exe' $Game025 Thread.print -l *> (Join-Path $Output025 "threads-$i025.txt")
                    "DUMP=$i025 EXIT=$LASTEXITCODE UTC=$([DateTime]::UtcNow.ToString('O'))"
                }
            }
        }
        try{ & (Join-Path $runtime025 'Stop-LocalPlay.ps1') }
        finally{
            if($dumpJob025){$dumpJob025 | Wait-Job -Timeout 30 | Out-Null; Receive-Job $dumpJob025 *> (Join-Path $dumpRoot025 'dump-control.log'); Remove-Job $dumpJob025 -Force}
        }
    }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort025=Join-Path (Split-Path $runtime025 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort025 -Encoding utf8
        $body025=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Export-Persistence024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract025')
        & ([scriptblock]::Create($body025)) -RuntimeRoot $runtime025 -CohortJson $cohort025 -OutputRoot $OutputRoot
    }
}
