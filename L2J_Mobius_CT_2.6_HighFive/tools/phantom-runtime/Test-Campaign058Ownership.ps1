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
foreach ($variant in @('root','schema','role','owner','port')) {
    $changed=$release.Clone(); $root=$release.runtimeRoot; $schema=$release.schema
    switch ($variant) {
        'root' {$root='D:\PhantomWorld\M1-057-candidate'}
        'schema' {$schema='l2jmobiush5_phantom_test_058a'}
        'role' {$changed.role='CUMULATIVE_INTEGRATION'}
        'owner' {$changed.owner='TASK057'}
        'port' {$changed.gamePort='7792'}
    }
    $rejected=$false
    try {Assert-Campaign058Binding $root $schema $changed} catch {$rejected=$true}
    if (!$rejected) {throw "TASK058 release ownership negative failed: $variant"}; $count++
}
Write-Output "TASK058_OWNERSHIP_PASS=$count; DB_WRITES=0; PROCESS_ACTIONS=0"

$count059=0
foreach ($login059 in @('2106','2139')) {
    $release059=@{version='1';owner='TASK059_CAMPAIGN';role='RELEASE_CANDIDATE';schema='l2jmobiush5_phantom_release_059a';runtimeRoot='D:\PhantomWorld\M3-M7-059-candidate';layout=('LOGIN'+$login059);clientLoginPort=$login059;gamePort='7839';controlPort='9139'}
    Assert-Campaign058Binding $release059.runtimeRoot $release059.schema $release059
    $count059++
    foreach ($variant059 in @('root','schema','role','owner','layout','login','game','control')) {
        $changed059=$release059.Clone(); $root059=$release059.runtimeRoot; $schema059=$release059.schema
        switch ($variant059) {
            'root' {$root059='D:\PhantomWorld\M2-M7-058-candidate'}
            'schema' {$schema059='l2jmobiush5_phantom_release_058a'}
            'role' {$changed059.role='CUMULATIVE_INTEGRATION'}
            'owner' {$changed059.owner='TASK058_CAMPAIGN'}
            'layout' {$changed059.layout='LOGIN2138'}
            'login' {$changed059.clientLoginPort=$(if($login059 -ceq '2106'){'2139'}else{'2106'})}
            'game' {$changed059.gamePort='7838'}
            'control' {$changed059.controlPort='9138'}
        }
        $rejected059=$false
        try {Assert-Campaign058Binding $root059 $schema059 $changed059} catch {$rejected059=$true}
        if(!$rejected059){throw "TASK059 exact layout negative failed: $variant059/$login059"}
        $count059++
    }
}
Write-Output "TASK059_OWNERSHIP_PASS=$count059; DB_WRITES=0; PROCESS_ACTIONS=0"

$count060=0
foreach ($login060 in @('2106','2140')) {
    $release060=@{version='1';owner='TASK060_CAMPAIGN';role='RELEASE_CANDIDATE';schema='l2jmobiush5_phantom_release_060a';runtimeRoot='D:\PhantomWorld\Living-060-candidate';layout=('LOGIN'+$login060);clientLoginPort=$login060;gamePort='7840';controlPort='9140'}
    Assert-Campaign058Binding $release060.runtimeRoot $release060.schema $release060
    $count060++
    foreach ($variant060 in @('root','schema','role','owner','layout','login','game','control')) {
        $changed060=$release060.Clone(); $root060=$release060.runtimeRoot; $schema060=$release060.schema
        switch ($variant060) {
            'root' {$root060='D:\PhantomWorld\M2-M7-058-candidate'}
            'schema' {$schema060='l2jmobiush5_phantom_release_058a'}
            'role' {$changed060.role='CUMULATIVE_INTEGRATION'}
            'owner' {$changed060.owner='TASK058_CAMPAIGN'}
            'layout' {$changed060.layout='LOGIN2138'}
            'login' {$changed060.clientLoginPort=$(if($login060 -ceq '2106'){'2140'}else{'2106'})}
            'game' {$changed060.gamePort='7838'}
            'control' {$changed060.controlPort='9138'}
        }
        $rejected060=$false
        try {Assert-Campaign058Binding $root060 $schema060 $changed060} catch {$rejected060=$true}
        if(!$rejected060){throw "TASK060 exact layout negative failed: $variant060/$login060"}
        $count060++
    }
}
Write-Output "TASK060_OWNERSHIP_PASS=$count060; DB_WRITES=0; PROCESS_ACTIONS=0"
