param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RequiredParent = "6cf261370e3cb98158805828e995cfe6e8b14651"
$RequiredSubject = "feat(phantoms): add social memory and relationships"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "18001801"
$Accepted017Parent = "0015a5ffd0c10a99514732ef52b969a39ac62eb7"
$Accepted017Subject = "fix(phantoms): finalize party terminal verification"

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
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required Goal 018 file is missing: $relativePath"
		return [IO.File]::ReadAllBytes($path)
	}

	$repositoryPath = $script:ModulePrefix + $relativePath
	$start = [Diagnostics.ProcessStartInfo]::new()
	$start.FileName = "git"
	$start.Arguments = "show $($script:AcceptedCommit)`:$repositoryPath"
	$start.UseShellExecute = $false
	$start.RedirectStandardOutput = $true
	$start.RedirectStandardError = $true
	$start.CreateNoWindow = $true
	$process = [Diagnostics.Process]::Start($start)
	$memory = [IO.MemoryStream]::new()
	$process.StandardOutput.BaseStream.CopyTo($memory)
	$errorText = $process.StandardError.ReadToEnd()
	$process.WaitForExit()
	Assert-True ($process.ExitCode -eq 0) "Accepted Goal 018 blob is absent: $relativePath ($errorText)"
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

function Is-AllowedPath([string] $path)
{
	$exact = @(
		"build.xml",
		"PHANTOM_DEVELOPMENT_MASTER_PLAN.md",
		"dist/game/config/Custom/PhantomPlayers.ini",
		"dist/game/data/phantoms/social/high-five-social-v1.xml",
		"java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-017.ps1",
		"tools/phantoms/verify-task-018.ps1",
		"docs/phantoms/architecture/SOCIAL_MEMORY_RELATIONSHIP_CONTRACT.md",
		"docs/phantoms/reports/018-social-memory-relationships.md",
		"docs/phantoms/reviews/017-party-coordination-final-review.md"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/social/[^/]+\.java$")
	{
		return $true
	}
	if ($path -match "^test/java/org/l2jmobius/tests/phantoms/PhantomSocial[^/]*\.java$")
	{
		return $true
	}
	return $path -match "^docs/phantoms/tasks/018-social-memory-relationships/[^/]+$"
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$moduleName = Split-Path $script:ModuleRoot -Leaf
	$script:ModulePrefix = $moduleName + "/"
	$branch = (Git-Lines @("branch", "--show-current") | Select-Object -First 1)
	Assert-True ($branch -eq $RequiredBranch) "Goal 018 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	$accepted017Parent = (Git-Lines @("rev-parse", "$RequiredParent^") | Select-Object -First 1)
	$accepted017Subject = (Git-Lines @("show", "-s", "--format=%s", $RequiredParent) | Select-Object -First 1)
	Assert-True ($accepted017Parent -eq $Accepted017Parent) "Accepted Goal 017 commit has the wrong parent."
	Assert-True ($accepted017Subject -eq $Accepted017Subject) "Accepted Goal 017 commit has the wrong subject."
	& git merge-base --is-ancestor $RequiredParent $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 017 commit is not an ancestor of HEAD."

	$script:Mode = "working"
	$script:AcceptedCommit = ""
	if ($head -ne $RequiredParent)
	{
		$candidates = @()
		foreach ($line in Git-Lines @("log", "--format=%H`t%P`t%s", "--ancestry-path", "$RequiredParent..$head"))
		{
			$parts = $line -split "`t", 3
			if (($parts.Count -eq 3) -and ($parts[1] -eq $RequiredParent) -and ($parts[2] -eq $RequiredSubject))
			{
				$candidates += $parts[0]
			}
		}
		Assert-True ($candidates.Count -eq 1) "Expected one unique ordinary Goal 018 direct child."
		$script:AcceptedCommit = $candidates[0]
		& git merge-base --is-ancestor $script:AcceptedCommit $head
		Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 018 commit is not an ancestor of HEAD."
		Assert-True ((Git-Lines @("rev-parse", "$($script:AcceptedCommit)^") | Select-Object -First 1) -eq $RequiredParent) "Goal 018 accepted commit parent changed."
		Assert-True ((Git-Lines @("show", "-s", "--format=%s", $script:AcceptedCommit) | Select-Object -First 1) -eq $RequiredSubject) "Goal 018 accepted commit subject changed."
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
		foreach ($line in Git-Lines @("diff", "--name-only", $RequiredParent, $script:AcceptedCommit, "--"))
		{
			[void] $changed.Add((To-ModulePath $line))
		}
	}
	$changedPaths = @($changed | Sort-Object)
	Assert-True (($changedPaths.Count -gt 0) -and ($changedPaths.Count -le 32)) "Goal 018 total scope must contain 1..32 files."
	foreach ($path in $changedPaths)
	{
		Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 018 path: $path"
		Assert-True ($path -notmatch "(^|/)Player\.java$|(^|/)Party\.java$|(^|/)PartyInvitationService\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)") "Forbidden Goal 018 path: $path"
	}
	foreach ($required in @(
		"build.xml",
		"dist/game/data/phantoms/social/high-five-social-v1.xml",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialModel.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialCatalog.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStateCodec.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStore.java",
		"java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialEventSink.java",
		"java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSocialSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSocialPartyIntegrationSuite.java",
		"tools/phantoms/verify-task-018.ps1",
		"docs/phantoms/architecture/SOCIAL_MEMORY_RELATIONSHIP_CONTRACT.md",
		"docs/phantoms/reports/018-social-memory-relationships.md"
	))
	{
		Assert-True ($changed.Contains($required)) "Required Goal 018 artifact is absent: $required"
	}

	$production = @($changedPaths | Where-Object { ($_ -match "^java/org/l2jmobius/gameserver/") -or ($_ -match "^dist/game/(config|data)/") })
	Assert-True ($production.Count -le 15) "Goal 018 exceeds 15 changed production/data/config files."
	if ($script:Mode -eq "working")
	{
		$tracked = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
		foreach ($path in Git-Lines @("ls-files", "--"))
		{
			[void] $tracked.Add((To-ModulePath $path))
		}
		$newProduction = @($production | Where-Object { -not $tracked.Contains($_) })
	}
	else
	{
		$newProduction = @(Git-Lines @("diff", "--name-only", "--diff-filter=A", $RequiredParent, $script:AcceptedCommit, "--", "java", "dist/game") | ForEach-Object { To-ModulePath $_ })
	}
	Assert-True ($newProduction.Count -le 10) "Goal 018 exceeds 10 new production/data/config files."

	$manifest = Read-TargetUtf8Strict "docs/phantoms/tasks/018-social-memory-relationships/PACKAGE_MANIFEST.json" | ConvertFrom-Json
	Assert-True ($manifest.requiredParent -eq $RequiredParent) "Goal 018 task package parent mismatch."
	Assert-True ($manifest.commitSubject -eq $RequiredSubject) "Goal 018 task package subject mismatch."
	Assert-True ([string] $manifest.deterministicSeed -eq $RequiredSeed) "Goal 018 task package seed mismatch."
	foreach ($property in $manifest.payloadSha256.PSObject.Properties)
	{
		Assert-True ((Get-TargetSha256 $property.Name) -eq ([string] $property.Value).ToUpperInvariant()) "Goal 018 task package hash mismatch: $($property.Name)"
	}

	$review017 = Read-TargetUtf8Strict "docs/phantoms/reviews/017-party-coordination-final-review.md"
	Assert-True ($review017 -match "ACCEPT_WITH_EXPLICIT_FUTURE_CONTRACTS" -and $review017 -match $RequiredParent) "Goal 017 final acceptance record is incomplete."
	$verifier017 = Read-TargetUtf8Strict "tools/phantoms/verify-task-017.ps1"
	Assert-True ($verifier017 -match [regex]::Escape($RequiredParent) -and $verifier017 -match "merge-base --is-ancestor" -and $verifier017 -match "Read-AcceptedUtf8Strict") "Goal 017 verifier is not pinned and descendant-compatible."

	$catalog = Read-TargetUtf8Strict "dist/game/data/phantoms/social/high-five-social-v1.xml"
	$catalogHash = Get-TargetSha256 "dist/game/data/phantoms/social/high-five-social-v1.xml"
	foreach ($key in @("trust", "respect", "fear", "anger", "friendship", "rivalry", "debt", "reliability", "helpfulness", "competence", "hostility"))
	{
		Assert-True ($catalog -match "key=`"$([regex]::Escape($key))`"") "Required social dimension is absent: $key"
	}
	foreach ($key in @("party.invite.accepted.outbound", "party.invite.accepted.inbound", "party.invite.refused.outbound", "party.invite.refused.inbound", "party.invite.expired.outbound", "party.member.joined", "party.member.left", "party.member.expelled", "party.leader.transferred", "party.support.received", "agreement.fulfilled", "agreement.broken", "debt.incurred", "debt.repaid"))
	{
		Assert-True ($catalog.Contains("key=`"$key`"")) "Required social event is absent: $key"
	}
	foreach ($key in @("goal.persistence", "risk.tolerance", "party.invite.preference", "party.support.priority", "conversation.warmth", "conflict.escalation"))
	{
		Assert-True ($catalog.Contains("key=`"$key`"")) "Required social modifier is absent: $key"
	}
	Assert-True ($catalog -match 'relationships="24"' -and $catalog -match 'memories="24"' -and $catalog -match "expired-lowest-salience-oldest-hash") "Social catalog bounds or eviction policy changed."

	$model = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialModel.java"
	$codec = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStateCodec.java"
	$loader = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialCatalog.java"
	$service = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java"
	$store = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialStore.java"
	Assert-True ($model -match 'COMPONENT_TYPE = "social\.state"' -and $model -match "SCHEMA_VERSION = 1" -and $model -match "MAX_RELATIONSHIPS = 24" -and $model -match "MAX_MEMORIES = 24") "Social component identity or bounds changed."
	Assert-True ($codec -match "MAX_PAYLOAD_BYTES" -and $codec -match "Trailing bytes follow social\.state" -and $codec -match "ordering or uniqueness") "Compact social codec does not fail closed."
	Assert-True ($loader -match "disallow-doctype-decl" -and $loader -match "ACCESS_EXTERNAL_DTD" -and $loader -match "SHA-256" -and $loader -match "Duplicate global social catalog code") "Social catalog loader is not strict, hashed and XXE-safe."
	Assert-True ($service -match "STRIPES = 64" -and $service -match "MAX_ATTEMPTS = 3" -and $service -match "LinkedHashMap" -and $service -match "containsEvent") "Social writer ownership, retry or idempotency is incomplete."
	Assert-True ($service -match "Math\.max\(requestedMinute, state\.logicalMinute\(\)\)" -and $service -match "elapsedMinutes \* unitsPerDay" -and $service -match "effectiveSalience") "Lazy integer decay/expiry is incomplete."
	Assert-True ($store -match "insertComponent" -and $store -match "updateComponent" -and $store -notmatch "DELETE FROM|INSERT INTO|UPDATE phantom") "Social store does not reuse profile components."
	$socialSources = $model + $codec + $loader + $service + $store + (Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialEventSink.java")
	Assert-True ($socialSources -notmatch "\b(?:ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.|new\s+Thread\s*\(") "Social production owns a worker, executor, Future or task."
	Assert-True ($socialSources -notmatch "l2jmobiush5") "Social production names a database."

	$configJava = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java"
	$config = Read-TargetUtf8Strict "dist/game/config/Custom/PhantomPlayers.ini"
	Assert-True ($configJava -match "DEFAULT_SOCIAL_CACHE_PROFILES = 1024" -and $configJava -match 'PhantomSocialCacheProfiles"\), 16, 10000') "Social cache parser/default changed."
	Assert-True ($config -match "PhantomSocialCacheProfiles = 1024" -and $config -match "Valid range: 16\.\.10000") "Social cache config documentation changed."

	$party = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java"
	Assert-True (($party -split "PhantomGoalStatus\.ACTIVE").Count -ge 5 -and $party -notmatch "recoveryConsent") "Party consent/transition is not strictly ACTIVE-gated."
	Assert-True ($party -match "PhantomSocialEventSink\.noop" -and $party -match "emitInvitationTerminal" -and $party -match "emitJoined" -and $party -match "emitMembership") "Backward-compatible downstream Party sink is incomplete."
	foreach ($key in @("party.invite.accepted.outbound", "party.invite.accepted.inbound", "party.invite.refused.outbound", "party.invite.expired.outbound", "party.member.joined", "party.member.left", "party.member.expelled", "party.leader.transferred"))
	{
		Assert-True ($party.Contains("`"$key`"")) "Party coordinator event direction is absent: $key"
	}
	Assert-True ($party -match "SubjectRef\.phantom" -and $party -match "SubjectRef\.character" -and $party -match "socialEventFailures") "Party subject typing or failure accounting is incomplete."

	$system = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
	$socialStart = $system.IndexOf("new PhantomSocialService")
	$partyStart = $system.IndexOf("new PhantomPartyCoordinator")
	$decisionStart = $system.IndexOf("new PhantomDecisionEngine")
	Assert-True (($socialStart -ge 0) -and ($partyStart -gt $socialStart) -and ($decisionStart -gt $partyStart)) "PhantomSystem startup ownership order is wrong."
	$partyDrain = $system.IndexOf("_partyCoordinator.finishStop()")
	$socialStop = $system.IndexOf("_socialService.beginStop()", $partyDrain)
	Assert-True (($partyDrain -ge 0) -and ($socialStop -gt $partyDrain) -and $system.Contains("PhantomSocialService.Snapshot.inactive()")) "PhantomSystem shutdown/snapshot social ownership is incomplete."

	$tests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomSocialSuite.java"
	$integration = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomSocialPartyIntegrationSuite.java"
	$launcher = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
	$build = Read-TargetUtf8Strict "build.xml"
	foreach ($focusedMode in @("social-catalog", "social-codec", "social-personality", "social-decay", "social-events", "social-modifiers", "social-party-integration", "social-lifecycle-performance"))
	{
		Assert-True ($launcher.Contains("case `"$focusedMode`"") -and $build.Contains("`"$focusedMode`"")) "Focused social mode is not wired: $focusedMode"
	}
	Assert-True ($build -match 'phantom\.goal018\.seed" value="18001801"' -and $build -match 'target name="phantom-social-test"') "Goal 018 seed or aggregate target is absent."
	Assert-True ($tests -match "100000" -and $tests -match "10000" -and $tests -match "Worst-case social state" -and $tests -match "query-frequency-independent") "Focused social bounds/performance coverage is incomplete."
	Assert-True ($integration -match "PhantomTestDatabaseGuard\.TARGET_DATABASE" -and $integration -match "PartyInvitationService" -and $integration -match "byte-identical" -and $integration -match "Injected downstream social failure") "Real DB/Party social integration coverage is incomplete."

	$contract = Read-TargetUtf8Strict "docs/phantoms/architecture/SOCIAL_MEMORY_RELATIONSHIP_CONTRACT.md"
	$report = Read-TargetUtf8Strict "docs/phantoms/reports/018-social-memory-relationships.md"
	Assert-True ($contract -match "social\.state" -and $contract -match "AUTHORITY_STALE" -and $contract -match "Party drain") "Social architecture contract is incomplete."
	Assert-True (($report -split "`r?`n").Count -le 190) "Goal 018 report exceeds 190 lines."
	Assert-True ($report.Contains($RequiredParent) -and $report.Contains($RequiredSubject)) "Goal 018 report graph evidence is incomplete."
	if ($script:Mode -eq "accepted")
	{
		Assert-True ($report -match 'Status: `SUCCESS`') "Accepted Goal 018 report does not record SUCCESS."
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
		& git merge-base --is-ancestor $script:AcceptedCommit $remote
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not contain the accepted Goal 018 commit."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot "dist/libs/GameServer.jar")
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		Assert-True ($jarEntries -contains "org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.class") "GameServer.jar lacks PhantomSocialService."
		Assert-True ($jarEntries -contains "org/l2jmobius/gameserver/phantoms/social/PhantomSocialStateCodec.class") "GameServer.jar lacks PhantomSocialStateCodec."
	}
	else
	{
		$compiledService = Join-Path (Split-Path $script:ModuleRoot -Parent) "build/bin/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.class"
		Assert-True (Test-Path -LiteralPath $compiledService -PathType Leaf) "Compiled social service class is absent."
	}

	if ($script:Mode -eq "accepted")
	{
		& git diff --check $RequiredParent $script:AcceptedCommit --
	}
	else
	{
		& git diff --check $RequiredParent --
	}
	Assert-True ($LASTEXITCODE -eq 0) "git diff --check failed."

	Write-Output "TASK018_VERIFIER_OK"
	Write-Output "mode=$($script:Mode)"
	Write-Output "accepted_commit=$(if ($script:Mode -eq 'accepted') { $script:AcceptedCommit } else { 'WORKING' })"
	Write-Output "accepted_parent=$RequiredParent"
	Write-Output "catalog_sha256=$catalogHash"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
}
finally
{
	Pop-Location
}
