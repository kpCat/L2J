param(
	[switch] $WorkingTree
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AcceptedGoal021 = "043844c0fd7a0bfcac0d5f58461a21633b032332"
$FoundationCommit = "d02dc8429e88ef507347fc2e3860b0528844ae68"
$FoundationSubject = "feat(phantoms): add economy reservations craft and enchant"
$LifecycleCommit = "9e2bd551ecc03647641c16e393694b9a0cb51e60"
$LifecycleSubject = "fix(phantoms): close economy craft lifecycle and reservation ownership"
$AuthorityCommit = "20fe8daccfb5000b5b970bff7b3555a4051e5dbc"
$AuthoritySubject = "fix(phantoms): close economy resume authority and risk gates"
$RequiredSubject = "fix(phantoms): close participant economy lifecycle ordering"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "22002201"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Git-Lines([string[]] $arguments)
{
	$result = & git -c core.safecrlf=false @arguments
	Assert-True ($LASTEXITCODE -eq 0) "Git command failed: git $($arguments -join ' ')"
	return @($result | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function To-ModulePath([string] $path)
{
	$normalized = $path.Trim().Trim('"').Replace("\", "/")
	if ($normalized.StartsWith($script:ModulePrefix, [StringComparison]::Ordinal))
	{
		return $normalized.Substring($script:ModulePrefix.Length)
	}
	return $normalized
}

function Add-Paths([Collections.Generic.HashSet[string]] $set, [string[]] $arguments)
{
	foreach ($line in Git-Lines $arguments)
	{
		[void] $set.Add((To-ModulePath $line))
	}
}

function Add-Untracked([Collections.Generic.HashSet[string]] $set)
{
	foreach ($line in Git-Lines @("ls-files", "--others", "--exclude-standard"))
	{
		$path = To-ModulePath $line
		if (Test-Path -LiteralPath (Join-Path $script:ModuleRoot $path) -PathType Leaf)
		{
			[void] $set.Add($path)
		}
	}
}

function Read-CommitBytes([string] $commit, [string] $relativePath)
{
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = New-Object Diagnostics.ProcessStartInfo
	$start.FileName = "git"
	$start.Arguments = "show $commit`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = New-Object IO.MemoryStream
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 022c1 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 022c1 artifact is absent: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-TargetUtf8([string] $relativePath)
{
	$encoding = New-Object Text.UTF8Encoding($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Target-Sha256([string] $relativePath)
{
	$sha = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha.ComputeHash((Read-TargetBytes $relativePath)))).Replace("-", "").ToLowerInvariant()
	}
	finally
	{
		$sha.Dispose()
	}
}

function Is-Production([string] $path)
{
	return ($path -match '^java/org/l2jmobius/gameserver/') -or ($path -match '^dist/game/data/')
}

function Is-Allowed([string] $path)
{
	return ($path -in @(
		'PHANTOM_DEVELOPMENT_MASTER_PLAN.md',
		'build.xml',
		'docs/PHANTOM_BOTS_ROADMAP.md',
		'dist/db_installer/sql/game/phantom_reservations.sql',
		'dist/game/data/phantoms/economy/high-five-economy-v1.xml',
		'docs/phantoms/architecture/ECONOMY_TRANSACTION_CONTRACT.md',
		'docs/phantoms/reports/022-checkpoint-1-economy-craft-enchant.md',
		'docs/phantoms/reviews/021-final-review.md',
		'docs/phantoms/reviews/022-checkpoint-1-independent-review.md',
		'java/org/l2jmobius/gameserver/data/xml/EnchantItemData.java',
		'java/org/l2jmobius/gameserver/managers/RecipeCraftObserver.java',
		'java/org/l2jmobius/gameserver/managers/RecipeManager.java',
		'java/org/l2jmobius/gameserver/model/item/enchant/AbstractEnchantItem.java',
		'java/org/l2jmobius/gameserver/model/item/enchant/EnchantScroll.java',
		'java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java',
		'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionState.java',
		'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundInventoryHash.java',
		'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundModel.java',
		'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java',
		'java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java',
		'java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java',
		'java/org/l2jmobius/gameserver/services/EnchantItemService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java',
		'tools/phantoms/verify-task-021c2.ps1',
		'tools/phantoms/verify-task-022c1.ps1'
	)) -or
		($path -match '^java/org/l2jmobius/gameserver/phantoms/economy/') -or
		($path -match '^docs/phantoms/tasks/022-checkpoint-1-economy-craft-enchant/')
}

function Contains-All([string] $text, [string[]] $tokens, [string] $name)
{
	foreach ($token in $tokens)
	{
		Assert-True ($text.Contains($token)) "$name is missing required token: $token"
	}
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 022c1 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $FoundationCommit) | Select-Object -First 1) -eq $AcceptedGoal021) "Goal 022c1 foundation is not the direct child of the accepted Goal 021 baseline."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $FoundationCommit) | Select-Object -First 1) -eq $FoundationSubject) "Goal 022c1 foundation subject changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $LifecycleCommit) | Select-Object -First 1) -eq $FoundationCommit) "Goal 022c1 lifecycle completion is not the direct child of its foundation."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $LifecycleCommit) | Select-Object -First 1) -eq $LifecycleSubject) "Goal 022c1 lifecycle completion subject changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $AuthorityCommit) | Select-Object -First 1) -eq $LifecycleCommit) "Goal 022c1 authority completion is not the direct child of its lifecycle completion."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $AuthorityCommit) | Select-Object -First 1) -eq $AuthoritySubject) "Goal 022c1 authority completion subject changed."
	if ($WorkingTree)
	{
		Assert-True ($head -eq $AuthorityCommit) "Working Goal 022c1 terminal completion must start at the accepted authority completion."
		$script:Mode = "working"
		$script:TargetCommit = "WORKING_TREE"
	}
	else
	{
		& git merge-base --is-ancestor $AuthorityCommit $head
		Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 022c1 authority completion is not an ancestor of HEAD."
		$children = @(Git-Lines @("rev-list", "--ancestry-path", "--reverse", "$AuthorityCommit..$head"))
		Assert-True ($children.Count -ge 1) "Goal 022c1 terminal completion child is absent."
		$script:TargetCommit = $children[0]
		$script:Mode = "historical"
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $AuthorityCommit) "Goal 022c1 terminal completion is not one ordinary child of the authority completion."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 022c1 terminal completion subject changed."
	}

	$foundationChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-Paths $foundationChanged @("diff", "--name-only", $AcceptedGoal021, $FoundationCommit, "--")
	$foundationPaths = @($foundationChanged | Sort-Object)
	Assert-True (($foundationPaths.Count -gt 0) -and ($foundationPaths.Count -le 65)) "Goal 022c1 foundation scope exceeds its accepted bound."
	$foundationProduction = @($foundationPaths | Where-Object { Is-Production $_ })
	Assert-True ($foundationProduction.Count -le 38) "Goal 022c1 foundation production scope exceeds its accepted bound."
	foreach ($path in $foundationPaths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 022c1 foundation path: $path"
	}
	$lifecycleChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-Paths $lifecycleChanged @("diff", "--name-only", $FoundationCommit, $LifecycleCommit, "--")
	$lifecyclePaths = @($lifecycleChanged | Sort-Object)
	Assert-True (($lifecyclePaths.Count -gt 0) -and ($lifecyclePaths.Count -le 16)) "Goal 022c1 lifecycle completion scope exceeds its accepted bound."
	$lifecycleProduction = @($lifecyclePaths | Where-Object { Is-Production $_ })
	Assert-True ($lifecycleProduction.Count -le 9) "Goal 022c1 lifecycle completion production scope exceeds its accepted bound."
	foreach ($path in $lifecyclePaths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 022c1 lifecycle path: $path"
	}
	$authorityChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-Paths $authorityChanged @("diff", "--name-only", $LifecycleCommit, $AuthorityCommit, "--")
	$authorityPaths = @($authorityChanged | Sort-Object)
	Assert-True (($authorityPaths.Count -gt 0) -and ($authorityPaths.Count -le 19)) "Goal 022c1 authority completion scope exceeds its accepted bound."
	$authorityProduction = @($authorityPaths | Where-Object { Is-Production $_ })
	Assert-True ($authorityProduction.Count -le 12) "Goal 022c1 authority completion production scope exceeds its accepted bound."
	foreach ($path in $authorityPaths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 022c1 authority path: $path"
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $changed @("diff", "--name-only", $AuthorityCommit, "--")
		Add-Untracked $changed
	}
	else
	{
		Add-Paths $changed @("diff", "--name-only", $AuthorityCommit, $script:TargetCommit, "--")
	}
	$paths = @($changed | Sort-Object)
	Assert-True (($paths.Count -gt 0) -and ($paths.Count -le 8)) "Goal 022c1 terminal completion scope exceeds 8 files."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 022c1 path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|(^|/)(?:PlayerInventory|Inventory|TradeList|TradeItem)\.java$|L2J_Mobius_CT_(?!2\.6_HighFive)') "Forbidden Goal 022c1 path: $path"
		Assert-True ($path -notmatch '(?i)(direct.?trade|private.?store|player.?manufacture|mail|clan.?warehouse|023)') "Checkpoint 2 or Goal 023 path leaked into Goal 022c1: $path"
	}

	$newPaths = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $AuthorityCommit, "--")
		Add-Untracked $newPaths
	}
	else
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $AuthorityCommit, $script:TargetCommit, "--")
	}
	$newProduction = @($newPaths | Where-Object { Is-Production $_ })
	$changedProduction = @($paths | Where-Object { Is-Production $_ })
	$sql = @($paths | Where-Object { $_ -match '^dist/db_installer/sql/' })
	$economyXml = @($paths | Where-Object { $_ -match '^dist/game/data/phantoms/economy/.*\.xml$' })
	Assert-True ($newProduction.Count -eq 0) "Goal 022c1 terminal completion added production/data files."
	Assert-True ($changedProduction.Count -le 3) "Goal 022c1 terminal completion exceeds three production files."
	Assert-True ($sql.Count -eq 0) "Goal 022c1 terminal completion changed the accepted schema."
	Assert-True ($economyXml.Count -eq 0) "Goal 022c1 terminal completion changed accepted economy XML."

	$cumulativeChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $cumulativeChanged @("diff", "--name-only", $AcceptedGoal021, "--")
		Add-Untracked $cumulativeChanged
	}
	else
	{
		Add-Paths $cumulativeChanged @("diff", "--name-only", $AcceptedGoal021, $script:TargetCommit, "--")
	}
	$cumulativePaths = @($cumulativeChanged | Sort-Object)
	Assert-True ($cumulativePaths.Count -le 65) "Goal 022c1 cumulative foundation+completion scope exceeds 65 files."

	$operation = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOperation.java'
	Contains-All $operation @('SELF_CRAFT', 'ITEM_ENCHANT', 'PREPARED', 'RESERVED', 'DISPATCHING', 'OBSERVING', 'COMMITTED', 'ABORTED', 'EXPIRED', 'INCONSISTENT', 'canonicalKey()', 'ownerClassIndex', 'MAX_PAYLOAD_BYTES = 4096') 'Economy operation contract'
	Assert-True ($operation.Contains('((state == State.PREPARED) || (state == State.RESERVED))') -and !$operation.Contains('case DISPATCHING -> (next == EXPIRED)')) "Economy expiry is not restricted to predispatch."
	Assert-True ($operation.Contains('case DISPATCHING -> (next == OBSERVING) || (next == COMMITTED) || (next == ABORTED) || (next == INCONSISTENT)')) "Predispatch-effect abort cannot terminate exactly."
	Contains-All $operation @('case OBSERVING -> (next == COMMITTED) || (next == ABORTED) || (next == INCONSISTENT)', 'public boolean overlaps(Reservation other)', '(kind == ResourceKind.ITEM_OBJECT) && (other.kind == ResourceKind.ITEM_OBJECT)', 'return objectId == other.objectId', 'itemId == other.itemId') 'Resumable operation and exact-object overlap contract'

	$reservations = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java'
	Contains-All $reservations @('lockProfiles(connection, profileIds)', 'lockOperation(connection, operation.operationId())', 'lockReservationKeys(connection, operationId)', 'RESOURCE_CONFLICT', 'IDENTITY_CONFLICT', 'PROFILE_BUSY', 'retainedNonterminalOperations()', 'dispatch.ambiguous', 'EconomyConflictException', 'findReservations', 'nextAttempt') 'Reservation kernel'
	Assert-True ($reservations.IndexOf('lockProfiles(connection, profileIds)') -lt $reservations.IndexOf('lockOperation(connection, operation.operationId())')) "Profile/operation DB lock order drifted."
	Assert-True ($reservations.Contains('(operation.state() == State.DISPATCHING) || (operation.state() == State.OBSERVING)') -and $reservations.Contains('throw new EconomyConflictException')) "Materialization boundary does not fail stop after dispatch."
	Contains-All $reservations @('SELECT character_object_id FROM phantom_profiles', 'addParticipantLink(links, operation.profileId(), operation.characterObjectId())', 'addParticipantLink(links, rows.getLong(1), rows.getInt(2))', 'for (long profileId : profileIds)', 'hasAnotherActiveOperation(connection, profileId', 'hasReservationConflict(connection, operation.operationId(), reservations)', 'hasSemanticOverlap(reservations)', "resource_kind='ITEM_OBJECT'", "resource_kind='ITEM_COUNT'", 'findObserving(256)', 'findShutdownCandidates(256)', 'State.OBSERVING, State.INCONSISTENT') 'Participant-neutral ownership, exact overlap and shutdown conflicts'
	Contains-All $reservations @('record ParticipantSet', 'profileIds.stream().distinct().sorted().toList()', 'Collections.unmodifiableMap(orderedLinks)', 'lockLifecycle(connection, operationId)', 'discoverParticipantSet(connection, operationId)', 'findActiveOperationIdsForParticipant', 'UNION SELECT r.operation_id', 'r.profile_id=?', 'FORCE INDEX (idx_phantom_economy_reservations_owner)', 'ORDER BY operation_id LIMIT 2', 'participantDriftTerminal', 'participantDriftAudit', 'lockDispatchInTransaction', 'DispatchLock', 'dispatchAborted') 'Immutable participant snapshot, participant lookup and lifecycle ordering'
	$lockLifecycleStart = $reservations.IndexOf('private LifecycleLock lockLifecycle')
	$lockLifecycleProfiles = $reservations.IndexOf('lockProfiles(connection, discovered.profileIds())', $lockLifecycleStart)
	$lockLifecycleOperation = $reservations.IndexOf('lockOperation(connection, operationId)', $lockLifecycleProfiles)
	$lockLifecycleReservations = $reservations.IndexOf('lockReservationKeys(connection, operationId)', $lockLifecycleOperation)
	$lockLifecycleReread = $reservations.IndexOf('discoverParticipantSet(connection, operationId)', $lockLifecycleReservations)
	Assert-True (($lockLifecycleStart -ge 0) -and ($lockLifecycleProfiles -gt $lockLifecycleStart) -and ($lockLifecycleOperation -gt $lockLifecycleProfiles) -and ($lockLifecycleReservations -gt $lockLifecycleOperation) -and ($lockLifecycleReread -gt $lockLifecycleReservations)) "Participant lifecycle DB lock order drifted."
	Assert-True ($reservations.Contains('(operation.state() == State.DISPATCHING) || (operation.state() == State.OBSERVING)') -and $reservations.Contains('A participant belongs to multiple active economy operations.')) "Participant boundary no longer fails closed for dispatched or multiple active operations."

	$schema = Read-TargetUtf8 'dist/db_installer/sql/game/phantom_reservations.sql'
	Contains-All $schema @('CREATE TABLE IF NOT EXISTS `phantom_economy_operations`', 'CREATE TABLE IF NOT EXISTS `phantom_economy_reservations`', 'CREATE TABLE IF NOT EXISTS `phantom_economy_audit`', '`canonical_resource_key`', '`owner_class_index`', '`terminal_result`', '`items_consumed`', '`items_produced`', '`crystals_produced`', '`target_items_destroyed`', 'ENGINE=InnoDB') 'Economy migration'

	$policy = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyPolicy.java'
	$data = Read-TargetUtf8 'dist/game/data/phantoms/economy/high-five-economy-v1.xml'
	Contains-All $policy @('disallow-doctype-decl', 'ACCESS_EXTERNAL_DTD', 'strictUtf8', 'retainedNonterminalOperations != 100000', 'enchantAttempts != 16', 'ACTIVE_REQUIRED') 'Economy policy loader'
	Contains-All $data @('payloadBytes="4096"', 'reservationsPerOperation="32"', 'itemIdsPerRead="24"', 'participantsPerOperation="4"', 'retainedNonterminalOperations="100000"', 'replacementReservePercent="100"', 'equippedBackground="ACTIVE_REQUIRED"') 'Economy policy data'

	$observer = Read-TargetUtf8 'java/org/l2jmobius/gameserver/managers/RecipeCraftObserver.java'
	$recipeManager = Read-TargetUtf8 'java/org/l2jmobius/gameserver/managers/RecipeManager.java'
	Contains-All $observer @('ACCEPTED', 'INGREDIENTS_CONSUMED', 'SUCCESS_PRODUCT', 'RARE_PRODUCT', 'CRAFT_FAILED', 'ABORTED', 'List.copyOf(items)') 'Recipe observer'
	Contains-All $recipeManager @('requestMakeItem(player, recipeListId, RecipeCraftObserver.NONE)', 'RecipeCraftObserver observer', 'new RecipeItemMaker(player, recipeList, player, observer)', 'RecipeCraftObserver.Type.INGREDIENTS_CONSUMED', 'RecipeCraftObserver.Type.RARE_PRODUCT') 'RecipeManager observer seam'

	$projection = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyProjection.java'
	Contains-All $projection @('RecipePlan plan = acquisition.recipePlan()', 'PlayerConfig.ALT_GAME_CREATION', 'recipe.getSuccessRate()', 'recipe.getRareItemId()', 'scroll.calculateSuccess', 'SAFE_FAILURE', 'BLESSED_RESET', 'DESTROYED_WITH_CRYSTALS', 'ItemLocation.PAPERDOLL', 'Result.ACTIVE_REQUIRED') 'Background craft/enchant projection'
	Contains-All $projection @('record AuthorityFact', 'record AuthorityFacts', 'getBytes(StandardCharsets.UTF_8).length', 'Double.doubleToRawLongBits', 'acquisition.selected_source_id', 'plan.node.', 'plan.deficit.', 'recipe.ingredient.', 'recipe.stat.', 'recipe.normal_output.', 'recipe.rare_output.', 'config.craft_masterwork_chance_rate', 'target.crystal_destruction_consequence', 'addEnchantItemFacts(facts, "scroll."', 'addEnchantItemFacts(facts, "support."', 'prefix + "bonus_rate"', 'prefix + "weapon"', 'combination.valid', 'config.disable_over_enchanting', 'request.riskBudget()', 'maximumExpensePercent()') 'Complete craft/enchant authority and risk projection'

	$enchantService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/services/EnchantItemService.java'
	$packet = Read-TargetUtf8 'java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java'
	Contains-All $enchantService @('public final class EnchantItemService', 'destroyItem(ItemProcessType.FEE', 'calculateSuccess(player, item, supportTemplate)', 'scrollTemplate.isSafe()', 'scrollTemplate.isBlessed()', 'destroyItem(ItemProcessType.DESTROY', 'getCrystalItemId()', 'setActiveEnchantItemId(Player.ID_NONE)', 'broadcastUserInfo()', 'record Event') 'Canonical enchant service'
	Contains-All $enchantService @('player.isProcessingTransaction()', 'player.isInStoreMode()', 'item.getOwnerId() != player.getObjectId()', 'scroll.getOwnerId() != player.getObjectId()', 'support.getOwnerId() != player.getObjectId()', 'item == scroll', '!item.isEnchantable()', 'DISABLE_OVER_ENCHANTING') 'Canonical enchant actor validation'
	Contains-All $packet @('EnchantItemService.getInstance().execute', 'new Request(player, _objectId, scrollObjectId, supportObjectId, true', 'getActiveEnchantItemId()', 'getActiveEnchantTimestamp()') 'Ordinary enchant packet adapter'
	Assert-True (!$packet.Contains('calculateSuccess(') -and !$packet.Contains('destroyItem(ItemProcessType.DESTROY')) "Ordinary packet retained canonical enchant mutation."
	$authorityPacketBytes = Read-CommitBytes $AuthorityCommit 'java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java'
	Assert-True ([Convert]::ToBase64String($authorityPacketBytes) -eq [Convert]::ToBase64String((Read-TargetBytes 'java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java'))) "Ordinary enchant packet differs byte-for-byte from the accepted authority completion."

	$economyFiles = @($paths | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/phantoms/economy/.*\.java$' })
	foreach ($path in $economyFiles)
	{
		$text = Read-TargetUtf8 $path
		Assert-True ($text -notmatch 'RequestEnchantItem|RequestRecipeItemMakeSelf|GameClient|sendPacket\s*\(|new\s+Thread\s*\(|ThreadPool|Executor|ScheduledFuture|\bFuture\b') "Forbidden packet/client/worker dependency in $path"
	}

	$background = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyBackgroundTransaction.java'
	Contains-All $background @('connection.setAutoCommit(false)', 'ORDER BY object_id LIMIT', 'FOR UPDATE', 'lockDispatchInTransaction', 'commitDispatchInTransaction', 'writeComponent', 'updateVitals', 'connection.commit()', 'connection.rollback()', 'setQueryTimeout', 'interface FaultInjector', 'FaultInjector.none()', 'AFTER_PROFILE_LOCK', 'AFTER_DISPATCH_LOCK', 'AFTER_COMPONENT_LOCKS', 'AFTER_CHARACTER_RECIPE_SKILL_LOCKS', 'AFTER_ITEM_LOCKS', 'AFTER_ITEM_WRITES', 'AFTER_VITAL_WRITES', 'AFTER_BACKGROUND_WRITE', 'AFTER_ACQUISITION_OR_GOAL_WRITE', 'AFTER_OPERATION_AUDIT_WRITE', 'BEFORE_COMMIT', 'AFTER_COMMIT', 'rare=" + outcome.rare()', 'sourceFailure=') 'Atomic background economy transaction'
	Contains-All $background @('itemCounts(items).getOrDefault(Inventory.ADENA_ID, 0L)', 'goal.riskBudget()', 'ResourceKind.ADENA', 'spec.replacementReserve()', 'reservedCounts.putIfAbsent(recipe.getItemId()', 'reservedCounts.putIfAbsent(recipe.getRareItemId()', 'requireKey(keys, reservation(background, ResourceKind.ITEM_COUNT, 0, recipe.getItemId())', 'result.rare_product') 'Background authority, Adena and craft output reservations'
	Contains-All $background @('if (!dispatch.ready())', '_reservations.dispatchAborted(dispatch.releasedReservations())', 'dispatch.participants().characterObjectId(profileId)') 'Participant-aware background dispatch'
	$craftStart = $background.IndexOf('public TransactionResult executeCraft')
	$craftDispatch = $background.IndexOf('lockDispatchInTransaction', $craftStart)
	$enchantStart = $background.IndexOf('public TransactionResult executeEnchant')
	$enchantDispatch = $background.IndexOf('lockDispatchInTransaction', $enchantStart)
	Assert-True (($craftStart -ge 0) -and ($craftDispatch -gt $craftStart) -and !$background.Substring($craftStart, $craftDispatch - $craftStart).Contains('lockProfile(')) "Background craft pre-locked the initiator before participant dispatch."
	Assert-True (($enchantStart -ge 0) -and ($enchantDispatch -gt $enchantStart) -and !$background.Substring($enchantStart, $enchantDispatch - $enchantStart).Contains('lockProfile(')) "Background enchant pre-locked the initiator before participant dispatch."
	Assert-True ($background -notmatch '(?i)(?:UPDATE|INSERT|DELETE)\s+(?:character_quests|clan_data|items?\s+SET\s+owner_id)') "Background economy crossed forbidden ownership."

	$commerce = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/commerce/L2jCommerceBackend.java'
	$acquisition = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/acquisition/PhantomAcquisitionService.java'
	$backgroundAccepted = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java'
	$materialization = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationLifecyclePort.java'
	Contains-All $commerce @('PhantomEconomyConflictPort.claim', '_economyClaim.acquired()', '_economyClaim.close()', 'getItemLocation().name()') 'Commerce conflict integration'
	Contains-All $acquisition @('PhantomEconomyConflictPort.isInstalled()', 'PhantomEconomyConflictPort.claim', 'economyClaim.acquired()', 'try (PhantomEconomyConflictPort.Claim economyClaim') 'Acquisition conflict integration'
	Contains-All $backgroundAccepted @('PhantomEconomyConflictPort.claim', 'economyClaim.acquired()', 'try (economyClaim; Connection connection') 'Background conflict integration'
	Contains-All $materialization @('static PhantomMaterializationLifecyclePort chain', 'beforeMaterialize', 'afterPlayerLoad', 'materializeAborted', 'beforeStore', 'afterStore') 'Materialization lifecycle chain'

	$system = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Contains-All $system @('if (!_settings.enabled())', 'PhantomEconomyPolicy.load', 'new PhantomEconomyReservationService', 'PhantomEconomyConflictPort.install', 'economyDecision.registerCandidates', 'economyDecision.registerHandlers', '_economyReservations.shutdown', 'PhantomEconomyConflictPort.uninstall') 'PhantomSystem economy composition'

	$decision = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyDecision.java'
	Contains-All $decision @('economy.reserve', 'economy.dispatch', 'economy.reconcile', 'PhantomAcquisitionGoalSpec.GOAL_TYPE', 'PhantomEnchantGoalSpec.GOAL_TYPE', '_service.cancel', 'cancellation.status()') 'Economy Decision steps and cancellation terminalization'
	$economyService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyService.java'
	Contains-All $economyService @('target.getItemLocation().name()', 'scroll.getItemLocation().name()', 'support.getItemLocation().name()') 'Active enchant reservation location'
	Contains-All $economyService @('acquisition.progress() < acquisition.requiredAmount()', 'craft.pre_effect_aborted', 'craft.canonical_failure', 'result.rare_product', 'craftConsequence(terminal, targetProduced', 'State.INCONSISTENT', 'State.DISPATCHING, State.OBSERVING', 'economy.reconcile.observing', 'economy.cancel.observing_fail_stop', 'craft.authority.drift', 'enchant.authority.drift', 'exactCraftObservation', 'exactEnchantObservation', 'ResourceKind.ADENA', 'Inventory.ADENA_ID', 'goal.replacementReserve()', 'storedGoal.riskBudget()', 'maximumExpensePercent()', 'reservedCounts.putIfAbsent(recipe.getItemId()', 'reservedCounts.putIfAbsent(recipe.getRareItemId()') 'Resumable active authority, risk and exact attribution'
	Assert-True (!$economyService.Contains('getLocation().toString()')) "Active enchant reservation captured world coordinates as item location."

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java'
	foreach ($evidence in @('materialization.materialize(profile.profileId())', 'World.getInstance().getPlayer', 'RecipeManager.getInstance().requestMakeItem', 'EnchantItemService.getInstance().execute', 'PhantomEconomyConflictPort.install', 'foreign.acquired()', 'owner.acquired()', 'Result.SAFE_FAILURE', 'Result.BLESSED_RESET', 'Result.DESTROYED_WITH_CRYSTALS', 'State.INCONSISTENT', 'expireDue', '100000', '10000', 'beforeBoundary', 'TEST_DATABASE', 'decision-service-repeatable-lifecycle', 'actual-outcome-attribution', 'actual-outcome-matrix', 'non-atomic-restart-windows', 'atomic-fault-matrix', 'FaultPoint.values()', 'operationIds.stream().distinct().count()', 'result.rare_product', 'participant-link-mismatch', 'item-count-object-overlap', 'item-object-overlap-matrix', 'canonical-actor-guards', 'craft-authority-facts', 'enchant-authority-and-risk', 'shutdown-terminalizes-claims', 'New craft plan did not resume DISPATCHING', 'New enchant plan did not resume DISPATCHING', 'OBSERVING cancellation', 'authority drift', 'NPC BUY Adena writer', 'assertEveryAuthorityFactChangesHash', 'recipe.stat.0.value', 'recipe.rare_output.stackable', 'economy.backgroundEnchantFaultPoints', 'participant-boundary-lifecycle', 'participant-transition-renew-drift', 'participant-reverse-order-stress', 'participant-dispatch-lock-order', 'iteration < 100', 'beforeMaterialize(adapterParticipant.profileId()', 'beforeStore(adapterParticipant.profileId()'))
	{
		Assert-True ($tests.Contains($evidence)) "Goal 022c1 test evidence is absent: $evidence"
	}
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('name="phantom.goal022c1.seed" value="22002201"', 'name="phantom-economy-checkpoint1-test"', 'name="phantom-economy-checkpoint1-affected-test"', 'name="phantom-static-verify-022c1"') 'Goal 022c1 Ant routes'
	foreach ($testMode in @('economy-reservation-schema', 'economy-reservation-concurrency', 'economy-self-craft-active', 'economy-self-craft-background', 'economy-enchant-active', 'economy-enchant-background', 'economy-restart-transition', 'economy-lifecycle-performance'))
	{
		Assert-True ($build.Contains($testMode)) "Mandatory Goal 022c1 mode is absent: $testMode"
	}

	$goal021 = Read-TargetUtf8 'docs/phantoms/reviews/021-final-review.md'
	Contains-All $goal021 @('Goal 021 Checkpoint 1: `ACCEPT`', 'Goal 021 Checkpoint 2: `ACCEPT`', 'Goal 021 overall: `ACCEPT`', $AcceptedGoal021) 'Goal 021 final review'
	$report = Read-TargetUtf8 'docs/phantoms/reports/022-checkpoint-1-economy-craft-enchant.md'
	$review = Read-TargetUtf8 'docs/phantoms/reviews/022-checkpoint-1-independent-review.md'
	Assert-True (($report -split "`r?`n").Count -le 300) "Goal 022c1 report exceeds 300 lines."
	Contains-All $report @('COMPLETED_PENDING_INDEPENDENT_REVIEW', $AcceptedGoal021, $FoundationCommit, $LifecycleCommit, $RequiredSubject, 'Goal 022', 'Checkpoint 2', '## Terminal section', 'authority/resume/risk completion') 'Goal 022c1 report'
	Contains-All $review @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', 'self-accept', 'Checkpoint 2') 'Independent-review handoff'

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B), @(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A), @(0x0420, 0x00A4), @(0x0420, 0x045A),
		@(0x0420, 0x0408), @(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5), @(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026), @(0x0421, 0x040F),
		@(0x0421, 0x20AC), @(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A), @(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026), @(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char] $_[0]) + ([string][char] $_[1])) }) -join '|'
	$replacement = [string][char] 0xFFFD
	$escaped = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $paths)
	{
		if (($path -match '\.(?:java|xml|md|txt|json|ps1|sql)$') -or ($path -eq 'build.xml'))
		{
			$text = Read-TargetUtf8 $path
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacement)) "Mojibake marker found in Goal 022c1 file: $path"
			if ($path -ne 'tools/phantoms/verify-task-022c1.ps1')
			{
				Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found in Goal 022c1 file: $path"
			}
		}
	}

	if ($script:Mode -eq "working")
	{
		foreach ($class in @(
			'../build/bin/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.class',
			'../build/bin/org/l2jmobius/gameserver/services/EnchantItemService.class'
		))
		{
			Assert-True (Test-Path -LiteralPath (Join-Path $script:ModuleRoot $class) -PathType Leaf) "Compiled Goal 022c1 class is absent: $class"
		}
		& git -c core.safecrlf=false diff --check $AuthorityCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Working git diff --check failed."
	}
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain Goal 022c1."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @(
			'org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.class',
			'org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyService.class',
			'org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyProjection.class',
			'org/l2jmobius/gameserver/services/EnchantItemService.class',
			'org/l2jmobius/gameserver/managers/RecipeCraftObserver.class'
		))
		{
			Assert-True ($jarEntries -contains $entry) "GameServer.jar lacks Goal 022c1 entry: $entry"
		}
		Assert-True ($jarEntries -notcontains 'data/phantoms/economy/high-five-economy-v1.xml') "Economy datapack must remain outside GameServer.jar."
		& git -c core.safecrlf=false diff --check $AuthorityCommit $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed git diff --check failed."
	}

	Write-Output 'TASK022C1_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "implementation_commit=$($script:TargetCommit)"
	Write-Output "accepted_goal021=$AcceptedGoal021"
	Write-Output "foundation_commit=$FoundationCommit"
	Write-Output "lifecycle_commit=$LifecycleCommit"
	Write-Output "authority_commit=$AuthorityCommit"
	Write-Output "seed=$RequiredSeed"
	Write-Output "foundation_scope=$($foundationPaths.Count)"
	Write-Output "foundation_production=$($foundationProduction.Count)"
	Write-Output "lifecycle_scope=$($lifecyclePaths.Count)"
	Write-Output "lifecycle_production=$($lifecycleProduction.Count)"
	Write-Output "authority_scope=$($authorityPaths.Count)"
	Write-Output "authority_production=$($authorityProduction.Count)"
	Write-Output "terminal_scope=$($paths.Count)"
	Write-Output "cumulative_scope=$($cumulativePaths.Count)"
	Write-Output "terminal_production=$($changedProduction.Count)"
	Write-Output "new_production=$($newProduction.Count)"
	Write-Output "sql=$($sql.Count)"
	Write-Output "economy_xml=$($economyXml.Count)"
	Write-Output "policy_sha256=$(Target-Sha256 'dist/game/data/phantoms/economy/high-five-economy-v1.xml')"
}
finally
{
	Pop-Location
}
