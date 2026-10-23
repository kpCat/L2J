[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task072Common.ps1')
$module072=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo072=Split-Path $module072 -Parent
$task072=Join-Path $repo072 '.phantom-local/task072'
$candidate072=Join-Path ([IO.Path]::GetPathRoot($repo072)) 'PhantomWorld/Sustainable-Life-072-candidate'
$source072=Join-Path ([IO.Path]::GetPathRoot($repo072)) 'PhantomWorld/Personal-Adventure-067-candidate'
foreach($plain072 in @($module072,$task072,$candidate072,$source072,(Join-Path $task072 'private'),(Join-Path $task072 'reports'),(Join-Path $task072 'native-test/game/config'),(Join-Path $task072 'native-test/game/data/geodata'))){Assert-Plain072 $plain072}
if((Split-Path $module072 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK072: exact module required.'}
foreach($path072 in @((Join-Path $task072 'ownership072.properties'),$candidate072)){
    if(Test-Path -LiteralPath $path072){throw "TASK072: existing resource is preserved: $path072"}
}
$ports072=@(2106,7777,9152,2252,7952,9252)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports072).Count){throw 'TASK072: selected ports occupied; no process action.'}
$protectedProcesses072=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar072=Join-Path $source072 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar072 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK072: protected067 source JAR differs.'}
$package072=Join-Path $module072 'docs/phantoms/tasks/PHANTOM-SUSTAINABLE-LIFE-072'
$packageRows072=@(Get-ChildItem -LiteralPath $package072 -File | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{relativePath=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$databaseSource072=Join-Path $module072 'dist/game/config/Database.ini'
Assert-Plain072 $databaseSource072
$database072=[IO.File]::ReadAllText($databaseSource072)
if($database072 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5\?'){throw 'TASK072: repository loopback operator input differs.'}
$database072=[regex]::Replace($database072,'(?m)^MaximumDatabaseConnections\s*=.*$','MaximumDatabaseConnections = 4')
New-Item -ItemType Directory -Path $task072 -Force | Out-Null
foreach($part072 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task072 $part072) -Force | Out-Null}
$sid072='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task072 'private') /inheritance:r /grant:r ($sid072+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK072: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task072 'ownership072.properties'),"version=1`nowner=TASK072_SUSTAINABLE_LIFE`ntaskRoot=$($task072.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_072`nreleaseSchema=l2jmobiush5_phantom_release_072`ninstallationId=$([guid]::NewGuid().ToString())`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role072 in @('test','release')){
    $settings072=[regex]::Replace($database072,'(?m)^URL\s*=.*$',"URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_$($role072)_072?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8")
    [IO.File]::WriteAllText((Join-Path $task072 "private/Database.$role072.ini"),$settings072,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module072 'dist/game/config') -Destination (Join-Path $task072 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module072 'dist/game/data') -Destination (Join-Path $task072 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task072 'private/Database.test.ini') -Destination (Join-Path $task072 'native-test/game/config/Database.ini') -Force
$geoRoot072=Join-Path $source072 'game/data/geodata'
Assert-Plain072 $geoRoot072
$geoRows072=@(Get-ChildItem -LiteralPath $geoRoot072 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK072: physical GeoData required.'}
    $target072=Join-Path $task072 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target072){throw 'TASK072: duplicate GeoData target.'}
    $hash072=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target072
    if((Get-FileHash -LiteralPath $target072 -Algorithm SHA256).Hash -ine $hash072){throw 'TASK072: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash072}
})
if($geoRows072.Count -ne 203){throw 'TASK072: expected203 source GeoData regions.'}
$geoRows072 | Export-Csv -LiteralPath (Join-Path $task072 'GEODATA_INPUTS072.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows072 | Export-Csv -LiteralPath (Join-Path $task072 'PACKAGE_INPUTS072.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses072 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task072 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows072=@(foreach($p072 in @($sourceJar072,$databaseSource072,(Join-Path $source072 'ownership067.properties'),(Join-Path $source072 'local-play.json'))){
    [pscustomobject]@{path=$p072;sha256=(Get-FileHash -LiteralPath $p072 -Algorithm SHA256).Hash}
})
$protectedRows072 | Export-Csv -LiteralPath (Join-Path $task072 'PROTECTED_INPUTS072.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output "OWN072_INPUTS_CREATED; package=$($packageRows072.Count); protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private."
