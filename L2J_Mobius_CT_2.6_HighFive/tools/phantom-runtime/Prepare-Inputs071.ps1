[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task071Common.ps1')
$module071=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo071=Split-Path $module071 -Parent
$task071=Join-Path $repo071 '.phantom-local/task071'
$candidate071=Join-Path ([IO.Path]::GetPathRoot($repo071)) 'PhantomWorld/Living-Combat-071-candidate'
$source071=Join-Path ([IO.Path]::GetPathRoot($repo071)) 'PhantomWorld/Personal-Adventure-067-candidate'
foreach($plain071 in @($module071,$task071,$candidate071,$source071,(Join-Path $task071 'private'),(Join-Path $task071 'reports'),(Join-Path $task071 'native-test/game/config'),(Join-Path $task071 'native-test/game/data/geodata'))){Assert-Plain071 $plain071}
if((Split-Path $module071 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK071: exact module required.'}
foreach($path071 in @((Join-Path $task071 'ownership071.properties'),$candidate071)){
    if(Test-Path -LiteralPath $path071){throw "TASK071: existing resource is preserved: $path071"}
}
$ports071=@(2106,7777,9151,2251,7951,9251)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports071).Count){throw 'TASK071: selected ports occupied; no process action.'}
$protectedProcesses071=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar071=Join-Path $source071 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar071 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK071: protected067 source JAR differs.'}
$package071=Join-Path $module071 'docs/phantoms/tasks/PHANTOM-LIVING-COMBAT-071'
$packageRows071=@(Get-ChildItem -LiteralPath $package071 -File | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{relativePath=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$databaseSource071=Join-Path $repo071 '.phantom-local/task067/private/Database.test.ini'
Assert-Plain071 $databaseSource071
$database071=[IO.File]::ReadAllText($databaseSource071)
if($database071 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5_personal_test_067\?useSSL=false' -or $database071 -notmatch '(?m)^MaximumDatabaseConnections\s*=\s*4\s*$'){throw 'TASK071: bounded protected database input differs.'}
New-Item -ItemType Directory -Path $task071 -Force | Out-Null
foreach($part071 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task071 $part071) -Force | Out-Null}
$sid071='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task071 'private') /inheritance:r /grant:r ($sid071+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK071: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task071 'ownership071.properties'),"version=1`nowner=TASK071_LIVING_COMBAT`ntaskRoot=$($task071.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_071`nreleaseSchema=l2jmobiush5_phantom_release_071`ninstallationId=$([guid]::NewGuid().ToString())`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role071 in @('test','release')){
    $settings071=$database071.Replace('l2jmobiush5_personal_test_067',"l2jmobiush5_phantom_$($role071)_071")
    [IO.File]::WriteAllText((Join-Path $task071 "private/Database.$role071.ini"),$settings071,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module071 'dist/game/config') -Destination (Join-Path $task071 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module071 'dist/game/data') -Destination (Join-Path $task071 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task071 'private/Database.test.ini') -Destination (Join-Path $task071 'native-test/game/config/Database.ini') -Force
$geoRoot071=Join-Path $source071 'game/data/geodata'
Assert-Plain071 $geoRoot071
$geoRows071=@(Get-ChildItem -LiteralPath $geoRoot071 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK071: physical GeoData required.'}
    $target071=Join-Path $task071 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target071){throw 'TASK071: duplicate GeoData target.'}
    $hash071=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target071
    if((Get-FileHash -LiteralPath $target071 -Algorithm SHA256).Hash -ine $hash071){throw 'TASK071: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash071}
})
if($geoRows071.Count -ne 203){throw 'TASK071: expected203 source GeoData regions.'}
$geoRows071 | Export-Csv -LiteralPath (Join-Path $task071 'GEODATA_INPUTS071.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows071 | Export-Csv -LiteralPath (Join-Path $task071 'PACKAGE_INPUTS071.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses071 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task071 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows071=@(foreach($p071 in @($sourceJar071,$databaseSource071,(Join-Path $source071 'ownership067.properties'),(Join-Path $source071 'local-play.json'))){
    [pscustomobject]@{path=$p071;sha256=(Get-FileHash -LiteralPath $p071 -Algorithm SHA256).Hash}
})
$protectedRows071 | Export-Csv -LiteralPath (Join-Path $task071 'PROTECTED_INPUTS071.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output "OWN071_INPUTS_CREATED; package=$($packageRows071.Count); protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private."
