[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,
      [Parameter(Mandatory)][ValidateRange(1,2147483647)][long]$HumanObjectId,
      [Parameter(Mandatory)][ValidatePattern('^[A-Za-z][A-Za-z0-9]{2,15}$')][string]$HumanName,
      [Parameter(Mandatory)][ValidateRange(1,2147483647)][long]$ProfileId)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task070Common.ps1')
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
$owner=Join-Path $runtime 'ownership070.properties'
$module=Read-Field070 $owner 'sourceModuleRoot'
$binding=Assert-Binding070 $runtime $module (Read-Field070 $owner 'schema') (Read-Field070 $owner 'role')
if($binding.role -cne 'MUTABLE_FIXTURE'){throw 'TASK070: fixture configuration cannot change RELEASE.'}
$status=(& (Join-Path $runtime 'tools/Runtime070.ps1') -Action Status -RuntimeRoot $runtime) -join "`n" | ConvertFrom-Json
if($status.game.state -cne 'STOPPED' -or $status.login.state -cne 'STOPPED' -or $status.databaseConnections -ne 0){throw 'TASK070: stopped TEST/DB0 required.'}
$receipt=Join-Path $binding.task 'reports/TEST_CONFIGURATION070.json'
if(Test-Path -LiteralPath $receipt){throw 'TASK070: existing test profile is preserved; no implicit reinitialization.'}
$backup=Join-Path ([IO.Path]::GetTempPath()) ('Phantom-070-test-config-preimages-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $backup | Out-Null
Set-Private070 $backup
$preimages=@(foreach($relative in @('game/config/Custom/PhantomPlayers.ini','game/config/Custom/LocalPlayPilot.ini')){
    $path=Join-Path $runtime $relative
    $copy=Join-Path $backup (Split-Path $relative -Leaf)
    Copy-Item -LiteralPath $path -Destination $copy
    $hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if((Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash -ine $hash){throw 'TASK070: preimage hash differs.'}
    [pscustomobject]@{path=$path;copy=$copy;sha256=$hash}
})
foreach($entry in @(@('PhantomPopulationTarget','8'),@('PhantomPopulationActiveTarget','8'),@('MaxMaterializedPhantoms','8'),@('PhantomPopulationTimeZone','-12:00'),@('PhantomStarterShotLifetimeLimit','0'))){Set-Config070 $runtime 'game/config/Custom/PhantomPlayers.ini' $entry[0] $entry[1]}
foreach($entry in @(@('EnableLocalPlayPilot','True'),@('EnableLocalPlayPilotAutoAttach','True'),@('LocalPlayPilotAutoAttachCharacters',$HumanName),@('EnableLocalPlaySyntheticHuman','True'),@('LocalPlaySyntheticCharacterObjectId',"$HumanObjectId"),@('LocalPlaySyntheticCharacterName',$HumanName),@('LocalPlayObserverProfileIds',"$ProfileId"))){Set-Config070 $runtime 'game/config/Custom/LocalPlayPilot.ini' $entry[0] $entry[1]}
$source=Read-Json070 (Join-Path $runtime 'SOURCE_BUILD_INFO070.json')
foreach($name in @('LocalPlay-Ownership.ps1','LocalPlay-Pilot.ps1','Start-LocalPlaySynthetic.ps1')){
    $input=Join-Path $source.committedModuleRoot ('tools/phantom-local-play/'+$name)
    $output=Join-Path $runtime $name
    if(Test-Path -LiteralPath $output){throw 'TASK070: preserve existing native test operator.'}
    Copy-Item -LiteralPath $input -Destination $output
    if((Get-FileHash -LiteralPath $input -Algorithm SHA256).Hash -ine (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash){throw 'TASK070: native test operator differs.'}
}
Write-Text070 $receipt ([ordered]@{owner=$binding.owner;schema=$binding.schema;runtime=$runtime;ordinaryCharacter=$HumanObjectId;ordinaryName=$HumanName;profile=$ProfileId;population=8;zone='-12:00';starterShots=0;fixtureOnly=$true;preimages=$preimages} | ConvertTo-Json -Depth 5)
Write-Output 'TEST070_CONFIGURED; short TestHuman available only by explicit START; no existing profile/goal/cursor/HP mutation; RELEASE unchanged.'
