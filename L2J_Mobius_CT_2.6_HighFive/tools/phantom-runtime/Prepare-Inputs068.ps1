[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module068=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo068=Split-Path $module068 -Parent
$task068=Join-Path $repo068 '.phantom-local/task068'
$candidate068=Join-Path ([IO.Path]::GetPathRoot($repo068)) 'PhantomWorld/Living-Economy-068-candidate'
$source068=Join-Path ([IO.Path]::GetPathRoot($repo068)) 'PhantomWorld/Personal-Adventure-067-candidate'
if((Split-Path $module068 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK068: exact module required.'}
foreach($path068 in @($task068,$candidate068)){
    if(Test-Path -LiteralPath $path068){throw "TASK068: existing resource is preserved: $path068"}
}
$ports068=@(2149,7849,9149,2249,7949,9249)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports068).Count){throw 'TASK068: selected ports occupied; no process action.'}
$protectedProcesses068=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar068=Join-Path $source068 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar068 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK068: protected067 source JAR differs.'}
$package068=Join-Path $module068 'docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068'
$packageRows068=@(foreach($line068 in Get-Content -LiteralPath (Join-Path $package068 'PACKAGE_SHA256.txt')){
    if($line068 -cnotmatch '^([0-9a-f]{64})  ([A-Z_]+\.md)$'){throw 'TASK068: invalid package manifest.'}
    $expected068=$Matches[1];$relative068=$Matches[2]
    $actual068=(Get-FileHash -LiteralPath (Join-Path $package068 $relative068) -Algorithm SHA256).Hash
    if($actual068 -ine $expected068){throw "TASK068: package mismatch: $relative068"}
    [pscustomobject]@{relativePath=$relative068;sha256=$actual068}
})
if($packageRows068.Count -ne 6){throw 'TASK068: exact six package inputs required.'}
$databaseSource068=Join-Path $repo068 '.phantom-local/task067/private/Database.test.ini'
$database068=[IO.File]::ReadAllText($databaseSource068)
if($database068 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5_personal_test_067\?useSSL=false' -or $database068 -notmatch '(?m)^MaximumDatabaseConnections\s*=\s*4\s*$'){throw 'TASK068: bounded protected database input differs.'}
New-Item -ItemType Directory -Path $task068 | Out-Null
foreach($part068 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task068 $part068) | Out-Null}
$sid068='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task068 'private') /inheritance:r /grant:r ($sid068+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK068: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task068 'ownership068.properties'),"version=1`nowner=TASK068_LIVING_ECONOMY`ntaskRoot=$($task068.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_068`nreleaseSchema=l2jmobiush5_phantom_release_068`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role068 in @('test','release')){
    $settings068=$database068.Replace('l2jmobiush5_personal_test_067',"l2jmobiush5_phantom_$($role068)_068")
    [IO.File]::WriteAllText((Join-Path $task068 "private/Database.$role068.ini"),$settings068,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module068 'dist/game/config') -Destination (Join-Path $task068 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module068 'dist/game/data') -Destination (Join-Path $task068 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task068 'private/Database.test.ini') -Destination (Join-Path $task068 'native-test/game/config/Database.ini') -Force
$geoRoot068=Join-Path $source068 'game/data/geodata'
$geoRows068=@(Get-ChildItem -LiteralPath $geoRoot068 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK068: physical GeoData required.'}
    $target068=Join-Path $task068 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target068){throw 'TASK068: duplicate GeoData target.'}
    $hash068=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target068
    if((Get-FileHash -LiteralPath $target068 -Algorithm SHA256).Hash -ine $hash068){throw 'TASK068: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash068}
})
if($geoRows068.Count -ne 203){throw 'TASK068: expected203 source GeoData regions.'}
$geoRows068 | Export-Csv -LiteralPath (Join-Path $task068 'GEODATA_INPUTS068.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows068 | Export-Csv -LiteralPath (Join-Path $task068 'PACKAGE_INPUTS068.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses068 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task068 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows068=@(foreach($p068 in @($sourceJar068,$databaseSource068,(Join-Path $source068 'ownership067.properties'),(Join-Path $source068 'local-play.json'))){
    [pscustomobject]@{path=$p068;sha256=(Get-FileHash -LiteralPath $p068 -Algorithm SHA256).Hash}
})
$protectedRows068 | Export-Csv -LiteralPath (Join-Path $task068 'PROTECTED_INPUTS068.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output 'OWN068_INPUTS_CREATED; package6/6; protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private.'
