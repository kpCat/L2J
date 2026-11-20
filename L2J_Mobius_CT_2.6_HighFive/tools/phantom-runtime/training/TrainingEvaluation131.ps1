[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Manifest,
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$Output
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

foreach ($candidatePath in @($Manifest, $EvidenceRoot, $Output)) {
    if (![IO.Path]::IsPathRooted($candidatePath)) {
        throw 'TASK131 evaluation paths must be absolute.'
    }
}

$sourceRoot = Join-Path (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path 'test/java/org/l2jmobius/tests/phantoms'
$reader = Join-Path $sourceRoot 'PhantomLearning131Evaluation.java'
$comparator = Join-Path $sourceRoot 'TrainingEvaluation131.java'
$readerSha = (Get-FileHash -LiteralPath $reader -Algorithm SHA256).Hash.ToLowerInvariant()
$comparatorSha = (Get-FileHash -LiteralPath $comparator -Algorithm SHA256).Hash.ToLowerInvariant()
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $evaluatorSha = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::ASCII.GetBytes("${readerSha}:${comparatorSha}"))).Replace('-', '').ToLowerInvariant()
} finally {
    $hasher.Dispose()
}

$java = (Get-Command java -CommandType Application).Source
# JDK 25 source-file mode keeps the verifier independent of previous build output.
& $java --source 25 $reader $Manifest $EvidenceRoot $Output $evaluatorSha
exit $LASTEXITCODE
