$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$config043=Join-Path $module043 '.phantom-local/contract043a/runtime/game/java.cfg'
$before043=(Get-FileHash $config043).Hash
$rejected043=$false
try { & (Join-Path $module043 '.phantom-local/ops043/Control032.ps1') -Action Start -Episode a -ExpectedSha ('0'*40) }
catch { if($_.Exception.Message -cne 'Owned source/DB mismatch.'){throw}; $rejected043=$true }
if(-not $rejected043 -or (Get-FileHash $config043).Hash -cne $before043 -or (Test-Path (Join-Path $module043 '.phantom-local/contract043a/runtime/local-play/pids/GameServer.json'))){throw 'Wrong-source guard did not reject before start mutation.'}
@{case='WrongSource';expectedFailure='Owned source/DB mismatch.';pass=$true;beforeRuntimeStart=$true;configPreserved=$true;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'evidence/NORMAL_GUARD043.json') -Encoding utf8
'NORMAL_GUARD043 PASS: wrong source rejected before config/JVM mutation.'
