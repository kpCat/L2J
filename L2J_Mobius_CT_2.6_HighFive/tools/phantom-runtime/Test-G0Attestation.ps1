[CmdletBinding()]
param([string]$OutputRoot = (Join-Path ([IO.Path]::GetTempPath()) ('Phantom-G0-tests-' + [guid]::NewGuid().ToString('N'))))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'G0Attestation.ps1'))) { throw 'TEST_RED_RECEIPT_CONTRACT_MISSING' }
. (Join-Path $PSScriptRoot 'G0Attestation.ps1')
function Check([bool]$Condition,[string]$Name) { if (!$Condition) { throw ('TEST_FAILED:'+$Name) } }
function Reject([scriptblock]$Action,[string]$Reason) {
    $rejected=$false
    try { & $Action | Out-Null } catch { if ($_.Exception.Message -notlike ('*'+$Reason+'*')) { throw }; $rejected=$true }
    Check $rejected ('FALSE_ACCEPT:'+ $Reason)
}
function RejectAccess([scriptblock]$Action) {
    $rejected=$false
    try {& $Action | Out-Null} catch {$cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException};if(($cause.HResult -band 65535) -notin @(5,32)){throw};$rejected=$true}
    Check $rejected 'FILE_ACCESS_FALSE_ACCEPT'
}
$expected=[pscustomobject]@{ServiceName='MariaDB';ServiceAccount='NT SERVICE\MariaDB';Executable='C:\Approved\bin\mysqld.exe';Config='C:\Approved\data\my.ini'}
$command='"C:\Approved\bin\mysqld.exe" "--defaults-file=C:\Approved\data\my.ini" "MariaDB"'
$baseline=@{
 TokenElevated=$false;ServiceName='MariaDB';ServiceState=4;ServiceType=16;ServiceAccount='NT SERVICE\MariaDB';ServicePid=123;AfterServicePid=123;AfterServiceState=4;AfterServiceType=16;AfterServiceAccount='NT SERVICE\MariaDB'
 ListenerPids=@(123);ListenerKey='127.0.0.1:3308:123';AfterListenerKey='127.0.0.1:3308:123';RegisteredCommand=$command;AfterRegisteredCommand=$command;PhysicalPaths=$true
 BinaryHash=('A'*64);AfterBinaryHash=('A'*64);ConfigHash=('B'*64);AfterConfigHash=('B'*64);NativeSuccess=$true;NativeBeforeSuccess=$true;NativeAfterSuccess=$true;NativeBeforeError=0;NativeAfterError=0;NativeError=0
 RuntimeImage=$expected.Executable;AfterRuntimeImage=$expected.Executable;NativeCreated=134000000000000009L;AfterNativeCreated=134000000000000009L;WmiCreated=134000000000000000L;AfterWmiCreated=134000000000000000L
 WmiImage='';AfterWmiImage='';RuntimeCommand=$command;AfterRuntimeCommand=$command;ProcessName='mysqld.exe'
}
function RejectFact($Changes,[string]$Reason) { $f=$baseline.Clone(); foreach($key in $Changes.Keys){$f[$key]=$Changes[$key]}; Reject { Assert-G0IdentityEvidence ([pscustomobject]$f) $expected } $Reason }
$accepted=Assert-G0IdentityEvidence ([pscustomobject]$baseline) $expected
Check ($accepted.Created -ceq [DateTime]::FromFileTimeUtc($baseline.NativeCreated).ToString('o')) 'EXACT_100NS'
Check ((Get-G0Hash 'abc') -ceq 'ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=') 'UTF8_SHA256_VECTOR'
RejectFact @{NativeSuccess=$false;NativeError=5} 'G0_RUNTIME_IMAGE_ACCESS_DENIED'
RejectFact @{RuntimeCommand=$null} 'G0_RUNTIME_COMMAND_UNAVAILABLE'
RejectFact @{AfterRuntimeCommand=$command+' '} 'G0_RUNTIME_COMMAND_CHANGED'
RejectFact @{ListenerPids=@(123,456)} 'G0_SCM_TCP_PID_MISMATCH'
RejectFact @{AfterServicePid=124} 'G0_IDENTITY_CHANGED_DURING_READ'
RejectFact @{AfterServiceState=3} 'G0_IDENTITY_CHANGED_DURING_READ'
RejectFact @{AfterListenerKey='127.0.0.1:3308:124'} 'G0_IDENTITY_CHANGED_DURING_READ'
RejectFact @{AfterNativeCreated=134000000000000008L} 'G0_PROCESS_CREATION_MISMATCH'
RejectFact @{WmiCreated=134000000000000010L} 'G0_PROCESS_CREATION_MISMATCH'
RejectFact @{WmiImage='C:\Foreign\mysqld.exe'} 'G0_WMI_NATIVE_IMAGE_CONFLICT'
RejectFact @{AfterBinaryHash=('C'*64)} 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'
RejectFact @{AfterConfigHash=('D'*64)} 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'
RejectFact @{ConfigHash='invalid'} 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'
RejectFact @{PhysicalPaths=$false} 'G0_PHYSICAL_FILE_IDENTITY_CHANGED'
RejectFact @{RegisteredCommand='unquoted';AfterRegisteredCommand='unquoted'} 'G0_AMBIGUOUS_SERVICE_COMMAND'
RejectFact @{RuntimeImage='C:\Foreign\mysqld.exe'} 'G0_RUNTIME_IMAGE_MISMATCH'
RejectFact @{RegisteredCommand=$command.Replace('Approved','Foreign');AfterRegisteredCommand=$command.Replace('Approved','Foreign')} 'G0_REGISTERED_PATH_NOT_ALLOWLISTED'
RejectFact @{TokenElevated=$true} 'G0_UNEXPECTED_ELEVATED_TOKEN'
$mixed=$baseline.Clone();$mixed.NativeSuccess=$false;$mixed.NativeAfterSuccess=$false;$mixed.NativeError=5
Reject {ConvertTo-G0Observation ([pscustomobject]$mixed) $expected} 'G0_NATIVE_VISIBILITY_CHANGED'
$differentErrors=$baseline.Clone();$differentErrors.NativeSuccess=$false;$differentErrors.NativeBeforeSuccess=$false;$differentErrors.NativeAfterSuccess=$false;$differentErrors.NativeError=5;$differentErrors.NativeBeforeError=5;$differentErrors.NativeAfterError=6
Reject {ConvertTo-G0Observation ([pscustomobject]$differentErrors) $expected} 'G0_NATIVE_VISIBILITY_CHANGED'
Write-Output 'PASS1 identity: exact FILETIME/hash vector/runtime command/SCM/TCP/race negatives'

