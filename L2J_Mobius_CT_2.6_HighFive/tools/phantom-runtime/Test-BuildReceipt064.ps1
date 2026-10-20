[CmdletBinding()]
param([Parameter(Mandatory)][string]$ScratchRoot)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
if(Test-Path -LiteralPath $ScratchRoot){throw 'Evidence exists; no overwrite.'}
$module064=Join-Path $ScratchRoot 'module';$tools064=Join-Path $module064 'tools/phantom-runtime'
$build064=Join-Path $module064 '.phantom-local/task064/build'
foreach($folder064 in @($tools064,(Join-Path $build064 'dist/libs'),(Join-Path $build064 'phantom-test/bin'),(Join-Path $build064 'phantom-test/resources'))){New-Item -ItemType Directory -Path $folder064 -Force | Out-Null}
$runner064=Join-Path $tools064 'Invoke-Task064Test.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Invoke-Task064Test.ps1') -Destination $runner064
$jar064=Join-Path $build064 'dist/libs/GameServer.jar';$class064=Join-Path $build064 'phantom-test/bin/Fixture.class'
[IO.File]::WriteAllText($jar064,'prepared fake jar, never executed')
[IO.File]::WriteAllText($class064,'prepared fake bytecode, never executed')
$sha064=[Security.Cryptography.SHA256]::Create()
$line064='bin/Fixture.class:'+(Get-FileHash -LiteralPath $class064).Hash
try{$tests064=([BitConverter]::ToString($sha064.ComputeHash([Text.Encoding]::UTF8.GetBytes($line064)))).Replace('-','')}finally{$sha064.Dispose()}
$receipt064=Join-Path $build064 'BUILD_INFO064.json'
$info064=[ordered]@{format=1;owner='TASK064_CAMPAIGN';sourceState='COMMITTED';codeCommit=('b'*40);sourceArchiveSha256=('C'*64);buildRoot=$build064;gameJarSha256=(Get-FileHash -LiteralPath $jar064).Hash;testTreeSha256=$tests064}
[IO.File]::WriteAllText($receipt064,($info064 | ConvertTo-Json))
& $runner064 -Mode harness-unit -Seed 64 -Label validate -ValidateOnly
Write-Output 'PASS receipt064.exact-build-no-runtime-attribution'
function Reject064{
    $rejected064=$false
    try{& $runner064 -Mode harness-unit -Seed 64 -Label validate -ValidateOnly | Out-Null}catch{$rejected064=$true}
    if(!$rejected064){throw 'Mismatched build evidence accepted.'}
}
[IO.File]::AppendAllText($jar064,'tampered');Reject064
[IO.File]::WriteAllText($jar064,'prepared fake jar, never executed')
[IO.File]::AppendAllText($class064,'tampered');Reject064
Remove-Item -LiteralPath $receipt064;Reject064
Write-Output 'PASS receipt064.changed-jar-bytecode-missing-receipt'
