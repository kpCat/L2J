param([ValidateRange(1,900)][int]$DurationSeconds=600,[ValidatePattern('^[a-z0-9-]+$')][string]$Segment='passive1')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime = Join-Path $module '.phantom-local/observe010/runtime'
$evidence = Join-Path $module ".phantom-local/observe010/observation/$Segment"
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$run = [guid]::NewGuid().ToString('D')
$started = [DateTimeOffset]::UtcNow
$deadline = $started.AddSeconds($DurationSeconds)
$rows = [Collections.Generic.List[object]]::new()
$sample = 0
do {
    $sample++
    foreach ($operation in @('SNAPSHOT_PHANTOMS','SELECT_VISIBLE_PHANTOM_TRACE')) {
        $json = & (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation $operation -RunId $run
        $json | Set-Content (Join-Path $evidence "$sample-$operation.json") -Encoding utf8
        $result = $json | ConvertFrom-Json
        if ($result.status -notin @('SUCCEEDED','ACCEPTED') -and $result.reason -ne 'NO_VISIBLE_MATERIALIZED_PHANTOM') { throw "Observation transport refused: $($result.reason)" }
        $data = if ($null -ne $result.PSObject.Properties['candidate']) { $result.candidate } else { [pscustomobject]@{} }
        $fields = [ordered]@{sample=$sample;utc=$result.endUtc;elapsedSeconds=[math]::Round(([DateTimeOffset]::UtcNow-$started).TotalSeconds);operation=$operation;status=$result.status;reason=$result.reason}
        foreach ($property in $data.PSObject.Properties) { $fields[$property.Name]=$property.Value }
        $rows.Add([pscustomobject]$fields)
        if ($operation -eq 'SNAPSHOT_PHANTOMS') {
            Write-Output "sample=$sample elapsed=$($fields.elapsedSeconds)s census=$($result.reason) data=$($data | ConvertTo-Json -Compress)"
        }
        if ($operation -eq 'SELECT_VISIBLE_PHANTOM_TRACE' -and $result.status -eq 'SUCCEEDED') {
            $trace = & (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation SNAPSHOT_SELECTED_PHANTOM_TRACE -RunId $run
            $trace | Set-Content (Join-Path $evidence "$sample-trace.json") -Encoding utf8
        }
    }
    $remaining = ($deadline-[DateTimeOffset]::UtcNow).TotalSeconds
    if ($remaining -gt 0) { Start-Sleep -Seconds ([math]::Min(10,[math]::Ceiling($remaining))) }
} while ([DateTimeOffset]::UtcNow -lt $deadline)
# A final snapshot at or after the ten-minute boundary.
$json = & (Join-Path $runtime 'Invoke-LocalPlayPilot.ps1') -Operation SNAPSHOT_PHANTOMS -RunId $run
$json | Set-Content (Join-Path $evidence 'final-census.json') -Encoding utf8
$json | ConvertFrom-Json | Select-Object status,reason,endUtc | ConvertTo-Json -Compress | Write-Output
$columns = @($rows | ForEach-Object { $_.PSObject.Properties.Name } | Sort-Object -Unique)
$rows | Select-Object $columns | Export-Csv (Join-Path $PSScriptRoot "OBSERVATION-$Segment.tsv") -Delimiter "`t" -NoTypeInformation -Encoding utf8
Write-Output "OBSERVATION_SECONDS=$([math]::Floor(([DateTimeOffset]::UtcNow-$started).TotalSeconds)); samples=$sample; raw evidence retained privately."
