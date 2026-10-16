Set-StrictMode -Version Latest

function Assert-Campaign058Binding {
    param([string]$RuntimeRoot,[string]$DatabaseName,[System.Collections.IDictionary]$Owner)
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
