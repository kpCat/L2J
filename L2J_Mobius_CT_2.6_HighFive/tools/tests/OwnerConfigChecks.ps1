[CmdletBinding()]
param([string]$OutputRoot='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$module=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$core=Join-Path $module 'tools/OwnerConfig.ps1'
if(!(Test-Path -LiteralPath $core -PathType Leaf)){throw 'OwnerConfig core is missing.'}
. $core
$work=if($OutputRoot){[IO.Path]::GetFullPath($OutputRoot)}else{Join-Path (Split-Path $module -Parent) ('.phantom-local/task075/ownerconfig-checks-'+[guid]::NewGuid().ToString('N'))}
New-Item -ItemType Directory -Path $work | Out-Null
function Assert-Check([bool]$Condition,[string]$Reason){if(!$Condition){throw ('CHECK_FAIL '+$Reason)}}
function Write-Check([string]$Path,[string]$Text){[IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))}
function Hash-Checks([string]$Root){return ((Get-ChildItem -LiteralPath $Root -Recurse -File | Sort-Object FullName | ForEach-Object {$_.FullName.Substring($Root.Length)+':'+(Get-FileHash -LiteralPath $_.FullName).Hash}) -join "`n")}
function Expect-Conflict([scriptblock]$Work,[string]$Forbidden=''){
    $message='';try{& $Work | Out-Null}catch{$message=$_.Exception.Message}
    Assert-Check ([bool]$message) ('expected conflict at caller line '+(Get-PSCallStack)[1].ScriptLineNumber)
    if($Forbidden){Assert-Check (!$message.Contains($Forbidden)) 'secret value leaked'}
    return $message
}
$script:OwnerChecksManaged=@{}
function Read-OwnerManagedFile([string]$File,[string]$Revision){if($script:OwnerChecksManaged.ContainsKey($File)){return Add-OwnerOptionalManagedDefaults $File $script:OwnerChecksManaged[$File]};return $null}
function New-CheckFixture([string]$Name){
    $root=Join-Path $work ($Name+'/Путь с пробелом/game')
    New-Item -ItemType Directory -Path (Join-Path $root 'config/Custom/Дополнительно'),(Join-Path $root 'data/xsd') -Force | Out-Null
    $source=Join-Path $module 'dist/game'
    foreach($relative in @('config/OwnerProfile38.tsv','config/OwnerProfilePreserved.properties','data/EnchantItemGroups.xml','data/EnchantItemData.xml','data/xsd/enchantItemGroups.xsd','data/xsd/enchantItemData.xsd')){Copy-Item -LiteralPath (Join-Path $source $relative) -Destination (Join-Path $root $relative)}
    $rules=Import-Csv -LiteralPath (Join-Path $root 'config/OwnerProfile38.tsv') -Delimiter "`t"
    $catalog=[ordered]@{}
    $script:OwnerChecksManaged=@{}
    foreach($group in ($rules | Where-Object file -Like '*.ini' | Group-Object file)){
        $file=$group.Name.Substring('dist/game/config/'.Length);$catalog[$file]='public'
        if($file -eq 'Rates.ini'){$lines=@(Select-String -LiteralPath (Join-Path $source ('config/'+$file)) -Pattern '^\w*Quest\w*\s*=' | ForEach-Object Line)}
        else{$lines=@(foreach($rule in $group.Group){$hits=@(Select-String -LiteralPath (Join-Path $source ('config/'+$file)) -Pattern ('^'+[regex]::Escape($rule.key_or_range)+'\s*='));Assert-Check ($hits.Count -eq 1) 'public fixture key';$hits[0].Line})}
        $text=($lines -join "`r`n")+"`r`n"
        if($file -eq 'Player.ini'){$text+="FakeDeathUntarget = False `r`n"}
        Write-Check (Join-Path $root ('config/'+$file)) $text;$script:OwnerChecksManaged[$file]=$text
    }
    $extra='Custom/Дополнительно/Проба.ini'
    $text="# OwnerConfig: public`r`n# keep this comment`r`nCount = 1`r`nCaption = Hello  `r`nList = one,\`r`n    two`r`nFreshFactory = 42`r`n"
    Write-Check (Join-Path $root ('config/'+$extra)) $text;$script:OwnerChecksManaged[$extra]=$text
    $catalog['Database.ini']='excluded'
    Write-Check (Join-Path $root 'config/Database.ini') 'Password = DUMMY_PRIVATE_NEEDLE_075'
    Write-Check (Join-Path $root 'config/OwnerConfig.json') (([ordered]@{format=1;files=$catalog;overrides=@()} | ConvertTo-Json -Depth 5)+"`n")
    return $root
}
function Invoke-CheckOwner([string]$Operation,[string]$Root,[string]$Profile=''){
    if(!$Profile){$Profile=Join-Path $Root 'config/OwnerConfig.json'}
    Invoke-OwnerConfig -Action $Operation -GameRoot $Root -ProfilePath $Profile
}
function Copy-CheckGame([string]$Source,[string]$Name){$target=Join-Path $work ($Name+'/game');New-Item -ItemType Directory -Path $target -Force | Out-Null;Copy-Item -Path (Join-Path $Source '*') -Destination $target -Recurse;return $target}

