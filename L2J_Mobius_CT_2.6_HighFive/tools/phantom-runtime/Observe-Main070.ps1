[CmdletBinding()]
param([Parameter(Mandatory)][string]$RuntimeRoot,
      [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [Parameter(Mandatory)][ValidateRange(1,2147483647)][long]$ProfileId,
      [ValidateRange(15,45)][int]$HumanSeconds=45,
      [ValidateRange(90,180)][int]$PassiveSeconds=120,
      [switch]$Resume)
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
function Copy-Mailbox070([string]$InputPath,[string]$OutputPath){
    Assert-Plain070 $InputPath
    Assert-Plain070 $OutputPath
    $input070=[IO.File]::Open($InputPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    try{
        if($input070.Length -gt 65536){throw 'TASK070: bounded mailbox snapshot required.'}
        $output070=[IO.File]::Open($OutputPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try{$input070.CopyTo($output070)}finally{$output070.Dispose()}
    }finally{$input070.Dispose()}
}
function Copy-Observer070([string]$Name){
    $path=Join-Path $runtime 'playtest-pilot/observer.properties'
    Assert-Plain070 $path
    if(Test-Path -LiteralPath $path){Copy-Mailbox070 $path (Join-Path $reports ($Name+'.properties'))}
}
try{
    $start070=@{RunId=$run}
    # Resume must retain the native human/profile coordinates and ownership from boot1.
    if(!$Resume){$start070.SetupProfileId=$ProfileId}
    & (Join-Path $runtime 'Start-LocalPlaySynthetic.ps1') @start070 | Set-Content -LiteralPath (Join-Path $reports 'native-test-human-start.json') -Encoding utf8
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
        Copy-Mailbox070 (Join-Path $context.SyntheticRoot 'session.properties') (Join-Path $reports 'native-test-human-stop.properties')
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
