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
Write-Output ('SUMMARY CLI smoke=3/3; output='+$rootPriceTest068)
