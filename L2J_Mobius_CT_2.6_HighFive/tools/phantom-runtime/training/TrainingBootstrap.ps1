[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{32}$')][string]$Nonce,
    [Parameter(Mandatory)][ValidatePattern('^S-1-5-[0-9-]+$')][string]$ExpectedSid,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$BootstrapSha,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$OwnerSha,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$MainSha,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$JobProbeSha,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$BrokerSha,
    [switch]$Probe,
    [switch]$LifetimeProbe,
    [switch]$StartupProbe
)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$lifetimeDeadline=[DateTime]::UtcNow.AddSeconds(30)
$env:PSModulePath=Join-Path $PSHOME 'Modules'
$ownerSource=Join-Path $PSScriptRoot 'TrainingOwner.ps1'
if((Get-FileHash -LiteralPath $ownerSource).Hash -ine $OwnerSha){throw 'TRAIN002_BOOTSTRAP_OWNER_SOURCE_CHANGED'}
. $ownerSource
$fixture=@{Root=[IO.Path]::GetFullPath($Root);PowerShell=(Join-Path $PSHOME 'powershell.exe')}
if([Security.Principal.WindowsIdentity]::GetCurrent().User.Value -cne $ExpectedSid){throw 'TRAIN002_BOOTSTRAP_SID_CHANGED'}
Assert-TrainInstallation $fixture
if($LifetimeProbe -and !$Probe){throw 'TRAIN002_LIFETIME_PROBE_ONLY'}
if($StartupProbe -and (!$Probe -or $LifetimeProbe)){throw 'TRAIN002_STARTUP_PROBE_ONLY'}
$logRoot=Join-Path $fixture.Root 'logs';Assert-TrainDirectory $logRoot
$stdout=Join-Path $logRoot ('broker.'+$Nonce+'.out');$stderr=Join-Path $logRoot ('broker.'+$Nonce+'.err')
$script:TrainBootstrapExit=1
try{
    & {
        Assert-TrainHash $PSCommandPath $BootstrapSha
        $mainSource=Join-Path $PSScriptRoot 'Training.ps1';Assert-TrainHash $mainSource $MainSha
        $jobSource=Join-Path $PSScriptRoot 'TrainingJobProbe.cs';Assert-TrainHash $jobSource $JobProbeSha
        $brokerSource=Join-Path $PSScriptRoot 'TrainingBroker.ps1';Assert-TrainHash $brokerSource $BrokerSha
        . $brokerSource
        Initialize-TrainWin32
        if(!('TrainingJobProbe' -as [type])){Add-Type -Path $jobSource}
        $pin=[Win32SqlIdentity]::Hold([uint32]$PID)
        try{
            $identity=[Win32SqlIdentity]::Read($pin,[uint32]$PID);$inside=$false
            $queried=[TrainingJobProbe]::IsProcessInJob($pin.DangerousGetHandle(),[IntPtr]::Zero,[ref]$inside)
            $nativeError=$(if($queried){0}else{[Runtime.InteropServices.Marshal]::GetLastWin32Error()})
            $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
            $currentJob=[TrainingJobProbe]::CurrentJob()
            $admitted=Test-TrainBrokerAdmission $queried $inside $currentJob
            $proofPath=Join-Path $logRoot ('bootstrap.'+$Nonce+'.json')
            Write-TrainJson $proofPath @{schemaVersion=1;pid=$PID;created=$identity.CreatedFileTime;nonce=$Nonce;sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;tokenElevated=[Win32SqlIdentity]::CurrentTokenElevated();integrityRid=[Win32SqlIdentity]::CurrentIntegrityRid();debugEnabled=[Win32SqlIdentity]::CurrentDebugEnabled();querySucceeded=$queried;inAnyJob=$(if($queried){$inside}else{$null});nativeError=$nativeError;currentJob=$currentJob;admitted=$admitted;admissionScope='NO_JOB_OR_IMMEDIATE_NONKILL_BREAKAWAY_SILENT';ancestorJobChain='NOT_ENUMERATED';parentProcessId=$cim.ParentProcessId;probe=[bool]$Probe;lifetimeProbe=[bool]$LifetimeProbe;startupProbe=[bool]$StartupProbe;bootstrapSha=$BootstrapSha;ownerSha=$OwnerSha;mainSha=$MainSha;jobProbeSha=$JobProbeSha;brokerSha=$BrokerSha;observedUtc=[DateTime]::UtcNow.ToString('o')}
            $gate=@{pid=$PID;created=$identity.CreatedFileTime;nonce=$Nonce}
            $ready=Join-Path $logRoot ('bootstrap.'+$Nonce+'.ready.json');$ackPath=Join-Path $logRoot ('bootstrap.'+$Nonce+'.ack.json')
            Write-TrainJson $ready $gate
            $ackSeconds=$(if($StartupProbe){3}elseif($Probe){20}else{15})
            $deadline=[DateTime]::UtcNow.AddSeconds($ackSeconds)
                if($LifetimeProbe -and $deadline -gt $lifetimeDeadline){$deadline=$lifetimeDeadline}
            while(!(Test-Path -LiteralPath $ackPath) -and [DateTime]::UtcNow -lt $deadline){
                if(Test-TrainBootstrapCancelled $fixture $gate){$script:TrainBootstrapExit=0;return}
                Start-Sleep -Milliseconds 20
            }
            if(Test-TrainBootstrapCancelled $fixture $gate){$script:TrainBootstrapExit=0;return}
            if(!(Test-Path -LiteralPath $ackPath)){throw 'TRAIN002_BOOTSTRAP_ACK_TIMEOUT'}
            $ack=Read-TrainJson $ackPath
            if($ack.pid -ne $PID -or [long]$ack.created -ne $identity.CreatedFileTime -or $ack.nonce -cne $Nonce){throw 'TRAIN002_BROKER_PROBE_ACK_IDENTITY'}
            if(!$Probe -or $StartupProbe){
                $pending=Get-TrainPendingBootstrap $fixture
                if(!$pending -or $pending.process.pid -ne $PID -or [long]$pending.process.created -ne $identity.CreatedFileTime -or $pending.process.nonce -cne $Nonce -or $ack.proofSha -cne (Get-FileHash -LiteralPath $proofPath).Hash){throw 'TRAIN002_BOOTSTRAP_ACK_WITHOUT_PENDING_PROOF'}
            }
            Write-TrainJson (Join-Path $logRoot ('bootstrap.'+$Nonce+'.acknowledged.json')) $ack
            if($StartupProbe){
                # Fixed test-only ACK -> owner window; ordinary production has no delay.
                $window=[DateTime]::UtcNow.AddSeconds(3)
                while([DateTime]::UtcNow -lt $window){if(Test-TrainBootstrapCancelled $fixture $gate){$script:TrainBootstrapExit=0;return};Start-Sleep -Milliseconds 20}
            }
            if(!$queried -or (!$admitted -and !$LifetimeProbe)){throw 'TRAIN002_BOOTSTRAP_JOB_ADMISSION_FAILED'}
        }finally{$pin.Dispose()}
        if(Test-TrainBootstrapCancelled $fixture $gate){$script:TrainBootstrapExit=0;return}
        if(!$Probe -or $StartupProbe){Write-TrainJson (Join-Path $logRoot ('bootstrap.'+$Nonce+'.run-admitted.json')) @{pid=$PID;created=$gate.created;nonce=$Nonce;proofSha=(Get-FileHash -LiteralPath $proofPath).Hash}}
        if(!$Probe){
            & $mainSource -Action Run -Root $fixture.Root -Nonce $Nonce
            $script:TrainBootstrapExit=Assert-ObservedExitCode $LASTEXITCODE
            return
        }
        Write-Output ('TRAIN002_BROKER_STDOUT:'+ $Nonce)
        Write-Error ('TRAIN002_BROKER_STDERR:'+ $Nonce) -ErrorAction Continue
        Write-Information ('TRAIN002_BROKER_INFO:'+ $Nonce) -InformationAction Continue
        if($LifetimeProbe){
            # This bounded fixture reports survival only; it does not certify the unnamed job chain.
            $sequence=0L;$releasePath=Join-Path $logRoot ('bootstrap.'+$Nonce+'.release.json')
            while([DateTime]::UtcNow -lt $lifetimeDeadline){
                $sequence++
                Write-TrainJson (Join-Path $logRoot ('bootstrap.'+$Nonce+'.heartbeat.json')) @{pid=$PID;created=$identity.CreatedFileTime;nonce=$Nonce;sequence=$sequence;observedUtc=[DateTime]::UtcNow.ToString('o')}
                if(Test-Path -LiteralPath $releasePath){
                    $release=Read-TrainJson $releasePath
                    if($release.pid -ne $PID -or [long]$release.created -ne $identity.CreatedFileTime -or $release.nonce -cne $Nonce){throw 'TRAIN002_LIFETIME_RELEASE_IDENTITY'}
                    break
                }
                Start-Sleep -Milliseconds 500
            }
        }else{Start-Sleep -Seconds 3}
        $script:TrainBootstrapExit=5
    } 3>&1 4>&1 5>&1 6>&1 1>>$stdout 2>>$stderr
}catch{
    Write-TrainJson (Join-Path $logRoot ('bootstrap.'+$Nonce+'.error.json')) (Get-TrainSafeErrorRecord $_)
}
exit $script:TrainBootstrapExit
