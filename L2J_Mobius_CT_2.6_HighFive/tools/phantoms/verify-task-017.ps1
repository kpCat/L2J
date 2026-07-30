param(
	[string] $ExpectedParent = "d731bf91b5f75cf733175bf57faf19c0354085c0"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

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

function Read-Utf8Strict([string] $relativePath)
{
	$path = Join-Path $script:Root $relativePath
	Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required file is absent: $relativePath"
	$bytes = [System.IO.File]::ReadAllBytes($path)
	$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
	return $utf8.GetString($bytes)
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $Root
try
{
	$branch = (Git-Lines @("branch", "--show-current") | Select-Object -First 1)
	Assert-True ($branch -eq "feature/phantom-world") "Goal 017 must remain on feature/phantom-world."

	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	if ($head -ne $ExpectedParent)
	{
		$parent = (Git-Lines @("rev-parse", "HEAD^") | Select-Object -First 1)
		Assert-True ($parent -eq $ExpectedParent) "HEAD is not the single Goal 017 completion commit above required parent."
		$subject = (Git-Lines @("show", "-s", "--format=%s", "HEAD") | Select-Object -First 1)
		Assert-True ($subject -eq "fix(phantoms): complete party lifecycle safety") "Goal 017 completion commit subject is wrong."
	}

	$modulePrefix = (Split-Path $Root -Leaf) + "/"
	$trackedChanges = @(Git-Lines @("diff", "--name-only", $ExpectedParent, "--") | ForEach-Object { $_ -replace ("^" + [regex]::Escape($modulePrefix)), "" })
	$untrackedChanges = @(Git-Lines @("ls-files", "--others", "--exclude-standard") | ForEach-Object { $_ -replace ("^" + [regex]::Escape($modulePrefix)), "" })
	$changedPaths = @($trackedChanges + $untrackedChanges | Sort-Object -Unique)
	Assert-True ($changedPaths.Count -le 30) "Goal 017 completion exceeds the 30-file total scope."

	$allowedPatterns = @(
		"^java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery\.java$",
		"^java/org/l2jmobius/gameserver/model/groups/PartyInvitationService\.java$",
		"^java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty\.java$",
		"^java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty\.java$",
		"^java/org/l2jmobius/gameserver/phantoms/party/.+\.java$",
		"^java/org/l2jmobius/gameserver/phantoms/semantic/PhantomPartySemanticActs\.java$",
		"^java/org/l2jmobius/gameserver/phantoms/combat/(PhantomCombatBackend|PhantomCombatActorLease|PhantomCombatService|L2jCombatBackend|PhantomPartySupportAction)\.java$",
		"^java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService\.java$",
		"^java/org/l2jmobius/gameserver/phantoms/PhantomSystem\.java$",
		"^test/java/org/l2jmobius/tests/phantoms/PhantomParty.+\.java$",
		"^test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher\.java$",
		"^tools/phantoms/verify-task-017\.ps1$",
		"^docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT\.md$",
		"^docs/phantoms/reports/017-party-coordination-kernel\.md$",
		"^docs/phantoms/tasks/017-party-lifecycle-safety-completion/.+$"
	)
	foreach ($path in $changedPaths)
	{
		Assert-True (@($allowedPatterns | Where-Object { $path -match $_ }).Count -gt 0) "Out-of-scope Goal 017 path: $path"
		Assert-True ($path -notmatch "(^|/)Player\.java$|(^|/)Party\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)") "Forbidden Goal 017 path: $path"
	}

	$production = @($changedPaths | Where-Object { $_ -match "^java/" })
	Assert-True ($production.Count -le 18) "Goal 017 completion exceeds 18 production files."
	$newProductionTracked = @(Git-Lines @("diff", "--name-only", "--diff-filter=A", $ExpectedParent, "--", "java") | ForEach-Object { $_ -replace ("^" + [regex]::Escape($modulePrefix)), "" })
	$newProduction = @($newProductionTracked + ($untrackedChanges | Where-Object { $_ -match "^java/" }) | Sort-Object -Unique)
	Assert-True ($newProduction.Count -le 3) "Goal 017 completion exceeds three new production files."
	Assert-True ($newProduction -contains "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyParticipationPort.java") "Party participation port is missing."
	Assert-True ($newProduction -contains "java/org/l2jmobius/gameserver/phantoms/combat/PhantomPartySupportAction.java") "Typed support action is missing."

	$invitationDelivery = Read-Utf8Strict "java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery.java"
	$invitationService = Read-Utf8Strict "java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java"
	Assert-True ($invitationDelivery -match "PreparationOutcome" -and $invitationDelivery -match "TerminalOutcome" -and $invitationDelivery -match "managedRequester" -and $invitationDelivery -match "managedInvitee") "Managed invitation callback contract is incomplete."
	foreach ($outcome in @("ACCEPTED", "REFUSED", "DISABLED", "EXPIRED", "CANCELLED", "DELIVERY_REJECTED", "REVALIDATION_FAILED", "REQUESTER_UNAVAILABLE"))
	{
		Assert-True ($invitationDelivery -match "\b$outcome\b") "Terminal outcome is absent: $outcome"
	}
	Assert-True ($invitationService -match "_pendingByRequester" -and $invitationService -match "_pendingByInvitee" -and $invitationService -match "findByParticipant") "Bidirectional invitation indexes/expiry are incomplete."
	Assert-True ($invitationService -match "_pendingByInvitee\.remove\(" -and $invitationService -match "_pendingByRequester\.remove\(" -and $invitationService -match "_terminal\.compareAndSet\(false, true\)") "Exact once terminal cleanup is incomplete."
	Assert-True ($invitationService.IndexOf("deliveryPort.prepare") -lt $invitationService.IndexOf("pending.publishAndDeliver")) "Managed preparation does not precede publication."
	Assert-True ($invitationService -match "ownedPending" -and $invitationService -match "party\.invite\.delivery_closed") "DeliveryRegistration close does not drain owned invitations."
	Assert-True ($invitationService -notmatch "new\s+GameClient|import\s+org\.l2jmobius\.gameserver\.phantoms") "Canonical invitation service depends on Phantom transport."

	$coordinator = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
	Assert-True ($coordinator -match "prepare\(PartyInvitation" -and $coordinator -match "CANONICAL_PENDING" -and $coordinator -match "sameInvitation" -and $coordinator -match "processTerminal") "Exact durable invitation saga is incomplete."
	Assert-True ($coordinator -match "IDEMPOTENT" -and $coordinator -match "leave\(" -and $coordinator -match "expelTarget\(" -and $coordinator -match "transferLeaderTarget\(" -and $coordinator -match "travel\(") "Idempotent lifecycle commands are incomplete."
	Assert-True ($coordinator -match "_operationClaims" -and $coordinator -match "beginTerminalOperation" -and $coordinator -match "pendingInvitations\(\)") "Operation/shutdown claim drain is incomplete."
	Assert-True ($coordinator -match "_claimsByGroup" -and $coordinator -match "_dueGroups" -and $coordinator -match "_terminalEvents" -and $coordinator -match "_tacticalReleases") "Bounded coordinator indexes/queues are incomplete."
	$onPulseStart = $coordinator.IndexOf("public void onPulse()")
	$onPulseEnd = $coordinator.IndexOf("public void beginStop()", $onPulseStart)
	$onPulse = $coordinator.Substring($onPulseStart, $onPulseEnd - $onPulseStart)
	Assert-True ($onPulse -notmatch "_groups\.values\(\)\.stream|_claims\.values\(\)\.stream|_tacticalActions\.values\(\)") "Coordinator pulse contains a full managed-state scan."
	Assert-True ($onPulse -match "_operationBudget" -and $onPulse -match "_lastPulseExamined") "Coordinator pulse does not account its exact budget."

	$background = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java"
	$system = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
	Assert-True (($background -split "party\.materialized_only").Count -ge 5) "Background party gate is not rechecked at all mutation boundaries."
	Assert-True ($system -match "PhantomPartyParticipationPort\.bridge\(\)" -and $system -match "partyParticipation\.install\(_partyCoordinator\)") "Production party participation bridge wiring is incomplete."

	$matcher = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRoleMatcher.java"
	$partyTests = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java"
	Assert-True ($matcher -match "MatchSolution" -and $matcher -match "requiredFilled" -and $matcher -match "totalScore" -and $matcher -match "optionalFilled") "Deterministic maximum role matching is incomplete."
	Assert-True ($partyTests -match "maximum-matching-beats-greedy-counterexample") "Required global matching counterexample is absent."

	$supportAction = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/combat/PhantomPartySupportAction.java"
	$l2jCombat = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"
	$partyBackend = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java"
	$tactics = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java"
	Assert-True ($supportAction -match "capabilityKey" -and $supportAction -match "variantKey" -and $supportAction -match "targetScope" -and $supportAction -match "targetObjectId" -and $supportAction -match "SelectedSkill") "Typed exact-target support action is incomplete."
	Assert-True ($partyBackend -match "capabilities\(MemberRef actor, int exactTargetObjectId\)" -and $partyBackend -match "target\.required") "Party backend does not preserve exact target capability truth."
	Assert-True ($tactics -match "_backend\.capabilities\(actor, target\.characterObjectId\(\)\)" -and $tactics -notmatch "use-all|useAll") "Tactics does not query exact target capabilities."
	Assert-True ($l2jCombat -match "capabilityKey\(\)" -and $l2jCombat -match "variantKey\(\)" -and $l2jCombat -match "targetScope\(\)" -and $l2jCombat -match "checkCondition") "L2J support execution does not revalidate the typed catalog action."

	$route = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java"
	Assert-True ($route -match "route\.topologyHash\(\)\.equals\(currentTopologyHash\)" -and $route -match "snapshot == null" -and $route -match "snapshot\.attacking\(\)" -and $route -match "RouteStatus\.REGROUPING") "Route authority or missing-member guards are incomplete."
	Assert-True ($partyTests -match "missing-member-cannot-advance-or-arrive" -and $partyTests -match "coordinator-pulse-count-never-exceeds-budget") "Dynamic route/pulse regression coverage is absent."

	$integration = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomPartyServerIntegrationSuite.java"
	$launcher = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
	Assert-True ($integration -match "PhantomHeadlessPlayerTestEnvironment" -and $integration -match "PhantomMaterializationService" -and $integration -match "PartyInvitationService" -and $integration -match "phantom_profile_components" -and $integration -match "AskJoinParty") "Real DB/materialized Party/ordinary-client integration is incomplete."
	Assert-True ($integration -match "bilateral-expiry" -and $integration -match "both-managed-identities" -and $integration -notmatch "Files\.readString|source\(context") "Party integration still relies on source-string assertions."
	Assert-True ($launcher -match 'case "party-server-integration" -> new PhantomPartyServerIntegrationSuite\(\)') "Party server integration launcher still selects the source-string suite."

	$architecture = Read-Utf8Strict "docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md"
	$report = Read-Utf8Strict "docs/phantoms/reports/017-party-coordination-kernel.md"
	Assert-True (($report -split "`r?`n").Count -le 190) "Goal 017 report exceeds 190 lines."
	Assert-True ($architecture -match "maximum matching" -and $architecture -match "party\.materialized_only" -and $architecture -match "Missing/cross-instance") "Party architecture contract is stale."
	Assert-True ($report -match "d731bf91b5f75cf733175bf57faf19c0354085c0" -and $report -match "650" -and $report -match "l2jmobiush5_phantom_test") "Goal 017 completion report evidence is incomplete."

	$mojibakePairs = @(
		@(0x0420, 0x045F), @(0x0420, 0x045C), @(0x0420, 0x045B),
		@(0x0420, 0x2022), @(0x0420, 0x040E), @(0x0420, 0x203A),
		@(0x0420, 0x00A4), @(0x0420, 0x045A), @(0x0420, 0x0408),
		@(0x0420, 0x2122), @(0x0420, 0x0491), @(0x0420, 0x00B5),
		@(0x0420, 0x00B0), @(0x0420, 0x00BB), @(0x0420, 0x2026),
		@(0x0420, 0x2022), @(0x0421, 0x040F), @(0x0421, 0x20AC),
		@(0x0421, 0x0402), @(0x0421, 0x2039), @(0x0421, 0x040A),
		@(0x0421, 0x201A), @(0x0421, 0x0453), @(0x0421, 0x040B),
		@(0x0421, 0x2026), @(0x0421, 0x2020)
	)
	$mojibake = ($mojibakePairs | ForEach-Object { [regex]::Escape(([string][char]$_[0]) + ([string][char]$_[1])) }) -join "|"
	$replacementCharacter = [string][char]0xFFFD
	$escapedCyrillic = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
	foreach ($path in $changedPaths)
	{
		$text = Read-Utf8Strict $path
		Assert-True (($text -notmatch $mojibake) -and -not $text.Contains($replacementCharacter)) "Mojibake marker found in changed file: $path"
		Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in changed file: $path"
	}

	& git diff --check $ExpectedParent --
	Assert-True ($LASTEXITCODE -eq 0) "git diff --check failed."
	Write-Output "TASK017_VERIFIER_OK"
	Write-Output "parent=$ExpectedParent"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
}
finally
{
	Pop-Location
}
