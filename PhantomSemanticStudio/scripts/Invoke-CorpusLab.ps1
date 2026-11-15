[CmdletBinding()]
param(
    [ValidateSet('Build','Test','Plan','ThinkingPlan','Models','Pilot','Status','Report')][string]$Action = 'Status',
    [string]$WorkRoot = '',
    [string]$Source = '',
    [string]$Lab = '',
    [string]$Model = '',
    [ValidateRange(1,400)][int]$Limit = 1,
    [ValidateSet('OPENAI_COMPAT','LM_NATIVE')][string]$Transport = 'OPENAI_COMPAT',
    [ValidateSet('default','off','on')][string]$Reasoning = 'default',
    [ValidateSet('none','reasoning_effort','chat_template_kwargs')][string]$ThinkingControl = 'none',
    [switch]$Detached,
    [switch]$MaturePilotConsent,
    [switch]$IntimateHoldout,
    [switch]$ThinkingQuality,
    [ValidateRange(128,8192)][int]$ThinkingBudget = 2048,
    [switch]$BatchSizeProbe
)
$ErrorActionPreference = 'Stop'
$studio = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$snapshot = Split-Path $studio -Parent
if (!$WorkRoot) {
    if (!(Test-Path -LiteralPath (Join-Path $snapshot 'SOURCE_MANIFEST.json'))) { throw 'Укажите внешний -WorkRoot; личный workspace автоматически не выбирается.' }
    $WorkRoot = Split-Path $snapshot -Parent
}
$WorkRoot = [IO.Path]::GetFullPath($WorkRoot)
function Within([string]$Path,[string]$Root) {
    $p=[IO.Path]::GetFullPath($Path).TrimEnd('\'); $r=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    return $p.Equals($r,[StringComparison]::OrdinalIgnoreCase) -or $p.StartsWith($r+'\',[StringComparison]::OrdinalIgnoreCase)
}
if ((Within $WorkRoot $studio) -or (Within $studio $WorkRoot) -and $snapshot -ne (Join-Path $WorkRoot 'shadow')) { throw 'Рабочие данные и source должны быть отделены; поддерживается явный shadow layout.' }
if (!$Source) { $Source = Join-Path $snapshot 'L2J_Mobius_CT_2.6_HighFive' }
$Source=[IO.Path]::GetFullPath($Source)
if (!$Lab) { $Lab=Join-Path $WorkRoot 'pilot/thinking-v1' }
$Lab=[IO.Path]::GetFullPath($Lab)
if (!(Within $Lab $WorkRoot)) { throw 'Lab должен находиться внутри явно указанного WorkRoot.' }
$scratch=Join-Path $snapshot 'tmp'; New-Item -ItemType Directory -Path $scratch -Force | Out-Null
$env:TEMP=$scratch; $env:TMP=$scratch
$env:DOTNET_CLI_HOME=Join-Path $snapshot '.dotnet'
$env:NUGET_PACKAGES=Join-Path $snapshot '.nuget/packages'
$env:DOTNET_CLI_TELEMETRY_OPTOUT='1'
$cli=Join-Path $studio 'src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll'
if ($Action -in @('Build','Test')) {
    & dotnet build (Join-Path $studio 'PhantomSemanticStudio.sln') -c Release --no-restore --nologo
    if ($LASTEXITCODE -ne 0) { throw "BUILD_FAILED: $LASTEXITCODE" }
    if ($Action -eq 'Test') {
        $runner=Join-Path $studio 'tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll'
        foreach ($route in @('--corpus001','--task095','--batch081')) {
            & dotnet $runner $route
            if ($LASTEXITCODE -ne 0) { throw "TEST_FAILED: $route" }
        }
    }
    return
}
if (!(Test-Path -LiteralPath $cli -PathType Leaf)) { throw 'Сначала выполните -Action Build.' }
$command = switch ($Action) { 'Plan' {'corpus-plan'} 'ThinkingPlan' {'corpus-plan'} 'Models' {'corpus-models'} 'Pilot' {'corpus-pilot'} 'Status' {'corpus-status'} 'Report' {'corpus-report'} }
$cliArguments=@($cli,$command,'--lab',$Lab,'--source',$Source)
if ($Action -eq 'ThinkingPlan') { $cliArguments+='--thinking' }
if ($MaturePilotConsent) {
    if ($Action -ne 'Plan') { throw '-MaturePilotConsent задаётся только при создании нового Plan.' }
    $cliArguments+='--mature-pilot-consent'
}
if ($IntimateHoldout) {
    if ($Action -ne 'Plan' -or !$MaturePilotConsent) { throw '-IntimateHoldout требует новый Plan и -MaturePilotConsent.' }
    $cliArguments+='--intimate-holdout'
}
if ($ThinkingQuality) {
    if ($Action -ne 'Plan' -or !$MaturePilotConsent) { throw '-ThinkingQuality требует новый Plan и -MaturePilotConsent.' }
    $cliArguments+=@('--thinking-quality','--thinking-budget',$ThinkingBudget.ToString())
}
if ($BatchSizeProbe) {
    if ($Action -ne 'Plan' -or $ThinkingQuality -or $IntimateHoldout) { throw '-BatchSizeProbe требует отдельный Plan без других экспериментальных режимов.' }
    $cliArguments+='--batch-size-probe'
}
if ($Action -eq 'Plan') { $cliArguments+=@('--transport',$Transport,'--reasoning',$Reasoning,'--thinking-control',$ThinkingControl) }
if ($Action -in @('Models','Pilot')) {
    if ($Action -eq 'Pilot' -and !$Model) { throw 'Нужен точный -Model из результата Models после ручного запуска владельцем.' }
    if (!$Model) { $Model='_discovery_only_' }
    $cliArguments+=@('--model',$Model)
}
if ($Action -eq 'Pilot') { $cliArguments+=@('--limit',$Limit.ToString()) }
if ($Detached) {
    if ($Action -ne 'Pilot') { throw '-Detached допускается только для Pilot, модель уже должна быть вручную запущена.' }
    $logs=Join-Path $WorkRoot 'state/pilot-processes'; New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $runId=[guid]::NewGuid().ToString('N')
    $quoted=@($cliArguments | ForEach-Object { if ($_ -match '["\r\n]') { throw 'Неподдерживаемый символ в аргументе.' }; '"'+$_+'"' })
    $process=Start-Process -FilePath (Get-Command dotnet).Source -ArgumentList $quoted -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs "$runId.out.log") -RedirectStandardError (Join-Path $logs "$runId.err.log") -PassThru
    [pscustomobject]@{ ProcessId=$process.Id; Lab=$Lab; MaxRequests=$Limit; Status='STARTED_NOT_YET_PROVEN'; LogPrefix=(Join-Path $logs $runId) } | ConvertTo-Json
    return
}
& dotnet @cliArguments
if ($LASTEXITCODE -ne 0) { throw "CORPUS_COMMAND_FAILED: $LASTEXITCODE" }
