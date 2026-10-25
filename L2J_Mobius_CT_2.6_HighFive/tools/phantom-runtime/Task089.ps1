[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Prepare','Refresh','RefreshHarness','Compile','Focused','Geo')][string]$Action,
      [Parameter(Mandatory)][string]$Stage)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module089=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo089=Split-Path $module089 -Parent
$stage089=[IO.Path]::GetFullPath($Stage).TrimEnd('\')
$plain089=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
function Assert-Plain089([string]$Path) {
    if(![IO.Path]::IsPathRooted($Path) -or $Path -match '(^|[\\/])\.\.?([\\/]|$)'){throw 'TASK089 canonical absolute path required.'}
    $cursor089=[IO.Path]::GetPathRoot($Path)
    foreach($part089 in $Path.Substring($cursor089.Length).Split('\')) {
        if(!$part089){continue}; $cursor089=Join-Path $cursor089 $part089
        if($plain089.Add($cursor089) -and (Test-Path -LiteralPath $cursor089) -and ([IO.File]::GetAttributes($cursor089) -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK089 reparse path refused.'}
    }
}
Assert-Plain089 $Stage
Assert-Plain089 $module089
if($stage089 -cne $Stage.TrimEnd('\') -or $stage089.StartsWith($repo089+'\',[StringComparison]::OrdinalIgnoreCase) -or !(Split-Path $stage089 -Leaf).StartsWith('phantom089-')){throw 'TASK089 separate physical stage required.'}
if((& git -C $repo089 symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git -C $repo089 rev-parse HEAD) -cne '48a5c7f1f1b14cd0b09c4a461e4da7e4b4549560'){throw 'TASK089 source authority drift.'}
function Write-Text089([string]$Path,[string]$Text) { [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false)) }
$copy089=Join-Path $stage089 'source'
if($Action -eq 'Prepare') {
    if(Test-Path -LiteralPath $stage089){throw 'TASK089 refuses existing stage.'}
    New-Item -ItemType Directory -Path $stage089 | Out-Null
    $identity089=[Security.Principal.WindowsIdentity]::GetCurrent()
    $acl089=[Security.AccessControl.DirectorySecurity]::new()
    $acl089.SetOwner($identity089.User); $acl089.SetAccessRuleProtection($true,$false)
    foreach($principal089 in @($identity089.User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $acl089.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal089,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    Set-Acl -LiteralPath $stage089 -AclObject $acl089
    Write-Text089 (Join-Path $stage089 'owner.txt') ([guid]::NewGuid().ToString()+"`n"+$identity089.User.Value+"`n"+$module089)
    $rows089=[Collections.Generic.List[string]]::new()
    function Copy-Input089([string]$From,[string]$To) {
        Assert-Plain089 $From
        if((Get-Item -LiteralPath $From).PSIsContainer) {
            New-Item -ItemType Directory -Path $To -Force | Out-Null
            foreach($child089 in Get-ChildItem -LiteralPath $From -Force){Copy-Input089 $child089.FullName (Join-Path $To $child089.Name)}
        } else {
            if($From -ieq (Join-Path $module089 'dist/game/config/Database.ini')){return}
            New-Item -ItemType Directory -Path (Split-Path $To -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $From -Destination $To
            $digest089=(Get-FileHash -LiteralPath $From -Algorithm SHA256).Hash.ToLowerInvariant()
            if((Get-FileHash -LiteralPath $To -Algorithm SHA256).Hash.ToLowerInvariant() -cne $digest089){throw 'TASK089 copy mismatch.'}
            $rows089.Add($From+"`t"+$To+"`t"+$digest089)
        }
    }
    foreach($family089 in @('build.xml','java','test','tools','dist/game','dist/libs','dist/db_installer/sql','docs/phantoms/live-world')) {
        Copy-Input089 (Join-Path $module089 $family089) (Join-Path $copy089 $family089)
    }
    $geo089=Join-Path ([IO.Path]::GetPathRoot($module089)) 'PhantomWorld/Living-Economy-068-candidate/game/data/geodata'
    Assert-Plain089 $geo089
    $tiles089=@(Get-ChildItem -LiteralPath $geo089 -File -Filter '*.l2j')
    if($tiles089.Count -ne 203){throw 'TASK089 Geo203 required.'}
    foreach($tile089 in $tiles089){Copy-Input089 $tile089.FullName (Join-Path $copy089 ('dist/game/data/geodata/'+$tile089.Name))}
    Write-Text089 (Join-Path $stage089 'inputs.tsv') (($rows089 | Sort-Object) -join "`n")
    & python -B (Join-Path $module089 'test/region_travel087_source_audit.py') $module089 (Join-Path $stage089 'source-audit') *> (Join-Path $stage089 'source-audit.log')
    if($LASTEXITCODE){throw 'TASK089 source audit failed.'}
    Write-Output "TASK089_PREPARED inputs=$($rows089.Count); Geo=203; SQL=0; DatabaseConfig=ABSENT"
    return
}
$owner089=[IO.File]::ReadAllLines((Join-Path $stage089 'owner.txt'))
if($owner089.Count -ne 3 -or $owner089[1] -cne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value -or $owner089[2] -cne $module089){throw 'TASK089 owner mismatch.'}
$actualAcl089=Get-Acl -LiteralPath $stage089
if(!$actualAcl089.AreAccessRulesProtected){throw 'TASK089 protected DACL required.'}
foreach($rule089 in $actualAcl089.Access){if($rule089.AccessControlType -eq 'Allow' -and $rule089.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($owner089[1],'S-1-5-18')){throw 'TASK089 private ACL mismatch.'}}
if($Action -in @('Refresh','RefreshHarness')) {
    $refreshMode089=if($Action -eq 'Refresh'){'refresh'}else{'refresh_harness'}
    & python -B (Join-Path $module089 'test/region_travel089.py') $refreshMode089 $module089 $stage089
    exit $LASTEXITCODE
}
$verifyMode089=if($Action -eq 'Compile'){'verify'}else{'verify_execution'}
& python -B (Join-Path $module089 'test/region_travel089.py') $verifyMode089 $module089 $stage089
if($LASTEXITCODE){throw 'TASK089 exact input fence failed.'}
if($Action -eq 'Compile') {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $module089 'tools/OwnerConfig.ps1') -Action Verify *> (Join-Path $stage089 'owner-source-verify.log')
    if($LASTEXITCODE){throw 'TASK089 source OwnerConfig Verify failed.'}
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $copy089 'tools/OwnerConfig.ps1') -Action Apply -GameRoot (Join-Path $copy089 'dist/game') -ProfilePath (Join-Path $module089 'dist/game/config/OwnerConfig.json') *> (Join-Path $stage089 'owner-apply.log')
    if($LASTEXITCODE){throw 'TASK089 staged OwnerConfig Apply failed.'}
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $copy089 'tools/OwnerConfig.ps1') -Action Verify *> (Join-Path $stage089 'owner-copy-verify.log')
    if($LASTEXITCODE){throw 'TASK089 staged OwnerConfig Verify failed.'}
    if(Test-Path -LiteralPath (Join-Path $stage089 'compile.started')){throw 'TASK089 one compile only.'}
    Write-Text089 (Join-Path $stage089 'compile.started') 'compile-tests'
    & ant -f (Join-Path $copy089 'build.xml') "-Dbuild=$(Join-Path $stage089 'build')" compile-tests *> (Join-Path $stage089 'compile.log')
    if($LASTEXITCODE){throw 'TASK089 compile failed.'}
    & python -B (Join-Path $module089 'test/region_travel089.py') seal_build $module089 $stage089
    if($LASTEXITCODE){throw 'TASK089 compiled-output receipt failed.'}
    Write-Output 'TASK089_COMPILE_PASS shadowOnly=true; jar=NOT_RUN'
    return
}
$libs089=@(Get-ChildItem -LiteralPath (Join-Path $copy089 'dist/libs') -Filter '*.jar' -File | Where-Object {$_.Name -notmatch '^(GameServer|LoginServer)\.jar$|-sources\.jar$'} | ForEach-Object FullName)
$cp089=(Join-Path $stage089 'build/bin')+';'+(Join-Path $stage089 'build/phantom-test/bin')+';'+(Join-Path $stage089 'build/phantom-test/resources')+';'+($libs089 -join ';')
if($Action -eq 'Focused') {
    & java -Xmx256m -cp $cp089 org.l2jmobius.tests.phantoms.PhantomTravel089Test
    exit $LASTEXITCODE
}
Push-Location (Join-Path $copy089 'dist/game')
try {
    $stamp089=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $geoLog089=Join-Path $stage089 ("geo089-$stamp089.log")
    & java -Xmx4g -cp $cp089 org.l2jmobius.tests.phantoms.PhantomTravel089Geo (Join-Path $stage089 'source-audit/ingress-gk-candidates.tsv') (Join-Path $stage089 ("geo089-$stamp089.tsv")) *> $geoLog089
    $geoExit089=$LASTEXITCODE
    Get-Content -LiteralPath $geoLog089 -Tail 8
    exit $geoExit089
} finally { Pop-Location }
