/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Focused contracts and actual owned SQL ancestry readback. No native scene is fabricated here. */
public final class Learning006OutcomeChecks
{
	public static void main(String[] args) throws Exception
	{
		if(args.length==2&&args[0].equals("commit-r1")){commitR1(Path.of(args[1]));return;}
		if(args.length==2&&args[0].equals("before-r1")){beforeR1(Path.of(args[1]));return;}
		if(args.length==1&&args[0].equals("quota-r1")){quotaR1();return;}
		if (args.length == 1 && args[0].equals("contracts")) { contracts(); return; }
		if(args.length==2&&args[0].equals("audit")){audit(Path.of(args[1]));return;}
		if(args.length==2&&args[0].equals("terminal-sql"))
		{
			final var empty=new TacticalStoreV3(Path.of(args[1]));empty.initialize();empty.requireStopped();
			final String before=empty.optimizationParent().sha();
			check(empty.freezeTerminal()==null,"Empty terminal receipts fabricated a frozen fit");
			check(empty.optimizationParent().sha().equals(before)&&empty.nativeSampleCount()==509,"Empty terminal freeze changed optimizer/native credits");
			final var evaluation=TacticalTrainerV3.evaluate(Path.of(args[1]));
			check(evaluation.state().equals("COLLECTING")&&evaluation.metrics().contains("TERMINAL_OUTCOME_V1"),"006 trainer silently used legacy cast objective");
			System.out.println("LEARNING006_EMPTY_TERMINAL_SQL fit=NOT_CREATED optimizer=UNCHANGED native=NOT_RUN");return;
		}
		if (args.length != 2 || !args[0].equals("lineage")) { throw new IllegalArgumentException("Expected contracts or lineage and owned installation"); }
		final var store = new TacticalStoreV3(Path.of(args[1]));
		store.initialize(); store.requireStopped();
		final var parent = store.optimizationParent();
		check(parent.modelId() == 8 && parent.sha().equals("56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f"), "Checkpoint8 ancestry/SHA changed");
		check(parent.state().updates() == 769 && parent.state().completedEpochs() == 124 && parent.state().seed() == 183, "Checkpoint8 Adam progress changed");
		final var selected = store.candidate(8);
		check(selected.status().equals("DRAFT") && selected.sha().equals("ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016"), "Selected8 changed");
		check(!Arrays.equals(selected.model().bytes(), parent.state().model().bytes()), "Selected8 was substituted for terminal optimizer weights");
		check(store.accepted(2,"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc") != null, "Accepted2 changed");
		check(store.nativeSampleCount() == 509, "Historical native ledger changed");
		System.out.println("LEARNING006_LINEAGE fullParentTraversal=PASS model=8 updates=769 epochs=124 seed=183 nativeEvents=509 selectedAndTerminalDistinct=true optimizerSha=" + parent.sha() + " terminalWeightsSha=" + parent.state().model().sha256());
	}
	private static void audit(Path root) throws Exception
	{
		final var store=new TacticalStoreV3(root);store.initialize();store.requireStopped();
		final var parent=store.optimizationParent();
		final var guard=new TrainingStore(root);guard.initialize();
		final var receipts=new java.util.ArrayList<TerminalRewardReceiptV1>();
		long applications;
		try(var connection=guard.openTacticalConnection();var query=connection.createStatement())
		{
			try(var rows=query.executeQuery("SELECT receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards ORDER BY natural_key"))
			{while(rows.next()){final var receipt=TerminalRewardReceiptV1.restore(rows.getBytes(2),rows.getString(1));check(receipt.config().sha256().equals(store.terminalConfig().sha256()),"Cold reward config differs");receipts.add(receipt);
				System.out.println("{\"check\":\"RESTORED_STAGED_RECEIPT\",\"receiptSha\":\""+receipt.sha256()+"\",\"receipt\":"+receipt.json()+"}");}}
			try(var rows=query.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications")){rows.next();applications=rows.getLong(1);}
		}
		final long train=receipts.stream().filter(r->r.split().equals("TRAIN")).mapToLong(r->r.actions().size()).sum();
		final long tuning=receipts.stream().filter(r->r.split().equals("TUNING")).mapToLong(r->r.actions().size()).sum();
		final long audit=receipts.stream().filter(r->r.split().equals("AUDIT")).mapToLong(r->r.actions().size()).sum();
		if(train<16||tuning<8)
		{
			check(store.freezeTerminal()==null,"Insufficient native terminal split was fitted");
			check(applications==0&&parent.modelId()==8&&parent.sha().equals("56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f"),"Insufficient native data changed Adam8 or used markers");
		}
		System.out.println("{\"check\":\"LEARNING006_COLD_TERMINAL_SQL\",\"receipts\":"+receipts.size()+",\"trainActions\":"+train+",\"tuningActions\":"+tuning+",\"auditActions\":"+audit+",\"applications\":"+applications+",\"workingModel\":"+parent.modelId()+",\"optimizerSha\":\""+parent.sha()+"\",\"optimizerUpdates\":"+parent.state().updates()+",\"completedEpochs\":"+parent.state().completedEpochs()+",\"seed\":"+parent.state().seed()+",\"nativeSamples\":"+store.nativeSampleCount()+"}");
	}
	private static void commitR1(Path root) throws Exception
	{
		final var store=new TacticalStoreV3(root);store.initialize();store.requireStopped();
		final var parent=store.optimizationParent();final var model=store.candidate(parent.modelId());
		check(parent.modelId()>8&&parent.state().updates()>769&&parent.state().completedEpochs()>124&&model.epochs()>0&&model.status().equals("DRAFT"),"R1 committed optimizer/model progress missing");
		check(!model.sha().equals("fad52efe1757ae86b234314c8c15ece54b281fdcd3de370f8d6a215f3179709c")&&!model.sha().equals("ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016"),"R1 candidate retained old weights");
		final var frozen=store.terminalSnapshot(model.id());check(frozen.sufficient()&&frozen.datasetId().equals("outcome006r1-v1"),"R1 exact terminal snapshot/quota changed");
		final var guard=new TrainingStore(root);guard.initialize();long markers=0,used=0;
		try(var connection=guard.openTacticalConnection();var query=connection.createStatement())
		{
			try(var rows=query.executeQuery("SELECT parent_model_id,parent_optimizer_sha,parent_weights_sha FROM phantom_tactical_v3_optimizers WHERE model_id="+model.id()))
			{check(rows.next()&&rows.getLong(1)==8&&rows.getString(2).equals("56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f")&&rows.getString(3).equals("fad52efe1757ae86b234314c8c15ece54b281fdcd3de370f8d6a215f3179709c"),"R1 exact optimizer8 parent lost");}
			try(var rows=query.executeQuery("SELECT fit_role,terminal_reward_used,optimizer_sha FROM phantom_tactical_v3_terminal_applications WHERE model_id="+model.id()))
			{while(rows.next()){check(rows.getBoolean(2)==rows.getString(1).equals("TRAIN")&&rows.getString(3).equals(parent.sha()),"R1 held-out gradient/optimizer marker changed");markers++;if(rows.getBoolean(2)){used++;}}}
			check(markers==frozen.receipts().size()&&used==frozen.receipts().stream().filter(r->r.split().equals("TRAIN")).count(),"R1 atomic marker/model count differs");
			try(var rows=query.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications a JOIN phantom_tactical_v3_terminal_rewards r ON r.natural_key=a.natural_key WHERE r.dataset_id='outcome006-v1'")){rows.next();check(rows.getLong(1)==0,"Historical006 terminal credit was consumed by R1");}
		}
		for(String insert:List.of(
			"INSERT INTO phantom_tactical_v3_terminal_rewards(natural_key,goal_key,episode_id,owner_nonce,split,dataset_id,config_sha,receipt_sha,receipt_blob) SELECT natural_key,goal_key,episode_id,owner_nonce,split,CONCAT(dataset_id,'-repack'),config_sha,receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards WHERE dataset_id='outcome006r1-v1' LIMIT 1",
			"INSERT INTO phantom_tactical_v3_terminal_applications SELECT * FROM phantom_tactical_v3_terminal_applications WHERE model_id="+model.id()+" LIMIT 1"))
		{
			try(var connection=guard.openTacticalConnection();var query=connection.createStatement())
			{
				connection.setAutoCommit(false);boolean rejected=false;
				try{query.executeUpdate(insert);}catch(java.sql.SQLException duplicate){if(duplicate.getErrorCode()!=1062||!"23000".equals(duplicate.getSQLState())){throw duplicate;}rejected=true;}
				finally{connection.rollback();}
				check(rejected,"R1 duplicate reward/application got second credit");
			}
		}
		check(store.freezeTerminal()==null,"R1 applied receipts refroze for another gradient");
		check(store.optimizationParent().sha().equals(parent.sha())&&store.candidate(model.id()).sha().equals(model.sha()),"R1 duplicate controls changed model/Adam");
		check(store.accepted(2,"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc")!=null,"R1 accepted2 was changed");
		final String result="{\"check\":\"R1_COMMIT_COLD_DUPLICATE_SQL\",\"modelId\":"+model.id()+",\"modelSha\":\""+model.sha()+"\",\"optimizerSha\":\""+parent.sha()+"\",\"updates\":"+parent.state().updates()+",\"completedEpochs\":"+parent.state().completedEpochs()+",\"terminalDatasetSha\":\""+frozen.datasetSha()+"\",\"runId\":\""+frozen.runId()+"\",\"applicationMarkers\":"+markers+",\"trainReceiptsUsed\":"+used+",\"duplicateReward\":\"REJECTED_1062_ROLLBACK\",\"duplicateApplication\":\"REJECTED_1062_ROLLBACK\",\"secondFreeze\":\"NONE\",\"historical006Used\":false}";
		final Path proof=root.resolve("outcome006/campaigns/learning006r1/commit-cold-"+java.util.UUID.randomUUID()+".json");Files.writeString(proof,result+"\n",java.nio.file.StandardOpenOption.CREATE_NEW);System.out.println(result);
	}
	private static void beforeR1(Path root) throws Exception
	{
		final var store=new TacticalStoreV3(root);store.initialize();store.requireStopped();
		final var parent=store.optimizationParent();
		check(parent.modelId()==8&&parent.sha().equals("56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f")&&parent.state().updates()==769&&parent.state().completedEpochs()==124,"R1 exact Adam8 lineage changed before fit");
		check(parent.state().model().sha256().equals("fad52efe1757ae86b234314c8c15ece54b281fdcd3de370f8d6a215f3179709c"),"R1 selected8 substituted for terminal weights");
		check(store.candidate(8).sha().equals("ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016")&&store.accepted(2,"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc")!=null,"R1 historical model changed");
		final var guard=new TrainingStore(root);guard.initialize();final var closure=new StringBuilder();int events=0,oldReceipts=0;
		try(var connection=guard.openTacticalConnection();var sql=connection.createStatement())
		{
			try(var rows=sql.executeQuery("SELECT event_key,receipt_sha,receipt_blob FROM phantom_tactical_v3_events ORDER BY created_at,event_key LIMIT 536"))
			{while(rows.next()){check(TacticalObservationV3.hash(rows.getBytes(3)).equals(rows.getString(2)),"R1 historical event blob changed");closure.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');events++;}}
			try(var rows=sql.executeQuery("SELECT natural_key,receipt_sha,receipt_blob FROM phantom_tactical_v3_terminal_rewards WHERE dataset_id='outcome006-v1' ORDER BY natural_key"))
			{while(rows.next()){final var receipt=TerminalRewardReceiptV1.restore(rows.getBytes(3),rows.getString(2));check(receipt.naturalKey().equals(rows.getString(1)),"R1 old terminal identity changed");closure.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');oldReceipts++;}}
			try(var rows=sql.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_terminal_applications")){rows.next();check(rows.getLong(1)==0,"R1 terminal applications existed before first fit");}
		}
		check(events==536&&oldReceipts==9,"R1 preserved native536/terminal9 closure missing");
		final Path campaign=root.resolve("outcome006/campaigns/learning006r1");Files.createDirectories(campaign);
		check(campaign.equals(campaign.toRealPath()),"R1 physical SQL closure destination required");
		final Path pin=campaign.resolve("historical-sql.sha256");final String sha=TacticalObservationV3.hash(closure.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		if(Files.exists(pin)){check(pin.equals(pin.toRealPath())&&Files.readString(pin).equals(sha),"R1 historical SQL closure changed across resume");}
		else{Files.writeString(pin,sha,java.nio.file.StandardOpenOption.CREATE_NEW);}
		System.out.println("{\"check\":\"BEFORE_R1_SQL\",\"historicalEvents\":536,\"historicalTerminalReceipts\":9,\"applications\":0,\"optimizerUpdates\":769,\"completedEpochs\":124,\"terminalWeightsDistinct\":true,\"historicalClosureSha\":\""+sha+"\",\"nativeSamplesNow\":"+store.nativeSampleCount()+",\"recordedSceneIdentities\":"+store.recordedSceneIds().size()+"}");
	}
	private static void quotaR1()
	{
		final var missingSolo=new java.util.ArrayList<TerminalRewardReceiptV1>();
		final var complete=new java.util.ArrayList<TerminalRewardReceiptV1>();
		for(int i=1;i<=26;i++)
		{
			final String split=i<=16?"TRAIN":i<=24?"TUNING":"AUDIT";
			missingSolo.add(quotaReceipt(i,split,"PARTY"));
			complete.add(quotaReceipt(i,split,i<=2?"SOLO":"PARTY"));
		}
		check(!TerminalReplayV1.freeze("1".repeat(32),missingSolo).sufficient(),"R1 party-only TRAIN passed without two completed Solo episodes");
		check(TerminalReplayV1.freeze("2".repeat(32),complete).sufficient(),"R1 full native quota was rejected");
		System.out.println("LEARNING006R1_QUOTA partyOnly=REJECTED complete=PASS synthetic=CONTRACT_ONLY");
	}
	private static TerminalRewardReceiptV1 quotaReceipt(int i,String split,String scope)
	{
		final int actor=100+i,target=200+i;final long epoch=10+i,spawn=20+i,profile=1000+i;
		final String party=scope.equals("SOLO")?"solo:"+actor:"party:contract-"+i;
		final var facts=Map.of(Field.HP,Fact.known(.8),Field.MAX_HP,Fact.known(2000),Field.LEVEL,Fact.known(77));
		final var self=new Entity(Role.SELF,actor,epoch,0,94,facts,List.of(),List.of(),List.of(),List.of());
		final var enemy=new Entity(Role.ENEMY,target,spawn,0,0,facts,List.of(),List.of(),List.of(),List.of());
		final var observation=new TacticalObservationV3(7+i,150,profile,actor,epoch,party,List.of(self,enemy));
		final var action=new LegalActionV3(LegalActionV3.Family.DAMAGE,"Combat",profile,actor,epoch,7+i,target,spawn,party,1230,28,60,4000,900,false,true);
		final var alternative=new LegalActionV3(LegalActionV3.Family.DAMAGE,"Combat",profile,actor,epoch,7+i,target,spawn,party,1339,1,70,5000,900,false,true);
		final var attempt=new TacticalReceiptV3.Attempt(observation,List.of(action,alternative),action,alternative,2);
		final var effect=new TacticalReceiptV3(String.format("%064x",i),attempt,1600,1100,500,60,4_000_000_000L,TacticalReceiptV3.reward(500,2000,60,4_000_000_000L),new HealEvidenceV3.SourceOwner(40,"CONTRACT_FIXTURE"));
		final var row=new TacticalStoreV3.Row(split,"006r1.contract."+split+"."+i,effect);
		final var participant=new TacticalEpisodeV3.Actor(profile,actor,epoch,94,77,900,800,false,50000L,10000L);
		final var kill=new TacticalEpisodeV3.Kill(target,21798,spawn,0,actor,1);
		final var episode=new TacticalEpisodeV3(String.format("%064x",10000+i),"b".repeat(32),scope,100,3_000_000_100L,"NATIVE_KILL",List.of(participant),List.of(kill),List.of(row.eventKey()),500,0,60,0);
		final var boundary=new TerminalRewardReceiptV1.Boundary("a".repeat(64),"b".repeat(32),1,0,target,spawn,100,2_000_000_100L,3_000_000_100L,true);
		return TerminalRewardReceiptV1.create(boundary,episode,List.of(new TerminalRewardReceiptV1.TimedAction(row,0,200,300,400,500,false)),split,"outcome006r1-v1",TerminalRewardReceiptV1.Config.defaults());
	}
	private static void contracts()
	{
		final var config = TerminalRewardReceiptV1.Config.defaults();
		final var actor = new TacticalEpisodeV3.Actor(1000,100,10,94,77,900,800,false,50000L,10000L);
		final var second = new TacticalEpisodeV3.Actor(1001,101,11,94,77,900,800,false,10000L,1000L);
		final var kill = new TacticalEpisodeV3.Kill(200,21798,20,0,100,1);
		final var boundary = new TerminalRewardReceiptV1.Boundary("a".repeat(64),"b".repeat(32),1,0,200,20,100,2_000_000_100L,3_000_000_100L,true);
		final var row = action("TRAIN",LegalActionV3.Family.DAMAGE);
		final var timed = new TerminalRewardReceiptV1.TimedAction(row,0,200,300,400,500,false);
		final var episode = new TacticalEpisodeV3("d".repeat(64),"b".repeat(32),"PARTY",100,3_000_000_100L,"NATIVE_KILL",List.of(actor,second),List.of(kill,new TacticalEpisodeV3.Kill(200,21798,20,0,101,1)),List.of(row.eventKey()),500,0,60,0);
		final var receipt = TerminalRewardReceiptV1.create(boundary,episode,List.of(timed),"TRAIN","006.dataset",config);
		check(receipt.components().uniqueKills()==1 && receipt.reward()>0,"R01 canonical Party kill doubled or no positive reward");
		check(receipt.components().creditedExp()==60000 && receipt.components().creditedSp()==11000,"R04 credited deltas not summed");
		check(receipt.components().durationNanos()==2_000_000_000L,"R03 drain included in encounter time");
		check(receipt.json().contains("\"completeDeathCount\":null") && receipt.json().contains("\"wholePartyMp\":null"),"R02/R04 UNKNOWN converted to zero");
		check(receipt.json().contains("\"terminalRewardUsedForTraining\":false"),"R06 staged receipt claimed applied fit");
		final var dead = new TacticalEpisodeV3.Actor(1000,100,10,94,77,0,800,true,null,null);
		final var failed = new TacticalEpisodeV3("e".repeat(64),"b".repeat(32),"SOLO",100,3_000_000_100L,"NATIVE_ACTOR_DIED",List.of(dead),List.of(),List.of(row.eventKey()),500,0,60,1);
		check(TerminalRewardReceiptV1.create(boundary,failed,List.of(timed),"TRAIN","006.dataset",config).reward()==-1,"R02 death compensated by earnings");
		final var unknown = new TacticalEpisodeV3.Actor(1000,100,10,94,77,900,800,false,null,10000L);
		final var noEarnings = new TacticalEpisodeV3("f".repeat(64),"b".repeat(32),"SOLO",100,3_000_000_100L,"NATIVE_KILL",List.of(unknown),List.of(kill),List.of(row.eventKey()),500,0,60,0);
		reject(()->TerminalRewardReceiptV1.create(boundary,noEarnings,List.of(timed),"TRAIN","006.dataset",config),"R04 unknown earnings accepted");
		reject(()->TerminalRewardReceiptV1.create(boundary,episode,List.of(timed),"EVAL","006.dataset",config),"R07 EVAL leakage accepted");
		reject(()->TerminalRewardReceiptV1.create(boundary,episode,List.of(timed,timed),"TRAIN","006.dataset",config),"R06 duplicate native action accepted");
		final var late = new TerminalRewardReceiptV1.TimedAction(row,0,2_000_000_200L,2_000_000_300L,400,500,false);
		reject(()->TerminalRewardReceiptV1.create(boundary,episode,List.of(late),"TRAIN","006.dataset",config),"R03 late effect accepted");
		final var falseLethal = new TerminalRewardReceiptV1.TimedAction(row,0,200,300,400,500,true);
		reject(()->TerminalRewardReceiptV1.create(boundary,episode,List.of(falseLethal),"TRAIN","006.dataset",config),"R03 lethal flag without overlapping terminal native write accepted");
		final var healRow=action("TRAIN",LegalActionV3.Family.HEAL);
		final var paidHeal=new TerminalRewardReceiptV1.TimedAction(healRow,0,200,300,400,500,false);
		final var healed=TerminalRewardReceiptV1.create(boundary,episode,List.of(paidHeal),"TRAIN","006.dataset",config);
		check(healed.components().usefulHeal()==500 && healed.components().observedPaidMp()==60,"R05 useful paid HEAL lost");
		reject(()->TerminalRewardReceiptV1.create(boundary,episode,List.of(),"TRAIN","006.dataset",config),"R05 unexecuted/cancelled actions got terminal targets");
		check(config.sha256().equals("3d1ae88c1e8cfc2da6e7c465254b5e8a561373c5fc047b8d0947f63c9f48f8ae"),"Reward configuration differs from predeclared exact bytes");
		final var repacked = TerminalRewardReceiptV1.create(boundary,episode,List.of(timed),"TRAIN","006.other-dataset",config);
		check(receipt.naturalKey().equals(repacked.naturalKey()) && !receipt.sha256().equals(repacked.sha256()),"R06 dedupe identity depends on dataset wrapper");
		final var restored=TerminalRewardReceiptV1.restore(receipt.bytes(),receipt.sha256());
		check(restored.json().equals(receipt.json())&&Arrays.equals(restored.bytes(),receipt.bytes()),"R08 terminal receipt cold codec changed provenance");
		final byte[] corrupt=receipt.bytes();corrupt[corrupt.length-1]^=1;
		reject(()->TerminalRewardReceiptV1.restore(corrupt,receipt.sha256()),"R08 corrupt persisted receipt accepted");
		final var frozen=TerminalReplayV1.freeze("1".repeat(32),List.of(receipt));
		check(frozen.examples("TRAIN").getFirst().reward()==receipt.reward()&&receipt.reward()!=row.receipt().reward(),"R07 terminal target replaced by historical cast reward");
		check(Arrays.equals(frozen.bytes(),TerminalReplayV1.restore(frozen.runId(),frozen.bytes(),frozen.datasetSha()).bytes()),"R08 frozen terminal dataset cold codec changed");
		reject(()->TerminalReplayV1.freeze("1".repeat(32),List.of(receipt,repacked)),"R06 native credit repackaged into two dataset receipts");
		System.out.println("LEARNING006_CONTRACT R01-R07 PASS native=NOT_RUN");
	}
	private static TacticalStoreV3.Row action(String split, LegalActionV3.Family family)
	{
		final var facts=Map.of(Field.HP,Fact.known(.8),Field.MAX_HP,Fact.known(2000),Field.LEVEL,Fact.known(77));
		final var self=new Entity(Role.SELF,100,10,0,94,facts,List.of(),List.of(),List.of(),List.of());
		final boolean heal=family==LegalActionV3.Family.HEAL;
		final int target=heal?101:200;final long targetEpoch=heal?11:20;
		final var enemy=new Entity(heal?Role.ALLY:Role.ENEMY,target,targetEpoch,0,heal?94:0,facts,List.of(),List.of(),List.of(),List.of());
		final var observation=new TacticalObservationV3(7,150,1000,100,10,"solo:100",List.of(self,enemy));
		final var a=new LegalActionV3(family,heal?"PartyTactics":"Combat",1000,100,10,7,target,targetEpoch,"solo:100",1230,28,60,4000,900,false,true);
		final var b=new LegalActionV3(family,heal?"PartyTactics":"Combat",1000,100,10,7,target,targetEpoch,"solo:100",1339,1,70,5000,900,false,true);
		final var attempt=new TacticalReceiptV3.Attempt(observation,List.of(a,b),a,b,2);
		final var effect=new TacticalReceiptV3("c".repeat(64),attempt,heal?600:1600,1100,500,60,4_000_000_000L,TacticalReceiptV3.reward(500,2000,60,4_000_000_000L),new HealEvidenceV3.SourceOwner(40,"CONTRACT_FIXTURE"));
		return new TacticalStoreV3.Row(split,"006.damage.solo",effect);
	}
	private static void reject(Runnable action, String message) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
