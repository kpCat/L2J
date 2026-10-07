[CmdletBinding()]
param([string]$Suite='PhantomCheckpointRecovery025Suite',[string]$Focus='', [ValidateSet('Candidate','Base')][string]$Engine='Candidate',
      [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label,[string]$LauncherId='',[switch]$ResetFixture)
$ErrorActionPreference='Stop'
$taskRoot025=$PSScriptRoot
$module025=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
if($ResetFixture){
    $private025=Join-Path $module025 '.phantom-local/contract025b'
    $snapshot025=Join-Path $private025 'play-snapshot.sql'
    $owned025=Get-Content (Join-Path $private025 'test/owned.properties') | ConvertFrom-StringData
    if($owned025.owner -cne 'TASK025_CONTRACT' -or $owned025.database -cne 'l2jmobiush5_localplay_contract025b' -or (Get-FileHash $snapshot025).Hash.ToLowerInvariant() -cne $owned025.exportSha256){throw 'Exact own fixture input guard failed.'}
    if(Select-String -LiteralPath $snapshot025 -Pattern '^(CREATE|DROP) DATABASE|^USE ' -Quiet){throw 'Unsafe import directive.'}
    $option025=Join-Path $private025 'secrets/client.cnf'
    $client025='C:/Program Files/MariaDB 11.4/bin/mariadb.exe'
    $active025=& $client025 "--defaults-extra-file=$option025" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB='l2jmobiush5_localplay_contract025b'"
    if($LASTEXITCODE -ne 0 -or "$active025".Trim() -cne '0'){throw 'Fixture must have zero active DB connections.'}
    $backup025=Join-Path $private025 "$Label-before.sql"
    if(Test-Path $backup025){throw 'Immutable fixture backup already exists.'}
    & 'C:/Program Files/MariaDB 11.4/bin/mariadb-dump.exe' "--defaults-extra-file=$option025" --single-transaction --skip-lock-tables --hex-blob "--result-file=$backup025" l2jmobiush5_localplay_contract025b
    if($LASTEXITCODE -ne 0){throw 'Own fixture backup failed.'}
    $process025=Start-Process -FilePath $client025 -ArgumentList @("--defaults-extra-file=`"$option025`"",'l2jmobiush5_localplay_contract025b') -RedirectStandardInput $snapshot025 -RedirectStandardOutput (Join-Path $private025 "$Label-import.out") -RedirectStandardError (Join-Path $private025 "$Label-import.err") -WindowStyle Hidden -Wait -PassThru
    if($process025.ExitCode -ne 0){throw 'Own fixture original snapshot import failed.'}
    [ordered]@{purpose='DISPOSABLE_TEST_FIXTURE_RESET_NOT_PRODUCT_RECOVERY';database=$owned025.database;inputSha256=$owned025.exportSha256;backupSha256=(Get-FileHash $backup025).Hash;backupPath=$backup025;activeConnectionsBefore=0} | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot "$Label.json") -Encoding utf8
    return
}
$body025=[IO.File]::ReadAllText((Join-Path $module025 'docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/Run-Checks024.ps1'))
$body025=$body025.Replace('$PSScriptRoot','$taskRoot025').Replace('contract024a','contract025b').Replace('phantom.contract024.manifest','phantom.contract025.manifest')
$body025=$body025.Replace('819e3cea5baa64e6c429e450c8fc296874e37d1c','07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6')
if($LauncherId){
    if($LauncherId -notin @('population-ecology-goal033','population-ecology-handoff-regression','live003-admission')){throw 'Named regression route required.'}
    $body025=$body025.Replace('else{$args023+=@("org.l2jmobius.tests.phantoms.$Suite",$module,$reports023)}', 'else{$args023+=@("org.l2jmobius.tests.phantoms.PhantomTestLauncher",$LauncherId,"25002503")}')
}
& ([scriptblock]::Create($body025)) -Suite $Suite -Focus $Focus -Engine $Engine -Label $Label
