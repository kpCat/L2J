[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root064=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
$status064=(& (Join-Path $PSScriptRoot 'Runtime064.ps1') -Action Status -RuntimeRoot $root064 | ConvertFrom-Json)
if($status064.database -cne 'l2jmobiush5_phantom_release_064a' -or $status064.game.state -cnotin @('STOPPED','STALE_RECORD') -or $status064.login.state -cnotin @('STOPPED','STALE_RECORD')){throw 'Ordinary preparation requires only the stopped own064 candidate.'}
$receipt064=Join-Path $root064 'private/observer064.properties'
$observer064=[regex]::Match([IO.File]::ReadAllText($receipt064),'(?m)^objectId=([0-9]+)\r?$')
if(!$observer064.Success){throw 'Native prepared064 viewpoint receipt required.'}
$backup064=Join-Path $root064 'private/before-ordinary-config'
if(Test-Path -LiteralPath $backup064){throw 'Ordinary config was prepared; no silent overwrite.'}
New-Item -ItemType Directory -Path $backup064 | Out-Null
foreach($pair064 in @(@('game/config/Custom/PhantomPlayers.ini','PhantomPlayers.ini'),@('game/config/Custom/LocalPlayPilot.ini','LocalPlayPilot.ini'))){Copy-Item -LiteralPath (Join-Path $root064 $pair064[0]) -Destination (Join-Path $backup064 $pair064[1])}
function Set064([string]$Relative,[string]$Key,[string]$Value){
    $path064=Join-Path $root064 $Relative;$text064=[IO.File]::ReadAllText($path064);$pattern064='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text064,$pattern064).Count -ne 1){throw "Need one $Key"}
    [IO.File]::WriteAllText($path064,[regex]::Replace($text064,$pattern064,{param($match064) $match064.Groups[1].Value+$Value}),[Text.UTF8Encoding]::new($false))
}
$phantom064='game/config/Custom/PhantomPlayers.ini'
foreach($pair064 in @(@('EnablePhantomSystem','True'),@('MaxMaterializedPhantoms','16'),@('PhantomPopulationTarget','12'),@('PhantomPopulationActiveTarget','6'),@('PhantomEcologyPreset','FRESH'),@('PhantomEcologyWorldAgeDays','0'))){Set064 $phantom064 $pair064[0] $pair064[1]}
$pilot064='game/config/Custom/LocalPlayPilot.ini'
foreach($pair064 in @(@('EnableLocalPlayPilot','True'),@('EnableLocalPlayPilotAutoAttach','True'),@('LocalPlayPilotAutoAttachCharacters','Task064View'),@('EnableLocalPlaySyntheticHuman','False'),@('LocalPlaySyntheticCharacterObjectId',$observer064.Groups[1].Value),@('LocalPlaySyntheticCharacterName','Task064View'))){Set064 $pilot064 $pair064[0] $pair064[1]}
# Existing Pilot.privateAcl intentionally accepts exactly the current user, including on subfolders.
foreach($relative064 in @('game/config/Custom/LocalPlayPilot.ini','playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal','playtest-lifecycle')){
    $path064=Join-Path $root064 $relative064
    if(!(Test-Path -LiteralPath $path064)){New-Item -ItemType Directory -Path $path064 | Out-Null}
    $user064='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant064=if((Get-Item -LiteralPath $path064).PSIsContainer){$user064+':(OI)(CI)F'}else{$user064+':F'}
    & icacls.exe $path064 /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant064 | Out-Null
    if($LASTEXITCODE){throw 'TASK064 private DACL operation failed.'}
}
Write-Output 'TASK064_ORDINARY_CONFIG_PREPARED population=12; ACTIVE=6; cap=16; freshAge=0; synthetic=false; observer=PREPARED_PASSIVE_VIEWPOINT; DB_WRITES=0'
