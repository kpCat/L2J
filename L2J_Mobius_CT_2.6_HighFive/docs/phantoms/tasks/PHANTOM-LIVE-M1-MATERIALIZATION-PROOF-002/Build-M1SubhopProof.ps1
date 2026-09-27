[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$inputPath = Join-Path $PSScriptRoot 'ROUTE_CANDIDATES.tsv'
$probeInput = Join-Path $PSScriptRoot 'adaptive-geo-input.tsv'
$rows = Import-Csv -LiteralPath $inputPath -Delimiter "`t"
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('connector_id' + "`t" + 'from_x' + "`t" + 'from_y' + "`t" + 'from_z' + "`t" + 'from_instance' + "`t" + 'to_x' + "`t" + 'to_y' + "`t" + 'to_z' + "`t" + 'to_instance')
foreach ($row in $rows)
{
	$lines.Add(($row.PSObject.Properties.Value -join "`t"))
	if ($row.connector_id -notmatch '^(route_[3-6]|return_[1-4])$') { continue }
	$previous = @([int]$row.from_x, [int]$row.from_y, [int]$row.from_z)
	for ($part = 1; $part -le 4; $part++)
	{
		$next = @(
			[int][Math]::Round([int]$row.from_x + (([int]$row.to_x - [int]$row.from_x) * $part / 4.0)),
			[int][Math]::Round([int]$row.from_y + (([int]$row.to_y - [int]$row.from_y) * $part / 4.0)),
			[int][Math]::Round([int]$row.from_z + (([int]$row.to_z - [int]$row.from_z) * $part / 4.0))
		)
		$lines.Add((@("$($row.connector_id)_$part", $previous[0], $previous[1], $previous[2], 0, $next[0], $next[1], $next[2], 0) -join "`t"))
		$previous = $next
	}
}
[IO.File]::WriteAllLines($probeInput, $lines, [Text.UTF8Encoding]::new($false))
& (Join-Path $module 'tools/phantom-world-data/Probe-FinalGeo.ps1') -WorkDirectory $PSScriptRoot -SkipCompile -ProofOnly
if ($LASTEXITCODE -ne 0) { throw 'GeoEngine proof failed.' }
$build = [IO.Path]::GetFullPath((Join-Path $module '../build'))
$libs = @(Get-ChildItem (Join-Path $module 'dist/libs') -Filter '*.jar' -File | Sort-Object Name | ForEach-Object { $_.FullName })
$classpath = (@((Join-Path $build 'bin'), (Join-Path $build 'phantom-test/bin')) + $libs) -join ';'
Push-Location (Join-Path $module 'dist/game')
try
{
	& java -cp $classpath org.l2jmobius.tests.phantoms.PhantomContentAnchorGeoProbe (Join-Path $PSScriptRoot 'ANCHOR_CANDIDATE.tsv') (Join-Path $PSScriptRoot 'ANCHOR_PROOF.tsv')
	if ($LASTEXITCODE -ne 0) { throw 'Native anchor probe failed.' }
}
finally { Pop-Location }
