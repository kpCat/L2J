param()

$ErrorActionPreference = "Stop"
$Parent = "06929a2973ca2450688d413b4d58de034194053f"
$Goal013Parent = "ca50ea28f233e41343035977c55c98129e5d113a"
$AcceptedBaseline = "8dba87e9c1d5828376b80c1ea16c4578726d4947"
$Branch = "feature/phantom-world"
$Subject = "fix(phantoms): make class skill learning durable"
$ModuleRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$RepoRoot = (& git -C $ModuleRoot rev-parse --show-toplevel).Trim()
$ModuleName = Split-Path $ModuleRoot -Leaf
$Pass = 0
$Fail = 0

function Test-Gate
{
	param([string]$Id, [bool]$Condition, [string]$Detail)
	if ($Condition)
	{
		$script:Pass++
		Write-Output ("PASS " + $Id + " :: " + $Detail)
	}
	else
	{
		$script:Fail++
		Write-Output ("FAIL " + $Id + " :: " + $Detail)
	}
}

function Git-Text
{
	param([string[]]$Arguments)
	$oldPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"
	$output = & git -C $RepoRoot @Arguments 2>$null
	$exitCode = $LASTEXITCODE
	$ErrorActionPreference = $oldPreference
	if ($exitCode -ne 0)
	{
		throw ("git command failed with exit code " + $exitCode)
	}
	return (($output) -join "`n").Trim()
}

function Git-Succeeds
{
	param([string[]]$Arguments)
	$oldPreference = $ErrorActionPreference
	$ErrorActionPreference = "Continue"
	& git -C $RepoRoot @Arguments 1>$null 2>$null
	$result = $LASTEXITCODE -eq 0
	$ErrorActionPreference = $oldPreference
	return $result
}

