param([Parameter(Mandatory=$true)][ValidateSet('prepare059','prepare-raid059','upgrade-career061','record','recall','recall-legacy')][string]$Phase,[Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]{1,30}$')][string]$Episode,[Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]{1,60}$')][string]$Label,[switch]$ProductionGrounding,[switch]$Campaign061,[switch]$CareerContext061)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ((Get-Location).Path -ine (Join-Path $hf059 '.phantom-local/contract058a/runtime/game')) { throw 'Exact stopped058a runtime cwd required' }
if ($Phase -ceq 'upgrade-career061' -and (!$Campaign061 -or !$ProductionGrounding)) {throw 'Known-generation upgrade requires exact061 and production grounding'}
if ($CareerContext061 -and !$Campaign061) {throw 'Focused context helper requires exact061'}
if ($Phase -ceq 'upgrade-career061' -and !$CareerContext061) {throw 'Explicit compiled focused061 context helper required'}
if ($Campaign061 -and $Phase -cnotin @('recall-legacy','upgrade-career061')) {throw 'TASK061 permits preserved original memory recall or exact known-generation upgrade only'}
if ($Campaign061 -and (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'task061|World-Career-061-candidate|Living-060-candidate|M3-M7-059-candidate')) {throw 'Sequential061 or protected runtime active'}
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'contract058[at]|task059|M[12].*05[78]-candidate') { throw 'Another058/059/protected JVM active' }
$build059=Join-Path $hf059 '.phantom-local/contract058a/task059/build'
$jar059=Join-Path $build059 'dist/libs/GameServer.jar'
$log059=Join-Path $hf059 ('.phantom-local/contract058a/task059/'+$Label+'.log')
if ($Campaign061) {$build059=Join-Path $hf059 '.phantom-local/contract058a/task061/build'; $jar059=Join-Path $build059 'dist/libs/GameServer.jar'; $log059=Join-Path $hf059 ('.phantom-local/contract058a/task061/'+$Label+'.log')}
$focused061=''
if ($CareerContext061) {
 $focused061=Join-Path $hf059 '.phantom-local/contract058a/task061/memory-upgrade-bin'
 if(!(Test-Path -LiteralPath (Join-Path $focused061 'source.sha256')) -or [IO.File]::ReadAllText((Join-Path $focused061 'source.sha256')).Trim() -cne (Get-FileHash -LiteralPath (Join-Path $hf059 'test/java/org/l2jmobius/tests/phantoms/PhantomSocialMemory058.java')).Hash){throw 'Focused helper source provenance differs'}
 $focused061+=';'
}
if (Test-Path -LiteralPath $log059) { throw 'Evidence exists; no overwrite' }
$args059=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$hf059","-Dphantom.contract058.semantic.jar=$jar059",'-Dphantom.campaign058.persistent=true','-Dphantom.test.continuation059=true',"-Dphantom.test.config=$hf059/.phantom-local/contract058a/runtime/game/config/Database.ini")
if ($ProductionGrounding) { $args059+='-Dphantom.test.productionGrounding059=true' }
if ($Campaign061) { $args059+='-Dphantom.test.continuation061=true' }
Write-Output ('MEMORY059 '+$Phase+'; episode='+$Episode+'; GameSHA='+(Get-FileHash -LiteralPath $jar059).Hash)
$nativePreference059=$ErrorActionPreference
$locks061=@()
try {
 if($Phase -ceq 'upgrade-career061'){
  foreach($root061 in @((Join-Path $hf059 '.phantom-local/contract058a/runtime'),'D:\PhantomWorld\World-Career-061-candidate')){
   [IO.Directory]::CreateDirectory((Join-Path $root061 'local-play')) | Out-Null
   $locks061 += [IO.File]::Open((Join-Path $root061 'local-play/operator.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
  }
 }
 $ErrorActionPreference='Continue'
 & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp "$jar059;$focused061$build059/phantom-test/bin;$build059/phantom-test/resources;$hf059/dist/libs/*" org.l2jmobius.tests.phantoms.PhantomSocialMemory058 $Phase $Episode *> $log059
 $exit059=$LASTEXITCODE
} finally {$ErrorActionPreference=$nativePreference059;foreach($lock061 in $locks061){$lock061.Dispose()}}
Get-Content -LiteralPath $log059 -Tail 22
exit $exit059
