[CmdletBinding()]
param([ValidateSet('Update','Start','Stop','Export','Check','Collector')][string]$Action='Collector',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',[string]$OutputRoot='',
      [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Observe','FullObserve','Census','Recovery','Flush','CrashNative','CrashFinalize')][string]$Mode='Build',
      [string]$CohortJson='', [string]$Suite='PhantomCheckpointRecovery025Suite',
      [string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [string]$Label='', [string]$LauncherId='', [switch]$ResetFixture)
$ErrorActionPreference='Stop'
$taskRoot026=$PSScriptRoot
$module026=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$oldTask026=Join-Path $module026 'docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025'
$name026=switch($Action){'Collector'{'Collector025.ps1'} 'Check'{'Checks025.ps1'} default{'Control025.ps1'}}
$body026=[IO.File]::ReadAllText((Join-Path $oldTask026 $name026))
$body026=$body026.Replace('$PSScriptRoot','$taskRoot026').Replace('contract025','contract026').Replace('ops025','ops026').Replace('TASK025_CONTRACT','TASK026_CONTRACT')
$body026=$body026.Replace(".Replace('`$taskRoot026',", ".Replace('`$PSScriptRoot',")
if($Action -ceq 'Check'){
    $body026=$body026.Replace('contract026b','contract026a').Replace('phantom.contract026.manifest','phantom.contract026.manifest')
    $body026=$body026.Replace('contract025b','contract026a')
    $body026=$body026.Replace(".Replace('contract024a','contract026b')",".Replace('contract024a','contract026a')")
    $body026=$body026.Replace(".Replace('phantom.contract024.manifest','phantom.contract026.manifest')",".Replace('phantom.contract024.manifest','phantom.contract026.manifest')")
    $body026=$body026.Replace('07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6','6ebe1d93f4ee168cd8952f0416dc920f26c430ac')
    & ([scriptblock]::Create($body026)) -Suite $Suite -Focus $Focus -Engine $Engine -Label $Label -LauncherId $LauncherId -ResetFixture:$ResetFixture
}elseif($Action -ceq 'Collector'){
    $body026=$body026.Replace('Contract025Observer','Contract026Observer').Replace('observer025','observer026')
    $observerSource026=Join-Path $taskRoot026 'Contract026Observer.java'
    $observerHash026=(Get-FileHash $observerSource026).Hash
    $agentClass026='Contract026Observer'+$observerHash026.Substring(0,12)
    $agentJar026='observer026-'+$observerHash026.Substring(0,12)+'.jar'
    if($Mode -ceq 'Build'){
        $ops026=Join-Path $module026 '.phantom-local/ops026'
        $classes026=Join-Path $ops026 'observer-classes'
        New-Item -ItemType Directory -Path $classes026 -Force | Out-Null
        $generated026=Join-Path $ops026 ($agentClass026+'.java')
        [IO.File]::WriteAllText($generated026,[IO.File]::ReadAllText($observerSource026).Replace('Contract026Observer',$agentClass026),[Text.UTF8Encoding]::new($false))
        $attach026=Join-Path $module026 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/AttachContract024.java'
        $jdk026='C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin'
        & (Join-Path $jdk026 'javac.exe') -encoding UTF-8 -cp (Join-Path $module026 ".phantom-local/contract026$Episode/runtime/libs/*") -d $classes026 $generated026 $attach026
        if($LASTEXITCODE -ne 0){throw 'Versioned collector compile failed.'}
        $mf026=Join-Path $ops026 'observer026.mf'
        [IO.File]::WriteAllText($mf026,"Manifest-Version: 1.0`nAgent-Class: $agentClass026`n`n",[Text.UTF8Encoding]::new($false))
        & (Join-Path $jdk026 'jar.exe') --create --file (Join-Path $ops026 $agentJar026) --manifest $mf026 -C $classes026 .
        if($LASTEXITCODE -ne 0){throw 'Versioned collector JAR build failed.'}
        [ordered]@{sourceSha256=$observerHash026;jarSha256=(Get-FileHash (Join-Path $ops026 $agentJar026)).Hash;attachSourceSha256=(Get-FileHash $attach026).Hash;agentClass=$agentClass026} | ConvertTo-Json | Set-Content (Join-Path $ops026 'observer026-build.json') -Encoding utf8
        return
    }
    $body026=$body026.Replace('observer026.jar',$agentJar026)
    $body026=$body026.Replace("'Build','Observe','Flush','CrashNative','CrashFinalize'", "'Build','Observe','FullObserve','Census','Recovery','Flush','CrashNative','CrashFinalize'")
    $body026=$body026.Replace("'Flush'{'FLUSH'}", "'FullObserve'{'FULL_OBSERVE'} 'Census'{'CENSUS'} 'Recovery'{'RECOVERY'} 'Flush'{'FLUSH'}")
    $body026=$body026.Replace("if(`$Mode -ceq 'Observe' -or `$crash025)", "if(`$Mode -in @('Observe','FullObserve','Census','Recovery') -or `$crash025)")
    $body026=$body026.Replace('[long]$row025.materializedAtNanos -le 0', "(`$Mode -notin @('FullObserve','Census','Recovery') -and [long]`$row025.materializedAtNanos -le 0)")
    if($Mode -ceq 'Recovery'){
        # Read-only early attach: exact marked PID/start/JAR/DB still mandatory; no foreign owner may bind the game port.
        $body026=$body026.Replace("`$state025=Get-LocalPlayRoleState `$runtime025 'GameServer' 'GameServer.jar' @(7777)",
            "`$startup026=Get-LocalPlayRoleState `$runtime025 'GameServer' 'GameServer.jar' @(7777); `$state025=Get-LocalPlayRoleState `$runtime025 'GameServer' 'GameServer.jar' @(); `$portOwners026=Get-LocalPlayPortOwners @(7777); if(`$portOwners026.ContainsKey(7777) -and [int]`$portOwners026[7777] -ne [int]`$state025.pid){throw 'Recovery attach found a foreign game port owner.'}; Write-Output ('TASK026_RECOVERY_ATTACH phase='+`$startup026.state+' exactMarkedIncarnation=true')")
    }
    & ([scriptblock]::Create($body026)) -Mode $Mode -Episode $Episode -CohortJson $CohortJson -OutputRoot $OutputRoot
}else{
    & ([scriptblock]::Create($body026)) -Action $Action -Episode $Episode -Revision $Revision -ExpectedSha $ExpectedSha -OutputRoot $OutputRoot -ProfileIds $ProfileIds -DumpDuringStop:$DumpDuringStop
}
