param([string]$FrozenSha='80e7bdd346c24faf2153dcf04f5d0adf7a7e06ae')
$ErrorActionPreference='Stop'
$module044=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$receipt044=Join-Path $PSScriptRoot 'evidence/DIAGNOSTIC_ADDITIONAL_INPUT044.json'
if(Test-Path $receipt044){throw 'Additional diagnostic already enrolled; no retry.'}
$approval044=Join-Path $PSScriptRoot 'evidence/ADDITIONAL_TRACE_AUTH044.json'
$approval=Get-Content -LiteralPath $approval044 -Raw | ConvertFrom-Json
if($approval.contract -cne 'ONE_ADDITIONAL_SOURCE_ONLY044' -or $approval.fullG4 -ne $false -or [DateTime]::UtcNow -ge [DateTime]::Parse('2026-10-14T12:36:00Z').ToUniversalTime()){throw 'Additional source-only authorization/deadline required.'}
$names044=@('Contract032Observer.java','Observe032.ps1','Control032.ps1','Stop-ExactOwnedGracefully.ps1','Select-Primary044.ps1')
$pins044=@(foreach($name044 in $names044){$path044=Join-Path $module044 ".phantom-local/ops044/$name044"; @{path=$path044;sha256=(Get-FileHash $path044).Hash}})
@{contract='ONE_ADDITIONAL_SOURCE_ONLY044';utc=[DateTime]::UtcNow.ToString('O');sourceSha=$FrozenSha;authorizationSha256=(Get-FileHash $approval044).Hash;selection='lowest living ordinary profile with existing native cycle/reward/damage; no current ecology requirement';pins=$pins044;fullG4=$false;allowedAdditionalDiagnosticCount=1} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receipt044 -Encoding utf8
$previous044=$env:PHANTOM_TASK044_DIAGNOSTIC
try{
    $env:PHANTOM_TASK044_DIAGNOSTIC='true'
    & (Join-Path $PSScriptRoot 'Run-Life044.ps1') -Episode a -Label DIAG_A2_SOURCE044 -FrozenSha $FrozenSha -PathJson (Join-Path $PSScriptRoot 'evidence/G4_DRY044/path.json') -InitialPreparationSeconds 90 -LifeCurrentReady -PreconditionsFile (Join-Path $PSScriptRoot 'PRECONDITIONS_SOURCE044a.md')
}finally{$env:PHANTOM_TASK044_DIAGNOSTIC=$previous044}
