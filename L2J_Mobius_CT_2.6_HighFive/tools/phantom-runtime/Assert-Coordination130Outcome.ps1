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
$restart=($phase -eq 'restart').ToString().ToLowerInvariant()
$pass='NATIVE130_PASS restart='+$restart+' sameGroup=true actualPartyKill=true STORE=true'
if(!(Select-String -LiteralPath ($prefix+'.stdout.log') -CaseSensitive -Pattern ('^'+[regex]::Escape($pass)+'$') -Quiet)){
    throw "TEST130_USEFUL_VERTICAL_PASS_MISSING attempt=$Attempt"
}
$stockStop=Select-String -LiteralPath ($prefix+'.stderr.log') -SimpleMatch -CaseSensitive -Pattern 'The server has been successfully shut down in ' -Quiet
$poolClosed=Select-String -LiteralPath ($prefix+'.stderr.log') -SimpleMatch -CaseSensitive -Pattern 'Database: HikariCP pool closed successfully.' -Quiet
$stockSave=Select-String -LiteralPath ($prefix+'.stderr.log') -SimpleMatch -CaseSensitive -Pattern 'All players disconnected and saved(' -Quiet
if(!$stockStop -or !$poolClosed -or !$stockSave){
    throw "TEST130_STOCK_STOP_EVIDENCE_MISSING attempt=$Attempt"
}
Write-Output "TEST130_USEFUL_VERTICAL_AND_STOCK_STOP_PASS attempt=$Attempt pid=$($process.pid)"
