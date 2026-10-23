[CmdletBinding()]
param([Parameter(Mandatory)][string]$GameRoot,[Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$JavaClassPath,[Parameter(Mandatory)][string]$SourceManifest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$tool070=Join-Path $PSScriptRoot 'Price-PhantomEconomy070.ps1'
$encoding070=[Text.UTF8Encoding]::new($false,$true)
$root070=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $root070){throw 'Fresh own070 pricing evidence directory required.'}
$fixture070=Join-Path $root070 'own070/game'
New-Item -ItemType Directory -Path $fixture070 -Force|Out-Null
function HashTest070([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function RunTest070([string[]]$Arguments,[bool]$Failure=$false){
    & powershell -NoProfile -ExecutionPolicy Bypass -File $tool070 @Arguments
    if(($LASTEXITCODE -eq 0) -eq $Failure){throw "CLI070 unexpected exit=$LASTEXITCODE; expectedFailure=$Failure"}
}
$sources070=@(Import-Csv -LiteralPath $SourceManifest -Delimiter "`t"|Where-Object {!$_.path.StartsWith('@')})
$originalHashes070=@{}
foreach($source070 in $sources070){
    $from070=Join-Path $GameRoot $source070.path
    $target070=Join-Path $fixture070 $source070.path
    New-Item -ItemType Directory -Path (Split-Path $target070 -Parent) -Force|Out-Null
    Copy-Item -LiteralPath $from070 -Destination $target070
    $originalHashes070[$source070.path]=HashTest070 $from070
}
foreach($relative070 in @('data/economy070/price-policy070.properties','data/economy070/quest-contract070.tsv','data/economy070/pricing070.tsv','data/economy070/pricing070.sha256','data/multisell/91005.xml','data/multisell/91006.xml')){
    $target070=Join-Path $fixture070 $relative070
    New-Item -ItemType Directory -Path (Split-Path $target070 -Parent) -Force|Out-Null
    Copy-Item -LiteralPath (Join-Path $GameRoot $relative070) -Destination $target070 -Force
}
$ownership070=Join-Path $fixture070 'OWNERSHIP070.json'
[IO.File]::WriteAllText($ownership070,([ordered]@{owner='TASK070_ECONOMY_QOL';gameRoot=$fixture070;allowPriceApply=$true}|ConvertTo-Json),$encoding070)
$baseline070=Join-Path $root070 'canonical'
RunTest070 @('-Mode','Baseline','-GameRoot',$fixture070,'-OutputDirectory',$baseline070,'-JavaClassPath',$JavaClassPath)
$baselineHash070=HashTest070 (Join-Path $baseline070 'STORE_CATALOG070.tsv')
$ratesPath070=Join-Path $fixture070 'config/Rates.ini'
$originalRates070=[IO.File]::ReadAllBytes($ratesPath070)
$cycles070=@()
foreach($name070 in @('x1','x10-first','x20','x10-repeat')){
    $scale070=if($name070 -eq 'x1'){1}elseif($name070 -eq 'x20'){20}else{10}
    $rates070=$encoding070.GetString($originalRates070)
    $rates070=[regex]::Replace($rates070,'(?m)^(DropAmountMultiplierByItemId\s*=\s*)([^\r\n]*)',{param($match) $match.Groups[1].Value+([regex]::Replace($match.Groups[2].Value,'(^|;)57,[^;]*',{param($part) $part.Groups[1].Value+'57,'+[string]$scale070}))})
    [IO.File]::WriteAllText($ratesPath070,$rates070,$encoding070)
    $planRoot070=Join-Path $root070 $name070
    RunTest070 @('-Mode','Plan','-GameRoot',$fixture070,'-OutputDirectory',$planRoot070,'-BaselineDirectory',$baseline070,'-JavaClassPath',$JavaClassPath)
    $planPath070=Join-Path $planRoot070 'plan070.json'
    $planHash070=HashTest070 $planPath070
    $apply070=Join-Path $planRoot070 'apply'
    $args070=@('-Mode','Apply','-GameRoot',$fixture070,'-OutputDirectory',$apply070,'-PlanPath',$planPath070,'-ExpectedPlanHash',$planHash070,'-OwnershipManifest',$ownership070)
    if($name070 -eq 'x10-first'){
        $conflictPath070=Join-Path $fixture070 'data/multisell/custom/600024.xml'
        $before070=[IO.File]::ReadAllBytes($conflictPath070)
        [IO.File]::AppendAllText($conflictPath070,"`n<!-- TASK070 conflict control -->",$encoding070)
        $conflictHash070=HashTest070 $conflictPath070
        RunTest070 $args070 $true
        if((HashTest070 $conflictPath070) -cne $conflictHash070){throw 'Preimage conflict was overwritten.'}
        [IO.File]::WriteAllBytes($conflictPath070,$before070)
    }
    RunTest070 $args070
    RunTest070 $args070
    $prices070=@(Import-Csv -LiteralPath (Join-Path $fixture070 'data/economy070/pricing070.tsv') -Delimiter "`t")
    if([long]($prices070|Where-Object key -ceq 'premium.day').price -ne 1000000*$scale070 -or [long]($prices070|Where-Object key -ceq 'mana').price -ne 1000*$scale070){throw 'Premium/mana do not follow actual nonpremium Adena yield.'}
    [xml]$codex070=Get-Content -LiteralPath (Join-Path $fixture070 'data/multisell/91005.xml') -Raw
    if([long]$codex070.list.item[0].ingredient.count -ne 20000000){throw 'Adena-only rate change incorrectly repriced AA Codex.'}
    [xml]$epa070=Get-Content -LiteralPath (Join-Path $fixture070 'data/multisell/91006.xml') -Raw
    if($epa070.list.item.Count -ne 3 -or [long]$epa070.list.item[0].ingredient[0].count -ne 200 -or [long]$epa070.list.item[0].ingredient[1].count -ne 1000000*$scale070){throw 'Native dual currencies were not preserved.'}
    $cycles070+=@{plan=$planPath070;hash=$planHash070;apply=$apply070}
}
foreach($relative070 in @('payload/data/economy070/pricing070.tsv','payload/data/multisell/custom/600024.xml','payload/data/multisell/91005.xml','payload/data/multisell/91006.xml')){
    if((HashTest070 (Join-Path (Join-Path $root070 'x10-first') $relative070)) -cne (HashTest070 (Join-Path (Join-Path $root070 'x10-repeat') $relative070))){throw 'x10->x20->x10 compounded canonical price.'}
}
Write-Output 'PASS070 1/3 canonical x1/x10/x20/x10; premium/mana exact; AA independent; native dual costs.'
$last070=$cycles070[-1]
$changed070=Join-Path $fixture070 'data/economy070/pricing070.tsv'
$applied070=[IO.File]::ReadAllBytes($changed070)
[IO.File]::AppendAllText($changed070,"# late edit`n",$encoding070)
$laterHash070=HashTest070 $changed070
RunTest070 @('-Mode','Rollback','-GameRoot',$fixture070,'-OutputDirectory',$last070.apply,'-PlanPath',$last070.plan,'-ExpectedPlanHash',$last070.hash,'-OwnershipManifest',$ownership070) $true
if((HashTest070 $changed070) -cne $laterHash070){throw 'Rollback destroyed later operator edit.'}
[IO.File]::WriteAllBytes($changed070,$applied070)
[array]::Reverse($cycles070)
foreach($cycle070 in $cycles070){RunTest070 @('-Mode','Rollback','-GameRoot',$fixture070,'-OutputDirectory',$cycle070.apply,'-PlanPath',$cycle070.plan,'-ExpectedPlanHash',$cycle070.hash,'-OwnershipManifest',$ownership070)}
if((HashTest070 (Join-Path $baseline070 'STORE_CATALOG070.tsv')) -cne $baselineHash070){throw 'Canonical baseline changed.'}
foreach($file070 in (Get-Content -LiteralPath $cycles070[-1].plan -Raw|ConvertFrom-Json).files){if((HashTest070 (Join-Path $fixture070 $file070.path)) -cne $file070.preHash){throw 'Rollback did not restore byte-identical x1 preimages.'}}
Write-Output 'PASS070 2/3 preimage/later-edit conflicts, idempotent Apply and exact reverse Rollback.'
foreach($relative070 in $originalHashes070.Keys){if((HashTest070 (Join-Path $GameRoot $relative070)) -cne $originalHashes070[$relative070]){throw 'Source game changed during fixture test.'}}
[IO.File]::WriteAllBytes($ratesPath070,$originalRates070)
$lockPath070=Join-Path $fixture070 '.price070.lock'
[IO.File]::WriteAllText($lockPath070,'TEST stale mutation lock',$encoding070)
try{RunTest070 @('-Mode','Apply','-GameRoot',$fixture070,'-OutputDirectory',(Join-Path $root070 'locked-apply'),'-PlanPath',$cycles070[-1].plan,'-ExpectedPlanHash',$cycles070[-1].hash,'-OwnershipManifest',$ownership070) $true}finally{Remove-Item -LiteralPath $lockPath070}
Write-Output 'PASS070 3/3 mutation lock fail-closed; source preserved; no server/DB start.'
