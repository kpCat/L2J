[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('stage','provision-sql','probe-sql','prepare-actors','plan-scenes','check-capacity','check-party','survey','shadow','on','coverage','train','promote','read','read-native')][string]$Action,
    [ValidatePattern('^(native|cycle(?:[2-9]|[1-9][0-9]{1,2})?)$')][string]$Lane='native',
    [ValidatePattern('^[0-9a-fA-F]{64}$')][string]$JarSha,
    [ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ModelSha,
    [long]$Version=0,
    [long]$Seed=183,
    [string]$GeoSource,
    [string]$GeoManifest,
    [string]$ProtectedManifest
)

$ErrorActionPreference='Stop'
if ($Lane -cnotmatch '^cycle(?:1[2-9]|[2-9][0-9]|[1-9][0-9]{2})$' -and $Action -notin @('probe-sql','read-native')) { throw 'LEARNING001_HISTORICAL_LANE_READBACK_ONLY' }
if ($Action -in @('check-party','shadow','on','coverage','train','promote','read') -and !$JarSha) { throw 'LEARNING001_EXPLICIT_JAR_SHA_REQUIRED' }
if ($Action -in @('on','promote','read') -and ($Version -le 0 -or !$ModelSha)) { throw 'LEARNING001_EXPLICIT_MODEL_PIN_REQUIRED' }
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$root=Join-Path (Split-Path $module -Parent) ('.phantom-local\learning001\'+$Lane)
$runtime=Join-Path $root 'game'
$sql=Join-Path $root 'sql'
$data=Join-Path $sql 'data'
$config=Join-Path $sql 'owned-server.ini'
$receipt=Join-Path $sql 'owned-process.json'
$port=33317
$gamePort=17961
$loginPort=19261
$bin=Join-Path $env:ProgramFiles 'MariaDB 11.4\bin'
$daemon=Join-Path $bin 'mariadbd.exe'
$install=Join-Path $bin 'mariadb-install-db.exe'
$admin=Join-Path $bin 'mariadb-admin.exe'
$adminConfig=Join-Path $sql 'admin.client.ini'
$encoding=[Text.UTF8Encoding]::new($false)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$expectedGeo='A8A4B73BC7B53BED28F545CD889F489492B585B369F8E84988F8DB8A91C973D5'
$expectedProtected='9777AB48BC8D14BDEE77D4ACA8E7E650CB099F03CA33FEA108D5CDB941C59DCD'
. (Join-Path $PSScriptRoot 'G0SqlIdentity.ps1')
Add-Type -Path (Join-Path $PSScriptRoot 'Win32SqlIdentity.cs')
if ([Win32SqlIdentity]::CurrentTokenElevated() -or [Win32SqlIdentity]::CurrentDebugEnabled()) { throw 'LEARNING001_ORDINARY_TOKEN_REQUIRED' }

function Assert-Hash([string]$Path,[string]$Hash) {
    Assert-G0PhysicalFile $Path
    if ((Get-FileHash -LiteralPath $Path).Hash -ine $Hash) { throw "LEARNING001_HASH_MISMATCH $Path" }
}

function Assert-Protected([string]$Path) {
    Assert-Hash $Path $expectedProtected
    $rows=ConvertFrom-Json ([IO.File]::ReadAllText($Path))
    if ($rows.Count -ne 207) { throw 'LEARNING001_PROTECTED_COUNT' }
    foreach ($row in $rows) { Assert-Hash $row.path $row.sha256 }
}

function Read-Geo([string]$Path) {
    Assert-Hash $Path $expectedGeo
    $rows=ConvertFrom-Json ([IO.File]::ReadAllText($Path))
    if ($rows.Count -ne 203 -or ($rows | Measure-Object -Property bytes -Sum).Sum -ne 1063452308) { throw 'LEARNING001_GEO_COUNT_OR_SIZE' }
    return $rows
}

function Assert-Geo([string]$Directory,$Rows) {
    $files=@(Get-ChildItem -LiteralPath $Directory -File -Force)
    if ($files.Count -ne $Rows.Count -or @(Compare-Object $files.Name $Rows.name -CaseSensitive).Count) { throw 'LEARNING001_GEO_FILE_SET' }
    foreach ($row in $Rows) {
        $file=Join-Path $Directory $row.name
        if ((Get-Item -LiteralPath $file).Length -ne $row.bytes) { throw 'LEARNING001_GEO_FILE_LENGTH' }
        Assert-Hash $file $row.sha256
    }
}

function Assert-PortsFree($Ports) {
    foreach ($candidate in $Ports) {
        if (@([Win32SqlIdentity]::Listeners($candidate)).Count) { throw "LEARNING001_PORT_BUSY $candidate" }
    }
}

function Assert-GameSocket($Game,$Executable,[string]$AttemptId) {
    $identity=[Win32SqlIdentity]::Process([uint32]$Game.Id,0x1000)
    $cim=Get-CimInstance Win32_Process -Filter "ProcessId=$($Game.Id)"
    $listeners=@([Win32SqlIdentity]::Listeners($gamePort))
    if (!$identity.Success -or $identity.CreatedFileTime -ne $Game.StartTime.ToUniversalTime().ToFileTimeUtc() -or
        $identity.ImagePath -ine $Executable -or !$cim -or $cim.ExecutablePath -ine $Executable -or
        $cim.CommandLine -notmatch ('\b'+[regex]::Escape($AttemptId)+'\b') -or
        $cim.CommandLine -notmatch 'org\.l2jmobius\.tests\.phantoms\.PhantomLearning001ShadowNative' -or
        $listeners.Count -ne 1 -or $listeners[0].OwningProcess -ne $Game.Id -or $listeners[0].LocalAddress -ne '127.0.0.1') {
        throw 'LEARNING001_GAME_PID_OR_LOOPBACK_SOCKET'
    }
    $owner=Invoke-CimMethod -InputObject $cim -MethodName GetOwnerSid
    if ($owner.ReturnValue -ne 0 -or $owner.Sid -cne $sid.Value) { throw 'LEARNING001_GAME_PROCESS_OWNER' }
    Assert-PortsFree @($loginPort)
    Write-Output "LEARNING001_GAME_LOOPBACK_PASS pid=$($Game.Id) filetime=$($identity.CreatedFileTime) endpoint=127.0.0.1:${gamePort}"
}

function Set-RuntimeIni([string]$Relative,$Overrides) {
    $file=Join-Path $runtime ('config\'+$Relative)
    Assert-G0PhysicalFile $file
    $body=[IO.File]::ReadAllText($file)
    foreach ($key in $Overrides.Keys) {
        $pattern='(?m)^'+[regex]::Escape($key)+'[ \t]*=[^\r\n]*'
        if ([regex]::Matches($body,$pattern).Count -ne 1) { throw "LEARNING001_INI_KEY $Relative $key" }
        $body=[regex]::Replace($body,$pattern,($key+' = '+$Overrides[$key]))
    }
    [IO.File]::WriteAllText($file,$body,$encoding)
}

function Assert-SqlOptions {
    Assert-G0PhysicalFile $daemon
    Assert-G0PhysicalFile $config
    $expected=@('[mysqld]','bind-address=127.0.0.1',"port=$port",('datadir="'+$data.Replace('\','/')+'"'),'default-time-zone=+00:00')
    $directory=Get-Item -LiteralPath $data -Force
    if (!$directory.PSIsContainer -or $directory.FullName -ine $data -or
        ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        ((Get-Content -LiteralPath $config) -join '|') -cne ($expected -join '|')) { throw 'LEARNING001_SQL_OPTIONS' }
}

function Assert-OwnSql($Owned) {
    Assert-SqlOptions
    $commandPattern='^"?'+[regex]::Escape($daemon)+'"?\s+"--defaults-file='+[regex]::Escape($config)+'"\s+--console\s*$'
    for ($i=0;$i -lt 2;$i++) {
        $identity=[Win32SqlIdentity]::Process([uint32]$Owned.pid,0x1000)
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($Owned.pid)"
        if (!$identity.Success -or $identity.CreatedFileTime -ne $Owned.created -or $identity.ImagePath -ine $daemon -or
            !$process -or $process.ExecutablePath -ine $daemon -or $process.CommandLine -notmatch $commandPattern -or
            (Get-G0Microseconds $identity.CreatedFileTime) -ne (Get-G0Microseconds $process.CreationDate.ToUniversalTime().ToFileTimeUtc())) {
            throw 'LEARNING001_SQL_PID_OR_COMMAND'
        }
        $owner=Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid
        $listeners=@([Win32SqlIdentity]::Listeners($port))
        if ($owner.ReturnValue -ne 0 -or $owner.Sid -cne $sid.Value -or $listeners.Count -ne 1 -or
            $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne $Owned.pid -or
            @(Get-CimInstance Win32_Service -Filter "ProcessId=$($Owned.pid)").Count) { throw 'LEARNING001_SQL_OWNER_OR_LISTENER' }
    }
    if ((Get-FileHash -LiteralPath $config).Hash -cne $Owned.configHash -or (Get-FileHash -LiteralPath $daemon).Hash -cne $Owned.imageHash) {
        throw 'LEARNING001_SQL_FILE_CHANGED'
    }
    Write-Output "LEARNING001_G0_OWNED_SQL_PASS pid=$($Owned.pid) filetime=$($Owned.created) port=$port"
}

function Stop-OwnSql($Owned) {
    Assert-OwnSql $Owned
    Assert-G0PhysicalFile $adminConfig
    $lines=@(Get-Content -LiteralPath $adminConfig)
    if ($lines.Count -ne 5 -or ($lines[0..3] -join '|') -cne ('[client]|host=127.0.0.1|port='+$port+'|user=root') -or
        $lines[4] -cnotmatch '^password=[0-9a-f]{64}$') { throw 'LEARNING001_SQL_ADMIN_OPTIONS' }
    & $admin ('--defaults-file='+$adminConfig) --protocol=tcp --host=127.0.0.1 "--port=$port" --user=root shutdown
    if ($LASTEXITCODE -ne 0) { throw 'LEARNING001_SQL_SHUTDOWN_FAILED' }
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {
        $current=[Win32SqlIdentity]::Process([uint32]$Owned.pid,0x1000)
        if (!$current.Success -or $current.CreatedFileTime -ne $Owned.created) {
            Assert-PortsFree @($port)
            Write-Output 'LEARNING001_SQL_STOP_PASS'
            return
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'LEARNING001_SQL_STOP_TIMEOUT'
}

if ($Action -eq 'stage') {
    if (Test-Path -LiteralPath $root) { throw 'LEARNING001_FRESH_ROOT_REQUIRED' }
    foreach ($item in @($GeoSource,$GeoManifest,$ProtectedManifest)) {
        if (![IO.Path]::IsPathRooted($item)) { throw 'LEARNING001_ABSOLUTE_INPUT_REQUIRED' }
    }
    Assert-PortsFree @($port,$gamePort,$loginPort)
    Assert-Protected $ProtectedManifest
    $rows=Read-Geo $GeoManifest
    Assert-Geo $GeoSource $rows
    $existingParent=Split-Path $root -Parent
    while (!(Test-Path -LiteralPath $existingParent)) { $existingParent=Split-Path $existingParent -Parent }
    for ($parent=Get-Item -LiteralPath $existingParent;$null -ne $parent;$parent=$parent.Parent) {
        if ($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'LEARNING001_REPARSE_ROOT' }
    }
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($trustee in @($sid.Value,'S-1-5-18')) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($trustee),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    $null=[IO.Directory]::CreateDirectory($root,$acl)
    [IO.File]::WriteAllText((Join-Path $root 'owner.txt'),('LEARNING001:'+([guid]::NewGuid().ToString('N'))),$encoding)
    $geo=Join-Path $runtime 'geodata'
    $null=New-Item -ItemType Directory -Path $runtime,$geo,(Join-Path $runtime 'libs'),(Join-Path $runtime 'log')
    Copy-Item -LiteralPath $GeoManifest -Destination (Join-Path $root 'geo203.json')
    Copy-Item -LiteralPath $ProtectedManifest -Destination (Join-Path $root 'protected207.json')
    foreach ($row in $rows) { Copy-Item -LiteralPath (Join-Path $GeoSource $row.name) -Destination (Join-Path $geo $row.name) }
    Assert-Geo $geo $rows
    foreach ($name in @('data','log.cfg')) { Copy-Item -LiteralPath (Join-Path $module "dist\game\$name") -Destination $runtime -Recurse }
    $runtimeConfig=Join-Path $runtime 'config'
    $null=New-Item -ItemType Directory -Path $runtimeConfig
    Get-ChildItem -LiteralPath (Join-Path $module 'dist\game\config') -Force |
        Where-Object Name -ne 'Database.ini' | Copy-Item -Destination $runtimeConfig -Recurse
    Get-ChildItem -LiteralPath (Join-Path $module 'dist\libs') -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Copy-Item -Destination (Join-Path $runtime 'libs')
    Assert-Protected (Join-Path $root 'protected207.json')
    Write-Output 'LEARNING001_STAGE_PASS GEO203=203 PROTECTED=207 GAMESERVER_BOOT=0'
    return
}

$ownerFile=Join-Path $root 'owner.txt'
Assert-G0PhysicalFile $ownerFile
if ([IO.File]::ReadAllText($ownerFile) -cnotmatch '^LEARNING001:[0-9a-f]{32}$') { throw 'LEARNING001_OWNER' }
Assert-Protected (Join-Path $root 'protected207.json')
& (Join-Path $PSScriptRoot 'Invoke-G0IdentityAttestation.ps1') -CheckOnly
if ($LASTEXITCODE -ne 0) { throw 'LEARNING001_G0_SELF_CHECK' }
foreach ($file in @($daemon,$install,$admin)) { Assert-G0PhysicalFile $file }
if ((Get-Item -LiteralPath $daemon).VersionInfo.ProductVersion -ne '11.4.13.0') { throw 'LEARNING001_SQL_VERSION' }
Assert-PortsFree @($port,$gamePort,$loginPort)

if ($Action -eq 'provision-sql') {
    if (Test-Path -LiteralPath $sql) { throw 'LEARNING001_FRESH_SQL_ROOT_REQUIRED' }
    $null=New-Item -ItemType Directory -Path $sql
    $password=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllLines($adminConfig,@('[client]','host=127.0.0.1',"port=$port",'user=root',"password=$password"),$encoding)
    [IO.File]::WriteAllLines((Join-Path $sql 'admin.jdbc.ini'),@("URL=jdbc:mysql://127.0.0.1:$port/mysql",'Login=root',"Password=$password"),$encoding)
    $init=Start-Process -FilePath $install -ArgumentList @('--datadir="'+$data+'"',"--port=$port","--password=$password",'--silent') -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $sql 'install.out') -RedirectStandardError (Join-Path $sql 'install.err')
    if ($init.ExitCode -ne 0 -or !(Test-Path -LiteralPath (Join-Path $data 'mysql') -PathType Container)) { throw 'LEARNING001_SQL_INIT' }
    [IO.File]::WriteAllLines($config,@('[mysqld]','bind-address=127.0.0.1',"port=$port",('datadir="'+$data.Replace('\','/')+'"'),'default-time-zone=+00:00'),$encoding)
}

Assert-SqlOptions
$attempt=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
$server=$null
$gameProcess=$null
$owned=@{pid=0;created=0L;configHash=(Get-FileHash -LiteralPath $config).Hash;imageHash=(Get-FileHash -LiteralPath $daemon).Hash}
try {
    $server=Start-Process -FilePath $daemon -ArgumentList @('"--defaults-file='+$config+'"','--console') -WorkingDirectory $sql -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $sql "$attempt.out") -RedirectStandardError (Join-Path $sql "$attempt.err")
    $owned.pid=$server.Id
    $owned.created=$server.StartTime.ToUniversalTime().ToFileTimeUtc()
    $owned | ConvertTo-Json | Set-Content -LiteralPath $receipt -Encoding UTF8
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    while (!@([Win32SqlIdentity]::Listeners($port)).Count -and !$server.HasExited -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 200 }
    Assert-OwnSql $owned
    $testBin=Join-Path (Split-Path $module -Parent) 'build\phantom-test\bin'
    $classpath="$testBin;"+(Join-Path $runtime 'libs\*')
    $command=switch ($Action) {'provision-sql' {'prepare'} 'read-native' {'read-native'} default {'probe'}}
    & java -cp $classpath org.l2jmobius.tests.phantoms.PhantomLearning001FixtureSql $command $module $Lane
    if ($LASTEXITCODE -ne 0) { throw 'LEARNING001_SQL_SCHEMA_OR_PROBE_FAILED' }
    if ($Action -eq 'provision-sql') {
        $ownConfig=Join-Path $root 'Database.ini'
        $targetConfig=Join-Path $runtime 'config\Database.ini'
        Assert-G0PhysicalFile $ownConfig
        if (Test-Path -LiteralPath $targetConfig) { Assert-G0PhysicalFile $targetConfig }
        Copy-Item -LiteralPath $ownConfig -Destination $targetConfig -Force
        Assert-Hash $targetConfig (Get-FileHash -LiteralPath $ownConfig).Hash
        Set-RuntimeIni 'Interface.ini' @{EnableGUI='False'}
        Set-RuntimeIni 'Server.ini' @{LoginHost='127.0.0.1';LoginPort=$loginPort;GameserverHostname='127.0.0.1';GameserverPort=$gamePort;DatapackRoot='.'}
        Set-RuntimeIni 'GeoEngine.ini' @{GeoDataPath='./geodata/'}
        Set-RuntimeIni 'Custom\PhantomPlayers.ini' @{EnablePhantomSystem='False';LearningMode='OFF';LearningModelVersion='0';LearningEvidenceEnabled='False';LearningDeterministicSeed='0'}
        Set-RuntimeIni 'Custom\PhantomMerchants.ini' @{EnableOfflinePhantomMerchants='False'}
        Set-RuntimeIni 'Custom\OfflineTrade.ini' @{RestoreOffliners='False'}
        Write-Output 'LEARNING001_RUNTIME_LOCAL_ENDPOINTS_PINNED'
    }
    if ($Action -in @('prepare-actors','plan-scenes','check-capacity','check-party','survey')) {
        if ($Action -eq 'check-party') { Assert-Hash (Join-Path $runtime 'libs/GameServer.jar') $JarSha }
        Assert-Hash (Join-Path $runtime 'config\Database.ini') (Get-FileHash -LiteralPath (Join-Path $root 'Database.ini')).Hash
        Assert-Geo (Join-Path $runtime 'geodata') (Read-Geo (Join-Path $root 'geo203.json'))
        Push-Location -LiteralPath $runtime
        try {
            $phase=switch ($Action) {'prepare-actors' {'actors'} 'plan-scenes' {'scenes'} 'check-capacity' {'capacity'} 'check-party' {'party'} 'survey' {'survey'}}
            & java '-XX:ActiveProcessorCount=4' -Xms256m -Xmx3g -cp $classpath org.l2jmobius.tests.phantoms.PhantomLearning001ActorFixture $phase $module $Lane
            if ($LASTEXITCODE -ne 0) { throw 'LEARNING001_ACTOR_PREFLIGHT_FAILED' }
        } finally {
            Pop-Location
        }
        Assert-PortsFree @($gamePort,$loginPort)
        if ($Action -eq 'check-party') {
            [IO.File]::WriteAllText((Join-Path $root 'party.ready'),$JarSha.ToLowerInvariant(),$encoding)
        }
    }
    if ($Action -in @('coverage','train','promote','read')) {
        Assert-Hash (Join-Path $runtime 'libs\GameServer.jar') $JarSha
        $offlineArgs=@($Action,$module,$Lane)
        switch ($Action) {
            'coverage' { $offlineArgs+=@('4096') }
            'train' { $offlineArgs+=@('4096',[string]$Seed) }
            default { $offlineArgs+=@([string]$Version,$ModelSha.ToLowerInvariant()) }
        }
        & java '-Dphantom.learning.train=true' -cp $classpath org.l2jmobius.tests.phantoms.PhantomLearning001Offline @offlineArgs
        if ($LASTEXITCODE -ne 0) { throw "LEARNING001_OFFLINE_PHASE_FAILED $Action" }
    }
    if ($Action -in @('shadow','on')) {
        Assert-Hash (Join-Path $runtime 'libs\GameServer.jar') $JarSha
        Assert-Hash (Join-Path $runtime 'config\Database.ini') (Get-FileHash -LiteralPath (Join-Path $root 'Database.ini')).Hash
        Assert-Geo (Join-Path $runtime 'geodata') (Read-Geo (Join-Path $root 'geo203.json'))
        foreach ($file in 'actors.tsv','rosters.tsv','capacity.tsv','party.ready') { Assert-G0PhysicalFile (Join-Path $root $file) }
        if ([IO.File]::ReadAllText((Join-Path $root 'party.ready')) -cne $JarSha.ToLowerInvariant()) { throw 'LEARNING001_STOCK_PARTY_PREFLIGHT_PIN' }
        if ($Action -eq 'on') {
            & java '-Dphantom.learning.train=true' -cp $classpath org.l2jmobius.tests.phantoms.PhantomLearning001Offline read $module $Lane $Version ($ModelSha.ToLowerInvariant())
            if ($LASTEXITCODE -ne 0) { throw 'LEARNING001_COLD_MODEL_PIN_UNVERIFIED' }
        }
        $runId=[guid]::NewGuid().ToString('N')
        $run=Join-Path $root ('attempts\'+$runId)
        $null=[IO.Directory]::CreateDirectory($run)
        $javaExe=(Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
        Assert-G0PhysicalFile $javaExe
        $modelPin=if ($Action -eq 'on') { $ModelSha.ToLowerInvariant() } else { '-' }
        $argsList=@('-XX:ActiveProcessorCount=4','-Xms512m','-Xmx4g','-Dphantom.learning.train=true','-Dphantom.learning.witness=true','-cp',('"'+$classpath+'"'),
            'org.l2jmobius.tests.phantoms.PhantomLearning001ShadowNative',('"'+$module+'"'),$Lane,$runId,$JarSha.ToLowerInvariant(),
            $Action,[string]$Version,$modelPin)
        $gameProcess=Start-Process -FilePath $javaExe -ArgumentList $argsList -WorkingDirectory $runtime -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $run 'game.out') -RedirectStandardError (Join-Path $run 'game.err')
        Write-Output "LEARNING001_GAME_STARTED pid=$($gameProcess.Id) attempt=$runId directory=$run"
        try {
            $ready=Join-Path $run 'game.ready'
            $deadline=[DateTime]::UtcNow.AddMinutes(3)
            while (!$gameProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
                if ((Test-Path -LiteralPath $ready -PathType Leaf) -and [IO.File]::ReadAllText($ready) -ceq [string]$gameProcess.Id) { break }
                Start-Sleep -Milliseconds 250
            }
            Assert-G0PhysicalFile $ready
            if ([IO.File]::ReadAllText($ready) -cne [string]$gameProcess.Id) { throw 'LEARNING001_GAME_READY_PID' }
            Assert-OwnSql $owned
            Assert-GameSocket $gameProcess $javaExe $runId
            $socketPending=Join-Path $run 'socket.pending'
            [IO.File]::WriteAllText($socketPending,[string]$gameProcess.Id,$encoding)
            [IO.File]::Move($socketPending,(Join-Path $run 'socket.ok'))
            if (!$gameProcess.WaitForExit(2700000)) { throw 'LEARNING001_GAME_ATTEMPT_TIMEOUT' }
            if ($gameProcess.ExitCode -ne 0 -or (Test-Path -LiteralPath (Join-Path $run "$Action.failed")) -or
                !(Test-Path -LiteralPath (Join-Path $run "$Action.complete"))) { throw "LEARNING001_GAME_PHASE_UNPROVEN $Action $run" }
            Assert-PortsFree @($gamePort,$loginPort)
            Assert-Hash (Join-Path $runtime 'libs\GameServer.jar') $JarSha
            Write-Output "LEARNING001_GAME_STOP_PASS attempt=$runId"
            if ($Action -eq 'on') {
                Assert-OwnSql $owned
                & java '-Dphantom.learning.train=true' -cp $classpath org.l2jmobius.tests.phantoms.PhantomLearning001Offline verify-on $module $Lane $Version $modelPin $runId
                if ($LASTEXITCODE -ne 0) { throw "LEARNING001_ON_EVENT_SQL_UNPROVEN $run" }
            }
        } finally {
            if (!$gameProcess.HasExited) {
                [IO.File]::WriteAllText((Join-Path $run 'stop.request'),'operator',$encoding)
                if (!$gameProcess.WaitForExit(90000)) { Write-Warning "LEARNING001_GAME_STOP_UNCONFIRMED pid=$($gameProcess.Id) attempt=$runId" }
            }
        }
    }
} finally {
    try {
        if ($server -and !$server.HasExited -and (!$gameProcess -or $gameProcess.HasExited)) {
            try { Stop-OwnSql $owned }
            catch {
                Write-Warning "LEARNING001_SQL_STOP_UNCONFIRMED pid=$($owned.pid) filetime=$($owned.created) receipt=$receipt"
                throw
            }
        }
        elseif ($gameProcess -and !$gameProcess.HasExited) { Write-Warning "LEARNING001_SQL_RETAINED_FOR_LIVE_GAME pid=$($owned.pid)" }
    } finally {
        Assert-Protected (Join-Path $root 'protected207.json')
    }
}
