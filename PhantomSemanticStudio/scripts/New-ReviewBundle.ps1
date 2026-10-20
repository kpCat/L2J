param([ValidatePattern('^PhantomSemanticStudio-review-[A-Za-z0-9-]+\.zip$')][string]$ZipName)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
function Assert-RegularPath([string]$path) {
    $current = Get-Item -LiteralPath $path -Force
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw ('Reparse point in review path: ' + $current.FullName) }
        $current = if ($current -is [IO.DirectoryInfo]) { $current.Parent } else { $current.Directory }
    }
}
Assert-RegularPath $root
$out = Join-Path $root 'artifacts'
if (Test-Path -LiteralPath $out) { Assert-RegularPath $out }
New-Item -ItemType Directory -Path $out -Force | Out-Null
$staging = Join-Path $out ('review-staging-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
try {
    $allowed = @('src','tests','scripts','docs','reports','.editorconfig','.gitignore','AGENTS.md','Directory.Build.props','PhantomSemanticStudio.sln','README_RU.md','START_HERE_RU.txt','Start.cmd','BASELINE_MANIFEST.json')
    foreach ($name in $allowed) {
        $source = Join-Path $root $name
        if (-not (Test-Path -LiteralPath $source)) { continue }
        Assert-RegularPath $source
        if (Test-Path -LiteralPath $source -PathType Leaf) { Copy-Item -LiteralPath $source -Destination (Join-Path $staging $name); continue }
        foreach ($file in Get-ChildItem -LiteralPath $source -File -Recurse) {
            Assert-RegularPath $file.FullName
            $relative = $file.FullName.Substring($root.Length).TrimStart('\','/')
            if ($relative -match '(^|[\\/])(bin|obj|\.vs|workspace|local|node_modules|cache)([\\/]|$)|\.(exe|dll|pdb|zip|user|suo|pem|key|pfx)$|(^|[\\/])\.env|settings\.json$|session\.json$|secrets?\.json$|live-drafts') { continue }
            $target = Join-Path $staging $relative
            New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $target
        }
    }
    $zip = Join-Path $out $(if ($ZipName) { $ZipName } else { 'PhantomSemanticStudio-review-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.zip' })
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -CompressionLevel Optimal
    Write-Host $zip
}
finally {
    $resolvedStaging = [IO.Path]::GetFullPath($staging)
    $resolvedOut = [IO.Path]::GetFullPath($out).TrimEnd('\') + '\'
    if (-not $resolvedStaging.StartsWith($resolvedOut, [StringComparison]::OrdinalIgnoreCase)) { throw 'Cleanup target outside artifacts' }
    Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
}
