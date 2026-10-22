Set-StrictMode -Version Latest

# The verified067 contracts are reused with one explicit069 binding. Historical tools stay independent.
function Assert-Plain069([string]$Path){
    $absolute=[IO.Path]::GetFullPath($Path)
    $walk=[IO.Path]::GetPathRoot($absolute)
    foreach($part in $absolute.Substring($walk.Length).Split('\')){
        if(!$part){continue}
        $walk=Join-Path $walk $part
        if((Test-Path -LiteralPath $walk) -and ((Get-Item -LiteralPath $walk -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'TASK069: plain physical paths required.'}
    }
}
function Read-Field069([string]$Path,[string]$Key){
    Assert-Plain069 $Path
    if((Get-Item -LiteralPath $Path).Length -gt 16384){throw 'TASK069: bounded configuration required.'}
    $fields=[regex]::Matches([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if($fields.Count -ne 1){throw "TASK069: need exactly one key $Key."}
    return $fields[0].Groups[1].Value.Trim()
}
function Read-Json069([string]$Path){
    Assert-Plain069 $Path
    if((Get-Item -LiteralPath $Path).Length -gt 32768){throw 'TASK069: bounded manifest required.'}
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}
function Write-Text069([string]$Path,[string]$Text,[bool]$Bom=$false){
    Assert-Plain069 $Path
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($Bom))
}
function Get-Binding069([string]$ModuleRoot,[ValidateSet('Test','Release')][string]$Kind){
    $module=[IO.Path]::GetFullPath($ModuleRoot).TrimEnd('\')
    if((Split-Path $module -Leaf) -cne 'L2J_Mobius_CT_2.6_HighFive'){throw 'TASK069: exact High Five module required.'}
    $task=Join-Path (Split-Path $module -Parent) '.phantom-local/task069'
    if($Kind -ieq 'Test'){
        return [pscustomobject]@{owner='TASK069_PLAYABLE_RELEASE';schema='l2jmobiush5_phantom_test_069';role='MUTABLE_FIXTURE';root=(Join-Path $task 'runtime-test');ports=@(2250,7950,9250);task=$task;module=$module}
    }
    return [pscustomobject]@{owner='TASK069_PLAYABLE_RELEASE';schema='l2jmobiush5_phantom_release_069';role='RELEASE_CANDIDATE';root=(Join-Path ([IO.Path]::GetPathRoot($module)) 'PhantomWorld/Playable-069-candidate');ports=@(2106,7777,9150);task=$task;module=$module}
}
function Assert-Binding069([string]$RuntimeRoot,[string]$ModuleRoot,[string]$Schema,[string]$Role){
    $kind=if($Schema -ceq 'l2jmobiush5_phantom_test_069'){'Test'}elseif($Schema -ceq 'l2jmobiush5_phantom_release_069'){'Release'}else{throw 'TASK069: historical/foreign schema forbidden.'}
    $binding=Get-Binding069 $ModuleRoot $kind
    if([IO.Path]::GetFullPath($RuntimeRoot).TrimEnd('\') -ine $binding.root -or $Role -cne $binding.role){throw 'TASK069: exact runtime root and role required.'}
    Assert-Plain069 $RuntimeRoot
    return $binding
}
function Assert-Jdbc069([string]$Url,[string]$Schema){
    if($Schema -cnotin @('l2jmobiush5_phantom_test_069','l2jmobiush5_phantom_release_069') -or $Url -cnotmatch ('^jdbc:(mysql|mariadb)://127\.0\.0\.1:3308/'+[regex]::Escape($Schema)+'\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?$')){throw 'TASK069: exact own loopback JDBC binding required.'}
}
function Assert-Java069([string]$JavaHome){
    foreach($relative in @('bin/java.exe','bin/javac.exe','release')){Assert-Plain069 (Join-Path $JavaHome $relative)}
    if(!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')) -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe')) -or [IO.File]::ReadAllText((Join-Path $JavaHome 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")'){throw 'TASK069: existing JDK25 required.'}
}
function Get-TreeHash069([string]$Root,[string[]]$Entries,[string[]]$Exclude=@()){
    Assert-Plain069 $Root
    $lines=@(foreach($entry in $Entries){
        $path=Join-Path $Root $entry
        if(!(Test-Path -LiteralPath $path)){throw "TASK069: tree entry missing: $entry"}
        foreach($file in Get-ChildItem -LiteralPath $path -File -Recurse | Sort-Object FullName){
            Assert-Plain069 $file.FullName
            $relative=$file.FullName.Substring($Root.Length+1).Replace('\','/')
            if($relative -cin $Exclude){continue}
            $relative+':'+(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        }
    })
    if(!$lines.Count){throw 'TASK069: empty provenance tree.'}
    $sha=[Security.Cryptography.SHA256]::Create()
    try{return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))))).Replace('-','')}
    finally{$sha.Dispose()}
}
function Set-Config069([string]$Root,[string]$Relative,[string]$Key,[string]$Value){
    $path=Join-Path $Root $Relative
    Assert-Plain069 $path
    $text=[IO.File]::ReadAllText($path)
    $pattern='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if([regex]::Matches($text,$pattern).Count -ne 1){throw "TASK069: need one $Key in $Relative."}
    Write-Text069 $path ([regex]::Replace($text,$pattern,{param($match) $match.Groups[1].Value+$Value}))
}
function Set-Private069([string]$Path){
    Assert-Plain069 $Path
    $sid='*'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $grant=if((Get-Item -LiteralPath $Path).PSIsContainer){$sid+':(OI)(CI)F'}else{$sid+':F'}
    & icacls.exe $Path /inheritance:r /remove:g '*S-1-5-18' '*S-1-5-32-544' /grant:r $grant | Out-Null
    if($LASTEXITCODE){throw 'TASK069: private DACL failed.'}
}
