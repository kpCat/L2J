param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RequiredParent = "21ba300fc612f9777891912f80efc633f5b6db18"
$RequiredSubject = "feat(phantoms): activate conversation responses and actions"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "20002002"

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
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required Goal 020c2 file is missing: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}
	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = [Diagnostics.ProcessStartInfo]::new()
	$start.FileName = "git"
	$start.Arguments = "show $($script:TargetCommit)`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = [IO.MemoryStream]::new()
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Goal 020c2 blob is absent: $relativePath ($errorText)"
	return $memory.ToArray()
}

function Read-TargetUtf8Strict([string] $relativePath)
{
	return [Text.UTF8Encoding]::new($false, $true).GetString((Read-TargetBytes $relativePath))
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

function Is-AllowedPath([string] $path)
{
	$exact = @(
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"build.xml",
		"docs/PHANTOM_BOTS_ROADMAP.md",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml",
		"java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java",
		"java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationPlanSink.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationStore.java",
		"java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-020c1.ps1",
		"tools/phantoms/verify-task-020c2.ps1",
		"docs/phantoms/architecture/CONVERSATION_OUTBOUND_ACTION_CONTRACT.md",
		"docs/phantoms/reports/020-conversation-outbound-actions.md",
		"docs/phantoms/reviews/020-checkpoint-1-final-review.md",
		"docs/phantoms/reviews/020-checkpoint-2-independent-review.md"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/conversation/(?:L2j)?PhantomConversationExecution[^/]+\.java$")
	{
		return $true
	}
	return $path -match "^docs/phantoms/tasks/020-checkpoint-2-conversation-outbound-actions/[^/]+$"
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$script:ModulePrefix = (Split-Path $script:ModuleRoot -Leaf) + "/"
	Assert-True ((Git-Lines @("branch", "--show-current") | Select-Object -First 1) -eq $RequiredBranch) "Goal 020c2 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($head -eq $RequiredParent)
	{
		$script:Mode = "working"
		$script:TargetCommit = $null
	}
	else
	{
		& git merge-base --is-ancestor $RequiredParent $head
		Assert-True ($LASTEXITCODE -eq 0) "Pinned Checkpoint 1 parent is not an ancestor of HEAD."
		$pathCommits = @(Git-Lines @("rev-list", "--ancestry-path", "--reverse", "$RequiredParent..$head"))
		Assert-True ($pathCommits.Count -gt 0) "Goal 020c2 implementation commit is absent."
		$script:TargetCommit = $pathCommits[0]
		Assert-True ((Git-Lines @("rev-parse", "$($script:TargetCommit)^" ) | Select-Object -First 1) -eq $RequiredParent) "Goal 020c2 is not one ordinary child of its required parent."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:TargetCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 020c2 commit subject changed."
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
				[void] $changed.Add((To-ModulePath $line.Substring(3)))
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
	Assert-True (($changedPaths.Count -gt 0) -and ($changedPaths.Count -le 60)) "Goal 020c2 total scope must contain 1..60 files."
	foreach ($path in $changedPaths)
	{
		Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 020c2 path: $path"
		Assert-True ($path -notmatch "(^|/)(Player|Party)\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)|(?:^|/)scripts?/handlers?/chat/") "Forbidden Goal 020c2 path: $path"
	}
	$production = @($changedPaths | Where-Object { ($_ -match "^java/org/l2jmobius/gameserver/") -or ($_ -match "^dist/game/(?:config|data)/") })
	Assert-True ($production.Count -le 34) "Goal 020c2 exceeds 34 changed production/data/config files."
	$newProduction = @()
	foreach ($path in $production)
	{
		$existing = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $RequiredParent, "--", ($script:ModulePrefix + $path)))
		if ($existing.Count -eq 0)
		{
			$newProduction += $path
		}
	}
	Assert-True ($newProduction.Count -le 18) "Goal 020c2 exceeds 18 new production/data files."

	foreach ($required in @(
		"build.xml",
		"dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml",
		"java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java",
		"java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionCodec.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionModel.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionStore.java",
		"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java",
		"tools/phantoms/verify-task-020c2.ps1",
		"docs/phantoms/architecture/CONVERSATION_OUTBOUND_ACTION_CONTRACT.md",
		"docs/phantoms/reports/020-conversation-outbound-actions.md",
		"docs/phantoms/reviews/020-checkpoint-1-final-review.md",
		"docs/phantoms/reviews/020-checkpoint-2-independent-review.md"
	))
	{
		Assert-True ($changed.Contains($required)) "Required Goal 020c2 artifact is absent: $required"
	}

	$manifest = Read-TargetUtf8Strict "docs/phantoms/tasks/020-checkpoint-2-conversation-outbound-actions/PACKAGE_MANIFEST.json" | ConvertFrom-Json
	Assert-True (($manifest.requiredParent -eq $RequiredParent) -and ($manifest.commitSubject -eq $RequiredSubject) -and ([string] $manifest.deterministicSeed -eq $RequiredSeed) -and $manifest.finalGoal020Checkpoint) "Goal 020c2 task manifest contract changed."
	foreach ($property in $manifest.payloadSha256.PSObject.Properties)
	{
		Assert-True ((Get-TargetSha256 $property.Name) -eq ([string] $property.Value).ToUpperInvariant()) "Goal 020c2 task package hash mismatch: $($property.Name)"
	}

	$reviewC1 = Read-TargetUtf8Strict "docs/phantoms/reviews/020-checkpoint-1-final-review.md"
	$verifierC1 = Read-TargetUtf8Strict "tools/phantoms/verify-task-020c1.ps1"
	Assert-True ($reviewC1.Contains("ACCEPT_WITH_ACTIVATION_GATE") -and $reviewC1.Contains($RequiredParent)) "Checkpoint 1 final review is not pinned."
	Assert-True ($verifierC1.Contains('$AcceptedCommit = "21ba300fc612f9777891912f80efc633f5b6db18"') -and $verifierC1.Contains("merge-base --is-ancestor") -and $verifierC1.Contains("Read-TargetBytes")) "Verifier 020c1 is not descendant-compatible."

	$conversation = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java"
	$ownerCheck = $conversation.IndexOf("_identities.getOwnerKind(delivered.recipientObjectId())")
	$ingressOffer = $conversation.IndexOf("_ingress.offer(IngressEvent.delivered", $ownerCheck)
	Assert-True (($ownerCheck -ge 0) -and ($ingressOffer -gt $ownerCheck) -and $conversation.Contains("Origin.CLIENT_CHAT") -and $conversation.Contains("forceOverflow") -and $conversation.Contains("processOnePromotion")) "PHANTOM-only ingress or bounded housekeeping is incomplete."

	$executionModel = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionModel.java"
	$executionCodec = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionCodec.java"
	$executionStore = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionStore.java"
	$executionService = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java"
	Assert-True ($executionModel.Contains('COMPONENT_TYPE = "conversation.execution"') -and $executionModel.Contains("MAX_ENTRIES = 4") -and $executionModel.Contains("MAX_RECEIPTS = 16") -and $executionModel.Contains("RECEIPT_CAPACITY_REACHED") -and $executionModel.Contains("pruneReceipts")) "conversation.execution identity, bounds or replay horizon is incomplete."
	Assert-True ($executionCodec.Contains("DECLARED_WORST_CASE_BYTES = 4076") -and $executionCodec.Contains("result.length > 4096") -and $executionCodec.Contains("has trailing bytes") -and $executionCodec.Contains("Unknown execution")) "conversation.execution codec is not fail-closed and bounded."
	Assert-True ($executionStore.Contains("mutateComponentsAtomically") -and $executionStore.Contains("PhantomConversationModel.COMPONENT_TYPE") -and $executionStore.Contains("PhantomConversationExecutionModel.COMPONENT_TYPE") -and $executionStore.Contains("mutateGoal") -and $executionStore.Contains("pageAfter")) "Atomic planner handoff, Goal mutation or component paging is incomplete."
	foreach ($phase in @("RECOVERY_PAGE", "RECOVERY_ENTRY", "DELAY_PROMOTE", "LOAD", "AUTHORIZE", "QUERY", "GOAL_SUBMIT", "GOAL_OBSERVE", "PARTY_RESPONSE", "OUTBOUND_PREPARE", "OUTBOUND_DISPATCH", "TERMINAL_STORE"))
	{
		Assert-True ($executionService.Contains($phase)) "Execution boundary is absent: $phase"
	}
	Assert-True ($executionService.Contains("operationsPerPulse") -and $executionService.Contains("remainingBudget() < 3") -and $executionService.Contains("OutboundState.DISPATCHING") -and $executionService.Contains("OutboundState.UNCERTAIN") -and $executionService.Contains("recoveryPage")) "Execution budget, paging or at-most-once policy is incomplete."

	$policyText = Read-TargetUtf8Strict "dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml"
	$policy = [xml] $policyText
	Assert-True (($policy.conversationExecutionPolicy.version -eq "1") -and ($policy.conversationExecutionPolicy.limits.executionQueue -eq "1024") -and ($policy.conversationExecutionPolicy.limits.operationsPerPulse -eq "16") -and ($policy.conversationExecutionPolicy.limits.recoveryPage -eq "256") -and ($policy.conversationExecutionPolicy.limits.entries -eq "4") -and ($policy.conversationExecutionPolicy.limits.receipts -eq "16")) "Execution policy hard bounds changed."
	$proposalKeys = @($policy.conversationExecutionPolicy.proposals.proposal | ForEach-Object { $_.key })
	foreach ($proposal in @("party.role.query", "entity.locate", "item.acquire", "item.source", "content.requirements", "party.invite", "party.leave", "party.travel", "party.accept", "party.refuse", "party.support", "party.assist", "party.regroup"))
	{
		Assert-True ($proposalKeys -contains $proposal) "Execution policy proposal is absent: $proposal"
	}
	foreach ($deferred in @("party.support", "party.assist", "party.regroup"))
	{
		Assert-True (($policy.conversationExecutionPolicy.proposals.proposal | Where-Object { $_.key -eq $deferred }).kind -eq "DEFERRED") "Deferred Goal 024 boundary changed: $deferred"
	}

	$adapter = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java"
	$goalStore = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/decision/PhantomGoalStateStore.java"
	$party = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
	Assert-True ($adapter.Contains("_knowledge.query()") -and $adapter.Contains("_topology.findNode") -and $adapter.Contains("_party.claim") -and $adapter.Contains("policy.goalType()") -and $adapter.Contains("party.generation") -and $adapter.Contains("party.instance")) "Canonical query or Goal evidence dependencies are incomplete."
	Assert-True ($goalStore.Contains("componentMutation") -and $party.Contains("pendingInvitation") -and $party.Contains("respondToPending") -and $party.Contains("goalMatchesPlan") -and $party.Contains("PartyInvitationService.RespondOutcome.REFUSED")) "Atomic Goal or exact Party response seam is incomplete."
	Assert-True ($adapter.Contains("ChatHandler.getInstance().getHandler") -and $adapter.Contains("openGeneratedDispatch") -and $adapter.Contains("tryAcquireAction") -and $adapter.Contains("ChatType.WHISPER") -and $adapter.Contains("ChatType.PARTY") -and $adapter.Contains("ChatType.GENERAL") -and $adapter.Contains("ChatType.TRADE")) "Generated current-handler dispatch is incomplete."
	Assert-True ($adapter -notmatch "\.addItem\s*\(|\.destroyItem\s*\(|\.teleToLocation\s*\(|\.setParty\s*\(|\.doCast\s*\(|\.doAttack\s*\(|new\s+GameClient|clientpackets") "Conversation adapter contains a direct gameplay or packet-handler bypass."

	$chat = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/model/chat/ChatObservationService.java"
	$creatureSay = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/network/serverpackets/CreatureSay.java"
	Assert-True ($chat.Contains("PHANTOM_GENERATED") -and $chat.Contains("openGeneratedDispatch") -and $chat.Contains("generatedDeliveries") -and $chat.Contains("clientDeliveries") -and $creatureSay.Contains("capturePacket")) "Generated origin or delivery audit seam is incomplete."
	Assert-True ($conversation.Contains("dispatch.origin() != Origin.CLIENT_CHAT")) "Generated chat can re-enter conversation ingress."
	$conversationSources = ($production | Where-Object { $_ -match "^java/org/l2jmobius/gameserver/phantoms/conversation/" } | ForEach-Object { Read-TargetUtf8Strict $_ }) -join "`n"
	Assert-True ($conversationSources -notmatch "\b(?:ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.|new\s+Thread\s*\(") "Conversation execution owns a worker, executor, Future or task."

	$system = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
	$plannerIndex = $system.IndexOf("new PhantomConversationService")
	$executionIndex = $system.IndexOf("new PhantomConversationExecutionService")
	$installIndex = $system.IndexOf("conversationExecutionSignal.install")
	Assert-True (($plannerIndex -ge 0) -and ($executionIndex -gt $plannerIndex) -and ($installIndex -gt $executionIndex) -and $system.Contains("PhantomCompositeSchedulerControlPort") -and $system.Contains("_conversationExecutionService.finishStop")) "PhantomSystem execution composition or lifecycle ordering is incomplete."

	$launcher = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
	$build = Read-TargetUtf8Strict "build.xml"
	foreach ($focusedMode in @("conversation-managed-ingress", "conversation-execution-catalog-codec", "conversation-handoff-durability", "conversation-query-execution", "conversation-party-actions", "conversation-outbound-chat", "conversation-restart-idempotency", "conversation-execution-lifecycle-performance"))
	{
		Assert-True ($launcher.Contains("case `"$focusedMode`"") -and $build.Contains("`"$focusedMode`"")) "Focused Goal 020c2 mode is not wired: $focusedMode"
	}
	Assert-True ($build.Contains('name="phantom.goal020c2.seed" value="20002002"') -and $build.Contains('name="phantom-conversation-checkpoint2-test"') -and $build.Contains('name="phantom-conversation-checkpoint2-affected-test"') -and $build.Contains('name="phantom-static-verify-020c2"')) "Goal 020c2 seed, aggregates or verifier target is absent."
	$executionTests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomConversationExecutionSuite.java"
	$integrationTests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java"
	Assert-True ($executionTests.Contains("10_000") -and $executionTests.Contains("maximumOperationsPerPulse") -and $executionTests.Contains("DISPATCHING") -and $executionTests.Contains("UNCERTAIN") -and $executionTests.Contains("replay-horizon") -and $integrationTests.Contains("100_000") -and $integrationTests.Contains("PHANTOM_GENERATED")) "Mandatory execution, ingress, restart or lifecycle evidence is incomplete."

	$contract = Read-TargetUtf8Strict "docs/phantoms/architecture/CONVERSATION_OUTBOUND_ACTION_CONTRACT.md"
	$report = Read-TargetUtf8Strict "docs/phantoms/reports/020-conversation-outbound-actions.md"
	$review = Read-TargetUtf8Strict "docs/phantoms/reviews/020-checkpoint-2-independent-review.md"
	Assert-True ($contract.Contains("conversation.execution") -and $contract.Contains("DISPATCHING") -and $contract.Contains("PHANTOM_GENERATED") -and $contract.Contains("Goal 024")) "Goal 020 final architecture contract is incomplete."
	Assert-True (($report -split "`r?`n").Count -le 240) "Goal 020c2 report exceeds 240 lines."
	$statusRecorded = $report.Contains("IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -or (($script:Mode -eq "working") -and $report.Contains("PENDING_FINAL_GATES"))
	Assert-True ($statusRecorded -and $report.Contains($RequiredParent) -and $report.Contains($RequiredSubject) -and $review.Contains("PENDING_INDEPENDENT_REVIEW")) "Goal 020c2 report or independent review handoff is incomplete."

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B), @(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A), @(0x0420, 0x00A4), @(0x0420, 0x045A),
		@(0x0420, 0x0408), @(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5), @(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026), @(0x0421, 0x040F),
		@(0x0421, 0x20AC), @(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A), @(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B), @(0x0421, 0x2026), @(0x0421, 0x2020)
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
		& git merge-base --is-ancestor $script:TargetCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain Goal 020c2."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot "dist/libs/GameServer.jar")
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		foreach ($entry in @(
			"org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionModel.class",
			"org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionCodec.class",
			"org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.class",
			"org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.class"
		))
		{
			Assert-True ($jarEntries -contains $entry) "GameServer.jar lacks Goal 020c2 entry: $entry"
		}
		Assert-True ($jarEntries -notcontains "data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml") "Execution datapack must remain outside GameServer.jar."
	}
	else
	{
		$compiled = Join-Path (Split-Path $script:ModuleRoot -Parent) "build/bin/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.class"
		Assert-True (Test-Path -LiteralPath $compiled -PathType Leaf) "Compiled conversation execution service is absent."
	}

	if ($script:Mode -eq "working")
	{
		& git diff --check $RequiredParent --
	}
	else
	{
		& git diff --check $RequiredParent $script:TargetCommit --
	}
	Assert-True ($LASTEXITCODE -eq 0) "git diff --check failed."

	Write-Output "TASK020C2_VERIFIER_OK"
	Write-Output "mode=$($script:Mode)"
	Write-Output "implementation_commit=$(if ($script:Mode -eq 'accepted') { $script:TargetCommit } else { 'WORKING' })"
	Write-Output "accepted_parent=$RequiredParent"
	Write-Output "policy_sha256=$(Get-TargetSha256 'dist/game/data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml')"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
}
finally
{
	Pop-Location
}
