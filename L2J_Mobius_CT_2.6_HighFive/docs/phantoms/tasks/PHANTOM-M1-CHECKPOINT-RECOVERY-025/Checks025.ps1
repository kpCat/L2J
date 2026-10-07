[CmdletBinding()]
param([string]$Suite='PhantomCheckpointRecovery025Suite',[string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label,[string]$LauncherId='')
$ErrorActionPreference='Stop'
$taskRoot025=$PSScriptRoot
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$body025=[IO.File]::ReadAllText((Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Run-Checks024.ps1'))
$body025=$body025.Replace('$PSScriptRoot','$taskRoot025').Replace('contract024a','contract025b').Replace('phantom.contract024.manifest','phantom.contract025.manifest')
$body025=$body025.Replace('819e3cea5baa64e6c429e450c8fc296874e37d1c','07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6')
if($LauncherId){
    if($LauncherId -notin @('population-ecology-goal033','population-ecology-handoff-regression','live003-admission')){throw 'Named regression route required.'}
    $body025=$body025.Replace('else{$args023+=@("org.l2jmobius.tests.phantoms.$Suite",$module,$reports023)}', 'else{$args023+=@("org.l2jmobius.tests.phantoms.PhantomTestLauncher",$LauncherId,"25002503")}')
}
& ([scriptblock]::Create($body025)) -Suite $Suite -Focus $Focus -Engine $Engine -Label $Label
