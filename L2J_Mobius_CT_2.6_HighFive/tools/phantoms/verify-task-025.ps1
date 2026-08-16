$ErrorActionPreference = "Stop"

$acceptedParent = "922f72c0d422904dcbdc6215a5cc1167a1bb84fb"
$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$repoRoot = (Resolve-Path (Join-Path $moduleRoot "..")).Path
$moduleName = Split-Path $moduleRoot -Leaf

function Fail([string] $message)
{
	throw "Goal 025 static verification failed: $message"
}

function Require-File([string] $relative)
{
	$path = Join-Path $moduleRoot $relative
	if (!(Test-Path -LiteralPath $path -PathType Leaf))
	{
		Fail "required file missing: $relative"
	}
	return [IO.File]::ReadAllText($path)
}

function Require-Text([string] $text, [string] $needle, [string] $evidence)
{
	if (!$text.Contains($needle))
	{
		Fail "required evidence missing: $evidence"
	}
}

function Reject-Pattern([string] $text, [string] $pattern, [string] $evidence)
{
	if ([regex]::IsMatch($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase))
	{
		Fail "forbidden evidence found: $evidence"
	}
}

$pvpFiles = Get-ChildItem -LiteralPath (Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/pvp") -Filter "*.java" -File
if ($pvpFiles.Count -lt 6)
{
	Fail "bounded PvP package is incomplete"
}
$pvpText = ($pvpFiles | Sort-Object FullName | ForEach-Object { [IO.File]::ReadAllText($_.FullName) }) -join [Environment]::NewLine
$goal025ProductionFiles = @(
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomPvpCombatRequest.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/PhantomPvpSkillSafety.java",
	"java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java",
	"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionStore.java",
	"java/org/l2jmobius/gameserver/phantoms/conversation/PhantomPvpConversationBridge.java",
	"java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java",
	"java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java",
	"java/org/l2jmobius/gameserver/phantoms/navigation/PhantomPvpRetreatCoordinator.java",
	"java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java",
	"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
	"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java",
	"java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java"
)
$phantomText = ($goal025ProductionFiles | ForEach-Object { [IO.File]::ReadAllText((Join-Path $moduleRoot $_)) }) -join [Environment]::NewLine

foreach ($forbidden in @(
	@("World\s*\.\s*getPlayers\s*\(", "global World player scan"),
	@("\.\s*setCurrentHp\s*\(", "direct HP mutation"),
	@("\.\s*setCurrentCp\s*\(", "direct CP mutation"),
	@("\.\s*setKarma\s*\(", "direct karma mutation"),
	@("\.\s*updatePvPStatus\s*\(", "direct PvP flag mutation"),
	@("\.\s*increasePvpKills\s*\(", "direct PvP kill mutation"),
	@("\.\s*increasePkKillsAndKarma\s*\(", "direct PK or karma mutation"),
	@("\.\s*onDieDropItem\s*\(", "direct death drop invocation"),
	@("new\s+AttackRequest\s*\(", "ClientPacket attack construction"),
	@("new\s+RequestMagicSkillUse\s*\(", "ClientPacket skill construction")
))
{
	Reject-Pattern $phantomText $forbidden[0] $forbidden[1]
}

foreach ($forbidden in @(
	@("ChatHandler", "direct chat handler use from PvP package"),
	@("ServerPacket", "direct packet use from PvP package"),
	@("ClientPacket", "client packet use from PvP package"),
	@("new\s+Thread\b", "new PvP thread"),
	@("Executor(Service)?\b", "new PvP executor"),
	@("ScheduledFuture\b", "new PvP future"),
	@("Timer(Task)?\b", "new PvP timer")
))
{
	Reject-Pattern $pvpText $forbidden[0] $forbidden[1]
}

$model = Require-File "java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpModel.java"
Require-Text $model "ACTUAL_ATTACK(false)" "ACTUAL_ATTACK causal source"
Require-Text $model "FARMING_ESCALATION(true)" "FARMING_ESCALATION causal source"
Require-Text $model "PARTY_DEFENSE(false)" "PARTY_DEFENSE causal source"
Require-Text $model "REVENGE(true)" "REVENGE causal source"
Reject-Pattern $model "(?m)^\s+(VISIBLE|PVP_FLAG|KARMA|LOW_HP)[,(]" "non-causal aggression source"

$combat = Require-File "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java"
Require-Text $combat "startPvpSession(PhantomPvpCombatRequest request)" "separate PvP combat API"
Require-Text $combat "resolvePvp(actor, request.mode(), lease" "separate PvP capability resolution"
Require-Text $combat "target.validFor(actor, _policy.maximumAcquisitionDistance())" "canonical bounded PvP target gate"
Require-Text $combat "session._actorLease.attackPvp" "explicit physical PvP backend path"
Require-Text $combat "session._actorLease.castPvp" "explicit skill PvP backend path"

$backend = Require-File "java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"
Require-Text $backend "target.onForcedAttack(_player);" "canonical Player forced attack seam"
Require-Text $backend "_player.useMagic(skill, forceUse, false)" "canonical Player skill seam"
Require-Text $backend "ItemHandler.getInstance().getHandler(item.getEtcItem())" "registered item handler lookup"
Require-Text $backend ""ItemSkills".equals(handler.getClass().getSimpleName())" "registered ItemSkills ownership"
Require-Text $backend "(itemId != 5591) && (itemId != 5592)" "exact CP stock IDs"
Require-Text $backend "(skills[0].getSkillId() != 2166)" "source-derived CP skill ID"

$skillSafety = Require-File "java/org/l2jmobius/gameserver/phantoms/combat/PhantomPvpSkillSafety.java"
Require-Text $skillSafety "TargetType.ONE" "one-target PvP safety"
Reject-Pattern $skillSafety "AROUND|AREA|AURA|MULTIFACE|MULTIFACE_AURA" "unsafe AoE PvP target"

$context = Require-File "java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpContext.java"
Require-Text $context "_party.pvpProtection" "Goal017 party authority join"
Require-Text $context "_farming.pvpEscalation" "Goal024 farming authority join"
Require-Text $context "_social.revenge" "Goal018 revenge authority join"
Require-Text $context "_combat.observePvp" "Goal012 bounded observation"
Reject-Pattern $context "findAll|listAll|World\." "global profile or world scan"

$service = Require-File "java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java"
Require-Text $service "implements PhantomSchedulerControlPort, PhantomMaterializationLifecyclePort" "shared scheduler and lifecycle ownership"
Require-Text $service "warningReceiptId()" "persisted warning receipt gate"
$policyJava = Require-File "java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpPolicy.java"
Require-Text $policyJava "maxProactiveEngagementsPerPair()" "per-pair engagement budget"
Require-Text $service "_retreat.start" "navigation-owned retreat"
Require-Text $service "_conversation.submit" "Goal020 outbound handoff"
Require-Text $service "_social.record" "Goal018 social handoff"

$policy = Require-File "dist/game/data/phantoms/pvp/pvp-policy-v1.xml"
Require-Text $policy 'version="1"' "versioned PvP policy"
Require-Text $policy 'maxProactiveEngagementsPerPair="1"' "bounded proactive budget"
Require-Text $policy 'cpPotionThresholdPercent="30"' "CP potion threshold"

$system = Require-File "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
Require-Text $system 'data/phantoms/pvp/pvp-policy-v1.xml' "production PvP policy wiring"
Require-Text $system "new PhantomCompositeSchedulerControlPort(java.util.List.of(_populationManager, _partyCoordinator, _conversationService, _conversationExecutionService, _pvpService))" "shared scheduler wiring"
if ($system.IndexOf("if (!_settings.enabled())") -gt $system.IndexOf("data/phantoms/pvp/pvp-policy-v1.xml"))
{
	Fail "disabled gate no longer precedes PvP construction"
}

$combatBackendContract = Require-File "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java"
Require-Text $combatBackendContract "record PvpLocalSupportSnapshot" "bounded local support contract"
Require-Text $combatBackendContract "(observedPlayers > limit)" "local support snapshot cap validation"

$serverTests = Require-File "test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java"
Require-Text $serverTests "testPvpPhysical()" "real Player forced-attack test"
Require-Text $serverTests "testPvpMagic()" "real Player useMagic test"
Require-Text $serverTests "testPvpCpAndConsequences()" "real CP and consequence test"
Require-Text $serverTests "testPvpReputationOutcomes()" "real PvP/PK/karma test"
Require-Text $serverTests "_combat.observePvp(_profile.profileId(), List.of(target.getObjectId()), 16, 1)" "bounded local risk server test"
Require-Text $serverTests "localSupport().observedPlayers() <= 1" "local support cap assertion"

$goal025Tests = Require-File "test/java/org/l2jmobius/tests/phantoms/PhantomPvpSuite.java"
Require-Text $goal025Tests "25002501L" "Goal025 deterministic seed"
Require-Text $goal025Tests "performance" "Goal025 performance smoke"
$launcher = Require-File "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
Require-Text $launcher '"pvp-policy"' "Goal025 aggregate launcher route"
Require-Text $launcher '"pvp-combat-server-integration"' "Goal025 server integration launcher route"
$build = Require-File "build.xml"
Require-Text $build 'name="phantom-pvp-goal025-test"' "single Goal025 aggregate target"
Require-Text $build 'name="phantom-pvp-combat-integration-test"' "focused PvP combat integration target"

$architecture = Require-File "docs/phantoms/architecture/PVP_THREAT_ESCALATION_CONTRACT.md"
Require-Text $architecture "ACTUAL_ATTACK" "documented causal source matrix"
Require-Text $architecture "target.onForcedAttack(actor)" "documented canonical physical path"
Require-Text $architecture "Player.useMagic" "documented canonical skill path"
$report = Require-File "docs/phantoms/reports/025-pvp-threat-escalation.md"
Require-Text $report "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW" "Goal025 report verdict"
Require-Text $report $acceptedParent "Goal025 accepted parent evidence"
$review = Require-File "docs/phantoms/reviews/025-independent-review.md"
Require-Text $review "PENDING_INDEPENDENT_REVIEW" "independent review handoff"
$master = Require-File "PHANTOM_DEVELOPMENT_MASTER_PLAN.md"
Require-Text $master "Goal 025" "master Goal025 status"
Require-Text $master "IMPLEMENTED_PENDING_INDEPENDENT_REVIEW" "master pending review status"
$roadmap = Require-File "docs/PHANTOM_BOTS_ROADMAP.md"
Require-Text $roadmap "Goal 024: ACCEPT" "roadmap accepted Goal024"
Require-Text $roadmap "Goal 024A: ACCEPT" "roadmap accepted Goal024A"
Require-Text $roadmap "Goal 025: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW" "roadmap pending Goal025"
Require-Text $roadmap "Goal 026+: NOT_STARTED" "roadmap future goal boundary"

$expectedPayloadHashes = [ordered]@{
	"docs/phantoms/tasks/025-pvp-threat-escalation/ACCEPTANCE.md" = "6EF87168F4C24E7C62C7C10D4503CF242D0319C0DC8E58B51FD255B0C14C3CBC"
	"docs/phantoms/tasks/025-pvp-threat-escalation/ARCHITECTURE.md" = "2BFBF755FCD0C05A23541A8737DA86492B19749BA7027C2654EC34245707584E"
	"docs/phantoms/tasks/025-pvp-threat-escalation/CODEX_LAUNCHER.txt" = "C7F8F225DF996D7E0A5EE0B9A9C57FF48B5D7E7991CEF52B5963E0A4AFC60375"
	"docs/phantoms/tasks/025-pvp-threat-escalation/CONTEXT.md" = "134601AAB7D68856191E11695B74323989B76FBD06823153504587065F77391A"
	"docs/phantoms/tasks/025-pvp-threat-escalation/PRIOR_INDEPENDENT_REVIEW.md" = "6621693A2E98B7B351D77D01DB44B4CF8738631DFFD5CBF593E6F707DF09B33F"
	"docs/phantoms/tasks/025-pvp-threat-escalation/TASK.md" = "DF1AE73B73E99F6189FFBFDBA7D62F6BFC22BA70595B647B57B8FBC9FDD36E07"
	"docs/phantoms/tasks/025-pvp-threat-escalation/TEST_CASES.md" = "C2A4160B7603C3065ABC02119BC10A0D9843EBB8889D103E1BD8DB35C5309A64"
}
foreach ($entry in $expectedPayloadHashes.GetEnumerator())
{
	$payloadPath = Join-Path $moduleRoot $entry.Key
	if (!(Test-Path -LiteralPath $payloadPath -PathType Leaf))
	{
		Fail "task package payload missing: $($entry.Key)"
	}
	$actualHash = (Get-FileHash -LiteralPath $payloadPath -Algorithm SHA256).Hash
	if ($actualHash -ne $entry.Value)
	{
		Fail "task package payload changed: $($entry.Key)"
	}
}
$manifestText = Require-File "docs/phantoms/tasks/025-pvp-threat-escalation/PACKAGE_MANIFEST.json"
$manifest = $manifestText | ConvertFrom-Json
if (($manifest.requiredParent -ne $acceptedParent) -or ($manifest.branch -ne "feature/phantom-world") -or ($manifest.deterministicSeed -ne 25002501) -or ($manifest.priorIndependentReview.goal024A -ne "ACCEPT") -or ($manifest.priorIndependentReview.goal024 -ne "ACCEPT") -or ($manifest.priorIndependentReview.acceptedBaseline -ne $acceptedParent))
{
	Fail "task package manifest authority changed"
}
Push-Location $repoRoot
try
{
	$changed = @(& git diff --name-only $acceptedParent --)
	if ($LASTEXITCODE -ne 0)
	{
		Fail "could not inventory baseline diff"
	}
	$changed += @(& git ls-files --others --exclude-standard --)
	if ($LASTEXITCODE -ne 0)
	{
		Fail "could not inventory untracked files"
	}
}
finally
{
	Pop-Location
}

$changed = $changed | Where-Object { $_ } | Sort-Object -Unique
$expectedChanged = @(
	"$moduleName/PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
	"$moduleName/build.xml",
	"$moduleName/dist/game/data/phantoms/pvp/pvp-policy-v1.xml",
	"$moduleName/dist/game/data/phantoms/social/high-five-social-v1.xml",
	"$moduleName/docs/PHANTOM_BOTS_ROADMAP.md",
	"$moduleName/docs/phantoms/architecture/PVP_THREAT_ESCALATION_CONTRACT.md",
	"$moduleName/docs/phantoms/reports/025-pvp-threat-escalation.md",
	"$moduleName/docs/phantoms/reviews/025-independent-review.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/ACCEPTANCE.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/ARCHITECTURE.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/CODEX_LAUNCHER.txt",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/CONTEXT.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/PACKAGE_MANIFEST.json",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/PRIOR_INDEPENDENT_REVIEW.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/TASK.md",
	"$moduleName/docs/phantoms/tasks/025-pvp-threat-escalation/TEST_CASES.md",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatCapabilityResolver.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatSession.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomPvpCombatRequest.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/combat/PhantomPvpSkillSafety.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionService.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationExecutionStore.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/conversation/PhantomPvpConversationBridge.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomNavigationService.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/navigation/PhantomPvpRetreatCoordinator.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyBackend.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpContext.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpModel.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpPersistencePort.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpPolicy.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpStateCodec.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpStore.java",
	"$moduleName/java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java",
	"$moduleName/test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java",
	"$moduleName/test/java/org/l2jmobius/tests/phantoms/PhantomPvpSuite.java",
	"$moduleName/test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"$moduleName/tools/phantoms/verify-task-025.ps1"
) | Sort-Object -Unique
$unexpected = @($changed | Where-Object { $_ -notin $expectedChanged })
$missing = @($expectedChanged | Where-Object { $_ -notin $changed })
if (($unexpected.Count -ne 0) -or ($missing.Count -ne 0))
{
	Fail "exact changed-file allowlist mismatch; unexpected=$($unexpected -join ','); missing=$($missing -join ',')"
}

foreach ($path in $changed)
{
	if ($path -match "^L2J_Mobius_(?!CT_2\.6_HighFive/)")
	{
		Fail "other chronicle changed: $path"
	}
	if (($path -match "(^|/)\.l2j(/|$)") -or ($path -match "^$([regex]::Escape($moduleName))/dist/game/config/") -or ($path -match "Database.*\.ini$"))
	{
		Fail "forbidden staging, production config or DB path changed: $path"
	}
}

Write-Output "PHANTOM_STATIC_VERIFY_025_OK"
