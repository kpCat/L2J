param(
	[switch] $WorkingTree
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AcceptedCheckpoint1 = "feb569efa787917411cfb5c419f0e8646c3ee84f"
$FoundationCommit = "5fd8dcfc1b294e234cc55aaabc0cbfbbd134e1f7"
$FoundationSubject = "feat(phantoms): add multiparty trade stores and manufacture"
$CausalityCommit = "988ca85e91fb0e3aa2f58dc2aaa1e4277290e1a2"
$CausalitySubject = "fix(phantoms): close multiparty economy causality and lifecycle"
$AcceptedBaseline = "1c8c99f83ebc9f32ac2c3bc670aec506b8efcccb"
$RequiredSubject = "fix(phantoms): close external trade and manufacture observer lifetime"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "22002202"
$RequiredWaiver = "ACCEPT_WITH_EXPLICIT_UNRELATED_TIMING_FLAKE_WAIVER"

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
	Assert-True ($process.ExitCode -eq 0) "Committed Goal 022c2 artifact is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Working Goal 022c2 artifact is absent: $relativePath"
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

function Contains-All([string] $text, [string[]] $tokens, [string] $name)
{
	foreach ($token in $tokens)
	{
		Assert-True ($text.Contains($token)) "$name is missing required token: $token"
	}
}

function Read-AddedText([string] $relativePath)
{
	$repositoryPath = $script:ModulePrefix + $relativePath
	if ($script:Mode -eq "working")
	{
		$lines = & git -c core.safecrlf=false diff --unified=0 $CausalityCommit -- $repositoryPath
	}
	else
	{
		$lines = & git -c core.safecrlf=false diff --unified=0 $CausalityCommit $script:TargetCommit -- $repositoryPath
	}
	Assert-True ($LASTEXITCODE -eq 0) "Could not inspect added lines for $relativePath"
	return (($lines | Where-Object { $_.StartsWith('+') -and !$_.StartsWith('+++') }) -join "`n")
}

function Is-ProductionData([string] $path)
{
	return ($path -match '^java/org/l2jmobius/gameserver/') -or ($path -match '^dist/(?:game/data|game/config|db_installer/sql)/')
}

function Is-Allowed([string] $path)
{
	return ($path -in @(
		'docs/phantoms/reports/022-checkpoint-2-multiparty-trade-stores-manufacture.md',
		'docs/phantoms/reviews/022-checkpoint-2-independent-review.md',
		'java/org/l2jmobius/gameserver/managers/RecipeCraftObserver.java',
		'java/org/l2jmobius/gameserver/managers/RecipeManager.java',
		'java/org/l2jmobius/gameserver/network/holders/TradeList.java',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java',
		'java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.java',
		'java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java',
		'java/org/l2jmobius/gameserver/services/DirectTradeService.java',
		'java/org/l2jmobius/gameserver/services/ManufactureService.java',
		'java/org/l2jmobius/gameserver/services/PrivateStoreService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomMultipartyEconomySuite.java',
		'tools/phantoms/verify-task-022c2.ps1'
	))
}

function Is-TerminalAllowed([string] $path)
{
	return ($path -in @(
		'build.xml',
		'docs/phantoms/reports/022-checkpoint-2-multiparty-trade-stores-manufacture.md',
		'docs/phantoms/reviews/022-checkpoint-2-independent-review.md',
		'java/org/l2jmobius/gameserver/managers/RecipeManager.java',
		'java/org/l2jmobius/gameserver/network/holders/TradeList.java',
		'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
		'java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.java',
		'java/org/l2jmobius/gameserver/services/DirectTradeService.java',
		'java/org/l2jmobius/gameserver/services/PrivateStoreService.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java',
		'test/java/org/l2jmobius/tests/phantoms/PhantomMultipartyEconomySuite.java',
		'tools/phantoms/verify-task-022c2.ps1'
	))
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 022c2 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	& git merge-base --is-ancestor $AcceptedCheckpoint1 $FoundationCommit
	Assert-True ($LASTEXITCODE -eq 0) "C2 foundation does not descend from accepted Checkpoint 1."
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $FoundationCommit) | Select-Object -First 1) -eq $AcceptedCheckpoint1) "C2 foundation is not the ordinary direct child of accepted Checkpoint 1."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $FoundationCommit) | Select-Object -First 1) -eq $FoundationSubject) "C2 foundation subject changed."
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $CausalityCommit) | Select-Object -First 1) -eq $FoundationCommit) "C2 causality completion is not the ordinary direct child of the foundation."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $CausalityCommit) | Select-Object -First 1) -eq $CausalitySubject) "C2 causality completion subject changed."
	$causalityLineage = @(Git-Lines @("rev-list", "--reverse", "--ancestry-path", "$FoundationCommit..$CausalityCommit"))
	Assert-True (($causalityLineage.Count -eq 1) -and ($causalityLineage[0] -eq $CausalityCommit)) "Frozen C2 foundation-to-causality ancestry changed."
	& git merge-base --is-ancestor $CausalityCommit $head
	Assert-True ($LASTEXITCODE -eq 0) "C2 causality completion is not an ancestor of HEAD."
	& git merge-base --is-ancestor $AcceptedBaseline $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 022 baseline is not an ancestor of HEAD."
	$script:TargetCommit = $AcceptedBaseline
	$script:Mode = "historical"
	Assert-True ((Git-Lines @("show", "-s", "--format=%P", $script:TargetCommit) | Select-Object -First 1) -eq $CausalityCommit) "Accepted Goal 022 baseline is not the direct causality child."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Accepted Goal 022 baseline subject changed."

	$foundationChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-Paths $foundationChanged @("diff", "--name-only", $AcceptedCheckpoint1, $FoundationCommit, "--")
	$foundationPaths = @($foundationChanged | Sort-Object)
	Assert-True (($foundationPaths.Count -gt 0) -and ($foundationPaths.Count -le 78)) "Accepted C2 foundation scope exceeds its frozen 78-file bound."

	$causalityChanged = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	Add-Paths $causalityChanged @("diff", "--name-only", $FoundationCommit, $CausalityCommit, "--")
	$causalityPaths = @($causalityChanged | Sort-Object)
	Assert-True (($causalityPaths.Count -gt 0) -and ($causalityPaths.Count -le 17)) "Accepted C2 causality scope exceeds its frozen 17-file bound."
	foreach ($path in $causalityPaths)
	{
		Assert-True (Is-Allowed $path) "Out-of-scope accepted C2 causality path: $path"
	}
	$causalityProduction = @($causalityPaths | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/.*\.java$' })
	Assert-True ($causalityProduction.Count -le 11) "Accepted C2 causality production scope exceeds 11 files."

	$changed = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $changed @("diff", "--name-only", $CausalityCommit, "--")
		Add-Untracked $changed
	}
	else
	{
		Add-Paths $changed @("diff", "--name-only", $CausalityCommit, $script:TargetCommit, "--")
	}
	$paths = @($changed | Sort-Object)
	Assert-True (($paths.Count -gt 0) -and ($paths.Count -le 11)) "Goal 022c2 terminal completion scope exceeds 11 files."
	foreach ($path in $paths)
	{
		Assert-True (Is-TerminalAllowed $path) "Out-of-scope Goal 022c2 terminal path: $path"
		Assert-True ($path -notmatch '(^|/)Player\.java$|(^|/)(?:Inventory|PlayerInventory)\.java$|L2J_Mobius_CT_(?!2\.6_HighFive)') "Forbidden Goal 022c2 path: $path"
		Assert-True ($path -notmatch '(?i)(mail|freight|warehouse|auction|combat|clan|goal.?023|tasks/023)') "Forbidden subsystem leaked into Goal 022c2: $path"
	}

	$newPaths = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $CausalityCommit, "--")
		Add-Untracked $newPaths
	}
	else
	{
		Add-Paths $newPaths @("diff", "--name-only", "--diff-filter=A", $CausalityCommit, $script:TargetCommit, "--")
	}
	$production = @($paths | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/.*\.java$' })
	$newProductionData = @($newPaths | Where-Object { Is-ProductionData $_ })
	$sql = @($paths | Where-Object { $_ -match '^dist/db_installer/sql/.*\.sql$' })
	$policyXml = @($paths | Where-Object { $_ -match '(?i)(?:policy|policies).*\.xml$' })
	Assert-True ($production.Count -le 6) "Goal 022c2 terminal production scope exceeds 6 files."
	Assert-True ($newProductionData.Count -eq 0) "Goal 022c2 terminal completion added production/data files."
	Assert-True ($sql.Count -eq 0) "Goal 022c2 terminal completion changed SQL/schema."
	Assert-True ($policyXml.Count -eq 0) "Goal 022c2 terminal completion changed policy XML."

	$c1Review = Read-TargetUtf8 'docs/phantoms/reviews/022-checkpoint-1-final-review.md'
	Contains-All $c1Review @($AcceptedCheckpoint1, $RequiredWaiver, 'Goal 022 Checkpoint 2: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`', 'Goal 022 overall: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`', 'does not claim that the C1 plain verify', 'passed.') 'C1 waiver review'
	$c1Verifier = Read-TargetUtf8 'tools/phantoms/verify-task-022c1.ps1'
	Contains-All $c1Verifier @('$AcceptedCheckpointCommit = "feb569efa787917411cfb5c419f0e8646c3ee84f"', '$script:Mode = "historical"', 'merge-base --is-ancestor') 'Historical descendant-compatible C1 verifier'

	$migration = Read-TargetUtf8 'dist/db_installer/sql/game/phantom_reservations_checkpoint2.sql'
	Contains-All $migration @('CREATE INDEX IF NOT EXISTS idx_phantom_economy_reservations_profile_operation', '(profile_id, operation_id)', 'CREATE TABLE IF NOT EXISTS `phantom_economy_offers`', '`offer_payload` VARBINARY(4096)', '`counterparty_profile_id`', '`row_version`', 'ENGINE=InnoDB', 'ON DELETE CASCADE') 'C2 migration'
	$reservations = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyReservationService.java'
	Contains-All $reservations @('findActiveOperationIdsForParticipant', 'idx_phantom_economy_reservations_profile_operation', 'r.profile_id=?', 'UNION SELECT r.operation_id', 'profileId == 0', 'lockProfiles(connection, discovered.profileIds())', 'lockOperation(connection, operationId)', 'lockReservationKeys(connection, operationId)', 'discoverParticipantSet(connection, operationId)', 'participantsValid()', 'participantDriftTerminal', 'A terminal transition may remove participant reservations after the indexed lookup.', 'raced.state().terminal()') 'Indexed participant lifecycle and terminal race'
	$lookupStart = $reservations.IndexOf('private List<String> findActiveOperationIdsForParticipant')
	$lookupEnd = $reservations.IndexOf('private static void addParticipant', $lookupStart)
	Assert-True (($lookupStart -ge 0) -and ($lookupEnd -gt $lookupStart) -and !$reservations.Substring($lookupStart, $lookupEnd - $lookupStart).Contains('character_object_id=')) "Participant lookup still depends on the current character link."

	$operation = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOperation.java'
	Contains-All $operation @('DIRECT_TRADE', 'PRIVATE_STORE_BUY', 'PRIVATE_STORE_SELL', 'PLAYER_MANUFACTURE', '(profileId < 0)', 'canonicalKey()', 'overlaps(Reservation other)') 'External-capable operation model'
	$offer = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOffer.java'
	Contains-All $offer @('DRAFT', 'OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'CONSUMED', 'INCONSISTENT', 'payload.length > 4096', 'initiatorLines > 16', 'counterpartyLines > 16', 'contentHash(payload)', 'expiresEpochMillis') 'Immutable bounded offer model'
	$offerService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOfferService.java'
	Contains-All $offerService @("offer_state='OFFERED'", 'State.OFFERED, State.EXPIRED', 'SELECT 1 FROM phantom_economy_offers', 'bindOperation', 'consume', 'inconsistent', 'findActiveAfter', 'FOR UPDATE') 'Durable offer lifecycle'

	$direct = Read-TargetUtf8 'java/org/l2jmobius/gameserver/services/DirectTradeService.java'
	Contains-All $direct @('public Result request', 'public Result answer', 'public Result addItem', 'public Result finish', 'public void cancel', 'public boolean cancel(Player player, Player expectedPartner)', 'canonicalPairCleared', 'beforeExchange', '_entry.observer.beforeExecute(first, second)', 'afterTransfer', 'afterExecute', 'Pair.of', 'first < second') 'Canonical direct-trade service under TradeList locks and exact pair cleanup'
	$tradeList = Read-TargetUtf8 'java/org/l2jmobius/gameserver/network/holders/TradeList.java'
	Contains-All $tradeList @('confirm(ExchangeObserver observer)', 'beforeExchange', 'afterTransfer', 'afterExchange', 'privateStoreBuyExact', 'privateStoreSellExact', 'enum MutationMode', 'ORDINARY_COMPATIBLE', 'STRICT_EXACT_OBJECT', 'strictBuyFromSellStorePreflight', 'strictSellToBuyStorePreflight') 'Canonical TradeList seams and strict aggregate preflight'
	$privateStore = Read-TargetUtf8 'java/org/l2jmobius/gameserver/services/PrivateStoreService.java'
	Contains-All $privateStore @('public Result buy', 'public Result sell', 'public Result buyExact', 'public Result sellExact', 'listingHash', 'requestHash', 'expectedListingHash', 'expectedRequestHash', 'MutationMode.ORDINARY_COMPATIBLE', 'MutationMode.STRICT_EXACT_OBJECT', 'beforeMutation', 'afterMutation', 'OfflineTraderTable.getInstance().onTransaction', 'owner.setPrivateStoreType(PrivateStoreType.NONE)') 'Canonical private-store authority service'
	Assert-True ($privateStore.IndexOf('observer.afterMutation(direction, actor, owner, list, beforeHash, listingHash(list), true)') -lt $privateStore.IndexOf('owner.setPrivateStoreType(PrivateStoreType.NONE)')) "Empty-store cleanup precedes exact economy observation."
	$manufacture = Read-TargetUtf8 'java/org/l2jmobius/gameserver/services/ManufactureService.java'
	Contains-All $manufacture @('public Result manufacture', 'PrivateStoreType.MANUFACTURE', 'customer.isCrafting()', 'manufacturer.isCrafting()', 'LocationUtil.checkIfInRange(150', 'ManufactureStartResult.STARTED', 'STARTED', 'REJECTED_BEFORE_EFFECT') 'Structured canonical manufacture service'

	$packetChecks = @(
		@('TradeRequest.java', 'DirectTradeService.getInstance().request', 'onTransactionRequest('),
		@('AnswerTradeRequest.java', 'DirectTradeService.getInstance().answer', 'startTrade('),
		@('AddTradeItem.java', 'DirectTradeService.getInstance().addItem', 'trade.addItem('),
		@('TradeDone.java', 'DirectTradeService.getInstance().finish', 'trade.confirm('),
		@('RequestPrivateStoreBuy.java', 'PrivateStoreService.getInstance().buy', '.privateStoreBuy('),
		@('RequestPrivateStoreSell.java', 'PrivateStoreService.getInstance().sell', '.privateStoreSell('),
		@('RequestRecipeShopMakeItem.java', 'ManufactureService.getInstance().manufacture', 'requestManufactureItem(')
	)
	foreach ($check in $packetChecks)
	{
		$text = Read-TargetUtf8 ("java/org/l2jmobius/gameserver/network/clientpackets/" + $check[0])
		$packetPath = "java/org/l2jmobius/gameserver/network/clientpackets/" + $check[0]
		Assert-True ([Convert]::ToBase64String((Read-CommitBytes $CausalityCommit $packetPath)) -ceq [Convert]::ToBase64String((Read-TargetBytes $packetPath))) "Ordinary packet changed from the accepted C2 causality commit: $($check[0])"
		Assert-True ($text.Contains($check[1])) "Ordinary packet does not delegate: $($check[0])"
		Assert-True (!$text.Contains($check[2])) "Ordinary packet retained a second mutation path: $($check[0])"
	}

	$recipeObserver = Read-TargetUtf8 'java/org/l2jmobius/gameserver/managers/RecipeCraftObserver.java'
	$recipeManager = Read-TargetUtf8 'java/org/l2jmobius/gameserver/managers/RecipeManager.java'
	Contains-All $recipeObserver @('record Authority', 'requiredIngredients', 'FEE_TRANSFERRED', 'INGREDIENTS_CONSUMED', 'SUCCESS_PRODUCT', 'RARE_PRODUCT', 'CRAFT_FAILED', 'ABORTED', 'expConsequence', 'spConsequence', 'hpConsumed', 'mpConsumed', 'List.copyOf(items)') 'Multi-party recipe authority and consequence evidence'
	Contains-All $recipeManager @('ManufactureStartResult', 'STARTED', 'REJECTED_BEFORE_EFFECT', 'RecipeCraftObserver observer', 'authority()', 'FEE_TRANSFERRED', 'INGREDIENTS_CONSUMED', 'SUCCESS_PRODUCT', 'RARE_PRODUCT', 'CRAFT_FAILED', 'isManufactureActive', 'requestMakeItemAbort') 'RecipeManager structured start, formula and canonical abort ownership'

	$goalSpec = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomSocialEconomyGoalSpec.java'
	Contains-All $goalSpec @('trade.exchange', 'private.store.buy', 'private.store.sell', 'manufacture.item', 'record DirectTrade', 'record StoreBuy', 'record StoreSell', 'record Manufacture') 'Strict C2 Goals'
	$decision = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyDecision.java'
	Contains-All $decision @('DISCOVER_OR_LOAD_OFFER', 'OFFER_OR_ACCEPT', 'RESERVE', 'DISPATCH', 'OBSERVE_RECONCILE', 'CLOSE', 'new PhantomPlanStep(5') 'Six-step C2 Decision'
	$orchestrator = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.java'
	Contains-All $orchestrator @('acquireParticipants', 'new TreeMap<>()', 'admittedActionCount() != 0', 'admittedActionCount() != 1', 'participants.retain()', 'participants.exclusive()', 'directAuthority', 'exactTradeList', 'expectedTransfers', 'if (!counterpartyList.isConfirmed())', 'cancelDirectForShutdown', 'STRICT_EXACT_OBJECT', 'manufactureAuthority', '|stat-use:', '|alt-stat-change:', '|craft-config:', '|customer-capacity:', '|economy-policy:', 'manufacture.authority.changed', 'private boolean _tainted', 'private String _firstTaintReason = ""', 'requestMakeItemAbort(_manufacturer)', 'FaultPoint.AFTER_RECIPE_INGREDIENTS', 'FaultPoint.AFTER_PRODUCT_OR_FAILURE', '_faults.inject(FaultPoint.AFTER_OPERATION_AUDIT)', 'finally', 'public record ShutdownResult', 'manufacture.callback_missing_before_effect', 'manufacture.callback_missing_after_effect', 'exactProgress', 'exactVitals', 'StepResult.activeRequired', 'State.DISPATCHING, PhantomEconomyOperation.State.OBSERVING', 'globallyConserved', 'exactTransfer', 'State.INCONSISTENT') 'Completion causality, external consent, terminal cleanup and manufacture taint orchestration'
	Assert-True ($orchestrator -notmatch 'ClientPacket|GameClient|sendPacket\s*\(|new\s+Thread\s*\(|ThreadPool|Executor|ScheduledFuture|\bFuture\b') "Multiparty orchestrator gained a forbidden packet/client/worker dependency."

	$storePlan = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomStorePlan.java'
	$storeService = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java'
	Contains-All $storePlan @('SELL', 'PACKAGE_SELL', 'BUY', 'MANUFACTURE', 'contentHash', 'expiresEpochMillis', 'SCHEMA_VERSION') 'Durable store plan'
	Contains-All $storeService @('acquireExclusive', 'admittedActionCount() != 0', 'admittedActionCount() != 1', 'Result.RETRY', 'Result.INCONSISTENT', 'record ShutdownResult', 'retryProfileIds', 'inconsistentProfileIds', 'deleteComponent', 'closeOwnerObserver') 'Truthful visible Phantom store close and shutdown'
	$lifecycle = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyMaterializationLifecycle.java'
	Contains-All $lifecycle @('_offers.blocksMaterialization(profileId)', '_reservations.beforeBoundary(profileId', 'player.getPrivateStoreType() != PrivateStoreType.NONE') 'Offer/store materialization boundary'

	$system = Read-TargetUtf8 'java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java'
	Contains-All $system @('new PhantomEconomyOfferService', 'new PhantomMultipartyEconomyService', 'new PhantomStoreService', 'new PhantomMultipartyEconomyDecision', 'reconcileStartup', '_multipartyEconomyService.shutdown', '_phantomStoreService.shutdown().successful()', '_metrics.recordShutdownFailure()', '_state = State.FAILED') 'C2 system composition and honest store shutdown propagation'

	$tests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomMultipartyEconomySuite.java'
	foreach ($evidence in @('SEED = 22002202L', 'idx_phantom_economy_reservations_profile_operation', 'indexed-link-drift-and-idempotent-revalidation', 'updateProfileCharacter(participant.profileId(), null)', 'deleteProfile(participant.profileId())', 'State.INCONSISTENT', 'PhantomActivityState.BACKGROUND', 'Counterparty ActionLease conflict was not fail closed.', 'Background direct-trade execution was admitted', 'external-confirmation-timeout-cleanup', 'full-direct-fault-cleanup-matrix', 'economy.c2.directFaultMatrix', 'economy.c2.manufactureFaultMatrix', 'AFTER_RECIPE_INGREDIENTS', 'AFTER_PRODUCT_OR_FAILURE', 'Audit-fault terminal audit was not exactly once.', 'Private-store BUY audit was not exactly once.', 'Private-store SELL audit was not exactly once.', 'Manufacture audit was not exactly once.', 'Strict SELL-store aggregate overdraw mutated before full preflight.', 'Strict BUY-store aggregate overdraw mutated before full preflight.', 'buyExact', 'sellExact', 'Background private-store BUY execution was admitted', 'Background private-store SELL execution was admitted', 'ManufactureService.Result.STARTED', 'Background manufacture execution was admitted', 'PhantomStoreService.Result.RETRY', 'shutdown.successful()', 'six-step-durable-orchestration', 'Private-store SELL operation did not commit', 'Manufacture observer did not commit', '100000', '10000', 'TEST_DATABASE'))
	{
		Assert-True ($tests.Contains($evidence)) "Goal 022c2 test evidence is absent: $evidence"
	}
	Contains-All $tests @('External confirmation refusal was not aborted before effect.', 'Disconnected external counterparty was not aborted before effect.', 'External direct cancellation did not complete.', 'External direct shutdown retained protected work:', 'Stale external line after ordinary confirmation was not aborted before effect.') 'External direct refusal, disconnect, cancel, shutdown and stale-line matrix'
	$economyTests = Read-TargetUtf8 'test/java/org/l2jmobius/tests/phantoms/PhantomEconomySuite.java'
	Contains-All $economyTests @('for (int order = 0; order < 2; order++)', 'for (int iteration = 0; iteration < 1000; iteration++)', 'economy.participantConcurrencyIterations", 2000', 'Action-issued DISPATCHING participant drift') 'Deterministic participant terminal/boundary race and true corruption rejection'
	$verifier = Read-TargetUtf8 'tools/phantoms/verify-task-022c2.ps1'
	Contains-All $verifier @('$CausalityCommit = "988ca85e91fb0e3aa2f58dc2aaa1e4277290e1a2"', '$descendants = @(Git-Lines @(', '$descendants.Count -eq 1', '[Convert]::ToBase64String((Read-CommitBytes $CausalityCommit $packetPath))') 'Descendant-compatible terminal completion and packet parity verifier'
	$build = Read-TargetUtf8 'build.xml'
	Contains-All $build @('name="phantom.goal022c2.seed" value="22002202"', 'name="phantom-economy-checkpoint2-test"', 'name="phantom-economy-checkpoint2-affected-test"', 'name="phantom-static-verify-022c2"', 'phantom-economy-checkpoint2-test,phantom-economy-checkpoint2-affected-test', 'phantom-static-verify-022c2" description="Run Goal 022 Checkpoint 2') 'C2 Ant release routes'
	foreach ($testMode in @('economy-participant-index-c2', 'economy-offer-lifecycle', 'economy-direct-trade', 'economy-private-store-buy', 'economy-private-store-sell', 'economy-manufacture', 'economy-multiparty-restart-fault', 'economy-checkpoint2-performance'))
	{
		Assert-True ($build.Contains($testMode)) "Mandatory Goal 022c2 mode is absent: $testMode"
	}

	$contract = Read-TargetUtf8 'docs/phantoms/architecture/MULTIPARTY_ECONOMY_CONTRACT.md'
	$report = Read-TargetUtf8 'docs/phantoms/reports/022-checkpoint-2-multiparty-trade-stores-manufacture.md'
	$review = Read-TargetUtf8 'docs/phantoms/reviews/022-checkpoint-2-independent-review.md'
	Assert-True (($report -split "`r?`n").Count -le 350) "Goal 022c2 report exceeds 350 lines."
	Contains-All $contract @('DIRECT_TRADE', 'PRIVATE_STORE_BUY', 'PRIVATE_STORE_SELL', 'PLAYER_MANUFACTURE', 'OBSERVING', 'ACTIVE_REQUIRED', 'INCONSISTENT') 'C2 architecture contract'
	Contains-All $report @('IMPLEMENTED_PENDING_INDEPENDENT_REVIEW', $AcceptedCheckpoint1, $FoundationCommit, $CausalityCommit, $RequiredSubject, $RequiredSeed, $RequiredWaiver, '## Terminal release evidence', '2000', 'byte-identical') 'C2 terminal completion report'
	Contains-All $review @('Goal 022 Checkpoint 2: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`', 'Goal 022 overall: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`', $FoundationCommit, $CausalityCommit, $RequiredSubject, 'self-accept', 'Goal 023') 'C2 independent review handoff'

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
			Assert-True (($text -notmatch $mojibake) -and !$text.Contains($replacement)) "Mojibake marker found in Goal 022c2 file: $path"
			if ($path -ne 'tools/phantoms/verify-task-022c2.ps1')
			{
				Assert-True ($text -notmatch $escaped) "Escaped Cyrillic found in Goal 022c2 file: $path"
			}
		}
	}

	foreach ($path in @($production | Where-Object { $_ -match '\.java$' }))
	{
		$text = if ($newPaths.Contains($path)) { Read-TargetUtf8 $path } else { Read-AddedText $path }
		Assert-True ($text -notmatch 'new\s+Thread\s*\(|Executors\.|ScheduledFuture|\bFuture<|ThreadPool\.schedule') "New worker/task API found in Goal 022c2 production: $path"
	}

	if ($script:Mode -eq "working")
	{
		foreach ($class in @(
			'../build/bin/org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.class',
			'../build/bin/org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOfferService.class',
			'../build/bin/org/l2jmobius/gameserver/services/DirectTradeService.class',
			'../build/bin/org/l2jmobius/gameserver/services/PrivateStoreService.class',
			'../build/bin/org/l2jmobius/gameserver/services/ManufactureService.class'
		))
		{
			Assert-True (Test-Path -LiteralPath (Join-Path $script:ModuleRoot $class) -PathType Leaf) "Compiled Goal 022c2 class is absent: $class"
		}
		& git -c core.safecrlf=false diff --check $CausalityCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Working git diff --check failed."
	}
	else
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain Goal 022c2."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot 'dist/libs/GameServer.jar')
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @(
			'org/l2jmobius/gameserver/phantoms/economy/PhantomMultipartyEconomyService.class',
			'org/l2jmobius/gameserver/phantoms/economy/PhantomEconomyOfferService.class',
			'org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.class',
			'org/l2jmobius/gameserver/services/DirectTradeService.class',
			'org/l2jmobius/gameserver/services/PrivateStoreService.class',
			'org/l2jmobius/gameserver/services/ManufactureService.class'
		))
		{
			Assert-True ($jarEntries -contains $entry) "GameServer.jar lacks Goal 022c2 entry: $entry"
		}
		& git -c core.safecrlf=false diff --check $CausalityCommit $script:TargetCommit --
		Assert-True ($LASTEXITCODE -eq 0) "Committed git diff --check failed."
	}

	Write-Output 'TASK022C2_VERIFIER_OK'
	Write-Output "mode=$($script:Mode)"
	Write-Output "completion_commit=$($script:TargetCommit)"
	Write-Output "accepted_checkpoint1=$AcceptedCheckpoint1"
	Write-Output "foundation_commit=$FoundationCommit"
	Write-Output "causality_commit=$CausalityCommit"
	Write-Output "seed=$RequiredSeed"
	Write-Output "foundation_scope=$($foundationPaths.Count)"
	Write-Output "causality_scope=$($causalityPaths.Count)"
	Write-Output "terminal_scope=$($paths.Count)"
	Write-Output "terminal_production=$($production.Count)"
	Write-Output "new_production_data=$($newProductionData.Count)"
	Write-Output "sql=$($sql.Count)"
	Write-Output "policy_xml=$($policyXml.Count)"
	Write-Output "schema_sha256=$(Target-Sha256 'dist/db_installer/sql/game/phantom_reservations_checkpoint2.sql')"
}
finally
{
	Pop-Location
}
