[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,[ValidateSet('build','build-final')][string]$Build='build',[switch]$ValidateOnly)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$module064=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task064=Join-Path $module064 '.phantom-local/task064'
$baseline064=@(Import-Csv -LiteralPath (Join-Path $module064 'test/resources/phantoms/task064/baseline111.tsv') -Delimiter "`t")
$groups064=@($baseline064 | Group-Object mode)
if($baseline064.Count -ne 111 -or $groups064.Count -ne 23 -or @($baseline064 | Group-Object mode,caseId | Where-Object Count -ne 1).Count){throw 'Frozen111 identity inventory differs.'}
if($ValidateOnly){Write-Output 'TASK064_BASELINE_PREFLIGHT suites=23; caseIds=111; testsNotRun=true';return}
$output064=Join-Path $task064 ($Label+'.tsv')
if(Test-Path -LiteralPath $output064){throw 'Cumulative evidence exists; no overwrite.'}
"mode`tseed`ttotal`tpassed`tfailed`texit`tretainedBaselineCases`tproof" | Set-Content -LiteralPath $output064 -Encoding UTF8
$failed064=0
foreach($group064 in $groups064){
    $mode064=$group064.Name;$seed064=$group064.Group[0].seed;$episode064=$Label+'-'+$mode064
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'Invoke-Task064Test.ps1') -Mode $mode064 -Seed $seed064 -Label $episode064 -Build $Build
    $exit064=$LASTEXITCODE
    $proof064=Join-Path $task064 ("reports/$episode064/$mode064.txt")
    $text064=[IO.File]::ReadAllText($proof064)
    $ids064=@([regex]::Matches($text064,'(?m)^(?:PASS|FAIL) ([^ ]+) ') | ForEach-Object {$_.Groups[1].Value})
    if(@($group064.Group | Where-Object {$_.caseId -cnotin $ids064}).Count){throw "An original case disappeared: $mode064"}
    $counts064=@{};foreach($key064 in @('total','passed','failed')){
        $match064=[regex]::Match($text064,'(?m)^'+$key064+'=([0-9]+)\r?$');if(!$match064.Success){throw 'Missing launcher result count.'};$counts064[$key064]=[int]$match064.Groups[1].Value
    }
    (@($mode064,$seed064,$counts064.total,$counts064.passed,$counts064.failed,$exit064,$group064.Count,$proof064) -join "`t") | Add-Content -LiteralPath $output064 -Encoding UTF8
    if($exit064 -ne 0 -or $counts064.failed -ne 0){$failed064++}
}
Write-Output "TASK064_CUMULATIVE suites=23; originalCaseIds=111; failedSuites=$failed064; proof=$output064"
if($failed064){exit 1}
