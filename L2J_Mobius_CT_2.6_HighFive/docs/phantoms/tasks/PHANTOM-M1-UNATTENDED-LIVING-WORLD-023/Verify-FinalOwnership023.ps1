[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$states=@(foreach($episode in @('a','b','c')){
    $private=Join-Path $module ".phantom-local/night023$episode"
    $runtime=Join-Path $private 'runtime'
    . (Join-Path $runtime 'LocalPlay-Pilot.ps1')
    $runtimeId=Get-LocalPlayRuntimeId $runtime
    $processes=@(Get-LocalPlayProcesses $runtimeId)
    if($processes.Count){throw "Task-owned live JVM remains: night023$episode"}
    $manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
    if((Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256){throw 'Retained JAR identity differs.'}
    foreach($role in @('GameServer','LoginServer')){
        $ports=if($role -ceq 'GameServer'){@([int](Get-PilotIniValue (Join-Path $runtime 'game/config/Server.ini') 'GameserverPort'))}else{@([int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginPort'),[int](Get-PilotIniValue (Join-Path $runtime 'login/config/Server.ini') 'LoginserverPort'))}
        [pscustomobject]@{episode=$episode;role=$role;runtimeId=$runtimeId;codeSha=$manifest.codeSha;jarSha256=$manifest.gameJarSha256;ownedProcessCount=$processes.Count;roleState=(Get-LocalPlayRoleState $runtime $role "$role.jar" $ports)}
    }
})
$states | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $PSScriptRoot 'evidence/FINAL_OWNED_PROCESS_STATES.json') -Encoding utf8
$original='C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'
$expected=Import-Csv (Join-Path $module '.phantom-local/night023b/original-preservation.tsv') -Delimiter "`t"
$changed=@(foreach($row in $expected){if((Get-FileHash (Join-Path $original $row.path)).Hash -cne $row.sha256){$row.path}})
if($changed.Count){throw "Original runtime files changed: $($changed -join ',')"}
@('TASK_OWNED_JVMS=0','RETENTION=night023a,b,c runtime and databases; no deletion',"ORIGINAL_RUNTIME_PRESERVED=$($expected.Count) files",'EMERGENCY_FORCE=0','PLANNED_CRASH=1 dedicated exact c GameServer only','PLAY_WRITES=0; all runtime JDBC targets own clone; source PLAY export read-only') | Set-Content (Join-Path $PSScriptRoot 'evidence/FINAL_OWNERSHIP_GUARD.txt') -Encoding utf8
Get-Content (Join-Path $PSScriptRoot 'evidence/FINAL_OWNERSHIP_GUARD.txt')