# TASK095: additive managed default must not require editing the existing source INI.
$v4=New-CheckFixture 'v4-optin';$v4File='Custom/PhantomPlayers.ini';$v4Path=Join-Path $v4 ('config/'+$v4File)
Write-Check $v4Path "# OwnerConfig: public`r`n"
$script:OwnerChecksManaged[$v4File]="# OwnerConfig: public`r`n"
Write-Check $v4Path ([IO.File]::ReadAllText($v4Path)+"EnablePhantomSemanticV4=True`r`n")
Invoke-CheckOwner 'Save' $v4
$v4Profile=[IO.File]::ReadAllText((Join-Path $v4 'config/OwnerConfig.json')) | ConvertFrom-Json
$v4Override=@($v4Profile.overrides | Where-Object key -CEQ 'EnablePhantomSemanticV4')
Assert-Check ($v4Override.Count -eq 1 -and $v4Override[0].kind -eq 'boolean' -and $v4Override[0].baseValue -ieq 'false' -and $v4Override[0].value -ieq 'true') 'v4 typed optional default'
Invoke-CheckOwner 'Verify' $v4
$v4Fresh=Copy-CheckGame $v4 'v4-fresh-default'
$v4FreshPath=Join-Path $v4Fresh ('config/'+$v4File)
Write-Check $v4FreshPath ([regex]::Replace([IO.File]::ReadAllText($v4FreshPath),'(?m)^EnablePhantomSemanticV4[^\r\n]*(?:\r?\n)?',''))
Invoke-CheckOwner 'Apply' $v4Fresh
Invoke-CheckOwner 'Verify' $v4Fresh
Assert-Check ([IO.File]::ReadAllText($v4FreshPath) -match '(?m)^EnablePhantomSemanticV4=True') 'saved v4 opt-in applies to old INI without key'
Write-Check $v4Path ([IO.File]::ReadAllText($v4Path)+"UnknownOptionalKey=True`r`n")
$v4Before=Hash-Checks $v4;$null=Expect-Conflict {Invoke-CheckOwner 'Save' $v4}
Assert-Check ((Hash-Checks $v4) -eq $v4Before) 'unrelated missing managed key guard changed'
Write-Output 'OWNER_CONFIG_CHECK semantic_v4 PASS'

