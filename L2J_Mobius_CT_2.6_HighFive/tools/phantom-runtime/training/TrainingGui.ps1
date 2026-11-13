[CmdletBinding()]
param([string]$Root,[switch]$CompileOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'TrainingOwner.ps1')
$layout=Get-TrainLayout $Root
$sources=@((Join-Path $PSScriptRoot 'TrainingGui.cs'),(Join-Path $PSScriptRoot 'TrainingGui.Designer.cs'))
$sha=[Security.Cryptography.SHA256]::Create()
try{$sourceSha=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes((($sources|ForEach-Object{[IO.File]::ReadAllText($_)}) -join "`n"))))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
Assert-TrainInstallation $layout
$directory=Join-Path $layout.Root ('gui\'+$sourceSha);$null=[IO.Directory]::CreateDirectory($directory);Assert-TrainDirectory $directory
$assembly=Join-Path $directory 'PhantomTrainingGui.exe';$manifest=Join-Path $directory 'binary.json'
if(Test-Path -LiteralPath $assembly){
    $binary=Read-TrainJson $manifest
    if($binary.sourceSha -cne $sourceSha){throw 'TRAIN009_GUI_SOURCE_CHANGED'}
    Assert-TrainHash $assembly $binary.binarySha
}else{
    Add-Type -Path $sources -ReferencedAssemblies System.Windows.Forms,System.Drawing,System.Web.Extensions -OutputAssembly $assembly -OutputType WindowsApplication
    Write-TrainJson $manifest @{sourceSha=$sourceSha;binarySha=(Get-FileHash -LiteralPath $assembly).Hash}
}
if($CompileOnly){Write-Output ('TRAIN009_GUI_COMPILE_GREEN '+$assembly);return}
# The visible application is a WinForms executable; PowerShell remains a short launcher.
Start-Process -FilePath $assembly -ArgumentList @(('"'+(Join-Path $PSScriptRoot 'Training.ps1')+'"'),('"'+$layout.Root+'"')) -WindowStyle Normal | Out-Null
