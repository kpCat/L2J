param([Parameter(Mandatory=$true)][string]$Mode,[Parameter(Mandatory=$true)][long]$Seed,[Parameter(Mandatory=$true)][string]$Label,[switch]$Commands,[switch]$Authority,[switch]$BusyHold,[switch]$SupportRequests,[switch]$SupportKinds,[switch]$CardinalBuff,[switch]$SocialContexts,[switch]$PvpFlow,[switch]$PvpFlowOnly,[switch]$PvpGuardsOnly,[switch]$RaidFlow,[switch]$RaidStaging,[switch]$RaidLevelGuard,[switch]$RaidMinionGuard,[switch]$RaidTriggerGuard,[switch]$KamalokaFlow,[switch]$RiftFlow,[switch]$LearningJourney,[switch]$CleanCandidate,[switch]$Campaign060,[switch]$AutonomousLearning,[switch]$AutonomousLearningGuards,[switch]$Living,[switch]$LivingMage,[switch]$LivingDwarf,[int]$LivingClass=0,[switch]$PaidAutoGetGuards,[switch]$LivingRelocate,[switch]$LivingSupply,[switch]$SupplyGuards,[switch]$LivingOfflineStore,[switch]$SupplyReturnGuards,[switch]$SupplyManufactureGuards,[switch]$LivingManufacture,[switch]$LivingGatekeeper,[switch]$SupplyGatekeeperGuards,[switch]$SupplyResumeGuards,[switch]$LivingResumeSave,[switch]$LivingResumeContinue,[ValidatePattern('^living-resume060-[1-9][0-9]?$')][string]$ResumeReceipt='living-resume060-1',[switch]$DeathCpGuards,[switch]$PetSupport,[switch]$SupplyAmmunitionGuards,[switch]$LivingBow,[switch]$AreaAggro,[switch]$AggroReadiness,[switch]$Campaign061,[switch]$CareerProducer,[switch]$Frozen061,
 [switch]$CareerFull061,[switch]$CareerRouteProbe061,[switch]$CareerWalkStep061,[switch]$CareerDefense061,[switch]$CareerWalkObstacle061,[ValidateSet(25,45)][int]$CareerFullMinutes061=45,[switch]$LearningLong061,[switch]$JourneyLifecycle061,[switch]$LoadoutGuards061)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$hf059='D:\Codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive'
