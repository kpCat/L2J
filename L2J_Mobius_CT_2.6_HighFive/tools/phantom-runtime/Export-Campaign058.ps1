[CmdletBinding()]
param([switch]$ValidateOnly,[switch]$ReadOnlyPreflight)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Campaign058Ownership.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$expected='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ($module -ine $expected) {throw 'TASK058 export requires exact OWN.'}
$lane=Join-Path $module '.phantom-local/contract058a'
$source=Join-Path $lane 'runtime'
$releaseInput=Join-Path $lane 'release-input'
$target='D:\PhantomWorld\M2-M7-058-candidate'
$sourceDb='l2jmobiush5_phantom_test_058a'
$targetDb='l2jmobiush5_phantom_release_058a'
$sourceOwner=Read-Campaign058Owner (Join-Path $source 'ownership058.properties')
Assert-Campaign058Binding $source $sourceDb $sourceOwner
$releaseOwner=Read-Campaign058Owner (Join-Path $releaseInput 'ownership058.properties')
Assert-Campaign058Binding $target $targetDb $releaseOwner
if ($sourceOwner.schemaAggregateSha256 -cne $releaseOwner.schemaAggregateSha256 -or $sourceOwner.schemaAggregateSha256 -cne ([IO.File]::ReadAllText((Join-Path $lane 'bootstrap.complete')).Trim())) {throw 'Owned schema bootstrap fingerprint differs.'}
function Read-Setting([string]$Path,[string]$Key) {
    $current=[IO.Path]::GetPathRoot($Path)
    foreach ($part in $Path.Substring($current.Length).Split('\')) {
        if (!$part) {continue}; $current=Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Alias in private configuration path is forbidden.'}
    }
    $match=[regex]::Match([IO.File]::ReadAllText($Path),'(?m)^\s*'+[regex]::Escape($Key)+'\s*=\s*([^\r\n]*)\r?$')
    if (!$match.Success) {throw "Missing private setting: $Key"}; return $match.Groups[1].Value.Trim()
}
foreach ($role in @('game','login')) {
    foreach ($pair in @(@($source,$sourceDb),@($releaseInput,$targetDb))) {
        $path=Join-Path $pair[0] "$role/config/Database.ini"
        $url=Read-Setting $path 'URL'
        if ($url -notmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+$pair[1]+'\?useSSL=false(?:&[A-Za-z0-9=:-]+)*$')) {throw 'Exact local058 database URL required.'}
    }
}
$sourceConfig=Join-Path $source 'game/config/Database.ini'
$targetConfig=Join-Path $releaseInput 'game/config/Database.ini'
foreach ($key in @('Login','Password')) {
    if ((Read-Setting $sourceConfig $key) -cne (Read-Setting $targetConfig $key)) {throw 'Release credentials differ from the approved private source.'}
}
if ((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath (Join-Path $lane 'release-export.started')) -or (Test-Path -LiteralPath (Join-Path $lane 'release-export.complete'))) {throw 'Release path or one-use export receipt already exists; no replay/reset.'}
$client='C:\Program Files\MariaDB 11.4\bin\mariadb.exe'
$dump='C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe'
foreach ($tool in @($client,$dump)) {if (!(Test-Path -LiteralPath $tool -PathType Leaf)) {throw 'Installed native MariaDB11.4 tools required.'}}
if ($ValidateOnly) {Write-Output 'TASK058_EXPORT_PREFLIGHT_PASS; DB_WRITES=0; PROCESS_ACTIONS=0'; return}
$statusRaw=& (Join-Path $source 'tools/Runtime.ps1') -Action Status -RuntimeRoot $source
if (!$?) {throw 'Own source status failed.'}
$status=($statusRaw -join "`n") | ConvertFrom-Json
if ($status.game.state -cnotin @('STOPPED','STALE_RECORD') -or $status.login.state -cnotin @('STOPPED','STALE_RECORD')) {throw 'Cumulative058a must be normally stopped.'}
$lock=[IO.File]::Open((Join-Path $source 'local-play/operator.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$savedMysqlPassword=$env:MYSQL_PWD
try {
    if (Get-NetTCPConnection -State Listen -LocalPort 2138,7838,9138 -ErrorAction SilentlyContinue) {throw 'Own058 runtime ports are occupied.'}
    # Only this exporter process and its native children receive the approved private credential.
    $env:MYSQL_PWD=Read-Setting $sourceConfig 'Password'
    $exportLogin=Read-Setting $sourceConfig 'Login'
    if ($exportLogin -cnotmatch '^[A-Za-z0-9_]+$') {throw 'Native import user name is outside the approved argument bounds.'}
    $connectionArgs=@('--no-defaults','--protocol=tcp','--host=127.0.0.1','--port=3308','--skip-ssl','--default-character-set=utf8mb4',"--user=$exportLogin")
    function Query([string]$Sql) { $rows=@(& $client @connectionArgs --batch --skip-column-names "--execute=$Sql"); if ($LASTEXITCODE -ne 0) {throw 'Native scoped SQL failed.'}; return $rows }
    if (@(Query "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$targetDb'").Count) {throw 'Release schema already exists; overwrite/import replay forbidden.'}
    if (@(Query "SELECT ID FROM information_schema.PROCESSLIST WHERE DB='$sourceDb' AND ID<>CONNECTION_ID()").Count) {throw 'Source058a has a live database connection; quiescence refused.'}
    $tables=@(Query "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$sourceDb' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")
    if ($tables.Count -ne 124 -or @($tables | Where-Object {$_ -cnotmatch '^[a-z0-9_]+$'}).Count) {throw 'Exact stock058a table inventory differs.'}
    if ($ReadOnlyPreflight) {
        $sample=@(Query 'CHECKSUM TABLE `l2jmobiush5_phantom_test_058a`.`characters` EXTENDED')
        if ($sample.Count -ne 1 -or ($sample[0] -split "`t")[-1] -ceq 'NULL') {throw 'Native read-only checksum support unavailable.'}
        Write-Output 'TASK058_NATIVE_EXPORT_PREFLIGHT_PASS; sourceStopped=true; targetAbsent=true; tables=124; nativeChecksum=available; DB_WRITES=0'
        return
    }
    $sql=Join-Path $lane 'release-export.sql'
    if (Test-Path -LiteralPath $sql) {throw 'Own stopped export already exists.'}
    [IO.File]::WriteAllText((Join-Path $lane 'release-export.started'),[DateTime]::UtcNow.ToString('o'),[Text.UTF8Encoding]::new($false))
    & $dump @connectionArgs --single-transaction --quick --skip-lock-tables --skip-add-locks --skip-add-drop-table --skip-comments --routines --events --triggers --hex-blob "--result-file=$sql" $sourceDb
    if ($LASTEXITCODE -ne 0) {throw 'Native stopped export failed; receipt retained.'}
    if ([IO.File]::ReadAllText($sql) -match '(?im)^\s*(?:USE\s|CREATE\s+DATABASE\s|DROP\s)') {throw 'Export contains forbidden database switch/drop; target not created.'}
    $null=Query 'CREATE DATABASE `l2jmobiush5_phantom_release_058a` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
    $process=Start-Process -FilePath $client -ArgumentList ($connectionArgs + @("--database=$targetDb",'--binary-mode')) -WindowStyle Hidden -RedirectStandardInput $sql -RedirectStandardOutput (Join-Path $lane 'release-import.stdout.log') -RedirectStandardError (Join-Path $lane 'release-import.stderr.log') -PassThru -Wait
    if ($process.ExitCode -ne 0) {throw 'Native import failed; no reset/reimport fallback.'}
    $proof=[Collections.Generic.List[string]]::new()
    foreach ($table in $tables) {
        $rows=@(Query ("CHECKSUM TABLE ``$sourceDb``.``$table``,``$targetDb``.``$table`` EXTENDED"))
        if ($rows.Count -ne 2 -or ($rows[0] -split "`t")[-1] -cne ($rows[1] -split "`t")[-1] -or ($rows[0] -split "`t")[-1] -ceq 'NULL') {throw "Native source/release checksum differs: $table"}
        $proof.Add($rows[0]); $proof.Add($rows[1])
    }
    $sourceMetadata=@(Query "SELECT TABLE_NAME,ENGINE,COALESCE(AUTO_INCREMENT,0),TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='$sourceDb' ORDER BY TABLE_NAME")
    $targetMetadata=@(Query "SELECT TABLE_NAME,ENGINE,COALESCE(AUTO_INCREMENT,0),TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='$targetDb' ORDER BY TABLE_NAME")
    if (($sourceMetadata -join "`n") -cne ($targetMetadata -join "`n")) {throw 'Release engine/counter/collation inventory differs.'}
    if (@(Query "SELECT TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$targetDb' AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA<>'$targetDb'").Count) {throw 'Release foreign key refers outside its exact schema.'}
    $proof.Add('dumpSha256='+(Get-FileHash -LiteralPath $sql -Algorithm SHA256).Hash)
    $proof.Add('sourceStopped=true; nativeSingleTransaction=true; tables=124; engines/counters/collations=equal; foreignKeys=releaseOnly; old057Untouched=true')
    [IO.File]::WriteAllLines((Join-Path $lane 'release-export.complete'),$proof,[Text.UTF8Encoding]::new($false))
    Write-Output 'TASK058_RELEASE_DB_CREATED_AND_VERIFIED tables=124; consistent stopped native export; all table checksums equal; no source writes/reset/users/grants.'
} finally {
    $env:MYSQL_PWD=$savedMysqlPassword
    $lock.Dispose()
}
