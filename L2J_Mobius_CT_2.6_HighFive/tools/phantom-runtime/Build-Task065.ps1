[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [ValidateSet('build','build-final')][string]$Build='build')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain065([string]$Path){
    $absolute065=[IO.Path]::GetFullPath($Path)
    $walk065=[IO.Path]::GetPathRoot($absolute065)
    foreach($part065 in $absolute065.Substring($walk065.Length).Split('\')){
        if(!$part065){continue}
        $walk065=Join-Path $walk065 $part065
        if((Test-Path -LiteralPath $walk065) -and ((Get-Item -LiteralPath $walk065 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK065: plain physical paths required.'}
    }
}
function Get-TestTreeHash065([string]$Root){
    $lines065=@(foreach($tree065 in @('bin','resources')){
        $treeRoot065=Join-Path $Root "phantom-test/$tree065"
        if(!(Test-Path -LiteralPath $treeRoot065 -PathType Container)){throw 'TASK065: compiled test tree missing.'}
        foreach($file065 in Get-ChildItem -LiteralPath $treeRoot065 -Recurse -File | Sort-Object FullName){
            Assert-Plain065 $file065.FullName
            $tree065+'/'+$file065.FullName.Substring($treeRoot065.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file065.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines065.Count){throw 'TASK065: empty compiled test tree.'}
    $sha065=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha065.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines065 -join "`n"))))).Replace('-','')}
    finally{$sha065.Dispose()}
}

$module065=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo065=Split-Path $module065 -Parent
$relative065=Split-Path $module065 -Leaf
if($relative065 -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK065: High Five module required.'}
$task065=Join-Path $module065 '.phantom-local/task065'
$build065=Join-Path $task065 $Build
$archive065=Join-Path $task065 ($Label+'.zip')
$source065=Join-Path $task065 $Label
$receipt065=Join-Path $build065 'BUILD_INFO065.json'
if($Label -cin @('build','build-final','runtime','private')){throw 'TASK065: source label collides with an owned runtime/build path.'}
foreach($path065 in @($module065,$task065,$build065,$archive065,$source065)){Assert-Plain065 $path065}
if((Test-Path -LiteralPath $archive065) -or (Test-Path -LiteralPath $source065)){throw 'TASK065: source evidence exists; no overwrite.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.Contains('-Dphantom.task065.control=true') -or $_.CommandLine.Contains('-Dphantom.task065.manifest='))}).Count){throw 'TASK065: own native runtime is active; builds are sequential.'}

New-Item -ItemType Directory -Path $task065 -Force | Out-Null
if(Test-Path -LiteralPath $build065){
    $ownership065=Join-Path $build065 'BUILD_OWNERSHIP065.properties'
    Assert-Plain065 $ownership065
    if(!(Test-Path -LiteralPath $ownership065) -or [IO.File]::ReadAllText($ownership065) -cne "version=1`nowner=TASK065_LIVING_PRESENCE`nbuildRoot=$($build065.Replace('\','/'))`n"){throw 'TASK065: existing build directory has no exact ownership.'}
}else{
    New-Item -ItemType Directory -Path $build065 | Out-Null
    [IO.File]::WriteAllText((Join-Path $build065 'BUILD_OWNERSHIP065.properties'),"version=1`nowner=TASK065_LIVING_PRESENCE`nbuildRoot=$($build065.Replace('\','/'))`n",[Text.UTF8Encoding]::new($false))
}

Push-Location $repo065
try{
    $branch065=& git symbolic-ref --short HEAD
    if($LASTEXITCODE -ne 0 -or $branch065 -cne 'feature/phantom-world'){throw 'TASK065: branch differs.'}
    $commit065=& git rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $commit065 -cnotmatch '^[0-9a-f]{40}$'){throw 'TASK065: cannot bind source commit.'}
    $dirt065=@(& git status --porcelain --untracked-files=all -- $relative065)
    if($LASTEXITCODE -ne 0 -or $dirt065.Count){throw 'TASK065: commit exact module changes before clean source build.'}
    & git archive --format=zip "--output=$archive065" $commit065 -- "$relative065/build.xml" "$relative065/java" "$relative065/test" "$relative065/dist" "$relative065/tools"
    if($LASTEXITCODE -ne 0){throw 'TASK065: committed source archive failed.'}
}finally{Pop-Location}

Expand-Archive -LiteralPath $archive065 -DestinationPath $source065
$committedModule065=Join-Path $source065 $relative065
if(Test-Path -LiteralPath $receipt065){
    $previous065=Join-Path $task065 ($Label+'-previous-build-info.json')
    if(Test-Path -LiteralPath $previous065){throw 'TASK065: previous build evidence exists; no overwrite.'}
    Copy-Item -LiteralPath $receipt065 -Destination $previous065
    Remove-Item -LiteralPath $receipt065
}
$log065=Join-Path $task065 ($Label+'-build.log')
if(Test-Path -LiteralPath $log065){throw 'TASK065: build log exists; no overwrite.'}
& ant -f (Join-Path $committedModule065 'build.xml') "-Dbuild=$build065" jar compile-tests *> $log065
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath $log065 -Tail 30;throw 'TASK065: clean committed Ant build failed; no receipt.'}
$info065=[ordered]@{
    format=1;owner='TASK065_LIVING_PRESENCE';sourceState='COMMITTED';codeCommit=$commit065
    sourceModuleRoot=$module065;committedModuleRoot=$committedModule065
    sourceArchivePath=$archive065;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive065 -Algorithm SHA256).Hash
    buildRoot=$build065
    gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build065 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash
    loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build065 'dist/libs/LoginServer.jar') -Algorithm SHA256).Hash
    testTreeSha256=(Get-TestTreeHash065 $build065);builtUtc=[DateTime]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($receipt065,($info065 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output "TASK065_CLEAN_BUILD commit=$commit065; gameJar=$($info065.gameJarSha256); receipt=$receipt065"
