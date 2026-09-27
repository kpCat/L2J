[CmdletBinding()]
param(
	[string] $ScenarioPath = (Join-Path $PSScriptRoot 'scenarios\pilot-smoke.xml'),
	[string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext -RequireEnabled
$session = Get-PilotSession $context
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $context.PilotRoot 'reports' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
Assert-PilotNoReparse $OutputDirectory
if (-not (Test-Path -LiteralPath $OutputDirectory)) { $null = [IO.Directory]::CreateDirectory($OutputDirectory) }

if ((Get-Item -LiteralPath $ScenarioPath).Length -gt 65536) { throw 'Scenario XML превышает 64 KiB.' }
$settings = New-Object Xml.XmlReaderSettings
$settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
$settings.XmlResolver = $null
$settings.MaxCharactersInDocument = 65536
$reader = [Xml.XmlReader]::Create($ScenarioPath, $settings)
try
{
	$scenario = New-Object Xml.XmlDocument
	$scenario.XmlResolver = $null
	$scenario.Load($reader)
}
finally { $reader.Dispose() }
if (($scenario.DocumentElement.LocalName -cne 'pilotScenario') -or ($scenario.DocumentElement.GetAttribute('version') -cne '1')) { throw 'Некорректная версия pilot scenario.' }
$maxSeconds = [int] $scenario.DocumentElement.GetAttribute('maxSeconds')
if (($maxSeconds -lt 1) -or ($maxSeconds -gt 1200)) { throw 'Scenario budget вне диапазона 1..1200 секунд.' }
$steps = @($scenario.DocumentElement.SelectNodes('segment/step'))
if (($steps.Count -eq 0) -or ($steps.Count -gt 128)) { throw 'Scenario step count вне диапазона 1..128.' }
$started = [DateTime]::UtcNow
$runDeadline = $started.AddSeconds($maxSeconds)
$runId = [guid]::NewGuid().ToString('D')
$rows = New-Object System.Collections.Generic.List[object]
$stepNumber = 0
$segmentNames = New-Object 'System.Collections.Generic.HashSet[string]'
$origin = $null
$boundClientIdentity = $null
$originPartyId = $null
$lastStatus = $null
$candidate = $null
$inviteDelivered = $false
$inviteActorObjectId = 0
$resultStatus = 'COMPLETED'
$failure = ''

function Add-ScenarioRow([string] $Segment, [string] $Step, [string] $Operation, [string] $Harness, [string] $Gameplay, [string] $Detail, [string] $RequestId = '')
{
	$script:rows.Add([pscustomobject]@{ segment = $Segment; step = $Step; operation = $Operation; requestId = $RequestId; harnessResult = $Harness; gameplayResult = $Gameplay; detail = $Detail; utc = [DateTime]::UtcNow.ToString('o') })
}

function Invoke-ScenarioAction([string] $Segment, [string] $Step, [string] $Operation, [hashtable] $Arguments = @{}, [int] $TimeoutSeconds = 30, [switch] $Cleanup)
{
	if ((-not $Cleanup) -and ([DateTime]::UtcNow.AddSeconds($TimeoutSeconds + 2) -ge $script:runDeadline)) { throw 'Scenario hard runtime budget исчерпан.' }
	$script:stepNumber++
	if ($script:stepNumber -gt 128) { throw 'Scenario превысил 128 native actions.' }
	$json = & (Join-Path $PSScriptRoot 'Invoke-LocalPlayPilot.ps1') -Operation $Operation -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -RunId $script:runId
	$result = $json | ConvertFrom-Json
	if ($null -ne $result.after)
	{
		if (($result.after.identityOwner -cne 'REAL_LOGIN') -or ($result.after.clientIdentity -ceq 'none') -or ($result.after.worldPresent -cne 'true') -or ([int] $result.after.actorObjectId -ne [int] $script:session.objectId)) { throw 'Native result lost bound REAL_LOGIN/GameClient/World identity.' }
		if ($null -eq $script:boundClientIdentity) { $script:boundClientIdentity = [string] $result.after.clientIdentity }
		elseif ($script:boundClientIdentity -cne [string] $result.after.clientIdentity) { throw 'GameClient identity changed during pilot run.' }
	}
	$harness = if ($result.harnessResult) { [string] $result.harnessResult } else { [string] $result.status }
	$gameplay = if ($result.gameplayResult) { [string] $result.gameplayResult } else { 'NOT_OBSERVED' }
	Add-ScenarioRow $Segment $Step $Operation $harness $gameplay ([string] $result.reason) ([string] $result.requestId)
	if (($result.status -eq 'UNCERTAIN') -or ($harness -eq 'UNCERTAIN')) { throw "UNCERTAIN: native action $Operation; дальнейшие actions остановлены." }
	return $result
}

function Get-AfterPosition($Result)
{
	if (($null -eq $Result.after) -or ($null -eq $Result.after.x) -or ($null -eq $Result.after.y) -or ($null -eq $Result.after.z) -or ($null -eq $Result.after.instanceId)) { throw 'STATUS не содержит native координат/instance.' }
	return [pscustomobject]@{ x = [int] $Result.after.x; y = [int] $Result.after.y; z = [int] $Result.after.z; instanceId = [int] $Result.after.instanceId }
}

function Wait-ScenarioPosition([string] $Segment, [string] $Step, $Target, [int] $WaitSeconds)
{
	$deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
	do
	{
		Start-Sleep -Seconds 2
		$status = Invoke-ScenarioAction $Segment "$Step-VERIFY" 'STATUS'
		if (($status.status -cne 'SUCCEEDED') -or ($status.after.online -cne 'true') -or ([int] $status.after.instanceId -ne $Target.instanceId)) { throw "${Step}: клиент/instance изменился во время ожидания." }
		$distance = [Math]::Sqrt(([Math]::Pow(([int] $status.after.x - $Target.x), 2)) + ([Math]::Pow(([int] $status.after.y - $Target.y), 2)))
		if (($distance -le 64) -and ([Math]::Abs([int] $status.after.z - $Target.z) -le 120) -and ($status.after.moving -cne 'true') -and ($status.after.teleporting -cne 'true'))
		{
			Add-ScenarioRow $Segment $Step 'STATUS' 'SUCCEEDED' 'NOT_OBSERVED' 'Native position and completed movement/teleport observed.'
			return
		}
	}
	while ([DateTime]::UtcNow -lt $deadline)
	throw "${Step}: TIMEOUT без подтверждённого native прибытия."
}

function Wait-ScenarioPose([string] $Segment, [string] $Step, [string] $Expected)
{
	for ($i = 0; $i -lt 8; $i++)
	{
		$status = Invoke-ScenarioAction $Segment "$Step-VERIFY" 'STATUS'
		if ($status.after.sitting -ceq $Expected) { return }
		Start-Sleep -Seconds 1
	}
	throw "${Step}: native pose не подтверждена."
}

function Test-CandidateReady($Candidate)
{
	return ($null -ne $Candidate) -and ($Candidate.materialized -ceq 'true') -and ($null -ne $Candidate.PSObject.Properties['objectId']) -and ($null -ne $Candidate.PSObject.Properties['name']) -and (-not [string]::IsNullOrWhiteSpace([string] $Candidate.objectId)) -and (-not [string]::IsNullOrWhiteSpace([string] $Candidate.name))
}

function Get-ScenarioCandidate($Result)
{
	if ($null -eq $Result.PSObject.Properties['candidate']) { return $null }
	return $Result.candidate
}

function Invoke-FailureCleanup
{
	if (($null -eq $script:origin) -or $script:failure.StartsWith('UNCERTAIN:') -or $script:failure.StartsWith('CANCELLED:'))
	{
		Add-ScenarioRow 'cleanup' 'SKIPPED' '' 'NOT_APPLICABLE' 'NOT_OBSERVED' 'Нет безопасного origin или outcome неопределён/отменён; новых actions нет.'
		return
	}
	$active = Get-PilotSession $script:context
	if (($active.sessionId -cne $script:session.sessionId) -or ($active.state -cne 'RUNNING') -or ($active.ContainsKey('stoppedRunId') -and ($active.stoppedRunId -ceq $script:runId)))
	{
		Add-ScenarioRow 'cleanup' 'SKIPPED' '' 'CANCELLED' 'NOT_OBSERVED' 'Run отозван или прерван ручным действием; персонаж не перемещается.'
		return
	}
	$status = Invoke-ScenarioAction 'cleanup' 'FAILURE_STATUS' 'STATUS' @{} 8 -Cleanup
	if (($status.status -cne 'SUCCEEDED') -or ($status.after.online -cne 'true') -or ([double]::Parse([string] $status.after.hp, [Globalization.CultureInfo]::InvariantCulture) -le 0) -or ([int] $status.after.instanceId -ne $script:origin.instanceId))
	{
		Add-ScenarioRow 'cleanup' 'SKIPPED' '' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'Клиент offline, персонаж без HP или instance изменился; cleanup не выполняется.'
		return
	}
	if ($script:inviteDelivered -and ([long] $status.after.partyId -ne 0))
	{
		if (($script:originPartyId -ne 0) -or ([long] $status.after.partyId -ne $script:inviteActorObjectId)) { throw 'Party не соответствует сценарию; native leave и перемещение не выполняются.' }
		$leave = Invoke-ScenarioAction 'cleanup' 'FAILURE_PARTY_LEAVE' 'PARTY_LEAVE' @{} 8 -Cleanup
		if ($leave.status -cne 'SUCCEEDED') { throw 'Native PARTY_LEAVE отказал; состав party мог измениться.' }
		$status = Invoke-ScenarioAction 'cleanup' 'FAILURE_PARTY_VERIFY' 'STATUS' @{} 8 -Cleanup
		if ([long] $status.after.partyId -ne 0) { throw 'Party не освобождена после native leave.' }
	}
	if ($status.after.teleporting -ceq 'true')
	{
		Add-ScenarioRow 'cleanup' 'POSITION_SKIPPED' '' 'NOT_OBSERVED' 'NOT_OBSERVED' 'Телепорт ещё идёт; дополнительное перемещение не выполняется.'
		return
	}
	if ($status.after.moving -ceq 'true')
	{
		$halt = Invoke-ScenarioAction 'cleanup' 'FAILURE_STOP_MOVE' 'STOP_MOVE' @{} 8 -Cleanup
		if ($halt.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native STOP_MOVE отказал; телепорт cleanup не выполняется.' }
		$status = Invoke-ScenarioAction 'cleanup' 'FAILURE_STOP_VERIFY' 'STATUS' @{} 8 -Cleanup
		if ($status.after.moving -ceq 'true') { throw 'Движение не остановилось; телепорт cleanup не выполняется.' }
	}
	$distance = [Math]::Sqrt(([Math]::Pow(([int] $status.after.x - $script:origin.x), 2)) + ([Math]::Pow(([int] $status.after.y - $script:origin.y), 2)))
	if (($distance -le 64) -and ([Math]::Abs([int] $status.after.z - $script:origin.z) -le 120)) { return }
	$back = Invoke-ScenarioAction 'cleanup' 'FAILURE_RETURN' 'TELEPORT_SELF' @{ x = $script:origin.x; y = $script:origin.y; z = $script:origin.z; instanceId = $script:origin.instanceId } 8 -Cleanup
	if ($back.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native TELEPORT_SELF cleanup отказал.' }
	for ($attempt = 0; $attempt -lt 3; $attempt++)
	{
		Start-Sleep -Seconds 2
		$status = Invoke-ScenarioAction 'cleanup' 'FAILURE_RETURN_VERIFY' 'STATUS' @{} 8 -Cleanup
		if (($status.after.online -cne 'true') -or ([int] $status.after.instanceId -ne $script:origin.instanceId)) { throw 'Клиент/instance изменился во время cleanup.' }
		$distance = [Math]::Sqrt(([Math]::Pow(([int] $status.after.x - $script:origin.x), 2)) + ([Math]::Pow(([int] $status.after.y - $script:origin.y), 2)))
		if (($distance -le 64) -and ([Math]::Abs([int] $status.after.z - $script:origin.z) -le 120) -and ($status.after.moving -cne 'true') -and ($status.after.teleporting -cne 'true')) { return }
	}
	throw 'Native position не вернулась в origin за bounded cleanup window.'
}

try
{
	Write-PilotHeartbeat $context ([string] $session.sessionId) $runId
	foreach ($segment in $scenario.DocumentElement.SelectNodes('segment'))
	{
		$name = $segment.GetAttribute('name')
		if (($name -cnotmatch '^[a-z][a-z0-9-]{0,31}$') -or (-not $segmentNames.Add($name))) { throw 'Некорректное или повторное имя segment.' }
		foreach ($step in $segment.SelectNodes('step'))
		{
			$kind = $step.GetAttribute('kind')
			switch ($kind)
			{
				'STATUS'
				{
					$lastStatus = Invoke-ScenarioAction $name $kind 'STATUS'
					if ($lastStatus.status -cne 'SUCCEEDED') { throw 'Native STATUS не завершён успешно.' }
					if ($null -eq $origin)
					{
						$origin = Get-AfterPosition $lastStatus
						$originPartyId = [long] $lastStatus.after.partyId
						if ($lastStatus.after.online -cne 'true') { throw 'Привязанный клиент offline.' }
						if ($originPartyId -ne 0) { throw 'Пилот уже состоит в чужой party.' }
					}
					break
				}
				'STATUS_BURST'
				{
					$count = [int] $step.GetAttribute('count')
					$intervalMs = [int] $step.GetAttribute('intervalMs')
					if (($count -lt 1) -or ($count -gt 20) -or ($intervalMs -lt 0) -or ($intervalMs -gt 1000)) { throw 'Некорректный STATUS_BURST.' }
					for ($i = 0; $i -lt $count; $i++)
					{
						$lastStatus = Invoke-ScenarioAction $name "$kind-$i" 'STATUS'
						if ($lastStatus.status -cne 'SUCCEEDED' -or $lastStatus.after.online -cne 'true') { throw 'STATUS_BURST потерял клиент или native status.' }
						if ($i -lt ($count - 1)) { Start-Sleep -Milliseconds $intervalMs }
					}
					break
				}
				'SIT_STAND'
				{
					$sit = Invoke-ScenarioAction $name 'SIT' 'SIT'
					if ($sit.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native SIT отклонён.' }
					Wait-ScenarioPose $name 'SIT' 'true'
					Start-Sleep -Seconds 3
					$stand = Invoke-ScenarioAction $name 'STAND' 'STAND'
					if ($stand.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native STAND отклонён.' }
					Wait-ScenarioPose $name 'STAND' 'false'
					break
				}
				'MOVE_RETURN'
				{
					if ($null -eq $origin) { throw 'MOVE_RETURN без origin STATUS.' }
					$distance = [int] $step.GetAttribute('distance')
					if (($distance -lt 20) -or ($distance -gt 100)) { throw 'MOVE_RETURN distance вне безопасного диапазона.' }
					$move = Invoke-ScenarioAction $name 'MOVE_OUT' 'MOVE_SELF' @{ x = ($origin.x + $distance); y = $origin.y; z = $origin.z } 45
					if ($move.status -notin @('SUCCEEDED', 'ACCEPTED')) { Add-ScenarioRow $name 'MOVE_RETURN' 'MOVE_SELF' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'Native move отказан; teleport fallback не выполняется.'; break }
					Wait-ScenarioPosition $name 'MOVE_OUT' ([pscustomobject]@{ x = ($origin.x + $distance); y = $origin.y; z = $origin.z; instanceId = $origin.instanceId }) 20
					$return = Invoke-ScenarioAction $name 'MOVE_BACK' 'MOVE_SELF' @{ x = $origin.x; y = $origin.y; z = $origin.z } 45
					if ($return.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native move return отклонён.' }
					Wait-ScenarioPosition $name 'MOVE_BACK' $origin 20
					break
				}
				'TELEPORT_RETURN'
				{
					if ($null -eq $origin) { throw 'TELEPORT_RETURN без origin STATUS.' }
					$distance = [int] $step.GetAttribute('distance')
					if (($distance -lt 20) -or ($distance -gt 100)) { throw 'TELEPORT_RETURN distance вне безопасного диапазона.' }
					$to = Invoke-ScenarioAction $name 'TELEPORT_OUT' 'TELEPORT_SELF' @{ x = ($origin.x + $distance); y = $origin.y; z = $origin.z; instanceId = $origin.instanceId } 45
					if ($to.status -notin @('SUCCEEDED', 'ACCEPTED')) { Add-ScenarioRow $name 'TELEPORT_RETURN' 'TELEPORT_SELF' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'Native teleport отказан; возврат не нужен.'; break }
					Wait-ScenarioPosition $name 'TELEPORT_OUT' ([pscustomobject]@{ x = ($origin.x + $distance); y = $origin.y; z = $origin.z; instanceId = $origin.instanceId }) 20
					$back = Invoke-ScenarioAction $name 'TELEPORT_BACK' 'TELEPORT_SELF' @{ x = $origin.x; y = $origin.y; z = $origin.z; instanceId = $origin.instanceId } 45
					if ($back.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Native teleport return отклонён; дальнейшие actions остановлены.' }
					Wait-ScenarioPosition $name 'TELEPORT_BACK' $origin 20
					break
				}
				'PHANTOM_CONTACT'
				{
					$snapshot = Invoke-ScenarioAction $name 'SNAPSHOT_PHANTOMS' 'SNAPSHOT_PHANTOMS'
					$candidate = Get-ScenarioCandidate $snapshot
					if (($snapshot.status -ne 'SUCCEEDED') -or ($null -eq $candidate)) { Add-ScenarioRow $name $kind 'SNAPSHOT_PHANTOMS' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'NO_CANDIDATE: бот не создаётся и не materialize принудительно.'; break }
					if ([int] $candidate.instanceId -ne $origin.instanceId) { Add-ScenarioRow $name $kind 'SNAPSHOT_PHANTOMS' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'Кандидат в другом instance.'; break }
					$near = Invoke-ScenarioAction $name 'CONTACT_POSITION' 'TELEPORT_SELF' @{ x = [int] $candidate.x; y = [int] $candidate.y; z = [int] $candidate.z; instanceId = $origin.instanceId } 45
					if ($near.status -notin @('SUCCEEDED', 'ACCEPTED')) { Add-ScenarioRow $name $kind 'TELEPORT_SELF' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' 'Позиционирование к кандидату отклонено.'; break }
					Wait-ScenarioPosition $name 'CONTACT_POSITION' ([pscustomobject]@{ x = [int] $candidate.x; y = [int] $candidate.y; z = [int] $candidate.z; instanceId = $origin.instanceId }) 20
					for ($i = 0; $i -lt 4; $i++)
					{
						$snapshot = Invoke-ScenarioAction $name 'CONTACT_OBSERVE' 'SNAPSHOT_PHANTOMS'
						if (Test-CandidateReady (Get-ScenarioCandidate $snapshot)) { $candidate = $snapshot.candidate; break }
						Start-Sleep -Seconds 5
					}
					if (-not (Test-CandidateReady $candidate))
					{
						Add-ScenarioRow $name $kind 'SNAPSHOT_PHANTOMS' 'NOT_OBSERVED' 'NOT_OBSERVED' 'Кандидат не materialized; адресный chat/invite неприменим без живого Player.'
					}
					else
					{
						$chat = Invoke-ScenarioAction $name 'SAY_TO_PHANTOM' 'SAY' @{ channel = 'WHISPER'; target = [string] $candidate.name; text = 'Привет! Проверяю связь.' } 30
						if ($chat.status -eq 'ACCEPTED')
						{
							Start-Sleep -Seconds 10
							Add-ScenarioRow $name 'BOT_REPLY' 'SAY' 'SUCCEEDED' 'NOT_OBSERVED' 'Native chat dispatch принят; ответ бота не подтверждён доступным snapshot после 10 секунд.'
						}
						$invite = Invoke-ScenarioAction $name 'PARTY_INVITE' 'PARTY_INVITE' @{ targetObjectId = [int] $candidate.objectId; distributionTypeId = 0 } 30
						if ($invite.status -eq 'SUCCEEDED')
						{
							$inviteDelivered = $true
							$inviteActorObjectId = [long] $invite.actorObjectId
							Start-Sleep -Seconds 10
							$partyStatus = Invoke-ScenarioAction $name 'PARTY_OBSERVE' 'STATUS'
							if ($partyStatus.after.partyId -and ([long] $partyStatus.after.partyId -ne 0))
							{
								if (([long] $partyStatus.after.partyId -ne [long] $invite.actorObjectId) -or ([long] $originPartyId -ne 0)) { throw 'Новая party не соответствует пилотскому invitation owner; cleanup остановлен.' }
								Add-ScenarioRow $name 'BOT_ACCEPT' 'PARTY_INVITE' 'SUCCEEDED' 'SUCCEEDED' 'Новая party пилотского лидера наблюдалась через native STATUS.'
								$leave = Invoke-ScenarioAction $name 'PARTY_LEAVE' 'PARTY_LEAVE'
								if ($leave.status -cne 'SUCCEEDED') { throw 'Native PARTY_LEAVE отклонён: состав party изменился или больше не принадлежит сценарию.' }
								$afterLeave = Invoke-ScenarioAction $name 'PARTY_LEFT_VERIFY' 'STATUS'
								if ([long] $afterLeave.after.partyId -ne 0) { throw 'Native PARTY_LEAVE не освободил пилота от party.' }
							}
							else { Add-ScenarioRow $name 'BOT_ACCEPT' 'PARTY_INVITE' 'SUCCEEDED' 'GAMEPLAY_GAP' 'Invitation доставлено, принятие за 10 секунд не наблюдалось.' }
						}
					}
					$back = Invoke-ScenarioAction $name 'CONTACT_RETURN' 'TELEPORT_SELF' @{ x = $origin.x; y = $origin.y; z = $origin.z; instanceId = $origin.instanceId } 45
					if ($back.status -notin @('SUCCEEDED', 'ACCEPTED')) { throw 'Возврат после контакта отклонён.' }
					Wait-ScenarioPosition $name 'CONTACT_RETURN' $origin 20
					break
				}
				'OPTIONAL_COMBAT'
				{
					Add-ScenarioRow $name 'CAST_LEARNED_SKILL' 'CAST_LEARNED_SKILL' 'NOT_APPLICABLE' 'NOT_OBSERVED' 'Нет подтверждённого уже выученного навыка и законной безопасной цели; skill не выдаётся.'
					Add-ScenarioRow $name 'ATTACK_NPC' 'ATTACK_NPC' 'NOT_APPLICABLE' 'NOT_OBSERVED' 'Нет явно разрешённой безопасной monster target; PvP/raid не допускается.'
					break
				}
				'IDLE_CHECK'
				{
					$seconds = [int] $step.GetAttribute('seconds')
					if (($seconds -lt 60) -or ($seconds -gt 120)) { throw 'IDLE_CHECK вне диапазона 60..120 секунд.' }
					for ($elapsed = 0; $elapsed -lt $seconds; $elapsed += 10)
					{
						Start-Sleep -Seconds ([Math]::Min(10, $seconds - $elapsed))
						Write-PilotHeartbeat $context ([string] $session.sessionId) $runId
					}
					$lastStatus = Invoke-ScenarioAction $name 'AFTER_IDLE' 'STATUS'
					if ($lastStatus.after.online -cne 'true') { throw 'Клиент offline после idle interval.' }
					Add-ScenarioRow $name $kind 'STATUS' 'SUCCEEDED' 'NOT_OBSERVED' "Наблюдалось online после $seconds секунд; более длинный AFK threshold не доказан."
					break
				}
				default { throw "Неподдерживаемый scenario step: $kind" }
				}
		}
	}
	if ($inviteDelivered)
	{
		$finalParty = Invoke-ScenarioAction 'cleanup' 'PARTY_FINAL_OBSERVE' 'STATUS'
		if ([long] $finalParty.after.partyId -ne 0)
		{
			if (([long] $finalParty.after.partyId -ne $inviteActorObjectId) -or ($originPartyId -ne 0)) { throw 'Финальная party не соответствует пилотскому invitation owner; cleanup остановлен.' }
			$leave = Invoke-ScenarioAction 'cleanup' 'PARTY_LEAVE' 'PARTY_LEAVE'
			if ($leave.status -cne 'SUCCEEDED') { throw 'Финальный native PARTY_LEAVE отклонён: party больше не принадлежит сценарию.' }
			$afterLeave = Invoke-ScenarioAction 'cleanup' 'PARTY_LEFT_VERIFY' 'STATUS'
			if ([long] $afterLeave.after.partyId -ne 0) { throw 'Финальный native PARTY_LEAVE не освободил пилота.' }
		}
	}
}
catch
{
	$resultStatus = 'STOPPED_ON_FAILURE'
	$failure = $_.Exception.Message
	$classification = if ($failure.StartsWith('UNCERTAIN:')) { 'UNCERTAIN' } elseif ($failure.StartsWith('CANCELLED:')) { 'CANCELLED' } elseif ($failure.Contains('TIMEOUT')) { 'TIMEOUT' } else { 'HARNESS_BUG' }
	Add-ScenarioRow 'runner' 'failure' '' $classification 'NOT_OBSERVED' $failure
}
finally
{
	if ($resultStatus -ceq 'STOPPED_ON_FAILURE')
	{
		try { Invoke-FailureCleanup }
		catch
		{
			$cleanupFailure = $_.Exception.Message
			Add-ScenarioRow 'cleanup' 'failure' '' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' $cleanupFailure
			$failure += "; cleanup: $cleanupFailure"
		}
	}
	try
	{
		$stopJson = & (Join-Path $PSScriptRoot 'Stop-LocalPlayPilot.ps1')
		$stopResult = $stopJson | ConvertFrom-Json
		$confirmed = $false
		for ($attempt = 0; $attempt -lt 10; $attempt++)
		{
			$afterStop = (& (Join-Path $PSScriptRoot 'Get-LocalPlayPilot.ps1')) | ConvertFrom-Json
			if ($afterStop.state -cin @('ARMED_IDLE', 'OFF')) { $confirmed = $true; break }
			Start-Sleep -Milliseconds 250
		}
		if (-not $confirmed) { throw "Stop не подтверждён: $($stopResult.state); состояние $($afterStop.state)." }
		Add-ScenarioRow 'runner' 'stop' '' 'SUCCEEDED' 'NOT_OBSERVED' "Runner остановлен; session state $($afterStop.state)."
	}
	catch
	{
		$stopFailure = $_.Exception.Message
		$resultStatus = 'STOPPED_ON_FAILURE'
		$failure += "; stop: $stopFailure"
		Add-ScenarioRow 'runner' 'stop' '' 'UNCERTAIN' 'NOT_OBSERVED' $stopFailure
	}
	$heartbeatPath = Join-Path $context.PilotRoot 'heartbeat.properties'
	if (Test-Path -LiteralPath $heartbeatPath)
	{
		try
		{
			$heartbeat = Read-PilotProperties $heartbeatPath
			if (($heartbeat.runId -ceq $runId) -and ($heartbeat.sessionId -ceq $session.sessionId)) { Remove-Item -LiteralPath $heartbeatPath -Force }
		}
		catch { Add-ScenarioRow 'runner' 'heartbeat-cleanup' '' 'ENVIRONMENT_BLOCKED' 'NOT_OBSERVED' $_.Exception.Message }
	}
	$reportId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + $runId.Substring(0, 8)
	$tsvPath = Join-Path $OutputDirectory "pilot-$reportId-RESULTS.tsv"
	$reportPath = Join-Path $OutputDirectory "pilot-$reportId-REPORT.md"
	$environmentBlocked = @($rows | Where-Object { $_.harnessResult -eq 'ENVIRONMENT_BLOCKED' }).Count
	$gameplayGaps = @($rows | Where-Object { $_.gameplayResult -eq 'GAMEPLAY_GAP' }).Count
	if (($resultStatus -eq 'COMPLETED') -and (($environmentBlocked -gt 0) -or ($gameplayGaps -gt 0))) { $resultStatus = 'COMPLETED_WITH_GAPS' }
	$columns = @('segment', 'step', 'operation', 'requestId', 'harnessResult', 'gameplayResult', 'detail', 'utc')
	$lines = New-Object System.Collections.Generic.List[string]
	$lines.Add(($columns -join "`t"))
	foreach ($row in $rows)
	{
		$values = foreach ($column in $columns) { ([string] $row.$column).Replace("`t", ' ').Replace("`r", ' ').Replace("`n", ' ') }
		$lines.Add(($values -join "`t"))
	}
	$utf8 = New-Object Text.UTF8Encoding($false)
	[IO.File]::WriteAllLines($tsvPath, $lines, $utf8)
	$report = @(
		'# LocalPlay pilot scenario report',
		'',
		"- Status: $resultStatus",
		"- Started UTC: $($started.ToString('o'))",
		"- Ended UTC: $([DateTime]::UtcNow.ToString('o'))",
		"- Owned Game PID: $($context.Pid)",
		"- Actor objectId: $($session.objectId)",
		"- Native actions: $stepNumber",
		"- Segments: $($segmentNames.Count)",
		"- Environment blocked subcases: $environmentBlocked",
		"- Gameplay gaps: $gameplayGaps",
		"- Failure: $failure",
		"- Results: $(Split-Path -Leaf $tsvPath)",
		'',
		'Native server outcomes are separate from bot response and client rendering. Stop confirmation is recorded in RESULTS.tsv.'
	)
	[IO.File]::WriteAllLines($reportPath, $report, $utf8)
	[pscustomobject]@{ status = $resultStatus; report = $reportPath; results = $tsvPath; actions = $stepNumber; segments = $segmentNames.Count; failure = $failure } | ConvertTo-Json -Compress
}
