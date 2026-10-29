[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit','Prepare','Bind','Seal','A','B','Forensic','Cleanup')][string]$Action,
    [string]$SqlClient = 'C:\Program Files\MariaDB 11.4\bin\mariadb.exe',
    [string]$ApprovedProcessReceiptSHA256
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Physical([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    for($walk=$item;$null -ne $walk;$walk=if($walk -is [IO.FileInfo]){$walk.Directory}else{$walk.Parent}) {
        if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN106_REPARSE'}
    }
    return $item.FullName
}
function Hash([string]$Path){(Get-FileHash -LiteralPath (Physical $Path) -Algorithm SHA256).Hash.ToLowerInvariant()}
function New-Text([string]$Path,[string]$Value){
    $stream=[IO.File]::Open($Path,'CreateNew','Write','None')
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
}
function Properties([string]$Path){
    $result=@{}
    foreach($line in [IO.File]::ReadAllLines((Physical $Path))){if($line -match '^([^#=]+)=(.*)$'){$result[$matches[1].Trim()]=$matches[2].Trim()}}
    return $result
}
function Private-Directory([string]$Path){
    if(Test-Path -LiteralPath $Path){throw 'OWN106_FRESH_DIRECTORY_REQUIRED'}
    New-Item -ItemType Directory -Path $Path|Out-Null
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid));$acl.SetAccessRuleProtection($true,$false)
    foreach($id in @($sid,'S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($id),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-Private $Path
}
function Assert-Private([string]$Path){
    $acl=Get-Acl -LiteralPath (Physical $Path)
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN106_PRIVATE_OWNER_REQUIRED'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN106_FOREIGN_ACL'}}
}
function Java0 {
    if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN106_JAVA_PRESENT_NO_RUNTIME'}
    if(@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308)}).Count -ne 0){throw 'OWN106_TCP_PRESENT_NO_RUNTIME'}
}
$module=Physical (Join-Path $PSScriptRoot '../..')
$repo=Physical (Split-Path -Parent $module)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior=Get-Content -LiteralPath (Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105/resume105.private.json') -Raw|ConvertFrom-Json
$origin=Physical $prior.origin
$original=Properties (Join-Path $origin 'private/owner.properties')
$client=Physical $SqlClient
$options=Physical (Join-Path $prior.archive 'sql-client.private.cnf')
$index=Join-Path $repo '.phantom-local/logs/PHANTOM-MERCHANT-IDLE-RECOVERY-106'
foreach($root in @($origin,(Physical $prior.archive),(Physical $prior.resume))){Assert-Private $root}
if($original.owner -cne 'TASK102_POPULATION_SCALE' -or $original.schema -cne 'l2jmobiush5_phantom_test_102' -or $original.ownerSid -cne $sid -or
    (Physical $original.sourceModule) -ine $module -or (Hash (Join-Path $origin 'inputs.tsv')) -cne $original.inputsSha256 -or
    ![IO.File]::ReadAllText((Physical (Join-Path $origin 'private/db.created'))).StartsWith($original.installationId+"`n") -or
    (Hash (Join-Path $module 'dist/game/config/Database.ini')) -cne $original.sourceDatabaseSha256){throw 'OWN106_ORIGINAL_AUTHORITY_DRIFT'}
function Sql([string]$Query,[string]$Output){
    # Only callers' fixed read-only queries; credentials remain in the original private option file.
    $rows=@($Query|& $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>($Output+'.stderr.private.txt'))
    if($LASTEXITCODE -ne 0){throw 'OWN106_READONLY_SQL_FAILED'}
    New-Text $Output (($rows -join "`n")+"`n")
    return $rows
}
function Clearance([string]$Output){
    Java0
    $null=New-Item -ItemType Directory -Path $Output
    $grants=@(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $Output 'grants.private.tsv'))
    if(!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')){throw 'OWN106_FULL_VISIBILITY_REQUIRED'}
    $authority=@(Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' (Join-Path $Output 'authority.private.tsv'))
    if($authority.Count -ne 1 -or $authority[0] -cne ($original.installationId+"`t"+$original.sourceHead+"`t"+$original.inputsSha256)){throw 'OWN106_SQL_AUTHORITY_DRIFT'}
    $sessions=@(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $Output 'sessions.private.tsv'))
    if($sessions.Count -ne 0){throw 'OWN106_FOREIGN_OR_OWN_SQL_PRESENT'}
    foreach($cut in @('first','second')){
        $null=Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $Output ($cut+'-preimage.private.tsv'))
    }
    if((Hash (Join-Path $Output 'first-preimage.private.tsv')) -cne (Hash (Join-Path $Output 'second-preimage.private.tsv'))){throw 'OWN106_UNSTABLE_PREIMAGE'}
    Java0
    New-Text (Join-Path $Output 'clearance.json') (@{JVM=0;SQL=0;TCP=0;foreign=0;fullVisibility=$true;authorityExact=$true;stable=$true;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
}
function Protected {
    $pins=Get-Content -LiteralPath (Join-Path $prior.archive 'protected-source105.json') -Raw|ConvertFrom-Json
    foreach($pin in $pins){if((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256){throw 'OWN106_PROTECTED_SOURCE_DRIFT'}}
}
if($Action -eq 'Audit'){
    Java0;Protected
    Private-Directory $index
    $archive=Join-Path (Split-Path -Parent $origin) ('TASK106-'+[guid]::NewGuid().ToString('N'))
    Private-Directory $archive
    New-Text (Join-Path $index 'index.private.json') (@{archive=$archive;origin=$origin;stage=(Join-Path $archive 'stage106');preparationId=[guid]::NewGuid().ToString();head=(& git -C $repo rev-parse HEAD).Trim();createdUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
    Clearance (Join-Path $archive 'g0')
    Copy-Item -LiteralPath (Join-Path $prior.archive 'protected-source105.json') -Destination (Join-Path $archive 'protected-source.private.json')
    Write-Output 'G0_106_PASS originalAuthority=EXACT;fullVisibility=true;foreign=0;JVM=0;SQL=0;TCP=0;stable=true;protected173=UNCHANGED;SQL_WRITES=0'
    return
}
Assert-Private $index
$info=Get-Content -LiteralPath (Join-Path $index 'index.private.json') -Raw|ConvertFrom-Json
$archive=Physical $info.archive
Assert-Private $archive
$stage=$info.stage
if($Action -in @('Forensic','Cleanup')){
    $stage=Physical $stage;Assert-Private $stage
    $receipt=Join-Path $stage 'private/receipts106/process6.private.json'
    $expected=Get-Content -LiteralPath $receipt -Raw|ConvertFrom-Json
    if($Action -eq 'Cleanup'){
        $forensic=Get-Content -LiteralPath (Join-Path $index 'forensic106.private.json') -Raw|ConvertFrom-Json
        if($ApprovedProcessReceiptSHA256 -cne (Hash $receipt) -or $ApprovedProcessReceiptSHA256 -cne $forensic.processReceiptSHA256 -or
            !$forensic.stable -or $forensic.foreignSQL -ne 0 -or (Test-Path -LiteralPath (Join-Path $archive 'force106.begun'))){throw 'OWN106_EXACT_NEW_HUMAN_APPROVAL_REQUIRED_SINGLE_FORCE'}
    }
    $seal=Properties (Join-Path $stage 'private/recovery106.properties')
    $db=Properties (Join-Path $module 'dist/game/config/Database.ini')
    function Exact106 {
        $os=Get-CimInstance Win32_Process -Filter ('ProcessId='+$expected.PID)
        if(!$os){throw 'OWN106_PROCESS_ABSENT_NO_FORCE'}
        $native=Get-Process -Id $os.ProcessId
        $digest=[Security.Cryptography.SHA256]::Create()
        try{$commandHash=([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-','').ToLowerInvariant()}finally{$digest.Dispose()}
        if($os.Name -cne 'java.exe' -or $os.CreationDate.ToUniversalTime() -ne ([DateTime]$expected.CreationUTC).ToUniversalTime() -or
            [Math]::Abs($native.StartTime.ToUniversalTime().Ticks-$os.CreationDate.ToUniversalTime().Ticks) -ge 10 -or
            (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or $expected.SID -cne $sid -or
            (Physical $os.ExecutablePath) -ine (Physical $expected.Executable) -or $commandHash -cne $expected.CommandSHA256 -or
            !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($original.installationId) -or
            !$os.CommandLine.Contains('PhantomPopulation102Native') -or !$os.CommandLine.Contains('recovery106A') -or
            (Hash (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar')) -cne $expected.JarSHA256 -or $expected.JarSHA256 -cne $seal.jarSha256){throw 'OWN106_EXACT_INCARNATION_MISMATCH'}
        if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 1){throw 'OWN106_FOREIGN_JVM'}
        return $os
    }
    $null=Exact106
    $out=Join-Path $archive ('retained106-'+[guid]::NewGuid().ToString('N'))
    Private-Directory $out
    Copy-Item -LiteralPath $receipt -Destination (Join-Path $out 'process6.private.json')
    $grants=@(Sql 'SHOW GRANTS FOR CURRENT_USER;' (Join-Path $out 'grants.private.tsv'))
    if(!($grants -join '').Contains('GRANT ALL PRIVILEGES ON *.*')){throw 'OWN106_FORENSIC_FULL_VISIBILITY_REQUIRED'}
    foreach($cut in @('first','second')){
        $os=Exact106
        $tcp=@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.OwningProcess -eq $expected.PID})
        New-Text (Join-Path $out ($cut+'-tcp.private.json')) (ConvertTo-Json -InputObject $tcp -Depth 3)
        $sessions=@(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $out ($cut+'-sessions.private.tsv')))
        foreach($row in $sessions){
            $columns=$row -split "`t"
            if($columns.Count -lt 4 -or $columns[1] -cne $db.Login -or $columns[3] -cne $original.schema -or $columns[2] -notmatch '^(?:127\.0\.0\.1|localhost):([0-9]+)$'){throw 'OWN106_FOREIGN_SQL_SESSION'}
            $port=[int]$matches[1]
            if(@($tcp|Where-Object {$_.State -eq 'Established' -and $_.RemotePort -eq 3308 -and $_.LocalPort -eq $port -and $_.LocalAddress -eq '127.0.0.1' -and $_.RemoteAddress -eq '127.0.0.1'}).Count -ne 1){throw 'OWN106_SQL_TCP_ATTRIBUTION_FAILED'}
        }
        & (Join-Path (Split-Path -Parent $os.ExecutablePath) 'jcmd.exe') $expected.PID Thread.print -l 1>(Join-Path $out ($cut+'-threads.private.txt')) 2>(Join-Path $out ($cut+'-jcmd.stderr.private.txt'))
        if($LASTEXITCODE -ne 0){throw 'OWN106_READONLY_THREAD_DUMP_FAILED'}
        $null=Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $out ($cut+'-preimage.private.tsv'))
        $null=Exact106
    }
    Protected
    $stable=(Hash (Join-Path $out 'first-preimage.private.tsv')) -ceq (Hash (Join-Path $out 'second-preimage.private.tsv'))
    if($Action -eq 'Cleanup'){
        if(!$stable){throw 'OWN106_UNSTABLE_PRE_FORCE_NO_CLEANUP'}
        New-Text (Join-Path $out 'approval106.private.json') (@{approvedReceiptSHA256=$ApprovedProcessReceiptSHA256;scope='One targeted force exact retained A106; no B/retry/TASK107';authority='Explicit human reply in current chat';utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
        $null=Exact106
        New-Text (Join-Path $archive 'force106.begun') ('approvedReceiptSHA256='+$ApprovedProcessReceiptSHA256+';utc='+[DateTime]::UtcNow.ToString('o'))
        # Explicit operator-only action; there is no force fallback in any runtime/STOP path.
        Stop-Process -Id $expected.PID -Force -ErrorAction Stop
        for($wait=0;$wait -lt 30;$wait++){
            if($null -eq (Get-CimInstance Win32_Process -Filter ('ProcessId='+$expected.PID))){break}
            Start-Sleep -Milliseconds 500
        }
        Java0
        $after=@(Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' (Join-Path $out 'after-sessions.private.tsv'))
        if($after.Count -ne 0){throw 'OWN106_POST_FORCE_SQL_NOT_ZERO'}
        $null=Sql ([IO.File]::ReadAllText((Physical (Join-Path $prior.archive 'first-preimage.sql')))) (Join-Path $out 'after-preimage.private.tsv')
        $unchanged=(Hash (Join-Path $out 'second-preimage.private.tsv')) -ceq (Hash (Join-Path $out 'after-preimage.private.tsv'))
        Protected
        New-Text (Join-Path $out 'cleanup106.json') (@{status='FORCED_UNCLEAN';forceCount=1;JVM=0;SQL=0;TCP=0;fullVisibility=$true;dataUnchanged=$unchanged;naturalExit0='NOT_PROVEN';newRuntimeAllowed=$false;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
        $manifest=@(Get-ChildItem -LiteralPath $out -File|ForEach-Object {@{name=$_.Name;sha256=(Hash $_.FullName);bytes=$_.Length}})
        New-Text (Join-Path $out 'manifest.private.json') ($manifest|ConvertTo-Json)
        New-Text (Join-Path $index 'cleanup106.private.json') (@{archive=$out;processReceiptSHA256=(Hash $receipt);status='FORCED_UNCLEAN';forceCount=1;JVM=0;SQL=0;TCP=0;dataUnchanged=$unchanged;newRuntimeAllowed=$false}|ConvertTo-Json)
        Write-Output "OWN106_FORCED_UNCLEAN exactApprovedForce=1;JVM=0;SQL=0;TCP=0;dataUnchanged=$unchanged;B_RETRY_TASK107=FORBIDDEN"
        return
    }
    New-Text (Join-Path $out 'audit.json') (@{status='RETAINED106_STOP_BLOCKED';exactIncarnation=$true;fullVisibility=$true;ownSQL=$sessions.Count;foreignSQL=0;JVM=1;stable=$stable;forceApproval=$false;newRuntimeAllowed=$false;processReceiptSHA256=(Hash $receipt);utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
    $manifest=@(Get-ChildItem -LiteralPath $out -File|ForEach-Object {@{name=$_.Name;sha256=(Hash $_.FullName);bytes=$_.Length}})
    New-Text (Join-Path $out 'manifest.private.json') ($manifest|ConvertTo-Json)
    New-Text (Join-Path $index 'forensic106.private.json') (@{archive=$out;processReceiptSHA256=(Hash $receipt);stable=$stable;ownSQL=$sessions.Count;foreignSQL=0;forceApproval=$false}|ConvertTo-Json)
    Write-Output "OWN106_FORENSIC_PRESERVED exactIncarnation=true;JVM=1;ownSQL=$($sessions.Count);foreignSQL=0;stable=$stable;force=0;noNextRuntime=true"
    return
}
Java0;Protected
if($Action -eq 'Prepare'){
    Clearance (Join-Path $archive 'before-prepare')
    & (Join-Path $PSScriptRoot 'Task102.ps1') -Action Prepare -SourceRoot $module -StageRoot $stage -InstallationId $info.preparationId
    if(!$?){throw 'OWN106_PREPARE_FAILED'}
    return
}
$stage=Physical $stage;Assert-Private $stage
$shadow=Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
$private=Physical (Join-Path $stage 'private')
$game=Physical (Join-Path $shadow 'dist/game')
$database=Join-Path $game 'config/Database.ini'
$receipts=Join-Path $private 'receipts106'
$cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $shadow 'dist/libs/*')
if($Action -eq 'Bind'){
    Clearance (Join-Path $archive 'before-bind')
    if(Test-Path -LiteralPath $database){throw 'OWN106_FRESH_PREPARE_ONLY'}
    $prepared=Properties (Join-Path $private 'owner.properties')
    if($prepared.installationId -cne $info.preparationId -or (Hash (Join-Path $stage 'inputs.tsv')) -cne $prepared.inputsSha256){throw 'OWN106_PREPARATION_DRIFT'}
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared106-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source106-inputs.tsv')
    $owner=[IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
    $owner=[regex]::Replace($owner,'(?m)^stage=.*$',('stage='+$stage.Replace('\','/')))
    $owner=[regex]::Replace($owner,'(?m)^expiresUtc=.*$',('expiresUtc='+[DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'),$owner,[Text.UTF8Encoding]::new($false))
    foreach($relative in @('inputs.tsv','private/db.created')){Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force}
    Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path $receipts|Out-Null
    Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime106-final.ini')
    $ini=[IO.File]::ReadAllText((Join-Path $private 'runtime106-final.ini'))
    foreach($key in @('PhantomPopulationActiveTarget','MaxMaterializedPhantoms')){
        $pattern='(?m)^('+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
        if([regex]::Matches($ini,$pattern).Count -ne 1){throw 'OWN106_OVERLAY_KEY'}
        $ini=[regex]::Replace($ini,$pattern,{param($m)$m.Groups[1].Value+'16'})
    }
    [IO.File]::WriteAllText((Join-Path $game 'config/Custom/PhantomPlayers.ini'),$ini,[Text.UTF8Encoding]::new($false))
    Write-Output 'OWN106_BOUND originalDB=true;newProfiles=0;privateCap=16;source128=UNCHANGED'
    return
}
if($Action -eq 'Seal'){
    Clearance (Join-Path $archive 'before-seal')
    if(Test-Path -LiteralPath (Join-Path $private 'recovery106.properties')){throw 'OWN106_SINGLE_SEAL_REQUIRED'}
    $rows=[Collections.Generic.List[string]]::new()
    foreach($root in @($shadow,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
        foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
            $relative=(Physical $file.FullName).Substring($stage.Length+1).Replace('\','/')
            if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
            $rows.Add($relative+"`t"+(Hash $file.FullName))
        }
    }
    New-Text (Join-Path $private 'native-inputs.tsv') (($rows|Sort-Object) -join "`n")
    $pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini')|ForEach-Object{$_+'='+(Hash (Join-Path $shadow $_))}
    New-Text (Join-Path $private 'native.properties') (($pins -join "`n")+"`nnativeInputsSha256="+(Hash (Join-Path $private 'native-inputs.tsv')))
    New-Text (Join-Path $private 'recovery106.properties') ("task=PHANTOM-MERCHANT-IDLE-RECOVERY-106`nnoReseed=true`njarSha256="+(Hash (Join-Path $shadow 'dist/libs/GameServer.jar'))+"`nsourceOwnerSha256="+(Hash (Join-Path $module 'dist/game/config/OwnerConfig.json'))+"`nsourceIniSha256="+(Hash (Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')))
    Write-Output 'OWN106_SEALED namespace=106;SAME_DB=true;cap=16;productionPatch=0'
    return
}
function Census([string]$Cut){
    Clearance (Join-Path $archive ('clearance-'+$Cut))
    $out=Join-Path $receipts ('readonly-'+$Cut)
    New-Item -ItemType Directory -Path $out|Out-Null
    & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
    if($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true'){throw 'OWN106_READONLY_CENSUS_FAILED'}
    Java0
    return $out
}
$seal=Properties (Join-Path $private 'recovery106.properties')
foreach($pair in @(@('jarSha256',(Join-Path $shadow 'dist/libs/GameServer.jar')),@('sourceOwnerSha256',(Join-Path $module 'dist/game/config/OwnerConfig.json')),@('sourceIniSha256',(Join-Path $module 'dist/game/config/Custom/PhantomPlayers.ini')))){
    if($seal[$pair[0]] -cne (Hash $pair[1])){throw 'OWN106_SEAL_DRIFT'}
}
$phase=if($Action -eq 'A'){6}else{4}
if(Test-Path -LiteralPath (Join-Path $receipts "launch$phase.begun")){throw 'OWN106_SINGLE_GENERATION_REQUIRED'}
if($Action -eq 'B'){
    foreach($name in @('phase6.complete','stopped6.checkpoint','stop6-world-leases.txt','exit6.clean')){if(!(Test-Path -LiteralPath (Join-Path $receipts $name))){throw 'OWN106_A_FULL_CLEAN_REQUIRED'}}
    if([IO.File]::ReadAllText((Join-Path $receipts 'exit6.clean')) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;'){throw 'OWN106_A_NATURAL_EXIT_REQUIRED'}
    if(![IO.File]::ReadAllText((Join-Path $receipts 'merchants106-second6.txt')).Contains('open=250;')){throw 'OWN106_DIAGNOSTIC_A_REQUIRES_CAUSAL_REVIEW_BEFORE_B'}
}
$pre=Census ('before'+$Action)
if($Action -eq 'B' -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts 'stopped6.checkpoint')).TrimEnd([char[]]"`r`n")){throw 'OWN106_PRESTART_CHECKPOINT_DRIFT'}
New-Text (Join-Path $receipts "launch$phase.begun") ('utc='+[DateTime]::UtcNow.ToString('o'))
$uuid=$original.installationId
$arguments=@('-Xms512m','-Xmx8g','-Dphantom.task102.native=true',"-Dphantom.task102.stage=$stage","-Dphantom.task102.uuid=$uuid","-Dphantom.module.root=$shadow","-Dphantom.test.config=$database",'-cp',$cp,'org.l2jmobius.tests.phantoms.PhantomPopulation102Native',('recovery106'+$Action))
$line=($arguments|ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
$process=Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
$os=Get-CimInstance Win32_Process -Filter ('ProcessId='+$process.Id)
if(!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)){throw 'OWN106_NEW_PROCESS_IDENTITY_UNPROVEN_NO_RETRY'}
$digest=[Security.Cryptography.SHA256]::Create()
try{$commandHash=([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($os.CommandLine)))).Replace('-','').ToLowerInvariant()}finally{$digest.Dispose()}
New-Text (Join-Path $receipts "process$phase.private.json") (@{PID=$process.Id;CreationUTC=$os.CreationDate.ToUniversalTime().ToString('o');SID=$sid;Executable=$os.ExecutablePath;CommandSHA256=$commandHash;Stage=$stage;Installation=$uuid;JarSHA256=$seal.jarSha256}|ConvertTo-Json)
Write-Output "OWN106_NATIVE_STARTED generation=$Action;cap=16;newProfiles=FORBIDDEN"
$deadline=[DateTime]::UtcNow.AddMinutes(12)
while(!$process.WaitForExit(1000)){
    if((Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")) -or [DateTime]::UtcNow -gt $deadline){
        New-Text (Join-Path $receipts "retained$phase.private.json") (@{PID=$process.Id;CreationUTC=$os.CreationDate.ToUniversalTime().ToString('o');SID=$sid;Executable=$os.ExecutablePath;CommandSHA256=$commandHash;Stage=$stage;Installation=$uuid;JarSHA256=$seal.jarSha256;status='STOP_BLOCKED_NO_RETRY';forceApproval=$false}|ConvertTo-Json)
        throw 'OWN106_STOP_BLOCKED;NO_B;NO_RETRY;NO_FORCE'
    }
}
$exit=$process.ExitCode
New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc="+[DateTime]::UtcNow.ToString('o'))
if($exit -ne 0){throw 'OWN106_NATIVE_EXIT_FAILED_NO_NEXT_RUNTIME'}
Java0
$post=Census ('after'+$Action)
$boundary=[IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))
if(!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
    $boundary -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=([0-9]+);attributedMerchants=([0-9]+);samples=2;nativePersisted=true\r?\n?$' -or $matches[1] -cne $matches[2]){throw 'OWN106_FULL_NATIVE_STOP_PROOF_REQUIRED'}
if([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")){throw 'OWN106_POSTEXIT_CHECKPOINT_DRIFT'}
New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;jarSha256="+$seal.jarSha256)
Protected
Write-Output "OWN106_GENERATION_CLEAN generation=$Action;cap=16;JVM=0;SQL=0;TCP=0;allResidualLeasesAttributed=true"
