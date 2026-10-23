[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
$module073=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo073=Split-Path $module073 -Parent
$task073=Join-Path $repo073 '.phantom-local/task073'
$candidate073=Join-Path ([IO.Path]::GetPathRoot($repo073)) 'PhantomWorld/Earned-Life-073-candidate'
$source073=Join-Path ([IO.Path]::GetPathRoot($repo073)) 'PhantomWorld/Personal-Adventure-067-candidate'
foreach($plain073 in @($module073,$task073,$candidate073,$source073,(Join-Path $task073 'private'),(Join-Path $task073 'reports'),(Join-Path $task073 'native-test/game/config'),(Join-Path $task073 'native-test/game/data/geodata'))){Assert-Plain073 $plain073}
if((Split-Path $module073 -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK073: exact module required.'}
foreach($path073 in @((Join-Path $task073 'ownership073.properties'),$candidate073)){
    if(Test-Path -LiteralPath $path073){throw "TASK073: existing resource is preserved: $path073"}
}
$ports073=@(2106,7777,9153,2253,7953,9253)
if(@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -in $ports073).Count){throw 'TASK073: selected ports occupied; no process action.'}
$protectedProcesses073=@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop | Select-Object ProcessId,CreationDate,ExecutablePath,CommandLine)
$sourceJar073=Join-Path $source073 'libs/GameServer.jar'
if((Get-FileHash -LiteralPath $sourceJar073 -Algorithm SHA256).Hash -ine '06C4AF77760A1F168CC472DF6C98101E1839A3D57879B98CACE93FFCD2A8AF58'){throw 'TASK073: protected067 source JAR differs.'}
$package073=Join-Path $module073 'docs/phantoms/tasks/PHANTOM-EARNED-LIFE-073'
$packageRows073=@(Get-ChildItem -LiteralPath $package073 -File | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{relativePath=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$databaseSource073=Join-Path $module073 'dist/game/config/Database.ini'
Assert-Plain073 $databaseSource073
$database073=[IO.File]::ReadAllText($databaseSource073)
if($database073 -notmatch '(?m)^URL\s*=\s*jdbc:mysql://127\.0\.0\.1:3308/l2jmobiush5\?'){throw 'TASK073: repository loopback operator input differs.'}
$database073=[regex]::Replace($database073,'(?m)^MaximumDatabaseConnections\s*=.*$','MaximumDatabaseConnections = 4')
New-Item -ItemType Directory -Path $task073 -Force | Out-Null
foreach($part073 in @('private','reports','native-test','native-test/game')){New-Item -ItemType Directory -Path (Join-Path $task073 $part073) -Force | Out-Null}
$sid073='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe (Join-Path $task073 'private') /inheritance:r /grant:r ($sid073+':(OI)(CI)F') | Out-Null
if($LASTEXITCODE){throw 'TASK073: private ACL failed.'}
[IO.File]::WriteAllText((Join-Path $task073 'ownership073.properties'),"version=1`nowner=TASK073_EARNED_LIFE`ntaskRoot=$($task073.Replace('\','/'))`ntestSchema=l2jmobiush5_phantom_test_073`nreleaseSchema=l2jmobiush5_phantom_release_073`ninstallationId=$([guid]::NewGuid().ToString())`ncreatedUtc=$([DateTime]::UtcNow.ToString('o'))`n",[Text.UTF8Encoding]::new($false))
foreach($role073 in @('test','release')){
    $settings073=[regex]::Replace($database073,'(?m)^URL\s*=.*$',"URL = jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_$($role073)_073?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8")
    [IO.File]::WriteAllText((Join-Path $task073 "private/Database.$role073.ini"),$settings073,[Text.UTF8Encoding]::new($false))
}
Copy-Item -LiteralPath (Join-Path $module073 'dist/game/config') -Destination (Join-Path $task073 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $module073 'dist/game/data') -Destination (Join-Path $task073 'native-test/game') -Recurse
Copy-Item -LiteralPath (Join-Path $task073 'private/Database.test.ini') -Destination (Join-Path $task073 'native-test/game/config/Database.ini') -Force
$geoRoot073=Join-Path $source073 'game/data/geodata'
Assert-Plain073 $geoRoot073
$geoRows073=@(Get-ChildItem -LiteralPath $geoRoot073 -Filter '*.l2j' -File | Sort-Object Name | ForEach-Object {
    if($_.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'TASK073: physical GeoData required.'}
    $target073=Join-Path $task073 ('native-test/game/data/geodata/'+$_.Name)
    if(Test-Path -LiteralPath $target073){throw 'TASK073: duplicate GeoData target.'}
    $hash073=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $_.FullName -Destination $target073
    if((Get-FileHash -LiteralPath $target073 -Algorithm SHA256).Hash -ine $hash073){throw 'TASK073: physical GeoData copy mismatch.'}
    [pscustomobject]@{kind='GeoData';relativePath=('game/data/geodata/'+$_.Name);source=$_.FullName;bytes=$_.Length;sha256=$hash073}
})
if($geoRows073.Count -ne 203){throw 'TASK073: expected203 source GeoData regions.'}
$geoRows073 | Export-Csv -LiteralPath (Join-Path $task073 'GEODATA_INPUTS073.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$packageRows073 | Export-Csv -LiteralPath (Join-Path $task073 'PACKAGE_INPUTS073.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$protectedProcesses073 | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $task073 'private/protected-processes-preflight.json') -Encoding utf8
$protectedRows073=@(foreach($p073 in @($sourceJar073,$databaseSource073,(Join-Path $source073 'ownership067.properties'),(Join-Path $source073 'local-play.json'))){
    [pscustomobject]@{path=$p073;sha256=(Get-FileHash -LiteralPath $p073 -Algorithm SHA256).Hash}
})
$protectedRows073 | Export-Csv -LiteralPath (Join-Path $task073 'PROTECTED_INPUTS073.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output "OWN073_INPUTS_CREATED; package=$($packageRows073.Count); protected067 JAR exact; Geo203 physical/hash verified; ports free; no DB/process writes; credentials private."
