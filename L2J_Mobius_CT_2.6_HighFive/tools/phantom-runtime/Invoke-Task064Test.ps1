[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Mode,
      [Parameter(Mandatory)][long]$Seed,[Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
      [string[]]$Features=@(),[ValidateSet('timers','producer','native-phase')][string]$NativeFocus,
      [ValidateSet('build','build-final')][string]$Build='build',[switch]$ValidateOnly)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$module064=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$task064=Join-Path $module064 '.phantom-local/task064'
$runtime064=Join-Path $task064 'runtime'
$build064=Join-Path $task064 $Build
$jar064=Join-Path $build064 'dist/libs/GameServer.jar'
$report064=Join-Path $task064 ('reports/'+$Label)
$log064=Join-Path $task064 ($Label+'.log')
if($Seed -le 0 -or (Test-Path -LiteralPath $report064) -or (Test-Path -LiteralPath $log064)){throw 'Invalid seed or existing evidence; no overwrite.'}
if(@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains('-Dphantom.task064.control=true')}).Count){throw 'Own ordinary runtime is active; native tests are sequential.'}
$receipt064=Join-Path $build064 'BUILD_INFO064.json'
$info064=Get-Content -LiteralPath $receipt064 -Raw | ConvertFrom-Json
$hash064=(Get-FileHash -LiteralPath $jar064).Hash
if($info064.format -ne 1 -or $info064.owner -cne 'TASK064_CAMPAIGN' -or $info064.sourceState -cne 'COMMITTED' -or $info064.codeCommit -cnotmatch '^[0-9a-f]{40}$' -or $info064.sourceArchiveSha256 -cnotmatch '^[A-Fa-f0-9]{64}$' -or $info064.buildRoot -ine $build064 -or $info064.gameJarSha256 -ine $hash064){throw 'Actual test JAR differs from clean committed build receipt.'}
$lines064=@(foreach($tree064 in @('bin','resources')){
    $root064=Join-Path $build064 "phantom-test/$tree064"
    foreach($file064 in Get-ChildItem -LiteralPath $root064 -Recurse -File | Sort-Object FullName){
        $tree064+'/'+$file064.FullName.Substring($root064.Length+1).Replace('\','/')+':'+(Get-FileHash -LiteralPath $file064.FullName).Hash
    }
})
$sha064=[Security.Cryptography.SHA256]::Create()
try{$tests064=([BitConverter]::ToString($sha064.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines064 -join "`n"))))).Replace('-','')}finally{$sha064.Dispose()}
if($info064.testTreeSha256 -ine $tests064){throw 'Actual test binaries/resources differ from clean committed build receipt.'}
if($ValidateOnly){Write-Output "TASK064_TEST_BINDING_PASS commit=$($info064.codeCommit); jar=$hash064; DB_WRITES=0; PROCESS_ACTIONS=0";return}
$flags064=@{
    PvpFlow='pvpFlow059';PvpFlowOnly='pvpFlowOnly059';PvpGuardsOnly='pvpGuardsOnly059';PvpRouteProbe='pvpRouteProbe064';PvpPerimeter='pvpPerimeter064';PvpTowns='pvpTownPerimeter064';
    RaidFlow='raidFlow059';RaidStaging='raidStagingOnly059';RaidLevelGuard='raidLevelGuard059';RaidMinionGuard='raidMinionGuard059';RaidTriggerGuard='raidTriggerGuard059';
    KamalokaFlow='kamalokaFlow059';RiftFlow='riftFlow059';RiftLifecycle='riftLifecycle064';PailakaOnly='pailakaOnly064';LearningJourney='learningJourney059';
    CareerProducer='careerProducer061';AutoProfessions061='autoProfessions061';
    AutonomousLearning='autonomousLearning060';AutonomousLearningGuards='autonomousLearningGuards060';JourneyLifecycle061='journeyLifecycle061';LearningLong061='learningLong061';
    Living='living060';LivingMage='livingMage060';LivingDwarf='livingDwarf060';LivingSupply='livingSupply060';LivingRelocate='livingRelocate060';
    SupplyGuards='supplyGuards060';LoadoutGuards061='loadoutGuards061';LowFunds061='lowFunds061';
    Commands='commands059';Authority='commandAuthority059';BusyHold='busyHold059';SocialContexts='socialContexts059';CardinalBuff='cardinalBuff059';SupportKinds='supportKinds059';SupportRequests='supportRequests059'
}
$options064=@('-Xmx8192m','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',"-Dphantom.module.root=$module064","-Dphantom.contract058.semantic.jar=$jar064","-Dphantom.test.reports=$report064","-Dphantom.test.config=$task064/test/Database.test.ini",'-Dphantom.test.freshIdentities059=true')
# The historical ownership suite is pure and deliberately exercises its unchanged058 contracts.
if($Mode -cne 'campaign058-ownership'){$options064+="-Dphantom.task064.manifest=$task064/test/owned.properties"}
foreach($feature064 in $Features){if(!$flags064.ContainsKey($feature064)){throw 'Unknown exact native feature flag.'};$options064+=('-Dphantom.test.'+$flags064[$feature064]+'=true')}
if($NativeFocus){
    if($Mode -cne 'm1-native-lifecycle'){throw 'Native focus requires the existing M1 lifecycle mode.'}
    $options064+=('-Dphantom.m1.native.focus='+$NativeFocus)
}
if('PvpFlowOnly' -in $Features){$options064+='-Dphantom.test.pvpFlow059=true'}
if('RiftLifecycle' -in $Features){$options064+='-Dphantom.test.riftFlow059=true'}
if(@($Features | Where-Object {$_ -in @('RaidStaging','RaidLevelGuard','RaidMinionGuard','RaidTriggerGuard')}).Count){$options064+='-Dphantom.test.raidFlow059=true'}
New-Item -ItemType Directory -Path $report064 | Out-Null
[ordered]@{format=1;sourceCommit=$info064.codeCommit;sourceArchiveSha256=$info064.sourceArchiveSha256;testTreeSha256=$tests064;buildReceiptSha256=(Get-FileHash -LiteralPath $receipt064).Hash;jar=$jar064;jarSha256=$hash064;schema='l2jmobiush5_phantom_test_064';owner='TASK064_CAMPAIGN';mode=$Mode;seed=$Seed;features=$Features;nativeFocus=$NativeFocus;startedUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $report064 'EPISODE064.json') -Encoding UTF8
$classpath064="$jar064;$build064/phantom-test/bin;$build064/phantom-test/resources;$module064/dist/libs/*"
Write-Output "TASK064_TEST label=$Label; mode=$Mode; seed=$Seed; JAR=$hash064"
Push-Location (Join-Path $runtime064 'game')
$previousEncoding064=[Console]::OutputEncoding
try{
    [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
    $ErrorActionPreference='Continue'
    & java @options064 -cp $classpath064 org.l2jmobius.tests.phantoms.PhantomTestLauncher $Mode $Seed 2>&1 | Out-File -LiteralPath $log064 -Encoding utf8 -Width 4096
    $result064=$LASTEXITCODE
}finally{$ErrorActionPreference='Stop';[Console]::OutputEncoding=$previousEncoding064;Pop-Location}
Get-Content -LiteralPath $log064 -Tail 20
Write-Output "TASK064_TEST_EXIT=$result064"
exit $result064
