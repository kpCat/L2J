/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;

/** Executed-action contextual-bandit targets. Historical terminal reward and native receipt bytes are unchanged. */
public final class ActionAdvantageV1
{
	public static final String OBJECTIVE="CAUSAL_ACTION_ADVANTAGE_V1";
	public static final String REVISION="007/advantage/.70terminal+.30local/scale4/leave-block-out-A3/IPSclip4/v1";
	public static final String CONFIG_SHA=hash(REVISION+"/local=clip(2hp/maxHp-mp/500-time/1e10)/TRAIN-B-only/normalize-TRAIN-mean");
	private static final int MAGIC=0x41443037,MAX_BYTES=64*1024*1024;
	private ActionAdvantageV1(){ }
	public record Sample(TrainExplorationV1.Selection selection,TerminalRewardReceiptV1 terminal)
	{
		public Sample
		{
			if(selection==null||terminal==null||!selection.episode().equals(terminal.episode().id())||!selection.split().equals(terminal.split())
				||!TrainExplorationV1.DATASET.equals(terminal.dataset())||!selection.fallback().isEmpty()
				||selection.modelVersion()!=TrainExplorationV1.TEACHER_VERSION||!selection.modelSha().equals(TrainExplorationV1.TEACHER_SHA)
				||terminal.actions().stream().filter(a->selection.matches(a.row().receipt())).count()!=1)
			{throw new IllegalArgumentException("Advantage requires one exact selected paid action and native terminal boundary");}
		}
		public TacticalStoreV3.Row row(){return terminal.actions().stream().filter(a->selection.matches(a.row().receipt())).findFirst().orElseThrow().row();}
		public String actionKey(){return row().actionKey();}
		public float utility()
		{
			var receipt=row().receipt();var maximum=selection.observation().entity(selection.selected().targetObjectId()).fact(Field.MAX_HP);
			if(!maximum.known()||maximum.value()<=0){throw new IllegalArgumentException("Unknown native effect scale");}
			double local=Math.clamp(2*receipt.actualHp()/maximum.value()-receipt.actualMp()/500-receipt.elapsedNanos()/1e10,-1,1);
			return (float)(.70*terminal.reward()+.30*local);
		}
		/** No object/profile/epoch identities in comparison key; exact pre-CUT physical cell and legal action shape remain. */
		public String context()
		{
			var o=selection.observation();var shape=new ArrayList<String>();
			for(var a:selection.offered())
			{
				var target=o.entity(a.targetObjectId());
				shape.add(a.family()+":"+a.skillId()+":"+a.skillLevel()+":"+a.mpCost()+":"+a.castMillis()+":"+a.range()+":"+target.role()+":"+target.classId()+":"+target.fact(Field.MAX_HP));
			}
			Collections.sort(shape);
			return hash(selection.cell()+"/"+o.self().classId()+"/"+o.self().fact(Field.LEVEL)+"/"+shape);
		}
	}
	public record Target(Sample sample,float baseline,float utility,float advantage,double weight,int baselineCount,String baselineSha)
	{
		public TacticalModelV3.Example example()
		{var s=sample.selection();return new TacticalModelV3.Example(s.observation().state(),s.selected().features(s.observation()),advantage);}
	}
	public record Frozen(String runId,String datasetSha,List<Sample> samples)
	{
		public Frozen
		{
			if(runId==null||!runId.matches("[a-f0-9]{32}")){throw new IllegalArgumentException("Advantage run identity");}
			samples=validate(samples);
			if(datasetSha==null||!TacticalObservationV3.hash(pack(samples)).equals(datasetSha)){throw new IllegalArgumentException("Advantage dataset checksum");}
		}
		public byte[] bytes(){return pack(samples);}
		public List<Target> targets()
		{
			var result=new ArrayList<Target>();
			var controls=samples.stream().filter(s->s.selection().split().equals("TRAIN")&&!s.selection().experimental()).toList();
			for(var sample:samples)
			{
				var s=sample.selection();if(s.split().equals("TRAIN")&&!s.experimental()){continue;}
				var baseline=controls.stream().filter(a->a.context().equals(sample.context())&&(!s.split().equals("TRAIN")||!a.selection().block().equals(s.block()))).toList();
				if(baseline.size()<3){continue;}
				float b=(float)baseline.stream().mapToDouble(Sample::utility).average().orElseThrow();float u=sample.utility();
				double weight=Math.min(4,1/(s.offered().size()*s.propensity()));
				String baselineSha=hash(baseline.stream().map(a->a.terminal().naturalKey()+":"+a.utility()).sorted().toList().toString());
				result.add(new Target(sample,b,u,(float)Math.clamp(4*(u-b),-1,1),weight,baseline.size(),baselineSha));
			}
			return List.copyOf(result);
		}
		public List<TacticalModelV3.WeightedExample> weightedExamples()
		{return targets().stream().filter(t->t.sample().selection().split().equals("TRAIN")&&t.sample().selection().experimental()).map(t->new TacticalModelV3.WeightedExample(t.example(),t.weight())).toList();}
		public List<TacticalModelV3.Example> examples(String split)
		{
			if(!Set.of("TUNING","AUDIT").contains(split)){throw new IllegalArgumentException("Held-out advantage examples only");}
			return targets().stream().filter(t->t.sample().selection().split().equals(split)).map(Target::example).toList();
		}
		public List<TacticalStoreV3.Row> sourceRows(){return samples.stream().flatMap(s->s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList();}
		public boolean sufficient()
		{
			return weightedExamples().size()>=16&&examples("TUNING").size()>=8&&examples("AUDIT").size()>=8
				&&targets().stream().anyMatch(t->t.sample().selection().split().equals("TRAIN")&&t.sample().selection().experimental()&&!t.sample().selection().selected().equals(t.sample().selection().stock()))
				&&targets().stream().filter(t->t.sample().selection().split().equals("TRAIN")).map(t->t.sample().terminal().episode().scope()).distinct().count()>=2;
		}
		public double constantLoss()
		{
			double mean=weightedExamples().stream().mapToDouble(w->w.example().reward()).average().orElseThrow();
			return examples("AUDIT").stream().mapToDouble(e->Math.pow(e.reward()-mean,2)).average().orElseThrow();
		}
		public String metrics()
		{
			var train=targets().stream().filter(t->t.sample().selection().split().equals("TRAIN")).toList();
			return "{\"objective\":"+TrainingStore.quote(OBJECTIVE)+",\"configSha\":"+TrainingStore.quote(CONFIG_SHA)+",\"datasetSha\":"+TrainingStore.quote(datasetSha)
				+",\"samples\":"+samples.size()+",\"train\":"+train.size()+",\"tuning\":"+examples("TUNING").size()+",\"audit\":"+examples("AUDIT").size()
				+",\"paidExploratoryAlternatives\":"+train.stream().filter(t->!t.sample().selection().selected().equals(t.sample().selection().stock())).count()
				+",\"baselineData\":\"TRAIN_A_ONLY_LEAVE_BLOCK_OUT_MIN3\",\"propensity\":\"CLIPPED_IPS_UNIFORM_ACTIONS\",\"counterfactualLabels\":false,\"productionPromotion\":false}";
		}
	}
	private static List<Sample> validate(List<Sample> input)
	{
		if(input==null||input.isEmpty()||input.size()>128||input.stream().anyMatch(Objects::isNull)){throw new IllegalArgumentException("Advantage capacity");}
		var samples=input.stream().sorted(Comparator.comparing(s->s.terminal().naturalKey())).toList();var episodes=new HashSet<String>();var actions=new HashSet<String>();var events=new HashSet<String>();
		String installation=samples.getFirst().terminal().boundary().installation(),config=samples.getFirst().terminal().config().sha256();
		for(var sample:samples)
		{
			if(!episodes.add(sample.terminal().naturalKey())||!actions.add(sample.actionKey())||!installation.equals(sample.terminal().boundary().installation())||!config.equals(sample.terminal().config().sha256()))
			{throw new IllegalArgumentException("Duplicate or foreign native advantage source");}
			for(var a:sample.terminal().actions()){if(!events.add(a.row().eventKey())){throw new IllegalArgumentException("Episode actions credited twice");}}
		}
		TacticalStoreV3.validateDataset(samples.stream().flatMap(s->s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList());
		return List.copyOf(samples);
	}
	public static Frozen freeze(String runId,List<Sample> samples)
	{var valid=validate(samples);return new Frozen(runId,TacticalObservationV3.hash(pack(valid)),valid);}
	private static String hash(String value){return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8));}
	private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
	private static byte[] blob(DataInputStream in,int max)throws IOException
	{int count=in.readInt();if(count<1||count>max||count>in.available()){throw new IllegalArgumentException("Advantage blob bound");}return in.readNBytes(count);}
	private static byte[] pack(List<Sample> samples)
	{
		try
		{
			var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);out.writeInt(MAGIC);out.writeInt(1);out.writeUTF(REVISION);out.writeUTF(CONFIG_SHA);out.writeInt(samples.size());
			for(var s:samples){blob(out,s.selection().bytes());out.writeUTF(s.terminal().sha256());blob(out,s.terminal().bytes());if(buffer.size()>MAX_BYTES){throw new IllegalArgumentException("Advantage byte capacity");}}
			out.flush();return buffer.toByteArray();
		}
		catch(IOException failure){throw new IllegalArgumentException("Advantage encoding",failure);}
	}
	public static Frozen restore(String runId,byte[] bytes,String expectedSha)
	{
		if(bytes==null||bytes.length>MAX_BYTES||!TacticalObservationV3.hash(bytes).equals(expectedSha)){throw new IllegalArgumentException("Advantage SHA changed");}
		try(var in=new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if(in.readInt()!=MAGIC||in.readInt()!=1||!in.readUTF().equals(REVISION)||!in.readUTF().equals(CONFIG_SHA)){throw new IllegalArgumentException("Advantage revision changed");}
			int count=in.readInt();if(count<1||count>128){throw new IllegalArgumentException("Advantage sample count");}
			var samples=new ArrayList<Sample>();for(int i=0;i<count;i++)
			{var selection=TrainExplorationV1.Selection.restore(blob(in,1000000));String sha=in.readUTF();samples.add(new Sample(selection,TerminalRewardReceiptV1.restore(blob(in,TerminalRewardReceiptV1.MAX_BYTES),sha)));}
			var result=new Frozen(runId,expectedSha,samples);if(in.available()!=0||!Arrays.equals(result.bytes(),bytes)){throw new IllegalArgumentException("Noncanonical advantage snapshot");}return result;
		}
		catch(IOException failure){throw new IllegalArgumentException("Advantage snapshot invalid",failure);}
	}
}
