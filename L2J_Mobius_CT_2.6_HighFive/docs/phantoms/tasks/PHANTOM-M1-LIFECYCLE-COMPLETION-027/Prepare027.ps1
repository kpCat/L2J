[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a', [ValidateSet('c','d','h')][string]$Source='c', [switch]$ResumeMissingBuild)
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$oldModule027='C:/Users/ZBook/.codex/worktrees/m1-sustained-026/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive'
$source027=Join-Path $oldModule027 ".phantom-local/contract026$Source/runtime"
$sourceDb027="l2jmobiush5_localplay_contract026$Source"
$template027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Prepare-Runtime024.ps1'
$body027=[IO.File]::ReadAllText($template027).Replace('$PSScriptRoot','$task027').Replace('contract024','contract027')
$body027=$body027.Replace("'C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'",("'"+$source027+"'"))
$body027=$body027.Replace('l2jmobiush5_localplay3',$sourceDb027)
$body027=$body027.Replace("'Stop-LocalPlay024.ps1'","'../PHANTOM-M1-RUNTIME-CONTRACTS-024/Stop-LocalPlay024.ps1'")
if($ResumeMissingBuild){
    if($Episode -cne 'a'){throw 'Only incomplete own TEST a setup may resume; no product reset.'}
    $private=Join-Path $module027 '.phantom-local/contract027a'
    $runtime=Join-Path $private 'runtime'
    if(Test-Path (Join-Path $runtime 'local-play.json')){throw 'Completed setup cannot resume.'}
    if(Test-Path (Join-Path $private 'test/owned.properties')){throw 'Existing fixture cannot resume setup.'}
    . (Join-Path $module027 'tools/phantom-local-play/LocalPlay-Pilot.ps1')
    $config=Join-Path $runtime 'game/config/Database.ini'
    if((Get-PilotIniValue $config 'URL') -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay_contract027a\?'){throw 'Exact incomplete own TEST DB guard failed.'}
    foreach($entry in (Import-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t")){
        if((Get-FileHash (Join-Path $source027 $entry.path)).Hash -cne $entry.sha256){throw 'Read-only source changed during setup.'}
    }
    foreach($jar027 in @('GameServer.jar','LoginServer.jar')){Copy-Item -LiteralPath (Join-Path $module027 "dist/libs/$jar027") -Destination (Join-Path $runtime "libs/$jar027")}
    $module=$module027; $db='l2jmobiush5_localplay_contract027a'; $original=$source027
    $preserved=Import-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t"
    $tail027=$body027.Substring($body027.IndexOf('$old=Get-Content'))
    & ([scriptblock]::Create($tail027))
}else{ & ([scriptblock]::Create($body027)) -Episode $Episode }
$private027=Join-Path $module027 ".phantom-local/contract027$Episode"
$runtime027=Join-Path $private027 'runtime'
$ops027=Join-Path $module027 '.phantom-local/ops027'
New-Item -ItemType Directory -Path $ops027 -Force | Out-Null
foreach($name027 in @('RequestGracefulShutdown.class','graceful-shutdown.jar','graceful-login-shutdown.jar')){
    $old027=Join-Path $oldModule027 ".phantom-local/ops026/$name027"
    $new027=Join-Path $ops027 $name027
    Copy-Item -LiteralPath $old027 -Destination $new027 -Force
    if((Get-FileHash $old027).Hash -cne (Get-FileHash $new027).Hash){throw 'Exact stock graceful helper mismatch.'}
}
$stop027=[IO.File]::ReadAllText((Join-Path $oldModule027 '.phantom-local/ops026/Stop-ExactOwnedGracefully.ps1')).Replace('contract026','contract027').Replace('ops026','ops027')
[IO.File]::WriteAllText((Join-Path $ops027 'Stop-ExactOwnedGracefully.ps1'),$stop027,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $runtime027 'Stop-LocalPlay.ps1'),"param(); & (Join-Path `$PSScriptRoot '../../ops027/Stop-ExactOwnedGracefully.ps1') -RuntimeRoot `$PSScriptRoot`n",[Text.UTF8Encoding]::new($false))
if($Episode -ceq 'a'){
    $test027=Join-Path $private027 'test'; New-Item -ItemType Directory -Path $test027 | Out-Null
    $config027=[IO.File]::ReadAllText((Join-Path $runtime027 'game/config/Database.ini'))
    $config027=[regex]::Replace($config027,'(?m)^(MaximumDatabaseConnections\s*=\s*)[^\r\n]*','${1}4')
    [IO.File]::WriteAllText((Join-Path $test027 'Database.test.ini'),$config027,[Text.UTF8Encoding]::new($false))
    $ownership027="owner=TASK027_CONTRACT`ndatabase=l2jmobiush5_localplay_contract027a`nexportSha256=$((Get-FileHash (Join-Path $private027 'play-snapshot.sql')).Hash.ToLowerInvariant())`n"
    [IO.File]::WriteAllText((Join-Path $test027 'owned.properties'),$ownership027,[Text.UTF8Encoding]::new($false))
}
"TASK027_PREPARED episode=$Episode source=$sourceDb027"