function Module-Path
{
	param([string]$RepositoryPath)
	$normalized = $RepositoryPath.Replace("\", "/")
	$prefix = $ModuleName + "/"
	if ($normalized.StartsWith($prefix))
	{
		return $normalized.Substring($prefix.Length)
	}
	return $normalized
}

function Read-Text
{
	param([string]$RelativePath)
	return [System.IO.File]::ReadAllText((Join-Path $ModuleRoot $RelativePath), [System.Text.UTF8Encoding]::new($false, $true))
}

function Count-Matches
{
	param([string]$Text, [string]$Pattern)
	return ([regex]::Matches($Text, $Pattern)).Count
}

$head = Git-Text @("rev-parse", "HEAD")
$branch = Git-Text @("branch", "--show-current")
$childCount = [int](Git-Text @("rev-list", "--count", ($Parent + "..HEAD")))
$worktreePhase = $head -eq $Parent
$oneChild = $childCount -eq 1
$exactParent = $worktreePhase -or ((Git-Text @("rev-parse", "HEAD^")) -eq $Parent)
$exactSubject = $worktreePhase -or ((Git-Text @("show", "-s", "--format=%s", "HEAD")) -eq $Subject)
$remote = Git-Text @("rev-parse", ("origin/" + $Branch))

Test-Gate "repository.module" ($ModuleName -eq "L2J_Mobius_CT_2.6_HighFive") $ModuleName
Test-Gate "repository.branch" ($branch -eq $Branch) $branch
Test-Gate "repository.parent" ((Git-Text @("cat-file", "-t", $Parent)) -eq "commit") $Parent
Test-Gate "repository.goal013-parent" ((Git-Text @("rev-parse", ($Parent + "^"))) -eq $Goal013Parent) $Goal013Parent
Test-Gate "repository.accepted-baseline" ((Git-Text @("rev-parse", ($Parent + "^^"))) -eq $AcceptedBaseline) $AcceptedBaseline
Test-Gate "repository.one-child" ($worktreePhase -or $oneChild) "worktree phase or one ordinary child"
Test-Gate "repository.exact-parent" $exactParent "Goal 013B exact parent"
Test-Gate "repository.subject" $exactSubject "exact subject after commit"
Test-Gate "repository.remote-phase" (($remote -eq $Parent) -or ($remote -eq $head)) "baseline before push or exact pushed head"
Test-Gate "repository.root-gitignore" (Git-Succeeds @("diff", "--quiet", $Parent, "--", ".gitignore")) "root .gitignore unchanged"

$changed = New-Object System.Collections.Generic.SortedSet[string]
foreach ($arguments in @(
	@("diff", "--name-only", $Parent),
	@("diff", "--cached", "--name-only"),
	@("ls-files", "--others", "--exclude-standard")
))
{
	foreach ($line in ((Git-Text $arguments) -split "`r?`n"))
	{
		if ($line)
		{
			[void]$changed.Add((Module-Path $line))
		}
	}
}

$allowedExact = @(
	"build.xml",
	"docs/PHANTOM_BOTS_ROADMAP.md",
	"docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md",
	"docs/phantoms/reports/013b-durable-class-skill-learning-transaction.md",
	"java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionDurabilitySuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionOperationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomProgressionPerformanceSuite.java",
	"test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java",
	"tools/phantoms/verify-task-013b.ps1"
)
$outside = @($changed | Where-Object {
	($_ -notin $allowedExact) -and
	($_ -notlike "java/org/l2jmobius/gameserver/phantoms/progression/*") -and
	($_ -notlike "docs/phantoms/tasks/013b-durable-class-skill-learning-transaction/*")
})
Test-Gate "scope.exact-allowlist" ($outside.Count -eq 0) $(if ($outside.Count -eq 0) { "Goal 013B exact allowlist" } else { $outside -join "," })
Test-Gate "scope.no-other-chronicle" (@($changed | Where-Object { $_ -match '^L2J_Mobius_' }).Count -eq 0) "High Five only"
Test-Gate "scope.no-core-player-item-inventory" (@($changed | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/(model/actor/Player\.java|model/item/instance/Item\.java|model/itemcontainer/|network/clientpackets/RequestAcquireSkill\.java)' }).Count -eq 0) "ordinary core unchanged"
Test-Gate "scope.no-accepted-knowledge" (@($changed | Where-Object { $_ -match '^(java/org/l2jmobius/gameserver/phantoms/knowledge|dist/game/data/phantoms/knowledge)/' }).Count -eq 0) "accepted Game Knowledge unchanged"
Test-Gate "scope.no-config-schema" (@($changed | Where-Object { $_ -match '^(dist/game/config/|dist/sql/|sql/)' }).Count -eq 0) "config and schema unchanged"
Test-Gate "scope.no-protected-phantom-subsystems" (@($changed | Where-Object { $_ -match '^java/org/l2jmobius/gameserver/phantoms/(combat|scheduler|decision|profile|player|identity|knowledge)/' }).Count -eq 0) "protected Phantom subsystems unchanged"
Test-Gate "scope.no-future-goals" (@($changed | Where-Object { $_ -match '(tasks|reports|reviews)/(014|015|017|025)-' }).Count -eq 0) "Goal 014/015/017/025 not started"
Test-Gate "scope.no-binaries-geodata" (@($changed | Where-Object { $_ -match '\.l2j$|\.(jar|class|exe|dll|zip|7z|png|jpg|jpeg)$' }).Count -eq 0) "no binary or geodata artifacts"

$transactionPath = "java/org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.java"
$transaction = Read-Text $transactionPath
$backend = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java"
$model = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java"
$service = Read-Text "java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionService.java"
$productionPaths = @(Get-ChildItem -LiteralPath (Join-Path $ModuleRoot "java/org/l2jmobius/gameserver/phantoms/progression") -Filter "*.java" -File | Sort-Object Name)
$production = ($productionPaths | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8) }) -join "`n"
$productionOutsideTransaction = ($productionPaths | Where-Object { $_.Name -ne "PhantomClassSkillLearningTransaction.java" } | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8) }) -join "`n"

