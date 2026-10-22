[CmdletBinding()]
param(
    [ValidateSet('Plan','Apply','Rollback')][string]$Mode='Plan',
    [Parameter(Mandatory)][string]$GameRoot,
    [string]$Profile,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$JavaClassPath,
    [string]$PlanPath,
    [string]$ExpectedPlanHash,
    [string]$OwnershipManifest,
    [double]$AdenaScale=1,
    [double]$LootScale=1,
    [int[]]$InspectItemId=@(1864,1458,729,6660,999999)
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$utf8Price068=New-Object Text.UTF8Encoding($false,$true)

function Assert-PlainPrice068([string]$Path){
    $absolutePrice068=[IO.Path]::GetFullPath($Path)
    $walkPrice068=[IO.Path]::GetPathRoot($absolutePrice068)
    foreach($partPrice068 in $absolutePrice068.Substring($walkPrice068.Length).Split([IO.Path]::DirectorySeparatorChar)){
        if(!$partPrice068){continue}
        $walkPrice068=Join-Path $walkPrice068 $partPrice068
        if((Test-Path -LiteralPath $walkPrice068) -and ((Get-Item -LiteralPath $walkPrice068 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Physical pricing path required: $walkPrice068"}
    }
    return $absolutePrice068.TrimEnd('\','/')
}
function Get-PriceHash068([byte[]]$Bytes){
    $hasherPrice068=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($hasherPrice068.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()}
    finally{$hasherPrice068.Dispose()}
}
function Get-PriceFileHash068([string]$Path){return Get-PriceHash068 ([IO.File]::ReadAllBytes($Path))}
function Resolve-PriceTarget068([string]$Relative){
    if($Relative -cnotmatch '^data/(multisell/(custom/)?[0-9]+|buylists/[0-9]+)\.xml$'){throw 'Only exact reachable buylist/multisell price files are writable.'}
    $targetPrice068=Assert-PlainPrice068 (Join-Path $gamePrice068 $Relative)
    if(!$targetPrice068.StartsWith($gamePrice068+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Price target escaped the selected root.'}
    return $targetPrice068
}
function Assert-OwnPriceRoot068 {
    if(!$OwnershipManifest){throw 'Apply/Rollback requires an explicit OWNERSHIP068 pricing binding.'}
    $bindingPathPrice068=Assert-PlainPrice068 $OwnershipManifest
    $bindingPrice068=[IO.File]::ReadAllText($bindingPathPrice068,$utf8Price068)|ConvertFrom-Json
    if($bindingPrice068.owner -cne 'TASK068_LIVING_ECONOMY' -or $bindingPrice068.allowPriceApply -ne $true -or (Assert-PlainPrice068 $bindingPrice068.gameRoot) -cne $gamePrice068){throw 'Ownership manifest does not authorize this exact own068 game root.'}
    $bindingParentPrice068=Split-Path $bindingPathPrice068 -Parent
    if($bindingParentPrice068 -cne $gamePrice068 -and $bindingParentPrice068 -cne (Split-Path $gamePrice068 -Parent)){throw 'OWNERSHIP068 pricing binding must be inside this own candidate.'}
}
function Read-PricePlan068 {
    if(!$PlanPath -or $ExpectedPlanHash -cnotmatch '^[0-9a-fA-F]{64}$'){throw 'An exact PlanPath and SHA256 ExpectedPlanHash are required.'}
    $script:planFilePrice068=Assert-PlainPrice068 $PlanPath
    $actualPrice068=Get-PriceFileHash068 $script:planFilePrice068
    if($actualPrice068 -cne $ExpectedPlanHash.ToLowerInvariant()){throw 'Plan hash differs; no target writes performed.'}
    $resultPrice068=[IO.File]::ReadAllText($script:planFilePrice068,$utf8Price068)|ConvertFrom-Json
    if($resultPrice068.version -ne 1 -or $resultPrice068.owner -cne 'TASK068_LIVING_ECONOMY' -or $resultPrice068.gameRoot -cne $gamePrice068){throw 'Price plan root/version/owner mismatch.'}
    return $resultPrice068
}
function Get-PriceBytes068([byte[]]$Before,[object[]]$Offers,[string]$Kind){
    $textPrice068=$utf8Price068.GetString($Before)
    $entryPatternPrice068=if($Kind -ceq 'multisell'){'(?s)<item\b[^>]*>.*?</item>'}elseif($Kind -ceq 'buylist'){'<item\b[^>]*/>'}else{throw 'Unknown price document kind.'}
    $entriesPrice068=[regex]::Matches($textPrice068,$entryPatternPrice068)
    foreach($offerPrice068 in @($Offers|Sort-Object {[int]$_.ordinal} -Descending)){
        $ordinalPrice068=[int]$offerPrice068.ordinal
        if($ordinalPrice068 -lt 1 -or $ordinalPrice068 -gt $entriesPrice068.Count -or [long]$offerPrice068.new_price -lt 1){throw 'Invalid bounded price row.'}
        $entryPrice068=$entriesPrice068[$ordinalPrice068-1]
        $blockPrice068=$entryPrice068.Value
        if($Kind -ceq 'multisell'){
            $ingredientsPrice068=[regex]::Matches($blockPrice068,'<ingredient\b[^>]*/>')
            if($ingredientsPrice068.Count -ne 1 -or $ingredientsPrice068[0].Value -cnotmatch '\bid\s*=\s*"57"'){throw 'Only a single ordinary Adena ingredient is writable.'}
            $ingredientPrice068=$ingredientsPrice068[0]
            $countPrice068=[regex]::Match($ingredientPrice068.Value,'\bcount\s*=\s*"([0-9]+)"')
            if(!$countPrice068.Success -or [long]$countPrice068.Groups[1].Value -ne [long]$offerPrice068.old_price){throw 'Multisell price preimage differs.'}
            $newIngredientPrice068=$ingredientPrice068.Value.Remove($countPrice068.Groups[1].Index,$countPrice068.Groups[1].Length).Insert($countPrice068.Groups[1].Index,[string]$offerPrice068.new_price)
            $newBlockPrice068=$blockPrice068.Remove($ingredientPrice068.Index,$ingredientPrice068.Length).Insert($ingredientPrice068.Index,$newIngredientPrice068)
        }else{
            $countPrice068=[regex]::Match($blockPrice068,'\bprice\s*=\s*"([0-9]+)"')
            if(!$countPrice068.Success -or [long]$countPrice068.Groups[1].Value -ne [long]$offerPrice068.old_price){throw 'Buylist price preimage differs.'}
            $newBlockPrice068=$blockPrice068.Remove($countPrice068.Groups[1].Index,$countPrice068.Groups[1].Length).Insert($countPrice068.Groups[1].Index,[string]$offerPrice068.new_price)
        }
        $textPrice068=$textPrice068.Remove($entryPrice068.Index,$entryPrice068.Length).Insert($entryPrice068.Index,$newBlockPrice068)
    }
    $xmlSettingsPrice068=New-Object Xml.XmlReaderSettings
    $xmlSettingsPrice068.DtdProcessing=[Xml.DtdProcessing]::Prohibit
    $xmlSettingsPrice068.XmlResolver=$null
    $stringReaderPrice068=New-Object IO.StringReader($textPrice068)
    $xmlReaderPrice068=[Xml.XmlReader]::Create($stringReaderPrice068,$xmlSettingsPrice068)
    try{while($xmlReaderPrice068.Read()){} }finally{$xmlReaderPrice068.Dispose();$stringReaderPrice068.Dispose()}
    return ,$utf8Price068.GetBytes($textPrice068)
}
function Write-AtomicPrice068([string]$Path,[byte[]]$Bytes){
    $temporaryPrice068=$Path+'.price068-'+[Guid]::NewGuid().ToString('N')
    $priorPrice068=$temporaryPrice068+'.prior'
    try{[IO.File]::WriteAllBytes($temporaryPrice068,$Bytes);[IO.File]::Replace($temporaryPrice068,$Path,$priorPrice068)}
    finally{foreach($temporaryOwnedPrice068 in @($temporaryPrice068,$priorPrice068)){if(Test-Path -LiteralPath $temporaryOwnedPrice068){Remove-Item -LiteralPath $temporaryOwnedPrice068 -Force}}}
}

$gamePrice068=Assert-PlainPrice068 $GameRoot
$outputPrice068=Assert-PlainPrice068 $OutputDirectory
if(!(Test-Path -LiteralPath $gamePrice068 -PathType Container)){throw 'Existing physical game root required.'}
if($Mode -ceq 'Plan'){
    if(!$Profile){$Profile=Join-Path $gamePrice068 'config/Custom/PhantomMarket.ini'}
    $profilePrice068=Assert-PlainPrice068 $Profile
    if(!$JavaClassPath){$JavaClassPath=(Join-Path (Split-Path $gamePrice068 -Parent) 'libs/GameServer.jar')+';'+(Join-Path (Split-Path $gamePrice068 -Parent) 'libs/*')}
    New-Item -ItemType Directory -Path $outputPrice068 -Force|Out-Null
    $factsPrice068=Join-Path $outputPrice068 'facts'
    & java -cp $JavaClassPath org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPricingExporter $gamePrice068 $profilePrice068 $factsPrice068 ([string]$AdenaScale) ([string]$LootScale) ($InspectItemId -join ',')
    if($LASTEXITCODE -ne 0){throw "Shared Java pricing exporter failed: exit $LASTEXITCODE"}
    $offersPrice068=@(Import-Csv -LiteralPath (Join-Path $factsPrice068 'offers.tsv') -Delimiter "`t")
    $sourcesPrice068=@(Import-Csv -LiteralPath (Join-Path $factsPrice068 'sources.tsv') -Delimiter "`t")
    $changesPrice068=@()
    foreach($groupPrice068 in @($offersPrice068|Where-Object {$_.status -ceq 'PRICED' -and [long]$_.old_price -ne [long]$_.new_price}|Group-Object path|Sort-Object Name)){
        $targetPrice068=Resolve-PriceTarget068 $groupPrice068.Name
        $beforePrice068=[IO.File]::ReadAllBytes($targetPrice068)
        $afterPrice068=Get-PriceBytes068 $beforePrice068 @($groupPrice068.Group) $groupPrice068.Group[0].kind
        $changesPrice068+=[ordered]@{path=$groupPrice068.Name;preHash=(Get-PriceHash068 $beforePrice068);postHash=(Get-PriceHash068 $afterPrice068);offers=@($groupPrice068.Group)}
    }
    $planPrice068=[ordered]@{version=1;owner='TASK068_LIVING_ECONOMY';gameRoot=$gamePrice068;profile=$profilePrice068;adenaScale=$AdenaScale;lootScale=$LootScale;sources=$sourcesPrice068;changes=$changesPrice068;offers=$offersPrice068;unknown=@([IO.File]::ReadAllLines((Join-Path $factsPrice068 'unknown.txt'),$utf8Price068))}
    $planOutputPrice068=Join-Path $outputPrice068 'plan.json'
    [IO.File]::WriteAllText($planOutputPrice068,($planPrice068|ConvertTo-Json -Depth 20),$utf8Price068)
    $offersPrice068|Export-Csv -LiteralPath (Join-Path $outputPrice068 'plan.tsv') -Delimiter "`t" -NoTypeInformation -Encoding UTF8
    @($offersPrice068|Where-Object {$_.status -ceq 'UNPRICED'})|Export-Csv -LiteralPath (Join-Path $outputPrice068 'UNPRICED.tsv') -Delimiter "`t" -NoTypeInformation -Encoding UTF8
    Write-Output ('PLAN path='+$planOutputPrice068+';sha256='+(Get-PriceFileHash068 $planOutputPrice068)+';files='+$changesPrice068.Count+';priced='+@($offersPrice068|Where-Object {$_.status -ceq 'PRICED'}).Count+';unpriced='+@($offersPrice068|Where-Object {$_.status -ceq 'UNPRICED'}).Count)
    return
}

Assert-OwnPriceRoot068
$planPrice068=Read-PricePlan068
if($Mode -ceq 'Apply' -and ([double]$planPrice068.adenaScale -ne 1 -or [double]$planPrice068.lootScale -ne 1)){throw 'Illustrative rate scales cannot be applied; use an actual rates snapshot with scales 1/1.'}
$receiptPathPrice068=Join-Path $outputPrice068 ('apply-'+$ExpectedPlanHash.ToLowerInvariant()+'.json')
if($Mode -ceq 'Apply'){
    if(Test-Path -LiteralPath $receiptPathPrice068){
        $receiptPrice068=[IO.File]::ReadAllText($receiptPathPrice068,$utf8Price068)|ConvertFrom-Json
        if($receiptPrice068.owner -cne 'TASK068_LIVING_ECONOMY' -or $receiptPrice068.planHash -cne $ExpectedPlanHash.ToLowerInvariant() -or $receiptPrice068.gameRoot -cne $gamePrice068){throw 'Existing Apply receipt is not owned by this plan/root.'}
        foreach($changePrice068 in $planPrice068.changes){if((Get-PriceFileHash068 (Resolve-PriceTarget068 $changePrice068.path)) -cne $changePrice068.postHash){throw 'Existing Apply drifted; refusing a blind retry.'}}
        Write-Output 'ALREADY_APPLIED: exact postimages unchanged.';return
    }
    foreach($sourcePrice068 in $planPrice068.sources){
        if($sourcePrice068.path.StartsWith('@formula:')){continue}
        $sourcePathPrice068=if($sourcePrice068.path -ceq '@profile'){$planPrice068.profile}else{Assert-PlainPrice068 (Join-Path $gamePrice068 $sourcePrice068.path)}
        if($sourcePrice068.path -cne '@profile' -and !$sourcePathPrice068.StartsWith($gamePrice068+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Source hash path escaped game root.'}
        if((Get-PriceFileHash068 $sourcePathPrice068) -cne $sourcePrice068.sha256){throw "Source/preimage conflict: $($sourcePrice068.path)"}
    }
    New-Item -ItemType Directory -Path $outputPrice068 -Force|Out-Null
    $backupPrice068=Assert-PlainPrice068 (Join-Path $outputPrice068 ('backup-'+$ExpectedPlanHash.ToLowerInvariant()))
    $existingBackupPrice068=Test-Path -LiteralPath $backupPrice068
    if(!$existingBackupPrice068){New-Item -ItemType Directory -Path $backupPrice068|Out-Null}
    $preparedPrice068=@()
    foreach($changePrice068 in $planPrice068.changes){
        $targetPrice068=Resolve-PriceTarget068 $changePrice068.path
        $beforePrice068=[IO.File]::ReadAllBytes($targetPrice068)
        if((Get-PriceHash068 $beforePrice068) -cne $changePrice068.preHash){throw 'Exact file preimage conflict.'}
        $afterPrice068=Get-PriceBytes068 $beforePrice068 @($changePrice068.offers) $changePrice068.offers[0].kind
        if((Get-PriceHash068 $afterPrice068) -cne $changePrice068.postHash){throw 'Recomputed exact price payload differs from the approved plan.'}
        $backupPathPrice068=Join-Path $backupPrice068 $changePrice068.path
        New-Item -ItemType Directory -Path (Split-Path $backupPathPrice068 -Parent) -Force|Out-Null
        if(!$existingBackupPrice068){[IO.File]::WriteAllBytes($backupPathPrice068,$beforePrice068)}
        if((Get-PriceFileHash068 $backupPathPrice068) -cne $changePrice068.preHash){throw 'Backup hash differs.'}
        $preparedPrice068+=@{target=$targetPrice068;before=$beforePrice068;after=$afterPrice068;preHash=$changePrice068.preHash;postHash=$changePrice068.postHash}
    }
    $committedPrice068=@()
    try{
        foreach($preparedEntryPrice068 in $preparedPrice068){
            if((Get-PriceFileHash068 $preparedEntryPrice068.target) -cne $preparedEntryPrice068.preHash){throw 'Concurrent preimage drift before atomic Apply.'}
            Write-AtomicPrice068 $preparedEntryPrice068.target $preparedEntryPrice068.after
            $committedPrice068+=$preparedEntryPrice068
        }
        $receiptPrice068=[ordered]@{owner='TASK068_LIVING_ECONOMY';gameRoot=$gamePrice068;planHash=$ExpectedPlanHash.ToLowerInvariant();backupRoot=$backupPrice068;files=@($planPrice068.changes|Select-Object path,preHash,postHash)}
        [IO.File]::WriteAllText($receiptPathPrice068,($receiptPrice068|ConvertTo-Json -Depth 10),$utf8Price068)
    }catch{
        foreach($committedEntryPrice068 in $committedPrice068){if((Get-PriceFileHash068 $committedEntryPrice068.target) -ceq $committedEntryPrice068.postHash){Write-AtomicPrice068 $committedEntryPrice068.target $committedEntryPrice068.before}}
        throw
    }
    Write-Output ('APPLIED files='+$preparedPrice068.Count+';receipt='+$receiptPathPrice068+';restart required.');return
}
if(!(Test-Path -LiteralPath $receiptPathPrice068)){throw 'Rollback requires the exact completed Apply receipt.'}
$receiptPrice068=[IO.File]::ReadAllText($receiptPathPrice068,$utf8Price068)|ConvertFrom-Json
if($receiptPrice068.planHash -cne $ExpectedPlanHash.ToLowerInvariant() -or $receiptPrice068.owner -cne 'TASK068_LIVING_ECONOMY' -or $receiptPrice068.gameRoot -cne $gamePrice068){throw 'Rollback receipt owner/root/hash mismatch.'}
if((Assert-PlainPrice068 $receiptPrice068.backupRoot) -cne (Join-Path $outputPrice068 ('backup-'+$ExpectedPlanHash.ToLowerInvariant())) -or @($receiptPrice068.files).Count -ne @($planPrice068.changes).Count){throw 'Rollback receipt differs from the approved plan scope.'}
$rollbackPrice068=@()
foreach($filePrice068 in $receiptPrice068.files){
    $approvedPrice068=@($planPrice068.changes|Where-Object {$_.path -ceq $filePrice068.path -and $_.preHash -ceq $filePrice068.preHash -and $_.postHash -ceq $filePrice068.postHash})
    if($approvedPrice068.Count -ne 1){throw 'Rollback receipt contains an unapproved path/hash.'}
    $targetPrice068=Resolve-PriceTarget068 $filePrice068.path
    if((Get-PriceFileHash068 $targetPrice068) -cne $filePrice068.postHash){throw 'Rollback conflict: a later change is preserved.'}
    $backupPathPrice068=Assert-PlainPrice068 (Join-Path $receiptPrice068.backupRoot $filePrice068.path)
    if(!$backupPathPrice068.StartsWith((Assert-PlainPrice068 $receiptPrice068.backupRoot)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-PriceFileHash068 $backupPathPrice068) -cne $filePrice068.preHash){throw 'Rollback backup boundary/hash failed.'}
    $rollbackPrice068+=@{target=$targetPrice068;postHash=$filePrice068.postHash;bytes=[IO.File]::ReadAllBytes($backupPathPrice068)}
}
foreach($entryPrice068 in $rollbackPrice068){
    if((Get-PriceFileHash068 $entryPrice068.target) -cne $entryPrice068.postHash){throw 'Concurrent later change preserved during rollback.'}
    Write-AtomicPrice068 $entryPrice068.target $entryPrice068.bytes
}
Write-Output ('ROLLED_BACK files='+$rollbackPrice068.Count+';original bytes restored. Receipt retained for audit.')