$now=134000000000000000L
$fingerprint=Get-G0Hash 'facts'
$request=[ordered]@{Version='1';Nonce=('a'*64);Issued=[string]($now-10000000L);Expires=[string]($now+1100000000L);CallerSid='S-1-5-21-1-2-3-1001';CallerPid='42';CallerCreated='133999000000000001';ServiceName=$expected.ServiceName;ServiceAccount=$expected.ServiceAccount;Executable=$expected.Executable;Config=$expected.Config;Port='3308';Before=$fingerprint}
$observation=[ordered]@{Fingerprint=$fingerprint;Pid='123';CreatedMicro=[string](Get-G0Microseconds $baseline.NativeCreated);Binary=('A'*64);ConfigHash=('B'*64);RegisteredHash=(Get-G0Hash $command);ListenerKey=$baseline.ListenerKey;RuntimeHash=(Get-G0Hash $command);NativeCreated=[string]$baseline.NativeCreated;RuntimeImage=$expected.Executable}
$receipt=[ordered]@{Version='1';Nonce=$request.Nonce;RequestHash=(Get-G0Hash (ConvertTo-G0Document $request $script:G0RequestFields));CallerSid=$request.CallerSid;CallerPid=$request.CallerPid;CallerCreated=$request.CallerCreated;Issued=[string]$now;Expires=[string]($now+300000000L);Pid='123';Created=[string]$baseline.NativeCreated;RuntimeImage=$expected.Executable;Binary=('A'*64);ConfigHash=('B'*64);CommandHash=(Get-G0Hash $command);Fingerprint=$fingerprint;ListenerKey=$baseline.ListenerKey}
$requestHash=$receipt.RequestHash
$text=ConvertTo-G0Document $receipt $script:G0ReceiptFields
$decoded=ConvertFrom-G0Document $text $script:G0ReceiptFields
$null=Assert-G0Receipt $decoded $request $requestHash $observation $now
$effective=Get-G0ValidationTime $now ([TimeSpan]::FromSeconds(90)) ($now+200000000L)
Check ($effective -eq $now+900000000L) 'MONOTONIC_CLOCK_ROLLBACK_CLAMP'
Reject {Assert-G0Receipt $decoded $request $requestHash $observation $effective} 'G0_RECEIPT_TIME'
Reject {Get-G0ValidationTime $now ([TimeSpan]::FromSeconds(1)) ($now-1L)} 'G0_CLOCK_ROLLBACK'
function RejectReceipt($Changes,[string]$Reason) { $r=[ordered]@{};foreach($key in $receipt.Keys){$r[$key]=$receipt[$key]}; foreach($key in $Changes.Keys){$r[$key]=$Changes[$key]}; Reject {Assert-G0Receipt $r $request $requestHash $observation $now} $Reason }
RejectReceipt @{Nonce=('b'*64)} 'G0_RECEIPT_BINDING'
RejectReceipt @{RequestHash=('X'*44)} 'G0_RECEIPT_BINDING'
RejectReceipt @{CallerPid='43'} 'G0_RECEIPT_BINDING'
RejectReceipt @{CallerSid='S-1-5-21-1-2-3-1002'} 'G0_RECEIPT_BINDING'
RejectReceipt @{CallerCreated='133999000000000002'} 'G0_RECEIPT_BINDING'
RejectReceipt @{Issued=[string]($now+1L)} 'G0_RECEIPT_TIME'
RejectReceipt @{Expires=[string]$now} 'G0_RECEIPT_TIME'
RejectReceipt @{Expires=[string]($now+300000001L)} 'G0_RECEIPT_TIME'
RejectReceipt @{Created='134000000000000010'} 'G0_RECEIPT_IDENTITY'
RejectReceipt @{Created='1.34e17'} 'G0_RECEIPT_IDENTITY'
RejectReceipt @{CommandHash='not-sha256'} 'G0_RECEIPT_HASH'
RejectReceipt @{Binary=('D'*64)} 'G0_RECEIPT_IDENTITY'
RejectReceipt @{RuntimeImage='C:\Foreign\mysqld.exe'} 'G0_RECEIPT_IDENTITY'
RejectReceipt @{Fingerprint='changed'} 'G0_RECEIPT_IDENTITY'
$badRequest=[ordered]@{};foreach($key in $request.Keys){$badRequest[$key]=$request[$key]};$badRequest.Expires=[string]$now
Reject {Assert-G0Request $badRequest $now} 'G0_REQUEST_TIME'
$descriptor=[G0AttestationFile]::ReceiptSddl($request.CallerSid)
[G0AttestationFile]::AssertDescriptor($descriptor,$request.CallerSid)
Reject {[G0AttestationFile]::AssertDescriptor($descriptor.Replace('O:BA','O:'+$request.CallerSid),$request.CallerSid)} 'G0_RECEIPT_ACL'
Reject {[G0AttestationFile]::AssertDescriptor($descriptor.Replace('D:P','D:'),$request.CallerSid)} 'G0_RECEIPT_ACL'
Reject {[G0AttestationFile]::AssertDescriptor($descriptor.Replace('0x12008d','FA'),$request.CallerSid)} 'G0_RECEIPT_ACL'
Reject {[G0AttestationFile]::AssertDescriptor(($descriptor+'(A;;FR;;;WD)'),$request.CallerSid)} 'G0_RECEIPT_ACL'
Reject {ConvertFrom-G0Document ($text+"USED`n") $script:G0ReceiptFields} 'G0_DOCUMENT_FORMAT'
Reject {ConvertFrom-G0Document ($text+($text -split "`n")[1]+"`n") $script:G0ReceiptFields} 'G0_DOCUMENT_FORMAT'
Reject {ConvertFrom-G0Document ($text.Replace('Nonce:','Unknown:')) $script:G0ReceiptFields} 'G0_DOCUMENT_FORMAT'
Reject {ConvertFrom-G0Document ($text.Replace("`n","`r`n")) $script:G0ReceiptFields} 'G0_DOCUMENT_FORMAT'
$extra=[ordered]@{};foreach($key in $receipt.Keys){$extra[$key]=$receipt[$key]};$extra.Secret='never serialize'
Reject {ConvertTo-G0Document $extra $script:G0ReceiptFields} 'G0_DOCUMENT_FIELDS'
Write-Output 'PASS2 receipt: fresh positive/binding/expiry/identity/format/consumed negatives'

