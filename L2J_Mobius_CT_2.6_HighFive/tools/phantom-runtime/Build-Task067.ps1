[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [ValidateSet('build','build-final')][string]$Build='build')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

function Assert-Plain067([string]$Path){
    $absolute067=[IO.Path]::GetFullPath($Path)
    $walk067=[IO.Path]::GetPathRoot($absolute067)
    foreach($part067 in $absolute067.Substring($walk067.Length).Split('\')){
        if(!$part067){continue}
        $walk067=Join-Path $walk067 $part067
        if((Test-Path -LiteralPath $walk067) -and ((Get-Item -LiteralPath $walk067 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK067: plain physical paths required.'}
    }
}
function Get-TestTreeHash067([string]$Root){
    $lines067=@(foreach($tree067 in @('bin','resources')){
        $treeRoot067=Join-Path $Root "phantom-test/$tree067"
        if(!(Test-Path -LiteralPath $treeRoot067 -PathType Container)){throw 'TASK067: compiled test tree missing.'}
        foreach($file067 in Get-ChildItem -LiteralPath $treeRoot067 -Recurse -File | Sort-Object FullName){
            Assert-Plain067 $file067.FullName
            $tree067+'/'+$file067.FullName.Substring($treeRoot067.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file067.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines067.Count){throw 'TASK067: empty compiled test tree.'}
    $sha067=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha067.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines067 -join "`n"))))).Replace('-','')}
    finally{$sha067.Dispose()}
}
function Get-CommittedTreeHash067([string]$Root){
    $lines067=@(foreach($entry067 in @('build.xml','java','test','dist','tools')){
        $entryRoot067=Join-Path $Root $entry067
        foreach($file067 in Get-ChildItem -LiteralPath $entryRoot067 -Recurse -File | Sort-Object FullName){
            Assert-Plain067 $file067.FullName
            $relative067=$file067.FullName.Substring($Root.Length+1).Replace('\','/')
            if($relative067 -cin @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar')){continue}
            $relative067+':'+(Get-FileHash -LiteralPath $file067.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines067.Count){throw 'TASK067: empty committed source tree.'}
    $sha067=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha067.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines067 -join "`n"))))).Replace('-','')}
    finally{$sha067.Dispose()}
}

$module067=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo067=Split-Path $module067 -Parent
$relative067=Split-Path $module067 -Leaf
if($relative067 -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK067: High Five module required.'}
$task067=Join-Path (Split-Path $module067 -Parent) '.phantom-local/task067'
$build067=Join-Path $task067 ($Build+'-'+$Label)
$archive067=Join-Path $task067 ($Label+'.zip')
$source067=Join-Path $task067 $Label
$receipt067=Join-Path $build067 'BUILD_INFO067.json'
if($Label -cin @('build','build-final','runtime','private','native-test')){throw 'TASK067: source label collides with an owned runtime/build path.'}
foreach($path067 in @($module067,$task067,$build067,$archive067,$source067)){Assert-Plain067 $path067}
if((Test-Path -LiteralPath $archive067) -or (Test-Path -LiteralPath $source067) -or (Test-Path -LiteralPath $build067)){throw 'TASK067: source/build evidence exists; choose a distinct label; no overwrite.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and ($_.CommandLine.Contains('-Dphantom.task067.control=true') -or $_.CommandLine.Contains('-Dphantom.task067.manifest='))}).Count){throw 'TASK067: own native runtime is active; builds are sequential.'}

New-Item -ItemType Directory -Path $task067 -Force | Out-Null
New-Item -ItemType Directory -Path $build067 | Out-Null
[IO.File]::WriteAllText((Join-Path $build067 'BUILD_OWNERSHIP067.properties'),"version=1`nowner=TASK067_PERSONAL_ADVENTURE`nbuildRoot=$($build067.Replace('\','/'))`n",[Text.UTF8Encoding]::new($false))

Push-Location $repo067
try{
    $branch067=& git symbolic-ref --short HEAD
    if($LASTEXITCODE -ne 0 -or $branch067 -cne 'feature/phantom-world'){throw 'TASK067: branch differs.'}
    $commit067=& git rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $commit067 -cnotmatch '^[0-9a-f]{40}$'){throw 'TASK067: cannot bind source commit.'}
    $dirt067=@(& git status --porcelain --untracked-files=all -- $relative067)
    if($LASTEXITCODE -ne 0 -or $dirt067.Count){throw 'TASK067: commit exact module changes before clean source build.'}
    & git archive --format=zip "--output=$archive067" $commit067 -- "$relative067/build.xml" "$relative067/java" "$relative067/test" "$relative067/dist" "$relative067/tools"
    if($LASTEXITCODE -ne 0){throw 'TASK067: committed source archive failed.'}
}finally{Pop-Location}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($archive067,$source067)
$committedModule067=Join-Path $source067 $relative067
$log067=Join-Path $task067 ($Label+'-build.log')
if(Test-Path -LiteralPath $log067){throw 'TASK067: build log exists; no overwrite.'}
& ant -f (Join-Path $committedModule067 'build.xml') "-Dbuild=$build067" jar compile-tests *> $log067
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath $log067 -Tail 30;throw 'TASK067: clean committed Ant build failed; no receipt.'}
$info067=[ordered]@{
    format=1;owner='TASK067_PERSONAL_ADVENTURE';sourceState='COMMITTED';codeCommit=$commit067
    sourceModuleRoot=$module067;committedModuleRoot=$committedModule067
    sourceArchivePath=$archive067;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive067 -Algorithm SHA256).Hash
    committedTreeSha256=(Get-CommittedTreeHash067 $committedModule067)
    buildRoot=$build067
    gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build067 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash
    loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build067 'dist/libs/LoginServer.jar') -Algorithm SHA256).Hash
    testTreeSha256=(Get-TestTreeHash067 $build067);builtUtc=[DateTime]::UtcNow.ToString('o')
}
[IO.File]::WriteAllText($receipt067,($info067 | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Write-Output "TASK067_CLEAN_BUILD commit=$commit067; gameJar=$($info067.gameJarSha256); receipt=$receipt067"
