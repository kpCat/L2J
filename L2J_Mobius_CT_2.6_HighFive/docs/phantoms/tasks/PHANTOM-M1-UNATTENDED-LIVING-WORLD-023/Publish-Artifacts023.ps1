[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Stage','Commit','Push','Verify')][string]$Mode)
$ErrorActionPreference='Stop'
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$root=Split-Path $module -Parent
$expectedRoot='C:\Users\ZBook\.codex\worktrees\m1-overnight-023\L2J_Mobius'
if($root -cne $expectedRoot){throw 'Exact TASK023 worktree mismatch.'}
$prefix='L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-M1-UNATTENDED-LIVING-WORLD-023/'
$base='fa65d4f8ae02ebb4e1c103c6e811e9352aac89f6'
$source='1b71464a9e09a80dad890e2bfd39df0f3b5d3b96'
$branch='refs/heads/experiment/m1-candidate007-observe008'
$commandFile=Join-Path $PSScriptRoot 'PUBLISH_COMMANDS.json'
$commands=[Collections.Generic.List[object]]::new()
if(Test-Path -LiteralPath $commandFile){foreach($entry in @(Get-Content -LiteralPath $commandFile -Raw | ConvertFrom-Json)){$commands.Add($entry)}}
function Invoke-RecordedGit {
    param([string[]]$GitArgs,[switch]$AllowNonzero)
    $output=@(& git -C $root @GitArgs 2>&1)
    $code=$LASTEXITCODE
    $commands.Add([pscustomobject]@{utc=[DateTime]::UtcNow.ToString('o');cwd=$root;argv=@('git','-C',$root)+$GitArgs;exitCode=$code})
    $commands | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $commandFile -Encoding utf8
    if($code -ne 0 -and -not $AllowNonzero){throw "Git failed ($code): $($GitArgs[0]); $($output | Select-Object -Last 4)"}
    return $output | ForEach-Object {"$_"}
}
function Assert-FrozenSource {
    foreach($family in 'java','test'){
        $path='L2J_Mobius_CT_2.6_HighFive/'+$family
        $changed=@(Invoke-RecordedGit -GitArgs @('diff','--name-only',$source,'--',$path))
        if($changed.Count){throw "Frozen $family differs."}
        $a=Invoke-RecordedGit -GitArgs @('rev-parse',($source+':'+$path))
        $b=Invoke-RecordedGit -GitArgs @('rev-parse',('HEAD:'+ $path))
        if("$a" -cne "$b"){throw "Frozen committed $family tree differs."}
    }
}
function Get-ExactArtifacts {
    $paths=@(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ARTIFACT_EXACT_ALLOWLIST.txt'))
    if(($paths | Select-Object -Unique).Count -ne $paths.Count){throw 'Duplicate exact artifact path.'}
    foreach($path in $paths){
        if(-not $path.StartsWith($prefix,[StringComparison]::Ordinal) -or $path -match '[*?\[\]]|\.\.|__pycache__|\.pyc$|\.class$|PUBLISH_RECEIPT|PUBLISH_COMMANDS|PUBLISH_WHITESPACE' -or -not (Test-Path -LiteralPath (Join-Path $root $path) -PathType Leaf)){throw "Not an exact allowed file: $path"}
    }
    return $paths
}
function Assert-ExactStaged {
    param([string[]]$Paths)
    $staged=@(Invoke-RecordedGit -GitArgs @('diff','--cached','--name-only'))
    if(-not $staged.Count){throw 'No staged artifacts.'}
    foreach($path in $staged){if($path -notin $Paths){throw "Unexpected staged file: $path"}}
    return $staged.Count
}
Assert-FrozenSource
switch($Mode){
    'Stage' {
        $guard=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ARTIFACT_GUARD.json') -Raw | ConvertFrom-Json
        if(-not $guard.checks_pass){throw 'Artifact UTF-8/encoding/secret/input guard failed.'}
        $paths=Get-ExactArtifacts
        foreach($entry in Import-Csv -LiteralPath (Join-Path $PSScriptRoot 'ARTIFACT_INVENTORY.tsv') -Delimiter "`t"){
            if((Get-FileHash -LiteralPath (Join-Path $root $entry.path) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256){throw "Inventory drift: $($entry.path)"}
        }
        foreach($existing in @(Invoke-RecordedGit -GitArgs @('diff','--cached','--name-only'))){if($existing -notin $paths){throw "Foreign staged path: $existing"}}
        for($offset=0;$offset -lt $paths.Count;$offset+=50){
            $end=[Math]::Min($offset+49,$paths.Count-1)
            Invoke-RecordedGit -GitArgs (@('add','--')+$paths[$offset..$end]) | Out-Null
        }
        $count=Assert-ExactStaged $paths
        $raw=Invoke-RecordedGit -GitArgs @('diff','--cached','--check') -AllowNonzero
        $raw | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'PUBLISH_WHITESPACE.txt') -Encoding utf8
        $manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'PACKAGE_MANIFEST.json') -Raw | ConvertFrom-Json
        $manual=@($paths | Where-Object {$_ -match '\.(md|ps1|py|java)$' -and $_.Substring($prefix.Length) -notin @($manifest.files.PSObject.Properties.Name)})
        Invoke-RecordedGit -GitArgs (@('diff','--cached','--check','--')+$manual) | Out-Null
        Write-Output "EXACT_STAGE_PASS=$count RAW_WHITESPACE_OUTPUT_LINES=$(@($raw).Count)"
    }
    'Commit' {
        $count=Assert-ExactStaged (Get-ExactArtifacts)
        Invoke-RecordedGit -GitArgs @('commit','-m','Record TASK023 blocked M1 runtime and safety evidence') | Select-Object -First 3
        Write-Output "ARTIFACT_COMMIT_PASS=$count HEAD=$(Invoke-RecordedGit -GitArgs @('rev-parse','HEAD'))"
    }
    'Push' {
        $remote=Invoke-RecordedGit -GitArgs @('ls-remote','origin',$branch)
        if("$remote".Split("`t")[0] -cne $base){throw 'PUBLISH_BLOCKED: remote moved; no merge or force.'}
        Invoke-RecordedGit -GitArgs @('push','origin',('HEAD:'+$branch))
    }
    'Verify' {
        $head=Invoke-RecordedGit -GitArgs @('rev-parse','HEAD')
        $remote=Invoke-RecordedGit -GitArgs @('ls-remote','origin',$branch)
        if("$remote".Split("`t")[0] -cne "$head"){throw 'Published remote HEAD differs.'}
        $utc=[DateTime]::UtcNow
        $minutes=($utc-[DateTime]::Parse('2026-10-06T22:20:26Z').ToUniversalTime()).TotalMinutes
        @('TASK_RESULT=BLOCKED','SERVER_M1_PASS=false','M1=OPEN','REAL_FINAL_PASS=NOT_RUN',
          "FINAL_ARTIFACT_HEAD=$head","REMOTE_HEAD=$head",'REMOTE_EQUALITY=PASS',
          "FROZEN_SOURCE_SHA=$source",'FROZEN_PRODUCTION_AND_TEST_TREES=PASS',
          "PUBLISHED_UTC=$($utc.ToString('o'))","ELAPSED_WALL_MINUTES=$([Math]::Round($minutes,2))",
          'OWNED_JVMS=0; planned_crash=1; emergency_force=0',
          'Git exact argv: PUBLISH_COMMANDS.json; raw whitespace: PUBLISH_WHITESPACE.txt') |
          Set-Content -LiteralPath (Join-Path $PSScriptRoot 'PUBLISH_RECEIPT.txt') -Encoding utf8
        Write-Output "PUBLICATION_VERIFIED_HEAD=$head ELAPSED_MINUTES=$([Math]::Round($minutes,2))"
    }
}
