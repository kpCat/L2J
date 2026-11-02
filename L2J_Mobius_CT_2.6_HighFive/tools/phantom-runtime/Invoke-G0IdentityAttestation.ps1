[CmdletBinding()]
param(
    [string]$ServiceName='MariaDB',
    [string]$ServiceAccount='NT SERVICE\MariaDB',
    [string]$Executable=(Join-Path $env:ProgramFiles 'MariaDB 11.4\bin\mysqld.exe'),
    [string]$Config=(Join-Path $env:ProgramFiles 'MariaDB 11.4\data\my.ini'),
    [ValidateRange(1,65535)][int]$Port=3308,
    [string]$RequestDirectory=(Join-Path ([IO.Path]::GetTempPath()) ('Phantom-G0-request-'+[guid]::NewGuid().ToString('N'))),
    [switch]$CheckOnly
)
$ErrorActionPreference='Stop'
$resources=[Collections.Generic.List[IDisposable]]::new()
$requestRoot=$null;$requestCreated=$false;$helper=$null;$failed=$false;$result=$null
try {
    . (Join-Path $PSScriptRoot 'G0Attestation.ps1')
    if([Win32SqlIdentity]::CurrentTokenElevated()){throw 'G0_UNEXPECTED_ELEVATED_TOKEN'}
    if([Win32SqlIdentity]::CurrentIntegrityRid() -ge 12288 -or [Win32SqlIdentity]::CurrentDebugEnabled()){throw 'G0_ORDINARY_TOKEN_REQUIRED'}
    $lua=Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System' -Name EnableLUA -ErrorAction Stop
    if($lua.EnableLUA -ne 1){throw 'G0_UAC_DISABLED'}
    Write-Host ('UAC EnableLUA='+$lua.EnableLUA+';callerElevated=False;integrityRid='+[Win32SqlIdentity]::CurrentIntegrityRid())
    $pins=[ordered]@{}
    foreach($name in @('Win32SqlIdentity.cs','G0SqlIdentity.ps1','G0Attestation.ps1','Invoke-G0PrivilegedAttestation.ps1','Invoke-G0IdentityAttestation.ps1','Test-G0Attestation.ps1')){
        $source=Join-Path $PSScriptRoot $name
        $resources.Add([G0AttestationFile]::PinParents($source,$false))
        $resources.Add([G0AttestationFile]::PinFile($source))
        $pins[$name]=(Get-FileHash -LiteralPath $source -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    }
    $ps51=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    & $ps51 -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Test-G0Attestation.ps1')
    Assert-G0HelperExit $LASTEXITCODE
    if($CheckOnly){Write-Host 'G0_CHECKS_PASS;focused3;RunAs=0;SQL=0;GameServer=0';return}
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    $sid=$identity.User.Value
    $requestRoot=[G0AttestationFile]::Canonical($RequestDirectory)
    $repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..')).TrimEnd('\')
    if($requestRoot -ieq $repo -or $requestRoot.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'G0_REQUEST_OUTSIDE_CHECKOUT_REQUIRED'}
    if(Test-Path -LiteralPath $requestRoot){throw 'G0_FRESH_REQUEST_DIRECTORY_REQUIRED'}
    $resources.Add([G0AttestationFile]::PinParents($requestRoot,$false))
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($identity.User);$acl.SetAccessRuleProtection($true,$false)
    foreach($trustee in @($sid,'S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    $null=[IO.Directory]::CreateDirectory($requestRoot,$acl);$requestCreated=$true
    $expected=[pscustomobject]@{ServiceName=$ServiceName;ServiceAccount=$ServiceAccount;Executable=$Executable;Config=$Config}
    $observation=Get-G0Observation $expected $Port
    $own=[Win32SqlIdentity]::Process([uint32]$PID,0x1000)
    if(!$own.Success){throw 'G0_REQUEST_CALLER'}
    $nonceBytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($nonceBytes)}finally{$rng.Dispose()}
    $nonce=[BitConverter]::ToString($nonceBytes).Replace('-','').ToLowerInvariant()
    $watch=[Diagnostics.Stopwatch]::StartNew();$start=[DateTime]::UtcNow.ToFileTimeUtc()
    $request=[ordered]@{Version='1';Nonce=$nonce;Issued=[string]$start;Expires=[string]($start+1200000000L);CallerSid=$sid;CallerPid=[string]$PID;CallerCreated=[string]$own.CreatedFileTime;ServiceName=$ServiceName;ServiceAccount=$ServiceAccount;Executable=$Executable;Config=$Config;Port=[string]$Port;Before=$observation.Fingerprint}
    Assert-G0Request $request $start
    $text=ConvertTo-G0Document $request $script:G0RequestFields
    $path=Join-Path $requestRoot 'identity.request'
    $fileAcl=[Security.AccessControl.FileSecurity]::new();$fileAcl.SetOwner($identity.User);$fileAcl.SetAccessRuleProtection($true,$false)
    foreach($trustee in @($sid,'S-1-5-18')){$fileAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','Allow'))}
    $create=[IO.FileStream]::new($path,[IO.FileMode]::CreateNew,[Security.AccessControl.FileSystemRights]::FullControl,[IO.FileShare]::None,4096,[IO.FileOptions]::None,$fileAcl)
    try{$bytes=[Text.Encoding]::UTF8.GetBytes($text);$create.Write($bytes,0,$bytes.Length);$create.Flush($true)}finally{$create.Dispose()}
    # Delete-on-close owns cleanup even if this caller crashes. Readers share DELETE,
    # but cannot write/replace this still-open request.
    $resources.Add([IO.FileStream]::new($path,[IO.FileMode]::Open,([Security.AccessControl.FileSystemRights]::Read -bor [Security.AccessControl.FileSystemRights]::Delete),[IO.FileShare]::Read,4096,[IO.FileOptions]::DeleteOnClose))
    $resources.Add([G0AttestationFile]::PinParents($path,$false))
    if([G0AttestationFile]::ReadRequest($path,$sid) -cne $text){throw 'G0_REQUEST_CHANGED'}
    $bootstrap=New-G0HelperBootstrap $PSScriptRoot $path (Get-G0HexHash $text) $pins
    Write-Host 'ATTESTATION_RUNNING;maxSeconds=120;SQL=0;GameServer=0'
    $helper=Start-G0ElevatedHelper $ps51 ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand '+$bootstrap) $request $start $watch
    $helperHandle=$helper.Handle
    if($helperHandle -eq [IntPtr]::Zero){throw 'G0_HELPER_HANDLE'}
    while(!$helper.WaitForExit(100)){
        Assert-G0Request $request (Get-G0ValidationTime $start $watch.Elapsed ([DateTime]::UtcNow.ToFileTimeUtc()))
    }
    Assert-G0HelperExit $helper.ExitCode
    Assert-G0Request $request (Get-G0ValidationTime $start $watch.Elapsed ([DateTime]::UtcNow.ToFileTimeUtc()))
    $root=Get-G0ReceiptRoot $sid;$receiptPath=Join-Path $root ($nonce+'.receipt')
    if(!(Test-Path -LiteralPath $receiptPath -ErrorAction Stop)){throw 'G0_RECEIPT_MISSING'}
    $resources.Add([G0AttestationFile]::PinParents($receiptPath,$true))
    $resources.Add([G0AttestationFile]::CheckDirectory((Split-Path $root -Parent),$sid,$true))
    $resources.Add([G0AttestationFile]::CheckDirectory($root,$sid,$false))
    $file=[G0AttestationFile]::Open($receiptPath);$resources.Add($file)
    $file.AssertAcl($sid)
    $receiptText=$file.ReadText()
    $file.Consume()
    $receipt=ConvertFrom-G0Document $receiptText $script:G0ReceiptFields
    $before=Get-G0Observation $expected $Port
    $null=Assert-G0Receipt $receipt $request (Get-G0Hash $text) $before (Get-G0ValidationTime $start $watch.Elapsed ([DateTime]::UtcNow.ToFileTimeUtc()))
    $after=Get-G0Observation $expected $Port
    if($watch.Elapsed.TotalSeconds -ge 120 -or [DateTime]::UtcNow.ToFileTimeUtc() -lt $start){throw 'G0_REQUEST_EXPIRED'}
    $result=Assert-G0Receipt $receipt $request (Get-G0Hash $text) $after (Get-G0ValidationTime $start $watch.Elapsed ([DateTime]::UtcNow.ToFileTimeUtc()))
    $file.AssertAcl($sid)
} catch {
    $failed=$true
    if(Get-Command Write-G0Blocked -ErrorAction SilentlyContinue){Write-G0Blocked $_} else {[Console]::Error.WriteLine('BLOCKED:G0_INITIALIZATION_FAILED')}
} finally {
    if($null -ne $helper){$helper.Dispose()}
    for($i=$resources.Count-1;$i -ge 0;$i--){try{$resources[$i].Dispose()}catch{$failed=$true;[Console]::Error.WriteLine('BLOCKED:G0_CLEANUP_FAILED')}}
    if($requestCreated){try{[IO.Directory]::Delete($requestRoot,$false)}catch{$failed=$true;[Console]::Error.WriteLine('BLOCKED:G0_CLEANUP_FAILED')}}
}
if($failed){exit 2}
if($null -ne $result){
    Write-Output ([pscustomobject]$result)
    Write-Host 'G0_ATTESTATION_PASS;OS_IDENTITY_ATTESTED;G0=NOT_ACCEPTED;SQL=0;GameServer=0;TASK114=0'
}
