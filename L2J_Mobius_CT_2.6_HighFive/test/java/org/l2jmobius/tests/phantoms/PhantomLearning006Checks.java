/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Focused outcome006 contracts; existing public owner functions, no server/SQL bootstrap. */
public final class PhantomLearning006Checks
{
	private static final String BASELINE = "246ac12cd718e698b18e6ab2b338622b24d505f75ba492eb1cd348bca2e335cd";
	public static void main(String[] args) throws Exception
	{
		if(args.length==1&&args[0].equals("clock-r1"))
		{
			final Path root=Files.createTempDirectory("outcome-clock-r1-").toRealPath();
			final var now=new java.util.concurrent.atomic.AtomicLong();
			try(var clock=new PhantomLearning004Native.OutcomeClock(root,java.time.Duration.ofMinutes(1).toNanos(),now::get))
			{now.addAndGet(java.time.Duration.ofMinutes(2).toNanos());check(!clock.expired(),"Fresh clock prematurely exhausted");}
			try(var clock=new PhantomLearning004Native.OutcomeClock(root,java.time.Duration.ofMinutes(1).toNanos(),now::get))
			{check(clock.spentNanos()==java.time.Duration.ofMinutes(4).toNanos(),"Pause/resume lost elapsed/bootstrap");now.addAndGet(java.time.Duration.ofMinutes(86).toNanos());check(clock.expired(),"Elapsed90min still admitted another slot");}
			final Path crash=Files.createTempDirectory("outcome-clock-crash-r1-").toRealPath();
			new PhantomLearning004Native.OutcomeClock(crash,1,now::get);
			try(var recovered=new PhantomLearning004Native.OutcomeClock(crash,0,now::get)){check(recovered.expired(),"Unclosed crashed segment refunded unproven time");}
			System.out.println("LEARNING006R1_CLOCK pauseResume=RETAINED bootstrap=COUNTED limit=ENFORCED crash=CONSERVATIVE");return;
		}
		if(args.length==1&&args[0].equals("heal-totals-r1"))
		{
			final var admitted=reviewHeal(1,100,20);final var late=reviewHeal(2,200,40);
			final var action=admitted.attempt().selected();final var legal=List.of("1217:1/101","1218:1/101");
			final String json="{\"origin\":\"NATIVE_V3_EXACT_HEAL_HP\",\"eventKey\":\""+admitted.eventKey()+"\",\"actor\":100,\"actorEpoch\":10,\"target\":101,\"targetEpoch\":11,\"instance\":0,\"selected\":\"1217:1/101\",\"baseline\":\"1218:1/101\",\"legalSha\":\""+PhantomLearning005Evaluation.hash(PhantomLearning005Evaluation.arrayJson(legal))+"\",\"modelSha\":\"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc\",\"modelVersion\":2,\"beforeHp\":600,\"afterHp\":700,\"usefulHp\":100,\"actualMp\":20,\"elapsedNanos\":4000000000}";
			final var effect=new PhantomLearning005Evaluation.Effect(admitted.eventKey(),100,10,101,11,0,"1217:1/101","1218:1/101",legal,PhantomLearning005Evaluation.Family.HEAL,100,20.0,PhantomLearning005Evaluation.hash(json),json);
			final var metrics=new java.util.TreeMap<String,Double>();
			PhantomLearning005Native.healMetrics(metrics,List.of(effect),List.of(admitted,late),true);
			check(metrics.get("observedUsefulHeal")==100&&metrics.get("observedHealMpDebit")==20,"Late drain HEAL changed encounter outcome totals");
			check(metrics.get("rawObservedUsefulHealIncludingDrain")==300&&metrics.get("rawObservedHealMpIncludingDrain")==60,"Late HEAL raw diagnostics lost");
			System.out.println("LEARNING006R1_HEAL_TOTALS admittedHp=100 admittedMp=20 rawHp=300 rawMp=60 late=DIAGNOSTIC_ONLY");return;
		}
		if(args.length==2&&args[0].equals("routing-r1"))
		{
			final String template=Files.readString(Path.of(args[1]).resolve("tools/phantom-runtime/training/Outcome006.ini"));
			final Path config=Files.createTempFile("outcome-route-r1-",".ini");
			for(String phase:List.of("COLLECT","PROOF","EVALUATE","CONTROL"))
			{
				Files.writeString(config,template.replace("outcome006Phase=COLLECT","outcome006Phase="+phase));
				check(PhantomTrainingNative.evaluationOnly(org.l2jmobius.gameserver.phantoms.learning.TrainingConfig.load(config))!=phase.equals("COLLECT"),"R1 evaluation phase entered train collector route");
			}
			Files.writeString(config,template.replace("outcome006Phase=COLLECT","outcome006Phase=PROOF")+"\noutcome006EvaluationCampaign=proof-r2\n");
			check(PhantomLearning005Native.evaluationName(TrainingConfig.load(config)).equals("proof-r2"),"R1 fresh evaluator namespace lost");
			Files.writeString(config,template.replace("outcome006Phase=COLLECT","outcome006Phase=PROOF")+"\noutcome006EvaluationCampaign=../collection\n");
			reject(()->{try{PhantomLearning005Native.evaluationName(TrainingConfig.load(config));}catch(java.io.IOException e){throw new RuntimeException(e);}},"R1 evaluator path traversal admitted");
			System.out.println("LEARNING006R1_ROUTING collect=TRAIN proofAuditControl=EVALUATION_ONLY");return;
		}
		if(args.length==1&&args[0].equals("authority-r1"))
		{
			final var authority=new PhantomLearning005Evaluation.DraftAuthority(9,"a".repeat(64),"b".repeat(64),"c".repeat(64),"1".repeat(32));
			final var pin=new PhantomLearning005Evaluation.Pin(PhantomLearning005Evaluation.Mode.SOLO_DAMAGE_V1,9,"DRAFT","a".repeat(64),org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.SCHEMA,3,authority);
			PhantomLearning005Evaluation.admitDraft(pin,authority);
			reject(()->PhantomLearning005Evaluation.admit(pin),"R1 DRAFT leaked into accepted-only005");
			reject(()->PhantomLearning005Evaluation.admitDraft(new PhantomLearning005Evaluation.Pin(pin.mode(),10,"DRAFT",pin.sha(),pin.schema(),3,authority),authority),"R1 wrong model ID admitted");
			final var arm=new PhantomLearning005Evaluation.Arm("r1-proof",PhantomLearning005Evaluation.Side.STOCK,pin.mode(),183,0,"d".repeat(64),"e".repeat(64),"scene",java.util.Map.of(),List.of(),null,pin,PhantomLearning005Evaluation.Outcome.SKIP,"CONTRACT_FIXTURE",0,0,java.util.Map.of(),List.of(),List.of(),true);
			final Path root=Files.createTempDirectory("draft-authority-r1-").toRealPath();
			try(var ledger=new PhantomLearning005Ledger(root.resolve("legacy"),"legacy","d".repeat(64)))
			{boolean refused=false;try{ledger.put(arm);}catch(java.io.IOException expected){refused=true;}check(refused,"R1 DRAFT written into legacy005 ledger");}
			try(var ledger=new PhantomLearning005Ledger(root.resolve("r1"),"r1","d".repeat(64),"e".repeat(64),authority)){check(ledger.put(arm),"R1 authorized arm rejected");}
			try(var ledger=new PhantomLearning005Ledger(root.resolve("r1"),"r1","d".repeat(64),"e".repeat(64),authority)){check(ledger.arms().size()==1&&ledger.arms().getFirst().json().contains("EVAL006R1"),"R1 cold authority lost");}
			System.out.println("LEARNING006R1_AUTHORITY exact=PASS wrongId=REJECTED legacy005=REJECTED cold=PASS synthetic=CONTRACT_ONLY");return;
		}
		if(args.length==1&&args[0].equals("campaign-r1"))
		{
			final Path root=Files.createTempDirectory("outcome006r1-contract-").toRealPath();
			final Path legacy=PhantomLearning004Native.outcomeCampaign(root,"");
			Files.writeString(legacy.resolve("native.complete"),"historical");
			final Path fresh=PhantomLearning004Native.outcomeCampaign(root,"learning006r1");
			check(!fresh.equals(legacy)&&!Files.exists(fresh.resolve("native.complete")),"R1 reused historical complete budget");
			check(PhantomLearning004Native.reserveOutcomeSlot(fresh,0,"first"),"R1 first reservation rejected");
			check(!PhantomLearning004Native.reserveOutcomeSlot(fresh,0,"retry"),"R1 consumed slot retried");
			check(Files.readString(fresh.resolve("slot-0/reserved.json")).equals("first"),"R1 rewrote immutable reservation");
			check(Files.readString(legacy.resolve("native.complete")).equals("historical"),"R1 changed historical marker");
			reject(()->{try{PhantomLearning004Native.outcomeCampaign(root,"../collection");}catch(java.io.IOException e){throw new RuntimeException(e);}},"Campaign path traversal admitted");
			System.out.println("LEARNING006R1_CAMPAIGN legacy=UNCHANGED fresh=SEPARATE consumed=NOT_RETRIED traversal=REJECTED");return;
		}
		if(args.length==1&&args[0].equals("boundaries"))
		{
			final var window=new PhantomLearning001ShadowNative.Runtime.OutcomeDeaths006();
			window.observe(1,100);window.observe(2,90);
			final var first=window.snapshot(120,true);
			check(first.first()==90&&first.firstDeath(2)&&!first.firstDeath(1),"First terminal used CAS winner or included later actor death");
			window.observe(3,80);check(window.snapshot(200,false).equals(first),"Drain changed frozen terminal facts");
			final var timeout=new PhantomLearning001ShadowNative.Runtime.OutcomeDeaths006();timeout.observe(2,110);
			check(timeout.snapshot(100,true).first()==0,"Postdeadline kill promoted timeout");
			timeout.observe(2,90);check(timeout.snapshot(200,false).first()==0,"Late publication changed closed timeout");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_LEGAL_TARGET:test",true,false,false).startsWith("NO_LEGAL_TARGET"),"HEAL substituted for encounter admission");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_EXACT_NATIVE_EVIDENCE",false,false,false).startsWith("NO_LEGAL_TARGET"),"Unadmitted encounter became timeout");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("NO_EXACT_NATIVE_EVIDENCE",true,false,false).equals("TIMEOUT"),"Admitted timeout lost");
			check(PhantomLearning001ShadowNative.Runtime.outcomeReason("REQUESTED_DRAIN",true,true,false).equals("REQUESTED_DRAIN"),"STOP became positive training credit");
			System.out.println("LEARNING006_BOUNDARIES noAdmission=EXCLUDED noLegalTarget=EXCLUDED stop=EXCLUDED admittedTimeout=TIMEOUT");return;
		}
		if(args.length==2&&args[0].equals("schema")){schema(Path.of(args[1]).toRealPath());return;}
		if (args.length != 2 || !args[0].equals("baseline")) { throw new IllegalArgumentException("Expected baseline and module root"); }
		final Path module = Path.of(args[1]).toRealPath();
		final String owner = module.resolve("tools/phantom-runtime/training/TrainingOwner.ps1").toString().replace("'", "''");
		final String prefix = ". '" + owner + "'; $ErrorActionPreference='Stop'; ";
		check(run(prefix + "Get-TrainProtectedPin @{configProfile='OUTCOME006';outcome006ProtectedSha='" + BASELINE + "'}").equalsIgnoreCase(BASELINE), "R12 new exact baseline pin was not selected");
		check(run(prefix + "Get-TrainProtectedPin @{configProfile='V3'}").equalsIgnoreCase("9777ab48bc8d14bdee77d4aca8e7e650cb099f03ca33fea108d5cdb941c59dcd"), "R12 legacy V3 baseline changed");
		check(run(prefix + "try { Get-TrainProtectedPin @{configProfile='OUTCOME006';outcome006ProtectedSha=('0'*64)}; exit 0 } catch { Write-Output 'REJECTED' }").equals("REJECTED"), "R12 wrong baseline pin accepted");
		System.out.println("LEARNING006_BASELINE R12 PASS new=EXACT legacy=UNCHANGED wrongPin=REJECTED");
	}
	private static void schema(Path module) throws Exception
	{
		final Path fixture=module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-006-PVE-OUTCOME/fixtures/schema-before-terminal.sql");
		final Path script=module.resolve("dist/db_installer/sql/game/phantom_tactical_v3.sql");
		final var before=StrictSqlScriptRunner.splitStatements(fixture,Files.readString(fixture));
		final var after=StrictSqlScriptRunner.splitStatements(script,Files.readString(script));
		check(PhantomLearning004Schema.terminalDelta(before,after).size()==2,"R06 exact two terminal tables missing");
		final var changed=new ArrayList<>(after);changed.set(0,changed.getFirst().replace("MEDIUMBLOB","LONGBLOB"));
		reject(()->PhantomLearning004Schema.terminalDelta(before,changed),"Existing table schema mutation accepted");
		final var dropped=new ArrayList<>(after);dropped.add("DROP TABLE phantom_tactical_v3_events");
		reject(()->PhantomLearning004Schema.terminalDelta(before,dropped),"Extra terminal migration DDL accepted");
		System.out.println("LEARNING006_SCHEMA exactAdditive2=PASS changedOld=REJECTED extraDDL=REJECTED DB_CONNECTIONS=0");
	}
	private static TacticalReceiptV3 reviewHeal(int id,double hp,double mp)
	{
		final var facts=java.util.Map.of(Field.HP,Fact.known(.3),Field.MAX_HP,Fact.known(2000),Field.LEVEL,Fact.known(77));
		final var self=new Entity(Role.SELF,100,10,0,105,facts,List.of(),List.of(),List.of(),List.of());
		final var target=new Entity(Role.ALLY,101,11,0,94,facts,List.of(),List.of(),List.of(),List.of());
		final var observation=new TacticalObservationV3(7,150,1000,100,10,"party:review",List.of(self,target));
		final var a=new LegalActionV3(LegalActionV3.Family.HEAL,"PartyTactics",1000,100,10,7,101,11,"party:review",1217,1,mp,4000,900,false,true);
		final var b=new LegalActionV3(LegalActionV3.Family.HEAL,"PartyTactics",1000,100,10,7,101,11,"party:review",1218,1,mp,4000,900,false,true);
		return new TacticalReceiptV3(String.format("%064x",id),new TacticalReceiptV3.Attempt(observation,List.of(a,b),a,b,2),600,600+hp,hp,mp,4_000_000_000L,TacticalReceiptV3.reward(hp,2000,mp,4_000_000_000L),new HealEvidenceV3.SourceOwner(40,"CONTRACT_FIXTURE"));
	}
	private static void reject(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError(message);}
	private static String run(String command) throws Exception
	{
		final var process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)).redirectErrorStream(true).start();
		final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
		if (process.waitFor() != 0) { throw new AssertionError("Owner contract process failed: " + output); }
		return output;
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
}
