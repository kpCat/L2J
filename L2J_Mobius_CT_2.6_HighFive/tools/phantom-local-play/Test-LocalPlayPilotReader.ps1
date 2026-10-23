[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'LocalPlay-Pilot.ps1')
$root070=[IO.Path]::GetFullPath($OutputDirectory)
Assert-PilotNoReparse $root070
if(Test-Path -LiteralPath $root070){throw 'Preserve existing reader evidence; choose a new fixture path.'}
Protect-PilotDirectory $root070
$path070=Join-Path $root070 'session.properties'
Write-PilotAtomicText $path070 "version=1`nstate=BEFORE`n"
$retired070=Join-Path $root070 'retired.properties'
# Sharing DELETE permits renaming a held file; replacing a held destination has a separate writer contract.
$old070=[IO.File]::Open($path070,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
$blocked070=$false
try{
    try{[IO.File]::Move($path070,$retired070)}
    catch{
        $cause070=$_.Exception.GetBaseException()
        if($cause070 -isnot [IO.IOException] -and $cause070 -isnot [UnauthorizedAccessException]){throw}
        $blocked070=$true
    }
}finally{$old070.Dispose()}
if(!$blocked070){throw 'Former reader did not reproduce Windows rename denial.'}
$shared070=[IO.File]::Open($path070,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
try{
    [IO.File]::Move($path070,$retired070)
    Write-PilotAtomicText $path070 "version=1`nstate=AFTER`n"
    $reader070=[IO.StreamReader]::new($shared070,[Text.UTF8Encoding]::new($false,$true),$true)
    try{$oldText070=$reader070.ReadToEnd()}finally{$reader070.Dispose()}
}finally{$shared070.Dispose()}
if($oldText070 -cne "version=1`nstate=BEFORE`n"){throw 'Atomic reader did not retain its complete old snapshot.'}
if((Read-PilotProperties $path070).state -cne 'AFTER'){throw 'Current private record did not parse after replacement.'}
'PILOT_READER_SHARE_PASS; oldReadBlocksRename=true; sharedDeleteAllowsRename=true; stableOldSnapshot=true; currentRecordParsed=true; DB_WRITES=0'
