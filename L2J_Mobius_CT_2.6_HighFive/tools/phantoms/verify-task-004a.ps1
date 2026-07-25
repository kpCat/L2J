[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$ReviewedTask004 = "5b22b1ee9bab556cd5a14c2212dfa3f4119c4566"
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

function Test-RoadmapOnlyCommit
{
    param(
        [string]$GitRoot,
        [string]$Commit,
        [string]$RoadmapRepositoryPath
    )

    $paths = @(Get-OrdinalSortedUnique (Invoke-Git -Root $GitRoot -Arguments @("diff-tree", "--no-commit-id", "--name-only", "-r", $Commit)).Output)
    return ($paths.Count -eq 1) -and ($paths[0] -ceq $RoadmapRepositoryPath)
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
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java") -or
        $relative.StartsWith("test/java/org/l2jmobius/tests/phantoms/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "tools/phantoms/verify-task-004a.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/004-headless-player-feasibility-spike.md") -or
        ($relative -ceq "docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md") -or
        ($relative -ceq "docs/phantoms/reviews/004-headless-player-feasibility-spike-review.md") -or
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
    $reviewedExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$ReviewedTask004`^{commit}") -AllowFailure
    Add-Result "repository.reviewed-task-004" ($reviewedExists.ExitCode -eq 0) $ReviewedTask004
    $reviewedParent = if ($reviewedExists.ExitCode -eq 0) { (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$ReviewedTask004^")).Output[0] } else { "" }
    Add-Result "repository.reviewed-task-004-parent" ($reviewedParent -ceq "1ca74a3d96e8fa51612ef3e5145c7398abf60f6d") $reviewedParent

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $effectiveBaseline = $ReviewedTask004
    $baselineResolved = $false
    $shapeMode = "invalid"
    if ($head -ceq $ReviewedTask004)
    {
        $effectiveBaseline = $ReviewedTask004
        $baselineResolved = $true
        $shapeMode = "pre-commit-reviewed"
    }
    elseif ($reviewedExists.ExitCode -eq 0)
    {
        $reviewedAncestor = Invoke-Git -Root $gitRoot -Arguments @("merge-base", "--is-ancestor", $ReviewedTask004, $head) -AllowFailure
        if ($reviewedAncestor.ExitCode -eq 0)
        {
            $distance = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$ReviewedTask004..$head")).Output[0]
            if ($distance -eq 1)
            {
                $headParent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
                if (($headParent -ceq $ReviewedTask004) -and (Test-RoadmapOnlyCommit -GitRoot $gitRoot -Commit $head -RoadmapRepositoryPath $roadmapRepositoryPath))
                {
                    $effectiveBaseline = $head
                    $baselineResolved = $true
                    $shapeMode = "pre-commit-roadmap-child"
                }
                elseif ($headParent -ceq $ReviewedTask004)
                {
                    $effectiveBaseline = $ReviewedTask004
                    $baselineResolved = $true
                    $shapeMode = "post-commit-reviewed"
                }
            }
            elseif ($distance -eq 2)
            {
                $candidateBaseline = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
                $candidateParent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$candidateBaseline^")).Output[0]
                if (($candidateParent -ceq $ReviewedTask004) -and (Test-RoadmapOnlyCommit -GitRoot $gitRoot -Commit $candidateBaseline -RoadmapRepositoryPath $roadmapRepositoryPath))
                {
                    $effectiveBaseline = $candidateBaseline
                    $baselineResolved = $true
                    $shapeMode = "post-commit-roadmap-child"
                }
            }
        }
    }
    Add-Result "repository.effective-baseline" $baselineResolved "$effectiveBaseline|$shapeMode"

    $ordinaryShape = $false
    if ($baselineResolved)
    {
        if ($head -ceq $effectiveBaseline)
        {
            $ordinaryShape = $true
        }
        else
        {
            $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
            $count = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$effectiveBaseline..HEAD")).Output[0]
            $parentLine = (Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--parents", "-n", "1", "HEAD")).Output[0]
            $ordinaryShape = (($parent -ceq $effectiveBaseline) -and ($count -eq 1) -and (($parentLine -split " ").Count -eq 2))
        }
    }
    Add-Result "repository.one-ordinary-task-004a-child" $ordinaryShape $shapeMode

    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    $roadmapBaselineBlob = if ($baselineResolved) { (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$effectiveBaseline`:$roadmapRepositoryPath")).Output[0] } else { "" }
    $roadmapWorkingBlob = if (Test-Path -LiteralPath $roadmapPath -PathType Leaf) { (Invoke-Git -Root $gitRoot -Arguments @("hash-object", $roadmapPath)).Output[0] } else { "missing" }
    $roadmapDiff = if ($baselineResolved) { Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $effectiveBaseline, "--", $roadmapRepositoryPath) -AllowFailure } else { [PSCustomObject]@{ ExitCode = 1 } }
    Add-Result "repository.roadmap-byte-preserved" (($roadmapBaselineBlob -ceq $roadmapWorkingBlob) -and ($roadmapDiff.ExitCode -eq 0)) $roadmapWorkingBlob

    $required = @(
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java",
        "tools/phantoms/verify-task-004a.ps1",
        "docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md",
        "docs/phantoms/reviews/004-headless-player-feasibility-spike-review.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/TASK.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/CONTEXT.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/ARCHITECTURE.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/REVIEW_FINDINGS.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/ACCEPTANCE.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/TEST_CASES.md",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/PACKAGE_MANIFEST.json",
        "docs/phantoms/tasks/004a-real-login-lease-cleanup-hardening/CODEX_LAUNCHER.txt"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $required))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }

    $committed = @()
    if ($baselineResolved -and ($head -cne $effectiveBaseline))
    {
        $committed = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$effectiveBaseline...HEAD")).Output
    }
    $tracked = if ($baselineResolved) { (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $effectiveBaseline)).Output } else { @() }
    $cached = if ($baselineResolved) { (Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $effectiveBaseline)).Output } else { @() }
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
    Add-Result "scope.no-goal-005" (@($changed | Where-Object { $_ -match "(?i)(task|report|verify)[^/]*005|tasks/005|reports/005|phantomProfile|profile.*persistence" }).Count -eq 0) "Goal 005 absent"
    Add-Result "scope.no-schema-config-sql" (@($changed | Where-Object { ($_ -match "(?i)\.sql$") -or ($_ -match "/dist/game/config/") -or ($_ -match "/test/resources/phantoms/db/") }).Count -eq 0) "no schema/config/SQL"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binaries"
    $changedClientHandlers = @($changed | Where-Object { $_.StartsWith("$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/", [System.StringComparison]::Ordinal) })
    Add-Result "scope.only-character-select-handler" (($changedClientHandlers.Count -eq 1) -and ($changedClientHandlers[0] -ceq "$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java")) ($changedClientHandlers -join ",")

    $frozenPaths = @(
        "$modulePrefix`tools/phantoms/verify-task-001.ps1",
        "$modulePrefix`tools/phantoms/verify-task-001a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-003.ps1",
        "$modulePrefix`tools/phantoms/verify-task-004.ps1",
        "$modulePrefix`java/org/l2jmobius/gameserver/model/actor/Player.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/phantoms/player/PhantomActionFacade.java",
        "$modulePrefix`dist/game/config/Custom/PhantomPlayers.ini",
        "$modulePrefix`java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
        "$modulePrefix`test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql",
        "$modulePrefix`test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql"
    )
    $frozenDiff = if ($baselineResolved) { Invoke-Git -Root $gitRoot -Arguments ([string[]](@("diff", "--quiet", $effectiveBaseline, "--") + $frozenPaths)) -AllowFailure } else { [PSCustomObject]@{ ExitCode = 1 } }
    Add-Result "scope.prior-contracts-and-config-unchanged" ($frozenDiff.ExitCode -eq 0) "$($frozenPaths.Count) paths"

    $identityPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java"
    $identityTokens = @(
        "requiresRealLoginArbitration(boolean phantomSystemEnabled, OwnerKind currentOwner)",
        "return phantomSystemEnabled || (currentOwner == OwnerKind.PHANTOM);",
        "REAL_LOGIN,",
        "PHANTOM",
        "_owners.putIfAbsent",
        "_owners.remove(entry.objectId(), entry);"
    )
    Add-Result "arbitration.pure-truth-table-policy" (Test-ContainsAll -Path $identityPath -Tokens $identityTokens) "$($identityTokens.Count) tokens"

    $gameClientPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/GameClient.java"
    $gameClient = Get-Content -LiteralPath $gameClientPath -Raw -Encoding UTF8
    $loadStart = $gameClient.IndexOf("public Player load(int characterSlot)", [System.StringComparison]::Ordinal)
    $policyIndex = $gameClient.IndexOf("requiresRealLoginArbitration(PhantomPlayersConfig.isEnabled(), currentOwner)", $loadStart, [System.StringComparison]::Ordinal)
    $legacyIndex = $gameClient.IndexOf("return loadWithoutIdentityArbitration(objectId, characterSlot);", $loadStart, [System.StringComparison]::Ordinal)
    $acquireIndex = $gameClient.IndexOf("tryAcquire(objectId, OwnerKind.REAL_LOGIN)", $loadStart, [System.StringComparison]::Ordinal)
    Add-Result "arbitration.game-client-disabled-branch-before-acquire" (($loadStart -ge 0) -and ($policyIndex -gt $loadStart) -and ($legacyIndex -gt $policyIndex) -and ($acquireIndex -gt $legacyIndex)) "policy < legacy return < acquire"
    $legacyStart = $gameClient.IndexOf("private Player loadWithoutIdentityArbitration", [System.StringComparison]::Ordinal)
    $legacyEnd = $gameClient.IndexOf("private synchronized void attachPlayerIdentityLease", $legacyStart, [System.StringComparison]::Ordinal)
    $legacyBody = if (($legacyStart -ge 0) -and ($legacyEnd -gt $legacyStart)) { $gameClient.Substring($legacyStart, $legacyEnd - $legacyStart) } else { "" }
    Add-Result "arbitration.disabled-legacy-has-no-lease" (($legacyBody.Length -gt 0) -and ($legacyBody -notmatch "tryAcquire|attachPlayerIdentityLease|_playerIdentityLease")) "legacy helper has no lease tokens"
    Add-Result "arbitration.canonical-config-only" (($gameClient.Contains("PhantomPlayersConfig.isEnabled()")) -and ($gameClient -notmatch "System\.getProperty|Boolean\.getBoolean")) "canonical config"

    $disconnectStart = $gameClient.IndexOf("public void onDisconnection()", [System.StringComparison]::Ordinal)
    $lockIndex = $gameClient.IndexOf("_playerLock.lock();", $disconnectStart, [System.StringComparison]::Ordinal)
    $disconnectedIndex = $gameClient.IndexOf("_connectionState = ConnectionState.DISCONNECTED;", $disconnectStart, [System.StringComparison]::Ordinal)
    $cleanupIndex = $gameClient.IndexOf("Disconnection.of(this).onDisconnection();", $disconnectStart, [System.StringComparison]::Ordinal)
    $unlockIndex = $gameClient.IndexOf("_playerLock.unlock();", $disconnectStart, [System.StringComparison]::Ordinal)
    Add-Result "synchronization.disconnect-same-player-lock" (($disconnectStart -ge 0) -and ($lockIndex -gt $disconnectStart) -and ($disconnectedIndex -gt $lockIndex) -and ($cleanupIndex -gt $disconnectedIndex) -and ($unlockIndex -gt $cleanupIndex)) "lock < DISCONNECTED < cleanup < unlock"
    Add-Result "synchronization.disconnect-unlock-finally" (Test-ContainsAll -Path $gameClientPath -Tokens @("_playerLock.lock();", "finally", "_playerLock.unlock();")) "finally unlock"

    $characterSelectPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java"
    $characterSelect = Get-Content -LiteralPath $characterSelectPath -Raw -Encoding UTF8
    $selectLock = $characterSelect.IndexOf("client.getPlayerLock().tryLock()", [System.StringComparison]::Ordinal)
    $stateCheck = $characterSelect.IndexOf("client.getConnectionState() != ConnectionState.AUTHENTICATED", $selectLock, [System.StringComparison]::Ordinal)
    $selectLoad = $characterSelect.IndexOf("client.load(_charSlot)", $selectLock, [System.StringComparison]::Ordinal)
    $selectBind = $characterSelect.IndexOf("cha.setClient(client);", $selectLock, [System.StringComparison]::Ordinal)
    $selectUnlock = $characterSelect.IndexOf("client.getPlayerLock().unlock();", $selectLock, [System.StringComparison]::Ordinal)
    Add-Result "synchronization.character-select-state-before-load-bind" (($selectLock -ge 0) -and ($stateCheck -gt $selectLock) -and ($selectLoad -gt $stateCheck) -and ($selectBind -gt $selectLoad) -and ($selectUnlock -gt $selectBind)) "lock < AUTHENTICATED < load < bind < unlock"

    $autosavePath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java"
    Add-Result "cleanup.autosave-narrow-membership" (Test-ContainsAll -Path $autosavePath -Tokens @("public boolean contains(Player player)", "return PLAYER_TIMES.containsKey(player);")) "exact Player membership"

    $cleanupPolicyPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerCleanupPolicy.java"
    $cleanupPolicy = if (Test-Path -LiteralPath $cleanupPolicyPath) { Get-Content -LiteralPath $cleanupPolicyPath -Raw -Encoding UTF8 } else { "" }
    $cleanupTokens = @(
        "public static boolean isComplete(Player player)",
        "!player.isOnline()",
        "world.getPlayer(player.getObjectId()) != player",
        "world.findObject(player.getObjectId()) != player",
        "!PlayerAutoSaveTaskManager.getInstance().contains(player)",
        "player.getClient() == null"
    )
    Add-Result "cleanup.shared-four-postconditions" (Test-ContainsAll -Path $cleanupPolicyPath -Tokens $cleanupTokens) "$($cleanupTokens.Count) tokens"
    Add-Result "cleanup.policy-read-only-source" ($cleanupPolicy -notmatch "\.remove\s*\(|\.add\s*\(|setOnline|setClient|deleteMe|storeMe") "no mutation calls"

    $disconnectionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/Disconnection.java"
    $disconnection = Get-Content -LiteralPath $disconnectionPath -Raw -Encoding UTF8
    $storeStart = $disconnection.IndexOf("public void storeAndDelete()", [System.StringComparison]::Ordinal)
    $storeEnd = $disconnection.IndexOf("public void storeAndDeleteWith", $storeStart, [System.StringComparison]::Ordinal)
    $storeBody = if (($storeStart -ge 0) -and ($storeEnd -gt $storeStart)) { $disconnection.Substring($storeStart, $storeEnd - $storeStart) } else { "" }
    $realCleanupTokens = @(
        "_client.hasPlayerIdentityLease()",
        "(failure == null)",
        "PhantomPlayerCleanupPolicy.isComplete(_player)",
        "_client.releasePlayerIdentityLease();",
        "_client.markPlayerIdentityLeaseRetentionReported()",
        "REAL_LOGIN identity lease retained"
    )
    Add-Result "cleanup.real-login-fail-closed-release" (Test-ContainsAll -Path $disconnectionPath -Tokens $realCleanupTokens) "$($realCleanupTokens.Count) tokens"
    Add-Result "cleanup.real-login-no-finally-release" (($storeBody.Length -gt 0) -and ($storeBody -notmatch "\bfinally\b") -and ($disconnection -notmatch "finally[\s\S]{0,180}releasePlayerIdentityLease")) "no unconditional final release"
    Add-Result "cleanup.real-login-delayed-reuses-same-rule" ($disconnection.Contains("ThreadPool.schedule(this::storeAndDelete, AttackStanceTaskManager.COMBAT_TIME);")) "one bounded delayed attempt"
    Add-Result "cleanup.real-login-no-retry-loop" ($disconnection -notmatch "scheduleAtFixedRate|scheduleWithFixedDelay|while\s*\(|for\s*\(") "no retry loop"

    $materializerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java"
    $materializer = Get-Content -LiteralPath $materializerPath -Raw -Encoding UTF8
    $beforeStore = $materializer.IndexOf("failAfter(FailurePoint.BEFORE_STORE_OPERATION);", [System.StringComparison]::Ordinal)
    $storeOperation = $materializer.IndexOf("cleanupPlayer.storeMe();", [System.StringComparison]::Ordinal)
    $beforeDelete = $materializer.IndexOf("failAfter(FailurePoint.BEFORE_DELETE_OPERATION);", [System.StringComparison]::Ordinal)
    $deleteOperation = $materializer.IndexOf("cleanupPlayer.deleteMe();", [System.StringComparison]::Ordinal)
    Add-Result "cleanup.phantom-before-operation-order" (($beforeStore -ge 0) -and ($storeOperation -gt $beforeStore) -and ($beforeDelete -gt $storeOperation) -and ($deleteOperation -gt $beforeDelete)) "before-store < store < before-delete < delete"
    $lastPolicy = $materializer.LastIndexOf("PhantomPlayerCleanupPolicy.isComplete(cleanupPlayer)", [System.StringComparison]::Ordinal)
    $detachIndex = $materializer.IndexOf("_outboundAttachment.close();", $lastPolicy, [System.StringComparison]::Ordinal)
    $leaseReleaseIndex = $materializer.IndexOf("_identityLease.close();", $lastPolicy, [System.StringComparison]::Ordinal)
    $clearIndex = $materializer.IndexOf("_player = null;", $lastPolicy, [System.StringComparison]::Ordinal)
    $storedIndex = $materializer.IndexOf("_state = State.STORED;", $lastPolicy, [System.StringComparison]::Ordinal)
    $finishedIndex = $materializer.IndexOf("_cleanupFinished = true;", $lastPolicy, [System.StringComparison]::Ordinal)
    Add-Result "cleanup.phantom-release-last-and-stored" (($lastPolicy -ge 0) -and ($detachIndex -gt $lastPolicy) -and ($leaseReleaseIndex -gt $detachIndex) -and ($clearIndex -gt $leaseReleaseIndex) -and ($storedIndex -gt $clearIndex) -and ($finishedIndex -gt $storedIndex)) "policy < detach < lease < clear < STORED < finished"
    $materializerTokens = @(
        "BEFORE_STORE_OPERATION",
        "BEFORE_DELETE_OPERATION",
        "_state = State.FAILED;",
        "if (_cleanupFinished || _cleanupStarted)",
        "afterStepFailure = remember(afterStepFailure, e);",
        "Canonical Player cleanup postconditions are incomplete"
    )
    Add-Result "cleanup.phantom-fail-closed-retry-contract" (Test-ContainsAll -Path $materializerPath -Tokens $materializerTokens) "$($materializerTokens.Count) tokens"
    Add-Result "cleanup.phantom-single-success-release-site" (([regex]::Matches($materializer, "_identityLease\.close\(\);")).Count -eq 1) "one release site"

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
    $missingFailurePoints = @($failurePointNames | Where-Object { $materializer.IndexOf($_, [System.StringComparison]::Ordinal) -lt 0 })
    Add-Result "cleanup.phantom-original-eleven-points-present" ($missingFailurePoints.Count -eq 0) "11/11 enum tokens"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java"
    $suite = Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8
    $suiteTokens = @(
        "real-login-arbitration-policy",
        "shared-cleanup-policy-read-only",
        "cleanup-before-store-retains-and-retries",
        "cleanup-before-delete-retains-and-retries",
        "requiresRealLoginArbitration(false, null)",
        "requiresRealLoginArbitration(false, OwnerKind.REAL_LOGIN)",
        "requiresRealLoginArbitration(false, OwnerKind.PHANTOM)",
        "requiresRealLoginArbitration(true, null)",
        "PhantomPlayerCleanupPolicy.isComplete(player)",
        "State.FAILED",
        "State.STORED",
        "TASK_004_FAILURE_POINTS",
        "assertEquals(11, verified"
    )
    Add-Result "tests.task-004a-focused-cases" (Test-ContainsAll -Path $suitePath -Tokens $suiteTokens) "$($suiteTokens.Count) tokens"
    Add-Result "tests.no-gameclient-or-connection-fixture" ($suite -notmatch "new\s+GameClient\s*\(|extends\s+GameClient|new\s+Connection\s*\(|extends\s+Connection") "no fake network"
    Add-Result "tests.policy-registry-disabled-empty" (Test-ContainsAll -Path $suitePath -Tokens @("Disabled legacy policy created a REAL_LOGIN lease.", "Disabled legacy policy retained registry ownership.")) "empty registry asserted"
    Add-Result "tests.cleanup-policy-read-only-evidence" (Test-ContainsAll -Path $suitePath -Tokens @("Cleanup policy mutated the active Player state.", "Cleanup policy mutated the clean Player state.")) "before/after observations"
    Add-Result "tests.before-operation-retains-owner" (Test-ContainsAll -Path $suitePath -Tokens @("failed.identityLeaseRetained()", "failed.outboundAttached()", "Failed cleanup cleared the retained Player.", "Cleanup retry did not reach STORED.")) "retention and retry"

    $productionPaths = @(
        $gameClientPath,
        $disconnectionPath,
        $characterSelectPath,
        $identityPath,
        $cleanupPolicyPath,
        $materializerPath,
        $autosavePath
    )
    $productionText = ($productionPaths | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    $newPhantomProductionText = (@($identityPath, $cleanupPolicyPath, $materializerPath) | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    Add-Result "architecture.no-fake-gameclient-or-connection" ($productionText -notmatch "new\s+(?:GameClient|Connection)\s*\(|FakeGameClient|NullGameClient|FakeConnection") "no fake network"
    Add-Result "architecture.no-player-subclass-or-fork" ($productionText -notmatch "extends\s+Player\b|class\s+PhantomPlayer\b") "canonical Player"
    Add-Result "architecture.no-per-phantom-worker" ($newPhantomProductionText -notmatch "new\s+Thread\b|ExecutorService|newSingleThread|newFixedThread|scheduleAtFixedRate|scheduleWithFixedDelay") "no new worker"
    Add-Result "architecture.no-production-database-api" ($newPhantomProductionText -notmatch "DatabaseFactory|java\.sql|javax\.sql|l2jmobiush5") "no new DB access"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $buildTokens = @(
        'target name="phantom-static-verify-004" depends="phantom-static-verify-004a"',
        'target name="phantom-static-verify-004a"',
        'verify-task-004a.ps1',
        'target name="phantom-headless-player-test"',
        'target name="phantom-headless-player-performance-smoke"',
        'target name="verify"',
        'phantom-static-verify-004a'
    )
    Add-Result "build.task-004a-cumulative-verifier" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) tokens"
    $javaCount = ([regex]::Matches($build, "<java\s")).Count
    $forkCount = ([regex]::Matches($build, 'fork="true"')).Count
    Add-Result "build.all-java-targets-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"
    foreach ($target in @("test", "phantom-skeleton-test", "phantom-headless-player-test", "phantom-headless-player-performance-smoke", "phantom-negative-control", "phantom-db-guard-negative-control", "phantom-provisioning-lock-control", "phantom-schema-freshness-negative-control", "phantom-lifecycle-negative-control", "phantom-db-test", "phantom-scenario-test", "phantom-performance-smoke", "phantom-static-verify-004a", "verify", "jar"))
    {
        Add-Result "build.required-target.$target" ($build.Contains("target name=`"$target`"")) $target
    }

    $configPath = Join-Path $moduleRoot "dist/game/config/Custom/PhantomPlayers.ini"
    $configText = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
    Add-Result "config.defaults-still-false" (($configText -match "(?im)^\s*EnablePhantomSystem\s*=\s*False\s*$") -and ($configText -match "(?im)^\s*EnablePhantomDiagnostics\s*=\s*False\s*$")) "both false"

    $task004ReportPath = Join-Path $moduleRoot "docs/phantoms/reports/004-headless-player-feasibility-spike.md"
    $task004ReportTokens = @(
        "Commit: 5b22b1ee9bab556cd5a14c2212dfa3f4119c4566",
        "Parent: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d",
        "Push/remote: exact",
        "Final verifier 1: 97/97",
        "Final verifier 2: 97/97",
        "FA94A404CC98A16BA892DCD93CFC979C8CB0F2D51B0AC4978696404E54B251E9",
        "Independent feasibility verdict: ACCEPT",
        "Independent commit verdict: FIX_REQUIRED",
        "Task 004A: REQUIRED"
    )
    Add-Result "docs.task-004-independent-closure" (Test-ContainsAll -Path $task004ReportPath -Tokens $task004ReportTokens) "$($task004ReportTokens.Count) facts"

    $reviewPath = Join-Path $moduleRoot "docs/phantoms/reviews/004-headless-player-feasibility-spike-review.md"
    $reviewTokens = @(
        "Technical feasibility: ACCEPT",
        "Commit verdict: FIX_REQUIRED",
        "P1 CharacterSelect/onDisconnection race",
        "P1 fail-open lease release",
        "P1 materializer fail-open cleanup",
        "P2 disabled compatibility and terminal state",
        "Task 004A: REQUIRED",
        "Task 005: NOT_STARTED"
    )
    Add-Result "docs.task-004-review-record" (Test-ContainsAll -Path $reviewPath -Tokens $reviewTokens) "$($reviewTokens.Count) facts"

    $adrPath = Join-Path $moduleRoot "docs/phantoms/adr/0001-headless-player-integration-seam.md"
    $adr = Get-Content -LiteralPath $adrPath -Raw -Encoding UTF8
    Add-Result "docs.adr-remains-proposed" (($adr.Contains('`Proposed`')) -and ($adr.Contains("FEASIBLE_WITH_SEAM_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW")) -and ($adr -notmatch '(?m)^`Accepted`$')) "Proposed"

    $report004aPath = Join-Path $moduleRoot "docs/phantoms/reports/004a-real-login-lease-cleanup-hardening.md"
    $report004aTokens = @(
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
        "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW",
        'Manual gate: `PENDING_INDEPENDENT_REVIEW`',
        'Task 005: `NOT_STARTED`',
        'ADR: `Proposed`',
        "Exact immutable commit SHA, push result and post-commit verifier outputs are"
    )
    Add-Result "docs.task-004a-report" (Test-ContainsAll -Path $report004aPath -Tokens $report004aTokens) "$($report004aTokens.Count) sections/facts"

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
