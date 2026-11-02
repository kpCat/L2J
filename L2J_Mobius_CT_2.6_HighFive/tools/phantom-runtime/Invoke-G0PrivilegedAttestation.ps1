[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RequestPath,[Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$RequestSha256)
$ErrorActionPreference='Stop'
$resources=[Collections.Generic.List[IDisposable]]::new()
try {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    $principal=[Security.Principal.WindowsPrincipal]::new($identity)
    if(!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'G0_HELPER_ELEVATION_REQUIRED'}
    . (Join-Path $PSScriptRoot 'G0Attestation.ps1')
    if(![Win32SqlIdentity]::CurrentTokenElevated()){throw 'G0_HELPER_ELEVATION_REQUIRED'}
    $integrity=[Win32SqlIdentity]::CurrentIntegrityRid()
    if($integrity -lt 12288){throw 'G0_HELPER_INTEGRITY_REQUIRED'}
    $debugLease=[Win32SqlIdentity]::EnableDebug();$resources.Add($debugLease)
    if(![Win32SqlIdentity]::CurrentDebugEnabled()){throw 'G0_DEBUG_PRIVILEGE_UNAVAILABLE'}
    $resources.Add([G0AttestationFile]::PinParents($RequestPath,$false))
    $text=[G0AttestationFile]::ReadRequest($RequestPath,$identity.User.Value)
    if((Get-G0HexHash $text) -cne $RequestSha256){throw 'G0_REQUEST_HASH'}
    $request=ConvertFrom-G0Document $text $script:G0RequestFields
    Assert-G0Request $request ([DateTime]::UtcNow.ToFileTimeUtc())
    if($request.CallerSid -cne $identity.User.Value){throw 'G0_REQUEST_CALLER'}
    $caller=[Win32SqlIdentity]::Hold([uint32]$request.CallerPid);$resources.Add($caller)
    $callerFact=[Win32SqlIdentity]::Read($caller,[uint32]$request.CallerPid)
    if([string]$callerFact.CreatedFileTime -cne $request.CallerCreated){throw 'G0_REQUEST_CALLER'}
    $callerCim=Get-CimInstance Win32_Process -Filter ('ProcessId='+$request.CallerPid) -ErrorAction Stop
    if(!$callerCim -or (Invoke-CimMethod -InputObject $callerCim -MethodName GetOwnerSid -ErrorAction Stop).Sid -cne $request.CallerSid){throw 'G0_REQUEST_CALLER'}
    $expected=[pscustomobject]@{ServiceName=$request.ServiceName;ServiceAccount=$request.ServiceAccount;Executable=$request.Executable;Config=$request.Config}
    $service=[Win32SqlIdentity]::Service($expected.ServiceName)
    $server=[Win32SqlIdentity]::Hold($service.Pid);$resources.Add($server)
    $held=[Win32SqlIdentity]::Read($server,$service.Pid)
    $fact=Get-G0PinnedEvidence $expected ([int]$request.Port)
    $strict=Assert-G0IdentityEvidence $fact $expected $true
    if($strict.PID -ne $service.Pid -or $fact.NativeCreated -ne $held.CreatedFileTime -or $held.ImagePath -ine $fact.RuntimeImage){throw 'G0_HELD_PROCESS_CHANGED'}
    $observation=ConvertTo-G0Observation $fact $expected
    if($observation.Fingerprint -cne $request.Before){throw 'G0_INDEPENDENT_FACTS_CHANGED'}
    $now=[DateTime]::UtcNow.ToFileTimeUtc()
    Assert-G0Request $request $now
    $expires=[Math]::Min($now+300000000L,[long]$request.Expires)
    $receipt=[ordered]@{Version='1';Nonce=$request.Nonce;RequestHash=(Get-G0Hash $text);CallerSid=$request.CallerSid;CallerPid=$request.CallerPid;CallerCreated=$request.CallerCreated;Issued=[string]$now;Expires=[string]$expires;Pid=[string]$strict.PID;Created=[string]$fact.NativeCreated;RuntimeImage=$fact.RuntimeImage;Binary=$fact.BinaryHash;ConfigHash=$fact.ConfigHash;CommandHash=$strict.CommandHash;Fingerprint=$observation.Fingerprint;ListenerKey=$observation.ListenerKey}
    # Re-read bound request and held processes before the only privileged output.
    if([G0AttestationFile]::ReadRequest($RequestPath,$request.CallerSid) -cne $text -or [Win32SqlIdentity]::Read($caller,[uint32]$request.CallerPid).CreatedFileTime -ne $callerFact.CreatedFileTime -or [Win32SqlIdentity]::Read($server,$service.Pid).CreatedFileTime -ne $held.CreatedFileTime){throw 'G0_HELD_PROCESS_CHANGED'}
    $root=Get-G0ReceiptRoot $request.CallerSid
    $resources.Add([G0AttestationFile]::PinParents((Join-Path ([Environment]::GetFolderPath('CommonApplicationData')) 'guard'),$true))
    $resources.Add([G0AttestationFile]::EnsureDirectory((Split-Path $root -Parent),$request.CallerSid,$true))
    $resources.Add([G0AttestationFile]::EnsureDirectory($root,$request.CallerSid,$false))
    Assert-G0Request $request ([DateTime]::UtcNow.ToFileTimeUtc())
    $debugLease.Dispose()
    [G0AttestationFile]::Create((Join-Path $root ($request.Nonce+'.receipt')),(ConvertTo-G0Document $receipt $script:G0ReceiptFields),$request.CallerSid)
    Write-Output ('ATTESTATION_ISSUED;elevated=True;integrityRid='+$integrity+';debugPrivilegeVerified=True;validSeconds<=30;G0=NOT_ACCEPTED')
} catch {
    if(Get-Command Write-G0Blocked -ErrorAction SilentlyContinue){Write-G0Blocked $_} else {[Console]::Error.WriteLine('BLOCKED:G0_HELPER_ELEVATION_REQUIRED')}
    exit 2
} finally {for($i=$resources.Count-1;$i -ge 0;$i--){$resources[$i].Dispose()}}
exit 0
