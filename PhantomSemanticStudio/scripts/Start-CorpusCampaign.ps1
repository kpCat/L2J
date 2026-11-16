[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CampaignId,
    [Parameter(Mandatory)][string]$PlanHash,
    [Parameter(Mandatory)][string]$Workspace,
    [Parameter(Mandatory)][string]$Source,
    [ValidateRange(1,1000000)][int]$Limit = 1000000,
    [string]$PolicyId = '',
    [string]$ApprovalHash = '',
    [switch]$Resume,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
if ($CampaignId -cnotmatch '^[a-f0-9]{32}$' -or $PlanHash -cnotmatch '^[a-f0-9]{64}$') { throw 'Нужны точные ID и SHA256 campaign-плана.' }
if ([bool]$PolicyId -ne [bool]$ApprovalHash -or ($PolicyId -and ($PolicyId -cnotmatch '^[a-f0-9]{32}$' -or $ApprovalHash -cnotmatch '^[a-f0-9]{64}$'))) { throw 'Нужна пара PolicyId и ApprovalHash утверждённой политики.' }
if (![IO.Path]::IsPathRooted($Workspace) -or ![IO.Path]::IsPathRooted($Source)) { throw 'Workspace и Source должны быть абсолютными путями.' }
$Workspace = [IO.Path]::GetFullPath($Workspace)
$Source = [IO.Path]::GetFullPath($Source)
$studio = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cli = Join-Path $studio 'src/PhantomSemanticStudio.Cli/bin/Release/net10.0/PhantomSemanticStudio.Cli.dll'
if (!(Test-Path -LiteralPath $cli -PathType Leaf)) { throw 'Сначала соберите существующую Studio в Release.' }
foreach ($value in @($Workspace,$Source,$cli)) { if ($value -match '["\r\n]' -or $value.EndsWith('\')) { throw 'Неподдерживаемый символ или завершающий слеш в аргументе.' } }
# Existing offline guard checks disjoint/reparse paths and frozen pins before log creation.
& dotnet $cli campaign-check $CampaignId --hash $PlanHash --workspace $Workspace --source $Source | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Campaign check не пройден; процесс не запущен.' }
if ($PolicyId) {
    & dotnet $cli campaign-policy-check $PolicyId --hash $ApprovalHash --workspace $Workspace --source $Source | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Policy check не пройден; процесс не запущен.' }
}
$logs = Join-Path $Workspace ('labs/prompt-lab/campaigns/' + $CampaignId + '/processes')
function AssertProcessPath([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (!$resolved.StartsWith($Workspace.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'PROCESS_PATH_OUTSIDE_WORKSPACE' }
    $cursor = $resolved
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'LINKED_PROCESS_PATH' }
        }
        $parent = [IO.Path]::GetDirectoryName($cursor); if ($parent -eq $cursor) { break }; $cursor = $parent
    }
}
AssertProcessPath $logs
if ($CheckOnly) { Write-Output 'CAMPAIGN_LAUNCH_PREFLIGHT_PASS; no process started.'; return }
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$launch = [guid]::NewGuid().ToString('N')
$output = Join-Path $logs ($launch + '.out.log'); $errorLog = Join-Path $logs ($launch + '.err.log')
$receiptPath = Join-Path $logs ($launch + '.json')
foreach ($path in @($output,$errorLog,$receiptPath)) { AssertProcessPath $path }
$command = if ($Resume) { 'campaign-resume' } else { 'campaign-run' }
$arguments = @($cli,$command,$CampaignId,'--hash',$PlanHash,'--workspace',$Workspace,'--source',$Source,'--limit',$Limit.ToString())
if ($PolicyId) { $arguments += @('--policy',$PolicyId,'--approval-hash',$ApprovalHash) } else { $arguments += '--draft-only' }
$quoted = @($arguments | ForEach-Object { '"' + $_ + '"' })
$process = Start-Process -FilePath (Get-Command dotnet).Source -ArgumentList $quoted -WindowStyle Hidden -RedirectStandardOutput $output -RedirectStandardError $errorLog -PassThru
$receipt = [pscustomobject]@{
    Schema='pw-campaign-process/1'; State='STARTED_NOT_YET_PROVEN'; ProcessId=$process.Id; StartTimeUtc=$process.StartTime.ToUniversalTime().ToString('O')
    CampaignId=$CampaignId; PlanHash=$PlanHash; Workspace=$Workspace; MaxBatches=$Limit; DraftOnly=(!$PolicyId); PolicyId=$PolicyId; ApprovalHash=$ApprovalHash
    RunnerSha256=(Get-FileHash -LiteralPath $cli -Algorithm SHA256).Hash.ToLowerInvariant(); Output=$output; Error=$errorLog
}
AssertProcessPath $receiptPath
[IO.File]::WriteAllText($receiptPath,($receipt | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$receipt | ConvertTo-Json
# No Wait-Process: the worker owns its workspace lock and survives this launcher.
