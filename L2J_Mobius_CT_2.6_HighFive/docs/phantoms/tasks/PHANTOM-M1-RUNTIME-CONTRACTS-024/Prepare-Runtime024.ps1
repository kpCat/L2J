[CmdletBinding()]
param([ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$original='C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime'
$private=Join-Path $module ".phantom-local/contract024$Episode"
$runtime=Join-Path $private 'runtime'
$db="l2jmobiush5_localplay_contract024$Episode"
if(Test-Path -LiteralPath $private){throw 'Owned episode directory already exists; no overwrite.'}
. (Join-Path $module 'tools/phantom-local-play/LocalPlay-Pilot.ps1')
New-Item -ItemType Directory -Path $private | Out-Null
$preserved=@(foreach($part in @('game/config','login/config','libs','game/data/geodata','local-play/pids')){
    Get-ChildItem (Join-Path $original $part) -Recurse -File | ForEach-Object {
        [pscustomobject]@{path=$_.FullName.Substring($original.Length+1);sha256=(Get-FileHash $_.FullName).Hash}
    }
})
$preserved | Export-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$config=Join-Path $original 'game/config/Database.ini'
$url=Get-PilotIniValue $config 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay3\?'){throw 'Unexpected read-only PLAY source.'}
$secrets=Join-Path $private 'secrets'
Protect-PilotDirectory $secrets
$option=Join-Path $secrets 'client.cnf'
$login=Get-PilotIniValue $config 'Login'
$password=Get-PilotIniValue $config 'Password'
[IO.File]::WriteAllText($option,"[client]`nhost=127.0.0.1`nport=3308`nuser=$login`npassword=$password`n",[Text.UTF8Encoding]::new($false))
$client='C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
$dump='C:/Program Files/MariaDB 11.4/bin/mariadb-dump.exe'
$exists=& $client "--defaults-extra-file=$option" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$db'"
if($LASTEXITCODE -ne 0 -or "$exists".Trim() -cne '0'){throw 'Clone database must be absent.'}
$snapshot=Join-Path $private 'play-snapshot.sql'
& $dump "--defaults-extra-file=$option" --single-transaction --skip-lock-tables --skip-triggers --skip-routines --skip-events --hex-blob "--result-file=$snapshot" l2jmobiush5_localplay3
if($LASTEXITCODE -ne 0){throw 'Read-only export failed.'}
if(Select-String -LiteralPath $snapshot -Pattern '^(CREATE|DROP) DATABASE|^USE ' -Quiet){throw 'Unsafe database directive in export.'}
& $client "--defaults-extra-file=$option" -e "CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
if($LASTEXITCODE -ne 0){throw 'Clone CREATE failed.'}
$import=Start-Process -FilePath $client -ArgumentList @("--defaults-extra-file=`"$option`"",$db) -RedirectStandardInput $snapshot -RedirectStandardOutput (Join-Path $private 'import.out') -RedirectStandardError (Join-Path $private 'import.err') -WindowStyle Hidden -Wait -PassThru
if($import.ExitCode -ne 0){throw 'Clone import failed.'}
$query="SELECT charId,char_name,online,level,exp,sp,x,y,z FROM $db.characters WHERE charId=268492939 AND char_name='TestAdmin'; SELECT COUNT(*) FROM $db.phantom_profiles WHERE character_object_id=268492939;"
# Column name is verified against the repository schema before this script is run.
$admin=@(& $client "--defaults-extra-file=$option" --batch --skip-column-names -e $query)
if($LASTEXITCODE -ne 0 -or $admin.Count -ne 2 -or $admin[0].Split("`t")[2] -cne '0' -or $admin[1] -cne '0'){throw 'Exact offline ordinary clone TestAdmin required.'}
$admin | Set-Content (Join-Path $private 'observer-before.tsv') -Encoding utf8
New-Item -ItemType Directory -Path $runtime | Out-Null
foreach($part in @('game','login','libs')){Copy-Item -LiteralPath (Join-Path $module "dist/$part") -Destination $runtime -Recurse}
foreach($part in @('game/config','login/config')){Copy-Item -Path (Join-Path $original "$part/*") -Destination (Join-Path $runtime $part) -Recurse -Force}
foreach($role in @('game','login')){foreach($file in @('java.cfg','log.cfg')){Copy-Item -LiteralPath (Join-Path $original "$role/$file") -Destination (Join-Path $runtime "$role/$file") -Force}}
Copy-Item -Path (Join-Path $original 'game/data/geodata/*') -Destination (Join-Path $runtime 'game/data/geodata') -Force
Copy-Item -LiteralPath (Join-Path $module 'dist/game/config/Custom/LocalPlayPilot.ini') -Destination (Join-Path $runtime 'game/config/Custom/LocalPlayPilot.ini') -Force
$changes=[Collections.Generic.List[object]]::new()
function Override-Ini([string]$Relative,[string]$Key,[string]$Value){
    $file=Join-Path $runtime $Relative; $text=[IO.File]::ReadAllText($file)
    $pattern='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*)([^\r\n]*)'
    $matches=[regex]::Matches($text,$pattern)
    if($matches.Count -ne 1){throw "Expected one config key $Relative/$Key"}
    $changes.Add([pscustomobject]@{path=$Relative;key=$Key;before=$matches[0].Groups[2].Value;after=$Value})
    [IO.File]::WriteAllText($file,[regex]::Replace($text,$pattern,{param($match) $match.Groups[1].Value+$Value}),[Text.UTF8Encoding]::new($false))
}
foreach($role in @('game','login')){Override-Ini "$role/config/Database.ini" 'URL' ($url.Replace('/l2jmobiush5_localplay3?',"/$db`?"))}
foreach($entry in @{EnablePhantomSystem='True';EnablePhantomDiagnostics='True';PhantomPopulationTarget='1280';PhantomPopulationActiveTarget='8';MaxMaterializedPhantoms='8';MaxScheduledPhantomProfiles='10000'}.GetEnumerator()){Override-Ini 'game/config/Custom/PhantomPlayers.ini' $entry.Key $entry.Value}
foreach($entry in @{EnableLocalPlayPilot='True';EnableLocalPlayPilotAutoAttach='True';LocalPlayPilotAutoAttachCharacters='TestAdmin';EnableLocalPlaySyntheticHuman='True';LocalPlaySyntheticCharacterObjectId='268492939';LocalPlaySyntheticCharacterName='TestAdmin'}.GetEnumerator()){Override-Ini 'game/config/Custom/LocalPlayPilot.ini' $entry.Key $entry.Value}
foreach($key in @('GMStartupBuilderHide','GMStartupInvulnerable','GMStartupInvisible','GMStartupSilence')){Override-Ini 'game/config/General.ini' $key 'False'}
$pilotConfig=Join-Path $runtime 'game/config/Custom/LocalPlayPilot.ini'
$pilotAcl=Get-Acl -LiteralPath $pilotConfig
$pilotAcl.SetAccessRuleProtection($true,$false)
foreach($rule in @($pilotAcl.Access)){$pilotAcl.RemoveAccessRuleSpecific($rule) | Out-Null}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$pilotAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,[Security.AccessControl.FileSystemRights]::FullControl,[Security.AccessControl.AccessControlType]::Allow))
Set-Acl -LiteralPath $pilotConfig -AclObject $pilotAcl
Assert-PilotPrivateFile $pilotConfig
$changes | Export-Csv (Join-Path $private 'config-overrides.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
foreach($name in @('LocalPlay-Ownership.ps1','Start-LocalPlay.ps1','Check-LocalPlay.ps1','LocalPlay-Pilot.ps1','Get-LocalPlayPilot.ps1','Invoke-LocalPlayPilot.ps1','Start-LocalPlaySynthetic.ps1','Stop-LocalPlayPilot.ps1')){Copy-Item -LiteralPath (Join-Path $module "tools/phantom-local-play/$name") -Destination $runtime}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Stop-LocalPlay024.ps1') -Destination (Join-Path $runtime 'Stop-LocalPlay.ps1')
foreach($name in @('high-five-population-v1.xml','high-five-population-v2.xml','high-five-ecology-v1.xml')){
    $relative="L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/population/$name"
    $bytes= & git -C $module show "HEAD:$relative"
    $target=Join-Path $runtime "game/data/phantoms/population/$name"
    $normalized=($bytes -join "`n")+"`n"
    if([IO.File]::ReadAllText($target).Replace("`r`n","`n") -cne $normalized){throw 'Pinned catalog semantic mismatch.'}
    [IO.File]::WriteAllText($target,$normalized,[Text.UTF8Encoding]::new($false))
}
$old=Get-Content (Join-Path $original 'local-play.json') -Raw | ConvertFrom-Json
$flat=[ordered]@{}
foreach($property in $old.PSObject.Properties){if($property.Value -is [string] -or $property.Value -is [ValueType]){$flat[$property.Name]=$property.Value}}
$flat.databaseConfig='FRESH_LOCAL_PROVISIONED'; $flat.databaseName=$db; $flat.activeTarget=8; $flat.materializedCap=8
$flat.codeSha=(& git -C $module rev-parse HEAD).Trim(); $flat.gameSourceCodeSha=$flat.codeSha
$flat.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
$flat.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
$flat | ConvertTo-Json | Set-Content (Join-Path $runtime 'local-play.json') -Encoding utf8
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
Initialize-PilotMailbox ([pscustomobject]@{PilotRoot=(Join-Path $runtime 'playtest-pilot')})
foreach($entry in $preserved){if((Get-FileHash (Join-Path $original $entry.path)).Hash -cne $entry.sha256){throw 'Original PLAY runtime changed.'}}
Write-Output "OWNED_RUNTIME_READY runtime=$runtime db=$db originalHashes=$($preserved.Count)"
