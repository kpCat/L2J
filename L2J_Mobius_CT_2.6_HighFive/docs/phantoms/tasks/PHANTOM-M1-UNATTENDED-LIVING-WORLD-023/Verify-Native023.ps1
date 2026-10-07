[CmdletBinding()]
param([ValidateSet('regression','farm','recovery','store')][string]$Group='regression')
$ErrorActionPreference='Stop'
$routes=switch($Group){
    'regression' {@(
        @{suite='PhantomLivingWorld023Suite';focus='';label='FINAL_BOUNDARIES'},
        @{suite='native';focus='dynamic';label='FINAL_DYNAMIC'},
        @{suite='native';focus='secondary';label='FINAL_SECONDARY'},
        @{suite='native';focus='closure';label='FINAL_CLOSURE'},
        @{suite='native';focus='timers';label='FINAL_TIMERS'},
        @{suite='native';focus='native-phase';label='FINAL_NATIVE_PHASE'})}
    'farm' {@(
        @{suite='PhantomNativeFarmContinuation022Suite';focus='mage';label='FINAL_MAGE_ACTIVE'},
        @{suite='PhantomNativeFarmContinuation022Suite';focus='ordinary';label='FINAL_ORDINARY_ACTIVE'},
        @{suite='PhantomNativeFarmContinuation022Suite';focus='';label='FINAL_FARM_AGGREGATE'})}
    'recovery' {@(
        @{suite='PhantomVisibleIntentRecoverySuite';focus='';label='FINAL_CURRENT_INTENT'},
        @{suite='PhantomLocalFarmRecoverySuite';focus='';label='FINAL_LOCAL_RECOVERY'})}
    'store' {@(
        @{suite='native';focus='store';label='FINAL_OWNED_STORE'},
        @{suite='native';focus='process-crash';label='FINAL_OWNED_STORE_CRASH'})}
}
$summary=[Collections.Generic.List[object]]::new()
foreach($route in $routes){
    & (Join-Path $PSScriptRoot 'Run-Checks023.ps1') -Suite $route.suite -Focus $route.focus -Label $route.label
    $summary.Add([pscustomobject]@{label=$route.label;exitCode=$LASTEXITCODE})
    # Each guarded JVM is complete before the next starts. Preserve all failed reports.
}
$summary | Export-Csv (Join-Path $PSScriptRoot "VERIFY-$Group.tsv") -Delimiter "`t" -NoTypeInformation -Encoding utf8
$summary | Format-Table -AutoSize
if(@($summary | Where-Object {$_.exitCode -ne 0}).Count){exit 1}
