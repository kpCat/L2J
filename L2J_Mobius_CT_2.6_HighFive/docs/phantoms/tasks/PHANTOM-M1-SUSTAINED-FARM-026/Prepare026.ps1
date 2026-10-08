[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',[ValidateSet('c','d','h')][string]$Source='c',[switch]$FromRetained)
$ErrorActionPreference='Stop'
$module026=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$task026=$PSScriptRoot
$oldModule026='C:/Users/ZBook/.codex/worktrees/m1-checkpoint-025/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive'
$source026=Join-Path $oldModule026 ".phantom-local/contract025$Source/runtime"
$sourceDb026="l2jmobiush5_localplay_contract025$Source"
if($FromRetained){
    if($Episode -cne 'h'){throw 'Retained source export is bounded to own026h.'}
    $source026='C:/Users/ZBook/.codex/worktrees/m1-contracts-024/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/contract024c/runtime'
    $sourceDb026='l2jmobiush5_localplay_contract024c'
}
$template026=Join-Path $module026 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Prepare-Runtime024.ps1'
$body026=[IO.File]::ReadAllText($template026).Replace('$PSScriptRoot','$task026').Replace('contract024','contract026')
$body026=$body026.Replace("'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'",("'"+$source026+"'"))
$body026=$body026.Replace('l2jmobiush5_localplay3',$sourceDb026)
$body026=$body026.Replace("'Stop-LocalPlay024.ps1'","'../PHANTOM-M1-RUNTIME-CONTRACTS-024/Stop-LocalPlay024.ps1'")
& ([scriptblock]::Create($body026)) -Episode $Episode
$private026=Join-Path $module026 ".phantom-local/contract026$Episode"
$runtime026=Join-Path $private026 'runtime'
$ops026=Join-Path $module026 '.phantom-local/ops026'
New-Item -ItemType Directory -Path $ops026 -Force | Out-Null
foreach($name026 in @('RequestGracefulShutdown.class','graceful-shutdown.jar','graceful-login-shutdown.jar')){
    $old026=Join-Path $oldModule026 ".phantom-local/ops025/$name026"
    $new026=Join-Path $ops026 $name026
    Copy-Item -LiteralPath $old026 -Destination $new026 -Force
    if((Get-FileHash $old026).Hash -cne (Get-FileHash $new026).Hash){throw 'Exact stock graceful helper mismatch.'}
}
$stop026=[IO.File]::ReadAllText((Join-Path $oldModule026 '.phantom-local/ops025/Stop-ExactOwnedGracefully.ps1')).Replace('contract025','contract026').Replace('ops025','ops026')
[IO.File]::WriteAllText((Join-Path $ops026 'Stop-ExactOwnedGracefully.ps1'),$stop026,[Text.UTF8Encoding]::new($false))
$runtimeStop026="param(); & (Join-Path `$PSScriptRoot '../../ops026/Stop-ExactOwnedGracefully.ps1') -RuntimeRoot `$PSScriptRoot`n"
[IO.File]::WriteAllText((Join-Path $runtime026 'Stop-LocalPlay.ps1'),$runtimeStop026,[Text.UTF8Encoding]::new($false))
if($Episode -ceq 'a'){
    $test026=Join-Path $private026 'test'; New-Item -ItemType Directory -Path $test026 | Out-Null
    $config026=[IO.File]::ReadAllText((Join-Path $runtime026 'game/config/Database.ini'))
    $config026=[regex]::Replace($config026,'(?m)^(MaximumDatabaseConnections\s*=\s*)[^\r\n]*','${1}4')
    [IO.File]::WriteAllText((Join-Path $test026 'Database.test.ini'),$config026,[Text.UTF8Encoding]::new($false))
    $ownership026="owner=TASK026_CONTRACT`ndatabase=l2jmobiush5_localplay_contract026a`nexportSha256=$((Get-FileHash (Join-Path $private026 'play-snapshot.sql')).Hash.ToLowerInvariant())`n"
    [IO.File]::WriteAllText((Join-Path $test026 'owned.properties'),$ownership026,[Text.UTF8Encoding]::new($false))
}
"TASK026_PREPARED episode=$Episode source=$sourceDb026"
