param([Parameter(Mandatory=$true)][string]$Mode,[Parameter(Mandatory=$true)][long]$Seed,[Parameter(Mandatory=$true)][string]$Label,[switch]$Commands,[switch]$Authority,[switch]$BusyHold)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ($Mode -cnotmatch '^[a-z0-9-]+$' -or $Label -cnotmatch '^[a-z0-9-]+$' -or $Seed -le 0) { throw 'Invalid exact test arguments' }
if ((Get-Location).Path -ine (Join-Path $hf059 '.phantom-local/contract058t/runtime/game')) { throw 'Exact058t native cwd required' }
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'contract058[at]|task059|M[12].*05[78]-candidate') { throw 'Another058/059/protected JVM active; sequential only' }
$build059=Join-Path $hf059 '.phantom-local/contract058a/task059/build'
$jar059=Join-Path $build059 'dist/libs/GameServer.jar'
$report059=Join-Path $hf059 ('.phantom-local/contract058t/reports/task059/'+$Label)
$log059=Join-Path $hf059 ('.phantom-local/contract058a/task059/'+$Label+'.log')
if ((Test-Path -LiteralPath $report059) -or (Test-Path -LiteralPath $log059)) { throw 'Evidence exists; no overwrite' }
$classpath059="$jar059;$build059\phantom-test\bin;$build059\phantom-test\resources;$hf059\dist\libs\*"
$args059=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$hf059","-Dphantom.contract058.semantic.jar=$jar059","-Dphantom.test.reports=$report059","-Dphantom.test.config=$hf059\.phantom-local\contract058t\test\Database.test.ini","-Dphantom.contract058.manifest=$hf059\.phantom-local\contract058t\test\owned.properties")
if ($Commands -or $Authority) { $args059+='-Dphantom.test.commands059=true' }
if ($Authority) { $args059+='-Dphantom.test.commandAuthority059=true' }
if ($BusyHold) { $args059+='-Dphantom.test.busyHold059=true' }
Write-Output ('TEST059 '+$Label+'; mode='+$Mode+'; seed='+$Seed+'; GameSHA='+(Get-FileHash -LiteralPath $jar059).Hash)
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed *> $log059
$exit059=$LASTEXITCODE
Get-Content -LiteralPath $log059 -Tail 22
exit $exit059
