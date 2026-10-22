[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module069=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo069=Split-Path $module069 -Parent
$task069=Join-Path $repo069 '.phantom-local/task069'
$candidate069=Join-Path ([IO.Path]::GetPathRoot($repo069)) 'PhantomWorld/Playable-069-candidate'
$source069=Join-Path ([IO.Path]::GetPathRoot($repo069)) 'PhantomWorld/Personal-Adventure-067-candidate'
if((Split-Path $module069 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK069: exact module required.'}
foreach($path069 in @((Join-Path $task069 'ownership069.properties'),$candidate069)){
    if(Test-Path -LiteralPath $path069){throw "TASK069: existing resource is preserved: $path069"}
}
$ports069=@(2106,7777,9150,2250,7950,9250)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports069).Count){throw 'TASK069: selected ports occupied; no process action.'}
$protectedProcesses069=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar069=Join-Path $source069 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar069 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK069: protected067 source JAR differs.'}
$package069=Join-Path $module069 'docs/phantoms/tasks/PHANTOM-PLAYABLE-RELEASE-069'
$packageRows069=@(foreach($line069 in Get-Content -LiteralPath (Join-Path $package069 'PACKAGE_SHA256.txt')){
    if($line069 -cnotmatch '^([0-9a-fA-F]{64})  ([A-Z_]+\.(?:md|txt|tsv))$'){throw 'TASK069: invalid package manifest.'}
    $expected069=$Matches[1];$relative069=$Matches[2]
    $actual069=(Get-FileHash -LiteralPath (Join-Path $package069 $relative069) -Algorithm SHA256).Hash
    if($actual069 -ine $expected069){throw "TASK069: package mismatch: $relative069"}
    [pscustomobject]@{relativePath=$relative069;sha256=$actual069}
})
if($packageRows069.Count -ne 8){throw 'TASK069: exact eight package inputs required.'}
$databaseSource069=Join-Path $repo069 '.phantom-local/task067/private/Database.test.ini'
$database069=[IO.File]::ReadAllText($databaseSource069)
if($database069 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5_personal_test_067\?useSSL=false' -or $database069 -notmatch '(?m)^MaximumDatabaseConnections\s*=\s*4\s*$'){throw 'TASK069: bounded protected database input differs.'}
New-Item -ItemType Directory -Path $task069 -Force | Out-Null
foreach($part069 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task069 $part069) -Force | Out-Null}
$sid069='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task069 'private') /inheritance:r /grant:r ($sid069+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK069: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task069 'ownership069.properties'),"version=1`nowner=TASK069_PLAYABLE_RELEASE`ntaskRoot=$($task069.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_069`nreleaseSchema=l2jmobiush5_phantom_release_069`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role069 in @('test','release')){
    $settings069=$database069.Replace('l2jmobiush5_personal_test_067',"l2jmobiush5_phantom_$($role069)_069")
    [IO.File]::WriteAllText((Join-Path $task069 "private/Database.$role069.ini"),$settings069,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module069 'dist/game/config') -Destination (Join-Path $task069 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module069 'dist/game/data') -Destination (Join-Path $task069 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task069 'private/Database.test.ini') -Destination (Join-Path $task069 'native-test/game/config/Database.ini') -Force
$geoRoot069=Join-Path $source069 'game/data/geodata'
$geoRows069=@(Get-ChildItem -LiteralPath $geoRoot069 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK069: physical GeoData required.'}
    $target069=Join-Path $task069 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target069){throw 'TASK069: duplicate GeoData target.'}
    $hash069=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target069
    if((Get-FileHash -LiteralPath $target069 -Algorithm SHA256).Hash -ine $hash069){throw 'TASK069: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash069}
})
if($geoRows069.Count -ne 203){throw 'TASK069: expected203 source GeoData regions.'}
$geoRows069 | Export-Csv -LiteralPath (Join-Path $task069 'GEODATA_INPUTS069.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows069 | Export-Csv -LiteralPath (Join-Path $task069 'PACKAGE_INPUTS069.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses069 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task069 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows069=@(foreach($p069 in @($sourceJar069,$databaseSource069,(Join-Path $source069 'ownership067.properties'),(Join-Path $source069 'local-play.json'))){
    [pscustomobject]@{path=$p069;sha256=(Get-FileHash -LiteralPath $p069 -Algorithm SHA256).Hash}
})
$protectedRows069 | Export-Csv -LiteralPath (Join-Path $task069 'PROTECTED_INPUTS069.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output 'OWN069_INPUTS_CREATED; package8/8; protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private.'
