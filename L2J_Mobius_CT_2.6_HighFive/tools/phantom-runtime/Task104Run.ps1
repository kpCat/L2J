[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Bind','Seal','A','B','C')][string]$Action,
    [Parameter(Mandatory)][string]$SourceRoot,
    [Parameter(Mandatory)][string]$StageRoot,
    [Parameter(Mandatory)][string]$OriginStage
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Physical([string]$Path) {
    $item=Get-Item -LiteralPath $Path -Force
    for($walk=$item;$null -ne $walk;$walk=if($walk -is [IO.FileInfo]){$walk.Directory}else{$walk.Parent}) {
        if($walk.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'OWN104_REPARSE'}
    }
    return $item.FullName
}
function Hash([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function New-Text([string]$Path,[string]$Value){
    $stream=[IO.File]::Open($Path,'CreateNew','Write','None')
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Value);$stream.Write($bytes,0,$bytes.Length)}finally{$stream.Dispose()}
}
function Java0 {
    if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count -ne 0){throw 'OWN104_JAVA_PRESENT_NO_NATIVE'}
    if(@(Get-NetTCPConnection -ErrorAction Stop|Where-Object {$_.State -eq 'Established' -and ($_.LocalPort -eq 3308 -or $_.RemotePort -eq 3308)}).Count -ne 0){throw 'OWN104_SQL_TCP_PRESENT'}
}
$source=Physical $SourceRoot
$stage=Physical $StageRoot
$origin=Physical $OriginStage
$repo=Split-Path -Parent $source
if($stage -ieq $origin -or $stage.StartsWith($origin+'\',[StringComparison]::OrdinalIgnoreCase) -or $origin.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase) -or $stage -ieq $repo -or $stage.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OWN104_DISTINCT_EXTERNAL_SHADOW_REQUIRED'}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
foreach($root in @($stage,$origin)){
    $acl=Get-Acl -LiteralPath $root
    if(!$acl.AreAccessRulesProtected -or [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid){throw 'OWN104_OWNER_ACL'}
    foreach($rule in $acl.Access){if($rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid,'S-1-5-18')){throw 'OWN104_NONPRIVATE_ACL'}}
}
$module=Join-Path $stage 'L2J_Mobius_CT_2.6_HighFive'
$private=Join-Path $stage 'private'
$game=Join-Path $module 'dist/game'
$database=Join-Path $game 'config/Database.ini'
$originOwner=[IO.File]::ReadAllText((Join-Path $origin 'private/owner.properties'))
$uuid=[regex]::Match($originOwner,'(?m)^installationId=([^\r\n]+)').Groups[1].Value
if(!$originOwner.Contains('owner=TASK102_POPULATION_SCALE') -or !$originOwner.Contains('schema=l2jmobiush5_phantom_test_102') -or !$originOwner.Contains("ownerSid=$sid")){throw 'OWN104_EXACT_ORIGIN_REQUIRED'}
Java0
if($Action -eq 'Bind'){
    # Bind may replace authority only in the fresh Prepare output, never in an existing runtime.
    Physical $private|Out-Null
    Physical (Join-Path $game 'config')|Out-Null
    foreach($relative in @('private/db.created','private/native.properties','private/native-inputs.tsv','private/recovery103.bound','private/recovery103.properties','private/recovery104.bound','private/recovery104.properties','private/prepared104-owner.properties','private/receipts','private/receipts103','private/receipts104','L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini')){
        if(Test-Path -LiteralPath (Join-Path $stage $relative)){throw 'OWN104_FRESH_PREPARED_SHADOW_ONLY'}
    }
    $preparedPath=Physical (Join-Path $private 'owner.properties')
    $prepared=[IO.File]::ReadAllText($preparedPath)
    $preparationId=[regex]::Match($prepared,'(?m)^installationId=([^\r\n]+)').Groups[1].Value
    $preparedStage=[regex]::Match($prepared,'(?m)^stage=([^\r\n]+)').Groups[1].Value
    $preparedSource=[regex]::Match($prepared,'(?m)^sourceModule=([^\r\n]+)').Groups[1].Value
    $completion=[IO.File]::ReadAllText((Physical (Join-Path $stage 'prepare.completed')))
    $begun=[IO.File]::ReadAllText((Physical (Join-Path $stage 'prepare.begun')))
    $inputs=Physical (Join-Path $stage 'inputs.tsv')
    if([guid]::ParseExact($preparationId,'D').ToString() -cne $preparationId -or $preparationId -ceq $uuid -or
        (Physical $preparedStage) -ine $stage -or (Physical $preparedSource) -ine $source -or
        !$prepared.Contains('owner=TASK102_POPULATION_SCALE') -or !$prepared.Contains('schema=l2jmobiush5_phantom_test_102') -or
        !$prepared.Contains('branch=feature/phantom-world') -or !$prepared.Contains("ownerSid=$sid") -or
        !$prepared.Contains('inputsSha256='+(Hash $inputs)) -or !$completion.Contains("installationId=$preparationId`n") -or !$begun.Contains("installationId=$preparationId`n")){
        throw 'OWN104_PREPARATION_IDENTITY_MISMATCH'
    }
    $oldReceipts=Join-Path $origin 'private/receipts-continuation2'
    if([IO.File]::ReadAllText((Join-Path $oldReceipts 'stop4.txt')) -notmatch '^drained=false;' -or (Test-Path (Join-Path $oldReceipts 'stopped4.checkpoint')) -or @([IO.File]::ReadAllLines((Join-Path $oldReceipts 'ids4.tsv'))).Count -ne 5000){throw 'OWN104_UNCLEAN_ORIGINAL5000_REQUIRED'}
    Copy-Item -LiteralPath (Join-Path $private 'owner.properties') -Destination (Join-Path $private 'prepared104-owner.properties')
    Copy-Item -LiteralPath (Join-Path $stage 'inputs.tsv') -Destination (Join-Path $private 'source104-inputs.tsv')
    $owner=[regex]::Replace($originOwner,'(?m)^stage=.*$',('stage='+$stage.Replace('\','/')))
    $owner=[regex]::Replace($owner,'(?m)^expiresUtc=.*$',('expiresUtc='+[DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ')))
    [IO.File]::WriteAllText((Join-Path $private 'owner.properties'),$owner,[Text.UTF8Encoding]::new($false))
    foreach($relative in @('inputs.tsv','private/db.created')){Copy-Item -LiteralPath (Join-Path $origin $relative) -Destination (Join-Path $stage $relative) -Force;if((Hash (Join-Path $origin $relative)) -cne (Hash (Join-Path $stage $relative))){throw 'OWN104_ORIGIN_COPY_DRIFT'}}
    Copy-Item -LiteralPath (Join-Path $oldReceipts 'ids4.tsv') -Destination (Join-Path $private 'origin103-ids.tsv')
    Copy-Item -LiteralPath (Join-Path $origin 'L2J_Mobius_CT_2.6_HighFive/dist/game/config/Database.ini') -Destination $database
    New-Item -ItemType Directory -Path (Join-Path $private 'receipts104')|Out-Null
    New-Text (Join-Path $private 'recovery104.bound') 'originalStop=FAILED;reentry=UNCLEAN;noReseed=true;schemaWrites=0'
    Write-Output 'OWN104_BOUND originalAuthorityPreserved=true;historicalReceiptsUntouched=true;SQL=0'
    return
}
$cp=(Join-Path $stage 'build/phantom-test/bin')+';'+(Join-Path $stage 'build/phantom-test/resources')+';'+(Join-Path $module 'dist/libs/*')
function Census([string]$Cut){
    $out=Join-Path $private ('receipts104/readonly-'+$Cut)
    New-Item -ItemType Directory -Path $out|Out-Null
    & java -cp $cp (Join-Path $module 'tools/phantom-runtime/Task104Census.java') $origin $out
    if($LASTEXITCODE -ne 0 -or [IO.File]::ReadAllText((Join-Path $out 'sessions.txt')) -notmatch ';own=0;foreign=0;fullVisibility=true'){throw 'OWN104_READONLY_AUTHORITY_QUIESCENCE_FAILED'}
    return $out
}
if($Action -eq 'Seal'){
    if(!(Test-Path (Join-Path $private 'recovery104.bound')) -or (Test-Path (Join-Path $private 'recovery104.properties'))){throw 'OWN104_SINGLE_SEAL_REQUIRED'}
    Census 'seal' | Out-Host
    $rows=[Collections.Generic.List[string]]::new()
    foreach($root in @($module,(Join-Path $stage 'build/phantom-test/bin'),(Join-Path $stage 'build/phantom-test/resources'))){
        foreach($file in Get-ChildItem -LiteralPath $root -File -Recurse -Force){
            Physical $file.FullName|Out-Null
            $relative=$file.FullName.Substring($stage.Length+1).Replace('\','/')
            if($relative -match '/dist/(?:game|login)/(?:log|logs)/'){continue}
            $rows.Add($relative+"`t"+(Hash $file.FullName))
        }
    }
    New-Text (Join-Path $private 'native-inputs.tsv') (($rows|Sort-Object) -join "`n")
    $pins=@('dist/libs/GameServer.jar','dist/game/config/Database.ini','dist/game/config/Custom/PhantomPlayers.ini')|ForEach-Object{$_+'='+(Hash (Join-Path $module $_))}
    New-Text (Join-Path $private 'native.properties') (($pins -join "`n")+"`nnativeInputsSha256="+(Hash (Join-Path $private 'native-inputs.tsv')))
    Copy-Item -LiteralPath (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini') -Destination (Join-Path $private 'runtime104-final.ini')
    New-Text (Join-Path $private 'recovery104.properties') ("task=PHANTOM-LOCK-ORDER-RECOVERY-104`nnoReseed=true`njarSha256="+(Hash (Join-Path $module 'dist/libs/GameServer.jar'))+"`noriginIdsSha256="+(Hash (Join-Path $private 'origin103-ids.tsv'))+"`nsourceOwnerSha256="+(Hash (Join-Path $source 'dist/game/config/OwnerConfig.json'))+"`nsourceIniSha256="+(Hash (Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')))
    Write-Output 'OWN104_SEALED sameOriginalDB=true;currentJAR=true;inputsPinned=true'
    return
}
$authority=[IO.File]::ReadAllText((Join-Path $private 'recovery104.properties'))
foreach($pair in @(@('jarSha256',(Join-Path $module 'dist/libs/GameServer.jar')),@('originIdsSha256',(Join-Path $private 'origin103-ids.tsv')),@('sourceOwnerSha256',(Join-Path $source 'dist/game/config/OwnerConfig.json')),@('sourceIniSha256',(Join-Path $source 'dist/game/config/Custom/PhantomPlayers.ini')),@('sourceIniSha256',(Join-Path $private 'runtime104-final.ini')))){
    if(!$authority.Contains($pair[0]+'='+(Hash $pair[1]))){throw 'OWN104_SEALED_INPUT_DRIFT'}
}
$phase=switch($Action){'A'{6};'B'{4};'C'{5}}
$previous=switch($Action){'B'{6};'C'{4};default{0}}
$receipts=Join-Path $private 'receipts104'
if(Test-Path (Join-Path $receipts "start$phase.begun")){throw 'OWN104_SINGLE_GENERATION_ONLY'}
if($previous -ne 0){
    foreach($name in @("phase$previous.complete","stopped$previous.checkpoint","stop$previous-world-leases.txt","exit$previous.clean")){if(!(Test-Path (Join-Path $receipts $name))){throw 'OWN104_COMPLETE_PREVIOUS_STOP_REQUIRED'}}
    if([IO.File]::ReadAllText((Join-Path $receipts "stop$previous.txt")) -notmatch '^drained=true;'){throw 'OWN104_PREVIOUS_STOP_FAILED'}
}
$pre=@(Census ('before'+$Action))[-1]
if($previous -ne 0){
    $current=([IO.File]::ReadAllLines((Join-Path $pre 'durable.checkpoint'))|Select-Object -First 4) -join "`n"
    if($current -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$previous.checkpoint")).TrimEnd([char[]]"`r`n")){throw 'OWN104_STOPPED_CHECKPOINT_DRIFT'}
}
$cap=if($Action -eq 'A'){16}else{128}
$ini=Join-Path $game 'config/Custom/PhantomPlayers.ini'
$text=[IO.File]::ReadAllText((Join-Path $private 'runtime104-final.ini'))
foreach($key in @('PhantomPopulationActiveTarget','MaxMaterializedPhantoms')){
    $pattern='(?m)^('+[regex]::Escape($key)+'[ \t]*=[ \t]*)[^\r\n]*'
    if([regex]::Matches($text,$pattern).Count -ne 1){throw 'OWN104_OVERLAY_KEY'}
    $text=[regex]::Replace($text,$pattern,{param($match)$match.Groups[1].Value+[string]$cap})
}
[IO.File]::WriteAllText($ini,$text,[Text.UTF8Encoding]::new($false))
$manifest=Join-Path $private 'native-inputs.tsv'
$pinsPath=Join-Path $private 'native.properties'
$configRelative='L2J_Mobius_CT_2.6_HighFive/dist/game/config/Custom/PhantomPlayers.ini'
$rows=@([IO.File]::ReadAllLines($manifest)|ForEach-Object{if($_.StartsWith($configRelative+"`t")){$configRelative+"`t"+(Hash $ini)}else{$_}})
[IO.File]::WriteAllText($manifest,($rows -join "`n"),[Text.UTF8Encoding]::new($false))
$pins=[regex]::Replace([IO.File]::ReadAllText($pinsPath),'(?m)^dist/game/config/Custom/PhantomPlayers.ini=.*$',('dist/game/config/Custom/PhantomPlayers.ini='+(Hash $ini)))
$pins=[regex]::Replace($pins,'(?m)^nativeInputsSha256=.*$',('nativeInputsSha256='+(Hash $manifest)))
[IO.File]::WriteAllText($pinsPath,$pins,[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $manifest -Destination (Join-Path $private "generation$Action-native-inputs.tsv")
Copy-Item -LiteralPath $pinsPath -Destination (Join-Path $private "generation$Action-native.properties")
Java0
Push-Location $game
try{
    & java -Xms512m -Xmx8g '-Dphantom.task102.native=true' "-Dphantom.task102.stage=$stage" "-Dphantom.task102.uuid=$uuid" "-Dphantom.module.root=$module" "-Dphantom.test.config=$database" -cp $cp org.l2jmobius.tests.phantoms.PhantomPopulation102Native ('recovery104'+$Action)
    if($LASTEXITCODE -ne 0){throw "OWN104_NATIVE_FAILED generation=$Action exit=$LASTEXITCODE"}
}finally{Pop-Location}
Java0
$post=@(Census ('after'+$Action))[-1]
if([IO.File]::ReadAllText((Join-Path $receipts "stop$phase.txt")) -notmatch '^drained=true;' -or [IO.File]::ReadAllText((Join-Path $receipts "stop$phase-world-leases.txt")) -cne "ordinaryWorld=0;allLeases=0;observerOwners=0`n"){throw 'OWN104_NATIVE_STOP_RECEIPT_REQUIRED'}
$current=([IO.File]::ReadAllLines((Join-Path $post 'durable.checkpoint'))|Select-Object -First 4) -join "`n"
if($current -cne [IO.File]::ReadAllText((Join-Path $receipts "stopped$phase.checkpoint")).TrimEnd([char[]]"`r`n")){throw 'OWN104_NATIVE_POSTCHECKPOINT_DRIFT'}
New-Text (Join-Path $receipts "exit$phase.clean") ("exit=0;JVM=0;SQL=0;World=0;leases=0;jarSha256="+(Hash (Join-Path $module 'dist/libs/GameServer.jar')))
Write-Output "OWN104_GENERATION_CLEAN generation=$Action;cap=$cap;sameDB=true;sameJAR=true;JVM=0;SQL=0;World=0;leases=0"