Test-Gate "transaction.dedicated-facade" ($transaction.Contains("final class PhantomClassSkillLearningTransaction") -and $backend.Contains("_skillLearningTransaction.execute(")) "one bounded CLASS transaction facade"
Test-Gate "transaction.single-sql-owner" ($productionOutsideTransaction -notmatch 'character_skills|character_subclasses|UPDATE\s+characters\s+SET\s+sp|FROM\s+items\s+WHERE\s+object_id') "durable SQL remains inside facade"
Test-Gate "transaction.row-locks" ((Count-Matches $transaction 'FOR UPDATE') -ge 4) "SP, skill and item rows locked"
Test-Gate "transaction.boundary" ($transaction.Contains("connection.setAutoCommit(false)") -and $transaction.Contains("connection.commit()") -and $transaction.Contains("connection.rollback()")) "explicit commit and rollback"
Test-Gate "transaction.affected-row-guards" ($transaction.Contains("requireOne(statement.executeUpdate()") -and $transaction.Contains("affectedRows != 1")) "every mutation requires one row"
Test-Gate "transaction.main-subclass-sp" ($transaction.Contains("SELECT_MAIN_SP") -and $transaction.Contains("SELECT_SUBCLASS_SP") -and $transaction.Contains("class_index = ?") -and $transaction.Contains("class_id = ?")) "main and exact subclass SP paths"
Test-Gate "transaction.skill-insert-update" ($transaction.Contains("INSERT INTO character_skills") -and $transaction.Contains("UPDATE character_skills SET skill_level") -and ($transaction -notmatch '(?i)REPLACE\s+INTO\s+character_skills')) "guarded first level and upgrade without REPLACE"
Test-Gate "transaction.exact-item-object" ($backend.Contains("getAllItemsByItemId") -and $backend.Contains("Comparator.comparingInt(Item::getObjectId)") -and $transaction.Contains("WHERE object_id = ? AND owner_id = ? AND item_id = ? AND loc = ? AND count = ?")) "deterministic exact object and durable guards"
Test-Gate "transaction.timeout" ($transaction.Contains("setQueryTimeout(QUERY_TIMEOUT_SECONDS)")) "bounded JDBC query/lock wait"
Test-Gate "transaction.no-persistent-player-skill-call" ($production -notmatch 'addSkill\s*\([^;\r\n]*,\s*true\s*\)') "production progression has no addSkill(..., true)"
Test-Gate "transaction.runtime-after-commit" ($transaction.IndexOf("connection.commit()") -lt $transaction.IndexOf("player.destroyItem(") -and $transaction.IndexOf("connection.commit()") -lt $transaction.IndexOf("player.addSkill(skill, false)")) "runtime item/SP/skill apply follows commit"
Test-Gate "transaction.fresh-postconditions" ($transaction.Contains("DatabaseFactory.getConnection()") -and $transaction.Contains("BEFORE_POSTCONDITION_READ") -and $transaction.Contains("if (!freshStateMatches(identity, DurableState.expected(identity)))")) "fresh connection durability proof"
Test-Gate "transaction.fail-stop-status" ($model.Contains("DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED") -and $service.Contains("_state = State.FAILED") -and $service.Contains("_failureCategory = result.status().name()")) "postcommit invariant failure fail-stops service"
Test-Gate "transaction.typed-conflicts" ($model.Contains("DURABLE_SKILL_STATE_CONFLICT") -and $model.Contains("DURABLE_SP_STATE_CONFLICT") -and $model.Contains("DURABLE_ITEM_STATE_CONFLICT") -and $model.Contains("DURABLE_SCHEMA_OR_ROW_MISSING")) "typed durable conflict statuses"
Test-Gate "transaction.event-after-proof" ($backend.Contains("(result.status() == OperationStatus.SUCCESS) && EventDispatcher") -and $backend.IndexOf("_skillLearningTransaction.execute(") -lt $backend.IndexOf("notifyEventAsync(new OnPlayerSkillLearn")) "event is gated by completed transaction SUCCESS"
Test-Gate "transaction.no-workers" ($production -notmatch 'new\s+Thread|Executors\.|ExecutorService|ScheduledFuture|CompletableFuture|java\.util\.concurrent\.Future') "no production worker/task/future"
Test-Gate "transaction.no-packet-bypass" ($production -notmatch 'network\.(clientpackets|serverpackets)|RequestAcquireSkill|sendPacket\s*\(|bypass') "no packet or bypass simulation"

$durabilityTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionDurabilitySuite.java"
$integrationTests = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomProgressionServerIntegrationSuite.java"
$launcher = Read-Text "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java"
$build = Read-Text "build.xml"
Test-Gate "tests.seed" ($build.Contains('<property name="phantom.goal013b.seed" value="13001302"') -and $build.Contains('<arg value="${phantom.goal013b.seed}"') -and $durabilityTests.Contains("13001302L")) "exact deterministic seed"
Test-Gate "tests.fault-matrix" (($durabilityTests.Contains("BEFORE_ITEM_SQL")) -and $durabilityTests.Contains("AFTER_ITEM_SQL") -and $durabilityTests.Contains("BEFORE_SP_SQL") -and $durabilityTests.Contains("AFTER_SP_SQL") -and $durabilityTests.Contains("BEFORE_SKILL_SQL") -and $durabilityTests.Contains("AFTER_SKILL_SQL") -and $durabilityTests.Contains("BEFORE_COMMIT")) "seven precommit injection stages"
Test-Gate "tests.main-subclass-reload" ($durabilityTests.Contains("main-class-restart-proof") -and $durabilityTests.Contains("main-subclass-main-restart-proof") -and $durabilityTests.Contains("character_subclasses")) "main/subclass direct DB and reload proof"
Test-Gate "tests.exact-item" ($durabilityTests.Contains("exactItem.getObjectId()") -and $durabilityTests.Contains("readItemCount") -and $durabilityTests.Contains("exact-item-object-commits-once")) "exact runtime object and durable row assertions"
Test-Gate "tests.concurrency-autosave" ($durabilityTests.Contains("OPERATION_IN_PROGRESS") -and $durabilityTests.Contains("CompletableFuture.runAsync(_player::storeMe)") -and $durabilityTests.Contains("BEFORE_COMMIT")) "same-profile and autosave race proof"
Test-Gate "tests.fail-stop-reload" ($durabilityTests.Contains("BEFORE_POSTCONDITION_READ") -and $durabilityTests.Contains("State.FAILED") -and $durabilityTests.Contains("SERVICE_NOT_RUNNING") -and $durabilityTests.Contains("_failStopReload")) "postcommit fail-stop and reload proof"
Test-Gate "tests.typed-conflicts" ($durabilityTests.Contains("exerciseConflictMatrix") -and $durabilityTests.Contains("DURABLE_SKILL_STATE_CONFLICT") -and $durabilityTests.Contains("DURABLE_SP_STATE_CONFLICT") -and $durabilityTests.Contains("DURABLE_ITEM_STATE_CONFLICT")) "runtime/DB drift is rejected with typed results"
Test-Gate "tests.event-count" ($durabilityTests.Contains("_skillLearnEvents.get() == 3") -and $durabilityTests.Contains("ON_PLAYER_SKILL_LEARN") -and $durabilityTests.Contains("_eventsAfterPostconditions")) "three successes, no retry/failure events, durable postconditions visible"
Test-Gate "tests.real-trainer-regression" ($integrationTests.Contains("_player.storeMe()") -and $integrationTests.Contains("Real trainer CLASS learning failed.")) "existing real trainer route persists its baseline"
Test-Gate "tests.launcher-route" ($launcher.Contains('case "progression-durability" -> new PhantomProgressionDurabilitySuite()')) "focused launcher route"
Test-Gate "tests.ant-route" ($build.Contains('name="phantom-progression-durability-test"') -and $build.Contains('<arg value="progression-durability"')) "focused Ant route"
Test-Gate "tests.verify-route" ($build.Contains("phantom-progression-durability-test") -and $build.Contains("phantom-static-verify-013b") -and $build.Contains('name="verify"')) "cumulative verify includes Goal 013B"

$contract = Read-Text "docs/phantoms/architecture/PROGRESSION_CAPABILITY_CONTRACT.md"
$roadmap = Read-Text "docs/PHANTOM_BOTS_ROADMAP.md"
$reportPresent = Test-Path -LiteralPath (Join-Path $ModuleRoot "docs/phantoms/reports/013b-durable-class-skill-learning-transaction.md") -PathType Leaf
$report = if ($reportPresent) { Read-Text "docs/phantoms/reports/013b-durable-class-skill-learning-transaction.md" } else { "" }
Test-Gate "docs.contract" ($contract.Contains("PhantomClassSkillLearningTransaction") -and $contract.Contains("character_subclasses") -and $contract.Contains("fresh") -and $contract.Contains("fail-stop")) "durable ownership and failure contract"
Test-Gate "docs.roadmap" ($roadmap.Contains("Goal 013: FIX_REQUIRED after first review") -and $roadmap.Contains("Goal 013A: FIX_REQUIRED after durability review") -and $roadmap.Contains("Goal 013B: IMPLEMENTED_PENDING_INDEPENDENT_REVIEW") -and $roadmap.Contains("Goal 014: NOT_STARTED / BLOCKED") -and $roadmap.Contains("Goal 025: NOT_STARTED")) "progress truth only"
Test-Gate "docs.report" ($reportPresent -and $report.Contains("SUCCESS") -and $report.Contains("13001302") -and $report.Contains("l2jmobiush5_phantom_test")) "required execution report"

