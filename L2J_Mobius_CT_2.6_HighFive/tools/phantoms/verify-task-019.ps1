param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AcceptedCommit = "384b521f2cd29f4162c9aca9116eb0ff40cbd681"
$RequiredParent = "d30b657a9351d8cb099548e959854bf826b7d1d1"
$RequiredSubject = "feat(phantoms): add russian semantic understanding"
$RequiredBranch = "feature/phantom-world"
$RequiredSeed = "19001901"

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
		Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Required Goal 019 file is missing: $relativePath"
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
	Assert-True ($process.ExitCode -eq 0) "Accepted Goal 019 blob is absent: $relativePath ($errorText)"
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
		"dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml",
		"dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSkeletonSuite.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
		"tools/phantoms/verify-task-019.ps1",
		"docs/phantoms/architecture/RUSSIAN_SEMANTIC_UNDERSTANDING_CONTRACT.md",
		"docs/phantoms/reports/019-russian-semantic-understanding.md",
		"docs/phantoms/reviews/018-social-memory-review.md"
	)
	if ($exact -contains $path)
	{
		return $true
	}
	if ($path -match "^java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemantic[^/]+\.java$")
	{
		return $true
	}
	if ($path -match "^test/java/org/l2jmobius/tests/phantoms/PhantomSemantic[^/]+\.java$")
	{
		return $true
	}
	return $path -match "^docs/phantoms/tasks/019-russian-semantic-pack/[^/]+$"
}

