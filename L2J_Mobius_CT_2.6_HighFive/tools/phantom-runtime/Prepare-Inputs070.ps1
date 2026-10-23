[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module070=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo070=Split-Path $module070 -Parent
$task070=Join-Path $repo070 '.phantom-local/task070'
$candidate070=Join-Path ([IO.Path]::GetPathRoot($repo070)) 'PhantomWorld/Economy-QoL-070-candidate'
$source070=Join-Path ([IO.Path]::GetPathRoot($repo070)) 'PhantomWorld/Personal-Adventure-067-candidate'
if((Split-Path $module070 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK070: exact module required.'}
foreach($path070 in @((Join-Path $task070 'ownership070.properties'),$candidate070)){
    if(Test-Path -LiteralPath $path070){throw "TASK070: existing resource is preserved: $path070"}
}
$ports070=@(2106,7777,9150,2250,7950,9250)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports070).Count){throw 'TASK070: selected ports occupied; no process action.'}
$protectedProcesses070=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar070=Join-Path $source070 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar070 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK070: protected067 source JAR differs.'}
$package070=Join-Path $module070 'docs/phantoms/tasks/PHANTOM-ECONOMY-QOL-070'
$packageRows070=@(Get-ChildItem -LiteralPath $package070 -File | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{relativePath=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$databaseSource070=Join-Path $repo070 '.phantom-local/task067/private/Database.test.ini'
$database070=[IO.File]::ReadAllText($databaseSource070)
if($database070 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5_personal_test_067\?useSSL=false' -or $database070 -notmatch '(?m)^MaximumDatabaseConnections\s*=\s*4\s*$'){throw 'TASK070: bounded protected database input differs.'}
New-Item -ItemType Directory -Path $task070 -Force | Out-Null
foreach($part070 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task070 $part070) -Force | Out-Null}
$sid070='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task070 'private') /inheritance:r /grant:r ($sid070+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK070: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task070 'ownership070.properties'),"version=1`nowner=TASK070_ECONOMY_QOL`ntaskRoot=$($task070.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_070`nreleaseSchema=l2jmobiush5_phantom_release_070`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role070 in @('test','release')){
    $settings070=$database070.Replace('l2jmobiush5_personal_test_067',"l2jmobiush5_phantom_$($role070)_070")
    [IO.File]::WriteAllText((Join-Path $task070 "private/Database.$role070.ini"),$settings070,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module070 'dist/game/config') -Destination (Join-Path $task070 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module070 'dist/game/data') -Destination (Join-Path $task070 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task070 'private/Database.test.ini') -Destination (Join-Path $task070 'native-test/game/config/Database.ini') -Force
$geoRoot070=Join-Path $source070 'game/data/geodata'
$geoRows070=@(Get-ChildItem -LiteralPath $geoRoot070 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK070: physical GeoData required.'}
    $target070=Join-Path $task070 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target070){throw 'TASK070: duplicate GeoData target.'}
    $hash070=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target070
    if((Get-FileHash -LiteralPath $target070 -Algorithm SHA256).Hash -ine $hash070){throw 'TASK070: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash070}
})
if($geoRows070.Count -ne 203){throw 'TASK070: expected203 source GeoData regions.'}
$geoRows070 | Export-Csv -LiteralPath (Join-Path $task070 'GEODATA_INPUTS070.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows070 | Export-Csv -LiteralPath (Join-Path $task070 'PACKAGE_INPUTS070.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses070 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task070 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows070=@(foreach($p070 in @($sourceJar070,$databaseSource070,(Join-Path $source070 'ownership067.properties'),(Join-Path $source070 'local-play.json'))){
    [pscustomobject]@{path=$p070;sha256=(Get-FileHash -LiteralPath $p070 -Algorithm SHA256).Hash}
})
$protectedRows070 | Export-Csv -LiteralPath (Join-Path $task070 'PROTECTED_INPUTS070.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output "OWN070_INPUTS_CREATED; package=$($packageRows070.Count); protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private."
