[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,40}$')][string]$Label,
      [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
      [Parameter(Mandatory)][string]$BuildRoot)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task073Common.ps1')
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$binding=Get-Binding073 $module 'Test'
$repo=Split-Path $module -Parent
$build=[IO.Path]::GetFullPath($BuildRoot).TrimEnd('\')
$helper=Join-Path $binding.task ('helpers-'+$Label)
$archive=Join-Path $binding.task ('helpers-'+$Label+'.zip')
foreach($path in @($build,$helper,$archive)){Assert-Plain073 $path}
if((Split-Path $build -Parent) -ine $binding.task -or (Test-Path -LiteralPath $helper) -or (Test-Path -LiteralPath $archive)){throw 'OWN073 new helper and exact build required.'}
$proof=Read-Json073 (Join-Path $build 'BUILD_INFO073.json')
$game=Join-Path $build 'dist/libs/GameServer.jar'
if($proof.owner -cne $binding.owner -or $proof.sourceState -cne 'COMMITTED' -or (Get-FileHash -LiteralPath $game -Algorithm SHA256).Hash -ine $proof.gameJarSha256){throw 'OWN073 final JAR binding differs.'}
$files=@('PhantomSupply073JourneyNative.java')
$paths=@($files | ForEach-Object {'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/'+$_})
Push-Location $repo
try{
    if((& git symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git rev-parse HEAD) -cne $SourceCommit){throw 'OWN073 current committed feature source required.'}
    & git archive --format=zip "--output=$archive" $SourceCommit -- $paths
    if($LASTEXITCODE){throw 'OWN073 helper source archive failed.'}
}finally{Pop-Location}
New-Item -ItemType Directory -Path $helper,(Join-Path $helper 'bin') | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($archive,(Join-Path $helper 'source'))
$sources=@($paths | ForEach-Object {Join-Path (Join-Path $helper 'source') $_})
& javac -encoding UTF-8 -cp ($game+';'+(Join-Path $build 'phantom-test/bin')+';'+(Join-Path $module 'dist/libs/*')) -d (Join-Path $helper 'bin') @sources
if($LASTEXITCODE){throw 'OWN073 helper compile failed.'}
foreach($class in Get-ChildItem -LiteralPath (Join-Path $helper 'bin') -Recurse -File){if($class.FullName.Substring($helper.Length+1).Replace('\','/') -cnotmatch '^bin/org/l2jmobius/tests/phantoms/Phantom(Supply073JourneyNative)(\$[A-Za-z0-9]+)?\.class$'){throw 'OWN073 helper must contain only its two test classes.'}}
$receipt=[ordered]@{owner=$binding.owner;sourceState='COMMITTED';codeCommit=$SourceCommit;sourceArchivePath=$archive;sourceArchiveSha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash;gameJarSha256=$proof.gameJarSha256;classTreeSha256=(Get-TreeHash073 $helper @('bin'));productionFirst=$true}
Write-Text073 (Join-Path $helper 'HELPERS073.json') ($receipt | ConvertTo-Json)
Write-Output "OWN073_HELPER_COMMITTED source=$SourceCommit; root=$helper; productionFirst=true"
