$ErrorActionPreference = 'Stop'
$module = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$runtime = Join-Path $module '.phantom-local/observe010/runtime'
$root = Join-Path $module '.phantom-local/observe010/observation'
$rows = @(foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -Filter '*.json') {
    $r = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    $utc = if ($r.endUtc -is [DateTime]) { $r.endUtc.ToUniversalTime().ToString('o') } else { [string]$r.endUtc }
    $row = [ordered]@{utc=$utc;operation=$r.operation;status=$r.status;reason=$r.reason;actorObjectId=$r.actorObjectId;online=$r.after.online;identityOwner=$r.after.identityOwner;x=$r.after.x;y=$r.after.y;z=$r.after.z}
    if ($null -ne $r.PSObject.Properties['candidate']) {
        foreach ($p in $r.candidate.PSObject.Properties) { $row['candidate_'+$p.Name]=$p.Value }
    }
    [pscustomobject]$row
})
$columns = @($rows | ForEach-Object { $_.PSObject.Properties.Name } | Sort-Object -Unique)
$rows | Sort-Object utc | Select-Object $columns | Export-Csv (Join-Path $PSScriptRoot 'OBSERVATION.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
$visible = @($rows | Where-Object { $_.operation -eq 'SELECT_VISIBLE_PHANTOM_TRACE' -and $_.status -eq 'SUCCEEDED' })
Write-Output "RAW_SAMPLES=$($rows.Count) VISIBLE_PHANTOM_SELECTIONS=$($visible.Count)"
# Publish actor/session-boundary evidence without reusable private session identifiers.
$records = @(foreach ($file in Get-ChildItem -LiteralPath (Join-Path $runtime 'playtest-pilot/results') -Filter '*.xml') {
    [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
    $r = $xml.pilotResult
    if ($r.operation -in @('STATUS','PREPARE_M1_ENVELOPE','SNAPSHOT_M1_ENVELOPE','TELEPORT_SELF')) {
        $row = [ordered]@{utc=$r.endUtc;operation=$r.operation;status=$r.status;reason=$r.reason;actorObjectId=$r.actorObjectId}
        foreach ($element in @('before','after','candidate')) {
            if ($null -ne $r.$element) { foreach ($attribute in $r.$element.Attributes) { $row[$element+'_'+$attribute.Name]=$attribute.Value } }
        }
        [pscustomobject]$row
    }
})
$columns = @($records | ForEach-Object { $_.PSObject.Properties.Name } | Sort-Object -Unique)
$records | Sort-Object utc | Select-Object $columns | Export-Csv (Join-Path $PSScriptRoot 'LOGIN-ENVELOPE.tsv') -Delimiter "`t" -NoTypeInformation -Encoding utf8