# One composed evolution scenario, including actual RatesConfig primitive discovery and continuations.
$first=New-CheckFixture 'evolution'
$earlyCopy=Copy-CheckGame $first 'before-save-copy'
$extra='Custom/Дополнительно/Проба.ini';$path=Join-Path $first ('config/'+$extra)
$text=[IO.File]::ReadAllText($path).Replace('Count = 1','Count = 2').Replace('Caption = Hello  ','Caption = hello  ').Replace("one,\`r`n    two","red,\`r`n    blue")
Write-Check $path $text
$rates=Join-Path $first 'config/Rates.ini';Write-Check $rates (Set-OwnerIniValue ([IO.File]::ReadAllText($rates)) 'RateQuestRewardSP' '1.5')
$servitor=Join-Path $first 'config/Custom/ServitorShare.ini';Write-Check $servitor (Set-OwnerIniValue ([IO.File]::ReadAllText($servitor)) 'ServitorShareMAtkBonusMultiplier' '3.0')
Invoke-CheckOwner 'Save' $first
Invoke-CheckOwner 'Verify' $first
$profile=Join-Path $first 'config/OwnerConfig.json';$data=([IO.File]::ReadAllText($profile) | ConvertFrom-Json)
$rate=@($data.overrides | Where-Object key -EQ 'RateQuestRewardSP');Assert-Check ($rate.Count -eq 1 -and $rate[0].kind -eq 'number') 'Rates float discovery'
$caption=@($data.overrides | Where-Object key -CEQ 'Caption');Assert-Check ($caption.Count -eq 1 -and $caption[0].baseValue -ceq 'Hello  ' -and $caption[0].value -ceq 'hello  ') 'case-only string/trailing whitespace'
$earlyServitor=Join-Path $earlyCopy 'config/Custom/ServitorShare.ini';$earlyText=[IO.File]::ReadAllText($earlyServitor)
foreach($bad in @([regex]::Replace($earlyText,'(?m)^ServitorShareMAtkBonusMultiplier[^\r\n]*',''),(Set-OwnerIniValue $earlyText 'ServitorShareMAtkBonusMultiplier' 'invalid'))){
    Write-Check $earlyServitor $bad;$before=Hash-Checks $earlyCopy
    $null=Expect-Conflict {Invoke-CheckOwner 'Apply' $earlyCopy $profile}
    Assert-Check ((Hash-Checks $earlyCopy) -eq $before) 'old metadata changed on INI conflict'
}
Write-Check $earlyServitor $earlyText
Invoke-CheckOwner 'Apply' $earlyCopy $profile
Invoke-CheckOwner 'Verify' $earlyCopy $profile
foreach($name in @('OwnerConfig.json','OwnerProfile38.tsv','OwnerProfilePreserved.properties')){
    Assert-Check ((Get-FileHash -LiteralPath (Join-Path $earlyCopy ('config/'+$name))).Hash -eq (Get-FileHash -LiteralPath (Join-Path $first ('config/'+$name))).Hash) 'canonical copied metadata'
}
$oldOnce=Hash-Checks $earlyCopy;Invoke-CheckOwner 'Apply' $earlyCopy $profile;Assert-Check ((Hash-Checks $earlyCopy) -eq $oldOnce) 'old metadata Apply idempotence'
foreach($name in @('OwnerConfig.json','OwnerProfile38.tsv','OwnerProfilePreserved.properties')){Remove-Item -LiteralPath (Join-Path $earlyCopy ('config/'+$name))}
$before=Hash-Checks $earlyCopy;$null=Expect-Conflict {Invoke-CheckOwner 'Verify' $earlyCopy $profile};Assert-Check ((Hash-Checks $earlyCopy) -eq $before) 'Verify created missing metadata'
$script:MetadataAtomicCount=0;$savedMetadataAtomic=(Get-Item Function:Write-OwnerAtomic).ScriptBlock
function Write-OwnerAtomic([string]$Path,[byte[]]$Bytes){$script:MetadataAtomicCount++;if($script:MetadataAtomicCount -eq 2){throw 'Injected metadata I/O failure'};& $savedMetadataAtomic $Path $Bytes}
try{$message=Expect-Conflict {Invoke-CheckOwner 'Apply' $earlyCopy $profile};Assert-Check ($message -match 'SHA-256 verified') 'new metadata rollback not verified'}finally{Set-Item Function:Write-OwnerAtomic $savedMetadataAtomic}
Assert-Check ((Hash-Checks $earlyCopy) -eq $before) 'new metadata rollback left created files'
Invoke-CheckOwner 'Apply' $earlyCopy $profile
Invoke-CheckOwner 'Verify' $earlyCopy $profile
$copy=Copy-CheckGame $first 'compatible-copy'
$copyExtra=Join-Path $copy ('config/'+$extra)
Write-Check $copyExtra ($script:OwnerChecksManaged[$extra].Replace('Count = 1','Count = 9')+"# later factory comment`r`nNewerFactory = 99`r`n")
$copyRates=Join-Path $copy 'config/Rates.ini';Write-Check $copyRates (Set-OwnerIniValue ([IO.File]::ReadAllText($copyRates)) 'RateQuestRewardSP' '2.5')
Invoke-CheckOwner 'Apply' $copy $profile
$text=[IO.File]::ReadAllText($copyExtra)
Assert-Check ($text.Contains('# keep this comment') -and $text.Contains('NewerFactory = 99') -and $text.Contains('List = red,blue') -and !$text.Contains("`r`n    two")) 'comments/new keys/logical span'
Write-Check $copyExtra $text.Replace('Caption = hello  ','Caption = Hello  ')
$null=Expect-Conflict {Invoke-CheckOwner 'Verify' $copy $profile}
Invoke-CheckOwner 'Apply' $copy $profile
Assert-Check ([IO.File]::ReadAllText($copyExtra).Contains("Caption = hello  `r`n")) 'case-only Apply lost whitespace'
$once=Hash-Checks $copy;Invoke-CheckOwner 'Apply' $copy $profile;Invoke-CheckOwner 'Verify' $copy $profile;Assert-Check ((Hash-Checks $copy) -eq $once) 'idempotence'
$script:OwnerChecksManaged[$extra]=[IO.File]::ReadAllText($path)
$script:OwnerChecksManaged['Rates.ini']=[IO.File]::ReadAllText($rates)
Invoke-CheckOwner 'Save' $first
$data=([IO.File]::ReadAllText($profile) | ConvertFrom-Json);$count=@($data.overrides | Where-Object key -EQ 'Count')
Assert-Check ($count.Count -eq 1 -and $count[0].baseValue -eq '1') 'base retained after managed commit'
$factoryPlayer=Join-Path $first 'config/Player.ini'
Write-Check $factoryPlayer (Set-OwnerIniValue ([IO.File]::ReadAllText($factoryPlayer)) 'FakeDeathUntarget' 'True')
Invoke-CheckOwner 'Save' $first
Invoke-CheckOwner 'Verify' $first
$data=([IO.File]::ReadAllText($profile) | ConvertFrom-Json);$toggle=@($data.overrides | Where-Object key -CEQ 'FakeDeathUntarget')
Assert-Check ($toggle.Count -eq 1 -and $toggle[0].baseValue -ceq 'False ' -and $toggle[0].kind -eq 'boolean') 'native false whitespace baseline'
Write-Output 'OWNER_CONFIG_CHECK evolution PASS'

