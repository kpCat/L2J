[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('start','attest','stop')][string]$Action)

$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..')).TrimEnd('\')
$root=Join-Path (Split-Path $repo -Parent) 'sql-first-party'
$data=Join-Path $root 'data'
$config=Join-Path $root 'owned-server.ini'
$receipt=Join-Path $root 'owned-process.json'
$bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
$daemon=Join-Path $bin 'mariadbd.exe'
$install=Join-Path $bin 'mariadb-install-db.exe'
$admin=Join-Path $bin 'mariadb-admin.exe'
$port=33306
. (Join-Path $PSScriptRoot 'G0SqlIdentity.ps1')
Add-Type -Path (Join-Path $PSScriptRoot 'Win32SqlIdentity.cs')
if([Win32SqlIdentity]::CurrentTokenElevated()){throw 'TEST130_ORDINARY_TOKEN_REQUIRED'}

if($Action -eq 'start'){
    foreach($file in @($daemon,$install,$admin)){Assert-G0PhysicalFile $file}
    if((Get-Item -LiteralPath $daemon).VersionInfo.ProductVersion -ne '11.4.13.0'){throw 'TEST130_MARIADB_VERSION'}
    if(Test-Path -LiteralPath $root){throw 'TEST130_SQL_ROOT_ALREADY_EXISTS'}
    if(@([Win32SqlIdentity]::Listeners($port)).Count){throw 'TEST130_SQL_PORT_BUSY'}
    $user=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($user); $acl.SetAccessRuleProtection($true,$false)
    foreach($sid in @($user.Value,'S-1-5-18')){
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    $null=[IO.Directory]::CreateDirectory($root,$acl)
    $password=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
    $utf8=[Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllLines((Join-Path $root 'admin.jdbc.ini'),@("URL=jdbc:mysql://127.0.0.1:$port/mysql",'Login=root',"Password=$password"),$utf8)
    [IO.File]::WriteAllLines((Join-Path $root 'admin.client.ini'),@('[client]','host=127.0.0.1',"port=$port",'user=root',"password=$password"),$utf8)
    $initializeArgs=@{FilePath=$install; ArgumentList=@(('--datadir="'+$data+'"'),"--port=$port","--password=$password",'--silent');
        WindowStyle='Hidden'; Wait=$true; PassThru=$true; RedirectStandardOutput=(Join-Path $root 'install.stdout.log'); RedirectStandardError=(Join-Path $root 'install.stderr.log')}
    $initialize=Start-Process @initializeArgs
    if($initialize.ExitCode -ne 0 -or !(Test-Path -LiteralPath (Join-Path $data 'mysql') -PathType Container)){throw 'TEST130_SQL_INSTALL_FAILED'}
    [IO.File]::WriteAllLines($config,@('[mysqld]','bind-address=127.0.0.1',"port=$port",('datadir="'+$data.Replace('\','/')+'"'),'default-time-zone=+00:00'),$utf8)
    $serverArgs=@{FilePath=$daemon; ArgumentList=@(('"--defaults-file='+$config+'"'),'--console'); WorkingDirectory=$root;
        WindowStyle='Hidden'; PassThru=$true; RedirectStandardOutput=(Join-Path $root 'server.stdout.log'); RedirectStandardError=(Join-Path $root 'server.stderr.log')}
    $server=Start-Process @serverArgs
    $native=[Win32SqlIdentity]::Process([uint32]$server.Id,0x1000)
    if(!$native.Success -or $native.ImagePath -ine $daemon){throw 'TEST130_SQL_START_IDENTITY'}
    @{pid=$server.Id;created=$native.CreatedFileTime} | ConvertTo-Json | Set-Content -LiteralPath $receipt -Encoding UTF8
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while(!@([Win32SqlIdentity]::Listeners($port)).Count -and !$server.HasExited -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 200}
}

$owned=Get-Content -LiteralPath $receipt -Raw | ConvertFrom-Json
$attestation=@{Executable=$daemon; Config=$config; Port=$port; OwnedPid=$owned.pid; OwnedCreated=$owned.created; OwnedDataDirectory=$data}
& (Join-Path $PSScriptRoot 'Invoke-G0IdentityAttestation.ps1') @attestation
if($LASTEXITCODE){exit $LASTEXITCODE}
if($Action -eq 'stop'){
    & $admin ('--defaults-file='+(Join-Path $root 'admin.client.ini')) shutdown
    if($LASTEXITCODE){exit $LASTEXITCODE}
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while([Win32SqlIdentity]::Process([uint32]$owned.pid,0x1000).Success -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 200}
    if(@([Win32SqlIdentity]::Listeners($port)).Count -or [Win32SqlIdentity]::Process([uint32]$owned.pid,0x1000).Success){throw 'TEST130_SQL_STOP_INCOMPLETE'}
    Write-Output "TEST130_OWN_SQL_STOP pid=$($owned.pid); port=$port"
}
