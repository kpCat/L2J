[CmdletBinding()]
param([string]$OutputRoot = '', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$studio = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repository = [IO.Path]::GetDirectoryName($studio)
$module = Join-Path $repository 'L2J_Mobius_CT_2.6_HighFive'
if (!$OutputRoot) { $OutputRoot = Join-Path $env:TEMP ('phantom095-pipeline-' + [guid]::NewGuid().ToString('N')) }
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
if ($OutputRoot.Equals($repository, [StringComparison]::OrdinalIgnoreCase) -or $OutputRoot.StartsWith($repository + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Smoke output must be outside the protected repository.' }
$ancestor = $OutputRoot
while ($ancestor) {
    if ((Test-Path -LiteralPath $ancestor) -and ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Smoke output cannot use reparse points.' }
    $ancestor = [IO.Path]::GetDirectoryName($ancestor)
}
if (Test-Path -LiteralPath $OutputRoot) { throw 'Use a new empty output root.' }
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
if (!$SkipBuild) {
    & (Join-Path $PSScriptRoot 'Build-Verify.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Build-Verify failed.' }
}
$cli = Join-Path $studio 'src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll'
$tests = Join-Path $studio 'tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll'
$desktop = Join-Path $studio 'src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json'
& dotnet $tests --task095
if ($LASTEXITCODE -ne 0) { throw 'Core TASK095 failed.' }
& dotnet exec --runtimeconfig $desktop $tests --task095-controls
if ($LASTEXITCODE -ne 0) { throw 'GUI TASK095 failed.' }
$script:transcript = [Collections.Generic.List[string]]::new()
function Invoke-Cli([string[]]$Arguments, [string]$Workspace) {
    $script:transcript.Add('dotnet CLI ' + ($Arguments -join ' '))
    $result = & dotnet $cli @Arguments --workspace $Workspace --source $module
    $code = $LASTEXITCODE
    foreach ($line in $result) { $script:transcript.Add([string]$line) }
    if ($code -ne 0) { throw ('CLI failed exit ' + $code + ': ' + ($result -join "`n")) }
    return $result
}
$workspace = Join-Path $OutputRoot 'workspace'
$demo = Join-Path $module 'docs/phantoms/semantic/v4/demo'
Invoke-Cli @('validate', $demo) $workspace | Out-Null
Invoke-Cli @('import', $demo, '--dry-run') $workspace | Out-Null
Invoke-Cli @('import', $demo) $workspace | Out-Null
$repeat = Invoke-Cli @('import', $demo) $workspace
if (($repeat -join "`n") -notmatch 'новых 0, дублей 218') { throw 'Idempotence regression.' }
Invoke-Cli @('build') $workspace | Out-Null
$demoStage = (Get-ChildItem -LiteralPath (Join-Path $workspace 'stages') -Directory).FullName
Invoke-Cli @('verify', $demoStage) $workspace | Out-Null
Invoke-Cli @('stats') $workspace | Out-Null
Invoke-Cli @('categories') $workspace | Out-Null
Invoke-Cli @('preview', '--category', 'hybrid.trade.absurd_offers', '--persona', 'calm') $workspace | Out-Null
$passport = Join-Path $workspace 'CHATGPT_GENERATION_RU.md'
Invoke-Cli @('export-passport', $passport) $workspace | Out-Null
$published = Join-Path $module 'docs/phantoms/semantic/CHATGPT_GENERATION_RU.md'
if ((Get-FileHash -LiteralPath $passport).Hash -ne (Get-FileHash -LiteralPath $published).Hash) { throw 'Published passport differs from current validator registry.' }
# This is synthetic test-only review of an explicitly known fixture, never user auto-approval.
$roundtrip = Join-Path $OutputRoot 'roundtrip'
$key = 'hybrid.trade.owner_reviewed'
$proposalResult = Invoke-Cli @('taxonomy', 'propose', '--parent', 'hybrid.trade', '--key', $key, '--label', 'Проверенная владельцем тема', '--act', ($key + '.reply'), '--intent', ($key + '.observe')) $roundtrip
$proposalFiles = @(Get-ChildItem -LiteralPath (Join-Path $roundtrip 'proposals') -File)
if ($proposalFiles.Count -ne 1) { throw 'Expected one exact proposal.' }
$proposal = $proposalFiles[0]
$reviewedHash = (Get-FileHash -LiteralPath $proposal.FullName).Hash.ToLowerInvariant()
Invoke-Cli @('taxonomy', 'approve', '--proposal', $proposal.FullName, '--hash', $reviewedHash, '--reviewed') $roundtrip | Out-Null
$nextPassport = Join-Path $roundtrip 'CHATGPT_GENERATION_RU.md'
Invoke-Cli @('export-passport', $nextPassport) $roundtrip | Out-Null
if (![IO.File]::ReadAllText($nextPassport).Contains($key)) { throw 'Approved leaf missing from passport.' }
$utf8 = [Text.UTF8Encoding]::new($false)
$inputRoot = Join-Path $OutputRoot 'new-leaf-input'
New-Item -ItemType Directory -Path $inputRoot | Out-Null
$header = @{schema='pw-semantic-batch/1';category=$key;act=($key+'.reply');language='ru';count=1}
$header.mode = 'TRIGGER'
$trigger = @{text='Проверка новой категории.';intent=($key+'.observe')}
[IO.File]::WriteAllText((Join-Path $inputRoot 'trigger.jsonl'), (($header | ConvertTo-Json -Compress) + "`n" + ($trigger | ConvertTo-Json -Compress) + "`n"), $utf8)
$header.mode = 'REPLY'
$reply = @{text='Новая тема принята без правки игрового кода.';stance='NEUTRAL'}
[IO.File]::WriteAllText((Join-Path $inputRoot 'reply.jsonl'), (($header | ConvertTo-Json -Compress) + "`n" + ($reply | ConvertTo-Json -Compress) + "`n"), $utf8)
Invoke-Cli @('import', $inputRoot) $roundtrip | Out-Null
Invoke-Cli @('build') $roundtrip | Out-Null
$roundtripStage = (Get-ChildItem -LiteralPath (Join-Path $roundtrip 'stages') -Directory).FullName
Invoke-Cli @('verify', $roundtripStage) $roundtrip | Out-Null
[IO.File]::WriteAllLines((Join-Path $OutputRoot 'cli-transcript.txt'), $script:transcript, $utf8)
$receipt = @{DemoStage=$demoStage; RoundTripStage=$roundtripStage; RoundTripText=$trigger.text; RoundTripCategory=$key; Java='NOT_RUN'; Gui='2_PASS'; ProtectedSource=$module}
[IO.File]::WriteAllText((Join-Path $OutputRoot 'pipeline.json'), ($receipt | ConvertTo-Json), $utf8)
Write-Output ('CONTENT_TEST_PASS / GUI_SMOKE_PASS; Java content proof must be run separately: ' + $OutputRoot)
Write-Output ($receipt | ConvertTo-Json -Compress)
