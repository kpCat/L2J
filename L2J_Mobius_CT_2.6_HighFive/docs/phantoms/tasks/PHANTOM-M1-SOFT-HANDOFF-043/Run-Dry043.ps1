$ErrorActionPreference='Stop'
$module043=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
& (Join-Path $module043 '.phantom-local/ops043/Control032.ps1') -Action DryPath -Episode a -OriginPoint @{x=44131;y=42673;z=-3488} -EndpointPoint @{x=38131;y=42673} -OutputRoot (Join-Path $PSScriptRoot 'evidence/G4_DRY043')
exit $LASTEXITCODE
