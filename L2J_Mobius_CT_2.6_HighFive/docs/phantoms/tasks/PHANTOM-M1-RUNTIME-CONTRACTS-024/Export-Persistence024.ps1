[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$CohortJson,[Parameter(Mandatory)][string]$OutputRoot)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot);$output=[IO.Path]::GetFullPath($OutputRoot)
if($runtime -notmatch '[\\/]contract024[a-h][\\/]runtime$'){throw 'Exact owned TASK024 runtime required.'}
if(Test-Path -LiteralPath $output){throw 'Evidence exists; no overwrite.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$cfg=Join-Path $runtime 'game/config/Database.ini'
$url=Get-PilotIniValue $cfg 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract024[a-h])\?'){throw 'Owned DB guard failed.'}
$database=$Matches[2]
$rows=@(Get-Content -LiteralPath $CohortJson -Raw | ConvertFrom-Json)
if($rows.Count -lt 1 -or $rows.Count -gt 8 -or @($rows | Where-Object {[long]$_.profileId -le 0}).Count){throw 'Exact full cohort 1..8 required.'}
$ids=($rows | ForEach-Object {[long]$_.profileId}) -join ','
New-Item -ItemType Directory -Path $output | Out-Null
$oldPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=Get-PilotIniValue $cfg 'Password';$login=Get-PilotIniValue $cfg 'Login'
    $client='C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
    $common=@('--no-defaults','--host=127.0.0.1','--port=3308',"--user=$login",'--batch',"--database=$database")
    $queries=[ordered]@{
        characters="SELECT p.profile_id AS profileId,c.charId AS objectId,c.level,c.exp,c.sp,c.expBeforeDeath,c.curHp AS hp,c.maxHp,c.curMp AS mp,c.maxMp,c.curCp AS cp,c.maxCp,c.x,c.y,c.z,c.heading,CASE WHEN c.classid=c.base_class THEN 0 ELSE sc.class_index END AS classIndex,c.classid AS classId,c.race,c.vitality_points AS vitality,c.online FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id LEFT JOIN character_subclasses sc ON sc.charId=c.charId AND sc.class_id=c.classid WHERE p.profile_id IN ($ids) ORDER BY p.profile_id;"
        items="SELECT p.profile_id AS profileId,i.owner_id,i.object_id,i.item_id,i.count,i.loc,i.loc_data,i.enchant_level FROM phantom_profiles p JOIN items i ON i.owner_id=p.character_object_id WHERE p.profile_id IN ($ids) ORDER BY p.profile_id,i.object_id;"
        skills="SELECT p.profile_id AS profileId,s.charId,s.skill_id,s.skill_level,s.class_index FROM phantom_profiles p JOIN character_skills s ON s.charId=p.character_object_id WHERE p.profile_id IN ($ids) ORDER BY p.profile_id,s.class_index,s.skill_id;"
        counts="SELECT DATABASE() AS database_name,(SELECT COUNT(*) FROM phantom_profile_components WHERE component_type='background.owned-store') AS pendingOwnedStores,(SELECT COUNT(*) FROM characters WHERE online<>0) AS onlineCharacters;"
    }
    foreach($query in $queries.GetEnumerator()){
        & $client @common -e $query.Value | Set-Content (Join-Path $output ($query.Key+'.tsv')) -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'Read-only persistence export failed: '+$query.Key}
    }
    & $client @common --skip-column-names -e "SELECT profile_id,row_version,component_type,HEX(payload) FROM phantom_profile_components WHERE profile_id IN ($ids) AND component_type IN ('background.state','background.native-context','background.owned-store') ORDER BY profile_id,component_type;" | Set-Content (Join-Path $output 'durable-hex.tsv') -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Read-only durable payload export failed.'}
    $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
    & java -cp "$module/dist/libs/GameServer.jar;$module/dist/libs/*" (Join-Path $PSScriptRoot 'ReadDurable024.java') (Join-Path $output 'durable-hex.tsv') | Set-Content (Join-Path $output 'durable-decoded.tsv') -Encoding utf8
    if($LASTEXITCODE -ne 0){throw 'Existing codec decode failed.'}
    Get-Content (Join-Path $runtime 'local-play.json') -Raw | Set-Content (Join-Path $output 'runtime-manifest.json') -Encoding utf8
    'TASK024_PERSISTENCE_EXPORT database='+$database+' cohort='+$rows.Count+' output='+$output
}finally{$env:MYSQL_PWD=$oldPassword}
