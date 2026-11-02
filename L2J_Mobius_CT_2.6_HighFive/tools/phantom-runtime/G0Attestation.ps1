# Shared identity-only protocol. No SQL, campaign markers, process writes or service controls.
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1) { throw 'G0_WINDOWS_POWERSHELL_51_REQUIRED' }
. (Join-Path $PSScriptRoot 'G0SqlIdentity.ps1')
if (-not ('Win32SqlIdentity' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'Win32SqlIdentity.cs') -ErrorAction Stop }
$script:G0RequestFields=@('Version','Nonce','Issued','Expires','CallerSid','CallerPid','CallerCreated','ServiceName','ServiceAccount','Executable','Config','Port','Before')
$script:G0ReceiptFields=@('Version','Nonce','RequestHash','CallerSid','CallerPid','CallerCreated','Issued','Expires','Pid','Created','RuntimeImage','Binary','ConfigHash','CommandHash','Fingerprint','ListenerKey')
$script:G0ObservationFields=@('Pid','CreatedMicro','Binary','ConfigHash','RegisteredHash','ListenerKey')

function Get-G0Hash([string]$Text) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToBase64String($hash.ComputeHash([Text.UTF8Encoding]::new($false,$true).GetBytes($Text))) } finally {$hash.Dispose()}
}
function Get-G0HexHash([string]$Text) { return ([BitConverter]::ToString([Convert]::FromBase64String((Get-G0Hash $Text)))).Replace('-','').ToLowerInvariant() }
function ConvertTo-G0Document($Values,[string[]]$Fields) {
    if($Values.Count -ne $Fields.Count){throw 'G0_DOCUMENT_FIELDS'}
    $builder=[Text.StringBuilder]::new("G0A1`n")
    foreach($name in $Fields) {
        if(!$Values.Contains($name) -or $Values[$name] -isnot [string] -or $Values[$name].Length -gt 2048 -or $Values[$name] -match '[\r\n\x00]'){throw 'G0_DOCUMENT_FIELDS'}
        $null=$builder.Append($name).Append(':').Append([Convert]::ToBase64String([Text.UTF8Encoding]::new($false,$true).GetBytes($Values[$name]))).Append("`n")
    }
    $result=$builder.ToString()
    if([Text.Encoding]::UTF8.GetByteCount($result) -gt 16384){throw 'G0_DOCUMENT_SIZE'}
    return $result
}
function ConvertFrom-G0Document([string]$Text,[string[]]$Fields) {
    if($Text.Length -gt 16384 -or !$Text.StartsWith("G0A1`n",[StringComparison]::Ordinal) -or $Text.Contains("`r")){throw 'G0_DOCUMENT_FORMAT'}
    $lines=$Text.Split([char]10)
    if($lines.Length -ne ($Fields.Count+2) -or $lines[-1] -cne ''){throw 'G0_DOCUMENT_FORMAT'}
    $values=[ordered]@{}
    for($i=0;$i -lt $Fields.Count;$i++) {
        $prefix=$Fields[$i]+':'
        if(!$lines[$i+1].StartsWith($prefix,[StringComparison]::Ordinal)){throw 'G0_DOCUMENT_FORMAT'}
        try {
            $encoded=$lines[$i+1].Substring($prefix.Length)
            $bytes=[Convert]::FromBase64String($encoded)
            if([Convert]::ToBase64String($bytes) -cne $encoded){throw 'G0_DOCUMENT_FORMAT'}
            $values[$Fields[$i]]=[Text.UTF8Encoding]::new($false,$true).GetString($bytes)
        } catch {throw 'G0_DOCUMENT_FORMAT'}
    }
    if((ConvertTo-G0Document $values $Fields) -cne $Text){throw 'G0_DOCUMENT_FORMAT'}
    return $values
}
function Get-G0Integer([string]$Value,[string]$Reason) {
    $result=0L
    if($Value -cnotmatch '^[1-9][0-9]{0,18}$' -or ![long]::TryParse($Value,[ref]$result) -or $result -le 0){throw $Reason}
    return $result
}
function Test-G0Hash([string]$Value) {
    try { $bytes=[Convert]::FromBase64String($Value); return ($bytes.Length -eq 32 -and [Convert]::ToBase64String($bytes) -ceq $Value) } catch {return $false}
}
function Get-G0ValidationTime([long]$Start,[TimeSpan]$Elapsed,[long]$Now) {
    if($Now -lt $Start -or $Elapsed.Ticks -lt 0){throw 'G0_CLOCK_ROLLBACK'}
    if($Elapsed.TotalSeconds -ge 120){throw 'G0_REQUEST_EXPIRED'}
    # A clock rollback must not extend a receipt's 30-second life inside a live challenge.
    return [Math]::Max($Now,$Start+$Elapsed.Ticks)
}
function Assert-G0Request($Request,[long]$Now) {
    $null=ConvertTo-G0Document $Request $script:G0RequestFields
    if($Request.Version -cne '1' -or $Request.Nonce -cnotmatch '^[0-9a-f]{64}$'){throw 'G0_REQUEST_FORMAT'}
    $issued=Get-G0Integer $Request.Issued 'G0_REQUEST_TIME';$expires=Get-G0Integer $Request.Expires 'G0_REQUEST_TIME'
    if($issued -gt $Now -or $expires -le $Now -or $expires -le $issued -or $expires-$issued -gt 1200000000L){throw 'G0_REQUEST_TIME'}
    $null=Get-G0Integer $Request.CallerCreated 'G0_REQUEST_CALLER'
    $caller=Get-G0Integer $Request.CallerPid 'G0_REQUEST_CALLER'
    if($caller -gt [uint32]::MaxValue -or $Request.CallerSid -cnotmatch '^S-1-5-21-[0-9]+-[0-9]+-[0-9]+-[0-9]+$'){throw 'G0_REQUEST_CALLER'}
    $null=[Security.Principal.SecurityIdentifier]::new($Request.CallerSid)
    $port=Get-G0Integer $Request.Port 'G0_REQUEST_POLICY'
    if($port -gt 65535 -or $Request.ServiceName -cnotmatch '^[A-Za-z][A-Za-z0-9_-]{0,79}$' -or $Request.ServiceAccount -ine ('NT SERVICE\'+$Request.ServiceName) -or !(Test-G0Hash $Request.Before)){throw 'G0_REQUEST_POLICY'}
    $null=[G0AttestationFile]::Canonical($Request.Executable);$null=[G0AttestationFile]::Canonical($Request.Config)
}
function ConvertTo-G0Observation($Fact,$Expected) {
    Assert-G0SharedEvidence $Fact $Expected
    if($Fact.ProcessName -notin @('mysqld.exe','mariadbd.exe')){throw 'G0_SQL_SERVER_EXECUTABLE'}
    if(!$Fact.NativeSuccess -and $Fact.NativeError -ne 5){throw 'G0_RUNTIME_IMAGE_UNAVAILABLE'}
    if($Fact.NativeSuccess -and ($Fact.RuntimeImage -ine $Expected.Executable -or $Fact.RuntimeImage -ine $Fact.AfterRuntimeImage -or $Fact.NativeCreated -ne $Fact.AfterNativeCreated -or (Get-G0Microseconds $Fact.NativeCreated) -ne (Get-G0Microseconds $Fact.WmiCreated))){throw 'G0_PROCESS_CREATION_MISMATCH'}
    $public=[ordered]@{Pid=[string]$Fact.ServicePid;CreatedMicro=[string](Get-G0Microseconds $Fact.WmiCreated);Binary=$Fact.BinaryHash.ToUpperInvariant();ConfigHash=$Fact.ConfigHash.ToUpperInvariant();RegisteredHash=(Get-G0Hash $Fact.RegisteredCommand);ListenerKey=$Fact.ListenerKey}
    $fingerprint=Get-G0Hash (ConvertTo-G0Document $public $script:G0ObservationFields)
    $public.Fingerprint=$fingerprint
    $public.RuntimeHash=if($Fact.RuntimeCommand){Get-G0Hash $Fact.RuntimeCommand}else{''}
    $public.NativeCreated=if($Fact.NativeSuccess){[string]$Fact.NativeCreated}else{''}
    $public.RuntimeImage=$Expected.Executable
    return $public
}
function Get-G0PinnedEvidence($Expected,[int]$Port) {
    $pins=[Collections.Generic.List[IDisposable]]::new()
    try {
        foreach($path in @($Expected.Executable,$Expected.Config)) {
            Assert-G0PhysicalFile $path
            $pins.Add([G0AttestationFile]::PinParents($path,$false))
            $pins.Add([G0AttestationFile]::PinFile($path))
        }
        $fact=Get-G0SqlIdentityEvidence $Expected $Port
        foreach($path in @($Expected.Executable,$Expected.Config)){Assert-G0PhysicalFile $path}
        return $fact
    } finally {for($i=$pins.Count-1;$i -ge 0;$i--){$pins[$i].Dispose()}}
}
function Get-G0Observation($Expected,[int]$Port) { return ConvertTo-G0Observation (Get-G0PinnedEvidence $Expected $Port) $Expected }
function Assert-G0Receipt($Receipt,$Request,[string]$RequestHash,$Observation,[long]$Now) {
    Assert-G0Request $Request $Now
    $null=ConvertTo-G0Document $Receipt $script:G0ReceiptFields
    foreach($field in @('Version','Nonce','CallerSid','CallerPid','CallerCreated')){if($Receipt[$field] -cne $Request[$field]){throw 'G0_RECEIPT_BINDING'}}
    if($Receipt.RequestHash -cne $RequestHash){throw 'G0_RECEIPT_BINDING'}
    $issued=Get-G0Integer $Receipt.Issued 'G0_RECEIPT_TIME';$expires=Get-G0Integer $Receipt.Expires 'G0_RECEIPT_TIME'
    if($issued -lt [long]$Request.Issued -or $issued -gt $Now -or $expires -le $Now -or $expires -le $issued -or $expires-$issued -gt 300000000L -or $expires -gt [long]$Request.Expires){throw 'G0_RECEIPT_TIME'}
    if($Receipt.Binary -cnotmatch '^[0-9A-Fa-f]{64}$' -or $Receipt.ConfigHash -cnotmatch '^[0-9A-Fa-f]{64}$' -or !(Test-G0Hash $Receipt.CommandHash)){throw 'G0_RECEIPT_HASH'}
    $created=Get-G0Integer $Receipt.Created 'G0_RECEIPT_IDENTITY';$null=Get-G0Integer $Receipt.Pid 'G0_RECEIPT_IDENTITY'
    if($Receipt.Pid -cne $Observation.Pid -or $Receipt.RuntimeImage -ine $Request.Executable -or $Receipt.RuntimeImage -ine $Observation.RuntimeImage -or $Receipt.Binary -ine $Observation.Binary -or $Receipt.ConfigHash -ine $Observation.ConfigHash -or [string](Get-G0Microseconds $created) -cne $Observation.CreatedMicro -or $Receipt.Fingerprint -cne $Request.Before -or $Receipt.Fingerprint -cne $Observation.Fingerprint -or $Receipt.ListenerKey -cne $Observation.ListenerKey){throw 'G0_RECEIPT_IDENTITY'}
    if(($Observation.NativeCreated -and $Receipt.Created -cne $Observation.NativeCreated) -or ($Observation.RuntimeHash -and $Receipt.CommandHash -cne $Observation.RuntimeHash)){throw 'G0_RECEIPT_IDENTITY'}
    return [ordered]@{PID=[uint32]$Receipt.Pid;Created=[DateTime]::FromFileTimeUtc($created).ToString('o');Binary=$Receipt.Binary;CommandHash=$Receipt.CommandHash;RuntimeImage=$Receipt.RuntimeImage;RuntimeImageSource='PrivilegedReceipt/QueryFullProcessImageNameW';RegisteredService=$Request.ServiceName}
}
function Get-G0ReceiptRoot([string]$Sid) { return Join-Path ([Environment]::GetFolderPath('CommonApplicationData')) ('PhantomG0Attestation\'+$Sid) }
function Assert-G0HelperExit($Value) {
    # Match Task108R3: null/string must never coerce to successful integer zero.
    if($null -eq $Value -or $Value -isnot [int] -or $Value -ne 0){throw 'G0_HELPER_EXIT'}
}
function Invoke-G0HelperScript([string]$Path,[string]$RequestPath,[string]$RequestSha256) {
    $global:LASTEXITCODE=$null
    & $Path -RequestPath $RequestPath -RequestSha256 $RequestSha256
    $observed=$LASTEXITCODE
    Assert-G0HelperExit $observed
}
function New-G0HelperBootstrap([string]$Tools,[string]$RequestPath,[string]$RequestSha256,$Pins) {
    $payload=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((@{Tools=$Tools;Request=$RequestPath;Hash=$RequestSha256;Pins=$Pins}|ConvertTo-Json -Compress)))
    # All variable data is Base64, never interpolated as executable PowerShell.
    $code=@'
$ErrorActionPreference='Stop'
$held=[Collections.Generic.List[IDisposable]]::new()
try {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    if(!([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'G0_HELPER_ELEVATION_REQUIRED'}
    $data=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__PAYLOAD__'))|ConvertFrom-Json
    $native=[IO.File]::ReadAllBytes((Join-Path $data.Tools 'Win32SqlIdentity.cs'))
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$hash=[BitConverter]::ToString($sha.ComputeHash($native)).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
    if($hash -cne $data.Pins.'Win32SqlIdentity.cs'){throw 'G0_SOURCE_HASH'}
    Add-Type -TypeDefinition ([Text.UTF8Encoding]::new($false,$true).GetString($native)) -ErrorAction Stop
    foreach($pin in $data.Pins.PSObject.Properties){
        $path=Join-Path $data.Tools $pin.Name
        $held.Add([G0AttestationFile]::PinParents($path,$false))
        $held.Add([G0AttestationFile]::PinFile($path))
        if((Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant() -cne $pin.Value){throw 'G0_SOURCE_HASH'}
    }
    . (Join-Path $data.Tools 'G0Attestation.ps1')
    Invoke-G0HelperScript (Join-Path $data.Tools 'Invoke-G0PrivilegedAttestation.ps1') $data.Request $data.Hash
} catch {
    $reason='G0_BOOTSTRAP_FAILED'
    if($_.Exception.ToString() -cmatch '\b(G0_[A-Z0-9_]+)\b'){$reason=$Matches[1]}
    [Console]::Error.WriteLine('BLOCKED:'+$reason);exit 2
} finally {for($i=$held.Count-1;$i -ge 0;$i--){$held[$i].Dispose()}}
'@
    return [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($code.Replace('__PAYLOAD__',$payload)))
}
function Start-G0ElevatedHelper([string]$PowerShell,[string]$Arguments,$Request,[long]$Start,[Diagnostics.Stopwatch]$Watch) {
    # RunAs may wait for consent. Keep the caller deadline live while ShellExecute waits.
    $launch=[PowerShell]::Create()
    $null=$launch.AddScript({param($exe,$arguments)
        $ErrorActionPreference='Stop'
        $process=Start-Process -FilePath $exe -ArgumentList $arguments -Verb RunAs -WindowStyle Hidden -PassThru
        $handle=$process.Handle
        if($handle -eq [IntPtr]::Zero){throw 'G0_HELPER_HANDLE'}
        $process
    }).AddArgument($PowerShell).AddArgument($Arguments)
    $pending=$launch.BeginInvoke()
    try {
        while(!$pending.IsCompleted){
            Assert-G0Request $Request (Get-G0ValidationTime $Start $Watch.Elapsed ([DateTime]::UtcNow.ToFileTimeUtc()))
            Start-Sleep -Milliseconds 100
        }
        try {$output=$launch.EndInvoke($pending)} catch {
            $cause=$_.Exception
            while($cause){if($cause -is [ComponentModel.Win32Exception] -and $cause.NativeErrorCode -eq 1223){throw 'G0_UAC_CANCELLED'};$cause=$cause.InnerException}
            throw 'G0_RUNAS_UNAVAILABLE'
        }
        if($launch.HadErrors -or $output.Count -ne 1 -or $output[0] -isnot [Diagnostics.Process]){throw 'G0_RUNAS_UNAVAILABLE'}
        return $output[0]
    } finally {
        if($pending.IsCompleted){$launch.Dispose()}else{$null=$launch.BeginStop($null,$null)}
    }
}
function Write-G0Blocked($Failure) {
    $reason='G0_OPERATION_FAILED'
    if($Failure.Exception.ToString() -cmatch '\b(G0_[A-Z0-9_]+)\b'){$reason=$Matches[1]}
    [Console]::Error.WriteLine('BLOCKED:'+$reason)
}
