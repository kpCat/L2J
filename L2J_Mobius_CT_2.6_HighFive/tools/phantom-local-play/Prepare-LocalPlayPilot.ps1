[CmdletBinding()]
param(
	[ValidatePattern('^[\p{L}\p{N}_-]{1,32}$')][string] $ExpectedName = 'TestAdmin',
	[ValidateRange(1, 10)][int] $ArmMinutes = 10
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')

$context = Get-PilotContext -RequireEnabled
Initialize-PilotMailbox $context
$sessionPath = Join-Path $context.PilotRoot 'session.properties'
if (Test-Path -LiteralPath $sessionPath)
{
	$prior = Read-PilotProperties $sessionPath
	if ($prior.version -cne '1') { throw 'Некорректная существующая pilot session.' }
	if (([int] $prior.pid -eq $context.Pid) -and ([long] $prior.startTimeUtcTicks -eq $context.StartTimeUtcTicks) -and ([long] $prior.expiresUtcMillis -gt [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
	{
		throw 'Pilot session уже активна. Сначала выполните .playtest off.'
	}
}
$bytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) }
finally { $random.Dispose() }
$nonce = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
$expiry = [DateTimeOffset]::UtcNow.AddMinutes($ArmMinutes).ToUnixTimeMilliseconds()
$record = "version=1`nnonce=$nonce`nexpectedName=$ExpectedName`npid=$($context.Pid)`nstartTimeUtcTicks=$($context.StartTimeUtcTicks)`nruntimeId=$($context.RuntimeId)`nexpiresUtcMillis=$expiry`n"
Write-PilotAtomicText (Join-Path $context.PilotRoot 'arm.properties') $record -Replace
[pscustomobject]@{
	state = 'WAITING_ARM'
	expectedName = $ExpectedName
	armCommand = ".playtest arm $nonce"
	expiresUtc = [DateTimeOffset]::FromUnixTimeMilliseconds($expiry).UtcDateTime.ToString('o')
	gamePid = $context.Pid
} | ConvertTo-Json -Compress
