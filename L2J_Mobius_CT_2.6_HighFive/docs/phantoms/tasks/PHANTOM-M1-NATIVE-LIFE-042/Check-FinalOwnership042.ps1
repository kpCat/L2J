param()
$ErrorActionPreference='Stop'
$module042=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$java042=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'")
$protected042=@(
    @{pid=18688;ticks='639274376704922975';role='LoginServer'},
    @{pid=30312;ticks='639274376723876308';role='GameServer'})
$protectedRuntime042='d41f9ba822d51f3e95487a6d94b55bd85d7e62df4522ecfb7abc64f1e575429d'
foreach($entry042 in $protected042){
    $process042=Get-Process -Id $entry042.pid -ErrorAction Stop
    $actual042=@($java042 | Where-Object ProcessId -eq $entry042.pid)
    if($process042.StartTime.ToUniversalTime().Ticks.ToString() -cne $entry042.ticks -or $actual042.Count -ne 1 -or
       -not $actual042[0].CommandLine.Contains("-Dphantom.localplay.runtime=$protectedRuntime042")){throw 'Protected040a incarnation differs; no mutation.'}
}
$namespaces042=@(foreach($episode042 in @('a','b')){
    $manifest042=Get-Content (Join-Path $module042 ".phantom-local/contract042$episode042/runtime/local-play.json") -Raw | ConvertFrom-Json
    $pidRecords042=@(foreach($role042 in @('LoginServer','GameServer')){
        Get-Content (Join-Path $module042 ".phantom-local/contract042$episode042/runtime/local-play/pids/$role042.json") -Raw | ConvertFrom-Json
    })
    if($pidRecords042[0].runtime -cne $pidRecords042[1].runtime){throw 'Same exact own runtime required.'}
    [pscustomobject]@{episode=$episode042;database=$manifest042.databaseName;codeSha=$manifest042.codeSha;jarSha256=$manifest042.gameJarSha256;runtime=$pidRecords042[0].runtime}
})
$own042=@($java042 | Where-Object {
    $command042=$_.CommandLine
    $command042 -match 'contract042|phantom\.contract042\.manifest|ops042' -or
    @($namespaces042 | Where-Object {$command042.Contains('-Dphantom.localplay.runtime='+$_.runtime)}).Count -gt 0
})
if($own042.Count){throw 'Owned042 JVM still running; only stock shutdown permitted.'}
$preserved042=@(foreach($episode042 in @('a','b')){
    $rows042=@(Import-Csv (Join-Path $module042 ".phantom-local/contract042$episode042/original-preservation.tsv") -Delimiter "`t")
    foreach($row042 in $rows042){
        $source042=Join-Path (Join-Path $module042 '.phantom-local/contract036b/runtime') $row042.path
        if((Get-FileHash -LiteralPath $source042).Hash -cne $row042.sha256){throw 'Immutable clone-source preservation mismatch.'}
    }
    [pscustomobject]@{episode=$episode042;sourceFiles=$rows042.Count;preserved=$true}
})
[pscustomobject]@{ownJvmCount=0;protected040a=$protected042;protectedRuntime=$protectedRuntime042;protectedIncarnationsExact=$true;
    namespaces=$namespaces042;immutableSourcePreserved=$preserved042;utc=[DateTime]::UtcNow.ToString('o')} |
    ConvertTo-Json -Depth 6 | Set-Content (Join-Path $PSScriptRoot 'evidence/FINAL_OWNERSHIP042.json') -Encoding utf8
'FINAL_OWNERSHIP042 own JVM0; protected040a exact; immutable source hashes preserved.'