$script:ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
Push-Location $script:ModuleRoot
try
{
	$repositoryRoot = (Git-Lines @("rev-parse", "--show-toplevel") | Select-Object -First 1)
	$moduleName = Split-Path $script:ModuleRoot -Leaf
	$script:ModulePrefix = $moduleName + "/"
	$branch = (Git-Lines @("branch", "--show-current") | Select-Object -First 1)
	Assert-True ($branch -eq $RequiredBranch) "Goal 019 must remain on feature/phantom-world."
	$head = (Git-Lines @("rev-parse", "HEAD") | Select-Object -First 1)
	Assert-True ((Git-Lines @("rev-parse", "$AcceptedCommit^" ) | Select-Object -First 1) -eq $RequiredParent) "Accepted Goal 019 commit has the wrong parent."
	Assert-True ((Git-Lines @("show", "-s", "--format=%s", $AcceptedCommit) | Select-Object -First 1) -eq $RequiredSubject) "Accepted Goal 019 commit has the wrong subject."
	& git merge-base --is-ancestor $AcceptedCommit $head
	Assert-True ($LASTEXITCODE -eq 0) "Accepted Goal 019 commit is not an ancestor of HEAD."
	$script:Mode = "accepted"
	$script:AcceptedCommit = $AcceptedCommit

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
	Assert-True (($changedPaths.Count -gt 0) -and ($changedPaths.Count -le 28)) "Goal 019 total scope must contain 1..28 files."
	foreach ($path in $changedPaths)
	{
		Assert-True ($path -notmatch "^docs/phantoms/tasks/020") "Goal 020 path leaked into historical Goal 019 scope: $path"
		Assert-True (Is-AllowedPath $path) "Out-of-scope Goal 019 path: $path"
		Assert-True ($path -notmatch "(^|/)(Player|Party|World)\.java$|(^|/)(sql|schema|migrations?)/|L2J_Mobius_CT_(?!2\.6_HighFive)|(^|/)phantoms/social/") "Forbidden Goal 019 path: $path"
	}
	foreach ($required in @(
		"build.xml",
		"dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml",
		"dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticModel.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticGrounding.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticNormalizer.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java",
		"java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.java",
		"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
		"test/java/org/l2jmobius/tests/phantoms/PhantomSemanticSuite.java",
		"tools/phantoms/verify-task-019.ps1",
		"docs/phantoms/reviews/018-social-memory-review.md",
		"docs/phantoms/architecture/RUSSIAN_SEMANTIC_UNDERSTANDING_CONTRACT.md",
		"docs/phantoms/reports/019-russian-semantic-understanding.md"
	))
	{
		Assert-True ($changed.Contains($required)) "Required Goal 019 artifact is absent: $required"
	}

	$production = @($changedPaths | Where-Object { ($_ -match "^java/org/l2jmobius/gameserver/") -or ($_ -match "^dist/game/(config|data)/") })
	Assert-True ($production.Count -le 13) "Goal 019 exceeds 13 changed production/data/config files."
	if ($script:Mode -eq "working")
	{
		$newProduction = @()
		foreach ($path in $production)
		{
			$parentEntries = @(Git-Lines @("-C", $repositoryRoot, "ls-tree", "--name-only", $RequiredParent, "--", ($script:ModulePrefix + $path)))
			if ($parentEntries.Count -eq 0)
			{
				$newProduction += $path
			}
		}
	}
	else
	{
		$newProduction = @(Git-Lines @("diff", "--name-only", "--diff-filter=A", $RequiredParent, $script:AcceptedCommit, "--", "java", "dist/game") | ForEach-Object { To-ModulePath $_ })
	}
	Assert-True ($newProduction.Count -le 10) "Goal 019 exceeds 10 new production/data/config files."

	$manifest = Read-TargetUtf8Strict "docs/phantoms/tasks/019-russian-semantic-pack/PACKAGE_MANIFEST.json" | ConvertFrom-Json
	Assert-True ($manifest.requiredParent -eq $RequiredParent) "Goal 019 task package parent mismatch."
	Assert-True ($manifest.commitSubject -eq $RequiredSubject) "Goal 019 task package subject mismatch."
	Assert-True ([string] $manifest.deterministicSeed -eq $RequiredSeed) "Goal 019 task package seed mismatch."
	foreach ($property in $manifest.payloadSha256.PSObject.Properties)
	{
		Assert-True ((Get-TargetSha256 $property.Name) -eq ([string] $property.Value).ToUpperInvariant()) "Goal 019 task package hash mismatch: $($property.Name)"
	}

	$review018 = Read-TargetUtf8Strict "docs/phantoms/reviews/018-social-memory-review.md"
	Assert-True ($review018 -match "ACCEPT_WITH_ACTIVATION_GATE" -and $review018 -match $RequiredParent -and $review018 -match "retained" -and $review018 -match "out-of-order" -and $review018 -match "party\.member\.joined") "Goal 018 activation gate record is incomplete."
	$master = Read-TargetUtf8Strict "PHANTOM_DEVELOPMENT_MASTER_PLAN.md"
	Assert-True ($master -match '018[\s\S]+?Status: `ACCEPT_WITH_ACTIVATION_GATE`' -and $master -match '019[\s\S]+?Status: `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`') "Master-plan Goal 018/019 status is incomplete."
	foreach ($future in @("020", "021", "025"))
	{
		Assert-True ($master -match "$future[\s\S]+?Status: ``NOT_STARTED``") "Future Goal $future was started."
	}

	$xmlText = Read-TargetUtf8Strict "dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml"
	$corpusText = Read-TargetUtf8Strict "dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv"
	$packHash = Get-TargetSha256 "dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml"
	$corpusHash = Get-TargetSha256 "dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv"
	[xml] $xml = $xmlText
	Assert-True ($xml.semanticPack.id -eq "high-five-ru-semantic-v1" -and $xml.semanticPack.locale -eq "ru" -and $xml.semanticPack.version -eq "1") "Semantic XML identity changed."
	Assert-True (@($xml.semanticPack.slots.slot).Count -eq 10) "Semantic XML must declare exactly ten slot types."
	Assert-True (@($xml.semanticPack.intents.intent).Count -eq 14) "Semantic XML must declare exactly fourteen intents."
	$lines = @($corpusText -split "`r?`n" | Where-Object { $_.Length -gt 0 })
	Assert-True ($lines.Count -eq 241) "Semantic corpus must contain one header and 240 cases."
	Assert-True ($lines[0] -eq "case_id`tinput`tcontext_fixture`texpected_status`texpected_intent`texpected_slots`tminimum_confidence`treason_key") "Semantic TSV header changed."
	Assert-True (@($lines | Select-Object -Skip 1 | ForEach-Object { ($_ -split "`t", -1)[0] } | Sort-Object -Unique).Count -eq 240) "Semantic corpus IDs are not unique."

	$model = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticModel.java"
	$grounding = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticGrounding.java"
	$normalizer = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticNormalizer.java"
	$loader = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.java"
	$service = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.java"
	$semanticSources = $model + $grounding + $normalizer + $loader + $service
	Assert-True ($model -match "MAX_CONTEXT_PLAYERS = 32" -and $model -match "MAX_ALTERNATIVES = 4" -and $model -match "canonicalEncoding") "Semantic immutable result/context bounds are incomplete."
	Assert-True ($grounding -match "PhantomGameKnowledgeQuery" -and $grounding -match "PhantomTopologyQuery" -and $grounding -match "PhantomPartyRoleCatalog" -and $grounding -match "generation drifted") "Semantic authoritative grounding is incomplete."
	Assert-True ($normalizer -match "Normalizer\.Form\.NFKC" -and $normalizer -match "Locale\.ROOT" -and $normalizer -match "mixed_script" -and $model -match "originalStartCodePoint") "Semantic Unicode normalization/spans are incomplete."
	Assert-True ($loader -match "disallow-doctype-decl" -and $loader -match "ACCESS_EXTERNAL_DTD" -and $loader -match "decodeUtf8Strict" -and $loader -match "readBounded") "Semantic XML/TSV loader is not strict, bounded and XXE-safe."
	Assert-True ($service -match "CandidateBudget" -and $service -match "ambiguityMargin" -and $service -match "maximumDistance" -and $service -match "hasCrossSlotExactConflict") "Semantic bounded fuzzy/ambiguity gates are incomplete."
	Assert-True ($semanticSources -notmatch "org\.l2jmobius\.gameserver\.model\.Player|org\.l2jmobius\.gameserver\.model\.World|org\.l2jmobius\.gameserver\.model\.groups\.Party|java\.sql|\bChat\b|\bServerPacket\b|\bClientPacket\b|sendPacket|broadcast|DatabaseFactory|OpenAI|\bLLM\b") "Semantic production contains a forbidden runtime/action/social seam."
	Assert-True ($semanticSources -notmatch "\b(?:ExecutorService|ScheduledFuture|CompletableFuture)\b|ThreadPool\.|new\s+Thread\s*\(") "Semantic production owns a worker, executor, Future or task."
	Assert-True ($semanticSources -notmatch "phantoms\.social|PhantomSocial") "Goal 019 semantic production consumes social production."

	$system = Read-TargetUtf8Strict "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"
	$knowledgeStart = $system.IndexOf("_gameKnowledgeService.start()")
	$roleStart = $system.IndexOf("PhantomPartyRoleCatalog.load")
	$semanticStart = $system.IndexOf("PhantomSemanticUnderstandingService.production")
	$partyStart = $system.IndexOf("new PhantomPartyCoordinator")
	Assert-True (($knowledgeStart -ge 0) -and ($roleStart -gt $knowledgeStart) -and ($semanticStart -gt $roleStart) -and ($partyStart -gt $semanticStart)) "PhantomSystem semantic startup authority order is wrong."
	Assert-True ($system -match "_semanticUnderstandingService\.beginStop\(\)" -and $system -match "semanticStopped" -and $system -match "PhantomSemanticUnderstandingService\.Snapshot\.inactive\(\)") "PhantomSystem semantic stop/snapshot ownership is incomplete."

	$tests = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomSemanticSuite.java"
	$launcher = Read-TargetUtf8Strict "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
	$build = Read-TargetUtf8Strict "build.xml"
	foreach ($focusedMode in @("semantic-pack", "semantic-normalization", "semantic-intents", "semantic-grounding", "semantic-context", "semantic-corpus", "semantic-lifecycle-performance"))
	{
		Assert-True ($launcher.Contains("case `"$focusedMode`"") -and $build.Contains("`"$focusedMode`"")) "Focused semantic mode is not wired: $focusedMode"
	}
	Assert-True ($build -match 'phantom\.goal019\.seed" value="19001901"' -and $build -match 'target name="phantom-semantic-test"') "Goal 019 seed or aggregate target is absent."
	Assert-True ($tests -match "100000" -and $tests -match "positiveIntentAccuracyBasisPoints" -and $tests -match "forward-reverse-shuffle" -and $tests -match "safetyBasisPoints") "Focused semantic corpus/performance coverage is incomplete."

	$contract = Read-TargetUtf8Strict "docs/phantoms/architecture/RUSSIAN_SEMANTIC_UNDERSTANDING_CONTRACT.md"
	$report = Read-TargetUtf8Strict "docs/phantoms/reports/019-russian-semantic-understanding.md"
	Assert-True ($contract -match "NFKC" -and $contract -match "Game Knowledge" -and $contract -match "CLARIFICATION_REQUIRED" -and $contract -match "Goal 018 activation gate") "Semantic architecture contract is incomplete."
	Assert-True (($report -split "`r?`n").Count -le 190) "Goal 019 report exceeds 190 lines."
	Assert-True ($report.Contains($RequiredParent) -and $report.Contains($RequiredSubject)) "Goal 019 report graph evidence is incomplete."
	if ($script:Mode -eq "accepted")
	{
		Assert-True ($report -match 'Status: `SUCCESS`') "Accepted Goal 019 report does not record SUCCESS."
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
		Assert-True ($LASTEXITCODE -eq 0) "Remote feature/phantom-world does not descend from accepted Goal 019."
		$jarEntries = & jar tf (Join-Path $script:ModuleRoot "dist/libs/GameServer.jar")
		Assert-True ($LASTEXITCODE -eq 0) "Could not inspect GameServer.jar."
		Assert-True ($jarEntries -contains "org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.class") "GameServer.jar lacks PhantomSemanticUnderstandingService."
		Assert-True ($jarEntries -contains "org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticPack.class") "GameServer.jar lacks PhantomSemanticPack."
	}
	else
	{
		$compiledService = Join-Path (Split-Path $script:ModuleRoot -Parent) "build/bin/org/l2jmobius/gameserver/phantoms/semantic/understanding/PhantomSemanticUnderstandingService.class"
		Assert-True (Test-Path -LiteralPath $compiledService -PathType Leaf) "Compiled semantic service class is absent."
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

	Write-Output "TASK019_VERIFIER_OK"
	Write-Output "mode=$($script:Mode)"
	Write-Output "accepted_commit=$(if ($script:Mode -eq 'accepted') { $script:AcceptedCommit } else { 'WORKING' })"
	Write-Output "accepted_parent=$RequiredParent"
	Write-Output "pack_sha256=$packHash"
	Write-Output "corpus_sha256=$corpusHash"
	Write-Output "corpus_cases=240"
	Write-Output "scope=$($changedPaths.Count)"
	Write-Output "production=$($production.Count)"
	Write-Output "new_production=$($newProduction.Count)"
}
finally
{
	Pop-Location
}
