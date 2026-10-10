# Atomic Java telemetry replacement must remain possible while Windows reads a frame.
function Read-SharedJson032([string]$Path){
    $stream=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader=$null
    try{
        $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true)
        $raw=$reader.ReadToEnd()
    }finally{
        if($reader){$reader.Dispose()}else{$stream.Dispose()}
    }
    return ($raw | ConvertFrom-Json)
}
