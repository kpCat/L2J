[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [Parameter(Mandatory)][ValidateRange(1,2147483647)][long]$ProfileId,
      [ValidateRange(15,45)][int]$HumanSeconds=45,
      [ValidateRange(90,180)][int]$PassiveSeconds=120)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Task070Common.ps1')
$runtime=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
$owner=Join-Path $runtime 'ownership070.properties'
$module=Read-Field070 $owner 'sourceModuleRoot'
$binding=Assert-Binding070 $runtime $module (Read-Field070 $owner 'schema') (Read-Field070 $owner 'role')
if($binding.role -cne 'MUTABLE_FIXTURE'){throw 'TASK070: native human observation is TEST-only.'}
. (Join-Path $runtime 'LocalPlay-Pilot.ps1')
$reports=Join-Path $binding.task ('reports/main-'+$Label)
if(Test-Path -LiteralPath $reports){throw 'TASK070: preserve existing Main evidence.'}
New-Item -ItemType Directory -Path $reports | Out-Null
$run=[guid]::NewGuid().ToString()
$context=$null
function Copy-Observer070([string]$Name){
    $path=Join-Path $runtime 'playtest-pilot/observer.properties'
    Assert-Plain070 $path
    if(Test-Path -LiteralPath $path){Copy-Item -LiteralPath $path -Destination (Join-Path $reports ($Name+'.properties'))}
}
try{
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') -RunId $run -SetupProfileId $ProfileId | Set-Content -LiteralPath (Join-Path $reports 'native-test-human-start.json') -Encoding utf8
    $context=Get-PilotContext -RequireEnabled -ActorMode Synthetic -SessionId $run
    $until=[DateTime]::UtcNow.AddSeconds($HumanSeconds)
    $sample=0
    while([DateTime]::UtcNow -lt $until){
        Write-PilotHeartbeat $context $run $run
        if((Get-PilotSession $context).state -cne 'RUNNING'){throw 'TASK070: TestHuman stopped; no replay.'}
        Copy-Observer070 ('observer-'+$sample.ToString('000'))
        $sample++
        Start-Sleep -Seconds 5
    }
}finally{
    if($context){
        $body="version=1`ncommand=STOP`nrunId=$run`nruntimeId=$($context.RuntimeId)`npid=$($context.Pid)`nstartTimeUtcTicks=$($context.StartTimeUtcTicks)`n"
        Write-PilotAtomicText (Join-Path $context.SyntheticRoot 'control.properties') $body
        Start-Sleep -Seconds 1
        Copy-Item -LiteralPath (Join-Path $context.SyntheticRoot 'session.properties') -Destination (Join-Path $reports 'native-test-human-stop.properties')
    }
}
# The real60s recent-human retention must expire before a background result can be assessed.
# No heartbeat, actor movement, goal/debt/cursor changes or HP restoration during this period.
$until=[DateTime]::UtcNow.AddSeconds($PassiveSeconds)
$sample=0
do{
    Copy-Observer070 ('after-human-'+$sample.ToString('000'))
    $sample++
    Start-Sleep -Seconds 5
}while([DateTime]::UtcNow -lt $until)
Write-Output "MAIN070_OBSERVATION_COMPLETE label=$Label; nativeHuman=$HumanSeconds; passiveAfterExit=$PassiveSeconds; inspect actual sameDB/epoch/native progress before any PASS."
