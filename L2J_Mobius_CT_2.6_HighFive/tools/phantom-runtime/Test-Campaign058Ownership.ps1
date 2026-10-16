$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Campaign058Ownership.ps1')
$cumulativeRoot = 'D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract058a\runtime'
$owner = @{version='1';owner='TASK058_CAMPAIGN';role='CUMULATIVE_INTEGRATION';schema='l2jmobiush5_phantom_test_058a';runtimeRoot=$cumulativeRoot;clientLoginPort='2138';gamePort='7838';controlPort='9138'}
Assert-Campaign058Binding $cumulativeRoot $owner.schema $owner
$count = 1
foreach ($variant in @('root','schema','role','owner','port')) {
    $changed = $owner.Clone()
    $root = $cumulativeRoot
    $schema = $owner.schema
    switch ($variant) {
        'root' { $root='D:\PhantomWorld\M1-057-candidate' }
        'schema' { $schema='l2jmobiush5_phantom_release_057a' }
        'role' { $changed.role='MUTABLE_FIXTURE' }
        'owner' { $changed.owner='TASK057' }
        'port' { $changed.gamePort='7792' }
    }
    $rejected = $false
    try { Assert-Campaign058Binding $root $schema $changed } catch { $rejected=$true }
    if (!$rejected) { throw "TASK058 ownership negative failed: $variant" }
    $count++
}
$release = $owner.Clone()
$release.role='RELEASE_CANDIDATE'
$release.schema='l2jmobiush5_phantom_release_058a'
$release.runtimeRoot='D:\PhantomWorld\M2-M7-058-candidate'
Assert-Campaign058Binding $release.runtimeRoot $release.schema $release
$count++
Write-Output "TASK058_OWNERSHIP_PASS=$count; DB_WRITES=0; PROCESS_ACTIONS=0"
