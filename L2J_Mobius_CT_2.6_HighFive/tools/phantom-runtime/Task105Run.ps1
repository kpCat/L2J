[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Audit104','CheckSessionOwnership','CheckBoundary','Clearance','Bind','Seal','A','B','C')][string]$Action,
    [string]$SqlClient,
    [string]$StageRoot
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
function Valid-Boundary105([string]$Value){
    if($Value -cnotmatch '^ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=([0-9]+);attributedMerchants=([0-9]+);samples=2;nativePersisted=true\r?\n?$'){return $false}
    return [long]$matches[1] -eq [long]$matches[2]
}
if($Action -eq 'CheckBoundary'){
    $value="ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=17;attributedMerchants=17;samples=2;nativePersisted=true`n"
    if(!(Valid-Boundary105 $value)){throw 'OWN105_LAWFUL_RESIDUAL_REJECTED'}
    if(Valid-Boundary105 $value.Replace('global=17','global=18')){throw 'OWN105_UNATTRIBUTED_TOTAL_ACCEPTED'}
    if(Valid-Boundary105 $value.Replace('unknown=0','unknown=1')){throw 'OWN105_UNKNOWN_ACCEPTED'}
    if(Valid-Boundary105 "ordinaryWorld=0;allLeases=0;observerOwners=0`n"){throw 'OWN105_LEGACY_ZERO_ACCEPTED'}
    Write-Output 'BOUNDARY105_CHECK_PASS lawfulResidual=ACCEPTED;unknownAndUnattributedAndLegacy=REJECTED;SQL=0;OS=0'
    return
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
if($Action -in @('Bind','Seal','A','B','C')){
    $index=Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105'
    $resumeInfo=Get-Content -LiteralPath (Join-Path $index 'resume105.private.json') -Raw|ConvertFrom-Json
    $resume=Physical $resumeInfo.resume
    $stageInfo=Get-Content -LiteralPath (Join-Path $resume 'stage105.private.json') -Raw|ConvertFrom-Json
    $stage=Physical $StageRoot
    $origin=Physical $resumeInfo.origin
    if($stage -ine (Physical $stageInfo.stage) -or $stage -ieq $origin -or $stage.StartsWith($origin+'\',[StringComparison]::OrdinalIgnoreCase) -or
        $stage -ieq $repo -or $stage.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN105_EXACT_NEW_EXTERNAL_STAGE_REQUIRED'}
    foreach($root in @($stage,$origin,$resume)){
        $acl=Get-Acl -LiteralPath $root
        if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN105_PRIVATE_OWNER_REQUIRED'}
        foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN105_FOREIGN_ACL'}}
    }
    $clearance=Get-Content -LiteralPath (Join-Path $resume 'clearance105.json') -Raw|ConvertFrom-Json
    if($clearance.JVM -ne 0 -or $clearance.SQL -ne 0 -or $clearance.TCP -ne 0 -or !$clearance.fullVisibility -or !$clearance.preimagesStable){throw 'OWN105_CLEARANCE_PROOF_REQUIRED'}
    function Java0 {
        if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN105_JAVA_PRESENT_NO_NATIVE'}
        if(@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308)}).Count -ne 0){throw 'OWN105_SQL_TCP_PRESENT'}
    }
    Java0
    $source=$module
    $module=Physical (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive')
    $private=Physical (Join-Path $stage 'private')
    $game=Physical (Join-Path $module 'dist/game')
    $database=Join-Path $game 'config/Database.ini'
    $originOwner=[IO.File]::ReadAllText((Physical (Join-Path $origin 'private/owner.properties')))
    $originProperties=Properties (Join-Path $origin 'private/owner.properties')
    if($originProperties.owner -cne 'TASK102_POPULATION_SCALE' -or $originProperties.schema -cne 'l2jmobiush5_phantom_test_102' -or $originProperties.ownerSid -cne $sid){throw 'OWN105_ORIGINAL_AUTHORITY_REQUIRED'}
    $uuid=$originProperties.installationId
    if($Action -eq 'Bind'){
        foreach($path in @('private/db.created','private/native.properties','private/recovery103.properties','private/recovery104.properties','private/recovery105.bound','private/recovery105.properties','private/receipts105','L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini')){
            if(Test-Path -LiteralPath (Join-Path $stage $path)){throw 'OWN105_FRESH_PREPARE_ONLY'}
        }
        $prepared=Properties (Join-Path $private 'owner.properties')
        if($prepared.installationId -cne $stageInfo.preparationId -or $prepared.installationId -ceq $uuid -or (Physical $prepared.stage) -ine $stage -or
            (Physical $prepared.sourceModule) -ine $source -or $prepared.ownerSid -cne $sid -or $prepared.branch -cne 'feature/phantom-world' -or
            $prepared.inputsSha256 -cne (Hash (Join-Path $stage 'inputs.tsv')) -or
            ![IO.File]::ReadAllText((Physical (Join-Path $stage 'prepare.completed'))).Contains('installationId='+$prepared.installationId+"`n")){throw 'OWN105_PREPARATION_MISMATCH'}
        Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared105-owner.properties')
        Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source105-inputs.tsv')
        $owner=[regex]::Replace($originOwner,'(?m)^stage=.*$',('stage='+$stage.Replace('\','/')))
        $owner=[regex]::Replace($owner,'(?m)^expiresUtc=.*$',('expiresUtc='+[DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
        [IO.File]::WriteAllText((Join-Path $private 'owner.properties'),$owner,[Text.UTF8Encoding]::new($false))
        foreach($path in @('inputs.tsv','private/db.created')){
            Copy-Item -LiteralPath (Join-Path $origin $path) -Destination (Join-Path $stage $path) -Force
            if((Hash (Join-Path $origin $path)) -cne (Hash (Join-Path $stage $path))){throw 'OWN105_ORIGIN_COPY_DRIFT'}
        }
        Copy-Item -LiteralPath (Join-Path $origin 'private/receipts-continuation2/ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
        Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
        New-Item -ItemType Directory -Path (Join-Path $private 'receipts105')|Out-Null
        New-Text (Join-Path $private 'recovery105.bound') 'priorExit=UNCLEAN_UNPROVEN;clearance=JVM0_SQL0_TCP0;noReseed=true;DDL_DML=0'
        Write-Output 'OWN105_BOUND freshStage=true;originalAuthority=true;historicalArtifactsUntouched=true;SQL_WRITES=0'
        return
    }
    $cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
    $receipts=Physical (Join-Path $private 'receipts105')
    $client=Physical $SqlClient
    $options=Physical (Join-Path $resumeInfo.archive 'sql-client.private.cnf')
    function Census([string]$Cut){
        Java0
        $out=Join-Path $receipts ('readonly-'+$Cut)
        New-Item -ItemType Directory -Path $out|Out-Null
        & java -cp $cp Task105Census snapshot $origin $out 1>(Join-Path $out 'census.log') 2>(Join-Path $out 'census.stderr.private.txt')
        if($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true'){throw 'OWN105_READONLY_CENSUS_FAILED'}
        Get-Content -LiteralPath (Join-Path $resumeInfo.archive 'first-preimage.sql') -Raw|& $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>(Join-Path $out 'frame.stderr.private.txt')|Out-File -LiteralPath (Join-Path $out 'frame105.private.tsv') -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'OWN105_FRAME_SQL_FAILED'}
        Java0
        return $out
    }
    if($Action -eq 'Seal'){
        if(!(Test-Path -LiteralPath (Join-Path $private 'recovery105.bound')) -or (Test-Path -LiteralPath (Join-Path $private 'recovery105.properties'))){throw 'OWN105_SINGLE_SEAL_REQUIRED'}
        $null=Census 'seal'
        $rows=[Collections.Generic.List[string]]::new()
        foreach($root in @($module,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
            foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
                Physical $file.FullName|Out-Null
                $relative=$file.FullName.Substring($stage.Length+1).Replace('\','/')
                if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
                $rows.Add($relative+"`t"+(Hash $file.FullName))
            }
        }
        New-Text (Join-Path $private 'native-inputs.tsv') (($rows|Sort-Object) -join "`n")
        $pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini')|ForEach-Object{$_+'='+(Hash (Join-Path $module $_))}
        New-Text (Join-Path $private 'native.properties') (($pins -join "`n")+"`nnativeInputsSha256="+(Hash (Join-Path $private 'native-inputs.tsv')))
        Copy-Item -LiteralPath (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime105-final.ini')
        New-Text (Join-Path $private 'recovery105.properties') ("task=PHANTOM-LEASE-STOP-CLOSURE-105`nnoReseed=true`njarSha256="+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))+"`noriginIdsSha256="+(Hash (Join-Path $private 'origin103-ids.tsv'))+"`nsourceOwnerSha256="+(Hash (Join-Path $source 'dist/game/config/OwnerConfig.json'))+"`nsourceIniSha256="+(Hash (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')))
        Write-Output 'OWN105_SEALED originalDB=true;currentJAR=true;allInputsPinned=true'
        return
    }
    $authority=Properties (Join-Path $private 'recovery105.properties')
    foreach($pair in @(@('jarSha256',(Join-Path $module 'dist/libs/GameServer.jar')),@('originIdsSha256',(Join-Path $private 'origin103-ids.tsv')),@('sourceOwnerSha256',(Join-Path $source 'dist/game/config/OwnerConfig.json')),@('sourceIniSha256',(Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')),@('sourceIniSha256',(Join-Path $private 'runtime105-final.ini')))){
        if($authority[$pair[0]] -cne (Hash $pair[1])){throw 'OWN105_SEALED_SOURCE_DRIFT'}
    }
    $phase=switch($Action){'A'{6};'B'{4};'C'{5}}
    $previous=switch($Action){'B'{6};'C'{4};default{0}}
    if(Test-Path -LiteralPath (Join-Path $receipts ("launch$phase.begun"))){throw 'OWN105_SINGLE_GENERATION_REQUIRED'}
    if($previous){
        foreach($name in @("phase$previous.complete","stopped$previous.checkpoint","stop$previous-world-leases.txt","exit$previous.clean")){if(!(Test-Path -LiteralPath (Join-Path $receipts $name))){throw 'OWN105_PREVIOUS_COMPLETE_STOP_REQUIRED'}}
        if(!(Valid-Boundary105 ([IO.File]::ReadAllText((Join-Path $receipts "stop$previous-world-leases.txt"))))){throw 'OWN105_PREVIOUS_ATTRIBUTION_INVALID'}
        if([IO.File]::ReadAllText((Join-Path $receipts "exit$previous.clean")) -notmatch '^exit=0;JVM=0;SQL=0;TCP=0;'){throw 'OWN105_PREVIOUS_PROCESS_EXIT_REQUIRED'}
    }
    $pre=Census ('before'+$Action)
    if($previous -and [IO.File]::ReadAllText((Join-Path $pre 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$previous.checkpoint")).TrimEnd([char[]]"`r`n")){throw 'OWN105_PRESTART_STOPPED_DATA_DRIFT'}
    $cap=if($Action -eq 'A'){16}else{128}
    $ini=Join-Path $game 'config/Custom/PhantomPlayers.ini'
    $text=[IO.File]::ReadAllText((Join-Path $private 'runtime105-final.ini'))
    foreach($key in @('PhantomPopulationActiveTarget','MaxMaterializedPhantoms')){
        $pattern='(?m)^('+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
        if([regex]::Matches($text,$pattern).Count -ne 1){throw 'OWN105_OVERLAY_KEY'}
        $text=[regex]::Replace($text,$pattern,{param($match)$match.Groups[1].Value+[string]$cap})
    }
    [IO.File]::WriteAllText($ini,$text,[Text.UTF8Encoding]::new($false))
    $manifest=Join-Path $private 'native-inputs.tsv'
    $pinsPath=Join-Path $private 'native.properties'
    $relative='L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/PhantomPlayers.ini'
    $rows=@([IO.File]::ReadAllLines($manifest)|ForEach-Object{if($_.StartsWith($relative+"`t")){$relative+"`t"+(Hash $ini)}else{$_}})
    [IO.File]::WriteAllText($manifest,($rows -join "`n"),[Text.UTF8Encoding]::new($false))
    $pins=[regex]::Replace([IO.File]::ReadAllText($pinsPath),'(?m)^dist/game/config/Custom/PhantomPlayers.ini=.*$',('dist/game/config/Custom/PhantomPlayers.ini='+(Hash $ini)))
    $pins=[regex]::Replace($pins,'(?m)^nativeInputsSha256=.*$',('nativeInputsSha256='+(Hash $manifest)))
    [IO.File]::WriteAllText($pinsPath,$pins,[Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath $manifest -Destination (Join-Path $private ("generation$Action-native-inputs.tsv"))
    Copy-Item -LiteralPath $pinsPath -Destination (Join-Path $private ("generation$Action-native.properties"))
    Java0
    New-Text (Join-Path $receipts "launch$phase.begun") ('utc='+[DateTime]::UtcNow.ToString('o'))
    $arguments=@('-Xms512m','-Xmx8g','-Dphantom.task102.native=true',"-Dphantom.task102.stage=$stage","-Dphantom.task102.uuid=$uuid","-Dphantom.module.root=$module","-Dphantom.test.config=$database",'-cp',$cp,'org.l2jmobius.tests.phantoms.PhantomPopulation102Native',('recovery105'+$Action))
    $line=($arguments|ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
    $process=Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList $line -WorkingDirectory $game -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $receipts "native$phase.private.log") -RedirectStandardError (Join-Path $receipts "native$phase.stderr.private.log")
    $os=Get-CimInstance Win32_Process -Filter ('ProcessId='+$process.Id)
    if(!$os -or (Invoke-CimMethod -InputObject $os -MethodName GetOwnerSid).Sid -cne $sid -or !$os.CommandLine.Contains($stage) -or !$os.CommandLine.Contains($uuid)){throw 'OWN105_LAUNCHED_PROCESS_IDENTITY_UNPROVEN_NO_RETRY'}
    New-Text (Join-Path $receipts "process$phase.private.json") (@{PID=$process.Id;CreationUTC=$os.CreationDate.ToUniversalTime().ToString('o');SID=$sid;Executable=$os.ExecutablePath;CommandSHA256=(TextHash $os.CommandLine);Stage=$stage;Installation=$uuid;JarSHA256=(Hash (Join-Path $module 'dist/libs/GameServer.jar'))}|ConvertTo-Json)
    Write-Output "OWN105_NATIVE_STARTED generation=$Action;cap=$cap;newProfiles=FORBIDDEN"
    while(!$process.WaitForExit(1000)){
        if(Test-Path -LiteralPath (Join-Path $receipts "stop$phase.blocked")){
            New-Text (Join-Path $receipts "retained$phase.private.json") (@{PID=$process.Id;live=(!$process.HasExited);status='STOP_BLOCKED_NO_RETRY';forceApproval=$false}|ConvertTo-Json)
            throw 'OWN105_NATIVE_STOP_BLOCKED;NO_NEXT_GENERATION;NO_FORCE'
        }
    }
    $exit=$process.ExitCode
    New-Text (Join-Path $receipts "exit$phase.observed") ("exit=$exit;utc="+[DateTime]::UtcNow.ToString('o'))
    if($exit -ne 0){throw 'OWN105_NATIVE_EXIT_FAILED;NO_NEXT_GENERATION'}
    Java0
    $post=Census ('after'+$Action)
    if(!(Test-Path -LiteralPath (Join-Path $receipts "phase$phase.complete")) -or
        [IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or
        !(Valid-Boundary105 ([IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt"))))){throw 'OWN105_COMPLETE_NATIVE_STOP_PROOF_REQUIRED'}
    if([IO.File]::ReadAllText((Join-Path $post 'durable105.checkpoint')).TrimEnd([char[]]"`r`n") -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")){throw 'OWN105_POSTEXIT_STOPPED_DATA_DRIFT'}
    New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;TCP=0;nativePersistence=VERIFIED;jarSha256="+$authority.jarSha256)
    Write-Output "OWN105_GENERATION_CLEAN generation=$Action;cap=$cap;JVM=0;SQL=0;TCP=0;allResidualLeasesAttributed=true"
    return
}
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
if($Action -eq 'Clearance'){
    $index=Join-Path $repo '.phantom-local/logs/PHANTOM-LEASE-STOP-CLOSURE-105'
    $previous=Get-Content -LiteralPath (Join-Path $index 'audit105-complete.private.json') -Raw|ConvertFrom-Json
    $archive=Physical $previous.archive
    $resume=Join-Path $archive ('resume-'+[guid]::NewGuid().ToString('N'))
    Private-Directory $resume
    $client=Physical $SqlClient
    if([IO.Path]::GetFileName($client) -cne 'mariadb.exe'){throw 'OWN105_SQL_CLIENT_REQUIRED'}
    $options=Physical (Join-Path $archive 'sql-client.private.cnf')
    function Empty-Process105 {
        if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN105_JAVA_PRESENT_NO_CLEARANCE'}
        if(@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308)}).Count -ne 0){throw 'OWN105_SQL_TCP_PRESENT_NO_CLEARANCE'}
    }
    function Read-Sql105([string]$Query,[string]$Name){
        $inputPath=Join-Path $resume ($Name+'.sql')
        New-Text $inputPath $Query
        $output=Join-Path $resume ($Name+'.private.tsv')
        Get-Content -LiteralPath $inputPath -Raw|& $client "--defaults-extra-file=$options" --batch --skip-column-names --database=l2jmobiush5_phantom_test_102 2>(Join-Path $resume ($Name+'.stderr.private.txt'))|Out-File -LiteralPath $output -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'OWN105_CLEARANCE_SQL_FAILED'}
        return $output
    }
    Empty-Process105
    $grants=Read-Sql105 'SHOW GRANTS FOR CURRENT_USER;' 'grants'
    if(!(Get-Content -LiteralPath $grants -Raw).Contains('GRANT ALL PRIVILEGES ON *.*')){throw 'OWN105_FULL_SQL_VISIBILITY_REQUIRED'}
    $authority=Read-Sql105 'SELECT installation_id,source_head,inputs_sha256 FROM own102_authority;' 'authority'
    if((Get-Content -LiteralPath $authority -Raw).Trim() -cne ($original.installationId+"`t"+$original.sourceHead+"`t"+$original.inputsSha256)){throw 'OWN105_DURABLE_AUTHORITY_DRIFT'}
    foreach($cut in @('first','second')){
        Empty-Process105
        $sessions=Read-Sql105 'SELECT ID,USER,HOST,DB,COMMAND,TIME,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() ORDER BY ID;' ($cut+'-sessions')
        if(@([IO.File]::ReadAllLines($sessions)|Where-Object {$_}).Count -ne 0){throw 'OWN105_SQL_NOT_EMPTY'}
        $null=Read-Sql105 ([IO.File]::ReadAllText((Physical (Join-Path $archive 'first-preimage.sql')))) ($cut+'-preimage')
    }
    Empty-Process105
    $pins=Get-Content -LiteralPath (Join-Path $archive 'protected-source105.json') -Raw|ConvertFrom-Json
    foreach($pin in $pins){if((Hash (Join-Path $repo $pin.relative)) -cne $pin.sha256){throw 'OWN105_PROTECTED_SOURCE_DRIFT'}}
    $stable=(Hash (Join-Path $resume 'first-preimage.private.tsv')) -ceq (Hash (Join-Path $resume 'second-preimage.private.tsv'))
    if(!$stable){throw 'OWN105_UNSTABLE_CLEARANCE_PREIMAGE'}
    New-Text (Join-Path $resume 'clearance105.json') (@{status='PRIOR_EXIT_UNCLEAN_UNPROVEN';exact104Absent=$true;JVM=0;SQL=0;TCP=0;fullVisibility=$true;preimagesStable=$stable;protectedDrift=0;forceApprovalReceived=$true;forceUsed=$false;utc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
    $manifest=@(Get-ChildItem -LiteralPath $resume -File|ForEach-Object {@{name=$_.Name;sha256=(Hash $_.FullName);bytes=$_.Length}})
    New-Text (Join-Path $resume 'clearance105-manifest.json') ($manifest|ConvertTo-Json)
    New-Text (Join-Path $index 'resume105.private.json') (@{archive=$archive;resume=$resume;origin=$origin;stage104=$stage;approval='Exact104 one targeted force approved; unused because already absent';createdUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)
    Write-Output 'PROCESS105_CLEARANCE JVM=0;SQL=0;TCP=0;fullVisibility=true;preimagesStable=true;priorExit=UNCLEAN_UNPROVEN;force=0;native105=0'
    return
}
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
