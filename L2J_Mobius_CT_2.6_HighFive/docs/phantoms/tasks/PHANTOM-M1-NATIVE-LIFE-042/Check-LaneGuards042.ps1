param([ValidateSet('WrongManifest','MixedOwners')][string]$Case)
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$lane042=Join-Path $module042 '.phantom-local/contract042b'
$ops042=Join-Path $module042 '.phantom-local/ops042'
$manifest042=Join-Path $lane042 'test/owned.properties'
$report042=Join-Path $PSScriptRoot ('evidence/GUARD_'+$Case+'042')
$log042=$report042+'.log'
if(Test-Path $log042){throw 'Immutable negative log exists.'}
$selected042=$manifest042
if($Case -ceq 'WrongManifest'){
    $selected042=Join-Path $ops042 'wrong-path-owned.properties'
    if(Test-Path $selected042){throw 'Immutable negative fixture exists.'}
    Copy-Item -LiteralPath $manifest042 -Destination $selected042
}
$arguments042=@('-Xmx2g','-Dfile.encoding=UTF-8',"-Dphantom.contract042.manifest=$selected042",
    "-Dphantom.test.config=$lane042/test/Database.test.ini","-Dphantom.module.root=$module042",
    '-cp',"$module042/dist/libs/GameServer.jar;$ops042/build/phantom-test/bin;$ops042/build/phantom-test/resources;$module042/dist/libs/*")
if($Case -ceq 'MixedOwners'){$arguments042+="-Dphantom.contract041.manifest=$manifest042"}
$arguments042+=@('org.l2jmobius.tests.phantoms.PhantomLifecycleCompletion027Suite',$module042,$report042)
Push-Location (Join-Path $lane042 'runtime/game')
try{$ErrorActionPreference='Continue'; & java @arguments042 *> $log042; $negativeExit042=$LASTEXITCODE}
finally{Pop-Location; $ErrorActionPreference='Stop'}
$expected042=if($Case -ceq 'WrongManifest'){'Exact owned TASK026 manifest required.'}else{'Only one exact owned lane may be selected.'}
if($negativeExit042 -eq 0 -or -not (Select-String -LiteralPath $log042 -SimpleMatch $expected042 -Quiet)){throw 'Negative did not reject at the exact authority boundary.'}
[pscustomobject]@{case=$Case;expectedFailure=$expected042;processExit=$negativeExit042;pass=$true;beforeDatabaseInit=$true} |
    ConvertTo-Json | Set-Content ($report042+'.json') -Encoding utf8
Write-Output "GUARD_PASS case=$Case expectedExit=$negativeExit042 beforeDatabaseInit=true"
