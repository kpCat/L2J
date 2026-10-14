$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
& (Join-Path $module '.phantom-local/ops044/Control032.ps1') -Action DryPath -Episode a -OriginPoint @{x=44131;y=42673;z=-3488} -EndpointPoint @{x=38131;y=42673} -OutputRoot (Join-Path $PSScriptRoot 'evidence/G4_DRY044')
exit $LASTEXITCODE
