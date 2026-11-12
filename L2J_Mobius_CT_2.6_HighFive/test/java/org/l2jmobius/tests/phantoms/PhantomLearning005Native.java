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
	private static Path directory(Path root,TrainingConfig c){return root.resolve("evaluation005").resolve(c.text("evaluation005Campaign",""));}
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
	private record Raw(String key,String json,byte[] observation) { }
	private static String skill(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill s){return s.skillId()+":"+s.skillLevel();}
	private static String action(LegalActionV3 action){return action.skillId()+":"+action.skillLevel()+"/"+action.targetObjectId();}
	private static void arm(Path root,Path attempt,TrainingConfig config,PhantomBackgroundSuite.ProductionAuthorityFixture authority,PhantomLearning005Ledger ledger,
		NeuralPolicy damage,TacticalModelV3 heal,PhantomTrainingScenarios.Scene scene,String pair,Mode mode,Side side,int position,int ordinal,int x,int y,int z,String jar) throws Exception
	{
		final Path work=attempt.resolve("evaluation005").resolve(pair+"-"+side);Files.createDirectories(work);
		final var effects=new ConcurrentLinkedQueue<Effect>();final var raw=new ConcurrentLinkedQueue<Raw>();final var heals=new ConcurrentLinkedQueue<TacticalReceiptV3>();
		final var issued=new ConcurrentHashMap<LearningPolicy.Attempt,Issued>();
		final var policy=LearningPolicy.evaluation(mode==Mode.SOLO_DAMAGE_V1&&side==Side.LEARNED?LearningPolicy.Mode.ON:LearningPolicy.Mode.SHADOW,1,damage,4,PhantomLearning005Evaluation.hash(config.text("evaluation005Campaign","")+"/"+pair+"/"+side));
		policy.observer(new LearningPolicy.Observer()
		{
			@Override public void issued(LearningPolicy.Attempt a,Player actor,Monster target)
			{
				if(issued.size()>=2048){throw new IllegalStateException("005 pending receipt capacity");}
				issued.put(a,new Issued(actor.getObjectId(),TacticalSnapshotV3.epoch(actor),target.getObjectId(),TacticalSnapshotV3.epoch(target),target.getInstanceId(),skill(a.candidate().skill()),skill(a.baseline()),a.offered().stream().map(c->skill(c.skill())).toList(),a.request().profileId(),a.generation(),a.sequence()));
			}
			@Override public void recorded(LearningPolicy.Attempt a,LearningStore.Evidence e)
			{
				final var i=issued.remove(a);if(i==null||effects.size()>=2048){throw new IllegalStateException("005 missing issued receipt");}
				final String json="{\"origin\":\"NATIVE_V1_EXACT_CAST_HP\",\"eventKey\":"+PhantomLearning005Evaluation.q(e.eventKey())+",\"actor\":"+i.actor()+",\"actorEpoch\":"+i.epoch()+",\"profile\":"+i.profile()+",\"generation\":"+i.generation()+",\"sequence\":"+i.sequence()
					+",\"target\":"+i.target()+",\"targetEpoch\":"+i.targetEpoch()+",\"instance\":"+i.instance()+",\"selected\":"+PhantomLearning005Evaluation.q(i.selected())+",\"baseline\":"+PhantomLearning005Evaluation.q(i.baseline())+",\"legalSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(i.legal())))+",\"actualDamage\":"+e.damage()+",\"exactMpDebit\":null,\"modelVersion\":"+e.version()+",\"modelSha\":"+PhantomLearning005Evaluation.q(damage.sha256())+",\"trainingCredit\":false}";
				final String sha=PhantomLearning005Evaluation.hash(json);
				effects.add(new Effect(e.eventKey(),i.actor(),i.epoch(),i.target(),i.targetEpoch(),i.instance(),i.selected(),i.baseline(),i.legal(),Family.DAMAGE,e.damage(),null,sha,json));raw.add(new Raw(e.eventKey(),json,null));
			}
		});
		final var tactical=mode==Mode.SOLO_DAMAGE_V1?TacticalPolicyV3.OFF:new TacticalPolicyV3(side==Side.LEARNED?TacticalPolicyV3.Mode.ON:TacticalPolicyV3.Mode.SHADOW,2,heal,20,16,2000,r->
		{
			if(effects.size()>=2048){throw new IllegalStateException("005 effect capacity");}
			final var receipt=TacticalReceiptV3.fromHeal(r);final var a=r.attempt();final var s=a.selected();
			if(!(World.getInstance().findObject(s.targetObjectId()) instanceof Player recipient)||TacticalSnapshotV3.epoch(recipient)!=s.targetEpoch()){throw new IllegalStateException("Stale native heal recipient");}
			final String json="{\"origin\":\"NATIVE_V3_EXACT_HEAL_HP\",\"eventKey\":"+PhantomLearning005Evaluation.q(r.eventKey())+",\"actor\":"+s.actorObjectId()+",\"actorEpoch\":"+s.actorEpoch()+",\"owner\":"+PhantomLearning005Evaluation.q(r.owner().operationKey())+",\"generation\":"+r.owner().generation()
				+",\"target\":"+s.targetObjectId()+",\"targetEpoch\":"+s.targetEpoch()+",\"instance\":"+recipient.getInstanceId()+",\"selected\":"+PhantomLearning005Evaluation.q(action(s))+",\"baseline\":"+PhantomLearning005Evaluation.q(action(a.baseline()))+",\"legalSha\":"+PhantomLearning005Evaluation.q(PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(a.offered().stream().map(PhantomLearning005Native::action).toList())))+",\"beforeHp\":"+r.beforeHp()+",\"afterHp\":"+r.afterHp()+",\"usefulHp\":"+r.actualHp()+",\"actualMp\":"+r.actualMp()+",\"elapsedNanos\":"+r.elapsedNanos()+",\"modelVersion\":"+a.modelVersion()+",\"modelSha\":"+PhantomLearning005Evaluation.q(heal.sha256())+",\"observationSha\":"+PhantomLearning005Evaluation.q(NeuralPolicy.sha256(a.observation().bytes()))+",\"trainingCredit\":false}";
			effects.add(new Effect(r.eventKey(),s.actorObjectId(),s.actorEpoch(),s.targetObjectId(),s.targetEpoch(),recipient.getInstanceId(),
				action(s),action(a.baseline()),a.offered().stream().map(PhantomLearning005Native::action).toList(),Family.HEAL,r.actualHp(),r.actualMp(),PhantomLearning005Evaluation.hash(json),json));
			heals.add(receipt);raw.add(new Raw(r.eventKey(),json,a.observation().bytes()));
		});
		tactical.failureObserver(failure ->
		{
			final String json=failure.json();raw.add(new Raw(failure.eventKey()+".failure",json,null));
			System.out.println("EVAL005_HEAL_FAILURE pair="+pair+" side="+side+" receipt="+json);
		});
		final long preparation=System.nanoTime(); boolean cut=false;Monster target=null;
		Map<String,String> facts=Map.of();List<Incarnation> incarnations=List.of();Incarnation targetIdentity=null;
		TacticalEpisodeV3 episode=null;String reason="PREPARATION_FAILED";double prepareMillis=0,elapsed=0,drainMillis=0,startHp=0,startMp=0;Outcome outcome=Outcome.SKIP;
		try(var runtime=new PhantomLearning001ShadowNative.Runtime(authority,root.resolve("game"),policy,attempt.resolve("stop.request"),true,5,tactical,false))
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
				runtime.prepareEvaluation(target,ordinal);
				// Matched declared TEST input before durable CUT. Stock spawn RNG was consumed unchanged.
				target.teleToLocation(x,y,z,0,false);
				final var players=runtime.evaluationPlayers();facts=facts(players,target,scene,config);
				startHp=players.stream().mapToDouble(Player::getCurrentHp).sum();startMp=players.stream().mapToDouble(Player::getCurrentMp).sum();
				incarnations=players.stream().map(p->new Incarnation(p.getObjectId(),TacticalSnapshotV3.epoch(p),p.getInstanceId())).toList();targetIdentity=new Incarnation(target.getObjectId(),TacticalSnapshotV3.epoch(target),target.getInstanceId());
				prepareMillis=(System.nanoTime()-preparation)/1e6;effects.clear();raw.clear();heals.clear();issued.clear();
				final var start=new Arm(pair,side,mode,config.seed(),position,jar,protocol(config),scene.id(),facts,incarnations,targetIdentity,PhantomLearning005Evaluation.approved(mode),Outcome.INCOMPLETE,"STARTED",0,prepareMillis,Map.of(),List.of(),List.of(),true);
				ledger.begin(start,attempt.getFileName().toString());cut=true;
				Files.writeString(work.resolve("precut.json"),start.json()+"\n",StandardOpenOption.CREATE_NEW);
				System.out.println("EVAL005_CUT pair="+pair+" side="+side+" mode="+mode+" target="+targetIdentity+" fingerprint="+start.fingerprint());
				terminal=runtime.runLearning004Encounter(PhantomLearning005Evaluation.hash(pair+side),attempt.getFileName().toString(),config.episodeSeconds(),()->requested(attempt),()->List.copyOf(heals));
			}
			catch(IllegalStateException refusal){reason=refusal.getMessage();if(cut){outcome=Outcome.INCOMPLETE;}System.out.println("EVAL005_REFUSAL pair="+pair+" side="+side+" afterCut="+cut+" reason="+reason);}
			finally
			{
				if(terminal==null){runtime.settleTraining();}else{var settled=runtime.settleLearning004(terminal);episode=settled.episode();elapsed=runtime.evaluationEncounterMillis();drainMillis=runtime.evaluationDrainMillis();Files.writeString(work.resolve("terminal.json"),episode.json()+"\n",StandardOpenOption.CREATE_NEW);}
			}
		}
		finally{policy.stop();if(target!=null){target.deleteMe();}}
		final var metrics=new TreeMap<String,Double>();final var kills=new ArrayList<Kill>();
		if(episode!=null)
		{
			reason=episode.reason();outcome=Outcome.valueOf(episode.outcome());
			for(var k:episode.kills()){kills.add(new Kill(k.targetObjectId(),k.spawnGeneration(),k.instanceId(),k.creditedActorId()));}
			metrics.put("elapsedMillis",elapsed);metrics.put("drainMillis",drainMillis);metrics.put("settlementSnapshotMillis",(episode.settledNanos()-episode.startedNanos())/1e6);metrics.put("hp",episode.actors().stream().mapToDouble(TacticalEpisodeV3.Actor::hp).sum());metrics.put("mp",episode.actors().stream().mapToDouble(TacticalEpisodeV3.Actor::mp).sum());
			metrics.put("exp",episode.actors().stream().allMatch(a->a.exp()!=null)?episode.actors().stream().mapToDouble(a->a.exp()).sum():null);
			metrics.put("sp",episode.actors().stream().allMatch(a->a.sp()!=null)?episode.actors().stream().mapToDouble(a->a.sp()).sum():null);
			metrics.put("netHpChange",metrics.get("hp")-startHp);metrics.put("netMpChange",metrics.get("mp")-startMp);
			metrics.put("observedUsefulHeal",heals.stream().mapToDouble(TacticalReceiptV3::actualHp).sum());metrics.put("observedHealMpDebit",heals.stream().mapToDouble(TacticalReceiptV3::actualMp).sum());metrics.put("deathsLowerBound",(double)episode.observedDeaths());
			metrics.put("uniqueEnemyKills",(double)PhantomLearning005Evaluation.distinctKills(kills));
			metrics.put("expPerMinute",metrics.get("exp")==null||elapsed<=0?null:metrics.get("exp")*60000/elapsed);metrics.put("spPerMinute",metrics.get("sp")==null||elapsed<=0?null:metrics.get("sp")*60000/elapsed);
			for(String unknown:List.of("deaths","loot","consumables","adenaNet","wholePartyDamage","allActionsMpDebit")){metrics.put(unknown,null);}
			if(effects.isEmpty()){outcome=Outcome.INCOMPLETE;reason="NO_TRACKED_NATIVE_EFFECT";}
		}
		if(policy.counters().unknown()>0){outcome=cut?Outcome.INCOMPLETE:Outcome.SKIP;reason="EVIDENCE_REJECTED_"+policy.counters().unknown();}
		final var result=new Arm(pair,side,mode,config.seed(),position,jar,protocol(config),scene.id(),facts,incarnations,targetIdentity,PhantomLearning005Evaluation.approved(mode),outcome,reason,elapsed,prepareMillis,metrics,List.copyOf(effects),kills,true);
		for(var r:raw){Files.writeString(work.resolve(r.key()+".source.json"),r.json()+"\n",StandardOpenOption.CREATE_NEW);if(r.observation()!=null){Files.write(work.resolve(r.key()+".observation.bin"),r.observation(),StandardOpenOption.CREATE_NEW);}}
		Files.writeString(work.resolve("policy-timing.txt"),"v1="+policy.timing(mode==Mode.SOLO_DAMAGE_V1?org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope.SOLO:org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope.PARTY)+"\nv3="+tactical.timing()+"\n",StandardOpenOption.CREATE_NEW);
		ledger.put(result);System.out.println("EVAL005_ARM "+result.json());
	}
	private static Map<String,String> facts(List<Player> players,Monster target,PhantomTrainingScenarios.Scene scene,TrainingConfig config) throws Exception
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
	private static boolean requested(Path attempt){return Files.exists(attempt.resolve("stop.request"))||Files.exists(attempt.resolve("pause.request"));}
}
