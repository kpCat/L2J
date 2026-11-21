<#
.SYNOPSIS
Attest a completed N01 setup and prepare a pinned offline TASK131 candidate.
.DESCRIPTION
Preflight is read-only. Prepare creates a new physical runtime and campaign input tree.
Attest compares both trees against the original asset pins, reviewed build and SQL SETUP seals.
The tool creates no campaign authorization or server process.
#>
[CmdletBinding()]
param(
    [ValidateSet('Preflight','Prepare','Attest')][string]$Action='Preflight',
    [Parameter(Mandatory)][ValidatePattern('^n01-setup-r[1-9][0-9]*$')][string]$SqlLane,
    [Parameter(Mandatory)][ValidatePattern('^native-runtime-r[1-9][0-9]*$')][string]$RuntimeId,
    [switch]$StageMetadataByRuntime,
    [ValidatePattern('^build-v[1-9][0-9]*-reviewed$')][string]$AuditedBuild='',
    [ValidatePattern('^github-readback-v[1-9][0-9]*\.json$')][string]$GithubReadback='',
    [string]$GithubReadbackSha='',
    [string]$GameJarSha='',
    [string]$LoginJarSha='',
    [string]$AdapterClassSha='',
    [string]$FactoryClassSha='',
    [ValidateRange(3,12)][int]$MaxCyclesPerActor=6,
    [ValidateRange(1,1200)][int]$ObserveSeconds=600,
    [ValidateRange(1,120)][int]$DrainSeconds=90
)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'G0SqlIdentity.ps1')

$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$task=Join-Path (Split-Path $module -Parent) '.phantom-local\logs\TASK131'
$oldRuntime=Join-Path $task 'native-runtime'
$runtime=Join-Path $task $RuntimeId
$sql=Join-Path $task "sql-lanes\$SqlLane\sql-task131"
$campaignParent=Join-Path $task $(if($StageMetadataByRuntime){'runtime-candidates'}else{'campaigns'})
$campaign=Join-Path $campaignParent $(if($StageMetadataByRuntime){$RuntimeId}else{'n01'})
$utf8=[Text.UTF8Encoding]::new($false,$true)
$schema='l2jmobiush5_phantom_test_131'
$javaEntry='org/l2jmobius/tests/phantoms/PhantomLearning131Native.class'
$factoryEntry='org/l2jmobius/gameserver/phantoms/PhantomLearning131Factory.class'
$libraries=@('HikariCP-7.0.2.jar','mysql-connector-j-9.5.0.jar','slf4j-api-2.0.17.jar','slf4j-simple-2.0.17.jar')
$configFiles=@('game/config/Server.ini','game/config/Database.ini','game/config/Custom/PhantomPlayers.ini','game/config/General.ini',
    'game/config/Player.ini','login/config/Server.ini','game/log.cfg')

function Assert-N01([bool]$Condition,[string]$Reason) {
    if(!$Condition){throw "TASK131_N01_SETUP_$Reason"}
}

