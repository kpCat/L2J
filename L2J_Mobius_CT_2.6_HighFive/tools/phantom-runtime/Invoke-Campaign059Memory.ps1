param([Parameter(Mandatory=$true)][ValidateSet('prepare059','prepare-raid059','record','recall','recall-legacy')][string]$Phase,[Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]{1,30}$')][string]$Episode,[Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label,[switch]$ProductionGrounding,[switch]$Campaign061)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ((Get-Location).Path -ine (Join-Path $hf059 '.phantom-local/contract058a/runtime/game')) { throw 'Exact stopped058a runtime cwd required' }
if ($Campaign061 -and $Phase -cne 'recall-legacy') {throw 'TASK061 permits only preserved original memory recall, without preparation or replay'}
if ($Campaign061 -and (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'task061|World-Career-061-candidate|Living-060-candidate|M3-M7-059-candidate')) {throw 'Sequential061 or protected runtime active'}
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'contract058[at]|task059|M[12].*05[78]-candidate') { throw 'Another058/059/protected JVM active' }
$build059=Join-Path $hf059 '.phantom-local/contract058a/task059/build'
$jar059=Join-Path $build059 'dist/libs/GameServer.jar'
$log059=Join-Path $hf059 ('.phantom-local/contract058a/task059/'+$Label+'.log')
if ($Campaign061) {$build059=Join-Path $hf059 '.phantom-local/contract058a/task061/build'; $jar059=Join-Path $build059 'dist/libs/GameServer.jar'; $log059=Join-Path $hf059 ('.phantom-local/contract058a/task061/'+$Label+'.log')}
if (Test-Path -LiteralPath $log059) { throw 'Evidence exists; no overwrite' }
$args059=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$hf059","-Dphantom.contract058.semantic.jar=$jar059",'-Dphantom.campaign058.persistent=true','-Dphantom.test.continuation059=true',"-Dphantom.test.config=$hf059/.phantom-local/contract058a/runtime/game/config/Database.ini")
if ($ProductionGrounding) { $args059+='-Dphantom.test.productionGrounding059=true' }
if ($Campaign061) { $args059+='-Dphantom.test.continuation061=true' }
Write-Output ('MEMORY059 '+$Phase+'; episode='+$Episode+'; GameSHA='+(Get-FileHash -LiteralPath $jar059).Hash)
$nativePreference059=$ErrorActionPreference
try {
 $ErrorActionPreference='Continue'
 & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp "$jar059;$build059/phantom-test/bin;$build059/phantom-test/resources;$hf059/dist/libs/*" org.l2jmobius.tests.phantoms.PhantomSocialMemory058 $Phase $Episode *> $log059
 $exit059=$LASTEXITCODE
} finally {$ErrorActionPreference=$nativePreference059}
Get-Content -LiteralPath $log059 -Tail 22
exit $exit059
