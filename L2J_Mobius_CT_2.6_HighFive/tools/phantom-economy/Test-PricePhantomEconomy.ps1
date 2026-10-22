[CmdletBinding()]
param([Parameter(Mandatory)][string]$GameRoot,[Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$JavaClassPath,[switch]$ResumePreparedFixture)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$toolPriceTest068=Join-Path $PSScriptRoot 'Price-PhantomEconomy.ps1'
$rootPriceTest068=[IO.Path]::GetFullPath($OutputDirectory)
if((Test-Path -LiteralPath $rootPriceTest068) -and !$ResumePreparedFixture){throw 'Fresh own068 pricing smoke output required; existing evidence is preserved.'}
if(!(Test-Path -LiteralPath $rootPriceTest068)){New-Item -ItemType Directory -Path $rootPriceTest068|Out-Null}
$encodingPriceTest068=New-Object Text.UTF8Encoding($false,$true)
function Invoke-PriceSmoke068([string[]]$Arguments,[int]$ExpectedExit=0){
    & powershell -NoProfile -ExecutionPolicy Bypass -File $toolPriceTest068 @Arguments
    $actualPriceTest068=$LASTEXITCODE
    Write-Output "CLI exit=$actualPriceTest068 expected_class=$ExpectedExit"
    if(($ExpectedExit -eq 0 -and $actualPriceTest068 -ne 0) -or ($ExpectedExit -ne 0 -and $actualPriceTest068 -eq 0)){throw "Pricing smoke command exit=$actualPriceTest068; expected class=$ExpectedExit"}
}
function Hash-PriceSmoke068([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
$sourcePriceTest068=[IO.Path]::GetFullPath($GameRoot)
$firstPriceTest068=Join-Path $rootPriceTest068 'source-first'
$secondPriceTest068=Join-Path $rootPriceTest068 'source-repeat'
if(!$ResumePreparedFixture){
    Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$sourcePriceTest068,'-OutputDirectory',$firstPriceTest068,'-JavaClassPath',$JavaClassPath)
    Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$sourcePriceTest068,'-OutputDirectory',$secondPriceTest068,'-JavaClassPath',$JavaClassPath)
}
if((Hash-PriceSmoke068 (Join-Path $firstPriceTest068 'plan.json')) -cne (Hash-PriceSmoke068 (Join-Path $secondPriceTest068 'plan.json'))){throw 'Identical source inputs did not produce identical plan bytes/hash.'}
Write-Output 'PASS 01 deterministic source Plan and source hash manifest.'

$fixturePriceTest068=Join-Path $rootPriceTest068 'owned-fixture/game'
New-Item -ItemType Directory -Path $fixturePriceTest068 -Force|Out-Null
if(!$ResumePreparedFixture){
foreach($sourceRowPriceTest068 in @(Import-Csv -LiteralPath (Join-Path $firstPriceTest068 'facts/sources.tsv') -Delimiter "`t")){
    if($sourceRowPriceTest068.path.StartsWith('@')){continue}
    $targetPriceTest068=Join-Path $fixturePriceTest068 $sourceRowPriceTest068.path
    New-Item -ItemType Directory -Path (Split-Path $targetPriceTest068 -Parent) -Force|Out-Null
    Copy-Item -LiteralPath (Join-Path $sourcePriceTest068 $sourceRowPriceTest068.path) -Destination $targetPriceTest068
    if((Hash-PriceSmoke068 $targetPriceTest068) -cne $sourceRowPriceTest068.sha256){throw 'Fixture physical copy/hash differs.'}
}
}
$ownershipPriceTest068=Join-Path $fixturePriceTest068 'OWNERSHIP068.json'
if(!$ResumePreparedFixture){[IO.File]::WriteAllText($ownershipPriceTest068,([ordered]@{owner='TASK068_LIVING_ECONOMY';gameRoot=$fixturePriceTest068;allowPriceApply=$true}|ConvertTo-Json),$encodingPriceTest068)}
$fixturePlanPriceTest068=Join-Path $rootPriceTest068 'fixture-plan'
if(!$ResumePreparedFixture){Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$fixturePlanPriceTest068,'-JavaClassPath',$JavaClassPath)}
$planPathPriceTest068=Join-Path $fixturePlanPriceTest068 'plan.json'
$planHashPriceTest068=Hash-PriceSmoke068 $planPathPriceTest068
$planPriceTest068=[IO.File]::ReadAllText($planPathPriceTest068,$encodingPriceTest068)|ConvertFrom-Json
if(@($planPriceTest068.changes).Count -lt 1){throw 'Fixture did not contain a real supported price change.'}
$firstChangePriceTest068=$planPriceTest068.changes[0]
$changedPathPriceTest068=Join-Path $fixturePriceTest068 $firstChangePriceTest068.path
$originalPriceTest068=[IO.File]::ReadAllBytes($changedPathPriceTest068)
[IO.File]::AppendAllText($changedPathPriceTest068,"`n<!-- own068 preimage conflict TEST -->",$encodingPriceTest068)
$conflictHashPriceTest068=Hash-PriceSmoke068 $changedPathPriceTest068
$applyOutputPriceTest068=Join-Path $rootPriceTest068 'fixture-apply'
$applyArgsPriceTest068=@('-Mode','Apply','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$applyOutputPriceTest068,'-PlanPath',$planPathPriceTest068,'-ExpectedPlanHash',$planHashPriceTest068,'-OwnershipManifest',$ownershipPriceTest068)
Invoke-PriceSmoke068 -Arguments $applyArgsPriceTest068 -ExpectedExit 1
if((Hash-PriceSmoke068 $changedPathPriceTest068) -cne $conflictHashPriceTest068){throw 'Apply overwrote a preimage conflict.'}
[IO.File]::WriteAllBytes($changedPathPriceTest068,$originalPriceTest068)
Invoke-PriceSmoke068 -Arguments $applyArgsPriceTest068
Invoke-PriceSmoke068 -Arguments $applyArgsPriceTest068
foreach($changePriceTest068 in $planPriceTest068.changes){if((Hash-PriceSmoke068 (Join-Path $fixturePriceTest068 $changePriceTest068.path)) -cne $changePriceTest068.postHash){throw 'Applied bytes differ from approved postimage.'}}
Write-Output 'PASS 02 preimage conflict preserved; exact Apply, backup and idempotent retry.'

$appliedPriceTest068=[IO.File]::ReadAllBytes($changedPathPriceTest068)
[IO.File]::AppendAllText($changedPathPriceTest068,"`n<!-- own068 later change TEST -->",$encodingPriceTest068)
$laterHashPriceTest068=Hash-PriceSmoke068 $changedPathPriceTest068
$rollbackArgsPriceTest068=@('-Mode','Rollback','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$applyOutputPriceTest068,'-PlanPath',$planPathPriceTest068,'-ExpectedPlanHash',$planHashPriceTest068,'-OwnershipManifest',$ownershipPriceTest068)
Invoke-PriceSmoke068 -Arguments $rollbackArgsPriceTest068 -ExpectedExit 1
if((Hash-PriceSmoke068 $changedPathPriceTest068) -cne $laterHashPriceTest068){throw 'Rollback overwrote a later change.'}
[IO.File]::WriteAllBytes($changedPathPriceTest068,$appliedPriceTest068)
Invoke-PriceSmoke068 -Arguments $rollbackArgsPriceTest068
foreach($changePriceTest068 in $planPriceTest068.changes){if((Hash-PriceSmoke068 (Join-Path $fixturePriceTest068 $changePriceTest068.path)) -cne $changePriceTest068.preHash){throw 'Rollback did not restore original bytes.'}}
foreach($sourceRowPriceTest068 in @(Import-Csv -LiteralPath (Join-Path $firstPriceTest068 'facts/sources.tsv') -Delimiter "`t")){
    if(!$sourceRowPriceTest068.path.StartsWith('@') -and (Hash-PriceSmoke068 (Join-Path $sourcePriceTest068 $sourceRowPriceTest068.path)) -cne $sourceRowPriceTest068.sha256){throw 'Read-only source changed during CLI smoke.'}
}
Write-Output 'PASS 03 later-change rollback conflict preserved; exact rollback restored; source unchanged.'

# Two actual Rates.ini profiles; canonical prices never come from a previous Apply.
$baselinePriceTest068=Join-Path $rootPriceTest068 'canonical-baseline'
Invoke-PriceSmoke068 -Arguments @('-Mode','Baseline','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$baselinePriceTest068,'-JavaClassPath',$JavaClassPath)
$baselineHashPriceTest068=Hash-PriceSmoke068 (Join-Path $baselinePriceTest068 'baseline.properties')
$ratesPathPriceTest068=Join-Path $fixturePriceTest068 'config/Rates.ini'
$originalRatesPriceTest068=[IO.File]::ReadAllBytes($ratesPathPriceTest068)
function Set-RebalanceRates068([int]$Scale){
    $textPriceTest068=$encodingPriceTest068.GetString($originalRatesPriceTest068)
    $chancePriceTest068=if($Scale -eq 10){2}else{3}
    foreach($keyPriceTest068 in @('DeathDropAmountMultiplier','SpoilDropAmountMultiplier','RaidDropAmountMultiplier')){
        $textPriceTest068=[regex]::Replace($textPriceTest068,'(?m)^('+ $keyPriceTest068 +'\s*=\s*).*$',{param($match) $match.Groups[1].Value+[string]$Scale})
    }
    foreach($keyPriceTest068 in @('DeathDropChanceMultiplier','SpoilDropChanceMultiplier','RaidDropChanceMultiplier')){
        $textPriceTest068=[regex]::Replace($textPriceTest068,'(?m)^('+ $keyPriceTest068 +'\s*=\s*).*$',{param($match) $match.Groups[1].Value+[string]$chancePriceTest068})
    }
    $textPriceTest068=[regex]::Replace($textPriceTest068,'(?m)^(DropAmountMultiplierByItemId\s*=\s*)([^\r\n]*)',{param($match) $match.Groups[1].Value+([regex]::Replace($match.Groups[2].Value,'(^|;)57,[^;]*',{param($entry) $entry.Groups[1].Value+'57,'+[string]$Scale}))})
    [IO.File]::WriteAllText($ratesPathPriceTest068,$textPriceTest068,$encodingPriceTest068)
}
$cycleReceiptsPriceTest068=@()
try{
    foreach($namePriceTest068 in @('x10-first','x20','x10-repeat')){
        $scalePriceTest068=if($namePriceTest068 -eq 'x20'){20}else{10}
        Set-RebalanceRates068 $scalePriceTest068
        $directoryPriceTest068=Join-Path $rootPriceTest068 $namePriceTest068
        Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$directoryPriceTest068,'-JavaClassPath',$JavaClassPath,'-BaselineDirectory',$baselinePriceTest068)
        $effectivePriceTest068=@(Import-Csv -LiteralPath (Join-Path $directoryPriceTest068 'facts/rates.tsv') -Delimiter "`t")
        $expectedChancePriceTest068=if($scalePriceTest068 -eq 10){2}else{3}
        if([double]($effectivePriceTest068|Where-Object key -eq 'adenaAmount').value -ne $scalePriceTest068 -or [double]($effectivePriceTest068|Where-Object key -eq 'deathAmount').value -ne $scalePriceTest068 -or [double]($effectivePriceTest068|Where-Object key -eq 'spoilAmount').value -ne $scalePriceTest068 -or [double]($effectivePriceTest068|Where-Object key -eq 'deathChance').value -ne $expectedChancePriceTest068 -or [double]($effectivePriceTest068|Where-Object key -eq 'spoilChance').value -ne $expectedChancePriceTest068 -or [double]($effectivePriceTest068|Where-Object key -eq 'raidChance').value -ne $expectedChancePriceTest068){throw 'Actual per-item57/drop/spoil chance+amount snapshot was not read.'}
        $cyclePlanPriceTest068=Join-Path $directoryPriceTest068 'plan.json'
        $cycleHashPriceTest068=Hash-PriceSmoke068 $cyclePlanPriceTest068
        $cycleOutputPriceTest068=Join-Path $directoryPriceTest068 'apply'
        Invoke-PriceSmoke068 -Arguments @('-Mode','Apply','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$cycleOutputPriceTest068,'-PlanPath',$cyclePlanPriceTest068,'-ExpectedPlanHash',$cycleHashPriceTest068,'-OwnershipManifest',$ownershipPriceTest068)
        $cycleReceiptsPriceTest068+=@{plan=$cyclePlanPriceTest068;hash=$cycleHashPriceTest068;output=$cycleOutputPriceTest068}
    }
    $x10PriceTest068=Join-Path $rootPriceTest068 'x10-first'
    $x20PriceTest068=Join-Path $rootPriceTest068 'x20'
    $repeatPriceTest068=Join-Path $rootPriceTest068 'x10-repeat'
    foreach($relativePriceTest068 in @('target-prices.tsv','facts/quotes.tsv')){
        if((Hash-PriceSmoke068 (Join-Path $x10PriceTest068 $relativePriceTest068)) -cne (Hash-PriceSmoke068 (Join-Path $repeatPriceTest068 $relativePriceTest068))){throw 'x10->x20->x10 compounded target prices or valuation.'}
    }
    if((Hash-PriceSmoke068 (Join-Path $x10PriceTest068 'target-prices.tsv')) -ceq (Hash-PriceSmoke068 (Join-Path $x20PriceTest068 'target-prices.tsv'))){throw 'Actual x10/x20 profiles did not change any reachable target price.'}
    if((Hash-PriceSmoke068 (Join-Path $baselinePriceTest068 'baseline.properties')) -cne $baselineHashPriceTest068){throw 'Canonical capture was rewritten by rebalance.'}
    # A missing57 override must inherit the actual global death amount, exactly as the native rates.
    Set-RebalanceRates068 10
    $without57PriceTest068=[regex]::Replace([IO.File]::ReadAllText($ratesPathPriceTest068),'(?m)^(DropAmountMultiplierByItemId\s*=\s*)57,[^;]*;?','$1')
    $without57PriceTest068=[regex]::Replace($without57PriceTest068,'(?m)^(RateXp\s*=\s*).*$', '${1}999')
    $without57PriceTest068=[regex]::Replace($without57PriceTest068,'(?m)^(RateSp\s*=\s*).*$', '${1}777')
    [IO.File]::WriteAllText($ratesPathPriceTest068,$without57PriceTest068,$encodingPriceTest068)
    $inheritPriceTest068=Join-Path $rootPriceTest068 'x10-inherit57'
    Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$inheritPriceTest068,'-JavaClassPath',$JavaClassPath,'-BaselineDirectory',$baselinePriceTest068)
    if((Hash-PriceSmoke068 (Join-Path $x10PriceTest068 'target-prices.tsv')) -cne (Hash-PriceSmoke068 (Join-Path $inheritPriceTest068 'target-prices.tsv'))){throw 'Adena missing override did not inherit the native death amount.'}
    $raidInheritTextPriceTest068=[regex]::Replace($without57PriceTest068,'(?m)^(RaidDropAmountMultiplier\s*=\s*).*$', '${1}20')
    [IO.File]::WriteAllText($ratesPathPriceTest068,$raidInheritTextPriceTest068,$encodingPriceTest068)
    $raidInheritPriceTest068=Join-Path $rootPriceTest068 'x10-raid-inherit57'
    Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$raidInheritPriceTest068,'-JavaClassPath',$JavaClassPath,'-BaselineDirectory',$baselinePriceTest068)
    $raidEffectivePriceTest068=@(Import-Csv -LiteralPath (Join-Path $raidInheritPriceTest068 'facts/rates.tsv') -Delimiter "`t")
    if([double]($raidEffectivePriceTest068|Where-Object key -eq 'adenaAmount').value -ne 10 -or [double]($raidEffectivePriceTest068|Where-Object key -eq 'raidAdenaAmount').value -ne 20){throw 'Missing57 did not inherit separate native death/raid amount rates.'}
    $canonicalRowPriceTest068=Import-Csv -LiteralPath (Join-Path $baselinePriceTest068 'files.tsv') -Delimiter "`t"|Where-Object {$_.path -like 'data/multisell/*'}|Select-Object -First 1
    $canonicalXmlPriceTest068=Join-Path $baselinePriceTest068 $canonicalRowPriceTest068.path
    $canonicalBytesPriceTest068=[IO.File]::ReadAllBytes($canonicalXmlPriceTest068)
    try{
        [IO.File]::AppendAllText($canonicalXmlPriceTest068,"`n<!-- own068 canonical drift TEST -->",$encodingPriceTest068)
        Invoke-PriceSmoke068 -Arguments @('-Mode','Plan','-GameRoot',$fixturePriceTest068,'-OutputDirectory',(Join-Path $rootPriceTest068 'baseline-conflict'),'-JavaClassPath',$JavaClassPath,'-BaselineDirectory',$baselinePriceTest068) -ExpectedExit 1
    }finally{[IO.File]::WriteAllBytes($canonicalXmlPriceTest068,$canonicalBytesPriceTest068)}
    Write-Output 'PASS 04 actual Rates.ini x10->x20->x10 identical target/quote bytes; canonical baseline retained/conflict protected; native missing57 inheritance; XP/SP ignored.'
}finally{
    try{
        [array]::Reverse($cycleReceiptsPriceTest068)
        foreach($cyclePriceTest068 in $cycleReceiptsPriceTest068){Invoke-PriceSmoke068 -Arguments @('-Mode','Rollback','-GameRoot',$fixturePriceTest068,'-OutputDirectory',$cyclePriceTest068.output,'-PlanPath',$cyclePriceTest068.plan,'-ExpectedPlanHash',$cyclePriceTest068.hash,'-OwnershipManifest',$ownershipPriceTest068)}
    }finally{[IO.File]::WriteAllBytes($ratesPathPriceTest068,$originalRatesPriceTest068)}
}
Write-Output ('SUMMARY CLI smoke=4/4; output='+$rootPriceTest068)
