param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RequiredParent = "384b521f2cd29f4162c9aca9116eb0ff40cbd681"
$ImplementationCommit = "e7ba469e63caa6dee113278087258fab005a435a"
$ImplementationSubject = "feat(phantoms): add conversation observation and planning"
$CompletionSubject = "fix(phantoms): complete conversation planning safety"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "20002001"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Git-Lines([string[]] $arguments)
{
	$result = & git @arguments
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

function Read-TargetBytes([string] $relativePath)
{
	if ($script:Mode -eq "working")
	{
		$path = Join-Path $script:ModuleRoot $relativePath
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required Goal 020c1 file is missing: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}

	return Read-CommitBytes $script:TargetCommit $relativePath
}

function Read-CommitBytes([string] $commit, [string] $relativePath)
{
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = [Diagnostics.ProcessStartInfo]::new()
	$start.FileName = "git"
	$start.Arguments = "show $commit`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = [IO.MemoryStream]::new()
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Goal 020c1 blob is absent at $commit`: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetUtf8Strict([string] $relativePath)
{
	$encoding = [Text.UTF8Encoding]::new($false, $true)
	return $encoding.GetString((Read-TargetBytes $relativePath))
}

function Get-TargetSha256([string] $relativePath)
{
	$sha256 = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha256.ComputeHash((Read-TargetBytes $relativePath)))).Replace("-", "")
	}
	finally
	{
		$sha256.Dispose()
	}
}

function Get-CommitSha256([string] $commit, [string] $relativePath)
{
	$sha256 = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha256.ComputeHash((Read-CommitBytes $commit $relativePath)))).Replace("-", "")
	}
	finally
	{
		$sha256.Dispose()
	}
}

function Is-AllowedPath([string] $path)
{
	$exact = @(
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"build.xml",
		"dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv",
		"dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml",
		"java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/Say2.java",
		"java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializationService.java",
		"java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationContextPort.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticGrounding.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticModel.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialEventSink.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialReceiptLedger.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStore.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomActivationGateSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomChatObservationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSemanticSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSocialPartyIntegrationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSocialSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSocialTestDoubles.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-019.ps1",
		"tools/phantoms/verify-task-018.ps1",
		"tools/phantoms/verify-task-020c1.ps1",
		"docs/phantoms/architecture/CONVERSATION_OBSERVATION_PLANNING_CONTRACT.md",
		"docs/phantoms/reports/020-checkpoint-1-conversation-observation-planning.md",
		"docs/phantoms/reviews/019-russian-semantic-understanding-review.md",
		"docs/phantoms/reviews/020-checkpoint-1-independent-review.md"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversation[^/]+\.java$")
	{
		return $true
	}
	return $path -match "^docs/phantoms/tasks/020-checkpoint-1-conversation-observation-planning/[^/]+$"
}

function Is-CompletionAllowedPath([string] $path)
{
	return @(
		"build.xml",
		"java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/Say2.java",
		"java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationModel.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationStateCodec.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationCatalog.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomChatObservationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomServerShutdownHandoffSuite.java",
		"tools/phantoms/verify-task-020c1.ps1",
		"docs/phantoms/tasks/020-checkpoint-1-conversation-observation-planning/ARCHITECTURE.md",
		"docs/phantoms/reports/020-checkpoint-1-conversation-observation-planning.md",
		"docs/phantoms/reviews/020-checkpoint-1-independent-review.md"
	) -contains $path
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$moduleName = Split-Path $script:ModuleRoot -Leaf
	$script:ModulePrefix = $moduleName + "/"
	$branch = (Git-Lines @("branch", "--show-current") | Select-Object -First 1)
	Assert-True ($branch -eq $RequiredBranch) "Goal 020c1 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	$implementationParent = (Git-Lines @("rev-parse", "$ImplementationCommit^") | Select-Object -First 1)
	$implementationActualSubject = (Git-Lines @("show", "-s", "--format=%s", $ImplementationCommit) | Select-Object -First 1)
	Assert-True (($implementationParent -eq $RequiredParent) -and ($implementationActualSubject -eq $ImplementationSubject)) "Pinned Goal 020c1 implementation commit graph or subject changed."
	& git merge-base --is-ancestor $ImplementationCommit $head
	Assert-True ($LASTEXITCODE -eq 0) "Pinned Goal 020c1 implementation commit is not an ancestor of HEAD."
	$script:Mode = "working"
	$script:TargetCommit = ""
	$script:CompletionCommit = ""
	if ($head -ne $ImplementationCommit)
	{
		$candidates = @()
		foreach ($commit in Git-Lines @("rev-list", "--ancestry-path", "$ImplementationCommit..$head"))
		{
			$parent = (Git-Lines @("rev-parse", "$commit^") | Select-Object -First 1)
			$subject = (Git-Lines @("show", "-s", "--format=%s", $commit) | Select-Object -First 1)
			if (($parent -eq $ImplementationCommit) -and ($subject -eq $CompletionSubject))
			{
				$candidates += $commit
			}
		}
		Assert-True ($candidates.Count -eq 1) "Expected one unique ordinary Goal 020c1 completion child."
		$script:CompletionCommit = $candidates[0]
		$script:TargetCommit = $script:CompletionCommit
		& git merge-base --is-ancestor $script:CompletionCommit $head
		Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 020c1 completion child is not an ancestor of HEAD."
		$script:Mode = "accepted"
	}

	$changed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		foreach ($line in Git-Lines @("diff", "--name-only", $RequiredParent, "--"))
		{
			[void] $changed.Add((To-ModulePath $line))
		}
		foreach ($line in Git-Lines @("-c", "core.quotepath=false", "status", "--porcelain=v1", "--untracked-files=all", "--", "."))
		{
			if ($line.Length -ge 4)
			{
				$path = $line.Substring(3)
				if ($path.Contains(" -> "))
				{
					$path = $path.Split(@(" -> "), [StringSplitOptions]::None)[1]
				}
				[void] $changed.Add((To-ModulePath $path))
			}
		}
	}
	else
	{
		foreach ($line in Git-Lines @("diff", "--name-only", $RequiredParent, $script:TargetCommit, "--"))
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
	$changedPaths = @($changed | Sort-Object)
	Assert-True (($changedPaths.Count -gt 0) -and ($changedPaths.Count -le 54)) "Goal 020c1 total scope must contain 1..54 files."
	foreach ($path in $changedPaths)
	{
		Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 020c1 path: $path"
		Assert-True ($path -notmatch "(^|/)(Player|Party)\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)|(^|/)handlers?/.*chat") "Forbidden Goal 020c1 path: $path"
	}
	foreach ($required in @(
		"build.xml",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv",
		"java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/Say2.java",
		"java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialReceiptLedger.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomActivationGateSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomChatObservationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationSuite.java",
		"tools/phantoms/verify-task-020c1.ps1",
		"docs/phantoms/architecture/CONVERSATION_OBSERVATION_PLANNING_CONTRACT.md",
		"docs/phantoms/reports/020-checkpoint-1-conversation-observation-planning.md",
		"docs/phantoms/reviews/019-russian-semantic-understanding-review.md"
	))
	{
		Assert-True ($changed.Contains($required)) "Required Goal 020c1 artifact is absent: $required"
	}

	$completionChanged = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
	if ($script:Mode -eq "working")
	{
		foreach ($line in Git-Lines @("diff", "--name-only", $ImplementationCommit, "--"))
		{
			[void] $completionChanged.Add((To-ModulePath $line))
		}
		foreach ($line in Git-Lines @("-c", "core.quotepath=false", "status", "--porcelain=v1", "--untracked-files=all", "--", "."))
		{
			if ($line.Length -ge 4)
			{
				$path = $line.Substring(3)
				if ($path.Contains(" -> "))
				{
					$path = $path.Split(@(" -> "), [StringSplitOptions]::None)[1]
				}
				[void] $completionChanged.Add((To-ModulePath $path))
			}
		}
	}
	else
	{
		foreach ($line in Git-Lines @("diff", "--name-only", $ImplementationCommit, $script:TargetCommit, "--"))
		{
			[void] $completionChanged.Add((To-ModulePath $line))
		}
	}
	$completionPaths = @($completionChanged | Sort-Object)
	Assert-True (($completionPaths.Count -gt 0) -and ($completionPaths.Count -le 16)) "Goal 020c1 completion scope must contain 1..16 files."
	Assert-True ($completionChanged.Contains("docs/phantoms/reviews/020-checkpoint-1-independent-review.md")) "Goal 020c1 independent review handoff is absent."
	foreach ($path in $completionPaths)
	{
		Assert-True (Is-CompletionAllowedPath $path) "Out-of-scope Goal 020c1 completion path: $path"
	}
	$completionProduction = @($completionPaths | Where-Object { ($_ -match "^java/org/l2jmobius/gameserver/") -or ($_ -match "^dist/game/(config|data)/") })
	Assert-True ($completionProduction.Count -le 8) "Goal 020c1 completion exceeds eight production files."
	foreach ($path in $completionProduction)
	{
		$implementationEntry = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $ImplementationCommit, "--", ($script:ModulePrefix + $path)))
		Assert-True ($implementationEntry.Count -eq 1) "Goal 020c1 completion adds a new production/data file: $path"
	}

	$production = @($changedPaths | Where-Object { ($_ -match "^java/org/l2jmobius/gameserver/") -or ($_ -match "^dist/game/(config|data)/") })
	Assert-True ($production.Count -le 30) "Goal 020c1 exceeds 30 changed production/data/config files."
	$newProduction = @()
	foreach ($path in $production)
	{
		$parentEntries = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $RequiredParent, "--", ($script:ModulePrefix + $path)))
		if ($parentEntries.Count -eq 0)
		{
			$newProduction += $path
		}
	}
	Assert-True ($newProduction.Count -le 16) "Goal 020c1 exceeds 16 new production/data files."

	$manifest = Read-TargetUtf8Strict "docs/phantoms/tasks/020-checkpoint-1-conversation-observation-planning/PACKAGE_MANIFEST.json" | ConvertFrom-Json
	Assert-True ($manifest.requiredParent -eq $RequiredParent) "Goal 020c1 task package parent mismatch."
	Assert-True ($manifest.commitSubject -eq $ImplementationSubject) "Goal 020c1 task package subject mismatch."
	Assert-True ([string] $manifest.deterministicSeed -eq $RequiredSeed) "Goal 020c1 task package seed mismatch."
	foreach ($property in $manifest.payloadSha256.PSObject.Properties)
	{
		$actualHash = if ($property.Name -eq "docs/phantoms/tasks/020-checkpoint-1-conversation-observation-planning/ARCHITECTURE.md") { Get-CommitSha256 $ImplementationCommit $property.Name } else { Get-TargetSha256 $property.Name }
		Assert-True ($actualHash -eq ([string] $property.Value).ToUpperInvariant()) "Goal 020c1 task package hash mismatch: $($property.Name)"
	}

	$review019 = Read-TargetUtf8Strict "docs/phantoms/reviews/019-russian-semantic-understanding-review.md"
	Assert-True ($review019.Contains("ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS") -and $review019.Contains($RequiredParent)) "Goal 019 final review record is incomplete."
	$verifier019 = Read-TargetUtf8Strict "tools/phantoms/verify-task-019.ps1"
	Assert-True ($verifier019.Contains('$AcceptedCommit = "384b521f2cd29f4162c9aca9116eb0ff40cbd681"') -and $verifier019.Contains("Read-TargetBytes") -and $verifier019.Contains("merge-base --is-ancestor") -and $verifier019.Contains("Goal 020 path leaked")) "Goal 019 verifier is not historical/descendant-compatible."
	$verifier018 = Read-TargetUtf8Strict "tools/phantoms/verify-task-018.ps1"
	Assert-True ($verifier018.Contains("merge-base --is-ancestor `$script:AcceptedCommit `$remote") -and $verifier018.Contains("does not contain the accepted Goal 018 commit")) "Goal 018 remote check is not descendant-compatible."
	$master = Read-TargetUtf8Strict "PHANTOM_DEVELOPMENT_MASTER_PLAN.md"
	Assert-True ($master -match '018[\s\S]+?Status: `ACCEPT_WITH_ACTIVATION_GATE`' -and $master -match '019[\s\S]+?Status: `ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`' -and $master -match '020[\s\S]+?Status: `CHECKPOINT_1_IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`') "Master-plan checkpoint status is incomplete."
	Assert-True ($master -match '020[\s\S]+?action/outbound[\s\S]+?`NOT_STARTED`' -and $master -match '021[\s\S]+?Status: `NOT_STARTED`') "A later Goal 020/021 slice was started."

	$chat = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java"
	$say2 = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/network/clientpackets/Say2.java"
	$creatureSay = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java"
	Assert-True ($chat -notmatch "org\.l2jmobius\.gameserver\.phantoms" -and $chat -match "ThreadLocal<DispatchScope>" -and $chat -match "A chat delivery observer is already registered" -and $chat -match "onDispatchClosed" -and $chat -match "validDescriptorFields" -and $chat -match "callbackFailures") "Generic chat seam dependency, close boundary or bounded registration contract is wrong."
	$filterIndex = $say2.IndexOf("checkText()")
	$scopeIndex = $say2.IndexOf("openClientDispatch")
	$handlerIndex = $say2.IndexOf("handler.onChat", $scopeIndex)
	$closeIndex = $say2.IndexOf("observationScope.close()", $handlerIndex)
	Assert-True (($filterIndex -ge 0) -and ($scopeIndex -gt $filterIndex) -and ($handlerIndex -gt $scopeIndex) -and ($closeIndex -gt $handlerIndex) -and $say2.Contains("finally")) "Say2 final-filtered dispatch scope ordering is wrong."
	$captureIndex = $creatureSay.IndexOf("captureClientPacket")
	$snoopIndex = $creatureSay.IndexOf("player.broadcastSnoop")
	$publishIndex = $creatureSay.IndexOf("publishDelivered", $snoopIndex)
	Assert-True (($captureIndex -ge 0) -and ($snoopIndex -gt $captureIndex) -and ($publishIndex -gt $snoopIndex)) "CreatureSay actual recipient callback ordering is wrong."

	$conversationPaths = @($changedPaths | Where-Object { $_ -match "^java/org/l2jmobius/gameserver/phantoms/conversation/" })
	$conversationSources = ($conversationPaths | ForEach-Object { Read-TargetUtf8Strict $_ }) -join "`n"
	Assert-True ($conversationSources -notmatch "new\s+CreatureSay|\.sendPacket\s*\(|\.broadcastPacket\s*\(|PartyInvitationService|PhantomGoalService|\.addItem\s*\(|\.destroyItem\s*\(|\.moveTo\s*\(|\.attack\s*\(") "Conversation production sends chat or executes a forbidden action."
	Assert-True ($conversationSources -notmatch "\b(?:ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.|new\s+Thread\s*\(") "Conversation production owns a worker, executor, Future or task."
	$planSink = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationPlanSink.java"
	$service = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java"
	$model = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationModel.java"
	$codec = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationStateCodec.java"
	Assert-True ($planSink -match "observerOnly" -and $planSink -match "final class ObserverOnly" -and $planSink -notmatch "CreatureSay|sendPacket") "Conversation plan sink is not observer-only."
	foreach ($phase in @("COLLECTING", "RESOLVING_OBSERVERS", "ELECTING", "LOADING_STATE", "BUILDING_CONTEXT", "UNDERSTANDING", "READING_SOCIAL", "PERSISTING", "PUBLISHING", "DONE", "FAILED"))
	{
		Assert-True ($service.Contains($phase)) "Conversation resumable phase is absent: $phase"
	}
	Assert-True ($service -match "AtomicBoolean _pulseOwner" -and $service -match "PriorityQueue<DueEntry>" -and $service -match "_dueMembership" -and $service -notmatch "_pulseMonitor|_batches\.entrySet\(\)\.stream") "Conversation pulse ownership or incremental due index is incomplete."
	Assert-True ($service -match "PersistenceStatus" -and $service -match "AUTHORITY_STALE" -and $service -match "token\.conflictReload" -and $service -match "onDispatchClosed" -and $service -match "case WHISPER" -and $service -match "case PARTY" -and $service -match "exactAddress" -and $service -match "resolveFragment") "Conversation aggregation, typed persistence, election or clarification continuation is incomplete."
	Assert-True ($service -match "conversationCatalogHash" -or $service -match "_catalog\.hash\(\)") "Conversation catalog hash is absent from deterministic planning."
	Assert-True ($model -match 'COMPONENT_TYPE = "conversation\.state"' -and $model -match "MAX_SESSIONS = 8" -and $model -match "MAX_RECENT_HASHES = 8" -and $model -match "MAX_PENDING_SLOTS = 4") "Conversation state identity or bounds changed."
	Assert-True ($model -match "orderedHashes" -and $model -notmatch "recentObservationHashes\s*=\s*sortedHashes|recent\.sort") "Recent observation hashes are not preserved oldest-to-newest."
	Assert-True ($codec -match "DECLARED_WORST_CASE_BYTES = 3456" -and $codec -match "result\.length > 4096" -and $codec -match "has trailing bytes" -and $codec -match "is invalid") "Conversation codec is not compact and fail-closed."

	$repository = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/profile/PhantomProfileRepository.java"
	$socialStore = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStore.java"
	$receipts = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialReceiptLedger.java"
	$social = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java"
	$party = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
	Assert-True ($repository -match "mutateComponentsAtomically" -and $repository -match "mutate Phantom profile components atomically" -and $socialStore -match "mutateComponentsAtomically") "Social state and receipts are not one atomic component mutation."
	Assert-True ($receipts -match 'COMPONENT_TYPE = "social\.receipts"' -and $receipts -match "MAX_RECEIPTS = 96" -and $receipts -match "RECEIPT_BYTES = 42" -and $receipts -match "APPLIED" -and $receipts -match "STALE") "Social receipt identity, bounds or statuses changed."
	Assert-True ($social -match "ReceiptStatus\.STALE" -and $social -match "happenedMinute" -and $social -match "logicalMinute" -and $social -match "mutate") "Stale/out-of-order social causality is incomplete."
	Assert-True ($party -match "exactObservedJoin" -and $party -match "CANONICAL_PENDING" -and $party -match '"party\.member\.joined"') "First exact JOIN emission gate is incomplete."

	$semanticModel = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticModel.java"
	$semanticPack = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java"
	$semanticService = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.java"
	Assert-True ($semanticModel -match "positive decimal number" -and $semanticModel -match "validNamespace" -and $semanticModel -match "FragmentResult") "Strict semantic identity/slot contract is incomplete."
	Assert-True ($semanticPack -match "clarify\.complexity" -and $semanticPack -match "must start with a literal" -and $semanticPack -match "slots must be unique, literal-separated and bounded to four") "Strict semantic pattern contract is incomplete."
	Assert-True ($semanticService -match "CandidateBudget" -and $semanticService -match "incomplete\(\)" -and $semanticService -match "clarify\.complexity" -and $semanticService -match "_startClaimed" -and $semanticService -match "_operationClaims") "Strict semantic budget/start-drain contract is incomplete."

	$catalogText = Read-TargetUtf8Strict "dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml"
	$catalog = [xml] $catalogText
	$limits = $catalog.conversationCatalog.limits
	Assert-True (($limits.ingressQueue -eq "1024") -and ($limits.openBatches -eq "256") -and ($limits.observersPerMessage -eq "32") -and ($limits.operationsPerPulse -eq "32") -and ($limits.sessionsPerProfile -eq "8") -and ($limits.pendingSlots -eq "4") -and ($limits.statePayload -eq "4096") -and ($limits.aggregationPulses -eq "1")) "Conversation catalog limits changed."
	$corpusText = Read-TargetUtf8Strict "dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"
	$corpusRows = @(($corpusText -split "`r?`n") | Where-Object { $_ -and !$_.StartsWith("#") -and !$_.StartsWith("case_id`t") })
	Assert-True ($corpusRows.Count -eq 128) "Conversation corpus must contain exactly 128 deterministic rows."
	$catalogHash = Get-TargetSha256 "dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml"
	$corpusHash = Get-TargetSha256 "dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"

	$system = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
	$conversationStart = $system.IndexOf("new PhantomConversationService")
	$partyStart = $system.IndexOf("new PhantomPartyCoordinator")
	$conversationDrain = $system.IndexOf("_conversationService.finishStop()")
	$partyDrain = $system.IndexOf("_partyCoordinator.finishStop()", $conversationDrain)
	Assert-True (($conversationStart -gt $partyStart) -and ($conversationDrain -ge 0) -and ($partyDrain -gt $conversationDrain) -and $system.Contains("PhantomConversationService.Snapshot.inactive()")) "PhantomSystem conversation startup/shutdown/snapshot ownership is incomplete."

	$launcher = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
	$build = Read-TargetUtf8Strict "build.xml"
	foreach ($focusedMode in @("social-activation", "semantic-activation", "chat-observation", "conversation-catalog-codec", "conversation-understanding", "conversation-social-style", "conversation-chat-integration", "conversation-lifecycle-performance"))
	{
		Assert-True ($launcher.Contains("case `"$focusedMode`"") -and $build.Contains("`"$focusedMode`"")) "Focused Goal 020c1 mode is not wired: $focusedMode"
	}
	Assert-True ($build.Contains('name="phantom.goal020c1.seed" value="20002001"') -and $build.Contains('name="phantom-conversation-checkpoint1-test"') -and $build.Contains('name="phantom-conversation-checkpoint1-affected-test"') -and $build.Contains('name="phantom-static-verify-020c1"')) "Goal 020c1 seed, aggregates or verifier target are absent."
	$activationTests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomActivationGateSuite.java"
	$integrationTests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java"
	$socialPartyTests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomSocialPartyIntegrationSuite.java"
	Assert-True ($activationTests -match "PhantomProfileRepository\.open" -and $activationTests -match "production-authority" -and $socialPartyTests -match "PhantomTestDatabaseGuard\.TARGET_DATABASE" -and $socialPartyTests -match "First canonical JOIN") "Activation test does not cover real DB/authority/JOIN gates."
	Assert-True ($integrationTests -match "DISPATCH_CLOSED" -and $integrationTests -match "32-recipient" -and $integrationTests -match "256-closed-batches" -and $integrationTests -match "AUTHORITY_STALE" -and $integrationTests -match "one-operation-budget" -and $integrationTests -match "shutdown-during-every-operational-phase") "Conversation completion integration/performance coverage is incomplete."

	$contract = Read-TargetUtf8Strict "docs/phantoms/architecture/CONVERSATION_OBSERVATION_PLANNING_CONTRACT.md"
	$report = Read-TargetUtf8Strict "docs/phantoms/reports/020-checkpoint-1-conversation-observation-planning.md"
	$review020 = Read-TargetUtf8Strict "docs/phantoms/reviews/020-checkpoint-1-independent-review.md"
	Assert-True ($contract -match "delivery thread" -and $contract -match "observer-only" -and $contract -match "4096") "Conversation architecture contract is incomplete."
	Assert-True ($review020 -match "PENDING_INDEPENDENT_REVIEW" -and $review020.Contains($ImplementationCommit) -and $review020.Contains($CompletionSubject)) "Goal 020c1 independent review handoff is incomplete."
	Assert-True (($report -split "`r?`n").Count -le 220) "Goal 020c1 report exceeds 220 lines."
	Assert-True ($report.Contains($RequiredParent) -and $report.Contains($ImplementationCommit) -and $report.Contains($ImplementationSubject) -and $report.Contains($CompletionSubject)) "Goal 020c1 report graph evidence is incomplete."
	$completion = "OK"
	if ($script:Mode -eq "accepted")
	{
		if ($report -match 'Status: `PARTIAL`')
		{
			$completion = "PARTIAL"
		}
		else
		{
			Assert-True ($report -match 'Status: `SUCCESS`') "Accepted Goal 020c1 report records neither SUCCESS nor PARTIAL."
		}
	}

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B), @(0x0420, 0x2022),
		@(0x0420, 0x040E), @(0x0420, 0x203A), @(0x0420, 0x00A4), @(0x0420, 0x045A),
		@(0x0420, 0x0408), @(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5),
		@(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026), @(0x0421, 0x040F),
		@(0x0421, 0x20AC), @(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A),
		@(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026),
		@(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char]$_[0]) + ([string][char]$_[1])) }) -join "|"
	$replacementCharacter = [string][char]0xFFFD
	$escapedCyrillic = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $changedPaths)
	{
		$text = Read-TargetUtf8Strict $path
		Assert-True (($text -notmatch $mojibake) -and -not $text.Contains($replacementCharacter)) "Mojibake marker found in changed file: $path"
		Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in changed file: $path"
	}

	if ($script:Mode -eq "accepted")
	{
		$remote = (Git-Lines @("rev-parse", "origin/feature/phantom-world") | Select-Object -First 1)
		& git merge-base --is-ancestor $script:CompletionCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain accepted Goal 020c1."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot "dist/libs/GameServer.jar")
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @(
			"org/l2jmobius/gameserver/model/chat/ChatObservationService.class",
			"org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.class",
			"org/l2jmobius/gameserver/phantoms/social/PhantomSocialReceiptLedger.class"
		))
		{
			Assert-True ($jarEntries -contains $entry) "GameServer.jar lacks Goal 020c1 entry: $entry"
		}
		$conversationJava = Git-Lines @("-C", $repositoryRoot, "ls-tree", "-r", "--name-only", $script:TargetCommit, "--", ($script:ModulePrefix + "java/org/l2jmobius/gameserver/phantoms/conversation"))
		foreach ($sourcePath in @($conversationJava | Where-Object { $_.EndsWith(".java", [StringComparison]::Ordinal) }))
		{
			$classEntry = (To-ModulePath $sourcePath).Substring("java/".Length).Replace(".java", ".class")
			Assert-True ($jarEntries -contains $classEntry) "GameServer.jar lacks compiled conversation class: $classEntry"
		}
		foreach ($dataPath in @(
			"dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml",
			"dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"
		))
		{
			$tracked = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $script:TargetCommit, "--", ($script:ModulePrefix + $dataPath)))
			Assert-True ($tracked.Count -eq 1) "Canonical tracked conversation datapack is absent: $dataPath"
			$jarDataEntry = $dataPath.Substring("dist/game/".Length)
			Assert-True ($jarEntries -notcontains $jarDataEntry) "Conversation datapack must remain outside GameServer.jar: $jarDataEntry"
		}
	}
	else
	{
		$compiledService = Join-Path (Split-Path $script:ModuleRoot -Parent) "build/bin/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.class"
		Assert-True (Test-Path -LiteralPath $compiledService -PathType Leaf) "Compiled conversation service class is absent."
	}

	if ($script:Mode -eq "accepted")
	{
		& git diff --check $RequiredParent $script:TargetCommit --
	}
	else
	{
		& git diff --check $RequiredParent --
	}
	Assert-True ($LASTEXITCODE -eq 0) "git diff --check failed."

	Write-Output "TASK020C1_VERIFIER_$completion"
	Write-Output "mode=$($script:Mode)"
	Write-Output "implementation_commit=$ImplementationCommit"
	Write-Output "completion_commit=$(if ($script:Mode -eq 'accepted') { $script:CompletionCommit } else { 'WORKING' })"
	Write-Output "accepted_parent=$RequiredParent"
	Write-Output "catalog_sha256=$catalogHash"
	Write-Output "corpus_sha256=$corpusHash"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
	Write-Output "completion_scope=$($completionPaths.Count)"
	Write-Output "completion_production=$($completionProduction.Count)"
}
finally
{
	Pop-Location
}
