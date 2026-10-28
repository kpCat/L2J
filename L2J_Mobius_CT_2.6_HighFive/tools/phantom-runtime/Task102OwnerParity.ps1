[CmdletBinding()]
param([Parameter(Mandatory)][string]$SourceRoot,[Parameter(Mandatory)][string]$StageRoot)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$source=[IO.Path]::GetFullPath($SourceRoot)
$stage=[IO.Path]::GetFullPath($StageRoot)
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$acl=Get-Acl -LiteralPath $stage
if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $identity){throw 'OWN102_OWNER_REQUIRED'}
foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity,'S-1-5-18')){throw 'OWN102_ACL_DRIFT'}}
foreach($root in @($stage,$source)){
    for($walk=Get-Item -LiteralPath $root;$null -ne $walk;$walk=$walk.Parent){if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN102_REPARSE'}}
}
if($stage -ieq (Split-Path $source -Parent) -or $stage.StartsWith((Split-Path $source -Parent)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN102_EXTERNAL_REQUIRED'}
$authority=Get-Content -LiteralPath (Join-Path $stage 'private/owner.properties') -Raw
if(!$authority.Contains('owner=TASK102_POPULATION_SCALE') -or !$authority.Contains('schema=l2jmobiush5_phantom_test_102') -or !$authority.Contains("ownerSid=$identity")){throw 'OWN102_AUTHORITY'}
if(!(Test-Path -LiteralPath (Join-Path $stage 'prepare.completed'))){throw 'OWN102_PREPARE_REQUIRED'}
$profilePath=Join-Path $source 'dist/game/config/OwnerConfig.json'
$profile=Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
$keys=@('EnablePhantomSystem','PhantomPopulationTarget','PhantomPopulationActiveTarget','MaxMaterializedPhantoms')
$expected=@{EnablePhantomSystem=@('boolean','False','True');PhantomPopulationTarget=@('long','0','5000');PhantomPopulationActiveTarget=@('long','0','128');MaxMaterializedPhantoms=@('long','32','128')}
foreach($key in $keys){
    $tuple=@($profile.overrides | Where-Object { $_.file -ceq 'Custom/PhantomPlayers.ini' -and $_.key -ceq $key })
    if($tuple.Count -ne 1 -or $tuple[0].kind -cne $expected[$key][0] -or $tuple[0].baseValue -cne $expected[$key][1] -or $tuple[0].value -cne $expected[$key][2]){throw 'OWN102_SAVED_TUPLE_DRIFT'}
}
function Hash([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Verify-Owner([string]$Action,[string]$Game){
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $source 'tools/OwnerConfig.ps1') -Action $Action -GameRoot $Game -ProfilePath $profilePath
    if($LASTEXITCODE -ne 0){throw "OWN102_OWNER_FAILED $Action exit=$LASTEXITCODE"}
}
$before=@{}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $source 'dist/game/config') -File -Recurse){$before[$file.FullName]=Hash $file.FullName}
Verify-Owner 'Verify' (Join-Path $source 'dist/game')
$game=Join-Path $stage 'owner-parity/game'
if(Test-Path -LiteralPath $game){throw 'OWN102_FRESH_PARITY_REQUIRED'}
New-Item -ItemType Directory -Path $game | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'dist/game/config') -Destination (Join-Path $game 'config') -Recurse
foreach($relative in @('data/EnchantItemGroups.xml','data/EnchantItemData.xml','data/xsd/enchantItemGroups.xsd','data/xsd/enchantItemData.xsd')){
    $target=Join-Path $game $relative
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $source ('dist/game/'+$relative)) -Destination $target
}
$ini=Join-Path $game 'config/Custom/PhantomPlayers.ini'
$original=[IO.File]::ReadAllText($ini)
$baseline=$original
foreach($key in $keys){
    $pattern='(?m)^([ \t]*'+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
    if([regex]::Matches($baseline,$pattern).Count -ne 1){throw 'OWN102_INI_DUPLICATE_OR_MISSING'}
    $value=$expected[$key][1]
    $baseline=[regex]::Replace($baseline,$pattern,{param($match) $match.Groups[1].Value+$value})
}
[IO.File]::WriteAllText($ini,$baseline,[Text.UTF8Encoding]::new($false))
Verify-Owner 'Apply' $game
Verify-Owner 'Verify' $game
$stable=@{}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $game 'config') -File -Recurse){$stable[$file.FullName]=Hash $file.FullName}
Verify-Owner 'Apply' $game
Verify-Owner 'Verify' $game
foreach($path in $stable.Keys){if((Hash $path) -cne $stable[$path]){throw 'OWN102_REPEAT_APPLY_DRIFT'}}
if([IO.File]::ReadAllText($ini) -cne $original){throw 'OWN102_APPLY_INI_CONTENT_DRIFT'}
# An invalid primitive type must be rejected by Apply before its transaction.
$invalid=[regex]::Replace($original,'(?m)^(PhantomPopulationActiveTarget[ \t]*=[ \t]*)[^\r\n]*',{param($match) $match.Groups[1].Value+'invalid'})
[IO.File]::WriteAllText($ini,$invalid,[Text.UTF8Encoding]::new($false))
$invalidHash=Hash $ini
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $source 'tools/OwnerConfig.ps1') -Action Apply -GameRoot $game -ProfilePath $profilePath
if($LASTEXITCODE -eq 0 -or (Hash $ini) -cne $invalidHash){throw 'OWN102_INVALID_APPLY_NOT_FAIL_CLOSED'}
[IO.File]::WriteAllText($ini,$original,[Text.UTF8Encoding]::new($false))
Verify-Owner 'Verify' $game
foreach($path in $before.Keys){if((Hash $path) -cne $before[$path]){throw 'OWN102_SOURCE_CONFIG_DRIFT'}}
Verify-Owner 'Apply' (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive/dist/game')
Verify-Owner 'Verify' (Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive/dist/game')
Write-Output "OWN102_OWNER_PARITY_PASS overrides=$(@($profile.overrides).Count);sourceWrites=0;repeatApplyByteDrift=0;negativeApply=REJECTED;runtime=NOT_RUN"
