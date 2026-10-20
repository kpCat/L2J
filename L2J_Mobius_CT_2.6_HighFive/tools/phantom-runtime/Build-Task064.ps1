[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [ValidateSet('build','build-final')][string]$Build='build')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$module064=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$repo064=Split-Path $module064 -Parent;$relative064=Split-Path $module064 -Leaf
$task064=Join-Path $module064 '.phantom-local/task064';$build064=Join-Path $task064 $Build
$archive064=Join-Path $task064 ($Label+'.zip');$source064=Join-Path $task064 $Label
$receipt064=Join-Path $build064 'BUILD_INFO064.json'
if((Test-Path -LiteralPath $archive064) -or (Test-Path -LiteralPath $source064)){throw 'Source evidence exists; no overwrite.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.Contains('-Dphantom.task064.control=true') -or $_.CommandLine.Contains('-Dphantom.task064.manifest='))}).Count){throw 'Own native runtime is active; builds are sequential.'}
Push-Location $repo064
try{
    $branch064=& git symbolic-ref --short HEAD
    if($LASTEXITCODE -ne 0 -or $branch064 -cne 'feature/phantom-world'){throw 'TASK064 branch differs.'}
    $commit064=& git rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $commit064 -cnotmatch '^[0-9a-f]{40}$'){throw 'Cannot bind source commit.'}
    $dirt064=@(& git status --porcelain --untracked-files=all -- $relative064)
    if($LASTEXITCODE -ne 0 -or $dirt064.Count){throw 'Commit exact module changes before clean source build.'}
    & git archive --format=zip "--output=$archive064" $commit064 -- "$relative064/build.xml" "$relative064/java" "$relative064/test" "$relative064/dist" "$relative064/tools"
    if($LASTEXITCODE -ne 0){throw 'Committed source archive failed.'}
}finally{Pop-Location}
Expand-Archive -LiteralPath $archive064 -DestinationPath $source064
if(Test-Path -LiteralPath $receipt064){
    Copy-Item -LiteralPath $receipt064 -Destination (Join-Path $task064 ($Label+'-previous-build-info.json'))
    Remove-Item -LiteralPath $receipt064
}
$log064=Join-Path $task064 ($Label+'-build.log')
& ant -f (Join-Path $source064 "$relative064/build.xml") "-Dbuild=$build064" jar compile-tests *> $log064
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath $log064 -Tail 30;throw 'Clean committed Ant build failed; no receipt.'}
$lines064=@(foreach($tree064 in @('bin','resources')){
    $root064=Join-Path $build064 "phantom-test/$tree064"
    foreach($file064 in Get-ChildItem -LiteralPath $root064 -Recurse -File | Sort-Object FullName){
        $tree064+'/'+$file064.FullName.Substring($root064.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file064.FullName).Hash
    }
})
$sha064=[Security.Cryptography.SHA256]::Create()
try{$tests064=([BitConverter]::ToString($sha064.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines064 -join "`n"))))).Replace('-','')}finally{$sha064.Dispose()}
$info064=[ordered]@{format=1;owner='TASK064_CAMPAIGN';sourceState='COMMITTED';codeCommit=$commit064;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive064).Hash;buildRoot=$build064;gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build064 'dist/libs/GameServer.jar')).Hash;loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build064 'dist/libs/LoginServer.jar')).Hash;testTreeSha256=$tests064;builtUtc=[DateTime]::UtcNow.ToString('o')}
[IO.File]::WriteAllText($receipt064,($info064 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output "TASK064_CLEAN_BUILD commit=$commit064; gameJar=$($info064.gameJarSha256); receipt=$receipt064"