$implementationPaths = @($changed | Where-Object { ($_ -match '^(java|test)/') -and ($_ -ne "test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java") })
$implementationText = ($implementationPaths | Where-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf } | ForEach-Object { Read-Text $_ }) -join "`n"
Test-Gate "safety.test-db-only" ($implementationText -notmatch 'l2jmobiush5(?!_phantom_test)') "no production DB name in implementation"
Test-Gate "safety.no-credentials" ($implementationText -notmatch '(?i)(password\s*[=:]\s*[^<\s]|root/root)') "no embedded credentials"

$changedText = ($changed | Where-Object { Test-Path -LiteralPath (Join-Path $ModuleRoot $_) -PathType Leaf } | ForEach-Object { Read-Text $_ }) -join "`n"
$mojibakeMarkers = @(
	(-join @([char]0x0420, [char]0x045f)),
	(-join @([char]0x0420, [char]0x045c)),
	(-join @([char]0x0420, [char]0x045b)),
	(-join @([char]0x0420, [char]0x2022)),
	(-join @([char]0x0420, [char]0x040e)),
	(-join @([char]0x0420, [char]0x203a)),
	(-join @([char]0x0420, [char]0x00a4)),
	(-join @([char]0x0420, [char]0x045a)),
	(-join @([char]0x0420, [char]0x0408)),
	(-join @([char]0x0420, [char]0x0459)),
	(-join @([char]0x0420, [char]0x0491)),
	(-join @([char]0x0420, [char]0x00b5)),
	(-join @([char]0x0420, [char]0x00b0)),
	(-join @([char]0x0420, [char]0x00bb)),
	(-join @([char]0x0420, [char]0x0405)),
	(-join @([char]0x0420, [char]0x0455)),
	(-join @([char]0x0421, [char]0x040f)),
	(-join @([char]0x0421, [char]0x20ac)),
	(-join @([char]0x0421, [char]0x0402)),
	(-join @([char]0x0421, [char]0x2039)),
	(-join @([char]0x0421, [char]0x040a)),
	(-join @([char]0x0421, [char]0x201a)),
	(-join @([char]0x0421, [char]0x0453)),
	(-join @([char]0x0421, [char]0x2021)),
	(-join @([char]0x0421, [char]0x2026)),
	(-join @([char]0x0421, [char]0x2020)),
	([string][char]0xfffd)
)
$mojibakeFound = @($mojibakeMarkers | Where-Object { $changedText.Contains($_) })
$escapedPattern = '\\u04[0-9A-Fa-f]{2}|\\u05[0-9A-Fa-f]{2}|&#[xX]04[0-9A-Fa-f]{2};|&#[xX]05[0-9A-Fa-f]{2};'
Test-Gate "encoding.utf8-strict" $true "all verifier-read changed files decode as strict UTF-8"
Test-Gate "encoding.mojibake" ($mojibakeFound.Count -eq 0) "no mojibake markers in changed files"
Test-Gate "encoding.escaped-cyrillic" ($changedText -notmatch $escapedPattern) "no escaped Cyrillic in changed files"

$verifierText = Read-Text "tools/phantoms/verify-task-013b.ps1"
$mutationPattern = ("Set-" + "Content|Add-" + "Content|Out-" + "File|Remove-" + "Item|Move-" + "Item|Copy-" + "Item|git\s+(ad" + "d|com" + "mit|pu" + "sh|res" + "et|res" + "tore|check" + "out)")
Test-Gate "verifier.read-only" ($verifierText -notmatch $mutationPattern) "no filesystem/history mutation command"

$jarPath = Join-Path $ModuleRoot "dist/libs/GameServer.jar"
$jarProduction = $false
$jarTestsAbsent = $false
if (Test-Path -LiteralPath $jarPath -PathType Leaf)
{
	Add-Type -AssemblyName System.IO.Compression.FileSystem
	$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
	try
	{
		$entries = @($archive.Entries | ForEach-Object { $_.FullName })
		$jarProduction = ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.class") -and ($entries -contains "org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.class")
		$jarTestsAbsent = @($entries | Where-Object { $_ -like "org/l2jmobius/tests/phantoms/*" }).Count -eq 0
	}
	finally
	{
		$archive.Dispose()
	}
}
Test-Gate "jar.production" $jarProduction "durable transaction and backend classes present"
Test-Gate "jar.no-tests" $jarTestsAbsent "test classes absent"

$total = $Pass + $Fail
Write-Output ("SUMMARY PASS=" + $Pass + " FAIL=" + $Fail + " TOTAL=" + $total)
if ($Fail -ne 0)
{
	exit 1
}
Write-Output "VERIFY_TASK_013B_OK"
