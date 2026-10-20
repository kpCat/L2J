param([switch]$NegativeMutation, [switch]$GitScope, [switch]$Staged)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($root -match '[\\/]L2J_Mobius([\\/]|$)') { throw 'Run only in Studio.' }
$base = '9d5399e65c6c260ea2026c2da006cb48ba7bffce'
Push-Location $root
try {
    if ($NegativeMutation) {
        New-Item -ItemType Directory -Path (Join-Path $root 'artifacts/PSS-010') -Force | Out-Null
        $path = Join-Path $root 'src/PhantomSemanticStudio.Core/PackPreview.cs'
        $original = [IO.File]::ReadAllBytes($path)
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        $anchor = '.OrderBy(e => e.Id, StringComparer.Ordinal).ToArray();'
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($original)
        if (($text.Split($anchor).Count - 1) -ne 1) { throw 'Mutation anchor must be unique.' }
        try {
            [IO.File]::WriteAllText($path, $text.Replace($anchor, '.OrderBy(e => e.Id, StringComparer.Ordinal).Take(1).ToArray();'), [Text.UTF8Encoding]::new($false))
            & dotnet build tests/PhantomSemanticStudio.Tests/PhantomSemanticStudio.Tests.csproj -c Release --nologo > artifacts/PSS-010/mutation-build.txt 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'Mutation compilation failure is not product RED.' }
            & dotnet tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-010 > reports/PSS-010-negative.txt 2>&1
            $exitCode = $LASTEXITCODE
            $output = Get-Content -LiteralPath reports/PSS-010-negative.txt -Raw -Encoding UTF8
            Add-Content -LiteralPath reports/PSS-010-negative.txt -Value "MUTATION_EXIT=$exitCode; first-only selection; successful compilation" -Encoding utf8
            if ($exitCode -ne 1 -or $output -notmatch 'FAIL PSS-010 synthetic v1 привет' -or $output -notmatch 'FAIL PSS-010 synthetic v1 как дела') { throw 'Mutation did not reproduce both required runtime failures.' }
        }
        finally {
            [IO.File]::WriteAllBytes($path, $original)
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $hash) { throw 'Mutation bytes were not restored.' }
            Add-Content -LiteralPath reports/PSS-010-negative.txt -Value "RESTORED_SHA256=$($hash.ToLowerInvariant())" -Encoding utf8
        }
        & dotnet build tests/PhantomSemanticStudio.Tests/PhantomSemanticStudio.Tests.csproj -c Release --nologo > artifacts/PSS-010/restored-build.txt 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Restored compilation failed.' }
        & dotnet tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --pss-010 > reports/PSS-010-green.txt 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Restored final GREEN failed.' }
        Write-Output 'PASS runtime first-only negative RED; finally byte restore; restored targeted GREEN'
        exit 0
    }
    $owned = @(Get-Content -LiteralPath reports/PSS-010-owned-files.txt -Encoding UTF8)
    if ($owned.Count -ne @($owned | Select-Object -Unique).Count) { throw 'Duplicate exact inventory.' }
    $markers = @("Рџ","Рќ","Рћ","Р•","РЎ","Р›","Р¤","Рњ","РЈ","Рљ","Рґ","Рµ","Р°","Р»","РЅ","Рѕ","СЏ","С€","СЂ","С‹","СЊ","С‚","Сѓ","С‡","С…","С†","�")
    foreach ($relative in $owned) {
        if ($relative -match '\\|(^|/)(artifacts|workspace|labs|corpora|bin|obj)/|\.\.|\.(zip|db|sqlite|log|dll|exe|pdb)$|session\.json|settings\.json') { throw "Unsafe/private owned path: $relative" }
        $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
        if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Inventory outside Studio.' }
        $content = [Text.UTF8Encoding]::new($false, $true).GetString([IO.File]::ReadAllBytes($path))
        # Only the technical marker table itself is excluded, never user-facing text.
        if ($relative -eq 'scripts/Verify-PSS010.ps1') { $content = ($content -split "`n" | Where-Object { $_ -notmatch '^    \$markers =' }) -join "`n" }
        foreach ($marker in $markers) { if ($content.Contains($marker)) { throw "Mojibake: $relative" } }
    }
    Write-Output "PASS mojibake markers in changed files checked; strict UTF-8; files=$($owned.Count)"
    foreach ($relative in $owned) {
        $content = [IO.File]::ReadAllText((Join-Path $root $relative), [Text.UTF8Encoding]::new($false, $true))
        if ($content -match '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};') { throw "Escaped Cyrillic: $relative" }
    }
    Write-Output 'PASS escaped Cyrillic in changed files checked separately'
    $manifest = Get-Content -LiteralPath docs/tasks/PSS-010/PACKAGE_MANIFEST.json -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $manifest.files) {
        $path = Join-Path $root $entry.path
        if ((Get-Item -LiteralPath $path).Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw "Changed task package: $($entry.path)" }
    }
    $guards = Get-Content -LiteralPath reports/PSS-010-preservation.json -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $guards) {
        if ($owned -contains $entry.Path) { throw "Protected path in owned inventory: $($entry.Path)" }
        $path = Join-Path $root $entry.Path
        if ((Get-Item -LiteralPath $path).Length -ne $entry.Bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Sha256) { throw "Protected bytes changed: $($entry.Path)" }
    }
    Write-Output "PASS incoming task SHA/bytes; user .sln and all Designer/resx unchanged ($($guards.Count) guards)"
    $ignore = Get-Content -LiteralPath .gitignore -Raw -Encoding UTF8
    foreach ($pattern in @('artifacts/', 'workspace/', '*.zip', '*.db', '*.sqlite', '*.log')) { if (-not $ignore.Contains($pattern)) { throw "Missing private ignore: $pattern" } }
    $allowedSources = @('src/PhantomSemanticStudio.Core/PackPreview.cs','src/PhantomSemanticStudio.Core/DialogueLab.cs','src/PhantomSemanticStudio.Core/Models.cs','tests/PhantomSemanticStudio.Tests/Program.cs','tests/PhantomSemanticStudio.Tests/Pss010.cs','scripts/Verify-PSS010.ps1','README_RU.md')
    foreach ($path in $owned) {
        if ($allowedSources -notcontains $path -and $path -notmatch '^reports/PSS-010-[A-Za-z0-9-]+\.(md|txt|json)$' -and $path -notmatch '^docs/tasks/PSS-010/(ACCEPTANCE.md|CODEX_START_RU.txt|GOAL.md|HANDOFF.md|IMPLEMENTATION.md|PACKAGE_MANIFEST.json|REVIEW_CHECKLIST.md|ROOT_CAUSE.md|SAFETY_GIT.md|TEST_CASES.md)$') { throw "Outside bounded artifact family: $path" }
    }
    Write-Output "PASS bounded owner/privacy allowlist=$($owned.Count)"
    if ($GitScope -or $Staged) {
        if ((git remote get-url origin) -ne 'https://github.com/kpCat/PhantomSemanticStudio.git' -or (git remote get-url --push origin) -ne 'https://github.com/kpCat/PhantomSemanticStudio.git') { throw 'Wrong Studio origin.' }
        $changed = @(git diff --name-only $base)
        if ($LASTEXITCODE -ne 0) { throw 'Base inventory failed.' }
        $untracked = @(git ls-files --others --exclude-standard)
        if ($LASTEXITCODE -ne 0) { throw 'Untracked inventory failed.' }
        $actual = @(@($changed + $untracked) | Where-Object { $_ -ne 'PhantomSemanticStudio.sln' } | Sort-Object -Unique)
        if (@(Compare-Object $owned $actual).Count -ne 0) { throw 'Exact owned inventory differs from actual change inventory.' }
        git diff --check
        if ($LASTEXITCODE -ne 0) { throw 'Whitespace diff check failed.' }
        Write-Output "PASS exact artifact scope vs required base=$base; files=$($actual.Count); only protected .sln excluded"
        if ($Staged) {
            $stagedPaths = @(git diff --cached --name-only)
            if ($LASTEXITCODE -ne 0 -or @(Compare-Object $owned $stagedPaths).Count -ne 0) { throw 'Staged inventory differs from exact owners.' }
            git diff --cached --check
            if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace check failed.' }
            Write-Output "PASS exact staged allowlist=$($stagedPaths.Count); user .sln excluded"
        }
    }
}
finally { Pop-Location }
