[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$original = 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\artifacts\local-play\runtime'
$private = Join-Path $module '.phantom-local/observe021'
$runtime = Join-Path $private 'runtime'
if (Test-Path -LiteralPath $runtime) { throw 'Task021 runtime already exists; no overwrite.' }
. (Join-Path $module 'tools/phantom-local-play/LocalPlay-Pilot.ps1')
New-Item -ItemType Directory -Path $private -Force | Out-Null
$preserved = @(foreach ($part in @('game/config','login/config','libs','game/data/geodata','local-play/pids')) {
    Get-ChildItem (Join-Path $original $part) -Recurse -File | ForEach-Object {
        [pscustomobject]@{path=$_.FullName.Substring($original.Length+1);sha256=(Get-FileHash $_.FullName).Hash}
    }
})
$preserved | Export-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$sourceConfig = Join-Path $original 'game/config/Database.ini'
$url = Get-PilotIniValue $sourceConfig 'URL'
if ($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay3\?') { throw 'Unexpected PLAY URL.' }
$secretRoot = Join-Path $private 'secrets'
Protect-PilotDirectory $secretRoot
$option = Join-Path $secretRoot 'client.cnf'
$login = Get-PilotIniValue $sourceConfig 'Login'
$password = Get-PilotIniValue $sourceConfig 'Password'
[IO.File]::WriteAllText($option,"[client]`nhost=127.0.0.1`nport=3308`nuser=$login`npassword=$password`n",[Text.UTF8Encoding]::new($false))
$client = 'C:\Program Files\MariaDB 11.4\bin\mariadb.exe'
$dump = 'C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe'
$db = 'l2jmobiush5_localplay_observe021'
$existing = & $client "--defaults-extra-file=$option" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$db'"
if ($LASTEXITCODE -ne 0 -or "$existing".Trim() -ne '0') { throw 'Clone database not absent.' }
$snapshot = Join-Path $private 'play-snapshot.sql'
& $dump "--defaults-extra-file=$option" --single-transaction --skip-lock-tables --skip-triggers --skip-routines --skip-events --hex-blob "--result-file=$snapshot" l2jmobiush5_localplay3
if ($LASTEXITCODE -ne 0) { throw 'PLAY read-only export failed.' }
if (Select-String -LiteralPath $snapshot -Pattern '^(CREATE|DROP) DATABASE|^USE ' -Quiet) { throw 'Unsafe dump database directive.' }
& $client "--defaults-extra-file=$option" -e "CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
if ($LASTEXITCODE -ne 0) { throw 'Fresh clone creation failed.' }
$import = Start-Process -FilePath $client -ArgumentList @("--defaults-extra-file=`"$option`"",$db) -RedirectStandardInput $snapshot -RedirectStandardOutput (Join-Path $private 'import.out') -RedirectStandardError (Join-Path $private 'import.err') -WindowStyle Hidden -Wait -PassThru
if ($import.ExitCode -ne 0) { throw 'Clone import failed.' }
& $client "--defaults-extra-file=$option" --batch -e "SELECT '$db' AS clone,(SELECT COUNT(*) FROM $db.characters) AS characters,(SELECT COUNT(*) FROM $db.phantom_profiles) AS profiles,(SELECT COUNT(*) FROM $db.phantom_profile_components) AS components; SELECT charId,char_name,accesslevel,online,level,exp,sp,x,y,z FROM $db.characters WHERE char_name='TestAdmin'"
if ($LASTEXITCODE -ne 0) { throw 'Clone verification failed.' }
& $client "--defaults-extra-file=$option" -e "UPDATE $db.characters SET accesslevel=100 WHERE char_name='TestAdmin'; SELECT charId,char_name,accesslevel,online,level,exp,sp,x,y,z FROM $db.characters WHERE char_name='TestAdmin'"
if ($LASTEXITCODE -ne 0) { throw 'Fresh clone TestAdmin setup failed.' }
New-Item -ItemType Directory -Path $runtime | Out-Null
foreach ($part in @('game','login','libs')) { Copy-Item -LiteralPath (Join-Path $module "dist/$part") -Destination $runtime -Recurse }
foreach ($part in @('game/config','login/config')) { Copy-Item -Path (Join-Path $original "$part/*") -Destination (Join-Path $runtime $part) -Recurse -Force }
foreach ($role in @('game','login')) {
    foreach ($file in @('java.cfg','log.cfg')) { Copy-Item -LiteralPath (Join-Path $original "$role/$file") -Destination (Join-Path $runtime "$role/$file") -Force }
}
Copy-Item -Path (Join-Path $original 'game/data/geodata/*') -Destination (Join-Path $runtime 'game/data/geodata') -Force
Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/LocalPlayPilot.ini') -Destination (Join-Path $runtime 'game/config/Custom/LocalPlayPilot.ini') -Force
$changes = [Collections.Generic.List[object]]::new()
function Override-Ini([string]$Relative,[string]$Key,[string]$Value) {
    $file = Join-Path $runtime $Relative
    $text = [IO.File]::ReadAllText($file)
    $pattern = '(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*)([^\r\n]*)'
    $matches = [regex]::Matches($text,$pattern)
    if ($matches.Count -ne 1) { throw "Expected one config key: $Relative/$Key" }
    $changes.Add([pscustomobject]@{path=$Relative;key=$Key;before=$matches[0].Groups[2].Value;after=$Value})
    [IO.File]::WriteAllText($file,[regex]::Replace($text,$pattern,{param($match) $match.Groups[1].Value+$Value}),[Text.UTF8Encoding]::new($false))
}
foreach ($role in @('game','login')) { Override-Ini "$role/config/Database.ini" 'URL' ($url.Replace('/l2jmobiush5_localplay3?','/l2jmobiush5_localplay_observe021?')) }
Override-Ini 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
Override-Ini 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
foreach ($entry in @{EnablePhantomSystem='True';EnablePhantomDiagnostics='True';PhantomPopulationTarget='1280';PhantomPopulationActiveTarget='8';MaxMaterializedPhantoms='8';MaxScheduledPhantomProfiles='10000'}.GetEnumerator()) { Override-Ini 'game/config/Custom/PhantomPlayers.ini' $entry.Key $entry.Value }
foreach ($entry in @{EnableLocalPlayPilot='True';EnableLocalPlayPilotAutoAttach='True';LocalPlayPilotAutoAttachCharacters='TestAdmin';EnableLocalPlaySyntheticHuman='False'}.GetEnumerator()) { Override-Ini 'game/config/Custom/LocalPlayPilot.ini' $entry.Key $entry.Value }
foreach ($key in @('GMStartupBuilderHide','GMStartupInvulnerable','GMStartupInvisible','GMStartupSilence')) { Override-Ini 'game/config/General.ini' $key 'False' }
$changes | Export-Csv (Join-Path $PSScriptRoot 'CONFIG_OVERRIDES.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
foreach ($name in @('LocalPlay-Ownership.ps1','Start-LocalPlay.ps1','Check-LocalPlay.ps1','LocalPlay-Pilot.ps1','Get-LocalPlayPilot.ps1','Invoke-LocalPlayPilot.ps1','Stop-LocalPlayPilot.ps1','Stop-LocalPlay.ps1')) { Copy-Item -LiteralPath (Join-Path $module "tools/phantom-local-play/$name") -Destination $runtime }
$invokePath = Join-Path $runtime 'Invoke-LocalPlayPilot.ps1'
$invoke = [IO.File]::ReadAllText($invokePath).Replace("'STATUS', 'CAPABILITIES'", "'STATUS', 'BEGIN_PHANTOM_CAUSAL_TRACE', 'SNAPSHOT_PHANTOM_CAUSAL_TRACE', 'END_PHANTOM_CAUSAL_TRACE', 'CAPABILITIES'")
[IO.File]::WriteAllText($invokePath,$invoke,[Text.UTF8Encoding]::new($false))
# Reuse previously reviewed stock shutdown agents; bind the private helper to observe021 only.
$oldOps = 'C:/Users/ZBook/.codex/worktrees/m1-inventory-013/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops013'
$ops = Join-Path $module '.phantom-local/ops021'
New-Item -ItemType Directory -Path $ops -Force | Out-Null
foreach ($name in @('graceful-shutdown.jar','graceful-login-shutdown.jar','RequestGracefulShutdown.class')) { Copy-Item -LiteralPath (Join-Path $oldOps $name) -Destination $ops }
$stop = [IO.File]::ReadAllText((Join-Path $oldOps 'Stop-ExactOwnedGracefully.ps1')).Replace('observe01[13]', 'observe021').Replace('013','019a')
[IO.File]::WriteAllText((Join-Path $ops 'Stop-ExactOwnedGracefully.ps1'),$stop,[Text.UTF8Encoding]::new($false))
$wrapper = 'param(); & (Join-Path $PSScriptRoot ''../../ops021/Stop-ExactOwnedGracefully.ps1'') -RuntimeRoot $PSScriptRoot'
[IO.File]::WriteAllText((Join-Path $runtime 'Stop-LocalPlay.ps1'),$wrapper,[Text.UTF8Encoding]::new($false))
& python (Join-Path $PSScriptRoot 'Restore-Private-Catalogs.py') $runtime
if ($LASTEXITCODE -ne 0) { throw 'Private pinned catalog verification failed.' }
$old = Get-Content (Join-Path $original 'local-play.json') -Raw | ConvertFrom-Json
$flat = [ordered]@{}
foreach ($property in $old.PSObject.Properties) { if ($property.Value -is [string] -or $property.Value -is [ValueType]) { $flat[$property.Name]=$property.Value } }
$flat.databaseConfig='USER_CONFIRMED_EXISTING'
$flat.databaseName=$db
$flat.activeTarget=8
$flat.materializedCap=8
$flat.codeSha=(& git -C $module rev-parse HEAD).Trim()
$flat.gameSourceCodeSha=$flat.codeSha
$flat.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
$flat.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
$flat | ConvertTo-Json | Set-Content (Join-Path $runtime 'local-play.json') -Encoding utf8
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
Initialize-PilotMailbox ([pscustomobject]@{PilotRoot=(Join-Path $runtime 'playtest-pilot')})
foreach ($entry in $preserved) { if ((Get-FileHash (Join-Path $original $entry.path)).Hash -cne $entry.sha256) { throw 'Original PLAY file changed.' } }
Write-Output "Fresh runtime ready: $runtime; original $($preserved.Count) hashes preserved."
