param([Parameter(Mandatory)][ValidateSet('x72','x73')][string]$Episode,[Parameter(Mandatory)][string]$Label)
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime041=Join-Path $module041 ".phantom-local/contract041$Episode/runtime"
$output041=Join-Path $module041 "docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/evidence/$Label"
if(Test-Path $output041){throw 'Immutable guard output exists.'}
New-Item -ItemType Directory -Path $output041 | Out-Null
. (Join-Path $runtime041 'LocalPlay-Pilot.ps1')
. (Join-Path $runtime041 'LocalPlay-Ownership.ps1')
$manifest041=Get-Content (Join-Path $runtime041 'local-play.json') -Raw | ConvertFrom-Json
if($manifest041.databaseName -cne "l2jmobiush5_phantom_test_041$Episode"){throw 'Exact own crash schema required.'}
$state041=Get-LocalPlayRoleState $runtime041 'GameServer' 'GameServer.jar' @([int](Get-PilotIniValue (Join-Path $runtime041 'game/config/Server.ini') 'GameserverPort'))
if($state041.state -cne 'RUNNING' -or !$state041.recordVerified){throw 'Exact own crash JVM required.'}
$build041=Get-Content (Join-Path $PSScriptRoot 'observer032-build.json') -Raw | ConvertFrom-Json
$agent041=Join-Path $PSScriptRoot ('observer032-'+$build041.sourceSha256.Substring(0,12)+'.jar')
if((Get-FileHash $agent041).Hash -cne $build041.jarSha256){throw 'Collector pin differs.'}
$stderr041=(Get-ChildItem (Join-Path $runtime041 'local-play/logs') -Filter 'GameServer*stderr.log' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$mode041=if($Episode -ceq 'x72'){'CRASH_NATIVE'}else{'CRASH_FINALIZE'}
$cases041=@(
 @{name='wrongRoot';marker='TASK032_RUNTIME_GUARD'},
 @{name='wrongDeclaredPid';marker='TASK032_PROCESS_GUARD'},
 @{name='wrongDeclaredSchema';marker='TASK041_CRASH_SCHEMA_GUARD'},
 @{name='wrongEpoch';marker='TASK032_CRASH_CURRENT_LIFETIME_REQUIRED'}
)
$results041=@()
function Read-SharedGuardLog041([string]$Path){
 $stream041=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
 $reader041=[IO.StreamReader]::new($stream041,[Text.Encoding]::UTF8,$true)
 try{return $reader041.ReadToEnd()}finally{$reader041.Dispose()}
}
foreach($case041 in $cases041){
 $declaredRoot041=if($case041.name -ceq 'wrongRoot'){Join-Path $module041 '.phantom-local/contract041a/runtime'}else{$runtime041}
 $declaredPid041=if($case041.name -ceq 'wrongDeclaredPid'){[long]$state041.pid+1}else{$state041.pid}
 $declaredSchema041=if($case041.name -ceq 'wrongDeclaredSchema'){'l2jmobiush5_phantom_test_041a'}else{$manifest041.databaseName}
 $spec041=Join-Path (Split-Path $runtime041 -Parent) ('negative-'+[guid]::NewGuid().ToString('N')+'.properties')
 $fields041=[ordered]@{runtime=$declaredRoot041.Replace('\','/');output=$output041.Replace('\','/');owner='TASK041_CONTRACT';pid=$declaredPid041;startTicks=$state041.startTimeUtcTicks;codeSha=$manifest041.codeSha;database=$declaredSchema041;mode=$mode041;'profile.68'=1}
 $lines041=[Collections.Generic.List[string]]::new()
 foreach($entry041 in $fields041.GetEnumerator()){$lines041.Add($entry041.Key+'='+[string]$entry041.Value)}
 [IO.File]::WriteAllLines($spec041,$lines041,[Text.UTF8Encoding]::new($false))
 $prior041=(Read-SharedGuardLog041 $stderr041).Length
 $saved041=$ErrorActionPreference; $ErrorActionPreference='Continue'
 try {& java --add-modules jdk.attach -cp (Join-Path $PSScriptRoot 'observer-classes') AttachContract024 ([string]$state041.pid) $agent041 $spec041 *> (Join-Path $output041 ($case041.name+'.log')); $exit041=$LASTEXITCODE}
 finally {$ErrorActionPreference=$saved041}
 $new041=(Read-SharedGuardLog041 $stderr041).Substring($prior041)
 if($exit041 -eq 0 -or !$new041.Contains($case041.marker)){throw "Negative guard unconfirmed: $($case041.name)"}
 if((Get-Process -Id $state041.pid).StartTime.ToUniversalTime().Ticks -ne [long]$state041.startTimeUtcTicks){throw 'Own incarnation changed during negative.'}
 $results041+=@{name=$case041.name;pass=$true;exit=$exit041;marker=$case041.marker;actualAttachPid=$state041.pid;declaredPid=$declaredPid041;specSha256=(Get-FileHash $spec041).Hash}
}
@{pass=$true;runtime=$runtime041;database=$manifest041.databaseName;pid=$state041.pid;startTicks=$state041.startTimeUtcTicks;cases=$results041;faultArmed=$false;utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $output041 'result.json') -Encoding utf8
'EXACT_041_CRASH_GUARDS_PASS wrong-root / declared-PID / schema / epoch; actual attach only own JVM'
$global:LASTEXITCODE=0
