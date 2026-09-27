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
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) }
finally { $random.Dispose() }
$nonce = ([BitConverter]::ToString($bytes, 0, 24)).Replace('-', '').ToLowerInvariant()
$alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
$codeChars = New-Object char[] 8
for ($index = 0; $index -lt 8; $index++) { $codeChars[$index] = $alphabet[[int] ($bytes[24 + $index] -band 31)] }
$armCode = -join $codeChars
$sha = [Security.Cryptography.SHA256]::Create()
try { $armCodeSha256 = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($armCode)))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
$expiry = [DateTimeOffset]::UtcNow.AddMinutes($ArmMinutes).ToUnixTimeMilliseconds()
$record = "version=2`nnonce=$nonce`narmCodeSha256=$armCodeSha256`nexpectedName=$ExpectedName`npid=$($context.Pid)`nstartTimeUtcTicks=$($context.StartTimeUtcTicks)`nruntimeId=$($context.RuntimeId)`nexpiresUtcMillis=$expiry`n"
Write-PilotAtomicText (Join-Path $context.PilotRoot 'arm.properties') $record -Replace
[pscustomobject]@{
	state = 'WAITING_ARM'
	expectedName = $ExpectedName
	armCommand = ".playtest arm $armCode"
	expiresUtc = [DateTimeOffset]::FromUnixTimeMilliseconds($expiry).UtcDateTime.ToString('o')
	gamePid = $context.Pid
} | ConvertTo-Json -Compress
