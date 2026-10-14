param([ValidateSet('WrongManifest','MixedOwners')][string]$Case)
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$lane043=Join-Path $module043 '.phantom-local/contract043a'
$ops043=Join-Path $module043 '.phantom-local/ops043'
$manifest043=Join-Path $lane043 'test/owned.properties'
$report043=Join-Path $PSScriptRoot ('evidence/GUARD_'+$Case+'043')
$log043=$report043+'.log'
if(Test-Path $log043){throw 'Immutable negative log exists.'}
$selected043=$manifest043
if($Case -ceq 'WrongManifest'){
    $selected043=Join-Path $ops043 'wrong-path-owned.properties'
    if(Test-Path $selected043){throw 'Immutable negative fixture exists.'}
    Copy-Item -LiteralPath $manifest043 -Destination $selected043
}
$arguments043=@('-Xmx2g','-Dfile.encoding=UTF-8',"-Dphantom.contract043.manifest=$selected043",
    "-Dphantom.test.config=$lane043/test/Database.test.ini","-Dphantom.module.root=$module043",
    '-cp',"$module043/dist/libs/GameServer.jar;$ops043/build/phantom-test/bin;$ops043/build/phantom-test/resources;$module043/dist/libs/*")
if($Case -ceq 'MixedOwners'){$arguments043+="-Dphantom.contract041.manifest=$manifest043"}
$arguments043+=@('org.l2jmobius.tests.phantoms.PhantomLifecycleCompletion027Suite',$module043,$report043)
Push-Location (Join-Path $lane043 'runtime/game')
try{$ErrorActionPreference='Continue'; & java @arguments043 *> $log043; $negativeExit043=$LASTEXITCODE}
finally{Pop-Location; $ErrorActionPreference='Stop'}
$expected043=if($Case -ceq 'WrongManifest'){'Exact owned TASK026 manifest required.'}else{'Only one exact owned lane may be selected.'}
if($negativeExit043 -eq 0 -or -not (Select-String -LiteralPath $log043 -SimpleMatch $expected043 -Quiet)){throw 'Negative did not reject at the exact authority boundary.'}
[pscustomobject]@{case=$Case;expectedFailure=$expected043;processExit=$negativeExit043;pass=$true;beforeDatabaseInit=$true} |
    ConvertTo-Json | Set-Content ($report043+'.json') -Encoding utf8
Write-Output "GUARD_PASS case=$Case expectedExit=$negativeExit043 beforeDatabaseInit=true"
