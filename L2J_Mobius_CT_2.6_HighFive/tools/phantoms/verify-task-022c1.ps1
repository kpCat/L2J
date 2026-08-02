param(
	[switch] $WorkingTree
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RequiredParent = "043844c0fd7a0bfcac0d5f58461a21633b032332"
$RequiredSubject = "feat(phantoms): add economy reservations craft and enchant"
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
	if ($WorkingTree)
	{
		Assert-True ($head -eq $RequiredParent) "Working Goal 022c1 must start at the required parent."
		$script:Mode = "working"
		$script:TargetCommit = "WORKING_TREE"
	}
	else
	{
		& git merge-base --is-ancestor $RequiredParent $head
		Assert-True ($LASTEXITCODE -eq 0) "Required Goal 021 baseline is not an ancestor of HEAD."
		$children = @(Git-Lines @("rev-list", "--ancestry-path", "--reverse", "$RequiredParent..$head"))
		Assert-True ($children.Count -ge 1) "Goal 022c1 implementation child is absent."
		$script:TargetCommit = $children[0]
		$script:Mode = "historical"
		Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredParent) "Goal 022c1 is not one ordinary child of the required parent."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 022c1 commit subject changed."
	}

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $changed @("diff", "--name-only", $RequiredParent, "--")
		Add-Untracked $changed
	}
	else
	{
		Add-Paths $changed @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--")
	}
	$paths = @($changed | Sort-Object)
	Assert-True (($paths.Count -gt 0) -and ($paths.Count -le 65)) "Goal 022c1 scope exceeds 65 files."
	foreach ($path in $paths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope Goal 022c1 path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|(^|/)(?:PlayerInventory|Inventory|TradeList|TradeItem)\.java$|L2J_Mobius_CT_(?!2\.6_HighFive)') "Forbidden Goal 022c1 path: $path"
		Assert-True ($path -notmatch '(?i)(direct.?trade|private.?store|player.?manufacture|mail|clan.?warehouse|023)') "Checkpoint 2 or Goal 023 path leaked into Goal 022c1: $path"
	}

	$newPaths = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $RequiredParent, "--")
		Add-Untracked $newPaths
	}
	else
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $RequiredParent, $script:TargetCommit, "--")
	}
	$newProduction = @($newPaths | Where-Object { Is-Production $_ })
	$changedProduction = @($paths | Where-Object { Is-Production $_ })
	$sql = @($paths | Where-Object { $_ -match '^dist/db_installer/sql/' })
	Assert-True ($newProduction.Count -le 24) "New production/data scope exceeds 24 files."
	Assert-True ($changedProduction.Count -le 38) "Changed production/data scope exceeds 38 files."
	Assert-True ($sql.Count -le 2) "Goal 022c1 exceeds two SQL migrations."

	$operation = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOperation.java'
	Contains-All $operation @('SELF_CRAFT', 'ITEM_ENCHANT', 'PREPARED', 'RESERVED', 'DISPATCHING', 'OBSERVING', 'COMMITTED', 'ABORTED', 'EXPIRED', 'INCONSISTENT', 'canonicalKey()', 'ownerClassIndex', 'MAX_PAYLOAD_BYTES = 4096') 'Economy operation contract'
	Assert-True ($operation.Contains('((state == State.PREPARED) || (state == State.RESERVED))') -and !$operation.Contains('case DISPATCHING -> (next == EXPIRED)')) "Economy expiry is not restricted to predispatch."

	$reservations = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java'
	Contains-All $reservations @('lockProfiles(connection, profileIds)', 'lockOperation(connection, operation.operationId())', 'lockReservationKeys(connection, operationId)', 'RESOURCE_CONFLICT', 'IDENTITY_CONFLICT', 'PROFILE_BUSY', 'retainedNonterminalOperations()', 'dispatch.ambiguous', 'EconomyConflictException', 'findReservations', 'nextAttempt') 'Reservation kernel'
	Assert-True ($reservations.IndexOf('lockProfiles(connection, profileIds)') -lt $reservations.IndexOf('lockOperation(connection, operation.operationId())')) "Profile/operation DB lock order drifted."
	Assert-True ($reservations.Contains('(operation.state() == State.DISPATCHING) || (operation.state() == State.OBSERVING)') -and $reservations.Contains('throw new EconomyConflictException')) "Materialization boundary does not fail stop after dispatch."

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

	$enchantService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/services/EnchantItemService.java'
	$packet = Read-TargetUtf8 'java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java'
	Contains-All $enchantService @('public final class EnchantItemService', 'destroyItem(ItemProcessType.FEE', 'calculateSuccess(player, item, supportTemplate)', 'scrollTemplate.isSafe()', 'scrollTemplate.isBlessed()', 'destroyItem(ItemProcessType.DESTROY', 'getCrystalItemId()', 'setActiveEnchantItemId(Player.ID_NONE)', 'broadcastUserInfo()', 'record Event') 'Canonical enchant service'
	Contains-All $packet @('EnchantItemService.getInstance().execute', 'new Request(player, _objectId, scrollObjectId, supportObjectId, true', 'getActiveEnchantItemId()', 'getActiveEnchantTimestamp()') 'Ordinary enchant packet adapter'
	Assert-True (!$packet.Contains('calculateSuccess(') -and !$packet.Contains('destroyItem(ItemProcessType.DESTROY')) "Ordinary packet retained canonical enchant mutation."

	$economyFiles = @($paths | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/phantoms/economy/.*\.java$' })
	foreach ($path in $economyFiles)
	{
		$text = Read-TargetUtf8 $path
		Assert-True ($text -notmatch 'RequestEnchantItem|RequestRecipeItemMakeSelf|GameClient|sendPacket\s*\(|new\s+Thread\s*\(|ThreadPool|Executor|ScheduledFuture|\bFuture\b') "Forbidden packet/client/worker dependency in $path"
	}

	$background = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyBackgroundTransaction.java'
	Contains-All $background @('connection.setAutoCommit(false)', 'ORDER BY object_id LIMIT', 'FOR UPDATE', 'lockDispatchInTransaction', 'commitDispatchInTransaction', 'writeComponent', 'updateVitals', 'connection.commit()', 'connection.rollback()', 'setQueryTimeout') 'Atomic background economy transaction'
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
	Contains-All $decision @('economy.reserve', 'economy.dispatch', 'economy.reconcile', 'PhantomAcquisitionGoalSpec.GOAL_TYPE', 'PhantomEnchantGoalSpec.GOAL_TYPE') 'Economy Decision steps'
	$economyService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyService.java'
	Contains-All $economyService @('target.getItemLocation().name()', 'scroll.getItemLocation().name()', 'support.getItemLocation().name()') 'Active enchant reservation location'
	Assert-True (!$economyService.Contains('getLocation().toString()')) "Active enchant reservation captured world coordinates as item location."

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java'
	foreach ($evidence in @('materialization.materialize(profile.profileId())', 'World.getInstance().getPlayer', 'RecipeManager.getInstance().requestMakeItem', 'EnchantItemService.getInstance().execute', 'PhantomEconomyConflictPort.install', 'foreign.acquired()', 'owner.acquired()', 'Result.SAFE_FAILURE', 'Result.BLESSED_RESET', 'Result.DESTROYED_WITH_CRYSTALS', 'State.INCONSISTENT', 'expireDue', '100000', '10000', 'beforeBoundary', 'TEST_DATABASE'))
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
	Contains-All $goal021 @('Goal 021 Checkpoint 1: `ACCEPT`', 'Goal 021 Checkpoint 2: `ACCEPT`', 'Goal 021 overall: `ACCEPT`', $RequiredParent) 'Goal 021 final review'
	$report = Read-TargetUtf8 'docs/phantoms/reports/022-checkpoint-1-economy-craft-enchant.md'
	$review = Read-TargetUtf8 'docs/phantoms/reviews/022-checkpoint-1-independent-review.md'
	Assert-True (($report -split "`r?`n").Count -le 300) "Goal 022c1 report exceeds 300 lines."
	Contains-All $report @('COMPLETED_PENDING_INDEPENDENT_REVIEW', $RequiredParent, $RequiredSubject, 'Goal 022', 'Checkpoint 2', '## Terminal section') 'Goal 022c1 report'
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
		& git -c core.safecrlf=false diff --check $RequiredParent --
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
		& git -c core.safecrlf=false diff --check $RequiredParent $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed git diff --check failed."
	}

	Write-Output 'TASK022C1_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "implementation_commit=$($script:TargetCommit)"
	Write-Output "required_parent=$RequiredParent"
	Write-Output "seed=$RequiredSeed"
	Write-Output "scope=$($paths.Count)"
	Write-Output "production=$($changedProduction.Count)"
	Write-Output "new_production=$($newProduction.Count)"
	Write-Output "sql=$($sql.Count)"
	Write-Output "policy_sha256=$(Target-Sha256 'dist/game/data/phantoms/economy/high-five-economy-v1.xml')"
}
finally
{
	Pop-Location
}
