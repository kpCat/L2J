[CmdletBinding()]
param([ValidateSet('Probe','Discovery','Scene')][string]$Mode='Probe',
      [ValidateSet('a','b','c','d','e','f','g','h')][string]$Episode='b',
      [Parameter(Mandatory)][string]$OutputRoot,[int]$Seconds=80,
      [string]$FrozenSha='',[string[]]$PreviousPrimaryIds=@(),
      [hashtable]$SetupTeleport=@{},[hashtable]$SetupMove=@{},
      [switch]$CollectSealed,[long[]]$ProbeProfileIds=@(452),[long[]]$OriginalProfileIds=@())
$ErrorActionPreference='Stop'
$task027=$PSScriptRoot
$module027=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$source027=Join-Path $module027 'docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/Observe026.ps1'
$body027=[IO.File]::ReadAllText($source027).Replace('026','027')
$body027=$body027.Replace('PHANTOM-M1-SUSTAINED-FARM-027','PHANTOM-M1-LIFECYCLE-COMPLETION-027')
$body027=$body027.Replace('$taskRoot027=$PSScriptRoot','$taskRoot027=$task027').Replace('(Join-Path $PSScriptRoot','(Join-Path $task027')
& ([scriptblock]::Create($body027)) @PSBoundParameters
