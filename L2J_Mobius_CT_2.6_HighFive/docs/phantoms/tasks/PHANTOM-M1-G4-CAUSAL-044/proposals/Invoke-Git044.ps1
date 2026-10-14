param([Parameter(Mandatory)][string[]]$GitArgs)
$ErrorActionPreference='Stop'
$repo='C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
if($GitArgs[0] -cnotin @('status','rev-parse','ls-remote','diff','show','add','commit','push')){throw 'Operation outside HANDOFF044'}
if($GitArgs -contains '--force' -or $GitArgs -contains '--amend' -or $GitArgs -contains '-A'){throw 'Forbidden Git option'}
$utc=[DateTime]::UtcNow.ToString('O')
$result=@(& git -C $repo @GitArgs 2>&1 | ForEach-Object {"$_"})
$code=$LASTEXITCODE
@{utc=$utc;argv=@('git','-C',$repo)+$GitArgs;exitCode=$code;output=$result} | ConvertTo-Json -Depth 5 -Compress | Add-Content (Join-Path $repo 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops044/GIT044.jsonl') -Encoding utf8
$result | Write-Output
exit $code
