[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Start','Attest','Stop')][string]$Action,
    [ValidateScript({$_ -cmatch '^[a-z][a-z0-9-]{0,23}$'})][string]$LaneId='native'
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'

. (Join-Path $PSScriptRoot 'G0SqlIdentity.ps1')
if(-not ('Win32SqlIdentity' -as [type])){Add-Type -Path (Join-Path $PSScriptRoot 'Win32SqlIdentity.cs')}
if([Win32SqlIdentity]::CurrentTokenElevated()){throw 'TASK131_SQL_ORDINARY_TOKEN_REQUIRED'}

$moduleRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repoRoot=Split-Path $moduleRoot -Parent
$logRoot=Join-Path $repoRoot '.phantom-local\logs\TASK131'
$lanes=Join-Path $logRoot 'sql-lanes'
$lane=Join-Path $lanes $LaneId
$root=Join-Path $lane 'sql-task131'
$data=Join-Path $root 'data'
$config=Join-Path $root 'owned-server.ini'
$binding=Join-Path $root 'schema-ready.json'
$state=Join-Path $root 'owned-process.json'
$adminConfig=Join-Path $root 'admin.client.ini'
$runtimeConfig=Join-Path $root 'runtime.client.ini'
$jdbcConfig=Join-Path $root 'Database.ini'
$port=33331
$schema='l2jmobiush5_phantom_test_131'
$schemaGrant=[string][char]96+$schema.Replace('_','\_')+[char]96
$login='task131_runtime'
$jdbcUrl="jdbc:mysql://127.0.0.1:$port/$schema"+'?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000'
$bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
$daemon=Join-Path $bin 'mariadbd.exe'
$install=Join-Path $bin 'mariadb-install-db.exe'
$client=Join-Path $bin 'mariadb.exe'
$admin=Join-Path $bin 'mariadb-admin.exe'
$utf8=[Text.UTF8Encoding]::new($false,$true)

function Assert-Task131PhysicalDirectory([string]$Path) {
    $directory=Get-Item -Force -LiteralPath $Path
    if(!$directory.PSIsContainer -or $directory.FullName -ine [IO.Path]::GetFullPath($Path)){throw 'TASK131_SQL_DIRECTORY'}
    for($part=$directory;$null -ne $part;$part=$part.Parent){
        if($part.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK131_SQL_REPARSE_PATH'}
    }
}

function Assert-Task131OwnerAcl {
    Assert-Task131PhysicalDirectory $root
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $acl=Get-Acl -LiteralPath $root
    $rules=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]))
    if(!$acl.AreAccessRulesProtected -or $acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -cne $sid -or
        $rules.Count -ne 2 -or @($rules|Where-Object {$_.IdentityReference.Value -cnotin @($sid,'S-1-5-18') -or
            $_.FileSystemRights -ne 'FullControl' -or $_.AccessControlType -ne 'Allow'}).Count){
        throw 'TASK131_SQL_ROOT_ACL'
    }
}

function Write-Task131Receipt([string]$Name,$Body) {
    Assert-Task131PhysicalDirectory $root
    $target=Join-Path $root $Name
    $pending=Join-Path $root ($Name+'.'+[Guid]::NewGuid().ToString('N')+'.pending')
    [IO.File]::WriteAllText($pending,($Body|ConvertTo-Json -Depth 7),$utf8)
    if(Test-Path -LiteralPath $target){
        Assert-G0PhysicalFile $target
        [IO.File]::Replace($pending,$target,[NullString]::Value)
    } else {
        [IO.File]::Move($pending,$target)
    }
}

function Read-Task131Receipt([string]$Path) {
    Assert-G0PhysicalFile $Path
    return [IO.File]::ReadAllText($Path,$utf8)|ConvertFrom-Json
}

function Get-Task131SqlIdentity($Owned) {
    if($Owned.lane -cne $LaneId -or $Owned.port -ne $port -or $Owned.schema -cne $schema){throw 'TASK131_SQL_STATE_SCOPE'}
    $fact=Get-G0OwnedSqlIdentity $Owned.pid $Owned.created $daemon $config $data $port
    if($fact.ConfigHash -cne $Owned.configHash -or $fact.Binary -cne $Owned.binary){throw 'TASK131_SQL_PIN_DRIFT'}
    return $fact
}

