[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task068Common.ps1')
. (Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1')
function Assert-Check068([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
function Assert-Refused068([scriptblock]$Action,[string]$Message){
    $refused=$false
    try{& $Action | Out-Null}catch{$refused=$true}
    Assert-Check068 $refused $Message
}
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$test=Get-Binding068 $module 'Test'
$release=Get-Binding068 $module 'Release'
foreach($binding in @($test,$release)){
    $null=Assert-Binding068 $binding.root $module $binding.schema $binding.role
    Assert-Jdbc068 ('jdbc:mysql://127.0.0.1:3308/'+$binding.schema+'?useSSL=false') $binding.schema
    Assert-Refused068 {Assert-Binding068 ($binding.root+'-foreign') $module $binding.schema $binding.role} 'Sibling runtime root was accepted.'
    Assert-Refused068 {Assert-Jdbc068 ('jdbc:mysql://localhost:3308/'+$binding.schema+'?useSSL=false') $binding.schema} 'Host alias was accepted.'
}
Assert-Refused068 {Assert-Binding068 $release.root $module 'l2jmobiush5_personal_release_067' $release.role} 'Protected historical schema was accepted.'
Assert-Refused068 {Assert-Binding068 $test.root $module $test.schema 'RELEASE_CANDIDATE'} 'Fixture inherited release role.'
$runtimeId='f'*64
$process=[pscustomobject]@{pid=681;startTimeUtcTicks=100000L;role='GameServer';jar='GameServer.jar';runtime=$runtimeId}
$record=[pscustomobject]@{format=2;pid=681;startTimeUtcTicks=99999L;role='GameServer';jar='GameServer.jar';runtime=$runtimeId}
$state=Resolve-LocalPlayOwnership 'GameServer' $runtimeId 'GameServer.jar' @(7849) $record @($process) @{7849=681}
Assert-Check068 (!$state.recordVerified) 'A reused PID inherited stale incarnation authority.'
$record.startTimeUtcTicks=100000L
$state=Resolve-LocalPlayOwnership 'GameServer' $runtimeId 'GameServer.jar' @(7849) $record @($process) @{7849=999}
Assert-Check068 ($state.state -ceq 'FOREIGN_PORT_OWNER') 'Foreign listener ownership was accepted.'
foreach($name in @('Task068Common.ps1','Build-Task068.ps1','Assemble068.ps1','Runtime068.ps1','Test-Delivery068.ps1')){
    $tokens=$null; $errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name),[ref]$tokens,[ref]$errors)
    Assert-Check068 ($errors.Count -eq 0) "PowerShell parser rejected $name."
}
Write-Output 'TASK068_DELIVERY_GUARDS_PASS cases=3; DB_WRITES=0; PROCESS_ACTIONS=0; externalWrites=0'