# One composed preflight/rollback scenario; target state must remain byte-identical on every conflict.
$second=New-CheckFixture 'rollback';$extra='Custom/Дополнительно/Проба.ini';$path=Join-Path $second ('config/'+$extra)
Write-Check $path ([IO.File]::ReadAllText($path).Replace('Count = 1','Count = 2'))
$rates=Join-Path $second 'config/Rates.ini';Write-Check $rates (Set-OwnerIniValue ([IO.File]::ReadAllText($rates)) 'RateQuestRewardSP' '1.5')
Invoke-CheckOwner 'Save' $second
$profile=Join-Path $second 'config/OwnerConfig.json';$copy=Copy-CheckGame $second 'rollback-copy';$copyExtra=Join-Path $copy ('config/'+$extra)
$original=[IO.File]::ReadAllText($copyExtra)
foreach($bad in @($original.Replace('Count = 2', '# Count removed'),($original+"Count=3`r`n"),$original.Replace('Count = 2','Count = invalid'))){
    Write-Check $copyExtra $bad;$before=Hash-Checks $copy
    $null=Expect-Conflict {Invoke-CheckOwner 'Apply' $copy $profile}
    Assert-Check ((Hash-Checks $copy) -eq $before) 'conflict wrote target'
}
Write-Check $copyExtra $script:OwnerChecksManaged[$extra]
$copyRates=Join-Path $copy 'config/Rates.ini';Write-Check $copyRates $script:OwnerChecksManaged['Rates.ini']
$before=Hash-Checks $copy;$script:AtomicCount=0;$savedAtomic=(Get-Item Function:Write-OwnerAtomic).ScriptBlock
function Write-OwnerAtomic([string]$Path,[byte[]]$Bytes){$script:AtomicCount++;if($script:AtomicCount -eq 2){throw 'Injected test I/O failure'};& $savedAtomic $Path $Bytes}
try{$message=Expect-Conflict {Invoke-CheckOwner 'Apply' $copy $profile};Assert-Check ($message -match 'SHA-256 verified') 'rollback not verified'}finally{Set-Item Function:Write-OwnerAtomic $savedAtomic}
Assert-Check ((Hash-Checks $copy) -eq $before) 'I/O rollback changed bytes'
$managed=$script:OwnerChecksManaged[$extra];$script:OwnerChecksManaged[$extra]=$managed.Replace("Count = 1`r`n",'')
$before=Hash-Checks $second;$null=Expect-Conflict {Invoke-CheckOwner 'Save' $second};Assert-Check ((Hash-Checks $second) -eq $before) 'deleted managed key accepted'
$script:OwnerChecksManaged[$extra]=$managed
$player=Join-Path $second 'config/Player.ini';$playerText=[IO.File]::ReadAllText($player)
Write-Check $player (Set-OwnerIniValue $playerText 'MaximumSlotsForNoDwarf' '200.5');$before=Hash-Checks $second
$null=Expect-Conflict {Invoke-CheckOwner 'Save' $second};Assert-Check ((Hash-Checks $second) -eq $before) 'Player integer fraction accepted'
Write-Check $player $playerText
$savedModule=$script:OwnerModule
$fakeModule=Join-Path $work 'source-alias-module';$aliasRoot=Join-Path $fakeModule 'dist/game'
New-Item -ItemType Directory -Path $aliasRoot -Force | Out-Null
Copy-Item -Path (Join-Path $second '*') -Destination $aliasRoot -Recurse
$aliasExtra=Join-Path $aliasRoot ('config/'+$extra)
Write-Check $aliasExtra ([IO.File]::ReadAllText($aliasExtra).Replace('Count = 2','Count = 3'))
try{
    $script:OwnerModule=$fakeModule
    $before=Hash-Checks $aliasRoot
    $message=Expect-Conflict {Invoke-CheckOwner 'Apply' $aliasRoot}
    Assert-Check ($message -match 'unsaved SOURCE') 'explicit SOURCE alias bypass'
    Assert-Check ((Hash-Checks $aliasRoot) -eq $before) 'SOURCE alias changed target'
}finally{$script:OwnerModule=$savedModule}
$historical=Join-Path $work 'historical/.phantom-local/task074/game';New-Item -ItemType Directory -Path $historical -Force | Out-Null
$message=Expect-Conflict {Invoke-OwnerConfig -Action Apply -GameRoot $historical}
Assert-Check ($message -match 'historical runtime') 'historical Windows root boundary'
Write-Output 'OWNER_CONFIG_CHECK rollback PASS'

