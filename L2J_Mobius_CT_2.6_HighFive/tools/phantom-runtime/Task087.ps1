[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Prepare','Compile','Run')][string]$Action,
      [Parameter(Mandatory)][string]$Stage,
      [ValidateSet('guards','preflight','bootstrap','native','refine','connections')][string]$Mode='guards',
      [ValidateRange(1,180000)][int]$MaximumWaitMillis=180000)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module087=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo087=Split-Path $module087 -Parent
if(![IO.Path]::IsPathRooted($Stage) -or $Stage -match '(^|[\\/])\.\.?([\\/]|$)'){throw 'TASK087 raw absolute path without traversal required.'}
$stage087=[IO.Path]::GetFullPath($Stage).TrimEnd('\')
if($stage087 -ine $Stage.TrimEnd('\')){throw 'TASK087 canonical stage spelling required.'}
$copy087=Join-Path $stage087 'source'
function Assert-Plain087([string]$Path){
    $full087=[IO.Path]::GetFullPath($Path)
    if(![IO.Path]::IsPathRooted($Path) -or $Path -match '(^|[\\/])\.\.?([\\/]|$)'){throw 'TASK087 absolute path without traversal required.'}
    $walk087=[IO.Path]::GetPathRoot($full087)
    foreach($part087 in $full087.Substring($walk087.Length).Split('\')){
        if(!$part087){continue};$walk087=Join-Path $walk087 $part087
        if((Test-Path -LiteralPath $walk087) -and ((Get-Item -LiteralPath $walk087 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK087 reparse path refused.'}
    }
}
foreach($path087 in @($module087,$stage087)){Assert-Plain087 $path087}
if($stage087.StartsWith($repo087+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'TASK087 external stage required.'}
if((& git -C $repo087 symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git -C $repo087 rev-parse HEAD) -cne '826838d9a76cc68832df576f964781fe96e94339'){throw 'TASK087 baseline/branch drift.'}
if($Action -eq 'Prepare'){
    if(Test-Path -LiteralPath $stage087){throw 'TASK087 refuses existing stage.'}
    New-Item -ItemType Directory -Path $stage087 | Out-Null
    $identity087=[Security.Principal.WindowsIdentity]::GetCurrent()
    $privateAcl087=[Security.AccessControl.DirectorySecurity]::new()
    $privateAcl087.SetOwner($identity087.User)
    $privateAcl087.SetAccessRuleProtection($true,$false)
    foreach($principal087 in @($identity087.User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){
        $privateAcl087.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal087,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    Set-Acl -LiteralPath $stage087 -AclObject $privateAcl087
}
$acl087=Get-Acl -LiteralPath $stage087
$sid087=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if(!$acl087.AreAccessRulesProtected -or $acl087.Owner -cne [Security.Principal.WindowsIdentity]::GetCurrent().Name){throw 'TASK087 own protected DACL required.'}
foreach($rule087 in $acl087.Access){if($rule087.AccessControlType -eq 'Allow' -and $rule087.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($sid087,'S-1-5-18')){throw 'TASK087 extra private ACL principal.'}}
function Write-Text087([string]$Path,[string]$Text){[IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))}
function Read-Properties087([string]$Path){
    $map087=@{}
    foreach($line087 in [IO.File]::ReadAllLines($Path)){if($line087 -match '^\s*([A-Za-z][A-Za-z0-9]*)\s*=\s*(.*?)\s*$'){$map087[$Matches[1]]=$Matches[2]}}
    return $map087
}
if($Action -eq 'Prepare'){
    if(Test-Path -LiteralPath $copy087){throw 'TASK087 refuses existing shadow.'}
    if(@(Get-Process -Name java,javaw -ErrorAction SilentlyContinue).Count){throw 'TASK087 foreign Java process present; no process action.'}
    New-Item -ItemType Directory -Path (Join-Path $stage087 'private') | Out-Null
    $sourceDb087=Join-Path $module087 'dist/game/config/Database.ini'
    $sourceDbHash087=(Get-FileHash -LiteralPath $sourceDb087 -Algorithm SHA256).Hash.ToLowerInvariant()
    $sourceSettings087=Read-Properties087 $sourceDb087
    if($sourceSettings087['URL'] -notmatch '^jdbc:mysql://127\.0\.0\.1:3308/' -or !$sourceSettings087['Login'] -or !$sourceSettings087.ContainsKey('Password')){throw 'TASK087 source local endpoint/credentials unavailable.'}
    $privateText087="Driver=com.mysql.cj.jdbc.Driver`nURL=jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_087?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8`nLogin=$($sourceSettings087['Login'])`nPassword=$($sourceSettings087['Password'])`nMaximumDatabaseConnections=4`nTestDatabaseConnections=false`nBackupDatabase=false`n"
    Write-Text087 (Join-Path $stage087 'private/Database.ini') $privateText087
    $rows087=[Collections.Generic.List[string]]::new()
    function Copy-Input087([string]$From,[string]$To){
        Assert-Plain087 $From
        if((Get-Item -LiteralPath $From).PSIsContainer){
            New-Item -ItemType Directory -Path $To -Force | Out-Null
            foreach($child087 in Get-ChildItem -LiteralPath $From -Force){Copy-Input087 $child087.FullName (Join-Path $To $child087.Name)}
        }else{
            if($From -ieq (Join-Path $module087 'dist/game/config/Database.ini')){return}
            New-Item -ItemType Directory -Path (Split-Path $To -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $From -Destination $To
            $hash087=(Get-FileHash -LiteralPath $From -Algorithm SHA256).Hash.ToLowerInvariant()
            if((Get-FileHash -LiteralPath $To -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash087){throw 'TASK087 copy mismatch.'}
            $rows087.Add($From.Replace('\','/')+"`t"+$To.Replace('\','/')+"`t"+$hash087)
        }
    }
    foreach($family087 in @('build.xml','java','test','tools','dist/game','dist/libs','dist/db_installer/sql','docs/phantoms/live-world')){Copy-Input087 (Join-Path $module087 $family087) (Join-Path $copy087 $family087)}
    $geo087=Join-Path ([IO.Path]::GetPathRoot($module087)) 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
    Assert-Plain087 $geo087
    $tiles087=@(Get-ChildItem -LiteralPath $geo087 -File -Filter '*.l2j')
    if($tiles087.Count -ne 203){throw 'TASK087 physical203 Geo tiles required.'}
    foreach($tile087 in $tiles087){Copy-Input087 $tile087.FullName (Join-Path $copy087 ('dist/game/data/geodata/'+$tile087.Name))}
    Write-Text087 (Join-Path $stage087 'inputs.tsv') (($rows087 | Sort-Object) -join "`n")
    $privateConfig087=Join-Path $stage087 'private/Database.ini'
    Copy-Item -LiteralPath $privateConfig087 -Destination (Join-Path $copy087 'dist/game/config/Database.ini')
    $db087=Read-Properties087 $privateConfig087
    $loginHash087=[BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($db087['Login']))).Replace('-','').ToLowerInvariant()
    $id087=[guid]::NewGuid().ToString()
    $owner087="owner=TASK087_NATIVE_FARM`nschema=l2jmobiush5_phantom_test_087`nbaseline=826838d9a76cc68832df576f964781fe96e94339`ninstallationId=$id087`nsourceModule=$($module087.Replace('\','/'))`nshadowModule=$($copy087.Replace('\','/'))`nstage=$($stage087.Replace('\','/'))`ngeoSource=$($geo087.Replace('\','/'))`nloginSha256=$loginHash087`nexpiresUtc=$([DateTime]::UtcNow.AddHours(3).ToString('yyyy-MM-ddTHH:mm:ssZ'))`ninputsSha256=$((Get-FileHash -LiteralPath (Join-Path $stage087 'inputs.tsv') -Algorithm SHA256).Hash.ToLowerInvariant())`n"
    Write-Text087 (Join-Path $stage087 'private/owner.properties') $owner087
    Add-Content -LiteralPath (Join-Path $stage087 'private/owner.properties') -Encoding utf8 -Value "sourceDatabaseSha256=$sourceDbHash087"
    & python (Join-Path $module087 'test/spot_diversity085_source_audit.py') $module087 (Join-Path $stage087 'source-audit') > (Join-Path $stage087 'source-audit.log')
    if($LASTEXITCODE){throw 'TASK087 source audit failed.'}
    & python -B (Join-Path $module087 'test/region_travel087_source_audit.py') $module087 (Join-Path $stage087 'source-audit087') > (Join-Path $stage087 'source-audit087.log')
    if($LASTEXITCODE){throw 'TASK087 ingress source audit failed.'}
    Write-Output "TASK087_PREPARED inputs=$($rows087.Count); Geo203; freshUUID/privateACL; SQL=0"
    return
}
if($Action -eq 'Compile'){
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $copy087 'tools/OwnerConfig.ps1') -Action Verify *> (Join-Path $stage087 'owner-verify.log')
    if($LASTEXITCODE){throw 'TASK087 OwnerConfig Verify failed; compile not run.'}
    if(Test-Path -LiteralPath (Join-Path $stage087 'compile.started')){throw 'TASK087 one Ant compile only.'}
    Write-Text087 (Join-Path $stage087 'compile.started') 'one sequential compile-tests'
    & ant -f (Join-Path $copy087 'build.xml') "-Dbuild=$(Join-Path $stage087 'build')" compile-tests *> (Join-Path $stage087 'compile.log')
    if($LASTEXITCODE){throw 'TASK087 Ant compile failed; no repeat.'}
    Write-Output 'TASK087_COMPILED oneAnt=true; shadowOnly=true'
    return
}
$owner087=Read-Properties087 (Join-Path $stage087 'private/owner.properties')
$config087=Join-Path $copy087 'dist/game/config/Database.ini'
$db087=Read-Properties087 $config087
$libraries087=@(Get-ChildItem -LiteralPath (Join-Path $copy087 'dist/libs') -File -Filter '*.jar' | Where-Object {$_.Name -notmatch '^(GameServer|LoginServer)\.jar$|-sources\.jar$'} | ForEach-Object FullName)
$cp087=(Join-Path $stage087 'build/bin')+';'+(Join-Path $stage087 'build/phantom-test/bin')+';'+(Join-Path $stage087 'build/phantom-test/resources')+';'+($libraries087 -join ';')
if(!(Test-Path -LiteralPath (Join-Path $stage087 'build/phantom-test/bin'))){throw 'TASK087 fresh shadow compilation required.'}
$process087=$null
if($Mode -eq 'guards'){New-Item -ItemType Junction -Path (Join-Path $stage087 'negative-junction') -Target $copy087 | Out-Null}
try{
    $psi087=[Diagnostics.ProcessStartInfo]::new()
    $psi087.FileName=(Get-Command java.exe).Source
    $psi087.WorkingDirectory=Join-Path $copy087 'dist/game'
    $psi087.UseShellExecute=$false
    $psi087.RedirectStandardOutput=$true;$psi087.RedirectStandardError=$true
    $psi087.Arguments='-Xms256m -Xmx4g -Dfile.encoding=UTF-8 -Duser.language=en -Dphantom.task087.native=true "-Dphantom.task087.root='+$stage087+'" "-Dphantom.task087.uuid='+$owner087['installationId']+'" "-Dphantom.module.root='+$copy087+'" "-Dphantom.test.config='+$config087+'" -cp "'+$cp087+'" org.l2jmobius.tests.phantoms.PhantomUniversalRegionTravel087 '+$Mode
    $process087=[Diagnostics.Process]::Start($psi087)
    Write-Text087 (Join-Path $stage087 ('process-'+$Mode+'.json')) (([ordered]@{pid=$process087.Id;startUtc=$process087.StartTime.ToUniversalTime().ToString('o');mode=$Mode;stage=$stage087}) | ConvertTo-Json)
    $stdout087=$process087.StandardOutput.ReadToEndAsync();$stderr087=$process087.StandardError.ReadToEndAsync()
    $timedOut087=!$process087.WaitForExit($MaximumWaitMillis)
    if($timedOut087){
        # This Process handle belongs to Start above; never resolve or terminate a foreign PID.
        if(!$process087.HasExited){$process087.Kill()}
        if(!$process087.WaitForExit(10000)){throw 'TASK087 owned timeout process did not exit; unsafe stage STOP.'}
    }
    $text087=$stdout087.Result+"`n"+$stderr087.Result
    foreach($secret087 in @($db087['Password'],$db087['Login'])){if($secret087){$text087=$text087.Replace($secret087,'[REDACTED]')}}
    $log087=Join-Path $stage087 ($Mode+'-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.log')
    Write-Text087 $log087 $text087
    if($timedOut087){Write-Output "TASK087_OWNED_TIMEOUT stopped=true; sanitizedLog=$log087; noAutomaticRetry=true";exit 2}
    $text087 -split "`r?`n" | Where-Object {$_ -match '^TASK087_'} | Select-Object -Last 40 | Write-Output
    Write-Output "TASK087_MODE=$Mode exit=$($process087.ExitCode); sanitizedLog=$log087"
    exit $process087.ExitCode
}finally{
    if($null -ne $process087){
        # Only the handle returned by this invocation's Start is eligible for cleanup.
        if(!$process087.HasExited){
            if(!$process087.WaitForExit(5000)){
                $process087.Kill()
                if(!$process087.WaitForExit(10000)){throw 'TASK087 own process cleanup failed; STOP without PASS.'}
                Write-Output 'TASK087_EXCEPTION_CLEANUP ownedHandleStopped=true; result=FAIL'
            }
        }
        $process087.Dispose()
    }
    if($Mode -eq 'guards' -and (Test-Path -LiteralPath (Join-Path $stage087 'negative-junction'))){[IO.Directory]::Delete((Join-Path $stage087 'negative-junction'))}
}
