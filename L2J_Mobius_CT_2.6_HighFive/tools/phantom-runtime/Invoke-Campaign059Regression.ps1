[CmdletBinding()]
param([ValidatePattern('^[a-z0-9-]{1,45}$')][string]$Label='final-current059',[switch]$ValidateOnly,[switch]$Campaign060,[switch]$Campaign061)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if($hf059 -ine 'D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'){throw 'Exact OWN High Five required'}
if($Campaign060 -and $Campaign061){throw 'One exact campaign required'}
$frozen059=Join-Path $hf059 $(if($Campaign061){'.phantom-local/contract058a/task060/final-current060-r2.tsv'}else{'.phantom-local/contract058a/final-selected058b.tsv'})
$expectedHash059=if($Campaign061){'40751731BE90EEF4DAC76C055AF28B98D95D1D825B106F8AB77AE9F6B83C890F'}else{'0A1BB702F06047C6A36967799B7CD01FA6EC55DE49583DA85BAB7C1B664332A0'}
if((Get-FileHash -LiteralPath $frozen059).Hash -cne $expectedHash059){throw 'Historical selection changed'}
$selection059=@(Import-Csv -LiteralPath $frozen059 -Delimiter "`t")
if($Campaign061){foreach($row059 in $selection059){$row059 | Add-Member -NotePropertyName caseResults -NotePropertyValue ([int]$row059.total)}}
$expectedCases059=if($Campaign061){111}else{101}
if($selection059.Count -ne 23 -or ($selection059 | Measure-Object -Property caseResults -Sum).Sum -ne $expectedCases059){throw 'Historical baseline count changed'}
function Baseline-Report059($Row){
 $path059=if([IO.Path]::IsPathRooted($Row.proof)){$Row.proof}else{Join-Path $hf059 $Row.proof}
 if($Campaign061 -and !([IO.Path]::GetFullPath($path059).StartsWith((Join-Path $hf059 '.phantom-local/contract058t/reports/task060')+'\',[StringComparison]::OrdinalIgnoreCase))){throw 'Wrong frozen report root'}
 return [IO.File]::ReadAllText($path059)
}
foreach($row059 in $selection059){
 $old059=Baseline-Report059 $row059
 if([regex]::Matches($old059,'(?m)^PASS ([^ ]+) ').Count -ne [int]$row059.caseResults){throw 'Historical report case inventory differs'}
}
if($ValidateOnly){Write-Output ('CURRENT_SELECTION_PREFLIGHT_PASS; suites=23; originalCaseIds='+$expectedCases059+'; currentTestsNotRun=true');return}
$output059=Join-Path $hf059 ($(if($Campaign061){'.phantom-local/contract058a/task061/'}elseif($Campaign060){'.phantom-local/contract058a/task060/'}else{'.phantom-local/contract058a/task059/'})+$Label+'.tsv')
if(Test-Path -LiteralPath $output059){throw 'Current evidence exists; no overwrite'}
Set-Content -LiteralPath $output059 -Encoding UTF8 -Value "mode`tseed`ttotal`tpassed`tfailed`texit`tretainedBaselineCases`tproof"
$runner059=Join-Path $PSScriptRoot 'Invoke-Campaign059Test.ps1'
$cwd059=Join-Path $hf059 '.phantom-local/contract058t/runtime/game'
$failures059=0
$campaignArgs060=@(); if($Campaign060){$campaignArgs060+='-Campaign060'}
if($Campaign061){$campaignArgs060=@('-Campaign061','-Frozen061')}else{$campaignArgs060+='-CleanCandidate'}
Push-Location $cwd059
try {
 foreach($row059 in $selection059){
  $caseLabel059=$Label+'-'+$row059.mode
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $runner059 -Mode $row059.mode -Seed $row059.seed -Label $caseLabel059 @campaignArgs060
  $exit059=$LASTEXITCODE
  $directory059=Join-Path $hf059 ($(if($Campaign061){'.phantom-local/contract058t/reports/task061/'}elseif($Campaign060){'.phantom-local/contract058t/reports/task060/'}else{'.phantom-local/contract058t/reports/task059/'})+$caseLabel059)
  $reports059=@(Get-ChildItem -LiteralPath $directory059 -File -Filter '*.txt')
  if($reports059.Count -ne 1){throw 'Expected one current launcher report'}
  $text059=[IO.File]::ReadAllText($reports059[0].FullName)
  $old059=Baseline-Report059 $row059
  $oldIds059=@([regex]::Matches($old059,'(?m)^PASS ([^ ]+) ') | ForEach-Object {$_.Groups[1].Value})
  $newIds059=@([regex]::Matches($text059,'(?m)^(?:PASS|FAIL) ([^ ]+) ') | ForEach-Object {$_.Groups[1].Value})
  if($oldIds059.Count -ne [int]$row059.caseResults -or @($oldIds059 | Where-Object {$_ -cnotin $newIds059}).Count){throw 'Historical requirement/case identity disappeared'}
  $counts059=@{}
  foreach($key059 in @('total','passed','failed')){
   $match059=[regex]::Match($text059,'(?m)^'+$key059+'=([0-9]+)\r?$')
   if(!$match059.Success){throw 'Missing native launcher result count'}
   $counts059[$key059]=[int]$match059.Groups[1].Value
  }
  Add-Content -LiteralPath $output059 -Encoding UTF8 -Value (@($row059.mode,$row059.seed,$counts059.total,$counts059.passed,$counts059.failed,$exit059,$oldIds059.Count,$reports059[0].FullName) -join "`t")
  if($exit059 -ne 0 -or $counts059.failed -ne 0){$failures059++}
 }
} finally {Pop-Location}
Write-Output ($(if($Campaign061){'CURRENT061_REGRESSION suites='}elseif($Campaign060){'CURRENT060_REGRESSION suites='}else{'CURRENT059_REGRESSION suites='})+$selection059.Count+'; baselineCases='+$expectedCases059+'; failedSuites='+$failures059+'; proof='+$output059)
if($failures059){exit 1}
