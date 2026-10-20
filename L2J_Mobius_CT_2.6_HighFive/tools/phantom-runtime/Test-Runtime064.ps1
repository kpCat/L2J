[CmdletBinding()]
param([Parameter(Mandatory)][string]$ScratchRoot,[Parameter(Mandatory)][string]$JarRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(Test-Path -LiteralPath $ScratchRoot){throw 'Test evidence exists; no overwrite.'}
$module064=[IO.Path]::GetFullPath((Join-Path $ScratchRoot 'module'))
$root064=Join-Path $module064 '.phantom-local/task064/runtime'
foreach($folder064 in @('game/config','login/config','libs')){New-Item -ItemType Directory -Path (Join-Path $root064 $folder064) -Force | Out-Null}
foreach($kind064 in @('GameServer','LoginServer')){Copy-Item -LiteralPath (Join-Path $JarRoot "$kind064.jar") -Destination (Join-Path $root064 "libs/$kind064.jar")}
$game064=(Get-FileHash -LiteralPath (Join-Path $root064 'libs/GameServer.jar')).Hash
$login064=(Get-FileHash -LiteralPath (Join-Path $root064 'libs/LoginServer.jar')).Hash
$encoding064=[Text.UTF8Encoding]::new($false)
function Write064([string]$Relative,[string]$Value){[IO.File]::WriteAllText((Join-Path $root064 $Relative),$Value,$encoding064)}
$owner064="version=1`nowner=TASK064_CAMPAIGN`nrole=MUTABLE_FIXTURE`nschema=l2jmobiush5_phantom_test_064`nruntimeRoot=$($root064.Replace('\','/'))`nsourceModuleRoot=$($module064.Replace('\','/'))`nclientLoginPort=2244`ngamePort=7944`ncontrolPort=9244`ngameJarSha256=$game064`nloginJarSha256=$login064`n"
Write064 'ownership064.properties' $owner064
Write064 'runtime.local.ini' "DatabaseName=l2jmobiush5_phantom_test_064`n"
foreach($kind064 in @('game','login')){Write064 "$kind064/config/Database.ini" "URL=jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_064?useSSL=false`n"}
Write064 'game/config/Server.ini' "GameserverPort=7944`nLoginPort=9244`n"
Write064 'login/config/Server.ini' "LoginserverPort=2244`nLoginPort=9244`n"
$manifest064=[ordered]@{format=1;codeSha=('a'*40);databaseName='l2jmobiush5_phantom_test_064';databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$game064;loginJarSha256=$login064;clientLoginPort=2244;gamePort=7944;controlPort=9244}
Write064 'local-play.json' ($manifest064 | ConvertTo-Json)
$manifest064.Remove('codeSha');$manifest064.codeCommit='a'*40
Write064 'BUILD_INFO.json' ($manifest064 | ConvertTo-Json)
$runner064=Join-Path $PSScriptRoot 'Runtime064.ps1'
& $runner064 -Action Validate -RuntimeRoot $root064
Write-Output 'PASS runtime064.exact-config-jars-no-process'
function Reject064{
    $rejected064=$false
    try{& $runner064 -Action Validate -RuntimeRoot $root064 | Out-Null}catch{$rejected064=$true}
    if(!$rejected064){throw 'Negative runtime binding was accepted.'}
}
foreach($replacement064 in @(@('TASK064_CAMPAIGN','TASK061_CAMPAIGN'),@('schema=l2jmobiush5_phantom_test_064','schema=l2jmobiush5_phantom_release_061a'),@("runtimeRoot=$($root064.Replace('\','/'))","runtimeRoot=$($module064.Replace('\','/'))"))){
    Write064 'ownership064.properties' ($owner064.Replace($replacement064[0],$replacement064[1]));Reject064
}
Write064 'ownership064.properties' $owner064
Write-Output 'PASS runtime064.wrong-owner-schema-root'
Write064 'login/config/Server.ini' "LoginserverPort=2138`nLoginPort=9244`n";Reject064
Write064 'login/config/Server.ini' "LoginserverPort=2244`nLoginPort=9244`n"
Write064 'game/config/Database.ini' "URL=jdbc:mysql://127.0.0.1:3308/l2jmobiush5?useSSL=false`n";Reject064
Write064 'game/config/Database.ini' "URL=jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_064?useSSL=false`n"
$jar064=Join-Path $root064 'libs/GameServer.jar'
$stream064=[IO.File]::Open($jar064,[IO.FileMode]::Append);try{$stream064.WriteByte(0)}finally{$stream064.Dispose()};Reject064
Write-Output 'PASS runtime064.foreign-port-jdbc-jar'
