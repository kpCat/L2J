param([Parameter(Mandatory=$true)][string]$Mode,[Parameter(Mandatory=$true)][long]$Seed,[Parameter(Mandatory=$true)][string]$Label,[switch]$Commands,[switch]$Authority,[switch]$BusyHold,[switch]$SupportRequests,[switch]$SupportKinds,[switch]$CardinalBuff,[switch]$SocialContexts,[switch]$PvpFlow,[switch]$PvpFlowOnly,[switch]$PvpGuardsOnly,[switch]$RaidFlow,[switch]$RaidStaging,[switch]$RaidLevelGuard,[switch]$RaidMinionGuard,[switch]$RaidTriggerGuard,[switch]$KamalokaFlow,[switch]$RiftFlow,[switch]$LearningJourney,[switch]$CleanCandidate,[switch]$Campaign060,[switch]$AutonomousLearning,[switch]$AutonomousLearningGuards,[switch]$Living,[switch]$LivingMage,[switch]$LivingDwarf,[int]$LivingClass=0,[switch]$PaidAutoGetGuards,[switch]$LivingRelocate,[switch]$LivingSupply,[switch]$SupplyGuards,[switch]$LivingOfflineStore,[switch]$SupplyReturnGuards,[switch]$SupplyManufactureGuards,[switch]$LivingManufacture,[switch]$LivingGatekeeper,[switch]$SupplyGatekeeperGuards)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ($Mode -cnotmatch '^[a-z0-9-]+$' -or $Label -cnotmatch '^[a-z0-9-]+$' -or $Seed -le 0) { throw 'Invalid exact test arguments' }
if ((Get-Location).Path -ine (Join-Path $hf059 '.phantom-local/contract058t/runtime/game')) { throw 'Exact058t native cwd required' }
if ($AutonomousLearning -and (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833)) {throw 'Autonomous learning requires exact060 continuation of native058 mode/seed'}
if ($AutonomousLearningGuards -and !$AutonomousLearning) {throw 'Learning guards require the exact060 autonomous learning input'}
if ($Living -and (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $AutonomousLearning)) {throw 'Living composition requires exact060 native058 mode/seed and a separate scene'}
if (($LivingMage -or $LivingDwarf -or $LivingClass -ne 0) -and !$Living) {throw 'Living mage requires the full living composition'}
if ($Campaign060 -and (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'Living-060-candidate|M3-M7-059-candidate|task060')) {throw 'Own060 or protected059 JVM active; sequential only'}
if ($PvpFlowOnly -and $CleanCandidate -and ($Mode -cne 'pvp-current-native058' -or $Seed -ne 58005829)) { throw 'Current PvP flow test classes require exact native mode/seed' }
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'contract058[at]|task059|M[12].*05[78]-candidate') { throw 'Another058/059/protected JVM active; sequential only' }
$build059=Join-Path $hf059 $(if($CleanCandidate){'.phantom-local/contract058a/build/release059/compiled'}else{'.phantom-local/contract058a/task059/build'})
$jar059=Join-Path $build059 'dist/libs/GameServer.jar'
$report059=Join-Path $hf059 ('.phantom-local/contract058t/reports/task059/'+$Label)
$log059=Join-Path $hf059 ('.phantom-local/contract058a/task059/'+$Label+'.log')
if ($Campaign060) {
 $build059=Join-Path $hf059 $(if($CleanCandidate){'.phantom-local/contract058a/build/release060/compiled'}else{'.phantom-local/contract058a/task060/build'})
 $jar059=Join-Path $build059 'dist/libs/GameServer.jar'
 $report059=Join-Path $hf059 ('.phantom-local/contract058t/reports/task060/'+$Label)
 $log059=Join-Path $hf059 ('.phantom-local/contract058a/task060/'+$Label+'.log')
}
if ((Test-Path -LiteralPath $report059) -or (Test-Path -LiteralPath $log059)) { throw 'Evidence exists; no overwrite' }
$testBuild059=if($CleanCandidate -and $PvpFlowOnly -and !$Campaign060){Join-Path $hf059 '.phantom-local/contract058a/task059/build'}else{$build059}
$classpath059="$jar059;$testBuild059\phantom-test\bin;$testBuild059\phantom-test\resources;$hf059\dist\libs\*"
$args059=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$hf059","-Dphantom.contract058.semantic.jar=$jar059","-Dphantom.test.reports=$report059","-Dphantom.test.config=$hf059\.phantom-local\contract058t\test\Database.test.ini","-Dphantom.contract058.manifest=$hf059\.phantom-local\contract058t\test\owned.properties")
$args059+='-Dphantom.test.freshIdentities059=true'
if ($PaidAutoGetGuards) {
 if (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $AutonomousLearning) {throw 'Paid auto-get contract requires exact separate060 mode/seed'}
 $args059+='-Dphantom.test.paidAutoGetGuards060=true'
}
if ($AutonomousLearning) {$args059+='-Dphantom.test.autonomousLearning060=true'}
if ($AutonomousLearningGuards) {$args059+='-Dphantom.test.autonomousLearningGuards060=true'}
if ($LivingRelocate) {
 if (!$Living -or $LivingMage -or $LivingDwarf -or $LivingClass -ne 0) {throw 'Relocation requires separate exact Fighter living input'}
 $args059+='-Dphantom.test.livingRelocate060=true'
}
if ($LivingSupply) {
 if (!$Living -or $LivingRelocate) {throw 'Supply requires a separate declared living input'}
 $args059+='-Dphantom.test.livingSupply060=true'
}
if ($LivingOfflineStore) { if (!$LivingSupply -or !$Campaign060 -or $LivingClass -notin @(0,53) -or $LivingMage) {throw 'Offline store requires declared Fighter or Dwarf supply scene'}; $args059+='-Dphantom.test.livingOfflineStore060=true' }
if ($SupplyReturnGuards) { if (!$SupplyGuards) {throw 'Native supply return control requires exact separate SupplyGuards input'}; $args059+='-Dphantom.test.supplyReturnGuards060=true' }
if ($SupplyGatekeeperGuards) { if (!$SupplyGuards -or $SupplyManufactureGuards -or $SupplyReturnGuards) {throw 'Native GK corridor control requires separate060 SupplyGuards'}; $args059+='-Dphantom.test.supplyGatekeeperGuards060=true' }
if ($SupplyManufactureGuards) { if (!$SupplyGuards -or $SupplyReturnGuards) {throw 'Native manufacture source control requires exact separate SupplyGuards input'}; $args059+='-Dphantom.test.supplyManufactureGuards060=true' }
if ($LivingGatekeeper) { if (!$LivingSupply -or !$Campaign060 -or $LivingOfflineStore -or $LivingManufacture -or $LivingMage -or (!$LivingDwarf -and $LivingClass -ne 53)) {throw 'Gatekeeper requires separate prepared Dwarf supply scene'}; $args059+='-Dphantom.test.livingGatekeeper060=true' }
if ($LivingManufacture) { if (!$LivingSupply -or !$Campaign060 -or $LivingOfflineStore -or $LivingMage -or (!$LivingDwarf -and $LivingClass -ne 53)) {throw 'Manufacture requires separate prepared Dwarf supply scene'}; $args059+='-Dphantom.test.livingManufacture060=true' }
if ($SupplyGuards) {
 if (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $AutonomousLearning) {throw 'Supply contracts require exact separate060 mode/seed'}
 $args059+='-Dphantom.test.supplyGuards060=true'
}
if ($Living) {$args059+='-Dphantom.test.living060=true'}
if ($LivingMage) {$args059+='-Dphantom.test.livingMage060=true'}
if ($LivingDwarf -and $LivingMage) {throw 'Separate declared class scenes required'}
if ($LivingDwarf) {$args059+='-Dphantom.test.livingDwarf060=true'}
if ($LivingClass -ne 0) {
 if ($LivingMage -or $LivingDwarf -or $LivingClass -notin @(10,25,44,53)) {throw 'Separate supported declared Living class required'}
 $args059+=('-Dphantom.test.livingClass060='+$LivingClass)
}
if ($Commands -or $Authority) { $args059+='-Dphantom.test.commands059=true' }
if ($Authority) { $args059+='-Dphantom.test.commandAuthority059=true' }
if ($BusyHold) { $args059+='-Dphantom.test.busyHold059=true' }
if ($SupportRequests) { $args059+='-Dphantom.test.supportRequests059=true' }
if ($SupportKinds) { if ($Mode -cne 'companion-real-support058' -or $Seed -ne 58005806) { throw 'Exact support-kind native058 mode/seed required' }; $args059+='-Dphantom.test.supportKinds059=true' }
if ($CardinalBuff) { if ($Mode -cne 'companion-real-support058' -or $Seed -ne 58005806) { throw 'Exact Cardinal buff058 mode/seed required' }; $args059+='-Dphantom.test.cardinalBuff059=true' }
if ($SocialContexts) { $args059+='-Dphantom.test.socialContexts059=true' }
if ($PvpFlow) { $args059+='-Dphantom.test.pvpFlow059=true' }
if ($PvpFlowOnly) { $args059+='-Dphantom.test.pvpFlow059=true'; $args059+='-Dphantom.test.pvpFlowOnly059=true' }
if ($PvpGuardsOnly) { $args059+='-Dphantom.test.pvpGuardsOnly059=true' }
if ($RaidFlow -or $RaidStaging -or $RaidLevelGuard -or $RaidMinionGuard -or $RaidTriggerGuard) { if ($Mode -cne 'raid-tactics-native058' -or $Seed -ne 58005830) { throw 'Exact native raid058 mode/seed required' }; $args059+='-Dphantom.test.raidFlow059=true' }
if ($RaidStaging) { $args059+='-Dphantom.test.raidStagingOnly059=true' }
if ($RaidLevelGuard) { $args059+='-Dphantom.test.raidLevelGuard059=true' }
if ($RaidMinionGuard) { $args059+='-Dphantom.test.raidMinionGuard059=true' }
if ($RaidTriggerGuard) { $args059+='-Dphantom.test.raidTriggerGuard059=true' }
if ($KamalokaFlow) { if($Mode -cne 'instances-native058' -or $Seed -ne 58005831){throw 'Exact Kamaloka058 mode/seed required'}; $args059+='-Dphantom.test.kamalokaFlow059=true' }
if ($RiftFlow) { if($Mode -cne 'rift-native058' -or $Seed -ne 58005832){throw 'Exact Rift058 mode/seed required'}; $args059+='-Dphantom.test.riftFlow059=true' }
if ($LearningJourney) { if($Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833){throw 'Exact earned-learning058 mode/seed required'}; $args059+='-Dphantom.test.learningJourney059=true' }
Write-Output ('TEST059 '+$Label+'; mode='+$Mode+'; seed='+$Seed+'; GameSHA='+(Get-FileHash -LiteralPath $jar059).Hash)
$nativePreference059=$ErrorActionPreference
try {
    $ErrorActionPreference='Continue'
    if ($Campaign060) {
        & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed 2>&1 | Out-File -LiteralPath $log059 -Encoding utf8 -Width 4096
    } else {
        & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed *> $log059
    }
    $exit059=$LASTEXITCODE
} finally {$ErrorActionPreference=$nativePreference059}
Get-Content -LiteralPath $log059 -Tail 22
exit $exit059
