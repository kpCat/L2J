[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Bind','Seal','Probe16','Probe32','Stop','Restart','Postflight')][string]$Action,
    [Parameter(Mandatory)][string]$SourceRoot,
    [Parameter(Mandatory)][string]$StageRoot,
    [Parameter(Mandatory)][string]$OriginStage
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Physical([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    for($walk=$item;$null -ne $walk;$walk=if($walk -is [IO.FileInfo]){$walk.Directory}else{$walk.Parent}) {
        if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN103_REPARSE'}
    }
    return $item.FullName
}
function Hash([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function New-Text([string]$Path,[string]$Value){
    $stream=[IO.File]::Open($Path,'CreateNew','Write','None')
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
}
$source=Physical $SourceRoot
$stage=Physical $StageRoot
$origin=Physical $OriginStage
$repo=Split-Path -Parent $source
if($stage -ieq $origin -or $stage.StartsWith($origin+'\',[StringComparison]::OrdinalIgnoreCase) -or $origin.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase) -or $stage -ieq $repo -or $stage.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN103_EXTERNAL_DISTINCT_SHADOW_REQUIRED'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
foreach($root in @($stage,$origin)){
    $acl=Get-Acl -LiteralPath $root
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN103_OWNER_ACL'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN103_NONPRIVATE_ACL'}}
}
$module=Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
$private=Join-Path $stage 'private'
$game=Join-Path $module 'dist/game'
$database=Join-Path $game 'config/Database.ini'
$originOwner=[IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
$uuid=[regex]::Match($originOwner,'(?m)^installationId=([^\r\n]+)').Groups[1].Value
if(!$originOwner.Contains('owner=TASK102_POPULATION_SCALE') -or !$originOwner.Contains('schema=l2jmobiush5_phantom_test_102') -or !$originOwner.Contains("ownerSid=$sid")){throw 'OWN103_EXACT_ORIGIN_REQUIRED'}
if($Action -eq 'Bind'){
    if(Test-Path (Join-Path $private 'recovery103.bound')){throw 'OWN103_BIND_ONCE'}
    if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN103_JAVA_PRESENT_NO_BIND'}
    $oldReceipts=Join-Path $origin 'private/receipts-continuation2'
    if([IO.File]::ReadAllText((Join-Path $oldReceipts 'stop4.txt')) -notmatch '^drained=false;' -or (Test-Path (Join-Path $oldReceipts 'stopped4.checkpoint')) -or @([IO.File]::ReadAllLines((Join-Path $oldReceipts 'ids4.tsv'))).Count -ne 5000){throw 'OWN103_PRESERVED_UNCLEAN5000_REQUIRED'}
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared-shadow-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source103-inputs.tsv')
    # Retain the durable OWN102 UUID/source/input identity. Only this new shadow's location/expiry changes.
    $owner=[regex]::Replace($originOwner,'(?m)^stage=.*$',('stage='+$stage.Replace('\','/')))
    $owner=[regex]::Replace($owner,'(?m)^expiresUtc=.*$',('expiresUtc='+[DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'),$owner,[Text.UTF8Encoding]::new($false))
    foreach($relative in @('inputs.tsv','private/db.created')){Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force;if((Hash (Join-Path $origin $relative)) -cne (Hash (Join-Path $stage $relative))){throw 'OWN103_ORIGIN_COPY_DRIFT'}}
    Copy-Item -LiteralPath (Join-Path $oldReceipts 'ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path (Join-Path $private 'receipts103') | Out-Null
    New-Text (Join-Path $private 'recovery103.bound') ("origin=$origin`ninstallationId=$uuid`noriginalStop=FAILED;recovery=UNCLEAN_RECOVERY;schemaWrites=0`n")
    Write-Output 'OWN103_BOUND sameDB=true;originalReceiptsUntouched=true;SQL=0'
    return
}
$cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
function Probe {
    Push-Location $game
    try{& java -cp $cp (Join-Path $module 'tools/phantom-runtime/Task102DatabaseProbe.java') diagnose $stage $uuid;if($LASTEXITCODE -ne 0){throw 'OWN103_READONLY_PROBE_FAILED'}}finally{Pop-Location}
}
if($Action -eq 'Postflight'){Probe;return}
if($Action -eq 'Seal'){
    if(!(Test-Path (Join-Path $private 'recovery103.bound')) -or (Test-Path (Join-Path $private 'recovery103.properties'))){throw 'OWN103_BOUND_SINGLE_SEAL_REQUIRED'}
    Probe
    $rows=[Collections.Generic.List[string]]::new()
    foreach($root in @($module,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
        foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
            Physical $file.FullName | Out-Null
            $relative=$file.FullName.Substring($stage.Length+1).Replace('\','/')
            if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
            $rows.Add($relative+"`t"+(Hash $file.FullName))
        }
    }
    New-Text (Join-Path $private 'native-inputs.tsv') (($rows|Sort-Object) -join "`n")
    $pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini')|ForEach-Object{$_+'='+(Hash (Join-Path $module $_))}
    New-Text (Join-Path $private 'native.properties') (($pins -join "`n")+"`nnativeInputsSha256="+(Hash (Join-Path $private 'native-inputs.tsv')))
    Copy-Item -LiteralPath (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime103-final.ini')
    New-Text (Join-Path $private 'recovery103.properties') ("task=PHANTOM-SHUTDOWN-RECOVERY-103`nnoReseed=true`njarSha256="+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))+"`noriginIdsSha256="+(Hash (Join-Path $private 'origin103-ids.tsv'))+"`nsourceOwnerSha256="+(Hash (Join-Path $source 'dist/game/config/OwnerConfig.json'))+"`nsourceIniSha256="+(Hash (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')))
    Write-Output 'OWN103_SEALED repairedJAR=true;originalDBAuthorityPreserved=true'
    return
}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN103_JAVA_PRESENT_NO_NATIVE_START'}
$authority=[IO.File]::ReadAllText((Join-Path $private 'recovery103.properties'))
foreach($pair in @(@('jarSha256',(Join-Path $module 'dist/libs/GameServer.jar')),@('originIdsSha256',(Join-Path $private 'origin103-ids.tsv')),@('sourceOwnerSha256',(Join-Path $source 'dist/game/config/OwnerConfig.json')),@('sourceIniSha256',(Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')),@('sourceIniSha256',(Join-Path $private 'runtime103-final.ini')))){
    if(!$authority.Contains($pair[0]+'='+(Hash $pair[1]))){throw 'OWN103_SEALED_INPUT_DRIFT'}
}
$phase=switch($Action){'Probe16'{6};'Probe32'{7};'Stop'{4};'Restart'{5}}
if(Test-Path (Join-Path $private "receipts103/start$phase.begun")){throw 'OWN103_SINGLE_CAMPAIGN_PHASE_ONLY'}
$previous=switch($phase){7{6};4{7};5{4};default{0}}
if($previous -ne 0 -and (!(Test-Path (Join-Path $private "receipts103/phase$previous.complete")) -or [IO.File]::ReadAllText((Join-Path $private "receipts103/stop$previous.txt")) -notmatch '^drained=true;')){throw 'OWN103_PREVIOUS_GREEN_NATIVE_STOP_REQUIRED'}
Probe
# The only overlays are native16/32 gates; target5000 and all source owner values remain unchanged.
$cap=switch($phase){6{16};7{32};default{128}}
$ini=Join-Path $game 'config/Custom/PhantomPlayers.ini'
$text=[IO.File]::ReadAllText((Join-Path $private 'runtime103-final.ini'))
foreach($key in @('PhantomPopulationActiveTarget','MaxMaterializedPhantoms')){
    $pattern='(?m)^('+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
    if([regex]::Matches($text,$pattern).Count -ne 1){throw 'OWN103_OVERLAY_KEY'}
    $text=[regex]::Replace($text,$pattern,{param($match)$match.Groups[1].Value+[string]$cap})
}
[IO.File]::WriteAllText($ini,$text,[Text.UTF8Encoding]::new($false))
$manifest=Join-Path $private 'native-inputs.tsv'
$pinsPath=Join-Path $private 'native.properties'
Copy-Item -LiteralPath $manifest -Destination (Join-Path $private "before$phase-native-inputs.tsv")
Copy-Item -LiteralPath $pinsPath -Destination (Join-Path $private "before$phase-native.properties")
$rows=[IO.File]::ReadAllLines($manifest)
$configRelative='L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/PhantomPlayers.ini'
$rows=@($rows|ForEach-Object{if($_.StartsWith($configRelative+"`t")){$configRelative+"`t"+(Hash $ini)}else{$_}})
[IO.File]::WriteAllText($manifest,($rows -join "`n"),[Text.UTF8Encoding]::new($false))
$pins=[IO.File]::ReadAllText($pinsPath)
$pins=[regex]::Replace($pins,'(?m)^dist/game/config/Custom/PhantomPlayers.ini=.*$',('dist/game/config/Custom/PhantomPlayers.ini='+(Hash $ini)))
$pins=[regex]::Replace($pins,'(?m)^nativeInputsSha256=.*$',('nativeInputsSha256='+(Hash $manifest)))
[IO.File]::WriteAllText($pinsPath,$pins,[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $manifest -Destination (Join-Path $private "phase$phase-native-inputs.tsv")
Copy-Item -LiteralPath $pinsPath -Destination (Join-Path $private "phase$phase-native.properties")
Push-Location $game
try{
    & java -Xms512m -Xmx8g '-Dphantom.task102.native=true' "-Dphantom.task102.stage=$stage" "-Dphantom.task102.uuid=$uuid" "-Dphantom.module.root=$module" "-Dphantom.test.config=$database" -cp $cp org.l2jmobius.tests.phantoms.PhantomPopulation102Native "recovery$phase"
    if($LASTEXITCODE -ne 0){throw "OWN103_NATIVE_FAILED phase=$phase exit=$LASTEXITCODE"}
}finally{Pop-Location}
Probe
