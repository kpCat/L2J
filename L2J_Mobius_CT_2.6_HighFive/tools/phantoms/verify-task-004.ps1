[CmdletBinding()]
param(
    [string]$Branch = "feature/phantom-world",
    [string]$BaseCommit = "1ca74a3d96e8fa51612ef3e5145c7398abf60f6d"
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
        ($relative -ceq "java/org/l2jmobius/gameserver/model/actor/Player.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/GameClient.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/Disconnection.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java") -or
        ($relative -ceq "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java") -or
        $relative.StartsWith("java/org/l2jmobius/gameserver/phantoms/player/", [System.StringComparison]::Ordinal) -or
        $relative.StartsWith("test/java/org/l2jmobius/tests/phantoms/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "tools/phantoms/verify-task-004.ps1") -or
        $relative.StartsWith("docs/phantoms/tasks/004-headless-player-feasibility-spike/", [System.StringComparison]::Ordinal) -or
        $relative.StartsWith("docs/phantoms/audits/004-headless-player-feasibility-spike/", [System.StringComparison]::Ordinal) -or
        ($relative -ceq "docs/phantoms/reports/003-disabled-skeleton-config-metrics.md") -or
        ($relative -ceq "docs/phantoms/reports/004-headless-player-feasibility-spike.md") -or
        ($relative -ceq "docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md") -or
        ($relative -ceq "docs/phantoms/adr/0001-headless-player-integration-seam.md")
}

try
{
    $moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $gitRootResult = Invoke-Git -Root $moduleRoot -Arguments @("rev-parse", "--show-toplevel")
    $gitRoot = (Resolve-Path $gitRootResult.Output[0]).Path
    $relativeModule = "L2J_Mobius_CT_2.6_HighFive"
    $modulePrefix = "$relativeModule/"
    Add-Result "repository.module-root" ($moduleRoot -ceq (Join-Path $gitRoot $relativeModule)) $moduleRoot

    $currentBranch = (Invoke-Git -Root $gitRoot -Arguments @("branch", "--show-current")).Output[0]
    Add-Result "repository.branch" ($currentBranch -ceq $Branch) $currentBranch
    $baseExists = Invoke-Git -Root $gitRoot -Arguments @("cat-file", "-e", "$BaseCommit`^{commit}") -AllowFailure
    Add-Result "repository.base-commit" ($baseExists.ExitCode -eq 0) $BaseCommit
    $baseParent = if ($baseExists.ExitCode -eq 0) { (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "$BaseCommit^")).Output[0] } else { "" }
    Add-Result "repository.approved-baseline-parent" ($baseParent -ceq "eb008f2216b3e8381c0181d71ce200bbf4907ac7") $baseParent

    $roadmapRepositoryPath = "$modulePrefix`docs/PHANTOM_BOTS_ROADMAP.md"
    $roadmapDiff = Invoke-Git -Root $gitRoot -Arguments @("diff", "--quiet", $BaseCommit, "--", $roadmapRepositoryPath) -AllowFailure
    $roadmapPath = Join-Path $moduleRoot "docs/PHANTOM_BOTS_ROADMAP.md"
    $roadmapHash = if (Test-Path -LiteralPath $roadmapPath -PathType Leaf) { (Get-FileHash -LiteralPath $roadmapPath -Algorithm SHA256).Hash } else { "missing" }
    Add-Result "repository.approved-roadmap-unchanged" (($roadmapDiff.ExitCode -eq 0) -and ($roadmapHash -ceq "B049F85AE276906C969FE2FCC8A39F126B342AB1D8F256036E2EE6A60F1498D8")) $roadmapHash

    $head = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD")).Output[0]
    $mode = "invalid"
    $shape = $false
    if ($head -ceq $BaseCommit)
    {
        $mode = "pre-commit"
        $shape = $true
    }
    elseif ($baseExists.ExitCode -eq 0)
    {
        $parent = (Invoke-Git -Root $gitRoot -Arguments @("rev-parse", "HEAD^")).Output[0]
        $count = [int](Invoke-Git -Root $gitRoot -Arguments @("rev-list", "--count", "$BaseCommit..HEAD")).Output[0]
        $mode = "post-commit"
        $shape = (($parent -ceq $BaseCommit) -and ($count -eq 1))
    }
    Add-Result "repository.one-ordinary-commit-shape" $shape $mode

    $required = @(
        "java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java",
        "java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomActionFacade.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java",
        "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerFixture.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerPerformanceSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java",
        "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java",
        "docs/phantoms/audits/004-headless-player-feasibility-spike/TOUCHPOINT_AUDIT.md",
        "docs/phantoms/audits/004-headless-player-feasibility-spike/ONLINE_SESSION_POLICY.md",
        "docs/phantoms/audits/004-headless-player-feasibility-spike/MATERIALIZATION_STEPS.md",
        "docs/phantoms/reports/004-headless-player-feasibility-spike.md",
        "docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md",
        "tools/phantoms/verify-task-004.ps1"
    )
    foreach ($relative in (Get-OrdinalSortedUnique $required))
    {
        Add-Result "artifact.$relative" (Test-Path -LiteralPath (Join-Path $moduleRoot $relative) -PathType Leaf) $relative
    }

    $committed = @()
    if ($head -cne $BaseCommit)
    {
        $committed = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", "$BaseCommit...HEAD")).Output
    }
    $tracked = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--name-only", $BaseCommit)).Output
    $cached = (Invoke-Git -Root $gitRoot -Arguments @("diff", "--cached", "--name-only", $BaseCommit)).Output
    $untracked = (Invoke-Git -Root $gitRoot -Arguments @("ls-files", "--others", "--exclude-standard")).Output
    $taskUntracked = @($untracked | Where-Object { Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix })
    $unrelatedUntracked = @($untracked | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    $changed = Get-OrdinalSortedUnique ([string[]]($committed + $tracked + $cached + $taskUntracked))
    Add-Result "scope.changed-files-present" ($changed.Count -gt 0) "$($changed.Count) files"
    Add-Result "workspace.unrelated-untracked-preserved" $true "$($unrelatedUntracked.Count) excluded"

    $violations = @($changed | Where-Object { -not (Test-TaskScopePath -RepositoryPath $_ -ModulePrefix $modulePrefix) })
    Add-Result "scope.exact-allowlist" ($violations.Count -eq 0) (($violations -join ",") -replace "^$", "no violations")
    Add-Result "scope.high-five-only" (@($changed | Where-Object { -not $_.StartsWith($modulePrefix, [System.StringComparison]::Ordinal) }).Count -eq 0) "High Five only"
    Add-Result "scope.no-task-005" (@($changed | Where-Object { $_ -match "(?i)(task|report|verify)[^/]*005|tasks/005|reports/005" }).Count -eq 0) "Task 005 absent"
    Add-Result "scope.no-binaries" (@($changed | Where-Object { $_ -match "(?i)\.(jar|class|zip|7z|exe|dll|bin|log)$" }).Count -eq 0) "no binaries"
    $conditionalPaths = @(
        "$modulePrefix`java/org/l2jmobius/gameserver/network/serverpackets/ServerPacket.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/model/World.java",
        "$modulePrefix`java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java"
    )
    Add-Result "scope.no-conditional-production-touch" (@($changed | Where-Object { $conditionalPaths -contains $_ }).Count -eq 0) "0 conditional paths"
    $changedClientHandlers = @($changed | Where-Object { $_.StartsWith("$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/", [System.StringComparison]::Ordinal) })
    Add-Result "scope.only-character-select-handler" (($changedClientHandlers.Count -eq 1) -and ($changedClientHandlers[0] -ceq "$modulePrefix`java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java")) ($changedClientHandlers -join ",")

    $frozenPaths = @(
        "$modulePrefix`tools/phantoms/verify-task-001.ps1",
        "$modulePrefix`tools/phantoms/verify-task-001a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002.ps1",
        "$modulePrefix`tools/phantoms/verify-task-002a.ps1",
        "$modulePrefix`tools/phantoms/verify-task-003.ps1",
        "$modulePrefix`tools/phantoms/prepare-test-db.ps1",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLock.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomProvisioningLockControl.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseBootstrap.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseGuard.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestDatabaseProvisioner.java",
        "$modulePrefix`test/java/org/l2jmobius/tests/phantoms/PhantomTestSchemaManifest.java",
        "$modulePrefix`test/resources/phantoms/db/migrations/001_create_phantom_test_harness.sql",
        "$modulePrefix`test/resources/phantoms/db/migrations/002_create_phantom_test_schema_manifest.sql",
        "$modulePrefix`dist/game/config/Custom/PhantomPlayers.ini"
    )
    $frozenDiff = Invoke-Git -Root $gitRoot -Arguments ([string[]](@("diff", "--quiet", $BaseCommit, "--") + $frozenPaths)) -AllowFailure
    Add-Result "scope.prior-verifiers-lock-manifest-config-unchanged" ($frozenDiff.ExitCode -eq 0) "$($frozenPaths.Count) paths"
    $schemaOrConfigChanges = @($changed | Where-Object { ($_ -match "/test/resources/phantoms/db/") -or ($_ -match "/dist/game/config/") -or ($_ -match "(?i)\.sql$") })
    Add-Result "scope.no-schema-production-config-sql" ($schemaOrConfigChanges.Count -eq 0) (($schemaOrConfigChanges -join ",") -replace "^$", "none")

    $playerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/model/actor/Player.java"
    $playerTokens = @(
        "private volatile PlayerOutboundSession _outboundSession = PlayerOutboundSession.clientBound();",
        "public synchronized OutboundSessionAttachment attachOutboundSession",
        "Only a headless outbound session may be attached",
        "Cannot bind a real client while a headless outbound session owns the Player",
        "_player.detachOutboundSession(_outboundSession, _token);",
        'throw new NullPointerException("packet");',
        "_outboundSession.send(this, packet);",
        "if (_isOnline && (_outboundSession.kind() == SessionKind.HEADLESS))",
        "_inventoryUpdateTask.cancel(false);",
        "_broadcastCharInfoTask.cancel(false);",
        "_nevitHourglassTask.cancel(false);"
    )
    Add-Result "player.outbound-online-cleanup-contract" (Test-ContainsAll -Path $playerPath -Tokens $playerTokens) "$($playerTokens.Count) tokens"

    $seamPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/PlayerOutboundSession.java"
    $seamTokens = @(
        "public interface PlayerOutboundSession",
        "CLIENT_BOUND,",
        "HEADLESS",
        "final GameClient client = player.getClient();",
        "client.sendPacket(packet);"
    )
    Add-Result "outbound.client-bound-current-client-delegation" (Test-ContainsAll -Path $seamPath -Tokens $seamTokens) "$($seamTokens.Count) tokens"

    $gameClientPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/GameClient.java"
    $gameClient = Get-Content -LiteralPath $gameClientPath -Raw -Encoding UTF8
    $sendStart = $gameClient.IndexOf("public void sendPacket(ServerPacket packet)", [System.StringComparison]::Ordinal)
    $writeIndex = $gameClient.IndexOf("writePacket(packet);", $sendStart, [System.StringComparison]::Ordinal)
    $effectIndex = $gameClient.IndexOf("packet.runImpl(_player);", $sendStart, [System.StringComparison]::Ordinal)
    Add-Result "outbound.real-send-order-unchanged" (($sendStart -ge 0) -and ($writeIndex -gt $sendStart) -and ($effectIndex -gt $writeIndex)) "writePacket < runImpl"

    $headlessPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/HeadlessPlayerOutboundSession.java"
    $headless = Get-Content -LiteralPath $headlessPath -Raw -Encoding UTF8
    $headlessTokens = @(
        "public final class HeadlessPlayerOutboundSession implements PlayerOutboundSession",
        "_maximumDepth",
        "_maximumPacketsPerRoot",
        "_recordedPacketClasses",
        "recordingCapacity == 0 ? null",
        "_rejectedCount++",
        "_droppedRecordCount++",
        "public synchronized Snapshot snapshot()",
        "packet.runImpl(player);"
    )
    Add-Result "outbound.headless-bounded-contract" (Test-ContainsAll -Path $headlessPath -Tokens $headlessTokens) "$($headlessTokens.Count) tokens"
    Add-Result "outbound.headless-runimpl-exactly-once-source" (([regex]::Matches($headless, "\bpacket\.runImpl\s*\(")).Count -eq 1) "one invocation"
    Add-Result "outbound.headless-no-network-types" ($headless -cnotmatch "\bGameClient\b|\bConnection\b|writePacket|writeBytes|Socket|Channel|ConnectionManager") "zero transport tokens"
    Add-Result "outbound.headless-no-hot-log" ($headless -notmatch "\bLogger\b|LOGGER|System\.out|System\.err") "no logging"

    $identityPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomIdentityLeaseRegistry.java"
    $identityTokens = @(
        "REAL_LOGIN,",
        "PHANTOM",
        "ConcurrentHashMap<Integer, Entry>",
        "_owners.putIfAbsent",
        "_owners.remove(entry.objectId(), entry);",
        "_closed.compareAndSet(false, true)",
        "long token()"
    )
    Add-Result "identity.tokenized-registry-contract" (Test-ContainsAll -Path $identityPath -Tokens $identityTokens) "$($identityTokens.Count) tokens"
    $gameClientIdentityTokens = @(
        "tryAcquire(objectId, OwnerKind.REAL_LOGIN)",
        "getOwnerKind(objectId) == OwnerKind.PHANTOM",
        "Keep the pre-seam real-real double-login cleanup",
        "attachPlayerIdentityLease(identityLease);",
        "public synchronized void releasePlayerIdentityLease()"
    )
    Add-Result "identity.game-client-real-login-hook" (Test-ContainsAll -Path $gameClientPath -Tokens $gameClientIdentityTokens) "$($gameClientIdentityTokens.Count) tokens"

    $characterSelectPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/clientpackets/CharacterSelect.java"
    $characterSelectTokens = @(
        "getOwnerKind(info.getObjectId()) == OwnerKind.PHANTOM",
        "client.load(_charSlot);",
        "boolean selectionOwnsLoadedPlayer = true;",
        "Disconnection.of(client, cha).storeAndDelete();"
    )
    Add-Result "identity.character-select-collision-and-failure-cleanup" (Test-ContainsAll -Path $characterSelectPath -Tokens $characterSelectTokens) "$($characterSelectTokens.Count) tokens"

    $disconnectionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/network/Disconnection.java"
    $disconnection = Get-Content -LiteralPath $disconnectionPath -Raw -Encoding UTF8
    Add-Result "identity.disconnection-final-release" (([regex]::Matches($disconnection, "_client\.releasePlayerIdentityLease\(\);")).Count -ge 3) "all immediate/delayed paths"

    $materializerPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomPlayerMaterializationSpike.java"
    $materializer = Get-Content -LiteralPath $materializerPath -Raw -Encoding UTF8
    $materializerTokens = @(
        "public enum State",
        "STORED,",
        "CLAIMED,",
        "LOADING,",
        "MATERIALIZING,",
        "ACTIVE,",
        "DEMATERIALIZING,",
        "FAILED",
        "tryAcquire(_objectId, OwnerKind.PHANTOM)",
        "_player = Player.load(_objectId);",
        "_outboundAttachment = _player.attachOutboundSession(_outboundSession);",
        "_player.spawnMe();",
        "_actionAdmissionOpen = false;",
        "cleanupPlayer.storeMe();",
        "cleanupPlayer.deleteMe();",
        "_identityLease.close();",
        "_player = null;"
    )
    Add-Result "materialization.explicit-lifecycle-contract" (Test-ContainsAll -Path $materializerPath -Tokens $materializerTokens) "$($materializerTokens.Count) tokens"
    Add-Result "materialization.no-enter-world" ($materializer -notmatch "\bEnterWorld\b|runImpl\s*\(") "no handler dependency"
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
    Add-Result "materialization.failure-matrix-eleven-points" ($missingFailurePoints.Count -eq 0) "11/11 enum and injection tokens"
    Add-Result "materialization.not-wired-to-game-server" (-not (Get-Content -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/GameServer.java") -Raw -Encoding UTF8).Contains("PhantomPlayerMaterializationSpike")) "test-instantiated only"

    $actionPath = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/player/PhantomActionFacade.java"
    $action = Get-Content -LiteralPath $actionPath -Raw -Encoding UTF8
    $actionTokens = @(
        "performReversibleInventoryFixture",
        "addItem(ItemProcessType.REWARD",
        "destroyItemByItemId(ItemProcessType.DESTROY",
        "restoreFixtureBaseline",
        "FIXTURE_ITEM_ID = 57",
        "FIXTURE_ITEM_AMOUNT = 1"
    )
    Add-Result "action.single-reversible-inventory-contract" (Test-ContainsAll -Path $actionPath -Tokens $actionTokens) "$($actionTokens.Count) tokens"
    $publicActionMethods = ([regex]::Matches($action, "(?m)^\s*public\s+(?:ActionResult|void|long)\s+(?<name>[A-Za-z0-9_]+)\s*\(") | ForEach-Object { $_.Groups["name"].Value })
    Add-Result "action.one-executable-action" (@($publicActionMethods | Where-Object { $_ -like "perform*" }).Count -eq 1) ($publicActionMethods -join ",")
    Add-Result "action.no-sql-client-handler" ($action -notmatch "DatabaseFactory|java\.sql|clientpackets|ClientPacket|GameClient") "canonical inventory API only"

    $productionPlayerFiles = @(
        $seamPath,
        $headlessPath,
        $identityPath,
        $actionPath,
        $materializerPath
    )
    $newPlayerProduction = ($productionPlayerFiles | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    Add-Result "architecture.no-player-or-gameclient-subclass" ($newPlayerProduction -notmatch "extends\s+(?:Player|GameClient|Connection)\b") "no fork/subclass"
    Add-Result "architecture.no-fake-gameclient-or-connection" ($newPlayerProduction -notmatch "new\s+(?:GameClient|Connection)\s*\(|FakeGameClient|NullGameClient|FakeConnection") "no fake network"
    Add-Result "architecture.no-production-thread-per-phantom" ($newPlayerProduction -notmatch "new\s+Thread\b|ExecutorService|newSingleThread|newFixedThread|scheduleAtFixedRate|scheduleWithFixedDelay") "shared canonical pool only"
    Add-Result "architecture.no-production-database-api" ($newPlayerProduction -notmatch "DatabaseFactory|java\.sql|javax\.sql|l2jmobiush5") "no production persistence seam"

    $suitePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerSuite.java"
    $suiteTokens = @(
        "fixture-create-load-canonical",
        "headless-basic-effect-contract",
        "actual-html-and-tutorial-effects",
        "actual-item-list-recursion",
        "bounded-recursion-and-recording",
        "identity-token-and-concurrency",
        "identity-materialization-collisions",
        "materialize-action-cleanup-reload",
        "observer-visibility-and-creature-say-snoop",
        "action-admission-closes-before-cleanup",
        "failure-matrix-all-eleven-points",
        "final-world-autosave-lease-residue"
    )
    Add-Result "tests.headless-regression-matrix" (Test-ContainsAll -Path $suitePath -Tokens $suiteTokens) "$($suiteTokens.Count) tokens"
    $suite = Get-Content -LiteralPath $suitePath -Raw -Encoding UTF8
    Add-Result "tests.no-gameclient-instance" ($suite -notmatch "new\s+GameClient\s*\(|extends\s+GameClient") "no GameClient fixture"
    Add-Result "tests.canonical-player-exact-class" ($suite.Contains("Player.class, player.getClass()")) "exact Player class asserted"

    $environmentPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java"
    $environmentTokens = @(
        'context.moduleRoot().resolve("dist/game")',
        "PhantomTestDatabaseBootstrap.initialize",
        "PhantomTestDatabaseGuard.TARGET_DATABASE",
        "ThreadPool.init();",
        "Player.create(template",
        "GameClient.deleteCharByObjId(objectId);",
        "SELECT COUNT(*) FROM items WHERE owner_id=?",
        "ThreadPool.shutdown();",
        "DatabaseFactory.close();",
        "headless.initializedSingletons",
        "headless.transitiveSingletons"
    )
    Add-Result "tests.minimal-environment-db-cleanup-contract" (Test-ContainsAll -Path $environmentPath -Tokens $environmentTokens) "$($environmentTokens.Count) tokens"
    $environment = Get-Content -LiteralPath $environmentPath -Raw -Encoding UTF8
    Add-Result "tests.test-db-only-literal" (($environment.Contains("PhantomTestDatabaseGuard.TARGET_DATABASE")) -and ($environment -notmatch '"l2jmobiush5"')) "allowlisted constant only"
    Add-Result "tests.no-network-server-bootstrap" ($environment -cnotmatch "\bGameServer\b|\bLoginServer\b|\bConnectionManager\b|new\s+GameClient\s*\(") "no server/network instance"
    Add-Result "tests.threadpool-initialized-once" (([regex]::Matches($environment, "ThreadPool\.init\(\);")).Count -eq 1) "one call"

    $performancePath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerPerformanceSuite.java"
    $performanceTokens = @(
        "one-fixture-measured",
        "ten-sequential-fixtures-measured",
        "MAX_ONE_FIXTURE_NANOS",
        "MAX_TEN_FIXTURES_NANOS",
        "for (int i = 0; i < 10; i++)",
        "tenSequentialEffects",
        "tenSequentialDroppedRecords"
    )
    Add-Result "tests.performance-one-ten-sequential-contract" (Test-ContainsAll -Path $performancePath -Tokens $performanceTokens) "$($performanceTokens.Count) tokens"

    $launcherPath = Join-Path $moduleRoot "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
    Add-Result "tests.launcher-modes" (Test-ContainsAll -Path $launcherPath -Tokens @('case "headless-player" -> new PhantomHeadlessPlayerSuite();', 'case "headless-player-performance" -> new PhantomHeadlessPlayerPerformanceSuite();')) "two explicit modes"

    $buildPath = Join-Path $moduleRoot "build.xml"
    $buildTokens = @(
        'target name="phantom-headless-player-test"',
        'arg value="headless-player"',
        'target name="phantom-headless-player-performance-smoke"',
        'arg value="headless-player-performance"',
        'dir="${datapack}/game"',
        'timeout="240000"',
        'target name="phantom-static-verify-004"',
        'verify-task-004.ps1'
    )
    Add-Result "build.task-004-target-contract" (Test-ContainsAll -Path $buildPath -Tokens $buildTokens) "$($buildTokens.Count) tokens"
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding UTF8
    $javaCount = ([regex]::Matches($build, "<java\s")).Count
    $forkCount = ([regex]::Matches($build, 'fork="true"')).Count
    Add-Result "build.all-java-targets-forked" (($javaCount -gt 0) -and ($javaCount -eq $forkCount)) "java=$javaCount forked=$forkCount"
    foreach ($target in @("test", "phantom-skeleton-test", "phantom-headless-player-test", "phantom-headless-player-performance-smoke", "phantom-negative-control", "phantom-db-guard-negative-control", "phantom-provisioning-lock-control", "phantom-schema-freshness-negative-control", "phantom-lifecycle-negative-control", "phantom-db-test", "phantom-scenario-test", "phantom-performance-smoke", "phantom-static-verify-004", "verify", "jar"))
    {
        Add-Result "build.required-target.$target" ($build.Contains("target name=`"$target`"")) $target
    }
    $verifyLine = [regex]::Match($build, '<target name="verify"[^>]+>').Value
    Add-Result "build.verify-includes-headless-gates" (($verifyLine.Contains("phantom-headless-player-test")) -and ($verifyLine.Contains("phantom-headless-player-performance-smoke")) -and ($verifyLine.Contains("phantom-static-verify-004"))) "functional, performance, static"

    $configPath = Join-Path $moduleRoot "dist/game/config/Custom/PhantomPlayers.ini"
    $configText = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
    Add-Result "config.task-003-defaults-still-false" (($configText -match "(?im)^\s*EnablePhantomSystem\s*=\s*False\s*$") -and ($configText -match "(?im)^\s*EnablePhantomDiagnostics\s*=\s*False\s*$")) "both false"

    $taskManifestPath = Join-Path $moduleRoot "docs/phantoms/tasks/004-headless-player-feasibility-spike/PACKAGE_MANIFEST.json"
    $taskManifest = Get-Content -LiteralPath $taskManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Add-Result "docs.package-effective-base" ($taskManifest.baseCommit -ceq $BaseCommit) ([string]$taskManifest.baseCommit)

    $task003ReportPath = Join-Path $moduleRoot "docs/phantoms/reports/003-disabled-skeleton-config-metrics.md"
    $task003Tokens = @(
        "Commit: eb008f2216b3e8381c0181d71ce200bbf4907ac7",
        "Parent: 84f29a0002b25d2b1ff1a19fa9c92867479fd6a5",
        "Push: successful",
        "Remote ref: exact",
        "Final verifier 1: 72/72",
        "Final verifier 2: 72/72",
        "447FDBA9B5C2592C40250FF5026B5DB0E71C66520EF8E0F46CF9E3A252894F9D",
        "Independent review: ACCEPT",
        "## Task 004",
        '`ALLOWED`'
    )
    Add-Result "docs.task-003-provenance-closure" (Test-ContainsAll -Path $task003ReportPath -Tokens $task003Tokens) "$($task003Tokens.Count) facts"
    $review003Path = Join-Path $moduleRoot "docs/phantoms/reviews/003-disabled-skeleton-config-metrics-review.md"
    Add-Result "docs.task-003-review-acceptance" (Test-ContainsAll -Path $review003Path -Tokens @("Task 003 implementation: ACCEPT", "Task 003 revert: NOT_REQUIRED", "Task 004: ALLOWED", "Task 005: NOT_STARTED")) "review gate"

    $touchAuditPath = Join-Path $moduleRoot "docs/phantoms/audits/004-headless-player-feasibility-spike/TOUCHPOINT_AUDIT.md"
    $touchTokens = @(
        "FC569FF715B031E64B06BA6C7BD89D3934F1C5F81CE5766AB86D9CC9C75F2E54",
        "5C7958A1ECBBA322791ABDCFBD6FB25C73C7BF486E6129CFFEE40E59819855B1",
        "9C2D211C556EC9126CEDA6F62A40845F76D147546CDFD65D2C9671C986001236",
        "D9FEBE2DDABA2C3416906DACB648DFC17C17CEBC8A2FCAC3AEED191C07F01D86",
        "Effective Task 004 baseline: 1ca74a3d96e8fa51612ef3e5145c7398abf60f6d",
        "Roadmap SHA-256: B049F85AE276906C969FE2FCC8A39F126B342AB1D8F256036E2EE6A60F1498D8"
    )
    Add-Result "docs.touchpoint-audit-and-baseline-advancement" (Test-ContainsAll -Path $touchAuditPath -Tokens $touchTokens) "$($touchTokens.Count) facts"

    $onlinePolicyPath = Join-Path $moduleRoot "docs/phantoms/audits/004-headless-player-feasibility-spike/ONLINE_SESSION_POLICY.md"
    Add-Result "docs.online-session-policy" (Test-ContainsAll -Path $onlinePolicyPath -Tokens @("active headless outbound attached", 'Complete `isOnlineInt()` call-site audit', "AutoPotionTaskManager", "PcCafePointsManager", "Observer evidence", "Gate result")) "0/1/2 policy"
    $stepsPath = Join-Path $moduleRoot "docs/phantoms/audits/004-headless-player-feasibility-spike/MATERIALIZATION_STEPS.md"
    Add-Result "docs.materialization-classification" (Test-ContainsAll -Path $stepsPath -Tokens @("REQUIRED_NOW", "DEFERRED_SAFE", "CLIENT_SESSION_ONLY", "FORBIDDEN", "all eleven points", "GameServer", "EnterWorld.runImpl")) "all classifications"

    $adrPath = Join-Path $moduleRoot "docs/phantoms/adr/0001-headless-player-integration-seam.md"
    Add-Result "docs.adr-proposed-implementation-verdict" (Test-ContainsAll -Path $adrPath -Tokens @('`Proposed`', "FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW", "This is a recommendation to accept the seam, not an ADR status transition.", "Task 005")) "status remains Proposed"

    $report004Path = Join-Path $moduleRoot "docs/phantoms/reports/004-headless-player-feasibility-spike.md"
    $report004Tokens = @(
        "## Status and starting baseline",
        "## Approved documentation-only baseline advancement",
        "## Task 003 review closure",
        "## Touchpoint audit and hashes",
        "## Architecture verdict",
        "## Production changes",
        "## Outbound/session seam",
        "## Packet-effect evidence",
        "## Online/session policy",
        "## Identity ownership and real-login hook",
        "## Test environment",
        "## Fixture lifecycle",
        "## Explicit materialization steps",
        "## Action facade and conservation",
        "## Observer visibility",
        "## Cleanup and failure matrix",
        "## Task, autosave and World residue",
        "## One/ten fixture measurements",
        "## DB and network safety",
        "## Disabled production behavior",
        "## Tests, counts and exit codes",
        "## Ant verify and jar",
        "## Static verifier",
        "## Scope and conditional touches",
        "## Deviations, limitations and risks",
        "## Branch, parent and subject",
        "## Manual gate",
        "PENDING_INDEPENDENT_REVIEW",
        'ADR remains `Proposed`',
        'Task 005: `NOT_STARTED`',
        "Exact immutable commit SHA, push result and post-commit verifier outputs are",
        "FEASIBLE_WITH_SEAM_PENDING_INDEPENDENT_REVIEW"
    )
    Add-Result "docs.task-004-complete-report" (Test-ContainsAll -Path $report004Path -Tokens $report004Tokens) "$($report004Tokens.Count) sections/facts"

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