# One composed classification/privacy scenario. No secret values may enter JSON or diagnostics.
$third=New-CheckFixture 'privacy';$unknown=Join-Path $third 'config/Custom/Unknown.ini';Write-Check $unknown 'Count=2'
$script:OwnerChecksManaged['Custom/Unknown.ini']='Count=1'
$before=Hash-Checks $third;$null=Expect-Conflict {Invoke-CheckOwner 'Save' $third};Assert-Check ((Hash-Checks $third) -eq $before) 'unknown file accepted'
$needle='PRIVATE_TOKEN_NEEDLE_075'
Write-Check $unknown ("# OwnerConfig: public`nToken=$needle`n")
$null=Expect-Conflict {Invoke-CheckOwner 'Save' $third} $needle
Remove-Item -LiteralPath $unknown
$personal=Join-Path $third 'config/Custom/PersonalCharacterQoL.ini'
Write-Check $personal ([IO.File]::ReadAllText($personal)+"AllowedAccounts=$needle`r`n")
Invoke-CheckOwner 'Save' $third
$profile=Join-Path $third 'config/OwnerConfig.json';Assert-Check (![IO.File]::ReadAllText($profile).Contains($needle)) 'private field stored'
$data=([IO.File]::ReadAllText($profile) | ConvertFrom-Json)
$data.overrides=@([pscustomobject]@{file='Custom/PersonalCharacterQoL.ini';key='AllowedAccounts';kind='string';baseValue='';value=$needle})
Write-Check $profile ($data | ConvertTo-Json -Depth 6)
$null=Expect-Conflict {Invoke-CheckOwner 'Verify' $third} $needle
Write-Output 'OWNER_CONFIG_CHECK privacy PASS'

