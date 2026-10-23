[CmdletBinding()]
param(
    [ValidateSet('Baseline','Plan','Apply','Rollback')][string]$Mode='Plan',
    [Parameter(Mandatory)][string]$GameRoot,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$JavaClassPath,
    [string]$BaselineDirectory,
    [string]$PlanPath,
    [string]$ExpectedPlanHash,
    [string]$OwnershipManifest
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$encoding070=[Text.UTF8Encoding]::new($false,$true)
$owner070='TASK070_ECONOMY_QOL'
$writable070=@('data/multisell/custom/600012.xml','data/multisell/custom/600024.xml','data/multisell/91002.xml','data/multisell/91003.xml','data/multisell/91005.xml','data/multisell/91006.xml','data/economy070/pricing070.tsv','data/economy070/pricing070.sha256')
function Plain070([string]$Path){
    $absolute070=[IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    $walk070=[IO.Path]::GetPathRoot($absolute070)
    foreach($part070 in $absolute070.Substring($walk070.Length).Split([IO.Path]::DirectorySeparatorChar)){
        if(!$part070){continue}
        $walk070=Join-Path $walk070 $part070
        if((Test-Path -LiteralPath $walk070) -and ((Get-Item -LiteralPath $walk070 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Physical pricing path required: $walk070"}
    }
    return $absolute070
}
function Hash070([string]$Path){return (Get-FileHash -LiteralPath (Plain070 $Path) -Algorithm SHA256).Hash.ToLowerInvariant()}
function BytesHash070([byte[]]$Bytes){$sha070=[Security.Cryptography.SHA256]::Create();try{return ([BitConverter]::ToString($sha070.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()}finally{$sha070.Dispose()}}
function Target070([string]$Relative){
    if($Relative -cnotin $writable070){throw 'Path outside exact TASK070 price scope.'}
    return Plain070 (Join-Path $game070 $Relative)
}
function CurrentHash070([string]$Path){if(Test-Path -LiteralPath $Path){return Hash070 $Path};return 'MISSING'}
function Atomic070([string]$Path,[byte[]]$Bytes){
    $temporary070=$Path+'.price070-'+[Guid]::NewGuid().ToString('N')
    $prior070=$temporary070+'.prior'
    try{
        New-Item -ItemType Directory -Path (Split-Path $Path -Parent) -Force|Out-Null
        [IO.File]::WriteAllBytes($temporary070,$Bytes)
        if(Test-Path -LiteralPath $Path){[IO.File]::Replace($temporary070,$Path,$prior070)}else{[IO.File]::Move($temporary070,$Path)}
    }finally{foreach($ownedTemporary070 in @($temporary070,$prior070)){if(Test-Path -LiteralPath $ownedTemporary070){Remove-Item -LiteralPath $ownedTemporary070 -Force}}}
}
function OwnStopped070 {
    if(!$OwnershipManifest){throw 'Explicit OWNERSHIP070.json required.'}
    $manifestPath070=Plain070 $OwnershipManifest
    $manifest070=[IO.File]::ReadAllText($manifestPath070,$encoding070)|ConvertFrom-Json
    if($manifest070.owner -cne $owner070 -or $manifest070.allowPriceApply -ne $true -or (Plain070 $manifest070.gameRoot) -ine $game070 -or (Split-Path $manifestPath070 -Parent) -inotmatch ('^'+[regex]::Escape((Split-Path $game070 -Parent))+'(?:\\game)?$')){throw 'Exact own070 owner/root price binding required.'}
    $runtimeRoot070=Split-Path $game070 -Parent
    $runtimeId070=BytesHash070 ($encoding070.GetBytes($runtimeRoot070.ToLowerInvariant()))
    foreach($process070 in @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop)){
        if(!$process070.CommandLine){throw 'Cannot establish STOPPED while an unreadable Java command line exists.'}
        if($process070.CommandLine -match ('(?:^|\s)-Dphantom\.localplay\.runtime='+$runtimeId070+'(?:\s|$)') -or $process070.CommandLine.IndexOf($game070,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or $process070.CommandLine.IndexOf($runtimeRoot070,[StringComparison]::OrdinalIgnoreCase) -ge 0){throw 'OWN070 must be STOPPED before price mutation.'}
    }
    foreach($role070 in @('GameServer','LoginServer')){
        $recordPath070=Plain070 (Join-Path $runtimeRoot070 "local-play/pids/$role070.json")
        if(!(Test-Path -LiteralPath $recordPath070)){continue}
        $record070=[IO.File]::ReadAllText($recordPath070,$encoding070)|ConvertFrom-Json
        $live070=Get-Process -Id ([int]$record070.pid) -ErrorAction SilentlyContinue
        if($live070 -and $live070.StartTime.ToUniversalTime().Ticks -eq [long]$record070.startTimeUtcTicks){throw 'Own070 live PID receipt prevents price mutation.'}
    }
}
$game070=Plain070 $GameRoot
$output070=Plain070 $OutputDirectory
if(!(Test-Path -LiteralPath $game070 -PathType Container) -or $output070.StartsWith($game070+'\',[StringComparison]::OrdinalIgnoreCase) -or $output070 -ieq $game070){throw 'Existing physical game and external output required.'}
if($Mode -cin @('Baseline','Plan')){
    if(!$JavaClassPath){$JavaClassPath=(Join-Path (Split-Path $game070 -Parent) 'libs/GameServer.jar')+';'+(Join-Path (Split-Path $game070 -Parent) 'libs/*')}
    if($Mode -ceq 'Baseline'){
        if(Test-Path -LiteralPath $output070){throw 'Fresh canonical baseline required; no recapture from applied prices.'}
        & java -cp $JavaClassPath org.l2jmobius.gameserver.phantoms.economy.PhantomEconomy070CatalogExporter baseline $game070 $output070
        if($LASTEXITCODE -ne 0){throw 'Canonical070 capture failed; preserve diagnostics.'}
        Write-Output ('BASELINE070 catalogSHA256='+(Hash070 (Join-Path $output070 'STORE_CATALOG070.tsv')));return
    }
    if(!$BaselineDirectory){throw 'Plan070 requires original canonical BaselineDirectory.'}
    $baseline070=Plain070 $BaselineDirectory
    if(Test-Path -LiteralPath (Join-Path $output070 'plan070.json')){throw 'Fresh plan output required.'}
    & java -cp $JavaClassPath org.l2jmobius.gameserver.phantoms.economy.PhantomEconomy070CatalogExporter plan $game070 $output070 $baseline070
    if($LASTEXITCODE -ne 0){throw 'Source-backed070 exporter failed.'}
    $files070=@(Import-Csv -LiteralPath (Join-Path $output070 'FILE_PLAN070.tsv') -Delimiter "`t")
    foreach($file070 in $files070){$null=Target070 $file070.path}
    $plan070=[ordered]@{version=1;owner=$owner070;gameRoot=$game070;baselineDirectory=$baseline070;payloadRoot=(Join-Path $output070 'payload');files=$files070;sources=@(Import-Csv -LiteralPath (Join-Path $output070 'sources.tsv') -Delimiter "`t")}
    $planOutput070=Join-Path $output070 'plan070.json'
    [IO.File]::WriteAllText($planOutput070,($plan070|ConvertTo-Json -Depth 12),$encoding070)
    Write-Output ('PLAN070 path='+$planOutput070+';sha256='+(Hash070 $planOutput070)+';files='+$files070.Count);return
}
OwnStopped070
if(!$PlanPath -or $ExpectedPlanHash -cnotmatch '^[0-9a-fA-F]{64}$' -or (Hash070 $PlanPath) -cne $ExpectedPlanHash.ToLowerInvariant()){throw 'Exact plan SHA required before mutation.'}
$plan070=[IO.File]::ReadAllText((Plain070 $PlanPath),$encoding070)|ConvertFrom-Json
if($plan070.version -ne 1 -or $plan070.owner -cne $owner070 -or $plan070.gameRoot -ine $game070 -or @($plan070.files).Count -ne $writable070.Count -or @($plan070.files.path|Select-Object -Unique).Count -ne $writable070.Count){throw 'Plan owner/root/schema/scope mismatch.'}
$payload070=Plain070 $plan070.payloadRoot
if($payload070 -ine (Join-Path (Split-Path (Plain070 $PlanPath) -Parent) 'payload')){throw 'Payload must remain adjacent to exact plan.'}
$receiptPath070=Join-Path $output070 ('apply070-'+$ExpectedPlanHash.ToLowerInvariant()+'.json')
$backup070=Join-Path $output070 ('backup070-'+$ExpectedPlanHash.ToLowerInvariant())
$lockPath070=Join-Path $game070 '.price070.lock'
$lock070=[IO.File]::Open($lockPath070,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{
    OwnStopped070
    $prepared070=@()
    if($Mode -ceq 'Apply'){
        if(Test-Path -LiteralPath $receiptPath070){
            $receipt070=[IO.File]::ReadAllText($receiptPath070,$encoding070)|ConvertFrom-Json
            if($receipt070.owner -cne $owner070 -or $receipt070.gameRoot -ine $game070 -or $receipt070.planHash -cne $ExpectedPlanHash.ToLowerInvariant()){throw 'Existing receipt mismatch.'}
            foreach($file070 in $plan070.files){if((CurrentHash070 (Target070 $file070.path)) -cne $file070.postHash){throw 'Applied postimage drift.'}}
            Write-Output 'ALREADY_APPLIED070';return
        }
        foreach($source070 in $plan070.sources){
            if($source070.path.StartsWith('@formula:')){continue}
            $sourcePath070=if($source070.path.StartsWith('@baseline:')){Join-Path (Plain070 $plan070.baselineDirectory) $source070.path.Substring(10)}elseif($source070.path -ceq '@profile'){Join-Path $game070 'config/Custom/PhantomMarket.ini'}else{Join-Path $game070 $source070.path}
            if($source070.path.Contains('..') -or (Hash070 $sourcePath070) -cne $source070.sha256){throw "Source conflict: $($source070.path)"}
        }
        foreach($file070 in $plan070.files){
            $target070=Target070 $file070.path
            if((CurrentHash070 $target070) -cne $file070.preHash){throw 'Exact native preimage conflict.'}
            $after070=Plain070 (Join-Path $payload070 $file070.path)
            if((Hash070 $after070) -cne $file070.postHash){throw 'Approved payload changed.'}
            $before070=if($file070.preHash -ceq 'MISSING'){$null}else{[IO.File]::ReadAllBytes($target070)}
            $backupPath070=Plain070 (Join-Path $backup070 $file070.path)
            New-Item -ItemType Directory -Path (Split-Path $backupPath070 -Parent) -Force|Out-Null
            if($null -ne $before070){if(!(Test-Path -LiteralPath $backupPath070)){[IO.File]::WriteAllBytes($backupPath070,$before070)};if((Hash070 $backupPath070) -cne $file070.preHash){throw 'Backup conflict.'}}
            $prepared070+=@{path=$target070;before=$before070;after=[IO.File]::ReadAllBytes($after070);preHash=$file070.preHash;postHash=$file070.postHash}
        }
    }else{
        if(!(Test-Path -LiteralPath $receiptPath070)){throw 'Rollback requires completed exact Apply receipt.'}
        $receipt070=[IO.File]::ReadAllText($receiptPath070,$encoding070)|ConvertFrom-Json
        if($receipt070.owner -cne $owner070 -or $receipt070.gameRoot -ine $game070 -or $receipt070.planHash -cne $ExpectedPlanHash.ToLowerInvariant() -or (Plain070 $receipt070.backupRoot) -ine $backup070){throw 'Rollback receipt mismatch.'}
        foreach($file070 in $plan070.files){
            $target070=Target070 $file070.path
            if((CurrentHash070 $target070) -cne $file070.postHash){throw 'Rollback preserves later changed postimages.'}
            $bytes070=$null
            if($file070.preHash -cne 'MISSING'){$backupPath070=Plain070 (Join-Path $backup070 $file070.path);if((Hash070 $backupPath070) -cne $file070.preHash){throw 'Rollback backup mismatch.'};$bytes070=[IO.File]::ReadAllBytes($backupPath070)}
            $prepared070+=@{path=$target070;before=[IO.File]::ReadAllBytes($target070);after=$bytes070;preHash=$file070.postHash;postHash=$file070.preHash}
        }
    }
    $committed070=@()
    try{
        foreach($file070 in $prepared070){
            if((CurrentHash070 $file070.path) -cne $file070.preHash){throw 'Concurrent preimage conflict.'}
            if($null -eq $file070.after){Remove-Item -LiteralPath $file070.path}else{Atomic070 $file070.path $file070.after}
            $committed070+=$file070
        }
        if($Mode -ceq 'Apply'){
            $receipt070=[ordered]@{owner=$owner070;gameRoot=$game070;planHash=$ExpectedPlanHash.ToLowerInvariant();backupRoot=$backup070;files=$plan070.files}
            New-Item -ItemType Directory -Path $output070 -Force|Out-Null
            [IO.File]::WriteAllText($receiptPath070,($receipt070|ConvertTo-Json -Depth 10),$encoding070)
        }
    }catch{
        foreach($file070 in $committed070){if((CurrentHash070 $file070.path) -ceq $file070.postHash){if($null -eq $file070.before){Remove-Item -LiteralPath $file070.path}else{Atomic070 $file070.path $file070.before}}}
        throw
    }
    Write-Output ($Mode.ToUpperInvariant()+'070 files='+$prepared070.Count+';no startup Apply;restart required.')
}finally{$lock070.Dispose();Remove-Item -LiteralPath $lockPath070}
