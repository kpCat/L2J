[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a')
$ErrorActionPreference='Stop'
$taskRoot025=$PSScriptRoot
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$oldModule025='C:/Users/ZBook/.codex/worktrees/m1-contracts-024/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive'
$source025=if($Episode -ceq 'a'){Join-Path $oldModule025 '.phantom-local/contract024c/runtime'}else{'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'}
$sourceDb025=if($Episode -ceq 'a'){'l2jmobiush5_localplay_contract024c'}else{'l2jmobiush5_localplay3'}
$script025=[IO.File]::ReadAllText((Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Prepare-Runtime024.ps1'))
$script025=$script025.Replace('$PSScriptRoot','$taskRoot025').Replace('contract024','contract025')
$script025=$script025.Replace("'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'",("'"+$source025+"'"))
$script025=$script025.Replace('l2jmobiush5_localplay3',$sourceDb025)
$script025=$script025.Replace("'Stop-LocalPlay024.ps1'","'../PHANTOM-M1-RUNTIME-CONTRACTS-024/Stop-LocalPlay024.ps1'")
& ([scriptblock]::Create($script025)) -Episode $Episode
$private025=Join-Path $module025 ".phantom-local/contract025$Episode"
$runtime025=Join-Path $private025 'runtime'
foreach($entry025 in @(@('game/config/Server.ini','GameserverHostname'),@('login/config/Server.ini','LoginserverHostname'))){
    $path025=Join-Path $runtime025 $entry025[0]
    $text025=[IO.File]::ReadAllText($path025)
    $pattern025='(?m)^('+[regex]::Escape($entry025[1])+'\s*=\s*)[^\r\n]*'
    if([regex]::Matches($text025,$pattern025).Count -ne 1){throw 'Exact loopback config key missing.'}
    [IO.File]::WriteAllText($path025,[regex]::Replace($text025,$pattern025,'${1}127.0.0.1'),[Text.UTF8Encoding]::new($false))
}
$ops025=Join-Path $module025 '.phantom-local/ops025'
New-Item -ItemType Directory -Path $ops025 -Force | Out-Null
foreach($name025 in @('RequestGracefulShutdown.class','graceful-shutdown.jar','graceful-login-shutdown.jar')){
    $old025=Join-Path $oldModule025 ".phantom-local/ops024/$name025"
    $new025=Join-Path $ops025 $name025
    Copy-Item -LiteralPath $old025 -Destination $new025 -Force
    if((Get-FileHash -LiteralPath $old025).Hash -cne (Get-FileHash -LiteralPath $new025).Hash){throw 'Exact stock graceful helper copy mismatch.'}
}
$stop025=[IO.File]::ReadAllText((Join-Path $oldModule025 '.phantom-local/ops024/Stop-ExactOwnedGracefully.ps1')).Replace('contract024','contract025')
[IO.File]::WriteAllText((Join-Path $ops025 'Stop-ExactOwnedGracefully.ps1'),$stop025,[Text.UTF8Encoding]::new($false))
$runtimeStop025="param(); & (Join-Path `$PSScriptRoot '../../ops025/Stop-ExactOwnedGracefully.ps1') -RuntimeRoot `$PSScriptRoot`n"
[IO.File]::WriteAllText((Join-Path $runtime025 'Stop-LocalPlay.ps1'),$runtimeStop025,[Text.UTF8Encoding]::new($false))
if($Episode -ceq 'b'){
    $test025=Join-Path $private025 'test';New-Item -ItemType Directory -Path $test025 | Out-Null
    $dbConfig025=[IO.File]::ReadAllText((Join-Path $runtime025 'game/config/Database.ini'))
    $dbConfig025=[regex]::Replace($dbConfig025,'(?m)^(MaximumDatabaseConnections\s*=\s*)[^\r\n]*','${1}4')
    [IO.File]::WriteAllText((Join-Path $test025 'Database.test.ini'),$dbConfig025,[Text.UTF8Encoding]::new($false))
    $properties025="owner=TASK025_CONTRACT`ndatabase=l2jmobiush5_localplay_contract025b`nexportSha256=$((Get-FileHash (Join-Path $private025 'play-snapshot.sql')).Hash.ToLowerInvariant())`n"
    [IO.File]::WriteAllText((Join-Path $test025 'owned.properties'),$properties025,[Text.UTF8Encoding]::new($false))
}
"TASK025_PREPARED episode=$Episode source=$sourceDb025"
