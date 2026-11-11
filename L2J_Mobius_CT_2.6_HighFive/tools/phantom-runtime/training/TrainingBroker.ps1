# Local ordinary-user background launch through documented WMI, not a job-limit change.
# https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/create-method-in-class-win32-process
function Test-TrainBrokerAdmission($QuerySucceeded,$InAnyJob,$CurrentJob) {
    if($QuerySucceeded -isnot [bool] -or !$QuerySucceeded -or $InAnyJob -isnot [bool]){return $false}
    if(!$InAnyJob){return $true}
    if($null -eq $CurrentJob){return $false}
    foreach($name in @('LimitsSucceeded','KillOnJobClose','BreakawayOk','SilentBreakawayOk')){
        if(!$CurrentJob.PSObject.Properties[$name] -or $CurrentJob.$name -isnot [bool]){return $false}
    }
    # NULL QueryInformationJobObject reports the immediate job only. The owned
    # kill-on-close fixture separately proves survival of the caller's job close.
    return $CurrentJob.LimitsSucceeded -and !$CurrentJob.KillOnJobClose -and $CurrentJob.BreakawayOk -and $CurrentJob.SilentBreakawayOk
}
function Assert-TrainBrokerProof($Layout,$Record) {
    Assert-TrainInstallation $Layout
    $proof=Read-TrainJson (Join-Path $Layout.Root ('logs\bootstrap.'+$Record.nonce+'.json'))
    if($proof.pid -ne $Record.pid -or [long]$proof.created -ne [long]$Record.created -or $proof.nonce -cne $Record.nonce -or $proof.sid -cne $Record.sid -or
       $proof.admitted -isnot [bool] -or !$proof.admitted -or $proof.admissionScope -cne 'NO_JOB_OR_IMMEDIATE_NONKILL_BREAKAWAY_SILENT' -or
       !(Test-TrainBrokerAdmission $proof.querySucceeded $proof.inAnyJob $proof.currentJob) -or
       $proof.tokenElevated -isnot [bool] -or $proof.tokenElevated -or $proof.debugEnabled -isnot [bool] -or $proof.debugEnabled -or $proof.integrityRid -le 0 -or $proof.integrityRid -ge 12288){throw 'TRAIN002_BOOTSTRAP_PROOF_IDENTITY_OR_ADMISSION'}
    $sources=@{bootstrapSha='TrainingBootstrap.ps1';ownerSha='TrainingOwner.ps1';mainSha='Training.ps1';jobProbeSha='TrainingJobProbe.cs';brokerSha='TrainingBroker.ps1'}
    foreach($key in $sources.Keys){Assert-TrainHash (Join-Path $PSScriptRoot $sources[$key]) $proof.$key}
    if($Record.kind -ceq 'Director'){
        if($proof.probe -or $proof.lifetimeProbe -or $Record.configPath -ine (Join-Path $PSScriptRoot 'Training.ps1')){throw 'TRAIN002_BOOTSTRAP_DIRECTOR_PROBE_OR_SOURCE'}
        $handoff=Read-TrainJson (Join-Path $Layout.Root ('logs\bootstrap.'+$Record.nonce+'.run-admitted.json'))
        if($handoff.pid -ne $Record.pid -or [long]$handoff.created -ne [long]$Record.created -or $handoff.nonce -cne $Record.nonce -or $handoff.proofSha -cne (Get-FileHash -LiteralPath (Join-Path $Layout.Root ('logs\bootstrap.'+$Record.nonce+'.json'))).Hash){throw 'TRAIN002_BOOTSTRAP_DIRECTOR_HANDOFF'}
    }
    return $proof
}
function Assert-TrainNoPendingBootstrap($Layout) {
    Assert-TrainInstallation $Layout
    if(Test-Path -LiteralPath (Join-Path $Layout.Root 'pending-bootstrap.json')){throw 'TRAIN002_PENDING_BOOTSTRAP_REQUIRES_RECONCILIATION'}
}
function Get-TrainPendingBootstrap($Layout) {
    Assert-TrainInstallation $Layout
    $path=Join-Path $Layout.Root 'pending-bootstrap.json'
    if(!(Test-Path -LiteralPath $path)){return $null}
    $pending=Read-TrainJson $path;$record=$pending.process
    if($pending.installationOwner -cne [IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt')) -or $record.kind -cne 'Worker' -or
       $record.sid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $record.nonce -cnotmatch '^[0-9a-f]{32}$' -or
       $record.pid -le 0 -or [long]$record.created -le 0 -or $record.image -ine $Layout.PowerShell -or $record.configPath -ine (Join-Path $PSScriptRoot 'TrainingBootstrap.ps1') -or
       $pending.launch.pid -ne $record.pid -or $pending.launch.nonce -cne $record.nonce -or $pending.launch.expectedSid -cne $record.sid -or
       $pending.launch.bootstrapPath -ine $record.configPath -or $pending.launch.sourceHashes.bootstrap -cne $record.configHash){throw 'TRAIN002_PENDING_BOOTSTRAP_IDENTITY'}
    Assert-TrainHash $record.image $record.imageHash
    $sources=@{bootstrap='TrainingBootstrap.ps1';owner='TrainingOwner.ps1';main='Training.ps1';job='TrainingJobProbe.cs';broker='TrainingBroker.ps1'}
    foreach($key in $sources.Keys){Assert-TrainHash (Join-Path $PSScriptRoot $sources[$key]) $pending.launch.sourceHashes.$key}
    return $pending
}
function Register-TrainPendingBootstrap($Layout,$Launch,[Diagnostics.Process]$Process,[object]$Handle) {
    Assert-TrainNoPendingBootstrap $Layout
    if($Launch.pid -ne $Process.Id -or $Launch.expectedSid -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $Launch.bootstrapPath -ine (Join-Path $PSScriptRoot 'TrainingBootstrap.ps1')){throw 'TRAIN002_PENDING_BOOTSTRAP_LAUNCH'}
    $record=New-TrainProcessRecord $Process $Layout.PowerShell $Launch.nonce Worker $Launch.bootstrapPath
    if((Get-TrainHeldExitProof $record $Process $Handle).exited){throw 'TRAIN002_PENDING_BOOTSTRAP_ALREADY_EXITED'}
    Assert-TrainProcess $record -Kind Worker
    $null=[IO.Directory]::CreateDirectory((Join-Path $Layout.Root ('workers\'+$record.nonce)))
    Write-TrainJson (Join-Path $Layout.Root 'pending-bootstrap.json') @{installationOwner=[IO.File]::ReadAllText((Join-Path $Layout.Root 'owner.txt'));process=$record;launch=$Launch;registeredUtc=[DateTime]::UtcNow.ToString('o')}
    $null=Get-TrainPendingBootstrap $Layout
    return $record
}
function Test-TrainBootstrapCancelled($Layout,$Record) {
    $path=Join-Path $Layout.Root ('logs\bootstrap.'+$Record.nonce+'.cancel.json')
    if(Test-Path -LiteralPath $path){
        $cancel=Read-TrainJson $path
        if($cancel.pid -ne $Record.pid -or [long]$cancel.created -ne [long]$Record.created -or $cancel.nonce -cne $Record.nonce){throw 'TRAIN002_BOOTSTRAP_CANCEL_IDENTITY'}
        return $true
    }
    $command=Join-Path $Layout.Root 'command.json'
    if(Test-Path -LiteralPath $command){$request=Read-TrainJson $command;if($request.kind -ceq 'stop' -and $request.nonce -ceq $Record.nonce){return $true}}
    return $false
}
function Complete-TrainPendingBootstrap($Layout,$Director) {
    $pending=Get-TrainPendingBootstrap $Layout
    if(!$pending){throw 'TRAIN002_PENDING_BOOTSTRAP_MISSING'}
    if($pending.process.pid -ne $Director.pid -or [long]$pending.process.created -ne [long]$Director.created -or $pending.process.nonce -cne $Director.nonce -or $pending.process.commandHash -cne $Director.commandHash){throw 'TRAIN002_PENDING_DIRECTOR_MISMATCH'}
    Assert-TrainProcess $Director -Kind Director;$null=Assert-TrainBrokerProof $Layout $Director
    Write-TrainJson (Join-Path $Layout.Root ('archives\bootstrap.'+$Director.nonce+'.published.json')) $pending
    Remove-Item -LiteralPath (Join-Path $Layout.Root 'pending-bootstrap.json')
}
function Stop-TrainPendingBootstrap($Layout,[int]$Seconds,[Diagnostics.Process]$TrackedProcess=$null,[object]$Handle=$null) {
    $pending=Get-TrainPendingBootstrap $Layout
    if(!$pending){return $null}
    $record=$pending.process;$process=$TrackedProcess;$held=$Handle;$dispose=$false
    $result=[ordered]@{confirmed=$false;pid=$record.pid;created=$record.created;nonce=$record.nonce;exitCode=$null;proof=''}
    try{
        $absent=$(if($process){(Get-TrainHeldExitProof $record $process $held).exited}else{Test-TrainProcessAbsent $record})
        if(!$absent){
            if(!$process){$process=Get-Process -Id $record.pid -ErrorAction Stop;$held=Hold-ProcessHandle $process;$dispose=$true}
            if(!(Get-TrainHeldExitProof $record $process $held).exited){
                try{Assert-TrainProcess $record -Kind Worker}catch{if(!(Test-TrainTerminatedCimRace $_ $record $process $held)){throw}}
                if(!$process.HasExited){
                    Write-TrainJson (Join-Path $Layout.Root ('logs\bootstrap.'+$record.nonce+'.cancel.json')) @{pid=$record.pid;created=$record.created;nonce=$record.nonce}
                    $request=@{kind='stop';nonce=$record.nonce;id=[guid]::NewGuid().ToString('N');issuedUtc=[DateTime]::UtcNow.ToString('o')}
                    Write-TrainJson (Join-Path $Layout.Root ('commands\'+$request.id+'.json')) $request;Write-TrainJson (Join-Path $Layout.Root 'command.json') $request
                    if(!$process.WaitForExit($Seconds*1000)){throw 'TRAIN002_PENDING_BOOTSTRAP_STOP_UNCONFIRMED'}
                }
            }
        }
        if($process){
            $exit=Get-TrainHeldExitProof $record $process $held
            if(!$exit.exited){throw 'TRAIN002_PENDING_BOOTSTRAP_STOP_UNCONFIRMED'}
            Write-TrainExitReceipt $Layout $record $process $held 'bootstrap'
            $result.exitCode=$exit.exitCode;$result.proof='OWN_HELD_HANDLE_EXIT'
        }else{$result.proof='ABSENT_OR_REUSED_FILETIME'}
        $result.confirmed=$true
        Write-TrainJson (Join-Path $Layout.Root ('archives\bootstrap.'+$record.nonce+'.stopped.json')) @{pending=$pending;result=$result}
        Remove-Item -LiteralPath (Join-Path $Layout.Root 'pending-bootstrap.json')
        return $result
    }finally{if($dispose){$process.Dispose()}}
}
function Start-TrainBroker($Layout,[ValidatePattern('^[0-9a-f]{32}$')][string]$Nonce,[switch]$Probe,[switch]$LifetimeProbe,[switch]$StartupProbe) {
    if($LifetimeProbe -and !$Probe){throw 'TRAIN002_LIFETIME_PROBE_ONLY'}
    if($StartupProbe -and (!$Probe -or $LifetimeProbe)){throw 'TRAIN002_STARTUP_PROBE_ONLY'}
    Initialize-TrainWin32
    Assert-TrainInstallation $Layout
    $logRoot=Join-Path $Layout.Root 'logs';Assert-TrainDirectory $logRoot
    $bootstrap=Join-Path $PSScriptRoot 'TrainingBootstrap.ps1'
    $ownerSource=Join-Path $PSScriptRoot 'TrainingOwner.ps1'
    $mainSource=Join-Path $PSScriptRoot 'Training.ps1'
    $jobSource=Join-Path $PSScriptRoot 'TrainingJobProbe.cs'
    $brokerSource=Join-Path $PSScriptRoot 'TrainingBroker.ps1'
    foreach($path in @($bootstrap,$ownerSource,$mainSource,$jobSource,$brokerSource,$Layout.PowerShell)){Assert-G0PhysicalFile $path}
    $hashes=@{bootstrap=(Get-FileHash -LiteralPath $bootstrap).Hash;owner=(Get-FileHash -LiteralPath $ownerSource).Hash;main=(Get-FileHash -LiteralPath $mainSource).Hash;job=(Get-FileHash -LiteralPath $jobSource).Hash;broker=(Get-FileHash -LiteralPath $brokerSource).Hash}
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $command='"'+$Layout.PowerShell+'" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$bootstrap+'" -Root "'+$Layout.Root+'" -Nonce '+$Nonce+' -ExpectedSid '+$sid+' -BootstrapSha '+$hashes.bootstrap+' -OwnerSha '+$hashes.owner+' -MainSha '+$hashes.main+' -JobProbeSha '+$hashes.job+' -BrokerSha '+$hashes.broker
    if($Probe){$command+=' -Probe'}
    if($LifetimeProbe){$command+=' -LifetimeProbe'}
    if($StartupProbe){$command+=' -StartupProbe'}
    $environment=[Collections.Generic.List[string]]::new();$names=[Collections.Generic.List[string]]::new()
    foreach($name in @('SystemRoot','WINDIR','SystemDrive','ComSpec','OS','ProgramFiles','ProgramFiles(x86)','ProgramW6432','ProgramData','ALLUSERSPROFILE','PUBLIC','PATH','PATHEXT','JAVA_HOME','ANT_HOME','TEMP','TMP','USERPROFILE','LOCALAPPDATA','APPDATA','USERNAME','USERDOMAIN','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE')){
        $value=[Environment]::GetEnvironmentVariable($name,'Process')
        if($null -ne $value){$environment.Add($name+'='+$value);$names.Add($name)}
    }
    $environment.Add('PSModulePath='+(Join-Path (Split-Path $Layout.PowerShell -Parent) 'Modules'));$names.Add('PSModulePath')
    $startupClass=Get-CimClass -ClassName Win32_ProcessStartup -Namespace root/cimv2
    $startup=New-CimInstance -CimClass $startupClass -ClientOnly -Property @{CreateFlags=[uint32]16777216;ShowWindow=[uint16]0;EnvironmentVariables=[string[]]$environment.ToArray()}
    $startupValues=@{createFlags=$startup.CimInstanceProperties['CreateFlags'].Value;createFlagsClrType=$startup.CimInstanceProperties['CreateFlags'].Value.GetType().FullName;createFlagsCimType=$startup.CimInstanceProperties['CreateFlags'].CimType.ToString();showWindow=$startup.CimInstanceProperties['ShowWindow'].Value;showWindowClrType=$startup.CimInstanceProperties['ShowWindow'].Value.GetType().FullName;showWindowCimType=$startup.CimInstanceProperties['ShowWindow'].CimType.ToString();environmentCount=@($startup.CimInstanceProperties['EnvironmentVariables'].Value).Count;environmentNames=$names.ToArray()}
    if($Probe){
        if(!('TrainingJobProbe' -as [type])){Add-Type -Path $jobSource}
        $caller=[Win32SqlIdentity]::Process([uint32]$PID,0x1000)
        if(!$caller.Success){throw 'TRAIN002_BROKER_CALLER_IDENTITY'}
        Write-TrainJson (Join-Path $logRoot ('broker-caller.'+$Nonce+'.json')) @{pid=$PID;created=$caller.CreatedFileTime;nonce=$Nonce;sid=$sid;startup=$startupValues;currentJob=[TrainingJobProbe]::CurrentJob();jobProbeSha=$hashes.job;observedUtc=[DateTime]::UtcNow.ToString('o')}
    }
    $created=Invoke-CimMethod -ClassName Win32_Process -Namespace root/cimv2 -MethodName Create -Arguments @{CommandLine=$command;CurrentDirectory=$Layout.Root;ProcessStartupInformation=$startup}
    if($created.ReturnValue -ne 0 -or $created.ProcessId -le 0){throw ('TRAIN002_BROKER_CREATE_FAILED:'+ $created.ReturnValue)}
    return [ordered]@{pid=[int]$created.ProcessId;nonce=$Nonce;expectedSid=$sid;creationFlags=$startupValues.createFlags;showWindow=$startupValues.showWindow;startup=$startupValues;environmentNames=$names.ToArray();bootstrapPath=$bootstrap;sourceHashes=$hashes;stdoutPath=(Join-Path $logRoot ('broker.'+$Nonce+'.out'));stderrPath=(Join-Path $logRoot ('broker.'+$Nonce+'.err'));proofPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.json'));readyPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.ready.json'));ackPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.ack.json'));acknowledgedPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.acknowledged.json'));errorPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.error.json'));runAdmittedPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.run-admitted.json'));heartbeatPath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.heartbeat.json'));releasePath=(Join-Path $logRoot ('bootstrap.'+$Nonce+'.release.json'))}
}
