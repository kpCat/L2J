param([string]$FrozenSha='80e7bdd346c24faf2153dcf04f5d0adf7a7e06ae')
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$receipt=Join-Path $PSScriptRoot 'evidence/DIAGNOSTIC_INPUT044.json'
if(Test-Path $receipt){throw 'One diagnostic maximum; input already enrolled'}
$paths=@('Contract032Observer.java','Observe032.ps1','Control032.ps1','Stop-ExactOwnedGracefully.ps1')
$pins=@(foreach($name in $paths){$path=Join-Path $module ".phantom-local/ops044/$name"; @{path=$path;sha256=(Get-FileHash $path).Hash}})
@{contract='ONE_ORDINARY_NATIVE_DIAGNOSTIC044';utc=[DateTime]::UtcNow.ToString('O');sourceSha=$FrozenSha;path='evidence/G4_DRY044/path.json';selection='lowest living current-ready ordinary profile before outcome';pins=$pins;fullG4=$false;allowedNativeDiagnosticCount=1} | ConvertTo-Json -Depth 6 | Set-Content $receipt -Encoding utf8
$previous=$env:PHANTOM_TASK044_DIAGNOSTIC
try{
 $env:PHANTOM_TASK044_DIAGNOSTIC='true'
 & (Join-Path $PSScriptRoot 'Run-Life044.ps1') -Episode a -Label DIAG_A1_044 -FrozenSha $FrozenSha -PathJson (Join-Path $PSScriptRoot 'evidence/G4_DRY044/path.json') -InitialPreparationSeconds 90 -LifeCurrentReady -PreconditionsFile (Join-Path $PSScriptRoot 'PRECONDITIONS044.md')
}finally{$env:PHANTOM_TASK044_DIAGNOSTIC=$previous}
