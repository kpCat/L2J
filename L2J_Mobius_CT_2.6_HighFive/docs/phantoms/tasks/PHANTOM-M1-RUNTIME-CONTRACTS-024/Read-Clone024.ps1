[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,[Parameter(Mandatory)][string]$OutputRoot,
      [long[]]$ProfileIds=@())
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if($runtime -notmatch '[\\/]contract024[a-h][\\/]runtime$'){throw 'Exact TASK023 clone required.'}
if(Test-Path -LiteralPath $OutputRoot){throw 'Evidence already exists.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$cfg=Join-Path $runtime 'game/config/Database.ini'
$url=Get-PilotIniValue $cfg 'URL'
if($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/(l2jmobiush5_localplay_contract024[a-h])\?'){throw 'Owned DB guard failed.'}
$database=$Matches[2]
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
$savedPassword=$env:MYSQL_PWD
try{
    $env:MYSQL_PWD=Get-PilotIniValue $cfg 'Password'
    $login=Get-PilotIniValue $cfg 'Login'
    $client='C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
    $common=@('--no-defaults','--host=127.0.0.1','--port=3308',"--user=$login",'--batch',"--database=$database")
    $queries=[ordered]@{
        summary="SELECT DATABASE(); SELECT COUNT(*) AS profiles,COUNT(DISTINCT character_object_id) AS distinct_characters FROM phantom_profiles; SELECT component_type,COUNT(*) AS components,SUM(OCTET_LENGTH(payload)) AS bytes FROM phantom_profile_components GROUP BY component_type ORDER BY component_type; SELECT charId,char_name,online FROM characters WHERE online<>0 OR charId=268492939 ORDER BY charId;"
        progress="SELECT p.profile_id,c.charId,c.char_name,c.level,c.exp,c.sp,c.expBeforeDeath,c.x,c.y,c.z,c.curHp,c.curMp,c.curCp FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id ORDER BY p.profile_id;"
        inventory="SELECT p.profile_id,i.owner_id,i.object_id,i.item_id,i.count,i.loc,i.loc_data,i.enchant_level FROM phantom_profiles p JOIN items i ON i.owner_id=p.character_object_id ORDER BY p.profile_id,i.object_id;"
        components="SELECT profile_id,component_type,row_version,SHA2(payload,256) AS payload_hash FROM phantom_profile_components ORDER BY profile_id,component_type;"
    }
    foreach($entry in $queries.GetEnumerator()){
        & $client @common -e $entry.Value | Set-Content (Join-Path $OutputRoot "$($entry.Key).tsv") -Encoding utf8
        if($LASTEXITCODE -ne 0){throw "Read-only snapshot failed: $($entry.Key)"}
    }
    if($ProfileIds.Count){
        if(@($ProfileIds | Where-Object {$_ -le 0}).Count){throw 'Positive selected profile IDs required.'}
        $ids=$ProfileIds -join ','
        & $client @common --skip-column-names -e "SELECT profile_id,row_version,component_type,HEX(payload) FROM phantom_profile_components WHERE profile_id IN ($ids) AND component_type IN ('background.state','background.catchup') ORDER BY profile_id,component_type" | Set-Content (Join-Path $OutputRoot 'durable-hex.tsv') -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'Read-only selected receipts failed.'}
        $module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
        $cp="$(Split-Path $module -Parent)/build/bin;$module/dist/libs/*"
        & java -cp $cp (Join-Path $PSScriptRoot 'ReadDurable023.java') (Join-Path $OutputRoot 'durable-hex.tsv') | Set-Content (Join-Path $OutputRoot 'durable-decoded.txt') -Encoding utf8
        if($LASTEXITCODE -ne 0){throw 'Existing codec decode failed.'}
    }
    "READ_ONLY_CLONE_SNAPSHOT database=$database output=$OutputRoot"
}finally{$env:MYSQL_PWD=$savedPassword}