function Assert-N01Directory([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    Assert-N01 ($item.PSIsContainer -and $item.FullName -ieq $Path) 'DIRECTORY'
    for($part=$item;$null -ne $part;$part=$part.Parent){Assert-N01 (!($part.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'REPARSE'}
}

function Get-N01Hash([string]$Path) {
    Assert-G0PhysicalFile $Path
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-N01Hash([string]$Path,[string]$Expected) {
    Assert-N01 ($Expected -match '^[a-f0-9]{64}$' -and (Get-N01Hash $Path) -ieq $Expected) 'HASH'
}

function Read-N01Text([string]$Path) {
    Assert-G0PhysicalFile $Path
    Assert-N01 ((Get-Item -LiteralPath $Path).Length -le 1048576) 'METADATA_SIZE'
    return [IO.File]::ReadAllText($Path,$utf8)
}

function Read-N01Properties([string]$Path) {
    $result=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($line in (Read-N01Text $Path) -split '\r?\n'){
        if($line -eq ''){continue}
        Assert-N01 ($line -cmatch '^([a-zA-Z][a-zA-Z0-9]*)=(\S.*)$') 'PROPERTIES'
        Assert-N01 (!$result.ContainsKey($Matches[1])) 'DUPLICATE_PROPERTY'
        $result.Add($Matches[1],$Matches[2])
    }
    return ,$result
}

function Read-N01Table([string]$Path,[string]$Header) {
    $body=Read-N01Text $Path
    Assert-N01 (($body -split '\r?\n')[0] -ceq $Header) 'TABLE_HEADER'
    return @($body | ConvertFrom-Csv -Delimiter "`t")
}

function Assert-N01Off {
    $listeners=@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object Port -in 2106,33331,33332,33333)
    Assert-N01 ($listeners.Count -eq 0) 'OWN_PORTS_NOT_OFF'
}

function Get-N01Fixture {
    Assert-N01Directory $sql
    $fixture=Join-Path $sql 'n01-fixture.tsv'
    $scenes=Join-Path $sql 'n01-scenes.tsv'
    $fixtureSha=Get-N01Hash $fixture
    $sceneSha=Get-N01Hash $scenes
    $rows=@(Read-N01Table $fixture ("status`tprofileId`tobjectId`taccount`trole`tclass`tlevel`tskillId`tskillLevel`tweapon`tshotItem`tshots`tadena`tanchor`tnpcId`tspawn`tpreflightObjectId`tx`ty`tz`texp`tsp"))
    $plans=@(Read-N01Table $scenes ("profileId`tactorObjectId`tgoalId`tgoalRevision`tplanIdentity`tanchor`tx`ty`tz`tnpcId`tnpcLevel`tspawnKey`trespawnMinMs`trespawnMaxMs`tpreflightObjectId`tpreflightInstanceId`tpreflightGeneration`tqualifyingTargets`tknowledgeSha256`ttopologySha256`tprogressionSha256`tcommerceSha256"))
    Assert-N01 ($rows.Count -eq 4 -and $plans.Count -eq 4) 'INCOMPLETE_SCENE_SET'
    $roles=[Collections.Generic.HashSet[string]]::new()
    $actors=[Collections.Generic.HashSet[string]]::new()
    $profiles=[Collections.Generic.HashSet[string]]::new()
    for($i=0;$i -lt 4;$i++){
        $row=$rows[$i]; $plan=$plans[$i]
        Assert-N01 ($row.status -ceq 'SETUP_STOCK_CANDIDATE' -and $row.profileId -cmatch '^[1-9][0-9]*$' -and $row.objectId -cmatch '^[1-9][0-9]*$' -and
            $profiles.Add($row.profileId) -and $actors.Add($row.objectId) -and "$($row.role)$($row.level)" -cin @('MAGE52','MAGE76','PHYSICAL52','PHYSICAL76') -and
            $roles.Add("$($row.role)$($row.level)")) 'ROSTER'
        Assert-N01 ($row.profileId -ceq $plan.profileId -and $row.objectId -ceq $plan.actorObjectId -and $row.anchor -ceq $plan.anchor -and
            $row.npcId -ceq $plan.npcId -and $row.spawn -ceq $plan.spawnKey -and $row.preflightObjectId -ceq $plan.preflightObjectId -and
            $row.x -ceq $plan.x -and $row.y -ceq $plan.y -and $row.z -ceq $plan.z -and $plan.preflightInstanceId -ceq '0' -and
            $plan.preflightGeneration -cmatch '^[1-9][0-9]*$' -and $plan.qualifyingTargets -cmatch '^[1-9][0-9]*$' -and
            $plan.respawnMinMs -cmatch '^[1-9][0-9]*$' -and [int]$plan.respawnMaxMs -ge [int]$plan.respawnMinMs) 'SCENE_ROW_BINDING'
    }
    $scopePath=Join-Path $sql 'n01-scene-scope.properties'
    $sceneScopeSha=Get-N01Hash $scopePath
    $seal=Read-N01Properties $scopePath
    $order=($rows.profileId -join ',')
    Assert-N01 ($seal.Count -eq 5 -and $seal['stage'] -ceq 'SETUP_CANDIDATES' -and $seal['nextBootMustBindNativeTargets'] -ceq 'true' -and
        $seal['fixtureSha256'] -ieq $fixtureSha -and $seal['sceneSha256'] -ieq $sceneSha -and $seal['profiles'] -ceq $order) 'SETUP_SEAL'
    Assert-N01Hash $fixture $fixtureSha
    Assert-N01Hash $scenes $sceneSha
    Assert-N01Hash $scopePath $sceneScopeSha
    return [ordered]@{fixtureSha=$fixtureSha;sceneSha=$sceneSha;sceneScopeSha=$sceneScopeSha;profiles=@($rows.profileId)}
}

function Get-N01OwnedSql {
    $file=Join-Path $sql 'Database.ini'
    $schemaFile=Join-Path $sql 'schema-ready.json'
    $offPath=Join-Path $sql 'off.json'
    $schemaSha=Get-N01Hash $schemaFile
    $offSha=Get-N01Hash $offPath
    $ready=Read-N01Text $schemaFile | ConvertFrom-Json
    $off=Read-N01Text $offPath | ConvertFrom-Json
    $jdbc=Read-N01Properties $file
    $hash=Get-N01Hash $file
    Assert-N01 ($ready.version -eq 1 -and $ready.lane -ceq $SqlLane -and $ready.schema -ceq $schema -and $ready.port -eq 33331 -and
        $ready.jdbcConfigSha256 -ieq $hash -and $off.version -eq 1 -and $off.lane -ceq $SqlLane -and $off.phase -ceq 'OFF' -and $off.port -eq 33331 -and
        $off.listeners -eq 0 -and $off.datadir -ieq (Join-Path $sql 'data') -and $off.pid -gt 0 -and [long]$off.createdFileTime -gt 0 -and
        $off.exitEvidence -cin @('OWN_PROCESS_HANDLE_EXIT','WMI_ABSENCE_AFTER_ATTESTATION')) 'SQL_SEAL_OR_OFF'
    Assert-N01Directory $off.datadir
    $current=Get-Process -Id $off.pid -ErrorAction SilentlyContinue
    if($null -ne $current){
        try{Assert-N01 ($current.StartTime.ToUniversalTime().ToFileTimeUtc() -ne [long]$off.createdFileTime) 'SQL_STILL_RUNNING'}
        finally{$current.Dispose()}
    }
    Assert-N01 ($jdbc['Driver'] -ceq 'com.mysql.cj.jdbc.Driver' -and $jdbc['Login'] -ceq 'task131_runtime' -and $jdbc['BackupDatabase'] -ceq 'false' -and
        $jdbc['Password'] -cmatch '^[a-f0-9]{64}$' -and $jdbc['URL'] -ceq "jdbc:mysql://127.0.0.1:33331/$schema`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000") 'SQL_JDBC_SCOPE'
    return [ordered]@{file=$file;jdbcSha=$hash;dbSealSha=$schemaSha;offSha=$offSha}
}

function Get-N01AuditedInputs {
    foreach($pin in @($GithubReadbackSha,$GameJarSha,$LoginJarSha,$AdapterClassSha,$FactoryClassSha)){
        Assert-N01 ($pin -match '^[a-f0-9]{64}$') 'REQUIRED_DEPLOYMENT_PIN'
    }
    Assert-N01 (![string]::IsNullOrWhiteSpace($GithubReadback) -and ![string]::IsNullOrWhiteSpace($AuditedBuild)) 'AUDITED_SOURCE'
    $readback=Join-Path $task $GithubReadback
    Assert-N01Hash $readback $GithubReadbackSha
    $commit=Read-N01Text $readback | ConvertFrom-Json
    Assert-N01 ($commit.result -ceq 'PASS' -and $commit.branch -ceq 'feature/phantom-world' -and $commit.commit -cmatch '^[a-f0-9]{40}$' -and $commit.parent -cmatch '^[a-f0-9]{40}$' -and
        $AuditedBuild -ceq ('build-'+($GithubReadback -creplace '^github-readback-([^\.]+)\.json$','$1')+'-reviewed')) 'AUDITED_GITHUB_READBACK'
    $verifiedFiles=@($commit.files)
    Assert-N01 (($commit.count -is [int] -or $commit.count -is [long]) -and $verifiedFiles.Count -gt 0 -and $verifiedFiles.Count -eq $commit.count) 'GITHUB_FILE_COUNT'
    $moduleName=Split-Path $module -Leaf
    $sourcePaths=@(
        'test/java/org/l2jmobius/tests/phantoms/PhantomLearning131Native.java',
        'test/java/org/l2jmobius/gameserver/phantoms/PhantomLearning131Factory.java'
    )
    $mappedPaths=@($sourcePaths | ForEach-Object {$moduleName+'/'+$_})
    $inherited=@()
    if($null -ne $commit.PSObject.Properties['pinnedSources']){
        $inherited=@($commit.pinnedSources)
        Assert-N01 ($inherited.Count -le $mappedPaths.Count -and @($inherited | Where-Object {$_.path -cnotin $mappedPaths}).Count -eq 0) 'GITHUB_INHERITED_SCOPE'
    }
    $byPath=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($file in $verifiedFiles+$inherited){
        Assert-N01 ($null -ne $file -and $file.path -is [string] -and $file.path.Length -gt 0 -and $file.fullContentsVerified -is [bool] -and $file.fullContentsVerified -and
            ($file.size -is [int] -or $file.size -is [long]) -and $file.size -ge 0 -and $file.gitBlobSha1 -cmatch '^[a-f0-9]{40}$' -and
            $file.sha256 -cmatch '^[a-f0-9]{64}$' -and !$byPath.ContainsKey($file.path)) 'GITHUB_FULL_CONTENTS_ROW'
        Assert-N01Relative $file.path
        $byPath.Add($file.path,$file)
    }
    $snapshot=Join-Path $task ($AuditedBuild -creplace '^build-','offline-snapshot-')
    foreach($sourcePath in $sourcePaths){
        $repoPath=$moduleName+'/'+$sourcePath
        Assert-N01 ($byPath.ContainsKey($repoPath) -and $byPath[$repoPath].path -ceq $repoPath) 'GITHUB_N01_SOURCE_MISSING'
        $row=$byPath[$repoPath]
        $sourceFile=Join-Path $snapshot $sourcePath
        Assert-N01Hash $sourceFile $row.sha256
        Assert-N01 ((Get-Item -LiteralPath $sourceFile).Length -eq $row.size) 'GITHUB_N01_SOURCE_SIZE'
    }
    Assert-N01Hash $readback $GithubReadbackSha
    $build=Join-Path $task $AuditedBuild
    Assert-N01Directory $build
    $classRoot=Join-Path $build 'phantom-test\bin'
    Assert-N01Directory $classRoot
    $gs=Join-Path $build 'dist\libs\GameServer.jar'
    $ls=Join-Path $build 'dist\libs\LoginServer.jar'
    Assert-N01Hash $gs $GameJarSha
    Assert-N01Hash $ls $LoginJarSha
    Assert-N01 ($GameJarSha -ine '9CAE9D2382371D096E0BAEA26BB7097C45A449CEC255E31ED8D73D7FE9E2C5F3') 'OLD_BOOTSTRAP_GAME_JAR'
    Assert-N01Hash (Join-Path $classRoot $javaEntry) $AdapterClassSha
    Assert-N01Hash (Join-Path $classRoot $factoryEntry) $FactoryClassSha
    $java=(Get-Command java.exe -CommandType Application | Select-Object -First 1).Source
    Assert-N01 ((Get-Item -LiteralPath $java).VersionInfo.ProductMajorPart -eq 25) 'JDK25'
    return [ordered]@{commit=$commit.commit;parent=$commit.parent;classRoot=$classRoot;game=$gs;login=$ls;javaSha=(Get-N01Hash $java);readbackSha=$GithubReadbackSha.ToLowerInvariant()}
}

function Assert-N01Relative([string]$Relative) {
    Assert-N01 ($Relative -cnotmatch '^[\\/]|[\\:*?"<>|;\x00-\x1f]|(?:^|/)\.{1,2}(?:/|$)|//|/$') 'RELATIVE_PATH'
}

function Get-N01Assets {
    Assert-N01Directory $oldRuntime
    $receipt=Join-Path $oldRuntime 'asset-preflight.json'
    $receiptSha='1fa0f138dfc5fbcb56aed5ddbe5bb0f5a115d951970b52eb8869087c11037d75'
    Assert-N01Hash $receipt $receiptSha
    $root=Read-N01Text $receipt | ConvertFrom-Json
    Assert-N01 ($root.schema -ceq 'TASK131_NATIVE_ASSET_PREFLIGHT_V1' -and $root.runtimeRoot -ieq $oldRuntime -and
        $root.launchAuthorized -eq $false -and $root.sourceCommit -ceq '734042731152d24e01f2132c1d86209ce40bea38' -and
        $root.assetFiles -eq 25809 -and $root.geodata.tiles -eq 203) 'ORIGINAL_ASSET_SCOPE'
    $pinPath=Join-Path $oldRuntime 'asset-pins.tsv'
    Assert-N01Hash $pinPath $root.assetPinsSha256
    $pins=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($pin in Import-Csv -LiteralPath $pinPath -Delimiter "`t"){
        Assert-N01Relative $pin.path
        Assert-N01 ($pin.path.Length -gt 0 -and $pin.sha256 -cmatch '^[A-F0-9]{64}$' -and !$pins.ContainsKey($pin.path)) 'ASSET_PIN_ROW'
        $pins.Add($pin.path,$pin.sha256.ToLowerInvariant())
    }
    Assert-N01 ($pins.Count -eq $root.assetFiles) 'ASSET_PIN_COUNT'
    foreach($name in $configFiles + @('BOOT_DISABLED.txt','login/config/Database.ini','libs/GameServer.jar','libs/LoginServer.jar') + @($libraries|ForEach-Object {'libs/'+$_})){
        Assert-N01 ($pins.ContainsKey($name)) 'ASSET_REQUIRED_FILE'
        Assert-N01Hash (Join-Path $oldRuntime $name) $pins[$name]
    }
    return ,$pins
}

function New-N01PrivateDirectory([string]$Path) {
    Assert-N01 (!(Test-Path -LiteralPath $Path)) 'NO_OVERWRITE'
    Assert-N01Directory (Split-Path $Path -Parent)
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl=[Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($identity)
    $acl.SetAccessRuleProtection($true,$false)
    foreach($sid in @($identity.Value,'S-1-5-18','S-1-5-32-544') | Select-Object -Unique){
        $rule=[Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $acl.AddAccessRule($rule)
    }
    $null=New-Item -ItemType Directory -Path $Path
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-N01PrivateDirectory $Path
}

function Assert-N01PrivateDirectory([string]$Path) {
    Assert-N01Directory $Path
    $acl=Get-Acl -LiteralPath $Path
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $sids=@(@($identity,'S-1-5-18','S-1-5-32-544') | Select-Object -Unique)
    $rules=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]))
    Assert-N01 ($acl.AreAccessRulesProtected -and $acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -eq $identity -and $rules.Count -eq $sids.Count) 'PRIVATE_ACL_SCOPE'
    foreach($rule in $rules){
        Assert-N01 (!$rule.IsInherited -and $rule.IdentityReference.Value -cin $sids -and $rule.AccessControlType -eq 'Allow' -and
            $rule.FileSystemRights -eq [Security.AccessControl.FileSystemRights]::FullControl -and
            $rule.InheritanceFlags -eq ([Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit)) 'PRIVATE_ACL_RULE'
    }
}

function Write-N01New([string]$Path,[string]$Body) {
    $bytes=$utf8.GetBytes($Body)
    $file=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$file.Write($bytes,0,$bytes.Length)}finally{$file.Dispose()}
}

function Copy-N01Pinned([string]$Source,[string]$Root,[string]$Relative,[string]$Expected) {
    Assert-N01Relative $Relative
    Assert-N01Hash $Source $Expected
    $target=Join-Path $Root $Relative
    $parent=Split-Path $target -Parent
    if(!(Test-Path -LiteralPath $parent)){$null=[IO.Directory]::CreateDirectory($parent)}
    Assert-N01Directory $parent
    [IO.File]::Copy($Source,$target,$false)
    Assert-N01Hash $target $Expected
}

function Set-N01IniValues([string]$Source,$Settings) {
    $body=Read-N01Text $Source
    foreach($key in $Settings.Keys){
        $pattern='(?m)^([ \t]*'+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
        Assert-N01 ([regex]::Matches($body,$pattern).Count -eq 1) 'INI_KEY_EXACT'
        $setting=$Settings[$key]
        $body=[regex]::Replace($body,$pattern,{param($line) $line.Groups[1].Value+$setting})
    }
    return $body
}

function Get-N01TextHash([string]$Text) {
    $digest=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($digest.ComputeHash($utf8.GetBytes($Text)))).Replace('-','').ToLowerInvariant()}
    finally{$digest.Dispose()}
}

