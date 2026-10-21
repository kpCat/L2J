[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [ValidateSet('build','build-final')][string]$Build='build')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain066([string]$Path){
    $absolute066=[IO.Path]::GetFullPath($Path)
    $walk066=[IO.Path]::GetPathRoot($absolute066)
    foreach($part066 in $absolute066.Substring($walk066.Length).Split('\')){
        if(!$part066){continue}
        $walk066=Join-Path $walk066 $part066
        if((Test-Path -LiteralPath $walk066) -and ((Get-Item -LiteralPath $walk066 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK066: plain physical paths required.'}
    }
}
function Get-TestTreeHash066([string]$Root){
    $lines066=@(foreach($tree066 in @('bin','resources')){
        $treeRoot066=Join-Path $Root "phantom-test/$tree066"
        if(!(Test-Path -LiteralPath $treeRoot066 -PathType Container)){throw 'TASK066: compiled test tree missing.'}
        foreach($file066 in Get-ChildItem -LiteralPath $treeRoot066 -Recurse -File | Sort-Object FullName){
            Assert-Plain066 $file066.FullName
            $tree066+'/'+$file066.FullName.Substring($treeRoot066.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file066.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines066.Count){throw 'TASK066: empty compiled test tree.'}
    $sha066=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha066.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines066 -join "`n"))))).Replace('-','')}
    finally{$sha066.Dispose()}
}

$module066=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo066=Split-Path $module066 -Parent
$relative066=Split-Path $module066 -Leaf
if($relative066 -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK066: High Five module required.'}
$task066r2=Join-Path (Split-Path $module066 -Parent) '.phantom-local/task066r2'
$build066=Join-Path $task066r2 $Build
$archive066=Join-Path $task066r2 ($Label+'.zip')
$source066=Join-Path $task066r2 $Label
$receipt066=Join-Path $build066 'BUILD_INFO066R2.json'
if($Label -cin @('build','build-final','runtime','private')){throw 'TASK066: source label collides with an owned runtime/build path.'}
foreach($path066 in @($module066,$task066r2,$build066,$archive066,$source066)){Assert-Plain066 $path066}
if((Test-Path -LiteralPath $archive066) -or (Test-Path -LiteralPath $source066)){throw 'TASK066: source evidence exists; no overwrite.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.Contains('-Dphantom.task066r2.control=true') -or $_.CommandLine.Contains('-Dphantom.task066r2.manifest='))}).Count){throw 'TASK066: own native runtime is active; builds are sequential.'}

New-Item -ItemType Directory -Path $task066r2 -Force | Out-Null
if(Test-Path -LiteralPath $build066){
    $ownership066r2=Join-Path $build066 'BUILD_OWNERSHIP066.properties'
    Assert-Plain066 $ownership066r2
    if(!(Test-Path -LiteralPath $ownership066r2) -or [IO.File]::ReadAllText($ownership066r2) -cne "version=1`nowner=TASK066R2_FARM_ECONOMY`nbuildRoot=$($build066.Replace('\','/'))`n"){throw 'TASK066: existing build directory has no exact ownership.'}
}else{
    New-Item -ItemType Directory -Path $build066 | Out-Null
    [IO.File]::WriteAllText((Join-Path $build066 'BUILD_OWNERSHIP066.properties'),"version=1`nowner=TASK066R2_FARM_ECONOMY`nbuildRoot=$($build066.Replace('\','/'))`n",[Text.UTF8Encoding]::new($false))
}

Push-Location $repo066
try{
    $branch066=& git symbolic-ref --short HEAD
    if($LASTEXITCODE -ne 0 -or $branch066 -cne 'feature/phantom-world'){throw 'TASK066: branch differs.'}
    $commit066=& git rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $commit066 -cnotmatch '^[0-9a-f]{40}$'){throw 'TASK066: cannot bind source commit.'}
    $dirt066=@(& git status --porcelain --untracked-files=all -- $relative066)
    if($LASTEXITCODE -ne 0 -or $dirt066.Count){throw 'TASK066: commit exact module changes before clean source build.'}
    & git archive --format=zip "--output=$archive066" $commit066 -- "$relative066/build.xml" "$relative066/java" "$relative066/test" "$relative066/dist" "$relative066/tools"
    if($LASTEXITCODE -ne 0){throw 'TASK066: committed source archive failed.'}
}finally{Pop-Location}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($archive066,$source066)
$committedModule066=Join-Path $source066 $relative066
if(Test-Path -LiteralPath $receipt066){
    $previous066=Join-Path $task066r2 ($Label+'-previous-build-info.json')
    if(Test-Path -LiteralPath $previous066){throw 'TASK066: previous build evidence exists; no overwrite.'}
    Copy-Item -LiteralPath $receipt066 -Destination $previous066
    Remove-Item -LiteralPath $receipt066
}
$log066=Join-Path $task066r2 ($Label+'-build.log')
if(Test-Path -LiteralPath $log066){throw 'TASK066: build log exists; no overwrite.'}
& ant -f (Join-Path $committedModule066 'build.xml') "-Dbuild=$build066" jar compile-tests *> $log066
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath $log066 -Tail 30;throw 'TASK066: clean committed Ant build failed; no receipt.'}
$info066=[ordered]@{
    format=1;owner='TASK066R2_FARM_ECONOMY';sourceState='COMMITTED';codeCommit=$commit066
    sourceModuleRoot=$module066;committedModuleRoot=$committedModule066
    sourceArchivePath=$archive066;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive066 -Algorithm SHA256).Hash
    buildRoot=$build066
    gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build066 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash
    loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build066 'dist/libs/LoginServer.jar') -Algorithm SHA256).Hash
    testTreeSha256=(Get-TestTreeHash066 $build066);builtUtc=[DateTime]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($receipt066,($info066 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output "TASK066_CLEAN_BUILD commit=$commit066; gameJar=$($info066.gameJarSha256); receipt=$receipt066"
