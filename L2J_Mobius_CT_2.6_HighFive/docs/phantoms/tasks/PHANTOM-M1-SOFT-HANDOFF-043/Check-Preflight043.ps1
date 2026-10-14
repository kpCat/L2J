param()
$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if($module043 -cne 'C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'){throw 'Exact OWN required.'}
foreach($path043 in @($module043,(Join-Path $module043 '.phantom-local'))){
 if((Get-Item -LiteralPath $path043).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'No junction authority.'}
}
$base043='9a378d0e8984b3673b41538a9f5f9c9823507317'
$head043=(& git -C (Split-Path $module043 -Parent) rev-parse HEAD).Trim()
$remote043=(& git -C (Split-Path $module043 -Parent) ls-remote origin refs/heads/experiment/m1-candidate007-observe008).Split("`t")[0]
if($head043 -cne $base043 -or $remote043 -cne $base043){throw 'Exact BASE/remote required before scene.'}
if(& git -C (Split-Path $module043 -Parent) status --short --untracked-files=no){throw 'Foreign tracked/index changes; no write.'}
$java043=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'")
$protected043=@(@{pid=18688;ticks='639274376704922975'},@{pid=30312;ticks='639274376723876308'})
foreach($entry043 in $protected043){
 $process043=Get-Process -Id $entry043.pid -ErrorAction Stop
 $record043=@($java043 | Where-Object ProcessId -eq $entry043.pid)
 if($process043.StartTime.ToUniversalTime().Ticks.ToString() -cne $entry043.ticks -or $record043.Count -ne 1 -or -not $record043[0].CommandLine.Contains('-Dphantom.localplay.runtime=d41f9ba822d51f3e95487a6d94b55bd85d7e62df4522ecfb7abc64f1e575429d')){throw 'Protected040a incarnation changed.'}
}
if(@($java043 | Where-Object {$_.CommandLine -match 'contract043|ops043'}).Count){throw 'Existing043 JVM; no create.'}
if(Test-Path -LiteralPath (Join-Path $module043 '.phantom-local/contract043a')){throw 'Existing043a namespace; no reset.'}
if(@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in @(7795,2125,9035)}).Count){throw 'Own new ports occupied; no listener changed.'}
$pins043=@(foreach($relative043 in @('dist/libs/GameServer.jar','docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py','.phantom-local/contract036b/play-snapshot.sql')){
 $file043=Join-Path $module043 $relative043
 @{path=$file043;sha256=(Get-FileHash -LiteralPath $file043).Hash}
})
@{head=$head043;remote=$remote043;protected040a=$protected043;protectedIncarnationsExact=$true;newNamespaceAbsent=$true;newPortsFree=@(7795,2125,9035);pins=$pins043;utc=[DateTime]::UtcNow.ToString('O')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'evidence/PREFLIGHT043.json') -Encoding utf8
'PREFLIGHT043 PASS: exact BASE/remote, tracked clean, protected040a unchanged, namespace absent, own ports free.'
