param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$requiredParent = "57caea2e5b5597c9a06b87cb8e868f227c4aa88e"
$requiredBranch = "feature/phantom-world"
$requiredSubject = "feat(phantoms): add party coordination kernel"
$seed = "17001701"
$moduleRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$repositoryRoot = (Resolve-Path ((& git -C $moduleRoot rev-parse --show-toplevel).Trim())).Path
$repositoryPrefix = $repositoryRoot.TrimEnd("\", "/") + "\"

function Assert-True([bool] $condition, [string] $message)
{
	if (-not $condition)
	{
		throw $message
	}
}

function Read-Utf8Strict([string] $relativePath)
{
	$path = Join-Path $moduleRoot $relativePath
	Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required file is missing: $relativePath"
	$encoding = [Text.UTF8Encoding]::new($false, $true)
	return $encoding.GetString([IO.File]::ReadAllBytes($path))
}

function Get-Sha256([string] $relativePath)
{
	$sha256 = [Security.Cryptography.SHA256]::Create()
	try
	{
		return ([BitConverter]::ToString($sha256.ComputeHash([IO.File]::ReadAllBytes((Join-Path $moduleRoot $relativePath))))).Replace("-", "")
	}
	finally
	{
		$sha256.Dispose()
	}
}

function Invoke-Git([string[]] $Arguments)
{
	$startInfo = [Diagnostics.ProcessStartInfo]::new()
	$startInfo.FileName = "git"
	$startInfo.UseShellExecute = $false
	$startInfo.RedirectStandardOutput = $true
	$startInfo.RedirectStandardError = $true
	$quoted = @("-C", $repositoryRoot) + $Arguments
	$startInfo.Arguments = ($quoted | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }) -join " "
	$process = [Diagnostics.Process]::new()
	$process.StartInfo = $startInfo
	[void] $process.Start()
	$outputRead = $process.StandardOutput.ReadToEndAsync()
	$errorRead = $process.StandardError.ReadToEndAsync()
	$process.WaitForExit()
	$outputText = $outputRead.GetAwaiter().GetResult()
	$errorText = $errorRead.GetAwaiter().GetResult()
	Assert-True ($process.ExitCode -eq 0) "git $($Arguments -join ' ') failed: $errorText"
	if ($outputText.Length -eq 0)
	{
		return
	}
	return @($outputText.TrimEnd("`r", "`n") -split "`r?`n")
}