function Assert-Task131ClientConfig([string]$Path,[string]$UserName) {
    Assert-G0PhysicalFile $Path
    $lines=[IO.File]::ReadAllLines($Path,$utf8)
    if($lines.Length -ne 5 -or ($lines[0..3] -join '|') -cne ('[client]|host=127.0.0.1|port='+$port+'|user='+$UserName) -or
        $lines[4] -cnotmatch '^password=[0-9a-f]{64}$'){throw 'TASK131_SQL_CLIENT_OPTIONS'}
}

function Invoke-Task131Sql([string]$Options,[string]$UserName,[string]$Database,[string]$Sql,[string]$Failure,[switch]$ExpectDenied) {
    Assert-Task131ClientConfig $Options $UserName
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$client
    $start.Arguments='"--defaults-file='+$Options+'" --protocol=tcp --host=127.0.0.1 --port='+$port+' --user='+$UserName+
        ' --database='+$Database+' --default-character-set=utf8mb4 --connect-timeout=5 --local-infile=0 --batch --raw --skip-column-names --binary-mode'
    $start.WorkingDirectory=$root
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $start.StandardOutputEncoding=$utf8
    $start.StandardErrorEncoding=$utf8
    $process=[Diagnostics.Process]::Start($start)
    try {
        $stdout=$process.StandardOutput.ReadToEndAsync()
        $stderr=$process.StandardError.ReadToEndAsync()
        $bytes=$utf8.GetBytes($Sql)
        $write=$process.StandardInput.BaseStream.WriteAsync($bytes,0,$bytes.Length)
        $inputFailed=$false
        try {
            if(!$write.Wait(10000)){throw 'TASK131_SQL_INPUT_TIMEOUT'}
        } catch [Management.Automation.MethodInvocationException] {
            # The client can close stdin on SQL rejection. Preserve its exit and diagnostic.
            $inputFailed=$true
        }
        $process.StandardInput.Close()
        if(!$process.WaitForExit(45000)){throw 'TASK131_SQL_QUERY_TIMEOUT'}
        $errors=$stderr.GetAwaiter().GetResult()
        if($ExpectDenied){
            if($process.ExitCode -eq 0 -or $errors -notmatch '(?m)^ERROR 1142\b'){throw 'TASK131_SQL_FOREIGN_TABLE_GUARD'}
            return
        }
        if($process.ExitCode -ne 0){
            # Bootstrap failure output can include a credential statement. Keep only the server error code.
            $errorLine=[regex]::Match($errors,'(?m)^ERROR ([0-9]+)(?: \([0-9A-Z]+\))?(?: at line ([0-9]+))?')
            throw "$Failure (client=$($process.ExitCode) sql=$($errorLine.Groups[1].Value) line=$($errorLine.Groups[2].Value))"
        }
        if($inputFailed){throw 'TASK131_SQL_INPUT_INCOMPLETE'}
        return $stdout.GetAwaiter().GetResult().TrimEnd()
    } finally {
        if(!$process.HasExited){$process.Kill();$null=$process.WaitForExit(5000)}
        $process.Dispose()
    }
}