if ($Mode -cnotmatch '^[a-z0-9-]+$' -or $Label -cnotmatch '^[a-z0-9-]+$' -or $Seed -le 0) { throw 'Invalid exact test arguments' }
if ((Get-Location).Path -ine (Join-Path $hf059 '.phantom-local/contract058t/runtime/game')) { throw 'Exact058t native cwd required' }
$frozen061=$PSBoundParameters.ContainsKey('Frozen061') -and $PSBoundParameters['Frozen061']
$frozenMatch061=$false
if($frozen061){
 if(!$Campaign061 -or @($PSBoundParameters.Keys | Where-Object {$_ -cnotin @('Mode','Seed','Label','Campaign061','Frozen061','CleanCandidate')}).Count){throw 'Frozen061 requires only exact mode/seed/label/Campaign061/current-package arguments'}
 $frozenInput061=Join-Path $hf059 '.phantom-local/contract058a/task060/final-current060-r2.tsv'
 if((Get-FileHash -LiteralPath $frozenInput061).Hash -cne '40751731BE90EEF4DAC76C055AF28B98D95D1D825B106F8AB77AE9F6B83C890F'){throw 'Frozen111 inventory changed'}
 $frozenRows061=@(Import-Csv -LiteralPath $frozenInput061 -Delimiter "`t")
 if($frozenRows061.Count -ne 23 -or ($frozenRows061 | Measure-Object -Property total -Sum).Sum -ne 111){throw 'Expected exact23 suites/111 cases'}
 $frozenMatch061=@($frozenRows061 | Where-Object {$_.mode -ceq $Mode -and [long]$_.seed -eq $Seed}).Count -eq 1
 if(!$frozenMatch061){throw 'Mode/seed is not in frozen111 inventory'}
}
if ($Campaign061 -and ($Campaign060 -or !($frozenMatch061 -or ($Mode -ceq 'quest-instance-goal036' -and $Seed -eq 36003601) -or ($Mode -ceq 'lifecycle-completion027' -and $Seed -eq 27002701) -or (($AutonomousLearning -or $PaidAutoGetGuards -or $SupplyGuards) -and $Mode -ceq 'earned-learning-native058' -and $Seed -eq 58005833)))) {throw 'TASK061 requires an exact frozen111, quest controls/producer, owned lifecycle, supply or learning continuation mode/seed/build'}
if ($Campaign061 -and $SupplyGuards -and @($PSBoundParameters.Keys | Where-Object {$_ -cnotin @('Mode','Seed','Label','Campaign061','CleanCandidate','SupplyGuards','SupplyAmmunitionGuards','SupplyReturnGuards','SupplyResumeGuards','SupplyManufactureGuards','SupplyGatekeeperGuards','LoadoutGuards061')}).Count) {throw 'TASK061 supply continuation has an unrelated flag'}
if($LoadoutGuards061 -and (!$Campaign061 -or !$SupplyGuards -or @($PSBoundParameters.Keys | Where-Object {$_ -cnotin @('Mode','Seed','Label','Campaign061','CleanCandidate','SupplyGuards','LoadoutGuards061')}).Count)){throw 'Native loadout guards require separate exact061 supply mode'}
if ($CareerProducer -and !$Campaign061) {throw 'Career061 input requires explicit TASK061 authority'}
if($LearningLong061 -and (!$Campaign061 -or !$PaidAutoGetGuards -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833)){throw 'Observed learning route requires separate exact061 paid guard mode/seed'}
if ($CareerFull061 -and (!$Campaign061 -or !$CareerProducer -or $Mode -cne 'quest-instance-goal036' -or $Seed -ne 36003601)) {throw 'Full career requires separate exact061 producer mode/seed'}
if($PSBoundParameters.ContainsKey('CareerFullMinutes061') -and !$CareerFull061){throw 'Strict new episode bound requires full061 TEST mode'}
if ($CareerRouteProbe061 -and (!$Campaign061 -or !$CareerProducer -or $CareerFull061 -or $Mode -cne 'quest-instance-goal036' -or $Seed -ne 36003601)) {throw 'Route probe requires separate exact061 producer mode/seed'}
if ($CareerWalkStep061 -and !$CareerRouteProbe061) {throw 'Walk step requires exact061 separate route probe'}
if ($CareerWalkObstacle061 -and (!$CareerWalkStep061 -or $CareerDefense061 -or $CleanCandidate)) {throw 'Observed obstacle requires separate focused061 local walk TEST build'}
if ($CareerDefense061 -and (!$Campaign061 -or !$CareerProducer -or $CareerFull061 -or $CareerRouteProbe061 -or $CareerWalkStep061 -or $Mode -cne 'quest-instance-goal036' -or $Seed -ne 36003601)) {throw 'Native defense requires separate exact061 producer mode/seed'}
if ($Campaign061 -and (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object CommandLine -Match 'World-Career-061-candidate|Living-060-candidate|M3-M7-059-candidate|task061')) {throw 'Own061 or protected059/060 JVM active; sequential only'}
if ($AutonomousLearning -and ((!$Campaign060 -and !$Campaign061) -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833)) {throw 'Autonomous learning requires exact060/061 continuation of native058 mode/seed'}
if ($AutonomousLearningGuards -and !$AutonomousLearning) {throw 'Learning guards require the exact060 autonomous learning input'}
if ($JourneyLifecycle061 -and (!$Campaign061 -or !$AutonomousLearningGuards -or !$AutonomousLearning)) {throw 'Journey lifecycle requires the exact061 autonomous learning guards'}
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
if ($Campaign061) {
 $build059=Join-Path $hf059 '.phantom-local/contract058a/task061/build'
 $jar059=Join-Path $build059 'dist/libs/GameServer.jar'
 if ($CleanCandidate) {
  $jar059='D:\PhantomWorld\World-Career-061-candidate\libs\GameServer.jar'
  $compiledRoot061=Join-Path $hf059 '.phantom-local/contract058a/build/release061/compiled'
  $package061=Get-Content -LiteralPath 'D:\PhantomWorld\World-Career-061-candidate\BUILD_INFO.json' -Raw | ConvertFrom-Json
  if($null -ne $package061.PSObject.Properties['cleanCompiledRoot061']){
   $allowedRoots061=@('correction1','correction2','lifecycle1','career1','career2','career3','final1','final2','career4','career5','career6','career7','career8','career9','career10','career11','career12','career13','career14') | ForEach-Object {Join-Path $hf059 ('.phantom-local/contract058a/build/release061-'+$_+'/compiled')}
   if($package061.cleanCompiledRoot061 -cnotin $allowedRoots061){throw 'Unknown corrective clean061 build root'}
   $compiledRoot061=$package061.cleanCompiledRoot061
  }
  $compiled061=Join-Path $compiledRoot061 'dist/libs/GameServer.jar'
  if((Get-FileHash -LiteralPath $jar059).Hash -cne (Get-FileHash -LiteralPath $compiled061).Hash){throw 'Exact061 candidate is not its clean whole compiled JAR'}
 }
 $report059=Join-Path $hf059 ('.phantom-local/contract058t/reports/task061/'+$Label)
 $log059=Join-Path $hf059 ('.phantom-local/contract058a/task061/'+$Label+'.log')
}
if ((Test-Path -LiteralPath $report059) -or (Test-Path -LiteralPath $log059)) { throw 'Evidence exists; no overwrite' }
$testBuild059=if($Campaign061 -and $CleanCandidate){$compiledRoot061}elseif($Campaign061){Join-Path $hf059 '.phantom-local/contract058a/task061/build'}elseif($CleanCandidate -and $PvpFlowOnly -and !$Campaign060){Join-Path $hf059 '.phantom-local/contract058a/task059/build'}else{$build059}
$classpath059="$jar059;$testBuild059\phantom-test\bin;$testBuild059\phantom-test\resources;$hf059\dist\libs\*"
if($CareerWalkObstacle061){
 $focused061=Join-Path $hf059 '.phantom-local/contract058a/task061/route-obstacle-bin'
 if(!(Test-Path -LiteralPath (Join-Path $focused061 'source.sha256')) -or [IO.File]::ReadAllText((Join-Path $focused061 'source.sha256')).Trim() -cne (Get-FileHash -LiteralPath (Join-Path $hf059 'test/java/org/l2jmobius/tests/phantoms/PhantomQuestInstanceGoal036Suite.java')).Hash){throw 'Focused route helper source provenance differs'}
 $classpath059="$jar059;$focused061;$testBuild059\phantom-test\bin;$testBuild059\phantom-test\resources;$hf059\dist\libs\*"
}
$args059=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$hf059","-Dphantom.contract058.semantic.jar=$jar059","-Dphantom.test.reports=$report059","-Dphantom.test.config=$hf059\.phantom-local\contract058t\test\Database.test.ini","-Dphantom.contract058.manifest=$hf059\.phantom-local\contract058t\test\owned.properties")
$args059+='-Dphantom.test.freshIdentities059=true'
if ($JourneyLifecycle061) {$args059+='-Dphantom.test.journeyLifecycle061=true'}
if ($CareerProducer) {$args059+='-Dphantom.test.careerProducer061=true'}
if ($CareerFull061) {$args059+='-Dphantom.test.careerFull061=true';$args059+=('-Dphantom.test.careerFullMinutes061='+$CareerFullMinutes061)}
if ($CareerRouteProbe061) {$args059+='-Dphantom.test.careerRouteProbe061=true'}
if ($CareerWalkStep061) {$args059+='-Dphantom.test.careerWalkStep061=true'}
if ($CareerWalkObstacle061) {$args059+='-Dphantom.test.careerWalkObstacle061=true'}
if ($CareerDefense061) {$args059+='-Dphantom.test.careerDefense061=true'}
if ($Mode -ceq 'lifecycle-completion027') {
 if (!$Campaign061 -or $Seed -ne 27002701) {throw 'Owned career lifecycle requires exact061 mode/seed'}
 $args059+='-Dphantom.m1.native.focus=career061'
}
if ($Campaign061 -and $AutonomousLearning) {$args059+='-Dphantom.test.nearbyLife061=true'}
if ($Campaign061 -and $PaidAutoGetGuards) {$args059+='-Dphantom.test.retainedAutoGet061=true'}
if ($LearningLong061) {$args059+='-Dphantom.test.learningLong061=true'}
if ($SupplyAmmunitionGuards) { if ((!$Campaign060 -and !$Campaign061) -or !$SupplyGuards -or $Living -or $PetSupport -or $SupplyGatekeeperGuards -or $SupplyManufactureGuards -or $SupplyReturnGuards -or $SupplyResumeGuards) {throw 'Ammunition guards require separate exact060/061 SupplyGuards'}; $args059+='-Dphantom.test.supplyAmmunitionGuards060=true' }
if ($LivingBow) { if (!$Campaign060 -or !$Living -or !$LivingSupply -or $LivingClass -notin @(0,53) -or $LivingMage -or $LivingResumeSave -or $LivingResumeContinue -or $LivingGatekeeper -or $LivingOfflineStore -or $LivingManufacture) {throw 'Bow requires separate declared Fighter/Dwarf NPC supply scene'}; $args059+='-Dphantom.test.livingBow060=true' }
if ($AggroReadiness) { if (!$Campaign060 -or $Mode -cne 'companion-npc-aggro058' -or $Seed -ne 58005817 -or $Living -or $AreaAggro -or $PetSupport -or $SupplyGuards) {throw 'Aggro readiness requires separate exact060 native NPC scene'}; $args059+='-Dphantom.test.aggroReadiness060=true' }
if ($AreaAggro) { if (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $PetSupport -or $SupplyGuards -or $AutonomousLearning) {throw 'Area aggression requires separate exact060 native input'}; $args059+='-Dphantom.test.areaAggro060=true' }
if ($PetSupport) { if (!$Campaign060 -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $AutonomousLearning -or $SupplyGuards) {throw 'Pet support requires separate exact060 native input'}; $args059+='-Dphantom.test.petSupport060=true' }
if ($PaidAutoGetGuards) {
 if ((!$Campaign060 -and !$Campaign061) -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $AutonomousLearning) {throw 'Paid auto-get contract requires exact separate060/061 mode/seed'}
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
if ($DeathCpGuards) { if (!$Campaign060 -or !$Living -or !$LivingSupply -or !$LivingDwarf -or $LivingResumeSave -or $LivingResumeContinue -or $LivingOfflineStore -or $LivingManufacture -or $LivingGatekeeper) {throw 'Native death CP control requires separate declared Dwarf life input'}; $args059+='-Dphantom.test.deathCpGuards060=true' }
if ($LivingResumeSave -or $LivingResumeContinue) {
 if (($LivingResumeSave -and $LivingResumeContinue) -or !$Living -or !$LivingSupply -or !$Campaign060 -or $LivingGatekeeper -or $LivingManufacture -or $LivingOfflineStore -or $LivingRelocate -or $LivingMage -or $LivingClass -notin @(0,53)) {throw 'Two-JVM resume requires separate declared Fighter/Dwarf NPC supply phases'}
 $args059+=('-Dphantom.test.livingResumeReceipt060='+$ResumeReceipt)
 if($LivingResumeSave){$args059+='-Dphantom.test.livingResumeSave060=true'}else{$args059+='-Dphantom.test.livingResumeContinue060=true'}
}
if ($SupplyResumeGuards) { if (!$SupplyGuards -or $SupplyGatekeeperGuards -or $SupplyManufactureGuards -or $SupplyReturnGuards) {throw 'Resume models require separate060 SupplyGuards'}; $args059+='-Dphantom.test.supplyResumeGuards060=true' }
if ($SupplyGatekeeperGuards) { if (!$SupplyGuards -or $SupplyManufactureGuards -or $SupplyReturnGuards) {throw 'Native GK corridor control requires separate060 SupplyGuards'}; $args059+='-Dphantom.test.supplyGatekeeperGuards060=true' }
if ($SupplyManufactureGuards) { if (!$SupplyGuards -or $SupplyReturnGuards) {throw 'Native manufacture source control requires exact separate SupplyGuards input'}; $args059+='-Dphantom.test.supplyManufactureGuards060=true' }
if ($LivingGatekeeper) { if (!$LivingSupply -or !$Campaign060 -or $LivingOfflineStore -or $LivingManufacture -or $LivingMage -or (!$LivingDwarf -and $LivingClass -ne 53)) {throw 'Gatekeeper requires separate prepared Dwarf supply scene'}; $args059+='-Dphantom.test.livingGatekeeper060=true' }
if ($LivingManufacture) { if (!$LivingSupply -or !$Campaign060 -or $LivingOfflineStore -or $LivingMage -or (!$LivingDwarf -and $LivingClass -ne 53)) {throw 'Manufacture requires separate prepared Dwarf supply scene'}; $args059+='-Dphantom.test.livingManufacture060=true' }
if ($SupplyGuards) {
 if ((!$Campaign060 -and !$Campaign061) -or $Mode -cne 'earned-learning-native058' -or $Seed -ne 58005833 -or $Living -or $AutonomousLearning) {throw 'Supply contracts require exact separate060/061 mode/seed'}
 $args059+='-Dphantom.test.supplyGuards060=true'
}
if ($LoadoutGuards061) {$args059+='-Dphantom.test.loadoutGuards061=true'}
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
    if ($Mode -ceq 'lifecycle-completion027') {
        & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomLifecycleCompletion027Suite $hf059 $report059 *> $log059
    } elseif ($Campaign060) {
        & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed 2>&1 | Out-File -LiteralPath $log059 -Encoding utf8 -Width 4096
    } else {
        & 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' @args059 -cp $classpath059 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed *> $log059
    }
    $exit059=$LASTEXITCODE
} finally {$ErrorActionPreference=$nativePreference059}
Get-Content -LiteralPath $log059 -Tail 22
exit $exit059
