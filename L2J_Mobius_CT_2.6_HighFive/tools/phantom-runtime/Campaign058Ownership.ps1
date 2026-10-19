Set-StrictMode -Version Latest

function Assert-Campaign058Binding {
    param([string]$RuntimeRoot,[string]$DatabaseName,[System.Collections.IDictionary]$Owner)
    if ($DatabaseName -ceq 'l2jmobiush5_phantom_release_061a' -or $Owner['owner'] -ceq 'TASK061_CAMPAIGN') { Assert-Campaign061Binding $RuntimeRoot $DatabaseName $Owner; return }
    if ($DatabaseName -ceq 'l2jmobiush5_phantom_release_060a' -or $Owner['owner'] -ceq 'TASK060_CAMPAIGN') { Assert-Campaign060Binding $RuntimeRoot $DatabaseName $Owner; return }
    if ($DatabaseName -ceq 'l2jmobiush5_phantom_release_059a' -or $Owner['owner'] -ceq 'TASK059_CAMPAIGN') { Assert-Campaign059Binding $RuntimeRoot $DatabaseName $Owner; return }
    $expected = switch ([string]$Owner['role']) {
        'CUMULATIVE_INTEGRATION' { @('D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract058a\runtime','l2jmobiush5_phantom_test_058a') }
        'RELEASE_CANDIDATE' { @('D:\PhantomWorld\M2-M7-058-candidate','l2jmobiush5_phantom_release_058a') }
        default { throw 'TASK058: недопустимая роль среды.' }
    }
    $actualRoot = [IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    if ($Owner['version'] -cne '1' -or $Owner['owner'] -cne 'TASK058_CAMPAIGN' -or $DatabaseName -cne $expected[1] -or $Owner['schema'] -cne $expected[1] -or $actualRoot -ine $expected[0] -or [IO.Path]::GetFullPath([string]$Owner['runtimeRoot']).TrimEnd('\') -ine $expected[0]) {
        throw 'TASK058: root/schema/owner не совпадают с точной binding.'
    }
    foreach ($pair in @(@('clientLoginPort','2138'),@('gamePort','7838'),@('controlPort','9138'))) {
        if ([string]$Owner[$pair[0]] -cne $pair[1]) { throw 'TASK058: порты не совпадают с manifest.' }
    }
    $current = [IO.Path]::GetPathRoot($actualRoot)
    foreach ($part in $actualRoot.Substring($current.Length).Split('\')) {
        if (!$part) { continue }
        $current = Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'TASK058: alias в runtime path запрещён.' }
    }
}

function Assert-Campaign059Binding {
    param([string]$RuntimeRoot,[string]$DatabaseName,[System.Collections.IDictionary]$Owner)
    $expected059='D:\PhantomWorld\M3-M7-059-candidate'
    $schema059='l2jmobiush5_phantom_release_059a'
    $actual059=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    $login059=switch ([string]$Owner['layout']) { 'LOGIN2106' {'2106'} 'LOGIN2139' {'2139'} default {throw 'TASK059: неизвестный exact layout.'} }
    if ($Owner['version'] -cne '1' -or $Owner['owner'] -cne 'TASK059_CAMPAIGN' -or $Owner['role'] -cne 'RELEASE_CANDIDATE' -or $DatabaseName -cne $schema059 -or $Owner['schema'] -cne $schema059 -or $actual059 -ine $expected059 -or [IO.Path]::GetFullPath([string]$Owner['runtimeRoot']).TrimEnd('\') -ine $expected059) {throw 'TASK059: root/schema/owner/role не совпадают с exact binding.'}
    foreach ($pair059 in @(@('clientLoginPort',$login059),@('gamePort','7839'),@('controlPort','9139'))) {if([string]$Owner[$pair059[0]] -cne $pair059[1]){throw 'TASK059: порты не совпадают с записанным layout.'}}
    $current059=[IO.Path]::GetPathRoot($actual059)
    foreach ($part059 in $actual059.Substring($current059.Length).Split('\')) {
        if(!$part059){continue}; $current059=Join-Path $current059 $part059
        if((Test-Path -LiteralPath $current059) -and ((Get-Item -LiteralPath $current059 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK059: alias в runtime path запрещён.'}
    }
}

function Assert-Campaign060Binding {
    param([string]$RuntimeRoot,[string]$DatabaseName,[System.Collections.IDictionary]$Owner)
    $expected060='D:\PhantomWorld\Living-060-candidate'
    $schema060='l2jmobiush5_phantom_release_060a'
    $actual060=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    $login060=switch ([string]$Owner['layout']) { 'LOGIN2106' {'2106'} 'LOGIN2140' {'2140'} default {throw 'TASK060: неизвестный exact layout.'} }
    if ($Owner['version'] -cne '1' -or $Owner['owner'] -cne 'TASK060_CAMPAIGN' -or $Owner['role'] -cne 'RELEASE_CANDIDATE' -or $DatabaseName -cne $schema060 -or $Owner['schema'] -cne $schema060 -or $actual060 -ine $expected060 -or [IO.Path]::GetFullPath([string]$Owner['runtimeRoot']).TrimEnd('\') -ine $expected060) {throw 'TASK060: root/schema/owner/role не совпадают с exact binding.'}
    foreach ($pair060 in @(@('clientLoginPort',$login060),@('gamePort','7840'),@('controlPort','9140'))) {if([string]$Owner[$pair060[0]] -cne $pair060[1]){throw 'TASK060: порты не совпадают с записанным layout.'}}
    $current060=[IO.Path]::GetPathRoot($actual060)
    foreach ($part060 in $actual060.Substring($current060.Length).Split('\')) {
        if(!$part060){continue}; $current060=Join-Path $current060 $part060
        if((Test-Path -LiteralPath $current060) -and ((Get-Item -LiteralPath $current060 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK060: alias в runtime path запрещён.'}
    }
}

function Assert-Campaign061Binding {
    param([string]$RuntimeRoot,[string]$DatabaseName,[System.Collections.IDictionary]$Owner)
    $expected061='D:\PhantomWorld\World-Career-061-candidate'
    $schema061='l2jmobiush5_phantom_release_061a'
    $actual061=[IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\')
    $login061=switch ([string]$Owner['layout']) { 'LOGIN2106' {'2106'} 'LOGIN2141' {'2141'} default {throw 'TASK061: неизвестный exact layout.'} }
    if ($Owner['version'] -cne '1' -or $Owner['owner'] -cne 'TASK061_CAMPAIGN' -or $Owner['role'] -cne 'RELEASE_CANDIDATE' -or $DatabaseName -cne $schema061 -or $Owner['schema'] -cne $schema061 -or $actual061 -ine $expected061 -or [IO.Path]::GetFullPath([string]$Owner['runtimeRoot']).TrimEnd('\') -ine $expected061) {throw 'TASK061: root/schema/owner/role не совпадают с exact binding.'}
    foreach ($pair061 in @(@('clientLoginPort',$login061),@('gamePort','7841'),@('controlPort','9141'))) {if([string]$Owner[$pair061[0]] -cne $pair061[1]){throw 'TASK061: порты не совпадают с записанным layout.'}}
    $current061=[IO.Path]::GetPathRoot($actual061)
    foreach ($part061 in $actual061.Substring($current061.Length).Split('\')) {
        if(!$part061){continue}; $current061=Join-Path $current061 $part061
        if((Test-Path -LiteralPath $current061) -and ((Get-Item -LiteralPath $current061 -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK061: alias в runtime path запрещён.'}
    }
}

function Read-Campaign058Owner {
    param([string]$Path)
    $owner = @{}
    foreach ($line in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($Path))) {
        if (!$line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Za-z][A-Za-z0-9]*)=(.+)$' -or $owner.ContainsKey($Matches[1])) { throw 'TASK058: некорректный owner manifest.' }
        $owner[$Matches[1]] = $Matches[2]
    }
    return $owner
}
