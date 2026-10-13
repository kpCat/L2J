[CmdletBinding()]
param(
    [ValidateSet('OperatorSmoke','Regression','Tooling')][string]$Action='Tooling',
    [string]$RuntimeRoot='',
    [string]$SourceModuleRoot='',
    [string]$TestLaneRoot='',
    [string]$BuildRoot='',
    [string]$ReportRoot='',
    [string]$JavaHome='',
    [string]$JarPath='',
    [string]$Suite='PhantomM1Closure041Suite',
    [string]$Focus='',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label='VERIFY_M1'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if (!$RuntimeRoot) {$RuntimeRoot=Join-Path $PSScriptRoot '..'}
if ($Action -ceq 'Regression') {
    foreach ($value in @($SourceModuleRoot,$TestLaneRoot,$BuildRoot,$ReportRoot,$JavaHome,$JarPath)) {if (!$value) {throw 'Regression требует explicit source/test/build/report/JDK/JAR paths.'}}
    $runner=Join-Path $SourceModuleRoot 'docs/phantoms/tasks/PHANTOM-M1-FINAL-CLOSURE-041/Verify041.ps1'
    $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',$runner,'-Action','Test','-Label',$Label,'-Suite',$Suite,'-JarPath',$JarPath,'-TestLaneRoot',$TestLaneRoot,'-BuildRoot',$BuildRoot,'-ReportRoot',$ReportRoot,'-JavaHome',$JavaHome)
    if ($Focus) {$arguments+=@('-Focus',$Focus)}
    & powershell.exe @arguments
    exit $LASTEXITCODE
}
$runtime=[IO.Path]::GetFullPath($RuntimeRoot)
if ($Action -ceq 'Tooling') {
    $script=Join-Path $PSScriptRoot 'Runtime.ps1'
    $tokens=$null; $errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($script,[ref]$tokens,[ref]$errors)
    if ($errors.Count) {throw ($errors.Message -join '; ')}
    $ownership=Join-Path $PSScriptRoot 'LocalPlay-Ownership.ps1'
    if (!(Test-Path -LiteralPath $ownership)) {$ownership=Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1'}
    . $ownership
    $record=[pscustomobject]@{format=2;role='GameServer';pid=42;startTimeUtcTicks=100;jar='GameServer.jar';runtime='own'}
    $process=[pscustomobject]@{role='GameServer';pid=42;startTimeUtcTicks=101;jar='GameServer.jar';runtime='own'}
    $stale=Resolve-LocalPlayOwnership 'GameServer' 'own' 'GameServer.jar' @(7792) $record @($process) @{7792=42}
    if ($stale.recordVerified) {throw 'RF1 stale incarnation was accepted.'}
    $foreign=Resolve-LocalPlayOwnership 'GameServer' 'own' 'GameServer.jar' @(7792) $null @() @{7792=30312}
    if ($foreign.state -cne 'FOREIGN_PORT_OWNER') {throw 'RF1 foreign port was accepted.'}
    Write-Output 'TOOLING_PASS: parse / stale incarnation denied / foreign port denied'
    exit 0
}
if (!$ReportRoot) {throw 'OperatorSmoke требует новый explicit ReportRoot.'}
$report=[IO.Path]::GetFullPath($ReportRoot)
if (Test-Path -LiteralPath $report) {throw 'Immutable operator smoke output exists.'}
New-Item -ItemType Directory -Path $report | Out-Null
function Invoke-Runtime([string]$Operation,[string]$Name) {
    # A START child may leave inherited output handles in its background JVMs.
    # Wait for the controller incarnation, independently of descendant pipe EOF.
    $arguments='-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $PSScriptRoot 'Runtime.ps1')+'" -Action '+$Operation+' -RuntimeRoot "'+$runtime+'"'
    $controller=Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $report "$Name.log") -RedirectStandardError (Join-Path $report "$Name.stderr.log") -PassThru
    if (!$controller.WaitForExit(650000)) {throw "Operator $Operation controller timeout; no force fallback."}
    if ($Operation -cne 'Start') {$controller.WaitForExit()}
    if ($controller.ExitCode -ne 0) {throw "Operator $Operation failed; log=$Name.log"}
}
function Get-State([string]$Name) {
    Invoke-Runtime 'Status' $Name
    return Get-Content -LiteralPath (Join-Path $report "$Name.log") -Raw | ConvertFrom-Json
}
Invoke-Runtime 'Start' 'start1'
$first=Get-State 'status1'
if ($first.game.state -cne 'RUNNING' -or $first.login.state -cne 'RUNNING' -or !$first.game.recordVerified -or !$first.login.recordVerified) {throw 'First start ownership unconfirmed.'}
Invoke-Runtime 'Start' 'start-idempotent'
$same=Get-State 'status-idempotent'
if ($same.game.pid -ne $first.game.pid -or $same.game.startTimeUtcTicks -ne $first.game.startTimeUtcTicks -or $same.login.pid -ne $first.login.pid) {throw 'Repeated START created another incarnation.'}
Invoke-Runtime 'Stop' 'stock-stop'
$stopped=Get-State 'status-stopped'
if ($stopped.game.state -cnotin @('STOPPED','STALE_RECORD') -or $stopped.login.state -cnotin @('STOPPED','STALE_RECORD')) {throw 'Stock STOP did not finish.'}
Invoke-Runtime 'Start' 'start2'
$second=Get-State 'status2'
if ($second.game.state -cne 'RUNNING' -or $second.login.state -cne 'RUNNING' -or $second.game.startTimeUtcTicks -eq $first.game.startTimeUtcTicks -or $second.database -cne $first.database) {throw 'SameDB restart failed.'}
[ordered]@{pass=$true;source=$second.source;database=$second.database;first=$first;second=$second;ordinaryStockShutdown=$true;personalAcceptance='REQUIRED'} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $report 'result.json') -Encoding UTF8
Write-Output 'OPERATOR_SMOKE_PASS: Start / idempotent Start / Status / stock Stop / sameDB Start'
