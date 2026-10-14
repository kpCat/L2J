param()
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../..'))
$ops=Join-Path $module '.phantom-local/ops044'
$previous=Join-Path $module '.phantom-local/ops043'
$task=Split-Path $PSScriptRoot -Parent
$oldTask=Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-SOFT-HANDOFF-043'
$mapping=@{
 'Prepare032.ps1'='Prepare032.ps1'; 'Control032.ps1'='Control032.ps1';
 'Observe032.ps1'='Observe032.ps1'; 'Contract032Observer.java'='Contract032Observer.java';
 'Read-SharedJson032.ps1'='Read-SharedJson032.ps1'; 'ReadDryRoute032.java'='ReadDryRoute032.java';
 'ReadDryPath043.java'='ReadDryPath044.java'; 'Select-Primary043.ps1'='Select-Primary044.ps1';
 'All-Birth-Cut043.ps1'='All-Birth-Cut044.ps1';
 'Build-StockShutdown043.ps1'='Build-StockShutdown044.ps1';
 'Stop-ExactOwnedGracefully.ps1'='Stop-ExactOwnedGracefully.ps1'
}
$utf8=[Text.UTF8Encoding]::new($false)
foreach($name in $mapping.Keys){
 $output=Join-Path $ops $mapping[$name]
 if(Test-Path -LiteralPath $output){throw "Immutable044 helper exists:$output"}
 $text=[IO.File]::ReadAllText((Join-Path $previous $name)).Replace('PHANTOM-M1-SOFT-HANDOFF-043','PHANTOM-M1-G4-CAUSAL-044').Replace('043','044')
 if($name -ceq 'Prepare032.ps1'){
  $text=$text.Replace('7795+','7796+').Replace('2125+','2126+').Replace('9035+','9036+')
 }
 [IO.File]::WriteAllText($output,$text,$utf8)
}
foreach($name in @('Verify043.ps1','Run-Life043.ps1','Check-LifeBoundary043.ps1','Verify-Life043.py','Decode-LifeReceipts043.java')){
 $output=Join-Path $task $name.Replace('043','044')
 if(Test-Path -LiteralPath $output){throw "Immutable044 runner exists:$output"}
 $text=[IO.File]::ReadAllText((Join-Path $oldTask $name)).Replace('PHANTOM-M1-SOFT-HANDOFF-043','PHANTOM-M1-G4-CAUSAL-044').Replace('043','044')
 $text=$text.Replace('2026-10-14T02:35:55Z','2026-10-14T03:59:13Z')
 [IO.File]::WriteAllText($output,$text,$utf8)
}
Write-Output 'NORMAL044_HELPERS_COPIED; protected043 unchanged'
