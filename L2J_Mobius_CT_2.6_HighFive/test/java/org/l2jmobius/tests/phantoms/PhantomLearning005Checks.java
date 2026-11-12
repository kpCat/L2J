/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.*;

/** DB-free contracts. These fixtures are never exported as native evidence. */
public final class PhantomLearning005Checks
{
	private static final String SHA = "a".repeat(64);
	private static int checks;
	public static void main(String[] args) throws Exception
	{
		final var a = arm("pair1", Side.STOCK, Outcome.COMPLETED, "gear1", 1, 100, false);
		final var b = arm("pair1", Side.LEARNED, Outcome.COMPLETED, "gear1", 2, 90, true);
		check(PhantomLearning005Evaluation.compare(a,b).status().equals("MATCHED_COMPLETED"), "T01 equal facts and independent incarnations");
		check(PhantomLearning005Evaluation.compare(a,arm("pair1",Side.LEARNED,Outcome.COMPLETED,"gear2",2,90,true)).status().equals("UNMATCHED"), "T01 mutated gear must reject");
		for (var mode : Mode.values()) { PhantomLearning005Evaluation.admit(PhantomLearning005Evaluation.approved(mode)); }
		reject(() -> PhantomLearning005Evaluation.admit(new Pin(Mode.PARTY_HEAL_V3,8,"DRAFT",SHA,"schema",3)), "T02 draft admission");
		reject(() -> PhantomLearning005Evaluation.admit(new Pin(Mode.SOLO_DAMAGE_V1,1,"ACCEPTED",SHA,"schema",2)), "T02 wrong SHA/schema");
		check(PhantomLearning005Evaluation.order(183,0) != PhantomLearning005Evaluation.order(183,1), "T03 balanced order");
		check(PhantomLearning005Evaluation.compare(a,arm("pair1",Side.LEARNED,Outcome.COMPLETED,"gear1",2,90,false)).divergences()==0,"T03 same policy stochastic outcome is no intervention");
		check(!PhantomLearning005Evaluation.compare(a,arm("pair1",Side.LEARNED,Outcome.INCOMPLETE,"gear1",2,90,true)).status().equals("MATCHED_COMPLETED"),"T04 incomplete not completed");
		check(PhantomLearning005Evaluation.distinctKills(List.of(new Kill(50,9,0,1),new Kill(50,9,0,2)))==1,"T05 party member kill dedup");
		check(PhantomLearning005Evaluation.compare(a,b).deltas().get("loot")==null,"T06 unknown not zero");
		reject(()->new Effect(SHA,1,1,51,1,0,"a","a",List.of("a"),Family.DAMAGE,0,null,SHA,"{}"),"T06 failed/zero cast is not a native effect");
		final var root = Files.createTempDirectory("learning005-contract-").toRealPath();
		try (var ledger = new PhantomLearning005Ledger(root,"campaign",SHA))
		{
			check(ledger.put(a),"T07 first arm"); check(!ledger.put(a),"T07 exact retry idempotent");
			reject(() -> ledger.put(arm("pair1",Side.STOCK,Outcome.COMPLETED,"gear2",1,100,false)),"T07 conflicting replay");
			ledger.put(b);
			reject(() -> new PhantomLearning005Ledger(root,"campaign",SHA),"T07 simultaneous process lock");
		}
		try (var ledger = new PhantomLearning005Ledger(root,"campaign",SHA))
		{ check(ledger.arms().size()==2,"T07 cold resume durable"); }
		try (var ledger = new PhantomLearning005Ledger(root,"campaign",SHA))
		{
			final var start=new Arm("unfinished",Side.STOCK,a.mode(),a.seed(),0,a.buildSha(),a.configSha(),a.scenario(),a.facts(),a.actors(),a.target(),a.pin(),Outcome.INCOMPLETE,"STARTED",0,0,Map.of(),List.of(),List.of(),true);
			ledger.begin(start,"a".repeat(32));check(ledger.pending().size()==1,"T07 durable START is not completed credit");
		}
		try (var ledger = new PhantomLearning005Ledger(root,"campaign",SHA)) {check(ledger.pending().size()==1&&ledger.arms().size()==2,"T07 pending START survives cold resume without replay");}
		reject(() -> new PhantomLearning005Ledger(root,"campaign","b".repeat(64)),"T07 changed campaign build");
		final var foreign=effect("foreign",1,999,false);
		reject(()->new Arm(a.pair(),a.side(),a.mode(),a.seed(),a.position(),a.buildSha(),a.configSha(),a.scenario(),a.facts(),a.actors(),a.target(),a.pin(),a.outcome(),a.reason(),a.elapsedMillis(),a.preparationMillis(),a.metrics(),List.of(foreign),a.kills(),true),"foreign native damage recipient");
		reject(()->new Effect(foreign.key(),foreign.actor(),foreign.actorEpoch(),foreign.target(),foreign.targetEpoch(),foreign.instance(),foreign.selected(),foreign.baseline(),foreign.legal(),foreign.family(),foreign.hp(),foreign.mp(),foreign.sourceSha(),"{}"),"missing native source receipt");
		final var json = root.resolve(a.id()+".json"); Files.writeString(json,"{malformed");
		reject(() -> { try (var ledger = new PhantomLearning005Ledger(root,"campaign",SHA)) { ledger.arms(); } },"T07 mutated JSON");
		check(PhantomLearning005Evaluation.SPLIT.equals("EVAL005") && !List.of("TRAIN","TUNING","AUDIT").contains(PhantomLearning005Evaluation.SPLIT),"T08 held out lineage");
		final var pairs = new ArrayList<Pair>();
		for (int i=0;i<4;i++) { pairs.add(PhantomLearning005Evaluation.compare(arm("p"+i,Side.STOCK,Outcome.COMPLETED,"gear1",i*2+1,100,false),arm("p"+i,Side.LEARNED,Outcome.COMPLETED,"gear1",i*2+2,90,true))); }
		check(PhantomLearning005Evaluation.summarize(pairs,183).label().equals("INCONCLUSIVE"),"T09 four faster pairs are inconclusive");
		final var module=java.nio.file.Path.of(System.getProperty("phantom.module.root","L2J_Mobius_CT_2.6_HighFive"));
		final byte[] weights;
		try(var zip=new java.util.zip.ZipFile(module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip").toFile());var input=zip.getInputStream(zip.getEntry("weights.bin"))){weights=input.readAllBytes();}
		final var model=org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.restore(weights,PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha());
		System.setProperty("phantom.learning.train","true");
		final var eval=org.l2jmobius.gameserver.phantoms.learning.LearningPolicy.evaluation(org.l2jmobius.gameserver.phantoms.learning.LearningPolicy.Mode.SHADOW,1,model,4,SHA);
		check(eval.collecting(),"T10 exact evaluation receipts without a training writer");
		System.clearProperty("phantom.learning.train");
		reject(()->org.l2jmobius.gameserver.phantoms.learning.LearningPolicy.evaluation(org.l2jmobius.gameserver.phantoms.learning.LearningPolicy.Mode.ON,1,model,4,SHA),"T10 explicit TEST opt-in required");
		final var recovery=Files.createTempDirectory("learning005-exit-contract-").toRealPath();final String nonce="a".repeat(32);
		Files.createDirectories(recovery.resolve("attempts").resolve(nonce));Files.writeString(recovery.resolve("owner.txt"),"LEARNING002:fixture");
		Files.writeString(recovery.resolve("native-process.json"),"{\"nonce\":\""+nonce+"\",\"kind\":\"Native\",\"pid\":1}");
		Files.writeString(recovery.resolve("attempts").resolve(nonce).resolve("exit.json"),"{\"proof\":\"OWN_HELD_HANDLE_EXIT\",\"installationOwner\":\"LEARNING002:fixture\",\"nonce\":\""+"b".repeat(32)+"\",\"pid\":999}");
		reject(()->PhantomLearning005Native.verifyPriorExit(recovery,nonce),"foreign attempt exit must not recover START");
		final var healLegal=List.of("1401:1/1","1401:1/2");final String healKey=PhantomLearning005Evaluation.hash("heal-contract");
		final String healSource="{\"origin\":\"NATIVE_V3_EXACT_HEAL_HP\",\"eventKey\":\""+healKey+"\",\"actor\":1,\"actorEpoch\":1,\"target\":2,\"targetEpoch\":2,\"instance\":0,\"selected\":\"1401:1/2\",\"baseline\":\"1401:1/1\",\"legalSha\":\""+PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(healLegal))+"\",\"beforeHp\":900.0,\"afterHp\":1000.0,\"usefulHp\":100.0,\"actualMp\":5.0,\"elapsedNanos\":10,\"modelVersion\":2,\"modelSha\":\""+PhantomLearning005Evaluation.approved(Mode.PARTY_HEAL_V3).sha()+"\"}";
		final var healEffect=new Effect(healKey,1,1,2,2,0,"1401:1/2","1401:1/1",healLegal,Family.HEAL,100,5.0,PhantomLearning005Evaluation.hash(healSource),healSource);
		check(healEffect.changed(),"HEAL native decision difference retained");
		final String badHeal=healSource.replace("900.0","800.0");
		reject(()->new Effect(healKey,1,1,2,2,0,"1401:1/2","1401:1/1",healLegal,Family.HEAL,100,5.0,PhantomLearning005Evaluation.hash(badHeal),badHeal),"mutated native HEAL HP delta");
		System.out.println("LEARNING005_CONTRACT assertions="+checks+" PASS native=NOT_RUN fixtures=CONTRACT_ONLY");
	}
	static Arm arm(String pair, Side side, Outcome outcome, String gear, int incarnation, double time, boolean changed)
	{
		final var facts = new java.util.TreeMap<String,String>();
		for (String key : PhantomLearning005Evaluation.REQUIRED_FACTS) { facts.put(key,"same"); }
		facts.put("equipment",gear);
		final var event = effect(pair+side,incarnation,50+incarnation,changed);
		return new Arm(pair,side,Mode.SOLO_DAMAGE_V1,183,side==Side.STOCK?0:1,SHA,SHA,"scene",facts,List.of(new Incarnation(incarnation,incarnation)),
			new Incarnation(50+incarnation,incarnation),PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1),outcome,"NATIVE_KILL",time,3,
			Map.of("elapsedMillis",time,"exp",1000.0,"mp",5.0),List.of(event),List.of(new Kill(50+incarnation,incarnation,0,incarnation)),true);
	}
	private static Effect effect(String key,int actor,int target,boolean changed)
	{
		final String id=PhantomLearning005Evaluation.hash(key),selected=changed?"1339:1/target":"1230:28/target",baseline="1230:28/target";final var legal=List.of("1230:28/target","1339:1/target");
		final String json="{\"origin\":\"NATIVE_V1_EXACT_CAST_HP\",\"eventKey\":\""+id+"\",\"actor\":"+actor+",\"actorEpoch\":"+actor+",\"target\":"+target+",\"targetEpoch\":"+actor+",\"instance\":0,\"selected\":\""+selected+"\",\"baseline\":\""+baseline+"\",\"legalSha\":\""+PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(legal))+"\",\"actualDamage\":10.0,\"actualMp\":5.0,\"modelVersion\":1,\"modelSha\":\""+PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha()+"\"}";
		return new Effect(id,actor,actor,target,actor,0,selected,baseline,legal,Family.DAMAGE,10,5.0,PhantomLearning005Evaluation.hash(json),json);
	}
	private static void check(boolean condition,String message) { checks++; if(!condition){throw new AssertionError(message);} }
	private static void reject(Throwing action,String message) throws Exception
	{ boolean failed=false; try { action.run(); } catch(IllegalArgumentException|IllegalStateException|java.io.IOException failure){failed=true;} check(failed,message); }
	@FunctionalInterface private interface Throwing { void run() throws Exception; }
}
