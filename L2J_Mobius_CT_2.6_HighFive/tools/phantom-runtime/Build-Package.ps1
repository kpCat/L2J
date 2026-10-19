[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputRoot,
    [Parameter(Mandatory)][string]$StageRoot,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$Ant,
    [Parameter(Mandatory)][string]$GeoDataRoot,
    [Parameter(Mandatory)][ValidatePattern('^l2jmobiush5_phantom_(test_041final|test_058a|release_[A-Za-z0-9_]{1,24})$')][string]$DatabaseName,
    [Parameter(Mandatory)][string]$DatabaseConfigRoot,
    [Parameter(Mandatory)][string]$RegistrationFile,
    [ValidateRange(1024,65535)][int]$GamePort=7792,
    [ValidateRange(1024,65535)][int]$ClientLoginPort=2122,
    [ValidateRange(1024,65535)][int]$ControlPort=9032,
    [ValidateLength(1,4096)]
    [ValidatePattern('^[A-Za-z0-9_-]{1,45}(?:[ \t]*[,;][ \t]*[A-Za-z0-9_-]{1,45}){0,255}$')][string]$Account='localplayer',
    [ValidatePattern('^[A-Za-z0-9_-]{1,35}$')][string]$ObserverCharacter='TestAdmin',
    [Parameter(Mandatory)][ValidateRange(1,2147483647)][int]$ObserverObjectId,
    [ValidateSet('M1Accepted','Campaign058','Campaign059','Campaign060','Campaign061')][string]$Profile='M1Accepted',
    [string]$OwnershipManifest='',
    [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$moduleRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$repositoryRoot=Split-Path $moduleRoot -Parent
$moduleName=Split-Path $moduleRoot -Leaf
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'release')) -or [IO.File]::ReadAllText((Join-Path $JavaHome 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")') {throw 'Требуется JDK25.'}
$output=[IO.Path]::GetFullPath($OutputRoot)
$stage=[IO.Path]::GetFullPath($StageRoot)
if ($Profile -in @('Campaign058','Campaign059','Campaign060','Campaign061')) {
    if ($moduleRoot -ine 'D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive' -or !$OwnershipManifest) {throw 'TASK058: нужны точные OWN и owner manifest.'}
    . (Join-Path $PSScriptRoot 'Campaign058Ownership.ps1')
    $campaignOwner=Read-Campaign058Owner $OwnershipManifest
    Assert-Campaign058Binding $output $DatabaseName $campaignOwner
    if ($Profile -ceq 'Campaign059' -and ($DatabaseName -cne 'l2jmobiush5_phantom_release_059a' -or $stage -ine (Join-Path $moduleRoot '.phantom-local/contract058a/build/release059'))) {throw 'TASK059: нужны exact release059 schema и clean staging.'}
    if ($Profile -ceq 'Campaign060' -and ($DatabaseName -cne 'l2jmobiush5_phantom_release_060a' -or $stage -ine (Join-Path $moduleRoot '.phantom-local/contract058a/build/release060'))) {throw 'TASK060: нужны exact release060 schema и clean staging.'}
    if ($Profile -ceq 'Campaign061' -and ($DatabaseName -cne 'l2jmobiush5_phantom_release_061a' -or $stage -ine (Join-Path $moduleRoot '.phantom-local/contract058a/build/release061'))) {throw 'TASK061: нужны exact release061 schema и clean staging.'}
    if ($Profile -ceq 'Campaign058' -and $DatabaseName -in @('l2jmobiush5_phantom_release_059a','l2jmobiush5_phantom_release_060a','l2jmobiush5_phantom_release_061a')) {throw 'Требуется отдельный exact campaign profile.'}
    if (!$stage.StartsWith((Join-Path $moduleRoot '.phantom-local/contract058a/build')+'\',[StringComparison]::OrdinalIgnoreCase)) {throw 'TASK058: staging вне собственного build root запрещён.'}
    if ([string]$GamePort -cne $campaignOwner.gamePort -or [string]$ClientLoginPort -cne $campaignOwner.clientLoginPort -or [string]$ControlPort -cne $campaignOwner.controlPort) {throw 'TASK058: порты builder отличаются от owner manifest.'}
} elseif ($DatabaseName -in @('l2jmobiush5_phantom_test_058a','l2jmobiush5_phantom_release_058a','l2jmobiush5_phantom_release_059a','l2jmobiush5_phantom_release_060a','l2jmobiush5_phantom_release_061a')) {throw 'Для campaign среды требуется точный campaign profile.'}
if ((Test-Path -LiteralPath $output) -or (Test-Path -LiteralPath $stage)) { throw 'Нужны два новых пустых пути; overwrite запрещён.' }
if ($output.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase) -or $stage.StartsWith($output+'\',[StringComparison]::OrdinalIgnoreCase) -or $output -ieq $stage) { throw 'Stage/output должны быть независимы.' }
if (@(@($GamePort,$ClientLoginPort,$ControlPort) | Select-Object -Unique).Count -ne 3) { throw 'Порты должны различаться.' }
foreach ($path in @($output,$stage,[IO.Path]::GetFullPath($GeoDataRoot),[IO.Path]::GetFullPath($DatabaseConfigRoot))) {
    $current=[IO.Path]::GetPathRoot($path)
    foreach ($part in $path.Substring($current.Length).Split('\')) {
        if (!$part) {continue}; $current=Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw "Alias запрещён: $current"}
    }
}
function Set-Ini([string]$Path,[string]$Key,[string]$Value) {
    $text=[IO.File]::ReadAllText($Path)
    $pattern='(?m)^([ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*).*$'
    if ([regex]::Matches($text,$pattern).Count -ne 1) {throw "Ожидался один ключ $Key в$Path"}
    $text=[regex]::Replace($text,$pattern,{param($match) $match.Groups[1].Value+$Value})
    [IO.File]::WriteAllText($Path,$text,[Text.UTF8Encoding]::new($false))
}
function Read-Ini([string]$Path,[string]$Key) {
    $match=[regex]::Match([IO.File]::ReadAllText($Path),'(?m)^[ \t]*'+[regex]::Escape($Key)+'[ \t]*=[ \t]*([^\r\n]*)\r?$')
    if (!$match.Success) {throw "Ключ отсутствует:$Key"}; return $match.Groups[1].Value.Trim()
}
if ($ValidateOnly) {Write-Output "PACKAGE_PREFLIGHT_ONLY profile=$Profile schema=$DatabaseName; DB_WRITES=0; PROCESS_ACTIONS=0"; return}
Push-Location $repositoryRoot
try {
    $commit=(& git rev-parse HEAD).Trim(); if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {throw 'Git commit отсутствует.'}
    if ($Profile -in @('Campaign059','Campaign060','Campaign061')) {
        & git diff --quiet -- $moduleName; if($LASTEXITCODE -ne 0){throw 'TASK059: tracked source отличается от committed input.'}
        & git diff --cached --quiet -- $moduleName; if($LASTEXITCODE -ne 0){throw 'TASK059: index отличается от committed input.'}
    }
    # Commit archive is the complete source of code/config/tooling; untracked content is excluded.
    New-Item -ItemType Directory -Path $stage | Out-Null
    $archive=Join-Path $stage 'tracked-input.zip'
    & git archive --format=zip "--output=$archive" $commit -- $moduleName
    if ($LASTEXITCODE -ne 0) {throw 'git archive не прошёл.'}
} finally {Pop-Location}
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $stage 'source')
$cleanModule=Join-Path $stage "source/$moduleName"
$predecessor=Join-Path $cleanModule 'dist/game/data/phantoms/population/high-five-population-v1.xml'
if ((Get-FileHash -LiteralPath $predecessor).Hash -ine '23b12fc523de83d1bdea54a75677823b2e8129978792a9c9fec684accfaa4748') {throw 'Tracked population predecessor fingerprint differs; packaging refused.'}
$env:JAVA_HOME=$JavaHome
$savedPath=$env:PATH
try {
    $env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
    Push-Location $cleanModule
    try {
        & $Ant -q '-Dbuild=../../compiled' jar compile-tests
        if ($LASTEXITCODE -ne 0) {throw 'Clean Ant compile/package/tests compile не прошёл.'}
    } finally {Pop-Location}
} finally {$env:PATH=$savedPath}
New-Item -ItemType Directory -Path $output | Out-Null
Copy-Item -Path (Join-Path $cleanModule 'dist/*') -Destination $output -Recurse
if ($Profile -ceq 'Campaign061') {Copy-Item -LiteralPath (Join-Path $cleanModule 'dist/START_HERE_061_RU.md') -Destination (Join-Path $output 'START_HERE_RU.md') -Force; Copy-Item -LiteralPath (Join-Path $cleanModule 'dist/KNOWN_ISSUES_061_RU.md') -Destination (Join-Path $output 'KNOWN_ISSUES.md') -Force; $start061=Join-Path $output 'START_HERE_RU.md'; [IO.File]::WriteAllText($start061,[IO.File]::ReadAllText($start061).Replace('[LOGIN_PORT_061]',[string]$ClientLoginPort),[Text.UTF8Encoding]::new($false))}
elseif ($Profile -ceq 'Campaign060') {Copy-Item -LiteralPath (Join-Path $cleanModule 'dist/START_HERE_060_RU.md') -Destination (Join-Path $output 'START_HERE_RU.md') -Force; Copy-Item -LiteralPath (Join-Path $cleanModule 'dist/KNOWN_ISSUES_060_RU.md') -Destination (Join-Path $output 'KNOWN_ISSUES.md') -Force; $start060=Join-Path $output 'START_HERE_RU.md'; [IO.File]::WriteAllText($start060,[IO.File]::ReadAllText($start060).Replace('[LOGIN_PORT_060]',[string]$ClientLoginPort),[Text.UTF8Encoding]::new($false))}
elseif ($Profile -ceq 'Campaign059') {Copy-Item -LiteralPath (Join-Path $cleanModule 'dist/START_HERE_059_RU.md') -Destination (Join-Path $output 'START_HERE_RU.md') -Force}
$tools=Join-Path $output 'tools'; New-Item -ItemType Directory -Path $tools | Out-Null
foreach ($name in @('Runtime.ps1','Verify-M1.ps1','RequestShutdown.java','RuntimeShutdownAgent.java','RuntimeDatabaseCheck.java','RuntimeCampaign058Guard.java','Campaign058Ownership.ps1','runtime.local.ini.template')) {
    Copy-Item -LiteralPath (Join-Path $cleanModule "tools/phantom-runtime/$name") -Destination $tools
}
foreach ($name in @('LocalPlay-Ownership.ps1','LocalPlay-Pilot.ps1','LocalPlay-AtomicRename.cs','Invoke-LocalPlayPilot.ps1','Stop-LocalPlayPilot.ps1')) {
    Copy-Item -LiteralPath (Join-Path $cleanModule "tools/phantom-local-play/$name") -Destination $tools
    if ($Profile -in @('Campaign059','Campaign060','Campaign061')) {Copy-Item -LiteralPath (Join-Path $cleanModule "tools/phantom-local-play/$name") -Destination $output}
}
$classes=Join-Path $tools 'classes'; New-Item -ItemType Directory -Path $classes | Out-Null
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 --add-modules jdk.attach -cp (Join-Path $output 'libs/*') -d $classes (Join-Path $tools 'RequestShutdown.java') (Join-Path $tools 'RuntimeShutdownAgent.java') (Join-Path $tools 'RuntimeDatabaseCheck.java') (Join-Path $tools 'RuntimeCampaign058Guard.java')
if ($LASTEXITCODE -ne 0) {throw 'Operator tooling compilation failed.'}
$agentManifest=Join-Path $tools 'shutdown.mf'
[IO.File]::WriteAllText($agentManifest,"Manifest-Version: 1.0`nAgent-Class: RuntimeShutdownAgent`n`n",[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin/jar.exe') cfm (Join-Path $tools 'runtime-shutdown.jar') $agentManifest -C $classes RuntimeShutdownAgent.class -C $classes RuntimeCampaign058Guard.class
if ($LASTEXITCODE -ne 0) {throw 'Operator shutdown package failed.'}
$geo=@(Get-ChildItem -LiteralPath $GeoDataRoot -File -Filter '*.l2j')
if ($geo.Count -eq 0) {throw 'Нужны явно указанные существующие лицензированные .l2j геоданные.'}
$geoHashes=@($geo | ForEach-Object {[ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
Copy-Item -LiteralPath $geo.FullName -Destination (Join-Path $output 'game/data/geodata')
if (!(Test-Path -LiteralPath $RegistrationFile -PathType Leaf)) {throw 'Нужна существующая registration identity новой собственной schema.'}
Copy-Item -LiteralPath $RegistrationFile -Destination (Join-Path $output 'game/config/hexid.txt')
foreach ($role in @('game','login')) {
    $source=Join-Path $DatabaseConfigRoot "$role/config/Database.ini"
    $url=Read-Ini $source 'URL'
    if ($url -notmatch ('^jdbc:(mysql|mariadb)://(127\.0\.0\.1|localhost):[0-9]+/'+[regex]::Escape($DatabaseName)+'(\?.*)?$')) {throw "Private DB input не соответствует$DatabaseName"}
    Copy-Item -LiteralPath $source -Destination (Join-Path $output "$role/config/Database.ini")
}
Set-Ini (Join-Path $output 'game/config/Server.ini') 'GameserverPort' ([string]$GamePort)
Set-Ini (Join-Path $output 'game/config/Server.ini') 'LoginPort' ([string]$ControlPort)
Set-Ini (Join-Path $output 'login/config/Server.ini') 'LoginserverPort' ([string]$ClientLoginPort)
Set-Ini (Join-Path $output 'login/config/Server.ini') 'LoginPort' ([string]$ControlPort)
# Existing LocalPlay personal configuration pattern; M1 acceptance caps remain unchanged.
$patches=@{
    'game/config/Interface.ini'=@{EnableGUI='False'}
    'game/config/Custom/PhantomPlayers.ini'=@{EnablePhantomSystem='True';MaxMaterializedPhantoms='8';PhantomPopulationTarget='1280';PhantomPopulationActiveTarget='8';EnablePhantomDiagnostics='True'}
    'game/config/Rates.ini'=@{RateXp='3';RateSp='5';RatePartyXp='2';RatePartySp='2'}
    'game/config/Custom/PhantomMarket.ini'=@{EnableAutonomousPhantomMarket='True'}
    'game/config/Custom/PersonalCharacterQoL.ini'=@{EnablePersonalCharacterQoL='True';EnablePersonalCrossClassSkills='True';EnablePersonalCrystallization='True';EnablePersonalSevenSignsAccess='True';EnablePersonalPartySupport='True';EnablePersonalEffectDurations='True';AllowedAccounts=$Account}
    'game/config/Custom/PersonalPremiumQoL.ini'=@{EnablePersonalPremiumQoL='True';EnablePersonalPremiumShop='True'}
    'game/config/Custom/PersonalProgressionQoL.ini'=@{EnablePersonalQuestOverLevelRelief='True';EnableServerWideAutoNoblesse='True'}
    'game/config/Custom/LocalPlayPilot.ini'=@{EnableLocalPlayPilot='True';EnableLocalPlayPilotAutoAttach='True';LocalPlayPilotAutoAttachCharacters=$ObserverCharacter;EnableLocalPlaySyntheticHuman='False';LocalPlaySyntheticCharacterObjectId=[string]$ObserverObjectId;LocalPlaySyntheticCharacterName=$ObserverCharacter}
}
if ($Profile -in @('Campaign058','Campaign059','Campaign060','Campaign061')) {
    $patches['game/config/Custom/PhantomPlayers.ini']['MaxMaterializedPhantoms']='16'
    $patches['game/config/Custom/PhantomPlayers.ini']['PhantomPopulationTarget']='32'
    if ($Profile -ceq 'Campaign061') {$patches['game/config/Custom/PhantomPlayers.ini']['PhantomPopulationTarget']='36'}
    $patches['game/config/Rates.ini']=@{RateXp='1';RateSp='1';RatePartyXp='1';RatePartySp='1'}
    $patches['game/config/Custom/LocalPlayPilot.ini']['LocalPlayPilotAutoAttachCharacters']='TestAdmin,kpCat'
    Copy-Item -LiteralPath $OwnershipManifest -Destination (Join-Path $output $(if($Profile -ceq 'Campaign061'){'ownership061.properties'}elseif($Profile -ceq 'Campaign060'){'ownership060.properties'}elseif($Profile -ceq 'Campaign059'){'ownership059.properties'}else{'ownership058.properties'}))
}
foreach ($file in $patches.Keys) {foreach ($key in $patches[$file].Keys) {Set-Ini (Join-Path $output $file) $key $patches[$file][$key]}}
Copy-Item -LiteralPath (Join-Path $tools 'runtime.local.ini.template') -Destination (Join-Path $output 'runtime.local.ini')
Set-Ini (Join-Path $output 'runtime.local.ini') 'JavaHome' $JavaHome
Set-Ini (Join-Path $output 'runtime.local.ini') 'DatabaseName' $DatabaseName
$gameHash=(Get-FileHash (Join-Path $output 'libs/GameServer.jar')).Hash
$loginHash=(Get-FileHash (Join-Path $output 'libs/LoginServer.jar')).Hash
[ordered]@{format=1;codeSha=$commit;databaseName=$DatabaseName;databaseConfig='USER_CONFIRMED_EXISTING';gameJarSha256=$gameHash;loginJarSha256=$loginHash;observerCharacter=$ObserverCharacter} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'local-play.json') -Encoding UTF8
$configurationHashes=@(foreach ($role in @('game','login')) {Get-ChildItem -LiteralPath (Join-Path $output "$role/config") -Recurse -File | Sort-Object FullName | ForEach-Object {[ordered]@{path=$_.FullName.Substring($output.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}}})
$operatorHashes=@(Get-ChildItem -LiteralPath $tools -File | Sort-Object Name | ForEach-Object {[ordered]@{path='tools/'+$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
if ($Profile -in @('Campaign059','Campaign060','Campaign061')) {
    foreach($name059 in @('LocalPlay-Ownership.ps1','LocalPlay-Pilot.ps1','LocalPlay-AtomicRename.cs','Invoke-LocalPlayPilot.ps1','Stop-LocalPlayPilot.ps1','START.cmd','STATUS.cmd','STOP.cmd','START_HERE_RU.md')) {$operatorHashes += [ordered]@{path=$name059;sha256=(Get-FileHash -LiteralPath (Join-Path $output $name059)).Hash}}
}
[ordered]@{format=1;codeCommit=$commit;trackedInputSha256=(Get-FileHash $archive).Hash;gameJarSha256=$gameHash;loginJarSha256=$loginHash;shutdownJarSha256=(Get-FileHash (Join-Path $tools 'runtime-shutdown.jar')).Hash;registrationSha256=(Get-FileHash $RegistrationFile).Hash;initialConfiguration=$configurationHashes;operatorInputs=$operatorHashes;externalGeodata=$geoHashes;createdUtc=[DateTime]::UtcNow.ToString('o');toolchain='Java25 / Ant / Windows PowerShell5.1';m1Status='OPEN';personalAcceptance='REQUIRED'} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'BUILD_INFO.json') -Encoding UTF8
[ordered]@{format=1;updatedUtc=[DateTime]::UtcNow.ToString('o');codeCommit=$commit;gameJarSha256=$gameHash;taskResult='IN_PROGRESS';m1='OPEN';readyForM2=$false;serverGates='PENDING';personalAcceptance='REQUIRED'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'ACCEPTANCE_STATUS.json') -Encoding UTF8
if ($Profile -in @('Campaign058','Campaign059','Campaign060','Campaign061')) {
    $acceptancePath=Join-Path $output 'ACCEPTANCE_STATUS.json'
    $campaignAcceptance=Get-Content -LiteralPath $acceptancePath -Raw | ConvertFrom-Json
    $campaignAcceptance.personalAcceptance='DEFERRED_BY_OWNER'
    $campaignAcceptance | Add-Member -NotePropertyName forwardWorkM2M7 -NotePropertyValue 'AUTHORIZED'
    $campaignAcceptance | Add-Member -NotePropertyName g4 -NotePropertyValue 'DEFERRED_BY_OWNER_NOT_PROVEN'
    $campaignAcceptance | Add-Member -NotePropertyName g6 -NotePropertyValue 'BLOCKED_PLATFORM_DEFERRED'
    if ($Profile -in @('Campaign059','Campaign060','Campaign061')) {
        $campaignAcceptance | Add-Member -NotePropertyName developmentBase -NotePropertyValue 'ACCEPTED_WITH_DEFERRED_GATES'
        $campaignAcceptance | Add-Member -NotePropertyName $(if($Profile -ceq 'Campaign061'){'campaign061'}elseif($Profile -ceq 'Campaign060'){'campaign060'}else{'campaign059'}) -NotePropertyValue 'IN_PROGRESS'
    }
    $campaignAcceptance | ConvertTo-Json | Set-Content -LiteralPath $acceptancePath -Encoding UTF8
    $buildPath=Join-Path $output 'BUILD_INFO.json'
    $campaignBuild=Get-Content -LiteralPath $buildPath -Raw | ConvertFrom-Json
    $campaignBuild.personalAcceptance='DEFERRED_BY_OWNER'
    $campaignBuild | Add-Member -NotePropertyName profile -NotePropertyValue $Profile
    $campaignBuild | Add-Member -NotePropertyName campaignRole -NotePropertyValue $campaignOwner.role
    $campaignBuild | Add-Member -NotePropertyName ownershipSha256 -NotePropertyValue (Get-FileHash -LiteralPath $OwnershipManifest).Hash
    if ($Profile -in @('Campaign059','Campaign060','Campaign061')) {
        foreach ($pair059 in @(@('layout',$campaignOwner.layout),@('clientLoginPort',$ClientLoginPort),@('gamePort',$GamePort),@('controlPort',$ControlPort))) {$campaignBuild | Add-Member -NotePropertyName $pair059[0] -NotePropertyValue $pair059[1]}
        $runtimeManifest059=Get-Content -LiteralPath (Join-Path $output 'local-play.json') -Raw | ConvertFrom-Json
        foreach ($pair059 in @(@('layout',$campaignOwner.layout),@('clientLoginPort',$ClientLoginPort),@('gamePort',$GamePort),@('controlPort',$ControlPort))) {$runtimeManifest059 | Add-Member -NotePropertyName $pair059[0] -NotePropertyValue $pair059[1]}
        $runtimeManifest059 | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'local-play.json') -Encoding UTF8
    }
    $campaignBuild | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $buildPath -Encoding UTF8
}
# Use the existing private ACL routine; no permissive observer mailbox or shared mutable links.
. (Join-Path $tools 'LocalPlay-Pilot.ps1')
foreach ($directory in @('playtest-pilot','playtest-pilot/inbox','playtest-pilot/processing','playtest-pilot/results','playtest-pilot/journal')) {Protect-PilotDirectory (Join-Path $output $directory)}
foreach ($file in @('runtime.local.ini','game/config/Database.ini','login/config/Database.ini','game/config/hexid.txt','game/config/Custom/LocalPlayPilot.ini')) {
    $path=Join-Path $output $file
    $acl=New-Object Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')) {$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier($sid)), 'FullControl','Allow')))}
    Set-Acl -LiteralPath $path -AclObject $acl
}
Write-Output "TRACKED_PACKAGE_CREATED source=$commit root=$output"
