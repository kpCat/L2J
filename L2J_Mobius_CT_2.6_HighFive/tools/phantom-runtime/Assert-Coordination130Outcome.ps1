[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$RuntimeRoot,
    [Parameter(Mandatory=$true)][ValidatePattern('^(native|restart)-[0-9]{8}-[0-9]{6}$')][string]$Attempt,
    [Parameter(Mandatory=$true)][ValidateSet('original','morning')][string]$Fixture,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$JarHash
)
$ErrorActionPreference='Stop'
$phase=$Attempt.Split('-')[0]
$prefix=Join-Path $RuntimeRoot $Attempt
$scene=if($Fixture -eq 'morning'){Join-Path $RuntimeRoot 'morning'}else{$RuntimeRoot}
$process=Get-Content -LiteralPath ($prefix+'.process.json') -Raw | ConvertFrom-Json
$exited=Get-Content -LiteralPath ($prefix+'.exit.json') -Raw | ConvertFrom-Json
if($process.pid -le 0 -or $exited.pid -ne $process.pid -or $process.phase -cne $phase -or $exited.phase -cne $phase -or $process.jar -ine $JarHash){
    throw "TEST130_ATTEMPT_IDENTITY_MISMATCH attempt=$Attempt"
}
if($null -eq $exited.exitCode -or $exited.exitCode -ne 0){
    throw "TEST130_STOCK_STOP_EXIT attempt=$Attempt code=$($exited.exitCode)"
}
$phaseMarker=$phase+'130'
$failure=Select-String -LiteralPath ($prefix+'.stdout.log') -Pattern '^NATIVE130_FAILURE ' | Select-Object -Last 1
if($failure -or (Test-Path -LiteralPath (Join-Path $scene ($phaseMarker+'.failed.'+$Attempt+'.'+$process.pid)))){
    throw "TEST130_VERTICAL_FAILED attempt=$Attempt pid=$($process.pid) $($failure.Line)"
}
$complete=Join-Path $scene ($phaseMarker+'.complete')
if(!(Test-Path -LiteralPath $complete -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $scene 'native130.persisted') -PathType Leaf)){
    throw "TEST130_USEFUL_VERTICAL_OR_STORE_MISSING attempt=$Attempt"
}
$receipt=Get-Content -LiteralPath $complete
if($receipt -cnotcontains ('pid='+$process.pid) -or $receipt -inotcontains ('jar='+$JarHash) -or $receipt -cnotcontains ('attempt='+$Attempt)){
    throw "TEST130_COMPLETION_IDENTITY_MISMATCH attempt=$Attempt"
}
$stdout=$prefix+'.stdout.log'
$stderr=$prefix+'.stderr.log'
$pass='NATIVE130_PASS restart=false sameGroup=true actualPartyKill=true STORE=true'
if($phase -eq 'restart'){
    $prior=Get-Content -LiteralPath (Join-Path $scene 'native130.complete')
    $group=@($receipt -cmatch '^group=[A-F0-9]{64}$')
    $generation=@($receipt -cmatch '^generation=[1-9][0-9]*$')
    $profiles=@($receipt -cmatch '^profiles=([1-9][0-9]*,){1,8}[1-9][0-9]*$')
    if($group.Count -ne 1 -or $generation.Count -ne 1 -or $profiles.Count -ne 1 -or $prior -cnotcontains $group[0] -or $prior -cnotcontains $profiles[0] -or $prior -inotcontains ('jar='+$JarHash)){
        throw "TEST130_RESTART_AGREEMENT_RECEIPT_MISMATCH attempt=$Attempt"
    }
    $agreement=[regex]::Escape($group[0]+' '+$generation[0])
    $roster='leader=[1-9][0-9]* roster=\[([1-9][0-9]*, ){1,8}[1-9][0-9]*\]'
    $target='target=Target\[objectId=[1-9][0-9]*, instanceId=[0-9]+, spawnGeneration=[1-9][0-9]*\]'
    $damage="^NATIVE130_RESTART_USEFUL_ACTION kind=DAMAGE $agreement $roster actor=[1-9][0-9]* epoch=[1-9][0-9]* $target source=\S+ npc=[1-9][0-9]* hpBefore=\S+ hpAfter=\S+ damageSequenceBefore=[0-9]+ damageSequenceAfter=[1-9][0-9]* stockDamageBefore=[0-9]+ stockDamageAfter=[1-9][0-9]* nativeRewardDelta=0$"
    $kill="^NATIVE130_RESTART_USEFUL_ACTION kind=KILL $agreement $roster $target$"
    $claims=$profiles[0].Split(',').Length
    if(!(Select-String -LiteralPath $stdout -CaseSensitive -Pattern $damage,$kill -Quiet) -or
        !(Select-String -LiteralPath $stdout -CaseSensitive -Pattern "^NATIVE130_RESTART_STORE_PASS $agreement claims=$claims$" -Quiet)){
        throw "TEST130_RESTART_ACTION_OR_STORE_MISSING attempt=$Attempt"
    }
    $pass='NATIVE130_PASS restart=true sameGroup=true usefulPartyAction=true STORE=true'
}
if(!(Select-String -LiteralPath ($prefix+'.stdout.log') -CaseSensitive -Pattern ('^'+[regex]::Escape($pass)+'$') -Quiet)){
    throw "TEST130_USEFUL_VERTICAL_PASS_MISSING attempt=$Attempt"
}
$typedStop='NATIVE130_DISABLE=OperatorControlResult[code=DISABLED, desiredMode=DISABLED, desiredRuntimeEnabled=false, runtimeConfigured=false, runtimeState=null]'
if(!(Select-String -LiteralPath $stdout -CaseSensitive -Pattern ('^'+[regex]::Escape($typedStop)+'$') -Quiet) -or
    (Select-String -LiteralPath $stderr -CaseSensitive -Pattern 'Phantom World stop failed:', 'Phantom World: Initial subsystem drain remains incomplete; systemState=FAILED', 'Phantom World: Final subsystem drain (is incomplete|failed)' -Quiet)){
    throw "TEST130_PHANTOM_SUBSYSTEM_STOP_FAILED attempt=$Attempt"
}
$stockStop=Select-String -LiteralPath $stderr -SimpleMatch -CaseSensitive -Pattern 'The server has been successfully shut down in ' -Quiet
$poolClosed=Select-String -LiteralPath $stderr -SimpleMatch -CaseSensitive -Pattern 'Database: HikariCP pool closed successfully.' -Quiet
$stockSave=Select-String -LiteralPath $stderr -SimpleMatch -CaseSensitive -Pattern 'All players disconnected and saved(' -Quiet
if(!$stockStop -or !$poolClosed -or !$stockSave){
    throw "TEST130_STOCK_STOP_EVIDENCE_MISSING attempt=$Attempt"
}
Write-Output "TEST130_USEFUL_VERTICAL_AND_STOCK_STOP_PASS attempt=$Attempt pid=$($process.pid)"
