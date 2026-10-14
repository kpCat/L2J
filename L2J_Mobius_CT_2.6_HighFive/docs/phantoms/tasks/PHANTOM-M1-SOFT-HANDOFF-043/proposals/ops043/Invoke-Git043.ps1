param([Parameter(Mandatory)][string[]]$GitArgs)
$ErrorActionPreference='Stop'
$repo043='C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
$allowed043=@('status','rev-parse','branch','worktree','log','show','diff','ls-files','check-attr','ls-remote','merge-base','archive','add','commit','push')
if ($GitArgs[0] -notin $allowed043) { throw 'Git operation not authorized by HANDOFF043.' }
if ($GitArgs -contains '--force' -or $GitArgs -contains '--amend' -or $GitArgs -contains '-A') { throw 'Prohibited Git option.' }
$utc043=[DateTime]::UtcNow.ToString('o')
$result043=@(& git -C $repo043 @GitArgs 2>&1 | ForEach-Object { "$_" })
$exit043=$LASTEXITCODE
$record043=[ordered]@{utc=$utc043;argv=@('git','-C',$repo043)+$GitArgs;exitCode=$exit043;output=$result043}
$record043 | ConvertTo-Json -Compress -Depth 5 | Add-Content -LiteralPath (Join-Path $PSScriptRoot 'GIT043.jsonl') -Encoding utf8
$result043 | Write-Output
exit $exit043
