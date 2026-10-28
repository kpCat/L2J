/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles;
import org.l2jmobius.tests.phantoms.PhantomTopologyCoreSuite;

/** Pure reproduction of the observed READY/ACTIVE/pending native-context cycle; not native population evidence. */
public final class PhantomPopulation102ReadinessChecks
{
    public static void main(String[] args) throws Exception
    {
        if(args.length!=1){throw new IllegalArgumentException("Exact module required; DB-free checks.");}
        int failures=0;
        for(int test=1;test<=3;test++)
        {
            try(var f=new Fixture(Path.of(args[0])))
            {
                if(test==1){f.preparation();}else if(test==2){f.handoff();}else{f.negativeGuards();}
                System.out.println("PASS readiness102."+test);
            }
            catch(AssertionError error){failures++;System.out.println("FAIL readiness102."+test+" "+error.getMessage());}
        }
        PhantomAssertions.assertFalse(DatabaseFactory.isInitialized(),"Pure regression initialized a database.");
        if(failures!=0){throw new AssertionError("readiness102 failed="+failures);}
        System.out.println("READINESS102_PASS tests=3;JDBC=0;World=NOT_INITIALIZED");
    }

    private static final class Fixture implements AutoCloseable
    {
        final PhantomPopulationManager manager;
        final PhantomPopulationEcologyService ecology;
        final PhantomTopologyService topology;
        final AtomicReference<List<PhantomTopologyPoint>> humans=new AtomicReference<>(List.of(PhantomTopologyCoreSuite.LEFT_POINT));
        final AtomicLong nowMillis=new AtomicLong(1000);
        final PhantomRelevanceSignalPort signals=new PhantomRelevanceSignalPort(){
            @Override public SignalDelivery submit(long id,org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal){return SignalDelivery.ACCEPTED;}
            @Override public SignalDelivery withdraw(long id,String source,long sequence){return SignalDelivery.ACCEPTED;}
        };
        final String claim="e".repeat(64);
        final AtomicReference<MaterializationRequest> loaded=new AtomicReference<>();
        final PhantomActivityMaterializationPort delegate=new PhantomActivityMaterializationPort(){
            @Override public TransitionOutcome materialize(long id){loaded.set(MaterializationRequest.normal());return TransitionOutcome.success();}
            @Override public TransitionOutcome materialize(long id,MaterializationRequest request){loaded.set(request);return TransitionOutcome.success(request);}
            @Override public TransitionOutcome dematerialize(long id){return TransitionOutcome.success();}
            @Override public TransitionOutcome retryCleanup(long id){return TransitionOutcome.deferred();}
            @Override public boolean isMaterialized(long id){return false;}
            @Override public boolean hasLifecycleOwnership(long id){return false;}
        };
        Fixture(Path module) throws Exception
        {
            final var instant=Instant.parse("2026-01-05T20:30:00Z");final long target=instant.toEpochMilli()/60000,from=target-120;
            final var population=PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"),ZoneOffset.UTC);
            final var social=PhantomSocialCatalog.load(module.resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
            final var catalog=PhantomPopulationEcologyCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"),population,social);
            final var store=new PhantomPopulationTestDoubles.MemoryStore(population.hash());final var ready=store.seedReady(1,1);
            final var states=new EcologyMemoryStore(null);
            states.insert(1,new PhantomPopulationEcologyState(catalog.hash(),PhantomPopulationEcologyState.Preset.LIVING,1,1,target,from,from,target,
                PhantomPopulationEcologyState.Pace.OUTLIER,10000,catalog.limits().productiveBlockMinutes(),PhantomPopulationEcologyState.Personality.BALANCED,
                Map.of(1,0,2,0,3,0,4,0,5,0,6,0),ready.state().scheduleTemplate(),PhantomPopulationEcologyState.Disposition.MANAGED,target+10000,0,claim,target,0,0,""));
            final var history=new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING,claim,1,from,target,from,0,0,1,1,1,1,0,"f".repeat(64),PhantomBackgroundState.MODEL_VERSION,new PhantomBackgroundState.Hashes("knowledge","topology","progression","commerce"),"");
            final var snapshot=new PhantomBackgroundCatchupStore.Snapshot(history,0);
            final PhantomPopulationEcologyService.HistoricalPort historical=new PhantomPopulationEcologyService.HistoricalPort(){
                @Override public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long id){return Optional.of(snapshot);}
                @Override public PhantomHistoricalBackgroundService.Result begin(long id,long start,long end,long seed){throw new AssertionError("Pending claim was restarted.");}
                @Override public PhantomHistoricalBackgroundService.Result advance(long id,int intervals,int minutes){return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED,"native_context.required:accepted",snapshot);}
            };
            final var clock=new PhantomPopulationTestDoubles.MutableClock(instant);
            ecology=new PhantomPopulationEcologyService(catalog,population,states,historical,id->false,id->"",clock,ZoneOffset.UTC,PhantomPopulationEcologyState.Preset.LIVING,0,10,worker->{worker.run();return true;});
            ecology.enablePeriodicDueMode();
            manager=new PhantomPopulationManager(store,population,null,new PhantomPopulationTestDoubles.Ownership(),clock,ZoneOffset.UTC,1,1,2,1,1,64);
            manager.installEcology(ecology);PhantomAssertions.assertTrue(manager.start(),"Manager start.");
            for(int pulse=0;pulse<32;pulse++){manager.onPulse();}
            PhantomAssertions.assertTrue(manager.presence().isOnline(1),"READY calendar must be online.");
            PhantomAssertions.assertFalse(manager.admissionProfile(1).orElseThrow().eligible(),"Pending catchup must retain ecology eligibility fence.");
            PhantomAssertions.assertFalse(manager.admissionProfile(1).orElseThrow().admitted(),"Pending catchup must not gain ordinary admission.");
            final var backend=new PhantomTopologyCoreSuite.TestBackend();
            topology=PhantomTopologyService.fromSnapshotForTesting(PhantomTopologyCoreSuite.snapshot(backend),backend,PhantomTopologyCoreSuite.POLICY,signals);
            PhantomAssertions.assertTrue(topology.start(),"Topology start.");topology.registerProfile(1);topology.updateProfile(1,PhantomTopologyCoreSuite.LEFT_POINT,1);
        }
        PhantomHumanLocalityControl locality(boolean productionPredicate)
        {
            final var locality=new PhantomHumanLocalityControl(topology,signals,humans::get,nowMillis::get,id->productionPredicate?PhantomSystem.localityPreparationCandidate(manager,id):manager.presence().isOnline(id),Map::of);
            ecology.installMaterializationDemand(id->manager.presence().isOnline(id)&&locality.hasPhysicalDemand(id));
            return locality;
        }
        void preparation()
        {
            final var locality=locality(true);
            locality.installPreparationDemand((facts,slots)->{manager.updateMaterializationDemand(facts);ecology.updateMaterializationDemand(facts,slots);},()->1);
            locality.onPulse();
            PhantomAssertions.assertTrue(locality.hasPhysicalDemand(1),"Actual nearby human geometry absent.");
            PhantomAssertions.assertEquals(1,locality.physicalSnapshot().facts().size(),"READY/calendar-online native-context prerequisite disappeared behind ecology eligibility.");
            PhantomAssertions.assertFalse(ecology.permitsScheduling(1),"Preparation changed ordinary scheduling permission.");
        }
        void handoff()
        {
            final var locality=locality(false);locality.onPulse();
            ecology.updateMaterializationDemand(locality.physicalSnapshot().facts(),1);
            for(int pulse=0;pulse<512 && ecology.preparationSnapshot().admittedPreparationCount()==0;pulse++){manager.onPulse();}
            final var port=new PhantomReconcileFirstActivityPort(delegate);
            port.installPopulationReadiness(manager.presence(),locality,ecology,id->false,id->manager.admissionProfile(id).orElseThrow().admitted());
            final var result=port.materialize(1);
            PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS,result.outcome(),"Exact pending native-context claim blocked: "+result+";prep="+ecology.preparationSnapshot()+";due="+ecology.dueSnapshot(1));
            PhantomAssertions.assertEquals(MaterializationRequest.nativeContextHandoff(claim),loaded.get(),"Exact historical claim was not preserved.");
            PhantomAssertions.assertFalse(ecology.permitsScheduling(1),"Native handoff opened background/schedule admission.");
        }
        void negativeGuards()
        {
            final var locality=locality(false);locality.onPulse();
            final var normal=new PhantomReconcileFirstActivityPort(delegate);
            normal.installPopulationReadiness(manager.presence(),locality,null,id->false,id->false);
            PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,normal.materialize(1).outcome(),"NORMAL bypassed ordinary admission.");
            ecology.updateMaterializationDemand(locality.physicalSnapshot().facts(),0);
            final var noPreparationSlot=new PhantomReconcileFirstActivityPort(delegate);
            noPreparationSlot.installPopulationReadiness(manager.presence(),locality,ecology,id->false,id->false);
            PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,noPreparationSlot.materialize(1).outcome(),"Typed handoff bypassed bounded preparation capacity.");
            humans.set(List.of());nowMillis.addAndGet(1000);locality.onPulse();
            final var noHuman=new PhantomReconcileFirstActivityPort(delegate);
            noHuman.installPopulationReadiness(manager.presence(),locality,ecology,id->false,id->false);
            PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,noHuman.materialize(1).outcome(),"Absent human opened native handoff.");
            humans.set(List.of(PhantomTopologyCoreSuite.LEFT_POINT));manager.presence().schedule(1,PhantomActivityState.SLEEPING);nowMillis.addAndGet(1000);locality.onPulse();
            PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,noHuman.materialize(1).outcome(),"Sleeping calendar opened native handoff.");
            PhantomAssertions.assertEquals(null,loaded.get(),"Negative gate reached materialization delegate.");
        }
        @Override public void close(){manager.beginStop();PhantomAssertions.assertTrue(manager.finishStop(),"Manager stop.");topology.beginStop();PhantomAssertions.assertTrue(topology.finishStop(),"Topology stop.");}
    }
}