function Assert-Task131SqlQuery([string]$Options,[string]$UserName,[string]$Database,[string]$Principal) {
    $line=Invoke-Task131Sql $Options $UserName $Database 'SELECT DATABASE(),@@port,@@datadir,CURRENT_USER(),VERSION(),@@session.time_zone,@@global.bind_address;' 'TASK131_SQL_QUERY_IDENTITY'
    $fields=$line -split '\t'
    if($fields.Length -ne 7 -or $fields[0] -cne $Database -or $fields[1] -cne [string]$port -or
        [IO.Path]::GetFullPath($fields[2]).TrimEnd('\') -ine $data -or $fields[3] -cnotmatch $Principal -or
        $fields[4] -cnotmatch '^11\.4\.13-MariaDB(?:[-+]|$)' -or $fields[5] -cne '+00:00' -or $fields[6] -cne '127.0.0.1'){
        throw 'TASK131_SQL_QUERY_SCOPE'
    }
    return [ordered]@{host=$fields[6];port=[int]$fields[1];schema=$fields[0];dataDirectory=$data;principal=$fields[3];version=$fields[4];timeZone=$fields[5]}
}

function Get-Task131SchemaInventory {
    foreach($relative in @('dist/db_installer/sql/login','dist/db_installer/sql/game','test/resources/phantoms/db/migrations')){
        $directory=Join-Path $moduleRoot $relative
        Assert-Task131PhysicalDirectory $directory
        $names=[string[]]@(Get-ChildItem -LiteralPath $directory -File -Filter '*.sql'|Select-Object -ExpandProperty Name)
        if($names.Count -eq 0){throw 'TASK131_SQL_EMPTY_INVENTORY'}
        # Match StrictSqlScriptRunner's ordinal filename order, including "." before "_".
        [Array]::Sort($names,[StringComparer]::OrdinalIgnoreCase)
        foreach($name in $names){
            $file=Join-Path $directory $name
            Assert-G0PhysicalFile $file
            $bytes=[IO.File]::ReadAllBytes($file)
            $body=$utf8.GetString($bytes)
            if($body -match '(?im)^\s*(?:\\|USE\s|SOURCE\s|DELIMITER\s|(?:CREATE|DROP)\s+DATABASE\s)|/\*!'){
                throw ('TASK131_SQL_UNSUPPORTED_SCHEMA_COMMAND '+$name)
            }
            $digest=[Security.Cryptography.SHA256]::Create()
            try{$hex=[BitConverter]::ToString($digest.ComputeHash($bytes)).Replace('-','')}finally{$digest.Dispose()}
            [pscustomobject]@{path="$relative/$name";sha256=$hex;body=$body}
        }
    }
}

function Write-Task131Schema($Owned) {
    $null=Get-Task131SqlIdentity $Owned
    $null=Assert-Task131SqlQuery $adminConfig 'root' 'mysql' '^root@(localhost|127\.0\.0\.1)$'
    $scripts=@(Get-Task131SchemaInventory)
    $runtimePassword=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $setup=@(
        "CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        ('REVOKE ALL PRIVILEGES ON '+[char]96+'test'+[char]96+'.* FROM PUBLIC;'),
        ('REVOKE ALL PRIVILEGES ON '+[char]96+'test\_%'+[char]96+'.* FROM PUBLIC;'),
        "CREATE USER '$login'@'127.0.0.1' IDENTIFIED BY '$runtimePassword';",
        "GRANT ALL PRIVILEGES ON $schemaGrant.* TO '$login'@'127.0.0.1';"
    ) -join [Environment]::NewLine
    $null=Invoke-Task131Sql $adminConfig 'root' 'mysql' $setup 'TASK131_SQL_CREATE_SCHEMA'
    [IO.File]::WriteAllLines($runtimeConfig,@('[client]','host=127.0.0.1',"port=$port","user=$login","password=$runtimePassword"),$utf8)
    $null=Assert-Task131SqlQuery $runtimeConfig $login $schema '^task131_runtime@127\.0\.0\.1$'
    $null=Invoke-Task131Sql $runtimeConfig $login $schema ($scripts.body -join [Environment]::NewLine) 'TASK131_SQL_IMPORT_SCHEMA'
    [IO.File]::WriteAllLines($jdbcConfig,@('Driver=com.mysql.cj.jdbc.Driver',"URL=$jdbcUrl","Login=$login","Password=$runtimePassword",
        'MaximumDatabaseConnections=8','TestDatabaseConnections=true','BackupDatabase=false'),$utf8)
    Write-Task131Receipt 'schema-ready.json' ([ordered]@{version=1;lane=$LaneId;schema=$schema;port=$port;scriptCount=$scripts.Count
        scripts=@($scripts|Select-Object path,sha256);jdbcConfigSha256=(Get-FileHash -LiteralPath $jdbcConfig).Hash
        runtimeClientSha256=(Get-FileHash -LiteralPath $runtimeConfig).Hash;adminClientSha256=(Get-FileHash -LiteralPath $adminConfig).Hash})
}

function Write-Task131Attestation($Owned,[bool]$Fresh) {
    $os=Get-Task131SqlIdentity $Owned
    $ready=Read-Task131Receipt $binding
    foreach($file in @($jdbcConfig,$runtimeConfig,$adminConfig)){Assert-G0PhysicalFile $file}
    if($ready.version -ne 1 -or $ready.lane -cne $LaneId -or $ready.schema -cne $schema -or $ready.port -ne $port -or
        $ready.jdbcConfigSha256 -cne (Get-FileHash -LiteralPath $jdbcConfig).Hash -or
        $ready.runtimeClientSha256 -cne (Get-FileHash -LiteralPath $runtimeConfig).Hash -or
        $ready.adminClientSha256 -cne (Get-FileHash -LiteralPath $adminConfig).Hash){throw 'TASK131_SQL_SCHEMA_BINDING'}
    $query=Assert-Task131SqlQuery $runtimeConfig $login $schema '^task131_runtime@127\.0\.0\.1$'
    $grants=(Invoke-Task131Sql $runtimeConfig $login $schema 'SHOW GRANTS;' 'TASK131_SQL_GRANTS') -split '\r?\n'
    $scoped='GRANT ALL PRIVILEGES ON '+$schemaGrant+'.* TO '+[char]96+$login+[char]96+'@'+[char]96+'127.0.0.1'+[char]96
    if($grants.Length -ne 2 -or $grants[0] -cnotmatch '^GRANT USAGE ON \*\.\* TO .+?(?: IDENTIFIED BY PASSWORD ''\*[0-9A-F]{40}'')?$' -or
        $grants[1] -cne $scoped){throw 'TASK131_SQL_FOREIGN_GRANTS'}
    Invoke-Task131Sql $runtimeConfig $login $schema 'SELECT user FROM mysql.user LIMIT 1;' 'TASK131_SQL_NEGATIVE' -ExpectDenied
    $countSql=@'
SELECT (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()),
       (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN
           ('accounts','gameservers','characters','phantom_profiles','phantom_ai_training_examples')),
       (SELECT COUNT(*) FROM accounts),(SELECT COUNT(*) FROM characters);
'@
    $counts=(Invoke-Task131Sql $runtimeConfig $login $schema $countSql 'TASK131_SQL_CORE_TABLES') -split '\t'
    if($counts.Length -ne 4 -or [int]$counts[1] -ne 5 -or ($Fresh -and ([int]$counts[2] -ne 0 -or [int]$counts[3] -ne 0))){
        throw 'TASK131_SQL_CORE_TABLES'
    }
    $null=Get-Task131SqlIdentity $Owned
    $proof=[ordered]@{version=1;lane=$LaneId;phase='RUNNING';os=$os;query=$query;canonicalScripts=[int]$ready.scriptCount
        schemaSealSha256=(Get-FileHash -LiteralPath $binding).Hash;tableCount=[int]$counts[0]
        accounts=[int]$counts[2];characters=[int]$counts[3];foreignTableDenied=$true;observedAtUtc=[DateTime]::UtcNow.ToString('o')}
    Write-Task131Receipt 'attestation.json' $proof
    Write-Output "TASK131_SQL_ATTESTED lane=$LaneId pid=$($Owned.pid) port=$port schema=$schema tables=$($proof.tableCount) foreignTable=DENIED"
}

function Stop-Task131Sql($Owned) {
    if($Owned.lane -cne $LaneId -or $Owned.port -ne $port -or $Owned.schema -cne $schema){throw 'TASK131_SQL_STATE_SCOPE'}
    if(!(Test-Task131SqlPid $Owned)){
        $prior=Read-Task131Receipt (Join-Path $root 'attestation.json')
        if($prior.lane -cne $LaneId -or $prior.os.PID -ne $Owned.pid -or
            ([DateTime]$prior.os.Created).ToUniversalTime().ToFileTimeUtc() -ne $Owned.created -or
            $prior.os.ConfigHash -cne $Owned.configHash -or $prior.os.Binary -cne $Owned.binary){
            throw 'TASK131_SQL_STOP_ATTESTATION_SCOPE'
        }
        Write-Task131Off $Owned $prior.os.CommandLine 'WMI_ABSENCE_AFTER_ATTESTATION'
        return
    }
    $os=Get-Task131SqlIdentity $Owned
    $process=[Diagnostics.Process]::GetProcessById($Owned.pid)
    try {
        if($process.Handle -eq [IntPtr]::Zero -or $process.StartTime.ToUniversalTime().ToFileTimeUtc() -ne $Owned.created){
            throw 'TASK131_SQL_STOP_HANDLE_IDENTITY'
        }
        Assert-Task131ClientConfig $adminConfig 'root'
        & $admin ('--defaults-file='+$adminConfig) --host=127.0.0.1 "--port=$port" --protocol=tcp --connect-timeout=5 --user=root shutdown
        if($LASTEXITCODE -ne 0){throw 'TASK131_SQL_SHUTDOWN'}
        if(!$process.WaitForExit(20000)){throw 'TASK131_SQL_STOP_PROCESS_STILL_RUNNING'}
        Write-Task131Off $Owned $os.CommandLine 'OWN_PROCESS_HANDLE_EXIT'
    } finally {
        $process.Dispose()
    }
}

function Test-Task131SqlPid($Owned) {
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$Owned.pid)"
    return ($null -ne $process -and (Get-G0Microseconds $process.CreationDate.ToUniversalTime().ToFileTimeUtc()) -eq (Get-G0Microseconds $Owned.created))
}