# The real wrapper is run from another CWD in a spaced Cyrillic directory with a tiny action stub.
$wrapper=Join-Path $module 'SaveOwnerConfig.bat';Assert-Check (Test-Path -LiteralPath $wrapper) 'wrapper missing'
$wrapperRoot=Join-Path $work 'Обёртка с пробелом';New-Item -ItemType Directory -Path (Join-Path $wrapperRoot 'tools') -Force | Out-Null
Copy-Item -LiteralPath $wrapper -Destination (Join-Path $wrapperRoot 'SaveOwnerConfig.bat')
$stub=@'
param([string]$Action)
[IO.File]::AppendAllText((Join-Path $PSScriptRoot '../actions.txt'),$Action+"`n")
if($Action -eq 'Save' -and $env:OWNERCONFIG_STUB_MODE -eq 'savefail'){exit 7}
if($Action -eq 'Verify' -and $env:OWNERCONFIG_STUB_MODE -eq 'verifyfail'){exit 8}
exit 0
'@
[IO.File]::WriteAllText((Join-Path $wrapperRoot 'tools/OwnerConfig.ps1'),$stub,[Text.UTF8Encoding]::new($true))
$savedMode=$env:OWNERCONFIG_STUB_MODE
try{
    foreach($mode in @('ok','savefail','verifyfail')){
        $env:OWNERCONFIG_STUB_MODE=$mode;$actions=Join-Path $wrapperRoot 'actions.txt';if(Test-Path -LiteralPath $actions){Remove-Item -LiteralPath $actions}
        $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$env:ComSpec
        $start.Arguments='/d /s /c ""'+(Join-Path $wrapperRoot 'SaveOwnerConfig.bat')+'" <nul"'
        $start.WorkingDirectory=Split-Path $module -Parent;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
        $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $start.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$start.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
        $process=[Diagnostics.Process]::new();$process.StartInfo=$start
        try{
            $null=$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
            if(!$process.WaitForExit(55000)){if(!$process.HasExited){$process.Kill();$null=$process.WaitForExit(5000)};throw 'Wrapper timeout'}
            $result=[pscustomobject]@{code=$process.ExitCode;output=$stdout.Result;error=$stderr.Result}
        }finally{$process.Dispose()}
        Assert-Check ($(if($mode -eq 'ok'){$result.code -eq 0}else{$result.code -ne 0})) ('wrapper exit '+$mode)
        Assert-Check (($result.output.Contains('Настройки успешно сохранены и проверены')) -eq ($mode -eq 'ok')) ('wrapper success sentence '+$mode)
        $calls=[IO.File]::ReadAllLines($actions)
        Assert-Check (($calls -join ',') -eq $(if($mode -eq 'savefail'){'Save'}else{'Save,Verify'})) ('wrapper actions '+$mode)
    }
}finally{$env:OWNERCONFIG_STUB_MODE=$savedMode}
Write-Output 'OWNER_CONFIG_CHECK wrapper PASS'
Write-Output ('OWNER_CONFIG_CHECKS PASS scenarios=5 fixtureRoot='+$work)
