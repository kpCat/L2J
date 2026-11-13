/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.*;

/** Focused007 contracts. Controlled examples are mathematics, never native game evidence. */
public final class Learning007Checks
{
	public static void main(String[] args) throws Exception
	{
		if(args.length==2&&Set.of("fault-readback","sql-inspect","offline").contains(args[0])){ownedSql(args[0],java.nio.file.Path.of(args[1]));return;}
		if(args.length!=1){throw new IllegalArgumentException("Expected selection or mathematics");}
		switch(args[0])
		{
			case "selection" -> selection();
			case "mathematics" -> mathematics();
			case "objective" -> objective();
			case "authority" -> authority();
			default -> throw new IllegalArgumentException("Unknown007 focused group");
		}
	}
	private static void selection() throws Exception
	{
		var module=java.nio.file.Path.of(System.getProperty("phantom.module.root","L2J_Mobius_CT_2.6_HighFive"));
		var shipped=TrainingConfig.load(module.resolve("tools/phantom-runtime/training/Exploration007.ini"));
		check(shipped.text("exploration007Enabled","").equals("false")&&shipped.text("outcome006Enabled","").equals("false"),"Shipped exploration is not OFF");
		check(shipped.integer("decisionCooldownMillis",0,1000,60000)==2000&&shipped.text("v3Mode","").equals("SHADOW"),"Agreed policy gates changed");
		TrainExplorationV1.validatePermit(true,true,"COLLECT","learning007-causal-v1","TRAIN");
		for(String split:List.of("TUNING","AUDIT","EVAL","MAIN","PLAY"))
		{reject(()->TrainExplorationV1.validatePermit(true,true,"COLLECT","learning007-causal-v1",split),"Exploration leaked to "+split);}
		reject(()->TrainExplorationV1.validatePermit(false,true,"COLLECT","learning007-causal-v1","TRAIN"),"Nonisolated exploration");
		reject(()->TrainExplorationV1.validatePermit(true,false,"COLLECT","learning007-causal-v1","TRAIN"),"Opt-in missing");
		reject(()->TrainExplorationV1.validatePermit(true,true,"EVALUATE","learning007-causal-v1","TRAIN"),"EVAL phase exploration");
		reject(()->TrainExplorationV1.validatePermit(true,true,"COLLECT","learning006r1","TRAIN"),"Foreign campaign");
		int stock=0;final int[] counts=new int[3];
		for(int i=0;i<800;i++)
		{
			var pick=TrainExplorationV1.pick(3,stock,(i+.5)/800.0,true);counts[pick.index()]++;
			check(pick.propensity()==(pick.index()==stock?.75:.125),"Wrong conditional propensity");
		}
		check(Arrays.equals(counts,new int[]{600,100,100}),"Epsilon/uniform alternative distribution changed");
		var control=TrainExplorationV1.pick(3,2,.99,false);check(control.index()==2&&control.propensity()==1,"A control is exploratory");
		reject(()->TrainExplorationV1.pick(1,0,.9,true),"Single candidate exploration");
		System.out.println("LEARNING007_SELECTION TRAIN_ONLY epsilon=.25 conditionalPropensity=EXACT native=NOT_RUN");
	}
	private static void mathematics()
	{
		var parent=TacticalModelV3.startOptimization(TacticalModelV3.initialized(183),183);
		var train=new ArrayList<TacticalModelV3.WeightedExample>();var tuning=new ArrayList<TacticalModelV3.Example>();
		for(int i=0;i<32;i++)
		{
			float[] state=new float[128],action=new float[64];state[0]=.4f+(i%4)*.05f;action[0]=(i&1)==0?-.8f:.8f;
			train.add(new TacticalModelV3.WeightedExample(new TacticalModelV3.Example(state,action,action[0]>0?.7f:-.7f),action[0]>0?2:2.0/3));
		}
		for(int i=0;i<8;i++)
		{
			float[] state=new float[128],action=new float[64];state[0]=.43f+(i%4)*.04f;action[0]=(i&1)==0?-.8f:.8f;
			tuning.add(new TacticalModelV3.Example(state,action,action[0]>0?.7f:-.7f));
		}
		var trained=TacticalModelV3.continueTrainingWeighted(parent,train,tuning);
		float[] independent=new float[128],low=new float[64],high=new float[64];independent[0]=.49f;low[0]=-.8f;high[0]=.8f;
		float before=parent.model().score(independent,high)-parent.model().score(independent,low);
		float after=trained.fit().model().score(independent,high)-trained.fit().model().score(independent,low);
		check(after>.025f&&after>before+.025f,"Training did not distinguish observed actions on independent state");
		check(trained.continuation().updates()>0&&trained.continuation().completedEpochs()>0,"Adam continuation missing");
		var restored=TacticalModelV3.Optimizer.restore(trained.continuation().bytes(),trained.continuation().sha256());
		check(Arrays.equals(restored.bytes(),trained.continuation().bytes()),"Weighted optimizer codec changed");
		check(parent.updates()==0&&parent.completedEpochs()==0,"Parent mutated");
		var ones=train.stream().map(w->new TacticalModelV3.WeightedExample(w.example(),1)).toList();
		var unit=TacticalModelV3.continueTrainingWeighted(parent,ones,tuning);
		var legacy=TacticalModelV3.continueTraining(parent,train.stream().map(TacticalModelV3.WeightedExample::example).toList(),tuning);
		check(Arrays.equals(unit.continuation().bytes(),legacy.continuation().bytes())&&Arrays.equals(unit.fit().model().bytes(),legacy.fit().model().bytes()),"Unit weights changed historical gradient/Adam contract");
		check(!Arrays.equals(trained.continuation().bytes(),unit.continuation().bytes()),"Propensity weights had no gradient effect");
		System.out.println("LEARNING007_MATH actionGapBefore="+before+" actionGapAfter="+after+" heldOutOrder=CHANGED optimizerCold=EXACT native=NOT_RUN");
	}
	private static void objective()
	{
		var samples=new ArrayList<ActionAdvantageV1.Sample>();
		for(int i=0;i<8;i++){samples.add(sample(i,"TRAIN",false));}
		for(int i=8;i<24;i++){samples.add(sample(i,"TRAIN",true));}
		for(int i=24;i<32;i++){samples.add(sample(i,"TUNING",false));}
		for(int i=32;i<40;i++){samples.add(sample(i,"AUDIT",false));}
		var frozen=ActionAdvantageV1.freeze("1".repeat(32),samples);
		check(frozen.weightedExamples().size()==16,"A baseline or held-out received gradient");
		check(frozen.examples("TUNING").size()==8&&frozen.examples("AUDIT").size()==8,"Independent held-out targets missing");
		check(frozen.targets().stream().filter(t->t.sample().selection().experimental()).allMatch(t->t.advantage()>.025f&&t.weight()==2),"Executed relative utility/propensity not learned");
		check(frozen.targets().stream().filter(t->t.sample().selection().experimental()).anyMatch(t->t.baselineCount()==3),"Own matched A leaked into baseline");
		check(frozen.sufficient(),"Paid alternatives with both scopes were not admitted");
		var coinOnly=new ArrayList<ActionAdvantageV1.Sample>();
		for(int i=0;i<40;i++){coinOnly.add(sample(i,i<24?"TRAIN":i<32?"TUNING":"AUDIT",i>=8&&i<24,false));}
		var noAlternative=ActionAdvantageV1.freeze("4".repeat(32),coinOnly);
		check(noAlternative.weightedExamples().size()==16&&noAlternative.examples("TUNING").size()==8&&noAlternative.examples("AUDIT").size()==8,"Stock-only negative fixture lost sufficient counts");
		check(!noAlternative.sufficient(),"Stock-only paid choices admitted to advantage fit");
		coinOnly.set(8,sample(8,"TRAIN",true,true));
		check(ActionAdvantageV1.freeze("5".repeat(32),coinOnly).sufficient(),"One valid paid TRAIN B alternative was not admitted");
		var restored=ActionAdvantageV1.restore(frozen.runId(),frozen.bytes(),frozen.datasetSha());
		check(Arrays.equals(frozen.bytes(),restored.bytes()),"Advantage frozen codec not exact");
		var noControls=ActionAdvantageV1.freeze("2".repeat(32),samples.stream().filter(s->!s.selection().split().equals("TRAIN")||s.selection().experimental()).toList());
		check(noControls.weightedExamples().isEmpty(),"Held-out substituted missing TRAIN baseline");
		var duplicate=new ArrayList<>(samples);duplicate.add(samples.getFirst());
		reject(()->ActionAdvantageV1.freeze("3".repeat(32),duplicate),"Duplicate native episode/action credit");
		reject(()->new ActionAdvantageV1.Sample(samples.get(8).selection(),samples.get(9).terminal()),"Foreign effect admitted");
		System.out.println("LEARNING007_OBJECTIVE executedOnly=PASS leaveBlockOut=PASS propensity=PASS splitIsolation=PASS coldCodec=EXACT native=NOT_RUN");
	}
	private static ActionAdvantageV1.Sample sample(int i,String split,boolean experimental)
	{return sample(i,split,experimental,experimental);}
	private static ActionAdvantageV1.Sample sample(int i,String split,boolean experimental,boolean chooseAlternative)
	{
		int actor=100+i,target=1000+i;long profile=10000+i,epoch=100000+i,spawn=200000+i;
		var facts=Map.of(TacticalObservationV3.Field.HP,TacticalObservationV3.Fact.known(.8),TacticalObservationV3.Field.MAX_HP,TacticalObservationV3.Fact.known(2000),TacticalObservationV3.Field.LEVEL,TacticalObservationV3.Fact.known(77));
		var self=new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF,actor,epoch,0,94,facts,List.of(),List.of(),List.of(),List.of());
		var enemy=new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY,target,spawn,0,0,facts,List.of(),List.of(),List.of(),List.of());
		var observation=new TacticalObservationV3(1,150,profile,actor,epoch,"solo:"+actor,List.of(self,enemy));
		var stock=new LegalActionV3(LegalActionV3.Family.DAMAGE,"Combat",profile,actor,epoch,1,target,spawn,observation.partyIdentity(),1230,28,60,4000,900,false,true);
		var alternative=new LegalActionV3(LegalActionV3.Family.DAMAGE,"Combat",profile,actor,epoch,1,target,spawn,observation.partyIdentity(),1339,1,70,5000,900,false,true);
		var selected=chooseAlternative?alternative:stock;double hp=chooseAlternative?900:500,mp=selected.mpCost();
		var attempt=new TacticalReceiptV3.Attempt(observation,List.of(stock,alternative),selected,stock,9);
		var receipt=new TacticalReceiptV3(String.format("%064x",i+1),attempt,2000,2000-hp,hp,mp,1000000000L,TacticalReceiptV3.reward(hp,2000,mp,1000000000L),new HealEvidenceV3.SourceOwner(1,"Combat/test/"+i));
		var row=new TacticalStoreV3.Row(split,"learning007|"+split+"|spot=fixture|"+i,receipt);
		String id=String.format("%064x",1000+i);
		var actorResult=new TacticalEpisodeV3.Actor(profile,actor,epoch,94,77,900,800,false,50000L,10000L);
		var kill=new TacticalEpisodeV3.Kill(target,21798,spawn,0,actor,1);
		var episode=new TacticalEpisodeV3(id,"b".repeat(32),i%2==0?"SOLO":"PARTY",100,3000000100L,"NATIVE_KILL",List.of(actorResult),List.of(kill),List.of(receipt.eventKey()),hp,0,mp,0);
		var boundary=new TerminalRewardReceiptV1.Boundary("a".repeat(64),"b".repeat(32),1,0,target,spawn,100,2000000100L,3000000100L,true);
		var terminal=TerminalRewardReceiptV1.create(boundary,episode,List.of(new TerminalRewardReceiptV1.TimedAction(row,0,200,300,400,500,false)),split,TrainExplorationV1.DATASET,TerminalRewardReceiptV1.Config.defaults());
		String block="block"+(experimental?(i-8)%8:i%8);
		var selection=new TrainExplorationV1.Selection(id,block,(i%2==0?"c":"d").repeat(64),split,experimental,observation,List.of(stock,alternative),stock,selected,experimental?(chooseAlternative?.25:.75):1,experimental?(chooseAlternative?.9:.25):0,experimental?(chooseAlternative?"EXPLORATION_ALTERNATIVE":"EXPLORATION_COIN_STOCK"):"CONTROL_STOCK","",9,TrainExplorationV1.TEACHER_SHA);
		return new ActionAdvantageV1.Sample(selection,terminal);
	}
	private static void authority() throws Exception
	{
		var authority=new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.DraftAuthority(11,"a".repeat(64),"b".repeat(64),"c".repeat(64),"1".repeat(32),"EVAL007");
		var legacy=new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.DraftAuthority(11,"a".repeat(64),"b".repeat(64),"c".repeat(64),"1".repeat(32));
		var mode=org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Mode.SOLO_DAMAGE_V1;
		var pin=new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Pin(mode,11,"DRAFT","a".repeat(64),TacticalObservationV3.SCHEMA,3,authority);
		var arm=new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Arm("007-proof",org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Side.STOCK,mode,183,0,"d".repeat(64),"e".repeat(64),"fixture",Map.of(),List.of(),null,pin,org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Outcome.SKIP,"MATH_ONLY",0,0,Map.of(),List.of(),List.of(),true);
		var root=java.nio.file.Files.createTempDirectory("eval007-contract-").toRealPath();
		try(var ledger=new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root,"eval007","d".repeat(64),"e".repeat(64),authority)){check(ledger.put(arm),"007 arm rejected");}
		try(var ledger=new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root,"eval007","d".repeat(64),"e".repeat(64),authority)){check(ledger.arms().size()==1&&ledger.arms().getFirst().json().contains("\"split\":\"EVAL007\""),"007 namespace/codec lost cold");}
		boolean refused=false;try(var ignored=new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root,"eval007","d".repeat(64),"e".repeat(64),legacy)){ }catch(java.io.IOException expected){refused=true;}
		check(refused,"007 frames admitted as historical006R1");
		System.out.println("LEARNING007_AUTHORITY typedNamespace=EVAL007 coldFrame=EXACT legacyIsolation=PASS native=NOT_RUN");
	}
	public record Offline(boolean solo,boolean party,String json) { }
	/** Pure ranking on frozen native held-out inputs. It never fits, calibrates, dispatches, or labels an unexecuted outcome. */
	public static Offline offlineResult(TacticalStoreV3.Checkpoint model,ActionAdvantageV1.Frozen frozen)
	{
		int[][] count=new int[2][2],different=new int[2][2];var observations=new ArrayList<String>();
		var trained=frozen.targets().stream().filter(t->t.sample().selection().split().equals("TRAIN")).map(t->t.sample().terminal().episode().scope()+"/"+t.sample().selection().observation().self().classId()+"/"+t.sample().selection().selected().family()).collect(java.util.stream.Collectors.toSet());
		for(var sample:frozen.samples())
		{
			if(sample.selection().split().equals("TRAIN")){continue;}
			// Every paid held-out legal observation is diagnostic; only one TRAIN decision receives an advantage label.
			for(var timed:sample.terminal().actions())
			{
				var row=timed.row();var attempt=row.receipt().attempt();var observation=attempt.observation();var stock=attempt.baseline();
				float[] encoded=model.model().encode(observation.state());float best=-Float.MAX_VALUE,second=best;LegalActionV3 top=null;
				var scores=new ArrayList<String>();float[] stockFeatures=stock.features(observation);
				for(var action:attempt.offered())
				{
					float[] features=action.features(observation);float score=model.model().scoreEncoded(encoded,features);double distance=0;for(int i=0;i<features.length;i++){distance+=Math.abs(features[i]-stockFeatures[i]);}
					scores.add("{\"action\":"+TrainingStore.quote(action.skillId()+":"+action.skillLevel()+"/"+action.targetObjectId())+",\"score\":"+score+",\"actionFeatureL1FromStock\":"+distance+"}");
					if(score>best){second=best;best=score;top=action;}else{second=Math.max(second,score);}
				}
				int mode=sample.terminal().episode().scope().equals("SOLO")?0:1,split=sample.selection().split().equals("TUNING")?0:1;float gap=best-second;
				boolean covered=trained.contains(sample.terminal().episode().scope()+"/"+observation.self().classId()+"/"+attempt.selected().family());
				boolean differs=!top.equals(stock);if(covered&&attempt.offered().size()>=2){count[mode][split]++;if(differs&&gap>=.025f){different[mode][split]++;}}
				observations.add("{\"eventKey\":"+TrainingStore.quote(row.eventKey())+",\"receiptSha\":"+TrainingStore.quote(TacticalObservationV3.hash(TacticalStoreV3.encode(row)))+",\"terminalSha\":"+TrainingStore.quote(sample.terminal().sha256())
					+",\"episodeId\":"+TrainingStore.quote(sample.terminal().episode().id())+",\"split\":"+TrainingStore.quote(row.split())+",\"scope\":"+TrainingStore.quote(sample.terminal().episode().scope())+",\"class\":"+observation.self().classId()+",\"coveredByTrain\":"+covered
					+",\"stock\":"+TrainingStore.quote(stock.skillId()+":"+stock.skillLevel()+"/"+stock.targetObjectId())+",\"neuralTop\":"+TrainingStore.quote(top.skillId()+":"+top.skillLevel()+"/"+top.targetObjectId())
					+",\"topDifferentFromStock\":"+differs+",\"gap\":"+gap+",\"scores\":["+String.join(",",scores)+"],\"counterfactualOutcome\":null}");
			}
		}
		boolean solo=count[0][0]>=4&&count[0][1]>=4&&different[0][0]>=1&&different[0][1]>=1;
		boolean party=count[1][0]>=4&&count[1][1]>=4&&different[1][0]>=1&&different[1][1]>=1;
		String json="{\"schemaVersion\":7,\"check\":\"INDEPENDENT_FROZEN_NATIVE_OBSERVATION_RANKING\",\"modelId\":"+model.id()+",\"modelSha\":"+TrainingStore.quote(model.sha())+",\"targetSha\":"+TrainingStore.quote(frozen.datasetSha())
			+",\"marginThreshold\":0.025,\"soloAdmitted\":"+solo+",\"partyAdmitted\":"+party+",\"countsTuningAudit\":"+Arrays.deepToString(count)+",\"differentAtMarginTuningAudit\":"+Arrays.deepToString(different)
			+",\"observations\":["+String.join(",",observations)+"],\"trainingCredit\":false,\"limitation\":\"All actions stay episode-split; correlated observations and predicted rankings do not prove native intervention or causal advantage\"}";
		return new Offline(solo,party,json);
	}
	private static void proof(java.nio.file.Path path,String json)throws Exception
	{
		byte[] bytes=(json+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Files.createDirectories(path.getParent());
		if(java.nio.file.Files.exists(path)){check(Arrays.equals(bytes,java.nio.file.Files.readAllBytes(path)),"Immutable007 proof changed");}
		else{java.nio.file.Files.write(path,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);}
	}
	private static void ownedSql(String action,java.nio.file.Path root)throws Exception
	{
		var store=new TacticalStoreV3(root);store.requireStopped();var guard=new TrainingStore(root);var parent=store.optimizationParent();
		check(store.candidate(9).sha().equals("5eacf0b7619c82de3950c0203fcf599c149156d13d31d67cfc9e408362b416dc"),"Historical DRAFT9 changed");
		check(store.accepted(2,"6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc")!=null,"Accepted2 changed");
		check(store.terminalSnapshot(9).datasetSha().equals("37ef5f6a259cbbcb7deed3425772ec9bd4a422a13d133bfc8475eae672720c0f"),"Historical006R1 targets changed");
		if(action.equals("fault-readback"))
		{
			check(parent.modelId()==9&&parent.sha().equals("f76ea960288f3cdb1ba85bb203a33cb327937fdffb99bc6bc4503b90cd54990c")&&parent.state().updates()==817&&parent.state().completedEpochs()==172,"Pre-COMMIT failure changed Adam9");
			var frozen=store.freezeAdvantage();check(frozen!=null,"Fault lost FROZEN007 snapshot");
			check(java.nio.file.Files.exists(store.advantageDirectory().resolve("precommit-"+frozen.runId()+".json")),"Controlled process fault was not observed");
			long models,used;
			try(var connection=guard.openTacticalConnection();var query=connection.createStatement())
			{
				try(var rows=query.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_models WHERE metrics_json LIKE '%CAUSAL_ACTION_ADVANTAGE_V1%'")){rows.next();models=rows.getLong(1);}
				try(var rows=query.executeQuery("SELECT COUNT(*) FROM phantom_tactical_v3_advantage_applications WHERE model_id<>0 OR gradient_used=TRUE")){rows.next();used=rows.getLong(1);}
			}
			check(models==0&&used==0,"Half-committed007 model/Adam/application record");
			String json="{\"check\":\"REAL_PRE_COMMIT_PROCESS_FAILURE_COLD_READBACK\",\"runId\":"+TrainingStore.quote(frozen.runId())+",\"targetSha\":"+TrainingStore.quote(frozen.datasetSha())+",\"modelRows\":0,\"appliedMarkers\":0,\"parentOptimizerSha\":"+TrainingStore.quote(parent.sha())+",\"updates\":817,\"epochs\":172,\"frozenResumable\":true}";
			proof(store.advantageDirectory().resolve("fault-readback-"+frozen.runId()+".json"),json);System.out.println(json);return;
		}
		var model=store.candidate(parent.modelId());
		if(!model.metrics().contains("\"objective\":\""+ActionAdvantageV1.OBJECTIVE+"\""))
		{
			String json="{\"stage\":\"INSUFFICIENT_TRAIN\",\"offline007\":\"NOT_RUN_NO_NEW_DRAFT\",\"soloAdmitted\":false,\"partyAdmitted\":false}";
			proof(store.advantageDirectory().resolve("offline-not-run.json"),json);System.out.println(json);return;
		}
		var frozen=store.advantageSnapshot(model.id());
		if(action.equals("offline"))
		{
			var report=offlineResult(model,frozen);proof(store.advantageDirectory().resolve("offline-"+model.id()+"-"+model.sha()+".json"),report.json());
			System.out.println("LEARNING007_OFFLINE model="+model.id()+" sha="+model.sha()+" SOLO="+report.solo()+" PARTY="+report.party()+" target="+frozen.datasetSha());return;
		}
		check(parent.state().updates()>817&&parent.state().completedEpochs()>172,"007 optimizer did not advance");
		long applications=0,gradients=0;try(var connection=guard.openTacticalConnection();var query=connection.prepareStatement("SELECT fit_role,gradient_used,optimizer_sha FROM phantom_tactical_v3_advantage_applications WHERE model_id=?"))
		{query.setLong(1,model.id());try(var rows=query.executeQuery()){while(rows.next()){applications++;if(rows.getBoolean(2)){gradients++;}check(rows.getBoolean(2)==rows.getString(1).equals("GRADIENT")&&rows.getString(3).equals(parent.sha()),"007 marker/Adam/split mismatch");}}}
		check(applications==frozen.samples().size()&&gradients==frozen.weightedExamples().size(),"007 applications not atomic");
		for(boolean repack:List.of(false,true))
		{
			try(var connection=guard.openTacticalConnection();var query=connection.createStatement())
			{
				connection.setAutoCommit(false);boolean rejected=false;
				String natural=repack?"SHA2(CONCAT(natural_key,'repack007'),256)":"natural_key";
				try{query.executeUpdate("INSERT INTO phantom_tactical_v3_advantage_applications(natural_key,action_key,run_id,model_id,target_sha,receipt_sha,selection_sha,optimizer_sha,split,fit_role,gradient_used) SELECT "+natural+",action_key,run_id,model_id,target_sha,receipt_sha,selection_sha,optimizer_sha,split,fit_role,gradient_used FROM phantom_tactical_v3_advantage_applications WHERE model_id="+model.id()+" LIMIT 1");}
				catch(java.sql.SQLException duplicate){if(duplicate.getErrorCode()!=1062){throw duplicate;}rejected=true;}finally{connection.rollback();}
				check(rejected,"007 duplicate or repack action got another credit");
			}
		}
		check(store.freezeAdvantage()==null&&store.optimizationParent().sha().equals(parent.sha()),"007 retry got another gradient or changed optimizer");
		String json="{\"check\":\"COLD007_ATOMIC_MODEL_ADAM_APPLICATIONS\",\"modelId\":"+model.id()+",\"modelSha\":"+TrainingStore.quote(model.sha())+",\"optimizerSha\":"+TrainingStore.quote(parent.sha())+",\"updates\":"+parent.state().updates()+",\"epochs\":"+parent.state().completedEpochs()+",\"applications\":"+applications+",\"gradientActions\":"+gradients+",\"duplicateAndRepack\":\"REJECTED_1062_ROLLBACK\",\"secondFreeze\":\"NONE\"}";
		proof(store.advantageDirectory().resolve("cold-"+model.id()+".json"),json);System.out.println(json);
	}
	private static void reject(Runnable action,String message)
	{try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError(message);}
	private static void check(boolean passed,String message){if(!passed){throw new AssertionError(message);}}
}
