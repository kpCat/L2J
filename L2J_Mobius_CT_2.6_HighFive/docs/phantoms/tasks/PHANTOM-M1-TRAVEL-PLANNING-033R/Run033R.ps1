[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Initialize','Prepare','ResumePrepare','Build','Geometry','Control','Observe','Verify')][string]$Action,
      [ValidateSet('a','b','c','t')][string]$Episode='a', [hashtable]$Arguments=@{})
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
if($root -notmatch '[\\/]\.codex[\\/]worktrees[\\/]m1-travel-033r(?:-[0-9]+)?[\\/]L2J_Mobius$'){throw 'Exact own TASK033R worktree required.'}
$old=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-CONTINUITY-CLOSEOUT-032'
$ops=Join-Path $module '.phantom-local/ops033r'
$reuse=Join-Path $ops 'reuse'
$retained='C:/Users/ZBook/.codex/worktrees/m1-continuity-032/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/contract032a/runtime'
$retainedOps='C:/Users/ZBook/.codex/worktrees/m1-continuity-032/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops032'
$utf8=[Text.UTF8Encoding]::new($false)
function Quote-Literal033R([string]$Value){return "'"+$Value.Replace("'","''")+"'"}
function Convert-Reused033R([string]$Name){
    $source=Join-Path $old $Name
    $text=[IO.File]::ReadAllText($source)
    if($Name -ceq 'Prepare032.ps1'){
        $text=[regex]::Replace($text,'(?m)^\$original=.*$',('$original='+ (Quote-Literal033R $retained)))
        $text=$text.Replace('l2jmobiush5_localplay_contract031a','RETAINED_SOURCE033R')
        $text=[regex]::Replace($text,'(?m)^\$oldOps=.*$','$oldOps=''RETAINED_OPS033R''')
        $text=$text.Replace(".Replace('contract027','contract032')",".Replace('RETAINED_LANE033R','travel033r')")
    }
    $text=$text.Replace('contract032','travel033r').Replace('TASK032_CONTRACT','TASK033R_CONTRACT').Replace('ops032','ops033r')
    $text=$text.Replace('PHANTOM-M1-CONTINUITY-CLOSEOUT-032/evidence','PHANTOM-M1-TRAVEL-PLANNING-033R/evidence')
    $text=$text.Replace('RETAINED_SOURCE033R','l2jmobiush5_localplay_contract032a')
    $text=$text.Replace('RETAINED_OPS033R',$retainedOps).Replace('RETAINED_LANE033R','contract032')
    if($Name -ceq 'Prepare032.ps1'){
        $text=$text.Replace("'C:/Users/ZBook/.codex/worktrees/m1-continuity-032/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/travel033ra/runtime'",(Quote-Literal033R $retained))
    }
    if($Name.EndsWith('.ps1')){
        $text=$text.Replace('`$PSScriptRoot','RUNTIME_SCRIPT_ROOT033R')
        foreach($peer in @('Control032.ps1','Read-SharedJson032.ps1','Contract032Observer.java','ReadDryRoute032.java')){
            $expression="Join-Path `$PSScriptRoot '$peer'"
            $text=$text.Replace($expression,(Quote-Literal033R (Join-Path $reuse $peer)))
        }
        $text=$text.Replace('$PSScriptRoot',(Quote-Literal033R $PSScriptRoot))
        $text=$text.Replace('RUNTIME_SCRIPT_ROOT033R','`$PSScriptRoot')
    }
    [IO.File]::WriteAllText((Join-Path $reuse $Name),$text,$utf8)
    return [pscustomobject]@{source=$source;sourceSha256=(Get-FileHash $source).Hash;derived=(Join-Path $reuse $Name);derivedSha256=(Get-FileHash (Join-Path $reuse $Name)).Hash}
}
if($Action -ceq 'Initialize'){
    if(Test-Path -LiteralPath $reuse){
        if(-not $Arguments.ContainsKey('Refresh') -or -not $Arguments.Refresh){throw 'Derived inventory exists; explicit Refresh required.'}
        Copy-Item -LiteralPath (Join-Path $ops 'reuse-inventory.json') -Destination (Join-Path $ops ('reuse-inventory-before-'+[guid]::NewGuid().ToString('N')+'.json'))
    }
    New-Item -ItemType Directory -Path $reuse -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'evidence') -Force | Out-Null
    $inventory=@(foreach($name in @('Prepare032.ps1','Control032.ps1','Observe032.ps1','Verify032.ps1','Read-SharedJson032.ps1','Contract032Observer.java','ReadDryRoute032.java')){Convert-Reused033R $name})
    $inventory | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $ops 'reuse-inventory.json') -Encoding utf8
    'TASK033R_REUSE_READY helpers='+$inventory.Count
    return
}
$inventory=@(Get-Content -LiteralPath (Join-Path $ops 'reuse-inventory.json') -Raw | ConvertFrom-Json)
foreach($row in $inventory){
    if((Get-FileHash $row.source).Hash -cne $row.sourceSha256 -or (Get-FileHash $row.derived).Hash -cne $row.derivedSha256){throw 'Reused helper source/derived identity changed.'}
}
if($Arguments.ContainsKey('Mode') -and $Arguments.Mode -in @('CrashNative','CrashFinalize')){throw 'TASK033R does not authorize planned crashes.'}
if($Arguments.ContainsKey('SetupTeleport') -or $Arguments.ContainsKey('InitialObservationPoint')){throw 'TASK033R observation uses pre-publication native setup only.'}
switch($Action){
    'Prepare' { & (Join-Path $reuse 'Prepare032.ps1') -Episode $Episode }
    'ResumePrepare' {
        $private=Join-Path $module ".phantom-local/travel033r$Episode"
        $runtime=Join-Path $private 'runtime'; $db="l2jmobiush5_localplay_travel033r$Episode"
        $snapshot=Join-Path $private 'play-snapshot.sql'
        $manifest=Get-Content (Join-Path $runtime 'local-play.json') -Raw | ConvertFrom-Json
        if($manifest.databaseName -cne $db -or (Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash -cne $manifest.gameJarSha256 -or -not (Test-Path $snapshot)){throw 'Exact completed export/runtime required; no database retry.'}
        if(Test-Path (Join-Path $runtime 'Stop-LocalPlay.ps1')){throw 'Preparation already has its terminal stop helper.'}
        $text=[IO.File]::ReadAllText((Join-Path $reuse 'Prepare032.ps1'))
        $marker='$ops=Join-Path $module ''.phantom-local/ops033r'''
        $offset=$text.IndexOf($marker)
        if($offset -lt 0 -or $text.IndexOf($marker,$offset+1) -ge 0){throw 'Existing preparation tail shape changed.'}
        . ([scriptblock]::Create($text.Substring($offset)))
    }
    'Build' {
        $env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
        & 'C:/Tools/apache-ant-1.10.17/bin/ant.bat' -f (Join-Path $module 'build.xml') jar compile-tests
        if($LASTEXITCODE -ne 0){throw 'TASK033R build failed.'}
    }
    'Geometry' {
        $label=[string]$Arguments.Label
        if($label -notmatch '^[A-Za-z0-9_-]+$'){throw 'Exact geometry evidence label required.'}
        $jdk='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
        $cp="$root/build/phantom-test/bin;$root/build/phantom-test/resources;$module/dist/libs/*"
        & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp $cp -d (Join-Path $root 'build/phantom-test/bin') (Join-Path $module 'test/java/org/l2jmobius/tests/phantoms/PhantomTravelPlanning033RSuite.java')
        if($LASTEXITCODE -ne 0){throw 'Geometry suite compile failed; INVALID, not semantic RED.'}
        Push-Location (Join-Path $module ".phantom-local/travel033r$Episode/runtime/game")
        try{ & (Join-Path $jdk 'java.exe') -Xmx4g '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' '-Dsun.stderr.encoding=UTF-8' -cp $cp org.l2jmobius.tests.phantoms.PhantomTravelPlanning033RSuite $module (Join-Path $PSScriptRoot "evidence/$label") }
        finally{Pop-Location}
    }
    'Control' { & (Join-Path $reuse 'Control032.ps1') -Episode $Episode @Arguments }
    'Observe' { & (Join-Path $reuse 'Observe032.ps1') -Episode $Episode @Arguments }
    'Verify' { & (Join-Path $reuse 'Verify032.ps1') @Arguments }
}
