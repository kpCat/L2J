[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit104','CheckSessionOwnership')][string]$Action,
    [string]$SqlClient
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Physical([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    for($walk=$item;$null -ne $walk;$walk=if($walk -is [IO.FileInfo]){$walk.Directory}else{$walk.Parent}) {
        if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN105_REPARSE'}
    }
    return $item.FullName
}
function Hash([string]$Path){(Get-FileHash -LiteralPath (Physical $Path) -Algorithm SHA256).Hash.ToLowerInvariant()}
function TextHash([string]$Value){
    $digest=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-','').ToLowerInvariant()}finally{$digest.Dispose()}
}
function New-Text([string]$Path,[string]$Value){
    $stream=[IO.File]::Open($Path,'CreateNew','Write','None')
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
}
function Owned-Session([string[]]$Columns,[object[]]$Connections,[int]$ExpectedPid,[string]$Schema,[string]$Login){
    if($Columns.Count -lt 4 -or $Columns[3] -cne $Schema -or $Columns[1] -cne $Login -or $Columns[2] -notmatch '^(?:127\.0\.0\.1|localhost):([0-9]{1,5})$'){return $false}
    $port=[int]$matches[1]
    $matched=@($Connections|Where-Object {$_.OwningProcess -eq $ExpectedPid -and $_.LocalPort -eq $port -and $_.RemotePort -eq 3308 -and
        $_.State -eq 'Established' -and $_.LocalAddress -eq '127.0.0.1' -and $_.RemoteAddress -eq '127.0.0.1'})
    return $matched.Count -eq 1
}
if($Action -eq 'CheckSessionOwnership'){
    $schema='l2jmobiush5_phantom_test_102'
    $row=@('1','fixture','127.0.0.1:50001',$schema,'Sleep','0','')
    $own=[pscustomobject]@{OwningProcess=42;LocalPort=50001;RemotePort=3308;State='Established';LocalAddress='127.0.0.1';RemoteAddress='127.0.0.1'}
    $foreign=[pscustomobject]@{OwningProcess=43;LocalPort=50001;RemotePort=3308;State='Established';LocalAddress='127.0.0.1';RemoteAddress='127.0.0.1'}
    if(!(Owned-Session $row @($own) 42 $schema 'fixture')){throw 'OWN105_VALID_SESSION_REJECTED'}
    if(Owned-Session $row @($foreign) 42 $schema 'fixture'){throw 'OWN105_SAME_SCHEMA_FOREIGN_SESSION_ACCEPTED'}
    if(Owned-Session $row @($own,$own) 42 $schema 'fixture'){throw 'OWN105_AMBIGUOUS_TCP_ACCEPTED'}
    $row[3]='different_schema'
    if(Owned-Session $row @($own) 42 $schema 'fixture'){throw 'OWN105_FOREIGN_SCHEMA_ACCEPTED'}
    Write-Output 'SESSION105_CHECK_PASS valid=1;sameSchemaForeign=REJECTED;ambiguousTCP=REJECTED;foreignSchema=REJECTED;SQL=0;OS=0'
    return
}
function Properties([string]$Path){
    $result=@{}
    foreach($line in [IO.File]::ReadAllLines((Physical $Path))){if($line -match '^([^#=]+)=(.*)$'){$result[$matches[1].Trim()]=$matches[2].Trim()}}
    return $result
}
function Private-Directory([string]$Path){
    if(Test-Path -LiteralPath $Path){throw 'OWN105_FRESH_PRIVATE_DIRECTORY_REQUIRED'}
    New-Item -ItemType Directory -Path $Path|Out-Null
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner([Security.Principal.SecurityIdentifier]::new($sid))
    $acl.SetAccessRuleProtection($true,$false)
    foreach($id in @($sid,'S-1-5-18')){
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($id),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
    Physical $Path|Out-Null
}
$module=Physical (Join-Path $PSScriptRoot '../..')
$repo=Physical (Split-Path -Parent $module)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$prior=Join-Path $repo '.phantom-local/logs/PHANTOM-LOCK-ORDER-RECOVERY-104'
$e=Get-Content -LiteralPath (Join-Path $prior 'evidence104.private.json') -Raw|ConvertFrom-Json
$retained=Get-Content -LiteralPath (Join-Path $prior 'retained104.private.json') -Raw|ConvertFrom-Json
$stage=Physical $e.stage104
$origin=Physical $e.origin
$owner=Properties (Join-Path $stage 'private/owner.properties')
$original=Properties (Join-Path $origin 'private/owner.properties')
$seal=Properties (Join-Path $stage 'private/recovery104.properties')
$nativePins=Properties (Join-Path $stage 'private/native.properties')
$jar=Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar'
foreach($root in @($stage,$origin,(Physical $e.archive))){
    $acl=Get-Acl -LiteralPath $root
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN105_PRIOR_PRIVATE_ACL'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN105_PRIOR_NONPRIVATE_ACL'}}
}
if($owner.owner -cne 'TASK102_POPULATION_SCALE' -or $owner.schema -cne 'l2jmobiush5_phantom_test_102' -or
    $owner.ownerSid -cne $sid -or $owner.installationId -cne $original.installationId -or
    (Physical $owner.stage) -ine $stage -or $seal.jarSha256 -cne (Hash $jar) -or
    $owner.inputsSha256 -cne (Hash (Join-Path $stage 'inputs.tsv')) -or
    $nativePins.nativeInputsSha256 -cne (Hash (Join-Path $stage 'private/native-inputs.tsv')) -or
    $original.inputsSha256 -cne (Hash (Join-Path $origin 'inputs.tsv'))) {throw 'OWN105_PRIOR_AUTHORITY_MISMATCH'}
function Exact-Process {
    $process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$retained.PID)
    if($null -eq $process){throw 'OWN105_PRIOR_PROCESS_ABSENT_REQUIRES_UNCLEAN_CLEARANCE_AUDIT'}
    $processSid=(Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid).Sid
    $native=Get-Process -Id $process.ProcessId
    if($process.Name -cne 'java.exe' -or $process.CreationDate.ToUniversalTime() -ne ([DateTime]$retained.CreationUTC).ToUniversalTime() -or
        [Math]::Abs($native.StartTime.ToUniversalTime().Ticks-$process.CreationDate.ToUniversalTime().Ticks) -ge 10 -or $processSid -cne $sid -or
        (TextHash $process.CommandLine) -cne $retained.CommandSHA256 -or !$process.CommandLine.Contains($stage) -or
        !$process.CommandLine.Contains($owner.installationId) -or !$process.CommandLine.Contains('PhantomPopulation102Native recovery104A') -or
        (Physical $process.ExecutablePath) -ine (Physical (Get-Command java.exe).Source) -or (Hash $jar) -cne $seal.jarSha256){throw 'OWN105_EXACT_PROCESS_MISMATCH'}
    [pscustomobject]@{PID=$process.ProcessId;CreationUTC=$process.CreationDate.ToUniversalTime();SID=$processSid;Executable=$process.ExecutablePath;Command=$process.CommandLine;CommandSHA256=(TextHash $process.CommandLine);Stage=$stage;Installation=$owner.installationId;JarSHA256=(Hash $jar)}
}
$null=Exact-Process
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 1){throw 'OWN105_UNKNOWN_JVM'}
$client=Physical $SqlClient
if([IO.Path]::GetFileName($client) -cne 'mariadb.exe'){throw 'OWN105_MARIADB_CLIENT_REQUIRED'}
$archive=Join-Path (Split-Path -Parent (Physical $e.archive)) ('TASK105-'+[guid]::NewGuid().ToString('N'))
Private-Directory $archive
$index=Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105'
if(!(Test-Path -LiteralPath $index)){Private-Directory $index}
$indexAcl=Get-Acl -LiteralPath (Physical $index)
if(!$indexAcl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($indexAcl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN105_INDEX_ACL'}
foreach($rule in $indexAcl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN105_INDEX_NOT_PRIVATE'}}
New-Text (Join-Path $index ((Split-Path -Leaf $archive)+'.private.json')) (@{archive=$archive;origin=$origin;stage104=$stage;createdUtc=[DateTime]::UtcNow.ToString('o');status='READ_ONLY_AUDIT_IN_PROGRESS';forceApproval=$false}|ConvertTo-Json)
New-Text (Join-Path $archive 'process-before.private.json') ((Exact-Process)|ConvertTo-Json)
foreach($name in @('evidence104.private.json','retained104.private.json')){Copy-Item -LiteralPath (Join-Path $prior $name) -Destination (Join-Path $archive $name)}
foreach($name in @('owner.properties','recovery104.properties','native.properties','generationA-native.properties','native-inputs.tsv','origin103-ids.tsv')){
    Copy-Item -LiteralPath (Join-Path $stage ('private/'+$name)) -Destination (Join-Path $archive ('prior-'+$name))
}
Copy-Item -LiteralPath (Join-Path $stage 'private/receipts104') -Destination (Join-Path $archive 'receipts104') -Recurse
Copy-Item -LiteralPath (Join-Path $e.archive 'native16.log') -Destination (Join-Path $archive 'native104A.private.log')
$protected=Get-Content -LiteralPath (Join-Path $e.archive 'protected-source.json') -Raw|ConvertFrom-Json
$pins=@()
foreach($entry in $protected){
    $path=Join-Path $module $entry.relative
    $actual=Hash $path
    if($actual -cne $entry.sha256.ToLowerInvariant()){throw 'OWN105_PROTECTED_SOURCE_DRIFT'}
    $pins+=@{relative=('L2J_Mobius_CT_2.6_HighFive/'+$entry.relative);sha256=$actual}
}
foreach($relative in @('dist/game/config/OwnerConfig.json','dist/game/config/Custom/PhantomPlayers.ini','dist/libs/GameServer.jar')){
    $path=Join-Path $module $relative
    $pins+=@{relative=('L2J_Mobius_CT_2.6_HighFive/'+$relative);sha256=(Hash $path)}
}
New-Text (Join-Path $archive 'protected-source105.json') ($pins|ConvertTo-Json)
$sourceDb=Join-Path $module 'dist/game/config/Database.ini'
if((Hash $sourceDb) -cne $original.sourceDatabaseSha256){throw 'OWN105_SQL_CREDENTIAL_ORIGIN_DRIFT'}
$database=Properties $sourceDb
if($database.URL -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5(?:\?[^\r\n]*)?$'){throw 'OWN105_LOOPBACK_SQL_REQUIRED'}
# Credentials remain in an owner+SYSTEM file, never on the command line or console.
function Option([string]$Value){'"'+$Value.Replace('\','\\').Replace('"','\"')+'"'}
$options=Join-Path $archive 'sql-client.private.cnf'
New-Text $options ("[client]`nhost=127.0.0.1`nport=3308`nprotocol=tcp`nuser="+(Option $database.Login)+"`npassword="+(Option $database.Password)+"`ndefault-character-set=utf8mb4`n")
function Sql([string]$Query,[string]$Name){
    $sqlPath=Join-Path $archive ($Name+'.sql')
    New-Text $sqlPath $Query
    $output=Join-Path $archive ($Name+'.private.tsv')
    $errorPath=Join-Path $archive ($Name+'.stderr.private.txt')
    Get-Content -LiteralPath $sqlPath -Raw|& $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>$errorPath|Out-File -LiteralPath $output -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'OWN105_READONLY_SQL_FAILED_SEE_PRIVATE_STDERR'}
    return $output
}
$grants=Sql 'SHOW GRANTS FOR CURRENT_USER;' 'grants'
if(!(Get-Content -LiteralPath $grants -Raw).Contains('GRANT ALL PRIVILEGES ON *.*')){throw 'OWN105_FULL_SQL_VISIBILITY_REQUIRED'}
$authority=Sql 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' 'authority'
$actualAuthority=(Get-Content -LiteralPath $authority -Raw).Trim()
if($actualAuthority -cne ($original.installationId+"`t"+$original.sourceHead+"`t"+$original.inputsSha256)){throw 'OWN105_DURABLE_AUTHORITY_MISMATCH'}
$queries=[ordered]@{
    profiles='SELECT profile_id,character_object_id FROM phantom_profiles ORDER BY profile_id;'
    characters='SELECT * FROM characters ORDER BY charId;'
    inventory='SELECT * FROM items ORDER BY object_id;'
    components='SELECT profile_id,component_type,component_schema_version,row_version,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type;'
    recipebook='SELECT * FROM character_recipebook ORDER BY charId,id,classIndex;'
    offliners='SELECT * FROM character_offline_trade ORDER BY charId;'
    offlineItems='SELECT * FROM character_offline_trade_items ORDER BY charId,item;'
    accountAccess='SELECT login,accessLevel FROM accounts ORDER BY login;'
    columns='SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (''phantom_profiles'',''characters'',''items'',''phantom_profile_components'',''character_recipebook'',''character_offline_trade'',''character_offline_trade_items'') ORDER BY TABLE_NAME,ORDINAL_POSITION;'
}
foreach($cut in @('first','second')){
    $process=Exact-Process
    New-Text (Join-Path $archive ($cut+'-process.private.json')) ($process|ConvertTo-Json)
    $tcp=@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.OwningProcess -eq $process.PID})
    New-Text (Join-Path $archive ($cut+'-tcp.private.json')) (ConvertTo-Json -InputObject $tcp -Depth 3)
    & (Join-Path (Split-Path -Parent $process.Executable) 'jcmd.exe') $process.PID Thread.print -l 1>(Join-Path $archive ($cut+'-threads.private.txt')) 2>(Join-Path $archive ($cut+'-jcmd.stderr.private.txt'))
    if($LASTEXITCODE -ne 0){throw 'OWN105_READONLY_JCMD_FAILED'}
    $null=Exact-Process
    $sessionPath=Sql 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' ($cut+'-sessions')
    $sessions=@(Get-Content -LiteralPath $sessionPath|Where-Object {$_})
    foreach($session in $sessions){if(!(Owned-Session ($session -split "`t") $tcp $process.PID $owner.schema $database.Login)){throw 'OWN105_UNATTRIBUTED_OR_FOREIGN_SQL_SESSION'}}
    $sql="SET SESSION TRANSACTION READ ONLY;`nSTART TRANSACTION WITH CONSISTENT SNAPSHOT;`n"
    foreach($key in $queries.Keys){$sql+="SELECT 'SLICE:$key';`n"+$queries[$key]+"`n"}
    $sql+="ROLLBACK;`n"
    $null=Sql $sql ($cut+'-preimage')
}
$stable=(Hash (Join-Path $archive 'first-preimage.private.tsv')) -ceq (Hash (Join-Path $archive 'second-preimage.private.tsv'))
$summary=@{status='RETAINED104';identityMatched=$true;preimagesStable=$stable;forceApproval=$false;native105Runs=0;ownSessions=$sessions.Count;foreignSessions=0;jarSha256=(Hash $jar);protectedPins=$pins.Count;readOnlyJcmdExit=0;supportedGracefulControl='NOT_FOUND_IN_HARNESS';sqlWrites=0}
New-Text (Join-Path $archive 'audit105.json') ($summary|ConvertTo-Json)
$manifest=@(Get-ChildItem -LiteralPath $archive -File -Recurse|ForEach-Object {@{relative=$_.FullName.Substring($archive.Length+1);sha256=(Hash $_.FullName);bytes=$_.Length}})
New-Text (Join-Path $archive 'preimage105-sha256.json') ($manifest|ConvertTo-Json)
New-Text (Join-Path $index 'audit105-complete.private.json') (@{archive=$archive;origin=$origin;stage104=$stage;createdUtc=[DateTime]::UtcNow.ToString('o');preimagesStable=$stable;forceApproval=$false}|ConvertTo-Json)
$summary|ConvertTo-Json
