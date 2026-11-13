/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.learning.*;

/** Bounded campaign adapter. All preparation, combat, Party, cast and settlement remain existing native owners. */
public final class PhantomLearning007Native
{
	private static final String PARENT_SHA="5eacf0b7619c82de3950c0203fcf599c149156d13d31d67cfc9e408362b416dc";
	private static final String ADAM_SHA="f76ea960288f3cdb1ba85bb203a33cb327937fdffb99bc6bc4503b90cd54990c";
	private static long budgetOriginNanos,budgetBootstrapMillis=-1;
	private PhantomLearning007Native(){ }
	static boolean enabled(TrainingConfig c){return c.text("exploration007Enabled","false").equals("true")&&c.text("exploration007Campaign","").equals(TrainExplorationV1.CAMPAIGN);}
	static Path directory(Path root){return root.resolve("outcome006/campaigns").resolve(TrainExplorationV1.CAMPAIGN);}
	private static String q(String value){return PhantomLearning005Evaluation.q(value);}
	private static String hash(String value){return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8));}
	private static String json(Map<String,String> facts)
	{return "{"+new TreeMap<>(facts).entrySet().stream().map(e->q(e.getKey())+":"+q(e.getValue())).collect(java.util.stream.Collectors.joining(","))+"}";}
	private static void immutable(Path path,byte[] bytes)throws Exception
	{
		Files.createDirectories(path.getParent());if(Files.exists(path)){if(!path.equals(path.toRealPath())||!Arrays.equals(bytes,Files.readAllBytes(path))){throw new IllegalStateException("007 immutable artifact changed");}return;}
		Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
	}
	private static void immutable(Path path,String text)throws Exception{immutable(path,(text+"\n").getBytes(StandardCharsets.UTF_8));}
	private static long number(String json,String key)
	{
		var m=java.util.regex.Pattern.compile("\""+key+"\"\\s*:\\s*(\\d+)").matcher(json);if(!m.find()){throw new IllegalArgumentException("007 budget field missing: "+key);}return Long.parseLong(m.group(1));
	}
	private static String text(String json,String key)
	{var m=java.util.regex.Pattern.compile("\""+key+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);if(!m.find()){throw new IllegalArgumentException("007 budget field missing");}return m.group(1);}
	private static synchronized long elapsed(Path root)throws Exception
	{
		String b=Files.readString(directory(root).resolve("budget.json"));TrainingStore.validateObservation(b);
		if(budgetBootstrapMillis<0){budgetBootstrapMillis=Math.max(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),Math.max(0,Duration.between(Instant.parse(text(b,"pendingStartedUtc")),Instant.now()).toMillis()));budgetOriginNanos=System.nanoTime();}
		return budgetBootstrapMillis+Math.max(0,System.nanoTime()-budgetOriginNanos)/1000000;
	}
	static boolean evaluationDeadline(Path root)
	{
		try{String b=Files.readString(directory(root).resolve("budget.json"));return number(b,"wallMillis")+elapsed(root)>=5100000;}
		catch(Exception unavailable){return true;}
	}
	private static boolean limit(Path root,TrainingConfig c)throws Exception
	{
		String b=Files.readString(directory(root).resolve("budget.json"));long current=elapsed(root);
		long headroom=(c.preparationSeconds()+c.episodeSeconds()+c.integer("shutdownTimeoutSeconds",180,30,900)+150L)*1000;
		return number(b,"wallMillis")+current+headroom>=5400000||number(b,"collectionWallMillis")+current+headroom>=3600000;
	}
	static void prepare(Path root,TrainingConfig c)throws Exception
	{
		if(!enabled(c)||!c.text("outcome006DatasetId","").equals(TrainExplorationV1.DATASET)||!c.text("v3DatasetContextPrefix","").equals("learning007|"))
		{throw new IllegalArgumentException("Exact007 campaign/dataset/context required");}
		var store=new TacticalStoreV3(root);store.initialize();var parent=store.optimizationParent();
		if(c.text("exploration007Phase","").equals("COLLECT")&&(parent.modelId()!=9||!parent.sha().equals(ADAM_SHA)||!store.candidate(9).sha().equals(PARENT_SHA)))
		{throw new IllegalArgumentException("007 collection parent changed");}
		String jar=NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));
		String protocol="{\"schemaVersion\":7,\"campaign\":"+q(TrainExplorationV1.CAMPAIGN)+",\"dataset\":"+q(TrainExplorationV1.DATASET)+",\"objectiveSha\":"+q(ActionAdvantageV1.CONFIG_SHA)
			+",\"jarSha\":"+q(jar)+",\"parentModelSha\":"+q(PARENT_SHA)+",\"parentOptimizerSha\":"+q(ADAM_SHA)
			+",\"epsilon\":0.25,\"trainBlocks\":32,\"tuningEpisodes\":8,\"auditEpisodes\":8,\"maximumOpportunityPerEpisode\":1"
			+",\"cells\":[\"SOLO/class94/level77/A/enchant0\",\"PARTY/class94/level77/A/enchant0\",\"SOLO/class94/level78/S/enchant2\",\"PARTY/class94/level78/S/enchant2\"]"
			+",\"npc\":21798,\"targetPosition\":\"canonical-current-Geo-LOS-lowest-objectId-per-cell-frozen-before-first-CUT\",\"partyInjuries\":\"existing-prepareOutcome006-variant0or1-per-cell\",\"episodeSeconds\":"+c.episodeSeconds()
			+",\"order\":\"AB,BA,AB,BA,BA,AB,BA,AB\",\"startsCeiling\":8,\"nativeMinutesCeiling\":90,\"collectionMinutesCeiling\":60,\"scope\":\"ISOLATED_TEST_ONLY\"}";
		immutable(directory(root).resolve("protocol.json"),protocol);
		System.out.println("LEARNING007_PROTOCOL sha="+hash(protocol)+" jar="+jar+" parent="+parent.sha());
	}
	public static void main(String[] args)
	{
		int exit=2;
		try
		{
			if(args.length!=2||!args[0].equals("prepare")){throw new IllegalArgumentException("Expected prepare and owned TEST");}
			Path root=PhantomTrainingActors.physicalRoot(Path.of(args[1]));var c=TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
			ConfigLoader.init();DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());prepare(root,c);exit=0;
		}
		catch(Exception failure){System.err.println("LEARNING007_PREPARE_FAILED "+failure.getClass().getSimpleName());}
		finally{DatabaseFactory.close();}
		System.exit(exit);
	}
	static void run(Path root,Path attempt,TrainingConfig c)throws Exception
	{
		if(!enabled(c)||!c.text("exploration007Phase","").equals("COLLECT")){throw new IllegalArgumentException("007 native collection phase required");}
		PhantomTrainingActors.settings(root);prepare(root,c);
		var store=new TacticalStoreV3(root);var model=store.candidate(9);
		var v1=new LearningStore(DatabaseConfig.DATABASE_URL,DatabaseConfig.DATABASE_LOGIN,DatabaseConfig.DATABASE_PASSWORD,root.resolve("game")).accepted(1);
		if(v1==null||!v1.model().sha256().equals("e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2")){throw new IllegalArgumentException("Historical stock observer unavailable");}
		for(int warm=0;warm<512;warm++){model.model().score(new float[128],new float[64]);}
		Path campaign=directory(root);String budget=Files.readString(campaign.resolve("budget.json"));int segment=number(budget,"starts")==1?2:39,attempted=0;boolean timeLimit=false;
		try(var authority=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			if(PhantomLearning003Damage.sourcePreflight(authority)!=21798){throw new IllegalStateException("Declared007 canonical NPC unavailable");}
			for(int slot=0;slot<80&&attempted<segment&&!requested(attempt);slot++)
			{
				Path work=campaign.resolve(String.format("slot-%03d",slot));
				if(Files.exists(work.resolve("reserved.json"))){continue;}
				if(limit(root,c)){timeLimit=true;break;}
				int block=slot<64?slot/2:slot<72?slot-64:slot-72,cell=slot<64?block%4:slot%4,variant=cell/2;
				String split=slot<64?"TRAIN":slot<72?"TUNING":"AUDIT",blockId=split.toLowerCase(Locale.ROOT)+"-"+block;
				int[] order={0,1,0,1,1,0,1,0};boolean experimental=slot<64&&((slot%2==1)==(order[block/4]==0));boolean party=cell%2==1;
				String id=hash(TrainExplorationV1.CAMPAIGN+"/"+slot+"/"+attempt.getFileName());
				immutable(work.resolve("reserved.json"),"{\"slot\":"+slot+",\"episode\":"+q(id)+",\"nonce\":"+q(attempt.getFileName().toString())+",\"split\":"+q(split)+",\"block\":"+q(blockId)+",\"experimental\":"+experimental+",\"cell\":"+cell+"}");
				attempted++;episode(root,attempt,c,authority,store,model,v1.model(),work,id,slot,cell,variant,split,blockId,experimental,party);
			}
		}
		long reserved;try(var paths=Files.list(campaign)){reserved=paths.filter(p->p.getFileName().toString().matches("slot-\\d{3}")).filter(p->Files.exists(p.resolve("reserved.json"))).count();}
		if(reserved>=80||timeLimit){immutable(campaign.resolve("native.complete"),timeLimit?"COLLECTION_TIME_LIMIT":"FIXED_80_SLOTS_CONSUMED");}
		immutable(attempt.resolve("campaign007.segment.json"),"{\"attempted\":"+attempted+",\"reservedTotal\":"+reserved+",\"timeLimit\":"+timeLimit+",\"fit\":false}");
		immutable(attempt.resolve("batch.complete"),"schemaVersion=7\ncollectionOnly=true");
	}
	private static void episode(Path root,Path attempt,TrainingConfig c,PhantomBackgroundSuite.ProductionAuthorityFixture authority,TacticalStoreV3 store,TacticalStoreV3.Checkpoint model,NeuralPolicy v1,
		Path work,String id,int slot,int cell,int variant,String split,String block,boolean experimental,boolean party)throws Exception
	{
		var receipts=new ConcurrentLinkedQueue<TacticalReceiptV3>();var failures=new ConcurrentLinkedQueue<TacticalFailureV3>();var selections=new ConcurrentLinkedQueue<TrainExplorationV1.Selection>();
		var timings=new ConcurrentHashMap<String,TerminalRewardReceiptV1.NativeTiming>();TerminalRewardReceiptV1.nativeTimingObserver(t->{if(timings.size()>=2048){throw new IllegalStateException("007 native effect bound");}timings.put(t.eventKey(),t);});
		var policy=LearningPolicy.evaluation(LearningPolicy.Mode.SHADOW,1,v1,4,hash(TrainExplorationV1.CAMPAIGN+"/"+id));
		var tactical=new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW,model.id(),model.model(),20,16,2000,r->receipts.add(TacticalReceiptV3.fromHeal(r)));
		tactical.damageObserver(receipts::add);tactical.failureObserver(failures::add);
		var scene=PhantomLearning003Damage.scene(21798,77+variant,variant==0?"A":"S",variant*2);
		String context="learning007|"+split+"|"+(party?"PARTY":"SOLO")+"|spot="+scene.source()+"|cell="+cell+"|episode="+id;
		Monster target=null;TacticalEpisodeV3 episode=null;boolean cut=false;String exclusion="",physical="";long preparation=System.nanoTime();
		try(var runtime=new PhantomLearning001ShadowNative.Runtime(authority,root.resolve("game"),policy,attempt.resolve("stop.request"),true,5,tactical,!party))
		{
			java.util.function.Supplier<TacticalEpisodeV3> terminal=null;
			try
			{
				var actors=PhantomLearning003Damage.prepare(authority,scene,scene,work,c);
				runtime.prepareTraining(scene,party?actors.stream().filter(r->!r[1].equals("SOLO")).toList():actors,party,c.preparationSeconds(),()->requested(attempt));
				var geo=GeoEngine.getInstance();Path position=directory(root).resolve("cell-"+cell+".position");int x,y,z;
				if(Files.exists(position)){String[] xyz=Files.readString(position).trim().split(",");x=Integer.parseInt(xyz[0]);y=Integer.parseInt(xyz[1]);z=Integer.parseInt(xyz[2]);}
				else
				{
					var planner=new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner(authority.knowledge(),authority.topology(),authority.authority());
					var source=new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm",scene.source());
					var origin=World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
						.filter(m->m.getId()==scene.npc()&&!m.isAlikeDead()&&Math.hypot(m.getX()-scene.x(),m.getY()-scene.y())<1600
							&&planner.partyNativeTarget(source,scene.npc(),new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(m.getX(),m.getY(),m.getZ(),m.getInstanceId()))
							&&runtime.evaluationPlayers().stream().allMatch(p->geo.canSeeTarget(p,m)&&geo.canMoveToTarget(p.getX(),p.getY(),p.getZ(),m.getX(),m.getY(),m.getZ(),p.getInstanceId())))
						.min(Comparator.comparingInt(Monster::getObjectId)).orElseThrow(()->new IllegalStateException("007_PRE_CUT_CANONICAL_GEOMETRY"));
					x=origin.getX();y=origin.getY();z=origin.getZ();immutable(position,x+","+y+","+z);
				}
				for(int candidate=0;candidate<4;candidate++)
				{
					var spawn=new Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc()));spawn.setXYZ(x,y,z);var npc=(Monster)spawn.doSpawn(false);spawn.stopRespawn();
					boolean admitted=!npc.isChampion()&&!npc.isRaid()&&!npc.isAggressive()&&!npc.isAlikeDead()&&npc.getCurrentHp()==npc.getMaxHp();
					Files.writeString(work.resolve("admissions.jsonl"),"{\"candidate\":"+candidate+",\"objectId\":"+npc.getObjectId()+",\"champion\":"+npc.isChampion()+",\"admitted\":"+admitted+"}\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
					if(admitted){target=npc;break;}npc.deleteMe();
				}
				if(target==null){throw new IllegalStateException("007_NORMAL_TARGET_ADMISSIONS_EXHAUSTED");}
				target.teleToLocation(x,y,z,0,false);target.setHeading(0);runtime.prepareOutcome006(target,variant);
				for(var player:runtime.evaluationPlayers()){if(!geo.canSeeTarget(player,target)||!geo.canMoveToTarget(player.getX(),player.getY(),player.getZ(),target.getX(),target.getY(),target.getZ(),player.getInstanceId())){throw new IllegalStateException("007_PRE_CUT_GEOMETRY");}}
				var facts=PhantomLearning005Native.facts(runtime.evaluationPlayers(),target,scene,c);physical=hash(json(facts));
				immutable(work.resolve("precut.json"),"{\"episode\":"+q(id)+",\"physicalSha\":"+q(physical)+",\"facts\":"+json(facts)+"}");
				var window=TrainExplorationV1.owned(root,id,block,physical,split,experimental,party?LegalActionV3.Family.HEAL:LegalActionV3.Family.DAMAGE,model.id(),model.sha(),selections::add);
				var identity=target.getNativeEvidenceTarget();String before="{\"comparisonCellSha\":"+q(physical)+",\"comparisonFacts\":"+json(facts)+",\"runtime\":"+runtime.outcomeBeforeFacts()+"}";
				long generation=number(Files.readString(root.resolve("director.json")),"generation");
				receipts.clear();failures.clear();timings.clear();cut=true;
				terminal=runtime.runLearning006Encounter(id,attempt.getFileName().toString(),c.episodeSeconds(),()->requested(attempt),()->List.copyOf(receipts),stamp->
				{try{store.beginTerminalEpisode(id,attempt.getFileName().toString(),party?"PARTY":"SOLO",before,generation,identity.instanceId(),identity.objectId(),identity.spawnGeneration(),stamp);tactical.trainExploration(window);}catch(Exception failure){throw new IllegalStateException("007 durable CUT failed",failure);}});
			}
			catch(IllegalStateException unavailable)
			{
				exclusion=unavailable.getMessage();
				if(exclusion==null||exclusion.equals("007 durable CUT failed")||!cut&&!exclusion.startsWith("007_")&&!exclusion.startsWith("EVAL005_")&&!exclusion.startsWith("OUTCOME006_")&&!exclusion.startsWith("LEARNING001_TRAIN_"))
				{immutable(work.resolve("failure.json"),"{\"afterCut\":"+cut+",\"exception\":"+q(unavailable.getClass().getSimpleName())+"}");throw unavailable;}
			}
			finally
			{
				tactical.trainExploration(null);if(terminal==null){runtime.settleTraining();}else{episode=runtime.settleLearning004(terminal).episode();}runtime.releaseOutcome006();
			}
			if(episode!=null)
			{
				for(var r:receipts){store.record(r,split,context);}for(var f:failures){store.recordFailure(f,split,context,id);}store.settleEpisode(episode);
				immutable(work.resolve("settlement.json"),episode.json());
				if(receipts.isEmpty()||!runtime.outcomeEncounterAdmitted()||!Set.of("NATIVE_KILL","NATIVE_ACTOR_DIED","TIMEOUT").contains(episode.reason())){exclusion="NO_ELIGIBLE_TERMINAL_ACTIONS_OR_ENCOUNTER";}
				else
				{
				var identity=target.getNativeEvidenceTarget();var boundary=new TerminalRewardReceiptV1.Boundary(hash(Files.readString(root.resolve("owner.txt"))),attempt.getFileName().toString(),number(Files.readString(root.resolve("director.json")),"generation"),identity.instanceId(),identity.objectId(),identity.spawnGeneration(),runtime.outcomeCutNanos(),runtime.outcomeTerminalNanos(),episode.settledNanos(),true);
				var eligible=new ArrayList<TerminalRewardReceiptV1.TimedAction>();
				for(var r:receipts)
				{
					try{var timing=timings.get(r.eventKey());if(timing==null){throw new IllegalArgumentException("Missing exact native timing");}var action=timing.bind(new TacticalStoreV3.Row(split,context,r));TerminalRewardReceiptV1.create(boundary,episode,List.of(action),split,TrainExplorationV1.DATASET,store.terminalConfig());eligible.add(action);}
					catch(IllegalArgumentException invalid){Files.writeString(work.resolve("excluded-actions.jsonl"),"{\"eventKey\":"+q(r.eventKey())+",\"reason\":"+q(invalid.getMessage())+"}\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
				}
				if(!eligible.isEmpty())
				{
					var reward=TerminalRewardReceiptV1.create(boundary,episode,eligible,split,TrainExplorationV1.DATASET,store.terminalConfig());store.stageTerminal(reward);
					immutable(work.resolve("terminal.bin"),reward.bytes());immutable(work.resolve("terminal.json"),reward.json());
					for(var a:eligible){immutable(work.resolve(a.row().eventKey()+".native.json"),PhantomLearning005Native.nativeReceiptJson(a.row().receipt()));}
				}
				}
			}
			for(var selection:selections)
			{
				immutable(directory(root).resolve("samples").resolve(id+".selection.bin"),selection.bytes());
				immutable(work.resolve("selection.json"),"{\"sha\":"+q(NeuralPolicy.sha256(selection.bytes()))+",\"episode\":"+q(id)+",\"modelId\":"+selection.modelVersion()+",\"modelSha\":"+q(selection.modelSha())
					+",\"split\":"+q(split)+",\"experimental\":"+experimental+",\"propensity\":"+selection.propensity()+",\"draw\":"+selection.draw()+",\"reason\":"+q(selection.reason())+",\"fallback\":"+q(selection.fallback())
					+",\"stock\":"+q(selection.stock().toString())+",\"selected\":"+q(selection.selected().toString())+",\"offered\":["+selection.offered().stream().map(a->q(a.toString())).collect(java.util.stream.Collectors.joining(","))+"],\"budget\":"+Files.readString(directory(root).resolve("budget.json")).trim()+"}");
			}
			immutable(work.resolve("result.json"),"{\"slot\":"+slot+",\"episode\":"+q(id)+",\"split\":"+q(split)+",\"experimental\":"+experimental+",\"physicalSha\":"+q(physical)+",\"afterCut\":"+cut
				+",\"outcome\":"+(episode==null?"null":q(episode.outcome()))+",\"exclusion\":"+q(exclusion)+",\"opportunities\":"+selections.size()+",\"paidRawEffects\":"+receipts.size()+",\"totalMillis\":"+(System.nanoTime()-preparation)/1e6+",\"policyDiagnostics\":"+tactical.diagnostics()+",\"policyTiming\":"+q(tactical.timing())+"}");
			System.out.println("LEARNING007_EPISODE slot="+slot+" split="+split+" B="+experimental+" outcome="+(episode==null?"SKIP":episode.outcome())+" decisions="+selections.size()+" effects="+receipts.size()+" exclusion="+exclusion);
		}
		finally{if(target!=null){target.deleteMe();}TerminalRewardReceiptV1.nativeTimingObserver(_->{ });}
	}
	private static boolean requested(Path attempt){return Files.exists(attempt.resolve("stop.request"))||Files.exists(attempt.resolve("pause.request"));}
}
