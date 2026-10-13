param([Parameter(Mandatory)][string[]]$GitArgs)
$ErrorActionPreference='Stop'
$repo042='C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
$allowed042=@('status','rev-parse','branch','worktree','log','show','diff','ls-files','check-attr','ls-remote','merge-base','archive','add','commit','push')
if ($GitArgs[0] -notin $allowed042) { throw 'Git operation not authorized by HANDOFF042.' }
if ($GitArgs -contains '--force' -or $GitArgs -contains '--amend' -or $GitArgs -contains '-A') { throw 'Prohibited Git option.' }
$utc042=[DateTime]::UtcNow.ToString('o')
$result042=@(& git -C $repo042 @GitArgs 2>&1 | ForEach-Object { "$_" })
$exit042=$LASTEXITCODE
$record042=[ordered]@{utc=$utc042;argv=@('git','-C',$repo042)+$GitArgs;exitCode=$exit042;output=$result042}
$record042 | ConvertTo-Json -Compress -Depth 5 | Add-Content -LiteralPath (Join-Path $PSScriptRoot 'GIT042.jsonl') -Encoding utf8
$result042 | Write-Output
exit $exit042
