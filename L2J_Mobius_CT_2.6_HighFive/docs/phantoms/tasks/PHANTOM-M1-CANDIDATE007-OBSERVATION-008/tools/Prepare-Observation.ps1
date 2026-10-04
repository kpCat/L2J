[CmdletBinding()]
param([ValidateSet('Freeze','VerifyCommit','Runtime','Validate')][string]$Phase, [Parameter(Mandatory)][string]$Repository)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = Join-Path $Repository 'L2J_Mobius_CT_2.6_HighFive'
$task = Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-CANDIDATE007-OBSERVATION-008'
$candidate = Join-Path $module 'docs/phantoms/tasks/PHANTOM-M1-LIVING-WORLD-CLOSEOUT-007/candidate-paused'
$manifest = Join-Path $candidate 'SOURCE_MANIFEST.tsv'
if ((Get-FileHash $manifest).Hash -cne '5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350') { throw 'Manifest hash mismatch.' }
$rows = @(Import-Csv $manifest -Delimiter "`t")
$integrated = @($rows | Where-Object state -like 'INTEGRATED_*')
$groups = @($rows | Group-Object state | Select-Object Name,Count)
$groups | Format-Table
if ($rows.Count -ne 161 -or $integrated.Count -ne 103) { throw 'Manifest count mismatch.' }
function Safe-Path([string]$Root,[string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or ($Relative -split '[/\\]' -contains '..')) { throw 'Unsafe relative path.' }
    $full = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (-not $full.StartsWith([IO.Path]::GetFullPath($Root).TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside root.' }
    $walk = $full
    while ($walk.Length -ge $Root.Length) {
        if ((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Reparse path forbidden.' }
        $walk = Split-Path -Parent $walk
    }
    return $full
}
foreach ($row in $rows) {
    $source = Safe-Path $candidate $row.artifact_path
    if ((Get-Item $source).Length -ne [long]$row.bytes -or (Get-FileHash $source).Hash -cne $row.sha256) { throw "Artifact mismatch: $($row.module_path)" }
}
if ($Phase -eq 'Freeze') {
    foreach ($row in $integrated) {
        $destination = Safe-Path $module $row.module_path
        $source = Safe-Path $candidate $row.artifact_path
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
        if ((Get-FileHash $destination).Hash -cne $row.sha256) { throw 'Destination hash mismatch.' }
    }
    $integrated | Export-Csv (Join-Path $task 'CANDIDATE_SOURCE.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    $publication = @($integrated | ForEach-Object { 'L2J_Mobius_CT_2.6_HighFive/'+$_.module_path })
    $publication += @(Get-ChildItem $task -File -Recurse | ForEach-Object { $_.FullName.Substring($Repository.TrimEnd('\').Length+1).Replace('\','/') })
    $private = Join-Path $module '.phantom-local/observe008'
    New-Item -ItemType Directory -Path $private -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $private 'snapshot-paths.nul'),($publication -join [char]0)+[char]0,[Text.UTF8Encoding]::new($false))
    Write-Output 'Verified 161 artifacts, overlaid 103 integrated entries, skipped 58 pending.'
} elseif ($Phase -eq 'VerifyCommit') {
    $export = Join-Path $module '.phantom-local/observe008/blob-check'
    New-Item -ItemType Directory -Path $export -Force | Out-Null
    foreach ($row in $integrated) {
        $info = [Diagnostics.ProcessStartInfo]::new('git',('show HEAD:L2J_Mobius_CT_2.6_HighFive/'+$row.module_path))
        $info.WorkingDirectory = $Repository
        $info.UseShellExecute = $false
        $info.RedirectStandardOutput = $true
        $process = [Diagnostics.Process]::Start($info)
        $file = Join-Path $export 'blob.bin'
        $stream = [IO.File]::Create($file)
        try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or (Get-FileHash $file).Hash -cne $row.sha256) { throw "Committed blob mismatch: $($row.module_path)" }
        if ((Get-FileHash (Safe-Path $module $row.module_path)).Hash -cne $row.sha256) { throw 'Source tree mismatch.' }
    }
    Write-Output 'Verified 103 committed blob and source tree SHA256 hashes.'
} elseif ($Phase -eq 'Validate') {
    $files=@($integrated | ForEach-Object { Safe-Path $module $_.module_path })
    $files+=@(Get-ChildItem $task -Recurse -File | Where-Object Extension -ne '.png' | ForEach-Object FullName)
    $mojibake="Рџ|Рќ|Рћ|Р•|РЎ|Р›|Р¤|Рњ|РЈ|Рљ|Рґ|Рµ|Р°|Р»|РЅ|Рѕ|СЏ|С€|СЂ|С‹|СЊ|С‚|Сѓ|С‡|С…|С†|�"
    $escaped='\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#x04[0-9A-Fa-f]{2};|&#x05[0-9A-Fa-f]{2};|&#X04[0-9A-Fa-f]{2};|&#X05[0-9A-Fa-f]{2};'
    $findings=@(foreach ($file in $files) {
        $text=[IO.File]::ReadAllText($file,[Text.UTF8Encoding]::new($false,$true))
        $scope=if ($file.StartsWith($task,[StringComparison]::OrdinalIgnoreCase)) { 'DOCS008' } else { 'FROZEN103' }
        foreach ($check in @(@{name='MOJIBAKE';pattern=$mojibake},@{name='ESCAPED_CYRILLIC';pattern=$escaped})) {
            $count=[regex]::Matches($text,$check.pattern).Count
            if ($count) { [pscustomobject]@{scope=$scope;path=$file.Substring($module.Length+1);check=$check.name;matches=$count} }
        }
    })
    $findings | Export-Csv (Join-Path $module '.phantom-local/observe008/encoding-findings.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    $findings | Format-Table
    Write-Output "Encoding scanned $($files.Count) exact text files."
    foreach ($script in @(Get-ChildItem (Join-Path $task 'tools') -Filter '*.ps1')) {
        $parseTokens=$null; $parseErrors=$null
        $null=[Management.Automation.Language.Parser]::ParseFile($script.FullName,[ref]$parseTokens,[ref]$parseErrors)
        if ($parseErrors.Count) { throw 'Task script parse failed.' }
    }
    Write-Output 'Task-local PS1 syntax passed.'
} else {
    $original = 'C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\artifacts\local-play\runtime'
    $private = Join-Path $module '.phantom-local/observe008'
    $runtime = Join-Path $private 'runtime'
    if (Test-Path $runtime) { throw 'Runtime already exists; no overwrite.' }
    $preserved = @(foreach ($part in @('game/config','login/config','libs','game/data/geodata','local-play/pids')) {
        Get-ChildItem (Join-Path $original $part) -Recurse -File | ForEach-Object {
            [pscustomobject]@{ path=$_.FullName.Substring($original.Length+1); sha256=(Get-FileHash $_.FullName).Hash }
        }
    })
    $preserved | Export-Csv (Join-Path $private 'original-preservation.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    New-Item -ItemType Directory -Path $runtime | Out-Null
    foreach ($part in @('game','login','libs')) { Copy-Item -LiteralPath (Join-Path $module "dist/$part") -Destination $runtime -Recurse }
    foreach ($part in @('game/config','login/config')) {
        Copy-Item -Path (Join-Path $original "$part/*") -Destination (Join-Path $runtime $part) -Recurse -Force
    }
    foreach ($role in @('game','login')) {
        Copy-Item -LiteralPath (Join-Path $original "$role/java.cfg") -Destination (Join-Path $runtime "$role/java.cfg") -Force
        Copy-Item -LiteralPath (Join-Path $original "$role/log.cfg") -Destination (Join-Path $runtime "$role/log.cfg") -Force
    }
    Copy-Item -Path (Join-Path $original 'game/data/geodata/*') -Destination (Join-Path $runtime 'game/data/geodata') -Force
    foreach ($entry in @($preserved | Where-Object path -like 'game\data\geodata\*')) {
        if ((Get-FileHash (Join-Path $runtime $entry.path)).Hash -cne $entry.sha256) { throw 'Geodata copy mismatch.' }
    }
    $changes = [Collections.Generic.List[object]]::new()
    function Override-Ini([string]$Relative,[string]$Key,[string]$Value) {
        $file = Join-Path $runtime $Relative
        $text = [IO.File]::ReadAllText($file)
        $pattern = '(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*)([^\r\n]*)'
        $matches = [regex]::Matches($text,$pattern)
        if ($matches.Count -ne 1) { throw "Expected one config key: $Relative/$Key" }
        $before = $matches[0].Groups[2].Value
        $text = [regex]::Replace($text,$pattern,{ param($match) $match.Groups[1].Value+$Value })
        [IO.File]::WriteAllText($file,$text,[Text.UTF8Encoding]::new($false))
        $changes.Add([pscustomobject]@{path=$Relative;key=$Key;before=$before;after=$Value})
    }
    foreach ($role in @('game','login')) {
        $file = Join-Path $runtime "$role/config/Database.ini"
        $url = [regex]::Match([IO.File]::ReadAllText($file),'(?m)^URL\s*=\s*([^\r\n]*)').Groups[1].Value
        if ($url -notmatch '^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/l2jmobiush5_localplay3\?') { throw 'Unexpected source DB.' }
        Override-Ini "$role/config/Database.ini" 'URL' ($url.Replace('/l2jmobiush5_localplay3?','/l2jmobiush5_localplay_observe008?'))
    }
    Override-Ini 'game/config/Server.ini' 'GameserverHostname' '127.0.0.1'
    Override-Ini 'login/config/Server.ini' 'LoginserverHostname' '127.0.0.1'
    Override-Ini 'game/config/Custom/PhantomPlayers.ini' 'MaxMaterializedPhantoms' '8'
    Override-Ini 'game/config/Custom/LocalPlayPilot.ini' 'EnableLocalPlayPilot' 'True'
    Override-Ini 'game/config/Custom/LocalPlayPilot.ini' 'EnableLocalPlaySyntheticHuman' 'False'
    $changes | Export-Csv (Join-Path $task 'CONFIG_OVERRIDES.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
    $scripts = @('LocalPlay-Ownership.ps1','Start-LocalPlay.ps1','Check-LocalPlay.ps1','LocalPlay-Pilot.ps1','Prepare-LocalPlayPilot.ps1','Get-LocalPlayPilot.ps1','Invoke-LocalPlayPilot.ps1','Stop-LocalPlayPilot.ps1','Stop-LocalPlay.ps1')
    foreach ($name in $scripts) { Copy-Item -LiteralPath (Join-Path $original $name) -Destination $runtime }
    $oldManifest = Get-Content (Join-Path $original 'local-play.json') -Raw | ConvertFrom-Json
    $flat = [ordered]@{}
    foreach ($property in $oldManifest.PSObject.Properties) { if ($property.Value -is [string] -or $property.Value -is [ValueType]) { $flat[$property.Name]=$property.Value } }
    $flat.databaseConfig='USER_CONFIRMED_EXISTING'
    $flat.databaseName='l2jmobiush5_localplay_observe008'
    $flat.materializedCap=8
    $flat.codeSha=(& git -C $Repository rev-parse HEAD).Trim()
    $flat.gameSourceCodeSha=$flat.codeSha
    $flat.gameJarSha256=(Get-FileHash (Join-Path $runtime 'libs/GameServer.jar')).Hash
    $flat.loginJarSha256=(Get-FileHash (Join-Path $runtime 'libs/LoginServer.jar')).Hash
    $flat | ConvertTo-Json | Set-Content (Join-Path $runtime 'local-play.json') -Encoding utf8
    foreach ($entry in $preserved) { if ((Get-FileHash (Join-Path $original $entry.path)).Hash -cne $entry.sha256) { throw 'Original runtime changed.' } }
    Write-Output "Runtime assembled: $runtime"
    Write-Output "Original preservation: $($preserved.Count) hashes verified."
    Write-Output "Game SHA256=$($flat.gameJarSha256); Login SHA256=$($flat.loginJarSha256)"
}
