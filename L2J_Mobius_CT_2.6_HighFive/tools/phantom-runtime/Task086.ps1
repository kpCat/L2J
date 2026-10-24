[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Prepare','Compile','Run')][string]$Action,
      [Parameter(Mandatory)][string]$Stage,
      [ValidateSet('guards','preflight','bootstrap','native','connections')][string]$Mode='guards',
      [ValidateRange(1,180000)][int]$MaximumWaitMillis=180000)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module086=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo086=Split-Path $module086 -Parent
$stage086=[IO.Path]::GetFullPath($Stage).TrimEnd('\')
$copy086=Join-Path $stage086 'source'
function Assert-Plain086([string]$Path){
    $full086=[IO.Path]::GetFullPath($Path)
    if(![IO.Path]::IsPathRooted($Path) -or $Path -match '(^|[\\/])\.\.?([\\/]|$)'){throw 'TASK086 absolute path without traversal required.'}
    $walk086=[IO.Path]::GetPathRoot($full086)
    foreach($part086 in $full086.Substring($walk086.Length).Split('\')){
        if(!$part086){continue};$walk086=Join-Path $walk086 $part086
        if((Test-Path -LiteralPath $walk086) -and ((Get-Item -LiteralPath $walk086 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK086 reparse path refused.'}
    }
}
foreach($path086 in @($module086,$stage086)){Assert-Plain086 $path086}
if($stage086.StartsWith($repo086+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'TASK086 external stage required.'}
if((& git -C $repo086 symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git -C $repo086 rev-parse HEAD) -cne '712e50efb2a448d1fcfad76a73bb8d73117ba9e8'){throw 'TASK086 baseline/branch drift.'}
$acl086=Get-Acl -LiteralPath $stage086
$sid086=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if(!$acl086.AreAccessRulesProtected -or $acl086.Owner -cne [Security.Principal.WindowsIdentity]::GetCurrent().Name){throw 'TASK086 own protected DACL required.'}
foreach($rule086 in $acl086.Access){if($rule086.AccessControlType -eq 'Allow' -and $rule086.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid086,'S-1-5-18')){throw 'TASK086 extra private ACL principal.'}}
function Write-Text086([string]$Path,[string]$Text){[IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))}
function Read-Properties086([string]$Path){
    $map086=@{}
    foreach($line086 in [IO.File]::ReadAllLines($Path)){if($line086 -match '^\s*([A-Za-z][A-Za-z0-9]*)\s*=\s*(.*?)\s*$'){$map086[$Matches[1]]=$Matches[2]}}
    return $map086
}
if($Action -eq 'Prepare'){
    if(Test-Path -LiteralPath $copy086){throw 'TASK086 refuses existing shadow.'}
    if(@(Get-Process -Name java,javaw -ErrorAction SilentlyContinue).Count){throw 'TASK086 foreign Java process present; no process action.'}
    $rows086=[Collections.Generic.List[string]]::new()
    function Copy-Input086([string]$From,[string]$To){
        Assert-Plain086 $From
        if((Get-Item -LiteralPath $From).PSIsContainer){
            New-Item -ItemType Directory -Path $To -Force | Out-Null
            foreach($child086 in Get-ChildItem -LiteralPath $From -Force){Copy-Input086 $child086.FullName (Join-Path $To $child086.Name)}
        }else{
            if($From -ieq (Join-Path $module086 'dist/game/config/Database.ini')){return}
            New-Item -ItemType Directory -Path (Split-Path $To -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $From -Destination $To
            $hash086=(Get-FileHash -LiteralPath $From -Algorithm SHA256).Hash.ToLowerInvariant()
            if((Get-FileHash -LiteralPath $To -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash086){throw 'TASK086 copy mismatch.'}
            $rows086.Add($From.Replace('\','/')+"`t"+$To.Replace('\','/')+"`t"+$hash086)
        }
    }
    foreach($family086 in @('build.xml','java','test','tools','dist/game','dist/libs','dist/db_installer/sql','docs/phantoms/reports/PHANTOM-NATIVE-FARM-REACHABILITY-086.md')){Copy-Input086 (Join-Path $module086 $family086) (Join-Path $copy086 $family086)}
    $geo086=Join-Path ([IO.Path]::GetPathRoot($module086)) 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
    Assert-Plain086 $geo086
    $tiles086=@(Get-ChildItem -LiteralPath $geo086 -File -Filter '*.l2j')
    if($tiles086.Count -ne 203){throw 'TASK086 physical203 Geo tiles required.'}
    foreach($tile086 in $tiles086){Copy-Input086 $tile086.FullName (Join-Path $copy086 ('dist/game/data/geodata/'+$tile086.Name))}
    Write-Text086 (Join-Path $stage086 'inputs.tsv') (($rows086 | Sort-Object) -join "`n")
    $privateConfig086=Join-Path $stage086 'private/Database.ini'
    Copy-Item -LiteralPath $privateConfig086 -Destination (Join-Path $copy086 'dist/game/config/Database.ini')
    $db086=Read-Properties086 $privateConfig086
    $loginHash086=[BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($db086['Login']))).Replace('-','').ToLowerInvariant()
    $id086=[guid]::NewGuid().ToString()
    $owner086="owner=TASK086_NATIVE_FARM`nschema=l2jmobiush5_phantom_test_086`nbaseline=712e50efb2a448d1fcfad76a73bb8d73117ba9e8`ninstallationId=$id086`nsourceModule=$($module086.Replace('\','/'))`nshadowModule=$($copy086.Replace('\','/'))`nstage=$($stage086.Replace('\','/'))`ngeoSource=$($geo086.Replace('\','/'))`nloginSha256=$loginHash086`nexpiresUtc=$([DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ'))`ninputsSha256=$((Get-FileHash -LiteralPath (Join-Path $stage086 'inputs.tsv') -Algorithm SHA256).Hash.ToLowerInvariant())`n"
    Write-Text086 (Join-Path $stage086 'private/owner.properties') $owner086
    & python (Join-Path $module086 'test/spot_diversity085_source_audit.py') $module086 (Join-Path $stage086 'source-audit') > (Join-Path $stage086 'source-audit.log')
    if($LASTEXITCODE){throw 'TASK086 source audit failed.'}
    Write-Output "TASK086_PREPARED inputs=$($rows086.Count); Geo203; freshUUID/privateACL; SQL=0"
    return
}
if($Action -eq 'Compile'){
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $copy086 'tools/OwnerConfig.ps1') -Action Verify *> (Join-Path $stage086 'owner-verify.log')
    if($LASTEXITCODE){throw 'TASK086 OwnerConfig Verify failed; compile not run.'}
    if(Test-Path -LiteralPath (Join-Path $stage086 'compile.started')){throw 'TASK086 one Ant compile only.'}
    Write-Text086 (Join-Path $stage086 'compile.started') 'one sequential compile-tests'
    & ant -f (Join-Path $copy086 'build.xml') "-Dbuild=$(Join-Path $stage086 'build')" compile-tests *> (Join-Path $stage086 'compile.log')
    if($LASTEXITCODE){throw 'TASK086 Ant compile failed; no repeat.'}
    Write-Output 'TASK086_COMPILED oneAnt=true; shadowOnly=true'
    return
}
$owner086=Read-Properties086 (Join-Path $stage086 'private/owner.properties')
$config086=Join-Path $copy086 'dist/game/config/Database.ini'
$db086=Read-Properties086 $config086
$libraries086=@(Get-ChildItem -LiteralPath (Join-Path $copy086 'dist/libs') -File -Filter '*.jar' | Where-Object {$_.Name -notmatch '^(GameServer|LoginServer)\.jar$|-sources\.jar$'} | ForEach-Object FullName)
$cp086=(Join-Path $stage086 'build/bin')+';'+(Join-Path $stage086 'build/phantom-test/bin')+';'+(Join-Path $stage086 'build/phantom-test/resources')+';'+($libraries086 -join ';')
if(!(Test-Path -LiteralPath (Join-Path $stage086 'build/phantom-test/bin'))){throw 'TASK086 fresh shadow compilation required.'}
if($Mode -eq 'guards'){New-Item -ItemType Junction -Path (Join-Path $stage086 'negative-junction') -Target $copy086 | Out-Null}
try{
    $psi086=[Diagnostics.ProcessStartInfo]::new()
    $psi086.FileName=(Get-Command java.exe).Source
    $psi086.WorkingDirectory=Join-Path $copy086 'dist/game'
    $psi086.UseShellExecute=$false
    $psi086.RedirectStandardOutput=$true;$psi086.RedirectStandardError=$true
    $psi086.Arguments='-Xms256m -Xmx4g -Dfile.encoding=UTF-8 -Duser.language=en -Dphantom.task086.native=true "-Dphantom.task086.root='+$stage086+'" "-Dphantom.task086.uuid='+$owner086['installationId']+'" "-Dphantom.module.root='+$copy086+'" "-Dphantom.test.config='+$config086+'" -cp "'+$cp086+'" org.l2jmobius.tests.phantoms.PhantomNativeFarmReachability086 '+$Mode
    $process086=[Diagnostics.Process]::Start($psi086)
    Write-Text086 (Join-Path $stage086 ('process-'+$Mode+'.json')) (([ordered]@{pid=$process086.Id;startUtc=$process086.StartTime.ToUniversalTime().ToString('o');mode=$Mode;stage=$stage086}) | ConvertTo-Json)
    $stdout086=$process086.StandardOutput.ReadToEndAsync();$stderr086=$process086.StandardError.ReadToEndAsync()
    $timedOut086=!$process086.WaitForExit($MaximumWaitMillis)
    if($timedOut086){
        # This Process handle belongs to Start above; never resolve or terminate a foreign PID.
        if(!$process086.HasExited){$process086.Kill()}
        if(!$process086.WaitForExit(10000)){throw 'TASK086 owned timeout process did not exit; unsafe stage STOP.'}
    }
    $text086=$stdout086.Result+"`n"+$stderr086.Result
    foreach($secret086 in @($db086['Password'],$db086['Login'])){if($secret086){$text086=$text086.Replace($secret086,'[REDACTED]')}}
    $log086=Join-Path $stage086 ($Mode+'-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.log')
    Write-Text086 $log086 $text086
    if($timedOut086){Write-Output "TASK086_OWNED_TIMEOUT stopped=true; sanitizedLog=$log086; noAutomaticRetry=true";exit 2}
    $text086 -split "`r?`n" | Where-Object {$_ -match '^TASK086_'} | Select-Object -Last 40 | Write-Output
    Write-Output "TASK086_MODE=$Mode exit=$($process086.ExitCode); sanitizedLog=$log086"
    exit $process086.ExitCode
}finally{
    if($Mode -eq 'guards' -and (Test-Path -LiteralPath (Join-Path $stage086 'negative-junction'))){[IO.Directory]::Delete((Join-Path $stage086 'negative-junction'))}
}
