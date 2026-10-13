param([Parameter(Mandatory)][string]$JarPath,[ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Candidate='FINAL')
$ErrorActionPreference='Stop'
$module041=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task041=Join-Path $module041 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041'
$runner041=Join-Path $task041 'Verify041.ps1'
$routes041=@(
 @{Suite='PhantomM1Closure041Suite';Label='FINAL_CLOSURE041'},
 @{Suite='PhantomObserverHandoff040Suite';Label='FINAL_OBSERVER041'},
 @{Suite='PhantomM1Closeout040Suite';Label='FINAL_ACTION041'},
 @{Suite='PhantomM1Closeout040Suite';Focus='resource040';Label='FINAL_RESOURCE041'},
 @{Suite='PhantomM1Closeout040Suite';Focus='stale040';Label='FINAL_STALE041'},
 @{Suite='LocalPlayPilotSuite';Label='FINAL_PILOT041'},
 @{Suite='PhantomLifecycleCompletion027Suite';Focus='acceptance';Label='FINAL_LIFECYCLE041'},
 @{Suite='PhantomNativeCastDrain036Suite';Label='FINAL_NATIVE036_041'},
 @{Suite='native';Focus='teardown';Label='FINAL_Q15_041'},
 @{Suite='PhantomInventoryCapture035Suite';Label='FINAL_INVENTORY035_041'},
 @{Suite='PhantomActionLoop038Suite';Focus='full038';Label='FINAL_ACTION038_041'},
 @{Suite='PhantomVisibleContinuity032Suite';Focus='continuity037';Label='FINAL_DEBT037_041'},
 @{Suite='PhantomFarmProjection030Suite';Label='FINAL_PROJECTION041'},
 @{Suite='PhantomFarmProjection030Suite';Focus='capacity031';Label='FINAL_CAPACITY041'},
 @{Suite='PhantomM1Campaign039Suite';Label='FINAL_A02_041'},
 @{Suite='PhantomM1Campaign039Suite';Focus='boundary039';Label='FINAL_BOUNDARY041'},
 @{Suite='PhantomM1Campaign039Suite';Focus='snapshot039';Label='FINAL_SNAPSHOT041'},
 @{Suite='PhantomM1Campaign039Suite';Focus='death039';Label='FINAL_DEATH_USEFUL041'}
)
foreach($route041 in $routes041){$route041.Label=$route041.Label.Replace('FINAL_',$Candidate+'_')}
$summary041=Join-Path $PSScriptRoot ($Candidate+'_ROUTES041.tsv')
if(Test-Path -LiteralPath $summary041){throw 'Immutable final route receipt already exists.'}
"label`texitCode`tsummary" | Set-Content -LiteralPath $summary041 -Encoding utf8
$failed041=0
foreach($route041 in $routes041){
 & powershell.exe -NoProfile -File $runner041 -Action Test -JarPath $JarPath @route041
 $exit041=$LASTEXITCODE
 $line041=(Select-String -LiteralPath (Join-Path $task041 "evidence/$($route041.Label).log") -Pattern '^SUMMARY:' | Select-Object -Last 1).Line
 "$($route041.Label)`t$exit041`t$line041" | Add-Content -LiteralPath $summary041 -Encoding utf8
 if($exit041 -ne 0){$failed041++}
}
& powershell.exe -NoProfile -File $runner041 -Action Matrix -Label ($Candidate+'93_041') -JarPath $JarPath
if($LASTEXITCODE -ne 0){$failed041++}
Write-Output "FINAL041 failedRoutes=$failed041"
if($failed041){exit 1}
