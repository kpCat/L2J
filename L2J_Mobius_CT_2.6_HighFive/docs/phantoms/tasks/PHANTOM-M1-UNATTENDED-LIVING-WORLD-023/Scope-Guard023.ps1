[CmdletBinding()]
param([switch]$CommitSource,[string]$Message='Fix M1 continuous native evidence and configured ecology drain')
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$prefix='L2J_Mobius_CT_2.6_HighFive/'
$source023=@(
'java/org/l2jmobius/gameserver/model/actor/PlayerNativeWork.java',
'java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java',
'java/org/l2jmobius/gameserver/model/actor/Creature.java',
'java/org/l2jmobius/gameserver/model/actor/Attackable.java',
'java/org/l2jmobius/gameserver/model/actor/Player.java',
'java/org/l2jmobius/gameserver/model/actor/tasks/creature/MagicUseTask.java',
'java/org/l2jmobius/gameserver/model/groups/Party.java',
'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundDecision.java',
'java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java',
'java/org/l2jmobius/gameserver/phantoms/background/PhantomVisibleAutoPlay.java',
'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomLivingWorld023Suite.java',
'test/java/org/l2jmobius/gameserver/phantoms/player/PhantomM1DynamicRecipientChecks.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomNativeFarmContinuation022Suite.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomNativeContextHandoffSuite.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomBackgroundSuite.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomNativeEvidenceContinuation022Suite.java',
'test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java') | ForEach-Object { $prefix+$_ }
$source023 | Set-Content (Join-Path $PSScriptRoot 'SOURCE_EXACT_ALLOWLIST.txt') -Encoding utf8
$changed=@(& git -C $root diff --name-only 'fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6')
if($LASTEXITCODE -ne 0){throw 'Exact diff inventory failed.'}
foreach($path in $changed){if($path -notin $source023 -and -not $path.StartsWith($prefix+'docs/phantoms/tasks/PHANTOM-M1-UNATTENDED-LIVING-WORLD-023/')){throw "OUT_OF_SCOPE:$path"}}
& git -C $root diff --check
if($LASTEXITCODE -ne 0){throw 'Whitespace check failed.'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
$mojibake="Рџ|Рќ|Рћ|Р•|РЎ|Р›|Р¤|Рњ|РЈ|Рљ|Рґ|Рµ|Р°|Р»|РЅ|Рѕ|СЏ|С€|СЂ|С‹|СЊ|С‚|Сѓ|С‡|С…|С†|�"
$escaped='\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
foreach($path in $source023){
    $text=$utf8.GetString([IO.File]::ReadAllBytes((Join-Path $root $path)))
    if($text -match $mojibake){throw "MOJIBAKE:$path"}
    if($text -match $escaped){throw "ESCAPED_CYRILLIC:$path"}
}
@('scope=PASS;productionPaths=11;testPaths=7;newProductionClasses=0',
  'mojibake=PASS;18 exact source/test paths;all required markers',
  'escapedCyrillic=PASS;18 exact source/test paths;Unicode and XML patterns',
  'whitespace=PASS') | Set-Content (Join-Path $PSScriptRoot 'SOURCE_GUARD.txt') -Encoding utf8
if($CommitSource){
    foreach($path in $source023){& git -C $root add -- $path; if($LASTEXITCODE -ne 0){throw "Exact add failed:$path"}}
    $staged=@(& git -C $root diff --cached --name-only)
    foreach($path in $staged){if($path -notin $source023){throw "Unexpected staged path:$path"}}
    & git -C $root diff --cached --check
    if($LASTEXITCODE -ne 0){throw 'Staged whitespace check failed.'}
    & git -C $root commit -m $Message
    if($LASTEXITCODE -ne 0){throw 'Exact source commit failed.'}
}
& git -C $root rev-parse HEAD
