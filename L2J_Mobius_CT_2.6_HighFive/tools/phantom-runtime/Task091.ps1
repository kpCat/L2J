[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare','Refresh','Verify','Compile','Focused','Preflight','Configure','Guards','Bootstrap','Native','ContinueNative','Connections','Regression')][string]$Action,
    [Parameter(Mandatory)][string]$Stage
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module091 = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')
$repo091 = Split-Path $module091 -Parent
if (![IO.Path]::IsPathRooted($Stage) -or $Stage -match '(^|[\\/])\.\.?([\\/]|$)') { throw 'TASK091_ABSOLUTE_STAGE_REQUIRED' }
$stage091 = [IO.Path]::GetFullPath($Stage).TrimEnd('\')
if ($stage091 -cne $Stage.TrimEnd('\') -or $stage091.StartsWith($repo091+'\',[StringComparison]::OrdinalIgnoreCase) -or !(Split-Path $stage091 -Leaf).StartsWith('phantom091-')) { throw 'TASK091_EXTERNAL_PHYSICAL_STAGE_REQUIRED' }
foreach ($path091 in @($module091,$stage091)) {
    $cursor091 = [IO.Path]::GetPathRoot($path091)
    foreach ($part091 in $path091.Substring($cursor091.Length).Split('\')) {
        if (!$part091) { continue }
        $cursor091 = Join-Path $cursor091 $part091
        if ((Test-Path -LiteralPath $cursor091) -and ([IO.File]::GetAttributes($cursor091) -band [IO.FileAttributes]::ReparsePoint)) { throw 'TASK091_REPARSE_REFUSED' }
    }
}
if ((& git -C $repo091 symbolic-ref --short HEAD) -cne 'feature/phantom-world' -or (& git -C $repo091 rev-parse HEAD) -cne '627bca947fd565ce18aa5cc85c7e1aa1288ccaef') { throw 'TASK091_BASELINE_DRIFT' }
$identity091 = [Security.Principal.WindowsIdentity]::GetCurrent()
if ($Action -eq 'Prepare') {
    if (Test-Path -LiteralPath $stage091) { throw 'TASK091_EXISTING_STAGE_REFUSED' }
    New-Item -ItemType Directory -Path $stage091 | Out-Null
    $acl091 = [Security.AccessControl.DirectorySecurity]::new()
    $acl091.SetOwner($identity091.User)
    $acl091.SetAccessRuleProtection($true,$false)
    foreach ($principal091 in @($identity091.User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $acl091.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal091,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    Set-Acl -LiteralPath $stage091 -AclObject $acl091
}
$actualAcl091 = Get-Acl -LiteralPath $stage091
if (!$actualAcl091.AreAccessRulesProtected) { throw 'TASK091_PRIVATE_ACL_REQUIRED' }
foreach ($rule091 in $actualAcl091.Access) {
    if ($rule091.AccessControlType -eq 'Allow' -and $rule091.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @($identity091.User.Value,'S-1-5-18')) { throw 'TASK091_PRIVATE_ACL_PRINCIPAL' }
}
& python -B (Join-Path $module091 'test/interregional_travel091.py') $Action.ToLowerInvariant() $module091 $stage091
exit $LASTEXITCODE
