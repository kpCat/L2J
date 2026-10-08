[CmdletBinding()]
param([ValidateSet('Update','Start','Stop','Export','Check','Collector')][string]$Action='Collector',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='a',
      [string]$Revision='R1',[string]$ExpectedSha='',[string]$OutputRoot='',
      [long[]]$ProfileIds=@(),[switch]$DumpDuringStop,
      [ValidateSet('Build','Observe','FullObserve','Census','Recovery','Flush','CrashNative','CrashFinalize')][string]$Mode='Build',
      [string]$CohortJson='', [string]$Suite='PhantomCheckpointRecovery025Suite',
      [string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [string]$Label='', [string]$LauncherId='', [switch]$ResetFixture)
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$source027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/Control026.ps1'
$body027=[IO.File]::ReadAllText($source027).Replace('026','027')
$body027=$body027.Replace('PHANTOM-M1-SUSTAINED-FARM-027','PHANTOM-M1-LIFECYCLE-COMPLETION-027')
$body027=$body027.Replace('$taskRoot027=$PSScriptRoot','$taskRoot027=$task027').Replace('(Join-Path $PSScriptRoot','(Join-Path $task027')
$body027=$body027.Replace('6ebe1d93f4ee168cd8952f0416dc920f26c430ac','882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf')
& ([scriptblock]::Create($body027)) @PSBoundParameters
