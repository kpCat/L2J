[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputRoot,
    [Parameter(Mandatory)][string]$StageRoot,
    [string]$GeoDataRoot = '',
    [ValidatePattern('^[a-z0-9_-]{1,45}$')][string]$Account = 'localplayer',
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$output = [IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
$stage = [IO.Path]::GetFullPath($StageRoot).TrimEnd('\')
function Assert-Plain078([string]$Path) {
    $current = [IO.Path]::GetPathRoot($Path)
    foreach ($part in $Path.Substring($current.Length).Split('\')) {
        if (!$part) { continue }; $current = Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -Force -LiteralPath $current).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'REPARSE_PATH' }
    }
}
foreach ($path in @($output,$stage)) { Assert-Plain078 $path }
if (Test-Path -LiteralPath $output) { throw 'OUTPUT_EXISTS: existing owner data must remain unchanged.' }
if (Test-Path -LiteralPath $stage) { throw 'STAGE_EXISTS: no reuse or cleanup is allowed.' }
if ($output.StartsWith($stage+'\',[StringComparison]::OrdinalIgnoreCase) -or $stage.StartsWith($output+'\',[StringComparison]::OrdinalIgnoreCase) -or $output -ieq $stage) { throw 'OUTPUT_STAGE_OVERLAP' }
if ($ValidateOnly) { Write-Output 'PATH_PREFLIGHT_PASS; no writes, DB or process actions'; return }
$script:moduleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$repository = Split-Path $script:moduleRoot -Parent
$moduleName = Split-Path $script:moduleRoot -Leaf
if ($output.StartsWith($repository+'\',[StringComparison]::OrdinalIgnoreCase) -or !$stage.StartsWith($repository+'\.phantom-local\',[StringComparison]::OrdinalIgnoreCase)) { throw 'OWN078 requires external delivery and new repo-local staging.' }
if ($output -ine (Join-Path ([IO.Path]::GetPathRoot($repository)) 'PhantomWorld/Playable-078-owner')) { throw 'OWN078 delivery path differs from task authority.' }
$DatabaseName = 'l2jmobiush5_phantom_release_078'
. (Join-Path $PSScriptRoot '../phantom-local-play/Build-FreshLocalPlay.ps1') -LibraryOnly -DatabaseName $DatabaseName -Account $Account
. (Join-Path $PSScriptRoot '../phantom-local-play/LocalPlay-Ownership.ps1')
if ((& git symbolic-ref --short HEAD).Trim() -cne 'feature/phantom-world') { throw 'Wrong branch.' }
$commit = (& git rev-parse HEAD).Trim()
if ($commit -notmatch '^[a-f0-9]{40}$') { throw 'Missing source commit.' }
$java = (Get-Command java.exe -ErrorAction Stop).Source
$javaHome = Split-Path (Split-Path $java -Parent) -Parent
if ([IO.File]::ReadAllText((Join-Path $javaHome 'release')) -notmatch '(?m)^JAVA_VERSION="25(?:\.|\")') { throw 'JDK25 required.' }
if ((Get-LocalPlayPortOwners @(2106,7777,9014)).Count -ne 0) { throw 'PORT_OCCUPIED: no process was stopped.' }
if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'").Count) { throw 'Foreign JVM present; bounded078 requires clean startup inventory.' }
Assert-Plain078 ([IO.Path]::GetFullPath($GeoDataRoot))
$geo = @(Get-ChildItem -LiteralPath $GeoDataRoot -File -Filter '*.l2j' | Sort-Object Name)
if ($geo.Count -ne 203 -or @($geo | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'GEODATA203_REQUIRED' }
$sourceDist = Join-Path $script:moduleRoot 'dist'
$protected = @(Get-ChildItem -LiteralPath (Join-Path $sourceDist 'game/config'),(Join-Path $sourceDist 'login/config') -Recurse -File | Select-Object -ExpandProperty FullName)
$before = Get-TrackedSourceHashes $protected
$loginConfig = Join-Path $sourceDist 'login/config/Database.ini'
$gameConfig = Join-Path $sourceDist 'game/config/Database.ini'
$loginUrl = Get-IniValue $loginConfig 'URL'; $gameUrl = Get-IniValue $gameConfig 'URL'
$target = Get-JdbcTarget $loginUrl; $gameTarget = Get-JdbcTarget $gameUrl
if ($target.Host -cne '127.0.0.1' -or $target.Port -ne 3308 -or $gameTarget.Host -cne $target.Host -or $gameTarget.Port -ne 3308 -or $target.Database -ceq $DatabaseName -or $gameTarget.Database -ceq $DatabaseName) { throw 'SOURCE_DB_ENDPOINT_MISMATCH' }
$script:databaseLogin = Get-IniValue $loginConfig 'Login'; $script:databasePassword = Get-IniValue $loginConfig 'Password'
if ($script:databaseLogin -cne (Get-IniValue $gameConfig 'Login') -or $script:databasePassword -cne (Get-IniValue $gameConfig 'Password')) { throw 'SOURCE_DB_CREDENTIAL_MISMATCH' }
$script:serverUrl = 'jdbc:mysql://127.0.0.1:3308/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8'
$script:guardSource = Join-Path $script:moduleRoot 'tools/phantom-local-play/FreshLocalPlayDatabaseGuard.java'
$connectors = @(Get-ChildItem -LiteralPath (Join-Path $sourceDist 'libs') -Filter 'mysql-connector-j-*.jar' | Where-Object Name -notmatch 'sources')
if ($connectors.Count -ne 1) { throw 'One JDBC connector required.' }
$script:connectorJar = $connectors[0].FullName
Write-Output (Invoke-DatabaseGuard 'assert-absent')
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $script:moduleRoot 'tools/OwnerConfig.ps1') -Action Verify
if ($LASTEXITCODE) { throw 'Unsaved owner config; source not changed.' }
function Protect-078([string]$Path) {
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')) {
        $identity = [Security.Principal.SecurityIdentifier]::new($sid)
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
}
New-Item -ItemType Directory -Path $stage | Out-Null; Protect-078 $stage
$archive = Join-Path $stage 'tracked-input.zip'
& git archive --format=zip "--output=$archive" $commit -- $moduleName
if ($LASTEXITCODE) { throw 'Tracked archive failed.' }
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $stage 'source')
$clean = Join-Path $stage "source/$moduleName"
$buildRoot = Join-Path $stage 'compiled'
Push-Location $clean
try { & ant -q "-Dbuild=$buildRoot" jar *> (Join-Path $stage 'production-build.log'); if ($LASTEXITCODE) { throw 'Production build failed; inspect private production-build.log.' } }
finally { Pop-Location }
New-Item -ItemType Directory -Path $output | Out-Null; Protect-078 $output
[IO.File]::WriteAllText((Join-Path $output 'DB_CONFIG_REQUIRED.txt'), 'Provisioning078 incomplete. START forbidden.')
Copy-Item -Path (Join-Path $clean 'dist/*') -Destination $output -Recurse
Copy-Item -LiteralPath (Join-Path $buildRoot 'dist/db_installer/DatabaseInstaller.jar') -Destination (Join-Path $output 'db_installer/DatabaseInstaller.jar') -Force
$geoHashes = @(foreach ($file in $geo) {
    $hash = (Get-FileHash -LiteralPath $file.FullName).Hash
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $output 'game/data/geodata')
    if ((Get-FileHash -LiteralPath (Join-Path $output "game/data/geodata/$($file.Name)")).Hash -cne $hash) { throw 'GEODATA_COPY_MISMATCH' }
    [ordered]@{name=$file.Name;sha256=$hash}
})
# Operational enablement follows shipped small-server guidance (10..50 profiles / 2..8 ACTIVE).
# Owner keys always win by conflict, never by rewriting them after packaging.
$patches = @{
    'game/config/Interface.ini' = @{EnableGUI='False'}
    'login/config/Interface.ini' = @{EnableGUI='False'}
    'game/config/Custom/PhantomPlayers.ini' = @{EnablePhantomSystem='True';PhantomPopulationTarget='32';PhantomPopulationActiveTarget='8';EnablePhantomHumanizedConversation='True';EnablePhantomCustomConversationPack='True'}
    'game/config/Custom/PersonalCharacterQoL.ini' = @{EnablePersonalCharacterQoL='True';EnablePersonalCrossClassSkills='True';EnablePersonalCrystallization='True';EnablePersonalSevenSignsAccess='True';EnablePersonalPartySupport='True';EnablePersonalEffectDurations='True';AllowedAccounts=$Account}
    'game/config/Custom/PersonalPremiumQoL.ini' = @{EnablePersonalPremiumQoL='True';EnablePersonalPremiumShop='True'}
    'game/config/Custom/PersonalProgressionQoL.ini' = @{EnablePersonalQuestOverLevelRelief='True'}
}
$ownerProfile = Get-Content -LiteralPath (Join-Path $output 'game/config/OwnerConfig.json') -Raw | ConvertFrom-Json
foreach ($relative in $patches.Keys) {
    foreach ($key in $patches[$relative].Keys) {
        $conflict = @($ownerProfile.overrides | Where-Object { ('game/config/'+$_.file) -ceq $relative -and $_.key -ceq $key -and [string]$_.value -cne $patches[$relative][$key] })
        if ($conflict.Count) { throw "OWNER_OVERRIDE_CONFLICT: $relative/$key" }
        Set-IniValue (Join-Path $output $relative) $key $patches[$relative][$key]
    }
}
foreach ($action in @('Apply','Verify')) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $script:moduleRoot 'tools/OwnerConfig.ps1') -Action $action -GameRoot (Join-Path $output 'game')
    if ($LASTEXITCODE) { throw 'Staged owner config validation failed.' }
}
$tools = Join-Path $output 'tools'; New-Item -ItemType Directory -Path $tools -Force | Out-Null
foreach ($name in @('Runtime.ps1','RequestShutdown.java','RuntimeShutdownAgent.java','RuntimeDatabaseCheck.java','RuntimeCampaign058Guard.java','Owner078Probe.java')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $tools
}
Copy-Item -LiteralPath (Join-Path $script:moduleRoot 'tools/phantom-local-play/LocalPlay-Ownership.ps1') -Destination $tools
$classes = Join-Path $tools 'classes'; New-Item -ItemType Directory -Path $classes | Out-Null
& (Join-Path $javaHome 'bin/javac.exe') --add-modules jdk.attach -encoding UTF-8 -cp (Join-Path $output 'libs/*') -d $classes (Join-Path $tools 'RequestShutdown.java') (Join-Path $tools 'RuntimeShutdownAgent.java') (Join-Path $tools 'RuntimeDatabaseCheck.java') (Join-Path $tools 'RuntimeCampaign058Guard.java') (Join-Path $tools 'Owner078Probe.java')
if ($LASTEXITCODE) { throw 'Operator helper compilation failed.' }
foreach ($agent in @(@('runtime-shutdown','RuntimeShutdownAgent'),@('owner078-probe','Owner078Probe'))) {
    $mf = Join-Path $stage ($agent[0]+'.mf')
    [IO.File]::WriteAllText($mf, "Manifest-Version: 1.0`r`nAgent-Class: $($agent[1])`r`n`r`n", [Text.Encoding]::ASCII)
    & (Join-Path $javaHome 'bin/jar.exe') cfm (Join-Path $tools ($agent[0]+'.jar')) $mf -C $classes .
    if ($LASTEXITCODE) { throw 'Operator agent packaging failed.' }
}
[IO.File]::WriteAllText((Join-Path $output 'runtime.local.ini'), "JavaHome = $javaHome`r`nDatabaseName = $DatabaseName`r`nLoginTimeoutSeconds = 60`r`nGameTimeoutSeconds = 300`r`nStopTimeoutSeconds = 120`r`n", [Text.UTF8Encoding]::new($true))
foreach ($action in @('START','STATUS','STOP')) {
    $verb = $action.Substring(0,1)+$action.Substring(1).ToLowerInvariant()
    $cmd = "@echo off`r`nchcp 65001 >nul`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0tools\Runtime.ps1`" -Action $verb -RuntimeRoot `"%~dp0.`"`r`nset `"result=%errorlevel%`"`r`necho.`r`necho Exit code: %result%`r`npause`r`nexit /b %result%`r`n"
    [IO.File]::WriteAllText((Join-Path $output ($action+'_078.cmd')), $cmd, [Text.Encoding]::ASCII)
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README_OWNER078_RU.md') -Destination (Join-Path $output 'README_OWNER_RU.md')
Write-Output (Invoke-DatabaseGuard 'create-fresh')
$installer = Join-Path $output 'db_installer'
Set-IniValue (Join-Path $installer 'config/Interface.ini') 'EnableGUI' 'False'
$result = Invoke-JavaProcess $installer @('-jar','DatabaseInstaller.jar') @('2','1',$target.Host,'3308',$script:databaseLogin,$script:databasePassword,$DatabaseName,'3')
if ($result.ExitCode -or $result.StdOut -match '(?m)^\[ERROR\]' -or $result.StdErr -match '(?i)exception|error' -or $result.StdOut -notmatch 'Installation completed successfully') { throw 'Canonical installer failed; no retry/reinstall permitted.' }
Write-Output 'CANONICAL_INSTALLER_PASS'
Write-Output (Invoke-DatabaseGuard 'verify-schema-empty' (Join-Path $installer 'sql'))
foreach ($role in @('login','game')) {
    $config = Join-Path $output "$role/config/Database.ini"
    Set-IniValue $config 'URL' "jdbc:mysql://127.0.0.1:3308/$DatabaseName`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
    Set-IniValue $config 'Login' $script:databaseLogin; Set-IniValue $config 'Password' $script:databasePassword
}
$generated = Join-Path $output 'login/hexid.txt'; $hexid = Join-Path $output 'game/config/hexid.txt'
if (Test-Path -LiteralPath $generated) { throw 'Unexpected inherited login registration.' }
if (Test-Path -LiteralPath $hexid) { Remove-Item -LiteralPath $hexid }
$register = Invoke-JavaProcess (Join-Path $output 'login') @('-cp','../libs/*','org.l2jmobius.tools.GameServerRegister') @('3','2','y','2','1','5')
if ($register.ExitCode -or $register.StdOut -notmatch 'Game server with ID: 1 .* successfully registered' -or !(Test-Path -LiteralPath $generated)) { throw 'Canonical registration failed.' }
Move-Item -LiteralPath $generated -Destination $hexid
Write-Output (Invoke-DatabaseGuard 'verify-registered' $hexid)
$gameHash = (Get-FileHash -LiteralPath (Join-Path $output 'libs/GameServer.jar')).Hash
$loginHash = (Get-FileHash -LiteralPath (Join-Path $output 'libs/LoginServer.jar')).Hash
[ordered]@{format=1;owner='PHANTOM-OWNER-PLAYABLE-078';runtimeRoot=$output;databaseName=$DatabaseName;databaseConfig='USER_CONFIRMED_EXISTING';codeSha=$commit;gameJarSha256=$gameHash;loginJarSha256=$loginHash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'local-play.json') -Encoding UTF8
$configuration = @(foreach ($role in @('game','login')) { Get-ChildItem -LiteralPath (Join-Path $output "$role/config") -Recurse -File | Sort-Object FullName | ForEach-Object { [ordered]@{path=$_.FullName.Substring($output.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} } })
$operators = @(Get-ChildItem -LiteralPath $tools -Recurse -File | Sort-Object FullName | ForEach-Object { [ordered]@{path=$_.FullName.Substring($output.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
[ordered]@{format=1;owner='PHANTOM-OWNER-PLAYABLE-078';runtimeRoot=$output;codeCommit=$commit;trackedInputSha256=(Get-FileHash -LiteralPath $archive).Hash;gameJarSha256=$gameHash;loginJarSha256=$loginHash;shutdownJarSha256=(Get-FileHash -LiteralPath (Join-Path $tools 'runtime-shutdown.jar')).Hash;registrationSha256=(Get-FileHash -LiteralPath $hexid).Hash;initialConfiguration=$configuration;operatorInputs=$operators;externalGeodata=$geoHashes;createdUtc=[DateTime]::UtcNow.ToString('o');ownerClient='OWNER_PLAY_PENDING';populationTarget=32;activeTarget=8;materializedCap=32} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'BUILD_INFO.json') -Encoding UTF8
Assert-SourceHashesUnchanged $before
Remove-Item -LiteralPath (Join-Path $output 'DB_CONFIG_REQUIRED.txt')
$script:databasePassword = $null
Write-Output "OWNER078_ASSEMBLED source=$commit root=$output; no server started"
