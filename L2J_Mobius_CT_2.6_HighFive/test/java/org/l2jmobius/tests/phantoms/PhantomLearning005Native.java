/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.*;

/** TEST-only evaluator around the existing owned Director and canonical GameServer owners. */
public final class PhantomLearning005Native
{
	private static final String BASE="dff800f4b95232c132b374f8ec97c932eda4a6b1";
	private static volatile boolean advantageDeadline;
	public static void main(String[] args)
	{
		int exit=1;
		try
		{
			if(args.length!=2||!Set.of("prepare","report","export").contains(args[0])){throw new IllegalArgumentException("Expected prepare/report/export and owned installation");}
			final var root=PhantomTrainingActors.physicalRoot(Path.of(args[1]));final var config=configuration(root);
			if(args[0].equals("prepare")){prepare(root,config);}else{report(root,config);}
			exit=0;
		}
		catch(Exception failure){failure.printStackTrace(System.err);}
		finally{DatabaseFactory.close();}
		System.exit(exit);
	}
	private static TrainingConfig configuration(Path root) throws Exception
	{
		final var config=TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		if(outcomeEvaluation(config))
		{
			if(!config.text("outcome006Enabled","false").equals("true")||!config.text("learningMode","").equals("SHADOW")||config.longValue("modelVersion",0,0,Long.MAX_VALUE)!=0){throw new IllegalArgumentException("Explicit TEST006R1 evaluation opt-in required");}
			draftAuthority(config);return config;
		}
		if(!config.text("evaluation005Enabled","false").equals("true")||!config.text("configProfile","").equals("EVAL005")||!config.text("evaluation005Mode","").equals("ACCEPTED_ONLY")
			||config.text("v3Core004","false").equals("true")||!config.text("learningMode","SHADOW").equals("SHADOW")||config.longValue("modelVersion",0,0,Long.MAX_VALUE)!=0)
		{throw new IllegalArgumentException("Explicit EVAL005 profile required; training/auto ON forbidden");}
		for(var mode:Mode.values())
		{
			final String prefix=mode==Mode.SOLO_DAMAGE_V1?"evaluation005V1":"evaluation005V3";
			final var pin=PhantomLearning005Evaluation.approved(mode);
			if(config.longValue(prefix+"Id",0,1,8)!=pin.id()||!config.text(prefix+"Sha","").equals(pin.sha())){throw new IllegalArgumentException("Invalid accepted-only evaluation pin");}
		}
		if(!config.text("evaluation005Campaign","").matches("[A-Za-z0-9._-]{1,64}")){throw new IllegalArgumentException("Campaign identity required");}
		config.integer("evaluation005MaxPairsPerScope",4,1,4);
		for(String scope:config.text("evaluation005Scopes","SOLO_DAMAGE_V1,PARTY_HEAL_V3").split(",")){Mode.valueOf(scope);}
		return config;
	}
	private static String jarSha(Path root) throws Exception{return NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));}
	static boolean outcomeEvaluation(TrainingConfig c)
	{return advantageEvaluation(c)||c.text("configProfile","").equals("OUTCOME006")&&c.text("outcome006Campaign","").equals("learning006r1")&&Set.of("PROOF","EVALUATE","CONTROL").contains(c.text("outcome006Phase",""));}
	static boolean advantageEvaluation(TrainingConfig c)
	{return c.text("configProfile","").equals("OUTCOME006")&&PhantomLearning007Native.enabled(c)&&Set.of("EVALUATE","CONTROL").contains(c.text("exploration007Phase",""));}
	private static DraftAuthority draftAuthority(TrainingConfig c)
	{return advantageEvaluation(c)?new DraftAuthority(c.longValue("exploration007DraftId",0,10,Long.MAX_VALUE),c.text("exploration007DraftSha",""),c.text("exploration007TargetSha",""),c.text("exploration007OptimizerSha",""),c.text("exploration007RunId",""),"EVAL007")
		:new DraftAuthority(c.longValue("outcome006DraftId",0,9,Long.MAX_VALUE),c.text("outcome006DraftSha",""),c.text("outcome006TerminalDatasetSha",""),c.text("outcome006OptimizerSha",""),c.text("outcome006RunId",""));}
	private static Pin draftPin(TrainingConfig c,Mode mode)
	{final var a=draftAuthority(c);return new Pin(mode,a.modelId(),"DRAFT",a.modelSha(),TacticalObservationV3.SCHEMA,3,a);}
	private static TacticalStoreV3.Checkpoint verifiedDraft(Path root,TrainingConfig c) throws Exception
	{
		final var pin=draftAuthority(c);final var store=new TacticalStoreV3(root);store.initialize();
		final var model=store.candidate(pin.modelId());final var optimizer=store.optimizationParent();
		if(advantageEvaluation(c))
		{
			if(model==null||!model.status().equals("DRAFT")||!model.sha().equals(pin.modelSha())||!model.runId().equals(pin.runId())||optimizer.modelId()!=pin.modelId()||!optimizer.sha().equals(pin.optimizerSha())
				||!model.metrics().contains("\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\"")){throw new IllegalArgumentException("Frozen007 DRAFT authority changed");}
			var frozen=store.advantageSnapshot(model.id());if(!frozen.datasetSha().equals(pin.terminalDatasetSha())){throw new IllegalArgumentException("Frozen007 targets changed");}
			var offline=org.l2jmobius.gameserver.phantoms.learning.Learning007Checks.offlineResult(model,frozen);
			if(!offline.solo()&&!offline.party()){throw new IllegalArgumentException("G4 did not admit any007 native mode");}
			return model;
		}
		if(model==null||!model.status().equals("DRAFT")||!model.sha().equals(pin.modelSha())||!model.runId().equals(pin.runId())
			||optimizer.modelId()!=pin.modelId()||!optimizer.sha().equals(pin.optimizerSha())||!store.terminalSnapshot(pin.modelId()).datasetSha().equals(pin.terminalDatasetSha())
			||!model.metrics().contains("\"objective\":\"TERMINAL_OUTCOME_V1\"")){throw new IllegalArgumentException("Frozen terminal DRAFT lineage differs from evaluation authority");}
		return model;
	}
	static String evaluationName(TrainingConfig c)
	{
		if(advantageEvaluation(c))
		{String phase=c.text("exploration007Phase","").toLowerCase(Locale.ROOT),name=c.text("exploration007EvaluationCampaign",phase+"-v1");if(!name.matches("[a-z][a-z0-9_-]{0,63}")||!name.startsWith(phase)){throw new IllegalArgumentException("007 phase namespace required");}return name;}
		final String phase=c.text("outcome006Phase","").toLowerCase(Locale.ROOT),name=c.text("outcome006EvaluationCampaign",phase);
		if(!name.matches("[a-z][a-z0-9_-]{0,63}")||!name.startsWith(phase)){throw new IllegalArgumentException("Explicit phase-scoped evaluation campaign name required");}return name;
	}
	private static String campaign(TrainingConfig c){return advantageEvaluation(c)?"learning007-"+evaluationName(c):outcomeEvaluation(c)?"learning006r1-"+evaluationName(c):c.text("evaluation005Campaign","");}
	private static Path directory(Path root,TrainingConfig c){return advantageEvaluation(c)?root.resolve("outcome006/evaluation007").resolve(evaluationName(c)):outcomeEvaluation(c)?root.resolve("outcome006/evaluation006r1").resolve(evaluationName(c)):root.resolve("evaluation005").resolve(c.text("evaluation005Campaign",""));}
	private static PhantomLearning005Ledger ledger(Path root,TrainingConfig c) throws Exception
	{return new PhantomLearning005Ledger(directory(root,c),campaign(c),jarSha(root),protocol(c),outcomeEvaluation(c)?draftAuthority(c):null);}
	private static String protocol(TrainingConfig c) throws Exception
	{
		final Path classes=Path.of(PhantomLearning005Native.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("org/l2jmobius/tests/phantoms");
		final var hashes=new ArrayList<String>();
		try(var paths=Files.list(classes))
		{
			for(var path:paths.filter(p->p.getFileName().toString().endsWith(".class")).filter(p->{String n=p.getFileName().toString();return n.startsWith("PhantomLearning005")||n.startsWith("PhantomLearning001ShadowNative")||n.equals("PhantomTrainingNative.class");}).sorted().toList())
			{hashes.add(path.getFileName()+":"+NeuralPolicy.sha256(Files.readAllBytes(path)));}
		}
		if(hashes.isEmpty()){throw new IllegalStateException("Evaluator bytecode pin missing");}
		return PhantomLearning005Evaluation.hash(BASE+"/005-schema5/n20-time-to-goal/no-safety-regression/"+c.sha256()+hashes);
	}
	private static void prepare(Path root,TrainingConfig config) throws Exception
	{
		if(outcomeEvaluation(config)){prepareOutcome(root,config);return;}
		PhantomTrainingActors.settings(root);
		final var tactical=new TacticalStoreV3(root);tactical.initialize();tactical.ensureLegacyParent();
		final var champion=tactical.accepted(2,PhantomLearning005Evaluation.approved(Mode.PARTY_HEAL_V3).sha());
		if(champion==null||!champion.status().equals("ACCEPTED_TEST")){throw new IllegalStateException("Accepted V3 cold readback failed");}
		if(tactical.accepted(8,"ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016")!=null){throw new IllegalStateException("DRAFT8 was admitted");}
		ConfigLoader.init();DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		final var clone=root.getParent().getParent().getParent();
		final var imported=Learning003DamageImport.load(root,clone.resolve("L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip"),config);
		final long writerDeadline=System.nanoTime()+java.time.Duration.ofSeconds(15).toNanos();
		while(!imported.policy().stop())
		{
			if(System.nanoTime()>=writerDeadline){throw new IllegalStateException("Historical import writer did not drain");}
			Thread.sleep(100);
		}
		Files.createDirectories(directory(root,config));
		try(var ledger=new PhantomLearning005Ledger(directory(root,config),config.text("evaluation005Campaign",""),jarSha(root),protocol(config)))
		{
			for(var pending:ledger.pending())
			{
				verifyPriorExit(root,pending.nonce());
				final var a=pending.arm();
				ledger.put(new Arm(a.pair(),a.side(),a.mode(),a.seed(),a.position(),a.buildSha(),a.configSha(),a.scenario(),a.facts(),a.actors(),a.target(),a.pin(),Outcome.INCOMPLETE,"OWNER_EXIT_BEFORE_SETTLEMENT",0,a.preparationMillis(),Map.of(),List.of(),List.of(),true));
			}
		}
		final String admission="{\"schemaVersion\":5,\"acceptedV1\":"+PhantomLearning005Evaluation.q(imported.checkpoint().model().sha256())+",\"acceptedV3\":"+PhantomLearning005Evaluation.q(champion.sha())+",\"draft8Rejected\":true,\"trainingCounts\":"+counts()+"}";
		Files.writeString(directory(root,config).resolve("admission.json"),admission+"\n");
		System.out.println("EVAL005_ADMISSION "+admission);
		report(root,config);
	}
	private static String counts() throws Exception
	{
		try(var connection=DatabaseFactory.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_ai_training_examples),(SELECT COUNT(*) FROM phantom_tactical_v3_events),(SELECT COUNT(*) FROM phantom_training_attempts)"))
		{if(!rows.next()){throw new IllegalStateException("Training count readback missing");}return "["+rows.getLong(1)+","+rows.getLong(2)+","+rows.getLong(3)+"]";}
	}
	static void verifyPriorExit(Path root,String nonce) throws Exception
	{
		if(nonce==null||!nonce.matches("[a-f0-9]{32}")){throw new IllegalArgumentException("Native nonce required");}
		final var exit=root.resolve("attempts").resolve(nonce).resolve("exit.json");final var record=root.resolve("native-process.json");final var owner=root.resolve("owner.txt");
		for(var path:List.of(exit,record,owner)){if(!path.toAbsolutePath().normalize().equals(path.toRealPath())||Files.size(path)>65536){throw new IllegalStateException("Physical bounded owner receipt required");}}
		final String proof=Files.readString(exit),identity=Files.readString(record);TrainingStore.validateObservation(proof);TrainingStore.validateObservation(identity);
		PhantomLearning005Evaluation.requireRaw(proof,"schemaVersion","1");PhantomLearning005Evaluation.requireRaw(proof,"proof",PhantomLearning005Evaluation.q("OWN_HELD_HANDLE_EXIT"));
		PhantomLearning005Evaluation.requireRaw(proof,"installationOwner",PhantomLearning005Evaluation.q(Files.readString(owner)));
		Integer.parseInt(PhantomLearning005Evaluation.rawField(proof,"exitCode"));
		PhantomLearning005Evaluation.requireRaw(identity,"nonce",PhantomLearning005Evaluation.q(nonce));PhantomLearning005Evaluation.requireRaw(identity,"kind",PhantomLearning005Evaluation.q("Native"));
		for(String key:List.of("pid","created","image","imageHash","sid","nonce","kind","commandHash","configPath","configHash"))
		{PhantomLearning005Evaluation.requireRaw(proof,key,PhantomLearning005Evaluation.rawField(identity,key));}
		if(Long.parseLong(PhantomLearning005Evaluation.rawField(identity,"pid"))<=0||Long.parseLong(PhantomLearning005Evaluation.rawField(identity,"created"))<=0){throw new IllegalStateException("Native process incarnation missing");}
	}
	static void run(Path root,Path attempt,TrainingConfig config) throws Exception
	{
		if(outcomeEvaluation(config)){runOutcome(root,attempt,config);return;}
		configuration(root);PhantomTrainingActors.settings(root);
		final String jar=jarSha(root),before=counts();
		final var v1=new LearningStore(DatabaseConfig.DATABASE_URL,DatabaseConfig.DATABASE_LOGIN,DatabaseConfig.DATABASE_PASSWORD,root.resolve("game")).accepted(1);
		final var v3=new TacticalStoreV3(root).accepted(2,PhantomLearning005Evaluation.approved(Mode.PARTY_HEAL_V3).sha());
		if(v1==null||!v1.model().sha256().equals(PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha())||v3==null){throw new IllegalStateException("Cold accepted policy unavailable before actor actions");}
		for(int warm=0;warm<2048;warm++){v1.model().score(new float[128]);v3.model().scoreEncoded(v3.model().encode(new float[128]),new float[64]);}
		try(var ledger=new PhantomLearning005Ledger(directory(root,config),config.text("evaluation005Campaign",""),jar,protocol(config));var authority=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			for(String scope:config.text("evaluation005Scopes","SOLO_DAMAGE_V1,PARTY_HEAL_V3").split(","))
			{
				final var mode=Mode.valueOf(scope);
				for(int ordinal=0;ordinal<config.integer("evaluation005MaxPairsPerScope",4,1,4)&&!requested(attempt);ordinal++)
				{
					final String pair=mode.name()+"-"+ordinal;
					final var previous=ledger.arms().stream().filter(a->a.pair().equals(pair)&&a.facts().containsKey("npcTemplateLevel")).findFirst().orElse(null);
					final int npc=previous==null?PhantomLearning003Damage.sourcePreflight(authority):Integer.parseInt(previous.facts().get("npcTemplateLevel").split(":")[0]);
					final var scene=PhantomLearning003Damage.scene(npc,77+ordinal%2,ordinal%2==0?"A":"S",ordinal%3);
					final var base=World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
						.filter(m->m.getId()==npc&&!m.isAlikeDead()&&Math.hypot(m.getX()-scene.x(),m.getY()-scene.y())<1600).min(Comparator.comparingInt(Monster::getObjectId)).orElseThrow();
					final String[] pinnedPosition=previous==null?null:previous.facts().get("npcHpPosition").split(":");
					final int x=pinnedPosition==null?base.getX():Integer.parseInt(pinnedPosition[2]),y=pinnedPosition==null?base.getY():Integer.parseInt(pinnedPosition[3]),z=pinnedPosition==null?base.getZ():Integer.parseInt(pinnedPosition[4]);
					final Side first=PhantomLearning005Evaluation.order(config.seed(),ordinal);
					for(int position=0;position<2&&!requested(attempt);position++)
					{
						final Side side=position==0?first:first==Side.STOCK?Side.LEARNED:Side.STOCK;
						if(ledger.arms().stream().anyMatch(a->a.pair().equals(pair)&&a.side()==side)){continue;}
						arm(root,attempt,config,authority,ledger,v1.model(),v3.model(),scene,pair,mode,side,position,ordinal,x,y,z,jar);
					}
				}
			}
		}
		if(!before.equals(counts())){throw new IllegalStateException("EVAL contaminated a training ledger");}
		Files.writeString(attempt.resolve("evaluation005.training-unchanged.json"),"{\"before\":"+before+",\"after\":"+counts()+",\"equal\":true}\n",StandardOpenOption.CREATE_NEW);
		Files.writeString(attempt.resolve("batch.complete"),"schemaVersion=5\nevaluationOnly=true\n",StandardOpenOption.CREATE_NEW);
		report(root,config);
	}
	private record Issued(int actor,long epoch,int target,long targetEpoch,int instance,String selected,String baseline,List<String> legal,long profile,long generation,long sequence) { }
	private static void prepareOutcome(Path root,TrainingConfig c) throws Exception
	{
		PhantomTrainingActors.settings(root);final var model=verifiedDraft(root,c);
		ConfigLoader.init();DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		final var clone=root.getParent().getParent().getParent();
		final var imported=Learning003DamageImport.load(root,clone.resolve("L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip"),c);
		final long writerDeadline=System.nanoTime()+java.time.Duration.ofSeconds(15).toNanos();
		while(!imported.policy().stop()){if(System.nanoTime()>=writerDeadline){throw new IllegalStateException("Historical observer import writer did not drain");}Thread.sleep(100);}
		Learning003DamageImport.verifyUnchanged(root,c);
		try(var ledger=ledger(root,c))
		{
			for(var pending:ledger.pending())
			{
				verifyPriorExit(root,pending.nonce());final var a=pending.arm();
				ledger.put(new Arm(a.pair(),a.side(),a.mode(),a.seed(),a.position(),a.buildSha(),a.configSha(),a.scenario(),a.facts(),a.actors(),a.target(),a.pin(),Outcome.INCOMPLETE,"OWNER_EXIT_BEFORE_SETTLEMENT",0,a.preparationMillis(),Map.of(),List.of(),List.of(),true));
			}
		}
		final String admission="{\"schemaVersion\":6,\"authority\":"+draftAuthority(c).json()+",\"draftStatus\":"+PhantomLearning005Evaluation.q(model.status())+",\"acceptedOnly005Unchanged\":true,\"acceptedV1ObserverSha\":"+PhantomLearning005Evaluation.q(imported.checkpoint().model().sha256())+",\"trainingEnabled\":false}";
		Files.writeString(directory(root,c).resolve("admission.json"),admission+"\n");System.out.println("EVAL006R1_ADMISSION "+admission);
		report(root,c);
	}
	private static String trainingState(Path root,boolean seven) throws Exception
	{
		final var parent=new TacticalStoreV3(root).optimizationParent();
		try(var connection=DatabaseFactory.getConnection();var sql=connection.createStatement();var rows=sql.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_tactical_v3_terminal_rewards),(SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications),(SELECT COUNT(*) FROM phantom_tactical_v3_models),(SELECT COUNT(*) FROM phantom_tactical_v3_optimizers)"))
		{
			if(!rows.next()){throw new IllegalStateException("Evaluation train state missing");}
			String result=counts()+"/"+rows.getLong(1)+"/"+rows.getLong(2)+"/"+rows.getLong(3)+"/"+rows.getLong(4)+"/"+parent.sha();
			if(seven)
			{
				try(var extra=connection.createStatement();var markers=extra.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_tactical_v3_advantage_snapshots),(SELECT COUNT(*) FROM phantom_tactical_v3_advantage_applications),(SELECT COUNT(*) FROM phantom_tactical_v3_advantage_applications WHERE gradient_used=TRUE)"))
				{if(!markers.next()){throw new IllegalStateException("Evaluation007 advantage state missing");}result+="/"+markers.getLong(1)+"/"+markers.getLong(2)+"/"+markers.getLong(3);}
			}
			return result;
		}
	}
	private static boolean fairOutcomeQuota(PhantomLearning005Ledger ledger,Mode mode) throws Exception
	{
		final var complete=ledger.pairs().stream().filter(p->p.stock().mode()==mode&&p.status().equals("MATCHED_COMPLETED")).toList();
		return complete.stream().filter(p->p.stock().position()==0).count()>=2&&complete.stream().filter(p->p.stock().position()==1).count()>=2;
	}
	private static void runOutcome(Path root,Path attempt,TrainingConfig c) throws Exception
	{
		configuration(root);PhantomTrainingActors.settings(root);final var model=verifiedDraft(root,c);
		final var v1=new LearningStore(DatabaseConfig.DATABASE_URL,DatabaseConfig.DATABASE_LOGIN,DatabaseConfig.DATABASE_PASSWORD,root.resolve("game")).accepted(1);
		if(v1==null||!v1.model().sha256().equals(PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha())){throw new IllegalStateException("Stock side observer model unavailable");}
		final boolean seven=advantageEvaluation(c);
		advantageDeadline=seven;
		final String before=trainingState(root,seven),jar=jarSha(root);final boolean ab=(seven?c.text("exploration007Phase",""):c.text("outcome006Phase","")).equals("EVALUATE");
		final var offline=seven?org.l2jmobius.gameserver.phantoms.learning.Learning007Checks.offlineResult(model,new TacticalStoreV3(root).advantageSnapshot(model.id())):null;
		for(int i=0;i<2048;i++){model.model().scoreEncoded(model.model().encode(new float[128]),new float[64]);}
		try(var ledger=ledger(root,c);var authority=PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final boolean smoke=ab&&ledger.arms().isEmpty();final int limit=seven?(ab?4:1):ab&&!smoke?8:1;
			for(var mode:Mode.values())
			{
				if(seven&&!(mode==Mode.SOLO_DAMAGE_V1?offline.solo():offline.party())){continue;}
				for(int ordinal=0;ordinal<limit&&!requested(attempt);ordinal++)
				{
					if(ab&&fairOutcomeQuota(ledger,mode)){break;}
					final String pair=(mode==Mode.SOLO_DAMAGE_V1?"SOLO_DAMAGE_V3":"PARTY_HEAL_V3")+"-"+ordinal;
					final var previous=ledger.arms().stream().filter(a->a.pair().equals(pair)&&a.facts().containsKey("npcTemplateLevel")).findFirst().orElse(null);
					final int npc=previous==null?PhantomLearning003Damage.sourcePreflight(authority):Integer.parseInt(previous.facts().get("npcTemplateLevel").split(":")[0]);
					// Held-out lawful equipment: TRAIN campaign enchants are 0..7. No model score enters scene selection.
					final var scene=PhantomLearning003Damage.scene(npc,77+ordinal%2,ordinal%2==0?"A":"S",8+ordinal%3);
					final var geo=GeoEngine.getInstance();
					final var planner=new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner(authority.knowledge(),authority.topology(),authority.authority());
					final var source=new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm",scene.source());
					final var base=World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
						.filter(m->m.getId()==npc&&!m.isAlikeDead()&&Math.hypot(m.getX()-scene.x(),m.getY()-scene.y())<1600
							&&planner.partyNativeTarget(source,npc,new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(m.getX(),m.getY(),m.getZ(),m.getInstanceId()))
							&&geo.canSeeTarget(scene.x(),scene.y(),scene.z(),0,m.getX(),m.getY(),m.getZ(),m.getInstanceId())
							&&geo.canMoveToTarget(scene.x(),scene.y(),scene.z(),m.getX(),m.getY(),m.getZ(),0))
						.min(Comparator.comparingInt(Monster::getObjectId)).orElseThrow();
					final String[] pinned=previous==null?null:previous.facts().get("npcHpPosition").split(":");
					final int x=pinned==null?base.getX():Integer.parseInt(pinned[2]),y=pinned==null?base.getY():Integer.parseInt(pinned[3]),z=pinned==null?base.getZ():Integer.parseInt(pinned[4]);
					final Side first=PhantomLearning005Evaluation.order(c.seed(),ordinal);
					for(int position=0;position<2&&!requested(attempt);position++)
					{
						final Side side=position==0?first:first==Side.STOCK?Side.LEARNED:Side.STOCK;
						if(ledger.arms().stream().anyMatch(a->a.pair().equals(pair)&&a.side()==side)){continue;}
						arm(root,attempt,c,authority,ledger,v1.model(),model.model(),scene,pair,mode,side,position,ordinal,x,y,z,jar);
					}
				}
			}
		}
		if(!before.equals(trainingState(root,seven))){throw new IllegalStateException("Evaluation changed training credit or optimizer");}
		Files.writeString(attempt.resolve(seven?"evaluation007.training-unchanged.json":"evaluation006r1.training-unchanged.json"),"{\"before\":"+PhantomLearning005Evaluation.q(before)+",\"after\":"+PhantomLearning005Evaluation.q(trainingState(root,seven))+",\"equal\":true}\n",StandardOpenOption.CREATE_NEW);
		Files.writeString(attempt.resolve("batch.complete"),"schemaVersion="+(seven?7:6)+"\nevaluationOnly=true\n",StandardOpenOption.CREATE_NEW);report(root,c);
	}
	private record Raw(String key,String json,byte[] observation) { }
	private static String skill(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill s){return s.skillId()+":"+s.skillLevel();}
	private static String action(LegalActionV3 action){return action.skillId()+":"+action.skillLevel()+"/"+action.targetObjectId();}
	private static void arm(Path root,Path attempt,TrainingConfig config,PhantomBackgroundSuite.ProductionAuthorityFixture authority,PhantomLearning005Ledger ledger,
		NeuralPolicy damage,TacticalModelV3 heal,PhantomTrainingScenarios.Scene scene,String pair,Mode mode,Side side,int position,int ordinal,int x,int y,int z,String jar) throws Exception
	{
		final boolean r1=outcomeEvaluation(config),neural=side==Side.LEARNED&&!(advantageEvaluation(config)?config.text("exploration007Phase",""):config.text("outcome006Phase","")).equals("CONTROL");
		final Pin evaluationPin=r1?draftPin(config,mode):PhantomLearning005Evaluation.approved(mode);
		final Path work=attempt.resolve(advantageEvaluation(config)?"evaluation007":r1?"evaluation006r1":"evaluation005").resolve(pair+"-"+side);Files.createDirectories(work);
		final var effects=new ConcurrentLinkedQueue<Effect>();final var raw=new ConcurrentLinkedQueue<Raw>();final var heals=new ConcurrentLinkedQueue<TacticalReceiptV3>();
		final var nativeTimes=new ConcurrentHashMap<String,TerminalRewardReceiptV1.NativeTiming>();
		if(r1){TerminalRewardReceiptV1.nativeTimingObserver(t->{if(nativeTimes.size()>=2048){throw new IllegalStateException("006R1 timing capacity");}nativeTimes.put(t.eventKey(),t);});}
		final var issued=new ConcurrentHashMap<LearningPolicy.Attempt,Issued>();
		final var policy=LearningPolicy.evaluation(!r1&&mode==Mode.SOLO_DAMAGE_V1&&side==Side.LEARNED?LearningPolicy.Mode.ON:LearningPolicy.Mode.SHADOW,1,damage,4,PhantomLearning005Evaluation.hash(campaign(config)+"/"+pair+"/"+side));
		policy.observer(new LearningPolicy.Observer()
		{
			@Override public void issued(LearningPolicy.Attempt a,Player actor,Monster target)
			{
				if(r1){return;}
				if(issued.size()>=2048){throw new IllegalStateException("005 pending receipt capacity");}
				issued.put(a,new Issued(actor.getObjectId(),TacticalSnapshotV3.epoch(actor),target.getObjectId(),TacticalSnapshotV3.epoch(target),target.getInstanceId(),skill(a.candidate().skill()),skill(a.baseline()),a.offered().stream().map(c->skill(c.skill())).toList(),a.request().profileId(),a.generation(),a.sequence()));
			}
			@Override public void recorded(LearningPolicy.Attempt a,LearningStore.Evidence e)
			{
				if(r1){return;}
				final var i=issued.remove(a);if(i==null||effects.size()>=2048){throw new IllegalStateException("005 missing issued receipt");}
				final String json="{\"origin\":\"NATIVE_V1_EXACT_CAST_HP\",\"eventKey\":"+PhantomLearning005Evaluation.q(e.eventKey())+",\"actor\":"+i.actor()+",\"actorEpoch\":"+i.epoch()+",\"profile\":"+i.profile()+",\"generation\":"+i.generation()+",\"sequence\":"+i.sequence()
					+",\"target\":"+i.target()+",\"targetEpoch\":"+i.targetEpoch()+",\"instance\":"+i.instance()+",\"selected\":"+PhantomLearning005Evaluation.q(i.selected())+",\"baseline\":"+PhantomLearning005Evaluation.q(i.baseline())+",\"legalSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(i.legal())))+",\"actualDamage\":"+e.damage()+",\"exactMpDebit\":null,\"modelVersion\":"+e.version()+",\"modelSha\":"+PhantomLearning005Evaluation.q(damage.sha256())+",\"trainingCredit\":false}";
				final String sha=PhantomLearning005Evaluation.hash(json);
				effects.add(new Effect(e.eventKey(),i.actor(),i.epoch(),i.target(),i.targetEpoch(),i.instance(),i.selected(),i.baseline(),i.legal(),Family.DAMAGE,e.damage(),null,sha,json));raw.add(new Raw(e.eventKey(),json,null));
			}
		});
		final var tactical=mode==Mode.SOLO_DAMAGE_V1&&!r1?TacticalPolicyV3.OFF:new TacticalPolicyV3(neural?TacticalPolicyV3.Mode.ON:TacticalPolicyV3.Mode.SHADOW,r1?evaluationPin.id():2,heal,20,16,2000,r->
		{
			if(r1){if(heals.size()>=2048){throw new IllegalStateException("006R1 native effect capacity");}heals.add(TacticalReceiptV3.fromHeal(r));return;}
			if(effects.size()>=2048){throw new IllegalStateException("005 effect capacity");}
			final var receipt=TacticalReceiptV3.fromHeal(r);final var a=r.attempt();final var s=a.selected();
			if(!(World.getInstance().findObject(s.targetObjectId()) instanceof Player recipient)||TacticalSnapshotV3.epoch(recipient)!=s.targetEpoch()){throw new IllegalStateException("Stale native heal recipient");}
			final String json="{\"origin\":\"NATIVE_V3_EXACT_HEAL_HP\",\"eventKey\":"+PhantomLearning005Evaluation.q(r.eventKey())+",\"actor\":"+s.actorObjectId()+",\"actorEpoch\":"+s.actorEpoch()+",\"owner\":"+PhantomLearning005Evaluation.q(r.owner().operationKey())+",\"generation\":"+r.owner().generation()
				+",\"target\":"+s.targetObjectId()+",\"targetEpoch\":"+s.targetEpoch()+",\"instance\":"+recipient.getInstanceId()+",\"selected\":"+PhantomLearning005Evaluation.q(action(s))+",\"baseline\":"+PhantomLearning005Evaluation.q(action(a.baseline()))+",\"legalSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(a.offered().stream().map(PhantomLearning005Native::action).toList())))+",\"beforeHp\":"+r.beforeHp()+",\"afterHp\":"+r.afterHp()+",\"usefulHp\":"+r.actualHp()+",\"actualMp\":"+r.actualMp()+",\"elapsedNanos\":"+r.elapsedNanos()+",\"modelVersion\":"+a.modelVersion()+",\"modelSha\":"+PhantomLearning005Evaluation.q(heal.sha256())+",\"observationSha\":"+PhantomLearning005Evaluation.q(NeuralPolicy.sha256(a.observation().bytes()))+",\"trainingCredit\":false}";
			effects.add(new Effect(r.eventKey(),s.actorObjectId(),s.actorEpoch(),s.targetObjectId(),s.targetEpoch(),recipient.getInstanceId(),
				action(s),action(a.baseline()),a.offered().stream().map(PhantomLearning005Native::action).toList(),Family.HEAL,r.actualHp(),r.actualMp(),PhantomLearning005Evaluation.hash(json),json));
			heals.add(receipt);raw.add(new Raw(r.eventKey(),json,a.observation().bytes()));
		});
		if(r1&&mode==Mode.SOLO_DAMAGE_V1){tactical.damageObserver(r->{if(heals.size()>=2048){throw new IllegalStateException("006R1 native effect capacity");}heals.add(r);});}
		tactical.failureObserver(failure ->
		{
			final String json=failure.json();raw.add(new Raw(failure.eventKey()+".failure",json,null));
			System.out.println("EVAL005_HEAL_FAILURE pair="+pair+" side="+side+" receipt="+json);
		});
		final long preparation=System.nanoTime(); boolean cut=false;Monster target=null;
		Map<String,String> facts=Map.of();List<Incarnation> incarnations=List.of();Incarnation targetIdentity=null;
		TacticalEpisodeV3 episode=null;String reason="PREPARATION_FAILED";double prepareMillis=0,elapsed=0,drainMillis=0,startHp=0,startMp=0;Outcome outcome=Outcome.SKIP;long nativeCut=0,nativeTerminal=0;
		try(var runtime=new PhantomLearning001ShadowNative.Runtime(authority,root.resolve("game"),policy,attempt.resolve("stop.request"),true,5,tactical,r1&&mode==Mode.SOLO_DAMAGE_V1))
		{
			java.util.function.Supplier<TacticalEpisodeV3> terminal=null;
			try
			{
				final var rows=PhantomLearning003Damage.prepare(authority,scene,scene,work,config);
				runtime.prepareTraining(scene,mode==Mode.PARTY_HEAL_V3?rows.stream().filter(r->!r[1].equals("SOLO")).toList():rows,mode==Mode.PARTY_HEAL_V3,config.preparationSeconds(),()->requested(attempt));
				final var admissions=new StringBuilder();
				for(int candidate=0;candidate<4;candidate++)
				{
					final var spawn=new Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc()));spawn.setXYZ(x,y,z);
					final var spawned=spawn.doSpawn(false);spawn.stopRespawn();if(!(spawned instanceof Monster)){if(spawned!=null){spawned.deleteMe();}throw new IllegalStateException("Ordinary native Monster required");}
					final var npc=(Monster)spawned;
					final boolean eligible=!npc.isChampion()&&!npc.isRaid()&&!npc.isAggressive()&&!npc.isAlikeDead()&&npc.getCurrentHp()==npc.getMaxHp();
					admissions.append("{\"candidate\":").append(candidate).append(",\"objectId\":").append(npc.getObjectId()).append(",\"epoch\":").append(TacticalSnapshotV3.epoch(npc))
						.append(",\"champion\":").append(npc.isChampion()).append(",\"hp\":").append(npc.getCurrentHp()).append(",\"maxHp\":").append(npc.getMaxHp())
						.append(",\"spawnX\":").append(npc.getX()).append(",\"spawnY\":").append(npc.getY()).append(",\"admitted\":").append(eligible).append("}\n");
					if(eligible){target=npc;break;}npc.deleteMe();
				}
				Files.writeString(work.resolve("target-admissions.jsonl"),admissions,StandardOpenOption.CREATE_NEW);
				if(target==null){throw new IllegalStateException("EVAL005_NO_NORMAL_TARGET_WITHIN_FOUR_ADMISSIONS");}
				// Matched declared TEST input before durable CUT. Stock spawn RNG was consumed unchanged.
				if(r1){target.teleToLocation(x,y,z,0,false);target.setHeading(0);runtime.prepareOutcome006(target,ordinal);}
				else{runtime.prepareEvaluation(target,ordinal);target.teleToLocation(x,y,z,0,false);}
				final var players=runtime.evaluationPlayers();facts=facts(players,target,scene,config);
				if(r1){for(var p:players){if(!GeoEngine.getInstance().canSeeTarget(p,target)
					||!GeoEngine.getInstance().canMoveToTarget(p.getX(),p.getY(),p.getZ(),target.getX(),target.getY(),target.getZ(),p.getInstanceId()))
				{throw new IllegalStateException("EVAL006R1_PRE_CUT_GEOMETRY");}}}
				startHp=players.stream().mapToDouble(Player::getCurrentHp).sum();startMp=players.stream().mapToDouble(Player::getCurrentMp).sum();
				incarnations=players.stream().map(p->new Incarnation(p.getObjectId(),TacticalSnapshotV3.epoch(p),p.getInstanceId())).toList();targetIdentity=new Incarnation(target.getObjectId(),TacticalSnapshotV3.epoch(target),target.getInstanceId());
				prepareMillis=(System.nanoTime()-preparation)/1e6;effects.clear();raw.clear();heals.clear();issued.clear();nativeTimes.clear();
				final var start=new Arm(pair,side,mode,config.seed(),position,jar,protocol(config),scene.id(),facts,incarnations,targetIdentity,evaluationPin,Outcome.INCOMPLETE,"STARTED",0,prepareMillis,Map.of(),List.of(),List.of(),true);
				ledger.begin(start,attempt.getFileName().toString());cut=true;
				Files.writeString(work.resolve("precut.json"),start.json()+"\n",StandardOpenOption.CREATE_NEW);
				System.out.println("EVAL005_CUT pair="+pair+" side="+side+" mode="+mode+" target="+targetIdentity+" fingerprint="+start.fingerprint());
				terminal=r1?runtime.runLearning006Encounter(PhantomLearning005Evaluation.hash(campaign(config)+pair+side),attempt.getFileName().toString(),config.episodeSeconds(),()->requested(attempt),()->List.copyOf(heals),stamp->{try{Files.writeString(work.resolve("cut.nanos"),Long.toString(stamp),StandardOpenOption.CREATE_NEW);}catch(java.io.IOException e){throw new IllegalStateException("EVAL006R1 durable CUT failed",e);}})
					:runtime.runLearning004Encounter(PhantomLearning005Evaluation.hash(pair+side),attempt.getFileName().toString(),config.episodeSeconds(),()->requested(attempt),()->List.copyOf(heals));
			}
			catch(IllegalStateException refusal){reason=refusal.getMessage();if(cut){outcome=Outcome.INCOMPLETE;}System.out.println("EVAL005_REFUSAL pair="+pair+" side="+side+" afterCut="+cut+" reason="+reason);}
			finally
			{
				if(terminal==null){runtime.settleTraining();}else{var settled=runtime.settleLearning004(terminal);episode=settled.episode();elapsed=runtime.evaluationEncounterMillis();drainMillis=runtime.evaluationDrainMillis();Files.writeString(work.resolve("terminal.json"),episode.json()+"\n",StandardOpenOption.CREATE_NEW);}
				if(r1){nativeCut=runtime.outcomeCutNanos();nativeTerminal=runtime.outcomeTerminalNanos();runtime.releaseOutcome006();}
			}
		}
		finally{policy.stop();if(target!=null){target.deleteMe();}if(r1){TerminalRewardReceiptV1.nativeTimingObserver(_ -> { });}}
		if(r1){for(var receipt:heals)
		{
			final var time=nativeTimes.get(receipt.eventKey());final var action=receipt.attempt().selected();
			final boolean lethal=episode!=null&&time!=null&&time.lethal()&&action.family()==LegalActionV3.Family.DAMAGE&&episode.kills().stream().anyMatch(k->k.targetObjectId()==action.targetObjectId()&&k.spawnGeneration()==action.targetEpoch());
			if(time!=null&&time.hpStart()>=nativeCut&&time.hpStart()<=nativeTerminal&&(time.hpEnd()<=nativeTerminal||lethal)&&time.mpStart()>=nativeCut&&time.mpEnd()<=nativeTerminal)
			{recordOutcomeEffect(receipt,heal,evaluationPin,time,nativeCut,nativeTerminal,effects,raw);}
			else{raw.add(new Raw(receipt.eventKey()+".excluded",nativeReceiptJson(receipt),receipt.attempt().observation().bytes()));}
		}}
		final var metrics=new TreeMap<String,Double>();final var kills=new ArrayList<Kill>();
		if(episode!=null)
		{
			reason=episode.reason();outcome=Outcome.valueOf(episode.outcome());
			for(var k:episode.kills()){kills.add(new Kill(k.targetObjectId(),k.spawnGeneration(),k.instanceId(),k.creditedActorId()));}
			metrics.put("elapsedMillis",elapsed);metrics.put("drainMillis",drainMillis);metrics.put("settlementSnapshotMillis",(episode.settledNanos()-episode.startedNanos())/1e6);metrics.put("hp",episode.actors().stream().mapToDouble(TacticalEpisodeV3.Actor::hp).sum());metrics.put("mp",episode.actors().stream().mapToDouble(TacticalEpisodeV3.Actor::mp).sum());
			metrics.put("exp",episode.actors().stream().allMatch(a->a.exp()!=null)?episode.actors().stream().mapToDouble(a->a.exp()).sum():null);
			metrics.put("sp",episode.actors().stream().allMatch(a->a.sp()!=null)?episode.actors().stream().mapToDouble(a->a.sp()).sum():null);
			metrics.put("netHpChange",metrics.get("hp")-startHp);metrics.put("netMpChange",metrics.get("mp")-startMp);
			healMetrics(metrics,List.copyOf(effects),List.copyOf(heals),r1);metrics.put("deathsLowerBound",(double)episode.observedDeaths());
			metrics.put("uniqueEnemyKills",(double)PhantomLearning005Evaluation.distinctKills(kills));
			metrics.put("expPerMinute",metrics.get("exp")==null||elapsed<=0?null:metrics.get("exp")*60000/elapsed);metrics.put("spPerMinute",metrics.get("sp")==null||elapsed<=0?null:metrics.get("sp")*60000/elapsed);
			for(String unknown:List.of("deaths","loot","consumables","adenaNet","wholePartyDamage","allActionsMpDebit")){metrics.put(unknown,null);}
			if(effects.isEmpty()){outcome=Outcome.INCOMPLETE;reason="NO_TRACKED_NATIVE_EFFECT";}
		}
		if(!r1&&policy.counters().unknown()>0){outcome=cut?Outcome.INCOMPLETE:Outcome.SKIP;reason="EVIDENCE_REJECTED_"+policy.counters().unknown();}
		if(r1){metrics.put("untrackedStockDamageUnknown",(double)policy.counters().unknown());metrics.put("canonicalCompleted",episode!=null&&episode.outcome().equals("COMPLETED")?1.0:0.0);}
		final var result=new Arm(pair,side,mode,config.seed(),position,jar,protocol(config),scene.id(),facts,incarnations,targetIdentity,evaluationPin,outcome,reason,elapsed,prepareMillis,metrics,List.copyOf(effects),kills,true);
		for(var r:raw){Files.writeString(work.resolve(r.key()+".source.json"),r.json()+"\n",StandardOpenOption.CREATE_NEW);if(r.observation()!=null){Files.write(work.resolve(r.key()+".observation.bin"),r.observation(),StandardOpenOption.CREATE_NEW);}}
		Files.writeString(work.resolve("policy-timing.txt"),"v1="+policy.timing(mode==Mode.SOLO_DAMAGE_V1?org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope.SOLO:org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope.PARTY)+"\nv3="+tactical.timing()+"\n",StandardOpenOption.CREATE_NEW);
		if(r1){Files.writeString(work.resolve("policy-diagnostics.json"),tactical.diagnostics()+"\n",StandardOpenOption.CREATE_NEW);}
		ledger.put(result);System.out.println("EVAL005_ARM "+result.json());
	}
	private static void recordOutcomeEffect(TacticalReceiptV3 receipt,TacticalModelV3 model,Pin pin,TerminalRewardReceiptV1.NativeTiming timing,long cut,long terminal,ConcurrentLinkedQueue<Effect> effects,ConcurrentLinkedQueue<Raw> raw)
	{
		final var a=receipt.attempt();final var selected=a.selected();final var observation=a.observation();final var legal=a.offered().stream().map(PhantomLearning005Native::action).toList();
		final var encoded=model.encode(observation.state());float best=-Float.MAX_VALUE,second=-Float.MAX_VALUE;
		for(var action:a.offered()){final float score=model.scoreEncoded(encoded,action.features(observation));if(score>best){second=best;best=score;}else{second=Math.max(second,score);}}
		final String nativeJson=nativeReceiptJson(receipt),nativeSha=PhantomLearning005Evaluation.hash(nativeJson);
		final boolean heal=selected.family()==LegalActionV3.Family.HEAL;
		final String json="{\"origin\":"+PhantomLearning005Evaluation.q(heal?"NATIVE_V3_EXACT_HEAL_HP":"NATIVE_V3_EXACT_CAST_HP")+",\"eventKey\":"+PhantomLearning005Evaluation.q(receipt.eventKey())
			+",\"actor\":"+selected.actorObjectId()+",\"actorEpoch\":"+selected.actorEpoch()+",\"profile\":"+observation.profileId()+",\"owner\":"+PhantomLearning005Evaluation.q(receipt.owner().operationKey())+",\"generation\":"+receipt.owner().generation()
			+",\"target\":"+selected.targetObjectId()+",\"targetEpoch\":"+selected.targetEpoch()+",\"instance\":"+timing.instanceId()+",\"selected\":"+PhantomLearning005Evaluation.q(action(selected))+",\"baseline\":"+PhantomLearning005Evaluation.q(action(a.baseline()))
			+",\"legalSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(legal)))+",\"beforeHp\":"+receipt.beforeHp()+",\"afterHp\":"+receipt.afterHp()+","+(heal?"\"usefulHp\":":"\"actualDamage\":")+receipt.actualHp()
			+",\"actualMp\":"+receipt.actualMp()+",\"elapsedNanos\":"+receipt.elapsedNanos()+",\"modelVersion\":"+a.modelVersion()+",\"modelSha\":"+PhantomLearning005Evaluation.q(model.sha256())+",\"authoritySha\":"+PhantomLearning005Evaluation.q(pin.authority().fingerprint())
			+",\"selectedScore\":"+model.scoreEncoded(encoded,selected.features(observation))+",\"stockScore\":"+model.scoreEncoded(encoded,a.baseline().features(observation))+",\"margin\":"+(best-second)+",\"scoreEvidence\":\"OFFLINE_RECOMPUTED_FROZEN_OBSERVATION\""
			+",\"cutNanos\":"+cut+",\"terminalNanos\":"+terminal+",\"hpStartedNanos\":"+timing.hpStart()+",\"hpFinishedNanos\":"+timing.hpEnd()+",\"mpStartedNanos\":"+timing.mpStart()+",\"mpFinishedNanos\":"+timing.mpEnd()+",\"linkedLethal\":"+timing.lethal()
			+",\"nativeReceiptSha\":"+PhantomLearning005Evaluation.q(nativeSha)+",\"observationSha\":"+PhantomLearning005Evaluation.q(NeuralPolicy.sha256(observation.bytes()))+",\"trainingCredit\":false}";
		effects.add(new Effect(receipt.eventKey(),selected.actorObjectId(),selected.actorEpoch(),selected.targetObjectId(),selected.targetEpoch(),timing.instanceId(),action(selected),action(a.baseline()),legal,heal?Family.HEAL:Family.DAMAGE,receipt.actualHp(),receipt.actualMp(),PhantomLearning005Evaluation.hash(json),json,pin));
		raw.add(new Raw(receipt.eventKey(),json,observation.bytes()));raw.add(new Raw(receipt.eventKey()+".native",nativeJson,null));
	}
	static void healMetrics(Map<String,Double> metrics,List<Effect> effects,List<TacticalReceiptV3> raw,boolean r1)
	{
		final double hp=raw.stream().filter(r->r.attempt().selected().family()==LegalActionV3.Family.HEAL).mapToDouble(TacticalReceiptV3::actualHp).sum();
		final double mp=raw.stream().filter(r->r.attempt().selected().family()==LegalActionV3.Family.HEAL).mapToDouble(TacticalReceiptV3::actualMp).sum();
		metrics.put("observedUsefulHeal",r1?effects.stream().filter(e->e.family()==Family.HEAL).mapToDouble(Effect::hp).sum():hp);
		metrics.put("observedHealMpDebit",r1?effects.stream().filter(e->e.family()==Family.HEAL).mapToDouble(e->e.mp()==null?0:e.mp()).sum():mp);
		if(r1){metrics.put("rawObservedUsefulHealIncludingDrain",hp);metrics.put("rawObservedHealMpIncludingDrain",mp);}
	}
	static String nativeReceiptJson(TacticalReceiptV3 receipt)
	{return "{\"schemaVersion\":6,\"format\":\"IMMUTABLE_JAVA_RECORD_TEXT_V1\",\"nativeRecord\":"+PhantomLearning005Evaluation.q(receipt.toString())+",\"observationSha\":"+PhantomLearning005Evaluation.q(NeuralPolicy.sha256(receipt.attempt().observation().bytes()))+"}";}
	static Map<String,String> facts(List<Player> players,Monster target,PhantomTrainingScenarios.Scene scene,TrainingConfig config) throws Exception
	{
		final var result=new TreeMap<String,String>();final var classes=new ArrayList<String>();final var skills=new ArrayList<String>();final var equipment=new ArrayList<String>();final var buffs=new ArrayList<String>();final var health=new ArrayList<String>();final var positions=new ArrayList<String>();final var roles=new ArrayList<String>();
		final var geo=GeoEngine.getInstance();final var los=new ArrayList<String>();
		for(int index=0;index<players.size();index++)
		{
			final var p=players.get(index);classes.add(index+":"+p.getActiveClass()+":"+p.getLevel()+":"+p.getMAtkSpd()+":"+p.getPAtk(p)+":"+p.getMAtk(p,null));
			skills.add(index+":"+p.getAllSkills().stream().sorted(Comparator.comparingInt(s->s.getId())).map(s->s.getId()+":"+s.getLevel()+":"+p.isSkillDisabled(s)).toList());
			final var gear=new ArrayList<String>();
			for(int slot=0;slot<Inventory.PAPERDOLL_TOTALSLOTS;slot++)
			{
				final var item=p.getInventory().getPaperdollItem(slot);if(item==null){continue;}
				final var defenses=new ArrayList<Integer>();for(byte element=0;element<6;element++){defenses.add(item.getElementDefAttr(element));}
				gear.add(slot+":"+item.getId()+":"+item.getEnchantLevel()+":"+(item.getAugmentation()==null?0:item.getAugmentation().getAugmentationId())+":"+item.getAttackElementType()+":"+item.getAttackElementPower()+":"+defenses+":"+Arrays.toString(item.getEnchantOptions()));
			}
			equipment.add(index+":"+gear);
			buffs.add(index+":buff="+p.getEffectList().getBuffs().stream().sorted(Comparator.comparingInt(b->b.getSkill().getId())).map(b->b.getSkill().getId()+":"+b.getSkill().getLevel()+":"+b.getTime()).toList()+":debuff="+p.getEffectList().getDebuffs().stream().sorted(Comparator.comparingInt(b->b.getSkill().getId())).map(b->b.getSkill().getId()+":"+b.getSkill().getLevel()+":"+b.getTime()).toList());
			health.add(index+":"+p.getCurrentHp()+"/"+p.getMaxHp()+":"+p.getCurrentMp()+"/"+p.getMaxMp()+":"+p.getCurrentCp()+"/"+p.getMaxCp());
			positions.add(index+":"+p.getX()+":"+p.getY()+":"+p.getZ()+":"+p.getHeading()+":"+p.getInstanceId());
			roles.add(index+":"+p.getActiveClass()+":"+(p.getParty()==null?"SOLO":players.indexOf(p.getParty().getLeader())));
			los.add(index+":"+geo.hasGeo(p.getX(),p.getY())+":"+geo.hasGeo(target.getX(),target.getY())+":"+geo.canSeeTarget(p,target)+":"+geo.canMoveToTarget(p.getX(),p.getY(),p.getZ(),target.getX(),target.getY(),target.getZ(),p.getInstanceId()));
		}
		result.put("classLevel",classes.toString());result.put("learnedSkills",skills.toString());result.put("equipment",equipment.toString());result.put("buffs",buffs.toString());result.put("hpMpCp",health.toString());result.put("positionInstance",positions.toString());result.put("partyRoles",roles.toString());
		result.put("npcTemplateLevel",target.getId()+":"+target.getLevel()+":"+target.getMaxHp()+":"+target.getMaxMp());
		result.put("npcHpPosition",target.getCurrentHp()+":"+target.getCurrentMp()+":"+target.getX()+":"+target.getY()+":"+target.getZ()+":"+target.getHeading()+":"+target.getInstanceId());
		result.put("geoLos",los.toString());result.put("objectiveTimeout","NATIVE_KILL/"+config.episodeSeconds()+"/"+scene.source());
		final Path configs=Path.of("config").toAbsolutePath().normalize();final var hashes=new ArrayList<String>();
		try(var paths=Files.walk(configs)){for(var path:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".ini")&&!p.getFileName().toString().equals("Database.ini")).sorted().toList()){hashes.add(configs.relativize(path)+":"+NeuralPolicy.sha256(Files.readAllBytes(path)));}}
		result.put("configRates",PhantomLearning005Evaluation.hash(hashes.toString()));return Collections.unmodifiableMap(result);
	}
	private static void report(Path root,TrainingConfig config) throws Exception
	{
		if(outcomeEvaluation(config)){reportOutcome(root,config);return;}
		final Path dir=directory(root,config);Files.createDirectories(dir);final List<Arm> arms;final List<Pair> paired;
		try(var ledger=new PhantomLearning005Ledger(dir,config.text("evaluation005Campaign",""),jarSha(root),protocol(config))){arms=ledger.arms();paired=ledger.pairs();}
		final var text=new StringBuilder("# PHANTOM-LEARNING-005 — наблюдения EVAL\n\nСтатус реализации: PARTIAL до проверки всех G0–G8. Это не утверждение о превосходстве модели.\n\n");
		text.append("UNKNOWN не равен нулю. При n<20 вывод о значимом преимуществе запрещён. Bootstrap CI здесь описывает лишь малую наблюдаемую выборку.\n\n");
		text.append("Время цели — первое наблюдение canonical terminal receipt; точность ограничена штатным poll. Drain и общий settlement snapshot сохранены отдельно. HP/MP — сумма личных end-state после drain; net changes включают native regeneration. EXP/SP — сумма персональных кредитов, не награда NPC; observed heal охватывает только точные выбранные receipts.\n\n");
		final var modes=new ArrayList<String>();int expected=0;
		for(String scope:config.text("evaluation005Scopes","SOLO_DAMAGE_V1,PARTY_HEAL_V3").split(","))
		{
			final var mode=Mode.valueOf(scope);final var selected=arms.stream().filter(a->a.mode()==mode).toList();final var pairs=paired.stream().filter(p->p.stock().mode()==mode).toList();
			final var s=PhantomLearning005Evaluation.summarize(pairs,config.seed());final int missing=selected.size()-pairs.size()*2;
			text.append("## ").append(mode).append("\n\nВердикт: **").append(s.label()).append("**, fair completed pairs n=").append(s.n()).append("; arms=").append(selected.size()).append("; одиночных arms=").append(missing).append(".\n\n");
			text.append("| Пара | Статус/причина | A исход/мс | B исход/мс | Δ B−A, мс | Изменено и исполнено |\n|---|---|---|---|---|---|\n");
			for(var p:pairs){text.append('|').append(p.id()).append('|').append(p.status()).append('/').append(p.reason()).append('|').append(p.stock().outcome()).append('/').append(p.stock().elapsedMillis()).append('|').append(p.learned().outcome()).append('/').append(p.learned().elapsedMillis()).append('|').append(p.deltas().get("elapsedMillis")).append('|').append(p.divergences()).append("|\n");}
			text.append("\nМедиана Δtime=").append(s.median()).append("; диапазон=").append(s.minimum()).append("…").append(s.maximum()).append("; 95% bootstrap CI=").append(s.ciLow()).append("…").append(s.ciHigh()).append(". Очень малая выборка не позволяет обобщать.\n\n");
			for(var a:selected){text.append("- ").append(a.pair()).append('/').append(a.side()).append(": ").append(a.outcome()).append('/').append(a.reason()).append("; метрики ").append(a.metrics()).append("; receipt `").append(a.id()).append(".json`; native event keys ").append(a.effects().stream().map(Effect::key).toList()).append(".\n");}
			modes.add("{\"mode\":"+PhantomLearning005Evaluation.q(mode.name())+",\"arms\":"+selected.size()+",\"fairPairs\":"+s.n()+",\"incompletePairs\":"+s.incomplete()+",\"unmatched\":"+s.unmatched()+",\"singleArms\":"+missing+",\"divergences\":"+s.divergences()+",\"label\":"+PhantomLearning005Evaluation.q(s.label())+"}");
			expected+=config.integer("evaluation005MaxPairsPerScope",4,1,4)*2;
		}
		final String summary="{\"schemaVersion\":5,\"campaign\":"+PhantomLearning005Evaluation.q(config.text("evaluation005Campaign",""))+",\"sourceBase\":"+PhantomLearning005Evaluation.q(BASE)+",\"jarSha\":"+PhantomLearning005Evaluation.q(jarSha(root))+",\"protocolSha\":"+PhantomLearning005Evaluation.q(protocol(config))+",\"arms\":"+arms.size()+",\"plannedArms\":"+expected+",\"budgetSettled\":"+(arms.size()==expected)+",\"modes\":["+String.join(",",modes)+"]}";
		Files.writeString(dir.resolve("summary.json"),summary+"\n");Files.writeString(dir.resolve("report.md"),text,StandardCharsets.UTF_8);
		final var tsv=new StringBuilder("pair\tmode\tside\toutcome\treason\telapsedMillis\tpreparationMillis\texp\tsp\texpPerMinute\tremainingHp\tremainingMp\tsourceArm\n");
		for(var a:arms){tsv.append(a.pair()).append('\t').append(a.mode()).append('\t').append(a.side()).append('\t').append(a.outcome()).append('\t').append(a.reason().replace('\t',' ')).append('\t').append(a.elapsedMillis()).append('\t').append(a.preparationMillis()).append('\t').append(a.metrics().get("exp")).append('\t').append(a.metrics().get("sp")).append('\t').append(a.metrics().get("expPerMinute")).append('\t').append(a.metrics().get("hp")).append('\t').append(a.metrics().get("mp")).append('\t').append(a.id()).append(".json\n");}
		Files.writeString(dir.resolve("arms.tsv"),tsv,StandardCharsets.UTF_8);
		if(arms.size()==expected){Files.writeString(dir.resolve("native.complete"),"Budget settled; implementation acceptance remains G0-G8\n");}
		System.out.println("EVAL005_REPORT "+summary);
	}
	private static boolean requested(Path attempt)
	{
		if(Files.exists(attempt.resolve("stop.request"))||Files.exists(attempt.resolve("pause.request"))){return true;}
		return advantageDeadline&&PhantomLearning007Native.evaluationDeadline(attempt.getParent().getParent());
	}
	private static void reportOutcome(Path root,TrainingConfig c) throws Exception
	{
		final List<Arm> arms;final List<org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Pair> pairs;boolean quota=true;
		try(var ledger=ledger(root,c)){arms=ledger.arms();pairs=ledger.pairs();for(var mode:Mode.values()){quota&=fairOutcomeQuota(ledger,mode);}}
		final boolean seven=advantageEvaluation(c),ab=(seven?c.text("exploration007Phase",""):c.text("outcome006Phase","")).equals("EVALUATE");
		final var admitted=seven?org.l2jmobius.gameserver.phantoms.learning.Learning007Checks.offlineResult(new TacticalStoreV3(root).candidate(draftAuthority(c).modelId()),new TacticalStoreV3(root).advantageSnapshot(draftAuthority(c).modelId())):null;
		final int expected=seven?((admitted.solo()?1:0)+(admitted.party()?1:0))*(ab?8:2):ab?32:4;
		final boolean settled=seven?arms.size()>=expected:ab?(quota||arms.size()>=32):arms.size()>=4;
		final var summaries=new ArrayList<String>();
		final var text=new StringBuilder("# PHANTOM-LEARNING-"+(seven?"007":"006R1")+" — независимые TEST наблюдения\n\nНовая модель DRAFT, accepted1/2 неизменны. EVAL никогда не обучает. Game verdict INCONCLUSIVE: бюджет меньше20пар на режим.\n\n");
		text.append("Native outcome подтверждается отдельно terminal.json; неполная action evidence не означает отсутствие kill. UNKNOWN deaths/loot/economy не заменяются0. Score/margin пересчитаны после drain по frozen model и exact observation; native selection и paid effect — из настоящих owners.\n\n");
		for(var mode:Mode.values())
		{
			final var selected=pairs.stream().filter(p->p.stock().mode()==mode).toList();final var s=PhantomLearning005Evaluation.summarize(selected,c.seed());
			final String name=mode==Mode.SOLO_DAMAGE_V1?"SOLO_DAMAGE_V3":"PARTY_HEAL_V3";
			final long firstA=selected.stream().filter(p->p.status().equals("MATCHED_COMPLETED")&&p.stock().position()==0).count(),firstB=selected.stream().filter(p->p.status().equals("MATCHED_COMPLETED")&&p.stock().position()==1).count();
			summaries.add("{\"mode\":"+PhantomLearning005Evaluation.q(name)+",\"fairPairs\":"+s.n()+",\"attemptedPairs\":"+selected.size()+",\"incompletePairs\":"+s.incomplete()+",\"unmatched\":"+s.unmatched()+",\"divergences\":"+s.divergences()+",\"completedAFirst\":"+firstA+",\"completedBFirst\":"+firstB+",\"diagnostic\":"+PhantomLearning005Evaluation.q(s.label())+",\"gameVerdict\":\"INCONCLUSIVE\"}");
			text.append("## ").append(name).append("\n\nFair completed pairs: ").append(s.n()).append("; A→B=").append(firstA).append("; B→A=").append(firstB).append("; paid divergences=").append(s.divergences()).append(". Median Δms=").append(s.median()).append("; 95% bootstrap CI=").append(s.ciLow()).append("…").append(s.ciHigh()).append(".\n\n");
			for(var p:selected){text.append("- ").append(p.id()).append(": ").append(p.status()).append('/').append(p.reason()).append("; A=").append(p.stock().outcome()).append('/').append(p.stock().elapsedMillis()).append("ms; B=").append(p.learned().outcome()).append('/').append(p.learned().elapsedMillis()).append("ms; divergences=").append(p.divergences()).append("; deltas=").append(p.deltas()).append(".\n");}
		}
		final String summary="{\"schemaVersion\":"+(seven?7:6)+",\"split\":"+PhantomLearning005Evaluation.q(draftAuthority(c).namespace())+",\"campaign\":"+PhantomLearning005Evaluation.q(campaign(c))+",\"authority\":"+draftAuthority(c).json()+",\"jarSha\":"+PhantomLearning005Evaluation.q(jarSha(root))+",\"protocolSha\":"+PhantomLearning005Evaluation.q(protocol(c))+",\"arms\":"+arms.size()+",\"quota\":"+quota+",\"budgetSettled\":"+settled+",\"modes\":["+String.join(",",summaries)+"],\"trainingCredit\":false,\"gameVerdict\":\"INCONCLUSIVE\"}";
		Files.writeString(directory(root,c).resolve("summary.json"),summary+"\n");Files.writeString(directory(root,c).resolve("report.md"),text,StandardCharsets.UTF_8);
		final Path complete=directory(root,c).resolve("native.complete");if(settled&&!Files.exists(complete)){Files.writeString(complete,"Bounded evaluation settled; no training or promotion.\n",StandardOpenOption.CREATE_NEW);}
		System.out.println("EVAL006R1_REPORT "+summary);
	}
}
