param()
$ErrorActionPreference='Stop'
$fixture042=Join-Path $PSScriptRoot 'evidence/FRAME_SHARING042_V2'
if(Test-Path $fixture042){throw 'Immutable fixture exists.'}
New-Item -ItemType Directory -Path $fixture042 | Out-Null
$source042=Join-Path $fixture042 'next.json'
$target042=Join-Path $fixture042 'current.json'
$backup042=Join-Path $fixture042 'previous.json'
[IO.File]::WriteAllText($source042,'{"generation":2}')
[IO.File]::WriteAllText($target042,'{"generation":1}')
$denied042=$false
$reader042=[IO.File]::Open($target042,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
try{try{[IO.File]::Replace($source042,$target042,$backup042)}catch [IO.IOException]{$denied042=$true}}
finally{$reader042.Dispose()}
if(-not $denied042 -or [IO.File]::ReadAllText($target042) -cne '{"generation":1}' -or -not (Test-Path $source042)){throw 'RED non-delete reader did not block atomic replacement.'}
$reader042=[IO.File]::Open($target042,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
try{[IO.File]::Replace($source042,$target042,$backup042)}finally{$reader042.Dispose()}
if([IO.File]::ReadAllText($target042) -cne '{"generation":2}' -or (Test-Path $source042)){throw 'GREEN delete-sharing publication differs.'}
[pscustomobject]@{negativeNoDeleteShareRejected=$true;positiveDeleteShareAllowed=$true;atomicBytesPreserved=$true;
    productionChange=$false;collectorChange=$false;diagnosticReaderCorrected=$true} | ConvertTo-Json |
    Set-Content (Join-Path $fixture042 'result.json') -Encoding utf8
'FRAME_SHARING042 RED non-delete rejected; GREEN delete-share allowed; negative old bytes retained.'