function To-ModulePath([string] $repositoryPath)
{
	$normalized = $repositoryPath.Trim().Trim('"').Replace("\", "/")
	if ($normalized.StartsWith($script:moduleRelative + "/", [StringComparison]::Ordinal))
	{
		return $normalized.Substring($script:moduleRelative.Length + 1)
	}
	return $normalized
}

function Is-AllowedPath([string] $path)
{
	$exact = @(
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"build.xml",
		"dist/game/config/Custom/PhantomPlayers.ini",
		"dist/game/data/phantoms/party/high-five-party-roles-v1.xml",
		"docs/PHANTOM_BOTS_ROADMAP.md",
		"docs/phantoms/architecture/PARTY_COORDINATION_CONTRACT.md",
		"docs/phantoms/reports/017-party-coordination-kernel.md",
		"docs/phantoms/reviews/016-population-manager-safety-review.md",
		"java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
		"java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery.java",
		"java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java",
		"java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/activity/PhantomCompositeSchedulerControlPort.java",
		"java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java",
		"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatActorLease.java",
		"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatBackend.java",
		"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatMetrics.java",
		"java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-014a.ps1",
		"tools/phantoms/verify-task-016.ps1",
		"tools/phantoms/verify-task-017.ps1"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/(?:party|semantic)/.+\.java$")
	{
		return $true
	}
	if ($path -match "^docs/phantoms/tasks/017-party-coordination-kernel/[^/]+$")
	{
		return $true
	}
	return $false
}

Assert-True ($moduleRoot.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) "Module root is outside repository root."
$script:moduleRelative = $moduleRoot.Substring($repositoryPrefix.Length).Replace("\", "/")
$head = ([string] (Invoke-Git @("rev-parse", "HEAD") | Select-Object -First 1)).Trim()
$branch = ([string] (Invoke-Git @("branch", "--show-current") | Select-Object -First 1)).Trim()
Assert-True ($branch -eq $requiredBranch) "Unexpected branch: $branch"

$mode = ""
$completionCommit = ""
$changed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if ($head -eq $requiredParent)
{
	$mode = "working-implementation"
	foreach ($line in Invoke-Git @("diff", "--name-only", $requiredParent, "--", $script:moduleRelative))
	{
		if ($line.Trim().Length -gt 0)
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
	foreach ($line in Invoke-Git @("-c", "core.quotepath=false", "status", "--porcelain=v1", "--untracked-files=all", "--", $script:moduleRelative))
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
	$candidates = [Collections.Generic.List[string]]::new()
	foreach ($line in Invoke-Git @("log", "--format=%H`t%P`t%s", "--ancestry-path", "$requiredParent..$head"))
	{
		$parts = $line -split "`t", 3
		if (($parts.Count -eq 3) -and ($parts[1] -eq $requiredParent) -and ($parts[2] -eq $requiredSubject))
		{
			$candidates.Add($parts[0])
		}
	}
	Assert-True ($candidates.Count -eq 1) "Expected one unique ordinary Goal 017 direct child."
	$completionCommit = $candidates[0]
	[void] (Invoke-Git @("merge-base", "--is-ancestor", $completionCommit, $head))
	if ($head -eq $completionCommit)
	{
		$mode = "completion-commit"
	}
	else
	{
		$mode = "completion-ancestor"
	}
	foreach ($line in Invoke-Git @("diff", "--name-only", $requiredParent, $completionCommit, "--", $script:moduleRelative))
	{
		if ($line.Trim().Length -gt 0)
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
}

Assert-True ($changed.Count -gt 0) "Goal 017 scope is empty."
foreach ($path in $changed)
{
	Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 017 path: $path"
	Assert-True (-not $path.StartsWith("../", [StringComparison]::Ordinal)) "Path escaped the High Five module: $path"
	Assert-True (-not $path.Contains("017A") -and -not $path.Contains("017B")) "Artificial Goal 017 suffix found: $path"
}
foreach ($forbidden in @(
	"java/org/l2jmobius/gameserver/model/actor/Player.java",
	"java/org/l2jmobius/gameserver/phantoms/schema",
	"docs/phantoms/tasks/018-",
	"docs/phantoms/tasks/019-",
	"docs/phantoms/tasks/020-",
	"docs/phantoms/tasks/023-",
	"docs/phantoms/tasks/025-"
))
{
	Assert-True (-not ($changed | Where-Object { $_.StartsWith($forbidden, [StringComparison]::Ordinal) })) "Forbidden Goal 017 path changed: $forbidden"
}

$manifestPath = "docs/phantoms/tasks/017-party-coordination-kernel/PACKAGE_MANIFEST.json"
$manifest = Read-Utf8Strict $manifestPath | ConvertFrom-Json
Assert-True ($manifest.requiredParent -eq $requiredParent) "Task package parent mismatch."
Assert-True ($manifest.commitSubject -eq $requiredSubject) "Task package subject mismatch."
Assert-True ([string] $manifest.deterministicSeed -eq $seed) "Task package seed mismatch."
foreach ($property in $manifest.payloadSha256.PSObject.Properties)
{
	$actual = Get-Sha256 $property.Name
	Assert-True ($actual -eq ([string] $property.Value).ToUpperInvariant()) "Task package hash mismatch: $($property.Name)"
}

$review016 = Read-Utf8Strict "docs/phantoms/reviews/016-population-manager-safety-review.md"
Assert-True ($review016 -match '(?m)^`ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS`\s*$') "Goal 016 independent verdict is missing."
Assert-True ($review016 -match "F016-ADMISSION-SCALE" -and $review016 -match "F016-HISTOGRAM-TRUTH") "Goal 016 future contracts are incomplete."
$historical016 = @(& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $moduleRoot "tools/phantoms/verify-task-016.ps1") 2>&1)
Assert-True ($LASTEXITCODE -eq 0) "Historical Goal 016 verifier failed: $($historical016 -join [Environment]::NewLine)"
Assert-True ($historical016 -contains "TASK016_VERIFIER_OK") "Historical Goal 016 verifier token is missing."

$requestJoin = Read-Utf8Strict "java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java"
$requestAnswer = Read-Utf8Strict "java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java"
$invitation = Read-Utf8Strict "java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java"
$delivery = Read-Utf8Strict "java/org/l2jmobius/gameserver/model/groups/PartyInvitationDelivery.java"
Assert-True ($requestJoin -match "PartyInvitationService\.getInstance\(\)\.invite\(") "RequestJoinParty does not delegate to the canonical invitation service."
Assert-True ($requestAnswer -match "PartyInvitationService\.getInstance\(\)" -and $requestAnswer -match "service\.respond\(") "RequestAnswerJoinParty does not delegate to the canonical invitation service."
foreach ($handler in @($requestJoin, $requestAnswer))
{
	Assert-True ($handler -notmatch "new Party\(|\.joinParty\(|setPendingInvitation\(") "Packet handler retains duplicate party mutation logic."
}
Assert-True ($invitation -match "class PartyInvitationService" -and $delivery -match "interface PartyInvitationDelivery") "Canonical invitation transport boundary is missing."
Assert-True ($invitation -notmatch "phantoms|GameClient|public synchronized|private synchronized") "Canonical invitation service depends on Phantom/client transport or uses a method-wide monitor."
Assert-True ($invitation -match "_pendingByRequester" -and $invitation -match "_pendingByInvitee" -and $invitation -match "detachExact") "Invitation identity reservation or stale-response protection is incomplete."

$partyRoot = Join-Path $moduleRoot "java/org/l2jmobius/gameserver/phantoms/party"
$partyFiles = Get-ChildItem -LiteralPath $partyRoot -Recurse -Filter "*.java" -File
$partyText = ($partyFiles | ForEach-Object {
	$relative = $_.FullName.Substring($moduleRoot.Length + 1).Replace("\", "/")
	Read-Utf8Strict $relative
}) -join "`n"
Assert-True ($partyText -notmatch "RequestJoinParty|RequestAnswerJoinParty|\.runImpl\(|GameClient|new Party\(|\.joinParty\(|setPendingInvitation\(|removePartyMember\(|\.setLeader\(") "Phantom party code invokes handlers/client transport or mutates Party membership directly."
Assert-True ($partyText -notmatch "l2jmobiush5(?!_phantom_test)" -and $partyText -cnotmatch "\b(?:Rift|Matchmaking|Clan|LLM)\b|personality") "Party kernel contains an excluded subsystem or production database name."

$model = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/model/PhantomPartyModel.java"
$codec = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyStateCodec.java"
$store = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyStore.java"
$coordinator = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
Assert-True ($model -match 'COMPONENT_TYPE\s*=\s*"party\.state"' -and $model -match "MAX_ROSTER\s*=\s*9" -and $model -match "MAX_ROUTE_WAYPOINTS\s*=\s*64") "Bounded durable party model is incomplete."
foreach ($phase in @("PREPARED", "CANONICAL_PENDING", "CANONICAL_OBSERVED", "COMMITTED", "ABORTED"))
{
	Assert-True ($model -match ("\b" + $phase + "\b")) "Durable operation phase is missing: $phase"
}
Assert-True ($codec -match "PhantomProfileComponent\.MAX_PAYLOAD_BYTES" -and $codec -match "DataOutputStream" -and $codec -match "DataInputStream") "Bounded deterministic party state codec is missing."
Assert-True ($store -match "insertComponent" -and $store -match "updateComponent" -and $store -match "listManagedAfter" -and $store -match "MAX_PAGE_SIZE\s*=\s*256") "Optimistic component persistence or bounded recovery paging is missing."
foreach ($goalKey in @('"party.form"', '"party.join"', '"party.lead"', '"party.member"', '"party.travel"', '"party.leave"'))
{
	Assert-True ($coordinator.Contains($goalKey)) "Explicit party goal key is missing: $goalKey"
}
Assert-True ($coordinator -match "explicitConsent" -and $coordinator -match "goalTargets" -and $coordinator -match "restart\.real_consent_not_restored") "Real-player consent or restart non-restoration boundary is missing."
Assert-True ($coordinator -match "electLeader" -and $coordinator -match "min\(Comparator\.comparingLong\(MemberRef::profileId\)\)" -and $coordinator -match "max\(\)\.orElse\(0\) \+ 1") "Deterministic Phantom-only leader recovery is missing."
Assert-True ($coordinator -match "MAX_INBOUND_INVITES\s*=\s*4096" -and $coordinator -match "ArrayBlockingQueue") "Managed invitation backpressure is missing."
$memberPrepared = $coordinator.IndexOf("preparedMember = save", [StringComparison]::Ordinal)
$canonicalInvite = $coordinator.IndexOf("_backend.invite(current.leader(), target, distribution)", [StringComparison]::Ordinal)
Assert-True (($memberPrepared -ge 0) -and ($canonicalInvite -gt $memberPrepared)) "Durable Phantom member claim does not precede the canonical invite."
Assert-True ($coordinator -match "goal\.revision\(\) != operation\.leaderGoalRevision\(\)" -and $coordinator -match "goalTargets\(goal, operation\.leader\(\)\.characterObjectId\(\)\)") "Stale goal revision or exact member consent guard is missing."

$roleCatalog = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRoleCatalog.java"
$roleMatcher = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRoleMatcher.java"
$roleData = Read-Utf8Strict "dist/game/data/phantoms/party/high-five-party-roles-v1.xml"
Assert-True ($roleCatalog -match "disallow-doctype-decl" -and $roleCatalog -match "ACCESS_EXTERNAL_DTD" -and $roleCatalog -match "SHA-256") "Strict content-addressed role catalog is missing."
Assert-True ($roleMatcher -match "MemberCapability" -and $roleMatcher -match "RoleAssignment" -and $roleMatcher -match "Vacancy" -and $roleMatcher -match "contextualScore") "Contextual multi-capability role/vacancy matching is incomplete."
Assert-True ($roleMatcher -notmatch "void search\(" -and $roleMatcher -match "for \(RoleRequirement requirement" -and $roleMatcher -match "for \(MemberSnapshot member") "Role matching is not bounded to requirements times roster."
Assert-True ($roleData -match "combat\.heal" -and $roleData -match "combat\.recharge" -and $roleData -match "combat\.resurrection" -and $roleData -notmatch "classId|class_id") "Party role data is not capability-based."

$semantic = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/PhantomSemanticAct.java"
$semanticKeys = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/PhantomPartySemanticActs.java"
Assert-True ($semantic -match "String actKey" -and $semantic -match "Map<String, PhantomDomainRef>" -and $semantic -match "Map<String, Long>") "Typed string-key semantic act is missing."
Assert-True ($semanticKeys -match "Set<String> KEYS" -and $semanticKeys -match "groupGeneration" -and $semanticKeys -match "currentGroupAtGeneration") "Party semantic registry is not generation-aware."
Assert-True ($semantic -notmatch "String (?:message|text|utterance|prompt)") "Party semantic act contains a text/LLM payload."

$route = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java"
$tactics = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java"
$combat = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java"
Assert-True ($route -match "_navigation\.submit\(" -and $route -match "leader" -and $route -match "maximumSeparation" -and $route -match "PARTY_ROUTE" -and $route -notmatch "public synchronized|private synchronized") "Shared leader route/regroup ownership is incomplete."
Assert-True ($route -match "_routeByGroup\.remove\(groupId\)" -and $route -match "routeId\.equals\(entry\.getValue\(\)\._routeId\)") "Route cancellation is not group-scoped."
Assert-True ($route -notmatch "teleport|setXYZ|setLocation|background") "Party route contains snap movement or background simulation."
foreach ($directive in @("ASSIST_TARGET", "PROTECT_MEMBER", "HEAL_MEMBER", "RECHARGE_MEMBER", "RESURRECT_MEMBER", "PARTY_SUPPORT"))
{
	Assert-True ($tactics -match ("\b" + $directive + "\b")) "Party tactic is missing: $directive"
}
Assert-True ($tactics -match "acquireExternalAction" -and $combat -match "PARTY_TACTIC" -and $combat -match "PARTY_SUPPORT" -and $combat -match "PARTY_ROUTE") "Party actions do not share combat external-action ownership."

$composite = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/activity/PhantomCompositeSchedulerControlPort.java"
$system = Read-Utf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
$config = Read-Utf8Strict "dist/game/config/Custom/PhantomPlayers.ini"
$configJava = Read-Utf8Strict "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
Assert-True ($composite -match "MAXIMUM_PORTS\s*=\s*8" -and $composite -match "_stageFailures" -and $composite -match "onPulse") "Bounded composite scheduler control is missing."
Assert-True ($system -match "new PhantomCompositeSchedulerControlPort" -and $system -match "_populationManager,\s*_partyCoordinator" -and $system -match "_partyCoordinator\.beginStop" -and $system -match "_partyCoordinator\.finishStop") "Single scheduler chain or party lifecycle cleanup is missing."
Assert-True ($config -match "(?m)^PhantomPartyOperationsPerPulse\s*=\s*64\s*$" -and $configJava -match "DEFAULT_PARTY_OPERATIONS_PER_PULSE\s*=\s*64") "Party operation budget default is missing."

$build = Read-Utf8Strict "build.xml"
foreach ($target in @(
	"phantom-party-canonical-invitation-test",
	"phantom-party-state-recovery-test",
	"phantom-party-role-vacancy-test",
	"phantom-party-semantic-acts-test",
	"phantom-party-route-test",
	"phantom-party-tactics-test",
	"phantom-party-lifecycle-test",
	"phantom-party-server-integration-test",
	"phantom-party-performance-smoke",
	"phantom-party-test",
	"phantom-party-affected-test",
	"phantom-static-verify-017"
))
{
	Assert-True ($build -match ('name="' + [Regex]::Escape($target) + '"')) "Missing Goal 017 Ant target: $target"
}
Assert-True ($build -match ('phantom\.goal017\.seed"\s+value="' + $seed + '"')) "Goal 017 build seed is missing."
$tests = Read-Utf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java"
foreach ($control in @("stale", "restart", "real", "100000", "10000", "1000", "generation"))
{
	Assert-True ($tests -match $control) "Goal 017 focused control is missing: $control"
}

$report = Read-Utf8Strict "docs/phantoms/reports/017-party-coordination-kernel.md"
Assert-True (($report -split "`r?`n").Count -le 240) "Goal 017 report exceeds 240 lines."
Assert-True ($report -match "READ_SET" -and $report -match "Scope" -and $report -match "Goal 016" -and $report -match "17001701") "Goal 017 report evidence is incomplete."

$utf8Exclusions = @(
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"docs/PHANTOM_BOTS_ROADMAP.md",
		"tools/phantoms/verify-task-014a.ps1",
		"tools/phantoms/verify-task-016.ps1",
	"tools/phantoms/verify-task-017.ps1"
)
$mojibakeMarkers = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("0KDRn3zQoNGcfNCg0Zt80KDigKJ80KDQjnzQoOKAunzQoMKkfNCg0Zp80KDQiHzQoNGZfNCg0pF80KDCtXzQoMKwfNCgwrt80KDQhXzQoNGVfNCh0I980KHigqx80KHQgnzQoeKAuXzQodCKfNCh4oCafNCh0ZN80KHigKF80KHigKZ80KHigKB877+9")).Split("|")
$escapedCyrillic = "\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};"
foreach ($path in $changed)
{
	$fullPath = Join-Path $moduleRoot $path
	if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf))
	{
		continue
	}
	if ($path -match "^docs/phantoms/tasks/017-party-coordination-kernel/")
	{
		continue
	}
	if (@(".java", ".xml", ".ini", ".md", ".ps1") -notcontains [IO.Path]::GetExtension($path).ToLowerInvariant())
	{
		continue
	}
	$text = Read-Utf8Strict $path
	if ($utf8Exclusions -notcontains $path)
	{
		foreach ($marker in $mojibakeMarkers)
		{
			Assert-True (-not $text.Contains($marker)) "Mojibake marker found in changed file: $path"
		}
		Assert-True ($text -notmatch $escapedCyrillic) "Escaped Cyrillic found in changed file: $path"
	}
}

$jarState = "DEFERRED"
$jarPath = Join-Path $moduleRoot "dist/libs/GameServer.jar"
if (($mode -ne "working-implementation") -or (Test-Path -LiteralPath $jarPath -PathType Leaf))
{
	Assert-True (Test-Path -LiteralPath $jarPath -PathType Leaf) "GameServer.jar is missing."
	$jarTool = Join-Path $env:JAVA_HOME "bin/jar.exe"
	Assert-True (Test-Path -LiteralPath $jarTool -PathType Leaf) "JDK jar tool is missing."
	$jarEntries = @(& $jarTool tf $jarPath)
	Assert-True ($LASTEXITCODE -eq 0) "Cannot inspect GameServer.jar."
	$requiredEntries = @(
		"org/l2jmobius/gameserver/model/groups/PartyInvitationService.class",
		"org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.class",
		"org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.class",
		"org/l2jmobius/gameserver/phantoms/semantic/PhantomSemanticAct.class",
		"org/l2jmobius/gameserver/phantoms/activity/PhantomCompositeSchedulerControlPort.class"
	)
	$present = @($requiredEntries | Where-Object { $jarEntries -contains $_ }).Count
	if ($mode -eq "working-implementation")
	{
		if ($present -eq $requiredEntries.Count)
		{
			$jarState = "OK"
		}
		else
		{
			$jarState = "DEFERRED"
		}
	}
	else
	{
		Assert-True ($present -eq $requiredEntries.Count) "GameServer.jar is missing Goal 017 classes."
		$jarState = "OK"
	}
}

Write-Output "TASK017_VERIFIER"
Write-Output "graph=$mode"
Write-Output "scope_files=$($changed.Count)"
Write-Output "historical016=TASK016_VERIFIER_OK"
Write-Output "package=OK"
Write-Output "canonical_invitation=OK"
Write-Output "durable_recovery=OK"
Write-Output "roles_semantics=OK"
Write-Output "route_tactics_ownership=OK"
Write-Output "scheduler_lifecycle=OK"
Write-Output "utf8=OK"
Write-Output "jar=$jarState"
Write-Output "TASK017_VERIFIER_OK"
