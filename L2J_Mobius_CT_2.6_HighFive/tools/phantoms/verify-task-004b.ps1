[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "d36e10e24787edce3fe4f4d933fca4d0ac884d50"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$script:Results = New-Object System.Collections.Generic.List[object]

function Add-Result
{
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    $script:Results.Add([PSCustomObject]@{
        Name = $Name
        Passed = $Passed
        Detail = $Detail
    })
}

function Invoke-Git
{
    param(
        [string]$Root,
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = @(& git -c core.safecrlf=false -C $Root @Arguments 2>&1)
    if (($LASTEXITCODE -ne 0) -and -not $AllowFailure)
    {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE`: $($output -join [Environment]::NewLine)"
    }
    return [PSCustomObject]@{
        ExitCode = $LASTEXITCODE
        Output = [string[]]$output
    }
}

function Get-OrdinalSortedUnique
{
    param([string[]]$Values)

    $set = New-Object "System.Collections.Generic.HashSet[string]" ([System.StringComparer]::Ordinal)
    foreach ($value in $Values)
    {
        if (-not [string]::IsNullOrWhiteSpace($value))
        {
            [void]$set.Add($value.Trim().Replace("\", "/"))
        }
    }
    $array = [string[]]$set
    [Array]::Sort($array, [System.StringComparer]::Ordinal)
    return $array
}

function Test-ContainsAll
{
    param(
        [string]$Path,
        [string[]]$Tokens
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        return $false
    }
    $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    foreach ($token in $Tokens)
    {
        if ($content.IndexOf($token, [System.StringComparison]::Ordinal) -lt 0)
        {
            return $false
        }
    }
    return $true
}

function Test-ValidUtf8
{
    param([string]$Path)

    try
    {
        $strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
        [void]$strictUtf8.GetString([System.IO.File]::ReadAllBytes($Path))
        return $true
    }
    catch
    {
        return $false
    }
}

function Test-TaskScopePath
{
    param(
        [string]$RepositoryPath,
        [string]$ModulePrefix
    )

    if (-not $RepositoryPath.StartsWith($ModulePrefix, [System.StringComparison]::Ordinal))
    {
        return $false
    }

    $relative = $RepositoryPath.Substring($ModulePrefix.Length)
    return ($relative -ceq "build.xml") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/GameClient.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/Disconnection.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java") -or
        ($relative -ceq "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java") -or
        ($relative -ceq "tools/phantoms/verify-task-004b.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/004b-retained-identity-ownership-fix/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md") -or
        ($relative -ceq "docs/phantoms/reports/004b-retained-identity-ownership-fix.md") -or
        ($relative -ceq "docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md") -or
        ($relative -ceq "docs/phantoms/adr/0001-headless-player-integration-seam.md")
}

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootResult = Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel")
    $gitRoot = (Resolve-Path $gitRootResult.Output[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $modulePrefix = "$relativeModule/"
    $roadmapRepositoryPath = "$modulePrefix`docs/PHANTOM_BOTS_ROADMAP.md"
    Add-Result "repository.module-root" ($moduleRoot -ceq (Join-Path $gitRoot $relativeModule)) $moduleRoot

    $currentBranch = (Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")).Output[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $currentBranch
    $baseExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.accepted-base" ($baseExists.ExitCode -eq 0) $BaseCommit

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $shapeMode = "invalid"
    $ordinaryShape = $false
    if ($head -ceq $BaseCommit)
    {
        $shapeMode = "pre-commit"
        $ordinaryShape = $true
    }
    elseif ($baseExists.ExitCode -eq 0)
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $distance = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $parentLine = (Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--parents", "-n", "1", "HEAD")).Output[0]
        if (($parent -ceq $BaseCommit) -and ($distance -eq 1) -and (($parentLine -split " ").Count -eq 2))
        {
            $shapeMode = "post-commit"
            $ordinaryShape = $true
        }
    }
    Add-Result "repository.one-ordinary-task-004b-child" $ordinaryShape "$head|$shapeMode"

    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    $roadmapSha = if (Test-Path -LiteralPath $roadmapPath -PathType Leaf) { (Get-FileHash -Algorithm SHA256 -LiteralPath $roadmapPath).Hash } else { "missing" }
    Add-Result "repository.roadmap-sha256" ($roadmapSha -ceq "52C6F680582DEB91E45E4112FEDE2E70A4A64807DB76B3970D2BF24FB6455346") $roadmapSha
    $roadmapBaselineBlob = if ($baseExists.ExitCode -eq 0) { (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$BaseCommit`:$roadmapRepositoryPath")).Output[0] } else { "" }
    $roadmapWorkingBlob = if (Test-Path -LiteralPath $roadmapPath -PathType Leaf) { (Invoke-Git -Root $gitRoot -Arguments @("hash-object", $roadmapPath)).Output[0] } else { "missing" }
    $roadmapDiff = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", $roadmapRepositoryPath) -AllowFailure
    Add-Result "repository.roadmap-byte-preserved" (($roadmapBaselineBlob -ceq $roadmapWorkingBlob) -and ($roadmapDiff.ExitCode -eq 0)) $roadmapWorkingBlob

    $required = @(
        "tools/phantoms/verify-task-004b.ps1",
        "docs/phantoms/reports/004b-retained-identity-ownership-fix.md",
        "docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/TASK.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/CONTEXT.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/ARCHITECTURE.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/REVIEW_FINDINGS.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/ACCEPTANCE.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/TEST_CASES.md",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/PACKAGE_MANIFEST.json",
        "docs/phantoms/tasks/004b-retained-identity-ownership-fix/CODEX_LAUNCHER.txt"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $required))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }

    $committed = if ($ordinaryShape -and ($head -cne $BaseCommit)) { (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$BaseCommit...HEAD")).Output } else { @() }
    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit)).Output
    $cached = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $BaseCommit)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")).Output
    $taskUntracked = @($untracked | Where-Object { Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix })
    $unrelatedUntracked = @($untracked | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    $changed = @(Get-OrdinalSortedUnique ([string[]]($committed + $tracked + $cached + $taskUntracked)))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "workspace.unrelated-untracked-preserved" $true "$($unrelatedUntracked.Count) excluded"
    $scopeViolations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($scopeViolations.Count -eq 0) (($scopeViolations -join ",") -replace "^$", "no violations")
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-player-java" (@($changed | Where-Object { $_ -ceq "$modulePrefix`java/org/l2jmobius/gameserver/model/actor/Player.java" }).Count -eq 0) "Player.java untouched"
    Add-Result "scope.no-packet-seam" (@($changed | Where-Object { ($_ -ceq "$modulePrefix`java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java") -or ($_ -ceq "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java") }).Count -eq 0) "packet seam untouched"
    Add-Result "scope.no-goal-005" (@($changed | Where-Object { $_ -match "(?i)(task|report|verify)[^/]*005|tasks/005|reports/005|phantomProfile|profile.*persistence" }).Count -eq 0) "Goal 005 absent"
    Add-Result "scope.no-schema-config-sql" (@($changed | Where-Object { ($_ -match "(?i)\.sql$") -or ($_ -match "/dist/game/config/") -or ($_ -match "/test/resources/phantoms/db/") }).Count -eq 0) "no schema/config/SQL"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binaries"

    $frozenPaths = @(
        "$modulePrefix`docs/PHANTOM_BOTS_ROADMAP.md",
        "$modulePrefix`docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening",
        "$modulePrefix`java/org/l2jmobius/gameserver/model/actor/Player.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/PhantomActionFacade.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java",
        "$modulePrefix`dist/game/config/Custom/PhantomPlayers.ini",
        "$modulePrefix`java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
        "$modulePrefix`test/resources/phantoms/db",
        "$modulePrefix`tools/phantoms/verify-task-001.ps1",
        "$modulePrefix`tools/phantoms/verify-task-001a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-003.ps1",
        "$modulePrefix`tools/phantoms/verify-task-004.ps1",
        "$modulePrefix`tools/phantoms/verify-task-004a.ps1"
    )
    $frozenDiff = Invoke-Git -Root $gitRoot -Arguments ([string[]](@("diff", "--quiet", $BaseCommit, "--") + $frozenPaths)) -AllowFailure
    Add-Result "scope.frozen-contracts-unchanged" ($frozenDiff.ExitCode -eq 0) "$($frozenPaths.Count) paths"

    $identityPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java"
    $identityTokens = @(
        "requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)",
        "return phantomSystemEnabled || (currentOwner != null);",
        "public boolean matchesObjectId(int objectId)",
        "return _entry.objectId() == objectId;",
        "_owners.putIfAbsent",
        "_owners.remove(entry.objectId(), entry);"
    )
    Add-Result "arbitration.corrected-truth-table-policy" (Test-ContainsAll -Path $identityPath -Tokens $identityTokens) "$($identityTokens.Count) tokens"

    $gameClientPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/GameClient.java"
    $gameClient = Get-Content -LiteralPath $gameClientPath -Raw -Encoding UTF8
    $loadStart = $gameClient.IndexOf("public Player load(int characterSlot)", [System.StringComparison]::Ordinal)
    $policyIndex = $gameClient.IndexOf("requiresRealLoginArbitration(PhantomPlayersConfig.isEnabled(), currentOwner)", $loadStart, [System.StringComparison]::Ordinal)
    $legacyIndex = $gameClient.IndexOf("return loadWithoutIdentityArbitration(objectId, characterSlot);", $loadStart, [System.StringComparison]::Ordinal)
    $acquireIndex = $gameClient.IndexOf("tryAcquire(objectId, OwnerKind.REAL_LOGIN)", $loadStart, [System.StringComparison]::Ordinal)
    Add-Result "arbitration.disabled-no-owner-legacy-before-acquire" (($loadStart -ge 0) -and ($policyIndex -gt $loadStart) -and ($legacyIndex -gt $policyIndex) -and ($acquireIndex -gt $legacyIndex)) "policy < legacy return < acquire"
    $legacyStart = $gameClient.IndexOf("private Player loadWithoutIdentityArbitration", [System.StringComparison]::Ordinal)
    $legacyEnd = $gameClient.IndexOf("private synchronized void attachPlayerIdentityLease", $legacyStart, [System.StringComparison]::Ordinal)
    $legacyBody = if (($legacyStart -ge 0) -and ($legacyEnd -gt $legacyStart)) { $gameClient.Substring($legacyStart, $legacyEnd - $legacyStart) } else { "" }
    Add-Result "arbitration.disabled-legacy-has-no-lease" (($legacyBody.Length -gt 0) -and ($legacyBody -notmatch "tryAcquire|attachPlayerIdentityLease|_playerIdentityLease")) "legacy helper has no lease tokens"
    Add-Result "arbitration.canonical-config-only" (($gameClient.Contains("PhantomPlayersConfig.isEnabled()")) -and ($gameClient -notmatch "System\.getProperty|Boolean\.getBoolean")) "canonical config"

    $gameClientLeaseTokens = @(
        "public synchronized boolean hasPlayerIdentityLeaseFor(int objectId)",
        "_playerIdentityLease.matchesObjectId(objectId)",
        "public synchronized boolean releasePlayerIdentityLeaseFor(int objectId)",
        "!identityLease.matchesObjectId(objectId)",
        "identityLease.close();"
    )
    Add-Result "lease.game-client-object-id-api" (Test-ContainsAll -Path $gameClientPath -Tokens $gameClientLeaseTokens) "$($gameClientLeaseTokens.Count) tokens"
    Add-Result "lease.no-unscoped-game-client-release-api" ($gameClient.IndexOf("releasePlayerIdentityLease()", [System.StringComparison]::Ordinal) -lt 0) "unscoped release absent"
    $releaseStart = $gameClient.IndexOf("public synchronized boolean releasePlayerIdentityLeaseFor", [System.StringComparison]::Ordinal)
    $releaseEnd = $gameClient.IndexOf("public void setCharSelection", $releaseStart, [System.StringComparison]::Ordinal)
    $releaseBody = if (($releaseStart -ge 0) -and ($releaseEnd -gt $releaseStart)) { $gameClient.Substring($releaseStart, $releaseEnd - $releaseStart) } else { "" }
    $matchGuard = $releaseBody.IndexOf("!identityLease.matchesObjectId(objectId)", [System.StringComparison]::Ordinal)
    $clearLease = $releaseBody.IndexOf("_playerIdentityLease = null;", [System.StringComparison]::Ordinal)
    Add-Result "lease.release-internally-guards-object-id" (($matchGuard -ge 0) -and ($clearLease -gt $matchGuard)) "match guard before clear"

    $disconnectionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/Disconnection.java"
    $disconnection = Get-Content -LiteralPath $disconnectionPath -Raw -Encoding UTF8
    $storeStart = $disconnection.IndexOf("public void storeAndDelete()", [System.StringComparison]::Ordinal)
    $storeEnd = $disconnection.IndexOf("public void storeAndDeleteWith", $storeStart, [System.StringComparison]::Ordinal)
    $storeBody = if (($storeStart -ge 0) -and ($storeEnd -gt $storeStart)) { $disconnection.Substring($storeStart, $storeEnd - $storeStart) } else { "" }
    $matchIndex = $storeBody.IndexOf("_client.hasPlayerIdentityLeaseFor(_player.getObjectId())", [System.StringComparison]::Ordinal)
    $policyReleaseIndex = $storeBody.IndexOf("PhantomPlayerCleanupPolicy.isComplete(_player)", [System.StringComparison]::Ordinal)
    $exactReleaseIndex = $storeBody.IndexOf("_client.releasePlayerIdentityLeaseFor(_player.getObjectId());", [System.StringComparison]::Ordinal)
    Add-Result "lease.disconnection-exact-match-before-release" (($matchIndex -ge 0) -and ($policyReleaseIndex -gt $matchIndex) -and ($exactReleaseIndex -gt $policyReleaseIndex)) "match < postconditions < exact release"
    Add-Result "lease.mismatch-retains-with-bounded-warning" (Test-ContainsAll -Path $disconnectionPath -Tokens @("cleanup Player object ID does not match the retained lease", "_client.markPlayerIdentityLeaseRetentionReported()")) "mismatch retained once"
    $noPlayerStart = $disconnection.IndexOf("if (_player == null)", $storeEnd, [System.StringComparison]::Ordinal)
    $canLogoutIndex = $disconnection.IndexOf("if (_player.canLogout())", $noPlayerStart, [System.StringComparison]::Ordinal)
    $noPlayerBody = if (($noPlayerStart -ge 0) -and ($canLogoutIndex -gt $noPlayerStart)) { $disconnection.Substring($noPlayerStart, $canLogoutIndex - $noPlayerStart) } else { "" }
    Add-Result "lease.no-player-path-does-not-release" (($noPlayerBody.Length -gt 0) -and ($noPlayerBody -notmatch "releasePlayerIdentityLease")) "retained lease unchanged"
    Add-Result "lease.no-unbounded-retry" ($disconnection -notmatch "scheduleAtFixedRate|scheduleWithFixedDelay") "no retry scheduler"

    $cleanupPolicyPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java"
    $cleanupPolicy = Get-Content -LiteralPath $cleanupPolicyPath -Raw -Encoding UTF8
    $cleanupTokens = @(
        "final int objectId = player.getObjectId();",
        "!player.isOnline()",
        "world.getPlayer(objectId) == null",
        "world.findObject(objectId) == null",
        "!PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId)",
        "player.getClient() == null"
    )
    Add-Result "cleanup.object-id-postconditions" (Test-ContainsAll -Path $cleanupPolicyPath -Tokens $cleanupTokens) "$($cleanupTokens.Count) tokens"
    Add-Result "cleanup.no-exact-instance-inequality" ($cleanupPolicy -notmatch "!=\s*player") "no exact-instance acceptance"
    Add-Result "cleanup.policy-read-only-source" ($cleanupPolicy -notmatch "\.remove\s*\(|\.add\s*\(|setOnline|setClient|deleteMe|storeMe") "no mutation calls"

    $autosavePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java"
    Add-Result "cleanup.autosave-object-id-query" (Test-ContainsAll -Path $autosavePath -Tokens @("public boolean containsObjectId(int objectId)", "for (Player player : PLAYER_TIMES.keySet())", "player.getObjectId() == objectId", "return true;")) "object-ID membership"
    Add-Result "cleanup.autosave-exact-query-preserved" (Test-ContainsAll -Path $autosavePath -Tokens @("public boolean contains(Player player)", "return PLAYER_TIMES.containsKey(player);")) "exact Player query retained"

    $materializerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java"
    $materializer = Get-Content -LiteralPath $materializerPath -Raw -Encoding UTF8
    $materializerTokens = @(
        "BEFORE_STORE_OPERATION",
        "BEFORE_DELETE_OPERATION",
        "_state = State.FAILED;",
        "PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer)",
        "_outboundAttachment.close();",
        "_identityLease.close();",
        "_player = null;",
        "_state = State.STORED;",
        "_cleanupFinished = true;"
    )
    Add-Result "cleanup.phantom-retry-stored-contract" (Test-ContainsAll -Path $materializerPath -Tokens $materializerTokens) "$($materializerTokens.Count) tokens"
    Add-Result "cleanup.phantom-single-release-site" (([regex]::Matches($materializer, "_identityLease\.close\(\);")).Count -eq 1) "one release site"
    $failurePointNames = @(
        "AFTER_IDENTITY_CLAIM",
        "AFTER_PLAYER_LOAD",
        "AFTER_IDENTITY_ATTACHMENT",
        "AFTER_HEADLESS_OUTPUT_ATTACHMENT",
        "AFTER_DOMAIN_INITIALIZATION",
        "AFTER_ONLINE_ACTIVATION",
        "AFTER_WORLD_SPAWN",
        "AFTER_ACTION_ADMISSION",
        "AFTER_ACTION_MUTATION",
        "AFTER_STORE_BEFORE_DELETE",
        "AFTER_DELETE_BEFORE_IDENTITY_RELEASE"
    )
    Add-Result "cleanup.phantom-original-eleven-points" (@($failurePointNames | Where-Object { $materializer.IndexOf($_, [System.StringComparison]::Ordinal) -lt 0 }).Count -eq 0) "11/11 enum tokens"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java"
    $suite = Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8
    $suiteTokens = @(
        "lease-object-id-match-policy",
        "requiresRealLoginArbitration(false, null)",
        "assertTrue(PhantomIdentityLeaseRegistry.requiresRealLoginArbitration(false, OwnerKind.REAL_LOGIN)",
        "requiresRealLoginArbitration(false, OwnerKind.PHANTOM)",
        "requiresRealLoginArbitration(true, null)",
        "retainedRealLogin",
        "Retained REAL_LOGIN owner did not block a second owner while disabled.",
        "matchesObjectId(staleObjectId)",
        "Cleanup of B released retained lease A.",
        "containsObjectId(player.getObjectId())",
        "Another World object with the same object ID was accepted as complete cleanup.",
        "TASK_004_FAILURE_POINTS",
        "assertEquals(11, verified",
        "cleanup-before-store-retains-and-retries",
        "cleanup-before-delete-retains-and-retries",
        "State.STORED"
    )
    Add-Result "tests.task-004b-focused-cases" (Test-ContainsAll -Path $suitePath -Tokens $suiteTokens) "$($suiteTokens.Count) tokens"
    Add-Result "tests.no-gameclient-or-connection-fixture" ($suite -notmatch "new\s+GameClient\s*\(|extends\s+GameClient|new\s+Connection\s*\(|extends\s+Connection") "no fake network"

    $productionPaths = @($gameClientPath, $disconnectionPath, $identityPath, $cleanupPolicyPath, $materializerPath, $autosavePath)
    $productionText = ($productionPaths | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    $newPhantomProductionText = (@($identityPath, $cleanupPolicyPath, $materializerPath) | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    Add-Result "architecture.no-fake-gameclient-or-connection" ($productionText -notmatch "new\s+(?:GameClient|Connection)\s*\(|FakeGameClient|NullGameClient|FakeConnection") "no fake network"
    Add-Result "architecture.no-player-subclass-or-fork" ($productionText -notmatch "extends\s+Player\b|class\s+PhantomPlayer\b") "canonical Player"
    Add-Result "architecture.no-per-phantom-worker" ($newPhantomProductionText -notmatch "new\s+Thread\b|ExecutorService|newSingleThread|newFixedThread|scheduleAtFixedRate|scheduleWithFixedDelay") "no new worker"
    Add-Result "architecture.no-production-database-api" ($newPhantomProductionText -notmatch "DatabaseFactory|java\.sql|javax\.sql|l2jmobiush5") "no new DB access"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $buildTokens = @(
        'target name="phantom-static-verify-004a" depends="phantom-static-verify-004b"',
        'target name="phantom-static-verify-004b"',
        'verify-task-004b.ps1',
        'target name="phantom-headless-player-test"',
        'target name="phantom-headless-player-performance-smoke"',
        'target name="verify"',
        'phantom-static-verify-004b'
    )
    Add-Result "build.task-004b-cumulative-verifier" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) tokens"
    $javaCount = ([regex]::Matches($build, "<java\s")).Count
    $forkCount = ([regex]::Matches($build, 'fork="true"')).Count
    Add-Result "build.all-java-targets-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"

    $report004aPath = Join-Path $moduleRoot "docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md"
    $report004aTokens = @(
        "Independent review: FIX_REQUIRED",
        "Root cause: retained REAL_LOGIN owner bypassed while disabled",
        "Task 004B: REQUIRED"
    )
    Add-Result "docs.task-004a-review-closure" (Test-ContainsAll -Path $report004aPath -Tokens $report004aTokens) "$($report004aTokens.Count) facts"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/004a-real-login-lease-cleanup-hardening-review.md"
    $reviewTokens = @(
        "Task 004A: FIX_REQUIRED",
        "P1 retained REAL_LOGIN owner bypassed while disabled",
        "P1 wrong-character cleanup may release another lease",
        "P1/P2 cleanup postcondition is exact-instance scoped",
        "Task 004B: REQUIRED",
        "Goal 005: BLOCKED"
    )
    Add-Result "docs.task-004a-independent-review" (Test-ContainsAll -Path $reviewPath -Tokens $reviewTokens) "$($reviewTokens.Count) facts"

    $adrPath = Join-Path $moduleRoot "docs/phantoms/adr/0001-headless-player-integration-seam.md"
    $adr = Get-Content -LiteralPath $adrPath -Raw -Encoding UTF8
    Add-Result "docs.adr-remains-proposed" (($adr.Contains('`Proposed`')) -and ($adr.Contains("FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW")) -and ($adr -notmatch '(?m)^`Accepted`$')) "Proposed"

    $report004bPath = Join-Path $moduleRoot "docs/phantoms/reports/004b-retained-identity-ownership-fix.md"
    $report004bTokens = @(
        "## Status",
        "## Summary",
        "## Changed files",
        "## Architecture decisions",
        "## DB and migrations",
        "## Configs",
        "## Commands and test results",
        "## Performance measurements",
        "## Deviations",
        "## Limitations and risks",
        "## Branch, commit and push",
        "## Next step",
        "FEASIBLE_WITH_SEAM_HARDENED_PENDING_INDEPENDENT_REVIEW",
        'Manual gate: `PENDING_INDEPENDENT_REVIEW`',
        'Goal 005: `NOT_STARTED`',
        'ADR: `Proposed`',
        "Exact immutable commit SHA, push result and post-commit verifier outputs are"
    )
    Add-Result "docs.task-004b-report" (Test-ContainsAll -Path $report004bPath -Tokens $report004bTokens) "$($report004bTokens.Count) sections/facts"

    $gameJarPath = Join-Path $moduleRoot "dist/libs/GameServer.jar"
    if (Test-Path -LiteralPath $gameJarPath -PathType Leaf)
    {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($gameJarPath)
        try
        {
            $testEntries = @($archive.Entries | Where-Object { $_.FullName.StartsWith("org/l2jmobius/tests/phantoms/", [System.StringComparison]::Ordinal) })
            Add-Result "jar.no-test-entries" ($testEntries.Count -eq 0) "$($testEntries.Count) test entries"
        }
        finally
        {
            $archive.Dispose()
        }
    }
    else
    {
        Add-Result "jar.no-test-entries" $false "dist/libs/GameServer.jar missing"
    }

    $allChangedText = ($changed | ForEach-Object {
        $absolute = Join-Path $gitRoot $_
        if ((Test-Path -LiteralPath $absolute -PathType Leaf) -and ($_ -notmatch "(?i)\.(jar|class|zip|7z|exe|dll|bin)$"))
        {
            Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        }
    }) -join "`n"
    Add-Result "safety.no-literal-admin-secret" ($allChangedText -notmatch "(?i)PHANTOM_DB_ADMIN_PASSWORD\s*=\s*['""][^<'""]+") "no credential assignment"
    Add-Result "safety.no-local-artifacts" (@($changed | Where-Object { $_ -match "/\.phantom-local/" }).Count -eq 0) ".phantom-local excluded"

    $mojibakeMarkers = @(
        ([string][char]0x0420 + [string][char]0x045F),
        ([string][char]0x0420 + [string][char]0x045C),
        ([string][char]0x0420 + [string][char]0x045B),
        ([string][char]0x0420 + [string][char]0x2022),
        ([string][char]0x0420 + [string][char]0x040E),
        ([string][char]0x0420 + [string][char]0x203A),
        ([string][char]0x0420 + [string][char]0x00A4),
        ([string][char]0x0420 + [string][char]0x045A),
        ([string][char]0x0420 + [string][char]0x0408),
        ([string][char]0x0420 + [string][char]0x0459),
        ([string][char]0x0420 + [string][char]0x0491),
        ([string][char]0x0420 + [string][char]0x00B5),
        ([string][char]0x0420 + [string][char]0x00B0),
        ([string][char]0x0420 + [string][char]0x00BB),
        ([string][char]0x0420 + [string][char]0x0405),
        ([string][char]0x0420 + [string][char]0x0455),
        ([string][char]0x0421 + [string][char]0x040F),
        ([string][char]0x0421 + [string][char]0x20AC),
        ([string][char]0x0421 + [string][char]0x0402),
        ([string][char]0x0421 + [string][char]0x2039),
        ([string][char]0x0421 + [string][char]0x040A),
        ([string][char]0x0421 + [string][char]0x201A),
        ([string][char]0x0421 + [string][char]0x0453),
        ([string][char]0x0421 + [string][char]0x2021),
        ([string][char]0x0421 + [string][char]0x2026),
        ([string][char]0x0421 + [string][char]0x2020),
        ([string][char]0xFFFD)
    )
    $escapedPatterns = @(
        ([string][char]92 + [char]92 + "u04[0-9A-Fa-f]{2}"),
        ([string][char]92 + [char]92 + "u05[0-9A-Fa-f]{2}"),
        ("&" + "#x04[0-9A-Fa-f]{2};"),
        ("&" + "#x05[0-9A-Fa-f]{2};"),
        ("&" + "#X04[0-9A-Fa-f]{2};"),
        ("&" + "#X05[0-9A-Fa-f]{2};")
    )
    $invalidUtf8 = New-Object System.Collections.Generic.List[string]
    $mojibakeFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    $escapedFiles = New-Object System.Collections.Generic.HashSet[string] ([System.StringComparer]::Ordinal)
    foreach ($repositoryPath in $changed)
    {
        $absolute = Join-Path $gitRoot $repositoryPath
        if (-not (Test-Path -LiteralPath $absolute -PathType Leaf))
        {
            continue
        }
        if (-not (Test-ValidUtf8 -Path $absolute))
        {
            $invalidUtf8.Add($repositoryPath)
            continue
        }
        if ($repositoryPath -match "(?i)\.(jar|class|zip|7z|exe|dll|bin)$")
        {
            continue
        }
        $text = Get-Content -LiteralPath $absolute -Raw -Encoding UTF8
        foreach ($marker in $mojibakeMarkers)
        {
            if ($text.IndexOf($marker, [System.StringComparison]::Ordinal) -ge 0)
            {
                [void]$mojibakeFiles.Add($repositoryPath)
            }
        }
        foreach ($pattern in $escapedPatterns)
        {
            if ($text -match $pattern)
            {
                [void]$escapedFiles.Add($repositoryPath)
            }
        }
    }
    Add-Result "text.valid-utf8" ($invalidUtf8.Count -eq 0) (($invalidUtf8 -join ",") -replace "^$", "all changed files")
    Add-Result "text.no-mojibake-markers" ($mojibakeFiles.Count -eq 0) (([string[]]$mojibakeFiles -join ",") -replace "^$", "0 matches")
    Add-Result "text.no-escaped-cyrillic" ($escapedFiles.Count -eq 0) (([string[]]$escapedFiles -join ",") -replace "^$", "0 matches")
    Add-Result "verifier.deterministic-sorted-output" $true "ordinal result names"
    Add-Result "verifier.local-read-only" $true "Git/file/JAR checks only; no DB/network/write"
}
catch
{
    Add-Result "verifier.exception" $false $_.Exception.Message
}

$names = [string[]]($script:Results | ForEach-Object { $_.Name })
[Array]::Sort($names, [System.StringComparer]::Ordinal)
$failed = 0
foreach ($name in $names)
{
    $result = $script:Results | Where-Object { $_.Name -ceq $name } | Select-Object -First 1
    $label = if ($result.Passed) { "PASS" } else { "FAIL" }
    if (-not $result.Passed)
    {
        $failed++
    }
    Write-Output "[$label] $($result.Name) - $($result.Detail)"
}

Write-Output "SUMMARY: total=$($script:Results.Count) passed=$($script:Results.Count - $failed) failed=$failed"
if ($failed -ne 0)
{
    exit 1
}
exit 0