function Write-Task131Off($Owned,[string]$Command,[string]$ExitEvidence) {
    if(@([Win32SqlIdentity]::Listeners($port)).Count){throw 'TASK131_SQL_STOP_PORT_STILL_LISTENING'}
    Write-Task131Receipt 'off.json' ([ordered]@{version=1;lane=$LaneId;phase='OFF';pid=$Owned.pid;createdFileTime=$Owned.created
        port=$port;listeners=0;datadir=$data;commandLine=$Command;exitEvidence=$ExitEvidence;observedAtUtc=[DateTime]::UtcNow.ToString('o')})
    Write-Output "TASK131_SQL_OFF lane=$LaneId pid=$($Owned.pid) port=$port listeners=0 evidence=$ExitEvidence"
}

foreach($file in @($daemon,$install,$client,$admin)){Assert-G0PhysicalFile $file}
if((Get-Item -LiteralPath $daemon).VersionInfo.ProductVersion -cne '11.4.13.0'){throw 'TASK131_SQL_MARIADB_VERSION'}
# Validate process observation before creating a child that would require the same checks for cleanup.
$caller=Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
if(!$caller -or !$caller.CommandLine){throw 'TASK131_SQL_PROCESS_VISIBILITY'}
Assert-Task131PhysicalDirectory $logRoot
if($Action -eq 'Start'){
    foreach($directory in @($lanes,$lane)){
        if(!(Test-Path -LiteralPath $directory)){[void](New-Item -ItemType Directory -Path $directory)}
        Assert-Task131PhysicalDirectory $directory
    }
}
Assert-Task131PhysicalDirectory $lane
$lockPath=Join-Path $lanes 'port-33331.lock'
if(Test-Path -LiteralPath $lockPath){Assert-G0PhysicalFile $lockPath}
$parentPins=[G0AttestationFile]::PinParents($root,$false)
$lock=$null
try {
    $lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    if($Action -ne 'Start'){
        Assert-Task131OwnerAcl
        $owned=Read-Task131Receipt $state
        if($Action -eq 'Attest'){Write-Task131Attestation $owned $false}else{Stop-Task131Sql $owned}
        return
    }
    if(@([Win32SqlIdentity]::Listeners($port)).Count){throw 'TASK131_SQL_PORT_BUSY'}
    $fresh=!(Test-Path -LiteralPath $root)
    if($fresh){
        [void](New-Item -ItemType Directory -Path $root)
        Assert-Task131PhysicalDirectory $root
        $user=[Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl=[Security.AccessControl.DirectorySecurity]::new()
        $acl.SetOwner($user)
        $acl.SetAccessRuleProtection($true,$false)
        foreach($sid in @($user.Value,'S-1-5-18')){
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        }
        Set-Acl -LiteralPath $root -AclObject $acl
        Assert-Task131OwnerAcl
        $password=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
        [IO.File]::WriteAllLines($adminConfig,@('[client]','host=127.0.0.1',"port=$port",'user=root',"password=$password"),$utf8)
        $initialize=@{FilePath=$install;ArgumentList=@(('--datadir="'+$data+'"'),"--port=$port","--password=$password",'--silent')
            WorkingDirectory=$root;PassThru=$true;Wait=$true;WindowStyle='Hidden'
            RedirectStandardOutput=(Join-Path $root 'install.out');RedirectStandardError=(Join-Path $root 'install.err')}
        $init=Start-Process @initialize
        if($init.ExitCode -ne 0 -or !(Test-Path -LiteralPath (Join-Path $data 'mysql') -PathType Container)){throw 'TASK131_SQL_INIT'}
        [IO.File]::WriteAllLines($config,@('[mysqld]','bind-address=127.0.0.1',"port=$port",('datadir="'+$data.Replace('\','/')+'"'),'default-time-zone=+00:00'),$utf8)
    } else {
        Assert-Task131OwnerAcl
        $previous=Read-Task131Receipt $state
        $off=Read-Task131Receipt (Join-Path $root 'off.json')
        $null=Read-Task131Receipt $binding
        if($off.phase -cne 'OFF' -or $off.pid -ne $previous.pid -or $off.createdFileTime -ne $previous.created){throw 'TASK131_SQL_PREVIOUS_STOP_UNPROVED'}
        if(Test-Task131SqlPid $previous){throw 'TASK131_SQL_PREVIOUS_PROCESS_ALIVE'}
    }
    Assert-G0PhysicalFile $config
    Assert-Task131PhysicalDirectory $data
    $expected=@('[mysqld]','bind-address=127.0.0.1',"port=$port",('datadir="'+$data.Replace('\','/')+'"'),'default-time-zone=+00:00')
    if(([IO.File]::ReadAllLines($config,$utf8) -join '|') -cne ($expected -join '|')){throw 'TASK131_SQL_DAEMON_OPTIONS'}
    $stamp=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
    $commandPattern='^"?'+[regex]::Escape($daemon)+'"?\s+"--defaults-file='+[regex]::Escape($config)+'"\s+--console\s*$'
    $pins=[ordered]@{lane=$LaneId;port=$port;schema=$schema;pid=0;created=0L;configHash=(Get-FileHash -LiteralPath $config).Hash;binary=(Get-FileHash -LiteralPath $daemon).Hash}
    $launch=@{FilePath=$daemon;ArgumentList=@(('"--defaults-file='+$config+'"'),'--console');PassThru=$true;WorkingDirectory=$root
        WindowStyle='Hidden';RedirectStandardOutput=(Join-Path $root "$stamp.out");RedirectStandardError=(Join-Path $root "$stamp.err")}
    $server=Start-Process @launch
    try {
        $pins.pid=$server.Id
        $pins.created=$server.StartTime.ToUniversalTime().ToFileTimeUtc()
        Write-Task131Receipt 'owned-process.json' $pins
        $watch=[Diagnostics.Stopwatch]::StartNew()
        while(!@([Win32SqlIdentity]::Listeners($port)).Count -and !$server.HasExited -and $watch.Elapsed.TotalSeconds -lt 25){Start-Sleep -Milliseconds 100}
        if($fresh){Write-Task131Schema $pins}
        Write-Task131Attestation $pins $fresh
    } catch {
        if(!$server.HasExited){
            $proc=Get-CimInstance Win32_Process -Filter "ProcessId=$($server.Id)"
            $native=[Win32SqlIdentity]::Process([uint32]$server.Id,0x1000)
            if(!$native.Success -or $native.CreatedFileTime -ne $pins.created -or $native.ImagePath -ine $daemon -or !$proc -or
                $proc.CommandLine -notmatch $commandPattern){throw 'TASK131_SQL_FAILED_CHILD_IDENTITY'}
            $server.Kill()
            if(!$server.WaitForExit(5000)){throw 'TASK131_SQL_FAILED_CHILD_STOP'}
            Write-Task131Off $pins $proc.CommandLine 'FAILED_OWN_CHILD_HANDLE_EXIT'
        }
        throw
    } finally {
        $server.Dispose()
    }
} finally {
    if($null -ne $lock){$lock.Dispose()}
    $parentPins.Dispose()
}
