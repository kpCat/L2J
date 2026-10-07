[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Update','Start','Stop','Export')][string]$Action,
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',
      [string]$OutputRoot='', [long[]]$ProfileIds=@())
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
    'Stop'{ & (Join-Path $runtime025 'Stop-LocalPlay.ps1') }
    'Export'{
        if(-not $OutputRoot -or $ProfileIds.Count -lt 1){throw 'Output path and exact selected profiles required.'}
        $cohort025=Join-Path (Split-Path $runtime025 -Parent) 'export-cohort.json'
        @($ProfileIds | ForEach-Object {[pscustomobject]@{profileId=$_}}) | ConvertTo-Json | Set-Content -LiteralPath $cohort025 -Encoding utf8
        $body025=[IO.File]::ReadAllText((Join-Path $taskRoot024 'Export-Persistence024.ps1')).Replace('$PSScriptRoot','$taskRoot024').Replace('contract024','contract025')
        & ([scriptblock]::Create($body025)) -RuntimeRoot $runtime025 -CohortJson $cohort025 -OutputRoot $OutputRoot
    }
}
