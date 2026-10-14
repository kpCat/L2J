$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$java043=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'")
$protected043=@(@{pid=18688;ticks='639274376704922975';role='LoginServer'},@{pid=30312;ticks='639274376723876308';role='GameServer'})
$protectedRuntime043='d41f9ba822d51f3e95487a6d94b55bd85d7e62df4522ecfb7abc64f1e575429d'
foreach($entry043 in $protected043){
 $process043=Get-Process -Id $entry043.pid -ErrorAction Stop
 $actual043=@($java043 | Where-Object ProcessId -eq $entry043.pid)
 if($process043.StartTime.ToUniversalTime().Ticks.ToString() -cne $entry043.ticks -or $actual043.Count -ne 1 -or -not $actual043[0].CommandLine.Contains("-Dphantom.localplay.runtime=$protectedRuntime043")){throw 'Protected040a incarnation differs.'}
}
$runtime043=Join-Path $module043 '.phantom-local/contract043a/runtime'
$manifest043=Get-Content (Join-Path $runtime043 'local-play.json') -Raw | ConvertFrom-Json
$pidRecords043=@(foreach($role043 in @('LoginServer','GameServer')){Get-Content (Join-Path $runtime043 "local-play/pids/$role043.json") -Raw | ConvertFrom-Json})
if($pidRecords043[0].runtime -cne $pidRecords043[1].runtime){throw 'Same exact own runtime required.'}
$ownRuntime043=$pidRecords043[0].runtime
$own043=@($java043 | Where-Object {$_.CommandLine -match 'contract043|ops043|PHANTOM-M1-SOFT-HANDOFF-043' -or $_.CommandLine.Contains('-Dphantom.localplay.runtime='+$ownRuntime043)})
if($own043.Count){throw 'Owned043 JVM still running; only stock shutdown permitted.'}
$preserved043=@(Import-Csv (Join-Path $module043 '.phantom-local/contract043a/original-preservation.tsv') -Delimiter "`t")
foreach($row043 in $preserved043){
 $source043=Join-Path (Join-Path $module043 '.phantom-local/contract036b/runtime') $row043.path
 if((Get-FileHash -LiteralPath $source043).Hash -cne $row043.sha256){throw 'Immutable source preservation mismatch.'}
}
$b2raw043=Join-Path $module043 'docs/phantoms/tasks/PHANTOM-M1-NATIVE-LIFE-042/evidence/LIFE_B2_RUNNER_FIXED042/full-native/full-cohort-samples.jsonl'
if((Get-FileHash $b2raw043).Hash.ToLowerInvariant() -cne '52cf75a556fc979ce1681d488cdfd79f83a5d1d17c3ebbd45aa55c12906d6727'){throw 'Immutable B2 bytes changed.'}
@{ownJvmCount=0;protected040a=$protected043;protectedRuntime=$protectedRuntime043;protectedIncarnationsExact=$true;namespace=@{database=$manifest043.databaseName;runtime=$ownRuntime043;jarSha256=$manifest043.gameJarSha256};immutableSourceFiles=$preserved043.Count;immutableSourcePreserved=$true;B2RawPreserved=$true;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $PSScriptRoot 'evidence/FINAL_OWNERSHIP043.json') -Encoding utf8
'FINAL_OWNERSHIP043 PASS: own JVM0, protected040a exact, original304 source files and B2 raw preserved.'
