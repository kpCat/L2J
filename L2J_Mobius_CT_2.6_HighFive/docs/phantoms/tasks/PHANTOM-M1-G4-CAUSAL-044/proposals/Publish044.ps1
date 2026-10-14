param([string]$ExpectedRemote='df295c79e0aa36428e88933352aeff629816ee83',[string]$Message='phantom(task-044): record bounded causal investigation and diagnostic failures',[string]$ReceiptLabel='PUBLICATION1')
$ErrorActionPreference='Stop'
$repo='C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius'
$task=[IO.Path]::GetFullPath((Join-Path $repo 'L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044'))
$ops=Join-Path $repo 'L2J_Mobius_CT_2.6_HighFive/.phantom-local/ops044'
$receipt=Join-Path $ops "$ReceiptLabel.json"
if(Test-Path $receipt){throw 'Immutable publication receipt already exists'}
$commands=[Collections.Generic.List[object]]::new()
function Invoke-PublishGit044([string[]]$Arguments){
 $savedPreference=$ErrorActionPreference
 $ErrorActionPreference='Continue'
 try { $result=@(& git -C $repo @Arguments 2>&1 | ForEach-Object {"$_"}); $code=$LASTEXITCODE }
 finally { $ErrorActionPreference=$savedPreference }
 $commands.Add(@{utc=[DateTime]::UtcNow.ToString('O');argv=@('git','-C',$repo)+$Arguments;exitCode=$code;output=$result})
 if($code -ne 0){throw "Git failed:$($Arguments -join ' ')`n$($result -join '`n')"}
 return $result
}
function Save-PublishReceipt044([string]$Status,[string]$Head,[string]$Remote){
 @{status=$Status;head=$Head;remote=$Remote;commands=$commands.ToArray();utc=[DateTime]::UtcNow.ToString('O');zipSha256=(Get-FileHash (Join-Path $task 'TASK044-review.zip')).Hash;ownJVMs=0;taskResult='PARTIAL_VERIFIED';M1='OPEN';readyForM2=$false} | ConvertTo-Json -Depth 8 | Set-Content $receipt -Encoding utf8
}
try{
 $before=@(Invoke-PublishGit044 @('diff','--cached','--name-only'))
 if($before.Count){throw 'Foreign staged paths present'}
 $remoteLine=@(Invoke-PublishGit044 @('ls-remote','origin','refs/heads/experiment/m1-candidate007-observe008'))
 $remote=($remoteLine[0] -split '\s+')[0]
 if($remote -cne $ExpectedRemote){throw 'Remote advanced; no write/push/rebase allowed'}
 $paths=@(Get-Content (Join-Path $task 'ALLOWLIST044.txt') | Where-Object {$_})
 if($paths.Count -ne @($paths | Select-Object -Unique).Count){throw 'Duplicate exact staging paths'}
 foreach($relative in $paths){
  $absolute=[IO.Path]::GetFullPath((Join-Path $repo $relative))
  if(-not $absolute.StartsWith($task+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $absolute -PathType Leaf)){throw 'Path escaped exact044 artifact family'}
 }
 $tracked=@(Invoke-PublishGit044 @('status','--short','--untracked-files=no'))
 foreach($line in $tracked){if($line.Substring(3).Replace('\','/') -notin $paths){throw 'Foreign tracked change present'}}
 $null=Invoke-PublishGit044 (@('add','--')+$paths)
 $staged=@(Invoke-PublishGit044 @('diff','--cached','--name-only'))
 if(-not $staged.Count -or @($staged | Where-Object {$_ -notin $paths}).Count){throw 'Unexpected staged scope'}
 # A literal Git patch has context-line prefix whitespace; validate it by exact source diff, not indentation lint.
 $whitePaths=@($paths | Where-Object {-not $_.EndsWith('/SOURCE_DIFF044.patch')})
 $null=Invoke-PublishGit044 (@('-c','core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol','diff','--cached','--check','--')+$whitePaths)
 $diff=Invoke-PublishGit044 (@('diff','--cached','--')+$paths)
 $diff | Set-Content (Join-Path $ops "$ReceiptLabel-staged.diff") -Encoding utf8
 $null=Invoke-PublishGit044 @('commit','-m',$Message)
 $head=(@(Invoke-PublishGit044 @('rev-parse','HEAD')))[0]
 $null=Invoke-PublishGit044 @('push','origin','HEAD:refs/heads/experiment/m1-candidate007-observe008')
 $remoteLine=@(Invoke-PublishGit044 @('ls-remote','origin','refs/heads/experiment/m1-candidate007-observe008'))
 $remote=($remoteLine[0] -split '\s+')[0]
 $clean=@(Invoke-PublishGit044 @('status','--short','--untracked-files=no'))
 if($head -cne $remote -or $clean.Count){throw 'Post-push HEAD/remote/tracked-clean guard failed'}
 Save-PublishReceipt044 'PASS' $head $remote
 Write-Output "ORDINARY_NON_FORCE_PUBLICATION_PASS HEAD=$head remote=$remote trackedClean=true"
}catch{
 Save-PublishReceipt044 'FAIL' '' ''
 throw
}