function Assert-N01Ini([string]$Path,$Settings) {
    $body=Read-N01Text $Path
    Assert-N01 ((Set-N01IniValues $Path $Settings) -ceq $body) 'INI_VALUE_EXACT'
}

function Get-N01ClassFiles([string]$Root) {
    Assert-N01Directory $Root
    $stack=[Collections.Generic.Stack[string]]::new()
    $stack.Push($Root)
    while($stack.Count -gt 0){
        $directory=$stack.Pop()
        Assert-N01Directory $directory
        foreach($entry in Get-ChildItem -LiteralPath $directory -Force){
            Assert-N01 (!($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'CLASS_REPARSE'
            if($entry.PSIsContainer){$stack.Push($entry.FullName);continue}
            $relative=$entry.FullName.Substring($Root.Length+1).Replace('\','/')
            Assert-N01 ($relative -cmatch '^(?:[a-zA-Z0-9_$-]+/)*[a-zA-Z0-9_$-]+\.class$') 'CLASS_FILE'
            [pscustomobject]@{source=$entry.FullName;relative=$relative}
        }
    }
}

function ConvertTo-N01Properties($Properties) {
    $rows=foreach($key in $Properties.Keys){
        $value=[string]$Properties[$key]
        Assert-N01 ($value -notmatch '[\x00-\x1f]') 'PROPERTY_VALUE'
        $key+'='+$value.Replace('\','\\')
    }
    return ($rows -join "`n")+"`n"
}

function Get-N01RuntimePins([string]$Root) {
    Assert-N01Directory $Root
    $folders=[Collections.Generic.Stack[string]]::new()
    $folders.Push($Root)
    $paths=[Collections.Generic.List[string]]::new()
    while($folders.Count -gt 0){
        $directory=$folders.Pop()
        Assert-N01Directory $directory
        foreach($item in Get-ChildItem -LiteralPath $directory -Force){
            Assert-N01 (!($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'RUNTIME_REPARSE'
            if($item.PSIsContainer){$folders.Push($item.FullName);continue}
            $paths.Add($item.FullName.Substring($Root.Length+1).Replace('\','/'))
        }
    }
    $paths.Sort([StringComparer]::Ordinal)
    foreach($path in $paths){
        Assert-N01Relative $path
        "$path`t$(Get-N01Hash (Join-Path $Root $path))"
    }
}

function Get-N01ClassPins([string]$Root) {
    $classes=@(Get-N01ClassFiles $Root | Sort-Object -Property relative)
    Assert-N01 ($classes.Count -in (1..10000)) 'CLASS_COUNT'
    foreach($entry in $classes){"$(Get-N01Hash $entry.source)`t$($entry.relative)"}
}

function Assert-N01Runtime([string]$Root) {
    Assert-N01PrivateDirectory $Root
    Assert-N01 (!(Test-Path -LiteralPath (Join-Path $Root 'BOOT_DISABLED.txt'))) 'INHERITED_BOOT_MARKER'
    Assert-N01Ini (Join-Path $Root 'game/config/Server.ini') ([ordered]@{GameserverHostname='127.0.0.1';GameserverPort='33332';LoginHost='127.0.0.1';LoginPort='33333'})
    Assert-N01Ini (Join-Path $Root 'login/config/Server.ini') ([ordered]@{LoginserverHostname='127.0.0.1';LoginserverPort='2106';LoginHostname='127.0.0.1';LoginPort='33333'})
    Assert-N01Ini (Join-Path $Root 'game/config/Custom/PhantomPlayers.ini') ([ordered]@{EnablePhantomSystem='False';PhantomPopulationTarget='0';PhantomPopulationActiveTarget='0';LearningMode='OFF'})
    Assert-N01Ini (Join-Path $Root 'game/config/Custom/PhantomPlayers.n01.ini') $enabledSettings
    Assert-N01Ini (Join-Path $Root 'game/config/General.ini') $logging
    Assert-N01Ini (Join-Path $Root 'game/config/Player.ini') ([ordered]@{AutoLoot='False';AutoLootHerbs='False';AutoLootItemIds='0'})
    Assert-N01Ini (Join-Path $Root 'game/config/Custom/OfflineTrade.ini') ([ordered]@{RestoreOffliners='False'})
    Assert-N01 (!(Test-Path -LiteralPath (Join-Path $Root 'game/config/hexid.txt'))) 'INHERITED_HEXID'
}

function Get-N01CandidatePins([string]$Root,[string]$Metadata) {
    $configShas=[ordered]@{}
    foreach($file in $configFiles){$configShas[$file]=Get-N01Hash (Join-Path $Root $file)}
    $dependencyShas=[ordered]@{}
    foreach($file in $libraries){$dependencyShas[$file]=Get-N01Hash (Join-Path $Root ('libs/'+$file))}
    return [ordered]@{
        sourceCommit=$audit.commit;javaSha=$audit.javaSha;gameJarSha=$GameJarSha.ToLowerInvariant()
        classPinsSha=(Get-N01Hash (Join-Path $Metadata 'class-pins.tsv'));executionSha=(Get-N01Hash (Join-Path $Metadata 'execution.properties'))
        fixtureSha=$fixture.fixtureSha;policySha=(Get-N01Hash (Join-Path $Root 'game/config/Custom/PhantomPlayers.n01.ini'))
        modelSha='NONE';dbSealSha=$database.dbSealSha;jdbcSha=$database.jdbcSha;dependencyShas=$dependencyShas;configShas=$configShas
    }
}

Assert-N01Directory $task
Assert-N01Off
# Refuse partial stock coverage before touching build artifacts or candidate output roots.
$fixture=Get-N01Fixture
$database=Get-N01OwnedSql
$audit=Get-N01AuditedInputs
$assets=Get-N01Assets
$enabledSettings=[ordered]@{
    EnablePhantomSystem='True';MaxMaterializedPhantoms='4';PhantomMaterializationBurstSlots='0';PhantomPopulationTarget='4';PhantomPopulationActiveTarget='4'
    EnablePhantomAutonomousPartySeeking='False';PhantomEcologyWorldAgeDays='0';PhantomStarterShotLifetimeLimit='0';LearningMode='OFF'
}
$logging=[ordered]@{LogItems='True';LogItemsSmallLog='False';LogItemsIdsOnly='False'}
$master='game/config/Custom/PhantomPlayers.ini'
$enabledFile='game/config/Custom/PhantomPlayers.n01.ini'
$general='game/config/General.ini'
$enabledBody=Set-N01IniValues (Join-Path $oldRuntime $master) $enabledSettings
$generalBody=Set-N01IniValues (Join-Path $oldRuntime $general) $logging
$replacements=[ordered]@{
    'game/config/Database.ini'=$database.jdbcSha;'login/config/Database.ini'=$database.jdbcSha
    'libs/GameServer.jar'=$GameJarSha.ToLowerInvariant();'libs/LoginServer.jar'=$LoginJarSha.ToLowerInvariant()
    $general=(Get-N01TextHash $generalBody);$enabledFile=(Get-N01TextHash $enabledBody)
}
$expectedPaths=[Collections.Generic.List[string]]::new()
foreach($name in $assets.Keys){if($name -cne 'BOOT_DISABLED.txt'){$expectedPaths.Add($name)}}
$expectedPaths.Add($enabledFile)
$expectedPaths.Sort([StringComparer]::Ordinal)
$expectedRuntimeRows=foreach($name in $expectedPaths){
    $expectedHash=if($replacements.Contains($name)){$replacements[$name]}else{$assets[$name]}
    "$name`t$expectedHash"
}
$execution=[ordered]@{
    schema='TRAINING_EXECUTION131_V1';phase='N01';campaignId='n01';sourceCommit=$audit.commit;runtimeRoot=$runtime;gameJar=(Join-Path $runtime 'libs\GameServer.jar')
    gameJarSha=$GameJarSha.ToLowerInvariant();adapterClassSha=$AdapterClassSha.ToLowerInvariant();factoryClassSha=$FactoryClassSha.ToLowerInvariant()
    sqlLane=$SqlLane;jdbcSha=$database.jdbcSha;fixtureSha=$fixture.fixtureSha;sceneSha=$fixture.sceneSha;sceneScopeSha=$fixture.sceneScopeSha
    enabledPhantomIniSha=$replacements[$enabledFile];maxEpisodes=4;maxCyclesPerActor=$MaxCyclesPerActor;observeSeconds=$ObserveSeconds;drainSeconds=$DrainSeconds
}
$executionBody=ConvertTo-N01Properties $execution

if($Action -eq 'Preflight'){
    Assert-N01Off
    Write-Output "TASK131_N01_SETUP_PREFLIGHT scenes=4 inputsPinned=true portsOff=true launchAuthorized=false"
    return
}

if($Action -eq 'Prepare'){
    Assert-N01 (!(Test-Path -LiteralPath $runtime) -and !(Test-Path -LiteralPath $campaign)) 'NO_OVERWRITE'
    if(!(Test-Path -LiteralPath $campaignParent)){$null=New-Item -ItemType Directory -Path $campaignParent}
    Assert-N01Directory $campaignParent
    $nonce=[guid]::NewGuid().ToString('N')
    $stage=Join-Path $task ('.n01-runtime-'+$nonce)
    $metadata=Join-Path $task ('.n01-candidate-'+$nonce)
    New-N01PrivateDirectory $stage
    New-N01PrivateDirectory $metadata
    $substituted=@('BOOT_DISABLED.txt','game/config/Database.ini','login/config/Database.ini','libs/GameServer.jar','libs/LoginServer.jar','game/config/General.ini')
    foreach($name in $assets.Keys){
        if($name -cnotin $substituted){Copy-N01Pinned (Join-Path $oldRuntime $name) $stage $name $assets[$name]}
    }
    Copy-N01Pinned $database.file $stage 'game/config/Database.ini' $database.jdbcSha
    Copy-N01Pinned $database.file $stage 'login/config/Database.ini' $database.jdbcSha
    Copy-N01Pinned $audit.game $stage 'libs/GameServer.jar' $GameJarSha
    Copy-N01Pinned $audit.login $stage 'libs/LoginServer.jar' $LoginJarSha
    Write-N01New (Join-Path $stage $enabledFile) $enabledBody
    Write-N01New (Join-Path $stage $general) $generalBody
    foreach($directory in @('game/log','login/log')){$null=[IO.Directory]::CreateDirectory((Join-Path $stage $directory))}
    Assert-N01Runtime $stage

    $classRoot=Join-Path $metadata 'native-classes'
    $null=[IO.Directory]::CreateDirectory($classRoot)
    $classPins=@(Get-N01ClassPins $audit.classRoot)
    foreach($pin in $classPins){
        $hash,$relative=$pin -split "`t",2
        Copy-N01Pinned (Join-Path $audit.classRoot $relative) $classRoot $relative $hash
    }
    Write-N01New (Join-Path $metadata 'class-pins.tsv') (($classPins -join "`n")+"`n")
    $runtimePins=@(Get-N01RuntimePins $stage)
    Assert-N01 (($runtimePins -join "`n") -ceq ($expectedRuntimeRows -join "`n")) 'RUNTIME_NOT_MATCHING_INPUTS'
    Write-N01New (Join-Path $metadata 'runtime-pins.tsv') (($runtimePins -join "`n")+"`n")
    Write-N01New (Join-Path $metadata 'execution.properties') $executionBody
    $candidate=[ordered]@{
        schema='TASK131_N01_OFFLINE_CANDIDATE_V1';status='STAGED_OFFLINE';launchAuthorized=$false;runtimeRoot=$runtime;sqlLane=$SqlLane
        profileIds=$fixture.profiles;sceneSha=$fixture.sceneSha;sceneScopeSha=$fixture.sceneScopeSha;sqlOffSha=$database.offSha
        githubReadbackSha=$audit.readbackSha;sourceParent=$audit.parent;loginJarSha=$LoginJarSha.ToLowerInvariant()
        runtimePinsSha=(Get-N01Hash (Join-Path $metadata 'runtime-pins.tsv'));runtimeFiles=$runtimePins.Count;compiledClasses=$classPins.Count
        controllerPins=(Get-N01CandidatePins $stage $metadata)
    }
    Assert-N01Hash (Join-Path $sql 'n01-fixture.tsv') $fixture.fixtureSha
    Assert-N01Hash (Join-Path $sql 'n01-scenes.tsv') $fixture.sceneSha
    Assert-N01Hash (Join-Path $sql 'n01-scene-scope.properties') $fixture.sceneScopeSha
    Assert-N01Hash (Join-Path $sql 'off.json') $database.offSha
    Assert-N01Off
    Write-N01New (Join-Path $metadata 'candidate-pins.json') (($candidate | ConvertTo-Json -Depth 6)+"`n")
    # Both destination trees are create-once; no existing runtime or signed campaign is replaced.
    [IO.Directory]::Move($stage,$runtime)
    [IO.Directory]::Move($metadata,$campaign)
    Assert-N01Off
    Write-Output "TASK131_N01_SETUP_PREPARED scenes=4 files=$($runtimePins.Count) classes=$($classPins.Count) launchAuthorized=false"
    return
}

Assert-N01Runtime $runtime
Assert-N01PrivateDirectory $campaign
$receipt=Read-N01Text (Join-Path $campaign 'candidate-pins.json') | ConvertFrom-Json
Assert-N01 ($receipt.schema -ceq 'TASK131_N01_OFFLINE_CANDIDATE_V1' -and $receipt.status -ceq 'STAGED_OFFLINE' -and !$receipt.launchAuthorized -and
    $receipt.runtimeRoot -ieq $runtime -and $receipt.sqlLane -ceq $SqlLane -and $receipt.githubReadbackSha -ceq $audit.readbackSha -and
    $receipt.sceneSha -ceq $fixture.sceneSha -and $receipt.sceneScopeSha -ceq $fixture.sceneScopeSha -and $receipt.sqlOffSha -ceq $database.offSha -and
    ($receipt.profileIds -join ',') -ceq ($fixture.profiles -join ',')) 'CANDIDATE_RECEIPT'
$runtimePins=Join-Path $campaign 'runtime-pins.tsv'
Assert-N01Hash $runtimePins $receipt.runtimePinsSha
$runtimeRows=@(Get-N01RuntimePins $runtime)
Assert-N01 ($runtimeRows.Count -eq $receipt.runtimeFiles -and ($runtimeRows -join "`n") -ceq ([IO.File]::ReadAllLines($runtimePins,$utf8) -join "`n")) 'RUNTIME_CONTENTS'
Assert-N01 (($runtimeRows -join "`n") -ceq ($expectedRuntimeRows -join "`n")) 'RUNTIME_NOT_MATCHING_INPUTS'
$classPins=Join-Path $campaign 'class-pins.tsv'
Assert-N01Hash $classPins $receipt.controllerPins.classPinsSha
$classRows=@(Get-N01ClassPins (Join-Path $campaign 'native-classes'))
Assert-N01 ($classRows.Count -eq $receipt.compiledClasses -and ($classRows -join "`n") -ceq ([IO.File]::ReadAllLines($classPins,$utf8) -join "`n")) 'CLASS_CONTENTS'
Assert-N01 (($classRows -join "`n") -ceq (@(Get-N01ClassPins $audit.classRoot) -join "`n")) 'CLASS_NOT_MATCHING_AUDIT'
Assert-N01Hash (Join-Path $runtime 'libs/GameServer.jar') $GameJarSha
Assert-N01Hash (Join-Path $runtime 'libs/LoginServer.jar') $LoginJarSha
Assert-N01Hash (Join-Path $runtime 'game/config/Database.ini') $database.jdbcSha
Assert-N01Hash (Join-Path $runtime 'login/config/Database.ini') $database.jdbcSha
Assert-N01Hash (Join-Path $runtime 'game/config/Custom/PhantomPlayers.ini') $assets['game/config/Custom/PhantomPlayers.ini']
Assert-N01Hash (Join-Path $campaign 'execution.properties') (Get-N01TextHash $executionBody)
Assert-N01 (((Get-N01CandidatePins $runtime $campaign)|ConvertTo-Json -Depth 6 -Compress) -ceq ($receipt.controllerPins|ConvertTo-Json -Depth 6 -Compress)) 'CONTROLLER_PINS_DRIFT'
Assert-N01Off
Write-Output "TASK131_N01_SETUP_ATTESTED scenes=4 files=$($runtimeRows.Count) classes=$($classRows.Count) launchAuthorized=false"
