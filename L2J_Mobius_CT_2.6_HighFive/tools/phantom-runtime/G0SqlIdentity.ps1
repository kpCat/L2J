# Standalone read-only identity seam. It never writes pins, pass markers or campaign receipts.
function Assert-G0PhysicalFile([string]$Path) {
    if(-not [IO.Path]::IsPathRooted($Path)){throw 'G0_ABSOLUTE_PATH_REQUIRED'}
    $item=Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if($item.PSIsContainer -or [IO.Path]::GetFullPath($Path) -ine $item.FullName){throw 'G0_CANONICAL_FILE_REQUIRED'}
    if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'G0_REPARSE_PATH'}
    for($part=$item.Directory;$null -ne $part;$part=$part.Parent){if($part.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'G0_REPARSE_PATH'}}
}

function Get-G0Microseconds([long]$FileTime) {
    $remainder=0L
    return [Math]::DivRem($FileTime,10L,[ref]$remainder)
}

function Assert-G0SharedEvidence($Fact,$Expected) {
    if($Fact.NativeBeforeSuccess -ne $Fact.NativeAfterSuccess -or $Fact.NativeBeforeError -ne $Fact.NativeAfterError){throw 'G0_NATIVE_VISIBILITY_CHANGED'}
    if($Fact.ServiceName -cne $Expected.ServiceName -or $Fact.ServiceState -ne 4 -or $Fact.ServiceType -ne 16 -or $Fact.ServiceAccount -ine $Expected.ServiceAccount){throw 'G0_SCM_SERVICE_MISMATCH'}
    if($Fact.ListenerPids.Count -ne 1 -or $Fact.ListenerPids[0] -ne $Fact.ServicePid -or $Fact.ServicePid -le 0){throw 'G0_SCM_TCP_PID_MISMATCH'}
    if($Fact.ServicePid -ne $Fact.AfterServicePid -or $Fact.ServiceState -ne $Fact.AfterServiceState -or $Fact.ServiceType -ne $Fact.AfterServiceType -or $Fact.ServiceAccount -cne $Fact.AfterServiceAccount -or $Fact.ListenerKey -cne $Fact.AfterListenerKey -or $Fact.RegisteredCommand -cne $Fact.AfterRegisteredCommand){throw 'G0_IDENTITY_CHANGED_DURING_READ'}
    if($Fact.RegisteredCommand -notmatch '^"([^"\r\n]+)"\s+"--defaults-file=([^"\r\n]+)"\s+"([^"\r\n]+)"\s*$'){throw 'G0_AMBIGUOUS_SERVICE_COMMAND'}
    if($Matches[1] -ine $Expected.Executable -or $Matches[2] -ine $Expected.Config -or $Matches[3] -cne $Expected.ServiceName){throw 'G0_REGISTERED_PATH_NOT_ALLOWLISTED'}
    foreach($value in @($Fact.BinaryHash,$Fact.AfterBinaryHash,$Fact.ConfigHash,$Fact.AfterConfigHash)){if([string]::IsNullOrWhiteSpace($value) -or $value -cnotmatch '^[0-9A-Fa-f]{64}$'){throw 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'}}
    if(-not $Fact.PhysicalPaths -or $Fact.BinaryHash -cne $Fact.AfterBinaryHash -or $Fact.ConfigHash -cne $Fact.AfterConfigHash){throw 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'}
    if($Fact.WmiCreated -le 0 -or $Fact.WmiCreated -ne $Fact.AfterWmiCreated){throw 'G0_PROCESS_CREATION_MISMATCH'}
    foreach($path in @($Fact.WmiImage,$Fact.AfterWmiImage)){if($path -and $path -ine $Expected.Executable){throw 'G0_WMI_NATIVE_IMAGE_CONFLICT'}}
    if($Fact.RuntimeCommand -cne $Fact.AfterRuntimeCommand){throw 'G0_RUNTIME_COMMAND_CHANGED'}
    if($Fact.RuntimeCommand -and $Fact.RuntimeCommand.Trim() -ine $Fact.RegisteredCommand.Trim()){throw 'G0_RUNTIME_SERVICE_COMMAND_CONFLICT'}
}

function Assert-G0IdentityEvidence($Fact,$Expected,[bool]$RequireElevated=$false) {
    if($Fact.TokenElevated -ne $RequireElevated){throw 'G0_UNEXPECTED_ELEVATED_TOKEN'}
    if([string]::IsNullOrWhiteSpace($Fact.RuntimeCommand)){throw 'G0_RUNTIME_COMMAND_UNAVAILABLE'}
    Assert-G0SharedEvidence $Fact $Expected
    # Registered/next-start configuration cannot substitute for the running process image.
    if(-not $Fact.NativeSuccess){
        if($Fact.NativeError -eq 5){throw 'G0_RUNTIME_IMAGE_ACCESS_DENIED'}
        throw 'G0_RUNTIME_IMAGE_UNAVAILABLE'
    }
    if($Fact.RuntimeImage -ine $Expected.Executable -or $Fact.AfterRuntimeImage -ine $Fact.RuntimeImage){throw 'G0_RUNTIME_IMAGE_MISMATCH'}
    if($Fact.NativeCreated -le 0 -or $Fact.NativeCreated -ne $Fact.AfterNativeCreated -or (Get-G0Microseconds $Fact.NativeCreated) -ne (Get-G0Microseconds $Fact.WmiCreated)){throw 'G0_PROCESS_CREATION_MISMATCH'}
    if($Fact.WmiImage -and $Fact.WmiImage -ine $Fact.RuntimeImage){throw 'G0_WMI_NATIVE_IMAGE_CONFLICT'}
    if([string]::IsNullOrWhiteSpace($Fact.RuntimeCommand)){throw 'G0_RUNTIME_COMMAND_UNAVAILABLE'}
    if($Fact.RuntimeCommand.Trim() -ine $Fact.RegisteredCommand.Trim()){throw 'G0_RUNTIME_SERVICE_COMMAND_CONFLICT'}
    $hash=[Security.Cryptography.SHA256]::Create()
    try{$commandHash=[Convert]::ToBase64String($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($Fact.RuntimeCommand)))}finally{$hash.Dispose()}
    return [ordered]@{PID=$Fact.ServicePid;Created=[DateTime]::FromFileTimeUtc($Fact.NativeCreated).ToString('o');Binary=$Fact.BinaryHash;CommandHash=$commandHash;RuntimeImage=$Fact.RuntimeImage;RuntimeImageSource='QueryFullProcessImageNameW';RegisteredService=$Fact.ServiceName}
}

function Get-G0SqlIdentityEvidence($Expected,[int]$Port=3308) {
    if(-not ('Win32SqlIdentity' -as [type])){Add-Type -Path (Join-Path $PSScriptRoot 'Win32SqlIdentity.cs')}
    $before=[Win32SqlIdentity]::Service($Expected.ServiceName)
    $tcp=@([Win32SqlIdentity]::Listeners($Port))
    $listenerPids=@($tcp.OwningProcess|Sort-Object -Unique)
    $listenerKey=($tcp|Sort-Object LocalAddress,OwningProcess|ForEach-Object {"$($_.LocalAddress):$($_.LocalPort):$($_.OwningProcess)"}) -join '|'
    $native=[Win32SqlIdentity]::Process($before.Pid,0x1000)
    $wmi=Get-CimInstance Win32_Process -Filter "ProcessId=$($before.Pid)" -ErrorAction Stop
    if($null -eq $wmi -or $null -eq $wmi.CreationDate){throw 'G0_PROCESS_METADATA_ABSENT'}
    foreach($path in @($Expected.Executable,$Expected.Config)){
        Assert-G0PhysicalFile $path
    }
    $binaryHash=(Get-FileHash -LiteralPath $Expected.Executable -Algorithm SHA256 -ErrorAction Stop).Hash
    $configHash=(Get-FileHash -LiteralPath $Expected.Config -Algorithm SHA256 -ErrorAction Stop).Hash
    $nativeAfter=[Win32SqlIdentity]::Process($before.Pid,0x1000)
    $wmiAfter=Get-CimInstance Win32_Process -Filter "ProcessId=$($before.Pid)" -ErrorAction Stop
    if($null -eq $wmiAfter -or $null -eq $wmiAfter.CreationDate){throw 'G0_PROCESS_METADATA_ABSENT'}
    $after=[Win32SqlIdentity]::Service($Expected.ServiceName)
    $afterTcp=@([Win32SqlIdentity]::Listeners($Port))
    $afterKey=($afterTcp|Sort-Object LocalAddress,OwningProcess|ForEach-Object {"$($_.LocalAddress):$($_.LocalPort):$($_.OwningProcess)"}) -join '|'
    return [pscustomobject]@{
        TokenElevated=[Win32SqlIdentity]::CurrentTokenElevated();ServiceName=$before.Name;ServiceState=$before.State;ServiceType=$before.Type;ServiceAccount=$before.Account;ServicePid=$before.Pid
        AfterServicePid=$after.Pid;AfterServiceState=$after.State;AfterServiceType=$after.Type;AfterServiceAccount=$after.Account;RegisteredCommand=$before.BinaryPath;AfterRegisteredCommand=$after.BinaryPath
        ListenerPids=$listenerPids;ListenerKey=$listenerKey;AfterListenerKey=$afterKey;PhysicalPaths=$true
        BinaryHash=$binaryHash;AfterBinaryHash=(Get-FileHash -LiteralPath $Expected.Executable -Algorithm SHA256 -ErrorAction Stop).Hash
        ConfigHash=$configHash;AfterConfigHash=(Get-FileHash -LiteralPath $Expected.Config -Algorithm SHA256 -ErrorAction Stop).Hash
        NativeSuccess=($native.Success -and $nativeAfter.Success);NativeError=if(-not $native.Success){$native.Error}else{$nativeAfter.Error}
        NativeBeforeSuccess=$native.Success;NativeAfterSuccess=$nativeAfter.Success
        NativeBeforeError=$native.Error;NativeAfterError=$nativeAfter.Error
        RuntimeImage=$native.ImagePath;AfterRuntimeImage=$nativeAfter.ImagePath;NativeCreated=$native.CreatedFileTime;AfterNativeCreated=$nativeAfter.CreatedFileTime
        WmiCreated=$wmi.CreationDate.ToUniversalTime().ToFileTimeUtc();WmiImage=$wmi.ExecutablePath;RuntimeCommand=$wmi.CommandLine
        AfterWmiCreated=$wmiAfter.CreationDate.ToUniversalTime().ToFileTimeUtc();AfterWmiImage=$wmiAfter.ExecutablePath;AfterRuntimeCommand=$wmiAfter.CommandLine;ProcessName=$wmi.Name
    }
}

function Get-StrictG0SqlIdentity($Expected,[int]$Port=3308) {
    $fact=Get-G0SqlIdentityEvidence $Expected $Port
    return Assert-G0IdentityEvidence $fact $Expected
}

function Get-G0OwnedSqlIdentity([int]$OwnedPid,[long]$Created,[string]$Executable,[string]$Config,[string]$DataDirectory,[int]$Port) {
    if($Port -ne 33306 -or $Created -le 0){throw 'G0_OWNED_SCOPE'}
    foreach($file in @($Executable,$Config)){Assert-G0PhysicalFile $file}
    $data=Get-Item -LiteralPath $DataDirectory -Force
    if(!$data.PSIsContainer -or $data.FullName -ine (Join-Path (Split-Path $Config -Parent) 'data') -or ($data.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'G0_OWNED_DATA_PATH'}
    $binary=(Get-FileHash -LiteralPath $Executable).Hash
    $configuration=(Get-FileHash -LiteralPath $Config).Hash
    $expectedConfig=@('[mysqld]','bind-address=127.0.0.1',"port=$Port",('datadir="'+$data.FullName.Replace('\','/')+'"'))
    $loaded=(Get-Content -LiteralPath $Config) -join "`n"
    if($loaded -cne ($expectedConfig -join "`n") -and $loaded -cne (($expectedConfig+ 'default-time-zone=+00:00') -join "`n")){throw 'G0_OWNED_CONFIG_OPTIONS'}
    $commandPattern='^"?'+[regex]::Escape($Executable)+'"?\s+"--defaults-file='+[regex]::Escape($Config)+'"\s+--console\s*$'
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $command=$null
    for($turn=0;$turn -lt 2;$turn++){
        $native=[Win32SqlIdentity]::Process([uint32]$OwnedPid,0x1000)
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$OwnedPid" -ErrorAction Stop
        if(!$native.Success -or $native.ImagePath -ine $Executable -or $native.CreatedFileTime -ne $Created -or !$process -or
            (Get-G0Microseconds $native.CreatedFileTime) -ne (Get-G0Microseconds $process.CreationDate.ToUniversalTime().ToFileTimeUtc()) -or
            $process.ExecutablePath -ine $Executable -or $process.CommandLine -notmatch $commandPattern){throw 'G0_OWNED_PROCESS_IDENTITY'}
        $owner=Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid -ErrorAction Stop
        if($owner.ReturnValue -ne 0 -or $owner.Sid -cne $sid){throw 'G0_OWNED_PROCESS_SID'}
        $listeners=@([Win32SqlIdentity]::Listeners($Port))
        if($listeners.Count -ne 1 -or $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne $OwnedPid -or
            @(Get-CimInstance Win32_Service -Filter "ProcessId=$OwnedPid" -ErrorAction Stop).Count){throw 'G0_OWNED_LISTENER_OR_SERVICE'}
        if($command -and $command -cne $process.CommandLine){throw 'G0_OWNED_COMMAND_CHANGED'}
        $command=$process.CommandLine
    }
    if((Get-FileHash -LiteralPath $Executable).Hash -cne $binary -or (Get-FileHash -LiteralPath $Config).Hash -cne $configuration){throw 'G0_OWNED_FILE_CHANGED'}
    return [ordered]@{PID=$OwnedPid;Created=[DateTime]::FromFileTimeUtc($Created).ToString('o');RuntimeImage=$Executable;Binary=$binary;ConfigHash=$configuration;DataDirectory=$data.FullName;Port=$Port;OwnerSid=$sid;RegisteredService=$false}
}
