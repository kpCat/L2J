param([Parameter(Mandatory)][string[]]$GitArgs)
$ErrorActionPreference='Stop'
if($GitArgs[0] -cnotin @('rev-parse','show')){throw 'Only committed identity/catalog reads are allowed in runtime tools.'}
$root='C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
& git -C $root @GitArgs
if($LASTEXITCODE -ne 0){throw 'Committed source read failed.'}
