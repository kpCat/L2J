[CmdletBinding()]
param(
    [ValidateSet('Save','Verify','Apply')][string]$Action='Verify',
    [string]$GameRoot='',
    [string]$ProfilePath='',
    [string]$JavaHome='',
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$')][string]$ManagedRevision='HEAD'
)
$script:OwnerModule=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$script:OwnerNativeKinds=$null

function Assert-OwnerPlain([string]$Path){
    $full=[IO.Path]::GetFullPath($Path)
    $walk=[IO.Path]::GetPathRoot($full)
    foreach($part in $full.Substring($walk.Length).Split('\')){
        if(!$part){continue};$walk=Join-Path $walk $part
        if((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'OwnerConfig: linked paths are forbidden.'}
    }
}
function Assert-OwnerRelative([string]$File){
    if([IO.Path]::IsPathRooted($File) -or $File.Contains('\') -or [IO.Path]::GetExtension($File) -ine '.ini'){throw 'OwnerConfig: invalid relative INI path.'}
    foreach($part in $File.Split('/')){if(!$part -or $part -in @('.','..') -or $part.EndsWith('.') -or $part.EndsWith(' ') -or $part.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0){throw 'OwnerConfig: invalid relative INI path.'}}
}
function Test-OwnerPrivateFile([string]$File){
    return [IO.Path]::GetFileName($File) -match '^(Database|Network|Server|CustomMailManager|PasswordChange|LocalPlayPilot)\.ini$|(?:^|[._-])(secret|credentials?|password|private)(?:[._-]|$)'
}
function Test-OwnerPrivateKey([string]$Key){
    return $Key -match '(?i)Password|Passwd|Credential|Secret|Token|ApiKey|AccessKey|Account|Allowlist|Whitelist|Webhook|SMTP|(?:Character|Char)(?:Names?|Ids?|ObjectIds?)|ObserverCharacter|SyntheticCharacter|PreparedOfflineCharacterIds|^URL$|^Login$|^(Host|Hostname|IP|Port|Endpoint)$|(?:^|[_.-])(?:host|hostname|ipaddress|port|endpoint)$|(?-i:Host|Hostname|IPAddress|Port)$'
}
function Test-OwnerPrivateValue([string]$Value){
    return $Value -match '(?i)jdbc:|://[^/\s]*:[^@\s]*@|-----BEGIN [^-]*PRIVATE KEY-----|(?:password|passwd|pwd|secret|token)\s*[:=]|\bBearer[ \t]+\S+|\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b|\b(?:localhost|127\.0\.0\.1|0\.0\.0\.0)\b|\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::[0-9]+)?\b'
}
function Read-OwnerText([string]$Path){
    Assert-OwnerPlain $Path
    $bytes=[IO.File]::ReadAllBytes($Path)
    $bom=$bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191
    $offset=if($bom){3}else{0}
    $text=[Text.UTF8Encoding]::new($false,$true).GetString($bytes,$offset,$bytes.Length-$offset)
    return [pscustomobject]@{path=$Path;bytes=$bytes;text=$text;bom=$bom}
}
function ConvertTo-OwnerBytes([string]$Text,[bool]$Bom){
    $encoding=[Text.UTF8Encoding]::new($Bom)
    return [byte[]]($encoding.GetPreamble()+$encoding.GetBytes($Text))
}
function Read-OwnerIni([string]$Text,[string]$File){
    $entries=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    $lines=@([regex]::Matches($Text,'[^\r\n]*(?:\r\n|\r|\n|$)') | Where-Object Length -GT 0)
    for($index=0;$index -lt $lines.Count;$index++){
        $first=$lines[$index];$line=$first.Value.TrimEnd([char[]]"`r`n")
        if($line -match '^\s*(?:[#!;]|$)'){continue}
        $last=$first
        while($line -match '(?<!\\)(?:\\\\)*\\$'){
            $line=$line.Substring(0,$line.Length-1)
            $index++
            if($index -ge $lines.Count){throw "OwnerConfig: unterminated continuation in $File; values withheld."}
            $last=$lines[$index]
            $line+=$last.Value.TrimEnd([char[]]"`r`n").TrimStart([char[]]" `t`f")
        }
        $match=[regex]::Match($line,'^(?<prefix>[ \t]*(?<key>[A-Za-z0-9_][A-Za-z0-9_.-]*)[ \t]*[=:][ \t]*)(?<value>.*)$')
        if(!$match.Success){throw "OwnerConfig: unsupported INI syntax in $File; values withheld."}
        $key=$match.Groups['key'].Value
        if($entries.ContainsKey($key)){throw "OwnerConfig: duplicate key in $File; values withheld."}
        $entries.Add($key,[pscustomobject]@{key=$key;value=$match.Groups['value'].Value;prefix=$match.Groups['prefix'].Value;start=$first.Index;length=$last.Index+$last.Length-$first.Index;newline=[regex]::Match($last.Value,'(?:\r\n|\r|\n)$').Value})
    }
    return ,$entries
}
function Get-OwnerNativeKinds {
    if($null -ne $script:OwnerNativeKinds){return $script:OwnerNativeKinds}
    $map=@{}
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $script:OwnerModule 'java/org/l2jmobius/gameserver/config') -Filter '*.java' -Recurse -File){
        $text=[IO.File]::ReadAllText($file.FullName)
        $paths=@([regex]::Matches($text,'"\./config/([^"\r\n]+\.ini)"') | ForEach-Object {$_.Groups[1].Value} | Select-Object -Unique)
        if($paths.Count -ne 1){continue}
        foreach($match in [regex]::Matches($text,'\bconfig\.get(Float|Double|Int|Long|Boolean)\(\s*"([A-Za-z0-9_][A-Za-z0-9_.-]*)"')){
            $identity=$paths[0]+"`t"+$match.Groups[2].Value
            $kind=@{Float='number';Double='double';Int='integer';Long='long';Boolean='boolean'}[$match.Groups[1].Value]
            if($map.ContainsKey($identity) -and $map[$identity] -ne $kind){$map[$identity]='ambiguous'}else{$map[$identity]=$kind}
        }
    }
    $script:OwnerNativeKinds=$map
    return $map
}
function Get-OwnerKind([string]$File,[string]$Key,[string]$Value){
    $map=Get-OwnerNativeKinds
    $identity=$File+"`t"+$Key
    if($map.ContainsKey($identity) -and $map[$identity] -ne 'ambiguous'){return $map[$identity]}
    if($Value -match '^(?i:true|false)$'){return 'boolean'}
    if($Value -match '^[+-]?[0-9]+$'){return 'long'}
    if($Value -match '^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$'){return 'double'}
    return 'string'
}
function ConvertTo-OwnerValue([string]$Kind,[string]$Value){
    if($Value -match '[\r\n]'){throw 'OwnerConfig: multiline value forbidden.'}
    if($Kind -eq 'string'){return $Value}
    if($Kind -eq 'boolean'){
        if($Value -match '^(?i:true)$'){return 'true'}
        if($Value -match '^(?i:false)[ \t]*$'){return 'false'}
        throw 'OwnerConfig: boolean type conflict.'
    }
    $value=$Value.Trim()
    switch($Kind){
        'boolean' {if($value -notmatch '^(?i:true|false)$'){throw 'OwnerConfig: boolean type conflict.'};return $value.ToLowerInvariant()}
        'integer' {$number=0;if(![int]::TryParse($value,[Globalization.NumberStyles]::Integer,[Globalization.CultureInfo]::InvariantCulture,[ref]$number)){throw 'OwnerConfig: integer type/range conflict.'};return $number.ToString([Globalization.CultureInfo]::InvariantCulture)}
        'long' {$number=0L;if(![long]::TryParse($value,[Globalization.NumberStyles]::Integer,[Globalization.CultureInfo]::InvariantCulture,[ref]$number)){throw 'OwnerConfig: long type/range conflict.'};return $number.ToString([Globalization.CultureInfo]::InvariantCulture)}
        {$_ -in @('number','double')} {$number=0.0;if(![double]::TryParse($value,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$number) -or [double]::IsNaN($number) -or [double]::IsInfinity($number) -or ($Kind -eq 'number' -and ([single]::IsInfinity([single]$number)))){throw 'OwnerConfig: finite numeric type/range conflict.'};return $number.ToString('R',[Globalization.CultureInfo]::InvariantCulture)}
        'string' {if($value -match '[\r\n]'){throw 'OwnerConfig: multiline value forbidden.'};return $value}
        default {throw 'OwnerConfig: unsupported override kind.'}
    }
}
function Invoke-OwnerNative([string]$Exe,[string[]]$Arguments,[string]$Directory){
    $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$Exe
    $start.Arguments=(($Arguments | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' ')
    $start.WorkingDirectory=$Directory;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $start.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$start.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{if(!$process.Start()){throw 'OwnerConfig: helper unavailable.'};$out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync();if(!$process.WaitForExit(55000)){if(!$process.HasExited){$process.Kill();$null=$process.WaitForExit(5000)};throw 'OwnerConfig: owned read-only helper timed out; no changes applied.'};return [pscustomobject]@{code=$process.ExitCode;output=$out.Result;error=$err.Result}}finally{$process.Dispose()}
}
function Read-OwnerManagedFile([string]$File,[string]$Revision){
    Assert-OwnerRelative $File
    $cacheKey=$Revision+"`t"+$File
    if($script:OwnerManagedCache.ContainsKey($cacheKey)){return $script:OwnerManagedCache[$cacheKey]}
    $git=(Get-Command git.exe -ErrorAction Stop).Source
    if(!$script:OwnerGitRoot){$root=Invoke-OwnerNative $git @('-C',$script:OwnerModule,'rev-parse','--show-toplevel') $script:OwnerModule;if($root.code -ne 0){throw 'OwnerConfig: Save requires the managed Git checkout.'};$script:OwnerGitRoot=[IO.Path]::GetFullPath($root.output.Trim()).TrimEnd('\')}
    $repo=$script:OwnerGitRoot
    if(!$script:OwnerModule.StartsWith($repo+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OwnerConfig: managed module outside repository.'}
    if(!$script:OwnerRevisions.ContainsKey($Revision)){$resolved=Invoke-OwnerNative $git @('-C',$repo,'rev-parse','--verify',($Revision+'^{commit}')) $repo;if($resolved.code -ne 0 -or $resolved.output.Trim() -notmatch '^[0-9a-f]{40}$'){throw 'OwnerConfig: managed revision unavailable.'};$script:OwnerRevisions[$Revision]=$resolved.output.Trim()}
    $relative=$script:OwnerModule.Substring($repo.Length+1).Replace('\','/')+'/dist/game/config/'+$File
    $result=Invoke-OwnerNative $git @('-C',$repo,'show',($script:OwnerRevisions[$Revision]+':'+$relative)) $repo
    $text=if($result.code -eq 0){$result.output.TrimStart([char]0xFEFF)}else{$null}
    $script:OwnerManagedCache[$cacheKey]=$text
    return $text
}
function Set-OwnerIniValue([string]$Text,[string]$Key,[string]$Value){
    $entries=Read-OwnerIni $Text 'selected target'
    if(!$entries.ContainsKey($Key)){throw "OwnerConfig: missing target key $Key; values withheld."}
    $entry=$entries[$Key]
    return $Text.Substring(0,$entry.start)+$entry.prefix+$Value+$entry.newline+$Text.Substring($entry.start+$entry.length)
}
function Write-OwnerAtomic([string]$Path,[byte[]]$Bytes){
    Assert-OwnerPlain $Path
    $temporary=$Path+'.ownerconfig-'+[guid]::NewGuid().ToString('N')
    $backup=$temporary+'.bak'
    try{
        [IO.File]::WriteAllBytes($temporary,$Bytes)
        if(Test-Path -LiteralPath $Path){[IO.File]::Replace($temporary,$Path,$backup)}else{[IO.File]::Move($temporary,$Path)}
        if((Get-OwnerHash ([IO.File]::ReadAllBytes($Path))) -ne (Get-OwnerHash $Bytes)){throw 'OwnerConfig: atomic write verification failed.'}
        if(Test-Path -LiteralPath $backup){Remove-Item -LiteralPath $backup -Force}
    }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
}
function Get-OwnerHash([byte[]]$Bytes){$sha=[Security.Cryptography.SHA256]::Create();try{return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}}
function Write-OwnerTransaction($Writes,$Expected){
    $before=@{};$changed=[Collections.Generic.List[string]]::new()
    foreach($path in $Writes.Keys){
        Assert-OwnerPlain $path;$before[$path]=if(Test-Path -LiteralPath $path){[IO.File]::ReadAllBytes($path)}else{$null}
        if(!$Expected.ContainsKey($path) -or (($null -eq $before[$path]) -ne ($null -eq $Expected[$path])) -or ($null -ne $before[$path] -and (Get-OwnerHash $before[$path]) -ne (Get-OwnerHash $Expected[$path]))){throw 'OwnerConfig: target changed during preflight; no writes.'}
    }
    $needed=@($Writes.Keys | Where-Object {$null -eq $before[$_] -or (Get-OwnerHash $before[$_]) -ne (Get-OwnerHash $Writes[$_])})
    if(!$needed.Count){return}
    $backupRoot=Join-Path ([IO.Path]::GetTempPath()) ('OwnerConfigBackup-'+[guid]::NewGuid().ToString('N'))
    Assert-OwnerPlain $backupRoot
    New-Item -ItemType Directory -Path $backupRoot | Out-Null
    $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    Set-Acl -LiteralPath $backupRoot -AclObject $acl
    $backups=@();$ordinal=0
    foreach($path in $needed){
        if($null -eq $before[$path]){$backups+=@{path=$path;backup=$null;sha256=$null};continue}
        $copy=Join-Path $backupRoot ($ordinal.ToString()+'.bin');$ordinal++
        [IO.File]::WriteAllBytes($copy,$before[$path])
        $hash=Get-OwnerHash $before[$path]
        if((Get-OwnerHash ([IO.File]::ReadAllBytes($copy))) -ne $hash){throw 'OwnerConfig: backup SHA-256 verification failed; no writes.'}
        $backups+=@{path=$path;backup=[IO.Path]::GetFileName($copy);sha256=$hash}
    }
    [IO.File]::WriteAllText((Join-Path $backupRoot 'manifest.json'),($backups | ConvertTo-Json -Depth 3),[Text.UTF8Encoding]::new($false))
    try{
        foreach($path in $needed){$changed.Add($path);Write-OwnerAtomic $path ([byte[]]$Writes[$path])}
    }catch{
        $restored=$true
        foreach($path in $changed){try{if($null -eq $before[$path]){if(Test-Path -LiteralPath $path){Assert-OwnerPlain $path;Remove-Item -LiteralPath $path -Force}}else{if(!(Test-Path -LiteralPath $path) -or (Get-OwnerHash ([IO.File]::ReadAllBytes($path))) -ne (Get-OwnerHash $before[$path])){[IO.File]::WriteAllBytes($path,$before[$path])};if((Get-OwnerHash ([IO.File]::ReadAllBytes($path))) -ne (Get-OwnerHash $before[$path])){$restored=$false}}}catch{$restored=$false}}
        if(!$restored){throw "OwnerConfig: I/O failure; rollback could not be verified. Stop; private backups=$backupRoot"}
        throw "OwnerConfig: I/O failure; original bytes restored and SHA-256 verified; private backups=$backupRoot"
    }
}

function Read-OwnerProfile([string]$Path){
    $document=Read-OwnerText $Path
    try{$data=$document.text | ConvertFrom-Json}catch{throw 'OwnerConfig: invalid profile JSON; values withheld.'}
    $rootKeys=@($data.PSObject.Properties.Name | Sort-Object)
    if(($rootKeys -join ',') -cne 'files,format,overrides' -or $data.format -ne 1 -or $null -eq $data.files -or $null -eq $data.overrides){throw 'OwnerConfig: profile format/files/overrides required; unknown fields forbidden.'}
    $catalog=@{}
    foreach($property in $data.files.PSObject.Properties){
        Assert-OwnerRelative $property.Name
        if($property.Value -notin @('public','excluded') -or ((Test-OwnerPrivateFile $property.Name) -and $property.Value -ne 'excluded')){throw 'OwnerConfig: invalid or private file classification.'}
        $catalog[$property.Name]=[string]$property.Value
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($entry in @($data.overrides)){
        if((@($entry.PSObject.Properties.Name | Sort-Object) -join ',') -cne 'baseValue,file,key,kind,value'){throw 'OwnerConfig: override needs exactly file/key/kind/baseValue/value.'}
        foreach($field in @('file','key','kind','baseValue','value')){if($entry.$field -isnot [string]){throw 'OwnerConfig: override fields must be strings.'}}
        Assert-OwnerRelative $entry.file
        if(!$catalog.ContainsKey($entry.file) -or $catalog[$entry.file] -ne 'public' -or (Test-OwnerPrivateFile $entry.file) -or (Test-OwnerPrivateKey $entry.key) -or (Test-OwnerPrivateValue $entry.value) -or (Test-OwnerPrivateValue $entry.baseValue)){throw 'OwnerConfig: private or unclassified override refused; values withheld.'}
        if($entry.key -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]*$' -or !$seen.Add($entry.file+"`t"+$entry.key)){throw 'OwnerConfig: invalid or duplicate override identity.'}
        $null=ConvertTo-OwnerValue $entry.kind $entry.baseValue
        $null=ConvertTo-OwnerValue $entry.kind $entry.value
    }
    return [pscustomobject]@{document=$document;data=$data;catalog=$catalog}
}
function Get-OwnerFiles([string]$Root,$Catalog,[string]$Operation){
    $config=Join-Path $Root 'config'
    $pending=[Collections.Generic.Stack[string]]::new();$pending.Push($config)
    $files=@{}
    while($pending.Count){
        foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force){
            Assert-OwnerPlain $item.FullName
            if($item.PSIsContainer){$pending.Push($item.FullName);continue}
            if($item.Extension -ine '.ini'){continue}
            $relative=$item.FullName.Substring($config.Length+1).Replace('\','/')
            Assert-OwnerRelative $relative
            if((Test-OwnerPrivateFile $relative) -or ($Catalog.ContainsKey($relative) -and $Catalog[$relative] -eq 'excluded')){continue}
            $document=Read-OwnerText $item.FullName
            $new=!$Catalog.ContainsKey($relative)
            if($new -and $Operation -ne 'Save'){continue}
            if($new -and $document.text -notmatch '(?im)^\s*#\s*OwnerConfig:\s*public\s*$'){throw "OwnerConfig: unclassified INI $relative; no writes."}
            $entries=Read-OwnerIni $document.text $relative
            if($new){
                foreach($entry in $entries.Values){if((Test-OwnerPrivateKey $entry.key) -or (Test-OwnerPrivateValue $entry.value)){throw 'OwnerConfig: new public INI contains protected material; values withheld.'}}
                $Catalog[$relative]='public'
            }
            $files[$relative]=[pscustomobject]@{document=$document;entries=$entries}
        }
    }
    return $files
}
function New-OwnerProjection([string]$Root,$Files,[bool]$Save,[string]$CanonicalDirectory=''){
    if(!$CanonicalDirectory){$CanonicalDirectory=Join-Path $Root 'config'}
    $tsv=Read-OwnerText (Join-Path $CanonicalDirectory 'OwnerProfile38.tsv')
    $preserved=Read-OwnerText (Join-Path $CanonicalDirectory 'OwnerProfilePreserved.properties')
    $newTsv=$tsv.text;$newPreserved=$preserved.text
    if($Save){
        foreach($row in ($tsv.text | ConvertFrom-Csv -Delimiter "`t")){
            if($row.file -notlike 'dist/game/config/*.ini' -or $row.section -eq 'QUEST'){continue}
            $file=$row.file.Substring('dist/game/config/'.Length)
            if(!$Files.ContainsKey($file) -or !$Files[$file].entries.ContainsKey($row.key_or_range) -or (Test-OwnerPrivateKey $row.key_or_range)){throw 'OwnerConfig: missing or protected current38 key.'}
            $value=$Files[$file].entries[$row.key_or_range].value
            $prefix=$row.section+"`t"+$row.file+"`t"+$row.key_or_range+"`t"
            $pattern='(?m)^'+[regex]::Escape($prefix)+'[^\t\r\n]*'
            if([regex]::Matches($newTsv,$pattern).Count -ne 1){throw 'OwnerConfig: duplicate current38 projection.'}
            $newTsv=[regex]::Replace($newTsv,$pattern,{param($m) $prefix+$value})
        }
        if(!$Files.ContainsKey('Rates.ini')){throw 'OwnerConfig: public Rates.ini required for quest projection.'}
        [string[]]$keys=@($Files['Rates.ini'].entries.Keys | Where-Object {$_ -cmatch 'Quest'})
        [Array]::Sort($keys,[StringComparer]::Ordinal)
        if(!$keys.Count){throw 'OwnerConfig: empty quest key projection.'}
        $canonical=(@($keys | ForEach-Object {$_+'='+$Files['Rates.ini'].entries[$_].value+"`n"}) -join '')
        $digest=Get-OwnerHash ([Text.Encoding]::UTF8.GetBytes($canonical))
        $newPreserved=Set-OwnerIniValue $newPreserved 'quest.keys' ($keys -join ',')
        $newPreserved=Set-OwnerIniValue $newPreserved 'quest.sha256' $digest
    }
    return [pscustomobject]@{tsv=$tsv;preserved=$preserved;tsvText=$newTsv;preservedText=$newPreserved}
}
function Test-OwnerStage([string]$Root,$Files,$Projection){
    $stageParent=Join-Path $script:OwnerModule '.phantom-local'
    Assert-OwnerPlain $stageParent
    $temporary=[IO.Path]::GetFullPath((Join-Path $stageParent ('OwnerConfig-'+[guid]::NewGuid().ToString('N'))))
    Assert-OwnerPlain $temporary
    try{
        New-Item -ItemType Directory -Path (Join-Path $temporary 'config'),(Join-Path $temporary 'data/xsd') -Force | Out-Null
        [IO.File]::WriteAllText((Join-Path $temporary 'config/OwnerProfile38.tsv'),$Projection.tsvText,[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $temporary 'config/OwnerProfilePreserved.properties'),$Projection.preservedText,[Text.UTF8Encoding]::new($false))
        $selected=@{}
        foreach($row in ($Projection.tsvText | ConvertFrom-Csv -Delimiter "`t")){
            if($row.file -notlike 'dist/game/config/*.ini'){continue}
            $file=$row.file.Substring('dist/game/config/'.Length)
            Assert-OwnerRelative $file
            if((Test-OwnerPrivateFile $file) -or (Test-OwnerPrivateKey $row.key_or_range) -and $row.section -ne 'QUEST'){throw 'OwnerConfig: protected projection refused.'}
            if(!$Files.ContainsKey($file)){throw 'OwnerConfig: required public current38 INI missing.'}
            if(!$selected.ContainsKey($file)){$selected[$file]=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)}
            if($row.section -eq 'QUEST'){foreach($key in $Files[$file].entries.Keys){if($key -cmatch 'Quest'){$null=$selected[$file].Add($key)}}}else{$null=$selected[$file].Add($row.key_or_range)}
        }
        foreach($file in $selected.Keys){
            $lines=@(foreach($key in $selected[$file]){
                if(!$Files[$file].entries.ContainsKey($key)){throw 'OwnerConfig: current38 key missing.'}
                $value=$Files[$file].entries[$key].value
                if((Test-OwnerPrivateKey $key) -or (Test-OwnerPrivateValue $value)){throw 'OwnerConfig: protected current38 value; withheld.'}
                $key+'='+$value
            })
            $path=Join-Path $temporary ('config/'+$file)
            New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force | Out-Null
            [IO.File]::WriteAllText($path,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
        }
        foreach($relative in @('data/EnchantItemGroups.xml','data/EnchantItemData.xml','data/xsd/enchantItemGroups.xsd','data/xsd/enchantItemData.xsd')){
            $path=Join-Path $Root $relative;Assert-OwnerPlain $path
            Copy-Item -LiteralPath $path -Destination (Join-Path $temporary $relative)
        }
        $source=Join-Path $script:OwnerModule 'java/org/l2jmobius/gameserver/config/OwnerProfileVerifier.java'
        Assert-OwnerPlain $source
        $result=Invoke-OwnerNative $script:OwnerJava @($source,$temporary) $script:OwnerModule
        if($result.code -ne 0 -or $result.output -notmatch 'OWNER_PROFILE_PASS rules=38'){
            $failed=@($result.output -split '\r?\n' | Where-Object {$_ -match '^OWNER_RULE\t.*\tFAIL$'} | ForEach-Object {$columns=$_ -split "`t";$columns[2]+'/'+$columns[3]})
            $reason=@($result.error -split '\r?\n' | Where-Object {$_ -match '^OWNER_PROFILE_FAIL:'}) -join '; '
            throw ('OwnerConfig: current38/quest/XML validation failed; no writes. '+($failed -join '; ')+' '+$reason)
        }
    }finally{
        $expected=Join-Path $stageParent ([IO.Path]::GetFileName($temporary))
        if($temporary -ine [IO.Path]::GetFullPath($expected) -or [IO.Path]::GetFileName($temporary) -notmatch '^OwnerConfig-[0-9a-f]{32}$'){throw 'OwnerConfig: temporary cleanup boundary refused.'}
        if(Test-Path -LiteralPath $temporary){Assert-OwnerPlain $temporary;foreach($item in Get-ChildItem -LiteralPath $temporary -Recurse -Force){Assert-OwnerPlain $item.FullName};Remove-Item -LiteralPath $temporary -Recurse -Force}
    }
}

function Assert-OwnerTyped([string]$File,[string]$Key,[string]$Kind,[string]$Value){
    try{return ConvertTo-OwnerValue $Kind $Value}catch{throw "OwnerConfig: type/range conflict file=$File key=$Key; values withheld."}
}
function Invoke-OwnerConfig {
    [CmdletBinding()]
    param([Parameter(Mandatory)][ValidateSet('Save','Verify','Apply')][string]$Action,[string]$GameRoot='',[string]$ProfilePath='',[string]$ManagedRevision='HEAD',[string]$JavaHome='')
    $ErrorActionPreference='Stop'
    if(!$GameRoot){$GameRoot=Join-Path $script:OwnerModule 'dist/game'}
    $root=[IO.Path]::GetFullPath($GameRoot).TrimEnd('\')
    if((Split-Path $root -Leaf) -ine 'game' -or !(Test-Path -LiteralPath $root -PathType Container)){throw 'OwnerConfig: an existing game root is required.'}
    if($root.Replace('\','/') -match '(?i)/(?:task0(?:5[7-9]|6[0-9]|7[0-4])|[^/]+-0(?:5[7-9]|6[0-9]|7[0-4])-candidate)(?:/|$)'){throw 'OwnerConfig: historical runtime roots are protected.'}
    Assert-OwnerPlain $root
    $sourceTarget=$root.Equals([IO.Path]::GetFullPath((Join-Path $script:OwnerModule 'dist/game')).TrimEnd('\'),[StringComparison]::OrdinalIgnoreCase)
    $jdk=if($JavaHome){[IO.Path]::GetFullPath($JavaHome)}elseif($env:JAVA_HOME){[IO.Path]::GetFullPath($env:JAVA_HOME)}else{Split-Path (Split-Path (Get-Command java.exe -ErrorAction Stop).Source -Parent) -Parent}
    Assert-OwnerPlain $jdk
    if(!(Test-Path -LiteralPath (Join-Path $jdk 'release')) -or [IO.File]::ReadAllText((Join-Path $jdk 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'OwnerConfig: explicit/configured JDK25 required.'}
    $script:OwnerJava=Join-Path $jdk 'bin/java.exe'
    Assert-OwnerPlain $script:OwnerJava
    if(!$ProfilePath){$ProfilePath=Join-Path $script:OwnerModule 'dist/game/config/OwnerConfig.json'}
    $profilePath=[IO.Path]::GetFullPath($ProfilePath)
    Assert-OwnerPlain $profilePath
    if((Split-Path $profilePath -Leaf) -ine 'OwnerConfig.json'){throw 'OwnerConfig: expected an explicit OwnerConfig.json profile.'}
    if($Action -eq 'Save' -and (Split-Path $profilePath -Parent) -ine (Join-Path $root 'config')){throw 'OwnerConfig: Save profile must belong to its source game/config.'}
    $script:OwnerManagedCache=@{};$script:OwnerGitRoot='';$script:OwnerRevisions=@{}
    $profile=Read-OwnerProfile $profilePath
    $files=Get-OwnerFiles $root $profile.catalog $Action
    $writes=[ordered]@{}
    $metadataBefore=@{}
    $existing=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($entry in @($profile.data.overrides)){
        $identity=$entry.file+"`t"+$entry.key;$existing.Add($identity,$entry)
        if(!$files.ContainsKey($entry.file) -or !$files[$entry.file].entries.ContainsKey($entry.key)){throw "OwnerConfig: missing/renamed target file=$($entry.file) key=$($entry.key); no writes."}
        $current=$files[$entry.file].entries[$entry.key].value
        if((Test-OwnerPrivateKey $entry.key) -or (Test-OwnerPrivateValue $current)){throw "OwnerConfig: protected target file=$($entry.file) key=$($entry.key); values withheld."}
        $null=Assert-OwnerTyped $entry.file $entry.key $entry.kind $current
        if($Action -eq 'Save'){
            $headText=Read-OwnerManagedFile $entry.file 'HEAD'
            $head=if($null -ne $headText){Read-OwnerIni $headText $entry.file}else{$null}
            if($null -eq $head -or !$head.ContainsKey($entry.key)){throw "OwnerConfig: managed key deleted file=$($entry.file) key=$($entry.key); no writes."}
        }
    }
    if($Action -eq 'Save'){
        $overrides=[Collections.Generic.List[object]]::new()
        foreach($file in @($files.Keys | Sort-Object)){
            $baseText=Read-OwnerManagedFile $file $ManagedRevision
            $headText=$null
            if($null -eq $baseText -and $ManagedRevision -ne 'HEAD'){$headText=Read-OwnerManagedFile $file 'HEAD';$baseText=$headText}
            $base=if($null -ne $baseText){Read-OwnerIni $baseText $file}else{$null}
            foreach($entry in $files[$file].entries.Values){
                if((Test-OwnerPrivateKey $entry.key) -or (Test-OwnerPrivateValue $entry.value)){continue}
                $identity=$file+"`t"+$entry.key
                if($existing.ContainsKey($identity)){$old=$existing[$identity];$baseValue=$old.baseValue;$kind=$old.kind}
                else{
                    if($null -eq $base -or !$base.ContainsKey($entry.key)){
                        if($ManagedRevision -ne 'HEAD'){
                            if($null -eq $headText){$headText=Read-OwnerManagedFile $file 'HEAD'}
                            $head=if($null -ne $headText){Read-OwnerIni $headText $file}else{$null}
                            if($null -ne $head -and $head.ContainsKey($entry.key)){$baseValue=$head[$entry.key].value}else{throw "OwnerConfig: managed default missing file=$file key=$($entry.key); introduce it in managed source first."}
                        }else{throw "OwnerConfig: managed default missing file=$file key=$($entry.key); introduce it in managed source first."}
                    }else{$baseValue=$base[$entry.key].value}
                    if((Test-OwnerPrivateValue $baseValue)){continue}
                    if($entry.value -ceq $baseValue){continue}
                    $kind=Get-OwnerKind $file $entry.key $baseValue
                }
                $baseValueNormalized=Assert-OwnerTyped $file $entry.key $kind $baseValue
                $desired=Assert-OwnerTyped $file $entry.key $kind $entry.value
                if($baseValueNormalized -cne $desired){$overrides.Add([ordered]@{file=$file;key=$entry.key;kind=$kind;baseValue=$baseValue;value=$entry.value})}
            }
        }
        $catalog=[ordered]@{};foreach($file in @($profile.catalog.Keys | Sort-Object)){$catalog[$file]=$profile.catalog[$file]}
        $data=[ordered]@{format=1;files=$catalog;overrides=@($overrides | Sort-Object file,key)}
        $projection=New-OwnerProjection $root $files $true
        Test-OwnerStage $root $files $projection
        $writes[$profilePath]=ConvertTo-OwnerBytes (($data | ConvertTo-Json -Depth 6)+"`n") $profile.document.bom
        $writes[$projection.tsv.path]=ConvertTo-OwnerBytes $projection.tsvText $projection.tsv.bom
        $writes[$projection.preserved.path]=ConvertTo-OwnerBytes $projection.preservedText $projection.preserved.bom
        $count=$overrides.Count
    }else{
        foreach($entry in @($profile.data.overrides)){
            $current=$files[$entry.file].entries[$entry.key].value
            $actual=Assert-OwnerTyped $entry.file $entry.key $entry.kind $current
            $desired=Assert-OwnerTyped $entry.file $entry.key $entry.kind $entry.value
            if($actual -ceq $desired){continue}
            if($Action -eq 'Verify'){throw "OwnerConfig: saved value drift file=$($entry.file) key=$($entry.key); no writes."}
            if($sourceTarget){
                $headText=Read-OwnerManagedFile $entry.file 'HEAD'
                $head=if($null -ne $headText){Read-OwnerIni $headText $entry.file}else{$null}
                if($null -eq $head -or !$head.ContainsKey($entry.key) -or (Assert-OwnerTyped $entry.file $entry.key $entry.kind $head[$entry.key].value) -cne $actual){throw "OwnerConfig: unsaved SOURCE change file=$($entry.file) key=$($entry.key); Save first, no writes."}
            }
            $document=$files[$entry.file].document
            $document.text=Set-OwnerIniValue $document.text $entry.key $entry.value
            $files[$entry.file].entries=Read-OwnerIni $document.text $entry.file
            $writes[$document.path]=ConvertTo-OwnerBytes $document.text $document.bom
        }
        $projection=New-OwnerProjection $root $files $false (Split-Path $profilePath -Parent)
        Test-OwnerStage $root $files $projection
        $metadata=[ordered]@{
            'OwnerProfile38.tsv'=$projection.tsv.bytes
            'OwnerProfilePreserved.properties'=$projection.preserved.bytes
            'OwnerConfig.json'=$profile.document.bytes
        }
        foreach($name in $metadata.Keys){
            $path=Join-Path $root ('config/'+$name)
            Assert-OwnerPlain $path
            if((Test-Path -LiteralPath $path) -and !(Test-Path -LiteralPath $path -PathType Leaf)){throw "OwnerConfig: metadata target is not a file file=config/$name; no writes."}
            $bytes=if(Test-Path -LiteralPath $path -PathType Leaf){[IO.File]::ReadAllBytes($path)}else{$null}
            if($Action -eq 'Verify'){
                if($null -eq $bytes -or (Get-OwnerHash $bytes) -cne (Get-OwnerHash $metadata[$name])){throw "OwnerConfig: saved metadata drift file=config/$name; no writes."}
            }else{
                $metadataBefore[$path]=$bytes
                $writes[$path]=[byte[]]$metadata[$name]
            }
        }
        $count=@($profile.data.overrides).Count
    }
    $changed=@(foreach($path in $writes.Keys){if(!(Test-Path -LiteralPath $path) -or (Get-OwnerHash ([IO.File]::ReadAllBytes($path))) -ne (Get-OwnerHash $writes[$path])){$path}})
    if($Action -ne 'Verify'){
        $expected=@{}
        foreach($path in $writes.Keys){
            if($metadataBefore.ContainsKey($path)){$expected[$path]=$metadataBefore[$path]}
            elseif($path -eq $profilePath){$expected[$path]=$profile.document.bytes}
            elseif($path -eq $projection.tsv.path){$expected[$path]=$projection.tsv.bytes}
            elseif($path -eq $projection.preserved.path){$expected[$path]=$projection.preserved.bytes}
            else{$relative=$path.Substring((Join-Path $root 'config').Length+1).Replace('\','/');$expected[$path]=$files[$relative].document.bytes}
        }
        Write-OwnerTransaction $writes $expected
    }
    $names=@($changed | ForEach-Object {if($_.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){$_.Substring($root.Length+1).Replace('\','/')}else{[IO.Path]::GetFileName($_)}})
    Write-Output ("OwnerConfig {0} OK filesChecked={1} overridesStored={2} conflicts=0 changed={3} paths=[{4}]; Проверено файлов: {1}; сохранено переопределений: {2}; конфликтов: 0; изменено путей: {3}." -f $Action,$files.Count,$count,$changed.Count,($names -join ','))
}

function Format-OwnerConflict([string]$Message){
    $reason=switch -Regex ($Message){
        'unsaved SOURCE' {'Есть несохранённое изменение SOURCE. Сначала выполните Save.';break}
        'duplicate' {'Найден повтор ключа или записи профиля.';break}
        'type|range' {'Тип или допустимый диапазон значения не соответствует управляемому ключу.';break}
        'missing|deleted|renamed|managed default' {'Отсутствует управляемый файл или ключ. Сначала добавьте новый ключ в управляемый исходник.';break}
        'unclassified' {'Неизвестный INI требует классификации или комментария # OwnerConfig: public.';break}
        'private|protected|classification' {'Приватные данные запрещены в профиле. Значения скрыты.';break}
        'JSON|profile format|override needs' {'Неверная структура JSON-профиля. Значения скрыты.';break}
        'current38|quest|XML' {'Проверка 38 правил, quest baseline или XML не пройдена. Изменения не применены.';break}
        'saved (?:value|metadata) drift' {'Фактическое значение или служебный профиль отличается от сохранённого канонического профиля.';break}
        'rollback could not' {'Ошибка записи; восстановление не подтверждено. Остановитесь и сохраните резервные копии.';break}
        'I/O failure' {'Ошибка записи. Исходные байты восстановлены, SHA-256 проверены.';break}
        'JDK25' {'Требуется настроенный JDK25 либо явный параметр -JavaHome.';break}
        default {'Проверка безопасности или формата не пройдена. Изменения не применены.'}
    }
    $context=[regex]::Match($Message,'file=([^\r\n]+?) key=([A-Za-z0-9_.-]+)').Value
    if(!$context){$context=[regex]::Match($Message,'file=([^;\r\n]+)').Value}
    if(!$context){$context=[regex]::Match($Message,'(?:in |INI )([^;\r\n]+\.ini)').Value}
    $backup=[regex]::Match($Message,'private backups=([^\r\n]+)').Value
    return $reason+' '+$context+' '+$backup
}

if($MyInvocation.InvocationName -ne '.'){
    try{Invoke-OwnerConfig -Action $Action -GameRoot $GameRoot -ProfilePath $ProfilePath -ManagedRevision $ManagedRevision -JavaHome $JavaHome;exit 0}
    catch{$state=if($_.Exception.Message -match 'rollback could not'){'UNKNOWN'}else{'0'};Write-Error ("OwnerConfig CONFLICT conflicts=1 changed=$state; "+(Format-OwnerConflict $_.Exception.Message));exit 1}
}
