[CmdletBinding()]
param(
    [ValidateSet('Legacy','Fixed')][string]$Mode = 'Fixed',
    [string]$OutputRoot = (Join-Path ([IO.Path]::GetTempPath()) ('Task108R3Process-' + [guid]::NewGuid().ToString('N'))),
    [string]$WrapperPath = (Join-Path $PSScriptRoot 'Task108R3Run.ps1')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1) { throw 'WINDOWS_POWERSHELL_51_REQUIRED' }
if (Test-Path -LiteralPath $OutputRoot) { throw 'FRESH_TEST_OUTPUT_REQUIRED' }
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
$wrapper = (Resolve-Path -LiteralPath $WrapperPath).Path
if ($Mode -eq 'Fixed') {
    # Load only the three pure process functions; never execute wrapper authority/runtime code.
    $parseErrors = $null; $tokens = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($wrapper, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw 'WRAPPER_PARSE_ERROR' }
    foreach ($name in @('Hold-ProcessHandle','Assert-ObservedExitCode','Get-TrackedProcessExitCode')) {
        $functions = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name }, $false))
        if ($functions.Count -ne 1) { throw "EXACT_FUNCTION_REQUIRED:$name" }
        . ([scriptblock]::Create($functions[0].Extent.Text))
    }
}
$stub = Join-Path $OutputRoot 'exit-stub.ps1'
[IO.File]::WriteAllText($stub, 'param([int]$Code) Start-Sleep -Milliseconds 1200; [Console]::Out.WriteLine("stub-out"); [Console]::Error.WriteLine("stub-err"); exit $Code', [Text.UTF8Encoding]::new($false))
$executable = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$results = @()
foreach ($expected in @(0,7)) {
    $stdout = Join-Path $OutputRoot ("exit$expected.stdout.txt")
    $stderr = Join-Path $OutputRoot ("exit$expected.stderr.txt")
    $arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $stub + '" -Code ' + $expected
    $process = Start-Process -FilePath $executable -ArgumentList $arguments -WorkingDirectory $OutputRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $handle = $null
    if ($Mode -eq 'Fixed') { $handle = Hold-ProcessHandle $process }
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    while (!$process.WaitForExit(1000)) { if ([DateTime]::UtcNow -gt $deadline) { throw 'STUB_DEADLINE_NO_FORCE' } }
    $observed = if ($Mode -eq 'Fixed') { Get-TrackedProcessExitCode $process $handle } else { $process.ExitCode }
    $results += @{ expected=$expected; observed=$observed; isNull=($null -eq $observed); zeroAccepted=($null -ne $observed -and $observed -is [int] -and $observed -eq 0) }
    if ([IO.File]::ReadAllText($stdout).Trim() -cne 'stub-out' -or [IO.File]::ReadAllText($stderr).Trim() -cne 'stub-err') { throw 'STUB_REDIRECTION_FAILED' }
    $process.Dispose()
}
$evidence = @{ mode=$Mode; powershell=$PSVersionTable.PSVersion.ToString(); results=$results; wrapperSha256=(Get-FileHash -LiteralPath $wrapper).Hash.ToLowerInvariant(); native=0; SQL=0 }
$evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputRoot 'results.json') -Encoding UTF8
$results | ForEach-Object { 'expected=' + $_.expected + ';observed=' + $_.observed + ';null=' + $_.isNull + ';zeroAccepted=' + $_.zeroAccepted }
if ($Mode -eq 'Legacy') {
    if (@($results | Where-Object { $_.isNull -or $_.observed -ne $_.expected }).Count) { throw 'RED_EXIT_CODE_NOT_OBSERVED' }
    'LEGACY_NOT_REPRODUCED'; return
}
foreach ($row in $results) { if ($row.isNull -or $row.observed -ne $row.expected -or $row.zeroAccepted -ne ($row.expected -eq 0)) { throw 'WRONG_EXIT_STATUS' } }
foreach ($invalid in @($null,'0',[DBNull]::Value)) {
    $rejected = $false
    try { Assert-ObservedExitCode $invalid | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'UNKNOWN_COERCED_TO_ZERO' }
}
$unstarted = [Diagnostics.Process]::new()
try {
    $rejected = $false
    try { Hold-ProcessHandle $unstarted | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'HANDLE_ACCESS_FAILURE_ACCEPTED' }
    foreach ($invalidHandle in @($null,[IntPtr]::Zero)) {
        $rejected = $false
        try { Get-TrackedProcessExitCode $unstarted $invalidHandle | Out-Null } catch { $rejected = $true }
        if (!$rejected) { throw 'UNKNOWN_HANDLE_ACCEPTED' }
    }
}
finally { $unstarted.Dispose() }
if (@(Get-ChildItem -LiteralPath $OutputRoot -Filter 'exit*.clean').Count -ne 0) { throw 'CLEAN_WITHOUT_POSTFLIGHT' }
$evidence.guardNegatives = 'PASS'
$evidence.cleanAbsent = $true
$evidence.completed = $true
$evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputRoot 'results.json') -Encoding UTF8
'PROCESS_EXIT_GREEN codes=0,7;nullAndUndefined=REJECTED;handleFailure=REJECTED;clean=ABSENT;native=0;SQL=0'
