[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$Label,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task070Common.ps1')

$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo=Split-Path $module -Parent
$binding=Get-Binding070 $module 'Release'
$task=$binding.task
$relative=Split-Path $module -Leaf
$build=Join-Path $task ('build-final-'+$Label)
$archive=Join-Path $task ($Label+'.zip')
$source=Join-Path $task ('source-'+$Label)
$log=Join-Path $task ($Label+'-build.log')
foreach($path in @($module,$task,$build,$archive,$source,$log)){Assert-Plain070 $path}
foreach($path in @($build,$archive,$source,$log)){if(Test-Path -LiteralPath $path){throw 'TASK070: evidence exists; use a distinct label, no overwrite.'}}
if((Read-Field070 (Join-Path $task 'ownership070.properties') 'owner') -cne $binding.owner){throw 'TASK070: task ownership differs.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine -match '-Dphantom\.task070\.(control|native)=true'}).Count){throw 'TASK070: native runtimes and builds must be sequential.'}

Push-Location $repo
try{
    $branch=& git symbolic-ref --short HEAD
    if($LASTEXITCODE -or $branch -cne 'feature/phantom-world'){throw 'TASK070: feature branch required.'}
    $head=& git rev-parse HEAD
    if($LASTEXITCODE -or $head -cne $SourceCommit){throw 'TASK070: requested codeSHA must be exact current HEAD.'}
    $dirt=@(& git status --porcelain --untracked-files=all -- $relative)
    if($LASTEXITCODE){throw 'TASK070: source inventory failed.'}
    $readOnlyPackage=Import-Csv -LiteralPath (Join-Path $task 'PROTECTED_UNTRACKED070.tsv') -Delimiter "`t"
    foreach($row in $dirt){
        $path=$row.Substring(3)
        $absolute=[IO.Path]::GetFullPath((Join-Path $repo $path))
        $expected=@($readOnlyPackage | Where-Object path -eq $absolute)
        if(!$row.StartsWith('?? ') -or $expected.Count -ne 1 -or (Get-FileHash -LiteralPath $absolute -Algorithm SHA256).Hash -ine $expected[0].sha256){throw 'TASK070: commit exact module changes; foreign dirt is preserved.'}
    }
    New-Item -ItemType Directory -Path $build | Out-Null
    Write-Text070 (Join-Path $build 'BUILD_OWNERSHIP070.properties') "version=1`nowner=$($binding.owner)`nbuildRoot=$($build.Replace('\','/'))`n"
    & git archive --format=zip "--output=$archive" $SourceCommit -- "$relative/build.xml" "$relative/java" "$relative/test" "$relative/dist" "$relative/tools"
    if($LASTEXITCODE){throw 'TASK070: committed source archive failed.'}
}finally{Pop-Location}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($archive,$source)
$committed=Join-Path $source $relative
$price070=Join-Path $committed 'dist/game/data/economy070/pricing070.tsv'
$pinned070=[IO.File]::ReadAllText((Join-Path (Split-Path $price070 -Parent) 'pricing070.sha256')).Trim()
if($pinned070 -cnotmatch '^[0-9a-f]{64}$' -or (Get-FileHash -LiteralPath $price070 -Algorithm SHA256).Hash.ToLowerInvariant() -cne $pinned070){throw 'TASK070: committed price payload checksum differs; no build or deployment.'}
& ant -f (Join-Path $committed 'build.xml') "-Dbuild=$build" jar compile-tests *> $log
if($LASTEXITCODE){Get-Content -LiteralPath $log -Tail 25;throw 'TASK070: committed build failed, no successful receipt.'}
$receipt=[ordered]@{format=1;owner=$binding.owner;sourceState='COMMITTED';codeCommit=$SourceCommit;sourceModuleRoot=$module;committedModuleRoot=$committed;sourceArchivePath=$archive;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash;committedTreeSha256=(Get-TreeHash070 $committed @('build.xml','java','test','dist','tools') @('dist/libs/GameServer.jar','dist/libs/LoginServer.jar'));buildRoot=$build;gameJarSha256=(Get-FileHash -LiteralPath (Join-Path $build 'dist/libs/GameServer.jar') -Algorithm SHA256).Hash;loginJarSha256=(Get-FileHash -LiteralPath (Join-Path $build 'dist/libs/LoginServer.jar') -Algorithm SHA256).Hash;testTreeSha256=(Get-TreeHash070 (Join-Path $build 'phantom-test') @('bin','resources'));builtUtc=[DateTime]::UtcNow.ToString('o')}
Write-Text070 (Join-Path $build 'BUILD_INFO070.json') ($receipt | ConvertTo-Json -Depth 4)
Write-Output "TASK070_COMMITTED_BUILD source=$SourceCommit; gameJar=$($receipt.gameJarSha256); receipt=$build/BUILD_INFO070.json"