Check (![Win32SqlIdentity]::CurrentTokenElevated()) 'TEST_REQUIRES_ORDINARY_TOKEN'
Check ([Win32SqlIdentity]::CurrentIntegrityRid() -lt 12288) 'TEST_REQUIRES_ORDINARY_INTEGRITY'
Check (![Win32SqlIdentity]::CurrentDebugEnabled()) 'ORDINARY_DEBUG_DISABLED'
Assert-G0HelperExit 0
foreach($invalid in @($null,'0',2)){Reject {Assert-G0HelperExit $invalid} 'G0_HELPER_EXIT'}
$bootstrap=New-G0HelperBootstrap $PSScriptRoot 'C:\G0 with space\owner''s.request' ('a'*64) ([ordered]@{'Win32SqlIdentity.cs'=('b'*64)})
$decodedBootstrap=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($bootstrap))
$tokens=$null;$parseErrors=$null
$null=[Management.Automation.Language.Parser]::ParseInput($decodedBootstrap,[ref]$tokens,[ref]$parseErrors)
Check ($parseErrors.Count -eq 0) 'BOOTSTRAP_SYNTAX'
$saved=$ErrorActionPreference;$ErrorActionPreference='Continue'
try{$bootstrapOutput=& powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand $bootstrap 2>&1;$bootstrapExit=$LASTEXITCODE}finally{$ErrorActionPreference=$saved}
Check ($bootstrapExit -eq 2 -and ($bootstrapOutput -join "`n") -match 'BLOCKED:G0_HELPER_ELEVATION_REQUIRED') 'BOOTSTRAP_ORDINARY_REFUSAL'
Reject {[Win32SqlIdentity]::EnableDebug()} 'G0_HELPER_ELEVATION_REQUIRED'
$own=[Win32SqlIdentity]::Process([uint32]$PID,0x1000)
Check ($own.Success -and $own.CreatedFileTime -gt 0 -and $own.ImagePath) 'NATIVE_OWN_PROCESS'
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
try {
    $listener.Start();$localPort=([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $owners=@([Win32SqlIdentity]::Listeners($localPort))
    Check ($owners.Count -eq 1 -and $owners[0].OwningProcess -eq $PID -and $owners[0].LocalAddress -ceq '127.0.0.1') 'NATIVE_TCP_OWNER'
} finally {$listener.Stop()}
if(Test-Path -LiteralPath $OutputRoot){throw 'FRESH_TEST_ROOT_REQUIRED'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
# DELETE_CHILD on the parent overrides a child's missing DELETE right on Windows.
# Model the receipt store's non-deleting reader, even outside the Codex sandbox.
$rootAcl=[Security.AccessControl.DirectorySecurity]::new()
$rootAcl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid))
$rootAcl.SetAccessRuleProtection($true,$false)
$rootRights=[Security.AccessControl.FileSystemRights]([int][Security.AccessControl.FileSystemRights]::FullControl -band (-bnot 64))
$rootAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),$rootRights,'ContainerInherit,ObjectInherit','None','Allow'))
$null=[IO.Directory]::CreateDirectory($OutputRoot,$rootAcl)
$requestFixture=Join-Path $OutputRoot 'cleanup.request'
$requestAcl=[Security.AccessControl.FileSecurity]::new();$requestAcl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid));$requestAcl.SetAccessRuleProtection($true,$false)
foreach($trustee in @($sid,'S-1-5-18')){$requestAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','Allow'))}
$create=[IO.FileStream]::new($requestFixture,[IO.FileMode]::CreateNew,[Security.AccessControl.FileSystemRights]::FullControl,[IO.FileShare]::None,4096,[IO.FileOptions]::None,$requestAcl)
try{$requestBytes=[Text.Encoding]::UTF8.GetBytes('request fixture');$create.Write($requestBytes,0,$requestBytes.Length)}finally{$create.Dispose()}
$requestLock=[IO.FileStream]::new($requestFixture,[IO.FileMode]::Open,([Security.AccessControl.FileSystemRights]::Read -bor [Security.AccessControl.FileSystemRights]::Delete),[IO.FileShare]::Read,4096,[IO.FileOptions]::DeleteOnClose)
try {
    Check ([G0AttestationFile]::ReadRequest($requestFixture,$sid) -ceq 'request fixture') 'LOCKED_REQUEST_READABLE'
    RejectAccess {$s=[IO.File]::Open($requestFixture,[IO.FileMode]::Open,[IO.FileAccess]::Write);$s.Dispose()}
} finally {$requestLock.Dispose()}
Check (![IO.File]::Exists($requestFixture)) 'REQUEST_DELETE_ON_CLOSE'
$exitFixture=Join-Path $OutputRoot 'helper-exit.ps1'
[IO.File]::WriteAllText($exitFixture,'param($RequestPath,$RequestSha256) Write-Output "ATTESTATION_ISSUED"; exit 2')
Reject {Invoke-G0HelperScript $exitFixture 'fixture' ('a'*64)} 'G0_HELPER_EXIT'
[IO.File]::WriteAllText($exitFixture,'param($RequestPath,$RequestSha256) exit 0')
Invoke-G0HelperScript $exitFixture 'fixture' ('a'*64)
[IO.File]::WriteAllText($exitFixture,'param($RequestPath,$RequestSha256) Write-Output "ATTESTATION_ISSUED"')
Reject {Invoke-G0HelperScript $exitFixture 'fixture' ('a'*64)} 'G0_HELPER_EXIT'
$file=Join-Path $OutputRoot 'append.receipt'
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$acl=New-Object Security.AccessControl.FileSecurity
$acl.SetAccessRuleProtection($true,$false)
$acl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid))
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),[Security.AccessControl.FileSystemRights]0x12008d,'Allow'))
$bytes=[Text.Encoding]::UTF8.GetBytes($text)
$stream=[IO.FileStream]::new($file,[IO.FileMode]::CreateNew,[Security.AccessControl.FileSystemRights]::FullControl,[IO.FileShare]::None,4096,[IO.FileOptions]::None,$acl)
try{$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
$locked=[G0AttestationFile]::Open($file)
try {
    Reject {$locked.AssertAcl($sid)} 'G0_RECEIPT_ACL'
    Check ($locked.ReadText() -ceq $text) 'HANDLE_READ'
    Reject {[G0AttestationFile]::Open($file)} 'G0_FILE_OPEN_DENIED'
    $locked.Consume()
} finally {$locked.Dispose()}
Check ([IO.File]::ReadAllText($file) -ceq ($text+"USED`n")) 'APPEND_CONSUME'
Reject {ConvertFrom-G0Document ([IO.File]::ReadAllText($file)) $script:G0ReceiptFields} 'G0_DOCUMENT_FORMAT'
RejectAccess {$s=[IO.File]::Open($file,[IO.FileMode]::Truncate,[IO.FileAccess]::Write);$s.Dispose()}
RejectAccess {[IO.File]::Delete($file)}
Check ([IO.File]::ReadAllText($file) -ceq ($text+"USED`n")) 'DELETE_REFUSAL_PRESERVES_RECEIPT'
$target=Join-Path $OutputRoot 'physical';New-Item -ItemType Directory -Path $target|Out-Null
[IO.File]::WriteAllText((Join-Path $target 'probe.txt'),'fixture')
$physical=Join-Path $target 'probe.txt'
$pin=[G0AttestationFile]::PinFile($physical)
try {RejectAccess {$s=[IO.File]::Open($physical,[IO.FileMode]::Open,[IO.FileAccess]::Write);$s.Dispose()}} finally {$pin.Dispose()}
$alias=Join-Path $target 'alias.txt'
New-Item -ItemType HardLink -Path $alias -Target $physical | Out-Null
try {Reject {[G0AttestationFile]::PinFile($physical)} 'G0_REPARSE_OR_ALIAS'} finally {[IO.File]::Delete($alias)}
$junction=Join-Path $OutputRoot 'linked'
New-Item -ItemType Junction -Path $junction -Target $target | Out-Null
try {Reject {Assert-G0PhysicalFile (Join-Path $junction 'probe.txt')} 'G0_REPARSE_PATH'} finally {[IO.Directory]::Delete($junction)}
$helper=Join-Path $PSScriptRoot 'Invoke-G0PrivilegedAttestation.ps1'
$saved=$ErrorActionPreference;$ErrorActionPreference='Continue'
try {$output=& powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $helper -RequestPath 'C:\missing.request' -RequestSha256 ('a'*64) 2>&1;$helperExit=$LASTEXITCODE} finally {$ErrorActionPreference=$saved}
Check ($helperExit -eq 2 -and ($output -join "`n") -match 'BLOCKED:G0_HELPER_ELEVATION_REQUIRED') 'NON_ELEVATED_HELPER_REFUSAL'
$ownerDoc=Join-Path $PSScriptRoot '..\..\docs\phantoms\tasks\PHANTOM-G0-PRIVILEGED-ATTESTATION\OWNER_RUN.md'
$ownerText=[IO.File]::ReadAllText($ownerDoc,[Text.Encoding]::UTF8)
$blocks=[regex]::Matches($ownerText,'(?ms)```powershell\r?\n(.*?)\r?\n```')
Check ($blocks.Count -eq 1) 'ONE_OWNER_COMMAND'
foreach($block in $blocks){$tokens=$null;$parseErrors=$null;$null=[Management.Automation.Language.Parser]::ParseInput($block.Groups[1].Value,[ref]$tokens,[ref]$parseErrors);Check ($parseErrors.Count -eq 0) 'OWNER_COMMAND_SYNTAX'}
Write-Output 'PASS3 Windows: own native read/non-admin refusal/junction/real append-only handle; trusted admin receipt NOT_RUN'
Write-Output 'G0_ATTESTATION_TESTS_PASS focused3; SQL=0;GameServer=0;elevated=0'
