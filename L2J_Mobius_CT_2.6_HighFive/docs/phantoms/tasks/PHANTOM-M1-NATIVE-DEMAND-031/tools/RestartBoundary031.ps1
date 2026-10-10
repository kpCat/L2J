[CmdletBinding()]
param([ValidateSet('a','b')][string]$Episode='a', [Parameter(Mandatory)][string]$CohortJson,
      [Parameter(Mandatory)][string]$OutputRoot, [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$FrozenSha)
$ErrorActionPreference='Stop'
$task=Split-Path $PSScriptRoot -Parent
$module=[IO.Path]::GetFullPath((Join-Path $task '../../../..'))
$runtime=Join-Path $module ".phantom-local/contract031$Episode/runtime"
$allowed=[IO.Path]::GetFullPath((Join-Path $task 'evidence'))+[IO.Path]::DirectorySeparatorChar
foreach($path in @($CohortJson,$OutputRoot)){if(-not [IO.Path]::GetFullPath($path).StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Exact own TASK031 evidence paths required'}}
if(Test-Path $OutputRoot){throw 'Immutable startup evidence exists'}
$rows=@(Get-Content $CohortJson -Raw | ConvertFrom-Json)
if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows.profileId | Select-Object -Unique).Count -ne $rows.Count){throw 'Exact whole cohort1..8 required'}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
$jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
$classes=Join-Path $module '.phantom-local/ops031/test-classes'
& "$jdk/javac.exe" --add-modules jdk.jdi -d $classes (Join-Path $PSScriptRoot 'TraceGuard031.java') *> (Join-Path $OutputRoot 'jdi-compile.log')
if($LASTEXITCODE){throw 'Exact startup JDI helper compile failed'}
$control=Join-Path $task 'Control031.ps1'
$job=Start-Job -ArgumentList $runtime,$OutputRoot,$CohortJson,$control,$jdk,$classes,$Episode -ScriptBlock {
    param($Runtime,$Output,$Cohort,$Control,$Jdk,$Classes,$Episode)
    $ErrorActionPreference='Stop'; $helper=$null; $boundary=Join-Path $Output 'boundary'
    try{
        $deadline=[DateTime]::UtcNow.AddSeconds(180); $owned=$false
        while([DateTime]::UtcNow -lt $deadline){
            $recordPath=Join-Path $Runtime 'local-play/pids/GameServer.json'
            if(Test-Path $recordPath){
                $record=Get-Content $recordPath -Raw | ConvertFrom-Json
                $process=Get-Process -Id ([int]$record.pid) -ErrorAction SilentlyContinue
                $listeners=@(Get-NetTCPConnection -LocalPort 5031 -State Listen -ErrorAction SilentlyContinue)
                if($process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$record.startTimeUtcTicks -and $listeners.Count -eq 1 -and $listeners[0].OwningProcess -eq [int]$record.pid){$owned=$true; break}
            }
            Start-Sleep -Milliseconds 200
        }
        if(-not $owned){throw 'Own startup JDWP incarnation not available'}
        $record | ConvertTo-Json | Set-Content (Join-Path $Output 'owned-debug-incarnation.json') -Encoding utf8
        $helper=Start-Process -FilePath "$Jdk/java.exe" -ArgumentList @('--add-modules','jdk.jdi','-cp',$Classes,'TraceGuard031','5031',(Join-Path $Output 'startup-jdi.txt'),'startup',$boundary) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Output 'jdi-console.txt') -RedirectStandardError (Join-Path $Output 'jdi-errors.txt')
        while([DateTime]::UtcNow -lt $deadline -and -not (Test-Path (Join-Path $boundary 'paused.properties'))){
            if($helper.HasExited){throw 'Startup JDI exited before quiet boundary'}
            Start-Sleep -Milliseconds 25
        }
        if(-not (Test-Path (Join-Path $boundary 'paused.properties'))){throw 'Exact quiet startup boundary not observed'}
        $watch=[Diagnostics.Stopwatch]::StartNew()
        $ids=@((Get-Content $Cohort -Raw | ConvertFrom-Json).profileId | ForEach-Object {[long]$_})
        & $Control -Action Export -Episode $Episode -ProfileIds $ids -OutputRoot (Join-Path $Output 'quiet-sql') *> (Join-Path $Output 'quiet-sql-export.log')
        if(-not $?){throw 'External quiet SQL export failed'}
        if($watch.Elapsed.TotalSeconds -ge 7){throw 'Quiet SQL export exceeded diagnostic8s boundary'}
        [IO.File]::WriteAllText((Join-Path $boundary 'release.signal'),'EXTERNAL_EXPORT_COMPLETE',[Text.UTF8Encoding]::new($false))
        if(-not $helper.WaitForExit(10000) -or $helper.ExitCode -ne 0){throw 'Startup JDI did not confirm timely resume'}
        $trace=[IO.File]::ReadAllText((Join-Path $Output 'startup-jdi.txt'))
        if(-not $trace.Contains('TARGET_THREAD_RESUMED=true') -or -not $trace.Contains('externalExportReleased=true')){throw 'Actual diagnostic resume proof missing'}
        [ordered]@{owner='TASK031_CONTRACT';gamePid=$record.pid;startTicks=$record.startTimeUtcTicks;quietExportSeconds=$watch.Elapsed.TotalSeconds;prePopulationStart=$true;gameplayTimingProof=$false;jdiSourceSha256=(Get-FileHash (Join-Path (Split-Path $Control -Parent) 'tools/TraceGuard031.java')).Hash;jdiTraceSha256=(Get-FileHash (Join-Path $Output 'startup-jdi.txt')).Hash} | ConvertTo-Json | Set-Content (Join-Path $Output 'startup-boundary-verified.json') -Encoding utf8
    }finally{
        if(Test-Path (Join-Path $boundary 'paused.properties')){[IO.File]::WriteAllText((Join-Path $boundary 'release.signal'),'DIAGNOSTIC_FINALLY_RESUME',[Text.UTF8Encoding]::new($false))}
        if($helper -and -not $helper.HasExited -and -not $helper.WaitForExit(5000)){Stop-Process -Id $helper.Id -ErrorAction SilentlyContinue; 'OWN_DEBUG_HELPER_TERMINATED_NOT_A_GAMESERVER_STOP'}
    }
}
try{
    & $control -Action Start -Episode $Episode -ExpectedSha $FrozenSha *> (Join-Path $OutputRoot 'stock-start.log')
    if(-not $?){throw 'Stock own server startup failed'}
    $job | Wait-Job -Timeout 10 | Out-Null
    Receive-Job $job *> (Join-Path $OutputRoot 'boundary-control.log')
    if($job.State -cne 'Completed' -or -not (Test-Path (Join-Path $OutputRoot 'startup-boundary-verified.json'))){throw 'Own startup boundary/export not verified'}
    'TASK031_STOCK_START_WITH_QUIET_BOUNDARY_PASS'
}finally{
    if($job.State -in @('Running','NotStarted')){Stop-Job $job}
    Remove-Job $job -Force
}
